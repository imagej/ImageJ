package ij.plugin;
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
public class SpecifyROI implements PlugIn, DialogListener {
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

    public void run(String arg) {
        imp = IJ.getImage();
        stackSize = imp!=null?imp.getStackSize():0;
        Roi roi = imp.getRoi();
        Rectangle r = roi!=null?roi.getBounds():imp.getProcessor().getRoi();
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
        showDialog();
     }

    /**
     *	Creates a dialog box, allowing the user to enter the requested
     *	width, height, x & y coordinates, slice number for a Region Of Interest,
     *  option for oval, and option for whether x & y coordinates to be centered.
     */
    void showDialog() {
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
        gd.addDialogListener(this);
        gd.showDialog();
        if (gd.wasCanceled()) {
        	 if (roi==null)
        		imp.killRoi();
        	 else if (!rectOrOval)
        		imp.setRoi(roi);
        }
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
    	
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		iWidth = (int) gd.getNextNumber();
		iHeight = (int) gd.getNextNumber();
		iXROI = (int) gd.getNextNumber();	
		iYROI = (int) gd.getNextNumber();
		if (stackSize>1)	
			iSlice = (int) gd.getNextNumber();  
		oval = gd.getNextBoolean();
		centered = gd.getNextBoolean();
		if (gd.invalidNumber())
			return false;
		else {
			if (stackSize>1 && iSlice>0 && iSlice<=stackSize)
			    imp.setSlice(iSlice);
			drawRoi();
        		return true;
		}
    }

}
