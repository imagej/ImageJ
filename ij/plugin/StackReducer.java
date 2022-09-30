package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.Calibration;
import java.awt.*;

/** This plugin implements the Image/Stacks/Tools/Reduce command. */
public class StackReducer implements PlugIn {
	ImagePlus imp;
	private static int factor = 2;
	private boolean hyperstack, reduceSlices;

	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
		ImageStack stack = imp.getStack();
		int size = stack.size();
		if (size==1 || (imp.getNChannels()==size&&imp.isComposite()))
			{IJ.error("Stack or hyperstack required"); return;}
		if (!showDialog(stack))
			return;
		if (!hyperstack && (stack instanceof VirtualStack)) {
			int previousSize = stack.size();
			((VirtualStack)stack).reduce(factor);
			if (stack.size()<previousSize)
				return;
		}
		if (hyperstack)
			reduceHyperstack(imp, factor, reduceSlices);
		else
			reduceStack(imp, factor);
	}

	public boolean showDialog(ImageStack stack) {
		hyperstack = imp.isHyperStack();
		boolean showCheckbox = false;
		if (hyperstack && imp.getNSlices()>1 && imp.getNFrames()>1)
			showCheckbox = true;
		else if (hyperstack && imp.getNSlices()>1)
			reduceSlices = true;
		int n = stack.size();
		GenericDialog gd = new GenericDialog("Reduce Size");
		gd.addNumericField("Reduction Factor:", factor, 0);
		if (showCheckbox)
			gd.addCheckbox("Reduce in Z-Dimension", false);
		gd.showDialog();
		if (gd.wasCanceled()) return false;
		factor = (int) gd.getNextNumber();
		if (showCheckbox)
			reduceSlices = gd.getNextBoolean();
		return true;
	}
	
	public void reduceStack(ImagePlus imp, int factor) {
		ImageStack stack = imp.getStack();
		boolean virtual = stack.isVirtual();
		int n = stack.size();
		ImageStack stack2 = new ImageStack(stack.getWidth(), stack.getHeight());
		for (int i=1; i<=n; i+=factor) {
			if (virtual) IJ.showProgress(i, n);
			stack2.addSlice(stack.getSliceLabel(i), stack.getProcessor(i));
		}
		imp.setStack(null, stack2);
		if (virtual) {
			IJ.showProgress(1.0);
			imp.setTitle(imp.getTitle());
		}
		Calibration cal = imp.getCalibration();
		if (cal.scaled()) cal.pixelDepth *= factor;
	}
	
	public void reduceHyperstack(ImagePlus imp, int factor, boolean reduceSlices) {
		int channels = imp.getNChannels();
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		int zfactor = reduceSlices?factor:1;
		int tfactor = reduceSlices?1:factor;
		ImageStack stack = imp.getStack();
		ImageStack stack2 = new ImageStack(imp.getWidth(), imp.getHeight());
		boolean virtual = stack.isVirtual();
		int slices2 = slices/zfactor + ((slices%zfactor)!=0?1:0);
		int frames2 = frames/tfactor + ((frames%tfactor)!=0?1:0);
		int n = channels*slices2*frames2;
		int count = 1;
		for (int t=1; t<=frames; t+=tfactor) {
			for (int z=1; z<=slices; z+=zfactor) {
				for (int c=1; c<=channels; c++) {
					int i = imp.getStackIndex(c, z, t);
					IJ.showProgress(i, n);
					ImageProcessor ip = stack.getProcessor(imp.getStackIndex(c, z, t));
					//IJ.log(count++ +"  "+i+" "+c+" "+z+" "+t);
					stack2.addSlice(stack.getSliceLabel(i), ip);
				}
			}
		}
		imp.setStack(stack2, channels, slices2, frames2);
		Calibration cal = imp.getCalibration();
		if (cal.scaled()) cal.pixelDepth *= zfactor;
		if (virtual) imp.setTitle(imp.getTitle());
		IJ.showProgress(1.0);
	}
	
}
