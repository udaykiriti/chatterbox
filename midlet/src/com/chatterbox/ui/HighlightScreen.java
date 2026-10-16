package com.chatterbox.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.ChoiceGroup;

import com.chatterbox.core.HighlightRules;

/**
 * Highlight rules management screen.
 */
public final class HighlightScreen extends List implements CommandListener {

    private final ChatterboxMIDlet midlet;
    private final HighlightRules rules;

    private final Command addCmd = new Command("Add", Command.SCREEN, 1);
    private final Command editCmd = new Command("Edit", Command.SCREEN, 2);
    private final Command removeCmd = new Command("Remove", Command.SCREEN, 3);
    private final Command backCmd = new Command("Back", Command.BACK, 4);

    public HighlightScreen(ChatterboxMIDlet midlet, HighlightRules r) {
        super("Highlights (" + r.size() + ")", javax.microedition.lcdui.Choice.EXCLUSIVE);
        this.midlet = midlet;
        this.rules = r;

        addCommand(addCmd);
        addCommand(editCmd);
        addCommand(removeCmd);
        addCommand(backCmd);
        setCommandListener(this);

        refresh();
    }

    private void refresh() {
        deleteAll();
        java.util.Vector list = rules.getRules();
        for (int i = 0; i < list.size(); i++) {
            HighlightRules.Rule r = (HighlightRules.Rule) list.elementAt(i);
            StringBuffer sb = new StringBuffer(48);
            sb.append(r.pattern);
            if (r.wholeWord) sb.append(" [word]");
            if (r.caseSensitive) sb.append(" [case]");
            if (r.matchHost) sb.append(" [host]");
            append(sb.toString(), null);
        }
    }

    public void commandAction(Command c, Displayable d) {
        int idx = getSelectedIndex();
        if (c == backCmd || c.getCommandType() == Command.BACK) {
            midlet.showServerList();
            return;
        }
        if (c == addCmd) {
            openRuleForm(null);
            return;
        }
        if (c == editCmd && idx >= 0) {
            java.util.Vector list = rules.getRules();
            if (idx < list.size()) {
                openRuleForm((HighlightRules.Rule) list.elementAt(idx));
            }
            return;
        }
        if (c == removeCmd && idx >= 0) {
            java.util.Vector list = rules.getRules();
            if (idx < list.size()) {
                HighlightRules.Rule r = (HighlightRules.Rule) list.elementAt(idx);
                rules.remove(r.pattern);
                refresh();
            }
            return;
        }
    }

    private void openRuleForm(final HighlightRules.Rule existing) {
        Form f = new Form(existing == null ? "Add Highlight" : "Edit Highlight");
        final TextField pattern = new TextField("Pattern", existing == null ? "" : existing.pattern, 64, TextField.ANY);
        final ChoiceGroup whole = new ChoiceGroup("Whole word",
                javax.microedition.lcdui.Choice.EXCLUSIVE,
                new String[] { "Yes", "No" }, null);
        final ChoiceGroup cases = new ChoiceGroup("Case sensitive",
                javax.microedition.lcdui.Choice.EXCLUSIVE,
                new String[] { "Yes", "No" }, null);
        final ChoiceGroup host = new ChoiceGroup("Match host",
                javax.microedition.lcdui.Choice.EXCLUSIVE,
                new String[] { "Yes", "No" }, null);
        f.append(pattern);
        whole.setSelectedIndex(1, true);
        f.append(whole);
        cases.setSelectedIndex(1, true);
        f.append(cases);
        host.setSelectedIndex(1, true);
        f.append(host);

        if (existing != null) {
            whole.setSelectedIndex(existing.wholeWord ? 0 : 1, true);
            cases.setSelectedIndex(existing.caseSensitive ? 0 : 1, true);
            host.setSelectedIndex(existing.matchHost ? 0 : 1, true);
        }

        f.addCommand(new Command("Save", Command.OK, 1));
        f.addCommand(new Command("Cancel", Command.BACK, 2));
        f.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getLabel().equals("Save")) {
                    String p = pattern.getString().trim();
                    if (p.length() > 0) {
                        HighlightRules.Rule r = existing == null ? new HighlightRules.Rule(p) : existing;
                        r.pattern = p;
                        r.wholeWord = whole.getSelectedIndex() == 0;
                        r.caseSensitive = cases.getSelectedIndex() == 0;
                        r.matchHost = host.getSelectedIndex() == 0;
                        rules.add(r);
                    }
                    refresh();
                    midlet.showScreen(HighlightScreen.this);
                } else {
                    midlet.showScreen(HighlightScreen.this);
                }
            }
        });
        midlet.getDisplay().setCurrent(f);
    }
}