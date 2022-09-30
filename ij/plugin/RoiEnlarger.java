package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.Calibration;
import ij.plugin.filter.EDM;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.frame.Recorder;
import java.awt.*;
import java.util.Vector;

/** This plugin, which enlarges or shrinks selections, implements the Edit/Selection/Enlarge command. */
public class RoiEnlarger implements PlugIn, DialogListener {
	private static final String DISTANCE_KEY = "enlarger.distance";
	private static final String USE_PIXELS_KEY = "enlarger.pixels";
	private double defaultDistance = Prefs.get(DISTANCE_KEY, 15); // pixels
	private boolean defaultUsePixels = Prefs.get(USE_PIXELS_KEY, false);
	private Calibration cal;
	private Label unitsLabel;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		Roi roi = imp.getRoi();
		if (roi==null || roi.isLine()) {
			IJ.error("Enlarge", "This command requires an area selection");
			return;
		}
		if (!imp.okToDeleteRoi())
			return;
		double n = showDialog(imp, defaultDistance);
		if (Double.isNaN(n))
			return;
		Prefs.set(DISTANCE_KEY, defaultDistance);
		Prefs.set(USE_PIXELS_KEY, defaultUsePixels);
		Roi roi2 = Math.abs(n)<256?enlarge255(roi,n):enlarge(roi,n);
		if (roi2!=null) {
			imp.setRoi(roi2);
			Roi.setPreviousRoi(roi);
			defaultDistance = n;
		}
		int pixels = (int)Math.round(n);
		Recorder.recordCall("RoiEnlarger.enlarge(imp, "+pixels+");");
	}

	public static void enlarge(ImagePlus imp, int pixels) {
		Roi roi = imp.getRoi();
		if (roi==null || roi.isLine() || (roi instanceof PointRoi))
			return;
		Roi roi2 = Math.abs(pixels)<256?enlarge255(roi,pixels):enlarge(roi,pixels);
		if (roi2!=null)
			imp.setRoi(roi2);
	}

	public double showDialog(ImagePlus imp, double pixels) {
		cal = imp.getCalibration();
		boolean scaled = cal.scaled();
		double pixelWidth = cal.pixelWidth;
		boolean xyScaleDifferent = scaled && cal.pixelWidth != cal.pixelHeight;
		boolean usePixels = defaultUsePixels;
		double n = pixels;
		int decimalPlaces = 0;
		if (scaled && !usePixels) {
			n *= pixelWidth;
			decimalPlaces = getDecimalPlaces(pixelWidth, n);
		}
		GenericDialog gd = new GenericDialog("Enlarge Selection");
		gd.addNumericField("Enlarge by", n, decimalPlaces);
		String units = scaled && !usePixels ? cal.getUnits()+"       " : "pixels ";
		gd.addToSameRow();
		gd.addMessage(units.replace('\n', ' ')); //just in case of a newline character, which would make it a MultiLineLabel
		unitsLabel = (Label)gd.getMessage();
		if (scaled) {
			gd.setInsets(0, 20, 0);     //top left bottom
			gd.addCheckbox("Pixel units", usePixels);
		}
		gd.setInsets(10, 0, 0);
		gd.addMessage("Enter negative number to shrink", null, Color.darkGray);
		if (xyScaleDifferent) {
			gd.setInsets(5, 0, 0);
			gd.addMessage(" \n ", null, Color.RED);
		}
		gd.addDialogListener(this);
		if (xyScaleDifferent && Macro.getOptions()==null)
			updateWarning(gd); //in interactive mode only
		gd.showDialog();
		if (gd.wasCanceled())
			return Double.NaN;
		n = gd.getNextNumber();
		if (scaled)
			usePixels = gd.getNextBoolean();
		pixels = usePixels ? n : n/pixelWidth;
		defaultDistance = pixels;
		defaultUsePixels = usePixels;
		return pixels;
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		Vector checkboxes = gd.getCheckboxes();
		Checkbox usePixelsCbx = checkboxes == null ? null : (Checkbox)checkboxes.get(0);
		double n = gd.getNextNumber();
		boolean usePixels = cal.scaled() ? gd.getNextBoolean() : true; //getNextBoolean also needed for macro recorded
		if (e!=null && e.getSource() == usePixelsCbx) {
			double pixelWidth = cal.pixelWidth;
			int decimalPlaces = 0;
			if (usePixels) {
				n /= pixelWidth;    //scaled to pixels
			} else {
				n *= pixelWidth;    //pixels to scaled
				decimalPlaces = getDecimalPlaces(pixelWidth, n);
			}
			TextField numberField = (TextField)gd.getNumericFields().get(0);
			numberField.setText(IJ.d2s(n, decimalPlaces));
			if (unitsLabel != null) unitsLabel.setText(usePixels ? "pixels" : cal.getUnits());
			boolean xyScaleDifferent = cal.scaled() && cal.pixelWidth != cal.pixelHeight;
			if (xyScaleDifferent && usePixelsCbx != null) updateWarning(gd);
		}
		return !gd.invalidNumber();
	}

	private void updateWarning(GenericDialog gd) {
		Checkbox usePixelsCbx = (Checkbox)gd.getCheckboxes().get(0);
		MultiLineLabel warningLabel = (MultiLineLabel)gd.getMessage();
		boolean showWarning = !usePixelsCbx.getState(); //warn if not pixels units
		warningLabel.setText(showWarning ? "WARNING: x & y scales differ\nConversion to pixels uses x scale" : " \n ");
	}

	//decimal places for displaying the scaled enlarge/shrink value
	private static int getDecimalPlaces(double pixelWidth, double number) {
		if (number == (int)number || pixelWidth == 1) return 0;
		int decimalPlaces = (int)(-Math.log10(pixelWidth)+1.9);
		if (decimalPlaces < 0) decimalPlaces = 0;
		if (decimalPlaces >9) decimalPlaces = 9;
		return decimalPlaces;
	}

	public static Roi enlarge(Roi roi, double pixels) {
		if (pixels==0)
			return roi;
		int type = roi.getType();
		int n = (int)Math.round(pixels);
		if (type==Roi.RECTANGLE || type==Roi.OVAL)
			return enlargeRectOrOval(roi, n);
		if (n<0)
			return shrink(roi, -n);
		Rectangle bounds = roi.getBounds();
		int width = bounds.width;
		int height = bounds.height;
		width += 2*n + 2;
		height += 2*n + 2;
		ImageProcessor ip = new ByteProcessor(width, height);
		ip.invert();
		roi.setLocation(n+1, n+1);
		ip.setColor(0);
		ip.fill(roi);
		ip.setThreshold(0, 0, ImageProcessor.NO_LUT_UPDATE);
		Roi roi2 = (new ThresholdToSelection()).convert(ip);
		Rectangle bounds2 = roi2.getBounds();
		int xoffset = bounds2.x - (n+1);
		int yoffset = bounds2.y - (n+1);
		roi.setLocation(bounds.x, bounds.y);
		FloatProcessor edm = new EDM().makeFloatEDM (ip, 0, false);
		edm.setThreshold(0, n, ImageProcessor.NO_LUT_UPDATE);
		roi2 = (new ThresholdToSelection()).convert(edm);
		if (roi2==null)
			return roi;
		roi2.copyAttributes(roi);
		roi2.setLocation(bounds.x-n+xoffset, bounds.y-n+yoffset);
		if (roi.getStroke()!=null)
			roi2.setStroke(roi.getStroke());
		return roi2;
	}

	private static Roi enlargeRectOrOval(Roi roi, int n) {
		Rectangle bounds = roi.getBounds();
		bounds.x -= n;
		bounds.y -= n;
		bounds.width += 2*n;
		bounds.height += 2*n;
		if (bounds.width<=0 || bounds.height<=0)
			return roi;
		Roi roi2 = null;
		if (roi.getType()==Roi.RECTANGLE)
			roi2 = new Roi(bounds.x, bounds.y, bounds.width, bounds.height);
		else
			roi2 = new OvalRoi(bounds.x, bounds.y, bounds.width, bounds.height);
		roi2.copyAttributes(roi);
		return roi2;
	}

	private static Roi shrink(Roi roi, int n) {
		Rectangle bounds = roi.getBounds();
		int width = bounds.width + 2;
		int height = bounds.height + 2;
		ImageProcessor ip = new ByteProcessor(width, height);
		roi.setLocation(1, 1);
		ip.setColor(255);
		ip.fill(roi);
		roi.setLocation(bounds.x, bounds.y);
		FloatProcessor edm = new EDM().makeFloatEDM (ip, 0, false);
		edm.setThreshold(n+1, Float.MAX_VALUE, ImageProcessor.NO_LUT_UPDATE);
		Roi roi2 = (new ThresholdToSelection()).convert(edm);
		if (roi2==null)
			return roi;
		Rectangle bounds2 = roi2.getBounds();
		if (bounds2.width<=0 && bounds2.height<=0)
			return roi;
		roi2.copyAttributes(roi);
		roi2.setLocation(bounds.x+bounds2.x-1, bounds.y+bounds2.y-1);
		return roi2;
	}

	public static Roi enlarge255(Roi roi, double pixels) {
		if (pixels==0)
			return roi;
		int type = roi.getType();
		int n = (int)Math.round(pixels);
		if (type==Roi.RECTANGLE || type==Roi.OVAL)
			return enlargeRectOrOval(roi, n);
		if (n<0)
			return shrink255(roi, -n);
		Rectangle bounds = roi.getBounds();
		int width = bounds.width;
		int height = bounds.height;
		width += 2*n + 2;
		height += 2*n + 2;
		ImageProcessor ip = new ByteProcessor(width, height);
		ip.invert();
		roi.setLocation(n+1, n+1);
		ip.setColor(0);
		ip.fill(roi);
		ip.setThreshold(0, 0, ImageProcessor.NO_LUT_UPDATE);
		Roi roi2 = (new ThresholdToSelection()).convert(ip);
		Rectangle bounds2 = roi2.getBounds();
		int xoffset = bounds2.x - (n+1);
		int yoffset = bounds2.y - (n+1);
		roi.setLocation(bounds.x, bounds.y);
		boolean bb = Prefs.blackBackground;
		Prefs.blackBackground = true;
		new EDM().toEDM(ip);
		Prefs.blackBackground = bb;
		ip.setThreshold(0, n, ImageProcessor.NO_LUT_UPDATE);
		roi2 = (new ThresholdToSelection()).convert(ip);
		if (roi2==null)
			return roi;
		roi2.copyAttributes(roi);
		roi2.setLocation(bounds.x-n+xoffset, bounds.y-n+yoffset);
		if (roi.getStroke()!=null)
			roi2.setStroke(roi.getStroke());
		return roi2;
	}

	private static Roi shrink255(Roi roi, int n) {
		Rectangle bounds = roi.getBounds();
		int width = bounds.width + 2;
		int height = bounds.height + 2;
		ImageProcessor ip = new ByteProcessor(width, height);
		roi.setLocation(1, 1);
		ip.setColor(255);
		ip.fill(roi);
		roi.setLocation(bounds.x, bounds.y);
		boolean bb = Prefs.blackBackground;
		Prefs.blackBackground = true;
		new EDM().toEDM(ip);
		Prefs.blackBackground = bb;
		ip.setThreshold(n+1, 255, ImageProcessor.NO_LUT_UPDATE);
		Roi roi2 = (new ThresholdToSelection()).convert(ip);
		if (roi2==null)
			return roi;
		Rectangle bounds2 = roi2.getBounds();
		if (bounds2.width<=0 && bounds2.height<=0)
			return roi;
		roi2.copyAttributes(roi);
		roi2.setLocation(bounds.x+bounds2.x-1, bounds.y+bounds2.y-1);
		return roi2;
	}

}
