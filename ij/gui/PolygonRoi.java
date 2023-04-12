package ij.gui;
import ij.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.frame.*;
import ij.util.Tools;
import ij.util.FloatArray;
import java.awt.*;
import java.awt.image.*;
import java.awt.geom.*;
import java.awt.event.*;

/** This class represents a polygon region of interest or polyline of interest. */
public class PolygonRoi extends Roi {

	protected int maxPoints = 1000; // will be increased if necessary
	protected int[] xp, yp;		    // image coordinates relative to origin of roi bounding box
	protected float[] xpf, ypf;		// or alternative sub-pixel coordinates
	protected int[] xp2, yp2;	    // absolute screen coordinates
	protected int nPoints;
	protected float[] xSpline,ySpline; // relative image coordinates
	protected int splinePoints = 200;
	Rectangle clip;

	private double angle1, degrees=Double.NaN;
	private int xClipMin, yClipMin, xClipMax, yClipMax;
	private boolean userCreated;
	private int boxSize = 8;

	long mouseUpTime = 0;

	/** Creates a new polygon or polyline ROI from x and y coordinate arrays.
		Type must be Roi.POLYGON, Roi.FREEROI, Roi.TRACED_ROI, Roi.POLYLINE, Roi.FREELINE or Roi.ANGLE.*/
	public PolygonRoi(int[] xPoints, int[] yPoints, int nPoints, int type) {
		super(0, 0, null);
		init1(nPoints, type);
		xp = xPoints;
		yp = yPoints;
		if (type!=TRACED_ROI) {
			xp = new int[nPoints];
			yp = new int[nPoints];
			for (int i=0; i<nPoints; i++) {
				xp[i] = xPoints[i];
				yp[i] = yPoints[i];
			}
		}
		xp2 = new int[nPoints];
		yp2 = new int[nPoints];
		init2(type);
		state = NORMAL;
	}

	/** Creates a new polygon or polyline ROI from float x and y arrays.
		Type must be Roi.POLYGON, Roi.FREEROI, Roi.POLYLINE, Roi.FREELINE or Roi.ANGLE.*/
	public PolygonRoi(float[] xPoints, float[] yPoints, int nPoints, int type) {
		super(0, 0, null);
		init1(nPoints, type);
		xpf = new float[nPoints];
		ypf = new float[nPoints];
		for (int i=0; i<nPoints; i++) {
			xpf[i] = xPoints[i];
			ypf[i] = yPoints[i];
		}
		enableSubPixelResolution();
		xp2 = new int[nPoints];
		yp2 = new int[nPoints];
		init2(type);
		state = NORMAL;
	}

	/** Creates a new polygon or polyline ROI from float x and y arrays.
		Type must be Roi.POLYGON, Roi.FREEROI, Roi.POLYLINE, Roi.FREELINE or Roi.ANGLE.*/
	public PolygonRoi(float[] xPoints, float[] yPoints, int type) {
		this(xPoints, yPoints, xPoints.length, type);
	}

	private void init1(int nPoints, int type) throws IllegalArgumentException{
		maxPoints = nPoints;
		this.nPoints = nPoints;
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
		else if (type==POINT)
			this.type = POINT;
		else
			throw new IllegalArgumentException("PolygonRoi: Invalid type");
	}

	private void init2(int type) {
		if (type==ANGLE && nPoints==3)
			getAngleAsString();
		if (type==POINT && Toolbar.getMultiPointMode()) {
			Prefs.pointAutoMeasure = false;
			Prefs.pointAutoNextSlice = false;
			Prefs.pointAddToManager = false;
			Prefs.pointAddToOverlay = false;
			userCreated = true;
		}
		if (lineWidth>1 && isLine())
			updateWideLine(lineWidth);
		finishPolygon();
	}

	/** Creates a new polygon or polyline ROI from a Polygon. Type must be Roi.POLYGON,
		Roi.FREEROI, Roi.TRACED_ROI, Roi.POLYLINE, Roi.FREELINE or Roi.ANGLE.*/
	public PolygonRoi(Polygon p, int type) {
		this(p.xpoints, p.ypoints, p.npoints, type);
	}

	/** Creates a new polygon or polyline ROI from a FloatPolygon. Type must be Roi.POLYGON,
		Roi.FREEROI, Roi.TRACED_ROI, Roi.POLYLINE, Roi.FREELINE or Roi.ANGLE.*/
	public PolygonRoi(FloatPolygon p, int type) {
		this(p.xpoints, p.ypoints, p.npoints, type);
	}

	/** @deprecated */
	public PolygonRoi(int[] xPoints, int[] yPoints, int nPoints, ImagePlus imp, int type) {
		this(xPoints, yPoints, nPoints, type);
		setImage(imp);
	}

	/** Starts the process of creating a new user-generated polygon or polyline ROI. */
	public PolygonRoi(int sx, int sy, ImagePlus imp) {
		super(sx, sy, imp);
		int tool = Toolbar.getToolId();
		switch (tool) {
			case Toolbar.POLYGON:
				type = POLYGON;
				break;
			case Toolbar.FREEROI:
				type = FREEROI;
				break;
			case Toolbar.FREELINE:
				type = FREELINE;
				break;
			case Toolbar.ANGLE:
				type = ANGLE;
				break;
			default:
				type = POLYLINE;
				break;
		}
		if (magnificationForSubPixel())
			enableSubPixelResolution();
		previousSX = sx;
		previousSY = sy;
		x = offScreenX(sx);
		y = offScreenY(sy);
		startXD = offScreenXD(sx);
		startYD = offScreenYD(sy);
		if (subPixelResolution()) {
			setLocation(startXD, startYD);
			xpf = new float[maxPoints];
			ypf = new float[maxPoints];
			double xbase = getXBase();
			double ybase = getYBase();
			xpf[0] = (float)(startXD-xbase);
			ypf[0] = (float)(startYD-ybase);
			xpf[1] = xpf[0];
			ypf[1] = ypf[0];
		} else {
			xp = new int[maxPoints];
			yp = new int[maxPoints];
		}
		xp2 = new int[maxPoints];
		yp2 = new int[maxPoints];
		nPoints = 2;
		width=1;
		height=1;
		clipX = x;
		clipY = y;
		clipWidth = 1;
		clipHeight = 1;
		state = CONSTRUCTING;
		userCreated = true;
		if (lineWidth>1 && isLine())
			updateWideLine(lineWidth);
		boxSize = (int)(boxSize*Prefs.getGuiScale());
	}

	private void drawStartBox(Graphics g) {
		if (type!=ANGLE)
			g.drawRect(screenXD(startXD)-4, screenYD(startYD)-4, 8, 8);
	}

	public void draw(Graphics g) {
		updatePolygon();
		Color color =  strokeColor!=null?strokeColor:ROIColor;
		boolean hasHandles = xSpline!=null||type==POLYGON||type==POLYLINE||type==ANGLE;
		boolean isActiveOverlayRoi = !overlay && isActiveOverlayRoi();
		if (isActiveOverlayRoi) {
			if (color==Color.cyan)
				color = Color.magenta;
			else
				color = Color.cyan;
		}
		boolean fill = false;
		mag = getMagnification();
		if (fillColor!=null && !isLine() && state!=CONSTRUCTING) {
			color = fillColor;
			fill = true;
		}
		g.setColor(color);
		Graphics2D g2d = (Graphics2D)g;
		setRenderingHint(g2d);
		if (stroke!=null && !isActiveOverlayRoi)
			g2d.setStroke(getScaledStroke());
		if (xSpline!=null) {
			if (type==POLYLINE || type==FREELINE) {
				drawSpline(g, xSpline, ySpline, splinePoints, false, fill, isActiveOverlayRoi);
				if (wideLine && !overlay) {
					g2d.setStroke(onePixelWide);
					g.setColor(getColor());
					drawSpline(g, xSpline, ySpline, splinePoints, false, fill, isActiveOverlayRoi);
				}
			} else
				drawSpline(g, xSpline, ySpline, splinePoints, true, fill, isActiveOverlayRoi);
		} else {
			if (type==POLYLINE || type==FREELINE || type==ANGLE || state==CONSTRUCTING) {
				g.drawPolyline(xp2, yp2, nPoints);
				if (wideLine && !overlay) {
					g2d.setStroke(onePixelWide);
					g.setColor(getColor());
					g.drawPolyline(xp2, yp2, nPoints);
				}
			} else {
				if (fill) {
					if (isActiveOverlayRoi) {
						g.setColor(Color.cyan);
						g.drawPolygon(xp2, yp2, nPoints);
					} else
						g.fillPolygon(xp2, yp2, nPoints);
					if (strokeColor!=null) {
						g.setColor(strokeColor);
						g.drawPolygon(xp2, yp2, nPoints);
					}
				} else
					g.drawPolygon(xp2, yp2, nPoints);
			 }
			if (state==CONSTRUCTING && type!=FREEROI && type!=FREELINE)
				drawStartBox(g);
		}
		if (hasHandles	&& clipboard==null && !overlay) {
			if (activeHandle>0)
				drawHandle(g, xp2[activeHandle-1], yp2[activeHandle-1]);
			if (activeHandle<nPoints-1)
				drawHandle(g, xp2[activeHandle+1], yp2[activeHandle+1]);
			handleColor= strokeColor!=null? strokeColor:ROIColor; drawHandle(g, xp2[0], yp2[0]); handleColor=Color.white;
			for (int i=1; i<nPoints; i++)
				drawHandle(g, xp2[i], yp2[i]);
		}
		drawPreviousRoi(g);
		if (!(state==MOVING_HANDLE||state==CONSTRUCTING||state==NORMAL))
			showStatus();
		if (updateFullWindow) {
			updateFullWindow = false;
			imp.draw();
		}
	}

	private void drawSpline(Graphics g, float[] xpoints, float[] ypoints, int npoints, boolean closed, boolean fill, boolean isActiveOverlayRoi) {
		if (xpoints==null || xpoints.length==0)
			return;
		boolean doScaling = ic!=null; //quicker drawing if we don't need to convert to screen coordinates
		if (ic!=null) {
			Rectangle srcRect = ic.getSrcRect();
			if (srcRect!=null && srcRect.x == 0 && srcRect.y == 0 && ic!=null && ic.getMagnification()==1.0)
				doScaling = false;
		}
		double xd = getXBase();
		double yd = getYBase();
		Graphics2D g2d = (Graphics2D)g;
		GeneralPath path = new GeneralPath();
		path.moveTo(screenXD(xpoints[0]+xd), screenYD(ypoints[0]+yd));
		if (doScaling) {
			for (int i=1; i<npoints; i++)
				path.lineTo(screenXD(xpoints[i]+xd), screenYD(ypoints[i]+yd));
		} else {
			double xd1 = xd, yd1 = yd;
			if (useLineSubpixelConvention()) {xd1 += 0.5; yd1 += 0.5;}
			for (int i=1; i<npoints; i++)
				path.lineTo(xpoints[i]+xd1, ypoints[i]+yd1);
		}
		if (closed)
			path.lineTo(screenXD(xpoints[0]+xd), screenYD(ypoints[0]+yd));
		if (fill) {
			if (isActiveOverlayRoi) {
				g2d.setColor(Color.cyan);
				g2d.draw(path);
			} else
				g2d.fill(path);
			if (strokeColor!=null) {
				g.setColor(strokeColor);
				g2d.draw(path);
			}
		} else
			g2d.draw(path);
	}

	public void drawPixels(ImageProcessor ip) {
		int saveWidth = ip.getLineWidth();
		if (getStrokeWidth()>1f)
			ip.setLineWidth((int)Math.round(getStrokeWidth()));
		double xbase = getXBase();
		double ybase = getYBase();
		if (xSpline!=null) {
			ip.moveTo((int)Math.round(xbase+xSpline[0]), (int)Math.round(ybase+ySpline[0]));
			for (int i=1; i<splinePoints; i++)
				ip.lineTo((int)Math.round(xbase+xSpline[i]), (int)Math.round(ybase+ySpline[i]));
			if (type==POLYGON || type==FREEROI || type==TRACED_ROI)  // close the shape
				ip.lineTo((int)Math.round(xbase+xSpline[0]), (int)Math.round(ybase+ySpline[0]));
		} else if (xpf!=null) {
			ip.moveTo((int)Math.round(xbase+xpf[0]), (int)Math.round(ybase+ypf[0]));
			for (int i=1; i<nPoints; i++)
				ip.lineTo((int)Math.round(xbase+xpf[i]), (int)Math.round(ybase+ypf[i]));
			if (type==POLYGON || type==FREEROI || type==TRACED_ROI)  // close the shape
				ip.lineTo((int)Math.round(xbase+xpf[0]), (int)Math.round(ybase+ypf[0]));
		} else {
			ip.moveTo(x+xp[0], y+yp[0]);
			for (int i=1; i<nPoints; i++)
				ip.lineTo(x+xp[i], y+yp[i]);
			if (type==POLYGON || type==FREEROI || type==TRACED_ROI)  // close the shape
				ip.lineTo(x+xp[0], y+yp[0]);
		}
		ip.setLineWidth(saveWidth);
		updateFullWindow = true;
	}

	protected void grow(int sx, int sy) {
	// Overrides grow() in Roi class
	}


	protected void updatePolygon() {
		int basex=0, basey=0;
		if (ic!=null) {
			Rectangle srcRect = ic.getSrcRect();
			basex=srcRect.x; basey=srcRect.y;
		}
		double mag = getMagnification();
		if (mag==1.0 && basex==0 && basey==0) {
			if (xpf!=null) {
				float xbase = (float)getXBase();
				float ybase = (float)getYBase();
				for (int i=0; i<nPoints; i++) {
					xp2[i] = (int)Math.round(xpf[i]+xbase);
					yp2[i] = (int)Math.round(ypf[i]+ybase);
				}
			} else {
				for (int i=0; i<nPoints; i++) {
					xp2[i] = xp[i]+x;
					yp2[i] = yp[i]+y;
				}
			}
		} else {
			if (xpf!=null) {
				float xbase = (float)getXBase();
				float ybase = (float)getYBase();
				for (int i=0; i<nPoints; i++) {
					xp2[i] = screenXD(xpf[i]+xbase);
					yp2[i] = screenYD(ypf[i]+ybase);
				}
			} else {
				for (int i=0; i<nPoints; i++) {
					xp2[i] = screenXD(xp[i]+x);
					yp2[i] = screenYD(yp[i]+y);
				}
			}
		}
	}

	public void mouseMoved(MouseEvent e) {
		int sx = e.getX();
		int sy = e.getY();
		int flags = e.getModifiers();
		constrain = (flags&Event.SHIFT_MASK)!=0;
		if (constrain) {  // constrain in 90deg steps
			int dx = sx - previousSX;
			int dy = sy - previousSY;
			if (Math.abs(dx) > Math.abs(dy))
				dy = 0;
			else
				dx = 0;
			sx = previousSX + dx;
			sy = previousSY + dy;
		}

		// Do rubber banding
		int tool = Toolbar.getToolId();
		if (!(tool==Toolbar.POLYGON || tool==Toolbar.POLYLINE || tool==Toolbar.ANGLE)) {
			imp.deleteRoi();
			imp.draw();
			return;
		}
		if (IJ.altKeyDown())
			wipeBack();

		drawRubberBand(sx, sy);

		// show status: length & angle
		degrees = Double.NaN;
		double len = -1;
		if (nPoints>1) {
			double x1, y1, x2, y2;
			if (xpf!=null) {
				x1 = xpf[nPoints-2];
				y1 = ypf[nPoints-2];
				x2 = xpf[nPoints-1];
				y2 = ypf[nPoints-1];
			} else {
				x1 = xp[nPoints-2];
				y1 = yp[nPoints-2];
				x2 = xp[nPoints-1];
				y2 = yp[nPoints-1];
			}
			degrees = getFloatAngle(x1, y1, x2, y2);
			if (tool!=Toolbar.ANGLE) {
				Calibration cal = imp.getCalibration();
				double pw=cal.pixelWidth, ph=cal.pixelHeight;
				if (IJ.altKeyDown()) {pw=1.0; ph=1.0;}
				len = Math.sqrt((x2-x1)*pw*(x2-x1)*pw + (y2-y1)*ph*(y2-y1)*ph);
			}
		}
		if (tool==Toolbar.ANGLE) {
			if (nPoints==2)
				angle1 = degrees;
			else if (nPoints==3) {
				double angle2 = xpf != null ? getFloatAngle(xpf[1], ypf[1], xpf[2], ypf[2]) :
						getAngle(xp[1], yp[1], xp[2], yp[2]);
				degrees = Math.abs(180-Math.abs(angle1-angle2));
				if (degrees>180.0)
					degrees = 360.0-degrees;
			}
		}
		String length = len!=-1?", length=" + IJ.d2s(len):"";
		double degrees2 = tool==Toolbar.ANGLE&&nPoints==3&&Prefs.reflexAngle?360.0-degrees:degrees;
		String angle = !Double.isNaN(degrees)?", angle=" + IJ.d2s(degrees2):"";
		int ox = ic.offScreenX(sx);
		int oy = ic.offScreenY(sy);
		IJ.showStatus(imp.getLocationAsString(ox,oy) + length + angle);
	}

	//Mouse behaves like an eraser when moved backwards with alt key down.
	//Within correction circle, all vertices with sharp angles are removed.
	//Norbert Vischer
	protected void wipeBack() {
		Roi prevRoi = Roi.getPreviousRoi();
		if (prevRoi!=null && prevRoi.modState==SUBTRACT_FROM_ROI)
			return;
		double correctionRadius = 20;
		if (ic!=null)
			correctionRadius /= ic.getMagnification();
		boolean found = false;
		int p3 = nPoints - 1;
		int p1 = p3;
		while (p1 > 0 && !found) {
			p1--;
			double dx = xpf != null ? xpf[p3] - xpf[p1] : xp[p3] - xp[p1];
			double dy = xpf != null ? ypf[p3] - ypf[p1] : yp[p3] - yp[p1];
			double dist = Math.sqrt(dx * dx + dy * dy);
			if (dist > correctionRadius)
				found = true;
		}
		//examine all angles p1-p2-p3
		boolean killed = false;
		int safety = 10; //don't delete more than this number of points at once
		do {
			killed = false;
			safety--;
			for (int p2 = p1 + 1; p2 < p3; p2++) {
				double dx1 = xpf != null ? xpf[p2] - xpf[p1] : xp[p2] - xp[p1];
				double dy1 = xpf != null ? ypf[p2] - ypf[p1] : yp[p2] - yp[p1];
				double dx2 = xpf != null ? xpf[p3] - xpf[p1] : xp[p3] - xp[p1];
				double dy2 = xpf != null ? ypf[p3] - ypf[p1] : yp[p3] - yp[p1];
				double kk = 1;//allowed sharpness
				if (this instanceof FreehandRoi)
					kk = 0.8;
				if ((dx1 * dx1 + dy1 * dy1) > kk * (dx2 * dx2 + dy2 * dy2)) {
					if (xpf != null) {
						xpf[p2] = xpf[p3]; ypf[p2] = ypf[p3]; //replace sharp vertex with end point
					} else {
						xp[p2] = xp[p3]; yp[p2] = yp[p3];
					}
					p3 = p2;
					nPoints = p2 + 1; //shorten array
					killed = true;
				}
			}
		} while (killed && safety > 0);
	}

	void drawRubberBand(int sx, int sy) {
		double oxd = offScreenXD(sx);
		double oyd = offScreenYD(sy);
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		int x1, y1, x2, y2;
		if (xpf!=null) {
			x1 = (int)xpf[nPoints-2]+x;
			y1 = (int)ypf[nPoints-2]+y;
			x2 = (int)xpf[nPoints-1]+x;
			y2 = (int)ypf[nPoints-1]+y;
		} else {
			x1 = xp[nPoints-2]+x;
			y1 = yp[nPoints-2]+y;
			x2 = xp[nPoints-1]+x;
			y2 = yp[nPoints-1]+y;
		}
		int xmin=Integer.MAX_VALUE, ymin=Integer.MAX_VALUE, xmax=0, ymax=0;
		if (x1<xmin) xmin=x1;
		if (x2<xmin) xmin=x2;
		if (ox<xmin) xmin=ox;
		if (x1>xmax) xmax=x1;
		if (x2>xmax) xmax=x2;
		if (ox>xmax) xmax=ox;
		if (y1<ymin) ymin=y1;
		if (y2<ymin) ymin=y2;
		if (oy<ymin) ymin=oy;
		if (y1>ymax) ymax=y1;
		if (y2>ymax) ymax=y2;
		if (oy>ymax) ymax=oy;
		int margin = boxSize;
		if (ic!=null) {
			double mag = ic.getMagnification();
			if (mag<1.0) margin = (int)(margin/mag);
		}
		margin = (int)(margin+getStrokeWidth());
		if (IJ.altKeyDown())
			margin+=20;
		if (xpf!=null) {
			xpf[nPoints-1] = (float)(oxd-getXBase());
			ypf[nPoints-1] = (float)(oyd-getYBase());
		} else {
			xp[nPoints-1] = ox-x;
			yp[nPoints-1] = oy-y;
		}
		if (type==POLYLINE && Prefs.splineFitLines) {
			fitSpline();
			imp.draw();
		} else
			imp.draw(xmin-margin, ymin-margin, (xmax-xmin)+margin*2, (ymax-ymin)+margin*2);
	}

	void finishPolygon() {
		if (xpf!=null) {
			double xbase0 = getXBase();
			double ybase0 = getYBase();
			FloatPolygon poly = new FloatPolygon(xpf, ypf, nPoints);
			bounds = poly.getFloatBounds();
			for (int i=0; i<nPoints; i++) {         //xpf, ypf have lowest value of 0.0 sharp
				xpf[i] -= (float)bounds.x;          //(bounds.x, bounds.y will be adjusted accordingly)
				ypf[i] -= (float)bounds.y;
			}
			if (xSpline!=null) {
				for (int i=0; i<splinePoints; i++) {
					xSpline[i] += (float)(xbase0 - bounds.x);
					ySpline[i] += (float)(ybase0 - bounds.y);
				}
			}
			setIntBounds(bounds);                   //all points must be within the integer bounding rectangle
		} else {
			Polygon poly = new Polygon(xp, yp, nPoints);
			Rectangle r = poly.getBounds();
			x = r.x;
			y = r.y;
			width = r.width;
			height = r.height;
			for (int i=0; i<nPoints; i++) {
				xp[i] = xp[i]-x;
				yp[i] = yp[i]-y;
			}
			if (useLineSubpixelConvention()) {
				width++; height++;   //for open polylines & PointRois, include the pixels affected by 'draw'
			}
			bounds = null;
		}
		if (nPoints<2 || (!(type==FREELINE||type==POLYLINE||type==ANGLE) && (nPoints<3||width==0||height==0))) {
			if (imp!=null) imp.deleteRoi();
			if (type!=POINT) return;
		}
		state = NORMAL;
		if (imp!=null && !(type==TRACED_ROI))
			imp.draw(x-5, y-5, width+10, height+10);
		oldX=x; oldY=y; oldWidth=width; oldHeight=height;
		if (Recorder.record && userCreated && (type==POLYGON||type==POLYLINE||type==ANGLE
		||(type==POINT&&Recorder.scriptMode()&&nPoints==3)))
			Recorder.recordRoi(getPolygon(), type);
		if (type!=POINT) modifyRoi();
		LineWidthAdjuster.update();
		notifyListeners(RoiListener.COMPLETED);
		updateFullWindow = true;
	}

	public void exitConstructingMode() {
		if (type==POLYLINE && state==CONSTRUCTING) {
			addOffset();
			finishPolygon();
		}
	}

	protected void moveHandle(int sx, int sy) {
		if (constrain) {  // constrain in 90deg steps
			int dx = sx - previousSX;
			int dy = sy - previousSY;
			if (Math.abs(dx) > Math.abs(dy))
				dy = 0;
			else
				dx = 0;
			sx = previousSX + dx;
			sy = previousSY + dy;
		}

		if (clipboard!=null) return;

		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		if (xpf!=null) {
			xpf[activeHandle] = (float)(offScreenXD(sx)-getXBase());
			ypf[activeHandle] = (float)(offScreenYD(sy)-getYBase());
		} else {
			xp[activeHandle] = ox-x;
			yp[activeHandle] = oy-y;
		}
		if (xSpline!=null) {
			fitSpline(splinePoints);
			imp.draw();
		} else {
			if (!subPixelResolution() || (type==POINT&&nPoints==1))
				resetBoundingRect();
			if (type==POINT && width==0 && height==0)
				{width=1; height=1;}
			updateClipRectAndDraw();
		}
		String angle = type==ANGLE?getAngleAsString():"";
		IJ.showStatus(imp.getLocationAsString(ox,oy) + angle);
	}

   /** After handle is moved, find clip rect and repaint. */
   void updateClipRectAndDraw() {
		if (xpf!=null) {
			xp = toInt(xpf, xp, nPoints);
			yp = toInt(ypf, yp, nPoints);
		}
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
		int handleSize = type==POINT?getHandleSize()+25:getHandleSize();
		double strokeWidth = getStrokeWidth();
		if (strokeWidth<1.0) strokeWidth=1.0;
		if (handleSize<strokeWidth && isLine())
			handleSize = (int)strokeWidth;
		int m = mag<1.0?(int)(handleSize/mag):handleSize;
		m = (int)(m*strokeWidth);
		imp.draw(xmin2-m, ymin2-m, xmax2-xmin2+m*2, ymax2-ymin2+m*2);
	}

	protected void resetBoundingRect() {
		//IJ.log("resetBoundingRect");
		if (xpf!=null) {
			resetSubPixelBoundingRect();
			xp = toInt(xpf, xp, nPoints);
			yp = toInt(ypf, yp, nPoints);
			return;
		}
		int xmin=Integer.MAX_VALUE, xmax=-xmin, ymin=xmin, ymax=xmax;
		int xx, yy;
		for (int i=0; i<nPoints; i++) {
			xx = xp[i];
			if (xx<xmin) xmin=xx;
			if (xx>xmax) xmax=xx;
			yy = yp[i];
			if (yy<ymin) ymin=yy;
			if (yy>ymax) ymax=yy;
		}
		if (xmin!=0) {
			for (int i=0; i<nPoints; i++)
				xp[i] -= xmin;
		}
		if (ymin!=0) {
			for (int i=0; i<nPoints; i++)
				yp[i] -= ymin;
		}
		//IJ.log("reset: "+ymin+" "+before+" "+yp[0]);
		x+=xmin; y+=ymin;
		width=xmax-xmin; height=ymax-ymin;
		if (useLineSubpixelConvention()) {
			width++; height++;    //for open polylines & PointRois, include the pixels affected by 'draw'
		}
		bounds = null;
	}

	private void resetSubPixelBoundingRect() {
		//IJ.log("resetSubPixelBoundingRect: "+state+" "+bounds);
		if (xSpline!=null) {
			resetSplineFitBoundingRect();
			return;
		}
		float xbase = (float)getXBase();
		float ybase = (float)getYBase();
		for (int i=0; i<nPoints; i++) {
			xpf[i] = xpf[i]+xbase;
			ypf[i] = ypf[i]+ybase;
		}
		FloatPolygon poly = new FloatPolygon(xpf, ypf, nPoints);
		bounds = poly.getFloatBounds();
		setIntBounds(bounds);
		xbase = (float)bounds.x;
		ybase = (float)bounds.y;
		for (int i=0; i<nPoints; i++) {
			xpf[i] -= xbase;
			ypf[i] -= ybase;
		}
	}

	private void resetSplineFitBoundingRect() {
		if (splinePoints==0)
			return;
		float xbase = (float)getXBase();
		float ybase = (float)getYBase();
		float xSpline0 = xSpline[0];
		float ySpline0 = ySpline[0];
		for (int i=0; i<splinePoints; i++) {
			xSpline[i] = xSpline[i]+xbase;
			ySpline[i] = ySpline[i]+ybase;
		}
		FloatPolygon poly = new FloatPolygon(xSpline, ySpline, splinePoints);
		bounds = poly.getFloatBounds();
		setIntBounds(bounds);
		xbase = (float)bounds.x;
		ybase = (float)bounds.y;
		for (int i=0; i<splinePoints; i++) {
			xSpline[i] -= xbase;
			ySpline[i] -= ybase;
		}
		for (int i=0; i<nPoints; i++) {
			xpf[i] -= xSpline0 - xSpline[0];
			ypf[i] -= ySpline0 - ySpline[0];
		}
	}

	String getAngleAsString() {
		double angle1 = 0.0;
		double angle2 = 0.0;
		if (xpf!=null) {
			angle1 = getFloatAngle(xpf[0], ypf[0], xpf[1], ypf[1]);
			angle2 = getFloatAngle(xpf[1], ypf[1], xpf[2], ypf[2]);
		} else {
			angle1 = getFloatAngle(xp[0], yp[0], xp[1], yp[1]);
			angle2 = getFloatAngle(xp[1], yp[1], xp[2], yp[2]);
		}
		degrees = Math.abs(180-Math.abs(angle1-angle2));
		if (degrees>180.0)
			degrees = 360.0-degrees;
		double degrees2 = Prefs.reflexAngle&&type==ANGLE?360.0-degrees:degrees;
		return ", angle=" + IJ.d2s(degrees2);
	}

   protected void mouseDownInHandle(int handle, int sx, int sy) {
		if (state==CONSTRUCTING)
			return;
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		double oxd = offScreenXD(sx);
		double oyd = offScreenYD(sy);
		if ((IJ.altKeyDown()||IJ.controlKeyDown()) && !(nPoints<=3 && type!=POINT) && !(this instanceof RotatedRectRoi)) {
			deleteHandle(oxd, oyd);
			return;
		} else if (IJ.shiftKeyDown() && type!=POINT && !(this instanceof RotatedRectRoi)) {
			addHandle(oxd, oyd);
			return;
		}
		super.mouseDownInHandle(handle, sx, sy); //sets state, activeHandle, previousSX&Y
		int m = ic!=null?(int)(10.0/ic.getMagnification()):1;
		xClipMin=ox-m; yClipMin=oy-m; xClipMax=ox+m; yClipMax=oy+m;
	}

	public void deleteHandle(double ox, double oy) {
		if (imp==null)
			return;
		if (nPoints<=1) {
			imp.deleteRoi();
			return;
		}
		boolean splineFit = xSpline!=null;
		if (splineFit)
			removeSplineFit();
		FloatPolygon points = getFloatPolygon();
		int pointToDelete = getClosestPoint(ox, oy, points);
		if (pointToDelete>=0) {
			deletePoint(pointToDelete);
			if (splineFit)
				fitSpline(splinePoints);
			imp.draw();
		}
	}

	protected void deletePoint(int index) {
		if (index<0 || index>=nPoints)
			return;
		for (int i=index; i<nPoints-1; i++) {
			if (xp!=null) {
				xp[i] = xp[i+1];
				yp[i] = yp[i+1];
			}
			if (xp2!=null) {
				xp2[i] = xp2[i+1];
				yp2[i] = yp2[i+1];
			}
			if (xpf!=null) {
				xpf[i] = xpf[i+1];
				ypf[i] = ypf[i+1];
			}
		}
		nPoints--;
	}

	void addHandle(double ox, double oy) {
		if (imp==null || type==ANGLE) return;
		boolean splineFit = xSpline != null;
		xSpline = null;
		FloatPolygon points = getFloatPolygon();
		int n = points.npoints;
		modState = NO_MODS;
		Roi prevRoi = Roi.getPreviousRoi();
		if (prevRoi!=null) prevRoi.modState = NO_MODS;
		int pointToDuplicate = getClosestPoint(ox, oy, points);
		if (pointToDuplicate<0)
			return;
		FloatPolygon points2 = new FloatPolygon();
		for (int i2=0; i2<n; i2++) {
			if (i2==pointToDuplicate) {
				int i1 = i2-1;
				if (i1==-1) i1 = isLine()?i2:n-1;
				int i3 = i2+1;
				if (i3==n) i3 = isLine()?i2:0;
				double x1 = points.xpoints[i1]	+ 2*(points.xpoints[i2] - points.xpoints[i1])/3;
				double y1 = points.ypoints[i1] + 2*(points.ypoints[i2] - points.ypoints[i1])/3;
				double x2 = points.xpoints[i2] + (points.xpoints[i3] - points.xpoints[i2])/3;
				double y2 = points.ypoints[i2] + (points.ypoints[i3] - points.ypoints[i2])/3;
				points2.addPoint(x1, y1);
				points2.addPoint(x2, y2);
			} else
				points2.addPoint(points.xpoints[i2], points.ypoints[i2]);
		}
		if (type==POINT)
			imp.setRoi(new PointRoi(points2));
		else {
			setPolygon(points2);
			if (splineFit)
				fitSpline(splinePoints);
			if (imp!=null) imp.draw();
		}
	}

	private void setPolygon(FloatPolygon p2) {
		nPoints = p2.npoints;
		if (nPoints>=maxPoints)
			enlargeArrays();
		float xbase = (float)getXBase();
		float ybase = (float)getYBase();
		if (xp==null) {
			xp = new int[maxPoints];
			yp = new int[maxPoints];
		}
		for (int i=0; i<nPoints; i++) {
			xp[i] = (int)(p2.xpoints[i]-x);
			yp[i] = (int)(p2.ypoints[i]-y);
			if (xpf!=null) {
				xpf[i] = p2.xpoints[i] - xbase;
				ypf[i] = p2.ypoints[i] - ybase;
			}
		}
	}

	protected int getClosestPoint(double x, double y, FloatPolygon points) {
		int index = -1;
		double distance = Double.MAX_VALUE;
		for (int i=0; i<points.npoints; i++) {
			double dx = points.xpoints[i] - x;
			double dy = points.ypoints[i] - y;
			double distance2 = dx*dx+dy*dy;
			if (distance2<distance) {
				distance = distance2;
				index = i;
			}
		}
		return index;
	}

	/** Fits a spline, which becomes the new shape of this Roi */
	public void fitSpline(int evaluationPoints) {
		float[][] spline = getSpline(evaluationPoints, xSpline, ySpline);
		setSpline(spline[0], spline[1]);
	}

	/** Sets the spline as a new shape of this Roi */
	void setSpline(float[] xSpline, float[] ySpline) {
		this.xSpline = xSpline;
		this.ySpline = ySpline;
		splinePoints = xSpline.length;
		cachedMask = null;
		// update protected xp and yp arrays for backward compatibility
		xp = toInt(xpf, xp, nPoints);
		yp = toInt(ypf, yp, nPoints);
		if (state==NORMAL)
			resetBoundingRect();
	}

	/** Fits a spline, but leaves the Roi unchanged.
	 *  The arrays supplied (which may be null) are reused if the number of spline
	 *  points remains unchanged.
	 *  @return Arrays of x coordinates and y coordinates */
	float[][] getSpline(int evaluationPoints, float[] xSpline, float[] ySpline) {
		if (xpf==null) {
			xpf = toFloat(xp);
			ypf = toFloat(yp);
			enableSubPixelResolution();
		}
		if (xSpline==null || xSpline.length!=evaluationPoints)
			xSpline = new float[evaluationPoints];
		if (ySpline==null || ySpline.length!=evaluationPoints)
			ySpline = new float[evaluationPoints];
		int nNodes = isLine() ? nPoints : nPoints+1;
		double length = getUncalibratedLength();
		float[] nodePositions = new float[nNodes];
		float lastNodePosition = 0f;		//independent coordinate for x & y-splines,
		nodePositions[0] = 0f;				//incremented by the sqrt of the distance between points
		for (int i=1; i<nPoints; i++) {
			float dx = xpf[i] - xpf[i-1];
			float dy = ypf[i] - ypf[i-1];
			float dLength = (float)Math.sqrt(Math.sqrt(dx*dx+dy*dy));
			if (dLength < 0.001f) dLength = 0.001f; //avoid numerical problems with duplicate points
			lastNodePosition += dLength;
			nodePositions[i] = lastNodePosition;
		}
		if (!isLine()) {					//closed polygon: close the line
			float dx = xpf[nPoints-1] - xpf[0];
			float dy = ypf[nPoints-1] - ypf[0];
			float dLength = (float)Math.sqrt(Math.sqrt(dx*dx+dy*dy));
			if (dLength < 0.001f) dLength = 0.001f;
			lastNodePosition += dLength;
			nodePositions[nNodes-1] = lastNodePosition;
			if (xpf.length < nNodes) enlargeArrays(nNodes);
			xpf[nNodes-1] = xpf[0];
			ypf[nNodes-1] = ypf[0];
		}
		SplineFitter sfx = new SplineFitter(nodePositions, xpf, nNodes, !isLine());
		SplineFitter sfy = new SplineFitter(nodePositions, ypf, nNodes, !isLine());

		// Evaluate the splines at all points
		double scale = (double)lastNodePosition/(evaluationPoints-1);
		for(int i=0; i<evaluationPoints; i++) {
			double xvalue = i*scale;
			xSpline[i] = (float)sfx.evalSpline(xvalue);
			ySpline[i] = (float)sfy.evalSpline(xvalue);
		}
		return new float[][] {xSpline, ySpline};
	}

	/** Fits a spline, which becomes the new shape of this Roi */
	public void fitSpline() {
		double length = getUncalibratedLength();
		int evaluationPoints = (int)(length/2.0);
		if (ic!=null) {
			double mag = ic.getMagnification();
			if (mag<1.0)
				evaluationPoints *= mag;;
		}
		if (evaluationPoints<100)
			evaluationPoints = 100;
		fitSpline(evaluationPoints);
	}

	public void removeSplineFit() {
		xSpline = null;
		ySpline = null;
	}

	/** Returns 'true' if this selection has been fitted with a spline. */
	public boolean isSplineFit() {
		return xSpline!=null;
	}

	/** Creates a spline fitted polygon with (roughly) one pixel segment lengths
		and sets it as the Roi shape.
		It can be retrieved using the getFloatPolygon() method. */
	public void fitSplineForStraightening() {
		 //create preliminary finer splines with half-pixel steps (these do not change asynchronously)
		float[][] spline = getSpline((int)(getUncalibratedLength()*2)+1, null, null);
		spline = getEquidistantPoints(spline[0], spline[1], spline[0].length, 1.0, null);
		setSpline(spline[0], spline[1]);
	}

	/** Interpolates an open polgonial shape to get line segments of (roughly) length 'segmentLength',
	 *  and returns arrays of x & y coordinates.
	 *  Since the full length is not necessarily an integer multiple of 'segmentLength',
	 *  the segmentLength actually used is returned as array element [2][0].
	 *  If the ImagePlus is given, calibrated coordinates are used, based on its calibration,
	 *  and 'segmentLength' should be in calibrated units.
	 *  Returns null if an input array is null, or the number of points is 0 */
	static float[][] getEquidistantPoints(float[] xpoints, float[] ypoints, int npoints, double segmentLength, ImagePlus imp) {
		if (xpoints==null || xpoints==null || npoints <= 0) return null;
		if (xpoints.length < npoints) npoints = xpoints.length;	// arguments might be inconsistent due to asynchronous modification
		if (ypoints.length < npoints) npoints = ypoints.length;
		double length = getLength(xpoints, ypoints, npoints, /*closeShape=*/false, imp);
		int npOut = (int)Math.round(length/segmentLength)+1;
		double step = length/(npOut-1);                  //as close to one pixel as we can get
		float[] xpOut = new float[npOut];
		float[] ypOut = new float[npOut];
		double pixelWidth = 1.0, pixelHeight = 1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pixelWidth = cal.pixelWidth;
			pixelHeight = cal.pixelHeight;
		}
		xpOut[0] = xpoints[0];
		ypOut[0] = ypoints[0];
		double lengthRead = 0;   //total arc length read from the preliminary spline
		int pointsWritten = 1;
		double x1, y1;
		double x2 = xpoints[0], y2 = ypoints[0];
		for (int i=1; i<npoints; i++) {
			x1=x2; y1=y2;
			x2=xpoints[i]; y2=ypoints[i];
			double dx = x2-x1;
			double dy = y2-y1;
			double distance = Math.sqrt(sqr(dx*pixelWidth) + sqr(dy*pixelHeight));
			lengthRead += distance;
			double distanceOverNextWrite = lengthRead - pointsWritten*step;
			while ((distanceOverNextWrite >= 0.0 || i==npoints-1) && pointsWritten < npOut) {  // we have to write a new point
				double fractionOverNextWrite = distanceOverNextWrite/distance;
				if (distance==0) fractionOverNextWrite = 0;
				//IJ.log("i="+i+" n="+pointsWritten+"/"+npOut+" leng="+IJ.d2s(lengthRead)+"/"+IJ.d2s(length)+" done="+IJ.d2s(pointsWritten*step)+" over="+IJ.d2s(fractionOverNextWrite)+" x,y="+IJ.d2s(x2 - fractionOverNextWrite*dx)+","+IJ.d2s(y2 - fractionOverNextWrite*dy));
				xpOut[pointsWritten] = (float)(x2 - fractionOverNextWrite*dx);
				ypOut[pointsWritten] = (float)(y2 - fractionOverNextWrite*dy);
				distanceOverNextWrite -= step;
				pointsWritten++;
			}
		}
		return new float[][] {xpOut, ypOut, new float[] {(float)step}};
	}

	/** With segmented selections, ignore first mouse up and finalize
		when user double-clicks, control-clicks or clicks in start box. */
	protected void handleMouseUp(int sx, int sy) {
		if (state==MOVING) {
			state = NORMAL;
			return;
		}
		if (state==MOVING_HANDLE) {
			cachedMask = null; //mask is no longer valid
			state = NORMAL;
			updateClipRect();
			oldX=x; oldY=y;
			oldWidth=width; oldHeight=height;
			if (subPixelResolution())
				resetBoundingRect();
			return;
		}
		if (state!=CONSTRUCTING)
			return;
		if (IJ.spaceBarDown()) // is user scrolling image?
			return;
		boolean samePoint = false;
		if (xpf!=null)
			samePoint = (xpf[nPoints-2]==xpf[nPoints-1] && ypf[nPoints-2]==ypf[nPoints-1]);
		else
			samePoint = (xp[nPoints-2]==xp[nPoints-1] && yp[nPoints-2]==yp[nPoints-1]);
		boolean doubleClick = (System.currentTimeMillis()-mouseUpTime)<=300;
		int size = boxSize+2;
		int size2 = boxSize/2 +1;
		Rectangle biggerStartBox = new Rectangle(screenXD(startXD)-5, screenYD(startYD)-5, 10, 10);
		if (nPoints>2 && (biggerStartBox.contains(sx, sy)
		|| (offScreenXD(sx)==startXD && offScreenYD(sy)==startYD)
		|| (samePoint && doubleClick))) {
			boolean okayToFinish = true;
			if (type==POLYGON && samePoint && doubleClick && nPoints>25) {
				okayToFinish = IJ.showMessageWithCancel("Polygon Tool", "Complete the selection?");
			}
			if (okayToFinish) {
				nPoints--;
				addOffset();
				finishPolygon();
				return;
			}
		} else if (!samePoint) {
			mouseUpTime = System.currentTimeMillis();
			if (type==ANGLE && nPoints==3) {
				addOffset();
				finishPolygon();
				return;
			}
			//add point to polygon
			if (xpf!=null) {
				xpf[nPoints] = xpf[nPoints-1];
				ypf[nPoints] = ypf[nPoints-1];
				nPoints++;
				if (nPoints==xpf.length)
					enlargeArrays();
			} else {
				xp[nPoints] = xp[nPoints-1];
				yp[nPoints] = yp[nPoints-1];
				nPoints++;
				if (nPoints==xp.length)
					enlargeArrays();
			}
			if (constrain) {  // this point was constrained in 90deg steps; correct coordinates
				int dx = sx - previousSX;
				int dy = sy - previousSY;
				if (Math.abs(dx) > Math.abs(dy))
					dy = 0;
				else
					dx = 0;
				sx = previousSX + dx;
				sy = previousSY + dy;
			}
			previousSX = sx;  //save for constraining next line if desired
			previousSY = sy;
			notifyListeners(RoiListener.EXTENDED);
		}
	}

	protected void addOffset() {
		if (xpf!=null) {
			float xbase = (float)getXBase();
			float ybase = (float)getYBase();
			for (int i=0; i<nPoints; i++) {
				xpf[i] = xpf[i]+xbase;
				ypf[i] = ypf[i]+ybase;
			}
		} else {
			for (int i=0; i<nPoints; i++) {
				xp[i] = xp[i]+x;
				yp[i] = yp[i]+y;
			}
		}
	}
	/** Returns whether the center of pixel (x,y) is contained in the Roi.
	 *  The position of a pixel center determines whether a pixel is selected.
	 *  Note the ImageJ convention of 0.5 pixel shift between outline and pixel centers,
	 *  i.e., pixel (0,0) is enclosed by the rectangle spanned between (0,0) and (1,1).
	 *  Points exactly at the left (right) border are considered outside (inside);
	 *  points exactly on horizontal borders, are considered outside (inside) at the border
	 *  with the lower (higher) y.
	 *  This convention is opposite to that of the java.awt.Shape class.
	 *  In x, the offset is chosen slightly below 0.5 to reduce the impact of numerical
	 *  errors. */
	public boolean contains(int x, int y) {
		if (!super.contains(x, y))
			return false;
		if (xSpline!=null) {
			FloatPolygon poly = new FloatPolygon(xSpline, ySpline, splinePoints);
			return poly.contains(x - getXBase() + 0.4999, y - getYBase() + 0.5);
		} else if (xpf!=null) {
			FloatPolygon poly = new FloatPolygon(xpf, ypf, nPoints);
			return poly.contains(x - getXBase() + 0.4999, y - getYBase() + 0.5);
		} else {
			Polygon poly = new Polygon(xp, yp, nPoints);
			return poly.contains(x - this.x + 0.4999, y - this.y + 0.5);
		}
	}

	/** Returns whether coordinate (x,y) is contained in the Roi.
	 *  Note that the coordinate (0,0) is the top-left corner of pixel (0,0).
	 *  Use contains(int, int) to determine whether a given pixel is contained in the Roi. */
	public boolean containsPoint(double x, double y) {
		if (!super.containsPoint(x, y))
			return false;
		if (xSpline!=null) {
			FloatPolygon poly = new FloatPolygon(xSpline, ySpline, splinePoints);
			return poly.contains(x - getXBase() +1e-7, y - getYBase() + 1e-10);
		} else if (xpf!=null) {
			FloatPolygon poly = new FloatPolygon(xpf, ypf, nPoints);
			return poly.contains(x - getXBase() + 1e-7, y - getYBase() + 1e-10);
		} else {
			Polygon poly = new Polygon(xp, yp, nPoints);
			return poly.contains(x - this.x + 1e-7, y - this.y + 1e-10);
		}
	}


	/** Returns a handle number if the specified screen coordinates are
		inside or near a handle, otherwise returns -1. */
	public int isHandle(int sx, int sy) {
		if (!(xSpline!=null||type==POLYGON||type==POLYLINE||type==ANGLE||type==POINT)||clipboard!=null)
		   return -1;
		int size = getHandleSize()+5;
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

	public ImageProcessor getMask() {
		ImageProcessor mask = cachedMask;
		if (mask!=null && mask.getPixels()!=null
		&& mask.getWidth()==width && mask.getHeight()==height)
			return mask;
		PolygonFiller pf = new PolygonFiller();
		if (xSpline!=null)
			pf.setPolygon(xSpline, ySpline, splinePoints, getXBase()-x, getYBase()-y);
		else if (xpf!=null)
			pf.setPolygon(xpf, ypf, nPoints, getXBase()-x, getYBase()-y);
		else
			pf.setPolygon(xp, yp, nPoints);
		mask = pf.getMask(width, height);
		cachedMask = mask;
		return mask;
	}

	/** Returns the length of this line selection after
		smoothing using a 3-point running average.*/
	double getSmoothedLineLength(ImagePlus imp) {
		if (subPixelResolution() && xpf!=null)
			return getFloatSmoothedLineLength(imp);
		double length = 0.0;
		double w2 = 1.0;
		double h2 = 1.0;
		double dx, dy;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			w2 = cal.pixelWidth*cal.pixelWidth;
			h2 = cal.pixelHeight*cal.pixelHeight;
		}
		dx = (xp[0]+xp[1]+xp[2])/3.0-xp[0];
		dy = (yp[0]+yp[1]+yp[2])/3.0-yp[0];
		length += Math.sqrt(dx*dx*w2+dy*dy*h2);
		for (int i=1; i<nPoints-2; i++) {
			dx = (xp[i+2]-xp[i-1])/3.0; // = (x[i]+x[i+1]+x[i+2])/3-(x[i-1]+x[i]+x[i+1])/3
			dy = (yp[i+2]-yp[i-1])/3.0; // = (y[i]+y[i+1]+y[i+2])/3-(y[i-1]+y[i]+y[i+1])/3
			length += Math.sqrt(dx*dx*w2+dy*dy*h2);
		}
		dx = xp[nPoints-1]-(xp[nPoints-3]+xp[nPoints-2]+xp[nPoints-1])/3.0;
		dy = yp[nPoints-1]-(yp[nPoints-3]+yp[nPoints-2]+yp[nPoints-1])/3.0;
		length += Math.sqrt(dx*dx*w2+dy*dy*h2);
		return length;
	}

	double getFloatSmoothedLineLength(ImagePlus imp) {
		double length = 0.0;
		double w2 = 1.0;
		double h2 = 1.0;
		double dx, dy;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			w2 = cal.pixelWidth*cal.pixelWidth;
			h2 = cal.pixelHeight*cal.pixelHeight;
		}
		dx = (xpf[0]+xpf[1]+xpf[2])/3.0-xpf[0];
		dy = (ypf[0]+ypf[1]+ypf[2])/3.0-ypf[0];
		length += Math.sqrt(dx*dx*w2+dy*dy*h2);
		for (int i=1; i<nPoints-2; i++) {
			dx = (xpf[i+2]-xpf[i-1])/3.0;
			dy = (ypf[i+2]-ypf[i-1])/3.0;
			length += Math.sqrt(dx*dx*w2+dy*dy*h2);
		}
		dx = xpf[nPoints-1]-(xpf[nPoints-3]+xpf[nPoints-2]+xpf[nPoints-1])/3.0;
		dy = ypf[nPoints-1]-(ypf[nPoints-3]+ypf[nPoints-2]+ypf[nPoints-1])/3.0;
		length += Math.sqrt(dx*dx*w2+dy*dy*h2);
		return length;
	}

	/** Returns the perimeter of this ROI after
		smoothing using a 3-point running average.*/
	double getSmoothedPerimeter(ImagePlus imp) {
		if (subPixelResolution() && xpf!=null)
			return getFloatSmoothedPerimeter(imp);
		double length = getSmoothedLineLength(imp);
		double w2=1.0, h2=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			w2 = cal.pixelWidth*cal.pixelWidth;
			h2 = cal.pixelHeight*cal.pixelHeight;
		}
		double dx = xp[nPoints-1]-xp[0];
		double dy = yp[nPoints-1]-yp[0];
		length += Math.sqrt(dx*dx*w2+dy*dy*h2);
		return length;
	}

	double getFloatSmoothedPerimeter(ImagePlus imp) {
		double length = getSmoothedLineLength(imp);
		double w2=1.0, h2=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			w2 = cal.pixelWidth*cal.pixelWidth;
			h2 = cal.pixelHeight*cal.pixelHeight;
		}
		double dx = xpf[nPoints-1]-xpf[0];
		double dy = ypf[nPoints-1]-ypf[0];
		length += Math.sqrt(dx*dx*w2+dy*dy*h2);
		return length;
	}

	/** Returns the perimeter length of ROIs created using the wand tool
	 *  and the particle analyzer. The algorithm counts pixels in straight edges
	 *  as 1 and pixels in corners as sqrt(2).
	 *  It does this by calculating the total length of the ROI boundary and subtracting
	 *  2-sqrt(2) for each non-adjacent corner. For example, a 1x1 pixel
	 *  ROI has a boundary length of 4 and 2 non-adjacent edges so the
	 *  perimeter is 4-2*(2-sqrt(2)). A 2x2 pixel ROI has a boundary length
	 *  of 8 and 4 non-adjacent edges so the perimeter is 8-4*(2-sqrt(2)).
	 *  Note that this code can currently create inconsistent legths depending on
	 *  the starting position.
	 */
	double getTracedPerimeter(ImagePlus imp) {
		if (xp==null)
			return Double.NaN;
		if (nPoints < 4) return 0;
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
		return getLength(imp);
	}

	public double getUncalibratedLength() {
		return getLength(null);
	}

	/** Returns the perimeter (for ROIs) or length (for lines), using calibration if imp is not null */
	double getLength(ImagePlus imp) {
		if (isTraced())
			return getTracedPerimeter(imp);

		if (nPoints>2) {
			if (type==FREEROI)
				return getSmoothedPerimeter(imp);
			else if (type==FREELINE && !(width==0 || height==0))
				return getSmoothedLineLength(imp);
		}

		boolean closeShape = isArea();
		if (xSpline!=null)
			return getLength(xSpline, ySpline, splinePoints, closeShape, imp);
		else if (xpf!=null)
			return getLength(xpf, ypf, nPoints, closeShape, imp);
		else
			return getLength(xp, yp, nPoints, closeShape, imp);
	}
	
	/** Returns 'true' if this ROI was created using
	 *  the wand tool or the particle analyzer.
	*/
	private boolean isTraced() {
		if (type==TRACED_ROI)
			return true;
		else if (type!=POLYGON)
			return false;
		else if (xp==null) {
			if (xpf==null)
				return false;
			for (int i=0; i<nPoints; i++) {
				int nexti = i+1;
				if (nexti==nPoints)
				  nexti = 0;
				if (!((xpf[nexti]-xpf[i])==0||(ypf[nexti]-ypf[i])==0))
					return false;
				if ((xpf[i]!=(int)xpf[i])||(ypf[i]!=(int)ypf[i]))
					return false;
			}
			xp = toInt(xpf, xp, nPoints);
			yp = toInt(ypf, yp, nPoints);
		}
		for (int i=0; i<nPoints; i++) {
			int nexti = i+1;
			if (nexti==nPoints)
			  nexti = 0;
			if (!((xp[nexti]-xp[i])==0||(yp[nexti]-yp[i])==0))
				return false;
		}
		return true;
	}

	/** Returns the length of a polygon with integer coordinates. Uses no calibration if imp is null. */
	static double getLength(int[] xpoints, int[] ypoints, int npoints, boolean closeShape, ImagePlus imp) {
		if (npoints < 2) return 0;
		double pixelWidth = 1.0, pixelHeight = 1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pixelWidth = cal.pixelWidth;
			pixelHeight = cal.pixelHeight;
		}
		double length = 0;
		for (int i=0; i<npoints-1; i++)
			length += Math.sqrt(sqr((xpoints[i+1] - xpoints[i])*pixelWidth) +
					sqr((ypoints[i+1] - ypoints[i])*pixelHeight));
		if (closeShape)
			length += Math.sqrt(sqr((xpoints[0] - xpoints[npoints-1])*pixelWidth) +
					sqr((ypoints[0] - ypoints[npoints-1])*pixelHeight));
		return length;
	}

	/** Returns the length of a polygon with float coordinates. Uses no calibration if imp is null. */
	static double getLength(float[] xpoints, float[] ypoints, int npoints, boolean closeShape, ImagePlus imp) {
		if (npoints < 2) return 0;
		double pixelWidth = 1.0, pixelHeight = 1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pixelWidth = cal.pixelWidth;
			pixelHeight = cal.pixelHeight;
		}
		double length = 0;
		for (int i=0; i<npoints-1; i++)
			length += Math.sqrt(sqr((xpoints[i+1] - xpoints[i])*pixelWidth) +
					sqr((ypoints[i+1] - ypoints[i])*pixelHeight));
		if (closeShape)
			length += Math.sqrt(sqr((xpoints[0] - xpoints[npoints-1])*pixelWidth) +
					sqr((ypoints[0] - ypoints[npoints-1])*pixelHeight));
		return length;
	}

	/** Returns the angle in degrees between the first two segments of this polyline.*/
	public double getAngle() {
		return degrees;
	}

	/** Returns the number of points that define this PolygonRoi.
		@see #getNonSplineCoordinates
	*/
	public int getNCoordinates() {
		if (xSpline!=null)
			return splinePoints;
		else
			return nPoints;
	}

	/** Obsolete; replaced by either getPolygon() or getFloatPolygon(). */
	public int[] getXCoordinates() {
		if (xSpline!=null)
			return toIntR(xSpline);
		else if (xpf!=null)
			return toIntR(xpf);
		else
			return xp;
	}

	/** Obsolete; replaced by either getPolygon() or getFloatPolygon(). */
	public int[] getYCoordinates() {
		if (xSpline!=null)
			return toIntR(ySpline);
		else if (ypf!=null)
			return toIntR(ypf);
		else
			return yp;
	}

	public Polygon getNonSplineCoordinates() {
		if (xpf!=null)
			return new Polygon(toIntR(xpf), toIntR(ypf), nPoints);
		else
			return new Polygon(xp, yp, nPoints);
	}

	public FloatPolygon getNonSplineFloatPolygon() {
		if (xpf!=null) {
			FloatPolygon p = (new FloatPolygon(xpf, ypf, nPoints)).duplicate();
			float xbase = (float)getXBase();
			float ybase = (float)getYBase();
			for (int i=0; i<p.npoints; i++) {
				p.xpoints[i] += xbase;
				p.ypoints[i] += ybase;
			}
			return p;
		} else
			return getFloatPolygon();
	}

	/** Returns this PolygonRoi as a Polygon.
		@see ij.process.ImageProcessor#setRoi
		@see ij.process.ImageProcessor#drawPolygon
		@see ij.process.ImageProcessor#fillPolygon
	*/
	public Polygon getPolygon() {
		int n;
		int[] xpoints1, ypoints1;
		if (xSpline!=null) {
			n = splinePoints;
			xpoints1 = toInt(xSpline);
			ypoints1 = toInt(ySpline);
		} else if (xpf!=null) {
			n = nPoints;
			xpoints1 = toIntR(xpf);
			ypoints1 = toIntR(ypf);
		} else {
			n = nPoints;
			xpoints1 = xp;
			ypoints1 = yp;
		}
		int[] xpoints2 = new int[n];
		int[] ypoints2 = new int[n];
		for (int i=0; i<n; i++) {
			xpoints2[i] = xpoints1[i] + x;
			ypoints2[i] = ypoints1[i] + y;
		}
		return new Polygon(xpoints2, ypoints2, n);
	}

	/** Returns this polygon or polyline as float arrays in image pixel coordinates. */
	public FloatPolygon getFloatPolygon() {
		int n = xSpline!=null?splinePoints:nPoints;
		float[] xpoints2 = new float[n];
		float[] ypoints2 = new float[n];
		float xbase = (float)getXBase();
		float ybase = (float)getYBase();
		if (xSpline!=null) {
			for (int i=0; i<n; i++) {
				xpoints2[i] = xSpline[i] + xbase;
				ypoints2[i] = ySpline[i] + ybase;
			}
		} else if (xpf!=null) {
			for (int i=0; i<n; i++) {
				xpoints2[i] = xpf[i] + xbase;
				ypoints2[i] = ypf[i] + ybase;
			}
		} else {
			for (int i=0; i<n; i++) {
				xpoints2[i] = xp[i] + x;
				ypoints2[i] = yp[i] + y;
			}
		}
		return new FloatPolygon(xpoints2, ypoints2, n);
	}

	/** Returns the number of points in this selection; equivalent to getPolygon().npoints. */
	public int size() {
		return xSpline!=null?splinePoints:nPoints;
	}

	public FloatPolygon getInterpolatedPolygon(double interval, boolean smooth) {
		FloatPolygon p = getFloatPolygon();
		if (smooth && (type==TRACED_ROI || type==FREEROI || type==FREELINE)) {
			for (int i=1; i<p.npoints-2; i++) {
				p.xpoints[i] = (p.xpoints[i-1]+p.xpoints[i]+p.xpoints[i+1])/3f;
				p.ypoints[i] = (p.ypoints[i-1]+p.ypoints[i]+p.ypoints[i+1])/3f;
			}
			if (type!=FREELINE) {
				p.xpoints[0] = (p.xpoints[p.npoints-1]+p.xpoints[0]+p.xpoints[1])/3f;
				p.ypoints[0] = (p.ypoints[p.npoints-1]+p.ypoints[0]+p.ypoints[1])/3f;
				p.xpoints[p.npoints-1] = (p.xpoints[p.npoints-2]+p.xpoints[p.npoints-1]+p.xpoints[0])/3f;
				p.ypoints[p.npoints-1] = (p.ypoints[p.npoints-2]+p.ypoints[p.npoints-1]+p.ypoints[0])/3f;
			}
		}
		return super.getInterpolatedPolygon(p, interval, smooth);
	}

	protected int clipRectMargin() {
		return type==POINT?4:0;
	}

	/** Returns a copy of this PolygonRoi. */
	public synchronized Object clone() {
		PolygonRoi r = (PolygonRoi)super.clone();
		if (xpf!=null) {
			r.xpf = new float[maxPoints];
			r.ypf = new float[maxPoints];
		} else {
			r.xp = new int[maxPoints];
			r.yp = new int[maxPoints];
		}
		r.xp2 = new int[maxPoints];
		r.yp2 = new int[maxPoints];
		for (int i=0; i<nPoints; i++) {
			if (xpf!=null) {
				r.xpf[i] = xpf[i];
				r.ypf[i] = ypf[i];
			} else {
				r.xp[i] = xp[i];
				r.yp[i] = yp[i];
			}
			r.xp2[i] = xp2[i];
			r.yp2[i] = yp2[i];
		}
		if (xSpline!=null) {
			r.xSpline = new float[splinePoints];
			r.ySpline = new float[splinePoints];
			r.splinePoints = splinePoints;
			for (int i=0; i<splinePoints; i++) {
				r.xSpline[i] = xSpline[i];
				r.ySpline[i] = ySpline[i];
			}
		}
		return r;
	}

	void enlargeArrays() {
		enlargeArrays(maxPoints*2);
	}

	void enlargeArrays(int newSize) {
		if (xp!=null) {
			int[] xptemp = new int[newSize];
			int[] yptemp = new int[newSize];
			System.arraycopy(xp, 0, xptemp, 0, maxPoints);
			System.arraycopy(yp, 0, yptemp, 0, maxPoints);
			xp=xptemp; yp=yptemp;
		}
		if (xpf!=null) {
			float[] xpftemp = new float[newSize];
			float[] ypftemp = new float[newSize];
			System.arraycopy(xpf, 0, xpftemp, 0, maxPoints);
			System.arraycopy(ypf, 0, ypftemp, 0, maxPoints);
			xpf=xpftemp; ypf=ypftemp;
		}
		int[] xp2temp = new int[newSize];
		int[] yp2temp = new int[newSize];
		System.arraycopy(xp2, 0, xp2temp, 0, maxPoints);
		System.arraycopy(yp2, 0, yp2temp, 0, maxPoints);
		xp2=xp2temp; yp2=yp2temp;
		if (IJ.debugMode) IJ.log("PolygonRoi: "+maxPoints+" points -> "+newSize);
		maxPoints = newSize;
	}

	public void setLocation(double x, double y) {
		super.setLocation(x, y);
		if ((int)x!=x || (int)y!=y)
			enableSubPixelResolution();
	}

	public void enableSubPixelResolution() {
		super.enableSubPixelResolution();
		if (xpf==null && xp!=null) {
			xpf = toFloat(xp);
			ypf = toFloat(yp);
		}
	}

	public String getDebugInfo() {
		String s = "ROI Debug Properties\n";
		s += "	bounds: "+bounds+"\n";
		s += "	x,y,w,h: "+x+","+y+","+width+","+height+"\n";
		if (xpf!=null && xpf.length>0)
			s += "	xpf[0],ypf[0]: "+xpf[0]+","+ypf[0]+"\n";
		return s;
	}

}
