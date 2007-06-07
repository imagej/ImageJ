package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.awt.*;

/** This plug-in implements the Invert, Smooth, Sharpen, Detect Edges, 
	Add Noise, Reduce Noise, and Threshold commands. */
public class Filters implements PlugInFilter {
	
	private String arg;
	private ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		if (arg.equals("threshold"))
			return IJ.setupDialog(imp, DOES_8G+DOES_8C+DOES_RGB);
		else
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
