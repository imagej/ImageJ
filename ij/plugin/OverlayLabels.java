package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.util.Tools;
import java.awt.*;
import java.util.Vector;

/** This plugin implements the Image/Overlay/Labels command. */
public class OverlayLabels implements PlugIn, DialogListener {
	private static final String[] fontSizes = {"7", "8", "9", "10", "12", "14", "18", "24", "28", "36", "48", "72"};
	private static Overlay defaultOverlay = new Overlay();
	private ImagePlus imp;
	private Overlay overlay;
	private GenericDialog gd;
	private boolean showLabels;
	private boolean showNames;
	private boolean drawBackgrounds;
	private String colorName;
	private int fontSize;
	private boolean bold;
	
	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
		overlay = imp!=null?imp.getOverlay():null;
		if (overlay==null)
			overlay = defaultOverlay;
		showDialog();
		if (!gd.wasCanceled()) {
			defaultOverlay.drawLabels(overlay.getDrawLabels());
			defaultOverlay.drawNames(overlay.getDrawNames());
			defaultOverlay.drawBackgrounds(overlay.getDrawBackgrounds());
			defaultOverlay.setLabelColor(overlay.getLabelColor());
			defaultOverlay.setLabelFont(overlay.getLabelFont());
		}
	}
	
	public void showDialog() {
		showLabels = overlay.getDrawLabels();
		showNames = overlay.getDrawNames();
		drawBackgrounds = overlay.getDrawBackgrounds();
		colorName = Colors.getColorName(overlay.getLabelColor(), "white");
		fontSize = 12;
		Font font = overlay.getLabelFont();
		if (font!=null) {
			fontSize = font.getSize();
			bold = font.getStyle()==Font.BOLD;
		}
		gd = new GenericDialog("Labels");
		gd.addChoice("Color:", Colors.colors, colorName);
		gd.addChoice("Font size:", fontSizes, ""+fontSize);
		gd.addCheckbox("Show labels", showLabels);
		gd.addCheckbox("Use names as labels", showNames);
		gd.addCheckbox("Draw backgrounds", drawBackgrounds);
		gd.addCheckbox("Bold", bold);
		gd.addDialogListener(this);
		gd.showDialog();
	}
	
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		if (gd.wasCanceled()) return false;
		String colorName2 = colorName;
		boolean showLabels2 = showLabels;
		boolean showNames2 = showNames;
		boolean drawBackgrounds2 = drawBackgrounds;
		boolean bold2 = bold;
		int fontSize2 = fontSize;
		colorName = gd.getNextChoice();
		fontSize = (int)Tools.parseDouble(gd.getNextChoice(), 12);
		showLabels = gd.getNextBoolean();
		showNames = gd.getNextBoolean();
		drawBackgrounds = gd.getNextBoolean();
		bold = gd.getNextBoolean();
		boolean colorChanged = !colorName.equals(colorName2);
		boolean sizeChanged = fontSize!=fontSize2;
		boolean changes = showLabels!=showLabels2 || showNames!=showNames2
			|| drawBackgrounds!=drawBackgrounds2 || colorChanged || sizeChanged
			|| bold!=bold2;
		if (changes) {
			if (showNames || colorChanged || sizeChanged) {
				showLabels = true;
				Vector checkboxes = gd.getCheckboxes();
				((Checkbox)checkboxes.elementAt(0)).setState(true);
			}
			overlay.drawLabels(showLabels);
			overlay.drawNames(showNames);
			overlay.drawBackgrounds(drawBackgrounds);
			Color color = Colors.getColor(colorName, Color.white);
			overlay.setLabelColor(color);
			if (sizeChanged || bold || bold!=bold2)
				overlay.setLabelFont(new Font("SansSerif", bold?Font.BOLD:Font.PLAIN, fontSize));
			if (imp!=null && imp.getOverlay()!=null)
				imp.draw();
		}
		return true;
	}

	/** Creates an empty Overlay that has the current label settings. */
	public static Overlay createOverlay() {
		return defaultOverlay.duplicate();
	}

}
