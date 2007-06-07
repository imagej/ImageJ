package ij.plugin.filter;
import ij.*;
import ij.process.*;

/** Sample ImageJ plug-in */
public class Macro1 implements PlugInFilter {
	
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		int x, y;
		int width = ip.getWidth();
		int height = ip.getHeight();
		double factor = 2.0;
		ImageProcessor ip2;
		
		ip.setInterpolate(false);
		for (int i=0; i<7; i++) {
			//ip2 = ip.resize((int)((double)width/factor), (int)((double)height/factor));
			ip2 = ip.resize((int)(width/factor), (int)(height/factor));
			x = 0; y = 0;
			do {
				ip.insert(ip2, x, y);
				x += width/factor;
				if (x >= width) {
					x = 0;
					y += height/factor;
				}
			} while (y<height);
			imp.updateAndDraw();
		}
		ip.reset();
		imp.updateAndDraw();
	}

}
