package ij.plugin.filter;
import ij.*;
import ij.gui.GenericDialog;
import ij.process.*;
import ij.measure.Calibration;
import java.awt.*;

/** This plug-in filter uses convolution with a Gaussian function for smoothing.
 * 'Radius' means the radius of decay to exp(-0.5) ~ 61%, i.e. the standard
 * deviation sigma of the Gaussian (this is the same as in Photoshop, but
 * different from the previous ImageJ function 'Gaussian Blur', where a value
 * 2.5 times as much has to be entered.
 * - Like all convolution operations in ImageJ, it assumes that out-of-image
 * pixels have a value equal to the nearest edge pixel. This gives higher
 * weight to edge pixels than pixels inside the image, and higher weight
 * to corner pixels than non-corner pixels at the edge. Thus, when smoothing
 * with very high blur radius, the output will be dominated by the edge
 * pixels and especially the corner pixels (in the extreme case, with
 * a blur radius of e.g. 1e20, the image will be raplaced by the average
 * of the four corner pixels).
 * - For increased speed, except for small blur radii, the lines (rows or
 * columns of the image) are downscaled before convolution and upscaled
 * to their original length thereafter.
 * 
 * Version 30-Apr-2007 M. Schmid
 *
 */

public class GaussianBlur implements PlugInFilter {

    /** the standard deviation of the Gaussian*/
    private static double sigma = 2.0;
    /** whether sigma is given in units corresponding to the pixel scale (not pixels)*/
    private static boolean sigmaScaled = false;
    /** whether the image has an x&y scale */
    private boolean hasScale = false;
    /** the ImagePlus of the setup call */
    private ImagePlus imp = null;
    /** The slice currently processed */
    private int slice;
    /** The number of passes */
    private int nPasses = 1;
    /** Current pass */
    private int pass;
    
    /** Default constructor */
    public GaussianBlur() {
    }

    /** Construct a GaussianBlur using an ImagePlus */
    public GaussianBlur(ImagePlus imp) {
    	nPasses = imp!=null&&imp.getBitDepth()==24?6:2;
    }

    /** Method to return types supported and "about" text
     * @param arg unused
     * @param imp The imagePlus, used to get the spatial calibration
     * @return Flags describing supported formats etc. (see ij.plugin.filter)
     */
    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        if (imp == null) {
            IJ.noImage();
            return DONE;
        }
        int flags = DOES_ALL|SUPPORTS_MASKING;
        if (!showDialog()) return DONE;
    	nPasses = imp.getBitDepth()==24?6:2;
        return IJ.setupDialog(imp, flags);  //ask whether to process all slices of stack (if a stack)
    }
    
    /** Ask the user for the parameters
     */
    public boolean showDialog() {
        String options = Macro.getOptions();
        //IJ.log("GaussBlurOptions="+options);
        boolean oldMacro = false;   //for old macros, "radius" was 2.5 sigma
        if  (options!=null) {
            sigmaScaled = false;
            if (options.indexOf("radius=") >= 0) {
                oldMacro = true;
                Macro.setOptions(options.replaceAll("radius=", "sigma="));
            }
        }
        GenericDialog gd = new GenericDialog("Gaussian Blur...");
        sigma = Math.abs(sigma);
        gd.addNumericField("Sigma (Radius)", sigma, 2);
        if (imp.getCalibration()!=null && !imp.getCalibration().getUnits().equals("pixels")) {
            hasScale = true;
            gd.addCheckbox("Scaled Units ("+imp.getCalibration().getUnits()+")", sigmaScaled);
        } else
            sigmaScaled = false;
        gd.showDialog();                        //input by the user (or macro) happens here
        if (gd.wasCanceled()) return false;
        sigma = gd.getNextNumber();
        if (sigma < 0 || gd.invalidNumber())
            return false;
        if (oldMacro) sigma /= 2.5;
        if (hasScale)
            sigmaScaled = gd.getNextBoolean();
        return true;
    }

    /** This method is invoked for each slice during execution
     * @param ip The image subject to filtering
     */
    public void run(ImageProcessor ip) {
        double sigmaX = sigmaScaled ? sigma/imp.getCalibration().pixelWidth : sigma;
        double sigmaY = sigmaScaled ? sigma/imp.getCalibration().pixelHeight : sigma;
        slice++;
        if (slice>1)
            IJ.showStatus("Gaussian Blur: "+slice+"/"+imp.getStackSize());
        double accuracy = (ip instanceof ByteProcessor || ip instanceof ColorProcessor) ?
            0.002 : 0.0002;
        Rectangle roi = ip.getRoi();
        if (roi.height!=ip.getHeight() && ip.getMask()==null)
            ip.snapshot();              // a snapshot is needed for out-of-Rectangle pixels
        blurGaussian(ip, sigmaX, sigmaY, accuracy);
    }

    /** Gaussian Filtering of an ImageProcessor. This method is for compatibility with the
     *  previous code and uses a low-accuracy kernel, only slightly better than the previous
     *  ImageJ code */
    public boolean blur(ImageProcessor ip, double radius) {
        Rectangle roi = ip.getRoi();
        if (roi.height!=ip.getHeight() && ip.getMask()==null)
            ip.snapshot();              // a snapshot is needed for out-of-Rectangle pixels
        blurGaussian(ip, 0.4*radius, 0.4*radius, 0.01);
        return true;
    }

    /** Gaussian Filtering of an ImageProcessor. If filtering is not applied to the
     *  full image height, the ImageProcessor must have a valid snapshot.
     * @param ip        The ImageProcessor to be filtered.
     * @param sigmaX   Standard deviation of the Gaussian in x direction (pixels)
     * @param sigmaY   Standard deviation of the Gaussian in y direction (pixels)
     * @param accuracy  Accuracy of kernel, should not be above 0.02. Better (lower)
     *                  accuracy needs slightly more computing time.
     */
    public void blurGaussian(ImageProcessor ip, double sigmaX, double sigmaY, double accuracy) {
    	if (nPasses==1)
    		nPasses = (ip instanceof ColorProcessor)?6:2;
        FloatProcessor fp = null;
        for (int i=0; i<ip.getNChannels(); i++) {
            fp = ip.toFloat(i, fp);
            blurFloat(fp, sigmaX, sigmaY, accuracy);
            ip.setPixels(i, fp);
        }
        if (ip.getRoi().height!=ip.getHeight())
            resetOutOfRoi(ip);          // reset out-of-Rectangle pixels above and below roi
        return;
    }

    /** Gaussian Filtering of a FloatProcessor. This method does NOT include
     *  resetOutOfRoi(ip), i.e., pixels above and below the roi rectangle will
     *  be also subject to filtering in x direction and must be restored
     *  afterwards (unless the full image height is processed).
     * @param ip        The ImageProcessor to be filtered.
     * @param sigmaX    Standard deviation of the Gaussian in x direction (pixels)
     * @param sigmaY    Standard deviation of the Gaussian in y direction (pixels)
     * @param accuracy  Accuracy of kernel, should not be above 0.02. Better (lower)
     *                  accuracy needs slightly more computing time.
     */
    public void blurFloat(FloatProcessor ip, double sigmaX, double sigmaY, double accuracy) {
        if (sigmaX > 0)
            blur1Direction(ip, sigmaX, accuracy, true, true);
        if (sigmaY > 0)
            blur1Direction(ip, sigmaY, accuracy, false, false);
        return;
    }

    /** Blur an image in one direction (x or y) by a Gaussian.
     * @param ip        The Image with the original data where also the result will be stored
     * @param sigma     Standard deviation of the Gaussian
     * @param accuracy  Accuracy of kernel, should not be > 0.02
     * @param xDirection True for bluring in x direction, false for y direction
     * @param doAllLines Whether all lines (parallel to the blurring direction) 
     *                  should be processed, irrespective of the roi.
     */
    public void blur1Direction(FloatProcessor ip, double sigma, double accuracy,
            boolean xDirection, boolean doAllLines) {
        final int UPSCALE_K_RADIUS = 2;             //number of pixels to add for upscaling
        final double MIN_DOWNSCALED_SIGMA = 4.;     //minimum standard deviation in the downscaled image
        float[] pixels = (float[])ip.getPixels();
        int width = ip.getWidth();
        int height = ip.getHeight();
        Rectangle roi = ip.getRoi();
        int length = xDirection ? width : height;   //number of points per line (line can be a row or column)
        int pointInc = xDirection ? 1 : width;      //increment of the pixels array index to the next point in a line
        int lineInc = xDirection ? width : 1;       //increment of the pixels array index to the next line
        int lineFrom = doAllLines? 0 : (xDirection ? roi.y : roi.x);    //the first line to process
        int lineTo = doAllLines ? (xDirection ? height : width) :       //the last line+1 to process
            (xDirection ? roi.y+roi.height : roi.x+roi.width);
        int writeFrom = xDirection? roi.x : roi.y;  //first point of a line that needs to be written
        int writeTo = xDirection ? roi.x+roi.width : roi.y+roi.height;
        int inc = Math.max((lineTo-lineFrom)/(40/(nPasses>0?nPasses:1)),1);
        pass++;
        if (pass>nPasses) pass =1;
        if (sigma > 2*MIN_DOWNSCALED_SIGMA + 0.5) {
            /* large radius (sigma): scale down, then convolve, then scale up */
            int reduceBy = (int)Math.floor(sigma/MIN_DOWNSCALED_SIGMA); //downscale by this factor
            if (reduceBy > length) reduceBy = length;
            /* Downscale gives std devation sigma = 1/sqrt(3); upscale gives sigma = 1/2. (in downscaled pixels) */
            /* All sigma^2 values add to full sigma^2  */
            double sigmaGauss = Math.sqrt(sigma*sigma/(reduceBy*reduceBy) - 1./3. - 1./4.);
            int maxLength = (length+reduceBy-1)/reduceBy + 2*(UPSCALE_K_RADIUS + 1); //downscaled line can't be longer
            float[][] gaussKernel = makeGaussianKernel(sigmaGauss, accuracy, maxLength);
            int kRadius = gaussKernel[0].length*reduceBy; //Gaussian kernel radius after upscaling
            int readFrom = (writeFrom-kRadius < 0) ? 0 : writeFrom-kRadius; //not including broadening by downscale&upscale
            int readTo = (writeTo+kRadius > length) ? length : writeTo+kRadius;
            int newLength = (readTo-readFrom+reduceBy-1)/reduceBy + 2*(UPSCALE_K_RADIUS + 1);
            int unscaled0 = readFrom - (UPSCALE_K_RADIUS + 1)*reduceBy; //input point corresponding to cache index 0
            //IJ.log("reduce="+reduceBy+", newLength="+newLength+", unscaled0="+unscaled0+", sigmaG="+(float)sigmaGauss+", kRadius="+gaussKernel[0].length);
            float[] downscaleKernel = makeDownscaleKernel(reduceBy);
            float[] upscaleKernel = makeUpscaleKernel(reduceBy);
            float[] cache1 = new float[newLength];  //holds data after downscaling
            float[] cache2 = new float[newLength];  //holds data after convolution
            int pixel0 = lineFrom*lineInc;
            for (int line=lineFrom; line<lineTo; line++, pixel0+=lineInc) {
                if (line%inc==0)
                    showProgress((double)(line-lineFrom)/(lineTo-lineFrom));
                downscaleLine(pixels, cache1, downscaleKernel, reduceBy, pixel0, unscaled0, length, pointInc, newLength);
                convolveLine(cache1, cache2, gaussKernel, 0, newLength, 1, newLength-1, 0, 1);
                upscaleLine(cache2, pixels, upscaleKernel, reduceBy, pixel0, unscaled0, writeFrom, writeTo, pointInc);
            }
        } else {
            /* small radius: normal convolution */
            float[][] gaussKernel = makeGaussianKernel(sigma, accuracy, length);
            int kRadius = gaussKernel[0].length;
            float[] cache = new float[length];          //input for convolution, hopefully in CPU cache
            int readFrom = (writeFrom-kRadius < 0) ? 0 : writeFrom-kRadius;
            int readTo = (writeTo+kRadius > length) ? length : writeTo+kRadius;
            int pixel0 = lineFrom*lineInc;
            for (int line=lineFrom; line<lineTo; line++, pixel0+=lineInc) {
                if (line%inc==0)
                    showProgress((double)(line-lineFrom)/(lineTo-lineFrom));
                int p = pixel0 + readFrom*pointInc;
                for (int i=readFrom; i<readTo; i++ ,p+=pointInc)
                    cache[i] = pixels[p];
                convolveLine(cache, pixels, gaussKernel, readFrom, readTo, writeFrom, writeTo, pixel0, pointInc);
            }
        }
        if (pass==nPasses) IJ.showProgress(1.0);
        return;
    }

    /** Scale a line (row or column of a FloatProcessor or part thereof)
     * down by a factor <code>reduceBy</code> and write the result into
     * <code>cache</code>.
     * Input line pixel # <code>unscaled0</code> will correspond to output
     * line pixel # 0. <code>unscaled0</code> may be negative. Out-of-line
     * pixels of the input are replaced by the edge pixels.
     */
    void downscaleLine(float[] pixels, float[] cache, float[] kernel,
            int reduceBy, int pixel0, int unscaled0, int length, int pointInc, int newLength) {
        float first = pixels[pixel0];
        float last = pixels[pixel0 + pointInc*(length-1)];
        int xin = unscaled0 - reduceBy/2;
        int p = pixel0 + pointInc*xin;
        for (int xout=0; xout<newLength; xout++) {
            float v = 0;
            for (int x=0; x<reduceBy; x++, xin++, p+=pointInc) {
                v += kernel[x] * ((xin-reduceBy < 0) ? first : ((xin-reduceBy >= length) ? last : pixels[p-pointInc*reduceBy]));
                v += kernel[x+reduceBy] * ((xin < 0) ? first : ((xin >= length) ? last : pixels[p]));
                v += kernel[x+2*reduceBy] * ((xin+reduceBy < 0) ? first : ((xin+reduceBy >= length) ? last : pixels[p+pointInc*reduceBy]));
            }
            cache[xout] = v;
        }
    }

    /* Create a kernel for downscaling. The kernel function preserves
     * norm and 1st moment (i.e., position) and has fixed 2nd moment,
     * (in contrast to linear interpolation).
     * In scaled space, the length of the kernel runs from -1.5 to +1.5,
     * and the standard deviation is 1/2.
     * Array index corresponding to the kernel center is
     * unitLength*3/2
     */
    float[] makeDownscaleKernel (int unitLength) {
        int mid = unitLength*3/2;
        float[] kernel = new float[3*unitLength];
        for (int i=0; i<=unitLength/2; i++) {
            double x = i/(double)unitLength;
            float v = (float)((0.75-x*x)/unitLength);
            kernel[mid-i] = v;
            kernel[mid+i] = v;
        }
        for (int i=unitLength/2+1; i<(unitLength*3+1)/2; i++) {
            double x = i/(double)unitLength;
            float v = (float)((0.125 + 0.5*(x-1)*(x-2))/unitLength);
            kernel[mid-i] = v;
            kernel[mid+i] = v;
        }
        return kernel;
    }

    /** Scale a line up by factor <code>reduceBy</code> and write as a row
     * or column (or part thereof) to the pixels array of a FloatProcessor.
     */
    void upscaleLine (float[] cache, float[] pixels, float[] kernel,
            int reduceBy, int pixel0, int unscaled0, int writeFrom, int writeTo, int pointInc) {
        int p = pixel0 + pointInc*writeFrom;
        for (int xout = writeFrom; xout < writeTo; xout++, p+=pointInc) {
            int xin = (xout-unscaled0+reduceBy-1)/reduceBy; //the corresponding point in the cache (if exact) or the one above
            int x = reduceBy - 1 - (xout-unscaled0+reduceBy-1)%reduceBy;
            pixels[p] = cache[xin-2]*kernel[x]
                    + cache[xin-1]*kernel[x+reduceBy]
                    + cache[xin]*kernel[x+2*reduceBy]
                    + cache[xin+1]*kernel[x+3*reduceBy];
        }
    }

    /** Create a kernel for upscaling. The kernel function is a convolution
     *  of four unit squares, i.e., four uniform kernels with value +1
     *  from -0.5 to +0.5 (in downscaled coordinates). The second derivative
     *  of this kernel is smooth, the third is not. Its standard deviation
     *  is 1/sqrt(3) in downscaled cordinates.
     *  The kernel runs from [-2 to +2[, corresponding to array index
     *  0 ... 4*unitLength (whereby the last point is not in the array any more).
     */
    float[] makeUpscaleKernel (int unitLength) {
        float[] kernel = new float[4*unitLength];
        int mid = 2*unitLength;
        kernel[0] = 0;
        for (int i=0; i<unitLength; i++) {
            double x = i/(double)unitLength;
            float v = (float)((2./3. -x*x*(1-0.5*x)));
            kernel[mid+i] = v;
            kernel[mid-i] = v;
        }
        for (int i=unitLength; i<2*unitLength; i++) {
            double x = i/(double)unitLength;
            float v = (float)((2.-x)*(2.-x)*(2.-x)/6.);
            kernel[mid+i] = v;
            kernel[mid-i] = v;
        }
        return kernel;
    }

    /** Convolve a line with a symmetric kernel and write to a separate array,
     * possibly the pixels array of a FloatProcessor (as a row or column or part thereof)
     *
     * @param input     Input array containing the line
     * @param pixels    Float array for output, can be the pixels of a FloatProcessor
     * @param kernel    "One-sided" kernel array, kernel[0][n] must contain the kernel
     *                  itself, kernel[1][n] must contain the running sum over all
     *                  kernel elements from kernel[0][n+1] to the periphery.
     *                  The kernel must be normalized, i.e. sum(kernel[0][n]) = 1
     *                  where n runs from the kernel periphery (last element) to 0 and
     *                  back. Normalization should include all kernel points, also these
     *                  not calculated because they are not needed.
     * @param readFrom  First array element of the line that must be read.
     *                  <code>writeFrom-kernel.length</code> or 0.
     * @param readTo    Last array element+1 of the line that must be read.
     *                  <code>writeTo+kernel.length</code> or <code>input.length</code>
     * @param writeFrom Index of the first point in the line that should be written
     * @param writeTo   Index+1 of the last point in the line that should be written
     * @param point0    Array index of first element of the 'line' in pixels (i.e., lineNumber * lineInc)
     * @param pointInc  Increment of the pixels array index to the next point (for an ImageProcessor,
     *                  it should be <code>1</code> for a row, <code>width</code> for a column)
     */
    public void convolveLine(float[] input, float[] pixels, float[][] kernel, int readFrom,
            int readTo, int writeFrom, int writeTo, int point0, int pointInc) {
        int length = input.length;
        float first = input[0];                 //out-of-edge pixels are replaced by nearest edge pixels
        float last = input[length-1];
        float[] kern = kernel[0];               //the kernel itself
        float kern0 = kern[0];
        float[] kernSum = kernel[1];            //the running sum over the kernel
        int kRadius = kern.length;
        int firstPart = kRadius < length ? kRadius : length;
        int p = point0 + writeFrom*pointInc;
        int i = writeFrom;
        for (; i<firstPart; i++,p+=pointInc) {  //while the sum would include pixels < 0
            float result = input[i]*kern0;
            result += kernSum[i]*first;
            if (i+kRadius>length) result += kernSum[length-i-1]*last;
            for (int k=1; k<kRadius; k++) {
                float v = 0;
                if (i-k >= 0) v += input[i-k];
                if (i+k<length) v+= input[i+k];
                result += kern[k] * v;
            }
            pixels[p] = result;
        }
        int iEndInside = length-kRadius<writeTo ? length-kRadius : writeTo;
        for (;i<iEndInside;i++,p+=pointInc) {   //while only pixels within the line are be addressed (the easy case)
            float result = input[i]*kern0;
            for (int k=1; k<kRadius; k++)
                result += kern[k] * (input[i-k] + input[i+k]);
            pixels[p] = result;
        }
        for (; i<writeTo; i++,p+=pointInc) {    //while the sum would include pixels >= length 
            float result = input[i]*kern0;
            if (i<kRadius) result += kernSum[i]*first;
            if (i+kRadius>=length) result += kernSum[length-i-1]*last;
            for (int k=1; k<kRadius; k++) {
                float v = 0;
                if (i-k >= 0) v += input[i-k];
                if (i+k<length) v+= input[i+k];
                result += kern[k] * v;
            }
            pixels[p] = result;
        }
    }

    /** Create a 1-dimensional normalized Gaussian kernel with standard deviation sigma
     *  and the running sum over the kernel
     *  Note: this is one side of the kernel only, not the full kernel as used by the
     *  Convolver class of ImageJ.
     *  To avoid a step due to the cutoff at a finite value, the near-edge values are
     *  replaced by a 2nd-order polynomial with its minimum=0 at the first out-of-kernel
     *  pixel. Thus, the kernel function has a smooth 1st derivative in spite of finite
     *  length.
     *
     * @param sigma     Standard deviation, i.e. radius of decay to 1/sqrt(e), in pixels.
     * @param accuracy  Relative accuracy; for best results below 0.01 when processing
     * 8-bit images. For short or float images, values of 1e-3 to 1e-4 are better (but
     * increase the kernel size and thereby the procesing time). Edge smoothing
     * will fail with very poor accuracy (above approx. 0.02)
     * @param maxRadius Maximum radius of the kernel: Limits kernel size in case of
     * large sigma, should be set to image width or height. For small values of
     * maxRadius, the kernel returned may have a larger radius, however.
     * @return A 2*n array. Array[0][n] is the kernel, decaying towards zero, which
     * would be reached at kernel.length (unless kernel size is limited by maxradius).
     * Array[1][n] holds the sum over all kernel values > n, including non-calculated
     * values in case the kernel size is limited by <code>maxRadius</code>.
     */
    public float[][] makeGaussianKernel(double sigma, double accuracy, int maxRadius) {
        int kRadius = (int)Math.ceil(sigma*Math.sqrt(-2*Math.log(accuracy)))+1;
        if (maxRadius < 50) maxRadius = 50;         // too small maxRadius would result in inaccurate sum.
        if (kRadius > maxRadius) kRadius = maxRadius;
        float[][] kernel = new float[2][kRadius];
        for (int i=0; i<kRadius; i++)               // Gaussian function
            kernel[0][i] = (float)(Math.exp(-0.5*i*i/sigma/sigma));
        if (kRadius < maxRadius && kRadius > 3) {   // edge correction
            double sqrtSlope = Double.MAX_VALUE;
            int r = kRadius;
            while (r > kRadius/2) {
                r--;
                double a = Math.sqrt(kernel[0][r])/(kRadius-r);
                if (a < sqrtSlope)
                    sqrtSlope = a;
                else
                    break;
            }
            for (int r1 = r+2; r1 < kRadius; r1++)
                kernel[0][r1] = (float)((kRadius-r1)*(kRadius-r1)*sqrtSlope*sqrtSlope);
        }
        double sum;                                 // sum over all kernel elements for normalization
        if (kRadius < maxRadius) {
            sum = kernel[0][0];
            for (int i=1; i<kRadius; i++)
                sum += 2*kernel[0][i];
        } else
            sum = sigma * Math.sqrt(2*Math.PI);
        
        double rsum = 0.5 + 0.5*kernel[0][0]/sum;
        for (int i=0; i<kRadius; i++) {
            double v = (kernel[0][i]/sum);
            kernel[0][i] = (float)v;
            rsum -= v;
            kernel[1][i] = (float)rsum;
            //IJ.log("k["+i+"]="+(float)v+" sum="+(float)rsum);
        }
        return kernel;
    }

    /** Set the processed pixels above and below the roi rectangle back to their
     * previous value (i.e., snapshot buffer). This is necessary since ImageJ
     * only restores out-of-roi pixels inside the enclosing rectangle of the roi
     * (If the roi is non-rectangular and the SUPPORTS_MASKING flag is set).
     * @param ip The image to be processed
     */    
    public void resetOutOfRoi(ImageProcessor ip) {
        Rectangle roi = ip.getRoi();
        int width = ip.getWidth();
        int height = ip.getHeight();
        Object pixels = ip.getPixels();
        Object snapshot = ip.getSnapshotPixels();
        for (int y=0,p=roi.x; y<roi.y; y++,p+=width)
            System.arraycopy(snapshot, p, pixels, p, roi.width);
        for (int y=roi.y+roi.height,p=width*y+roi.x; y<height; y++,p+=width)
            System.arraycopy(snapshot, p, pixels, p, roi.width);
    }
    
    void showProgress(double percent) {
    	percent = (double)(pass-1)/nPasses + percent/nPasses;
    	IJ.showProgress(percent);
    }
}
