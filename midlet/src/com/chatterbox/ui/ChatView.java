package com.chatterbox.ui;

import java.util.Vector;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

import com.chatterbox.core.Message;
import com.chatterbox.core.Str;
import com.chatterbox.core.Theme;

/**
 * Turns a window's {@link com.chatterbox.core.MessageRing} into wrapped,
 * coloured display lines and paints them.
 *
 * It owns NO state beyond the line cache, so ChatCanvas can recreate it on
 * every resize without losing scroll position: the cache is rebuilt from the
 * ring and the scroll offset is clamped.
 *
 * Assumptions:
 *  - Character-cell measurement, not pixel measurement. The proportional system
 *    font on S60 makes true per-character pixel layout expensive, and on a
 *    240px-wide screen an average of ~5 px/char at 12pt is close enough for
 *    word wrap. We compute a per-character advance by measuring a run of 'n'
 *    once and dividing, which is what most J2ME chat clients do.
 *  - The ring holds at most 200 messages; after wrapping that is ~600-1200
 *    lines. We cache lazily by building only the visible window plus a margin,
 *    because building all of them on every keystroke is the classic cause of
 *    "chat gets laggy after 50 lines" on these devices.
 */
public final class ChatView {

    /** One physical display line. */
    public static final class RenderLine {
        public String text;
        public int color;
        public int bg;
        public boolean highlight;
        public boolean link;
        /** Set when this line begins a URL; the rest is the URL tail. */
        public String url;

        RenderLine(String t, int c, int bg, boolean hl) {
            this.text = t;
            this.color = c;
            this.bg = bg;
            this.highlight = hl;
        }
    }

    private Theme theme;
    private Font font = Font.getDefaultFont();
    private int lineHeight = 16;
    private int charWidth = 6;
    private boolean showTimestamps;
    private boolean colorNicks = true;
    private boolean detectUrls = true;
    private String selfNick = "";
    private String searchTerm = "";

    private int width = 240;
    private int visibleRows = 14;

    /** Cached wrapped output, rebuilt whenever the ring sequence changes. */
    private Vector lines = new Vector();
    private long cachedSequence = -1;
    private int cachedWidth = -1;
    private String cachedSelf = "";

    public ChatView(Theme theme) {
        this.theme = theme;
    }

    public void setTheme(Theme t) {
        this.theme = t;
    }

    public Theme getTheme() {
        return theme;
    }

    public void setShowTimestamps(boolean b) {
        showTimestamps = b;
    }

    public boolean getShowTimestamps() {
        return showTimestamps;
    }

    public void setColorNicks(boolean b) {
        colorNicks = b;
    }

    public void setDetectUrls(boolean b) {
        detectUrls = b;
    }

    public void setSelfNick(String n) {
        selfNick = n == null ? "" : n;
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font f) {
        this.font = f;
        lineHeight = f.getHeight() + 2;
    }

    public int getLineHeight() {
        return lineHeight;
    }

    public int getCharWidth() {
        return charWidth;
    }

    public int getVisibleRows() {
        return visibleRows;
    }

    public int getLineCount() {
        return lines.size();
    }

    public RenderLine line(int i) {
        if (i < 0 || i >= lines.size()) {
            return null;
        }
        return (RenderLine) lines.elementAt(i);
    }

    /**
     * Recompute layout for the given canvas size and font. Returns true when the
     * wrapped output had to be rebuilt.
     */
    public boolean layout(Graphics g, int w, int h, Message[] messages) {
        this.width = w;
        font = g.getFont();
        lineHeight = font.getHeight() + 2;
        int newCharWidth = measureChar(g, font);
        int newRows = h / lineHeight;
        if (newRows < 1) {
            newRows = 1;
        }
        boolean metricsChanged = (newCharWidth != charWidth || w != cachedWidth
                || !selfNick.equals(cachedSelf));
        this.charWidth = newCharWidth;
        this.visibleRows = newRows;
        if (metricsChanged) {
            cachedSequence = -1;
        }
        return rebuild(messages);
    }

    /**
     * Measure the average advance of one character. We measure a 20-char run of
     * 'x' at the font's height and divide; this avoids allocating per char.
     */
    private int measureChar(Graphics g, Font f) {
        String probe = "xxxxxxxxxxxxxxxxxxxx";
        int w = f.stringWidth(probe);
        int cw = w / 20;
        if (cw < 1) {
            cw = 1;
        }
        return cw;
    }

    public int charsPerLine() {
        int n = width / charWidth;
        return n < 8 ? 8 : n;
    }

    private boolean rebuild(Message[] messages) {
        if (messages == null || messages.length == 0) {
            lines.removeAllElements();
            cachedSequence = 0;
            return true;
        }
        // Sequence token: MessageRing gives us a monotonically increasing count.
        long last = messages[messages.length - 1].time;
        long seq = last * 31 + messages.length;
        if (seq == cachedSequence) {
            return false;
        }
        cachedSequence = seq;
        cachedWidth = width;
        cachedSelf = selfNick;

        lines.removeAllElements();
        int cols = charsPerLine();
        for (int i = 0; i < messages.length; i++) {
            append(messages[i], cols);
        }
        return true;
    }

    private void append(Message m, int cols) {
        int fg = theme.fg;
        int bg = theme.bg;
        boolean hl = m.highlight;

        StringBuffer prefix = new StringBuffer(24);
        if (showTimestamps) {
            prefix.append('[');
            prefix.append(m.timeStamp());
            prefix.append("] ");
        }
        String text = prefix.toString();
        int color = fg;

        switch (m.kind) {
            case Message.CHAT:
            case Message.ACTION:
            case Message.NOTICE:
                if (m.nick.length() > 0) {
                    color = m.self ? theme.nickSelf
                            : (colorNicks ? theme.colorForNick(m.nick, theme) : theme.nick);
                    text += "<" + m.nick + ">";
                    if (m.kind == Message.ACTION) {
                        text += " " + italic(m.text);
                    } else {
                        text += " " + m.text;
                    }
                } else {
                    text += m.text;
                }
                break;
            case Message.SERVER:
                color = theme.system;
                text += m.text;
                break;
            case Message.JOIN:
            case Message.PART:
            case Message.QUIT:
                color = theme.nickAlt;
                text += "* " + m.text;
                break;
            case Message.NICK:
                color = theme.nickAlt;
                text += "* " + m.nick + " " + m.text;
                break;
            case Message.MODE:
            case Message.TOPIC:
            case Message.KICK:
                color = theme.nickAlt;
                text += "* " + m.text;
                break;
            default:
                color = theme.timestamp;
                text += m.text;
                break;
        }

        if (hl) {
            color = theme.highlightFg;
            bg = theme.highlightBg;
        }

        int avail = cols - text.length();
        Vector wrapped = Str.wrap(text, avail < 8 ? 8 : avail);
        for (int i = 0; i < wrapped.size(); i++) {
            String part = (String) wrapped.elementAt(i);
            String url = detectUrls ? Str.findUrl(part) : null;
            RenderLine rl = new RenderLine(part, color, bg, hl);
            if (url != null) {
                rl.link = true;
                rl.url = url;
            }
            lines.addElement(rl);
        }
    }

    private String italic(String s) {
        return s;
    }

    /**
     * Paint rows [top, top+rows).
     */
    public void paint(Graphics g, int top, int rows) {
        g.setFont(font);
        int y = 0;
        int max = Math.min(lines.size(), top + rows);
        for (int i = top; i < max; i++) {
            RenderLine rl = (RenderLine) lines.elementAt(i);
            if (rl.bg != theme.bg) {
                g.setColor(Theme.argb(rl.bg));
                g.fillRect(0, y, width, lineHeight);
            }
            int c = rl.link ? theme.link : rl.color;
            g.setColor(Theme.argb(c));
            g.drawString(rl.text, 1, y + font.getBaselinePosition(), Graphics.TOP | Graphics.LEFT);
            y += lineHeight;
        }
        g.setColor(Theme.argb(theme.bg));
        int h = rows * lineHeight;
        if (y < h) {
            g.fillRect(0, y, width, h - y);
        }
    }

    /**
     * The scrollbar: how many rows down from the bottom we are.
     * @return rows, 0 means pinned to the bottom.
     */
    public int distanceFromBottom(int top) {
        return lines.size() - (top + visibleRows);
    }

    public int maxTop() {
        int m = lines.size() - visibleRows;
        return m < 0 ? 0 : m;
    }

    /** Set search term; empty string clears search. */
    public void setSearch(String term) {
        searchTerm = term == null ? "" : term;
    }

    /** @return index of next match after 'from', or -1. */
    public int findNext(String term, int from) {
        if (term == null || term.length() == 0) {
            return -1;
        }
        String t = term.toLowerCase();
        for (int i = from; i < lines.size(); i++) {
            RenderLine rl = (RenderLine) lines.elementAt(i);
            if (rl.text.toLowerCase().indexOf(t) >= 0) {
                return i;
            }
        }
        return -1;
    }

    /** @return index of previous match before 'from', or -1. */
    public int findPrev(String term, int from) {
        if (term == null || term.length() == 0) {
            return -1;
        }
        String t = term.toLowerCase();
        for (int i = from; i >= 0; i--) {
            RenderLine rl = (RenderLine) lines.elementAt(i);
            if (rl.text.toLowerCase().indexOf(t) >= 0) {
                return i;
            }
        }
        return -1;
    }
}

