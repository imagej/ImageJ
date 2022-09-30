package ij.plugin;
import ij.*;
import static ij.IJ.createImage;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;
import java.awt.datatransfer.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.Measurements;
import ij.plugin.filter.Analyzer;
import ij.text.TextWindow;
import ij.measure.*;

/** This plugin implements the Analyze/Tools/Calibration Bar command.
	Bob Dougherty, OptiNav, Inc., 4/14/2002
	Based largely on HistogramWindow.java by Wayne Rasband.
	July 2002: Modified by Daniel Marsh and renamed CalibrationBar.
	January 2013: Displays calibration bar as an overlay.
	Jan 2020: calibration bar on separate image, Norbert Vischer
*/

public class CalibrationBar implements PlugIn {
	public static final double  STROKE_WIDTH = 1.0001;
	final static int BAR_LENGTH = 128;
	final static int BAR_THICKNESS = 12;
	final static int XMARGIN = 10;
	final static int YMARGIN = 10;
	final static int WIN_HEIGHT = BAR_LENGTH;
	final static int BOX_PAD = 0;
	final static String CALIBRATION_BAR = "|CB|";
	static int nBins = 256;
	static final String[] colors = {"White","Light Gray","Dark Gray","Black","Red","Green","Blue","Yellow","None"};
	static final String[] locations = {"Upper Right","Lower Right","Lower Left", "Upper Left", "At Selection", "Separate Image"};
	static final int UPPER_RIGHT=0, LOWER_RIGHT=1, LOWER_LEFT=2, UPPER_LEFT=3, AT_SELECTION=4, SEPARATE_IMAGE = 5;

	private static String sFillColor = colors[0];
	private static String sTextColor = colors[3];
	private static String sLocation = locations[UPPER_RIGHT];
	private static double sZoom = 1;
	private static int sNumLabels = 5;
	private static int sFontSize = 12;
	private static int sDecimalPlaces = 0;
	private static boolean sFlatten;
	private static boolean sBoldText;
	
	private String fillColor = sFillColor;
	private String textColor = sTextColor;
	private String location = sLocation;
	private double zoom = sZoom;
	private int numLabels = sNumLabels;
	private int fontSize = sFontSize;
	private int decimalPlaces = sDecimalPlaces;
	private boolean flatten = sFlatten;
	private boolean boldText = sBoldText;

	ImagePlus imp;
	LiveDialog gd;

	ImageStatistics stats;
	Calibration cal;
	int[] histogram;
	Image img;
	Button setup, redraw, insert, unInsert;
	Checkbox ne,nw,se,sw;
	CheckboxGroup locGroup;
	Label value, note;
	int newMaxCount;
	boolean logScale;
	int win_width;
	int userPadding = 0;
	int fontHeight = 0;
	boolean showUnit;
	Object backupPixels;
	byte[] byteStorage;
	int[] intStorage;
	short[] shortStorage;
	float[] floatStorage;
	String boxOutlineColor = colors[8];
	String barOutlineColor = colors[3];
	
	ImageProcessor ip;
	String[] fieldNames = null;
	int insetPad;
	boolean decimalPlacesChanged;

	public void run(String arg) {
		imp = IJ.getImage();
		if (imp.getBitDepth()==24 || imp.getCompositeMode()==IJ.COMPOSITE) {
			IJ.error("Calibration Bar", "RGB and composite images are not supported");
			return;
		}
		if (imp.getRoi()!=null && imp.getRoi().isArea())
			location = locations[AT_SELECTION];
		else if (location.equals(locations[AT_SELECTION]))
			location = locations[UPPER_RIGHT];
		ImageCanvas ic = imp.getCanvas();
		double mag = (ic!=null)?ic.getMagnification():1.0;
		if (zoom<=1 && mag<1)
			zoom = (double) 1.0/mag;
		insetPad = (imp.getWidth()+imp.getHeight())/100;
		if (insetPad<4)
			insetPad = 4;
		updateColorBar();
		if (IJ.isMacro()) {
			flatten = true;
			fillColor = colors[0];
			textColor = colors[3];
			location = locations[UPPER_RIGHT];
			zoom = 1;
			numLabels = 5;
			fontSize = 12;
			decimalPlaces = 0;
		}
		if (!showDialog()) {
			Overlay overlay = imp.getOverlay();
			if (overlay!=null) {
				overlay.remove(CALIBRATION_BAR);
				overlay.setIsCalibrationBar(false);
				imp.draw();
			}
			return;
		}
		updateColorBar();	
		boolean separate = location.equals(locations[SEPARATE_IMAGE]);
		if (flatten || separate) {
			imp.deleteRoi();
			IJ.wait(100);
			ImagePlus imp2 = null;
			if(!separate){
				imp2 = imp.flatten();
				imp2.setTitle(imp.getTitle()+" with bar");
			}
			Overlay overlay = imp.getOverlay();
			if (overlay!=null) {	
				if(separate){	
					Overlay overlaySep = overlay.duplicate();	
					overlay.setIsCalibrationBar(false);
					for (int jj=overlaySep.size()-1; jj>=0; jj--) {//isolate CB components
						Roi roi = overlaySep.get(jj);
						if(roi.getName() == null || !roi.getName().equals(CALIBRATION_BAR))
							overlaySep.remove(roi);
					}
					Rectangle r = overlaySep.get(0).getBounds();
					overlaySep.translate(-r.x, -r.y);
					ImagePlus impSep = IJ.createImage("CBar", "RGB", r.width, r.height, 1);
					impSep.setOverlay(overlaySep);
					impSep = impSep.flatten();//ignore the 'overlay' checkbox
					impSep.setTitle("CBar");
					impSep.show();
				}
				overlay.remove(CALIBRATION_BAR);
				imp.draw();
			}			
			if(imp2 != null)
				imp2.show();			
		}
	}

	private void updateColorBar() {
		Roi roi = imp.getRoi();
		if (roi!=null &&  location.equals(locations[AT_SELECTION])) {
			Rectangle r = roi.getBounds();
			drawBarAsOverlay(imp, r.x, r.y);
		} else if ( location.equals(locations[UPPER_LEFT]))
			drawBarAsOverlay(imp, insetPad, insetPad);
		else if (location.equals(locations[UPPER_RIGHT])) {
			calculateWidth();
			drawBarAsOverlay(imp, imp.getWidth()-insetPad-win_width, insetPad);
		} else if (location.equals(locations[LOWER_LEFT]) )
			drawBarAsOverlay(imp, insetPad,imp.getHeight() - (int)(WIN_HEIGHT*zoom + 2*(int)(YMARGIN*zoom)) - (int)(insetPad*zoom));
		else if(location.equals(locations[LOWER_RIGHT])) {
			calculateWidth();
			drawBarAsOverlay(imp, imp.getWidth()-win_width-insetPad,
				 imp.getHeight() - (int)(WIN_HEIGHT*zoom + 2*(int)(YMARGIN*zoom)) - insetPad);
		}
		else if ( location.equals(locations[SEPARATE_IMAGE])){
			drawBarAsOverlay(imp, insetPad, insetPad);
		}
		this.imp.updateAndDraw();
	}

	private boolean showDialog() {
		gd = new LiveDialog("Calibration Bar");
		gd.addChoice("Location:", locations, location);
		gd.addChoice("Fill color: ", colors, fillColor);
		gd.addChoice("Label color: ", colors, textColor);
		gd.addNumericField("Number of labels:", numLabels, 0);
		gd.addNumericField("Decimal places:", decimalPlaces, 0);
		gd.addNumericField("Font size:", fontSize, 0);
		gd.addNumericField("Zoom factor:", zoom, 1);
		String[] labels = {"Bold text", "Overlay", "Show unit"};
		boolean[] states = {boldText, !flatten, showUnit};
		gd.setInsets(10, 30, 0);
		gd.addCheckboxGroup(2, 2, labels, states);
		Checkbox overlayBox = (Checkbox)(gd.getCheckboxes().elementAt(1));
		if (location.equals(locations[SEPARATE_IMAGE]))
			overlayBox.setEnabled(false);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		location = gd.getNextChoice();
		fillColor = gd.getNextChoice();
		textColor = gd.getNextChoice();
		numLabels = (int)gd.getNextNumber();
		decimalPlaces = (int)gd.getNextNumber();
		fontSize = (int)gd.getNextNumber();
		zoom = (double)gd.getNextNumber();
		boldText = gd.getNextBoolean();
		flatten = !gd.getNextBoolean();
		showUnit = gd.getNextBoolean();
		if (!IJ.isMacro()) {
			sFlatten = flatten;
			sFillColor = fillColor;
			sTextColor = textColor;
			sLocation = location;
			sZoom = zoom;
			sNumLabels = numLabels;
			sFontSize = fontSize;
			sDecimalPlaces = decimalPlaces;
			sBoldText = boldText;
		}
		return true;
	}

	private void drawBarAsOverlay(ImagePlus imp, int x, int y) {
		Roi roi = imp.getRoi();
		if (roi!=null)
			imp.deleteRoi();
		stats = imp.getStatistics(Measurements.MIN_MAX, nBins);
		if (roi!=null)
			imp.setRoi(roi);
		histogram = stats.histogram;
		cal = imp.getCalibration();
		Overlay overlay = imp.getOverlay();
		if (overlay==null)
			overlay = new Overlay();
		else
			overlay.remove(CALIBRATION_BAR);
		int maxTextWidth = addText(null, 0, 0);
		win_width = (int)(XMARGIN*zoom) + 5 + (int)(BAR_THICKNESS*zoom) + maxTextWidth + (int)((XMARGIN/2)*zoom);
		if (x==-1 && y==-1)
			return;	 // return if calculating width
		Color c = getColor(fillColor);
		if (c!=null) {
			Roi r = new Roi(x, y, win_width, (int)(WIN_HEIGHT*zoom + 2*(int)(YMARGIN*zoom)));
			r.setFillColor(c);
			overlay.add(r, CALIBRATION_BAR);
		}
		int xOffset = x;
		int yOffset = y;
		if (decimalPlaces == -1)
			decimalPlaces = Analyzer.getPrecision();
		x = (int)(XMARGIN*zoom) + xOffset;
		y = (int)(YMARGIN*zoom) + yOffset;
		addVerticalColorBar(overlay, x, y, (int)(BAR_THICKNESS*zoom), (int)(BAR_LENGTH*zoom) );
		addText(overlay, x + (int)(BAR_THICKNESS*zoom), y);
		c = getColor(boxOutlineColor);
		overlay.setIsCalibrationBar(true);
		if (imp.getCompositeMode()>0) {
			for (int i=0; i<overlay.size(); i++)
				overlay.get(i).setPosition(imp.getC(), 0, 0);
		}
		imp.setOverlay(overlay);
	}
	
	private void addVerticalColorBar(Overlay overlay, int x, int y, int thickness, int length) {
		int width = thickness;
		int height = length;
		byte[] rLUT,gLUT,bLUT;
		int mapSize = 0;
		java.awt.image.ColorModel cm = imp.getProcessor().getCurrentColorModel();
		if (cm instanceof IndexColorModel) {
			IndexColorModel m = (IndexColorModel)cm;
			mapSize = m.getMapSize();
			rLUT = new byte[mapSize];
			gLUT = new byte[mapSize];
			bLUT = new byte[mapSize];
			m.getReds(rLUT);
			m.getGreens(gLUT);
			m.getBlues(bLUT);
		} else {
			mapSize = 256;
			rLUT = new byte[mapSize];
			gLUT = new byte[mapSize];
			bLUT = new byte[mapSize];
			for (int i = 0; i < mapSize; i++) {
				rLUT[i] = (byte)i;
				gLUT[i] = (byte)i;
				bLUT[i] = (byte)i;
			}
		}
		double colors = mapSize;
		int start = 0;
		ImageProcessor ipOrig =imp.getProcessor();
		if (ipOrig instanceof ByteProcessor) {
			int min = (int)ipOrig.getMin();
			if (min<0) min = 0;
			int max = (int)ipOrig.getMax();
			if (max>255) max = 255;
			colors = max-min+1;
			start = min;
		}
		for (int i = 0; i<(int)(BAR_LENGTH*zoom); i++) {
			int iMap = start + (int)Math.round((i*colors)/(BAR_LENGTH*zoom));
			if (iMap>=mapSize)
				iMap =mapSize - 1;
			int j = (int)(BAR_LENGTH*zoom) - i - 1;
			Line line = new Line(x, j+y, thickness+x, j+y);
			line.setStrokeColor(new Color(rLUT[iMap]&0xff, gLUT[iMap]&0xff, bLUT[iMap]&0xff));
			line.setStrokeWidth(STROKE_WIDTH);
			overlay.add(line, CALIBRATION_BAR);
		}

		Color c = getColor(barOutlineColor);
		if (c!=null) {
			Roi r = new Roi(x, y, width, height);
			r.setStrokeColor(c);
			r.setStrokeWidth(1.0);
			overlay.add(r, CALIBRATION_BAR);
		}
	}

	private int addText(Overlay overlay, int x, int y) {

		Color c = getColor(textColor);
		if (c == null)
			return 0;
		double hmin = cal.getCValue(stats.histMin);
		double hmax = cal.getCValue(stats.histMax);
		double barStep = (double)(BAR_LENGTH*zoom) ;
		if (numLabels > 2)
			barStep /= (numLabels - 1);

		int fontType = boldText?Font.BOLD:Font.PLAIN;
		Font font = null;
		if (fontSize<9)
			font = new Font("SansSerif", fontType, 9);
		else
			font = new Font("SansSerif", fontType, (int)( fontSize*zoom));
		int maxLength = 0;

		FontMetrics metrics = getFontMetrics(font);
		fontHeight = metrics.getHeight();

		for (int i = 0; i < numLabels; i++) {
			double yLabelD = (int)(YMARGIN*zoom + BAR_LENGTH*zoom - i*barStep - 1);
			int yLabel = (int)(Math.round( y + BAR_LENGTH*zoom - i*barStep - 1));
			Calibration cal = imp.getCalibration();
			String s = "";
			if (showUnit)
				s = cal.getValueUnit();
			ImageProcessor ipOrig = imp.getProcessor();
			double min = ipOrig.getMin();
			double max = ipOrig.getMax();
			if (ipOrig instanceof ByteProcessor) {
				if (min<0) min = 0;
				if (max>255) max = 255;
			}
			double grayLabel = min + (max-min)/(numLabels-1) * i;
			if (cal.calibrated()) {
				grayLabel = cal.getCValue(grayLabel);
				double cmin = cal.getCValue(min);
				double cmax = cal.getCValue(max);
				if (!decimalPlacesChanged && decimalPlaces==0 && ((int)cmax!=cmax||(int)cmin!=cmin))
					decimalPlaces = 2;
			}
			String todisplay = d2s(grayLabel)+" "+s;
			if (overlay!=null) {
				TextRoi label = new TextRoi(todisplay, x + 5, yLabel + fontHeight/2, font);				
				label.setStrokeColor(c);
				overlay.add(label, CALIBRATION_BAR);
			}
			int iLength = metrics.stringWidth(todisplay);
			if (iLength > maxLength)
				maxLength = iLength+5;
		}
		return maxLength;
	}
		
	String d2s(double d) {
			return IJ.d2s(d,decimalPlaces);
	}

	int getFontHeight() {
		int fontType = boldText?Font.BOLD:Font.PLAIN;
		Font font = new Font("SansSerif", fontType, (int) (fontSize*zoom) );
		FontMetrics metrics = getFontMetrics(font);
		return metrics.getHeight();
	}

	Color getColor(String color) {
		Color c = Color.white;
		if (color.equals(colors[1]))
			c = Color.lightGray;
		else if (color.equals(colors[2]))
			c = Color.darkGray;
		else if (color.equals(colors[3]))
			c = Color.black;
		else if (color.equals(colors[4]))
			c = Color.red;
		else if (color.equals(colors[5]))
			c = Color.green;
		else if (color.equals(colors[6]))
			c = Color.blue;
		else if (color.equals(colors[7]))
			c = Color.yellow;
		else if (color.equals(colors[8]))
			c = null;
		return c;
	}	 

	void calculateWidth() {
		drawBarAsOverlay(imp, -1, -1);
	}
	
	private FontMetrics getFontMetrics(Font font) {
		BufferedImage bi =new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		Graphics g = (Graphics2D)bi.getGraphics();
		g.setFont(font);
		return g.getFontMetrics(font);
	}
		
	class LiveDialog extends GenericDialog {

		LiveDialog(String title) {
			super(title);
		}

		public void textValueChanged(TextEvent e) {

			if (fieldNames == null) {
				fieldNames = new String[4];
				for(int i=0;i<4;i++)
					fieldNames[i] = ((TextField)numberField.elementAt(i)).getName();
			}

			TextField tf = (TextField)e.getSource();
			String name = tf.getName();
			String value = tf.getText();

			if (value.equals(""))
				return;

			int i=0;
			boolean needsRefresh = false;

			if (name.equals(fieldNames[0])) {

				i = getValue( value ).intValue() ;
				if(i<1)
					return;
				else {
					needsRefresh = true;
					numLabels = i;
				}
			} else if (name.equals(fieldNames[1])) {
				i = getValue( value ).intValue() ;
				if (i<0)
					return;
				else {
					needsRefresh = true;
					decimalPlaces = i;
					decimalPlacesChanged = true;
				}

			} else if (name.equals(fieldNames[2])) {
				i = getValue( value ).intValue() ;
				if(i<1)
					return;
				else {
					needsRefresh = true;
					fontSize = i;

				}

			} else if (name.equals(fieldNames[3])) {
				double d = 0;
				d = getValue( "0" + value ).doubleValue() ;
				if(d<=0)
					return;
				else {
					needsRefresh = true;
					zoom = d;
				}
			}

			if (needsRefresh)
				updateColorBar();
			return;
		}

		public void itemStateChanged(ItemEvent e) {
			location = ( (Choice)(choice.elementAt(0)) ).getSelectedItem();
			fillColor = ( (Choice)(choice.elementAt(1)) ).getSelectedItem();
			textColor = ( (Choice)(choice.elementAt(2)) ).getSelectedItem();
			boldText = ( (Checkbox)(checkbox.elementAt(0)) ).getState();
			flatten = !( (Checkbox)(checkbox.elementAt(1)) ).getState();
			showUnit = ( (Checkbox)(checkbox.elementAt(2)) ).getState();
			Checkbox overlayBox = (Checkbox)(checkbox.elementAt(1) );
			if (location.equals(locations[SEPARATE_IMAGE]))
					overlayBox.setEnabled(false);
			else
					overlayBox.setEnabled(true);
			updateColorBar();
		}

	} //LiveDialog inner class

}
