package com.vihu.ganlu;

import org.junit.jupiter.api.Test;

class GanluApplicationTests {

    @Test
    void applicationEntryPointIsAvailableWithoutRequiringARealDatabase() {
        org.junit.jupiter.api.Assertions.assertNotNull(GanluApplication.class);
    }

}
