package ij.process;
import java.awt.*;
import ij.*;
import ij.process.*;
import ij.macro.Interpreter;
import ij.util.ArrayUtil;
import ij.plugin.Filters3D;
import java.util.concurrent.atomic.AtomicInteger;


/** This class processes stacks. */
public class StackProcessor {
    public final static int FILTER_MEAN=Filters3D.MEAN, FILTER_MEDIAN=Filters3D.MEDIAN, FILTER_MIN=Filters3D.MIN,
		FILTER_MAX=Filters3D.MAX, FILTER_VAR=Filters3D.VAR, FILTER_MAXLOCAL=Filters3D.MAXLOCAL;

    private ImageStack stack;
    private ImageProcessor ip;
	int nSlices;
	double xScale, yScale;
	int[] table;
	double fillValue;
	float[] voxels;
	    
    /** Constructs a StackProcessor from a stack. */
    public StackProcessor(ImageStack stack) {
    	this(stack, null);
    }

    /** Constructs a StackProcessor from a stack. 'ip' is the
    	processor that will be used to process the slices. 
    	'ip' can be null when using crop(). */
    public StackProcessor(ImageStack stack, ImageProcessor ip) {
    	this.stack = stack;
    	this.ip = ip;
    	nSlices = stack.getSize();
 	    if (nSlices>1 && ip!=null)
 	    	ip.setProgressBar(null);
   }
	
	static final int FLIPH=0, FLIPV=1, SCALE=2, INVERT=3, APPLY_TABLE=4, SCALE_WITH_FILL=5;
	
	void process(int command) {
	    String s = "";
 	   	ImageProcessor ip2 = stack.getProcessor(1);
    	switch (command) {
    		case FLIPH: case FLIPV: s="Flip: "; break;
    		case SCALE: s="Scale: "; break;
    		case SCALE_WITH_FILL: s="Scale: "; ip2.setBackgroundValue(fillValue); break;
    		case INVERT: s="Invert: "; break;
    		case APPLY_TABLE: s="Apply: "; break;
    	}
    	if (ip==null)
    		ip = ip2;
 	   	ip2.setRoi(this.ip.getRoi());
	    ip2.setInterpolate(this.ip.getInterpolate());
	    for (int i=1; i<=nSlices; i++) {
    		showStatus(s,i,nSlices);
	    	ip2.setPixels(stack.getPixels(i));
	    	if (nSlices==1 && i==1 && command==SCALE)
	    		ip2.snapshot();
	    	switch (command) {
	    		case FLIPH: ip2.flipHorizontal(); break;
	    		case FLIPV: ip2.flipVertical(); break;
	    		case SCALE: case SCALE_WITH_FILL: ip2.scale(xScale, yScale); break;
	    		case INVERT: ip2.invert(); break;
	    		case APPLY_TABLE: ip2.applyTable(table); break;
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

	public void scale(double xScale, double yScale, double fillValue) {
		this.xScale = xScale;
		this.yScale = yScale;
		this.fillValue = fillValue;
		process(SCALE_WITH_FILL);
 	}

	/** Creates a new stack with dimensions 'newWidth' x 'newHeight'.
		To reduce memory requirements, the orginal stack is deleted
		as the new stack is created. */
	public ImageStack resize(int newWidth, int newHeight) {
		return resize(newWidth, newHeight, false);
	}

	public ImageStack resize(int newWidth, int newHeight, boolean averageWhenDownsizing) {
	    ImageStack stack2 = new ImageStack(newWidth, newHeight);
 		ImageProcessor ip2;
 		Rectangle roi = ip!=null?ip.getRoi():null;
    	if (ip==null)
    		ip = stack.getProcessor(1).duplicate();
		try {
	    	for (int i=1; i<=nSlices; i++) {
    			showStatus("Resize: ",i,nSlices);
	    		ip.setPixels(stack.getPixels(1));
	    		String label = stack.getSliceLabel(1);
	    		stack.deleteSlice(1);
				ip2 = ip.resize(newWidth, newHeight, averageWhenDownsizing);
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

	/** Crops the stack to the specified rectangle. */
	public ImageStack crop(int x, int y, int width, int height) {
	    ImageStack stack2 = new ImageStack(width, height);
 		ImageProcessor ip2;
		for (int i=1; i<=nSlices; i++) {
			ImageProcessor ip1 = stack.getProcessor(1);
			ip1.setRoi(x, y, width, height);
			String label = stack.getSliceLabel(1);
			stack.deleteSlice(1);
			ip2 = ip1.crop();
			stack2.addSlice(label, ip2);
			IJ.showProgress((double)i/nSlices);
		}
		IJ.showProgress(1.0);
		return stack2;
	}

	ImageStack rotate90Degrees(boolean clockwise) {
 	    ImageStack stack2 = new ImageStack(stack.getHeight(), stack.getWidth());
 		ImageProcessor ip2;
    	if (ip==null)
    		ip = stack.getProcessor(1).duplicate();
    	for (int i=1; i<=nSlices; i++) {
    		showStatus("Rotate: ",i,nSlices);
    		ip.setPixels(stack.getPixels(1));
    		String label = stack.getSliceLabel(1);
    		stack.deleteSlice(1);
			if (clockwise)
				ip2 = ip.rotateRight();
			else
				ip2 = ip.rotateLeft();
			if (ip2!=null)
				stack2.addSlice(label, ip2);
			if (!Interpreter.isBatchMode())
				IJ.showProgress((double)i/nSlices);
    	}
		if (!Interpreter.isBatchMode())
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
 	
    /**
     * Thomas Boudier Create a kernel neighorhood as an ellipsoid
     *
     * @param radx Radius x of the ellipsoid
     * @param rady Radius x of the ellipsoid
     * @param radz Radius x of the ellipsoid
     * @return The kernel as an array
     */
    private int[] createKernelEllipsoid(float radx, float rady, float radz) {
        int vx = (int) Math.ceil(radx);
        int vy = (int) Math.ceil(rady);
        int vz = (int) Math.ceil(radz);
        int[] ker = new int[(2 * vx + 1) * (2 * vy + 1) * (2 * vz + 1)];
        double dist;

        double rx2 = radx * radx;
        double ry2 = rady * rady;
        double rz2 = radz * radz;

        if (rx2 != 0) {
            rx2 = 1.0 / rx2;
        } else {
            rx2 = 0;
        }
        if (ry2 != 0) {
            ry2 = 1.0 / ry2;
        } else {
            ry2 = 0;
        }
        if (rz2 != 0) {
            rz2 = 1.0 / rz2;
        } else {
            rz2 = 0;
        }

        int idx = 0;
        for (int k = -vz; k <= vz; k++) {
            for (int j = -vy; j <= vy; j++) {
                for (int i = -vx; i <= vx; i++) {
                    dist = ((double) (i * i)) * rx2 + ((double) (j * j)) * ry2 + ((double) (k * k)) * rz2;
                    if (dist <= 1.0) {
                        ker[idx] = 1;
                    } else {
                        ker[idx] = 0;
                    }
                    idx++;
                }
            }
        }

        return ker;
    }

    /**
     * 3D filter using threads
     *
     * @param out
     * @param radx Radius of mean filter in x
     * @param rady Radius of mean filter in y
     * @param radz Radius of mean filter in z
     * @param zmin
     * @param zmax
     * @param filter
     */
    public void filter3D(ImageStack out, float radx, float rady, float radz, int zmin, int zmax, int filter) {
        int[] ker = this.createKernelEllipsoid(radx, rady, radz);
        int nb = 0;
        for (int i=0; i<ker.length; i++)
            nb += ker[i];
        if (zmin<0)
            zmin = 0;
        if (zmax>stack.getSize())
            zmax = stack.getSize();
        int sizex = stack.getWidth();
        int sizey = stack.getHeight();
        double value;
        for (int z=zmin; z<zmax; z++) {
            if (zmin==0) IJ.showProgress(z+1, zmax);
            for (int y=0; y<sizey; y++) {
                for (int x=0; x<sizex; x++) {
                    ArrayUtil tab = getNeighborhood(ker, nb, x, y, z, radx, rady, radz);
                    switch (filter) {
						case FILTER_MEAN:
							out.setVoxel(x, y, z, tab.getMean()); break;
						case FILTER_MEDIAN:
							out.setVoxel(x, y, z, tab.medianSort()); break;
						case FILTER_MIN:
							out.setVoxel(x, y, z, tab.getMinimum()); break;
						case FILTER_MAX:
							out.setVoxel(x, y, z, tab.getMaximum()); break;
						case FILTER_VAR:
							out.setVoxel(x, y, z, tab.getVariance()); break;
						case FILTER_MAXLOCAL:
							value = stack.getVoxel(x, y, z);
							if (tab.isMaximum(value))
								out.setVoxel(x, y, z, value);
							else
								out.setVoxel(x, y, z, 0);
							break;
                    } //switch
                }  //x
            } //y
        } //z
    }

    /*
    private ArrayUtil getNeighborhood(int[] ker, int nbval, int x, int y, int z, float radx, float rady, float radz) {
        int vx = (int)Math.ceil(radx);
        int vy = (int)Math.ceil(rady);
        int vz = (int)Math.ceil(radz);
        int x0=x-vx, y0=y-vy, z0=z-vz;
        int w=vx*2, h=vy*2, d=vz*2;
		if (x0<0 || x0+w>stack.getWidth() || y0<0 || y0+h>stack.getHeight() || z0<0 || z0+d>stack.getSize())
			return getEdgeNeighborhood(ker, nbval, x, y, z, radx, rady, radz);
        voxels = stack.getVoxels(x0, y0, z0, w, h, d, voxels);
		return new ArrayUtil(voxels);
    }
    */

    /**
     * Gets the neighboring attribute of the Image3D with a kernel as a array
     *
     * @param ker The kernel array (>0 ok)
     * @param nbval The number of non-zero values
     * @param x Coordinate x of the pixel
     * @param y Coordinate y of the pixel
     * @param z Coordinate z of the pixel
     * @param radx Radius x of the neighboring
     * @param radz Radius y of the neighboring
     * @param rady Radius z of the neighboring
     * @return The values of the nieghbor pixels inside an array
     */
    private ArrayUtil getNeighborhood(int[] ker, int nbval, int x, int y, int z, float radx, float rady, float radz) {
        ArrayUtil pix = new ArrayUtil(nbval);
        int vx = (int) Math.ceil(radx);
        int vy = (int) Math.ceil(rady);
        int vz = (int) Math.ceil(radz);
        int index = 0;
        int c = 0;
        int sizex = stack.getWidth();
        int sizey = stack.getHeight();
        int sizez = stack.getSize();
        for (int k = z - vz; k <= z + vz; k++) {
            for (int j = y - vy; j <= y + vy; j++) {
                for (int i = x - vx; i <= x + vx; i++) {
					if (ker[c]>0 && i>=0 && j>=0 && k>=0 && i<sizex && j<sizey && k<sizez) {
						pix.putValue(index, (float)stack.getVoxel(i, j, k));
						index++;
					}
                    c++;
                }
            }
        }
        pix.setSize(index);
        return pix;
    }
    
 }
