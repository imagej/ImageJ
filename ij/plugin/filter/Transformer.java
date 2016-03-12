package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.Calibration;
import java.awt.*;
import java.awt.image.*;

/** Implements the Flip and Rotate commands in the Image/Transform submenu. */
public class Transformer implements PlugInFilter {
	
	ImagePlus imp;
	String arg;

	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		if (arg.equals("fliph") || arg.equals("flipv"))
			return IJ.setupDialog(imp, DOES_ALL+NO_UNDO);
		else
			return DOES_ALL+NO_UNDO+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		Calibration cal = imp.getCalibration();
		boolean transformOrigin = cal.xOrigin!=0 || cal.yOrigin!=0;
		if (arg.equals("fliph")) {
			ip.flipHorizontal();
			Rectangle r = ip.getRoi();
			if (transformOrigin && r.x==0 && r.y==0 && r.width==ip.getWidth() && r.height==ip.getHeight())
				cal.xOrigin = imp.getWidth()-1 - cal.xOrigin;
			return;
		}
		if (arg.equals("flipv")) {
			ip.flipVertical();
			Rectangle r = ip.getRoi();
			if (transformOrigin && r.x==0 && r.y==0 && r.width==ip.getWidth() && r.height==ip.getHeight())
	    		cal.yOrigin = imp.getHeight()-1 - cal.yOrigin;
			return;
		}
		if (arg.equals("right") || arg.equals("left")) {
	    	StackProcessor sp = new StackProcessor(imp.getStack(), ip);
	    	ImageStack s2 = null;
			if (arg.equals("right")) {
	    		s2 = sp.rotateRight();
	    		if (transformOrigin) {
	    			double xOrigin = imp.getHeight()-1 - cal.yOrigin;
	    			double yOrigin = cal.xOrigin;
	    			cal.xOrigin = xOrigin;
	    			cal.yOrigin = yOrigin;
	    		}
	    	} else {
	    		s2 = sp.rotateLeft();
	    		if (transformOrigin) {
	    			double xOrigin = cal.yOrigin;
	    			double yOrigin = imp.getWidth()-1 - cal.xOrigin;
	    			cal.xOrigin = xOrigin;
	    			cal.yOrigin = yOrigin;
	    		}
	    	}
	    	imp.setStack(null, s2);
	    	double pixelWidth = cal.pixelWidth;
	    	cal.pixelWidth = cal.pixelHeight;
	    	cal.pixelHeight = pixelWidth;
			if (!cal.getXUnit().equals(cal.getYUnit())) {
				String xUnit = cal.getXUnit();
				cal.setXUnit(cal.getYUnit());
				cal.setYUnit(xUnit);
			}
			return;
		}
	}
	
}
