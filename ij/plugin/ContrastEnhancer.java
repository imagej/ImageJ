package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import java.awt.*;

/** Implements ImageJ's Process/Enhance Contrast command. */
public class ContrastEnhancer implements PlugIn, Measurements {

	int max, range;
	boolean classicEqualization;
	int stackSize;
	
	static boolean equalize;
	static boolean normalize;
	static boolean processStack;
	static double saturated = 0.5;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		stackSize = imp.getStackSize();
		imp.trimProcessor();
		if (!showDialog(imp))
			return;
		if (stackSize==1)
			Undo.setup(Undo.TRANSFORM, imp);
		else
			Undo.reset();
		if (equalize)
			equalize(imp);
		else
			stretchHistogram(imp, saturated);
		if (equalize || normalize)
			imp.getProcessor().resetMinAndMax();
		imp.updateAndDraw();
	}

	boolean showDialog(ImagePlus imp) {
		int bitDepth = imp.getBitDepth();
		GenericDialog gd = new GenericDialog("Enhance Contrast");
		gd.addNumericField("Saturated Pixels:", saturated, 1, 4, "%");
		if (bitDepth!=24)
			gd.addCheckbox("Normalize", normalize);
		gd.addCheckbox("Equalize Histogram", equalize);
		if (stackSize>1)
			gd.addCheckbox("Process Entire Stack", processStack);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		saturated = gd.getNextNumber();
		if (bitDepth!=24)
			normalize = gd.getNextBoolean();
		else
			normalize = false;		
		equalize = gd.getNextBoolean();
		processStack = stackSize>1?gd.getNextBoolean():false;
		if (saturated<0.0) saturated = 0.0;
		if (saturated>100.0) saturated = 100;
		if (processStack)
			normalize = true;
		return true;
	}
 
	public void stretchHistogram(ImagePlus imp, double saturated) {
		if (processStack) {
			ImageStack stack = imp.getStack();
			for (int i=1; i<=stackSize; i++) {
				IJ.showProgress(i, stackSize);
				ImageProcessor ip = stack.getProcessor(i);
				stretchHistogram(ip, saturated);
			}
		} else
			stretchHistogram(imp.getProcessor(), saturated);
	}

	public void stretchHistogram(ImageProcessor ip, double saturated) {
		ImageStatistics stats = ImageStatistics.getStatistics(ip, MIN_MAX, null);
		int hmin, hmax;
		int threshold;
		int[] histogram = stats.histogram;		
		if (saturated>0.0)
			threshold = (int)(stats.pixelCount*saturated/200.0);
		else
			threshold = 0;
		int i = -1;
		boolean found = false;
		int count = 0;
		do {
			i++;
			count += histogram[i];
			found = count>threshold;
		} while (!found && i<255);
		hmin = i;
				
		i = 256;
		count = 0;
		do {
			i--;
			count += histogram[i];
			found = count>threshold;
			//IJ.log(i+" "+count+" "+found);
		} while (!found && i>0);
		hmax = i;
				
		//IJ.log(hmin+" "+hmax+" "+threshold);
		if (hmax>hmin) {
			double min = stats.histMin+hmin*stats.binSize;
			double max = stats.histMin+hmax*stats.binSize;
			if (normalize)
				normalize(ip, min, max);
			else
				ip.setMinAndMax(min, max);
		}
	}
	
	void normalize(ImageProcessor ip, double min, double max) {
		int min2 = 0;
		int max2 = 255;
		int range = 256;
		if (ip instanceof ShortProcessor)
			{max2 = 65535; range=65536;}
		else if (ip instanceof FloatProcessor)
			normalizeFloat(ip, min, max);
		
		//double scale = range/max-min);
		int[] lut = new int[range];
		for (int i=0; i<range; i++) {
			if (i<=min)
				lut[i] = 0;
			else if (i>=max)
				lut[i] = max2;
			else
				lut[i] = (int)(((double)(i-min)/(max-min))*max2);
		}
		ip.applyTable(lut);
	}

	void normalizeFloat(ImageProcessor ip, double min, double max) {
		double scale = max>min?1.0/(max-min):1.0;
		int size = ip.getWidth()*ip.getHeight();
		float[] pixels = (float[])ip.getPixels();
		double v;
		for (int i=0; i<size; i++) {
			v = pixels[i] - min;
			if (v<0.0) v = 0.0;
			v *= scale;
			if (v>1.0) v = 1.0;
			pixels[i] = (float)v;
		
		}
	}

	public void equalize(ImagePlus imp) {
		if (imp.getBitDepth()==32) {
			IJ.showMessage("Contrast Enhancer", "Equalization of 32-bit images not supported.");
			return;
		}
		classicEqualization = IJ.altKeyDown();
		if (processStack) {
			//int[] mask = imp.getMask();
			//Rectangle rect = imp.get
			ImageStack stack = imp.getStack();
			for (int i=1; i<=stackSize; i++) {
				IJ.showProgress(i, stackSize);
				ImageProcessor ip = stack.getProcessor(i);
				equalize(ip);
			}
		} else
			equalize(imp.getProcessor());
	}

	/**	
		Changes the tone curves of images. 
		It should bring up the detail in the flat regions of your image.
		Histogram Equalization can enhance meaningless detail and hide 
		important but small high-contrast features. This method uses a
		similar algorithm, but uses the square root of the histogram 
		values, so its effects are less extreme. Hold the alt key down 
		to use the standard histogram equalization algorithm.
		This code was contributed by Richard Kirk (rak@cre.canon.co.uk).
	*/ 	
	public void equalize(ImageProcessor ip) {
	
		int[] histogram = ip.getHistogram();
		ip.resetRoi();
		if (ip instanceof ShortProcessor) {	// Short
			max = 65535;
			range = 65535;
		} else { //bytes
			max = 255;
			range = 255;
		}
		
		double sum;
		
		sum = getWeightedValue(histogram, 0);
		for (int i=1; i<max; i++)
			sum += 2 * getWeightedValue(histogram, i);
		sum += getWeightedValue(histogram, max);
		
		double scale = range/sum;
		int[] lut = new int[range+1];
		
		lut[0] = 0;
		sum = getWeightedValue(histogram, 0);
		for (int i=1; i<max; i++) {
			double delta = getWeightedValue(histogram, i);
			sum += delta;
			lut[i] = (int)Math.round(sum*scale);
			sum += delta;
		}
		lut[max] = max;
		
		ip.applyTable(lut);
	}

	private double getWeightedValue(int[] histogram, int i) {
		int h = histogram[i];
		if (h<2 || classicEqualization) return (double)h;
		return Math.sqrt((double)(h));
	}
	
}
