package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import ij.measure.*;

/** This class implements the Analyze/Tools/Grid command. */
public class Grid implements PlugIn, DialogListener {
	private static final String TYPE = "grid.type";
	private static final String COLOR = "grid.color";
	private static final String BOLD = "grid.bold";
	private static double crossSize = 0.1;
	private static String[] colors = {"Red","Green","Blue","Magenta","Cyan","Yellow","Orange","Black","White"};
	private final static int LINES=0, HLINES=1, CROSSES=2, POINTS=3, CIRCLES=4, NONE=4;
	private static String[] types = {"Lines","Horizontal Lines", "Crosses", "Points", "Circles", "None"};
	private Random random = new Random(System.currentTimeMillis());
	private ImagePlus imp;
	private double tileWidth, tileHeight;
	private int xstart, ystart;
	private int linesV, linesH;
	private double pixelWidth=1.0, pixelHeight=1.0;
	private String units = "pixels";
	private boolean isMacro;

	private static String staticType = Prefs.get(TYPE, "types[LINES]");
	private static double staticAreaPerPoint;
	private static String staticColor = Prefs.get(COLOR, "Cyan");
	private static boolean staticBold = Prefs.get(BOLD, false);
	private static boolean staticRandomOffset;

	private String type = staticType;
	private double areaPerPoint = staticAreaPerPoint;
	private static String color = staticColor;
	private static boolean bold = staticBold;
	private static boolean randomOffset = staticRandomOffset;

	public void run(String arg) {
		imp = IJ.getImage();
		if (showDialog() && !isMacro) {
			Prefs.set(TYPE, type);
			Prefs.set(COLOR, color);
			Prefs.set(BOLD, bold);
		}
	}
		
	// http://stackoverflow.com/questions/30654203/how-to-create-a-circle-using-generalpath-and-apache-poi
	private void drawCircles(double size) {
		double R  = size*tileWidth;
		if (R<1) R =1;
		if (bold && type.equals(types[POINTS])) R*=1.5;
		double kappa = 0.5522847498f;
		GeneralPath path = new GeneralPath();
		for(int h=0; h<linesV; h++) {
			for(int v=0; v<linesH; v++) {
				double x = xstart+h*tileWidth;
				double y = ystart+v*tileHeight;
				path.moveTo(x, y-R);
				path.curveTo(x+R*kappa, y-R, x+R, y-R*kappa, x+R, y);
				path.curveTo(x+R, y+R*kappa, x+R*kappa, y+R, x, y+R );
				path.curveTo(x-R*kappa, y+R, x-R, y+R*kappa, x-R, y);
				path.curveTo(x-R, y-R*kappa, x-R*kappa, y-R, x, y-R );
				path.closePath();
			}
		}
		drawGrid(path);
	}

	private void drawCrosses() {
		GeneralPath path = new GeneralPath();
		double arm  = crossSize*tileWidth;
		if (arm<3) arm=3;
		for(int h=0; h<linesV; h++) {
			for(int v=0; v<linesH; v++) {
				double x = xstart+h*tileWidth;
				double y = ystart+v*tileHeight;
				path.moveTo(x-arm, y);
				path.lineTo(x+arm, y);
				path.moveTo(x, y-arm);
				path.lineTo(x, y+arm);
			}
		}
		drawGrid(path);
	}

	private void drawLines() {
		GeneralPath path = new GeneralPath();
		int width = imp.getWidth();
		int height = imp.getHeight();
		for(int i=0; i<linesV; i++) {
			float xoff = (float)(xstart+i*tileWidth);
			path.moveTo(xoff,0f);
			path.lineTo(xoff, height);
		}
		for(int i=0; i<linesH; i++) {
			float yoff = (float)(ystart+i*tileHeight);
			path.moveTo(0f, yoff);
			path.lineTo(width, yoff);
		}
		drawGrid(path);
	}

	private void drawHorizontalLines() {
		GeneralPath path = new GeneralPath();
		int width = imp.getWidth();
		int height = imp.getHeight();
		for(int i=0; i<linesH; i++) {
			float yoff = (float)(ystart+i*tileHeight);
			path.moveTo(0f, yoff);
			path.lineTo(width, yoff);
		}
		drawGrid(path);
	}

	void drawGrid(Shape shape) {
		if (shape==null)
			imp.setOverlay(null);
		else {
			Roi roi = new ShapeRoi(shape);
			roi.setStrokeColor(Colors.getColor(color,Color.cyan));
			if (bold && linesV*linesH<5000) {
				ImageCanvas ic = imp.getCanvas();
				double mag = ic!=null?ic.getMagnification():1.0;
				double width = 2.0;
				if (mag<1.0)
					width = width/mag;
				roi.setStrokeWidth(width);
			}
			IJ.showStatus(linesV*linesH+" nodes");
			imp.setOverlay(new Overlay(roi));
		}
	}

	private boolean showDialog() {
		isMacro = Macro.getOptions()!=null;
		if (isMacro) {
			type = Prefs.get(TYPE, "types[LINES]");
			areaPerPoint = 0.0;
			color = Prefs.get(COLOR, "Cyan");
			bold = false;
			randomOffset = false;
		}
		int width = imp.getWidth();
		int height = imp.getHeight();
		Calibration cal = imp.getCalibration();
		int places;
		if (cal.scaled()) {
			pixelWidth = cal.pixelWidth;
			pixelHeight = cal.pixelHeight;
			units = cal.getUnits();
			places = 2;
		} else {
			pixelWidth = 1.0;
			pixelHeight = 1.0;
			units = "pixels";
			places = 0;
		}
		if (areaPerPoint==0.0)
			areaPerPoint = (width*cal.pixelWidth*height*cal.pixelHeight)/81.0; // default to 9x9 grid
		GenericDialog gd = new GenericDialog("Grid...");
		gd.addChoice("Grid type:", types, type);
		gd.addNumericField("Area per point:", areaPerPoint, places, 6, units+"^2");
		gd.addChoice("Color:", colors, color);
		gd.addCheckbox("Bold", bold);
		gd.addCheckbox("Random offset", randomOffset);
		gd.addDialogListener(this);
		dialogItemChanged(gd, null);
		gd.showDialog();
		if (gd.wasCanceled()) {
			drawGrid(null);
			return false;
		} else {
			if (!isMacro) {
				staticType = type;
				staticAreaPerPoint = areaPerPoint;
				staticColor = color;
				staticBold = bold;
				staticRandomOffset = randomOffset;
			}
			return true;
		}
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		int width = imp.getWidth();
		int height = imp.getHeight();
		type = gd.getNextChoice();
		areaPerPoint = gd.getNextNumber();
		color = gd.getNextChoice();
		bold = gd.getNextBoolean();
		randomOffset = gd.getNextBoolean();
		double minArea= (width*height)/50000.0;
		if (type.equals(types[CROSSES])&&minArea<50.0)
			minArea = 50.0;
		else if (minArea<16)
			minArea = 16.0;
		if (areaPerPoint/(pixelWidth*pixelHeight)<minArea) {
			String err = "\"Area per Point\" too small";
			if (gd.wasOKed())
				IJ.error("Grid", err);
			else
				IJ.showStatus(err);
			return true;
		}
		double tileSize = Math.sqrt(areaPerPoint);
		tileWidth = tileSize/pixelWidth;
		tileHeight = tileSize/pixelHeight;
		if (randomOffset) {
			xstart = (int)(random.nextDouble()*tileWidth);
			ystart = (int)(random.nextDouble()*tileHeight);
		} else {
			xstart = (int)(tileWidth/2.0+0.5);
			ystart = (int)(tileHeight/2.0+0.5);
		}
		linesV = (int)((width-xstart)/tileWidth)+1; 
		linesH = (int)((height-ystart)/tileHeight)+1;
		if (gd.invalidNumber())
			return true;
		drawGrid();
        	return true;
	}

	private void drawGrid() {
		if (type.equals(types[LINES]))
			drawLines();
		else if (type.equals(types[HLINES]))
			drawHorizontalLines();
		else if (type.equals(types[CROSSES]))
			drawCrosses();
		else  if (type.equals(types[POINTS]))
			drawCircles(0.01);
		else  if (type.equals(types[CIRCLES]))
			drawCircles(0.1);
		else
			drawGrid(null);
	}

}
