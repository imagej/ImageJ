package ij.process;

import java.util.*;
import java.awt.*;
import java.awt.image.*;


/** This is an extended ColorProcessor that supports signed 32-bit int images. */
public class IntProcessor extends ColorProcessor {
	private byte[] pixels8;

	/**Creates a blank IntProcessor with the specified dimensions. */
	public IntProcessor(int width, int height) {
		this(width, height, new int[width*height]);
	}

	/**Creates an IntProcessor from a pixel array. */
	public IntProcessor(int width, int height, int[] pixels) {
		super(width, height, pixels);
		makeDefaultColorModel();
	}

	/** Create an 8-bit AWT image by scaling pixels in the range min-max to 0-255. */
	@Override
	public Image createImage() {
		if (!minMaxSet)
			findMinAndMax();
		boolean firstTime = pixels8==null;
		boolean thresholding = minThreshold!=NO_THRESHOLD && lutUpdateMode<NO_LUT_UPDATE;
		//ij.IJ.log("createImage: "+firstTime+"  "+lutAnimation+"  "+thresholding);
		if (firstTime || !lutAnimation)
			create8BitImage(thresholding&&lutUpdateMode==RED_LUT);
		if (cm==null)
			makeDefaultColorModel();
		if (thresholding) {
			int t1 = (int)minThreshold;
			int t2 = (int)maxThreshold;
			int size = width*height;
			int value;
			if (lutUpdateMode==BLACK_AND_WHITE_LUT) {
				for (int i=0; i<size; i++) {
					value = (pixels[i]&0xffff);
					if (value>=t1 && value<=t2)
						pixels8[i] = (byte)255;
					else
						pixels8[i] = (byte)0;
				}
			} else { // threshold red
				for (int i=0; i<size; i++) {
					value = (pixels[i]&0xffff);
					if (value>=t1 && value<=t2)
						pixels8[i] = (byte)255;
				}
			}
		}
		return createBufferedImage();
	}
	
	// creates 8-bit image by linearly scaling from float to 8-bits
	private byte[] create8BitImage(boolean thresholding) {
		int size = width*height;
		if (pixels8==null)
			pixels8 = new byte[size];
		double value;
		int ivalue;
		double min2 = getMin();
		double max2 = getMax();
		double scale = 255.0/(max2-min2);
		int maxValue = thresholding?254:255;
		for (int i=0; i<size; i++) {
			value = pixels[i]-min2;
			if (value<0.0) value=0.0;
			ivalue = (int)(value*scale+0.5);
			if (ivalue>maxValue) ivalue = maxValue;
			pixels8[i] = (byte)ivalue;
		}
		return pixels8;
	}

	@Override
	byte[] create8BitImage() {
		return create8BitImage(false);
	}

	Image createBufferedImage() {
		if (raster==null) {
			SampleModel sm = getIndexSampleModel();
			DataBuffer db = new DataBufferByte(pixels8, width*height, 0);
			raster = Raster.createWritableRaster(sm, db, null);
		}
		if (image==null || cm!=cm2) {
			if (cm==null) cm = getDefaultColorModel();
			image = new BufferedImage(cm, raster, false, null);
			cm2 = cm;
		}
		lutAnimation = false;
		return image;
	}

	/** Returns this image as an 8-bit BufferedImage . */
	public BufferedImage getBufferedImage() {
		return convertToByte(true).getBufferedImage();
	}
	
	@Override
	public void setColorModel(ColorModel cm) {
		if (cm!=null && !(cm instanceof IndexColorModel))
			throw new IllegalArgumentException("IndexColorModel required");
		if (cm!=null && cm instanceof LUT)
			cm = ((LUT)cm).getColorModel();
		this.cm = cm;
		baseCM = null;
		rLUT1 = rLUT2 = null;
		inversionTested = false;
		minThreshold = NO_THRESHOLD;
	}
	
	@Override
	public float getPixelValue(int x, int y) {
		if (x>=0 && x<width && y>=0 && y<height)
			return (float)pixels[y*width+x];
		else 
			return Float.NaN;
	}

	/** Returns the number of channels (1). */
	@Override
	public int getNChannels() {
		return 1;
	}
	
	public void findMinAndMax() {
		int size = width*height;
		int value;
		int min = pixels[0];
		int max = pixels[0];
		for (int i=1; i<size; i++) {
			value = pixels[i];
			if (value<min)
				min = value;
			else if (value>max)
				max = value;
		}
		this.min = min;
		this.max = max;
		minMaxSet = true;
	}

	@Override
	public void resetMinAndMax() {
		findMinAndMax();
		resetThreshold();
	}
	
	@Override
	public void setMinAndMax(double minimum, double maximum, int channels) {
		min = (int)minimum;
		max = (int)maximum;
		minMaxSet = true;
		resetThreshold();
	}

}


