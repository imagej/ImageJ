package ij.gui;
import java.awt.*;
import java.util.Vector;
import java.awt.geom.Rectangle2D;
import java.util.*;
import ij.*;
import ij.process.ImageProcessor;
import ij.plugin.filter.*;
import ij.plugin.*;
import ij.measure.ResultsTable;

/** An Overlay is a list of ROIs that can be drawn non-destructively on an Image. */
public class Overlay implements Iterable<Roi> {
	private Vector<Roi> list;
    private boolean label;
    private boolean drawNames;
    private boolean drawBackgrounds;
    private Color labelColor;
    private Font labelFont;
    private boolean scalableLabels;
    private boolean isCalibrationBar;
    private boolean selectable = true;
    private boolean draggable = true;
    private double minStrokeWidth = -1;
    
    /** Constructs an empty Overlay. */
    public Overlay() {
    	list = new Vector<Roi>();
    }
    
    /** Constructs an Overlay and adds the specified ROI. */
    public Overlay(Roi roi) {
    	list = new Vector<Roi>();
    	if (roi!=null)
    		list.add(roi);
    }

    /** Adds an ROI to this Overlay. */
    public void add(Roi roi) {
    	if (roi!=null) {
    		if (minStrokeWidth>=0)
    			roi.setMinStrokeWidth(minStrokeWidth);
    		list.add(roi);
    	}
    }
        
    /** Adds an ROI to this Overlay using the specified name. */
	public void add(Roi roi, String name) {
		roi.setName(name);
		add(roi);
	}

    /** Adds the ROIs in 'overlay2' to this overlay. */
	public Overlay add(Overlay overlay2) {
		for (int i=0; i<overlay2.size(); i++)
			add(overlay2.get(i));
		return this;
	}

    /** Adds an ROI to this Overlay. */
    public void addElement(Roi roi) {
    	if (roi!=null)
    		list.add(roi);
    }

    /** Replaces the ROI at the specified index. */
    public void set(Roi roi, int index) {
    	if (index<0 || index>=list.size())
    		throw new IllegalArgumentException("set: index out of range");
    	if (roi!=null)
    		list.set(index, roi);
    }

    /** Removes the ROI with the specified index from this Overlay. */
    public void remove(int index) {
    	if (index>=0)
    		list.remove(index);
    }
    
    /** Removes the specified ROI from this Overlay. */
    public void remove(Roi roi) {
    	list.remove(roi);
    }

    /** Removes all ROIs that have the specified name. */
	public void remove(String name) {
		if (name==null) return;
		for (int i=size()-1; i>=0; i--) {
			if (name.equals(get(i).getName()))
				remove(i);
		}
	}

   /** Removes all the ROIs in this Overlay. */
    public void clear() {
    	list.clear();
    }

    /** Returns the ROI with the specified index or null if the index is invalid. */
    public Roi get(int index) {
    	try {
    		return (Roi)list.get(index);
    	} catch(Exception e) {
    		return null;
    	}
    }
    
    /** Returns the ROI with the specified name or null if not found. */
    public Roi get(String name) {
    	int index = getIndex(name);
    	if (index==-1)
    		return null;
    	else
    		return get(index);   		
    }

    /** Returns the index of the ROI with the specified name, or -1 if not found. */
    public int getIndex(String name) {
    	if (name==null) return -1;
    	Roi[] rois = toArray();
		for (int i=rois.length-1; i>=0; i--) {
			if (name.equals(rois[i].getName()))
				return i;
		}
		return -1;
    }
    
    /** Returns the index of the last ROI that contains the point (x,y)
    	or null if no ROI contains the point. */
    public int indexAt(int x, int y) {
     	Roi[] rois = toArray();
		for (int i=rois.length-1; i>=0; i--) {
			if (contains(rois[i],x,y))
				return i;
		}
		return -1;
    }
    
	private boolean contains(Roi roi, int x, int y) {
		if (roi==null) return false;
		if (roi instanceof Line)
			return  (((Line)roi).getFloatPolygon(10)).contains(x,y);
		else
			return roi.contains(x,y);
	}
	
    /** Returns 'true' if this Overlay contains the specified ROI. */
    public boolean contains(Roi roi) {
    	return list.contains(roi);
    }

    /** Returns the number of ROIs in this Overlay. */
    public int size() {
    	return list.size();
    }
    
    /** Returns on array containing the ROIs in this Overlay. */
    public Roi[] toArray() {
    	Roi[] array = new Roi[list.size()];
    	return (Roi[])list.toArray(array);
    }
    
    /** Returns on array containing the ROIs with the specified indexes. */
    public Roi[] toArray(int[] indexes) {
		ArrayList rois = new ArrayList();
		for (int i=0; i<indexes.length; i++) {
			if (indexes[i]>=0 && indexes[i]<size())
				rois.add(get(indexes[i]));
		}
		return (Roi[])rois.toArray(new Roi[rois.size()]);
	}

    /** Sets the stroke color of all the ROIs in this overlay. */
    public void setStrokeColor(Color color) {
		for (int i=0; i<size(); i++)
			get(i).setStrokeColor(color);
	}

    /** Sets the stroke width of all the ROIs in this overlay. */
    public void setStrokeWidth(Double width) {
		for (int i=0; i<size(); i++)
			get(i).setStrokeWidth(width);
	}

    /** Sets the fill color of all the ROIs in this overlay. */
    public void setFillColor(Color color) {
		for (int i=0; i<size(); i++)
			get(i).setFillColor(color);
	}

	/** Moves all the ROIs in this overlay. */
	public void translate(int dx, int dy) {
		for (int i=0; i<size(); i++)
			get(i).translate(dx,dy);
	}

	/** Moves all the ROIs in this overlay.
	* Marcel Boeglin, October 2013
	*/
	public void translate(double dx, double dy) {
		for (int i=0; i<size(); i++)
			get(i).translate(dx,dy);
	}
	
	/** Measures the ROIs in this overlay on the specified image
	* and returns the results as a ResultsTable.
	*/
	public ResultsTable measure(ImagePlus imp) {
		ResultsTable rt = new ResultsTable();
		rt.showRowNumbers(true);
		Analyzer analyzer = new Analyzer(imp, rt);
		for (int i=0; i<size(); i++) {
			Roi roi = get(i);
			imp.setRoi(roi);
			analyzer.measure();
		}
		imp.deleteRoi();
		return rt;
	}

	/*
	* Duplicate the elements of this overlay which  
	* intersect with the rectangle 'bounds'.
	* Author: Wilhelm Burger
	* Author: Marcel Boeglin
	*/
	public Overlay crop(Rectangle bounds) {
		if (bounds==null)
			return duplicate();
		Overlay overlay2 = create();
		Roi[] allRois = toArray();
		for (Roi roi: allRois) {
			Rectangle roiBounds = roi.getBounds();
			if (roiBounds.width==0) roiBounds.width=1;
			if (roiBounds.height==0) roiBounds.height=1;
			if (bounds.intersects(roiBounds))
				overlay2.add((Roi)roi.clone());
		}
		int dx = bounds.x>0?bounds.x:0;
		int dy = bounds.y>0?bounds.y:0;
		if (dx>0 || dy>0)
			overlay2.translate(-dx, -dy);
		return overlay2;
	}

	/** Removes ROIs having positions outside of the  
	* interval defined by firstSlice and lastSlice.
	* Marcel Boeglin, September 2013
	*/
	public void crop(int firstSlice, int lastSlice) {
		for (int i=size()-1; i>=0; i--) {
			Roi roi = get(i);
			int position = roi.getPosition();
			if (position>0) {
				if (position<firstSlice || position>lastSlice)
					remove(i);
				else
					roi.setPosition(position-firstSlice+1);
			}
		}
	}

	/** Removes ROIs having a C, Z or T coordinate outside the volume
	* defined by firstC, lastC, firstZ, lastZ, firstT and lastT.
	* Marcel Boeglin, September 2013
	*/
	public void crop(int firstC, int lastC, int firstZ, int lastZ, int firstT, int lastT) {
		int nc = lastC-firstC+1, nz = lastZ-firstZ+1, nt = lastT-firstT+1;
		boolean toCStack = nz==1 && nt==1;
		boolean toZStack = nt==1 && nc==1;
		boolean toTStack = nc==1 && nz==1;
		Roi roi;
		int c, z, t, c2, z2, t2;
		for (int i=size()-1; i>=0; i--) {
			roi = get(i);
			c = roi.getCPosition();
			z = roi.getZPosition();
			t = roi.getTPosition();
			c2 = c-firstC+1;
			z2 = z-firstZ+1;
			t2 = t-firstT+1;
			if (toCStack)
				roi.setPosition(c2);
			else if (toZStack)
				roi.setPosition(z2);
			else if (toTStack)
				roi.setPosition(t2);
			else
				roi.setPosition(c2, z2, t2);
			if ((c2<1||c2>nc) && c>0 || (z2<1||z2>nz) && z>0 || (t2<1||t2>nt) && t>0)
				remove(i);
		}
	}

    /** Returns the bounds of this overlay. */
    /*
    public Rectangle getBounds() {
    	if (size()==0)
    		return new Rectangle(0,0,0,0);
    	int xmin = Integer.MAX_VALUE;
    	int xmax = -Integer.MAX_VALUE;
    	int ymin = Integer.MAX_VALUE;
    	int ymax = -Integer.MAX_VALUE;
		Roi[] rois = toArray();
		for (int i=0; i<rois.length; i++) {
			Rectangle r = rois[i].getBounds();
			if (r.x<xmin) xmin = r.x;
			if (r.y<ymin) ymin = r.y;
			if (r.x+r.width>xmax) xmax = r.x+r.width;
			if (r.y+r.height>ymax) ymax = r.y+r.height;
		}
		return new Rectangle(xmin, ymin, xmax-xmin, ymax-ymin);
	}
	*/
	
	/* Returns the Roi that results from XORing all the ROIs
	 * in this overlay that have an index in the array ‘indexes’.
	*/
	public Roi xor(int[] indexes) {
		return Roi.xor(toArray(indexes));
	}

	/** Returns a new Overlay that has the same properties as this one. */
	public Overlay create() {
		Overlay overlay2 = new Overlay();
		overlay2.drawLabels(label);
		overlay2.drawNames(drawNames);
		overlay2.drawBackgrounds(drawBackgrounds);
		overlay2.setLabelColor(labelColor);
		overlay2.setLabelFont(labelFont, scalableLabels);
		overlay2.setIsCalibrationBar(isCalibrationBar);
		overlay2.selectable(selectable);
		overlay2.setDraggable(draggable);
		return overlay2;
	}
	
	/** Returns a clone of this Overlay. */
	public Overlay duplicate() {
		Roi[] rois = toArray();
		Overlay overlay2 = create();
		for (int i=0; i<rois.length; i++)
			overlay2.add((Roi)rois[i].clone());
		return overlay2;
	}
	
	/** Returns a scaled version of this Overlay. */
	public Overlay scale(double xscale, double yscale) {
		Overlay overlay2 = create();
		for (int i=0; i<size(); i++) {
			Roi roi = get(i);
			int position = roi.getPosition();
			roi = RoiScaler.scale(roi, xscale, yscale, false);
			roi.setPosition(position);
			overlay2.add(roi);
		}
		return overlay2;
	}

 	/** Returns a rotated version of this Overlay. */
	public Overlay rotate(double angle, double xcenter, double ycenter) {
		//IJ.log("rotate: "+angle+" "+xcenter+" "+ycenter);
		Overlay overlay2 = create();
		for (int i=0; i<size(); i++) {
			Roi roi = get(i);
			int position = roi.getPosition();
			if (!Rotator.GRID.equals(roi.getName()))
				roi = RoiRotator.rotate(roi, angle, xcenter, ycenter);
			roi.setPosition(position);
			overlay2.add(roi);
		}
		return overlay2;
	}

   public void drawLabels(boolean b) {
    	label = b;
    }
    
    public boolean getDrawLabels() {
    	return label;
    }
    
    public void drawNames(boolean b) {
    	drawNames = b;
		Roi[] rois = toArray();
		for (int i=0; i<rois.length; i++)
			rois[i].setIgnoreClipRect(drawNames);
    }
    
    public boolean getDrawNames() {
    	return drawNames;
    }

    public void drawBackgrounds(boolean b) {
    	drawBackgrounds = b;
    }
    
    public boolean getDrawBackgrounds() {
    	return drawBackgrounds;
    }

    public void setLabelColor(Color c) {
    	labelColor = c;
    }
    
    public Color getLabelColor() {
    	return labelColor;
    }

    public void setLabelFont(Font font) {
    	setLabelFont(font, false);
    }
    
    public void setLabelFont(Font font, boolean scalable) {
    	labelFont = font;
    	scalableLabels = scalable;
    }

    /** Set the label font size with options. The options string can contain
     * 'scale' (enlarge labels when image zoomed), 'bold'
     * (display bold labels) or 'background' (display labels
     * with contrasting background.
    */
    public void setLabelFontSize(int size, String options) {
    	int style = Font.PLAIN;
    	if (options!=null) {
    		scalableLabels = options.contains("scale");
    		if (options.contains("bold"))
    			style = Font.BOLD;
    		drawBackgrounds = options.contains("back");
    	}
    	labelFont = new Font("SansSerif", style, size);
    	drawLabels(true);
    }

    public Font getLabelFont() {
    	return labelFont;
    }

    public void setIsCalibrationBar(boolean b) {
    	this.isCalibrationBar = b;
    }
    
    public boolean isCalibrationBar() {
    	return isCalibrationBar;
    }
    
    /** Fills all the ROIs in this overlay with 'foreground' after clearing the
    	the image to 'background' if it is not null. */
    public void fill(ImagePlus imp, Color foreground, Color background) {
    	ImageProcessor ip = imp.getProcessor();
		if (background!=null) {
			ip.resetRoi();
			ip.setColor(background);
			ip.fillRect(0,0,ip.getWidth(),ip.getHeight());
		}
		if (foreground!=null) {
			ip.setColor(foreground);
			for (int i=0; i<size(); i++)
				ip.fill(get(i));
			ip.resetRoi();
		}
		imp.updateAndDraw();
    }

    void setVector(Vector<Roi> v) {list = v;}
        
    Vector<Roi> getVector() {return list;}
    
    /** Set 'false' to prevent ROIs in this overlay from being activated 
		by clicking on their labels or by a long clicking. */ 
    public void selectable(boolean selectable) {
    	this.selectable = selectable;
    }
    
    /** Returns 'true' if ROIs in this overlay can be activated
		by clicking on their labels or by a long press. */ 
	public boolean isSelectable() {
		return selectable;
	}
	
    /** Set 'false' to prevent ROIs in this overlay from being dragged by their labels. */ 
    public void setDraggable(boolean draggable) {
    	this.draggable = draggable;
    }
    
    /** Sets the minimum scaled stroke width (default is 0.05). */
	public void setMinStrokeWidth(double minWidth) {
		minStrokeWidth = minWidth;
	}
    
    /** Returns 'true' if ROIs in this overlay can be dragged by their labels. */
	public boolean isDraggable() {
		return draggable;
	}

 	public boolean scalableLabels() {
		return scalableLabels;
	}
	
	public String toString() {
    	return "Overlay[size="+size()+" "+(scalableLabels?"scale":"")+" "+Colors.colorToString(getLabelColor())+"]";
    }
    
    /** Updates overlays created by the particle analyzer
    	after rows are deleted from the Results table. */
    public static void updateTableOverlay(ImagePlus imp, int first, int last, int tableSize) {
		if (imp==null)
			return;
		Overlay overlay = imp.getOverlay();
		if (overlay==null)
			return;
		if (overlay.size()!=tableSize)
			return;
		if (first<0)
			first = 0;
		if (last>tableSize-1)
			last = tableSize-1;
		if (first>last)
			return;
		String name1 = overlay.get(0).getName();
		String name2 = overlay.get(overlay.size()-1).getName();
		if (!"1".equals(name1) || !(""+tableSize).equals(name2))
			return;
		int count = last-first+1;
		if (overlay.size()==count && !IJ.isMacro()) {
			if (count==1 || IJ.showMessageWithCancel("ImageJ", "Delete "+overlay.size()+" element overlay?  "))
				imp.setOverlay(null);
			return;
		}
		for (int i=0; i<count; i++)
			overlay.remove(first);
		for (int i=first; i<overlay.size(); i++)
			overlay.get(i).setName(""+(i+1));
		imp.draw();
	}
	
	public static Overlay createStackOverlay(Roi[] rois) {
		Overlay overlay = new Overlay();
		for (int i=0; i<rois.length; i++) {
			Roi roi = (Roi)rois[i].clone();
			roi.setLocation(0,0);
			roi.setPosition(i+1);
			overlay.add(roi);
		}
		return overlay;
	}
	
	@Override
	public Iterator<Roi> iterator() {
		final Overlay overlay = this;

		Iterator<Roi> it = new Iterator<Roi>() {
			private int index = -1;

			/** Returns 'true' if next element exists. */ 
			@Override
			public boolean hasNext() {
				if (index+1<overlay.size()) 
					return true;
				else 
					return false;
			}

			/** Returns current ROI and updates pointer. */
			@Override
			public Roi next() {
				if (index+1<overlay.size())
					return overlay.get(++index);
				else
					return null;
			} 

			@Override
			public void remove() { 
				throw new UnsupportedOperationException(); 
			}
		};
		return it;
	}
    
}
