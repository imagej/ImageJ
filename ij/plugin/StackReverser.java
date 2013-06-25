package ij.plugin;
import ij.*;
import ij.process.*;

/** This plugin implements the Image/Transform/Flip Z and
	Image/Stacks/Tools/Reverse commands. */
public class StackReverser implements PlugIn {
	
	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		if (imp.getStackSize()==1) {
			IJ.error("Flip Z", "This command requires a stack");
			return;
		}
		if (imp.isHyperStack()) {
			IJ.error("Flip Z", "This command does not currently work with hyperstacks.");
			return;
		}
		flipStack(imp);
	}
	
	public void flipStack(ImagePlus imp) {
		ImageStack stack = imp.getStack();
		int n = stack.getSize();
		if (n==1)
			return;
 		ImageStack stack2 = new ImageStack(imp.getWidth(), imp.getHeight(), n);
 		for (int i=1; i<=n; i++) {
 			stack2.setPixels(stack.getPixels(i), n-i+1);
 			stack2.setSliceLabel(stack.getSliceLabel(i), n-i+1);
 		}
 		stack2.setColorModel(stack.getColorModel());
		imp.setStack(stack2);
		if (imp.isComposite()) {
			((CompositeImage)imp).reset();
			imp.updateAndDraw();
		}
	}

}
