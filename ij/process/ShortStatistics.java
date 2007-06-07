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
		double minT = ip.getMinThreshold();
		int minThreshold,maxThreshold;
		if ((mOptions&LIMIT)==0 || minT==ip.NO_THRESHOLD)
			{minThreshold=0; maxThreshold=65535;}
		else
			{minThreshold=(int)minT; maxThreshold=(int)ip.getMaxThreshold();}
		int[] hist = ip.getHistogram(); // 65536 bin histogram
		float[] cTable = cal!=null?cal.getCTable():null;
		getRawMinAndMax(hist, minThreshold, maxThreshold);
		histMin = min;
		histMax = max;
		getStatistics(hist, (int)min, (int)max, cTable);
		if ((mOptions&MODE)!=0)
			getMode();
		if ((mOptions&ELLIPSE)!=0)
			fitEllipse(ip);
		else if ((mOptions&CENTROID)!=0)
			getCentroid(ip, minThreshold, maxThreshold);
		if ((mOptions&CENTER_OF_MASS)!=0)
			getCenterOfMass(ip, minThreshold, maxThreshold);
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
		binSize = (histMax-histMin)/nBins;
		double scale = 1.0/binSize;
		int hMin = (int)histMin;
		histogram = new int[nBins]; // 256 bin histogram
		int index;
        int maxCount = 0;
				
		for (int i=min; i<=max; i++) {
			count = hist[i];
            if (count>maxCount) {
                maxCount = count;
                dmode = i;
            }
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
        if (cTable!=null)
        	dmode = cTable[(int)dmode];
	}
	
	void getMode() {
        int count;
        maxCount = 0;
        for (int i=0; i<nBins; i++) {
        	count = histogram[i];
            if (count > maxCount) {
                maxCount = count;
                mode = i;
            }
        }
		//ij.IJ.write("mode2: "+mode+" "+dmode+" "+maxCount);
	}


	void getCentroid(ImageProcessor ip, int minThreshold, int maxThreshold) {
		short[] pixels = (short[])ip.getPixels();
		byte[] mask = ip.getMaskArray();
		boolean limit = minThreshold>0 || maxThreshold<65535;
		int count=0, xsum=0, ysum=0,i,mi,v;
		for (int y=ry,my=0; y<(ry+rh); y++,my++) {
			i = y*width + rx;
			mi = my*rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null||mask[mi++]!=0) {
					if (limit) {
						v = pixels[i]&0xffff;
						if (v>=minThreshold&&v<=maxThreshold) {
							count++;
							xsum+=x;
							ysum+=y;
						}
					} else {
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

	void getCenterOfMass(ImageProcessor ip,  int minThreshold, int maxThreshold) {
		short[] pixels = (short[])ip.getPixels();
		byte[] mask = ip.getMaskArray();
		int i, mi, v;
		double dv, count=0.0, xsum=0.0, ysum=0.0;
		for (int y=ry,my=0; y<(ry+rh); y++,my++) {
			i = y*width + rx;
			mi = my*rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null || mask[mi++]!=0) {
					v = pixels[i]&0xffff;
					if (v>=minThreshold&&v<=maxThreshold) {
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