package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.filter.Analyzer;
import ij.measure.Measurements;
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
		String type = PointRoi.types[PointRoi.getDefaultType()];
		String size = PointRoi.sizes[PointRoi.getDefaultSize()];
		GenericDialog gd = new GenericDialog("Point Tool");
		gd.setInsets(5,0,2);
		gd.addChoice("Type:", PointRoi.types, type);
		gd.addChoice("Size:", PointRoi.sizes, size);
		gd.addChoice("Color:", Colors.getColors(), sname);
		if (!multipointTool) {
			gd.addCheckbox("Auto-measure", Prefs.pointAutoMeasure);
			gd.addCheckbox("Auto-next slice", Prefs.pointAutoNextSlice);
			gd.addCheckbox("Add_to overlay", Prefs.pointAddToOverlay);
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
		int index = gd.getNextChoiceIndex();
		if (index!=PointRoi.getDefaultType()) {
			PointRoi.setDefaultType(index);
			redraw = true;
		}
		index = gd.getNextChoiceIndex();
		if (index!=PointRoi.getDefaultSize()) {
			PointRoi.setDefaultSize(index);
			redraw = true;
		}
		String selectionColor = gd.getNextChoice();
		Color sc = Colors.getColor(selectionColor, Color.yellow);
		if (sc!=Roi.getColor()) {
			Roi.setColor(sc);
			redraw = true;
			Toolbar.getInstance().repaint();
		}
		if (!multipointTool) {
			Prefs.pointAutoMeasure = gd.getNextBoolean();
			Prefs.pointAutoNextSlice = gd.getNextBoolean();
			Prefs.pointAddToOverlay = gd.getNextBoolean();
			Prefs.pointAddToManager = gd.getNextBoolean();
			if (Prefs.pointAddToOverlay)
				Prefs.pointAddToManager = false;
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
