package ij.io;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.*;

/** Opens or reverts an image specified by a FileInfo object. Images can
	be loaded from either a file (directory+fileName) or a URL (url+fileName). */
public class FileOpener {

	private FileInfo fi;
	private int width, height;
	private double displayMin, displayMax;

	public FileOpener(FileInfo fi) {
		this.fi = fi;
		if (fi!=null) {
			width = fi.width;
			height = fi.height;
		}
		if (IJ.debugMode) IJ.log("FileOpener: "+fi);
	}
	
	/** Opens the image and displays it. */
	public void open() {
		open(true);
	}
	
	/** Opens the image. Displays it if 'show' is
	true. Returns an ImagePlus object if successful. */
	public ImagePlus open(boolean show) {
		Image img;
		ImagePlus imp=null;
		Object pixels;
		ProgressBar pb=null;
	    ImageProcessor ip;
		
		ColorModel cm = createColorModel(fi);
		if (fi.nImages>1)
			{return openStack(cm, show);}
		switch (fi.fileType) {
			case FileInfo.GRAY8:
			case FileInfo.COLOR8:
			case FileInfo.BITMAP:
				pixels = readPixels(fi);
				if (pixels==null) return null;
			    //img = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(width, height, cm, (byte[])pixels, 0, width));
		        //imp = new ImagePlus(fi.fileName, img);
				ip = new ByteProcessor(width, height, (byte[])pixels, cm);
    			imp = new ImagePlus(fi.fileName, ip);
				break;
			case FileInfo.GRAY16_SIGNED:
			case FileInfo.GRAY16_UNSIGNED:
				pixels = readPixels(fi);
				if (pixels==null) return null;
	    		ip = new ShortProcessor(width, height, (short[])pixels, cm);
       			imp = new ImagePlus(fi.fileName, ip);
				break;
			case FileInfo.GRAY32_INT:
			case FileInfo.GRAY32_FLOAT:
				pixels = readPixels(fi);
				if (pixels==null) return null;
	    		ip = new FloatProcessor(width, height, (float[])pixels, cm);
       			imp = new ImagePlus(fi.fileName, ip);
				break;
			case FileInfo.RGB:
			case FileInfo.BGR:
			case FileInfo.ARGB:
			case FileInfo.RGB_PLANAR:
				pixels = readPixels(fi);
				if (pixels==null) return null;
	    		img = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(width, height, (int[])pixels, 0, width));
        		imp = new ImagePlus(fi.fileName, img);
				break;
		}
		imp.setFileInfo(fi);
		setCalibration(imp);
		if (show) imp.show();
		IJ.showProgress(1.0);
		return imp;
	}

	/** Opens a stack of images. */
	ImagePlus openStack(ColorModel cm, boolean show) {
		ImageStack stack = new ImageStack(fi.width, fi.height, cm);
		int skip = fi.offset;
		Object pixels;
		try {
			ImageReader reader = new ImageReader(fi);
			InputStream is = createInputStream(fi);
			for (int i=1; i<=fi.nImages; i++) {
				IJ.showStatus("Reading: " + i + "/" + fi.nImages);
				pixels = reader.readPixels(is, skip);
				if (pixels==null) break;
				stack.addSlice(null, pixels);
				skip = fi.gapBetweenImages;
				IJ.showProgress((double)i/fi.nImages);
			}
			is.close();
		}
		catch (Exception e) {
			IJ.write("" + e);
		}
		catch(OutOfMemoryError e) {
			IJ.outOfMemory(fi.fileName);
			stack.trim();
		}
		IJ.showProgress(1.0);
		if (stack.getSize()==0)
			return null;
		ImagePlus imp = new ImagePlus(fi.fileName, stack);
		if (show) imp.show();
		imp.setFileInfo(fi);
		setCalibration(imp);
		IJ.showProgress(1.0);
		return imp;
	}

	/** Restores original disk or network version of image. */
	public void revertToSaved(ImagePlus imp) {
		Image img;
		ProgressBar pb = IJ.getInstance().getProgressBar();
		ImageProcessor ip;
		
		if (fi.fileFormat==fi.GIF_OR_JPG) {
			// restore gif or jpg
			img = Toolkit.getDefaultToolkit().getImage(fi.directory + fi.fileName);
			imp.setImage(img);
			if (imp.getType()==ImagePlus.COLOR_RGB)
				Opener.convertGrayJpegTo8Bits(imp);
	    	return;
		}
				
		if (fi.nImages>1)
			return;
		
		ColorModel cm;
		if (fi.url==null || fi.url.equals(""))
			IJ.showStatus("Loading: " + fi.directory + fi.fileName);
		else
			IJ.showStatus("Loading: " + fi.url + fi.fileName);
		Object pixels = readPixels(fi);
		if (pixels==null) return;
		cm = createColorModel(fi);
		switch (fi.fileType) {
			case FileInfo.GRAY8:
			case FileInfo.COLOR8:
			case FileInfo.BITMAP:
				ip = new ByteProcessor(width, height, (byte[])pixels, cm);
		        imp.setProcessor(null, ip);
				break;
			case FileInfo.GRAY16_SIGNED:
			case FileInfo.GRAY16_UNSIGNED:
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
			case FileInfo.RGB_PLANAR:
	    		img = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(width, height, (int[])pixels, 0, width));
		        imp.setImage(img);
				break;
		}
	}
	
	void setCalibration(ImagePlus imp) {
		decodeDescriptionString();
		Calibration cal = null;
		if (fi.pixelWidth>0.0 && fi.unit!=null) {
			cal = new Calibration(imp);
			cal.pixelWidth = fi.pixelWidth;
			cal.pixelHeight = fi.pixelHeight;
			cal.pixelDepth = fi.pixelDepth;
			cal.setUnit(fi.unit);
		}
		
		if (fi.valueUnit!=null) {
			int f = fi.calibrationFunction;
			if ((f>=Calibration.STRAIGHT_LINE && f<=Calibration.GAMMA_VARIATE && fi.coefficients!=null)
			||f==Calibration.UNCALIBRATED_OD) {
				if (cal==null) cal = new Calibration(imp);
				cal.setFunction(f, fi.coefficients, fi.valueUnit);
			}
		}
		
		if (fi.frameInterval!=0.0) {
			if (cal==null) cal = new Calibration(imp);
			cal.frameInterval = fi.frameInterval;
		}
		
		if (fi.fileType==FileInfo.GRAY16_SIGNED) {
			if (IJ.debugMode) IJ.log("16-bit signed");
			if (cal==null) cal = new Calibration(imp);
			double[] coeff = new double[2];
			coeff[0] = -32768.0;
			coeff[1] = 1.0;
			cal.setFunction(Calibration.STRAIGHT_LINE, coeff, "gray value");
		}
		
		if (cal!=null)
			imp.setCalibration(cal);
		
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
	}

	/** Returns an IndexColorModel for the image specified by this FileInfo. */
	public static ColorModel createColorModel(FileInfo fi) {
		if (fi.fileType==FileInfo.COLOR8 && fi.lutSize>0)
			return new IndexColorModel(8, fi.lutSize, fi.reds, fi.greens, fi.blues);
		else
			return LookUpTable.createGrayscaleColorModel(fi.whiteIsZero);
	}

	/** Returns an InputStream for the image described by this FileInfo. */
	public static InputStream createInputStream(FileInfo fi) throws IOException, MalformedURLException {
		if (fi.inputStream!=null)
			return fi.inputStream;
		else if (fi.url!=null && !fi.url.equals(""))
			return new URL(fi.url+fi.fileName).openStream();
		else
			return new FileInputStream(new File(fi.directory + fi.fileName));
	}
	
	/** Reads the pixel data from an image described by a FileInfo object. */
	Object readPixels(FileInfo fi) {
		Object pixels = null;
		try {
			InputStream is = createInputStream(fi);
			ImageReader reader = new ImageReader(fi);
			pixels = reader.readPixels(is);
			is.close();
		}
		catch (Exception e) {
			IJ.write("FileOpener.readPixels(): " + e);
		}
		return pixels;
	}

	public void decodeDescriptionString() {
		if (fi.description==null || fi.description.length()<7)
			return;
		if (IJ.debugMode)
			IJ.log("Image Description: " + new String(fi.description).replace('\n',' '));
		if (!fi.description.startsWith("ImageJ"))
			return;
		Properties props = new Properties();
		InputStream is = new ByteArrayInputStream(fi.description.getBytes());
		try {props.load(is); is.close();}
		catch (IOException e) {return;}
		fi.unit = props.getProperty("unit","");
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
		if (fi.nImages>1) {
			double spacing = getDouble(props,"spacing");
			if (spacing!=0.0)
				fi.pixelDepth = spacing;
			n = getNumber(props,"fps");
			double fps = getDouble(props,"fps");
			if (fps!=0.0)
				fi.frameInterval = 1.0/fps;
		}
		displayMin = getDouble(props,"min");
		displayMax = getDouble(props,"max");
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
	

}