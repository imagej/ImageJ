package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.Calibration;
import ij.macro.Interpreter;


/** Implements the AddSlice, DeleteSlice and "Convert Windows to Stack" commands. */
public class StackEditor implements PlugIn {
	String arg;
	ImagePlus imp;
	int nSlices, width, height;

	/** 'arg' must be "add", "delete" or "convert". */
	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
    	nSlices = imp.getStackSize();
    	width = imp.getWidth();
    	height = imp.getHeight();
    	
    	if (arg.equals("tostack"))
    		{convertImagesToStack(); return;}
    	if (arg.equals("add"))
    		{addSlice(); return;}
    		
		if (nSlices<2)
			{IJ.error("Stack requred"); return;}
    	if (arg.equals("delete"))
    		deleteSlice();
    	else if (arg.equals("toimages"))
    		convertStackToImages(imp);
	}

    void addSlice() {
		if (!imp.lock())
			return;
		ImageStack stack = imp.getStack();
		if (stack.getSize()==1) stack.setSliceLabel(null, 1);
		ImageProcessor ip = imp.getProcessor();
		int n = imp.getCurrentSlice();
		if (IJ.altKeyDown())
			n--; // insert in front of current slice
		stack.addSlice(null, ip.createProcessor(width, height), n);
		imp.setStack(null, stack);
		imp.setSlice(n+1);
		imp.unlock();
	}

	void deleteSlice() {
		if (!imp.lock())
			return;
		ImageStack stack = imp.getStack();
		int n = imp.getCurrentSlice();
 		stack.deleteSlice(n);
		imp.setStack(null, stack);
 		if (n--<1) n = 1;
		imp.setSlice(n);
		imp.unlock();
	}

	public void convertImagesToStack() {
		int[] wList = WindowManager.getIDList();
		if (wList==null) {
			IJ.error("No images are open.");
			return;
		}

		int count = 0;
		ImagePlus[] image = new ImagePlus[wList.length];
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp = WindowManager.getImage(wList[i]);
			if (imp.getStackSize()==1)
				image[count++] = imp;
		}		
		if (count<2) {
			IJ.error("There must be at least two open images.");
			return;
		}

		Calibration cal2 = image[0].getCalibration();
		for (int i=0; i<(count-1); i++) {
			if (image[i].getType()!=image[i+1].getType()) {
				IJ.error("All open images must be the same type.");
				return;
			}
			if (image[i].getWidth()!=image[i+1].getWidth()
			|| image[i].getHeight()!=image[i+1].getHeight()) {
				IJ.error("All open images must be the same size.");
				return;
			}
			Calibration cal = image[i].getCalibration();
			if (!image[i].getCalibration().equals(cal2))
				cal2 = null;
		}
		
		int width = image[0].getWidth();
		int height = image[0].getHeight();
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		ImageStack stack = new ImageStack(width, height);
		for (int i=0; i<count; i++) {
			ImageProcessor ip = image[i].getProcessor();
			if (ip.getMin()<min) min = ip.getMin();
			if (ip.getMax()>max) max = ip.getMax();
            String label = image[i].getTitle();
            String info = (String)image[i].getProperty("Info");
            if (info!=null) label += "\n" + info;
            stack.addSlice(label, ip);
			image[i].changes = false;
			image[i].close();
		}
		ImagePlus imp = new ImagePlus("Stack", stack);
		if (imp.getType()==ImagePlus.GRAY16 || imp.getType()==ImagePlus.GRAY32)
			imp.getProcessor().setMinAndMax(min, max);
		if (cal2!=null)
			imp.setCalibration(cal2);
		imp.show();
	}

	public void convertStackToImages(ImagePlus imp) {
		if (!imp.lock())
			return;
		ImageStack stack = imp.getStack();
		int size = stack.getSize();
		if (size>30 && !IJ.macroRunning()) {
			boolean ok = IJ.showMessageWithCancel("Convert to Images?",
			"Are you sure you want to convert this\nstack to "
			+size+" separate windows?");
			if (!ok)
				{imp.unlock(); return;}
		}
		Calibration cal = imp.getCalibration();
		for (int i=1; i<=size; i++) {
			String label = stack.getShortSliceLabel(i);
			String title = label!=null&&!label.equals("")?label:getDigits(i);
			ImagePlus imp2 = new ImagePlus(title, stack.getProcessor(i));
			imp2.setCalibration(cal);
			imp2.show();
		}
		imp.changes = false;
		ImageWindow win = imp.getWindow();
		if (win!=null)
			win.close();
		else if (Interpreter.isBatchMode())
			Interpreter.removeBatchModeImage(imp);
		imp.unlock();
	}

	String getDigits(int n) {
		String digits = "00000000"+n;
		return digits.substring(digits.length()-4,digits.length());
	}

}

