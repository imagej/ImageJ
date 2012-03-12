package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.Calibration;
import java.awt.*;
import java.awt.image.*;

/** This plugin implements the Image/Transform/Bin command.
 * It reduces the size of an image or stack by binning groups of 
 * pixels of user-specified sizes. The resulting pixel can be 
 * calculated as average, median, maximum or minimum.
 *
 * @author Nico Stuurman
 * @author Wayne Rasband
 */
public class Binner implements PlugIn {
	public static int AVERAGE=0, MEDIAN=1, MIN=2, MAX=3;
	private static String[] methods = {"Average", "Median", "Min", "Max"};
	private int xshrink=2, yshrink=2, zshrink=1;
	private int method = AVERAGE;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		if (!showDialog(imp))
			return;
		if (imp.getStackSize()==1)
			Undo.setup(Undo.TYPE_CONVERSION, imp);
		imp.startTiming();
		ImagePlus imp2 = shrink(imp, xshrink, yshrink, zshrink, method);
		IJ.showTime(imp, imp.getStartTime(), "", imp.getStackSize());
		imp.setStack(imp2.getStack());
		imp.setCalibration(imp2.getCalibration());
	}

	public ImagePlus shrink(ImagePlus imp, int xshrink, int yshrink, int zshrink, int method) {
		this.xshrink = xshrink;
		this.yshrink = yshrink;
		int w = imp.getWidth()/xshrink;
		int h = imp.getHeight()/yshrink;
		ColorModel cm=imp.createLut().getColorModel();
		ImageStack stack=imp.getStack();
		ImageStack stack2 = new ImageStack (w, h, cm);
		int d = stack.getSize();
		for (int z=1; z<=d; z++) {
			IJ.showProgress(z, d);
			ImageProcessor ip = stack.getProcessor(z);
			if (ip.isInvertedLut()) 
				ip.invert();
			ImageProcessor ip2 = shrink(ip, method);
			if (ip.isInvertedLut()) ip2.invert();
			stack2.addSlice(stack.getSliceLabel(z), ip2);
		}
		if (zshrink>1)
			stack2 = shrinkZ(stack2, zshrink);
		ImagePlus imp2 = (ImagePlus)imp.clone();
		imp2.setStack("Reduced "+imp.getShortTitle(), stack2);
		Calibration cal2 = imp2.getCalibration();
		if (cal2.scaled()) {
			cal2.pixelWidth *= xshrink;
			cal2.pixelHeight *= yshrink;
			cal2.pixelDepth *= zshrink;
		}
		imp2.setOpenAsHyperStack(imp.isHyperStack());
		return imp2;
	}
	
	private ImageStack shrinkZ(ImageStack stack, int zshrink) {
		int w = stack.getWidth();
		int h = stack.getHeight();
		int d = stack.getSize();
		int d2 = d/zshrink;
		ImageStack stack2 = new ImageStack (w, h, stack.getColorModel());
		for (int z=1; z<=d2; z++)
			stack2.addSlice(stack.getProcessor(z).duplicate());
		boolean rgb = stack.getBitDepth()==24;
		ImageProcessor ip = rgb?new ColorProcessor(d, h):new FloatProcessor(d, h);
		for (int x=0; x<w; x++) {
			IJ.showProgress(x+1, w);
			for (int y=0; y<h; y++) {
				float value;
				for (int z=0; z<d; z++) {
					value = (float)stack.getVoxel(x, y, z);
					ip.setf(z, y, value);
				}
			}
			ImageProcessor ip2 = shrink(ip, zshrink, 1, method);
			for (int x2=0; x2<d2; x2++) {
				for (int y2=0; y2<h; y2++) {
					stack2.setVoxel(x, y2, x2, ip2.getf(x2,y2));
				}
			}
		}
		return stack2;
	}
	
	public ImageProcessor shrink(ImageProcessor ip, int xshrink, int yshrink, int method) {
		this.xshrink = xshrink;
		this.yshrink = yshrink;
		return shrink(ip, method);
	}

	private ImageProcessor shrink(ImageProcessor ip, int method) {
		if (method<0 || method>methods.length)
			method = AVERAGE;
		int w = ip.getWidth()/xshrink;
		int h = ip.getHeight()/yshrink;
		ImageProcessor ip2 = ip.createProcessor(w, h);
		if (ip instanceof ColorProcessor)
			return shrinkRGB((ColorProcessor)ip, (ColorProcessor)ip2, method);
		for (int y=0; y<h; y++) {
			for (int x=0; x<w; x++) {
				if (method==AVERAGE)
					ip2.setf(x, y, getAverage(ip, x, y));
				else if (method==MEDIAN)
					ip2.setf(x, y, getMedian(ip, x, y));
				else if (method==MIN)
					ip2.setf(x, y, getMin(ip, x, y));
				else if (method==MAX)
					ip2.setf(x, y, getMax(ip, x, y));
			}
		}
		return ip2;
	}

	private ImageProcessor shrinkRGB(ColorProcessor cp, ColorProcessor cp2, int method) {
		ByteProcessor bp = cp.getChannel(1, null);
		cp2.setChannel(1, (ByteProcessor)shrink(bp, method));
		cp2.setChannel(2, (ByteProcessor)shrink(cp.getChannel(2,bp), method));
		cp2.setChannel(3, (ByteProcessor)shrink(cp.getChannel(3,bp), method));
		return cp2;
	}

	private float getAverage(ImageProcessor ip, int x, int y) {
		float sum = 0;
		for (int y2=0; y2<yshrink; y2++) {
			for (int x2=0;  x2<xshrink; x2++)
				sum += ip.getf(x*xshrink+x2, y*yshrink+y2); 
		}
		return (float)(sum/(xshrink*yshrink));
	}

	private float getMedian(ImageProcessor ip, int x, int y) {
		int shrinksize=xshrink*yshrink;
		float[] pixels = new float[shrinksize];
		int p=0;
		// fill pixels within local neighborhood
		for (int y2=0; y2<yshrink; y2++) {
			for (int x2=0;  x2<xshrink; x2++)
				pixels[p++]= ip.getf(x*xshrink+x2, y*yshrink+y2); 
		}
		// find median value
		int halfsize=shrinksize/2;
		for (int i=0; i<=halfsize; i++) {
			float max=0f;
			int mj=0;
			for (int j=0; j<shrinksize; j++) {
				if (pixels[j]>max) {
					max = pixels[j];
					mj = j;
				}
			}
			pixels[mj] = 0;
		}
		float max = 0f;
		for (int j=0; j<shrinksize; j++) {
			if (pixels[j]>max)
				max = pixels[j];
		}
		return max;
	}

	private float getMin(ImageProcessor ip, int x, int y) {
		float min = Float.MAX_VALUE;
		float pixel;
		for (int y2=0; y2<yshrink; y2++) {
			for (int x2=0;  x2<xshrink; x2++) {
				pixel = ip.getf(x*xshrink+x2, y*yshrink+y2); 
				if (pixel<min)
					min = pixel;
			}
		}
		return min;
	}

	private float getMax(ImageProcessor ip, int x, int y) {
		float max = 0f;
		float pixel;
		for (int y2=0; y2<yshrink; y2++) {
			for (int x2=0;  x2<xshrink; x2++) {
				pixel = ip.getf(x*xshrink+x2, y*yshrink+y2); 
				if (pixel>max)
					max = pixel;
			}
		}
		return max;
	}

	private boolean showDialog(ImagePlus imp) {
		boolean stack = imp.getStackSize()>1;
		if (imp.isHyperStack() || imp.isComposite())
			stack = false;
		GenericDialog gd = new GenericDialog("Image Shrink");
		gd.addNumericField("X shrink factor:", xshrink, 0);
		gd.addNumericField("Y shrink factor:", yshrink, 0);
		if (stack)
			gd.addNumericField("Z shrink factor:", zshrink, 0);
		if (method>methods.length)
			method = 0;
		gd.addChoice ("Bin Method: ", methods, methods[method]);
		if (imp.getStackSize()==1) {
			gd.setInsets(5, 0, 0);
			gd.addMessage("This command supports Undo");
		}
		gd.showDialog();
		if (gd.wasCanceled()) 
			return false;
		xshrink = (int) gd.getNextNumber();
		yshrink = (int) gd.getNextNumber();
		if (stack)
			zshrink = (int) gd.getNextNumber();
		method = gd.getNextChoiceIndex();
		return true;
	}

}
