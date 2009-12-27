package ij.plugin;
import ij.*;
import ij.gui.*;
import java.awt.*;

/** This plugin implements the Edit/Options/Arrow Tool command. */
public class ArrowToolOptions implements PlugIn, DialogListener {
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
		Color color = Toolbar.getForegroundColor();
		colorName = Colors.getColorName(color, "red");
		int style = Arrow.getDefaultStyle();
		gd = new NonBlockingGenericDialog("Arrow Tool");
		gd.addSlider("Width:", 1, 50, (int)width);
		gd.addChoice("Color:", Colors.colors, colorName);
		gd.addChoice("Style:", Arrow.styles, Arrow.styles[style]);
		gd.addDialogListener(this);
		gd.showDialog();
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		double width2 = gd.getNextNumber();
		String colorName2 = gd.getNextChoice();
		int style2 = gd.getNextChoiceIndex();
		updateArrow(width2, style2);
		if (colorName!=null && !colorName2.equals(colorName)) {
			Color color = Colors.getColor(colorName2, Color.black);
			Toolbar.setForegroundColor(color);
		}
		colorName = colorName2;
		return true;
	}

	void updateArrow(double width2, int style2) {
		Arrow.setDefaultWidth(width2);
		Arrow.setDefaultStyle(style2);
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) return;
		Roi roi = imp.getRoi();
		if (roi==null) return;
		if (roi instanceof Arrow) {
			roi.setStrokeWidth((float)width2);
			((Arrow)roi).setStyle(style2);
			imp.draw();
		}
	}
	
} 
