package db.migration;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
            statement.execute("DROP TABLE IF EXISTS join_code_reference");
            statement.execute("DROP TABLE IF EXISTS uniactivity_v6_class_join_code_state");
            statement.execute("DROP TABLE IF EXISTS classes");
            statement.execute("DROP TABLE IF EXISTS code_registry");
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
        String[] disallowed = {"a2B3C4", "ABC12I", "ABC12L", "ABC12O", "ABC120", "ABC121"};
        for (int index = 0; index < disallowed.length; index++) {
            long id = 7L + index;
            String invalidCode = disallowed[index];
            assertThrows(SQLException.class, () -> insertClass(id, invalidCode));
        }

        migrateWithDeterministicRandom();

        assertEquals(after, codesById(), "completed V6 reruns must not rotate codes again");
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
    void avoidsLegacyCollisionsUsingTheActiveUniqueIndexComparisonSemantics() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE classes");
            statement.execute("""
                    CREATE TABLE classes (
                        id BIGINT PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        join_code VARCHAR_IGNORECASE(255)
                    )
                    """);
            statement.execute("INSERT INTO classes VALUES (1, 'Case insensitive', 'a2b3c4')");
            statement.execute("CREATE UNIQUE INDEX legacy_join_code_unique ON classes(join_code)");
        }
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(anyInt())).thenReturn(
                0, 23, 1, 24, 2, 25,
                3, 26, 4, 27, 5, 28
        );

        migrateWithRandom(random);

        assertEquals("D5E6F7", codesById().get(1L));
    }

    @Test
    void rejectsMysqlUniquePrefixIndexBeforeGeneratingReplacements() throws Exception {
        Connection mysql = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement query = mock(PreparedStatement.class);
        ResultSet indexes = mock(ResultSet.class);
        when(mysql.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(mysql.getCatalog()).thenReturn("uniactivity");
        when(mysql.prepareStatement(anyString())).thenReturn(query);
        when(query.executeQuery()).thenReturn(indexes);
        when(indexes.next()).thenReturn(true);
        when(indexes.getString("INDEX_NAME")).thenReturn("legacy_join_code_prefix");

        SQLException exception = assertThrows(
                SQLException.class,
                () -> new V6__normalize_class_join_codes(mock(SecureRandom.class))
                        .validateUpdateIndexSafety(mysql)
        );

        assertTrue(exception.getMessage().contains("legacy_join_code_prefix"));
        assertTrue(exception.getMessage().contains("prefix"));
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
    void exhaustedPreparationLeavesOldCodesAuthoritativeAndCanBeRetried() throws Exception {
        Map<Long, String> before = codesById();
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(anyInt())).thenReturn(0);
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);

        SQLException exception = assertThrows(
                SQLException.class,
                () -> new V6__normalize_class_join_codes(random).migrate(context)
        );

        assertTrue(exception.getMessage().contains("Không thể cấp mã lớp"));
        assertEquals(before, codesById());
        assertTrue(joinCodeColumnIsNullable());
        assertEquals(0, uniqueIndexesCoveringJoinCode());
        assertEquals(0, totalCheckConstraints());

        migrateWithDeterministicRandom();

        Map<Long, String> afterRetry = codesById();
        before.forEach((id, oldCode) -> assertNotEquals(oldCode, afterRetry.get(id)));
    }

    @Test
    void failureImmediatelyBeforeCutoverLeavesOriginalCodesAuthoritativeAndRerunCompletes()
            throws Exception {
        Map<Long, String> before = codesById();
        Connection interrupted = failOnSql(sql -> {
            String normalized = sql.replaceAll("\\s+", " ").trim().toUpperCase();
            return normalized.contains("ALTER COLUMN JOIN_CODE VARCHAR(6) NOT NULL")
                    || normalized.contains("RENAME COLUMN JOIN_CODE TO JOIN_CODE_V6_LEGACY");
        });

        assertThrows(SQLException.class, () -> migrateWithConnection(
                interrupted, deterministicRandom()
        ));

        assertEquals(before, codesById(), "join_code must stay authoritative before cutover");
        assertTrue(columnExists("JOIN_CODE_V6"));

        migrateWithDeterministicRandom();

        Map<Long, String> after = codesById();
        assertEquals(before.keySet(), after.keySet());
        before.forEach((id, oldCode) -> assertNotEquals(oldCode, after.get(id)));
        assertFalse(columnExists("JOIN_CODE_V6"));
    }

    @Test
    void preparedCodesMustNotMatchAnyLegacyRowUnderDatabaseComparisonSemantics()
            throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE classes ADD COLUMN join_code_v6 VARCHAR(6)");
            statement.execute("UPDATE classes SET join_code_v6 = 'ABC234' WHERE id = 1");
            statement.execute("UPDATE classes SET join_code_v6 = 'D5E6F7' WHERE id = 2");
            statement.execute("UPDATE classes SET join_code_v6 = 'G8H2J3' WHERE id = 3");
        }

        SQLException exception = assertThrows(
                SQLException.class,
                () -> new V6__normalize_class_join_codes().verifyPreparedData(connection)
        );

        assertTrue(exception.getMessage().contains("mã cũ"));
    }

    @Test
    void failureDuringPostCutoverCleanupResumesWithoutRotatingCodesAgain() throws Exception {
        Map<Long, String> before = codesById();
        Connection interrupted = failOnSql(sql -> sql.replaceAll("\\s+", " ")
                .toUpperCase()
                .contains("DROP CONSTRAINT CHK_CLASSES_JOIN_CODE_V6_ROTATED"));

        assertThrows(SQLException.class, () -> migrateWithConnection(
                interrupted, deterministicRandom()
        ));

        Map<Long, String> afterCutover = codesById();
        before.forEach((id, oldCode) -> assertNotEquals(oldCode, afterCutover.get(id)));
        assertTrue(columnExists("JOIN_CODE_V6_LEGACY"));

        migrateWithDeterministicRandom();

        assertEquals(afterCutover, codesById());
        assertFalse(columnExists("JOIN_CODE_V6_LEGACY"));
    }

    @Test
    void crashAfterCutoverBeforeMarkerWriteResumesWithoutRotatingAgain() throws Exception {
        Map<Long, String> before = codesById();

        assertThrows(
                SQLException.class,
                () -> migrateWithConnection(
                        failBeforeCutoverMarkerWrite(),
                        deterministicRandom()
                )
        );

        Map<Long, String> afterCutover = codesById();
        before.forEach((id, oldCode) -> assertNotEquals(oldCode, afterCutover.get(id)));
        assertTrue(columnExists("JOIN_CODE_V6_LEGACY"));
        assertEquals("PREPARING", markerPhase());

        migrateWithDeterministicRandom();

        assertEquals(afterCutover, codesById());
        assertFalse(columnExists("JOIN_CODE_V6_LEGACY"));
        assertEquals("CUTOVER", markerPhase());
    }

    @Test
    void mysqlCutoverContractUsesOneAtomicAlterAndFlywayDoesNotWrapItInATransaction() {
        String sql = V6__normalize_class_join_codes.mysqlAtomicCutoverSql(
                java.util.List.of("legacy_join_code_format"),
                true,
                "legacy_join_code_format"
        );

        assertEquals(1, sql.split("ALTER TABLE classes", -1).length - 1);
        assertTrue(sql.contains("DROP CHECK `legacy_join_code_format`"));
        assertTrue(sql.contains("DROP INDEX `uk_classes_join_code`"));
        assertTrue(sql.contains("RENAME COLUMN join_code TO join_code_v6_legacy"));
        assertTrue(sql.contains(
                "CHANGE COLUMN join_code_v6 join_code VARCHAR(6) NOT NULL"
        ));
        assertFalse(sql.contains("RENAME COLUMN join_code_v6 TO join_code"));
        assertTrue(sql.contains("ADD CONSTRAINT `uk_classes_join_code` UNIQUE (join_code)"));
        assertTrue(sql.contains("ADD CONSTRAINT `legacy_join_code_format` CHECK"));
        assertTrue(sql.contains("ADD CONSTRAINT `chk_classes_join_code_v6_rotated` CHECK"));
        assertFalse(new V6__normalize_class_join_codes().canExecuteInTransaction());
    }

    @Test
    void mysqlMarkerTableContractPinsInnoDbWhileH2KeepsPortableDdl() {
        assertTrue(V6__normalize_class_join_codes.markerTableDdl(true)
                .endsWith("ENGINE=InnoDB"));
        assertFalse(V6__normalize_class_join_codes.markerTableDdl(false)
                .contains("ENGINE="));
    }

    @Test
    void mysqlStagingDdlCompletesBeforeFreshLockProtectsPopulationVerificationAndCutover()
            throws Exception {
        Connection mysql = mock(Connection.class);
        Statement statements = mock(Statement.class);
        V6__normalize_class_join_codes.MysqlLockedWork stagingColumnDdl =
                mock(V6__normalize_class_join_codes.MysqlLockedWork.class);
        V6__normalize_class_join_codes.MysqlLockedWork populate =
                mock(V6__normalize_class_join_codes.MysqlLockedWork.class);
        V6__normalize_class_join_codes.MysqlLockedWork verifyPrepared =
                mock(V6__normalize_class_join_codes.MysqlLockedWork.class);
        V6__normalize_class_join_codes.MysqlLockedWork atomicCutover =
                mock(V6__normalize_class_join_codes.MysqlLockedWork.class);
        V6__normalize_class_join_codes.MysqlLockedWork protectedWork = () -> {
            populate.execute();
            verifyPrepared.execute();
            atomicCutover.execute();
        };
        when(mysql.getAutoCommit()).thenReturn(true);
        when(mysql.createStatement()).thenReturn(statements);

        new V6__normalize_class_join_codes().withMysqlClassesWriteLock(
                mysql,
                stagingColumnDdl,
                protectedWork
        );

        InOrder order = inOrder(
                stagingColumnDdl,
                mysql,
                statements,
                populate,
                verifyPrepared,
                atomicCutover
        );
        order.verify(stagingColumnDdl).execute();
        order.verify(mysql).setAutoCommit(false);
        order.verify(statements).execute("""
                LOCK TABLES classes WRITE, classes AS prepared WRITE, classes AS legacy WRITE, uniactivity_v6_class_join_code_state WRITE
                """.strip());
        order.verify(populate).execute();
        order.verify(verifyPrepared).execute();
        order.verify(atomicCutover).execute();
        order.verify(mysql).commit();
        order.verify(statements).execute("UNLOCK TABLES");
        order.verify(mysql).setAutoCommit(true);
    }

    @Test
    void mysqlWriteLockIsReleasedAndPreparationRolledBackWhenWorkFails() throws Exception {
        Connection mysql = mock(Connection.class);
        Statement statements = mock(Statement.class);
        V6__normalize_class_join_codes.MysqlLockedWork stagingColumnDdl =
                mock(V6__normalize_class_join_codes.MysqlLockedWork.class);
        V6__normalize_class_join_codes.MysqlLockedWork work =
                mock(V6__normalize_class_join_codes.MysqlLockedWork.class);
        when(mysql.getAutoCommit()).thenReturn(true);
        when(mysql.createStatement()).thenReturn(statements);
        doThrow(new SQLException("simulated locked-phase failure")).when(work).execute();

        SQLException exception = assertThrows(
                SQLException.class,
                () -> new V6__normalize_class_join_codes()
                        .withMysqlClassesWriteLock(mysql, stagingColumnDdl, work)
        );

        assertEquals("simulated locked-phase failure", exception.getMessage());
        InOrder order = inOrder(stagingColumnDdl, mysql, statements, work);
        order.verify(stagingColumnDdl).execute();
        order.verify(mysql).setAutoCommit(false);
        order.verify(statements).execute("""
                LOCK TABLES classes WRITE, classes AS prepared WRITE, classes AS legacy WRITE, uniactivity_v6_class_join_code_state WRITE
                """.strip());
        order.verify(work).execute();
        order.verify(mysql).rollback();
        order.verify(statements).execute("UNLOCK TABLES");
        order.verify(mysql).setAutoCommit(true);
    }

    @Test
    void rejectsNonInnoDbMysqlBeforeExecutingMutationSql() throws Exception {
        Connection mysql = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet versions = mock(ResultSet.class);
        PreparedStatement engineQuery = mock(PreparedStatement.class);
        ResultSet engines = mock(ResultSet.class);
        when(mysql.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(mysql.getCatalog()).thenReturn("uniactivity");
        when(mysql.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT VERSION()")).thenReturn(versions);
        when(versions.next()).thenReturn(true);
        when(versions.getString(1)).thenReturn("8.0.16");
        when(mysql.prepareStatement(anyString())).thenReturn(engineQuery);
        when(engineQuery.executeQuery()).thenReturn(engines);
        when(engines.next()).thenReturn(true);
        when(engines.getString("ENGINE")).thenReturn("MyISAM");
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(mysql);

        SQLException exception = assertThrows(
                SQLException.class,
                () -> new V6__normalize_class_join_codes().migrate(context)
        );

        assertTrue(exception.getMessage().contains("InnoDB"));
        verify(statement, never()).execute(anyString());
    }

    @Test
    void rejectsConflictingNamedCheckConstraint() throws Exception {
        Map<Long, String> before = codesById();
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE classes
                    ADD CONSTRAINT chk_classes_join_code_format CHECK (LENGTH(join_code) > 0)
                    """);
        }

        SQLException exception = assertThrows(SQLException.class, this::migrateWithDeterministicRandom);

        assertTrue(exception.getMessage().contains("không đúng định nghĩa"));
        assertEquals(before, codesById());
        assertFalse(columnExists("JOIN_CODE_V6"));
        assertFalse(tableExists("UNIACTIVITY_V6_CLASS_JOIN_CODE_STATE"));
    }

    @Test
    void rejectsMarkerWithoutItsRequiredPrimaryKeyBeforeClassMutation() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE uniactivity_v6_class_join_code_state (
                        migration_key VARCHAR(64) NOT NULL,
                        phase VARCHAR(16) NOT NULL
                    )
                    """);
        }
        Map<Long, String> before = codesById();

        SQLException exception = assertThrows(
                SQLException.class,
                this::migrateWithDeterministicRandom
        );

        assertTrue(exception.getMessage().toLowerCase(Locale.ROOT).contains("khóa chính"));
        assertEquals(before, codesById());
        assertFalse(columnExists("JOIN_CODE_V6"));
    }

    @Test
    void rejectsMarkerWithWrongPhysicalColumnLengthsBeforeClassMutation() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE uniactivity_v6_class_join_code_state (
                        migration_key VARCHAR(63) NOT NULL PRIMARY KEY,
                        phase VARCHAR(17) NOT NULL
                    )
                    """);
        }
        Map<Long, String> before = codesById();

        SQLException exception = assertThrows(
                SQLException.class,
                this::migrateWithDeterministicRandom
        );

        assertTrue(exception.getMessage().contains("cấu trúc không tương thích"));
        assertEquals(before, codesById());
        assertFalse(columnExists("JOIN_CODE_V6"));
    }

    @Test
    void rejectsJoinCodeForeignKeyDependencyBeforeAnyMigrationMutation() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE UNIQUE INDEX legacy_join_code_unique ON classes(join_code)");
            statement.execute("""
                    CREATE TABLE join_code_reference (
                        id BIGINT PRIMARY KEY,
                        class_join_code VARCHAR(255),
                        CONSTRAINT fk_class_join_code
                            FOREIGN KEY (class_join_code) REFERENCES classes(join_code)
                    )
                    """);
        }
        Map<Long, String> before = codesById();

        SQLException exception = assertThrows(
                SQLException.class,
                this::migrateWithDeterministicRandom
        );

        String message = exception.getMessage().toLowerCase(Locale.ROOT);
        assertTrue(message.contains("fk_class_join_code"));
        assertTrue(message.contains("foreign key"));
        assertEquals(before, codesById());
        assertFalse(columnExists("JOIN_CODE_V6"));
        assertFalse(tableExists("UNIACTIVITY_V6_CLASS_JOIN_CODE_STATE"));
    }

    @Test
    void rejectsOutboundJoinCodeForeignKeyBeforeAnyMigrationMutation() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE code_registry (code VARCHAR(255) PRIMARY KEY)");
            statement.execute("INSERT INTO code_registry VALUES ('ABCDEFGH'), ('ABC234')");
            statement.execute("""
                    ALTER TABLE classes
                    ADD CONSTRAINT fk_classes_join_code
                    FOREIGN KEY (join_code) REFERENCES code_registry(code)
                    """);
        }
        Map<Long, String> before = codesById();

        SQLException exception = assertThrows(
                SQLException.class,
                this::migrateWithDeterministicRandom
        );

        String message = exception.getMessage().toLowerCase(Locale.ROOT);
        assertTrue(message.contains("fk_classes_join_code"));
        assertTrue(message.contains("foreign key"));
        assertEquals(before, codesById());
        assertFalse(columnExists("JOIN_CODE_V6"));
        assertFalse(tableExists("UNIACTIVITY_V6_CLASS_JOIN_CODE_STATE"));
    }

    @Test
    void rejectsJoinCodePrimaryKeyBeforeAnyMigrationMutation() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE classes");
            statement.execute("""
                    CREATE TABLE classes (
                        id BIGINT NOT NULL UNIQUE,
                        name VARCHAR(255) NOT NULL,
                        join_code VARCHAR(255) PRIMARY KEY
                    )
                    """);
            statement.execute("INSERT INTO classes VALUES (1, 'One', 'A2B3C4')");
            statement.execute("INSERT INTO classes VALUES (2, 'Two', 'D5E6F7')");
            statement.execute("INSERT INTO classes VALUES (3, 'Three', 'G8H2J3')");
        }
        Map<Long, String> before = codesById();

        SQLException exception = assertThrows(
                SQLException.class,
                this::migrateWithDeterministicRandom
        );

        assertTrue(exception.getMessage().toLowerCase(Locale.ROOT).contains("khóa chính"));
        assertEquals(before, codesById());
        assertFalse(columnExists("JOIN_CODE_V6"));
        assertFalse(tableExists("UNIACTIVITY_V6_CLASS_JOIN_CODE_STATE"));
    }

    @Test
    void rejectsConflictingFixedNameEvenAfterFindingAnEquivalentConstraint() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("UPDATE classes SET join_code = 'A2B3C4' WHERE id = 1");
            statement.execute("UPDATE classes SET join_code = 'D5E6F7' WHERE id = 2");
            statement.execute("UPDATE classes SET join_code = 'G8H2J3' WHERE id = 3");
            statement.execute("""
                    ALTER TABLE classes
                    ADD CONSTRAINT aaa_equivalent_join_code_format
                    CHECK (REGEXP_LIKE(join_code, '^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$')
                      AND REGEXP_LIKE(join_code, '[ABCDEFGHJKMNPQRSTUVWXYZ]')
                      AND REGEXP_LIKE(join_code, '[23456789]'))
                    """);
            statement.execute("""
                    ALTER TABLE classes
                    ADD CONSTRAINT chk_classes_join_code_format CHECK (LENGTH(join_code) > 0)
                    """);
        }

        SQLException exception = assertThrows(SQLException.class, this::migrateWithDeterministicRandom);

        assertTrue(exception.getMessage().contains("chk_classes_join_code_format"));
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
        assertFalse(V6__normalize_class_join_codes.isExpectedCheckClause("""
                ((regexp_like(`join_code`,_utf8mb4'(?-i)^[abcdefghjkmnpqrstuvwxyz23456789]{6}$')
                  and regexp_like(`join_code`,_utf8mb4'(?-i)[abcdefghjkmnpqrstuvwxyz]'))
                  and regexp_like(`join_code`,_utf8mb4'(?-i)[23456789]'))
                """));
    }

    @Test
    void recognizesRawMysqlRegexpOperatorWithAndWithoutCharsetIntroducers() {
        assertTrue(V6__normalize_class_join_codes.isExpectedCheckClause("""
                ((join_code REGEXP '(?-i)^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$'
                  AND join_code REGEXP '(?-i)[ABCDEFGHJKMNPQRSTUVWXYZ]')
                  AND join_code REGEXP '(?-i)[23456789]')
                """));
        assertTrue(V6__normalize_class_join_codes.isExpectedCheckClause("""
                ((join_code REGEXP _utf8mb4'(?-i)^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$'
                  AND join_code REGEXP _utf8mb4'(?-i)[ABCDEFGHJKMNPQRSTUVWXYZ]')
                  AND join_code REGEXP _utf8mb4'(?-i)[23456789]')
                """));
    }

    @Test
    void preservesRegexLiteralCaseWhenRecognizingH2CheckClauses() {
        assertTrue(V6__normalize_class_join_codes.isExpectedH2CheckClause("""
                ((REGEXP_LIKE("JOIN_CODE", '^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$')
                  AND REGEXP_LIKE("JOIN_CODE", '[ABCDEFGHJKMNPQRSTUVWXYZ]'))
                  AND REGEXP_LIKE("JOIN_CODE", '[23456789]'))
                """));
        assertFalse(V6__normalize_class_join_codes.isExpectedH2CheckClause("""
                ((REGEXP_LIKE("JOIN_CODE", '^[abcdefghjkmnpqrstuvwxyz23456789]{6}$')
                  AND REGEXP_LIKE("JOIN_CODE", '[abcdefghjkmnpqrstuvwxyz]'))
                  AND REGEXP_LIKE("JOIN_CODE", '[23456789]'))
                """));
    }

    @Test
    void reusesMysqlEquivalentCheckOnlyWhenItIsEnforced() {
        String expectedClause = """
                ((regexp_like(`join_code`,_utf8mb4'(?-i)^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$')
                  and regexp_like(`join_code`,_utf8mb4'(?-i)[ABCDEFGHJKMNPQRSTUVWXYZ]'))
                  and regexp_like(`join_code`,_utf8mb4'(?-i)[23456789]'))
                """;

        assertFalse(V6__normalize_class_join_codes.isReusableMysqlCheckConstraint(
                expectedClause, "NO"
        ));
        assertTrue(V6__normalize_class_join_codes.isReusableMysqlCheckConstraint(
                expectedClause, "YES"
        ));
    }

    @Test
    void acceptsOnlyPhysicalVarcharSixNotNullColumnDefinition() {
        assertTrue(V6__normalize_class_join_codes.isExpectedJoinCodeColumn(
                DatabaseMetaData.columnNoNulls, 6, Types.VARCHAR, "VARCHAR"
        ));
        assertTrue(V6__normalize_class_join_codes.isExpectedJoinCodeColumn(
                DatabaseMetaData.columnNoNulls, 6, Types.VARCHAR, "CHARACTER VARYING"
        ));
        assertFalse(V6__normalize_class_join_codes.isExpectedJoinCodeColumn(
                DatabaseMetaData.columnNoNulls, 6, Types.CHAR, "CHARACTER"
        ));
        assertFalse(V6__normalize_class_join_codes.isExpectedJoinCodeColumn(
                DatabaseMetaData.columnNullable, 6, Types.VARCHAR, "VARCHAR"
        ));
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
        migrateWithRandom(deterministicRandom());
    }

    private SecureRandom deterministicRandom() {
        SecureRandom random = mock(SecureRandom.class);
        AtomicInteger sequence = new AtomicInteger();
        when(random.nextInt(anyInt())).thenAnswer(invocation -> {
            int bound = invocation.getArgument(0);
            return Math.floorMod(sequence.getAndIncrement(), bound);
        });
        return random;
    }

    private void migrateWithRandom(SecureRandom random) throws Exception {
        migrateWithConnection(connection, random);
    }

    private void migrateWithConnection(Connection migrationConnection, SecureRandom random)
            throws Exception {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(migrationConnection);
        new V6__normalize_class_join_codes(random).migrate(context);
    }

    private Connection failOnSql(Predicate<String> shouldFail) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    try {
                        Object result = method.invoke(connection, arguments);
                        if (result instanceof Statement statement
                                && "createStatement".equals(method.getName())) {
                            return failOnSql(statement, shouldFail);
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }
        );
    }

    private Connection failBeforeCutoverMarkerWrite() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    try {
                        Object result = method.invoke(connection, arguments);
                        if (result instanceof PreparedStatement statement
                                && "prepareStatement".equals(method.getName())
                                && arguments != null
                                && arguments[0] instanceof String sql
                                && sql.contains("UPDATE uniactivity_v6_class_join_code_state")) {
                            return failBeforeCutoverMarkerWrite(statement);
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }
        );
    }

    private PreparedStatement failBeforeCutoverMarkerWrite(PreparedStatement delegate) {
        Map<Integer, String> stringParameters = new HashMap<>();
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, arguments) -> {
                    if ("setString".equals(method.getName())) {
                        stringParameters.put((Integer) arguments[0], (String) arguments[1]);
                    }
                    if ("executeUpdate".equals(method.getName())
                            && "CUTOVER".equals(stringParameters.get(1))) {
                        throw new SQLException("Simulated crash before CUTOVER marker write");
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }
        );
    }

    private Statement failOnSql(Statement delegate, Predicate<String> shouldFail) {
        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, arguments) -> {
                    if (method.getName().startsWith("execute")
                            && arguments != null
                            && arguments.length > 0
                            && arguments[0] instanceof String sql
                            && shouldFail.test(sql)) {
                        throw new SQLException("Simulated interruption before cutover");
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }
        );
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

    private boolean columnExists(String columnName) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(
                connection.getCatalog(), connection.getSchema(), "CLASSES", columnName
        )) {
            return columns.next();
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(
                connection.getCatalog(), connection.getSchema(), tableName, new String[]{"TABLE"}
        )) {
            return tables.next();
        }
    }

    private String markerPhase() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT phase
                     FROM uniactivity_v6_class_join_code_state
                     WHERE migration_key = 'classes.join_code'
                     """)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }
}
