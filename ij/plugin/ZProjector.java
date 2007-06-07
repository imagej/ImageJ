package ij.plugin; 

import ij.*; 
import ij.gui.GenericDialog; 
import ij.process.*; 

import java.lang.*; 
import java.awt.*; 
import java.awt.event.*; 


/** This plugin performs a z-projection of the input stack. Type of
    output image is same as type of input image. Both maximum and
    average intensity projections are supported. 

    @author Patrick Kelly <phkelly@ucsd.edu> */

public class ZProjector implements PlugIn {
    public static final int AVG_METHOD = 0; 
    public static final int MAX_METHOD = 1;
    public static final int SUM_METHOD = 2;
	public static final int SD_METHOD = 3;
	public static final String[] METHODS = 
		{"Average Intensity", "Max Intensity", "Sum Slices", "Standard Deviation"}; 
    private static int method = AVG_METHOD;

    private static final int BYTE_TYPE  = 0; 
    private static final int SHORT_TYPE = 1; 
    private static final int FLOAT_TYPE = 2;
    
    public static final String lutMessage =
    	"Stacks with inverter LUTs may not project correctly.\n"
    	+"To create a standard LUT, invert the stack (Edit/Invert)\n"
    	+"and invert the LUT (Image/Lookup Tables/Invert LUT)."; 

    /** Image to hold z-projection. */
    private ImagePlus projImage = null; 

    /** Image stack to project. */
    private ImagePlus imp = null; 

    /** Projection starts from this slice. */
    private int startSlice = 1;
    /** Projection ends at this slice. */
    private int stopSlice = 1; 

    public ZProjector() {
    }

    /** Construction of ZProjector with image to be projected. */
    public ZProjector(ImagePlus imp) {
		setImage(imp); 
    }

    /** Explicitly set image to be projected. This is useful if
	ZProjection_ object is to be used not as a plugin but as a
	stand alone processing object.  */
    public void setImage(ImagePlus imp) {
    	this.imp = imp; 
		startSlice = 1; 
		stopSlice = imp.getStackSize(); 
    }

    public void setStartSlice(int slice) {
		if(imp==null || slice < 1 || slice > imp.getStackSize())
	    	return; 
		startSlice = slice; 
    }

    public void setStopSlice(int slice) {
		if(imp==null || slice < 1 || slice > imp.getStackSize())
	    	return; 
		stopSlice = slice; 
    }

    /** Retrieve results of most recent projection operation.*/
    public ImagePlus getProjection() {
		return projImage; 
    }

    public void run(String arg) {
		imp = WindowManager.getCurrentImage();
		if(imp==null) {
	    	IJ.noImage(); 
	    	return; 
		}

		//  Make sure input image is a stack.
		if(imp.getStackSize()==1) {
	    	IJ.showMessage("ZProjection", "Stack required"); 
	    	return; 
		}
	
		//  Check for RGB stack.
		if(imp.getType()==ImagePlus.COLOR_RGB) {
	    	IJ.showMessage("ZProjection", "RGB stacks are not supported."); 
	    	return; 
		}
	
		//  Check for inverted LUT.
		if(imp.getProcessor().isInvertedLut()) {
	    	if (!IJ.showMessageWithCancel("ZProjection", lutMessage))
	    		return; 
		}

		// Set default bounds.
		startSlice = 1; 
		stopSlice  = imp.getStackSize(); 

		// Build control dialog.
		GenericDialog gd = buildControlDialog(startSlice,stopSlice); 
		gd.showDialog(); 
		if(gd.wasCanceled()) return; 

		if(!imp.lock()) return;   // exit if in use
		long tstart = System.currentTimeMillis(); 
		doProjection(gd); 

		if(arg.equals("")) {
			long tstop = System.currentTimeMillis(); 
	    	projImage.show("ZProjector: " +IJ.d2s((tstop-tstart)/1000.0,2)+" seconds");
		}

		imp.unlock(); 
		IJ.register(ZProjector.class);
		return; 
    }

     /** Reads values from dialog and performs projection.
	@param gd read values from this generic dialog
    */
    private void doProjection(GenericDialog gd) {
		// Update starting and stopping slice values.
		setStartSlice((int)gd.getNextNumber()); 
		setStopSlice((int)gd.getNextNumber()); 

		// Get projection method.
		method = gd.getNextChoiceIndex(); 

		computeProjection(method); 
    }

    /** Builds dialog to query users for projection parameters.
	@param start starting slice to display
	@param stop last slice */
    protected GenericDialog buildControlDialog(int start, int stop) {
		GenericDialog gd = new GenericDialog("ZProjection",IJ.getInstance()); 
		gd.addNumericField("Start slice:",startSlice,0/*digits*/); 
		gd.addNumericField("Stop slice:",stopSlice,0/*digits*/); 

		// Different kinds of projections.
		gd.addChoice("Projection Type", METHODS, METHODS[method]); 

		return gd; 
    }

    /** Performs actual projection using specified method. */
    public void computeProjection(int method) {
		if(imp==null)
			return; 
		
		// Create new float processor for projected pixels.
		FloatProcessor fp = new FloatProcessor(imp.getWidth(),imp.getHeight()); 
		ImageStack stack = imp.getStack();
		 
		RayFunction rayFunc = getRayFunction(method, fp);
		if(IJ.debugMode==true) {
	    	IJ.write("\nProjecting stack from: "+startSlice
		     	+" to: "+stopSlice); 
		}

		// Determine type of input image. Explicit determination of
		// processor type is required for subsequent pixel
		// manipulation.  This approach is more efficient than the
		// more general use of ImageProcessor's getPixelValue and
		// putPixel methods.
		int ptype; 
		if(stack.getProcessor(1) instanceof ByteProcessor)       ptype = BYTE_TYPE; 
		else if(stack.getProcessor(1) instanceof ShortProcessor) ptype = SHORT_TYPE; 
		else if(stack.getProcessor(1) instanceof FloatProcessor) ptype = FLOAT_TYPE; 
		else {
	    	IJ.error("ZProjection_: Unknown processor type."); 
	    	return; 
		}

		// Directly traversing pixel array is more efficient than
		// using ImageProcessor pixel acces routines.
		Object[] pixelArray = stack.getImageArray(); 

		// Do the projection.
		for(int n=startSlice; n<=stopSlice; n++) {
	    	IJ.showStatus("ZProjection - projecting slice: "+n);
	    	projectSlice(pixelArray[n-1], rayFunc, ptype);
		}

		// Finish up projection.
		if (method==SUM_METHOD) {
			fp.resetMinAndMax();
			projImage = new ImagePlus("Sum",fp); 
		} else if (method==SD_METHOD) {
			rayFunc.postProcess();
			fp.resetMinAndMax();
			projImage = new ImagePlus("Standard Deviation", fp); 
		} else {
			rayFunc.postProcess(); 
			projImage = makeOutputImage(imp, fp, ptype);
		}

		if(projImage==null)
	    	IJ.error("ZProjection - error computing projection.");
    }

 	private RayFunction getRayFunction(int method, FloatProcessor fp) {
 		if(method==AVG_METHOD||method==SUM_METHOD)
	    	return new AverageIntensity(fp, stopSlice-startSlice+1); 
		else if(method==MAX_METHOD)
	    	return new MaxIntensity(fp); 
		else if(method==SD_METHOD)
	    	return new StandardDeviation(fp, stopSlice-startSlice+1); 
		else {
	    	IJ.error("ZProjection - unknown method.");
	    	return null;
	    } 
	}

    /** Generate output image whose type is same as input image. */
    private ImagePlus makeOutputImage(ImagePlus imp, FloatProcessor fp, int ptype) {
		int width = imp.getWidth(); 
		int height = imp.getHeight(); 
		float[] pixels = (float[])fp.getPixels(); 
		ImageProcessor oip=null; 

		// Create output image consistent w/ type of input image.
		int size = pixels.length;
		switch (ptype) {
			case BYTE_TYPE:
				oip = imp.getProcessor().createProcessor(width,height);
				byte[] pixels8 = (byte[])oip.getPixels(); 
				for(int i=0; i<size; i++)
					pixels8[i] = (byte)pixels[i];
				break;
			case SHORT_TYPE:
				oip = imp.getProcessor().createProcessor(width,height);
				short[] pixels16 = (short[])oip.getPixels(); 
				for(int i=0; i<size; i++)
					pixels16[i] = (short)pixels[i];
				break;
			case FLOAT_TYPE:
				oip = new FloatProcessor(width, height, pixels, null);
				break;
		}
	
		// Adjust for display.
	    // Calling this on non-ByteProcessors ensures image
	    // processor is set up to correctly display image.
	    oip.resetMinAndMax(); 

		// Create new image plus object. Don't use
		// ImagePlus.createImagePlus here because there may be
		// attributes of input image that are not appropriate for
		// projection.
		return new ImagePlus("zprojection", oip); 
    }

    /** Handles mechanics of projection by selecting appropriate pixel
	array type. We do this rather than using more general
	ImageProcessor getPixelValue() and putPixel() methods because
	direct manipulation of pixel arrays is much more efficient.  */
	private void projectSlice(Object pixelArray, RayFunction rayFunc, int ptype) {
		switch(ptype) {
			case BYTE_TYPE:
	    		rayFunc.projectSlice((byte[])pixelArray); 
	    		break; 
			case SHORT_TYPE:
	    		rayFunc.projectSlice((short[])pixelArray); 
	    		break; 
			case FLOAT_TYPE:
	    		rayFunc.projectSlice((float[])pixelArray); 
	    		break; 
		}
    }

     /** Abstract class that specifies structure of ray
	function. Preprocessing should be done in derived class
	constructors.
	*/
    abstract class RayFunction {
		/** Do actual slice projection for specific data types. */
		public abstract void projectSlice(byte[] pixels);
		public abstract void projectSlice(short[] pixels);
		public abstract void projectSlice(float[] pixels);
		
		/** Perform any necessary post processing operations, e.g.
	    	averging values. */
		public void postProcess() {}

    } // end RayFunction


    /** Compute average intensity projection. */
    class AverageIntensity extends RayFunction {
     	private float[] fpixels;
 		private int num, len; 

		/** Constructor requires number of slices to be
	    	projected. This is used to determine average at each
	    	pixel. */
		public AverageIntensity(FloatProcessor fp, int num) {
			fpixels = (float[])fp.getPixels();
			len = fpixels.length;
	    	this.num = num;
		}

		public void projectSlice(byte[] pixels) {
	    	for(int i=0; i<len; i++)
				fpixels[i] += (pixels[i]&0xff); 
		}

		public void projectSlice(short[] pixels) {
	    	for(int i=0; i<len; i++)
				fpixels[i] += pixels[i]&0xffff;
		}

		public void projectSlice(float[] pixels) {
	    	for(int i=0; i<len; i++)
				fpixels[i] += pixels[i]; 
		}

		public void postProcess() {
			float fnum = num;
	    	for(int i=0; i<len; i++)
				fpixels[i] /= fnum;
		}

    } // end AverageIntensity


     /** Compute max intensity projection. */
    class MaxIntensity extends RayFunction {
 		private FloatProcessor fp;
    	private float[] fpixels;
 		private int len; 

		/** Simple constructor since no preprocessing is necessary. */
		public MaxIntensity(FloatProcessor fp) {
			fpixels = (float[])fp.getPixels();
			len = fpixels.length;
			for (int i=0; i<len; i++)
				fpixels[i] = -Float.MAX_VALUE;
		}

		public void projectSlice(byte[] pixels) {
	    	for(int i=0; i<len; i++) {
				if((pixels[i]&0xff)>fpixels[i])
		    		fpixels[i] = (pixels[i]&0xff); 
	    	}
		}

		public void projectSlice(short[] pixels) {
	    	for(int i=0; i<len; i++) {
				if((pixels[i]&0xffff)>fpixels[i])
		    		fpixels[i] = pixels[i]&0xffff;
	    	}
		}

		public void projectSlice(float[] pixels) {
	    	for(int i=0; i<len; i++) {
				if(pixels[i]>fpixels[i])
		    		fpixels[i] = pixels[i]; 
	    	}
		}
		
    } // end MaxIntensity

    /** Compute standard deviation projection. */
    class StandardDeviation extends RayFunction {
    	FloatProcessor fp;
    	private float[] sum, sum2;
		private int num,len; 

		public StandardDeviation(FloatProcessor fp, int num) {
			sum = (float[])fp.getPixels();
			len = sum.length;
			sum2 = new float[len];
		    this.num = num;
		}
	
		public void projectSlice(byte[] pixels) {
			int v;
		    for(int i=0; i<len; i++) {
		    	v = pixels[i]&0xff;
				sum[i] += v;
				sum2[i] += v*v;
			} 
		}
	
		public void projectSlice(short[] pixels) {
			int v;
		    for(int i=0; i<len; i++) {
		    	v = pixels[i]&0xffff;
				sum[i] += v;
				sum2[i] += v*v;
			} 
		}
	
		public void projectSlice(float[] pixels) {
			float v;
		    for(int i=0; i<len; i++) {
		    	v = pixels[i];
				sum[i] += v;
				sum2[i] += v*v;
			} 
		}
	
		public void postProcess() {
			double stdDev;
			double n = num;
		    for(int i=0; i<len; i++) {
				if (num>1) {
					stdDev = (n*sum2[i]-sum[i]*sum[i])/n;
					if (stdDev>0.0)
						sum[i] = (float)Math.sqrt(stdDev/(n-1.0));
					else
						sum[i] = 0f;
				} else
					sum[i] = 0f;
			}
		}

    } // end AverageIntensity

}  // end ZProjection


