package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.measure.Calibration;

public class ImageProperties implements PlugInFilter {
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		showDialog(imp);
	}
	
	void showDialog(ImagePlus imp) {
		Calibration cal = imp.getCalibration();
		GenericDialog gd = new GenericDialog(imp.getTitle());
		gd.addStringField("Unit of Measure:", cal.getUnit());
		double ppu = cal.pixelWidth!=0?1.0/cal.pixelWidth:0;
		gd.addNumericField("Pixels/Unit:", ppu, (int)ppu==ppu?0:2);
		int stackSize = imp.getStackSize();
		if (stackSize>1) {
			gd.addMessage("");
			gd.addNumericField("Slice Spacing:", cal.pixelDepth, 2);
			double fps = cal.frameInterval>0.0?1/cal.frameInterval:0.0;
			gd.addNumericField("Frames per Second:", fps, (int)fps==fps?0:2);		}
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		String unit = gd.getNextString();
		double resolution = gd.getNextNumber();
		if (unit.equals("")||unit.equalsIgnoreCase("pixel")||resolution==0.0) {
			cal.setUnit(null);
			cal.pixelWidth = 1.0;
			cal.pixelHeight = 1.0;
		} else {
			cal.setUnit(unit);
			cal.pixelWidth = 1.0/resolution;
			cal.pixelHeight = 1.0/resolution;
		}
		if (stackSize>1) {
			double spacing = gd.getNextNumber();
			double fps = gd.getNextNumber();
			cal.pixelDepth = spacing;
			if (fps!=0.0)
				cal.frameInterval = 1.0/fps;
			else
				cal.frameInterval = 0.0;
		}
		imp.repaintWindow();
	}

}
