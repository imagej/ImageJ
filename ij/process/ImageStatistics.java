package ij.process;
import ij.measure.*;
import java.awt.*;

/** Statistics, including the histogram, of an image or image roi. */
public class ImageStatistics implements Measurements {

	public int[] histogram;
	public int pixelCount;
	public int mode;
	public double dmode;
	public double area;
	public double min;
	public double max;
	public double mean;
	public double stdDev;
	public double xCentroid;
	public double yCentroid;
	public double xCenterOfMass;
	public double yCenterOfMass;
	public double roiX, roiY, roiWidth, roiHeight;
	/** Uncalibrated mean */
	public double umean;
	/** Length of major axis of fitted ellipse */
	public double major;
	/** Length of minor axis of fitted ellipse */
	public double minor;
	/** Angle in degrees of fitted ellipse */
	public double angle;
	
	public double histMin;
	public double histMax;
	public int maxCount;
	public int nBins = 256;
	public double binSize = 1.0;
	
	protected int width, height;
	protected int rx, ry, rw, rh;
	protected double pw, ph;
	
	EllipseFitter ef;

	
	public static ImageStatistics getStatistics(ImageProcessor ip, int mOptions, Calibration cal) {
		if (ip instanceof ByteProcessor)
			return new ByteStatistics(ip, mOptions, cal);
		else if (ip instanceof ShortProcessor)
			return new ShortStatistics(ip, mOptions, cal);
		else if (ip instanceof ColorProcessor)
			return new ColorStatistics(ip, mOptions, cal);
		else
			return new FloatStatistics(ip, mOptions, cal);
	}

	void getRawMinAndMax(int minThreshold, int maxThreshold) {
		int min = minThreshold;
		while ((histogram[min] == 0) && (min < 255))
			min++;
		this.min = min;
			
		int max = maxThreshold;
		while ((histogram[max] == 0) && (max > 0))
			max--;
		this.max = max;
	}

	void getRawStatistics(int minThreshold, int maxThreshold) {
		int count;
		double value;
		double sum = 0.0;
		double sum2 = 0.0;
		
		for (int i=minThreshold; i<=maxThreshold; i++) {
			count = histogram[i];
			pixelCount += count;
			sum += i*count;
			value = i;
			sum2 += (value*value)*count;
			if (count>maxCount) {
				maxCount = count;
				mode = i;
			}
		}
		area = pixelCount*pw*ph;
		mean = sum/pixelCount;
		umean = mean;
		dmode = mode;
		calculateStdDev(pixelCount, sum, sum2);
		histMin = 0.0;
		histMax = 255.0;
	}
	
	void calculateStdDev(int n, double sum, double sum2) {
		//ij.IJ.write("calculateStdDev: "+n+" "+sum+" "+sum2);
		if (n>0) {
			stdDev = (n*sum2-sum*sum)/n;
			if (stdDev>0.0)
				stdDev = Math.sqrt(stdDev/(n-1.0));
			else
				stdDev = 0.0;
		}
		else
			stdDev = 0.0;
	}
		
	void setup(ImageProcessor ip, Calibration cal) {
		width = ip.getWidth();
		height = ip.getHeight();
		Rectangle roi = ip.getRoi();
		if (roi != null) {
			rx = roi.x;
			ry = roi.y;
			rw = roi.width;
			rh = roi.height;
		}
		else {
			rx = 0;
			ry = 0;
			rw = width;
			rh = height;
		}
		
		if (cal!=null) {
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
		} else {
			pw = 1.0;
			ph = 1.0;
		}
		
		roiX = rx*pw;
		roiY = ry*ph;
		roiWidth = rw*pw;
		roiHeight = rh*ph;
	}
	
	void getCentroid(ImageProcessor ip) {
		byte[] mask = ip.getMaskArray();
		int count=0, xsum=0, ysum=0,mi;
		for (int y=ry,my=0; y<(ry+rh); y++,my++) {
			mi = my*rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null||mask[mi++]!=0) {
					count++;
					xsum+=x;
					ysum+=y;
				}
			}
		}
		xCentroid = ((double)xsum/count+0.5)*pw;
		yCentroid = ((double)ysum/count+0.5)*ph;
	}
	
	void fitEllipse(ImageProcessor ip) {
		if (ef==null)
			ef = new EllipseFitter();
		ef.fit(ip, this);
		double psize = (Math.abs(pw-ph)/pw)<.01?pw:0.0;
		major = ef.major*psize;
		minor = ef.minor*psize;
		angle = ef.angle;
		xCentroid = ef.xCenter*pw;
		yCentroid = ef.yCenter*ph;
		//if (ij.IJ.altKeyDown())
		//	ef.drawEllipse(ip);
	}
	
	public void drawEllipse(ImageProcessor ip) {
		if (ef!=null)
			ef.drawEllipse(ip);
	}
	
}