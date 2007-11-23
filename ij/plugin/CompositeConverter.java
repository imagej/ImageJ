package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.image.*;
import ij.plugin.frame.ContrastAdjuster;

/** This plugin imlements the Image/Color/Make Composite command. */
public class CompositeConverter implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		if (imp.isComposite()) {
			CompositeImage ci = (CompositeImage)imp;
			if (ci.getMode()!=CompositeImage.COMPOSITE) {
				ci.setMode(CompositeImage.COMPOSITE);
				ci.updateAndDraw();
			}
			return;
		}
		int z = imp.getStackSize();
		int c = imp.getNChannels();
		if (c==1) c = z;
		if (imp.getBitDepth()==24) {
			if (z>1)
				convertRGBToCompositeStack(imp);
			else
				convertRGBToCompositeImage(imp);
		} else if (c>=2 && c<=7) {
			String[] modes = {"Composite", "Color", "Grayscale"};
			GenericDialog gd = new GenericDialog("Make Composite");
			gd.addChoice("Display Mode:", modes, modes[0]);
			gd.showDialog();
			if (gd.wasCanceled()) return;
			int mode = gd.getNextChoiceIndex();
			CompositeImage ci = new CompositeImage(imp, mode+1);
			ci.show();
			imp.hide();
		} else
			IJ.error("To create a composite, the current image must be\n a stack with fewer than 8 slices or be in RGB format.");
	}
	
	void convertRGBToCompositeImage(ImagePlus imp) {
			ImageWindow win = imp.getWindow();
			Point loc = win!=null?win.getLocation():null;
			ImagePlus imp2 = new CompositeImage(imp, CompositeImage.COMPOSITE);
			imp.hide();
			imp2.show();
			ImageWindow win2 = imp2.getWindow();
			if (loc!=null&&win2!=null) win2.setLocation(loc);
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
		n *= 3;
		imp.changes = false;
		ImageWindow win = imp.getWindow();
		Point loc = win!=null?win.getLocation():null;
		ImagePlus imp2 = new ImagePlus(imp.getTitle(), stack2);
		imp2.setDimensions(3, n/3, 1);
 		imp2 = new CompositeImage(imp2, CompositeImage.COMPOSITE);
		imp2.show();
		imp.changes = false;
		imp.close();
	}

}
