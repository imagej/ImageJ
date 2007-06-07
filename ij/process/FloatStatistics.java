package ij.process;
import ij.measure.Calibration;

/** 32-bit (float) image statistics, including histogram. */
public class FloatStatistics extends ImageStatistics {

	/** Constructs an ImageStatistics object from a FloatProcessor
		using the standard measurement options (area, mean,
		mode, min and max). */
	public FloatStatistics(ImageProcessor ip) {
		this(ip, AREA+MEAN+MODE+MIN_MAX, null);
	}

	/** Constructs a FloatStatistics object from a FloatProcessor
		using the specified measurement options.
	*/
	public FloatStatistics(ImageProcessor ip, int mOptions, Calibration cal) {
		this.width = ip.getWidth();
		this.height = ip.getHeight();
		setup(ip, cal);
		getStatistics(ip);
		if ((mOptions&MODE)!=0)
			getMode();
		if ((mOptions&ELLIPSE)!=0)
			fitEllipse(ip);
		else if ((mOptions&CENTROID)!=0)
			getCentroid(ip);
		if ((mOptions&CENTER_OF_MASS)!=0)
			getCenterOfMass(ip);
	}

	void getStatistics(ImageProcessor ip) {
		double v;
		float[] pixels = (float[])ip.getPixels();
		nBins = ip.getHistogramSize();
		histogram = new int[nBins];
		double sum = 0;
		double sum2 = 0;
		int[] mask = ip.getMask();
		
		// Find min and max
		double roiMin = Double.MAX_VALUE;
		double roiMax = -Double.MAX_VALUE;
		if (mask!=null)
			// non-rectangular roi
			for (int y=ry, my=0; y<(ry+rh); y++, my++) {
				int i = y * width + rx;
				int mi = my * rw;
				for (int x=rx; x<(rx+rw); x++) {
					if (mask[mi++]==ip.BLACK) {
						v = pixels[i];
						if (v<roiMin) roiMin = v;
						if (v>roiMax) roiMax = v;
					}
					i++;
				}
			}
		else
			// rectangular roi or no roi
			for (int y=ry; y<(ry+rh); y++) {
				int i = y * width + rx;
				for (int x=rx; x<(rx+rw); x++) {
					v = pixels[i++];
					if (v<roiMin) roiMin = v;
					if (v>roiMax) roiMax = v;
				}
			}
		min = roiMin; max = roiMax;
		histMin = min; histMax = max;
		binSize = (histMax-histMin)/nBins;

		// Generate histogram
		double scale = nBins/(histMax-histMin);
		int index;
		pixelCount = 0;
		for (int y=ry, my=0; y<(ry+rh); y++, my++) {
			int i = y * width + rx;
			int mi = my * rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null || mask[mi++]==ip.BLACK) {
					v = pixels[i];
					pixelCount++;
					sum += v;
					sum2 += v*v;
					index = (int)(scale*(v-histMin));
					if (index>=nBins)
						index = nBins-1;
					histogram[index]++;
				}
				i++;
			}
		}
		min = roiMin; max = roiMax;
		area = pixelCount*pw*ph;
		mean = sum/pixelCount;
		calculateStdDev(pixelCount, sum, sum2);
	}

	void getMode() {
        int count;
        maxCount = 0;
        for (int i = 0; i < nBins; i++) {
        	count = histogram[i];
            if (count > maxCount) {
                maxCount = count;
                mode = i;
            }
        }
        dmode = histMin+mode*binSize;
	}

	void getCenterOfMass(ImageProcessor ip) {
		float[] pixels = (float[])ip.getPixels();
		int[] mask = ip.getMask();
		int i, mi;
		double v, count=0.0, xsum=0.0, ysum=0.0;
		for (int y=ry,my=0; y<(ry+rh); y++,my++) {
			i = y*width + rx;
			mi = my*rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null || mask[mi++]==ip.BLACK) {
					v = pixels[i]+Double.MIN_VALUE;
					count += v;
					xsum += x*v;
					ysum += y*v;
				}
				i++;
			}
		}
		xCenterOfMass = (xsum/count+0.5)*pw;
		yCenterOfMass = (ysum/count+0.5)*ph;
	}

}