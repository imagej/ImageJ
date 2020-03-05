package ij.gui;

import java.awt.*;
import java.awt.image.*;
import ij.*;

/** Freehand region of interest or freehand line of interest*/
public class FreehandRoi extends PolygonRoi {

	public FreehandRoi(int sx, int sy, ImagePlus imp) {
		super(sx, sy, imp);
		if (Toolbar.getToolId()==Toolbar.FREEROI)
			type = FREEROI;
		else
			type = FREELINE;
		if (nPoints==2) nPoints--;
	}

	protected void grow(int sx, int sy) {
		if (subPixelResolution() && xpf!=null) {
			growFloat(sx, sy);
			return;
		}
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		if (ox<0) ox = 0;
		if (oy<0) oy = 0;
		if (ox>xMax) ox = xMax;
		if (oy>yMax) oy = yMax;
		if (ox!=xp[nPoints-1]+x || oy!=yp[nPoints-1]+y) {
			xp[nPoints] = ox-x;
			yp[nPoints] = oy-y;
			nPoints++;
			if (IJ.altKeyDown())
				wipeBack();
			if (nPoints==xp.length)
				enlargeArrays();
			drawLine();
		}
	}
              
	private void growFloat(int sx, int sy) {
		double ox = offScreenXD(sx);
		double oy = offScreenYD(sy);
		if (ox<0.0) ox = 0.0;
		if (oy<0.0) oy = 0.0;
		if (ox>xMax) ox = xMax;
		if (oy>yMax) oy = yMax;
		double xbase = getXBase();
		double ybase = getYBase();
		if (ox!=xpf[nPoints-1]+xbase || oy!=ypf[nPoints-1]+ybase) {
			xpf[nPoints] = (float)(ox-xbase);
			ypf[nPoints] = (float)(oy-ybase);
			nPoints++;
			if (nPoints==xpf.length)
				enlargeArrays();
			drawLine();
		}
	}
	
	void drawLine() {
		int x1, y1, x2, y2;
		if (xpf!=null) {
			x1 = (int)Math.round(xpf[nPoints-2]+x);
			y1 = (int)Math.round(ypf[nPoints-2]+y);
			x2 = (int)Math.round(xpf[nPoints-1]+x);
			y2 = (int)Math.round(ypf[nPoints-1]+y);
		} else {
			x1 = xp[nPoints-2]+x;
			y1 = yp[nPoints-2]+y;
			x2 = xp[nPoints-1]+x;
			y2 = yp[nPoints-1]+y;
		}
		int xmin = Math.min(x1, x2);
		int xmax = Math.max(x1, x2);
		int ymin = Math.min(y1, y2);
		int ymax = Math.max(y1, y2);
		int margin = 4;
		if (lineWidth>margin && isLine())
			margin = lineWidth;
		if (ic!=null) {
			double mag = ic.getMagnification();
			if (mag<1.0) margin = (int)(margin/mag);
		}
		if (IJ.altKeyDown())
			margin += 20; // for wipeBack
		imp.draw(xmin-margin, ymin-margin, (xmax-xmin)+margin*2, (ymax-ymin)+margin*2);
	}

	protected void handleMouseUp(int screenX, int screenY) {
		if (state==CONSTRUCTING) {
            addOffset();
			finishPolygon();
        }
		state = NORMAL;
	}

}
