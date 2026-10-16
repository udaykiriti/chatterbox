package com.chatterbox.ui;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import com.chatterbox.core.ChannelState;
import com.chatterbox.core.IrcEventListener;
import com.chatterbox.core.Message;
import com.chatterbox.core.ServerState;
import com.chatterbox.core.SessionManager;
import com.chatterbox.core.Theme;
import com.chatterbox.net.IrcConnection;
import com.chatterbox.rms.ProfileStore;

/**
 * Application entry point and UI coordinator.
 *
 * MIDlet lifecycle, and what each callback does here:
 *
 *   startApp()    Build or restore the display stack. Idempotent: S60 calls it
 *                 again after every return from the background, and some
 *                 firmwares call it twice in a row on first launch.
 *
 *   pauseApp()    Called when the user switches away or answers a call. We do
 *                 NOT touch the sockets here. On MIDP the MIDlet stays alive
 *                 after pauseApp for up to a short grace period, and on S60
 *                 third edition it stays alive essentially indefinitely as
 *                 long as we hold no Alert. Killing the connection here would
 *                 mean a visible reconnect cycle on every phone call. What we
 *                 do instead: flush RMS logs, stop the repaint timer, and set a
 *                 flag so the first paint after resume can refresh.
 *
 *   destroyApp()  Called on exit or on a hard kill. Flush logs, quit cleanly.
 *                 We cannot rely on it running at all, which is why nothing
 *                 important is written only here.
 *
 * Threading: Display.callSerially is the only sanctioned way to touch UI from a
 * non-MIDlet thread. Our IrcConnection callbacks arrive on socket threads, so
 * every callback posts a small Runnable via callSerially. We coalesce redraws
 * with a dirty flag rather than calling repaint() per line, because the AMS
 * merges repaint requests but each repaint still costs a full screen redraw on
 * these devices.
 */
public final class ChatterboxMIDlet extends MIDlet implements IrcEventListener {

    private Display display;
    private SessionManager manager;
    private ProfileStore store;

    private ServerListScreen serverList;
    private ChatCanvas chatCanvas;

    private Theme theme;
    private boolean paused;
    private boolean needRefresh;
    private int backgroundAlerts;

    /** Vibration/bell handling. */
    private Notifier alert;

    public ChatterboxMIDlet() {
        super();
    }

    protected void startApp() throws MIDletStateChangeException {
        if (display == null) {
            try {
                boot();
            } catch (Throwable t) {
                // A failure here must still give the user a usable screen with
                // an explanation, not a silent black screen.
                display = Display.getDisplay(this);
                javax.microedition.lcdui.Form fatal =
                        new javax.microedition.lcdui.Form("Chatterbox");
                fatal.append(new javax.microedition.lcdui.StringItem(
                        "Startup failed", errorOf(t)));
                fatal.addCommand(new javax.microedition.lcdui.Command(
                        "Exit", javax.microedition.lcdui.Command.EXIT, 1));
                fatal.setCommandListener(new FatalFormListener());
                display.setCurrent(fatal);
                return;
            }
        }
        paused = false;
        if (needRefresh) {
            needRefresh = false;
            refreshServerList();
        }
        if (chatCanvas != null) {
            chatCanvas.resume();
        }
        manager.startAll();
    }

    private void boot() {
        display = Display.getDisplay(this);
        store = new ProfileStore();
        theme = Theme.of(store.getTheme());
        alert = new Notifier();
        alert.setDisplay(Display.getDisplay(this));
        Notifier.Handler.install(display);
        manager = new SessionManager();
        manager.setListener(this);
        manager.setLogEnabled(store.isLogToRms());

        serverList = new ServerListScreen(this, manager, store);
        chatCanvas = new ChatCanvas(this, manager, theme);
        serverList.loadProfiles();
        if (manager.size() == 0) {
            serverList.seedDefaultProfile();
        }
        display.setCurrent(serverList);
        manager.startAll();
    }

    protected void pauseApp() {
        paused = true;
        if (chatCanvas != null) {
            chatCanvas.pause();
        }
        // Persist now: this is the only callback guaranteed on a clean exit.
        try {
            store.savePrefs();
            manager.flushLogs();
            serverList.saveProfiles();
        } catch (Throwable t) {
            // Out of storage or store in use; nothing useful to do.
        }
    }

    protected void destroyApp(boolean unconditional) {
        try {
            if (store != null) {
                store.savePrefs();
            }
            if (manager != null) {
                manager.flushLogs();
                manager.stopAll("Chatterbox closing");
            }
        } catch (Throwable t) {
            // Ignore.
        }
    }

    private static String errorOf(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.length() == 0) {
            m = t.getClass().getName();
        }
        return m;
    }

    // ------------------------------------------------------------------
    // Accessors for the screens
    // ------------------------------------------------------------------

    public Display getDisplay() {
        return display;
    }

    public ProfileStore getStore() {
        return store;
    }

    public SessionManager getManager() {
        return manager;
    }

    public ServerListScreen getServerList() {
        return serverList;
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(int kind) {
        store.setTheme(kind);
        theme = Theme.of(kind);
        if (chatCanvas != null) {
            chatCanvas.applyTheme(theme);
        }
        refreshServerList();
    }

    public boolean isPaused() {
        return paused;
    }

    public void show(ChatCanvas c) {
        display.setCurrent(c);
    }

    public void showScreen(javax.microedition.lcdui.Displayable d) {
        if (display != null && d != null) {
            display.setCurrent(d);
        }
    }

    public void showServerList() {
        refreshServerList();
        display.setCurrent(serverList);
    }

    public void refreshServerList() {
        if (serverList != null) {
            serverList.refresh();
        }
    }

    /** Called by the settings screen after a save. */
    public void onProfilesChanged() {
        refreshServerList();
        manager.startAll();
    }

    public void openChannel(IrcConnection conn, ChannelState win) {
        if (conn == null || win == null) {
            return;
        }
        conn.getServer().setActive(win);
        chatCanvas.attach(conn, win);
        display.setCurrent(chatCanvas);
    }

    public Notifier getAlert() {
        return alert;
    }

    // ------------------------------------------------------------------
    // IrcEventListener: every method here runs on a socket thread
    // ------------------------------------------------------------------

    public void onMessage(final ServerState s, final ChannelState w, final Message m) {
        post(new Runnable() {
            public void run() {
                if (w != null) {
                    w.touch();
                }
                if (chatCanvas != null) {
                    chatCanvas.onDataChanged();
                }
                if (backgroundAlerts > 0 && w != null && w.isPrivate()) {
                    backgroundAlerts--;
                }
            }
        });
    }

    public void onStateChanged(final ServerState s, final int state, final String text) {
        post(new Runnable() {
            public void run() {
                if (state == ServerState.DISCONNECTED) {
                    String msg = "Disconnected from " + s.getHost();
                    if (text != null && text.length() > 0) {
                        msg = msg + ": " + text;
                    }
                    notifyUser(s.getHost() + " offline", msg);
                }
                refreshServerList();
                if (chatCanvas != null) {
                    chatCanvas.onStateChanged(s);
                }
            }
        });
    }

    public void onNickInUse(final ServerState s, final String attempted, final String chosen) {
        post(new Runnable() {
            public void run() {
                notifyUser("Nickname in use",
                        "'" + attempted + "' is taken, now using '" + chosen + "'");
            }
        });
    }

    public void onWindowOpened(final ServerState s, final ChannelState w) {
        post(new Runnable() {
            public void run() {
                refreshServerList();
            }
        });
    }

    public void onWindowClosed(final ServerState s, final ChannelState w) {
        post(new Runnable() {
            public void run() {
                refreshServerList();
            }
        });
    }

    public void onTopicChanged(final ServerState s, final ChannelState c, final String topic) {
        post(new Runnable() {
            public void run() {
                if (chatCanvas != null) {
                    chatCanvas.setHeaderTopic(topic);
                }
                refreshServerList();
            }
        });
    }

    public void onError(final ServerState s, final String reason) {
        post(new Runnable() {
            public void run() {
                notifyUser(s.getHost() + " error", Str_clip(reason));
                refreshServerList();
            }
        });
    }

    private static String Str_clip(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 160 ? s.substring(0, 160) : s;
    }

    public void onAlert(final ServerState s, final ChannelState w,
                        final boolean mention, final boolean priv) {
        // Alerting is the one thing we deliberately do NOT defer to
        // callSerially: if the screen is busy the user still needs the buzz.
        // Alert.vibrate/vibrate is documented as callable from any thread.
        try {
            if (priv) {
                alert.privateMessage(paused);
            } else {
                alert.mention(paused);
            }
        } catch (Throwable t) {
            // A handset with vibration disabled throws here on some firmware.
        }
    }

    public void onLag(final ServerState s, final long millis) {
        post(new Runnable() {
            public void run() {
                if (chatCanvas != null) {
                    chatCanvas.onLagChanged();
                }
            }
        });
    }

    /**
     * Marshal work onto the MIDlet thread. During pauseApp the AMS may refuse
     * new callSerially requests on some devices, so we set needRefresh and skip.
     */
    private void post(Runnable r) {
        if (display == null) {
            return;
        }
        try {
            display.callSerially(r);
        } catch (Throwable t) {
            needRefresh = true;
        }
    }

    /**
     * Show a non-blocking notification. We deliberately do NOT use an Alert
     * here: a modal Alert steals focus from the chat view and on S60 causes a
     * full-screen transition that users find jarring for transient errors.
     * Instead we bump a badge and put the text in the server list.
     */
    void notifyUser(String title, String body) {
        try {
            if (display != null) {
                javax.microedition.lcdui.Alert a =
                        new javax.microedition.lcdui.Alert(title, body, null,
                                javax.microedition.lcdui.Alert.ALERT_TIMEOUT);
                a.setTimeout(3000);
                display.setCurrent(a, display.getCurrent());
            }
        } catch (Throwable t) {
            // Screen transition refused; the text is already in the window.
        }
    }

    /** Minimal listener that just acknowledges the fatal error screen. */
    private final class FatalFormListener implements javax.microedition.lcdui.CommandListener {
        public void commandAction(javax.microedition.lcdui.Command c,
                                   javax.microedition.lcdui.Displayable d) {
            try {
                destroyApp(true);
            } catch (Throwable t) {
                // Ignore.
            }
            notifyDestroyed();
        }
    }

    /** Convenience for screens that need to report a storage failure. */
    void reportStorage(String what) {
        notifyUser("Storage", what);
    }
}
