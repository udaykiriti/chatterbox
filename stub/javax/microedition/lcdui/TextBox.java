package javax.microedition.lcdui;

public class TextBox extends Displayable {
    public static final int ANY = 0;
    public static final int NUMERIC = 2;
    public static final int PHONENUMBER = 3;
    public static final int URL = 4;
    public static final int PASSWORD = 0x10000;

    public TextBox(String title, String text, int maxSize, int constraints) {}
    public String getString() { return ""; }
    public void setString(String text) {}
    public int getMaxSize() { return 0; }
    public int getCaretPosition() { return 0; }
    public void insertString(String s) {}
    public void setConstraints(int c) {}
    public int getConstraints() { return 0; }
}
