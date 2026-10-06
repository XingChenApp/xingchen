package com.xingchen.tv.theme;

public class XingChenTheme {
    public static final String UI_NORMAL = "normal";
    public static final String UI_GLASS = "glass";

    public static final String WP_BUILTIN = "builtin";
    public static final String WP_LOCAL = "local";
    public static final String WP_URL = "url";
    public static final String WP_COLOR = "color";

    public String uiStyle = UI_GLASS;
    public String wallpaperType = WP_BUILTIN;
    public String wallpaperValue = "shanjian";
    public int glassAlpha = 55;

    public static XingChenTheme load(android.content.Context ctx) {
        XingChenTheme t = new XingChenTheme();
        android.content.SharedPreferences sp = ctx.getSharedPreferences("xingchen", android.content.Context.MODE_PRIVATE);
        t.uiStyle = sp.getString("ui_style", UI_GLASS);
        t.wallpaperType = sp.getString("wallpaper_type", WP_BUILTIN);
        t.wallpaperValue = sp.getString("wallpaper_value", "shanjian");
        String oldWp = sp.getString("wallpaper", "shanjian");
        if (!"shanjian".equals(oldWp) && WP_BUILTIN.equals(t.wallpaperType)) {
            if ("color".equals(oldWp)) {
                t.wallpaperType = WP_COLOR;
                t.wallpaperValue = sp.getString("wallpaper_color", "#8fb0d1");
            }
        }
        t.glassAlpha = sp.getInt("glass_alpha", 55);
        return t;
    }

    public void save(android.content.Context ctx) {
        android.content.SharedPreferences.Editor e = ctx.getSharedPreferences("xingchen", android.content.Context.MODE_PRIVATE).edit();
        e.putString("ui_style", uiStyle);
        e.putString("wallpaper_type", wallpaperType);
        e.putString("wallpaper_value", wallpaperValue);
        e.putInt("glass_alpha", glassAlpha);
        e.apply();
    }
}
