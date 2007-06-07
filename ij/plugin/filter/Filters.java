package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.awt.*;

/** This plug-in implements the Invert, Smooth, Sharpen, Detect Edges, 
	Add Noise, Reduce Noise, and Threshold commands. */
public class Filters implements PlugInFilter {
	
	String arg;

	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		return IJ.setupDialog(imp, DOES_ALL+SUPPORTS_MASKING);
	}

	public void run(ImageProcessor ip) {
		if (arg.equals("invert")) {
	 		ip.invert();
	 		return;
	 	}
	 	
		if (arg.equals("smooth")) {
	 		ip.smooth();
	 		return;
	 	}
	 	
		if (arg.equals("sharpen")) {
	 		ip.sharpen();
	 		return;
	 	}
	 	
		if (arg.equals("edge")) {
			ip.findEdges();
	 		return;
		}
						
	 	if (arg.equals("threshold")) {
	 		ImagePlus imp = WindowManager.getCurrentImage();
			ip.setMask(imp.getMask());
			ip.autoThreshold();
			ip.setMask(null);
			imp.killRoi();
			return;
		}
		
	 	if (arg.equals("add")) {
	 		ip.noise(25.0);
	 		return;
	 	}
	 	
	 	if (arg.equals("addmore")) {
	 		ip.noise(75.0);
	 		return;
	 	}
	 	
		if (arg.equals("median")) {
			ip.medianFilter();
	 		return;
		}

	}

}
