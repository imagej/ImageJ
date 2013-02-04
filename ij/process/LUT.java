package ij.process;
import ij.plugin.Colors;
import java.awt.image.*;
import java.awt.Color;

	/** This is an indexed color model that allows an
		lower and upper bound to be specified. */
    public class LUT extends IndexColorModel implements Cloneable {
        public double min, max;
	
    /** Constructs a LUT from red, green and blue byte arrays, which must have a length of 256. */
    public LUT(byte r[], byte g[], byte b[]) {
    	this(8, 256, r, g, b);
	}
	
    /** Constructs a LUT from red, green and blue byte arrays, where 'bits' 
    	must be 8 and 'size' must be less than or equal to 256. */
    public LUT(int bits, int size, byte r[], byte g[], byte b[]) {
    	super(bits, size, r, g, b);
	}
	
	public LUT(IndexColorModel cm, double min, double max) {
		super(8, cm.getMapSize(), getReds(cm), getGreens(cm), getBlues(cm));
		this.min = min;
		this.max = max;
	}
	
	static byte[] getReds(IndexColorModel cm) {
		byte[] reds=new byte[256]; cm.getReds(reds); return reds;
	}
	
	static byte[] getGreens(IndexColorModel cm) {
		byte[] greens=new byte[256]; cm.getGreens(greens); return greens;
	}
	
	static byte[] getBlues(IndexColorModel cm) {
		byte[] blues=new byte[256]; cm.getBlues(blues); return blues;
	}
	
	public byte[] getBytes() {
		int size = getMapSize();
		if (size!=256) return null;
		byte[] bytes = new byte[256*3];
		for (int i=0; i<256; i++) bytes[i] = (byte)getRed(i);
		for (int i=0; i<256; i++) bytes[256+i] = (byte)getGreen(i);
		for (int i=0; i<256; i++) bytes[512+i] = (byte)getBlue(i);
		return bytes;
	}
	
	public LUT createInvertedLut() {
		int mapSize = getMapSize();
		byte[] reds = new byte[mapSize];
		byte[] greens = new byte[mapSize];
		byte[] blues = new byte[mapSize];	
		byte[] reds2 = new byte[mapSize];
		byte[] greens2 = new byte[mapSize];
		byte[] blues2 = new byte[mapSize];	
		getReds(reds); 
		getGreens(greens); 
		getBlues(blues);
		for (int i=0; i<mapSize; i++) {
			reds2[i] = (byte)(reds[mapSize-i-1]&255);
			greens2[i] = (byte)(greens[mapSize-i-1]&255);
			blues2[i] = (byte)(blues[mapSize-i-1]&255);
		}
		return new LUT(8, mapSize, reds2, greens2, blues2);
	}
	
	/** Creates a color LUT from a Color. */
	public static LUT createLutFromColor(Color color) {
		byte[] rLut = new byte[256];
		byte[] gLut = new byte[256];
		byte[] bLut = new byte[256];
		int red = color.getRed();
		int green = color.getGreen();
		int blue = color.getBlue();
		double rIncr = ((double)red)/255d;
		double gIncr = ((double)green)/255d;
		double bIncr = ((double)blue)/255d;
		for (int i=0; i<256; ++i) {
			rLut[i] = (byte)(i*rIncr);
			gLut[i] = (byte)(i*gIncr);
			bLut[i] = (byte)(i*bIncr);
		}
		return new LUT(rLut, gLut, bLut);
	}

	public synchronized Object clone() {
		try {return super.clone();}
		catch (CloneNotSupportedException e) {return null;}
	}
	
	public  String toString() {
		return "rgb[0]="+Colors.colorToString(new Color(getRGB(0)))+", rgb[255]="
			+Colors.colorToString(new Color(getRGB(255)))+", min="+(int)min+", max="+(int)max;
	}

}
