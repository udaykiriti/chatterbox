package javax.microedition.lcdui;

public class ChoiceGroup extends Item {
    public static final int EXCLUSIVE = 1;
    public static final int MULTI = 2;
    public static final int MULTIPLE = 2;
    public static final int POPUP = 3;
    public static final int RADIOBUTTONS = 1;
    public static final int CHECKBOXES = 2;

    public ChoiceGroup(String label, String[] choices, int noneIndex, int type) { super(label); }
    public int getSelectedIndices() { return 0; }
    public ChoiceGroup(String label, String[] choices, int[] imageIndices, int type) { super(label); }
    public int getSelectedIndex() { return 0; }
    public void setSelectedIndex(int index, boolean selected) {}
    public int getSelectedIndices(int[] array) { return 0; }
    public void setSelectedIndices(int[] array) {}
    public int append(String stringElement) { return 0; }
    public void delete(int index) {}
    public int size() { return 0; }
    public String getString(int index) { return ""; }
    public void setSelected(int element, boolean on) {}
    public boolean isSelected(int element) { return false; }
}
