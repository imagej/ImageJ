package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.Calibration;
import ij.plugin.filter.EDM;
import ij.plugin.filter.ThresholdToSelection;
import java.awt.*;

/** This plugin, which enlarges or shrinks selections, implements the Edit/Selection/Enlarge command. */
public class RoiEnlarger implements PlugIn {
	private static double defaultDistance = 15; // pixels

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		Roi roi = imp.getRoi();
		if (roi==null || roi.isLine()) {
			IJ.error("Enlarge", "This command requires an area selection");
			return;
		}
		if (!imp.okToDeleteRoi())
			return;
		double n = showDialog(imp, defaultDistance);
		if (n==Double.NaN)
			return;
		Roi roi2 = enlarge(roi, n);
		if (roi2!=null) {
			imp.setRoi(roi2);
			Roi.setPreviousRoi(roi);
			defaultDistance = n;
		}
	}
	
	public double showDialog(ImagePlus imp, double pixels) {
		Calibration cal = imp.getCalibration();
		boolean scaled = cal.scaled();
		boolean usePixels = false;
		double n = pixels*cal.pixelWidth;
		int decimalPlaces = 0;
		if (Math.floor(n)!=n)
			decimalPlaces = 2;
		GenericDialog gd = new GenericDialog("Enlarge Selection");
		gd.addNumericField("Enlarge by", n, decimalPlaces, 4, cal.getUnits());
		if (scaled) {
			gd.setInsets(0, 20, 0);
			gd.addCheckbox("Pixel units", usePixels);
		}
		gd.setInsets(10, 0, 0);
		gd.addMessage("Enter negative number to shrink", null, Color.darkGray);
		gd.showDialog();
		if (gd.wasCanceled())
			return Double.NaN;
		n = gd.getNextNumber();
		if (scaled)
			usePixels = gd.getNextBoolean();
		pixels = usePixels?n:n/cal.pixelWidth;
		return pixels;
	}
	
	public static Roi enlarge(Roi roi, double pixels) {
		if (pixels==0)
			return roi;
		int type = roi.getType();
		int n = (int)Math.round(pixels);
		if (type==Roi.RECTANGLE || type==Roi.OVAL)
			return enlargeRectOrOval(roi, n);
		if (n<0)
			return shrink(roi, -n);
		Rectangle bounds = roi.getBounds();
		int width = bounds.width;
		int height = bounds.height;
		width += 2*n + 2;
		height += 2*n + 2;
		ImageProcessor ip = new ByteProcessor(width, height);
		ip.invert();
		roi.setLocation(n+1, n+1);
		ip.setColor(0);
		ip.fill(roi);
		ip.setThreshold(0, 0, ImageProcessor.NO_LUT_UPDATE);
		Roi roi2 = (new ThresholdToSelection()).convert(ip);
		Rectangle bounds2 = roi2.getBounds();
		int xoffset = bounds2.x - (n+1);
		int yoffset = bounds2.y - (n+1);
		roi.setLocation(bounds.x, bounds.y);
		FloatProcessor edm = new EDM().makeFloatEDM (ip, 0, false);
		edm.setThreshold(0, n, ImageProcessor.NO_LUT_UPDATE);
		roi2 = (new ThresholdToSelection()).convert(edm);
		if (roi2==null)
			return roi;	
		roi2.setLocation(bounds.x-n+xoffset, bounds.y-n+yoffset);
		roi2.setStrokeColor(roi.getStrokeColor());
		if (roi.getStroke()!=null)
			roi2.setStroke(roi.getStroke());
		return roi2;
	}
	
	private static Roi enlargeRectOrOval(Roi roi, int n) {
		Rectangle bounds = roi.getBounds();
		bounds.x -= n;
		bounds.y -= n;
		bounds.width += 2*n;
		bounds.height += 2*n;
		if (bounds.width<=0 || bounds.height<=0)
			return roi;
		if (roi.getType()==Roi.RECTANGLE)
			return new Roi(bounds.x, bounds.y, bounds.width, bounds.height);
		else
			return new OvalRoi(bounds.x, bounds.y, bounds.width, bounds.height);
	}
   
	private static Roi shrink(Roi roi, int n) {
		Rectangle bounds = roi.getBounds();
		int width = bounds.width + 2;
		int height = bounds.height + 2;
		ImageProcessor ip = new ByteProcessor(width, height);
		roi.setLocation(1, 1);
		ip.setColor(255);
		ip.fill(roi);
		roi.setLocation(bounds.x, bounds.y);
		FloatProcessor edm = new EDM().makeFloatEDM (ip, 0, false);		
		edm.setThreshold(n+1, Float.MAX_VALUE, ImageProcessor.NO_LUT_UPDATE);
		Roi roi2 = (new ThresholdToSelection()).convert(edm);
		if (roi2==null)
			return roi;
		Rectangle bounds2 = roi2.getBounds();
		if (bounds2.width<=0 && bounds2.height<=0)
			return roi;
		roi2.setLocation(bounds.x+bounds2.x-1, bounds.y+bounds2.y-1);
		return roi2;
	}

}
