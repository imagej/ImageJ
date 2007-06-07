package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.io.*;
import ij.plugin.filter.*;

/** This plugin implements most of the commands
	in the Edit/Options sub-menu. */
public class Options implements PlugIn {

 	public void run(String arg) {
 	
		IJ.register(Options.class);
		
		if (arg.equals("misc")) {
			GenericDialog gd = new GenericDialog("Miscellaneous Options", IJ.getInstance());
			gd.addNumericField("Real Histogram Bins:", HistogramWindow.nBins, 0);
			gd.addStringField("Divide by Zero Value:", ""+FloatBlitter.divideByZeroValue, 10);
			gd.addCheckbox("Use Pointer Cursor", ImageCanvas.usePointer);
			gd.addCheckbox("Scale When Converting", ImageConverter.getDoScaling());
			gd.addCheckbox("Hide \"Process Stack?\" Dialog", IJ.hideProcessStackDialog);
			gd.addCheckbox("Debug Mode", IJ.debugMode);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
				
			int nBins = (int)gd.getNextNumber();
			if (nBins>=2 && nBins<=1000)
				HistogramWindow.nBins = nBins;
			
			String divValue = gd.getNextString();
			Float f;
			try {f = new Float(divValue);}
			catch (NumberFormatException e) {f = null;}
			if (f!=null)
				FloatBlitter.divideByZeroValue = f.floatValue();
				
			ImageCanvas.usePointer = gd.getNextBoolean();
			ImageConverter.setDoScaling(gd.getNextBoolean());
			IJ.hideProcessStackDialog = gd.getNextBoolean();
			IJ.debugMode = gd.getNextBoolean();
			return;
		}
		
		if (arg.equals("width")) {
			int width = (int)IJ.getNumber("Line Width:", Line.getWidth());
			if (width==IJ.CANCELED) return;
			Line.setWidth(width);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null && imp.isProcessor()) {
				ImageProcessor ip = imp.getProcessor();
				ip.setLineWidth(Line.getWidth());
			}
			return;
		}
	
		if (arg.equals("quality")) {
			int quality = (int)IJ.getNumber("JPEG quality (0-100):", JpegEncoder.getQuality());
			if (quality==IJ.CANCELED) return;
			JpegEncoder.setQuality(quality);
			return;
		}

		if (arg.equals("calc")) {
			String value = IJ.getString("Real Divide by Zero Value:", ""+FloatBlitter.divideByZeroValue);
			if (value.equals("")) return;
			Float f;
			try {f = new Float(value);}
			catch (NumberFormatException e) {f = null;}
			if (f!=null)
				FloatBlitter.divideByZeroValue = f.floatValue();
			return;
		}
		
		if (arg.equals("cross")) {
			int width = (int)IJ.getNumber("Mark Width:", Analyzer.markWidth);
			if (width==IJ.CANCELED) return;
			Analyzer.markWidth = width;
			return;
		}
	
	}

}