package com.chatterbox.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

import java.util.Vector;

import com.chatterbox.core.ChannelState;
import com.chatterbox.core.UserEntry;

/**
 * Nick list for the current window.
 *
 * Selecting a nick does the obvious thing for the context: in a channel it
 * inserts "nick: " into the chat input; in a private window it opens a query.
 * That avoids a second tap on the most common action.
 *
 * Assumptions:
 *  - Lists longer than a screen are handled by the platform List, including its
 *    own "a"/"z" jump on the S60 keyboard when the List has focus.
 *  - We do not refresh while displayed; the list is a snapshot. Re-entering
 *    rebuilds it, which is what users expect.
 */
public final class NickListScreen extends List implements CommandListener {

    private final ChatCanvas parent;
    private final ChannelState window;
    private Vector entries = new Vector();

    public NickListScreen(ChatCanvas parent, ChannelState window) {
        super(window.getName() + " (" + window.userCount() + ")",
                javax.microedition.lcdui.Choice.EXCLUSIVE);
        append("no users", null);
        this.parent = parent;
        this.window = window;
        addCommand(new Command("Select", Command.OK, 1));
        addCommand(new Command("Message", Command.SCREEN, 2));
        addCommand(new Command("Who is", Command.SCREEN, 3));
        addCommand(new Command("Back", Command.BACK, 4));
        setCommandListener(this);
        rebuild();
    }

    private void rebuild() {
        deleteAll();
        entries.removeAllElements();
        Vector users = window.snapshotUsers();
        for (int i = 0; i < users.size(); i++) {
            UserEntry u = (UserEntry) users.elementAt(i);
            StringBuffer sb = new StringBuffer(32);
            sb.append(u.isSelf() ? "*" : " ");
            sb.append(u.display());
            if (u.isAway()) {
                sb.append(" (away)");
            }
            append(sb.toString(), null);
            entries.addElement(u);
        }
        if (entries.size() == 0) {
            append("not a member", null);
            entries.addElement(null);
        }
    }

    public void commandAction(Command c, Displayable d) {
        int idx = getSelectedIndex();
        UserEntry u = (idx >= 0 && idx < entries.size())
                ? (UserEntry) entries.elementAt(idx) : null;

        if (c.getCommandType() == Command.BACK) {
            parent.midletShow();
            return;
        }
        if (u == null) {
            return;
        }
        if (c.getLabel().equals("Who is")) {
            parent.execCommand("/whois " + u.getNick());
            return;
        }
        if (c.getLabel().equals("Message")) {
            parent.execCommand("/msg " + u.getNick() + " ");
            return;
        }
        if (window.isChannel()) {
            parent.insertText(u.getNick() + ": ");
        } else {
            parent.insertText(u.getNick() + " ");
        }
    }
}
