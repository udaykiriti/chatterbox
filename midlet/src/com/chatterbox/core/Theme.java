package com.chatterbox.core;

/**
 * Colours and font metrics for both themes. Pure data, no MicroEdition imports,
 * so it can be unit-tested; the UI layer turns these ints into Graphics colours.
 *
 * Assumption: MIDP colours are ARGB ints; we store 0xRRGGBB and OR in 0xFF000000
 * at draw time via {@link #argb(int)}.
 */
public final class Theme {

    public static final int DARK = 0;
    public static final int LIGHT = 1;

    /** Foreground, background, nick palette, highlight, system, link. */
    public int bg;
    public int fg;
    public int system;
    public int highlightBg;
    public int highlightFg;
    public int nick;
    public int nickSelf;
    public int nickAlt;
    public int link;
    public int inputBg;
    public int inputFg;
    public int cursor;
    public int scrollbar;
    public int unread;
    public int timestamp;

    /** Round-robin memo for colorForNick; chat nicks repeat heavily. */
    private static final int NICK_CACHE_SIZE = 8;
    private final String[] nickCacheKeys = new String[NICK_CACHE_SIZE];
    private final int[] nickCacheVals = new int[NICK_CACHE_SIZE];
    private int nickCachePos;

    private Theme() {
    }

    public static Theme dark() {
        Theme t = new Theme();
        t.bg = 0x000000;
        t.fg = 0xC0C0C0;
        t.system = 0x008080;
        t.highlightBg = 0x003060;
        t.highlightFg = 0xFFFF00;
        t.nick = 0x00B0FF;
        t.nickSelf = 0xFF8000;
        t.nickAlt = 0x80FF80;
        t.link = 0x6080FF;
        t.inputBg = 0x202020;
        t.inputFg = 0xFFFFFF;
        t.cursor = 0xFFFFFF;
        t.scrollbar = 0x404040;
        t.unread = 0xFF3030;
        t.timestamp = 0x707070;
        return t;
    }

    public static Theme light() {
        Theme t = new Theme();
        t.bg = 0xFFFFFF;
        t.fg = 0x101010;
        t.system = 0x007070;
        t.highlightBg = 0xFFF3C0;
        t.highlightFg = 0x802000;
        t.nick = 0x0000C0;
        t.nickSelf = 0xB05000;
        t.nickAlt = 0x008000;
        t.link = 0x0000EE;
        t.inputBg = 0xEEEEEE;
        t.inputFg = 0x000000;
        t.cursor = 0x000000;
        t.scrollbar = 0xC0C0C0;
        t.unread = 0xCC0000;
        t.timestamp = 0x808080;
        return t;
    }

    public static Theme of(int kind) {
        return kind == LIGHT ? light() : dark();
    }

    public static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /**
     * Stable nick colour so the same user keeps the same colour in a session.
     * A simple multiply-shift hash over the lowercase nick; no Math.random so
     * colours are reproducible between runs (easier to test, easier to read).
     */
    public int colorForNick(String nick, Theme theme) {
        if (nick == null) {
            return theme.nickAlt;
        }
        // Memoize: a rebuild resolved every visible nick on every paint, each
        // one allocating a lowered copy. Chat nicks repeat heavily, so a tiny
        // round-robin cache absorbs nearly all of it.
        for (int i = 0; i < NICK_CACHE_SIZE; i++) {
            if (nick.equals(nickCacheKeys[i])) {
                return nickCacheVals[i];
            }
        }
        String n = Str.lower(nick);
        int h = 0;
        for (int i = 0; i < n.length(); i++) {
            h = (h * 31 + n.charAt(i)) & 0x7FFFFFFF;
        }
        int color;
        switch (h % 8) {
            case 0: color = theme.nick; break;
            case 1: color = theme.nickAlt; break;
            case 2: color = 0xC080FF; break;
            case 3: color = 0xFF8080; break;
            case 4: color = 0xFFFF80; break;
            case 5: color = 0x80FFC0; break;
            case 6: color = 0x80C0FF; break;
            default: color = theme.nick; break;
        }
        nickCacheKeys[nickCachePos] = nick;
        nickCacheVals[nickCachePos] = color;
        nickCachePos = (nickCachePos + 1) % NICK_CACHE_SIZE;
        return color;
    }
}
