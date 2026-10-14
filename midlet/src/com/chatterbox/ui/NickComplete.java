package com.chatterbox.ui;

import com.chatterbox.core.ChannelState;
import com.chatterbox.core.IrcCase;
import com.chatterbox.core.Str;
import com.chatterbox.core.UserEntry;

/**
 * Tab-completion over a channel's nick list.
 *
 * Pure logic, unit-testable. Rules:
 *  - Completion only triggers at the start of a word or after whitespace, so
 *    "foo bo|" completes "bob" but "foo: bo|" does too while "foob|" does not
 *    re-complete the middle of a word. Users expect both actually; we allow it
 *    at any word boundary OR at the start of a nick token after ':' which is
 *    how most IRC clients behave.
 *  - Case-insensitive prefix match using IRC casemapping.
 *  - Matches are sorted alphabetically so cycling is predictable; a prefix that
 *    matches exactly one nick still completes (to add the trailing ':').
 *  - An exact match on one nick inserts "nick: " and a trailing space.
 *
 * Assumption: the channel list is small, so a linear scan is correct and
 * cheaper than building a trie on a phone.
 */
public final class NickComplete {

    private NickComplete() {
    }

    /**
     * @param partial what the user has typed so far for this word.
     * @return candidates, or an empty array when nothing matches.
     */
    public static String[] matches(ChannelState channel, String partial) {
        String p = Str.stripPrefixModes(Str.nvl(partial));
        // A leading colon means the user already typed a separator.
        if (p.length() > 0 && p.charAt(0) == ':') {
            p = p.substring(1);
        }
        if (p.length() == 0 || channel == null) {
            return new String[0];
        }
        java.util.Vector out = new java.util.Vector();
        java.util.Vector users = channel.snapshotUsers();
        for (int i = 0; i < users.size(); i++) {
            UserEntry u = (UserEntry) users.elementAt(i);
            if (hasPrefix(u.getNick(), p)) {
                out.addElement(u.getNick());
            }
        }
        if (out.size() == 0) {
            return new String[0];
        }
        sortNicks(out);
        String[] a = new String[out.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = (String) out.elementAt(i);
        }
        return a;
    }

    /**
     * Decide whether Tab should complete at all.
     *
     * @return true when the caret is at a position where a nick makes sense:
     *         start of input, or immediately after a space.
     */
    public static boolean shouldComplete(InputLine line) {
        // Only at the end of the partial word: "bo|" completes, "bo|x" does not.
        if (line.getCaret() != line.wordEnd()) {
            return false;
        }
        int start = line.wordStart();
        if (start == 0) {
            return true;
        }
        char prev = line.getText().charAt(start - 1);
        return prev == ' ' || prev == ':' || prev == ',' || prev == '@';
    }

    /**
     * Turn raw matches into insertable strings, adding the ':' separator when
     * there is exactly one match and the user already typed it.
     */
    public static String[] decorate(String[] matches, String partial) {
        if (matches == null || matches.length == 0) {
            return new String[0];
        }
        if (matches.length == 1) {
            return new String[] { matches[0] + ": " };
        }
        return matches;
    }

    private static boolean hasPrefix(String nick, String partial) {
        String n = IrcCase.normalize(nick);
        String p = IrcCase.normalize(partial);
        if (p.length() > n.length()) {
            return false;
        }
        for (int i = 0; i < p.length(); i++) {
            if (n.charAt(i) != p.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Insertion sort on IRC-normalised keys. Stable, O(n^2) worst case, fine for
     * the 10-200 nicks of a typical channel.
     */
    static void sortNicks(java.util.Vector nicks) {
        int n = nicks.size();
        for (int i = 1; i < n; i++) {
            String cur = (String) nicks.elementAt(i);
            String curKey = IrcCase.normalize(cur);
            int j = i - 1;
            while (j >= 0 && compareNicks(IrcCase.normalize((String) nicks.elementAt(j)),
                    curKey) > 0) {
                nicks.setElementAt(nicks.elementAt(j), j + 1);
                j--;
            }
            nicks.setElementAt(cur, j + 1);
        }
    }

    private static int compareNicks(String a, String b) {
        int n = a.length() < b.length() ? a.length() : b.length();
        for (int i = 0; i < n; i++) {
            char ca = a.charAt(i);
            char cb = b.charAt(i);
            if (ca == cb) {
                continue;
            }
            return ca < cb ? -1 : 1;
        }
        return a.length() - b.length();
    }

    // ---- Command completion ----

    private static final String[] COMMANDS = {
        "join", "part", "msg", "me", "nick", "topic", "quit", "query",
        "whois", "who", "names", "mode", "kick", "away", "back", "cycle",
        "clear", "close", "raw", "help"
    };

    /** Complete a slash command. Returns decorated matches with trailing space. */
    public static String[] completeCommand(String partial) {
        if (partial == null || partial.length() == 0) {
            return decorate(COMMANDS, partial);
        }
        String p = partial.toLowerCase();
        java.util.Vector matches = new java.util.Vector();
        for (int i = 0; i < COMMANDS.length; i++) {
            if (COMMANDS[i].startsWith(p)) {
                matches.addElement(COMMANDS[i]);
            }
        }
        String[] out = new String[matches.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (String) matches.elementAt(i);
        }
        return decorate(out, partial);
    }

    // ---- Channel completion ----

    /** Complete a channel name from a list of known channels. */
    public static String[] completeChannel(String partial, String[] channels) {
        if (channels == null || channels.length == 0) {
            return new String[0];
        }
        String p = partial.toLowerCase();
        java.util.Vector matches = new java.util.Vector();
        for (int i = 0; i < channels.length; i++) {
            String c = channels[i];
            if (c.toLowerCase().startsWith(p)) {
                matches.addElement(c);
            }
        }
        String[] out = new String[matches.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (String) matches.elementAt(i);
        }
        return decorate(out, partial);
    }
}

