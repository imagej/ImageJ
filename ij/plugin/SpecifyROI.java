package ij.plugin;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.util.Tools;
import ij.measure.Calibration;

/**
 *		This plugin implements the Edit/Selection/Specify command.<p>

 *		New update, correctly handling existing oval ROIs, the case that 
 *		"Centered" is already selected when the plugin starts, and always 
 *		restoring the original ROI when the dialog is cancelled (JW, 2008/02/22)
 *
 *		Enhancing the original plugin created by Jeffrey Kuhn, this one takes,
 *		in addition to width and height and the option to have an oval ROI from 
 *		the original program, x & y coordinates, slice number, and the option to have
 *		the x & y coordinates centered or in default top left corner of ROI.
 *		The original creator is Jeffrey Kuhn, The University of Texas at Austin,
 *		jkuhn@ccwf.cc.utexas.edu
 *
 *		@author Joachim Wesner
 *		@author Anthony Padua
 *		
 */
public class SpecifyROI implements PlugIn, DialogListener {
	private static double width, height, xRoi, yRoi;
	private static boolean oval;
	private static boolean square;
	private static boolean centered;
	private static boolean scaledUnits;
	private final static int WIDTH = 0, HEIGHT = 1, X_ROI = 2, Y_ROI = 3;	//sequence of NumericFields
	private final static int OVAL = 0, SQUARE = 1, CENTERED = 2, SCALED_UNITS = 3; //sequence of Checkboxes
	private static Rectangle prevRoi;
	private static double prevPixelWidth = 1.0;
	private int iSlice;
	private boolean bAbort;
	private ImagePlus imp;
	private Vector fields, checkboxes;
	private int stackSize;

	public void run(String arg) {
		imp = IJ.getImage();
		if (imp == null) return;
		stackSize = imp.getStackSize();
		Roi roi = imp.getRoi();
		Calibration cal = imp.getCalibration();
		if (roi!=null && roi.getBounds().equals(prevRoi) && cal.pixelWidth==prevPixelWidth)
			roi = null;
		if (roi!=null) {
			boolean rectOrOval = roi!=null && (roi.getType()==Roi.RECTANGLE||roi.getType()==Roi.OVAL);
			oval = rectOrOval && (roi.getType()==Roi.OVAL); // Handle existing oval ROI
			Rectangle r = roi.getBounds();
			width = r.width;
			height = r.height;
			xRoi = r.x;
			yRoi = r.y;
			if (scaledUnits && cal.scaled()) {
				xRoi = xRoi*cal.pixelWidth;
				yRoi = yRoi*cal.pixelHeight;
				width = width*cal.pixelWidth;
				height = height*cal.pixelHeight;
			}
			if (centered) { // Make xRoi and yRoi consistent when centered mode is active
				xRoi += width/2.0;
				yRoi += height/2.0; 
			}
		} else if (!validDialogValues()) {
			width = imp.getWidth()/2;
			height = imp.getHeight()/2;
			xRoi = width/2;
			yRoi = height/2;
		}
		iSlice = imp.getCurrentSlice();
		showDialog();
	}
	
	boolean validDialogValues() {
		Calibration cal = imp.getCalibration();
		double pw=cal.pixelWidth, ph=cal.pixelHeight;
		if (width/pw<1 || height/ph<1)
			return false;
		if (xRoi/pw>imp.getWidth() || yRoi/ph>imp.getHeight())
			return false;
		return true;
	}

	/**
	 *	Creates a dialog box, allowing the user to enter the requested
	 *	width, height, x & y coordinates, slice number for a Region Of Interest,
	 *	option for oval, and option for whether x & y coordinates to be centered.
	 */
	void showDialog() {
		Calibration cal = imp.getCalibration();
		int digits = 0;
		if (scaledUnits && cal.scaled())
			digits = 2;
		Roi roi = imp.getRoi();
		if (roi==null)
			drawRoi();
		GenericDialog gd = new GenericDialog("Specify");
		gd.addNumericField("Width:", width, digits);
		gd.addNumericField("Height:", height, digits);
		gd.addNumericField("X coordinate:", xRoi, digits);
		gd.addNumericField("Y coordinate:", yRoi, digits);
		if (stackSize>1)
			gd.addNumericField("Slice:", iSlice, 0);
		gd.addCheckbox("Oval", oval);
		gd.addCheckbox("Constrain square/circle", square);
		gd.addCheckbox("Centered",centered);
		if (cal.scaled()) {
			boolean unitsMatch = cal.getXUnit().equals(cal.getYUnit());
			String units = unitsMatch ? cal.getUnits() : cal.getXUnit()+" x "+cal.getYUnit();
			gd.addCheckbox("Scaled units ("+units+")", scaledUnits);
		}
		fields = gd.getNumericFields();
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
			 if (roi==null)
				imp.deleteRoi();
			 else // *ALWAYS* restore initial ROI when cancelled
				imp.setRoi(roi);
		}
	}
	
	void drawRoi() {
		double xPxl = xRoi;
		double yPxl = yRoi;
		if (centered) {
			xPxl -= width/2;
			yPxl -= height/2;
		}
		double widthPxl = width;
		double heightPxl = height;
		Calibration cal = imp.getCalibration();
		if (scaledUnits && cal.scaled()) {
			xPxl /= cal.pixelWidth;
			yPxl /= cal.pixelHeight;
			widthPxl /= cal.pixelWidth;
			heightPxl /= cal.pixelHeight;
			prevPixelWidth = cal.pixelWidth;
		}
		Roi roi;
		if (oval)
			roi = new OvalRoi(xPxl, yPxl, widthPxl, heightPxl);
		else
			roi = new Roi(xPxl, yPxl, widthPxl, heightPxl);
		imp.setRoi(roi);
		prevRoi = roi.getBounds();
		//prevPixelWidth = cal.pixelWidth;
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		if (IJ.isMacOSX()) IJ.wait(50);
		Calibration cal = imp.getCalibration();
		width = gd.getNextNumber();
		height = gd.getNextNumber();
		xRoi = gd.getNextNumber();	
		yRoi = gd.getNextNumber();
		if (stackSize>1)	
			iSlice = (int) gd.getNextNumber(); 
		oval = gd.getNextBoolean();
		square = gd.getNextBoolean();
		centered = gd.getNextBoolean();
		if (cal.scaled())
			scaledUnits = gd.getNextBoolean();
		if (gd.invalidNumber() || width<=0 || height<=0)
			return false;
		//
		Vector numFields = gd.getNumericFields();
		Vector checkboxes = gd.getCheckboxes();
		boolean newWidth = false, newHeight = false, newXY = false;
		if (e!=null && e.getSource()==checkboxes.get(SQUARE) && square) {
			width = 0.5*(width+height);				//make square: same width&height
			height = width;
			newWidth = true;
			newHeight = true;
		}
		if (e!=null && e.getSource()==checkboxes.get(CENTERED)) {
			double shiftBy = centered ? 0.5 : -0.5; //'centered' changed:
			xRoi += shiftBy * width;				//shift x, y to keep roi the same
			yRoi += shiftBy * height;
			newXY = true;
		}
		if (square && width!=height && e!=null) {	//in 'square' mode, synchronize width&height
			if (e.getSource()==numFields.get(WIDTH)) {
				height = width;
				newHeight = true;
			} else if (e.getSource()==numFields.get(HEIGHT)) {
				width = height;
				newWidth = true;
			}
		}
		if (e!=null && cal.scaled() && e.getSource()==checkboxes.get(SCALED_UNITS)) {
			double xFactor = scaledUnits ? cal.pixelWidth : 1./cal.pixelWidth;
			double yFactor = scaledUnits ? cal.pixelHeight : 1./cal.pixelHeight;
			width *= xFactor;				 //transform everything to keep roi the same
			height *= yFactor;
			xRoi *= xFactor;
			yRoi *= yFactor;
			newWidth = true; newHeight = true; newXY = true;
		}
		int digits = (scaledUnits || (int)width!=width) ? 2 : 0;
		if (newWidth)
			((TextField)(numFields.get(WIDTH))).setText(IJ.d2s(width, digits));
		if (newHeight)
			((TextField)(numFields.get(HEIGHT))).setText(IJ.d2s(height, digits));
		digits = (scaledUnits || (int)xRoi!=xRoi || (int)yRoi!=yRoi) ? 2 : 0;
		if (newXY) {
			((TextField)(numFields.get(X_ROI))).setText(IJ.d2s(xRoi, digits));
			((TextField)(numFields.get(Y_ROI))).setText(IJ.d2s(yRoi, digits));
		}

		if (stackSize>1 && iSlice>0 && iSlice<=stackSize)
			imp.setSlice(iSlice);
		if (!newWidth && !newHeight	 && !newXY)	   // don't draw if an update will come immediately
			drawRoi();
		return true;
	}

}
