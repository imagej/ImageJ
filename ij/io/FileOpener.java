package ij.io;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.*;
import ij.plugin.frame.*;

/**
 * Opens or reverts an image specified by a FileInfo object. Images can
 * be loaded from either a file (directory+fileName) or a URL (url+fileName).
 * Here is an example:	
 * <pre>
 *   public class FileInfo_Test implements PlugIn {
 *     public void run(String arg) {
 *       FileInfo fi = new FileInfo();
 *       fi.width = 256;
 *       fi.height = 254;
 *       fi.offset = 768;
 *       fi.fileName = "blobs.tif";
 *       fi.directory = "/Users/wayne/Desktop/";
 *       new FileOpener(fi).open();
 *     }  
 *   }	
 * </pre> 
 */
public class FileOpener {

	private FileInfo fi;
	private int width, height;
	private static boolean showConflictMessage = true;
	private double minValue, maxValue;
	private static boolean silentMode;

	public FileOpener(FileInfo fi) {
		this.fi = fi;
		if (fi!=null) {
			width = fi.width;
			height = fi.height;
		}
		if (IJ.debugMode) IJ.log("FileInfo: "+fi);
	}
	
	/** Opens the image and returns it has an ImagePlus object. */
	public ImagePlus openImage() {
		boolean wasRecording = Recorder.record;
		Recorder.record = false;
		ImagePlus imp = open(false);
		Recorder.record = wasRecording;
		return imp;
	}

	/** Opens the image and displays it. */
	public void open() {
		open(true);
	}
	
	/** Obsolete, replaced by openImage() and open(). */
	public ImagePlus open(boolean show) {

		ImagePlus imp=null;
		Object pixels;
		ProgressBar pb=null;
	    ImageProcessor ip;
		
		ColorModel cm = createColorModel(fi);
		if (fi.nImages>1)
			return openStack(cm, show);
		switch (fi.fileType) {
			case FileInfo.GRAY8:
			case FileInfo.COLOR8:
			case FileInfo.BITMAP:
				pixels = readPixels(fi);
				if (pixels==null) return null;
				ip = new ByteProcessor(width, height, (byte[])pixels, cm);
    			imp = new ImagePlus(fi.fileName, ip);
				break;
			case FileInfo.GRAY16_SIGNED:
			case FileInfo.GRAY16_UNSIGNED:
			case FileInfo.GRAY12_UNSIGNED:
				pixels = readPixels(fi);
				if (pixels==null) return null;
	    		ip = new ShortProcessor(width, height, (short[])pixels, cm);
       			imp = new ImagePlus(fi.fileName, ip);
				break;
			case FileInfo.GRAY32_INT:
			case FileInfo.GRAY32_UNSIGNED:
			case FileInfo.GRAY32_FLOAT:
			case FileInfo.GRAY24_UNSIGNED:
			case FileInfo.GRAY64_FLOAT:
				pixels = readPixels(fi);
				if (pixels==null) return null;
	    		ip = new FloatProcessor(width, height, (float[])pixels, cm);
       			imp = new ImagePlus(fi.fileName, ip);
				break;
			case FileInfo.RGB:
			case FileInfo.BGR:
			case FileInfo.ARGB:
			case FileInfo.ABGR:
			case FileInfo.BARG:
			case FileInfo.RGB_PLANAR:
			case FileInfo.CMYK:
				pixels = readPixels(fi);
				if (pixels==null) return null;
				ip = new ColorProcessor(width, height, (int[])pixels);
				if (fi.fileType==FileInfo.CMYK)
					ip.invert();
				imp = new ImagePlus(fi.fileName, ip);
				break;
			case FileInfo.RGB48:
			case FileInfo.RGB48_PLANAR:
				boolean planar = fi.fileType==FileInfo.RGB48_PLANAR;
				Object[] pixelArray = (Object[])readPixels(fi);
				if (pixelArray==null) return null;
				int nChannels = 3;
				ImageStack stack = new ImageStack(width, height);
				stack.addSlice("Red", pixelArray[0]);
				stack.addSlice("Green", pixelArray[1]);
				stack.addSlice("Blue", pixelArray[2]);
				if (fi.samplesPerPixel==4 && pixelArray.length==4) {
					stack.addSlice("Gray", pixelArray[3]);
					nChannels = 4;
				}
        		imp = new ImagePlus(fi.fileName, stack);
        		imp.setDimensions(nChannels, 1, 1);
        		if (planar)
        			imp.getProcessor().resetMinAndMax();
				imp.setFileInfo(fi);
				int mode = IJ.COMPOSITE;
				if (fi.description!=null) {
					if (fi.description.indexOf("mode=color")!=-1)
					mode = IJ.COLOR;
					else if (fi.description.indexOf("mode=gray")!=-1)
					mode = IJ.GRAYSCALE;
				}
        		imp = new CompositeImage(imp, mode);
        		if (!planar && fi.displayRanges==null) {
        			if (nChannels==4)
        				((CompositeImage)imp).resetDisplayRanges();
        			else {
						for (int c=1; c<=3; c++) {
							imp.setPosition(c, 1, 1);
							imp.setDisplayRange(minValue, maxValue);
						}
						imp.setPosition(1, 1, 1);
       				}
        		}
        		if (fi.whiteIsZero) // cmyk?
        			IJ.run(imp, "Invert", "");
				break;
		}
		imp.setFileInfo(fi);
		setCalibration(imp);
		if (fi.info!=null)
			imp.setProperty("Info", fi.info);
		if (fi.sliceLabels!=null&&fi.sliceLabels.length==1&&fi.sliceLabels[0]!=null)
			imp.setProperty("Label", fi.sliceLabels[0]);
		if (fi.plot!=null) try {
			Plot plot = new Plot(imp, new ByteArrayInputStream(fi.plot));
			imp.setProperty(Plot.PROPERTY_KEY, plot);
		} catch (Exception e) { IJ.handleException(e); }
		if (fi.roi!=null)
			decodeAndSetRoi(imp, fi);
		if (fi.overlay!=null)
			setOverlay(imp, fi.overlay);
		if (show) imp.show();
		return imp;
	}
	
	void setOverlay(ImagePlus imp, byte[][] rois) {
		Overlay overlay = new Overlay();
		Overlay proto = null;
		for (int i=0; i<rois.length; i++) {
			Roi roi = RoiDecoder.openFromByteArray(rois[i]);
			if (roi==null)
				continue;
			if (proto==null) {
				proto = roi.getPrototypeOverlay();
				overlay.drawLabels(proto.getDrawLabels());
				overlay.drawNames(proto.getDrawNames());
				overlay.drawBackgrounds(proto.getDrawBackgrounds());
				overlay.setLabelColor(proto.getLabelColor());
				overlay.setLabelFont(proto.getLabelFont(), proto.scalableLabels());
			}
			overlay.add(roi);
		}
		imp.setOverlay(overlay);
	}

	/** Opens a stack of images. */
	ImagePlus openStack(ColorModel cm, boolean show) {
		ImageStack stack = new ImageStack(fi.width, fi.height, cm);
		long skip = fi.getOffset();
		Object pixels;
		try {
			ImageReader reader = new ImageReader(fi);
			InputStream is = createInputStream(fi);
			if (is==null)
				return null;
			IJ.resetEscape();
			for (int i=1; i<=fi.nImages; i++) {
				if (!silentMode)
					IJ.showStatus("Reading: " + i + "/" + fi.nImages);
				if (IJ.escapePressed()) {
					IJ.beep();
					IJ.showProgress(1.0);
					silentMode = false;
					return null;
				}
				pixels = reader.readPixels(is, skip);
				if (pixels==null)
					break;
				stack.addSlice(null, pixels);
				skip = fi.getGap();
				if (!silentMode)
					IJ.showProgress(i, fi.nImages);
			}
			is.close();
		}
		catch (Exception e) {
			IJ.log("" + e);
		}
		catch(OutOfMemoryError e) {
			IJ.outOfMemory(fi.fileName);
			stack.trim();
		}
		if (!silentMode) IJ.showProgress(1.0);
		if (stack.size()==0)
			return null;
		if (fi.sliceLabels!=null && fi.sliceLabels.length<=stack.size()) {
			for (int i=0; i<fi.sliceLabels.length; i++)
				stack.setSliceLabel(fi.sliceLabels[i], i+1);
		}
		ImagePlus imp = new ImagePlus(fi.fileName, stack);
		if (fi.info!=null)
			imp.setProperty("Info", fi.info);
		if (fi.roi!=null)
			decodeAndSetRoi(imp, fi);
		if (fi.overlay!=null)
			setOverlay(imp, fi.overlay);
		if (show) imp.show();
		imp.setFileInfo(fi);
		setCalibration(imp);
		ImageProcessor ip = imp.getProcessor();
		if (ip.getMin()==ip.getMax())  // find stack min and max if first slice is blank
			setStackDisplayRange(imp);
		if (!silentMode) IJ.showProgress(1.0);
		return imp;
	}
	
	private void decodeAndSetRoi(ImagePlus imp, FileInfo fi) {
		Roi roi = RoiDecoder.openFromByteArray(fi.roi);
		imp.setRoi(roi);
		if ((roi instanceof PointRoi) && ((PointRoi)roi).getNCounters()>1) 
			IJ.setTool("multi-point");
	}

	void setStackDisplayRange(ImagePlus imp) {
		ImageStack stack = imp.getStack();
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		int n = stack.size();
		for (int i=1; i<=n; i++) {
			if (!silentMode)
				IJ.showStatus("Calculating stack min and max: "+i+"/"+n);
			ImageProcessor ip = stack.getProcessor(i);
			ip.resetMinAndMax();
			if (ip.getMin()<min)
				min = ip.getMin();
			if (ip.getMax()>max)
				max = ip.getMax();
		}
		imp.getProcessor().setMinAndMax(min, max);
		imp.updateAndDraw();
	}
	
	/** Restores the original version of the specified image. */
	public void revertToSaved(ImagePlus imp) {
		if (fi==null)
			return;
		String path = fi.getFilePath();
		if (fi.url!=null && !fi.url.equals("") && (fi.directory==null||fi.directory.equals("")))
			path = fi.url;
		IJ.showStatus("Loading: " + path);
		ImagePlus imp2 = null;
		if (!path.endsWith(".raw"))
			imp2 = IJ.openImage(path);
		if (imp2!=null)
			imp.setImage(imp2);
		else {
			if (fi.nImages>1)
				return;
			Object pixels = readPixels(fi);
			if (pixels==null) return;
			ColorModel cm = createColorModel(fi);
			ImageProcessor ip = null;
			switch (fi.fileType) {
				case FileInfo.GRAY8:
				case FileInfo.COLOR8:
				case FileInfo.BITMAP:
					ip = new ByteProcessor(width, height, (byte[])pixels, cm);
					imp.setProcessor(null, ip);
					break;
				case FileInfo.GRAY16_SIGNED:
				case FileInfo.GRAY16_UNSIGNED:
				case FileInfo.GRAY12_UNSIGNED:
					ip = new ShortProcessor(width, height, (short[])pixels, cm);
					imp.setProcessor(null, ip);
					break;
				case FileInfo.GRAY32_INT:
				case FileInfo.GRAY32_FLOAT:
					ip = new FloatProcessor(width, height, (float[])pixels, cm);
					imp.setProcessor(null, ip);
					break;
				case FileInfo.RGB:
				case FileInfo.BGR:
				case FileInfo.ARGB:
				case FileInfo.ABGR:
				case FileInfo.RGB_PLANAR:
					Image img = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(width, height, (int[])pixels, 0, width));
					imp.setImage(img);
					break;
				case FileInfo.CMYK:
					ip = new ColorProcessor(width, height, (int[])pixels);
					ip.invert();
					imp.setProcessor(null, ip);
					break;
			}
		}
	}
	
	void setCalibration(ImagePlus imp) {
		if (fi.fileType==FileInfo.GRAY16_SIGNED) {
			if (IJ.debugMode) IJ.log("16-bit signed");
 			imp.getLocalCalibration().setSigned16BitCalibration();
		}
		Properties props = decodeDescriptionString(fi);
		Calibration cal = imp.getCalibration();
		boolean calibrated = false;
		if (fi.pixelWidth>0.0 && fi.unit!=null) {
			if (Prefs.convertToMicrons && fi.pixelWidth<=0.0001 && fi.unit.equals("cm")) {
				fi.pixelWidth *= 10000.0;
				fi.pixelHeight *= 10000.0;
				if (fi.pixelDepth!=1.0)
					fi.pixelDepth *= 10000.0;
				fi.unit = "um";
			}
			cal.pixelWidth = fi.pixelWidth;
			cal.pixelHeight = fi.pixelHeight;
			cal.pixelDepth = fi.pixelDepth;
			cal.setUnit(fi.unit);
			calibrated = true;
		}
		
		if (fi.valueUnit!=null) {
			if (imp.getBitDepth()==32)
				cal.setValueUnit(fi.valueUnit);
			else {
				int f = fi.calibrationFunction;
				if ((f>=Calibration.STRAIGHT_LINE && f<=Calibration.EXP_RECOVERY && fi.coefficients!=null)
				|| f==Calibration.UNCALIBRATED_OD) {
					boolean zeroClip = props!=null && props.getProperty("zeroclip", "false").equals("true");	
					cal.setFunction(f, fi.coefficients, fi.valueUnit, zeroClip);
					calibrated = true;
				}
			}
		}
		
		if (calibrated)
			checkForCalibrationConflict(imp, cal);
		
		if (fi.frameInterval!=0.0)
			cal.frameInterval = fi.frameInterval;
		
		if (props==null)
			return;
					
		cal.xOrigin = getDouble(props,"xorigin");
		cal.yOrigin = getDouble(props,"yorigin");
		cal.zOrigin = getDouble(props,"zorigin");
		cal.setInvertY(getBoolean(props, "inverty"));
		cal.info = props.getProperty("info");		
				
		cal.fps = getDouble(props,"fps");
		cal.loop = getBoolean(props, "loop");
		cal.frameInterval = getDouble(props,"finterval");
		cal.setTimeUnit(props.getProperty("tunit", "sec"));
		cal.setYUnit(props.getProperty("yunit"));
		cal.setZUnit(props.getProperty("zunit"));

		double displayMin = getDouble(props,"min");
		double displayMax = getDouble(props,"max");
		if (!(displayMin==0.0&&displayMax==0.0)) {
			int type = imp.getType();
			ImageProcessor ip = imp.getProcessor();
			if (type==ImagePlus.GRAY8 || type==ImagePlus.COLOR_256)
				ip.setMinAndMax(displayMin, displayMax);
			else if (type==ImagePlus.GRAY16 || type==ImagePlus.GRAY32) {
				if (ip.getMin()!=displayMin || ip.getMax()!=displayMax)
					ip.setMinAndMax(displayMin, displayMax);
			}
		}
		
		if (getBoolean(props, "8bitcolor"))
			imp.setTypeToColor256(); // set type to COLOR_256
		
		int stackSize = imp.getStackSize();
		if (stackSize>1) {
			int channels = (int)getDouble(props,"channels");
			int slices = (int)getDouble(props,"slices");
			int frames = (int)getDouble(props,"frames");
			if (channels==0) channels = 1;
			if (slices==0) slices = 1;
			if (frames==0) frames = 1;
			//IJ.log("setCalibration: "+channels+"  "+slices+"  "+frames);
			if (channels*slices*frames==stackSize) {
				imp.setDimensions(channels, slices, frames);
				if (getBoolean(props, "hyperstack"))
					imp.setOpenAsHyperStack(true);
			}
		}
	}

		
	void checkForCalibrationConflict(ImagePlus imp, Calibration cal) {
		Calibration gcal = imp.getGlobalCalibration();
		if  (gcal==null || !showConflictMessage || IJ.isMacro())
			return;
		if (cal.pixelWidth==gcal.pixelWidth && cal.getUnit().equals(gcal.getUnit()))
			return;
		GenericDialog gd = new GenericDialog(imp.getTitle());
		gd.addMessage("The calibration of this image conflicts\nwith the current global calibration.");
		gd.addCheckbox("Disable_Global Calibration", true);
		gd.addCheckbox("Disable_these Messages", false);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		boolean disable = gd.getNextBoolean();
		if (disable) {
			imp.setGlobalCalibration(null);
			imp.setCalibration(cal);
			WindowManager.repaintImageWindows();
		}
		boolean dontShow = gd.getNextBoolean();
		if (dontShow) showConflictMessage = false;
	}

	/** Returns an IndexColorModel for the image specified by this FileInfo. */
	public ColorModel createColorModel(FileInfo fi) {
		if (fi.lutSize>0)
			return new IndexColorModel(8, fi.lutSize, fi.reds, fi.greens, fi.blues);
		else
			return LookUpTable.createGrayscaleColorModel(fi.whiteIsZero);
	}

	/** Returns an InputStream for the image described by this FileInfo. */
	public InputStream createInputStream(FileInfo fi) throws IOException, MalformedURLException {
		InputStream is = null;
		boolean gzip = fi.fileName!=null && (fi.fileName.endsWith(".gz")||fi.fileName.endsWith(".GZ"));
		if (fi.inputStream!=null)
			is = fi.inputStream;
		else if (fi.url!=null && !fi.url.equals(""))
			is = new URL(fi.url+fi.fileName).openStream();
		else {
			if (fi.directory.length()>0 && !(fi.directory.endsWith(Prefs.separator)||fi.directory.endsWith("/")))
				fi.directory += Prefs.separator;
		    File f = new File(fi.getFilePath());
		    if (gzip) fi.compression = FileInfo.COMPRESSION_UNKNOWN;
		    if (f==null || !f.exists() || f.isDirectory() || !validateFileInfo(f, fi))
		    	is = null;
		    else
				is = new FileInputStream(f);
		}
		if (is!=null) {
			if (fi.compression>=FileInfo.LZW)
				is = new RandomAccessStream(is);
			else if (gzip)
				is = new GZIPInputStream(is, 50000);
		}
		return is;
	}
	
	static boolean validateFileInfo(File f, FileInfo fi) {
		long offset = fi.getOffset();
		long length = 0;
		if (fi.width<=0 || fi.height<=0) {
		   error("Width or height <= 0.", fi, offset, length);
		   return false;
		}
		if (offset>=0 && offset<1000L)
			 return true;
		if (offset<0L) {
		   error("Offset is negative.", fi, offset, length);
		   return false;
		}
		if (fi.fileType==FileInfo.BITMAP || fi.compression!=FileInfo.COMPRESSION_NONE)
			return true;
		length = f.length();
		long size = fi.width*fi.height*fi.getBytesPerPixel();
		size = fi.nImages>1?size:size/4;
		if (fi.height==1) size = 0; // allows plugins to read info of unknown length at end of file
		if (offset+size>length) {
		   error("Offset + image size > file length.", fi, offset, length);
		   return false;
		}
		return true;
	}

	static void error(String msg, FileInfo fi, long offset, long length) {
		String msg2 = "FileInfo parameter error. \n"
			+msg + "\n \n"
			+"  Width: " + fi.width + "\n"
			+"  Height: " + fi.height + "\n"
			+"  Offset: " + offset + "\n"
			+"  Bytes/pixel: " + fi.getBytesPerPixel() + "\n"
			+(length>0?"  File length: " + length + "\n":"");
		if (silentMode) {
			IJ.log("Error opening "+fi.getFilePath());
			IJ.log(msg2);
		} else
			IJ.error("FileOpener", msg2);
	}


	/** Reads the pixel data from an image described by a FileInfo object. */
	Object readPixels(FileInfo fi) {
		Object pixels = null;
		try {
			InputStream is = createInputStream(fi);
			if (is==null)
				return null;
			ImageReader reader = new ImageReader(fi);
			pixels = reader.readPixels(is);
			minValue = reader.min;
			maxValue = reader.max;
			is.close();
		}
		catch (Exception e) {
			if (!Macro.MACRO_CANCELED.equals(e.getMessage()))
				IJ.handleException(e);
		}
		return pixels;
	}

	public Properties decodeDescriptionString(FileInfo fi) {
		if (fi.description==null || fi.description.length()<7)
			return null;
		if (IJ.debugMode)
			IJ.log("Image Description: " + new String(fi.description).replace('\n',' '));
		if (!fi.description.startsWith("ImageJ"))
			return null;
		Properties props = new Properties();
		InputStream is = new ByteArrayInputStream(fi.description.getBytes());
		try {props.load(is); is.close();}
		catch (IOException e) {return null;}
		String dsUnit = props.getProperty("unit","");
		if ("cm".equals(fi.unit) && "um".equals(dsUnit)) {
			fi.pixelWidth *= 10000;
			fi.pixelHeight *= 10000;
		}
		fi.unit = dsUnit;
		Double n = getNumber(props,"cf");
		if (n!=null) fi.calibrationFunction = n.intValue();
		double c[] = new double[5];
		int count = 0;
		for (int i=0; i<5; i++) {
			n = getNumber(props,"c"+i);
			if (n==null) break;
			c[i] = n.doubleValue();
			count++;
		}
		if (count>=2) {
			fi.coefficients = new double[count];
			for (int i=0; i<count; i++)
				fi.coefficients[i] = c[i];			
		}
		fi.valueUnit = props.getProperty("vunit");
		n = getNumber(props,"images");
		if (n!=null && n.doubleValue()>1.0)
		fi.nImages = (int)n.doubleValue();
		n = getNumber(props, "spacing");
		if (n!=null) {
			double spacing = n.doubleValue();
			if (spacing<0) spacing = -spacing;
			fi.pixelDepth = spacing;
		}
		String name = props.getProperty("name");
		if (name!=null)
			fi.fileName = name;
		return props;
	}

	private Double getNumber(Properties props, String key) {
		String s = props.getProperty(key);
		if (s!=null) {
			try {
				return Double.valueOf(s);
			} catch (NumberFormatException e) {}
		}	
		return null;
	}
	
	private double getDouble(Properties props, String key) {
		Double n = getNumber(props, key);
		return n!=null?n.doubleValue():0.0;
	}
	
	private boolean getBoolean(Properties props, String key) {
		String s = props.getProperty(key);
		return s!=null&&s.equals("true")?true:false;
	}
	
	public static void setShowConflictMessage(boolean b) {
		showConflictMessage = b;
	}
	
	static void setSilentMode(boolean mode) {
		silentMode = mode;
	}


}
