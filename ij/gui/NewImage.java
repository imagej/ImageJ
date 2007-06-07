package ij.gui;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import ij.*;
import ij.process.*;
import ij.plugin.frame.Editor;

/** New image dialog box plus several static utility methods for creating images.*/
public class NewImage {

	public static final int GRAY8=0, GRAY16=1, GRAY32=2, RGB=3;
	public static final int FILL_WHITE=0, FILL_BLACK=1, FILL_RAMP=2;
	
    static final String NAME = "new.name";
    static final String TYPE = "new.type";
    static final String FILL = "new.fill";
	static final String WIDTH = "new.width";
	static final String HEIGHT = "new.height";
	static final String SLICES = "new.slices";

    private static String name = Prefs.getString(NAME, "Untitled");
    private static int width = Prefs.getInt(WIDTH, 400);
    private static int height = Prefs.getInt(HEIGHT, 400);
    private static int slices = Prefs.getInt(SLICES, 1);
    private static int type = Prefs.getInt(TYPE, GRAY8);
    private static int fillWith = Prefs.getInt(FILL, FILL_WHITE);
    private static String[] types = {"8-bit", "16-bit", "32-bit", "RGB"};
    private static String[] fill = {"White", "Black", "Ramp", "Clipboard"};
    
	
    public NewImage() {
    	openImage();
    }
    
	static void createStack(ImagePlus imp, ImageProcessor ip, int nSlices, int type) {
		int width = imp.getWidth();
		int height = imp.getHeight();
		ImageStack stack = imp.createEmptyStack();
		int inc = nSlices/40;
		if (inc<1) inc = 1;
		try {
			stack.addSlice(null, ip);
			for (int i=2; i<=nSlices; i++) {
				if ((i%inc)==0) IJ.showProgress(i, nSlices);
				Object pixels2 = null;
				switch (type) {
					case GRAY8: pixels2 = new byte[width*height]; break;
					case GRAY16: pixels2 = new short[width*height]; break;
					case GRAY32: pixels2 = new float[width*height]; break;
					case RGB: pixels2 = new int[width*height]; break;
				}
				System.arraycopy(ip.getPixels(), 0, pixels2, 0, width*height);
				stack.addSlice(null, pixels2);
			}
		}
		catch(OutOfMemoryError e) {
			IJ.outOfMemory(imp.getTitle());
			stack.trim();
		}
		IJ.showProgress(nSlices, nSlices);
		if (stack.getSize()>1)
			imp.setStack(null, stack);
	}

	static ImagePlus createImagePlus() {
		//ImagePlus imp = WindowManager.getCurrentImage();
		//if (imp!=null)
		//	return imp.createImagePlus();
		//else
		return new ImagePlus();
	}

	public static ImagePlus createByteImage(String title, int width, int height, int slices, int fill) {
		byte[] pixels = new byte[width*height];
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
					ramp[i] = (byte)(((i*256.0)/width)+0.5);
				int offset;
				for (int y=0; y<height; y++) {
					offset = y*width;
					for (int x=0; x<width; x++)
						pixels[offset++] = ramp[x];
				}
				break;
		}
		ImageProcessor ip = new ByteProcessor(width, height, pixels, null);
		ImagePlus imp = createImagePlus();
		imp.setProcessor(title, ip);
		if (slices>1)
			createStack(imp, ip, slices, GRAY8);
		return imp;
	}

	public static ImagePlus createRGBImage(String title, int width, int height, int slices, int fill) {
		int[] pixels = new int[width*height];
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
					r = g = b = (byte)(((i*256.0)/width)+0.5);
					ramp[i] = 0xff000000 | ((r<<16)&0xff0000) | ((g<<8)&0xff00) | (b&0xff);
				}
				for (int y=0; y<height; y++) {
					offset = y*width;
					for (int x=0; x<width; x++)
						pixels[offset++] = ramp[x];
				}
				break;
		}
		ImageProcessor ip = new ColorProcessor(width, height, pixels);
		ImagePlus imp = createImagePlus();
		imp.setProcessor(title, ip);
		if (slices>1)
			createStack(imp, ip, slices, RGB);
		return imp;
	}

	/** Creates an unsigned short image. */
	public static ImagePlus createShortImage(String title, int width, int height, int slices, int fill) {
		short[] pixels = new short[width*height];
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
		}
	    ImageProcessor ip = new ShortProcessor(width, height, pixels, null);
		ImagePlus imp = createImagePlus();
		imp.setProcessor(title, ip);
		if (slices>1)
			createStack(imp, ip, slices, GRAY16);
		return imp;
	}

	/** Obsolete. Short images are always unsigned. */
	public static ImagePlus createUnsignedShortImage(String title, int width, int height, int slices, int fill) {
		return createShortImage(title, width, height, slices, fill);
	}

	public static ImagePlus createFloatImage(String title, int width, int height, int slices, int fill) {
		float[] pixels = new float[width*height];
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
		}
	    ImageProcessor ip = new FloatProcessor(width, height, pixels, null);
		ImagePlus imp = createImagePlus();
		imp.setProcessor(title, ip);
		if (slices>1)
			createStack(imp, ip, slices, GRAY32);
		return imp;
	}

	public static void open(String title, int width, int height, int nSlices, int type, int fill) {
		ImagePlus imp = null;
		switch (type) {
			case GRAY8:
				imp = createByteImage(title, width, height, nSlices, fill);
				break;
			case GRAY16:
				imp = createShortImage(title, width, height, nSlices, fill);
				break;
			case GRAY32:
				imp = createFloatImage(title, width, height, nSlices, fill);
				break;
			case RGB:
				imp = createRGBImage(title, width, height, nSlices, fill);
				break;
		}
		if (imp!=null)
			imp.show();
	}

	void showClipboard() {
		ImagePlus clipboard = ImageWindow.getClipboard();
		if (clipboard!=null)
			clipboard.show();
		else
			IJ.error("The clipboard is empty.");
		}

	boolean showDialog() {
		if (type<GRAY8|| type>RGB)
			type = GRAY8;
		if (fillWith<FILL_WHITE||fillWith>FILL_RAMP)
			fillWith = FILL_WHITE;
		GenericDialog gd = new GenericDialog("New...", IJ.getInstance());
		gd.addStringField("Name:", name, 12);
		gd.addChoice("Type:", types, types[type]);
		gd.addChoice("Fill With:", fill, fill[fillWith]);
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
		return true;
	}

	void openImage() {
		if (!showDialog())
			return;
		if (fillWith>FILL_RAMP)
			{showClipboard(); return;}
		try {open(name, width, height, slices, type, fillWith);}
		catch(OutOfMemoryError e) {IJ.outOfMemory("New...");}
	}
	
	/** Called when ImageJ quits. */
	public static void savePreferences(Properties prefs) {
		prefs.put(NAME, name);
		prefs.put(TYPE, Integer.toString(type));
		prefs.put(FILL, Integer.toString(fillWith));
		prefs.put(WIDTH, Integer.toString(width));
		prefs.put(HEIGHT, Integer.toString(height));
		prefs.put(SLICES, Integer.toString(slices));
	}

}