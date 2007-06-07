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

	static int radius = 50; // default rolling ball radius
	private ImagePlus imp;
	
	public int setup(String arg, ImagePlus imp) {
		IJ.register(BackgroundSubtracter.class);
		this.imp = imp;
		return DOES_8G+DOES_RGB;
	}

	public void run(ImageProcessor ip) {
		GenericDialog gd = new GenericDialog("Subtract Background", IJ.getInstance());
		gd.addNumericField("Rolling Ball Radius:", radius, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		radius = (int)gd.getNextNumber();
		if (ip instanceof ColorProcessor)
			subtractRGBBackround((ColorProcessor)ip, radius);
		else
			subtractBackround(ip, radius);
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
 			ip.setRoi(null);
 		ip.setProgressBar(null);
 		IJ.showProgress(0.0);
 		RollingBall ball = new RollingBall(ballRadius);
		//new ImagePlus("ball", new ByteProcessor(ball.patchwidth+1, ball.patchwidth+1, ball.data, null)).show();
		//ImageProcessor smallImage = ip.resize(ip.getWidth()/ball.shrinkfactor, ip.getHeight()/ball.shrinkfactor);
		ImageProcessor smallImage = shrinkImage(ip, ball.shrinkfactor);
		//new ImagePlus("small image", smallImage).show();
 		IJ.showStatus("Rolling ball ("+ball.shrinkfactor+")...");
		ImageProcessor background = rollBall(ball, ip, smallImage);
		interpolateBackground(background, ball);
		extrapolateBackground(background, ball);
		IJ.showProgress(0.9);
		if (IJ.altKeyDown())
			new ImagePlus("background", background).show();
		ip.copyBits(background, 0, 0, Blitter.SUBTRACT);
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
		ImageProcessor ip2 = new ByteProcessor(width,height,(byte[])ip.getPixels(),ip.getColorModel());
		ip2.smooth();
		IJ.showProgress(0.1);
		ImageProcessor smallImage = new ByteProcessor(swidth, sheight);
		byte[] pixels = (byte[])ip2.getPixels();
		byte[] spixels = (byte[])smallImage.getPixels();
		int xmaskmin, ymaskmin, min, nextrowoffset, paddr, thispixel;
		for (int i=0; i<(swidth*sheight); i++) {
			xmaskmin = shrinkfactor*(i%swidth);
			ymaskmin = shrinkfactor*(i/swidth);
			min = 255;
			nextrowoffset = width-shrinkfactor;
			paddr = ymaskmin*width+xmaskmin;
			for (int j=1; j<=shrinkfactor; j++) {
				for (int k=1; k<=shrinkfactor; k++) {
					thispixel = pixels[paddr++]&255;
					if (thispixel<min)
						min = thispixel;
				}
				paddr += nextrowoffset;
			}
			spixels[i] = (byte)min; // each point in small image is minimum of its neighborhood
		}
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

}


class RollingBall {

	byte[] data;
	int patchwidth;
	int shrinkfactor;
	
	RollingBall(int radius) {
		shrinkfactor = 8;
		int arcTrimPer = 20; // trim 40% in x and y
		if (radius<=100) {
			shrinkfactor = 4;
			arcTrimPer = 16; // trim 32% in x and y
		} else if (radius<=30) {
			shrinkfactor = 2;
			arcTrimPer = 12; // trim 24% in x and y
		} else if (radius<=10) {
			shrinkfactor = 1;
			arcTrimPer = 12; // trim 24% in x and y
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



/*

implementation

	type
		IntRow = array[0..MaxLine] of Integer;
		BackSubKindType = (RollingHorizontalArc, RollingVerticalArc, RollingBothArcs, RollingBall);

	var
		ArcTrimPer: integer;                                  {trim off percentage of each side of the rolling ball patch}
		shrinkfactor: integer;                                 {shrink the image and ball by this factor before rolling
ball}
		BackSubKind: BackSubKindType;                {which kind of background subtraction are we doing}
		IntPlotWidth: Boolean;
		Intplotwidthval: integer;
		BoundRect: rect;
		xxcenter, yycenter: integer;                       {center of rectangular mask used in MinIn2DMask}
		xmaskmin, ymaskmin: integer;                                   {upper left corner of mask used in AvgIn2DMask}
		backgroundptr, ballptr, smallimageptr: ptr;              {ptrs to background, rolling ball, shrunk image memory}
		backgroundaddr, balladdr, smallimageaddr: longint;   {addrs of background, rolling ball shrunk image memory}
		patchwidth: LongInt;                                                     {x or y dimension of the rolling ball
patch}
		leftroll, rightroll, toproll, bottomroll: integer;         {bounds of the shrunk image}
		Aborting: boolean;





	procedure RollBall;
{*******************************************************************************}
{*     RollBall 'rolls' a filtering object over a (shrunken) image in order to find the image's smooth continuous    *}
{*  background.  For the purpose of explaining this algorithm, imagine that the 2D grayscale image has a third     *}
{*  (height) dimension defined by the intensity value (0-255) at every point in the image.  The center of the      *}
{*  filtering object, a patch from the top of a sphere having radius BallRadius, is moved along each scan line of     *}
{*  the image so that the patch is tangent to the image at one or more points with every other point on the patch    *}
{*  below the corresponding (x,y) point of the image.  Any point either on or below the patch during this process*}
{*  is considered part of the background.  Shrinking the image before running this procedure is advised due to      *}
{*  the fourth-degree complexity of the algorithm.  Care has been taken to avoid unnecessary operations (exp.      *}
{*  multiplication inside loops) in this code.                                                                                                               
*}
{*******************************************************************************}
		var
			halfpatchwidth, {distance in x or y from patch center to any edge}
			ptsbelowlastpatch, {number of points we may ignore because they were below last patch}
			left, right, top, bottom,                   {}
			xpt, ypt,  {current (x,y) point in the shrunken image}
			xpt2, ypt2, {current (x,y) point in the patch relative to upper left corner}
			xval, yval, {location in ball in shrunken image coordinates}
			zdif, {difference in z (height) between point on ball and point on image}
			zmin, {smallest zdif for ball patch with center at current point}
			zctr, {current height of the center of the sphere of which the patch is a part}
			zadd: integer; {height of a point on patch relative to the xy-plane of the shrunken image}
			ballpt, {index to chunk of memory storing the precomputed ball patch}
			imgpt,  {index to chunk of memory storing the shrunken image}
			backgrpt, {index to chunk of memory storing the calculated background}
			ybackgrpt, {displacement to current background scan line}
			ybackgrinc, {distance in memory between two shrunken y-points in background}
			smallimagewidth: longint; {length of a scan line in shrunken image}
			p1, p2: ptr;  {temporary pointers to background, ball, or small image}
	begin
		UpdateMeter(20, 'Finding Background...');
		left := 1;
		right := rightroll - leftroll - 1;
		top := 1;
		bottom := bottomroll - toproll - 1;
		smallimagewidth := right - left + 3;
		halfpatchwidth := patchwidth div 2;
		ybackgrinc := shrinkfactor * (BoundRect.right - BoundRect.left);  {real dist btwn 2 adjacent (dy=1) shrunk pts}
		zctr := 0;                                            {start z-center in the xy-plane}
		for ypt := top to (bottom + patchwidth) do begin
				for xpt := left to (right + patchwidth) do {while patch is tangent to edges or within image...}
					begin                                           {xpt is far right edge of ball patch}
						{do we have to move the patch up or down to make it tangent to but not above image?...}
						zmin := 255;                              {highest could ever be 255}
						ballpt := balladdr;
						ypt2 := ypt - patchwidth;          {ypt2 is top edge of ball patch}
						imgpt := smallimageaddr + ypt2 * smallimagewidth + xpt - patchwidth;
						while ypt2 <= ypt do begin
								xpt2 := xpt - patchwidth;      {xpt2 is far left edge of ball patch}
								while xpt2 <= xpt do            {check every point on ball patch}
									begin                                   {only examine points on }
										if ((xpt2 >= left) and (xpt2 <= right) and (ypt2 >= top) and (ypt2 <= bottom)) then begin
												p1 := ptr(ballpt);
												p2 := ptr(imgpt);
												zdif := BAND(p2^, 255) - (zctr + BAND(p1^, 255));  {curve - circle points}
												if (zdif < zmin) then begin {keep most negative, since ball should always be below curve}
 												zmin := zdif;
												end;
											end;  {if xpt2,ypt2}
										ballpt := ballpt + 1;          {step thru the ball patch memory}
										xpt2 := xpt2 + 1;
										imgpt := imgpt + 1;
									end;  {while xpt2 }
								ypt2 := ypt2 + 1;
								imgpt := imgpt - patchwidth - 1 + smallimagewidth;
							end;  {while ypt2}
						if (zmin <> 0) then
							zctr := zctr + zmin;                {move ball up or down if we find a new minimum}
						if (zmin < 0) then
							ptsbelowlastpatch := halfpatchwidth    {ignore left half of ball patch when dz < 0}
						else
							ptsbelowlastpatch := 0;
{now compare every point on ball with background,  and keep highest number}
						yval := ypt - patchwidth;
						ypt2 := 0;
						ballpt := balladdr;
						ybackgrpt := backgroundaddr + (yval - top + 1) * ybackgrinc;
						while ypt2 <= patchwidth do begin
								xval := xpt - patchwidth + ptsbelowlastpatch;
								xpt2 := ptsbelowlastpatch;
								ballpt := ballpt + ptsbelowlastpatch;
								backgrpt := ybackgrpt + (xval - left + 1) * shrinkfactor;
								while xpt2 <= patchwidth do begin     {for all the points in the ball patch}
										if ((xval >= left) and (xval <= right) and (yval >= top) and (yval <= bottom)) then
begin
												p1 := ptr(ballpt);
												zadd := zctr + BAND(p1^, 255);
												p1 := ptr(backgrpt);
												if (zadd > BAND(p1^, 255)) then  {keep largest adjustment}
													p1^ := zadd;
											end;
										ballpt := ballpt + 1;
										xval := xval + 1;
										xpt2 := xpt2 + 1;
										backgrpt := backgrpt + shrinkfactor;     {move to next point in x}
									end;  {while xpt2}
								yval := yval + 1;
								ypt2 := ypt2 + 1;
								ybackgrpt := ybackgrpt + ybackgrinc;       {move to next point in y}
							end;  {while ypt2}
					end;  {for xpt }
				if ((ypt mod 5) = 0) or not FasterBackgroundSubtraction then begin
						UpdateMeter(20 + (ord4(ypt - top) * 70) div (bottom + patchwidth - top), 'Finding Background...');
						if CommandPeriod then begin
								beep;
								Aborting := true;
								Exit(RollBall);
							end;
					end;
			end;  {for ypt}
	end;


	function MinIn2DMask {(xmaskmin,ymaskmin: integer)}
		: integer;
{*******************************************************************************}
{*     MinInMask finds the minimum pixel value in a shrinkfactor X shrinkfactor mask.                                          
*}
{*******************************************************************************}
		var
			i, j,                                           {loop indices to step through mask}
			thispixel,                                  {value at current pixel in mask}
			min,                                          {temporary minimum value in mask}
			nextrowoffset: integer;             {distance in memory from end of mask in this row to beginning in next}
			paddr: longint;                           {address of current mask pixel}
			p: ptr;                                        {pointer to current pixel in mask}
	begin
		with info^ do begin
				min := 255;
				nextrowoffset := bytesperrow - shrinkfactor;
				paddr := ord4(PicBaseAddr) + ymaskmin * bytesperrow + xmaskmin;
				for j := 1 to shrinkfactor do begin
						for i := 1 to shrinkfactor do begin
								p := ptr(paddr);
								thispixel := BAND(p^, 255);
								if (thispixel < min) then
									min := thispixel;
								paddr := paddr + 1;
							end;     {for i}
						paddr := paddr + nextrowoffset;
					end;     {for j}
				MinIn2DMask := min;
			end; {with}
	end;


	procedure GetRollingBall;
{******************************************************************************}
{*     This procedure computes the location of each point on the rolling ball patch relative to the center of the     *}
{*  sphere containing it.  The patch is located in the top half of this sphere.  The vertical axis of the sphere         *}
{*  passes through the center of the patch.  The projection of the patch in the xy-plane below is a square.           *}
{******************************************************************************}
		var
			rsquare,                                                                         {rolling ball radius squared}
			xtrim,                                                                            {# of pixels trimmed off each
end of ball to make patch}
			xval, yval,                                                                     {x,y-values on patch relative to
center of rolling ball}
			smallballradius, diam,                                                  {radius and diameter of rolling ball}
			temp,                                                                             {value must be >=0 to take
square root}
			halfpatchwidth: integer;                                                {distance in x or y from center of patch
to any edge}
			i,                                                                                    {index into rolling ball
patch memory}
			ballsize: LongInt;                                                           {size of rolling ball memory}
			p: ptr;                                                                            {pointer to current point on
the ball patch}
	begin
		smallballradius := ballradius div shrinkfactor;           {operate on small-sized image with small-sized ball}
		if smallballradius < 1 then
			smallballradius := 1;
		rsquare := smallballradius * smallballradius;
		diam := smallballradius * 2;
		xtrim := (ArcTrimPer * diam) div 100;                      {only use a patch of the rolling ball}
		patchwidth := diam - xtrim - xtrim;
		halfpatchwidth := smallballradius - xtrim;                   {this is half the patch width}
		ballsize := (patchwidth + 1) * (patchwidth + 1);
		ballptr := NewPtrClear(ballsize);
		p := ballptr;
		for i := 0 to ballsize - 1 do begin
				xval := i mod (patchwidth + 1) - halfpatchwidth;
				yval := i div (patchwidth + 1) - halfpatchwidth;
				temp := rsquare - (xval * xval) - (yval * yval);
				if (temp >= 0) then
					p^ := round(sqrt(temp))
				else
					p^ := 0;
				p := ptr(ord4(p) + 1);
			end;
	end;


	procedure InterpolateBackground2D; {(leftroll, rightroll, toproll, bottomroll: integer; backgroundaddr: longint)}
{******************************************************************************}
{*     This procedure uses bilinear interpolation to find the points in the full-scale background given the points *}
{*  from the shrunken image background.  Since the shrunken background is found from an image composed of    *}
{*  minima (over a sufficiently large mask), it is certain that no point in the full-scale interpolated                 *}
{*  background has a higher pixel value than the corresponding point in the original image.                                 
*}
{******************************************************************************}
		var
			i, ii,                                                   {horizontal loop indices}
			j, jj,                                                  {vertical loop indices}
			hloc, vloc,                                          {position of current pixel in calculated background}
			vinc,                                                   {memory offset from current calculated pos to current
interpolated pos}
			lastvalue, nextvalue: integer;           {calculated pixel values between which we are interpolating}
			p,                                                        {pointer to current interpolated pixel value}
			bglastptr, bgnextptr: ptr;                 {pointers to calculated pixel values between which we are
interpolating}
			width: LongInt;
	begin
		vloc := 0;
		with BoundRect do begin
				width := right - left;
				for j := 1 to bottomroll - toproll - 1 do begin     {interpolate to find background interior}
						hloc := 0;
						vloc := vloc + shrinkfactor;
						for i := 1 to rightroll - leftroll do begin
								hloc := hloc + shrinkfactor;
								bgnextptr := ptr(backgroundaddr + vloc * width + hloc);
								bglastptr := ptr(ord4(bgnextptr) - shrinkfactor);
								nextvalue := BAND(bgnextptr^, 255);
								lastvalue := BAND(bglastptr^, 255);
								for ii := 1 to shrinkfactor - 1 do begin     {interpolate horizontally}
										p := ptr(ord4(bgnextptr) - ii);
										p^ := lastvalue + (shrinkfactor - ii) * (nextvalue - lastvalue) div shrinkfactor;
									end;     {for ii}
								for ii := 0 to shrinkfactor - 1 do begin     {interpolate vertically}
										bglastptr := ptr(backgroundaddr + (vloc - shrinkfactor) * width + hloc - ii);
										bgnextptr := ptr(backgroundaddr + vloc * width + hloc - ii);
										lastvalue := BAND(bglastptr^, 255);
										nextvalue := BAND(bgnextptr^, 255);
										vinc := 0;
										for jj := 1 to shrinkfactor - 1 do begin
												vinc := vinc - right + left;
												p := ptr(ord4(bgnextptr) + vinc);
												p^ := lastvalue + (shrinkfactor - jj) * (nextvalue - lastvalue) div
shrinkfactor;
											end;     {for jj}
									end;     {for ii}
							end;     {for i}
					end;     {for j}
			end;   {with boundrect}
	end;


	procedure ExtrapolateBackground2D; {(leftroll, rightroll, toproll, bottomroll: integer; backgroundaddr: longint)}
{******************************************************************************}
{*     This procedure uses linear extrapolation to find pixel values on the top, left, right, and bottom edges of      *}
{*  the background.  First it finds the top and bottom edge points by extrapolating from the edges of the                *}
{*  calculated and interpolated background interior.  Then it uses the edge points on the new calculated,               *}
{*  interpolated, and extrapolated background to find all of the left and right edge points.  If extrapolation yields *}
{*  values below zero or above 255, then they are set to zero and 255 respectively.                                             
*}
{******************************************************************************}
		var
			ii, jj,                                                 {horizontal and vertical loop indices}
			hloc, vloc,                                          {position of current pixel in calculated/interpolated
background}
			edgeslope,                                          {difference of last two consecutive pixel values on an edge}
			pvalue,                                               {current extrapolated pixel value}
			lastvalue, nextvalue: integer;           {calculated pixel values from which we are extrapolating}
			p,                                                        {pointer to current extrapolated pixel value}
			bglastptr, bgnextptr: ptr;                 {pointers to calculated pixel values from which we are extrapolating}
			width: LongInt;
	begin
		with BoundRect do begin
				width := right - left;
				for hloc := shrinkfactor to shrinkfactor * (rightroll - leftroll) - 1 do begin     {extrapolate on top and
bottom}
						bglastptr := ptr(backgroundaddr + shrinkfactor * width + hloc);
						bgnextptr := ptr(backgroundaddr + (shrinkfactor + 1) * width + hloc);
						lastvalue := BAND(bglastptr^, 255);
						nextvalue := BAND(bgnextptr^, 255);
						edgeslope := nextvalue - lastvalue;
						p := bglastptr;
						pvalue := lastvalue;
						for jj := 1 to shrinkfactor do begin
								p := ptr(ord4(p) - right + left);
								pvalue := pvalue - edgeslope;
								if (pvalue < 0) then
									p^ := 0
								else if (pvalue > 255) then
									p^ := 255
								else
									p^ := pvalue;
							end;     {for jj}
						bglastptr := ptr(backgroundaddr + (shrinkfactor * (bottomroll - toproll - 1) - 1) * width + hloc);
						bgnextptr := ptr(backgroundaddr + shrinkfactor * (bottomroll - toproll - 1) * width + hloc);
						lastvalue := BAND(bglastptr^, 255);
						nextvalue := BAND(bgnextptr^, 255);
						edgeslope := nextvalue - lastvalue;
						p := bgnextptr;
						pvalue := nextvalue;
						for jj := 1 to (bottom - top - 1) - shrinkfactor * (bottomroll - toproll - 1) do begin
								p := ptr(ord4(p) + right - left);
								pvalue := pvalue + edgeslope;
								if (pvalue < 0) then
									p^ := 0
								else if (pvalue > 255) then
									p^ := 255
								else
									p^ := pvalue;
							end;     {for jj}
					end;     {for hloc}
				for vloc := top to bottom - 1 do begin     {extrapolate on left and right}
						bglastptr := ptr(backgroundaddr + (vloc - top) * width + shrinkfactor);
						bgnextptr := ptr(ord4(bglastptr) + 1);
						lastvalue := BAND(bglastptr^, 255);
						nextvalue := BAND(bgnextptr^, 255);
						edgeslope := nextvalue - lastvalue;
						p := bglastptr;
						pvalue := lastvalue;
						for ii := 1 to shrinkfactor do begin
								p := ptr(ord4(p) - 1);
								pvalue := pvalue - edgeslope;
								if (pvalue < 0) then
									p^ := 0
								else if (pvalue > 255) then
									p^ := 255
								else
									p^ := pvalue;
							end;     {for ii}
						bgnextptr := ptr(backgroundaddr + (vloc - top) * width + shrinkfactor * (rightroll - leftroll - 1) -
1);
						bglastptr := ptr(ord4(bgnextptr) - 1);
						lastvalue := BAND(bglastptr^, 255);
						nextvalue := BAND(bgnextptr^, 255);
						edgeslope := nextvalue - lastvalue;
						p := bgnextptr;
						pvalue := nextvalue;
						for ii := 1 to (right - left - 1) - shrinkfactor * (rightroll - leftroll - 1) + 1 do begin
								p := ptr(ord4(p) + 1);
								pvalue := pvalue + edgeslope;
								if (pvalue < 0) then
									p^ := 0
								else if (pvalue > 255) then
									p^ := 255
								else
									p^ := pvalue;
							end;     {for ii}
					end;     {for vloc}
			end;   {with BoundRect}
	end;


	procedure SubtractBackground2D;
{*****************************************************************************}
{*     This procedure subtracts each pixel from the calculated/interpolated/extrapolated background from the  *}
{*  corresponding pixel value in the original image.  The resulting image is stored in place of the original        *}
{*  image.  Any pixel subtractions with results below zero are given the value zero.                                          
*}
{*****************************************************************************}
		var
			hloc, vloc,                                          {current pixel location in image and background}
			pvalue: integer;                                 {difference at current pixel location}
			offset,                                                 {offset in memory from beginning of original image to
current scan line}
			backgrpt: LongInt;                              {offset to current point in background}
			p: ptr;                                                {temporary pointer to image or background points}
			Databand: Linetype;                           {current scan line in image}
			ControlKey: boolean;
	begin
		backgrpt := 0;
		ControlKey := ControlKeyDown;
		with Info^, BoundRect do begin
				for vloc := top to bottom - 1 do begin
						GetLine(0, vloc, pixelsperline, Databand);
						for hloc := left to right - 1 do begin
								p := ptr(backgroundaddr + backgrpt);
								pvalue := Databand[hloc] - BAND(p^, 255);
								if ControlKey then
									pvalue := BAND(p^, 255);
								if pvalue < 0 then
									Databand[hloc] := 0
								else
									Databand[hloc] := pvalue;
								backgrpt := backgrpt + 1;
							end;     {for}
						offset := vloc * BytesPerRow;
						p := ptr(ord4(PicBaseAddr) + offset);
						BlockMove(@Databand, p, pixelsperline);
					end;  {for}
			end;     {with}
	end;


	procedure Background2D;
{******************************************************************************}
{*     This procedure implements a rolling-ball algorithm for the removal of smooth continuous background       *}
{*  from a two-dimensional gel image.  It rolls the ball (actually a square patch on the top of a sphere) on a       *}
{*  low-resolution (by a factor of 'shrinkfactor' times) copy of the original image in order to increase speed     *}
{*  with little loss in accuracy.  It uses interpolation and extrapolation to blow the shrunk image to full size.     *}
{******************************************************************************}
		var
			tport: Grafptr;
			i,                                     {loop index for shrunk image memory}
			backgroundsize,              {size of the background memory}
			smallimagesize: LongInt;     {size of the shrunk image memory}
			p: ptr;                             {pointer to current pixel in shrunk image memory}
			table: FateTable;             {not used}
			width: LongInt;
	begin
		ShowWatch;
		UpdateMeter(0, 'Building Rolling Ball...');
		GetPort(tPort);
		with Info^ do begin
				SetPort(GrafPtr(osPort));
				BoundRect := roiRect;
			end;
		GetRollingBall;                                                                  {precompute the rolling ball}
		UpdateMeter(3, 'Building Rolling Ball...');
		balladdr := ord4(ballptr);
		with BoundRect do begin
				width := right - left;
				leftroll := left div shrinkfactor;                                  {left and right edges of shrunken image
or roi}
				rightroll := right div shrinkfactor - 1;                      {on which to roll ball}
				toproll := top div shrinkfactor;
				bottomroll := bottom div shrinkfactor - 1;
				backgroundsize := width * (bottom - top);
				backgroundptr := NewPtrClear(backgroundsize);
				Aborting := backgroundptr = nil;
				backgroundaddr := ord4(backgroundptr);
				smallimagesize := ord4(rightroll - leftroll + 1);
				smallimagesize := smallimagesize * (bottomroll - toproll + 1);
				smallimageptr := NewPtrClear(smallimagesize);
				Aborting := Aborting or (smallimageptr = nil);
				smallimageaddr := ord4(smallimageptr);
				if not aborting then begin
						UpdateMeter(6, 'Smoothing Image ');
						filter(unweightedAvg, 1, table);                                {smooth image before shrinking}
						UpdateMeter(10, concat('Shrinking Image ', long2str(shrinkfactor), ' times...'));
						for i := 0 to smallimagesize - 1 do begin                {create a lower resolution image for
ball-rolling}
								p := ptr(smallimageaddr + i);
								xmaskmin := left + shrinkfactor * (i mod (rightroll - leftroll + 1));
								ymaskmin := top + shrinkfactor * (i div (rightroll - leftroll + 1));
								p^ := MinIn2DMask;                            {each point in small image is minimum of its
neighborhood}
							end;
						if not aborting then begin
								Undo;        {restore original unsmoothed image}
								RollBall;
							end;
					end
				else
					beep;
				if not Aborting then begin
						UpdateMeter(90, 'Interpolating Background...');
						InterpolateBackground2D;                              {interpolate to find background interior}
						UpdateMeter(95, 'Extrapolating Background...');
						ExtrapolateBackground2D;                             {extrapolate on top and bottom}
						UpdateMeter(98, 'Subtracting Background...');
						SubtractBackground2D;                                  {subtract background from original image}
						UpdateMeter(100, 'Subtracting Background...');
					end;
			end;   {with boundrect}
		DisposePtr(backgroundptr);                           {free up background, rolling ball, shrunk image memory}
		DisposePtr(ballptr);
		DisposePtr(smallimageptr);
		DisposeWindow(MeterWindow);
		MeterWindow := nil;
		SetPort(tPort);
	end;


	procedure RollArc (left, rightminusone, diam: integer; var background, ballpoints: IntRow; var Dataline: Linetype);
		var
			xpt, xpt2, xval, ydif, ymin, yctr, bpt, yadd: integer;
	begin
		for xpt := left to rightminusone do begin
				background[xpt] := -255;         {init background curve to minimum values}
			end;
		yctr := 0;                                   {start y-center at the x axis}
		for xpt := left to (rightminusone + diam - 1) do {while semicircle is tangent to edges or within curve...}
			begin                                       {xpt is far right edge of semi-circle}
{do we have to move the circle?...}
				ymin := 256;                          {highest could ever be 255}
				bpt := 0;
				xpt2 := xpt - diam;                {xpt2 is far left edge of semi-circle}
				while xpt2 <= xpt do            {check every point on semicircle}
					begin
						if ((xpt2 >= left) and (xpt2 <= rightminusone)) then begin  {only examine points on curve}
								ydif := dataline[xpt2] - (yctr + ballpoints[bpt]);                {curve minus circle
points}
								if (ydif < ymin) then begin {keep most negative, since ball should always be below curve}
										ymin := ydif;
									end;
							end;  {if xpt2 }
						bpt := bpt + 1;
						xpt2 := xpt2 + 1;
					end;  {while xpt2 }
				if (ymin <> 256) then{if we found a new minimum...}
					yctr := yctr + ymin;   {move circle up or down}
{now compare every point on semi with background,  and keep highest number}
				xval := xpt - diam;
				xpt2 := 0;
				while xpt2 <= diam do begin  {for all the points in the semicircle}
						if ((xval >= left) and (xval <= rightminusone)) then begin
								yadd := yctr + ballpoints[xpt2];
								if (yadd > background[xval]) then  {keep largest adjustment}
									background[xval] := yadd;
							end;
						xval := xval + 1;
						xpt2 := xpt2 + 1;
					end;  {while xpt2}
			end;  {for xpt }
	end;


	function MinIn1DMask (var Databand: LineType; xcenter: integer): integer;
{*******************************************************************************}
{*     MinIn1DMask finds the minimum pixel value in a (2*shrinkfactor-1) mask about the point xcenter in the *}
{*  current line.  This code must run FAST because it gets called OFTEN!                                                                  
*}
{*******************************************************************************}
		var
			i,                                                                              {index to pixels in the mask}
			temp: integer;                                                          {temporary minimum value}
	begin
		temp := Databand[xcenter - shrinkfactor + 1];
		for i := xcenter - shrinkfactor + 2 to xcenter + shrinkfactor - 1 do
			if (Databand[i] < temp) then
				temp := Databand[i];
		MinIn1DMask := temp;
	end;


{******************************************************************************}
{*     This procedure computes the location of each point on the rolling arc relative to the center of the circle     *}
{*  containing it.  The arc is located in the top half of this circle.  The vertical axis of the circle passes through  *}
{*  the midpoint of the arc.  The projection of the arc on the x-axis below is a line segment.                                
*}
{******************************************************************************}
	procedure GetRollingArc (var arcpoints: IntRow; var arcwidth: integer);
		var
			xpt,                                                                                 {x-point along arc}
			xval,                                                                               {x-point in arc array}
			rsquare,                                                                         {shrunken arc radius squared}
			xtrim,                                                                            {points to be trimmed off each
end of arc}
			smallballradius,                                                            {radius of shrunken arc which
actually rolls}
			diam: integer;                                                                 {diameter of shrunken arc's
circle}
	begin
		smallballradius := ballradius div shrinkfactor;            { operate on small-sized image with small-sized ball}
		rsquare := smallballradius * smallballradius;
		for xpt := -smallballradius to smallballradius do        { find the ballpoints for arc based at  (x,y)=(0,0) }
			begin
				xval := xpt + smallballradius;                                     {offset, can't have negative index}
				arcpoints[xval] := round(sqrt(abs(rsquare - (xpt * xpt))));  {Ys are positive, top half of circle}
			end;
		diam := smallballradius * 2;
		xtrim := (ArcTrimPer * diam) div 100;                       {how many points to trim off each end}
		arcwidth := diam - xtrim - xtrim;
		for xpt := -smallballradius to smallballradius - xtrim - xtrim do begin
				xval := xpt + smallballradius;
				arcpoints[xval] := arcpoints[xval + xtrim];
			end;
		for xpt := smallballradius - xtrim - xtrim + 1 to smallballradius do begin
				xval := xpt + smallballradius;
				arcpoints[xval] := 0;
			end;
	end;


	procedure ExtrapolateBackground1D (var Backline, Dataline: LineType; background: IntRow; leftroll, rightroll: integer);
{******************************************************************************}
{*     This procedure uses linear extrapolation to find pixel values on the left and right edges of the current        *}
{*  line of the background.  It finds the edge points by extrapolating from the edges of the calculated and               *}
{*  interpolated background interior.  If extrapolation yields values below zero or above 255, then they are set *}
{*  to zero and 255 respectively.                                                                                                                              
*}
{******************************************************************************}
		var
			i,                                                                             {index to edges of background
array}
			hloc,                                                                       {}
			edgeslope: integer;                                                 {}
	begin
		with BoundRect do begin
				edgeslope := (background[leftroll + 1] - background[leftroll + 2]) div shrinkfactor;
				for i := left to shrinkfactor * (leftroll + 1) - 1 do begin     {extrapolate on left edge}
						hloc := shrinkfactor * (leftroll + 1) - 1 + left - i;
						if (Backline[hloc + 1] + edgeslope < 0) then
							Backline[hloc] := 0
						else if (Backline[hloc + 1] + edgeslope > Dataline[hloc]) then
							Backline[hloc] := Dataline[hloc]
						else
							Backline[hloc] := Backline[hloc + 1] + edgeslope;
					end;
				edgeslope := (background[rightroll] - background[rightroll - 1]) div shrinkfactor;
				for hloc := shrinkfactor * rightroll to right - 1 do begin     {extrapolate on right edge}
						if (Backline[hloc - 1] + edgeslope < 0) then
							Backline[hloc] := 0
						else if (Backline[hloc - 1] + edgeslope > Dataline[hloc]) then
							Backline[hloc] := Dataline[hloc]
						else
							Backline[hloc] := Backline[hloc - 1] + edgeslope;
					end;
			end;     {with}
	end;

	procedure Background1D;
		var
			tport: Grafptr;
			hloc, arcwidth, leftroll, rightroll, numpixels: integer;
			left, right, top, bottom: integer;                      {image bounds; ROTATED if RollingVerticalArc}
			i, j, maskwidth: integer;
			background, arcpoints: IntRow;
			vloc, offset: LongInt;
			p: ptr;
			Dataline, Backline, Smalldataline: Linetype;
			str: str255;
	begin
		ShowWatch;
		UpdateMeter(0, concat('Shrinking Image ', long2str(shrinkfactor), ' times...'));
		GetPort(tPort);
		with Info^ do begin
				SetPort(GrafPtr(osPort));
				BoundRect := roiRect;
			end;
		GetRollingArc(arcpoints, arcwidth);
		maskwidth := shrinkfactor + shrinkfactor - 1;
		case BackSubKind of
			RollingHorizontalArc:  begin
					left := BoundRect.left;
					top := BoundRect.top;
					right := BoundRect.right;
					bottom := BoundRect.bottom;
					numpixels := Info^.pixelsperline;
					str := 'Rolling Disk Horizontally...';
				end;
			RollingVerticalArc:  begin
					left := BoundRect.top;
					top := BoundRect.left;
					right := BoundRect.bottom;
					bottom := BoundRect.right;
					numpixels := Info^.nlines;
					str := 'Rolling Disk Vertically...';
				end;
		end;     {case}
		leftroll := left div shrinkfactor;                                  {left and right edges of shrunken image or roi}
		rightroll := right div shrinkfactor - 1;                      {on which to roll arc}
		for vloc := top to bottom - 1 do begin  {for ROI}
				case BackSubKind of
					RollingHorizontalArc: 
						GetLine(0, vloc, numpixels, Dataline);
					RollingVerticalArc: 
						GetColumn(vloc, 0, numpixels, Dataline);
				end;     {case}
				for i := leftroll + 1 to rightroll do
					smalldataline[i] := MinIn1DMask(Dataline, shrinkfactor * i - 1);
				RollArc(leftroll + 1, rightroll, arcwidth, background, arcpoints, smalldataline);  {roll arc on one line}
				for i := leftroll + 1 to rightroll do begin           {interpolate to find interior background points}
						hloc := shrinkfactor * i - 1;
						Backline[hloc] := background[i];
						for j := 1 to shrinkfactor - 1 do
							Backline[hloc - j] := background[i - 1] + (shrinkfactor - j) * (background[i] - background[i -
1]) div shrinkfactor;
					end;
				ExtrapolateBackground1D(Backline, Dataline, background, leftroll, rightroll);
				for i := left to right - 1 do begin                                {subtract background from current scan
line}
						Dataline[i] := Dataline[i] - Backline[i];
						if Dataline[i] < 0 then
							Dataline[i] := 0;
					end;
				case BackSubKind of
					RollingHorizontalArc: 
						with Info^ do begin
								offset := vloc * BytesPerRow;
								p := ptr(ord4(PicBaseAddr) + offset);
								BlockMove(@Dataline, p, numpixels);            {fast whole line write}
							end;
					RollingVerticalArc: 
						PutColumn(vloc, 0, numpixels, Dataline);         {slow whole column write}
				end;     {case}
				if ((vloc mod 8) = 0) and (vloc > 16) then begin
						UpdateMeter(((vloc - top) * 100) div (bottom - top - 1), str);
						if CommandPeriod then begin
								beep;
								Aborting := true;
								leave;
							end;
					end;
			end;
		UpdateMeter(100, str);
		DisposeWindow(MeterWindow);
		MeterWindow := nil;
		SetPort(tPort);
	end;

	procedure SetUpGel;
		var
			frame: rect;
			AutoSelectAll: boolean;
			p: ptr;
	begin
		if NotinBounds or NotRectangular then
			exit(SetUpGel);
		StopDigitizing;
		AutoSelectAll := not Info^.RoiShowing;
		if AutoSelectAll then
			SelectAll(false);
		SetupUndoFromClip;
		with info^ do begin
				frame := roiRect;
				if ((LutMode = GrayScale) or (LutMode = CustomGrayscale)) and (not IdentityFunction) then
					ApplyLookupTable;
				changes := true;
			end;
		case BackSubKind of
			RollingHorizontalArc, RollingVerticalArc: 
				Background1D;    {--------------> call background subtract <-------------------}
			RollingBall: 
				Background2D;
			RollingBothArcs:  begin
					BackSubKind := RollingHorizontalArc;           {remove horizontal streaks}
					Background1D;
					BackSubKind := RollingVerticalArc;               {remove vertical streaks}
					if not aborting then
						Background1D;
					BackSubKind := RollingBothArcs;                   {leave BackSubKind as we found it}
				end;
		end;     {case}
		UpdatePicWindow;
		SetUpRoiRect;
		WhatToUndo := UndoFilter;
		Info^.changes := true;
		ShowWatch;
		if AutoSelectAll then
			KillRoi;
		if Aborting then begin
				Undo;
				WhatToUndo := NothingToUndo;
				UpdatePicWindow;
			end;
	end;


	procedure GetBallRadius;
		var
			SaveRadius: integer;
			canceled: boolean;
	begin
		SaveRadius := BallRadius;
		BallRadius := GetInt('Rolling BallRadius:', BallRadius, canceled);
		if (BallRadius < 1) or (BallRadius > 319) or canceled then begin
				BallRadius := SaveRadius;
				if not canceled then
					beep;
			end;
	end;


	procedure DoBackgroundMenuEvent (MenuItem: integer);
		var
			map_array: Ptr;
	begin
		if MenuItem <= RemoveStreaksItem then
			if not CheckCalibration
				then exit(DoBackgroundMenuEvent);
		MeterWindow := nil;
		Aborting := false;
		case MenuItem of
			HorizontalItem, VerticalItem:  begin
					if FasterBackgroundSubtraction then begin
							ArcTrimPer := 20;
							shrinkfactor := 4;
						end
					else begin
							ArcTrimPer := 10;
							shrinkfactor := 2;
						end;
					if MenuItem = HorizontalItem then
						BackSubKind := RollingHorizontalArc
					else
						BackSubKind := RollingVerticalArc;
					SetUpGel;
				end;
			Sub2DItem:  begin
					if FasterBackgroundSubtraction then begin
							if BallRadius > 15 then begin
									ArcTrimPer := 20;     {trim 40% in x and y}
									shrinkfactor := 8;
								end
							else begin
									ArcTrimPer := 16;  {trim 32% in x and y}
									shrinkfactor := 4;
								end
						end
					else begin  {faster not checked}
							if BallRadius > 15 then begin
									ArcTrimPer := 16;  {trim 32% in x and y}
									shrinkfactor := 4;
								end
							else begin
									ArcTrimPer := 12;   {trim 24% in x and y}
									ShrinkFactor := 2;
								end
						end;
					BackSubKind := RollingBall;
					SetUpGel;
				end;
			RemoveStreaksItem:  begin
					ArcTrimPer := 20;
					shrinkfactor := 4;
					BackSubKind := RollingBothArcs;
					SetUpGel;
				end;
			FasterItem: 
				FasterBackgroundSubtraction := not FasterBackgroundSubtraction;
			RadiusItem: 
				GetBallRadius;
		end; {case}
	end;


end.
*/
