package ij.gui;

import java.awt.*;
import java.awt.image.*;
import ij.*;
import ij.process.*;
import ij.measure.Calibration;

/** Oval region of interest */
public class OvalRoi extends Roi {

	/** Creates a new OvalRoi.*/
	public OvalRoi(int x, int y, int width, int height) {
		super(x, y, width, height);
		type = OVAL;
	}

	/** Starts the process of creating a user-defined OvalRoi. */
	public OvalRoi(int x, int y, ImagePlus imp) {
		super(x, y, imp);
		type = OVAL;
	}

	/** Obsolete */
	public OvalRoi(int x, int y, int width, int height, ImagePlus imp) {
		this(x, y, width, height);
		setImage(imp);
	}

	protected void moveHandle(int ox, int oy) {
		if (clipboard!=null)
			return;
		//IJ.log("moveHandle: "+activeHandle+" "+ox+" "+oy);
		int x1=x, y1=y, x2=x1+width, y2=y+height;
		int w2 = (int)(0.14645*width);
		int h2 = (int)(0.14645*height);
		switch (activeHandle) {
			case 0: x=ox-w2; y=oy-h2; break;
			case 1: y=oy; break;
			case 2: x2=ox+w2; y=oy-h2; break;
			case 3: x2=ox; break;			
			case 4: x2=ox+w2; y2=oy+h2; break;
			case 5: y2=oy; break;
			case 6: x=ox-w2; y2=oy+h2; break;
			case 7: x=ox; break;
		}
		if (x<0) x=0; if (y<0) y=0;
		if (x<x2)
		   width=x2-x;
		else
		  {width=1; x=x2;}
		if (y<y2)
		   height = y2-y;
		else
		   {height=1; y=y2;}
		if (constrain)
			height = width;
		if ((x+width)>xMax) width=xMax-x;
		if ((y+height)>yMax) height=yMax-y;
		updateClipRect();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX=x; oldY=y;
		oldWidth=width; oldHeight=height;
	}

	public void draw(Graphics g) {
		if (ic==null) return;
		g.setColor(ROIColor);
		mag = ic!=null?ic.getMagnification():1.0;
		int sw = (int)(width*mag);
		int sh = (int)(height*mag);
		int sw2 = (int)(0.14645*width*mag);
		int sh2 = (int)(0.14645*height*mag);
		int sx1 = ic.screenX(x);
		int sy1 = ic.screenY(y);
		int sx2 = sx1+sw/2;
		int sy2 = sy1+sh/2;
		int sx3 = sx1+sw;
		int sy3 = sy1+sh;
		g.drawOval(sx1, sy1, sw, sh);
		if (state!=CONSTRUCTING && clipboard==null) {
			int size2 = HANDLE_SIZE/2;
			drawHandle(g, sx1+sw2-size2, sy1+sh2-size2);
			drawHandle(g, sx3-sw2-size2, sy1+sh2-size2);
			drawHandle(g, sx3-sw2-size2, sy3-sh2-size2);
			drawHandle(g, sx1+sw2-size2, sy3-sh2-size2);
			drawHandle(g, sx2-size2, sy1-size2);
			drawHandle(g, sx3-size2, sy2-size2);
			drawHandle(g, sx2-size2, sy3-size2);
			drawHandle(g, sx1-size2, sy2-size2);
		}
		if (updateFullWindow)
			{updateFullWindow = false; imp.draw();}
		showStatus();
	}

	public void drawPixels() {
		// equation for an ellipse is x^2/a^2 + y^2/b^2 = 1
		ImageProcessor ip = imp.getProcessor();
		int a = width/2;
		int b = height/2;
		double a2 = a*a;
		double b2 = b*b;
		int xbase = x+a;
		int ybase = y+b;
		double yy;
		ip.moveTo(x, y+b);
		for (int i=-a+1; i<=a; i++) {
			yy = Math.sqrt(b2*(1.0-(i*i)/a2));
			ip.lineTo(xbase+i, ybase+(int)(yy+0.5));		
		}
		ip.moveTo(x, y+b);
		for (int i=-a+1; i<=a; i++) {
			yy = Math.sqrt(b2*(1.0-(i*i)/a2));
			ip.lineTo(xbase+i, ybase-(int)(yy+0.5));		
		}
		if (Line.getWidth()>1)
			updateFullWindow = true;
	}		

	public boolean contains(int x, int y) {
	// equation for an ellipse is x^2/a^2 + y^2/b^2 = 1
		if (!super.contains(x, y))
			return false;
		else {
			x = Math.abs(x - (this.x + width/2));
			y = Math.abs(y - (this.y + height/2));
			double a = width/2;
			double b = height/2;
			return (x*x/(a*a) + y*y/(b*b)) <= 1;
		}
	}
		
	/** Returns a handle number if the specified screen coordinates are  
		inside or near a handle, otherwise returns -1. */
	public int isHandle(int sx, int sy) {
		if (clipboard!=null) return -1;
		double mag = ic.getMagnification();
		int size = HANDLE_SIZE+3;
		int halfSize = size/2;
		int sx1 = ic.screenX(x) - halfSize;
		int sy1 = ic.screenY(y) - halfSize;
		int sx3 = ic.screenX(x+width) - halfSize;
		int sy3 = ic.screenY(y+height) - halfSize;
		int sx2 = sx1 + (sx3 - sx1)/2;
		int sy2 = sy1 + (sy3 - sy1)/2;
		
		int sw2 = (int)(0.14645*(sx3-sx1));
		int sh2 = (int)(0.14645*(sy3-sy1));
		
		if (sx>=sx1+sw2&&sx<=sx1+sw2+size&&sy>=sy1+sh2&&sy<=sy1+sh2+size) return 0;
		if (sx>=sx2&&sx<=sx2+size&&sy>=sy1&&sy<=sy1+size) return 1;		
		if (sx>=sx3-sw2&&sx<=sx3-sw2+size&&sy>=sy1+sh2&&sy<=sy1+sh2+size) return 2;		
		if (sx>=sx3&&sx<=sx3+size&&sy>=sy2&&sy<=sy2+size) return 3;		
		if (sx>=sx3-sw2&&sx<=sx3-sw2+size&&sy>=sy3-sh2&&sy<=sy3-sh2+size) return 4;		
		if (sx>=sx2&&sx<=sx2+size&&sy>=sy3&&sy<=sy3+size) return 5;		
		if (sx>=sx1+sw2&&sx<=sx1+sw2+size&&sy>=sy3-sh2&&sy<=sy3-sh2+size) return 6;
		if (sx>=sx1&&sx<=sx1+size&&sy>=sy2&&sy<=sy2+size) return 7;
		return -1;
	}

	public int[] getMask() {
		Image img = GUI.createBlankImage(width, height);
		Graphics g = img.getGraphics();
		g.setColor(Color.black);
		g.fillOval(0, 0, width, height);
		g.dispose();
		ColorProcessor cp = new ColorProcessor(img);
		return (int[])cp.getPixels();
	}

	/** Returns the perimeter length. */
	public double getLength() {
		double pw=1.0, ph=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
		}
		return Math.PI*(width*pw+height*ph)/2.0;
	}
	
	/** Returns Feret's diameter, the greatest distance between 
		any two points along the ROI boundary. */
	public double getFeretsDiameter() {
		double pw=1.0, ph=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
		}
		return width*pw>=height*ph?width*pw:height*ph;
	}
	
}
