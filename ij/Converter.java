package ij;

import java.awt.*;
import java.awt.image.*;
import ij.gui.*;
import ij.process.*;

/** This class implements ImageJ's Image/Type submenu. */
public class Converter {
	ImagePlus imp;
	static boolean newWindowCreated; // tested by WindowManager.setCurrentWindow()

	public Converter(ImagePlus imp) {
		this.imp = imp;
	}

	/** Converts the ImagePlus to the specified image type. The string
	argument corresponds to one of the labels in the Image/Type submenu
	("8-bit", "16-bit", "32-bit", "8-bit Color", "RGB Color", "RGB Stack" or "HSB Stack"). */
	public void convert(String item) {
		int type = imp.getType();
		ImageStack stack = null;
		if (imp.getStackSize()>1)
			stack = imp.getStack();
		String msg = "Converting to " + item;
		IJ.showStatus(msg + "...");
	 	long start = System.currentTimeMillis();
	 	boolean isRoi = imp.getRoi()!=null;
	 	imp.killRoi();
	 	boolean saveChanges = imp.changes;
		imp.changes = IJ.getApplet()==null; //if not applet, set 'changes' flag
	 	newWindowCreated = false;
		try {
 			if (stack!=null) {
				// do stack conversions
		    	if (stack.isRGB() && item.equals("RGB Color")) {
					new ImageConverter(imp).convertRGBStackToRGB();
		    		newWindowCreated = true;
		    		new ImageWindow(imp); // replace StackWindow with ImageWindow
		    	} else if (stack.isHSB() && item.equals("RGB Color")) {
					new ImageConverter(imp).convertHSBToRGB();
		    		newWindowCreated = true;
		    		new ImageWindow(imp);
				} else if (item.equals("8-bit"))
					new StackConverter(imp).convertToGray8();
				else if (item.equals("32-bit"))
					new StackConverter(imp).convertToGray32();
		    	else throw new IllegalArgumentException();
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
		    		newWindowCreated = true;
			    	new StackWindow(imp); // replace window with a StackWindow
		    	} else if (item.equals("HSB Stack")) {
			    	Undo.reset();
					ic.convertToHSB();
		    		newWindowCreated = true;
			    	new StackWindow(imp);
		    	} else if (item.equals("RGB Color")) {
					ic.convertToRGB();
		    	} else if (item.equals("8-bit Color")) {
		    		int nColors = 256;
					if (type==ImagePlus.COLOR_RGB)
						nColors = (int)IJ.getNumber("Number of Colors (2-256):", 256);
		 			start = System.currentTimeMillis();
					if (nColors!=IJ.CANCELED)
						ic.convertRGBtoIndexedColor(nColors);
				} else {
					imp.changes = saveChanges;
					return;
				}
				IJ.showProgress(1.0);
			}
			
		}
		catch (IllegalArgumentException e) {
			unsupportedConversion(imp);
			IJ.showStatus("");
	    	Undo.reset();
	    	imp.changes = saveChanges;
			Menus.updateMenus();
			return;
		}
		if (isRoi)
			imp.restoreRoi();
		IJ.showTime(imp, start, "");
		imp.repaintWindow();
		Menus.updateMenus();
	}
	

	void unsupportedConversion(ImagePlus imp) {
		IJ.showMessage("Converter",
			"Supported Conversions:\n" +
			" \n" +
			"8-bit -> 16-bit\n" +
			"8-bit -> 32-bit*\n" +
			"8-bit -> RGB Color\n" +
			"16-bit -> 8-bit*\n" +
			"16-bit -> 32-bit*\n" +
			"16-bit -> RGB Color\n" +
			"32-bit -> 8-bit*\n" +
			"32-bit -> 16-bit\n" +
			"32-bit -> RGB Color\n" +
			"8-bit Color -> 8-bit (grayscale)*\n" +
			"8-bit Color -> RGB Color\n" +
			"RGB Color -> 8-bit (grayscale)*\n" +
			"RGB Color -> 8-bit Color\n" +
			"RGB Color -> RGB Stack\n" +
			"RGB Color -> HSB Stack\n" +
			"RGB Stack -> RGB Color\n" +
			"HSB Stack -> RGB Color\n" +
			" \n" +
			"* supports stacks\n"
			);
	}
	
}
