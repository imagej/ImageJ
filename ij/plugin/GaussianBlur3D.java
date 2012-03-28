package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.filter.*;

public class GaussianBlur3D implements PlugIn {
	private static double xsigma=2, ysigma=2, zsigma=2;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		if (imp.isComposite() && imp.getNChannels()==imp.getStackSize()) {
			IJ.error("3D Gaussian Blur", "Composite color images not supported");
			return;
		}
		if (!showDialog())
			return;
		imp.startTiming();
		blur3D(imp, xsigma, ysigma, zsigma);
		IJ.showTime(imp, imp.getStartTime(), "", imp.getStackSize());
	}
	
	private boolean showDialog() {
		GenericDialog gd = new GenericDialog("3D Gaussian Blur");
		gd.addNumericField("X sigma", xsigma, 1);
		gd.addNumericField("Y sigma", ysigma, 1);
		gd.addNumericField("Z sigma", zsigma, 1);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		xsigma = gd.getNextNumber();
		ysigma = gd.getNextNumber();
		zsigma = gd.getNextNumber();
		return true;
	}
	
	private void blur3D(ImagePlus imp, double sigmaX, double sigmaY, double sigmaZ) {
		imp.killRoi();
		ImageStack stack = imp.getStack();
		if (sigmaX==sigmaY) {
			if (sigmaX!=0.0)
				IJ.run(imp, "Gaussian Blur...", "sigma="+sigmaX+" stack");
		} else {
			GaussianBlur gb = new GaussianBlur();
			for (int i=1; i<=imp.getStackSize(); i++) {
				ImageProcessor ip = stack.getProcessor(i);
				double accuracy = (imp.getBitDepth()==8||imp.getBitDepth()==24)?0.002:0.0002;
				gb.blurGaussian(ip, sigmaX, sigmaY, accuracy);
			}
		}
		if (sigmaZ>0.0) {
			if (imp.isHyperStack())
				blurHyperStackZ(imp, zsigma);
			else
				blurZ(stack, sigmaZ);
			imp.updateAndDraw();
		}
	}

	private void blurZ(ImageStack stack, double sigmaZ) {
		GaussianBlur gb = new GaussianBlur();
		double accuracy = (stack.getBitDepth()==8||stack.getBitDepth()==24)?0.002:0.0002;
		int w=stack.getWidth(), h=stack.getHeight(), d=stack.getSize();
		float[] zpixels = null;
		FloatProcessor fp =null;
		IJ.showStatus("Z blurring");
		gb.showProgress(false);
		int channels = stack.getProcessor(1).getNChannels();
		for (int y=0; y<h; y++) {
			IJ.showProgress(y, h-1);
			for (int channel=0; channel<channels; channel++) {
				zpixels = stack.getVoxels(0, y, 0, w, 1, d, zpixels, channel);
				if (fp==null)
					fp = new FloatProcessor(w, d, zpixels);
				//if (y==h/2) new ImagePlus("before-"+h/2, fp.duplicate()).show();
				gb.blur1Direction(fp, sigmaZ, accuracy, false, 0);
				stack.setVoxels(0, y, 0, w, 1, d, zpixels, channel);
			}
		}
		IJ.showStatus("");
	}

	private void blurHyperStackZ(ImagePlus imp, double zsigma) {
		ImagePlus[] images = ChannelSplitter.split(imp);
		for (int i=0; i<images.length; i++) {
			blurZ(images[i].getStack(), zsigma);
		}
	}

}
