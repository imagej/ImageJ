package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.util.*;
import ij.plugin.frame.ContrastAdjuster;
import java.awt.*;
import java.util.*;

/** This plugin implements the Image/Lookup Tables/Apply LUT command. */
public class LutApplier implements PlugInFilter {
	ImagePlus imp;
	int min, max;
	boolean canceled;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		int baseOptions = DOES_8G+DOES_8C+DOES_16+DOES_RGB;
		return baseOptions;
	}

	public void run(ImageProcessor ip) {
		apply(imp, ip);
	}
	
	void apply(ImagePlus imp, ImageProcessor ip) {
        if (ip.getMinThreshold()!=ImageProcessor.NO_THRESHOLD) {
            imp.unlock();
			IJ.runPlugIn("ij.plugin.Thresholder", "skip");
            return;
        }
		min = (int)imp.getDisplayRangeMin();
		max = (int)imp.getDisplayRangeMax();
		int depth = imp.getBitDepth();
		if (!IJ.isMacro() && (depth==8||depth==24) && min==0 && max==255) {
				IJ.error("Apply LUT", "The display range must first be updated\n"
                +"using Image>Adjust>Brightness/Contrast\n"
                +"or threshold levels defined using\n"
                +"Image>Adjust>Threshold.");
				return;
		}
		if (imp.getType()==ImagePlus.COLOR_RGB) {
			if (imp.getStackSize()>1)
				applyRGBStack(imp);
			else {
				ip.reset();
				Undo.setup(Undo.TRANSFORM, imp);
				ip.setMinAndMax(min, max);
			}
			((ColorProcessor)ip).caSnapshot(false);
			resetContrastAdjuster();
			return;
		}
		ip.resetMinAndMax();
		int range = 256;
		if (depth==16) {
			range = 65536;
			int defaultRange = imp.getDefault16bitRange();
			if (defaultRange>0)
				range = (int)Math.pow(2,defaultRange)-1;
		}
		int tableSize = depth==16?65536:256;
		int[] table = new int[tableSize];
		for (int i=0; i<tableSize; i++) {
			if (i<=min)
				table[i] = 0;
			else if (i>=max)
				table[i] = range-1;
			else
				table[i] = (int)(((double)(i-min)/(max-min))*range);
		}
		ImageProcessor mask = imp.getMask();
		if (imp.getStackSize()>1) {
			ImageStack stack = imp.getStack();
			int flags = IJ.setupDialog(imp, 0);
			if (flags==PlugInFilter.DONE) {
				ip.setMinAndMax(min, max);
				return;
			}
			if (flags==PlugInFilter.DOES_STACKS) {
				int current = imp.getCurrentSlice();
				for (int i=1; i<=imp.getStackSize(); i++) {
					imp.setSlice(i);
					ip = imp.getProcessor();
					if (mask!=null) ip.snapshot();
					ip.applyTable(table);
					ip.reset(mask);
				}
				imp.setSlice(current);
				Undo.reset();
			} else {
				ip.applyTable(table);
				ip.reset(mask);
			}
		} else {
			ip.applyTable(table);
			ip.reset(mask);
		}
		if (depth==16)
			imp.setDisplayRange(0,range-1);
		resetContrastAdjuster();
	}
	
	private void resetContrastAdjuster() {
		ContrastAdjuster.update();
	}

	void applyRGBStack(ImagePlus imp) {
		int current = imp.getCurrentSlice();
		int n = imp.getStackSize();
		if (!IJ.showMessageWithCancel("Update Entire Stack?",
		"Apply brightness and contrast settings\n"+
		"to all "+n+" slices in the stack?\n \n"+
		"NOTE: There is no Undo for this operation.")) {
			canceled = true;
			return;
		}
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
