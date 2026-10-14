package javax.microedition.lcdui;

public class List extends Displayable {
    public static final int IMPLICIT = 0;
    public static final int COMBOBOX = 1;
    public static final int SINGLE = 2;
    public static final int MULTIPLE = 3;

    public List(String title, String[] listItems, int listType) {}
    public List(String title, int listType) {}
    public int append(String stringItem) { return 0; }
    public int append(String stringItem, Image image) { return 0; }
    public int insert(int index, String stringItem) { return 0; }
    public void delete(int index) {}
    public void deleteAll() {}
    public int getItemCount() { return 0; }
    public String getItemText(int index) { return ""; }
    public void setItemText(int index, String text) {}
    public int getSelectedIndex() { return -1; }
    public void setSelectedIndex(int index) {}
    public int getListType() { return IMPLICIT; }
    public boolean isSelected(int index) { return false; }
    public void setSelected(int index, boolean on) {}
    public void setVisible(boolean visible) {}
}
