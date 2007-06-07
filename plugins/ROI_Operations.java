import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import ij.plugin.filter.*;
import ij.measure.*;
/*
Copyright  2004 Cezar M. Tigaret.
Permission to use, copy, modify, distribute and sell this software and its documentation for any purpose
is hereby granted without fee, provided that the above copyright notice appear in all copies and
that both that copyright notice and this permission notice appear in supporting documentation.
The author makes no representations about the suitability of this software for any purpose.
It is provided "as is" without expressed or implied warranty.

This code is in the public domain
*/

/**
 * <div align="justify">
 * <br>
 * Created: 31/01/2004
 * </div>
 * @author  <a href="mailto: c.tigaret@ucl.ac.uk">Cezar M. Tigaret</a>
 * @version
 *
 */
public class ROI_Operations implements PlugIn
{
	ImagePlus imp;
	RoiManager roiMan;
	String[] actions ={"MEASURE","SHAPE","AND","OR","XOR","NOT","REPLICATE"};
	// by the way, it turns out that traced roi, freeline, polyline and angle
	// are all "flavors" of polygon roi. cool!

	public ROI_Operations()
	{
		roiMan = RoiManager.getInstance();
		if(roiMan==null)
		{
			roiMan = new RoiManager();
		}
	}

	public void run(String arg)
	{
		imp = IJ.getImage();
		if(imp==null) return;
		if(!IJ.isJava2()) return;
		String action="";
		GenericDialog gd = new GenericDialog("Choose an action");
		gd.addChoice("Action:",actions,actions[0]);
		if(arg==null || arg=="")
		{
			gd.showDialog();
	    if(!gd.wasCanceled())
				action = gd.getNextChoice();
		}
		if(action.equals("AND"))
			intersectRois();
		else if(action.equals("OR"))
			combineRois();
		else if(action.equals("XOR"))
			exclusiveCombineRois();
		else if(action.equals("NOT"))
			subtractRois();
		if(action.equals("MEASURE"))
			measureRois();
		else if(action.equals("SHAPE"))
			shapeRois();
		else if(action.equals("REPLICATE"))
			replicateRois();
	}

	void combineRois()
	{
		Hashtable rois = roiMan.getROIs();
		if(rois.isEmpty() || rois.size()<2) return;
		Roi[] rArray = new Roi[2];
		int i=0;
		for (Enumeration e = rois.elements(); e.hasMoreElements();)
		{
			rArray[i] = (Roi)e.nextElement();
			i++;
		}

		ShapeRoi s1 = new ShapeRoi(rArray[0]);
		ShapeRoi s2 = new ShapeRoi(rArray[1]);

		s1.or(s2);

		s1.setImage(imp);
		imp.setRoi(s1);
	}

	void intersectRois()
	{
		Hashtable rois = roiMan.getROIs();
		if(rois.isEmpty() || rois.size()<2) return;
		Roi[] rArray = new Roi[2];
		int i=0;
		for (Enumeration e = rois.elements(); e.hasMoreElements();)
		{
			rArray[i] = (Roi)e.nextElement();
			i++;
		}
		ShapeRoi s1 = new ShapeRoi(rArray[0]);
		ShapeRoi s2 = new ShapeRoi(rArray[1]);

		s1.and(s2);

		s1.setImage(imp);
		imp.setRoi(s1);
	}

	void exclusiveCombineRois()
	{
		Hashtable rois = roiMan.getROIs();
		if(rois.isEmpty() || rois.size()<2) return;
		Roi[] rArray = new Roi[2];
		int i=0;
		for (Enumeration e = rois.elements(); e.hasMoreElements();)
		{
			rArray[i] = (Roi)e.nextElement();
			i++;
		}

		ShapeRoi s1 = new ShapeRoi(rArray[0]);
		ShapeRoi s2 = new ShapeRoi(rArray[1]);

		s1.xor(s2);

		s1.setImage(imp);
		imp.setRoi(s1);
	}

	void subtractRois()
	{
		Hashtable rois = roiMan.getROIs();
		if(rois.isEmpty() || rois.size()<2) return;
		Roi[] rArray = new Roi[2];
		int i=0;
		for (Enumeration e = rois.elements(); e.hasMoreElements();)
		{
			rArray[i] = (Roi)e.nextElement();
			i++;
		}

		ShapeRoi s1 = new ShapeRoi(rArray[0]);
		ShapeRoi s2 = new ShapeRoi(rArray[1]);

		s1.not(s2);

		s1.setImage(imp);
		imp.setRoi(s1);
	}

	void replicateRois()
	{
		Hashtable rois = roiMan.getROIs();
		if (rois.isEmpty()) return;
		for (Enumeration e = rois.keys(); e.hasMoreElements();)
		{
			String roiName = (String)e.nextElement();
			Roi roi = (Roi)rois.get(roiName);
			imp.setRoi(roi);
			try
			{
				Thread.sleep(1000L);
			}catch(Throwable t){}
			ShapeRoi sr = new ShapeRoi(roi);
			Vector rr = sr.getRois();
			for(Enumeration e1 = rr.elements(); e1.hasMoreElements();)
			{
				Roi newRoi = (Roi)e1.nextElement();
				imp.setRoi(newRoi);
				try
				{
					Thread.sleep(1000L);
				}catch(Throwable t){}
			}
		}
	}

	void measureRois()
	{
		Hashtable rois = roiMan.getROIs();
		if (rois.isEmpty()) return;
		for (Enumeration e = rois.keys(); e.hasMoreElements();)
		{
			String roiName = (String)e.nextElement();
			Roi roi = (Roi)rois.get(roiName);
/*			IJ.write("Name: "+roiName);
			IJ.write("type: "+typeInt2Str(roi.getType()));*/
			ShapeRoi sr = new ShapeRoi(roi);
			imp.setRoi(sr);
/*			double l = sr.getLength();
			double a = sr.getArea();
			int[] mask = sr.getMask();
			IJ.write("length "+l+" area "+a);
			IJ.write("**************************************************************************");*/
			IJ.run("Set Measurements...", "area       perimeter        redirect=None decimal=3");
			IJ.run("Measure");
		}
	}

	void shapeRois()
	{
		Hashtable rois = roiMan.getROIs();
		if (rois.isEmpty())
		{
			IJ.write("no rois");
			return;
		}
		for (Enumeration e = rois.keys(); e.hasMoreElements();)
		{
			String roiName = (String)e.nextElement();
			Roi roi = (Roi)rois.get(roiName);
			ShapeRoi newR = new ShapeRoi(roi);
			imp.setRoi(newR);
			try
			{
				Thread.sleep(1000L);
			}catch(Throwable t){}
		}
	}

	String typeInt2Str(int i)
	{
		String s="";
		switch(i)
		{
			case Roi.POLYGON:
				s="Roi.POLYGON";
				break;
			case Roi.FREEROI:
				s="Roi.FREEROI";
				break;
			case Roi.TRACED_ROI:
				s="Roi.TRACED_ROI";
				break;
			case Roi.POLYLINE:
				s="Roi.POLYLINE";
				break;
			case Roi.FREELINE:
				s="Roi.FREELINE";
				break;
			case Roi.ANGLE:
				s="Roi.ANGLE";
				break;
			case Roi.LINE:
				s="Roi.LINE";
				break;
			case Roi.OVAL:
				s="Roi.OVAL";
				break;
			case Roi.COMPOSITE:
				s="Composite";
				break;
			default:
				s="Roi.RECTANGLE";
				break;
		}
		return s;
	}



} // ROI_Operations2


