package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.awt.*;

/** Implements the commands in the Process/Binary submenu. */
public class Binary implements PlugInFilter {
	
	String arg;
	ImagePlus imp;
	static int iterations = 1;

	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		IJ.register(Binary.class);
		
		if (arg.equals("set")) {
			setIterations();
			return DONE;
		}
		
		if (arg.equals("outline") || arg.equals("skel")) {
			if (imp!=null && (imp.getType()==ImagePlus.GRAY8 || imp.getType()==ImagePlus.COLOR_256)) {
				ImageStatistics stats = imp.getStatistics();
				if (stats.histogram[0]+stats.histogram[255]!=stats.pixelCount) {
					IJ.error("8-bit binary (black and white only) image required.");
					return DONE;
				}
			}
			return IJ.setupDialog(imp, DOES_8G+DOES_8C+SUPPORTS_MASKING);
		} else
			return IJ.setupDialog(imp, DOES_8G+DOES_8C+DOES_RGB+SUPPORTS_MASKING);
	}

	public void run(ImageProcessor ip) {
		if (arg.equals("erode")) erode(ip);
		else if (arg.equals("dilate")) dilate(ip);
		else if (arg.equals("open")) open(ip);
		else if (arg.equals("close")) close(ip);
		else if (arg.equals("outline")) ((ByteProcessor)ip).outline();
		else if (arg.equals("skel")) ((ByteProcessor)ip).skeletonize();
	}

		
	void erode(ImageProcessor ip) {
		for (int i=0; i<iterations; i++) {
			ip.erode();
			if (iterations>1) imp.updateAndDraw();
		}
	}
	
	void dilate(ImageProcessor ip) {
		for (int i=0; i<iterations; i++) {
			ip.dilate();
			if (iterations>1) imp.updateAndDraw();
		}
	}
	

	void open(ImageProcessor ip) {
		for (int i=0; i<iterations; i++) {
			ip.erode();
			if (iterations>1) imp.updateAndDraw();
		}
		for (int i=0; i<iterations; i++) {
			ip.dilate();
			if (iterations>1) imp.updateAndDraw();
		}
	}
	
	void close(ImageProcessor ip) {
		for (int i=0; i<iterations; i++) {
			ip.dilate();
			if (iterations>1) imp.updateAndDraw();
		}
		for (int i=0; i<iterations; i++) {
			ip.erode();
			if (iterations>1) imp.updateAndDraw();
		}
	}
		
	void setIterations() {
		int n = (int)IJ.getNumber("Iterations (1-25):", iterations);
		if (n==IJ.CANCELED) return;
		if (n>25) n = 25;
		if (n<1) n = 1;
		iterations = n;
	}

}
