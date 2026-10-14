package javax.microedition.lcdui;

public class Form extends Displayable {
    public Form(String title) {}
    public Form(String title, String[] items) {}
    public int append(String str) { return 0; }
    public int append(Item item) { return 0; }
    public int append(String label, Item item) { return 0; }
    public int append(String str, Item[] items) { return 0; }
    public void delete(int index) {}
    public void deleteAll() {}
    public int size() { return 0; }
    public Item get(int index) { return null; }
    public String getStringItem(int index) { return ""; }
    public int getSelectedIndex() { return -1; }
    public void setSelectedIndex(int index) {}
    public void setTitle(String s) {}
    public void setItemLabel(int index, String label) {}
    public void addCommand(Command cmd) {}
}
