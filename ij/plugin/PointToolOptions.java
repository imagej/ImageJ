package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.filter.Analyzer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;


/** This plugin implements the Edit/Options/Point Tool command. */
public class PointToolOptions implements PlugIn, DialogListener {
	boolean multipointTool;

 	public void run(String arg) {
 		showDialog();
 	}
		
	void showDialog() {
		multipointTool = IJ.getToolName().equals("multipoint");
		Color sc =Roi.getColor();
		String sname = Colors.getColorName(sc, "Yellow");
		Color cc =PointRoi.getDefaultCrossColor();
		String cname = Colors.getColorName(cc, "None");
		String markerSize = PointRoi.getDefaultMarkerSize();
		GenericDialog gd = new GenericDialog("Point Tool");
		gd.setInsets(5,0,2);
		gd.addChoice("Selection color:", Colors.getColors(), sname);
		gd.setInsets(0,0,2);
		gd.addChoice("Cross color:", Colors.getColors("None"), cname);
		gd.addChoice("Marker size:", PointRoi.sizes, markerSize);
		if (!multipointTool) {
			gd.addNumericField("Mark width:", Analyzer.markWidth, 0, 2, "pixels");
			gd.addCheckbox("Auto-measure", Prefs.pointAutoMeasure);
			gd.addCheckbox("Auto-next slice", Prefs.pointAutoNextSlice);
			gd.addCheckbox("Add to ROI Manager", Prefs.pointAddToManager);
		}
		gd.addCheckbox("Label points", !Prefs.noPointLabels);
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
		}
	}
	
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		boolean redraw = false;
		String selectionColor = gd.getNextChoice();
		Color sc = Colors.getColor(selectionColor, Color.yellow);
		if (sc!=Roi.getColor()) {
			Roi.setColor(sc);
			redraw = true;
			Toolbar.getInstance().repaint();
		}
		String crossColor = gd.getNextChoice();
		Color cc = Colors.getColor(crossColor, null);
		if (cc!=PointRoi.getDefaultCrossColor())
			redraw = true;
		PointRoi.setDefaultCrossColor(cc);
		String markerSize = gd.getNextChoice();
		if (!markerSize.equals(PointRoi.getDefaultMarkerSize())) {
			PointRoi.setDefaultMarkerSize(markerSize);
			redraw = true;
		}
		if (!multipointTool) {
			int width = (int)gd.getNextNumber();
			if (width<0) width = 0;
			Analyzer.markWidth = width;
			Prefs.pointAutoMeasure = gd.getNextBoolean();
			Prefs.pointAutoNextSlice = gd.getNextBoolean();
			Prefs.pointAddToManager = gd.getNextBoolean();
			if (Prefs.pointAutoNextSlice&&!Prefs.pointAddToManager)
				Prefs.pointAutoMeasure = true;
		}
		boolean noPointLabels = !gd.getNextBoolean();
		if (noPointLabels!=Prefs.noPointLabels)
			redraw = true;
		Prefs.noPointLabels = noPointLabels;
		if (redraw) {
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null)
				imp.draw();
		}
		return true;
    }
    			
}
