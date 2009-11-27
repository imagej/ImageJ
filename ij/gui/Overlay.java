package ij.gui;
import java.awt.*;
import java.util.ArrayList;
import ij.*;

public class Overlay {
	private ArrayList list;
    private boolean label;
    private Color color;
	private BasicStroke stroke;
    
    public Overlay() {
    	list = new ArrayList();
    }
    
    public void add(Roi roi) {
    	list.add(roi);
    }

    public void remove(int index) {
    	list.remove(index);
    }
    
    public void remove(Roi roi) {
    	list.remove(roi);
    }

    public void remove(int x, int y) {
    	Roi roi = get(x, y);
    	if (roi!=null) remove(roi);
    }

    public void clear() {
    	list.clear();
    }

    public Roi get(int i) {
    	return (Roi)list.get(i);
    }
    
    public synchronized Roi get(int x, int y) {
     	for (int i=0; i<list.size(); i++) {
    		Roi roi = (Roi)list.get(i);
    		if (roi==null) return null;
    		Rectangle bounds = roi.getBounds();
    		if (bounds.x==x && bounds.y==y)
    			return roi;
    	}
    	return null;
    }

    public int size() {
    	return list.size();
    }
    
    public Roi[] toArray() {
    	return (Roi[])list.toArray();
    }
    
    public String toString() {
    	return list.toString();
    }
    
    public void drawLabels(boolean b) {
    	label = b;
    }

    ArrayList getArrayList() {
    	return list;
    }
    
    Color getColor() {
    	return color;
    }
    
    void setColor(Color color) {
    	this.color = color;
    }
    
    boolean getDrawLabels() {
    	return label;
    }
    
    BasicStroke getStroke() {
    	return stroke;
    }
    
    void setStroke(BasicStroke stroke) {
    	this.stroke = stroke;
    }
    
}
