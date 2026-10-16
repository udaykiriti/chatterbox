package com.chatterbox.ui;

import java.util.Vector;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

import com.chatterbox.core.ChannelState;
import com.chatterbox.core.Str;
import com.chatterbox.net.IrcConnection;

/**
 * Channel list screen - shows /LIST results with filtering.
 */
public final class ChannelListScreen extends List implements CommandListener {

    private final ChatterboxMIDlet midlet;
    private final IrcConnection conn;
    private Vector channels = new Vector();

    private final Command joinCmd = new Command("Join", Command.OK, 1);
    private final Command filterCmd = new Command("Filter", Command.SCREEN, 2);
    private final Command refreshCmd = new Command("Refresh", Command.SCREEN, 3);
    private final Command backCmd = new Command("Back", Command.BACK, 4);

    private String filter = "";

    public ChannelListScreen(ChatterboxMIDlet midlet, IrcConnection conn) {
        super("Channel List", javax.microedition.lcdui.Choice.EXCLUSIVE);
        this.midlet = midlet;
        this.conn = conn;

        addCommand(joinCmd);
        addCommand(filterCmd);
        addCommand(refreshCmd);
        addCommand(backCmd);
        setCommandListener(this);

        requestList();
    }

    private void requestList() {
        if (filter.length() > 0) {
            conn.getSession().getSender().send(com.chatterbox.proto.IrcCodec.names(filter));
        } else {
            // LIST is better than NAMES for full channel list
            conn.getSender().send("LIST\r\n");
        }
        // Note: results come via 321/322/323 numerics or 353/366
        // For now we'll use a simple approach
    }

    public void addChannel(String name, String topic, int users) {
        StringBuffer sb = new StringBuffer(48);
        sb.append(name).append(" (").append(users).append(") ");
        if (topic != null && topic.length() > 0) {
            sb.append("- ").append(Str.clip(topic, 30));
        }
        channels.addElement(name);
        append(sb.toString(), null);
    }

    public void commandAction(Command c, Displayable d) {
        int idx = getSelectedIndex();
        if (idx < 0 || idx >= channels.size()) {
            return;
        }
        String chan = (String) channels.elementAt(idx);

        if (c == joinCmd) {
            conn.getSession().getSender().send(com.chatterbox.proto.IrcCodec.join(chan));
            ChannelState win = conn.getServer().window(chan, ChannelState.TYPE_CHANNEL);
            midlet.openChannel(conn, win);
        } else if (c == filterCmd) {
            openFilterBox();
        } else if (c == refreshCmd) {
            deleteAll();
            channels.removeAllElements();
            requestList();
        } else if (c == backCmd || c.getCommandType() == Command.BACK) {
            midlet.showServerList();
        }
    }

    private void openFilterBox() {
        javax.microedition.lcdui.TextBox tb = new javax.microedition.lcdui.TextBox("Filter (e.g. #chat*)", filter, 64, javax.microedition.lcdui.TextField.ANY);
        tb.addCommand(new Command("OK", Command.OK, 1));
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getLabel().equals("OK")) {
                    filter = ((javax.microedition.lcdui.TextBox) d).getString();
                    deleteAll();
                    channels.removeAllElements();
                    requestList();
                }
            }
        });
        midlet.getDisplay().setCurrent(tb);
    }
}