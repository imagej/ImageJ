package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.image.*;

/** Implements the flip and rotate commands in the Image/Transformations submenu. */
public class Transformer implements PlugInFilter {
	
	ImagePlus imp;
	String arg;

	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		if (arg.equals("fliph") || arg.equals("flipv"))
			return DOES_ALL+NO_UNDO;
		else
			return DOES_ALL+NO_UNDO+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {

		if (arg.equals("fliph")) {
			StackProcessor sp = new StackProcessor(imp.getStack(), ip);
			sp.flipHorizontal();
			return;
		}
		
		if (arg.equals("flipv")) {
			StackProcessor sp = new StackProcessor(imp.getStack(), ip);
			sp.flipVertical();
			return;
		}
		
		if (arg.equals("right") || arg.equals("left")) {
	    	StackProcessor sp = new StackProcessor(imp.getStack(), ip);
	    	ImageStack s2 = null;
			if (arg.equals("right"))
	    		s2 = sp.rotateRight();
	    	else
	    		s2 = sp.rotateLeft();
	    	imp.changes = false;
	    	imp.getWindow().close();
	    	new ImagePlus(imp.getTitle(), s2).show();
			return;
		}
	}

}
