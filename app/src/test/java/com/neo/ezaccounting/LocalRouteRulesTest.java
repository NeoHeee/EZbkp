package com.neo.ezaccounting;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LocalRouteRulesTest {
    @Test
    public void exactWifiMatchWinsOverDefault() {
        LocalRouteRule home = new LocalRouteRule("家里", "Home-5G", "http://home");
        List<LocalRouteRule> rules = Arrays.asList(
                new LocalRouteRule("", "http://default"), home);

        assertEquals("http://home", LocalRouteRules.select(rules, "home-5g", true));
        assertEquals(home, LocalRouteRules.selectRule(rules, "HOME-5G", true));
    }

    @Test
    public void defaultRuleWorksWhenSsidCannotBeRead() {
        List<LocalRouteRule> rules = Arrays.asList(
                new LocalRouteRule("", "http://default"),
                new LocalRouteRule("Home", "http://home"));

        assertEquals("http://default", LocalRouteRules.select(rules, null, true));
    }

    @Test
    public void defaultRuleIsBoundToWifi() {
        List<LocalRouteRule> rules = Arrays.asList(
                new LocalRouteRule("", "http://default"));

        assertEquals("", LocalRouteRules.select(rules, "Home", false));
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
