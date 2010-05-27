package ij.gui;
import java.awt.*;
import java.util.Vector;
import ij.*;
import ij.process.ImageProcessor;

public class Overlay {
	private Vector list;
    private boolean label;
    
    public Overlay() {
    	list = new Vector();
    }
    
    public Overlay(Roi roi) {
    	list = new Vector();
    	list.add(roi);
    }

    public void add(Roi roi) {
    	list.add(roi);
    }

    public void addElement(Roi roi) {
    	list.add(roi);
    }

    public void remove(int index) {
    	list.remove(index);
    }
    
    public void remove(Roi roi) {
    	list.remove(roi);
    }

    public void clear() {
    	list.clear();
    }

    public Roi get(int i) {
    	return (Roi)list.get(i);
    }
    
    public int size() {
    	return list.size();
    }
    
    public Roi[] toArray() {
    	Roi[] array = new Roi[list.size()];
    	return (Roi[])list.toArray(array);
    }
    
    public void drawPixels(ImageProcessor ip) {
		Roi[] rois = toArray();
		for (int i=0; i<rois.length; i++)
			rois[i].drawPixels(ip);
	}
	
	public String toString() {
    	return list.toString();
    }
    
    public void drawLabels(boolean b) {
    	label = b;
    }
    
    public void hide(int index1, int index2) {
    	int n = list.size();
    	if (index1<0 || index2>=n || index2<index1)
    		return;
    	for (int i=index1; i<=index2; i++)
    		get(i).hide();
    }

    boolean getDrawLabels() {return label;}
    
    void setVector(Vector v) {list = v;}
        
    Vector getVector() {return list;}
    
}
