package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.io.*;
import ij.util.Tools;
import ij.plugin.frame.Editor;
import ij.text.TextWindow;
import java.awt.*;
import java.util.*;
import java.lang.reflect.*;

/** This plugin implements the Image/Show Info command. */
public class Info implements PlugInFilter {
    private ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		String info = getImageInfo(imp, ip);
		if (info.indexOf("----")>0)
			showInfo(info, 450, 500);
		else
			showInfo(info, 300, 300);
	}

	public String getImageInfo(ImagePlus imp, ImageProcessor ip) {
		String infoProperty = null;
		if (imp.getStackSize()>1) {
			ImageStack stack = imp.getStack();
			String label = stack.getSliceLabel(imp.getCurrentSlice());
			if (label!=null && label.indexOf('\n')>0)
				infoProperty = label;
		}
		if (infoProperty==null) {
			infoProperty = (String)imp.getProperty("Info");
			if (infoProperty==null)
				infoProperty = getExifData(imp);
		}
		String info = getInfo(imp, ip);
		if (infoProperty!=null)
			return infoProperty + "\n------------------------\n" + info;
		else
			return info;		
	}
	
	private String getExifData(ImagePlus imp) {
		FileInfo fi = imp.getOriginalFileInfo();
		if (fi==null)
			return null;
		String directory = fi.directory;
		String name = fi.fileName;
		if (directory==null)
			return null;
		if ((name==null||name.equals("")) && imp.getStack().isVirtual())
			name = imp.getStack().getSliceLabel(imp.getCurrentSlice());
		if (name==null || !(name.endsWith("jpg")||name.endsWith("JPG")))
			return null;
		String path = directory + name;
		String metadata = null;
		try {
			Class c = IJ.getClassLoader().loadClass("Exif_Reader");
			if (c==null) return null;
			String methodName = "getMetadata";
			Class[] argClasses = new Class[1];
			argClasses[0] = methodName.getClass();
			Method m = c.getMethod("getMetadata", argClasses);
			Object[] args = new Object[1];
			args[0] = path;
			Object obj = m.invoke(null, args);
			metadata = obj!=null?obj.toString():null;
		} catch(Exception e) {
			return null;
		}
		if (metadata!=null && !metadata.startsWith("Error:"))
			return metadata;
		else
			return null;
	}

	String getInfo(ImagePlus imp, ImageProcessor ip) {
		String s = new String("\n");
		s += "Title: " + imp.getTitle() + "\n";
		Calibration cal = imp.getCalibration();
    	int stackSize = imp.getStackSize();
    	int channels = imp.getNChannels();
    	int slices = imp.getNSlices();
    	int frames = imp.getNFrames();
		int digits = imp.getBitDepth()==32?4:0;
		if (cal.scaled()) {
			String unit = cal.getUnit();
			String units = cal.getUnits();
	    	s += "Width:  "+IJ.d2s(imp.getWidth()*cal.pixelWidth,2)+" " + units+" ("+imp.getWidth()+")\n";
	    	s += "Height:  "+IJ.d2s(imp.getHeight()*cal.pixelHeight,2)+" " + units+" ("+imp.getHeight()+")\n";
	    	if (slices>1)
	    		s += "Depth:  "+IJ.d2s(slices*cal.pixelDepth,2)+" " + units+" ("+slices+")\n";	    			    	
	    	double xResolution = 1.0/cal.pixelWidth;
	    	double yResolution = 1.0/cal.pixelHeight;
	    	int places = Tools.getDecimalPlaces(xResolution, yResolution);
	    	if (xResolution==yResolution)
	    		s += "Resolution:  "+IJ.d2s(xResolution,places) + " pixels per "+unit+"\n";
	    	else {
	    		s += "X Resolution:  "+IJ.d2s(xResolution,places) + " pixels per "+unit+"\n";
	    		s += "Y Resolution:  "+IJ.d2s(yResolution,places) + " pixels per "+unit+"\n";
	    	}
	    } else {
	    	s += "Width:  " + imp.getWidth() + " pixels\n";
	    	s += "Height:  " + imp.getHeight() + " pixels\n";
	    	if (stackSize>1)
	    		s += "Depth:  " + slices + " pixels\n";
	    }
    	if (stackSize>1) 
	    	s += "Voxel size: "+d2s(cal.pixelWidth)+"x"+d2s(cal.pixelHeight)+"x"+d2s(cal.pixelDepth)+" "+cal.getUnit()+"\n";
	    else
	    	s += "Pixel size: "+d2s(cal.pixelWidth)+"x"+d2s(cal.pixelHeight)+" "+cal.getUnit()+"\n";

	    s += "ID: "+imp.getID()+"\n";
	    String zOrigin = stackSize>1||cal.zOrigin!=0.0?","+d2s(cal.zOrigin):"";
	    s += "Coordinate origin:  " + d2s(cal.xOrigin)+","+d2s(cal.yOrigin)+zOrigin+"\n";
	    int type = imp.getType();
    	switch (type) {
	    	case ImagePlus.GRAY8:
	    		s += "Bits per pixel: 8 ";
	    		String lut = "LUT";
	    		if (imp.getProcessor().isColorLut())
	    			lut = "color " + lut;
	    		else
	    			lut = "grayscale " + lut;
	    		if (imp.isInvertedLut())
	    			lut = "inverting " + lut;
	    		s += "(" + lut + ")\n";
	    		if (imp.getNChannels()>1)
	    			s += displayRanges(imp);
	    		else
					s += "Display range: "+(int)ip.getMin()+"-"+(int)ip.getMax()+"\n";
	    		break;
	    	case ImagePlus.GRAY16: case ImagePlus.GRAY32:
	    		if (type==ImagePlus.GRAY16) {
	    			String sign = cal.isSigned16Bit()?"signed":"unsigned";
	    			s += "Bits per pixel: 16 ("+sign+")\n";
	    		} else
	    			s += "Bits per pixel: 32 (float)\n";
	    		if (imp.getNChannels()>1)
	    			s += displayRanges(imp);
	    		else {
					s += "Display range: ";
					double min = ip.getMin();
					double max = ip.getMax();
					if (cal.calibrated()) {
						min = cal.getCValue((int)min);
						max = cal.getCValue((int)max);
					}
					s += IJ.d2s(min,digits) + " - " + IJ.d2s(max,digits) + "\n";
		    	}
	    		break;
	    	case ImagePlus.COLOR_256:
	    		s += "Bits per pixel: 8 (color LUT)\n";
	    		break;
	    	case ImagePlus.COLOR_RGB:
	    		s += "Bits per pixel: 32 (RGB)\n";
	    		break;
    	}
		double interval = cal.frameInterval;	
		double fps = cal.fps;	
    	if (stackSize>1) {
    		ImageStack stack = imp.getStack();
    		int slice = imp.getCurrentSlice();
    		String number = slice + "/" + stackSize;
    		String label = stack.getShortSliceLabel(slice);
    		if (label!=null && label.length()>0)
    			label = " (" + label + ")";
    		else
    			label = "";
			if (interval>0.0 || fps!=0.0) {
				s += "Frame: " + number + label + "\n";
				if (fps!=0.0) {
					String sRate = Math.abs(fps-Math.round(fps))<0.00001?IJ.d2s(fps,0):IJ.d2s(fps,5);
					s += "Frame rate: " + sRate + " fps\n";
				}
				if (interval!=0.0)
					s += "Frame interval: " + ((int)interval==interval?IJ.d2s(interval,0):IJ.d2s(interval,5)) + " " + cal.getTimeUnit() + "\n";
			} else
				s += "Image: " + number + label + "\n";
			if (imp.isHyperStack()) {
				if (channels>1)
					s += "  Channel: " + imp.getChannel() + "/" + channels + "\n";
				if (slices>1)
					s += "  Slice: " + imp.getSlice() + "/" + slices + "\n";
				if (frames>1)
					s += "  Frame: " + imp.getFrame() + "/" + frames + "\n";
			}
			if (imp.isComposite()) {
				if (!imp.isHyperStack() && channels>1)
					s += "  Channels: " + channels + "\n";
				String mode = ((CompositeImage)imp).getModeAsString();
				s += "  Composite mode: \"" + mode + "\"\n";
			}
		}

		if (ip.getMinThreshold()==ImageProcessor.NO_THRESHOLD)
	    	s += "No Threshold\n";
	    else {
	    	double lower = ip.getMinThreshold();
	    	double upper = ip.getMaxThreshold();
			int dp = digits;
			if (cal.calibrated()) {
				lower = cal.getCValue((int)lower);
				upper = cal.getCValue((int)upper);
				dp = cal.isSigned16Bit()?0:4;
			}
			s += "Threshold: "+IJ.d2s(lower,dp)+"-"+IJ.d2s(upper,dp)+"\n";
		}
		ImageCanvas ic = imp.getCanvas();
    	double mag = ic!=null?ic.getMagnification():1.0;
    	if (mag!=1.0)
			s += "Magnification: " + IJ.d2s(mag,2) + "\n";
			
	    if (cal.calibrated()) {
	    	s += " \n";
	    	int curveFit = cal.getFunction();
			s += "Calibration Function: ";
			if (curveFit==Calibration.UNCALIBRATED_OD)
				s += "Uncalibrated OD\n";	    	
			else if (curveFit==Calibration.CUSTOM)
				s += "Custom lookup table\n";	    	
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

	    FileInfo fi = imp.getOriginalFileInfo();
		if (fi!=null) {
			if (fi.url!=null && !fi.url.equals(""))
				s += "URL: " + fi.url + "\n";
			else if (fi.directory!=null && fi.fileName!=null)
				s += "Path: " + fi.directory + fi.fileName + "\n";
		}
	    
	    Roi roi = imp.getRoi();
	    if (roi == null) {
			if (cal.calibrated())
	    		s += " \n";
	    	s += "No Selection\n";
	    } else if (roi instanceof EllipseRoi) {
	    	s += "\nElliptical Selection\n";
	    	double[] p = ((EllipseRoi)roi).getParams();
			double dx = p[2] - p[0];
			double dy = p[3] - p[1];
			double major = Math.sqrt(dx*dx+dy*dy);
			s += "  Major: " + IJ.d2s(major,2) + "\n";
			s += "  Minor: " + IJ.d2s(major*p[4],2) + "\n";
			s += "  X1: " + IJ.d2s(p[0],2) + "\n";
			s += "  Y1: " + IJ.d2s(p[1],2) + "\n";
			s += "  X2: " + IJ.d2s(p[2],2) + "\n";
			s += "  Y2: " + IJ.d2s(p[3],2) + "\n";
			s += "  Aspect ratio: " + IJ.d2s(p[4],2) + "\n";
	    } else {
	    	s += " \n";
	    	s += roi.getTypeAsString()+" Selection";
	    	String points = null;
			if (roi instanceof PointRoi) {
				int npoints = ((PolygonRoi)roi).getNCoordinates();
				String suffix = npoints>1?"s)":")";
				points = " (" + npoints + " point" + suffix;
			}
    		String name = roi.getName();
    		if (name!=null) {
				s += " (\"" + name + "\")";
				if (points!=null) s += "\n " + points;		
			} else if (points!=null)
				s += points;
			s += "\n";		
	    	Rectangle r = roi.getBounds();
	    	if (roi instanceof Line) {
	    		Line line = (Line)roi;
	    		s += "  X1: " + IJ.d2s(line.x1d*cal.pixelWidth) + "\n";
	    		s += "  Y1: " + IJ.d2s(yy(line.y1d,imp)*cal.pixelHeight) + "\n";
	    		s += "  X2: " + IJ.d2s(line.x2d*cal.pixelWidth) + "\n";
	    		s += "  Y2: " + IJ.d2s(yy(line.y2d,imp)*cal.pixelHeight) + "\n";
			} else if (cal.scaled()) {
				s += "  X: " + IJ.d2s(cal.getX(r.x)) + " (" + r.x + ")\n";
				s += "  Y: " + IJ.d2s(cal.getY(r.y,imp.getHeight())) + " (" +  r.y + ")\n";
				s += "  Width: " + IJ.d2s(r.width*cal.pixelWidth) + " (" +  r.width + ")\n";
				s += "  Height: " + IJ.d2s(r.height*cal.pixelHeight) + " (" +  r.height + ")\n";
			} else {
				s += "  X: " + r.x + "\n";
				s += "  Y: " + yy(r.y,imp) + "\n";
				s += "  Width: " + r.width + "\n";
				s += "  Height: " + r.height + "\n";
			}
	    }
	    
		return s;
	}
	
	private String displayRanges(ImagePlus imp) {
		String s = "Display ranges\n";
		int channel = imp.getChannel();
		int n = imp.getNChannels();
		if (n>7) n=7;
		for (int c=1; c<=n; c++) {
			imp.setC(c);
			double min = imp.getDisplayRangeMin();
			double max = imp.getDisplayRangeMax();
			int digits = (int)min==min&&(int)max==max?0:2;
			s += "  " + c + ": " + IJ.d2s(min,digits) + "-" + IJ.d2s(max,digits) + "\n";
		}
		imp.setC(channel);
		return s;
	}
	
    String d2s(double n) {
		return n==(int)n?Integer.toString((int)n):IJ.d2s(n);
	}

	// returns a Y coordinate based on the "Invert Y Coodinates" flag
	int yy(int y, ImagePlus imp) {
		return Analyzer.updateY(y, imp.getHeight());
	}

	// returns a Y coordinate based on the "Invert Y Coodinates" flag
	double yy(double y, ImagePlus imp) {
		return Analyzer.updateY(y, imp.getHeight());
	}

	void showInfo(String info, int width, int height) {
		new TextWindow("Info for "+imp.getTitle(), info, width, height);
		//Editor ed = new Editor();
		//ed.setSize(width, height);
		//ed.create("Info for "+imp.getTitle(), info);
	}
	
}
