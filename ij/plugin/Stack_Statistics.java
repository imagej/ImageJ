package ij.plugin;
import ij.*;
import ij.process.*;
import ij.plugin.filter.Analyzer;
import ij.measure.*;
import ij.gui.Roi;
import java.awt.Rectangle;

/** This plugin implements the Image/Stacks/Statistics command. */
public class Stack_Statistics implements PlugIn {
	
	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
       	int measurements = Analyzer.getMeasurements();
       	Analyzer.setMeasurements(measurements | Measurements.LIMIT);
		ImageStatistics stats = new StackStatistics(imp);
       	Analyzer.setMeasurements(measurements);
		ResultsTable rt = Analyzer.getResultsTable();
		rt.incrementCounter();
		Roi roi = imp.getRoi();
		if (roi!=null && !roi.isArea()) {
			imp.deleteRoi();
			roi = null;
		}
       	double stackVoxels = 0.0;
       	double images = imp.getStackSize();
		if (roi==null)
			stackVoxels = imp.getWidth()*imp.getHeight()*images;
		else if (roi.getType()==Roi.RECTANGLE) {
			Rectangle r = roi.getBounds();
			stackVoxels = r.width*r.height*images;
		} else {
       		Analyzer.setMeasurements(measurements & ~Measurements.LIMIT);
			ImageStatistics stats2 = new StackStatistics(imp);
       		Analyzer.setMeasurements(measurements);
       		stackVoxels = stats2.longPixelCount;
		}
		Calibration cal = imp.getCalibration();
		String units = cal.getUnits();	
       	double scale = cal.pixelWidth*cal.pixelHeight*cal.pixelDepth;
		rt.addValue("Voxels", stats.longPixelCount);
       	if (scale!=1.0)
		rt.addValue("Volume("+units+"^3)", stats.longPixelCount*scale);
		rt.addValue("%Volume", stats.longPixelCount*100.0/stackVoxels);
		rt.addValue("Mean", stats.mean);
		rt.addValue("StdDev", stats.stdDev);
		rt.addValue("Min", stats.min);
		rt.addValue("Max", stats.max);
		rt.show("Results");
	}
	
}
