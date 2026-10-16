package javax.microedition.lcdui;

/** JSR 118 javax.microedition.lcdui.TextBox (compile stub). */
public class TextBox extends Displayable {
    public static final int ANY = 0;
    public static final int EMAILADDR = 1;
    public static final int NUMERIC = 2;
    public static final int PHONENUMBER = 3;
    public static final int URL = 4;
    public static final int DECIMAL = 5;
    public static final int PASSWORD = 0x10000;

    public TextBox(String title, String text, int maxSize, int constraints) {}

    public String getString() { return ""; }
    public void setString(String text) {}
    public void insert(String src, int position) {}
    public void delete(int offset, int length) {}
    public int setMaxSize(int size) { return 0; }
    public int size() { return 0; }
    public void setConstraints(int constraints) {}
    public int getConstraints() { return 0; }
}
