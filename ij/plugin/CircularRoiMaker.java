package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.measure.Calibration;
import java.awt.*;

/** This class implements the Process/FFT/Make Circular Selection command. */
public class CircularRoiMaker implements PlugIn, DialogListener {
	private static double saveRadius;
	private double xcenter, ycenter, radius;
	private boolean bAbort;
	private ImagePlus imp;
	private Calibration cal;

	public void run(String arg) {
		imp = IJ.getImage();
		cal = imp.getCalibration();
		int width = imp.getWidth();
		int height = imp.getHeight();
		xcenter = width/2;
		ycenter = height/2;
		boolean macro = Macro.getOptions()!=null;
		radius = !macro&&saveRadius!=0.0?saveRadius:width/4;
		if (radius>width/2)
			radius = width/2;
		if (radius>height/2)
			radius = height/2;
		showDialog();
		if (!macro)
			saveRadius = radius;

	}
	
	private void showDialog() {
		Roi roi = imp.getRoi();
		drawRoi();
		GenericDialog gd = new GenericDialog("Circular ROI");
		gd.addSlider("Radius:", 0, imp.getWidth()/2, radius);
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
			if (roi==null)
				imp.deleteRoi();
			 else // restore initial ROI when cancelled
				imp.setRoi(roi);
		}
	}
	
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		radius = gd.getNextNumber();
		if (gd.invalidNumber())
			return false;
		drawRoi();
		return true;
	}
	
	private void drawRoi() {
		double x = xcenter - radius;
		double y = ycenter - radius;
		Roi roi = new OvalRoi(x, y, radius*2.0, radius*2.0);
		imp.setRoi(roi);
		showRadius();
	}
		
	private void showRadius() {
		String units = cal.getUnits();
		String s = " radius = ";
		if (imp.getProperty("FHT")!=null) {
			int width = imp.getWidth();
			if (radius<1.0)
				s += "Infinity/c";
			else if (cal.scaled()) 
				s += IJ.d2s((width/radius)*cal.pixelWidth,2) + " " + units + "/c";
		   else
				s += IJ.d2s(width/radius,2) + " p/c";
		} else {
			int digits = cal.pixelWidth==1.0?0:2;
			s +=  IJ.d2s(radius*cal.pixelWidth,digits)+" "+units;
		}
		IJ.showStatus(s);
	}

}
