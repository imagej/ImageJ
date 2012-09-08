package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import java.util.*;

/** This plugin implements the Results Table's Sort command. */
public class ResultsSorter implements PlugIn {
	static String parameter = "Area";

	public void run(String arg) {
		ResultsTable rt = ResultsTable.getResultsTable();
		int count = rt.getCounter();
		if (count==0) {
			IJ.error("Sort", "The \"Results\" table is empty");
			return;
		}
		String head= rt.getColumnHeadings();
		StringTokenizer t = new StringTokenizer(head, "\t");
		int tokens = t.countTokens()-1;
		String[] strings = new String[tokens];
		strings[0] = t.nextToken(); // first token is empty?
	   	for(int i=0; i<tokens; i++)
			strings[i] = t.nextToken();
		GenericDialog gd = new GenericDialog("Sort");
		gd.addChoice("Parameter: ", strings, strings[getIndex(strings)]);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		parameter = gd.getNextChoice ();
		float[] data = null;
		int index = rt.getColumnIndex(parameter);
		if (index>=0)
			data = rt.getColumn(index);
		if (data==null) {
			IJ.error("Sort", "No available results: \""+parameter+"\"");
			return;
		}
	}
	
	private int getIndex(String[] strings) {
		for (int i=0; i<strings.length; i++) {
			if (strings[i].equals(parameter))
				return i;
		}
		return 0;
	}

}
