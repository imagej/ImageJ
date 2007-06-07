/**Implements the Edit/Undo command.*/

package ij;
import ij.process.*;
import java.awt.*;
import java.awt.image.*;
import ij.gui.*;

/** This class consists of static methods and
	fields that implement ImageJ's Undo command. */
public class Undo {

	public static final int NOTHING = 0;
	public static final int FILTER = 1;
	public static final int TYPE_CONVERSION = 2;
	public static final int PASTE = 3;
	public static final int COMPOUND_FILTER = 4;
	public static final int COMPOUND_FILTER_DONE = 5;
	
	private static int whatToUndo = NOTHING;
	private static int imageID;
	private static ImageProcessor ipCopy = null;
	
	
	public static void setup(int what, ImagePlus imp) {
		if (imp==null)
			{reset(); return;}
		//IJ.log(imp.getTitle() + ": set up undo (" + what + ")");
		if (what==FILTER && whatToUndo==COMPOUND_FILTER)
				return;
		if (what==COMPOUND_FILTER_DONE) {
			if (whatToUndo==COMPOUND_FILTER)
				whatToUndo = what;
			return;
		}
		whatToUndo = what;
		imageID = imp.getID();
		if (what==TYPE_CONVERSION)
			ipCopy = imp.getProcessor();
		else if (what==COMPOUND_FILTER) {
			ImageProcessor ip = imp.getProcessor();
			if (ip!=null)
				ipCopy = ip.duplicate();
			else
				ipCopy = null;
		} else
			ipCopy = null;
	}
	
	
	public static void reset() {
		whatToUndo = NOTHING;
		imageID = 0;
		ipCopy = null;
		//IJ.log("Undo: reset");
	}
	

	public static void undo() {
		ImagePlus imp = WindowManager.getCurrentImage();
		//IJ.log(imp.getTitle() + ": undo (" + whatToUndo + ")  "+(imageID!=imp.getID()));
		if (imageID!=imp.getID()) {
			reset();
			return;
		}
		switch (whatToUndo) {
			case FILTER:
				ImageProcessor ip = imp.getProcessor();
				if (ip!=null) {
					ip.reset();
					imp.updateAndDraw();
				}
	    		break;
			case TYPE_CONVERSION:
			case COMPOUND_FILTER:
			case COMPOUND_FILTER_DONE:
				if (ipCopy!=null)
					imp.setProcessor(null, ipCopy);
	    		break;
			case PASTE:
				Roi roi = imp.getRoi();
				if (roi!=null)
					roi.abortPaste();
	    		break;
    	}
    	reset();
	}
	
}
