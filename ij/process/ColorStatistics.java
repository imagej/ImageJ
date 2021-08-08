package ij.process;
import ij.measure.Calibration;

/** RGB image statistics, including histogram. */
public class ColorStatistics extends ImageStatistics {

	/** Construct an ImageStatistics object from a ColorProcessor
		using the standard measurement options (area, mean,
		mode, min and max). */
	public ColorStatistics(ImageProcessor ip) {
		this(ip, AREA+MEAN+MODE+MIN_MAX, null);
	}

	/** Constructs a ColorStatistics object from a ColorProcessor using
		the specified measurement options.
	*/
	public ColorStatistics(ImageProcessor ip, int mOptions, Calibration cal) {
		setup(ip, cal);
		if (ip instanceof IntProcessor) {
			getIntStatistics(ip);
			return;
		}
		ColorProcessor cp = (ColorProcessor)ip;
		histogram = cp.getHistogram();
		getRawStatistics(0,255);
		if ((mOptions&MIN_MAX)!=0)
			getRawMinAndMax(0,255);
		if ((mOptions&ELLIPSE)!=0 || (mOptions&SHAPE_DESCRIPTORS)!=0)
			fitEllipse(ip, mOptions);
		else if ((mOptions&CENTROID)!=0)
			getCentroid(ip);
		if ((mOptions&(CENTER_OF_MASS|SKEWNESS|KURTOSIS))!=0)
			calculateMoments(ip);
		if ((mOptions&MEDIAN)!=0)
			calculateMedian(histogram, 0, 255, cal);
	}

	void calculateMoments(ImageProcessor ip) {
		byte[] mask = ip.getMaskArray();
		int i, mi;
		double v, v2, sum1=0.0, sum2=0.0, sum3=0.0, sum4=0.0, xsum=0.0, ysum=0.0;
		for (int y=ry,my=0; y<(ry+rh); y++,my++) {
			i = y*width + rx;
			mi = my*rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null || mask[mi++]!=0) {
					v = ip.getPixelValue(x, y);
						v2 = v*v;
						sum1 += v;
						sum2 += v2;
						sum3 += v*v2;
						sum4 += v2*v2;
						xsum += x*v;
						ysum += y*v;
				}
				i++;
			}
		}
	    double mean2 = mean*mean;
	    double variance = sum2/pixelCount - mean2;
	    double sDeviation = Math.sqrt(variance);
	    skewness = ((sum3 - 3.0*mean*sum2)/pixelCount + 2.0*mean*mean2)/(variance*sDeviation);
	    kurtosis = (((sum4 - 4.0*mean*sum3 + 6.0*mean2*sum2)/pixelCount - 3.0*mean2*mean2)/(variance*variance)-3.0);
		xCenterOfMass = xsum/sum1+0.5;
		yCenterOfMass = ysum/sum1+0.5;
		if (cal!=null) {
			xCenterOfMass = cal.getX(xCenterOfMass);
			yCenterOfMass = cal.getY(yCenterOfMass, height);
		}
	}
	
	void getIntStatistics(ImageProcessor ip) {
		int v;
		int[] pixels = (int[])ip.getPixels();
		nBins = ip.getHistogramSize();
		histogram = new int[nBins];
		double sum = 0;
		double sum2 = 0;
		byte[] mask = ip.getMaskArray();
		
		// Find image min and max
		int roiMin = Integer.MAX_VALUE;
		int roiMax = -Integer.MAX_VALUE;
		for (int y=ry, my=0; y<(ry+rh); y++, my++) {
			int i = y * width + rx;
			int mi = my * rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null || mask[mi++]!=0) {
					v = pixels[i];
					if (v<roiMin)
						roiMin = v;
					else if (v>roiMax)
						roiMax = v;
				}
				i++;
			}
		}
		min = roiMin; max = roiMax;
		binSize = (max-min)/nBins;
		histMin = min; 
		histMax = max;

		// Generate histogram
		double scale = nBins/(max-min);
		int index;
		pixelCount = 0;
		for (int y=ry, my=0; y<(ry+rh); y++, my++) {
			int i = y * width + rx;
			int mi = my * rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null || mask[mi++]!=0) {
					v = pixels[i];
					pixelCount++;
					sum += v;
					sum2 += v*v;
					index = (int)(scale*(v-min));
					if (index>=nBins)
						index = nBins-1;
					histogram[index]++;
				}
				i++;
			}
		}
		area = pixelCount*pw*ph;
		mean = sum/pixelCount;
		umean = mean;
		calculateStdDev(pixelCount, sum, sum2);
		
        // calculate mode
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
        if (binSize!=1.0)
        	dmode += binSize/2.0;        	
	}

}
