package ij.io;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.frame.*;
import ij.plugin.*;
import ij.text.TextWindow;
import ij.util.Java2;
import ij.measure.ResultsTable;
import ij.macro.Interpreter;
import ij.util.Tools;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.URL;
import java.net.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;
import javax.swing.filechooser.*;
import java.awt.event.KeyEvent;
import javax.imageio.ImageIO;
import java.lang.reflect.Method;

/** Opens tiff (and tiff stacks), dicom, fits, pgm, jpeg, bmp or
	gif images, and look-up tables, using a file open dialog or a path.
	Calls HandleExtraFileTypes plugin if the file type is unrecognised. */
public class Opener {

	public static final int UNKNOWN=0,TIFF=1,DICOM=2,FITS=3,PGM=4,JPEG=5,
		GIF=6,LUT=7,BMP=8,ZIP=9,JAVA_OR_TEXT=10,ROI=11,TEXT=12,PNG=13,
		TIFF_AND_DICOM=14,CUSTOM=15, AVI=16, OJJ=17, TABLE=18, RAW=19; // don't forget to also update 'types'
	public static final String[] types = {"unknown","tif","dcm","fits","pgm",
		"jpg","gif","lut","bmp","zip","java/txt","roi","txt","png","t&d","custom","ojj","table","raw"};
	private static String defaultDirectory = null;
	private static int fileType;
	private boolean error;
	private boolean isRGB48;
	private boolean silentMode;
	private String omDirectory;
	private File[] omFiles;
	private static boolean openUsingPlugins;
	private static boolean bioformats;
	private String url;

	static {
		Hashtable commands = Menus.getCommands();
		bioformats = commands!=null && commands.get("Bio-Formats Importer")!=null;
	}

	public Opener() {
	}

	/**
	 * Displays a file open dialog box and then opens the tiff, dicom, 
	 * fits, pgm, jpeg, bmp, gif, lut, roi, or text file selected by 
	 * the user. Displays an error message if the selected file is not
	 * in a supported format. This is the method that
	 * ImageJ's File/Open command uses to open files.
	 * @see ij.IJ#open()
	 * @see ij.IJ#open(String)
	 * @see ij.IJ#openImage()
	 * @see ij.IJ#openImage(String)
	*/
	public void open() {
		OpenDialog od = new OpenDialog("Open", "");
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name!=null) {
			String path = directory+name;
			error = false;
			open(path);
			if (!error) Menus.addOpenRecentItem(path);
		}
	}

	/**
	 * Opens and displays the specified tiff, dicom, fits, pgm, jpeg, 
	 * bmp, gif, lut, roi, or text file. Displays an error message if 
	 * the file is not in a supported format.
	 * @see ij.IJ#open(String)
	 * @see ij.IJ#openImage(String)
	*/
	public void open(String path) {
		boolean isURL = path.indexOf("://")>0;
		if (isURL && isText(path)) {
			openTextURL(path);
			return;
		}
		if (path.endsWith(".jar") || path.endsWith(".class")) {
				(new PluginInstaller()).install(path);
				return;
		}
		boolean fullPath = path.startsWith("/") || path.startsWith("\\") || path.indexOf(":\\")==1 || path.indexOf(":/")==1 || isURL;
		if (!fullPath) {
			String defaultDir = OpenDialog.getDefaultDirectory();
			if (defaultDir!=null)
				path = defaultDir + path;
			else
				path = (new File(path)).getAbsolutePath();
		}
		if (!silentMode)
			IJ.showStatus("Opening: " + path);
		long start = System.currentTimeMillis();
		ImagePlus imp = null;
		if (path.endsWith(".txt"))
			fileType = JAVA_OR_TEXT;
		else
			imp = openImage(path);
		if (imp==null && isURL)
			return;
		if (imp!=null) {
			WindowManager.checkForDuplicateName = true;
			if (isRGB48)
				openRGB48(imp);
			else
				imp.show(getLoadRate(start,imp));
		} else {
			switch (fileType) {
				case LUT:
					imp = (ImagePlus)IJ.runPlugIn("ij.plugin.LutLoader", path);
					if (imp.getWidth()!=0)
						imp.show();
					break;
				case ROI:
					IJ.runPlugIn("ij.plugin.RoiReader", path);
					break;
				case JAVA_OR_TEXT: case TEXT:
					if (IJ.altKeyDown()) { // open in TextWindow if alt key down
						new TextWindow(path,400,450);
						IJ.setKeyUp(KeyEvent.VK_ALT);
						break;
					}
					File file = new File(path);
					int maxSize = 250000;
					long size = file.length();
					if (size>=28000) {
						String osName = System.getProperty("os.name");
						if (osName.equals("Windows 95") || osName.equals("Windows 98") || osName.equals("Windows Me"))
							maxSize = 60000;
					}
					if (size<maxSize) {
						Editor ed = (Editor)IJ.runPlugIn("ij.plugin.frame.Editor", "");
						if (ed!=null) ed.open(getDir(path), getName(path));
					} else
						new TextWindow(path,400,450);
					break;
				case OJJ:  // ObjectJ project
					IJ.runPlugIn("ObjectJ_", path);
					break;
				case TABLE: 
					openTable(path);
					break;
				case RAW:
					IJ.runPlugIn("ij.plugin.Raw", path);
					break;
				case UNKNOWN:
					String msg =
						"File is not in a supported format, a reader\n"+
						"plugin is not available, or it was not found.";
					if (path!=null) {
						if (path.length()>64)
							path = (new File(path)).getName();
						if (path.length()<=64) {
							if (IJ.redirectingErrorMessages())
								msg += " \n   "+path;
							else
								msg += " \n	 \n"+path;
						}
					}
					if (openUsingPlugins)
						msg += "\n \nNOTE: The \"OpenUsingPlugins\" option is set.";
					IJ.wait(IJ.isMacro()?500:100); // work around for OS X thread deadlock problem
					IJ.error("Opener", msg);
					error = true;
					break;
			}
		}
	}
	
	/** Displays a JFileChooser and then opens the tiff, dicom, 
		fits, pgm, jpeg, bmp, gif, lut, roi, or text files selected by 
		the user. Displays error messages if one or more of the selected 
		files is not in one of the supported formats. This is the method
		that ImageJ's File/Open command uses to open files if
		"Open/Save Using JFileChooser" is checked in EditOptions/Misc. */
	public void openMultiple() {
		Java2.setSystemLookAndFeel();
		// run JFileChooser in a separate thread to avoid possible thread deadlocks
		try {
			EventQueue.invokeAndWait(new Runnable() {
				public void run() {
					JFileChooser fc = new JFileChooser();
					fc.setMultiSelectionEnabled(true);
					File dir = null;
					String sdir = OpenDialog.getDefaultDirectory();
					if (sdir!=null)
						dir = new File(sdir);
					if (dir!=null)
						fc.setCurrentDirectory(dir);
					if (IJ.debugMode) IJ.log("Opener.openMultiple: "+sdir+" "+dir);
					int returnVal = fc.showOpenDialog(IJ.getInstance());
					if (returnVal!=JFileChooser.APPROVE_OPTION)
						return;
					omFiles = fc.getSelectedFiles();
					if (omFiles.length==0) { // getSelectedFiles does not work on some JVMs
						omFiles = new File[1];
						omFiles[0] = fc.getSelectedFile();
					}
					omDirectory = fc.getCurrentDirectory().getPath()+File.separator;
				}
			});
		} catch (Exception e) {}
		if (omDirectory==null) return;
		OpenDialog.setDefaultDirectory(omDirectory);
		for (int i=0; i<omFiles.length; i++) {
			String path = omDirectory + omFiles[i].getName();
			open(path);
			if (i==0 && Recorder.record)
				Recorder.recordPath("open", path);
			if (i==0 && !error)
				Menus.addOpenRecentItem(path);
		}
	}
	
	/**
	 * Opens, but does not display, the specified image file
	 * and returns an ImagePlus object object if successful,
	 * or returns null if the file is not in a supported format
	 * or is not found. Displays a file open dialog if 'path'
	 * is null or an empty string.
	 * @see ij.IJ#openImage(String)
	 * @see ij.IJ#openImage()
	*/
	public ImagePlus openImage(String path) {
		if (path==null || path.equals(""))
			path = getPath();
		if (path==null) return null;
		ImagePlus img = null;
		if (path.indexOf("://")>0)
			img = openURL(path);
		else
			img = openImage(getDir(path), getName(path));
		return img;
	}
	
	/**
	 * Open the nth image of the specified tiff stack.
	 * @see ij.IJ#openImage(String,int)
	*/
	public ImagePlus openImage(String path, int n) {
		if (path==null || path.equals(""))
			path = getPath();
		if (path==null) return null;
		int type = getFileType(path);
		if (type!=TIFF)
			throw new IllegalArgumentException("TIFF file require");
		return openTiff(path, n);
	}

	public static String getLoadRate(double time, ImagePlus imp) {
		time = (System.currentTimeMillis()-time)/1000.0;
		double mb = imp.getWidth()*imp.getHeight()*imp.getStackSize();
		int bits = imp.getBitDepth();
		if (bits==16)
			mb *= 2;
		else if (bits==24 || bits==32)
			mb *=4;
		mb /= 1024*1024;
		double rate = mb/time;
		int digits = rate<100.0?1:0;
		return ""+IJ.d2s(time,2)+" seconds ("+IJ.d2s(mb/time,digits)+" MB/sec)";
	}
	
	private boolean isText(String path) {
		if (path.endsWith(".txt") || path.endsWith(".ijm") || path.endsWith(".java")
		|| path.endsWith(".js") || path.endsWith(".html") || path.endsWith(".htm")
		|| path.endsWith(".bsh") || path.endsWith(".py") || path.endsWith("/"))
			return true;
		int lastSlash = path.lastIndexOf("/");
		if (lastSlash==-1) lastSlash = 0;
		int lastDot = path.lastIndexOf(".");
		if (lastDot==-1 || lastDot<lastSlash || (path.length()-lastDot)>6)
			return true;  // no extension
		else
			return false;
	}
	
	/** Opens the specified file and adds it to the File/Open Recent menu.
		Returns true if the file was opened successfully.  */
	public boolean openAndAddToRecent(String path) {
		open(path);
		if (!error)
			Menus.addOpenRecentItem(path);
		return error;
	}

	/**
	 * Attempts to open the specified file as a tiff, bmp, dicom, fits,
	 * pgm, gif or jpeg image. Returns an ImagePlus object if successful.
	 * Modified by Gregory Jefferis to call HandleExtraFileTypes plugin if 
	 * the file type is unrecognised.
	 * @see ij.IJ#openImage(String)
	*/
	public ImagePlus openImage(String directory, String name) {
		ImagePlus imp;
		FileOpener.setSilentMode(silentMode);
		if (directory.length()>0 && !(directory.endsWith("/")||directory.endsWith("\\")))
			directory += Prefs.separator;
		OpenDialog.setLastDirectory(directory);
		OpenDialog.setLastName(name);
		String path = directory+name;
		fileType = getFileType(path);
		if (IJ.debugMode) IJ.log("openImage: \""+types[fileType]+"\", "+path);
		switch (fileType) {
			case TIFF:
				imp = openTiff(directory, name);
				return imp;
			case DICOM: case TIFF_AND_DICOM:
				imp = (ImagePlus)IJ.runPlugIn("ij.plugin.DICOM", path);
				if (imp.getWidth()!=0) return imp; else return null;
			case FITS:
				imp = (ImagePlus)IJ.runPlugIn("ij.plugin.FITS_Reader", path);
				if (imp.getWidth()!=0) return imp; else return null;
			case PGM:
				imp = (ImagePlus)IJ.runPlugIn("ij.plugin.PGM_Reader", path);
				if (imp.getWidth()!=0) {
					if (imp.getStackSize()==3 && imp.getBitDepth()==16)
						imp = new CompositeImage(imp, IJ.COMPOSITE);
					return imp;
				} else
					return null;
			case JPEG:
				imp = openJpegOrGif(directory, name);
				if (imp!=null&&imp.getWidth()!=0) return imp; else return null;
			case GIF:
				imp = (ImagePlus)IJ.runPlugIn("ij.plugin.GIF_Reader", path);
				if (imp!=null&&imp.getWidth()!=0) return imp; else return null;
			case PNG: 
				imp = openUsingImageIO(directory+name);
				if (imp!=null&&imp.getWidth()!=0) return imp; else return null;
			case BMP:
				imp = (ImagePlus)IJ.runPlugIn("ij.plugin.BMP_Reader", path);
				if (imp.getWidth()!=0) return imp; else return null;
			case ZIP:
				return openZip(path);
			case AVI:
				AVI_Reader reader = new AVI_Reader();
				reader.setVirtual(true);
				reader.displayDialog(!IJ.macroRunning());
				reader.run(path);
				return reader.getImagePlus();
			case JAVA_OR_TEXT:
				if (name.endsWith(".txt"))
					return openTextImage(directory,name);
				else
					return null;
			case UNKNOWN: case TEXT:
				return openUsingHandleExtraFileTypes(fileType, path);
			default:
				return null;
		}
	}
	
	// Call HandleExtraFileTypes plugin to see if it can handle unknown format
	private ImagePlus openUsingHandleExtraFileTypes(int fileType, String path) {
		int[] wrap = new int[] {fileType};
		ImagePlus imp = openWithHandleExtraFileTypes(path, wrap);
		if (imp!=null && imp.getNChannels()>1)
			imp = new CompositeImage(imp, IJ.COLOR);
		fileType = wrap[0];
		if (imp==null && (fileType==UNKNOWN||fileType==TIFF))
			IJ.error("Opener", "Unsupported format or file not found:\n"+path);
		return imp;
	}
	
	String getPath() {
		OpenDialog od = new OpenDialog("Open", "");
		String dir = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return null;
		else
			return dir+name;
	}

	/** Opens the specified text file as a float image. */
	public ImagePlus openTextImage(String dir, String name) {
		String path = dir+name;
		TextReader tr = new TextReader();
		ImageProcessor ip = tr.open(path);
		return ip!=null?new ImagePlus(name,ip):null;
	}

	/**
	 * Attempts to open the specified url as a tiff, zip compressed tiff, 
	 * dicom, gif or jpeg. Tiff file names must end in ".tif", ZIP file names 
	 * must end in ".zip" and dicom file names must end in ".dcm". Returns an 
	 * ImagePlus object if successful.
	 * @see ij.IJ#openImage(String)
	*/
	public ImagePlus openURL(String url) {
		url = updateUrl(url);
		if (IJ.debugMode) IJ.log("OpenURL: "+url);
		ImagePlus imp = openCachedImage(url);
		if (imp!=null)
			return imp;
		try {
			String name = "";
			int index = url.lastIndexOf('/');
			if (index==-1)
				index = url.lastIndexOf('\\');
			if (index>0)
				name = url.substring(index+1);
			else
				throw new MalformedURLException("Invalid URL: "+url);
			if (url.indexOf(" ")!=-1)
				url = url.replaceAll(" ", "%20");
			URL u = new URL(url);
			IJ.showStatus(""+url);
			String lurl = url.toLowerCase(Locale.US);
			if (lurl.endsWith(".tif")) {
				this.url = url;
				imp = openTiff(u.openStream(), name);
			} else if (lurl.endsWith(".zip"))
				imp = openZipUsingUrl(u);
			else if (lurl.endsWith(".jpg") || lurl.endsWith(".gif"))
				imp = openJpegOrGifUsingURL(name, u);
			else if (lurl.endsWith(".dcm") || lurl.endsWith(".ima")) {
				imp = (ImagePlus)IJ.runPlugIn("ij.plugin.DICOM", url);
				if (imp!=null && imp.getWidth()==0) imp = null;
			} else if (lurl.endsWith(".png"))
				imp = openPngUsingURL(name, u);
			else {
				URLConnection uc = u.openConnection();
				String type = uc.getContentType();
				if (type!=null && (type.equals("image/jpeg")||type.equals("image/gif")))
					imp = openJpegOrGifUsingURL(name, u);
				else if (type!=null && type.equals("image/png"))
					imp = openPngUsingURL(name, u);
				else
					imp = openWithHandleExtraFileTypes(url, new int[]{0});
			}
			IJ.showStatus("");
			return imp;
		} catch (Exception e) {
			String msg = e.getMessage();
			if (msg==null || msg.equals(""))
				msg = "" + e;
			IJ.error("Open URL", msg);
			return null;
		} 
	}
	
	/** Can't open imagej.nih.gov URLs due to encryption so redirect to mirror.nih.net. */
	public static String updateUrl(String url) {
		if (url==null || !url.contains("nih.gov"))
			return url;
		url = url.replace("imagej.nih.gov/ij", "mirror.imagej.net");
		url = url.replace("rsb.info.nih.gov/ij", "mirror.imagej.net");
		url = url.replace("rsbweb.nih.gov/ij", "mirror.imagej.net");
		return url;
	}
	
	private ImagePlus openCachedImage(String url) {
		if (url==null || !url.contains("/images"))
			return null;
		String ijDir = IJ.getDirectory("imagej");
		if (ijDir==null)
			return null;
		int slash = url.lastIndexOf('/');
		File file = new File(ijDir + "samples", url.substring(slash+1));
		if (!file.exists())
			return null;
		if (url.endsWith(".gif"))  // ij.plugin.GIF_Reader does not correctly handle inverting LUTs
			return openJpegOrGif(file.getParent()+File.separator, file.getName());
		return IJ.openImage(file.getPath());
	}

	/** Used by open() and IJ.open() to open text URLs. */
	void openTextURL(String url) {
		if (url.endsWith(".pdf")||url.endsWith(".zip"))
			return;
		String text = IJ.openUrlAsString(url);
		if (text!=null && text.startsWith("<Error: ")) {
			IJ.error("Open Text URL", text);
			return;
		}
		String name = url.substring(7);
		int index = name.lastIndexOf("/");
		int len = name.length();
		if (index==len-1)
			name = name.substring(0, len-1);
		else if (index!=-1 && index<len-1)
			name = name.substring(index+1);
		name = name.replaceAll("%20", " ");
		Editor ed = new Editor();
		ed.setSize(600, 300);
		ed.create(name, text);
		IJ.showStatus("");
	}

	
	public ImagePlus openWithHandleExtraFileTypes(String path, int[] fileType) {
		ImagePlus imp = null;
		if (path.endsWith(".db")) {
			// skip hidden Thumbs.db files on Windows
			fileType[0] = CUSTOM;
			return null;
		}
		imp = (ImagePlus)IJ.runPlugIn("HandleExtraFileTypes", path);
		if (imp==null) return null;
		FileInfo fi = imp.getOriginalFileInfo();
		if (fi==null) {
			fi = new FileInfo();
			fi.width = imp.getWidth();
			fi.height = imp.getHeight();
			fi.directory = getDir(path);
			fi.fileName = getName(path);
			imp.setFileInfo(fi);
		}
		if (imp.getWidth()>0 && imp.getHeight()>0) {
			fileType[0] = CUSTOM;
			return imp;
		} else {
			if (imp.getWidth()==-1)
				fileType[0] = CUSTOM; // plugin opened image so don't display error
			return null;
		}
	}

	/** Opens the ZIP compressed TIFF or DICOM at the specified URL. */
	ImagePlus openZipUsingUrl(URL url) throws IOException {
		URLConnection uc = url.openConnection();
		InputStream in = uc.getInputStream();
		ZipInputStream zis = new ZipInputStream(in);
		ZipEntry entry = zis.getNextEntry();
		if (entry==null) {
			zis.close();
			return null;
		}
		String name = entry.getName();
		if (!(name.endsWith(".tif")||name.endsWith(".dcm")))
			throw new IOException("This ZIP archive does not appear to contain a .tif or .dcm file\n"+name);
		if (name.endsWith(".dcm"))
			return openDicomStack(zis, entry);
		else
			return openTiff(zis, name);
	}
	
	ImagePlus openDicomStack(ZipInputStream zis, ZipEntry entry) throws IOException {
		ImagePlus imp = null;
		int count = 0;
		ImageStack stack = null;
		while (true) {
			if (count>0) entry = zis.getNextEntry();
			if (entry==null) break;
			String name = entry.getName();
			ImagePlus imp2 = null;
			if (name.endsWith(".dcm")) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buf = new byte[4096];
				int len, byteCount=0, progress=0;
				while (true) {
					len = zis.read(buf);
					if (len<0) break;
					out.write(buf, 0, len);
					byteCount += len;
					//IJ.showProgress((double)(byteCount%fileSize)/fileSize);
				}
				byte[] bytes = out.toByteArray();
				out.close();
				InputStream is = new ByteArrayInputStream(bytes);
				DICOM dcm = new DICOM(is);
				dcm.run(name);
				imp2 = dcm;
				is.close();
			}
			zis.closeEntry();
			if (imp2==null) continue;
			count++;
			String label = imp2.getTitle();
			String info = (String)imp2.getProperty("Info");
			if (info!=null) label += "\n" + info;
			if (count==1) {
				imp = imp2;
				imp.getStack().setSliceLabel(label, 1);
			} else {
				stack = imp.getStack();
				stack.addSlice(label, imp2.getProcessor());
				imp.setStack(stack);
			}
		}
		zis.close();
		IJ.showProgress(1.0);
		if (count==0)
			throw new IOException("This ZIP archive does not appear to contain any .dcm files");
		return imp;
	}

	ImagePlus openJpegOrGifUsingURL(String title, URL url) {
		if (url==null) return null;
		Image img = Toolkit.getDefaultToolkit().createImage(url);
		if (img!=null) {
			ImagePlus imp = new ImagePlus(title, img);
			return imp;
		} else
			return null;
	}

	ImagePlus openPngUsingURL(String title, URL url) {
		if (url==null)
			return null;
		Image img = null;
		try {
			InputStream in = url.openStream();
			img = ImageIO.read(in);
		} catch (FileNotFoundException e) {
			IJ.error("Open PNG Using URL", ""+e);
		} catch (IOException e) {
			IJ.handleException(e);
		}
		if (img!=null) {
			ImagePlus imp = new ImagePlus(title, img);
			return imp;
		} else
			return null;
	}

	ImagePlus openJpegOrGif(String dir, String name) {
		ImagePlus imp = null;
		Image img = Toolkit.getDefaultToolkit().createImage(dir+name);
		if (img!=null) {
			try {
				imp = new ImagePlus(name, img);
			} catch (Exception e) {
				IJ.error("Opener", e.getMessage()+"\n(Note: ImageJ cannot open CMYK JPEGs)\n \n"+dir+name);
				return null; // error loading image
			}				
			if (imp.getType()==ImagePlus.COLOR_RGB)
				convertGrayJpegTo8Bits(imp);
			FileInfo fi = new FileInfo();
			fi.fileFormat = fi.GIF_OR_JPG;
			fi.fileName = name;
			fi.directory = dir;
			imp.setFileInfo(fi);
		}
		return imp;
	}
	
	ImagePlus openUsingImageIO(String path) {
		ImagePlus imp = null;
		BufferedImage img = null;
		File f = new File(path);
		try {
			img = ImageIO.read(f);
		} catch (Exception e) {
			IJ.error("Open Using ImageIO", ""+e);
		} 
		if (img==null)
			return null;
		if (img.getColorModel().hasAlpha()) {
			int width = img.getWidth();
			int height = img.getHeight();
			BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics g = bi.getGraphics();
			g.setColor(Color.white);
			g.fillRect(0,0,width,height);
			g.drawImage(img, 0, 0, null);
			img = bi;
		}
		imp = new ImagePlus(f.getName(), img);
		FileInfo fi = new FileInfo();
		fi.fileFormat = fi.IMAGEIO;
		fi.fileName = f.getName();
		String parent = f.getParent();
		if (parent!=null)
			fi.directory = parent + File.separator;
		imp.setFileInfo(fi);
		return imp;
	}

	/** Converts the specified RGB image to 8-bits if the 3 channels are identical. */
	public static void convertGrayJpegTo8Bits(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		if (ip.getBitDepth()==24 && ip.isGrayscale()) {
			IJ.showStatus("Converting to 8-bit grayscale");
			new ImageConverter(imp).convertToGray8();
		}
	}

	/** Are all the images in this file the same size and type? */
	boolean allSameSizeAndType(FileInfo[] info) {
		boolean sameSizeAndType = true;
		boolean contiguous = true;
		long startingOffset = info[0].getOffset();
		int size = info[0].width*info[0].height*info[0].getBytesPerPixel();
		for (int i=1; i<info.length; i++) {
			sameSizeAndType &= info[i].fileType==info[0].fileType
				&& info[i].width==info[0].width
				&& info[i].height==info[0].height;
			contiguous &= info[i].getOffset()==startingOffset+i*size;
		}
		if (contiguous &&  info[0].fileType!=FileInfo.RGB48)
			info[0].nImages = info.length;
		//if (IJ.debugMode) {
		//	IJ.log("sameSizeAndType: " + sameSizeAndType);
		//	IJ.log("contiguous: " + contiguous);
		//}
		return sameSizeAndType;
	}
	
	/** Attemps to open a tiff file as a stack. Returns 
		an ImagePlus object if successful. */
	public ImagePlus openTiffStack(FileInfo[] info) {
		if (info.length>1 && !allSameSizeAndType(info))
			return null;
		FileInfo fi = info[0];
		if (fi.nImages>1)
			return new FileOpener(fi).openImage(); // open contiguous images as stack
		else {
			ColorModel cm = createColorModel(fi);
			ImageStack stack = new ImageStack(fi.width, fi.height, cm);
			Object pixels = null;
			long skip = fi.getOffset();
			int imageSize = fi.width*fi.height*fi.getBytesPerPixel();
			if (info[0].fileType==FileInfo.GRAY12_UNSIGNED) {
				imageSize = (int)(fi.width*fi.height*1.5);
				if ((imageSize&1)==1) imageSize++; // add 1 if odd
			} if (info[0].fileType==FileInfo.BITMAP) {
				int scan=(int)Math.ceil(fi.width/8.0);
				imageSize = scan*fi.height;
			}
			long loc = 0L;
			int nChannels = 1;
			try {
				InputStream is = createInputStream(fi);
				ImageReader reader = new ImageReader(fi);
				IJ.resetEscape();
				for (int i=0; i<info.length; i++) {
					nChannels = 1;
					Object[] channels = null;
					if (!silentMode)
						IJ.showStatus("Reading: " + (i+1) + "/" + info.length);
					if (IJ.escapePressed()) {
						IJ.beep();
						IJ.showProgress(1.0);
						return null;
					}
					fi.stripOffsets = info[i].stripOffsets;
					fi.stripLengths = info[i].stripLengths;
					int bpp = info[i].getBytesPerPixel();
					if (info[i].samplesPerPixel>1 && !(bpp==3||bpp==4||bpp==6)) {
						nChannels = fi.samplesPerPixel;
						channels = new Object[nChannels];
						for (int c=0; c<nChannels; c++) {
							pixels = reader.readPixels(is, c==0?skip:0L);
							channels[c] = pixels;
						}
					} else 
						pixels = reader.readPixels(is, skip);
					if (pixels==null && channels==null) break;
					loc += imageSize*nChannels+skip;
					if (i<(info.length-1)) {
						skip = info[i+1].getOffset()-loc;
						if (info[i+1].compression>=FileInfo.LZW) skip = 0;
						if (skip<0L) {
							IJ.error("Opener", "Unexpected image offset");
							break;
						}
					}
					if (fi.fileType==FileInfo.RGB48) {
						Object[] pixels2 = (Object[])pixels;
						stack.addSlice(null, pixels2[0]);					
						stack.addSlice(null, pixels2[1]);					
						stack.addSlice(null, pixels2[2]);
						isRGB48 = true;					
					} else if (nChannels>1) {
						for (int c=0; c<nChannels; c++) {
							if (channels[c]!=null)
								stack.addSlice(null, channels[c]);
						}
					} else
						stack.addSlice(null, pixels);
					IJ.showProgress(i, info.length);
				}
				is.close();
			}
			catch (Exception e) {
				IJ.handleException(e);
			}
			catch(OutOfMemoryError e) {
				IJ.outOfMemory(fi.fileName);
				stack.deleteLastSlice();
				stack.deleteLastSlice();
			}
			IJ.showProgress(1.0);
			if (stack.size()==0)
				return null;
			if (fi.fileType==FileInfo.GRAY16_UNSIGNED||fi.fileType==FileInfo.GRAY12_UNSIGNED
			||fi.fileType==FileInfo.GRAY32_FLOAT||fi.fileType==FileInfo.RGB48) {
				ImageProcessor ip = stack.getProcessor(1);
				ip.resetMinAndMax();
				stack.update(ip);
			}
			//if (fi.whiteIsZero)
			//	new StackProcessor(stack, stack.getProcessor(1)).invert();
			ImagePlus imp = new ImagePlus(fi.fileName, stack);
			new FileOpener(fi).setCalibration(imp);
			imp.setFileInfo(fi);
			if (fi.info!=null)
				imp.setProperty("Info", fi.info);
			if (fi.description!=null && fi.description.contains("order=zct"))
				new HyperStackConverter().shuffle(imp, HyperStackConverter.ZCT);
			int stackSize = stack.size();
			if (nChannels>1 && (stackSize%nChannels)==0) {
				imp.setDimensions(nChannels, stackSize/nChannels, 1);
				imp = new CompositeImage(imp, IJ.COMPOSITE);
				imp.setOpenAsHyperStack(true);
			} else if (imp.getNChannels()>1)
				imp = makeComposite(imp, fi);
			IJ.showProgress(1.0);
			return imp;
		}
	}
	
	/** Attempts to open the specified file as a tiff.
		Returns an ImagePlus object if successful. */
	public ImagePlus openTiff(String directory, String name) {
		TiffDecoder td = new TiffDecoder(directory, name);
		if (IJ.debugMode) td.enableDebugging();
		FileInfo[] info=null;
		try {
			info = td.getTiffInfo();
		} catch (IOException e) {
			return openUsingHandleExtraFileTypes(TIFF, directory+name);
		}
		if (info==null)
			return null;
		return openTiff2(info);
	}
	
	/** Opens the nth image of the specified TIFF stack. */
	public ImagePlus openTiff(String path, int n) {
		TiffDecoder td = new TiffDecoder(getDir(path), getName(path));
		if (IJ.debugMode) td.enableDebugging();
		FileInfo[] info=null;
		try {
			info = td.getTiffInfo();
		} catch (IOException e) {
			String msg = e.getMessage();
			if (msg==null||msg.equals("")) msg = ""+e;
			IJ.error("Open TIFF", msg);
			return null;
		}
		if (info==null) return null;
		FileInfo fi = info[0];
		if (info.length==1 && fi.nImages>1) {
			if (n<1 || n>fi.nImages)
				throw new IllegalArgumentException("N out of 1-"+fi.nImages+" range");
			long size = fi.width*fi.height*fi.getBytesPerPixel();
			fi.longOffset = fi.getOffset() + (n-1)*(size+fi.getGap());
			fi.offset = 0;
			fi.nImages = 1;
		} else {
			if (n<1 || n>info.length)
				throw new IllegalArgumentException("N out of 1-"+info.length+" range");
			fi.longOffset = info[n-1].getOffset();
			fi.offset = 0;
			fi.stripOffsets = info[n-1].stripOffsets; 
			fi.stripLengths = info[n-1].stripLengths; 
		}
		FileOpener fo = new FileOpener(fi);
		return fo.openImage();
	}

	/** Returns the FileInfo of the specified TIFF file. */
	public static FileInfo[] getTiffFileInfo(String path) {
		Opener o = new Opener();
		TiffDecoder td = new TiffDecoder(o.getDir(path), o.getName(path));
		if (IJ.debugMode) td.enableDebugging();
		try {
			return td.getTiffInfo();
		} catch (IOException e) {
			return null;
		}
	}

	/** Attempts to open the specified inputStream as a
		TIFF, returning an ImagePlus object if successful. */
	public ImagePlus openTiff(InputStream in, String name) {
		FileInfo[] info = null;
		try {
			TiffDecoder td = new TiffDecoder(in, name);
			if (IJ.debugMode) td.enableDebugging();
			info = td.getTiffInfo();
		} catch (FileNotFoundException e) {
			IJ.error("Open TIFF", "File not found: "+e.getMessage());
			return null;
		} catch (Exception e) {
			IJ.error("Open TIFF", ""+e);
			return null;
		}
		if (url!=null && info!=null && info.length==1 && info[0].inputStream!=null) {
			try {
				info[0].inputStream.close();
			} catch (IOException e) {}
			try {
				info[0].inputStream = new URL(url).openStream();
			} catch (Exception e) {
				IJ.error("Open TIFF", ""+e);
				return null;
			}
		}
		return openTiff2(info);
	}

	/** Opens a single TIFF or DICOM contained in a ZIP archive,
		or a ZIPed collection of ".roi" files created by the ROI manager. */	
	public ImagePlus openZip(String path) {
		ImagePlus imp = null;
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(path));
			ZipEntry entry = zis.getNextEntry();
			if (entry==null) {
				zis.close();
				return null;
			}
			String name = entry.getName();
			if (name.endsWith(".roi")) {
				zis.close();
				if (!silentMode)
					if (IJ.isMacro() && Interpreter.isBatchMode() && RoiManager.getInstance()==null)
						IJ.log("Use roiManager(\"Open\", path) instead of open(path)\nto open ROI sets in batch mode macros.");
					else
						IJ.runMacro("roiManager(\"Open\", getArgument());", path);
				return null;
			}
			if (name.endsWith(".tif")) {
				imp = openTiff(zis, name);
			} else if (name.endsWith(".dcm")) {
				DICOM dcm = new DICOM(zis);
				dcm.run(name);
				imp = dcm;
			} else {
				zis.close();
				IJ.error("Opener", "This ZIP archive does not appear to contain a \nTIFF (\".tif\") or DICOM (\".dcm\") file, or ROIs (\".roi\").");
				return null;
			}
			zis.close();
		} catch (Exception e) {
			IJ.error("Opener", ""+e);
			return null;
		}
		File f = new File(path);
		FileInfo fi = imp.getOriginalFileInfo();
		if (fi!=null) {
			fi.fileFormat = FileInfo.ZIP_ARCHIVE;
			fi.fileName = f.getName();
			String parent = f.getParent();
			if (parent!=null)
				fi.directory = parent+File.separator;
		}
		return imp;
	}
	
	/** Deserialize a byte array that was serialized using the FileSaver.serialize(). */
	public ImagePlus deserialize(byte[] bytes) {
		ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
		TiffDecoder decoder = new TiffDecoder(stream, "Untitled");
		if (IJ.debugMode)
			decoder.enableDebugging();
		FileInfo[] info = null;
		try {
			info = decoder.getTiffInfo();
		} catch (IOException e) {
			return null;
		}
		FileOpener opener = new FileOpener(info[0]);
		ImagePlus imp = opener.openImage();
		if (imp==null)
			return null;
		imp.setTitle(info[0].fileName);
		imp = makeComposite(imp, info[0]);
		return imp;
   }
   
	private ImagePlus makeComposite(ImagePlus imp, FileInfo fi) {
		int c = imp.getNChannels();
		boolean composite = c>1 && fi.description!=null && fi.description.indexOf("mode=")!=-1;
		if (c>1 && (imp.getOpenAsHyperStack()||composite) && !imp.isComposite() && imp.getType()!=ImagePlus.COLOR_RGB) {
			int mode = IJ.COLOR;
			if (fi.description!=null) {
				if (fi.description.indexOf("mode=composite")!=-1)
					mode = IJ.COMPOSITE;
				else if (fi.description.indexOf("mode=gray")!=-1)
					mode = IJ.GRAYSCALE;
			}
			imp = new CompositeImage(imp, mode);
		}
		return imp;
	}

		
	public String getName(String path) {
		int i = path.lastIndexOf('/');
		if (i==-1)
			i = path.lastIndexOf('\\');
		if (i>0)
			return path.substring(i+1);
		else
			return path;
	}
	
	public String getDir(String path) {
		int i = path.lastIndexOf('/');
		if (i==-1)
			i = path.lastIndexOf('\\');
		if (i>0)
			return path.substring(0, i+1);
		else
			return "";
	}

	ImagePlus openTiff2(FileInfo[] info) {
		if (info==null)
			return null;
		ImagePlus imp = null;
		if (IJ.debugMode) // dump tiff tags
			IJ.log(info[0].debugInfo);
		if (info.length>1) { // try to open as stack
			imp = openTiffStack(info);
			if (imp!=null)
				return imp;
		}
		FileOpener fo = new FileOpener(info[0]);
		imp = fo.openImage();
		if (imp==null)
			return null;
		int[] offsets = info[0].stripOffsets;
		if (offsets!=null&&offsets.length>1 && offsets[offsets.length-1]<offsets[0])
			ij.IJ.run(imp, "Flip Vertically", "stack");
		imp = makeComposite(imp, info[0]);
		if (imp.getBitDepth()==32 && imp.getTitle().startsWith("FFT of"))
			return openFFT(imp);
		else
			return imp;
	}
	
	private ImagePlus openFFT(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		FHT fht = new FHT(ip, true);
		ImageProcessor ps = fht.getPowerSpectrum();
		ImagePlus imp2 = new ImagePlus(imp.getTitle(), ps);
		imp2.setProperty("FHT", fht);
		imp2.setProperty("Info", imp.getInfoProperty());
		fht.originalWidth = (int)imp2.getNumericProperty("width");
		fht.originalHeight = (int)imp2.getNumericProperty("height");
		fht.originalBitDepth = (int)imp2.getNumericProperty("bitdepth");
		fht.originalColorModel = ip.getColorModel();
		imp2.setCalibration(imp.getCalibration());
		return imp2;
	}
	
	/** Attempts to open the specified ROI, returning null if unsuccessful. */
	public Roi openRoi(String path) {
		Roi roi = null;
		RoiDecoder rd = new RoiDecoder(path);
		try {roi = rd.getRoi();}
		catch (IOException e) {
			IJ.error("RoiDecoder", e.getMessage());
			return null;
		}
		return roi;
	}
	
	/** Opens an image file using the Bio-Formats plugin. */
	public static ImagePlus openUsingBioFormats(String path) {
		String className = "loci.plugins.BF";
		String methodName = "openImagePlus";
		try {
			Class c = IJ.getClassLoader().loadClass(className);
			if (c==null)
				return null;
			Class[] argClasses = new Class[1];
			argClasses[0] = methodName.getClass();
			Method m = c.getMethod(methodName, argClasses);
			Object[] args = new Object[1];
			args[0] = path;
			Object obj = m.invoke(null, args);
			ImagePlus[] images = obj!=null?(ImagePlus[])obj:null;
			if (images==null || images.length==0)
				return null;
			ImagePlus imp = images[0];
			if (imp.getStackSize()==3 && imp.getNChannels()==3 && imp.getBitDepth()==8)
				imp = imp.flatten();
			return imp;
		} catch(Exception e) {
		}
		return null;
	}

	/** Opens a lookup table (LUT) and returns it as a LUT object, or returns null if there is an error.
	 * @see ij.ImagePlus#setLut
	*/
	public static LUT openLut(String filePathOrUrl) {
		return LutLoader.openLut(filePathOrUrl);
	}

	/** Opens a tab or comma delimited text file in the Results window. */
	public static void openResultsTable(String path) {
		try {
			ResultsTable rt = ResultsTable.open(path);
			rt.showRowNumbers(true);
			if (rt!=null)
				rt.show("Results");
		} catch(IOException e) {
			IJ.error("Open Results", e.getMessage());
		}
	}
	
	/** Opens a tab or comma delimited text file. */
	public static void openTable(String path) {
		String name = "";
		if (path==null || path.equals("")) {
			OpenDialog od = new OpenDialog("Open Table...");
			String dir = od.getDirectory();
			name = od.getFileName();
			if (name==null)
				return;
			else
				path = dir+name;
		} else {
			name = (new Opener()).getName(path);
			if (name.startsWith("Results."))
				name = "Results";
		}
		try {
			ResultsTable rt = ResultsTable.open(path);
			if (rt!=null)
				rt.show(name);
		} catch(IOException e) {
			IJ.error("Open Table", e.getMessage());
		}
	}

	public static String getFileFormat(String path) {
		if (!((new File(path)).exists()))
			return("not found");
		else
			return Opener.types[(new Opener()).getFileType(path)];
	}
	
	/**
	Attempts to determine the image file type by looking for
	'magic numbers' and the file name extension.
	 */
	public int getFileType(String path) {
		if (openUsingPlugins && !path.endsWith(".txt") &&  !path.endsWith(".java"))
			return UNKNOWN;
		File file = new File(path);
		String name = file.getName();
		InputStream is;
		byte[] buf = new byte[132];
		try {
			is = new FileInputStream(file);
			is.read(buf, 0, 132);
			is.close();
		} catch (IOException e) {
			return UNKNOWN;
		}
		
		int b0=buf[0]&255, b1=buf[1]&255, b2=buf[2]&255, b3=buf[3]&255;
		//IJ.log("getFileType: "+ name+" "+b0+" "+b1+" "+b2+" "+b3);
		
		 // Combined TIFF and DICOM created by GE Senographe scanners
		if (buf[128]==68 && buf[129]==73 && buf[130]==67 && buf[131]==77
		&& ((b0==73 && b1==73)||(b0==77 && b1==77)))
			return TIFF_AND_DICOM;

		 // Big-endian TIFF ("MM")
		if (name.endsWith(".lsm"))
				return UNKNOWN; // The LSM	Reader plugin opens these files
		if (b0==73 && b1==73 && b2==42 && b3==0 && !(bioformats&&name.endsWith(".flex")))
			return TIFF;

		 // Little-endian TIFF ("II")
		if (b0==77 && b1==77 && b2==0 && b3==42)
			return TIFF;

		 // JPEG
		if (b0==255 && b1==216 && b2==255)
			return JPEG;

		 // GIF ("GIF8")
		if (b0==71 && b1==73 && b2==70 && b3==56)
			return GIF;

		name = name.toLowerCase(Locale.US);

		 // DICOM ("DICM" at offset 128)
		if (buf[128]==68 && buf[129]==73 && buf[130]==67 && buf[131]==77 || name.endsWith(".dcm")) {
			return DICOM;
		}

		// ACR/NEMA with first tag = 00002,00xx or 00008,00xx
		if ((b0==8||b0==2) && b1==0 && b3==0 && !name.endsWith(".spe") && !name.equals("fid"))	
				return DICOM;

		// PGM ("P1", "P4", "P2", "P5", "P3" or "P6")
		if (b0==80&&(b1==49||b1==52||b1==50||b1==53||b1==51||b1==54)&&(b2==10||b2==13||b2==32||b2==9))
			return PGM;

		// Lookup table
		if (name.endsWith(".lut"))
			return LUT;
		
		// PNG
		if (b0==137 && b1==80 && b2==78 && b3==71)
			return PNG;
				
		// ZIP containing a TIFF
		if (name.endsWith(".zip"))
			return ZIP;

		// FITS ("SIMP")
		if ((b0==83 && b1==73 && b2==77 && b3==80) || name.endsWith(".fts.gz") || name.endsWith(".fits.gz"))
			return FITS;
			
		// Java source file, text file or macro
		if (name.endsWith(".java") || name.endsWith(".txt") || name.endsWith(".ijm") || name.endsWith(".js")
			|| name.endsWith(".bsh") || name.endsWith(".py") || name.endsWith(".html"))
			return JAVA_OR_TEXT;

		// ImageJ, NIH Image, Scion Image for Windows ROI
		if (b0==73 && b1==111) // "Iout"
			return ROI;
			
		// ObjectJ project
		if ((b0=='o' && b1=='j' && b2=='j' && b3==0) || name.endsWith(".ojj") )
			return OJJ;

		// Results table (tab-delimited or comma-separated tabular text)
		if (name.endsWith(".xls") || name.endsWith(".csv") || name.endsWith(".tsv")) 
			return TABLE;

		// AVI
		if (name.endsWith(".avi"))
			return AVI;

		// Text file
		boolean isText = true;
		for (int i=0; i<10; i++) {
		  int c = buf[i]&255;
		  if ((c<32&&c!=9&&c!=10&&c!=13) || c>126) {
			  isText = false;
			  break;
		  }
		}
		if (isText)
		   return TEXT;

		// BMP ("BM")
		if ((b0==66 && b1==77)||name.endsWith(".dib"))
			return BMP;
				
		// RAW
		if (name.endsWith(".raw") && !Prefs.skipRawDialog)
			return RAW;

		return UNKNOWN;
	}

	/** Returns an IndexColorModel for the image specified by this FileInfo. */
	ColorModel createColorModel(FileInfo fi) {
		if (fi.lutSize>0)
			return new IndexColorModel(8, fi.lutSize, fi.reds, fi.greens, fi.blues);
		else
			return LookUpTable.createGrayscaleColorModel(fi.whiteIsZero);
	}

	/** Returns an InputStream for the image described by this FileInfo. */
	InputStream createInputStream(FileInfo fi) throws IOException, MalformedURLException {
		if (fi.inputStream!=null)
			return fi.inputStream;
		else if (fi.url!=null && !fi.url.equals(""))
			return new URL(fi.url+fi.fileName).openStream();
		else {
			File f = new File(fi.getFilePath());
			if (f==null || f.isDirectory())
				return null;
			else {
				InputStream is = new FileInputStream(f);
				if (fi.compression>=FileInfo.LZW || (fi.stripOffsets!=null&&fi.stripOffsets.length>1))
					is = new RandomAccessStream(is);
				return is;
			}
		}
	}
	
	void openRGB48(ImagePlus imp) {
			isRGB48 = false;
			int stackSize = imp.getStackSize();
			imp.setDimensions(3, stackSize/3, 1);
			imp = new CompositeImage(imp, IJ.COMPOSITE);
			imp.show();
	}
	
	/** The "Opening: path" status message is not displayed in silent mode. */
	public void setSilentMode(boolean mode) {
		silentMode = mode;
	}

	/** Open all images using HandleExtraFileTypes. Set from
		a macro using setOption("openUsingPlugins", true). */
	public static void setOpenUsingPlugins(boolean b) {
		openUsingPlugins = b;
	}

	/** Returns the state of the openUsingPlugins flag. */
	public static boolean getOpenUsingPlugins() {
		return openUsingPlugins;
	}
		
}
