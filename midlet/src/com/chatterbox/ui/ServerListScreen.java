package com.chatterbox.ui;

import java.util.Vector;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

import com.chatterbox.core.ChannelState;
import com.chatterbox.core.SessionManager;
import com.chatterbox.core.Str;
import com.chatterbox.net.IrcConnection;
import com.chatterbox.rms.Profile;
import com.chatterbox.rms.ProfileStore;

/**
 * Top-level screen: servers, then that server's windows as list entries.
 *
 * A List is the right control here rather than a custom Canvas because the
 * platform List already handles the Nokia softkey menu, the scrollbar, and the
 * selection highlight correctly on every firmware we target. Fighting that is
 * how most J2ME clients end up with broken key handling on one device.
 *
 * Assumptions:
 *  - Entries are built as "  name" so the List's own bullets show hierarchy,
 *    which is the cheapest possible tree on MIDP.
 *  - We rebuild the whole list on refresh rather than trying to patch items.
 *    With < 50 entries this costs a few milliseconds and is far less bug-prone.
 *  - Unread badges are appended inline: "[#chan (3)]".
 */
public final class ServerListScreen extends List implements CommandListener {

    private final ChatterboxMIDlet midlet;
    private final SessionManager manager;
    private final ProfileStore store;

    private Vector profiles = new Vector();
    /** Parallel array: profile index -> server id, for mapping selections. */
    private Vector serverIds = new Vector();
    private Vector windowRefs = new Vector();

    private static final String[] TOP_LEVEL = { "Servers", "Settings", "About" };

    private final Command openCmd = new Command("Open", Command.OK, 1);
    private final Command connectCmd = new Command("Connect", Command.SCREEN, 2);
    private final Command disconnectCmd = new Command("Disconnect", Command.SCREEN, 3);
    private final Command editCmd = new Command("Edit", Command.ITEM, 4);
    private final Command addCmd = new Command("Add server", Command.SCREEN, 5);
    private final Command delCmd = new Command("Delete", Command.ITEM, 6);
    private final Command settingsCmd = new Command("Settings", Command.SCREEN, 7);
    private final Command aboutCmd = new Command("About", Command.SCREEN, 8);
    private final Command jumpCmd = new Command("Jump to", Command.SCREEN, 9);

    public ServerListScreen(ChatterboxMIDlet midlet, SessionManager manager,
                            ProfileStore store) {
        super("Chatterbox", new String[] { "Loading..." }, List.SINGLE);
        this.midlet = midlet;
        this.manager = manager;
        this.store = store;
        addCommand(openCmd);
        addCommand(connectCmd);
        addCommand(disconnectCmd);
        addCommand(jumpCmd);
        addCommand(addCmd);
        addCommand(settingsCmd);
        addCommand(aboutCmd);
        addCommand(delCmd);
        setCommandListener(this);
        loadProfiles();
    }

    public void seedDefaultProfile() {
        profiles.addElement(ProfileStore.defaultProfile());
        saveProfiles();
    }

    public void loadProfiles() {
        profiles = store.loadProfiles();
        rebuild();
    }

    public void saveProfiles() {
        store.saveProfiles(profiles);
    }

    /** Rebuild list contents from live connection state. */
    public void refresh() {
        rebuild();
    }

    private void rebuild() {
        int oldSelected = getSelectedIndex();
        deleteAll();
        serverIds.removeAllElements();
        windowRefs.removeAllElements();

        for (int i = 0; i < profiles.size(); i++) {
            Profile p = (Profile) profiles.elementAt(i);
            IrcConnection c = manager.byServerId(p.serverId());
            StringBuffer label = new StringBuffer(48);
            label.append('[');
            label.append(c == null ? "  " : stateMark(c));
            label.append("] ");
            label.append(p.getName().length() > 0 ? p.getName() : p.getHost());
            if (c != null) {
                int un = c.getServer().totalUnread();
                int hl = c.getServer().totalHighlights();
                if (hl > 0) {
                    label.append("  !").append(hl);
                } else if (un > 0) {
                    label.append("  (").append(un).append(')');
                }
            }
            append(label.toString(), null);
            serverIds.addElement(c == null ? p.serverId() : c.getServer().getId());
            windowRefs.addElement(null);

            if (c != null) {
                Vector names = c.getServer().windowNames();
                for (int j = 0; j < names.size(); j++) {
                    ChannelState w = c.getServer().window((String) names.elementAt(j));
                    if (w == null) {
                        continue;
                    }
                    StringBuffer wl = new StringBuffer(48);
                    wl.append("   ");
                    wl.append(w.getName());
                    if (w.isParted()) {
                        wl.append(" (parted)");
                    } else if (w.isPrivate() && w.userCount() > 0) {
                        wl.append(" (").append(w.userCount()).append(')');
                    }
                    String badge = w.badge();
                    if (badge.length() > 0) {
                        wl.append(' ').append(badge);
                    }
                    append(wl.toString(), null);
                    serverIds.addElement(c.getServer().getId());
                    windowRefs.addElement(w);
                }
            }
        }

        if (getItemCount() == 0) {
            append("No servers configured. Press Options > Add server.", null);
            serverIds.addElement("");
            windowRefs.addElement(null);
        }
        if (oldSelected >= 0 && oldSelected < getItemCount()) {
            setSelectedIndex(oldSelected);
        }
        repaint();
    }

    private String stateMark(IrcConnection c) {
        int s = c.getServer().getState();
        if (s == com.chatterbox.core.ServerState.CONNECTED) {
            return c.getServer().isSecure() ? "S" : "C";
        }
        if (s == com.chatterbox.core.ServerState.CONNECTING
                || s == com.chatterbox.core.ServerState.REGISTERING) {
            return ".";
        }
        if (s == com.chatterbox.core.ServerState.RECONNECTING) {
            return "r";
        }
        return "x";
    }

    public void commandAction(Command c, Displayable d) {
        int idx = getSelectedIndex();
        if (idx < 0) {
            return;
        }
        String sid = idx < serverIds.size() ? (String) serverIds.elementAt(idx) : "";
        Object wref = idx < windowRefs.size() ? windowRefs.elementAt(idx) : null;

        if (c == openCmd) {
            onOpen(sid, wref);
            return;
        }
        if (c == connectCmd || c == disconnectCmd) {
            IrcConnection conn = manager.byServerId(sid);
            if (conn != null) {
                if (conn.isRunning()) {
                    conn.stopGracefully("disconnected by user");
                    conn.abort();
                } else {
                    conn.start();
                }
            }
            refresh();
            return;
        }
        if (c == jumpCmd) {
            ChannelState hot = manager.hottest();
            if (hot == null) {
                notify("Nothing to jump to.");
            } else {
                midlet.openChannel(findConnectionFor(hot), hot);
            }
            return;
        }
        if (c == addCmd) {
            Profile np = new Profile();
            np.setName("Server " + (profiles.size() + 1));
            editProfile(np, true);
            return;
        }
        if (c == delCmd) {
            if (wref != null) {
                // Deleting a window is cheap and reversible enough; do it.
                ChannelState w = (ChannelState) wref;
                IrcConnection conn = findConnectionFor(w);
                if (conn != null) {
                    conn.getServer().closeWindow(w.getName());
                }
            } else if (sid.length() > 0) {
                for (int i = 0; i < profiles.size(); i++) {
                    Profile p = (Profile) profiles.elementAt(i);
                    if (p.serverId().equals(sid)) {
                        IrcConnection conn = manager.byServerId(sid);
                        if (conn != null) {
                            conn.stopGracefully("removed");
                            manager.remove(conn);
                        }
                        profiles.removeElementAt(i);
                        break;
                    }
                }
            }
            saveProfiles();
            refresh();
            return;
        }
        if (c == editCmd) {
            if (wref != null) {
                midlet.openChannel(findConnectionFor((ChannelState) wref), (ChannelState) wref);
                return;
            }
            for (int i = 0; i < profiles.size(); i++) {
                Profile p = (Profile) profiles.elementAt(i);
                if (p.serverId().equals(sid)) {
                    editProfile(p, false);
                    return;
                }
            }
            notify("That server is gone.");
            return;
        }
        if (c == settingsCmd) {
            midlet.getDisplay().setCurrent(new SettingsScreen(midlet, store));
            return;
        }
        if (c == aboutCmd) {
            notify("Chatterbox 1.0 - IRC for Nokia S60 / Series 40 (MIDP 2.0)");
            return;
        }
    }

    private IrcConnection findConnectionFor(ChannelState w) {
        for (int i = 0; i < manager.size(); i++) {
            IrcConnection c = manager.connection(i);
            if (c.getServer().window(w.getName()) == w) {
                return c;
            }
        }
        return null;
    }

    private void onOpen(String sid, Object wref) {
        IrcConnection conn = sid.length() > 0 ? manager.byServerId(sid) : null;
        if (conn == null) {
            // Not connected yet: start it and jump straight to its status window.
            for (int i = 0; i < profiles.size(); i++) {
                Profile p = (Profile) profiles.elementAt(i);
                if (p.serverId().equals(sid)) {
                    IrcConnection c = manager.create(p, store.toConfig());
                    c.start();
                    ChannelState status = c.getServer().window(
                            c.getServer().getHost(), com.chatterbox.core.ChannelState.TYPE_STATUS);
                    midlet.openChannel(c, status);
                    return;
                }
            }
            notify("Unknown server.");
            return;
        }
        if (wref != null) {
            midlet.openChannel(conn, (ChannelState) wref);
            return;
        }
        ChannelState active = conn.getServer().getActive();
        if (active != null) {
            midlet.openChannel(conn, active);
        } else {
            ChannelState status = conn.getServer().window(conn.getServer().getHost(),
                    com.chatterbox.core.ChannelState.TYPE_STATUS);
            midlet.openChannel(conn, status);
        }
    }

    private void editProfile(final Profile p, boolean isNew) {
        SettingsScreen s = new SettingsScreen(midlet, store, p, isNew,
                new SettingsScreen.SaveCallback() {
            public void onSaved() {
                saveProfiles();
                // Rebuild any connection whose parameters changed.
                IrcConnection existing = manager.byServerId(p.serverId());
                if (existing != null) {
                    existing.stopGracefully("profile updated");
                    manager.remove(existing);
                }
                IrcConnection c = manager.create(p, store.toConfig());
                c.start();
                refresh();
            }
            public void onCancelled() {
                refresh();
            }
        });
        midlet.getDisplay().setCurrent(s);
    }

    private void notify(String text) {
        midlet.notifyUser("Chatterbox", text);
    }

    /** Exposed for the settings screen's "delete all data" action. */
    public void resetProfiles() {
        profiles.removeAllElements();
        saveProfiles();
        rebuild();
    }

    public Vector getProfiles() {
        return profiles;
    }

    /** Convenience for About text without needing a resource bundle. */
    public static String about() {
        StringBuffer sb = new StringBuffer(200);
        sb.append("Chatterbox 1.0\n");
        sb.append("MIDP 2.0 / CLDC 1.1\n\n");
        sb.append("Keys:\n");
        sb.append("  Up/Down scroll, * send, # toggle\n");
        sb.append("  Tab complete nick, 0 topic\n");
        sb.append("  1 nick list, 2 commands, 3 cycle\n\n");
        sb.append("Commands: /join /part /msg /me /nick\n");
        sb.append("  /topic /quit /query /whois /clear\n");
        sb.append("  /away /back /cycle /names /kick /mode\n");
        sb.append("  /raw /help /close\n");
        return Str.clip(sb.toString(), 1000);
    }
}
