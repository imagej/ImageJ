package ij.gui;
import java.awt.*;
import java.awt.image.*;
import ij.*;
import ij.plugin.frame.Recorder;
import ij.process.FloatPolygon;
import ij.measure.Calibration;

/** Elliptical region of interest. */
public class TiltedRectangleRoi extends PolygonRoi {
	private double xstart, ystart;
	private static double rectWidth = 50;

	public TiltedRectangleRoi(double x1, double y1, double x2, double y2, double rectWidth) {
		super(new float[5], new float[5], 5, FREEROI);
		this.rectWidth = rectWidth;
		makeRectangle(x1, y1, x2, y2);
		state = NORMAL;
	}

	public TiltedRectangleRoi(int sx, int sy, ImagePlus imp) {
		super(sx, sy, imp);
		type = FREEROI;
		xstart = ic.offScreenXD(sx);
		ystart = ic.offScreenYD(sy);
		ImageWindow win = imp.getWindow();
		int pixels = win!=null?(int)(win.getSize().height/win.getCanvas().getMagnification()):imp.getHeight();
		if (rectWidth>pixels)
			rectWidth = pixels/3;
		
	}

	public void draw(Graphics g) {
		super.draw(g);
		if (!overlay && ic!=null) {
			double mag = ic.getMagnification();
		    int size2 = HANDLE_SIZE/2;
			for (int i=0; i<4; i++)
				drawHandle(g, hxs(i)-size2, hys(i)-size2);
		}
	}
	
	private int hxs(int index) {
		int indexPlus1 = index<3?index+1:0;
		return xp2[index]+(xp2[indexPlus1]-xp2[index])/2;
	}

	private int hys(int index) {
		int indexPlus1 = index<3?index+1:0;
		return yp2[index]+(yp2[indexPlus1]-yp2[index])/2;
	}

	private double hx(int index) {
		double xbase=x;
		if (bounds!=null)
			xbase = bounds.x;
		int indexPlus1 = index<3?index+1:0;
		return xpf[index]+(xpf[indexPlus1]-xpf[index])/2+xbase;
	}

	private double hy(int index) {
		double ybase=y;
		if (bounds!=null)
			ybase = bounds.y;
		int indexPlus1 = index<3?index+1:0;
		return ypf[index]+(ypf[indexPlus1]-ypf[index])/2+ybase;
	}

	protected void grow(int sx, int sy) {
		double x1 = xstart;
		double y1 = ystart;
		double x2 = ic.offScreenXD(sx);
		double y2 = ic.offScreenYD(sy);
		makeRectangle(x1, y1, x2, y2);
		imp.draw();
	}
		
	void makeRectangle(double x1, double y1, double x2, double y2) {
		double length = Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1));
		double angle = Math.atan ((x2-x1)/(y2-y1));
		double wsa = (rectWidth/2.0)*Math.sin((Math.PI/2.0)+angle);
		double wca = (rectWidth/2.0)*Math.cos((Math.PI/2)+angle);
		nPoints = 5;
		xpf[3] = (float)(x1-wsa);
		ypf[3] = (float)(y1-wca);
		xpf[0] = (float)(x1+wsa);
		ypf[0] = (float)(y1+wca);
		xpf[1] = (float)(x2+wsa);
		ypf[1] = (float)(y2+wca);
		xpf[2] = (float)(x2-wsa);
		ypf[2] = (float)(y2-wca);
		xpf[4] = xpf[0];
		ypf[4] = ypf[0];
		makePolygonRelative();
		cachedMask = null;
	}

	void makePolygonRelative() {
		FloatPolygon poly = new FloatPolygon(xpf, ypf, nPoints);
		Rectangle r = poly.getBounds();
		x = r.x;
		y = r.y;
		width = r.width;
		height = r.height;
		bounds = poly.getFloatBounds();
		float xbase = (float)bounds.getX();
		float ybase = (float)bounds.getY();
		for (int i=0; i<nPoints; i++) {
			xpf[i] = xpf[i]-xbase;
			ypf[i] = ypf[i]-ybase;
		}
	}
	
	protected void handleMouseUp(int screenX, int screenY) {
		if (state==CONSTRUCTING) {
            addOffset();
			finishPolygon();
        }
		nPoints = 4;
		state = NORMAL;
	}
	
	protected void moveHandle(int sx, int sy) {
		double ox = ic.offScreenXD(sx); 
		double oy = ic.offScreenYD(sy);
		double xbase=x, ybase=y;
		if (bounds!=null) {
			xbase = bounds.x;
			ybase = bounds.y;
		}
		double x1 = hx(3);
		double y1 = hy(3);
		double x2 = hx(1);
		double y2 = hy(1);
		switch(activeHandle) {
			case 0: 
				double dx = hx(2) - ox;
				double dy = hy(2) - oy;
				rectWidth = Math.sqrt(dx*dx+dy*dy);
				break;
			case 1: 
				x2 = ox;
				y2 = oy;
				break;
			case 2: 
				dx = hx(0) - ox;
				dy = hy(0) - oy;
				rectWidth = Math.sqrt(dx*dx+dy*dy);
				break;
			case 3: 
				x1 = ox;
				y1 = oy;
				break;
		}
		makeRectangle(x1, y1, x2, y2);
		imp.draw();
	}
	
	public int isHandle(int sx, int sy) {
		int size = HANDLE_SIZE+5;
		int halfSize = size/2;
		int index = -1;
		for (int i=0; i<4; i++) {
			int sx2 = (int)Math.round(hxs(i)-halfSize), sy2=(int)Math.round(hys(i)-halfSize);
			if (sx>=sx2 && sx<=sx2+size && sy>=sy2 && sy<=sy2+size) {
				index = i;
				break;
			}
		}
		return index;
	}
	
	/** Returns x1, y1, x2, y2 and width as a 5 element array. */
	public double[] getParams() {
		double xbase=x, ybase=y;
		if (bounds!=null) {
			xbase = bounds.x;
			ybase = bounds.y;
		}
		double[] params = new double[5];
		params[0] = hx(3);
		params[1]  = hy(3);
		params[2]  = hx(1);
		params[3]  = hy(1);
		params[4]  = rectWidth;
		return params;
	}
	
	/** Always returns true. */
	public boolean subPixelResolution() {
		return true;
	}

}
