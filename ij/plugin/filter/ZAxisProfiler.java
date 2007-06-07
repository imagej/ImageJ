package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.filter.Analyzer;
import java.awt.Rectangle;

/** Implements the Image/Stack/Plot Z-axis Profile command. */
public class ZAxisProfiler implements PlugInFilter, Measurements  {

	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+NO_CHANGES+ROI_REQUIRED;
	}

	public void run(ImageProcessor ip) {
		if (imp.getStackSize()<2) {
			IJ.showMessage("ZAxisProfiler", "This command requires a stack.");
			return;
		}
		Roi roi = imp.getRoi();
		if (roi.getType()>=Roi.LINE) {
			IJ.showMessage("ZAxisProfiler", "This command does not work with line selections.");
			return;
		}
		float[] y = getZAxisProfile(roi);
		if (y!=null) {
			float[] x = new float[y.length];
			for (int i=0; i<x.length; i++)
				x[i] = i+1;
			Rectangle r = imp.getRoi().getBoundingRect();
			new PlotWindow(imp.getTitle()+"-"+r.x+"-"+r.y, "Slice", "Mean", x, y).draw();
		}			
	}
		
	float[] getZAxisProfile(Roi roi) {
		ImageStack stack = imp.getStack();
		int size = stack.getSize();
		float[] values = new float[size];
		int[] mask = imp.getMask();
		Rectangle r = imp.getRoi().getBoundingRect();
		Calibration cal = imp.getCalibration();
		Analyzer analyzer = new Analyzer(imp);
		int measurements = analyzer.getMeasurements();
		boolean showResults = measurements!=0 && measurements!=LIMIT;
		measurements |= MEAN;
		if (showResults) {
			if (!analyzer.resetCounter())
				return null;
		}
		for (int i=1; i<=size; i++) {
			ImageProcessor ip = stack.getProcessor(i);
			ip.setRoi(r);
			ip.setMask(mask);
			ImageStatistics stats = ImageStatistics.getStatistics(ip, measurements, cal);
			analyzer.saveResults(stats, roi);
			if (showResults)
				analyzer.displayResults();
			values[i-1] = (float)stats.mean;
		}
		return values;
	}
	
}

