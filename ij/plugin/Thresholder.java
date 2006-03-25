package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.frame.Recorder;
import ij.plugin.filter.PlugInFilter;
import java.awt.*;

/** This plugin implements the Proxess/Binary/Threshold command. */
public class Thresholder implements PlugIn, Measurements {
	
	private int slice;
	private double minThreshold;
	private double maxThreshold;
	boolean autoThreshold;
	boolean skipDialog;
	ImageStack stack1;
	static boolean fill1 = true;
	static boolean fill2 = true;
	static boolean useBW = true;


	public void run(String arg) {
		skipDialog = arg.equals("skip");
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
		if (imp.getStackSize()==1) {
			Undo.setup(Undo.COMPOUND_FILTER, imp);
			applyThreshold(imp);
			Undo.setup(Undo.COMPOUND_FILTER_DONE, imp);
		} else {
			Undo.reset();
			applyThreshold(imp);
		}
	}

	void applyThreshold(ImagePlus imp) {
		if (!imp.lock())
			return;
		imp.killRoi();
		ImageProcessor ip = imp.getProcessor();
		double saveMinThreshold = ip.getMinThreshold();
		double saveMaxThreshold = ip.getMaxThreshold();
		double saveMin = ip.getMin();
		double saveMax = ip.getMax();
		if (ip instanceof ByteProcessor)
			{saveMin =0; saveMax = 255;}
		autoThreshold = saveMinThreshold==ImageProcessor.NO_THRESHOLD;
					
		boolean useBlackAndWhite = true;
		if (skipDialog)
			fill1 = fill2 = useBlackAndWhite = true;
		else if (!autoThreshold) {
			GenericDialog gd = new GenericDialog("Apply Lut");
			gd.addCheckbox("Thresholded pixels to foreground color", fill1);
			gd.addCheckbox("Remaining pixels to background color", fill2);
			gd.addMessage("");
			gd.addCheckbox("Black foreground, white background", useBW);
			gd.showDialog();
			if (gd.wasCanceled())
				{imp.unlock(); return;}
			fill1 = gd.getNextBoolean();
			fill2 = gd.getNextBoolean();
			useBW = useBlackAndWhite = gd.getNextBoolean();
		} else
			fill1 = fill2 = true;

		if (!(imp.getType()==ImagePlus.GRAY8))
			convertToByte(imp);
		ip = imp.getProcessor();
		if (autoThreshold)
			autoThreshold(imp);
		else {
			if (Recorder.record)
				Recorder.record("setThreshold", (int)saveMinThreshold, (int)saveMaxThreshold);
 			minThreshold = ((saveMinThreshold-saveMin)/(saveMax-saveMin))*255.0;
 			maxThreshold = ((saveMaxThreshold-saveMin)/(saveMax-saveMin))*255.0;
		}

		int fcolor, bcolor;
		ip.resetThreshold();
		int savePixel = ip.getPixel(0,0);
		if (useBlackAndWhite)
 			ip.setColor(Color.black);
 		else
 			ip.setColor(Toolbar.getForegroundColor());
		ip.drawPixel(0,0);
		fcolor = ip.getPixel(0,0);
		if (useBlackAndWhite)
 			ip.setColor(Color.white);
 		else
 			ip.setColor(Toolbar.getBackgroundColor());
		ip.drawPixel(0,0);
		bcolor = ip.getPixel(0,0);
		ip.setColor(Toolbar.getForegroundColor());
		ip.putPixel(0,0,savePixel);

		int[] lut = new int[256];
		for (int i=0; i<256; i++) {
			if (i>=minThreshold && i<=maxThreshold)
				lut[i] = fill1?fcolor:(byte)i;
			else {
				lut[i] = fill2?bcolor:(byte)i;
			}
		}
		int result = IJ.setupDialog(imp, 0);
		if (result==PlugInFilter.DONE) {
			if (stack1!=null)
				imp.setStack(null, stack1);
			imp.unlock();
			return;
		}
		if (result==PlugInFilter.DOES_STACKS)
			new StackProcessor(imp.getStack(), ip).applyTable(lut);
		else
			ip.applyTable(lut);
		if (fill1=true && fill2==true && ((fcolor==0&&bcolor==255)||(fcolor==255&&bcolor==0)))
			imp.getProcessor().setThreshold(fcolor, fcolor, ImageProcessor.NO_LUT_UPDATE);
		imp.updateAndDraw();
		imp.unlock();
	}

	void convertToByte(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		double min = ip.getMin();
		double max = ip.getMax();
		int currentSlice =  imp.getCurrentSlice();
		stack1 = imp.getStack();
		ImageStack stack2 = imp.createEmptyStack();
		int nSlices = imp.getStackSize();
		String label;
		for(int i=1; i<=nSlices; i++) {
			label = stack1.getSliceLabel(i);
			ip = stack1.getProcessor(i);
			ip.setMinAndMax(min, max);
			stack2.addSlice(label, ip.convertToByte(true));
		}
		imp.setStack(null, stack2);
		imp.setSlice(currentSlice);
		imp.setCalibration(imp.getCalibration()); //update calibration
	}
	
	void autoThreshold(ImagePlus imp) {
		ImageStatistics stats = imp.getStatistics(MIN_MAX+MODE);
		ImageProcessor ip = imp.getProcessor();
		int threshold = ((ByteProcessor)ip).getAutoThreshold();
		if ((stats.max-stats.mode)<(stats.mode-stats.min)) {
			minThreshold = stats.min;
			maxThreshold = threshold;
		} else {
			minThreshold = threshold;
			maxThreshold = stats.max;
		}
 	}

}
