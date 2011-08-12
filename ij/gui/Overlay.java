package ij.gui;
import java.awt.*;
import java.util.Vector;
import ij.*;
import ij.process.ImageProcessor;

/** An Overlay is a list of Rois that can be drawn non-destructively on an Image. */
public class Overlay {
	private Vector list;
    private boolean label;
    private boolean drawNames;
    private Font labelsFont;
    
    /** Constructs an empty Overlay. */
    public Overlay() {
    	list = new Vector();
    }
    
    /** Constructs an Overlay and adds the specified Roi. */
    public Overlay(Roi roi) {
    	list = new Vector();
    	list.add(roi);
    }

    /** Adds an Roi to this Overlay. */
    public void add(Roi roi) {
    	list.add(roi);
    }
    
    /* Adds a Shape to this Overlay. */
    //public void add(Shape shape, Color color, BasicStroke stroke) {
	//	Roi roi = new ShapeRoi(shape);
	//	roi.setStrokeColor(color);
	//	roi.setStroke(stroke);
    //	list.add(roi);
    //}

    /* Adds a String to this Overlay. */
    //public void add(String text, int x, int y, Color color, Font font) {
	//	TextRoi roi = new TextRoi(x, y-font.getSize(), text, font);
	//	roi.setStrokeColor(color);
	//	list.add(roi);
    //}
    
    /** Adds an Roi to this Overlay. */
    public void addElement(Roi roi) {
    	list.add(roi);
    }

    /** Removes the Roi with the specified index from this Overlay. */
    public void remove(int index) {
    	list.remove(index);
    }
    
    /** Removes the specified Roi from this Overlay. */
    public void remove(Roi roi) {
    	list.remove(roi);
    }

   /** Removes all the Rois in this Overlay. */
    public void clear() {
    	list.clear();
    }

    /** Returns the Roi with the specified index. */
    public Roi get(int index) {
    	return (Roi)list.get(index);
    }
    
    /** Returns the number of Rois in this Overlay. */
    public int size() {
    	return list.size();
    }
    
    /** Returns on array containing the Rois in this Overlay. */
    public Roi[] toArray() {
    	Roi[] array = new Roi[list.size()];
    	return (Roi[])list.toArray(array);
    }
    
    /** Sets the stroke color of all the Rois in this overlay. */
    public void setStrokeColor(Color color) {
		Roi[] rois = toArray();
		for (int i=0; i<rois.length; i++)
			rois[i].setStrokeColor(color);
	}

    /** Sets the fill color of all the Rois in this overlay. */
    public void setFillColor(Color color) {
		Roi[] rois = toArray();
		for (int i=0; i<rois.length; i++)
			rois[i].setFillColor(color);
	}

    /** Moves all the Rois in this overlay. */
    public void translate(int dx, int dy) {
		Roi[] rois = toArray();
		for (int i=0; i<rois.length; i++) {
			Rectangle r = rois[i].getBounds();
			rois[i].setLocation(r.x+dx, r.y+dy);
		}
	}

    /** Draws outlines of the Rois in this Overlay on the specified
    	ImageProcessor using the current color and line width of 'ip'. */
    //public void draw(ImageProcessor ip) {
	//	Roi[] rois = toArray();
	//	for (int i=0; i<rois.length; i++)
	//		rois[i].drawPixels(ip);
	//}
	
	/** Returns a clone of this Overlay. */
	public Overlay duplicate() {
		Roi[] rois = toArray();
		Overlay overlay2 = new Overlay();
		for (int i=0; i<rois.length; i++)
			overlay2.add((Roi)rois[i].clone());
		overlay2.drawLabels(label);
		return overlay2;
	}

	public String toString() {
    	return list.toString();
    }
    
    public void drawLabels(boolean b) {
    	label = b;
    }
    
    public boolean getDrawLabels() {
    	return label;
    }
    
    public void drawNames(boolean b) {
    	drawNames = b;
    }
    
    public boolean getDrawNames() {
    	return drawNames;
    }

    public void setLabelsFont(Font font) {
    	labelsFont = font;
    }
    
    public Font getLabelsFont() {
    	return labelsFont;
    }

    void setVector(Vector v) {list = v;}
        
    Vector getVector() {return list;}
    
}
