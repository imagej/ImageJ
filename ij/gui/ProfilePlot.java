package ij.gui;

import java.awt.*;
import ij.*;
import ij.process.*;
import ij.util.*;
import ij.measure.*;

/** Creates a density profile plot of a rectangular selection or line selection. */
public class ProfilePlot {

	static final int MIN_WIDTH = 350;
	static final double ASPECT_RATIO = 0.5;
	private double min, max;
	private boolean minAndMaxCalculated;
    private static double fixedMin = Prefs.getDouble("pp.min",0.0);
    private static double fixedMax = Prefs.getDouble("pp.max",0.0);
    
	protected ImagePlus imp;
	protected double[] profile;
	protected double magnification;
	protected double pixelSize;
	protected String units;
	protected String yLabel;
	
	public ProfilePlot() {
	}

	public ProfilePlot(ImagePlus imp) {
		this(imp, false);
	}

	public ProfilePlot(ImagePlus imp, boolean averageHorizontally) {
		this.imp = imp;
		Roi roi = imp.getRoi();
		if (roi==null) {
			IJ.error("Selection required.");
			return;
		}
		int roiType = roi.getType();
		if (!(roiType==Roi.LINE || roiType==Roi.POLYLINE || roiType==Roi.FREELINE || roiType==Roi.RECTANGLE)) {
			IJ.error("Line or rectangular selection required.");
			return;
		}
		Calibration cal = imp.getCalibration();
		pixelSize = cal.pixelWidth;
		units = cal.getUnits();
		yLabel = cal.getValueUnit();
		ImageProcessor ip = imp.getProcessor();
		ip.setCalibrationTable(cal.getCTable());
		if (roiType==Roi.LINE) {
			ip.setInterpolate(true);
			profile = ((Line)roi).getPixels();
		} else if (roiType==Roi.POLYLINE || roiType==Roi.FREELINE)
			profile = getIrregularProfile(roi, ip);
		else if (averageHorizontally)
			profile = getRowAverageProfile(roi.getBounds(), cal, ip);
		else
			profile = getColumnAverageProfile(roi.getBounds(), ip);
		ip.setCalibrationTable(null);
		ImageWindow win = imp.getWindow();
		if (win!=null)
			magnification = win.getCanvas().getMagnification();
		else
			magnification = 1.0;
	}

	//void calibrate(Calibration cal) {
	//	float[] cTable = cal.getCTable();
	//	if (cTable!=null)
	//		for ()
	//			profile[i] = profile[i];
	//	
	//}
	
	/** Returns the size of the plot that createWindow() creates. */
	public Dimension getPlotSize() {
		if (profile==null) return null;
		int width = (int)(profile.length*magnification);
		int height = (int)(width*ASPECT_RATIO);
		if (width<MIN_WIDTH) {
			width = MIN_WIDTH;
			height = (int)(width*ASPECT_RATIO);
		}
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int maxWidth = Math.min(screen.width-200, 1000);
		if (width>maxWidth) {
			width = maxWidth;
			height = (int)(width*ASPECT_RATIO);
		}
		return new Dimension(width, height);
	}
	
	/** Displays this profile plot in a window. */
	public void createWindow() {
		if (profile==null)
			return;
		Dimension d = getPlotSize();
		String xLabel = "Distance ("+units+")";
  		int n = profile.length;
   		float[] xValues = new float[n];
        for (int i=0; i<n; i++)
        	xValues[i] = (float)(i*pixelSize);
        float[] yValues = new float[n];
        for (int i=0; i<n; i++)
        	yValues[i] = (float)profile[i];
		boolean fixedYScale = fixedMin!=0.0 || fixedMax!=0.0;
		Plot plot = new Plot("Plot of "+imp.getShortTitle(), xLabel, yLabel, xValues, yValues);
		if (fixedYScale) {
			double[] a = Tools.getMinMax(xValues);
			plot.setLimits(a[0],a[1],fixedMin,fixedMax);
		}
		plot.show();
	}
	
	/** Returns the profile plot data. */
	public double[] getProfile() {
		return profile;
	}
	
	/** Returns the calculated minimum value. */
	public double getMin() {
		if (!minAndMaxCalculated)
			findMinAndMax();
		return min;
	}
	
	/** Returns the calculated maximum value. */
	public double getMax() {
		if (!minAndMaxCalculated)
			findMinAndMax();
		return max;
	}
	
	/** Sets the y-axis min and max. Specify (0,0) to autoscale. */
	public static void setMinAndMax(double min, double max) {
		fixedMin = min;
		fixedMax = max;
		IJ.register(ProfilePlot.class);
	}
	
	/** Returns the profile plot y-axis min. Auto-scaling is used if min=max=0. */
	public static double getFixedMin() {
		return fixedMin;
	}
	
	/** Returns the profile plot y-axis max. Auto-scaling is used if min=max=0. */
	public static double getFixedMax() {
		return fixedMax;
	}
	
	double[] getRowAverageProfile(Rectangle rect, Calibration cal, ImageProcessor ip) {
		double[] profile = new double[rect.height];
		double[] aLine;
		
		ip.setInterpolate(false);
		for (int x=rect.x; x<rect.x+rect.width; x++) {
			aLine = ip.getLine(x, rect.y, x, rect.y+rect.height-1);
			for (int i=0; i<rect.height; i++)
				profile[i] += aLine[i];
		}
		for (int i=0; i<rect.height; i++)
			profile[i] /= rect.width;
		if (cal!=null)
			pixelSize = cal.pixelHeight;
		return profile;
	}
	
	double[] getColumnAverageProfile(Rectangle rect, ImageProcessor ip) {
		double[] profile = new double[rect.width];
		double[] aLine;
		
		ip.setInterpolate(false);
		for (int y=rect.y; y<rect.y+rect.height; y++) {
			aLine = ip.getLine(rect.x, y, rect.x+rect.width-1, y);
			for (int i=0; i<rect.width; i++)
				profile[i] += aLine[i];
		}
		for (int i=0; i<rect.width; i++)
			profile[i] /= rect.height;
		return profile;
	}	
	
	double[] getIrregularProfile(Roi roi, ImageProcessor ip) {
		int n = ((PolygonRoi)roi).getNCoordinates();
		int[] x = ((PolygonRoi)roi).getXCoordinates();
		int[] y = ((PolygonRoi)roi).getYCoordinates();
		Rectangle r = roi.getBounds();
		int xbase = r.x;
		int ybase = r.y;
		double length = 0.0;
		double segmentLength;
		int xdelta, ydelta, iLength;
		double[] segmentLengths = new double[n];
		int[] dx = new int[n];
		int[] dy = new int[n];
		for (int i=0; i<(n-1); i++) {
			xdelta = x[i+1] - x[i];
			ydelta = y[i+1] - y[i];
			segmentLength = Math.sqrt(xdelta*xdelta+ydelta*ydelta);
			length += segmentLength;
			segmentLengths[i] = segmentLength;
			dx[i] = xdelta;
			dy[i] = ydelta;
		}
		double[] values = new double[(int)length];
		double leftOver = 1.0;
		double distance = 0.0;
		int index;
		//double oldx=xbase, oldy=ybase;
		for (int i=0; i<n; i++) {
			double len = segmentLengths[i];
			if (len==0.0)
				continue;
			double xinc = dx[i]/len;
			double yinc = dy[i]/len;
			double start = 1.0-leftOver;
			double rx = xbase+x[i]+start*xinc;
			double ry = ybase+y[i]+start*yinc;
			double len2 = len - start;
			int n2 = (int)len2;
			//double d=0;;
			//IJ.write("new segment: "+IJ.d2s(xinc)+" "+IJ.d2s(yinc)+" "+IJ.d2s(len)+" "+IJ.d2s(len2)+" "+IJ.d2s(n2)+" "+IJ.d2s(leftOver));
			for (int j=0; j<=n2; j++) {
				index = (int)distance+j;
				if (index<values.length)
					values[index] = ip.getInterpolatedValue(rx, ry);
				//d = Math.sqrt((rx-oldx)*(rx-oldx)+(ry-oldy)*(ry-oldy));
				//IJ.write(IJ.d2s(rx)+"    "+IJ.d2s(ry)+"    "+IJ.d2s(d));
				//IJ.log(IJ.d2s(rx)+"    "+IJ.d2s(ry));
				//oldx = rx; oldy = ry;
				rx += xinc;
				ry += yinc;
			}
			distance += len;
			leftOver = len2 - n2;
		}

		return values;

	}

	private double[] getLineSegment(ImageProcessor ip, double x1, double y1, double x2, double y2) {
		double dx = x2-x1;
		double dy = y2-y1;
		int n = (int)Math.round(Math.sqrt(dx*dx + dy*dy));
		double xinc = dx/n;
		double yinc = dy/n;
		n++;
		double[] data = new double[n];
		double rx = x1;
		double ry = y1;
		for (int i=0; i<n; i++) {
			data[i] = ip.getInterpolatedValue(rx, ry);
			rx += xinc;
			ry += yinc;
		}
		return data;
	}
	

	void findMinAndMax() {
		if (profile==null) return;
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		double value;
		for (int i=0; i<profile.length; i++) {
			value = profile[i];
			if (value<min) min=value;
			if (value>max) max=value;
		}
		this.min = min;
		this.max = max;
	}
	

}