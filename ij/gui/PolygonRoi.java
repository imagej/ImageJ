package ij.gui;
import java.awt.*;
import java.awt.image.*;
import ij.*;
import ij.process.*;
import ij.measure.*;

/** This class represents a polygon region of interest or polyline of interest. */
public class PolygonRoi extends Roi {

	protected int maxPoints = 1000; // will be increased if necessary
	protected int[] xp, yp; 	// image coordinates relative to origin of roi bounding box
	protected int[] xp2, yp2;	// absolute screen coordinates
	protected int nPoints;
	protected int[] xSpline,ySpline; // relative image coordinates
	protected int[] xScreenSpline,yScreenSpline;  // absolute screen coordinates
	protected int splinePoints = 200;
	protected Graphics g;
	
	private int lastX, lastY;
	private double angle1=-1.0, degrees=-1.0;
	private int xClipMin, yClipMin, xClipMax, yClipMax;

	long mouseUpTime = 0;

	/** Creates a new polygon or polyline ROI from x and y coordinate arrays.
		Type must be Roi.POLYGON, Roi.FREEROI, Roi.TRACED_ROI, Roi.POLYLINE, Roi.FREELINE or Roi.ANGLE.*/
	public PolygonRoi(int[] xPoints, int[] yPoints, int nPoints, int type) {
		super(0, 0, null);
		if (type==POLYGON)
			this.type = POLYGON;
		else if (type==FREEROI)
			this.type = FREEROI;
		else if (type==TRACED_ROI)
			this.type = TRACED_ROI;
		else if (type==POLYLINE)
			this.type = POLYLINE;
		else if (type==FREELINE)
			this.type = FREELINE;
		else if (type==ANGLE)
			this.type = ANGLE;
		else
			throw new IllegalArgumentException("Invalid type");
		maxPoints = xPoints.length;
		xp = xPoints;
		yp = yPoints;
		xp2 = new int[maxPoints];
		yp2 = new int[maxPoints];
		this.nPoints = nPoints;
		if (type==ANGLE && nPoints==3)
			getAngleAsString();
		finishPolygon();
	}
	
	/** Obsolete */
	public PolygonRoi(int[] xPoints, int[] yPoints, int nPoints, ImagePlus imp, int type) {
		this(xPoints, yPoints, nPoints, type);
		setImage(imp);
	}

	/** Starts the process of creating a new user-generated polygon or polyline ROI. */
	public PolygonRoi(int ox, int oy, ImagePlus imp) {
		super(ox, oy, imp);
		int tool = Toolbar.getToolId();
		if (tool==Toolbar.POLYGON)
			type = POLYGON;
		else if (tool==Toolbar.ANGLE)
			type = ANGLE;
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
		if (tool==Toolbar.POLYGON || tool==Toolbar.POLYLINE || tool==Toolbar.ANGLE) {
			g.setColor(ROIColor);
			lastX = x; lastY = y;
			drawStartBox();
		}
		state = CONSTRUCTING;
	}

	private void drawStartBox() {
		if (type!=ANGLE)
			g.drawRect(ic.screenX(startX)-4, ic.screenY(startY)-4, 8, 8);
	}
	
	public void draw(Graphics g) {
		if (state!=CONSTRUCTING) {
			updatePolygon();
			g.setColor(ROIColor);
			if (xSpline!=null) {
				if (type==POLYLINE || type==FREELINE)
					g.drawPolyline(xScreenSpline, yScreenSpline, splinePoints);
				else
					g.drawPolygon(xScreenSpline, yScreenSpline, splinePoints);
			} else {
				if (type==POLYLINE || type==FREELINE || type==ANGLE)
					g.drawPolyline(xp2, yp2, nPoints);
				else
					g.drawPolygon(xp2, yp2, nPoints);
			}
			if ((xSpline!=null||type==POLYGON||type==POLYLINE||type==ANGLE)
			&& state!=CONSTRUCTING && clipboard==null) {
				if (ic!=null) mag = ic.getMagnification();
				int size2 = HANDLE_SIZE/2;
				if (activeHandle>0)
					drawHandle(g, xp2[activeHandle-1]-size2, yp2[activeHandle-1]-size2);
				if (activeHandle<nPoints-1)
					drawHandle(g, xp2[activeHandle+1]-size2, yp2[activeHandle+1]-size2);
				for (int i=0; i<nPoints; i++)
					drawHandle(g, xp2[i]-size2, yp2[i]-size2);
			}
			if (!(state==MOVING_HANDLE))
				showStatus();
			if (updateFullWindow)
				{updateFullWindow = false; imp.draw();}
		}
	}

	public void drawPixels() {
		ImageProcessor ip = imp.getProcessor();
		if (xSpline!=null) {
			ip.moveTo(x+xSpline[0], y+ySpline[0]);
			for (int i=1; i<splinePoints; i++)
				ip.lineTo(x+xSpline[i], y+ySpline[i]);
			if (type==POLYGON || type==FREEROI || type==TRACED_ROI)
				ip.lineTo(x+xSpline[0], y+ySpline[0]);
		} else {
			ip.moveTo(x+xp[0], y+yp[0]);
			for (int i=1; i<nPoints; i++)
				ip.lineTo(x+xp[i], y+yp[i]);
			if (type==POLYGON || type==FREEROI || type==TRACED_ROI)
				ip.lineTo(x+xp[0], y+yp[0]);
		}
		if (xSpline!=null || Line.getWidth()>1)
			updateFullWindow = true;
	}

	protected void grow(int x, int y) {
	// Overrides grow() in Roi class
	}


	protected void updatePolygon() {
		Rectangle srcRect = ic.getSrcRect();
		if (ic.getMagnification()==1.0 && srcRect.x==0 && srcRect.y==0) {
			for (int i=0; i<nPoints; i++) {
				xp2[i] = xp[i]+x;
				yp2[i] = yp[i]+y;
			}
		} else {
			for (int i=0; i<nPoints; i++) {
				xp2[i] = ic.screenX(xp[i]+x);
				yp2[i] = ic.screenY(yp[i]+y);
			}
		}
		if (xSpline!=null) {
			for (int i=0; i<splinePoints; i++) {
				xScreenSpline[i] = ic.screenX(xSpline[i]+x);
				yScreenSpline[i] = ic.screenY(ySpline[i]+y);
			}
		}
	}

	void handleMouseMove(int ox, int oy) {
	// Do rubber banding
		int tool = Toolbar.getToolId();
		if (!(tool==Toolbar.POLYGON || tool==Toolbar.POLYLINE || tool==Toolbar.ANGLE)) {
			imp.killRoi();
			imp.draw();
			return;
		}
		g.setXORMode(Color.black);
		g.drawLine(ic.screenX(xp[nPoints-1]), ic.screenY(yp[nPoints-1]), ic.screenX(lastX), ic.screenY(lastY));
		g.drawLine(ic.screenX(xp[nPoints-1]), ic.screenY(yp[nPoints-1]), ic.screenX(ox), ic.screenY(oy));
		lastX = ox;
		lastY = oy;
		degrees = -1;
		double len = -1;
		if (nPoints>0) {
			int x1 = xp[nPoints-1];
			int y1 = yp[nPoints-1];
			degrees = getAngle(x1, y1, ox, oy);
			if (tool!=Toolbar.ANGLE) {
				Calibration cal = imp.getCalibration();
				len = Math.sqrt((ox-x1)*cal.pixelWidth*(ox-x1)*cal.pixelWidth
				+ (oy-y1)*cal.pixelHeight*(oy-y1)*cal.pixelHeight);
			}
		}
		if (tool==Toolbar.ANGLE) {
			if (nPoints==1)
				angle1 = degrees;
			else if (nPoints==2) {
				double angle2 = getAngle(xp[1], yp[1], ox, oy);
				degrees = Math.abs(180-Math.abs(angle1-angle2));
				if (degrees>180.0)
					degrees = 360.0-degrees;
			}
		}
		String length = len!=-1?", length=" + IJ.d2s(len):"";
		String angle = degrees!=-1?", angle=" + IJ.d2s(degrees):"";
		IJ.showStatus(imp.getLocationAsString(ox,oy) + length + angle);
	}

	void finishPolygon() {
		Polygon poly = new Polygon(xp, yp, nPoints);
		Rectangle r = poly.getBounds();
		x = r.x;
		y = r.y;
		width = r.width;
		height = r.height;
		if ((nPoints<2) || (!(type==FREELINE||type==POLYLINE) && (nPoints<3||width==0||height==0))) {
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
	
	protected void moveHandle(int ox, int oy) {
		if (clipboard!=null)
			return;
		xp[activeHandle] = ox-x;
		yp[activeHandle] = oy-y;
		if (xSpline!=null) {
			fitSpline(splinePoints);
			updateClipRect();
			imp.draw(clipX, clipY, clipWidth, clipHeight);
			oldX = x; oldY = y;
			oldWidth = width; oldHeight = height;
		} else {
			resetBoundingRect();
			updateClipRectAndDraw();
		}
		String angle = type==ANGLE?getAngleAsString():"";
		IJ.showStatus(imp.getLocationAsString(ox,oy) + angle);
	}

   /** After handle is moved, find clip rect and repaint. */
   void updateClipRectAndDraw() {
		int xmin=Integer.MAX_VALUE, ymin=Integer.MAX_VALUE, xmax=0, ymax=0;
		int x2, y2;
		if (activeHandle>0)
		   {x2=x+xp[activeHandle-1]; y2=y+yp[activeHandle-1];}
		else
		   {x2=x+xp[nPoints-1]; y2=y+yp[nPoints-1];}
		if (x2<xmin) xmin = x2;
		if (y2<ymin) ymin = y2;
		if (x2>xmax) xmax = x2;
		if (y2>ymax) ymax = y2;
		x2=x+xp[activeHandle]; y2=y+yp[activeHandle];
		if (x2<xmin) xmin = x2;
		if (y2<ymin) ymin = y2;
		if (x2>xmax) xmax = x2;
		if (y2>ymax) ymax = y2;
		if (activeHandle<nPoints-1)
		   {x2=x+xp[activeHandle+1]; y2=y+yp[activeHandle+1];}
		else
		   {x2=x+xp[0]; y2=y+yp[0];}
		if (x2<xmin) xmin = x2;
		if (y2<ymin) ymin = y2;
		if (x2>xmax) xmax = x2;
		if (y2>ymax) ymax = y2;
		int xmin2=xmin, ymin2=ymin, xmax2=xmax, ymax2=ymax;
		if (xClipMin<xmin2) xmin2 = xClipMin;
		if (yClipMin<ymin2) ymin2 = yClipMin;
		if (xClipMax>xmax2) xmax2 = xClipMax;
		if (yClipMax>ymax2) ymax2 = yClipMax;
		xClipMin=xmin; yClipMin=ymin; xClipMax=xmax; yClipMax=ymax;
		double mag = ic.getMagnification();
		int m = mag<1.0?(int)(HANDLE_SIZE/mag):HANDLE_SIZE;
		imp.draw(xmin2-m, ymin2-m, xmax2-xmin2+m*2, ymax2-ymin2+m*2);
	}

	void resetBoundingRect() {
		int xmin=Integer.MAX_VALUE, xmax=-xmin, ymin=xmin, ymax=xmax;
		int xx, yy;
		for(int i=0; i<nPoints; i++) {
			xx = xp[i];
			if (xx<xmin) xmin=xx;
			if (xx>xmax) xmax=xx;
			yy = yp[i];
			if (yy<ymin) ymin=yy;
			if (yy>ymax) ymax=yy;
		}
		if (xmin!=0)
		   for (int i=0; i<nPoints; i++)
			   xp[i] -= xmin;
		if (ymin!=0)
		   for (int i=0; i<nPoints; i++)
			   yp[i] -= ymin;
		//IJ.log("reset: "+ymin+" "+before+" "+yp[0]);
		x+=xmin; y+=ymin;
		width=xmax-xmin; height=ymax-ymin;
	}

	String getAngleAsString() {
		double angle1 = getAngle(xp[0], yp[0], xp[1], yp[1]);
		double angle2 = getAngle(xp[1], yp[1], xp[2], yp[2]);
		degrees = Math.abs(180-Math.abs(angle1-angle2));
		if (degrees>180.0)
			degrees = 360.0-degrees;
		return ", angle=" + IJ.d2s(degrees);
	}
   
   protected void mouseDownInHandle(int handle, int sx, int sy) {
		state = MOVING_HANDLE;
		activeHandle = handle;
		int ox=ic.offScreenX(sx), oy=ic.offScreenY(sy);
		int m = (int)(10.0/ic.getMagnification());
		xClipMin=ox-m; yClipMin=oy-m; xClipMax=ox+m; yClipMax=oy+m;
	}

	public void fitSpline(int evaluationPoints) {
		splinePoints = evaluationPoints;
		if (xSpline==null || splinePoints!=evaluationPoints) {
			xSpline = new int[splinePoints];
			ySpline = new int[splinePoints];
			xScreenSpline = new int[splinePoints];
			yScreenSpline = new int[splinePoints];
		}
		int nNodes = nPoints;
		if (type==POLYGON) {
			nNodes++;
			if (nNodes>=xp.length)
				enlargeArrays();
			xp[nNodes-1] = xp[0];
			yp[nNodes-1] = yp[0];
		}
		int[] xindex = new int[nNodes];
		for(int i=0; i<nNodes; i++)
			xindex[i] = i;
		SplineFitter sfx = new SplineFitter(xindex, xp, nNodes);
		SplineFitter sfy = new SplineFitter(xindex, yp, nNodes);
	   
		// Evaluate the splines at all points
		double scale = (double)(nNodes-1)/(splinePoints-1);
		int xs=0, ys=0;
		int xmin=Integer.MAX_VALUE, xmax=-xmin, ymin=xmin, ymax=xmax;
		for(int i=0; i<splinePoints; i++) {
			double xvalue = i*scale;
			xs = (int) Math.floor(sfx.evalSpline(xindex, xp, nNodes, xvalue) + 0.5);
			if (xs<xmin) xmin=xs;
			if (xs>xmax) xmax=xs;
			xSpline[i] = xs;
			ys = (int) Math.floor(sfy.evalSpline(xindex, yp, nNodes, xvalue) + 0.5);
			if (ys<ymin) ymin=ys;
			if (ys>ymax) ymax=ys;
			ySpline[i] = ys;
		}
		if (xmin!=0) {
		   for (int i=0; i<nPoints; i++)
			   xp[i] -= xmin;
		   for (int i=0; i<splinePoints; i++)
			   xSpline[i] -= xmin;
		}
		if (ymin!=0) {
		   for (int i=0; i<nPoints; i++)
			   yp[i] -= ymin;
		   for (int i=0; i<splinePoints; i++)
			   ySpline[i] -= ymin;
		}
		//IJ.log("reset: "+ymin+" "+before+" "+yp[0]);
		x+=xmin; y+=ymin;
		width=xmax-xmin; height=ymax-ymin;
	}

	/*
	double getSplineLength() {
		int nNodes = nPoints;
		if (type==POLYGON) {
			nNodes++;
			if (nNodes==xp.length)
				enlargeArrays();
			xp[nNodes-1] = xp[0];
			yp[nNodes-1] = yp[0];
		}
		int[] xindex = new int[nNodes];
		for(int i=0; i<nNodes; i++)
			xindex[i] = i;
		SplineFitter sfx = new SplineFitter(xindex, xp, nNodes);
		SplineFitter sfy = new SplineFitter(xindex, yp, nNodes);
		
		double scale = (double)(nNodes-1)/(splinePoints-1);
		double xs=0.0, ys=0.0;
		double length = 0.0;
		for(int i=0; i<splinePoints; i++) {
			double xvalue = i*scale;
			xs = sfx.evalSpline(xindex, xp, nNodes, xvalue);
			ys = sfy.evalSpline(xindex, yp, nNodes, xvalue);
			length += Math.sqrt(xs*xs + ys*ys);
		}
		//if (type==POLYGON)
		return length;
	}
	*/

	protected void handleMouseUp(int sx, int sy) {
		if (state==MOVING)
			{state = NORMAL; return;}				
		if (state==MOVING_HANDLE) {
			imp.getProcessor().setMask(null); //mask is no longer valid
			state = NORMAL;
			updateClipRect();
			//imp.draw(clipX, clipY, clipWidth, clipHeight);
			oldX=x; oldY=y;
			oldWidth=width; oldHeight=height;
			return;
		}		
		if (state!=CONSTRUCTING)
			return;
		if (IJ.spaceBarDown()) { // scrolling?
			g.setXORMode(Color.black);
			g.drawLine(ic.screenX(xp[nPoints-1]), ic.screenY(yp[nPoints-1]), ic.screenX(lastX), ic.screenY(lastY));
			drawLineSegments();
			return;
		}
		boolean samePoint = (xp[nPoints-1]==lastX && yp[nPoints-1]==lastY);
		Rectangle biggerStartBox = new Rectangle(ic.screenX(startX)-5, ic.screenY(startY)-5, 10, 10);
		if (nPoints>2 && (biggerStartBox.contains(sx, sy)
		|| (ic.offScreenX(sx)==startX && ic.offScreenY(sy)==startY)
		|| (samePoint && (System.currentTimeMillis()-mouseUpTime)<=500))) {
			finishPolygon();
			return;
		} else if (!samePoint) {
			//add point to polygon
			xp[nPoints] = lastX;
			yp[nPoints] = lastY;
			nPoints++;
			if (nPoints==xp.length)
				enlargeArrays();
			drawLineSegments();
			mouseUpTime = System.currentTimeMillis();
			if (type==ANGLE && nPoints==3)
				{finishPolygon(); return;}
		}
	}

	public boolean contains(int x, int y) {
		if (!super.contains(x, y))
			return false;
		else if (xScreenSpline!=null) {
			Polygon poly = new Polygon(xScreenSpline, yScreenSpline, splinePoints);
			return poly.contains(ic.screenX(x), ic.screenY(y));
		} else {
			Polygon poly = new Polygon(xp2, yp2, nPoints);
			return poly.contains(ic.screenX(x), ic.screenY(y));
		}
	}
	
	/** Returns a handle number if the specified screen coordinates are  
		inside or near a handle, otherwise returns -1. */
	public int isHandle(int sx, int sy) {
		if (!(xSpline!=null||type==POLYGON||type==POLYLINE||type==ANGLE)||clipboard!=null)
		   return -1;
		int size = HANDLE_SIZE+5;
		int halfSize = size/2;
		int handle = -1;
		int sx2, sy2;
		for (int i=0; i<nPoints; i++) {
			sx2 = xp2[i]-halfSize; sy2=yp2[i]-halfSize;
			if (sx>=sx2 && sx<=sx2+size && sy>=sy2 && sy<=sy2+size) {
				handle = i;
				break;
			}
		}
		return handle;
	}

	/** Override Roi.nudge() to support splines. */
	//public void nudge(int key) {
	//	super.nudge(key);
	//	if (xSpline!=null) {
	//		fitSpline();
	//		updateFullWindow = true;
	//		imp.draw();
	//	}
	//}

	public int[] getMask() {
		PolygonFiller pf = new PolygonFiller();
		if (xSpline!=null)
			pf.setPolygon(xSpline, ySpline, splinePoints);
		else
			pf.setPolygon(xp, yp, nPoints);
		return pf.getMask(width, height);
	}

	/*
	public int[] getMask() {
		if (type==POLYLINE || type==FREELINE || type==ANGLE || width==0 || height==0)
			return null;
		Image img = GUI.createBlankImage(width, height);
		Graphics g = img.getGraphics();
		//g.setColor(Color.white);
		//g.fillRect(0, 0, width, height);
		g.setColor(Color.black);
		if (xSpline!=null)
			g.fillPolygon(xSpline, ySpline, splinePoints);
		else
			g.fillPolygon(xp, yp, nPoints);
		//new ImagePlus("Mask", img).show();
		ColorProcessor cp = new ColorProcessor(img);
		img.flush();
		img = null;
		g.dispose();
		return (int[])cp.getPixels();
	}
	*/

	/** Returns the length of this line selection after
		smoothing using a 3-point running average.*/
	double getSmoothedLineLength() {
		double length = 0.0;
		double w2 = 1.0;
		double h2 = 1.0;
		double dx, dy;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			w2 = cal.pixelWidth*cal.pixelWidth;
			h2 = cal.pixelHeight*cal.pixelHeight;
		}
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
		double w2 = 1.0;
		double h2 = 1.0;
		double dx, dy;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			w2 = cal.pixelWidth*cal.pixelWidth;
			h2 = cal.pixelHeight*cal.pixelHeight;
		}
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
		if (type==TRACED_ROI)
			return getTracedPerimeter();
			
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
		if (xSpline!=null) {
			for (int i=0; i<(splinePoints-1); i++) {
				dx = xSpline[i+1]-xSpline[i];
				dy = ySpline[i+1]-ySpline[i];
				length += Math.sqrt(dx*dx*w2+dy*dy*h2);
			}
			if (type==POLYGON) {
				dx = xSpline[0]-xSpline[splinePoints-1];
				dy = ySpline[0]-ySpline[splinePoints-1];
				length += Math.sqrt(dx*dx*w2+dy*dy*h2);
			}
		} else {
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
		}
		return length;
	}
	
	/** Returns Feret's diameter, the greatest distance between 
		any two points along the ROI boundary. */
	public double getFeretsDiameter() {
		double w2=1.0, h2=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			w2 = cal.pixelWidth*cal.pixelWidth;
			h2 = cal.pixelHeight*cal.pixelHeight;
		}
		double diameter = 0.0, dx, dy, d;
		for (int i=0; i<nPoints; i++) {
			for (int j=i; j<nPoints; j++) {
				dx = xp[i] - xp[j];
				dy = yp[i] - yp[j];
				d = Math.sqrt(dx*dx*w2 + dy*dy*h2);
				if (d>diameter)
					diameter = d;
			}
		}
		return diameter;
	}

	/** Returns the angle in degrees between the first two segments of this polyline.*/
	public double getAngle() {
		return degrees;
	}
	
	/** Returns the number of XY coordinates. */
	public int getNCoordinates() {
		if (xSpline!=null)
			return splinePoints;
		else
			return nPoints;
	}
	
	/** Returns this ROI's X-coordinates, which are relative
		to origin of the bounding box. */
	public int[] getXCoordinates() {
		if (xSpline!=null)
			return xSpline;
		else
			return xp;
	}

	/** Returns this ROI's Y-coordinates, which are relative
		to origin of the bounding box. */
	public int[] getYCoordinates() {
		if (xSpline!=null)
			return ySpline;
		else
			return yp;
	}

	/** Returns a copy of this PolygonRoi. */
	public synchronized Object clone() {
		PolygonRoi r = (PolygonRoi)super.clone();
		r.xp = new int[maxPoints];
		r.yp = new int[maxPoints];
		r.xp2 = new int[maxPoints];
		r.yp2 = new int[maxPoints];
		for (int i=0; i<nPoints; i++) {
			r.xp[i] = xp[i];
			r.yp[i] = yp[i];
			r.xp2[i] = xp2[i];
			r.yp2[i] = yp2[i];
		}
		if (xSpline!=null) {
			r.xSpline = null;
			r.fitSpline(splinePoints);
		}
		return r;
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
		if (IJ.debugMode) IJ.log("PolygonRoi: "+maxPoints+" points");
		maxPoints *= 2;
	}

}
