package com.chatterbox.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;

import com.chatterbox.core.Str;
import com.chatterbox.rms.Profile;
import com.chatterbox.rms.ProfileStore;

/**
 * Global preferences, and also the per-profile editor when constructed with a
 * profile.
 *
 * A single Form rather than a custom screen: on a 240x320 screen the platform
 * Form gives us a working scroll, a working softkey bar, and correct handling of
 * the S60 in-call interruption. Items are grouped so the user can page through
 * with the arrow keys and never hunt for a setting.
 *
 * MIDP 2.0 Form notes:
 *  - Form.append puts every item on one screen and scrolls. On a 240x320 screen
 *    with the title and softkeys taking ~50 px we can show roughly 12 short
 *    items. Anything more needs paging, which is why this form is short and the
 *    per-profile editor is a separate Form.
 *  - TextField.CONSTRAINED with an allowed character set avoids needing to
 *    validate in software and gives the user immediate feedback. The exception
 *    is the password field: TextField.PASSWORD masks it.
 *  - Select elements for booleans avoid the two-tap checkbox dance.
 *
 * Assumption: the profile editor is reachable only from the server list, so the
 * callback returns there rather than to the chat canvas.
 */
public final class SettingsScreen extends Form implements CommandListener {

    /** Notified when the profile form closes. */
    public interface SaveCallback {
        void onSaved();

        void onCancelled();
    }

    private final ChatterboxMIDlet midlet;
    private final ProfileStore store;

    private Profile editing;
    private boolean isNew;
    private SaveCallback callback;

    // global-prefs items
    private javax.microedition.lcdui.ChoiceGroup themeGroup;
    private javax.microedition.lcdui.ChoiceGroup alertGroup;
    private javax.microedition.lcdui.ChoiceGroup viewGroup;
    private TextField gwHost;
    private TextField gwPort;
    private TextField gwPass;
    private TextField floodDelay;

    // profile items
    private TextField fName;
    private TextField fHost;
    private TextField fPort;
    private javax.microedition.lcdui.ChoiceGroup fSecure;
    private TextField fNick;
    private TextField fAltNick;
    private TextField fPass;
    private TextField fReal;
    private TextField fChannels;
    private javax.microedition.lcdui.ChoiceGroup fE2e;
    private TextField fE2eKey;

    private final Command saveCmd = new Command("Save", Command.OK, 1);
    private final Command cancelCmd = new Command("Cancel", Command.BACK, 2);
    private final Command exportCmd = new Command("Export", Command.SCREEN, 3);
    private final Command importCmd = new Command("Import", Command.SCREEN, 4);

    public SettingsScreen(ChatterboxMIDlet midlet, ProfileStore store) {
        super("Settings");
        this.midlet = midlet;
        this.store = store;
        buildGlobal();
        addCommand(saveCmd);
        addCommand(cancelCmd);
        addCommand(exportCmd);
        addCommand(importCmd);
        setCommandListener(this);
    }

    public SettingsScreen(ChatterboxMIDlet midlet, ProfileStore store,
                          Profile profile, boolean isNew, SaveCallback cb) {
        super(isNew ? "New server" : "Edit server");
        this.midlet = midlet;
        this.store = store;
        this.editing = profile;
        this.isNew = isNew;
        this.callback = cb;
        buildProfile();
        addCommand(saveCmd);
        addCommand(cancelCmd);
        setCommandListener(this);
    }

    /** Convenience overload used by the server list. */
    public void editProfile(Profile p, boolean isNew, SaveCallback cb) {
        this.editing = p;
        this.isNew = isNew;
        this.callback = cb;
        fillProfileForm();
    }

    // ------------------------------------------------------------------
    // Global preferences form
    // ------------------------------------------------------------------

    private void buildGlobal() {
        themeGroup = new javax.microedition.lcdui.ChoiceGroup("Theme",
                javax.microedition.lcdui.Choice.EXCLUSIVE,
                new String[] { "Dark", "Light" }, null);
        themeGroup.setSelectedIndex(store.getTheme(), true);
        append(themeGroup);

        alertGroup = new javax.microedition.lcdui.ChoiceGroup("Alerts",
                javax.microedition.lcdui.Choice.MULTIPLE,
                new String[] { "Vibrate", "Sound" }, null);
        alertGroup.setSelectedFlags(new boolean[] { store.isAlertVibrate(),
                store.isAlertBeep() });
        append(alertGroup);

        viewGroup = new javax.microedition.lcdui.ChoiceGroup("Display",
                javax.microedition.lcdui.Choice.MULTIPLE,
                new String[] { "Timestamps", "Detect URLs", "Log to RMS", "Require TLS" }, null);
        viewGroup.setSelectedFlags(new boolean[] { store.isShowTimestamps(),
                store.isDetectUrls(), store.isLogToRms(), store.isRequireTls() });
        append(viewGroup);

        gwHost = new TextField("Gateway host", store.getGatewayHost(), 64, TextField.ANY);
        gwPort = new TextField("Gateway port", String.valueOf(store.getGatewayPort()),
                5, TextField.NUMERIC);
        gwPass = new TextField("Gateway pass", store.getGatewayPassword(), 48, TextField.PASSWORD);
        floodDelay = new TextField("Flood ms/line", "700", 4, TextField.NUMERIC);
        append(new StringItem("Relay (optional TLS terminator)", ""));
        append(gwHost);
        append(gwPort);
        append(gwPass);
        append(floodDelay);

        // Export/Import info
        append(new StringItem("Backup", "Export profiles to file"));
        append(new StringItem("Restore", "Import profiles from file"));
    }

    // ------------------------------------------------------------------
    // Profile form
    // ------------------------------------------------------------------

    private void buildProfile() {
        fName = new TextField("Name", "", 24, TextField.ANY);
        fHost = new TextField("Server", "", 64, TextField.ANY);
        fPort = new TextField("Port", "6697", 5, TextField.NUMERIC);
        fSecure = new javax.microedition.lcdui.ChoiceGroup("Use TLS",
                javax.microedition.lcdui.Choice.EXCLUSIVE,
                new String[] { "ssl:// first", "plain only" }, null);
        append(new StringItem("Identity", ""));
        append(fName);
        append(fHost);
        append(fPort);
        append(fSecure);

        fNick = new TextField("Nick", "", 30, TextField.ANY);
        fAltNick = new TextField("Alt nick", "", 30, TextField.ANY);
        fPass = new TextField("Password", "", 40, TextField.PASSWORD);
        fReal = new TextField("Real name", "", 40, TextField.ANY);
        append(new StringItem("User", ""));
        append(fNick);
        append(fAltNick);
        append(fPass);
        append(fReal);

        fChannels = new TextField("Channels", "", 200, TextField.ANY);
        append(new StringItem("Join on connect", "comma separated, optional key after a space"));
        append(fChannels);

        fE2e = new javax.microedition.lcdui.ChoiceGroup("E2E mode",
                javax.microedition.lcdui.Choice.EXCLUSIVE,
                new String[] { "off", "on (encrypt)" }, null);
        fE2eKey = new TextField("E2E key (shared secret)", "",
                128, TextField.PASSWORD);
        append(new StringItem("End-to-end encryption", ""));
        append(fE2e);
        append(fE2eKey);
        fillProfileForm();
    }

    private void fillProfileForm() {
        if (editing == null) {
            return;
        }
        fName.setString(editing.getName());
        fHost.setString(editing.getHost());
        fPort.setString(String.valueOf(editing.getPort()));
        fSecure.setSelectedIndex(editing.isSecure() ? 0 : 1, false);
        fNick.setString(editing.getNick());
        fAltNick.setString(editing.getAltNick());
        fPass.setString(editing.getPassword());
        fReal.setString(editing.getRealName());
        fChannels.setString(editing.getChannels());
        fE2e.setSelectedIndex(editing.isE2eOn() ? 1 : 0, false);
        fE2eKey.setString(editing.getE2eKey());
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    public void commandAction(Command c, Displayable d) {
        // Global form commands (when not editing a profile)
        if (editing == null) {
            if (c == exportCmd) {
                doExport();
                return;
            }
            if (c == importCmd) {
                doImport();
                return;
            }
        }
        if (c == cancelCmd || c.getCommandType() == Command.BACK) {
            if (callback != null) {
                callback.onCancelled();
            } else {
                midlet.showServerList();
            }
            return;
        }
        if (c == saveCmd) {
            if (editing != null) {
                saveProfile();
            } else {
                saveGlobals();
            }
            return;
        }
    }

    private void saveGlobals() {
        store.setTheme(themeGroup.getSelectedIndex());
        boolean[] alertsSel = new boolean[alertGroup.size()];
        alertGroup.getSelectedFlags(alertsSel);
        store.setAlerts(alertsSel[0], alertsSel[1]);
        boolean[] viewSel = new boolean[viewGroup.size()];
        viewGroup.getSelectedFlags(viewSel);
        store.setShowTimestamps(viewSel[0]);
        store.setDetectUrls(viewSel[1]);
        store.setLogToRms(viewSel[2]);
        store.setRequireTls(viewSel[3]);
        store.setGateway(gwHost.getString(), Str.parseInt(gwPort.getString(), 0),
                gwPass.getString());
        long delay = Str.parseInt(floodDelay.getString(), 700);
        int burst = (int) (delay > 0 ? 2800 / delay : 4);
        if (burst < 1) {
            burst = 1;
        }
        if (burst > 10) {
            burst = 10;
        }
        store.setFlood(delay, burst);

        if (!store.savePrefs()) {
            midlet.notifyUser("Settings", "Could not save. Device storage may be full.");
        } else {
            midlet.getAlert().setVibrateEnabled(store.isAlertVibrate());
            midlet.getAlert().setBeepEnabled(store.isAlertBeep());
            midlet.getManager().setLogEnabled(store.isLogToRms());
            midlet.notifyUser("Settings", "Saved.");
        }
        midlet.refreshServerList();
        midlet.showServerList();
    }

    private void saveProfile() {
        editing.setName(fName.getString());
        editing.setHost(fHost.getString());
        editing.setPort(Str.parseInt(fPort.getString(), 6667));
        editing.setSecure(fSecure.getSelectedIndex() == 0);
        editing.setNick(fNick.getString());
        editing.setAltNick(fAltNick.getString());
        editing.setPassword(fPass.getString());
        editing.setRealName(fReal.getString());
        editing.setChannels(fChannels.getString());
        editing.setE2eOn(fE2e.getSelectedIndex() == 1);
        editing.setE2eKey(fE2eKey.getString());

        String err = editing.validate();
        if (err.length() > 0) {
            midlet.notifyUser("Cannot save", err);
            return;
        }
        // Persist the list.
        ServerListScreen list = midlet.getServerList();
        if (list == null) {
            midlet.notifyUser("Cannot save", "Server list is not available.");
            return;
        }
        java.util.Vector profiles = list.getProfiles();
        if (isNew) {
            profiles.addElement(editing);
        } else {
            boolean replaced = false;
            for (int i = 0; i < profiles.size(); i++) {
                Profile p = (Profile) profiles.elementAt(i);
                if (p == editing) {
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                profiles.addElement(editing);
            }
        }
        list.saveProfiles();
        midlet.onProfilesChanged();
        if (callback != null) {
            callback.onSaved();
        } else {
            midlet.showServerList();
        }
    }

    private void doExport() {
        byte[] data = store.exportProfiles(midlet.getServerList().getProfiles());
        String s = com.chatterbox.proto.Utf8.decode(data);
        javax.microedition.lcdui.Alert a = new javax.microedition.lcdui.Alert("Export", s.substring(0, Math.min(s.length(), 2000)), null, javax.microedition.lcdui.Alert.ALERT_TIMEOUT);
        a.setTimeout(10000);
        midlet.getDisplay().setCurrent(a, this);
    }

    private void doImport() {
        final TextBox tb = new TextBox("Import (paste export text)", "", 8000, TextBox.ANY);
        tb.addCommand(new Command("Import", Command.OK, 1));
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getLabel().equals("Import")) {
                    byte[] data = com.chatterbox.proto.Utf8.encode(tb.getString());
                    int count = store.importProfiles(data, midlet.getServerList().getProfiles());
                    midlet.getServerList().saveProfiles();
                    midlet.onProfilesChanged();
                    midlet.notifyUser("Import", count + " profiles imported");
                }
            }
        });
        midlet.getDisplay().setCurrent(tb);
    }
}