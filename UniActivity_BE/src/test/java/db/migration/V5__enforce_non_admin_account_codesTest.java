package db.migration;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V5__enforce_non_admin_account_codesTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:account_code_constraint;MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("""
                    CREATE TABLE users (
                        id BIGINT PRIMARY KEY,
                        username VARCHAR(255) NOT NULL UNIQUE,
                        role VARCHAR(20) NOT NULL,
                        token_version BIGINT NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("INSERT INTO users VALUES (1, 'root-admin', 'ADMIN', 4)");
            statement.execute("INSERT INTO users VALUES (2, '10000001', 'MANAGER', 3)");
            statement.execute("INSERT INTO users VALUES (3, '10000002', 'STUDENT', 7)");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void installsRoleAwareConstraintAfterPreflight() throws Exception {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);

        new V5__enforce_non_admin_account_codes().migrate(context);

        SQLException invalidManager = assertThrows(SQLException.class, () -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO users VALUES (4, 'legacy-manager', 'MANAGER', 0)");
            }
        });
        assertTrue(invalidManager.getMessage().contains("CHK_USERS_NON_ADMIN_ACCOUNT_CODE"));

        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users VALUES (5, 'another-admin', 'ADMIN', 0)");
        }
    }

    @Test
    void refusesToInstallConstraintWhenLegacyDataRemains() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users VALUES (4, 'legacy-student', 'STUDENT', 0)");
        }
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);

        SQLException exception = assertThrows(
                SQLException.class,
                () -> new V5__enforce_non_admin_account_codes().migrate(context)
        );

        assertTrue(exception.getMessage().contains("V4"));
    }

    @Test
    void recognizesOnlyMysqlVersionsThatEnforceCheckConstraints() {
        assertFalse(V5__enforce_non_admin_account_codes.isSupportedMysqlVersion("8.0.15"));
        assertTrue(V5__enforce_non_admin_account_codes.isSupportedMysqlVersion("8.0.16"));
        assertTrue(V5__enforce_non_admin_account_codes.isSupportedMysqlVersion("8.4.1-commercial"));
        assertTrue(V5__enforce_non_admin_account_codes.isSupportedMysqlVersion("9.0.0"));
        assertFalse(V5__enforce_non_admin_account_codes.isSupportedMysqlVersion("invalid"));
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
                () -> new V5__enforce_non_admin_account_codes().validateDatabaseSupport(mysql)
        );

        assertTrue(exception.getMessage().contains("8.0.16+"));
    }
}
