package ij.util;
import ij.*;
import ij.process.*;
import ij.gui.Plot;
import java.util.Vector;
import java.io.ByteArrayInputStream;

/** This class represents a collection of plots. */
public class PlotVirtualStack extends VirtualStack {
	Vector plots = new Vector(50);
	
	public PlotVirtualStack(int width, int height) {
		super(width, height);
	}
	
	/** Adds a plot to the end of the stack. */
	public void addPlot(Plot plot) {
		plots.add(plot.toByteArray());
	}
	   
   /** Returns the pixel array for the specified slice, were 1<=n<=nslices. */
	public Object getPixels(int n) {
		ImageProcessor ip = getProcessor(n);
		if (ip!=null)
			return ip.getPixels();
		else
			return null;
	}		
	
	/** Returns an ImageProcessor for the specified slice,
		were 1<=n<=nslices. Returns null if the stack is empty. */
	public ImageProcessor getProcessor(int n) {
		byte[] bytes = (byte[])plots.get(n-1);
		if (bytes!=null) {
			try {
				Plot plot = new Plot(null, new ByteArrayInputStream(bytes));
				ImageProcessor ip = plot.getProcessor();
				return ip.convertToRGB();
			} catch (Exception e) {
				IJ.handleException(e);
			}
		}
		return null;
	}
	 
	 /** Returns the number of slices in this stack. */
	public int getSize() {
		return plots.size();
	}
		
	/** Always returns 24 (RGB). */
	public int getBitDepth() {
		return 24;
	}
		
	public String getSliceLabel(int n) {
		return null;
	}

	public void setPixels(Object pixels, int n) {
	}

} 

