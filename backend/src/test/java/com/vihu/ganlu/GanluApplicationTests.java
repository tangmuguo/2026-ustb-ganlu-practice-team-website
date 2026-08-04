package com.vihu.ganlu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class GanluApplicationTests {

    @Test
    void applicationClassCanBeConstructedWithoutDatabaseConfiguration() {
        assertDoesNotThrow(GanluApplication::new);
    }

}
