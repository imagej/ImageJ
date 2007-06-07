package ij.io;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.*;

/** Opens or reverts an image specified by a FileInfo object. Images can
	be loaded from either a file (directory+fileName) or a URL (url+fileName). */
public class FileOpener {

	private FileInfo fi;
	private int width, height;

	public FileOpener(FileInfo fi) {
		this.fi = fi;
		if (fi!=null) {
			width = fi.width;
			height = fi.height;
		}
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
			case FileInfo.RGB_PLANAR:
				pixels = readPixels(fi);
				if (pixels==null) return null;
	    		img = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(width, height, (int[])pixels, 0, width));
        		imp = new ImagePlus(fi.fileName, img);
				break;
		}
		imp.setFileInfo(fi);
		setResolution(fi, imp);
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
		setResolution(fi, imp);
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
			case FileInfo.RGB_PLANAR:
	    		img = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(width, height, (int[])pixels, 0, width));
		        imp.setImage(img);
				break;
		}
	}
	
	static void setResolution(FileInfo fi, ImagePlus imp) {
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
			if ((f>=Calibration.STRAIGHT_LINE && f<=Calibration.RODBARD && fi.coefficients!=null)
			||f==Calibration.UNCALIBRATED_OD) {
				if (cal==null) cal = new Calibration(imp);
				cal.setFunction(f, fi.coefficients, fi.valueUnit);
			}
		}
		if (cal!=null)
			imp.setCalibration(cal);
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

}