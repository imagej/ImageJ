package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import java.awt.*;
import java.io.*;
import java.util.Properties;

/** This plugin opens a multi-page TIFF file, or a set of raw images, as a 
	virtual stack. It implements the File/Import/TIFF Virtual Stack command. */
public class FileInfoVirtualStack extends VirtualStack implements PlugIn {
	private FileInfo[] info;
	private int nImages;
	
	/* Default constructor. */
	public FileInfoVirtualStack() {}

	/* Constructs a FileInfoVirtualStack from a FileInfo object. */
	public FileInfoVirtualStack(FileInfo fi) {
		info = new FileInfo[1];
		info[0] = fi;
		ImagePlus imp = open();
		if (imp!=null)
			imp.show();
	}

	/* Constructs a FileInfoVirtualStack from a FileInfo 
		object and displays it if 'show' is true. */
	public FileInfoVirtualStack(FileInfo fi, boolean show) {
		info = new FileInfo[1];
		info[0] = fi;
		ImagePlus imp = open();
		if (imp!=null && show)
			imp.show();
	}
	
	/* Constructs a FileInfoVirtualStack from an array of FileInfo objects. */
	public FileInfoVirtualStack(FileInfo[] fi) {
		info = fi;
		nImages = info.length;
	}

	/** Opens the specified tiff file as a virtual stack. */
	public static ImagePlus openVirtual(String path) {
		OpenDialog  od = new OpenDialog("Open TIFF", path);
		String name = od.getFileName();
		String  dir = od.getDirectory();
		if (name==null)
			return null;
		FileInfoVirtualStack stack = new FileInfoVirtualStack();
		stack.init(dir, name);
		if (stack.info==null)
			return null;
		else
			return stack.open();
	}

	public void run(String arg) {
		OpenDialog  od = new OpenDialog("Open TIFF", arg);
		String name = od.getFileName();
		String  dir = od.getDirectory();
		if (name==null)
			return;
		init(dir, name);
		if (info==null)
			return;
		ImagePlus imp = open();
		if (imp!=null)
			imp.show();
	}
	
	private void init(String dir, String name) {
		if (name.endsWith(".zip")) {
			IJ.error("Virtual Stack", "ZIP compressed stacks not supported");
			return;
		}
		TiffDecoder td = new TiffDecoder(dir, name);
		if (IJ.debugMode) td.enableDebugging();
		IJ.showStatus("Decoding TIFF header...");
		try {
			info = td.getTiffInfo();
		} catch (IOException e) {
			String msg = e.getMessage();
			if (msg==null||msg.equals("")) msg = ""+e;
			IJ.error("TiffDecoder", msg);
			return;
		}
		if (info==null || info.length==0) {
			IJ.error("Virtual Stack", "This does not appear to be a TIFF stack");
			return;
		}
		if (IJ.debugMode)
			IJ.log(info[0].debugInfo);
	}
		
	private ImagePlus open() {
		FileInfo fi = info[0];
		int n = fi.nImages;
		if (info.length==1 && n>1) {
			long bytesPerImage = fi.width*fi.height*fi.getBytesPerPixel();
			if (fi.fileType==FileInfo.GRAY12_UNSIGNED)
				bytesPerImage = (int)(1.5*fi.width)*fi.height;
			n = validateNImages(fi, bytesPerImage);
			info = new FileInfo[n];
			for (int i=0; i<n; i++) {
				info[i] = (FileInfo)fi.clone();
				info[i].nImages = 1;
				info[i].longOffset = fi.getOffset() + i*(bytesPerImage + fi.getGap());
			}
		}
		nImages = info.length;
		FileOpener fo = new FileOpener(info[0]);
		ImagePlus imp = fo.openImage();
		if (nImages==1 && fi.fileType==FileInfo.RGB48)
			return imp;
		Properties props = fo.decodeDescriptionString(fi);
		ImagePlus imp2 = new ImagePlus(fi.fileName, this);
		imp2.setDisplayRange(imp.getDisplayRangeMin(),imp.getDisplayRangeMax());
		imp2.setFileInfo(fi);
		if (imp!=null && props!=null) {
			setBitDepth(imp.getBitDepth());
			imp2.setCalibration(imp.getCalibration());
			imp2.setOverlay(imp.getOverlay());
			if (fi.info!=null)
				imp2.setProperty("Info", fi.info);
			int channels = getInt(props,"channels");
			int slices = getInt(props,"slices");
			int frames = getInt(props,"frames");
			if (channels*slices*frames==nImages) {
				imp2.setDimensions(channels, slices, frames);
				if (getBoolean(props, "hyperstack"))
					imp2.setOpenAsHyperStack(true);
			}
			if (channels>1 && fi.description!=null) {
				int mode = IJ.COMPOSITE;
				if (fi.description.indexOf("mode=color")!=-1)
					mode = IJ.COLOR;
				else if (fi.description.indexOf("mode=gray")!=-1)
					mode = IJ.GRAYSCALE;
				imp2 = new CompositeImage(imp2, mode);
			}
		}
		return imp2;
	}
	
	private int validateNImages(FileInfo fi, long bytesPerImage) {
		File f = new File(fi.getFilePath());
		if (!f.exists())
			return fi.nImages;
		long fileLength = f.length();
		for (int i=fi.nImages-1; i>=0; i--) {
			long offset =  fi.getOffset() + i*(bytesPerImage+fi.getGap());
			if (offset+bytesPerImage<=fileLength)
				return i+1;
		}
		return fi.nImages;
	}

	int getInt(Properties props, String key) {
		Double n = getNumber(props, key);
		return n!=null?(int)n.doubleValue():1;
	}

	Double getNumber(Properties props, String key) {
		String s = props.getProperty(key);
		if (s!=null) {
			try {
				return Double.valueOf(s);
			} catch (NumberFormatException e) {}
		}	
		return null;
	}

	boolean getBoolean(Properties props, String key) {
		String s = props.getProperty(key);
		return s!=null&&s.equals("true")?true:false;
	}

	/** Deletes the specified image, where {@literal 1<=n<=nImages}. */
	public void deleteSlice(int n) {
		if (n<1 || n>nImages)
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (nImages<1) return;
		for (int i=n; i<nImages; i++)
			info[i-1] = info[i];
		info[nImages-1] = null;
		nImages--;
	}
	
	/** Returns an ImageProcessor for the specified image,
		where {@literal 1<=n<=nImages}. Returns null if the stack is empty.
	*/
	public ImageProcessor getProcessor(int n) {
		n = translate(n);  // update n for hyperstacks not in default CZT order
		if (n<1 || n>nImages)
			throw new IllegalArgumentException("Argument out of range: "+n);
		//if (n>1) IJ.log("  "+(info[n-1].getOffset()-info[n-2].getOffset()));
		info[n-1].nImages = 1; // why is this needed?
		ImageProcessor ip = null;
		if (IJ.debugMode) {
			long t0 = System.currentTimeMillis();
			FileOpener fo = new FileOpener(info[n-1]);
			ip = fo.openProcessor();
			IJ.log("FileInfoVirtualStack: "+n+", offset="+info[n-1].getOffset()+", "+(System.currentTimeMillis()-t0)+"ms");
		} else {
			FileOpener fo = new FileOpener(info[n-1]);
			if (info[n-1].fileType==FileInfo.RGB48) {
				ImagePlus imp = fo.openImage();
				if (info[n-1].sliceNumber>0)
					imp.setSlice(info[n-1].sliceNumber);
				ip = imp.getProcessor();
			} else
				ip = fo.openProcessor();
		}
		if (ip!=null) {
			if (cTable!=null)
				ip.setCalibrationTable(cTable);
			return ip;
		} else {
			int w=getWidth(), h=getHeight();
			IJ.log("Read error or file not found ("+n+"): "+info[n-1].directory+info[n-1].fileName);
			switch (getBitDepth()) {
				case 8: return new ByteProcessor(w, h);
				case 16: return new ShortProcessor(w, h);
				case 24: return new ColorProcessor(w, h);
				case 32: return new FloatProcessor(w, h);
				default: return null;
			}
		}
	 }
 
	/** Returns the number of slices in this stack. */
	public int size() {
		return getSize();
	}

	public int getSize() {
		return nImages;
	}

	/** Returns the label of the Nth image. */
	public String getSliceLabel(int n) {
		if (n<1 || n>nImages)
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (info[0].sliceLabels==null || info[0].sliceLabels.length!=nImages)
			return null;
		else
			return info[0].sliceLabels[n-1];
	}

	public int getWidth() {
		return info[0].width;
	}
	
	public int getHeight() {
		return info[0].height;
	}
	
	/** Adds an image to this stack. */
	public synchronized  void addImage(FileInfo fileInfo) {
		nImages++;
		if (info==null)
			info = new FileInfo[250];
		if (nImages==info.length) {
			FileInfo[] tmp = new FileInfo[nImages*2];
			System.arraycopy(info, 0, tmp, 0, nImages);
			info = tmp;
		}
		info[nImages-1] = fileInfo;
	}
	
	@Override
	public String getDirectory() {
		if (info!=null && info.length>0)
			return info[0].directory;
		else
			return null;
	}
		
	@Override
	public String getFileName(int n) {
		int index = n - 1;
		if (index>=0 && info!=null && info.length>index)
			return info[index].fileName;
		else
			return null;
	}

		
}
