package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExperienceVersionTest {
    @Test
    public void expectedExperienceVersionIsStable() {
        assertEquals("1.4.0", UpdateChecker.normalizeVersion("v1.4.0"));
    }
}
