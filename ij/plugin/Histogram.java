package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.util.Tools;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.Recorder;
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;


/** This plugin implements the Analyze/Histogram command. */
public class Histogram implements PlugIn, TextListener {

	private static int nBins = 256;
	private static boolean useImageMinAndMax = true;
	private static double histMin;
	private static double histMax;
	private static boolean stackHistogram;
	private static int imageID;	
	private Checkbox checkbox;
	private TextField minField, maxField;
	private String defaultMin, defaultMax;

 	public void run(String arg) {
 		ImagePlus imp = IJ.getImage();
 		if (imp.getBitDepth()==32) {
 			if (!showDialog(imp))
 				return;
 		} else {
 			int flags = setupDialog(imp, 0);
 			if (flags==PlugInFilter.DONE) return;
			stackHistogram = flags==PlugInFilter.DOES_STACKS;
 			nBins = 256;
 			histMin = 0.0;
 			histMax = 0.0;
 		}
 		ImageStatistics stats = null;
 		if (useImageMinAndMax)
 			{histMin=0.0; histMax=0.0;}
 		if (stackHistogram) {
			stats = new StackStatistics(imp, nBins, histMin, histMax);
			new HistogramWindow("Histogram of "+imp.getShortTitle(), imp, stats);
		} else 
			new HistogramWindow("Histogram of "+imp.getShortTitle(), imp, nBins, histMin, histMax);
	}
	
	boolean showDialog(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		double min = ip.getMin();
		double max = ip.getMax();
		if (imp.getID()!=imageID || (min==histMin&&min==histMax))
			useImageMinAndMax = true;
		if (imp.getID()!=imageID || useImageMinAndMax) {
			histMin = min;
			histMax = max;
		}
		defaultMin = IJ.d2s(histMin,2);
		defaultMax = IJ.d2s(histMax,2);;
		imageID = imp.getID();
		int stackSize = imp.getStackSize();
		GenericDialog gd = new GenericDialog("Histogram");
		gd.addNumericField("Number of Bins:", HistogramWindow.nBins, 0);
		gd.addCheckbox("Use Image Min and Max", useImageMinAndMax);
		gd.addMessage("          or");
		gd.addMessage("");
		gd.addNumericField("Histogram_Min:", histMin, 2);
		gd.addNumericField("Histogram_Max:", histMax, 2);
		if (stackSize>1)
			gd.addCheckbox("Stack Histogram", stackHistogram);
		Vector numbers = gd.getNumericFields();
		minField = (TextField)numbers.elementAt(1);
		minField.addTextListener(this);
		maxField = (TextField)numbers.elementAt(2);
		maxField.addTextListener(this);
		checkbox = (Checkbox)(gd.getCheckboxes().elementAt(0));
		gd.showDialog();
		if (gd.wasCanceled())
			return false;			
		nBins = (int)gd.getNextNumber();
		if (nBins>=2 && nBins<=1000)
			HistogramWindow.nBins = nBins;
		useImageMinAndMax = gd.getNextBoolean();
		histMin = gd.getNextNumber();
		histMax = gd.getNextNumber();
		stackHistogram = (stackSize>1)?gd.getNextBoolean():false;
		IJ.register(Histogram.class);
		return true;
	}

	public void textValueChanged(TextEvent e) {
		boolean rangeChanged = !defaultMin.equals(minField.getText())
			|| !defaultMax.equals(maxField.getText());
		if (rangeChanged)
			checkbox.setState(false);
	}
	
	int setupDialog(ImagePlus imp, int flags) {
		int stackSize = imp.getStackSize();
		if (stackSize>1) {
			String macroOptions = Macro.getOptions();
			if (macroOptions!=null) {
				if (macroOptions.indexOf("stack ")>=0)
					return flags+PlugInFilter.DOES_STACKS;
				else
					return flags;
			}
			YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(),
				"Histogram", "Include all "+stackSize+" slices?");
			if (d.cancelPressed())
				return PlugInFilter.DONE;
			else if (d.yesPressed()) {
				if (Recorder.record)
					Recorder.recordOption("stack");
				return flags+PlugInFilter.DOES_STACKS;
			}
			if (Recorder.record)
				Recorder.recordOption("slice");
		}
		return flags;
	}

}