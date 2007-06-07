package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.io.*;
import ij.plugin.filter.*;
import java.awt.*;

/** This plugin implements most of the commands
	in the Edit/Options sub-menu. */
public class Options implements PlugIn {

 	public void run(String arg) {
		if (arg.equals("misc"))
			{miscOptions(); return;}
		else if (arg.equals("width"))
			{lineWidth(); return;}
		else if (arg.equals("quality"))
			{jpegQuality(); return;}
		else if (arg.equals("cross"))
			{cross(); return;}
		else if (arg.equals("conv"))
			{conversions(); return;}
		else if (arg.equals("image"))
			{imageOptions(); return;}
	}
				
	// Miscellaneous Options
	void miscOptions() {
		GenericDialog gd = new GenericDialog("Miscellaneous Options", IJ.getInstance());
		gd.addStringField("Divide by Zero Value:", ""+FloatBlitter.divideByZeroValue, 10);
		gd.addCheckbox("Use Pointer Cursor", Prefs.usePointerCursor);
		gd.addCheckbox("Hide \"Process Stack?\" Dialog", IJ.hideProcessStackDialog);
		gd.addCheckbox("Antialiased Text", Prefs.antialiasedText);
		gd.addCheckbox("Open/Save Using JFileChooser", Prefs.useJFileChooser);
		gd.addCheckbox("Debug Mode", IJ.debugMode);
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
			try {f = new Float(divValue);}
			catch (NumberFormatException e) {f = null;}
			if (f!=null)
				FloatBlitter.divideByZeroValue = f.floatValue();
		}
			
		Prefs.usePointerCursor = gd.getNextBoolean();
		IJ.hideProcessStackDialog = gd.getNextBoolean();
		Prefs.antialiasedText = gd.getNextBoolean();
		Prefs.useJFileChooser = gd.getNextBoolean();
		IJ.debugMode = gd.getNextBoolean();

		if (!IJ.isJava2())
			Prefs.useJFileChooser = false;
	}

	void lineWidth() {
		int width = (int)IJ.getNumber("Line Width:", Line.getWidth());
		if (width==IJ.CANCELED) return;
		Line.setWidth(width);
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null && imp.isProcessor()) {
			ImageProcessor ip = imp.getProcessor();
			ip.setLineWidth(Line.getWidth());
		}
	}

	void jpegQuality() {
		int quality = (int)IJ.getNumber("JPEG quality (0-100):", JpegWriter.getQuality());
		if (quality==IJ.CANCELED) return;
		JpegWriter.setQuality(quality);
		return;
	}

	// Cross hair mark width
	void cross() {
		int width = (int)IJ.getNumber("Mark Width:", Analyzer.markWidth);
		if (width==IJ.CANCELED) return;
		Analyzer.markWidth = width;
		return;
	}

	// Conversion Options
	void conversions() {
		double[] weights = ColorProcessor.getWeightingFactors();
		boolean unweighted = weights[0]==1d/3d && weights[1]==1d/3d && weights[2]==1d/3d;
		GenericDialog gd = new GenericDialog("Conversion Options");
		gd.addCheckbox("Scale When Converting", ImageConverter.getDoScaling());
		gd.addCheckbox("Unweighted RGB to Grayscale Conversion", unweighted);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		ImageConverter.setDoScaling(gd.getNextBoolean());
		Prefs.unweightedColor = gd.getNextBoolean();
		if (Prefs.unweightedColor)
			ColorProcessor.setWeightingFactors(1d/3d, 1d/3d, 1d/3d);
		else if (unweighted)
			ColorProcessor.setWeightingFactors(0.299, 0.587, 0.114);
		return;
	}
		
	void imageOptions() {
		GenericDialog gd = new GenericDialog("Image Options", IJ.getInstance());
		gd.addCheckbox("Interpolate Images <100%", Prefs.interpolateScaledImages);
		gd.addCheckbox("Open Images at 100%", Prefs.open100Percent);
		gd.addCheckbox("Black Canvas", Prefs.blackCanvas);
		gd.showDialog();
		if (gd.wasCanceled())
			return;			
		boolean interpolate = gd.getNextBoolean();
		Prefs.open100Percent = gd.getNextBoolean();
		boolean blackCanvas = gd.getNextBoolean();
		if (interpolate!=Prefs.interpolateScaledImages) {
			Prefs.interpolateScaledImages = interpolate;
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null)
				imp.draw();
		}
		if (blackCanvas!=Prefs.blackCanvas) {
			Prefs.blackCanvas = blackCanvas;
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) {
				ImageWindow win = imp.getWindow();
				if (win!=null) {
					if (Prefs.blackCanvas) {
						win.setForeground(Color.white);
						win.setBackground(Color.black);
					} else {
						win.setForeground(Color.black);
						win.setBackground(Color.white);
					}
					imp.repaintWindow();
				}
			}
		}
	}

}