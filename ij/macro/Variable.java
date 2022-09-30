package ij.macro;

public class Variable implements MacroConstants, Cloneable {
	static final int VALUE=0, ARRAY=1, STRING=2;
    int symTabIndex;
    private double value;
    private String str;
    private Variable[] array;
    private int arraySize;

    public Variable() {
    }

    public Variable(double value) {
        this.value = value;
    }

    public Variable(String str) {
        this.str = str;
    }
    
    public Variable(Variable[] array) {
    	this.array = array;
    }

    Variable(int symTabIndex, double value, String str) {
        this.symTabIndex = symTabIndex;
        this.value = value;
        this.str = str;
    }

    Variable(int symTabIndex, double value, String str, Variable[] array) {
        this.symTabIndex = symTabIndex;
        this.value = value;
        this.str = str;
        this.array = array;
    }

    Variable(byte[] array) {
    	this.array = new Variable[array.length];
    	for (int i=0; i<array.length; i++)
    		this.array[i] = new Variable(array[i]&255);
    }

    Variable(int[] array) {
    	this.array = new Variable[array.length];
    	for (int i=0; i<array.length; i++)
    		this.array[i] = new Variable(array[i]);
    }

    Variable(double[] array) {
    	this.array = new Variable[array.length];
    	for (int i=0; i<array.length; i++)
    		this.array[i] = new Variable(array[i]);
    }

    public double getValue() {
    	if (str!=null)
    			return convertToDouble();  // string to number conversions
    	else
        	return value;
    }

	double convertToDouble() {
		try {
			Double d = Double.valueOf(str);
			return d.doubleValue();
		} catch (NumberFormatException e){
			return Double.NaN;
		}
	}

    void setValue(double value) {
        this.value = value;
        str = null;
        array = null;
    }

    public String getString() {
        return str;
    }

    void setString(String str) {
        this.str = str;
        value = 0.0;
        array = null;
    }

    Variable[] getArray() {
        return array;
    }

    void setArray(Variable[] array) {
        this.array = array;
        value = 0.0;
        str = null;
        arraySize = 0;
    }
    
    void setArraySize(int size) {
    	if (array==null)
    		size = 0;
    	else if (size>array.length)
    		size = array.length;
    	arraySize = size;
    }
    
    int getArraySize() {
    	int size = array!=null?array.length:0;
    	if (arraySize>0) size = arraySize;
    	return size;
    }

    int getType() {
    	if (array!=null)
    		return ARRAY;
    	else if (str!=null)
    		return STRING;
    	else
    		return VALUE;
    }

	public String toString() {
		String s = "";
		if (array!=null)
			s += "array["+array.length+"]";
		else if (str!=null) {
			s = str;
			if (s.length()>80)
				s = s.substring(0, 80)+"...";
			s = s.replaceAll("\n", " | ");
			s = "\""+s+"\"";
		} else {
			if (value==(int)value)
				s += (int)value;
			else
				s += ij.IJ.d2s(value,4);
		}
		return s;
	}
    
	public synchronized Object clone() {
		try {return super.clone();}
		catch (CloneNotSupportedException e) {return null;}
	}

} // class Variable
