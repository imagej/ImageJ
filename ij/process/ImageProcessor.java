package ij.process;

import java.util.*;
import java.awt.*;
import java.awt.image.*; 
import ij.gui.*;

/**
This abstract class is the superclass for classes that process
the four data types (byte, short, float and RGB) supported by ImageJ.
*/
public abstract class ImageProcessor extends Object {

	/** Value of pixels included in masks. */
	public static final int BLACK = 0xFF000000;
	
	/** Value returned by getMinThreshold() when thresholding is not enabled. */
	public static final double NO_THRESHOLD = -808080.0;
	
	static public final int RED_LUT=0, BLACK_AND_WHITE_LUT=1, NO_LUT_UPDATE=2;
	static final int INVERT=0, FILL=1, ADD=2, MULT=3, AND=4, OR=5,
		XOR=6, GAMMA=7, LOG=8, MINIMUM=9, MAXIMUM=10, SQR=11, SQRT=12;
	static final int BLUR_MORE=0, FIND_EDGES=1, MEDIAN_FILTER=2, MIN=3, MAX=4;
	static final double rWeight = 0.299, gWeight = 0.587, bWeight = 0.114;
	static final String WRONG_LENGTH = "(width*height) != pixels.length";
	
	int fgColor = 0;
	protected int lineWidth = 1;
	protected int cx, cy; //current drawing coordinates
	protected Font font;
	protected FontMetrics fontMetrics;
	static Frame frame;
		
    ProgressBar progressBar;
    boolean pixelsModified;
	protected int width, snapshotWidth;
	protected int height, snapshotHeight;
	protected int roiX, roiY, roiWidth, roiHeight;
	protected int xMin, xMax, yMin, yMax;
	boolean newSnapshot = false; // true if pixels = snapshotPixels
	int[] mask = null;
	protected ColorModel baseCM; // base color model
	protected ColorModel cm;
	protected byte[] rLUT1, gLUT1, bLUT1; // base LUT
	protected byte[] rLUT2, gLUT2, bLUT2; // LUT as modified by setMinAndMax and setThreshold
	protected boolean interpolate;
	protected double minThreshold=NO_THRESHOLD, maxThreshold=NO_THRESHOLD;
	protected int histogramSize = 256;
	protected float[] cTable;
	protected boolean lutAnimation;
	protected MemoryImageSource source;
	protected Image img;
	protected boolean newPixels;
	
	protected void showProgress(double percentDone) {
		if (progressBar!=null)
        	progressBar.show(percentDone);
	}

	protected void hideProgress() {
		showProgress(1.0);
		newSnapshot = false;
	}
		
	/** Returns the width of this image in pixels. */
	public int getWidth() {
		return width;
	}
	
	/** Returns the height of this image in pixels. */
	public int getHeight() {
		return height;
	}
	
	/** Returns this processor's color model. For non-RGB processors,
 		this is the base lookup table (LUT), not the one that may have
		been modified by setMinAndMax() or setThreshold(). */
	public ColorModel getColorModel() {
		if (baseCM!=null)
			return baseCM;
		else
			return cm;
	}

	/** Sets the color model. Must be an IndexColorModel (aka LUT)
		for all processors except the ColorProcessor. */
	public void setColorModel(ColorModel cm) {
		if (!(this instanceof ColorProcessor) && !(cm instanceof IndexColorModel))
			throw new IllegalArgumentException("Must be IndexColorModel");
		this.cm = cm;
		baseCM = null;
		rLUT1 = rLUT2 = null;
		newPixels = true;
		inversionTested = false;
		minThreshold = NO_THRESHOLD;
	}

	protected void makeDefaultColorModel() {
		byte[] rLUT = new byte[256];
		byte[] gLUT = new byte[256];
		byte[] bLUT = new byte[256];
		for(int i=0; i<256; i++) {
			rLUT[i]=(byte)i;
			gLUT[i]=(byte)i;
			bLUT[i]=(byte)i;
		}
		cm = new IndexColorModel(8, 256, rLUT, gLUT, bLUT);
	}

	/** Inverts the values in this image's LUT (indexed color model).
		Does nothing if this is a ColorProcessor. */
	public void invertLut() {
		if (cm==null)
			makeDefaultColorModel();
    	IndexColorModel icm = (IndexColorModel)cm;
		int mapSize = icm.getMapSize();
		byte[] reds = new byte[mapSize];
		byte[] greens = new byte[mapSize];
		byte[] blues = new byte[mapSize];	
		byte[] reds2 = new byte[mapSize];
		byte[] greens2 = new byte[mapSize];
		byte[] blues2 = new byte[mapSize];	
		icm.getReds(reds); 
		icm.getGreens(greens); 
		icm.getBlues(blues);
		for (int i=0; i<mapSize; i++) {
			reds2[i] = (byte)(reds[mapSize-i-1]&255);
			greens2[i] = (byte)(greens[mapSize-i-1]&255);
			blues2[i] = (byte)(blues[mapSize-i-1]&255);
		}
		cm = new IndexColorModel(8, mapSize, reds2, greens2, blues2); 
		newPixels = true;
		baseCM = null;
		rLUT1 = rLUT2 = null;
		inversionTested = false;
	}

	/** Returns the LUT index that's the best match for this color. */
	public int getBestIndex(Color c) {
		if (cm==null)
			makeDefaultColorModel();
    	IndexColorModel icm = (IndexColorModel)cm;
		int mapSize = icm.getMapSize();
		byte[] rLUT = new byte[mapSize];
    	byte[] gLUT = new byte[mapSize];
		byte[] bLUT = new byte[mapSize];
    	icm.getReds(rLUT); 
    	icm.getGreens(gLUT); 
    	icm.getBlues(bLUT); 
		int minDistance = Integer.MAX_VALUE;
		int distance;
		int minIndex = 0;
		int r1=c.getRed();
		int g1=c.getGreen();
		int b1=c.getBlue();
		int r2,b2,g2;
    	for (int i=0; i<mapSize; i++) {
			r2 = rLUT[i]&0xff; g2 = gLUT[i]&0xff; b2 = bLUT[i]&0xff;
    		distance = (r2-r1)*(r2-r1)+(g2-g1)*(g2-g1)+(b2-b1)*(b2-b1);
			//ij.IJ.write(i+" "+minIndex+" "+distance+" "+(rLUT[i]&255));
    		if (distance<minDistance) {
    			minDistance = distance;
    			minIndex = i;
    		}
    		if (minDistance==0.0)
    			break;
    	}
    	return minIndex;
	}

	protected boolean inversionTested = false;
	protected boolean invertedLut;
	
	/** Returns true if this image uses an inverted LUT. */
	public boolean isInvertedLut() {
		if (inversionTested)
			return invertedLut;
		inversionTested = true;
		if (cm==null || !(cm instanceof IndexColorModel))
			return (invertedLut=false);
		IndexColorModel icm = (IndexColorModel)cm;
		invertedLut = true;
		int v1, v2;
		for (int i=1; i<255; i++) {
			v1 = icm.getRed(i-1)+icm.getGreen(i-1)+icm.getBlue(i-1);
			v2 = icm.getRed(i)+icm.getGreen(i)+icm.getBlue(i);
			if (v1<v2) {
				invertedLut = false;
				break;
			}
		}
		return invertedLut;
	}

	/** Sets the default fill/draw value to the pixel
		value closest to the specified color. */
	public abstract void setColor(Color color);

	/** Obsolete (use setValue) */
	public void setColor(int value) {
		fgColor = value;
	}

	/** Sets the default fill/draw value. */
	public abstract void setValue(double value);

	/** Returns the smallest displayed pixel value. */
	public abstract double getMin();

	/** Returns the largest displayed pixel value. */
	public abstract double getMax();

	/** This image will be displayed by mapping pixel values in the
		range min-max to screen values in the range 0-255. For
		byte images, this mapping is done by updating the LUT. For
		short and float images, it's done by generating 8-bit AWT
		images. For RGB images, it's done by changing the pixel values. */
	public abstract void setMinAndMax(double min, double max);

	/** For short and float images, recalculates the min and max
		image values needed to correctly display the image. For
		ByteProcessors, resets the LUT. */
	public void resetMinAndMax() {}

	/** Sets the lower and upper threshold levels. If 'lutUdate' is true,
		recalculates the LUT. Thresholding of RGB images is not supported. */
	public void setThreshold(double minThreshold, double maxThreshold, int lutUpdate) {
		//ij.IJ.write("setThreshold: "+" "+minThreshold+" "+maxThreshold+" "+lutUpdate);
		if (this instanceof ColorProcessor)
			return;
		this.minThreshold = minThreshold;
		this.maxThreshold = maxThreshold;
		
		if (minThreshold==NO_THRESHOLD) {
			resetThreshold();
			return;
		}
		
		if (lutUpdate==NO_LUT_UPDATE)
			return;
		if (rLUT1==null) {
			if (cm==null)
				makeDefaultColorModel();
			baseCM = cm;
			IndexColorModel m = (IndexColorModel)cm;
			rLUT1 = new byte[256]; gLUT1 = new byte[256]; bLUT1 = new byte[256];
			m.getReds(rLUT1); m.getGreens(gLUT1); m.getBlues(bLUT1); 
			rLUT2 = new byte[256]; gLUT2 = new byte[256]; bLUT2 = new byte[256];
		}
		int t1 = (int)minThreshold;
		int t2 = (int)maxThreshold;
		int index;
		if (lutUpdate==RED_LUT)
			for (int i=0; i<256; i++) {
				if (i>=t1 && i<=t2) {
					rLUT2[i] = (byte)255;
					gLUT2[i] = (byte)0;
					bLUT2[i] = (byte)0;
				} else {
					rLUT2[i] = rLUT1[i];
					gLUT2[i] = gLUT1[i];
					bLUT2[i] = bLUT1[i];
				}
			}
		else
			for (int i=0; i<256; i++) {
				if (i>=t1 && i<=t2) {
					rLUT2[i] = (byte)0;
					gLUT2[i] = (byte)0;
					bLUT2[i] = (byte)0;
				} else {
					rLUT2[i] = (byte)255;
					gLUT2[i] = (byte)255;
					bLUT2[i] = (byte)255;
				}
			}

		cm = new IndexColorModel(8, 256, rLUT2, gLUT2, bLUT2);
		newPixels = true;
	}

	/** Disables thresholding. */
	public void resetThreshold() {
		minThreshold = NO_THRESHOLD;
		if (baseCM!=null) {
			cm = baseCM;
			baseCM = null;
		}
		rLUT1 = rLUT2 = null;
		inversionTested = false;
		newPixels = true;
	}

	/** Returns the lower threshold level. Returns NO_THRESHOLD
		if thresholding is not enabled. */
	public double getMinThreshold() {
		return minThreshold;
	}

	/** Returns the upper threshold level. */
	public double getMaxThreshold() {
		return maxThreshold;
	}

	/** Defines a rectangular region of interest
		and sets the mask to null. Use a null
		argument to set the ROI to the entire image. */
	public void setRoi(Rectangle roi) {
		if (roi==null)
			setRoi(0, 0, width, height);
		else
			setRoi(roi.x, roi.y, roi.width, roi.height);
	}

	/** Defines a rectangular region of interest and sets the mask to 
		null if this ROI is not the same size as the previous one. */
	public void setRoi(int x, int y, int rwidth, int rheight) {
		int oldWidth = roiWidth;
		int oldHeight = roiHeight;
		//find intersection of roi and this image
		roiX = Math.max(0, x);
		roiWidth = Math.min(width, x+rwidth)-roiX;
		roiY = Math.max(0, y);
		roiHeight = Math.min(height, y+rheight)-roiY;
		//setup limits for 3x3 filters
		xMin = Math.max(roiX, 1);
		xMax = Math.min(roiX + roiWidth - 1, width - 2);
		yMin = Math.max(roiY, 1);
		yMax = Math.min(roiY + roiHeight - 1, height - 2);
		if (roiWidth!=oldWidth || roiHeight!=oldHeight)
			mask = null;
	}

	/** Returns a Rectangle that represents the current
		region of interest. */
	public Rectangle getRoi() {
		return new Rectangle(roiX, roiY, roiWidth, roiHeight);
	}

	/** Sets an int array used as a mask to limit processing to an
		irregular ROI. The size of the array must be equal to
		roiWidth*roiHeight. Pixels in the array with a value of BLACK
		are inside the mask, all other pixels are outside the mask. */
	public void setMask(int[] mask) {
		this.mask = mask;
	}

	/** For images with irregular ROIs, returns a mask, otherwise, 
		returns null. Pixels inside the mask have a value of BLACK. */
	public int[] getMask() {
		return mask;
	}

	/** Assigns a progress bar to this processor. Set 'pb' to
		null to disable the progress bar. */
	public void setProgressBar(ProgressBar pb) {
		progressBar = pb;
	}

	/** Setting 'interpolate' true causes scale(), resize(),
		rotate() and getLine() to do bilinear interpolation. */
	public void setInterpolate(boolean interpolate) {
		this.interpolate = interpolate;
	}

	/** Obsolete. */
	public boolean isKillable() {
		return false;
	}

	private void process(int op, double value) {
		double SCALE = 255.0/Math.log(255.0);
		int v;
		
		int[] lut = new int[256];
		for (int i=0; i<256; i++) {
			switch(op) {
				case INVERT:
					v = 255 - i;
					break;
				case FILL:
					v = fgColor;
					break;
				case ADD:
					v = i + (int)value;
					break;
				case MULT:
					v = (int)Math.round(i * value);
					break;
				case AND:
					v = i & (int)value;
					break;
				case OR:
					v = i | (int)value;
					break;
				case XOR:
					v = i ^ (int)value;
					break;
				case GAMMA:
					v = (int)(Math.exp(Math.log(i/255.0)*value)*255.0);
					break;
				case LOG:
					if (i==0)
						v = 0;
					else
						v = (int)(Math.log(i) * SCALE);
					break;
				case SQR:
						v = i*i;
					break;
				case SQRT:
						v = (int)Math.sqrt(i);
					break;
				case MINIMUM:
					if (i<value)
						v = (int)value;
					else
						v = i;
					break;
				case MAXIMUM:
					if (i>value)
						v = (int)value;
					else
						v = i;
					break;
				 default:
				 	v = i;
			}
			if (v < 0)
				v = 0;
			if (v > 255)
				v = 255;
			lut[i] = v;
		}
		applyTable(lut);
    }

	/**
		Returns an array containing the pixel values along the
		line starting at (x1,y1) and ending at (x2,y2). For byte
		and short images, returns calibrated values if a calibration
		table has been set using setCalibrationTable().
		@see ImageProcessor#setInterpolate
	*/
	public double[] getLine(double x1, double y1, double x2, double y2) {
		double dx = x2-x1;
		double dy = y2-y1;
		int n = (int)Math.round(Math.sqrt(dx*dx + dy*dy));
		double xinc = dx/n;
		double yinc = dy/n;
		n++;
		double[] data = new double[n];
		double rx = x1;
		double ry = y1;
		if (interpolate)
			for (int i=0; i<n; i++) {
				if (cTable!=null)
					data[i] = getInterpolatedValue(rx, ry);
				else
					data[i] = getInterpolatedPixel(rx, ry);
				rx += xinc;
				ry += yinc;
			}
		else
			for (int i=0; i<n; i++) {
				data[i] = getPixelValue((int)(rx+0.5), (int)(ry+0.5));
				rx += xinc;
				ry += yinc;
			}
		return data;
	}
	
	/** Returns the pixel values along the horizontal line starting at (x,y). */
	public void getRow(int x, int y, int[] data, int length) {
		for (int i=0; i<length; i++)
			data[i] = getPixel(x++, y);
	}

	/** Returns the pixel values down the column starting at (x,y). */
	public void getColumn(int x, int y, int[] data, int length) {
		for (int i=0; i<length; i++)
			data[i] = getPixel(x, y++);
	}

	/** Inserts the pixels contained in 'data' into a 
		horizontal line starting at (x,y). */
	public void putRow(int x, int y, int[] data, int length) {
		for (int i=0; i<length; i++)
			putPixel(x++, y, data[i]);
	}
	
	/** Inserts the pixels contained in 'data' into a 
		column starting at (x,y). */
	public void putColumn(int x, int y, int[] data, int length) {
		//if (x>=0 && x<width && y>=0 && (y+length)<=height)
		//	((ShortProcessor)this).putColumn2(x, y, data, length);
		//else 
			for (int i=0; i<length; i++)
				putPixel(x, y++, data[i]);
	}

	/**
	Sets the current drawing location.
	@see ImageProcessor#lineTo
	@see ImageProcessor#drawString
	*/
	public void moveTo(int x, int y) {
		cx = x;
		cy = y;
	}
	
	/** Sets the line width used by lineTo() and drawDot(). */
	public void setLineWidth(int width) {
		lineWidth = width;
		if (lineWidth<1) lineWidth = 1;
	}
		
	/** Draws a line from the current drawing location to (x,y). */
	public void lineTo(int x2, int y2) {
		int dx = x2-cx;
		int dy = y2-cy;
		int absdx = dx>=0?dx:-dx;
		int absdy = dy>=0?dy:-dy;
		int n = absdy>absdx?absdy:absdx;
		double xinc = (double)dx/n;
		double yinc = (double)dy/n;
		double x = cx+0.5;
		double y = cy+0.5;
		n++;
		do {
			if (lineWidth==1)
				drawPixel((int)x, (int)y);
			else if (lineWidth==2)
				drawDot2((int)x, (int)y);
			else
				drawDot((int)x, (int)y);
			x += xinc;
			y += yinc;
		} while (--n>0);
		cx = x2; cy = y2;
	}
		
	/** Obsolete */
	public void drawDot2(int x, int y) {
		drawPixel(x, y);
		drawPixel(x-1, y);
		drawPixel(x, y-1);
		drawPixel(x-1, y-1);
	}
		
	/** Draws a dot using the current line width and fill/draw value. */
	public void drawDot(int xcenter, int ycenter) {
		int r = lineWidth/2;
		int r2 = r*r+1;
		for (int x=-r; x<=r; x++)
			for (int y=-r; y<=r; y++)
				if ((x*x+y*y)<=r2)
					drawPixel(xcenter+x, ycenter+y);
	}

	/** Draws a string at the current location using the current fill/draw value. */
	public void drawString(String s) {
		if (frame==null) {
			frame = new Frame();
			frame.pack();
			frame.setBackground(Color.white);
		}
		if (font==null)
			font = new Font("SansSerif", Font.PLAIN, 9);
		if (fontMetrics==null)
			fontMetrics = frame.getFontMetrics(font);
		int w =  fontMetrics.stringWidth(s);
		int h =  fontMetrics.getHeight();
		Image img = frame.createImage(w, h);
		Graphics g = img.getGraphics();
		g.setColor(Color.black);
		FontMetrics metrics = g.getFontMetrics(font);
		int fontHeight = metrics.getHeight();
		int descent = metrics.getDescent();
		g.setFont(font);
		g.drawString(s, 0, h-descent);
		g.dispose();
		ImageProcessor ip = new ColorProcessor(img);
		setRoi(cx,cy-h,w,h);
		fill((int[])ip.getPixels()); // fill using mask
		//new ij.ImagePlus("",ip).show();
		setRoi(null);
		cy += h;
	}
	
	/** Sets the font used by drawString(). */
	public void setFont(Font font) {
		this.font = font;
		fontMetrics	= null;
	}
	
	/** Returns the width in pixels of the specified string. */
	public int getStringWidth(String s) {
		if (font==null)
			font = new Font("SansSerif", Font.PLAIN, 9);
		if (fontMetrics==null) {
			if (frame==null)
				{frame = new Frame(); frame.pack();}
			fontMetrics = frame.getFontMetrics(font);
		}
		return fontMetrics.stringWidth(s);
	}

	/** Replaces each pixel with the 3x3 neighborhood mean. */
	public void smooth() {
		filter(BLUR_MORE);
	}

	/** Sharpens the image or ROI using a 3x3 convolution kernel. */
	public void sharpen() {
		int[] kernel = {-1, -1, -1,
		                -1, 12, -1,
		                -1, -1, -1};
		convolve3x3(kernel);
	}
	
	/** Finds edges in the image or ROI using a Sobel operator. */
	public void findEdges() {
		filter(FIND_EDGES);
	}

	/** Flips the image or ROI vertically. */
	public abstract void flipVertical();
	/* {
		int[] row1 = new int[roiWidth];
		int[] row2 = new int[roiWidth];
		for (int y=0; y<roiHeight/2; y++) {
			getRow(roiX, roiY+y, row1, roiWidth);
			getRow(roiX, roiY+roiHeight-y-1, row2, roiWidth);
			putRow(roiX, roiY+y, row2, roiWidth);
			putRow(roiX, roiY+roiHeight-y-1, row1, roiWidth);
		}
		newSnapshot = false;
	}
	*/

	/** Flips the image or ROI horizontally. */
	public void flipHorizontal() {
		int[] col1 = new int[roiHeight];
		int[] col2 = new int[roiHeight];
		for (int x=0; x<roiWidth/2; x++) {
			getColumn(roiX+x, roiY, col1, roiHeight);
			getColumn(roiX+roiWidth-x-1, roiY, col2, roiHeight);
			putColumn(roiX+x, roiY, col2, roiHeight);
			putColumn(roiX+roiWidth-x-1, roiY, col1, roiHeight);
		}
		newSnapshot = false;
	}

	/** Rotates the entire image 90 degrees clockwise. Returns
		a new ImageProcessor that represents the rotated image. */
	public ImageProcessor rotateRight() {
		int width2 = height;
		int height2 = width;
        ImageProcessor ip2 = createProcessor(width2, height2);
		int[] arow = new int[width];
		for (int row=0; row<height; row++) {
			getRow(0, row, arow, width);
			ip2.putColumn(width2-row-1, 0, arow, height2);
		}
        return ip2;
	}
	
	/** Rotates the entire image 90 degrees counter-clockwise. Returns
		a new ImageProcessor that represents the rotated image. */
	public ImageProcessor rotateLeft() {
		int width2 = height;
		int height2 = width;
        ImageProcessor ip2 = createProcessor(width2, height2);
		int[] arow = new int[width];
		int[] arow2 = new int[width];
		for (int row=0; row<height; row++) {
			getRow(0, row, arow, width);
			for (int i=0; i<width; i++) {
				arow2[i] = arow[width-i-1];
			}
			ip2.putColumn(row, 0, arow2, height2);
		}
        return ip2;
	}

	/** Inserts the image contained in 'ip' at (xloc, yloc). */
	public void insert(ImageProcessor ip, int xloc, int yloc) {
		copyBits(ip, xloc, yloc, Blitter.COPY);
	}
		
	/** Returns a string containing information about this ImageProcessor. */
	public String toString() {
		return ("width="+width+", height="+height+", min="+getMin()+", max="+getMax()+", v="+getPixel(0,0));
	}

	/** Fills the image or ROI with the current fill/draw value. */
	public void fill() {
		process(FILL, 0.0);
	}

	/** Fills pixels that are within the ROI and part of the mask
		(i.e. pixels that have a value=BLACK in the mask array). */
	public abstract void fill(int[] mask);

	/** Set a lookup table used by getPixelValue(), getLine() and
		convertToFloat() to calibrate pixel values. The length of
		the table must be 256 for byte images and 65536 for short
		images. RGB and float processors do not do calibration. */
	public void setCalibrationTable(float[] cTable) {
		this.cTable = cTable;
	}

	/** Set the number of bins to be used for float histograms. */
	public void setHistogramSize(int size) {
		histogramSize = size;
	}

	/**	Returns the number of float image histogram bins. The bin
		count is fixed at 256 for the other three data types. */
	public int getHistogramSize() {
		return histogramSize;
	}

	/** Returns a reference to this image's pixel array. The
		array type (byte[], short[], float[] or int[]) varies
		depending on the image type. */
	public abstract Object getPixels();
	
	/** Returns a reference to this image's snapshot (undo) array. If
		the snapshot array is null, returns a copy of the pixel data.
		The array type varies depending on the image type. */
	public abstract Object getPixelsCopy();

	/** Returns the value of the pixel at (x,y). For RGB images, the
		argb values are packed in an int. For float images, the
		the value must be converted using Float.intBitsToFloat().
		Returns zero if either the x or y coodinate is out of range. */
	public abstract int getPixel(int x, int y);
	
	/** Returns the value of the pixel at (x,y) without checking
		that x and y are within range. For RGB images, the
		argb values are packed in an int. For float images, the
		the value must be converted using Float.intBitsToFloat().
	*/
	public abstract int getUncheckedPixel(int x, int y);

	/** Uses bilinear interpolation to find the pixel value at real coordinates (x,y). */
	public abstract double getInterpolatedPixel(double x, double y);

	/** For color and float images, this is the same as getInterpolatedPixel(). */
	public double getInterpolatedValue(double x, double y) {
		return getInterpolatedPixel(x, y);
	}

	/** Stores the specified value at (x,y). For RGB images, the
		argb values are packed in 'value'. For float images,
		'value' is expected to be a float converted to an int
		using Float.floatToIntBits(). */
	public abstract void putPixel(int x, int y, int value);
	
	/** Stores the specified value at (x,y) withoutchecking that
		x and y are within range. For RGB images, the
		argb values are packed in 'value'. For float images,
		'value' is expected to be a float converted to an int
		using Float.floatToIntBits(). */
	public abstract void putUncheckedPixel(int x, int y, int value);

	/** Returns the value of the pixel at (x,y). For byte and short
		images, returns a calibrated value if a calibration table
		has been  set using setCalibraionTable(). For RGB images,
		returns the luminance value. */
	public abstract float getPixelValue(int x, int y);
		
	/** Stores the specified value at (x,y). For RGB images,
		does nothing. */
	public abstract void putPixelValue(int x, int y, double value);

	/** Sets the pixel at (x,y) to the current fill/draw value. */
	public abstract void drawPixel(int x, int y);
	
	/** Sets a new pixel array for the image and resets the snapshot
		buffer. The length of the array must be equal to width*height. */
	public abstract void setPixels(Object pixels);
	
	/** Copies the image contained in 'ip' to (xloc, yloc) using one of
		the transfer modes defined in the Blitter interface. */
	public abstract void copyBits(ImageProcessor ip, int xloc, int yloc, int mode);

	/** Transforms the image or ROI using a lookup table. The
		length of the table must be 256 for byte images and 
		65536 for short images. RGB and float images are not
		supported. */
	public abstract void applyTable(int[] lut);

	/** Inverts the image or ROI. */
	public void invert() {process(INVERT, 0.0);}
	
	/** Adds 'value' to each pixel in the image or ROI. */
	public void add(int value) {process(ADD, value);}
	
	/** Adds 'value' to each pixel in the image or ROI. */
	public void add(double value) {process(ADD, value);}
	
	/** Multiplies each pixel in the image or ROI by 'value'. */
	public void multiply(double value) {process(MULT, value);}
	
	/** Binary AND of each pixel in the image or ROI with 'value'. */
	public void and(int value) {process(AND, value);}

	/** Binary OR of each pixel in the image or ROI with 'value'. */
	public void or(int value) {process(OR, value);}
	
	/** Binary exclusive OR of each pixel in the image or ROI with 'value'. */
	public void xor(int value) {process(XOR, value);}
	
	/** Performs gamma correction of the image or ROI. */
	public void gamma(double value) {process(GAMMA, value);}
	
	/** Performs a log transform on the image or ROI. */
	public void log() {process(LOG, 0.0);}

	/** Performs a square transform on the image or ROI. */
	public void sqr() {process(SQR, 0.0);}

	/** Performs a square root transform on the image or ROI. */
	public void sqrt() {process(SQRT, 0.0);}

	/** Pixels less than 'value' are set to 'value'. */
	public void min(double value) {process(MINIMUM, value);}

	/** Pixels greater than 'value' are set to 'value'. */
	public void max(double value) {process(MAXIMUM, value);}

	/** Returns a copy of this image is the form of an AWT Image. */
	public abstract Image createImage();
	
	/** Returns a new, blank processor with the specified width and height. */
	public abstract ImageProcessor createProcessor(int width, int height);
	
	/** Makes a copy of this image's pixel data. */
	public abstract void snapshot();
	
	/** Restores the pixel data from the snapshot (undo) buffer. */
	public abstract void reset();
	
	/** Restore pixels that are within roi but not part of the mask. */
	public abstract void reset(int[] mask);
	
	/** Convolves the image or ROI with the specified
		3x3 integer convolution kernel. */
	public abstract void convolve3x3(int[] kernel);
	
	/** A 3x3 filter operation, where the argument (BLUR_MORE, 
		FIND_EDGES, etc.) determines the filter type. */
	public abstract void filter(int type);
	
	/** A 3x3 median filter. */
	public abstract void medianFilter();
	
    /** Adds random noise to the image or ROI.
    	@param range	the range of random numbers
    */
    public abstract void noise(double range);
    
	/** Creates a new processor containing an image
		that corresponds to the current ROI. */
	public abstract ImageProcessor crop();
	
	/** Returns a duplicate of this image. */
	public ImageProcessor duplicate() {
		Rectangle saveRoi = getRoi();
		setRoi(null);
		ImageProcessor ip2 = crop();
		setRoi(saveRoi);
		return ip2;
	}

	/** Scales the image by the specified factors. Does not
		change the image size.
		@see ImageProcessor#setInterpolate
		@see ImageProcessor#resize
	*/
	public abstract void scale(double xScale, double yScale);
	
	/** Creates a new ImageProcessor containing a scaled copy of this image or ROI.
		@see ij.process.ImageProcessor#setInterpolate
	*/
	public abstract ImageProcessor resize(int dstWidth, int dstHeight);
	
	/** Rotates the image or selection 'angle' degrees clockwise.
		@see ImageProcessor#setInterpolate
	*/
  	public abstract void rotate(double angle);
  	
	/**	For byte images, converts to binary using an automatically determined
		threshold. For RGB images, converts each channel to binary. For
		short and float images, does nothing. */
	public abstract void autoThreshold();
	
	/** Returns the histogram of the image or ROI. Returns
		a luminosity histogram for RGB images and null
		for float images. */
	public abstract int[] getHistogram();
	
	/** Erodes the image or ROI using a 3x3 maximum filter. */
	public abstract void erode();
	
	/** Dilates the image or ROI using a 3x3 minimum filter. */
	public abstract void dilate();
	
	/** For 16 and 32 bit processors, set 'lutAnimation' true
		to have createImage() use the cached 8-bit version
		of the image. */
	public void setLutAnimation(boolean lutAnimation) {
		this.lutAnimation = lutAnimation;
		newPixels = true;
	}
	
	void resetPixels(Object pixels) {
		if (pixels==null) {
			if (img!=null) {
				img.flush();
				img = null;
			}
			source = null;
		}
		newPixels = true;
	}

	/** Convertes this processor to a ByteProcessor. */
	public ImageProcessor convertToByte(boolean doScaling) {
		TypeConverter tc = new TypeConverter(this, doScaling);
		return tc.convertToByte();
	}

	/** Convertes this processor to a ShortProcessor. */
	public ImageProcessor convertToShort(boolean doScaling) {
		TypeConverter tc = new TypeConverter(this, doScaling);
		return tc.convertToShort();
	}

	/** Converts this processor to a FloatProcessor. For byte and
		short images, transforms using a calibration function if a
		calibration table has been set using setCalibrationTable(). */
	public ImageProcessor convertToFloat() {
		TypeConverter tc = new TypeConverter(this, false);
		return tc.convertToFloat(cTable);
	}
	
	/** Convertes this processor to an ColorProcessor. */
	public ImageProcessor convertToRGB() {
		TypeConverter tc = new TypeConverter(this, true);
		return tc.convertToRGB();
	}

}

