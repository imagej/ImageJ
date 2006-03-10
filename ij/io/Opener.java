package ij.io;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.URL;
import java.net.*;
import java.util.zip.*;
import java.util.Locale;
import javax.swing.*;
import javax.swing.filechooser.*;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.frame.*;
import ij.plugin.Zip_Reader;
import ij.text.TextWindow;
import ij.util.Java2;
import java.awt.event.KeyEvent;

/** Opens tiff (and tiff stacks), dicom, fits, pgm, jpeg, bmp or
	gif images, and look-up tables, using a file open dialog or a path.
	Calls HandleExtraFileTypes plugin if the file type is unrecognised. */
public class Opener {

	public static final int UNKNOWN=0,TIFF=1,DICOM=2,FITS=3,PGM=4,JPEG=5,
		GIF=6,LUT=7,BMP=8,ZIP=9,JAVA_OR_TEXT=10,ROI=11,TEXT=12,PNG=13,
		TIFF_AND_DICOM=14,CUSTOM=15;
	private static final String[] types = {"unknown","tif","dcm","fits","pgm",
		"jpg","gif","lut","bmp","zip","java/txt","roi","txt","png","t&d"};
	private static String defaultDirectory = null;
	private static int fileType;
	private boolean error;
	private boolean isRGB48;
	private boolean silentMode;
	private String omDirectory;
	private File[] omFiles;


	public Opener() {
	}

	/** Displays a file open dialog box and then opens the tiff, dicom, 
		fits, pgm, jpeg, bmp, gif, lut, roi, or text file selected by 
		the user. Displays an error message if the selected file is not
		in one of the supported formats. This is the method that
		ImageJ's File/Open command uses to open files. */
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

	/** Displays a JFileChooser and then opens the tiff, dicom, 
		fits, pgm, jpeg, bmp, gif, lut, roi, or text files selected by 
		the user. Displays error messages if one or more of the selected 
		files is not in one of the supported formats. This is the method
		that ImageJ's File/Open command uses to open files if
		"Open/Save Using JFileChooser" is checked in EditOptions/Misc. */
	public void openMultiple() {
		if (!IJ.isJava2()) return;
		Java2.setSystemLookAndFeel();
		// run JFileChooser in a separate thread to avoid possible thread deadlocks
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					JFileChooser fc = new JFileChooser();
					fc.setMultiSelectionEnabled(true);
					File dir = null;
					String sdir = OpenDialog.getDefaultDirectory();
					if (sdir!=null)
						dir = new File(sdir);
					if (dir!=null)
						fc.setCurrentDirectory(dir);
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

	/** Opens and displays a tiff, dicom, fits, pgm, jpeg, bmp, gif, lut, 
		roi, or text file. Displays an error message if the specified file
		is not in one of the supported formats. */
	public void open(String path) {
        boolean fullPath = path.startsWith("/") || path.indexOf(":\\")==1;
        if (!fullPath) {
            String workingDir = OpenDialog.getDefaultDirectory();
            if (workingDir!=null)
                path = workingDir + path;
        }
		if (!silentMode) IJ.showStatus("Opening: " + path);
		long start = System.currentTimeMillis();
		ImagePlus imp = openImage(path);
		if (imp!=null) {
			WindowManager.checkForDuplicateName = true;
			if (isRGB48)
				openRGB48(imp);
			else
				imp.show(IJ.d2s((System.currentTimeMillis()-start)/1000.0,3)+" seconds");
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
					boolean betterTextArea = IJ.isJava2() || IJ.isMacintosh();
					int maxSize = 250000;
					long size = file.length();
					if (size>=28000 && betterTextArea) {
						String osName = System.getProperty("os.name");
						if (osName.equals("Windows 95") || osName.equals("Windows 98") || osName.equals("Windows Me"))
							maxSize = 60000;
					}
					if (size<28000 || (betterTextArea && size<maxSize)) {
						Editor ed = (Editor)IJ.runPlugIn("ij.plugin.frame.Editor", "");
						if (ed!=null) ed.open(getDir(path), getName(path));
					} else
						new TextWindow(path,400,450);
					break;
				case UNKNOWN:
					String msg =
						"File is not in TIFF, JPEG, GIF, BMP, DICOM, FITS, PGM, \n"
						+"ZIP, LUT, ROI or text format, or it was not found.";
					if (path!=null && path.length()<=64)
						msg += " \n  \n   "+path;
					IJ.error("Opener", msg);
					error = true;
					break;
			}
		}
	}

	/** Attempts to open the specified file as a tiff, bmp, dicom, fits,
		pgm, gif or jpeg image. Returns an ImagePlus object if successful.
		Modified by Gregory Jefferis to call HandleExtraFileTypes plugin if 
		the file type is unrecognised. */
	public ImagePlus openImage(String directory, String name) {
		ImagePlus imp;
		if (directory.length()>0 && !directory.endsWith(Prefs.separator))
			directory += Prefs.separator;
		String path = directory+name;
		fileType = getFileType(path);
		if (IJ.debugMode)
			IJ.log("openImage: \""+types[fileType]+"\", "+path);
		switch (fileType) {
			case TIFF:
				imp = openTiff(directory, name);
				return imp;
			case DICOM:
				imp = (ImagePlus)IJ.runPlugIn("ij.plugin.DICOM", path);
				if (imp.getWidth()!=0) return imp; else return null;
			case TIFF_AND_DICOM:
				// "hybrid" files created by GE-Senographe 2000 D */
				imp = openTiff(directory,name);
				ImagePlus imp2 = (ImagePlus)IJ.runPlugIn("ij.plugin.DICOM", path);
				if (imp!=null)				
					imp.setProperty("Info",imp2.getProperty("Info"));
				return imp;
			case FITS:
				imp = (ImagePlus)IJ.runPlugIn("ij.plugin.FITS", path);
				if (imp.getWidth()!=0) return imp; else return null;
			case PGM:
				imp = (ImagePlus)IJ.runPlugIn("ij.plugin.PGM_Reader", path);
				if (imp.getWidth()!=0) return imp; else return null;
			case JPEG: case GIF: case PNG:
				imp = openJpegOrGif(directory, name);
				if (imp!=null&&imp.getWidth()!=0) return imp; else return null;
			case BMP:
				imp = (ImagePlus)IJ.runPlugIn("ij.plugin.BMP_Reader", path);
				if (imp.getWidth()!=0) return imp; else return null;
			case ZIP:
				imp = (ImagePlus)IJ.runPlugIn("ij.plugin.Zip_Reader", path);
				if (imp.getWidth()!=0) return imp; else return null;
			case UNKNOWN: case TEXT:
				// Call HandleExtraFileTypes plugin to see if it can handle unknown format
				imp = (ImagePlus)IJ.runPlugIn("HandleExtraFileTypes", path);
				if (imp==null) return null;
				if (imp.getWidth()>0 && imp.getHeight()>0) {
					fileType = CUSTOM;
					return imp;
				} else {
					if (imp.getWidth()==-1)
						fileType = CUSTOM; // plugin opened image so don't display error
					return null;
				}
			default:
				return null;
		}
	}
	
	/** Attempts to open the specified file as a tiff, bmp, dicom, fits,
	pgm, gif or jpeg. Returns an ImagePlus object if successful. */
	public ImagePlus openImage(String path) {
		if (path==null || path.equals("")) return null;
		ImagePlus img = null;
		if (path.indexOf("://")>0)
			img = openURL(path);
		else
			img = openImage(getDir(path), getName(path));
		return img;
	}

	/** Attempts to open the specified url as a tiff, zip compressed tiff, 
		dicom, gif or jpeg. Tiff file names must end in ".tif", ZIP file names 
		must end in ".zip" and dicom file names must end in ".dcm". Returns an 
		ImagePlus object if successful. */
	public ImagePlus openURL(String url) {
	   	try {
			String name = "";
			int index = url.lastIndexOf('/');
			if (index==-1)
				index = url.lastIndexOf('\\');
			if (index>0)
				name = url.substring(index+1);
			else
				throw new MalformedURLException("Invalid URL: "+url);
			URL u = new URL(url);
			IJ.showStatus(""+url);
			ImagePlus imp = null;
		    if (url.endsWith(".tif") || url.endsWith(".TIF"))
				imp = openTiff(u.openStream(), name);
	 	    else if (url.endsWith(".zip"))
				imp = openZip(u);
	 	    else if (url.endsWith(".dcm")) {
				imp = (ImagePlus)IJ.runPlugIn("ij.plugin.DICOM", url);
				if (imp!=null && imp.getWidth()==0) imp = null;
			} else
				imp = openJpegOrGifUsingURL(name, u);
			IJ.showStatus("");
			return imp;
    	} catch (Exception e) {
    		String msg = e.getMessage();
    		if (msg==null || msg.equals(""))
    			msg = "" + e;	
			IJ.error("Open URL",msg + "\n \n" + url);
			return null;
	   	} 
	}
	
	/** Opens the ZIP compressed TIFF at the specified URL. */
	ImagePlus openZip(URL url) throws IOException {
		IJ.showProgress(0.01);
		URLConnection uc = url.openConnection();
		int fileSize = uc.getContentLength(); // compressed size
		fileSize *=2; // estimate uncompressed size
      	InputStream in = uc.getInputStream();
		ZipInputStream zin = new ZipInputStream(in);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		ZipEntry entry = zin.getNextEntry();
		if (entry==null)
			return null;
		String name = entry.getName();
		//double fileSize = entry.getSize(); //returns -1
		if (!name.endsWith(".tif"))
			throw new IOException("This ZIP archive does not appear to contain a TIFF file");
		int len;
		int byteCount = 0;
		int progress = 0;
		while (true) {
			len = zin.read(buf);
			if (len<0) break;
			out.write(buf, 0, len);
			byteCount += len;
			IJ.showProgress((double)(byteCount%fileSize)/fileSize);
		}
		zin.close();
		byte[] bytes = out.toByteArray();
		IJ.showProgress(1.0);
		return openTiff(new ByteArrayInputStream(bytes), name);
	}

	ImagePlus openJpegOrGifUsingURL(String title, URL url) {
		if (url==null)
			return null;
		Toolkit tk = Toolkit.getDefaultToolkit();
		Image img = IJ.isJava2()?tk.createImage(url):tk.getImage(url);
		if (img!=null) {
			ImagePlus imp = new ImagePlus(title, img);
			return imp;
		} else
			return null;
	}

	ImagePlus openJpegOrGif(String dir, String name) {
	   	ImagePlus imp = null;
		Toolkit tk = Toolkit.getDefaultToolkit();
		Image img = IJ.isJava2()?tk.createImage(dir+name):tk.getImage(dir+name);
 		if (img!=null) {
 			try {
 				imp = new ImagePlus(name, img);
 			} catch (IllegalStateException e) {
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
	
	/** If this image is grayscale, convert it to 8-bits. */
	public static void convertGrayJpegTo8Bits(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		int width = ip.getWidth();
		int height = ip.getHeight();
		int[] pixels = (int[])ip.getPixels();
		int c,r,g,b,offset;
		for (int y=0; y<(height-8); y++) {
			offset = y*width;
			for (int x=0; x<(width-8); x++) {
				c = pixels[offset+x];
				r = (c&0xff0000)>>16;
				g = (c&0xff00)>>8;
				b = c&0xff;
				if (!((r==g)&&(g==b))) {
					//IJ.write("count: "+count+" "+r+" "+g+" "+b);
					return;
				}
			}
			//count++;
		}
		IJ.showStatus("Converting to 8-bits");
		new ImageConverter(imp).convertToGray8();
	}

	/** Are all the images in this file the same size and type? */
	boolean allSameSizeAndType(FileInfo[] info) {
		boolean sameSizeAndType = true;
		boolean contiguous = true;
		int startingOffset = info[0].offset;
		int size = info[0].width*info[0].height*info[0].getBytesPerPixel();
		for (int i=1; i<info.length; i++) {
			sameSizeAndType &= info[i].fileType==info[0].fileType
				&& info[i].width==info[0].width
				&& info[i].height==info[0].height;
			contiguous &= info[i].offset==startingOffset+i*size;
		}
		if (contiguous &&  info[0].fileType!=FileInfo.RGB48)
			info[0].nImages = info.length;
		if (IJ.debugMode) {
			IJ.log("  sameSizeAndType: " + sameSizeAndType);
			IJ.log("  contiguous: " + contiguous);
		}
		return sameSizeAndType;
	}
	
	/** Attemps to open a tiff file as a stack. Returns 
		an ImagePlus object if successful. */
	public ImagePlus openTiffStack(FileInfo[] info) {
		if (info.length>1 && !allSameSizeAndType(info))
			return null;
		FileInfo fi = info[0];
		if (fi.nImages>1)
			return new FileOpener(fi).open(false); // open contiguous images as stack
		else {
			ColorModel cm = createColorModel(fi);
			ImageStack stack = new ImageStack(fi.width, fi.height, cm);
			Object pixels;
			int skip = fi.offset;
			int imageSize = fi.width*fi.height*fi.getBytesPerPixel();
			if (info[0].fileType==FileInfo.GRAY12_UNSIGNED) {
				imageSize = (int)(fi.width*fi.height*1.5);
				if ((imageSize&1)==1) imageSize++; // add 1 if odd
			}
			int loc = 0;
			
			try {
				InputStream is = createInputStream(fi);
				ImageReader reader = new ImageReader(fi);
				for (int i=0; i<info.length; i++) {
					IJ.showStatus("Reading: " + (i+1) + "/" + info.length);
					pixels = reader.readPixels(is, skip);
					if (pixels==null) break;
					loc += imageSize+skip;
					if (i<(info.length-1)) {
						skip = info[i+1].offset-loc;
						if (skip<0) throw new IOException("Images are not in order");
					}
					if (fi.fileType==FileInfo.RGB48) {
						Object[] pixels2 = (Object[])pixels;
						stack.addSlice(null, pixels2[0]);					
						stack.addSlice(null, pixels2[1]);					
						stack.addSlice(null, pixels2[2]);
						isRGB48 = true;					
					} else
						stack.addSlice(null, pixels);					
					IJ.showProgress((double)i/info.length);
				}
				is.close();
			}
			catch (Exception e) {
				IJ.write("" + e);
			}
			catch(OutOfMemoryError e) {
				IJ.outOfMemory(fi.fileName);
				stack.deleteLastSlice();
				stack.deleteLastSlice();
			}
			IJ.showProgress(1.0);
			if (stack.getSize()==0)
				return null;
			if (fi.fileType==FileInfo.GRAY16_UNSIGNED||fi.fileType==FileInfo.GRAY12_UNSIGNED
			||fi.fileType==FileInfo.GRAY32_FLOAT||fi.fileType==FileInfo.RGB48) {
				ImageProcessor ip = stack.getProcessor(1);
				ip.resetMinAndMax();
				stack.update(ip);
			}
			if (fi.whiteIsZero)
				new StackProcessor(stack, stack.getProcessor(1)).invert();
			ImagePlus imp = new ImagePlus(fi.fileName, stack);
			new FileOpener(fi).setCalibration(imp);
			imp.setFileInfo(fi);
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
		try {info = td.getTiffInfo();}
		catch (IOException e) {
			String msg = e.getMessage();
			if (msg==null||msg.equals("")) msg = ""+e;
			IJ.error("TiffDecoder", msg);
			return null;
		}
		if (info==null)
			return null;
		return openTiff2(info);
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
			IJ.error("TiffDecoder", "File not found: "+e.getMessage());
			return null;
		} catch (Exception e) {
			IJ.error("TiffDecoder", ""+e);
			return null;
		}
		return openTiff2(info);
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
		imp = fo.open(false);
		//IJ.showStatus("");
		return imp;
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

	/**
	Attempts to determine the image file type by looking for
	'magic numbers' and the file name extension.
	 */
	public int getFileType(String path) {
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
        		return UNKNOWN; // The LSM  Reader plugin opens these files
		if (b0==73 && b1==73 && b2==42 && b3==0)
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
 		if ((b0==8||b0==2) && b1==0 && b3==0 && !name.endsWith(".spe")) 	
  			 	return DICOM;

		// FITS ("SIMP")
		if (b0==83 && b1==73 && b2==77 && b3==80)
			return FITS;
			
		// PGM ("P1", "P4", "P2", "P5", "P3" or "P6")
		if (b0==80&&(b1==49||b1==52||b1==50||b1==53||b1==51||b1==54)&&(b2==10||b2==13||b2==32||b2==9))
			return PGM;

		// Lookup table
		if (name.endsWith(".lut"))
			return LUT;
		
		// BMP ("BM")
		if (b0==66 && b1==77 && name.endsWith(".bmp"))
			return BMP;
				
		// PNG
		if (b0==137 && b1==80 && b2==78 && b3==71 && IJ.isJava2())
			return PNG;
				
		// ZIP containing a TIFF
		if (name.endsWith(".zip"))
			return ZIP;

		// Java source file or text file
		if (name.endsWith(".java") || name.endsWith(".txt") || name.endsWith(".ijm"))
			return JAVA_OR_TEXT;

		// ImageJ, NIH Image, Scion Image for Windows ROI
		if (b0==73 && b1==111) // "Iout"
			return ROI;
			
        // Text file
        boolean isText = true;
        for (int i=0; i<10; i++) {
          int c = buf[i];
          if ((c<32&&c!=9&&c!=10&&c!=13) || c>126) {
              isText = false;
              break;
          }
        }
        if (isText)
           return TEXT;

		return UNKNOWN;
	}

	/** Returns an IndexColorModel for the image specified by this FileInfo. */
	ColorModel createColorModel(FileInfo fi) {
		if (fi.fileType==FileInfo.COLOR8 && fi.lutSize>0)
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
		    File f = new File(fi.directory + fi.fileName);
		    if (f==null || f.isDirectory())
		    	return null;
		    else
				return new FileInputStream(f);
		}
	}
	
	void openRGB48(ImagePlus imp) {
			ImageStack stack = imp.getStack();
			ImageStack stack1 = imp.createEmptyStack();
			ImageStack stack2 = imp.createEmptyStack();
			ImageStack stack3 = imp.createEmptyStack();
			int n = imp.getStackSize()/3;
			for (int i=0; i<n; i++) {
				stack1.addSlice(null, stack.getProcessor(1));
				stack.deleteSlice(1);
				stack2.addSlice(null, stack.getProcessor(1));
				stack.deleteSlice(1);
				stack3.addSlice(null, stack.getProcessor(1));
				stack.deleteSlice(1);
			}
			ImagePlus imp1 = new ImagePlus("Red-"+imp.getTitle(), stack1);
			imp1.getProcessor().resetMinAndMax();
			imp1.show();
			ImagePlus imp2 = new ImagePlus("Green-"+imp.getTitle(), stack2);
			imp2.getProcessor().resetMinAndMax();
			imp2.show();
			ImagePlus imp3 = new ImagePlus("Blue-"+imp.getTitle(), stack3);
			imp3.getProcessor().resetMinAndMax();
			imp3.show();
	}
	
	/** The "Opening: path" status message is not displayed in silent mode. */
	public void setSilentMode(boolean mode) {
		silentMode = mode;
	}

}
