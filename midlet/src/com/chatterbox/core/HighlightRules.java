package com.chatterbox.core;

import java.util.Vector;

/**
 * Highlight rules - custom triggers for mentions.
 * More flexible than just "my nick" - supports patterns, whole words, case sensitivity.
 */
public final class HighlightRules {

    private final Vector rules = new Vector();
    private static final int MAX_RULES = 20;

    public static final class Rule {
        public String pattern;      // e.g. "myproject", "urgent", "deploy"
        public boolean wholeWord;   // true = match whole word only
        public boolean caseSensitive;
        public boolean matchHost;   // match in hostmask too
        public int color;           // custom highlight color (0 = default)

        public Rule(String p) {
            this.pattern = p == null ? "" : p.trim();
            this.wholeWord = true;
            this.caseSensitive = false;
            this.matchHost = false;
            this.color = 0;
        }

        public boolean matches(String text, String nick, String user, String host) {
            if (pattern.length() == 0) {
                return false;
            }
            String haystack = text;
            if (matchHost && user != null && host != null) {
                haystack = text + " " + user + "@" + host;
            }
            if (wholeWord) {
                return matchWholeWord(haystack);
            }
            return matchSubstring(haystack);
        }

        private boolean matchSubstring(String text) {
            String t = caseSensitive ? text : Str.lower(text);
            String p = caseSensitive ? pattern : Str.lower(pattern);
            return t.indexOf(p) >= 0;
        }

        private boolean matchWholeWord(String text) {
            String t = caseSensitive ? text : Str.lower(text);
            String p = caseSensitive ? pattern : Str.lower(pattern);
            int idx = t.indexOf(p);
            while (idx >= 0) {
                boolean before = (idx == 0) || !isWordChar(t.charAt(idx - 1));
                boolean after = (idx + p.length() >= t.length()) || !isWordChar(t.charAt(idx + p.length()));
                if (before && after) {
                    return true;
                }
                idx = t.indexOf(p, idx + 1);
            }
            return false;
        }

        private boolean isWordChar(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_';
        }
    }

    public synchronized void add(Rule r) {
        if (r == null || r.pattern.length() == 0) {
            return;
        }
        // Replace if same pattern exists
        for (int i = 0; i < rules.size(); i++) {
            Rule ex = (Rule) rules.elementAt(i);
            if (ex.pattern.equalsIgnoreCase(r.pattern)) {
                rules.setElementAt(r, i);
                return;
            }
        }
        if (rules.size() >= MAX_RULES) {
            rules.removeElementAt(0);
        }
        rules.addElement(r);
    }

    public synchronized void add(String pattern) {
        add(new Rule(pattern));
    }

    public synchronized void remove(String pattern) {
        if (pattern == null) {
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            Rule r = (Rule) rules.elementAt(i);
            if (r.pattern.equalsIgnoreCase(pattern.trim())) {
                rules.removeElementAt(i);
                return;
            }
        }
    }

    /** Check all rules against a message. Returns matching rule or null. */
    public synchronized Rule check(String text, String nick, String user, String host) {
        for (int i = 0; i < rules.size(); i++) {
            Rule r = (Rule) rules.elementAt(i);
            if (r.matches(text, nick, user, host)) {
                return r;
            }
        }
        return null;
    }

    public synchronized Vector getRules() {
        Vector v = new Vector(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            v.addElement(rules.elementAt(i));
        }
        return v;
    }

    public synchronized void clear() {
        rules.removeAllElements();
    }

    public synchronized int size() {
        return rules.size();
    }
}