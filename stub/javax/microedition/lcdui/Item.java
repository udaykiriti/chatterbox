package javax.microedition.lcdui;

public abstract class Item {
    public static final int PLAIN = 0;
    public static final int HYPERLINK = 1;
    public static final int BUTTON = 2;

    protected Item(String label) {}
    public String getLabel() { return ""; }
    public void setLabel(String s) {}
    public int getAppearance() { return PLAIN; }
    public void setDefaultCommand(Command cmd) {}
    public Command getDefaultCommand() { return null; }
}
