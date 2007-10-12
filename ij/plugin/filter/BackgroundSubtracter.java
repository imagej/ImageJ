package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import java.awt.*;


/** Implements ImageJ's Subtract Background command. Based on the concept of the
rolling ball algorithm described in Stanley Sternberg's article, "Biomedical Image
Processing", IEEE Computer, January 1983.

Imagine that the 2D grayscale image has a third (height) dimension by the image
value at every point in the image, creating a surface. A ball of given radius is
rolled over the bottom side of this surface; the hull of the volume reachable by
the ball is the background.

In the current implementation, the rolling ball is replaced by a sliding paraboloid
of rotation with the same curvature at its apex as a ball of a given radius.
A paraboloid has the advantage that suitable paraboloids can be found for any image
values, even if the pixel values are much larger than a typical object size (in pixels).
The paraboloid of rotation is approximated as parabolae in 4 directions: x, y and
the two 45-degree directions. Lines of the image in these directions are processed
by sliding a parabola against them. Obtaining the hull needs the parabola for a
given direction to be applied multiple times (after doing the other directions);
in this respect the current code is a compromise between accuracy and speed.

For noise rejection, the image used for calculating the background is slightly
smoothened (3x3 average). This can result in negative values after background
subtraction. Smoothing can be disabled.

Additional code has been added to avoid subtracting corner objects as a background
(note that a paraboloid or ball would always touch the 4 corner pixels and thus make
them background pixels). This code assumes that corner particles reach less than
1/4 of the image size into the image.

Current code by Michael Schmid, 09-Oct-2007.
*/
public class BackgroundSubtracter implements ExtendedPlugInFilter, DialogListener {
    /* parameters from the dialog: */
    private static double radius = 50;  // default rolling ball radius
    private static boolean lightBackground = Prefs.get("bs.background", true);
    private static boolean separateColors; // whether to create a separate background for each color channel
    private static boolean createBackground;   // don't subtract background (e.g., for processing the background before subtracting)
    private static boolean doPresmooth = true; // smoothen the image before creating the background
    /* more class variables */
    private boolean isRGB;              // whether we have an RGB image
    private boolean previewing;
    private final static int X_DIRECTION = 0, Y_DIRECTION = 1,
            DIAGONAL_1A = 2, DIAGONAL_1B = 3, DIAGONAL_2A = 4, DIAGONAL_2B = 5; //filter directions
    private final static int DIRECTION_PASSES = 9; //number of passes for different directions
    private int nPasses = DIRECTION_PASSES;
    private int pass;
    private int flags = DOES_ALL|FINAL_PROCESSING|KEEP_PREVIEW|PARALLELIZE_STACKS;

    public int setup(String arg, ImagePlus imp) {
        if (arg.equals("final")) {
            imp.getProcessor().resetMinAndMax();
            return DONE;
        } else
            return flags;
    }

    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        isRGB = imp.getProcessor() instanceof ColorProcessor;
        String options = Macro.getOptions();
        if  (options!=null)
            Macro.setOptions(options.replaceAll("white", "light"));
        GenericDialog gd = new GenericDialog(command);
        gd.addNumericField("Rolling Ball Radius:", radius, 1, 6, "Pixels");
        gd.addCheckbox("Light Background", lightBackground);
        if (isRGB) gd.addCheckbox("Separate Colors", separateColors);
        gd.addCheckbox("Create Background (Don't Subtract)", createBackground);
        gd.addCheckbox("Disable Smoothing", !doPresmooth);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        previewing = true;
        gd.showDialog();
        previewing = false;
        if (gd.wasCanceled()) return DONE;
        IJ.register(this.getClass());       //protect static class variables (filter parameters) from garbage collection
        Prefs.set("bs.background", lightBackground);
        return IJ.setupDialog(imp, flags);  //ask whether to process all slices of stack (if a stack)
    }

    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
        radius = gd.getNextNumber();
        if (radius <= 0.0001 || gd.invalidNumber())
            return false;
        lightBackground = gd.getNextBoolean();
        if (isRGB) separateColors = gd.getNextBoolean();
        createBackground = gd.getNextBoolean();
        doPresmooth = !gd.getNextBoolean();
        return true;
    }

    /** Background for any image type */
    public void run(ImageProcessor ip) {
        if (isRGB && !separateColors)
            rollingBallBrightnessBackground((ColorProcessor)ip, radius, createBackground, lightBackground, doPresmooth, true);
        else
            rollingBallBackground(ip, radius, createBackground, lightBackground, doPresmooth, true);
        if (previewing && (ip instanceof FloatProcessor || ip instanceof ShortProcessor)) {
            ip.resetMinAndMax();
        }
    }

    /** Depracated. For compatibility with previous ImageJ versions */
    public void subtractRGBBackround(ColorProcessor ip, int ballRadius) {
        rollingBallBrightnessBackground(ip, (double)ballRadius, false, lightBackground, true, true);
    }
    /** Depracated. For compatibility with previous ImageJ versions */
    public void subtractBackround(ImageProcessor ip, int ballRadius) {
        rollingBallBackground(ip, (double)ballRadius, false, lightBackground, true, true);
    }

    /** Create or subtract a background, based on the brightness of an RGB image (keeping
     * the hue of each pixel unchanged)
     * @param ip            The RGB image. On output, it will become the background-subtracted image or
     *                      the background (depending on <code>createBackground</code>).
     * @param radius        Radius of the rolling ball creating the background (actually a
     *                      paraboloid of rotation with the same curvature)
     * @param createBackground  Whether to create a background, not to subtract it.
     * @param lightBackground   Whether the image has a light background.
     * @param doPresmooth   Whether the image should be smoothened (3x3 mean) before creating
     *                      the background. With smoothing, the background will not necessarily
     *                      be below the image data.
     * @param correctCorners    Whether the algorithm should try to detect corner particles to avoid
     *                      subtracting them as a background.
     */
    public void rollingBallBrightnessBackground(ColorProcessor ip, double radius, boolean createBackground,
            boolean lightBackground, boolean doPresmooth, boolean correctCorners) {
        int width = ip.getWidth();
        int height = ip.getHeight();
        byte[] H = new byte[width*height];
        byte[] S = new byte[width*height];
        byte[] B = new byte[width*height];
        ip.getHSB(H, S, B);
        ByteProcessor bp = new ByteProcessor(width, height, B, null);
        rollingBallBackground(bp, radius, createBackground, lightBackground, doPresmooth, correctCorners);
        ip.setHSB(H, S, (byte[])bp.getPixels());
    }

    /** Create or subtract a background, works for all image types. For RGB images, the
     * background is subtracted from each channel separately
     * @param ip            The image. On output, it will become the background-subtracted image or
     *                      the background (depending on <code>createBackground</code>).
     * @param radius        Radius of the rolling ball creating the background (actually a
     *                      paraboloid of rotation with the same curvature)
     * @param createBackground  Whether to create a background, not to subtract it.
     * @param lightBackground   Whether the image has a light background.
     * @param doPresmooth   Whether the image should be smoothened (3x3 mean) before creating
     *                      the background. With smoothing, the background will not necessarily
     *                      be below the image data.
     * @param correctCorners    Whether the algorithm should try to detect corner particles to avoid
     *                      subtracting them as a background.
     */
    public void rollingBallBackground(ImageProcessor ip, double radius, boolean createBackground,
            boolean lightBackground, boolean doPresmooth, boolean correctCorners) {
        boolean invertedLut = ip.isInvertedLut();
        boolean invert = (invertedLut && !lightBackground) || (!invertedLut && lightBackground);
        float offset = 0;           // the ideal 'background' value (e.g., 0 or 255 for 8-bit images)
        if (invert) {
            if (ip instanceof FloatProcessor) offset = 0;
            else if (ip instanceof ShortProcessor) offset = 65535;
            else offset = 255;
        }
        FloatProcessor fp = null;
        for (int i=0; i<ip.getNChannels(); i++) {
            fp = ip.toFloat(i, fp);
            if (!createBackground) fp.snapshot();
            rollingBallFloatBackground(fp, (float)radius, createBackground, invert, offset, doPresmooth, correctCorners);
            ip.setPixels(i, fp);
        }
    }

    /** Background for a float image type by sliding a paraboloid over
     * the image. */
    void rollingBallFloatBackground(FloatProcessor fp, float radius, boolean createBackground, boolean invert, float offset,
            boolean doPresmooth, boolean correctCorners) {
        if (!createBackground) fp.snapshot();
        float[] pixels = (float[])fp.getPixels();   //this will become the background
        float[] snapshotPixels = (float[])fp.getSnapshotPixels();
        int width = fp.getWidth();
        int height = fp.getHeight();
        float[] cache = new float[Math.max(width, height)]; //work array for lineSlideParabola
        int[] nextPoint = new int[Math.max(width, height)]; //work array for lineSlideParabola
        float coeff2 = 0.5f/radius;                 //2nd-order coefficient of the polynomial approximating the ball
        float coeff2diag = 1.f/radius;              //same for diagonal directions where step is sqrt2

        showProgress(0.000001);                     //start the progress bar (only filter1D will increment it)
        if (invert)
            for (int i=0; i<pixels.length; i++)
                pixels[i] = -pixels[i];

        if (doPresmooth)
            fp.smooth();
        if (correctCorners)
            correctCorners(fp, coeff2, cache, nextPoint);   //modify corner data, avoids subtracting corner particles

        /* Slide the parabola over the image in different directions */
        /* Doing the diagonal directions at the end is faster (diagonal lines are denser,
         * so there are more such lines, and the algorithm gets faster with each iteration) */
        filter1D(fp, X_DIRECTION, coeff2, cache, nextPoint);
        filter1D(fp, Y_DIRECTION, coeff2, cache, nextPoint);
        filter1D(fp, X_DIRECTION, coeff2, cache, nextPoint);    //redo for better accuracy
        filter1D(fp, DIAGONAL_1A, coeff2diag, cache, nextPoint);
        filter1D(fp, DIAGONAL_1B, coeff2diag, cache, nextPoint);
        filter1D(fp, DIAGONAL_2A, coeff2diag, cache, nextPoint);
        filter1D(fp, DIAGONAL_2B, coeff2diag, cache, nextPoint);
        filter1D(fp, DIAGONAL_1A, coeff2diag, cache, nextPoint);//redo for better accuracy
        filter1D(fp, DIAGONAL_1B, coeff2diag, cache, nextPoint);

        if (invert)
            for (int i=0; i<pixels.length; i++)
                pixels[i] = -pixels[i];

        if (!createBackground)          //subtract the background now
            for (int i=0; i<pixels.length; i++)
                pixels[i] = snapshotPixels[i]-pixels[i]+offset;
    }

    /** Filter by subtracting a sliding parabola for all lines in one direction, x, y or one of
     *  the two diagonal directions (diagonals are processed only for half the image per call). */
    void filter1D(FloatProcessor fp, int direction, float coeff2, float[] cache, int[] nextPoint) {
        float[] pixels = (float[])fp.getPixels();   //this will become the background
        int width = fp.getWidth();
        int height = fp.getHeight();
        int startLine = 0;          //index of the first line to handle
        int nLines = 0;             //index+1 of the last line to handle (initialized to avoid compile-time error)
        int lineInc = 0;            //increment from one line to the next in pixels array
        int pointInc = 0;           //increment from one point to the next along the line
        int length = 0;             //length of the line
        switch (direction) {
            case X_DIRECTION:       //lines parallel to x direction
                nLines = height;
                lineInc = width;
                pointInc = 1;
                length = width;
            break;
            case Y_DIRECTION:       //lines parallel to y direction
                nLines = width;
                lineInc = 1;
                pointInc = width;
                length = height;
            break;
            case DIAGONAL_1A:       //lines parallel to x=y, starting at x axis
                nLines = width-2;   //the algorithm makes no sense for lines shorter than 3 pixels
                lineInc = 1;
                pointInc = width + 1;
            break;
            case DIAGONAL_1B:       //lines parallel to x=y, starting at y axis
                startLine = 1;
                nLines = height-2;
                lineInc = width;
                pointInc = width + 1;
            break;
            case DIAGONAL_2A:       //lines parallel to x=-y, starting at x axis
                startLine = 2;
                nLines = width;
                lineInc = 1;
                pointInc = width - 1;
            break;
            case DIAGONAL_2B:       //lines parallel to x=-y, starting at x=width-1, y=variable
                startLine = 0;
                nLines = height-2;
                lineInc = width;
                pointInc = width - 1;
            break;
        }
        for (int i=startLine; i<nLines; i++) {
            if (i%50==0) {
                if (Thread.currentThread().isInterrupted()) return;
                showProgress(i/(double)nLines);
            }
            int startPixel = i*lineInc;
            if (direction == DIAGONAL_2B) startPixel += width-1;
            switch (direction) {
                case DIAGONAL_1A: length = Math.min(height, width-i); break;
                case DIAGONAL_1B: length = Math.min(width, height-i); break;
                case DIAGONAL_2A: length = Math.min(height, i+1);     break;
                case DIAGONAL_2B: length = Math.min(width, height-i); break;
            }
            lineSlideParabola(pixels, startPixel, pointInc, length, coeff2, cache, nextPoint, null);
        }
        pass++;
    } //void filter1D

    /** Process one straight line in the image by sliding a parabola along the line
     *  (from the bottom) and setting the values to make all points reachable by
     *  the parabola
     * @param pixels    Image data, will be modified by parabolic interpolation
     *                  where the parabola does not touch.
     * @param start     Index of first pixel of the line in pixels array
     * @param inc       Increment of index in pixels array
     * @param length    Number of points the line consists of
     * @param coeff2    2nd order coefficient of the polynomial describing the parabola,
     *                  must be positive (although a parabola with negative curvature is
     *                  actually used)
     * @param cache     Work array, length at least <code>length</code>. Will usually remain
     *                  in the CPU cache and may therefore speed up the code.
     * @param nextPoint Work array. Will hold the index of the next point with sufficient local
     *                  curvature to get touched by the parabola.
     * @param correctedEdges Should be a 2-element array used for output or null.
     * @return          The correctedEdges array (if non-null on input) with the two estimated
     *                  edge pixel values corrected for edge particles.
     */
    static float[] lineSlideParabola(float[] pixels, int start, int inc, int length, float coeff2, float[] cache, int[] nextPoint, float[] correctedEdges) {
        float minValue = Float.MAX_VALUE;
        int lastpoint = 0;
        int firstCorner = length-1;             // the first point except the edge that is touched
        int lastCorner = 0;                     // the last point except the edge that is touched
        float vPrevious1 = 0f;
        float vPrevious2 = 0f;
        float curvatureTest = 1.999f*coeff2;     //not 2: numeric scatter of 2nd derivative
        /* copy data to cache, determine the minimum, and find points with local curvature such
         * that the parabola can touch them - only these need to be examined futher on */
        for (int i=0, p=start; i<length; i++, p+=inc) {
            float v = pixels[p];
            cache[i] = v;
            if (v < minValue) minValue = v;
            if (i >= 2 && vPrevious1+vPrevious1-vPrevious2-v < curvatureTest) {
                nextPoint[lastpoint] = i-1;     // point i-1 may be touched
                lastpoint = i-1;
            }
            vPrevious2 = vPrevious1;
            vPrevious1 = v;
        }
        nextPoint[lastpoint] = length-1;
        nextPoint[length-1] = Integer.MAX_VALUE;// breaks the search loop

        int i1 = 0;                             // i1 and i2 will be the two points where the parabola touches
        while (i1<length-1) {
            float v1 = cache[i1];
            float minSlope = Float.MAX_VALUE;
            int i2 = 0;                         //(initialized to avoid compile-time error)
            int searchTo = length;
            int recalculateLimitNow = 0;        // when 0, limits for searching will be recalculated
            /* find the second point where the parabola through point i1,v1 touches: */
            for (int j=nextPoint[i1]; j<searchTo; j=nextPoint[j], recalculateLimitNow++) {
                float v2 = cache[j];
                float slope = (v2-v1)/(j-i1)+coeff2*(j-i1);
                if (slope < minSlope) {
                    minSlope = slope;
                    i2 = j;
                    recalculateLimitNow = -3;
                }
                if (recalculateLimitNow==0) {   //time-consuming recalculation of search limit: wait a bit after slope is updated
                    double b = 0.5f*minSlope/coeff2;
                    int maxSearch = i1+(int)(b+Math.sqrt(b*b+(v1-minValue)/coeff2)+1); //(numeric overflow may make this negative)
                    if (maxSearch < searchTo && maxSearch > 0) searchTo = maxSearch;
                }
            }
            if (i1 == 0) firstCorner = i2;
            if (i2 == length-1) lastCorner = i1;
            /* interpolate between the two points where the parabola touches: */
            for (int j=i1+1, p=start+j*inc; j<i2; j++, p+=inc)
                pixels[p] = v1 + (j-i1)*(minSlope - (j-i1)*coeff2);
            i1 = i2;                            // continue from this new point
        } //while (i1<length-1)
        /* Now calculate estimated edge values without an edge particle, allowing for vignetting
         * described as a 6th-order polynomial: */
        if (correctedEdges != null) {
            if (4*firstCorner >= length) firstCorner = 0; // edge particles must be < 1/4 image size
            if (4*(length - 1 - lastCorner) >= length) lastCorner = length - 1;
            float v1 = cache[firstCorner];
            float v2 = cache[lastCorner];
            float slope = (v2-v1)/(lastCorner-firstCorner); // of the line through the two outermost non-edge touching points
            float value0 = v1 - slope * firstCorner;        // offset of this line
            float coeff6 = 0;                               // coefficient of 6th order polynomial
            float mid = 0.5f * (lastCorner + firstCorner);
            for (int i=(length+2)/3; i<=(2*length)/3; i++) {// compare with mid-image pixels to detect vignetting
                float dx = (i-mid)*2f/(lastCorner-firstCorner);
                float poly6 = dx*dx*dx*dx*dx*dx - 1f;       // the 6th order polynomial, zero at firstCorner and lastCorner
                if (cache[i] < value0 + slope*i + coeff6*poly6) {
                    coeff6 = -(value0 + slope*i - cache[i])/poly6;
                }
            }
            float dx = (firstCorner-mid)*2f/(lastCorner-firstCorner);
            correctedEdges[0] = value0 + coeff6*(dx*dx*dx*dx*dx*dx - 1f) + coeff2*firstCorner*firstCorner;
            dx = (lastCorner-mid)*2f/(lastCorner-firstCorner);
            correctedEdges[1] = value0 + (length-1)*slope + coeff6*(dx*dx*dx*dx*dx*dx - 1f) + coeff2*(length-1-lastCorner)*(length-1-lastCorner);
            //IJ.log("edge corr: corners@"+firstCorner+","+lastCorner+":"+v1+","+v2);
            //IJ.log("from "+cache[0]+","+cache[length-1]+" to linear:"+(v1-firstCorner*slope)+","+(v2+(length-1-lastCorner)*slope)+";full:"+correctedEdges[0]+","+correctedEdges[1]);
        }
        return correctedEdges;
    } //void lineSlideParabola

    /** Detect corner particles and adjust corner pixels if a particle is there.
     *  Analyzing the directions parallel to the edges and the diagonals, we
     *  average over the 3 correction values (found for the 3 directions)
     */
    void correctCorners(FloatProcessor fp, float coeff2, float[] cache, int[] nextPoint) {
        int width = fp.getWidth();
        int height = fp.getHeight();
        float[] pixels = (float[])fp.getPixels();
        float[] corners = new float[4];         //(0,0); (xmax,0); (ymax,0); (xmax,ymax)
        float[] correctedEdges = new float[2];
        correctedEdges = lineSlideParabola(pixels, 0, 1, width, coeff2, cache, nextPoint, correctedEdges);
        corners[0] = correctedEdges[0];
        corners[1] = correctedEdges[1];
        correctedEdges = lineSlideParabola(pixels, (height-1)*width, 1, width, coeff2, cache, nextPoint, correctedEdges);
        corners[2] = correctedEdges[0];
        corners[3] = correctedEdges[1];
        correctedEdges = lineSlideParabola(pixels, 0, width, height, coeff2, cache, nextPoint, correctedEdges);
        corners[0] += correctedEdges[0];
        corners[2] += correctedEdges[1];
        correctedEdges = lineSlideParabola(pixels, width-1, width, height, coeff2, cache, nextPoint, correctedEdges);
        corners[1] += correctedEdges[0];
        corners[3] += correctedEdges[1];
        int diagLength = Math.min(width,height);        //length of a 45-degree line from a corner
        float coeff2diag = 2 * coeff2;
        correctedEdges = lineSlideParabola(pixels, 0, 1+width, diagLength, coeff2diag, cache, nextPoint, correctedEdges);
        corners[0] += correctedEdges[0];
        correctedEdges = lineSlideParabola(pixels, width-1, -1+width, diagLength, coeff2diag, cache, nextPoint, correctedEdges);
        corners[1] += correctedEdges[0];
        correctedEdges = lineSlideParabola(pixels, (height-1)*width, 1-width, diagLength, coeff2diag, cache, nextPoint, correctedEdges);
        corners[2] += correctedEdges[0];
        correctedEdges = lineSlideParabola(pixels, width*height-1, -1-width, diagLength, coeff2diag, cache, nextPoint, correctedEdges);
        corners[3] += correctedEdges[0];
        //IJ.log("corner 00:"+pixels[0]+"->"+(corners[0]/3));
        //IJ.log("corner 01:"+pixels[width-1]+"->"+(corners[1]/3));
        //IJ.log("corner 10:"+pixels[(height-1)*width]+"->"+(corners[2]/3));
        //IJ.log("corner 11:"+pixels[width*height-1]+"->"+(corners[3]/3));
        if (pixels[0] > corners[0]/3) pixels[0] = corners[0]/3;
        if (pixels[width-1] > corners[1]/3) pixels[width-1] = corners[1]/3;
        if (pixels[(height-1)*width] > corners[2]/3) pixels[(height-1)*width] = corners[2]/3;
        if (pixels[width*height-1] > corners[3]/3) pixels[width*height-1] = corners[3]/3;
        //new ImagePlus("corner corrected",fp.duplicate()).show();
    } //void correctCorners

    public void setNPasses(int nPasses) {
        if (isRGB && separateColors) nPasses *= 3;
        this.nPasses = nPasses*DIRECTION_PASSES;
        pass = 0;
    }

    private void showProgress(double percent) {
        if (nPasses <= 0) return;
        percent = (double)pass/nPasses + percent/nPasses;
        IJ.showProgress(percent);
    }

}
