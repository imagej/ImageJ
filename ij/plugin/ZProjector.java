package ij.plugin; 

import ij.*; 
import ij.gui.GenericDialog; 
import ij.process.*;
import ij.plugin.filter.*; 
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
    public static final int MIN_METHOD = 2;
    public static final int SUM_METHOD = 3;
	public static final int SD_METHOD = 4;
	public static final int MEDIAN_METHOD = 5;
	public static final String[] METHODS = 
		{"Average Intensity", "Max Intensity", "Min Intensity", "Sum Slices", "Standard Deviation", "Median"}; 
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
    
    private String color = ""; 

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

	public void setMethod(int projMethod){
		method = projMethod;
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
	    	IJ.error("ZProjection", "Stack required"); 
	    	return; 
		}
	
		//  Check for inverting LUT.
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
		setStartSlice((int)gd.getNextNumber()); 
		setStopSlice((int)gd.getNextNumber()); 
		method = gd.getNextChoiceIndex(); 
		if (imp.getType()==ImagePlus.COLOR_RGB) {
			if(method==SUM_METHOD || method==SD_METHOD || method==MEDIAN_METHOD) {
	    		IJ.error("ZProjection", "Sum, StdDev and Median methods \nnot available with RGB stacks.");
	    		imp.unlock(); 
	    		return; 
			}
			doRGBProjection();
		} else 
			doProjection(); 

		if(arg.equals("")) {
			long tstop = System.currentTimeMillis();
			projImage.setCalibration(imp.getCalibration()); 
	    	projImage.show("ZProjector: " +IJ.d2s((tstop-tstart)/1000.0,2)+" seconds");
		}

		imp.unlock(); 
		IJ.register(ZProjector.class);
		return; 
    }
    
    public void doRGBProjection() {
        RGBStackSplitter splitter = new RGBStackSplitter();
        splitter.split(imp.getStack(), true);
        ImagePlus red = new ImagePlus("Red", splitter.red);
        ImagePlus green = new ImagePlus("Green", splitter.green);
        ImagePlus blue = new ImagePlus("Blue", splitter.blue);
        imp.unlock();
        ImagePlus saveImp = imp;
        imp = red;
		color = "(red)"; doProjection();
		ImagePlus red2 = projImage;
        imp = green;
		color = "(green)"; doProjection();
		ImagePlus green2 = projImage;
        imp = blue;
		color = "(blue)"; doProjection();
		ImagePlus blue2 = projImage;
        int w = red2.getWidth(), h = red2.getHeight(), d = red2.getStackSize();
        RGBStackMerge merge = new RGBStackMerge();
        ImageStack stack = merge.mergeStacks(w, h, d, red2.getStack(), green2.getStack(), blue2.getStack(), true);
        imp = saveImp;
        projImage = new ImagePlus("ZProjection of "+imp.getShortTitle(), stack);
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
    public void doProjection() {
		if(imp==null)
			return;
		if (method==MEDIAN_METHOD) {
			projImage = doMedianProjection();
			return;
		} 
			 
		
		// Create new float processor for projected pixels.
		FloatProcessor fp = new FloatProcessor(imp.getWidth(),imp.getHeight()); 
		ImageStack stack = imp.getStack();
		 
		RayFunction rayFunc = getRayFunction(method, fp);
		if(IJ.debugMode==true) {
	    	IJ.log("\nProjecting stack from: "+startSlice
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
	    	IJ.error("ZProjector: Unknown processor type."); 
	    	return; 
		}

		// Do the projection.
		for(int n=startSlice; n<=stopSlice; n++) {
	    	IJ.showStatus("ZProjection " + color +": " + n + "/" + stopSlice);
	    	IJ.showProgress(n-startSlice, stopSlice-startSlice);
	    	projectSlice(stack.getPixels(n), rayFunc, ptype);
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
 		switch (method) {
 			case AVG_METHOD: case SUM_METHOD:
	    		return new AverageIntensity(fp, stopSlice-startSlice+1); 
			case MAX_METHOD:
	    		return new MaxIntensity(fp);
	    	case MIN_METHOD:
	    		return new MinIntensity(fp); 
			case SD_METHOD:
	    		return new StandardDeviation(fp, stopSlice-startSlice+1); 
			default:
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
		return new ImagePlus("ZProjection of "+imp.getShortTitle(), oip); 
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
    
    ImagePlus doMedianProjection() {
    	IJ.showStatus("Calculating median...");
    	ImageStack stack = imp.getStack();
    	int nSlices = stopSlice - startSlice + 1;
    	ImageProcessor[] slices = new ImageProcessor[nSlices];
    	int index = 0;
    	for (int slice=startSlice; slice<=stopSlice; slice++)
    		slices[index++] = stack.getProcessor(slice);
    	ImageProcessor ip2 = slices[0].duplicate();
    	ip2 = ip2.convertToFloat();
    	float[] values = new float[nSlices];
    	int width = ip2.getWidth();
    	int height = ip2.getHeight();
    	int inc = Math.min(height/30, 1);
    	for (int y=0; y<height; y++) {
    		if (y%inc==0) IJ.showProgress(y, height-1);
    		for (int x=0; x<width; x++) {
    			for (int i=0; i<nSlices; i++)
    				values[i] = slices[i].getPixelValue(x, y);
    			ip2.putPixelValue(x, y, median(values));
    		}
    	}
  		return new ImagePlus("Median of "+imp.getShortTitle(), ip2);
    }

	float median(float[] a) {
		sort(a);
		int length = a.length;
		if ((length&1)==0)
			return (a[length/2-1]+a[length/2])/2f; // even
		else
			return a[length/2]; // odd
	}


	void sort(float[] a) {
		if (!alreadySorted(a))
			sort(a, 0, a.length - 1);
	}
	
	void sort(float[] a, int from, int to) {
		int i = from, j = to;
		float center = a[ (from + to) / 2 ];
		do {
			while ( i < to && center>a[i] ) i++;
			while ( j > from && center<a[j] ) j--;
			if (i < j) {float temp = a[i]; a[i] = a[j]; a[j] = temp; }
			if (i <= j) { i++; j--; }
		} while(i <= j);
		if (from < j) sort(a, from, j);
		if (i < to) sort(a,  i, to);
	}
		
	boolean alreadySorted(float[] a) {
		for ( int i=1; i<a.length; i++ ) {
			if (a[i]<a[i-1] )
			return false;
		}
		return true;
	}


	/*
	final float median(float[] a) {
		int nValues = a.length;
		int nv1b2 = (nValues-1)/2;
		int i,j;
		int l=0;
		int m=nValues-1;
		float med=a[nv1b2];
		float dum;
		
		while (l<m) {
			i=l ;
			j=m ;
			do {
				while (a[i]<med) i++;
				while (med<a[j]) j--;
				dum=a[j];
				a[j]=a[i];
				a[i]=dum;
				i++ ; j-- ;
			} while ((j>=nv1b2) && (i<=nv1b2)) ;
			if (j<nv1b2) l=i;
			if (nv1b2<i) m=j;
			med=a[nv1b2];
		}
		return med;
	}
	*/

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

     /** Compute min intensity projection. */
    class MinIntensity extends RayFunction {
 		private FloatProcessor fp;
    	private float[] fpixels;
 		private int len; 

		/** Simple constructor since no preprocessing is necessary. */
		public MinIntensity(FloatProcessor fp) {
			fpixels = (float[])fp.getPixels();
			len = fpixels.length;
			for (int i=0; i<len; i++)
				fpixels[i] = Float.MAX_VALUE;
		}

		public void projectSlice(byte[] pixels) {
	    	for(int i=0; i<len; i++) {
				if((pixels[i]&0xff)<fpixels[i])
		    		fpixels[i] = (pixels[i]&0xff); 
	    	}
		}

		public void projectSlice(short[] pixels) {
	    	for(int i=0; i<len; i++) {
				if((pixels[i]&0xffff)<fpixels[i])
		    		fpixels[i] = pixels[i]&0xffff;
	    	}
		}

		public void projectSlice(float[] pixels) {
	    	for(int i=0; i<len; i++) {
				if(pixels[i]<fpixels[i])
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
			float v;
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

    } // end StandardDeviation


}  // end ZProjection


