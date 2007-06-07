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

/** A frame for displaying images. */
public class ImageWindow extends Frame implements FocusListener, WindowListener {

	protected ImagePlus imp;
	protected ImageJ ij;
	protected ImageCanvas ic;
	private double initialMagnification = 1;
	protected static ImagePlus clipboard;
	protected boolean closed;
		
	private static final int XINC = 8;
	private static final int YINC = 12;
	private static final int TEXT_GAP = 10;
	private static final int MENU_BAR_HEIGHT = 40;
	private static int xbase = -1;
	private static int ybase;
	private static int xloc;
	private static int yloc;
	private static int count;
	//private static int defaultYLoc = IJ.isMacintosh()?5:32;
	
	/** This variable is set false if the user clicks in this
		window, presses the escape key, or closes the window. */
	public boolean running;
	
	private int j = 0;

    public ImageWindow(ImagePlus imp) {
    	this(imp, new ImageCanvas(imp));
   }
    
    public ImageWindow(ImagePlus imp, ImageCanvas ic) {
		super(imp.getTitle());
        setBackground(Color.white);
        setForeground(Color.black);
		ij = IJ.getInstance();
		this.imp = imp;
		this.ic = ic;
		ImageWindow previousWindow = imp.getWindow();
		setLayout(new ImageLayout(ic));
		add(ic);
 		addFocusListener(this);
 		addWindowListener(this);
 		addKeyListener(ij);
		setResizable(true);
		WindowManager.addWindow(this);
		imp.setWindow(this);
		if (previousWindow!=null) {
			setLocationAndSize();
			Point loc = previousWindow.getLocation();
			setLocation(loc.x, loc.y);
			pack();
			setVisible(true);
			boolean unlocked = imp.lockSilently();
			boolean changes = imp.changes;
			imp.changes = false;
			previousWindow.close();
			imp.changes = changes;
			if (unlocked)
				imp.unlock();
			WindowManager.setCurrentWindow(this);
		} else {
			setLocationAndSize();
			if (ij!=null) {
				Image img = ij.getIconImage();
				if (img!=null) setIconImage(img);
			}
			pack();
			setVisible(true);
		}
     }
    
	private void setLocationAndSize() {
		int width = imp.getWidth();
		int height = imp.getHeight();
		if (WindowManager.getWindowCount()<=1)
			xbase = -1;
		if (xbase==-1) {
			Rectangle ijBounds = ij!=null?ij.getBounds():new Rectangle(10,5,0,0);
			if (IJ.isMacintosh())
				ijBounds.height += 24;
			count = 0;
			xbase = 5;
			ybase = ijBounds.y+ijBounds.height;
			if (ybase>140) ybase = ijBounds.height;
			xloc = xbase;
			yloc = ybase;
		}
		int x = xloc;
		int y = yloc;
		xloc += XINC;
		yloc += YINC;
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		count++;
		if (count%6==0) {
			xloc = xbase;
			yloc = ybase;
		}

		int taskbarHeight = IJ.isWindows()?30:0;
		int sliderHeight = (this instanceof StackWindow)?20:0;
		int screenHeight = screen.height-MENU_BAR_HEIGHT-taskbarHeight-sliderHeight;
		double mag = 1;
		while (xbase+XINC*4+width*mag>screen.width || ybase+height*mag>screenHeight)
			mag = ImageCanvas.getLowerZoomLevel(mag);
		ic.setMagnification(mag);
		
		if (mag<1.0) {
			initialMagnification = mag;
			ic.setDrawingSize((int)(width*mag), (int)(height*mag));
		}
		if (y+height*mag>screenHeight)
			y = ybase;
		setLocation(x, y);
	}
	

	public double getInitialMagnification() {
		return initialMagnification;
	}
	
	/** Override Container getInsets() to make room for some text above the image. */
	public Insets getInsets() {
		Insets insets = super.getInsets();
		//IJ.write(""+insets);
		return new Insets(insets.top+TEXT_GAP, insets.left, insets.bottom, insets.right);
	}
    
	//public void update(Graphics g) {
	//}

    public void drawInfo(Graphics g) {
    	String s="";
		Insets insets = super.getInsets();
    	int nSlices = imp.getStackSize();
    	if (nSlices>1) {
    		ImageStack stack = imp.getStack();
    		int currentSlice = imp.getCurrentSlice();
    		s += currentSlice+"/"+nSlices;
    		boolean isLabel = false;
    		String label = stack.getSliceLabel(currentSlice);
    		if (label!=null && label.length()>0)
    			s += " (" + label + ")";
			if ((this instanceof StackWindow) && running) {
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
	    		s += "8-bit grayscale";
	    		break;
	    	case ImagePlus.GRAY16:
	    		s += "16-bit grayscale";
				size *= 2;
	    		break;
	    	case ImagePlus.GRAY32:
	    		s += "32-bit grayscale";
				size *= 4;
	    		break;
	    	case ImagePlus.COLOR_256:
	    		s += "8-bit color";
	    		break;
	    	case ImagePlus.COLOR_RGB:
	    		s += "RGB";
				size *= 4;
	    		break;
    	}
    	s += "; " + size + "K";
		g.drawString(s, 5, insets.top+TEXT_GAP);
    }

    public void paint(Graphics g) {
		//if (IJ.debugMode) IJ.log("wPaint: " + imp.getTitle());
		drawInfo(g);
		Point loc = ic.getLocation();
		Dimension csize = ic.getSize();
		g.drawRect(loc.x-1, loc.y-1, csize.width+1, csize.height+1);
		//IJ.write(p + " " + d);
    }
    
	/** Removes this window from the window list and disposes of it.
		Returns false if the user cancels the "save changes" dialog. */
	public boolean close() {
		boolean isRunning = running;
		running = false;
		if (isRunning) IJ.wait(500);
		if (imp.changes && IJ.getApplet()==null && !IJ.macroRunning()) {
			SaveChangesDialog d = new SaveChangesDialog(IJ.getInstance(), imp.getTitle());
			if (d.cancelPressed())
				return false;
			else if (d.savePressed()) {
				FileSaver fs = new FileSaver(imp);
				if (!fs.save())
					return false;
			}
		}
		if (WindowManager.getWindowCount()==0)
			{xloc = 0; yloc = 0;}
		closed = true;
		WindowManager.removeWindow(this);
		setVisible(false);
		dispose();
		imp.flush();
		//imp.setWindow(null);
		return true;
	}
	

	public ImagePlus getImagePlus() {
		return imp;
	}


	void setImagePlus(ImagePlus imp) {
		this.imp = imp;
		repaint();
	}
	
	public ImageCanvas getCanvas() {
		return ic;
	}
	

	static ImagePlus getClipboard() {
		return clipboard;
	}
	
	/** Has this window been closed? */
	public boolean isClosed() {
		return closed;
	}
	
	public void focusGained(FocusEvent e) {
		WindowManager.setCurrentWindow(this);
	}


	public void windowActivated(WindowEvent e) {
		if (IJ.isMacintosh() && IJ.getInstance()!=null)
			this.setMenuBar(Menus.getMenuBar());
		//if (IJ.debugMode) IJ.log(imp.getTitle() + ": Activated");
		if (!closed) {
			//ic.requestFocus();
			WindowManager.setCurrentWindow(this);
		}
	}
	
	public void windowClosing(WindowEvent e) {
		if (IJ.getInstance()!=null) {
			WindowManager.setCurrentWindow(this);
			IJ.doCommand("Close");
		} else {
			setVisible(false);
			dispose();
		}
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
		Roi roi = imp.getRoi();
		String msg = (cut)?"Cut":"Copy";
		IJ.showStatus(msg+ "ing...");
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor ip2 = ip.crop();
		clipboard = new ImagePlus("Clipboard", ip2);
		if (roi!=null && roi.getType()!=Roi.RECTANGLE)
			clipboard.setRoi((Roi)roi.clone());
		if (cut) {
			ip.snapshot();
	 		ip.setColor(Toolbar.getBackgroundColor());
			ip.fill();
			if (roi!=null && roi.getType()!=Roi.RECTANGLE)
				ip.reset(imp.getMask());
			imp.setColor(Toolbar.getForegroundColor());
			Undo.setup(Undo.FILTER, imp);
			imp.updateAndDraw();
		}
		int bytesPerPixel = 1;
		switch (clipboard.getType()) {
			case ImagePlus.GRAY16: bytesPerPixel = 2; break;
			case ImagePlus.GRAY32: case ImagePlus.COLOR_RGB: bytesPerPixel = 4;
		}
		IJ.showStatus(msg + ": " + (clipboard.getWidth()*clipboard.getHeight()*bytesPerPixel)/1024 + "k");
    }
                

	public void paste() {
		if (clipboard==null)
			return;
		int cType = clipboard.getType();
		int iType = imp.getType();
		
		boolean sameType = false;
		if ((cType==ImagePlus.GRAY8|cType==ImagePlus.COLOR_256)&&(iType==ImagePlus.GRAY8|iType==ImagePlus.COLOR_256)) sameType = true;
		else if ((cType==ImagePlus.COLOR_RGB|cType==ImagePlus.GRAY8|cType==ImagePlus.COLOR_256)&&iType==ImagePlus.COLOR_RGB) sameType = true;
		else if (cType==ImagePlus.GRAY16&&iType==ImagePlus.GRAY16) sameType = true;
		else if (cType==ImagePlus.GRAY32&&iType==ImagePlus.GRAY32) sameType = true;
		if (!sameType) {
			IJ.error("Images must be the same type to paste.");
			return;
		}
        int w = clipboard.getWidth();
        int h = clipboard.getHeight();
		if (w>imp.getWidth() || h>imp.getHeight()) {
			IJ.error("Image is too large to paste.");
			return;
		}
		Roi roi = imp.getRoi();
		Rectangle r = null;
		if (roi!=null)
			r = roi.getBoundingRect();
		if (r==null || (r!=null && (w!=r.width || h!=r.height))) {
			// create a new roi centered on visible part of image
			Rectangle srcRect = ic.getSrcRect();
			int xCenter = srcRect.x + srcRect.width/2;
			int yCenter = srcRect.y + srcRect.height/2;
			Roi cRoi = clipboard.getRoi();
			if (cRoi!=null && cRoi.getType()!=Roi.RECTANGLE) {
				cRoi.setImage(imp);
				cRoi.setLocation(xCenter-w/2, yCenter-h/2);
				imp.setRoi(cRoi);
			} else
				imp.setRoi(xCenter-w/2, yCenter-h/2, w, h);
			roi = imp.getRoi();
		}
		roi.startPaste(clipboard);
		Undo.setup(Undo.PASTE, imp);
		imp.changes = true;
		//Image img = clipboard.getImage();
		//ImagePlus imp2 = new ImagePlus("Clipboard", img);
		//imp2.show();
    }
                
    public void mouseMoved(int x, int y) {
    	imp.mouseMoved(x, y);
    }
    
    public String toString() {
    	return imp.getTitle();
    }
    
	/** Overrides the setBounds() method in Component so
		we can find out when the window is resized. */
	//public void setBounds(int x, int y, int width, int height)	{
	//	super.setBounds(x, y, width, height);
	//	ic.resizeSourceRect(width, height);
	//}
	
} //class ImageWindow

