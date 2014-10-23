package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.frame.Recorder;
import ij.plugin.filter.PlugInFilter;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/** This plugin implements the Process/Binary/Make Binary 
	and Convert to Mask commands. */
public class Thresholder implements PlugIn, Measurements, ItemListener {
	
	public static final String[] methods = AutoThresholder.getMethods();
	public static final String[] backgrounds = {"Default", "Dark", "Light"};
	private double minThreshold;
	private double maxThreshold;
	boolean autoThreshold;
	boolean skipDialog;
	static boolean fill1 = true;
	static boolean fill2 = true;
	static boolean useBW = true;
	private boolean useLocal = true;
	private boolean listThresholds;
	boolean convertToMask;
	private String method = methods[0];
	private String background = backgrounds[0];
	private static boolean staticUseLocal = true;
	private static boolean staticListThresholds;
	private static String staticMethod = methods[0];
	private static String staticBackground = backgrounds[0];
	private ImagePlus imp;
	private Vector choices;

	public void run(String arg) {
		convertToMask = arg.equals("mask");
		skipDialog = arg.equals("skip") || convertToMask;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
		if (imp.getStackSize()==1) {
			Undo.setup(Undo.COMPOUND_FILTER, imp);
			applyThreshold(imp);
			Undo.setup(Undo.COMPOUND_FILTER_DONE, imp);
		} else
			convertStack(imp);
	}
	
	void convertStack(ImagePlus imp) {
		if (imp.getStack().isVirtual()) {
			IJ.error("Thresholder", "This command does not work with virtual stacks.\nUse Image>Duplicate to convert to a normal stack.");
			return;
		}
		boolean thresholdSet = imp.getProcessor().getMinThreshold()!=ImageProcessor.NO_THRESHOLD;
		this.imp = imp;
		if (!IJ.isMacro()) {
			method = staticMethod;
			background = staticBackground;
			useLocal = staticUseLocal;
			listThresholds = staticListThresholds;
			if (!thresholdSet)
				updateThreshold(imp);
		}
		if (thresholdSet)
			useLocal = listThresholds = false;
		GenericDialog gd = new GenericDialog("Convert Stack to Binary");
		gd.addChoice("Method:", methods, method);
		gd.addChoice("Background:", backgrounds, background);
		gd.addCheckbox("Calculate threshold for each image", useLocal);
		gd.addCheckbox("Black background (of binary masks)", Prefs.blackBackground);
		gd.addCheckbox("List thresholds", listThresholds);
		choices = gd.getChoices();
		if (choices!=null) {
			((Choice)choices.elementAt(0)).addItemListener(this);
			((Choice)choices.elementAt(1)).addItemListener(this);
		}
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		this.imp = null;
		method = gd.getNextChoice();
		background = gd.getNextChoice();
		useLocal = gd.getNextBoolean();
		boolean saveBlackBackground = Prefs.blackBackground;
		Prefs.blackBackground = gd.getNextBoolean();
		listThresholds = gd.getNextBoolean();
		if (!IJ.isMacro()) {
			staticMethod = method;
			staticBackground = background;
			staticUseLocal = useLocal;
			staticListThresholds = listThresholds;
		}
		Undo.reset();
		if (useLocal)
			convertStackToBinary(imp);
		else
			applyThreshold(imp);
		Prefs.blackBackground = saveBlackBackground; 
	}

	void applyThreshold(ImagePlus imp) {
		imp.deleteRoi();
		ImageProcessor ip = imp.getProcessor();
		ip.resetBinaryThreshold();  // remove any invisible threshold set by Make Binary or Convert to Mask
		int type = imp.getType();
		if (type==ImagePlus.GRAY16 || type==ImagePlus.GRAY32) {
			applyShortOrFloatThreshold(imp);
			return;
		}
		if (!imp.lock()) return;
		double saveMinThreshold = ip.getMinThreshold();
		double saveMaxThreshold = ip.getMaxThreshold();
		autoThreshold = saveMinThreshold==ImageProcessor.NO_THRESHOLD;
					
		boolean useBlackAndWhite = true;
		boolean noArgMacro =IJ.macroRunning() && Macro.getOptions()==null;
		if (skipDialog)
			fill1 = fill2 = useBlackAndWhite = true;
		else if (!(autoThreshold||noArgMacro)) {
			GenericDialog gd = new GenericDialog("Make Binary");
			gd.addCheckbox("Thresholded pixels to foreground color", fill1);
			gd.addCheckbox("Remaining pixels to background color", fill2);
			gd.addMessage("");
			gd.addCheckbox("Black foreground, white background", useBW);
			gd.showDialog();
			if (gd.wasCanceled())
				{imp.unlock(); return;}
			fill1 = gd.getNextBoolean();
			fill2 = gd.getNextBoolean();
			useBW = useBlackAndWhite = gd.getNextBoolean();
		} else {
			fill1 = fill2 = true;
			convertToMask = true;
		}

		if (type!=ImagePlus.GRAY8)
			convertToByte(imp);
		ip = imp.getProcessor();
		
		if (autoThreshold)
			autoThreshold(ip);
		else {
			if (Recorder.record && !Recorder.scriptMode() && (!IJ.isMacro()||Recorder.recordInMacros))
				Recorder.record("//setThreshold", (int)saveMinThreshold, (int)saveMaxThreshold);
 			minThreshold = saveMinThreshold;
 			maxThreshold = saveMaxThreshold;
		}

		if (convertToMask && ip.isColorLut())
			ip.setColorModel(ip.getDefaultColorModel());
		int fcolor, bcolor;
		ip.resetThreshold();
		int savePixel = ip.getPixel(0,0);
		if (useBlackAndWhite)
 			ip.setColor(Color.black);
 		else
 			ip.setColor(Toolbar.getForegroundColor());
		ip.drawPixel(0,0);
		fcolor = ip.getPixel(0,0);
		if (useBlackAndWhite)
 			ip.setColor(Color.white);
 		else
 			ip.setColor(Toolbar.getBackgroundColor());
		ip.drawPixel(0,0);
		bcolor = ip.getPixel(0,0);
		ip.setColor(Toolbar.getForegroundColor());
		ip.putPixel(0,0,savePixel);

		int[] lut = new int[256];
		for (int i=0; i<256; i++) {
			if (i>=minThreshold && i<=maxThreshold)
				lut[i] = fill1?fcolor:(byte)i;
			else {
				lut[i] = fill2?bcolor:(byte)i;
			}
		}
		if (imp.getStackSize()>1)
			new StackProcessor(imp.getStack(), ip).applyTable(lut);
		else
			ip.applyTable(lut);
		if (convertToMask) {
			if (!imp.isInvertedLut()) {
				setInvertedLut(imp);
				fcolor = 255 - fcolor;
				bcolor = 255 - bcolor;
			}
			if (Prefs.blackBackground)
				ip.invertLut();
		}
		if (fill1 && fill2 && ((fcolor==0&&bcolor==255)||(fcolor==255&&bcolor==0)))
			imp.getProcessor().setThreshold(fcolor, fcolor, ImageProcessor.NO_LUT_UPDATE);
		imp.updateAndRepaintWindow();
		imp.unlock();
	}
	
	void applyShortOrFloatThreshold(ImagePlus imp) {
		if (!imp.lock()) return;
		int width = imp.getWidth();
		int height = imp.getHeight();
		int size = width*height;
		boolean isFloat = imp.getType()==ImagePlus.GRAY32;
		int nSlices = imp.getStackSize();
		ImageStack stack1 = imp.getStack();
		ImageStack stack2 = new ImageStack(width, height);
		ImageProcessor ip = imp.getProcessor();
		float t1 = (float)ip.getMinThreshold();
		float t2 = (float)ip.getMaxThreshold();
		if (t1==ImageProcessor.NO_THRESHOLD) {
			//ip.resetMinAndMax();
			double min = ip.getMin();
			double max = ip.getMax();
			ip = ip.convertToByte(true);
			autoThreshold(ip);
			t1 = (float)(min + (max-min)*(minThreshold/255.0));
			t2 = (float)(min + (max-min)*(maxThreshold/255.0));
		}
		float value;
		ImageProcessor ip1, ip2;
		IJ.showStatus("Converting to mask");
		for(int i=1; i<=nSlices; i++) {
			IJ.showProgress(i, nSlices);
			String label = stack1.getSliceLabel(i);
			ip1 = stack1.getProcessor(i);
			ip2 = new ByteProcessor(width, height);
			for (int j=0; j<size; j++) {
				value = ip1.getf(j);
				if (value>=t1 && value<=t2)
					ip2.set(j, 255);
				else
					ip2.set(j, 0);
			}
			stack2.addSlice(label, ip2);
		}
		imp.setStack(null, stack2);
		ImageStack stack = imp.getStack();
		stack.setColorModel(LookUpTable.createGrayscaleColorModel(!Prefs.blackBackground));
		imp.setStack(null, stack);
		if (imp.isComposite()) {
			CompositeImage ci = (CompositeImage)imp;
			ci.setMode(IJ.GRAYSCALE);
			ci.resetDisplayRanges();
			ci.updateAndDraw();
		}
		imp.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
		IJ.showStatus("");
		imp.unlock();
	}

	void convertStackToBinary(ImagePlus imp) {
		int nSlices = imp.getStackSize();
		double[] minValues = listThresholds?new double[nSlices]:null;
		double[] maxValues = listThresholds?new double[nSlices]:null;
		int bitDepth = imp.getBitDepth();
		if (bitDepth!=8) {
			IJ.showStatus("Converting to byte");
			ImageStack stack1 = imp.getStack();
			ImageStack stack2 = new ImageStack(imp.getWidth(), imp.getHeight());
			for (int i=1; i<=nSlices; i++) {
				IJ.showProgress(i, nSlices);
				String label = stack1.getSliceLabel(i);
				ImageProcessor ip = stack1.getProcessor(i);
				ip.resetMinAndMax();
				if (listThresholds) {
					minValues[i-1] = ip.getMin();
					maxValues[i-1] = ip.getMax();
				}
				stack2.addSlice(label, ip.convertToByte(true));
			}
			imp.setStack(null, stack2);
		}
		ImageStack stack = imp.getStack();
		IJ.showStatus("Auto-thresholding");
		if (listThresholds)
			IJ.log("Thresholding method: "+method);
		for (int i=1; i<=nSlices; i++) {
			IJ.showProgress(i, nSlices);
			ImageProcessor ip = stack.getProcessor(i);
			if (method.equals("Default") && background.equals("Default"))
				ip.setAutoThreshold(ImageProcessor.ISODATA2, ImageProcessor.NO_LUT_UPDATE);
			else
				ip.setAutoThreshold(method, !background.equals("Light"), ImageProcessor.NO_LUT_UPDATE);
			minThreshold = ip.getMinThreshold();
			maxThreshold = ip.getMaxThreshold();
			if (listThresholds) {
				double t1 = minThreshold;
				double t2 = maxThreshold;
				if (bitDepth!=8) {
					t1 = minValues[i-1] + (t1/255.0)*(maxValues[i-1]-minValues[i-1]);
					t2 = minValues[i-1] + (t2/255.0)*(maxValues[i-1]-minValues[i-1]);
				}
				int digits = bitDepth==32?2:0;
				IJ.log("  "+i+": "+IJ.d2s(t1,digits)+"-"+IJ.d2s(t2,digits));
			}
			int[] lut = new int[256];
			for (int j=0; j<256; j++) {
				if (j>=minThreshold && j<=maxThreshold)
					lut[j] = (byte)255;
				else
					lut[j] = 0;
			}
			ip.applyTable(lut);
		}
		stack.setColorModel(LookUpTable.createGrayscaleColorModel(!Prefs.blackBackground));
		imp.setStack(null, stack);
		imp.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
		if (imp.isComposite()) {
			CompositeImage ci = (CompositeImage)imp;
			ci.setMode(IJ.GRAYSCALE);
			ci.resetDisplayRanges();
			ci.updateAndDraw();
		}
		IJ.showStatus("");
	}

	void convertToByte(ImagePlus imp) {
		ImageProcessor ip;
		int currentSlice =  imp.getCurrentSlice();
		ImageStack stack1 = imp.getStack();
		ImageStack stack2 = imp.createEmptyStack();
		int nSlices = imp.getStackSize();
		String label;
		for(int i=1; i<=nSlices; i++) {
			label = stack1.getSliceLabel(i);
			ip = stack1.getProcessor(i);
			ip.setMinAndMax(0, 255);
			stack2.addSlice(label, ip.convertToByte(true));
		}
		imp.setStack(null, stack2);
		imp.setSlice(currentSlice);
		imp.setCalibration(imp.getCalibration()); //update calibration
	}
	
	void setInvertedLut(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		ip.invertLut();
		int nImages = imp.getStackSize();
		if (nImages==1)
			ip.invert();
		else {
			ImageStack stack = imp.getStack();
			for (int slice=1; slice<=nImages; slice++)
				stack.getProcessor(slice).invert();
			stack.setColorModel(ip.getColorModel());
		}
	}

	void autoThreshold(ImageProcessor ip) {
		ip.setAutoThreshold(ImageProcessor.ISODATA2, ImageProcessor.NO_LUT_UPDATE);
		minThreshold = ip.getMinThreshold();
		maxThreshold = ip.getMaxThreshold();
		if (IJ.debugMode) IJ.log("Thresholder: "+minThreshold+"-"+maxThreshold);
 	}
 	
 	public static void setMethod(String method) {
 		staticMethod = method;
 	}

 	public static void setBackground(String background) {
 		staticBackground = background;
 	}

	public void itemStateChanged(ItemEvent e) {
		if (imp==null)
			return;
		Choice choice = (Choice)e.getSource();
		if (choice==choices.elementAt(0))
			method = choice.getSelectedItem();
		else
			background = choice.getSelectedItem();
		updateThreshold(imp);
	}
	
	private void updateThreshold(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		if (method.equals("Default") && background.equals("Default"))
			ip.setAutoThreshold(ImageProcessor.ISODATA2, ImageProcessor.RED_LUT);
		else
			ip.setAutoThreshold(method, !background.equals("Light"), ImageProcessor.RED_LUT);
		imp.updateAndDraw();
	}

}
