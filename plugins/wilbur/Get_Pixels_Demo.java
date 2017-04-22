package plugins.wilbur;


import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.Line;
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
public class Get_Pixels_Demo implements PlugInFilter {

	private ImagePlus im = null;

	public int setup(String arg, ImagePlus im) {
		this.im = im;
		return DOES_RGB;
	}
	
	

	public void run(ImageProcessor ip) {
		
		Roi roi = im.getRoi();
		if (roi == null || !(roi instanceof Line)) {
			IJ.log("No line selected!");
			return;	
		}
		
		Line line = (Line) roi;
		double[] pixels = line.getPixels();
		
		IJ.log("no of line pixels: "  + pixels.length);
		
	}

}
