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
		double minT = ip.getMinThreshold();
		double minThreshold,maxThreshold;
		if ((mOptions&LIMIT)==0 || minT==ip.NO_THRESHOLD)
			{minThreshold=-Float.MAX_VALUE; maxThreshold=Float.MAX_VALUE;}
		else
			{minThreshold=minT; maxThreshold=ip.getMaxThreshold();}
		getStatistics(ip, minThreshold, maxThreshold);
		if ((mOptions&MODE)!=0)
			getMode();
		if ((mOptions&ELLIPSE)!=0)
			fitEllipse(ip);
		else if ((mOptions&CENTROID)!=0)
			getCentroid(ip, minThreshold, maxThreshold);
		if ((mOptions&CENTER_OF_MASS)!=0)
			getCenterOfMass(ip, minThreshold, maxThreshold);
	}

	void getStatistics(ImageProcessor ip, double minThreshold, double maxThreshold) {
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
		double roiMin2 = Double.MAX_VALUE;
		double roiMax2 = -Double.MAX_VALUE;
		for (int y=ry, my=0; y<(ry+rh); y++, my++) {
			int i = y * width + rx;
			int mi = my * rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null || mask[mi++]==ip.BLACK) {
					v = pixels[i];
					if (v>=minThreshold && v<=maxThreshold) {
						if (v<roiMin) roiMin = v;
						if (v>roiMax) roiMax = v;
					}
				}
				i++;
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
					if (v>=minThreshold && v<=maxThreshold) {
						pixelCount++;
						sum += v;
						sum2 += v*v;
						index = (int)(scale*(v-histMin));
						if (index>=nBins)
							index = nBins-1;
						histogram[index]++;
					}
				}
				i++;
			}
		}
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

	void getCenterOfMass(ImageProcessor ip, double minThreshold, double maxThreshold) {
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
					if (v>=minThreshold && v<=maxThreshold) {
						count += v;
						xsum += x*v;
						ysum += y*v;
					}
				}
				i++;
			}
		}
		xCenterOfMass = (xsum/count+0.5)*pw;
		yCenterOfMass = (ysum/count+0.5)*ph;
	}

	void getCentroid(ImageProcessor ip, double minThreshold, double maxThreshold) {
		float[] pixels = (float[])ip.getPixels();
		int[] mask = ip.getMask();
		double count=0.0, xsum=0.0, ysum=0.0, v;
		int i, mi;
		for (int y=ry,my=0; y<(ry+rh); y++,my++) {
			i = y*width + rx;
			mi = my*rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null||mask[mi++]==ip.BLACK) {
					v = pixels[i];
					if (v>=minThreshold && v<=maxThreshold) {
						count++;
						xsum+=x;
						ysum+=y;
					}
				}
				i++;
			}
		}
		xCentroid = ((double)xsum/count+0.5)*pw;
		yCentroid = ((double)ysum/count+0.5)*ph;
	}

}