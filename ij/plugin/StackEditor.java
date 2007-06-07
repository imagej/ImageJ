package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;

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
    	
    	if (arg.equals("add"))
    		addSlice();
    	else if (arg.equals("delete"))
    		deleteSlice();
    	else if (arg.equals("convert"))
    		convertImagesToStack();
	}

    void addSlice() {
		if (nSlices<2) {
			IJ.error("Stack requred");
			return;
		}
		if (!imp.lock())
			return;
		ImageStack stack = imp.getStack();
		ImageProcessor ip = imp.getProcessor();
		int n = imp.getCurrentSlice();
		stack.addSlice(null, ip.createProcessor(width, height), n);
		imp.setSlice(n+1);
		imp.repaintWindow();
		imp.unlock();
	}

	void deleteSlice() {
		if (nSlices<2) {
			IJ.error("Stack requred");
			return;
		}
		if (!imp.lock())
			return;
		ImageStack stack = imp.getStack();
		int n = imp.getCurrentSlice();
 		stack.deleteSlice(n);
		if (stack.getSize()==1) {
			imp.setProcessor(null, stack.getProcessor());
			new ImageWindow(imp);
		} else {
			imp.setStack(null, stack);
 			if (n--<1) n = 1;
			imp.setSlice(n);
			imp.repaintWindow();
		}
		imp.unlock();
	}

	public void convertImagesToStack() {
		int[] wList = WindowManager.getIDList();
		if (wList==null) {
			IJ.error("No images are open.");
			return;
		}
		if (wList.length<2) {
			IJ.error("There must be at least two open images.");
			return;
		}
		ImagePlus[] image = new ImagePlus[wList.length];
		for (int i=0; i<wList.length; i++) {
			image[i] = WindowManager.getImage(wList[i]);
			if (image[i].getStackSize()>1) {
				IJ.error("None of the open images can be a stack.");
				return;
			}
		}
		for (int i=0; i<(wList.length-1); i++) {
			if (image[i].getType()!=image[i+1].getType()) {
				IJ.error("All open images must be the same type.");
				return;
			}
			if (image[i].getWidth()!=image[i+1].getWidth()
			|| image[i].getHeight()!=image[i+1].getHeight()) {
				IJ.error("All open images must be the same size.");
				return;
			}
		}
		
		int width = image[0].getWidth();
		int height = image[0].getHeight();
		ImageStack stack = new ImageStack(width, height);
		for (int i=0; i<wList.length; i++) {
			stack.addSlice(null, image[i].getProcessor());
			image[i].changes = false;
			image[i].getWindow().close();
		}
		new ImagePlus("Stack", stack).show();
	}



}

