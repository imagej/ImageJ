package ij.plugin;
import ij.*;
import ij.process.*;
import ij.plugin.filter.Analyzer;
import ij.measure.ResultsTable;

/** This plugin implements the Image/Stacks/Statistics command. */
public class Stack_Statistics implements PlugIn {
	
	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		ImageStatistics stats = new StackStatistics(imp);
		ResultsTable rt = Analyzer.getResultsTable();
		rt.incrementCounter();
		rt.addValue("Voxels", stats.longPixelCount);
		//rt.addValue("Volume", stats.area);
		rt.addValue("Mean", stats.mean);
		rt.addValue("StdDev", stats.stdDev);
		rt.addValue("Min", stats.min);
		rt.addValue("Max", stats.max);
		rt.show("Results");
	}
	
}
