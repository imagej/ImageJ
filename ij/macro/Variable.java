package ij.macro;

class Variable implements MacroConstants {
    int symTabIndex;
    private int flags;
    private double value;
    private String str;

    Variable(int symTabIndex, double value, String str) {
        this.symTabIndex = symTabIndex;
        this.value = value;
        this.str = str;
    }

    double getValue() {
        return value;
    }

    void setValue(double value) {
        this.value = value;
        str = null;
    }

    String getString() {
        return str;
    }

    void setString(String str) {
        this.str = str;
        value = 0.0;
    }

    public String toString() {
        return value+" "+str+" "+symTabIndex;
    }

} // class Variable
