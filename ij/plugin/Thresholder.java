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
	private boolean autoThreshold;
	private boolean showLegacyDialog = true;
	private static boolean fill1 = true;
	private static boolean fill2 = true;
	private static boolean useBW = true;
	private boolean useLocal = true;
	private boolean listThresholds;
	private boolean convertToMask;
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
		if (arg.equals("skip") || convertToMask)
			showLegacyDialog = false;
		ImagePlus imp = IJ.getImage();
		if (imp.getStackSize()==1) {
			Undo.setup(Undo.TRANSFORM, imp);
			applyThreshold(imp, false);
		} else
			convertStack(imp);
	}
	
	void convertStack(ImagePlus imp) {
		if (imp.getStack().isVirtual()) {
			IJ.error("Thresholder", "This command does not work with virtual stacks.\nUse Image>Duplicate to convert to a normal stack.");
			return;
		}
		showLegacyDialog = false;
		boolean thresholdSet = imp.isThreshold();
		this.imp = imp;
		if (!IJ.isMacro()) {
			method = staticMethod;
			background = staticBackground;
			useLocal = staticUseLocal;
			listThresholds = staticListThresholds;
			if (!thresholdSet)
				updateThreshold(imp);
		} else {
			String macroOptions = Macro.getOptions();
			if (macroOptions!=null && macroOptions.indexOf("slice ")!=-1)
				Macro.setOptions(macroOptions.replaceAll("slice ", "only "));
			useLocal = false;
		}
		boolean saveBlackBackground = Prefs.blackBackground;
		boolean oneSlice = false;
		GenericDialog gd = new GenericDialog("Convert Stack to Binary");
		gd.addChoice("Method:", methods, method);
		gd.addChoice("Background:", backgrounds, background);
		gd.addCheckbox("Calculate threshold for each image", useLocal);
		gd.addCheckbox("Only convert current image", oneSlice);
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
		oneSlice = gd.getNextBoolean();
		Prefs.blackBackground = gd.getNextBoolean();
		listThresholds = gd.getNextBoolean();
		if (!IJ.isMacro()) {
			staticMethod = method;
			staticBackground = background;
			staticUseLocal = useLocal;
			staticListThresholds = listThresholds;
		}
		if (oneSlice) {
			useLocal = false;
			listThresholds = false;
			if (oneSlice && imp.getBitDepth()!=8) {
				IJ.error("Thresholder", "8-bit stack required to process a single slice.");
				return;
			}
		}
		Undo.reset();
		if (useLocal)
			convertStackToBinary(imp);
		else
			applyThreshold(imp, oneSlice);
		Prefs.blackBackground = saveBlackBackground;
		if (thresholdSet) {
			imp.getProcessor().resetThreshold();
			imp.updateAndDraw();
		}
	}

	private void applyThreshold(ImagePlus imp, boolean oneSlice) {
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
					
		boolean useBlackAndWhite = false;
		boolean fill1 = true;
		boolean fill2 = true;
		String options = Macro.getOptions();
		boolean modernMacro = options!=null && !(options.contains("thresholded")||options.contains("remaining"));
		if (autoThreshold || modernMacro || (IJ.macroRunning()&&options==null))
			showLegacyDialog = false;
		int fcolor=255, bcolor=0;
			
		if (showLegacyDialog) {
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
		} else
			convertToMask = true;

		if (type==ImagePlus.COLOR_RGB) {
			ImageProcessor ip2 = updateColorThresholdedImage(imp);
			if (ip2!=null) {
				imp.setProcessor(ip2);
				autoThreshold =false;
				saveMinThreshold = 255;
				saveMaxThreshold = 255;
				type = ImagePlus.GRAY8;
			}
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
		ip.resetThreshold();

		if (IJ.debugMode) IJ.log("Thresholder (apply): "+minThreshold+"-"+maxThreshold+" "+fcolor+" "+bcolor+" "+fill1+" "+fill2);
		int[] lut = new int[256];
		for (int i=0; i<256; i++) {
			if (i>=minThreshold && i<=maxThreshold)
				lut[i] = fill1?fcolor:(byte)i;
			else {
				lut[i] = fill2?bcolor:(byte)i;
			}
		}
		if (imp.getStackSize()>1 && !oneSlice)
			new StackProcessor(imp.getStack(), ip).applyTable(lut);
		else
			ip.applyTable(lut);
		if (convertToMask && !oneSlice) {
			boolean invertedLut = imp.isInvertedLut();
			if ((invertedLut && Prefs.blackBackground) || (!invertedLut && !Prefs.blackBackground)) {
				ip.invertLut();
				if (IJ.debugMode) IJ.log("Thresholder (inverting lut)");
			}
		}
		if (fill1 && fill2 && ((fcolor==0&&bcolor==255)||(fcolor==255&&bcolor==0))) {
			imp.getProcessor().setThreshold(fcolor, fcolor, ImageProcessor.NO_LUT_UPDATE);
			if (IJ.debugMode) IJ.log("Thresholder: "+fcolor+"-"+fcolor+" ("+(Prefs.blackBackground?"black":"white")+" background)");
		}
		imp.updateAndRepaintWindow();
		imp.unlock();
	}
	
	private ImageProcessor updateColorThresholdedImage(ImagePlus imp) {
		Object mask = imp.getProperty("Mask");
		if (mask==null || !(mask instanceof ByteProcessor))
			return null;
		ImageProcessor maskIP = (ImageProcessor)mask;
		if (maskIP.getWidth()!=imp.getWidth() || maskIP.getHeight()!=imp.getHeight())
			return null;
		Object originalImage = imp.getProperty("OriginalImage");
		if (originalImage!=null && (originalImage instanceof ImagePlus)) {
			ImagePlus imp2 = (ImagePlus)originalImage;
			if (imp2.getBitDepth()==24 && imp2.getWidth()==imp.getWidth() && imp2.getHeight()==imp.getHeight()) {
				imp.setProcessor(imp2.getProcessor());
				Undo.setup(Undo.TRANSFORM, imp);
			}
		}
		return maskIP;
	}
	
	private void applyShortOrFloatThreshold(ImagePlus imp) {
		if (!imp.lock()) return;
		int width = imp.getWidth();
		int height = imp.getHeight();
		int size = width*height;
		boolean isFloat = imp.getType()==ImagePlus.GRAY32;
		int currentSlice =  imp.getCurrentSlice();
		int nSlices = imp.getStackSize();
		ImageStack stack1 = imp.getStack();
		ImageStack stack2 = new ImageStack(width, height);
		ImageProcessor ip = imp.getProcessor();
		float t1 = (float)ip.getMinThreshold();
		float t2 = (float)ip.getMaxThreshold();
		if (t1==ImageProcessor.NO_THRESHOLD) {
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
		for (int i=1; i<=nSlices; i++) {
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
		if (IJ.debugMode) IJ.log("Thresholder16: 255-255 ("+(Prefs.blackBackground?"black":"white")+" background)");
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
	
	/** Returns a version of the specified image that has been converted into  
	 * an 8--bit binary (0 and 255) mask, based on how it is thresholded.
	 * @see ij.process.ImageProcessor#createMask
	 * @see ij.ImagePlus#createMask
	*/
	public static ImagePlus createMask(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		if (ip instanceof ColorProcessor)
			throw new IllegalArgumentException("Non-RGB image requires");
		if (ip.getMinThreshold()==ImageProcessor.NO_THRESHOLD)
			throw new IllegalArgumentException("Image must be thresholded");
		return new ImagePlus("Mask_"+imp.getTitle(),ip.createMask());
	}
	
	void autoThreshold(ImageProcessor ip) {
		ip.setAutoThreshold(ImageProcessor.ISODATA2, ImageProcessor.NO_LUT_UPDATE);
		minThreshold = ip.getMinThreshold();
		maxThreshold = ip.getMaxThreshold();
		if (IJ.debugMode) IJ.log("Thresholder (auto): "+minThreshold+"-"+maxThreshold);
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
