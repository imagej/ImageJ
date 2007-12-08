package ij;
import java.awt.image.*;

	/* This is an indexed color model that allows an
		lower and upper bound to be specified. */
    public class ExtendedColorModel extends IndexColorModel implements Cloneable {
	public double min, max;
	
    public ExtendedColorModel(byte r[], byte g[], byte b[]) {
    	this(8, 256, r, g, b);
	}
	
    public ExtendedColorModel(int bits, int size, byte r[], byte g[], byte b[]) {
    	super(bits, size, r, g, b);
	}

	public synchronized Object clone() {
		try {return super.clone();}
		catch (CloneNotSupportedException e) {return null;}
	}

}
