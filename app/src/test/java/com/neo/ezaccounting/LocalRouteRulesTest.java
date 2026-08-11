package com.neo.ezaccounting;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LocalRouteRulesTest {
    @Test
    public void exactWifiMatchWinsOverDefault() {
        List<LocalRouteRule> rules = Arrays.asList(
                new LocalRouteRule("", "http://default"),
                new LocalRouteRule("Home-5G", "http://home"));

        assertEquals("http://home", LocalRouteRules.select(rules, "home-5g", true));
    }

    @Test
    public void defaultRuleWorksWhenSsidCannotBeRead() {
        List<LocalRouteRule> rules = Arrays.asList(
                new LocalRouteRule("", "http://default"),
                new LocalRouteRule("Home", "http://home"));

        assertEquals("http://default", LocalRouteRules.select(rules, null, true));
    }

    @Test
    public void legacyDefaultRuleStillWorksOutsideWifi() {
        List<LocalRouteRule> rules = Arrays.asList(
                new LocalRouteRule("", "http://default"));

        assertEquals("http://default", LocalRouteRules.select(rules, "Home", false));
    }

    @Test
    public void wifiSpecificRuleIsDisabledOutsideWifi() {
        List<LocalRouteRule> rules = Arrays.asList(
                new LocalRouteRule("Home", "http://home"));

        assertEquals("", LocalRouteRules.select(rules, "Home", false));
    }

    @Test
    public void legacyAddressMigratesToDefaultRule() {
        List<LocalRouteRule> migrated = LocalRouteRules.decode("", "http://legacy");

        assertEquals(Arrays.asList(new LocalRouteRule("", "http://legacy")), migrated);
    }
}
