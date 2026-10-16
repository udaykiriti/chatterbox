package com.chatterbox.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.ChoiceGroup;

import com.chatterbox.core.ServiceIdentify;

/**
 * Service identify (NickServ/ChanServ) management screen.
 */
public final class ServiceScreen extends List implements CommandListener {

    private final ChatterboxMIDlet midlet;
    private final ServiceIdentify svc;

    private final Command addCmd = new Command("Add", Command.SCREEN, 1);
    private final Command editCmd = new Command("Edit", Command.SCREEN, 2);
    private final Command removeCmd = new Command("Remove", Command.SCREEN, 3);
    private final Command backCmd = new Command("Back", Command.BACK, 4);

    public ServiceScreen(ChatterboxMIDlet midlet, ServiceIdentify s) {
        super("Services (" + s.getServices().size() + ")", javax.microedition.lcdui.Choice.EXCLUSIVE);
        this.midlet = midlet;
        this.svc = s;

        addCommand(addCmd);
        addCommand(editCmd);
        addCommand(removeCmd);
        addCommand(backCmd);
        setCommandListener(this);

        refresh();
    }

    private void refresh() {
        deleteAll();
        java.util.Vector list = svc.getServices();
        for (int i = 0; i < list.size(); i++) {
            ServiceIdentify.Service s = (ServiceIdentify.Service) list.elementAt(i);
            String status = s.enabled ? "on" : "off";
            append(s.name + " (" + status + ")", null);
        }
    }

    public void commandAction(Command c, Displayable d) {
        int idx = getSelectedIndex();
        if (c == backCmd || c.getCommandType() == Command.BACK) {
            midlet.showServerList();
            return;
        }
        if (c == addCmd) {
            openServiceForm(null);
            return;
        }
        if (c == editCmd && idx >= 0) {
            java.util.Vector list = svc.getServices();
            if (idx < list.size()) {
                openServiceForm((ServiceIdentify.Service) list.elementAt(idx));
            }
            return;
        }
        if (c == removeCmd && idx >= 0) {
            java.util.Vector list = svc.getServices();
            if (idx < list.size()) {
                ServiceIdentify.Service s = (ServiceIdentify.Service) list.elementAt(idx);
                svc.removeService(s.name);
                refresh();
            }
            return;
        }
    }

    private void openServiceForm(final ServiceIdentify.Service existing) {
        Form f = new Form(existing == null ? "Add Service" : "Edit Service");
        final TextField name = new TextField("Name", existing == null ? "NickServ" : existing.name, 32, TextField.ANY);
        final TextField pass = new TextField("Password", existing == null ? "" : existing.password, 64, TextField.PASSWORD);
        final TextField acct = new TextField("Account (opt)", existing == null ? "" : existing.account, 32, TextField.ANY);
        final TextField cmd = new TextField("Command", existing == null ? "IDENTIFY" : existing.command, 32, TextField.ANY);
        final ChoiceGroup method = new ChoiceGroup("Method",
                javax.microedition.lcdui.Choice.EXCLUSIVE,
                new String[] { "PRIVMSG", "NOTICE", "QUOTE" }, null);
        final ChoiceGroup enabled = new ChoiceGroup("Enabled",
                javax.microedition.lcdui.Choice.EXCLUSIVE,
                new String[] { "Yes", "No" }, null);

        method.setSelectedIndex(1, true);
        enabled.setSelectedIndex(1, true);

        if (existing != null) {
            method.setSelectedIndex(methodIndex(existing.method), true);
            enabled.setSelectedIndex(existing.enabled ? 0 : 1, true);
        }

        f.append(name);
        f.append(pass);
        f.append(acct);
        f.append(cmd);
        f.append(method);
        f.append(enabled);

        f.addCommand(new Command("Save", Command.OK, 1));
        f.addCommand(new Command("Cancel", Command.BACK, 2));
        f.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getLabel().equals("Save")) {
                    String n = name.getString().trim();
                    String p = pass.getString();
                    if (n.length() > 0 && p.length() > 0) {
                        ServiceIdentify.Service s = existing == null ? new ServiceIdentify.Service(n) : existing;
                        s.name = n;
                        s.password = p;
                        s.account = acct.getString().trim();
                        s.command = cmd.getString().trim();
                        s.method = methodLabel(method.getSelectedIndex());
                        s.enabled = enabled.getSelectedIndex() == 0;
                        svc.addService(s);
                    }
                    refresh();
                    midlet.showScreen(ServiceScreen.this);
                } else {
                    midlet.showScreen(ServiceScreen.this);
                }
            }
        });
        midlet.getDisplay().setCurrent(f);
    }

    private int methodIndex(String m) {
        if ("NOTICE".equals(m)) return 1;
        if ("QUOTE".equals(m)) return 2;
        return 0;
    }

    private String methodLabel(int idx) {
        return idx == 1 ? "NOTICE" : (idx == 2 ? "QUOTE" : "PRIVMSG");
    }
}