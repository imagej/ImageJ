package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.ZProjector;
import java.awt.*;
import java.awt.image.*;

/**
This plugin creates a sequence of projections of a rotating volume (stack of slices) onto a plane using
nearest-point (surface), brightest-point, or mean-value projection or a weighted combination of nearest-
point projection with either of the other two methods (partial opacity).  The user may choose to rotate the
volume about any of the three orthogonal axes (x, y, or z), make portions of the volume transparent (using
thresholding), or add a greater degree of visual realism by employing depth cues. Based on Pascal code
contributed by Michael Castle of the  University of Michigan Mental Health Research Institute.
*/ 

public class Projector implements PlugInFilter {

	static final int xAxis=0, yAxis=1, zAxis=2;
	static final int nearestPoint=0, brightestPoint=1, meanValue=2;
	static final int BIGPOWEROF2 = 8192;
		
	String[] axisList = {"X-Axis", "Y-Axis", "Z-Axis"};
	String[] methodList = {"Nearest Point", "Brightest Point", "Mean Value"};
	
	private static int axisOfRotation = xAxis;
	private static int projectionMethod = nearestPoint;

	private static double sliceInterval = 1.0;
	private static int initAngle = 0;
	private static int totalAngle = 360;
	private static int angleInc = 10;
	private static int opacity = 0;
	private static int depthCueSurf = 0;
	private static int depthCueInt = 50;
	private static boolean debugMode;
	private int transparencyLower = 1;
	private int transparencyUpper = 255;	
	ImagePlus imp;
	ImageStack stack;
	ImageStack stack2;
	int width, height, imageWidth;
	int left, right, top, bottom;
	byte[] projArray, opaArray, brightCueArray;
	short[] zBuffer, cueZBuffer, countBuffer;
	int[] sumBuffer;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		IJ.register(Projector.class);
		return DOES_8G+STACK_REQUIRED+NO_CHANGES;
	}
	
	public void run(ImageProcessor ip) {
		if(ip.isInvertedLut()) {
	    	if (!IJ.showMessageWithCancel("3D Project", ZProjector.lutMessage))
	    		return; 
		}
		if (showDialog())
			doProjections(imp);
	}

	public boolean showDialog() {
		ImageProcessor ip = imp.getProcessor();
		double lower = ip.getMinThreshold();
		if (lower!=ImageProcessor.NO_THRESHOLD) {
			transparencyLower = (int)lower;
			transparencyUpper = (int)ip.getMaxThreshold();
		}
		GenericDialog gd = new GenericDialog("3D Projection");
		gd.addChoice("Projection Method:", methodList, methodList[projectionMethod]);
		gd.addChoice("Axis of Rotation:", axisList, axisList[axisOfRotation]);
		//gd.addMessage("");
		gd.addNumericField("Slice Interval (pixels):", sliceInterval, 1);
		gd.addNumericField("Initial Angle (0-359 degrees):", initAngle, 0);
		gd.addNumericField("Total Rotation (0-359 degrees):", totalAngle, 0);
		gd.addNumericField("Rotation Angle Increment:", angleInc, 0);
		gd.addNumericField("Lower Transparency Bound:", transparencyLower, 0);
		gd.addNumericField("Upper Transparency Bound:", transparencyUpper, 0);
		gd.addNumericField("Surface Opacity (0-100%):", opacity, 0);
		gd.addNumericField("Surface Depth-Cueing (0-100%):", 100-depthCueSurf, 0);
		gd.addNumericField("Interior Depth-Cueing (0-100%):", 100-depthCueInt, 0);
		//gd.addCheckbox("Debug Mode:", debugMode);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;;
		projectionMethod = gd.getNextChoiceIndex();
		axisOfRotation = gd.getNextChoiceIndex();
		sliceInterval =  gd.getNextNumber();
		initAngle =  (int)gd.getNextNumber();
		totalAngle =  (int)gd.getNextNumber();
		angleInc =  (int)gd.getNextNumber();
		transparencyLower =  (int)gd.getNextNumber();
		transparencyUpper =  (int)gd.getNextNumber();
		opacity =  (int)gd.getNextNumber();
		depthCueSurf =  100-(int)gd.getNextNumber();
		depthCueInt =  100-(int)gd.getNextNumber();
		//debugMode =  gd.getNextBoolean();

		return true;
    	}
    	
	public void doProjections(ImagePlus imp) {
		int nSlices;				// number of slices in volume
		int projwidth, projheight;	//dimensions of projection image
		int xcenter, ycenter, zcenter;	//coordinates of center of volume of rotation
		int theta;				//current angle of rotation in degrees
		double thetarad;			//current angle of rotation in radians
		int sintheta, costheta;		//sine and cosine of current angle
		int offset;
		int curval, prevval, nextval, aboveval, belowval;
		int n, nProjections, angle;
		boolean minProjSize = true;
		
		stack = imp.getStack();
		if ((angleInc==0) && (totalAngle!=0))
			angleInc = 5;
		angle = 0;
		nProjections = 0;
		if (angleInc==0)
			nProjections = 1;
		else {
			while (angle<=totalAngle) {
				nProjections++;
				angle += angleInc;
			}
  		}
		if (angle>360)
			nProjections--;
		if (nProjections<=0)
			nProjections = 1;

		ImageProcessor ip = imp.getProcessor();
		Rectangle r = ip.getRoi();
		left = r.x;
		top = r.y;
		right = r.x + r.width;
		bottom = r.y + r.height;
		nSlices = imp.getStackSize();
		imageWidth = imp.getWidth();
		width = right - left;
		height = bottom - top;
		xcenter = (left + right)/2;          //find center of volume of rotation
		ycenter = (top + bottom)/2;
		zcenter = (int)(nSlices*sliceInterval/2.0+0.5);

		projwidth = 0;
		projheight = 0;
		if (minProjSize && axisOfRotation!=zAxis) {
			switch (axisOfRotation) {
				case xAxis:
					projheight = (int)(Math.sqrt(nSlices*sliceInterval*nSlices*sliceInterval+height*height) + 0.5);
					projwidth = width;
					break;
				case yAxis:
					projwidth = (int)(Math.sqrt(nSlices*sliceInterval*nSlices*sliceInterval+width*width) + 0.5);
					projheight = height;
					break;
			}
		} else {
			projwidth = (int) (Math.sqrt (nSlices*sliceInterval*nSlices*sliceInterval+width*width) + 0.5);
			projheight = (int) (Math.sqrt (nSlices*sliceInterval*nSlices*sliceInterval+height*height) + 0.5);
		}
		if ((projwidth%1)==1)
			projwidth++;
		int projsize = projwidth * projheight;
		
		try {
			allocateArrays(nProjections, projwidth, projheight);
		}  catch(OutOfMemoryError e) {
			Object[] images = stack2.getImageArray();
			if (images!=null)
				for (int i=0; i<images.length; i++) images[i]=null;
			stack2 = null;
			IJ.showMessage("Projector - Out of Memory",
				"To use less memory, use a rectanguar\n"
				+"selection,  reduce \"Total Rotation\",\n"
				+"and/or increase \"Angle Increment\"."
				);
			return;
		}
		ImagePlus projections = new ImagePlus("Projections", stack2);
		projections.show();
		
		ImageWindow win = imp.getWindow();
		if (win!=null)
			win.running = true;
		theta = initAngle;
		for (n=0; n<nProjections; n++) {
			IJ.showStatus(n+"/"+nProjections);
			IJ.showProgress((double)n/nProjections);
			thetarad = theta * Math.PI/180.0;
			costheta = (int)(BIGPOWEROF2*Math.cos(thetarad) + 0.5);
			sintheta = (int)(BIGPOWEROF2*Math.sin(thetarad) + 0.5);
			
			projArray = (byte[])stack2.getPixels(n+1);
			if (projArray==null)
				break;
			if ((projectionMethod==nearestPoint) || (opacity>0)) {
  				for (int i=0; i<projsize; i++)
					zBuffer[i] = (short)32767;
			}
			if ((opacity>0) && (projectionMethod!=nearestPoint)) {
  				for (int i=0; i<projsize; i++)
					opaArray[i] = (byte)0;
			}
			if ((projectionMethod==brightestPoint) && (depthCueInt<100)) {
   				for (int i=0; i<projsize; i++)
					brightCueArray[i] = (byte)0;
 				for (int i=0; i<projsize; i++)
					cueZBuffer[i] = (short)0;
			}
			if (projectionMethod==meanValue) {
 				for (int i=0; i<projsize; i++)
					sumBuffer[i] = 0;
 				for (int i=0; i<projsize; i++)
					countBuffer[i] = (short)0;
			}
			switch (axisOfRotation) {
				case xAxis:
					doOneProjectionX (nSlices, ycenter, zcenter,projwidth, projheight, costheta, sintheta);
					break;
				case yAxis:
					doOneProjectionY (nSlices, xcenter, zcenter,projwidth, projheight, costheta, sintheta);
					break;
				case zAxis:
					doOneProjectionZ (nSlices, xcenter, ycenter, zcenter, projwidth, projheight, costheta, sintheta);
					break;
			}
			
			if (projectionMethod==meanValue) {
				int count;
				for (int i=0; i<projsize; i++) {
					count = countBuffer[i];
					if (count!=0)
						projArray[i] = (byte)(sumBuffer[i]/count);
				}
			}
			if ((opacity>0) && (projectionMethod!=nearestPoint)) {
 				for (int i=0; i<projsize; i++)
					projArray[i] = (byte)((opacity*(opaArray[i]&0xff) + (100-opacity)*(projArray[i] &0xff))/100);
			}
			if (axisOfRotation==zAxis) {
  				for (int i=projwidth; i<(projsize-projwidth); i++) {
					curval = projArray[i]&0xff;
					prevval = projArray[i-1]&0xff;
					nextval = projArray[i+1]&0xff;
					aboveval = projArray[i-projwidth]&0xff;
					belowval = projArray[i+projwidth]&0xff;
					if ((curval==0)&&(prevval!=0)&&(nextval!=0)&&(aboveval!=0)&&(belowval!=0))
						projArray[i] = (byte)((prevval+nextval+aboveval+belowval)/4);
				}
			}

			theta = (theta + angleInc)%360;
			if (projections.getWindow()==null)   // is "Projections" window still open?
				break;
			projections.setSlice(n+1);
   			if (win!=null && win.running!=true)
				break;
 		} //end for all projections
 		IJ.showProgress(1.0);
 
		if (debugMode) {
			if (projArray!=null) new ImagePlus("projArray", new ByteProcessor(projwidth, projheight, projArray, null)).show();
			if (opaArray!=null) new ImagePlus("opaArray", new ByteProcessor(projwidth, projheight, opaArray, null)).show();
			if (brightCueArray!=null) new ImagePlus("brightCueArray", new ByteProcessor(projwidth, projheight, brightCueArray, null)).show();
			if (zBuffer!=null) new ImagePlus("zBuffer", new ShortProcessor(projwidth, projheight, zBuffer, null)).show();
			if (cueZBuffer!=null) new ImagePlus("cueZBuffer", new ShortProcessor(projwidth, projheight, cueZBuffer, null)).show();
			if (countBuffer!=null) new ImagePlus("countBuffer", new ShortProcessor(projwidth, projheight, countBuffer, null)).show();
			if (sumBuffer!=null) {
				float[] tmp = new float[projwidth*projheight];
				for (int i=0; i<projwidth*projheight; i++)
					tmp[i] = sumBuffer[i];
				new ImagePlus("sumBuffer", new FloatProcessor(projwidth, projheight, tmp, null)).show();
			}
		}

	} // doProjection()
	
	
	void allocateArrays(int nProjections, int projwidth, int projheight) {
		int projsize = projwidth*projheight;
		ColorModel cm = imp.getProcessor().getColorModel();
		stack2 = new ImageStack(projwidth, projheight, cm);
		projArray = new byte[projsize];
		for (int i=0; i<nProjections; i++)
			stack2.addSlice(null, new byte[projsize]);
		if ((projectionMethod==nearestPoint) || (opacity > 0))
			zBuffer = new short[projsize];		
		if ((opacity>0) && (projectionMethod!=nearestPoint))
 			opaArray = new byte[projsize];
		if ((projectionMethod==brightestPoint) && (depthCueInt<100)) {
			brightCueArray = new byte[projsize];
			cueZBuffer = new short[projsize];
		}
		if (projectionMethod==meanValue) {
			sumBuffer = new int[projsize];
			countBuffer = new short[projsize];
		}
	}
				

	/**
	This method projects each pixel of a volume (stack of slices) onto a plane as the volume rotates about the x-axis. Integer
	arithmetic, precomputation of values, and iterative addition rather than multiplication inside a loop are used extensively
	to make the code run efficiently. Projection parameters stored in global variables determine how the projection will be performed.
	This procedure returns various buffers which are actually used by DoProjections() to find the final projected image for the volume
	of slices at the current angle.
	*/
	void doOneProjectionX (int nSlices, int ycenter, int zcenter, int projwidth, int projheight, int costheta, int sintheta) {
		int     thispixel;			//current pixel to be projected
		int    offset, offsetinit;		//precomputed offsets into an image buffer
   		int z;					//z-coordinate of points in current slice before rotation
		int ynew, znew;			//y- and z-coordinates of current point after rotation
		int zmax, zmin;			//z-coordinates of first and last slices before rotation
		int zmaxminuszmintimes100;	//precomputed values to save time in loops
		int c100minusDepthCueInt, c100minusDepthCueSurf;
		boolean DepthCueIntLessThan100, DepthCueSurfLessThan100;
		boolean OpacityOrNearestPt, OpacityAndNotNearestPt;
		boolean MeanVal, BrightestPt;
		int ysintheta, ycostheta;
		int zsintheta, zcostheta, ysinthetainit, ycosthetainit;
		byte[] pixels;
		int projsize = projwidth * projheight;

		//find z-coordinates of first and last slices
		zmax = zcenter + projheight/2;  
		zmin = zcenter - projheight/2;
		zmaxminuszmintimes100 = 100 * (zmax-zmin);
		c100minusDepthCueInt = 100 - depthCueInt;
		c100minusDepthCueSurf = 100 - depthCueSurf;
		DepthCueIntLessThan100 = (depthCueInt < 100);
		DepthCueSurfLessThan100 = (depthCueSurf < 100);
		OpacityOrNearestPt = ((projectionMethod==nearestPoint) || (opacity>0));
		OpacityAndNotNearestPt = ((opacity>0) && (projectionMethod!=nearestPoint));
		MeanVal = (projectionMethod==meanValue);
		BrightestPt = (projectionMethod==brightestPoint);
		ycosthetainit = (top - ycenter - 1) * costheta;
		ysinthetainit = (top - ycenter - 1) * sintheta;
		offsetinit = ((projheight-bottom+top)/2) * projwidth + (projwidth - right + left)/2 - 1;

		for (int k=1; k<=nSlices; k++) {
			pixels = (byte[])stack.getPixels(k);
			z = (int)((k-1)*sliceInterval+0.5) - zcenter;
			zcostheta = z * costheta;
			zsintheta = z * sintheta;
			ycostheta = ycosthetainit;
			ysintheta = ysinthetainit;
		for (int j=top; j<bottom; j++) {
			ycostheta += costheta;  //rotate about x-axis and find new y,z
			ysintheta += sintheta;  //x-coordinates will not change
			ynew = (ycostheta - zsintheta)/BIGPOWEROF2 + ycenter - top;
			znew = (ysintheta + zcostheta)/BIGPOWEROF2 + zcenter;
			offset = offsetinit + ynew * projwidth;
			//GetLine (BoundRect.left, j, width, theLine, Info->PicBaseAddr);
			//read each pixel in current row and project it
			int lineIndex = j*imageWidth;
			for (int i=left; i<right; i++) {
				thispixel = pixels[lineIndex+i]&0xff;
				//if (stack2.getSize()==32 && j==32 && i==32) IJ.write("thispixel: "+thispixel+ " "+lineIndex);
				offset++;
				if ((offset>=projsize) || (offset<0))
					offset = 0;
				if ((thispixel <= transparencyUpper) && (thispixel >= transparencyLower)) {
					if (OpacityOrNearestPt) {
						if (znew<zBuffer[offset]) {
							zBuffer[offset] = (short)znew;
							if (OpacityAndNotNearestPt) {
								if (DepthCueSurfLessThan100)
									opaArray[offset] = (byte)(/*255 -*/ (depthCueSurf*(/*255-*/thispixel)/100 + 
										 c100minusDepthCueSurf*(/*255-*/thispixel)*(zmax-znew)/zmaxminuszmintimes100));
								else
									opaArray[offset] = (byte)thispixel;
							} else {
								//p = (BYTE *)(projaddr + offset);
								if (DepthCueSurfLessThan100)
									projArray[offset] = (byte)(/*255 -*/ (depthCueSurf*(/*255-*/thispixel)/100 +
										c100minusDepthCueSurf*(/*255-*/thispixel)*(zmax-znew)/zmaxminuszmintimes100));
								else
									projArray[offset]  = (byte)thispixel;
							}
						} // if znew<zBuffer[offset]
					} //if OpacityOrNearestP
						if (MeanVal) {
							//sp = (long *)sumbufaddr;
							sumBuffer[offset] += thispixel;
							//cp = (short int *)countbufaddr;
							countBuffer[offset]++;
						} else
							if (BrightestPt) {
								if (DepthCueIntLessThan100) {
									if ((thispixel>(brightCueArray[offset]&0xff)) || (thispixel==(brightCueArray[offset]&0xff)) && (znew>cueZBuffer[offset])) {
										brightCueArray[offset] = (byte)thispixel;  //use z-buffer to ensure that if depth-cueing is on,
										cueZBuffer[offset] = (short)znew;       //the closer of two equally-bright points is displayed.
 										projArray[offset] = (byte)(255 - (depthCueInt*(255-thispixel)/100 +
											c100minusDepthCueInt*(255-thispixel)*(zmax-znew)/zmaxminuszmintimes100));
									}
							} else {
								if (thispixel>(projArray[offset]&0xff))
									projArray[offset] = (byte)thispixel;
							}
						} // else BrightestPt
					} // if thispixel in range
				} //for i (all pixels in row)
			} // for j (all rows of BoundRect)
		} // for k (all slices)
	} //  doOneProjectionX()
	

	/** Projects each pixel of a volume (stack of slices) onto a plane as the volume rotates about the y-axis. */
	void  doOneProjectionY (int nSlices, int xcenter, int zcenter, int projwidth, int projheight, int costheta, int sintheta) {
		//IJ.write("DoOneProjectionY: "+xcenter+" "+zcenter+" "+(double)costheta/BIGPOWEROF2+ " "+(double)sintheta/BIGPOWEROF2);
		int thispixel;			//current pixel to be projected
		int offset, offsetinit;		//precomputed offsets into an image buffer
		int z;					//z-coordinate of points in current slice before rotation
  		int xnew, znew;			//y- and z-coordinates of current point after rotation
		int zmax, zmin;			//z-coordinates of first and last slices before rotation
  		int zmaxminuszmintimes100; //precomputed values to save time in loops
		int c100minusDepthCueInt, c100minusDepthCueSurf;
		boolean DepthCueIntLessThan100, DepthCueSurfLessThan100;
		boolean OpacityOrNearestPt, OpacityAndNotNearestPt;
		boolean MeanVal, BrightestPt;
		int xsintheta, xcostheta;
		int zsintheta, zcostheta, xsinthetainit, xcosthetainit;
		byte[] pixels;
		int projsize = projwidth * projheight;

		//find z-coordinates of first and last slices
		zmax = zcenter + projwidth/2;  
		zmin = zcenter - projwidth/2;
		zmaxminuszmintimes100 = 100 * (zmax-zmin);
		c100minusDepthCueInt = 100 - depthCueInt;
		c100minusDepthCueSurf = 100 - depthCueSurf;
		DepthCueIntLessThan100 = (depthCueInt < 100);
		DepthCueSurfLessThan100 = (depthCueSurf < 100);
		OpacityOrNearestPt = ((projectionMethod==nearestPoint) || (opacity>0));
		OpacityAndNotNearestPt = ((opacity>0) && (projectionMethod!=nearestPoint));
		MeanVal = (projectionMethod==meanValue);
		BrightestPt = (projectionMethod==brightestPoint);
		xcosthetainit = (left - xcenter - 1) * costheta;
		xsinthetainit = (left - xcenter - 1) * sintheta;
		for (int k=1; k<=nSlices; k++) {
 			pixels = (byte[])stack.getPixels(k);
			z = (int)((k-1)*sliceInterval+0.5) - zcenter;
			zcostheta = z * costheta;
			zsintheta = z * sintheta;
			offsetinit = ((projheight-bottom+top)/2) * projwidth +(projwidth - right + left)/2 - projwidth;
			for (int j=top; j<bottom; j++) {
				xcostheta = xcosthetainit;
				xsintheta = xsinthetainit;
				offsetinit += projwidth;
				int lineOffset = j*imageWidth;
				//read each pixel in current row and project it
				for (int i=left; i<right; i++) {
					thispixel =pixels[lineOffset+i]&0xff;
					xcostheta += costheta;  //rotate about x-axis and find new y,z
					xsintheta += sintheta;  //x-coordinates will not change
//if (k==1 && j==top) IJ.write(k+" "thispixel);
					if ((thispixel <= transparencyUpper) && (thispixel >= transparencyLower)) {
						xnew = (xcostheta + zsintheta)/BIGPOWEROF2 + xcenter - left;
						znew = (zcostheta - xsintheta)/BIGPOWEROF2 + zcenter;
						offset = offsetinit + xnew;
						if ((offset>=projsize) || (offset<0))
							offset = 0;
						if (OpacityOrNearestPt) {
							if (znew<zBuffer[offset]) {
								zBuffer[offset] = (short)znew;
								if (OpacityAndNotNearestPt) {
									if (DepthCueSurfLessThan100)
										opaArray[offset] = (byte)((depthCueSurf*thispixel/100 + 
											c100minusDepthCueSurf*thispixel*(zmax-znew)/zmaxminuszmintimes100));
									else
										opaArray[offset] = (byte)thispixel;
								} else {
									if (DepthCueSurfLessThan100)
										projArray[offset] = (byte)((depthCueSurf*thispixel/100 +
											 c100minusDepthCueSurf*thispixel*(zmax-znew)/zmaxminuszmintimes100));
									else
										projArray[offset] = (byte)thispixel;
								}
							} // if (znew < zBuffer[offset])
						} // if (OpacityOrNearestPt)
						if (MeanVal) {
							sumBuffer[offset] += thispixel;
							countBuffer[offset]++;
						} else if (BrightestPt) {
							if (DepthCueIntLessThan100) {
								if ((thispixel>(brightCueArray[offset]&0xff)) || (thispixel==(brightCueArray[offset]&0xff)) && (znew>cueZBuffer[offset])) {
									brightCueArray[offset] = (byte)thispixel;  //use z-buffer to ensure that if depth-cueing is on,
									cueZBuffer[offset] = (short)znew;       //the closer of two equally-bright points is displayed.
									projArray[offset] = (byte)((depthCueInt*thispixel/100 +
										c100minusDepthCueInt*thispixel*(zmax-znew)/zmaxminuszmintimes100));
								}
							} else {
								if (thispixel > (projArray[offset]&0xff))
									projArray[offset] = (byte)thispixel;
							}
						} // if  BrightestPt
					} //end if thispixel in range
				} // for i (all pixels in row)
			} // for j (all rows)
		} // for k (all slices)
	} // DoOneProjectionY()
	

	/** Projects each pixel of a volume (stack of slices) onto a plane as the volume rotates about the z-axis. */
	void doOneProjectionZ (int nSlices, int xcenter, int ycenter, int zcenter, int projwidth, int projheight, int costheta, int sintheta) {
		int thispixel;        //current pixel to be projected
		int offset, offsetinit; //precomputed offsets into an image buffer
		int z;   //z-coordinate of points in current slice before rotation
		int xnew, ynew; //y- and z-coordinates of current point after rotation
		int zmax, zmin; //z-coordinates of first and last slices before rotation
		int zmaxminuszmintimes100; //precomputed values to save time in loops
		int c100minusDepthCueInt, c100minusDepthCueSurf;
		boolean DepthCueIntLessThan100, DepthCueSurfLessThan100;
		boolean OpacityOrNearestPt, OpacityAndNotNearestPt;
		boolean MeanVal, BrightestPt;
		int xsintheta, xcostheta, ysintheta, ycostheta;
		int xsinthetainit, xcosthetainit, ysinthetainit, ycosthetainit;
  		byte[] pixels;
		int projsize = projwidth * projheight;

		//find z-coordinates of first and last slices
		//zmax = zcenter + projwidth/2;  
		//zmin = zcenter - projwidth/2;
		zmax = (int)((nSlices-1)*sliceInterval+0.5) - zcenter;
		zmin = -zcenter;

		zmaxminuszmintimes100 = 100 * (zmax-zmin);
		c100minusDepthCueInt = 100 - depthCueInt;
		c100minusDepthCueSurf = 100 - depthCueSurf;
		DepthCueIntLessThan100 = (depthCueInt < 100);
		DepthCueSurfLessThan100 = (depthCueSurf < 100);
		OpacityOrNearestPt = ((projectionMethod==nearestPoint) || (opacity>0));
		OpacityAndNotNearestPt = ((opacity>0) && (projectionMethod!=nearestPoint));
		MeanVal = (projectionMethod==meanValue);
		BrightestPt = (projectionMethod==brightestPoint);
		xcosthetainit = (left - xcenter - 1) * costheta;
		xsinthetainit = (left - xcenter - 1) * sintheta;
		ycosthetainit = (top - ycenter - 1) * costheta;
		ysinthetainit = (top - ycenter - 1) * sintheta;
		//float[] f = new float[projsize];
		//IJ.write("");
		//IJ.write("depthCueSurf: "+depthCueSurf);
		//IJ.write("zmax: "+zmax);
		//IJ.write("zmin: "+zmin);
		//IJ.write("zcenter: "+zcenter);
		//IJ.write("zmaxminuszmintimes100: "+zmaxminuszmintimes100);
		//IJ.write("c100minusDepthCueSurf: "+c100minusDepthCueSurf);
		offsetinit = ((projheight-bottom+top)/2) * projwidth + (projwidth - right + left)/2 - 1;
 		for (int k=1; k<=nSlices; k++) {
			pixels = (byte[])stack.getPixels(k);
			z = (int)((k-1)*sliceInterval+0.5) - zcenter;
			ycostheta = ycosthetainit;
			ysintheta = ysinthetainit;
			for (int j=top; j<bottom; j++) {
				ycostheta += costheta;
				ysintheta += sintheta;
				xcostheta = xcosthetainit;
				xsintheta = xsinthetainit;
				//GetLine (BoundRect.left, j, width, theLine, Info->PicBaseAddr);
				int lineIndex = j*imageWidth;
				//read each pixel in current row and project it
				for (int i=left; i<right; i++) {
					thispixel = pixels[lineIndex+i]&0xff;
					xcostheta += costheta;  //rotate about x-axis and find new y,z
					xsintheta += sintheta;  //x-coordinates will not change
					if ((thispixel <= transparencyUpper) && (thispixel >= transparencyLower)) {
						xnew = (xcostheta - ysintheta)/BIGPOWEROF2 + xcenter - left;
						ynew = (xsintheta + ycostheta)/BIGPOWEROF2 + ycenter - top;
						offset = offsetinit + ynew * projwidth + xnew;
						if ((offset>=projsize) || (offset<0))
							offset = 0;
						if (OpacityOrNearestPt) {
							if (z<zBuffer[offset]) {
								zBuffer[offset] = (short)z;
								if (OpacityAndNotNearestPt) {
									if (DepthCueSurfLessThan100)
										opaArray[offset] = (byte)((depthCueSurf*(thispixel)/100 +  c100minusDepthCueSurf*(thispixel)*(zmax-z)/zmaxminuszmintimes100));
									else
										opaArray[offset] = (byte)thispixel;
								} else {
									if (DepthCueSurfLessThan100) {
										int v = (depthCueSurf*thispixel/100 + c100minusDepthCueSurf*thispixel*(zmax-z)/zmaxminuszmintimes100);
										//f[offset] = z;
										projArray[offset] = (byte)v;
									} else
										projArray[offset] = (byte)thispixel;
								}
							} // if z<zBuffer[offset]
						} // OpacityOrNearestPt
						if (MeanVal) {
							sumBuffer[offset] += thispixel;
							countBuffer[offset]++;
						} else if (BrightestPt) {
							if (DepthCueIntLessThan100) {
								if ((thispixel>(brightCueArray[offset]&0xff)) || (thispixel==(brightCueArray[offset]&0xff)) && (z>cueZBuffer[offset])) {
									brightCueArray[offset] = (byte)thispixel;  //use z-buffer to ensure that if depth-cueing is on,
									cueZBuffer[offset] = (short)z;       //the closer of two equally-bright points is displayed.
									projArray[offset] = (byte)((depthCueInt*(thispixel)/100 + c100minusDepthCueInt*(thispixel)*(zmax-z)/zmaxminuszmintimes100));
								}
							} else {
								//p = (BYTE *)(projaddr + offset);
								if (thispixel > (projArray[offset]&0xff))
									projArray[offset] = (byte)thispixel;
							}
						} // else BrightestPt
					} //if thispixel in range
				} //for i (all pixels in row)
			} // for j (all rows of BoundRect)
		} // for k (all slices)
		//new ImagePlus("f", new FloatProcessor(projwidth,projheight,f,null)).show();
	} // end doOneProjectionZ()


}
