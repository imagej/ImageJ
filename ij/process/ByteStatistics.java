package ij.process;
import ij.measure.Calibration;

/** 8-bit image statistics, including histogram. */
public class ByteStatistics extends ImageStatistics {

	/** Construct an ImageStatistics object from a ByteProcessor
		using the standard measurement options (area, mean,
		mode, min and max) and no calibration. */
	public ByteStatistics(ImageProcessor ip) {
		this(ip, AREA+MEAN+MODE+MIN_MAX, null);
	}

	/** Constructs a ByteStatistics object from a ByteProcessor using
		the specified measurement and calibration. */
	public ByteStatistics(ImageProcessor ip, int mOptions, Calibration cal) {
		ByteProcessor bp = (ByteProcessor)ip;
		histogram = bp.getHistogram();
		setup(ip, cal);
		double minT = ip.getMinThreshold();
		int minThreshold,maxThreshold;
		if ((mOptions&LIMIT)==0 || minT==ip.NO_THRESHOLD)
			{minThreshold=0; maxThreshold=255;}
		else
			{minThreshold=(int)minT; maxThreshold=(int)ip.getMaxThreshold();}
		float[] cTable = cal!=null?cal.getCTable():null;
		if (cTable!=null)
			getCalibratedStatistics(minThreshold,maxThreshold,cTable);
		else
			getRawStatistics(minThreshold,maxThreshold);
		if ((mOptions&MIN_MAX)!=0) {
			if (cTable!=null)
				getCalibratedMinAndMax(minThreshold, maxThreshold, cTable);
			else
				getRawMinAndMax(minThreshold, maxThreshold);
		}
		if ((mOptions&ELLIPSE)!=0)
			fitEllipse(ip);
		else if ((mOptions&CENTROID)!=0)
			getCentroid(ip, minThreshold, maxThreshold);
		if ((mOptions&CENTER_OF_MASS)!=0)
			getCenterOfMass(ip, minThreshold, maxThreshold, cTable);
	}

	void getCalibratedStatistics(int minThreshold, int maxThreshold, float[] cTable) {
		int count;
		double value;
		double sum = 0;
		double sum2 = 0.0;
		int isum = 0;
		
		for (int i=minThreshold; i<=maxThreshold; i++) {
			count = histogram[i];
			pixelCount += count;
			value = cTable[i];
			sum += value*count;
			isum += i*count;
			sum2 += (value*value)*count;
			if (count>maxCount) {
				maxCount = count;
				mode = i;
			}
		}
		area = pixelCount*pw*ph;
		mean = sum/pixelCount;
		umean = (double)isum/pixelCount;
		dmode = cTable[mode];
		calculateStdDev(pixelCount,sum,sum2);
		histMin = 0.0;
		histMax = 255.0;
	}
	
	void getCentroid(ImageProcessor ip, int minThreshold, int maxThreshold) {
		byte[] pixels = (byte[])ip.getPixels();
		int[] mask = ip.getMask();
		boolean limit = minThreshold>0 || maxThreshold<255;
		int count=0, xsum=0, ysum=0,i,mi,v;
		for (int y=ry,my=0; y<(ry+rh); y++,my++) {
			i = y*width + rx;
			mi = my*rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null||mask[mi++]==ip.BLACK) {
					if (limit) {
						v = pixels[i]&255;
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

	void getCenterOfMass(ImageProcessor ip,  int minThreshold, int maxThreshold, float[] cTable) {
		byte[] pixels = (byte[])ip.getPixels();
		int[] mask = ip.getMask();
		int v, i, mi;
		double dv, count=0.0, xsum=0.0, ysum=0.0;
		for (int y=ry,my=0; y<(ry+rh); y++,my++) {
			i = y*width + rx;
			mi = my*rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null || mask[mi++]==ip.BLACK) {
					v = pixels[i]&255;
					if (v>=minThreshold&&v<=maxThreshold) {
						dv = ((cTable!=null)?cTable[v]:v)+Double.MIN_VALUE;
						count += dv;
						xsum += x*dv;
						ysum += y*dv;
					}
				}
				i++;
			}
		}
		xCenterOfMass = (xsum/count+0.5)*pw;
		yCenterOfMass = (ysum/count+0.5)*ph;
	}
	
	void getCalibratedMinAndMax(int minThreshold, int maxThreshold, float[] cTable) {
		min = Double.MAX_VALUE;
		max = -Double.MAX_VALUE;
		double v = 0.0;
		for (int i=minThreshold; i<=maxThreshold; i++) {
			if (histogram[i]>0) {
				v = cTable[i];
				if (v<min) min = v;
				if (v>max) max = v;
			}
		}
	}
	
}