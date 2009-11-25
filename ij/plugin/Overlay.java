package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.frame.RoiManager;
import ij.macro.Interpreter;
import java.awt.*;
import java.util.Vector;

/** This plugin implements the commands in the Image/Overlay menu. */
public class Overlay implements PlugIn {
	private static Vector displayList2;
	private static boolean createImageRoi;

	public void run(String arg) {
		if (arg.equals("add"))
			addSelection();
		else if (arg.equals("image"))
			addImage();
		else if (arg.equals("flatten"))
			flatten();
		else if (arg.equals("hide"))
			hide();
		else if (arg.equals("show"))
			show();
		else if (arg.equals("remove"))
			remove();
		else if (arg.equals("from"))
			fromRoiManager();
		else if (arg.equals("to"))
			toRoiManager();
	}
			
	void addSelection() {
		ImagePlus imp = IJ.getImage();
		String macroOptions = Macro.getOptions();
		if (macroOptions!=null && IJ.macroRunning() && macroOptions.indexOf("remove")!=-1) {
			imp.setDisplayList(null);
			return;
		}
		Roi roi = imp.getRoi();
		if (roi==null && imp.getDisplayList()!=null) {
			GenericDialog gd = new GenericDialog("No Selection");
			gd.addMessage("\"Overlay>Add\" requires a selection.");
			gd.setInsets(15, 40, 0);
			gd.addCheckbox("Remove existing overlay", false);
			gd.showDialog();
			if (gd.wasCanceled()) return;
			if (gd.getNextBoolean()) imp.setDisplayList(null);
			return;
 		}
		if (roi==null) {
			IJ.error("This command requires a selection.");
			return;
		}
		roi = (Roi)roi.clone();
		Vector list = imp.getDisplayList();
		if (list!=null && list.size()>0) {
			Roi roi2 = (Roi)list.get(list.size()-1);
			roi.setStrokeColor(roi2.getStrokeColor());
			roi.setStrokeWidth(roi2.getStrokeWidth());
			roi.setFillColor(roi2.getFillColor());
		}
		boolean points = roi instanceof PointRoi && ((PolygonRoi)roi).getNCoordinates()>1;
		if (points) roi.setStrokeColor(Color.red);
		if (!IJ.altKeyDown()) {
			RoiProperties rp = new RoiProperties("Add to Overlay", roi);
			if (!rp.showDialog()) return;
		}
		String name = roi.getName();
		boolean newOverlay = name!=null && name.equals("new-overlay");
		if (list==null || newOverlay) list = new Vector();
		list.addElement(roi);
		imp.setDisplayList(list);
		displayList2 = list;
		if (points) imp.killRoi();
	}
	
	void addImage() {
		ImagePlus imp = IJ.getImage();
		int[] wList = WindowManager.getIDList();
		if (wList==null || wList.length<2) {
			IJ.error("Add Image...", "The command requires at least two open images.");
			return;
		}
		String[] titles = new String[wList.length];
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp2 = WindowManager.getImage(wList[i]);
			titles[i] = imp2!=null?imp2.getTitle():"";
		}
		int x=0, y=0;
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isArea()) {
			Rectangle r = roi.getBounds();
			x = r.x; y = r.y;
		}
		int index = 0;
		if (wList.length==2) {
			ImagePlus i1 = WindowManager.getImage(wList[0]);
			ImagePlus i2 = WindowManager.getImage(wList[1]);
			if (i2.getWidth()<i1.getWidth() && i2.getHeight()<i1.getHeight())
				index = 1;
		} else if (imp.getID()==wList[0])
			index = 1;


		GenericDialog gd = new GenericDialog("Add Image...");
		gd.addChoice("Image to add:", titles, titles[index]);
		gd.addNumericField("X location:", x, 0);
		gd.addNumericField("Y location:", y, 0);
		gd.addNumericField("Opacity (0-100%):", 100, 0);
		gd.addCheckbox("Create image selection", createImageRoi);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		index = gd.getNextChoiceIndex();
		x = (int)gd.getNextNumber();
		y = (int)gd.getNextNumber();
		double opacity = gd.getNextNumber()/100.0;
		createImageRoi = gd.getNextBoolean();
		ImagePlus overlay = WindowManager.getImage(wList[index]);
		if (wList.length==2) {
			ImagePlus i1 = WindowManager.getImage(wList[0]);
			ImagePlus i2 = WindowManager.getImage(wList[1]);
			if (i2.getWidth()<i1.getWidth() && i2.getHeight()<i1.getHeight()) {
				imp = i1;
				overlay = i2;
			}
		}
		if (overlay==imp) {
			IJ.error("Add Image...", "Image to be added cannot be the same as\n\""+imp.getTitle()+"\".");
			return;
		}
		if (overlay.getWidth()>imp.getWidth() && overlay.getHeight()>imp.getHeight()) {
			IJ.error("Add Image...", "Image to be added cannnot be larger than\n\""+imp.getTitle()+"\".");
			return;
		}
		if (createImageRoi && x==0 && y==0) {
			x = imp.getWidth()/2-overlay.getWidth()/2;
			y = imp.getHeight()/2-overlay.getHeight()/2;
		}	
		roi = new ImageRoi(x, y, overlay.getProcessor());
		if (opacity!=1.0) ((ImageRoi)roi).setOpacity(opacity);
		if (createImageRoi)
			imp.setRoi(roi);
		else {
			Vector list = imp.getDisplayList();
			if (list==null) list = new Vector();
			list.addElement(roi);
			imp.setDisplayList(list);
			displayList2 = list;
		}
	}

	void hide() {
		ImagePlus imp = IJ.getImage();
		Vector list = imp.getDisplayList();
		if (list!=null) {
			displayList2 = list;
			imp.setDisplayList(null);
		}
		RoiManager rm = RoiManager.getInstance();
		if (rm!=null) rm.runCommand("show none");
	}

	void show() {
		ImagePlus imp = IJ.getImage();
		if (displayList2!=null)
			imp.setDisplayList(displayList2);
		RoiManager rm = RoiManager.getInstance();
		if (rm!=null) rm.runCommand("show all");
	}

	void remove() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) imp.setDisplayList(null);
		displayList2 = null;
		RoiManager rm = RoiManager.getInstance();
		if (rm!=null) rm.runCommand("show none");
	}

	void flatten() {
		ImagePlus imp = IJ.getImage();
		ImagePlus imp2 = imp.flatten();
		imp2.setTitle(WindowManager.getUniqueName(imp.getTitle()));
		imp2.show();
	}
	
	void fromRoiManager() {
		ImagePlus imp = IJ.getImage();
		RoiManager rm = RoiManager.getInstance();
		if (rm==null) {
			IJ.error("ROI Manager is not open");
			return;
		}
		Roi[] rois = rm.getRoisAsArray();
		if (rois.length==0) {
			IJ.error("ROI Manager is empty");
			return;
		}
		Vector list = new Vector();
		for (int i=0; i<rois.length; i++)
			list.addElement((Roi)rois[i].clone());
		imp.setDisplayList(list);
		ImageCanvas ic = imp.getCanvas();
		if (ic!=null) ic.setShowAllROIs(false);
		rm.setEditMode(imp, false);
		imp.killRoi();
	}
	
	void toRoiManager() {
		ImagePlus imp = IJ.getImage();
		Vector list = imp.getDisplayList();
		if (list==null) {
			IJ.error("Overlay required");
			return;
		}
		RoiManager rm = RoiManager.getInstance();
		if (rm==null) {
			if (Macro.getOptions()!=null && Interpreter.isBatchMode())
				rm = Interpreter.getBatchModeRoiManager();
			if (rm==null) {
				Frame frame = WindowManager.getFrame("ROI Manager");
				if (frame==null)
					IJ.run("ROI Manager...");
				frame = WindowManager.getFrame("ROI Manager");
				if (frame==null || !(frame instanceof RoiManager))
					return;
				rm = (RoiManager)frame;
			}
		}
		rm.runCommand("reset");
		for (int i=0; i<list.size(); i++)
			rm.add(imp, (Roi)list.get(i), i);
		rm.setEditMode(imp, true);
		if (rm.getCount()==list.size())
			imp.setDisplayList(null);
	}
	
}
