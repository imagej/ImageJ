package ij.gui;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import ij.*;
import ij.plugin.frame.Recorder;
import ij.process.FloatPolygon;
import ij.measure.Calibration;

/** This class implements the rotated rectangle selection tool. */
public class RotatedRectRoi extends PolygonRoi {
	private double xstart, ystart;
	private static double DefaultRectWidth = 50;
	private double rectWidth = DefaultRectWidth;

	public RotatedRectRoi(double x1, double y1, double x2, double y2, double rectWidth) {
		super(new float[5], new float[5], 5, FREEROI);
		this.rectWidth = rectWidth;
		makeRectangle(x1, y1, x2, y2);
		state = NORMAL;
		bounds = null;
	}

	public RotatedRectRoi(int sx, int sy, ImagePlus imp) {
		super(sx, sy, imp);
		type = FREEROI;
		xstart = offScreenXD(sx);
		ystart = offScreenYD(sy);
		ImageWindow win = imp.getWindow();
		int pixels = win!=null?(int)(win.getSize().height/win.getCanvas().getMagnification()):imp.getHeight();
		if (IJ.debugMode) IJ.log("RotatedRectRoi: "+(int)rectWidth+" "+pixels);
		if (rectWidth>pixels)
			rectWidth = pixels/3;
		setDrawOffset(false);
		bounds = null;
	}

	public void draw(Graphics g) {	
		super.draw(g);
		if (!overlay && ic!=null) {
			double mag = ic.getMagnification();
			for (int i=0; i<4; i++) {
			if (i==3) //mark starting point
				handleColor = strokeColor!=null?strokeColor:ROIColor;
			else
				handleColor=Color.white;
			drawHandle(g, hxs(i), hys(i));
			}
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
		int indexPlus1 = index<3?index+1:0;
		return xpf[index]+(xpf[indexPlus1]-xpf[index])/2+x;
	}

	private double hy(int index) {
		int indexPlus1 = index<3?index+1:0;
		return ypf[index]+(ypf[indexPlus1]-ypf[index])/2+y;
	}

	protected void grow(int sx, int sy) {
		double x1 = xstart;
		double y1 = ystart;
		double x2 = offScreenXD(sx);
		double y2 = offScreenYD(sy);
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
		DefaultRectWidth = rectWidth;
		showStatus();
	}
	
	public void showStatus() {
		double[] p = getParams();
		double dx = p[2] - p[0];
		double dy = p[3] - p[1];
		double length = Math.sqrt(dx*dx+dy*dy);
		double width = p[4];
		if (imp!=null && !IJ.altKeyDown()) {
			Calibration cal = imp.getCalibration();
			if (cal.scaled() && cal.pixelWidth==cal.pixelHeight) {
				dx *= cal.pixelWidth;
				dy *= cal.pixelHeight;
				length = Math.sqrt(dx*dx+dy*dy);
				width = p[4]*cal.pixelWidth;
			}
		}
		double angle = getFloatAngle(p[0], p[1], p[2], p[3]);
		IJ.showStatus("length=" + IJ.d2s(length)+", width=" + IJ.d2s(width)+", angle=" + IJ.d2s(angle));
	}

	public void nudgeCorner(int key) {
		if (ic==null) return;
		double[] p = getParams();
		double x1 = p[0];
		double y1 = p[1];
		double x2 = p[2];
		double y2 = p[3];
		double inc = 1.0/ic.getMagnification();
		switch(key) {
			case KeyEvent.VK_UP: y2-=inc; break;
			case KeyEvent.VK_DOWN: y2+=inc; break;
			case KeyEvent.VK_LEFT: x2-=inc; break;
			case KeyEvent.VK_RIGHT: x2+=inc; break;
		}
		makeRectangle(x1, y1, x2, y2);
		imp.draw();
		notifyListeners(RoiListener.MOVED);
		showStatus();
	}

	void makePolygonRelative() {
		FloatPolygon poly = new FloatPolygon(xpf, ypf, nPoints);
		Rectangle r = poly.getBounds();
		x = r.x;
		y = r.y;
		width = r.width;
		height = r.height;
		bounds = null;
		for (int i=0; i<nPoints; i++) {
			xpf[i] = xpf[i]-x;
			ypf[i] = ypf[i]-y;
		}
	}
	
	protected void handleMouseUp(int screenX, int screenY) {
		nPoints = 4;
		state = NORMAL;
		if (Recorder.record) {
			double[] p = getParams();
			if (Recorder.scriptMode())
				Recorder.recordCall("imp.setRoi(new RotatedRectRoi("+(int)p[0]+","+(int)p[1]+","+(int)p[2]+","+(int)p[3]+","+(int)p[4]+"));");
			else
				Recorder.record("makeRotatedRectangle", (int)p[0], (int)p[1], (int)p[2], (int)p[3], (int)p[4]);
		}
	}
	
	protected void moveHandle(int sx, int sy) {
		double ox = offScreenXD(sx); 
		double oy = offScreenYD(sy);
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
		int size = getHandleSize()+5;
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
