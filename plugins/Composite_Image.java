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

	int[] awtImagePixels;
	boolean newPixels;
	MemoryImageSource imageSource;
	ColorModel imageColorModel;
	Image awtImage;
	int[][] awtChannelPixels;
	ImageProcessor[] channelIPs;
	Color[] colors = {Color.red, Color.green, Color.blue};
	boolean singleChannel = false;
	int currentChannel = 0;

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
       	channelIPs = new ImageProcessor[channels];
        	for (int i=0; i<channels; ++i) {
			channelIPs[i] = stack.getProcessor(i+1);
			channelIPs[i].setColorModel(createModelFromColor(colors[i]));
		}
		setStack(imp.getTitle(), stack);
		channelIPs[currentChannel] = getProcessor();
		channelIPs[currentChannel].setColorModel(createModelFromColor(colors[currentChannel]));
	}

	public Image getImage() {
		if (img==null)
			updateImage();
		return img;
	}

	public void updateImage() {
		if (singleChannel) {
			img = ip.createImage();
			return;
		}
		int imageSize = width*height;
		int nChannels = getNChannels();
		int redValue, greenValue, blueValue;
		if (awtImagePixels == null || awtImagePixels.length != imageSize) {
			awtImagePixels = new int[imageSize];
			newPixels = true;
		}
		if (awtChannelPixels==null || awtChannelPixels.length!=nChannels || awtChannelPixels[0].length!=imageSize) {
			awtChannelPixels = new int[nChannels][];
			for (int i=0; i<nChannels; ++i)
				awtChannelPixels[i] = new int[imageSize];
		}
		for (int i=0; i<nChannels; ++i) {
			PixelGrabber pg = new PixelGrabber(channelIPs[i].createImage(), 0, 0, width, height, awtChannelPixels[i], 0, width);
			try { pg.grabPixels();}
			catch (InterruptedException e){};
		}
		for (int i=0; i<imageSize; ++i) {
			redValue=0; greenValue=0; blueValue=0;
			for (int j=0; j<nChannels; ++j) {
				redValue += (awtChannelPixels[j][i]>>16)&0xFF;
				greenValue += (awtChannelPixels[j][i]>>8)&0xFF;
				blueValue += (awtChannelPixels[j][i])&0xFF; 
				if (redValue>255) redValue = 255;
				if (greenValue>255) greenValue = 255;
				if (blueValue>255) blueValue = 255;
			}
			awtImagePixels[i] = (redValue<<16) | (greenValue<<8) | (blueValue); 		}			
		if (imageSource==null) {
			imageColorModel = new DirectColorModel(32, 0xFF0000, 0xFF00, 0xFF);
			imageSource = new MemoryImageSource(width, height, imageColorModel, awtImagePixels, 0, width);
			imageSource.setAnimated(true);
			imageSource.setFullBufferUpdates(true);
			awtImage = Toolkit.getDefaultToolkit().createImage(imageSource);
			newPixels = false;
		} else if (newPixels){
			imageSource.newPixels(awtImagePixels, imageColorModel, 0, width);
			newPixels = false;
		} else
			imageSource.newPixels();	
		if (img == null && awtImage!=null)
			img = awtImage;
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

	public static IndexColorModel createModelFromColor(Color color) {
		byte[] rLut = new byte[256];
		byte[] gLut = new byte[256];
		byte[] bLut = new byte[256];

		int red = color.getRed();
		int green = color.getGreen();
		int blue = color.getBlue();

		double rIncr = ((double)red)/255d;
		double gIncr = ((double)green)/255d;
		double bIncr = ((double)blue)/255d;
		
		for (int i=0; i<256; ++i) {
			rLut[i] = (byte) (i*rIncr);
			gLut[i] = (byte) (i*gIncr);
			bLut[i] = (byte) (i*bIncr);
		}
		
		return new IndexColorModel(8, 256, rLut, gLut, bLut);
	}

}
