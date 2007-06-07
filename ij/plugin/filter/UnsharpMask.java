package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.measure.*;
import ij.gui.*;
import java.awt.*;
import java.util.*;

/** This plugin implements ImageJ's Unsharp Mask command. */
public class UnsharpMask implements PlugInFilter, Measurements {

	private ImagePlus imp;
	private int slice;
	private boolean canceled;
	private ImageWindow win;
	private static double radius = 2;
	private static double weight = 0.6;
	private boolean isLineRoi;
	
	public int setup(String arg, ImagePlus imp) {
 		IJ.register(UnsharpMask.class);
		this.imp = imp;
		if (imp!=null) {
			win = imp.getWindow();
			win.running = true;
			Roi roi = imp.getRoi();
			isLineRoi= roi!=null && roi.getType()>=Roi.LINE;
		}
		if (imp!=null && !showDialog())
			return DONE;
		else
			return IJ.setupDialog(imp, DOES_ALL);
	}

	public void run(ImageProcessor ip) {
		if (canceled)
			return;
		if (win.running!=true)
			{canceled=true; IJ.beep(); return;}
		slice++;
		if (slice>1)
			IJ.showStatus("Unsharp Mask: "+slice+"/"+imp.getStackSize());
		if (isLineRoi)
			ip.resetRoi();
		sharpen(ip, radius, weight);
	}
	
	public void sharpen(ImageProcessor ip, double radius, double weight) {
		ip.setCalibrationTable(null);
		Rectangle rect = ip.getRoi();
		boolean isRoi = rect.width!=ip.getWidth()||rect.height!=ip.getHeight();
		boolean nonRectRoi = ip.getMask()!=null;
		ImageProcessor ip2 = ip;
		if (isRoi) {
			ip2.setRoi(rect);
			ip2 = ip2.crop();
		}
		boolean convertToFloat = (ip instanceof ByteProcessor) || (ip instanceof ShortProcessor);
		if (convertToFloat)
			ip2 = ip2.convertToFloat();
		ImageStatistics stats = ImageStatistics.getStatistics(ip2, MIN_MAX, null);
		double min = stats.min;
		double max = stats.max;
		ImageProcessor mask = ip2.duplicate();
		new GaussianBlur().blur(mask, radius);
		mask.multiply(weight);
		//new ImagePlus("", mask).show();
		ip2.copyBits(mask,0,0,Blitter.SUBTRACT);
		ip2.multiply(1.0/(1.0-weight));
		if (!(ip2 instanceof ColorProcessor)) {
			ip2.min(min);
			ip2.max(max);
		}
		if (nonRectRoi)
			ip.snapshot();
		if (convertToFloat) {
			ImageProcessor ip3;
			boolean bytes = ip instanceof ByteProcessor;
			boolean scale = bytes && imp.getStackSize()==1;
			if (bytes)
				ip3 = ip2.convertToByte(scale);
			else 
				ip3 = ip2.convertToShort(scale);
			ip.insert(ip3, rect.x, rect.y);
		} else if (isRoi)
			ip.insert(ip2, rect.x, rect.y);
		if (nonRectRoi)
			ip.reset(ip.getMask());
	}
	
	public boolean showDialog() {
		GenericDialog gd = new GenericDialog("Unsharp Mask...");
		gd.addNumericField("Gaussian Radius (1-15)", radius,0);
		gd.addNumericField("Mask Weight (0.2-0.9)", weight,2);
		gd.showDialog();
		if (gd.wasCanceled()) {
			canceled = true;
			return false;
		}
		radius = gd.getNextNumber();
		weight = gd.getNextNumber();
		return true;
	}

}


