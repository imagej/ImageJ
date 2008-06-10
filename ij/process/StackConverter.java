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
		ImageProcessor ip = imp.getProcessor();
		boolean colorLut = ip.isColorLut();
		boolean pseudoColorLut = colorLut && ip.isPseudoColorLut();
		if (type==ImagePlus.GRAY8 && pseudoColorLut) {
			boolean invertedLut = ip.isInvertedLut();
			ip.setColorModel(LookUpTable.createGrayscaleColorModel(invertedLut));
			stack1.setColorModel(ip.getColorModel());
	    	imp.updateAndDraw();
			return;
		}
		if (type==ImagePlus.COLOR_RGB || type==ImagePlus.COLOR_256 || colorLut) {
			convertRGBToGray8();
			imp.setSlice(currentSlice);
			return;
		}
		
		ImageStack stack2 = new ImageStack(width, height);
		Image img;
		String label;
		double min = ip.getMin();
		double max = ip.getMax();
	    int inc = nSlices/20;
	    if (inc<1) inc = 1;
		for(int i=1; i<=nSlices; i++) {
			label = stack1.getSliceLabel(1);
			ip = stack1.getProcessor(1);
			stack1.deleteSlice(1);
			ip.setMinAndMax(min, max);
			boolean scale = ImageConverter.getDoScaling();
			stack2.addSlice(label, ip.convertToByte(scale));
			if ((i%inc)==0) {
				IJ.showProgress((double)i/nSlices);
				IJ.showStatus("Converting to 8-bits: "+i+"/"+nSlices);
			}
		}
		imp.setStack(null, stack2);
		imp.setSlice(currentSlice);
		imp.setCalibration(imp.getCalibration()); //update calibration
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
			if (ip instanceof ByteProcessor)
				ip = new ColorProcessor(ip.createImage());
			boolean scale = ImageConverter.getDoScaling();
			stack2.addSlice(label, ip.convertToByte(scale));
			if ((i%inc)==0) {
				IJ.showProgress((double)i/nSlices);
				IJ.showStatus("Converting to 8-bits: "+i+"/"+nSlices);
			}
		}
		imp.setStack(null, stack2);
		IJ.showProgress(1.0);
	}

	/** Converts this Stack to 16-bit grayscale. */
	public void convertToGray16() {
		int type = imp.getType();
		if (type==ImagePlus.GRAY16)
			return;
		if (!(type==ImagePlus.GRAY8 || type==ImagePlus.GRAY32))
			throw new IllegalArgumentException("Unsupported conversion");
		ImageStack stack1 = imp.getStack();
		ImageStack stack2 = new ImageStack(width, height);
		String label;
	    int inc = nSlices/20;
	    if (inc<1) inc = 1;
	    boolean scale = type==ImagePlus.GRAY32 && ImageConverter.getDoScaling();
	    ImageProcessor ip1, ip2;
		for(int i=1; i<=nSlices; i++) {
			label = stack1.getSliceLabel(1);
			ip1 = stack1.getProcessor(1);
			ip2 = ip1.convertToShort(scale);
			stack1.deleteSlice(1);
			stack2.addSlice(label, ip2);
			if ((i%inc)==0) {
				IJ.showProgress((double)i/nSlices);
				IJ.showStatus("Converting to 16-bits: "+i+"/"+nSlices);
			}
		}
		IJ.showProgress(1.0);
		imp.setStack(null, stack2);
	}

	/** Converts this Stack to 32-bit (float) grayscale. */
	public void convertToGray32() {
		int type = imp.getType();
		if (type==ImagePlus.GRAY32)
			return;
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
			stack2.addSlice(label, ip2);
			if ((i%inc)==0) {
				IJ.showProgress((double)i/nSlices);
				IJ.showStatus("Converting to 32-bits: "+i+"/"+nSlices);
			}
		}
		IJ.showProgress(1.0);
		imp.setStack(null, stack2);
		imp.setCalibration(imp.getCalibration()); //update calibration
	}

	/** Converts the Stack to RGB. */
	public void convertToRGB() {
		if (imp.isComposite())
			throw new IllegalArgumentException("Use Image>Color>Stack to RGB");
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
			stack2.addSlice(label, ip2);
			if ((i%inc)==0) {
				IJ.showProgress((double)i/nSlices);
				IJ.showStatus("Converting to RGB: "+i+"/"+nSlices);
			}
		}
		IJ.showProgress(1.0);
		imp.setStack(null, stack2);
		imp.setCalibration(imp.getCalibration()); //update calibration
	}

	/** Converts the stack to 8-bits indexed color. 'nColors' must
		be greater than 1 and less than or equal to 256. */
	public void convertToIndexedColor(int nColors) {
		if (type!=ImagePlus.COLOR_RGB)
			throw new IllegalArgumentException("RGB stack required");
		ImageStack stack = imp.getStack();
		int size = stack.getSize();
		ImageProcessor montage = new ColorProcessor(width*size, height);
        for (int i=0; i<size; i++)
            montage.insert(stack.getProcessor(i+1), i*width, 0);
        MedianCut mc = new MedianCut((ColorProcessor)montage);
        montage = mc.convertToByte(nColors);
        ImageStack stack2 = new ImageStack(width, height);
        for (int i=0; i<size; i++) {
            montage.setRoi(i*width, 0, width, height);
            stack2.addSlice(null, montage.crop());
        }
		imp.setStack(null, stack2);
	}

}
