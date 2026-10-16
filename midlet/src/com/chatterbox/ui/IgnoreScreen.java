package com.chatterbox.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextField;

import com.chatterbox.core.IgnoreList;

/**
 * Ignore list management screen.
 */
public final class IgnoreScreen extends List implements CommandListener {

    private final ChatterboxMIDlet midlet;
    private final IgnoreList ignoreList;

    private final Command addCmd = new Command("Add", Command.SCREEN, 1);
    private final Command removeCmd = new Command("Remove", Command.SCREEN, 2);
    private final Command backCmd = new Command("Back", Command.BACK, 3);

    public IgnoreScreen(ChatterboxMIDlet midlet, IgnoreList list) {
        super("Ignore List (" + list.size() + ")", List.SINGLE);
        this.midlet = midlet;
        this.ignoreList = list;

        addCommand(addCmd);
        addCommand(removeCmd);
        addCommand(backCmd);
        setCommandListener(this);

        refresh();
    }

    private void refresh() {
        deleteAll();
        java.util.Vector entries = ignoreList.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            IgnoreList.Entry e = (IgnoreList.Entry) entries.elementAt(i);
            String type = e.matchHost ? "host" : "nick";
            append("[" + type + "] " + e.pattern, null);
        }
    }

    public void commandAction(Command c, Displayable d) {
        int idx = getSelectedIndex();
        if (c == backCmd || c.getCommandType() == Command.BACK) {
            midlet.showServerList();
            return;
        }
        if (c == addCmd) {
            openAddBox();
            return;
        }
        if (c == removeCmd && idx >= 0) {
            java.util.Vector entries = ignoreList.getEntries();
            if (idx < entries.size()) {
                IgnoreList.Entry e = (IgnoreList.Entry) entries.elementAt(idx);
                ignoreList.remove(e.pattern);
                refresh();
            }
            return;
        }
    }

    private void openAddBox() {
        Form f = new Form("Add Ignore");
        final TextField pattern = new TextField("Pattern (use * ?)", "", 64, TextField.ANY);
        final javax.microedition.lcdui.ChoiceGroup type = new javax.microedition.lcdui.ChoiceGroup("Match",
                new String[] { "Nick", "Host (user@host)" }, 1,
                javax.microedition.lcdui.ChoiceGroup.EXCLUSIVE);
        f.append(pattern);
        f.append(type);
        f.addCommand(new Command("Add", Command.OK, 1));
        f.addCommand(new Command("Cancel", Command.BACK, 2));
        f.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getLabel().equals("Add")) {
                    String p = pattern.getString().trim();
                    boolean host = type.getSelectedIndex() == 1;
                    if (p.length() > 0) {
                        if (host && p.indexOf('@') < 0) {
                            p = "*@" + p;
                        }
                        ignoreList.add(p);
                    }
                    refresh();
                    midlet.showScreen(IgnoreScreen.this);
                } else {
                    midlet.showScreen(IgnoreScreen.this);
                }
            }
        });
        midlet.getDisplay().setCurrent(f);
    }
}