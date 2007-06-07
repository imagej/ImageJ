package ij.gui;

import java.awt.*;
import java.awt.image.*;
import ij.*;
import ij.process.*;
import ij.measure.*;

/** This class represents a polygon region of interest or polyline of interest. */
public class PolygonRoi extends Roi {

	protected int maxPoints = 1000; // will be increased if necessary
	protected int[] xp, yp;		// coordinates are relative to origin of roi bounding box
	protected int[] xp2, yp2;	// absolute screen coordinates
	protected int nPoints;
	protected Graphics g;
	
	private int lastX, lastY;
	private double angle1=-1.0, degrees=-1.0;
	long mouseUpTime = 0;

	/** Creates a new polygon or polyline ROI from x and y coordinate arrays.
		The ImagePlus argument can be null. Type must be Roi.POLYGON, Roi.FREEROI,
		Roi.TRACED_ROI or Roi.POLYLINE.*/
	public PolygonRoi(int[] xPoints, int[] yPoints, int nPoints, ImagePlus imp, int type) {
		super(0, 0, imp);
		if (type==POLYGON)
			this.type = POLYGON;
		else if (type==FREEROI)
			this.type = FREEROI;
		else if (type==TRACED_ROI)
			this.type = TRACED_ROI;
		else if (type==POLYLINE)
			this.type = POLYLINE;
		else
			throw new IllegalArgumentException("Invalid type");
		maxPoints = xPoints.length;
		xp = xPoints;
		yp = yPoints;
		xp2 = new int[maxPoints];
		yp2 = new int[maxPoints];
		this.nPoints = nPoints;
		finishPolygon();
	}

	/** Starts the process of creating a new user-generated polygon or polyline ROI. */
	public PolygonRoi(int ox, int oy, ImagePlus imp) {
		super(ox, oy, imp);
		int tool = Toolbar.getToolId();
		if (tool==Toolbar.POLYGON)
			type = POLYGON;
		else
			type = POLYLINE;
		xp = new int[maxPoints];
		yp = new int[maxPoints];
		xp2 = new int[maxPoints];
		yp2 = new int[maxPoints];
		nPoints = 1;
		xp[0] = ox;
		yp[0] = oy;
		x = ox;
		y = oy;
		width=1;
		height=1;
		clipX = ox;
		clipY = oy;
		clipWidth = 1;
		clipHeight = 1;
		ImageWindow win = imp.getWindow();
		if (win!=null)
			g = ic.getGraphics();
		if (tool==Toolbar.POLYGON || tool==Toolbar.POLYLINE) {
			g.setColor(ROIColor);
			lastX = x; lastY = y;
			drawStartBox();
		}
		state = CONSTRUCTING;
	}

	private void drawStartBox() {
		g.drawRect(ic.screenX(startX)-4, ic.screenY(startY)-4, 8, 8);
	}
	
	public void draw(Graphics g) {
		if (state!=CONSTRUCTING) {
			updatePolygon();
			g.setColor(ROIColor);
			if (type==POLYLINE || type==FREELINE)
				g.drawPolyline(xp2, yp2, nPoints);
			else
				g.drawPolygon(xp2, yp2, nPoints);
			showStatus();
			if (updateFullWindow)
				{updateFullWindow = false; imp.draw();}
		}
	}

	public void drawPixels() {
		ImageProcessor ip = imp.getProcessor();
		ip.moveTo(x+xp[0], y+yp[0]);
		for (int i=1; i<nPoints; i++)
			ip.lineTo(x+xp[i], y+yp[i]);
		if (type==POLYGON || type==FREEROI || type==TRACED_ROI)
			ip.lineTo(x+xp[0], y+yp[0]);
		if (Line.getWidth()>1)
			updateFullWindow = true;
	}

	protected void grow(int x, int y) {
	// Overrides grow() in Roi class
	}


	protected void updatePolygon() {
		Rectangle srcRect = ic.getSrcRect();
		if (ic.getMagnification()==1.0 && srcRect.x==0 && srcRect.y==0 )
			for (int i=0; i<nPoints; i++) {
				xp2[i] = xp[i]+x;
				yp2[i] = yp[i]+y;
			}
		else
			for (int i=0; i<nPoints; i++) {
				xp2[i] = ic.screenX(xp[i]+x);
				yp2[i] = ic.screenY(yp[i]+y);
			}
	}

	void handleMouseMove(int ox, int oy) {
	// Do rubber banding
		int tool = Toolbar.getToolId();
		if (!(tool==Toolbar.POLYGON || tool==Toolbar.POLYLINE)) {
			imp.killRoi();
			imp.draw();
			return;
		}
		g.setXORMode(Color.black);
		g.drawLine(ic.screenX(xp[nPoints-1]), ic.screenY(yp[nPoints-1]), ic.screenX(lastX), ic.screenY(lastY));
		g.drawLine(ic.screenX(xp[nPoints-1]), ic.screenY(yp[nPoints-1]), ic.screenX(ox), ic.screenY(oy));
		lastX = ox;
		lastY = oy;
		String angle = "";
		if (tool==Toolbar.POLYLINE) {
			if (nPoints==1) {
				degrees = getAngle(xp[0], yp[0], ox, oy);
				angle1 = degrees;
			} else if (nPoints==2) {
				double angle2 = getAngle(xp[1], yp[1], ox, oy);
				degrees = Math.abs(180-Math.abs(angle1-angle2));
				if (degrees>180.0)
					degrees = 360.0-degrees;
			} else
				angle1 = -1.0;
			if (angle1>0.0)
				angle = ", angle=" + IJ.d2s(degrees);
		}
		IJ.showStatus("  (" + ox + "," + oy + ")" + angle);
	}

	void finishPolygon() {
		Polygon poly = new Polygon(xp, yp, nPoints);
		Rectangle r = poly.getBounds();
		x = r.x;
		y = r.y;
		width = r.width;
		height = r.height;
		if ((nPoints<2) || (type!=FREELINE && (nPoints<3 || width==0 || height==0))) {
			imp.killRoi();
			return;
		}
		for (int i=0; i<nPoints; i++) {
			xp[i] = xp[i]-x;
			yp[i] = yp[i]-y;
		}
		state = NORMAL;
		if (imp!=null && !(type==TRACED_ROI))
			imp.draw(x-5, y-5, width+10, height+10);
		oldX=x; oldY=y; oldWidth=width; oldHeight=height;
	}
	
	
	void drawLineSegments() {
		Polygon poly = new Polygon(xp, yp, nPoints);
		Rectangle r = poly.getBounds();
		x = r.x;
		y = r.y;
		width = r.width;
		height = r.height;
		//imp.draw(x-5, y-5, width+10, height+10);
		g.setPaintMode();
		g.setColor(ROIColor);
		drawStartBox();
		for (int i=0; i<(nPoints-1); i++)
			g.drawLine(ic.screenX(xp[i]), ic.screenY(yp[i]), ic.screenX(xp[i+1]), ic.screenY(yp[i+1]));
	}
	
	protected void handleMouseUp(int sx, int sy) {
		if (state!=CONSTRUCTING)
			return;
		boolean samePoint = (xp[nPoints-1]==lastX && yp[nPoints-1]==lastY);
		Rectangle biggerStartBox = new Rectangle(ic.screenX(startX)-5, ic.screenY(startY)-5, 10, 10);
		if (nPoints>2 && (biggerStartBox.contains(sx, sy)
		|| (ic.offScreenX(sx)==startX && ic.offScreenY(sy)==startY)
		|| (samePoint && (System.currentTimeMillis()-mouseUpTime)<=500))) {
			finishPolygon();
			return;
		}
		else if (!samePoint) {
			//add point to polygon
			xp[nPoints] = lastX;
			yp[nPoints] = lastY;
			nPoints++;
			if (nPoints==xp.length)
				enlargeArrays();
			drawLineSegments();
 			mouseUpTime = System.currentTimeMillis();		}
	}

	public boolean contains(int x, int y) {
		if (!super.contains(x, y))
			return false;
		else {
			Polygon poly = new Polygon(xp2, yp2, nPoints);
			return poly.contains(ic.screenX(x), ic.screenY(y));
		}
	}
	
	public int[] getMask() {
		if (type==POLYLINE || type==FREELINE || width==0 || height==0)
			return null;
		Image img = GUI.createBlankImage(width, height);
		Graphics g = img.getGraphics();
		//g.setColor(Color.white);
		//g.fillRect(0, 0, width, height);
		g.setColor(Color.black);
		g.fillPolygon(xp, yp, nPoints);
		//new ImagePlus("Mask", img).show();
		ColorProcessor cp = new ColorProcessor(img);
		img.flush();
		img = null;
		g.dispose();
		return (int[])cp.getPixels();
	}

	/** Returns the length of this line selection after
		smoothing using a 3-point running average.*/
	double getSmoothedLineLength() {
		double length = 0.0;
		double dx, dy;
		Calibration cal = imp.getCalibration();
		double w2 = cal.pixelWidth*cal.pixelWidth;
		double h2 = cal.pixelHeight*cal.pixelHeight;
		double x1,x2,x3,x4,y1,y2,y3,y4;
		x2=xp[0]; x3=xp[0]; x4=xp[1];
		y2=yp[0]; y3=yp[0]; y4=yp[1];
		for (int i=0; i<(nPoints-1); i++) {
			x1=x2; x2=x3; x3=x4;
			y1=y2; y2=y3; y3=y4;;
			if ((i+2)<nPoints) {
				x4=xp[i+2];
				y4=yp[i+2];
			}
			dx = (x4-x1)/3.0; // = (x2+x3+x4)/3-(x1+x2+x3)/3
			dy = (y4-y1)/3.0; // = (y2+y3+y4)/3-(y1+y2+y3)/3
			length += Math.sqrt(dx*dx*w2+dy*dy*h2);
		}
		return length;
	}

	/** Returns the perimeter of this ROIs after
		smoothing using a 3-point running average.*/
	double getSmoothedPerimeter() {
		double length = 0.0;
		double dx, dy;
		Calibration cal = imp.getCalibration();
		double w2 = cal.pixelWidth*cal.pixelWidth;
		double h2 = cal.pixelHeight*cal.pixelHeight;
		double x1,x2,x3,x4,y1,y2,y3,y4;
		x2=xp[nPoints-1]; x3=xp[0]; x4=xp[1];
		y2=yp[nPoints-1]; y3=yp[0]; y4=yp[1];
		for (int i=0; i<(nPoints-1); i++) {
			x1=x2; x2=x3; x3=x4;
			y1=y2; y2=y3; y3=y4;;
			if ((i+2)<nPoints) {
				x4=xp[i+2];
				y4=yp[i+2];
			} else {
				x4=xp[0];
				y4=yp[0];
			}
			dx = (x4-x1)/3.0; // = (x2+x3+x4)/3-(x1+x2+x3)/3
			dy = (y4-y1)/3.0; // = (y2+y3+y4)/3-(y1+y2+y3)/3
			length += Math.sqrt(dx*dx*w2+dy*dy*h2);
		}
		x1=x2; x2=x3; x3=x4; x4=xp[1];
		y1=y2; y2=y3; y3=y4; y4=yp[1];
		dx = (x4-x1)/3.0;
		dy = (y4-y1)/3.0;
		length += Math.sqrt(dx*dx*w2+dy*dy*h2);
		return length;
	}

	/** Returns the perimeter length of ROIs created using
		the wand tool. Edge pixels are counted as 1 and
		corner pixels as sqrt(2). */
	double getTracedPerimeter() {
		int sumdx = 0;
		int sumdy = 0;
		int nCorners = 0;
		int dx1 = xp[0] - xp[nPoints-1];
		int dy1 = yp[0] - yp[nPoints-1];
		int side1 = Math.abs(dx1) + Math.abs(dy1); //one of these is 0
		boolean corner = false;
		int nexti, dx2, dy2, side2;
		for (int i=0; i<nPoints; i++) {
			nexti = i+1;
			if (nexti==nPoints)
			  nexti = 0;
			dx2 = xp[nexti] - xp[i];
			dy2 = yp[nexti] - yp[i];
			sumdx += Math.abs(dx1);
			sumdy += Math.abs(dy1);
			side2 = Math.abs(dx2) + Math.abs(dy2);
			if (side1>1 || !corner) {
			  corner = true;
			  nCorners++;
			} else
			  corner = false;
			dx1 = dx2;
			dy1 = dy2;
			side1 = side2;
		}
		double w=1.0,h=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			w = cal.pixelWidth;
			h = cal.pixelHeight;
		}
		return sumdx*w+sumdy*h-(nCorners*((w+h)-Math.sqrt(w*w+h*h)));
	}

	/** Returns the perimeter (for ROIs) or length (for lines).*/
	public double getLength() {
		if (type==TRACED_ROI) {
			return getTracedPerimeter();
		}
			
		if (nPoints>2) {
			if (type==FREEROI)
				return getSmoothedPerimeter();
			else if (type==FREELINE && !(width==0 || height==0))
				return getSmoothedLineLength();
		}
		
		double length = 0.0;
		int dx, dy;
		Calibration cal = imp.getCalibration();
		double w2 = cal.pixelWidth*cal.pixelWidth;
		double h2 = cal.pixelHeight*cal.pixelHeight;
		for (int i=0; i<(nPoints-1); i++) {
			dx = xp[i+1]-xp[i];
			dy = yp[i+1]-yp[i];
			length += Math.sqrt(dx*dx*w2+dy*dy*h2);
		}
		if (type==POLYGON) {
			dx = xp[0]-xp[nPoints-1];
			dy = yp[0]-yp[nPoints-1];
			length += Math.sqrt(dx*dx*w2+dy*dy*h2);
		}
		return length;
	}
	
	/** Returns the angle in degrees between the first two segments of this polyline.*/
	public double getAngle() {
		return degrees;
	}
	
	/** Returns the number of XY coordinates. */
	public int getNCoordinates() {
		return nPoints;
	}
	
	/** Returns this ROI's X-coordinates, which are relative
		to origin of the bounding box. */
	public int[] getXCoordinates() {
		return xp;
	}

	/** Returns this ROI's Y-coordinates, which are relative
		to origin of the bounding box. */
	public int[] getYCoordinates() {
		return yp;
	}

	void enlargeArrays() {
		int[] xptemp = new int[maxPoints*2];
		int[] yptemp = new int[maxPoints*2];
		int[] xp2temp = new int[maxPoints*2];
		int[] yp2temp = new int[maxPoints*2];
		System.arraycopy(xp, 0, xptemp, 0, maxPoints);
		System.arraycopy(yp, 0, yptemp, 0, maxPoints);
		System.arraycopy(xp2, 0, xp2temp, 0, maxPoints);
		System.arraycopy(yp2, 0, yp2temp, 0, maxPoints);
		xp=xptemp; yp=yptemp;
		xp2=xp2temp; yp2=yp2temp;
		if (IJ.debugMode) IJ.write("PolygonRoi: "+maxPoints+" points");
		maxPoints *= 2;
	}

}