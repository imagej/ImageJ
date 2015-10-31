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
	private static GenericDialog gd = null;
	private boolean multipointTool;
	
	private static final String help = "<html>"
	+"<h1>Point Tool</h1>"
	+"<font size=+1>"
	+"<ul>"
	+"<li> Alt-click, or control-click, on a point to delete it.<br>"
	+"<li> Press 'y' (<i>Edit&gt;Selection&gt;Properties</i>) to display<br>the counts in a results table.<br>"
	+"<li> Use <i>File&gt;Save As&gt;Tiff</i> or <i>File&gt;Save As&gt;Selection</i><br>to save the points and counts.<br>"
	+"</ul>"
	+" <br>"
	+"</font>";


 	public void run(String arg) {
 		if (gd!=null && gd.isShowing()) {
 			gd.toFront();
 			update();
 		} else
 			showDialog();
 	}
		
	void showDialog() {
		String options = IJ.isMacro()?Macro.getOptions():null;
		if (options!=null) {
			options = options.replace("selection=", "color=");
			options = options.replace("marker=", "size=");
			Macro.setOptions(options);
		}
		multipointTool = IJ.getToolName().equals("multipoint");
		Color sc =Roi.getColor();
		String sname = Colors.getColorName(sc, "Yellow");
		Color cc =PointRoi.getDefaultCrossColor();
		String cname = Colors.getColorName(cc, "None");
		String type = PointRoi.types[PointRoi.getDefaultType()];
		String size = PointRoi.sizes[PointRoi.getDefaultSize()];
		if (multipointTool)
			gd = new NonBlockingGenericDialog("Point Tool");
		else
			gd = new GenericDialog("Point Tool");
		gd.setInsets(5,0,2);
		gd.addChoice("Type:", PointRoi.types, type);
		gd.addChoice("Color:", Colors.getColors(), sname);
		gd.addChoice("Size:", PointRoi.sizes, size);
		if (!multipointTool) {
			gd.addCheckbox("Auto-measure", Prefs.pointAutoMeasure);
			gd.addCheckbox("Auto-next slice", Prefs.pointAutoNextSlice);
			gd.addCheckbox("Add_to overlay", Prefs.pointAddToOverlay);
			gd.addCheckbox("Add to ROI Manager", Prefs.pointAddToManager);
		}
		gd.setInsets(5, 20, 0);
		gd.addCheckbox("Label points", !Prefs.noPointLabels);
		if (multipointTool) {
			gd.addCheckbox("Show all", Prefs.showAllPoints);
			gd.setInsets(15,0,5);
			String[] choices =  PointRoi.getCounterChoices();
			gd.addChoice("Counter:", choices, choices[getCounter()]);
			gd.setInsets(2, 75, 0);
			gd.addMessage(getCount(getCounter())+"    ");
		}
		gd.addHelp(help);
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
		}
	}
	
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		boolean redraw = false;
		// type
		int index = gd.getNextChoiceIndex();
		if (index!=PointRoi.getDefaultType()) {
			PointRoi.setDefaultType(index);
			redraw = true;
		}
		// color
		String selectionColor = gd.getNextChoice();
		Color sc = Colors.getColor(selectionColor, Color.yellow);
		if (sc!=Roi.getColor()) {
			Roi.setColor(sc);
			redraw = true;
			Toolbar.getInstance().repaint();
		}
		// size
		index = gd.getNextChoiceIndex();
		if (index!=PointRoi.getDefaultSize()) {
			PointRoi.setDefaultSize(index);
			redraw = true;
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
		if (multipointTool) {
			boolean showAllPoints = gd.getNextBoolean();
			if (showAllPoints!=Prefs.showAllPoints)
				redraw = true;
			Prefs.showAllPoints = showAllPoints;
			int counter = gd.getNextChoiceIndex();
			if (counter!=getCounter()) {
				setCounter(counter);
				redraw = true;
			}
		}
		if (redraw) {
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) {
				Roi roi = imp.getRoi();
				if (roi instanceof PointRoi)
					((PointRoi)roi).setShowLabels(!Prefs.noPointLabels);
				imp.draw();
			}
		}
		return true;
    }
    
    private static int getCounter() {
    	int counter = 0;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			Roi roi = imp.getRoi();
			if (roi instanceof PointRoi)
				counter = ((PointRoi)roi).getCounter();
		}
		return counter;
    }
    
    private static void setCounter(int counter) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			Roi roi = imp.getRoi();
			if (roi instanceof PointRoi)
				((PointRoi)roi).setCounter(counter);
		}
    }

    private static int getCount(int counter) {
    	int count = 0;
    	ImagePlus imp = WindowManager.getCurrentImage();
    	if (imp!=null) {
    		Roi roi = imp.getRoi();
    		if (roi==null)
    			return 0;
    		if (roi!=null && (roi instanceof PointRoi))
				count = ((PointRoi)roi).getCount(counter);
    	}
    	return count;
    }
    
    public static void update() {
    	if (gd!=null && gd.isShowing()) {
			Vector choices = gd.getChoices();
			Choice choice = (Choice)choices.elementAt(3);
			int counter = getCounter();
			choice.select(counter);
			((Label)gd.getMessage()).setText(""+getCount(counter));
		}
    }
    			
}
