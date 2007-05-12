package ij;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.image.*;
import ij.plugin.*;
import ij.plugin.frame.ContrastAdjuster;

public class CompositeImage extends ImagePlus {

	int[] awtImagePixels;
	boolean newPixels;
	MemoryImageSource imageSource;
	ColorModel imageColorModel;
	Image awtImage;
	int[][] pixels;
	ImageProcessor[] cip;
	Color[] colors = {Color.red, Color.green, Color.blue, Color.white, Color.cyan, Color.magenta, Color.yellow};
	int currentChannel = 0;
	static int count;
	boolean singleChannel;

	public CompositeImage(ImagePlus imp, int channels) {
		//count++; if (count==1) throw new IllegalArgumentException("");
		ImageStack stack2;
		boolean isRGB = imp.getBitDepth()==24;
		if (isRGB)
			stack2 = getRGBStack(imp);
		else
			stack2 = imp.getStack();
		int stackSize = stack2.getSize();
		if (channels<2 || (stackSize%channels)!=0)
			throw new IllegalArgumentException("channels<2 or stacksize not multiple of channels");
		compositeImage = true;
		setDimensions(channels, stackSize/channels, 1);
		setup(channels, stack2);
		setStack(imp.getTitle(), stack2);
		setCalibration(imp.getCalibration());
		Object info = imp.getProperty("Info");
		if (info!=null)
			setProperty("Info", imp.getProperty("Info"));
	}

	public Image getImage() {
		if (img==null)
			updateImage();
		return img;
	}
	
	public void updateChannelAndDraw() {
		singleChannel = true;
		updateAndDraw();
	}
	
	public ImageProcessor getChannelProcessor() {
		return cip[currentChannel];
	}

	void setup(int channels, ImageStack stack2) {
       	cip = new ImageProcessor[channels];
        for (int i=0; i<channels; ++i) {
			cip[i] = stack2.getProcessor(i+1);
			cip[i].resetMinAndMax();
			cip[i].setColorModel(createModelFromColor(colors[i]));
		}
	}

	public void updateImage() {
		int imageSize = width*height;
		int nChannels = getNChannels();
		int redValue, greenValue, blueValue;
		int slice = getCurrentSlice();
		
		if (nChannels==1) {
			pixels = null;
			awtImagePixels = null;
			if (ip!=null)
				img = ip.createImage();
			return;

		}
	
		if (cip!=null && cip[0].getWidth()!=width||cip[0].getHeight()!=height||(pixels!=null&&pixels.length!=nChannels)) {
			setup(nChannels, getStack());
			pixels = null;
			awtImagePixels = null;
			if (slice>nChannels) {
				setSlice(1);
				slice = 1;
			}
		}
		if (slice>nChannels) slice = nChannels;

		if (slice-1!=currentChannel) {
			currentChannel = slice-1;
			getProcessor().setMinAndMax(cip[currentChannel].getMin(), cip[currentChannel].getMax());
			ContrastAdjuster.update();
		}
		//IJ.log(nChannels+" "+slice+" "+currentChannel);
				
		if (awtImagePixels == null) {
			awtImagePixels = new int[imageSize];
			newPixels = true;
			imageSource = null;
		}
		if (pixels==null || pixels.length!=nChannels || pixels[0].length!=imageSize) {
			pixels = new int[nChannels][];
			for (int i=0; i<nChannels; ++i)
				pixels[i] = new int[imageSize];
		}
		
		ImageProcessor ip = getProcessor();
		cip[currentChannel].setMinAndMax(ip.getMin(),ip.getMax());
		if (singleChannel) {
			PixelGrabber pg = new PixelGrabber(cip[currentChannel].createImage(), 0, 0, width, height, pixels[currentChannel], 0, width);
			try { pg.grabPixels();}
			catch (InterruptedException e){};
		} else {
			for (int i=0; i<nChannels; ++i) {
				PixelGrabber pg = new PixelGrabber(cip[i].createImage(), 0, 0, width, height, pixels[i], 0, width);
				try { pg.grabPixels();}
				catch (InterruptedException e){};
			}
		}
		if (singleChannel && nChannels<=3) {
			switch (currentChannel) {
				case 0:
					for (int i=0; i<imageSize; ++i) {
						redValue = (pixels[0][i]>>16)&0xff;
						awtImagePixels[i] = (awtImagePixels[i]&0xff00ffff) | (redValue<<16);
					}
					break;
				case 1:
					for (int i=0; i<imageSize; ++i) {
						greenValue = (pixels[1][i]>>8)&0xff;
						awtImagePixels[i] = (awtImagePixels[i]&0xffff00ff) | (greenValue<<8);
					}
					break;
				case 2:
					for (int i=0; i<imageSize; ++i) {
						blueValue = pixels[2][i]&0xff;
						awtImagePixels[i] = (awtImagePixels[i]&0xffffff00) | blueValue;
					}
					break;
			}
		} else {
			for (int i=0; i<imageSize; ++i) {
				redValue=0; greenValue=0; blueValue=0;
				for (int j=0; j<nChannels; ++j) {
					redValue += (pixels[j][i]>>16)&0xff;
					greenValue += (pixels[j][i]>>8)&0xff;
					blueValue += (pixels[j][i])&0xff; 
					if (redValue>255) redValue = 255;
					if (greenValue>255) greenValue = 255;
					if (blueValue>255) blueValue = 255;
				}
				awtImagePixels[i] = (redValue<<16) | (greenValue<<8) | (blueValue);
			}
		}
		if (imageSource==null) {
			imageColorModel = new DirectColorModel(32, 0xff0000, 0xff00, 0xff);
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
		singleChannel = false;
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
		stack.setColorModel(ip.getDefaultColorModel());
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
			rLut[i] = (byte)(i*rIncr);
			gLut[i] = (byte)(i*gIncr);
			bLut[i] = (byte)(i*bIncr);
		}
		return new IndexColorModel(8, 256, rLut, gLut, bLut);
	}
	
	public Color getChannelColor() {
		int index = getCurrentSlice()-1;
		if (index<colors.length && colors[index]!=Color.white)
			return colors[index];
		else;
			return Color.black;
	}

	public ImageProcessor getProcessor(int channel) {
		if (cip==null || channel>cip.length)
			return null;
		else
			return cip[channel-1];
	}

	public double getMin(int channel) {
		if (cip==null || channel>cip.length)
			return 0.0;
		else
			return cip[channel-1].getMin();
	}

	public double getMax(int channel) {
		if (cip==null || channel>cip.length)
			return 0.0;
		else
			return cip[channel-1].getMax();
	}

}
