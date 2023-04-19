package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.io.*;
import ij.plugin.filter.*;
import ij.plugin.frame.*;
import ij.measure.ResultsTable;
import java.awt.*;

/** This plugin implements most of the commands
	in the Edit/Options sub-menu. */
public class Options implements PlugIn {

 	public void run(String arg) {
		if (arg.equals("fresh-start"))
			{freshStart(); return;}
		if (arg.equals("misc"))
			{miscOptions(); return;}
		else if (arg.equals("line"))
			{lineWidth(); return;}
		else if (arg.equals("io"))
			{io(); return;}
		else if (arg.equals("conv"))
			{conversions(); return;}
		else if (arg.equals("dicom"))
			{dicom(); return;}
		else if (arg.equals("reset"))
			{reset(); return;}
	}
				
	// Miscellaneous Options
	void miscOptions() {
		String key = IJ.isMacintosh()?"command":"control";
		GenericDialog gd = new GenericDialog("Miscellaneous Options");
		gd.addStringField("Divide by zero value:", ""+FloatBlitter.divideByZeroValue, 10);
		gd.addCheckbox("Use pointer cursor", Prefs.usePointerCursor);
		gd.addCheckbox("Hide \"Process Stack?\" dialog", IJ.hideProcessStackDialog);
		gd.addCheckbox("Require "+key+" key for shortcuts", Prefs.requireControlKey);
		gd.addCheckbox("Move isolated plugins to Misc. menu", Prefs.moveToMisc);
		if (!IJ.isMacOSX())
			gd.addCheckbox("Run single instance listener", Prefs.runSocketListener);
		gd.addCheckbox("Enhanced line tool", Prefs.enhancedLineTool);
		gd.addCheckbox("Reverse CZT order of \">\" and \"<\"", Prefs.reverseNextPreviousOrder);
		if (IJ.isMacOSX())
			gd.addCheckbox("Don't set Mac menu bar", !Prefs.setIJMenuBar);
		if (IJ.isLinux())
			gd.addCheckbox("Save window locations", !Prefs.doNotSaveWindowLocations);
		gd.addCheckbox("Non-blocking filter dialogs", Prefs.nonBlockingFilterDialogs);
		gd.addCheckbox("Debug mode", IJ.debugMode);
		//gd.addCheckbox("Modern mode", Prefs.modernMode);
		gd.addHelp(IJ.URL2+"/docs/menus/edit.html#misc");
		gd.showDialog();
		if (gd.wasCanceled())
			return;
			
		String divValue = gd.getNextString();
		if (divValue.equalsIgnoreCase("infinity") || divValue.equalsIgnoreCase("infinite"))
			FloatBlitter.divideByZeroValue = Float.POSITIVE_INFINITY;
		else if (divValue.equalsIgnoreCase("NaN"))
			FloatBlitter.divideByZeroValue = Float.NaN;
		else if (divValue.equalsIgnoreCase("max"))
			FloatBlitter.divideByZeroValue = Float.MAX_VALUE;
		else {
			Float f;
			try {f = Float.valueOf(divValue);}
			catch (NumberFormatException e) {f = null;}
			if (f!=null)
				FloatBlitter.divideByZeroValue = f.floatValue();
		}
		IJ.register(FloatBlitter.class); 
			
		Prefs.usePointerCursor = gd.getNextBoolean();
		IJ.hideProcessStackDialog = gd.getNextBoolean();
		Prefs.requireControlKey = gd.getNextBoolean();
		Prefs.moveToMisc = gd.getNextBoolean();
		if (!IJ.isMacOSX())
			Prefs.runSocketListener = gd.getNextBoolean();
		Prefs.enhancedLineTool = gd.getNextBoolean();
		Prefs.reverseNextPreviousOrder = gd.getNextBoolean();
		if (IJ.isMacOSX())
			Prefs.setIJMenuBar = !gd.getNextBoolean();
		if (IJ.isLinux())
			Prefs.doNotSaveWindowLocations = !gd.getNextBoolean();
		Prefs.nonBlockingFilterDialogs = gd.getNextBoolean();
		IJ.setDebugMode(gd.getNextBoolean());
		//Prefs.modernMode = gd.getNextBoolean();
	}

	void lineWidth() {
		GenericDialog gd = new GenericDialog("Default Line Width");
		gd.addNumericField("Line width: ", Line.getWidth(), 0);
		gd.setInsets(5,2,0);
		gd.addMessage("Sets the default line selection width.\nPress 'y' (Edit>Selection>Properties)\nto change the width of the current\nline selection.");
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int width = (int)gd.getNextNumber();
		Line.setWidth(width);
		LineWidthAdjuster.update();
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null && imp.isProcessor()) {
			ImageProcessor ip = imp.getProcessor();
			ip.setLineWidth(Line.getWidth());
            Roi roi = imp.getRoi();
            if (roi!=null && roi.isLine())
            	imp.draw();
		}
	}

	// Input/Output options
	void io() {
		GenericDialog gd = new GenericDialog("I/O Options");
		gd.addNumericField("JPEG quality (0-100):", FileSaver.getJpegQuality(), 0, 3, "");
		gd.addNumericField("GIF and PNG transparent index:", Prefs.getTransparentIndex(), 0, 3, "");
		gd.addStringField("File extension for tables (.csv, .tsv or .txt):", Prefs.defaultResultsExtension(), 4);
		gd.addCheckbox("Use JFileChooser to open/save", Prefs.useJFileChooser);
		if (!IJ.isMacOSX())
			gd.addCheckbox("Use_file chooser to import sequences", Prefs.useFileChooser);
		gd.addCheckbox("Save TIFF and raw in Intel byte order", Prefs.intelByteOrder);
		gd.addCheckbox("Skip dialog when opening .raw files", Prefs.skipRawDialog);
		
		gd.setInsets(15, 20, 0);
		gd.addMessage("Results Table Options");
		gd.setInsets(3, 40, 0);
		gd.addCheckbox("Copy_column headers", Prefs.copyColumnHeaders);
		gd.setInsets(0, 40, 0);
		gd.addCheckbox("Copy_row numbers", !Prefs.noRowNumbers);
		gd.setInsets(0, 40, 0);
		gd.addCheckbox("Save_column headers", !Prefs.dontSaveHeaders);
		gd.setInsets(0, 40, 0);
		gd.addCheckbox("Save_row numbers", !Prefs.dontSaveRowNumbers);
		
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int quality = (int)gd.getNextNumber();
		if (quality<0) quality = 0;
		if (quality>100) quality = 100;
		FileSaver.setJpegQuality(quality);
		int transparentIndex = (int)gd.getNextNumber();
		Prefs.setTransparentIndex(transparentIndex);
		String extension = gd.getNextString();
		if (!extension.startsWith("."))
			extension = "." + extension;
		Prefs.set("options.ext", extension);
		boolean useJFileChooser2 = Prefs.useJFileChooser;
		Prefs.useJFileChooser = gd.getNextBoolean();
		if (Prefs.useJFileChooser!=useJFileChooser2)
			Prefs.jFileChooserSettingChanged = true;
		if (!IJ.isMacOSX())
			Prefs.useFileChooser = gd.getNextBoolean();
		Prefs.intelByteOrder = gd.getNextBoolean();
		Prefs.skipRawDialog = gd.getNextBoolean();
		Prefs.copyColumnHeaders = gd.getNextBoolean();
		Prefs.noRowNumbers = !gd.getNextBoolean();
		Prefs.dontSaveHeaders = !gd.getNextBoolean();
		ResultsTable.getResultsTable().saveColumnHeaders(!Prefs.dontSaveHeaders);
		Prefs.dontSaveRowNumbers = !gd.getNextBoolean();
		return;
	}

	// Conversion Options
	void conversions() {
		double[] weights = ColorProcessor.getWeightingFactors();
		boolean weighted = !(weights[0]==1d/3d && weights[1]==1d/3d && weights[2]==1d/3d);
		//boolean weighted = !(Math.abs(weights[0]-1d/3d)<0.0001 && Math.abs(weights[1]-1d/3d)<0.0001 && Math.abs(weights[2]-1d/3d)<0.0001);
		GenericDialog gd = new GenericDialog("Conversion Options");
		gd.addCheckbox("Scale when converting", ImageConverter.getDoScaling());
		gd.setInsets(0,40,0);
		gd.addCheckbox("Calibrate", Prefs.calibrateConversions);
		String prompt = "Weighted RGB conversions";
		if (weighted)
			prompt += " (" + IJ.d2s(weights[0]) + "," + IJ.d2s(weights[1]) + ","+ IJ.d2s(weights[2]) + ")";
		gd.addCheckbox(prompt, weighted);
		gd.addCheckbox("Full range 16-bit inversions", Prefs.fullRange16bitInversions);
		gd.addHelp(IJ.URL2+"/docs/menus/edit.html#conversions");
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		ImageConverter.setDoScaling(gd.getNextBoolean());
		Prefs.calibrateConversions = gd.getNextBoolean();
		Prefs.weightedColor = gd.getNextBoolean();
		Prefs.fullRange16bitInversions = gd.getNextBoolean();
		if (Prefs.calibrateConversions)
			ImageConverter.setDoScaling(true);
		if (!Prefs.weightedColor)
			ColorProcessor.setWeightingFactors(1d/3d, 1d/3d, 1d/3d);
		else if (Prefs.weightedColor && !weighted)
			ColorProcessor.setWeightingFactors(0.299, 0.587, 0.114);
		return;
	}
			
	// replaced by AppearanceOptions class
	void appearance() {
	}

	// DICOM options
	void dicom() {
		GenericDialog gd = new GenericDialog("DICOM Options");
		gd.addCheckbox("Open as 32-bit float", Prefs.openDicomsAsFloat);
		gd.addCheckbox("Ignore rescale slope", Prefs.ignoreRescaleSlope);
		gd.addCheckbox("Fixed Z slope and intercept", Prefs.fixedDicomScaling);
		gd.addMessage("Orthogonal views");
		gd.setInsets(5, 40, 0);
		gd.addCheckbox("Rotate YZ", Prefs.rotateYZ);
		gd.setInsets(0, 40, 0);
		gd.addCheckbox("Flip XZ", Prefs.flipXZ);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		Prefs.openDicomsAsFloat = gd.getNextBoolean();
		Prefs.ignoreRescaleSlope = gd.getNextBoolean();
		Prefs.fixedDicomScaling = gd.getNextBoolean();
		Prefs.rotateYZ = gd.getNextBoolean();
		Prefs.flipXZ = gd.getNextBoolean();
	}
		
	/** Close all images, empty ROI Manager, clear the
		 Results table, clears the Log window and sets
		 "Black background" 'true'.
	*/
	private void freshStart() {
		String options = Macro.getOptions();
		boolean keepImages = false;
		boolean keepResults = false;
		boolean keepRois = false;
		if (options!=null) {
			options = options.toLowerCase();
			keepImages = options.contains("images");			
			keepResults = options.contains("results");			
			keepRois = options.contains("rois");
		}
		if (!keepImages) {
			if (!Commands.closeAll())
				return;
		}
		if (!keepResults) {
			if (!Analyzer.resetCounter())
				return;
		}
		if (!keepRois) {
			RoiManager rm = RoiManager.getInstance();
			if (rm!=null)
				rm.reset();
		}
		if (WindowManager.getWindow("Log")!=null)
   			IJ.log("\\Clear");
		Prefs.blackBackground = true;
	}

	// Delete preferences file when ImageJ quits
	private void reset() {
		if (IJ.showMessageWithCancel("Reset Preferences", "Preferences will be reset when ImageJ restarts."))
			Prefs.resetPreferences();
	}

} // class Options
