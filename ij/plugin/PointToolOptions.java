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
	private boolean isMacro;
	
	public static final String help = "<html>"
	+"<h1>Point Tool</h1>"
	+"<font size=+1>"
	+"<ul>"
	+"<li> Click on a point and drag to move it.<br>"
	+"<li> Alt-click, or control-click, on a point to delete it.<br>"
	+"<li> Press 'alt+y' (<i>Edit&gt;Selection&gt;Properties</i> plus<br>alt key) to display the counts in a results table.<br>"
	+"<li> Press 'm' (<i>Analyze&gt;Measure</i>) to list the counter<br>and stack position associated with each point.<br>"
	+"<li> Use <i>File&gt;Save As&gt;Tiff</i> or <i>File&gt;Save As&gt;Selection</i><br>to save the points and counts.<br>"
	+"<li> Press 'F' (<i>Image&gt;Overlay</i>&gt;Flatten</i>) to create an<br>RGB image with embedded markers for export.<br>"
	+"<li> Hold the shift key down and points will be<br>constrained to a horizontal or vertical line.<br>"
	+"<li> Use <i>Edit&gt;Selection&gt;Select None</i> to delete a<br>multi-point selection.<br>"
	+"</ul>"
	+" <br>"
	+"</font>";
 
 	public void run(String arg) {
 		if (gd!=null && gd.isShowing() && !IJ.isMacro()) {
 			gd.toFront();
 			update();
 		} else
 			showDialog();
 	}
		
	void showDialog() {
		String options = IJ.isMacro()?Macro.getOptions():null;
		isMacro = options!=null;
		boolean legacyMacro = false;
		if (isMacro) {
			options = options.replace("selection=", "color=");
			options = options.replace("marker=", "size=");
			options = options.replace("type=Crosshair", "type=Cross");
			Macro.setOptions(options);
			legacyMacro = options.contains("auto-") || options.contains("add");
		}
		multipointTool = Toolbar.getMultiPointMode() && !legacyMacro;
		if (isMacro && !legacyMacro)
			multipointTool = true;
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
		gd.addCheckbox("Show on all slices", Prefs.showAllPoints);
		if (multipointTool) {
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
		int typeIndex = gd.getNextChoiceIndex();
		if (typeIndex!=PointRoi.getDefaultType()) {
			PointRoi.setDefaultType(typeIndex);
			redraw = true;
		}
		// color
		String selectionColor = gd.getNextChoice();
		Color sc = Colors.getColor(selectionColor, Color.yellow);
		if (sc!=Roi.getColor()) {
			Roi.setColor(sc);
			redraw = true;
			Toolbar tb = Toolbar.getInstance();
			if (tb!=null) tb.repaint();
		}
		// size
		int sizeIndex = gd.getNextChoiceIndex();
		if (sizeIndex!=PointRoi.getDefaultSize()) {
			PointRoi.setDefaultSize(sizeIndex);
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
		boolean updateLabels = false;
		boolean noPointLabels = !gd.getNextBoolean();
		if (noPointLabels!=Prefs.noPointLabels) {
			redraw = true;
			updateLabels = true;
		}
		Prefs.noPointLabels = noPointLabels;
		boolean showAllPoints = gd.getNextBoolean();
		if (showAllPoints!=Prefs.showAllPoints)
			redraw = true;
		Prefs.showAllPoints = showAllPoints;
		if (multipointTool) {
			int counter = gd.getNextChoiceIndex();
			if (counter!=getCounter()) {
				setCounter(counter);
				redraw = true;
			}
		}
		if (isMacro) {
			PointRoi roi = getPointRoi();
			if (roi!=null) {
				roi.setPointType(typeIndex);
				roi.setStrokeColor(sc);
				roi.setSize(sizeIndex);
			}
		}
		if (redraw) {
			ImagePlus imp = null;
			PointRoi roi = getPointRoi();
			if (roi!=null) {
				roi.setShowLabels(!Prefs.noPointLabels);
				imp = roi.getImage();
			}
			if (updateLabels) {
				imp = WindowManager.getCurrentImage();
				Overlay overlay = imp!=null?imp.getOverlay():null;
				int pointRoiCount = 0;
				if (overlay!=null) {
					for (int i=0; i<overlay.size(); i++) {
						Roi r = overlay.get(i);
						roi = r!=null && (r instanceof PointRoi)?(PointRoi)r:null;
						if (roi!=null) {
							roi.setShowLabels(!Prefs.noPointLabels);
							pointRoiCount++;
						}
					}
					if (pointRoiCount==0)
						imp = null;
				}
			}
			if (imp!=null)
				imp.draw();
		}
		return true;
    }
    
    private static int getCounter() {
     	PointRoi roi = getPointRoi();
     	return roi!=null?roi.getCounter():0;
    }
    
    private static void setCounter(int counter) {
    	PointRoi roi = getPointRoi();
		if (roi!=null)
			roi.setCounter(counter);
		PointRoi.setDefaultCounter(counter);
    }
    
    private static PointRoi getPointRoi() {
    	ImagePlus imp = WindowManager.getCurrentImage();
    	if (imp==null)
    		return null;
		Roi roi = imp.getRoi();
		if (roi==null)
			return null;
		if (roi instanceof PointRoi)
			return (PointRoi)roi;
		else
			return null;
    }

    private static int getCount(int counter) {
     	PointRoi roi = getPointRoi();
     	return roi!=null?roi.getCount(counter):0;
    }
    
    public static void update() {
    	if (gd!=null && gd.isShowing()) {
			Vector choices = gd.getChoices();
			if (choices==null || choices.size()<4)
				return;
			Choice counterChoice = (Choice)choices.elementAt(3);
			int counter = getCounter();
			int count = getCount(counter);
			counterChoice.select(counter);
			((Label)gd.getMessage()).setText(""+count);
		}
    }
    			
}
