/**Implements the Edit/Undo command.*/

package ij;
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
	public static final int TRANSFORMATION = 4;
	
	private static int whatToUndo = NOTHING;
	private static ImagePlus imp = null;
	private static Image imageCopy = null;
	
	
	public static void setup(int what, ImagePlus imagePlus) {
		whatToUndo = what;
		imp = imagePlus;
		if (what==TYPE_CONVERSION)
			imageCopy = imp.getImage();
		else
			imageCopy = null;
		if (IJ.debugMode) IJ.write(imp.getTitle() + ": set up undo (" + what + ")");
	}
	
	
	public static void reset() {
		whatToUndo = NOTHING;
		if (imp!=null)
			imp.trimProcessor();
		imp = null;
		imageCopy = null;
		if (IJ.debugMode) IJ.write("Undo: reset");
	}
	

	public static void undo() {
		if (imp!=WindowManager.getCurrentImage() && whatToUndo!=TRANSFORMATION)
			imp = null;
		if (imp==null)
			{reset(); return;}
		switch (whatToUndo) {
			case FILTER:
				imp.undoFilter();
	    		break;
			case TYPE_CONVERSION:
				imp.setImage(imageCopy);
		    	imp.repaintWindow();
				if (IJ.debugMode) IJ.write(imp.getTitle() + ": undo type conversion");
	    		break;
			case PASTE:
				Roi roi = imp.getRoi();
				if (roi!=null)
					roi.abortPaste();
	    		break;
			case TRANSFORMATION:
				ImagePlus iplus = WindowManager.getCurrentImage();
				if (iplus!=null && !iplus.changes)
					iplus.getWindow().close();
				new ImagePlus(imp.getTitle(), imp.getProcessor()).show();
	    		break;
    	}
    	reset();
	}
	
}
