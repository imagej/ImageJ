package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import java.awt.*;

/** Implements ImageJ's Process/Enhance Contrast command. */
public class ContrastEnhancer implements PlugIn, Measurements {
	static final double defaultSaturated = 0.35;
	static double gSaturated = defaultSaturated;
	static boolean gEqualize;
	double saturated = defaultSaturated;
	int max, range;
	boolean classicEqualization;
	int stackSize;
	boolean updateSelectionOnly;
	boolean equalize, normalize, processStack, useStackHistogram, entireImage;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		stackSize = imp.getStackSize();
		imp.trimProcessor();
		if (!showDialog(imp))
			return;
		Roi roi = imp.getRoi();
		if (roi!=null) roi.endPaste();
		if (stackSize==1)
			Undo.setup(Undo.TRANSFORM, imp);
		else
			Undo.reset();
		if (equalize)
			equalize(imp);
		else
			stretchHistogram(imp, saturated);
		if (normalize) {
			ImageProcessor ip = imp.getProcessor();
			ip.setMinAndMax(0,ip.getBitDepth()==32?1.0:ip.maxValue());
		}
		imp.updateAndDraw();
	}

	boolean showDialog(ImagePlus imp) {
		String options = IJ.isMacro()?Macro.getOptions():null;
		if (options!=null && options.contains("normalize_all"))
			Macro.setOptions(options.replaceAll("normalize_all", "process_all"));
		boolean isMacro = options!=null;
		if (!isMacro) {
			equalize = gEqualize;
			saturated = gSaturated;
		}
		int bitDepth = imp.getBitDepth();
		boolean composite = imp.isComposite();
		if (composite) stackSize = 1;
		Roi roi = imp.getRoi();
		boolean areaRoi = roi!=null && roi.isArea() && !composite;
		GenericDialog gd = new GenericDialog("Enhance Contrast");
		gd.addNumericField("Saturated pixels:", saturated, 2, 5, "%");
		if (bitDepth!=24 && !composite)
			gd.addCheckbox("Normalize", normalize);
		if (areaRoi) {
			String label = bitDepth==24?"Update entire image":"Update all when normalizing";
			gd.addCheckbox(label, entireImage);
		}
		gd.addCheckbox("Equalize histogram", equalize);
		if (stackSize>1) {
			if (!composite)
				gd.addCheckbox("Process_all "+stackSize+" slices", processStack);
			gd.addCheckbox("Use stack histogram", useStackHistogram);
		}
        gd.addHelp(IJ.URL+"/docs/menus/process.html#enhance");
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		saturated = gd.getNextNumber();
		if (bitDepth!=24 && !composite)
			normalize = gd.getNextBoolean();
		else
			normalize = false;
		if (areaRoi) {
			entireImage = gd.getNextBoolean();
			updateSelectionOnly = !entireImage;
			if (!normalize && bitDepth!=24) 
				updateSelectionOnly = false;
		}
		equalize = gd.getNextBoolean();
		processStack = stackSize>1?gd.getNextBoolean():false;
		useStackHistogram = stackSize>1?gd.getNextBoolean():false;
		if (saturated<0.0) saturated = 0.0;
		if (saturated>100.0) saturated = 100;
		if (processStack && !equalize)
			normalize = true;
		if (!isMacro) {
			gEqualize = equalize;
			gSaturated = saturated;
		}
		return true;
	}
 
	public void stretchHistogram(ImagePlus imp, double saturated) {
		ImageStatistics stats = null;
		if (useStackHistogram)
			stats = new StackStatistics(imp);
		if (processStack) {
			ImageStack stack = imp.getStack();
			int size = this.stackSize==0?stack.size():this.stackSize;
			for (int i=1; i<=size; i++) {
				IJ.showProgress(i, size);
				ImageProcessor ip = stack.getProcessor(i);
				ip.setRoi(imp.getRoi());
				if (!useStackHistogram)
					stats = ImageStatistics.getStatistics(ip, MIN_MAX, null);
				stretchHistogram(ip, saturated, stats);
			}
		} else {
			ImageProcessor ip = imp.getProcessor();
			ip.setRoi(imp.getRoi());
			if (stats==null)
				stats = ImageStatistics.getStatistics(ip, MIN_MAX, null);
			if (imp.isComposite())
				stretchCompositeImageHistogram((CompositeImage)imp, saturated, stats);
			else
				stretchHistogram(ip, saturated, stats);
		}
	}
	
	public void stretchHistogram(ImageProcessor ip, double saturated) {
		useStackHistogram = false;
		stretchHistogram(new ImagePlus("", ip), saturated);
	}

	public void stretchHistogram(ImageProcessor ip, double saturated, ImageStatistics stats) {
		int[] a = getMinAndMax(ip, saturated, stats);
		int hmin=a[0], hmax=a[1];
		if (hmax>hmin) {
			double min = stats.histMin+hmin*stats.binSize;
			double max = stats.histMin+hmax*stats.binSize;
			if (stats.histogram16!=null && ip instanceof ShortProcessor) {
				min = hmin;
				max = hmax;
			}
			if (!updateSelectionOnly)
				ip.resetRoi();
			if (normalize)
				normalize(ip, min, max);
			else {
				if (updateSelectionOnly) {
					ImageProcessor mask = ip.getMask();
					if (mask!=null) ip.snapshot();
					ip.setMinAndMax(min, max);
					if (mask!=null) ip.reset(mask);
				} else
					ip.setMinAndMax(min, max);
			}
		}
	}
	
	void stretchCompositeImageHistogram(CompositeImage imp, double saturated, ImageStatistics stats) {
		ImageProcessor ip = imp.getProcessor();
		int[] a = getMinAndMax(ip, saturated, stats);
		int hmin=a[0], hmax=a[1];
		if (hmax>hmin) {
			double min = stats.histMin+hmin*stats.binSize;
			double max = stats.histMin+hmax*stats.binSize;
			if (stats.histogram16!=null && imp.getBitDepth()==16) {
				min = hmin;
				max = hmax;
			}
			imp.setDisplayRange(min, max);
		}
	}

	int[] getMinAndMax(ImageProcessor ip, double saturated, ImageStatistics stats) {
		int hmin, hmax;
		int threshold;
		int[] histogram = stats.histogram;
		if (stats.histogram16!=null && ip instanceof ShortProcessor)
			histogram = stats.histogram16;
		int hsize = histogram.length;
		if (saturated>0.0)
			threshold = (int)(stats.pixelCount*saturated/200.0);
		else
			threshold = 0;
		int i = -1;
		boolean found = false;
		int count = 0;
		int maxindex = hsize-1;
		do {
			i++;
			count += histogram[i];
			found = count>threshold;
		} while (!found && i<maxindex);
		hmin = i;
				
		i = hsize;
		count = 0;
		do {
			i--;
			count += histogram[i];
			found = count>threshold;
		} while (!found && i>0);
		hmax = i;
		int[] a = new int[2];
		a[0]=hmin; a[1]=hmax;
		return a;
	}
	
	void normalize(ImageProcessor ip, double min, double max) {
		int min2 = 0;
		int max2 = 255;
		int range = 256;
		if (ip instanceof ShortProcessor)
			{max2 = 65535; range=65536;}
		else if (ip instanceof FloatProcessor)
			normalizeFloat(ip, min, max);
		int[] lut = new int[range];
		for (int i=0; i<range; i++) {
			if (i<=min)
				lut[i] = 0;
			else if (i>=max)
				lut[i] = max2;
			else
				lut[i] = (int)(((double)(i-min)/(max-min))*max2);
		}
		applyTable(ip, lut);
	}
	
	void applyTable(ImageProcessor ip, int[] lut) {
		if (updateSelectionOnly) {
			ImageProcessor mask = ip.getMask();
			if (mask!=null) ip.snapshot();
				ip.applyTable(lut);
			if (mask!=null) ip.reset(mask);
		} else
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
		int[] histogram = null;
		if (useStackHistogram) {
			ImageStatistics stats = new StackStatistics(imp);
			histogram = stats.histogram;
			if (stats.histogram16!=null && imp.getBitDepth()==16)
				histogram = stats.histogram16;
		}
		if (processStack) {
			ImageStack stack = imp.getStack();
			int size = this.stackSize==0?stack.size():this.stackSize;
			for (int i=1; i<=size; i++) {
				IJ.showProgress(i, size);
				ImageProcessor ip = stack.getProcessor(i);
				if (histogram==null)
					histogram = ip.getHistogram();
				equalize(ip, histogram);
			}
		} else {
			ImageProcessor ip = imp.getProcessor();
			if (histogram==null)
				histogram = ip.getHistogram();
			equalize(ip, histogram);
		}
		if (imp.getBitDepth()==16 && processStack && imp.getStackSize()>1) {
			ImageStack stack = imp.getStack();
			ImageProcessor ip = stack.getProcessor(stack.size()/2);
			ImageStatistics stats = ip.getStats();
			imp.getProcessor().setMinAndMax(stats.min, stats.max);
		} else
			imp.getProcessor().resetMinAndMax();
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
		equalize(ip, ip.getHistogram());
	}

	private void equalize(ImageProcessor ip, int[] histogram) {
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
		applyTable(ip, lut);
	}

	private double getWeightedValue(int[] histogram, int i) {
		int h = histogram[i];
		if (h<2 || classicEqualization) return (double)h;
		return Math.sqrt((double)(h));
	}
	
	public void setNormalize(boolean normalize) {
		this.normalize = normalize;
	}
	
	public void setProcessStack(boolean processStack) {
		this.processStack = processStack;
		this.normalize = true;
	}

	public void setUseStackHistogram(boolean useStackHistogram) {
		this.useStackHistogram = useStackHistogram;
	}

}
