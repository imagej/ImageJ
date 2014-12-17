package ij.plugin;
import ij.*;
import ij.text.*;
import ij.measure.ResultsTable;
import ij.io.*;
import java.io.*;
import java.awt.Frame;

/** Writes measurements to a csv or tab-delimited text file. */
public class MeasurementsWriter implements PlugIn {

	public void run(String path) {
		save(path);
	}
	
	public boolean save(String path) {
		Frame frame = WindowManager.getFrontWindow();
		if (frame!=null && (frame instanceof TextWindow) && ((TextWindow)frame).getTextPanel().getResultsTable()!=null) {
			ResultsTable rt = ((TextWindow)frame).getTextPanel().getResultsTable();
			return rt.save(path);
		} else if (IJ.isResultsWindow() && IJ.getTextPanel()!=null) {
			TextPanel tp = IJ.getTextPanel();
			ResultsTable rt = tp.getResultsTable();
			return rt.save(path);
		} else {
			ResultsTable rt = ResultsTable.getResultsTable();
			if (rt==null || rt.getCounter()==0) {
				frame = WindowManager.getFrame("Results");
				if (frame==null || !(frame instanceof TextWindow)) {
					frame = WindowManager.getFrontWindow();
					if (frame!=null && (frame instanceof TextWindow)) {
						TextWindow tw = (TextWindow)frame;
						return tw.getTextPanel().saveAs(path);
					} else
						return false;
				} else {
					TextWindow tw = (TextWindow)frame;
					return tw.getTextPanel().saveAs(path);
				}
			}
			if (path.equals("")) {
				SaveDialog sd = new SaveDialog("Save as Text", "Results", Prefs.get("options.ext", ".xls"));
				String file = sd.getFileName();
				if (file == null) return false;
				path = sd.getDirectory() + file;
			}
			return rt.save(path);
		}
	}

}

