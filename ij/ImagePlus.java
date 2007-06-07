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
import ij.util.Tools;

/**
This is an extended image class that supports 8-bit, 16-bit,
32-bit (real) and RGB images. It also provides support for
3D image stacks.
@see ij.process.ImageProcessor
@see ImageStack
@see ij.gui.ImageWindow
@see ij.gui.ImageCanvas
*/
   
public class ImagePlus implements ImageObserver, Measurements {

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
	
	/** True if any changes have been made to this image. */
	public boolean changes;
	
	/** Obsolete. Use GetCalibration(). */
	public double pixelWidth = 1.0;
	
	/** Obsolete. Use GetCalibration(). */
	public double pixelHeight = 1.0;
	
	/** Obsolete. Use GetCalibration(). */
	public double pixelDepth = 1.0;

	/** Obsolete. Use GetCalibration(). */
	public String unit = "pixel";
	
	/** Obsolete. Use GetCalibration(). */
	public String units = "pixels";
	
	/** Obsolete. Use GetCalibration(). */
	public boolean sCalibrated;

	protected Image img;
	protected ImageProcessor ip;
	protected ImageWindow win;
	private ImageJ ij = IJ.getInstance();
	private String title;
	private	String url;
	private FileInfo fileInfo;
	protected int width;
	protected int height;
	private int imageType = GRAY8;
	private ImageStack stack;
	private int currentSlice;
	private Roi roi;
	protected boolean locked = false;
	private static int currentID = -1;
	private int ID;
	private static Component comp;
	private boolean imageLoaded;
	private int imageUpdateY, imageUpdateW;
	private Properties properties;
	private long startTime;
	private Calibration calibration;
	private static Calibration globalCalibration;

    /** Constructs an uninitialized ImagePlus. */
    public ImagePlus() {
    	ID = --currentID;
		title="null";
    }
    
    /** Constructs an ImagePlus from an AWT Image. The first argument
		will be used as the title of the window that displays the image. */
    public ImagePlus(String title, Image img) {
		this.title = title;
    	ID = --currentID;
		if (img!=null)
			setImage(img);
    }
    
    /** Constructs an ImagePlus from an ImageProcessor. */
    public ImagePlus(String title, ImageProcessor ip) {
 		setProcessor(title, ip);
   		ID = --currentID;
    }
    
	/** Constructs an ImagePlus from a TIFF, BMP, DICOM, FITS,
		PGM, GIF or JPRG specified by a path or from a TIFF,
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
   			if (isURL)
   				this.url = pathOrURL;
   			ID = --currentID;
    	}
    }

	/** Constructs an ImagePlus from a stack. */
    public ImagePlus(String title, ImageStack stack) {
    	setStack(title, stack);
    	ID = --currentID;
    }
    
	/** Locks the image so other threads can test to see if it
		is in use. Returns true if the image was successfully locked.
		Beeps, displays a message in the status bar, and returns
		false if the image is already locked. */
	public synchronized boolean lock() {
		if (locked) {
			IJ.beep();
			IJ.showStatus("\"" + title + "\" is locked");
			if (IJ.macroRunning()) {
				IJ.error("Image is locked");
				Macro.abort();
			}
			return false;
        } else {
        	locked = true;
			if (IJ.debugMode) IJ.log(title + ": lock");
			return true;
        }
	}
	
	/** Similar to lock, but doesn't beep and display an error
		message if the attempt to lock the image fails. */
	public synchronized boolean lockSilently() {
		if (locked)
			return false;
        else {
        	locked = true;
			if (IJ.debugMode) IJ.log(title + ": lock silently");
			return true;
        }
	}
	
	/** Unlocks the image. */
	public synchronized void unlock() {
		locked = false;
		if (IJ.debugMode) IJ.log(title + ": unlock");
	}
		
	private void waitForImage(Image img) {
		if (comp==null) {
			comp = IJ.getInstance();
			if (comp==null)
				comp = new Canvas();
		}
		imageLoaded = false;
		if (!comp.prepareImage(img, this)) {
			double progress;
			while (!imageLoaded) {
				//IJ.showStatus(imageUpdateY+" "+imageUpdateW);
				IJ.wait(30);
				if (imageUpdateW>1) {
					progress = (double)imageUpdateY/imageUpdateW;
					if (!(progress<1.0)) {
						progress = 1.0 - (progress-1.0);
						if (progress<0.0) progress = 0.9;
					}
					IJ.showProgress(progress);
				}
			}
			IJ.showProgress(1.0);
		}
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
		}
	}
	
	/** Updates this image from the pixel data in its 
		associated ImageProcessor, then displays it. Does
		nothing if there is no window associated with
		this image (i.e. show() has not been called).*/
	public void updateAndDraw() {
		if (ip != null) {
			if (win!=null)
				win.getCanvas().setImageUpdated();
			draw();
		}
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
		
	static int counter;
	
	/** ImageCanvas.paint() calls this method when the
		ImageProcessor has generated new image. */
	public void updateImage() {
		if (ip!=null)
			img = ip.createImage();
	}

	/** Closes the window, if any, that is displaying this image. */
	public void hide() {
		if (win==null)
			return;
		boolean unlocked = lockSilently();
		changes = false;
		win.close();
		win = null;
		if (unlocked)
			unlock();
	}

	/** Opens a window to display this image and clears the status bar. */
	public void show() {
		show("");
	}

	/** Opens a window to display this image and displays
		'statusMessage' in the status bar. */
	public void show(String statusMessage) {
		if (win!=null)
			return;
		if (ij==null && IJ.macroRunning()) {
			WindowManager.setTempCurrentImage(this);
			return;
		}
		if (img==null && ip!=null)
			img = ip.createImage();
		if ((img!=null) && (width>=0) && (height>=0)) {
			if (stack!=null && stack.getSize()>1)
				win = new StackWindow(this);
			else
				win = new ImageWindow(this);
			draw();
			IJ.showStatus(statusMessage);
		}
	}
		
	/** Returns the current AWT image. */
	public Image getImage() {
		if (img==null && ip!=null)
			img = ip.createImage();
		return img;
	}
	
	/** Returns this image's unique numeric ID. */
	public int getID() {
		return ID;
	}
	
	/** Replaces the AWT image, if any, with the one specified. */
	public void setImage(Image img) {
		roi = null;
		waitForImage(img);
		this.img = img;
		int newWidth = img.getWidth(ij);
		int newHeight = img.getHeight(ij);
		boolean dimensionsChanged = newWidth!=width || newHeight!=height;
		width = newWidth;
		height = newHeight;
		ip = null;
		stack = null;
		LookUpTable lut = new LookUpTable(img);
		int type;
		if (lut.getMapSize() > 0) {
			if (lut.isGrayscale())
				type = GRAY8;
			else
				type = COLOR_256;
		} else
			type = COLOR_RGB;
		setType(type);
		setupProcessor();
		this.img = ip.createImage();
		if (win!=null) {
			if (dimensionsChanged)
				win = new ImageWindow(this);
			else
				repaintWindow();
		}
	}
	
	/** Replaces the ImageProcessor, if any, with the one specified.
		Set 'title' to null to leave the image title unchanged. */
	public void setProcessor(String title, ImageProcessor ip) {
		if (stack!=null && stack.getSize()<2) {
			stack = null;
			currentSlice = 1;
		}
		setProcessor2(title, ip);
	}
	
	void setProcessor2(String title, ImageProcessor ip) {
		if (title!=null) this.title = title;
		this.ip = ip;
		if (ij!=null) ip.setProgressBar(ij.getProgressBar());
		if (stack!=null) {
			int stackSize = stack.getSize();
			if (stackSize<currentSlice)
				currentSlice = 1;
		}
		//img = ip.createImage();
		img = null;
		boolean dimensionsChanged = width!=ip.getWidth() || height!=ip.getHeight();
		if (dimensionsChanged)
			roi = null;
		//LookUpTable lut = new LookUpTable(img);
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
			if (dimensionsChanged)
				win = new ImageWindow(this);
			else
				repaintWindow();
		}
	}

	/** Replaces the stack, if any, with the one specified.
		Set 'title' to null to leave the title unchanged. */
    public void setStack(String title, ImageStack stack) {
   		int stackSize = stack.getSize();
    	boolean stackSizeChanged = this.stack!=null && stackSize!=getStackSize();
    	if (currentSlice<1)
    		currentSlice = 1;
    	boolean resetCurrentSlice = currentSlice>stackSize;
    	if (resetCurrentSlice)
    		currentSlice = 1;
    	ImageProcessor ip = stack.getProcessor(currentSlice);
    	boolean dimensionsChanged = width!=ip.getWidth() || height!=ip.getHeight();
    	this.stack = stack;
    	setProcessor2(title, ip);
		if (stackSize>1 && win!=null
		&& (!(win instanceof StackWindow) || resetCurrentSlice || dimensionsChanged))
			win = new StackWindow(this);   // replaces this window
		else if (win!=null)
			repaintWindow();
    }
    
	/**	Saves this image's FileInfo so it can be later
		retieved using getOriginalFileInfo(). */
	public void setFileInfo(FileInfo fi) {
		fi.pixels = null;
		fileInfo = fi;
	}
		
	/** Returns the ImageWindow that is being used to display
		this image. Returns null if show() has not be called
		or the ImageWindow has been closed. */
	public ImageWindow getWindow() {
		return win;
	}
	
	/** This method should only be called from an ImageWindow. */
	public void setWindow(ImageWindow win) {
		this.win = win;
	}
	
	/** Sets current foreground color. */
	public void setColor(Color c) {
		if (ip!=null)
			ip.setColor(c);
	}
	
	void setupProcessor() {
		if (imageType==COLOR_RGB) {
			if (ip == null || ip instanceof ByteProcessor) {
				ip = new ColorProcessor(getImage());
				if (IJ.debugMode) IJ.log(title + ": new ColorProcessor");
			}
		}
		else if (ip==null || (ip instanceof ColorProcessor)) {
			ip = new ByteProcessor(getImage());
			if (IJ.debugMode) IJ.log(title + ": new ByteProcessor");
		}
		if (roi!=null && roi.getType()<Roi.LINE)
			ip.setRoi(roi.getBoundingRect());
		else
			ip.resetRoi();
	}
	
	public boolean isProcessor() {
		return ip!=null;
	}
	
	/** Returns a reference to the current ImageProcessor. If there
	    is no ImageProcessor, it creates one. Returns null if this
	    ImagePlus contains no ImageProcessor and no AWT Image. */
	public ImageProcessor getProcessor() {
		if (ip==null && img==null)
			return null;
		setupProcessor();
		ip.setLineWidth(Line.getWidth());
		if (ij!=null) {
			//setColor(Toolbar.getForegroundColor());
			ip.setProgressBar(ij.getProgressBar());
		}
		return ip;
	}
	
	/** Frees RAM by setting the snapshot (undo) buffer in
		the current ImageProcessor to null. */
	public synchronized void trimProcessor() {
		if (ip!=null && !locked) {
			if (ip!=null && IJ.debugMode) IJ.log(title + ": trimProcessor");
			ip.setPixels(ip.getPixels()); // sets snapshot buffer to null
		}
	}
	
	/** Obsolete. */
	public void killProcessor() {
	}
	
	private Rectangle maskRect;
	private int maskCount;
	
	/** For images with irregular ROIs, returns a binary mask, otherwise, returns
		null. Mask pixels have a value of ImageProcessor.BLACK. The size of the
		mask array is rw*rh, where rw and rh are the width and height of the ROI's 
		bounding rectangle.*/
	public int[] getMask () {
		int[] mask = null;
		if (roi!=null && roi.getType()<Roi.LINE) {
			if (ip!=null) {
				mask = ip.getMask();
				if (mask!=null) {
					Rectangle r = roi.getBoundingRect();
					if (maskRect==null || r.width!=maskRect.width || r.height!=maskRect.height)
						mask = null;
					if (roi instanceof PolygonRoi) {
						if (maskCount!=((PolygonRoi)roi).getNCoordinates())
							mask = null;
					} else if (roi instanceof TextRoi)
						mask = null;
				}
			}
			if (mask==null)
				mask = roi.getMask();
		}
		if (ip!=null) {
			if (roi!=null) {
				ip.setMask(mask);
				ip.setRoi(roi.getBoundingRect());
				maskRect = roi.getBoundingRect();
				if (roi instanceof PolygonRoi)
					maskCount = ((PolygonRoi)roi).getNCoordinates();
				else
					maskCount = 0;

			} else
				ip.setMask(null);
		}
		return mask;
	}

	/** Returns an ImageStatistics object generated using the standard
		measurement options (area, mean, mode, min and max). */
	public ImageStatistics getStatistics() {
		return getStatistics(AREA+MEAN+MODE+MIN_MAX);
	}
	
	/** Returns an ImageStatistics object generated using the
		 specified measurement options. */
	public ImageStatistics getStatistics(int mOptions) {
		return getStatistics(mOptions, 256);
	}
	
	/** Returns an ImageStatistics object generated using the
		specified measurement options and histogram bin count. 
		Note: except for float images, the number of histogram bins
		is currently fixed at 256 .
	*/
	public ImageStatistics getStatistics(int mOptions, int nBins) {
		setupProcessor();
		//int[] mask = null;
		//if (ip!=null)
		//	mask = ip.getMask();
		//if (mask==null)
		//	mask = getMask();
		ip.setMask(getMask());
		ip.setHistogramSize(nBins);
		ImageStatistics stats = ImageStatistics.getStatistics(ip, mOptions, getCalibration());
		ip.setHistogramSize(256);
		return stats;
	}
	
	/** Returns the image name. */
	public String getTitle() {
		if (title==null)
			return "";
		else
    		return title;
    }

	/** Returns a shortened version of image name. */
	public String getShortTitle() {
		String title = getTitle();
		int index = title.indexOf(' ');
		if (index>-1)
			title = title.substring(0,index);
		return title;
    }

	/** Sets the image name. */
	public void setTitle(String title) {
    	if (win!=null) {
    		if (ij!=null)
				Menus.updateWindowMenuItem(this.title, title);
			String scale = "";
			double magnification = win.getCanvas().getMagnification();
			if (magnification!=1.0) {
				double percent = magnification*100.0;
				if (percent==(int)percent)
					scale = " (" + IJ.d2s(percent,0) + "%)";
				else
					scale = " (" + IJ.d2s(percent,1) + "%)";
			}
			win.setTitle(title+scale);
    	}
    	this.title = title;
    }

    public int getWidth() {
    	return width;
    }

    public int getHeight() {
    	return height;
    }
    
	/** If this is a stack, return the number of slices, else return 1. */
	public int getStackSize() {
		if (stack==null)
			return 1;
		else {
			int slices = stack.getSize();
			if (slices==0) slices = 1;
			return slices;
		}
	}
	
	/** Returns the current image type. */
    public int getType() {
    	return imageType;
    }

    /** Returns the bit depth, 8, 16, 24 (RGB) or 32. RGB images actually use 32 bits per pixel. */
    public int getBitDepth() {
    	int bitDepth = 0;
    	switch (imageType) {
	    	case GRAY8: case COLOR_256: bitDepth=8; break;
	    	case GRAY16: bitDepth=16; break;
	    	case GRAY32: bitDepth=32; break;
	    	case COLOR_RGB: bitDepth=24; break;
    	}
    	return bitDepth;
    }
    
    protected void setType(int type) {
    	if ((type<0) || (type>COLOR_RGB))
    		return;
    	int previousType = imageType;
    	imageType = type;
		if (win!=null && imageType!=previousType);
			Menus.updateMenus();
    }

	/** Adds a key-value pair to this image's properties. */
	public void setProperty(String key, Object value) {
		if (properties==null)
			properties = new Properties();
		properties.put(key, value);
	}
		
	/** Returns the property associated with 'key'. May return null. */
	public Object getProperty(String key) {
		if (properties==null)
			return null;
		else
			return properties.get(key);
	}
	
	/** Returns this image's Properties. May return null. */
	public Properties getProperties() {
			return properties;
	}
		
	/** Creates a LookUpTable object corresponding to this image. */
    public LookUpTable createLut() {
    	if (getImage()!=null)
    		return new LookUpTable(img);
    	else
    		return null;
    }
    
	/** Returns true is this image uses an inverted LUT that displays zero as white. */
	public boolean isInvertedLut() {
		if (ip==null) {
			if (img==null)
				return false;
			setupProcessor();
		}
		return ip.isInvertedLut();
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
		if (img == null)
			return pvalue;
		switch (imageType) {
			case GRAY8: case COLOR_256:
				int index;
				if (ip!=null)
					index = ip.getPixel(x, y);
				else {
					byte[] pixels8;
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
				int[] pixels32 = new int[1];
				if (win==null) break;
				ImageCanvas ic = win.getCanvas();
				PixelGrabber pg = new PixelGrabber(img, x, y, 1, 1, pixels32, 0, width);
				try {pg.grabPixels();}
				catch (InterruptedException e){return pvalue;};
				int c = pixels32[0];
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
	
	/** Returns the image stack. The stack may have only one slice. */
	public ImageStack getStack() {
		ImageStack s;
		if (stack==null) {
			s = createEmptyStack();
			s.addSlice(null, getProcessor());
		} else {
			s = stack;
			if (ip!=null) s.update(ip);
		}
		if (roi!=null)
			s.setRoi(roi.getBoundingRect());
		return s;
	}
	
	/** Returns the current stack slice number or 1 if
		this is a single image. */
	public int getCurrentSlice() {
		if (currentSlice==0)
			return 1;
		else
			return currentSlice;
	}
	
	public void killStack() {
		stack = null;
		trimProcessor();
	}
			
	/** Activates the specified slice. The index must be >= 1
		and <= N, where N in the number of slices in the stack.
		Does nothing if this ImagePlus does not use a stack. */
	public synchronized void setSlice(int index) {
		if (stack==null || index==currentSlice) {
	    	updateAndRepaintWindow();
			return;
		}
		if (index>=1 && index<=stack.getSize()) {
			Roi roi = getRoi();
			if (roi!=null)
				roi.endPaste();
			if (isProcessor())
				stack.setPixels(ip.getPixels(),currentSlice);
			ip = getProcessor();
			currentSlice = index;
			Object pixels = stack.getPixels(currentSlice);
			if (pixels!=null) ip.setPixels(pixels);
			if (win!=null && win instanceof StackWindow)
				((StackWindow)win).updateSliceSelector();
			if (IJ.spaceBarDown() && (imageType==GRAY16||imageType==GRAY32)) {
				ip.resetMinAndMax();
				IJ.showStatus(index+": min="+ip.getMin()+", max="+ip.getMax());
			}
	    	updateAndRepaintWindow();
		}
	}

	/** Obsolete */
	void undoFilter() {
		if (ip!=null) {
			ip.reset();
			updateAndDraw();
		}
	}

	public Roi getRoi() {
		return roi;
	}
	
	public void setRoi(Roi roi) {
		killRoi();
		if (roi==null)
			return;
		this.roi = roi;
		if (ip!=null) {
			ip.setMask(null);
			ip.setRoi(roi.getBoundingRect());
		}
		this.roi.setImage(this);
		draw();
	}
	
	/** Creates a rectangular selection. */
	public void setRoi(int x, int y, int width, int height) {
		setRoi(new Rectangle(x, y, width, height));
	}

	/** Creates a rectangular selection. */
	public void setRoi(Rectangle r) {
		if (r==null)
			{killRoi(); return;}
		killRoi();
		roi = new Roi(r.x, r.y, r.width, r.height);
		roi.setImage(this);
		if (ip!=null) {
			ip.setMask(null);
			ip.setRoi(r);
		}
		draw();
	}
	
	/** Creates a new selection. The type is determined by which tool in
		the tool bar is active. The user interactively sets the size. */
	public void createNewRoi(int x, int y) {
		killRoi();
		switch (Toolbar.getToolId()) {
			case Toolbar.RECTANGLE:
				roi = new Roi(x, y, this);
				break;
			case Toolbar.OVAL:
				roi = new OvalRoi(x, y, this);
				break;
			case Toolbar.POLYGON:
			case Toolbar.POLYLINE:
				roi = new PolygonRoi(x, y, this);
				break;
			case Toolbar.FREEROI:
			case Toolbar.FREELINE:
				roi = new FreehandRoi(x, y, this);
				break;
			case Toolbar.LINE:
				roi = new Line(x, y, this);
				break;
			case Toolbar.TEXT:
				roi = new TextRoi(x, y, this);
				break;
		}
	}

	/** Deletes the current region of interest. Makes a copy
		of the current ROI so it can be recovered by the
		Edit/Restore Selection command. */
	public void killRoi() {
		if (roi!=null) {
			saveRoi();
			roi = null;
			if (ip!=null)
			ip.resetRoi();
			draw();
		}
	}
	
	void saveRoi() {
		if (roi!=null) {
			roi.endPaste();
			Rectangle r = roi.getBoundingRect();
			if (r.width>0 && r.height>0) {
				Roi.previousRoi = roi;
				if (IJ.debugMode) IJ.log("saveRoi: "+roi);
			}
		}
	}
    
	public void restoreRoi() {
		if (Roi.previousRoi!=null) {
			Roi pRoi = Roi.previousRoi;
			Rectangle r = pRoi.getBoundingRect();
			if (r.width<=width || r.height<=height) { // will it fit in this window?
				roi = (Roi)pRoi.clone();
				roi.setImage(this);
				if ((r.x+r.width)>width || (r.y+r.height)>height) // does it need to be moved?
					roi.setLocation((width-r.width)/2, (height-r.height)/2);
				draw();
			}
		}
	}
    
	void revert() {
		if (getStackSize()>1) // can't revert stacks
			return;
		if (roi!=null)
			roi.endPaste();
		trimProcessor();
		FileInfo fi = getOriginalFileInfo();
		if (fi!=null && fi.fileFormat!=FileInfo.UNKNOWN)
			new FileOpener(fi).revertToSaved(this);
		else if (url!=null) {
			IJ.showStatus("Loading: " + url);
	    	Opener opener = new Opener();
	    	try {
	    		ImagePlus imp = opener.openURL(url);
	    		if (imp!=null)
	     			setProcessor(imp.getTitle(), imp.getProcessor());
	    	} catch (Exception e) {} 
			if (getType()==COLOR_RGB && getTitle().endsWith(".jpg"))
				Opener.convertGrayJpegTo8Bits(this);
		}
		repaintWindow();
		IJ.showStatus("");
    }
    
    /** Returns a FileInfo object containing information, including the
		pixel array, needed to save this image. Use getOriginalFileInfo()
		to get a copy of the FileInfo object used to open the image.
		@see ij.io.FileInfo
		@see getOriginalFileInfo
		@see setFileInfo
	*/
    public FileInfo getFileInfo() {
    	FileInfo fi = new FileInfo();
    	fi.width = width;
    	fi.height = height;
    	fi.nImages = getStackSize();
    	fi.whiteIsZero = isInvertedLut();
		fi.intelByteOrder = false;
    	setupProcessor();
    	if (fi.nImages==1)
    		fi.pixels = ip.getPixels();
    	else
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
		}
    	switch (imageType) {
	    	case GRAY8: case COLOR_256:
    			LookUpTable lut = createLut();
    			if (imageType==COLOR_256 || !lut.isGrayscale())
    				fi.fileType = FileInfo.COLOR8;
    			else
    				fi.fileType = FileInfo.GRAY8;
				fi.lutSize = lut.getMapSize();
				fi.reds = lut.getReds();
				fi.greens = lut.getGreens();
				fi.blues = lut.getBlues();
				break;
	    	case GRAY16:
				fi.fileType = fi.GRAY16_UNSIGNED;
				break;
	    	case GRAY32:
				fi.fileType = fi.GRAY32_FLOAT;
				break;
	    	case COLOR_RGB:
				fi.fileType = fi.RGB;
				break;
			default:
    	}
    	return fi;
    }
        
    /** Returns the FileInfo object that was used to open this
    	image. Returns null for Gif, Jpeg and New images.
		@see ij.io.FileInfo
		@see getFileInfo
	*/
    public FileInfo getOriginalFileInfo() {
    	return fileInfo;
    }

    /** Used by ImagePlus to monitor loading of images. */
    public boolean imageUpdate(Image img, int flags, int x, int y, int w, int h) {
    	imageUpdateY = y;
    	imageUpdateW = w;
    	imageLoaded = (flags & (ALLBITS|FRAMEBITS|ABORT)) != 0;
    	/*
    	// Load animated gif into stack
		if ((flags & FRAMEBITS)!= 0) {
			//Image img2 = IJ.getInstance().createImage(w, h);
			if (stack==null) stack = new ImageStack(w, h);
			//ImageProcessor ip = new ByteProcessor(img);
			//stack.addSlice(null, ip);
			Image img2 = ij.createImage(w, h);
			img2.getGraphics().drawImage(img, 0, 0, this);
			ImageProcessor ip = new ColorProcessor(img2);
			stack.addSlice(null, ip);
			IJ.write(""+stack);
			if (stack.getSize()==10)
				imageLoaded = true;
		}
		//IJ.write(y+" "+flags);
    	//IJ.wait(10);
    	*/
		return !imageLoaded;
    }

	/** Sets the image arrays to null to help the garbage collector
		do its job. Does nothing if the image is locked. */
	public synchronized void flush() {
		if (locked)
			return;
		if (ip!=null) {
			ip.setPixels(null);
			ip = null;
		}
		if (stack!=null) {
			Object[] arrays = stack.getImageArray();
			if (arrays!=null)
				for (int i=0; i<arrays.length; i++)
					arrays[i] = null;
		}
		img = null;
		System.gc();
	}
	
	/** Returns a new ImagePlus with this ImagePlus' attributes
		(e.g. spatial scale), but no image. */
	public ImagePlus createImagePlus() {
		ImagePlus imp2 = new ImagePlus();
		imp2.setCalibration(getCalibration());
		return imp2;
	}

	/** Copies the calibration of the specified image to this image. */
	public void copyScale(ImagePlus imp) {
		if (imp!=null && globalCalibration==null)
			setCalibration(imp.getCalibration());
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
		if (globalCalibration!=null)
			return globalCalibration;
		else {
			if (calibration==null)
				calibration = new Calibration(this);
			return calibration;
		}
	}

   /** Sets this image's calibration. */
    public void setCalibration(Calibration cal) {
		//IJ.write("setCalibration: "+cal);
		if (cal==null)
			calibration = null;
		else {
			calibration = cal.copy();
			calibration.setImage(this);
		}
   }

    /** Sets the system-wide calibration. */
    public void setGlobalCalibration(Calibration global) {
		//IJ.write("setGlobalCalibration ("+getTitle()+"): "+global);
		if (global==null)
			globalCalibration = null;
		else
			globalCalibration = global.copy();
    }
    
    /** Displays the cursor coordinates and pixel value in the status bar.
    	Called by ImageCanvas when the mouse moves. Can be overridden by
    	ImagePlus subclasses.
    */
    public void mouseMoved(int x, int y) {
		IJ.showStatus(getLocationAsString(x,y) + getValueAsString(x,y));
		savex=x; savey=y;
	}
	
    private int savex, savey;
    
    /** Redisplays the (x,y) coordinates and pixel value (which may
		have changed) in the status bar. Called by the Next Slice and
		Previous Slice commands to update the z-coordinate and pixel value.
    */
	public void updateStatusbarValue() {
		IJ.showStatus(getLocationAsString(savex,savey) + getValueAsString(savex,savey));
	}

    /** Converts the current cursor location to a string. */
    public String getLocationAsString(int x, int y) {
		Calibration cal = getCalibration();
		y = Analyzer.updateY(y, height);
		if (cal.scaled()) {
			if (getStackSize()>1)
				return " x="+IJ.d2s(cal.getX(x))+", y="+IJ.d2s(cal.getY(y))
				+", z="+IJ.d2s(cal.getZ(getCurrentSlice()-1));
			else
				return " x="+IJ.d2s(cal.getX(x))+" ("+x+")"
				+", y="+IJ.d2s(cal.getY(y))+" ("+y+")";
		} else {
			String s =  " x="+x+", y=" + y;
			if (getStackSize()>1)
				s += ", z=" + (getCurrentSlice()-1);
			return s;
		}
    }
    
    private String getValueAsString(int x, int y) {
		Calibration cal = getCalibration();
    	int[] v = getPixel(x, y);
		switch (getType()) {
			case GRAY8: case GRAY16:
				double cValue = cal.getCValue(v[0]);
				if (cValue==v[0])
    				return(", value=" + v[0]);
    			else
    				return(", value=" + IJ.d2s(cValue) + " ("+v[0]+")");
    		case GRAY32:
    			return(", value=" + Float.intBitsToFloat(v[0]));
			case COLOR_256:
    			return(", index=" + v[3] + ", value=" + v[0] + "," + v[1] + "," + v[2]);
			case COLOR_RGB:
    			return(", value=" + v[0] + "," + v[1] + "," + v[2]);
    		default: return("");
		}
    }
    
    public String toString() {
    	return getTitle()+" "+width+"x"+height+"x"+getStackSize();
    }

}
