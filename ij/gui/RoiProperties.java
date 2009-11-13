package ij.gui;
import ij.*;
import ij.plugin.Colors;
import java.awt.*;

 /** Displays a dialog that allows the user to specify ROI properties such as color and line width. */
public class RoiProperties {
	private Roi roi;
	private String title;
	private boolean showName = true;

    /** Constructs a ColorChooser using the specified title and initial color. */
    public RoiProperties(String title, Roi roi) {
    	if (roi==null)
    		throw new IllegalArgumentException("ROI is null");
    	this.title = title;
    	showName = title.startsWith("Prop");
    	this.roi = roi;
    }
    
    /** Displays the dialog box and returns 'false' if the user cancels the dialog. */
    public boolean showDialog() {
    	Color strokeColor = null;
    	Color fillColor = null;
    	int strokeWidth = 1;
    	String name= roi.getName();
    	boolean isRange = name!=null && name.startsWith("range: ");
    	String nameLabel = isRange?"Range:":"Name:";
    	if (isRange) name = name.substring(7);
    	if (name==null) name = "";
		if (roi.getStrokeColor()!=null) strokeColor = roi.getStrokeColor();
		if (strokeColor==null) strokeColor = Roi.getColor();
		if (roi.getFillColor()!=null) fillColor = roi.getFillColor();
		int width = roi.getStrokeWidth();
		if (width>1) strokeWidth = width;
		boolean isText = roi instanceof TextRoi;
		if (isText) {
			Font font = ((TextRoi)roi).getCurrentFont();
			strokeWidth = font.getSize();
		}
		String linec = strokeColor!=null?"#"+Integer.toHexString(strokeColor.getRGB()):"none";
		if (linec.length()==9 && linec.startsWith("#ff"))
			linec = "#"+linec.substring(3);
		String lc = Colors.hexToColor(linec);
		if (lc!=null) linec = lc;
		String fillc = fillColor!=null?"#"+Integer.toHexString(fillColor.getRGB()):"none";
		if (IJ.isMacro()) fillc = "none";
		GenericDialog gd = new GenericDialog(title);
		if (showName)
			gd.addStringField(nameLabel, name, 15);
		gd.addStringField("Stroke Color: ", linec);
		gd.addNumericField(isText?"Font Size":"Width:", strokeWidth, 0);
		gd.addMessage("");
		gd.addStringField("Fill Color: ", fillc);
		gd.showDialog();
		if (gd.wasCanceled()) return false;
		if (showName) {
			name = gd.getNextString();
			if (!isRange) roi.setName(name.length()>0?name:null);
		}
		linec = gd.getNextString();
		strokeWidth = (int)gd.getNextNumber();
		fillc = gd.getNextString();
		strokeColor = Colors.decode(linec, Roi.getColor());
		fillColor = Colors.decode(fillc, null);
		if (isText) {
			Font font = ((TextRoi)roi).getCurrentFont();
			if (strokeWidth!=font.getSize()) {
				font = new Font(font.getName(), font.getStyle(), strokeWidth);
				((TextRoi)roi).setCurrentFont(font);
			}
		} else
			roi.setStrokeWidth(strokeWidth);
		roi.setStrokeColor(strokeColor);
		roi.setFillColor(fillColor);
		if (strokeWidth>1)
			Line.setWidth(1);
		return true;
    }
        
}
