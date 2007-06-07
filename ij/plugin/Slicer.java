package ij.plugin; 

import ij.*; 
import ij.gui.*; 
import ij.process.*;
import ij.measure.*; 

import java.util.*; 
import java.awt.image.*; 

public class Slicer implements PlugIn{
    private double xinc,yinc,xstart,ystart,xend,yend; 
    private double dx, dy; 
    private int number; 

    private double zscale;
    private static int swidth = 1; 
    private static boolean interpolate = true; 

    // Parameters used throughout processing.
    private ColorModel cmod; 
    private int width,height;
    private ImagePlus imp;

    // --------------------------------------------------
    public void run(String arg) {
	// Retrieve current image.
	imp = WindowManager.getCurrentImage(); 
	
	if(imp==null) {
	    IJ.noImage(); 
	    return; 
	}

	if (imp.getStackSize()<2) {
	    IJ.error("Stack required"); 
	    return; 
	}

	// Get roi from image and check for validity.
	Roi roi = imp.getRoi(); 
	if(roiIsLine(roi)==false) {
	    IJ.error("Straight line selection required"); 
	    return; 
	}

	// Get z-scaling factor and line width from user.
	Calibration cal = imp.getCalibration();
	GenericDialog gd = new GenericDialog("Slice Parameters",IJ.getInstance()); 
	gd.addNumericField("Z-Spacing ("+cal.getUnits()+"):",cal.pixelDepth,1); 
	gd.addNumericField("Slice Width (pixels):",swidth,0); 
	gd.addCheckbox("Interpolate", interpolate);
	gd.showDialog(); 
	if(gd.wasCanceled()) return; 

	// Read z-spacing and slice width.
	cal.pixelDepth = gd.getNextNumber();
	imp.setCalibration(cal);
	zscale = cal.pixelDepth/cal.pixelWidth;
	swidth = (int)gd.getNextNumber(); 
	interpolate = gd.getNextBoolean();

	//  Record current timing info, for later display.
	long tstart = System.currentTimeMillis(); 

	if(!imp.lock()) return;   // exit if in use
	ImagePlus oimg = sliceImage(imp,(Line)roi); 
	imp.unlock(); 

	// Show slice image.
	long tstop = System.currentTimeMillis(); 
	double seconds = (tstop-tstart)/1000.0; 
	oimg.show("Slicer: "+IJ.d2s(seconds,2)+" seconds"); 
	IJ.register(Slicer.class);
    }

    // --------------------------------------------------
    public void setZScaling(double zscale) {
	this.zscale = zscale; 
    }

    // --------------------------------------------------
    public void setSliceWidth(int swidth) {
	this.swidth = swidth; 
    }

    // --------------------------------------------------
    public ImagePlus sliceImage(ImagePlus imp, Line roi) {
		// Adjust z-spacing and slice width if necessary.
		adjustParameters(imp); 
	
		// Set up parameters that are used in subsequent processing
		// routines.  Do this here to avoid recomputing for each
		// slice.
		initParameters(imp); 
		
		ImageStack stack = imp.getStack(); 
	
		// Generate the "sliced" image.
		ImageStack ostack = stackSlice(stack,roi); 
	
		// Scale stack.
		if (zscale!=1.0)
			ostack = applyZScaling(ostack); 
	
		// TODO: make sure spatial calibration is set correctly.
	
		return new ImagePlus("Slice", ostack); 
    }

    // --------------------------------------------------
    //
    // IMPORTANT: The correctness of this routine depends on the fact
    // that the ImageProcessor.getPixel routine does bounds checking.
    // In the current version of ImageJ (ImageJ107l) this is the
    // case. If this routine does not do bounds checking, then we
    // should check bounds in this routine or in the line2Image
    // routine.
    //
    private ImageStack stackSlice(ImageStack stack, Line roi) {
		// Initialize processing parameters.
		setLineParams(roi); 
	
		// Initialize additional incremental parameters for traversing
		// line width.
		double nrm = Math.sqrt(dx*dx + dy*dy); 
		double sXInc = -dy/nrm; 
		double sYInc = dx/nrm; 
	
		ImageStack ostack =  new ImageStack(number,
						    stack.getSize(),
						    cmod); 

		for(int n=0; n<swidth;++n) {
		    // Get processor containing pixels for particular slice.
		    ImageProcessor ip = processorSlice(stack, roi);
		    int type = imp.getType();
		    switch (type) {
		    	case ImagePlus.GRAY8:
		    		ip = ip.convertToByte(false);
		    		break;
		    	case ImagePlus.GRAY16:
		    		ip = ip.convertToShort(false);
		    		break;
		    } 
	
		    // Add this data to output stack.
		    ostack.addSlice("",ip); 
	
		    // Update starting and ending coordinates.
		    xstart += sXInc; 
		    ystart += sYInc; 
		    xend += sXInc; 
		    yend += sYInc; 
	
		    if(n%3==0)
			IJ.showProgress((double)n/(double)swidth); 
		}
	
		IJ.showProgress(1.0); 
			
		return ostack; 
    }

    private ImageProcessor processorSlice(ImageStack stack, Line roi) {
		ImageProcessor sp = stack.getProcessor(1); 

		// Write new data into this processor.
		int width = number;
		int height = stack.getSize();
		ImageProcessor oip = new FloatProcessor(width, height, new float[width*height], cmod);

		// Read pixel data from this processor.
		ImageProcessor sip = sp.createProcessor(sp.getWidth(), sp.getHeight());
		sip.setInterpolate(interpolate); 
								
		// Note, output image built in reverse order.
		int row = stack.getSize()-1;
		for(int n=1; n<=stack.getSize(); ++n, --row) {
		    // Explicitly set pixels rather than calling getProcessor
		    // because in current ImageJ implementation (ImageJ107l)
		    // the ShortProcessor form of getProcessor creates a new
		    // ShortProcessor.  This in turn, results in a call to
		    // findMinAndMax (in ShortProcessor ctor). findMinAndMax
		    // is costly for large images as it touchs each pixel in
		    // image. It is also a completely unnecessary side-effect
		    // of the getProcessor() call that we don't need for
		    // subsequent processing. Hence, our less intuitive but
		    // more efficient approach.
		    sip.setPixels(stack.getPixels(n)); 
	
		    // Add appropriate line from this slice to image.
		    line2Image(sip, oip, row);
		    double[] data = sip.getLine(xstart, ystart, xend, yend);
		    int x = 0;
			for (int i=0; i<data.length; i++)
				oip.putPixelValue(i, row, data[i]);
		     
		}

		// No longer needed so help garbage collection.
		sip = null; 

		return oip; 
    }

    // --------------------------------------------------
    /** Scale sliced image along "z-coordinate." Because of nature of
	sliced image construction this will be image y-axis. This code
	is taken from ij.plugin.filter.Scaler. Would have used that
	class directly but it always prompts user for xy-scaling
	parameters.  That behavior is not appropriate here.  

	Note - the correctness of this routine depends on the fact
	that resize method allocates new memory for pixel data. This
	seems logical and is the case for current (ImageJ107l)
	implementation but is not explicitly documented in interface.  */
    private ImageStack applyZScaling(ImageStack stack) {
		if(zscale > 25.0) zscale = 25.0; 
		if(zscale < 0.05) zscale = 0.05; 
	
		ImageProcessor ip = stack.getProcessor(1);
		StackProcessor sp = new StackProcessor(stack, ip);
		ip.setInterpolate(interpolate); 
		return sp.resize(imp.getWidth(),(int)(zscale*stack.getHeight())); 
    }


    // --------------------------------------------------
    private void line2Image(ImageProcessor ip, ImageProcessor oip, int col) {
		double rx = xstart, ry = ystart; 
		double value;

		for(int n=0; n<number; ++n) {
		    // Extract pixel value.
		    if (interpolate)
		    	value = ip.getInterpolatedPixel(rx, ry);
		    else
		     	value = (int)ip.getPixel((int)(rx+0.5),(int)(ry+0.5)); 
		    rx += xinc; 
		    ry += yinc; 
	
		    oip.putPixelValue(n, col, value); 
		}
    }


    // --------------------------------------------------
    /** Retrieve processing parameters from input Roi. */
    private void setLineParams(Line roi) {
		dx = roi.x2 - roi.x1; 
		dy = roi.y2 - roi.y1; 

		// Width of output image.
		number = (int)Math.round(Math.sqrt(dx*dx+dy*dy))+1; 

		xinc = (double)dx/number; 
		yinc = (double)dy/number; 

		xstart = roi.x1; ystart = roi.y1; 
		xend = roi.x2; yend = roi.y2; 
    }


    // --------------------------------------------------
    /** Determine whether roi is valid. */
    private boolean roiIsLine(Roi roi) {
	if(roi==null || roi.getType()!=Roi.LINE) return false; 
	return true; 
    }

    // --------------------------------------------------
    /** Adjust z-spacing and slice width to ensure they have
	reasonable values. */
    private void adjustParameters(ImagePlus imp) {
	if(zscale < 0.0) zscale = 1.0; 

	// Maximum reasonable width is equal to diagonal dimension of 
	// image.
	int w = imp.getWidth(), h = imp.getHeight(); 
	int maxwidth = (int)Math.sqrt(w*w + h*h); 

	if(swidth < 1 || swidth > maxwidth) swidth = 1; 
    }

    // --------------------------------------------------
    private void initParameters(ImagePlus imp) {
		cmod = imp.getProcessor().getColorModel(); 
		width  = imp.getWidth(); 
		height = imp.getHeight(); 
    }
}

