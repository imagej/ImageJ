package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.awt.*;
import java.awt.geom.*;


/** This plugin implements the Image/Translate command. */
public class Translator implements ExtendedPlugInFilter, DialogListener {
	private int flags = DOES_ALL|PARALLELIZE_STACKS;
	private static double xOffset = 15;
	private static double yOffset = 15;
	private ImagePlus imp;
	private GenericDialog gd;
	private PlugInFilterRunner pfr;
	private static int interpolationMethod = ImageProcessor.NONE;
	private String[] methods = ImageProcessor.getInterpolationMethods();
	private boolean previewing;
	private Overlay origOverlay;
	private boolean overlayOnly;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		if (imp!=null) {
			origOverlay = imp.getOverlay();
			Undo.saveOverlay(imp);
		}
		return flags;
	}

	public void run(ImageProcessor ip) {
		ip.setInterpolationMethod(interpolationMethod);
		if (!overlayOnly || origOverlay==null)
			ip.translate(xOffset, yOffset);
		if (origOverlay!=null) {
			Overlay overlay = origOverlay.duplicate();
			overlay.translate(xOffset, yOffset);
			imp.setOverlay(overlay);
		}
	}

	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
		this.pfr = pfr;
		int digits = xOffset==(int)xOffset&&yOffset==(int)yOffset?1:3;
		if (IJ.isMacro())
			interpolationMethod = ImageProcessor.NONE;
		gd = new GenericDialog("Translate");
		gd.addSlider("X offset:", -100, 100, xOffset, 0.1);
		gd.addSlider("Y offset:", -100, 100, xOffset, 0.1);
		gd.addChoice("Interpolation:", methods, methods[interpolationMethod]);
		if (origOverlay!=null)
			gd.addCheckbox("Overlay only", false);
		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		previewing = true;
		gd.showDialog();
		if (gd.wasCanceled()) {
			imp.setOverlay(origOverlay);
			return DONE;
		}
		previewing = false;
		return IJ.setupDialog(imp, flags);
	}
	
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		xOffset = gd.getNextNumber();
		yOffset = gd.getNextNumber();
		interpolationMethod = gd.getNextChoiceIndex();
		if (origOverlay!=null)
			overlayOnly = gd.getNextBoolean();
		if (gd.invalidNumber()) {
			if (gd.wasOKed()) IJ.error("Offset is invalid.");
			return false;
		}
		return true;
	}

	public void setNPasses(int nPasses) {
	}

}

