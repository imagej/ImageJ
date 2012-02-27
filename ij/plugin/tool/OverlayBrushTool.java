package ij.plugin.tool;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.Colors;
import java.awt.*;
import java.awt.event.*;
import java.awt.BasicStroke;
import java.awt.geom.*;

public class OverlayBrushTool extends PlugInTool implements Runnable {
	private static String WIDTH_KEY = "obrush.width";
	private float width = (float)Prefs.get(WIDTH_KEY, 5);
	private BasicStroke stroke = new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
	private GeneralPath path;
	private boolean newPath;
	private int transparency;
	private Thread thread;
	private boolean dialogShowing;

	public void mousePressed(ImagePlus imp, MouseEvent e) {
		ImageCanvas ic = imp.getCanvas();
		float x = (float)ic.offScreenXD(e.getX());
		float y = (float)ic.offScreenYD(e.getY());
		path = new GeneralPath();
		path.moveTo(x, y);
		newPath = true;
	}

	public void mouseDragged(ImagePlus imp, MouseEvent e) {
		ImageCanvas ic = imp.getCanvas();
		double x = ic.offScreenXD(e.getX());
		double y = ic.offScreenYD(e.getY());
		path.lineTo(x, y);
		Overlay overlay = imp.getOverlay();
		if (overlay==null)
			overlay = new Overlay();
		ShapeRoi roi = new ShapeRoi(path);
		Color color = Toolbar.getForegroundColor();
		float red = (float)(color.getRed()/255.0);
		float green = (float)(color.getGreen()/255.0);
		float blue = (float)(color.getBlue()/255.0);
		float alpha = (float)((100-transparency)/100.0);
		roi.setStrokeColor(new Color(red, green, blue, alpha));
		roi.setStroke(stroke);
		if (newPath) {
			overlay.add(roi);
			newPath = false;
		} else {
			overlay.remove(overlay.size()-1);
			overlay.add(roi);
		}
		imp.setOverlay(overlay);
	}

	public void showOptionsDialog() {
		thread = new Thread(this, "Brush Options");
		thread.setPriority(Thread.NORM_PRIORITY);
		thread.start();
	}

	public String getToolName() {
		return "Overlay Brush Tool";
	}

	public String getToolIcon() {
		return "C037La077Ld098L6859L4a2fL2f4fL3f99L5e9bL9b98L6888L5e8dL888cCf64P2f7f9ebdcaf70P2e7e9dbcc9f60";
	}

	public void run() {
		new Options();
	}

	class Options implements DialogListener {

		Options() {
			if (dialogShowing)
				return;
			dialogShowing = true;
			showDialog();
		}

		public void showDialog() {
			Color color = Toolbar.getForegroundColor();
			String colorName = Colors.getColorName(color, "red");
			GenericDialog gd = new NonBlockingGenericDialog("Overlay Brush Options");
			gd.addSlider("Brush width (pixels):", 1, 50, width);
			gd.addSlider("Transparency (%):", 0, 100, transparency);
			gd.addChoice("Color:", Colors.colors, colorName);
			gd.setInsets(10, 0, 0);
			gd.addMessage("Also set the color using Color Picker (shift-k)");
			gd.hideCancelButton();
			gd.addHelp("");
			gd.setHelpLabel("Remove Last Object");
			gd.setOKLabel("Close");
			gd.addDialogListener(this);
			gd.showDialog();
			dialogShowing = false;
		}

		public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
			if (e!=null && e.toString().contains("Remove Last Object")) {
				ImagePlus imp = WindowManager.getCurrentImage();
				if (imp==null) return true;
				Overlay overlay = imp.getOverlay();
				if (overlay!=null && overlay.size()>0) {
					overlay.remove(overlay.size()-1);
					imp.draw();
				}
				return true;
			}
			width = (float)gd.getNextNumber();
			transparency = (int)gd.getNextNumber();
			String colorName = gd.getNextChoice();
			Color color = Colors.getColor(colorName, Color.black);
			Toolbar.setForegroundColor(color);
			stroke = new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
			Prefs.set(WIDTH_KEY, width);
			return true;
		}
	}

}
