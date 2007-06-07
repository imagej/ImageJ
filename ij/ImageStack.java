package ij;
import java.awt.*;
import java.awt.image.*;
import ij.process.*;

/**
This class represents an expandable array of images.
@see ImagePlus
*/

public class ImageStack {

	static final int INITIAL_SIZE = 25;
	static final String outOfRange = "Argument out of range: ";
	private int nSlices = 0;
	private Object[] stack;
	private String[] label;
	private int width, height;
	private Rectangle roi;
	private ColorModel cm;
	private double min=Double.MAX_VALUE;
	private double max;
	private static boolean msgShown;
	
	/** Creates a new, empty image stack. */
	public ImageStack(int width, int height) {
		this(width, height, null);
	}
	
	/** Creates a new, empty image stack. */
	public ImageStack(int width, int height, ColorModel cm) {
		this.width = width;
		this.height = height;
		this.cm = cm;
		stack = new Object[INITIAL_SIZE];
		label = new String[INITIAL_SIZE];
		nSlices = 0;
	}

	/** Adds an image to the end of the stack. */
	public void addSlice(String sliceLabel, Object pixels) {
		if (pixels==null) 
			throw new IllegalArgumentException("'pixels' is null!");
		nSlices++;
		if (nSlices==stack.length) {
			Object[] tmp1 = new Object[nSlices*2];
			System.arraycopy(stack, 0, tmp1, 0, nSlices);
			stack = tmp1;
			String[] tmp2 = new String[nSlices*2];
			System.arraycopy(label, 0, tmp2, 0, nSlices);
			label = tmp2;
		}
		stack[nSlices-1] = pixels;
		this.label[nSlices-1] = sliceLabel;
	}
	
	/** Obsolete. Short images are always unsigned. */
	public void addUnsignedShortSlice(String sliceLabel, Object pixels) {
		addSlice(sliceLabel, pixels);
	}
	
	/** Adds the image in 'ip' to the end of the stack. */
	public void addSlice(String sliceLabel, ImageProcessor ip) {
		if (ip.getWidth()!=width || ip.getHeight()!=height)
			throw new IllegalArgumentException("Dimensions do not match");
		if (nSlices==0) {
			cm = ip.getColorModel();
			min = ip.getMin();
			max = ip.getMax();
		}
		addSlice(sliceLabel, ip.getPixels());
	}
	
	/** Adds the image in 'ip' to the stack following slice 'n'. Adds
		the slice to the beginning of the stack if 'n' is zero. */
	public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
		if (n<0 || n>nSlices)
			throw new IllegalArgumentException(outOfRange+n);
		addSlice(sliceLabel, ip);
		Object tempSlice = stack[nSlices-1];
		String tempLabel = label[nSlices-1];
		int first = n>0?n:1;
		for (int i=nSlices-1; i>=first; i--) {
			stack[i] = stack[i-1];
			label[i] = label[i-1];
		}
		stack[n] = tempSlice;
		label[n] = tempLabel;
	}
	
	/** Deletes the specified slice, were 1<=n<=nslices. */
	public void deleteSlice(int n) {
		if (n<1 || n>nSlices)
			throw new IllegalArgumentException(outOfRange+n);
		if (nSlices<1)
			return;
		for (int i=n; i<nSlices; i++) {
			stack[i-1] = stack[i];
			label[i-1] = label[i];
		}
		stack[nSlices-1] = null;
		label[nSlices-1] = null;
		nSlices--;
	}
	
	/** Deletes the last slice in the stack. */
	public void deleteLastSlice() {
		if (nSlices>0)
			deleteSlice(nSlices);
	}
	
    public int getWidth() {
    	return width;
    }

    public int getHeight() {
    	return height;
    }
    
	public void setRoi(Rectangle roi) {
		this.roi = roi;
	}
	
	public Rectangle getRoi() {
		return(this.roi);
	}
	
	/** Updates this stack so its attributes, such as min and max
		displayed value, are the same as 'ip'. */
	public void update(ImageProcessor ip) {
		min = ip.getMin();
		max = ip.getMax();
	}
	
	/** Returns the pixel array for the specified slice, were 1<=n<=nslices. */
	public Object getPixels(int n) {
		if (n<1 || n>nSlices)
			throw new IllegalArgumentException(outOfRange+n);
		return stack[n-1];
	}
	
	/** Assigns a pixel array to the specified slice,
		were 1<=n<=nslices. */
	public void setPixels(Object pixels, int n) {
		if (pixels==null) 
			throw new IllegalArgumentException("'pixels' is null!");
		if (n<1 || n>nSlices)
			throw new IllegalArgumentException(outOfRange+n);
		stack[n-1] = pixels;
	}
	
	/** Returns the stack as an array of 1D pixel arrays. Note
		that the size of the returned array may be greater than
		the number of slices currently in the stack, with
		unused elements set to null. */
	public Object[] getImageArray() {
		return stack;
	}
	
	/** Returns the number of slices in this stack. */
	public int getSize() {
		return nSlices;
	}

	/** Returns the label of the specified slice, were 1<=n<=nslices.
		Returns null if the slice does not have a label. */
	public String getSliceLabel(int n) {
		if (n<1 || n>nSlices)
			throw new IllegalArgumentException(outOfRange+n);
		return label[n-1];
	}
	
	/** Sets the label of the specified slice, were 1<=n<=nslices. */
	public void setSliceLabel(String label, int n) {
		if (n<1 || n>nSlices)
			throw new IllegalArgumentException(outOfRange+n);
		this.label[n-1] = label;
	}
	
	/** Returns an ImageProcessor for the specified slice,
		were 1<=n<=nslices. Returns null if the stack is empty.
	*/
	public ImageProcessor getProcessor(int n) {
		if (n<1 || n>nSlices)
			throw new IllegalArgumentException(outOfRange+n);
		if (nSlices==0) return null;
		ImageProcessor ip = null;
		if (stack[0] instanceof byte[])
			ip = new ByteProcessor(width, height, (byte[])stack[n-1], cm);
		else if (stack[0] instanceof short[])
			ip = new ShortProcessor(width, height, (short[])stack[n-1], cm);
		else if (stack[0] instanceof int[])
			ip = new ColorProcessor(width, height, (int[])stack[n-1]);
		else if (stack[0] instanceof float[])
			ip = new FloatProcessor(width, height, (float[])stack[n-1], cm);
		if (min!=Double.MAX_VALUE && ip!=null && !(ip instanceof ColorProcessor))
			ip.setMinAndMax(min, max);
		return ip;
	}
	
	/** Assigns a new color model to this stack. */
	public void setColorModel(ColorModel cm) {
		this.cm = cm;
	}
	
	/** Returns this stack's color model. My return null. */
	public ColorModel getColorModel() {
		return cm;
	}
	
	/** Returns true if this is a 3-slice RGB stack. */
	public boolean isRGB() {
    	if (nSlices==3 && getSliceLabel(1)!=null && getSliceLabel(1).equals("Red"))	
			return true;
		else
			return false;
	}
	
	/** Returns true if this is a 3-slice HSB stack. */
	public boolean isHSB() {
    	if (nSlices==3 && getSliceLabel(1)!=null && getSliceLabel(1).equals("Hue"))	
			return true;
		else
			return false;
	}
	/** Frees memory by deleting a few slices from the end of the stack. */
	public void trim() {
		int n = (int)Math.round(Math.log(nSlices)+1.0);
		for (int i=0; i<n; i++) {
			deleteLastSlice();
			System.gc();
		}
	}

	public String toString() {
		return ("width="+width+", height="+height+", nSlices="+nSlices+", cm="+cm);
	}
		
}