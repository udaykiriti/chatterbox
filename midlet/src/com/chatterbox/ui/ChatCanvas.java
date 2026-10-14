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
    private boolean pumpStarted;
    private long lastActivityFlash;

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
        dirty = true;
        repaint();
    }

    public IrcConnection getConnection() {
        return conn;
    }

    public ChannelState getWindow() {
        return window;
    }

    public void applyTheme(Theme t) {
        this.theme = t;
        view = new ChatView(t);
        view.setShowTimestamps(showTimestamps);
        view.setSelfNick(conn == null ? "" : conn.getServer().getSelfNick());
        dirty = true;
        repaint();
    }

    public void resume() {
        running = true;
        if (pumpStarted) {
            return;
        }
        pumpStarted = true;
        // ~25 fps repaint while interacting is plenty and saves battery.
        new Thread(this).start();
        dirty = true;
        repaint();
    }

    public void pause() {
        running = false;
    }

    public void onDataChanged() {
        dirty = true;
        if (pinnedToBottom) {
            topLine = view.maxTop();
        }
        repaint();
    }

    public void onStateChanged(ServerState s) {
        dirty = true;
        repaint();
    }

    public void onLagChanged() {
        dirty = true;
    }

    public void setHeaderTopic(String topic) {
        dirty = true;
        repaint();
    }

    // ------------------------------------------------------------------
    // Repaint timer
    // ------------------------------------------------------------------

    public void run() {
        while (running) {
            try {
                Thread.sleep(40);
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

        if (window == null || conn == null) {
            g.setColor(Theme.argb(theme.fg));
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM));
            g.drawString("No conversation", 4, 4, Graphics.TOP | Graphics.LEFT);
            return;
        }

        Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        Font body = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
        Font inputFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_LARGE);

        headerHeight = small.getHeight() + 4;
        inputHeight = inputFont.getHeight() + 6;
        if (inputHeight + headerHeight >= h) {
            // Extremely short screen (some Series 40 landscape): drop the header.
            headerHeight = 0;
        }

        view.setFont(body);
        int textAreaH = h - headerHeight - inputHeight;
        view.layout(g, w, textAreaH, window.getRing().snapshot());

        if (topLine > view.maxTop()) {
            topLine = view.maxTop();
        }
        view.paint(g, topLine, view.getVisibleRows());
        drawScrollbar(g, textAreaH);
        drawHeader(g, w, small);
        drawInput(g, w, h, inputFont);

        g.setColor(Theme.argb(theme.bg));
        g.fillRect(0, h - inputHeight, w, 1);
    }

    private void drawScrollbar(Graphics g, int areaH) {
        int total = view.getLineCount();
        int rows = view.getVisibleRows();
        if (total <= rows || rows < 1) {
            return;
        }
        int barW = 4;
        int x = getWidth() - barW - 1;
        int thumbH = areaH * rows / total;
        if (thumbH < 6) {
            thumbH = 6;
        }
        int range = view.maxTop();
        int thumbY = range == 0 ? 0 : (areaH - thumbH) * topLine / range;
        g.setColor(Theme.argb(theme.scrollbar));
        g.fillRect(x, thumbY, barW, thumbH);
    }

    private void drawHeader(Graphics g, int w, Font small) {
        if (headerHeight <= 0) {
            return;
        }
        StringBuffer sb = new StringBuffer(40);
        sb.append(window.getName());
        ChannelState active = conn.getServer().getActive();
        if (active == window && view.distanceFromBottom(topLine) > 0) {
            sb.append(" ^");
        }
        int lag = (int) conn.getServer().getLagMs();
        if (lag >= 0) {
            sb.append("  ");
            sb.append(lag);
            sb.append("ms");
        }
        if (!conn.isConnected()) {
            sb.append("  ");
            sb.append(conn.getServer().getStateName());
        }
        String topic = window.getTopic();
        if (showTopicHeader && topic.length() > 0) {
            sb.append("  ");
            sb.append(Str.clip(topic, w / small.charWidth('m') - 20));
        }

        g.setColor(Theme.argb(theme.bg));
        g.fillRect(0, 0, w, headerHeight);
        g.setFont(small);
        g.setColor(Theme.argb(window.getHighlight() > 0 ? theme.unread : theme.system));
        g.drawString(Str.clipWord(sb.toString(), w / Math.max(1, small.charWidth('m')) - 1),
                2, 1, Graphics.TOP | Graphics.LEFT);
        g.setColor(Theme.argb(theme.system));
        g.fillRect(0, headerHeight - 1, w, 1);
    }

    private void drawInput(Graphics g, int w, int h, Font font) {
        int y = h - inputHeight;
        g.setColor(Theme.argb(theme.inputBg));
        g.fillRect(0, y, w, inputHeight);

        String prompt = window.isPrivate() ? "> " : "# ";
        g.setFont(font);
        int promptW = font.stringWidth(prompt);
        g.setColor(Theme.argb(focusInput ? theme.link : theme.timestamp));
        g.drawString(prompt, 2, y + 3, Graphics.TOP | Graphics.LEFT);

        String text = input.getText();
        int avail = w - promptW - 8;
        // Scroll the input horizontally when it does not fit.
        int caret = input.getCaret();
        int caretX = font.stringWidth(text.substring(0, Math.min(caret, text.length())));
        int scroll = 0;
        if (caretX > avail - font.charWidth('m')) {
            scroll = caretX - avail + font.charWidth('m');
        }
        String shown = text;
        if (scroll > 0 && scroll < text.length()) {
            shown = text.substring(scroll);
        }
        g.setColor(Theme.argb(theme.inputFg));
        g.drawString(Str.clip(shown, Math.max(1, avail / Math.max(1, font.charWidth('m')))),
                promptW + 2, y + 3, Graphics.TOP | Graphics.LEFT);

        if (focusInput) {
            int cx = promptW + 2 - (scroll > 0 ? font.stringWidth(text.substring(0, Math.min(scroll,
                    text.length()))) : 0) + font.stringWidth(
                    shown.substring(0, Math.max(0, Math.min(caret - scroll, shown.length()))));
            g.setColor(Theme.argb(theme.cursor));
            // A blinking caret; blink state is driven by the repaint loop.
            if (((System.currentTimeMillis() / 500) % 2) == 0) {
                g.fillRect(cx, y + 3, 1, font.getHeight() - 2);
            }
        }

        String hint = input.completionHint();
        if (hint != null && hint.length() > 0) {
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
            g.setColor(Theme.argb(theme.timestamp));
            g.drawString(Str.clip(hint, w / 6), 2, y - Font.getDefaultFont().getHeight(),
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
            case KEY_NUM_POUND:   // == KEY_POUND (35) on every device
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
                dirty = true;
                repaint();
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
        // KEY_STAR and KEY_ENTER both send. On S60 3rd, KEY_STAR == KEY_NUM_STAR.
        return keyCode == KEY_STAR || keyCode == KEY_ENTER || keyCode == KEY_NUM_STAR;
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
        dirty = true;
        repaint();
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
        dirty = true;
        repaint();
    }

    private void scrollDown(int n) {
        topLine += n;
        if (topLine >= view.maxTop()) {
            topLine = view.maxTop();
            pinnedToBottom = true;
        }
        dirty = true;
        repaint();
    }

    private void scrollToBottom() {
        topLine = view.maxTop();
        pinnedToBottom = true;
        dirty = true;
        repaint();
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void toggleFocus() {
        focusInput = !focusInput;
        dirty = true;
        repaint();
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
        if (r.quit) {
            conn.stopGracefully("quit command");
            midlet.showServerList();
            return;
        }
        dirty = true;
        repaint();
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
            dirty = true;
            repaint();
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
        dirty = true;
        repaint();
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
        final javax.microedition.lcdui.Form f =
                new javax.microedition.lcdui.Form("Commands", new String[] {
                    "/join #channel", "/part", "/msg nick text", "/me waves",
                    "/nick newnick", "/topic new topic", "/query nick",
                    "/whois nick", "/names", "/away", "/back", "/clear", "/help"
                });
        f.addCommand(new Command("Use", Command.OK, 1));
        f.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getLabel().equals("Use")) {
                    int idx = f.getSelectedIndex();
                    if (idx >= 0 && idx < f.size()) {
                        StringBuffer sb = new StringBuffer(f.getStringItem(idx));
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
            dirty = true;
            repaint();
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
                    if (idx >= 0) {
                        topLine = Math.max(0, idx - view.getVisibleRows() / 2);
                        pinnedToBottom = false;
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
        if (idx >= 0) {
            topLine = Math.max(0, idx - view.getVisibleRows() / 2);
            pinnedToBottom = false;
            dirty = true;
            repaint();
        }
    }

    private void findPrev() {
        if (searchTerm == null || searchTerm.length() == 0) {
            openSearch();
            return;
        }
        int idx = view.findPrev(searchTerm, topLine - 1);
        if (idx >= 0) {
            topLine = Math.max(0, idx - view.getVisibleRows() / 2);
            pinnedToBottom = false;
            dirty = true;
            repaint();
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
