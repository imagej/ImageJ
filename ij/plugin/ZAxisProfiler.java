package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.filter.Analyzer;
import ij.util.Tools;
import java.awt.*;

/** Implements the Image/Stack/Plot Z-axis Profile command. */
public class ZAxisProfiler implements PlugIn, Measurements, PlotMaker  {
	private static String[] choices = {"time", "z-axis"};
	private static String choice = choices[0];
	private boolean showingDialog;
	private ImagePlus imp;
	private boolean isPlotMaker;
	private boolean timeProfile;
	private boolean firstTime = true;

	public void run(String arg) {
		imp = IJ.getImage();
		if (imp.getStackSize()<2) {
			IJ.error("ZAxisProfiler", "This command requires a stack.");
			return;
		}
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isLine()) {
			IJ.error("ZAxisProfiler", "This command does not work with line selections.");
			return;
		}
		isPlotMaker = roi!=null && !IJ.macroRunning();
		Plot plot = getPlot();
		if (plot!=null) {
			if (isPlotMaker)
				plot.setPlotMaker(this);
			plot.show();
		}
	}
		
	public Plot getPlot() {
		Roi roi = imp.getRoi();
		ImageProcessor ip = imp.getProcessor();
		double minThreshold = ip.getMinThreshold();
		double maxThreshold = ip.getMaxThreshold();
		float[] y;
		boolean hyperstack = imp.isHyperStack();
		if (hyperstack)
			y = getHyperstackProfile(roi, minThreshold, maxThreshold);
		else
			y = getZAxisProfile(roi, minThreshold, maxThreshold);
		if (y==null)
			return null;
		float[] x = new float[y.length];
		
		String xAxisLabel = showingDialog&&choice.equals(choices[0])?"Frame":"Slice";
		Calibration cal = imp.getCalibration();
		if (cal.scaled()) {
			float c=1.0f;
			if (timeProfile) {
				c = (float) cal.frameInterval;
				xAxisLabel = "["+cal.getTimeUnit()+"]";
			} else {
				c = (float) cal.pixelDepth;
				xAxisLabel = "["+cal.getZUnit()+"]";
			}
			for (int i=0; i<x.length; i++)
				x[i] = i*c;
		} else {
			for (int i=0; i<x.length; i++)
				x[i] = i+1;
		}
		String title;
		if (roi!=null) {
			Rectangle r = roi.getBounds();
			title = imp.getTitle()+"-"+r.x+"-"+r.y;
		} else
			title = imp.getTitle()+"-0-0";
		//String xAxisLabel = showingDialog&&choice.equals(choices[0])?"Frame":"Slice";
		Plot plot = new Plot(title, xAxisLabel, "Mean", x, y);
		if (x.length<=60) {
			plot.setColor(Color.red);
			plot.addPoints(x, y, Plot.CIRCLE);
			plot.setColor(Color.black);
		}
		double ymin = ProfilePlot.getFixedMin();
		double ymax= ProfilePlot.getFixedMax();
		if (!(ymin==0.0 && ymax==0.0)) {
			double[] a = Tools.getMinMax(x);
			double xmin=a[0]; double xmax=a[1];
			plot.setLimits(xmin, xmax, ymin, ymax);
		}
		if (!firstTime) {
			int pos = imp.getCurrentSlice();
			int size = imp.getStackSize();
			if (hyperstack) {
				if (timeProfile) {
					pos = imp.getT();
					size = imp.getNFrames();
				} else {
					pos = imp.getZ();
					size = imp.getNSlices();
				}
			}
			double xx = (pos-1.0)/(size-1.0);
			if (xx==0.0)
				plot.setLineWidth(2);
			plot.setColor(Color.blue);
			plot.drawNormalizedLine(xx, 0, xx, 1.0);
			plot.setColor(Color.black);
			plot.setLineWidth(1);
		}
		firstTime = false;
		return plot;
	}
	
	public ImagePlus getSourceImage() {
		return imp;
	}

	private float[] getHyperstackProfile(Roi roi, double minThreshold, double maxThreshold) {
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		int c = imp.getC();
		int z = imp.getZ();
		int t = imp.getT();
		int size = slices;
		if (firstTime)
			timeProfile = slices==1 && frames>1;
		if (slices>1 && frames>1 && (!isPlotMaker ||firstTime)) {
			showingDialog = true;
			GenericDialog gd = new GenericDialog("Profiler");
			gd.addChoice("Profile", choices, choice);
			gd.showDialog();
			if (gd.wasCanceled())
				return null;
			choice = gd.getNextChoice();
			timeProfile = choice.equals(choices[0]);
		}
		if (timeProfile)
			size = frames;
		else
			size = slices;
		float[] values = new float[size];
		Calibration cal = imp.getCalibration();
		Analyzer analyzer = new Analyzer(imp);
		int measurements = Analyzer.getMeasurements();
		boolean showResults = !isPlotMaker && measurements!=0 && measurements!=LIMIT;
		measurements |= MEAN;
		if (showResults) {
			if (!Analyzer.resetCounter())
				return null;
		}
		ImageStack stack = imp.getStack();
		for (int i=1; i<=size; i++) {
			int index = 1;
			if (timeProfile)
				index = imp.getStackIndex(c, z, i);
			else
				index = imp.getStackIndex(c, i, t);
			ImageProcessor ip = stack.getProcessor(index);
			if (minThreshold!=ImageProcessor.NO_THRESHOLD)
				ip.setThreshold(minThreshold,maxThreshold,ImageProcessor.NO_LUT_UPDATE);
			ip.setRoi(roi);
			ImageStatistics stats = ImageStatistics.getStatistics(ip, measurements, cal);
			analyzer.saveResults(stats, roi);
			values[i-1] = (float)stats.mean;
		}
		if (showResults) {
			ResultsTable rt = Analyzer.getResultsTable();
			rt.show("Results");
		}
		return values;
	}

	private float[] getZAxisProfile(Roi roi, double minThreshold, double maxThreshold) {
		ImageStack stack = imp.getStack();
		if (firstTime) {
			int slices = imp.getNSlices();
			int frames = imp.getNFrames();
			timeProfile = slices==1 && frames>1;
		}
		int size = stack.getSize();
		float[] values = new float[size];
		Calibration cal = imp.getCalibration();
		Analyzer analyzer = new Analyzer(imp);
		int measurements = Analyzer.getMeasurements();
		boolean showResults = !isPlotMaker && measurements!=0 && measurements!=LIMIT;
		boolean showingLabels = firstTime && showResults && ((measurements&LABELS)!=0 || (measurements&SLICE)!=0);
		measurements |= MEAN;
		if (showResults) {
			if (!Analyzer.resetCounter())
				return null;
		}
		int current = imp.getCurrentSlice();
		for (int i=1; i<=size; i++) {
			if (showingLabels)
				imp.setSlice(i);
			ImageProcessor ip = stack.getProcessor(i);
			if (minThreshold!=ImageProcessor.NO_THRESHOLD)
				ip.setThreshold(minThreshold,maxThreshold,ImageProcessor.NO_LUT_UPDATE);
			ip.setRoi(roi);
			ImageStatistics stats = ImageStatistics.getStatistics(ip, measurements, cal);
			analyzer.saveResults(stats, roi);
			values[i-1] = (float)stats.mean;
		}
		if (showResults) {
			ResultsTable rt = Analyzer.getResultsTable();
			rt.show("Results");
		}
		if (showingLabels)
			imp.setSlice(current);
		return values;
	}
	
}

