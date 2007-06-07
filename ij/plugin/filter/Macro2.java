package ij.plugin.filter;
import ij.*;
import ij.process.*;

/** Sample ImageJ plug-in */
public class Macro2 implements PlugInFilter {
	
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		double scale = 0.85;
		int angle = 10;
		ip.setInterpolate(false);
		for (int i=0; i <17; i++) {
			ip.reset();
			ip.scale(scale, scale);
			ip.rotate(angle);
			imp.updateAndDraw();
			scale *= 0.85;
			angle += 10;
		}
		for (int i=0; i <18; i++) {
			ip.reset();
			ip.scale(scale, scale);
			ip.rotate(angle);
			imp.updateAndDraw();
			scale /= 0.85;
			angle += 10;
		}
		ip.reset();
		imp.updateAndDraw();
	}

}
