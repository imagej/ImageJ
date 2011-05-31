package ij.plugin;
import ij.*;
import ij.gui.*;
import java.awt.*;

/** This plugin implements the rounded rectangle tool dialog box. */
public class RectToolOptions implements PlugIn, DialogListener {
	private String strokeColorName, fillColorName;
	private static GenericDialog gd;
	private static double defaultStrokeWidth = 2.0;

 	public void run(String arg) {
 		if (gd!=null && gd.isVisible())
 			gd.toFront();
 		else
			rectToolOptions();
	}
				
	void rectToolOptions() {
		Color strokeColor = Toolbar.getForegroundColor();
		Color fillColor = null;
		double strokeWidth = defaultStrokeWidth;
		int cornerDiameter = (int)Prefs.get(Toolbar.ARC_SIZE, 20);
		ImagePlus imp = WindowManager.getCurrentImage();
		Roi roi = imp!=null?imp.getRoi():null;
		if (roi!=null && (roi.getType()==Roi.RECTANGLE)) {
			strokeColor = roi.getStrokeColor();
			if (strokeColor==null)
				strokeColor = Roi.getColor();
			fillColor = roi.getFillColor();
			strokeWidth = roi.getStrokeWidth();
			cornerDiameter = roi.getCornerDiameter();
		}
		String strokec = Colors.colorToString(strokeColor);
		String fillc = Colors.colorToString(fillColor);

		gd = new NonBlockingGenericDialog("Rounded Rectangle Tool");
		gd.addSlider("Stroke width:", 1, 25, (int)strokeWidth);
		gd.addSlider("Corner diameter:", 0, 200, cornerDiameter);
		gd.addStringField("Stroke color: ", strokec);
		gd.addStringField("Fill color: ", fillc);
		gd.addDialogListener(this);
		gd.showDialog();
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		double strokeWidth2 = gd.getNextNumber();
		int cornerDiameter2 = (int)gd.getNextNumber();
		String strokec2 = gd.getNextString();
		String fillc2 = gd.getNextString();
		ImagePlus imp = WindowManager.getCurrentImage();
		Roi roi = imp!=null?imp.getRoi():null;
		if (roi!=null && (roi.getType()==Roi.RECTANGLE)) {
			roi.setStrokeWidth((int)strokeWidth2);
			roi.setCornerDiameter((int)(cornerDiameter2));
			Color strokeColor = Colors.decode(strokec2, roi.getStrokeColor());
			Color fillColor = Colors.decode(fillc2, roi.getFillColor());
			roi.setStrokeColor(strokeColor);
			roi.setFillColor(fillColor);
		}
		defaultStrokeWidth = strokeWidth2;
		Toolbar.setRoundRectArcSize(cornerDiameter2);
		if (cornerDiameter2>0) {
			if (!Toolbar.getToolName().equals("roundrect"))
				IJ.setTool("roundrect");
		} else {
			if (!Toolbar.getToolName().equals("rectangle"))
				IJ.setTool("rectangle");
		}
		return true;
	}
	
	public static float getDefaultStrokeWidth() {
		return (float)defaultStrokeWidth;
	}
	
} 
