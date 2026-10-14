package javax.microedition.lcdui;

public class TextField extends Item {
    public static final int ANY = 0;
    public static final int NUMERIC = 2;
    public static final int PHONENUMBER = 3;
    public static final int URL = 4;
    public static final int DECIMAL = 5;
    public static final int PASSWORD = 0x10000;
    public static final int UNEDITABLE = 0x20000;

    public TextField(String label, String text, int maxSize, int constraints) { super(label); }
    public String getString() { return ""; }
    public void setString(String text) {}
    public int getMaxSize() { return 0; }
    public void setMaxSize(int size) {}
    public int getConstraints() { return 0; }
    public void setConstraints(int c) {}
    public int getCaretPosition() { return 0; }
    public void setCaretPosition(int pos) {}
    public void insertString(String s) {}
    public void delete(int offset, int len) {}
}
