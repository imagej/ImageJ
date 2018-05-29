package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.io.*;
import ij.util.Tools;
import ij.plugin.frame.Editor;
import ij.plugin.filter.Analyzer;
import ij.text.TextWindow;
import ij.macro.Interpreter;
import java.awt.*;
import java.util.*;
import java.lang.reflect.*;

/** This plugin implements the Image/Show Info command. */
public class ImageInfo implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			showInfo();
		else {
			String info = getImageInfo(imp);
			if (info.contains("----"))
				showInfo(imp, info, 450, 500);
			else {
				int inc = info.contains("No Selection")?0:75;
				showInfo(imp, info, 300, 350+inc);
			}
		}
	}
	
	private void showInfo() {
		String s = new String("");
		if (IJ.getInstance()!=null)
			s += IJ.getInstance().getInfo()+"\n \n";
		s += "No images are open\n";
		Dimension screen = IJ.getScreenSize();
		s += "ImageJ home: "+IJ.getDir("imagej")+"\n";
		s += "Java home: "+System.getProperty("java.home")+"\n";
		s += "Screen size: "+screen.width+"x"+screen.height+"\n";
		if (IJ.isMacOSX()) {
			String time = " ("+ImageWindow.setMenuBarTime+"ms)";
			s += "SetMenuBarCount: "+Menus.setMenuBarCount+time+"\n";
		}
		new TextWindow("Info", s, 600, 300);
	}

	public String getImageInfo(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
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
			return infoProperty + "\n------------------------------------------------------\n" + info;
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

	private String getInfo(ImagePlus imp, ImageProcessor ip) {
		String s = new String("");
		if (IJ.getInstance()!=null)
			s += IJ.getInstance().getInfo()+"\n \n";
		s += "Title: " + imp.getTitle() + "\n";
		Calibration cal = imp.getCalibration();
    	int stackSize = imp.getStackSize();
    	int channels = imp.getNChannels();
    	int slices = imp.getNSlices();
    	int frames = imp.getNFrames();
		int digits = imp.getBitDepth()==32?4:0;
		int dp, dp2;
		boolean nonUniformUnits = !cal.getXUnit().equals(cal.getYUnit());
		String xunit = cal.getXUnit();
		String yunit = cal.getYUnit();
		String zunit = cal.getZUnit();
		if (cal.scaled()) {
			String xunits = cal.getUnits();
			String yunits = xunits;
			String zunits = xunits;
			if (nonUniformUnits) {
				xunits = xunit;
				yunits = yunit;
				zunits = zunit;
			}
			double pw = imp.getWidth()*cal.pixelWidth;
			double ph = imp.getHeight()*cal.pixelHeight;
	    	s += "Width:  "+d2s(pw)+" " + xunits+" ("+imp.getWidth()+")\n";
	    	s += "Height:  "+d2s(ph)+" " + yunits+" ("+imp.getHeight()+")\n";
	    	if (slices>1) {
				double pd = slices*cal.pixelDepth;
	    		s += "Depth:  "+d2s(pd)+" " + zunits+" ("+slices+")\n";
	    	}
			s += "Size:  "+ImageWindow.getImageSize(imp)+"\n";
	    	double xResolution = 1.0/cal.pixelWidth;
	    	double yResolution = 1.0/cal.pixelHeight;
	    	if (xResolution==yResolution)
	    		s += "Resolution:  "+d2s(xResolution) + " pixels per "+xunit+"\n";
	    	else {
	    		s += "X Resolution:  "+d2s(xResolution) + " pixels per "+xunit+"\n";
	    		s += "Y Resolution:  "+d2s(yResolution) + " pixels per "+yunit+"\n";
	    	}
	    } else {
	    	s += "Width:  " + imp.getWidth() + " pixels\n";
	    	s += "Height:  " + imp.getHeight() + " pixels\n";
	    	if (stackSize>1)
	    		s += "Depth:  " + slices + " pixels\n";
			s += "Size:  "+ImageWindow.getImageSize(imp)+"\n";
	    }
    	if (stackSize>1) {
    		String vunit = cal.getUnit()+"^3";
    		if (nonUniformUnits)
    			vunit = "("+xunit+" x "+yunit+" x "+zunit+")";
	    	s += "Voxel size: "+d2s(cal.pixelWidth)+"x"+d2s(cal.pixelHeight)+"x"+d2s(cal.pixelDepth)+" "+vunit+"\n";
	    } else {
    		String punit = cal.getUnit()+"^2";
    		if (nonUniformUnits)
    			punit = "("+xunit+" x "+yunit+")";
	    	dp = Tools.getDecimalPlaces(cal.pixelWidth, cal.pixelHeight);
	    	s += "Pixel size: "+d2s(cal.pixelWidth)+"x"+d2s(cal.pixelHeight)+" "+punit+"\n";
	    }

	    s += "ID: "+imp.getID()+"\n";
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
					s += d2s(min) + " - " + d2s(max) + "\n";
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
    		String label = stack.getSliceLabel(slice);
    		if (label!=null && label.contains("\n"))
    			label = stack.getShortSliceLabel(slice);
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
			if (stack.isVirtual()) {
				String stackType = "virtual";
				if (stack instanceof AVI_Reader)
					stackType += " (AVI Reader)";
				if (stack instanceof FileInfoVirtualStack)
					stackType += " (FileInfoVirtualStack)";
				if (stack instanceof ListVirtualStack)
					stackType += " (ListVirtualStack)";
				s += "Stack type: " + stackType+ "\n";
			}
		}
		
		if (imp.isLocked())
			s += "**Locked**\n";
		if (ip.getMinThreshold()==ImageProcessor.NO_THRESHOLD)
			s += "No threshold\n";
	    else {
	    	double lower = ip.getMinThreshold();
	    	double upper = ip.getMaxThreshold();
	    	String uncalibrated = "";
			if (cal.calibrated()) {
				uncalibrated = " ("+(int)lower+"-"+(int)upper+")";
				lower = cal.getCValue((int)lower);
				upper = cal.getCValue((int)upper);
			}
			s += "Threshold: "+d2s(lower)+"-"+d2s(upper)+uncalibrated+"\n";
		}
		ImageCanvas ic = imp.getCanvas();
    	double mag = ic!=null?ic.getMagnification():1.0;
    	if (mag!=1.0)
			s += "Magnification: " + IJ.d2s(mag,2) + "\n";
		if (ic!=null)
			s += "ScaleToFit: " + ic.getScaleToFit() + "\n";

			
	    if (cal.calibrated()) {
	    	s += " \n";
	    	int curveFit = cal.getFunction();
			s += "Calibration function: ";
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
		
		ImageWindow win = imp.getWindow();
		if (win!=null) {
			Point loc = win.getLocation();
			Dimension screen = IJ.getScreenSize();
			s += "Screen location: "+loc.x+","+loc.y+" ("+screen.width+"x"+screen.height+")\n";
		}
		if (IJ.isMacOSX()) {
			String time = " ("+ImageWindow.setMenuBarTime+"ms)";
			s += "SetMenuBarCount: "+Menus.setMenuBarCount+time+"\n";
		}
		
		String zOrigin = stackSize>1||cal.zOrigin!=0.0?","+d2s(cal.zOrigin):"";
		String origin = d2s(cal.xOrigin)+","+d2s(cal.yOrigin)+zOrigin;
		if (!origin.equals("0,0") || cal.getInvertY())
	    	s += "Coordinate origin:  "+origin+"\n";
	    if (cal.getInvertY())
	    	s += "Inverted y coordinates\n";

	    Overlay overlay = imp.getOverlay();
		if (overlay!=null) {
			int n = overlay.size();
			String elements = n==1?" element":" elements";
			String selectable = overlay.isSelectable()?" selectable ":" non-selectable ";
			String hidden = imp.getHideOverlay()?" (hidden)":"";
			s += "Overlay: " + n + selectable + elements + hidden + "\n";
		} else
	    	s += "No overlay\n";

		Interpreter interp = Interpreter.getInstance();
		if (interp!=null)
			s += "Macro is running"+(Interpreter.isBatchMode()?" in batch mode":"")+"\n";

	    Roi roi = imp.getRoi();
	    if (roi == null) {
			if (cal.calibrated())
	    		s += " \n";
	    	s += "No selection\n";
	    } else if (roi instanceof RotatedRectRoi) {
	    	s += "\nRotated rectangle selection\n";
	    	double[] p = ((RotatedRectRoi)roi).getParams();
			double dx = p[2] - p[0];
			double dy = p[3] - p[1];
			double major = Math.sqrt(dx*dx+dy*dy);
			s += "  Length: " + IJ.d2s(major,2) + "\n";
			s += "  Width: " + IJ.d2s(p[4],2) + "\n";
			s += "  X1: " + IJ.d2s(p[0],2) + "\n";
			s += "  Y1: " + IJ.d2s(p[1],2) + "\n";
			s += "  X2: " + IJ.d2s(p[2],2) + "\n";
			s += "  Y2: " + IJ.d2s(p[3],2) + "\n";
	    } else if (roi instanceof EllipseRoi) {
	    	s += "\nElliptical selection\n";
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
		LUT[] luts = imp.getLuts();
		if (luts==null)
			return "";
		String s = "Display ranges\n";
		int n = luts.length;
		if (n>7) n=7;
		for (int i=0; i<n; i++) {
			double min = luts[i].min;
			double max = luts[i].max;
			s += "  " + (i+1) + ": " + d2s(min) + "-" + d2s(max) + "\n";
		}
		return s;
	}
	
	// returns a Y coordinate based on the "Invert Y Coodinates" flag
	private int yy(int y, ImagePlus imp) {
		return Analyzer.updateY(y, imp.getHeight());
	}

	// returns a Y coordinate based on the "Invert Y Coodinates" flag
	private double yy(double y, ImagePlus imp) {
		return Analyzer.updateY(y, imp.getHeight());
	}

	private void showInfo(ImagePlus imp, String info, int width, int height) {
		new TextWindow("Info for "+imp.getTitle(), info, width, height);
		//Editor ed = new Editor();
		//ed.setSize(width, height);
		//ed.create("Info for "+imp.getTitle(), info);
	}
	
    private String d2s(double n) {
		return IJ.d2s(n,Tools.getDecimalPlaces(n));
    }

}
