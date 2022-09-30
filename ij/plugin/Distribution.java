package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.PlugIn;
import ij.measure.*;
import ij.util.Tools;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;

/**
This plugin implements the Analyze/Distribution command.
It reads the data from the ResultsTable and plots a frequency histogram.
@author G. Landini at bham. ac. uk
*/
public class Distribution implements PlugIn, TextListener {
	static String parameter = "Area";
	static boolean autoBinning = true;
	static int nBins = 10;
	static String range = "0-0";
	Checkbox checkbox;
	TextField nBinsField, rangeField;
	String defaultNBins, defaultRange;

	public void run(String arg) {
		ResultsTable rt=ResultsTable.getResultsTable();
		if (rt.size()==0) {
			IJ.error("Distribution", "The \"Results\" table is empty");
			return;
		}
		run(rt);
	}
	
	public void run(ResultsTable rt) {
		if (rt==null)
			return;
		int count = rt.size();
		String head = rt.getColumnHeadings();
		//IJ.log(head);

		StringTokenizer t = new StringTokenizer(head, "\t");
		int tokens = t.countTokens()-1;
		String[] strings = new String[tokens];
		strings[0] = t.nextToken(); // first token is empty?
	   	for(int i=0; i<tokens; i++)
			strings[i] = t.nextToken();

		defaultNBins = ""+nBins;
		defaultRange = range;
		GenericDialog gd = new GenericDialog("Distribution");
		gd.addChoice("Parameter: ", strings, strings[getIndex(strings)]);
		gd.setInsets(0, 40, 0);
		gd.addMessage(count+" data points", null, Color.darkGray);
		gd.addCheckbox("Automatic binning", autoBinning);
		gd.addNumericField ("or specify bins:", nBins, 0);
		gd.addStringField ("and range:", range);

		Vector v = gd.getNumericFields();
		if (v!=null) {
			nBinsField = (TextField)v.elementAt(0);
			nBinsField.addTextListener(this);
		}
		v = gd.getStringFields();
		if (v!=null) {
			rangeField = (TextField)v.elementAt(0);
			rangeField.addTextListener(this);
		}
		checkbox = (Checkbox)(gd.getCheckboxes().elementAt(0));
		gd.showDialog();
		if (gd.wasCanceled())
			return;

		parameter = gd.getNextChoice ();
		autoBinning = gd.getNextBoolean();
		double nMin=0.0, nMax=0.0;
		if (!autoBinning) {
			nBins = (int)gd.getNextNumber();
			range = gd.getNextString();
			// find hyphen and replace with comma
			String[] minAndMax = range.replaceAll("([0-9.])[\t ]*-", "$1,").split(",");
			nMin = Tools.parseDouble(minAndMax[0]);
			nMax = minAndMax.length==2?Tools.parseDouble(minAndMax[1]):Double.NaN;
			if (Double.isNaN(nMin) || Double.isNaN(nMax))
				{nMin=0.0; nMax=0.0; range="0-0";}
		}

		float[] data = null;
		int index = rt.getColumnIndex(parameter);
		if (index>=0)
			data = rt.getColumn(index);
		if (data==null) {
			IJ.error("Distribution", "No available results: \""+parameter+"\"");
			return;
		}

		float [] pars = new float [11];
		stats(count, data, pars);
		if (autoBinning) {
			//sd = 7, min = 3, max = 4
			// use Scott's method (1979 Biometrika, 66:605-610) for optimal binning: 3.49*sd*N^-1/3
			float binWidth = (float)(3.49 * pars[7]*(float)Math.pow((float)count, -1.0/3.0));
			nBins= (int)Math.floor(((pars[4]-pars[3])/binWidth)+.5);
			if (nBins<2) nBins = 2;
		}

		ImageProcessor ip = new FloatProcessor(count, 1, data, null);
		ImagePlus imp = new ImagePlus("", ip);
		ImageStatistics stats = new StackStatistics(imp, nBins, nMin, nMax);
		int maxCount = 0;
		for (int i=0; i<stats.histogram.length; i++) {
			if (stats.histogram[i]>maxCount)
				maxCount = stats.histogram[i];
		}
		stats.histYMax = maxCount;
		new HistogramWindow(parameter+" Distribution", imp, stats);
	}
	
	int getIndex(String[] strings) {
		for (int i=0; i<strings.length; i++) {
			if (strings[i].equals(parameter))
				return i;
		}
		return 0;
	}

	public void textValueChanged(TextEvent e) {
		if (!defaultNBins.equals(nBinsField.getText()))
			checkbox.setState(false);
		if (!defaultRange.equals(rangeField.getText()))
			checkbox.setState(false);
	}

	void stats(int nc, float[] data, float[] pars){
 // ("\tPoints\tEdges_n\tGraph_Length\tMin\tMax\tMean\tAvDev\tSDev\tVar\tSkew\tKurt");
		int i;
		float s = 0, min = Float.MAX_VALUE, max = -Float.MAX_VALUE, totl=0, ave=0, adev=0, sdev=0, var=0, skew=0, kurt=0, p;

		for(i=0;i<nc;i++){
			totl+= data[i];
			//tot& = tot& + 1
				if(data[i]<min) min = data[i];
			if(data[i]>max) max = data[i];
		}

		ave = totl/nc;

		for(i=0;i<nc;i++){
			s = data[i] - ave;
			adev+=Math.abs(s);
			p = s * s;
			var+= p;
			p*=s;
			skew+= p;
			p*= s;
			kurt+= p;
		}

		adev/= nc;
		var/=nc-1;
		sdev = (float) Math.sqrt(var);

		if(var> 0){
			skew = (float)skew / (nc * (float) Math.pow(sdev,3));
			kurt = (float)kurt / (nc * (float) Math.pow(var, 2)) - 3;
		}
		pars[1]=(float) nc;
		pars[2]=totl;
		pars[3]=min;
		pars[4]=max;
		pars[5]=ave;
		pars[6]=adev;
		pars[7]=sdev;
		pars[8]=var;
		pars[9]=skew;
		pars[10]=kurt;

	}

}
