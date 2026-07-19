package com.neo.ezaccounting;

public final class EzBookkeepingPageDetector {
    public enum PageIdentity {
        HOME,
        OTHER,
        UNKNOWN
    }

    private static final String HOME_SCRIPT =
            "(function(){try{" +
            "var view=document.getElementById('main-view');" +
            "if(!view){return 'unknown';}" +
            "var pages=view.querySelectorAll('.page-current');" +
            "var page=null;" +
            "for(var i=0;i<pages.length;i++){" +
            "if(pages[i].closest('.view')===view){" +
            "if(page){return 'unknown';}" +
            "page=pages[i];" +
            "}" +
            "}" +
            "if(!page||!page.isConnected){return 'unknown';}" +
            "var style=window.getComputedStyle(page);" +
            "if(style.display==='none'||style.visibility==='hidden'){return 'unknown';}" +
            "return page.querySelector('.home-summary-card')" +
            "&&page.querySelector('.overview-transaction-list')" +
            "?'home':'other';" +
            "}catch(e){return 'unknown';}})();";

    private EzBookkeepingPageDetector() {}

    public static String homeDetectionScript() {
        return HOME_SCRIPT;
    }

    public static PageIdentity parseIdentity(String rawResult) {
        if (rawResult == null) return PageIdentity.UNKNOWN;
        String value = rawResult.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if ("home".equalsIgnoreCase(value)) return PageIdentity.HOME;
        if ("other".equalsIgnoreCase(value)) return PageIdentity.OTHER;
        return PageIdentity.UNKNOWN;
    }

    public static boolean isHomeResult(String rawResult) {
        return parseIdentity(rawResult) == PageIdentity.HOME;
    }
}
