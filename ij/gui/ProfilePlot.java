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
	protected double[] profile;
	private double magnification;
	private double min, max;
	private boolean minAndMaxCalculated;
	private ImagePlus imp;
    static private double fixedMin = Prefs.getDouble("pp.min",0.0);
    static private double fixedMax = Prefs.getDouble("pp.max",0.0);
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
		if (roiType==Roi.LINE)
			profile = ((Line)roi).getPixels();
		else if (roiType==Roi.POLYLINE || roiType==Roi.FREELINE)
			profile = getIrregularProfile(roi, ip);
		else if (averageHorizontally)
			profile = getRowAverageProfile(roi.getBoundingRect(), cal, ip);
		else
			profile = getColumnAverageProfile(roi.getBoundingRect(), ip);
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
		PlotWindow pw = new PlotWindow("Plot of "+imp.getShortTitle(), xLabel, yLabel, xValues, yValues);
		if (fixedYScale) {
			double[] a = Tools.getMinMax(xValues);
			pw.setLimits(a[0],a[1],fixedMin,fixedMax);
		}
		pw.draw();
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
		Rectangle r = roi.getBoundingRect();
		int xbase = r.x;
		int ybase = r.y;
		int index = 0;
		double length = 0.0;
		double segmentLength;
		int xdelta, ydelta, iLength;
		double[] segmentLengths = new double[n];
		for (int i=0; i<(n-1); i++) {
			xdelta = x[i+1] - x[i];
			ydelta = y[i+1] - y[i];
			segmentLength = Math.sqrt(xdelta*xdelta+ydelta*ydelta);
			length += segmentLength;
			segmentLengths[i] = segmentLength;
		}
		double[] values = new double[(int)length+1];
		double[] segmentValues;
		length = 0.0;
		for (int i=0; i<(n-1); i++) {
			segmentValues = ip.getLine(x[i]+xbase, y[i]+ybase, x[i+1]+xbase, y[i+1]+ybase);
			for (int j=0; j<segmentValues.length; j++)
				values[index+j] = segmentValues[j];
			length += segmentLengths[i];
			index = (int)length;
		}
		return values;
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