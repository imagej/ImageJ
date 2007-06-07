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
	private static double xMin;
	private static double xMax;
	private static String yMax = "Auto";
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
 			xMin = 0.0;
 			xMax = 0.0;
 			yMax = "Auto";
 		}
 		ImageStatistics stats = null;
 		if (useImageMinAndMax)
 			{xMin=0.0; xMax=0.0;}
 		int iyMax = (int)Tools.parseDouble(yMax, 0.0);
 		if (stackHistogram) {
			stats = new StackStatistics(imp, nBins, xMin, xMax);
			new HistogramWindow("Histogram of "+imp.getShortTitle(), imp, stats);
		} else
			new HistogramWindow("Histogram of "+imp.getShortTitle(), imp, nBins, xMin, xMax, iyMax);
	}
	
	boolean showDialog(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		double min = ip.getMin();
		double max = ip.getMax();
		if (imp.getID()!=imageID || (min==xMin&&min==xMax))
			useImageMinAndMax = true;
		if (imp.getID()!=imageID || useImageMinAndMax) {
			xMin = min;
			xMax = max;
		}
		defaultMin = IJ.d2s(xMin,2);
		defaultMax = IJ.d2s(xMax,2);;
		imageID = imp.getID();
		int stackSize = imp.getStackSize();
		GenericDialog gd = new GenericDialog("Histogram");
		gd.addNumericField("Bins:", HistogramWindow.nBins, 0);
		gd.addCheckbox("Use min/max or:", useImageMinAndMax);
		//gd.addMessage("          or");
		gd.addMessage("");
		gd.addNumericField("X_Min:", xMin, 2);
		gd.addNumericField("X_Max:", xMax, 2);
		gd.addMessage(" ");
		gd.addStringField("Y_Max:", yMax, 6);
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
		xMin = gd.getNextNumber();
		xMax = gd.getNextNumber();
		yMax = gd.getNextString();
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