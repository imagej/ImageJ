package ij.plugin;
import java.awt.*;
import ij.*;
import ij.process.*;
import ij.gui.*;

/** Converts a 2 or 3 slice stack to RGB. */
public class RGBStackConverter implements PlugIn {
	
	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
		int size = imp.getStackSize();
		if (size<2||size>3) {
			IJ.error("2 or 3 slice stack required");
			return;
		}
		int type = imp.getType();
		if (!(type==ImagePlus.GRAY8 || type==ImagePlus.GRAY16)) {
			IJ.error("8-bit or 16-bit grayscale stack required");
			return;
		}
		if (!imp.lock())
			return;
		Undo.reset();
		if (type==ImagePlus.GRAY16)
			sixteenBitsToRGB(imp);
		else {
			ImagePlus imp2 = imp.createImagePlus();
			imp2.setStack(imp.getTitle()+" (RGB)", imp.getStack());
	 		ImageConverter ic = new ImageConverter(imp2);
			ic.convertRGBStackToRGB();
			if (imp.getWindow()!=null)
				new ImageWindow(imp2); // replace StackWindow with ImageWindow
		}
		imp.unlock();
	}

	void sixteenBitsToRGB(ImagePlus imp) {
		Roi roi = imp.getRoi();
		int width, height;
		Rectangle r;
		if (roi!=null) {
			r = roi.getBounds();
			width = r.width;
			height = r.height;
		} else
			r = new Rectangle(0,0,imp.getWidth(),imp.getHeight());
		ImageProcessor ip;
		ImageStack stack1 = imp.getStack();
		ImageStack stack2 = new ImageStack(r.width, r.height);
		for (int i=1; i<=stack1.getSize(); i++) {
			ip = stack1.getProcessor(i);
			ip.setRoi(r);
			ImageProcessor ip2 = ip.crop();
			stack2.addSlice(null, ip2);
		}
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setStack(imp.getTitle()+" (RGB)", stack2);
		ImageProcessor ip2 = imp2.getProcessor();
		StackConverter sc = new StackConverter(imp2);
		sc.convertToGray8();
	 	ImageConverter ic = new ImageConverter(imp2);
		ic.convertRGBStackToRGB();
		imp2.show();
	}
	
}
