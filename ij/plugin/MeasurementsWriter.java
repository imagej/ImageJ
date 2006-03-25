package ij.plugin;
import ij.IJ;
import ij.text.TextPanel;

/** Writes measurements to a tab-delimited text file. */
public class MeasurementsWriter implements PlugIn {

	public void run(String arg) {
		if (IJ.isResultsWindow()) {
			TextPanel tp = IJ.getTextPanel();
			if (tp!=null)
				tp.saveAs(arg);
		}
	}
	
}

