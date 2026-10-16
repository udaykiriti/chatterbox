package com.chatterbox.core;

import java.util.Vector;

/**
 * String helpers. CLDC 1.1 has no regex, no String.format and no StringBuilder,
 * so word wrapping, case folding and URL detection all live here.
 *
 * Pure Java, no MicroEdition imports.
 */
public final class Str {

    public static final char ELLIPSIS = '~';

    private Str() {
    }

    public static boolean isEmpty(String s) {
        return s == null || s.length() == 0;
    }

    public static String nvl(String s) {
        return s == null ? "" : s;
    }

    public static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** Truncate to at most n characters, the last of which is a '~'. */
    public static String clip(String s, int n) {
        if (s == null) {
            return "";
        }
        if (s.length() <= n) {
            return s;
        }
        if (n <= 1) {
            return "~";
        }
        return s.substring(0, n - 1) + ELLIPSIS;
    }

    /** Clip on a word boundary if one is available near the end. */
    public static String clipWord(String s, int n) {
        if (s == null || s.length() <= n || n < 4) {
            return clip(s, n);
        }
        int sp = s.lastIndexOf(' ', n - 2);
        if (sp > n / 2) {
            return s.substring(0, sp) + ELLIPSIS;
        }
        return clip(s, n);
    }

    /**
     * Split on runs of whitespace. Returns a Vector; never null.
     */
    public static Vector words(String s) {
        Vector v = new Vector();
        if (s == null) {
            return v;
        }
        int len = s.length();
        int i = 0;
        while (i < len) {
            while (i < len && s.charAt(i) == ' ') {
                i++;
            }
            if (i >= len) {
                break;
            }
            int start = i;
            while (i < len && s.charAt(i) != ' ') {
                i++;
            }
            v.addElement(s.substring(start, i));
        }
        return v;
    }

    /**
     * Split on a single delimiter, keeping empty fields.
     */
    public static Vector split(String s, char sep) {
        Vector v = new Vector();
        if (s == null) {
            return v;
        }
        int start = 0;
        int i = 0;
        int len = s.length();
        while (i <= len) {
            if (i == len || s.charAt(i) == sep) {
                v.addElement(s.substring(start, i));
                start = i + 1;
            }
            i++;
        }
        return v;
    }

    public static String join(Vector v, String sep) {
        if (v == null || v.size() == 0) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < v.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(nvl((String) v.elementAt(i)));
        }
        return sb.toString();
    }

    /** IRC casemapping: lowercase A-Z only. Unicode case folding is wrong here. */
    public static String lower(String s) {
        if (s == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c = (char) (c + 32);
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static boolean equalsIgnoreCase(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

    public static boolean startsWithIgnoreCase(String s, String p) {
        if (s == null || p == null || s.length() < p.length()) {
            return false;
        }
        for (int i = 0; i < p.length(); i++) {
            if (foldAscii(s.charAt(i)) != foldAscii(p.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Case-insensitive 'contains' using IRC casemapping. Returns the index or -1.
     * Allocation-free: compares folded chars inline instead of lowercasing
     * substrings, so per-message highlight checks and URL scans do not churn
     * the heap on devices without a JIT.
     */
    public static int indexOfIgnoreCase(String s, String needle, int from) {
        if (s == null || needle == null || needle.length() == 0) {
            return -1;
        }
        int n = needle.length();
        int start = from < 0 ? 0 : from;
        for (int i = start; i + n <= s.length(); i++) {
            int j = 0;
            while (j < n && foldAscii(s.charAt(i + j)) == foldAscii(needle.charAt(j))) {
                j++;
            }
            if (j == n) {
                return i;
            }
        }
        return -1;
    }

    /** Same mapping as lower(), but on a single char: A-Z only. */
    private static char foldAscii(char c) {
        return (c >= 'A' && c <= 'Z') ? (char) (c + 32) : c;
    }

    public static boolean containsIgnoreCase(String s, String needle) {
        return indexOfIgnoreCase(s, needle, 0) >= 0;
    }

    /** Strip leading '!', '@', '+', '&', '%', '~' user modes from a name. */
    public static String stripPrefixModes(String s) {
        if (s == null) {
            return "";
        }
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '@' || c == '+' || c == '!' || c == '&' || c == '%' || c == '~') {
                i++;
            } else {
                break;
            }
        }
        return s.substring(i);
    }

    /**
     * Wrap text to a pixel-independent character width.
     * Does not break words unless a single word is longer than the width.
     *
     * @return a Vector of display lines.
     */
    public static Vector wrap(String text, int width) {
        Vector out = new Vector();
        if (text == null) {
            return out;
        }
        if (width < 4) {
            width = 4;
        }
        int len = text.length();
        int lineStart = 0;
        while (lineStart < len) {
            if (len - lineStart <= width) {
                out.addElement(text.substring(lineStart));
                break;
            }
            // Find the last space at or before the wrap point.
            int brk = -1;
            for (int i = lineStart + width; i > lineStart; i--) {
                char c = text.charAt(i);
                if (c == ' ' || c == '\t') {
                    brk = i;
                    break;
                }
            }
            if (brk <= lineStart) {
                // A single word longer than the width: hard break.
                out.addElement(text.substring(lineStart, lineStart + width));
                lineStart += width;
            } else {
                out.addElement(text.substring(lineStart, brk));
                lineStart = brk + 1;
            }
        }
        if (out.size() == 0) {
            out.addElement("");
        }
        return out;
    }

    /**
     * Detect the first http/https/ftp URL in a line.
     * @return the URL without trailing punctuation, or null.
     */
    public static String findUrl(String line) {
        if (line == null) {
            return null;
        }
        String[] schemes = { "http://", "https://", "ftp://" };
        int best = -1;
        int bestLen = 0;
        for (int i = 0; i < schemes.length; i++) {
            int at = indexOfIgnoreCase(line, schemes[i], 0);
            if (at >= 0 && (best < 0 || at < best)) {
                best = at;
                bestLen = schemes[i].length();
            }
        }
        if (best < 0) {
            return null;
        }
        int end = best + bestLen;
        while (end < line.length()) {
            char c = line.charAt(end);
            if (c == ' ' || c == '<' || c == '>' || c == '"' || c == '\'' || c == ')' || c == ',') {
                break;
            }
            end++;
        }
        if (end <= best + bestLen) {
            return null;
        }
        return line.substring(best, end);
    }

    /** Strip a trailing ':' so text read from a TextBox reads naturally. */
    public static String deColon(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() > 0 && t.charAt(0) == ':') {
            return t.substring(1).trim();
        }
        return t;
    }

    public static String pad(String s, int n) {
        String v = nvl(s);
        if (v.length() >= n) {
            return v;
        }
        StringBuffer sb = new StringBuffer(v);
        while (sb.length() < n) {
            sb.append(' ');
        }
        return sb.toString();
    }

    public static String repeat(char c, int n) {
        if (n <= 0) {
            return "";
        }
        char[] a = new char[n];
        for (int i = 0; i < n; i++) {
            a[i] = c;
        }
        return new String(a);
    }

    /**
     * Longest common prefix length, used for inline nick completion.
     */
    public static int commonPrefix(String a, String b) {
        if (a == null || b == null) {
            return 0;
        }
        int n = a.length() < b.length() ? a.length() : b.length();
        for (int i = 0; i < n; i++) {
            char ca = a.charAt(i);
            char cb = b.charAt(i);
            if (ca >= 'A' && ca <= 'Z') {
                ca = (char) (ca + 32);
            }
            if (cb >= 'A' && cb <= 'Z') {
                cb = (char) (cb + 32);
            }
            if (ca != cb) {
                return i;
            }
        }
        return n;
    }

    public static int parseInt(String s, int def) {
        if (s == null) {
            return def;
        }
        String t = s.trim();
        if (t.length() == 0) {
            return def;
        }
        int sign = 1;
        int i = 0;
        if (t.charAt(0) == '-') {
            sign = -1;
            i = 1;
        } else if (t.charAt(0) == '+') {
            i = 1;
        }
        if (i >= t.length()) {
            return def;
        }
        int v = 0;
        for (; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c < '0' || c > '9') {
                return def;
            }
            v = v * 10 + (c - '0');
            if (v > 1000000000) {
                return def;
            }
        }
        return v * sign;
    }

    public static boolean parseBool(String s, boolean def) {
        if (s == null) {
            return def;
        }
        String t = s.trim();
        if (t.length() == 0) {
            return def;
        }
        if (t.equals("1") || t.equalsIgnoreCase("true") || t.equalsIgnoreCase("yes")
                || t.equalsIgnoreCase("on")) {
            return true;
        }
        if (t.equals("0") || t.equalsIgnoreCase("false") || t.equalsIgnoreCase("no")
                || t.equalsIgnoreCase("off")) {
            return false;
        }
        return def;
    }

    /** Two-digit zero-padded number, avoiding String.format. */
    public static String two(int v) {
        if (v < 0) {
            v = 0;
        }
        if (v < 10) {
            return "0" + v;
        }
        if (v < 100) {
            return String.valueOf(v);
        }
        return String.valueOf(v % 100);
    }
}
