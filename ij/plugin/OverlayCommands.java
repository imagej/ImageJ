package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.frame.RoiManager;
import ij.plugin.frame.Recorder;
import ij.macro.Interpreter;
import ij.io.RoiDecoder;
import ij.plugin.filter.PlugInFilter;
import ij.measure.ResultsTable;
import java.awt.*;
import java.util.ArrayList;

/** This plugin implements the commands in the Image/Overlay menu. */
public class OverlayCommands implements PlugIn {
	private static int opacity = 100;
	private static Roi defaultRoi;
	private static boolean zeroTransparent;
	
	static {
		defaultRoi = new Roi(0, 0, 1, 1);
		defaultRoi.setPosition(1); // set stacks positions by default
	}

	public void run(String arg) {
		if (arg.equals("add"))
			addSelection();
		else if (arg.equals("image"))
			addImage(false);
		else if (arg.equals("image-roi"))
			addImage(true);
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
		else if (arg.equals("list"))
			list();
		else if (arg.equals("options"))
			options();
	}
			
	void addSelection() {
		ImagePlus imp = IJ.getImage();
		String macroOptions = Macro.getOptions();
		if (macroOptions!=null && IJ.macroRunning() && macroOptions.indexOf("remove")!=-1) {
			imp.setOverlay(null);
			return;
		}
		Roi roi = imp.getRoi();
		if (roi==null && imp.getOverlay()!=null) {
			GenericDialog gd = new GenericDialog("No Selection");
			gd.addMessage("\"Overlay>Add\" requires a selection.");
			gd.setInsets(15, 40, 0);
			gd.addCheckbox("Remove existing overlay", false);
			gd.showDialog();
			if (gd.wasCanceled()) return;
			if (gd.getNextBoolean())
				imp.setOverlay(null);
			return;
 		}
		if (roi==null) {
			IJ.error("This command requires a selection.");
			return;
		}
		roi = (Roi)roi.clone();
		Overlay overlay = imp.getOverlay();
		if (!roi.isDrawingTool()) {
			if (roi.getStroke()==null)
				roi.setStrokeWidth(defaultRoi.getStrokeWidth());
			if (roi.getStrokeColor()==null || Line.getWidth()>1&&defaultRoi.getStrokeColor()!=null)
				roi.setStrokeColor(defaultRoi.getStrokeColor());
			if (roi.getFillColor()==null)
				roi.setFillColor(defaultRoi.getFillColor());
		}
		setPosition(imp, roi);
		boolean points = roi instanceof PointRoi && ((PolygonRoi)roi).getNCoordinates()>1;
		if (IJ.altKeyDown() || (IJ.macroRunning() && Macro.getOptions()!=null)) {
			RoiProperties rp = new RoiProperties("Add to Overlay", roi);
			if (!rp.showDialog()) return;
			defaultRoi.setStrokeColor(roi.getStrokeColor());
			defaultRoi.setStrokeWidth(roi.getStrokeWidth());
			defaultRoi.setFillColor(roi.getFillColor());
		}
		String name = roi.getName();
		boolean newOverlay = name!=null && name.equals("new-overlay");
		Roi roiClone = (Roi)roi.clone();
		if (roi.getStrokeColor()==null)
			roi.setStrokeColor(Roi.getColor());
		if (overlay==null || newOverlay)
			overlay = OverlayLabels.createOverlay();
		overlay.add(roi);
		imp.setOverlay(overlay);
		boolean brushRoi = roi.getType()==Roi.COMPOSITE && Toolbar.getToolId()==Toolbar.OVAL && Toolbar.getBrushSize()>0;
		if (points || (roi instanceof ImageRoi) || (roi instanceof Arrow&&!Prefs.keepArrowSelections) || brushRoi)
			imp.deleteRoi();
		Undo.setup(Undo.OVERLAY_ADDITION, imp);
	}
	
	void addImage(boolean createImageRoi) {
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

		String title = createImageRoi?"Create Image ROI":"Add Image...";
		if (IJ.isMacro()) {
			opacity = 100;
			zeroTransparent = false;
		}
		GenericDialog gd = new GenericDialog(title);
		if (createImageRoi)
			gd.addChoice("Image:", titles, titles[index]);
		else {
			gd.addChoice("Image to add:", titles, titles[index]);
			gd.addNumericField("X location:", x, 0);
			gd.addNumericField("Y location:", y, 0);
		}
		gd.addNumericField("Opacity (0-100%):", opacity, 0);
		gd.addCheckbox("Zero transparent", zeroTransparent);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		index = gd.getNextChoiceIndex();
		if (!createImageRoi) {
			x = (int)gd.getNextNumber();
			y = (int)gd.getNextNumber();
		}
		opacity = (int)gd.getNextNumber();
		zeroTransparent = gd.getNextBoolean();
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
		roi.setName(overlay.getShortTitle());
		if (opacity!=100)
			((ImageRoi)roi).setOpacity(opacity/100.0);
		((ImageRoi)roi).setZeroTransparent(zeroTransparent);
		if (createImageRoi)
			imp.setRoi(roi);
		else {
			setPosition(imp, roi);
			Overlay overlayList = imp.getOverlay();
			if (overlayList==null)
				overlayList = new Overlay();
			overlayList.add(roi);
			imp.setOverlay(overlayList);
			Undo.setup(Undo.OVERLAY_ADDITION, imp);
		}
	}
	
	private void setPosition(ImagePlus imp, Roi roi) {
		boolean setPos = defaultRoi.getPosition()!=0;
		int stackSize = imp.getStackSize();
		if (setPos && stackSize>1) {
			if (imp.isHyperStack()||imp.isComposite()) {
				boolean compositeMode = imp.isComposite() && ((CompositeImage)imp).getMode()==IJ.COMPOSITE;
				int channel = !compositeMode||imp.getNChannels()==stackSize?imp.getChannel():0;
				if (imp.getNSlices()>1)
					roi.setPosition(channel, imp.getSlice(), 0);
				else if (imp.getNFrames()>1)
					roi.setPosition(channel, 0, imp.getFrame());
			} else
				roi.setPosition(imp.getCurrentSlice());
		}
	}

	void hide() {
		ImagePlus imp = IJ.getImage();
		imp.setHideOverlay(true);
		RoiManager rm = RoiManager.getInstance();
		if (rm!=null) rm.runCommand("show none");
	}

	void show() {
		ImagePlus imp = IJ.getImage();
		imp.setHideOverlay(false);
		if (imp.getOverlay()==null) {
			RoiManager rm = RoiManager.getInstance();
			if (rm!=null && rm.getCount()>1) {
				if (!IJ.isMacro()) rm.toFront();
				rm.runCommand("show all with labels");
			}
		}
	}

	void remove() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			ImageCanvas ic = imp.getCanvas();
			if (ic!=null)
				ic.setShowAllList(null);
			imp.setOverlay(null);
		}
	}

	void flatten() {
		ImagePlus imp = IJ.getImage();
		Overlay overlay = imp.getOverlay();
		Overlay roiManagerOverlay = null;
		ImageCanvas ic = imp.getCanvas();
		if (ic!=null)
			roiManagerOverlay = ic.getShowAllList();
		if (imp.getBitDepth()==24 && overlay==null && imp.getRoi()==null && roiManagerOverlay==null && !imp.isComposite() && !IJ.macroRunning()) {
			IJ.error("Flatten", "Overlay or selection required to flatten RGB image");
			return;
		}
		int flags = IJ.setupDialog(imp, 0);
		if (flags==PlugInFilter.DONE)
			return;
		else if (flags==PlugInFilter.DOES_STACKS) {
			//Added by Marcel Boeglin 2014.01.24
			if (overlay==null && roiManagerOverlay==null && !imp.isComposite()) {
				IJ.error("Flatten", "Overlay or multi-channel image required");
				return;
			}
			flattenStack(imp);
			if (Recorder.record)
				Recorder.recordCall("imp.flattenStack();");
		} else {
			ImagePlus imp2 = imp.flatten();
			imp2.setTitle(WindowManager.getUniqueName(imp.getTitle()));
			imp2.show();
			if (Recorder.record) // Added by Marcel Boeglin 2014.01.12
				Recorder.recordCall("imp2 = imp.flatten();");
		}
	}


	//Marcel Boeglin 2014.01.25
	void flattenStack(ImagePlus imp) {
		imp.flattenStack();
	}
	
	void fromRoiManager() {
		ImagePlus imp = IJ.getImage();
		RoiManager rm = RoiManager.getInstance2();
		if (rm==null) {
			IJ.error("ROI Manager is not open");
			return;
		}
		Roi[] rois = rm.getRoisAsArray();
		if (rois.length==0) {
			IJ.error("ROI Manager is empty");
			return;
		}
		rm.moveRoisToOverlay(imp);
		imp.deleteRoi();
	}

	void toRoiManager() {
		ImagePlus imp = IJ.getImage();
		Overlay overlay = imp.getOverlay();
		if (overlay==null) {
			IJ.error("Overlay required");
			return;
		}
		RoiManager rm = RoiManager.getInstance2();
		if (rm==null)
			rm = new RoiManager();
		if (overlay.size()>=4 && overlay.get(3).getPosition()!=0)
			Prefs.showAllSliceOnly = true;
		rm.setOverlay(overlay);
		imp.setOverlay(null);
	}
	
	void options() {
		ImagePlus imp = WindowManager.getCurrentImage();
		Overlay overlay = null;
		Roi roi = null;
		if (imp!=null) {
			overlay = imp.getOverlay();
			roi = imp.getRoi();
			if (roi!=null)
				roi = (Roi)roi.clone();
		}
		if (roi==null)
			roi = defaultRoi;
		if (roi==null) {
			int size = imp!=null?imp.getWidth():512;
			roi = new Roi(0, 0, size/4, size/4);
		}
		if (!roi.isDrawingTool()) {
			if (roi.getStroke()==null)
				roi.setStrokeWidth(defaultRoi.getStrokeWidth());
			if (roi.getStrokeColor()==null || Line.getWidth()>1&&defaultRoi.getStrokeColor()!=null)
				roi.setStrokeColor(defaultRoi.getStrokeColor());
			if (roi.getFillColor()==null)
				roi.setFillColor(defaultRoi.getFillColor());
		}
		boolean points = roi instanceof PointRoi && ((PolygonRoi)roi).getNCoordinates()>1;
		if (points) roi.setStrokeColor(Color.red);
		roi.setPosition(defaultRoi.getPosition());
		RoiProperties rp = new RoiProperties("Overlay Options", roi);
		if (!rp.showDialog()) return;
		defaultRoi = roi;
	}
	
	void list() {
		ImagePlus imp = IJ.getImage();
		Overlay overlay = imp.getOverlay();
		if (overlay!=null)
			listRois(overlay.toArray());
	}
	
	public static void listRois(Roi[] rois) {
		ResultsTable rt = new ResultsTable();
		for (int i=0; i<rois.length; i++) {
			if (rois[i]==null)
				continue;
			Rectangle r = rois[i].getBounds();
			String color = Colors.colorToString(rois[i].getStrokeColor());
			String fill = Colors.colorToString(rois[i].getFillColor());
			double strokeWidth = rois[i].getStrokeWidth();
			int digits = strokeWidth==(int)strokeWidth?0:1;
			String sWidth = IJ.d2s(strokeWidth,digits);
			int position = rois[i].getPosition();
			int c = rois[i].getCPosition();
			int z = rois[i].getZPosition();
			int t = rois[i].getTPosition();
			rt.setValue("Index", i, i);
			rt.setValue("Name", i, rois[i].getName());
			rt.setValue("Type", i, rois[i].getTypeAsString());
			rt.setValue("X", i, r.x);
			rt.setValue("Y", i, r.y);
			rt.setValue("Width", i, r.width);		
			rt.setValue("Height", i, r.height);		
			rt.setValue("Points", i, rois[i].size());
			rt.setValue("Color", i, color);		
			rt.setValue("Fill", i, fill);		
			rt.setValue("LWidth", i, sWidth);		
			rt.setValue("Pos", i, position);	
			rt.setValue("C", i, c);	
			rt.setValue("Z", i, z);	
			rt.setValue("T", i, t);	
		}
		rt.show("Overlay Elements");
	}
	
}
