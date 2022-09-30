package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;

/** Implements the conversion commands in the Image/Type submenu. */
public class Converter implements PlugIn {

	/** obsolete */
	public static boolean newWindowCreated;
	private ImagePlus imp;

	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			if ("RGB Color".equals(arg))
				imp.setProp(LUT.nameKey,null);
			if (imp.isComposite() && arg.equals("RGB Color") && !imp.getStack().isRGB() && !imp.getStack().isHSB() && !imp.getStack().isLab()) {
				if (imp.getWindow()==null && !ij.macro.Interpreter.isBatchMode())
					RGBStackConverter.convertToRGB(imp);
				else {
					(new RGBStackConverter()).run("");
					imp.setTitle(imp.getTitle()); // updates size in Window menu
				}
			} else if (imp.isComposite() && imp.getNChannels()>1 && imp.getNChannels()==imp.getStackSize() && arg.equals("RGB Stack")) {
				convertCompositeToRGBStack(imp);
			} else if (imp.lock()) {
				convert(arg);
				imp.unlock();
				imp.setTitle(imp.getTitle());
			} else 
				IJ.log("<<Converter: image is locked ("+imp+")>>");
		} else
			IJ.noImage();
	}

	/** Converts the ImagePlus to the specified image type. The string
	argument corresponds to one of the labels in the Image/Type submenu
	("8-bit", "16-bit", "32-bit", "8-bit Color", "RGB Color", "RGB Stack", "HSB Stack", "Lab Stack" or "HSB (32-bit)"). */
	public void convert(String item) {
		int type = imp.getType();
		ImageStack stack = null;
		if (imp.getStackSize()>1)
			stack = imp.getStack();
		String msg = "Converting to " + item;
		IJ.showStatus(msg + "...");
	 	long start = System.currentTimeMillis();
	 	Roi roi = imp.getRoi();
	 	imp.deleteRoi();
	 	if (imp.isThreshold())
			imp.getProcessor().resetThreshold();
	 	boolean saveChanges = imp.changes;
		imp.changes = IJ.getApplet()==null; //if not applet, set 'changes' flag
	 	ImageWindow win = imp.getWindow();
		try {
 			if (stack!=null) {
 				boolean wasVirtual = stack.isVirtual();
				// do stack conversions
		    	if (stack.isRGB() && item.equals("RGB Color")) {
					new ImageConverter(imp).convertRGBStackToRGB();
					if (win!=null) new ImageWindow(imp, imp.getCanvas()); // replace StackWindow with ImageWindow
		    	} else if (stack.isHSB() && item.equals("RGB Color")) {
					new ImageConverter(imp).convertHSBToRGB();
					if (win!=null) new ImageWindow(imp, imp.getCanvas());
		    	} else if (stack.isHSB32() && item.equals("RGB Color")) {
					new ImageConverter(imp).convertHSB32ToRGB();
					if (win!=null) new ImageWindow(imp, imp.getCanvas());
		    	} else if (stack.isLab() && item.equals("RGB Color")) {
					new ImageConverter(imp).convertLabToRGB();
					if (win!=null) new ImageWindow(imp, imp.getCanvas());
				} else if (item.equals("8-bit"))
					new StackConverter(imp).convertToGray8();
				else if (item.equals("16-bit"))
					new StackConverter(imp).convertToGray16();
				else if (item.equals("32-bit"))
					new ImageConverter(imp).convertToGray32();
				else if (item.equals("RGB Color"))
					new StackConverter(imp).convertToRGB();
				else if (item.equals("RGB Stack"))
					new StackConverter(imp).convertToRGBHyperstack();
				else if (item.equals("HSB Stack"))
					new StackConverter(imp).convertToHSBHyperstack();
				else if (item.equals("HSB (32-bit)"))
					new StackConverter(imp).convertToHSB32Hyperstack();
				else if (item.equals("Lab Stack"))
					new StackConverter(imp).convertToLabHyperstack();
		    	else if (item.equals("8-bit Color")) {
		    		int nColors = getNumber();
		    		if (nColors!=0)
						new StackConverter(imp).convertToIndexedColor(nColors);
				} else throw new IllegalArgumentException();
				if (wasVirtual) imp.setTitle(imp.getTitle());
			} else {
				// do single image conversions
				Undo.setup(Undo.TYPE_CONVERSION, imp);
		    	ImageConverter ic = new ImageConverter(imp);
				if (item.equals("8-bit"))
					ic.convertToGray8();
				else if (item.equals("16-bit"))
					ic.convertToGray16();
				else if (item.equals("32-bit"))
					ic.convertToGray32();
				else if (item.equals("RGB Stack")) {
			    	Undo.reset(); // Reversible; no need for Undo
					ic.convertToRGBStack();
		    	} else if (item.equals("HSB Stack")) {
			    	Undo.reset();
					ic.convertToHSB();
		    	} else if (item.equals("HSB (32-bit)")) {
			    	Undo.reset();
					ic.convertToHSB32();
		    	} else if (item.equals("Lab Stack")) {
			    	Undo.reset();
					ic.convertToLab();
		    	} else if (item.equals("RGB Color")) {
					ic.convertToRGB();
		    	} else if (item.equals("8-bit Color")) {
		    		int nColors = getNumber();
		 			start = System.currentTimeMillis();
					if (nColors!=0)
						ic.convertRGBtoIndexedColor(nColors);
				} else {
					imp.changes = saveChanges;
					return;
				}
				IJ.showProgress(1.0);
			}
			if ("RGB Color".equals(item))
				imp.setProp(LUT.nameKey,null);
		}
		catch (IllegalArgumentException e) {
			unsupportedConversion(imp);
			IJ.showStatus("");
	    	Undo.reset();
	    	imp.changes = saveChanges;
			Menus.updateMenus();
			Macro.abort();
			return;
		}
		if (roi!=null)
			imp.setRoi(roi);
		IJ.showTime(imp, start, "");
		imp.repaintWindow();
		Menus.updateMenus();
	}

	void unsupportedConversion(ImagePlus imp) {
		IJ.error("Converter",
			"Supported Conversions:\n" +
			" \n" +
			"8-bit -> 16-bit*\n" +
			"8-bit -> 32-bit*\n" +
			"8-bit -> RGB Color*\n" +
			"16-bit -> 8-bit*\n" +
			"16-bit -> 32-bit*\n" +
			"16-bit -> RGB Color*\n" +
			"32-bit -> 8-bit*\n" +
			"32-bit -> 16-bit\n" +
			"32-bit -> RGB Color*\n" +
			"8-bit Color -> 8-bit (grayscale)*\n" +
			"8-bit Color -> RGB Color\n" +
			"RGB -> 8-bit (grayscale)*\n" +
			"RGB -> 8-bit Color*\n" +
			"RGB -> RGB Stack*\n" +
			"RGB -> HSB Stack*\n" +
			"RGB -> Lab Stack\n" +
			"RGB Stack -> RGB Color\n" +
			"HSB Stack -> RGB Color\n" +
			" \n" +
			"* works with stacks\n"
			);
	}

	int getNumber() {
		if (imp.getType()!=ImagePlus.COLOR_RGB)
			return 256;
		GenericDialog gd = new GenericDialog("MedianCut");
		gd.addNumericField("Number of Colors (2-256):", 256, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return 0;
		int n = (int)gd.getNextNumber();
		if (n<2) n = 2;
		if (n>256) n = 256;
		return n;
	}
	
	private void convertCompositeToRGBStack(ImagePlus imp) {
		imp.setDisplayMode(IJ.COLOR);
		if (imp.getNChannels()!=imp.getStackSize())
			IJ.run(imp, "Reduce Dimensionality...", "channels");
		ImagePlus[] channels = ChannelSplitter.split(imp);
		ImageStack stack = new ImageStack();
		for (int i=0; i<channels.length; i++) {
			ImageProcessor ip = channels[i].getProcessor();
			ip = ip.convertToRGB();
			stack.addSlice(ip);
		}
		imp.setStack(stack);
	}
		
}
