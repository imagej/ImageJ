package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.awt.List;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.filter.PlugInFilter;

/** This plugin implements the Analyze/Tools/ROI Manager command. */
public class RoiManager extends PlugInFrame implements ActionListener, ItemListener {

	Panel panel;
	static Frame instance;
	java.awt.List list;
	Hashtable rois = new Hashtable();

	public RoiManager() {
		super("ROI Manager");
		if (instance!=null) {
			instance.toFront();
			return;
		}
		instance = this;
		setLayout(new FlowLayout(FlowLayout.CENTER,5,5));
		int rows = 18;
		list = new List(rows, true);
		list.add("012345678901234567");
		list.addItemListener(this);
		add(list);
		panel = new Panel();
		panel.setLayout(new GridLayout(10, 1, 5, 5));
		addButton("Add");
		addButton("Add & Draw");
		addButton("Delete");
		addButton("Open");
		addButton("Open All");
		addButton("Save");
		addButton("Select All");
		addButton("Measure");
		addButton("Draw");
		addButton("Fill");
		add(panel);
		
		pack();
		list.delItem(0);
		GUI.center(this);
		show();
	}
	
	void addButton(String label) {
		Button b = new Button(label);
		b.addActionListener(this);
		panel.add(b);
	}

	public void actionPerformed(ActionEvent e) {
		String label = e.getActionCommand();
		if (label==null)
			return;
		String command = label;
		if (command.equals("Add"))
			add();
		if (command.equals("Add & Draw"))
			addAndDraw();
		else if (command.equals("Delete"))
			delete();
		else if (command.equals("Open"))
			open();
		else if (command.equals("Open All"))
			openAll();
		else if (command.equals("Save"))
			save();
		else if (command.equals("Select All"))
			selectAll();
		else if (command.equals("Measure"))
			measure();
		else if (command.equals("Draw"))
			draw();
		else if (command.equals("Fill"))
			fill();
	}
	
	public void itemStateChanged(ItemEvent e) {
		if (e.getStateChange()==ItemEvent.SELECTED
		&& WindowManager.getCurrentImage()!=null) {
			int index = 0;
            try {index = Integer.parseInt(e.getItem().toString());}
            catch (NumberFormatException ex) {}
			restore(index);
		}
	}

	boolean add() {
		ImagePlus imp = getImage();
		if (imp==null)
			return false;
		Roi roi = imp.getRoi();
		if (roi==null) {
			error("The active image does not have an ROI.");
			return false;
		}
		String type = null;
		switch (roi.getType()) {
			case Roi.RECTANGLE: type ="Rectangle"; break;
			case Roi.OVAL: type = "Oval"; break;
			case Roi.POLYGON: type = "Polygon"; break;
			case Roi.FREEROI: type = "Freehand"; break;
			case Roi.TRACED_ROI: type = "Traced"; break;
			case Roi.LINE: type = "Line"; break;
			case Roi.POLYLINE: type = "Polyline"; break;
			case Roi.FREELINE: type = "Freeline"; break;
		}
		if (type==null)
			return false;
		Rectangle r = roi.getBoundingRect();
		//String label = type+" ("+(r.x+r.width/2)+","+(r.y+r.height/2)+")";
		String label = type+(r.x+r.width/2)+"-"+(r.y+r.height/2);
		list.add(label);
		rois.put(label, roi.clone());
		return true;
	}
	
	void addAndDraw() {
		if (add()) {
			list.select(list.getItemCount()-1);
			draw();
		}
	}
	
	boolean delete() {
		if (list.getItemCount()==0)
			return error("The ROI list is empty.");
		int index[] = list.getSelectedIndexes();
		if (index.length==0)
			return error("At least one ROI in the list must be selected.");
		for (int i=index.length-1; i>=0; i--) {
			rois.remove(list.getItem(index[i]));
			list.delItem(index[i]);
		}
		return true;
	}
	
	boolean restore(int index) {
		String label = list.getItem(index);
		Roi roi = (Roi)rois.get(label);
		ImagePlus imp = getImage();
		if (imp==null)
			return false;
		Rectangle r = roi.getBoundingRect();
		if (r.x+r.width>imp.getWidth() || r.y+r.height>imp.getHeight())
			return error("This ROI does not fit the current image.");
		imp.setRoi(roi);
		return true;
	}
	
	void open() {
		Macro.setOptions(null);
		OpenDialog od = new OpenDialog("Open ROI...", "");
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return;
		String path = directory + name;
		Opener o = new Opener();
		Roi roi = o.openRoi(path);
		if (roi!=null) {
			list.add(name);
			rois.put(name, roi);
		}
	}

	void openAll() {
		Macro.setOptions(null);
		Macro.setOptions(null);
		OpenDialog od = new OpenDialog("Select a file in the folder...", "");
		if (od.getFileName()==null) return;
		String dir  = od.getDirectory();
		String[] files = new File(dir).list();
		if (files==null) return;
		for (int i=0; i<files.length; i++) {
			File f = new File(dir+files[i]);
			if (!f.isDirectory() && files[i].endsWith(".roi")) {
                			Roi roi = new Opener().openRoi(dir+files[i]);
  				if (roi!=null) {
					list.add(files[i]);
					rois.put(files[i], roi);
				}
			}
		}
	}
	
	boolean save() {
		if (list.getItemCount()==0)
			return error("The ROI list is empty.");
		int index[] = list.getSelectedIndexes();
		if (index.length==0)
			return error("At least one ROI in the list must be selected.");
		String name = list.getItem(index[0]);
		Macro.setOptions(null);
		SaveDialog sd = new SaveDialog("Save ROI...", name, ".roi");
		name = sd.getFileName();
		if (name == null)
			return false;
		String dir = sd.getDirectory();
		for (int i=0; i<index.length; i++) {
			if (restore(index[i])) {
				if (index.length>1)
					name = list.getItem(index[i])+".roi";
				IJ.run("ROI...", "path='"+dir+name+"'");
			} else
				break;
		}
		return true;
	}
	
	void selectAll() {
		boolean allSelected = true;
		int count = list.getItemCount();
		for (int i=0; i<count; i++) {
			if (!list.isIndexSelected(i))
				allSelected = false;
		}
		for (int i=0; i<count; i++) {
			if (allSelected)
				list.deselect(i);
			else
				list.select(i);
		}
	}
	
	boolean measure() {
		ImagePlus imp = getImage();
		if (imp==null)
			return false;
		int[] index = list.getSelectedIndexes();
		if (index.length==0)
			return error("At least one ROI must be selected from the list.");
		
		int setup = IJ.setupDialog(imp, 0);
		if (setup==PlugInFilter.DONE)
			return false;
		int nSlices = setup==PlugInFilter.DOES_STACKS?imp.getStackSize():1;
		int currentSlice = imp.getCurrentSlice();
		for (int slice=1; slice<=nSlices; slice++) {
			imp.setSlice(slice);
			for (int i=0; i<index.length; i++) {
				if (restore(index[i]))
					IJ.run("Measure");
				else
					break;
			}
		}
		imp.setSlice(currentSlice);
		if (index.length>1)
			IJ.run("Select None");
		return true;
	}	

	boolean fill() {
		int[] index = list.getSelectedIndexes();
		if (index.length==0)
			return error("At least one ROI must be selected from the list.");
		ImagePlus imp = WindowManager.getCurrentImage();
		Undo.setup(Undo.COMPOUND_FILTER, imp);
		for (int i=0; i<index.length; i++) {
			if (restore(index[i])) {
				IJ.run("Fill");
				IJ.run("Select None");
			} else
				break;
		}
		Undo.setup(Undo.COMPOUND_FILTER_DONE, imp);
		return true;
	}	
	
	boolean draw() {
		int[] index = list.getSelectedIndexes();
		if (index.length==0)
			return error("At least one ROI must be selected from the list.");
		ImagePlus imp = WindowManager.getCurrentImage();
		Undo.setup(Undo.COMPOUND_FILTER, imp);
		for (int i=0; i<index.length; i++) {
			if (restore(index[i])) {
				IJ.run("Draw");
				IJ.run("Select None");
			} else
				break;
		}
		Undo.setup(Undo.COMPOUND_FILTER_DONE, imp);
		return true;
	}
		
	ImagePlus getImage() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) {
			error("There are no images open.");
			return null;
		} else
			return imp;
	}

	boolean error(String msg) {
		new MessageDialog(this, "ROI Manager", msg);
		return false;
	}
	
	public void processWindowEvent(WindowEvent e) {
		super.processWindowEvent(e);
		if (e.getID()==WindowEvent.WINDOW_CLOSING) {
			instance = null;	
		}
	}
	
	/** Returns a reference to the ROI Manager
		or null if it is not open. */
	public static RoiManager getInstance() {
		return (RoiManager)instance;
	}

	/** Returns the ROI Hashtable. */
	public Hashtable getROIs() {
		return rois;
	}

	/** Returns the ROI list. */
	public List getList() {
		return list;
	}

}

