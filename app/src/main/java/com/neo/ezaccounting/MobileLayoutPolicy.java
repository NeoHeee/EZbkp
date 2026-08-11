package com.neo.ezaccounting;

public final class MobileLayoutPolicy {
    private MobileLayoutPolicy() {}

    public static boolean useSingleColumn(float widthDp, float fontScale) {
        return widthDp < 380f || fontScale >= 1.25f;
    }
}
