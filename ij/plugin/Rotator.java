package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.filter.PlugInFilter;
import java.awt.*;

/** This plugin implements the Image/Rotate/Arbitrarily command. */
public class Rotator implements PlugIn {
    private static double angle = 15.0;
    private static boolean interpolate = true;
    private static boolean fillWithBackground;
    private static boolean enlarge;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		int bitDepth = imp.getBitDepth();
		ImageProcessor ip = imp.getProcessor();
		if (!showDialog(imp, bitDepth))
			return;
		int slices = imp.getStackSize();
		boolean rotateStack = false;
		long startTime = System.currentTimeMillis();
		boolean zeroFill = false;
		if (enlarge) {
			if (slices==1)
				Undo.setup(Undo.COMPOUND_FILTER, imp);
			IJ.run("Select All");
			IJ.run("Rotate...", "angle="+angle);
			Roi roi = imp.getRoi();
			Rectangle r = roi.getBounds();
			IJ.showStatus("Enlarging...");
			IJ.run("Canvas Size...", "width="+r.width+" height="+r.height+" position=Center "+(fillWithBackground?"":"zero"));
			imp = IJ.getImage();
			ip = imp.getProcessor();
			zeroFill = true;
			if (slices>1) rotateStack = true;
		} 
		if (slices>1 && !enlarge) {
			int result = IJ.setupDialog(imp, 0);
			if (result==PlugInFilter.DONE)
				return;
			rotateStack = result==PlugInFilter.DOES_STACKS;
		}
		if (slices==1 && !enlarge) {
			ip.snapshot();
			Undo.setup(Undo.FILTER, imp);
		} else if (slices>1)
			Undo.reset();
		IJ.showStatus("Rotating...");
		if (!rotateStack) slices = 1;
		int slice = imp.getCurrentSlice();
		ImageStack stack = imp.getStack();
		for (int i=1; i<=slices; i++) {
			if (rotateStack) {
				IJ.showProgress(i, slices);
				IJ.showStatus("Rotating... ("+i+"/"+slices+")");
				ip = stack.getProcessor(i);
			}
			ip.setInterpolate(interpolate);
			if (fillWithBackground) {
				Color bgc = Toolbar.getBackgroundColor();
				if (bitDepth==8)
					ip.setBackgroundValue(ip.getBestIndex(bgc));
				else if (bitDepth==24)
					ip.setBackgroundValue(bgc.getRGB());
			} else {
				if (zeroFill)
					ip.setBackgroundValue(0);
				else {
					if (bitDepth==8 || bitDepth==24)
						ip.setColor(Color.white); 
				}
			}
			ip.rotate(angle);
		}
		if (rotateStack) {
			imp.setStack(null, stack);
			imp.setSlice(slice);
		}
		imp.updateAndDraw();
		imp.changes = true;
		if (enlarge && slices==1)
			Undo.setup(Undo.COMPOUND_FILTER_DONE, imp);
		IJ.showTime(imp, startTime, "Rotate: ");
	}
	
	boolean showDialog(ImagePlus imp, int bitDepth) {
			Roi roi = imp.getRoi();
			Rectangle r = roi!=null?roi.getBounds():null;
			boolean canEnlarge = r==null || (r.x==0&&r.y==0&&r.width==imp.getWidth()&&r.height==imp.getHeight());
			GenericDialog gd = new GenericDialog("Rotate", IJ.getInstance());
			gd.addNumericField("Angle (degrees): ", angle, 2);
			gd.addCheckbox("Interpolate", interpolate);
			if (bitDepth==8 || bitDepth==24)
				gd.addCheckbox("Fill with Background Color", fillWithBackground);
			if (canEnlarge)
				gd.addCheckbox("Enlarge Image to Fit Result", enlarge);
			else
				enlarge = false;
			gd.showDialog();
			if (gd.wasCanceled())
				return false;
			angle = gd.getNextNumber();
			if (gd.invalidNumber()) {
				IJ.error("Angle is invalid.");
				return false;
			}
			interpolate = gd.getNextBoolean();
			if (bitDepth==8 || bitDepth==24)
				fillWithBackground = gd.getNextBoolean();
			if (canEnlarge)
				enlarge = gd.getNextBoolean();
			return true;
		}
	
}