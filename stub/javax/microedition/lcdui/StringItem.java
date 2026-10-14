package javax.microedition.lcdui;

public class StringItem extends Item {
    public StringItem(String label, String text) { super(label); }
    public String getText() { return ""; }
    public void setText(String s) {}
    public int getAppearance() { return PLAIN; }
    public void setFont(Font f) {}
    public Font getFont() { return null; }
    public void setLayout(int layout) {}
    public int getLayout() { return 0; }
}
