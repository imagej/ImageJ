package ij.process;

import java.util.*;
import java.awt.*;
import java.awt.image.*;
import ij.gui.*;

/**
This is an 8-bit image and methods that operate on that image. Based on the ImageProcessor class
from "KickAss Java Programming" by Tonny Espeset.
*/
public class ByteProcessor extends ImageProcessor {

	protected byte[] pixels;
	protected byte[] snapshotPixels;
	private int bgColor = 255; //white
	private int min=0, max=255;

	/**Creates a ByteProcessor from an 8-bit, indexed color AWT Image. */
	public ByteProcessor(Image img) {
		width = img.getWidth(null);
		height = img.getHeight(null);
		resetRoi();
		pixels = new byte[width * height];
		PixelGrabber pg = new PixelGrabber(img, 0, 0, width, height, false);
		try {
			pg.grabPixels();
		} catch (InterruptedException e) {
			System.err.println(e);
		};
   		cm = pg.getColorModel();
		if (cm instanceof IndexColorModel)
			pixels = (byte[])(pg.getPixels());
		else
			System.err.println("ByteProcessor: not 8-bit image");
	}

	/**Creates a blank ByteProcessor of the specified dimensions. */
	public ByteProcessor(int width, int height) {
		this(width, height, new byte[width*height], null);
	}

	/**Creates a ByteProcessor from a pixel array and IndexColorModel. */
	public ByteProcessor(int width, int height, byte[] pixels, ColorModel cm) {
		if (pixels!=null && width*height!=pixels.length)
			throw new IllegalArgumentException(WRONG_LENGTH);
		this.width = width;
		this.height = height;
		resetRoi();
		this.pixels = pixels;
		this.cm = cm;
	}

	public Image createImage() {
		if (cm==null)
			makeDefaultColorModel();
		if (source==null || (ij.IJ.isMacintosh()&&!ij.IJ.isJava2())) {
			source = new MemoryImageSource(width, height, cm, pixels, 0, width);
			source.setAnimated(true);
			source.setFullBufferUpdates(true);
			img = Toolkit.getDefaultToolkit().createImage(source);
		} else if (newPixels) {
			source.newPixels(pixels, cm, 0, width);
			newPixels = false;
		} else
			source.newPixels();
		return img;
	}

	/** Returns a new, blank ByteProcessor with the specified width and height. */
	public ImageProcessor createProcessor(int width, int height) {
		ImageProcessor ip2;
		ip2 =  new ByteProcessor(width, height, new byte[width*height], getColorModel());
		if (baseCM!=null)
			ip2.setMinAndMax(min, max);
		return ip2;
	}

	public ImageProcessor crop() {
		ImageProcessor ip2 = createProcessor(roiWidth, roiHeight);
		byte[] pixels2 = (byte[])ip2.getPixels();
		for (int ys=roiY; ys<roiY+roiHeight; ys++) {
			int offset1 = (ys-roiY)*roiWidth;
			int offset2 = ys*width+roiX;
			for (int xs=0; xs<roiWidth; xs++)
				pixels2[offset1++] = pixels[offset2++];
		}
        return ip2;
	}
	
	/**Make a snapshot of the current image.*/
	public void snapshot() {
		snapshotWidth=width;
		snapshotHeight=height;
		if (snapshotPixels==null || (snapshotPixels!=null && snapshotPixels.length!=pixels.length))
			snapshotPixels = new byte[width * height];
		System.arraycopy(pixels, 0, snapshotPixels, 0, width*height);
		newSnapshot = true;
		//double sum = 0;
		//for (int i=0; i<width*height; i++)
		//	sum += pixels[i]&0xff;
		//ij.IJ.write("snapshot: "+(sum/(width*height)));
	}
	
	/** Reset the image from snapshot.*/
	public void reset() {
		if (snapshotPixels==null)
			return;	
        System.arraycopy(snapshotPixels,0,pixels,0,width*height);
        newSnapshot = true;
	}
	
	/** Restore pixels that are within roi but not part of mask. */
	public void reset(int[] mask) {
		if (mask==null || snapshotPixels==null)
			return;	
		if (mask.length!=roiWidth*roiHeight)
			throw new IllegalArgumentException("mask.length!=roiWidth*roiHeight");
		for (int y=roiY, my=0; y<(roiY+roiHeight); y++, my++) {
			int i = y * width + roiX;
			int mi = my * roiWidth;
			for (int x=roiX; x<(roiX+roiWidth); x++) {
				if (mask[mi++]!=BLACK)
					pixels[i] = snapshotPixels[i];
				i++;
			}
		}
	}

	/** Fills pixels that are within roi and part of the mask.
		Throws an IllegalArgumentException if the mask is
		not the same size as the ROI. */
	public void fill(int[] mask) {
		if (mask==null)
			{fill(); return;}
		if (mask.length!=roiWidth*roiHeight)
			throw new IllegalArgumentException();
		for (int y=roiY, my=0; y<(roiY+roiHeight); y++, my++) {
			int i = y * width + roiX;
			int mi = my * roiWidth;
			for (int x=roiX; x<(roiX+roiWidth); x++) {
				if (mask[mi++]==BLACK)
					pixels[i] = (byte)fgColor;
				i++;
			}
		}
	}

	public int getPixel(int x, int y) {
		if (x>=0 && x<width && y>=0 && y<height)
			return pixels[y*width+x]&0xff;
		else
			return 0;
	}
	
	static double oldx, oldy;

	/** Uses bilinear interpolation to find the pixel value at real coordinates (x,y). */
	public double getInterpolatedPixel(double x, double y) {
		if (x<0.0) x = 0.0;
		if (x>=width-1.0) x = width-1.001;
		if (y<0.0) y = 0.0;
		if (y>=height-1.0) y = height-1.001;
		return getInterpolatedPixel(x, y, pixels);
	}

	/** Uses bilinear interpolation to find the calibrated
		pixel value at real coordinates (x,y). */
		public double getInterpolatedValue(double x, double y) {
		if (cTable==null)
			return getInterpolatedPixel(x, y);
		if (x<0.0) x = 0.0;
		if (x>=width-1.0) x = width-1.001;
		if (y<0.0) y = 0.0;
		if (y>=height-1.0) y = height-1.001;
		int xbase = (int)x;
		int ybase = (int)y;
		double xFraction = x - xbase;
		double yFraction = y - ybase;
		int offset = ybase * width + xbase;
		double lowerLeft = cTable[pixels[offset]&255];
		//if ((xbase>=(width-1))||(ybase>=(height-1)))
		//	return lowerLeft;
		double lowerRight = cTable[pixels[offset + 1]&255];
		double upperRight = cTable[pixels[offset + width + 1]&255];
		double upperLeft = cTable[pixels[offset + width]&255];
		double upperAverage = upperLeft + xFraction * (upperRight - upperLeft);
		double lowerAverage = lowerLeft + xFraction * (lowerRight - lowerLeft);
		return lowerAverage + yFraction * (upperAverage - lowerAverage);
	}

	public float getPixelValue(int x, int y) {
		if (x>=0 && x<width && y>=0 && y<height) {
			if (cTable==null)
				return pixels[y*width + x]&0xff;
			else
				return cTable[pixels[y*width + x]&0xff];
		} else
			return 0f;
	}

	/** Sets the foreground drawing color. */
	public void setColor(Color color) {
		//if (ij.IJ.altKeyDown()) throw new IllegalArgumentException("setColor: "+color);
		drawingColor = color;
		fgColor = getBestIndex(color);
	}

	/** Sets the default fill/draw value, where 0<=value<=255. */
	public void setValue(double value) {
		fgColor = (int)value;
		if (fgColor<0) fgColor = 0;
		if (fgColor>255) fgColor = 255;
	}

	/** Stores the specified real value at (x,y). Does
		nothing if (x,y) is outside the image boundary.
		The value is clamped to be in the range 0-255. */
	public void putPixelValue(int x, int y, double value) {
		if (x>=0 && x<width && y>=0 && y<height) {
			if (value>255.0)
				value = 255.0;
			else if (value<0.0)
				value = 0.0;
			pixels[y*width + x] = (byte)(value+0.5);
		}
	}

	/** Stores the specified value at (x,y). */
	public void putPixel(int x, int y, int value) {
		if (x>=0 && x<width && y>=0 && y<height)
			pixels[y*width + x] = (byte)value;
	}

	/** Draws a pixel in the current foreground color. */
	public void drawPixel(int x, int y) {
		if (x>=0 && x<width && y>=0 && y<height)
			pixels[y*width + x] = (byte)fgColor;
	}

	/**	Returns a reference to the byte array containing this image's
		pixel data. To avoid sign extension, the pixel values must be
		accessed using a mask (e.g. int i = pixels[j]&0xff). */
	public Object getPixels() {
		return (Object)pixels;
	}

	/** Returns a reference to this image's snapshot (undo) byte array. If
		the snapshot array is null, returns a copy of the pixel data. */
	public Object getPixelsCopy() {
		if (snapshotPixels!=null && newSnapshot)
			return snapshotPixels;
		else {
			byte[] pixels2 = new byte[width*height];
        	System.arraycopy(pixels, 0, pixels2, 0, width*height);
			return pixels2;
		}
	}

	public void setPixels(Object pixels) {
		if (pixels!=null && this.pixels!=null && (((byte[])pixels).length!=this.pixels.length))
			throw new IllegalArgumentException("");
		this.pixels = (byte[])pixels;
		resetPixels(pixels);
		snapshotPixels = null;
	}

	/*
	public void getRow(int x, int y, int[] data, int length) {
		int j = y*width+x;
		for (int i=0; i<length; i++)
			data[i] = pixels[j++];
	}

	public void putRow(int x, int y, int[] data, int length) {
		int j = y*width+x;
		for (int i=0; i<length; i++)
			pixels[j++] = (byte)data[i];
	}
	*/
	
	/** Returns the smallest displayed pixel value. */
	public double getMin() {
		return min;
	}

	/** Returns the largest displayed pixel value. */
	public double getMax() {
		return max;
	}

	/** Maps the entries in this image's LUT from min-max to 0-255. */
	public void setMinAndMax(double min, double max) {
		if (max<min)
			return;
		this.min = (int)min;
		this.max = (int)max;
		
		if (rLUT1==null) {
			if (cm==null)
				makeDefaultColorModel();
			baseCM = cm;
			IndexColorModel m = (IndexColorModel)cm;
			rLUT1 = new byte[256]; gLUT1 = new byte[256]; bLUT1 = new byte[256];
			m.getReds(rLUT1); m.getGreens(gLUT1); m.getBlues(bLUT1); 
			rLUT2 = new byte[256]; gLUT2 = new byte[256]; bLUT2 = new byte[256];
		}
		int index;
		for (int i=0; i<256; i++) {
			if (i<min) {
				rLUT2[i] = rLUT1[0];
				gLUT2[i] = gLUT1[0];
				bLUT2[i] = bLUT1[0];
			} else if (i>max) {
				rLUT2[i] = rLUT1[255];
				gLUT2[i] = gLUT1[255];
				bLUT2[i] = bLUT1[255];
			} else {
				index = i-this.min;
				index = (int)(256.0*index/(max-min));
				if (index < 0)
					index = 0;
				if (index > 255)
					index = 255;
				rLUT2[i] = rLUT1[index];
				gLUT2[i] = gLUT1[index];
				bLUT2[i] = bLUT1[index];
			}
		}
		cm = new IndexColorModel(8, 256, rLUT2, gLUT2, bLUT2);
		newPixels = true;
		minThreshold = NO_THRESHOLD;
	}

	/** Resets this image's LUT. */
	public void resetMinAndMax() {
		setMinAndMax(0, 255);
	}

	/** Copies the image contained in 'ip' to (xloc, yloc) using one of
		the transfer modes defined in the Blitter interface. */
	public void copyBits(ImageProcessor ip, int xloc, int yloc, int mode) {
		if (!(ip instanceof ByteProcessor || ip instanceof ColorProcessor))
			throw new IllegalArgumentException("8-bit or RGB image required");
		new ByteBlitter(this).copyBits(ip, xloc, yloc, mode);
	}

	/* Filters start here */

	public void applyTable(int[] lut) {
		int lineStart, lineEnd;
		for (int y=roiY; y<(roiY+roiHeight); y++) {
			lineStart = y * width + roiX;
			lineEnd = lineStart + roiWidth;
			for (int i=lineEnd; --i>=lineStart;)
				pixels[i] = (byte)lut[pixels[i]&0xff];
		}
	}

	/** 3x3 convolution contributed by Glynne Casteel. */
	public void convolve3x3(int[] kernel) {
		int p1, p2, p3,
		    p4, p5, p6,
		    p7, p8, p9;
		int k1=kernel[0], k2=kernel[1], k3=kernel[2],
		    k4=kernel[3], k5=kernel[4], k6=kernel[5],
		    k7=kernel[6], k8=kernel[7], k9=kernel[8];

		int scale = 0;
		for (int i=0; i<kernel.length; i++)
			scale += kernel[i];
		if (scale==0) scale = 1;
		int inc = roiHeight/25;
		if (inc<1) inc = 1;
		
		byte[] pixels2 = (byte[])getPixelsCopy();
		int offset, sum;
        int rowOffset = width;
		for (int y=yMin; y<=yMax; y++) {
			offset = xMin + y * width;
			p1 = 0;
			p2 = pixels2[offset-rowOffset-1]&0xff;
			p3 = pixels2[offset-rowOffset]&0xff;
			p4 = 0;
			p5 = pixels2[offset-1]&0xff;
			p6 = pixels2[offset]&0xff;
			p7 = 0;
			p8 = pixels2[offset+rowOffset-1]&0xff;
			p9 = pixels2[offset+rowOffset]&0xff;

			for (int x=xMin; x<=xMax; x++) {
				p1 = p2; p2 = p3;
				p3 = pixels2[offset-rowOffset+1]&0xff;
				p4 = p5; p5 = p6;
				p6 = pixels2[offset+1]&0xff;
				p7 = p8; p8 = p9;
				p9 = pixels2[offset+rowOffset+1]&0xff;

				sum = k1*p1 + k2*p2 + k3*p3
				    + k4*p4 + k5*p5 + k6*p6
				    + k7*p7 + k8*p8 + k9*p9;
				sum /= scale;

				if(sum>255) sum= 255;
				if(sum<0) sum= 0;

				pixels[offset++] = (byte)sum;
			}
			if (y%inc==0)
				showProgress((double)(y-roiY)/roiHeight);
		}
		hideProgress();
	}

	/** Filters using a 3x3 neighborhood. */
	public void filter(int type) {
		int p1, p2, p3, p4, p5, p6, p7, p8, p9;
		int inc = roiHeight/25;
		if (inc<1) inc = 1;
		
		byte[] pixels2 = (byte[])getPixelsCopy();
		int offset, sum1, sum2=0, sum=0;
        int[] values = new int[10];
        if (type==MEDIAN_FILTER) values = new int[10];
        int rowOffset = width;
        int count = 0;
		for (int y=yMin; y<=yMax; y++) {
			offset = xMin + y * width;
			p2 = pixels2[offset-rowOffset-1]&0xff;
			p3 = pixels2[offset-rowOffset]&0xff;
			p5 = pixels2[offset-1]&0xff;
			p6 = pixels2[offset]&0xff;
			p8 = pixels2[offset+rowOffset-1]&0xff;
			p9 = pixels2[offset+rowOffset]&0xff;

			for (int x=xMin; x<=xMax; x++) {
				p1 = p2; p2 = p3;
				p3 = pixels2[offset-rowOffset+1]&0xff;
				p4 = p5; p5 = p6;
				p6 = pixels2[offset+1]&0xff;
				p7 = p8; p8 = p9;
				p9 = pixels2[offset+rowOffset+1]&0xff;

				switch (type) {
					case BLUR_MORE:
						sum = (p1+p2+p3+p4+p5+p6+p7+p8+p9)/9;
						break;
					case FIND_EDGES:
	        			sum1 = p1 + 2*p2 + p3 - p7 - 2*p8 - p9;
	        			sum2 = p1  + 2*p4 + p7 - p3 - 2*p6 - p9;
	        			sum = (int)Math.sqrt(sum1*sum1 + sum2*sum2);
	        			if (sum> 255) sum = 255;
						break;
					case MEDIAN_FILTER:
						values[1]=p1; values[2]=p2; values[3]=p3; values[4]=p4; values[5]=p5;
						values[6]=p6; values[7]=p7; values[8]=p8; values[9]=p9;
						sum = findMedian(values);
						break;
					case MIN:
						sum = p5;
						if (p1<sum) sum = p1;
						if (p2<sum) sum = p2;
						if (p3<sum) sum = p3;
						if (p4<sum) sum = p4;
						if (p6<sum) sum = p6;
						if (p7<sum) sum = p7;
						if (p8<sum) sum = p8;
						if (p9<sum) sum = p9;
						break;
					case MAX:
						sum = p5;
						if (p1>sum) sum = p1;
						if (p2>sum) sum = p2;
						if (p3>sum) sum = p3;
						if (p4>sum) sum = p4;
						if (p6>sum) sum = p6;
						if (p7>sum) sum = p7;
						if (p8>sum) sum = p8;
						if (p9>sum) sum = p9;
						break;
				}
				
				pixels[offset++] = (byte)sum;
			}
			if (y%inc==0)
				showProgress((double)(y-roiY)/roiHeight);
		}
		hideProgress();
	}

	public void erode() {
		if (isInvertedLut())
			filter(MIN);
		else
			filter(MAX);
	}
	
	public void dilate() {
		if (isInvertedLut())
			filter(MAX);
		else
			filter(MIN);
	}

	public void outline() {
		new BinaryProcessor(this).outline();
	}
	
	public void skeletonize() {
		new BinaryProcessor(this).skeletonize();
	}
	
	private final int findMedian (int[] values) {
	//Finds the 5th largest of 9 values
		for (int i = 1; i <= 4; i++) {
			int max = 0;
			int mj = 1;
			for (int j = 1; j <= 9; j++)
				if (values[j] > max) {
					max = values[j];
					mj = j;
				}
			values[mj] = 0;
		}
		int max = 0;
		for (int j = 1; j <= 9; j++)
			if (values[j] > max)
				max = values[j];
		return max;
	}

	public void medianFilter() {
		filter(MEDIAN_FILTER);
	}

    public void noise(double range) {
		Random rnd=new Random();
		int v;

		for (int y=roiY; y<(roiY+roiHeight); y++) {
			int i = y * width + roiX;
			for (int x=roiX; x<(roiX+roiWidth); x++) {
				int RandomBrightness = (int)Math.round(rnd.nextGaussian()*range);
				v = (pixels[i] & 0xff) + RandomBrightness;
				if (v < 0)
					v = 0;
				if (v > 255)
					v = 255;
				pixels[i] = (byte)v;
				i++;
			}
			if (y%10==0)
				showProgress((double)(y-roiY)/roiHeight);
		}
		hideProgress();
    }

	/** Scales the image or selection using the specified scale factors.
		@see ImageProcessor#setInterpolate
	*/
	public void scale(double xScale, double yScale) {
		double xCenter = roiX + roiWidth/2.0;
		double yCenter = roiY + roiHeight/2.0;
		int xmin, xmax, ymin, ymax;
		if (isInvertedLut()) bgColor = 0;
		
		if ((xScale>1.0) && (yScale>1.0)) {
			//expand roi
			xmin = (int)(xCenter-(xCenter-roiX)*xScale);
			if (xmin<0) xmin = 0;
			xmax = xmin + (int)(roiWidth*xScale) - 1;
			if (xmax>=width) xmax = width - 1;
			ymin = (int)(yCenter-(yCenter-roiY)*yScale);
			if (ymin<0) ymin = 0;
			ymax = ymin + (int)(roiHeight*yScale) - 1;
			if (ymax>=height) ymax = height - 1;
		} else {
			xmin = roiX;
			xmax = roiX + roiWidth - 1;
			ymin = roiY;
			ymax = roiY + roiHeight - 1;
		}
		byte[] pixels2 = (byte[])getPixelsCopy();
		boolean checkCoordinates = (xScale < 1.0) || (yScale < 1.0);
		int index1, index2, xsi, ysi;
		double ys, xs;
		double xlimit = width-1.0, xlimit2 = width-1.001;
		double ylimit = height-1.0, ylimit2 = height-1.001;
		for (int y=ymin; y<=ymax; y++) {
			ys = (y-yCenter)/yScale + yCenter;
			ysi = (int)ys;
			if (ys<0.0) ys = 0.0;			
			if (ys>=ylimit) ys = ylimit2;
			index1 = y*width + xmin;
			index2 = width*(int)ys;
			for (int x=xmin; x<=xmax; x++) {
				xs = (x-xCenter)/xScale + xCenter;
				xsi = (int)xs;
				if (checkCoordinates && ((xsi<xmin) || (xsi>xmax) || (ysi<ymin) || (ys>ymax)))
					pixels[index1++] = (byte)bgColor;
				else {
					if (interpolate) {
						if (xs<0.0) xs = 0.0;
						if (xs>=xlimit) xs = xlimit2;
						pixels[index1++] =(byte)((int)(getInterpolatedPixel(xs, ys, pixels2)+0.5)&255);
					} else
						pixels[index1++] = pixels2[index2+xsi];
				}
			}
			if (y%20==0)
			showProgress((double)(y-ymin)/height);
		}
		hideProgress();
	}

	/** Uses bilinear interpolation to find the pixel value at real coordinates (x,y). */
	private final double getInterpolatedPixel(double x, double y, byte[] pixels) {
		int xbase = (int)x;
		int ybase = (int)y;
		double xFraction = x - xbase;
		double yFraction = y - ybase;
		int offset = ybase * width + xbase;
		int lowerLeft = pixels[offset]&255;
		//if ((xbase>=(width-1))||(ybase>=(height-1)))
		//	return lowerLeft;
		int lowerRight = pixels[offset + 1]&255;
		int upperRight = pixels[offset + width + 1]&255;
		int upperLeft = pixels[offset + width]&255;
		double upperAverage = upperLeft + xFraction * (upperRight - upperLeft);
		double lowerAverage = lowerLeft + xFraction * (lowerRight - lowerLeft);
		return lowerAverage + yFraction * (upperAverage - lowerAverage);
	}

	/** Creates a new ByteProcessor containing a scaled copy of this image or selection.
		@see ij.process.ImageProcessor#setInterpolate
	*/
	public ImageProcessor resize(int dstWidth, int dstHeight) {
		if (roiWidth==dstWidth && roiHeight==dstHeight)
			return crop();
		double srcCenterX = roiX + roiWidth/2.0;
		double srcCenterY = roiY + roiHeight/2.0;
		double dstCenterX = dstWidth/2.0;
		double dstCenterY = dstHeight/2.0;
		double xScale = (double)dstWidth/roiWidth;
		double yScale = (double)dstHeight/roiHeight;
		if (interpolate) {
			dstCenterX += xScale/2.0;
			dstCenterY += yScale/2.0;
		}
		ImageProcessor ip2 = createProcessor(dstWidth, dstHeight);
		byte[] pixels2 = (byte[])ip2.getPixels();
		double xs, ys;
		double xlimit = width-1.0, xlimit2 = width-1.001;
		double ylimit = height-1.0, ylimit2 = height-1.001;
		int index1, index2;
		for (int y=0; y<=dstHeight-1; y++) {
			ys = (y-dstCenterY)/yScale + srcCenterY;
			if (interpolate) {
				if (ys<0.0) ys = 0.0;
				if (ys>=ylimit) ys = ylimit2;
			}
			index1 = width*(int)ys;
			index2 = y*dstWidth;
			for (int x=0; x<=dstWidth-1; x++) {
				xs = (x-dstCenterX)/xScale + srcCenterX;
				if (interpolate) {
					if (xs<0.0) xs = 0.0;
					if (xs>=xlimit) xs = xlimit2;
					pixels2[index2++] = (byte)((int)(getInterpolatedPixel(xs, ys, pixels)+0.5)&255);
				} else
		  			pixels2[index2++] = pixels[index1+(int)xs];
			}
			if (y%20==0)
			showProgress((double)y/dstHeight);
		}
		hideProgress();
		return ip2;
	}

	/** Rotates the image or ROI 'angle' degrees clockwise.
		@see ImageProcessor#setInterpolate
	*/
	public void rotate(double angle) {
        if (angle%360==0)
        	return;
		byte[] pixels2 = (byte[])getPixelsCopy();
		double centerX = roiX + (roiWidth-1)/2.0;
		double centerY = roiY + (roiHeight-1)/2.0;
		int xMax = roiX + this.roiWidth - 1;
		if (isInvertedLut()) bgColor = 0;
		
		double angleRadians = -angle/(180.0/Math.PI);
		double ca = Math.cos(angleRadians);
		double sa = Math.sin(angleRadians);
		double tmp1 = centerY*sa-centerX*ca;
		double tmp2 = -centerX*sa-centerY*ca;
		double tmp3, tmp4, xs, ys;
		int index, ixs, iys;
		double dwidth=width, dheight=height;
		double xlimit = width-1.0, xlimit2 = width-1.001;
		double ylimit = height-1.0, ylimit2 = height-1.001;
		
		for (int y=roiY; y<(roiY + roiHeight); y++) {
			index = y*width + roiX;
			tmp3 = tmp1 - y*sa + centerX;
			tmp4 = tmp2 + y*ca + centerY;
			for (int x=roiX; x<=xMax; x++) {
				xs = x*ca + tmp3;
				ys = x*sa + tmp4;
				if ((xs>=-0.01) && (xs<dwidth) && (ys>=-0.01) && (ys<dheight)) {
					if (interpolate) {
						if (xs<0.0) xs = 0.0;
						if (xs>=xlimit) xs = xlimit2;
						if (ys<0.0) ys = 0.0;			
						if (ys>=ylimit) ys = ylimit2;
						pixels[index++] = (byte)((int)(getInterpolatedPixel(xs, ys, pixels2)+0.5)&255);
				  	} else {
				  		ixs = (int)(xs+0.5);
				  		iys = (int)(ys+0.5);
				  		if (ixs>=width) ixs = width - 1;
				  		if (iys>=height) iys = height -1;
						pixels[index++] = pixels2[width*iys+ixs];
					}
				} else
					pixels[index++] = (byte)bgColor;
			}
			if (y%30==0)
				showProgress((double)(y-roiY)/roiHeight);
		}
		hideProgress();
	}

	public void flipVertical() {
		int index1,index2;
		byte tmp;
		for (int y=0; y<roiHeight/2; y++) {
			index1 = (roiY+y)*width+roiX;
			index2 = (roiY+roiHeight-1-y)*width+roiX;
			for (int i=0; i<roiWidth; i++) {
				tmp = pixels[index1];
				pixels[index1++] = pixels[index2];
				pixels[index2++] = tmp;
			}
		}
		newSnapshot = false;
	}
	
	public int[] getHistogram() {
		if (mask!=null)
			return getHistogram(mask);
		int[] histogram = new int[256];
		for (int y=roiY; y<(roiY+roiHeight); y++) {
			int i = y * width + roiX;
			for (int x=roiX; x<(roiX+roiWidth); x++) {
				int v = pixels[i++] & 0xff;
				histogram[v]++;
			}
		}
		return histogram;
	}

	public int[] getHistogram(int[] mask) {
		int v;
		int[] histogram = new int[256];
		for (int y=roiY, my=0; y<(roiY+roiHeight); y++, my++) {
			int i = y * width + roiX;
			int mi = my * roiWidth;
			for (int x=roiX; x<(roiX+roiWidth); x++) {
				if (mask[mi++]==BLACK) {
					v = pixels[i] & 0xff;
					histogram[v]++;
				}
				i++;
			}
		}
		return histogram;
	}

	public void threshold(int level) {
		for (int i=0; i<width*height; i++) {
			if ((pixels[i] & 0xff) <= level)
				pixels[i] = 0;
			else
				pixels[i] = (byte)255;
		}
		newSnapshot = false;
	}

	/** Iterative thresholding technique, described originally by Ridler & Calvard in
	"PIcture Thresholding Using an Iterative Selection Method", IEEE transactions
	on Systems, Man and Cybernetics, August, 1978. */
	public int getAutoThreshold() {
		int level;
		int[] histogram = getHistogram();
		double result,tempSum1,tempSum2,tempSum3,tempSum4;

		histogram[0] = 0; //set to zero so erased areas aren't included
		histogram[255] = 0;
		int min = 0;
		while ((histogram[min] == 0) && (min < 255))
			min++;
		int max = 255;
		while ((histogram[max] == 0) && (max > 0))
			max--;
		if (min >= max) {
			level = 128;
			return level;
		}
		
		int movingIndex = min;
		do {
			tempSum1=tempSum2=tempSum3=tempSum4=0.0;
			for (int i=min; i<=movingIndex; i++) {
				tempSum1 += i*histogram[i];
				tempSum2 += histogram[i];
			}
			for (int i=(movingIndex+1); i<=max; i++) {
				tempSum3 += i *histogram[i];
				tempSum4 += histogram[i];
			}
			
			result = (tempSum1/tempSum2/2.0) + (tempSum3/tempSum4/2.0);
			movingIndex++;
		} while ((movingIndex+1)<=result && movingIndex<=(max-1));
		
		level = (int)Math.round(result);
		return level;
	}
	
	/** Converts the image to binary using an automatically determined threshold. */
	public void autoThreshold() {
		threshold(getAutoThreshold());
	}

	public void applyLut() {
		if (rLUT2==null)
			return;
		if (isInvertedLut())
			for (int i=0; i<width*height; i++)
				pixels[i] = (byte)(255 - rLUT2[pixels[i]&0xff]);
		else
			for (int i=0; i<width*height; i++)
				pixels[i] = rLUT2[pixels[i] & 0xff];
		setMinAndMax(0, 255);
	}

	/** Performs a convolution operation using the specified kernel. */
	public void convolve(float[] kernel, int kernelWidth, int kernelHeight) {
		ImageProcessor ip2 = convertToFloat();
		ip2.setRoi(getRoi());
		new ij.plugin.filter.Convolver().convolve(ip2, kernel, kernelWidth, kernelHeight);
		ip2 = ip2.convertToByte(false);
		byte[] pixels2 = (byte[])ip2.getPixels();
		System.arraycopy(pixels2, 0, pixels, 0, pixels.length);
	}

}

