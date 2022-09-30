package ij.plugin;
import ij.*;
import ij.gui.*;
import java.awt.*;

/** This plugin implements the Edit/Options/Arrow Tool command. */
public class ArrowToolOptions implements PlugIn, DialogListener {
	private String colorName;
	private static GenericDialog gd;
	private static final String LOC_KEY = "arrows.loc";

 	public void run(String arg) {
 		if (gd!=null && gd.isVisible())
 			gd.toFront();
 		else
			arrowToolOptions();
	}
				
	void arrowToolOptions() {
		if (!Toolbar.getToolName().equals("arrow"))
			IJ.setTool("arrow");
		double width = Arrow.getDefaultWidth();
		double headSize = Arrow.getDefaultHeadSize();
		Color color = Toolbar.getForegroundColor();
		colorName = Colors.colorToString2(color);
		int style = Arrow.getDefaultStyle();
		gd = GUI.newNonBlockingDialog("Arrow Tool");
		gd.addSlider("Width:", 1, 50, (int)width);
		gd.addSlider("Size:", 0, 50, headSize);
		gd.addChoice("Color:", Colors.getColors(colorName), colorName);
		gd.addChoice("Style:", Arrow.styles, Arrow.styles[style]);
		gd.addCheckbox("Outline", Arrow.getDefaultOutline());
		gd.addCheckbox("Double head", Arrow.getDefaultDoubleHeaded());
		gd.addCheckbox("Keep after adding to overlay", Prefs.keepArrowSelections);
		gd.addDialogListener(this);
		Point loc = Prefs.getLocation(LOC_KEY);
		if (loc!=null) {
			gd.centerDialog(false);
			gd.setLocation (loc);
		}
		gd.showDialog();
		Prefs.saveLocation(LOC_KEY, gd.getLocation());
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		double width2 = gd.getNextNumber();
		double headSize2 = gd.getNextNumber();
		String colorName2 = gd.getNextChoice();
		int style2 = gd.getNextChoiceIndex();
		boolean outline2 = gd.getNextBoolean();
		boolean doubleHeaded2 = gd.getNextBoolean();
		Prefs.keepArrowSelections = gd.getNextBoolean();
		if (colorName!=null && !colorName2.equals(colorName)) {
			Color color = Colors.decode(colorName2, null);
			Toolbar.setForegroundColor(color);
		}
		colorName = colorName2;
		Arrow.setDefaultWidth(width2);
		Arrow.setDefaultHeadSize(headSize2);
		Arrow.setDefaultStyle(style2);
		Arrow.setDefaultOutline(outline2);
		Arrow.setDefaultDoubleHeaded(doubleHeaded2);
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) return true;
		Roi roi = imp.getRoi();
		if (roi==null) return true;
		if (roi instanceof Arrow) {
			Arrow arrow = (Arrow)roi;
			roi.setStrokeWidth((float)width2);
			arrow.setHeadSize(headSize2);
			arrow.setStyle(style2);
			arrow.setOutline(outline2);
			arrow.setDoubleHeaded(doubleHeaded2);
			imp.draw();
		}
		Prefs.set(Arrow.STYLE_KEY, style2);
		Prefs.set(Arrow.WIDTH_KEY, width2);
		Prefs.set(Arrow.SIZE_KEY, headSize2);
		Prefs.set(Arrow.OUTLINE_KEY, outline2);
		Prefs.set(Arrow.DOUBLE_HEADED_KEY, doubleHeaded2);
		return true;
	}
	
} 
