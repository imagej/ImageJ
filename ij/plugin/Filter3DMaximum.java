package ij.plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.process.StackProcessor;
import ij.util.ThreadUtil;

public class Filter3DMaximum implements PlugIn {

	private static float xradius = 2, yradius = 2, zradius = 2;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		if (imp.isComposite() && imp.getNChannels() == imp.getStackSize()) {
			IJ.error("3D Median", "Composite color images not supported");
			return;
		}
		if (!showDialog()) {
			return;
		}
		imp.startTiming();
		maximum3D(imp, xradius, yradius, zradius);
		IJ.showTime(imp, imp.getStartTime(), "", imp.getStackSize());
	}

	private boolean showDialog() {
		GenericDialog gd = new GenericDialog("3D Maximum");
		gd.addNumericField("X radius:", xradius, 1);
		gd.addNumericField("Y radius:", yradius, 1);
		gd.addNumericField("Z radius:", zradius, 1);
		gd.showDialog();
		if (gd.wasCanceled()) {
			return false;
		}
		xradius = (float) gd.getNextNumber();
		yradius = (float) gd.getNextNumber();
		zradius = (float) gd.getNextNumber();
		return true;
	}

	private void maximum3D(ImagePlus imp, float radX, float radY, float radZ) {
		// multithread support
		int nbcpus = ThreadUtil.getNbCpus();
		ImageStack res=FastFilters3D.filterIntImage(imp.getStack(), StackProcessor.FILTER_MAX, radX, radY, radZ, nbcpus);
		imp.setStack(res);
		imp.updateAndDraw();
	}
}
