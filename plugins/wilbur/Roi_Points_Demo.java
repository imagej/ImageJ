package plugins.wilbur;

import java.awt.Point;
import java.util.Iterator;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;


/**
 * This plugin demonstrates how to iterate over all points
 * contained in a user-selected ROI (possibly composed of
 * multiple ROIs).
 * 
 * @author WB
 * @version 2017/04/16
 */
public class Roi_Points_Demo implements PlugInFilter {

	private ImagePlus im = null;

	public int setup(String arg, ImagePlus im) {
		this.im = im;
		return DOES_RGB;
	}
	
	public void run(ImageProcessor ip) {
		int[] rgb = {0, 255, 0};
		
		Roi roi = im.getRoi();
		if (roi == null) {
			IJ.log("No roi selected!");
			return;	
		}
		IJ.log("wilbur: ROI = " + roi.toString());
		
		for (Point p : roi) {		
			ip.putPixel(p.x, p.y, rgb);
		}
	}

}
