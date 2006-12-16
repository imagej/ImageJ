package ij.gui;

import java.awt.*;
import java.awt.image.*;
import java.util.Properties;
import java.awt.event.*;
import ij.*;
import ij.process.*;
import ij.io.*;
import ij.measure.*;
import ij.plugin.frame.Recorder;
import ij.macro.Interpreter;

/** A frame for displaying images. */
public class ImageWindow extends Frame implements FocusListener, WindowListener, WindowStateListener {

	public static final int MIN_WIDTH = 128;
	public static final int MIN_HEIGHT = 32;
	
	protected ImagePlus imp;
	protected ImageJ ij;
	protected ImageCanvas ic;
	private double initialMagnification = 1;
	private int newWidth, newHeight;
	protected boolean closed;
	private boolean newCanvas;
	private static Rectangle maxWindow;
	Rectangle maxBounds;
	//boolean maximized;
		
	private static final int XINC = 8;
	private static final int YINC = 12;
	private static final int TEXT_GAP = 10;
	private static int xbase = -1;
	private static int ybase;
	private static int xloc;
	private static int yloc;
	private static int count;
	private static boolean centerOnScreen;
	
    private int textGap = centerOnScreen?0:TEXT_GAP;
	
	/** This variable is set false if the user presses the escape key or closes the window. */
	public boolean running;
	
	/** This variable is set false if the user clicks in this
		window, presses the escape key, or closes the window. */
	public boolean running2;
	
	public ImageWindow(String title) {
		super(title);
	}

    public ImageWindow(ImagePlus imp) {
    	this(imp, null);
   }
    
    public ImageWindow(ImagePlus imp, ImageCanvas ic) {
		super(imp.getTitle());
		if (Prefs.blackCanvas && getClass().getName().equals("ij.gui.ImageWindow")) {
			setForeground(Color.white);
			setBackground(Color.black);
		} else {
        	setForeground(Color.black);
        	setBackground(Color.white);
        }
		ij = IJ.getInstance();
		this.imp = imp;
		if (ic==null)
			{ic=new ImageCanvas(imp); newCanvas=true;}
		this.ic = ic;
		ImageWindow previousWindow = imp.getWindow();
		setLayout(new ImageLayout(ic));
		add(ic);
 		addFocusListener(this);
 		addWindowListener(this);
 		addWindowStateListener(this);
 		addKeyListener(ij);
		setResizable(true);
		WindowManager.addWindow(this);
		imp.setWindow(this);
		if (previousWindow!=null) {
			if (newCanvas)
				setLocationAndSize(false);
			else
				ic.update(previousWindow.getCanvas());
			Point loc = previousWindow.getLocation();
			setLocation(loc.x, loc.y);
			if (!(this instanceof StackWindow)) {
				pack();
				show();
			}
			boolean unlocked = imp.lockSilently();
			boolean changes = imp.changes;
			imp.changes = false;
			previousWindow.close();
			imp.changes = changes;
			if (unlocked)
				imp.unlock();
			WindowManager.setCurrentWindow(this);
		} else {
			setLocationAndSize(false);
			if (ij!=null && !IJ.isMacintosh()) {
				Image img = ij.getIconImage();
				if (img!=null) 
					try {setIconImage(img);} catch (Exception e) {}
			}
			if (centerOnScreen) {
				GUI.center(this);
				centerOnScreen = false;
			}
			if (Interpreter.isBatchMode()) {
				WindowManager.setTempCurrentImage(imp);
				Interpreter.addBatchModeImage(imp);
			} else
				show();
		}
     }
    
	private void setLocationAndSize(boolean updating) {
		int width = imp.getWidth();
		int height = imp.getHeight();
		if (maxWindow==null)
			maxWindow = getMaxWindow();
		if (WindowManager.getWindowCount()<=1)
			xbase = -1;
		if (xbase==-1) {
			count = 0;
			xbase = maxWindow.x + 5;
			ybase = maxWindow.y;
			xloc = xbase;
			yloc = ybase;
		}
		int x = xloc;
		int y = yloc;
		xloc += XINC;
		yloc += YINC;
		count++;
		if (count%6==0) {
			xloc = xbase;
			yloc = ybase;
		}

		int sliderHeight = (this instanceof StackWindow)?20:0;
		int screenHeight = maxWindow.height-sliderHeight;
		double mag = 1;
		while (xbase+XINC*4+width*mag>maxWindow.width || ybase+height*mag>screenHeight) {
			double mag2 = ImageCanvas.getLowerZoomLevel(mag);
			if (mag2==mag)
				break;
			mag = mag2;
		}
		
		if (mag<1.0) {
			initialMagnification = mag;
			ic.setDrawingSize((int)(width*mag), (int)(height*mag));
		}
		ic.setMagnification(mag);
		if (y+height*mag>screenHeight)
			y = ybase;
        if (!updating) setLocation(x, y);
		if (Prefs.open100Percent && ic.getMagnification()<1.0) {
			while(ic.getMagnification()<1.0)
				ic.zoomIn(0, 0);
			setSize(Math.min(width, maxWindow.width-x), Math.min(height, screenHeight-y));
			validate();
		} else 
			pack();
		maxBounds = getMaximumBounds();
		setMaximizedBounds(maxBounds);
	}
				
	Rectangle getMaxWindow() {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		Rectangle maxWindow = ge.getMaximumWindowBounds();
		Dimension ijSize = ij!=null?ij.getSize():new Dimension(0,0);
		maxWindow.y += ijSize.height;
		maxWindow.height -= ijSize.height;
		return maxWindow;
	}

	public double getInitialMagnification() {
		return initialMagnification;
	}
	
	/** Override Container getInsets() to make room for some text above the image. */
	public Insets getInsets() {
		Insets insets = super.getInsets();
		double mag = ic.getMagnification();
		int extraWidth = (int)((MIN_WIDTH - imp.getWidth()*mag)/2.0);
		if (extraWidth<0) extraWidth = 0;
		int extraHeight = (int)((MIN_HEIGHT - imp.getHeight()*mag)/2.0);
		if (extraHeight<0) extraHeight = 0;
		insets = new Insets(insets.top+textGap+extraHeight, insets.left+extraWidth, insets.bottom+extraHeight, insets.right+extraWidth);
		return insets;
	}

    public void drawInfo(Graphics g) {
        if (textGap==0)
            return;
    	String s="";
		Insets insets = super.getInsets();
    	int nSlices = imp.getStackSize();
    	if (nSlices>1) {
    		ImageStack stack = imp.getStack();
    		int currentSlice = imp.getCurrentSlice();
    		s += currentSlice+"/"+nSlices;
    		boolean isLabel = false;
    		String label = stack.getShortSliceLabel(currentSlice);
    		if (label!=null && label.length()>0)
    			s += " (" + label + ")";
			if ((this instanceof StackWindow) && running2) {
				g.drawString(s, 5, insets.top+TEXT_GAP);
				return;
			}
    		s += "; ";
		}
		
    	int type = imp.getType();
    	Calibration cal = imp.getCalibration();
    	if (cal.pixelWidth!=1.0 || cal.pixelHeight!=1.0)
    		s += IJ.d2s(imp.getWidth()*cal.pixelWidth,2) + "x" + IJ.d2s(imp.getHeight()*cal.pixelHeight,2)
 			+ " " + cal.getUnits() + " (" + imp.getWidth() + "x" + imp.getHeight() + "); ";
    	else
    		s += imp.getWidth() + "x" + imp.getHeight() + " pixels; ";
		int size = (imp.getWidth()*imp.getHeight()*imp.getStackSize())/1024;
    	switch (type) {
	    	case ImagePlus.GRAY8:
	    	case ImagePlus.COLOR_256:
	    		s += "8-bit";
	    		break;
	    	case ImagePlus.GRAY16:
	    		s += "16-bit";
				size *= 2;
	    		break;
	    	case ImagePlus.GRAY32:
	    		s += "32-bit";
				size *= 4;
	    		break;
	    	case ImagePlus.COLOR_RGB:
	    		s += "RGB";
				size *= 4;
	    		break;
    	}
    	if (imp.isInvertedLut())
    		s += " (inverting LUT)";
    	if (size>=10000)    	
    		s += "; " + (int)Math.round(size/1024.0) + "MB";
    	else if (size>=1024) {
    		double size2 = size/1024.0;
    		s += "; " + IJ.d2s(size2,(int)size2==size2?0:1) + "MB";
    	} else
    		s += "; " + size + "K";
		g.drawString(s, 5, insets.top+TEXT_GAP);
    }


    public void paint(Graphics g) {
		//if (IJ.debugMode) IJ.log("wPaint: " + imp.getTitle());
		drawInfo(g);
		Rectangle r = ic.getBounds();
		int extraWidth = MIN_WIDTH - r.width;
		int extraHeight = MIN_HEIGHT - r.height;
		if (extraWidth<=0 && extraHeight<=0)
			g.drawRect(r.x-1, r.y-1, r.width+1, r.height+1);
    }
    
	/** Removes this window from the window list and disposes of it.
		Returns false if the user cancels the "save changes" dialog. */
	public boolean close() {
		boolean isRunning = running || running2;
		running = running2 = false;
		if (isRunning) IJ.wait(500);
		if (ij==null || IJ.getApplet()!=null || Interpreter.isBatchMode() || IJ.macroRunning())
			imp.changes = false;
		if (imp.changes) {
			String msg;
			String name = imp.getTitle();
			if (name.length()>22)
				msg = "Save changes to\n" + "\"" + name + "\"?";
			else
				msg = "Save changes to \"" + name + "\"?";
			YesNoCancelDialog d = new YesNoCancelDialog(this, "ImageJ", msg);
			if (d.cancelPressed())
				return false;
			else if (d.yesPressed()) {
				FileSaver fs = new FileSaver(imp);
				if (!fs.save()) return false;
			}
		}
		closed = true;
		if (WindowManager.getWindowCount()==0)
			{xloc = 0; yloc = 0;}
		WindowManager.removeWindow(this);
		setVisible(false);
		if (ij!=null && ij.quitting())  // this may help avoid thread deadlocks
			return true;
		dispose();
		imp.flush();
		return true;
	}
	
	public ImagePlus getImagePlus() {
		return imp;
	}


	void setImagePlus(ImagePlus imp) {
		this.imp = imp;
		repaint();
	}
	
	public void updateImage(ImagePlus imp) {
        if (imp!=this.imp)
            throw new IllegalArgumentException("imp!=this.imp");
		this.imp = imp;
        ic.updateImage(imp);
        setLocationAndSize(true);
        pack();
		repaint();
	}

	public ImageCanvas getCanvas() {
		return ic;
	}
	

	static ImagePlus getClipboard() {
		return ImagePlus.getClipboard();
	}
	
	public Rectangle getMaximumBounds() {
		int width = imp.getWidth();
		int height = imp.getHeight();
		maxWindow = getMaxWindow();
		Insets insets = getInsets();
		int extraHeight = insets.top+insets.bottom;
		if (this instanceof StackWindow) extraHeight += 25;
		//if (IJ.isWindows()) extraHeight += 20;
		double maxHeight = maxWindow.height-extraHeight;
		double maxWidth = maxWindow.width;
		double mAspectRatio = maxWidth/maxHeight;
		double iAspectRatio = (double)width/height;
		int wWidth, wHeight;
		if (iAspectRatio>=mAspectRatio) {
			wWidth = (int)maxWidth;
			wHeight = (int)(maxWidth/iAspectRatio);
		} else {
			wHeight = (int)maxHeight;
			wWidth = (int)(maxHeight*iAspectRatio);
		}
		int xloc = (int)(maxWidth-wWidth)/2;
		if (xloc<0) xloc = 0;
		return new Rectangle(xloc, maxWindow.y, wWidth, wHeight+extraHeight);
	}

	public void maximize() {
		if (maxBounds==null) return;
		int width = imp.getWidth();
		int height = imp.getHeight();
		Insets insets = getInsets();
		int extraHeight = insets.top+insets.bottom+5;
		if (this instanceof StackWindow) extraHeight += 25;
		double mag = Math.floor((maxBounds.height-extraHeight)*100.0/height)/100.0;
		ic.setMagnification2(mag);
		ic.setSrcRect(new Rectangle(0, 0, width, height));
		ic.setDrawingSize((int)(width*mag), (int)(height*mag));
		validate();
	}
	
	public void minimize() {
		ic.unzoom();
	}

	/** Has this window been closed? */
	public boolean isClosed() {
		return closed;
	}
	
	public void focusGained(FocusEvent e) {
		//IJ.log("focusGained: "+imp.getTitle());
		if (!Interpreter.isBatchMode() && ij!=null && !ij.quitting())
			WindowManager.setCurrentWindow(this);
	}


	public void windowActivated(WindowEvent e) {
		//IJ.log("windowActivated: "+imp.getTitle());
		ImageJ ij = IJ.getInstance();
		boolean quitting = ij!=null && ij.quitting();
		if (IJ.isMacintosh() && ij!=null && !quitting) {
			IJ.wait(10); // may be needed for Java 1.4 on OS X
			setMenuBar(Menus.getMenuBar());
		}
		imp.setActivated(); // notify ImagePlus that image has been activated
		if (!closed && !quitting && !Interpreter.isBatchMode())
			WindowManager.setCurrentWindow(this);
	}
	
	public void windowClosing(WindowEvent e) {
		//IJ.log("windowClosing: "+imp.getTitle()+" "+closed);
		if (closed)
			return;
		if (ij!=null) {
			WindowManager.setCurrentWindow(this);
			IJ.doCommand("Close");
		} else {
			setVisible(false);
			dispose();
			WindowManager.removeWindow(this);
		}
	}
	
	public void windowStateChanged(WindowEvent e) {
		int oldState = e.getOldState();
		int newState = e.getNewState();
		//IJ.log("WSC: "+getBounds()+" "+oldState+" "+newState);
		if ((oldState & Frame.MAXIMIZED_BOTH) == 0 && (newState & Frame.MAXIMIZED_BOTH) != 0)
			maximize();
		else if ((oldState & Frame.MAXIMIZED_BOTH) != 0 && (newState & Frame.MAXIMIZED_BOTH) == 0)
			minimize();
	}

	public void windowClosed(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
	public void focusLost(FocusEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}	
	public void windowOpened(WindowEvent e) {}
	
	/** Copies the current ROI to the clipboard. The entire
	    image is copied if there is no ROI. */
	public void copy(boolean cut) {
		imp.copy(cut);
    }
                

	public void paste() {
		imp.paste();
    }
                
    /** This method is called by ImageCanvas.mouseMoved(MouseEvent). 
    	@see ij.gui.ImageCanvas#mouseMoved
    */
    public void mouseMoved(int x, int y) {
    	imp.mouseMoved(x, y);
    }
    
    public String toString() {
    	return imp.getTitle();
    }
    
    /** Causes the next image to be opened to be centered on the screen
    	and displayed without informational text above the image. */
    public static void centerNextImage() {
    	centerOnScreen = true;
    }

	/** Overrides the setBounds() method in Component so
		we can find out when the window is resized. */
	//public void setBounds(int x, int y, int width, int height)	{
	//	super.setBounds(x, y, width, height);
	//	ic.resizeSourceRect(width, height);
	//}
	
} //class ImageWindow

