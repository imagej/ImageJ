package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.awt.List;
import java.util.zip.*;
import java.awt.geom.*;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.filter.*;
import ij.plugin.Colors;
import ij.plugin.OverlayLabels;
import ij.util.*;
import ij.macro.*;
import ij.measure.*;
import ij.plugin.OverlayCommands;

/** This plugin implements the Analyze/Tools/ROI Manager command. */
public class RoiManager extends PlugInFrame implements ActionListener, ItemListener, MouseListener, MouseWheelListener, ListSelectionListener {
	public static final String LOC_KEY = "manager.loc";
	private static final int BUTTONS = 11;
	private static final int DRAW=0, FILL=1, LABEL=2;
	private static final int SHOW_ALL=0, SHOW_NONE=1, LABELS=2, NO_LABELS=3;
	private static final int MENU=0, COMMAND=1;
	private static final int IGNORE_POSITION=-999;
	private static final int CHANNEL=0, SLICE=1, FRAME=2, SHOW_DIALOG=3;
	private static int rows = 15;
	private static int lastNonShiftClick = -1;
	private static boolean allowMultipleSelections = true; 
	private static String moreButtonLabel = "More "+'\u00bb';
	private Panel panel;
	private static Frame instance;
	private static int colorIndex = 4;
	private JList list;
	private DefaultListModel listModel;
	private ArrayList rois = new ArrayList();
	private boolean canceled;
	private boolean macro;
	private boolean ignoreInterrupts;
	private PopupMenu pm;
	private Button moreButton, colorButton;
	private Checkbox showAllCheckbox = new Checkbox("Show All", false);
	private Checkbox labelsCheckbox = new Checkbox("Labels", false);
	private Overlay overlayTemplate;

	private static boolean measureAll = true;
	private static boolean onePerSlice = true;
	private static boolean restoreCentered;
	private int prevID;
	private boolean noUpdateMode;
	private int defaultLineWidth = 1;
	private Color defaultColor;
	private boolean firstTime = true;
	private int[] selectedIndexes;
	private boolean appendResults;
	private static ResultsTable mmResults, mmResults2;
	private int imageID;
	private boolean allowRecording;
	private boolean recordShowAll = true;
	private boolean allowDuplicates;
	private double translateX = 10.0;
	private double translateY = 10.0;

		
	/** Opens the "ROI Manager" window, or activates it if it is already open.
	 * @see #RoiManager(boolean)
	 * @see #getRoiManager
	*/
	public RoiManager() {
		super("ROI Manager");
		if (instance!=null) {
			WindowManager.toFront(instance);
			return;
		}
		if (IJ.isMacro() && Interpreter.getBatchModeRoiManager()!=null) {
			list = new JList();
			listModel = new DefaultListModel();
			list.setModel(listModel);
			return;
		}
		instance = this;
		list = new JList();
		showWindow();
	}
	
	/** Constructs an ROIManager without displaying it. The boolean argument is ignored. */
	public RoiManager(boolean b) {
		super("ROI Manager");
		list = new JList();
		listModel = new DefaultListModel();
		list.setModel(listModel);
	}

	void showWindow() {
		ImageJ ij = IJ.getInstance();
		addKeyListener(ij);
		addMouseListener(this);
		addMouseWheelListener(this);
		WindowManager.addWindow(this);
		//setLayout(new FlowLayout(FlowLayout.CENTER,5,5));
		setLayout(new BorderLayout());
		listModel = new DefaultListModel();
		list.setModel(listModel);
		GUI.scale(list);
		list.setPrototypeCellValue("0000-0000-0000 ");			
		list.addListSelectionListener(this);
		list.addKeyListener(ij);
		list.addMouseListener(this);
		list.addMouseWheelListener(this);
		if (IJ.isLinux()) list.setBackground(Color.white);
		JScrollPane scrollPane = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add("Center", scrollPane);
		panel = new Panel();
		int nButtons = BUTTONS;
		panel.setLayout(new GridLayout(nButtons, 1, 5, 0));
		addButton("Add [t]");
		addButton("Update");
		addButton("Delete");
		addButton("Rename...");
		addButton("Measure");
		addButton("Deselect");
		addButton("Properties...");
		addButton("Flatten [F]");
		addButton(moreButtonLabel);
		showAllCheckbox.addItemListener(this);
		panel.add(showAllCheckbox);
		labelsCheckbox.addItemListener(this);
		panel.add(labelsCheckbox);
		add("East", panel);		
		addPopupMenu();
		GUI.scale(this);
		pack();
		Dimension size = getSize();
		if (size.width>270)
			setSize(size.width-40, size.height);
		list.remove(0);
		Point loc = Prefs.getLocation(LOC_KEY);
		if (loc!=null)
			setLocation(loc);
		else
			GUI.center(this);
		show();
	}

	void addButton(String label) {
		Button b = new Button(label);
		b.addActionListener(this);
		b.addKeyListener(IJ.getInstance());
		b.addMouseListener(this);
		if (label.equals(moreButtonLabel)) moreButton = b;
		panel.add(b);
	}

	void addPopupMenu() {
		pm=new PopupMenu();
		GUI.scalePopupMenu(pm);
		addPopupItem("Open...");
		addPopupItem("Save...");
		addPopupItem("Fill");
		addPopupItem("Draw");
		addPopupItem("AND");
		addPopupItem("OR (Combine)");
		addPopupItem("XOR");
		addPopupItem("Split");
		addPopupItem("Add Particles");
		addPopupItem("Multi Measure");
		addPopupItem("Multi Plot");
		addPopupItem("Sort");
		addPopupItem("Specify...");
		addPopupItem("Remove Positions...");
		addPopupItem("Labels...");
		addPopupItem("List");
		addPopupItem("Interpolate ROIs");
		addPopupItem("Translate...");
		addPopupItem("Help");
		addPopupItem("Options...");
		add(pm);
	}

	void addPopupItem(String s) {
		MenuItem mi=new MenuItem(s);
		mi.addActionListener(this);
		pm.add(mi);
	}
	
	public void actionPerformed(ActionEvent e) {
		String label = e.getActionCommand();
		if (label==null)
			return;
		String command = label;
		allowRecording = true;
		if (command.equals("Add [t]"))
			runCommand("add");
		else if (command.equals("Update"))
			update(true);
		else if (command.equals("Delete"))
			delete(false);
		else if (command.equals("Rename..."))
			rename(null);
		else if (command.equals("Properties..."))
			setProperties(null, -1, null);
		else if (command.equals("Flatten [F]"))
			flatten();
		else if (command.equals("Measure"))
			measure(MENU);
		else if (command.equals("Open..."))
			open(null);
		else if (command.equals("Save...")) {
			Thread t1 = new Thread(new Runnable() {
				public void run() {save();}
			});  
			t1.start();
		} else if (command.equals("Fill"))
			drawOrFill(FILL);
		else if (command.equals("Draw"))
			drawOrFill(DRAW);
		else if (command.equals("Deselect"))
			deselect();
		else if (command.equals(moreButtonLabel)) {
			Point ploc = panel.getLocation();
			Point bloc = moreButton.getLocation();
			pm.show(this, ploc.x, bloc.y);
		} else if (command.equals("OR (Combine)")) {
			new MacroRunner("roiManager(\"Combine\");");
			if (Recorder.record) Recorder.record("roiManager", "Combine");
		} else if (command.equals("Split"))
			split();
		else if (command.equals("AND"))
			and();
		else if (command.equals("XOR"))
			xor();
		else if (command.equals("Add Particles"))
			addParticles();
		else if (command.equals("Multi Measure"))
			multiMeasure("");
		else if (command.equals("Multi Plot"))
			multiPlot();
		else if (command.equals("Sort"))
			sort();
		else if (command.equals("Specify..."))
			specify();
		else if (command.equals("Remove Positions..."))
			removePositions(SHOW_DIALOG);
		else if (command.equals("Labels..."))
			labels();
		else if (command.equals("List"))
			listRois();
		else if (command.equals("Interpolate ROIs"))
			interpolateRois();
		else if (command.equals("Translate..."))
			translate();
		else if (command.equals("Help"))
			help();
		else if (command.equals("Options..."))
			options();
		else if (command.equals("\"Show All\" Color..."))
			setShowAllColor();
		allowRecording = false;
	}
	
	private void interpolateRois() {
		IJ.runPlugIn("ij.plugin.RoiInterpolator", "");
		if (record())
			Recorder.record("roiManager", "Interpolate ROIs");
	}

	public void itemStateChanged(ItemEvent e) {
		Object source = e.getSource();
		boolean showAllMode = showAllCheckbox.getState();
		if (source==showAllCheckbox) {
			if (firstTime && okToSet())
				labelsCheckbox.setState(true);
			showAll(showAllCheckbox.getState()?SHOW_ALL:SHOW_NONE);
			if (Recorder.record && recordShowAll) {
				if (showAllMode)
						Recorder.record("roiManager", "Show All");
					else
						Recorder.record("roiManager", "Show None");
			}
			recordShowAll = true;
			firstTime = false;
			return;
		}
		if (source==labelsCheckbox) {
			if (firstTime && okToSet())
				showAllCheckbox.setState(true);
			boolean editState = labelsCheckbox.getState();
			boolean showAllState = showAllCheckbox.getState();
			if (!showAllState && !editState)
				showAll(SHOW_NONE);
			else {
				showAll(editState?LABELS:NO_LABELS);
				if (Recorder.record) {
					if (editState)
						Recorder.record("roiManager", "Show All with labels");
					else if (showAllState)
						Recorder.record("roiManager", "Show All without labels");
				}
				if (editState && !showAllState && okToSet()) {
					showAllCheckbox.setState(true);
					recordShowAll = false;
				}
			}
			firstTime = false;
			return;
		}
	}
	
	private boolean okToSet() {
		return !(IJ.isMacOSX()&&IJ.isJava18());
	}
	
	void add(boolean shiftKeyDown, boolean altKeyDown) {
		if (shiftKeyDown)
			addAndDraw(altKeyDown);
		else if (altKeyDown)
			addRoi(true);
		else
			addRoi(false);
	}
	
	/** Adds the specified ROI. */
	public void addRoi(Roi roi) {
		allowDuplicates = true;
		addRoi(roi, false, null, -1);
	}
	
	boolean addRoi(boolean promptForName) {
		return addRoi(null, promptForName, null, IGNORE_POSITION);
	}

	boolean addRoi(Roi roi, boolean promptForName, Color color, int lineWidth) {
		if (listModel==null)
			IJ.log("<<Error: Uninitialized RoiManager>>");
		ImagePlus imp = roi==null?getImage():WindowManager.getCurrentImage();
		if (roi==null) {
			if (imp==null)
				return false;
			roi = imp.getRoi();
			if (roi==null) {
				error("The active image does not have a selection.");
				return false;
			}
		}
		if ((roi instanceof PolygonRoi) && ((PolygonRoi)roi).getNCoordinates()==0)
			return false;
		if (color==null && roi.getStrokeColor()!=null)
			color = roi.getStrokeColor();
		else if (color==null && defaultColor!=null)
			color = defaultColor;
		boolean ignorePosition = false;
		if (lineWidth==IGNORE_POSITION) {
			ignorePosition = true;
			lineWidth = -1;
		}
		if (lineWidth<0) {
			int sw = (int)roi.getStrokeWidth();
			lineWidth = sw>1?sw:defaultLineWidth;
		}
		if (lineWidth>100) lineWidth = 1;
		int n = getCount();
		int position = imp!=null&&!ignorePosition?roi.getPosition():0;
		int saveCurrentSlice = imp!=null?imp.getCurrentSlice():0;
		if (position>0 && position!=saveCurrentSlice)
			imp.setSliceWithoutUpdate(position);
		else
			position = 0;
		if (n>0 && !IJ.isMacro() && imp!=null && !allowDuplicates) {
			// check for duplicate
			Roi roi2 = (Roi)rois.get(n-1);
			if (roi2!=null) {
				String label = (String)listModel.getElementAt(n-1);
				int slice2 = getSliceNumber(roi2, label);
				if (roi.equals(roi2) && (slice2==-1||slice2==imp.getCurrentSlice()) && imp.getID()==prevID && !Interpreter.isBatchMode()) {
					if (position>0)
						imp.setSliceWithoutUpdate(saveCurrentSlice);
					return false;
				}
			}
		}
		allowDuplicates = false;
		prevID = imp!=null?imp.getID():0;
		String name = roi.getName();
		if (isStandardName(name))
			name = null;
		String label = name!=null?name:getLabel(imp, roi, -1);
		if (promptForName)
			label = promptForName(label);
		if (label==null) {
			if (position>0)
				imp.setSliceWithoutUpdate(saveCurrentSlice);
			return false;
		}
		listModel.addElement(label);
		roi.setName(label);
		Roi roiCopy = (Roi)roi.clone();
		roiCopy.setPosition(imp);
		if (lineWidth>1)
			roiCopy.setStrokeWidth(lineWidth);
		if (color!=null)
			roiCopy.setStrokeColor(color);
		rois.add(roiCopy);
		updateShowAll();
		if (record())
			recordAdd(defaultColor, defaultLineWidth);
		if (position>0)
			imp.setSliceWithoutUpdate(saveCurrentSlice);
		return true;
	}
		
	void recordAdd(Color color, int lineWidth) {
		if (Recorder.scriptMode())
			Recorder.recordCall("rm.addRoi(imp.getRoi());");
		else if (color!=null && lineWidth==1)
			Recorder.recordString("roiManager(\"Add\", \""+getHex(color)+"\");\n");
		else if (lineWidth>1)
			Recorder.recordString("roiManager(\"Add\", \""+getHex(color)+"\", "+lineWidth+");\n");
		else
			Recorder.record("roiManager", "Add");
	}
	
	String getHex(Color color) {
		if (color==null) color = ImageCanvas.getShowAllColor();
		String hex = Integer.toHexString(color.getRGB());
		if (hex.length()==8) hex = hex.substring(2);
		return hex;
	}
	
	/** Adds the specified ROI to the list. The second argument ('n') will 
	 * be used to form the first part of the ROI label if it is zero or greater.
	 * @param roi		the Roi to be added
	 * @param n		if zero or greater, will be used to form the first part of the label
	*/
	public void add(Roi roi, int n) {
		add((ImagePlus)null, roi, n);
	}

	/** Adds the specified ROI to the list. The third argument ('n') will 
	 * be used to form the first part of the ROI label if it is zero or greater.
	 * @param imp	the image associated with the ROI, or null
	 * @param roi		the Roi to be added
	 * @param n		if zero or greater, will be used to form the first part of the label
	*/
	public void add(ImagePlus imp, Roi roi, int n) {
		if (IJ.debugMode && n<3 && roi!=null) IJ.log("RoiManager.add: "+n+" "+roi.getName());
		if (roi==null)
			return;
		String label = roi.getName();
		String label2 = label;
		if (label==null)
			label = getLabel(imp, roi, n);
		else {
			if (n>=0)
				label = n+"-"+label;
		}
		if (label==null)
			return;
		listModel.addElement(label);
		if (label2!=null)
			roi.setName(label2);
		else
			roi.setName(label);
		rois.add((Roi)roi.clone());
	}
	
	/** Replaces the ROI at the specified index. */
	public void setRoi(Roi roi, int index) {
    	if (index<0 || index>=rois.size())
    		throw new IllegalArgumentException("setRoi: Index out of range");
		rois.set(index, (Roi)roi.clone());
		updateShowAll();
	}

	boolean isStandardName(String name) {
		if (name==null)
			return false;
		int len = name.length();
		if (len<9 || (len>0&&!Character.isDigit(name.charAt(0))))
			return false;
		boolean isStandard = false;
		if (len>=14 && name.charAt(4)=='-' && name.charAt(9)=='-' )
			isStandard = true;
		else if (len>=17 && name.charAt(5)=='-' && name.charAt(11)=='-' )
			isStandard = true;
		else if (len>=9 && name.charAt(4)=='-')
			isStandard = true;
		else if (len>=11 && name.charAt(5)=='-')
			isStandard = true;
		return isStandard;
	}
	
	String getLabel(ImagePlus imp, Roi roi, int n) {
		Rectangle r = roi.getBounds();
		int xc = r.x + r.width/2;
		int yc = r.y + r.height/2;
		if (n>=0)
			{xc = yc; yc=n;}
		if (xc<0) xc = 0;
		if (yc<0) yc = 0;
		int digits = 4;
		String xs = "" + xc;
		if (xs.length()>digits) digits = xs.length();
		String ys = "" + yc;
		if (ys.length()>digits) digits = ys.length();
		if (digits==4 && imp!=null && (imp.getStackSize()>=10000||imp.getHeight()>=10000))
			digits = 5;
		xs = "000000" + xc;
		ys = "000000" + yc;
		String label = ys.substring(ys.length()-digits) + "-" + xs.substring(xs.length()-digits);
		if (imp!=null && imp.getStackSize()>1) {
			int slice = imp.getCurrentSlice();
			String zs = "000000" + slice;
			label = zs.substring(zs.length()-digits) + "-" + label;
		}
		return label;
	}

	void addAndDraw(boolean altKeyDown) {
		if (altKeyDown) {
			if (!addRoi(true)) return;
		} else if (!addRoi(false))
			return;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			Undo.setup(Undo.COMPOUND_FILTER, imp);
			IJ.run(imp, "Draw", "slice");
			Undo.setup(Undo.COMPOUND_FILTER_DONE, imp);
		}
		if (record()) Recorder.record("roiManager", "Add & Draw");
	}
	
	boolean delete(boolean replacing) {
		int count = getCount();
		if (count==0)
			return error("The ROI Manager is empty.");
		int index[] = getSelectedIndexes();
		if (index.length==0 || (replacing&&count>1)) {
			String msg = "Delete all items on the list?";
			if (replacing)
				msg = "Replace items on the list?";
			canceled = false;
			if (!IJ.isMacro() && !macro) {
				YesNoCancelDialog d = new YesNoCancelDialog(this, "ROI Manager", msg);
				if (d.cancelPressed())
					{canceled = true; return false;}
				if (!d.yesPressed()) return false;
			}
			index = getAllIndexes();
		}
		if (count==index.length && !replacing) {
			rois.clear();
			listModel.removeAllElements();
		} else {
			for (int i=count-1; i>=0; i--) {
				boolean delete = false;
				for (int j=0; j<index.length; j++) {
					if (index[j]==i)
						delete = true;
				}
				if (delete) {
					if (EventQueue.isDispatchThread()) {
 						rois.remove(i);
						listModel.remove(i);
 					} else 
 						deleteOnEDT(i);
				} 
			}
		}
		ImagePlus imp = WindowManager.getCurrentImage();
		if (count>1 && index.length==1 && imp!=null)
			imp.deleteRoi();
		updateShowAll();
		if (record())
			Recorder.record("roiManager", "Delete");
		return true;
	}
	
	 // Delete ROI on event dispatch thread
	 private void deleteOnEDT(final int i) {
		try {
			EventQueue.invokeAndWait(new Runnable() {
				public void run() {
					rois.remove(i);
					listModel.remove(i);
				}
			});
		} catch (
			Exception e) {
		}
	}
	
	boolean update(boolean clone) {
		ImagePlus imp = getImage();
		if (imp==null)
			return false;
		ImageCanvas ic = imp.getCanvas();
		boolean showingAll = ic!=null &&  ic.getShowAllROIs();
		Roi roi = imp.getRoi();
		if (roi==null) {
			error("The active image does not have a selection.");
			return false;
		}
		int index = list.getSelectedIndex();
		if (index<0 && !showingAll)
			return error("Exactly one item in the list must be selected.");
		if (index>=0) {
			if (clone) {
				String name = (String)listModel.getElementAt(index);
				Roi roi2 = (Roi)roi.clone();
				roi2.setPosition(imp);
				roi.setName(name);
				roi2.setName(name);
				rois.set(index, roi2);
			} else
				rois.set(index, roi);
		}
		if (record()) Recorder.record("roiManager", "Update");
		updateShowAll();
		return true;
	}

	boolean rename(String name2) {
		int index = list.getSelectedIndex();
		if (index<0)
			return error("Exactly one item in the list must be selected.");
		String name = (String)listModel.getElementAt(index);
		if (name2==null)
			name2 = promptForName(name);
		if (name2==null)
			return false;
		if (name2.equals(name))
			return false;
		Roi roi = (Roi)rois.get(index);
		roi.setName(name2);
		int position = getSliceNumber(name2);
		if (position>0 && !roi.hasHyperStackPosition())
			roi.setPosition(position);
		rois.set(index, roi);
		listModel.setElementAt(name2, index);
		list.setSelectedIndex(index);
		if (Prefs.useNamesAsLabels && labelsCheckbox.getState()) {
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) imp.draw();
		}
		if (record())
			Recorder.record("roiManager", "Rename", name2);
		return true;
	}
	
	public void rename(int index, String newName) {
		if (index<0 || index>=getCount())
			throw new IllegalArgumentException("Index out of range: "+index);
		Roi roi = (Roi)rois.get(index);
		roi.setName(newName);
		listModel.setElementAt(newName, index);
	}

	String promptForName(String name) {
		GenericDialog gd = new GenericDialog("ROI Manager");
		gd.addStringField("Rename As:", name, 20);
		gd.showDialog();
		if (gd.wasCanceled())
			return null;
		else
			return gd.getNextString();
	}

	boolean restore(ImagePlus imp, int index, boolean setSlice) {
		Roi roi = (Roi)rois.get(index);
		if (imp==null || roi==null)
			return false;
		if (setSlice) {
			boolean hyperstack = imp.isHyperStack();
			if (hyperstack && roi.hasHyperStackPosition())
				imp.setPosition(roi.getCPosition(), roi.getZPosition(), roi.getTPosition());
			else {
				String label = (String)listModel.getElementAt(index);
				int n = getSliceNumber(roi, label);
				if (n>=1 && n<=imp.getStackSize()) {
					if (hyperstack) {
						if (imp.getNSlices()>1 && n<=imp.getNSlices())
							imp.setPosition(imp.getC(),n,imp.getT());
						else if (imp.getNFrames()>1 && n<=imp.getNFrames())
							imp.setPosition(imp.getC(),imp.getZ(),n);
						else
							imp.setPosition(n);
					} else
						imp.setSlice(n);
				}
			}
		}
		if (showAllCheckbox.getState() && !restoreCentered && !noUpdateMode) {
			roi.setImage(null);
			imp.setRoi(roi);
			return true;
		}
		Roi roi2 = (Roi)roi.clone();
		Rectangle r = roi2.getBounds();
		int width= imp.getWidth(), height=imp.getHeight();
		if (restoreCentered) {
			ImageCanvas ic = imp.getCanvas();
			if (ic!=null) {
				Rectangle r1 = ic.getSrcRect();
				Rectangle r2 = roi2.getBounds();
				roi2.setLocation(r1.x+r1.width/2-r2.width/2, r1.y+r1.height/2-r2.height/2);
			}
		}
		if (r.x>=width || r.y>=height || (r.x+r.width)<0 || (r.y+r.height)<0) {
			if (roi2.getType()!=Roi.POINT)
				roi2.setLocation((width-r.width)/2, (height-r.height)/2);
		}
		if (noUpdateMode) {
			imp.setRoi(roi2, false);
			noUpdateMode = false;
		} else
			imp.setRoi(roi2, true);
		return true;
	}
	
	private boolean restoreWithoutUpdate(ImagePlus imp, int index) {
		noUpdateMode = true;
		if (imp==null)
			imp = getImage();
		return restore(imp, index, false);
	}
	
	/** Returns the slice number associated with the specified name,
		or -1 if the name does not include a slice number. */
	public int getSliceNumber(String label) {
		int slice = -1;
		if (label.length()>=14 && label.charAt(4)=='-' && label.charAt(9)=='-')
			slice = (int)Tools.parseDouble(label.substring(0,4),-1);
		else if (label.length()>=17 && label.charAt(5)=='-' && label.charAt(11)=='-')
			slice = (int)Tools.parseDouble(label.substring(0,5),-1);
		else if (label.length()>=20 && label.charAt(6)=='-' && label.charAt(13)=='-')
			slice = (int)Tools.parseDouble(label.substring(0,6),-1);
		return slice;
	}
	
	/** Returns the slice number associated with the specified ROI or name,
		or -1 if the ROI or name does not include a slice number. */
	int getSliceNumber(Roi roi, String label) {
		int slice = roi!=null?roi.getPosition():-1;
		if (slice==0)
			slice=-1;
		if (slice==-1)
			slice = getSliceNumber(label);
		return slice;
	}

	void open(String path) {
		Macro.setOptions(null);
		String name = null;
		if (path==null || path.equals("")) {
			OpenDialog od = new OpenDialog("Open Selection(s)...", "");
			String directory = od.getDirectory();
			name = od.getFileName();
			if (name==null)
				return;
			path = directory + name;
		}
		if (Recorder.record && !Recorder.scriptMode())
			Recorder.record("roiManager", "Open", path);
		if (path.endsWith(".zip")) {
			boolean wasRecording = Recorder.record;
			Recorder.record = false;
			openZip(path);
			Recorder.record = wasRecording;
			return;
		}
		Opener o = new Opener();
		if (name==null) name = o.getName(path);
		Roi roi = o.openRoi(path);
		if (roi!=null) {
			if (roi.getName()!=null)
				name = roi.getName();
			if (name.endsWith(".roi"))
				name = name.substring(0, name.length()-4);
			listModel.addElement(name);
			rois.add(roi);
		}		
		updateShowAll();
	}
	
	// Modified on 2005/11/15 by Ulrik Stervbo to only read .roi files and to not empty the current list
	void openZip(String path) { 
		ZipInputStream in = null; 
		ByteArrayOutputStream out = null; 
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
						listModel.addElement(name); 
						rois.add(roi); 
						nRois++;
					} 
				} 
				entry = in.getNextEntry(); 
			} 
			in.close(); 
		} catch (IOException e) {
			error(e.toString());
		} finally {
			if (in!=null)
				try {in.close();} catch (IOException e) {}
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		if(nRois==0)
				error("This ZIP archive does not appear to contain \".roi\" files");
		updateShowAll();
	} 

	boolean save() {
		if (getCount()==0)
			return error("The selection list is empty.");
		int[] indexes = getIndexes();
		if (indexes.length>1)
			return saveMultiple(indexes, null);
		else
			return saveOne(indexes, null);
	}
	
	boolean saveOne(int[] indexes, String path) {
		if (indexes.length==0)
			return error("The list is empty");
		Roi roi = (Roi)rois.get(indexes[0]);
		if (path==null) {
			Macro.setOptions(null);
			String name = (String) listModel.getElementAt(indexes[0]);
			SaveDialog sd = new SaveDialog("Save Selection...", name, ".roi");
			String name2 = sd.getFileName();
			if (name2 == null)
				return false;
			String dir = sd.getDirectory();
			if (!name2.endsWith(".roi")) name2 = name2+".roi";
			String newName = name2.substring(0, name2.length()-4);
			rois.set(indexes[0], roi);
			roi.setName(newName);
			listModel.setElementAt(newName, indexes[0]);
			path = dir+name2;
		}
		RoiEncoder re = new RoiEncoder(path);
		try {
			re.write(roi);
		} catch (IOException e) {
			IJ.error("ROI Manager", e.getMessage());
		}
		if (Recorder.record && !IJ.isMacro())
			Recorder.record("roiManager", "Save", path);
		return true;
	}

	boolean saveMultiple(int[] indexes, String path) {
		Macro.setOptions(null);
		if (path==null) {
			SaveDialog sd = new SaveDialog("Save ROIs...", "RoiSet", ".zip");
			String name = sd.getFileName();
			if (name == null)
				return false;
			if (!(name.endsWith(".zip") || name.endsWith(".ZIP")))
				name = name + ".zip";
			String dir = sd.getDirectory();
			path = dir+name;
		}
		DataOutputStream out = null;
		IJ.showStatus("Saving "+indexes.length+" ROIs "+" to "+path);
		long t0 = System.currentTimeMillis();
		String[] names = new String[listModel.size()];
		for (int i=0; i<listModel.size(); i++)
			names[i] = (String)listModel.getElementAt(i);
		try {
			ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
			out = new DataOutputStream(new BufferedOutputStream(zos));
			RoiEncoder re = new RoiEncoder(out);
			for (int i=0; i<indexes.length; i++) {
				IJ.showProgress(i, indexes.length);
				String label = getUniqueName(names, indexes[i]);
				Roi roi = (Roi)rois.get(indexes[i]);
				if (IJ.debugMode) IJ.log("saveMultiple: "+i+"  "+label+"  "+roi);
				if (roi==null) continue;
				if (!label.endsWith(".roi")) label += ".roi";
				zos.putNextEntry(new ZipEntry(label));
				re.write(roi);
				out.flush();
			}
			out.close();
		} catch (IOException e) {
			error(""+e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		double time = (System.currentTimeMillis()-t0)/1000.0;
		IJ.showProgress(1.0);
		IJ.showStatus(IJ.d2s(time,3)+" seconds, "+indexes.length+" ROIs, "+path);
		if (Recorder.record && !IJ.isMacro())
			Recorder.record("roiManager", "Save", path);
		return true;
	}
	
	String getUniqueName(String[] names, int index) {
		String name = names[index];
		int n = 1;
		int index2 = getIndex(names, index, name);
		while (index2!=-1) {
			index2 = getIndex(names, index, name);
			if (index2!=-1) {
				int lastDash = name.lastIndexOf("-");
				if (lastDash!=-1 && name.length()-lastDash<5)
					name = name.substring(0, lastDash);
				name = name+"-"+n;
				n++;
			}
			index2 = getIndex(names, index, name);
		}
		names[index] = name;
		return name;
	}
    
	private int getIndex(String[] names, int index, String name) {
		int index2 = -1;
		for (int i=0; i<names.length; i++) {
			if (i!=index && names[i].equals(name))
			return i;
		}
		return index2;
	}

	private void listRois() {
		Roi[] list = getRoisAsArray();
		OverlayCommands.listRois(list);
		if (record())
			Recorder.record("roiManager", "List");
	}
		
	boolean measure(int mode) {
		ImagePlus imp = getImage();
		if (imp==null)
			return false;
		int[] indexes = getIndexes();
		if (indexes.length==0) return false;
		boolean allSliceOne = true;
		for (int i=0; i<indexes.length; i++) {
			Roi roi = (Roi)rois.get(indexes[i]);
			String label = (String) listModel.getElementAt(indexes[i]);
			if (getSliceNumber(roi,label)>1 || roi.hasHyperStackPosition())
				allSliceOne=false;
		}
		int measurements = Analyzer.getMeasurements();
		if (imp.getStackSize()>1)
			Analyzer.setMeasurements(measurements|Measurements.SLICE);
		int currentSlice = imp.getCurrentSlice();
		Analyzer.setMeasurements(measurements&(~Measurements.ADD_TO_OVERLAY));
		for (int i=0; i<indexes.length; i++) {
			if (restore(getImage(), indexes[i], !allSliceOne))
				IJ.run("Measure");
			else
				break;
		}
		Analyzer.setMeasurements(measurements);
		imp.setSlice(currentSlice);
		if (indexes.length>1)
			IJ.run("Select None");
		if (record()) Recorder.record("roiManager", "Measure");
		return true;
	}	
	
	/** This method measures the selected ROIs, or all ROIs if
	 * none are selected, on all the slices of a stack and returns
	 * a ResultsTable arranged with one row per slice.
	 * @see <a href="http://imagej.nih.gov/ij/macros/js/MultiMeasureDemo.js">JavaScript example</a>
	*/
	public ResultsTable multiMeasure(ImagePlus imp) {
		Roi[] rois = getSelectedRoisAsArray();
		ResultsTable rt = multiMeasure(imp, rois, false);
		imp.deleteRoi();
		return rt;
	}
	
	/** This method performs measurements for several ROI's in a stack
		and arranges the results with one line per slice.  By contrast, the 
		measure() method produces several lines per slice.	The results 
		from multiMeasure() may be easier to import into a spreadsheet 
		program for plotting or additional analysis. Based on the multi() 
		method in Bob Dougherty's Multi_Measure plugin
		(http://www.optinav.com/Multi-Measure.htm).
	*/
	boolean multiMeasure(String cmd) {
		ImagePlus imp = getImage();
		if (imp==null) return false;
		int[] indexes = getIndexes();
		if (indexes.length==0)
			return false;
		int measurements = Analyzer.getMeasurements();

		int nSlices = imp.getStackSize();
		if (cmd!=null)
			appendResults = cmd.contains("append")?true:false;
		if (IJ.isMacro()) {
			if (cmd.startsWith("multi-measure")) {
				measureAll = cmd.contains(" measure") && nSlices>1; // measure-all
				onePerSlice = cmd.contains(" one");
				appendResults = cmd.contains(" append");
			} else {
				if (nSlices>1)
					measureAll = true;
				onePerSlice = true;
			}
		} else {
			GenericDialog gd = new GenericDialog("Multi Measure");
			if (nSlices>1)
				gd.addCheckbox("Measure all "+nSlices+" slices", measureAll);
			gd.addCheckbox("One row per slice", onePerSlice);
			gd.addCheckbox("Append results", appendResults);
			int columns = getColumnCount(imp, measurements)*indexes.length;
			String str = nSlices==1?"this option":"both options";
			gd.setInsets(10, 25, 0);
			gd.addMessage(
				"Enabling "+str+" will result\n"+
				"in a table with "+columns+" columns."
			);
			gd.showDialog();
			if (gd.wasCanceled()) return false;
			if (nSlices>1)
				measureAll = gd.getNextBoolean();
			onePerSlice = gd.getNextBoolean();
			appendResults = gd.getNextBoolean();
		}
		if (!measureAll) nSlices = 1;
		int currentSlice = imp.getCurrentSlice();
		
		if (!onePerSlice) {
			int measurements2 = nSlices>1?measurements|Measurements.SLICE:measurements;
			ResultsTable rt = new ResultsTable();
			rt.showRowNumbers(true);
			if (appendResults && mmResults2!=null)
				rt = mmResults2;
			Analyzer analyzer = new Analyzer(imp, measurements2, rt);
			analyzer.disableReset(true);
			for (int slice=1; slice<=nSlices; slice++) {
				if (nSlices>1) imp.setSliceWithoutUpdate(slice);
				for (int i=0; i<indexes.length; i++) {
					if (restoreWithoutUpdate(imp, indexes[i]))
						analyzer.measure();
					else
						break;
				}
			}
			mmResults2 = (ResultsTable)rt.clone();
			rt.show("Results");
			if (nSlices>1)
				imp.setSlice(currentSlice);
		} else {
			Roi[] rois = getSelectedRoisAsArray();
			if ("".equals(cmd)) { // run More>>Multi Measure command in separate thread
				MultiMeasureRunner mmr = new MultiMeasureRunner();
				mmr.multiMeasure(imp, rois, appendResults);
			} else {
				ResultsTable rtMulti = multiMeasure(imp, rois, appendResults);
				mmResults = (ResultsTable)rtMulti.clone();
				rtMulti.show("Results");
				imp.setSlice(currentSlice);
				if (indexes.length>1)
					IJ.run("Select None");
			}
		}
		if (record()) {
			if (Recorder.scriptMode()) {
				Recorder.recordCall("rt = rm.multiMeasure(imp);");
				Recorder.recordCall("rt.show(\"Results\");");
			} else {
				if ((nSlices==1||measureAll) && onePerSlice && !appendResults)
					Recorder.record("roiManager", "Multi Measure");
				else {
					String options = "";
					if (measureAll)
						options += " measure_all";
					if (onePerSlice)
						options += " one";
					if (appendResults)
						options += " append";
					Recorder.record("roiManager", "multi-measure"+options);
				}
			}
		}
		return true;
	}
	
	private static ResultsTable multiMeasure(ImagePlus imp, Roi[] rois, boolean appendResults) {
		int nSlices = imp.getStackSize();
		Analyzer aSys = new Analyzer(imp); // System Analyzer
		ResultsTable rtSys = Analyzer.getResultsTable();
		ResultsTable rtMulti = new ResultsTable();
		rtMulti.showRowNumbers(true);
		if (appendResults && mmResults!=null)
			rtMulti = mmResults;
		rtSys.reset();
		int currentSlice = imp.getCurrentSlice();
		for (int slice=1; slice<=nSlices; slice++) {
			int sliceUse = slice;
			if (nSlices==1) sliceUse = currentSlice;
			imp.setSliceWithoutUpdate(sliceUse);
			rtMulti.incrementCounter();
			if ((Analyzer.getMeasurements()&Measurements.LABELS)!=0)
				rtMulti.addLabel("Label", imp.getTitle());
			int roiIndex = 0;
			for (int i=0; i<rois.length; i++) {
				imp.setRoi(rois[i]);
				roiIndex++;
				aSys.measure();
				for (int j=0; j<=rtSys.getLastColumn(); j++){
					float[] col = rtSys.getColumn(j);
					String head = rtSys.getColumnHeading(j);
					String suffix = ""+roiIndex;
					Roi roi = imp.getRoi();
					if (roi!=null) {
						String name = roi.getName();
						if (name!=null && name.length()>0 && (name.length()<9||!Character.isDigit(name.charAt(0))))
							suffix = "("+name+")";
					}
					if (head!=null && col!=null && !head.equals("Slice"))
						rtMulti.addValue(head+suffix, rtSys.getValue(j,rtSys.getCounter()-1));
				}
			}
			if (nSlices>1) IJ.showProgress(slice,nSlices);
		}
		return rtMulti;
	}

	int getColumnCount(ImagePlus imp, int measurements) {
		ImageStatistics stats = imp.getStatistics(measurements);
		ResultsTable rt = new ResultsTable();
		rt.showRowNumbers(true);
		Analyzer analyzer = new Analyzer(imp, measurements, rt);
		analyzer.saveResults(stats, null);
		int count = 0;
		for (int i=0; i<=rt.getLastColumn(); i++) {
			float[] col = rt.getColumn(i);
			String head = rt.getColumnHeading(i);
			if (head!=null && col!=null)
				count++;
		}
		return count;
	}
	
	void multiPlot() {
		ImagePlus imp = getImage();
		if (imp==null) return;
		int[] indexes = getIndexes();
		int n = indexes.length;
		if (n==0) return;
		Color[] colors = {Color.blue, Color.green, Color.magenta, Color.red, Color.cyan, Color.yellow};
		if (n>colors.length) {
			colors = new Color[n];
			double c = 0;
			double inc =150.0/n;
			for (int i=0; i<n; i++) {
				colors[i] = new Color((int)c, (int)c, (int)c);
				c += inc;
			}
		}
		int currentSlice = imp.getCurrentSlice();
		double[][] x = new double[n][];
		double[][] y = new double[n][];
		double minY = Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		double fixedMin = ProfilePlot.getFixedMin();
		double fixedMax = ProfilePlot.getFixedMax();	
		boolean freeYScale = fixedMin==0.0 && fixedMax==0.0;
		if (!freeYScale) {
			minY = fixedMin;
			maxY = fixedMax;
		}
		int maxX = 0;
		Calibration cal = imp.getCalibration();
		double xinc = cal.pixelWidth;
		for (int i=0; i<indexes.length; i++) {
			if (!restore(getImage(), indexes[i], true)) break;
			Roi roi = imp.getRoi();
			if (roi==null) break;
			if (roi.isArea() && roi.getType()!=Roi.RECTANGLE)
				IJ.run(imp, "Area to Line", "");
			ProfilePlot pp = new ProfilePlot(imp, Prefs.verticalProfile||IJ.altKeyDown());
			y[i] = pp.getProfile();
			if (y[i]==null) break;
			if (y[i].length>maxX) maxX = y[i].length;
			if (freeYScale) {
				double[] a = Tools.getMinMax(y[i]);
				if (a[0]<minY) minY=a[0];
				if (a[1]>maxY) maxY = a[1];
			}
			double[] xx = new double[y[i].length];
			for (int j=0; j<xx.length; j++)
				xx[j] = j*xinc;
			x[i] = xx;
		}
		String xlabel = "Distance ("+cal.getUnits()+")";
		Plot plot = new Plot("Profiles",xlabel, "Value", x[0], y[0]);
		plot.setLimits(0, maxX*xinc, minY, maxY);
		for (int i=1; i<indexes.length; i++) {
			plot.setColor(colors[i]);
			if (x[i]!=null)
				plot.addPoints(x[i], y[i], Plot.LINE);
		}
		plot.setColor(colors[0]);
		if (x[0]!=null)
			plot.show();
		imp.setSlice(currentSlice);
		if (indexes.length>1)
			IJ.run("Select None");
		if (record()) Recorder.record("roiManager", "Multi Plot");
	}	

	boolean drawOrFill(int mode) {
		int[] indexes = getIndexes();
		ImagePlus imp = WindowManager.getCurrentImage();
		imp.deleteRoi();
		ImageProcessor ip = imp.getProcessor();
		ip.setColor(Toolbar.getForegroundColor());
		ip.snapshot();
		Undo.setup(Undo.FILTER, imp);
		Filler filler = mode==LABEL?new Filler():null;
		int slice = imp.getCurrentSlice();
		for (int i=0; i<indexes.length; i++) {
			Roi roi = (Roi)rois.get(indexes[i]);
			int type = roi.getType();
			if (roi==null) continue;
			if (mode==FILL&&(type==Roi.POLYLINE||type==Roi.FREELINE||type==Roi.ANGLE))
				mode = DRAW;
			String name = (String) listModel.getElementAt(indexes[i]);
			int slice2 = getSliceNumber(roi, name);
			if (slice2>=1 && slice2<=imp.getStackSize()) {
				imp.setSlice(slice2);
				ip = imp.getProcessor();
				ip.setColor(Toolbar.getForegroundColor());
				if (slice2!=slice) Undo.reset();
			}
			switch (mode) {
				case DRAW: roi.drawPixels(ip); break;
				case FILL: ip.fill(roi); break;
				case LABEL:
					roi.drawPixels(ip);
					filler.drawLabel(imp, ip, i+1, roi.getBounds());
					break;
			}
		}
		if (record() && (mode==DRAW||mode==FILL))
			Recorder.record("roiManager", mode==DRAW?"Draw":"Fill");
		if (showAllCheckbox.getState())
			runCommand("show none");
		imp.updateAndDraw();
		return true;
	}

	void setProperties(Color color, int lineWidth, Color fillColor) {
		boolean showDialog = color==null && lineWidth==-1 && fillColor==null;
		int[] indexes = getIndexes();
		int n = indexes.length;
		if (n==0) return;
		Roi rpRoi = null;
		String rpName = null;
		Font font = null;
		int justification = TextRoi.LEFT;
		double opacity = -1;
		int pointType = -1;
		int pointSize = -1;
		if (showDialog) {
			//String label = (String) listModel.getElementAt(indexes[0]);
			rpRoi = (Roi)rois.get(indexes[0]);
			if (n==1) {
				fillColor =	 rpRoi.getFillColor();
				rpName = rpRoi.getName();
			}
			if (rpRoi.getStrokeColor()==null)
				rpRoi.setStrokeColor(Roi.getColor());
			rpRoi = (Roi) rpRoi.clone();
			if (n>1)
				rpRoi.setName("range: "+(indexes[0]+1)+"-"+(indexes[n-1]+1));
			rpRoi.setFillColor(fillColor);
			RoiProperties rp = new RoiProperties("Properties", rpRoi);
			if (!rp.showDialog())
				return;
			lineWidth = (int)rpRoi.getStrokeWidth();
			defaultLineWidth = lineWidth;
			color =	 rpRoi.getStrokeColor();
			fillColor =	 rpRoi.getFillColor();
			defaultColor = color;
			if (rpRoi instanceof TextRoi) {
				font = ((TextRoi)rpRoi).getCurrentFont();
				justification = ((TextRoi)rpRoi).getJustification();
			}
			if (rpRoi instanceof ImageRoi)
				opacity = ((ImageRoi)rpRoi).getOpacity();
			if (rpRoi instanceof PointRoi) {
				pointType = ((PointRoi)rpRoi).getPointType();
				pointSize = ((PointRoi)rpRoi).getSize();
			}
		}
		ImagePlus imp = WindowManager.getCurrentImage();
		if (n==getCount() && n>1 && !IJ.isMacro()) {
			GenericDialog gd = new GenericDialog("ROI Manager");
			gd.addMessage("Apply changes to all "+n+" selections?");
			gd.showDialog();
			if (gd.wasCanceled()) return;
		}
		for (int i=0; i<n; i++) {
			Roi roi = (Roi)rois.get(indexes[i]);
			if (roi==null) continue;
			if (color!=null) roi.setStrokeColor(color);
			if (lineWidth>=0) roi.setStrokeWidth(lineWidth);
			roi.setFillColor(fillColor);
			if (rpRoi!=null && n==1) {
				if (rpRoi.hasHyperStackPosition())
					roi.setPosition(rpRoi.getCPosition(), rpRoi.getZPosition(), rpRoi.getTPosition());
				else
					roi.setPosition(rpRoi.getPosition());
			}
			if (roi instanceof TextRoi) {
				roi.setImage(imp);
				if (font!=null)
					((TextRoi)roi).setCurrentFont(font);
				((TextRoi)roi).setJustification(justification);
				roi.setImage(null);
			}
			if ((roi instanceof ImageRoi) && opacity!=-1)
				((ImageRoi)roi).setOpacity(opacity);
			if (roi instanceof PointRoi) {
				if (pointType!=-1) ((PointRoi)roi).setPointType(pointType);
				if (pointSize!=-1) ((PointRoi)roi).setSize(pointSize);
			}
		}
		if (rpRoi!=null && rpName!=null && !rpRoi.getName().equals(rpName))
			rename(rpRoi.getName());
		ImageCanvas ic = imp!=null?imp.getCanvas():null;
		Roi roi = imp!=null?imp.getRoi():null;
		boolean showingAll = ic!=null &&  ic.getShowAllROIs();
		if (roi!=null && (n==1||!showingAll)) {
			if (lineWidth>=0) roi.setStrokeWidth(lineWidth);
			if (color!=null) roi.setStrokeColor(color);
			if (fillColor!=null) roi.setFillColor(fillColor);
			if (roi!=null && (roi instanceof TextRoi)) {
				((TextRoi)roi).setCurrentFont(font);
				((TextRoi)roi).setJustification(justification);
			}
			if (roi!=null && (roi instanceof ImageRoi) && opacity!=-1)
				((ImageRoi)roi).setOpacity(opacity);
		}
		if (lineWidth>1 && !showingAll && roi==null) {
			showAll(SHOW_ALL);
			showingAll = true;
		}
		if (imp!=null) imp.draw();
		if (record()) {
			if (fillColor!=null)
				Recorder.record("roiManager", "Set Fill Color", Colors.colorToString(fillColor));
			else {
				Recorder.record("roiManager", "Set Color", Colors.colorToString(color!=null?color:Color.red));
				Recorder.record("roiManager", "Set Line Width", lineWidth);
			}
		}
	}
	
	void flatten() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
		ImageCanvas ic = imp.getCanvas();
		if ((ic!=null && ic.getShowAllList()==null) && imp.getOverlay()==null && imp.getRoi()==null)
			error("Image does not have an overlay or ROI");
		else
			IJ.doCommand("Flatten"); // run Image>Flatten in separate thread
	}
			
	public boolean getDrawLabels() {
		return labelsCheckbox.getState();
	}

	private void combine() {
		ImagePlus imp = getImage();
		if (imp==null)
			return;
		Roi[] rois = getSelectedRoisAsArray();
		if (rois.length==1) {
			error("More than one item must be selected, or none");
			return;
		}
		int nPointRois = 0;
		for (int i=0; i<rois.length; i++) {
			if (rois[i].getType()==Roi.POINT)
				nPointRois++;
			else
				break;
		}
		if (nPointRois==rois.length)
			combinePoints(imp, rois);
		else
			combineRois(imp, rois);
	}
	
	private void combineRois(ImagePlus imp, Roi[] rois) {
		IJ.resetEscape();
		ShapeRoi s1=null, s2=null;
		ImageProcessor ip = null;
		for (int i=0; i<rois.length; i++) {
			IJ.showProgress(i, rois.length-1);
			if (IJ.escapePressed()) {
				IJ.showProgress(1.0);
				return;
			}
			Roi roi = rois[i];
			if (!roi.isArea()) {
				if (ip==null)
					ip = new ByteProcessor(imp.getWidth(), imp.getHeight());
				roi = convertLineToPolygon(roi, ip);
				if (roi==null) continue;
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
				s1.or(s2);
			}
		}
		if (s1!=null)
			imp.setRoi(simplifyShapeRoi(s1));
	}

	private Roi simplifyShapeRoi(ShapeRoi sRoi) { //convert composite roi to simple roi if possible
		Roi[] rois = sRoi.getRois();
		if (rois.length != 1) return sRoi;
		int type = rois[0].getType();
		if (type==Roi.POLYGON || type==Roi.FREEROI)
			return rois[0];
		else
			return sRoi;
	}

	Roi convertLineToPolygon(Roi roi, ImageProcessor ip) {
		if (roi==null) return null;
		ip.resetRoi();
		ip.setColor(0);
		ip.fill();
		ip.setColor(255);
		if (roi.getType()==Roi.LINE && roi.getStrokeWidth()>1)
			ip.fillPolygon(roi.getPolygon());
		else
			roi.drawPixels(ip);
		//new ImagePlus("ip", ip.duplicate()).show();
		ip.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
		ThresholdToSelection tts = new ThresholdToSelection();
		return tts.convert(ip);
	}

	void combinePoints(ImagePlus imp, Roi[] rois) {
		int n = rois.length;
		Polygon[] p = new Polygon[n];
		int points = 0;
		for (int i=0; i<n; i++) {
			p[i] = rois[i].getPolygon();
			points += p[i].npoints;
		}
		if (points==0)
			return;
		int[] xpoints = new int[points];
		int[] ypoints = new int[points];
		int index = 0;
		for (int i=0; i<p.length; i++) {
			for (int j=0; j<p[i].npoints; j++) {
				xpoints[index] = p[i].xpoints[j];
				ypoints[index] = p[i].ypoints[j];
				index++;
			}	
		}
		imp.setRoi(new PointRoi(xpoints, ypoints, xpoints.length));
	}

	void and() {
		ImagePlus imp = getImage();
		if (imp==null) return;
		int[] indexes = getSelectedIndexes();
		if (indexes.length==1) {
			error("More than one item must be selected, or none");
			return;
		}
		if (indexes.length==0)
			indexes = getAllIndexes();
		ShapeRoi s1=null, s2=null;
		for (int i=0; i<indexes.length; i++) {
			Roi roi = (Roi)rois.get(indexes[i]);
			if (roi==null || !roi.isArea())
				continue;
			if (s1==null) {
				if (roi instanceof ShapeRoi)
					s1 = (ShapeRoi)roi.clone();
				else
					s1 = new ShapeRoi(roi);
				if (s1==null) return;
			} else {
				if (roi instanceof ShapeRoi)
					s2 = (ShapeRoi)roi.clone();
				else
					s2 = new ShapeRoi(roi);
				if (s2==null) continue;
				s1.and(s2);
			}
		}
		if (s1!=null) imp.setRoi(simplifyShapeRoi(s1));
		if (record()) Recorder.record("roiManager", "AND");
	}

	void xor() {
		ImagePlus imp = getImage();
		if (imp==null) return;
		int[] indexes = getSelectedIndexes();
		if (indexes.length==1) {
			error("More than one item must be selected, or none");
			return;
		}
		if (indexes.length==0)
			indexes = getAllIndexes();
		ShapeRoi s1=null, s2=null;
		for (int i=0; i<indexes.length; i++) {
			Roi roi = (Roi)rois.get(indexes[i]);
			if (!roi.isArea()) continue;
			if (s1==null) {
				if (roi instanceof ShapeRoi)
					s1 = (ShapeRoi)roi.clone();
				else
					s1 = new ShapeRoi(roi);
				if (s1==null) return;
			} else {
				if (roi instanceof ShapeRoi)
					s2 = (ShapeRoi)roi.clone();
				else
					s2 = new ShapeRoi(roi);
				if (s2==null) continue;
				s1.xor(s2);
			}
		}
		if (s1!=null) imp.setRoi(simplifyShapeRoi(s1));
		if (record()) Recorder.record("roiManager", "XOR");
	}

	void addParticles() {
		String err = IJ.runMacroFile("ij.jar:AddParticles", null);
		if (err!=null && err.length()>0)
			error(err);
	}

	void sort() {
		int n = listModel.size();
		if (n==0)
			return;
		String[] labels = new String[n];
		for (int i=0; i<n; i++)
			labels[i] = (String)listModel.get(i);
		int[] indices = Tools.rank(labels);
		Roi[] rois2 = getRoisAsArray();
		listModel.removeAllElements();
		rois.clear();
		for (int i=0; i<labels.length; i++) {
			listModel.addElement(labels[indices[i]]);
			rois.add(rois2[indices[i]]);
		}
		if (record()) Recorder.record("roiManager", "Sort");
	}
	
	void specify() {
		try {IJ.run("Specify...");}
		catch (Exception e) {return;}
		runCommand("add");
	}
	
	private static boolean channel=false, slice=true, frame=false;
	
	private void removePositions(int position) {
		int[] indexes = getIndexes();
		if (indexes.length==0)
			return;
		boolean removeChannels = position==CHANNEL;
		boolean removeFrames = position==FRAME;
		boolean removeSlices = !(removeChannels||removeFrames);
		ImagePlus imp = WindowManager.getCurrentImage();
		if (position==SHOW_DIALOG) {
			if (imp!=null && !imp.isHyperStack()) {
				channel=false; slice=true; frame=false;
			}
			if (imp!=null && imp.isHyperStack()) {
				channel = slice = frame = false;
				if (imp.getNSlices()>1)
					slice = true;
				if (imp.getNFrames()>1 && imp.getNSlices()==1)
					frame = true;
			}
			Font font = new Font("SansSerif", Font.BOLD, 12);
			GenericDialog gd = new GenericDialog("Remove");
			gd.setInsets(5,15,0);
			gd.addMessage("Remove positions for:      ", font);
			gd.setInsets(6,25,0);
			gd.addCheckbox("Channels:", channel);
			gd.setInsets(0,25,0);
			gd.addCheckbox("Slices:", slice);
			gd.setInsets(0,25,0);
			gd.addCheckbox("Frames:", frame);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			removeChannels = gd.getNextBoolean();
			removeSlices = gd.getNextBoolean();
			removeFrames = gd.getNextBoolean();
		}
		if (!removeChannels && !removeSlices && !removeFrames) {
			slice = true;
			return;
		}
		for (int i=0; i<indexes.length; i++) {
			int index = indexes[i];
			Roi roi = (Roi)rois.get(index);
			String name = (String)listModel.getElementAt(index);
			int n = getSliceNumber(name);
			if (n>0) {
				String name2 = name.substring(5, name.length());
				roi.setName(name2);
				rois.set(index, roi);
				listModel.setElementAt(name2, index);
			}
			int c = roi.getCPosition();
			int z = roi.getZPosition();
			int t = roi.getTPosition();
			if (c>0 || t>0) {
				if (removeChannels) c = 0;
				if (removeSlices) z = 0;
				if (removeFrames) t = 0;
				roi.setPosition(c, z, t);
			} else
				roi.setPosition(0);
		}
		if (imp!=null)
			imp.draw();
		if (record()) {
			if (removeChannels) Recorder.record("roiManager", "Remove Channel Info");
			if (removeSlices) Recorder.record("roiManager", "Remove Slice Info");
			if (removeFrames) Recorder.record("roiManager", "Remove Frame Info");
		}
	}

	private void help() {
		String macro = "run('URL...', 'url="+IJ.URL+"/docs/menus/analyze.html#manager');";
		new MacroRunner(macro);
	}

	private void labels() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			showAllCheckbox.setState(true);
			labelsCheckbox.setState(true);
			showAll(LABELS);
		}
		try {
			IJ.run("Labels...");
		} catch(Exception e) {}
		Overlay defaultOverlay = OverlayLabels.createOverlay();
		Prefs.useNamesAsLabels = defaultOverlay.getDrawNames();
	}

	private void options() {
		Color c = ImageCanvas.getShowAllColor();
		GenericDialog gd = new GenericDialog("Options");
		//gd.addPanel(makeButtonPanel(gd), GridBagConstraints.CENTER, new Insets(5, 0, 0, 0));
		gd.addCheckbox("Associate \"Show All\" ROIs with slices", Prefs.showAllSliceOnly);
		gd.addCheckbox("Restore ROIs centered", restoreCentered);
		gd.addCheckbox("Use ROI names as labels", Prefs.useNamesAsLabels);
		gd.showDialog();
		if (gd.wasCanceled()) {
			if (c!=ImageCanvas.getShowAllColor())
				ImageCanvas.setShowAllColor(c);
			return;
		}
		Prefs.showAllSliceOnly = gd.getNextBoolean();
		restoreCentered = gd.getNextBoolean();
		Prefs.useNamesAsLabels = gd.getNextBoolean();
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			Overlay overlay = imp.getOverlay();
			if (overlay==null) {
				ImageCanvas ic = imp.getCanvas();
				if (ic!=null)
					overlay = ic.getShowAllList();
			}
			if (overlay!=null) {
				overlay.drawNames(Prefs.useNamesAsLabels);
				setOverlay(imp, overlay);
			} else
				imp.draw();
		}
		if (record()) {
			Recorder.record("roiManager", "Associate", Prefs.showAllSliceOnly?"true":"false");
			Recorder.record("roiManager", "Centered", restoreCentered?"true":"false");
			Recorder.record("roiManager", "UseNames", Prefs.useNamesAsLabels?"true":"false");
		}
	}

	Panel makeButtonPanel(GenericDialog gd) {
		Panel panel = new Panel();
		//buttons.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
		colorButton = new Button("\"Show All\" Color...");
		colorButton.addActionListener(this);
		panel.add(colorButton);
		return panel;
	}
	
	void setShowAllColor() {
			ColorChooser cc = new ColorChooser("\"Show All\" Color", ImageCanvas.getShowAllColor(),	 false);
			ImageCanvas.setShowAllColor(cc.getColor());
	}

	void split() {
		ImagePlus imp = getImage();
		if (imp==null) return;
		Roi roi = imp.getRoi();
		if (roi==null || roi.getType()!=Roi.COMPOSITE) {
			error("Image with composite selection required");
			return;
		}
		boolean record = Recorder.record;
		Recorder.record = false;
		Roi[] rois = ((ShapeRoi)roi).getRois();
		for (int i=0; i<rois.length; i++) {
			imp.setRoi(rois[i]);
			addRoi(false);
		}
		Recorder.record = record;
		if (record()) Recorder.record("roiManager", "Split");
	}
	
	void showAll(int mode) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			return;
		boolean showAll = mode==SHOW_ALL;
		if (showAll)
			imageID = imp.getID();
		if (mode==LABELS || mode==NO_LABELS)
			showAll = true;
		if (showAll) imp.deleteRoi();
		if (mode==SHOW_NONE) {
			removeOverlay(imp);
			imageID = 0;
		} else if (getCount()>0) {
			Roi[] rois = getRoisAsArray();
			Overlay overlay = newOverlay();
			for (int i=0; i<rois.length; i++)
				overlay.add(rois[i]);
			setOverlay(imp, overlay);
		}
	}

	void updateShowAll() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			return;
		if (showAllCheckbox.getState()) {
			if (getCount()>0) {
				Roi[] rois = getRoisAsArray();
				Overlay overlay = newOverlay();
				for (int i=0; i<rois.length; i++)
					overlay.add(rois[i]);
				setOverlay(imp, overlay);
			} else
				removeOverlay(imp);
		} else
			removeOverlay(imp);
	}

	int[] getAllIndexes() {
		int count = getCount();
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
		if (!IJ.isMacro())
			ignoreInterrupts = false;
	}
	
	/** Returns a reference to the ROI Manager and opens
		 the "ROI Manager" window if it is not already open. */
	public static RoiManager getRoiManager() {
		if (instance!=null)
			return (RoiManager)instance;
		else
			return new RoiManager();
	}

	/** Returns a reference to the ROI Manager, or null if it is not open
	 * and a batch mode macro is not running. If the ROI Manager 
	 * is not open and a batch mode macro is running, 
	 * returns the hidden batch mode RoiManager.
	 * @see #getRoiManager
	*/
	public static RoiManager getInstance() {
		if (instance==null && IJ.isMacro())
			return Interpreter.getBatchModeRoiManager();
		else
			return (RoiManager)instance;
	}
	
	public static RoiManager getRawInstance() {
		return (RoiManager)instance;
	}

	/** Returns a reference to the ROI Manager window or to the
		macro batch mode RoiManager, or null if neither exists. */
	public static RoiManager getInstance2() {
		RoiManager rm = getInstance();
		if (rm==null && IJ.isMacro())
			rm = Interpreter.getBatchModeRoiManager();
		return rm;
	}

	/** Obsolete
	 * @deprecated
	 * @see #getCount
	 * @see #getRoisAsArray
	*/
	public Hashtable getROIs() {
		Roi[] rois = getRoisAsArray();
		Hashtable ht = new Hashtable();
		for (int i=0; i<rois.length; i++)
			ht.put((String)listModel.getElementAt(i), rois[i]);
		return ht;
	}

	/** Obsolete
	 * @deprecated
	 * @see #getCount
	 * @see #getRoisAsArray
	 * @see #getSelectedIndex
	*/
	public List getList() {
		List awtList = new List();
		for (int i=0; i<getCount(); i++)
			awtList.add((String)listModel.getElementAt(i));
		int index = getSelectedIndex();
		if (index>=0)
			awtList.select(index);
		return awtList;
	}
	
	/** Returns the ROI count. */
	public int getCount() {
		return listModel!=null?listModel.getSize():0;
	}

	/** Returns the index of the specified Roi, or -1 if it is not found. */
    public int getRoiIndex(Roi roi) {
		int n = getCount();
		for (int i=0; i<n; i++) {
			Roi roi2 = (Roi)rois.get(i);
			if (roi==roi2)
				return i;
		}
		return -1;
    }
    
	/** Returns the index of the first selected ROI or -1 if no ROI is selected. */
	public int getSelectedIndex() {
		return list.getSelectedIndex();
    }
    
	/** Returns a reference to the ROI at the specified index. */
	public Roi getRoi(int index) {
		if (index<0 || index>=getCount())
			return null;
		return (Roi)rois.get(index);
	}

	/** Returns the ROIs as an array. */
	public synchronized Roi[] getRoisAsArray() {
		Roi[] array = new Roi[rois.size()];
		return (Roi[])rois.toArray(array);
	}
	
	/** Returns the selected ROIs as an array, or
		all the ROIs if none are selected. */
	public Roi[] getSelectedRoisAsArray() {
		int[] indexes = getIndexes();
		int n = indexes.length;
		Roi[] array = new Roi[n];
		for (int i=0; i<n; i++)
			array[i] = (Roi)rois.get(indexes[i]);
		return array;
	}
			
	/** Returns the name of the ROI with the specified index,
		or null if the index is out of range. */
	public String getName(int index) {
		if (index>=0 && index<getCount())
			return	(String) listModel.getElementAt(index);
		else
			return null;
	}

	/** Returns the name of the ROI with the specified index.
		Can be called from a macro using
		<pre>call("ij.plugin.frame.RoiManager.getName", index)</pre>
		Returns "null" if the Roi Manager is not open or index is
		out of range.
	*/
	public static String getName(String index) {
		int i = (int)Tools.parseDouble(index, -1);
		RoiManager instance = getInstance2();
		if (instance!=null && i>=0 && i<instance.getCount())
			return	(String) instance.listModel.getElementAt(i);
		else
			return "null";
	}

	/** Executes the ROI Manager "Add", "Add & Draw", "Update", "Delete", "Measure", "Draw",
		"Show All", "Show None", "Fill", "Deselect", "Select All", "Combine", "AND", "XOR", "Split",
		"Sort" or "Multi Measure" command.	Returns false if <code>cmd</code>
		is not one of these strings. */
	public boolean runCommand(String cmd) {
		cmd = cmd.toLowerCase();
		macro = true;
		boolean ok = true;
		if (cmd.equals("add")) {
			boolean shift = IJ.shiftKeyDown();
			boolean alt = IJ.altKeyDown();
			if (Interpreter.isBatchMode()) {
				shift = false;
				alt = false;
			}
			add(shift, alt);
			if (IJ.isJava18()&&IJ.isMacOSX())
				repaint();
		} else if (cmd.equals("add & draw"))
			addAndDraw(false);
		else if (cmd.equals("update"))
			update(true);
		else if (cmd.equals("update2"))
			update(false);
		else if (cmd.equals("delete"))
			delete(false);
		else if (cmd.equals("measure"))
			measure(COMMAND);
		else if (cmd.equals("draw"))
			drawOrFill(DRAW);
		else if (cmd.equals("fill"))
			drawOrFill(FILL);
		else if (cmd.equals("label"))
			drawOrFill(LABEL);
		else if (cmd.equals("and"))
			and();
		else if (cmd.equals("or") || cmd.equals("combine"))
			combine();
		else if (cmd.equals("xor"))
			xor();
		else if (cmd.equals("split"))
			split();
		else if (cmd.equals("sort"))
			sort();
		else if (cmd.startsWith("multi measure") || cmd.startsWith("multi-measure"))
			multiMeasure(cmd);
		else if (cmd.equals("multi plot"))
			multiPlot();
		else if (cmd.equals("show all")) {
			if (WindowManager.getCurrentImage()!=null) {
				showAll(SHOW_ALL);
				showAllCheckbox.setState(true);
			}
		} else if (cmd.equals("show none")) {
			if (WindowManager.getCurrentImage()!=null) {
				showAll(SHOW_NONE);
				showAllCheckbox.setState(false);
			}
		} else if (cmd.equals("show all with labels")) {
			labelsCheckbox.setState(true);
			showAll(LABELS);
			showAllCheckbox.setState(true);
			if (Interpreter.isBatchMode()) IJ.wait(250);
		} else if (cmd.equals("show all without labels")) {
			showAllCheckbox.setState(true);
			labelsCheckbox.setState(false);
			showAll(NO_LABELS);
			if (Interpreter.isBatchMode()) IJ.wait(250);
		} else if (cmd.equals("deselect")||cmd.indexOf("all")!=-1) {
			if (IJ.isMacOSX()) ignoreInterrupts = true;
			deselect();
			IJ.wait(50);
		} else if (cmd.equals("reset")) {
			reset();
		} else if (cmd.equals("debug")) {
			//IJ.log("Debug: "+debugCount);
			//for (int i=0; i<debugCount; i++)
			//	IJ.log(debug[i]);
		} else if (cmd.equals("enable interrupts")) {
			ignoreInterrupts = false;
		} else if (cmd.equals("remove channel info")) {
			removePositions(CHANNEL);
		} else if (cmd.equals("remove slice info")) {
			removePositions(SLICE);
		} else if (cmd.equals("remove frame info")) {
			removePositions(FRAME);
		} else if (cmd.equals("list")) {
			listRois();
		} else if (cmd.equals("interpolate rois")) {
			interpolateRois();
		} else
			ok = false;
		macro = false;
		return ok;
	}

	/** Using the specified image, runs the ROI Manager "Add", "Add & Draw", "Update",
		"Delete", "Measure", "Draw", "Show All", "Show None", "Fill", "Deselect", "Select All", 
		"Combine", "AND", "XOR", "Split", "Sort" or "Multi Measure" command. */
	public boolean runCommand(ImagePlus imp, String cmd) {
		WindowManager.setTempCurrentImage(imp);
		boolean ok = runCommand(cmd);
		WindowManager.setTempCurrentImage(null);
		return ok;
	}

	/** Executes the ROI Manager "Open", "Save" or "Rename" command. Returns false if 
	<code>cmd</code> is not "Open", "Save" or "Rename", or if an error occurs. */
	public boolean runCommand(String cmd, String name) {
		cmd = cmd.toLowerCase();
		macro = true;
		if (cmd.equals("open")) {
			open(name);
			macro = false;
			return true;
		} else if (cmd.equals("save")) {
			if (name!=null && name.endsWith(".roi"))
				saveOne(getIndexes(), name);
			else
				save(name, false);
		} else if (cmd.equals("save selected")) {
			if (name!=null && name.endsWith(".roi"))
				saveOne(getIndexes(), name);
			else
				save(name, true);
		} else if (cmd.equals("rename")) {
			rename(name);
			macro = false;
			return true;
		} else if (cmd.equals("set color")) {
			Color color = Colors.decode(name, Color.cyan);
			setProperties(color, -1, null);
			macro = false;
			return true;
		} else if (cmd.equals("set fill color")) {
			Color fillColor = Colors.decode(name, Color.cyan);
			setProperties(null, -1, fillColor);
			macro = false;
			return true;
		} else if (cmd.equals("set line width")) {
			int lineWidth = (int)Tools.parseDouble(name, 0);
			if (lineWidth>=0)
				setProperties(null, lineWidth, null);
			macro = false;
			return true;
		} else if (cmd.equals("associate")) {
			Prefs.showAllSliceOnly = name.equals("true")?true:false;
			macro = false;
			return true;
		} else if (cmd.equals("centered")) {
			restoreCentered = name.equals("true")?true:false;
			macro = false;
			return true;
		} else if (cmd.equals("usenames")) {
			Prefs.useNamesAsLabels = name.equals("true")?true:false;
			macro = false;
			if (labelsCheckbox.getState()) {
				ImagePlus imp = WindowManager.getCurrentImage();
				if (imp!=null) imp.draw();
			}
			return true;
		}
		return false;
	}
	
	/** Clears this RoiManager so that it contains no ROIs. */
	public void reset() {
		if (IJ.isMacOSX() && IJ.isMacro())
			ignoreInterrupts = true;
		listModel.removeAllElements();
		overlayTemplate = null;
		rois.clear();
		updateShowAll();
	}
	
	private void translate() {
		GenericDialog gd = new GenericDialog("Translate");
		gd.addNumericField("X offset (pixels): ", translateX, 0);
		gd.addNumericField("Y offset (pixels): ", translateY, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		translateX = gd.getNextNumber();
		translateY = gd.getNextNumber();
		translate(translateX, translateY);
		if (record()) {
			if (Recorder.scriptMode())
				Recorder.recordCall("rm.translate("+translateX+", "+translateY+");");
			else
				Recorder.record("roiManager", "translate", (int)translateX, (int)translateY);
		}
	}

	/** Moves the selected ROIs or all the ROIs if none are selected. */
	public void translate(double dx, double dy) {
		Roi[] rois = getSelectedRoisAsArray();
		for (int i=0; i<rois.length; i++) {
			Roi roi = rois[i];
			Rectangle2D r = roi.getFloatBounds();
			roi.setLocation(r.getX()+dx, r.getY()+dy);
		}
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null)
			imp.draw();
	}

	private boolean save(String name, boolean saveSelected) {
		if (!name.endsWith(".zip") && !name.equals(""))
			return error("Name must end with '.zip'");
		if (getCount()==0)
			return error("The list is empty");
		int[] indexes = null;
		if (saveSelected)
			indexes = getIndexes();
		else
			indexes = getAllIndexes();
		boolean ok = false;
		if (name.equals(""))
			ok = saveMultiple(indexes, null);
		else
			ok = saveMultiple(indexes, name);
		macro = false;
		return ok;
	}
	
	/** Adds the current selection to the ROI Manager, using the
		specified color (a 6 digit hex string) and line width. */
	public boolean runCommand(String cmd, String hexColor, double lineWidth) {
		if (hexColor==null && lineWidth==1.0 && (IJ.altKeyDown()&&!Interpreter.isBatchMode()))
			addRoi(true);
		else {
			Color color = hexColor!=null?Colors.decode(hexColor, Color.cyan):null;
			addRoi(null, false, color, (int)Math.round(lineWidth));
		}
		return true;	
	}
		
	/** Assigns the ROI at the specified index to the current image. */
	public void select(int index) {
		select(null, index);
	}
	
	/** Assigns the ROI at the specified index to 'imp'. */
	public void select(ImagePlus imp, int index) {
		selectedIndexes = null;
		if (index<0) {
			deselect();
			return;
		}
		int n = getCount();
		if (index>=n) return;
		boolean mm = list.getSelectionMode() == ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;
		if (mm) list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		int delay = 1;
		long start = System.currentTimeMillis();
		while (true) {
			if (list.isSelectedIndex(index))
				break;
			list.clearSelection();
			list.setSelectedIndex(index);
		}
		if (imp==null)
			imp = WindowManager.getCurrentImage();
		if (imp!=null)
			restore(imp, index, true);
		if (mm) list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
	}
	
	public void selectAndMakeVisible(ImagePlus imp, int index) {
		select(imp, index);
		list.ensureIndexIsVisible(index);
	}
	
	public void select(int index, boolean shiftKeyDown, boolean altKeyDown) {
		if (!(shiftKeyDown||altKeyDown))
			select(index);
		ImagePlus imp = IJ.getImage();
		if (imp==null)
			return;
		Roi previousRoi = imp.getRoi();
		if (previousRoi==null) {
			select(index);
			return;
		}
		Roi.previousRoi = (Roi)previousRoi.clone();
		Roi roi = (Roi)rois.get(index);
		if (roi!=null) {
			roi.setImage(imp);
			roi.update(shiftKeyDown, altKeyDown);
		}
	}
	
	public void deselect() {
		int n = getCount();
		for (int i=0; i<n; i++)
			list.clearSelection();
		if (record()) Recorder.record("roiManager", "Deselect");
		return;
	}
	
	/** Deselect the specified ROI if it is the only one selected. */
	public void deselect(Roi roi) {
		int[] indexes = getSelectedIndexes();
		if (indexes.length==1 && listModel.getSize()>0) {
			String label = (String)listModel.getElementAt(indexes[0]);
			if (label.equals(roi.getName())) {
				deselect();
				repaint();
			}
		}
	}

	public void setEditMode(ImagePlus imp, boolean editMode) {
		showAllCheckbox.setState(editMode);
		labelsCheckbox.setState(editMode);
		showAll(editMode?LABELS:SHOW_NONE);
	}
	
	/** Overrides PlugInFrame.close(). */
	public void close() {
		super.close();
		instance = null;
		resetMultiMeasureResults();
		Prefs.saveLocation(LOC_KEY, getLocation());
		if (!showAllCheckbox.getState() || IJ.macroRunning())
			return;
		int n = getCount();
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null || (imp.getCanvas()!=null && imp.getCanvas().getShowAllList()==null))
			return;
		if (n>0) {
			GenericDialog gd = new GenericDialog("ROI Manager");
			gd.addMessage("Save the "+n+" displayed ROIs as an overlay?");
			gd.setOKLabel("Discard");
			gd.setCancelLabel("Save as Overlay");
			gd.showDialog();
			if (gd.wasCanceled())
				moveRoisToOverlay(imp);
			else
				removeOverlay(imp);
		} else
			imp.draw();
	}
	
	/** Moves all the ROIs to the specified image's overlay. */
	public void moveRoisToOverlay(ImagePlus imp) {
		if (imp==null)
			return;
		Roi[] rois = getRoisAsArray();
		int n = rois.length;
		Overlay overlay = imp.getOverlay();
		if (overlay==null)
			overlay = newOverlay();
		for (int i=0; i<n; i++) {
			Roi roi = (Roi)rois[i].clone();
			if (!Prefs.showAllSliceOnly && !IJ.isMacro())
				roi.setPosition(0);
			//if (roi.getStrokeWidth()==1)
			//	roi.setStrokeWidth(0);
			overlay.add(roi);
		}
		if (overlayTemplate!=null)
			overlay.drawLabels(overlayTemplate.getDrawLabels());
		imp.setOverlay(overlay);
		if (imp.getCanvas()!=null)
			setOverlay(imp, null);
	}
	
	public void mousePressed (MouseEvent e) {
		int x=e.getX(), y=e.getY();
		if (e.isPopupTrigger() || e.isMetaDown())
			pm.show(e.getComponent(),x,y);
	}

	public void mouseWheelMoved(MouseWheelEvent event) {
		synchronized(this) {
			int index = list.getSelectedIndex();
			int rot = event.getWheelRotation();
			if (rot<-1) rot = -1;
			if (rot>1) rot = 1;
			index += rot;
			if (index<0) index = 0;
			if (index>=getCount()) index = getCount();
			//IJ.log(index+"  "+rot);
			select(index);
			if (IJ.isWindows())
				list.requestFocusInWindow();
			if (IJ.isJava18()&&IJ.isMacOSX())
				repaint();
		}
	}
	
	/** Selects multiple ROIs, where 'indexes' is an array of integers, 
		each greater than or equal to 0 and less than the value returned by getCount().
	*/
	/** Selects multiple ROIs, where 'indexes' is an array of integers, each
	* greater than or equal to 0 and less than the value returned by getCount().
	* @see #getSelectedIndexes
	* @see #getSelectedRoisAsArray
	* @see #getCount
	*/
	public void setSelectedIndexes(int[] indexes) {
		int count = getCount();
		if (count==0) return;
		for (int i=0; i<indexes.length; i++) {
			if (indexes[i]<0) indexes[i]=0;
			if (indexes[i]>=count) indexes[i]=count-1;
		}
		selectedIndexes = indexes;
		list.setSelectedIndices(indexes);
	}
	
	/** Returns an array of the selected indexes. */
	public int[] getSelectedIndexes() {
		if (selectedIndexes!=null) {
			int[] indexes = selectedIndexes;
			selectedIndexes = null;
			return indexes;
		} else
			return list.getSelectedIndices();
	}
	
	/** This is a macro-callable version of getSelectedIndexes().
	 * Example: indexes=split(call("ij.plugin.frame.RoiManager.getIndexesAsString"));
	*/
	public static String getIndexesAsString() {
		RoiManager rm = RoiManager.getInstance();
		if (rm==null) return "";
		String str = Arrays.toString(rm.getSelectedIndexes());
		str = str.replaceAll(",","");
		return str.substring(1,str.length()-1);
	}
	
	/** Returns an array of the selected indexes or all indexes if none are selected. */
	public int[] getIndexes() {
		int[] indexes = getSelectedIndexes();
		if (indexes.length==0)
			indexes = getAllIndexes();
		return indexes;
	}
	
	/** Returns 'true' if the index is valid and the indexed ROI is selected. */
	public boolean isSelected(int index) {
		return index>=0 && index<listModel.getSize() && list.isSelectedIndex(index);
	}
	
	private Overlay newOverlay() {
		Overlay overlay = OverlayLabels.createOverlay();
		overlay.drawLabels(labelsCheckbox.getState());
		if (overlay.getLabelFont()==null && overlay.getLabelColor()==null) {
			overlay.setLabelColor(Color.white);
			overlay.drawBackgrounds(true);
		}
		overlay.drawNames(Prefs.useNamesAsLabels);
		if (overlayTemplate!=null) {
			overlay.drawNames(overlayTemplate.getDrawNames());
			overlay.drawBackgrounds(overlayTemplate.getDrawBackgrounds());
			overlay.setLabelColor(overlayTemplate.getLabelColor());
			overlay.setLabelFont(overlayTemplate.getLabelFont(), overlayTemplate.scalableLabels());
		}
		return overlay;
	}

	private void removeOverlay(ImagePlus imp) {
		if (imp!=null && imp.getCanvas()!=null)
			setOverlay(imp, null);
	}
	
	private void setOverlay(ImagePlus imp, Overlay overlay) {
		if (imp==null)
			return;
		ImageCanvas ic = imp.getCanvas();
		if (ic==null) {
			imp.setOverlay(overlay);
			return;
		}
		ic.setShowAllList(overlay);
		imp.draw();
	}
	
	private boolean record() {
		return Recorder.record && allowRecording && !IJ.isMacro();
	}
	
	private boolean recordInEvent() {
		return Recorder.record && !IJ.isMacro();
	}

	public void allowRecording(boolean allow) {
		this.allowRecording = allow;
	}
	
	public void mouseReleased (MouseEvent e) {}
	public void mouseClicked (MouseEvent e) {}
	public void mouseEntered (MouseEvent e) {}
	public void mouseExited (MouseEvent e) {}
	
	public void valueChanged(ListSelectionEvent e) {
		if (e.getValueIsAdjusting())
			return;
		if (getCount()==0) {
			if (recordInEvent())
				Recorder.record("roiManager", "Deselect");
			return;
		}
		int[] selected = list.getSelectedIndices();
		if (selected.length==0) {
			imageID = 0;
			return;
		}
		if (WindowManager.getCurrentImage()!=null) {
			if (selected.length==1) {
				ImagePlus imp = getImage();
				if (imp!=null) {
					Roi roi = imp.getRoi();
					if (roi!=null)
						Roi.previousRoi = (Roi)roi.clone();
				}
				restore(imp, selected[0], true);
				ResultsTable.selectRow(imp!=null?imp.getRoi():null);
				imageID = imp!=null?imp.getID():0;
			}
			if (recordInEvent()) {
				String arg = Arrays.toString(selected);
				if (!arg.startsWith("[") || !arg.endsWith("]"))
					return;
				arg = arg.substring(1, arg.length()-1);
				arg = arg.replace(" ", "");
				if (Recorder.scriptMode()) {
					if (selected.length==1)
						Recorder.recordCall("rm.select("+arg+");");
					else
						Recorder.recordCall("rm.setSelectedIndexes(["+arg+"]);");
				} else {
					if (selected.length == 1)
						Recorder.recordString("roiManager(\"Select\", " + arg + ");\n");
					else
						Recorder.recordString("roiManager(\"Select\", newArray(" + arg + "));\n");
				}
			}
		}
	}

    public void windowActivated(WindowEvent e) {
    	super.windowActivated(e);
    	ImagePlus imp = WindowManager.getCurrentImage();
    	if (imp!=null) {
    		if (imageID!=0 && imp.getID()!=imageID) {
    			showAll(SHOW_NONE);
    			if (okToSet())
					showAllCheckbox.setState(false);
				deselect();
				imageID = 0;
    		}
    	}
	}
	
	public static void resetMultiMeasureResults() {
		mmResults = mmResults2 = null;
	}
	
	public void setOverlay(Overlay overlay) {
		if (overlay==null) {
			overlayTemplate = null;
			return;
		}
		reset();
		overlayTemplate = overlay.create();
		setEditMode(null, false);
		for (int i=0; i<overlay.size(); i++)
			add(overlay.get(i), i+1);
		setEditMode(null, true);
		runCommand("show all");
	}
	
	// This class runs the "Multi Measure" command in a separate thread
	private class MultiMeasureRunner implements Runnable  {
		private Thread thread;
		private ImagePlus imp;
		private Roi[] rois;
		private boolean appendResults;
		
		public void multiMeasure(ImagePlus imp, Roi[] rois, boolean appendResults) {
			this.imp = imp;
			this.rois = rois;
			this.appendResults = appendResults;
			thread = new Thread(this, "MultiMeasure"); 
			thread.start();
		}
	
		public void run() {
			int currentSlice = imp.getCurrentSlice();
			ResultsTable rtMulti = RoiManager.multiMeasure(imp, rois, appendResults);
			mmResults = (ResultsTable)rtMulti.clone();
			rtMulti.show("Results");
			imp.setSlice(currentSlice);
			if (rois.length>1)
				IJ.run("Select None");
		}
		
	}


}
