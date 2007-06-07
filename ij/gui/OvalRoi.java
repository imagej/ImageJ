package ij.gui;

import java.awt.*;
import java.awt.image.*;
import ij.*;
import ij.process.*;

/** Oval region of interest */
public class OvalRoi extends Roi {

	public OvalRoi(int x, int y, int width, int height, ImagePlus imp) {
		super(x, y, width, height, imp);
		type = OVAL;
	}

	public OvalRoi(int x, int y, ImagePlus imp) {
		super(x, y, imp);
		type = OVAL;
	}

	public void draw(Graphics g) {
		g.setColor(ROIColor);
		double mag = ic.getMagnification();
		g.drawOval(ic.screenX(x), ic.screenY(y), (int)(width*mag), (int)(height*mag));
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
		return Math.PI*(width+height)/2;
	}
	
}