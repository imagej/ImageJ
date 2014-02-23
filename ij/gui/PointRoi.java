package ij.gui;
import java.awt.*;
import java.awt.image.*;
import ij.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.Colors;
import ij.plugin.filter.Analyzer;
import java.awt.event.KeyEvent;
import ij.plugin.frame.Recorder;
import ij.util.Java2; 

/** This class represents a collection of points. */
public class PointRoi extends PolygonRoi {
	public static final String[] sizes = {"Small", "Median", "Large"};
	private static final String SIZE_KEY = "point.size";
	private static final String CROSS_COLOR_KEY = "point.cross.color";
	private static final int SMALL=3, MEDIAN=5, LARGE=7;
	private static int markerSize = SMALL;
	private static Font font;
	private static Color defaultCrossColor = Color.white;
	private static int fontSize = 9;
	private double saveMag;
	private boolean hideLabels;
	
	static {
		setDefaultMarkerSize(Prefs.get(SIZE_KEY, sizes[1]));
		setDefaultCrossColor(Colors.getColor(Prefs.get(CROSS_COLOR_KEY, "white"),null));
	}
	
	/** Creates a new PointRoi using the specified int arrays of offscreen coordinates. */
	public PointRoi(int[] ox, int[] oy, int points) {
		super(itof(ox), itof(oy), points, POINT);
		width+=1; height+=1;
	}

	/** Creates a new PointRoi using the specified float arrays of offscreen coordinates. */
	public PointRoi(float[] ox, float[] oy, int points) {
		super(ox, oy, points, POINT);
		width+=1; height+=1;
	}

	/** Creates a new PointRoi using the specified float arrays of offscreen coordinates. */
	public PointRoi(float[] ox, float[] oy) {
		this(ox, oy, ox.length);
	}

	/** Creates a new PointRoi from a FloatPolygon. */
	public PointRoi(FloatPolygon poly) {
		this(poly.xpoints, poly.ypoints, poly.npoints);
	}

	/** Creates a new PointRoi from a Polygon. */
	public PointRoi(Polygon poly) {
		this(itof(poly.xpoints), itof(poly.ypoints), poly.npoints);
	}

	/** Creates a new PointRoi using the specified offscreen int coordinates. */
	public PointRoi(int ox, int oy) {
		super(makeXArray(ox, null), makeYArray(oy, null), 1, POINT);
		width=1; height=1;
	}

	/** Creates a new PointRoi using the specified offscreen double coordinates. */
	public PointRoi(double ox, double oy) {
		super(makeXArray(ox, null), makeYArray(oy, null), 1, POINT);
		width=1; height=1;
	}

	/** Creates a new PointRoi using the specified screen coordinates. */
	public PointRoi(int sx, int sy, ImagePlus imp) {
		super(makeXArray(sx, imp), makeYArray(sy, imp), 1, POINT);
		setImage(imp);
		width=1; height=1;
		if (imp!=null)
			imp.draw(x-10, y-10, 20, 20);
		if (Recorder.record && !Recorder.scriptMode()) 
			Recorder.record("makePoint", x, y);
	}
	
	static float[] itof(int[] arr) {
		if (arr==null)
			return null;
		int n = arr.length;
		float[] temp = new float[n];
		for (int i=0; i<n; i++)
			temp[i] = arr[i];
		return temp;
	}


	static float[] makeXArray(double value, ImagePlus imp) {
		float[] array = new float[1];
		array[0] = (float)(imp!=null?imp.getCanvas().offScreenXD((int)value):value);
		return array;
	}
				
	static float[] makeYArray(double value, ImagePlus imp) {
		float[] array = new float[1];
		array[0] = (float)(imp!=null?imp.getCanvas().offScreenYD((int)value):value);
		return array;
	}
				
	void handleMouseMove(int ox, int oy) {
		//IJ.log("handleMouseMove");
	}
	
	protected void handleMouseUp(int sx, int sy) {
		super.handleMouseUp(sx, sy);
		modifyRoi(); //adds this point to previous points if shift key down
	}
	
	/** Draws the points on the image. */
	public void draw(Graphics g) {
		updatePolygon();
		if (ic!=null) mag = ic.getMagnification();
		if (!Prefs.noPointLabels && !hideLabels && nPoints>1) {
			fontSize = 9;
			if (mag>1.0)
				fontSize = (int)(((mag-1.0)/3.0+1.0)*9.0);
			if (fontSize>18) fontSize = 18;
			if (font==null || mag!=saveMag)
				font = new Font("SansSerif", Font.PLAIN, fontSize);
			g.setFont(font);
			if (fontSize>9)
				Java2.setAntialiasedText(g, true);
			saveMag = mag;
		}
		for (int i=0; i<nPoints; i++)
			drawPoint(g, xp2[i], yp2[i], i+1);
		if (updateFullWindow)
			{updateFullWindow = false; imp.draw();}
	}

	void drawPoint(Graphics g, int x, int y, int n) {
		Color cc = fillColor!=null?fillColor:defaultCrossColor;
		int size=markerSize, size2=size/2;
		if (cc!=null) {
			g.setColor(cc);
			g.drawLine(x-(size+2), y, x+size+2, y);
			g.drawLine(x, y-(size+2), x, y+size+2);
		}
		if (!Prefs.noPointLabels && !hideLabels && nPoints>1) {
			if (cc==null)
				g.setColor(strokeColor!=null?strokeColor:ROIColor);
			g.drawString(""+n, x+4, y+fontSize+2);
		}
		g.setColor(strokeColor!=null?strokeColor:ROIColor);
		g.fillRect(x-size2, y-size2, size, size);
		g.setColor(Color.black);
		g.drawOval(x-(size2+1), y-(size2+1), size+1, size+1);
	}

	public void drawPixels(ImageProcessor ip) {
		ip.setLineWidth(Analyzer.markWidth);
		for (int i=0; i<nPoints; i++) {
			ip.moveTo(x+(int)xpf[i], y+(int)ypf[i]);
			ip.lineTo(x+(int)xpf[i], y+(int)ypf[i]);
		}
	}
	
	/** Returns a copy of this PointRoi with a point at (x,y) added. */
	public PointRoi addPoint(double x, double y) {
		FloatPolygon poly = getFloatPolygon();
		poly.addPoint(x, y);
		PointRoi p = new PointRoi(poly.xpoints, poly.ypoints, poly.npoints);
		p.setHideLabels(hideLabels);
		IJ.showStatus("count="+poly.npoints);
		p.setStrokeColor(getStrokeColor());
		p.setFillColor(getFillColor());
		return p;
	}
	
	public PointRoi addPoint(int x, int y) {
		return addPoint((double)x, (double)y);
	}
	
	/** Subtract the points that intersect the specified ROI and return 
		the result. Returns null if there are no resulting points. */
	public PointRoi subtractPoints(Roi roi) {
		Polygon points = getPolygon();
		Polygon poly = roi.getPolygon();
		Polygon points2 = new Polygon();
		for (int i=0; i<points.npoints; i++) {
			if (!poly.contains(points.xpoints[i], points.ypoints[i]))
				points2.addPoint(points.xpoints[i], points.ypoints[i]);
		}
		if (points2.npoints==0)
			return null;
		else
			return new PointRoi(points2.xpoints, points2.ypoints, points2.npoints);
	}

	public ImageProcessor getMask() {
		if (cachedMask!=null && cachedMask.getPixels()!=null)
			return cachedMask;
		ImageProcessor mask = new ByteProcessor(width, height);
		for (int i=0; i<nPoints; i++) {
			mask.putPixel((int)xpf[i], (int)ypf[i], 255);
		}
		cachedMask = mask;
		return mask;
	}

	/** Returns true if (x,y) is one of the points in this collection. */
	public boolean contains(int x, int y) {
		for (int i=0; i<nPoints; i++) {
			if (x==this.x+xpf[i] && y==this.y+ypf[i]) return true;
		}
		return false;
	}
	
	public void setHideLabels(boolean hideLabels) {
		this.hideLabels = hideLabels;
	}

	public static void setDefaultMarkerSize(String size) {
		boolean set = false;
		if (sizes[0].equals(size)) {
			markerSize=SMALL; set=true;
		} else if (sizes[1].equals(size)) {
			markerSize=MEDIAN; set=true;
		} else if (sizes[2].equals(size)) {
			markerSize=LARGE; set=true;
		}
		if (set) Prefs.set(SIZE_KEY, size);
	}
	
	public static String getDefaultMarkerSize() {
		switch (markerSize) {
			case SMALL: return sizes[0];
			case MEDIAN: return sizes[1];
			case LARGE: return sizes[2];
		}
		return null;
	}

	public static void setDefaultCrossColor(Color color) {
		if (defaultCrossColor!=color)
			Prefs.set(CROSS_COLOR_KEY, Colors.getColorName(color, "None"));
		defaultCrossColor = color;
	}
	
	public static Color getDefaultCrossColor() {
		return defaultCrossColor;
	}

	/** Always returns true. */
	public boolean subPixelResolution() {
		return true;
	}

	public String toString() {
		if (nPoints>1)
			return ("Roi[Points, count="+nPoints+"]");
		else
			return ("Roi[Point, x="+x+", y="+y+"]");
	}

}
