package ij.plugin.filter;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.util.Tools;

/**
 *      This plugin implements the Edit/Selection/Specify command.<p>
 *      Enhancing the original plugin created by Jeffrey Kuhn, this one takes,
 *      in addition to width and height and the option to have an oval ROI from 
 *      the original program, x & y coordinates, slice number, and the option to have
 *      the x & y coordinates centered or in default top left corner of ROI.
 *      The original creator is Jeffrey Kuhn, The University of Texas at Austin,
 *	    jkuhn@ccwf.cc.utexas.edu
 *
 *      @author Anthony Padua
 *      @author Duke University Medical Center, Department of Radiology
 *      @author padua001@mc.duke.edu
 *      
 */
public class SpecifyROI implements PlugInFilter, TextListener, ItemListener {
    int             iX;
    int             iY;
    int             iXROI;
    int             iYROI;
    int             iSlice;
    int             iWidth;
    int             iHeight;
    boolean         bAbort;
    ImagePlus       imp;
    static boolean  oval;
    static boolean  centered;
    Vector fields, checkboxes;
    int stackSize;

    /**
     *	Called by ImageJ when the filter is loaded
     */
    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        stackSize = imp!=null?imp.getStackSize():0;
        return DOES_ALL+NO_CHANGES;
    }

    /**
     *	Called by ImageJ to process the image
     */
    public void run(ImageProcessor ip) {
        Roi roi = imp.getRoi();
        Rectangle r = roi!=null?roi.getBounds():ip.getRoi();
        iWidth = r.width;
        iHeight = r.height;
        iXROI = r.x;
        iYROI = r.y;
        if (roi==null) {
        	iWidth /= 2;
        	iHeight /= 2;
        	iXROI += iWidth/2;
        	iYROI += iHeight/2; 
        }
        iSlice = imp.getCurrentSlice();
        if (!showDialog())
            return;
        if (stackSize>1 && iSlice > 0 && iSlice <= stackSize)
           imp.setSlice(iSlice);
		drawRoi();
        IJ.register(SpecifyROI.class);
    }

    /**
     *	Creates a dialog box, allowing the user to enter the requested
     *	width, height, x & y coordinates, slice number for a Region Of Interest,
     *  option for oval, and option for whether x & y coordinates to be centered.
     */
    boolean showDialog() {
    	Roi roi = imp.getRoi();
    	boolean rectOrOval = roi!=null && (roi.getType()==Roi.RECTANGLE||roi.getType()==Roi.OVAL);
    	if (roi==null || !rectOrOval)
    		drawRoi();
        GenericDialog gd = new GenericDialog("Specify");
        gd.addNumericField("Width:", iWidth, 0);
        gd.addNumericField("Height:", iHeight, 0);
        gd.addNumericField("X Coordinate:", iXROI, 0);
        gd.addNumericField("Y Coordinate:", iYROI, 0);
        if (stackSize>1)
        	gd.addNumericField("Slice:", iSlice, 0);
        gd.addCheckbox("Oval", oval);
        gd.addCheckbox("Centered",centered);
        fields = gd.getNumericFields();
        for (int i=0; i<fields.size(); i++)
            ((TextField)fields.elementAt(i)).addTextListener(this);
        checkboxes = gd.getCheckboxes();
        for (int i=0; i<checkboxes.size(); i++)
            ((Checkbox)checkboxes.elementAt(i)).addItemListener(this);
        gd.showDialog();
        if (gd.wasCanceled()) {
        	if (roi==null)
        		imp.killRoi();
        	else if (!rectOrOval)
        		imp.setRoi(roi);
            return false;
        }
        iWidth = (int) gd.getNextNumber();
        iHeight = (int) gd.getNextNumber();
        iXROI = (int) gd.getNextNumber();	
        iYROI = (int) gd.getNextNumber();
        if (stackSize>1)	
        	iSlice = (int) gd.getNextNumber();  
        oval = gd.getNextBoolean();
        centered = gd.getNextBoolean();
        return true;
    }
    
    void drawRoi() {
        if (centered) {
            iX = iXROI - (iWidth/2);
            iY = iYROI - (iHeight/2);
        } else {
            iX = iXROI;
            iY = iYROI;
        }
        if (oval)
            imp.setRoi(new OvalRoi(iX, iY, iWidth, iHeight,imp));
        else
            imp.setRoi(iX, iY, iWidth, iHeight);
    }
    
    public void textValueChanged(TextEvent e) {
        int width = (int)Tools.parseDouble(((TextField)fields.elementAt(0)).getText(),-99);
        int height = (int)Tools.parseDouble(((TextField)fields.elementAt(1)).getText(),-99);
        int x = (int)Tools.parseDouble(((TextField)fields.elementAt(2)).getText(),-99);
        int y = (int)Tools.parseDouble(((TextField)fields.elementAt(3)).getText(),-99);
        if (width==-99 || height==-99 || x==-99 || y==-99)
        	return;
        if (width!=iWidth || height!=iHeight || x!=iXROI || y!=iYROI) {
        	iWidth = width;
        	iHeight = height;
        	iXROI = x;
        	iYROI = y;
        	drawRoi();
        }
    }

	public void itemStateChanged(ItemEvent e) {
		Checkbox cb = (Checkbox)checkboxes.elementAt(0);
        boolean oval = cb.getState();
		cb = (Checkbox)checkboxes.elementAt(1);
        boolean centered = cb.getState();
        if (oval!=this.oval || centered!=this.centered) {
        	this.oval = oval;
        	this.centered = centered;
        	drawRoi();
        }
	}
	
}
