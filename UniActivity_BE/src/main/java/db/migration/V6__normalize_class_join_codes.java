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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class V6__normalize_class_join_codes extends BaseJavaMigration {

    /*
     * Restart protocol:
     * 1. PRE_CUTOVER: join_code is untouched and authoritative; replacements live in join_code_v6.
     * 2. POST_CUTOVER: one atomic MySQL ALTER swaps the columns and installs final constraints.
     * 3. COMPLETE: the legacy column is gone and the durable marker prevents an accidental rerotation
     *    if Flyway has to invoke V6 again after the database committed its non-transactional DDL.
     */

    private static final String LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String ALPHABET = LETTERS + DIGITS;
    private static final int CODE_LENGTH = 6;
    private static final int MAX_CODE_ATTEMPTS = 1_000;

    private static final String UNIQUE_NAME = "uk_classes_join_code";
    private static final String CHECK_NAME = "chk_classes_join_code_format";
    private static final String ROTATION_CHECK_NAME = "chk_classes_join_code_v6_rotated";
    private static final String SHADOW_COLUMN = "join_code_v6";
    private static final String LEGACY_COLUMN = "join_code_v6_legacy";
    private static final String STATE_TABLE = "uniactivity_v6_class_join_code_state";
    private static final String STATE_KEY = "classes.join_code";
    private static final String PHASE_PREPARING = "PREPARING";
    private static final String PHASE_CUTOVER = "CUTOVER";

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
    public boolean canExecuteInTransaction() {
        // MySQL DDL implicitly commits. This migration owns a restartable state machine instead.
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Preflight preflight = preflight(connection);

        switch (preflight.state()) {
            case COMPLETE -> verifyFinalState(connection);
            case POST_CUTOVER -> finishPostCutover(connection);
            case H2_CUTOVER_IN_PROGRESS -> finishInterruptedH2Cutover(connection);
            case PRE_CUTOVER -> prepareAndCutOver(connection, preflight);
        }
    }

    private Preflight preflight(Connection connection) throws SQLException {
        return preflight(connection, true);
    }

    private Preflight preflight(Connection connection, boolean validateWriteLock)
            throws SQLException {
        validateDatabaseSupport(connection);
        validateAtomicDdlEngine(connection);

        Marker marker = readMarker(connection);
        Map<String, ColumnDefinition> columns = loadColumns(connection, "classes");
        MigrationState state = classifyState(connection, marker, columns);

        validateForeignKeySafety(connection);
        validatePrimaryKeySafety(connection);
        validateColumnState(connection, state, columns);
        validateManagedIndexes(connection, state);
        validateConstraintNameOwnership(connection);
        List<CheckConstraint> checks = findCheckConstraints(connection);
        List<String> oldFormatChecks = validateManagedChecks(connection, state, checks);

        boolean fixedUniqueOnAuthority = hasNamedUniqueIndexOnColumn(
                connection, UNIQUE_NAME, authoritativeColumn(state)
        );
        if (indexNameExists(connection, UNIQUE_NAME) && !fixedUniqueOnAuthority) {
            throw new SQLException(
                    "Index " + UNIQUE_NAME + " không bảo vệ riêng cột join_code đang có hiệu lực"
            );
        }

        String reusableCheckName = oldFormatChecks.isEmpty()
                ? CHECK_NAME
                : oldFormatChecks.getFirst();
        if (validateWriteLock && !isH2(connection)) {
            withMysqlWriteLock(
                    connection,
                    "LOCK TABLES classes WRITE",
                    () -> { }
            );
        }
        return new Preflight(
                state,
                List.copyOf(oldFormatChecks),
                fixedUniqueOnAuthority,
                reusableCheckName
        );
    }

    private void prepareAndCutOver(Connection connection, Preflight preflight) throws SQLException {
        ensurePreparingMarker(connection);

        if (isH2(connection)) {
            removeInterruptedShadow(connection);
            addShadowColumn(connection);
            Map<Long, String> replacements = generateReplacements(connection);
            updateShadowColumn(connection, replacements);
            verifyPreparedData(connection);
            performH2Cutover(connection, preflight);
        } else {
            withMysqlClassesWriteLock(connection, () -> {
                removeInterruptedShadow(connection);
                addShadowColumn(connection);
            }, () -> {
                Preflight lockedPreflight = preflight(connection, false);
                Map<Long, String> replacements = generateReplacements(connection);
                updateShadowColumn(connection, replacements);
                verifyPreparedData(connection);
                performMysqlAtomicCutover(connection, lockedPreflight);
            });
        }

        writePhase(connection, PHASE_CUTOVER);
        cleanupLegacyColumn(connection);
        verifyFinalState(connection);
    }

    void withMysqlClassesWriteLock(
            Connection connection,
            MysqlLockedWork stagingColumnDdl,
            MysqlLockedWork protectedWork
    ) throws SQLException {
        stagingColumnDdl.execute();
        withMysqlWriteLock(
                connection,
                "LOCK TABLES classes WRITE, classes AS prepared WRITE, "
                        + "classes AS legacy WRITE, " + STATE_TABLE + " WRITE",
                protectedWork
        );
    }

    private void withMysqlWriteLock(
            Connection connection,
            String lockSql,
            MysqlLockedWork work
    ) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        boolean autoCommitChanged = false;
        boolean locked = false;
        SQLException failure = null;
        try {
            if (originalAutoCommit) {
                connection.setAutoCommit(false);
                autoCommitChanged = true;
            }
            execute(connection, lockSql);
            locked = true;
            work.execute();
            connection.commit();
        } catch (SQLException exception) {
            failure = exception;
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        } finally {
            if (locked) {
                try {
                    execute(connection, "UNLOCK TABLES");
                } catch (SQLException unlockFailure) {
                    if (failure == null) {
                        failure = unlockFailure;
                    } else {
                        failure.addSuppressed(unlockFailure);
                    }
                }
            }
            if (autoCommitChanged) {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException restoreFailure) {
                    if (failure == null) {
                        failure = restoreFailure;
                    } else {
                        failure.addSuppressed(restoreFailure);
                    }
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void finishPostCutover(Connection connection) throws SQLException {
        if (isH2(connection)) {
            completeH2FinalSchema(connection);
        }
        verifyCutoverData(connection);
        writePhase(connection, PHASE_CUTOVER);
        cleanupLegacyColumn(connection);
        verifyFinalState(connection);
    }

    private void finishInterruptedH2Cutover(Connection connection) throws SQLException {
        execute(connection, """
                ALTER TABLE classes
                RENAME COLUMN join_code_v6 TO join_code
                """);
        completeH2FinalSchema(connection);
        verifyCutoverData(connection);
        writePhase(connection, PHASE_CUTOVER);
        cleanupLegacyColumn(connection);
        verifyFinalState(connection);
    }

    private void ensurePreparingMarker(Connection connection) throws SQLException {
        if (findTable(connection, STATE_TABLE).isEmpty()) {
            execute(connection, markerTableDdl(!isH2(connection)));
        }
        writePhase(connection, PHASE_PREPARING);
    }

    static String markerTableDdl(boolean mysql) {
        String ddl = """
                CREATE TABLE uniactivity_v6_class_join_code_state (
                    migration_key VARCHAR(64) NOT NULL PRIMARY KEY,
                    phase VARCHAR(16) NOT NULL
                )
                """.strip();
        return mysql ? ddl + " ENGINE=InnoDB" : ddl;
    }

    private void writePhase(Connection connection, String phase) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE uniactivity_v6_class_join_code_state
                SET phase = ?
                WHERE migration_key = ?
                """)) {
            update.setString(1, phase);
            update.setString(2, STATE_KEY);
            if (update.executeUpdate() == 1) {
                return;
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO uniactivity_v6_class_join_code_state (migration_key, phase)
                VALUES (?, ?)
                """)) {
            insert.setString(1, STATE_KEY);
            insert.setString(2, phase);
            insert.executeUpdate();
        }
    }

    private Marker readMarker(Connection connection) throws SQLException {
        Optional<TableReference> stateTable = findTable(connection, STATE_TABLE);
        if (stateTable.isEmpty()) {
            return new Marker(false, null);
        }

        validateStateTableDefinition(connection);

        String phase = null;
        int rowCount = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT migration_key, phase FROM " + STATE_TABLE
             )) {
            while (rows.next()) {
                rowCount++;
                String key = rows.getString("migration_key");
                if (!STATE_KEY.equals(key)) {
                    throw new SQLException("Bảng trạng thái " + STATE_TABLE + " chứa khóa không xác định");
                }
                phase = rows.getString("phase");
            }
        }
        if (rowCount > 1) {
            throw new SQLException("Bảng trạng thái " + STATE_TABLE + " chứa nhiều bản ghi");
        }
        if (phase != null
                && !PHASE_PREPARING.equals(phase)
                && !PHASE_CUTOVER.equals(phase)) {
            throw new SQLException("Trạng thái V6 không xác định: " + phase);
        }
        return new Marker(true, phase);
    }

    private void validateStateTableDefinition(Connection connection) throws SQLException {
        if (!isH2(connection)) {
            String engine = mysqlTableEngine(connection, STATE_TABLE);
            if (!"INNODB".equalsIgnoreCase(engine)) {
                throw new SQLException(
                        "Bảng trạng thái " + STATE_TABLE + " phải dùng InnoDB; hiện tại: " + engine
                );
            }
        }

        Map<String, ColumnDefinition> columns = loadColumns(connection, STATE_TABLE);
        if (!columns.keySet().equals(Set.of("migration_key", "phase"))
                || !isRequiredVarchar(columns.get("migration_key"), 64)
                || !isRequiredVarchar(columns.get("phase"), 16)) {
            throw new SQLException("Bảng trạng thái " + STATE_TABLE + " có cấu trúc không tương thích");
        }
        if (!loadPrimaryKeyColumns(connection, STATE_TABLE).equals(List.of("migration_key"))) {
            throw new SQLException(
                    "Bảng trạng thái " + STATE_TABLE + " phải có khóa chính migration_key"
            );
        }
    }

    private MigrationState classifyState(
            Connection connection,
            Marker marker,
            Map<String, ColumnDefinition> columns
    ) throws SQLException {
        boolean joinCode = columns.containsKey("join_code");
        boolean shadow = columns.containsKey(SHADOW_COLUMN);
        boolean legacy = columns.containsKey(LEGACY_COLUMN);

        if (!marker.exists() && (shadow || legacy)) {
            throw new SQLException("Phát hiện cột staging V6 nhưng không có bảng trạng thái V6");
        }
        if (joinCode && !shadow && !legacy) {
            return PHASE_CUTOVER.equals(marker.phase())
                    ? MigrationState.COMPLETE
                    : MigrationState.PRE_CUTOVER;
        }
        if (joinCode && shadow && !legacy
                && marker.exists()
                && !PHASE_CUTOVER.equals(marker.phase())) {
            return MigrationState.PRE_CUTOVER;
        }
        if (joinCode && !shadow && legacy && marker.exists()) {
            return MigrationState.POST_CUTOVER;
        }
        if (!joinCode && shadow && legacy && marker.exists() && isH2(connection)) {
            return MigrationState.H2_CUTOVER_IN_PROGRESS;
        }
        throw new SQLException(
                "Trạng thái cột V6 không thể phục hồi: join_code=" + joinCode
                        + ", shadow=" + shadow + ", legacy=" + legacy
        );
    }

    private void validateColumnState(
            Connection connection,
            MigrationState state,
            Map<String, ColumnDefinition> columns
    ) throws SQLException {
        switch (state) {
            case PRE_CUTOVER -> {
                if (!isCompatibleLegacyJoinCode(columns.get("join_code"), connection)) {
                    throw new SQLException("Cột classes.join_code phải là VARCHAR có ít nhất 6 ký tự");
                }
                if (columns.containsKey(SHADOW_COLUMN)
                        && !isExpectedShadowColumn(columns.get(SHADOW_COLUMN))) {
                    throw new SQLException("Cột staging " + SHADOW_COLUMN + " không đúng VARCHAR(6)");
                }
            }
            case H2_CUTOVER_IN_PROGRESS -> {
                if (!isCompatibleLegacyJoinCode(columns.get(LEGACY_COLUMN), connection)
                        || !isExpectedShadowColumn(columns.get(SHADOW_COLUMN))) {
                    throw new SQLException("Cột staging V6 đang dở có định nghĩa không tương thích");
                }
            }
            case POST_CUTOVER -> {
                if (!isCompatibleFinalCandidate(columns.get("join_code"))
                        || !isCompatibleLegacyJoinCode(columns.get(LEGACY_COLUMN), connection)) {
                    throw new SQLException("Cột V6 sau cutover có định nghĩa không tương thích");
                }
            }
            case COMPLETE -> {
                ColumnDefinition joinCode = columns.get("join_code");
                if (!isExpectedJoinCodeColumn(
                        joinCode.nullable(), joinCode.size(), joinCode.dataType(), joinCode.typeName()
                )) {
                    throw new SQLException("Cột classes.join_code phải là VARCHAR(6) NOT NULL");
                }
            }
        }
    }

    private boolean isCompatibleLegacyJoinCode(
            ColumnDefinition column,
            Connection connection
    ) throws SQLException {
        if (column == null || column.size() < CODE_LENGTH || column.dataType() != Types.VARCHAR) {
            return false;
        }
        String type = normalizeType(column.typeName());
        return "VARCHAR".equals(type)
                || "CHARACTER VARYING".equals(type)
                || (isH2(connection) && "VARCHAR_IGNORECASE".equals(type));
    }

    private boolean isExpectedShadowColumn(ColumnDefinition column) {
        return column != null
                && column.size() == CODE_LENGTH
                && column.dataType() == Types.VARCHAR
                && ("VARCHAR".equals(normalizeType(column.typeName()))
                || "CHARACTER VARYING".equals(normalizeType(column.typeName())));
    }

    private boolean isCompatibleFinalCandidate(ColumnDefinition column) {
        return isExpectedShadowColumn(column);
    }

    private boolean isRequiredVarchar(ColumnDefinition column, int size) {
        return column != null
                && column.nullable() == DatabaseMetaData.columnNoNulls
                && column.size() == size
                && column.dataType() == Types.VARCHAR
                && ("VARCHAR".equals(normalizeType(column.typeName()))
                || "CHARACTER VARYING".equals(normalizeType(column.typeName())));
    }

    private String normalizeType(String typeName) {
        return typeName == null
                ? ""
                : typeName.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private void validateAtomicDdlEngine(Connection connection) throws SQLException {
        if (isH2(connection)) {
            return;
        }
        String engine = mysqlTableEngine(connection, "classes");
        if (!"INNODB".equalsIgnoreCase(engine)) {
            throw new SQLException(
                    "V6 yêu cầu bảng classes dùng InnoDB để cutover DDL nguyên tử; hiện tại: "
                            + engine
            );
        }
    }

    private String mysqlTableEngine(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT ENGINE
                FROM INFORMATION_SCHEMA.TABLES
                WHERE UPPER(TABLE_SCHEMA) = UPPER(?)
                  AND UPPER(TABLE_NAME) = UPPER(?)
                """)) {
            query.setString(1, connection.getCatalog());
            query.setString(2, tableName);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException(
                            "Không tìm thấy bảng " + tableName + " trong INFORMATION_SCHEMA"
                    );
                }
                return rows.getString("ENGINE");
            }
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
                  AND UPPER(COLUMN_NAME) = 'JOIN_CODE'
                  AND SUB_PART IS NOT NULL
                """)) {
            query.setString(1, connection.getCatalog());
            try (ResultSet rows = query.executeQuery()) {
                if (rows.next()) {
                    throw new SQLException(
                            "Index " + rows.getString("INDEX_NAME")
                                    + " dùng prefix join_code; không thể cutover an toàn"
                    );
                }
            }
        }
    }

    private void validateManagedIndexes(Connection connection, MigrationState state)
            throws SQLException {
        validateUpdateIndexSafety(connection);
        String authority = authoritativeColumn(state);
        for (IndexDefinition index : loadIndexes(connection)) {
            boolean managed = index.columns().stream().anyMatch(column ->
                    "join_code".equalsIgnoreCase(column)
                            || SHADOW_COLUMN.equalsIgnoreCase(column)
                            || LEGACY_COLUMN.equalsIgnoreCase(column)
            );
            if (!managed) {
                continue;
            }
            boolean exactManagedUnique = index.unique()
                    && index.columns().size() == 1
                    && (index.columns().getFirst().equalsIgnoreCase(authority)
                    || index.columns().getFirst().equalsIgnoreCase(LEGACY_COLUMN));
            if (!exactManagedUnique) {
                throw new SQLException(
                        "Index " + index.name()
                                + " dùng cột join_code theo định nghĩa không thể bảo toàn khi cutover"
                );
            }
        }
    }

    private void validateForeignKeySafety(Connection connection) throws SQLException {
        TableReference classes = findClassesTable(connection);
        DatabaseMetaData metadata = connection.getMetaData();

        try (ResultSet foreignKeys = metadata.getImportedKeys(
                classes.catalog(), classes.schema(), classes.name()
        )) {
            while (foreignKeys.next()) {
                String column = foreignKeys.getString("FKCOLUMN_NAME");
                if (isManagedJoinCodeColumn(column)) {
                    throw foreignKeyDependency(
                            foreignKeys.getString("FK_NAME"),
                            classes.name(),
                            column
                    );
                }
            }
        }

        try (ResultSet foreignKeys = metadata.getExportedKeys(
                classes.catalog(), classes.schema(), classes.name()
        )) {
            while (foreignKeys.next()) {
                String column = foreignKeys.getString("PKCOLUMN_NAME");
                if (isManagedJoinCodeColumn(column)) {
                    throw foreignKeyDependency(
                            foreignKeys.getString("FK_NAME"),
                            foreignKeys.getString("FKTABLE_NAME"),
                            foreignKeys.getString("FKCOLUMN_NAME")
                    );
                }
            }
        }
    }

    private void validatePrimaryKeySafety(Connection connection) throws SQLException {
        for (String column : loadPrimaryKeyColumns(connection, "classes")) {
            if (isManagedJoinCodeColumn(column)) {
                throw new SQLException(
                        "Khóa chính classes không được phụ thuộc cột " + column
                                + " trong cutover V6"
                );
            }
        }
    }

    private boolean isManagedJoinCodeColumn(String column) {
        return column != null && ("join_code".equalsIgnoreCase(column)
                || SHADOW_COLUMN.equalsIgnoreCase(column)
                || LEGACY_COLUMN.equalsIgnoreCase(column));
    }

    private SQLException foreignKeyDependency(String name, String table, String column) {
        String displayName = name == null ? "<không tên>" : name;
        return new SQLException(
                "Foreign key " + displayName + " trên " + table + "." + column
                        + " phụ thuộc classes.join_code; phải gỡ hoặc di trú khóa ngoại trước V6"
        );
    }

    private String authoritativeColumn(MigrationState state) {
        return state == MigrationState.H2_CUTOVER_IN_PROGRESS
                ? LEGACY_COLUMN
                : "join_code";
    }

    private List<IndexDefinition> loadIndexes(Connection connection) throws SQLException {
        if (!isH2(connection)) {
            Map<String, MutableIndex> indexes = new LinkedHashMap<>();
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME, SEQ_IN_INDEX
                    FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE UPPER(TABLE_SCHEMA) = UPPER(?)
                      AND UPPER(TABLE_NAME) = 'CLASSES'
                    ORDER BY INDEX_NAME, SEQ_IN_INDEX
                    """)) {
                query.setString(1, connection.getCatalog());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        String name = rows.getString("INDEX_NAME");
                        MutableIndex index = indexes.computeIfAbsent(
                                name.toLowerCase(Locale.ROOT),
                                ignored -> new MutableIndex(name, rowsBoolean(rows, "NON_UNIQUE"))
                        );
                        index.columns().add(rows.getString("COLUMN_NAME"));
                    }
                }
            }
            return indexes.values().stream()
                    .map(index -> new IndexDefinition(
                            index.name(), !index.nonUnique(), List.copyOf(index.columns())
                    ))
                    .toList();
        }

        TableReference table = findClassesTable(connection);
        Map<String, MutableIndex> indexes = new LinkedHashMap<>();
        try (ResultSet rows = connection.getMetaData().getIndexInfo(
                table.catalog(), table.schema(), table.name(), false, false
        )) {
            while (rows.next()) {
                if (rows.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }
                String name = rows.getString("INDEX_NAME");
                String column = rows.getString("COLUMN_NAME");
                if (name == null || column == null) {
                    continue;
                }
                MutableIndex index = indexes.computeIfAbsent(
                        name.toLowerCase(Locale.ROOT),
                        ignored -> new MutableIndex(name, rowsBoolean(rows, "NON_UNIQUE"))
                );
                index.columns().add(column);
            }
        }
        return indexes.values().stream()
                .map(index -> new IndexDefinition(
                        index.name(), !index.nonUnique(), List.copyOf(index.columns())
                ))
                .toList();
    }

    private boolean rowsBoolean(ResultSet rows, String column) {
        try {
            return rows.getBoolean(column);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void validateConstraintNameOwnership(Connection connection) throws SQLException {
        for (String name : List.of(CHECK_NAME, ROTATION_CHECK_NAME)) {
            Optional<String> owner = checkConstraintOwner(connection, name);
            if (owner.isPresent() && !"classes".equalsIgnoreCase(owner.get())) {
                throw new SQLException(
                        "Constraint " + name + " đã được dùng bởi bảng " + owner.get()
                );
            }
        }
    }

    private Optional<String> checkConstraintOwner(Connection connection, String name)
            throws SQLException {
        String schema = informationSchemaName(connection);
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT TABLE_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE UPPER(CONSTRAINT_SCHEMA) = UPPER(?)
                  AND UPPER(CONSTRAINT_NAME) = UPPER(?)
                  AND UPPER(CONSTRAINT_TYPE) = 'CHECK'
                """)) {
            query.setString(1, schema);
            query.setString(2, name);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? Optional.ofNullable(rows.getString("TABLE_NAME")) : Optional.empty();
            }
        }
    }

    private List<String> validateManagedChecks(
            Connection connection,
            MigrationState state,
            List<CheckConstraint> checks
    ) throws SQLException {
        List<String> oldFormatChecks = new ArrayList<>();
        for (CheckConstraint check : checks) {
            boolean enforced = isH2(connection) || "YES".equalsIgnoreCase(check.enforced());
            boolean referencesJoin = referencesColumn(check.checkClause(), "join_code");
            boolean referencesShadow = referencesColumn(check.checkClause(), SHADOW_COLUMN);
            boolean referencesLegacy = referencesColumn(check.checkClause(), LEGACY_COLUMN);

            if (referencesShadow) {
                throw new SQLException(
                        "Constraint " + check.name() + " tham chiếu cột staging " + SHADOW_COLUMN
                );
            }
            if (state == MigrationState.PRE_CUTOVER && referencesJoin) {
                if (!isExpectedCheckClause(connection, check.checkClause()) || !enforced) {
                    String displayName = CHECK_NAME.equalsIgnoreCase(check.name())
                            ? CHECK_NAME
                            : check.name();
                    throw new SQLException(
                            "Constraint " + displayName
                                    + " tham chiếu join_code nhưng không đúng định nghĩa bắt buộc"
                    );
                }
                oldFormatChecks.add(check.name());
                continue;
            }
            if (referencesLegacy) {
                if (!ROTATION_CHECK_NAME.equalsIgnoreCase(check.name())
                        || !isExpectedRotationClause(check.checkClause())
                        || !enforced) {
                    throw new SQLException(
                            "Constraint " + check.name()
                                    + " tham chiếu mã legacy theo định nghĩa không thể phục hồi"
                    );
                }
                continue;
            }
            if (referencesJoin) {
                if (!isExpectedCheckClause(connection, check.checkClause()) || !enforced) {
                    throw new SQLException(
                            "Constraint " + check.name()
                                    + " trên join_code không đúng định nghĩa bắt buộc"
                    );
                }
                continue;
            }
            if (CHECK_NAME.equalsIgnoreCase(check.name())
                    || ROTATION_CHECK_NAME.equalsIgnoreCase(check.name())) {
                throw new SQLException(
                        "Constraint " + check.name() + " dùng tên dành riêng của migration V6"
                );
            }
        }

        Optional<CheckConstraint> fixed = checks.stream()
                .filter(check -> CHECK_NAME.equalsIgnoreCase(check.name()))
                .findFirst();
        if (fixed.isPresent()
                && !isExpectedCheckClause(connection, fixed.get().checkClause())) {
            throw new SQLException("Constraint " + CHECK_NAME + " không đúng định nghĩa bắt buộc");
        }
        return oldFormatChecks;
    }

    private boolean referencesColumn(String clause, String column) {
        if (clause == null) {
            return false;
        }
        Pattern identifier = Pattern.compile(
                "(?i)(?<![A-Z0-9_$])(?:[`\"])?"
                        + Pattern.quote(column)
                        + "(?:[`\"])?(?![A-Z0-9_$])"
        );
        return identifier.matcher(clause).find();
    }

    private void removeInterruptedShadow(Connection connection) throws SQLException {
        if (loadColumns(connection, "classes").containsKey(SHADOW_COLUMN)) {
            execute(connection, "ALTER TABLE classes DROP COLUMN " + SHADOW_COLUMN);
        }
    }

    private void addShadowColumn(Connection connection) throws SQLException {
        execute(connection, """
                ALTER TABLE classes
                ADD COLUMN join_code_v6 VARCHAR(6) NULL
                """);
    }

    private Map<Long, String> generateReplacements(Connection connection) throws SQLException {
        List<ClassCode> classes = new ArrayList<>();
        Set<String> reservedCodes = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, join_code FROM classes FOR UPDATE"
             )) {
            while (rows.next()) {
                long id = rows.getLong("id");
                String joinCode = rows.getString("join_code");
                classes.add(new ClassCode(id, joinCode));
                if (joinCode != null) {
                    reservedCodes.add(joinCode);
                }
            }
        }

        Map<Long, String> replacements = new LinkedHashMap<>();
        try (PreparedStatement collisionQuery = connection.prepareStatement(
                "SELECT 1 FROM classes WHERE join_code = ? LIMIT 1"
        )) {
            for (ClassCode studentClass : classes) {
                replacements.put(
                        studentClass.id(),
                        generateUnusedCode(reservedCodes, collisionQuery)
                );
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
        collisionQuery.setString(1, candidate);
        try (ResultSet rows = collisionQuery.executeQuery()) {
            return rows.next();
        }
    }

    private void updateShadowColumn(
            Connection connection,
            Map<Long, String> replacements
    ) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE classes SET join_code_v6 = ? WHERE id = ?"
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

    void verifyPreparedData(Connection connection) throws SQLException {
        Set<String> seenCodes = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, join_code, join_code_v6 FROM classes"
             )) {
            while (rows.next()) {
                String oldCode = rows.getString("join_code");
                String replacement = rows.getString(SHADOW_COLUMN);
                if (!isNormalizedCode(replacement)) {
                    throw new SQLException(
                            "Dữ liệu staging lớp " + rows.getLong("id")
                                    + " không có mã sáu ký tự hợp lệ"
                    );
                }
                if (oldCode != null && oldCode.equalsIgnoreCase(replacement)) {
                    throw new SQLException(
                            "Dữ liệu staging lớp " + rows.getLong("id") + " chưa được xoay mã"
                    );
                }
                if (!seenCodes.add(replacement)) {
                    throw new SQLException("Dữ liệu staging có mã tham gia trùng lặp: " + replacement);
                }
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet collision = statement.executeQuery("""
                     SELECT prepared.id AS prepared_id, legacy.id AS legacy_id
                     FROM classes prepared
                     JOIN classes legacy
                       ON prepared.join_code_v6 = legacy.join_code
                     LIMIT 1
                     """)) {
            if (collision.next()) {
                throw new SQLException(
                        "Mã staging của lớp " + collision.getLong("prepared_id")
                                + " trùng mã cũ của lớp " + collision.getLong("legacy_id")
                );
            }
        }
    }

    private void performMysqlAtomicCutover(Connection connection, Preflight preflight)
            throws SQLException {
        execute(connection, mysqlAtomicCutoverSql(
                preflight.oldFormatChecks(),
                preflight.fixedUniqueOnAuthority(),
                preflight.reusableCheckName()
        ));
    }

    static String mysqlAtomicCutoverSql(
            List<String> oldFormatChecks,
            boolean dropFixedUnique,
            String finalCheckName
    ) {
        List<String> clauses = new ArrayList<>();
        oldFormatChecks.forEach(name ->
                clauses.add("DROP CHECK " + mysqlIdentifier(name))
        );
        if (dropFixedUnique) {
            clauses.add("DROP INDEX " + mysqlIdentifier(UNIQUE_NAME));
        }
        clauses.add("RENAME COLUMN join_code TO " + LEGACY_COLUMN);
        clauses.add("CHANGE COLUMN " + SHADOW_COLUMN + " join_code VARCHAR(6) NOT NULL");
        clauses.add("ADD CONSTRAINT " + mysqlIdentifier(UNIQUE_NAME)
                + " UNIQUE (join_code)");
        clauses.add("ADD CONSTRAINT " + mysqlIdentifier(finalCheckName) + """
                 CHECK (join_code REGEXP '(?-i)^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$'
                   AND join_code REGEXP '(?-i)[ABCDEFGHJKMNPQRSTUVWXYZ]'
                   AND join_code REGEXP '(?-i)[23456789]')
                """.stripTrailing());
        clauses.add("ADD CONSTRAINT " + mysqlIdentifier(ROTATION_CHECK_NAME)
                + " CHECK (join_code <> " + LEGACY_COLUMN
                + " OR " + LEGACY_COLUMN + " IS NULL)");
        return "ALTER TABLE classes\n  " + String.join(",\n  ", clauses);
    }

    private static String mysqlIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private void performH2Cutover(Connection connection, Preflight preflight)
            throws SQLException {
        for (String check : preflight.oldFormatChecks()) {
            execute(connection, "ALTER TABLE classes DROP CONSTRAINT "
                    + quoteIdentifier(connection, check));
        }
        if (preflight.fixedUniqueOnAuthority()) {
            dropNamedUnique(connection, UNIQUE_NAME);
        }
        execute(connection, """
                ALTER TABLE classes
                RENAME COLUMN join_code TO join_code_v6_legacy
                """);
        execute(connection, """
                ALTER TABLE classes
                RENAME COLUMN join_code_v6 TO join_code
                """);
        completeH2FinalSchema(connection, preflight.reusableCheckName());
    }

    private void completeH2FinalSchema(Connection connection) throws SQLException {
        completeH2FinalSchema(connection, CHECK_NAME);
    }

    private void completeH2FinalSchema(Connection connection, String checkName)
            throws SQLException {
        if (!hasExpectedColumnDefinition(connection)) {
            execute(connection, """
                    ALTER TABLE classes
                    ALTER COLUMN join_code VARCHAR(6) NOT NULL
                    """);
        }
        if (!hasUniqueIndexOnJoinCode(connection)) {
            if (indexNameExists(connection, UNIQUE_NAME)) {
                throw new SQLException("Index " + UNIQUE_NAME + " dùng sai cột");
            }
            execute(connection, """
                    ALTER TABLE classes
                    ADD CONSTRAINT uk_classes_join_code UNIQUE (join_code)
                    """);
        }
        if (findReusableFormatConstraint(connection) == null) {
            execute(connection, """
                    ALTER TABLE classes
                    ADD CONSTRAINT %s
                    CHECK (REGEXP_LIKE(join_code, '^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$')
                      AND REGEXP_LIKE(join_code, '[ABCDEFGHJKMNPQRSTUVWXYZ]')
                      AND REGEXP_LIKE(join_code, '[23456789]'))
                    """.formatted(quoteIdentifier(connection, checkName)));
        }
        if (!hasRotationConstraint(connection)) {
            execute(connection, """
                    ALTER TABLE classes
                    ADD CONSTRAINT chk_classes_join_code_v6_rotated
                    CHECK (join_code <> join_code_v6_legacy
                      OR join_code_v6_legacy IS NULL)
                    """);
        }
    }

    private void dropNamedUnique(Connection connection, String name) throws SQLException {
        if (hasTableConstraint(connection, name, "UNIQUE")) {
            execute(connection, "ALTER TABLE classes DROP CONSTRAINT "
                    + quoteIdentifier(connection, name));
        } else {
            execute(connection, "DROP INDEX " + quoteIdentifier(connection, name));
        }
    }

    private boolean hasTableConstraint(
            Connection connection,
            String name,
            String type
    ) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT 1
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE UPPER(TABLE_NAME) = 'CLASSES'
                  AND UPPER(CONSTRAINT_SCHEMA) = UPPER(?)
                  AND UPPER(CONSTRAINT_NAME) = UPPER(?)
                  AND UPPER(CONSTRAINT_TYPE) = UPPER(?)
                """)) {
            query.setString(1, informationSchemaName(connection));
            query.setString(2, name);
            query.setString(3, type);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }

    private void cleanupLegacyColumn(Connection connection) throws SQLException {
        Map<String, ColumnDefinition> columns = loadColumns(connection, "classes");
        if (!columns.containsKey(LEGACY_COLUMN)) {
            return;
        }
        if (isH2(connection)) {
            if (hasTableConstraint(connection, ROTATION_CHECK_NAME, "CHECK")) {
                execute(connection, "ALTER TABLE classes DROP CONSTRAINT "
                        + quoteIdentifier(connection, ROTATION_CHECK_NAME));
            }
            execute(connection, "ALTER TABLE classes DROP COLUMN " + LEGACY_COLUMN);
            return;
        }
        execute(connection, """
                ALTER TABLE classes
                  DROP CHECK chk_classes_join_code_v6_rotated,
                  DROP COLUMN join_code_v6_legacy
                """);
    }

    private boolean hasRotationConstraint(Connection connection) throws SQLException {
        return findCheckConstraints(connection).stream()
                .anyMatch(check -> ROTATION_CHECK_NAME.equalsIgnoreCase(check.name())
                        && isExpectedRotationClause(check.checkClause()));
    }

    private static boolean isExpectedRotationClause(String checkClause) {
        String normalized = normalizeCheckClause(checkClause);
        return "join_code<>join_code_v6_legacyorjoin_code_v6_legacyisnull".equals(normalized)
                || "join_code_v6_legacyisnullorjoin_code<>join_code_v6_legacy"
                .equals(normalized);
    }

    private void verifyCutoverData(Connection connection) throws SQLException {
        verifyNormalizedData(connection);
        if (!hasExpectedColumnDefinition(connection)) {
            throw new SQLException("Cột classes.join_code phải là VARCHAR(6) NOT NULL sau cutover");
        }
        if (!hasUniqueIndexOnJoinCode(connection)) {
            throw new SQLException("Cột classes.join_code chưa có unique constraint sau cutover");
        }
        if (findReusableFormatConstraint(connection) == null) {
            throw new SQLException("Constraint mã lớp không đúng định nghĩa sau cutover");
        }
    }

    private void verifyFinalState(Connection connection) throws SQLException {
        verifyCutoverData(connection);
        Map<String, ColumnDefinition> columns = loadColumns(connection, "classes");
        if (columns.containsKey(SHADOW_COLUMN) || columns.containsKey(LEGACY_COLUMN)) {
            throw new SQLException("Cột staging V6 chưa được dọn dẹp");
        }
        Marker marker = readMarker(connection);
        if (!marker.exists() || !PHASE_CUTOVER.equals(marker.phase())) {
            throw new SQLException("Marker V6 chưa ghi nhận cutover hoàn tất");
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

    private boolean hasExpectedColumnDefinition(Connection connection) throws SQLException {
        ColumnDefinition joinCode = loadColumns(connection, "classes").get("join_code");
        if (joinCode == null) {
            throw new SQLException("Không tìm thấy cột classes.join_code");
        }
        return isExpectedJoinCodeColumn(
                joinCode.nullable(), joinCode.size(), joinCode.dataType(), joinCode.typeName()
        );
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
        return loadIndexes(connection).stream().anyMatch(index ->
                index.unique()
                        && index.columns().size() == 1
                        && "join_code".equalsIgnoreCase(index.columns().getFirst())
        );
    }

    private boolean hasNamedUniqueIndexOnColumn(
            Connection connection,
            String name,
            String column
    ) throws SQLException {
        return loadIndexes(connection).stream().anyMatch(index ->
                name.equalsIgnoreCase(index.name())
                        && index.unique()
                        && index.columns().size() == 1
                        && column.equalsIgnoreCase(index.columns().getFirst())
        ) || hasNamedUniqueConstraintOnColumn(connection, name, column);
    }

    private boolean hasNamedUniqueConstraintOnColumn(
            Connection connection,
            String name,
            String column
    ) throws SQLException {
        if (!hasTableConstraint(connection, name, "UNIQUE")) {
            return false;
        }
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT kcu.COLUMN_NAME
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                WHERE UPPER(kcu.CONSTRAINT_SCHEMA) = UPPER(?)
                  AND UPPER(kcu.TABLE_NAME) = 'CLASSES'
                  AND UPPER(kcu.CONSTRAINT_NAME) = UPPER(?)
                ORDER BY kcu.ORDINAL_POSITION
                """)) {
            query.setString(1, informationSchemaName(connection));
            query.setString(2, name);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next() || !column.equalsIgnoreCase(rows.getString("COLUMN_NAME"))) {
                    return false;
                }
                return !rows.next();
            }
        }
    }

    private boolean indexNameExists(Connection connection, String expectedName) throws SQLException {
        return loadIndexes(connection).stream()
                .anyMatch(index -> expectedName.equalsIgnoreCase(index.name()))
                || hasTableConstraint(connection, expectedName, "UNIQUE");
    }

    private Map<String, ColumnDefinition> loadColumns(
            Connection connection,
            String tableName
    ) throws SQLException {
        TableReference table = findTable(connection, tableName)
                .orElseThrow(() -> new SQLException("Không tìm thấy bảng " + tableName));
        Map<String, ColumnDefinition> columns = new LinkedHashMap<>();
        try (ResultSet rows = connection.getMetaData().getColumns(
                table.catalog(), table.schema(), table.name(), null
        )) {
            while (rows.next()) {
                String name = rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                columns.put(name, new ColumnDefinition(
                        rows.getInt("NULLABLE"),
                        rows.getInt("COLUMN_SIZE"),
                        rows.getInt("DATA_TYPE"),
                        rows.getString("TYPE_NAME")
                ));
            }
        }
        return columns;
    }

    private List<String> loadPrimaryKeyColumns(Connection connection, String tableName)
            throws SQLException {
        TableReference table = findTable(connection, tableName)
                .orElseThrow(() -> new SQLException("Không tìm thấy bảng " + tableName));
        Map<Short, String> columns = new TreeMap<>();
        try (ResultSet rows = connection.getMetaData().getPrimaryKeys(
                table.catalog(), table.schema(), table.name()
        )) {
            while (rows.next()) {
                columns.put(
                        rows.getShort("KEY_SEQ"),
                        rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT)
                );
            }
        }
        return List.copyOf(columns.values());
    }

    private TableReference findClassesTable(Connection connection) throws SQLException {
        return findTable(connection, "classes")
                .orElseThrow(() -> new SQLException("Không tìm thấy bảng classes"));
    }

    private Optional<TableReference> findTable(Connection connection, String expectedName)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = isH2(connection) ? connection.getSchema() : null;
        try (ResultSet tables = metadata.getTables(catalog, schema, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                if (expectedName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return Optional.of(new TableReference(
                            tables.getString("TABLE_CAT"),
                            tables.getString("TABLE_SCHEM"),
                            tables.getString("TABLE_NAME")
                    ));
                }
            }
        }
        return Optional.empty();
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
        constraints.sort(Comparator.comparing(
                CheckConstraint::name, String.CASE_INSENSITIVE_ORDER
        ));
        return constraints;
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

    private boolean isExpectedCheckClause(Connection connection, String checkClause)
            throws SQLException {
        return isH2(connection)
                ? isExpectedH2CheckClause(checkClause)
                : isExpectedCheckClause(checkClause);
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
                removeCharsetIntroducer(normalized, checkClause, index);
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

    private static void removeCharsetIntroducer(
            StringBuilder normalized,
            String checkClause,
            int quoteIndex
    ) {
        int introducerStart = charsetIntroducerStart(checkClause, quoteIndex);
        if (introducerStart >= 0) {
            int introducerLength = quoteIndex - introducerStart;
            normalized.delete(normalized.length() - introducerLength, normalized.length());
        }
    }

    private static int charsetIntroducerStart(String checkClause, int quoteIndex) {
        if (quoteIndex == 0 || Character.isWhitespace(checkClause.charAt(quoteIndex - 1))) {
            return -1;
        }
        int start = quoteIndex - 1;
        while (start >= 0) {
            char character = checkClause.charAt(start);
            if (character != '_' && !Character.isLetterOrDigit(character)) {
                break;
            }
            start--;
        }
        start++;
        if (start >= quoteIndex - 1 || checkClause.charAt(start) != '_') {
            return -1;
        }
        if (start > 0) {
            char boundary = checkClause.charAt(start - 1);
            if (boundary == '_' || Character.isLetterOrDigit(boundary)) {
                return -1;
            }
        }
        return start;
    }

    private String informationSchemaName(Connection connection) throws SQLException {
        return isH2(connection) ? connection.getSchema() : connection.getCatalog();
    }

    private String quoteIdentifier(Connection connection, String identifier) throws SQLException {
        if (isH2(connection)) {
            return identifier;
        }
        return mysqlIdentifier(identifier);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private boolean isH2(Connection connection) throws SQLException {
        return isH2(connection.getMetaData().getDatabaseProductName());
    }

    private boolean isH2(String product) {
        return product != null && product.toUpperCase(Locale.ROOT).contains("H2");
    }

    private enum MigrationState {
        PRE_CUTOVER,
        H2_CUTOVER_IN_PROGRESS,
        POST_CUTOVER,
        COMPLETE
    }

    private record Preflight(
            MigrationState state,
            List<String> oldFormatChecks,
            boolean fixedUniqueOnAuthority,
            String reusableCheckName
    ) {
    }

    private record Marker(boolean exists, String phase) {
    }

    private record TableReference(String catalog, String schema, String name) {
    }

    private record ColumnDefinition(int nullable, int size, int dataType, String typeName) {
    }

    private record CheckConstraint(String name, String checkClause, String enforced) {
    }

    private record ClassCode(long id, String joinCode) {
    }

    private record IndexDefinition(String name, boolean unique, List<String> columns) {
    }

    private record MutableIndex(String name, boolean nonUnique, List<String> columns) {
        private MutableIndex(String name, boolean nonUnique) {
            this(name, nonUnique, new ArrayList<>());
        }
    }

    @FunctionalInterface
    interface MysqlLockedWork {
        void execute() throws SQLException;
    }
}
