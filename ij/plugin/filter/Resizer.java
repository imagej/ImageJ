package ij.plugin.filter;
import ij.*;
import ij.process.*;

/** Obsolete; replaced by ij.plugin.Resizer. */
public class Resizer implements PlugInFilter {
 
	public int setup(String arg, ImagePlus imp) {
		return DONE;
	}

	public void run(ImageProcessor ip) {
	}

}