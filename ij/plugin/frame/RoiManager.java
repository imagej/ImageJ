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
import ij.plugin.filter.*;

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
		WindowManager.addWindow(this);
		setLayout(new FlowLayout(FlowLayout.CENTER,5,5));
		int rows = 18;
		list = new List(rows, true);
		list.add("012345678901234567");
		list.addItemListener(this);
		add(list);
		panel = new Panel();
		panel.setLayout(new GridLayout(11, 1, 5, 0));
		addButton("Add");
		addButton("Add & Draw");
		addButton("Rename");
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
		list.remove(0);
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
		else if (command.equals("Rename"))
			rename();
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
			if (index<0) index = 0;
			restore(index);
		}
	}

	boolean add() {
		ImagePlus imp = getImage();
		if (imp==null)
			return false;
		Roi roi = imp.getRoi();
		if (roi==null) {
			error("The active image does not have a selection.");
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
		String name = roi.getName();
		String label = name!=null?getUniqueName(name):getUniqueLabel(type);
		list.add(label);
		if (name==null)
			roi.setName(label);
		rois.put(label, roi.clone());
		return true;
	}
	
	String getUniqueLabel(String label) {
			String label2 = label+"001";
			int n = 2;
			Roi roi2 = (Roi)rois.get(label2);
			String ext;
			while (roi2!=null) {
				roi2 = (Roi)rois.get(label2);
				if (roi2!=null) {
					ext = ""+n;
					while (ext.length()<3)
						ext = "0"+ext; 
					label2 = label2.substring(0,label2.length()-3)+ext;
				}
				n++;
				roi2 = (Roi)rois.get(label2);
			}
			return label2;
	}

	void addAndDraw() {
		if (add()) {
			list.select(list.getItemCount()-1);
			draw();
		}
	}
	
	boolean delete() {
		int count = list.getItemCount();
		if (count==0)
			return error("The RSelection list is empty.");
		int index[] = list.getSelectedIndexes();
		if (index.length==0)
			return error("At least one Selection in the list must be selected.");
		for (int i=count-1; i>=0; i--) {
			boolean delete = false;
			for (int j=0; j<index.length; j++) {
				if (index[j]==i)
					delete = true;
			}
			if (delete) {
				rois.remove(list.getItem(i));
				list.remove(i);
			}
		}
		return true;
	}
	
	boolean rename() {
		int index = list.getSelectedIndex();
		if (index<0)
			return error("Exactly one item in the list must be selected.");
		String name = list.getItem(index);
		GenericDialog gd = new GenericDialog("ROI Manager");
		gd.addStringField("Rename As:", name, 20);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		String name2 = gd.getNextString();
		name2 = getUniqueName(name2);
		Roi roi = (Roi)rois.get(name);
		rois.remove(name);
		roi.setName(name2);
		rois.put(name2, roi);
		list.replaceItem(name2, index);
		list.select(index);
		return true;
	}

	boolean restore(int index) {
		String label = list.getItem(index);
		Roi roi = (Roi)rois.get(label);
		ImagePlus imp = getImage();
		if (imp==null || roi==null)
			return false;
		Rectangle r = roi.getBoundingRect();
		if (r.x+r.width>imp.getWidth() || r.y+r.height>imp.getHeight())
			return error("This selection does not fit the current image.");
		imp.setRoi(roi);
		return true;
	}
	
	void open() {
		Macro.setOptions(null);
		OpenDialog od = new OpenDialog("Open Selection...", "");
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return;
		String path = directory + name;
		Opener o = new Opener();
		Roi roi = o.openRoi(path);
		if (roi!=null) {
			if (name.endsWith(".roi"))
				name = name.substring(0, name.length()-4);
			name = getUniqueName(name);
			list.add(name);
			rois.put(name, roi);
		}
	}

	String getUniqueName(String name) {
			String name2 = name;
			int n = 1;
			Roi roi2 = (Roi)rois.get(name2);
			while (roi2!=null) {
				roi2 = (Roi)rois.get(name2);
				if (roi2!=null)
					name2 = name+"-"+n;
				n++;
				roi2 = (Roi)rois.get(name2);
			}
			return name2;
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
  					String name = files[i];
					if (name.endsWith(".roi"))
						name = name.substring(0, name.length()-4);
					name = getUniqueName(name);
					list.add(name);
					rois.put(name, roi);
				}
			}
		}
	}
	
	boolean save() {
		if (list.getItemCount()==0)
			return error("The selection list is empty.");
		int[] index = list.getSelectedIndexes();
		if (index.length==0)
			return error("At least one item in the list must be selected.");
		String name = list.getItem(index[0]);
		Macro.setOptions(null);
		SaveDialog sd = new SaveDialog("Save Selection...", name, ".roi");
		String name2 = sd.getFileName();
		if (name == null)
			return false;
		if (index.length==1) {
			Roi roi = (Roi)rois.get(name);
			rois.remove(name);
			if (name2.endsWith(".roi"))
					name2 = name2.substring(0, name2.length()-4);
			rois.put(name2, roi);
			list.replaceItem(name2, index[0]);
			list.select(index[0]);
			index = list.getSelectedIndexes();
		}
		String dir = sd.getDirectory();
		for (int i=0; i<index.length; i++) {
			if (restore(index[i])) {
				name = list.getItem(index[i]);
				if (!name.endsWith(".roi"))
					name = name+".roi";
				IJ.run("Selection...", "path='"+dir+name+"'");
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
			return error("At least one item must be selected from the list.");
		
		int setup = IJ.setupDialog(imp, 0);
		if (setup==PlugInFilter.DONE)
			return false;
		int nSlices = setup==PlugInFilter.DOES_STACKS?imp.getStackSize():1;
		int currentSlice = imp.getCurrentSlice();
		for (int slice=1; slice<=nSlices; slice++) {
			if (nSlices>1) imp.setSlice(slice);
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
			return error("At least one item must be selected from the list.");
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
			return error("At least one item must be selected from the list.");
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

	/** Returns the selection list. */
	public List getList() {
		return list;
	}

}

