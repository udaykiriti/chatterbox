package com.chatterbox.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;

import com.chatterbox.core.Str;
import com.chatterbox.rms.LogStore;

/**
 * Local log viewer - reads RMS scrollback for a server.
 */
public final class LogViewer extends Form implements CommandListener {

    private final ChatterboxMIDlet midlet;
    private final LogStore logStore;
    private final TextField content;

    private final Command backCmd = new Command("Back", Command.BACK, 1);
    private final Command refreshCmd = new Command("Refresh", Command.SCREEN, 2);
    private final Command clearCmd = new Command("Clear Log", Command.SCREEN, 3);

    public LogViewer(ChatterboxMIDlet midlet, LogStore log) {
        super("Log: " + log.storeName());
        this.midlet = midlet;
        this.logStore = log;

        content = new TextField("", "", 32000, TextField.ANY | TextField.UNEDITABLE);
        append(content);

        addCommand(backCmd);
        addCommand(refreshCmd);
        addCommand(clearCmd);
        setCommandListener(this);

        refresh();
    }

    private void refresh() {
        String log = logStore.readAll(30000);
        content.setString(log.length() > 0 ? log : "(empty)");
        setTitle("Log: " + logStore.storeName() + " (" + content.getString().length() + " chars)");
    }

    public void commandAction(Command c, Displayable d) {
        if (c == backCmd || c.getCommandType() == Command.BACK) {
            midlet.showServerList();
        } else if (c == refreshCmd) {
            refresh();
        } else if (c == clearCmd) {
            logStore.clear();
            refresh();
            midlet.notifyUser("Log", "Cleared");
        }
    }
}