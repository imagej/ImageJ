package ij.gui;

import java.awt.*;
import java.awt.image.*;
import ij.*;
import ij.process.*;
import ij.measure.*;
import java.awt.event.KeyEvent;

/** This class represents a straight line selection. */
public class Line extends Roi {

	public int x1, y1, x2, y2;  // the line
	private int x1R, y1R, x2R, y2R;  // the line, relative to base of bounding rect
	private static int lineWidth = 1;

	/** Creates a new user-generated straight line selection. 'ox'
		and 'oy' are offscreen coordinates that specify the start
		of the line. The user will determine the end of the line
		interactively using rubber banding. */
	public Line(int ox, int oy, ImagePlus imp) {
		super(ox, oy, imp);
		x1R = 0; y1R = 0;
		type = LINE;
	}

	/** Creates a new straight line selection using the specified
		starting and ending offscreen coordinates. */
	public Line(int ox1, int oy1, int ox2, int oy2, ImagePlus imp) {
		this(ox1, oy1, imp);
		grow(ox2, oy2);
		state = NORMAL;
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
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX=x; oldY=y;
		oldWidth=width; oldHeight=height;
	}

	/** Draws this line in the image. */
	public void draw(Graphics g) {
		g.setColor(ROIColor);
		x1=x+x1R; y1=y+y1R; x2=x+x2R; y2=y+y2R;
		g.drawLine(ic.screenX(x1), ic.screenY(y1), ic.screenX(x2), ic.screenY(y2));
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