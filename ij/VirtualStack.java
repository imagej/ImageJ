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
	private int delay;
	private Properties properties;
	private boolean generateData;
	private int[] indexes;  		// used to translate non-CZT hyperstack slice numbers (0-based)
	/** subclasses that support translation of slice indices (by calling translate) should set this to true */
	protected boolean canTranslate;

	
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
		path = IJ.addSeparator(path);
		this.path = path;
		if (path != null)
			canTranslate = true;
		names = new String[INITIAL_SIZE];
		labels = new String[INITIAL_SIZE];
	}

	/** Creates a virtual stack with no backing storage.<br>
	 * See: Help&gt;Examples&gt;JavaScript&gt;Terabyte VirtualStack
	*/
	public VirtualStack(int width, int height, int slices) {
		this(width, height, slices, "8-bit");
	}

	/** Creates a virtual stack with no backing storage.<br>
	 * See: Help&gt;Examples&gt;JavaScript&gt;Terabyte VirtualStack
	 */
	public VirtualStack(int width, int height, int slices, String options) {
		super(width, height, null);
		nSlices = slices;
		int depth = 8;
  		if (options.contains("16-bit")) depth=16;
 	    if (options.contains("RGB")) depth=24;
        if (options.contains("32-bit")) depth=32;
        if (options.contains("delay")) delay=250;
        this.generateData = options.contains("fill");
		this.bitDepth = depth;
	}

	/** Adds an image to the end of a virtual stack created using the
	 * VirtualStack(w,h,cm,path) constructor. The argument 
	 * can be a full file path (e.g., "C:/Users/wayne/dir1/image.tif")
	 * if the 'path' argument in the constructor is "". File names
	 * that start with '.' are ignored.
	*/
	public void addSlice(String fileName) {
		if (fileName==null) 
			throw new IllegalArgumentException("'fileName' is null!");
		if (fileName.startsWith("."))
			return;
		if (names==null)
			throw new IllegalArgumentException("This Virtual Stack cannot be modified [VirtualStack(w,h,cm,path) constructor not used]");
		if (isTranslatingIndices())
			throw new IllegalArgumentException("Virtual hyperstack with non-czt order cannot be modified");
		nSlices++;
		expandArrays(nSlices);
		names[nSlices-1] = fileName;
	}

	/** Expands the 'names' and 'labels' arrays if existing and smaller than minSize.
	 *  This should be called each time the stack is enlarged */
	protected void expandArrays(int minSize) {
		if (indexes != null && minSize < indexes.length)
			minSize = indexes.length; //min size if slices were deleted previously
		if (names != null && names.length < minSize) {
			String[] tmp = new String[minSize*2];
			System.arraycopy(names, 0, tmp, 0, names.length);
			names = tmp;
		}
		if (labels != null && labels.length < minSize) {
			String[] tmp = new String[minSize*2];
			System.arraycopy(labels, 0, tmp, 0, labels.length);
			labels = tmp;
		}
	}

   /** Does nothing. */
	public void addSlice(String sliceLabel, Object pixels) {
	}

	/** Does nothing.. */
	public void addSlice(String sliceLabel, ImageProcessor ip) {
	}
	
	/** Does nothing. */
	public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
	}

	/** Deletes the specified slice, where {@literal 1<=n<=nslices}. */
	public void deleteSlice(int n) {
		if (nSlices==0)
			return;
		if (n<1 || n>nSlices)
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (names==null)
			throw new IllegalArgumentException("This Virtual Stack cannot be modified [VirtualStack(w,h,cm,path) constructor not used]");
		if (isTranslatingIndices())
			throw new IllegalArgumentException("Virtual hyperstack with non-czt order cannot be modified");
		for (int i=n; i<nSlices; i++)
			names[i-1] = names[i];
		names[nSlices-1] = null;
		if (labels != null)
			for (int i=n; i<nSlices; i++)
				labels[i-1] = labels[i];
		nSlices--;
	}
	
	/** Deletes the last slice in the stack. */
	public void deleteLastSlice() {
		int n = size();
		if (n>0)
			deleteSlice(n);
	}
	   
   /** Returns the pixel array for the specified slice, where {@literal 1<=n<=nslices}. */
	public Object getPixels(int n) {
		ImageProcessor ip = getProcessor(n);
		if (ip!=null)
			return ip.getPixels();
		else
			return null;
	}		
	
	public void setPixels(Object pixels, int n) {
	}

	/** Returns an ImageProcessor for the specified slice,
	 * where {@literal 1<=n<=nslices}. Returns null if
	 * the stack is empty.
	 * When overriding this function, note that the ImageProcessor
	 * may be modified at a later time. Its pixel array
	 * can get replaced by the pixel array of another slice.
	 * This is because the ImageProcessor may be re-used when a
	 * different slice is accessed via ImagePlus.setSlice.
	 * {@link <a href="https://wsr.imagej.net/plugins/Test_Virtual_Stack2.java#gemsiv">Example</a>}
	*/
	public ImageProcessor getProcessor(int n) {
		if (path==null) {  //Help>Examples>JavaScript>Terabyte VirtualStack
			ImageProcessor ip = null;
			int w=getWidth(), h=getHeight();
			switch (bitDepth) {
				case 8: ip = new ByteProcessor(w,h); break;
				case 16: ip = new ShortProcessor(w,h); break;
				case 24: ip = new ColorProcessor(w,h); break;
				case 32: ip = new FloatProcessor(w,h); break;
			}
			String hlabel = null;
			if (generateData) {
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
				if (img!=null && img.isHyperStack()) {
					int[] pos = img.convertIndexToPosition(n);
					hlabel = pos[0]+" "+pos[1]+" "+pos[2]+" "+n;
				}
			}
			label(ip, hlabel!=null?hlabel:""+n, Color.white);
			if (delay>0)
				IJ.wait(delay);
			return ip;
		}
		n = translate(n);  // update n for hyperstacks not in the default CZT order
		Opener opener = new Opener();
		opener.setSilentMode(true);
		IJ.redirectErrorMessages(true);
		ImagePlus imp = opener.openTempImage(path, names[n-1]);
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
					setSliceLabel(info, n);
			} else {
				String sliceLabel = imp.getStack().getSliceLabel(1);
				if (FolderOpener.useInfo(sliceLabel))
					setSliceLabel("Label: "+sliceLabel, n);
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
		if (cTable!=null)
			ip.setCalibrationTable(cTable);
		ip.setSliceNumber(n);
		return ip;
	 }

	 /** Draw label for Help>Examples>JavaScript>Terabyte VirtualStack */	 
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
	public int size() {
		return getSize();
	}

	/** Returns the number of slices in this stack. Subclasses shold override this function. */
	public int getSize() {
		return nSlices;
	}

	/** Returns the label of the Nth image. */
	public String getSliceLabel(int n) {
		if (isTranslatingIndices())
			n = translate(n);
		if (labels==null)
			return null;
		String label = (n<=labels.length) ? labels[n-1] : null; //subclass may have grown stack without adding labels
		if (names == null)
			return label;
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

	/** Sets the label of the specified slice, where {@literal 1<=n<=nSlices}. */
	public void setSliceLabel(String label, int n) {
		if (n <= 0 || n > getSize())
			throw new IllegalArgumentException(outOfRange+n);
		if (isTranslatingIndices())
			n = translate(n);
		if (labels == null)
			labels = new String[getSize()];
		expandArrays(getSize());
		labels[n-1] = label;
	}

	/** Sets the array of slice labels. The array size must be the stack size or larger */
	protected void setSliceLabels(String[] labels) {
		if (labels != null && labels.length < getSize())
			throw new IllegalArgumentException("Labels array too short: "+labels.length);
		this.labels = labels;
	}

	/** Always returns true. */
	public boolean isVirtual() {
		return true;
	}

   /** Does nothing. */
	public void trim() {
	}
	
	/** Returns the path to the directory containing the images. */
	public String getDirectory() {
		return IJ.addSeparator(path);
	}
		
	/** Returns the file name of the specified slice, where {@literal 1<=n<=nslices}. */
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
		int n = size();
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
	
	/** Sets the (0-based) table that translates slice numbers of hyperstacks not in default CZT order.
	 *  May be called with a null argument to stop translation of indexes.
	 *  When called more than once, the translations are executed sequentially. */
	public void setIndexes(int[] indexes) {
		if (indexes == null) {
			this.indexes = null;
			return;
		}
		if (indexesSorted(indexes)) return;
		if (this.indexes == null) {
			this.indexes = indexes;
		} else if (indexes.length != this.indexes.length) {
			throw new IllegalArgumentException("Invalid array length of indexes translation table");
		} else {		//apply new translation to old translation (after stack->hyperstack>stack>hyperstack to reshuffle)
			indexes = indexes.clone();
			for (int i=0; i<indexes.length; i++)
				indexes[i] = this.indexes[indexes[i]];
			this.indexes = indexesSorted(indexes) ? null : indexes;
		}
	}

	/** Returns whether an indexes array is the trivial one, sorted numbers 0...length-1 */
	boolean indexesSorted(int[] indexes) {
		for (int i=0; i<indexes.length; i++)
			if (indexes[i] != i)
				return false;
		return true;
	}

	/** Translates slice numbers of hyperstacks not in default CZT order. */
	public int translate(int n) {
		int n2 = (indexes!=null&&indexes.length==getSize()) ? indexes[n-1]+1 : n;
		//IJ.log("translate: "+n+" "+n2+" "+getSize()+" "+(indexes!=null?indexes.length:null));
		return n2;
	}

	/** Returns whether this hyperstack uses index translation for hyperstacks not in default CZT order. */
	public boolean isTranslatingIndices() {
		return canTranslate && indexes!=null;
	}

	/** Reduces the number of slices in this stack by a factor. */
	public void reduce(int factor) {
		if (factor<2 || nSlices/factor<1 || names==null)
			return;
		nSlices = nSlices/factor;
		for (int i=0; i<nSlices; i++) {
			names[i] = names[i*factor];
			if (labels != null)
				labels[i] = labels[i*factor];
		}
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			imp.setSlice(1);
			imp.updateAndRepaintWindow();
		}
	}

} 

