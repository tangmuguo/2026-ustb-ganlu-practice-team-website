package com.vihu.ganlu.configs;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stops the application before request handlers and scheduled jobs run against an old database schema.
 *
 * <p>The project intentionally keeps data migrations as explicit SQL scripts. This check does not apply
 * migrations: it makes a missed migration immediately actionable instead of producing repeated, unrelated
 * {@code BadSqlGrammarException}s after the server appears to have started.</p>
 */
@Component
@ConditionalOnProperty(name = "ganlu.schema-validation.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseSchemaValidator implements InitializingBean {
    private static final String TABLES_QUERY =
            "SELECT table_name FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'";
    private static final String COLUMNS_QUERY =
            "SELECT table_name, column_name FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE()";

    static final Map<String, Set<String>> REQUIRED_SCHEMA = buildRequiredSchema();

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        Set<String> availableTables = new LinkedHashSet<String>();
        for (String tableName : jdbcTemplate.queryForList(TABLES_QUERY, String.class)) {
            if (tableName != null) {
                availableTables.add(normalize(tableName));
            }
        }

        Map<String, Set<String>> availableColumns = new LinkedHashMap<String, Set<String>>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(COLUMNS_QUERY)) {
            String tableName = valueFor(row, "table_name");
            String columnName = valueFor(row, "column_name");
            if (tableName == null || columnName == null) {
                continue;
            }
            String normalizedTable = normalize(tableName);
            Set<String> columns = availableColumns.get(normalizedTable);
            if (columns == null) {
                columns = new LinkedHashSet<String>();
                availableColumns.put(normalizedTable, columns);
            }
            columns.add(normalize(columnName));
        }

        validateSchema(availableTables, availableColumns);
    }

    void validateSchema(Set<String> availableTables, Map<String, Set<String>> availableColumns) {
        List<String> missingTables = new ArrayList<String>();
        List<String> missingColumns = new ArrayList<String>();

        for (Map.Entry<String, Set<String>> requirement : REQUIRED_SCHEMA.entrySet()) {
            String tableName = requirement.getKey();
            if (!availableTables.contains(tableName)) {
                missingTables.add(tableName);
                continue;
            }

            Set<String> columns = availableColumns.get(tableName);
            if (columns == null) {
                columns = Collections.emptySet();
            }
            for (String columnName : requirement.getValue()) {
                if (!columns.contains(columnName)) {
                    missingColumns.add(tableName + "." + columnName);
                }
            }
        }

        if (!missingTables.isEmpty() || !missingColumns.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("数据库结构不完整，后端已停止启动，避免接口和定时任务在运行中反复报错。");
            if (!missingTables.isEmpty()) {
                message.append(" 缺少表: ").append(String.join(", ", missingTables)).append("。");
            }
            if (!missingColumns.isEmpty()) {
                message.append(" 缺少字段: ").append(String.join(", ", missingColumns)).append("。");
            }
            message.append(" 请先备份现有库，再按 database/patches/README.md 的固定顺序执行：")
                    .append("00 → 10 → 11 → 12 → 13 → 14 → 15 → 20 → 30 → 31。")
                    .append(" 仅排障时可设置 GANLU_SCHEMA_VALIDATION_ENABLED=false；这不会修复数据库，不能用于正常运行。");
            throw new IllegalStateException(message.toString());
        }
    }

    private static Map<String, Set<String>> buildRequiredSchema() {
        Map<String, Set<String>> requirements = new LinkedHashMap<String, Set<String>>();
        require(requirements, "banner");
        require(requirements, "course", "status");
        require(requirements, "course_detail", "uploader_user_id", "year", "custom_subject", "cover_path",
                "original_file_path", "preview_file_path", "original_filename", "file_extension", "mime_type",
                "preview_status", "status");
        require(requirements, "file_deletion_task", "id", "asset_type", "asset_id", "relative_path", "owner_user_id",
                "file_size", "status", "retry_count", "last_error", "next_retry_at", "created_at", "updated_at");
        require(requirements, "message");
        require(requirements, "news");
        require(requirements, "public_image_asset", "asset_id", "relative_path", "owner_user_id", "file_size", "created_at");
        require(requirements, "public_image_quota", "owner_user_id", "used_file_count", "used_bytes", "updated_at");
        require(requirements, "reply");
        require(requirements, "team", "owner_user_id", "region", "school", "description", "cover_url", "status",
                "created_at", "updated_at");
        require(requirements, "team_media", "filename", "relative_path", "mime_type", "file_size", "uploader_id",
                "team_id", "related_type", "related_id", "status", "reject_reason", "created_at", "updated_at");
        require(requirements, "team_media_global_quota", "singleton_id", "used_file_count", "used_bytes", "updated_at");
        require(requirements, "team_media_quota", "owner_user_id", "used_file_count", "used_bytes", "updated_at");
        require(requirements, "team_media_upload_reservation", "reservation_id", "owner_user_id", "reserved_bytes",
                "status", "expires_at", "created_at", "released_at");
        require(requirements, "team_page", "team_id", "status", "created_at", "updated_at");
        require(requirements, "team_page_images", "team_id", "status", "reject_reason", "log_date");
        require(requirements, "team_page_word", "team_id", "status", "reject_reason", "log_date");
        require(requirements, "user");
        return Collections.unmodifiableMap(requirements);
    }

    private static void require(Map<String, Set<String>> requirements, String tableName, String... columnNames) {
        Set<String> columns = new LinkedHashSet<String>(Arrays.asList(columnNames));
        requirements.put(tableName, Collections.unmodifiableSet(columns));
    }

    private static String valueFor(Map<String, Object> row, String expectedKey) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (expectedKey.equalsIgnoreCase(entry.getKey()) && entry.getValue() != null) {
                return entry.getValue().toString();
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
