package com.vihu.ganlu.configs;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSchemaValidatorTests {

    private final DatabaseSchemaValidator validator = new DatabaseSchemaValidator(null);

    @Test
    void acceptsTheCompleteRequiredSchema() {
        assertDoesNotThrow(() -> validator.validateSchema(requiredTables(), requiredColumns()));
    }

    @Test
    void reportsEveryMismatchSeenInTheStartupLogTogether() {
        Set<String> tables = requiredTables();
        tables.remove("file_deletion_task");
        tables.remove("team_media");
        Map<String, Set<String>> columns = requiredColumns();
        columns.get("course").remove("status");
        columns.get("course_detail").remove("status");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> validator.validateSchema(tables, columns));

        assertTrue(exception.getMessage().contains("file_deletion_task"));
        assertTrue(exception.getMessage().contains("team_media"));
        assertTrue(exception.getMessage().contains("course.status"));
        assertTrue(exception.getMessage().contains("course_detail.status"));
        assertTrue(exception.getMessage().contains("00 → 10 → 11 → 12 → 13 → 14 → 15 → 20 → 30 → 31"));
    }

    private Set<String> requiredTables() {
        return new LinkedHashSet<String>(DatabaseSchemaValidator.REQUIRED_SCHEMA.keySet());
    }

    private Map<String, Set<String>> requiredColumns() {
        Map<String, Set<String>> copy = new LinkedHashMap<String, Set<String>>();
        for (Map.Entry<String, Set<String>> entry : DatabaseSchemaValidator.REQUIRED_SCHEMA.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<String>(entry.getValue()));
        }
        return copy;
    }
}
