package ij.process;
import ij.measure.Calibration;

/** 16-bit image statistics, including histogram. */
public class ShortStatistics extends ImageStatistics {

	/** Construct an ImageStatistics object from a ShortProcessor
		using the standard measurement options (area, mean,
		mode, min and max). */
	public ShortStatistics(ImageProcessor ip) {
		this(ip, AREA+MEAN+MODE+MIN_MAX, null);
	}

	/** Constructs a ShortStatistics object from a ShortProcessor using
		the specified measurement options. The 'cal' argument, which
		can be null, is currently ignored. */
	public ShortStatistics(ImageProcessor ip, int mOptions, Calibration cal) {
		this.width = ip.getWidth();
		this.height = ip.getHeight();
		setup(ip, cal);
		nBins = 256;
		int minThreshold,maxThreshold;
		minThreshold=0;
		maxThreshold=65535;
		int[] hist = ip.getHistogram(); // 65536 bin histogram
		float[] cTable = cal!=null?cal.getCTable():null;
		getRawMinAndMax(hist, minThreshold, maxThreshold);
		histMin = min;
		histMax = max;
		getStatistics(hist, (int)min, (int)max, cTable);
		if ((mOptions&MODE)!=0)
			getMode(cTable);
		if ((mOptions&ELLIPSE)!=0)
			fitEllipse(ip);
		else if ((mOptions&CENTROID)!=0)
			getCentroid(ip);
		if ((mOptions&CENTER_OF_MASS)!=0)
			getCenterOfMass(ip, cTable);
		if ((mOptions&MIN_MAX)!=0 && cTable!=null) {
			getCalibratedMinAndMax(hist, (int)min, (int)max, cTable);
		}
	}

	void getRawMinAndMax(int[] hist, int minThreshold, int maxThreshold) {
		int min = minThreshold;
		while ((hist[min]==0) && (min<65535))
			min++;
		this.min = min;
			
		int max = maxThreshold;
		while ((hist[max]==0) && (max>0))
			max--;
		this.max = max;
	}

	void getStatistics(int[] hist, int min, int max, float[] cTable) {
		int count;
		double value;
		double sum = 0.0;
		double sum2 = 0.0;
		double scale = (double)(nBins/(histMax-histMin));
		int hMin = (int)histMin;
		binSize = (histMax-histMin)/nBins;
		histogram = new int[nBins]; // 256 bin histogram
		int index;
				
		for (int i=min; i<=max; i++) {
			count = hist[i];
			pixelCount += count;
			value = cTable==null?i:cTable[i];
			sum += value*count;
			sum2 += (value*value)*count;
			index = (int)(scale*(i-hMin));
			if (index>=nBins)
				index = nBins-1;
			histogram[index] += count;
		}
		area = pixelCount*pw*ph;
		mean = sum/pixelCount;
		umean = mean;
		calculateStdDev(pixelCount, sum, sum2);
	}
	
	void getMode(float[] cTable) {
        int count;
        maxCount = 0;
        for (int i=0; i<nBins; i++) {
        	count = histogram[i];
            if (count > maxCount) {
                maxCount = count;
                mode = i;
            }
        }
        dmode = histMin+mode*binSize;
        if (cTable!=null)
        	dmode = cTable[(int)dmode];
		//ij.IJ.write("mode2: "+mode+" "+dmode+" "+maxCount);
	}


	void getCenterOfMass(ImageProcessor ip, float[] cTable) {
		short[] pixels = (short[])ip.getPixels();
		int[] mask = ip.getMask();
		int i, mi, v;
		double dv, count=0.0, xsum=0.0, ysum=0.0;
		for (int y=ry,my=0; y<(ry+rh); y++,my++) {
			i = y*width + rx;
			mi = my*rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null || mask[mi++]==ip.BLACK) {
					v = pixels[i]&0xffff;
					dv = ((cTable!=null)?cTable[v]:v)+Double.MIN_VALUE;
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

	void getCalibratedMinAndMax(int[] hist, int minValue, int maxValue, float[] cTable) {
		min = Double.MAX_VALUE;
		max = -Double.MAX_VALUE;
		double v = 0.0;
		for (int i=minValue; i<=maxValue; i++) {
			if (hist[i]>0) {
				v = cTable[i];
				if (v<min) min = v;
				if (v>max) max = v;
			}
		}
	}

}