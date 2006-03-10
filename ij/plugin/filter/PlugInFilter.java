package ij.plugin.filter;
import ij.*;
import ij.process.*;

/** ImageJ plugins that process an image should implement this interface. */
public interface PlugInFilter {

	/** This method is called once when the filter is loaded. 'arg',
		which may be blank, is the argument specified for this plugin 
		in IJ_Props.txt. 'imp' is the currently active image.
		This method should return a flag word that specifies the
		filters capabilities. */
	public int setup(String arg, ImagePlus imp);

	/** Filters use this method to process the image. If the
	 	SUPPORTS_STACKS flag was set, it is called for each slice in
	 	a stack. ImageJ will lock the image before calling
		this method and unlock it when the filter is finished. */
	public void run(ImageProcessor ip);

	/** Set this flag if the filter handles 8-bit grayscale images. */
	public int DOES_8G = 1;
	/** Set this flag if the filter handles 8-bit indexed color images. */
	public int DOES_8C = 2;
	/** Set this flag if the filter handles 16-bit images. */
	public int DOES_16 = 4;
	/** Set this flag if the filter handles float images. */
	public int DOES_32 = 8;
	/** Set this flag if the filter handles RGB images. */
	public int DOES_RGB = 16;
	/** Set this flag if the filter handles all types of images. */
	public int DOES_ALL = DOES_8G+DOES_8C+DOES_16+DOES_32+DOES_RGB;
	/** Set this flag if the filter wants its run() method to be
		called for all the slices in a stack. */
	public int DOES_STACKS = 32;
	/** Set this flag if the filter wants ImageJ, for non-rectangular
		ROIs, to restore that part of the image that's inside the bounding
		rectangle but outside of the ROI. */
	public int SUPPORTS_MASKING = 64;
	/** Set this flag if the filter makes no changes to the pixel data. */
	public int NO_CHANGES = 128;
	/** Set this flag if the filter does not require undo. */
	public int NO_UNDO = 256;
	/** Set this flag if the filter does not require that an image be open. */
	public int NO_IMAGE_REQUIRED = 512;
	/** Set this flag if the filter requires an ROI. */
	public int ROI_REQUIRED = 1024;
	/** Set this flag if the filter requires a stack. */
	public int STACK_REQUIRED = 2048;
	/** Set this flag if the filter does not want its run method called. */
	public int DONE = 4096;
	
}