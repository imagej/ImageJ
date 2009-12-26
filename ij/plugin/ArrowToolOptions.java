package ij.plugin;
import ij.*;
import ij.gui.*;
import java.awt.*;

/** This plugin implements the Edit/Options/Arrow Tool command. */
public class ArrowToolOptions implements PlugIn, DialogListener {
	private double width;
	private String colorName;
	private static GenericDialog gd;

 	public void run(String arg) {
 		if (gd!=null && gd.isVisible())
 			gd.toFront();
 		else
			arrowToolOptions();
	}
				
	void arrowToolOptions() {
		double width = Arrow.getDefaultWidth();
		int digits = (int)width==width?0:1;
		Color color = Toolbar.getForegroundColor();
		colorName = Colors.getColorName(color, "red");
		gd = new NonBlockingGenericDialog("Arrow Tool");
		gd.addSlider("Width:", 1, 50, width);
		gd.addChoice("Color:", Colors.colors, colorName);
		gd.addDialogListener(this);
		gd.showDialog();
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		width = gd.getNextNumber();
		String colorName2 = gd.getNextChoice();
		if (colorName!=null && !colorName2.equals(colorName)) {
			Color color = Colors.getColor(colorName2, Color.black);
			Toolbar.setForegroundColor(color);
		}
		colorName = colorName2;
		updateArrowWidth();
		return true;
	}

	void updateArrowWidth() {
		Arrow.setDefaultWidth(width);
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) return;
		Roi roi = imp.getRoi();
		if (roi==null) return;
		if (roi instanceof Arrow) {
			roi.setStrokeWidth((float)width);
			imp.draw();
		}
	}
	
} 
