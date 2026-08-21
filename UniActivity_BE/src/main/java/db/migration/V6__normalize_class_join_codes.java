package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class V6__normalize_class_join_codes extends BaseJavaMigration {

    private static final String LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String ALPHABET = LETTERS + DIGITS;
    private static final int CODE_LENGTH = 6;
    private static final int MAX_CODE_ATTEMPTS = 1_000;
    private static final String UNIQUE_NAME = "uk_classes_join_code";
    private static final String CHECK_NAME = "chk_classes_join_code_format";

    private static final Pattern CODE = Pattern.compile("^[" + ALPHABET + "]{" + CODE_LENGTH + "}$");
    private static final Pattern HAS_LETTER = Pattern.compile(".*[" + LETTERS + "].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*[" + DIGITS + "].*");
    private static final Pattern MYSQL_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+).*$");

    private final SecureRandom random;

    public V6__normalize_class_join_codes() {
        this(new SecureRandom());
    }

    V6__normalize_class_join_codes(SecureRandom random) {
        this.random = random;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Map<Long, String> replacements = generateReplacements(connection);
        updateAllRows(connection, replacements);
        verifyNormalizedData(connection);
        makeColumnRequired(connection);
        installUniqueConstraint(connection);
        installFormatConstraint(connection);
        verifyInstalledConstraints(connection);
    }

    private Map<Long, String> generateReplacements(Connection connection) throws SQLException {
        validateUpdateIndexSafety(connection);
        List<Long> classIds = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id FROM classes FOR UPDATE")) {
            while (rows.next()) {
                classIds.add(rows.getLong("id"));
            }
        }

        Set<String> reservedCodes = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT join_code FROM classes")) {
            while (rows.next()) {
                String joinCode = rows.getString("join_code");
                if (joinCode != null) {
                    reservedCodes.add(joinCode);
                }
            }
        }

        Map<Long, String> replacements = new LinkedHashMap<>();
        if (hasUniqueIndexContainingJoinCode(connection)) {
            try (PreparedStatement collisionQuery = connection.prepareStatement(
                    "SELECT 1 FROM classes WHERE join_code = ? LIMIT 1"
            )) {
                for (long classId : classIds) {
                    replacements.put(classId, generateUnusedCode(reservedCodes, collisionQuery));
                }
            }
        } else {
            for (long classId : classIds) {
                replacements.put(classId, generateUnusedCode(reservedCodes, null));
            }
        }
        return replacements;
    }

    private String generateUnusedCode(
            Set<String> reservedCodes,
            PreparedStatement collisionQuery
    ) throws SQLException {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            StringBuilder candidate = new StringBuilder(CODE_LENGTH);
            for (int position = 0; position < CODE_LENGTH; position++) {
                candidate.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String code = candidate.toString();
            if (isNormalizedCode(code)
                    && !reservedCodes.contains(code)
                    && !collidesWithStoredCode(collisionQuery, code)) {
                reservedCodes.add(code);
                return code;
            }
        }
        throw new SQLException("Không thể cấp mã lớp duy nhất khi migration");
    }

    private boolean collidesWithStoredCode(
            PreparedStatement collisionQuery,
            String candidate
    ) throws SQLException {
        if (collisionQuery == null) {
            return false;
        }
        collisionQuery.setString(1, candidate);
        try (ResultSet rows = collisionQuery.executeQuery()) {
            return rows.next();
        }
    }

    void validateUpdateIndexSafety(Connection connection) throws SQLException {
        if (isH2(connection)) {
            return;
        }
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT INDEX_NAME, SUB_PART
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE UPPER(TABLE_SCHEMA) = UPPER(?)
                  AND UPPER(TABLE_NAME) = 'CLASSES'
                  AND NON_UNIQUE = 0
                  AND UPPER(COLUMN_NAME) = 'JOIN_CODE'
                  AND SUB_PART IS NOT NULL
                """)) {
            query.setString(1, connection.getCatalog());
            try (ResultSet rows = query.executeQuery()) {
                if (rows.next()) {
                    throw new SQLException(
                            "Unique index " + rows.getString("INDEX_NAME")
                                    + " dùng prefix join_code; không thể xoay mã an toàn"
                    );
                }
            }
        }
    }

    private boolean hasUniqueIndexContainingJoinCode(Connection connection) throws SQLException {
        if (!isH2(connection)) {
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT 1
                    FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE UPPER(TABLE_SCHEMA) = UPPER(?)
                      AND UPPER(TABLE_NAME) = 'CLASSES'
                      AND NON_UNIQUE = 0
                      AND UPPER(COLUMN_NAME) = 'JOIN_CODE'
                    """)) {
                query.setString(1, connection.getCatalog());
                try (ResultSet rows = query.executeQuery()) {
                    return rows.next();
                }
            }
        }
        TableReference table = findClassesTable(connection);
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                table.catalog(), table.schema(), table.name(), true, false
        )) {
            while (indexes.next()) {
                if ("join_code".equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateAllRows(Connection connection, Map<Long, String> replacements) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE classes SET join_code = ? WHERE id = ?"
        )) {
            for (Map.Entry<Long, String> replacement : replacements.entrySet()) {
                update.setString(1, replacement.getValue());
                update.setLong(2, replacement.getKey());
                update.addBatch();
            }
            if (!replacements.isEmpty()) {
                update.executeBatch();
            }
        }
    }

    private void verifyNormalizedData(Connection connection) throws SQLException {
        Set<String> seenCodes = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, join_code FROM classes")) {
            while (rows.next()) {
                String joinCode = rows.getString("join_code");
                if (!isNormalizedCode(joinCode)) {
                    throw new SQLException(
                            "Dữ liệu lớp " + rows.getLong("id")
                                    + " không có mã lớp sáu ký tự hợp lệ"
                    );
                }
                if (!seenCodes.add(joinCode)) {
                    throw new SQLException("Dữ liệu lớp có mã tham gia trùng lặp: " + joinCode);
                }
            }
        }
    }

    private boolean isNormalizedCode(String code) {
        return code != null
                && CODE.matcher(code).matches()
                && HAS_LETTER.matcher(code).matches()
                && HAS_DIGIT.matcher(code).matches();
    }

    private void makeColumnRequired(Connection connection) throws SQLException {
        String sql = isH2(connection)
                ? "ALTER TABLE classes ALTER COLUMN join_code VARCHAR(6) NOT NULL"
                : "ALTER TABLE classes MODIFY COLUMN join_code VARCHAR(6) NOT NULL";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void installUniqueConstraint(Connection connection) throws SQLException {
        if (hasUniqueIndexOnJoinCode(connection)) {
            return;
        }
        if (indexNameExists(connection, UNIQUE_NAME)) {
            throw new SQLException("Index " + UNIQUE_NAME + " không bảo vệ riêng cột join_code");
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE classes
                    ADD CONSTRAINT uk_classes_join_code UNIQUE (join_code)
                    """);
        }
    }

    private void installFormatConstraint(Connection connection) throws SQLException {
        validateDatabaseSupport(connection);
        if (findReusableFormatConstraint(connection) != null) {
            return;
        }

        String sql = isH2(connection)
                ? """
                  ALTER TABLE classes
                  ADD CONSTRAINT chk_classes_join_code_format
                  CHECK (REGEXP_LIKE(join_code, '^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$')
                    AND REGEXP_LIKE(join_code, '[ABCDEFGHJKMNPQRSTUVWXYZ]')
                    AND REGEXP_LIKE(join_code, '[23456789]'))
                  """
                : """
                  ALTER TABLE classes
                  ADD CONSTRAINT chk_classes_join_code_format
                  CHECK (join_code REGEXP '(?-i)^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$'
                    AND join_code REGEXP '(?-i)[ABCDEFGHJKMNPQRSTUVWXYZ]'
                    AND join_code REGEXP '(?-i)[23456789]')
                  """;
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    void validateDatabaseSupport(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        if (isH2(product)) {
            return;
        }
        if (product == null || !product.toUpperCase(Locale.ROOT).contains("MYSQL")) {
            throw new SQLException("V6 chỉ hỗ trợ MySQL 8.0.16+; database hiện tại: " + product);
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

    private void verifyInstalledConstraints(Connection connection) throws SQLException {
        verifyNormalizedData(connection);
        if (!hasExpectedColumnDefinition(connection)) {
            throw new SQLException("Cột classes.join_code phải là VARCHAR(6) NOT NULL");
        }
        if (!hasUniqueIndexOnJoinCode(connection)) {
            throw new SQLException("Cột classes.join_code chưa có unique constraint");
        }

        if (findReusableFormatConstraint(connection) == null) {
            throw new SQLException("Constraint mã lớp không đúng định nghĩa hoặc chưa được thực thi");
        }
    }

    private CheckConstraint findReusableFormatConstraint(Connection connection) throws SQLException {
        CheckConstraint reusable = null;
        for (CheckConstraint constraint : findCheckConstraints(connection)) {
            boolean expected = isExpectedCheckClause(connection, constraint.checkClause());
            boolean enforced = isH2(connection)
                    ? expected
                    : isReusableMysqlCheckConstraint(
                            constraint.checkClause(), constraint.enforced()
                    );
            if (CHECK_NAME.equalsIgnoreCase(constraint.name())) {
                if (!expected) {
                    throw new SQLException(
                            "Constraint " + CHECK_NAME + " không đúng định nghĩa bắt buộc"
                    );
                }
                if (!enforced) {
                    throw new SQLException("Constraint " + CHECK_NAME + " chưa được thực thi");
                }
            }
            if (expected && enforced && reusable == null) {
                reusable = constraint;
            }
        }
        return reusable;
    }

    private boolean hasExpectedColumnDefinition(Connection connection) throws SQLException {
        TableReference table = findClassesTable(connection);
        try (ResultSet columns = connection.getMetaData().getColumns(
                table.catalog(), table.schema(), table.name(), null
        )) {
            while (columns.next()) {
                if ("join_code".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return isExpectedJoinCodeColumn(
                            columns.getInt("NULLABLE"),
                            columns.getInt("COLUMN_SIZE"),
                            columns.getInt("DATA_TYPE"),
                            columns.getString("TYPE_NAME")
                    );
                }
            }
        }
        throw new SQLException("Không tìm thấy cột classes.join_code");
    }

    static boolean isExpectedJoinCodeColumn(
            int nullable,
            int size,
            int dataType,
            String typeName
    ) {
        if (typeName == null) {
            return false;
        }
        String normalizedType = typeName.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return nullable == DatabaseMetaData.columnNoNulls
                && size == CODE_LENGTH
                && dataType == Types.VARCHAR
                && ("VARCHAR".equals(normalizedType)
                || "CHARACTER VARYING".equals(normalizedType));
    }

    private boolean hasUniqueIndexOnJoinCode(Connection connection) throws SQLException {
        if (!isH2(connection)) {
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT s.INDEX_NAME
                    FROM INFORMATION_SCHEMA.STATISTICS s
                    WHERE UPPER(s.TABLE_SCHEMA) = UPPER(?)
                      AND UPPER(s.TABLE_NAME) = 'CLASSES'
                      AND s.NON_UNIQUE = 0
                    GROUP BY s.INDEX_NAME
                    HAVING COUNT(*) = 1
                       AND SUM(CASE
                           WHEN UPPER(s.COLUMN_NAME) = 'JOIN_CODE' AND s.SUB_PART IS NULL THEN 1
                           ELSE 0
                       END) = 1
                    """)) {
                query.setString(1, connection.getCatalog());
                try (ResultSet rows = query.executeQuery()) {
                    return rows.next();
                }
            }
        }
        TableReference table = findClassesTable(connection);
        Map<String, List<String>> indexColumns = new HashMap<>();
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                table.catalog(), table.schema(), table.name(), true, false
        )) {
            while (indexes.next()) {
                if (indexes.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }
                String indexName = indexes.getString("INDEX_NAME");
                String columnName = indexes.getString("COLUMN_NAME");
                if (indexName != null && columnName != null) {
                    indexColumns.computeIfAbsent(indexName, ignored -> new ArrayList<>()).add(columnName);
                }
            }
        }
        return indexColumns.values().stream()
                .anyMatch(columns -> columns.size() == 1 && "join_code".equalsIgnoreCase(columns.getFirst()));
    }

    private boolean indexNameExists(Connection connection, String expectedName) throws SQLException {
        if (!isH2(connection)) {
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT 1
                    FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE UPPER(TABLE_SCHEMA) = UPPER(?)
                      AND UPPER(TABLE_NAME) = 'CLASSES'
                      AND UPPER(INDEX_NAME) = UPPER(?)
                    """)) {
                query.setString(1, connection.getCatalog());
                query.setString(2, expectedName);
                try (ResultSet rows = query.executeQuery()) {
                    return rows.next();
                }
            }
        }
        TableReference table = findClassesTable(connection);
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                table.catalog(), table.schema(), table.name(), false, false
        )) {
            while (indexes.next()) {
                if (expectedName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private TableReference findClassesTable(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = isH2(connection) ? connection.getSchema() : null;
        try (ResultSet tables = metadata.getTables(catalog, schema, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                if ("classes".equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return new TableReference(
                            tables.getString("TABLE_CAT"),
                            tables.getString("TABLE_SCHEM"),
                            tables.getString("TABLE_NAME")
                    );
                }
            }
        }
        throw new SQLException("Không tìm thấy bảng classes");
    }

    private List<CheckConstraint> findCheckConstraints(Connection connection) throws SQLException {
        String schema = informationSchemaName(connection);
        List<CheckConstraint> constraints = new ArrayList<>();
        String enforcedExpression = isH2(connection) ? "'YES'" : "tc.ENFORCED";
        String sql = """
                SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE, %s AS ENFORCED
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                LEFT JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                  ON UPPER(cc.CONSTRAINT_NAME) = UPPER(tc.CONSTRAINT_NAME)
                 AND UPPER(cc.CONSTRAINT_SCHEMA) = UPPER(tc.CONSTRAINT_SCHEMA)
                WHERE UPPER(tc.TABLE_NAME) = 'CLASSES'
                  AND UPPER(tc.CONSTRAINT_SCHEMA) = UPPER(?)
                  AND UPPER(tc.CONSTRAINT_TYPE) = 'CHECK'
                """.formatted(enforcedExpression);
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, schema);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    constraints.add(new CheckConstraint(
                            rows.getString("CONSTRAINT_NAME"),
                            rows.getString("CHECK_CLAUSE"),
                            rows.getString("ENFORCED")
                    ));
                }
            }
        }
        return constraints;
    }

    static boolean isExpectedCheckClause(String checkClause) {
        String normalized = normalizeCheckClause(checkClause);
        if (normalized == null) {
            return false;
        }

        String alphabetPattern = "^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$";
        String lettersPattern = "[ABCDEFGHJKMNPQRSTUVWXYZ]";
        String digitsPattern = "[23456789]";
        String inlineCaseFlag = "(?-i)";
        String mysqlRegexpOperator = "join_coderegexp" + inlineCaseFlag + alphabetPattern
                + "andjoin_coderegexp" + inlineCaseFlag + lettersPattern
                + "andjoin_coderegexp" + inlineCaseFlag + digitsPattern;
        String mysqlRenderedOperator = "regexp_likejoin_code," + inlineCaseFlag + alphabetPattern
                + "andregexp_likejoin_code," + inlineCaseFlag + lettersPattern
                + "andregexp_likejoin_code," + inlineCaseFlag + digitsPattern;
        String mysqlExplicitCase = "regexp_likejoin_code," + alphabetPattern + ",c"
                + "andregexp_likejoin_code," + lettersPattern + ",c"
                + "andregexp_likejoin_code," + digitsPattern + ",c";
        return mysqlRegexpOperator.equals(normalized)
                || mysqlRenderedOperator.equals(normalized)
                || mysqlExplicitCase.equals(normalized);
    }

    static boolean isReusableMysqlCheckConstraint(String checkClause, String enforced) {
        return isExpectedCheckClause(checkClause) && "YES".equalsIgnoreCase(enforced);
    }

    private boolean isExpectedCheckClause(Connection connection, String checkClause) throws SQLException {
        if (!isH2(connection)) {
            return isExpectedCheckClause(checkClause);
        }
        return isExpectedH2CheckClause(checkClause);
    }

    static boolean isExpectedH2CheckClause(String checkClause) {
        String normalized = normalizeCheckClause(checkClause);
        if (normalized == null) {
            return false;
        }
        String alphabetPattern = "^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$";
        String lettersPattern = "[ABCDEFGHJKMNPQRSTUVWXYZ]";
        String digitsPattern = "[23456789]";
        return ("regexp_likejoin_code," + alphabetPattern
                + "andregexp_likejoin_code," + lettersPattern
                + "andregexp_likejoin_code," + digitsPattern).equals(normalized);
    }

    private static String normalizeCheckClause(String checkClause) {
        if (checkClause == null) {
            return null;
        }
        StringBuilder normalized = new StringBuilder(checkClause.length());
        boolean inLiteral = false;
        for (int index = 0; index < checkClause.length(); index++) {
            char character = checkClause.charAt(index);
            if (inLiteral) {
                if (character == '\'') {
                    if (index + 1 < checkClause.length() && checkClause.charAt(index + 1) == '\'') {
                        normalized.append('\'');
                        index++;
                    } else {
                        inLiteral = false;
                    }
                } else {
                    normalized.append(character);
                }
                continue;
            }

            if (character == '\'') {
                removeCharsetIntroducer(normalized);
                inLiteral = true;
            } else if (character != '`'
                    && character != '"'
                    && character != '('
                    && character != ')'
                    && !Character.isWhitespace(character)) {
                normalized.append(Character.toLowerCase(character));
            }
        }
        return inLiteral ? null : normalized.toString();
    }

    private static void removeCharsetIntroducer(StringBuilder normalized) {
        int end = normalized.length();
        int start = end;
        while (start > 0 && Character.isLetterOrDigit(normalized.charAt(start - 1))) {
            start--;
        }
        if (start > 0 && normalized.charAt(start - 1) == '_') {
            normalized.delete(start - 1, end);
        }
    }

    private String informationSchemaName(Connection connection) throws SQLException {
        return isH2(connection) ? connection.getSchema() : connection.getCatalog();
    }

    private boolean isH2(Connection connection) throws SQLException {
        return isH2(connection.getMetaData().getDatabaseProductName());
    }

    private boolean isH2(String product) {
        return product != null && product.toUpperCase(Locale.ROOT).contains("H2");
    }

    private record TableReference(String catalog, String schema, String name) {
    }

    private record CheckConstraint(String name, String checkClause, String enforced) {
    }
}
