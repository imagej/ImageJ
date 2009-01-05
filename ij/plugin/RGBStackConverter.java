package ij.plugin;
import java.awt.*;
import ij.*;
import ij.process.*;
import ij.gui.*;

/** Converts a 2 or 3 slice stack to RGB. */
public class RGBStackConverter implements PlugIn {
	
	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		CompositeImage cimg = imp.isComposite()?(CompositeImage)imp:null;
		int size = imp.getStackSize();
		if ((size<2||size>3) && cimg==null) {
			IJ.error("A 2 or 3 image stack, or a HyperStack, required");
			return;
		}
		int type = imp.getType();
		if (cimg==null && !(type==ImagePlus.GRAY8 || type==ImagePlus.GRAY16)) {
			IJ.error("8-bit or 16-bit grayscale stack required");
			return;
		}
		if (!imp.lock())
			return;
		Undo.reset();
		String title = imp.getTitle()+" (RGB)";
		if (cimg!=null)
			compositeToRGB(cimg, title);
		else if (type==ImagePlus.GRAY16) {
			sixteenBitsToRGB(imp);
		} else {
			ImagePlus imp2 = imp.createImagePlus();
			imp2.setStack(title, imp.getStack());
	 		ImageConverter ic = new ImageConverter(imp2);
			ic.convertRGBStackToRGB();
			imp2.show();
		}
		imp.unlock();
	}
	
	void compositeToRGB(CompositeImage imp, String title) {
		int channels = imp.getNChannels();
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		int images = channels*slices*frames;
		if (channels==images) {
			compositeImageToRGB(imp, title);
			return;
		}
		String msg = null;
		if (frames>1)
			msg = "Convert all "+frames+" frames?";
		else
			msg = "Convert all "+slices+" slices?";
		if (!IJ.isMacro()) {
			YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), "Convert to RGB", msg);
			if (d.cancelPressed())
				return;
			else if (!d.yesPressed()) {
				compositeImageToRGB(imp, title);
				return;
			}
		}
		if (!imp.isHyperStack()) return;
		int n = frames;
		if (n==1) n = slices;
		ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
		int c=imp.getChannel(), z=imp.getSlice(), t=imp.getFrame();
		for (int i=1; i<=n; i++) {
			if (frames==1)
				imp.setPositionWithoutUpdate(imp.getChannel(), i, imp.getFrame());
			else
				imp.setPositionWithoutUpdate(imp.getChannel(), imp.getSlice(), i);
			stack.addSlice(null, new ColorProcessor(imp.getImage()));
		}
		imp.setPosition(c, z, t);
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setStack(title, stack);
		Object info = imp.getProperty("Info");
		if (info!=null) imp2.setProperty("Info", info);
		imp2.show();
	}

	void compositeImageToRGB(CompositeImage imp, String title) {
		ImagePlus imp2 = imp.createImagePlus();
		imp.updateImage();
		imp2.setProcessor(title, new ColorProcessor(imp.getImage()));
		imp2.show();
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
			ip2 = ip2.convertToByte(true);
			stack2.addSlice(null, ip2);
		}
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setStack(imp.getTitle()+" (RGB)", stack2);
	 	ImageConverter ic = new ImageConverter(imp2);
		ic.convertRGBStackToRGB();
		imp2.show();
	}
	
}
