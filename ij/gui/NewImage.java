package ij.gui;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import ij.*;
import ij.process.*;

/** New image dialog box plus several static utility methods for creating images.*/
public class NewImage {

	public static final int GRAY8=0, GRAY16=1, GRAY32=2, RGB=3;
	public static final int FILL_BLACK=1, FILL_RAMP=2, FILL_RANDOM=3, FILL_WHITE=4, CHECK_AVAILABLE_MEMORY=8;
	private static final int OLD_FILL_WHITE=0;
	
    static final String TYPE = "new.type";
    static final String FILL = "new.fill";
	static final String WIDTH = "new.width";
	static final String HEIGHT = "new.height";
	static final String SLICES = "new.slices";

    private static String name = "Untitled";
    private static int width = Prefs.getInt(WIDTH, 400);
    private static int height = Prefs.getInt(HEIGHT, 400);
    private static int slices = Prefs.getInt(SLICES, 1);
    private static int type = Prefs.getInt(TYPE, GRAY8);
    private static int fillWith = Prefs.getInt(FILL, FILL_BLACK);
    private static String[] types = {"8-bit", "16-bit", "32-bit", "RGB"};
    private static String[] fill = {"White", "Black", "Ramp", "Random"};
    
	
    public NewImage() {
    	openImage();
    }
    
	static boolean createStack(ImagePlus imp, ImageProcessor ip, int nSlices, int type, int options) {
		int fill = getFill(options);
		int width = imp.getWidth();
		int height = imp.getHeight();
		long bytesPerPixel = 1;
		if (type==GRAY16) bytesPerPixel = 2;
		else if (type==GRAY32||type==RGB) bytesPerPixel = 4;
		long size = (long)width*height*nSlices*bytesPerPixel;
		boolean bigStack = size/(1024*1024)>=50;
		String size2 = size/(1024*1024)+"MB ("+width+"x"+height+"x"+nSlices+")";
		if ((options&CHECK_AVAILABLE_MEMORY)!=0) {
			long max = IJ.maxMemory(); // - 100*1024*1024;
			if (max>0) {
				long inUse = IJ.currentMemory();
				long available = max - inUse;
				if (size>available)
					System.gc();
				inUse = IJ.currentMemory();
				available = max-inUse;
				if (size>available) {
					IJ.error("Insufficient Memory", "There is not enough free memory to allocate a \n"
					+ size2+" stack.\n \n"
					+ "Memory available: "+available/(1024*1024)+"MB\n"		
					+ "Memory in use: "+IJ.freeMemory()+"\n \n"	
					+ "More information can be found in the \"Memory\"\n"
					+ "sections of the ImageJ installation notes at\n"
					+ "\""+IJ.URL+"/docs/install/\".");
					return false;
				}
			}
		}
		ImageStack stack = imp.createEmptyStack();
		int inc = nSlices/40;
		if (inc<1) inc = 1;
		IJ.showStatus("Allocating "+size2+". Press 'Esc' to abort.");
		IJ.resetEscape();
		try {
			stack.addSlice(null, ip);
			for (int i=2; i<=nSlices; i++) {
				if ((i%inc)==0 && bigStack)
					IJ.showProgress(i, nSlices);
				Object pixels2 = null;
				switch (type) {
					case GRAY8: pixels2 = new byte[width*height]; break;
					case GRAY16: pixels2 = new short[width*height]; break;
					case GRAY32: pixels2 = new float[width*height]; break;
					case RGB: pixels2 = new int[width*height]; break;
				}
				if (fill!=FILL_BLACK || type==RGB)
					System.arraycopy(ip.getPixels(), 0, pixels2, 0, width*height);
				stack.addSlice(null, pixels2);
				if (IJ.escapePressed()) {IJ.beep(); break;};
			}
		}
		catch(OutOfMemoryError e) {
			IJ.outOfMemory(imp.getTitle());
			stack.trim();
		}
		IJ.showStatus("");
		if (bigStack)
			IJ.showProgress(nSlices, nSlices);
		if (stack.getSize()>1)
			imp.setStack(null, stack);
		return true;
	}

	static int getFill(int options) {
		int fill = options&7; 
		if (fill==OLD_FILL_WHITE)
			fill = FILL_WHITE;
		if (fill==7||fill==6||fill==5)
			fill = FILL_BLACK;
		return fill;
	}

	public static ImagePlus createByteImage(String title, int width, int height, int slices, int options) {
		int fill = getFill(options);
		int size = getSize(width, height);
		if (size<0) return null;
		byte[] pixels = new byte[size];
		ImageProcessor ip = new ByteProcessor(width, height, pixels, null);
		switch (fill) {
			case FILL_WHITE:
				for (int i=0; i<width*height; i++)
					pixels[i] = (byte)255;
				break;
			case FILL_BLACK:
				break;
			case FILL_RAMP:
				byte[] ramp = new byte[width];
				for (int i=0; i<width; i++)
					ramp[i] = (byte)((i*256.0)/width);
				int offset;
				for (int y=0; y<height; y++) {
					offset = y*width;
					for (int x=0; x<width; x++)
						pixels[offset++] = ramp[x];
				}
				break;
			case FILL_RANDOM:
				ip.add(127);
				ip.noise(31);
				break;
		}
		ImagePlus imp = new ImagePlus(title, ip);
		if (slices>1) {
			boolean ok = createStack(imp, ip, slices, GRAY8, options);
			if (!ok) imp = null;
		}
		return imp;
	}

	public static ImagePlus createRGBImage(String title, int width, int height, int slices, int options) {
		int fill = getFill(options);
		int size = getSize(width, height);
		if (size<0) return null;
		int[] pixels = new int[size];
		ColorProcessor ip = new ColorProcessor(width, height, pixels);
		switch (fill) {
			case FILL_WHITE:
				for (int i=0; i<width*height; i++)
					pixels[i] = -1;
				break;
			case FILL_BLACK:
				for (int i=0; i<width*height; i++)
					pixels[i] = 0xff000000;
				break;
			case FILL_RAMP:
				int r,g,b,offset;
				int[] ramp = new int[width];
				for (int i=0; i<width; i++) {
					r = g = b = (byte)((i*256.0)/width);
					ramp[i] = 0xff000000 | ((r<<16)&0xff0000) | ((g<<8)&0xff00) | (b&0xff);
				}
				for (int y=0; y<height; y++) {
					offset = y*width;
					for (int x=0; x<width; x++)
						pixels[offset++] = ramp[x];
				}
				break;
			case FILL_RANDOM:
				ByteProcessor rr = new ByteProcessor(width, height);
				ByteProcessor gg = new ByteProcessor(width, height);
				ByteProcessor bb = new ByteProcessor(width, height);
				rr.add(127); gg.add(127); bb.add(127);
				rr.noise(31); gg.noise(31); bb.noise(31);
				ip.setChannel(1,rr); ip.setChannel(2,gg); ip.setChannel(3,bb);
				break;
		}
		ImagePlus imp = new ImagePlus(title, ip);
		if (slices>1) {
			boolean ok = createStack(imp, ip, slices, RGB, options);
			if (!ok) imp = null;
		}
		return imp;
	}
	
	/** Creates an unsigned short image. */
	public static ImagePlus createShortImage(String title, int width, int height, int slices, int options) {
		int fill = getFill(options);
		int size = getSize(width, height);
		if (size<0) return null;
		short[] pixels = new short[size];
		ImageProcessor ip = new ShortProcessor(width, height, pixels, null);
		switch (fill) {
			case FILL_WHITE: case FILL_BLACK:
				break;
			case FILL_RAMP:
				short[] ramp = new short[width];
				for (int i=0; i<width; i++)
					ramp[i] = (short)(((i*65536.0)/width)+0.5);
				int offset;
				for (int y=0; y<height; y++) {
					offset = y*width;
					for (int x=0; x<width; x++)
						pixels[offset++] = ramp[x];
				}
				break;
			case FILL_RANDOM:
				ip.add(32767);
				ip.noise(7940);
				break;
		}
	    if (fill==FILL_WHITE)
	    	ip.invertLut();
		ImagePlus imp = new ImagePlus(title, ip);
		if (slices>1) {
			boolean ok = createStack(imp, ip, slices, GRAY16, options);
			if (!ok) imp = null;
		}
		imp.getProcessor().setMinAndMax(0, 65535); // default display range
		return imp;
	}

	/**
	* @deprecated
	* Short images are always unsigned.
	*/
	public static ImagePlus createUnsignedShortImage(String title, int width, int height, int slices, int options) {
		return createShortImage(title, width, height, slices, options);
	}

	public static ImagePlus createFloatImage(String title, int width, int height, int slices, int options) {
		int fill = getFill(options);
		int size = getSize(width, height);
		if (size<0) return null;
		float[] pixels = new float[size];
		ImageProcessor ip = new FloatProcessor(width, height, pixels, null);
		switch (fill) {
			case FILL_WHITE: case FILL_BLACK:
				break;
			case FILL_RAMP:
				float[] ramp = new float[width];
				for (int i=0; i<width; i++)
					ramp[i] = (float)((i*1.0)/width);
				int offset;
				for (int y=0; y<height; y++) {
					offset = y*width;
					for (int x=0; x<width; x++)
						pixels[offset++] = ramp[x];
				}
				break;
			case FILL_RANDOM:
				ip.noise(1);
				break;
		}
	    if (fill==FILL_WHITE)
	    	ip.invertLut();
		ImagePlus imp = new ImagePlus(title, ip);
		if (slices>1) {
			boolean ok = createStack(imp, ip, slices, GRAY32, options);
			if (!ok) imp = null;
		}
		if (fill!=FILL_RANDOM)
			imp.getProcessor().setMinAndMax(0.0, 1.0); // default display range
		return imp;
	}

	private static int getSize(int width, int height) {
		long size = (long)width*height;
		if (size>Integer.MAX_VALUE) {
			IJ.error("Image is too large. ImageJ does not support\nsingle images larger than 2 gigapixels.");
			return -1;
		} else
			return (int)size;
	}

	public static void open(String title, int width, int height, int nSlices, int type, int options) {
		int bitDepth = 8;
		if (type==GRAY16) bitDepth = 16;
		else if (type==GRAY32) bitDepth = 32;
		else if (type==RGB) bitDepth = 24;
		long startTime = System.currentTimeMillis();
		ImagePlus imp = createImage(title, width, height, nSlices, bitDepth, options);
		if (imp!=null) {
			WindowManager.checkForDuplicateName = true;          
			imp.show();
			IJ.showStatus(IJ.d2s(((System.currentTimeMillis()-startTime)/1000.0),2)+" seconds");
		}
	}

	public static ImagePlus createImage(String title, int width, int height, int nSlices, int bitDepth, int options) {
		ImagePlus imp = null;
		switch (bitDepth) {
			case 8: imp = createByteImage(title, width, height, nSlices, options); break;
			case 16: imp = createShortImage(title, width, height, nSlices, options); break;
			case 32: imp = createFloatImage(title, width, height, nSlices, options); break;
			case 24: imp = createRGBImage(title, width, height, nSlices, options); break;
			default: throw new IllegalArgumentException("Invalid bitDepth: "+bitDepth);
		}
		return imp;
	}
	
	boolean showDialog() {
		if (type<GRAY8|| type>RGB)
			type = GRAY8;
		if (fillWith<OLD_FILL_WHITE||fillWith>FILL_RANDOM)
			fillWith = FILL_WHITE;
		GenericDialog gd = new GenericDialog("New Image...", IJ.getInstance());
		gd.addStringField("Name:", name, 12);
		gd.addChoice("Type:", types, types[type]);
		gd.addChoice("Fill with:", fill, fill[fillWith]);
		gd.addNumericField("Width:", width, 0, 5, "pixels");
		gd.addNumericField("Height:", height, 0, 5, "pixels");
		gd.addNumericField("Slices:", slices, 0, 5, "");
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		name = gd.getNextString();
		String s = gd.getNextChoice();
		if (s.startsWith("8"))
			type = GRAY8;
		else if (s.startsWith("16"))
			type = GRAY16;
		else if (s.endsWith("RGB") || s.endsWith("rgb"))
			type = RGB;
		else
			type = GRAY32;
		fillWith = gd.getNextChoiceIndex();
		width = (int)gd.getNextNumber();
		height = (int)gd.getNextNumber();
		slices = (int)gd.getNextNumber();
		if (slices<1) slices = 1;
		if (width<1 || height<1) {
			IJ.error("New Image", "Width and height must be >0");
			return false;
		} else
			return true;
	}

	void openImage() {
		if (!showDialog())
			return;
		try {open(name, width, height, slices, type, fillWith);}
		catch(OutOfMemoryError e) {IJ.outOfMemory("New Image...");}
	}
	
	/** Called when ImageJ quits. */
	public static void savePreferences(Properties prefs) {
		prefs.put(TYPE, Integer.toString(type));
		prefs.put(FILL, Integer.toString(fillWith));
		prefs.put(WIDTH, Integer.toString(width));
		prefs.put(HEIGHT, Integer.toString(height));
		prefs.put(SLICES, Integer.toString(slices));
	}

}
