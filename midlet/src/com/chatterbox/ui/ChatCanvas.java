package com.chatterbox.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.TextBox;

import com.chatterbox.core.ChannelState;
import java.util.Vector;
import com.chatterbox.core.CommandProcessor;
import com.chatterbox.core.ServerState;
import com.chatterbox.core.SessionManager;
import com.chatterbox.core.Str;
import com.chatterbox.core.Theme;
import com.chatterbox.net.IrcConnection;

/**
 * The chat view: wrapped scrollback on top, inline input line at the bottom.
 *
 * WHY A CUSTOM CANVAS AND NOT A TEXTBOX
 * A TextBox gives us free editing but no per-line colouring, no nick colours,
 * no highlight background and no cheap scrolling of a 200-line ring. So the
 * scrollback is a Canvas and only the input is a TextField-less inline editor
 * driven by keyPressed.
 *
 * The exception is long/complex input: the softkey "Write" opens a real TextBox
 * for composing a multi-line-ish paste or a message with characters the inline
 * editor cannot produce, then copies the result into the inline buffer.
 *
 * KEY MAP (all keys are remappable in Settings in a later revision; these are
 * the defaults chosen to match S60 chat-client convention):
 *
 *   KEY_NUM_UP / KEY_UP      scroll up one line
 *   KEY_NUM_DOWN / KEY_DOWN  scroll down one line, or snap to bottom
 *   KEY_STAR  / ENTER        send the input line
 *   KEY_NUM_POUND / '#'      cycle: input <-> scroll focus
 *   KEY_NUM1                nick list for this window
 *   KEY_NUM2                commands menu
 *   KEY_NUM3                cycle part/join
 *   KEY_NUM0                show topic in the header
 *   KEY_NUM7                toggle timestamps
 *   KEY_NUM9                page up
 *   KEY_NUM8                page down
 *   KEY_NUM_STAR  (Nokia)    send  [same as KEY_STAR on S60 3rd]
 *   KEY_POUND    (Nokia)     toggle focus
 *   KEY_NUM_LEFT / KEY_LEFT  caret left in the input line
 *   KEY_NUM_RIGHT            caret right
 *
 * DEL/CLR                       backspace
 * KEY_LEFT_SOFTKEY              command menu on Nokia when focused elsewhere
 *
 * Assumptions flagged for firmware:
 *  - KEY_STAR is KEY_NUM_STAR on S60 3rd edition and KEY_STAR on some Series
 *    40 builds. We accept both and de-duplicate.
 *  - The '*' and '#' chars can also arrive as CHARACTER typed events when the
 *    user is in text mode; we treat a CHARACTER '*' with a non-empty buffer as
 *    send only if the buffer's first char is '/', otherwise we insert it.
 *  - keyRepeated() is available on MIDP 2.0 Canvas but is NOT delivered
 *    consistently on S60 for KEY_NUM_* keys; we implement our own repeat timer
 *    for scroll keys instead.
 */
public final class ChatCanvas extends Canvas implements Runnable, CommandListener {

    // ---- Key-code aliases (app convention, NOT part of the MIDP API) ----
    // Real firmwares differ; MicroEmulator's j2se device uses the S60-style
    // negative codes below. Values must stay in one place so the on-device
    // checklist can re-baseline them per handset family.
    private static final int KEY_UP = -1;
    private static final int KEY_DOWN = -6;
    private static final int KEY_LEFT = -2;
    private static final int KEY_RIGHT = -5;
    private static final int KEY_DEL = 8;
    private static final int KEY_BACKSPACE = 8;
    private static final int KEY_CLEAR = 12;
    private static final int KEY_ENTER = 10;
    private static final int KEY_SOFT1 = -4;
    private static final int KEY_SOFT2 = -3;

    /** Height reserved for the input line, in pixels. */
    private int inputHeight;
    /** Height reserved for the header. */
    private int headerHeight;

    private final ChatterboxMIDlet midlet;
    private final SessionManager manager;
    private Theme theme;
    private ChatView view;

    private IrcConnection conn;
    private ChannelState window;

    private final InputLine input = new InputLine();
    private boolean focusInput = true;
    private boolean showTopicHeader;
    private boolean showTimestamps;

    private int topLine;
    private boolean pinnedToBottom = true;
    private boolean dirty = true;
    private boolean running;
    private Thread pump;
    private long lastActivityFlash;

    /**
     * Cached fonts and metrics. Font.getFont() plus the metric calls cost a
     * native round-trip each; the old paint() paid ~15 of them per frame.
     * Refreshed only when the canvas size changes (fonts never change
     * otherwise; colours are read live from 'theme').
     */
    private int metricsW = -1;
    private int metricsH = -1;
    private Font fontSmall;
    private Font fontBody;
    private Font fontInput;
    private int smallCharW = 6;
    private int inputCharW = 6;
    private int defaultFontH = 12;
    private String promptCacheStr = "";
    private int promptCacheW;

    /**
     * Cached header line. Rebuilt only when one of its inputs changes; the
     * lag figure is bucketed to whole seconds so a millisecond tick does not
     * invalidate it on every paint.
     */
    private String headerCache = "";
    private String hName = "";
    private String hTopic = "";
    private String hState = "";
    private int hLagBucket = -2;
    private int hBack = -1;
    private int hW;
    private boolean hConnected;
    private boolean hShowTopic;

    /** Custom repeat for scroll keys. */
    private int repeatKey = -1;
    private long repeatUntil;
    private long repeatNext;

    private NickListScreen nickList;

    private final Command menuCmd = new Command("Menu", Command.SCREEN, 1);
    private final Command writeCmd = new Command("Write", Command.SCREEN, 2);
    private final Command sendCmd = new Command("Send", Command.SCREEN, 3);
    private final Command nicksCmd = new Command("Nicks", Command.SCREEN, 4);
    private final Command serversCmd = new Command("Servers", Command.SCREEN, 5);
    private final Command topicCmd = new Command("Topic", Command.SCREEN, 6);
    private final Command clearCmd = new Command("Clear", Command.SCREEN, 7);
    private final Command timeCmd = new Command("Timestamps", Command.SCREEN, 8);
    private final Command copyCmd = new Command("Copy URL", Command.SCREEN, 9);
    private final Command closeCmd = new Command("Close window", Command.SCREEN, 10);
    private final Command searchCmd = new Command("Search", Command.SCREEN, 11);

    public ChatCanvas(ChatterboxMIDlet midlet, SessionManager manager, Theme theme) {
        super();
        this.midlet = midlet;
        this.manager = manager;
        this.theme = theme;
        this.view = new ChatView(theme);
        addCommand(menuCmd);
        addCommand(writeCmd);
        addCommand(nicksCmd);
        addCommand(serversCmd);
        addCommand(topicCmd);
        addCommand(clearCmd);
        addCommand(timeCmd);
        addCommand(copyCmd);
        addCommand(closeCmd);
        addCommand(searchCmd);
        addCommand(sendCmd);
        setCommandListener(this);
        setFullScreenMode(true);
        showTimestamps = midlet.getStore() != null && midlet.getStore().isShowTimestamps();
        view.setShowTimestamps(showTimestamps);
        view.setDetectUrls(midlet.getStore() != null && midlet.getStore().isDetectUrls());
        resume();
    }

    // ------------------------------------------------------------------
    // Attachment and lifecycle
    // ------------------------------------------------------------------

    /**
     * Request a repaint through the pump thread (lands within ~40 ms).
     * Direct repaint() calls were removed from every mutator: each one forced
     * a full synchronous paint, so every keystroke and scroll step painted
     * twice. The pump coalesces them into one.
     */
    private void markDirty() {
        dirty = true;
    }

    public void attach(IrcConnection c, ChannelState w) {
        conn = c;
        window = w;
        if (w != null && c != null) {
            c.getServer().setActive(w);
            view.setSelfNick(c.getServer().getSelfNick());
        }
        topLine = 0;
        pinnedToBottom = true;
        input.clear();
        focusInput = true;
        markDirty();
    }

    public IrcConnection getConnection() {
        return conn;
    }

    public ChannelState getWindow() {
        return window;
    }

    public void applyTheme(Theme t) {
        this.theme = t;
        // Keep the view (and its wrap cache): setTheme() invalidates colours
        // only, so scrollback is not rewrapped on a colour change.
        view.setTheme(t);
        metricsW = -1;
        markDirty();
    }

    public void resume() {
        running = true;
        // The pump thread exits when pause() stops it (or on any failure), so
        // restart it whenever it is gone; the old pumpStarted flag never
        // reset, which froze the screen after one minimise/restore cycle.
        if (pump == null || !pump.isAlive()) {
            pump = new Thread(this);
            pump.start();
        }
        markDirty();
    }

    public void pause() {
        running = false;
    }

    public void onDataChanged() {
        markDirty();
        if (pinnedToBottom) {
            topLine = view.maxTop();
        }
    }

    public void onStateChanged(ServerState s) {
        markDirty();
    }

    public void onLagChanged() {
        markDirty();
    }

    public void setHeaderTopic(String topic) {
        markDirty();
    }

    // ------------------------------------------------------------------
    // Repaint timer
    // ------------------------------------------------------------------

    public void run() {
        while (running) {
            try {
                // While the MIDlet is backgrounded nothing can be seen: poll
                // rarely instead of spinning at 25 fps.
                Thread.sleep(midlet.isPaused() ? 500 : 40);
                if (repeatKey >= 0) {
                    long now = System.currentTimeMillis();
                    if (now > repeatUntil) {
                        repeatKey = -1;
                    } else if (now >= repeatNext) {
                        repeatNext = now + 60;
                        doRepeat(repeatKey);
                    }
                }
                if (dirty && !midlet.isPaused()) {
                    dirty = false;
                    repaint();
                }
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                // The repaint loop must never die silently; a dead loop means
                // a frozen screen with no way to recover short of reinstall.
                break;
            }
        }
    }

    // ------------------------------------------------------------------
    // Painting
    // ------------------------------------------------------------------

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        g.setColor(Theme.argb(theme.bg));
        g.fillRect(0, 0, w, h);

        ensureMetrics(w, h);
        if (window == null || conn == null) {
            g.setColor(Theme.argb(theme.fg));
            g.setFont(fontBody);
            g.drawString("No conversation", 4, 4, Graphics.TOP | Graphics.LEFT);
            return;
        }

        // Set the body font explicitly: layout() measures through g.getFont(),
        // and without this it would inherit whatever drawInput left behind.
        g.setFont(fontBody);
        int textAreaH = h - headerHeight - inputHeight;
        view.layout(g, w, textAreaH, window.getRing());

        if (topLine > view.maxTop()) {
            topLine = view.maxTop();
        }
        view.paint(g, topLine, view.getVisibleRows());
        drawScrollbar(g, w, textAreaH);
        drawHeader(g, w);
        drawInput(g, w, h);

        g.setColor(Theme.argb(theme.bg));
        g.fillRect(0, h - inputHeight, w, 1);
    }

    /**
     * Refresh cached fonts and derived metrics. Runs only when the canvas
     * size changed since the last paint (or after applyTheme clears it).
     */
    private void ensureMetrics(int w, int h) {
        if (w == metricsW && h == metricsH && fontSmall != null) {
            return;
        }
        metricsW = w;
        metricsH = h;
        fontSmall = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        fontBody = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
        fontInput = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_LARGE);
        smallCharW = Math.max(1, fontSmall.charWidth('m'));
        inputCharW = Math.max(1, fontInput.charWidth('m'));
        defaultFontH = Font.getDefaultFont().getHeight();

        headerHeight = fontSmall.getHeight() + 4;
        inputHeight = fontInput.getHeight() + 6;
        if (inputHeight + headerHeight >= h) {
            // Extremely short screen (some Series 40 landscape): drop the header.
            headerHeight = 0;
        }
        promptCacheStr = "";
    }

    private void drawScrollbar(Graphics g, int w, int areaH) {
        int total = view.getLineCount();
        int rows = view.getVisibleRows();
        if (total <= rows || rows < 1) {
            return;
        }
        int barW = 4;
        int x = w - barW - 1;
        int thumbH = areaH * rows / total;
        if (thumbH < 6) {
            thumbH = 6;
        }
        int range = view.maxTop();
        int thumbY = range == 0 ? 0 : (areaH - thumbH) * topLine / range;
        g.setColor(Theme.argb(theme.scrollbar));
        g.fillRect(x, thumbY, barW, thumbH);
    }

    private void drawHeader(Graphics g, int w) {
        if (headerHeight <= 0) {
            return;
        }
        String name = window.getName();
        ChannelState active = conn.getServer().getActive();
        int back = (active == window && view.distanceFromBottom(topLine) > 0) ? 1 : 0;
        int lag = (int) conn.getServer().getLagMs();
        int lagBucket = lag < 0 ? -1 : lag / 1000;
        boolean connected = conn.isConnected();
        String state = connected ? "" : conn.getServer().getStateName();
        String topic = showTopicHeader ? window.getTopic() : "";
        int wBucket = w / smallCharW;
        if (!name.equals(hName) || !topic.equals(hTopic) || !state.equals(hState)
                || lagBucket != hLagBucket || back != hBack || wBucket != hW
                || connected != hConnected || showTopicHeader != hShowTopic) {
            StringBuffer sb = new StringBuffer(40);
            sb.append(name);
            if (back == 1) {
                sb.append(" ^");
            }
            if (lag >= 0) {
                sb.append("  ");
                sb.append(lag);
                sb.append("ms");
            }
            if (!connected) {
                sb.append("  ");
                sb.append(state);
            }
            if (showTopicHeader && topic.length() > 0) {
                sb.append("  ");
                sb.append(Str.clip(topic, wBucket - 20));
            }
            headerCache = Str.clipWord(sb.toString(), wBucket - 1);
            hName = name;
            hTopic = topic;
            hState = state;
            hLagBucket = lagBucket;
            hBack = back;
            hW = wBucket;
            hConnected = connected;
            hShowTopic = showTopicHeader;
        }

        g.setColor(Theme.argb(theme.bg));
        g.fillRect(0, 0, w, headerHeight);
        g.setFont(fontSmall);
        g.setColor(Theme.argb(window.getHighlight() > 0 ? theme.unread : theme.system));
        g.drawString(headerCache, 2, 1, Graphics.TOP | Graphics.LEFT);
        g.setColor(Theme.argb(theme.system));
        g.fillRect(0, headerHeight - 1, w, 1);
    }

    private void drawInput(Graphics g, int w, int h) {
        int y = h - inputHeight;
        g.setColor(Theme.argb(theme.inputBg));
        g.fillRect(0, y, w, inputHeight);

        String prompt = window.isPrivate() ? "> " : "# ";
        g.setFont(fontInput);
        int promptW;
        if (prompt.equals(promptCacheStr)) {
            promptW = promptCacheW;
        } else {
            promptW = fontInput.stringWidth(prompt);
            promptCacheStr = prompt;
            promptCacheW = promptW;
        }
        g.setColor(Theme.argb(focusInput ? theme.link : theme.timestamp));
        g.drawString(prompt, 2, y + 3, Graphics.TOP | Graphics.LEFT);

        String text = input.getText();
        int avail = w - promptW - 8;
        // Scroll the input horizontally when it does not fit.
        int caret = input.getCaret();
        int caretX = fontInput.stringWidth(text.substring(0, Math.min(caret, text.length())));
        int scroll = 0;
        if (caretX > avail - inputCharW) {
            scroll = caretX - avail + inputCharW;
        }
        String shown = text;
        if (scroll > 0 && scroll < text.length()) {
            shown = text.substring(scroll);
        }
        g.setColor(Theme.argb(theme.inputFg));
        g.drawString(Str.clip(shown, Math.max(1, avail / inputCharW)),
                promptW + 2, y + 3, Graphics.TOP | Graphics.LEFT);

        if (focusInput) {
            int cx = promptW + 2 - (scroll > 0 ? fontInput.stringWidth(text.substring(0, Math.min(scroll,
                    text.length()))) : 0) + fontInput.stringWidth(
                    shown.substring(0, Math.max(0, Math.min(caret - scroll, shown.length()))));
            g.setColor(Theme.argb(theme.cursor));
            // A blinking caret; blink state is driven by the repaint loop.
            if (((System.currentTimeMillis() / 500) % 2) == 0) {
                g.fillRect(cx, y + 3, 1, fontInput.getHeight() - 2);
            }
        }

        String hint = input.completionHint();
        if (hint != null && hint.length() > 0) {
            g.setFont(fontSmall);
            g.setColor(Theme.argb(theme.timestamp));
            g.drawString(Str.clip(hint, w / 6), 2, y - defaultFontH,
                    Graphics.TOP | Graphics.LEFT);
        }
    }

    // ------------------------------------------------------------------
    // Key handling
    // ------------------------------------------------------------------

    protected void keyPressed(int keyCode) {
        if (window == null || conn == null) {
            return;
        }

        if (isSendKey(keyCode)) {
            sendInput();
            return;
        }

        switch (keyCode) {
            case KEY_UP:
                scrollUp(1);
                startRepeat(KEY_UP);
                return;
            case KEY_DOWN:
                scrollDown(1);
                startRepeat(KEY_DOWN);
                return;
            case KEY_NUM9:
                scrollUp(view.getVisibleRows() - 1);
                return;
            case KEY_NUM8:
                scrollDown(view.getVisibleRows() - 1);
                return;
            case KEY_POUND:       // '#' cycles: input <-> scroll focus
                toggleFocus();
                return;
            case KEY_NUM1:
                showNickList();
                return;
            case KEY_NUM3:
                cycle();
                return;
            case KEY_NUM0:
                showTopicHeader = !showTopicHeader;
                markDirty();
                return;
            case KEY_NUM7:
                toggleTimestamps();
                return;
            case KEY_NUM2:
                openCommandMenu();
                return;
            case KEY_LEFT:
                input.moveLeft();
                dirty = true;
                return;
            case KEY_RIGHT:
                input.moveRight();
                dirty = true;
                return;
            default:
                break;
        }

        if (keyCode == KEY_DEL || keyCode == KEY_BACKSPACE || keyCode == KEY_CLEAR) {
            input.backspace();
            dirty = true;
            return;
        }

        // getGameAction maps a key to a game action int; anything above the
        // ASCII range is not a character, so fall through to the raw key code.
        char c = (char) keyCode;
        if (keyCode > 0 && keyCode < 0x10000 && !isModifierKey(keyCode)) {
            handleChar(c);
        }
    }

    protected void keyRepeated(int keyCode) {
        // S60 delivers this inconsistently; our own timer in run() covers the rest.
        if (keyCode == KEY_UP) {
            scrollUp(1);
        } else if (keyCode == KEY_DOWN) {
            scrollDown(1);
        }
    }

    private static boolean isModifierKey(int k) {
        return k == KEY_UP || k == KEY_DOWN || k == KEY_LEFT || k == KEY_RIGHT
                || k == KEY_DEL || k == KEY_CLEAR || k == KEY_STAR || k == KEY_POUND
                || k == KEY_SOFT1 || k == KEY_SOFT2;
    }

    private boolean isSendKey(int keyCode) {
        // KEY_STAR and KEY_ENTER both send. On S60 3rd, KEY_STAR == '*'.
        return keyCode == KEY_STAR || keyCode == KEY_ENTER;
    }

    private void handleChar(char c) {
        if (c == 0) {
            return;
        }
        if (c == '\n' || c == '\r') {
            sendInput();
            return;
        }
        if (c == '\t') {
            completeNick();
            return;
        }
        if (c == '#') {
            // '#' starts a channel reference in a sentence, so only treat it as
            // a mode switch when the buffer is empty.
            if (input.isEmpty()) {
                toggleFocus();
                return;
            }
        }
        if (c < 32) {
            return;
        }
        input.insert(c);
        markDirty();
    }

    private void startRepeat(int key) {
        repeatKey = key;
        long now = System.currentTimeMillis();
        repeatNext = now + 350;   // initial delay
        repeatUntil = now + 2000;  // then stop, so the user can re-press
    }

    private void doRepeat(int key) {
        if (key == KEY_UP) {
            scrollUp(1);
        } else if (key == KEY_DOWN) {
            scrollDown(1);
        }
    }

    // ------------------------------------------------------------------
    // Scrolling
    // ------------------------------------------------------------------

    private void scrollUp(int n) {
        topLine -= n;
        if (topLine < 0) {
            topLine = 0;
        }
        pinnedToBottom = (topLine >= view.maxTop());
        markDirty();
    }

    private void scrollDown(int n) {
        topLine += n;
        if (topLine >= view.maxTop()) {
            topLine = view.maxTop();
            pinnedToBottom = true;
        }
        markDirty();
    }

    private void scrollToBottom() {
        topLine = view.maxTop();
        pinnedToBottom = true;
        markDirty();
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void toggleFocus() {
        focusInput = !focusInput;
        markDirty();
    }

    private void toggleTimestamps() {
        showTimestamps = !showTimestamps;
        view.setShowTimestamps(showTimestamps);
        if (midlet.getStore() != null) {
            midlet.getStore().setShowTimestamps(showTimestamps);
            midlet.getStore().savePrefs();
        }
        onDataChanged();
    }

    private void sendInput() {
        String line = input.consume();
        if (Str.trim(line).length() == 0) {
            return;
        }
        scrollToBottom();
        CommandProcessor.Result r =
                new CommandProcessor(conn.getServer(), conn.getSession(), conn).execute(window, line);
        if (r.notice != null && r.notice.length() > 0) {
            conn.getSession().post(window, window.getName(),
                    com.chatterbox.core.Message.SERVER, r.notice);
        }
        if (r.focus != null && r.focus != window) {
            attach(conn, r.focus);
        }
        if (r.themeKind >= 0) {
            midlet.setTheme(r.themeKind);
        }
        if (r.quit) {
            conn.stopGracefully("quit command");
            midlet.showServerList();
            return;
        }
        markDirty();
    }

    private void completeNick() {
        if (window == null) {
            return;
        }
        if (!NickComplete.shouldComplete(input)) {
            return;
        }
        String word = input.currentWord();
        String[] decorated;
        if (word.startsWith("/")) {
            // Command completion
            decorated = NickComplete.completeCommand(word.substring(1));
        } else if (word.startsWith("#") || word.startsWith("&") || word.startsWith("+") || word.startsWith("!")) {
            // Channel completion
            String[] channels = getKnownChannels();
            decorated = NickComplete.completeChannel(word, channels);
        } else {
            // Nick completion (default)
            if (!window.isChannel() && window.userCount() == 0) {
                return;
            }
            String[] raw = NickComplete.matches(window, word);
            decorated = NickComplete.decorate(raw, word);
        }
        if (decorated != null && decorated.length > 0 && input.complete(decorated)) {
            markDirty();
        }
    }

    private String[] getKnownChannels() {
        if (conn == null) {
            return new String[0];
        }
        Vector wins = conn.getServer().channelWindows();
        String[] out = new String[wins.size()];
        for (int i = 0; i < wins.size(); i++) {
            out[i] = ((ChannelState) wins.elementAt(i)).getName();
        }
        return out;
    }

    private void cycle() {
        if (window == null || !window.isChannel()) {
            return;
        }
        CommandProcessor.Result r =
                new CommandProcessor(conn.getServer(), conn.getSession(), conn)
                        .execute(window, "/cycle");
        if (r.notice != null && r.notice.length() > 0) {
            conn.getSession().post(window, window.getName(),
                    com.chatterbox.core.Message.SERVER, r.notice);
        }
    }

    /** Return from a sub-screen (nick list, TextBox) to this canvas. */
    void midletShow() {
        midlet.show(this);
        markDirty();
    }

    /** Insert text at the caret, used by the nick list. */
    void insertText(String s) {
        input.insert(s);
        midletShow();
    }

    /** Run a slash command as if the user had typed it, then return here. */
    void execCommand(String line) {
        CommandProcessor.Result r =
                new CommandProcessor(conn.getServer(), conn.getSession(), conn)
                        .execute(window, line);
        if (r.notice != null && r.notice.length() > 0) {
            conn.getSession().post(window, window.getName(),
                    com.chatterbox.core.Message.SERVER, r.notice);
        }
        if (r.focus != null && r.focus != window) {
            attach(conn, r.focus);
            midlet.show(this);
            return;
        }
        midletShow();
    }

    private void showNickList() {
        if (window == null) {
            return;
        }
        nickList = new NickListScreen(this, window);
        midlet.getDisplay().setCurrent(nickList);
    }

    private void openCommandMenu() {
        final javax.microedition.lcdui.List f =
                new javax.microedition.lcdui.List("Commands",
                        javax.microedition.lcdui.Choice.EXCLUSIVE);
        String[] cmds = new String[] {
            "/join #channel", "/part", "/msg nick text", "/me waves",
            "/nick newnick", "/topic new topic", "/query nick",
            "/whois nick", "/names", "/away", "/back", "/clear", "/help"
        };
        for (int i = 0; i < cmds.length; i++) {
            f.append(cmds[i], null);
        }
        f.addCommand(new Command("Use", Command.OK, 1));
        f.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getLabel().equals("Use") || c.getLabel().equals("Select")) {
                    int idx = f.getSelectedIndex();
                    if (idx >= 0 && idx < f.size()) {
                        StringBuffer sb = new StringBuffer(f.getString(idx));
                        sb.append(' ');
                        input.setText(sb.toString());
                        input.setCaret(input.length());
                        dirty = true;
                    }
                    midlet.show(ChatCanvas.this);
                }
            }
        });
        midlet.getDisplay().setCurrent(f);
    }

    /** Build a TextBox seeded with the current input, for the "Write" command. */
    private void openWriteBox() {
        final TextBox tb = new TextBox("Message", input.getText(), 480, TextBox.ANY);
        tb.addCommand(new Command("Insert", Command.OK, 1));
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getLabel().equals("Insert")) {
                    input.setText(tb.getString());
                    midlet.show(ChatCanvas.this);
                }
            }
        });
        midlet.getDisplay().setCurrent(tb);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == menuCmd) {
            // On a Canvas, a SCREEN command shows up under the platform menu.
            return;
        }
        if (c == writeCmd) {
            openWriteBox();
            return;
        }
        if (c == sendCmd) {
            sendInput();
            return;
        }
        if (c == nicksCmd) {
            showNickList();
            return;
        }
        if (c == serversCmd) {
            midlet.showServerList();
            return;
        }
        if (c == topicCmd) {
            showTopicHeader = !showTopicHeader;
            markDirty();
            return;
        }
        if (c == clearCmd) {
            window.getRing().clear();
            scrollToBottom();
            return;
        }
        if (c == timeCmd) {
            toggleTimestamps();
            return;
        }
        if (c == copyCmd) {
            copyVisibleUrl();
            return;
        }
        if (c == searchCmd) {
            openSearch();
            return;
        }
        if (c == closeCmd) {
            if (window.isPrivate()) {
                conn.getServer().closeWindow(window.getName());
            } else {
                new CommandProcessor(conn.getServer(), conn.getSession(), conn)
                        .execute(window, "/part");
            }
            midlet.showServerList();
            return;
        }
    }

    private String searchTerm = "";

    private void openSearch() {
        final TextBox tb = new TextBox("Search", searchTerm, 80, TextBox.ANY);
        tb.addCommand(new Command("Find", Command.OK, 1));
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getLabel().equals("Find")) {
                    searchTerm = tb.getString();
                    view.setSearch(searchTerm);
                    int idx = view.findNext(searchTerm, topLine + 1);
                    if (idx < 0) {
                        // Wrap to the top before giving up.
                        idx = view.findNext(searchTerm, 0);
                    }
                    if (idx >= 0) {
                        topLine = Math.max(0, idx - view.getVisibleRows() / 2);
                        pinnedToBottom = false;
                    } else {
                        midlet.notifyUser("Search", "No match for \"" + searchTerm + "\"");
                    }
                    midletShow();
                }
            }
        });
        midlet.getDisplay().setCurrent(tb);
    }

    private void findNext() {
        if (searchTerm == null || searchTerm.length() == 0) {
            openSearch();
            return;
        }
        int idx = view.findNext(searchTerm, topLine + view.getVisibleRows());
        if (idx < 0) {
            idx = view.findNext(searchTerm, 0);
        }
        if (idx >= 0) {
            topLine = Math.max(0, idx - view.getVisibleRows() / 2);
            pinnedToBottom = false;
            markDirty();
        } else {
            midlet.notifyUser("Search", "No match for \"" + searchTerm + "\"");
        }
    }

    private void findPrev() {
        if (searchTerm == null || searchTerm.length() == 0) {
            openSearch();
            return;
        }
        int idx = view.findPrev(searchTerm, topLine - 1);
        if (idx < 0) {
            idx = view.findPrev(searchTerm, view.getLineCount() - 1);
        }
        if (idx >= 0) {
            topLine = Math.max(0, idx - view.getVisibleRows() / 2);
            pinnedToBottom = false;
            markDirty();
        } else {
            midlet.notifyUser("Search", "No match for \"" + searchTerm + "\"");
        }
    }

    private void copyVisibleUrl() {
        String url = null;
        for (int i = view.getLineCount() - 1; i >= 0 && url == null; i--) {
            ChatView.RenderLine rl = view.line(i);
            if (rl != null && rl.link) {
                url = rl.url;
            }
        }
        if (url == null) {
            conn.getSession().post(window, window.getName(),
                    com.chatterbox.core.Message.SERVER, "No URL on screen.");
            return;
        }
        // No Clipboard on most Series 40 builds, so we show it for manual copy.
        conn.getSession().post(window, window.getName(),
                com.chatterbox.core.Message.SERVER, "URL: " + url);
        try {
            javax.microedition.lcdui.Alert a =
                    new javax.microedition.lcdui.Alert("URL", url, null,
                            javax.microedition.lcdui.Alert.ALERT_TIMEOUT);
            a.setTimeout(6000);
            midlet.getDisplay().setCurrent(a, this);
        } catch (Throwable t) {
            // Already posted it in the window; good enough.
        }
    }
}
