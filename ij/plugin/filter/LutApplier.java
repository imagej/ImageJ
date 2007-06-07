package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.util.*;
import java.awt.*;
import java.util.*;

/** This plugin implements the Image/Lookup Tables/Apply LUT command. */
public class LutApplier implements PlugInFilter {
	ImagePlus imp;
	int min, max;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_8G+DOES_8C;
	}

	public void run(ImageProcessor ip) {
		apply(imp, ip);
	}
	
	void apply(ImagePlus imp, ImageProcessor ip) {
		min = (int)ip.getMin();
		max = (int)ip.getMax();
		if (min==0 && max==255) {
				IJ.showMessage("Apply LUT", "The display range must first be updated \nusing Image->Adjust->Brightness/Contrast.");
				return;
		}
		ip.resetMinAndMax();
		if (imp.getType()==ImagePlus.COLOR_RGB) {
			if (imp.getStackSize()>1)
				applyRGBStack(imp);
			else
				ip.snapshot();
			return;
		}
		int[] table = new int[256];
		for (int i=0; i<256; i++) {
			if (i<=min)
				table[i] = 0;
			else if (i>=max)
				table[i] = 255;
			else
				table[i] = (int)(((double)(i-min)/(max-min))*255);
		}
		if (imp.getStackSize()>1) {
			ImageStack stack = imp.getStack();
			
			int flags = IJ.setupDialog(imp, 0);
			if (flags==PlugInFilter.DONE)
				{ip.setMinAndMax(min, max); return;}
			if (flags==PlugInFilter.DOES_STACKS) {
				new StackProcessor(stack, ip).applyTable(table);
				Undo.reset();
			} else
				ip.applyTable(table);
		} else
			ip.applyTable(table);
	}

	void applyRGBStack(ImagePlus imp) {
		int current = imp.getCurrentSlice();
		int n = imp.getStackSize();
		if (!IJ.showMessageWithCancel("Update Entire Stack?",
		"Apply brightness and contrast settings\n"+
		"to all "+n+" slices in the stack?\n \n"+
		"NOTE: There is no Undo for this operation."))
			return;
		for (int i=1; i<=n; i++) {
			if (i!=current) {
				imp.setSlice(i);
				ImageProcessor ip = imp.getProcessor();
				ip.setMinAndMax(min, max);
				IJ.showProgress((double)i/n);
			}
		}
		imp.setSlice(current);
	}
	
}
