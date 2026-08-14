package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class V5__enforce_non_admin_account_codes extends BaseJavaMigration {

    private static final String CONSTRAINT_NAME = "chk_users_non_admin_account_code";
    private static final Pattern ACCOUNT_CODE = Pattern.compile("^[0-9]{8}$");
    private static final Pattern MYSQL_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+).*$");

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        validateDatabaseSupport(connection);
        verifyNormalizedData(connection);

        ConstraintDefinition constraint = findNamedConstraint(connection);
        if (constraint != null && !isExpectedConstraint(constraint)) {
            throw new SQLException("Constraint " + CONSTRAINT_NAME + " không đúng định nghĩa bắt buộc");
        }

        if (constraint == null) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE users
                        ADD CONSTRAINT chk_users_non_admin_account_code
                        CHECK (role = 'ADMIN' OR username REGEXP '^[0-9]{8}$')
                        """);
            }
        }

        ConstraintDefinition installed = findNamedConstraint(connection);
        if (!isExpectedConstraint(installed) || !constraintIsEnforced(connection)) {
            throw new SQLException("Constraint mã tài khoản không đúng định nghĩa hoặc chưa được thực thi");
        }
    }

    void validateDatabaseSupport(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        if (isH2(product)) {
            return;
        }
        if (product == null || !product.toUpperCase(Locale.ROOT).contains("MYSQL")) {
            throw new SQLException("V5 chỉ hỗ trợ MySQL 8.0.16+; database hiện tại: " + product);
        }

        String version;
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT VERSION()")) {
            if (!rows.next()) {
                throw new SQLException("Không đọc được phiên bản MySQL");
            }
            version = rows.getString(1);
        }
        if (!isSupportedMysqlVersion(version)) {
            throw new SQLException(
                    "MySQL " + version + " không thực thi CHECK constraint; yêu cầu MySQL 8.0.16+"
            );
        }
    }

    static boolean isSupportedMysqlVersion(String version) {
        if (version == null) {
            return false;
        }
        Matcher matcher = MYSQL_VERSION.matcher(version.trim());
        if (!matcher.matches()) {
            return false;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        return major > 8 || (major == 8 && (minor > 0 || patch >= 16));
    }

    private void verifyNormalizedData(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, username, role FROM users")) {
            while (rows.next()) {
                String role = rows.getString("role");
                String username = rows.getString("username");
                if (isNonAdmin(role) && (username == null || !ACCOUNT_CODE.matcher(username).matches())) {
                    throw new SQLException(
                            "Dữ liệu user " + rows.getLong("id")
                                    + " chưa được V4 chuẩn hóa; không thể thêm constraint"
                    );
                }
            }
        }
    }

    private ConstraintDefinition findNamedConstraint(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        String schema = isH2(product) ? connection.getSchema() : connection.getCatalog();

        try (PreparedStatement query = connection.prepareStatement("""
                SELECT tc.CONSTRAINT_TYPE, cc.CHECK_CLAUSE
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                LEFT JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                  ON UPPER(cc.CONSTRAINT_NAME) = UPPER(tc.CONSTRAINT_NAME)
                 AND UPPER(cc.CONSTRAINT_SCHEMA) = UPPER(tc.CONSTRAINT_SCHEMA)
                WHERE UPPER(tc.CONSTRAINT_NAME) = UPPER(?)
                  AND UPPER(tc.TABLE_NAME) = 'USERS'
                  AND UPPER(tc.CONSTRAINT_SCHEMA) = UPPER(?)
                """)) {
            query.setString(1, CONSTRAINT_NAME);
            query.setString(2, schema);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new ConstraintDefinition(
                        rows.getString("CONSTRAINT_TYPE"),
                        rows.getString("CHECK_CLAUSE")
                );
            }
        }
    }

    private boolean isExpectedConstraint(ConstraintDefinition constraint) {
        if (constraint == null || !"CHECK".equalsIgnoreCase(constraint.type())) {
            return false;
        }

        return isExpectedCheckClause(constraint.checkClause());
    }

    static boolean isExpectedCheckClause(String checkClause) {
        if (checkClause == null) {
            return false;
        }

        String clause = checkClause
                .toLowerCase(Locale.ROOT)
                .replace("\\", "")
                .replaceAll("_[a-z0-9]+(?=')", "")
                .replace("`", "")
                .replace("\"", "")
                .replace("'", "")
                .replaceAll("\\s+", "")
                .replace("(", "")
                .replace(")", "");

        return "role=adminorusernameregexp^[0-9]{8}$".equals(clause)
                || "role=adminorregexp_likeusername,^[0-9]{8}$".equals(clause);
    }

    private boolean constraintIsEnforced(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        if (isH2(product)) {
            return true;
        }

        try (PreparedStatement query = connection.prepareStatement("""
                SELECT ENFORCED
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE UPPER(CONSTRAINT_NAME) = UPPER(?)
                  AND UPPER(TABLE_NAME) = 'USERS'
                  AND UPPER(CONSTRAINT_SCHEMA) = UPPER(?)
                  AND UPPER(CONSTRAINT_TYPE) = 'CHECK'
                """)) {
            query.setString(1, CONSTRAINT_NAME);
            query.setString(2, connection.getCatalog());
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() && "YES".equalsIgnoreCase(rows.getString("ENFORCED"));
            }
        }
    }

    private boolean isH2(String product) {
        return product != null && product.toUpperCase(Locale.ROOT).contains("H2");
    }

    private boolean isNonAdmin(String role) {
        return "STUDENT".equals(role) || "MANAGER".equals(role);
    }

    private record ConstraintDefinition(String type, String checkClause) {
    }
}
