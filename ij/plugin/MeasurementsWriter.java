package ij.plugin;
import ij.*;
import ij.text.*;
import ij.measure.ResultsTable;
import ij.io.*;
import java.io.*;
import java.awt.Frame;

/** Saves a tables as a csv or tab-delimited text file. */
public class MeasurementsWriter implements PlugIn {

	public void run(String path) {
		save(path);
	}
	
	public boolean save(String path) {
		Frame frame = WindowManager.getFrontWindow();
		if (frame!=null && (frame instanceof TextWindow) && !"Log".equals(frame.getTitle())) {
			TextWindow tw = (TextWindow)frame;
			return tw.getTextPanel().saveAs(path);
		} else if (IJ.isResultsWindow()) {
			TextPanel tp = IJ.getTextPanel();
			if (tp!=null) {
				if (!tp.saveAs(path))
					return false;
			}
		} else {
			ResultsTable rt = ResultsTable.getResultsTable();
			if (rt==null || rt.size()==0) {
				frame = WindowManager.getFrame("Results");
				if (frame==null || !(frame instanceof TextWindow))
					return false;
				else {
					TextWindow tw = (TextWindow)frame;
					return tw.getTextPanel().saveAs(path);
				}
			}
			if (path.equals("")) {
				SaveDialog sd = new SaveDialog("Save as Text", "Results", Prefs.defaultResultsExtension());
				String file = sd.getFileName();
				if (file == null) return false;
				path = sd.getDirectory() + file;
			}
			return rt.save(path);
		}
		return true;
	}

}

