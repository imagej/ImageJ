package ij.io;
import java.awt.*;
import java.io.*;
import java.util.zip.*;
import ij.*;
import ij.process.*;
import ij.measure.Calibration;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.Recorder;


/** Saves images in tiff, gif, jpeg, raw, zip and text format. */
public class FileSaver {

	private static String defaultDirectory = null;
	private ImagePlus imp;
	private FileInfo fi;
	private String name;
	private String directory;

	/** Constructs a FileSaver from an ImagePlus. */
	public FileSaver(ImagePlus imp) {
		this.imp = imp;
		fi = imp.getFileInfo();
	}

	/** Resaves the image. Calls saveAsTiff() if this is a new image, not a TIFF, a 
		stack or if the image was loaded using a URL. Returns false if saveAsTiff() is
		called and the user selects cancel in the file save dialog box. */
	public boolean save() {
		FileInfo ofi = null;
		if (imp!=null) ofi = imp.getOriginalFileInfo();
		boolean validName = ofi!=null && imp.getTitle().equals(ofi.fileName);
		if (validName && ofi.fileFormat==FileInfo.TIFF && imp.getStackSize()==1 && ofi.nImages==1 && (ofi.url==null||ofi.url.equals(""))) {
            name = imp.getTitle();
            directory = ofi.directory;
			String path = directory+name;
			File f = new File(path);
			if (!IJ.macroRunning() && f!=null && f.exists()) {
				if (!IJ.showMessageWithCancel("Save as TIFF", "The file "+ofi.fileName+" already exists.\nDo you want to replace it?"))
					return false;
			}
			IJ.showStatus("Saving "+path);
		    return saveAsTiff(path);
		} else
			return saveAsTiff();
	}
	
	String getPath(String type, String extension) {
		name = imp.getTitle();
		SaveDialog sd = new SaveDialog("Save as "+type, name, extension);
		name = sd.getFileName();
		if (name==null)
			return null;
		directory = sd.getDirectory();
		imp.startTiming();
		String path = directory+name;
		return path;
	}
	
	/** Save the image or stack in TIFF format using a save file
		dialog. Returns false if the user selects cancel. */
	public boolean saveAsTiff() {
		String path = getPath("TIFF", ".tif");
		if (path==null)
			return false;
		if (imp.getStackSize()==1)
			return saveAsTiff(path);
		else
			return saveAsTiffStack(path);
	}
	
	/** Save the image in TIFF format using the specified path. */
	public boolean saveAsTiff(String path) {
		fi.nImages = 1;
		Object info = imp.getProperty("Info");
		if (info!=null && (info instanceof String))
			fi.info = (String)info;
		fi.description = getDescriptionString();
		try {
			TiffEncoder file = new TiffEncoder(fi);
			DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
			file.write(out);
			out.close();
		}
		catch (IOException e) {
			showErrorMessage(e);
			return false;
		}
		updateImp(fi, fi.TIFF);
		return true;
	}

	/** Save the stack as a multi-image TIFF using the specified path. */
	public boolean saveAsTiffStack(String path) {
		if (fi.nImages==1)
			{IJ.error("This is not a stack"); return false;}
		if (fi.pixels==null && imp.getStack().isVirtual())
			{IJ.error("Save As Tiff", "Virtual stacks not supported."); return false;}
		Object info = imp.getProperty("Info");
		if (info!=null && (info instanceof String))
			fi.info = (String)info;
		fi.description = getDescriptionString();
		fi.sliceLabels = imp.getStack().getSliceLabels();
		try {
			TiffEncoder file = new TiffEncoder(fi);
			DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
			file.write(out);
			out.close();
		}
		catch (IOException e) {
			showErrorMessage(e);
			return false;
		}
		updateImp(fi, fi.TIFF);
		return true;
	}

	/** Uses a save file dialog to save the image or stack as a TIFF
		in a ZIP archive. Returns false if the user selects cancel. */
	public boolean saveAsZip() {
		String path = getPath("TIFF/ZIP", ".zip");
		if (path==null)
			return false;
		else
			return saveAsZip(path);
	}
	
	/** Save the image or stack in TIFF/ZIP format using the specified path. */
	public boolean saveAsZip(String path) {
		//fi.nImages = 1;
		if (!path.endsWith(".zip"))
			path = path+".zip";
		if (name==null)
			name = imp.getTitle();
		if (name.endsWith(".zip"))
			name = name.substring(0,name.length()-4);
		if (!name.endsWith(".tif"))
			name = name+".tif";
		fi.description = getDescriptionString();
		Object info = imp.getProperty("Info");
		if (info!=null && (info instanceof String))
			fi.info = (String)info;
		fi.sliceLabels = imp.getStack().getSliceLabels();
		try {
			ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path));
			DataOutputStream out = new DataOutputStream(new BufferedOutputStream(zos));
        	zos.putNextEntry(new ZipEntry(name));
			TiffEncoder te = new TiffEncoder(fi);
			te.write(out);
			out.close();
		}
		catch (IOException e) {
			showErrorMessage(e);
			return false;
		}
		updateImp(fi, fi.TIFF);
		return true;
	}

	public static boolean okForGif(ImagePlus imp) {
		int type = imp.getType();
		if (type==ImagePlus.COLOR_RGB || type==ImagePlus.GRAY16 || type==ImagePlus.GRAY32) {
			IJ.error("To save as Gif, the image must be \"8-bit\" or \"8-bit Color\".");
			return false;
		} else
			return true;
		
	}

	/** Save the image in GIF format using a save file
		dialog. Returns false if the user selects cancel
		or the image is not 8-bits. */
	public boolean saveAsGif() {
		if (!okForGif(imp))
			return false;
		String path = getPath("GIF", ".gif");
		if (path==null)
			return false;
		else
			return saveAsGif(path);
	}
	
	/** Save the image in Gif format using the specified path. Returns
		false if the image is not 8-bits or there is an I/O error. */
	public boolean saveAsGif(String path) {
		if (!okForGif(imp))
			return false;
		try {
			byte[] pixels = (byte[])imp.getProcessor().getPixels();
			GifEncoder encoder = new GifEncoder(fi.width, fi.height, pixels, fi.reds, fi.greens, fi.blues);
			OutputStream output = new BufferedOutputStream(new FileOutputStream(path));
			encoder.write(output);
			output.close();
		}
		catch (IOException e) {
			showErrorMessage(e);
			return false;
		}
		updateImp(fi, fi.GIF_OR_JPG);
		return true;
	}

	/** Always returns true. */
	public static boolean okForJpeg(ImagePlus imp) {
		return true;
	}

	/** Save the image in JPEG format using a save file
		dialog. Returns false if the user selects cancel.
		@see ij.plugin.JpegWriter#setQuality
		@see ij.plugin.JpegWriter#getQuality
	*/
	public boolean saveAsJpeg() {
		String path = getPath("JPEG", ".jpg");
		if (path==null)
			return false;
		else
			return saveAsJpeg(path);
	}

	/** Save the image in JPEG format using the specified path.
		@see ij.plugin.JpegWriter#setQuality
		@see ij.plugin.JpegWriter#getQuality
	*/
	public boolean saveAsJpeg(String path) {
		Object jpegWriter = null;
		ImagePlus tempImage = WindowManager.getTempCurrentImage();
		WindowManager.setTempCurrentImage(imp);
		if (IJ.isJava2())
			IJ.runPlugIn("ij.plugin.JpegWriter", path);
		else
			IJ.runPlugIn("Jpeg_Writer", path);		
		WindowManager.setTempCurrentImage(tempImage);
		if (!(imp.getType()==ImagePlus.GRAY16 || imp.getType()==ImagePlus.GRAY32))
			updateImp(fi, fi.GIF_OR_JPG);
		return true;
	}

	/** Save the image in BMP format using a save file dialog. 
		Returns false if the user selects cancel. */
	public boolean saveAsBmp() {
		String path = getPath("BMP", ".bmp");
		if (path==null)
			return false;
		else
			return saveAsBmp(path);
	}

	/** Save the image in BMP format using the specified path. */
	public boolean saveAsBmp(String path) {
		ImagePlus tempImage = WindowManager.getTempCurrentImage();
		WindowManager.setTempCurrentImage(imp);
		IJ.runPlugIn("ij.plugin.BMP_Writer", path);
		WindowManager.setTempCurrentImage(tempImage);
		return true;
	}

	/** Saves grayscale images in PGM (portable graymap) format 
		and RGB images in PPM (portable pixmap) format,
		using a save file dialog.
		Returns false if the user selects cancel.
	*/
	public boolean saveAsPgm() {
		String extension = imp.getBitDepth()==24?".pnm":".pgm";
		String path = getPath("PGM", extension);
		if (path==null)
			return false;
		else
			return saveAsPgm(path);
	}

	/** Saves grayscale images in PGM (portable graymap) format 
		and RGB images in PPM (portable pixmap) format,
		using the specified path. */
	public boolean saveAsPgm(String path) {
		ImagePlus tempImage = WindowManager.getTempCurrentImage();
		WindowManager.setTempCurrentImage(imp);
		IJ.runPlugIn("ij.plugin.PNM_Writer", path);
		WindowManager.setTempCurrentImage(tempImage);
		return true;
	}

	/** Save the image in PNG format using a save file dialog. 
		Returns false if the user selects cancel. Requires
		java 1.4 or later. */
	public boolean saveAsPng() {
		if (!IJ.isJava14()) {
			IJ.error("Save As PNG", "Java 1.4 or later required");
			return false;
		}
		String path = getPath("PNG", ".png");
		if (path==null)
			return false;
		else
			return saveAsPng(path);
	}

	/** Save the image in PNG format using the specified path. 
		Requires Java 1,4 or later. */
	public boolean saveAsPng(String path) {
		ImagePlus tempImage = WindowManager.getTempCurrentImage();
		WindowManager.setTempCurrentImage(imp);
		IJ.runPlugIn("PNG_Writer", path);
		WindowManager.setTempCurrentImage(tempImage);
		return true;
	}

	/** Save the image or stack as raw data using a save file
		dialog. Returns false if the user selects cancel. */
	public boolean saveAsRaw() {
		String path = getPath("Raw", ".raw");
		if (path==null)
			return false;
		if (imp.getStackSize()==1)
			return saveAsRaw(path);
		else
			return saveAsRawStack(path);
	}
	
	/** Save the image as raw data using the specified path. */
	/** Save the image as raw data using the specified path. */
	public boolean saveAsRaw(String path) {
		fi.nImages = 1;
		boolean signed16Bit = false;
		short[] pixels = null;
		int n = 0;
		try {
			signed16Bit = imp.getCalibration().isSigned16Bit();
			if (signed16Bit) {
				pixels = (short[])imp.getProcessor().getPixels();
				n = imp.getWidth()*imp.getHeight();
				for (int i=0; i<n; i++)
					pixels[i] = (short)(pixels[i]-32768);
			}
			ImageWriter file = new ImageWriter(fi);
			OutputStream out = new BufferedOutputStream(new FileOutputStream(path));
			file.write(out);
			out.close();
		}
		catch (IOException e) {
			showErrorMessage(e);
			return false;
		}
		if (signed16Bit) {
			for (int i=0; i<n; i++)
			pixels[i] = (short)(pixels[i]+32768);
		}
		updateImp(fi, fi.RAW);
		return true;
	}

	/** Save the stack as raw data using the specified path. */
	public boolean saveAsRawStack(String path) {
		if (fi.nImages==1)
			{IJ.write("This is not a stack"); return false;}
		boolean signed16Bit = false;
		Object[] stack = null;
		int n = 0;
		try {
			signed16Bit = imp.getCalibration().isSigned16Bit();
			if (signed16Bit) {
				stack = (Object[])fi.pixels;
				n = imp.getWidth()*imp.getHeight();
				for (int slice=0; slice<fi.nImages; slice++) {
					short[] pixels = (short[])stack[slice];
					for (int i=0; i<n; i++)
						pixels[i] = (short)(pixels[i]-32768);
				}
			}
			ImageWriter file = new ImageWriter(fi);
			OutputStream out = new BufferedOutputStream(new FileOutputStream(path));
			file.write(out);
			out.close();
		}
		catch (IOException e) {
			showErrorMessage(e);
			return false;
		}
		if (signed16Bit) {
			for (int slice=0; slice<fi.nImages; slice++) {
				short[] pixels = (short[])stack[slice];
				for (int i=0; i<n; i++)
					pixels[i] = (short)(pixels[i]+32768);
			}
		}
		updateImp(fi, fi.RAW);
		return true;
	}

	/** Save the image as tab-delimited text using a save file
		dialog. Returns false if the user selects cancel. */
	public boolean saveAsText() {
		String path = getPath("Text", ".txt");
		if (path==null)
			return false;
		return saveAsText(path);
	}
	
	/** Save the image as tab-delimited text using the specified path. */
	public boolean saveAsText(String path) {
		try {
			Calibration cal = imp.getCalibration();
			int precision = Analyzer.getPrecision();
			TextEncoder file = new TextEncoder(imp.getProcessor(), cal, precision);
			DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
			file.write(out);
			out.close();
		}
		catch (IOException e) {
			showErrorMessage(e);
			return false;
		}
		return true;
	}

	/** Save the current LUT using a save file
		dialog. Returns false if the user selects cancel. */
	public boolean saveAsLut() {
		if (imp.getType()==ImagePlus.COLOR_RGB) {
			IJ.error("RGB Images do not have a LUT.");
			return false;
		}
		String path = getPath("LUT", ".lut");
		if (path==null)
			return false;
		return saveAsLut(path);
	}
	
	/** Save the current LUT using the specified path. */
	public boolean saveAsLut(String path) {
		LookUpTable lut = imp.createLut();
		int mapSize = lut.getMapSize();
		if (mapSize==0) {
			IJ.error("RGB Images do not have a LUT.");
			return false;
		}
		if (mapSize<256) {
			IJ.error("Cannot save LUTs with less than 256 entries.");
			return false;
		}
		byte[] reds = lut.getReds(); 
		byte[] greens = lut.getGreens();
		byte[] blues = lut.getBlues();
		byte[] pixels = new byte[768];
		for (int i=0; i<256; i++) {
			pixels[i] = reds[i];
			pixels[i+256] = greens[i];
			pixels[i+512] = blues[i];
		}
		FileInfo fi = new FileInfo();
		fi.width = 768;
		fi.height = 1;
		fi.pixels = pixels;

		try {
			ImageWriter file = new ImageWriter(fi);
			OutputStream out = new FileOutputStream(path);
			file.write(out);
			out.close();
		}
		catch (IOException e) {
			showErrorMessage(e);
			return false;
		}
		return true;
	}

	private void updateImp(FileInfo fi, int fileFormat) {
		imp.changes = false;
		if (name!=null) {
			fi.fileFormat = fileFormat;
			fi.fileName = name;
			fi.directory = directory;
			if (fileFormat==fi.TIFF)
				fi.offset = TiffEncoder.IMAGE_START;
			fi.description = null;
			imp.setTitle(name);
			imp.setFileInfo(fi);
		}
	}

	void showErrorMessage(IOException e) {
		String msg = e.getMessage();
		if (msg.length()>100)
			msg = msg.substring(0, 100);
		IJ.error("FileSaver", "An error occured writing the file.\n \n" + msg);
	}

	/** Returns a string containing information about the specified  image. */
	public String getDescriptionString() {
		StringBuffer sb = new StringBuffer(100);
		sb.append("ImageJ="+ImageJ.VERSION+"\n");
		if (fi.nImages>1)
			sb.append("images="+fi.nImages+"\n");
		int channels = imp.getNChannels();
		if (channels>1)
			sb.append("channels="+channels+"\n");
		int slices = imp.getNSlices();
		if (slices>1)
			sb.append("slices="+slices+"\n");
		int frames = imp.getNFrames();
		if (frames>1)
			sb.append("frames="+frames+"\n");
		if (fi.unit!=null)
			sb.append("unit="+fi.unit+"\n");
		if (fi.valueUnit!=null) {
			sb.append("cf="+fi.calibrationFunction+"\n");
			if (fi.coefficients!=null) {
				for (int i=0; i<fi.coefficients.length; i++)
					sb.append("c"+i+"="+fi.coefficients[i]+"\n");
			}
			sb.append("vunit="+fi.valueUnit+"\n");
			Calibration cal = imp.getCalibration();
			if (cal.zeroClip()) sb.append("zeroclip=true\n");
		}
		
		// get stack z-spacing and fps
		if (fi.nImages>1) {
			if (fi.pixelDepth!=0.0 && fi.pixelDepth!=1.0)
				sb.append("spacing="+fi.pixelDepth+"\n");
			if (fi.frameInterval!=0.0) {
				double fps = 1.0/fi.frameInterval;
				if ((int)fps==fps)
					sb.append("fps="+(int)fps+"\n");
				else
					sb.append("fps="+fps+"\n");
			}
		}
		
		// get min and max display values
		ImageProcessor ip = imp.getProcessor();
		double min = ip.getMin();
		double max = ip.getMax();
		int type = imp.getType();
		boolean enhancedLut = (type==ImagePlus.GRAY8 || type==ImagePlus.COLOR_256) && (min!=0.0 || max !=255.0);
		if (enhancedLut || type==ImagePlus.GRAY16 || type==ImagePlus.GRAY32) {
			sb.append("min="+min+"\n");
			sb.append("max="+max+"\n");
		}
		
		// get non-zero origins
		Calibration cal = imp.getCalibration();
		if (cal.xOrigin!=0.0)
			sb.append("xorigin="+cal.xOrigin+"\n");
		if (cal.yOrigin!=0.0)
			sb.append("yorigin="+cal.yOrigin+"\n");
		if (cal.zOrigin!=0.0)
			sb.append("zorigin="+cal.zOrigin+"\n");
		if (cal.info!=null && cal.info.length()<=64 && cal.info.indexOf('=')==-1 && cal.info.indexOf('\n')==-1)
			sb.append("info="+cal.info+"\n");			
		sb.append((char)0);
		return new String(sb);
	}

}
