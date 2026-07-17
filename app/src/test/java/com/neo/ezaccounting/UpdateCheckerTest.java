package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UpdateCheckerTest {
    @Test
    public void normalizesTagPrefixAndPrerelease() {
        assertEquals("1.4.0", UpdateChecker.normalizeVersion("v1.4.0-beta.1"));
    }

    @Test
    public void comparesSemanticVersions() {
        assertTrue(UpdateChecker.compareVersions("1.4.0", "1.3.1") > 0);
        assertTrue(UpdateChecker.compareVersions("1.3.1", "1.4.0") < 0);
        assertEquals(0, UpdateChecker.compareVersions("v1.4", "1.4.0"));
    }
}
