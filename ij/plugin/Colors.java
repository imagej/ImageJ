package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.io.*;
import ij.plugin.filter.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;

/** This plugin implements most of the Edit/Options/Colors command. */
public class Colors implements PlugIn, ItemListener {
	public static final String[] colors = {"red","green","blue","magenta","cyan","yellow","orange","black","white"};
	private Choice fchoice, bchoice, schoice;
	private Color fc2, bc2, sc2;

 	public void run(String arg) {
 		if (arg.equals("point"))
 			pointToolOptions();
 		else
			showDialog();
	}

	void showDialog() {
		Color fc =Toolbar.getForegroundColor();
		String fname = getColorName(fc, "black");
		Color bc =Toolbar.getBackgroundColor();
		String bname = getColorName(bc, "white");
		Color sc =Roi.getColor();
		String sname = getColorName(sc, "yellow");
		Color fillc = Roi.getDefaultFillColor();
		String fillColor1 = fillc!=null?Integer.toHexString(fillc.getRGB()):"none";
		GenericDialog gd = new GenericDialog("Colors");
		gd.addChoice("Foreground:", colors, fname);
		gd.addChoice("Background:", colors, bname);
		gd.addChoice("Selection:", colors, sname);
		gd.addStringField("ROI fill color: ", fillColor1);
		Vector choices = gd.getChoices();
		fchoice = (Choice)choices.elementAt(0);
		bchoice = (Choice)choices.elementAt(1);
		schoice = (Choice)choices.elementAt(2);
		fchoice.addItemListener(this);
		bchoice.addItemListener(this);
		schoice.addItemListener(this);
		
		gd.showDialog();
		if (gd.wasCanceled()) {
			if (fc2!=fc) Toolbar.setForegroundColor(fc);
			if (bc2!=bc) Toolbar.setBackgroundColor(bc);
			if (sc2!=sc) {
				Roi.setColor(sc);
				ImagePlus imp = WindowManager.getCurrentImage();
				if (imp!=null && imp.getRoi()!=null) imp.draw();
			}
			return;
		}
		fname = gd.getNextChoice();
		bname = gd.getNextChoice();
		sname = gd.getNextChoice();
		String fillColor2 = gd.getNextString();
		if (!fillColor2.equals(fillColor1)) {
			fillc = decode(fillColor2, null);
			Roi.setDefaultFillColor(fillc);
			ImagePlus imp = WindowManager.getCurrentImage();
			Roi roi = imp!=null?imp.getRoi():null;
			if (roi!=null) {
				roi.setFillColor(fillc);
				imp.draw();
			}
		}
		fc2 = getColor(fname, Color.black);
		bc2 = getColor(bname, Color.white);
		sc2 = getColor(sname, Color.yellow);
		if (fc2!=fc) Toolbar.setForegroundColor(fc2);
		if (bc2!=bc) Toolbar.setBackgroundColor(bc2);
		if (sc2!=sc) {
			Roi.setColor(sc2);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) imp.draw();
			Toolbar.getInstance().repaint();
		}
	}
	
	public static String getColorName(Color c, String defaultName) {
		if (c==null) return defaultName;
		String name = defaultName;
		if (c.equals(Color.red)) name = colors[0];
		else if (c.equals(Color.green)) name = colors[1];
		else if (c.equals(Color.blue)) name = colors[2];
		else if (c.equals(Color.magenta)) name = colors[3];
		else if (c.equals(Color.cyan)) name = colors[4];
		else if (c.equals(Color.yellow)) name = colors[5];
		else if (c.equals(Color.orange)) name = colors[6];
		else if (c.equals(Color.black)) name = colors[7];
		else if (c.equals(Color.white)) name = colors[8];
		return name;
	}
	
	public static Color getColor(String name, Color defaultColor) {
		if (name==null) return defaultColor;
		Color c = defaultColor;
		if (name.equals(colors[0])) c = Color.red;
		else if (name.equals(colors[1])) c = Color.green;
		else if (name.equals(colors[2])) c = Color.blue;
		else if (name.equals(colors[3])) c = Color.magenta;
		else if (name.equals(colors[4])) c = Color.cyan;
		else if (name.equals(colors[5])) c = Color.yellow;
		else if (name.equals(colors[6])) c = Color.orange;
		else if (name.equals(colors[7])) c = Color.black;
		else if (name.equals(colors[8])) c = Color.white;
		return c;
	}

	public static Color decode(String hexColor, Color defaultColor) {
		Color color = getColor(hexColor, Color.gray);
		if (color==Color.gray) {
			if (hexColor.startsWith("#"))
				hexColor = hexColor.substring(1);
			int len = hexColor.length();
			if (!(len==6 || len==8))
				return defaultColor;
			float alpha = len==8?parseHex(hexColor.substring(0,2)):1f;
			if (len==8)
				hexColor = hexColor.substring(2);
			float red = parseHex(hexColor.substring(0,2));
			float green = parseHex(hexColor.substring(2,4));
			float blue = parseHex(hexColor.substring(4,6));
			color = new Color(red, green, blue, alpha);
		}
		return color;
	}

	private static float parseHex(String hex) {
		float value = 0f;
		try {value=Integer.parseInt(hex,16);}
		catch(Exception e) { }
		return value/255f;
	}

	public void itemStateChanged(ItemEvent e) {
		Choice choice = (Choice)e.getSource();
		String item = choice.getSelectedItem();
		Color color = getColor(item, Color.black);
		if (choice==fchoice)
			Toolbar.setForegroundColor(color);
		else if (choice==bchoice)
			Toolbar.setBackgroundColor(color);
		else if (choice==schoice) {
			Roi.setColor(color);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null && imp.getRoi()!=null) imp.draw();
			Toolbar.getInstance().repaint();
		}
	}
	
	// Point tool options
	void pointToolOptions() {
		boolean saveNoPointLabels = Prefs.noPointLabels;
		Color sc =Roi.getColor();
		String sname = getColorName(sc, "yellow");
		GenericDialog gd = new GenericDialog("Point Tool");
		gd.addNumericField("Mark Width:", Analyzer.markWidth, 0, 2, "pixels");
		gd.addCheckbox("Auto-Measure", Prefs.pointAutoMeasure);
		gd.addCheckbox("Auto-Next Slice", Prefs.pointAutoNextSlice);
		gd.addCheckbox("Add to ROI Manager", Prefs.pointAddToManager);
		gd.addCheckbox("Label Shift-Click Points", !Prefs.noPointLabels);
		gd.addChoice("Selection Color:", colors, sname);
		Vector choices = gd.getChoices();
		schoice = (Choice)choices.elementAt(0);
		schoice.addItemListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
			if (sc2!=sc) {
				Roi.setColor(sc);
				ImagePlus imp = WindowManager.getCurrentImage();
				if (imp!=null && imp.getRoi()!=null) imp.draw();
				Toolbar.getInstance().repaint();
			}
			return;
		}
		int width = (int)gd.getNextNumber();
		if (width<0) width = 0;
		Analyzer.markWidth = width;
		Prefs.pointAutoMeasure = gd.getNextBoolean();
		Prefs.pointAutoNextSlice = gd.getNextBoolean();
		Prefs.pointAddToManager = gd.getNextBoolean();
		Prefs.noPointLabels = !gd.getNextBoolean();
		sname = gd.getNextChoice();
		sc2 = getColor(sname, Color.yellow);
		if (Prefs.pointAutoNextSlice&&!Prefs.pointAddToManager)
			Prefs.pointAutoMeasure = true;
		if (Prefs.noPointLabels!=saveNoPointLabels) {
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) imp.draw();
		}
		if (sc2!=sc) {
			Roi.setColor(sc2);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) imp.draw();
			Toolbar.getInstance().repaint();
		}
	}

}