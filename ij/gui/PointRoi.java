package ij.gui;

import java.awt.*;
import java.awt.image.*;
import ij.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.filter.Analyzer;
import java.awt.event.KeyEvent;
import ij.plugin.frame.Recorder;
import ij.util.Java2; 

/** This class represents a collection of points. */
public class PointRoi extends PolygonRoi {
	private static Font font;
	private static int fontSize = 9;
	private double saveMag;
	private boolean hideLabels;
	
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
		if (imp!=null) imp.draw(x-5, y-5, width+10, height+10);
		if (Recorder.record && !Recorder.scriptMode()) 
			Recorder.record("makePoint", x, y);

	}
	
	static float[] itof(int[] arr) {
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
		//IJ.log("draw: " + nPoints+"  "+width+"  "+height);
		updatePolygon();
		//IJ.log("draw: "+ xpf[0]+" "+ypf[0]+" "+xp2[0]+" "+xp2[0]);
		if (ic!=null) mag = ic.getMagnification();
		int size2 = HANDLE_SIZE/2;
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
			drawPoint(g, xp2[i]-size2, yp2[i]-size2, i+1);
		//showStatus();
		if (updateFullWindow)
			{updateFullWindow = false; imp.draw();}
	}

	void drawPoint(Graphics g, int x, int y, int n) {
		g.setColor(fillColor!=null?fillColor:Color.white);
		g.drawLine(x-4, y+2, x+8, y+2);
		g.drawLine(x+2, y-4, x+2, y+8);
		g.setColor(strokeColor!=null?strokeColor:ROIColor);
		g.fillRect(x+1,y+1,3,3);
		if (!Prefs.noPointLabels && !hideLabels && nPoints>1)
			g.drawString(""+n, x+6, y+fontSize+4);
		g.setColor(Color.black);
		g.drawRect(x, y, 4, 4);
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

	public String toString() {
		if (nPoints>1)
			return ("Roi[Points, count="+nPoints+"]");
		else
			return ("Roi[Point, x="+x+", y="+y+"]");
	}

}
