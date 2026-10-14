package com.chatterbox.ui;

/**
 * Single-line text editor model for inline chat input on a Canvas.
 *
 * Pure logic, no MicroEdition imports, so the key-handling rules are testable.
 *
 * Assumptions and key semantics on S60 / Series 40:
 *  - The Canvas receives KEY_NUM_* (or its own char equivalents) and the Nokia
 *    proprietary KEY_STAR / KEY_POUND. We normalise everything to a char plus
 *    a small set of modifier codes.
 *  - We do NOT intercept KEY_STAR or KEY_HASH; the IME owns them, and text comes
 *    back to us through setText(). Letting the platform IME edit the buffer is
 *    what makes long nicks and non-Latin text usable.
 *  - Our own caret model is used only for the rendering cursor. If the platform
 *    IME calls setText() we mark the buffer as "caret at end" and re-render.
 */
public final class InputLine {

    public static final int MAX_INPUT = 480;

    private StringBuffer buf = new StringBuffer(120);
    private int caret;
    private boolean composing;
    /** Set when the user is cycling nick completions, so Tab advances. */
    private String[] completions;
    private int completionIndex;
    private String completionPrefix;

    public InputLine() {
        caret = 0;
    }

    public String getText() {
        return buf.toString();
    }

    public int length() {
        return buf.length();
    }

    public boolean isEmpty() {
        return buf.length() == 0;
    }

    public int getCaret() {
        return caret;
    }

    public void setCaret(int p) {
        if (p < 0) {
            p = 0;
        }
        if (p > buf.length()) {
            p = buf.length();
        }
        caret = p;
    }

    /** Replace the whole buffer, caret to end. Used by the platform IME. */
    public void setText(String s) {
        buf.setLength(0);
        if (s != null) {
            buf.append(clip(s));
        }
        caret = buf.length();
        clearCompletions();
    }

    public void clear() {
        buf.setLength(0);
        caret = 0;
        clearCompletions();
    }

    private void clearCompletions() {
        completions = null;
        completionIndex = -1;
        completionPrefix = null;
    }

    private static String clip(String s) {
        if (s.length() <= MAX_INPUT) {
            return s;
        }
        return s.substring(0, MAX_INPUT);
    }

    public void insert(char c) {
        if (buf.length() >= MAX_INPUT) {
            return;
        }
        buf.insert(caret, c);
        caret++;
        clearCompletions();
    }

    public void insert(String s) {
        if (s == null || s.length() == 0) {
            return;
        }
        int room = MAX_INPUT - buf.length();
        if (room <= 0) {
            return;
        }
        String t = s.length() > room ? s.substring(0, room) : s;
        buf.insert(caret, t);
        caret += t.length();
        clearCompletions();
    }

    public void backspace() {
        if (caret <= 0) {
            return;
        }
        buf.deleteCharAt(caret - 1);
        caret--;
        clearCompletions();
    }

    public void delete() {
        if (caret >= buf.length()) {
            return;
        }
        // FALL THROUGH to the charAt/deleteCharAt below.
        buf.deleteCharAt(caret);
        clearCompletions();
    }

    public void moveLeft() {
        if (caret > 0) {
            caret--;
            clearCompletions();
        }
    }

    public void moveRight() {
        if (caret < buf.length()) {
            caret++;
            clearCompletions();
        }
    }

    public void moveHome() {
        caret = 0;
    }

    public void moveEnd() {
        caret = buf.length();
    }

    /** Delete the word at the caret plus the space after it, like a shell. */
    public void clearWord() {
        int start = caret;
        while (start > 0 && buf.charAt(start - 1) != ' ') {
            start--;
        }
        int end = caret;
        while (end < buf.length() && buf.charAt(end) == ' ') {
            end++;
        }
        buf.delete(start, end);
        caret = start;
        clearCompletions();
    }

    /**
     * @return the word the caret sits in, or "" when the caret follows a space.
     */
    public String currentWord() {
        int start = caret;
        while (start > 0 && buf.charAt(start - 1) != ' ') {
            start--;
        }
        int end = caret;
        while (end < buf.length() && buf.charAt(end) != ' ') {
            end++;
        }
        return buf.substring(start, end);
    }

    public int wordStart() {
        int start = caret;
        while (start > 0 && buf.charAt(start - 1) != ' ') {
            start--;
        }
        return start;
    }

    public int wordEnd() {
        int end = caret;
        while (end < buf.length() && buf.charAt(end) != ' ') {
            end++;
        }
        return end;
    }

    /**
     * First Tab on a word replaces it with the first match. Further Tabs cycle.
     *
     * @param matches candidates from NickComplete; null or empty means no-op.
     * @return true when something was inserted.
     */
    public boolean complete(String[] matches) {
        if (matches == null || matches.length == 0) {
            clearCompletions();
            return false;
        }
        int start = wordStart();
        if (completions != null && matches.length == completions.length) {
            // A cycle is already running for this word: advance it.
            completionIndex = (completionIndex + 1) % matches.length;
        } else {
            completions = matches;
            completionPrefix = buf.substring(start, caret);
            completionIndex = 0;
        }
        String chosen = matches[completionIndex];
        buf.replace(start, caret, chosen);
        caret = start + chosen.length();
        return true;
    }

    /** The current completion cycle, for display as a hint. */
    public String completionHint() {
        if (completions == null || completionIndex < 0) {
            return null;
        }
        int total = completions.length;
        if (total <= 1) {
            return completions[0];
        }
        return completions[completionIndex] + "  (" + (completionIndex + 1) + "/" + total + ")";
    }

    public boolean isComposing() {
        return composing;
    }

    public void setComposing(boolean b) {
        composing = b;
    }

    /** Take the buffer and reset. */
    public String consume() {
        String s = buf.toString();
        clear();
        return s;
    }
}
