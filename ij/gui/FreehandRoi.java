package ij.gui;

import java.awt.*;
import java.awt.image.*;
import ij.*;

/** Freehand region of interest or freehand line of interest*/
public class FreehandRoi extends PolygonRoi {


	public FreehandRoi(int x, int y, ImagePlus imp) {
		super(x, y, imp);
		if (Toolbar.getToolId()==Toolbar.FREEROI)
			type = FREEROI;
		else
			type = FREELINE;
	}

	protected void grow(int ox, int oy) {
		if (ox<0) ox = 0;
		if (oy<0) oy = 0;
		if (ox>xMax) ox = xMax;
		if (oy>yMax) oy = yMax;
		if  (ox!=xp[nPoints-1] || oy!=yp[nPoints-1]) {
			xp[nPoints] = ox;
			yp[nPoints] = oy;
			nPoints++;
			if (nPoints==xp.length)
				enlargeArrays();
			g.setColor(ROIColor);
			g.drawLine(ic.screenX(xp[nPoints-2]), ic.screenY(yp[nPoints-2]), ic.screenX(ox), ic.screenY(oy));
		}
	}


	protected void handleMouseUp(int screenX, int screenY) {
		if (state==CONSTRUCTING)
			finishPolygon();
		state = NORMAL;
	}

}