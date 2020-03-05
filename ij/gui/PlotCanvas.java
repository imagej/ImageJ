package ij.gui;

import java.awt.*;
import ij.process.*;
import ij.*;
import ij.util.*;
import java.awt.event.*;


/** This subclass of ImageCanvas has special provisions for plots:
 * - Zooming: sets the plot range
 * - Scrolling: moves data area
 *  This behavior is suppressed if the plot is frozen
 * */
public class PlotCanvas extends ImageCanvas {
	/** The plot displayed */
	Plot plot;
	int xScrolled, yScrolled;	//distance scrolled so far
	int oldWidth, oldHeight;
	int rangeArrowIndexWhenPressed = -1;

	/** Creates a new PlotCanvas */
	public PlotCanvas(ImagePlus imp) {
		super(imp);
		oldWidth = imp.getWidth();
		oldHeight = imp.getHeight();
	}

	/** Tells the PlotCanvas which plot to use for zooming etc.
	 *	Call this immediately after construction */
	public void setPlot(Plot plot) {
		this.plot = plot;
	}

	/** Returns the Plot displayed in this canvas */
	public Plot getPlot() {
		return plot;
	}

	/** Whether the plot is frozen, i.e. its ImageProcessor can not be changed */
	public boolean isFrozen() {
		return plot != null && plot.isFrozen();
	}

	/** Zoom operations that are handled by ij.plugin.Zoom */
	public void zoom(String arg) {
		int cursorX = -1, cursorY = -1;
		if (cursorOverImage()) {
			Point cursorLoc = getCursorLoc();
			cursorX = screenX(cursorLoc.x);
			cursorY = screenY(cursorLoc.y);
		}
		Rectangle roiRect = null;
		Roi roi = imp.getRoi();
		if (roi != null && roi.isArea())
			roiRect = roi.getBounds();
		if (arg.equals("in")) {
			if (roiRect != null) {
				plot.zoomToRect(roiRect);
				imp.deleteRoi();
			} else
				zoomIn(cursorX, cursorY);
		} else if (arg.equals("out")) {
				zoomOut(cursorX, cursorY);
		} else if (arg.equals("orig")) {
			unzoom();
		} else if (arg.equals("100%")) {
			zoom100Percent();
		} else if (arg.equals("to") && roiRect != null) {
			plot.zoomToRect(roiRect);
			imp.deleteRoi();
		} else if (arg.equals("set"))
			new PlotDialog(plot, PlotDialog.SET_RANGE).showDialog(null);
		else if (arg.equals("max")) {
			ImageWindow win = imp.getWindow();
			win.setBounds(win.getMaximumBounds());
			win.maximize();
		} else if (arg.equals("scale"))
			plot.setLimitsToFit(true);
	}

	/** Zooms in by modifying the plot range; sx and sy are screen coordinates */
	public void zoomIn(int sx, int sy) {
		zoom(sx, sy, Math.sqrt(2));
	}

    /** Zooms out by modifying the plot range; sx and sy are screen coordinates */
	public void zoomOut(int sx, int sy) {
		zoom(sx, sy, Math.sqrt(0.5));
	}

	void zoom(int sx, int sy, double zoomFactor) {//n__ 
		if (plot == null || plot.isFrozen()) {
			if (zoomFactor > 1)
				super.zoomIn(sx, sy);
			else
				super.zoomOut(sx, sy);
			return;
		}
		plot.zoom(sx, sy, zoomFactor);
	}

	/** Implements the Image/Zoom/Original Scale command.
	 *	Sets the original range of the x, y axes (unless the plot is frozen) */
	public void unzoom() {
		if (plot == null || plot.isFrozen()) {
			super.unzoom();
			return;
		}
		resetMagnification();
		plot.setLimitsToDefaults(true);
	}

	/** Implements the Image/Zoom/View 100% command: Sets the original frame size as specified
	 *	in Edit/Options/Plots (unless the plot is frozen) */
	public void zoom100Percent() {
		if (plot == null || plot.isFrozen()) {
			super.zoom100Percent();
			return;
		}
		resetMagnification();
		plot.setFrameSize(PlotWindow.plotWidth, PlotWindow.plotHeight);
	}

	/** Resizes the plot (unless frozen) to fit the window */
	public void fitToWindow() {
		if (plot == null || plot.isFrozen()) {
			super.fitToWindow();
			return;
		}
		ImageWindow win = imp.getWindow();
		if (win==null) return;
		Rectangle bounds = win.getBounds();
		Dimension extraSize = win.getExtraSize();
		int width = bounds.width-extraSize.width;//(insets.left+insets.right+ImageWindow.HGAP*2);
		int height = bounds.height-extraSize.height;//(insets.top+insets.bottom+ImageWindow.VGAP*2);
		//IJ.log("fitToWindow "+bounds+"-> w*h="+width+"*"+height);
		resizeCanvas(width, height);
		getParent().doLayout();
	}

	/** Resizes the canvas when the user resizes the window. To avoid a race condition while creating
	 *	a new window, this is ignored if no window exists or the window has not been activated yet.
     */
	void resizeCanvas(int width, int height) {
		if (plot == null || plot.isFrozen()) {
			super.resizeCanvas(width, height);
			return;
		}
		resetMagnification();
		if (width == oldWidth && height == oldHeight) return;
		if (plot == null) return;
		ImageWindow win = imp.getWindow();
		if (win==null || !(win instanceof PlotWindow)) return;
		if (!win.isVisible()) return;
		if (!((PlotWindow)win).wasActivated) return;				// window layout not finished yet?
		Dimension minSize = plot.getMinimumSize();
		int plotWidth  =  width < minSize.width	 ? minSize.width  : width;
		int plotHeight = height < minSize.height ? minSize.height : height;
		plot.setSize(plotWidth, plotHeight);
		setSize(width, height);
		oldWidth = width;
		oldHeight = height;
		((PlotWindow)win).canvasResized();
	}

	/** The image of a PlotCanvas is always shown at 100% magnification unless the plot is frozen */
	public void setMagnification(double magnification) {
		if (plot == null || plot.isFrozen())
			super.setMagnification(magnification);
		else
			resetMagnification();
	}

	/** Scrolling a PlotCanvas is updating the plot, not viewing part of the plot, unless the plot is frozen */
	public void setSourceRect(Rectangle r) {
		if (plot.isFrozen())
			super.setSourceRect(r);
		else
			resetMagnification();
	}

	void resetMagnification() {
		magnification = 1.0;
		srcRect.x = 0;
		srcRect.y = 0;
	}

	/** overrides ImageCanvas.setupScroll; if plot is not frozen, scrolling modifies the plot data range */
	protected void setupScroll(int ox, int oy) {
		if (plot.isFrozen()) {
			super.setupScroll(ox, oy);
			return;
		}
		xMouseStart = ox;
		yMouseStart = oy;
		xScrolled = 0;
		yScrolled = 0;
	}

	/** overrides ImageCanvas.scroll; if plot is not frozen, scrolling modifies the plot data range */
	protected void scroll(int sx, int sy) {
		if (plot.isFrozen()) {
			super.scroll(sx, sy);
			return;
		}
		if (sx == 0 && sy == 0) return;
		if (xScrolled == 0 && yScrolled == 0)
			plot.saveMinMax();
		int dx = sx - xMouseStart;
		int dy = sy - yMouseStart;
		plot.scroll(dx-xScrolled, dy-yScrolled);
		xScrolled = dx;
		yScrolled = dy;
		Thread.yield();
	}

	/** overrides ImageCanvas.mouseExited; removes 'range' arrows */
	public void mouseExited(MouseEvent e) {
		ImageWindow win = imp.getWindow();
		if (win instanceof PlotWindow)
			((PlotWindow)win).mouseExited(e);
		super.mouseExited(e);
	}

	/** overrides ImageCanvas.mousePressed: no further processing of clicks on 'range' arrows */
	public void mousePressed(MouseEvent e) {
		rangeArrowIndexWhenPressed = getRangeArrowIndex(e);
		if (rangeArrowIndexWhenPressed <0)
			super.mousePressed(e);
	}

	/** Overrides ImageCanvas.mouseReleased, handles clicks on 'range' arrows */
	public void mouseReleased(MouseEvent e) {
		if (rangeArrowIndexWhenPressed>=0 && rangeArrowIndexWhenPressed==getRangeArrowIndex(e))
			plot.zoomOnRangeArrow(rangeArrowIndexWhenPressed);
		else
			super.mouseReleased(e);
	}

    /** Returns the index of the arrow for modifying the range when the mouse click was
     *  at such an arrow, otherwise -1 */
    int getRangeArrowIndex(MouseEvent e) {
        ImageWindow win = imp.getWindow();
        int rangeArrowIndex = -1;
        if (win instanceof PlotWindow) {
            int x = e.getX();
		    int y = e.getY();
            rangeArrowIndex = ((PlotWindow)win).getRangeArrowIndex(x, y);
        }
        return rangeArrowIndex;
    }
}
