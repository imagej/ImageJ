package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.measure.Calibration;
import ij.plugin.frame.Recorder;
import java.awt.AWTEvent;
import java.awt.geom.Rectangle2D;

/**
 * The plugin implements the Image/Adjust/Coordinates command. It allows
 * the user to set the corner coordinates of the selection bounds or the full image.
 * This modifies the image scale (pixelWidth, pixelHeight) and the xOrigin, yOrigin.
 * If a single point is selected, the coordinates of the point can be specified, which only
 * sets the xOrigin and yOrigin.
 * Units for x and y can be also selected (2016-08-30, Michael Schmid).
 * Z unit of stacks can be selected (2019-09-30, Stein Rorvik).
 */
 
public class Coordinates implements PlugIn, DialogListener {

	private static final String help = "<html>"
	+"<h1>Image&gt;Adjust&gt;Coordinates</h1>"
	+"<font size=+1>"
	+"This command allows the user to set the corner coordinates of<br>the selection bounds "
	+"or the full image. This modifies the image<br>scale (<i>pixelWidth</i>, <i>pixelHeight</i>) and <i>xOrigin</i> and <i>yOrigin</i>. "
	+"If a<br>single point is selected, the coordinates of the point can be<br>specified, which only "
	+"sets <i>xOrigin</i> and <i>yOrigin</i>. The units for X<br>and Y can be also selected.<br> "
	+"</font>";

	private final static String SAME_AS_X = "<same as x unit>";
	private final static int IMAGE = 0, ROI_BOUNDS = 1, POINT = 2;	//mode: coordinates of what to specify
	private int mode = IMAGE;
	private boolean isStack;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		int imageHeight = imp.getHeight();
		Calibration cal = imp.getCalibration();
		Roi roi = imp.getRoi();
		Rectangle2D.Double bounds = null;
		int numSlices = imp.getNSlices();
		int currSlice = imp.getCurrentSlice();
		isStack = numSlices>1;
		if (roi != null) {
			bounds = roi.getFloatBounds();
			if (bounds.width==0 && bounds.height==0)
				mode = POINT;
			else
				mode = ROI_BOUNDS;
		} else {	//no Roi, use image bounds
			bounds = new Rectangle2D.Double(0, 0, imp.getWidth(), imp.getHeight());
		}
		String title = (mode==IMAGE ? "Image" : "Selection") +" Coordinates";
		if (mode == POINT)
			title = "Point Coordinates";
		GenericDialog gd = new GenericDialog(title);
		if (mode == POINT) {
			gd.addNumericField("X:", cal.getX(bounds.x), 2, 8, "");
			gd.addNumericField("Y:", cal.getY(bounds.y, imageHeight), 2, 8, "");
			if (isStack)
				gd.addNumericField("Z:", cal.getZ(currSlice-1), 2, 8, "");
		} else {
			gd.addNumericField("Left:", cal.getX(bounds.x), 2, 8, "");
			gd.addNumericField("Right:", cal.getX(bounds.x+bounds.width), 2, 8, "");
			gd.addNumericField("Top:", cal.getY(bounds.y, imageHeight), 2, 8, "");
			gd.addNumericField("Bottom:", cal.getY(bounds.y+bounds.height, imageHeight), 2, 8, "");
			if (isStack) {
				gd.addNumericField("Front:", cal.getZ(0), 2, 8, "");
				gd.addNumericField("Back:", cal.getZ(numSlices), 2, 8, "");
			}
		}
		String xUnit = cal.getUnit();
		String yUnit = cal.getYUnit();
		String zUnit = cal.getZUnit();
		boolean xUnitChanged=false,yUnitChanged=false,zUnitChanged=false;
		gd.addStringField("X_unit:", xUnit, 18);
		gd.addStringField("Y_unit:", yUnit.equals(xUnit) ? SAME_AS_X : yUnit, 18);
		if (isStack)
			gd.addStringField("Z_unit:", zUnit.equals(xUnit) ? SAME_AS_X : zUnit, 18);
		gd.addHelp(help);
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		if (mode == POINT) {
			double x = gd.getNextNumber();
			double y = gd.getNextNumber();
			if (gd.invalidNumber()) {
				IJ.error("Invalid number");
				return;
			}
			cal.xOrigin = coordinate2offset(x, bounds.x, cal.pixelWidth);
			cal.yOrigin = coordinate2offset(y, bounds.y, cal.getInvertY() ? -cal.pixelHeight : cal.pixelHeight);
			if (isStack) {
				double z = gd.getNextNumber();
				if (gd.invalidNumber()) {
					IJ.error("Invalid number");
					return;
				}
				cal.zOrigin = coordinate2offset(z, currSlice-1, cal.pixelDepth);
			}
		} else {
			double xl = gd.getNextNumber();
			double xr = gd.getNextNumber();
			double yt = gd.getNextNumber();
			double yb = gd.getNextNumber();
			if (gd.invalidNumber()) {
				IJ.error("Invalid number");
				return;
			}
			cal.pixelWidth = (xr-xl)/bounds.width;
			cal.pixelHeight = (yb-yt)/bounds.height;
			cal.xOrigin = coordinate2offset(xl, bounds.x, cal.pixelWidth);
			cal.yOrigin = coordinate2offset(yt, bounds.y, cal.pixelHeight);
			cal.setInvertY(cal.pixelHeight < 0);
			if (isStack) {
				double zf = gd.getNextNumber();
				double zl = gd.getNextNumber();
				cal.pixelDepth = (zl-zf)/numSlices;
				cal.zOrigin = coordinate2offset(zf, 0, cal.pixelDepth);	
			}
			if (cal.pixelHeight < 0)
				cal.pixelHeight = -cal.pixelHeight;
		}
		String xUnit2 = gd.getNextString();
		xUnitChanged = !xUnit2.equals(xUnit);
		if (xUnitChanged)
			cal.setXUnit(xUnit2);
		String yUnit2 = gd.getNextString();
		yUnitChanged = !yUnit2.equals(yUnit) && !yUnit2.equals(SAME_AS_X);
		if (yUnitChanged)
			cal.setYUnit(yUnit2);
		String zUnit2 = null;
		if (isStack) {
			zUnit2 = gd.getNextString();
			zUnitChanged = !zUnit2.equals(zUnit) && !zUnit2.equals(SAME_AS_X);
			if (zUnitChanged)
				cal.setZUnit(zUnit2);
		}
		ImageWindow win = imp.getWindow();
		imp.repaintWindow();
		if (Recorder.record) {
			if (Recorder.scriptMode()) {
				if (xUnitChanged)
					Recorder.recordCall("imp.getCalibration().setXUnit(\""+xUnit2+"\");", true);
				if (yUnitChanged)
					Recorder.recordCall("imp.getCalibration().setYUnit(\""+yUnit2+"\");", true);
				if (zUnitChanged)
					Recorder.recordCall("imp.getCalibration().setZUnit(\""+zUnit2+"\");", true);
			} else {
				if (xUnitChanged)
					Recorder.record("Stack.setXUnit", xUnit2);
				if (yUnitChanged)
					Recorder.record("Stack.setYUnit", yUnit2);
				if (zUnitChanged)
					Recorder.record("Stack.setZUnit", zUnit2);
			}
		}
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		if (mode == POINT) {
			gd.getNextNumber();
			gd.getNextNumber();
			if (isStack)
				gd.getNextNumber();
			return (!gd.invalidNumber());
		} else {
			double xl = gd.getNextNumber();
			double xr = gd.getNextNumber();
			double yt = gd.getNextNumber();
			double yb = gd.getNextNumber();
			if (isStack) {
				double zf = gd.getNextNumber();
				double zl = gd.getNextNumber();
				return (!gd.invalidNumber() && (xr>xl) && (yt!=yb) && (zl>zf));
			} else
				return (!gd.invalidNumber() && xr>xl && yt!=yb);
		}
	}

	// Calculates pixel offset from scaled coordinates of a point with given pixel position
	private double coordinate2offset(double coordinate, double pixelPos, double pixelSize) {
		return	pixelPos - coordinate/pixelSize;
	}

}