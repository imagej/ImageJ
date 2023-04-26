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
import java.awt.geom.Rectangle2D;

/** This plugin implements the Image/Show Info command. */
public class ImageInfo implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			showInfo();
		else {
			String info = getImageInfo(imp);
			if (info.contains("----"))
				showInfo(imp, info, 450, 600);
			else {
				int inc = info.contains("No selection")?0:130;
				showInfo(imp, info, 400, 500+inc);
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
		s += "Java version: "+IJ.javaVersion()+"\n";
		s += "Screen size: "+screen.width+"x"+screen.height+"\n";
		s += "GUI scale: "+IJ.d2s(Prefs.getGuiScale(),2)+"\n";
		//s += "Active window: "+WindowManager.getActiveWindow()+"\n";
		String path = Prefs.getCustomPropsPath();
		if (path!=null)
			s += "*Custom properties*: "+ path +"\n";
		path = Prefs.getCustomPrefsPath();
		if (path!=null)
			s += "*Custom preferences*: "+ path +"\n";
		//if (IJ.isMacOSX()) {
		//	String time = " ("+ImageWindow.setMenuBarTime+"ms)";
		//	s += "SetMenuBarCount: "+Menus.setMenuBarCount+time+"\n";
		//}
		new TextWindow("Info", s, 600, 300);
	}

	public String getImageInfo(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		String infoProperty = null;
		boolean isStack = imp.getStackSize()>1;
		if (isStack || imp.hasImageStack()) {
			ImageStack stack = imp.getStack();
			String label = stack.getSliceLabel(imp.getCurrentSlice());
			if (label!=null && label.indexOf('\n')>0)
				infoProperty = label;
		}
		if (infoProperty==null || (isStack && (imp.getStack() instanceof ListVirtualStack))) {
			infoProperty = (String)imp.getProperty("Info");
			if (infoProperty==null)
				infoProperty = getExifData(imp);
		}
		if (imp.getProp("HideInfo")==null) {
			String properties = getImageProperties(imp);
			if (properties!=null) {
				if (infoProperty!=null)
					infoProperty = properties + "\n" + infoProperty;
				else
					infoProperty = properties;
			}
		}
		String info = getInfo(imp, ip);
		if (infoProperty!=null)
			return infoProperty + "--------------------------------------------\n" + info;
		else
			return info;
	}

	public String getExifData(ImagePlus imp) {
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
	    		String lut = getLutInfo(imp);
				s += "(" + lut + ")\n";
				if (imp.getNChannels()>1)
					s += displayRanges(imp);
				else {
					s += "Display range: "+(int)ip.getMin()+"-"+(int)ip.getMax()+"\n";
					ip.resetRoi();
					ImageStatistics stats = ip.getStats();
					s += "Pixel value range: "+(int)stats.min+"-"+(int)stats.max+"\n";
				}
				break;
	    	case ImagePlus.GRAY16: case ImagePlus.GRAY32:
	    		if (type==ImagePlus.GRAY16) {
	    			String sign = cal.isSigned16Bit()?"signed, ":"unsigned, ";
	    			s += "Bits per pixel: 16 ("+sign+getLutInfo(imp)+")\n";
	    		} else
	    			s += "Bits per pixel: 32 (float, "+getLutInfo(imp)+")\n";
				if (imp.getNChannels()>1)
					s += displayRanges(imp);
				else {
					String pvrLabel = "Pixel value range: ";
					s += "Display range: ";
					double min = ip.getMin();
					double max = ip.getMax();
					String dash = "-";
					if (type==ImagePlus.GRAY32||cal.calibrated())
						dash = " - ";
					if (type==ImagePlus.GRAY32)
						s += d2s(min) + dash + d2s(max) + "\n";
					else if (cal.calibrated()) {
						pvrLabel = "Raw pixel value range: ";
						min = cal.getCValue((int)min);
						max = cal.getCValue((int)max);
						s += d2s(min) + dash + d2s(max) + "\n";
					} else
						s += (int)min+dash+(int)max+"\n";
					ip.resetRoi();
					ImageStatistics stats = ip.getStats();
					if (ip.isSigned16Bit()) {
						stats.min -= 32768;
						stats.max -= 32768;
					}
					if (type==ImagePlus.GRAY32)
						s += pvrLabel+d2s(stats.min)+dash+d2s(stats.max)+"\n";
					else
						s += pvrLabel+(int)stats.min+dash+(int)stats.max+"\n";
			}
				break;
	    	case ImagePlus.COLOR_256:
	    		s += "Bits per pixel: 8 (color LUT)\n";
	    		break;
	    	case ImagePlus.COLOR_RGB:
	    		s += "Bits per pixel: 32 (RGB)\n";
	    		break;
    	}
    	String lutName = imp.getProp(LUT.nameKey);
    	if (lutName!=null)
			s += "LUT name: "+lutName+"\n";    		
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
			if (imp.getNFrames()>1 || interval>0.0 || fps!=0.0) {
				s += "Frame: " + number + label + "\n";
				if (fps!=0.0) {
					String sRate = Math.abs(fps-Math.round(fps))<0.00001?IJ.d2s(fps,0):IJ.d2s(fps,5);
					s += "Frame rate: " + sRate + " fps\n";
				} else
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
		} else if (imp.hasImageStack()) { // one image stack
    		String label = imp.getStack().getShortSliceLabel(1);
    		if (label!=null && label.length()>0)
				s += "Image: 1/1 (" + label + ")\n";
		}

		if (imp.isLocked())
			s += "**Locked**\n";
		if (!ip.isThreshold())
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
			int lutMode = ip.getLutUpdateMode();
			String mode = "red";
			switch (lutMode) {
				case ImageProcessor.BLACK_AND_WHITE_LUT: mode="B&W"; break;
				case ImageProcessor.NO_LUT_UPDATE: mode="invisible"; break;
				case ImageProcessor.OVER_UNDER_LUT: mode="over/under"; break;
			}
			s += "Threshold: "+d2s(lower)+"-"+d2s(upper)+uncalibrated+" ("+mode+")\n";
		}
		ImageCanvas ic = imp.getCanvas();
    	double mag = ic!=null?ic.getMagnification():1.0;
    	if (mag!=1.0)
			s += "Magnification: " + IJ.d2s(mag,2) + "\n";
		if (ic!=null)
			s += "ScaleToFit: " + ic.getScaleToFit() + "\n";


	    String valueUnit = cal.getValueUnit();
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
			s += "  Unit: \""+valueUnit+"\"\n";
	    } else if (valueUnit!=null && !valueUnit.equals("Gray Value")) {
			s += "Calibration function: None\n";
			s += "  Unit: \""+valueUnit+"\"\n";
	    } else
	    	s += "Uncalibrated\n";

	    FileInfo fi = imp.getOriginalFileInfo();
		if (fi!=null) {
			if (fi.url!=null && !fi.url.equals(""))
				s += "URL: " + fi.url + "\n";
			else {
				String defaultDir = (fi.directory==null || fi.directory.length()==0)?System.getProperty("user.dir"):"";
				if (defaultDir.length()>0) {
					defaultDir = defaultDir.replaceAll("\\\\", "/");
					defaultDir += "/";
				}
				s += "Path: " + defaultDir + fi.getFilePath() + "\n";
			}
		}

		ImageWindow win = imp.getWindow();
		if (win!=null) {
			Point loc = win.getLocation();
			Rectangle bounds = GUI.getScreenBounds(win);
			s += "Screen location: "+(loc.x-bounds.x)+","+(loc.y-bounds.y)+" ("+bounds.width+"x"+bounds.height+")\n";
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

	    String pinfo = imp.getPropsInfo();
	    if (!pinfo.equals("0"))
	   		s += "Properties: " + pinfo + "\n";
	   	else
	   		s += "No properties\n";
	   	
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
	    if (roi==null) {
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
	    	if (roi instanceof Line) {
	    		Line line = (Line)roi;
	    		s += "  X1: " + IJ.d2s(cal.getX(line.x1d)) + "\n";
	    		s += "  Y1: " + IJ.d2s(cal.getY(line.y1d, imp.getHeight())) + "\n";
	    		s += "  X2: " + IJ.d2s(cal.getX(line.x2d)) + "\n";
	    		s += "  Y2: " + IJ.d2s(cal.getY(line.y2d, imp.getHeight())) + "\n";
			} else {
				Rectangle2D.Double r = roi.getFloatBounds();
				int decimals = r.x==(int)r.x && r.y==(int)r.y && r.width==(int)r.width && r.height==(int)r.height ?
						0 : 2;
				if (cal.scaled()) {
					s += "  X: " + IJ.d2s(cal.getX(r.x)) + " (" + IJ.d2s(r.x, decimals) + ")\n";
					s += "  Y: " + IJ.d2s(cal.getY(r.y,imp.getHeight())) + " (" +  IJ.d2s(yy(r.y, imp), decimals) + ")\n";
					s += "  Width: " + IJ.d2s(r.width*cal.pixelWidth) + " (" +  IJ.d2s(r.width, decimals) + ")\n";
					s += "  Height: " + IJ.d2s(r.height*cal.pixelHeight) + " (" +  IJ.d2s(r.height, decimals) + ")\n";
				} else {
					s += "  X: " + IJ.d2s(r.x, decimals) + "\n";
					s += "  Y: " + IJ.d2s(yy(r.y, imp), decimals) + "\n";
					s += "  Width: " + IJ.d2s(r.width, decimals) + "\n";
					s += "  Height: " + IJ.d2s(r.height, decimals) + "\n";
				}
			}
	    }

		return s;
	}
	
	private String getLutInfo(ImagePlus imp) {
		String lut = "LUT";
		if (imp.getProcessor().isColorLut())
			lut = "color " + lut;
		else
			lut = "grayscale " + lut;
		if (imp.isInvertedLut())
			lut = "inverting " + lut;
		return lut;
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
    
    private String getImageProperties(ImagePlus imp) {
    	String s = "";
    	String[] props = imp.getPropertiesAsArray();
    	if (props==null)
    		return null;
		for (int i=0; i<props.length; i+=2) {
			String key = props[i];
			String value = props[i+1];
			if (LUT.nameKey.equals(key) || "UniqueName".equals(key))
				continue;
			if (key!=null && value!=null && !(key.equals("ShowInfo")||key.equals("Slice_Label"))) {
				if (value.length()<80)
					s += key + ": " + value + "\n";
				else
					s += key + ": <" + value.length() + " characters>\n";
			}
		}
		return  (s.length()>0)?s:null;
    }

}
