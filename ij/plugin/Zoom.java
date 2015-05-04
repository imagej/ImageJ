package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import java.awt.*;

/** This plugin implements the commands in the Image/Zoom submenu. */
public class Zoom implements PlugIn{

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
		ImageCanvas ic = imp.getCanvas();
		if (ic==null) return;
		if (ic instanceof PlotCanvas && !((PlotCanvas)ic).isFrozen()) {
			((PlotCanvas)ic).zoom(arg);
			return;
		}
		Point loc = ic.getCursorLoc();
		if (!ic.cursorOverImage()) {
			Rectangle srcRect = ic.getSrcRect();
			loc.x = srcRect.x + srcRect.width/2;
			loc.y = srcRect.y + srcRect.height/2;
		}
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
    	else if (arg.equals("100%"))
    		ic.zoom100Percent();
		else if (arg.equals("to"))
			zoomToSelection(imp, ic);
		else if (arg.equals("set"))
			setZoom(imp, ic);
		else if (arg.equals("max")) {
			ImageWindow win = imp.getWindow();
			win.setBounds(win.getMaximumBounds());
			win.maximize();
		} if (arg.equals("scale"))
			scaleToFit(imp);
	}
	
	void zoomToSelection(ImagePlus imp, ImageCanvas ic) {
		Roi roi = imp.getRoi();
		ic.unzoom();
		if (roi==null) return;
		Rectangle w = imp.getWindow().getBounds();
		Rectangle r = roi.getBounds();
		double mag = ic.getMagnification();
		int marginw = (int)((w.width - mag * imp.getWidth()));
		int marginh = (int)((w.height - mag * imp.getHeight()));
		int x = r.x+r.width/2;
		int y = r.y+r.height/2;
		mag = ic.getHigherZoomLevel(mag);
		while(r.width*mag<w.width - marginw && r.height*mag<w.height - marginh) {
			ic.zoomIn(ic.screenX(x), ic.screenY(y));
			double cmag = ic.getMagnification();
			if (cmag==32.0) break;
			mag = ic.getHigherZoomLevel(cmag);
			w = imp.getWindow().getBounds();
		}
	}
	
	/** Based on Albert Cardona's ZoomExact plugin:
		http://albert.rierol.net/software.html */
	void setZoom(ImagePlus imp, ImageCanvas ic) {
		int width = imp.getWidth();
		int height = imp.getHeight();
		int x = width/2;
		int y = height/2;
		Rectangle srcRect = ic.getSrcRect();
		Roi roi = imp.getRoi();
		boolean areaSelection = false;
		if (roi!=null) {
			Rectangle bounds = roi.getBounds();
			x = bounds.x + bounds.width/2;
			y = bounds.y + bounds.height/2;
			areaSelection = roi.isArea();
			if (areaSelection)
				srcRect = bounds;
		}
		ImageWindow win = imp.getWindow();
		GenericDialog gd = new GenericDialog("Set Zoom");
		gd.addNumericField("Zoom:", ic.getMagnification() * 200, 0, 4, "%");
		gd.addNumericField("X center:", x, 0, 5, "");
		gd.addNumericField("Y center:", y, 0, 5, "");
		if (areaSelection) {
			gd.addNumericField("Width:", srcRect.width, 0, 5, "");
			gd.addNumericField("Height:", srcRect.height, 0, 5, "");
		}
		gd.showDialog();
		if (gd.wasCanceled()) return;
		double mag = gd.getNextNumber()/100.0;
		x = (int)gd.getNextNumber();
		y = (int)gd.getNextNumber();
		int srcWidth = srcRect.width;
		int srcHeight = srcRect.height;
		if (areaSelection) {
			srcWidth = (int)gd.getNextNumber();
			srcHeight = (int)gd.getNextNumber();
		}
		if (x<0) x=0;
		if (y<0) y=0;
		String options = IJ.macroRunning()?Macro.getOptions():null;
		boolean legacyMacro = areaSelection && options!=null && options.contains("x=") && !options.contains("width=");
		Rectangle bounds = GUI.getMaxWindowBounds();
		boolean smallImage = mag>1.0 && width*mag<bounds.width && height*mag<bounds.height;
		if ((areaSelection||smallImage||srcWidth!=srcRect.width||srcHeight!=srcRect.height) && !legacyMacro) {
			if (areaSelection && roi.getType()==Roi.RECTANGLE)
				imp.deleteRoi();
			Insets insets = win.getInsets();
			int canvasWidth = (int)(srcWidth*mag+insets.right+insets.left+ImageWindow.HGAP*2);
			int canvasHeight = (int)(srcHeight*mag+insets.top+insets.bottom+ImageWindow.VGAP*2+win.getSliderHeight());
			ic.setSourceRect(new Rectangle(x-srcWidth/2,y-srcHeight/2,srcWidth,srcHeight));
			ic.setMagnification(mag);
			win.setSize(canvasWidth, canvasHeight);
			return;
		}
		if (x>=width) x=width-1;
		if (y>=height) y=height-1;
		if (mag<=0.0) mag = 1.0;
		ic.setMagnification(mag);
		double newWidth = width*mag;
		double newHeight = height*mag;
		Dimension size = ic.getSize();
		if (newWidth>=size.width && newHeight>=size.height) {
			srcWidth = (int)Math.round(size.width/mag);
			srcHeight = (int)Math.round(size.height/mag);
			if ((int)(srcWidth*mag)<size.width)
				srcWidth++;
			if ((int)(srcHeight*mag)<size.height)
				srcHeight++;
			x = x-srcWidth/2;
			y = y-srcHeight/2;
			if (x+srcWidth>width)
				x = width - srcWidth;
			if (y+srcHeight>height)
				y = height - srcHeight;
			if (x<0) x=0;
			if (y<0) y=0;
			ic.setSourceRect(new Rectangle(x, y, srcWidth, srcHeight));
		} else {
			srcWidth = width;
			srcHeight = height;
			ic.setSize((int)newWidth, (int)newHeight);
			ic.setSourceRect(new Rectangle(0, 0, srcWidth, srcHeight));
			win.pack();
		}
		ic.repaint();
	}
	
	private void scaleToFit(ImagePlus imp) {
		ImageCanvas ic = imp.getCanvas();
		if (ic==null)
			return;
		if (ic.getScaleToFit()) {
			ic.setScaleToFit(false);
			ic.unzoom();
			IJ.showStatus("Exiting scale to fit mode (resize with 'alt' key to scale to fit)");
		} else {
			ic.setScaleToFit(true);
			ic.fitToWindow();
			IJ.showStatus("Resize window to scale (use 'alt' key as shortcut)");
		}

	}
	
}

