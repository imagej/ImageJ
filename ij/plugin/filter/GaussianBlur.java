package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.util.*;

/** This plugin implements ImageJ's Gaussian Blur command. */
public class GaussianBlur implements PlugInFilter {

	private ImagePlus imp;
	private boolean canceled;
	private int slice;
	private ImageWindow win;
	private boolean displayKernel;
	private static int radius = 2;
	
	public int setup(String arg, ImagePlus imp) {
 		IJ.register(GaussianBlur.class);
		this.imp = imp;
		if (imp!=null) {
			win = imp.getWindow();
			win.running = true;
		}
		if (imp!=null && !showDialog())
			return DONE;
		else
			return IJ.setupDialog(imp, DOES_ALL);
	}

	public void run(ImageProcessor ip) {
		if (canceled)
			return;
		if (win.running!=true)
			{canceled=true; IJ.beep(); return;}
		slice++;
		if (slice>1)
			IJ.showStatus("Gaussian Blur: "+slice+"/"+imp.getStackSize());
		blur(ip, radius);
	}
	
	public void blur(ImageProcessor ip, double radius) {
		ImageProcessor kernel = makeKernel(radius);
		if (displayKernel) {
			kernel.resetMinAndMax();
			new ImagePlus("Kernel", kernel).show();
		}
		new Convolver().convolve(ip, (float[])kernel.getPixels(), kernel.getWidth(), kernel.getHeight());
	}
	
	public ImageProcessor makeKernel(double radius) {
		int size = (int)radius*2+1;
		ImageProcessor kernel = new FloatProcessor(size, size);
		double v;
		for (int y=0; y<size; y++)
			for (int x=0; x<size; x++) {
				v = Math.exp(-0.5*((sqr((x-radius)/((double)radius*2))+sqr((y-radius)/((double)radius*2)))/sqr(0.2)));
				if (v<0.005)
					v = 0.0;
				kernel.putPixelValue(x, y, v);
			}
						
		return kernel;
	}
	
	double sqr(double x) {return x*x;}
	
	public boolean showDialog() {
		GenericDialog gd = new GenericDialog("Guassian Blur...");
		gd.addNumericField("Radius", radius,0);
		gd.addCheckbox("Display Kernel", displayKernel);
		gd.showDialog();
		if (gd.wasCanceled()) {
			canceled = true;
			return false;
		}
		radius = (int)gd.getNextNumber();
		displayKernel = gd.getNextBoolean();
		return true;
	}

}


