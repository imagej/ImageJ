package ij.plugin.filter;  //##
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.filter.ParticleAnalyzer;
import ij.measure.*;
import ij.util.*;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;


/**
 *  Implements ImageJ's Analyze/Tools/Analyze Line Graph command.
 * 
 *  2022-07-19 Michael Schmid: 	New implementation, tries to follow the curves.
 * 								Plots (and lists) different curves as separate data sets.
 * 								Works on all image types (for RGB, uses gray value and threshold at 127.5)
 */
public class LineGraphAnalyzer implements PlugInFilter, Measurements  {
	static final int MAX_EXTRAPOLATE = 10; 	//maximum extrapolation in x (pixels) to find the same curve
	static final int MAX_Y_JUMP = 10; 		//maximum jump in y (pixels) for continuation of the curve
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL|NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		analyze(imp);
	}
	
	/** Uses extracts a set of coordinate pairs from a digitized line graph.
	 *  Assumes the graph is thresholded or dark on white.
	 *  Only analyzes pixels inside the ROI */
	public void analyze(ImagePlus imp) {
		Calibration cal = imp.getCalibration();
		boolean invertedLut = imp.isInvertedLut();
		Roi roi = imp.getRoi();
		ImageProcessor ip = imp.getProcessor();
		Rectangle rect = ip.getRoi();
		int height = ip.getHeight();
		double minThreshold = ip.getMinThreshold();
		double maxThreshold = ip.getMaxThreshold();
		if (minThreshold == ImageProcessor.NO_THRESHOLD) {	//if we have no threshold, assume a bright background and 50% threshold
			double midValue = cal.getCValue(0.5*(ip.getMin() + ip.getMax()));
			minThreshold = invertedLut ? midValue : -Float.MAX_VALUE;
			maxThreshold = invertedLut ? Float.MAX_VALUE : midValue;
		}
		int xStart = -1;		// all arrays will begin with x=xStart (there is nothing at lower x)
		FloatArray xData = new FloatArray(rect.width);
		ArrayList<FloatArray> yDataAll = new ArrayList<FloatArray>(10);  //will contain the data for each curve
		FloatArray yValuesLo = new FloatArray(10);			//foreground y ranges for current x
		FloatArray yValuesHi = new FloatArray(10);
		FloatArray curveValuesLo = new FloatArray(10);		//last y range for each curve
		FloatArray curveValuesHi = new FloatArray(10);
		Extrapolator extrapolator = new Extrapolator();
		for (int x=rect.x; x < rect.x+rect.width; x++) {
			boolean lastIsForeground = false;
			for (int y=rect.y; y<rect.y+rect.height; y++) {  //transverse vertically at fixed x
				boolean isForeground = false;
				if (roi == null || roi.contains(x,y)) {
					float value = ip.getPixelValue(x, y);
					isForeground = value >= minThreshold && value <= maxThreshold;
				}
				if (isForeground) {
					if (!lastIsForeground) {				//we have reached a line
						yValuesLo.add(y);
						lastIsForeground = true;
						if (xStart < 0) xStart = x;
					}
				} else if (lastIsForeground) {
					yValuesHi.add(y-1);						//we have stepped over the line
					lastIsForeground = false;
				}
			}
			if (yValuesLo.size() > yValuesHi.size())
				yValuesHi.add(rect.y+rect.height-1);

			if (xStart >= 0) {
				float xScaled = (float)cal.getX(x);
				xData.add(xScaled);
			}
			if (yValuesLo.size() > 0) {
				double[] missing = new double[yDataAll.size()];
				for (int n=0; n<yDataAll.size(); n++) {		//count recent data points of all curves (first try extending curves with many points)
					FloatArray arr = yDataAll.get(n);
					for (int ix = x-xStart-1; ix >= 0 && ix >= x-xStart-MAX_EXTRAPOLATE; ix--) {
						if (ix >= arr.size() || Float.isNaN(arr.get(ix)))
							missing[n]++;
					}
				}
				int[] ranks = Tools.rank(missing);
				for (int i=0; i<ranks.length; i++) {		//for curves with many recent points first,
					int n = ranks[i];						//try to find a continuation of the curve n (using the current y values)
					double yExtrapolated = extrapolated(yDataAll.get(n), x-xStart, extrapolator);
					double minDistance = Double.MAX_VALUE;
					int jOfMinDist = -1;
					for (int j=0; j<yValuesLo.size(); j++) {
						float yValue = 0.5f*(yValuesLo.get(j) + yValuesHi.get(j));
						double distance = Math.abs(yExtrapolated - yValue);
						double tolerance = yValuesHi.get(j) - yValuesLo.get(j) + 1;
						distance = Math.sqrt(distance*distance + tolerance*tolerance) - tolerance; //better score if in or near (yLo-yHi) range
						float overlap = Float.NaN;
						if (!Float.isNaN(curveValuesLo.get(n))) {
							overlap = Math.min(curveValuesHi.get(n) - yValuesLo.get(j), yValuesHi.get(j) - curveValuesLo.get(n));
							if (overlap >= -1)
								distance *= 0.2;			//curve continues 8-connected, better score
						}
						if (distance < minDistance && (distance <= MAX_Y_JUMP || overlap > -1)) {
							minDistance = distance;
							jOfMinDist = j;
						}
					}
					if (jOfMinDist >= 0) {
						addPoint(yDataAll, curveValuesLo, curveValuesHi, n, x-xStart, yValuesLo.get(jOfMinDist), yValuesHi.get(jOfMinDist));
						yValuesLo.set(jOfMinDist, Float.NaN);	//continuation found, don't search for that value any more
					}
				}
				for (int j=0; j<yValuesLo.size(); j++) {	//add the remaining points to an arbitrary curve that has not been used recently
					if (!Float.isNaN(yValuesLo.get(j))) {
						for (int n=0; n<yDataAll.size(); n++) {
							FloatArray arr = yDataAll.get(n);
							if (arr.size() < x-xStart-MAX_EXTRAPOLATE) {
								addPoint(yDataAll, curveValuesLo, curveValuesHi, n, x-xStart, yValuesLo.get(j), yValuesHi.get(j));
								yValuesLo.set(j, Float.NaN);
								break;
							}
						}
					}
					if (!Float.isNaN(yValuesLo.get(j)))		//no curve to add it, start a new curve
						addPoint(yDataAll, curveValuesLo, curveValuesHi, -1, x-xStart, yValuesLo.get(j), yValuesHi.get(j));
				}
				yValuesLo.clear();
				yValuesHi.clear();
			}
			for (int n=0; n<yDataAll.size(); n++) {
				FloatArray arr = yDataAll.get(n);
				if (arr.size() < x-xStart)					//all curves where we have no current point:
					curveValuesLo.set(n, Float.NaN);		//no valid information on last y range
			}
		}
		if (xData.size() == 0)
			return;

		int maxlen = 0;										//maximum array length of y data
		for (int n=0; n<yDataAll.size(); n++)
			if (yDataAll.get(n).size() > maxlen)
				maxlen = yDataAll.get(n).size();

		double[] points = new double[yDataAll.size()];
		for (int n=0; n<yDataAll.size(); n++) {				//count data points of all curves
			FloatArray arr = yDataAll.get(n);
			for (int ix=0; ix<arr.size(); ix++) {
				if (!Float.isNaN(arr.get(ix)))
					points[n]++;
			}
		}
		int[] ranks = Tools.rank(points);

		String xLabel = "X ("+cal.getUnits()+")";
		String yLabel = "Y ("+cal.getYUnit()+")";
		Plot plot = new Plot(WindowManager.getUniqueName("Line Graph"), xLabel, yLabel);
		float[] xArray = xData.toArray();
		if (xArray.length > maxlen)
			xArray = Arrays.copyOf(xArray, maxlen);
		for (int i=ranks.length-1; i>=0; i--) {				//plot curves with many points first
			int n = ranks[i];
			float[] yArray = yDataAll.get(n).toArray();
			for (int j=0; j<yArray.length; j++) {
				if (cal.scaled())
					yArray[j] = (float)cal.getY(yArray[j], height);
				else
					yArray[j] = height - 1 - yArray[j];
			}
			plot.addPoints(xArray, yArray, null, Plot.LINE, "y"+(ranks.length-i));
		}
		plot.setLimitsToFit(false);
		plot.show();				
	}

	/** Adds the average of yLo, yhi to the n-th FloatArray at a given (integer) x value.
	 *  A new FloatArray is created if n < 0.
	 *  Missing data are filled with NaN if necessary.
	 *  Also writes the yLo and yHi values to the curveValuesLo, curveValuesHi arrays at the corresponding index n */
	void addPoint(ArrayList<FloatArray> yDataAll, FloatArray curveValuesLo, FloatArray curveValuesHi, int n, int x, float yLo, float yHi) {
		if (n < 0) {
			yDataAll.add(new FloatArray());
			n = yDataAll.size() - 1;
			curveValuesLo.add(yLo);
			curveValuesHi.add(yHi);
		}
		FloatArray arr = yDataAll.get(n);
		for (int i=arr.size(); i<x; i++)	//fill unwritten range with NaN
			arr.add(Float.NaN);
		arr.add(0.5f*(yLo + yHi));
		curveValuesLo.set(n, yLo);
		curveValuesHi.set(n, yHi);
	}


	/** Returns the extrapolated value or NaN if we can't extrapolate.
	 *  Extrapolation is based on the last array value added and the slope
	 *  of the last values (never more than MAX_EXTRAPOLATE points back)
	 *  A Extrapolator instance must be provided (avoids unnnecessary garbage) */
	float extrapolated(FloatArray arr, int x, Extrapolator extrapolator) {
		extrapolator.clear();
		for (int i=0, ix=x-1; ix>=0; ix--) {
			if (ix >= arr.size()) continue; //we don't have a point at this x value
			double y = arr.get(ix);
			if (Double.isNaN(y) && ix < x-MAX_EXTRAPOLATE && i==0)
				return Float.NaN;		//no data in allowed extrapolation range
			if (ix < x-MAX_EXTRAPOLATE)
				break;					//don't take extrapolated data from too far
			if (!Double.isNaN(y)) {
				extrapolator.add(ix, y);
				i++;
			}
		}
		return (float)extrapolator.extrapolate(x);
	}

	/** Extrapolator class.
	 *  Extrapolation is based on the first point added and the slope of a
	 *  linear regression over all points added to the Extrapolator. */
	class Extrapolator {
		int counter = 0;
		double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
		double firstX, firstY = Double.NaN;

		/** Clears the extrapolator */
		public void clear() {
			counter = 0;
			sumX = 0;  sumY = 0;
			sumXY = 0; sumX2 = 0;
			firstY = Double.NaN;
		}

		/** Adds a point x,y */
		public void add(double x, double y) {
			counter++;
			sumX+=x;     sumY+=y;
			sumXY+=x*y;  sumX2+=x*x;
			if (Double.isNaN(firstY)) {
				firstX = x;
				firstY = y;
			}
		}

		/** Gets the value of the extrapolation or NaN if undefined.
		 *  If the x value where we want the extrapolated value is close to
		 *  the first point added and no slope is avialable, assumes a slope of 0 */
		public double extrapolate(double x) {
			if (counter <= 0) return Double.NaN;
			double slope=(sumXY-sumX*sumY/counter)/(sumX2-sumX*sumX/counter);
			if (Double.isNaN(slope) && Math.abs(x-firstX) <= 3) slope=0; //if we have no valid slope, assume slope=0 for nearby points
			return firstY + slope*(x-firstX);
		}
	}

}
