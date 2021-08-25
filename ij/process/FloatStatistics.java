package ij.process;
import ij.measure.Calibration;
import java.util.Arrays;

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
		boolean limitToThreshold = (mOptions&LIMIT)!=0;
		if (!limitToThreshold || minT==ImageProcessor.NO_THRESHOLD) {
			minThreshold=-Float.MAX_VALUE;
			maxThreshold=Float.MAX_VALUE;
		} else {
			minThreshold=minT;
			maxThreshold=ip.getMaxThreshold();
		}
		if (limitToThreshold)
			saveThreshold(minThreshold, maxThreshold, cal);
		getStatistics(ip, minThreshold, maxThreshold);
		if ((mOptions&MODE)!=0)
			getMode();
		if ((mOptions&ELLIPSE)!=0 || (mOptions&SHAPE_DESCRIPTORS)!=0)
			fitEllipse(ip, mOptions);
		else if ((mOptions&CENTROID)!=0)
			getCentroid(ip, minThreshold, maxThreshold);
		if ((mOptions&(CENTER_OF_MASS|SKEWNESS|KURTOSIS))!=0)
			calculateMoments(ip, minThreshold, maxThreshold);
		if ((mOptions&MEDIAN)!=0)
			getMedian(ip, minThreshold, maxThreshold);
		if ((mOptions&AREA_FRACTION)!=0)
			calculateAreaFraction(ip);
	}

	void getStatistics(ImageProcessor ip, double minThreshold, double maxThreshold) {
		double v;
		float[] pixels = (float[])ip.getPixels();
		nBins = ip.getHistogramSize();
		histMin = ip.getHistogramMin();
		histMax = ip.getHistogramMax();
		histogram = new int[nBins];
		double sum = 0;
		double sum2 = 0;
		byte[] mask = ip.getMaskArray();
		
		// Find image min and max
		double roiMin = Double.MAX_VALUE;
		double roiMax = -Double.MAX_VALUE;
		for (int y=ry, my=0; y<(ry+rh); y++, my++) {
			int i = y * width + rx;
			int mi = my * rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null || mask[mi++]!=0) {
					v = pixels[i];
					if (v>=minThreshold && v<=maxThreshold) {
						if (v<roiMin)
							roiMin = v;
						if (v>roiMax)
							roiMax = v;
					}
				}
				i++;
			}
		}
		min = roiMin; max = roiMax;
		if (histMin==0.0 && histMax==0.0) {
			histMin = min; 
			histMax = max;
		} else {
			if (min<histMin)
				min = histMin;
			if (max>histMax)
				max = histMax;
		}
		binSize = (histMax-histMin)/nBins;

		// Generate histogram
		double scale = nBins/(histMax-histMin);
		int index;
		pixelCount = 0;
		for (int y=ry, my=0; y<(ry+rh); y++, my++) {
			int i = y * width + rx;
			int mi = my * rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null || mask[mi++]!=0) {
					v = pixels[i];
					if (v>=minThreshold && v<=maxThreshold && v>=histMin && v<=histMax) {
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
		umean = mean;
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
        if (binSize!=1.0)
        	dmode += binSize/2.0;        	
	}

	void calculateMoments(ImageProcessor ip, double minThreshold, double maxThreshold) {
		float[] pixels = (float[])ip.getPixels();
		byte[] mask = ip.getMaskArray();
		int i, mi;
		double v, v2, sum1=0.0, sum2=0.0, sum3=0.0, sum4=0.0, xsum=0.0, ysum=0.0;
		for (int y=ry,my=0; y<(ry+rh); y++,my++) {
			i = y*width + rx;
			mi = my*rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null || mask[mi++]!=0) {
					v = pixels[i]+Double.MIN_VALUE;
					if (v>=minThreshold && v<=maxThreshold) {
						v2 = v*v;
						sum1 += v;
						sum2 += v2;
						sum3 += v*v2;
						sum4 += v2*v2;
						xsum += x*v;
						ysum += y*v;
					}
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

	void getCentroid(ImageProcessor ip, double minThreshold, double maxThreshold) {
		float[] pixels = (float[])ip.getPixels();
		byte[] mask = ip.getMaskArray();
		double count=0.0, xsum=0.0, ysum=0.0, v;
		int i, mi;
		for (int y=ry,my=0; y<(ry+rh); y++,my++) {
			i = y*width + rx;
			mi = my*rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null||mask[mi++]!=0) {
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
		xCentroid = xsum/count+0.5;
		yCentroid = ysum/count+0.5;
		if (cal!=null) {
			xCentroid = cal.getX(xCentroid);
			yCentroid = cal.getY(yCentroid, height);
		}
	}

	void calculateAreaFraction(ImageProcessor ip) {
		int sum = 0;
		int total = 0;
		float t1 = (float)ip.getMinThreshold();
		float t2 = (float)ip.getMaxThreshold();
		float v;
		float[] pixels = (float[])ip.getPixels();
		boolean noThresh = t1==ImageProcessor.NO_THRESHOLD;
		byte[] mask = ip.getMaskArray();
		int i, mi;
		for (int y=ry,my=0; y<(ry+rh); y++,my++) {
			i = y*width + rx;
			mi = my*rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null||mask[mi++]!=0) {
					v = pixels[i];
					total++;
					if (noThresh) {
						if (v!=0f) sum++;
					} else if (v>=t1 && v<=t2)
						sum++;
				}
				i++;
			}
		}
		areaFraction = sum*100.0/total;
	}
	
	void getMedian(ImageProcessor ip, double minThreshold, double maxThreshold) {
		if (pixelCount==0) {
			median = Double.NaN;
			return;
		}
		float[] pixels = (float[])ip.getPixels();
		float[] pixels2 = new float[pixelCount];
		byte[] mask = ip.getMaskArray();
		int i, mi;
		float v;
		int count = 0;
		for (int y=ry,my=0; y<(ry+rh); y++,my++) {
			i = y*width + rx;
			mi = my*rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null||mask[mi++]!=0) {
					v = pixels[i];
					if (v>=minThreshold && v<=maxThreshold) {
						if (count==pixels2.length) {
							median = Double.NaN;
							return;
						}
						pixels2[count++] = v;
					}
				}
				i++;
			}
		}
		Arrays.sort(pixels2);
		int middle = pixels2.length/2;
		if ((pixels2.length&1)==0) //even
			median = (pixels2[middle-1] + pixels2[middle])/2f;
		else
			median = pixels2[middle];
	}

}
