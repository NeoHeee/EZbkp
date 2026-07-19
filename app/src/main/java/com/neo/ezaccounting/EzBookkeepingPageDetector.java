package com.neo.ezaccounting;

public final class EzBookkeepingPageDetector {
    private static final String HOME_SCRIPT =
            "(function(){try{" +
            "var page=document.querySelector('.page-current');" +
            "if(!page){return false;}" +
            "return !!(page.querySelector('.home-summary-card')" +
            "&&page.querySelector('.overview-transaction-list'));" +
            "}catch(e){return false;}})();";

    private EzBookkeepingPageDetector() {}

    public static String homeDetectionScript() {
        return HOME_SCRIPT;
    }

    public static boolean isHomeResult(String rawResult) {
        if (rawResult == null) return false;
        String value = rawResult.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return "true".equalsIgnoreCase(value);
    }
}
