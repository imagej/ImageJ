package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.awt.List;
import java.util.zip.*;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.filter.*;
import ij.util.Tools;
import ij.macro.Interpreter;
import ij.measure.Calibration;

/** This plugin implements the Analyze/Tools/ROI Manager command. */
public class RoiManager extends PlugInFrame implements ActionListener, ItemListener {

	static final int BUTTONS = 10;
	Panel panel;
	static Frame instance;
	java.awt.List list;
	Hashtable rois = new Hashtable();
	Roi roiCopy;
	int slice2;
	boolean canceled;
	boolean macro;
	boolean ignoreInterrupts;

	public RoiManager() {
		super("ROI Manager");
		if (instance!=null) {
			instance.toFront();
			return;
		}
		instance = this;
		ImageJ ij = IJ.getInstance();
 		addKeyListener(ij);
		WindowManager.addWindow(this);
		setLayout(new FlowLayout(FlowLayout.CENTER,5,5));
		int rows = 15;
		boolean allowMultipleSelections = IJ.isMacintosh();
		list = new List(rows, allowMultipleSelections);
		list.add("012345678901234");
		list.addItemListener(this);
 		list.addKeyListener(ij);
		add(list);
		panel = new Panel();
		int nButtons = IJ.isJava2()?BUTTONS:BUTTONS-1;
		panel.setLayout(new GridLayout(nButtons, 1, 5, 0));
		addButton("Add");
		addButton("Update");
		addButton("Delete");
		addButton("Rename");
		addButton("Open");
		addButton("Save");
		addButton("Measure");
		addButton("Draw");
		addButton("Deselect");
		if (IJ.isJava2())
			addButton("Combine");
		add(panel);		
		pack();
		list.remove(0);
		GUI.center(this);
		show();
	}
	
	void addButton(String label) {
		Button b = new Button(label);
		b.addActionListener(this);
		b.addKeyListener(IJ.getInstance());
		panel.add(b);
	}

	public void actionPerformed(ActionEvent e) {
		int modifiers = e.getModifiers();
		boolean altKeyDown = (modifiers&ActionEvent.ALT_MASK)!=0 || IJ.altKeyDown();
		boolean shiftKeyDown = (modifiers&ActionEvent.SHIFT_MASK)!=0 || IJ.shiftKeyDown();
		IJ.setKeyUp(KeyEvent.VK_ALT);
		IJ.setKeyUp(KeyEvent.VK_SHIFT);
		String label = e.getActionCommand();
		if (label==null)
			return;
		String command = label;
		if (command.equals("Add"))
			add(shiftKeyDown, altKeyDown);
		else if (command.equals("Update"))
			update();
		else if (command.equals("Delete"))
			delete(false);
		else if (command.equals("Rename"))
			rename();
		else if (command.equals("Open"))
			open(null);
		else if (command.equals("Save"))
			save();
		else if (command.equals("Measure"))
			measure();
		else if (command.equals("Draw"))
			draw();
		else if (command.equals("Deselect"))
			select(-1);
		else if (command.equals("Combine")) 
			combine();
	}

	public void itemStateChanged(ItemEvent e) {
		//IJ.log("itemStateChanged: "+e.getItem().toString()+"  "+e+"  "+ignoreInterrupts);
		if (e.getStateChange()==ItemEvent.SELECTED
		&& WindowManager.getCurrentImage()!=null && !ignoreInterrupts) {
			int index = 0;
            try {index = Integer.parseInt(e.getItem().toString());}
            catch (NumberFormatException ex) {}
			if (index<0) index = 0;
			restore(index, true);
		}
	}
	
	void add(boolean shiftKeyDown, boolean altKeyDown) {
		if (shiftKeyDown)
			addAndDraw(altKeyDown);
		else if (altKeyDown)
			add(true);
		else
			add(false);
	}

	boolean add(boolean promptForName) {
		ImagePlus imp = getImage();
		if (imp==null)
			return false;
		Roi roi = imp.getRoi();
		if (roi==null) {
			error("The active image does not have a selection.");
			return false;
		}
		String name = roi.getName();
		int slice1 = imp.getCurrentSlice();
		if (name!=null && roiCopy!=null && name.equals(roiCopy.getName()) && name.indexOf('-')!=-1) {
			Rectangle r1 = roi.getBounds();
			Rectangle r2 = roiCopy.getBounds();
			if (r1.x!=r2.x || r1.y!=r2.y || slice1!=slice2)
				name = null;
		}
		String label = name!=null?name:getLabel(imp, roi);
		if (promptForName)
			label = getName(label);
		else
			label = getUniqueName(label);
		if (label==null) return false;
		list.add(label);
		roi.setName(label);
		roiCopy = (Roi)roi.clone();
		Calibration cal = imp.getCalibration();
		if (cal.xOrigin!=0.0 || cal.yOrigin!=0.0) {
			Rectangle r = roiCopy.getBounds();
			roiCopy.setLocation(r.x-(int)cal.xOrigin, r.y-(int)cal.yOrigin);
		}
		slice2 = slice1;
		rois.put(label, roiCopy);
		if (Recorder.record) Recorder.record("roiManager", "Add");
		return true;
	}
	
	String getLabel(ImagePlus imp, Roi roi) {
		Rectangle r = roi.getBounds();
		int xc = r.x + r.width/2;
		int yc = r.y + r.height/2;
		if (xc<0) xc = 0;
		if (yc<0) yc = 0;
		int digits = 4;
		String xs = "" + xc;
		if (xs.length()>digits) digits = xs.length();
		String ys = "" + yc;
		if (ys.length()>digits) digits = ys.length();
		xs = "000" + xc;
		ys = "000" + yc;
		String label = ys.substring(ys.length()-digits) + "-" + xs.substring(xs.length()-digits);
		if (imp.getStackSize()>1) {
			String zs = "000" + imp.getCurrentSlice();
			label = zs.substring(zs.length()-digits) + "-" + label;
		}
		return label;
	}

	void addAndDraw(boolean altKeyDown) {
		if (altKeyDown) {
			if (!add(true)) return;
		} else if (!add(false))
			return;
		ImagePlus imp = WindowManager.getCurrentImage();
		Undo.setup(Undo.COMPOUND_FILTER, imp);
		IJ.run("Draw");
		Undo.setup(Undo.COMPOUND_FILTER_DONE, imp);
		if (Recorder.record) Recorder.record("roiManager", "Add & Draw");
	}
	
	boolean delete(boolean replacing) {
		int count = list.getItemCount();
		if (count==0)
			return error("The list is empty.");
		int index[] = list.getSelectedIndexes();
		if (index.length==0 || (replacing&&count>1)) {
			String msg = "Delete all items on the list?";
			if (replacing)
				msg = "Replace items on the list?";
			canceled = false;
			if (!IJ.macroRunning() && !macro) {
				YesNoCancelDialog d = new YesNoCancelDialog(this, "ROI Manager", msg);
				if (d.cancelPressed())
					{canceled = true; return false;}
				if (!d.yesPressed()) return false;
			}
			index = getAllIndexes();
		}
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
	
	boolean update() {
		ImagePlus imp = getImage();
		if (imp==null) return false;
		Roi roi = imp.getRoi();
		if (roi==null) {
			error("The active image does not have a selection.");
			return false;
		}
		int index = list.getSelectedIndex();
		if (index<0)
			return error("Exactly one item in the list must be selected.");
		String name = list.getItem(index);
		rois.remove(name);
		rois.put(name, roi);
		return true;
	}

	boolean rename() {
		int index = list.getSelectedIndex();
		if (index<0)
			return error("Exactly one item in the list must be selected.");
		String name = list.getItem(index);
		String name2 = getName(name);
		if (name2==null) return false;
		Roi roi = (Roi)rois.get(name);
		rois.remove(name);
		roi.setName(name2);
		rois.put(name2, roi);
		list.replaceItem(name2, index);
		list.select(index);
		return true;
	}
	
	String getName(String name) {
		GenericDialog gd = new GenericDialog("ROI Manager");
		gd.addStringField("Rename As:", name, 20);
		gd.showDialog();
		if (gd.wasCanceled())
			return null;
		String name2 = gd.getNextString();
		name2 = getUniqueName(name2);
		return name2;
	}

	boolean restore(int index, boolean setSlice) {
		String label = list.getItem(index);
		Roi roi = (Roi)rois.get(label);
		ImagePlus imp = getImage();
		if (imp==null || roi==null)
			return false;
        if (setSlice) {
            int slice = getSliceNumber(label);
            if (slice>=1 && slice<=imp.getStackSize())
                imp.setSlice(slice);
        }
        Roi roi2 = (Roi)roi.clone();
		Calibration cal = imp.getCalibration();
		if (cal.xOrigin!=0.0 || cal.yOrigin!=0.0) {
			Rectangle r = roi2.getBounds();
			roi2.setLocation(r.x+(int)cal.xOrigin, r.y+(int)cal.yOrigin);
		}
		imp.setRoi(roi2);
		return true;
	}
	
	int getSliceNumber(String label) {
		int slice = -1;
		if (label.length()>4 && label.charAt(4)=='-' && label.length()>=14)
			slice = (int)Tools.parseDouble(label.substring(0,4),-1);
		return slice;
	}
	
	void open(String path) {
		Macro.setOptions(null);
		String name = null;
		if (path==null) {
			OpenDialog od = new OpenDialog("Open Selection(s)...", "");
			String directory = od.getDirectory();
			name = od.getFileName();
			if (name==null)
				return;
			path = directory + name;
		}
		if (Recorder.record) Recorder.record("roiManager", "Open", path);
		if (path.endsWith(".zip")) {
			openZip(path);
			return;
		}
		Opener o = new Opener();
		if (name==null) name = o.getName(path);
		Roi roi = o.openRoi(path);
		if (roi!=null) {
			if (name.endsWith(".roi"))
				name = name.substring(0, name.length()-4);
			name = getUniqueName(name);
			list.add(name);
			rois.put(name, roi);
		}		
	}
	
	// Modified on 2005/11/15 by Ulrik Stervbo to only read .roi files and to not empty the current list
	void openZip(String path) { 
		ZipInputStream in = null; 
		ByteArrayOutputStream out; 
		int nRois = 0; 
		try { 
			in = new ZipInputStream(new FileInputStream(path)); 
			byte[] buf = new byte[1024]; 
			int len; 
			ZipEntry entry = in.getNextEntry(); 
			while (entry!=null) { 
				String name = entry.getName(); 
				if (name.endsWith(".roi")) { 
					out = new ByteArrayOutputStream(); 
					while ((len = in.read(buf)) > 0) 
						out.write(buf, 0, len); 
					out.close(); 
					byte[] bytes = out.toByteArray(); 
					RoiDecoder rd = new RoiDecoder(bytes, name); 
					Roi roi = rd.getRoi(); 
					if (roi!=null) { 
						name = name.substring(0, name.length()-4); 
						name = getUniqueName(name); 
						list.add(name); 
						rois.put(name, roi); 
						nRois++;
					} 
				} 
				entry = in.getNextEntry(); 
			} 
			in.close(); 
		} catch (IOException e) {error(e.toString());} 
		if(nRois==0)
				error("This ZIP archive does not appear to contain \".roi\" files");
	} 


	String getUniqueName(String name) {
			String name2 = name;
			int n = 1;
			Roi roi2 = (Roi)rois.get(name2);
			while (roi2!=null) {
				roi2 = (Roi)rois.get(name2);
				if (roi2!=null) {
					int lastDash = name2.lastIndexOf("-");
					if (lastDash!=-1 && name2.length()-lastDash<5)
						name2 = name2.substring(0, lastDash);
					name2 = name2+"-"+n;
					n++;
				}
				roi2 = (Roi)rois.get(name2);
			}
			return name2;
	}
	
	boolean save() {
		if (list.getItemCount()==0)
			return error("The selection list is empty.");
		int[] indexes = list.getSelectedIndexes();
		if (indexes.length==0)
			indexes = getAllIndexes();
		if (indexes.length>1)
			return saveMultiple(indexes, null);
		String name = list.getItem(indexes[0]);
		Macro.setOptions(null);
		SaveDialog sd = new SaveDialog("Save Selection...", name, ".roi");
		String name2 = sd.getFileName();
		if (name2 == null)
			return false;
		String dir = sd.getDirectory();
		Roi roi = (Roi)rois.get(name);
		rois.remove(name);
		if (!name2.endsWith(".roi")) name2 = name2+".roi";
		String newName = name2.substring(0, name2.length()-4);
		rois.put(newName, roi);
		roi.setName(newName);
		list.replaceItem(newName, indexes[0]);
		if (restore(indexes[0], true))
			IJ.run("Selection...", "path='"+dir+name2+"'");
		return true;
	}

	boolean saveMultiple(int[] indexes, String path) {
		Macro.setOptions(null);
		if (path==null) {
			SaveDialog sd = new SaveDialog("Save ROIs...", "RoiSet", ".zip");
			String name = sd.getFileName();
			if (name == null)
				return false;
			String dir = sd.getDirectory();
			path = dir+name;
		}
		try {
			ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path));
			DataOutputStream out = new DataOutputStream(new BufferedOutputStream(zos));
			RoiEncoder re = new RoiEncoder(out);
			for (int i=0; i<indexes.length; i++) {
				String label = list.getItem(indexes[i]);
				Roi roi = (Roi)rois.get(label);
				if (!label.endsWith(".roi")) label += ".roi";
        		zos.putNextEntry(new ZipEntry(label));
				re.write(roi);
				out.flush();
			}
			out.close();
		}
		catch (IOException e) {
			error(""+e);
			return false;
		}
		if (Recorder.record) Recorder.record("roiManager", "Save", path);
		return true;
	}
	
	/*
	boolean save() {
		if (list.getItemCount()==0)
			return error("The selection list is empty.");
		int[] indexes = list.getSelectedIndexes();
		if (indexes.length==0)
			indexes = getAllIndexes();
		String name = list.getItem(indexes[0]);
		Macro.setOptions(null);
		SaveDialog sd = new SaveDialog("Save Selection...", name, ".roi");
		String name2 = sd.getFileName();
		if (name2 == null)
			return false;
		String dir = sd.getDirectory();
		if (indexes.length==1) {
			Roi roi = (Roi)rois.get(name);
			rois.remove(name);
			if (!name2.endsWith(".roi")) name = name+".roi";
			String newName = name2.substring(0, name2.length()-4);
			rois.put(newName, roi);
			list.replaceItem(newName, indexes[0]);
			if (restore(indexes[0]))
				IJ.run("Selection...", "path='"+dir+name2+"'");
			return true;
		}
		for (int i=0; i<indexes.length; i++) {
			if (restore(indexes[i])) {
				name = list.getItem(indexes[i]);
				if (!name.endsWith(".roi"))
					name = name+".roi";
				//IJ.log("Selection...," + " path='"+dir+name+"'");
				IJ.run("Selection...", "path='"+dir+name+"'");
			} else
				break;
		}
		return true;
	}
	*/
	
	boolean measure() {
		ImagePlus imp = getImage();
		if (imp==null)
			return false;
		int[] indexes = list.getSelectedIndexes();
		if (indexes.length==0)
			indexes = getAllIndexes();
        if (indexes.length==0) return false;

		int nLines = 0;
		for (int i=0; i<indexes.length; i++) {
			String label = list.getItem(indexes[i]);
			Roi roi = (Roi)rois.get(label);
			if (roi.isLine()) nLines++;
		}
		if (nLines>0 && nLines!=indexes.length) {
			error("All items must be areas or all must be lines.");
			return false;
		}
						
		int nSlices = 1;
		String label = list.getItem(indexes[0]);
		if (getSliceNumber(label)==-1 || indexes.length==1) {
			int setup = IJ.setupDialog(imp, 0);
			if (setup==PlugInFilter.DONE)
				return false;
			nSlices = setup==PlugInFilter.DOES_STACKS?imp.getStackSize():1;
		}
		int currentSlice = imp.getCurrentSlice();
		for (int slice=1; slice<=nSlices; slice++) {
			if (nSlices>1) imp.setSlice(slice);
			for (int i=0; i<indexes.length; i++) {
				if (restore(indexes[i], nSlices==1))
					IJ.run("Measure");
				else
					break;
			}
		}
		imp.setSlice(currentSlice);
		if (indexes.length>1)
			IJ.run("Select None");
		if (Recorder.record) Recorder.record("roiManager", "Measure");
		return true;
	}	

	boolean draw() {
		int[] indexes = list.getSelectedIndexes();
		if (indexes.length==0)
			indexes = getAllIndexes();
		ImagePlus imp = WindowManager.getCurrentImage();
		Undo.setup(Undo.COMPOUND_FILTER, imp);
		for (int i=0; i<indexes.length; i++) {
			if (restore(indexes[i], true)) {
				IJ.run("Draw");
				IJ.run("Select None");
			} else
				break;
		}
		Undo.setup(Undo.COMPOUND_FILTER_DONE, imp);
		if (Recorder.record) Recorder.record("roiManager", "Draw");
		return true;
	}

	void combine() {
		ImagePlus imp = getImage();
		if (imp==null) return;
		int[] indexes = list.getSelectedIndexes();
		if (indexes.length<=1)
			indexes = getAllIndexes();
		ShapeRoi s1=null, s2=null;
		for (int i=0; i<indexes.length; i++) {
			Roi roi = (Roi)rois.get(list.getItem(indexes[i]));
			if (roi.isLine() || roi.getType()==Roi.POINT)
				continue;
			Calibration cal = imp.getCalibration();
			if (cal.xOrigin!=0.0 || cal.yOrigin!=0.0) {
				roi = (Roi)roi.clone();
				Rectangle r = roi.getBounds();
				roi.setLocation(r.x+(int)cal.xOrigin, r.y+(int)cal.yOrigin);
			}
			if (s1==null) {
				if (roi instanceof ShapeRoi)
					s1 = (ShapeRoi)roi;
				else
					s1 = new ShapeRoi(roi);
				if (s1==null) return;
			} else {
				if (roi instanceof ShapeRoi)
					s2 = (ShapeRoi)roi;
				else
					s2 = new ShapeRoi(roi);
				if (s2==null) continue;
				if (roi.isArea())
					s1.or(s2);
			}
		}
		if (s1!=null)
			imp.setRoi(s1);
		if (Recorder.record) Recorder.record("roiManager", "Combine");
	}

	/*
	void split() {
		ImagePlus imp = getImage();
		if (imp==null) return;
		Roi roi = imp.getRoi();
		if (roi==null || roi.getType()!=Roi.COMPOSITE) {
			error("Image with composite selection required");
			return;
		}
		Roi[] rois = ((ShapeRoi)roi).getRois();
		if (rois.length<2) {
			error("Enable to decompose this composite ROI into two or more simple ROIs.");
			return;
		}
		//IJ.log("split: "+list.getItemCount());
		if (list.getItemCount()>0) {
			if (!delete(true)) {
				//IJ.log("delete: false");
				return;
			}
			//IJ.log("delete: true");
		}
		for (int i=0; i<rois.length; i++) {
			imp.setRoi(rois[i]);
			add();
		}
		if (Recorder.record) Recorder.record("roiManager", "Split");
	}
	*/

	int[] getAllIndexes() {
		int count = list.getItemCount();
		int[] indexes = new int[count];
		for (int i=0; i<count; i++)
			indexes[i] = i;
		return indexes;
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
		Macro.abort();
		return false;
	}
	
	public void processWindowEvent(WindowEvent e) {
		super.processWindowEvent(e);
		if (e.getID()==WindowEvent.WINDOW_CLOSING) {
			instance = null;	
		}
		ignoreInterrupts = false;
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
		
	/** Executes the ROI Manager "Add", "Add & Draw", "Delete", "Measure", "Draw",
		"Combine" command. Returns false if <code>cmd</code> is not one of these strings. */
	public boolean runCommand(String cmd) {
		cmd = cmd.toLowerCase();
		macro = true;
		boolean ok = true;
		if (cmd.equals("add"))
			add(IJ.shiftKeyDown(), IJ.altKeyDown());
		else if (cmd.equals("add & draw"))
			addAndDraw(false);
		else if (cmd.equals("delete"))
			delete(false);
		else if (cmd.equals("measure"))
			measure();
		else if (cmd.equals("draw"))
			draw();
		else if (cmd.equals("combine"))
			combine();
		else if (cmd.equals("deselect")) {
			if (IJ.isMacOSX()) ignoreInterrupts = true;
			select(-1);
		} else if (cmd.equals("reset"))
			list.removeAll();
		else
			ok = false;
		macro = false;
		return ok;
	}

	/** Executes the ROI Manager "Open" or "Save" command. Returns false if 
	<code>cmd</code> is not "Open" or "Save" or if an error occurs. */
	public boolean runCommand(String cmd, String path) {
		cmd = cmd.toLowerCase();
		macro = true;
		if (cmd.equals("open")) {
			open(path);
			macro = false;
			return true;
		} else if (cmd.equals("save")) {
			if (!path.endsWith(".zip"))
				return error("Path must end with '.zip'");
			if (list.getItemCount()==0)
				return error("The selection list is empty.");
			int[] indexes = getAllIndexes();
			boolean ok = saveMultiple(indexes, path);
			macro = false;
			return ok;
		}
		return false;
	}
	
	public void select(int index) {
		int n = list.getItemCount();
		if (index<0) {
			for (int i=0; i<n; i++)
				if (list.isSelected(i)) list.deselect(i);
			return;
		}
		boolean mm = list.isMultipleMode();
		if (mm) list.setMultipleMode(false);
		if (index<n) {
			list.select(index);
			restore(index, true);	
			if (!Interpreter.isBatchMode())
				IJ.wait(10);
		}
		if (mm) list.setMultipleMode(true);
	}
	
	/*
	void selectAll() {
		boolean allSelected = true;
		int count = list.getItemCount();
		for (int i=0; i<count; i++) {
			if (!list.isIndexSelected(i))
				allSelected = false;
		}
		if (allSelected)
			select(-1);
		else {
			for (int i=0; i<count; i++)
				if (!list.isSelected(i)) list.select(i);
		}
	}
	*/

    /** Overrides PlugInFrame.close(). */
    public void close() {
    	super.close();
    	instance = null;
    }
}

