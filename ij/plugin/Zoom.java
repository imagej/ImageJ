package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import java.awt.*;

/** This plugin implements the commands in the Image/Zoom submenu. */
public class Zoom implements PlugIn{

	/** 'arg' must be "in", "out", "100%" or "orig". */
	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
		ImageWindow win = imp.getWindow();
		if (win==null) return;
		ImageCanvas ic = win.getCanvas();
		Point loc = ic.getCursorLoc();
		int x = ic.screenX(loc.x);
		int y = ic.screenY(loc.y);
    	if (arg.equals("in")) {
			ic.zoomIn(x, y);
			if (ic.getMagnification()<=1.0) imp.repaintWindow();
    	} else if (arg.equals("out")) {
			ic.zoomOut(x, y);
			if (ic.getMagnification()<1.0) imp.repaintWindow();
    	} else if (arg.equals("orig"))
			ic.unzoom();
    	else if (arg.equals("100%")) {
			while(ic.getMagnification()<1.0)
				ic.zoomIn(0, 0);
			while(ic.getMagnification()>1.0)
				ic.zoomOut(0, 0);
		} else if (arg.equals("to"))
			zoomToSelection(imp, ic);
	}
	
	void zoomToSelection(ImagePlus imp, ImageCanvas ic) {
			Roi roi = imp.getRoi();
			if (roi==null) {
				IJ.error("Zoom", "Selection required");
				return;
			}
			Rectangle w = imp.getWindow().getBounds();
			Rectangle r = roi.getBounds();
			int x = r.x+r.width/2;
			int y = r.y+r.height/2;
			double mag = ic.getHigherZoomLevel(ic.getMagnification());
			while(r.width*mag<w.width && r.height*mag<w.height) {
				ic.zoomIn(ic.screenX(x), ic.screenY(y));
				mag = ic.getHigherZoomLevel(ic.getMagnification());
				w = imp.getWindow().getBounds();
				//IJ.log(mag+"  "+r.width+"  "+w.width+"  "+r.height+"  "+w.height);
				//IJ.wait(5000);
			}
			while(r.width*mag>w.width || r.height*mag>w.height) {
				ic.zoomOut(ic.screenX(x), ic.screenY(y));
				mag = ic.getHigherZoomLevel(ic.getMagnification());
				w = imp.getWindow().getBounds();
			}
	}
	
}

