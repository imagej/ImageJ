package ij.process;
import java.awt.*;
import ij.*;
import ij.process.*;

/** This class processes stacks. */
public class StackProcessor {
    private ImageStack stack;
    private ImageProcessor ip;
	int nSlices;
	double xScale, yScale;
	int[] table;
	    
    /* Constructs a StackProcessor from a stack. */
    //public StackProcessor(ImageStack stack) {
    //	this.StackProcessor(stack, stack.getProcessor());
   //}
	
    /** Constructs a StackProcessor from a stack. 'ip' is the
    	processor that will be used to process the slices. */
    public StackProcessor(ImageStack stack, ImageProcessor ip) {
    	this.stack = stack;
    	this.ip = ip;
    	nSlices = stack.getSize();
 	    if (nSlices>1)
 	    	ip.setProgressBar(null);
   }
	
	static final int FLIPH=0, FLIPV=1, SCALE=2, INVERT=3, APPLY_TABLE=4;
	
	void process(int command) {
	    String s = "";
    	switch (command) {
    		case FLIPH: case FLIPV: s="Flip: "; break;
    		case SCALE: s="Scale: "; break;
    		case INVERT: s="Invert: "; break;
    		case APPLY_TABLE: s="Apply: "; break;
    	}
	    for (int i=1; i<=nSlices; i++) {
    		showStatus(s,i,nSlices);
 	    	ImageProcessor ip = stack.getProcessor(i);
	    	if (nSlices==1 && i==1 && command==SCALE)
	    		ip.snapshot();
	    	switch (command) {
	    		case FLIPH: ip.flipHorizontal(); break;
	    		case FLIPV: ip.flipVertical(); break;
	    		case SCALE: ip.scale(xScale, yScale); break;
	    		case INVERT: ip.invert(); break;
	    		case APPLY_TABLE: ip.applyTable(table); break;
	    	}
			IJ.showProgress((double)i/nSlices);
	    }
		IJ.showProgress(1.0);
	}

	public void invert() {
		process(INVERT);
	}
	
	public void flipHorizontal() {
		process(FLIPH);
	}
	
	public void flipVertical() {
		process(FLIPV);
	}
	
	public void applyTable(int[] table) {
		this.table = table;
		process(APPLY_TABLE);
	}

	public void scale(double xScale, double yScale) {
		this.xScale = xScale;
		this.yScale = yScale;
		process(SCALE);
 	}

	/** Creates a new stack with dimensions 'newWidth' x 'newHeight'.
		To reduce memory requirements, the orginal stack is deleted
		as the new stack is created. */
	public ImageStack resize(int newWidth, int newHeight) {
	    ImageStack stack2 = new ImageStack(newWidth, newHeight);
 		ImageProcessor ip2;
		try {
	    	for (int i=1; i<=nSlices; i++) {
    			showStatus("Resize: ",i,nSlices);
	    		ip.setPixels(stack.getPixels(1));
	    		String label = stack.getSliceLabel(1);
	    		stack.deleteSlice(1);
				System.gc();
				ip2 = ip.resize(newWidth, newHeight);
				if (ip2!=null)
					stack2.addSlice(label, ip2);
				IJ.showProgress((double)i/nSlices);
	    	}
			IJ.showProgress(1.0);
		} catch(OutOfMemoryError o) {
			while(stack.getSize()>1)
				stack.deleteLastSlice();
			IJ.outOfMemory("StackProcessor.resize");
			IJ.showProgress(1.0);
		}
		return stack2;
	}

	ImageStack rotate90Degrees(boolean clockwise) {
 	    ImageStack stack2 = new ImageStack(stack.getHeight(), stack.getWidth());
 		ImageProcessor ip2;
    	for (int i=1; i<=nSlices; i++) {
    		showStatus("Rotate: ",i,nSlices);
    		ip.setPixels(stack.getPixels(1));
    		String label = stack.getSliceLabel(1);
    		stack.deleteSlice(1);
			System.gc();
			if (clockwise)
				ip2 = ip.rotateRight();
			else
				ip2 = ip.rotateLeft();
			if (ip2!=null)
				stack2.addSlice(label, ip2);
			IJ.showProgress((double)i/nSlices);
    	}
		IJ.showProgress(1.0);
		return stack2;
	}
	
	public ImageStack rotateRight() {
		return rotate90Degrees(true);
 	}
 	
	public ImageStack rotateLeft() {
		return rotate90Degrees(false);
 	}
 	
 	public void copyBits(ImageProcessor src, int xloc, int yloc, int mode) {
 		copyBits(src, null, xloc, yloc, mode);
 	}

 	public void copyBits(ImageStack src, int xloc, int yloc, int mode) {
 		copyBits(null, src, xloc, yloc, mode);
 	}

 	private void copyBits(ImageProcessor srcIp, ImageStack srcStack, int xloc, int yloc, int mode) {
	    int inc = nSlices/20;
	    if (inc<1) inc = 1;
	    boolean stackSource = srcIp==null;
	    for (int i=1; i<=nSlices; i++) {
	    	if (stackSource)
	    		srcIp = srcStack.getProcessor(i);
 	    	ImageProcessor dstIp = stack.getProcessor(i);
	    	dstIp.copyBits(srcIp, xloc, yloc, mode);
			if ((i%inc) == 0) IJ.showProgress((double)i/nSlices);
	    }
		IJ.showProgress(1.0);
 	}
 	
 	void showStatus(String s, int n, int total) {
 		IJ.showStatus(s+n+"/"+total);
 	}	
 	
}