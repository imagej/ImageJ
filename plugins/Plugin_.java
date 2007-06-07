import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.filter.*;

public class Plugin_ implements PlugInFilter {
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL;
	}

	public void run(ImageProcessor ip) {
  Roi roi = new OvalRoi(50,50,75,50, null); // create an oval ROI 
  imp.setRoi(roi); // assign it to the ImagePlus
  //ImageProcessor ip = imp.getProcessor(); // get the ImageProcessor
  ip = imp.getProcessor(); // get the ImageProcessor
  ip.snapshot(); // needed for reset(mask) to work
  int[] mask = imp.getMask(); // get the mask needed to process non-rect ROIs
  ip.setRoi(roi.getBounds()); // define a rectangular area
  ip.invert();
IJ.log("mask: "+mask);
  ip.reset(mask); // restore image outside of mask
  imp.updateAndDraw(); // update the display

	}

}
