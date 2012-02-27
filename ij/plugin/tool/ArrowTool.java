package ij.plugin.tool;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;
import java.awt.*;
import java.awt.event.*;

public class ArrowTool extends PlugInTool {
	Roi arrow;

	public void mousePressed(ImagePlus imp, MouseEvent e) {
		ImageCanvas ic = imp.getCanvas();
		int sx = e.getX();
		int sy = e.getY();
		int ox = ic.offScreenX(sx);
		int oy = ic.offScreenY(sy);
		Roi roi = imp.getRoi();
		int handle = roi!=null?roi.isHandle(ox, oy):-1;
		if (!(roi!=null && (roi instanceof Arrow) && (handle>=0||roi.contains(ox,oy)))) {
			arrow = new Arrow(sx, sy, imp);
			imp.setRoi(arrow, false);
			e.consume();
		}
	}

	public void mouseDragged(ImagePlus imp, MouseEvent e) {
		ImageCanvas ic = imp.getCanvas();
		int sx = e.getX();
		int sy = e.getY();
		int ox = ic.offScreenX(sx);
		int oy = ic.offScreenY(sy);
		Roi roi = imp.getRoi();
		if (roi!=null && (roi instanceof Arrow) && roi.contains(ox,oy))
			roi.mouseDragged(e);
		else if (arrow!=null)
			arrow.mouseDragged(e);
		e.consume();
	}

	public void mouseReleased(ImagePlus imp, MouseEvent e) {
		ImageCanvas ic = imp.getCanvas();
		int sx = e.getX();
		int sy = e.getY();
		int ox = ic.offScreenX(sx);
		int oy = ic.offScreenY(sy);
		Roi roi = imp.getRoi();
		if (arrow!=null && !(roi!=null && (roi instanceof Arrow) && roi.contains(ox,oy))) {
			arrow.mouseReleased(e);
			e.consume();
		}
	}

	public void showOptionsDialog() {
		IJ.doCommand("Arrow Tool...");
	}

	public String getToolIcon() {
		return "C037L1ee1Cf64L65e1La9e1L65a9";
	}

	public String getToolName() {
		return "Arrow Tool";
	}
	
}


