package ij.plugin.filter;
import ij.*;
import ij.gui.GenericDialog;
import ij.process.*;
import ij.plugin.filter.GaussianBlur;
import ij.measure.Measurements;
import java.awt.*;

/** This plugin-filter implements ImageJ's Unsharp Mask command.
 * Unsharp masking subtracts a blurred copy of the image and rescales the image
 * to obtain the same contrast of large (low-frequency) structures as in the
 * input image. This is equivalent to adding a high-pass filtered image and
 * thus sharpens the image.
 * "Sigma (Radius)" is the standard deviation (blur radius) of the Gaussian blur that
 * is subtracted. "Mask Weight" determines the strength of filtering, where "Mask Weight"=1
 * would be an infinite weight of the high-pass filtered image that is added.
 */
public class UnsharpMask implements PlugInFilter {
    private static double sigma = 1.0; // standard deviation of the Gaussian
    private static double weight = 0.6; // weight of the mask
    private ImagePlus imp;
    private int slice;
    private GaussianBlur gb;

    /** Method to return types supported
     * @param arg Not used by this plugin
     * @param imp The image to be filtered
     * @return Code Describes supported formats etc. (see ij.plugin.filter)
     */
    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        int flags = DOES_ALL|SUPPORTS_MASKING|CONVERT_TO_FLOAT|SNAPSHOT;
        if (!showDialog()) return DONE;
        gb = new GaussianBlur(imp);
        return IJ.setupDialog(imp, flags);  //ask whether to process all slices of stack (if a stack)
    }
    
    /** This method is invoked for each slice or color channel. It filters
     * an image by enhancing high-frequency components. Since this
     * PlugInFilter specifies the CONVERT_TO_FLOAT and SNAPHOT
     * flags, 'ip' is always a FloatProcessor with a valid snapshot.
     * @param ip The image, slice or channel to filter
     */
    public void run(ImageProcessor ip) {
        slice++;
		if (slice>1) IJ.showStatus("Unsharp Mask: "+slice+"/"+
			(imp.getStackSize()*imp.getProcessor().getNChannels()));
        sharpenFloat((FloatProcessor)ip, sigma, (float)weight);
    }
    
    /** Unsharp Mask filtering of a float image. 'fp' must have a valid snapshot.     */
    public void sharpenFloat(FloatProcessor fp, double sigma, float weight) {
        gb.blurGaussian(fp, sigma, sigma, 0.01);
        float[] pixels = (float[])fp.getPixels();
        float[] snapshotPixels = (float[])fp.getSnapshotPixels();
        int width = fp.getWidth();
        Rectangle roi = fp.getRoi();
        for (int y=roi.y; y<roi.y+roi.height; y++)
            for (int x=roi.x, p=width*y+x; x<roi.x+roi.width; x++,p++)
                pixels[p] = (snapshotPixels[p] - weight*pixels[p])/(1f - weight);
    }

    /** Ask the user for the parameters */
    public boolean showDialog() {
        String options = Macro.getOptions();
        boolean oldMacro = false;    //for old macros, "radius" was 2.5 sigma
        if  (options!=null) {
            if (options.indexOf("gaussian=") >= 0) {
                oldMacro = true;
                Macro.setOptions(options.replaceAll("gaussian=", "radius="));
            }
        }
        GenericDialog gd = new GenericDialog("Unsharp Mask...");
        sigma = Math.abs(sigma);
        if (weight<0) weight = 0;
        if (weight>0.99) weight = 0.99; 
        gd.addNumericField("Radius (Sigma)", sigma, 1, 6, "pixels");
        gd.addNumericField("Mask Weight (0.1-0.9)", weight,2);
        gd.showDialog();                        //input by the user (or macro) happens here
        if (gd.wasCanceled()) return false;
        sigma = gd.getNextNumber();
        weight = gd.getNextNumber();
        if (sigma < 0 || weight < 0 || weight > 0.99 || gd.invalidNumber())
            return false;
        if (oldMacro) sigma /= 2.5;
        return true;
    }

}