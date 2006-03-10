package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.awt.*;

/** This plugin implements the Image/Rotate command. */
public class Rotator implements PlugInFilter {
    private static double angle = 15.0;
    private static boolean interpolate = true;
    private static boolean fillWithBackground;
    private static boolean firstTime;
    private static boolean canceled;
    private ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		IJ.register(Rotator.class);
		firstTime = true;
		canceled = false;
		return IJ.setupDialog(imp, DOES_ALL);
	}

	public void run(ImageProcessor ip) {
		if (canceled)
			return;
		int bitDepth = imp.getBitDepth();
		if (firstTime) {
			GenericDialog gd = new GenericDialog("Rotate", IJ.getInstance());
			gd.addNumericField("Angle (degrees): ", angle, 2);
			gd.addCheckbox("Interpolate", interpolate);
			if (bitDepth==8 || bitDepth==24)
				gd.addCheckbox("Fill with Background Color", fillWithBackground);
			gd.showDialog();
			canceled = gd.wasCanceled();
			if (canceled)
				return;
			angle = gd.getNextNumber();
			if (gd.invalidNumber()) {
				IJ.error("Angle is invalid.");
				return;
			}
			interpolate = gd.getNextBoolean();
			if (bitDepth==8 || bitDepth==24)
				fillWithBackground = gd.getNextBoolean();
			imp.startTiming();
			firstTime = false;
		}
		ip.setInterpolate(interpolate);
		if (fillWithBackground) {
			Color bgc = Toolbar.getBackgroundColor();
			if (bitDepth==8)
				ip.setBackgroundValue(ip.getBestIndex(bgc));
			else if (bitDepth==24)
				ip.setBackgroundValue(bgc.getRGB());
		} else {
			if (bitDepth==8)
				ip.setBackgroundValue(ip.isInvertedLut()?0.0:255.0); // white
			else if (bitDepth==24)
				ip.setBackgroundValue(0xffffffff); // white
		}
		ip.rotate(angle);
	}
	
}