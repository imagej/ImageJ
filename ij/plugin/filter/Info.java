package ij.plugin.filter;
import java.awt.*;
import java.util.*;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.text.*;
import ij.measure.*;

/** This plug-in implements the Image/Show Info command. */
public class Info implements PlugInFilter {
    private ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		String infoProperty = (String)imp.getProperty("Info");
		String info = getInfo(ip);
		if (infoProperty!=null)
			showInfo(infoProperty+"\n------------------------\n"
				+info, 400, 500);
		else
			showInfo(info, 300, 300);
	}

	String getInfo(ImageProcessor ip) {
		String s = new String("\n");
		s += "Title: '" + imp.getTitle() + "'\n";
		Calibration cal = imp.getCalibration();
		if (cal.scaled()) {
			String unit = cal.getUnit();
			String units = cal.getUnits();
	    	s += "Width:  "+IJ.d2s(imp.getWidth()*cal.pixelWidth,2)+" " + units+" ("+imp.getWidth()+")\n";
	    	s += "Height:  "+IJ.d2s(imp.getHeight()*cal.pixelHeight,2)+" " + units+" ("+imp.getHeight()+")\n";
	    	if (cal.pixelWidth==cal.pixelHeight)
	    		s += "Resolution:  "+IJ.d2s(1.0/cal.pixelWidth,1) + " pixels per "+unit+"\n";
	    	else {
	    		s += "X Resolution:  "+IJ.d2s(1.0/cal.pixelWidth,1) + " pixels per "+unit+"\n";
	    		s += "Y Resolution:  "+IJ.d2s(1.0/cal.pixelHeight,1) + " pixels per "+unit+"\n";
	    	}
	    } else {
	    	s += "Width:  " + imp.getWidth() + " pixels\n";
	    	s += "Height:  " + imp.getHeight() + " pixels\n";
	    }
	    
	    int type = imp.getType();
    	switch (type) {
	    	case ImagePlus.GRAY8:
	    		s += "Bits per pixel: 8 ";
	    		if (imp.isInvertedLut())
	    			s += "(inverted LUT)\n";
	    		else
	    			s += "(grayscale LUT)\n";
	    		break;
	    	case ImagePlus.GRAY16: case ImagePlus.GRAY32:
	    		if (type==ImagePlus.GRAY16) {
					ShortProcessor sp = (ShortProcessor)imp.getProcessor(); 
	    			s += "Bits per pixel: 16 (unsigned short)\n";
	    		} else
	    			s += "Bits per pixel: 32 (float)\n";
		    		s += "Display window: " + IJ.d2s(ip.getMin()) + " - " + IJ.d2s(ip.getMax()) + "\n";
	    		break;
	    	case ImagePlus.COLOR_256:
	    		s += "Bits per pixel: 8 (color LUT)\n";
	    		break;
	    	case ImagePlus.COLOR_RGB:
	    		s += "Bits per pixel: 32 (RGB)\n";
	    		break;
    	}
    	int nSlices = imp.getStackSize();
    	if (nSlices>1)
			s += "Slices: " + nSlices + " (" + imp.getCurrentSlice() + ")\n";

		if (ip.getMinThreshold()==ip.NO_THRESHOLD)
	    	s += "No Threshold\n";
	    else
			s += "Threshold: "+IJ.d2s(ip.getMinThreshold(),0)+"-"+IJ.d2s(ip.getMaxThreshold(),0)+"\n";
		ImageCanvas ic = imp.getWindow().getCanvas();
    	double mag = ic.getMagnification();
    	if (mag!=1.0)
			s += "Magnification: " + mag + "\n";
			
	    if (cal.calibrated()) {
	    	s += " \n";
	    	int curveFit = cal.getFunction();
			s += "Calibration Function: ";
			if (curveFit==Calibration.UNCALIBRATED_OD)
				s += "Uncalibrated OD\n";	    	
			else
				s += CurveFitter.fList[curveFit]+"\n";
			double[] c = cal.getCoefficients();
			if (c!=null) {
				s += "  a: "+IJ.d2s(c[0],6)+"\n";
				s += "  b: "+IJ.d2s(c[1],6)+"\n";
				if (c.length>=3)
					s += "  c: "+IJ.d2s(c[2],6)+"\n";
				if (c.length>=4)
					s += "  c: "+IJ.d2s(c[3],6)+"\n";
				if (c.length>=5)
					s += "  c: "+IJ.d2s(c[4],6)+"\n";
			}
			s += "  Unit: \""+cal.getValueUnit()+"\"\n";	    	
	    } else
	    	s += "Uncalibrated\n";

	    Roi roi = imp.getRoi();
	    if (roi == null) {
			if (cal.calibrated())
	    		s += " \n";
	    	s += "No ROI\n";
	    } else {
	    	s += " \n";
    		switch (roi.getType()) {
    			case Roi.RECTANGLE: s += "Rectangular ROI\n"; break;
    			case Roi.OVAL: s += "Oval ROI\n"; break;
    			case Roi.POLYGON: s += "Polygon ROI\n"; break;
    			case Roi.FREEROI: s += "Freehand ROI\n"; break;
    			case Roi.TRACED_ROI: s += "Traced ROI\n"; break;
    			case Roi.LINE: s += "Line Selection\n"; break;
    			case Roi.POLYLINE: s += "Polyline Selection\n"; break;
    			case Roi.FREELINE: s += "Freehand line Selection\n"; break;
    		}
	    	Rectangle r = roi.getBoundingRect();
	    	if (roi instanceof Line) {
	    		Line line = (Line)roi;
	    		s += "  X1: " + IJ.d2s(line.x1*cal.pixelWidth) + "\n";
	    		s += "  Y1: " + IJ.d2s(line.y1*cal.pixelHeight) + "\n";
	    		s += "  X2: " + IJ.d2s(line.x2*cal.pixelWidth) + "\n";
	    		s += "  Y2: " + IJ.d2s(line.y2*cal.pixelHeight) + "\n";
	    	
			} else if (cal.scaled()) {
				s += "  X: " + IJ.d2s(r.x*cal.pixelWidth) + " (" + r.x + ")\n";
				s += "  Y: " + IJ.d2s(r.y*cal.pixelHeight) + " (" +  r.y + ")\n";
				s += "  Width: " + IJ.d2s(r.width*cal.pixelWidth) + " (" +  r.width + ")\n";
				s += "  Height: " + IJ.d2s(r.height*cal.pixelHeight) + " (" +  r.height + ")\n";
			} else {
				s += "  X: " + r.x + "\n";
				s += "  Y: " + r.y + "\n";
				s += "  Width: " + r.width + "\n";
				s += "  Height: " + r.height + "\n";
	    	}
	    }

		s += " \n";
		s += IJ.freeMemory() + "\n";
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		s += "Screen Size: " + d.width + "x" + d.height;
		return s;
	}

	void showInfo(String info, int width, int height) {
		TextWindow tw = new TextWindow("Info for "+imp.getTitle(), info, width, height);
	}

}
