package ij.gui;
import ij.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.*;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.RoiManager;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.ThresholdToSelection;
import ij.macro.Interpreter;
import ij.io.RoiDecoder;
import java.awt.*;
import java.util.*;
import java.io.*;
import java.awt.image.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * A rectangular region of interest and superclass for the other ROI classes.
 *
 * This class implements {@code Iterable<Point>} and can thus be
 * used to iterate over the contained coordinates. Usage example:
 * <pre>
 * Roi roi = ...;
 * for (Point p : roi) {
 *   // process p
 * }
 * </pre>
 * <b>
 * Convention for subpixel resolution and zooming in:
 * </b><ul>
 * <li> Area ROIs: Integer coordinates refer to the top-left corner of the pixel with these coordinates.
 *      Thus, pixel (0,0) is enclosed by the rectangle spanned between points (0,0) and (1,1),
 *      i.e., a rectangle at (0,0) with width = height = 1 pixel.
 * <li> Line and Point Rois: Integer coordinates refer to the center of a pixel.
 *      Thus, a line from (0,0) to (1,0) has its start and end points in the center of
 *      pixels (0,0) and (1,0), respectively, and drawing the line should affect both
 *      pixels. For images dispplayed at high zoom levels, this means that (open) lines
 *      and single points are displayed 0.5 pixels further to the right and bottom than
 *      the outlines of area ROIs (closed lines) with the same coordinates.
 * </ul>
 * Note that rectangular and (nonrotated) oval ROIs do not support subpixel resolution.
 * Since ImageJ 1.52t, this convention does not depend on the Prefs.subpixelResolution
 * (previously accessible via Edit>Options>Plot) and this flag has no effect any more.
 *
  */
public class Roi extends Object implements Cloneable, java.io.Serializable, Iterable<Point> {

	public static final int CONSTRUCTING=0, MOVING=1, RESIZING=2, NORMAL=3, MOVING_HANDLE=4; // States
	public static final int RECTANGLE=0, OVAL=1, POLYGON=2, FREEROI=3, TRACED_ROI=4, LINE=5,
		POLYLINE=6, FREELINE=7, ANGLE=8, COMPOSITE=9, POINT=10; // Types
	public static final int HANDLE_SIZE = 5;  // replaced by getHandleSize()
	public static final int NOT_PASTING = -1;
	public static final int FERET_ARRAYSIZE = 16; // Size of array with Feret values
	public static final int FERET_ARRAY_POINTOFFSET = 8; // Where point coordinates start in Feret array
	private static final String NAMES_KEY = "group.names";

	static final int NO_MODS=0, ADD_TO_ROI=1, SUBTRACT_FROM_ROI=2; // modification states

	int startX, startY, x, y, width, height;
	double startXD, startYD;
	Rectangle2D.Double bounds;
	int activeHandle;
	int state;
	int modState = NO_MODS;
	int cornerDiameter;             //for rounded rectangle
	int previousSX, previousSY;     //remember for aborting moving with esc and constrain

	public static final BasicStroke onePixelWide = new BasicStroke(1);
	protected static Color ROIColor = Prefs.getColor(Prefs.ROICOLOR,Color.yellow);
	protected static int pasteMode = Blitter.COPY;
	protected static int lineWidth = 1;
	protected static Color defaultFillColor;
	private static Vector listeners = new Vector();
	private static LUT glasbeyLut;
	private static int defaultGroup; // zero is no specific group
	private static Color groupColor;
	private static double defaultStrokeWidth;
	private static String groupNamesString = Prefs.get(NAMES_KEY, null);
	private static String[] groupNames;
	private static boolean groupNamesChanged;

	/** Get using getPreviousRoi() and set using setPreviousRoi() */
	public static Roi previousRoi;

	protected int type;
	protected int xMax, yMax;
	protected ImagePlus imp;
	private int imageID;
	protected ImageCanvas ic;
	protected int oldX, oldY, oldWidth, oldHeight;  //remembers previous clip rect
	protected int clipX, clipY, clipWidth, clipHeight;
	protected ImagePlus clipboard;
	protected boolean constrain;    // to be square or limit to horizontal/vertical motion
	protected boolean center;
	protected boolean aspect;
	protected boolean updateFullWindow;
	protected double mag = 1.0;
	protected double asp_bk;        //saves aspect ratio if resizing takes roi very small
	protected ImageProcessor cachedMask;
	protected Color handleColor = Color.white;
	protected Color strokeColor;
	protected Color instanceColor;  //obsolete; replaced by strokeColor
	protected Color fillColor;
	protected BasicStroke stroke;
	protected boolean nonScalable;
	protected boolean overlay;
	protected boolean wideLine;
	protected boolean ignoreClipRect;
	protected double flattenScale = 1.0;
	protected static Color defaultColor;

	private String name;
	private int position;
	private int channel, slice, frame;
	private boolean hyperstackPosition;
	private Overlay prototypeOverlay;
	private boolean subPixel;
	private boolean activeOverlayRoi;
	private Properties props;
	private boolean isCursor;
	private double xcenter = Double.NaN;
	private double ycenter;
	private boolean listenersNotified;
	private boolean antiAlias = true;
	private int group;
	private boolean usingDefaultStroke;
	private static int defaultHandleSize;
	private int handleSize = -1;
	private boolean scaleStrokeWidth; // Scale stroke width when zooming images?

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
		double defaultWidth = defaultStrokeWidth();
		if (defaultWidth>0) {
			stroke = new BasicStroke((float)defaultWidth);
			usingDefaultStroke = true;
		}
		fillColor = defaultFillColor;
		this.group = defaultGroup; //initialize with current group and associated color
		if (defaultGroup>0)
			this.strokeColor = groupColor;
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
		where sx and sy are the starting screen coordinates.
		For rectangular rois, also a corner diameter may be specified to
		make it a rounded rectangle */
	public Roi(int sx, int sy, ImagePlus imp, int cornerDiameter) {
		setImage(imp);
		int ox=sx, oy=sy;
		if (ic!=null) {
			ox = ic.offScreenX2(sx);
			oy = ic.offScreenY2(sy);
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
		double defaultWidth = defaultStrokeWidth();
		if (defaultWidth>0) {
			stroke = new BasicStroke((float)defaultWidth);
			usingDefaultStroke = true;
		}
		fillColor = defaultFillColor;
		this.group = defaultGroup;
		if (defaultGroup>0)
			this.strokeColor = groupColor;
	}

	/** Creates a rectangular ROI. */
	public static Roi create(double x, double y, double width, double height) {
		return new Roi(x, y, width, height);
	}

	/** Creates a rounded rectangular ROI. */
	public static Roi create(double x, double y, double width, double height, int cornerDiameter) {
		return new Roi(x, y, width, height, cornerDiameter);
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
			if (!isInteger(bounds.x) || !isInteger(bounds.y)) {
				cachedMask = null;
				width  = (int)Math.ceil(bounds.width);
				height = (int)Math.ceil(bounds.height);
			}
			bounds.x = x;
			bounds.y = y;
			if (this instanceof PolygonRoi) setIntBounds(bounds);
		}
	}

	/** Set the location of the ROI in image coordinates. */
	public void setLocation(double x, double y) {
		setLocation((int)x, (int)y);
		if (isInteger(x) && isInteger(y))
			return;
		if (bounds!=null) {
			if (!isInteger(x-bounds.x) || !isInteger(y-bounds.y)) {
				cachedMask = null;
				width  = (int)Math.ceil(bounds.x + bounds.width) - this.x;	//ensure that all pixels are inside
				height = (int)Math.ceil(bounds.y + bounds.height) - this.y;
			}
			bounds.x = x;
			bounds.y = y;
		} else {
			cachedMask = null;
			bounds = new Rectangle2D.Double(x, y, width, height);
		}
		if (this instanceof PolygonRoi) setIntBounds(bounds);
		subPixel = true;
	}

	public void translate(double dx, double dy) {
		boolean intArgs = (int)dx==dx && (int)dy==dy;
		if (subPixelResolution() || !intArgs) {
			Rectangle2D r = getFloatBounds();
			setLocation(r.getX()+dx, r.getY()+dy);
		} else {
			Rectangle r = getBounds();
			setLocation(r.x+(int)dx, r.y+(int)dy);
		}
	}

	/** Sets the ImagePlus associated with this ROI.
	 *  <code>imp</code> may be null to remove the association to an image. */
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
		ImagePlus imp = this.imp;
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
		double perimeter = 2.0*width*pw + 2.0*height*ph;
		if (cornerDiameter > 0) {      //using Ramanujan's approximation for the circumference of an ellipse
			double a = 0.5*Math.min(cornerDiameter, width)*pw;
			double b = 0.5*Math.min(cornerDiameter, height)*ph;
			 perimeter += Math.PI*(3*(a + b) - Math.sqrt((3*a + b)*(a + 3*b))) -4*(a+b);
		}
		return perimeter;
	}

	/** Returns Feret's diameter, the greatest distance between
		any two points along the ROI boundary. */
	public double getFeretsDiameter() {
		double[] a = getFeretValues();
		return a!=null?a[0]:0.0;
	}

	/** Returns an array with the following values:
	 *  <br>[0] "Feret" (maximum caliper width)
	 *  <br>[1] "FeretAngle" (angle of diameter with maximum caliper width, between 0 and 180 deg)
	 *  <br>[2] "MinFeret" (minimum caliper width)
	 *  <br>[3][4] , "FeretX" and "FeretY", the X and Y coordinates of the starting point
	 *  (leftmost point) of the maximum-caliper-width diameter.
	 *  <br>[5-7] reserved
	 *  <br>All these values and point coordinates are in calibrated image coordinates.
	 *  <p>
	 *  The following array elements are end points of the maximum and minimum caliper diameter,
	 *  in unscaled image pixel coordinates:
	 *  <br>[8][9]   "FeretX1", "FeretY1"; unscaled versions of "FeretX" and "FeretY"
	 *  (subclasses may use any end of the diameter, not necessarily the left one)
	 *  <br>[10][11] "FeretX2", "FeretY2", end point of the maxium-caliper-width diameter.
	 *  Both of these points are vertices of the convex hull.
	 *  <br> The final four array elements are the starting and end points of the minimum caliper width,
	 *  <br>[12],[13] "MinFeretX", "MinFeretY", and
	 *  <br>[14],[15] "MinFeretX2", "MinFeretY2". These two pooints are not sorted by x,
	 *  but the first point point (MinFeretX, MinFeretY) is guaranteed to be a vertex of the convex hull,
	 *  while second point (MinFeretX2, MinFeretY2) usually is not a vertex point but at a
	 *  boundary line of the convex hull. */
	public double[] getFeretValues() {
		double pw=1.0, ph=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
		}

		FloatPolygon poly = getFloatConvexHull();
		if (poly==null || poly.npoints==0) return null;

		double[] a = new double[FERET_ARRAYSIZE];
		// calculate maximum Feret diameter: largest distance between any two points
		int p1=0, p2=0;
		double diameterSqr = 0.0;  //square of maximum Feret diameter
		for (int i=0; i<poly.npoints; i++) {
			for (int j=i+1; j<poly.npoints; j++) {
				double dx = (poly.xpoints[i] - poly.xpoints[j])*pw;
				double dy = (poly.ypoints[i] - poly.ypoints[j])*ph;
				double dsqr = dx*dx + dy*dy;
				if (dsqr>diameterSqr) {diameterSqr=dsqr; p1=i; p2=j;}
			}
		}
        if (poly.xpoints[p1] > poly.xpoints[p2]) {
            int p2swap = p1; p1 = p2; p2 = p2swap;
        }
		double xf1=poly.xpoints[p1], yf1=poly.ypoints[p1];
		double xf2=poly.xpoints[p2], yf2=poly.ypoints[p2];
		double angle = (180.0/Math.PI)*Math.atan2((yf1-yf2)*ph, (xf2-xf1)*pw);
		if (angle < 0.0)
			angle += 180.0;
		a[0] = Math.sqrt(diameterSqr);
		a[1] = angle;
		a[3] = xf1; a[4] = yf1;
		{ int i = FERET_ARRAY_POINTOFFSET;     //array elements 8-11 are start and end points of max Feret diameter
			a[i++] = poly.xpoints[p1]; a[i++] = poly.ypoints[p1];
			a[i++] = poly.xpoints[p2]; a[i++] = poly.ypoints[p2];
		}

		// Calculate minimum Feret diameter:
		// For all pairs of points on the convex hull:
		//   Get the point with the largest distance from the line between these two points
		//   Of all these pairs, take the one where the distance is the lowest
		// The following code requires a counterclockwise convex hull with no duplicate points
		double x0 = poly.xpoints[poly.npoints-1];
		double y0 = poly.ypoints[poly.npoints-1];
		double minFeret = Double.MAX_VALUE;
		double[] xyEnd = new double[4];        //start and end points of the minFeret diameter, uncalibrated
		double[] xyEi  = new double[4];        //intermediate values of xyEnd
		for (int i=0; i<poly.npoints; i++) {   //find caliper width for one side of calipers touching points i-1, i
			double xprev = x0;
			double yprev = y0;
			x0 = poly.xpoints[i];
			y0 = poly.ypoints[i];
			double xnorm = (y0 - yprev) * ph;
			double ynorm = (xprev - x0) * pw;
			double normalizationFactor = 1/Math.sqrt(xnorm*xnorm + ynorm*ynorm);
			xnorm *= normalizationFactor * pw; //normalized vector perpendicular to line between i-1, i; * scale factor for product below
			ynorm *= normalizationFactor * ph;
			double maxDist = 0;
			for (int j=0; j<poly.npoints; j++) {
				double x1 = poly.xpoints[j];
				double y1 = poly.ypoints[j];
				double dx = x1 - x0;
				double dy = y1 - y0;
				double dist = dx*xnorm + dy*ynorm;
				if (dist > maxDist) {
					maxDist = dist;
					xyEi[0] = x1;
					xyEi[1] = y1;
					xyEi[2] = xyEi[0] - (xnorm/pw * dist)/pw;
					xyEi[3] = xyEi[1] - (ynorm/ph * dist)/ph;
				}
			}
			if (maxDist < minFeret) {
				minFeret = maxDist;
				System.arraycopy(xyEi, 0, xyEnd, 0, 4);
			}
		}
		a[2] = minFeret;
		System.arraycopy(xyEnd, 0, a, FERET_ARRAY_POINTOFFSET+4, 4);    //a[12]-a[15] are minFeretX, Y, X2, Y2
		return a;
	}

	/** Returns the convex hull of this Roi as a Polygon with integer coordinates
	 *  by rounding the floating-point values.
	 *  Coordinates of the convex hull are image pixel coordinates. */
	public Polygon getConvexHull() {
		FloatPolygon fp = getFloatConvexHull();
		return new Polygon(toIntR(fp.xpoints), toIntR(fp.ypoints), fp.npoints);
	}

	/** Returns the convex hull of this Roi as a FloatPolygon.
	 *  Coordinates of the convex hull are image pixel coordinates. */
	public FloatPolygon getFloatConvexHull() {
		FloatPolygon fp = getFloatPolygon("");   //no duplicate closing points, no path-separating NaNs needed
		return fp == null ? null : fp.getConvexHull();
	}

	double getFeretBreadth(Shape shape, double angle, double x1, double y1, double x2, double y2) {
		double cx = x1 + (x2-x1)/2;
		double cy = y1 + (y2-y1)/2;
		AffineTransform at = new AffineTransform();
		at.rotate(angle*Math.PI/180.0, cx, cy);
		Shape s = at.createTransformedShape(shape);
		Rectangle2D r = s.getBounds2D();
		return Math.min(r.getWidth(), r.getHeight());
	}

	/** Returns this selection's bounding rectangle. */
	public Rectangle getBounds() {
		return new Rectangle(x, y, width, height);
	}

	/** Returns this selection's bounding rectangle (with subpixel accuracy). */
	public Rectangle2D.Double getFloatBounds() {
		if (bounds!=null)
			return new Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height);
		else
			return new Rectangle2D.Double(x, y, width, height);
	}

	/** Sets the bounds of rectangular, oval or text selections.
	 *  Note that for these types, subpixel resolution is ignored,
	 *  and the x,y values are rounded down, the width and height values rounded up.
	 *  Do not use for other ROI types since their width and height are results of
	 *  a calculation.
	 *  For translating ROIs, use setLocation. */
	public void setBounds(Rectangle2D.Double b) {
		if (!(type==RECTANGLE||type==OVAL||(this instanceof TextRoi)))
			return;
		this.x = (int)b.x;
		this.y = (int)b.y;
		this.width = (int)Math.ceil(b.width);
		this.height = (int)Math.ceil(b.height);
		bounds = new Rectangle2D.Double(b.x, b.y, b.width, b.height);
		cachedMask = null;
	}

	/** Sets the integer boundaries x, y, width, height from given sub-pixel
	 *  boundaries, such that all points are within the integer bounding rectangle.
	 *  For open line selections and (multi)Point Rois, note that integer Roi
	 *  coordinates correspond to the center of the 1x1 rectangle enclosing a pixel.
	 *  Points at the boundary of such a rectangle are counted for the higher x or y
	 *  value, in agreement to how (poly-)line or PointRois are displayed at the
	 *  screen at high zoom levels. (For lines and points, it should include all
	 *  pixels affected by 'draw' */
	void setIntBounds(Rectangle2D.Double bounds) {
		if (useLineSubpixelConvention()) { //for PointRois & open lines, ensure the 'draw' area is enclosed
			x = (int)Math.floor(bounds.x + 0.5);
			y = (int)Math.floor(bounds.y + 0.5);
			width  = (int)Math.floor(bounds.x + bounds.width + 1.5)  - x;
			height = (int)Math.floor(bounds.y + bounds.height + 1.5) - y;
		} else {  //for area Rois, the subpixel bounds must be enclosed in the int bounds
			x = (int)Math.floor(bounds.x);
			y = (int)Math.floor(bounds.y);
			width  = (int)Math.ceil(bounds.x + bounds.width)  - x;
			height = (int)Math.ceil(bounds.y + bounds.height) - y;
		}
	}

	/**
	* @deprecated
	* replaced by getBounds()
	*/
	public Rectangle getBoundingRect() {
		return getBounds();
	}

	/** Returns the outline of this selection as a Polygon.
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

	/** Returns the outline of this selection as a FloatPolygon */
	public FloatPolygon getFloatPolygon() {
		if (cornerDiameter>0) {  // Rounded Rectangle
			ShapeRoi s = new ShapeRoi(this);
			return s.getFloatPolygon();
		} else if (subPixelResolution() && bounds!=null) {
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

	/** Returns the outline in image pixel coordinates,
	 *  where options may include "close" to add a point to close the outline
	 *  if this is an area roi and the outline is not closed yet.
	 *  (For ShapeRois, "separate" inserts NaN values between subpaths). */
	public FloatPolygon getFloatPolygon(String options) {
		options = options.toLowerCase();
		boolean addPointForClose = options.indexOf("close") >= 0;
		FloatPolygon fp = getFloatPolygon();
		int n = fp.npoints;
		if (isArea() && n > 1) {
			boolean isClosed = fp.xpoints[0] == fp.xpoints[n-1] && fp.ypoints[0] == fp.ypoints[n-1];
			if (addPointForClose && !isClosed)
				fp.addPoint(fp.xpoints[0], fp.ypoints[0]);
			else if (!addPointForClose && isClosed)
				fp.npoints--;
		}
		return fp;
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
		if (npoints<2)
			return p;
		if (Math.abs(interval)<0.01) {
			IJ.error("Interval must be >= 0.01");
			return p;
		}
		
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

	/** Returns the coordinates of the pixels inside this ROI as an array of Points.
	 * @see #getContainedFloatPoints
	 * @see #iterator
	 */
	public Point[] getContainedPoints() {
		Roi roi = this;
		if (isLine())
			roi = convertLineToArea(this);
		ImageProcessor mask = roi.getMask();
		Rectangle bounds = roi.getBounds();
		ArrayList points = new ArrayList();
		for (int y=0; y<bounds.height; y++) {
			for (int x=0; x<bounds.width; x++) {
				if (mask==null || mask.getPixel(x,y)!=0)
					points.add(new Point(roi.x+x,roi.y+y));
			}
		}
		return (Point[])points.toArray(new Point[points.size()]);
	}

	/** Returns the coordinates of the pixels inside this ROI as a FloatPolygon.
	 * @see #getContainedPoints
	 * @see #iterator
	 */
	public FloatPolygon getContainedFloatPoints() {
		Roi roi2 = this;
		if (isLine()) {
			if (getStrokeWidth()<=1)
				return roi2.getInterpolatedPolygon();
			else
				roi2 = convertLineToArea(this);
		}
		ImageProcessor mask = roi2.getMask();
		Rectangle bounds = roi2.getBounds();
		FloatPolygon points = new FloatPolygon();
		for (int y=0; y<bounds.height; y++) {
			for (int x=0; x<bounds.width; x++) {
				if (mask==null || mask.getPixel(x,y)!=0)
					points.addPoint((float)(bounds.x+x),(float)(bounds.y+y));
			}
		}
		return points;
	}

	/**
	 * <pre>
	 * Calculates intersections of a line segment with a circle
	 * Author N.Vischer
	 * ax, ay, bx, by: points A and B of line segment
	 * cx, cy, rad: Circle center and radius.
	 * ignoreOutside: if true, ignores intersections outside the line segment A-B
	 * Returns an array of 0, 2 or 4 coordinates (for 0, 1, or 2 intersection
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
			if (!usingDefaultStroke)
				r.setStroke(getStroke());
			Color strokeColor2 = getStrokeColor();
			r.setFillColor(getFillColor());
			r.setStrokeColor(strokeColor2);
			r.imageID = getImageID();
			r.listenersNotified = false;
			if (bounds!=null)
				r.bounds = (Rectangle2D.Double)bounds.clone();
			return r;
		}
		catch (CloneNotSupportedException e) {return null;}
	}

	/** Aborts constructing or modifying the roi (called by the ImageJ class on escape) */
	public void abortModification(ImagePlus imp) {
		if (state == CONSTRUCTING) {
			setImage(null);
			if (imp!=null) {
				Roi savedPreviousRoi = getPreviousRoi();
				imp.setRoi(previousRoi!=null && previousRoi.getImage() == imp ? previousRoi : null);
				setPreviousRoi(savedPreviousRoi);     //(overrule saving this aborted roi as previousRoi)
			}
		} else if (state==MOVING)
			move(previousSX, previousSY);       //move back to starting point
		else if (state == MOVING_HANDLE)
			moveHandle(previousSX, previousSY); //move handle back to starting point
		state = NORMAL;
	}

	protected void grow(int sx, int sy) {
		if (clipboard!=null) return;
		int xNew = ic.offScreenX2(sx);
		int yNew = ic.offScreenY2(sy);
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
		int ox = ic.offScreenX2(sx);
		int oy = ic.offScreenY2(sy);
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
		if (constrain) {  // constrain translation in 90deg steps
			int dx = sx - previousSX;
			int dy = sy - previousSY;
			if (Math.abs(dx) > Math.abs(dy))
				dy = 0;
			else
				dx = 0;
			sx = previousSX + dx;
			sy = previousSY + dy;
		}
		int xNew = ic.offScreenX(sx);
		int yNew = ic.offScreenY(sy);
		int dx = xNew - startX;
		int dy = yNew - startY;
		if (dx==0 && dy==0)
			return;
		x += dx;
		y += dy;
		if (bounds!=null)
			setLocation(bounds.x + dx, bounds.y + dy);
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
	}

	/** Nudge ROI one pixel on arrow key press. */
	public void nudge(int key) {
		if (WindowManager.getActiveWindow() instanceof RoiManager)
			return;
		if (bounds != null && (!isInteger(bounds.x) || !isInteger(bounds.y)))
			cachedMask = null;
		switch(key) {
			case KeyEvent.VK_UP:
				this.y--;
				if (this.y<0 && (type!=RECTANGLE||clipboard==null))
					this.y = 0;
				break;
			case KeyEvent.VK_DOWN:
				this.y++;
				if ((this.y+height)>=yMax && (type!=RECTANGLE||clipboard==null))
					this.y = yMax-height;
				break;
			case KeyEvent.VK_LEFT:
				this.x--;
				if (this.x<0 && (type!=RECTANGLE||clipboard==null))
					this.x = 0;
				break;
			case KeyEvent.VK_RIGHT:
				this.x++;
				if ((this.x+width)>=xMax && (type!=RECTANGLE||clipboard==null))
					this.x = xMax-width;
				break;
		}
		updateClipRect();
		if (type==POINT)
			imp.draw();
		else
			imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = this.x; oldY = this.y;
		bounds = null;
		setLocation(this.x, this.y);
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
				notifyListeners(RoiListener.MODIFIED);
				break;
			case KeyEvent.VK_DOWN:
				height++;
				if ((y+height) > yMax) height = yMax-y;
				notifyListeners(RoiListener.MODIFIED);
				break;
			case KeyEvent.VK_LEFT:
				width--;
				if (width<1) width = 1;
				notifyListeners(RoiListener.MODIFIED);
				break;
			case KeyEvent.VK_RIGHT:
				width++;
				if ((x+width) > xMax) width = xMax-x;
				notifyListeners(RoiListener.MODIFIED);
				break;
		}
		updateClipRect();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x; oldY = y;
		cachedMask = null;
		showStatus();
		notifyListeners(RoiListener.MOVED);
	}

	// Finds the union of current and previous roi
	protected void updateClipRect() {
		clipX = (x<=oldX)?x:oldX;
		clipY = (y<=oldY)?y:oldY;
		clipWidth = ((x+width>=oldX+oldWidth)?x+width:oldX+oldWidth) - clipX + 1;
		clipHeight = ((y+height>=oldY+oldHeight)?y+height:oldY+oldHeight) - clipY + 1;
		int handleSize = getHandleSize();
		double mag = ic!=null?ic.getMagnification():1;
		int m = mag<1.0?(int)(handleSize/mag):handleSize;
		m += clipRectMargin();
		double strokeWidth = getStrokeWidth();
		if (strokeWidth==0.0)
			strokeWidth = defaultStrokeWidth();
		m = (int)(m+strokeWidth*2);
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

	public void draw(Graphics g) {
		Color color =  strokeColor!=null?strokeColor:ROIColor;
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
		setRenderingHint(g2d);
		if (cornerDiameter>0) {
			int sArcSize = (int)Math.round(cornerDiameter*mag);
			if (fillColor!=null) {
				g.fillRoundRect(sx1, sy1, sw, sh, sArcSize, sArcSize);
				if (strokeColor!=null) {
					g.setColor(strokeColor);
					g.drawRoundRect(sx1, sy1, sw, sh, sArcSize, sArcSize);
				}
			} else
				g.drawRoundRect(sx1, sy1, sw, sh, sArcSize, sArcSize);
		} else {
			if (fillColor!=null) {
				if (!overlay && isActiveOverlayRoi()) {
					g.setColor(Color.cyan);
					g.drawRect(sx1, sy1, sw, sh);
				} else {
					if (!(this instanceof TextRoi)) {
						g.fillRect(sx1, sy1, sw, sh);
						if (strokeColor!=null) {
							g.setColor(strokeColor);
							g.drawRect(sx1, sy1, sw, sh);
						}
					} else
						g.drawRect(sx1, sy1, sw, sh);
				}
			} else
				g.drawRect(sx1, sy1, sw, sh);
		}
		if (clipboard==null && !overlay) {
			drawHandle(g, sx1, sy1);
			drawHandle(g, sx2, sy1);
			drawHandle(g, sx3, sy1);
			drawHandle(g, sx3, sy2);
			drawHandle(g, sx3, sy3);
			drawHandle(g, sx2, sy3);
			drawHandle(g, sx1, sy3);
			drawHandle(g, sx1, sy2);
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

	private static double defaultStrokeWidth() {
		double defaultWidth = defaultStrokeWidth;
		double guiScale = Prefs.getGuiScale();
		if (defaultWidth<=1 && guiScale>1.0) {
			defaultWidth = guiScale;
			if (defaultWidth<1.5) defaultWidth = 1.5;
		}
		return defaultWidth;
	}

	/** Returns the current handle size. */
	public int getHandleSize() {
		if (handleSize>=0)
			return handleSize;
		else
			return getDefaultHandleSize();
	}

	/** Sets the current handle size. */
	public void setHandleSize(int size) {
		if (size>=0 && ((size&1)==0))
			size++; // add 1 if odd
		handleSize = size;
		if (imp!=null)
			imp.draw();
	}

	/** Returns the default handle size. */
	public static int getDefaultHandleSize() {
		if (defaultHandleSize>0)
			return defaultHandleSize;
		double defaultWidth = defaultStrokeWidth();
		int size = 7;
		if (defaultWidth>1.5) size=9;
		if (defaultWidth>=3) size=11;
		if (defaultWidth>=4) size=13;
		if (defaultWidth>=5) size=15;
		if (defaultWidth>=11) size=(int)defaultWidth;
		defaultHandleSize = size;
		return defaultHandleSize;
	}

	public static void resetDefaultHandleSize() {
		defaultHandleSize = 0;
	}

	void drawHandle(Graphics g, int x, int y) {
		int threshold1 = 7500;
		int threshold2 = 1500;
		double size = (this.width*this.height)*this.mag*this.mag;
		if (this instanceof Line) {
			size = ((Line)this).getLength()*this.mag;
			threshold1 = 150;
			threshold2 = 50;
		} else {
			if (state==CONSTRUCTING && !(type==RECTANGLE||type==OVAL))
				size = threshold1 + 1;
		}
		int width = 7;
		int x0=x, y0=y;
		if (size>threshold1) {
			x -= 3;
			y -= 3;
		} else if (size>threshold2) {
			x -= 2;
			y -= 2;
			width = 5;
		} else {
			x--; y--;
			width = 3;
		}
		int inc = getHandleSize() - 7;
		width += inc;
		x -= inc/2;
		y -= inc/2;
		g.setColor(Color.black);
		if (width<3) {
			g.fillRect(x0,y0,1,1);
			return;
		}
		g.fillRect(x++,y++,width,width);
		g.setColor(handleColor);
		width -= 2;
		g.fillRect(x,y,width,width);
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

	/** Returns whether the center of pixel (x,y) is contained in the Roi.
	 *  The position of a pixel center determines whether a pixel is selected.
	 *  Points exactly at the left (right) border are considered outside (inside);
	 *  points exactly on horizontal borders are considered outside (inside) at the border
	 *  with the lower (higher) y. This convention is opposite to that of the java.awt.Shape class. */
	public boolean contains(int x, int y) {
		Rectangle r = new Rectangle(this.x, this.y, width, height);
		boolean contains = r.contains(x, y);
		if (cornerDiameter==0 || contains==false)
			return contains;
		RoundRectangle2D rr = new RoundRectangle2D.Double(this.x, this.y, width, height, cornerDiameter, cornerDiameter);
		return rr.contains(x+0.4999, y+0.4999);
	}

	/** Returns whether coordinate (x,y) is contained in the Roi.
	 *  Note that the coordinate (0,0) is the top-left corner of pixel (0,0).
	 *  Use contains(int, int) to determine whether a given pixel is contained in the Roi. */
	public boolean containsPoint(double x, double y) {
		boolean contains = false;
		if (bounds == null)
			contains = x>=this.x && y>=this.y && x<this.x+width && y<this.y+height;
		if (cornerDiameter==0 || contains==false)
			return contains;
		RoundRectangle2D rr = new RoundRectangle2D.Double(this.x, this.y, width, height, cornerDiameter, cornerDiameter);
		return rr.contains(x, y);
	}

	/** Returns the inverted roi, or null if this is not an area roi or cannot be converted to a ShapeRoi.
	 *  If imp is not given, assumes a rectangle of size 2e9*2e9 for the boundary. */
	public Roi getInverse(ImagePlus imp) {
		if (!isArea()) return null;
		Roi fullImage = (imp == null) ? new Roi(0,0, 2000000000, 2000000000) : new Roi(0,0, imp.getWidth(), imp.getHeight());
		ShapeRoi s = (this instanceof ShapeRoi) ? (ShapeRoi)(this.clone()) : new ShapeRoi(this);
		if (s == null) return null;
		ShapeRoi inverse = s.xor(new ShapeRoi(fullImage));
		return inverse.trySimplify();
	}

	/** Returns a handle number if the specified screen coordinates are
		inside or near a handle, otherwise returns -1. */
	public int isHandle(int sx, int sy) {
		if (clipboard!=null || ic==null) return -1;
		double mag = ic.getMagnification();
		int margin = IJ.getScreenSize().width>1280?5:3;
		int size = getHandleSize()+margin;
		int halfSize = size/2;
		double x = getXBase();
		double y = getYBase();
		double width = getFloatWidth();
		double height = getFloatHeight();
		int sx1 = screenXD(x) - halfSize;
		int sy1 = screenYD(y) - halfSize;
		int sx3 = screenXD(x+width) - halfSize;
		int sy3 = screenYD(y+height) - halfSize;
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
		previousSX = sx;
		previousSY = sy;
		activeHandle = handle;
	}

	protected void handleMouseDown(int sx, int sy) {
		if (state==NORMAL && ic!=null) {
			state = MOVING;
			previousSX = sx;
			previousSY = sy;
			startX = offScreenX(sx);
			startY = offScreenY(sy);
			startXD = offScreenXD(sx);
			startYD = offScreenYD(sy);
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
		Roi originalRoi = (Roi)previousRoi.clone();
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
		Roi roi2 = s1.trySimplify();
		if (roi2 == null) return;
		if (roi2!=null)
			roi2.copyAttributes(previousRoi);
		imp.setRoi(roi2);
		RoiManager rm = RoiManager.getRawInstance();		
		if (rm!=null && rm.getCount()>0) {
			Roi[] rois = rm.getSelectedRoisAsArray();
			if (rois!=null && rois.length==1 && rois[0].equals(originalRoi))
				rm.runCommand("update");
		}
		setPreviousRoi(previous);
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
			double v = ip.getPixelValue(this.x,this.y);
			int digits = (imp.getType()==ImagePlus.GRAY8||imp.getType()==ImagePlus.GRAY16)?0:2;
			value = ", value="+IJ.d2s(v,digits);
		} else
			value = "";
		Calibration cal = imp.getCalibration();
		String size;
		if (cal.scaled())
			size = ", w="+IJ.d2s(width*cal.pixelWidth)+" ("+width+"), h="+IJ.d2s(height*cal.pixelHeight)+" ("+height+")";
		else
			size = ", w="+width+", h="+height;
		IJ.showStatus(imp.getLocationAsString(this.x,this.y)+size+value);
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
			Roi croi = clipboard!=null?clipboard.getRoi():null;
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

	/** Returns the default stroke width. */
	public static double getDefaultStrokeWidth() {
		return defaultStrokeWidth;
	}

	/** Sets the default stroke width. */
	public static void setDefaultStrokeWidth(double width) {
		defaultStrokeWidth = width<0.0?0.0:width;
		resetDefaultHandleSize();
	}

	/** Returns the group value assigned to newly created ROIs. */
	public static int getDefaultGroup() {
		return defaultGroup;
	}

	/** Sets the group value assigned to newly created ROIs, and also
	 * sets the default ROI color to the group color. Set to zero to not
	 * have a default group and to use the default ROI color.
	 * @see #setGroup
	 * @see #getGroup
	 * @see #getGroupColor
	*/
	public static void setDefaultGroup(int group) {
		if (group<0 || group>255)
			throw new IllegalArgumentException("Invalid group: "+group);
		defaultGroup = group;
		groupColor = getGroupColor(group);
	}

	/** Returns the group attribute of this ROI. */
	public int getGroup() {
		return this.group;
	}

	/** Returns the group name associtated with the specified group. */
	public static String getGroupName(int groupNumber) {
		if (groupNumber<1 || groupNumber>255)
			return null;
		if (groupNames==null && groupNamesString==null)
			return null;
		if (groupNames==null)
			groupNames = groupNamesString.split(",");
		if (groupNumber>groupNames.length)
			return null;
		String name = groupNames[groupNumber-1];
		if (name==null)
			return null;
		return name.length()>0?name:null;
	}

	public static synchronized void setGroupName(int groupNumber, String name) {
		if (groupNumber<1 || groupNumber>255)
			return;
		if (groupNamesString==null && groupNames==null)
			groupNames = new String[groupNumber];
		if (groupNames==null)
			groupNames = groupNamesString.split(",");
		if (groupNumber>groupNames.length) {
			String[] temp = new String[groupNumber];
			for (int i=0; i<groupNames.length; i++)
				temp[i] = groupNames[i];
			groupNames = temp;
		}
		//IJ.log("setGroupName: "+groupNumber+"  "+name+"  "+groupNames.length);
		groupNames[groupNumber-1] = name;
		groupNamesChanged = true;
	}

	public static synchronized void saveGroupNames() {
		if (groupNames==null)
			return;
		StringBuilder sb = new StringBuilder(groupNames.length*12);
		for (int i=0; i<groupNames.length; i++) {
			String name = groupNames[i];
			if (name==null)
				name = "";
			sb.append(name);
			if (i<groupNames.length-1)
				sb.append(",");
		}
		groupNamesString = sb.toString();
		groupNames = null;
		Prefs.set(NAMES_KEY, groupNamesString);
	}

	/** Returns the group names as a comma-delimeted string. */
	public static String getGroupNames() {
		if (groupNamesChanged && groupNames!=null)
			saveGroupNames();
		groupNamesChanged = false;
		return groupNamesString;
	}

	/** Sets the group names from a comma-delimeted string. */
	public static void setGroupNames(String names) {
		groupNamesString = names;
		groupNames = null;
	}

	/** Sets the group of this Roi, and updates stroke color accordingly. */
	public void setGroup(int group) {
		if (group<0 || group>255)
			throw new IllegalArgumentException("Invalid group: "+group);
		if (group>0)
			setStrokeColor(getGroupColor(group));
		if (group==0 && this.group>0)
			setStrokeColor(null);			
		this.group = group;
		if (imp!=null) // Update Roi Color in the GUI
			imp.draw();
	}

	/** Retrieves color associated to a given roi group. */
	private static Color getGroupColor(int group) {
		Color color = ROIColor; // default ROI color
		if (group>0) { // read Glasbey Lut
			if (glasbeyLut==null) {
				String path = IJ.getDir("luts")+"Glasbey.lut";
				glasbeyLut = LutLoader.openLut("noerror:"+path);
				if (glasbeyLut==null) {
					path = IJ.getDir("luts")+"glasbey.lut";
					glasbeyLut = LutLoader.openLut("noerror:"+path);
				}
				if (glasbeyLut==null)
					IJ.log("LUT not found: "+path);
			}
			if (glasbeyLut!=null)
				color = new Color(glasbeyLut.getRGB(group));
		}
		return color;
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

	/** Sets the color used by this ROI to draw its outline.
	 * This color, if not null, overrides the global color set
	 * by the static setColor() method. Set the stroke color
	 * after setting the fill color to both fill and outline
	 * the ROI.
	 * @see #getStrokeColor
	 * @see #setStrokeWidth
	 * @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
	 */
	public void setStrokeColor(Color c) {
		strokeColor = c;
		//if (getType()==TRACED_ROI && c!=null && fillColor!=null)
		//	throw new IllegalArgumentException();
	}
	
	/** Returns the the color used to draw the ROI outline or null if the default color is being used.
	 * @see #setStrokeColor(Color)
	 */
	public Color getStrokeColor() {
		return	strokeColor;
	}

	/** Sets the default stroke color. */
	public static void setDefaultColor(Color color) {
		 defaultColor = color;
	}

	/** Sets the fill color used to display this ROI, or set to null to display it transparently.
	 * @see #getFillColor
	 * @see #setStrokeColor
	 */
	public void setFillColor(Color color) {
		fillColor = color;
		if (fillColor!=null && isArea())
			strokeColor=null;
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

	public void setAntiAlias(boolean antiAlias) {
		this.antiAlias = antiAlias;
	}

	public boolean getAntiAlias() {
		return antiAlias;
	}

	protected void setRenderingHint(Graphics2D g2d) {
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			antiAlias?RenderingHints.VALUE_ANTIALIAS_ON:RenderingHints.VALUE_ANTIALIAS_OFF);
	}

	/** Copy the attributes (outline color, fill color, outline width)
		of	'roi2' to the this selection. */
	public void copyAttributes(Roi roi2) {
		this. strokeColor = roi2.strokeColor;
		this.fillColor = roi2.fillColor;
		this.setStrokeWidth(roi2.getStrokeWidth());
		this.setName(roi2.getName());
		this.group = roi2.group;
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
	 * @see #setDefaultStrokeWidth(double)
	 * @see #setUnscalableStrokeWidth(double)	 
	 * @see #setStrokeColor(Color)
	 * @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
	 */
	public void setStrokeWidth(float strokeWidth) {
		if (strokeWidth<0f)
			strokeWidth = 0f;
		if (strokeWidth==0f && usingDefaultStroke)
			return;
		if (strokeWidth>0f) {
			scaleStrokeWidth = true;
			usingDefaultStroke = false;
		}
		boolean notify = listeners.size()>0 && isLine() && getStrokeWidth()!=strokeWidth;
		if (strokeWidth==0f)
			this.stroke = null;
		else if (wideLine)
			this.stroke = new BasicStroke(strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
		else
			this.stroke = new BasicStroke(strokeWidth);
		//if (strokeWidth>1f)
		//	fillColor = null;
		if (notify)
			notifyListeners(RoiListener.MODIFIED);
	}

	/** This is a version of setStrokeWidth() that accepts a double argument. */
	public void setStrokeWidth(double strokeWidth) {
		setStrokeWidth((float)strokeWidth);
	}

	/** Sets the width of the line used to draw this ROI and
	 * prevents the width from increasing when the image
	 * is zoomed.
	*/
	public void setUnscalableStrokeWidth(double strokeWidth) {
		setStrokeWidth((float)strokeWidth);
		scaleStrokeWidth = false;

	}

	/** Returns the line width. */
	public float getStrokeWidth() {
		return (stroke!=null&&!usingDefaultStroke)?stroke.getLineWidth():0f;
	}

	/** Sets the Stroke used to draw this ROI. */
	public void setStroke(BasicStroke stroke) {
		this.stroke = stroke;
		if (stroke!=null)
			usingDefaultStroke = false;
	}

	/** Returns the Stroke used to draw this ROI, or null if no Stroke is used. */
	public BasicStroke getStroke() {
		if (usingDefaultStroke)
			return null;
		else
			return stroke;
	}

	/** Returns 'true' if the stroke width is scaled as images are zoomed. */
	public boolean getScaleStrokeWidth() {
		return scaleStrokeWidth;
	}

	protected BasicStroke getScaledStroke() {
		if (ic==null || usingDefaultStroke || !scaleStrokeWidth)
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
		hyperstackPosition = false;
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
		hyperstackPosition = true;
	}

	/** Returns 'true' if setPosition(C,Z,T) has been called. */
	public boolean hasHyperStackPosition() {
		return hyperstackPosition;
	}

	/** Sets the position of this ROI based on the stack position of the specified image.  */
	public void setPosition(ImagePlus imp ) {
		if (imp==null)
			return;
		if (imp.isHyperStack()) {
			int channel = imp.getDisplayMode()==IJ.COMPOSITE?0:imp.getChannel();
			setPosition(channel, imp.getSlice(), imp.getFrame());
		} else if (imp.getStackSize()>1)
			setPosition(imp.getCurrentSlice());
		else
			setPosition(0);
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
		return slice==0&&!hyperstackPosition?position:slice;
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
		prototypeOverlay.setLabelFont(overlay.getLabelFont(), overlay.scalableLabels());
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
		return type>=LINE && type<=ANGLE;
	}


	/** Return 'true' if this is a line or point selection. */
	public boolean isLineOrPoint() {
		return isLine() || type==POINT;
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
			case POLYGON: s="Polygon";
				if (this instanceof EllipseRoi) s="Ellipse";
				if (this instanceof RotatedRectRoi) s="Rotated Rectangle";
				break;
			case FREEROI: s="Freehand"; break;
			case TRACED_ROI: s="Traced"; break;
			case POLYLINE: s="Polyline"; break;
			case FREELINE: s="Freeline"; break;
			case ANGLE: s="Angle"; break;
			case LINE: s=this instanceof Arrow ? "Arrow" : "Straight Line"; break;
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

	/** @deprecated Drawoffset is not used any more. */
	@Deprecated
	public boolean getDrawOffset() {
		return false;
	}

	/** @deprecated This method was previously used to draw lines and polylines shifted
	 *  by 0.5 pixels top the bottom and right, for better agreement with the
	 *  position used by ProfilePlot, with the default taken from
	 *  Prefs.subPixelResolution. Now the shift is independent of this
	 *  setting and only depends on the ROI type (area or line/point ROI). */
	@Deprecated
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

	/** Converts image canvas screen x coordinates to integer offscreen image pixel
	 *  coordinates, depending on whether this roi uses the line or area convention
	 *  for coordinates. */
	protected int offScreenX(int sx) {
		if (ic == null) return sx;
		return useLineSubpixelConvention() ? ic.offScreenX(sx) : ic.offScreenX2(sx);
	}

	/** Converts image canvas screen y coordinates to integer offscreen image pixel
	 *  coordinates, depending on whether this roi uses the line or area convention
	 *  for coordinates. */
	protected int offScreenY(int sy) {
		if (ic == null) return sy;
		return useLineSubpixelConvention() ? ic.offScreenY(sy) : ic.offScreenY2(sy);
	}

	/** Converts image canvas screen x coordinates to floating-point offscreen image pixel
	 *  coordinates, depending on whether this roi uses the line or area convention
	 *  for coordinates. */
	protected double offScreenXD(int sx) {
		if (ic == null) return sx;
		double offScreenValue = ic.offScreenXD(sx);
		if (useLineSubpixelConvention())
			offScreenValue -= 0.5;
		return offScreenValue;
	}

	/** Converts image canvas screen y coordinates to floating-point offscreen image pixel
	 *  coordinates, depending on whether this roi uses the line or area convention
	 *  for coordinates. */
	protected double offScreenYD(int sy) {
		if (ic == null) return sy;
		double offScreenValue = ic.offScreenYD(sy);
		if (useLineSubpixelConvention())
			offScreenValue -= 0.5;
		return offScreenValue;
	}

	/** Returns 'true' if this ROI uses for drawing the convention for
	 *  line and point ROIs, where the coordinates are with respect
	 *  to the pixel center.
	 *  Returns false for area rois, which have coordinates with respect to
	 *  the upper left corners of the pixels */
	protected boolean useLineSubpixelConvention() {
		return isLineOrPoint();
	}

	/** Returns whether a roi created interactively should have subpixel resolution,
	 *  (if the roi type supports it), i.e., whether the magnification is high enough */
	protected boolean magnificationForSubPixel() {
		return magnificationForSubPixel(getMagnification());
	}

	protected static boolean magnificationForSubPixel(double magnification) {
		return magnification > 1.5;
	}

	/**Converts an image pixel x (offscreen)coordinate to a screen x coordinate,
	 * taking the the line or area convention for coordinates into account */
	protected int screenXD(double ox) {
		if (ic==null) return (int)ox;
		if (useLineSubpixelConvention()) ox += 0.5;
		return ic!=null?ic.screenXD(ox):(int)ox;
	}

	/**Converts an image pixel y (offscreen)coordinate to a screen y coordinate,
	 * taking the the line or area convention for coordinates into account */
	protected int screenYD(double oy) {
		if (ic==null) return (int)oy;
		if (useLineSubpixelConvention()) oy += 0.5;
		return ic!=null?ic.screenYD(oy):(int)oy;
	}

	protected int screenX(int ox) {return screenXD(ox);}
	protected int screenY(int oy) {return screenYD(oy);}

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

	/** Returns whether a number is an integer */
	public static boolean isInteger(double x) {
		return x == (int)x;
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

	public ImageStatistics getStatistics() {
		Roi roi = this;
		ImageProcessor ip = null;
		if (imp!=null)
			ip = imp.getProcessor();
		boolean noImage = ip==null;
		Rectangle bounds = null;
		if (noImage) {
			roi = (Roi)this.clone();
			bounds = roi.getBounds();
			ip = new ByteProcessor(bounds.width, bounds.height);
			roi.setLocation(0, 0);
		}
		if (roi.isLine())
			roi = null;
		ip.setRoi(roi);
		ImageStatistics stats = ip.getStatistics();
		if (noImage) {
			stats.mean = stats.min = stats.max = Double.NaN;
			stats.xCentroid+=bounds.x; stats.yCentroid+=bounds.y;
		}
		ip.resetRoi();
		return stats;
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

	/** Returns the number of points in this selection; equivalent to getFloatPolygon().npoints. */
	public int size() {
		return getFloatPolygon().npoints;
	}

	/** Saves 'roi' so it can be restored later using Edit/Selection/Restore Selection. */
	public static void setPreviousRoi(Roi roi) {
		if (roi!=null) {
			previousRoi = (Roi)roi.clone();
			previousRoi.setImage(null);
		} else
			previousRoi = null;
	}

	/** Returns the Roi saved by setPreviousRoi(). */
	public static Roi getPreviousRoi() {
		return previousRoi;
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
		for (int n1=0; n1<nPoints; n1++) {
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

	/** Obsolete, replaced by Roi.convertLineToArea()
	 * @deprecated
	*/
	public Roi convertToPolygon() {
		return convertLineToArea(this);
	}

	/** Converts a line selection into an area (polygon or composite) selection.<br>
	 * Author: Michael Schmid
	*/
	public static Roi convertLineToArea(Roi line) {
		if (line==null || !line.isLine())
			throw new IllegalArgumentException("Line selection required");
		double lineWidth = line.getStrokeWidth();
		if (lineWidth<1.0)
			lineWidth = 1.0;
		Roi roi2 = null;
		if (line.getType()==Roi.LINE) {
			FloatPolygon p = ((Line)line).getFloatPoints();
			roi2 = new RotatedRectRoi(p.xpoints[0],p.ypoints[0],p.xpoints[1],p.ypoints[1],lineWidth);
			line.setStrokeWidth(lineWidth);
		} else {
			Rectangle bounds = line.getBounds();
			double width = bounds.x+bounds.width + lineWidth;
			double height = bounds.y+bounds.height + lineWidth;
			ByteProcessor ip = new ByteProcessor((int)Math.round(width), (int)Math.round(height));
			PolygonFiller polygonFiller = new PolygonFiller();
			//ip.setColor(255);
			double radius = lineWidth/2.0;
			FloatPolygon p = line.getFloatPolygon();
			int n = p.npoints;
			float[] xv = new float[4]; //vertex points of rectangle will be filled for each line segment
			float[] yv = new float[4];
			float[] xt = new float[3]; //vertex points of triangle will be filled between line segments
			float[] yt = new float[3];
			double dx1 = p.xpoints[1]-p.xpoints[0];
			double dy1 = p.ypoints[1]-p.ypoints[0];
			double l = length(dx1, dy1);
			dx1 = dx1/l;			   //unit vector along current line segment
			dy1 = dy1/l;
			double dx0 = dx1;
			double dy0 = dy1;
			double xfrom = p.xpoints[0] - 0.5*dx1;
			double yfrom = p.ypoints[0] - 0.5*dy1;
			//Overlay ovly = new Overlay();
			for (int i=1; i<n; i++) { //line segment from point i-1 ("from") to point i ("to")
				double xto = p.xpoints[i];
				double yto = p.ypoints[i];
				if (i==n-1) {
					xto += 0.5*dx1;
					yto += 0.5*dy1;
				}
				xv[0] = (float)(xfrom + radius*dy1);
				yv[0] = (float)(yfrom - radius*dx1);
				xv[1] = (float)(xfrom - radius*dy1);
				yv[1] = (float)(yfrom + radius*dx1);
				xv[2] = (float)(xto - radius*dy1);
				yv[2] = (float)(yto + radius*dx1);
				xv[3] = (float)(xto + radius*dy1);
				yv[3] = (float)(yto - radius*dx1);
				polygonFiller.setPolygon(xv, yv, 4, 0.5f, 0.5f); //offset 0.5 pxl: line vs area coordinate convention
				polygonFiller.fillByteProcessorMask(ip);
				//ovly.add(new PolygonRoi(xv,yv,Roi.POLYGON));
				if (i>1) {  //fill triangle to previous line segment
					boolean rightTurn=(dx1*dy0>dx0*dy1);
					xt[0] = (float)xfrom;
					yt[0] = (float)yfrom;
					if (rightTurn) {
						xt[1] = (float)(xfrom-radius*dy0);
						yt[1] = (float)(yfrom+radius*dx0);
						xt[2] = (float)(xfrom-radius*dy1);
						yt[2] = (float)(yfrom+radius*dx1);
						xt[0] += (float)(0.5*(radius*dy0+radius*dy1));  //extend triangle to avoid missing pixels (due to rounding errors)
						yt[0] -= (float)(0.5*(radius*dx0+radius*dx1));  //where it touches a rectangle
					} else {
						xt[1] = (float)(xfrom+radius*dy0);
						yt[1] = (float)(yfrom-radius*dx0);
						xt[2] = (float)(xfrom+radius*dy1);
						yt[2] = (float)(yfrom-radius*dx1);
						xt[0] -= (float)(0.5*(radius*dy0+radius*dy1));
						yt[0] += (float)(0.5*(radius*dx0+radius*dx1));
					}
					polygonFiller.setPolygon(xt, yt, 3, 0.5f, 0.5f);
					polygonFiller.fillByteProcessorMask(ip);
					//ovly.add(new PolygonRoi(xt,yt,Roi.POLYGON));
				}
				dx0 = dx1;
				dy0 = dy1;
				xfrom = xto;
				yfrom = yto;
				if (i<n-1) {
					dx1 = p.xpoints[i+1]-p.xpoints[i];
					dy1 = p.ypoints[i+1]-p.ypoints[i];
					l = length(dx1, dy1);
					dx1 = dx1/l;	   //unit vector along next line segment
					dy1 = dy1/l;
				}
			}
			//IJ.getImage().setOverlay(ovly);
			ip.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
			ThresholdToSelection tts = new ThresholdToSelection();
			roi2 = tts.convert(ip);
		}
		if (roi2==null)
			return null;
		transferProperties(line, roi2);
		roi2.setStrokeWidth(0);
		Color c = roi2.getStrokeColor();
		if (c!=null)  // remove any transparency
			roi2.setStrokeColor(new Color(c.getRed(),c.getGreen(),c.getBlue()));
		return roi2;
	}

	/** Returns the length of a vector with components dx, dy */
	static double length(double dx, double dy) {
		return Math.sqrt(dx*dx+dy*dy);
	}

	/** Used by PolygonRoi, Line, ShapeRoi etc. */
	static double sqr(double x) {return x*x; }

	private static void transferProperties(Roi roi1, Roi roi2) {
		if (roi1==null || roi2==null)
			return;
		roi2.setStrokeColor(roi1.getStrokeColor());
		if (roi1.getStroke()!=null)
			roi2.setStroke(roi1.getStroke());
		roi2.setDrawOffset(roi1.getDrawOffset());
	}

	/** Returns a hashcode for this Roi that typically changes
		if it is moved, even though it is still the same object. */
	public int getHashCode() {
		return hashCode() ^ (Double.valueOf(getXBase()).hashCode()) ^
			Integer.rotateRight(Double.valueOf(getYBase()).hashCode(),16);
	}

	public void setFlattenScale(double scale) {
		flattenScale = scale;
	}

	public void notifyListeners(int id) {
		if (id==RoiListener.CREATED) {
			if (listenersNotified)
				return;
			listenersNotified = true;
		}
		synchronized (listeners) {
			for (int i=0; i<listeners.size(); i++) {
				RoiListener listener = (RoiListener)listeners.elementAt(i);
				listener.roiModified(imp, id);
			}
		}
	}

	public static Roi xor(Roi[] rois) {
		ShapeRoi s1=null, s2=null;
		for (int i=0; i<rois.length; i++) {
			Roi roi = rois[i];
			if (roi==null)
				continue;
			if (s1==null) {
				if (roi instanceof ShapeRoi)
					s1 = (ShapeRoi)roi.clone();
				else
					s1 = new ShapeRoi(roi);
				if (s1==null) return null;
			} else {
				if (roi instanceof ShapeRoi)
					s2 = (ShapeRoi)roi.clone();
				else
					s2 = new ShapeRoi(roi);
				if (s2==null) continue;
				s1.xor(s2);
			}
		}
		return s1!=null?s1.trySimplify():null;
	}

	public static void addRoiListener(RoiListener listener) {
		listeners.addElement(listener);
	}

	public static void removeRoiListener(RoiListener listener) {
		listeners.removeElement(listener);
	}

	public static Vector getListeners() {
		return listeners;
	}

	/**
	 * Required by the {@link Iterable} interface.
	 * Use to iterate over the contained coordinates. Usage example:
	 * <pre>
	 * for (Point p : roi) {
	 *   // process p
	 * }
	 * </pre>
	 * Author: Wilhelm Burger
	 * @see #getContainedPoints()
	 * @see #getContainedFloatPoints()
	*/
	public Iterator<Point> iterator() {
		// Returns the default (mask-based) point iterator. Note that 'Line' overrides the
		// iterator() method and returns a specific point iterator.
		return new RoiPointsIteratorMask();
	}


	/**
	 * Default iterator over points contained in a mask-backed {@link Roi}.
	 * Author: W. Burger
	*/
	private class RoiPointsIteratorMask implements Iterator<Point> {
		private ImageProcessor mask;
		private final Rectangle bounds;
		private final int xbase, ybase;
		private final int n;
		private int next;

		RoiPointsIteratorMask() {
			if (isLine()) {
				Roi roi2 = Roi.convertLineToArea(Roi.this);
				mask = roi2.getMask();
				xbase = roi2.x;
				ybase = roi2.y;
			} else {
				mask = getMask();
				if (mask==null && type==RECTANGLE) {
					mask = new ByteProcessor(width, height);
					mask.invert();
				}
				xbase = Roi.this.x;
				ybase = Roi.this.y;
			}
			bounds = new Rectangle(mask.getWidth(), mask.getHeight());
			n = bounds.width * bounds.height;
			findNext(0);	// sets next
		}

		@Override
		public boolean hasNext() {
			return next < n;
		}

		@Override
		public Point next() {
			if (next >= n)
				throw new NoSuchElementException();
			int x = next % bounds.width;
			int y = next / bounds.width;
			findNext(next+1);
			return new Point(xbase+x, ybase+y);
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

		// finds the next element (from start), sets next
		private void findNext(int start) {
			if (mask == null)
				next = start;
			else {
				next = n;
				for (int i=start; i<n; i++) {
					if (mask.get(i)!=0) {
						next = i;
						break;
					}
				}
			}
		}
	}

}
