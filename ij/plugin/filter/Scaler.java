package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import java.awt.*;

/** This plugin implements the Edit/Scale command. */
public class Scaler implements PlugInFilter {
    private ImagePlus imp;
    private static double xscale = 0.5;
    private static double yscale = 0.5;
    private static boolean newWindow = true;
    private static boolean interpolate = true;
    private static boolean processStack = true;
    private static String title = "Untitled";

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		IJ.register(Scaler.class);
		if (imp!=null) {
			Roi roi = imp.getRoi();
			if (roi!=null && roi.getType()>Roi.TRACED_ROI)
				imp.killRoi(); // ignore any line selection
		}
		return DOES_ALL;
	}

	public void run(ImageProcessor ip) {
		if (!showDialog())
			return;
		ip.setInterpolate(interpolate);
		imp.startTiming();
		try {
			if (newWindow && imp.getStackSize()>1 && processStack)
				createNewStack(imp, ip);
			else
				scale(ip);
		}
		catch(OutOfMemoryError o) {
			IJ.outOfMemory("Scale");
		}
		IJ.showProgress(1.0);
	}
	
	void createNewStack(ImagePlus imp, ImageProcessor ip) {
		Rectangle r = ip.getRoi();
		boolean crop = r.width!=imp.getWidth() || r.height!=imp.getHeight();
		int newWidth = (int)(r.width*xscale);
		int newHeight = (int)(r.height*yscale);
		int nSlices = imp.getStackSize();
	    ImageStack stack1 = imp.getStack();
	    ImageStack stack2 = new ImageStack(newWidth, newHeight);
 		ImageProcessor ip1, ip2;
		for (int i=1; i<=nSlices; i++) {
			IJ.showStatus("Scale: " + i + "/" + nSlices);
			ip1 = stack1.getProcessor(i);
			String label = stack1.getSliceLabel(i);
			if (crop) {
				ip1.setRoi(r);
				ip1 = ip1.crop();
			}
			ip1.setInterpolate(interpolate);
			ip2 = ip1.resize(newWidth, newHeight);
			if (ip2!=null)
				stack2.addSlice(label, ip2);
			IJ.showProgress(i, nSlices);
		}
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setStack(title, stack2);
		Calibration cal = imp2.getCalibration();
		if (cal.scaled()) {
			cal.pixelWidth *= 1.0/xscale;
			cal.pixelHeight *= 1.0/yscale;
		}
		IJ.showProgress(1.0);
		imp2.show();
	}

	void scale(ImageProcessor ip) {
		if (newWindow) {
			Rectangle r = ip.getRoi();
			int newWidth = (int)(xscale*r.width);
			int newHeight = (int)(yscale*r.height);
			ImagePlus imp2 = imp.createImagePlus();
			imp2.setProcessor(title, ip.resize(newWidth, newHeight));
			Calibration cal = imp2.getCalibration();
			if (cal.scaled()) {
				cal.pixelWidth *= 1.0/xscale;
				cal.pixelHeight *= 1.0/yscale;
			}
			imp2.show();
			imp.trimProcessor();
			imp2.trimProcessor();
		} else {
			if (processStack && imp.getStackSize()>1) {
				Undo.reset();
				StackProcessor sp = new StackProcessor(imp.getStack(), ip);
				sp.scale(xscale, yscale);
			} else
				ip.scale(xscale, yscale);
			imp.killRoi();
		}
	}
	
	boolean showDialog() {
		boolean isStack = imp.getStackSize()>1;
		GenericDialog gd = new GenericDialog("Scale");
		gd.addNumericField("X Scale (0.05-25):", xscale, 2);
		gd.addNumericField("Y Scale (0.05-25):", yscale, 2);
		gd.addCheckbox("Interpolate", interpolate);
		if (isStack)
			gd.addCheckbox("Process Entire Stack", processStack);
		gd.addCheckbox("Create New Window", newWindow);
		gd.addStringField("Title:", title);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		xscale = gd.getNextNumber();
		yscale = gd.getNextNumber();
		if (gd.invalidNumber()) {
			IJ.error("X or Y scale are invalid.");
			return false;
		}
		if (xscale > 25.0) xscale = 25.0;
		if (xscale < 0.05) xscale = 0.05;
		if (yscale > 25.0) yscale = 25.0;
		if (yscale < 0.05) yscale = 0.05;
		interpolate = gd.getNextBoolean();
		if (isStack)
			processStack = gd.getNextBoolean();
		newWindow = gd.getNextBoolean();
		title = gd.getNextString();
		return true;
	}

}