package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import java.awt.*;

/** This plug-in implements ImageJ's Resize command. */
public class Resizer implements PlugInFilter {
	ImagePlus imp;
	private boolean crop;
    private static int newWidth = 100;
    private static int newHeight = 100;
    private static boolean constrain = true;
    private static boolean interpolate = true;

	public int setup(String arg, ImagePlus imp) {
		crop = arg.equals("crop");
		this.imp = imp;
		IJ.register(Resizer.class);
		if (crop)
			return DOES_ALL+ROI_REQUIRED+NO_CHANGES;
		else
			return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		boolean sizeToHeight=false;
		if (crop) {
			Roi roi = imp.getRoi();
			Rectangle bounds = roi.getBoundingRect();
			newWidth = bounds.width;
			newHeight = bounds.height;
		} else {
			GenericDialog gd = new GenericDialog("Resize", IJ.getInstance());
			gd.addNumericField("New width (pixels):", newWidth, 0);
			gd.addNumericField("New Height (pixels):", newHeight, 0);
			gd.addCheckbox("Constrain Aspect Ratio", constrain);
			gd.addCheckbox("Interpolate", interpolate);
			gd.addMessage("NOTE: Undo is not available");
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			newWidth = (int)gd.getNextNumber();
			newHeight = (int)gd.getNextNumber();
			if (gd.invalidNumber()) {
				IJ.error("Width or height are invalid.");
				return;
			}
			constrain = gd.getNextBoolean();
			interpolate = gd.getNextBoolean();
			sizeToHeight = constrain && newWidth==0;
			if (newWidth<=0.0 && !constrain)  newWidth = 50;
			if (newHeight<=0.0) newHeight = 50;
		}
		
		Rectangle r = ip.getRoi();
		double oldWidth = r.width;;
		double oldHeight = r.height;
		if (!crop && constrain) {
			if (sizeToHeight)
				newWidth = (int)(newHeight*(oldWidth/oldHeight));
			else
				newHeight = (int)(newWidth*(oldHeight/oldWidth));
		}
		ip.setInterpolate(interpolate);
    	
		int nSlices = imp.getStackSize();
		try {
	    	StackProcessor sp = new StackProcessor(imp.getStack(), ip);
	    	ImageStack s2 = sp.resize(newWidth, newHeight);
	    	int newSize = s2.getSize();
	    	if (s2.getWidth()>0 && newSize>0) {
	    		imp.hide();
	    		Calibration cal = imp.getCalibration();
	    		if (cal.scaled()) {
    				cal.pixelWidth *= oldWidth/newWidth;
    				cal.pixelHeight *= oldHeight/newHeight;
    				imp.setCalibration(cal);
    			}
	    		imp.setStack(null, s2);
	    		imp.show();
	    	}
	    	if (nSlices>1 && newSize<nSlices)
	    		IJ.error("ImageJ ran out of memory causing \nthe last "+(nSlices-newSize)+" slices to be lost.");
		} catch(OutOfMemoryError o) {
			IJ.outOfMemory("Resize");
		}
	}

}