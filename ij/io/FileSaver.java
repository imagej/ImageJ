package ij.io;
import java.awt.*;
import java.io.*;
import java.util.zip.*;
import ij.*;
import ij.process.*;
import ij.measure.Calibration;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.Recorder;
import ij.plugin.JpegWriter;
import ij.plugin.Orthogonal_Views;
import ij.gui.*;
import ij.measure.Measurements;
import ij.util.Tools;
import javax.imageio.*;

/** Saves images in tiff, gif, jpeg, raw, zip and text format. */
public class FileSaver {

	public static final int DEFAULT_JPEG_QUALITY = 85;
	private static int jpegQuality;
	private static int bsize = 32768; // 32K default buffer size
	
    static {setJpegQuality(ij.Prefs.getInt(ij.Prefs.JPEG, DEFAULT_JPEG_QUALITY));}

	private static String defaultDirectory = null;
	private ImagePlus imp;
	private FileInfo fi;
	private String name;
	private String directory;
	private boolean saveName;

	/** Constructs a FileSaver from an ImagePlus. */
	public FileSaver(ImagePlus imp) {
		this.imp = imp;
		fi = imp.getFileInfo();
	}

	/** Resaves the image. Calls saveAsTiff() if this is a new image, not a TIFF,
		or if the image was loaded using a URL. Returns false if saveAsTiff() is
		called and the user selects cancel in the file save dialog box. */
	public boolean save() {
		FileInfo ofi = null;
		if (imp!=null) ofi = imp.getOriginalFileInfo();
		boolean validName = ofi!=null && imp.getTitle().equals(ofi.fileName);
		if (validName && ofi.fileFormat==FileInfo.TIFF && ofi.directory!=null && !ofi.directory.equals("") && (ofi.url==null||ofi.url.equals(""))) {
            name = imp.getTitle();
            directory = ofi.directory;
			String path = directory+name;
			File f = new File(path);
			if (f==null || !f.exists())
				return saveAsTiff();
			if (!IJ.isMacro()) {
				GenericDialog gd = new GenericDialog("Save as TIFF");
				gd.addMessage("\""+ofi.fileName+"\" already exists.\nDo you want to replace it?");
				gd.setOKLabel("Replace");
				gd.showDialog();
				if (gd.wasCanceled())
					return false;
			}
			IJ.showStatus("Saving "+path);
			if (imp.getStackSize()>1) {
				IJ.saveAs(imp, "tif", path);
				return true;
			} else
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
	
	/** Saves the image or stack in TIFF format using a save file
		dialog. Returns false if the user selects cancel. Equivalent to 
		IJ.saveAsTiff(imp,""), which is more convenient. */
	public boolean saveAsTiff() {
		String path = getPath("TIFF", ".tif");
		if (path==null)
			return false;
		if (fi.nImages>1)
			return saveAsTiffStack(path);
		else
			return saveAsTiff(path);
	}
	
	/** Saves the image in TIFF format using the specified path. Equivalent to
		 IJ.saveAsTiff(imp,path), which is more convenient. */
	public boolean saveAsTiff(String path) {
		if (fi.nImages>1)
			return saveAsTiffStack(path);
		if (imp.getProperty("FHT")!=null && path.contains("FFT of "))
			setupFFTSave();
		fi.info = imp.getInfoProperty();
		String label = imp.hasImageStack()?imp.getStack().getSliceLabel(1):null;
		if (label!=null) {
			fi.sliceLabels = new String[1];
			fi.sliceLabels[0] = label;
		}
		fi.description = getDescriptionString();
		if (imp.getProperty(Plot.PROPERTY_KEY) != null) {
			Plot plot = (Plot)(imp.getProperty(Plot.PROPERTY_KEY));
			fi.plot = plot.toByteArray();
		}
		fi.roi = RoiEncoder.saveAsByteArray(imp.getRoi());
		fi.overlay = getOverlay(imp);
		fi.properties = imp.getPropertiesAsArray();
		DataOutputStream out = null;
		try {
			TiffEncoder file = new TiffEncoder(fi);
			out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path),bsize));
			file.write(out);
			out.close();
		} catch (IOException e) {
			showErrorMessage("saveAsTiff", path, e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		updateImp(fi, FileInfo.TIFF);
		return true;
	}
	
	private void setupFFTSave() {
		Object obj = imp.getProperty("FHT");
		if (obj==null) return;
		FHT fht = (obj instanceof FHT)?(FHT)obj:null;
		if (fht==null) return;
		if (fht.originalColorModel!=null && fht.originalBitDepth!=24)
			fht.setColorModel(fht.originalColorModel);
		ImagePlus imp2 = new ImagePlus(imp.getTitle(), fht);
		imp2.setProperty("Info", imp.getProperty("Info"));
		imp2.setProperties(imp.getPropertiesAsArray());
		imp2.setCalibration(imp.getCalibration());
		imp = imp2;
		fi = imp.getFileInfo();
	}
	
	public static byte[][] getOverlay(ImagePlus imp) {
		if (imp.getHideOverlay())
			return null;
		Overlay overlay = imp.getOverlay();
		if (overlay==null) {
			ImageCanvas ic = imp.getCanvas();
			if (ic==null) return null;
			overlay = ic.getShowAllList(); // ROI Manager "Show All" list
			if (overlay==null) return null;
		}
		int n = overlay.size();
		if (n==0)
			return null;
		if (Orthogonal_Views.isOrthoViewsImage(imp))
			return null;
		byte[][] array = new byte[n][];
		for (int i=0; i<overlay.size(); i++) {
			Roi roi = overlay.get(i);
			if (i==0)
				roi.setPrototypeOverlay(overlay);
			array[i] = RoiEncoder.saveAsByteArray(roi);
		}
		return array;
	}

	/** Saves the stack as a multi-image TIFF using the specified path.
		 Equivalent to IJ.saveAsTiff(imp,path), which is more convenient. */
	public boolean saveAsTiffStack(String path) {
		if (fi.nImages==1) {
			error("This is not a stack");
			return false;
		}
		boolean virtualStack = imp.getStack().isVirtual();
		if (virtualStack)
			fi.virtualStack = (VirtualStack)imp.getStack();
		fi.info = imp.getInfoProperty();
		fi.description = getDescriptionString();
		if (virtualStack) {
			FileInfo ofi = imp.getOriginalFileInfo();
			if (path!=null && ofi!=null && path.equals(ofi.directory+ofi.fileName)) {
				error("TIFF virtual stacks cannot be saved in place.");
				return false;
			}
			String[] labels = null;
			ImageStack vs = imp.getStack();
			for (int i=1; i<=vs.getSize(); i++) {
				String label = vs.getSliceLabel(i);
				if (i==1 && label==null)
					break;
				if (labels==null)
					labels = new String[vs.getSize()];
				labels[i-1] = label;
			}
			fi.sliceLabels = labels;
		} else
			fi.sliceLabels = imp.getStack().getSliceLabels();
		fi.roi = RoiEncoder.saveAsByteArray(imp.getRoi());
		fi.overlay = getOverlay(imp);
		fi.properties = imp.getPropertiesAsArray();
		if (imp.isComposite()) saveDisplayRangesAndLuts(imp, fi);
		DataOutputStream out = null;
		try {
			TiffEncoder file = new TiffEncoder(fi);
			out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path),bsize));
			file.write(out);
			out.close();
		} catch (IOException e) {
			showErrorMessage("saveAsTiffStack", path, e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		updateImp(fi, FileInfo.TIFF);
		return true;
	}
	
	/** Converts this image to a TIFF encoded array of bytes, 
		which can be decoded using Opener.deserialize(). */
	public byte[] serialize() {
		if (imp.getStack().isVirtual())
			return null;
		fi.info = imp.getInfoProperty();
		saveName = true;
		fi.description = getDescriptionString();
		saveName = false;
		fi.sliceLabels = imp.getStack().getSliceLabels();
		if (imp.getProperty(Plot.PROPERTY_KEY) != null) {
			Plot plot = (Plot)(imp.getProperty(Plot.PROPERTY_KEY));
			fi.plot = plot.toByteArray();
		}
		fi.roi = RoiEncoder.saveAsByteArray(imp.getRoi());
		fi.overlay = getOverlay(imp);
		if (imp.isComposite()) saveDisplayRangesAndLuts(imp, fi);
		ByteArrayOutputStream out = null;
		try {
			TiffEncoder encoder = new TiffEncoder(fi);
			out = new ByteArrayOutputStream();
			encoder.write(out);
			out.close();
		} catch (IOException e) {
			return null;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		return out.toByteArray();
	}

	public void saveDisplayRangesAndLuts(ImagePlus imp, FileInfo fi) {
		CompositeImage ci = (CompositeImage)imp;
		int channels = imp.getNChannels();
		fi.displayRanges = new double[channels*2];
		for (int i=1; i<=channels; i++) {
			LUT lut = ci.getChannelLut(i);
			fi.displayRanges[(i-1)*2] = lut.min;
			fi.displayRanges[(i-1)*2+1] = lut.max;
		}
		if (ci.hasCustomLuts()) {
			fi.channelLuts = new byte[channels][];
			for (int i=0; i<channels; i++) {
				LUT lut = ci.getChannelLut(i+1);
				byte[] bytes = lut.getBytes();
				if (bytes==null)
					{fi.channelLuts=null; break;}
				fi.channelLuts[i] = bytes;
			}
		}	
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
		if (imp.getProperty("FHT")!=null && path.contains("FFT of "))
			setupFFTSave();
		if (!path.endsWith(".zip"))
			path = path+".zip";
		if (name==null)
			name = imp.getTitle();
		if (name.endsWith(".zip"))
			name = name.substring(0,name.length()-4);
		if (!name.endsWith(".tif"))
			name = name+".tif";
		fi.description = getDescriptionString();
		fi.info = imp.getInfoProperty();
		fi.properties = imp.getPropertiesAsArray();
		if (imp.getProperty(Plot.PROPERTY_KEY) != null) {
			Plot plot = (Plot)(imp.getProperty(Plot.PROPERTY_KEY));
			fi.plot = plot.toByteArray();
		}
		fi.roi = RoiEncoder.saveAsByteArray(imp.getRoi());
		fi.overlay = getOverlay(imp);
		fi.sliceLabels = imp.getStack().getSliceLabels();
		if (imp.isComposite()) saveDisplayRangesAndLuts(imp, fi);
		if (fi.nImages>1 && imp.getStack().isVirtual())
			fi.virtualStack = (VirtualStack)imp.getStack();
		DataOutputStream out = null;
		try {
			ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path));
			out = new DataOutputStream(new BufferedOutputStream(zos,bsize));
        	zos.putNextEntry(new ZipEntry(name));
			TiffEncoder te = new TiffEncoder(fi);
			te.write(out);
			out.close();
		}
		catch (IOException e) {
			showErrorMessage("saveAsZip", path, e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		updateImp(fi, FileInfo.TIFF);
		return true;
	}

	public static boolean okForGif(ImagePlus imp) {
		if (imp.getType()==ImagePlus.COLOR_RGB)
			return false;
		else
			return true;
	}

	/** Save the image in GIF format using a save file
		dialog. Returns false if the user selects cancel
		or the image is not 8-bits. */
	public boolean saveAsGif() {
		String path = getPath("GIF", ".gif");
		if (path==null)
			return false;
		else
			return saveAsGif(path);
	}
	
	/** Save the image in Gif format using the specified path. Returns
		false if the image is not 8-bits or there is an I/O error. */
	public boolean saveAsGif(String path) {
		IJ.runPlugIn(imp, "ij.plugin.GifWriter", path);
		updateImp(fi, FileInfo.GIF_OR_JPG);
		return true;
	}

	/** Always returns true. */
	public static boolean okForJpeg(ImagePlus imp) {
		return true;
	}

	/** Save the image in JPEG format using a save file
		dialog. Returns false if the user selects cancel.
		@see #setJpegQuality
		@see #getJpegQuality
	*/
	public boolean saveAsJpeg() {
		String type = "JPEG ("+getJpegQuality()+")";
		String path = getPath(type, ".jpg");
		if (path==null)
			return false;
		else
			return saveAsJpeg(path);
	}

	/** Save the image in JPEG format using the specified path.
		@see #setJpegQuality
		@see #getJpegQuality
	*/
	public boolean saveAsJpeg(String path) {
		String err = JpegWriter.save(imp, path, jpegQuality);
		if (err==null && !(imp.getType()==ImagePlus.GRAY16 || imp.getType()==ImagePlus.GRAY32))
			updateImp(fi, FileInfo.GIF_OR_JPG);
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
		IJ.runPlugIn(imp, "ij.plugin.BMP_Writer", path);
		updateImp(fi, FileInfo.BMP);
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
		IJ.runPlugIn(imp, "ij.plugin.PNM_Writer", path);
		updateImp(fi, FileInfo.PGM);
		return true;
	}

	/** Save the image in PNG format using a save file dialog. 
		Returns false if the user selects cancel. */
	public boolean saveAsPng() {
		String path = getPath("PNG", ".png");
		if (path==null)
			return false;
		else
			return saveAsPng(path);
	}

	/** Save the image in PNG format using the specified path. */
	public boolean saveAsPng(String path) {
		IJ.runPlugIn(imp, "ij.plugin.PNG_Writer", path);
		updateImp(fi, FileInfo.IMAGEIO);
		return true;
	}

	/** Save the image in FITS format using a save file dialog. 
		Returns false if the user selects cancel. */
	public boolean saveAsFits() {
		if (!okForFits(imp)) return false;
		String path = getPath("FITS", ".fits");
		if (path==null)
			return false;
		else
			return saveAsFits(path);
	}

	/** Save the image in FITS format using the specified path. */
	public boolean saveAsFits(String path) {
		if (!okForFits(imp)) return false;
		IJ.runPlugIn(imp, "ij.plugin.FITS_Writer", path);
		updateImp(fi, FileInfo.FITS);
		return true;
	}

	public static boolean okForFits(ImagePlus imp) {
		if (imp.getBitDepth()==24) {
			IJ.error("FITS Writer", "Grayscale image required");
			return false;
		} else
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
	public boolean saveAsRaw(String path) {
		fi.nImages = 1;
		fi.intelByteOrder = Prefs.intelByteOrder;
		boolean signed16Bit = false;
		short[] pixels = null;
		int n = 0;
		OutputStream out = null;
		try {
			signed16Bit = imp.getCalibration().isSigned16Bit();
			if (signed16Bit) {
				pixels = (short[])imp.getProcessor().getPixels();
				n = imp.getWidth()*imp.getHeight();
				for (int i=0; i<n; i++)
					pixels[i] = (short)(pixels[i]-32768);
			}
			ImageWriter file = new ImageWriter(fi);
			out = new BufferedOutputStream(new FileOutputStream(path),bsize);
			file.write(out);
			out.close();
		}
		catch (IOException e) {
			showErrorMessage("saveAsRaw", path, e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
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
			{IJ.log("This is not a stack"); return false;}
		fi.intelByteOrder = Prefs.intelByteOrder;
		boolean signed16Bit = false;
		Object[] stack = null;
		int n = 0;
		boolean virtualStack = imp.getStackSize()>1 && imp.getStack().isVirtual();
		if (virtualStack) {
			fi.virtualStack = (VirtualStack)imp.getStack();
			if (imp.getProperty("AnalyzeFormat")!=null) fi.fileName="FlipTheseImages";
		}
		OutputStream out = null;
		try {
			signed16Bit = imp.getCalibration().isSigned16Bit();
			if (signed16Bit && !virtualStack) {
				stack = (Object[])fi.pixels;
				n = imp.getWidth()*imp.getHeight();
				for (int slice=0; slice<fi.nImages; slice++) {
					short[] pixels = (short[])stack[slice];
					for (int i=0; i<n; i++)
						pixels[i] = (short)(pixels[i]-32768);
				}
			}
			ImageWriter file = new ImageWriter(fi);
			out = new BufferedOutputStream(new FileOutputStream(path),bsize);
			file.write(out);
			out.close();
		} catch (IOException e) {
			showErrorMessage("saveAsRawStack", path, e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
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
		DataOutputStream out = null;
		try {
			Calibration cal = imp.getCalibration();
			int precision = Analyzer.getPrecision();
			int measurements = Analyzer.getMeasurements();
			boolean scientificNotation = (measurements&Measurements.SCIENTIFIC_NOTATION)!=0;
			if (scientificNotation)
				precision = -precision;
			TextEncoder file = new TextEncoder(imp.getProcessor(), cal, precision);
			out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
			file.write(out);
			out.close();
		} catch (IOException e) {
			showErrorMessage("saveAsText", path, e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		return true;
	}

	/** Save the current LUT using a save file
		dialog. Returns false if the user selects cancel. */
	public boolean saveAsLut() {
		if (imp.getType()==ImagePlus.COLOR_RGB) {
			error("RGB Images do not have a LUT.");
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
			error("RGB Images do not have a LUT.");
			return false;
		}
		if (mapSize<256) {
			error("Cannot save LUTs with less than 256 entries.");
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

		OutputStream out = null;
		try {
			ImageWriter file = new ImageWriter(fi);
			out = new FileOutputStream(path);
			file.write(out);
			out.close();
		}
		catch (IOException e) {
			showErrorMessage("saveAsLut", path, e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		return true;
	}

	public void updateImagePlus(String path, int fileFormat) {
		if (imp==null || fi==null)
			return;
		if (name==null && path!=null) {
			File f = new File(path);
			directory = f.getParent() + File.separator;
			name = f.getName();
		}
		updateImp(fi, fileFormat);
	}
	
	private void updateImp(FileInfo fi, int fileFormat) {
		imp.changes = false;
		if (name!=null) {
			fi.fileFormat = fileFormat;
			FileInfo ofi = imp.getOriginalFileInfo();
			if (ofi!=null) {
				if (ofi.openNextName==null) {
					fi.openNextName = ofi.fileName;
					fi.openNextDir = ofi.directory;
				} else {
					fi.openNextName = ofi.openNextName;
					fi.openNextDir = ofi.openNextDir ;
				}
			}
			fi.fileName = name;
			fi.directory = directory;
			//if (fileFormat==fi.TIFF)
			//	fi.offset = TiffEncoder.IMAGE_START;
			fi.description = null;
			imp.setTitle(name);
			fi.imageSaved = true;
			imp.setFileInfo(fi);
		}
	}

	private void showErrorMessage(String title, String path, IOException e) {
		String msg = e.getMessage();
		if (msg.length()>100)
			msg = msg.substring(0, 100);
		msg = "File saving error (IOException):\n   \"" + msg + "\"";
		IJ.error("FileSaver."+title, msg+" \n   "+path);
		IJ.showProgress(1.0);
	}
	
	private void error(String msg) {
		IJ.error("FileSaver", msg);
	}

	/** Returns a string containing information about the specified  image. */
	public String getDescriptionString() {
		Calibration cal = imp.getCalibration();
		StringBuffer sb = new StringBuffer(100);
		sb.append("ImageJ="+ImageJ.VERSION+"\n");
		if (fi.nImages>1 && fi.fileType!=FileInfo.RGB48)
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
		if (imp.isHyperStack()) sb.append("hyperstack=true\n");
		if (imp.isComposite()) {
			String mode = ((CompositeImage)imp).getModeAsString();
			sb.append("mode="+mode+"\n");
		}
		if (fi.unit!=null)
			appendEscapedLine(sb, "unit="+fi.unit);
		int bitDepth = imp.getBitDepth();
		if (fi.valueUnit!=null && (fi.calibrationFunction!=Calibration.CUSTOM||bitDepth==32)) {
			if (bitDepth!=32) {
				sb.append("cf="+fi.calibrationFunction+"\n");
				if (fi.coefficients!=null) {
					for (int i=0; i<fi.coefficients.length; i++)
						sb.append("c"+i+"="+fi.coefficients[i]+"\n");
				}
			}
			appendEscapedLine(sb, "vunit="+fi.valueUnit);
			if (cal.zeroClip() && bitDepth!=32)
				sb.append("zeroclip=true\n");
		}
		// get stack z-spacing, more units and fps
		if (cal.frameInterval!=0.0) {
			if ((int)cal.frameInterval==cal.frameInterval)
				sb.append("finterval="+(int)cal.frameInterval+"\n");
			else
				sb.append("finterval="+cal.frameInterval+"\n");
		}
		if (!cal.getTimeUnit().equals("sec"))
			appendEscapedLine(sb, "tunit="+cal.getTimeUnit());
		if (!cal.getYUnit().equals(cal.getUnit()))
			appendEscapedLine(sb, "yunit="+cal.getYUnit());
		if (!cal.getZUnit().equals(cal.getUnit()))
			appendEscapedLine(sb, "zunit="+cal.getZUnit());
		if (fi.nImages>1) {
			if (fi.pixelDepth!=1.0)
				sb.append("spacing="+fi.pixelDepth+"\n");
			if (cal.fps!=0.0) {
				if ((int)cal.fps==cal.fps)
					sb.append("fps="+(int)cal.fps+"\n");
				else
					sb.append("fps="+cal.fps+"\n");
			}
			sb.append("loop="+(cal.loop?"true":"false")+"\n");
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
		if (cal.xOrigin!=0.0)
			sb.append("xorigin="+cal.xOrigin+"\n");
		if (cal.yOrigin!=0.0)
			sb.append("yorigin="+cal.yOrigin+"\n");
		if (cal.zOrigin!=0.0)
			sb.append("zorigin="+cal.zOrigin+"\n");
		if (cal.info!=null && cal.info.length()<=64 && cal.info.indexOf('=')==-1 && cal.info.indexOf('\n')==-1)
			appendEscapedLine(sb, "info="+cal.info);
			
		// get invertY flag
		if (cal.getInvertY())
			sb.append("inverty=true\n");

		if (saveName)
			appendEscapedLine(sb, "name="+imp.getTitle());
			
		if (imp.getType()==ImagePlus.COLOR_256)
			sb.append("8bitcolor=true\n");

		sb.append((char)0);
		return new String(sb);
	}

	// Append a string to a StringBuffer with escaped special characters as needed for java.util.Properties,
	// and add a linefeed character
	void appendEscapedLine(StringBuffer sb, String str) {
		for (int i=0; i<str.length(); i++) {
			char c = str.charAt(i);
			if (c>=0x20 && c<0x7f && c!='\\')
				sb.append(c);
			else if (c<=0xffff) {   //(supplementary unicode characters >0xffff unsupported)
				sb.append("\\u");
				sb.append(Tools.int2hex(c, 4));
			}
		}
		sb.append('\n');
	}

	/** Specifies the image quality (0-100). 0 is poorest image quality,
		highest compression, and 100 is best image quality, lowest compression. */
    public static void setJpegQuality(int quality) {
        jpegQuality = quality;
    	if (jpegQuality<0) jpegQuality = 0;
    	if (jpegQuality>100) jpegQuality = 100;
    }

    /** Returns the current JPEG quality setting (0-100). */
    public static int getJpegQuality() {
        return jpegQuality;
    }
    
    /** Sets the BufferedOutputStream buffer size in bytes (default is 32K). */
    public static void setBufferSize(int bufferSize) {
        bsize = bufferSize;
        if (bsize<2048) bsize = 2048;
    }

}
