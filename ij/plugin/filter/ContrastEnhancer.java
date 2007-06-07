package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.Calibration;
import java.awt.*;

/** Implements ImageJ's Process/Enhance Contrast command. */
public class ContrastEnhancer implements PlugInFilter {

	ImagePlus imp;
	int[] histogram;
	int max, range;
	int slice;
	boolean classicEqualization = IJ.altKeyDown();
	boolean canceled;
	
	static boolean equalize;
	static boolean normalize;
	static double saturated = 0.5;

	/** Set 'arg' to "classic" to use the standard histogram equalization algorithm. */
	public int setup(String arg, ImagePlus imp) {
		if (imp==null)
			return DONE;
		this.imp = imp;
		ImageProcessor ip = imp.getProcessor();
		histogram = ip.getHistogram();
		imp.killRoi();
		return IJ.setupDialog(imp, DOES_8G+DOES_RGB+DOES_16);
	}

	public void run(ImageProcessor ip) {
		slice++;
		if (slice==1)
			showDialog();
		if (canceled)
			return;
		if (equalize)
			equalize(ip);
		else
			stretchHistogram(imp, ip, saturated);
	}

	void showDialog() {
		GenericDialog gd = new GenericDialog("Enhance Contrast");
		gd.addNumericField("Saturated Pixels:", saturated, 1, 4, "%");
		gd.addCheckbox("Normalize", normalize);
		gd.addCheckbox("Equalize Histogram", equalize);
		gd.showDialog();
		if (gd.wasCanceled()) {
			canceled = true;
			return;
		}
		saturated = gd.getNextNumber();
		normalize = gd.getNextBoolean();
		equalize = gd.getNextBoolean();
		if (saturated<0.0) saturated = 0.0;
		if (saturated>100.0) saturated = 100;
	}
 
	public void stretchHistogram(ImagePlus imp, ImageProcessor ip, double saturated) {
		Calibration cal = imp.getCalibration();
		imp.setCalibration(null);
		ImageStatistics stats = imp.getStatistics(); // get uncalibrated stats
		imp.setCalibration(cal);
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
			found = count>=threshold;
		} while (!found && i<255);
		if (i > 0)
			hmin = i-1;
		else
			hmin = 0;
				
		i = 256;
		count = 0;
		do {
			i--;
			count += histogram[i];
			found = count>=threshold;
		} while (!found && i>0);
		if (i < 255)
			hmax = i+1;
		else
			hmax = 255;
				
		//IJ.log(hmin+" "+hmax+" "+threshold);
		if (hmax>hmin) {
			imp.killRoi();
			double min = stats.histMin+hmin*stats.binSize;
			double max = stats.histMin+hmax*stats.binSize;
			ip.setMinAndMax(min, max);
		}
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
	
		if (histogram==null)
			return;
	
		if (ip instanceof ShortProcessor) {	// Short
			max = 65535;
			range = 65535;
		} else { //bytes
			max = 255;
			range = 255;
		}
		
		double sum;
		
		sum = getWeightedValue(0);
		for (int i=1; i<max; i++)
			sum += 2 * getWeightedValue(i);
		sum += getWeightedValue(max);
		
		double scale = range/sum;
		int[] lut = new int[range+1];
		
		lut[0] = 0;
		sum = getWeightedValue(0);
		for (int i=1; i<max; i++) {
			double delta = getWeightedValue(i);
			sum += delta;
			lut[i] = (int)Math.round(sum*scale);
			sum += delta;
		}
		lut[max] = max;
		
		ip.applyTable(lut);
	}

	private double getWeightedValue(int i) {
		int h = histogram[i];
		if (h<2 || classicEqualization) return (double)h;
		return Math.sqrt((double)(h));
	}
	
}
