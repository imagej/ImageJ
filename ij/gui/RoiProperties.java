package ij.gui;
import ij.*;
import ij.plugin.Colors;
import ij.io.RoiDecoder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;


 /** Displays a dialog that allows the user to specify ROI properties such as color and line width. */
public class RoiProperties implements ItemListener {
	private Roi roi;
	private String title;
	private boolean showName = true;
	private boolean addToOverlay;
	private boolean overlayOptions;
	private boolean existingOverlay;
	private boolean showLabels;
	private boolean showNames;
	private boolean drawBackgrounds = true;
	private String labelColor;
	private boolean overlayShowLabels;
	private boolean setPositions;
	private static final String[] justNames = {"Left", "Center", "Right"};
	private Vector checkboxes;

	/** Constructs a ColorChooser using the specified title and initial color. */
	public RoiProperties(String title, Roi roi) {
		if (roi==null)
			throw new IllegalArgumentException("ROI is null");
		this.title = title;
		showName = title.startsWith("Prop");
		addToOverlay = title.equals("Add to Overlay");
		overlayOptions = title.equals("Overlay Options");
		ImagePlus imp = WindowManager.getCurrentImage();
		if (overlayOptions) {
			int options = roi.getOverlayOptions();
			showLabels = (options&RoiDecoder.OVERLAY_LABELS)!=0;
			showNames = (options&RoiDecoder.OVERLAY_NAMES)!=0;
			drawBackgrounds = (options&RoiDecoder.OVERLAY_BACKGROUNDS)!=0;
			labelColor = decodeColor(roi.getOverlayLabelColor(), Color.white);
			Overlay overlay = imp!=null?imp.getOverlay():null;
			setPositions = roi.getPosition()!=0;
			if (overlay!=null) {
				existingOverlay = true;
				showLabels = overlay.getDrawLabels();
				showNames = overlay.getDrawNames();
				drawBackgrounds = overlay.getDrawBackgrounds();
				labelColor = decodeColor(overlay.getLabelColor(), Color.white);
			}
		}
		this.roi = roi;
	}
	
	private String decodeColor(Color color, Color defaultColor) {
		if (color==null)
			color = defaultColor;
		String str = "#"+Integer.toHexString(color.getRGB());
		if (str.length()==9 && str.startsWith("#ff"))
			str = "#"+str.substring(3);
		String lc = Colors.hexToColor(str);
		if (lc!=null) str = lc;
		return str;
	}
	
	/** Displays the dialog box and returns 'false' if the user cancels it. */
	public boolean showDialog() {
		Color strokeColor = null;
		Color fillColor = null;
		double strokeWidth = 1.0;
		String name= roi.getName();
		boolean isRange = name!=null && name.startsWith("range: ");
		String nameLabel = isRange?"Range:":"Name:";
		if (isRange) name = name.substring(7);
		if (name==null) name = "";
		if (!isRange && (roi instanceof ImageRoi))
			return showImageDialog(name);
		if (roi.getStrokeColor()!=null) strokeColor = roi.getStrokeColor();
		if (strokeColor==null) strokeColor = Roi.getColor();
		if (roi.getFillColor()!=null) fillColor = roi.getFillColor();
		double width = roi.getStrokeWidth();
		if (width>1.0) strokeWidth = width;
		boolean isText = roi instanceof TextRoi;
		boolean isLine = roi.isLine();
		int justification = TextRoi.LEFT;
		if (isText) {
			TextRoi troi = (TextRoi)roi;
			Font font = troi.getCurrentFont();
			strokeWidth = font.getSize();
			justification = troi.getJustification();
		}
		String linec = strokeColor!=null?"#"+Integer.toHexString(strokeColor.getRGB()):"none";
		if (linec.length()==9 && linec.startsWith("#ff"))
			linec = "#"+linec.substring(3);
		String lc = Colors.hexToColor(linec);
		if (lc!=null) linec = lc;
		String fillc = fillColor!=null?"#"+Integer.toHexString(fillColor.getRGB()):"none";
		if (IJ.isMacro()) fillc = "none";
		int digits = (int)strokeWidth==strokeWidth?0:1;
		GenericDialog gd = new GenericDialog(title);
		if (showName)
			gd.addStringField(nameLabel, name, 15);
		gd.addStringField("Stroke color: ", linec);
		if (isText) {
			gd.addNumericField("Font size:", strokeWidth, digits);
			gd.addChoice("Justification:", justNames, justNames[justification]);
		} else
			gd.addNumericField("Width:", strokeWidth, digits);
		if (!isLine) {
			gd.addMessage("");
			gd.addStringField("Fill color: ", fillc);
		}
		if (addToOverlay)
			gd.addCheckbox("New overlay", false);
		if (overlayOptions) {
			if (existingOverlay)
				gd.addCheckbox("Apply to current overlay", false);
			gd.addCheckbox("Set stack positions", setPositions);
			gd.addMessage("Labeling options:");
			gd.setInsets(0, 30, 0);
			gd.addCheckbox("Show labels", showLabels);
			gd.setInsets(0, 30, 0);
			gd.addCheckbox("Use names as labels", showNames);
			gd.setInsets(0, 30, 3);
			gd.addCheckbox("Draw backgrounds", drawBackgrounds);
			gd.setInsets(0, 30, 0);
			gd.addStringField("Label color:", labelColor, 6);
			checkboxes = gd.getCheckboxes();
			((Checkbox)checkboxes.elementAt(existingOverlay?3:2)).addItemListener(this);
		}
		gd.showDialog();
		if (gd.wasCanceled()) return false;
		if (showName) {
			name = gd.getNextString();
			if (!isRange) roi.setName(name.length()>0?name:null);
		}
		linec = gd.getNextString();
		strokeWidth = gd.getNextNumber();
		if (isText)
			justification = gd.getNextChoiceIndex();
		if (!isLine)
			fillc = gd.getNextString();
		boolean applyToOverlay = false;
		boolean newOverlay = addToOverlay?gd.getNextBoolean():false;
		if (overlayOptions) {
			boolean showLabels2 = showLabels;
			boolean showNames2 = showNames;
			boolean drawBackgrounds2 = drawBackgrounds;
			String labelColor2 = labelColor;
			if (existingOverlay)
				applyToOverlay = gd.getNextBoolean();
			boolean sp = setPositions;
			boolean sl =showLabels;
			boolean sn = showNames;
			boolean db = drawBackgrounds;
			String lcolor = labelColor;
			setPositions = gd.getNextBoolean();
			showLabels = gd.getNextBoolean();
			showNames = gd.getNextBoolean();
			drawBackgrounds = gd.getNextBoolean();
			labelColor = gd.getNextString();
			Color color = Colors.decode(labelColor, Color.black);
			if (showNames) showLabels = true;
			ImagePlus imp = WindowManager.getCurrentImage();
			Overlay overlay = imp!=null?imp.getOverlay():null;
			boolean changes = setPositions!=sp || showLabels!=sl || sn!=showNames
				|| drawBackgrounds!=db || !labelColor.equals(lcolor);
			if (changes) {
				if (overlay!=null) {
					overlay.drawLabels(showLabels);
					overlay.drawNames(showNames);
					overlay.drawBackgrounds(drawBackgrounds);
					overlay.setLabelColor(color);
					if (!applyToOverlay) imp.draw();
				}
				roi.setPosition(setPositions?1:0);
				int options = 0;
				if (showLabels)
					options |= RoiDecoder.OVERLAY_LABELS;
				if (showNames)
					options |= RoiDecoder.OVERLAY_NAMES;
				if (drawBackgrounds)
					options |= RoiDecoder.OVERLAY_BACKGROUNDS;
				roi.setOverlayOptions(options);
				roi.setOverlayLabelColor(color);
			}
		}
		strokeColor = Colors.decode(linec, Roi.getColor());
		fillColor = Colors.decode(fillc, null);
		if (isText) {
			TextRoi troi = (TextRoi)roi;
			Font font = troi.getCurrentFont();
			if ((int)strokeWidth!=font.getSize()) {
				font = new Font(font.getName(), font.getStyle(), (int)strokeWidth);
				troi.setCurrentFont(font);
			}
			if (justification!=troi.getJustification())
				troi.setJustification(justification);
		} else if (addToOverlay||strokeWidth>1.0)
				roi.setStrokeWidth((float)strokeWidth);
		roi.setStrokeColor(strokeColor);
		roi.setFillColor(fillColor);
		if (newOverlay) roi.setName("new-overlay");
		if (applyToOverlay) {
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp==null)
				return true;
			Overlay overlay = imp.getOverlay();
			if (overlay==null)
				return true;
			Roi[] rois = overlay.toArray();
			for (int i=0; i<rois.length; i++) {
				rois[i].setStrokeColor(strokeColor);
				rois[i].setStrokeWidth((float)strokeWidth);
				rois[i].setFillColor(fillColor);
			}
			imp.draw();
		}
		//if (strokeWidth>1.0 && !roi.isDrawingTool())
		//	Line.setWidth(1);
		return true;
	}
		
	public boolean showImageDialog(String name) {
		GenericDialog gd = new GenericDialog(title);
		gd.addStringField("Name:", name, 15);
		gd.addNumericField("Opacity (0-100%):", ((ImageRoi)roi).getOpacity()*100.0, 0);
		if (addToOverlay)
			gd.addCheckbox("New Overlay", false);
		gd.showDialog();
		if (gd.wasCanceled()) return false;
		name = gd.getNextString();
		roi.setName(name.length()>0?name:null);
		double opacity = gd.getNextNumber()/100.0;
		((ImageRoi)roi).setOpacity(opacity);
		boolean newOverlay = addToOverlay?gd.getNextBoolean():false;
		if (newOverlay) roi.setName("new-overlay");
		return true;
	}
	
	public void itemStateChanged(ItemEvent e) {
		Checkbox usNames = (Checkbox)checkboxes.elementAt(existingOverlay?3:2);
		if (usNames.getState())
			((Checkbox)checkboxes.elementAt(existingOverlay?2:1)).setState(true);
	}

}
