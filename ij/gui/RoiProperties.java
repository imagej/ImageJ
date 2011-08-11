package ij.gui;
import ij.*;
import ij.plugin.Colors;
import java.awt.*;

 /** Displays a dialog that allows the user to specify ROI properties such as color and line width. */
public class RoiProperties {
	private Roi roi;
	private String title;
	private boolean showName = true;
	private boolean addToOverlay;
	private boolean overlayOptions;
	private boolean existingOverlay;
	private boolean showLabels;
	private boolean setPositions;
	private static final String[] justNames = {"Left", "Center", "Right"};

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
    		if (imp!=null && imp.getOverlay()!=null)
    			existingOverlay = true;
    		showLabels = roi.getNumber()!=0;
    		setPositions = roi.getPosition()!=0;
    	}
    	this.roi = roi;
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
			gd.addCheckbox("Show numeric labels", showLabels);
			gd.addCheckbox("Set stack positions", setPositions);
			if (existingOverlay)
				gd.addCheckbox("Apply to current overlay", false);
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
		boolean showLabelsChanged = false;
		if (overlayOptions) {
			boolean showLabels2 = showLabels;
			showLabels = gd.getNextBoolean();
			setPositions = gd.getNextBoolean();
			if (existingOverlay)
				applyToOverlay = gd.getNextBoolean();
			if (showLabels!=showLabels2)
				showLabelsChanged = true;
			roi.setNumber(showLabels?1:0);
			roi.setPosition(setPositions?1:0);
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
				if (showLabels)
					rois[i].setNumber(i+1);
				else
					rois[i].setNumber(0);
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
    
}
