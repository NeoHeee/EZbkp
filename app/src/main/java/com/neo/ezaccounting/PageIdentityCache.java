package com.neo.ezaccounting;

public final class PageIdentityCache {
    private EzBookkeepingPageDetector.PageIdentity identity =
            EzBookkeepingPageDetector.PageIdentity.UNKNOWN;
    private String url;

    public EzBookkeepingPageDetector.PageIdentity get() {
        return identity;
    }

    public EzBookkeepingPageDetector.PageIdentity getFor(String currentUrl) {
        return isValidFor(currentUrl) ? identity :
                EzBookkeepingPageDetector.PageIdentity.UNKNOWN;
    }

    public boolean isValidFor(String currentUrl) {
        return identity != EzBookkeepingPageDetector.PageIdentity.UNKNOWN &&
                same(url, currentUrl);
    }

    public void update(String currentUrl,
                       EzBookkeepingPageDetector.PageIdentity pageIdentity) {
        url = currentUrl;
        identity = pageIdentity == null ?
                EzBookkeepingPageDetector.PageIdentity.UNKNOWN : pageIdentity;
    }

    public void invalidate() {
        identity = EzBookkeepingPageDetector.PageIdentity.UNKNOWN;
        url = null;
    }

    private boolean same(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }
}
