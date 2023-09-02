package ij.gui;
import ij.*;
import ij.process.*;
import java.util.*;
import java.io.*;

/** This is a virtual stack of frozen plots. */
public class PlotVirtualStack extends VirtualStack {
	private Vector plots = new Vector(50);
	private int bitDepth = 8;
	
	public PlotVirtualStack(int width, int height) {
		super(width, height);
	}
	
	/** Adds a plot to the end of the stack. */
	public void addPlot(Plot plot) {
		plots.add(plot.toByteArray());
		if (plot.isColored())
			bitDepth = 24;
	}
	   
   /** Returns the pixel array for the specified slice, where {@literal 1<=n<=nslices}. */
	public Object getPixels(int n) {
		ImageProcessor ip = getProcessor(n);
		if (ip!=null)
			return ip.getPixels();
		else
			return null;
	}		
	
	/** Returns an ImageProcessor for the specified slice,
		where {@literal 1<=n<=nslices}. Returns null if the stack is empty. */
	public ImageProcessor getProcessor(int n) {
		byte[] bytes = (byte[])plots.get(n-1);
		if (bytes!=null) {
			try {
				Plot plot = new Plot(null, new ByteArrayInputStream(bytes));
				ImageProcessor ip = plot.getProcessor();
				if (bitDepth==24)
					ip = ip.convertToRGB();
				else if (bitDepth==8)
					ip =  ip.convertToByte(false);
				ip.setSliceNumber(n);
				return ip;
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
		
	/** Returns either 24 (RGB) or 8 (grayscale). */
	public int getBitDepth() {
		return bitDepth;
	}
		
	public void setBitDepth(int bitDepth) {
		this.bitDepth = bitDepth;
	}

	public String getSliceLabel(int n) {
		return null;
	}

	public void setPixels(Object pixels, int n) {
	}
	
	/** Deletes the specified slice, where {@literal 1<=n<=nslices}. */
	public void deleteSlice(int n) {
		if (n<1 || n>plots.size())
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (plots.size()<1)
			return;			
		plots.remove(n-1);
	}


} // PlotVirtualStack

