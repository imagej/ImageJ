package ij;
import java.awt.*;
import java.awt.image.*;
import java.net.URL;
import java.util.*;
import ij.process.*;
import ij.io.*;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.filter.Analyzer;
import ij.util.*;
import ij.macro.Interpreter;
import ij.plugin.*;
import ij.plugin.frame.*;


/**
An ImagePlus contain an ImageProcessor (2D image) or an ImageStack (3D, 4D or 5D image).
It also includes metadata (spatial calibration and possibly the directory/file where
it was read from). The ImageProcessor contains the pixel data (8-bit, 16-bit, float or RGB)
of the 2D image and some basic methods to manipulate it. An ImageStack is essentually
a list ImageProcessors of same type and size.
@see ij.process.ImageProcessor
@see ij.ImageStack
@see ij.gui.ImageWindow
@see ij.gui.ImageCanvas
*/

public class ImagePlus implements ImageObserver, Measurements, Cloneable {

	/** 8-bit grayscale (unsigned)*/
	public static final int GRAY8 = 0;

	/** 16-bit grayscale (unsigned) */
	public static final int GRAY16 = 1;

	/** 32-bit floating-point grayscale */
	public static final int GRAY32 = 2;

	/** 8-bit indexed color */
	public static final int COLOR_256 = 3;

	/** 32-bit RGB color */
	public static final int COLOR_RGB = 4;

	/** Title of image used by Flatten command */
	public static final String flattenTitle = "flatten~canvas";

	/** True if any changes have been made to this image. */
	public boolean changes;

	protected Image img;
	protected ImageProcessor ip;
	protected ImageWindow win;
	protected Roi roi;
	protected int currentSlice; // current stack index (one-based)
	protected static final int OPENED=0, CLOSED=1, UPDATED=2;
	protected boolean compositeImage;
	protected int width;
	protected int height;
	protected boolean locked;
	private int lockedCount;
	private Thread lockingThread;
	protected int nChannels = 1;
	protected int nSlices = 1;
	protected int nFrames = 1;
	protected boolean dimensionsSet;

	private ImageJ ij = IJ.getInstance();
	private String title;
	private	String url;
	private FileInfo fileInfo;
	private int imageType = GRAY8;
	private boolean typeSet;
	private ImageStack stack;
	private static int currentID = -1;
	private int ID;
	private static Component comp;
	private boolean imageLoaded;
	private int imageUpdateY, imageUpdateW;
	private Properties properties;
	private long startTime;
	private Calibration calibration;
	private static Calibration globalCalibration;
	private boolean activated;
	private boolean ignoreFlush;
	private boolean errorLoadingImage;
	private static ImagePlus clipboard;
	private static Vector listeners = new Vector();
	private boolean openAsHyperStack;
	private int[] position = {1,1,1};
	private boolean noUpdateMode;
	private ImageCanvas flatteningCanvas;
	private Overlay overlay;
	private boolean compositeChanges;
	private boolean hideOverlay;
	private static int default16bitDisplayRange;
	private boolean antialiasRendering = true;
	private boolean ignoreGlobalCalibration;
	private boolean oneSliceStack;
	public boolean setIJMenuBar = Prefs.setIJMenuBar;
	private Plot plot;
	private Properties imageProperties;


    /** Constructs an uninitialized ImagePlus. */
    public ImagePlus() {
		title = (this instanceof CompositeImage)?"composite":"null";
		setID();
    }

    /** Constructs an ImagePlus from an Image or BufferedImage. The first
		argument will be used as the title of the window that displays the image.
		Throws an IllegalStateException if an error occurs while loading the image. */
    public ImagePlus(String title, Image image) {
		this.title = title;
		if (image!=null)
			setImage(image);
		setID();
    }

    /** Constructs an ImagePlus from an ImageProcessor. */
    public ImagePlus(String title, ImageProcessor ip) {
 		setProcessor(title, ip);
   		setID();
    }

	/** Constructs an ImagePlus from a TIFF, BMP, DICOM, FITS,
		PGM, GIF or JPRG specified by a path or from a TIFF, DICOM,
		GIF or JPEG specified by a URL. */
    public ImagePlus(String pathOrURL) {
    	Opener opener = new Opener();
    	ImagePlus imp = null;
    	boolean isURL = pathOrURL.indexOf("://")>0;
    	if (isURL)
    		imp = opener.openURL(pathOrURL);
    	else
    		imp = opener.openImage(pathOrURL);
    	if (imp!=null) {
    		if (imp.getStackSize()>1)
    			setStack(imp.getTitle(), imp.getStack());
    		else
     			setProcessor(imp.getTitle(), imp.getProcessor());
     		setCalibration(imp.getCalibration());
     		properties = imp.getProperties();
     		setFileInfo(imp.getOriginalFileInfo());
     		setDimensions(imp.getNChannels(), imp.getNSlices(), imp.getNFrames());
     		setOverlay(imp.getOverlay());
     		setRoi(imp.getRoi());
   			if (isURL)
   				this.url = pathOrURL;
   			setID();
    	}
    }

	/** Constructs an ImagePlus from a stack. */
    public ImagePlus(String title, ImageStack stack) {
    	setStack(title, stack);
    	setID();
    }

    private void setID() {
    	ID = --currentID;
	}

	/** Locks the image so other threads can test to see if it is in use.
	 * One thread can lock an image multiple times, then it has to unlock
	 * it as many times until it is unlocked. This allows nested locking
	 * within a thread.
	 * Returns true if the image was successfully locked.
	 * Beeps, displays a message in the status bar, and returns
	 * false if the image is already locked by another thread.
	*/
	public synchronized boolean lock() {
		return lock(true);
	}

	/** Similar to lock, but doesn't beep and display an error
	 * message if the attempt to lock the image fails.
	*/
	public synchronized boolean lockSilently() {
		return lock(false);
	}

	private synchronized boolean lock(boolean loud) {
		if (locked) {
			if (Thread.currentThread()==lockingThread) {
				lockedCount++; //allow locking multiple times by the same thread
				return true;
			} else {
				if (loud) {
					IJ.beep();
					IJ.showStatus("\"" + title + "\" is locked");
					if (IJ.debugMode) IJ.log(title + " is locked by " + lockingThread + "; refused locking by " + Thread.currentThread().getName());
					if (IJ.macroRunning())
						IJ.wait(500);
				}
				return false;
			}
		} else {
			locked = true;  //we could use 'lockedCount instead, but subclasses might use
			lockedCount = 1;
			lockingThread = Thread.currentThread();
			if (win instanceof StackWindow)
				((StackWindow)win).setSlidersEnabled(false);
			if (IJ.debugMode) IJ.log(title + ": locked" + (loud ? "" : "silently") + " by " + Thread.currentThread().getName());
			return true;
		}
	}

	/** Unlocks the image.
	 * In case the image had been locked several times by the current thread,
	 * it gets unlocked only after as many unlock operations as there were
	 * previous lock operations.
	*/
	public synchronized void unlock() {
		if (Thread.currentThread()==lockingThread && lockedCount>1)
			lockedCount--;
		else {
			locked = false;
			lockedCount = 0;
			lockingThread = null;
			if (win instanceof StackWindow)
				((StackWindow)win).setSlidersEnabled(true);
			if (IJ.debugMode) IJ.log(title + ": unlocked");
		}
	}

	/** Returns 'true' if the image is locked. */
	public boolean isLocked() {
		return locked;
	}

	/** Returns 'true' if the image was locked on another thread. */
	public boolean isLockedByAnotherThread() {
		return locked && Thread.currentThread()!=lockingThread;
	}

	private void waitForImage(Image image) {
		if (comp==null) {
			comp = IJ.getInstance();
			if (comp==null)
				comp = new Canvas();
		}
		imageLoaded = false;
		if (!comp.prepareImage(image, this)) {
			double progress;
			waitStart = System.currentTimeMillis();
			while (!imageLoaded && !errorLoadingImage) {
				IJ.wait(30);
				if (imageUpdateW>1) {
					progress = (double)imageUpdateY/imageUpdateW;
					if (!(progress<1.0)) {
						progress = 1.0 - (progress-1.0);
						if (progress<0.0) progress = 0.9;
					}
					showProgress(progress);
				}
			}
			showProgress(1.0);
		}
	}

	long waitStart;
	private void showProgress(double percent) {
		if ((System.currentTimeMillis()-waitStart)>500L)
			IJ.showProgress(percent);
	}

	/** Draws the image. If there is an ROI, its
		outline is also displayed.  Does nothing if there
		is no window associated with this image (i.e. show()
		has not been called).*/
	public void draw(){
		if (win!=null)
			win.getCanvas().repaint();
	}

	/** Draws image and roi outline using a clip rect. */
	public void draw(int x, int y, int width, int height){
		if (win!=null) {
			ImageCanvas ic = win.getCanvas();
			double mag = ic.getMagnification();
			x = ic.screenX(x);
			y = ic.screenY(y);
			width = (int)(width*mag);
			height = (int)(height*mag);
			ic.repaint(x, y, width, height);
			if (listeners.size()>0 && roi!=null && roi.getPasteMode()!=Roi.NOT_PASTING)
				notifyListeners(UPDATED);
		}
	}

	/** Updates this image from the pixel data in its
		associated ImageProcessor, then displays it. Does
		nothing if there is no window associated with
		this image (i.e. show() has not been called).*/
	public synchronized void updateAndDraw() {
		if (stack!=null && !stack.isVirtual() && currentSlice>=1 && currentSlice<=stack.size()) {		
			if (stack.size()>1 && win!=null && !(win instanceof StackWindow)) {
				setStack(stack);	//adds scroll bar if stack size has changed to >1
				return;
			}
			Object pixels = stack.getPixels(currentSlice);
			if (ip!=null && pixels!=null && pixels!=ip.getPixels()) { // was stack updated?
				try {
					ip.setPixels(pixels);
					ip.setSnapshotPixels(null);
				} catch(Exception e) {}
			}
		}
		if (win!=null) {
			win.getCanvas().setImageUpdated();
			if (listeners.size()>0) notifyListeners(UPDATED);
		}
		draw();
	}

	/** Use to update the image when the underlying virtual stack changes. */
	public void updateVirtualSlice() {
		ImageStack vstack = getStack();
		if (vstack.isVirtual()) {
			double min=getDisplayRangeMin(), max=getDisplayRangeMax();
			setProcessor(vstack.getProcessor(getCurrentSlice()));
			setDisplayRange(min,max);
		} else
			throw new IllegalArgumentException("Virtual stack required");
	}

	/** Sets the display mode of composite color images, where 'mode'
		 should be IJ.COMPOSITE, IJ.COLOR or IJ.GRAYSCALE. */
	public void setDisplayMode(int mode) {
		if (this instanceof CompositeImage) {
			((CompositeImage)this).setMode(mode);
			updateAndDraw();
		}
	}

	/** Returns the display mode (IJ.COMPOSITE, IJ.COLOR
		or IJ.GRAYSCALE) if this is a composite color
		image, or 0 if it not. */
	public int getDisplayMode() {
		if (this instanceof CompositeImage)
			return ((CompositeImage)this).getMode();
		else
			return 0;
	}

	/** Controls which channels in a composite color image are displayed,
		where 'channels' is a list of ones and zeros that specify the channels to
		display. For example, "101" causes channels 1 and 3 to be displayed. */
	public void setActiveChannels(String channels) {
		if (!(this instanceof CompositeImage))
			return;
		boolean[] active = ((CompositeImage)this).getActiveChannels();
		for (int i=0; i<active.length; i++) {
			boolean b = false;
			if (channels.length()>i && channels.charAt(i)=='1')
				b = true;
			active[i] = b;
		}
		updateAndDraw();
		Channels.updateChannels();
	}

	/** Updates this image from the pixel data in its
		associated ImageProcessor, then displays it.
		The CompositeImage class overrides this method
		to only update the current channel. */
	public void updateChannelAndDraw() {
		updateAndDraw();
	}

	/** Returns a reference to the current ImageProcessor. The
		CompositeImage class overrides this method to return
		the processor associated with the current channel. */
	public ImageProcessor getChannelProcessor() {
		return getProcessor();
	}

	/**  Returns an array containing the lookup tables used by this image,
	 * one per channel, or an empty array if this is an RGB image.
	 * @see #getNChannels
	 * @see #isComposite
	 * @see #getCompositeMode
	*/
	public LUT[] getLuts() {
		ImageProcessor ip2 = getProcessor();
		if (ip2==null)
			return new LUT[0];
		LUT lut = ip2.getLut();
		if (lut==null)
			return new LUT[0];
		LUT[] luts = new LUT[1];
		luts[0] = lut;
		return luts;
	}

	/** Calls draw to draw the image and also repaints the
		image window to force the information displayed above
		the image (dimension, type, size) to be updated. */
	public void repaintWindow() {
		if (win!=null) {
			draw();
			win.repaint();
		}
	}

	/** Calls updateAndDraw to update from the pixel data
		and draw the image, and also repaints the image
		window to force the information displayed above
		the image (dimension, type, size) to be updated. */
	public void updateAndRepaintWindow() {
		if (win!=null) {
			updateAndDraw();
			win.repaint();
		}
	}

	/** ImageCanvas.paint() calls this method when the
		ImageProcessor has generated a new image. */
	public void updateImage() {
		if (ip!=null)
			img = ip.createImage();
	}

	/** Closes the window, if any, that is displaying this image. */
	public void hide() {
		if (win==null) {
			Interpreter.removeBatchModeImage(this);
			return;
		}
		boolean unlocked = lockSilently();
		Overlay overlay2 = getOverlay();
		changes = false;
		win.close();
		win = null;
		setOverlay(overlay2);
		if (unlocked) unlock();
	}

	/** Closes this image and sets the ImageProcessor to null. To avoid the
		"Save changes?" dialog, first set the public 'changes' variable to false. */
	public void close() {
		ImageWindow win = getWindow();
		if (win!=null)
			win.close();
		else {
            if (WindowManager.getCurrentImage()==this)
                WindowManager.setTempCurrentImage(null);
			deleteRoi(); //save any ROI so it can be restored later
			Interpreter.removeBatchModeImage(this);
		}
    }

	/** Opens a window to display this image and clears the status bar. */
	public void show() {
		show("");
	}

	/** Opens a window to display this image and displays
		'statusMessage' in the status bar. */
	public void show(String statusMessage) {
		if (isVisible())
			return;
		win = null;
		if ((IJ.isMacro() && ij==null) || Interpreter.isBatchMode()) {
			if (isComposite()) ((CompositeImage)this).reset();
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) imp.saveRoi();
			WindowManager.setTempCurrentImage(this);
			Interpreter.addBatchModeImage(this);
			return;
		}
		if (Prefs.useInvertingLut && getBitDepth()==8 && ip!=null && !ip.isInvertedLut()&& !ip.isColorLut())
			invertLookupTable();
		img = getImage();
		if ((img!=null) && (width>=0) && (height>=0)) {
			activated = false;
			int stackSize = getStackSize();
			if (stackSize>1)
				win = new StackWindow(this);
			else if (getProperty(Plot.PROPERTY_KEY) != null)
				win = new PlotWindow(this, (Plot)(getProperty(Plot.PROPERTY_KEY)));
			else
				win = new ImageWindow(this);
			if (roi!=null) roi.setImage(this);
			if (overlay!=null && getCanvas()!=null)
				getCanvas().setOverlay(overlay);
			IJ.showStatus(statusMessage);
			if (IJ.isMacro()) { // wait for window to be activated
				long start = System.currentTimeMillis();
				while (!activated) {
					IJ.wait(5);
					if ((System.currentTimeMillis()-start)>2000) {
						WindowManager.setTempCurrentImage(this);
						break; // 2 second timeout
					}
				}
			}
			if (imageType==GRAY16 && default16bitDisplayRange!=0) {
				resetDisplayRange();
				updateAndDraw();
			}
			if (stackSize>1) {
				int c = getChannel();
				int z = getSlice();
				int t = getFrame();
				if (c>1 || z>1 || t>1)
					setPosition(c, z, t);
			}
			if (setIJMenuBar)
				IJ.wait(25);
			notifyListeners(OPENED);
		}
	}

	void invertLookupTable() {
		int nImages = getStackSize();
		ip.invertLut();
		if (nImages==1)
			ip.invert();
		else {
			ImageStack stack2 = getStack();
			for (int i=1; i<=nImages; i++)
				stack2.getProcessor(i).invert();
			stack2.setColorModel(ip.getColorModel());
		}
	}

	/** Called by ImageWindow.windowActivated(). */
	public void setActivated() {
		activated = true;
	}

	/** Returns this image as a AWT image. */
	public Image getImage() {
		if (img==null && ip!=null)
			img = ip.createImage();
		return img;
	}

	/** Returns a copy of this image as an 8-bit or RGB BufferedImage.
	 * @see ij.process.ShortProcessor#get16BitBufferedImage
	 */
	public BufferedImage getBufferedImage() {
		if (isComposite())
			return (new ColorProcessor(getImage())).getBufferedImage();
		else
			return ip.getBufferedImage();
	}

	/** Returns this image's unique numeric ID. */
	public int getID() {
		return ID;
	}

	/** Replaces the image, if any, with the one specified.
		Throws an IllegalStateException if an error occurs
		while loading the image. */
	public void setImage(Image image) {
		if (image instanceof BufferedImage) {
			BufferedImage bi = (BufferedImage)image;
			if (bi.getType()==BufferedImage.TYPE_USHORT_GRAY) {
				setProcessor(null, new ShortProcessor(bi));
				return;
			} else if (bi.getType()==BufferedImage.TYPE_BYTE_GRAY) {
				setProcessor(null, new ByteProcessor(bi));
				return;
			}
		}
		roi = null;
		errorLoadingImage = false;
		waitForImage(image);
		if (errorLoadingImage)
			throw new IllegalStateException ("Error loading image");
		int newWidth = image.getWidth(ij);
		int newHeight = image.getHeight(ij);
		boolean dimensionsChanged = newWidth!=width || newHeight!=height;
		width = newWidth;
		height = newHeight;
		setStackNull();
		LookUpTable lut = new LookUpTable(image);
		int type = lut.getMapSize()>0?GRAY8:COLOR_RGB;
		if (image!=null && type==COLOR_RGB)
			ip = new ColorProcessor(image);
		if (ip==null && image!=null)
			ip = new ByteProcessor(image);
		setType(type);
		this.img = ip.createImage();
		if (win!=null) {
			if (dimensionsChanged)
				win = new ImageWindow(this);
			else
				repaintWindow();
		}
	}

	/** Replaces this image with the specified ImagePlus. May
		not work as expected if 'imp' is a CompositeImage
		and this image is not. */
	public void setImage(ImagePlus imp) {
		Properties newProperties = imp.getProperties();
		if (newProperties!=null)
			newProperties = (Properties)(newProperties.clone());
		if (imp.getWindow()!=null)
			imp = imp.duplicate();
		ImageStack stack2 = imp.getStack();
		if (imp.isHyperStack())
			setOpenAsHyperStack(true);
		LUT[] luts = null;
		if (imp.isComposite() && (this instanceof CompositeImage)) {
			if (((CompositeImage)imp).getMode()!=((CompositeImage)this).getMode())
				((CompositeImage)this).setMode(((CompositeImage)imp).getMode());
			luts = ((CompositeImage)imp).getLuts();
		}
		LUT lut = !imp.isComposite()?imp.getProcessor().getLut():null;
		setStack(stack2, imp.getNChannels(), imp.getNSlices(), imp.getNFrames());
		compositeImage = imp.isComposite();
		if (luts!=null) {
			((CompositeImage)this).setLuts(luts);
			((CompositeImage)this).setMode(((CompositeImage)imp).getMode());
			updateAndRepaintWindow();
		} else if (lut!=null) {
			getProcessor().setLut(lut);
			updateAndRepaintWindow();
		}
		setTitle(imp.getTitle());
		setCalibration(imp.getCalibration());
		setOverlay(imp.getOverlay());
		properties = newProperties;
		if (getProperty(Plot.PROPERTY_KEY)!=null && win instanceof PlotWindow) {
			Plot plot = (Plot)(getProperty(Plot.PROPERTY_KEY));
			((PlotWindow)win).setPlot(plot);
			plot.setImagePlus(this);
		}
		setFileInfo(imp.getOriginalFileInfo());
		setProperty ("Info", imp.getProperty ("Info"));
	}

	/** Replaces the ImageProcessor with the one specified and updates the
		 display. With stacks, the ImageProcessor must be the same type as the
		 other images in the stack and it must be the same width and height. */
	public void setProcessor(ImageProcessor ip) {
		setProcessor(null, ip);
	}

	/** Replaces the ImageProcessor with the one specified and updates the display. With
		stacks, the ImageProcessor must be the same type as other images in the stack and
		it must be the same width and height.  Set 'title' to null to leave the title unchanged. */
	public void setProcessor(String title, ImageProcessor ip) {
		if (ip==null || ip.getPixels()==null)
			throw new IllegalArgumentException("ip null or ip.getPixels() null");
		if (getStackSize()>1) {
			if (ip.getWidth()!=width || ip.getHeight()!=height)
				throw new IllegalArgumentException("Wrong dimensions for this stack");
			int stackBitDepth = stack!=null?stack.getBitDepth():0;
			if (stackBitDepth>0 && getBitDepth()!=stackBitDepth)
				throw new IllegalArgumentException("Wrong type for this stack");
		} else {
			setStackNull();
			setCurrentSlice(1);
		}
		setProcessor2(title, ip, null);
	}

	void setProcessor2(String title, ImageProcessor ip, ImageStack newStack) {
		//IJ.log("setProcessor2: "+ip+" "+this.ip+" "+newStack);
		if (title!=null) setTitle(title);
		if (ip==null)
			return;
		this.ip = ip;
		if (this.ip!=null && getWindow()!=null)
			notifyListeners(UPDATED);
		if (ij!=null)
			ip.setProgressBar(ij.getProgressBar());
		int stackSize = 1;
		boolean dimensionsChanged = width>0 && height>0 && (width!=ip.getWidth() || height!=ip.getHeight());
		if (stack!=null) {
			stackSize = stack.size();
			if (currentSlice>stackSize)
				setCurrentSlice(stackSize);
			if (currentSlice>=1 && currentSlice<=stackSize && !dimensionsChanged)
				stack.setPixels(ip.getPixels(),currentSlice);
		}
		img = null;
		if (dimensionsChanged) roi = null;
		int type;
		if (ip instanceof ByteProcessor)
			type = GRAY8;
		else if (ip instanceof ColorProcessor)
			type = COLOR_RGB;
		else if (ip instanceof ShortProcessor)
			type = GRAY16;
		else
			type = GRAY32;
		if (width==0)
			imageType = type;
		else
			setType(type);
		width = ip.getWidth();
		height = ip.getHeight();
		if (win!=null) {
			if (dimensionsChanged && stackSize==1)
                win.updateImage(this);
			else if (newStack==null)
				repaintWindow();
				draw();
		}
	}

	/** Replaces the image with the specified stack and updates the display. */
	public void setStack(ImageStack stack) {
    	setStack(null, stack);
    }

	/** Replaces the image with the specified stack and updates
		the display. Set 'title' to null to leave the title unchanged. */
    public void setStack(String title, ImageStack newStack) {
		//IJ.log("setStack1: "+nChannels+" "+nSlices+" "+nFrames);
		int bitDepth1 = getBitDepth();
		int previousStackSize = getStackSize();
		int newStackSize = newStack.getSize();
		if (newStackSize==0)
			throw new IllegalArgumentException("Stack is empty");
		if (!newStack.isVirtual()) {
			Object[] arrays = newStack.getImageArray();
			if (arrays==null || (arrays.length>0&&arrays[0]==null))
				throw new IllegalArgumentException("Stack pixel array null");
		}
    	boolean sliderChange = false;
    	if (win!=null && (win instanceof StackWindow)) {
    		int nScrollbars = ((StackWindow)win).getNScrollbars();
    		if (nScrollbars>0 && newStackSize==1)
    			sliderChange = true;
    		else if (nScrollbars==0 && newStackSize>1)
    			sliderChange = true;
    	}
    	if (currentSlice<1) setCurrentSlice(1);
    	boolean resetCurrentSlice = currentSlice>newStackSize;
    	if (resetCurrentSlice) setCurrentSlice(newStackSize);
    	ImageProcessor ip = newStack.getProcessor(currentSlice);
    	boolean dimensionsChanged = width>0 && height>0 && (width!=ip.getWidth()||height!=ip.getHeight());
    	if (this.stack==null)
    	    newStack.viewers(+1);
    	this.stack = newStack;
    	oneSliceStack = false;
    	setProcessor2(title, ip, newStack);
		if (bitDepth1!=0 && bitDepth1!=getBitDepth())
			compositeChanges = true;
		if (compositeChanges && (this instanceof CompositeImage)) {
			this.compositeImage = getStackSize()!=getNSlices();
			((CompositeImage)this).completeReset();
			if (bitDepth1!=0 && bitDepth1!=getBitDepth())
				((CompositeImage)this).resetDisplayRanges();
		}
		compositeChanges = false;
		if (win==null) {
			if (resetCurrentSlice) setSlice(currentSlice);
			return;
		}
		boolean invalidDimensions = (isDisplayedHyperStack()||(this instanceof CompositeImage)) && (win instanceof StackWindow) && !((StackWindow)win).validDimensions();
		if (newStackSize>1 && !(win instanceof StackWindow)) {
			if (isDisplayedHyperStack())
				setOpenAsHyperStack(true);
			activated = false;
			win = new StackWindow(this, dimensionsChanged?null:getCanvas());   // replaces this window
			if (IJ.isMacro()) { // wait for stack window to be activated
				long start = System.currentTimeMillis();
				while (!activated) {
					IJ.wait(5);
					if ((System.currentTimeMillis()-start)>200)
						break; // 0.2 second timeout
				}
			}
			setPosition(1, 1, 1);
		} else if (newStackSize>1 && invalidDimensions) {
			if (isDisplayedHyperStack())
				setOpenAsHyperStack(true);
			win = new StackWindow(this);   // replaces this window
			setPosition(1, 1, 1);
		} else if (dimensionsChanged || sliderChange) {
			win.updateImage(this);
		} else {
			if (win!=null && win instanceof StackWindow)
				((StackWindow)win).updateSliceSelector();
			if (isComposite()) {
				((CompositeImage)this).reset();
				updateAndDraw();
			}
			repaintWindow();
		}
		if (resetCurrentSlice)
			setSlice(currentSlice);
    }

	public void setStack(ImageStack newStack, int channels, int slices, int frames) {
		if (newStack==null || channels*slices*frames!=newStack.getSize())
			throw new IllegalArgumentException("channels*slices*frames!=stackSize");
		if (IJ.debugMode) IJ.log("setStack: "+newStack.getSize()+" "+channels+" "+slices+" "+frames+" "+isComposite());
		compositeChanges = channels!=this.nChannels;
		this.nChannels = channels;
		this.nSlices = slices;
		this.nFrames = frames;
		setStack(null, newStack);
	}

	private synchronized void setStackNull() {
		if (oneSliceStack && stack!=null && stack.size()>0) {
			String label = stack.getSliceLabel(1);
			setProperty("Label", label);
		}
		stack = null;
		oneSliceStack = false;
	}

	/**	Saves this image's FileInfo so it can be later
		retieved using getOriginalFileInfo(). */
	public void setFileInfo(FileInfo fi) {
		if (fi!=null)
			fi.pixels = null;
		fileInfo = fi;
	}

	/** Returns the ImageWindow that is being used to display
		this image. Returns null if show() has not be called
		or the ImageWindow has been closed. */
	public ImageWindow getWindow() {
		return win;
	}

	/** Returns true if this image is currently being displayed in a window. */
	public boolean isVisible() {
		return win!=null && win.isVisible();
	}

	/** This method should only be called from an ImageWindow. */
	public void setWindow(ImageWindow win) {
		this.win = win;
		if (roi!=null)
			roi.setImage(this);  // update roi's 'ic' field
	}

	/** Returns the ImageCanvas being used to
		display this image, or null. */
	public ImageCanvas getCanvas() {
		return win!=null?win.getCanvas():flatteningCanvas;
	}

	/** Sets current foreground color. */
	public void setColor(Color c) {
		if (ip!=null)
			ip.setColor(c);
	}

	void setupProcessor() {
	}

	public boolean isProcessor() {
		return ip!=null;
	}

	/** Returns a reference to the current ImageProcessor. If there
	    is no ImageProcessor, it creates one. Returns null if this
	    ImagePlus contains no ImageProcessor and no AWT Image.
		Sets the line width to the current line width and sets the
		calibration table if the image is density calibrated. */
	public ImageProcessor getProcessor() {
		if (ip==null)
			return null;
		if (roi!=null && roi.isArea())
			ip.setRoi(roi.getBounds());
		else
			ip.resetRoi();
		if (!compositeImage)
			ip.setLineWidth(Line.getWidth());
		if (ij!=null)
			ip.setProgressBar(ij.getProgressBar());
		Calibration cal = getCalibration();
		if (cal.calibrated())
			ip.setCalibrationTable(cal.getCTable());
		else
			ip.setCalibrationTable(null);
		if (Recorder.record) {
			Recorder recorder = Recorder.getInstance();
			if (recorder!=null) recorder.imageUpdated(this);
		}
		return ip;
	}

	/** Frees RAM by setting the snapshot (undo) buffer in
		the current ImageProcessor to null. */
	public void trimProcessor() {
		ImageProcessor ip2 = ip;
		if (!locked && ip2!=null) {
			if (IJ.debugMode) IJ.log(title + ": trimProcessor");
			Roi roi2 = getRoi();
			if (roi2!=null && roi2.getPasteMode()!=Roi.NOT_PASTING)
				roi2.endPaste();
			ip2.setSnapshotPixels(null);
		}
	}

	/** For images with irregular ROIs, returns a byte mask, otherwise, returns
	 * null. Mask pixels have a non-zero value.and the dimensions of the
	 * mask are equal to the width and height of the ROI.
	 * @see ij.ImagePlus#createRoiMask
	 * @see ij.ImagePlus#createThresholdMask
	*/
	public ImageProcessor getMask() {
		if (roi==null) {
			if (ip!=null) ip.resetRoi();
			return null;
		}
		ImageProcessor mask = roi.getMask();
		if (mask==null)
			return null;
		if (ip!=null && roi!=null) {
			ip.setMask(mask);
			ip.setRoi(roi.getBounds());
		}
		return mask;
	}

	/** Returns an 8-bit binary (foreground=255, background=0)
	 * ROI or overlay mask that has the same dimensions
	 * as this image. Creates an ROI mask If the image has both
	 * both an ROI and an overlay. Set the threshold of the mask to 255.
	 * @see #createThresholdMask
	 * @see ij.gui.Roi#getMask
	*/
	public ByteProcessor createRoiMask() {
		Roi roi2 = getRoi();
		Overlay overlay2 = getOverlay();
		if (roi2==null && overlay2==null)
			throw new IllegalArgumentException("ROI or overlay required");
		ByteProcessor mask = new ByteProcessor(getWidth(),getHeight());
		mask.setColor(255);
		if (roi2!=null)
			mask.fill(roi2);
		else if (overlay2!=null) {
			if (overlay2.size()==1 && (overlay2.get(0) instanceof ImageRoi)) {
				ImageRoi iRoi = (ImageRoi)overlay2.get(0);
				ImageProcessor ip = iRoi.getProcessor();
				if (ip.getWidth()!=mask.getWidth() || ip.getHeight()!=mask.getHeight())
					return mask;
				for (int i=0; i<ip.getPixelCount(); i++) {
					if (ip.get(i)!=0)
						mask.set(i, 255);
				}
			} else {
				for (int i=0; i<overlay2.size(); i++)
					mask.fill(overlay2.get(i));
			}
		}
		mask.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
		return mask;
	}

	/** Returns an 8-bit binary threshold mask
	 * (foreground=255, background=0)
	 * that has the same dimensions as this image.
	 * The threshold of the mask is set to 255.
	 * @see ij.plugin.Thresholder#createMask
	 * @see ij.process.ImageProcessor#createMask
	*/
	public ByteProcessor createThresholdMask() {
		ByteProcessor mask = Thresholder.createMask(this);
		mask.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
		return mask;
	}

	/** Get calibrated statistics for this image or ROI, including
		 histogram, area, mean, min and max, standard
		 deviation and mode.
		This code demonstrates how to get the area, mean
		max and median of the current image or selection:
		<pre>
         imp = IJ.getImage();
         stats = imp.getStatistics();
         IJ.log("Area: "+stats.area);
         IJ.log("Mean: "+stats.mean);
         IJ.log("Max: "+stats.max);
		</pre>
		@return an {@link ij.process.ImageStatistics} object
		@see #getAllStatistics
		@see #getRawStatistics
		@see ij.process.ImageProcessor#getStats
		*/
	public ImageStatistics getStatistics() {
		return getStatistics(AREA+MEAN+STD_DEV+MODE+MIN_MAX+RECT);
	}

	/** This method returns complete calibrated statistics for this
	 * image or ROI (with "Limit to threshold"), but it is up to 70 times
	 * slower than getStatistics().
	 * @return an {@link ij.process.ImageStatistics} object
	 * @see #getStatistics
	 * @see ij.process.ImageProcessor#getStatistics
	*/
	public ImageStatistics getAllStatistics() {
		return getStatistics(ALL_STATS+LIMIT);
	}

	/* Returns uncalibrated statistics for this image or ROI, including
		256 bin histogram, pixelCount, mean, mode, min and max. */
	public ImageStatistics getRawStatistics() {
		if (roi!=null && roi.isArea())
			ip.setRoi(roi);
		else
			ip.resetRoi();
		return ImageStatistics.getStatistics(ip, AREA+MEAN+MODE+MIN_MAX, null);
	}

	/** Returns an ImageStatistics object generated using the
		specified measurement options.
		@see ij.measure.Measurements
	*/
	public ImageStatistics getStatistics(int mOptions) {
		return getStatistics(mOptions, 256, 0.0, 0.0);
	}

	/** Returns an ImageStatistics object generated using the
		specified measurement options and histogram bin count.  */
	public ImageStatistics getStatistics(int mOptions, int nBins) {
		return getStatistics(mOptions, nBins, 0.0, 0.0);
	}

	/** Returns an ImageStatistics object generated using the
		specified measurement options, histogram bin count
		and histogram range. */
	public ImageStatistics getStatistics(int mOptions, int nBins, double histMin, double histMax) {
		ImageProcessor ip2 = ip;
		int bitDepth = getBitDepth();
		if (nBins!=256 && (bitDepth==8||bitDepth==24))
			ip2 =ip.convertToShort(false);
		Roi roi2 = roi;
		if (roi2==null)
			ip2.resetRoi();
		else if (roi2.isArea())
			ip2.setRoi(roi2);
		else if ((roi2 instanceof PointRoi) && roi2.size()==1) {
				// needed to be consistent with ImageProcessor.getStatistics()
				FloatPolygon p = roi2.getFloatPolygon();
				ip2.setRoi((int)p.xpoints[0], (int)p.ypoints[0], 1, 1);
		}
		ip2.setHistogramSize(nBins);
		Calibration cal = getCalibration();
		if (getType()==GRAY16&& !(histMin==0.0&&histMax==0.0)) {
			histMin = cal.getRawValue(histMin);
			histMax=cal.getRawValue(histMax);
		}
		ip2.setHistogramRange(histMin, histMax);
		ImageStatistics stats = ImageStatistics.getStatistics(ip2, mOptions, cal);
		ip2.setHistogramSize(256);
		ip2.setHistogramRange(0.0, 0.0);
		return stats;
	}

	/** Returns the image name. */
	public String getTitle() {
		if (title==null)
			return "";
		else
    		return title;
    }

	/** If the image title is a file name, returns the name
		without the extension and with spaces removed,
		otherwise returns the title shortened to the first space.
	*/	
	public String getShortTitle() {
		String title = getTitle().trim();
		int index = title.lastIndexOf('.');
		boolean fileName = index>0;
		if (fileName) {
			title = title.substring(0, index);
			title = title.replaceAll(" ","");
		} else {
			index = title.indexOf(' ');
			if (index>-1 && !fileName)
				title = title.substring(0, index);
		}
		return title;
    }

	/** Sets the image name. */
	public void setTitle(String title) {
		if (title==null)
			return;
    	if (win!=null) {
    		if (ij!=null)
				Menus.updateWindowMenuItem(this, this.title, title);
			String virtual = stack!=null && stack.isVirtual()?" (V)":"";
			String global = getGlobalCalibration()!=null?" (G)":"";
			String scale = "";
			double magnification = win.getCanvas().getMagnification();
			if (magnification!=1.0) {
				double percent = magnification*100.0;
				int digits = percent>100.0||percent==(int)percent?0:1;
				scale = " (" + IJ.d2s(percent,digits) + "%)";
			}
			win.setTitle(title+virtual+global+scale);
		}
		boolean titleChanged = !title.equals(this.title);
		this.title = title;
		if (titleChanged && listeners.size()>0)
			notifyListeners(UPDATED);
    }

    /** Returns the width of this image in pixels. */
    public int getWidth() {
    	return width;
    }

    /** Returns the height of this image in pixels. */
    public int getHeight() {
    	return height;
    }

    /** Returns the size of this image in bytes. */
    public double getSizeInBytes() {
    	double size = ((double)getWidth()*getHeight()*getStackSize());
		int type = getType();
    	switch (type) {
	    	case ImagePlus.GRAY16: size *= 2.0; break;
	    	case ImagePlus.GRAY32: size *= 4.0; break;
	    	case ImagePlus.COLOR_RGB: size *= 4.0; break;
    	}
    	return size;
	}

	/** If this is a stack, returns the number of slices, else returns 1. */
	public int getStackSize() {
		if (stack==null || oneSliceStack)
			return 1;
		else {
			int slices = stack.size();
			if (slices<=0) slices = 1;
			return slices;
		}
	}

	/** If this is a stack, returns the actual number of images in the stack, else returns 1. */
	public int getImageStackSize() {
		if (stack==null)
			return 1;
		else {
			int slices = stack.size();
			if (slices==0) slices = 1;
			return slices;
		}
	}

	/** Sets the 3rd, 4th and 5th dimensions, where
	<code>nChannels</code>*<code>nSlices</code>*<code>nFrames</code>
	must be equal to the stack size. */
	public void setDimensions(int nChannels, int nSlices, int nFrames) {
		//IJ.log("setDimensions: "+nChannels+" "+nSlices+" "+nFrames+" "+getImageStackSize());
		if (nChannels*nSlices*nFrames!=getImageStackSize() && ip!=null) {
			//throw new IllegalArgumentException("channels*slices*frames!=stackSize");
			nChannels = 1;
			nSlices = getImageStackSize();
			nFrames = 1;
			if (isDisplayedHyperStack()) {
				setOpenAsHyperStack(false);
				new StackWindow(this);
				setSlice(1);
			}
		}
		boolean updateWin = isDisplayedHyperStack() && (this.nChannels!=nChannels||this.nSlices!=nSlices||this.nFrames!=nFrames);
		boolean newSingleImage = win!=null && (win instanceof StackWindow) && nChannels==1&&nSlices==1&&nFrames==1;
		if (newSingleImage) updateWin = true;
		this.nChannels = nChannels;
		this.nSlices = nSlices;
		this.nFrames = nFrames;
		if (updateWin) {
			if (nSlices!=getImageStackSize())
				setOpenAsHyperStack(true);
			ip = null;
			img = null;
			setPositionWithoutUpdate(getChannel(), getSlice(), getFrame());
			if (isComposite()) ((CompositeImage)this).reset();
			new StackWindow(this);
		}
		dimensionsSet = true;
	}

	/** Returns 'true' if this image is a hyperstack. */
	public boolean isHyperStack() {
		return isDisplayedHyperStack() || (openAsHyperStack&&getNDimensions()>3);
	}

	/** Returns the number of dimensions (2, 3, 4 or 5). */
	public int getNDimensions() {
		int dimensions = 2;
		int[] dim = getDimensions(true);
		if (dim[2]>1) dimensions++;
		if (dim[3]>1) dimensions++;
		if (dim[4]>1) dimensions++;
		return dimensions;
	}

	/** Returns 'true' if this is a hyperstack currently being displayed in a StackWindow. */
	public boolean isDisplayedHyperStack() {
		return win!=null && win instanceof StackWindow && ((StackWindow)win).isHyperStack();
	}

	/** Returns the number of channels. */
	public int getNChannels() {
		verifyDimensions();
		return nChannels;
	}

	/** Returns the image depth (number of z-slices). */
	public int getNSlices() {
		verifyDimensions();
		return nSlices;
	}

	/** Returns the number of frames (time-points). */
	public int getNFrames() {
		verifyDimensions();
		return nFrames;
	}

	/** Returns the dimensions of this image (width, height, nChannels,
		nSlices, nFrames) as a 5 element int array. */
	public int[] getDimensions() {
		return getDimensions(true);
	}

	public int[] getDimensions(boolean varify) {
		if (varify)
			verifyDimensions();
		int[] d = new int[5];
		d[0] = width;
		d[1] = height;
		d[2] = nChannels;
		d[3] = nSlices;
		d[4] = nFrames;
		return d;
	}

	void verifyDimensions() {
		int stackSize = getImageStackSize();
		if (nSlices==1) {
			if (nChannels>1 && nFrames==1)
				nChannels = stackSize;
			else if (nFrames>1 && nChannels==1)
				nFrames = stackSize;
		}
		if (nChannels*nSlices*nFrames!=stackSize) {
			nSlices = stackSize;
			nChannels = 1;
			nFrames = 1;
		}
	}

	/** Returns the current image type (ImagePlus.GRAY8, ImagePlus.GRAY16,
		ImagePlus.GRAY32, ImagePlus.COLOR_256 or ImagePlus.COLOR_RGB).
		@see #getBitDepth
	*/
    public int getType() {
    	return imageType;
    }

    /** Returns the bit depth, 8, 16, 24 (RGB) or 32, or 0 if the bit depth
    	is unknown. RGB images actually use 32 bits per pixel. */
    public int getBitDepth() {
    	ImageProcessor ip2 = ip;
    	if (ip2==null) {
			int bitDepth = 0;
			switch (imageType) {
				case GRAY8: bitDepth=typeSet?8:0; break;
				case COLOR_256: bitDepth=8; break;
				case GRAY16: bitDepth=16; break;
				case GRAY32: bitDepth=32; break;
				case COLOR_RGB: bitDepth=24; break;
			}
			return bitDepth;
    	}
    	if (ip2 instanceof ByteProcessor)
    		return 8;
    	else if (ip2 instanceof ShortProcessor)
    		return 16;
    	else if (ip2 instanceof ColorProcessor)
    		return 24;
      	else if (ip2 instanceof FloatProcessor)
    		return 32;
    	return 0;
    }

    /** Returns the number of bytes per pixel. */
    public int getBytesPerPixel() {
    	switch (imageType) {
	    	case GRAY16: return 2;
	    	case GRAY32: case COLOR_RGB: return 4;
	    	default: return 1;
    	}
	}

	protected void setType(int type) {
		if ((type<0) || (type>COLOR_RGB))
			return;
		int previousType = imageType;
		imageType = type;
		if (imageType!=previousType) {
			if (win!=null)
				Menus.updateMenus();
			getLocalCalibration().setImage(this);
		}
		typeSet = true;
	}
	
	public void setTypeToColor256() {
		if (imageType==ImagePlus.GRAY8) {
			ImageProcessor ip2 = getProcessor();
			if (ip2!=null && ip2.getMinThreshold()==ImageProcessor.NO_THRESHOLD && ip2.isColorLut() && !ip2.isPseudoColorLut()) {
				imageType = COLOR_256;
				typeSet = true;
			}
		}
	}
	

 	/** Returns the string value from the "Info" property string
	 * associated with 'key', or null if the key is not found.
	 * Works with DICOM tags and Bio-Formats metadata.
	 * @see #getNumericProperty
	 * @see #getInfoProperty
	 * @see #getProp
	 * @see #setProp
	*/
	public String getStringProperty(String key) {
		if (key==null)
			return null;
		if (isDicomTag(key))
			return DicomTools.getTag(this, key);
		if (getStackSize()>1) {
			ImageStack stack2 = getStack();
			String label = stack2.getSliceLabel(getCurrentSlice());
			if (label!=null && label.indexOf('\n')>0) {
				String value = getStringProperty(key, label);
				if (value!=null)
					return value;
			}
		}
		Object obj = getProperty("Info");
		if (obj==null || !(obj instanceof String))
			return null;
		String info = (String)obj;
		return getStringProperty(key, info);
	}

	private boolean isDicomTag(String key) {
		if (key.length()!=9 || key.charAt(4)!=',')
			return false;
		key = key.toLowerCase();
		for (int i=0; i<9; i++) {
			char c = i!=4?key.charAt(i):'0';
			if (!(Character.isDigit(c)||(c=='a'||c=='b'||c=='c'||c=='d'||c=='e'||c=='f')))
				return false;
		}
		return true;
	}

	/** Returns the numeric value from the "Info" property string
	 * associated with 'key', or NaN if the key is not found or the
	 * value associated with the key is not numeric. Works with
	 * DICOM tags and Bio-Formats metadata.
	 * @see #getStringProperty
	 * @see #getInfoProperty
	*/
	public double getNumericProperty(String key) {
		return Tools.parseDouble(getStringProperty(key));
	}

	private String getStringProperty(String key, String info) {
		int index1 = -1;
		index1 = findKey(info, key+": "); // standard 'key: value' pair?
		if (index1<0) // Bio-Formats metadata?
			index1 = findKey(info, key+" = ");
		if (index1<0) // '=' with no spaces
			index1 = findKey(info, key+"=");
		if (index1<0) // otherwise not found
			return null;
		if (index1==info.length())
			return ""; //empty value at the end
		int index2 = info.indexOf("\n", index1);
		if (index2==-1)
			index2=info.length();
		String value = info.substring(index1, index2);
		return value;
	}

	/** Find a key in a String (words merely ending with 'key' don't qualify).
	* @return index of first character after the key, or -1 if not found
	*/
	private int findKey(String s, String key) {
		int i = s.indexOf(key);
		if (i<0)
			return -1; //key not found
		while (i>0 && Character.isLetterOrDigit(s.charAt(i-1)))
			i = s.indexOf(key, i+key.length());
		if (i>=0)
			return i + key.length();
		else
			return -1;
	}

	/** Adds a key-value pair to this image's string properties.
	 * The key-value pair is removed if 'value' is null. The 
	 * properties persist if the image is saved in TIFF format.
	*/
	public void setProp(String key, String value) {
		if (key==null)
			return;
		if (imageProperties==null)
			imageProperties = new Properties();
		if (value==null || value.length()==0)
			imageProperties.remove(key);
		else
			imageProperties.setProperty(key, value);
	}
	
	/** Saves a persistent numeric propery. The property is
	 *  removed if 'value' is NaN.
	 * @see #getNumericProp
	*/
	public void setProp(String key, double value) {
		setProp(key, Double.isNaN(value)?null:""+value);
	}

	/** Returns the string property associated with the specified key
	 * or null if the property is not found.
	 * @see #setProp
	 * @see #getNumericProp
	*/
	public String getProp(String key) {
		if (imageProperties==null)
			return null;
		else
			return imageProperties.getProperty(key);
	}
	
	/** Returns the numeric property associated with the specified key
	 * or NaN if the property is not found.
	 * @see #setProp(String,double)
	 * @see #getProp
	*/
	public double getNumericProp(String key) {
		if (imageProperties==null)
			return Double.NaN;
		else
			return Tools.parseDouble(getProp(key), Double.NaN);
	}

	/** Used for saving string properties in TIFF header. */
	public String[] getPropertiesAsArray() {
		if (imageProperties==null || imageProperties.size()==0)
			return null;
		String props[] = new String[imageProperties.size()*2];
		int index = 0;
		for (Enumeration en=imageProperties.keys(); en.hasMoreElements();) {
			String key = (String)en.nextElement();
			String value = imageProperties.getProperty(key);
			props[index++] = key;
			props[index++] = value;
		}
		return props;
	}
	
	/** Returns information displayed by Image/Show Info command. */
	public String getPropsInfo() {
		if (imageProperties==null || imageProperties.size()==0)
			return "0";
		String info2 = "";
		for (Enumeration en=imageProperties.keys(); en.hasMoreElements();) {
			String key = (String)en.nextElement();
			if (info2.length()>50) {
				info2 += "...";
				break;
			} else
				info2 += " " + key;
		}
		if (info2.length()>1)
			info2 = " (" + info2.substring(1) + ")";
		return imageProperties.size() + info2;			
	}
	
	/** Used for restoring string properties from TIFF header. */
	public void setProperties(String[] props) {
		if (props==null)
			return;
		//IJ.log("setProperties: "+props.length+" "+getTitle());
		this.imageProperties = null;
		int equalsIndex = props[0].indexOf("=");
		if (equalsIndex>0 && equalsIndex<50) { // v1.53a3 format
			for (int i=0; i<props.length; i++) {
				int index = props[i].indexOf("=");
				if (index==-1) continue;
				String key = props[i].substring(0,index);
				String value = props[i].substring(index+1);
				setProp(key,value);
			}
		} else {
			for (int i=0; i<props.length; i+=2) {
				String key = props[i];
				String value = props[i+1];
				//IJ.log("   "+key+" "+value.length());
				setProp(key,value);
			}
		}
	}

	/** Returns the "Info" property string, or null if it is not found.
	 * @see #getProp
	 * @see #setProp
	*/
	public String getInfoProperty() {
		String info = null;
		Object obj = getProperty("Info");
		if (obj!=null && (obj instanceof String)) {
			info = (String)obj;
			if (info.length()==0)
				info = null;
		}
		return info;
	}

	/** Returns the property associated with 'key', or null if it is not found.
	 * @see #getProp
	 * @see #setProp
	 * @see #getStringProperty
	 * @see #getNumericProperty
	 * @see #getInfoProperty
	*/
	public Object getProperty(String key) {
		if (properties==null)
			return null;
		else
			return properties.get(key);
	}

	/** Adds a key-value pair to this image's properties. The key
	 * is removed from the properties table if value is null.
	 * @see #getProp
	 * @see #setProp
	*/
	public void setProperty(String key, Object value) {
		if (properties==null)
			properties = new Properties();
		if (value==null)
			properties.remove(key);
		else
			properties.put(key, value);
	}

	/** Returns this image's Properties. May return null. */
	public Properties getProperties() {
			return properties;
	}
	/** Creates a LookUpTable object that corresponds to this image. */
    public LookUpTable createLut() {
		ImageProcessor ip2 = getProcessor();
		if (ip2!=null)
			return new LookUpTable(ip2.getColorModel());
		else
			return new LookUpTable(LookUpTable.createGrayscaleColorModel(false));
	}

	/** Returns true is this image uses an inverting LUT that
		displays zero as white and 255 as black. */
	public boolean isInvertedLut() {
		return ip!=null && ip.isInvertedLut();
	}

	private int[] pvalue = new int[4];

	/**
	Returns the pixel value at (x,y) as a 4 element array. Grayscale values
	are retuned in the first element. RGB values are returned in the first
	3 elements. For indexed color images, the RGB values are returned in the
	first 3 three elements and the index (0-255) is returned in the last.
	*/
	public int[] getPixel(int x, int y) {
		pvalue[0]=pvalue[1]=pvalue[2]=pvalue[3]=0;
		switch (imageType) {
			case GRAY8: case COLOR_256:
				int index;
				if (ip!=null)
					index = ip.getPixel(x, y);
				else {
					byte[] pixels8;
					if (img==null) return pvalue;
					PixelGrabber pg = new PixelGrabber(img,x,y,1,1,false);
					try {pg.grabPixels();}
					catch (InterruptedException e){return pvalue;};
					pixels8 = (byte[])(pg.getPixels());
					index = pixels8!=null?pixels8[0]&0xff:0;
				}
				if (imageType!=COLOR_256) {
					pvalue[0] = index;
					return pvalue;
				}
				pvalue[3] = index;
				// fall through to get rgb values
			case COLOR_RGB:
				int c = 0;
				if (imageType==COLOR_RGB && ip!=null)
					c = ip.getPixel(x, y);
				else {
					int[] pixels32 = new int[1];
					if (img==null) return pvalue;
					PixelGrabber pg = new PixelGrabber(img, x, y, 1, 1, pixels32, 0, width);
					try {pg.grabPixels();}
					catch (InterruptedException e) {return pvalue;};
					c = pixels32[0];
				}
				int r = (c&0xff0000)>>16;
				int g = (c&0xff00)>>8;
				int b = c&0xff;
				pvalue[0] = r;
				pvalue[1] = g;
				pvalue[2] = b;
				break;
			case GRAY16: case GRAY32:
				if (ip!=null) pvalue[0] = ip.getPixel(x, y);
				break;
		}
		return pvalue;
	}

	/** Returns an empty image stack that has the same
		width, height and color table as this image. */
	public ImageStack createEmptyStack() {
		ColorModel cm;
		if (ip!=null)
			cm = ip.getColorModel();
		else
			cm = createLut().getColorModel();
		return new ImageStack(width, height, cm);
	}

	/** Returns the image stack. The stack may have only
		one slice. After adding or removing slices, call
		<code>setStack()</code> to update the image and
		the window that is displaying it.
		@see #setStack
	*/
	public ImageStack getStack() {
		ImageStack s;
		if (stack==null) {
			s = createEmptyStack();
			ImageProcessor ip2 = getProcessor();
			if (ip2==null)
				return s;
			String label = (String)getProperty("Label");
			if (label==null) {
				String info = (String)getProperty("Info");
				label = info!=null?getTitle()+"\n"+info:null; // DICOM metadata
			}
			s.addSlice(label, ip2);
			s.update(ip2);
			this.stack = s;
			ip = ip2;
			oneSliceStack = true;
			setCurrentSlice(1);
		} else {
			s = stack;
			if (ip!=null) {
				Calibration cal = getCalibration();
				if (cal.calibrated())
					ip.setCalibrationTable(cal.getCTable());
				else
					ip.setCalibrationTable(null);
			}
			s.update(ip);
		}
		if (roi!=null)
			s.setRoi(roi.getBounds());
		else
			s.setRoi(null);
		return s;
	}

	/** Returns the base image stack. */
	public ImageStack getImageStack() {
		if (stack==null)
			return getStack();
		else {
			stack.update(ip);
			return stack;
		}
	}

	/** Returns the current stack index (one-based) or 1 if this is a single image. */
	public int getCurrentSlice() {
		if (currentSlice<1) setCurrentSlice(1);
		if (currentSlice>getStackSize())
			setCurrentSlice(getStackSize());
		return currentSlice;
	}

	final void setCurrentSlice(int slice) {
		currentSlice = slice;
		int stackSize = getStackSize();
		if (nChannels==stackSize) updatePosition(currentSlice, 1, 1);
		if (nSlices==stackSize) updatePosition(1, currentSlice, 1);
		if (nFrames==stackSize) updatePosition(1, 1, currentSlice);
	}

	public int getChannel() {
		return position[0];
	}

	public int getSlice() {
		return position[1];
	}

	public int getFrame() {
		return position[2];
	}

	public void killStack() {
		setStackNull();
		trimProcessor();
	}

	/** Sets the current hyperstack position and updates the display,
		where 'channel', 'slice' and 'frame' are one-based indexes. */
	public void setPosition(int channel, int slice, int frame) {
		//IJ.log("setPosition: "+channel+"  "+slice+"  "+frame+"  "+noUpdateMode);
		verifyDimensions();
		if (channel<0) channel=0;
		if (slice<0) slice=0;
		if (frame<0) frame=0;
		if (channel==0) channel=getC();
		if (slice==0) slice=getZ();
		if (frame==0) frame=getT();
		if (channel>nChannels) channel=nChannels;
		if (slice>nSlices) slice=nSlices;
		if (frame>nFrames) frame=nFrames;
		if (isDisplayedHyperStack())
			((StackWindow)win).setPosition(channel, slice, frame);
		else {
			boolean channelChanged = channel!=getChannel();
			setSlice((frame-1)*nChannels*nSlices + (slice-1)*nChannels + channel);
			updatePosition(channel, slice, frame);
			if (channelChanged && isComposite() && !noUpdateMode)
				updateImage();
		}
	}

	/** Sets the current hyperstack position without updating the display,
		where 'channel', 'slice' and 'frame' are one-based indexes. */
	public void setPositionWithoutUpdate(int channel, int slice, int frame) {
		noUpdateMode = true;
		setPosition(channel, slice, frame);
		noUpdateMode = false;
	}

	/** Sets the hyperstack channel position (one based). */
	public void setC(int channel) {
		setPosition(channel, getZ(), getT());
	}

	/** Sets the hyperstack slice position (one based). */
	public void setZ(int slice) {
		setPosition(getC(), slice, getT());
	}

	/** Sets the hyperstack frame position (one based). */
	public void setT(int frame) {
		setPosition(getC(), getZ(), frame);
	}

	/** Returns the current hyperstack channel position. */
	public int getC() {
		return position[0];
	}

	/** Returns the current hyperstack slice position. */
	public int getZ() {
		return position[1];
	}

	/** Returns the current hyperstack frame position. */
	public int getT() {
		return position[2];
	}

	/** Returns that stack index (one-based) corresponding to the specified position. */
	public int getStackIndex(int channel, int slice, int frame) {
   		if (channel<1) channel = 1;
    	if (channel>nChannels) channel = nChannels;
    	if (slice<1) slice = 1;
    	if (slice>nSlices) slice = nSlices;
    	if (frame<1) frame = 1;
    	if (frame>nFrames) frame = nFrames;
		return (frame-1)*nChannels*nSlices + (slice-1)*nChannels + channel;
	}

	/* Hack needed to make the HyperStackReducer work. */
	public void resetStack() {
		if (currentSlice==1 && stack!=null && stack.size()>0) {
			ColorModel cm = ip.getColorModel();
			double min = ip.getMin();
			double max = ip.getMax();
			ImageProcessor ip2 = stack.getProcessor(1);
			if (ip2!=null) {
				ip = ip2;
				ip.setColorModel(cm);
				ip.setMinAndMax(min, max);
			}
		}
	}

	/** Set the current hyperstack position based on the stack index 'n' (one-based). */
	public void setPosition(int n) {
		int[] pos = convertIndexToPosition(n);
		setPosition(pos[0], pos[1], pos[2]);
	}

	/** Converts the stack index 'n' (one-based) into a hyperstack position (channel, slice, frame). */
	public int[] convertIndexToPosition(int n) {
		if (n<1 || n>getStackSize())
			throw new IllegalArgumentException("n out of range: "+n);
		int[] position = new int[3];
		int[] dim = getDimensions();
		position[0] = ((n-1)%dim[2])+1;
		position[1] = (((n-1)/dim[2])%dim[3])+1;
		position[2] = (((n-1)/(dim[2]*dim[3]))%dim[4])+1;
		return position;
	}

	/** Displays the specified stack image, where 1<=n<=stackSize.
	 * Does nothing if this image is not a stack.
	 * @see #setPosition
	 * @see #setC
	 * @see #setZ
	 * @see #setT
	 */
	public synchronized void setSlice(int n) {
		if (stack==null || (n==currentSlice&&ip!=null)) {
			if (!noUpdateMode)
				updateAndRepaintWindow();
			return;
		}
		if (n>=1 && n<=stack.size()) {
			Roi roi = getRoi();
			if (roi!=null)
				roi.endPaste();
			if (isProcessor()) {
				if (currentSlice==0) currentSlice=1;
				stack.setPixels(ip.getPixels(),currentSlice);
			}
			setCurrentSlice(n);
			Object pixels = null;
			Overlay overlay2 = null;
			if (stack.isVirtual() && !((stack instanceof FileInfoVirtualStack)||(stack instanceof AVI_Reader))) {
				ImageProcessor ip2 = stack.getProcessor(currentSlice);
				overlay2 = ip2!=null?ip2.getOverlay():null;
				if (overlay2!=null)
					setOverlay(overlay2);
				if (stack instanceof VirtualStack) {
					Properties props = ((VirtualStack)stack).getProperties();
					if (props!=null)
						setProperty("FHT", props.get("FHT"));
				}
				if (ip2!=null) pixels=ip2.getPixels();
			} else
				pixels = stack.getPixels(currentSlice);
			if (ip!=null && pixels!=null) {
				try {
					ip.setPixels(pixels);
					ip.setSnapshotPixels(null);
				} catch(Exception e) {}
			} else {
				ImageProcessor ip2 = stack.getProcessor(n);
				if (ip2!=null) ip = ip2;
			}
			if (compositeImage && getCompositeMode()==IJ.COMPOSITE && ip!=null) {
				int channel = getC();
				if (channel>0 && channel<=getNChannels())
					ip.setLut(((CompositeImage)this).getChannelLut(channel));
			}
			if (win!=null && win instanceof StackWindow)
				((StackWindow)win).updateSliceSelector();
			if (Prefs.autoContrast && nChannels==1 && imageType!=COLOR_RGB) {
				(new ContrastEnhancer()).stretchHistogram(ip,0.35,ip.getStats());
				ContrastAdjuster.update();
				//IJ.showStatus(n+": min="+ip.getMin()+", max="+ip.getMax());
			}
			if (imageType==COLOR_RGB)
				ContrastAdjuster.update();
			else if (imageType==GRAY16 || imageType==GRAY32)
				ThresholdAdjuster.update();
			if (!noUpdateMode)
				updateAndRepaintWindow();
			else
				img = null;
		}
	}

	/** Displays the specified stack image (1<=n<=stackSize)
		without updating the display. */
	public void setSliceWithoutUpdate(int n) {
		noUpdateMode = true;
		setSlice(n);
		noUpdateMode = false;
	}

	/** Returns the current selection, or null if there is no selection. */
	public Roi getRoi() {
		return roi;
	}

	/** Assigns the specified ROI to this image and displays it. Any existing
		ROI is deleted if <code>roi</code> is null or its width or height is zero. */
	public void setRoi(Roi newRoi) {
		setRoi(newRoi, true);
	}

	/** Assigns 'newRoi'  to this image and displays it if 'updateDisplay' is true. */
	public void setRoi(Roi newRoi, boolean updateDisplay) {
		if (newRoi==null) {
			deleteRoi();
			return;
		}
		if (Recorder.record) {
			Recorder recorder = Recorder.getInstance();
			if (recorder!=null) recorder.imageUpdated(this);
		}
		Rectangle bounds = newRoi.getBounds();
		if (newRoi.isVisible()) {
			if ((newRoi instanceof Arrow) && newRoi.getState()==Roi.CONSTRUCTING && bounds.width==0 && bounds.height==0) {
				deleteRoi();
				roi = newRoi;
				return;
			}
			newRoi = (Roi)newRoi.clone();
			if (newRoi==null)
				{deleteRoi(); return;}
		}
		if (bounds.width==0 && bounds.height==0 && !(newRoi.getType()==Roi.POINT||newRoi.getType()==Roi.LINE)) {
			deleteRoi();
			return;
		}
		roi = newRoi;
		if (ip!=null) {
			ip.setMask(null);
			if (roi.isArea())
				ip.setRoi(bounds);
			else
				ip.resetRoi();
		}
		roi.setImage(this);
		if ((roi instanceof PointRoi) && ((PointRoi)roi).addToOverlay()) {
			IJ.run(this, "Add Selection...", "");
			roi = null;
			return;
		}
		if (updateDisplay)
			draw();
		if (roi!=null)
			roi.notifyListeners(RoiListener.CREATED);
	}

	/** Creates a rectangular selection. */
	public void setRoi(int x, int y, int width, int height) {
		setRoi(new Rectangle(x, y, width, height));
	}

	/** Creates a rectangular selection. */
	public void setRoi(Rectangle r) {
		setRoi(new Roi(r.x, r.y, r.width, r.height));
	}

	/** Starts the process of creating a new selection, where sx and sy are the
		starting screen coordinates. The selection type is determined by which tool in
		the tool bar is active. The user interactively sets the selection size and shape. */
	public void createNewRoi(int sx, int sy) {
		Roi previousRoi = roi;
		deleteRoi();   //also saves the roi as <code>Roi.previousRoi</code> if non-null
		if (Roi.previousRoi != null)
			Roi.previousRoi.setImage(previousRoi== null ? null : this); //with 'this' it will be recalled in case of ESC

		switch (Toolbar.getToolId()) {
			case Toolbar.RECTANGLE:
				if (Toolbar.getRectToolType()==Toolbar.ROTATED_RECT_ROI)
					roi = new RotatedRectRoi(sx, sy, this);
				else
					roi = new Roi(sx, sy, this, Toolbar.getRoundRectArcSize());
				break;
			case Toolbar.OVAL:
				if (Toolbar.getOvalToolType()==Toolbar.ELLIPSE_ROI)
					roi = new EllipseRoi(sx, sy, this);
				else
					roi = new OvalRoi(sx, sy, this);
				break;
			case Toolbar.POLYGON:
			case Toolbar.POLYLINE:
			case Toolbar.ANGLE:
				roi = new PolygonRoi(sx, sy, this);
				break;
			case Toolbar.FREEROI:
			case Toolbar.FREELINE:
				roi = new FreehandRoi(sx, sy, this);
				break;
			case Toolbar.LINE:
				if ("arrow".equals(Toolbar.getToolName()))
					roi = new Arrow(sx, sy, this);
				else
					roi = new Line(sx, sy, this);
				break;
			case Toolbar.TEXT:
				roi = new TextRoi(sx, sy, this);
				((TextRoi)roi).setPreviousRoi(previousRoi);
				break;
			case Toolbar.POINT:
				roi = new PointRoi(sx, sy, this);
				if (Prefs.pointAddToOverlay) {
					int measurements = Analyzer.getMeasurements();
					if (!(Prefs.pointAutoMeasure && (measurements&Measurements.ADD_TO_OVERLAY)!=0))
						IJ.run(this, "Add Selection...", "");
					Overlay overlay2 = getOverlay();
					if (overlay2!=null)
						overlay2.drawLabels(!Prefs.noPointLabels);
					Prefs.pointAddToManager = false;
				}
				if (Prefs.pointAutoMeasure || (Prefs.pointAutoNextSlice&&!Prefs.pointAddToManager))
					IJ.run(this, "Measure", "");
				if (Prefs.pointAddToManager) {
					IJ.run(this, "Add to Manager ", "");
					ImageCanvas ic = getCanvas();
					if (ic!=null) {
						RoiManager rm = RoiManager.getInstance();
						if (rm!=null) {
							if (Prefs.noPointLabels)
								rm.runCommand("show all without labels");
							else
								rm.runCommand("show all with labels");
						}
					}
				}
				if (Prefs.pointAutoNextSlice && getStackSize()>1) {
					IJ.run(this, "Next Slice [>]", "");
					deleteRoi();
				}
				break;
		}
		if (roi!=null)
			roi.notifyListeners(RoiListener.CREATED);
	}

	/** Deletes the current region of interest. Makes a copy of the ROI
		so it can be recovered by Edit/Selection/Restore Selection. */
	public void deleteRoi() {
		if (roi!=null) {
			saveRoi();
			if (!(IJ.altKeyDown()||IJ.shiftKeyDown())) {
				RoiManager rm = RoiManager.getRawInstance();
				if (rm!=null)
					rm.deselect(roi);
			}
			if (roi!=null) {
				roi.notifyListeners(RoiListener.DELETED);
				if (roi instanceof PointRoi)
					((PointRoi)roi).resetCounters();
			}
			roi = null;
			if (ip!=null)
				ip.resetRoi();
			draw();
		}
	}

	public boolean okToDeleteRoi() {
		if (roi!=null && (roi instanceof PointRoi) && getWindow()!=null && ((PointRoi)roi).promptBeforeDeleting()) {
			int npoints = ((PolygonRoi)roi).getNCoordinates();
			int counters = ((PointRoi)roi).getNCounters();
			String msg = "Delete this multi-point selection ("+npoints+" points, "+counters+" counter"+(counters>1?"s":"")+")?";
			GenericDialog gd=new GenericDialog("Delete Points?");
			gd.addMessage(msg+"\nRestore using Edit>Selection>Restore Selection.");
			gd.addHelp(PointToolOptions.help);
			gd.setOKLabel("Keep");
			gd.setCancelLabel("Delete");
			gd.showDialog();
			if (gd.wasOKed())
				return false;
		}
		return true;
	}

	/** Deletes the current region of interest. */
	public void killRoi() {
		deleteRoi();
	}

	public void saveRoi() {
		Roi roi2 = roi;
		if (roi2!=null) {
			roi2.endPaste();
			Rectangle r = roi2.getBounds();
			if ((r.width>0 || r.height>0)) {
				Roi.previousRoi = (Roi)roi2.clone();
				if (IJ.debugMode) IJ.log("saveRoi: "+roi2);
			}
			if ((roi2 instanceof PointRoi) && ((PointRoi)roi2).promptBeforeDeleting()) {
				PointRoi.savedPoints = (PointRoi)roi2.clone();
				if (IJ.debugMode) IJ.log("saveRoi: saving multi-point selection");
			}
		}
	}

	public void restoreRoi() {
		if (Toolbar.getToolId()==Toolbar.POINT && PointRoi.savedPoints!=null) {
			roi = (Roi)PointRoi.savedPoints.clone();
			draw();
			roi.notifyListeners(RoiListener.MODIFIED);
			return;
		}
		if (Roi.previousRoi!=null) {
			Roi pRoi = Roi.previousRoi;
			Rectangle r = pRoi.getBounds();
			if (r.width<=width||r.height<=height||(r.x<width&&r.y<height)||isSmaller(pRoi)) { // will it (mostly) fit in this image?
				roi = (Roi)pRoi.clone();
				roi.setImage(this);
				if (r.x>=width || r.y>=height || (r.x+r.width)<0 || (r.y+r.height)<0) // does it need to be moved?
					roi.setLocation((width-r.width)/2, (height-r.height)/2);
				else if (r.width==width && r.height==height) // is it the same size as the image
					roi.setLocation(0, 0);
				draw();
				roi.notifyListeners(RoiListener.MODIFIED);
			}
		}
	}

	boolean isSmaller(Roi r) {
		ImageProcessor mask = r.getMask();
		if (mask==null) return false;
		mask.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
		ImageStatistics stats = ImageStatistics.getStatistics(mask, MEAN+LIMIT, null);
		return stats.area<=width*height;
	}

	/** Implements the File/Revert command. */
	public void revert() {
		if (getStackSize()>1 && getStack().isVirtual()) {
			int thisSlice = currentSlice;
			currentSlice = 0;
			setSlice(thisSlice);
			return;
		}
		FileInfo fi = getOriginalFileInfo();
		boolean isFileInfo = fi!=null && fi.fileFormat!=FileInfo.UNKNOWN;
		if (!isFileInfo && url==null)
			return;
		if (fi.directory==null && url==null)
			return;
		if (ij!=null && changes && isFileInfo && !Interpreter.isBatchMode() && !IJ.isMacro() && !IJ.altKeyDown()) {
			if (!IJ.showMessageWithCancel("Revert?", "Revert to saved version of\n\""+getTitle()+"\"?"))
				return;
		}
		Roi saveRoi = null;
		if (roi!=null) {
			roi.endPaste();
			saveRoi = (Roi)roi.clone();
		}
		trimProcessor();
		new FileOpener(fi).revertToSaved(this);
		if (Prefs.useInvertingLut && getBitDepth()==8 && ip!=null && !ip.isInvertedLut()&& !ip.isColorLut())
			invertLookupTable();
		if (getProperty("FHT")!=null) {
			properties.remove("FHT");
			if (getTitle().startsWith("FFT of "))
				setTitle(getTitle().substring(7));
		}
		ContrastAdjuster.update();
		if (saveRoi!=null) setRoi(saveRoi);
		repaintWindow();
		IJ.showStatus("");
		changes = false;
		notifyListeners(UPDATED);
    }

	void revertStack(FileInfo fi) {
		String path = null;
		String url2 = null;
		if (url!=null && !url.equals("")) {
			path = url;
			url2 = url;
		} else if (fi!=null && !((fi.directory==null||fi.directory.equals("")))) {
			path = fi.getFilePath();
		} else if (fi!=null && fi.url!=null && !fi.url.equals("")) {
			path = fi.url;
			url2 = fi.url;
		} else
			return;
		IJ.showStatus("Loading: " + path);
		ImagePlus imp = IJ.openImage(path);
		if (imp!=null) {
			int n = imp.getStackSize();
			int c = imp.getNChannels();
			int z = imp.getNSlices();
			int t = imp.getNFrames();
			if (z==n || t==n || (c==getNChannels()&&z==getNSlices()&&t==getNFrames())) {
				setCalibration(imp.getCalibration());
				setStack(imp.getStack(), c, z, t);
			} else {
				ImageWindow win = getWindow();
				Point loc = null;
				if (win!=null) loc = win.getLocation();
				changes = false;
				close();
				FileInfo fi2 = imp.getOriginalFileInfo();
				if (fi2!=null && (fi2.url==null || fi2.url.length()==0)) {
					fi2.url = url2;
					imp.setFileInfo(fi2);
				}
				ImageWindow.setNextLocation(loc);
				imp.show();
			}
		}
	}

    /** Returns a FileInfo object containing information, including the
		pixel array, needed to save this image. Use getOriginalFileInfo()
		to get a copy of the FileInfo object used to open the image.
		@see ij.io.FileInfo
		@see #getOriginalFileInfo
		@see #setFileInfo
	*/
    public FileInfo getFileInfo() {
    	FileInfo fi = new FileInfo();
    	fi.width = width;
    	fi.height = height;
    	fi.nImages = getStackSize();
    	if (compositeImage)
    		fi.nImages = getImageStackSize();
    	fi.whiteIsZero = isInvertedLut();
		fi.intelByteOrder = false;
		if (fi.nImages==1 && ip!=null)
			fi.pixels = ip.getPixels();
		else if (stack!=null)
			fi.pixels = stack.getImageArray();
		Calibration cal = getCalibration();
    	if (cal.scaled()) {
    		fi.pixelWidth = cal.pixelWidth;
    		fi.pixelHeight = cal.pixelHeight;
   			fi.unit = cal.getUnit();
    	}
    	if (fi.nImages>1)
     		fi.pixelDepth = cal.pixelDepth;
   		fi.frameInterval = cal.frameInterval;
    	if (cal.calibrated()) {
    		fi.calibrationFunction = cal.getFunction();
     		fi.coefficients = cal.getCoefficients();
			fi.valueUnit = cal.getValueUnit();
		} else if (!Calibration.DEFAULT_VALUE_UNIT.equals(cal.getValueUnit()))
			fi.valueUnit = cal.getValueUnit();

    	switch (imageType) {
			case GRAY8: case COLOR_256:
				LookUpTable lut = createLut();
				boolean customLut = !lut.isGrayscale() || (ip!=null&&!ip.isDefaultLut());
				if (imageType==COLOR_256 || customLut)
					fi.fileType = FileInfo.COLOR8;
				else
					fi.fileType = FileInfo.GRAY8;
				addLut(lut, fi);
				break;
	    	case GRAY16:
	    		if (compositeImage && fi.nImages==3) {
	    			if ("Red".equals(getStack().getSliceLabel(1)))
						fi.fileType = fi.RGB48;
					else
						fi.fileType = fi.GRAY16_UNSIGNED;
				} else
					fi.fileType = fi.GRAY16_UNSIGNED;
				if (!compositeImage) {
    				lut = createLut();
    				if (!lut.isGrayscale() || (ip!=null&&!ip.isDefaultLut()))
    					addLut(lut, fi);
				}
				break;
	    	case GRAY32:
				fi.fileType = fi.GRAY32_FLOAT;
				if (!compositeImage) {
    				lut = createLut();
    				if (!lut.isGrayscale() || (ip!=null&&!ip.isDefaultLut()))
    					addLut(lut, fi);
				}
				break;
	    	case COLOR_RGB:
				fi.fileType = fi.RGB;
				break;
			default:
    	}
    	return fi;
    }

	private void addLut(LookUpTable lut, FileInfo fi) {
		fi.lutSize = lut.getMapSize();
		fi.reds = lut.getReds();
		fi.greens = lut.getGreens();
		fi.blues = lut.getBlues();
	}

    /** Returns the FileInfo object that was used to open this image.
    	Returns null for images created using the File/New command.
		@see ij.io.FileInfo
		@see #getFileInfo
	*/
    public FileInfo getOriginalFileInfo() {
    	if (fileInfo==null & url!=null) {
    		fileInfo = new FileInfo();
    		fileInfo.width = width;
    		fileInfo.height = height;
    		fileInfo.url = url;
    		fileInfo.directory = null;
    	}
    	return fileInfo;
    }

    /** Used by ImagePlus to monitor loading of images. */
    public boolean imageUpdate(Image img, int flags, int x, int y, int w, int h) {
    	imageUpdateY = y;
    	imageUpdateW = w;
		if ((flags & ERROR) != 0) {
			errorLoadingImage = true;
			return false;
		}
    	imageLoaded = (flags & (ALLBITS|FRAMEBITS|ABORT)) != 0;
		return !imageLoaded;
    }

	/** Sets the ImageProcessor, Roi, AWT Image and stack image
		arrays to null. Does nothing if the image is locked. */
	public synchronized void flush() {
		notifyListeners(CLOSED);
		if (locked || ignoreFlush) return;
		ip = null;
		if (roi!=null) roi.setImage(null);
		roi = null;
		if (stack!=null && stack.viewers(-1)<=0) {
			Object[] arrays = stack.getImageArray();
			if (arrays!=null) {
				for (int i=0; i<arrays.length; i++)
					arrays[i] = null;
			}
			if (isComposite())
				((CompositeImage)this).setChannelsUpdated(); //flush
		}
		setStackNull();
		img = null;
		win = null;
		if (roi!=null) roi.setImage(null);
		roi = null;
		properties = null;
		//calibration = null;
		overlay = null;
		flatteningCanvas = null;
	}

	public void setIgnoreFlush(boolean ignoreFlush) {
		this.ignoreFlush = ignoreFlush;
	}


	/** Returns a copy of this image or stack.
	* @see #crop
	* @see ij.plugin.Duplicator#run
	*/
	public ImagePlus duplicate() {
		Roi roi = getRoi();
		deleteRoi();
		ImagePlus imp2 = (new Duplicator()).run(this);
		setRoi(roi);
		return imp2;
	}
	
	/** Returns a scaled copy of this image or ROI, where the
		 'options'  string can contain 'none', 'bilinear'. 'bicubic',
		'average' and 'constrain'.
	*/
	public ImagePlus resize(int dstWidth, int dstHeight, String options) {
		return resize(dstWidth, dstHeight, 1, options);
	}

	/** Returns a scaled copy of this image or ROI, where the
		 'options'  string can contain 'none', 'bilinear'. 'bicubic',
		'average' and 'constrain'.
	*/
	public ImagePlus resize(int dstWidth, int dstHeight, int dstDepth, String options) {
		return Scaler.resize(this, dstWidth, dstHeight, dstDepth, options);
	}

	/** Returns a copy this image or stack slice, cropped if there is an ROI.
	 * @see #duplicate
	 * @see ij.plugin.Duplicator#crop
	*/
	public ImagePlus crop() {
		return (new Duplicator()).crop(this);
	}

	/** Returns a cropped copy this image or stack, where 'options'
	 * can be "stack", "slice" or a range (e.g., "20-30").
	 * @see #duplicate
	 * @see #crop
	 * @see ij.plugin.Duplicator#crop
	*/
	public ImagePlus crop(String options) {
		String msg = "crop: \"stack\", \"slice\" or a range (e.g., \"20-30\") expected";
		int stackSize = getStackSize();
		if (options==null || options.equals("stack"))
			return (new Duplicator()).run(this);
		else if (options.equals("slice") || stackSize==1)
			return crop();
		else {
			String[] range = Tools.split(options, " -");
			if (range.length!=2)
				throw new IllegalArgumentException(msg);
			double s1 = Tools.parseDouble(range[0]);
			double s2 = Tools.parseDouble(range[1]);
			if (Double.isNaN(s1) || Double.isNaN(s2))
				throw new IllegalArgumentException(msg);
			if (s1<1) s1 = 1;
			if (s2>stackSize) s2 = stackSize;
			if (s1>s2) {s1=1; s2=stackSize;}
			return new Duplicator().run(this, (int)s1, (int)s2);
		}
	}

	/** Returns a new ImagePlus with this image's attributes
		(e.g. spatial scale), but no image. */
	public ImagePlus createImagePlus() {
		ImagePlus imp2 = new ImagePlus();
		imp2.setType(getType());
		imp2.setCalibration(getCalibration());
		String info = (String)getProperty("Info");
		if (info!=null)
			imp2.setProperty("Info", info);
		imp2.setProperties(getPropertiesAsArray());
		FileInfo fi = getOriginalFileInfo();
		if (fi!=null) {
			fi = (FileInfo)fi.clone();
			fi.directory = null;
			fi.url = null;
			imp2.setFileInfo(fi);
		}
		return imp2;
	}


 	/** This method has been replaced by IJ.createHyperStack(). */
	public ImagePlus createHyperStack(String title, int channels, int slices, int frames, int bitDepth) {
		int size = channels*slices*frames;
		ImageStack stack2 = new ImageStack(width, height, size); // create empty stack
		ImageProcessor ip2 = null;
		switch (bitDepth) {
			case 8: ip2 = new ByteProcessor(width, height); break;
			case 16: ip2 = new ShortProcessor(width, height); break;
			case 24: ip2 = new ColorProcessor(width, height); break;
			case 32: ip2 = new FloatProcessor(width, height); break;
			default: throw new IllegalArgumentException("Invalid bit depth");
		}
		stack2.setPixels(ip2.getPixels(), 1); // can't create ImagePlus will null 1st image
		ImagePlus imp2 = new ImagePlus(title, stack2);
		stack2.setPixels(null, 1);
		imp2.setDimensions(channels, slices, frames);
		imp2.setCalibration(getCalibration());
		imp2.setOpenAsHyperStack(true);
		return imp2;
	}

	/** Copies the calibration of the specified image to this image. */
	public void copyScale(ImagePlus imp) {
		if (imp!=null && globalCalibration==null)
			setCalibration(imp.getCalibration());
	}

	/** Copies attributes (name, ID, calibration, path, plot) of the specified image to this image. */
	public void copyAttributes(ImagePlus imp) {
		if (IJ.debugMode) IJ.log("copyAttributes: "+imp.getID()+"  "+this.getID()+" "+imp+"   "+this);
		if (imp==null || imp.getWindow()!=null)
			throw new IllegalArgumentException("Source image is null or displayed");
		ID = imp.getID();
		setTitle(imp.getTitle());
		setCalibration(imp.getCalibration());
		FileInfo fi = imp.getOriginalFileInfo();
		if (fi!=null)
			setFileInfo(fi);
		Object info = imp.getProperty("Info");
		if (info!=null)
			setProperty("Info", imp.getProperty("Info"));
		setProperties(imp.getPropertiesAsArray());
		Object plot = imp.getProperty(Plot.PROPERTY_KEY);
		if (plot != null)
			setProperty(Plot.PROPERTY_KEY, plot);
	}

    /** Calls System.currentTimeMillis() to save the current
		time so it can be retrieved later using getStartTime()
		to calculate the elapsed time of an operation. */
    public void startTiming() {
		startTime = System.currentTimeMillis();
    }

    /** Returns the time in milliseconds when
		startTiming() was last called. */
    public long getStartTime() {
		return startTime;
    }

	/** Returns this image's calibration. */
	public Calibration getCalibration() {
		//IJ.log("getCalibration: "+globalCalibration+" "+calibration);
		if (globalCalibration!=null && !ignoreGlobalCalibration) {
			Calibration gc = globalCalibration.copy();
			gc.setImage(this);
			return gc;
		} else {
			if (calibration==null)
				calibration = new Calibration(this);
			return calibration;
		}
	}

   /** Sets this image's calibration. */
    public void setCalibration(Calibration cal) {
		if (cal==null)
			calibration = null;
		else {
			calibration = cal.copy();
			calibration.setImage(this);
		}
   }

    /** Sets the system-wide calibration. */
    public void setGlobalCalibration(Calibration global) {
		//IJ.log("setGlobalCalibration: "+calibration);
		if (global==null)
			globalCalibration = null;
		else
			globalCalibration = global.copy();
    }

    /** Returns the system-wide calibration, or null. */
    public Calibration getGlobalCalibration() {
			return globalCalibration;
    }

    /** This is a version of getGlobalCalibration() that can be called from a static context. */
    public static Calibration getStaticGlobalCalibration() {
			return globalCalibration;
    }

	/** Returns this image's local calibration, ignoring
		the "Global" calibration flag. */
	public Calibration getLocalCalibration() {
		if (calibration==null)
			calibration = new Calibration(this);
		return calibration;
	}

	public void setIgnoreGlobalCalibration(boolean ignoreGlobalCalibration) {
		this.ignoreGlobalCalibration = ignoreGlobalCalibration;
	}

    /** Displays the cursor coordinates and pixel value in the status bar.
	 * Called by ImageCanvas when the mouse moves.
    */
	public void mouseMoved(int x, int y) {
		Roi roi2 = getRoi();
		if (ij!=null && !IJ.statusBarProtected() && (roi2==null || roi2.getState()==Roi.NORMAL))
			ij.showStatus(getLocationAsString(x,y) + getValueAsString(x,y));
	}

    /** Redisplays the (x,y) coordinates and pixel value (which may
	 * have changed) in the status bar. Called by the Next Slice and
	 * Previous Slice commands to update the z-coordinate and pixel value.
    */
	public void updateStatusbarValue() {
		ImageCanvas ic = getCanvas();
		Point loc = ic!=null?ic.getCursorLoc():null;
		if (loc!=null)
			mouseMoved(loc.x,loc.y);
	}

	String getFFTLocation(int x, int y, Calibration cal) {
		double center = width/2.0;
		double r = Math.sqrt((x-center)*(x-center) + (y-center)*(y-center));
		double theta = Math.atan2(y-center, x-center);
		theta = theta*180.0/Math.PI;
		if (theta<0) theta=360.0+theta;
		String s = "r=";
		if (r<1.0)
			return s+"Infinity/c (0)"; //origin ('DC offset'), no angle
		else if (cal.scaled())
			s += IJ.d2s((width/r)*cal.pixelWidth,2) + " " + cal.getUnit() + "/c (" + IJ.d2s(r,0) + ")";
		else
			s += IJ.d2s(width/r,2) + " p/c (" + IJ.d2s(r,0) + ")";
		s += ", theta= " + IJ.d2s(theta,2) + IJ.degreeSymbol;
		return s;
	}

    /** Converts the current cursor location to a string. */
    public String getLocationAsString(int x, int y) {
		Calibration cal = getCalibration();
		if (getProperty("FHT")!=null)
			return getFFTLocation(x, height-y, cal);
		String xx="", yy="";
		if (cal.scaled()) {
			xx = " ("+x+")";
			yy = " ("+y+")";
		}
		String s = " x="+d2s(cal.getX(x)) + xx + ", y=" + d2s(cal.getY(y,height)) + yy;
		if (getStackSize()>1) {
			Roi roi2 = getRoi();
			if (roi2==null || roi2.getState()==Roi.NORMAL) {
				int z = isDisplayedHyperStack()?getSlice()-1:getCurrentSlice()-1;
				String zz = cal.scaled()&&cal.getZ(z)!=z?" ("+z+")":"";
				s += ", z="+d2s(cal.getZ(z))+zz;
			}
		}
		return s;
    }

    private String d2s(double n) {
		return n==(int)n?Integer.toString((int)n):IJ.d2s(n);
	}

    private String getValueAsString(int x, int y) {
    	if (win!=null && win instanceof PlotWindow)
    		return "";
		Calibration cal = getCalibration();
    	int[] v = getPixel(x, y);
    	int type = getType();
		switch (type) {
			case GRAY8: case GRAY16: case COLOR_256:
				if (type==COLOR_256) {
					if (cal.getCValue(v[3])==v[3]) // not calibrated
						return(", index=" + v[3] + ", value=" + v[0] + "," + v[1] + "," + v[2]);
					else
						v[0] = v[3];
				}
				double cValue = cal.getCValue(v[0]);
				if (cValue==v[0])
    				return(", value=" + v[0]);
    			else
    				return(", value=" + IJ.d2s(cValue) + " ("+v[0]+")");
    		case GRAY32:
    			double value = Float.intBitsToFloat(v[0]);
    			String s = (int)value==value?IJ.d2s(value,0)+".0":IJ.d2s(value,4,7);
    			return(", value=" + s);
			case COLOR_RGB:
				String hex = Colors.colorToString(new Color(v[0],v[1],v[2]));
				return(", value=" + IJ.pad(v[0],3) + "," + IJ.pad(v[1],3) + "," + IJ.pad(v[2],3) + " ("+hex + ")");
    		default: return("");
		}
    }

	/** Copies the contents of the current selection, or the entire
		image if there is no selection, to the internal clipboard. */
	public void copy() {
		copy(false);
	}

	/** Copies the contents of the current selection to the internal clipboard.
		Copies the entire image if there is no selection. Also clears
		the selection if <code>cut</code> is true. */
	public void copy(boolean cut) {
		Roi roi = getRoi();
		if (roi!=null && !roi.isArea())
			roi = null;
		if (cut && roi==null && !IJ.isMacro()) {
			IJ.error("Edit>Cut", "This command requires an area selection");
			return;
		}
		boolean batchMode = Interpreter.isBatchMode();
		String msg = (cut)?"Cut":"Copy";
		if (!batchMode) IJ.showStatus(msg+ "ing...");
		ImageProcessor ip = getProcessor();
		ImageProcessor ip2;
		ip2 = ip.crop();
		clipboard = new ImagePlus("Clipboard", ip2);
		if (roi!=null)
			clipboard.setRoi((Roi)roi.clone());
		if (cut) {
			ip.snapshot();
	 		ip.setColor(Toolbar.getBackgroundColor());
			ip.fill();
			if (roi!=null && roi.getType()!=Roi.RECTANGLE) {
				getMask();
				ip.reset(ip.getMask());
			} setColor(Toolbar.getForegroundColor());
			Undo.setup(Undo.FILTER, this);
			updateAndDraw();
		}
		int bytesPerPixel = 1;
		switch (clipboard.getType()) {
			case ImagePlus.GRAY16: bytesPerPixel = 2; break;
			case ImagePlus.GRAY32: case ImagePlus.COLOR_RGB: bytesPerPixel = 4;
		}
		if (!batchMode) {
			msg = (cut)?"Cut":"Copy";
			IJ.showStatus(msg + ": " + (clipboard.getWidth()*clipboard.getHeight()*bytesPerPixel)/1024 + "k");
		}
    }


	 /** Inserts the contents of the internal clipboard into the active image. If there
	 is a selection the same size as the image on the clipboard, the image is inserted
	 into that selection, otherwise the selection is inserted into the center of the image.*/
	 public void paste() {
		if (clipboard==null)
			return;
		int cType = clipboard.getType();
		int iType = getType();
        int w = clipboard.getWidth();
        int h = clipboard.getHeight();
		Roi cRoi = clipboard.getRoi();
		Rectangle r = null;
		Rectangle cr = null;
		Roi roi = getRoi();
		if (roi!=null)
			r = roi.getBounds();
		if (cRoi!=null)
			cr = cRoi.getBounds();
		if (cr==null)
			cr = new Rectangle(0, 0, w, h);
		if (r==null || (cr.width!=r.width || cr.height!=r.height)) {
			// Create a new roi centered on visible part of image, or centered on image if clipboard is >= image
			ImageCanvas ic = win!=null?ic = win.getCanvas():null;
			Rectangle srcRect = ic!=null?ic.getSrcRect():new Rectangle(0,0,width,height);
			int xCenter = w>=width ? width/2 : srcRect.x + srcRect.width/2;
			int yCenter = h>=height ? height/2 : srcRect.y + srcRect.height/2;
			if (cRoi!=null && cRoi.getType()!=Roi.RECTANGLE) {
				cRoi.setImage(this);
				cRoi.setLocation(xCenter-w/2, yCenter-h/2);
				setRoi(cRoi);
			} else
				setRoi(xCenter-w/2, yCenter-h/2, w, h);
			roi = getRoi();
		}
		if (IJ.isMacro()) {
			//non-interactive paste
			int pasteMode = Roi.getCurrentPasteMode();
			boolean nonRect = roi.getType()!=Roi.RECTANGLE;
			ImageProcessor ip = getProcessor();
			if (nonRect) ip.snapshot();
			r = roi.getBounds();
			int xoffset = cr.x<0?-cr.x:0;
			int yoffset = cr.y<0?-cr.y:0;
			ip.copyBits(clipboard.getProcessor(), r.x+xoffset, r.y+yoffset, pasteMode);
			if (nonRect) {
				ImageProcessor mask = roi.getMask();
				ip.setMask(mask);
				ip.setRoi(roi.getBounds());
				ip.reset(ip.getMask());
			}
			updateAndDraw();
		} else if (roi!=null) {
			roi.startPaste(clipboard);
			Undo.setup(Undo.PASTE, this);
		}
		changes = true;
    }

	/** Returns the internal clipboard or null if the internal clipboard is empty. */
	public static ImagePlus getClipboard() {
		return clipboard;
	}

	/** Clears the internal clipboard. */
	public static void resetClipboard() {
		clipboard = null;
	}
	
	protected void notifyListeners(final int id) {
	    final ImagePlus imp = this;
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				for (int i=0; i<listeners.size(); i++) {
					ImageListener listener = (ImageListener)listeners.elementAt(i);
					switch (id) {
						case OPENED:
							listener.imageOpened(imp);
							break;
						case CLOSED:
							listener.imageClosed(imp);
							break;
						case UPDATED:
							listener.imageUpdated(imp);
							break;
					}
				}
			}
		});
	}

	public static void addImageListener(ImageListener listener) {
		listeners.addElement(listener);
	}

	public static void removeImageListener(ImageListener listener) {
		listeners.removeElement(listener);
	}
	
	/** For debug purposes, writes all registered (and possibly,
		forgotten) ImageListeners to the log window */
	public static void logImageListeners() {
		if (listeners.size() == 0)
			IJ.log("No ImageListeners");
		else {
			for (Object li : listeners) {
				IJ.log("imageListener: "+li);
				if (li instanceof Window)
					IJ.log("   ("+(((Window)li).isShowing() ? "showing" : "invisible")+")");
			}
		}
	}

	public void setOpenAsHyperStack(boolean openAsHyperStack) {
		this.openAsHyperStack = openAsHyperStack;
	}

	public boolean getOpenAsHyperStack() {
		return openAsHyperStack;
	}

	/** Returns true if this is a CompositeImage. */
	public boolean isComposite() {
		return compositeImage && nChannels>=1 && imageType!=COLOR_RGB && (this instanceof CompositeImage);
	}

	/** Returns the display mode (IJ.COMPOSITE, IJ.COLOR
		or IJ.GRAYSCALE) if this is a CompositeImage, otherwise returns -1. */
	public int getCompositeMode() {
		if (isComposite())
			return ((CompositeImage)this).getMode();
		else
			return -1;
	}

	/** Sets the display range of the current channel. With non-composite
	    images it is identical to ip.setMinAndMax(min, max). */
	public void setDisplayRange(double min, double max) {
		if (ip!=null)
			ip.setMinAndMax(min, max);
	}

	public double getDisplayRangeMin() {
		return ip.getMin();
	}

	public double getDisplayRangeMax() {
		return ip.getMax();
	}

	/**	Sets the display range of specified channels in an RGB image, where 4=red,
		2=green, 1=blue, 6=red+green, etc. With non-RGB images, this method is
		identical to setDisplayRange(min, max).  This method is used by the
		Image/Adjust/Color Balance tool . */
	public void setDisplayRange(double min, double max, int channels) {
		if (ip instanceof ColorProcessor)
			((ColorProcessor)ip).setMinAndMax(min, max, channels);
		else
			ip.setMinAndMax(min, max);
	}

	public void resetDisplayRange() {
		if (imageType==GRAY16 && default16bitDisplayRange>=8 && default16bitDisplayRange<=16 && !(getCalibration().isSigned16Bit()))
			ip.setMinAndMax(0, Math.pow(2,default16bitDisplayRange)-1);
		else
			ip.resetMinAndMax();
	}

	/** Returns 'true' if this image is thresholded. */
	public boolean isThreshold() {
		return ip!=null && ip.getMinThreshold()!=ImageProcessor.NO_THRESHOLD;
	}

    /** Set the default 16-bit display range, where 'bitDepth' must be 0 (auto-scaling),
    	8 (0-255), 10 (0-1023), 12 (0-4095, 14 (0-16383), 15 (0-32767) or 16 (0-65535). */
    public static void setDefault16bitRange(int bitDepth) {
    	if (!(bitDepth==8 || bitDepth==10 || bitDepth==12 || bitDepth==14 || bitDepth==15 || bitDepth==16))
    		bitDepth = 0;
    	default16bitDisplayRange = bitDepth;
    }

    /** Returns the default 16-bit display range, 0 (auto-scaling), 8, 10, 12, 14, 15 or 16. */
    public static int getDefault16bitRange() {
    	return default16bitDisplayRange;
    }

	public void updatePosition(int c, int z, int t) {
		//IJ.log("updatePosition: "+c+", "+z+", "+t);
		position[0] = c;
		position[1] = z;
		position[2] = t;
	}

	/** Returns a "flattened" version of this image, in RGB format. */
	public ImagePlus flatten() {
		if (IJ.debugMode) IJ.log("flatten");
		ImagePlus imp2 = createImagePlus();
		imp2.setTitle(flattenTitle);
		ImageCanvas ic2 = new ImageCanvas(imp2);
		imp2.flatteningCanvas = ic2;
		imp2.setRoi(getRoi());
		if (getStackSize()>1) {
			imp2.setStack(getStack());
			imp2.setSlice(getCurrentSlice());
			if (isHyperStack()) {
				imp2.setDimensions(getNChannels(),getNSlices(),getNFrames());
				imp2.setPosition(getChannel(),getSlice(),getFrame());
				imp2.setOpenAsHyperStack(true);
			}
		}
		Overlay overlay2 = getOverlay();
		if (overlay2!=null && imp2.getRoi()!=null) {
			imp2.deleteRoi();
			if (getWindow()!=null) IJ.wait(100);
		}
		setPointScale(imp2.getRoi(), overlay2);
		imp2.setOverlay(overlay2);
		ImageCanvas ic = getCanvas();
		if (ic!=null)
			ic2.setShowAllList(ic.getShowAllList());
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = (Graphics2D)bi.getGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			antialiasRendering?RenderingHints.VALUE_ANTIALIAS_ON:RenderingHints.VALUE_ANTIALIAS_OFF);
		g.drawImage(getImage(), 0, 0, null);
		ic2.paint(g);
		imp2.flatteningCanvas = null;
		ImagePlus imp3 = new ImagePlus("Flat_"+getTitle(), new ColorProcessor(bi));
		imp3.copyScale(this);
		imp3.setProperty("Info", getProperty("Info"));
		imp3.setProperties(getPropertiesAsArray());
		return imp3;
	}

	/** Flattens all slices of this stack or HyperStack.<br>
	 * @throws UnsupportedOperationException if this image<br>
	 * does not have an overlay and the RoiManager overlay is null<br>
	 * or Java version is less than 1.6.
	 * Copied from OverlayCommands and modified by Marcel Boeglin
	 * on 2014.01.08 to work with HyperStacks.
	 */
	public void flattenStack() {
		if (IJ.debugMode) IJ.log("flattenStack");
		if (getStackSize()==1)
			throw new UnsupportedOperationException("Image stack required");
		boolean composite = isComposite();
		if (getBitDepth()!=24)
			new ImageConverter(this).convertToRGB();
		Overlay overlay1 = getOverlay();
		Overlay roiManagerOverlay = null;
		boolean roiManagerShowAllMode = !Prefs.showAllSliceOnly;
		ImageCanvas ic = getCanvas();
		if (ic!=null)
			roiManagerOverlay = ic.getShowAllList();
		setOverlay(null);
		if (roiManagerOverlay!=null) {
			RoiManager rm = RoiManager.getInstance();
			if (rm!=null)
				rm.runCommand("show none");
		}
		Overlay overlay2 = overlay1!=null?overlay1:roiManagerOverlay;
		if (composite && overlay2==null)
				return;
		if (overlay2==null||overlay2.size()==0)
			throw new UnsupportedOperationException("A non-empty overlay is required");
		ImageStack stack2 = getStack();
		boolean showAll = overlay1!=null?false:roiManagerShowAllMode;
		if (isHyperStack()) {
			int Z = getNSlices();
			for (int z=1; z<=Z; z++) {
				for (int t=1; t<=getNFrames(); t++) {
					int s = z + (t-1)*Z;
					flattenImage(stack2, s, overlay2.duplicate(), showAll, z, t);
				}
			}
		} else {
			for (int s=1; s<=stack2.getSize(); s++) {
				flattenImage(stack2, s, overlay2.duplicate(), showAll);
			}
		}
		setStack(stack2);
	}

	/** Flattens Overlay 'overlay' on slice 'slice' of ImageStack 'stack'.
	 * Copied from OverlayCommands by Marcel Boeglin 2014.01.08.
	 */
	private void flattenImage(ImageStack stack, int slice, Overlay overlay, boolean showAll) {
		ImageProcessor ips = stack.getProcessor(slice);
		ImagePlus imp1 = new ImagePlus("temp", ips);
		int w = imp1.getWidth();
		int h = imp1.getHeight();
		for (int i=0; i<overlay.size(); i++) {
			Roi r = overlay.get(i);
			int roiPosition = r.getPosition();
			//IJ.log(slice+" "+i+" "+roiPosition+" "+showAll+" "+overlay.size());
			if (!(roiPosition==0 || roiPosition==slice || showAll))
				r.setLocation(w, h);
		}
		imp1.setOverlay(overlay);
		ImagePlus imp2 = imp1.flatten();
		stack.setPixels(imp2.getProcessor().getPixels(), slice);
	}

	/** Flattens Overlay 'overlay' on slice 'slice' corresponding to
	 * coordinates 'z' and 't' in RGB-HyperStack 'stack'
	 */
	private void flattenImage(ImageStack stack, int slice, Overlay overlay, boolean showAll, int z, int t) {
		ImageProcessor ips = stack.getProcessor(slice);
		ImagePlus imp1 = new ImagePlus("temp", ips);
		int w = imp1.getWidth();
		int h = imp1.getHeight();
		for (int i=0; i<overlay.size(); i++) {
			Roi r = overlay.get(i);
			int cPos = r.getCPosition();// 0 or 1 (RGB-HyperStack)
			int zPos = r.getZPosition();
			int tPos = r.getTPosition();
			if (!((cPos==1 || cPos==0) && (zPos==z || zPos==0) && (tPos==t || tPos==0) || showAll))
				r.setLocation(w, h);
		}
		imp1.setOverlay(overlay);
		ImagePlus imp2 = imp1.flatten();
		stack.setPixels(imp2.getProcessor().getPixels(), slice);
	}
	
	public boolean tempOverlay() {
		Overlay o = getOverlay();
		if (o==null || o.size()!=1)
			return false;
		if ("Pixel Inspector".equals(o.get(0).getName()))
			return true;
		else
			return false;
	}

	private void setPointScale(Roi roi2, Overlay overlay2) {
		ImageCanvas ic = getCanvas();
		if (ic==null)
			return;
		double scale = 1.0/ic.getMagnification();
		if (scale==1.0)
			return;
		if (roi2!=null && (roi2 instanceof PointRoi))
			roi2.setFlattenScale(scale);
		if (overlay2!=null) {
			for (int i=0; i<overlay2.size(); i++) {
				roi2 = overlay2.get(i);
				if (roi2!=null && (roi2 instanceof PointRoi))
					roi2.setFlattenScale(scale);
			}
		}
	}

	/** Assigns a LUT (lookup table) to this image.
	 * @see ij.io.Opener#openLut
	*/
	public void setLut(LUT lut) {
		ImageProcessor ip2 = getProcessor();
		if (ip2!=null && lut!=null) {
			ip2.setLut(lut);
			setProcessor(ip2);
		}
	}

	/** Installs a list of ROIs that will be drawn on this image as a non-destructive overlay.
	 * @see ij.gui.Roi#setStrokeColor
	 * @see ij.gui.Roi#setStrokeWidth
	 * @see ij.gui.Roi#setFillColor
	 * @see ij.gui.Roi#setLocation
	 * @see ij.gui.Roi#setNonScalable
	 */
	public void setOverlay(Overlay overlay) {
		this.overlay = overlay;
		setHideOverlay(false);
		ImageCanvas ic = getCanvas();
		if (ic!=null)
			ic.repaintOverlay();
	}

	/** Creates an Overlay from the specified Shape, Color
	 * and BasicStroke, and assigns it to this image.
	 * @see #setOverlay(ij.gui.Overlay)
	 * @see ij.gui.Roi#setStrokeColor
	 * @see ij.gui.Roi#setStrokeWidth
	 */
	public void setOverlay(Shape shape, Color color, BasicStroke stroke) {
		if (shape==null)
			{setOverlay(null); return;}
		Roi roi = new ShapeRoi(shape);
		roi.setStrokeColor(color);
		roi.setStroke(stroke);
		setOverlay(new Overlay(roi));
	}

	/** Creates an Overlay from the specified ROI, and assigns it to this image.
	 * @see #setOverlay(ij.gui.Overlay)
	 */
	public void setOverlay(Roi roi, Color strokeColor, int strokeWidth, Color fillColor) {
		roi.setStrokeColor(strokeColor);
		roi.setStrokeWidth(strokeWidth);
		roi.setFillColor(fillColor);
		setOverlay(new Overlay(roi));
	}

	/** Returns the current overly, or null if this image does not have an overlay. */
	public Overlay getOverlay() {
		return overlay;
	}

	public void setHideOverlay(boolean hide) {
		hideOverlay = hide;
		ImageCanvas ic = getCanvas();
		if (ic!=null && ic.getOverlay()!=null)
			ic.repaint();
	}

	public boolean getHideOverlay() {
		return hideOverlay;
	}

	/** Enable/disable use of antialiasing by the flatten() method. */
	public void setAntialiasRendering(boolean antialiasRendering) {
		this.antialiasRendering = antialiasRendering;
	}

	/** Returns a shallow copy of this ImagePlus. */
	public synchronized Object clone() {
		try {
			ImagePlus copy = (ImagePlus)super.clone();
			copy.win = null;
			return copy;
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

	/** Plots a 256 bin histogram of this image and returns the PlotWindow. */
	public PlotWindow plotHistogram() {
		return plotHistogram(256);
	}

	/** Plots a histogram of this image using the specified
		number of bins and returns the PlotWindow. */
	public PlotWindow plotHistogram(int bins) {
		ImageStatistics stats = getStatistics(AREA+MEAN+MODE+MIN_MAX, bins);
		Plot plot = new Plot("Hist_"+getTitle(), "Value", "Frequency");
		plot.setColor("black", "#999999");
		plot.setFont(new Font("SansSerif",Font.PLAIN,14));
		double[] y = stats.histogram();
		int n = y.length;
		double[] x = new double[n];
		int bits = getBitDepth();
		boolean eightBit = bits==8 || bits==24;
		double min = !eightBit?stats.min:0;
		for (int i=0; i<n; i++)
			x[i] = min+i*stats.binSize;
		plot.add("bar", x, y);
		if (bins!=256)
			plot.addLegend(bins+" bins", "auto");
		if (eightBit)
			plot.setLimits(0,256,0,Double.NaN);
		return plot.show();
	}

    public String toString() {
    	return "img[\""+getTitle()+"\" ("+getID()+"), "+getBitDepth()+"-bit, "+width+"x"+height+"x"+getNChannels()+"x"+getNSlices()+"x"+getNFrames()+"]";
    }

    public void setIJMenuBar(boolean b) {
    	setIJMenuBar = b;
    }

    public boolean setIJMenuBar() {
    	return setIJMenuBar && Prefs.setIJMenuBar;
    }

    public boolean isStack() {
    	return stack!=null;
    }

    public void setPlot(Plot plot) {
    	this.plot = plot;
    }

    public Plot getPlot() {
    	return plot;
    }
    
}
