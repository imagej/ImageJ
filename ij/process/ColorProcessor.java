package ij.process;

import java.util.*;
import java.awt.*;
import java.awt.image.*;
import ij.gui.*;

/**
This is an 32-bit RGB image and methods that operate on that image.. Based on the ImageProcessor class from
"KickAss Java Programming" by Tonny Espeset (http://www.sn.no/~espeset).
*/
public class ColorProcessor extends ImageProcessor {

	protected int[] pixels;
	protected int[] snapshotPixels = null;
	private int bgColor = 0xffffffff; //white
	private int min=0, max=255;


	/**Creates a ColorProcessor from an AWT Image. */
	public ColorProcessor(Image img) {
		width = img.getWidth(null);
		height = img.getHeight(null);
		pixels = new int[width * height];
		PixelGrabber pg = new PixelGrabber(img, 0, 0, width, height, pixels, 0, width);
		try {
			pg.grabPixels();
		} catch (InterruptedException e){};
		createColorModel();
		fgColor = 0xff000000; //black
		setRoi(null);
	}

	/**Creates a blank ColorProcessor of the specified dimensions. */
	public ColorProcessor(int width, int height) {
		this(width, height, new int[width*height]);
	}
	
	/**Creates a ColorProcessor from a pixel array. */
	public ColorProcessor(int width, int height, int[] pixels) {
		this.width = width;
		this.height = height;
		createColorModel();
		fgColor = 0xff000000; //black
		setRoi(null);
		this.pixels = pixels;
	}


	void createColorModel() {
		cm = new DirectColorModel(24, 0xff0000, 0xff00, 0xff);
	}
	
	public Image createImage() {
		imageSource = new MemoryImageSource(width, height, cm, pixels, 0, width);
		Image img = Toolkit.getDefaultToolkit().createImage(imageSource);
		return img;
	}


	/** Returns a new, blank ShortProcessor with the specified width and height. */
	public ImageProcessor createProcessor(int width, int height) {
		int[] pixels = new int[width*height];
		for (int i=0; i<width*height; i++)
			pixels[i] = -1;
		return new ColorProcessor(width, height, pixels);
	}

	public Color getColor(int x, int y) {
		int c = pixels[y*width+x];
		int r = (c&0xff0000)>>16;
		int g = (c&0xff00)>>8;
		int b = c&0xff;
		return new Color(r,g,b);
	}


	/** Sets the foreground color. */
	public void setColor(Color color) {
		fgColor = color.getRGB();
	}


	/** Sets the default fill/draw value, where value is interpreted as an RGB int. */
	public void setValue(double value) {
		fgColor = (int)value;
	}

	/** Returns the smallest displayed pixel value. */
	public double getMin() {
		return min;
	}


	/** Returns the largest displayed pixel value. */
	public double getMax() {
		return max;
	}


	/** Uses a table look-up to map the pixels in this image from min-max to 0-255. */
	public void setMinAndMax(double min, double max) {
		if (max<=min)
			return;
		this.min = (int)min;
		this.max = (int)max;
		int v;
		int[] lut = new int[256];
		for (int i=0; i<256; i++) {
			v = i-this.min;
			v = (int)(256.0*v/(max-min));
			if (v < 0)
				v = 0;
			if (v > 255)
				v = 255;
			lut[i] = v;
		}
		reset();
		applyTable(lut);
	}


	public void snapshot() {
		snapshotWidth = width;
		snapshotHeight = height;
		if (snapshotPixels==null || (snapshotPixels!=null && snapshotPixels.length!=pixels.length))
			snapshotPixels = new int[width * height];
		System.arraycopy(pixels, 0, snapshotPixels, 0, width*height);
		newSnapshot = true;
	}


	public void reset() {
		if (snapshotPixels==null)
			return;
		System.arraycopy(snapshotPixels, 0, pixels, 0, width*height);
		newSnapshot = true;
	}


	public void reset(int[] mask) {
		if (mask==null || snapshotPixels==null || mask.length!=roiWidth*roiHeight)
			return;
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


	/** Fills pixels that are within roi and part of the mask. */
	public void fill(int[] mask) {
		for (int y=roiY, my=0; y<(roiY+roiHeight); y++, my++) {
			int i = y * width + roiX;
			int mi = my * roiWidth;
			for (int x=roiX; x<(roiX+roiWidth); x++) {
				if (mask[mi++]==BLACK)
					pixels[i] = fgColor;
				i++;
			}
		}
	}


	public Object getPixelsCopy() {
		if (newSnapshot)
			return snapshotPixels;
		else {
			int[] pixels2 = new int[width*height];
        	System.arraycopy(pixels, 0, pixels2, 0, width*height);
			return pixels2;
		}
	}


	public int getPixel(int x, int y) {
		if (x>=0 && x<width && y>=0 && y<height)
			return pixels[y*width+x];
		else
			return 0;
	}


	/** Calls getPixelValue(x,y). */
	public double getInterpolatedPixel(double x, double y) {
		return getPixelValue((int)(x+0.5), (int)(y+0.5));
	}

	/** Stores the specified value at (x,y). */
	public void putPixel(int x, int y, int value) {
		if (x>=0 && x<width && y>=0 && y<height)
			pixels[y*width + x] = value;
	}


	/** Stores the specified real value at (x,y). */
	public void putPixelValue(int x, int y, double value) {
		if (x>=0 && x<width && y>=0 && y<height)
			pixels[y*width + x] = (int)value;
	}

	/** Converts the specified pixel to grayscale
		(g=r*0.30+g*0.59+b*0.11) and returns it as a float. */
	public float getPixelValue(int x, int y) {
		if (x>=0 && x<width && y>=0 && y<height) {
			int c = pixels[y*width+x];
			int r = (c&0xff0000)>>16;
			int g = (c&0xff00)>>8;
			int b = c&0xff;
			return (float)(r*0.30 + g*0.59 + b*0.11);
		}
		else
			return 0;
	}


	/** Draws a pixel in the current foreground color. */
	public void drawPixel(int x, int y) {
		if (x>=0 && x<width && y>=0 && y<height)
			pixels[y*width + x] = fgColor;
	}


	/**	Returns a reference to the int array containing
		this image's pixel data. */
	public Object getPixels() {
		return (Object)pixels;
	}


	public void setPixels(Object pixels) {
		this.pixels = (int[])pixels;
		snapshotPixels = null;
		imageSource = null;
	}


	/** Returns hue, saturation and brightness in 3 byte arrays. */
	public void getHSB(byte[] H, byte[] S, byte[] B) {
		int c, r, g, b;
		float[] hsb = new float[3];
		for (int i=0; i < width*height; i++) {
			c = pixels[i];
			r = (c&0xff0000)>>16;
			g = (c&0xff00)>>8;
			b = c&0xff;
			hsb = Color.RGBtoHSB(r, g, b, hsb);
			H[i] = (byte)((int)(hsb[0]*255.0));
			S[i] = (byte)((int)(hsb[1]*255.0));
			B[i] = (byte)((int)(hsb[2]*255.0));
		}
	}
	

	/** Returns the red, green and blue planes as 3 byte arrays. */
	public void getRGB(byte[] R, byte[] G, byte[] B) {
		int c, r, g, b;
		for (int i=0; i < width*height; i++) {
			c = pixels[i];
			r = (c&0xff0000)>>16;
			g = (c&0xff00)>>8;
			b = c&0xff;
			R[i] = (byte)r;
			G[i] = (byte)g;
			B[i] = (byte)b;
		}
	}


	/** Sets the current pixels from 3 byte arrays (reg, green, blue). */
	public void setRGB(byte[] R, byte[] G, byte[] B) {
		int c, r, g, b;
		for (int i=0; i < width*height; i++)
			pixels[i] = 0xff000000 | ((R[i]&0xff)<<16) | ((G[i]&0xff)<<8) | B[i]&0xff;
	}


	/** Sets the current pixels from 3 byte arrays (hue, saturation and brightness). */
	public void setHSB(byte[] H, byte[] S, byte[] B) {
		float hue, saturation, brightness;
		for (int i=0; i < width*height; i++) {
			hue = (float)((H[i]&0xff)/255.0);
			saturation = (float)((S[i]&0xff)/255.0);
			brightness = (float)((B[i]&0xff)/255.0);
			pixels[i] = Color.HSBtoRGB(hue, saturation, brightness);
		}
	}
	
	/** Copies the image contained in 'ip' to (xloc, yloc) using one of
		the transfer modes defined in the Blitter interface. */
	public void copyBits(ImageProcessor ip, int xloc, int yloc, int mode) {
		if (!((ip instanceof ColorProcessor) | (ip instanceof ByteProcessor)))
			throw new IllegalArgumentException("8-bit or RGB image required");
		new ColorBlitter(this).copyBits(ip, xloc, yloc, mode);
	}

	/* Filters start here */

	public void applyTable(int[] lut) {
		for (int y=roiY; y<(roiY+roiHeight); y++) {
			int i = y * width + roiX;
			for (int x=roiX; x<(roiX+roiWidth); x++) {
				int c = pixels[i];
				int r = lut[(c&0xff0000)>>16];
				int g = lut[(c&0xff00)>>8];
				int b = lut[c&0xff];
				pixels[i] = (c&0xff000000) + (r<<16) + (g<<8) + b;
				i++;
			}
			if (y%20==0)
				showProgress((double)(y-roiY)/roiHeight);
		}
		hideProgress();
	}
	/** Fills the current rectangular ROI. */
	public void fill() {
		for (int y=roiY; y<(roiY+roiHeight); y++) {
			int i = y * width + roiX;
			for (int x=roiX; x<(roiX+roiWidth); x++)
				pixels[i++] = fgColor;
			if (y%20==0)
				showProgress((double)(y-roiY)/roiHeight);
		}
		hideProgress();
	}


	public final int RGB_NOISE=0, RGB_MEDIAN=1, RGB_FIND_EDGES=2,
		RGB_ERODE=3, RGB_DILATE=4, RGB_THRESHOLD=5;

 	/** Performs the specified 3x3 filter on the reg, green and blue planes. */
 	public void filterRGB(int type, double arg) {
		showProgress(0.01);
		byte[] R = new byte[width*height];
		byte[] G = new byte[width*height];
		byte[] B = new byte[width*height];
		getRGB(R, G, B);
		Rectangle roi = new Rectangle(roiX, roiY, roiWidth, roiHeight);
		
		ByteProcessor r = new ByteProcessor(width, height, R, null);
		r.setRoi(roi);
		ByteProcessor g = new ByteProcessor(width, height, G, null);
		g.setRoi(roi);
		ByteProcessor b = new ByteProcessor(width, height, B, null);
		b.setRoi(roi);
		
		showProgress(0.15);
		switch (type) {
			case RGB_NOISE:
				r.noise(arg); showProgress(0.40);
				g.noise(arg); showProgress(0.65);
				b.noise(arg); showProgress(0.90);
				break;
			case RGB_MEDIAN:
				r.medianFilter(); showProgress(0.40);
				g.medianFilter(); showProgress(0.65);
				b.medianFilter(); showProgress(0.90);
				break;
			case RGB_FIND_EDGES:
				r.findEdges(); showProgress(0.40);
				g.findEdges(); showProgress(0.65);
				b.findEdges(); showProgress(0.90);
				break;
			case RGB_ERODE:
				r.erode(); showProgress(0.40);
				g.erode(); showProgress(0.65);
				b.erode(); showProgress(0.90);
				break;
			case RGB_DILATE:
				r.dilate(); showProgress(0.40);
				g.dilate(); showProgress(0.65);
				b.dilate(); showProgress(0.90);
				break;
			case RGB_THRESHOLD:
				r.autoThreshold(); showProgress(0.40);
				g.autoThreshold(); showProgress(0.65);
				b.autoThreshold(); showProgress(0.90);
				break;
		}
		
		R = (byte[])r.getPixels();
		G = (byte[])g.getPixels();
		B = (byte[])b.getPixels();
		
		setRGB(R, G, B);
		hideProgress();
	}

   public void noise(double range) {
    	filterRGB(RGB_NOISE, range);
    }

	public void medianFilter() {
    	filterRGB(RGB_MEDIAN, 0.0);
	}
	
	public void findEdges() {
    	filterRGB(RGB_FIND_EDGES, 0.0);
	}		
		
	public void erode() {
    	filterRGB(RGB_ERODE, 0.0);
	}
			
	public void dilate() {
    	filterRGB(RGB_DILATE, 0.0);

	}
			
	public void autoThreshold() {
   		filterRGB(RGB_THRESHOLD, 0.0);
	}
	
	/** Scales the image or selection using the specified scale factors.
		@see ImageProcessor#setInterpolate
	*/
	public void scale(double xScale, double yScale) {
		double xCenter = roiX + roiWidth/2.0;
		double yCenter = roiY + roiHeight/2.0;
		int xmin, xmax, ymin, ymax;
		
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
		int[] pixels2 = (int[])getPixelsCopy();
		boolean checkCoordinates = (xScale < 1.0) || (yScale < 1.0);
		int index1, index2, xsi, ysi;
		double ys, xs;
		for (int y=ymin; y<=ymax; y++) {
			ys = (y-yCenter)/yScale + yCenter;
			ysi = (int)ys;
			index1 = y*width + xmin;
			index2 = width*(int)ys;
			for (int x=xmin; x<=xmax; x++) {
				xs = (x-xCenter)/xScale + xCenter;
				xsi = (int)xs;
				if (checkCoordinates && ((xsi<xmin) || (xsi>xmax) || (ysi<ymin) || (ys>ymax)))
					pixels[index1++] = (byte)bgColor;
				else {
					if (interpolate)
						pixels[index1++] = getInterpolatedPixel(xs, ys, pixels2);
					else
						pixels[index1++] = pixels2[index2+xsi];
				}
			}
			if (y%20==0)
			showProgress((double)(y-ymin)/height);
		}
		hideProgress();
	}

	public ImageProcessor crop() {
		int[] pixels2 = new int[roiWidth*roiHeight];
		for (int ys=roiY; ys<roiY+roiHeight; ys++)
			System.arraycopy(pixels, roiX+ys*width, pixels2, (ys-roiY)*roiWidth, roiWidth);
		return new ColorProcessor(roiWidth, roiHeight, pixels2);
	}
	

	/** Uses bilinear interpolation to find the pixel value at real coordinates (x,y). */
	private final int getInterpolatedPixel(double x, double y, int[] pixels) {
		int xbase = (int)x;
		int ybase = (int)y;
		double xFraction = x - xbase;
		double yFraction = y - ybase;
		int offset = ybase * width + xbase;
		
		int lowerLeft = pixels[offset];
		if ((xbase>=(width-1))||(ybase>=(height-1)))
			return lowerLeft;
		int rll = (lowerLeft&0xff0000)>>16;
		int gll = (lowerLeft&0xff00)>>8;
		int bll = lowerLeft&0xff;
		
		int lowerRight = pixels[offset + 1];
		int rlr = (lowerRight&0xff0000)>>16;
		int glr = (lowerRight&0xff00)>>8;
		int blr = lowerRight&0xff;

		int upperRight = pixels[offset + width + 1];
		int rur = (upperRight&0xff0000)>>16;
		int gur = (upperRight&0xff00)>>8;
		int bur = upperRight&0xff;

		int upperLeft = pixels[offset + width];
		int rul = (upperLeft&0xff0000)>>16;
		int gul = (upperLeft&0xff00)>>8;
		int bul = upperLeft&0xff;
		
		int r, g, b;
		double upperAverage, lowerAverage;
		upperAverage = rul + xFraction * (rur - rul);
		lowerAverage = rll + xFraction * (rlr - rll);
		r = (int)(lowerAverage + yFraction * (upperAverage - lowerAverage)+0.5);
		upperAverage = gul + xFraction * (gur - gul);
		lowerAverage = gll + xFraction * (glr - gll);
		g = (int)(lowerAverage + yFraction * (upperAverage - lowerAverage)+0.5);
		upperAverage = bul + xFraction * (bur - bul);
		lowerAverage = bll + xFraction * (blr - bll);
		b = (int)(lowerAverage + yFraction * (upperAverage - lowerAverage)+0.5);

		return 0xff000000 | ((r&0xff)<<16) | ((g&0xff)<<8) | b&0xff;
	}

	/** Creates a new ColorProcessor containing a scaled copy of this image or selection.
		@see ImageProcessor#setInterpolate
	*/
	public ImageProcessor resize(int dstWidth, int dstHeight) {
		double srcCenterX = roiX + roiWidth/2.0;
		double srcCenterY = roiY + roiHeight/2.0;
		double dstCenterX = dstWidth/2.0;
		double dstCenterY = dstHeight/2.0;
		double xScale = (double)dstWidth/roiWidth;
		double yScale = (double)dstHeight/roiHeight;
		ImageProcessor ip2 = createProcessor(dstWidth, dstHeight);
		int[] pixels2 = (int[])ip2.getPixels();
		double xs, ys=0.0;
		int index1, index2;
		for (int y=0; y<=dstHeight-1; y++) {
			index1 = width*(int)ys;
			index2 = y*dstWidth;
			ys = (y-dstCenterY)/yScale + srcCenterY;
			for (int x=0; x<=dstWidth-1; x++) {
				xs = (x-dstCenterX)/xScale + srcCenterX;
				if (interpolate)
					pixels2[index2++] = getInterpolatedPixel(xs, ys, pixels);
				else
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
		if (interpolate) {
			rotateInterpolated(angle);
			return;
		}
        int[] pixels2 = (int[])getPixelsCopy();
		int centerX = roiX + roiWidth/2;
		int centerY = roiY + roiHeight/2;
		int width = this.width;  //Local variables are faster than instance variables
        int height = this.height;
        int roiX = this.roiX;
        int xMax = roiX + this.roiWidth - 1;
        int SCALE = 1024;

        //Convert from degrees to radians and calculate cos and sin of angle
        //Negate the angle to make sure the rotation is clockwise
        double angleRadians = -angle/(180.0/Math.PI);
        int ca = (int)(Math.cos(angleRadians)*SCALE);
        int sa = (int)(Math.sin(angleRadians)*SCALE);
        int temp1 = centerY*sa - centerX*ca;
        int temp2 = -centerX*sa - centerY*ca;
        
        for (int y=roiY; y<(roiY + roiHeight); y++) {
            int index = y*width + roiX;
            int temp3 = (temp1 - y*sa)/SCALE + centerX;
            int temp4 = (temp2 + y*ca)/SCALE + centerY;
            for (int x=roiX; x<=xMax; x++) {
                //int xs = (int)((x-centerX)*ca-(y-centerY)*sa)+centerX;
                //int ys = (int)((y-centerY)*ca+(x-centerX)*sa)+centerY;
                int xs = (x*ca)/SCALE + temp3;
                int ys = (x*sa)/SCALE + temp4;
                if ((xs>=0) && (xs<width) && (ys>=0) && (ys<height))
                	  pixels[index++] = pixels2[width*ys+xs];
                else
                	  pixels[index++] = bgColor;
            }
		if (y%20==0)
			showProgress((double)(y-roiY)/roiHeight);
        }
		hideProgress();
	}

	private void rotateInterpolated(double angle) {
		int[] pixels2 = (int[])getPixelsCopy();
		double centerX = roiX + roiWidth/2.0;
		double centerY = roiY + roiHeight/2.0;
		int xMax = roiX + this.roiWidth - 1;
		
		double angleRadians = -angle/(180.0/Math.PI);
		double ca = Math.cos(angleRadians);
		double sa = Math.sin(angleRadians);
		double tmp1 = centerY*sa-centerX*ca;
		double tmp2 = -centerX*sa-centerY*ca;
		double tmp3, tmp4, xs, ys;
		int index, xsi, ysi;
		
		for (int y=roiY; y<(roiY + roiHeight); y++) {
			index = y*width + roiX;
			tmp3 = tmp1 - y*sa + centerX;
			tmp4 = tmp2 + y*ca + centerY;
			for (int x=roiX; x<=xMax; x++) {
				xs = x*ca + tmp3;
				ys = x*sa + tmp4;
				if ((xs>=0.0) && (xs<width) && (ys>=0.0) && (ys<height))
				  	pixels[index++] = getInterpolatedPixel(xs, ys, pixels2);
				else
					pixels[index++] = bgColor;
			}
			if (y%20==0)
			showProgress((double)(y-roiY)/roiHeight);
		}
		hideProgress();
	}

	/** 3x3 convolution contributed by Glynne Casteel. */
	public void convolve3x3(int[] kernel) {
		int p1, p2, p3, p4, p5, p6, p7, p8, p9;
		int k1=kernel[0], k2=kernel[1], k3=kernel[2],
		    k4=kernel[3], k5=kernel[4], k6=kernel[5],
		    k7=kernel[6], k8=kernel[7], k9=kernel[8];

		int scale = 0;
		for (int i=0; i<kernel.length; i++)
			scale += kernel[i];
		if (scale==0) scale = 1;
		int inc = roiHeight/25;
		if (inc<1) inc = 1;
		
		int[] pixels2 = (int[])getPixelsCopy();
		int offset;
		int rsum = 0, gsum = 0, bsum = 0;
        int rowOffset = width;
		for (int y=yMin; y<=yMax; y++) {
			offset = xMin + y * width;
			p1 = 0;
			p2 = pixels2[offset-rowOffset-1];
			p3 = pixels2[offset-rowOffset];
			p4 = 0;
			p5 = pixels2[offset-1];
			p6 = pixels2[offset];
			p7 = 0;
			p8 = pixels2[offset+rowOffset-1];
			p9 = pixels2[offset+rowOffset];

			for (int x=xMin; x<=xMax; x++) {
				p1 = p2; p2 = p3;
				p3 = pixels2[offset-rowOffset+1];
				p4 = p5; p5 = p6;
				p6 = pixels2[offset+1];
				p7 = p8; p8 = p9;
				p9 = pixels2[offset+rowOffset+1];

				rsum = k1*((p1 & 0xff0000) >> 16)
				     + k2*((p2 & 0xff0000) >> 16)
				     + k3*((p3 & 0xff0000) >> 16)
				     + k4*((p4 & 0xff0000) >> 16)
				     + k5*((p5 & 0xff0000) >> 16)
				     + k6*((p6 & 0xff0000) >> 16)
				     + k7*((p7 & 0xff0000) >> 16)
				     + k8*((p8 & 0xff0000) >> 16)
				     + k9*((p9 & 0xff0000) >> 16);
				rsum /= scale;
				if(rsum>255) rsum = 255;
				if(rsum<0) rsum = 0;

				gsum = k1*((p1 & 0xff00) >> 8)
				     + k2*((p2 & 0xff00) >> 8)
				     + k3*((p3 & 0xff00) >> 8)
				     + k4*((p4 & 0xff00) >> 8)
				     + k5*((p5 & 0xff00) >> 8)
				     + k6*((p6 & 0xff00) >> 8)
				     + k7*((p7 & 0xff00) >> 8)
				     + k8*((p8 & 0xff00) >> 8)
				     + k9*((p9 & 0xff00) >> 8);
				gsum /= scale;
				if(gsum>255) gsum = 255;
				else if(gsum<0) gsum = 0;

				bsum = k1*(p1 & 0xff)
				     + k2*(p2 & 0xff)
				     + k3*(p3 & 0xff)
				     + k4*(p4 & 0xff)
				     + k5*(p5 & 0xff)
				     + k6*(p6 & 0xff)
				     + k7*(p7 & 0xff)
				     + k8*(p8 & 0xff)
				     + k9*(p9 & 0xff);
				bsum /= scale;
				if (bsum>255) bsum = 255;
				if (bsum<0) bsum = 0; 

				pixels[offset++] = 0xff000000
				                 | ((rsum << 16) & 0xff0000)
				                 | ((gsum << 8 ) & 0xff00)
				                 |  (bsum        & 0xff);
			}
			if (y%inc==0)
				showProgress((double)(y-roiY)/roiHeight);
		}
		hideProgress();
	}

	/** 3x3 unweighted smoothing. */
	public void filter(int type) {
		int p1, p2, p3, p4, p5, p6, p7, p8, p9;
		int inc = roiHeight/25;
		if (inc<1) inc = 1;
		
		int[] pixels2 = (int[])getPixelsCopy();
		int offset, rsum=0, gsum=0, bsum=0;
        int rowOffset = width;
		for (int y=yMin; y<=yMax; y++) {
			offset = xMin + y * width;
			p1 = 0;
			p2 = pixels2[offset-rowOffset-1];
			p3 = pixels2[offset-rowOffset];
			p4 = 0;
			p5 = pixels2[offset-1];
			p6 = pixels2[offset];
			p7 = 0;
			p8 = pixels2[offset+rowOffset-1];
			p9 = pixels2[offset+rowOffset];

			for (int x=xMin; x<=xMax; x++) {
				p1 = p2; p2 = p3;
				p3 = pixels2[offset-rowOffset+1];
				p4 = p5; p5 = p6;
				p6 = pixels2[offset+1];
				p7 = p8; p8 = p9;
				p9 = pixels2[offset+rowOffset+1];
				rsum = (p1 & 0xff0000) + (p2 & 0xff0000) + (p3 & 0xff0000) + (p4 & 0xff0000) + (p5 & 0xff0000)
					+ (p6 & 0xff0000) + (p7 & 0xff0000) + (p8 & 0xff0000) + (p9 & 0xff0000);
				gsum = (p1 & 0xff00) + (p2 & 0xff00) + (p3 & 0xff00) + (p4 & 0xff00) + (p5 & 0xff00)
					+ (p6 & 0xff00) + (p7 & 0xff00) + (p8 & 0xff00) + (p9 & 0xff00);
				bsum = (p1 & 0xff) + (p2 & 0xff) + (p3 & 0xff) + (p4 & 0xff) + (p5 & 0xff)
					+ (p6 & 0xff) + (p7 & 0xff) + (p8 & 0xff) + (p9 & 0xff);
				pixels[offset++] = 0xff000000 | ((rsum/9) & 0xff0000) | ((gsum/9) & 0xff00) | (bsum/9);
			}
			if (y%inc==0)
				showProgress((double)(y-roiY)/roiHeight);
		}
		hideProgress();
	}

	public int[] getHistogram() {
		if (mask!=null)
			return getHistogram(mask);
		int c, r, g, b, v;
		int[] histogram = new int[256];
		for (int y=roiY; y<(roiY+roiHeight); y++) {
			int i = y * width + roiX;
			for (int x=roiX; x<(roiX+roiWidth); x++) {
				c = pixels[i++];
				r = (c&0xff0000)>>16;
				g = (c&0xff00)>>8;
				b = c&0xff;
				v = (int)(r*0.30 + g*0.59 + b*0.11);
				histogram[v]++;
			}
			if (y%20==0)
				showProgress((double)(y-roiY)/roiHeight);
		}
		hideProgress();
		return histogram;
	}


	public int[] getHistogram(int[] mask) {
		int c, r, g, b, v;
		int[] histogram = new int[256];
		for (int y=roiY, my=0; y<(roiY+roiHeight); y++, my++) {
			int i = y * width + roiX;
			int mi = my * roiWidth;
			for (int x=roiX; x<(roiX+roiWidth); x++) {
				if (mask[mi++]==BLACK) {
					c = pixels[i];
					r = (c&0xff0000)>>16;
					g = (c&0xff00)>>8;
					b = c&0xff;
					v = (int)(r*0.30 + g*0.59 + b*0.11);
					histogram[v]++;
					//histogram[r]++;
					//histogram[g]++;
					//histogram[b]++;
				}
				i++;
			}
			if (y%20==0)
				showProgress((double)(y-roiY)/roiHeight);
		}
		hideProgress();
		return histogram;
	}

	/** Always returns false since RGB images do not use LUTs. */
	public boolean isInvertedLut() {
		return false;
	}
	
	/** Always returns 0 since RGB images do not use LUTs. */
	public int getBestIndex(Color c) {
		return 0;
	}
	
	/** Does nothing since RGB images do not use LUTs. */
	public void invertLut() {
	}
	
}

