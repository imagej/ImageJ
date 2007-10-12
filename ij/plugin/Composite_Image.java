package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.image.*;
import ij.plugin.frame.ContrastAdjuster;

public class Composite_Image implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		if (imp instanceof CompositeImage)
			return;
		int z = imp.getStackSize();
		if (imp.getBitDepth()==24) {
			if (z>1)
				convertRGBToCompositeStack(imp);
			else {
				imp.hide();
				new CompositeImage(imp, 3).show();
			}
		} else if (z>=2 && z<=7) {
			imp.hide();
			new CompositeImage(imp, z).show();
		} else
			IJ.error("To convert to composite color, the current image must be\n a stack with fewer than 8 slices or be in RGB format.");
	}
	
	void convertRGBToCompositeStack(ImagePlus imp) {
		int width = imp.getWidth();
		int height = imp.getHeight();
		ImageStack stack1 = imp.getStack();
		int n = stack1.getSize();
		ImageStack stack2 = new ImageStack(width, height);
		for (int i=0; i<n; i++) {
			ColorProcessor ip = (ColorProcessor)stack1.getProcessor(1);
			stack1.deleteSlice(1);
			byte[] R = new byte[width*height];
			byte[] G = new byte[width*height];
			byte[] B = new byte[width*height];
			ip.getRGB(R, G, B);
			stack2.addSlice(null, R);
			stack2.addSlice(null, G);
			stack2.addSlice(null, B);
		}
		imp.changes = false;
		imp.close();
		ImagePlus imp2 = new ImagePlus(imp.getTitle(), stack2);
 		imp2 = new CompositeImage(imp2, 3);
		imp2.setDimensions(3, n, 1);
		imp2.setOpenAsHypervolume(true);
		new StackWindow(imp2);
	}

}
