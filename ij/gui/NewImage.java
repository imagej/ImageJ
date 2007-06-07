package ij.gui;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import ij.*;
import ij.process.*;
import ij.plugin.frame.Editor;

/** New image dialog box plus several static utility methods for creating images.*/
public class NewImage {

	public static final int GRAY8=0, GRAY16=1, GRAY32=2, RGB=3;
	public static final int FILL_WHITE=0, FILL_BLACK=1, FILL_RAMP=2;
	
    private static String name = "Untitled";
    private static int width = 400;
    private static int height = 400;
    private static int slices = 1;
    private static int type = GRAY8;
    private static int fillWith = FILL_WHITE;
    private static String[] types = {"8-bit Unsigned", "16-bit Unsigned", "32-bit Real", "32-bit RGB"};
    private static String[] fill = {"White", "Black", "Ramp", "Clipboard"};
	
    public NewImage() {
    	openImage();
    }
    
	static void createStack(ImagePlus imp, ImageProcessor ip, int nSlices, int type) {
		int width = imp.getWidth();
		int height = imp.getHeight();
		ImageStack stack = imp.createEmptyStack();
		try {
			stack.addSlice(null, ip);
			for (int i=2; i<=nSlices; i++) {
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
					ramp[i] = (byte)((i*255)/(width-1));
				for (int y=0; y<height; y++) {
					int offset = y*width;
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
				int r,g,b;
				for (int y=0; y<height; y++) {
					for (int x=0; x<width; x++) {
						r = g = b = (byte)((x*255)/(width-1));
						pixels[y*width+x] = 0xff000000 | ((r<<16)&0xff0000) | ((g<<8)&0xff00) | (b&0xff);
					}
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
				for (int y=0; y<height; y++)
					for (int x=0; x<width; x++)
						pixels[y*width+x] = (short)((x*65535)/(width-1));
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
				for (int y=0; y<height; y++)
					for (int x=0; x<width; x++)
						pixels[y*width+x] = (float)((x*1.0)/(width-1));
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
		GenericDialog gd = new GenericDialog("New...", IJ.getInstance());
		gd.addStringField("Name:", name, 12);
		gd.addChoice("Type:", types, types[type]);
		gd.addChoice("Fill With:", fill, fill[fillWith]);
		gd.addNumericField("Width (pixels):", width, 0);
		gd.addNumericField("Height (pixels):", height, 0);
		gd.addNumericField("Slices:", slices, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		name = gd.getNextString();
		type = gd.getNextChoiceIndex();
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
	
}