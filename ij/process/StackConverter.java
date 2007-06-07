package ij.process;

import java.awt.*;
import java.awt.image.*;
import ij.*;
import ij.gui.*;
import ij.measure.*;

/** This class does stack type conversions. */
public class StackConverter {
	ImagePlus imp;
	int type, nSlices, width, height;

	public StackConverter(ImagePlus imp) {
		this.imp = imp;
		type = imp.getType();
		nSlices = imp.getStackSize();
		if (nSlices<2)
			throw new IllegalArgumentException("Stack required");
		width = imp.getWidth();
		height = imp.getHeight();
	}
	
	/** Converts this Stack to 8-bit grayscale. */
	public void convertToGray8() {
		ImageStack stack1 = imp.getStack();
		int currentSlice =  imp.getCurrentSlice();
		if (type==ImagePlus.COLOR_RGB || type==ImagePlus.COLOR_256) {
			convertRGBToGray8();
			imp.setSlice(currentSlice);
			return;
		}
		
		ImageStack stack2 = new ImageStack(width, height);
		ImageProcessor ip;
		Image img;
		String label;
		ip = imp.getProcessor();
		double min = ip.getMin();
		double max = ip.getMax();
	    int inc = nSlices/20;
	    if (inc<1) inc = 1;
		for(int i=1; i<=nSlices; i++) {
			label = stack1.getSliceLabel(1);
			ip = stack1.getProcessor(1);
			stack1.deleteSlice(1);
			System.gc();
			ip.setMinAndMax(min, max);
			boolean scale = ImageConverter.getDoScaling();
			stack2.addSlice(label, ip.convertToByte(scale));
			if ((i%inc)==0) IJ.showProgress((double)i/nSlices);
		}
		imp.setStack(null, stack2);
		imp.setSlice(currentSlice);
		imp.getCalibration().disableDensityCalibration();
		IJ.showProgress(1.0);
	}

	/** Converts an RGB or 8-bit color stack to 8-bit grayscale. */
	void convertRGBToGray8() {
		ImageStack stack1 = imp.getStack();
		ImageStack stack2 = new ImageStack(width, height);
		ImageProcessor ip;
		Image img;
		String label;
	    int inc = nSlices/20;
	    if (inc<1) inc = 1;
		for(int i=1; i<=nSlices; i++) {
			label = stack1.getSliceLabel(1);
			ip = stack1.getProcessor(1);
			stack1.deleteSlice(1);
			System.gc();
			if (ip instanceof ByteProcessor)
				ip = new ColorProcessor(ip.createImage());
			boolean scale = ImageConverter.getDoScaling();
			stack2.addSlice(label, ip.convertToByte(scale));
			if ((i%inc)==0) IJ.showProgress((double)i/nSlices);
		}
		imp.setStack(null, stack2);
		IJ.showProgress(1.0);
	}

	/** Converts this Stack to 32-bit (float) grayscale. */
	public void convertToGray32() {
		int type = imp.getType();
		if (!(type==ImagePlus.GRAY8||type==ImagePlus.GRAY16))
			throw new IllegalArgumentException("Unsupported conversion");
		ImageStack stack1 = imp.getStack();
		ImageStack stack2 = new ImageStack(width, height);
		String label;
	    int inc = nSlices/20;
	    if (inc<1) inc = 1;
	    ImageProcessor ip1, ip2;
	    Calibration cal = imp.getCalibration();
		for(int i=1; i<=nSlices; i++) {
			label = stack1.getSliceLabel(1);
			ip1 = stack1.getProcessor(1);
			ip1.setCalibrationTable(cal.getCTable());
			ip2 = ip1.convertToFloat();
			stack1.deleteSlice(1);
			System.gc();
			stack2.addSlice(label, ip2);
			if ((i%inc)==0) IJ.showProgress((double)i/nSlices);
		}
		IJ.showProgress(1.0);
		imp.setStack(null, stack2);
		cal.disableDensityCalibration();
	}

	/** Converts the Stack to RGB. */
	public void convertToRGB() {
		ImageStack stack1 = imp.getStack();
		ImageStack stack2 = new ImageStack(width, height);
		String label;
	    int inc = nSlices/20;
	    if (inc<1) inc = 1;
	    ImageProcessor ip1, ip2;
	    Calibration cal = imp.getCalibration();
		for(int i=1; i<=nSlices; i++) {
			label = stack1.getSliceLabel(i);
			ip1 = stack1.getProcessor(i);
			ip2 = ip1.convertToRGB();
			//stack1.deleteSlice(1);
			//System.gc();
			stack2.addSlice(label, ip2);
			if ((i%inc)==0) IJ.showProgress((double)i/nSlices);
		}
		IJ.showProgress(1.0);
		imp.setStack(null, stack2);
		cal.disableDensityCalibration();
	}
}
