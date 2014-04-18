package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.util.Tools;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.Recorder;
import ij.measure.Calibration;
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;


/** This plugin implements the Analyze/Histogram command. */
public class Histogram implements PlugIn, TextListener {

	private static boolean staticUseImageMinAndMax = true;
	private static double staticXMin, staticXMax;
	private static String staticYMax = "Auto";
	private static boolean staticStackHistogram;
	private static int imageID;	
	private int nBins = 256;
	private boolean useImageMinAndMax = true;
	private double xMin, xMax;
	private String yMax = "Auto";
	private boolean stackHistogram;
	private Checkbox checkbox;
	private TextField minField, maxField;
	private String defaultMin, defaultMax;

 	public void run(String arg) {
 		ImagePlus imp = IJ.getImage();
 		int bitDepth = imp.getBitDepth();
 		if (bitDepth==32 || IJ.altKeyDown() || (IJ.isMacro()&&Macro.getOptions()!=null)) {
			IJ.setKeyUp(KeyEvent.VK_ALT);
 			if (!showDialog(imp))
 				return;
 		} else {
 			int stackSize = imp.getStackSize();
 			boolean noDialog = stackSize==1 || imp.isComposite();
 			if (stackSize==3) {
 				ImageStack stack = imp.getStack();
 				String label1 = stack.getSliceLabel(1);
 				if ("Hue".equals(label1))
 					noDialog = true;
 			}
 			int flags = noDialog?0:setupDialog(imp, 0);
 			if (flags==PlugInFilter.DONE) return;
			stackHistogram = flags==PlugInFilter.DOES_STACKS;
			Calibration cal = imp.getCalibration();
			if (bitDepth==16 && ImagePlus.getDefault16bitRange()!=0) {
				xMin = 0.0;
				xMax = Math.pow(2,ImagePlus.getDefault16bitRange())-1;
				useImageMinAndMax = false;
			} else if (stackHistogram && ((bitDepth==8&&!cal.calibrated())||bitDepth==24)) {
				xMin = 0.0;
				xMax = 256.0;
				useImageMinAndMax = false;
			} else
				useImageMinAndMax = true;
 			yMax = "Auto";
 		}
 		ImageStatistics stats = null;
 		if (useImageMinAndMax) {
 			xMin = 0.0;
 			xMax = 0.0;
 		}
 		int iyMax = (int)Tools.parseDouble(yMax, 0.0);
 		boolean customHistogram = (bitDepth==8||bitDepth==24) && (!(xMin==0.0&&xMax==0.0)||nBins!=256||iyMax>0);
 		if (stackHistogram || customHistogram) {
 			ImagePlus imp2 = imp;
 			if (customHistogram && !stackHistogram && imp.getStackSize()>1)
 				imp2 = new ImagePlus("Temp", imp.getProcessor());
			stats = new StackStatistics(imp2, nBins, xMin, xMax);
			stats.histYMax = iyMax;
			new HistogramWindow("Histogram of "+imp.getShortTitle(), imp, stats);
		} else
			new HistogramWindow("Histogram of "+imp.getShortTitle(), imp, nBins, xMin, xMax, iyMax);
	}
	
	boolean showDialog(ImagePlus imp) {
		if (!IJ.isMacro()) {
			nBins = HistogramWindow.nBins;
			useImageMinAndMax = staticUseImageMinAndMax;
			xMin=staticXMin; xMax=staticXMax;
			yMax = staticYMax;
			stackHistogram = staticStackHistogram;
		}
		ImageProcessor ip = imp.getProcessor();
		double min = ip.getMin();
		double max = ip.getMax();
		if (imp.getID()!=imageID || (min==xMin&&min==xMax))
			useImageMinAndMax = true;
		if (imp.getID()!=imageID || useImageMinAndMax) {
			xMin = min;
			xMax = max;
			Calibration cal = imp.getCalibration();
			xMin = cal.getCValue(xMin);
			xMax = cal.getCValue(xMax);
		}
		defaultMin = IJ.d2s(xMin,2);
		defaultMax = IJ.d2s(xMax,2);
		imageID = imp.getID();
		int stackSize = imp.getStackSize();
		GenericDialog gd = new GenericDialog("Histogram");
		gd.addNumericField("Bins:", nBins, 0);
		gd.addCheckbox("Use pixel value range", useImageMinAndMax);
		gd.setInsets(5, 40, 10);
		gd.addMessage("or use:");
		int fwidth = 6;
		int nwidth = Math.max(IJ.d2s(xMin,2).length(), IJ.d2s(xMax,2).length());
		if (nwidth>fwidth) fwidth = nwidth;
		int digits = 2;
		if (xMin==(int)xMin && xMax==(int)xMax)
			digits = 0;
		gd.addNumericField("X_Min:", xMin, digits, fwidth, null);
		gd.addNumericField("X_Max:", xMax, digits, fwidth, null);
		gd.setInsets(15, 0, 10);
		gd.addStringField("Y_Max:", yMax, 6);
		if (stackSize>1)
			gd.addCheckbox("Stack histogram", stackHistogram);
		
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
		useImageMinAndMax = gd.getNextBoolean();
		xMin = gd.getNextNumber();
		xMax = gd.getNextNumber();
		yMax = gd.getNextString();
		stackHistogram = (stackSize>1)?gd.getNextBoolean():false;
		if (!IJ.isMacro()) {
			if (nBins>=2 && nBins<=1000)
				HistogramWindow.nBins = nBins;
			staticUseImageMinAndMax = useImageMinAndMax;
			staticXMin=xMin; staticXMax=xMax;
			staticYMax = yMax;
			staticStackHistogram = stackHistogram;
		}
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
				"Histogram", "Include all "+stackSize+" images?");
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
