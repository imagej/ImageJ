package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.util.Tools;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/** This plugin implements ImageJ's Resize command. */
public class Resizer implements PlugInFilter, TextListener, ItemListener  {
	ImagePlus imp;
	private boolean crop;
    private static int newWidth;
    private static int newHeight;
    private int newDepth;
    private static boolean constrain = true;
	private static int interpolationMethod = ImageProcessor.BILINEAR;
	private String[] methods = ImageProcessor.getInterpolationMethods();
    private Vector fields, checkboxes;
	private double origWidth, origHeight;
	private boolean sizeToHeight;
 
	public int setup(String arg, ImagePlus imp) {
		crop = arg.equals("crop");
		this.imp = imp;
		IJ.register(Resizer.class);
		if (crop)
			return DOES_ALL+ROI_REQUIRED+NO_CHANGES;
		else
			return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isLine()) {
			IJ.error("The Crop and Adjust->Size commands\ndo not work with line selections.");
			return;
		}
		Rectangle r = ip.getRoi();
		origWidth = r.width;;
		origHeight = r.height;
		sizeToHeight=false;
	    boolean restoreRoi = crop && roi!=null && roi.getType()!=Roi.RECTANGLE;
		if (roi!=null) {
			Rectangle b = roi.getBounds();
			int w = ip.getWidth();
			int h = ip.getHeight();
			if (b.x<0 || b.y<0 || b.x+b.width>w || b.y+b.height>h) {
				ShapeRoi shape1 = new ShapeRoi(roi);
				ShapeRoi shape2 = new ShapeRoi(new Roi(0, 0, w, h));
				roi = shape2.and(shape1);
				if (restoreRoi) imp.setRoi(roi);
			}
		}
		int stackDepth= imp.getStackSize();
		if (crop) {
			Rectangle bounds = roi.getBounds();
			newWidth = bounds.width;
			newHeight = bounds.height;
			interpolationMethod = ImageProcessor.NONE;
		} else {
			if (newWidth==0 || newHeight==0) {
				newWidth = (int)origWidth/2;
				newHeight = (int)origHeight/2;
			}
			if (constrain) newHeight = (int)(newWidth*(origHeight/origWidth));
			if (stackDepth>1) {
				newWidth = (int)origWidth;
				newHeight = (int)origHeight;
			}
			GenericDialog gd = new GenericDialog("Resize", IJ.getInstance());
			gd.addNumericField("Width (pixels):", newWidth, 0);
			gd.addNumericField("Height (pixels):", newHeight, 0);
			if (stackDepth>1) 
				gd.addNumericField("Depth (images):", stackDepth, 0);
			gd.addCheckbox("Constrain Aspect Ratio", constrain);
			gd.addChoice("Interpolation:", methods, methods[interpolationMethod]);
			gd.addMessage("NOTE: Undo is not available");
			fields = gd.getNumericFields();
			for (int i=0; i<2; i++)
				((TextField)fields.elementAt(i)).addTextListener(this);
			checkboxes = gd.getCheckboxes();
			((Checkbox)checkboxes.elementAt(0)).addItemListener(this);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			newWidth = (int)gd.getNextNumber();
			newHeight = (int)gd.getNextNumber();
			if (stackDepth>1) 
				newDepth = (int)gd.getNextNumber();
			if (gd.invalidNumber()) {
				IJ.error("Width or height are invalid.");
				return;
			}
			constrain = gd.getNextBoolean();
			interpolationMethod = gd.getNextChoiceIndex();
			if (constrain && newWidth==0)
				sizeToHeight = true;
			if (newWidth<=0.0 && !constrain)  newWidth = 50;
			if (newHeight<=0.0) newHeight = 50;
		}
		
		if (!crop && constrain) {
			if (sizeToHeight)
				newWidth = (int)(newHeight*(origWidth/origHeight));
			else
				newHeight = (int)(newWidth*(origHeight/origWidth));
		}
		if (ip.getWidth()==1 || ip.getHeight()==1)
			ip.setInterpolationMethod(ImageProcessor.NONE);
		else
			ip.setInterpolationMethod(interpolationMethod);
    	
		if (roi!=null || newWidth!=origWidth || newHeight!=origHeight) {
			try {
				StackProcessor sp = new StackProcessor(imp.getStack(), ip);
				ImageStack s2 = sp.resize(newWidth, newHeight);
				int newSize = s2.getSize();
				if (s2.getWidth()>0 && newSize>0) {
					if (restoreRoi)
						imp.killRoi();
					//imp.hide();
					Calibration cal = imp.getCalibration();
					if (cal.scaled()) {
						cal.pixelWidth *= origWidth/newWidth;
						cal.pixelHeight *= origHeight/newHeight;
						imp.setCalibration(cal);
					}
					imp.setStack(null, s2);
					if (restoreRoi && roi!=null) {
						roi.setLocation(0, 0);
						imp.setRoi(roi);
						imp.draw();
					}
				}
				if (stackDepth>1 && newSize<stackDepth)
					IJ.error("ImageJ ran out of memory causing \nthe last "+(stackDepth-newSize)+" slices to be lost.");
			} catch(OutOfMemoryError o) {
				IJ.outOfMemory("Resize");
			}
			imp.changes = true;
		}
		if (newDepth>0 && newDepth!=stackDepth) {
			if (imp.isHyperStack()) {
				IJ.error("ImageJ is not yet able to adjust hyperstack depths.");
				return;
			}
			int bitDepth = imp.getBitDepth();
			if (bitDepth==32) {
				IJ.error("ImageJ is not yet able to adjust depth of 32-bit stacks.");
				return;
			}
			ImagePlus imp2 = null;
			if (newDepth<=stackDepth/2 && interpolationMethod==ImageProcessor.NONE)
				imp2 = shrinkZ(imp, newDepth);
			else
				imp2 = resizeZ(imp, newDepth, interpolationMethod);
			double min = ip.getMin();
			double max = ip.getMax();
			imp2.changes = true;
			if (imp2!=null) imp.setStack(null, imp2.getStack());
			Calibration cal = imp.getCalibration();
			if (cal.scaled()) cal.pixelDepth *= (double)stackDepth/newDepth;
			if (bitDepth==16) {
				imp.getProcessor().setMinAndMax(min, max);
				imp.updateAndDraw();
			}
			imp.setTitle(imp.getTitle());
		}
	}

	ImagePlus shrinkZ(ImagePlus imp, int newDepth) {
		ImageStack stack = imp.getStack();
		int factor = imp.getStackSize()/newDepth;
		boolean virtual = stack.isVirtual();
		int n = stack.getSize();
		ImageStack stack2 = new ImageStack(stack.getWidth(), stack.getHeight());
		for (int i=1; i<=n; i+=factor) {
			if (virtual) IJ.showProgress(i, n);
			stack2.addSlice(stack.getSliceLabel(i), stack.getProcessor(i));
		}
		return new ImagePlus("", stack2);
	}

	ImagePlus resizeZ(ImagePlus imp, int newDepth, int interpolationMethod) {
		ImageStack stack1 = imp.getStack();
		int width = stack1.getWidth();
		int height = stack1.getHeight();
		int depth = stack1.getSize();
		int bitDepth = imp.getBitDepth();
		ImagePlus imp2 = IJ.createImage("", bitDepth+"-bit", width, height, newDepth);
		if (imp2==null) return null;
		ImageStack stack2 = imp2.getStack();
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor xzPlane1 = ip.createProcessor(width, depth);
		xzPlane1.setInterpolationMethod(interpolationMethod);
		ImageProcessor xzPlane2;		
		int[] line = new int[width];
		IJ.showStatus("Z Scaling...");
		for (int y=0; y<height; y++) {
			IJ.showProgress(y, height-1);
			for (int z=0; z<depth; z++) {
				switch (bitDepth) {
					case 8: getByteRow(stack1, y, z, width, line); break;
					case 16: getShortRow(stack1, y, z, width, line); break;
					case 24: getRGBRow(stack1, y, z, width, line); break;
				}
				xzPlane1.putRow(0, z, line, width);
			}
			//if (y==r.y) new ImagePlus("xzPlane", xzPlane1).show();
			xzPlane2 = xzPlane1.resize(width, newDepth);
			for (int z=0; z<newDepth; z++) {
				xzPlane2.getRow(0, z, line, width);
				switch (bitDepth) {
					case 8: putByteRow(stack2, y, z, width, line); break;
					case 16: putShortRow(stack2, y, z, width, line); break;
					case 24: putRGBRow(stack2, y, z, width, line); break;
				}
			}
		}
		return imp2;
	}

	private void getByteRow(ImageStack stack, int y, int z, int width, int[] line) {
		byte[] pixels = (byte[])stack.getPixels(z+1);
		int j = y*width;
		for (int i=0; i<width; i++)
			line[i] = pixels[j++]&255;
	}

	private void getShortRow(ImageStack stack, int y, int z, int width, int[] line) {
		short[] pixels = (short[])stack.getPixels(z+1);
		int j = y*width;
		for (int i=0; i<width; i++)
			line[i] = pixels[j++]&0xffff;
	}

	private void putByteRow(ImageStack stack, int y, int z, int width, int[] line) {
		byte[] pixels = (byte[])stack.getPixels(z+1);
		int j = y*width;
		for (int i=0; i<width; i++)
			pixels[j++] = (byte)line[i];
	}

	private void putShortRow(ImageStack stack, int y, int z, int width, int[] line) {
		short[] pixels = (short[])stack.getPixels(z+1);
		int j = y*width;
		for (int i=0; i<width; i++)
			pixels[j++] = (short)line[i];
	}

	private void getRGBRow(ImageStack stack, int y, int z, int width, int[] line) {
		int[] pixels = (int[])stack.getPixels(z+1);
		int j = y*width;
		for (int i=0; i<width; i++)
			line[i] = pixels[j++];
	}

	private void putRGBRow(ImageStack stack, int y, int z, int width, int[] line) {
		int[] pixels = (int[])stack.getPixels(z+1);
		int j = y*width;
		for (int i=0; i<width; i++)
			pixels[j++] = line[i];
	}

    public void textValueChanged(TextEvent e) {
    	TextField widthField = (TextField)fields.elementAt(0);
    	TextField heightField = (TextField)fields.elementAt(1);
        int width = (int)Tools.parseDouble(widthField.getText(),-99);
        int height = (int)Tools.parseDouble(heightField.getText(),-99);
        if (width==-99 || height==-99)
        	return;
        if (constrain) {
        	if (width!=newWidth) {
         		sizeToHeight = false;
        		newWidth = width;
				updateFields();
         	} else if (height!=newHeight) {
         		sizeToHeight = true;
        		newHeight = height;
				updateFields();
			}
        }
    }
    
    void updateFields() {
		if (sizeToHeight) {
			newWidth = (int)(newHeight*(origWidth/origHeight));
			TextField widthField = (TextField)fields.elementAt(0);
			widthField.setText(""+newWidth);
		} else {
			newHeight = (int)(newWidth*(origHeight/origWidth));
			TextField heightField = (TextField)fields.elementAt(1);
			heightField.setText(""+newHeight);
		}
   }

	public void itemStateChanged(ItemEvent e) {
		Checkbox cb = (Checkbox)checkboxes.elementAt(0);
        boolean newConstrain = cb.getState();
        if (newConstrain && newConstrain!=constrain)
        	updateFields();
        constrain = newConstrain;
	}

}