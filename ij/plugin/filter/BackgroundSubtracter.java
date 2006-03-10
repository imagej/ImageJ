package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import java.awt.*;

/** Implements ImageJ's Subtract Background command. Based on
	the NIH Image Pascal version by Michael Castle and Janice 
	Keller of the University of Michigan Mental Health Research
	Institute. Rolling ball algorithm inspired by Stanley 
	Sternberg's article, "Biomedical Image Processing",
	IEEE Computer, January 1983.                                                                                                                               
*/
public class BackgroundSubtracter implements PlugInFilter {

	private static int radius = 50; // default rolling ball radius
	private static boolean whiteBackground = true;
	private ImagePlus imp;
	private boolean canceled;
	private int slice;
	private boolean invert;
	
	public int setup(String arg, ImagePlus imp) {
		IJ.register(BackgroundSubtracter.class);
		this.imp = imp;
		if (imp!=null) {
			showDialog();
			if (canceled)
				return DONE;
		}
		IJ.register(BackgroundSubtracter.class);
		return IJ.setupDialog(imp, DOES_8G+DOES_16+DOES_RGB);
	}

	public void run(ImageProcessor ip) {
		if (canceled)
			return;
		slice++;
		if (slice>1)
			IJ.showStatus("Subtract Background: "+slice+"/"+imp.getStackSize());
		if (ip instanceof ColorProcessor)
			subtractRGBBackround((ColorProcessor)ip, radius);
		else
			subtractBackround(ip, radius);
		if (slice==1 && ip instanceof ShortProcessor)
			imp.getProcessor().resetMinAndMax();
	}

	public void showDialog() {
		GenericDialog gd = new GenericDialog("Subtract Background");
		gd.addNumericField("Rolling Ball Radius:", radius, 0);
		gd.addCheckbox("White Background", whiteBackground);
		gd.showDialog();
		if (gd.wasCanceled())
			canceled = true;
		else {
			radius = (int)gd.getNextNumber();
			whiteBackground = gd.getNextBoolean();
		}
		boolean invertedLut = imp.isInvertedLut();
		invert = (invertedLut && !whiteBackground) || (!invertedLut && whiteBackground);
	}

	public void subtractRGBBackround(ColorProcessor ip, int ballRadius) {
		int width = ip.getWidth();
		int height = ip.getHeight();
		byte[] H = new byte[width*height];
		byte[] S = new byte[width*height];
		byte[] B = new byte[width*height];
		ip.getHSB(H, S, B);
		ByteProcessor brightness = new ByteProcessor(width, height, B, null);
		subtractBackround(brightness, radius);
		ip.setHSB(H, S, (byte[])brightness.getPixels());
	}

	/** Implements a rolling-ball algorithm for the removal of smooth continuous background
		from a two-dimensional gel image.  It rolls the ball (actually a square patch on the
		top of a sphere) on a low-resolution (by a factor of 'shrinkfactor' times) copy of
		the original image in order to increase speed with little loss in accuracy.  It uses
		interpolation and extrapolation to blow the shrunk image to full size.
	*/
	public void subtractBackround(ImageProcessor ip, int ballRadius) {
 		if (imp!=null)
 			imp.killRoi();
 		else
 			ip.resetRoi();
 		ip.setProgressBar(null);
 		IJ.showProgress(0.0);
 		if (invert)
 			ip.invert();
 		RollingBall ball = new RollingBall(ballRadius);
		//new ImagePlus("ball", new ByteProcessor(ball.patchwidth+1, ball.patchwidth+1, ball.data, null)).show();
		//ImageProcessor smallImage = ip.resize(ip.getWidth()/ball.shrinkfactor, ip.getHeight()/ball.shrinkfactor);
		ImageProcessor smallImage = shrinkImage(ip, ball.shrinkfactor);
		//new ImagePlus("small image", smallImage).show();
		if (slice==1)
 			IJ.showStatus("Rolling ball ("+ball.shrinkfactor+")...");
		ImageProcessor background;
 		if (ip instanceof ShortProcessor) {
			background = rollBall16(ball, ip, smallImage);
			interpolateBackground16(background, ball);
			extrapolateBackground16(background, ball);
		} else {
			background = rollBall(ball, ip, smallImage);
			interpolateBackground(background, ball);
			extrapolateBackground(background, ball);
		}
		IJ.showProgress(0.9);
		if (IJ.altKeyDown())
			new ImagePlus("background", background).show();
		ip.copyBits(background, 0, 0, Blitter.SUBTRACT);
		if (invert)
			ip.invert();
		IJ.showProgress(1.0);
	}

	/** 'Rolls' a filtering object over a (shrunken) image in order to find the
		image's smooth continuous background.  For the purpose of explaining this
		algorithm, imagine that the 2D grayscale image has a third (height) dimension
		defined by the intensity value at every point in the image.  The center of
		the filtering object, a patch from the top of a sphere having radius BallRadius,
		is moved along each scan line of the image so that the patch is tangent to the
		image at one or more points with every other point on the patch below the
		corresponding (x,y) point of the image.  Any point either on or below the patch
		during this process is considered part of the background.  Shrinking the image
		before running this procedure is advised due to the fourth-degree complexity
		of the algorithm.
	*/                                                                                                               
	ImageProcessor rollBall(RollingBall ball, ImageProcessor image, ImageProcessor smallImage) {

		int halfpatchwidth;		//distance in x or y from patch center to any edge
		int ptsbelowlastpatch;	//number of points we may ignore because they were below last patch
		int xpt2, ypt2;			// current (x,y) point in the patch relative to upper left corner
		int xval, yval;			// location in ball in shrunken image coordinates
		int zdif;				// difference in z (height) between point on ball and point on image
		int zmin;				// smallest zdif for ball patch with center at current point
		int zctr;				// current height of the center of the sphere of which the patch is a part
		int zadd;				// height of a point on patch relative to the xy-plane of the shrunken image
		int ballpt;				// index to array storing the precomputed ball patch
		int imgpt;				// index to array storing the shrunken image
		int backgrpt;			// index to array storing the calculated background
		int ybackgrpt;			// displacement to current background scan line
		int p1, p2;				// temporary indexes to background, ball, or small image
		int ybackgrinc;				// distance in memory between two shrunken y-points in background
		int smallimagewidth;	// length of a scan line in shrunken image
		int left, right, top, bottom;
		byte[] pixels = (byte[])smallImage.getPixels();
		byte[] patch = ball.data;
		int width = image.getWidth();
		int height = image.getHeight();
		int swidth = smallImage.getWidth();
		int sheight = smallImage.getHeight();
		ImageProcessor background = new ByteProcessor(width, height);
		byte[] backgroundpixels = (byte[])background.getPixels();
		int shrinkfactor = ball.shrinkfactor;
		int leftroll = 0;
		int rightroll = width/shrinkfactor-1;
		int toproll = 0;
		int bottomroll = height/shrinkfactor-1;
		
		left = 1;
		right = rightroll - leftroll - 1;
		top = 1;
		bottom = bottomroll - toproll - 1;
		smallimagewidth = swidth;
		int patchwidth = ball.patchwidth;
		halfpatchwidth = patchwidth / 2;
		ybackgrinc = shrinkfactor*width; // real dist btwn 2 adjacent (dy=1) shrunk pts
		zctr = 0; // start z-center in the xy-plane
		for (int ypt=top; ypt<=(bottom+patchwidth); ypt++) {
			for (int xpt=left; xpt<=(right+patchwidth); xpt++) {// while patch is tangent to edges or within image...
				// xpt is far right edge of ball patch
				// do we have to move the patch up or down to make it tangent to but not above image?...
				zmin = 255; // highest could ever be 255
				ballpt = 0;
				ypt2 = ypt - patchwidth; // ypt2 is top edge of ball patch
				imgpt = ypt2*smallimagewidth + xpt - patchwidth;
				while (ypt2<=ypt) {
					xpt2 = xpt-patchwidth; // xpt2 is far left edge of ball patch
					while (xpt2<=xpt) { // check every point on ball patch
						// only examine points on
						if ((xpt2>=left) && (xpt2<=right) && (ypt2>=top) && (ypt2<=bottom)) {
							p1 = ballpt;
							p2 = imgpt;
							zdif = (pixels[p2]&255) - (zctr + (patch[p1]&255));  //curve - circle points
							//if (xpt==50 && ypt==50) IJ.write(zdif
							//+" "+(pixels[p2]&255)
							//+" "+zctr
							//+" "+(patch[p1]&255)
							//+" "+p2
							//+" "+p1
							//);
							if (zdif<zmin) // keep most negative, since ball should always be below curve
								zmin = zdif;
						} // if xpt2,ypt2
						ballpt++;
						xpt2++;
						imgpt++;
					} // while xpt2
					ypt2++;
					imgpt = imgpt - patchwidth - 1 + smallimagewidth;
				}  // while ypt2
				if (zmin!=0)
					zctr += zmin; // move ball up or down if we find a new minimum
				if (zmin<0)
					ptsbelowlastpatch = halfpatchwidth; // ignore left half of ball patch when dz < 0
				else
					ptsbelowlastpatch = 0;
				// now compare every point on ball with background,  and keep highest number
				yval = ypt - patchwidth;
				ypt2 = 0;
				ballpt = 0;
				ybackgrpt = (yval - top + 1) * ybackgrinc;
				while (ypt2<=patchwidth) {
					xval = xpt - patchwidth + ptsbelowlastpatch;
					xpt2 = ptsbelowlastpatch;
					ballpt += ptsbelowlastpatch;
					backgrpt = ybackgrpt + (xval - left + 1) * shrinkfactor;
					while (xpt2<=patchwidth) { // for all the points in the ball patch
						if ((xval >= left) && (xval <= right) && (yval >= top) && (yval <= bottom)) {
							p1 = ballpt;
							zadd = zctr + (patch[p1]&255);
							p1 = backgrpt;
							//if (backgrpt>=backgroundpixels.length) backgrpt = 0; //(debug)
							if (zadd>(backgroundpixels[p1]&255)) //keep largest adjustment}
								backgroundpixels[p1] = (byte)zadd;
						}
						ballpt++;
						xval++;
						xpt2++;
						backgrpt += shrinkfactor; // move to next point in x
					} // while xpt2
					yval++;
					ypt2++;
					ybackgrpt += ybackgrinc; // move to next point in y
				} // while ypt2
			} // for xpt
			if (ypt%20==0)
				IJ.showProgress(0.2+0.6*ypt/(bottom+patchwidth));
		} // for ypt
		return background;
	}
	
	/** Creates a lower resolution image for ball-rolling. */
	ImageProcessor shrinkImage(ImageProcessor ip, int shrinkfactor) {
		int width = ip.getWidth();
		int height = ip.getHeight();
		int swidth = width/shrinkfactor;
		int sheight = height/shrinkfactor;
		ImageProcessor ip2 = ip.duplicate();
		ip2.smooth();
		IJ.showProgress(0.1);
		ImageProcessor smallImage = ip.createProcessor(swidth, sheight);
		int xmaskmin, ymaskmin, min, thispixel;
		for (int y=0; y<sheight; y++) {
			for (int x=0; x<swidth; x++) {
				xmaskmin = shrinkfactor*x;
				ymaskmin = shrinkfactor*y;
				min = 65535;
				for (int j=0; j<shrinkfactor; j++) {
					for (int k=0; k<shrinkfactor; k++) {
						thispixel = ip2.getPixel(xmaskmin+j, ymaskmin+k);
						if (thispixel<min)
							min = thispixel;
					}
				}
				smallImage.putPixel(x,y,min); // each point in small image is minimum of its neighborhood
			}
		}
		//new ImagePlus("smallImage", smallImage).show();
		return smallImage;
	}

/** Uses bilinear interpolation to find the points in the full-scale background
		given the points from the shrunken image background.  Since the shrunken background
		is found from an image composed of minima (over a sufficiently large mask), it
		is certain that no point in the full-scale interpolated background has a higher
		pixel value than the corresponding point in the original image
	*/                                 
	void interpolateBackground(ImageProcessor background, RollingBall ball) {
		int hloc, vloc;	// position of current pixel in calculated background
		int vinc;		// memory offset from current calculated pos to current interpolated pos
		int lastvalue, nextvalue;	// calculated pixel values between which we are interpolating
		int p;			// pointer to current interpolated pixel value
		int bglastptr, bgnextptr;	// pointers to calculated pixel values between which we are interpolating

		int width = background.getWidth();
		int height = background.getHeight();
		int shrinkfactor = ball.shrinkfactor;
		int leftroll = 0;
		int rightroll = width/shrinkfactor-1;
		int toproll = 0;
		int bottomroll = height/shrinkfactor-1;
		byte[] pixels = (byte[])background.getPixels();
		
		vloc = 0;
		for (int j=1; j<=(bottomroll-toproll-1); j++) { //interpolate to find background interior
			hloc = 0;
			vloc += shrinkfactor;
			for (int i=1; i<=(rightroll-leftroll); i++) {
				hloc += shrinkfactor;
				bgnextptr = vloc*width + hloc;
				bglastptr = bgnextptr - shrinkfactor;
				nextvalue = pixels[bgnextptr]&255;
				lastvalue = pixels[bglastptr]&255;
				for (int ii=1; ii<=(shrinkfactor-1); ii++) { //interpolate horizontally
					p = bgnextptr - ii;
					pixels[p] = (byte)(lastvalue+(shrinkfactor-ii)*(nextvalue-lastvalue)/shrinkfactor);
				}
				for (int ii=0; ii<=(shrinkfactor-1); ii++) { //interpolate vertically
					bglastptr = (vloc-shrinkfactor)*width+hloc-ii;
					bgnextptr = vloc*width+hloc-ii;
					lastvalue = pixels[bglastptr]&255;
					nextvalue = pixels[bgnextptr]&255;
					vinc = 0;
					for (int jj=1; jj<=(shrinkfactor-1); jj++) {
						vinc = vinc-width;
						p = bgnextptr+vinc;
						pixels[p] = (byte)(lastvalue+(shrinkfactor-jj)*(nextvalue-lastvalue)/shrinkfactor);
					} // for jj
				} // for ii
			} // for i
		} // for j
	}

	/** Uses linear extrapolation to find pixel values on the top, left, right,
		and bottom edges of the background.  First it finds the top and bottom
		edge points by extrapolating from the edges of the calculated and
		interpolated background interior.  Then it uses the edge points on the
		new calculated, interpolated, and extrapolated background to find all
		of the left and right edge points.  If extrapolation yields values
		below zero or above 255, then they are set to zero and 255 respectively.
	*/                                             
	void extrapolateBackground(ImageProcessor background, RollingBall ball) {
	
		int edgeslope;			// difference of last two consecutive pixel values on an edge
		int pvalue;				// current extrapolated pixel value
		int lastvalue, nextvalue;	//calculated pixel values from which we are extrapolating
		int p;					// pointer to current extrapolated pixel value
		int bglastptr, bgnextptr;	// pointers to calculated pixel values from which we are extrapolating

		int width = background.getWidth();
		int height = background.getHeight();
		int shrinkfactor = ball.shrinkfactor;
		int leftroll = 0;
		int rightroll = width/shrinkfactor-1;
		int toproll = 0;
		int bottomroll = height/shrinkfactor-1;
		byte[] pixels = (byte[])background.getPixels();
		
		for (int hloc=shrinkfactor; hloc<=(shrinkfactor*(rightroll-leftroll)-1); hloc++) {
			// extrapolate on top and bottom
			bglastptr = shrinkfactor*width+hloc;
			bgnextptr = (shrinkfactor+1)*width+hloc;
			lastvalue = pixels[bglastptr]&255;
			nextvalue = pixels[bgnextptr]&255;
			edgeslope = nextvalue-lastvalue;
			p = bglastptr;
			pvalue = lastvalue;
			for (int jj=1; jj<=shrinkfactor; jj++) {
				p = p-width;
				pvalue = pvalue-edgeslope;
				if (pvalue<0)
					pixels[p] = 0;
				else if (pvalue>255)
					pixels[p] = (byte)255;
				else
					pixels[p] = (byte)pvalue;
			} // for jj
			bglastptr = (shrinkfactor*(bottomroll-toproll-1)-1)*width+hloc;
			bgnextptr = shrinkfactor*(bottomroll-toproll-1)*width+hloc;
			lastvalue = pixels[bglastptr]&255;
			nextvalue = pixels[bgnextptr]&255;
			edgeslope = nextvalue-lastvalue;
			p = bgnextptr;
			pvalue = nextvalue;
			for (int jj=1; jj<=((height-1)-shrinkfactor*(bottomroll-toproll-1)); jj++) {
				p += width;
				pvalue += edgeslope;
				if (pvalue<0)
					pixels[p] = 0;
				else if (pvalue>255)
					pixels[p] = (byte)255;
				else
					pixels[p] = (byte)pvalue;
			} // for jj
		} // for hloc
		for (int vloc=0; vloc<height; vloc++) {
			// extrapolate on left and right
			bglastptr = vloc*width+shrinkfactor;
			bgnextptr = bglastptr+1;
			lastvalue = pixels[bglastptr]&255;
			nextvalue = pixels[bgnextptr]&255;
			edgeslope = nextvalue-lastvalue;
			p = bglastptr;
			pvalue = lastvalue;
			for (int ii=1; ii<=shrinkfactor; ii++) {
				p--;
				pvalue = pvalue - edgeslope;
				if (pvalue<0)
					pixels[p] = 0;
				else if (pvalue>255)
					pixels[p] = (byte)255;
				else
					pixels[p] = (byte)pvalue;
			} // for ii
			bgnextptr = vloc*width+shrinkfactor*(rightroll-leftroll-1)-1;
			bglastptr = bgnextptr-1;
			lastvalue = pixels[bglastptr]&255;
			nextvalue = pixels[bgnextptr]&255;
			edgeslope = nextvalue-lastvalue;
			p = bgnextptr;
			pvalue = nextvalue;
			for (int ii=1; ii<=((width-1)-shrinkfactor*(rightroll-leftroll-1)+1); ii++) {
				p++;
				pvalue = pvalue+edgeslope;
				if (pvalue<0)
					pixels[p] = 0;
				else if (pvalue>255)
					pixels[p] = (byte)255;
				else
					pixels[p] = (byte)pvalue;
			} // for ii
		} // for vloc
	}

	/** This is a 16-bit version of the rollBall() method. */                                                                                                               
	ImageProcessor rollBall16(RollingBall ball, ImageProcessor image, ImageProcessor smallImage) {

		int halfpatchwidth;		//distance in x or y from patch center to any edge
		int ptsbelowlastpatch;	//number of points we may ignore because they were below last patch
		int xpt2, ypt2;			// current (x,y) point in the patch relative to upper left corner
		int xval, yval;			// location in ball in shrunken image coordinates
		int zdif;				// difference in z (height) between point on ball and point on image
		int zmin;				// smallest zdif for ball patch with center at current point
		int zctr;				// current height of the center of the sphere of which the patch is a part
		int zadd;				// height of a point on patch relative to the xy-plane of the shrunken image
		int ballpt;				// index to array storing the precomputed ball patch
		int imgpt;				// index to array storing the shrunken image
		int backgrpt;			// index to array storing the calculated background
		int ybackgrpt;			// displacement to current background scan line
		int p1, p2;				// temporary indexes to background, ball, or small image
		int ybackgrinc;				// distance in memory between two shrunken y-points in background
		int smallimagewidth;	// length of a scan line in shrunken image
		int left, right, top, bottom;
		short[] pixels = (short[])smallImage.getPixels();
		byte[] patch = ball.data;
		int width = image.getWidth();
		int height = image.getHeight();
		int swidth = smallImage.getWidth();
		int sheight = smallImage.getHeight();
		ImageProcessor background = new ShortProcessor(width, height);
		short[] backgroundpixels = (short[])background.getPixels();
		int shrinkfactor = ball.shrinkfactor;
		int leftroll = 0;
		int rightroll = width/shrinkfactor-1;
		int toproll = 0;
		int bottomroll = height/shrinkfactor-1;
		
		left = 1;
		right = rightroll - leftroll - 1;
		top = 1;
		bottom = bottomroll - toproll - 1;
		smallimagewidth = swidth;
		int patchwidth = ball.patchwidth;
		halfpatchwidth = patchwidth / 2;
		ybackgrinc = shrinkfactor*width; // real dist btwn 2 adjacent (dy=1) shrunk pts
		zctr = 0; // start z-center in the xy-plane
		for (int ypt=top; ypt<=(bottom+patchwidth); ypt++) {
			for (int xpt=left; xpt<=(right+patchwidth); xpt++) {// while patch is tangent to edges or within image...
				// xpt is far right edge of ball patch
				// do we have to move the patch up or down to make it tangent to but not above image?...
				zmin = 65535; // highest possible value
				ballpt = 0;
				ypt2 = ypt - patchwidth; // ypt2 is top edge of ball patch
				imgpt = ypt2*smallimagewidth + xpt - patchwidth;
				while (ypt2<=ypt) {
					xpt2 = xpt-patchwidth; // xpt2 is far left edge of ball patch
					while (xpt2<=xpt) { // check every point on ball patch
						// only examine points on
						if ((xpt2>=left) && (xpt2<=right) && (ypt2>=top) && (ypt2<=bottom)) {
							p1 = ballpt;
							p2 = imgpt;
							zdif = (pixels[p2]&0xffff) - (zctr + (patch[p1]&255));  //curve - circle points
							if (zdif<zmin) // keep most negative, since ball should always be below curve
								zmin = zdif;
						} // if xpt2,ypt2
						ballpt++;
						xpt2++;
						imgpt++;
					} // while xpt2
					ypt2++;
					imgpt = imgpt - patchwidth - 1 + smallimagewidth;
				}  // while ypt2
				if (zmin!=0)
					zctr += zmin; // move ball up or down if we find a new minimum
				if (zmin<0)
					ptsbelowlastpatch = halfpatchwidth; // ignore left half of ball patch when dz < 0
				else
					ptsbelowlastpatch = 0;
				// now compare every point on ball with background,  and keep highest number
				yval = ypt - patchwidth;
				ypt2 = 0;
				ballpt = 0;
				ybackgrpt = (yval - top + 1) * ybackgrinc;
				while (ypt2<=patchwidth) {
					xval = xpt - patchwidth + ptsbelowlastpatch;
					xpt2 = ptsbelowlastpatch;
					ballpt += ptsbelowlastpatch;
					backgrpt = ybackgrpt + (xval - left + 1) * shrinkfactor;
					while (xpt2<=patchwidth) { // for all the points in the ball patch
						if ((xval >= left) && (xval <= right) && (yval >= top) && (yval <= bottom)) {
							p1 = ballpt;
							zadd = zctr + (patch[p1]&255);
							p1 = backgrpt;
							//if (backgrpt>=backgroundpixels.length) backgrpt = 0; //(debug)
							if (zadd>(backgroundpixels[p1]&0xffff)) //keep largest adjustment}
								backgroundpixels[p1] = (short)zadd;
						}
						ballpt++;
						xval++;
						xpt2++;
						backgrpt += shrinkfactor; // move to next point in x
					} // while xpt2
					yval++;
					ypt2++;
					ybackgrpt += ybackgrinc; // move to next point in y
				} // while ypt2
			} // for xpt
			if (ypt%20==0)
				IJ.showProgress(0.2+0.6*ypt/(bottom+patchwidth));
		} // for ypt
		return background;
	}
	
	/** This is a 16-bit version of the interpolateBackground(0 method. */
	void interpolateBackground16(ImageProcessor background, RollingBall ball) {
		int hloc, vloc;	// position of current pixel in calculated background
		int vinc;		// memory offset from current calculated pos to current interpolated pos
		int lastvalue, nextvalue;	// calculated pixel values between which we are interpolating
		int p;			// pointer to current interpolated pixel value
		int bglastptr, bgnextptr;	// pointers to calculated pixel values between which we are interpolating

		int width = background.getWidth();
		int height = background.getHeight();
		int shrinkfactor = ball.shrinkfactor;
		int leftroll = 0;
		int rightroll = width/shrinkfactor-1;
		int toproll = 0;
		int bottomroll = height/shrinkfactor-1;
		short[] pixels = (short[])background.getPixels();
		
		vloc = 0;
		for (int j=1; j<=(bottomroll-toproll-1); j++) { //interpolate to find background interior
			hloc = 0;
			vloc += shrinkfactor;
			for (int i=1; i<=(rightroll-leftroll); i++) {
				hloc += shrinkfactor;
				bgnextptr = vloc*width + hloc;
				bglastptr = bgnextptr - shrinkfactor;
				nextvalue = pixels[bgnextptr]&0xffff;
				lastvalue = pixels[bglastptr]&0xffff;
				for (int ii=1; ii<=(shrinkfactor-1); ii++) { //interpolate horizontally
					p = bgnextptr - ii;
					pixels[p] = (short)(lastvalue+(shrinkfactor-ii)*(nextvalue-lastvalue)/shrinkfactor);
				}
				for (int ii=0; ii<=(shrinkfactor-1); ii++) { //interpolate vertically
					bglastptr = (vloc-shrinkfactor)*width+hloc-ii;
					bgnextptr = vloc*width+hloc-ii;
					lastvalue = pixels[bglastptr]&0xffff;
					nextvalue = pixels[bgnextptr]&0xffff;
					vinc = 0;
					for (int jj=1; jj<=(shrinkfactor-1); jj++) {
						vinc = vinc-width;
						p = bgnextptr+vinc;
						pixels[p] = (short)(lastvalue+(shrinkfactor-jj)*(nextvalue-lastvalue)/shrinkfactor);
					} // for jj
				} // for ii
			} // for i
		} // for j
	}

	/** This is a 16-bit version of the extrapolateBackground() method. */
	void extrapolateBackground16(ImageProcessor background, RollingBall ball) {
	
		int edgeslope;			// difference of last two consecutive pixel values on an edge
		int pvalue;				// current extrapolated pixel value
		int lastvalue, nextvalue;	//calculated pixel values from which we are extrapolating
		int p;					// pointer to current extrapolated pixel value
		int bglastptr, bgnextptr;	// pointers to calculated pixel values from which we are extrapolating

		int width = background.getWidth();
		int height = background.getHeight();
		int shrinkfactor = ball.shrinkfactor;
		int leftroll = 0;
		int rightroll = width/shrinkfactor-1;
		int toproll = 0;
		int bottomroll = height/shrinkfactor-1;
		short[] pixels = (short[])background.getPixels();
		
		for (int hloc=shrinkfactor; hloc<=(shrinkfactor*(rightroll-leftroll)-1); hloc++) {
			// extrapolate on top and bottom
			bglastptr = shrinkfactor*width+hloc;
			bgnextptr = (shrinkfactor+1)*width+hloc;
			lastvalue = pixels[bglastptr]&0xffff;
			nextvalue = pixels[bgnextptr]&0xffff;
			edgeslope = nextvalue-lastvalue;
			p = bglastptr;
			pvalue = lastvalue;
			for (int jj=1; jj<=shrinkfactor; jj++) {
				p = p-width;
				pvalue = pvalue-edgeslope;
				if (pvalue<0)
					pixels[p] = 0;
				else if (pvalue>65535)
					pixels[p] = (short)65535;
				else
					pixels[p] = (short)pvalue;
			} // for jj
			bglastptr = (shrinkfactor*(bottomroll-toproll-1)-1)*width+hloc;
			bgnextptr = shrinkfactor*(bottomroll-toproll-1)*width+hloc;
			lastvalue = pixels[bglastptr]&0xffff;
			nextvalue = pixels[bgnextptr]&0xffff;
			edgeslope = nextvalue-lastvalue;
			p = bgnextptr;
			pvalue = nextvalue;
			for (int jj=1; jj<=((height-1)-shrinkfactor*(bottomroll-toproll-1)); jj++) {
				p += width;
				pvalue += edgeslope;
				if (pvalue<0)
					pixels[p] = 0;
				else if (pvalue>65535)
					pixels[p] = (short)65535;
				else
					pixels[p] = (short)pvalue;
			} // for jj
		} // for hloc
		for (int vloc=0; vloc<height; vloc++) {
			// extrapolate on left and right
			bglastptr = vloc*width+shrinkfactor;
			bgnextptr = bglastptr+1;
			lastvalue = pixels[bglastptr]&0xffff;
			nextvalue = pixels[bgnextptr]&0xffff;
			edgeslope = nextvalue-lastvalue;
			p = bglastptr;
			pvalue = lastvalue;
			for (int ii=1; ii<=shrinkfactor; ii++) {
				p--;
				pvalue = pvalue - edgeslope;
				if (pvalue<0)
					pixels[p] = 0;
				else if (pvalue>65535)
					pixels[p] = (short)65535;
				else
					pixels[p] = (short)pvalue;
			} // for ii
			bgnextptr = vloc*width+shrinkfactor*(rightroll-leftroll-1)-1;
			bglastptr = bgnextptr-1;
			lastvalue = pixels[bglastptr]&0xffff;
			nextvalue = pixels[bgnextptr]&0xffff;
			edgeslope = nextvalue-lastvalue;
			p = bgnextptr;
			pvalue = nextvalue;
			for (int ii=1; ii<=((width-1)-shrinkfactor*(rightroll-leftroll-1)+1); ii++) {
				p++;
				pvalue = pvalue+edgeslope;
				if (pvalue<0)
					pixels[p] = 0;
				else if (pvalue>65535)
					pixels[p] = (short)65535;
				else
					pixels[p] = (short)pvalue;
			} // for ii
		} // for vloc
	}

}


class RollingBall {

	byte[] data;
	int patchwidth;
	int shrinkfactor;
	
	RollingBall(int radius) {
		int arcTrimPer;
		if (radius<=10) {
			shrinkfactor = 1;
			arcTrimPer = 12; // trim 24% in x and y
		} else if (radius<=30) {
			shrinkfactor = 2;
			arcTrimPer = 12; // trim 24% in x and y
		} else if (radius<=100) {
			shrinkfactor = 4;
			arcTrimPer = 16; // trim 32% in x and y
		} else {
			shrinkfactor = 8;
			arcTrimPer = 20; // trim 40% in x and y
		}
		buildRollingBall(radius, arcTrimPer);
	}
	
	/** Computes the location of each point on the rolling ball patch relative to the 
	center of the sphere containing it.  The patch is located in the top half 
	of this sphere.  The vertical axis of the sphere passes through the center of 
	the patch.  The projection of the patch in the xy-plane below is a square.
	*/
	void buildRollingBall(int ballradius, int arcTrimPer) {
		int rsquare;		// rolling ball radius squared
		int xtrim;			// # of pixels trimmed off each end of ball to make patch
		int xval, yval;		// x,y-values on patch relative to center of rolling ball
		int smallballradius, diam; // radius and diameter of rolling ball
		int temp;			// value must be >=0 to take square root
		int halfpatchwidth;	// distance in x or y from center of patch to any edge
		int ballsize;		// size of rolling ball array
		
		this.shrinkfactor = shrinkfactor;
		smallballradius = ballradius/shrinkfactor;
		if (smallballradius<1)
			smallballradius = 1;
		rsquare = smallballradius*smallballradius;
		diam = smallballradius*2;
		xtrim = (arcTrimPer*diam)/100; // only use a patch of the rolling ball
		patchwidth = diam - xtrim - xtrim;
		halfpatchwidth = smallballradius - xtrim;
		ballsize = (patchwidth+1)*(patchwidth+1);
		data = new byte[ballsize];

		for (int i=0; i<ballsize; i++) {
			xval = i % (patchwidth+1) - halfpatchwidth;
			yval = i / (patchwidth+1) - halfpatchwidth;
			temp = rsquare - (xval*xval) - (yval*yval);
			if (temp >= 0)
				data[i] = (byte)Math.round(Math.sqrt(temp));
			else
				data[i] = 0;
		}
	}
	
}

