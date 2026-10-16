package com.chatterbox.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

import com.chatterbox.core.Str;
import com.chatterbox.rms.LogStore;

/**
 * Local log viewer - reads RMS scrollback for a server.
 */
public final class LogViewer extends Form implements CommandListener {

    /** Fits the TextField maximum with room to spare; setString() throws past it. */
    private static final int MAX_SHOW = 19500;

    private final ChatterboxMIDlet midlet;
    private final LogStore logStore;
    private final TextField content;
    private String filterTerm = "";

    private final Command backCmd = new Command("Back", Command.BACK, 1);
    private final Command refreshCmd = new Command("Refresh", Command.SCREEN, 2);
    private final Command clearCmd = new Command("Clear Log", Command.SCREEN, 3);
    private final Command filterCmd = new Command("Filter", Command.SCREEN, 4);

    public LogViewer(ChatterboxMIDlet midlet, LogStore log) {
        super("Log: " + log.storeName());
        this.midlet = midlet;
        this.logStore = log;

        content = new TextField("Log", "", 20000, TextField.ANY | TextField.UNEDITABLE);
        append(content);

        addCommand(backCmd);
        addCommand(refreshCmd);
        addCommand(filterCmd);
        addCommand(clearCmd);
        setCommandListener(this);

        refresh();
    }

    private void refresh() {
        String log = logStore.readAll(30000);
        int totalLines = countLines(log);
        if (filterTerm.length() > 0) {
            log = filterLines(log, filterTerm);
        }
        // The field holds 20000 chars; setString() throws past the maximum,
        // so truncate first. Large logs used to crash the viewer outright.
        if (log.length() > MAX_SHOW) {
            log = log.substring(0, MAX_SHOW);
        }
        content.setString(log.length() > 0 ? log : "(empty)");
        if (filterTerm.length() > 0) {
            setTitle("Log: " + logStore.storeName() + " [" + filterTerm + ", "
                    + countLines(log) + "/" + totalLines + " lines]");
        } else {
            setTitle("Log: " + logStore.storeName() + " (" + log.length() + " chars)");
        }
    }

    /** Keep only lines containing the term (case-insensitive). */
    private static String filterLines(String log, String term) {
        StringBuffer sb = new StringBuffer(Math.min(log.length(), MAX_SHOW));
        int start = 0;
        while (start < log.length()) {
            int end = log.indexOf('\n', start);
            if (end < 0) {
                end = log.length();
            }
            // indexOf folds inline: no per-line lowercase allocation.
            if (Str.indexOfIgnoreCase(log.substring(start, end), term, 0) >= 0) {
                if (sb.length() + (end - start) + 1 > MAX_SHOW) {
                    break;
                }
                if (end < log.length()) {
                    end++; // keep the newline
                }
                sb.append(log.substring(start, end));
            }
            start = end + 1;
        }
        return sb.toString();
    }

    private static int countLines(String log) {
        int n = 0;
        for (int i = 0; i < log.length(); i++) {
            if (log.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    private void openFilter() {
        final TextBox tb = new TextBox("Filter (empty shows all)", filterTerm, 64, TextBox.ANY);
        tb.addCommand(new Command("Apply", Command.OK, 1));
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getLabel().equals("Apply")) {
                    filterTerm = tb.getString().trim();
                    refresh();
                }
                midlet.showServerList();
            }
        });
        midlet.getDisplay().setCurrent(tb);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == backCmd || c.getCommandType() == Command.BACK) {
            midlet.showServerList();
        } else if (c == refreshCmd) {
            filterTerm = "";
            refresh();
        } else if (c == filterCmd) {
            openFilter();
        } else if (c == clearCmd) {
            logStore.clear();
            filterTerm = "";
            refresh();
            midlet.notifyUser("Log", "Cleared");
        }
    }
}
