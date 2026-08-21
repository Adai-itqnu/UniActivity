package db.migration;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V6__normalize_class_join_codesTest {

    private static final Pattern CODE = Pattern.compile("^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$");
    private static final Pattern HAS_LETTER = Pattern.compile(".*[ABCDEFGHJKMNPQRSTUVWXYZ].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*[23456789].*");

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:class_code_migration;MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS classes");
            statement.execute("""
                    CREATE TABLE classes (
                        id BIGINT PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        join_code VARCHAR(255)
                    )
                    """);
            statement.execute("INSERT INTO classes VALUES (1, 'Null code', NULL)");
            statement.execute("INSERT INTO classes VALUES (2, 'Legacy code', 'ABCDEFGH')");
            statement.execute("INSERT INTO classes VALUES (3, 'Valid legacy code', 'ABC234')");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void rotatesEveryClassAndEnforcesNormalizedJoinCodes() throws Exception {
        Map<Long, String> before = codesById();

        migrateWithDeterministicRandom();

        Map<Long, String> after = codesById();
        assertEquals(3, after.size());
        assertEquals(3, distinctCodeCount());
        for (Map.Entry<Long, String> row : after.entrySet()) {
            assertNotEquals(before.get(row.getKey()), row.getValue());
            assertTrue(CODE.matcher(row.getValue()).matches());
            assertTrue(HAS_LETTER.matcher(row.getValue()).matches());
            assertTrue(HAS_DIGIT.matcher(row.getValue()).matches());
        }

        String existing = after.values().iterator().next();
        assertThrows(SQLException.class, () -> insertClass(4, existing));
        assertThrows(SQLException.class, () -> insertClass(5, null));
        assertThrows(SQLException.class, () -> insertClass(6, "AAAAAA"));
        assertThrows(SQLException.class, () -> insertClass(7, "ABC12I"));
    }

    @Test
    void reusesAnExistingUniqueIndexInsteadOfInstallingADuplicate() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE UNIQUE INDEX legacy_join_code_unique ON classes(join_code)");
        }

        migrateWithDeterministicRandom();

        assertEquals(1, uniqueIndexesCoveringJoinCode());
        assertEquals(1, checkConstraintsNamed("chk_classes_join_code_format"));
    }

    @Test
    void reusesAnEquivalentCheckConstraintWithAnotherName() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("UPDATE classes SET join_code = 'A2B3C4' WHERE id = 1");
            statement.execute("UPDATE classes SET join_code = 'D5E6F7' WHERE id = 2");
            statement.execute("UPDATE classes SET join_code = 'G8H2J3' WHERE id = 3");
            statement.execute("""
                    ALTER TABLE classes
                    ADD CONSTRAINT legacy_join_code_format
                    CHECK (REGEXP_LIKE(join_code, '^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$')
                      AND REGEXP_LIKE(join_code, '[ABCDEFGHJKMNPQRSTUVWXYZ]')
                      AND REGEXP_LIKE(join_code, '[23456789]'))
                    """);
        }

        migrateWithDeterministicRandom();

        assertEquals(1, totalCheckConstraints());
        assertEquals(0, checkConstraintsNamed("chk_classes_join_code_format"));
    }

    @Test
    void failsBeforeDdlWhenNoMixedCodeCanBeGenerated() throws Exception {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(anyInt())).thenReturn(0);
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);

        SQLException exception = assertThrows(
                SQLException.class,
                () -> new V6__normalize_class_join_codes(random).migrate(context)
        );

        assertTrue(exception.getMessage().contains("Không thể cấp mã lớp"));
        assertTrue(joinCodeColumnIsNullable());
        assertEquals(0, uniqueIndexesCoveringJoinCode());
        assertEquals(0, totalCheckConstraints());
    }

    @Test
    void rejectsConflictingNamedCheckConstraint() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE classes
                    ADD CONSTRAINT chk_classes_join_code_format CHECK (LENGTH(join_code) > 0)
                    """);
        }

        SQLException exception = assertThrows(SQLException.class, this::migrateWithDeterministicRandom);

        assertTrue(exception.getMessage().contains("không đúng định nghĩa"));
    }

    @Test
    void recognizesOnlyMysqlVersionsThatEnforceCheckConstraints() {
        assertFalse(V6__normalize_class_join_codes.isSupportedMysqlVersion("8.0.15"));
        assertTrue(V6__normalize_class_join_codes.isSupportedMysqlVersion("8.0.16"));
        assertTrue(V6__normalize_class_join_codes.isSupportedMysqlVersion("8.4.1-commercial"));
        assertTrue(V6__normalize_class_join_codes.isSupportedMysqlVersion("9.0.0"));
        assertFalse(V6__normalize_class_join_codes.isSupportedMysqlVersion("invalid"));
    }

    @Test
    void requiresMysqlRenderedCheckClauseToBeExplicitlyCaseSensitive() {
        assertTrue(V6__normalize_class_join_codes.isExpectedCheckClause("""
                ((regexp_like(`join_code`,_utf8mb4'^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$',_utf8mb4'c')
                  and regexp_like(`join_code`,_utf8mb4'[ABCDEFGHJKMNPQRSTUVWXYZ]',_utf8mb4'c'))
                  and regexp_like(`join_code`,_utf8mb4'[23456789]',_utf8mb4'c'))
                """));
        assertTrue(V6__normalize_class_join_codes.isExpectedCheckClause("""
                ((regexp_like(`join_code`,_utf8mb4'(?-i)^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$')
                  and regexp_like(`join_code`,_utf8mb4'(?-i)[ABCDEFGHJKMNPQRSTUVWXYZ]'))
                  and regexp_like(`join_code`,_utf8mb4'(?-i)[23456789]'))
                """));
        assertFalse(V6__normalize_class_join_codes.isExpectedCheckClause("""
                ((regexp_like(`join_code`,_utf8mb4'^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$')
                  and regexp_like(`join_code`,_utf8mb4'[ABCDEFGHJKMNPQRSTUVWXYZ]'))
                  and regexp_like(`join_code`,_utf8mb4'[23456789]'))
                """));
    }

    @Test
    void rejectsMysqlVersionThatDoesNotEnforceCheckConstraints() throws Exception {
        Connection mysql = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet versions = mock(ResultSet.class);
        when(mysql.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(mysql.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT VERSION()")).thenReturn(versions);
        when(versions.next()).thenReturn(true);
        when(versions.getString(1)).thenReturn("8.0.15");

        SQLException exception = assertThrows(
                SQLException.class,
                () -> new V6__normalize_class_join_codes(mock(SecureRandom.class))
                        .validateDatabaseSupport(mysql)
        );

        assertTrue(exception.getMessage().contains("8.0.16+"));
    }

    private void migrateWithDeterministicRandom() throws Exception {
        SecureRandom random = mock(SecureRandom.class);
        AtomicInteger sequence = new AtomicInteger();
        when(random.nextInt(anyInt())).thenAnswer(invocation -> {
            int bound = invocation.getArgument(0);
            return Math.floorMod(sequence.getAndIncrement(), bound);
        });
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);

        new V6__normalize_class_join_codes(random).migrate(context);
    }

    private Map<Long, String> codesById() throws SQLException {
        Map<Long, String> codes = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, join_code FROM classes")) {
            while (rows.next()) {
                codes.put(rows.getLong("id"), rows.getString("join_code"));
            }
        }
        return codes;
    }

    private long distinctCodeCount() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(DISTINCT join_code) FROM classes")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private void insertClass(long id, String joinCode) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO classes (id, name, join_code) VALUES (?, ?, ?)"
        )) {
            insert.setLong(1, id);
            insert.setString(2, "Inserted class");
            insert.setString(3, joinCode);
            insert.executeUpdate();
        }
    }

    private int uniqueIndexesCoveringJoinCode() throws SQLException {
        int matches = 0;
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet indexes = metadata.getIndexInfo(
                connection.getCatalog(), connection.getSchema(), "CLASSES", true, false
        )) {
            while (indexes.next()) {
                if ("JOIN_CODE".equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) {
                    matches++;
                }
            }
        }
        return matches;
    }

    private int checkConstraintsNamed(String name) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE UPPER(TABLE_NAME) = 'CLASSES'
                  AND UPPER(CONSTRAINT_NAME) = UPPER(?)
                  AND UPPER(CONSTRAINT_TYPE) = 'CHECK'
                """)) {
            query.setString(1, name);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private int totalCheckConstraints() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT COUNT(*)
                     FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                     WHERE UPPER(TABLE_NAME) = 'CLASSES'
                       AND UPPER(CONSTRAINT_TYPE) = 'CHECK'
                     """)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private boolean joinCodeColumnIsNullable() throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(
                connection.getCatalog(), connection.getSchema(), "CLASSES", "JOIN_CODE"
        )) {
            assertTrue(columns.next());
            return columns.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
        }
    }
}
