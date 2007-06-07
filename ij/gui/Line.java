package ij.gui;

import java.awt.*;
import java.awt.image.*;
import ij.*;
import ij.process.*;
import ij.measure.*;
import java.awt.event.KeyEvent;

/** This class represents a straight line selection. */
public class Line extends Roi {

	public int x1, y1, x2, y2;	// the line
	private int x1R, y1R, x2R, y2R;  // the line, relative to base of bounding rect
	private static int lineWidth = 1;

	/** Creates a new straight line selection using the specified
		starting and ending offscreen coordinates. */
	public Line(int ox1, int oy1, int ox2, int oy2) {
		this(ox1, oy1, null);
		grow(ox2, oy2);
		x1=x+x1R; y1=y+y1R; x2=x+x2R; y2=y+y2R;
		state = NORMAL;
	}

	/** Starts the process of creating a new user-generated straight line
		selection. 'ox' and 'oy' are offscreen coordinates that specify
		the start of the line. The user will determine the end of the line
		interactively using rubber banding. */
	public Line(int ox, int oy, ImagePlus imp) {
		super(ox, oy, imp);
		x1R = 0; y1R = 0;
		type = LINE;
	}

	/** Obsolete */
	public Line(int ox1, int oy1, int ox2, int oy2, ImagePlus imp) {
		this(ox1, oy1, ox2, oy2);
		setImage(imp);
	}

	protected void grow(int xend, int yend) {
		if (xend<0) xend=0; if (yend<0) yend=0;
		if (xend>xMax) xend=xMax; if (yend>yMax) yend=yMax;
		int xstart=x+x1R, ystart=y+y1R;
		if (constrain) {
			int dx = Math.abs(xend-xstart);
			int dy = Math.abs(yend-ystart);
			if (dx>=dy)
				yend = ystart;
			else
				xend = xstart;
		}
		x=Math.min(x+x1R,xend); y=Math.min(y+y1R,yend);
		x1R=xstart-x; y1R=ystart-y;
		x2R=xend-x; y2R=yend-y;
		width=Math.abs(x2R-x1R); height=Math.abs(y2R-y1R);
		if (width<1) width=1; if (height<1) height=1;
		updateClipRect();
		if (imp!=null)
			imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX=x; oldY=y;
		oldWidth=width; oldHeight=height;
	}

	protected void moveHandle(int ox, int oy) {
		x1=x+x1R; y1=y+y1R; x2=x+x2R; y2=y+y2R;
		switch (activeHandle) {
			case 0: x1=ox; y1=oy; break;
			case 1: x2=ox; y2=oy; break;
			case 2:
				int dx = ox-(x1+(x2-x1)/2);
				int dy = oy-(y1+(y2-y1)/2);
				x1+=dx; y1+=dy; x2+=dx; y2+=dy;
				break;
		}
		if (constrain) {
			int dx = Math.abs(x1-x2);
			int dy = Math.abs(y1-y2);
			if (activeHandle==0) {
				if (dx>=dy) y1= y2; else x1 = x2;
			} else if (activeHandle==1) {
				if (dx>=dy) y2= y1; else x2 = x1;
			}
		}
		x=Math.min(x1,x2); y=Math.min(y1,y2);
		x1R=x1-x; y1R=y1-y;
		x2R=x2-x; y2R=y2-y;
		width=Math.abs(x2R-x1R); height=Math.abs(y2R-y1R);
		updateClipRect();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x;
		oldY = y;
		oldWidth = width;
		oldHeight = height;
	}

	protected void mouseDownInHandle(int handle, int sx, int sy) {
		state = MOVING_HANDLE;
		activeHandle = handle;
		ic.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
	}

	/** Draws this line in the image. */
	public void draw(Graphics g) {
		g.setColor(ROIColor);
		x1=x+x1R; y1=y+y1R; x2=x+x2R; y2=y+y2R;
		int sx1 = ic.screenX(x1);
		int sy1 = ic.screenY(y1);
		int sx2 = ic.screenX(x2);
		int sy2 = ic.screenY(y2);
		int sx3 = sx1 + (sx2-sx1)/2;
		int sy3 = sy1 + (sy2-sy1)/2;
		g.drawLine(sx1, sy1, sx2, sy2);
		if (state!=CONSTRUCTING) {
			int size2 = HANDLE_SIZE/2;
			if (ic!=null) mag = ic.getMagnification();
			drawHandle(g, sx1-size2, sy1-size2);
			drawHandle(g, sx2-size2, sy2-size2);
			drawHandle(g, sx3-size2, sy3-size2);
	   }
		IJ.showStatus(imp.getLocationAsString(x2,y2)+", angle=" + IJ.d2s(getAngle(x1,y1,x2,y2)) + ", length=" + IJ.d2s(getLength()));
		if (updateFullWindow)
			{updateFullWindow = false; imp.draw();}
	}

	/** Returns the length of this line. */
	public double getLength() {
		Calibration cal = imp.getCalibration();
		return Math.sqrt((x2-x1)*cal.pixelWidth*(x2-x1)*cal.pixelWidth
			+ (y2-y1)*cal.pixelHeight*(y2-y1)*cal.pixelHeight);
	}

	/** Returns the length of this line in pixels. */
	public double getRawLength() {
		return Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1));
	}

	/** Returns the pixel values along this line. */
	public double[] getPixels() {
		ImageProcessor ip = imp.getProcessor();
		double[] line = ip.getLine(x1, y1, x2, y2);
		return line;
	}

	public void drawPixels() {
		ImageProcessor ip = imp.getProcessor();
		ip.moveTo(x1, y1);
		ip.lineTo(x2, y2);
		if (Line.getWidth()>1)
			updateFullWindow = true;
	}

	public boolean contains(int x, int y) {
		return false;
	}
		
	/** Returns a handle number if the specified screen coordinates are  
		inside or near a handle, otherwise returns -1. */
	int isHandle(int sx, int sy) {
		int size = HANDLE_SIZE+5;
		int halfSize = size/2;
		int sx1 = ic.screenX(x+x1R) - halfSize;
		int sy1 = ic.screenY(y+y1R) - halfSize;
		int sx2 = ic.screenX(x+x2R) - halfSize;
		int sy2 = ic.screenY(y+y2R) - halfSize;
		int sx3 = sx1 + (sx2-sx1)/2-1;
		int sy3 = sy1 + (sy2-sy1)/2-1;
		if (sx>=sx1&&sx<=sx1+size&&sy>=sy1&&sy<=sy1+size) return 0;
		if (sx>=sx2&&sx<=sx2+size&&sy>=sy2&&sy<=sy2+size) return 1;
		if (sx>=sx3&&sx<=sx3+size+2&&sy>=sy3&&sy<=sy3+size+2) return 2;
		return -1;
	}

	public static int getWidth() {
		return lineWidth;
	}

	public static void setWidth(int w) {
		if (w<1) w = 1;
		if (w>99) w = 99;
		lineWidth = w;
	}
	
	/** Nudge end point of line by one pixel. */
	public void nudgeCorner(int key) {
		switch(key) {
			case KeyEvent.VK_UP: y2R--; break;
			case KeyEvent.VK_DOWN: y2R++; break;
			case KeyEvent.VK_LEFT: x2R--; break;
			case KeyEvent.VK_RIGHT: x2R++; break;
		}
		grow(x+x2R,y+y2R);
	}


}
