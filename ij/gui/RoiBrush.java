package ij.gui;
import ij.*;
import java.awt.*;

/** Implements the ROI Brush tool.*/
class RoiBrush implements Runnable {
	private Polygon poly;
	private Point previousP;
 
	RoiBrush() {
		Thread thread = new Thread(this, "RoiBrush");
		thread.start();
	}

	public void run() {
		int size = Toolbar.getBrushSize();
		ImagePlus img = WindowManager.getCurrentImage();
		if (img==null) return;
		ImageCanvas ic = img.getCanvas();
		if (ic==null) return;
		Roi roi = img.getRoi();
		if (roi!=null && !roi.isArea())
			img.killRoi();
		Point p;
		int flags, leftClick=16, alt=9;
		while (true) {
			p = ic.getCursorLoc();
			if (p.equals(previousP))
				{IJ.wait(1); continue;}
			previousP = p;
			flags = ic.getModifiers();
			if ((flags&leftClick)==0) return;
			if ((flags&alt)==0)
				addCircle(img, p.x, p.y, size);
			else
				subtractCircle(img, p.x, p.y, size);
		}
	}

	void addCircle(ImagePlus img, int x, int y, int width) {
		Roi roi = img.getRoi();
		if (roi!=null) {
			if (!(roi instanceof ShapeRoi))
			roi = new ShapeRoi(roi);
			((ShapeRoi)roi).or(getCircularRoi(x, y, width));
		} else
			roi = new OvalRoi(x-width/2, y-width/2, width, width);
			//roi = getCircularRoi(x, y, width);
		img.setRoi(roi);
	}

	void subtractCircle(ImagePlus img, int x, int y, int width) {
		Roi roi = img.getRoi();
		if (roi!=null) {
			if (!(roi instanceof ShapeRoi))
			roi = new ShapeRoi(roi);
			((ShapeRoi)roi).not(getCircularRoi(x, y, width));
			img.setRoi(roi);
		}
	}

    
	ShapeRoi getCircularRoi(int x, int y, int width) {
		if (poly==null) {
			Roi roi = new OvalRoi(x-width/2, y-width/2, width, width);
			poly = roi.getPolygon();
			for (int i=0; i<poly.npoints; i++) {
				poly.xpoints[i] -= x;
				poly.ypoints[i] -= y;
			}
		}
		return new ShapeRoi(x, y, poly);
	}

}

