package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.util.Tools;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;

/** This class implements the Analyze/Tools/Grid command. */
public class Grid implements PlugIn, DialogListener {
	private static final String OPTIONS = "grid.options";
	private static final String GRID = "|GRID|";
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
	private Roi gridOnEntry;

	private String type = types[LINES];
	private double areaPerPoint;
	private static double saveAreaPerPoint;
	private String color = "Cyan";
	private boolean bold;
	private boolean randomOffset;
	private boolean centered;
	private Checkbox centerCheckbox, randomCheckbox;

	public void run(String arg) {
		imp = IJ.getImage();
		Overlay overlay = imp.getOverlay();
		int index = overlay!=null?overlay.getIndex(GRID):-1;
		if (index>=0)
			gridOnEntry = overlay.get(index);
		if (showDialog() && !isMacro)
			saveSettings();
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
		if (shape==null) {
			Overlay overlay = imp.getOverlay();
			if (overlay!=null) {
				if (overlay.size()>1) {
					overlay.remove(GRID);
					imp.draw();
				} else
					imp.setOverlay(null);
			}
		} else {
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
			Overlay overlay = imp.getOverlay();
			if (overlay!=null)
				overlay.remove(GRID);
			else
				overlay = new Overlay();
			overlay.add(roi, GRID);
			imp.setOverlay(overlay);
		}
	}

	private boolean showDialog() {
		isMacro = Macro.getOptions()!=null;
		if (!isMacro)
			getSettings();
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
		gd.addCheckbox("Center grid on image", centered);
		gd.addCheckbox("Random offset", randomOffset);
		gd.addDialogListener(this);
		if (!isMacro) {
			Vector v = gd.getCheckboxes();
			centerCheckbox = (Checkbox)v.elementAt(1);
			randomCheckbox = (Checkbox)v.elementAt(2);
		}
		dialogItemChanged(gd, null);
		gd.showDialog();
		if (gd.wasCanceled()) {
			Overlay overlay = imp.getOverlay();
			if (overlay!=null && gridOnEntry!=null) {
				overlay.remove(GRID);
				overlay.add(gridOnEntry);
				imp.draw();
			} else
				drawGrid(null);
			return false;
		} else
			return true;
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		int width = imp.getWidth();
		int height = imp.getHeight();
		type = gd.getNextChoice();
		areaPerPoint = gd.getNextNumber();
		color = gd.getNextChoice();
		bold = gd.getNextBoolean();
		centered = gd.getNextBoolean();
		randomOffset = gd.getNextBoolean();
		if (randomOffset) {
			centered = false;
			if (centerCheckbox!=null)
				centerCheckbox.setState(false);
		}
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
		if (centered) {
			xstart = (int)Math.round((width%tileWidth)/2.0);
			ystart = (int)Math.round((height%tileHeight)/2.0);
		} else if (randomOffset) {
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
		//IJ.log(centered+" "+xstart+" "+ystart);
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
	
	private void getSettings() {
		String prefs = Prefs.get(OPTIONS, "Lines,Cyan,-");
		//IJ.log("options: "+prefs);
		String[] options = Tools.split(prefs, ",");
		if (options.length>=3) {
			type = options[0];
			if ("None".equals(type))
				type = types[LINES];
			areaPerPoint = saveAreaPerPoint;
			color = options[1];
			bold = options[2].contains("bold");
			centered = options[2].contains("centered");
			randomOffset = options[2].contains("random");
			if (centered)
				randomOffset = false;
		}
	}
	
	private void saveSettings() {
		String options = type+","+color+",";
		String options2 = (bold?"bold ":"")+(centered?"centered ":"")+(randomOffset?"random ":"");
		if (options2.length()==0)
			options2 = "-";
		Prefs.set(OPTIONS, options+options2);
		saveAreaPerPoint = areaPerPoint;
	}

}
