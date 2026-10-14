package com.chatterbox.proto;

/**
 * IRC text formatting (mIRC/KVIrc style control codes).
 *
 * Control codes:
 *  \u0002 (0x02) - Bold
 *  \u001D (0x1D) - Italic
 *  \u001F (0x1F) - Underline
 *  \u0016 (0x16) - Reverse
 *  \u000F (0x0F) - Reset all
 *  \u0003 (0x03) - Color: \u0003[fg][,bg]text\u000F
 *
 * Color indices (0-15):
 *  0: white        8: yellow
 *  1: black        9: light green
 *  2: blue         10: cyan
 *  3: green        11: light cyan
 *  4: light red    12: light blue
 *  5: red          13: pink
 *  6: magenta      14: grey
 *  7: orange       15: light grey
 */
public final class Format {

    public static final char BOLD = '\u0002';
    public static final char ITALIC = '\u001D';
    public static final char UNDERLINE = '\u001F';
    public static final char REVERSE = '\u0016';
    public static final char RESET = '\u000F';
    public static final char COLOR = '\u0003';

    private Format() {
    }

    /** Strip all formatting codes from a string. */
    public static String strip(String s) {
        if (s == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != BOLD && c != ITALIC && c != UNDERLINE && c != REVERSE
                    && c != RESET && c != COLOR) {
                sb.append(c);
            } else if (c == COLOR) {
                // Skip color digits: \u0003NN or \u0003N,NN
                i++;
                int digits = 0;
                while (i < s.length() && digits < 2 && isDigit(s.charAt(i))) {
                    i++;
                    digits++;
                }
                if (i < s.length() && s.charAt(i) == ',' && digits > 0) {
                    i++;
                    digits = 0;
                    while (i < s.length() && digits < 2 && isDigit(s.charAt(i))) {
                        i++;
                        digits++;
                    }
                }
                i--; // loop will increment
            }
        }
        return sb.toString();
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** Apply bold. */
    public static String bold(String s) {
        return BOLD + s + BOLD;
    }

    /** Apply italic. */
    public static String italic(String s) {
        return ITALIC + s + ITALIC;
    }

    /** Apply underline. */
    public static String underline(String s) {
        return UNDERLINE + s + UNDERLINE;
    }

    /** Apply color (foreground only). */
    public static String color(String s, int fg) {
        return COLOR + twoDigits(fg) + s + RESET;
    }

    /** Apply color (foreground and background). */
    public static String color(String s, int fg, int bg) {
        return COLOR + twoDigits(fg) + "," + twoDigits(bg) + s + RESET;
    }

    private static String twoDigits(int n) {
        if (n < 10) {
            return "0" + n;
        }
        if (n > 99) {
            return "99";
        }
        return String.valueOf(n);
    }

    /** Parse a color index from a string like "04" or "4". */
    public static int parseColor(String s) {
        if (s == null || s.length() == 0) {
            return -1;
        }
        int v = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                v = v * 10 + (c - '0');
            }
        }
        return v > 99 ? 99 : v;
    }

    /** Color name -> index mapping. */
    public static int colorByName(String name) {
        if (name == null) {
            return -1;
        }
        String n = name.toLowerCase();
        if (n.equals("white")) return 0;
        if (n.equals("black")) return 1;
        if (n.equals("blue")) return 2;
        if (n.equals("green")) return 3;
        if (n.equals("light red") || n.equals("lightred")) return 4;
        if (n.equals("red")) return 5;
        if (n.equals("magenta")) return 6;
        if (n.equals("orange") || n.equals("brown")) return 7;
        if (n.equals("yellow")) return 8;
        if (n.equals("light green") || n.equals("lightgreen")) return 9;
        if (n.equals("cyan")) return 10;
        if (n.equals("light cyan") || n.equals("lightcyan")) return 11;
        if (n.equals("light blue") || n.equals("lightblue")) return 12;
        if (n.equals("pink")) return 13;
        if (n.equals("grey") || n.equals("gray")) return 14;
        if (n.equals("light grey") || n.equals("lightgray")) return 15;
        return -1;
    }
}