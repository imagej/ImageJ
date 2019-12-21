package ij;
import ij.process.*;
import ij.io.*;
import ij.gui.ImageCanvas;
import ij.util.Tools;
import ij.plugin.FolderOpener;
import java.io.*;
import java.awt.*;
import java.awt.image.ColorModel;
import java.util.Properties;

/** This class represents an array of disk-resident images. */
public class VirtualStack extends ImageStack {
	private static final int INITIAL_SIZE = 100;
	private String path;
	private int nSlices;
	private String[] names;
	private String[] labels;
	private int bitDepth;
	private Properties  properties;

	
	/** Default constructor. */
	public VirtualStack() { }
	
	public VirtualStack(int width, int height) {
		super(width, height);
	}

	/** Creates an empty virtual stack.
	 * @param width		image width
	 * @param height	image height
	 * @param cm	ColorModel or null
	 * @param path	file path of directory containing the images
	 * @see #addSlice(String)
	 * @see <a href="http://wsr.imagej.net/macros/js/OpenAsVirtualStack.js">OpenAsVirtualStack.js</a>
	*/
	public VirtualStack(int width, int height, ColorModel cm, String path) {
		super(width, height, cm);
		if (path.length()>0 && !(path.endsWith(File.separator)||path.endsWith("/")))
			path = path + "/";
		this.path = path;
		names = new String[INITIAL_SIZE];
		labels = new String[INITIAL_SIZE];
		//IJ.log("VirtualStack: "+path);
	}

	/** Creates a virtual stack with no backing storage.
	This example creates a one million slice virtual
	stack that uses just 1MB of RAM:
	<pre>
    stack = new VirtualStack(1024,1024,1000000);
    new ImagePlus("No Backing Store Virtual Stack",stack).show();
	</pre>
	*/
	public VirtualStack(int width, int height, int slices) {
		this(width, height, slices, 8);
	}

	public VirtualStack(int width, int height, int slices, int bitDepth) {
		super(width, height, null);
		nSlices = slices;
		this.bitDepth = bitDepth;
	}

	/** Adds an image to the end of the stack. The argument 
	 * can be a full file path (e.g., "C:/Users/wayne/dir1/image.tif")
	 * if the 'path' argument in the constructor is "". File names
	 * that start with '.' are ignored.
	*/
	public void addSlice(String fileName) {
		if (fileName==null) 
			throw new IllegalArgumentException("'fileName' is null!");
		if (fileName.startsWith("."))
			return;
		nSlices++;
	   //IJ.log("addSlice: "+nSlices+"	"+fileName);
	   if (nSlices==names.length) {
			String[] tmp = new String[nSlices*2];
			System.arraycopy(names, 0, tmp, 0, nSlices);
			names = tmp;
			tmp = new String[nSlices*2];
			System.arraycopy(labels, 0, tmp, 0, nSlices);
			labels = tmp;
		}
		names[nSlices-1] = fileName;
	}

   /** Does nothing. */
	public void addSlice(String sliceLabel, Object pixels) {
	}

	/** Does nothing.. */
	public void addSlice(String sliceLabel, ImageProcessor ip) {
	}
	
	/** Does noting. */
	public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
	}

	/** Deletes the specified slice, were 1<=n<=nslices. */
	public void deleteSlice(int n) {
		if (n<1 || n>nSlices)
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (nSlices<1)
			return;
		for (int i=n; i<nSlices; i++)
			names[i-1] = names[i];
		names[nSlices-1] = null;
		nSlices--;
	}
	
	/** Deletes the last slice in the stack. */
	public void deleteLastSlice() {
		if (nSlices>0)
			deleteSlice(nSlices);
	}
	   
   /** Returns the pixel array for the specified slice, were 1<=n<=nslices. */
	public Object getPixels(int n) {
		ImageProcessor ip = getProcessor(n);
		if (ip!=null)
			return ip.getPixels();
		else
			return null;
	}		
	
	 /** Assigns a pixel array to the specified slice,
		were 1<=n<=nslices. */
	public void setPixels(Object pixels, int n) {
	}

   /** Returns an ImageProcessor for the specified slice,
		were 1<=n<=nslices. Returns null if the stack is empty.
	*/
	public ImageProcessor getProcessor(int n) {
		if (path==null) {  //Help>Examples?JavaScript>Terabyte VirtualStack
			ImageProcessor ip = null;
			int w=getWidth(), h=getHeight();
			switch (bitDepth) {
				case 8: ip = new ByteProcessor(w,h); break;
				case 16: ip = new ShortProcessor(w,h); break;
				case 24: ip = new ColorProcessor(w,h); break;
				case 32: ip = new FloatProcessor(w,h); break;
			}
			int value = 0;
			ImagePlus img = WindowManager.getCurrentImage();
			if (img!=null && img.getStackSize()==nSlices)
				value = img.getCurrentSlice()-1;
			if (bitDepth==16)
				value *= 256;
			if (bitDepth!=32) {
				for (int i=0; i<ip.getPixelCount(); i++)
					ip.set(i,value++);
			}
			label(ip, ""+n, Color.white);
			return ip;
		}
		Opener opener = new Opener();
		opener.setSilentMode(true);
		IJ.redirectErrorMessages(true);
		ImagePlus imp = opener.openImage(path+names[n-1]);
		IJ.redirectErrorMessages(false);
		ImageProcessor ip = null;
		int depthThisImage = 0;
		if (imp!=null) {
			int w = imp.getWidth();
			int h = imp.getHeight();
			int type = imp.getType();
			ColorModel cm = imp.getProcessor().getColorModel();
			String info = (String)imp.getProperty("Info");
			if (info!=null) {
				if (FolderOpener.useInfo(info))
					labels[n-1] = info;
			} else {
				String sliceLabel = imp.getStack().getSliceLabel(1);
				if (FolderOpener.useInfo(sliceLabel))
					labels[n-1] = "Label: "+sliceLabel;
			}
			depthThisImage = imp.getBitDepth();
			ip = imp.getProcessor();
			ip.setOverlay(imp.getOverlay());
			properties = imp.getProperty("FHT")!=null?imp.getProperties():null;
		} else {
			File f = new File(path, names[n-1]);
			String msg = f.exists()?"Error opening ":"File not found: ";
			ip = new ByteProcessor(getWidth(), getHeight());
			ip.invert();
			label(ip, msg+names[n-1], Color.black);
			depthThisImage = 8;
		}
		if (depthThisImage!=bitDepth) {
			switch (bitDepth) {
				case 8: ip=ip.convertToByte(true); break;
				case 16: ip=ip.convertToShort(true); break;
				case 24:  ip=ip.convertToRGB(); break;
				case 32: ip=ip.convertToFloat(); break;
			}
		}
		if (ip.getWidth()!=getWidth() || ip.getHeight()!=getHeight()) {
			ImageProcessor ip2 = ip.createProcessor(getWidth(), getHeight());
			ip2.insert(ip, 0, 0);
			ip = ip2;
		}
		return ip;
	 }
	 	 
	 private void label(ImageProcessor ip, String msg, Color color) {
		int size = getHeight()/20;
		if (size<9) size=9;
		Font font = new Font("Helvetica", Font.PLAIN, size);
		ip.setFont(font);
		ip.setAntialiasedText(true);
		ip.setColor(color);
		ip.drawString(msg, size, size*2);
	}
 
	/** Currently not implemented */
	public int saveChanges(int n) {
		return -1;
	}

	 /** Returns the number of slices in this stack. */
	public int getSize() {
		return nSlices;
	}

	/** Returns the label of the Nth image. */
	public String getSliceLabel(int n) {
		if (labels==null)
			return null;
		String label = labels[n-1];
		if (label==null)
			return names[n-1];
		else {
			if (label.startsWith("Label: "))  // slice label
				return label.substring(7,label.length());
			else
				return names[n-1]+"\n"+label;
		}
	}
	
	/** Returns null. */
	public Object[] getImageArray() {
		return null;
	}

   /** Does nothing. */
	public void setSliceLabel(String label, int n) {
	}

	/** Always return true. */
	public boolean isVirtual() {
		return true;
	}

   /** Does nothing. */
	public void trim() {
	}
	
	/** Returns the path to the directory containing the images. */
	public String getDirectory() {
		String path2 = path;
		if (!(path2.endsWith("/") || path2.endsWith(File.separator)))
			path2 = path2 + "/";
		return path2;
	}
		
	/** Returns the file name of the specified slice, were 1<=n<=nslices. */
	public String getFileName(int n) {
		return names[n-1];
	}
	
	/** Sets the bit depth (8, 16, 24 or 32). */
	public void setBitDepth(int bitDepth) {
		this.bitDepth = bitDepth;
	}

	/** Returns the bit depth (8, 16, 24 or 32), or 0 if the bit depth is not known. */
	public int getBitDepth() {
		return bitDepth;
	}
	
	public ImageStack sortDicom(String[] strings, String[] info, int maxDigits) {
		int n = getSize();
		String[] names2 = new String[n];
		for (int i=0; i<n; i++)
			names2[i] = names[i];
		for (int i=0; i<n; i++) {
			int slice = (int)Tools.parseDouble(strings[i].substring(strings[i].length()-maxDigits), 0.0);
			if (slice==0) return null;
			names[i] = names2[slice-1];
			labels[i] = info[slice-1];
		}
		return this;
	}
	
	/** Returns the ImagePlus Properties assoctated with the current slice, or null. */
	public Properties getProperties() {
		return properties;
	}


} 

