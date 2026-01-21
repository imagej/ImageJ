/**Implements the Edit/Undo command.*/

package ij;
import ij.process.*;
import ij.gui.*;
import ij.measure.Calibration;
import java.awt.*;
import java.awt.image.*;

/** This class consists of static methods and
	fields that implement ImageJ's Undo command. */
public class Undo {

	public static final int NOTHING = 0;
	/** Undo using ImageProcessor.snapshot. */
	public static final int FILTER = 1;
	/** Undo using an ImageProcessor copy. */
	public static final int TYPE_CONVERSION = 2;
	public static final int PASTE = 3;
	public static final int COMPOUND_FILTER = 4;
	public static final int COMPOUND_FILTER_DONE = 5;
	/** Undo using a single image, or composite color stack, copy (limited to 200MB). */
	public static final int TRANSFORM = 6;
	public static final int OVERLAY_ADDITION = 7;
	public static final int ROI = 8;
	public static final int MACRO = 9;
	/** Undo of overlay modification */
	public static final int OVERLAY = 10;
	
	private static int whatToUndo = NOTHING;
	private static int imageID;
	private static ImageProcessor ipCopy = null;
	private static ImagePlus impCopy;
	private static Calibration calCopy;
	private static Roi roiCopy;
	private static double displayRangeMin, displayRangeMax;
	private static LUT lutCopy;
	private static Overlay overlayCopy;
	private static int overlayImageID;
	
	public static void setup(int what, ImagePlus imp) {
		if (imp==null) {
			whatToUndo = NOTHING;
			reset();
			return;
		}
		if (IJ.debugMode) IJ.log("Undo.setup: "+what+" "+imp);
		if (what==FILTER && whatToUndo==COMPOUND_FILTER)
				return;
		if (what==COMPOUND_FILTER_DONE) {
			if (whatToUndo==COMPOUND_FILTER)
				whatToUndo = what;
			return;
		}
		whatToUndo = what;
		imageID = imp.getID();
		if (what==TYPE_CONVERSION) {
			ipCopy = imp.getProcessor();
			calCopy = (Calibration)imp.getCalibration().clone();
		} else if (what==TRANSFORM) {	
			if ((!IJ.macroRunning()||Prefs.supportMacroUndo) && (imp.getStackSize()==1||imp.getDisplayMode()==IJ.COMPOSITE) && imp.getSizeInBytes()<209715200)
				impCopy = imp.duplicate();
			else
				reset();
		} else if (what==MACRO) {	
			ipCopy = imp.getProcessor().duplicate();
			calCopy = (Calibration)imp.getCalibration().clone();
			impCopy = null;
		} else if (what==COMPOUND_FILTER) {
			ImageProcessor ip = imp.getProcessor();
			if (ip!=null)
				ipCopy = ip.duplicate();
			else
				ipCopy = null;
		} else if (what==OVERLAY_ADDITION) {
			impCopy = null;
			ipCopy = null;
		} else if (what==ROI) {
			impCopy = null;
			ipCopy = null;
			Roi roi = imp.getRoi();
			if (roi!=null) {
				roiCopy = (Roi)roi.clone();
				roiCopy.setImage(null);
			} else
				whatToUndo = NOTHING;
		} else if (what==OVERLAY) {
			saveOverlay(imp);
		} else {
			ipCopy = null;
			//ImageProcessor ip = imp.getProcessor();
			//lutCopy = (LUT)ip.getLut().clone();
		}
	}

	/** This function should be called from PlugInFilters that modify the overlay prior to the operation.
	 *  For the type 'FILTER', undo of overlays requires that the modified image also has an overlay. */
	public static void saveOverlay(ImagePlus imp) {
		Overlay overlay = imp!=null?imp.getOverlay():null;
		if (overlay!=null) {
			overlayCopy = overlay.duplicate();
			overlayImageID = imp.getID();
		} else
			overlayCopy = null;
	}
		
	public static void reset() {
		//if (IJ.debugMode) IJ.log("Undo.reset: "+ whatToUndo+" "+impCopy);
		if (whatToUndo==COMPOUND_FILTER || whatToUndo==OVERLAY_ADDITION)
			return;
		whatToUndo = NOTHING;
		imageID = 0;
		ipCopy = null;
		impCopy = null;
		calCopy = null;
		roiCopy = null;
		lutCopy = null;
		overlayCopy = null;
	}	

	public static void undo() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (IJ.debugMode) IJ.log("Undo.undo: "+ whatToUndo+" "+imp+"  "+impCopy);
		if (imp==null || imageID!=imp.getID()) {
			if (imp!=null && !IJ.macroRunning()) { // does foreground image still have an undo buffer?
				ImageProcessor ip2 = imp.getProcessor();
				ip2.swapPixelArrays();
				imp.updateAndDraw();
			} else
				reset();
			return;
		}
		switch (whatToUndo) {
			case FILTER:
				undoOverlay(imp, true);
				ImageProcessor ip = imp.getProcessor();
				if (ip!=null) {
					if (!IJ.macroRunning()) {
						ip.swapPixelArrays();
						imp.updateAndDraw();
						return; // don't reset
					} else {
						ip.reset();
						imp.updateAndDraw();
					}
				}
				break;
			case TYPE_CONVERSION:
			case COMPOUND_FILTER:
			case COMPOUND_FILTER_DONE:
				if (ipCopy!=null) {
					if (whatToUndo==TYPE_CONVERSION && calCopy!=null)
						imp.setCalibration(calCopy);
					if (swapImages(new ImagePlus("",ipCopy), imp)) {
						imp.updateAndDraw();
						return;
					} else
						imp.setProcessor(null, ipCopy);
					if (whatToUndo==COMPOUND_FILTER_DONE || whatToUndo==TYPE_CONVERSION)
						undoOverlay(imp, true);
				}
				break;
			case TRANSFORM:
				if (impCopy!=null)
					imp.setStack(impCopy.getStack());
				break;
			case PASTE:
				Roi roi = imp.getRoi();
				if (roi!=null)
					roi.abortPaste();
	    		break;
			case ROI:
				Roi roiCopy2 = roiCopy;
				setup(ROI, imp); // setup redo
				imp.setRoi(roiCopy2);
				return; //don't reset
			case MACRO:
				if (ipCopy!=null) {
					imp.setProcessor(ipCopy);
					if (calCopy!=null) imp.setCalibration(calCopy);
				}
				break;
			case OVERLAY_ADDITION:
				Overlay overlay = imp.getOverlay();
				if (overlay==null) 
					{IJ.beep(); return;}
				int size = overlay.size();
				if (size>0) {
					overlay.remove(size-1);
					imp.draw();
				} else {
					IJ.beep();
					return;
				}
	    		return; //don't reset; successive undo removes further rois
			case OVERLAY:
				undoOverlay(imp, false);
				imp.draw();
				break;
    	}
    	reset();
	}

	/** Reverts the overlay to the saved version. */
	private static void undoOverlay(ImagePlus imp, boolean onlyModifyOvly) {
		if (overlayCopy!=null && imp.getID()==overlayImageID) {
			Overlay overlay = imp.getOverlay();
			imp.setOverlay(overlayCopy);
			if (overlay != null)
				overlayCopy = overlay.duplicate();	//swap
		}
	}

	static boolean swapImages(ImagePlus imp1, ImagePlus imp2) {
		if (imp1.getWidth()!=imp2.getWidth() || imp1.getHeight()!=imp2.getHeight()
		|| imp1.getBitDepth()!=imp2.getBitDepth() || IJ.macroRunning())
			return false;
		ImageProcessor ip1 = imp1.getProcessor();
		ImageProcessor ip2 = imp2.getProcessor();
		double min1 = ip1.getMin();
		double max1 = ip1.getMax();
		double min2 = ip2.getMin();
		double max2 = ip2.getMax();
		ip2.setSnapshotPixels(ip1.getPixels());
		ip2.swapPixelArrays();
		ip1.setPixels(ip2.getSnapshotPixels());
		ip2.setSnapshotPixels(null);
		ip1.setMinAndMax(min2, max2);
		ip2.setMinAndMax(min1, max1);
		return true;
	}

}
