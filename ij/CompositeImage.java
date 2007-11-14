package ij;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.image.*;
import ij.plugin.*;
import ij.plugin.frame.*;

public class CompositeImage extends ImagePlus {

	public static final int COMPOSITE=1, COLORS=2, GRAYSCALE=3, TRANSPARENT=4;
	static final int MAX_CHANNELS = 7;
	int[] awtImagePixels;
	boolean newPixels;
	MemoryImageSource imageSource;
	ColorModel imageColorModel;
	ColorModel defaultColorModel;
	Image awtImage;
	int[][] pixels;
	ImageProcessor[] cip;
	Color[] colors = {Color.red, Color.green, Color.blue, Color.white, Color.cyan, Color.magenta, Color.yellow};
	ExtendedColorModel[] colorModel;
	int currentChannel = -1;
	int previousChannel;
	int currentSlice = 1;
	int currentFrame = 1;
	static int count;
	boolean singleChannel;
	boolean[] active = new boolean[MAX_CHANNELS];
	int mode = COLORS;
	int bitDepth;
	boolean customLut;

	public CompositeImage(ImagePlus imp) {
		this(imp, COLORS);
	}

	public CompositeImage(ImagePlus imp, int mode) {
		this.mode = mode;
		int channels = imp.getNChannels();
		bitDepth = getBitDepth();
		if (IJ.debugMode) IJ.log("CompositeImage: "+imp+" "+mode+" "+channels);
		//count++; if (count==1) throw new IllegalArgumentException("");
		ImageStack stack2;
		boolean isRGB = imp.getBitDepth()==24;
		if (isRGB) {
			if (imp.getStackSize()>1)
				throw new IllegalArgumentException("RGB stacks not supported");
			stack2 = getRGBStack(imp);
		} else
			stack2 = imp.getStack();
		int stackSize = stack2.getSize();
		if (channels==1 && isRGB) channels = 3;
		if (channels==1 && stackSize<8) channels = stackSize;
		if (channels<2 || (stackSize%channels)!=0)
			throw new IllegalArgumentException("channels<2 or stacksize not multiple of channels");
		compositeImage = true;
		int z = imp.getNSlices();
		int t = imp.getNFrames();
		if (channels==stackSize || channels*z*t!=stackSize)
			setDimensions(channels, stackSize/channels, 1);
		else
			setDimensions(channels, z, t);
		//setup(channels, stack2);
		setStack(imp.getTitle(), stack2);
		setCalibration(imp.getCalibration());
		setFileInfo(imp.getOriginalFileInfo());
		Object info = imp.getProperty("Info");
		if (info!=null)
			setProperty("Info", imp.getProperty("Info"));
		if (mode==COMPOSITE) {
			for (int i=0; i<MAX_CHANNELS; i++)
				active[i] = true;
		} else
			active[0] = true;
		if (!(channels==3&&stackSize==3))
			setOpenAsHyperStack(true);
	}

	public Image getImage() {
		if (img==null)
			updateImage();
		return img;
	}
	
	public void updateChannelAndDraw() {
		if (currentChannel!=-1 && active[currentChannel]) {
			if (!customLut) singleChannel = true;
			updateAndDraw();
		}
	}
	
	public ImageProcessor getChannelProcessor() {
		if (cip!=null && currentChannel!=-1)
			return cip[currentChannel];
		else
			return getProcessor();
	}

	void setup(int channels, ImageStack stack2) {
		setupColorModels(channels);
       	cip = new ImageProcessor[channels];
        for (int i=0; i<channels; ++i) {
			cip[i] = stack2.getProcessor(i+1);
			cip[i].resetMinAndMax();
			cip[i].setColorModel(colorModel[i]);
		}
	}

	void setupColorModels(int channels) {
		if (colorModel==null || colorModel.length<channels) {
			colorModel = new ExtendedColorModel[channels];
			for (int i=0; i<channels; ++i)
				colorModel[i] = createModelFromColor(colors[i]);
		}
	}

	public void updateImage() {
		int imageSize = width*height;
		int nChannels = getNChannels();
		int redValue, greenValue, blueValue;
		int ch = getChannel();
		
		//IJ.log("CompositeImage.updateImage: "+ch+"/"+nChannels+" "+currentSlice+" "+currentFrame);
		if (ch>nChannels) ch = nChannels;
		boolean newChannel = false;
		if (ch-1!=currentChannel) {
			previousChannel = currentChannel;
			currentChannel = ch-1;
			newChannel = true;
		}

		ImageProcessor ip = getProcessor();
		if (mode!=COMPOSITE) {
			if (newChannel) {
				if (mode==COLORS) {
					if (defaultColorModel==null)
						defaultColorModel = ip.getColorModel();
					setupColorModels(nChannels);
					ExtendedColorModel cm = colorModel[currentChannel];
					ip.setColorModel(colorModel[currentChannel]);
					if (previousChannel!=-1) {
						colorModel[previousChannel].min = ip.getMin();
						colorModel[previousChannel].max = ip.getMax();
					}
					if (!(cm.min==0.0&&cm.max==0.0))
						ip.setMinAndMax(cm.min, cm.max);
					ContrastAdjuster.update();
				}
				Frame channels = Channels.getInstance();
				for (int i=0; i<MAX_CHANNELS; i++)
					active[i] = i==currentChannel?true:false;
				if (channels!=null) ((Channels)channels).update();
			}
			img = ip.createImage();
			return;
		}

		if (nChannels==1) {
			cip = null;
			pixels = null;
			awtImagePixels = null;
			awtImage = null;
			if (ip!=null)
				img = ip.createImage();
			return;
		}
	
		if (cip==null||cip[0].getWidth()!=width||cip[0].getHeight()!=height||(pixels!=null&&pixels.length!=nChannels)||getBitDepth()!=bitDepth) {
			setup(nChannels, getStack());
			pixels = null;
			awtImagePixels = null;
			if (currentChannel>=nChannels) {
				setSlice(1);
				currentChannel = 0;
				newChannel = true;
			}
			bitDepth = getBitDepth();
		}
		
		if (newChannel) {
			getProcessor().setMinAndMax(cip[currentChannel].getMin(), cip[currentChannel].getMax());
			ContrastAdjuster.update();
		}
		//IJ.log(nChannels+" "+ch+" "+currentChannel+"  "+newChannel);
				
		if (isHyperStack() && (getSlice()!=currentSlice||getFrame()!=currentFrame)) {
			currentSlice = getSlice();
			currentFrame = getFrame();
			int position = (currentFrame-1)*nChannels*getNSlices() + (currentSlice-1)*nChannels + 1;
			for (int i=0; i<nChannels; ++i) {
				cip[i].setPixels(getStack().getProcessor(position+i).getPixels());
			}
		}

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
		
		if (mode ==TRANSPARENT) {
			createBlitterImage(nChannels);
			return;
		}
		
		if (mode==COLORS || mode==GRAYSCALE) {
			for (int i=0; i<MAX_CHANNELS; i++)
				active[i] = i==currentChannel?true:false;
			if (newChannel) {
				Frame channels = Channels.getInstance();
				if (channels!=null) ((Channels)channels).update();
			}
		}
		
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
					if (active[j]) {
						redValue += (pixels[j][i]>>16)&0xff;
						greenValue += (pixels[j][i]>>8)&0xff;
						blueValue += (pixels[j][i])&0xff; 
						if (redValue>255) redValue = 255;
						if (greenValue>255) greenValue = 255;
						if (blueValue>255) blueValue = 255;
					}
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
		if (img==null && awtImage!=null)
			img = awtImage;
		singleChannel = false;
	}
	
	void createBlitterImage(int n) {
		ImageProcessor ip = cip[n-1].duplicate();
		if (ip instanceof FloatProcessor){
			FloatBlitter fb = new FloatBlitter((FloatProcessor)ip);
			for (int i=1; i<n; i++)
				fb.copyBits(cip[i], 0, 0, Blitter.COPY_ZERO_TRANSPARENT);
		} else if (ip instanceof ByteProcessor){
			ByteBlitter bb = new ByteBlitter((ByteProcessor)ip);
			for (int i=1; i<n; i++)
				bb.copyBits(cip[i], 0, 0, Blitter.OR);
		} else if (ip instanceof ShortProcessor){
			ShortBlitter sb = new ShortBlitter((ShortProcessor)ip);
			for (int i=n-2; i>=0; i--)
				sb.copyBits(cip[i], 0, 0, Blitter. OR);
		}
		img = ip.createImage();
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

	public static ExtendedColorModel createModelFromColor(Color color) {
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
		ExtendedColorModel ecm = new ExtendedColorModel(8, 256, rLut, gLut, bLut);
		return ecm;
	}
	
	public Color getChannelColor() {
		if (colorModel==null || currentChannel==-1)
			return Color.black;
		IndexColorModel cm = colorModel[currentChannel];
		if (cm==null)
			return Color.black;
		int index = cm.getMapSize() - 1;
		int r = cm.getRed(index);
		int g = cm.getGreen(index);
		int b = cm.getBlue(index);
		//IJ.log(index+" "+r+" "+g+" "+b);
		if (r<100 || g<100 || b<100)
			return new Color(r, g, b);
		else
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
	
	public boolean[] getActiveChannels() {
		return active;
	}
	
	public void setMode(int mode) {
		if (mode<0 || mode>TRANSPARENT) return;
		for (int i=0; i<MAX_CHANNELS; i++)
			active[i] = true;
		if (this.mode!=COMPOSITE && mode==COMPOSITE)
			img = null;
		this.mode = mode;
		if (mode==COLORS || mode==GRAYSCALE) {
			if (cip!=null) {
				for (int i=0; i<cip.length; i++) {
					if (cip[i]!=null) cip[i].setPixels(null);
					cip[i] = null;
				}
			}
			cip = null;
			pixels = null;
			awtImagePixels = null;
			awtImage = null;
			currentChannel = -1;
		}
		if (mode==GRAYSCALE || mode==TRANSPARENT) {
			if (defaultColorModel!=null)
				getProcessor().setColorModel(defaultColorModel);
		}
		Frame channels = Channels.getInstance();
		if (channels!=null) ((Channels)channels).update();
	}

	public int getMode() {
		return mode;
	}
	
	public ColorModel getDefaultColorModel() {
		return defaultColorModel;
	}

	public void setChannelColorModel(IndexColorModel cm) {
		if (mode==GRAYSCALE) {
			if (defaultColorModel!=null)
				defaultColorModel = cm;
			getProcessor().setColorModel(cm);
		} else {
			if (currentChannel==-1) return;
			byte[] reds = new byte[256];
			byte[] greens = new byte[256];
			byte[] blues = new byte[256];
			cm.getReds(reds);
			cm.getGreens(greens);
			cm.getBlues(blues);
			colorModel[currentChannel] = new ExtendedColorModel(8, cm.getMapSize(), reds, greens, blues);
			if (mode==COMPOSITE) {
				cip[currentChannel].setColorModel(colorModel[currentChannel] );
				imageSource = null;
				newPixels = true;
				img = null;
			}
			currentChannel = -1;
			customLut = true;
			ContrastAdjuster.update();
		}
	}

}

class ExtendedColorModel extends IndexColorModel {
	double min, max;
	
    public ExtendedColorModel(int bits, int size, byte r[], byte g[], byte b[]) {
    	super(bits, size, r, g, b);
	}
	
}
