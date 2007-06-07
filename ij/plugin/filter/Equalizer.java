package ij.plugin.filter;
import ij.*;
import ij.process.*;
import java.awt.*;

/** Implements ImageJ's Equalize command. It changes the tone curves of 
images. It should bring up the detail in the flat regions of your image.
Histogram Equalization can enhance meaningless detail and hide 
important but small high-contrast features. This plugin uses a
similar algorithm, but uses the square root of the histogram 
values, so its effects are less extreme. Hold the alt key down, or set
'arg' to "classic", to use the standard histogram equalization algorithm.
This code was contributed by Richard Kirk (rak@cre.canon.co.uk).
*/
public class Equalizer implements PlugInFilter {

	ImagePlus imp;
	int[] histogram;
	int max, range;
	boolean classicEqualization = IJ.altKeyDown();

	/** Set 'arg' to "classic" to use the standard histogram equalization algorithm. */
	public int setup(String arg, ImagePlus imp) {
		if (imp==null)
			return DONE;
		this.imp = imp;
		ImageProcessor ip = imp.getProcessor();
		histogram = ip.getHistogram();
		imp.killRoi();
		if (arg.equals("classic"))
			classicEqualization = true;
		return IJ.setupDialog(imp, DOES_8G+DOES_RGB+DOES_16);
	}

	private double getWeightedValue(int i) {
		int h = histogram[i];
		if (h<2 || classicEqualization) return (double)h;
		return Math.sqrt((double)(h));
	}
	
	public void run(ImageProcessor ip) {
	
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

}
