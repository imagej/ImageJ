package ij.gui;

import java.awt.*;
import java.util.ArrayList;
import ij.*;
import ij.process.*;
import ij.util.*;
import ij.measure.*;
import ij.plugin.Straightener;

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
	protected double xInc;
	protected String units;
	protected String yLabel;
	protected float[] xValues;

	
	public ProfilePlot() {
	}

	public ProfilePlot(ImagePlus imp) {
		this(imp, false);
	}

	public ProfilePlot(ImagePlus imp, boolean averageHorizontally) {
		this.imp = imp;
		Roi roi = imp.getRoi();
		if (roi==null) {
			IJ.error("Profile Plot", "Selection required.");
			return;
		}
		int roiType = roi.getType();
		if (!(roi.isLine() || roiType==Roi.RECTANGLE)) {
			IJ.error("Line or rectangular selection required.");
			return;
		}
		Calibration cal = imp.getCalibration();
		xInc = cal.pixelWidth;
		units = cal.getUnits();
		yLabel = cal.getValueUnit();
		ImageProcessor ip = imp.getProcessor();
		//ip.setCalibrationTable(cal.getCTable());
		if (roiType==Roi.LINE)
			profile = getStraightLineProfile(roi, cal, ip);
		else if (roiType==Roi.POLYLINE || roiType==Roi.FREELINE) {
			int lineWidth = (int)Math.round(roi.getStrokeWidth());
			if (lineWidth<=1)
				profile = getIrregularProfile(roi, ip, cal);
			else
				profile = getWideLineProfile(imp, lineWidth);
		} else if (averageHorizontally)
			profile = getRowAverageProfile(roi.getBounds(), cal, ip);
		else
			profile = getColumnAverageProfile(roi.getBounds(), ip);
		ip.setCalibrationTable(null);
		ImageCanvas ic = imp.getCanvas();
		if (ic!=null)
			magnification = ic.getMagnification();
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
		Dimension screen = IJ.getScreenSize();
		int maxWidth = Math.min(screen.width-200, 1000);
		if (width>maxWidth) {
			width = maxWidth;
			height = (int)(width*ASPECT_RATIO);
		}
		return new Dimension(width, height);
	}
	
	/** Displays this profile plot in a window. */
	public void createWindow() {
		Plot plot = getPlot();
		if (plot==null) return;
		plot.setSourceImageID(imp.getID());
		plot.show();
	}
	
	Plot getPlot() {
		if (profile==null)
			return null;
		Dimension d = getPlotSize();
		String xLabel = "Distance ("+units+")";
  		int n = profile.length;
  		if (xValues==null) {
			xValues = new float[n];
			for (int i=0; i<n; i++)
				xValues[i] = (float)(i*xInc);
		}
        float[] yValues = new float[n];
        for (int i=0; i<n; i++)
        	yValues[i] = (float)profile[i];
		boolean fixedYScale = fixedMin!=0.0 || fixedMax!=0.0;
		Plot plot = new Plot("Plot of "+getShortTitle(imp), xLabel, yLabel, xValues, yValues);
		if (fixedYScale) {
			double[] a = Tools.getMinMax(xValues);
			plot.setLimits(a[0],a[1],fixedMin,fixedMax);
		}
		return plot;
	}
	
	String getShortTitle(ImagePlus imp) {
		String title = imp.getTitle();
		int index = title.lastIndexOf('.');
		if (index>0 && (title.length()-index)<=5)
			title = title.substring(0, index);
		return title;
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
	
	double[] getStraightLineProfile(Roi roi, Calibration cal, ImageProcessor ip) {
			ip.setInterpolate(PlotWindow.interpolate);
			Line line = (Line)roi;
			double[] values = line.getPixels();
			if (values==null) return null;
			if (cal!=null && cal.pixelWidth!=cal.pixelHeight) {
				double dx = cal.pixelWidth*(line.x2 - line.x1);
				double dy = cal.pixelHeight*(line.y2 - line.y1);
				double length = Math.round(Math.sqrt(dx*dx + dy*dy));
				if (values.length>1)
					xInc = length/(values.length-1);
			}
			return values;
	}

	double[] getRowAverageProfile(Rectangle rect, Calibration cal, ImageProcessor ip) {
		double[] profile = new double[rect.height];
		int[] counts = new int[rect.height];
		double[] aLine;
		ip.setInterpolate(false);
		for (int x=rect.x; x<rect.x+rect.width; x++) {
			aLine = ip.getLine(x, rect.y, x, rect.y+rect.height-1);
			for (int i=0; i<rect.height; i++) {
				if (!Double.isNaN(aLine[i])) {
					profile[i] += aLine[i];
					counts[i]++;
				}
			}
		}
		for (int i=0; i<rect.height; i++)
			profile[i] /= counts[i];
		if (cal!=null)
			xInc = cal.pixelHeight;
		return profile;
	}
	
	double[] getColumnAverageProfile(Rectangle rect, ImageProcessor ip) {
		double[] profile = new double[rect.width];
		int[] counts = new int[rect.width];
		double[] aLine;
		ip.setInterpolate(false);
		for (int y=rect.y; y<rect.y+rect.height; y++) {
			aLine = ip.getLine(rect.x, y, rect.x+rect.width-1, y);
			for (int i=0; i<rect.width; i++) {
				if (!Double.isNaN(aLine[i])) {
					profile[i] += aLine[i];
					counts[i]++;
				}
			}
		}
		for (int i=0; i<rect.width; i++)
			profile[i] /= counts[i];
		return profile;
	}	

	double[] getIrregularProfile(Roi roi, ImageProcessor ip, Calibration cal) {
		boolean interpolate = PlotWindow.interpolate;
		boolean calcXValues = cal!=null && cal.pixelWidth!=cal.pixelHeight;
		FloatPolygon p = roi.getFloatPolygon();
		int n = p.npoints;
		float[] xpoints = p.xpoints;
		float[] ypoints = p.ypoints;
		ArrayList values = new ArrayList();
		int n2;
		double inc = 0.01;
		double distance=0.0, distance2=0.0, dx=0.0, dy=0.0, xinc, yinc;
		double x, y, lastx=0.0, lasty=0.0, x1, y1, x2=xpoints[0], y2=ypoints[0];
		double value;
		for (int i=1; i<n; i++) {
			x1=x2; y1=y2;
			x=x1; y=y1;
			x2=xpoints[i]; y2=ypoints[i];
			dx = x2-x1;
			dy = y2-y1;
			distance = Math.sqrt(dx*dx+dy*dy);
			xinc = dx*inc/distance;
			yinc = dy*inc/distance;
			//n2 = (int)(dx/xinc);
			n2 = (int)(distance/inc);
			if (n==2) n2++;
			do {
				dx = x-lastx;
				dy = y-lasty;
				distance2 = Math.sqrt(dx*dx+dy*dy);
				//IJ.log(i+"   "+IJ.d2s(xinc,5)+"   "+IJ.d2s(yinc,5)+"   "+IJ.d2s(distance,2)+"   "+IJ.d2s(distance2,2)+"   "+IJ.d2s(x,2)+"   "+IJ.d2s(y,2)+"   "+IJ.d2s(lastx,2)+"   "+IJ.d2s(lasty,2)+"   "+n+"   "+n2);
				if (distance2>=1.0-inc/2.0) {
					if (interpolate)
						value = ip.getInterpolatedValue(x, y);
					else
						value = ip.getPixelValue((int)Math.round(x), (int)Math.round(y));
					values.add(new Double(value));
					lastx=x; lasty=y;
				}
				x += xinc;
				y += yinc;
			} while (--n2>0);
		}
		double[] values2 = new double[values.size()];
		for (int i=0; i<values.size(); i++)
			values2[i] = ((Double)values.get(i)).doubleValue();
		return values2;
	}

	double[] getWideLineProfile(ImagePlus imp, int lineWidth) {
		Roi roi = (Roi)imp.getRoi().clone();
		ImageProcessor ip2 = (new Straightener()).straightenLine(imp, lineWidth);
		int width = ip2.getWidth();
		int height = ip2.getHeight();
		profile = new double[width];
		double[] aLine;
		ip2.setInterpolate(false);
		for (int y=0; y<height; y++) {
			aLine = ip2.getLine(0, y, width-1, y);
			for (int i=0; i<width; i++)
				profile[i] += aLine[i];
		}
		for (int i=0; i<width; i++)
			profile[i] /= height;
		imp.setRoi(roi);
		if (roi.getType()==Roi.POLYLINE&& !((PolygonRoi)roi).isSplineFit()) {
			((PolygonRoi)roi).fitSpline();
			imp.draw();
		}
		return profile;
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
