import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.filter.*;

public class Calculate_Mean implements PlugInFilter {
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+ROI_REQUIRED;
	}

	public void run(ImageProcessor ip) {
		Roi roi = imp.getRoi();
		if (roi==null) {
			IJ.error("Selection required"); 
			return;
		}

		ImageProcessor mask=null;
		int[] imask = null;
		//for (int i=0; i<1000; i++)
		mask = ((OvalRoi)roi).getByteMask();
		//imask = roi.getMask();
		//new ImagePlus("mask", mask).show(); // display the mask

		//int[] imask = roi.getMask();
		if (mask==null) {
			IJ.error("Non-rectangular selection required"); 
			return;
		}
		Rectangle r = roi.getBounds();
		//new ImagePlus("Mask", ipm).show(); // display the mask
		double sum = 0;
		int count = 0;
		for (int y=0; y<r.height; y++) {
			for (int x=0; x<r.width; x++) {
				if (mask.getPixel(x,y)!=0) {
					count++;
					sum += ip.getPixel(r.x+x, r.y+y);
				}
			}
		}
		IJ.log("count: "+count);
		IJ.log("mean: "+sum/count);
	}
}
