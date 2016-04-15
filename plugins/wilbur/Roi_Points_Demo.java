package plugins.wilbur;

import java.awt.Point;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * This plugin demonstrates the use of ROIs. It displays
 * a binary image of all points included in the selection.
 * statistics.
 * 
 * @author WB
 * @version 2016/04/12
 */
public class Roi_Points_Demo implements PlugInFilter {

	private ImagePlus im = null;

	public int setup(String arg, ImagePlus im) {
		this.im = im;
		return DOES_RGB + ROI_REQUIRED + NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		Roi roi = im.getRoi();
		if (roi == null) {
			IJ.error("selection required!");
			return;
		}
		
		int w = ip.getWidth();
		int h = ip.getHeight();
		
		ByteProcessor bp = new ByteProcessor(w, h);
		Point[] pnts = roi.getContainedPoints();	// new (since IJ-1.51a5)!
		// iterate over all ROI points:
		for (Point p : pnts) {
			bp.putPixel(p.x, p.y, 255);
		}
		new ImagePlus("ROI points", bp).show();
	}
}
