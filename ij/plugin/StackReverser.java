package ij.plugin;
import ij.*;
import ij.process.*;

/** Implements the Image/Transform/Flip Stack command. */
public class StackReverser implements PlugIn {
	
	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		if (imp.getStackSize()==1) {
			IJ.error("Flip Stack", "This command requires a stack");
			return;
		}
		if (imp.isHyperStack()) {
			IJ.error("Flip Stack", "The command does not currently work with hyperstacks.");
			return;
		}
		flipStack(imp);
	}
	
	public void flipStack(ImagePlus imp) {
		ImageStack stack = imp.getStack();
 		ImageStack stack2 = imp.createEmptyStack();
 		int n;
		while ((n=stack.getSize())>0) { 
			stack2.addSlice(stack.getSliceLabel(n), stack.getProcessor(n));
			stack.deleteLastSlice();
		}
		imp.setStack(null, stack2);
	}

}
