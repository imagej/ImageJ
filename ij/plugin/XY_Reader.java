package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.measure.*;
import ij.plugin.TextReader;

/** This plugin implements the File/Import/XY Coordinates command. It reads a
	two column text file, such as those created by File/Save As/XY Coordinates,
	as a polygon ROI. The ROI is displayed in the current image or, if the image
	is too small, in a new blank image.
*/
public class XY_Reader implements PlugIn {

	public void run(String arg) {
		TextReader tr = new TextReader();
		ImageProcessor ip = tr.open();
		if (ip==null)
			return;
		int width = ip.getWidth();
		int height = ip.getHeight();
		if (width!=2 || height<3) {
			IJ.showMessage("XY Reader", "Two column text file required");
			return;
		}
		float[] x = new float[height];
		float[] y = new float[height];
		boolean allIntegers = true;
		double length = 0.0;
		for (int i=0; i<height; i++) {
			x[i] = ip.getf(0, i);
			y[i] = ip.getf(1, i);
			if ((int)x[i]!=x[i] || (int)y[i]!=y[i])
				allIntegers = false;
			if (i>0) {
				double dx = x[i] - x[i-1];
				double dy = y[i] - y[i-1];
				length += Math.sqrt(dx*dx+dy*dy);
			}
		}
		Roi roi = null;
		int type = length/x.length>10?Roi.POLYGON:Roi.FREEROI;
		if (allIntegers)
			roi = new PolygonRoi(Roi.toIntR(x), Roi.toIntR(y), height, type);
		else
			roi = new PolygonRoi(x, y, height, type);
		Rectangle r = roi.getBoundingRect();
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null || imp.getWidth()<r.x+r.width || imp.getHeight()<r.y+r.height) {
			new ImagePlus(tr.getName(), new ByteProcessor(Math.abs(r.x)+r.width+10, Math.abs(r.y)+r.height+10)).show();
			imp = WindowManager.getCurrentImage();
		}
		if (imp!=null)
			imp.setRoi(roi);
	}
	
}
