package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class V4__normalize_non_admin_account_codes extends BaseJavaMigration {

    private static final Pattern ACCOUNT_CODE = Pattern.compile("^[0-9]{8}$");
    private static final int MAX_CODE_ATTEMPTS = 1_000;

    private final SecureRandom random;

    public V4__normalize_non_admin_account_codes() {
        this(new SecureRandom());
    }

    V4__normalize_non_admin_account_codes(SecureRandom random) {
        this.random = random;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        normalizeUsers(connection);
        verifyInvariant(connection);
    }

    int normalizeUsers(Connection connection) throws SQLException {
        Set<String> usedUsernames = new HashSet<>();
        List<LegacyUser> legacyUsers = new ArrayList<>();

        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, username, role FROM users FOR UPDATE"
             )) {
            while (rows.next()) {
                long id = rows.getLong("id");
                String username = rows.getString("username");
                String role = rows.getString("role");
                usedUsernames.add(username);
                if (isNonAdmin(role) && !isValidCode(username)) {
                    legacyUsers.add(new LegacyUser(id));
                }
            }
        }

        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE users SET username = ?, token_version = COALESCE(token_version, 0) + 1 WHERE id = ?"
        )) {
            for (LegacyUser user : legacyUsers) {
                update.setString(1, generateUnusedCode(usedUsernames));
                update.setLong(2, user.id());
                update.addBatch();
            }
            if (!legacyUsers.isEmpty()) {
                update.executeBatch();
            }
        }

        return legacyUsers.size();
    }

    private String generateUnusedCode(Set<String> usedUsernames) {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = Integer.toString(10_000_000 + random.nextInt(90_000_000));
            if (usedUsernames.add(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Không thể cấp mã tài khoản duy nhất khi migration");
    }

    private void verifyInvariant(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, username, role FROM users")) {
            while (rows.next()) {
                String role = rows.getString("role");
                String username = rows.getString("username");
                if (isNonAdmin(role) && !isValidCode(username)) {
                    throw new SQLException(
                            "User " + rows.getLong("id") + " vẫn có mã tài khoản không hợp lệ"
                    );
                }
            }
        }
    }

    private boolean isNonAdmin(String role) {
        return "STUDENT".equals(role) || "MANAGER".equals(role);
    }

    private boolean isValidCode(String username) {
        return username != null && ACCOUNT_CODE.matcher(username).matches();
    }

    private record LegacyUser(long id) {
    }
}
