package ij.gui;
import ij.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.frame.Recorder;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.RectToolOptions;
import ij.macro.Interpreter;
import ij.io.RoiDecoder;
import java.awt.*;
import java.util.*;
import java.io.*;
import java.awt.image.*;
import java.awt.event.*;
import java.awt.geom.*;

/** A rectangular region of interest and superclass for the other ROI classes. */
public class Roi extends Object implements Cloneable, java.io.Serializable {

	public static final int CONSTRUCTING=0, MOVING=1, RESIZING=2, NORMAL=3, MOVING_HANDLE=4; // States
	public static final int RECTANGLE=0, OVAL=1, POLYGON=2, FREEROI=3, TRACED_ROI=4, LINE=5, 
		POLYLINE=6, FREELINE=7, ANGLE=8, COMPOSITE=9, POINT=10; // Types
	public static final int HANDLE_SIZE = 5; 
	public static final int NOT_PASTING = -1; 
	
	static final int NO_MODS=0, ADD_TO_ROI=1, SUBTRACT_FROM_ROI=2; // modification states
		
	int startX, startY, x, y, width, height;
	double startXD, startYD;
	Rectangle2D.Double bounds;
	int activeHandle;
	int state;
	int modState = NO_MODS;
	int cornerDiameter;
	
	public static Roi previousRoi;
	public static final BasicStroke onePixelWide = new BasicStroke(1);
	protected static Color ROIColor = Prefs.getColor(Prefs.ROICOLOR,Color.yellow);
	protected static int pasteMode = Blitter.COPY;
	protected static int lineWidth = 1;
	protected static Color defaultFillColor;
	private static Vector listeners = new Vector();
	
	protected int type;
	protected int xMax, yMax;
	protected ImagePlus imp;
	private int imageID;
	protected ImageCanvas ic;
	protected int oldX, oldY, oldWidth, oldHeight;
	protected int clipX, clipY, clipWidth, clipHeight;
	protected ImagePlus clipboard;
	protected boolean constrain; // to be square
	protected boolean center;
	protected boolean aspect;
	protected boolean updateFullWindow;
	protected double mag = 1.0;
	protected double asp_bk; //saves aspect ratio if resizing takes roi very small
	protected ImageProcessor cachedMask;
	protected Color handleColor = Color.white;
	protected Color	 strokeColor;
	protected Color instanceColor; //obsolete; replaced by	strokeColor
	protected Color fillColor;
	protected BasicStroke stroke;
	protected boolean nonScalable;
	protected boolean overlay;
	protected boolean wideLine;
	protected boolean ignoreClipRect;
	private String name;
	private int position;
	private int channel, slice, frame;
	private Overlay prototypeOverlay;
	private boolean subPixel;
	private boolean activeOverlayRoi;
	private Properties props;
	private boolean isCursor;
	private double xcenter = Double.NaN;
	private double ycenter;


	/** Creates a rectangular ROI. */
	public Roi(int x, int y, int width, int height) {
		this(x, y, width, height, 0);
	}

	/** Creates a rectangular ROI using double arguments. */
	public Roi(double x, double y, double width, double height) {
		this(x, y, width, height, 0);
	}

	/** Creates a new rounded rectangular ROI. */
	public Roi(int x, int y, int width, int height, int cornerDiameter) {
		setImage(null);
		if (width<1) width = 1;
		if (height<1) height = 1;
		if (width>xMax) width = xMax;
		if (height>yMax) height = yMax;
		this.cornerDiameter = cornerDiameter;
		this.x = x;
		this.y = y;
		startX = x; startY = y;
		oldX = x; oldY = y; oldWidth=0; oldHeight=0;
		this.width = width;
		this.height = height;
		oldWidth=width;
		oldHeight=height;
		clipX = x;
		clipY = y;
		clipWidth = width;
		clipHeight = height;
		state = NORMAL;
		type = RECTANGLE;
		if (ic!=null) {
			Graphics g = ic.getGraphics();
			draw(g);
			g.dispose();
		}
		fillColor = defaultFillColor;
	}
	
	/** Creates a rounded rectangular ROI using double arguments. */
	public Roi(double x, double y, double width, double height, int cornerDiameter) {
		this((int)x, (int)y, (int)Math.ceil(width), (int)Math.ceil(height), cornerDiameter);
		bounds = new Rectangle2D.Double(x, y, width, height);
		subPixel = true;
	}

	/** Creates a new rectangular Roi. */
	public Roi(Rectangle r) {
		this(r.x, r.y, r.width, r.height);
	}

	/** Starts the process of creating a user-defined rectangular Roi,
		where sx and sy are the starting screen coordinates. */
	public Roi(int sx, int sy, ImagePlus imp) {
		this(sx, sy, imp, 0);
	}
	
	/** Starts the process of creating a user-defined rectangular Roi,
		where sx and sy are the starting screen coordinates. */
	public Roi(int sx, int sy, ImagePlus imp, int cornerDiameter) {
		setImage(imp);
		int ox=sx, oy=sy;
		if (ic!=null) {
			ox = ic.offScreenX(sx);
			oy = ic.offScreenY(sy);
		}
		setLocation(ox, oy);
		this.cornerDiameter = cornerDiameter;
		width = 0;
		height = 0;
		state = CONSTRUCTING;
		type = RECTANGLE;
		if (cornerDiameter>0) {
			double swidth = RectToolOptions.getDefaultStrokeWidth();
			if (swidth>0.0)
				setStrokeWidth(swidth);
			Color scolor = RectToolOptions.getDefaultStrokeColor();
			if (scolor!=null)
				setStrokeColor(scolor);
		}
		fillColor = defaultFillColor;
	}

	/** @deprecated */
	public Roi(int x, int y, int width, int height, ImagePlus imp) {
		this(x, y, width, height);
		setImage(imp);
	}

	/** Set the location of the ROI in image coordinates. */
	public void setLocation(int x, int y) {
		this.x = x;
		this.y = y;
		startX = x; startY = y;
		oldX = x; oldY = y; oldWidth=0; oldHeight=0;
		if (bounds!=null) {
			bounds.x = x;
			bounds.y = y;
		}
	}
	
	/** Set the location of the ROI in image coordinates. */
	public void setLocation(double x, double y) {
		setLocation((int)x, (int)y);
		if ((int)x==x && (int)y==y)
			return;
		if (bounds!=null) {
			bounds.x = x;
			bounds.y = y;
		} else
			bounds = new Rectangle2D.Double(x, y, width, height);
		subPixel = true;
	}
	
	public void setImage(ImagePlus imp) {
		this.imp = imp;
		cachedMask = null;
		if (imp==null) {
			ic = null;
			clipboard = null;
			xMax = yMax = Integer.MAX_VALUE;
		} else {
			ic = imp.getCanvas();
			xMax = imp.getWidth();
			yMax = imp.getHeight();
		}
	}
	
	/** Returns the ImagePlus associated with this ROI, or null. */
	public ImagePlus getImage() {
		return imp;
	}
	
	/** Returns the ID of the image associated with this ROI. */
	public int getImageID() {
		return imp!=null?imp.getID():imageID;
	}

	public int getType() {
		return type;
	}
	
	public int getState() {
		return state;
	}
	
	/** Returns the perimeter length. */
	public double getLength() {
		double pw=1.0, ph=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
		}
		return 2.0*width*pw+2.0*height*ph;
	}
	
	/** Returns Feret's diameter, the greatest distance between 
		any two points along the ROI boundary. */
	public double getFeretsDiameter() {
		double[] a = getFeretValues();
		return a!=null?a[0]:0.0;
	}

	/** Caculates "Feret" (maximum caliper width), "FeretAngle"
		and "MinFeret" (minimum caliper width), "FeretX" and "FeretY". */	
	public double[] getFeretValues() {
		double min=Double.MAX_VALUE, diameter=0.0, angle=0.0, feretX=0.0, feretY=0.0;
		int p1=0, p2=0;
		double pw=1.0, ph=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
		}
		Polygon poly = getConvexHull();
		if (poly==null) {
			poly = getPolygon();
			if (poly==null) return null;
		}
		double w2=pw*pw, h2=ph*ph;
		double dx, dy, d;
		for (int i=0; i<poly.npoints; i++) {
			for (int j=i; j<poly.npoints; j++) {
				dx = poly.xpoints[i] - poly.xpoints[j];
				dy = poly.ypoints[i] - poly.ypoints[j];
				d = Math.sqrt(dx*dx*w2 + dy*dy*h2);
				if (d>diameter) {diameter=d; p1=i; p2=j;}
			}
		}
		Rectangle r = getBounds();
		double cx = r.x + r.width/2.0;
		double cy = r.y + r.height/2.0;
		int n = poly.npoints;
		double[] x = new double[n];
		double[] y = new double[n];
		for (int i=0; i<n; i++) {
			x[i] = (poly.xpoints[i]-cx)*pw;
			y[i] = (poly.ypoints[i]-cy)*ph;
		}
		double xr, yr;
		for (double a=0; a<=90; a+=0.5) { // rotate calipers in 0.5 degree increments
			double cos = Math.cos(a*Math.PI/180.0);
			double sin = Math.sin(a*Math.PI/180.0);
			double xmin=Double.MAX_VALUE, ymin=Double.MAX_VALUE;
			double xmax=-Double.MAX_VALUE, ymax=-Double.MAX_VALUE;
			for (int i=0; i<n; i++) {
				xr = cos*x[i] - sin*y[i];
				yr = sin*x[i] + cos*y[i];
				if (xr<xmin) xmin = xr;
				if (xr>xmax) xmax = xr;
				if (yr<ymin) ymin = yr;
				if (yr>ymax) ymax = yr;
			}
			double width = xmax - xmin;
			double height = ymax - ymin;
			double min2 = Math.min(width, height);
			min = Math.min(min, min2);
		}
		double x1=poly.xpoints[p1], y1=poly.ypoints[p1];
		double x2=poly.xpoints[p2], y2=poly.ypoints[p2];
		if (x1>x2) {
			double tx1=x1, ty1=y1;
			x1=x2; y1=y2; x2=tx1; y2=ty1;
		}
		feretX = x1*pw;
		feretY = y1*ph;
		dx=x2-x1; dy=y1-y2;
		angle = (180.0/Math.PI)*Math.atan2(dy*ph, dx*pw);
		if (angle<0.0)
			angle += 180.0;
		//breadth = getFeretBreadth(poly, angle, x1, y1, x2, y2);
		double[] a = new double[5];
		a[0] = diameter;
		a[1] = angle;
		a[2] = min;
		a[3] = feretX;
		a[4] = feretY;
		return a;
	}
	
	public Polygon getConvexHull() {
		return getPolygon();
	}
	
	double getFeretBreadth(Shape shape, double angle, double x1, double y1, double x2, double y2) {
		double cx = x1 + (x2-x1)/2;
		double cy = y1 + (y2-y1)/2;
		AffineTransform at = new AffineTransform();
		at.rotate(angle*Math.PI/180.0, cx, cy);
		Shape s = at.createTransformedShape(shape);
		Rectangle2D r = s.getBounds2D();
		return Math.min(r.getWidth(), r.getHeight());
		/*
		ShapeRoi roi2 = new ShapeRoi(s);
		Roi[] rois = roi2.getRois();
		if (rois!=null && rois.length>0) {
			Polygon p = rois[0].getPolygon();
			ImageProcessor ip = imp.getProcessor();
			for (int i=0; i<p.npoints-1; i++)
				ip.drawLine(p.xpoints[i], p.ypoints[i], p.xpoints[i+1], p.ypoints[i+1]);
			imp.updateAndDraw();
		}
		*/
	}

	/** Return this selection's bounding rectangle. */
	public Rectangle getBounds() {
		return new Rectangle(x, y, width, height);
	}
	
	/** Return this selection's bounding rectangle. */
	public Rectangle2D.Double getFloatBounds() {
		if (bounds!=null)
			return new Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height);
		else
			return new Rectangle2D.Double(x, y, width, height);
	}

	/**
	* @deprecated
	* replaced by getBounds()
	*/
	public Rectangle getBoundingRect() {
		return getBounds();
	}

	/** Returns the outline of this selection as a Polygon, or 
		null if this is a straight line selection. 
		@see ij.process.ImageProcessor#setRoi
		@see ij.process.ImageProcessor#drawPolygon
		@see ij.process.ImageProcessor#fillPolygon
	*/
	public Polygon getPolygon() {
		int[] xpoints = new int[4];
		int[] ypoints = new int[4];
		xpoints[0] = x;
		ypoints[0] = y;
		xpoints[1] = x+width;
		ypoints[1] = y;
		xpoints[2] = x+width;
		ypoints[2] = y+height;
		xpoints[3] = x;
		ypoints[3] = y+height;
		return new Polygon(xpoints, ypoints, 4);
	}

	public FloatPolygon getFloatPolygon() {
		if (cornerDiameter>0) {
			ImageProcessor ip = getMask();
			Roi roi2 = (new ThresholdToSelection()).convert(ip);
			roi2.setLocation(x, y);
			return roi2.getFloatPolygon();
		}
		if (subPixelResolution() && bounds!=null) {
			float[] xpoints = new float[4];
			float[] ypoints = new float[4];
			xpoints[0] = (float)bounds.x;
			ypoints[0] = (float)bounds.y;
			xpoints[1] = (float)(bounds.x+bounds.width);
			ypoints[1] = (float)bounds.y;
			xpoints[2] = (float)(bounds.x+bounds.width);
			ypoints[2] = (float)(bounds.y+bounds.height);
			xpoints[3] = (float)bounds.x;
			ypoints[3] = (float)(bounds.y+bounds.height);
			return new FloatPolygon(xpoints, ypoints);
		} else {
			Polygon p = getPolygon();
			return new FloatPolygon(toFloat(p.xpoints), toFloat(p.ypoints), p.npoints);
		}
	}
	
	/** Returns, as a FloatPolygon, an interpolated version 
	 * of this selection that has points spaced 1.0 pixel apart.
	 */
	public FloatPolygon getInterpolatedPolygon() {
		return getInterpolatedPolygon(1.0, false);
	}

	/** Returns, as a FloatPolygon, an interpolated version of
	 * this selection with points spaced 'interval' pixels apart. 
	 * If 'smooth' is true, traced and freehand selections are
	 * first smoothed using a 3 point running average.
	 */
	public FloatPolygon getInterpolatedPolygon(double interval, boolean smooth) {
		FloatPolygon p = (this instanceof Line)?((Line)this).getFloatPoints():getFloatPolygon();
		return getInterpolatedPolygon(p, interval, smooth);
	}

	 /**
	 * Returns, as a FloatPolygon, an interpolated version of this selection
	 * with points spaced abs('interval') pixels apart. If 'smooth' is true, traced
	 * and freehand selections are first smoothed using a 3 point running
	 * average. 
	 * If 'interval' is negative, the program is allowed to decrease abs('interval') 
	 * so that the last segment will hit the end point
	 */
	protected FloatPolygon getInterpolatedPolygon(FloatPolygon p, double interval, boolean smooth) {
		boolean allowToAdjust = interval < 0;
		interval = Math.abs(interval);
		boolean isLine = this.isLine();
		double length = p.getLength(isLine);
 
		int npoints = p.npoints;
		if (!isLine) {//**append (and later remove) closing point to end of array
			npoints++;
			p.xpoints = java.util.Arrays.copyOf(p.xpoints, npoints);
			p.xpoints[npoints - 1] = p.xpoints[0];
			p.ypoints = java.util.Arrays.copyOf(p.ypoints, npoints);
			p.ypoints[npoints - 1] = p.ypoints[0];
		}
		int npoints2 = (int) (10 + (length * 1.5) / interval);//allow some headroom

		double tryInterval = interval;
		double minDiff = 1e9;
		double bestInterval = 0;
		int srcPtr = 0;//index of source polygon
		int destPtr = 0;//index of destination polygon
		double[] destXArr = new double[npoints2];
		double[] destYArr = new double[npoints2];
		int nTrials = 50;
		int trial = 0;
		while (trial <= nTrials) {
			destXArr[0] = p.xpoints[0];
			destYArr[0] = p.ypoints[0];
			srcPtr = 0;
			destPtr = 0;
			double xA = p.xpoints[0];//start of current segment
			double yA = p.ypoints[0];

			while (srcPtr < npoints - 1) {//collect vertices
				double xC = destXArr[destPtr];//center circle
				double yC = destYArr[destPtr];
				double xB = p.xpoints[srcPtr + 1];//end of current segment
				double yB = p.ypoints[srcPtr + 1];
				double[] intersections = lineCircleIntersection(xA, yA, xB, yB, xC, yC, tryInterval, true);
				if (intersections.length >= 2) {
					xA = intersections[0];//only use first of two intersections
					yA = intersections[1];
					destPtr++;
					destXArr[destPtr] = xA;
					destYArr[destPtr] = yA;
				} else {
					srcPtr++;//no intersection found, pick next segment
					xA = p.xpoints[srcPtr];
					yA = p.ypoints[srcPtr];
				}
			}
			destPtr++;
			destXArr[destPtr] = p.xpoints[npoints - 1];
			destYArr[destPtr] = p.ypoints[npoints - 1];
			destPtr++;
			if (!allowToAdjust) {
				if (isLine)
					destPtr--;
				break;
			}

			int nSegments = destPtr - 1;
			double dx = destXArr[destPtr - 2] - destXArr[destPtr - 1];
			double dy = destYArr[destPtr - 2] - destYArr[destPtr - 1];
			double lastSeg = Math.sqrt(dx * dx + dy * dy);

			double diff = lastSeg - tryInterval;//always <= 0
			if (Math.abs(diff) < minDiff) {
				minDiff = Math.abs(diff);
				bestInterval = tryInterval;
			}
			double feedBackFactor = 0.66;//factor <1: applying soft successive approximation
			tryInterval = tryInterval + feedBackFactor * diff / nSegments;
			//stop if tryInterval < 80% of interval, OR if last segment differs < 0.05 pixels
			if ((tryInterval < 0.8 * interval || Math.abs(diff) < 0.05 || trial == nTrials - 1) && trial < nTrials) {
				trial = nTrials;//run one more loop with bestInterval to get best polygon
				tryInterval = bestInterval;
			} else
				trial++;
		}
		if (!isLine) //**remove closing point from end of array
			destPtr--;
		float[] xPoints = new float[destPtr];
		float[] yPoints = new float[destPtr];
		for (int jj = 0; jj < destPtr; jj++) {
			xPoints[jj] = (float) destXArr[jj];
			yPoints[jj] = (float) destYArr[jj];
		}
		FloatPolygon fPoly = new FloatPolygon(xPoints, yPoints);
		return fPoly;
	}
	
	/**
	 * <pre>
	 * Calculates intersections of a line segment with a circle
	 * Author N.Vischer
	 * ax, ay, bx, by: points A and B of line segment
	 * cx, cy, rad: Circle center and radius.
	 * ignoreOutside: if true, ignores intersections outside the line segment A-B
	 * @Returns an array of 0, 2 or 4 coordinates (for 0, 1, or 2 intersection
	 * points). If two intersection points are returned, they are listed in travel
	 * direction A->B
	 * </pre>
	 */
	public static double[] lineCircleIntersection(double ax, double ay, double bx, double by, double cx, double cy, double rad, boolean ignoreOutside) {
		//rotates & translates points A, B and C, creating new points A2, B2 and C2.
		//A2 is then on origin, and B2 is on positive x-axis 
		
		double dxAC = cx - ax;
		double dyAC = cy - ay;
		double lenAC = Math.sqrt(dxAC * dxAC + dyAC * dyAC);

		double dxAB = bx - ax;
		double dyAB = by - ay;

		//calculate B2 and C2:
		double xB2 = Math.sqrt(dxAB * dxAB + dyAB * dyAB);

		double phi1 = Math.atan2(dyAB, dxAB);//amount of rotation
		double phi2 = Math.atan2(dyAC, dxAC);
		double phi3 = phi1 - phi2;
		double xC2 = lenAC * Math.cos(phi3);
		double yC2 = lenAC * Math.sin(phi3);//rotation & translation is done
		if (Math.abs(yC2) > rad)
			return new double[0];//no intersection found
		double halfChord = Math.sqrt(rad * rad - yC2 * yC2);
		double sectOne = xC2 - halfChord;//first intersection point, still on x axis
		double sectTwo = xC2 + halfChord;//second intersection point, still on x axis
		double[] xyCoords = new double[4];
		int ptr = 0;
		if ((sectOne >= 0 && sectOne <= xB2) || !ignoreOutside) {
			double sectOneX = Math.cos(phi1) * sectOne + ax;//undo rotation and translation
			double sectOneY = Math.sin(phi1) * sectOne + ay;
			xyCoords[ptr++] = sectOneX;
			xyCoords[ptr++] = sectOneY;
		}
		if ((sectTwo >= 0 && sectTwo <= xB2) || !ignoreOutside) {
			double sectTwoX = Math.cos(phi1) * sectTwo + ax;//undo rotation and translation
			double sectTwoY = Math.sin(phi1) * sectTwo + ay;
			xyCoords[ptr++] = sectTwoX;
			xyCoords[ptr++] = sectTwoY;
		}
		if (halfChord == 0 && ptr > 2) //tangent line returns only one intersection
			ptr = 2;
		xyCoords = java.util.Arrays.copyOf(xyCoords,ptr);
		return xyCoords;
	}

	/** Returns a copy of this roi. See Thinking is Java by Bruce Eckel
		(www.eckelobjects.com) for a good description of object cloning. */
	public synchronized Object clone() {
		try { 
			Roi r = (Roi)super.clone();
			r.setImage(null);
			r.setStroke(getStroke());
			r.setFillColor(getFillColor());
			r.imageID = getImageID();
			if (bounds!=null)
				r.bounds = (Rectangle2D.Double)bounds.clone();
			return r;
		}
		catch (CloneNotSupportedException e) {return null;}
	}
	
	protected void grow(int sx, int sy) {
		if (clipboard!=null) return;
		int xNew = ic.offScreenX(sx);
		int yNew = ic.offScreenY(sy);
		if (type==RECTANGLE) {
			if (xNew < 0) xNew = 0;
			if (yNew < 0) yNew = 0;
		}
		if (constrain) {
			// constrain selection to be square
			if (!center)
				{growConstrained(xNew, yNew); return;}
			int dx, dy, d;
			dx = xNew - x;
			dy = yNew - y;
			if (dx<dy)
				d = dx;
			else
				d = dy;
			xNew = x + d;
			yNew = y + d;
		}
		if (center) {
			width = Math.abs(xNew - startX)*2;
			height = Math.abs(yNew - startY)*2;
			x = startX - width/2;
			y = startY - height/2;
		} else {
			width = Math.abs(xNew - startX);
			height = Math.abs(yNew - startY);
			x = (xNew>=startX)?startX:startX - width;
			y = (yNew>=startY)?startY:startY - height;
			if (type==RECTANGLE) {
				if ((x+width) > xMax) width = xMax-x;
				if ((y+height) > yMax) height = yMax-y;
			}
		}
		updateClipRect();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x;
		oldY = y;
		oldWidth = width;
		oldHeight = height;
		bounds = null;
	}

	private void growConstrained(int xNew, int yNew) {
		int dx = xNew - startX;
		int dy = yNew - startY;
		width = height = (int)Math.round(Math.sqrt(dx*dx + dy*dy));
		if (type==RECTANGLE) {
			x = (xNew>=startX)?startX:startX - width;
			y = (yNew>=startY)?startY:startY - height;
			if (x<0) x = 0;
			if (y<0) y = 0;
			if ((x+width) > xMax) width = xMax-x;
			if ((y+height) > yMax) height = yMax-y;
		} else {
			x = startX + dx/2 - width/2;
			y = startY + dy/2 - height/2;
		}
		updateClipRect();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x;
		oldY = y;
		oldWidth = width;
		oldHeight = height;
	}

	protected void moveHandle(int sx, int sy) {
		double asp;
		if (clipboard!=null) return;
		int ox = ic.offScreenX(sx);
		int oy = ic.offScreenY(sy);
		if (ox<0) ox=0; if (oy<0) oy=0;
		if (ox>xMax) ox=xMax; if (oy>yMax) oy=yMax;
		int x1=x, y1=y, x2=x1+width, y2=y+height, xc=x+width/2, yc=y+height/2;
		if (width > 7 && height > 7) {
			asp = (double)width/(double)height;
			asp_bk = asp;
		} else
			asp = asp_bk;
		
		switch (activeHandle) {
			case 0:
				x=ox; y=oy;
				break;
			case 1:
				y=oy;
				break;
			case 2:
				x2=ox; y=oy;
				break;
			case 3:
				x2=ox;
				break;
			case 4:
				x2=ox; y2=oy;
				break;
			case 5:
				y2=oy;
				break;
			case 6:
				x=ox; y2=oy;
				break;
			case 7:
				x=ox;
				break;
		}
		if (x<x2)
		   width=x2-x;
		else
		  {width=1; x=x2;}
		if (y<y2)
		   height = y2-y;
		else
		   {height=1; y=y2;}
		
		if (center) {
			switch (activeHandle){
				case 0:
					width=(xc-x)*2;
					height=(yc-y)*2;
					break;
				case 1:
					height=(yc-y)*2;
					break;
				case 2:
					width=(x2-xc)*2;
					x=x2-width;
					height=(yc-y)*2;
					break;
				case 3:
					width=(x2-xc)*2;
					x=x2-width;
					break;
				case 4:
					width=(x2-xc)*2;
					x=x2-width;
					height=(y2-yc)*2;
					y=y2-height;
					break;
				case 5:
					height=(y2-yc)*2;
					y=y2-height;
					break;
				case 6:
					width=(xc-x)*2;
					height=(y2-yc)*2;
					y=y2-height;
					break;
				case 7:
					width=(xc-x)*2;
					break;
			}
			if (x>=x2) {
				width=1;
				x=x2=xc;
			}
			if (y>=y2) {
				height=1;
				y=y2=yc;
			}
			bounds = null;
		}
		
		if (constrain) {
			if (activeHandle==1 || activeHandle==5)
				width=height;
			else
				height=width;

			if(x>=x2) {
				width=1;
				x=x2=xc;
			}
			if (y>=y2) {
				height=1;
				y=y2=yc;
			}
			switch (activeHandle) {
				case 0:
					x=x2-width;
					y=y2-height;
					break;
				case 1:
					x=xc-width/2;
					y=y2-height;
					break;
				case 2:
					y=y2-height;
					break;
				case 3:
					y=yc-height/2;
					break;
				case 5:
					x=xc-width/2;
					break;
				case 6:
					x=x2-width;
					break;
				case 7:
					y=yc-height/2;
					x=x2-width;
					break;
			}
			if (center) {
				x=xc-width/2;
				y=yc-height/2;
			}
		}

		if (aspect && !constrain) {
			if (activeHandle==1 || activeHandle==5) width=(int)Math.rint((double)height*asp);
			else height=(int)Math.rint((double)width/asp);
			
			switch (activeHandle){
				case 0:
					x=x2-width;
					y=y2-height;
					break;
				case 1:
					x=xc-width/2;
					y=y2-height;
					break;
				case 2:
					y=y2-height;
					break;
				case 3:
					y=yc-height/2;
					break;
				case 5:
					x=xc-width/2;
					break;
				case 6:
					x=x2-width;
					break;
				case 7:
					y=yc-height/2;
					x=x2-width;
					break;
			}
			if (center) {
				x=xc-width/2;
				y=yc-height/2;
			}
			
			// Attempt to preserve aspect ratio when roi very small:
			if (width<8) {
				if(width<1) width = 1;
				height=(int)Math.rint((double)width/asp_bk);
			}
			if (height<8) {
				if(height<1) height =1;
				width=(int)Math.rint((double)height*asp_bk);
			}
		}
		
		updateClipRect();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX=x; oldY=y;
		oldWidth=width; oldHeight=height;
		bounds = null;
		subPixel = false;
	}

	void move(int sx, int sy) {
		int xNew = ic.offScreenX(sx);
		int yNew = ic.offScreenY(sy);
		int dx = xNew - startX;
		int dy = yNew - startY;
		if (dx==0 && dy==0)
			return;
		x += dx;
		y += dy;
		if (bounds!=null) {
			bounds.x += dx;
			bounds.y += dy;
		}
		boolean isImageRoi = this instanceof ImageRoi;
		if (clipboard==null && type==RECTANGLE && !isImageRoi) {
			if (x<0) x=0; if (y<0) y=0;
			if ((x+width)>xMax) x = xMax-width;
			if ((y+height)>yMax) y = yMax-height;
		}
		startX = xNew;
		startY = yNew;
		if (type==POINT || ((this instanceof TextRoi) && ((TextRoi)this).getAngle()!=0.0))
			ignoreClipRect = true;
		updateClipRect();
		if ((lineWidth>1 && isLine()) || ignoreClipRect || ((this instanceof PolygonRoi)&&((PolygonRoi)this).isSplineFit()))
			imp.draw();
		else
			imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x;
		oldY = y;
		oldWidth = width;
		oldHeight=height;
		if (isImageRoi) showStatus();
		if (bounds!=null) {
			bounds.x = x;
			bounds.y = y;
		}
	}

	/** Nudge ROI one pixel on arrow key press. */
	public void nudge(int key) {
		if (WindowManager.getActiveWindow() instanceof ij.plugin.frame.RoiManager)
			return;
		switch(key) {
			case KeyEvent.VK_UP:
				y--;
				if (y<0 && (type!=RECTANGLE||clipboard==null))
					y = 0;
				break;
			case KeyEvent.VK_DOWN:
				y++;
				if ((y+height)>=yMax && (type!=RECTANGLE||clipboard==null))
					y = yMax-height;
				break;
			case KeyEvent.VK_LEFT:
				x--;
				if (x<0 && (type!=RECTANGLE||clipboard==null))
					x = 0;
				break;
			case KeyEvent.VK_RIGHT:
				x++;
				if ((x+width)>=xMax && (type!=RECTANGLE||clipboard==null))
					x = xMax-width;
				break;
		}
		updateClipRect();
		if (type==POINT)
			imp.draw();
		else
			imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x; oldY = y;
		bounds = null;
		showStatus();
		notifyListeners(RoiListener.MOVED);
	}
	
	/** Nudge lower right corner of rectangular and oval ROIs by
		one pixel based on arrow key press. */
	public void nudgeCorner(int key) {
		if (type>OVAL || clipboard!=null)
			return;
		switch(key) {
			case KeyEvent.VK_UP:
				height--;
				if (height<1) height = 1;
				break;
			case KeyEvent.VK_DOWN:
				height++;
				if ((y+height) > yMax) height = yMax-y;
				break;
			case KeyEvent.VK_LEFT:
				width--;
				if (width<1) width = 1;
				break;
			case KeyEvent.VK_RIGHT:
				width++;
				if ((x+width) > xMax) width = xMax-x;
				break;
		}
		updateClipRect();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x; oldY = y;
		cachedMask = null;
		showStatus();
	}
	
	// Finds the union of current and previous roi
	protected void updateClipRect() {
		clipX = (x<=oldX)?x:oldX;
		clipY = (y<=oldY)?y:oldY;
		clipWidth = ((x+width>=oldX+oldWidth)?x+width:oldX+oldWidth) - clipX + 1;
		clipHeight = ((y+height>=oldY+oldHeight)?y+height:oldY+oldHeight) - clipY + 1;
		int m = 3;
		if (ic!=null) {
			double mag = ic.getMagnification();
			if (mag<1.0)
				m = (int)(4.0/mag);
		}
		m += clipRectMargin();
		m = (int)(m+getStrokeWidth()*2);
		clipX-=m; clipY-=m;
		clipWidth+=m*2; clipHeight+=m*2;
	 }
	 
	protected int clipRectMargin() {
		return 0;
	}
		
	protected void handleMouseDrag(int sx, int sy, int flags) {
		if (ic==null) return;
		constrain = (flags&Event.SHIFT_MASK)!=0;
		center = (flags&Event.CTRL_MASK)!=0 || (IJ.isMacintosh()&&(flags&Event.META_MASK)!=0);
		aspect = (flags&Event.ALT_MASK)!=0;
		switch(state) {
			case CONSTRUCTING:
				grow(sx, sy);
				break;
			case MOVING:
				move(sx, sy);
				break;
			case MOVING_HANDLE:
				moveHandle(sx, sy);
				break;
			default:
				break;
		}
		notifyListeners(state==MOVING?RoiListener.MOVED:RoiListener.MODIFIED);
	}

	int getHandleSize() {
		double mag = ic!=null?ic.getMagnification():1.0;
		double size = HANDLE_SIZE/mag;
		return (int)(size*mag);
	}
	
	public void draw(Graphics g) {
		Color color =  strokeColor!=null? strokeColor:ROIColor;
		if (fillColor!=null) color = fillColor;
		if (Interpreter.isBatchMode() && imp!=null && imp.getOverlay()!=null && strokeColor==null && fillColor==null)
			return;
		g.setColor(color);
		mag = getMagnification();
		int sw = (int)(width*mag);
		int sh = (int)(height*mag);
		int sx1 = screenX(x);
		int sy1 = screenY(y);
		if (subPixelResolution() && bounds!=null) {
			sw = (int)(bounds.width*mag);
			sh = (int)(bounds.height*mag);
			sx1 = screenXD(bounds.x);
			sy1 = screenYD(bounds.y);
		}
		int sx2 = sx1+sw/2;
		int sy2 = sy1+sh/2;
		int sx3 = sx1+sw;
		int sy3 = sy1+sh;
		Graphics2D g2d = (Graphics2D)g;
		if (stroke!=null)
			g2d.setStroke(getScaledStroke());
		if (cornerDiameter>0) {
			int sArcSize = (int)Math.round(cornerDiameter*mag);
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (fillColor!=null)
				g.fillRoundRect(sx1, sy1, sw, sh, sArcSize, sArcSize);
			else
				g.drawRoundRect(sx1, sy1, sw, sh, sArcSize, sArcSize);
		} else {
			if (fillColor!=null) {
				if (!overlay && isActiveOverlayRoi()) {
					g.setColor(Color.cyan);
					g.drawRect(sx1, sy1, sw, sh);
				} else {
					if (!(this instanceof TextRoi))
						g.fillRect(sx1, sy1, sw, sh);
					else
						g.drawRect(sx1, sy1, sw, sh);
				}
			} else
				g.drawRect(sx1, sy1, sw, sh);
		}
		if (state!=CONSTRUCTING && clipboard==null && !overlay) {
			int size2 = HANDLE_SIZE/2;
			drawHandle(g, sx1-size2, sy1-size2);
			drawHandle(g, sx2-size2, sy1-size2);
			drawHandle(g, sx3-size2, sy1-size2);
			drawHandle(g, sx3-size2, sy2-size2);
			drawHandle(g, sx3-size2, sy3-size2);
			drawHandle(g, sx2-size2, sy3-size2);
			drawHandle(g, sx1-size2, sy3-size2);
			drawHandle(g, sx1-size2, sy2-size2);
		}
		drawPreviousRoi(g);
		if (state!=NORMAL)
			showStatus();
		if (updateFullWindow)
			{updateFullWindow = false; imp.draw();}
	}
	
	public void drawOverlay(Graphics g) {
		overlay = true;
		draw(g);
		overlay = false;
	}
	
	void drawPreviousRoi(Graphics g) {
		if (previousRoi!=null && previousRoi!=this && previousRoi.modState!=NO_MODS) {
			if (type!=POINT && previousRoi.getType()==POINT && previousRoi.modState!=SUBTRACT_FROM_ROI)
				return;
			previousRoi.setImage(imp);
			previousRoi.draw(g);
		}		
	}
	
	void drawHandle(Graphics g, int x, int y) {
		double size = (width*height)*mag*mag;
		if (type==LINE) {
			size = Math.sqrt(width*width+height*height);
			size *= size*mag*mag;
		}
		if (size>4000.0) {
			g.setColor(Color.black);
			g.fillRect(x,y,5,5);
			g.setColor(handleColor);
			g.fillRect(x+1,y+1,3,3);
		} else if (size>1000.0) {
			g.setColor(Color.black);
			g.fillRect(x+1,y+1,4,4);
			g.setColor(handleColor);
			g.fillRect(x+2,y+2,2,2);
		} else {			
			g.setColor(Color.black);
			g.fillRect(x+1,y+1,3,3);
			g.setColor(handleColor);
			g.fillRect(x+2,y+2,1,1);
		}
	}

	/**
	* @deprecated
	* replaced by drawPixels(ImageProcessor)
	*/
	public void drawPixels() {
		if (imp!=null)
			drawPixels(imp.getProcessor()); 
	}

	/** Draws the selection outline on the specified ImageProcessor.
		@see ij.process.ImageProcessor#setColor
		@see ij.process.ImageProcessor#setLineWidth
	*/
	public void drawPixels(ImageProcessor ip) {
		endPaste();
		int saveWidth = ip.getLineWidth();
		if (getStrokeWidth()>1f)
			ip.setLineWidth((int)Math.round(getStrokeWidth()));
		if (cornerDiameter>0)
			drawRoundedRect(ip);
		else {
			if (ip.getLineWidth()==1)
				ip.drawRect(x, y, width+1, height+1);
			else
				ip.drawRect(x, y, width, height);
		}
		ip.setLineWidth(saveWidth);
		if (Line.getWidth()>1 || getStrokeWidth()>1)
			updateFullWindow = true;
	}
	
	private void drawRoundedRect(ImageProcessor ip) {
		int margin = (int)getStrokeWidth()/2;
		BufferedImage bi = new BufferedImage(width+margin*2+1, height+margin*2+1, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = bi.createGraphics();
		if (stroke!=null)
			g.setStroke(stroke);
		g.drawRoundRect(margin, margin, width, height, cornerDiameter, cornerDiameter);
		ByteProcessor mask = new ByteProcessor(bi);
		ip.setRoi(x-margin, y-margin, width+margin*2+1, height+margin*2+1);
		ip.fill(mask);
	}
	
	public boolean contains(int x, int y) {
		Rectangle r = new Rectangle(this.x, this.y, width, height);
		boolean contains = r.contains(x, y);
		if (cornerDiameter==0 || contains==false)
			return contains;
		RoundRectangle2D rr = new RoundRectangle2D.Float(this.x, this.y, width, height, cornerDiameter, cornerDiameter);
		return rr.contains(x, y);
	}
		
	/** Returns a handle number if the specified screen coordinates are	 
		inside or near a handle, otherwise returns -1. */
	public int isHandle(int sx, int sy) {
		if (clipboard!=null || ic==null) return -1;
		double mag = ic.getMagnification();
		int size = HANDLE_SIZE+3;
		int halfSize = size/2;
		double x = getXBase();
		double y = getYBase();
		double width = getFloatWidth();
		double height = getFloatHeight();
		int sx1 = ic.screenXD(x) - halfSize;
		int sy1 = ic.screenYD(y) - halfSize;
		int sx3 = ic.screenXD(x+width) - halfSize;
		int sy3 = ic.screenYD(y+height) - halfSize;
		int sx2 = sx1 + (sx3 - sx1)/2;
		int sy2 = sy1 + (sy3 - sy1)/2;
		if (sx>=sx1&&sx<=sx1+size&&sy>=sy1&&sy<=sy1+size) return 0;
		if (sx>=sx2&&sx<=sx2+size&&sy>=sy1&&sy<=sy1+size) return 1;
		if (sx>=sx3&&sx<=sx3+size&&sy>=sy1&&sy<=sy1+size) return 2;
		if (sx>=sx3&&sx<=sx3+size&&sy>=sy2&&sy<=sy2+size) return 3;
		if (sx>=sx3&&sx<=sx3+size&&sy>=sy3&&sy<=sy3+size) return 4;
		if (sx>=sx2&&sx<=sx2+size&&sy>=sy3&&sy<=sy3+size) return 5;
		if (sx>=sx1&&sx<=sx1+size&&sy>=sy3&&sy<=sy3+size) return 6;
		if (sx>=sx1&&sx<=sx1+size&&sy>=sy2&&sy<=sy2+size) return 7;
		return -1;
	}
	
	protected void mouseDownInHandle(int handle, int sx, int sy) {
		state = MOVING_HANDLE;
		activeHandle = handle;
	}

	protected void handleMouseDown(int sx, int sy) {
		if (state==NORMAL && ic!=null) {
			state = MOVING;
			startX = ic.offScreenX(sx);
			startY = ic.offScreenY(sy);
			startXD = ic.offScreenXD(sx);
			startYD = ic.offScreenYD(sy);
			//showStatus();
		}
	}
		
	protected void handleMouseUp(int screenX, int screenY) {
		state = NORMAL;
		if (imp==null) return;
		imp.draw(clipX-5, clipY-5, clipWidth+10, clipHeight+10);
		if (Recorder.record) {
			String method;
			if (type==OVAL)
				Recorder.record("makeOval", x, y, width, height);
			else if (!(this instanceof TextRoi)) {
				if (cornerDiameter==0)
					Recorder.record("makeRectangle", x, y, width, height);
				else {
					if (Recorder.scriptMode())
						Recorder.recordCall("imp.setRoi(new Roi("+x+","+y+","+width+","+height+","+cornerDiameter+"));");
					else
						Recorder.record("makeRectangle", x, y, width, height, cornerDiameter);
				}
			}
		}
		if (Toolbar.getToolId()==Toolbar.OVAL&&Toolbar.getBrushSize()>0)  {
			int flags = ic!=null?ic.getModifiers():16;
			if ((flags&16)==0) // erase ROI Brush
				{imp.draw(); return;}
		}
		modifyRoi();
	}

	void modifyRoi() {
		if (previousRoi==null || previousRoi.modState==NO_MODS || imp==null)
			return;
		if (type==POINT || previousRoi.getType()==POINT) {
			if (type==POINT && previousRoi.getType()==POINT) {
				addPoint();
			} else if (isArea() && previousRoi.getType()==POINT && previousRoi.modState==SUBTRACT_FROM_ROI)
				subtractPoints();
			return;
		}
		Roi previous = (Roi)previousRoi.clone();
		previous.modState = NO_MODS;
		ShapeRoi s1	 = null;
		ShapeRoi s2 = null;
		if (previousRoi instanceof ShapeRoi)
			s1 = (ShapeRoi)previousRoi;
		else
			s1 = new ShapeRoi(previousRoi);
		if (this instanceof ShapeRoi)
			s2 = (ShapeRoi)this;
		else
			s2 = new ShapeRoi(this);
		if (previousRoi.modState==ADD_TO_ROI)
			s1.or(s2);
		else
			s1.not(s2);
		previousRoi.modState = NO_MODS;
		Roi[] rois = s1.getRois();
		if (rois.length==0) return;
		int type2 = rois[0].getType();
		Roi roi2 = null;
		if (rois.length==1 && (type2==POLYGON||type2==FREEROI))
			roi2 = rois[0];
		else
			roi2 = s1;
		if (roi2!=null)
			roi2.copyAttributes(previousRoi);
		imp.setRoi(roi2);
		previousRoi = previous;
	}
	
	void addPoint() {
		if (!(type==POINT && previousRoi.getType()==POINT)) {
			modState = NO_MODS;
			imp.draw();
			return;
		}
		previousRoi.modState = NO_MODS;
		PointRoi p1 = (PointRoi)previousRoi;
		FloatPolygon poly = getFloatPolygon();
		p1.addPoint(imp, poly.xpoints[0], poly.ypoints[0]);
		imp.setRoi(p1);
	}
	
	void subtractPoints() {
		previousRoi.modState = NO_MODS;
		PointRoi p1 = (PointRoi)previousRoi;
		PointRoi p2 = p1.subtractPoints(this);
		if (p2!=null)
			imp.setRoi(p1.subtractPoints(this));
		else
			imp.deleteRoi();
	}

	/** If 'add' is true, adds this selection to the previous one. If 'subtract' is true, subtracts 
		it from the previous selection. Called by the IJ.doWand() method, and the makeRectangle(), 
		makeOval(), makePolygon() and makeSelection() macro functions. */
	public void update(boolean add, boolean subtract) {
		if (previousRoi==null) return;
		if (add) {
			previousRoi.modState = ADD_TO_ROI;
			modifyRoi();
		} else if (subtract) {
			previousRoi.modState = SUBTRACT_FROM_ROI;
			modifyRoi();
		} else
			previousRoi.modState = NO_MODS;
	 }

	public void showStatus() {
		if (imp==null)
			return;
		String value;
		if (state!=CONSTRUCTING && (type==RECTANGLE||type==POINT) && width<=25 && height<=25) {
			ImageProcessor ip = imp.getProcessor();
			double v = ip.getPixelValue(x,y);
			int digits = (imp.getType()==ImagePlus.GRAY8||imp.getType()==ImagePlus.GRAY16)?0:2;
			value = ", value="+IJ.d2s(v,digits);
		} else
			value = "";
		Calibration cal = imp.getCalibration();
		String size;
		if (cal.scaled() && !(IJ.altKeyDown()||(state==NORMAL&&IJ.shiftKeyDown())))
			size = ", w="+IJ.d2s(width*cal.pixelWidth)+", h="+IJ.d2s(height*cal.pixelHeight);
		else
			size = ", w="+width+", h="+height;
		IJ.showStatus(imp.getLocationAsString(x,y)+size+value);
	}
		
	/** Always returns null for rectangular Roi's */
	public ImageProcessor getMask() {
		if (cornerDiameter>0)
			return (new ShapeRoi(new RoundRectangle2D.Float(x, y, width, height, cornerDiameter, cornerDiameter))).getMask();
		else
			return null;
	}
	
	public void startPaste(ImagePlus clipboard) {
		IJ.showStatus("Pasting...");
		IJ.wait(10);
		this.clipboard = clipboard;
		imp.getProcessor().snapshot();
		updateClipRect();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
	}
	
	void updatePaste() {
		if (clipboard!=null) {
			imp.getMask();
			ImageProcessor ip = imp.getProcessor();
			ip.reset();
			int xoffset=0, yoffset=0;
			Roi croi = clipboard.getRoi();
			if (croi!=null) {
				Rectangle r = croi.getBounds();
				if (r.x<0) xoffset=-r.x;
				if (r.y<0) yoffset=-r.y;
			}
			ip.copyBits(clipboard.getProcessor(), x+xoffset, y+yoffset, pasteMode);
			if (type!=RECTANGLE)
				ip.reset(ip.getMask());
			if (ic!=null)
				ic.setImageUpdated();
		}
	}

	public void endPaste() {
		if (clipboard!=null) {
			updatePaste();
			clipboard = null;
			Undo.setup(Undo.FILTER, imp);
		}
		activeOverlayRoi = false;
	}
	
	public void abortPaste() {
		clipboard = null;
		imp.getProcessor().reset();
		imp.updateAndDraw();
	}

	/** Returns the angle in degrees between the specified line and a horizontal line. */
	public double getAngle(int x1, int y1, int x2, int y2) {
		return getFloatAngle(x1, y1, x2, y2);
	}
	
	/** Returns the angle in degrees between the specified line and a horizontal line. */
	public double getFloatAngle(double x1, double y1, double x2, double y2) {
		double dx = x2-x1;
		double dy = y1-y2;
		if (imp!=null && !IJ.altKeyDown()) {
			Calibration cal = imp.getCalibration();
			dx *= cal.pixelWidth;
			dy *= cal.pixelHeight;
		}
		return (180.0/Math.PI)*Math.atan2(dy, dx);
	}

	/** Sets the default (global) color used for ROI outlines.
	 * @see #getColor()
	 * @see #setStrokeColor(Color)
	 */
	public static void setColor(Color c) {
		ROIColor = c;
	}
	
	/** Returns the default (global) color used for drawing ROI outlines.
	 * @see #setColor(Color)
	 * @see #getStrokeColor()
	 */
	public static Color getColor() {
		return ROIColor;
	}

	/** Sets the color used by this ROI to draw its outline. This color, if not null, 
	 * overrides the global color set by the static setColor() method.
	 * @see #getStrokeColor
	 * @see #setStrokeWidth
	 * @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
	 */
	public void setStrokeColor(Color c) {
		 strokeColor = c;
	}

	/** Returns the the color used to draw the ROI outline or null if the default color is being used.
	 * @see #setStrokeColor(Color)
	 */
	public Color getStrokeColor() {
		return	strokeColor;
	}

	/** Sets the fill color used to display this ROI, or set to null to display it transparently.
	 * @see #getFillColor
	 * @see #setStrokeColor
	 */
	public void setFillColor(Color color) {
		fillColor = color;
	}

	/** Returns the fill color used to display this ROI, or null if it is displayed transparently.
	 * @see #setFillColor
	 * @see #getStrokeColor
	 */
	public Color getFillColor() {
		return fillColor;
	}
	
	public static void setDefaultFillColor(Color color) {
		defaultFillColor = color;
	}
	
	public static Color getDefaultFillColor() {
		return defaultFillColor;
	}
	
	/** Copy the attributes (outline color, fill color, outline width) 
		of	'roi2' to the this selection. */
	public void copyAttributes(Roi roi2) {
		this. strokeColor = roi2. strokeColor;
		this.fillColor = roi2.fillColor;
		this.stroke = roi2.stroke;
	}

	/**
	* @deprecated
	* replaced by setStrokeColor()
	*/
	public void setInstanceColor(Color c) {
		 strokeColor = c;
	}

	/**
	* @deprecated
	* replaced by setStrokeWidth(int)
	*/
	public void setLineWidth(int width) {
		setStrokeWidth(width) ;
	}
		
	public void updateWideLine(float width) {
		if (isLine()) {
			wideLine = true;
			setStrokeWidth(width);
			if (getStrokeColor()==null) {
				Color c = getColor();
				setStrokeColor(new Color(c.getRed(),c.getGreen(),c.getBlue(), 77));
			}
		}
	}

	/** Set 'nonScalable' true to have TextRois in a display 
		list drawn at a fixed location and size. */
	public void setNonScalable(boolean nonScalable) {
		this.nonScalable = nonScalable;
	}
	
	/** Sets the width of the line used to draw this ROI. Set
	 * the width to 0.0 and the ROI will be drawn using a
	 * a 1 pixel stroke width regardless of the magnification.
	 * @see #setStrokeColor(Color)
	 * @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
	 */
	public void setStrokeWidth(float width) {
		if (width<0f)
			width = 0f;
		if (width==0)
			stroke = null;
		else if (wideLine)
			this.stroke = new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
		else
			this.stroke = new BasicStroke(width);
		if (width>1f) fillColor = null;
	}

	/** This is a version of setStrokeWidth() that accepts a double argument. */
	public void setStrokeWidth(double width) {
		setStrokeWidth((float)width);
	}

	/** Returns the lineWidth. */
	public float getStrokeWidth() {
		return stroke!=null?stroke.getLineWidth():0f;
	}

	/** Sets the Stroke used to draw this ROI. */
	public void setStroke(BasicStroke stroke) {
		this.stroke = stroke;
	}
	
	/** Returns the Stroke used to draw this ROI, or null if no Stroke is used. */
	public BasicStroke getStroke() {
		return stroke;
	}
	
	protected BasicStroke getScaledStroke() {
		if (ic==null)
			return stroke;
		double mag = ic.getMagnification();
		if (mag!=1.0) {
			float width = (float)(stroke.getLineWidth()*mag);
			//return new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
			return new BasicStroke(width, stroke.getEndCap(), stroke.getLineJoin(), stroke.getMiterLimit(), stroke.getDashArray(), stroke.getDashPhase());
		} else
			return stroke;
	}

	/** Returns the name of this ROI, or null. */
	public String getName() {
		return name;
	}

	/** Sets the name of this ROI. */
	public void setName(String name) {
		this.name = name;
	}

	/** Sets the Paste transfer mode.
		@see ij.process.Blitter
	*/
	public static void setPasteMode(int transferMode) {
		if (transferMode==pasteMode) return;
		pasteMode = transferMode;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null)
			imp.updateAndDraw();
	}
	
	/** Sets the rounded rectangle corner diameter (pixels). */
	public void setCornerDiameter(int cornerDiameter) {
		if (cornerDiameter<0) cornerDiameter = 0;
		this.cornerDiameter = cornerDiameter;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null && this==imp.getRoi())
			imp.updateAndDraw();
	}

	/** Returns the rounded rectangle corner diameter (pixels). */
	public int getCornerDiameter() {
		return cornerDiameter;
	}
	
	/** Obsolete; replaced by setCornerDiameter(). */
	public void setRoundRectArcSize(int cornerDiameter) {
		setCornerDiameter(cornerDiameter);
	}

	/** Obsolete; replaced by getCornerDiameter(). */
	public int getRoundRectArcSize() {
		return cornerDiameter;
	}

	/** Sets the stack position (image number) of this ROI. In an overlay, this
	* ROI is only displayed when the stack is at the specified position.
	* Set to zero to have the ROI displayed on all images in the stack.
	* @see ij.gui.Overlay
	*/
	public void setPosition(int n) {
		if (n<0) n=0;
		position = n;
		channel = slice = frame = 0;
	} 

	/** Returns the stack position (image number) of this ROI, or
	*  zero if the ROI is not associated with a particular stack image.
	* @see ij.gui.Overlay
	*/
	public int getPosition() {
		return position;
	}
	
	/** Sets the hyperstack position of this ROI. In an overlay, this
	* ROI is only displayed when the hyperstack is at the specified position.
	* @see ij.gui.Overlay
	*/
	public void setPosition(int channel, int slice, int frame) {
		if (channel<0) channel=0;
		this.channel = channel;
		if (slice<0) slice=0;
		this.slice = slice;
		if (frame<0) frame=0;
		this.frame = frame;
		position = 0;
	}
	
	/** Returns the channel position of this ROI, or zero
	*  if this ROI is not associated with a particular channel.
	*/
	public final int getCPosition() {
		return channel;
	}

	/** Returns the slice position of this ROI, or zero
	*  if this ROI is not associated with a particular slice.
	*/
	public final int getZPosition() {
		return slice;
	}
	
	/** Returns the frame position of this ROI, or zero
	*  if this ROI is not associated with a particular frame.
	*/
	public final int getTPosition() {
		return frame;
	}

	// Used by the FileSaver and RoiEncoder to save overlay settings. */
	public void setPrototypeOverlay(Overlay overlay) {
		prototypeOverlay = new Overlay();
		prototypeOverlay.drawLabels(overlay.getDrawLabels());
		prototypeOverlay.drawNames(overlay.getDrawNames());
		prototypeOverlay.drawBackgrounds(overlay.getDrawBackgrounds());
		prototypeOverlay.setLabelColor(overlay.getLabelColor());
		prototypeOverlay.setLabelFont(overlay.getLabelFont());
	} 

	// Used by the FileOpener and RoiDecoder to restore overlay settings. */
	public Overlay getPrototypeOverlay() {
		if (prototypeOverlay!=null)
			return prototypeOverlay;
		else
			return new Overlay();
	}

	/** Returns the current paste transfer mode, or NOT_PASTING (-1)
		if no paste operation is in progress.
		@see ij.process.Blitter
	*/
	public int getPasteMode() {
		if (clipboard==null)
			return NOT_PASTING;
		else
			return pasteMode;
	}

	/** Returns the current paste transfer mode. */
	public static int getCurrentPasteMode() {
		return pasteMode;
	}
	
	/** Returns 'true' if this is an area selection. */
	public boolean isArea() {
		return (type>=RECTANGLE && type<=TRACED_ROI) || type==COMPOSITE;
	}

	/** Returns 'true' if this is a line selection. */
	public boolean isLine() {
		return type>=LINE && type<=FREELINE;
	}
	
	/** Returns 'true' if this is an ROI primarily used from drawing
		(e.g., TextRoi or Arrow). */
	public boolean isDrawingTool() {
		//return cornerDiameter>0;
		return false;
	}
	
	protected double getMagnification() {
		return ic!=null?ic.getMagnification():1.0;
	}
	
	/** Convenience method that converts Roi type to a human-readable form. */
	public String getTypeAsString() {
		String s="";
		switch(type) {
			case POLYGON: s="Polygon"; break;
			case FREEROI: s="Freehand"; break;
			case TRACED_ROI: s="Traced"; break;
			case POLYLINE: s="Polyline"; break;
			case FREELINE: s="Freeline"; break;
			case ANGLE: s="Angle"; break;
			case LINE: s="Straight Line"; break;
			case OVAL: s="Oval"; break;
			case COMPOSITE: s = "Composite"; break;
			case POINT: s="Point"; break;
			default:
				if (this instanceof TextRoi)
					s = "Text";
				else if (this instanceof ImageRoi)
					s = "Image";
				else
					s = "Rectangle";
				break;
		}
		return s;
	}
	
	/** Returns true if this ROI is currently displayed on an image. */
	public boolean isVisible() {
		return ic!=null;
	}

	/** Returns true if this is a slection that supports sub-pixel resolution. */
	public boolean subPixelResolution() {
		return subPixel;
	}

	/** Returns true if this is a PolygonRoi that supports sub-pixel 
		resolution and polygons are drawn on zoomed images offset
		down and to the right by 0.5 pixels.. */
	public boolean getDrawOffset() {
		return false;
	}
	
	public void setDrawOffset(boolean drawOffset) {
	}
	
	public void setIgnoreClipRect(boolean ignoreClipRect) {
		this.ignoreClipRect = ignoreClipRect;
	}

	/** Returns 'true' if this ROI is displayed and is also in an overlay. */
	public final boolean isActiveOverlayRoi() {
		if (imp==null || this!=imp.getRoi())
			return false;
		Overlay overlay = imp.getOverlay();
		if (overlay!=null && overlay.contains(this))
			return true;
		ImageCanvas ic = imp.getCanvas();
		overlay = ic!=null?ic.getShowAllList():null; // ROI Manager overlay
		return overlay!=null && overlay.contains(this);
	}

	/** Checks whether two rectangles are equal. */
	public boolean equals(Object obj) {
		if (obj instanceof Roi) {
			Roi roi2 = (Roi)obj;
			if (type!=roi2.getType()) return false;
			if (!getBounds().equals(roi2.getBounds())) return false;
			if (getLength()!=roi2.getLength()) return false;
			return true;
		} else
			return false;
	}

	protected int screenX(int ox) {return ic!=null?ic.screenX(ox):ox;}
	protected int screenY(int oy) {return ic!=null?ic.screenY(oy):oy;}
	protected int screenXD(double ox) {return ic!=null?ic.screenXD(ox):(int)ox;}
	protected int screenYD(double oy) {return ic!=null?ic.screenYD(oy):(int)oy;}

	/** Converts a float array to an int array using truncation. */
	public static int[] toInt(float[] arr) {
		return toInt(arr, null, arr.length);
	}

	public static int[] toInt(float[] arr, int[] arr2, int size) {
		int n = arr.length;
		if (size>n) size=n;
		int[] temp = arr2;
		if (temp==null || temp.length<n)
			temp = new int[n];
		for (int i=0; i<size; i++)
			temp[i] = (int)arr[i];
			//temp[i] = (int)Math.floor(arr[i]+0.5);
		return temp;
	}

	/** Converts a float array to an int array using rounding. */
	public static int[] toIntR(float[] arr) {
		int n = arr.length;
		int[] temp = new int[n];
		for (int i=0; i<n; i++)
			temp[i] = (int)Math.floor(arr[i]+0.5);
		return temp;
	}

	/** Converts an int array to a float array. */
	public static float[] toFloat(int[] arr) {
		int n = arr.length;
		float[] temp = new float[n];
		for (int i=0; i<n; i++)
			temp[i] = arr[i];
		return temp;
	}

	public void setProperty(String key, String value) {
		if (key==null) return;
		if (props==null)
			props = new Properties();
		if (value==null || value.length()==0)
			props.remove(key);
		else
			props.setProperty(key, value);
	}
	
	public String getProperty(String property) {
		if (props==null)
			return null;
		else
			return props.getProperty(property);
	}
	
	public void setProperties(String properties) {
		if (props==null)
			props = new Properties();
		else
			props.clear();
		try {
			InputStream is = new ByteArrayInputStream(properties.getBytes("utf-8"));
			props.load(is);
		} catch(Exception e) {
			IJ.error(""+e);
		}
	}

	public String getProperties() {
		if (props==null)
			return null;
		Vector v = new Vector();
		for (Enumeration en=props.keys(); en.hasMoreElements();)
			v.addElement(en.nextElement());
		String[] keys = new String[v.size()];
		for (int i=0; i<keys.length; i++)
			keys[i] = (String)v.elementAt(i);
		Arrays.sort(keys);
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<keys.length; i++) {
			sb.append(keys[i]);
			sb.append(": ");
			sb.append(props.get(keys[i]));
			sb.append("\n");
		}
		return sb.toString();
	}
	
	public int getPropertyCount() {
		if (props==null)
			return 0;
		else
			return props.size();
	}
		
	public String toString() {
		return ("Roi["+getTypeAsString()+", x="+x+", y="+y+", width="+width+", height="+height+"]");
	}

	/** Deprecated */
	public void temporarilyHide() {
	}
	
	public void mouseDragged(MouseEvent e) {
		handleMouseDrag(e.getX(), e.getY(), e.getModifiers());
	}
	
	public void mouseMoved(MouseEvent e) {
	}

	public void mouseReleased(MouseEvent e) {
		handleMouseUp(e.getX(), e.getY());
	}

	public double getXBase() {
		if (bounds!=null)
			return bounds.x;
		else
			return x;
	}

	public double getYBase() {
		if (bounds!=null)
			return bounds.y;
		else
			return y;
	}
	
	public double getFloatWidth() {
		if (bounds!=null)
			return bounds.width;
		else
			return width;
	}

	public double getFloatHeight() {
		if (bounds!=null)
			return bounds.height;
		else
			return height;
	}
	
	/** Overridden by PolygonRoi (angle between first two points), TextRoi (text angle) and Line (line angle). */
	public double getAngle() {
		return 0.0;
	}
	
	public void enableSubPixelResolution() {
		bounds = new Rectangle2D.Double(getXBase(), getYBase(), getFloatWidth(), getFloatHeight());
		subPixel = true;
	}
	
	public void setIsCursor(boolean isCursor) {
		this.isCursor = isCursor;
	}

	public boolean isCursor() {
		return isCursor;
	}

	public String getDebugInfo() {
		return "";
	}
	
	public FloatPolygon getRotationCenter() {
		FloatPolygon p = new FloatPolygon();
		Rectangle2D r = getFloatBounds();
		if (Double.isNaN(xcenter)) {
			xcenter = r.getX()+r.getWidth()/2.0;
			ycenter = r.getY()+r.getHeight()/2.0;
		}
		p.addPoint(xcenter,ycenter);
		return p;
	}

	public void setRotationCenter(double x, double y) {
		xcenter = x;
		ycenter = y;
	}
	
	/* 
	 * Returns the center of the of this selection's countour, or the
	 * center of the bounding box of composite selections.<br> 
	 * Author: Peter Haub (phaub at dipsystems.de)
	 */
	public double[] getContourCentroid() {
		double xC=0, yC=0, lSum=0, x, y, dx, dy, l;
		FloatPolygon poly = getFloatPolygon();
		int nPoints = poly.npoints;
		int n2 = nPoints-1;
		for (int n1=0; n1<nPoints; n1++){
			dx = poly.xpoints[n1] - poly.xpoints[n2];
			dy = poly.ypoints[n1] - poly.ypoints[n2];
			x = poly.xpoints[n2] + dx/2.0;
			y = poly.ypoints[n2] + dy/2.0;
			l = Math.sqrt(dx*dx + dy*dy);
			xC += x*l;
			yC += y*l;
			lSum += l;
			n2 = n1;
		}
		xC /= lSum;
		yC /= lSum;
		return new double[]{xC, yC};
	}

	/** Returns a hashcode for this Roi that typically changes 
		if it is moved, even though it is still the same object. */
	public int getHashCode() {
		return hashCode() ^ (new Double(getXBase()).hashCode()) ^
			Integer.rotateRight(new Double(getYBase()).hashCode(),16);
	}
	
	public void notifyListeners(int id) {
		synchronized (listeners) {
			for (int i=0; i<listeners.size(); i++) {
				RoiListener listener = (RoiListener)listeners.elementAt(i);
				listener.roiModified(imp, id);
			}
		}
	}

	public static void addRoiListener(RoiListener listener) {
		listeners.addElement(listener);
	}
	
	public static void removeRoiListener(RoiListener listener) {
		listeners.removeElement(listener);
	}

}
