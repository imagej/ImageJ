import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.image.*;
import ij.plugin.*;

public class Composite_Image implements PlugIn {

	public void run(String arg) {
		IJ.run("Duplicate...", "title=temp duplicate");
		ImagePlus imp = IJ.getImage();
		imp.hide();
		if (!(imp.getBitDepth()==24 || imp.getStackSize()==3)) {
			IJ.error("Invalid image type");
			return;
		}
		new CompositeImage("Test", imp, 3).show();
	}

}

class CompositeImage extends ImagePlus {

	public CompositeImage(String title, ImagePlus imp, int channels) {
		ImageStack stack;
		if (imp.getBitDepth()==24)
			stack = getRGBStack(imp);
		else
			stack = imp.getStack();
		int stackSize = stack.getSize();
		if (channels<2 || (stackSize%channels)!=0)
			throw new IllegalArgumentException("channels<2 or stacksize not multiple of channels");
		setDimensions(channels, stackSize/channels, 1);
		compositeImage = true;
		setStack(imp.getTitle(), stack);
	}

	public Image getImage() {
		if (img==null) {
			if (ip==null) return null;
			switch (getBitDepth()) {
				case 8: img = createImage8(); break;
				case 16: img = createImage16(); break;
			}
		}
		return img;
	}

	BufferedImage bi;
	int[] pixels;

	Image createImage16() {
		int size = width*height;
		if (bi==null) {
			bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			pixels = new int[size];
		}
		ImageStack stack = getStack();
		short[] r = (short[])stack.getProcessor(1).getPixels();
		short[] g = (short[])stack.getProcessor(2).getPixels();
		short[] b = (short[])stack.getProcessor(3).getPixels();
		for (int i=0; i<size; i++) {
		}
		return bi;
	}

	Image createImage8() {
		return null;
	}

	ImageStack getRGBStack(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		int w = ip.getWidth();
		int h = ip.getHeight();
		int size = w*h;
		byte[] r = new byte[size];
		byte[] g = new byte[size];
		byte[] b = new byte[size];
		((ColorProcessor)ip).getRGB(r, g, b);
		ImageStack stack = new ImageStack(w, h);
		stack.addSlice("Red", r);	
		stack.addSlice("Green", g);	
		stack.addSlice("Blue", b);	
		return stack;
	}

}
