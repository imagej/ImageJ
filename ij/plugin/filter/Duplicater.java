package ij.plugin.filter;
import ij.*;
import ij.process.*;

/** Obsolete; replaced by Duplicator class. */
public class Duplicater implements PlugInFilter {
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		Duplicator duplicator = new Duplicator();
		duplicator.setup("", imp);
		duplicator.duplicate(imp);
	}

	public void duplicate(ImagePlus imp) {
		(new Duplicator()).duplicate(imp);
	}
                
	public ImagePlus duplicateStack(ImagePlus imp, String newTitle) {
		return (new Duplicator()).duplicateStack(imp, newTitle);
	}
	
	public ImagePlus duplicateSubstack(ImagePlus imp, String newTitle, int first, int last) {
		return (new Duplicator()).duplicateSubstack(imp, newTitle, first, last);
	}

}