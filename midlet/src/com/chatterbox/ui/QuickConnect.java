package com.chatterbox.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;

import com.chatterbox.net.IrcConnection;

/**
 * Quick connect bar - add to server list for fast connections.
 */
public final class QuickConnect extends Form implements CommandListener {

    private final ChatterboxMIDlet midlet;
    private final TextField hostField;
    private final TextField portField;
    private final TextField nickField;
    private final javax.microedition.lcdui.ChoiceGroup tlsGroup;

    private final Command connectCmd = new Command("Connect", Command.OK, 1);
    private final Command backCmd = new Command("Back", Command.BACK, 2);

    public QuickConnect(ChatterboxMIDlet midlet) {
        super("Quick Connect");
        this.midlet = midlet;

        hostField = new TextField("Server", "irc.libera.chat", 64, TextField.ANY);
        portField = new TextField("Port", "6697", 5, TextField.NUMERIC);
        nickField = new TextField("Nick", "chatterbox", 30, TextField.ANY);
        tlsGroup = new javax.microedition.lcdui.ChoiceGroup("Security",
                new String[] { "TLS (ssl://)", "Plain" }, 1,
                javax.microedition.lcdui.ChoiceGroup.EXCLUSIVE);

        append(hostField);
        append(portField);
        append(nickField);
        append(tlsGroup);

        addCommand(connectCmd);
        addCommand(backCmd);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == backCmd || c.getCommandType() == Command.BACK) {
            midlet.showServerList();
            return;
        }
        if (c == connectCmd) {
            String host = hostField.getString().trim();
            int port = com.chatterbox.core.Str.parseInt(portField.getString(), 6667);
            String nick = nickField.getString().trim();
            boolean tls = tlsGroup.getSelectedIndex() == 0;

            if (host.length() == 0 || nick.length() == 0) {
                midlet.notifyUser("Quick Connect", "Server and nick required");
                return;
            }

            // Create a temporary profile
            com.chatterbox.rms.Profile p = new com.chatterbox.rms.Profile();
            p.setName(host);
            p.setHost(host);
            p.setPort(port);
            p.setSecure(tls);
            p.setNick(nick);
            p.setAltNick(nick + "_");

            IrcConnection conn = midlet.getManager().create(p, midlet.getStore().toConfig());
            conn.start();

            com.chatterbox.core.ChannelState status = conn.getServer().window(
                    conn.getServer().getHost(), com.chatterbox.core.ChannelState.TYPE_STATUS);
            midlet.openChannel(conn, status);
        }
    }
}