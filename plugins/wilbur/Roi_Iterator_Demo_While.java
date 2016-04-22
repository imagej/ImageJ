package plugins.wilbur;

import java.awt.Point;
import java.util.Iterator;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * This plugin demonstrates the use of the ROI point iterator.
 * a binary image of all points included in the selection.
 * statistics.
 * 
 * @author Wilhelm Burger
 * @version 2016/04/13
 */
public class Roi_Iterator_Demo_While implements PlugInFilter {

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
		
		Iterator<Point> iter = roi.iterator();
		while (iter.hasNext()) {
			Point p = iter.next();
			bp.putPixel(p.x, p.y, 255);
		}
		
		// iterate over all ROI points:
//		for (Point p : roi) {
//			bp.putPixel(p.x, p.y, 255);
//		}
		new ImagePlus("ROI points", bp).show();
	}
}
