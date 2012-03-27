package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.filter.*;

public class GaussianBlur3D implements PlugIn {
	private static double xsigma=2, ysigma=2, zsigma=2;

	public void run(String arg) {
		ImagePlus img = IJ.getImage();
		if (img.isComposite() || img.isHyperStack()) {
			IJ.error("3D Gaussian Blur", "Composite color images and hyperstacks not supported");
			return;
		}
		GenericDialog gd = new GenericDialog("3D Gaussian Blur");
		gd.addNumericField("X sigma", xsigma, 1);
		gd.addNumericField("Y sigma", ysigma, 1);
		gd.addNumericField("Z sigma", zsigma, 1);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		xsigma = gd.getNextNumber();
		ysigma = gd.getNextNumber();
		zsigma = gd.getNextNumber();
		img.startTiming();
		GaussianBlur.blur3D(img, xsigma, ysigma, zsigma);
		IJ.showTime(img, img.getStartTime(), "", img.getStackSize());
	}

}
