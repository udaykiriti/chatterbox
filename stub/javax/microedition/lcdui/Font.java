package javax.microedition.lcdui;

public final class Font {
    public static final int FACE_SYSTEM = 0;
    public static final int FACE_MONOSPACE = 32;
    public static final int FACE_PROPORTIONAL = 64;
    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_LARGE = 16;

    public static Font getDefaultFont() { return new Font(); }
    public static Font getFont(int face, int style, int size) { return new Font(); }
    public int getHeight() { return 12; }
    public int getBaselinePosition() { return 10; }
    public int charWidth(char ch) { return 6; }
    public int stringWidth(String str) { return str.length() * 6; }
    public int getFace() { return FACE_SYSTEM; }
    public int getStyle() { return STYLE_PLAIN; }
    public int getSize() { return SIZE_MEDIUM; }
}
