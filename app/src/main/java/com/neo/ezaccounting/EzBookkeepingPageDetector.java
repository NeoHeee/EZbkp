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
            "var route=null;" +
            "if(view.f7View&&view.f7View.router){route=view.f7View.router.currentRoute;}" +
            "if(!route&&page.f7Page){route=page.f7Page.route;}" +
            "var path=route&&(route.path||route.url);" +
            "if(path){" +
            "path=String(path);" +
            "var hashAt=path.indexOf('#');" +
            "if(hashAt>=0&&path.substring(hashAt+1).charAt(0)==='/'){" +
            "path=path.substring(hashAt+1);" +
            "}else{path=path.split('#')[0];}" +
            "path=path.split('?')[0];" +
            "if(/^https?:/i.test(path)){path=new URL(path,location.href).pathname;}" +
            "if(path==='/'){return 'home';}" +
            "if(path.charAt(0)==='/'){return 'other';}" +
            "}" +
            "var hasHomeToolbar=page.querySelector('.main-tabbar')" +
            "&&page.querySelector('#homepage-add-button');" +
            "if(hasHomeToolbar){return 'home';}" +
            "var hasLegacyHomeWidgets=page.querySelector('.home-summary-card')" +
            "&&page.querySelector('.overview-transaction-list');" +
            "return hasLegacyHomeWidgets?'home':'other';" +
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

    public static boolean resolveHome(PageIdentity cachedIdentity, boolean urlAtHome) {
        if (cachedIdentity == PageIdentity.HOME) return true;
        if (cachedIdentity == PageIdentity.OTHER) return false;
        return urlAtHome;
    }
}
