package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PagePositionCacheTest {
    @Test
    public void restoresOnlyMatchingPagePosition() {
        PagePositionCache cache = new PagePositionCache();
        cache.save("https://money.example.com/statistics", 0, 640);
        assertEquals(640, cache.get("https://money.example.com/statistics").y);
        assertNull(cache.get("https://money.example.com/accounts"));
    }

    @Test
    public void keepsOnlyRecentPages() {
        PagePositionCache cache = new PagePositionCache();
        for (int index = 0; index < 9; index++) {
            cache.save("https://money.example.com/page/" + index, 0, index * 10);
        }
        assertNull(cache.get("https://money.example.com/page/0"));
        assertEquals(80, cache.get("https://money.example.com/page/8").y);
    }
}
