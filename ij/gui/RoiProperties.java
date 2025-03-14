package ij.gui;
import ij.*;
import ij.plugin.Colors;
import ij.io.RoiDecoder;
import ij.process.*;
import ij.measure.*;
import ij.util.Tools;
import ij.plugin.filter.Analyzer;
import ij.text.TextWindow;
import java.awt.*;
import java.util.*;
import java.awt.event.*;


 /** Displays a dialog that allows the user to specify ROI properties such as color and line width. */
public class RoiProperties implements TextListener, WindowListener {
	private ImagePlus imp;
	private Roi roi;
	private Overlay overlay;
	private String title;
	private boolean showName = true;
	private boolean showListCoordinates;
	private boolean addToOverlay;
	private boolean overlayOptions;
	private boolean setPositions;
	private boolean listCoordinates;
	private boolean listProperties;
	private boolean showPointCounts;
	private static final String[] justNames = {"Left", "Center", "Right"};
	private int nProperties;
	private TextField groupField, colorField;
	private Label groupName;

	/** Constructs a RoiProperties using the specified title for a given image and roi;
	 *  call showDialog for the actual dialog.
	 *  Note that the title determines which fields will be shown in the dialog. */
	public RoiProperties(String title, ImagePlus imp, Roi roi) {
		if (roi==null)
			throw new IllegalArgumentException("ROI is null");
		this.title = title;
		this.imp = imp;
		this.roi = roi;
		showName = title.startsWith("Prop");
		showListCoordinates = showName && title.endsWith(" ");
		nProperties = showListCoordinates?roi.getPropertyCount():0;
		addToOverlay = title.equals("Add to Overlay");
		overlayOptions = title.equals("Overlay Options");
		if (overlayOptions) {
			imp = WindowManager.getCurrentImage();
			overlay = imp!=null?imp.getOverlay():null;
			setPositions = roi.getPosition()!=0;
		}
	}

	/** Constructs a RoiProperties using the specified title for a given roi;
	 *  call showDialog for the actual dialog.
	 *  Note that the title determines which fields will be shown in the dialog. */
	 
	public RoiProperties(String title, Roi roi) {
		this(title, WindowManager.getCurrentImage(), roi);
	}
	
	/** Displays the dialog box and returns 'false' if the user cancels it. */
	public boolean showDialog() {
		String name = roi.getName();
		boolean isRange = name!=null && name.startsWith("range:");
		String nameLabel = isRange?"Range:":"Name:";
		if (isRange) name = name.substring(7);
		if (name==null) name = "";
		if (!isRange && (roi instanceof ImageRoi) && !overlayOptions)
			return showImageDialog(name);
		Color strokeColor = roi.getStrokeColor();
		Color fillColor = roi.getFillColor();
		double strokeWidth = roi.getStrokeWidth();
		double strokeWidth2 = strokeWidth;
		boolean isText = roi instanceof TextRoi;
		boolean isLine = roi.isLine();
		boolean isPoint = roi instanceof PointRoi;
		int justification = TextRoi.LEFT;
		double angle = 0.0;
		boolean antialias = true;
		if (isText) {
			TextRoi troi = (TextRoi)roi;
			Font font = troi.getCurrentFont();
			strokeWidth = font.getSize();
			angle = troi.getAngle();
			justification = troi.getJustification();
			antialias = troi.getAntiAlias();
		}
		String position = roi.getPositionAsString();			
		String group = ""+roi.getGroup();
		if (group.equals("0"))
			group = "none";
		String linec = Colors.colorToString(strokeColor);
		String fillc = Colors.colorToString(fillColor);
		if (IJ.isMacro()) {
			fillc = "none";
			setPositions = false;
		}
		int digits = (int)strokeWidth==strokeWidth?0:1;
		GenericDialog gd = new GenericDialog(title);
		if (showName) {
			gd.addStringField(nameLabel, name, 20);
			String label = "Position:";
			if (position.contains(",") || (imp!=null&&imp.isHyperStack()))
				label = "Position (c,z,t):";
			gd.addStringField(label, position, 20);
			gd.addStringField("Group:", group);
			gd.addToSameRow(); gd.addMessage("wwwwwwwwwwww");
		}
		if (isText) {
			gd.addStringField("Stroke color:", linec);
			gd.addNumericField("Font size:", strokeWidth, digits, 4, "points");
			digits = (int)angle==angle?0:1;
			gd.addNumericField("Angle:", angle, digits, 4, "degrees");
			gd.setInsets(0, 0, 0);
			gd.addChoice("Justification:", justNames, justNames[justification]);
		} else {
			if (isPoint)
				gd.addStringField("Stroke (point) color:", linec);
			else {
				gd.addStringField("Stroke color:", linec);
				gd.addNumericField("Width:", strokeWidth, digits);
			}
		}
		groupName = (Label)gd.getMessage();
		if (showName && !IJ.isMacro()) {
			Vector v = gd.getStringFields();
			groupField = (TextField)v.elementAt(v.size()-2);
			groupField.addTextListener(this);
			colorField = (TextField)v.elementAt(v.size()-1);
		}

		if (!isLine) {
			if (isPoint) {
				int index = ((PointRoi)roi).getPointType();
				gd.addChoice("Point type:", PointRoi.types, PointRoi.types[index]);
				index = ((PointRoi)roi).getSize();
				gd.addChoice("Size:", PointRoi.sizes, PointRoi.sizes[index]);
			} else {
				gd.addMessage("");
				gd.addStringField("Fill color:", fillc);
			}
		}
		boolean askShowOnAllSlices = addToOverlay && imp!=null && imp.getNSlices()>1;
		if (askShowOnAllSlices)
			gd.addCheckbox("Show on all Slices", roi.getPosition()==0&&!roi.hasHyperStackPosition());
		if (addToOverlay)
			gd.addCheckbox("New overlay", false);
		if (overlayOptions) {
			gd.addCheckbox("Set stack positions", setPositions);
			if (overlay!=null) {
				int size = overlay.size();
				gd.setInsets(15,20,0);
				if (imp!=null && imp.getHideOverlay())
					gd.addMessage("Current overlay is hidden", null, Color.darkGray);
				else
					gd.addMessage("Current overlay has "+size+" element"+(size>1?"s":""), null, Color.darkGray);
				gd.setInsets(0,30,0);
				gd.addCheckbox("Apply", false);
				gd.setInsets(0,30,0);
				gd.addCheckbox("Show labels", overlay.getDrawLabels());
				gd.setInsets(0,30,0);
				gd.addCheckbox("Hide", imp!=null?imp.getHideOverlay():false);
			} else
				gd.addMessage("No overlay", null, Color.darkGray);
		}
		if (isText)
			gd.addCheckbox("Antialiased text", antialias);
		if (showListCoordinates) {
			if ((roi instanceof PointRoi) && Toolbar.getMultiPointMode())
				showPointCounts = true;
			if (showPointCounts)
				gd.addCheckbox("Show point counts (shortcut: alt+y)", listCoordinates);
			else
				gd.addCheckbox("List coordinates ("+roi.size()+")", listCoordinates);
			if (nProperties>0)
				gd.addCheckbox("List properties ("+nProperties+")", listProperties);
			else {
				gd.setInsets(5,20,0);
				gd.addMessage("No properties");
			}
		}
		if (isText && !isRange) {
			String text = ((TextRoi)roi).getText();
			int nLines = Tools.split(text, "\n").length + 1;
			gd.addTextAreas(text, null, Math.min(nLines+1, 5), 30);
		}
		if (showName && "".equals(name) && "none".equals(position) && "none".equals(group) && "none".equals(fillc))
			gd.setSmartRecording(true);
		gd.addWindowListener(this);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		String position2 = "";
		String group2 = "";
		if (showName) {
			name = gd.getNextString();
			if (!isRange) roi.setName(name.length()>0?name:null);
			position2 = gd.getNextString();
			group2 = gd.getNextString();
		}
		linec = gd.getNextString();
		if (!isPoint)
			strokeWidth2 = gd.getNextNumber();
		if (isText) {
			angle = gd.getNextNumber();
			justification = gd.getNextChoiceIndex();
		}
		if (!isLine) {
			if (isPoint) {
				int index = gd.getNextChoiceIndex();
				((PointRoi)roi).setPointType(index);
				index = gd.getNextChoiceIndex();
				((PointRoi)roi).setSize(index);
			} else
				fillc = gd.getNextString();
		}
		if (askShowOnAllSlices) {
			boolean overlayOnAllSlices = gd.getNextBoolean();
			if (overlayOnAllSlices)
				roi.setPosition(0);
			else if (roi.getPosition() == 0)
				roi.setPosition(imp);
		}
		boolean applyToOverlay = false;
		boolean newOverlay = addToOverlay ? gd.getNextBoolean() : false;
		if (overlayOptions) {
			setPositions = gd.getNextBoolean();
			if (overlay!=null) {
				applyToOverlay = gd.getNextBoolean();
				boolean labels = gd.getNextBoolean();
				boolean hideOverlay = gd.getNextBoolean();
				if (hideOverlay && imp!=null) {
					if (!imp.getHideOverlay())
						imp.setHideOverlay(true);
				} else {
					overlay.drawLabels(labels);
					Analyzer.drawLabels(labels);
					overlay.drawBackgrounds(true);
					if (imp.getHideOverlay())
						imp.setHideOverlay(false);
					if (!applyToOverlay && imp!=null)
						imp.draw();
				}
			}
			roi.setPosition(setPositions?1:0);
		}
		if (isText)
			antialias = gd.getNextBoolean();
		if (showListCoordinates) {
			listCoordinates = gd.getNextBoolean();
			if (nProperties>0)
				listProperties = gd.getNextBoolean();
		}
		strokeColor = Colors.decode(linec, null);
		fillColor = Colors.decode(fillc, null);
		if (isText) {
			TextRoi troi = (TextRoi)roi;
			Font font = troi.getCurrentFont();
			if (strokeWidth2!=strokeWidth) {
				font = new Font(font.getName(), font.getStyle(), (int)strokeWidth2);
				troi.setCurrentFont(font);
			}
			troi.setAngle(angle);
			if (justification!=troi.getJustification())
				troi.setJustification(justification);
			troi.setAntiAlias(antialias);
			if (!isRange) troi.setText(gd.getNextText());
		} else if (strokeWidth2!=strokeWidth)
			roi.setStrokeWidth((float)strokeWidth2);
		roi.setFillColor(fillColor);
		roi.setStrokeColor(strokeColor);
		if (showName) {
			setPosition(roi, position, position2);
			setGroup(roi, group, group2);
		}
		if (newOverlay) roi.setName("new-overlay");
		if (applyToOverlay) {
			if (imp==null || overlay==null)
				return true;
			Undo.setup(Undo.OVERLAY, imp);
			Roi[] rois = overlay.toArray();
			for (int i=0; i<rois.length; i++) {
				if (strokeColor != null)
					rois[i].setStrokeColor(strokeColor);
				if (strokeWidth2!=strokeWidth)
					rois[i].setStrokeWidth((float)strokeWidth2);
				rois[i].setFillColor(fillColor);
				if (setPositions) {
					if (rois[i].getPosition()==0 && !rois[i].hasHyperStackPosition())
						rois[i].setPosition(imp);
				} else
					rois[i].setPosition(0);
			}
			imp.draw();
			imp.getProcessor(); // needed for correct recordering
		}
		if (listCoordinates) {
			if (showPointCounts && (roi instanceof PointRoi))
				((PointRoi)roi).displayCounts();
			else
				listCoordinates(roi);
		}
		if (listProperties && nProperties>0)
			listProperties(roi);
		return true;
	}
		
	private void setPosition(Roi roi, String pos1, String pos2) {
		if (pos1.equals(pos2))
			return;
		if (pos2.equals("none") || pos2.equals("0")) {
			roi.setPosition(0);
			return;
		}
		String[] positions = Tools.split(pos2, " ,");
		if (positions.length==1) {
			double stackPos = Tools.parseDouble(positions[0]);
			if (!Double.isNaN(stackPos))
				roi.setPosition((int)stackPos);
			return;
		}
		if (positions.length==3) {
			int[] pos = new int[3];
			for (int i=0; i<3; i++) {
				double dpos = Tools.parseDouble(positions[i]);
				if (Double.isNaN(dpos))
					return;
				else
					pos[i] = (int)dpos;
			}
			roi.setPosition(pos[0], pos[1], pos[2]);
			return;
		}
	}
	
	private void setGroup(Roi roi, String group1, String group2) {
		if (group1.equals(group2))
			return;
		if (group2.equals("none") || group2.equals("0")) {
			roi.setGroup(0);
			return;
		}
		double group = Tools.parseDouble(group2);
		if (!Double.isNaN(group))
			roi.setGroup((int)group);
	}
		
	public boolean showImageDialog(String name) {
		ImageRoi iRoi = (ImageRoi)roi;
		boolean zeroTransparent =  iRoi.getZeroTransparent();
		GenericDialog gd = new GenericDialog("Image ROI Properties");
		gd.addStringField("Name:", name, 15);
		gd.addNumericField("Opacity (0-100%):", iRoi.getOpacity()*100.0, 0);
		gd.addCheckbox("Transparent background", zeroTransparent);
		if (addToOverlay)
			gd.addCheckbox("New Overlay", false);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		name = gd.getNextString();
		roi.setName(name.length()>0?name:null);
		double opacity = gd.getNextNumber()/100.0;
		iRoi.setOpacity(opacity);
		boolean zeroTransparent2 = gd.getNextBoolean();
		if (zeroTransparent!=zeroTransparent2)
			iRoi.setZeroTransparent(zeroTransparent2);
		boolean newOverlay = addToOverlay?gd.getNextBoolean():false;
		if (newOverlay) roi.setName("new-overlay");
		return true;
	}
	
	void listCoordinates(Roi roi) {
		if (roi==null) return;
		boolean allIntegers = true;
		FloatPolygon fp = roi.getFloatPolygon();
		ImagePlus imp = roi.getImage();
		String title = "Coordinates";
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			int height = imp.getHeight();
			for (int i=0; i<fp.npoints; i++) {
				fp.xpoints[i] = (float)cal.getX(fp.xpoints[i]);
				fp.ypoints[i] = (float)cal.getY(fp.ypoints[i], height);
			}
			if (cal.pixelWidth!=1.0 || cal.pixelHeight!=1.0)
				allIntegers = false;
			title = imp.getTitle();
		}
		if (allIntegers) {
			for (int i=0; i<fp.npoints; i++) {
				if ((int)fp.xpoints[i]!=fp.xpoints[i] || (int)fp.ypoints[i]!=fp.ypoints[i]) {
					allIntegers = false;
					break;
				}
			}
		}
		ResultsTable rt = new ResultsTable();
		rt.setPrecision(allIntegers?0:Analyzer.getPrecision());
		for (int i=0; i<fp.npoints; i++) {
			rt.incrementCounter();
			rt.addValue("X", fp.xpoints[i]);
			rt.addValue("Y", fp.ypoints[i]);
		}
		rt.show("XY_"+title);
	}
	
	void listProperties(Roi roi) {
		String props = roi.getProperties();
		if (props==null) return;
		props = props.replaceAll(": ", "\t");
		new TextWindow("Properties", "Key\tValue", props, 300, 300);
	}
	
	public void textValueChanged(TextEvent e) {
		if (groupName==null)
			return;
		TextField tf = (TextField) e.getSource();
		String str = tf.getText();
		double group = Tools.parseDouble(str, Double.NaN);
		if (!Double.isNaN(group) && group>=0 && group<=255) {
			roi.setGroup((int)group);
			String name = Roi.getGroupName((int)group);
			if (name==null)
				name="unnamed";
			if (group==0)
				name = "";
			groupName.setText(" "+name);
			Color strokeColor = roi.getStrokeColor();
			colorField.setText(Colors.colorToString(strokeColor));
		} else
			groupName.setText("");
	}
	
	public void windowActivated(WindowEvent e) {
		if (groupName!=null) {
			String gname = Roi.getGroupName(roi.getGroup());
			groupName.setText(gname!=null?" "+gname:"");  // add space to separate label from field
		}
	}
	
	public void windowClosing(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}
	public void windowClosed(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
    
}
