package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import java.awt.*;

/** Implements the Analyze/Set Scale command. */
public class ScaleDialog implements PlugInFilter {

    private ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		IJ.register(ScaleDialog.class);
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		double measured = 0.0;
		double known = 1.0;
		double aspectRatio = 1.0;
		String unit = "cm";
		boolean oldGlobal = Calibrator.global;
		Calibration cal = imp.getCalibration();
		boolean isCalibrated = cal.scaled();
		
		if (isCalibrated) {
			measured = 1.0/cal.pixelWidth;
			known = 1.0;
			aspectRatio = cal.pixelHeight/cal.pixelWidth;
			unit = cal.getUnit();
		}
		Roi roi = imp.getRoi();
		if (roi!=null && (roi instanceof Line)) {
			measured = ((Line)roi).getRawLength();
			known = 0.0;
		}
		
		GenericDialog gd = new GenericDialog("Set Scale", IJ.getInstance());
		gd.addNumericField("Distance in Pixels:", measured, 2);
		gd.addNumericField("Known Distance:", known, 2);
		gd.addNumericField("Pixel Aspect Ratio:", aspectRatio, 1);
		gd.addStringField("Unit of Measurement:", unit);
		gd.addCheckbox("Global", Calibrator.global);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		measured = gd.getNextNumber();
		known = gd.getNextNumber();
		aspectRatio = gd.getNextNumber();
		unit = gd.getNextString();
		Calibrator.global = gd.getNextBoolean();
		if (measured!=0.0 && known==0.0) {
			imp.setGlobalCalibration(Calibrator.global?cal:null);
			return;
		}
		if (measured<=0.0 || unit.startsWith("pixel") || unit.startsWith("Pixel") || unit.equals("")) {
			cal.pixelWidth = 1.0;
			cal.pixelHeight = 1.0;
			cal.setUnit("pixel");
		} else {
			cal.pixelWidth = known/measured;
			if (aspectRatio!=0.0)
				cal.pixelHeight = cal.pixelWidth*aspectRatio;
			else
				cal.pixelHeight = cal.pixelWidth;
			cal.setUnit(unit);
		}
		if (oldGlobal&&!Calibrator.global)
			imp.setGlobalCalibration(null);
		else {
			imp.setCalibration(cal);
			imp.setGlobalCalibration(Calibrator.global?cal:null);
		}
		if (Calibrator.global || Calibrator.global!=oldGlobal) {
			int[] list = WindowManager.getIDList();
			if (list==null)
				return;
			for (int i=0; i<list.length; i++) {
				ImagePlus imp2 = WindowManager.getImage(list[i]);
				if (imp2!=null)
					imp2.getWindow().repaint();
			}
		} else
			imp.getWindow().repaint();
	}

}