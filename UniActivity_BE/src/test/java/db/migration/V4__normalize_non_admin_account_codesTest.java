package db.migration;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V4__normalize_non_admin_account_codesTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:account_code_migration;MODE=MySQL;DB_CLOSE_DELAY=-1"
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
            statement.execute("INSERT INTO users VALUES (2, 'manager51', 'MANAGER', 3)");
            statement.execute("INSERT INTO users VALUES (3, 'google_abc', 'STUDENT', 7)");
            statement.execute("INSERT INTO users VALUES (4, '10000005', 'STUDENT', 2)");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void migratesOnlyInvalidNonAdminsWithoutInstallingConstraint() throws Exception {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(90_000_000)).thenReturn(5, 6, 5, 6, 7);
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);

        new V4__normalize_non_admin_account_codes(random).migrate(context);

        assertUser(1, "root-admin", 4);
        assertUser(2, "10000006", 4);
        assertUser(3, "10000007", 8);
        assertUser(4, "10000005", 2);
        assertEquals(4, distinctUsernameCount());

        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users VALUES (5, 'legacy-manager', 'MANAGER', 0)");
        }
        assertUser(5, "legacy-manager", 0);
    }

    private void assertUser(long id, String username, long tokenVersion) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT username, token_version FROM users WHERE id = " + id
             )) {
            assertTrue(rows.next());
            assertEquals(username, rows.getString("username"));
            assertEquals(tokenVersion, rows.getLong("token_version"));
        }
    }

    private long distinctUsernameCount() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(DISTINCT username) FROM users")) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
