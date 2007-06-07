package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.awt.*;

/** This plug-in implements the Image/Rotate command. */
public class Rotator implements PlugInFilter {
    private static double angle = 15.0;
    private static boolean interpolate = true;
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
		if (firstTime) {
			GenericDialog gd = new GenericDialog("Rotate", IJ.getInstance());
			gd.addNumericField("Angle (degrees): ", angle, 1);
			gd.addCheckbox("Interpolate", interpolate);
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
			imp.startTiming();
			firstTime = false;
		}
		ip.setInterpolate(interpolate);
		ip.rotate(angle);
	}
	
}