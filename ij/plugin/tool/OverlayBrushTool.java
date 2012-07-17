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
	private final static int UNKNOWN=0, HORIZONTAL=1, VERTICAL=2, DO_RESIZE=3, RESIZED=4; //mode flags
	private static String WIDTH_KEY = "obrush.width";
	private float width = (float)Prefs.get(WIDTH_KEY, 5);
	private BasicStroke stroke = new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
	private GeneralPath path;
	private int mode;  //resizing brush or motion constrained horizontally or vertically
	private double xStart, yStart;
	private float oldWidth = width;
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
		xStart = x;
		yStart = y;
		oldWidth = width;
		mode = UNKNOWN;
		int resizeMask = InputEvent.SHIFT_MASK | (IJ.isMacintosh() ? InputEvent.META_MASK : InputEvent.CTRL_MASK);
		if ((e.getModifiers() & resizeMask) == resizeMask)
			mode = DO_RESIZE;
		newPath = true;
	}

	public void mouseDragged(ImagePlus imp, MouseEvent e) {
		ImageCanvas ic = imp.getCanvas();
		double x = ic.offScreenXD(e.getX());
		double y = ic.offScreenYD(e.getY());
		Overlay overlay = imp.getOverlay();
		if (overlay==null)
			overlay = new Overlay();
		if (mode == DO_RESIZE || mode == RESIZED) {
			changeBrushSize((float)(x-xStart), imp);
			return;
		}
		if ((e.getModifiers() & InputEvent.SHIFT_MASK) != 0) { //still shift down?
			if (mode == UNKNOWN) {
				if (Math.abs(x-xStart) > Math.abs(y-yStart))
					mode = HORIZONTAL;
				else if (Math.abs(x-xStart) < Math.abs(y-yStart))
					mode = VERTICAL;
				else return; //constraint direction still unclear
			}
			if (mode == HORIZONTAL)
				y = yStart;
			else if (mode == VERTICAL)
				x = xStart;
		} else {
			xStart = x;
			yStart = y;
			mode = UNKNOWN;
		}
		path.lineTo(x, y);
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

	public void mouseReleased(ImagePlus imp, MouseEvent e) {
		if (mode != RESIZED) return;
		Overlay overlay = imp.getOverlay();
		overlay.remove(overlay.size()-1); //delete brush resizing circle
		imp.setOverlay(overlay);
		Prefs.set(WIDTH_KEY, width);
		stroke = new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
	}

	private void changeBrushSize(float deltaWidth, ImagePlus imp) {
		if (deltaWidth==0) return;
		Overlay overlay = imp.getOverlay();
		width = oldWidth + deltaWidth;
		if (width < 0) width = 0;
		Roi circle = new OvalRoi(xStart-width/2, yStart-width/2, width, width);
		overlay = imp.getOverlay();
		if (overlay==null)
			overlay = new Overlay();
		if (mode == RESIZED)
			overlay.remove(overlay.size()-1);
		overlay.add(circle);
		imp.setOverlay(overlay);
		IJ.showStatus("Overlay Brush width: "+IJ.d2s(width));
		mode = RESIZED;
	}

	public void showOptionsDialog() {
		thread = new Thread(this, "Brush Options");
		thread.setPriority(Thread.NORM_PRIORITY);
		thread.start();
		if (IJ.debugMode) IJ.log("Options.show: "+dialogShowing);
	}

	public String getToolName() {
		return "Overlay Brush Tool";
	}

	public String getToolIcon() {
		return "C037La077Ld098L6859L4a2fL2f4fL3f99L5e9bL9b98L6888L5e8dL888cC123P2f7f9ebdcaf70";
	}

	public void run() {
		new Options();
	}

	class Options implements DialogListener {

		Options() {
			if (dialogShowing)
				return;
			dialogShowing = true;
			if (IJ.debugMode) IJ.log("Options: true");
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
			gd.addMessage("Also set the color using Color Picker (shift-k)\n"+
					"Shift-drag for horizontal or vertical lines\n"+
					(IJ.isMacintosh()? "Cmd":"Ctrl")+"-shift-drag to change brush width");
			gd.hideCancelButton();
			gd.addHelp("");
			gd.setHelpLabel("Undo");
			gd.setOKLabel("Close");
			gd.addDialogListener(this);
			gd.showDialog();
			if (IJ.debugMode) IJ.log("Options: false");
			dialogShowing = false;
		}

		public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
			if (e!=null && e.toString().contains("Undo")) {
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
