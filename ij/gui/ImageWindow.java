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
public class ImageWindow extends Frame implements FocusListener, WindowListener {

	protected ImagePlus imp;
	protected ImageJ ij;
	protected ImageCanvas ic;
	private double initialMagnification = 1;
	private int newWidth, newHeight;
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
	private static boolean centerOnScreen;
	
    private int textGap = centerOnScreen?0:TEXT_GAP;
	
	/** This variable is set false if presses the escape key or closes the window. */
	public boolean running;
	
	/** This variable is set false if the user clicks in this
		window, presses the escape key, or closes the window. */
	public boolean running2;
	
    public ImageWindow(ImagePlus imp) {
    	this(imp, new ImageCanvas(imp));
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
			setLocationAndSize(false);
			Point loc = previousWindow.getLocation();
			setLocation(loc.x, loc.y);
			show();
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
		while (xbase+XINC*4+width*mag>screen.width || ybase+height*mag>screenHeight) {
			double mag2 = ImageCanvas.getLowerZoomLevel(mag);
			if (mag2==mag)
				break;
			mag = mag2;
		}
		ic.setMagnification(mag);
		
		if (mag<1.0) {
			initialMagnification = mag;
			ic.setDrawingSize((int)(width*mag), (int)(height*mag));
		}
		if (y+height*mag>screenHeight)
			y = ybase;
        if (!updating) setLocation(x, y);
		if (Prefs.open100Percent && ic.getMagnification()<1.0) {
			while(ic.getMagnification()<1.0)
				ic.zoomIn(0, 0);
			setSize(Math.min(width, screen.width-x), Math.min(height, screenHeight-y));
			validate();
		} else 
			pack();
	}
				
	public double getInitialMagnification() {
		return initialMagnification;
	}
	
	/** Override Container getInsets() to make room for some text above the image. */
	public Insets getInsets() {
		Insets insets = super.getInsets();
		//IJ.write(""+insets);
		return new Insets(insets.top+textGap, insets.left, insets.bottom, insets.right);
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
	    		s += "8-bit";
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
		Point loc = ic.getLocation();
		Dimension csize = ic.getSize();
		g.drawRect(loc.x-1, loc.y-1, csize.width+1, csize.height+1);
		//IJ.write(p + " " + d);
    }
    
	/** Removes this window from the window list and disposes of it.
		Returns false if the user cancels the "save changes" dialog. */
	public boolean close() {
		boolean isRunning = running || running2;
		running = running2 = false;
		if (isRunning) IJ.wait(500);
		ImageJ ij = IJ.getInstance();
		if (imp.changes && IJ.getApplet()==null && !IJ.macroRunning() && ij!=null) {
			SaveChangesDialog d = new SaveChangesDialog(ij, imp.getTitle());
			if (d.cancelPressed())
				return false;
			else if (d.savePressed()) {
				FileSaver fs = new FileSaver(imp);
				if (!fs.save())
					return false;
			}
		}
		closed = true;
		if (WindowManager.getWindowCount()==0)
			{xloc = 0; yloc = 0;}
		WindowManager.removeWindow(this);
		setVisible(false);
		if (ij!=null && !ij.quitting()) { // may help avoid thread deadlocks
			dispose();
			imp.flush();
		}
		//imp.setWindow(null);
		//IJ.log("close: "+imp.getTitle());
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
	
	/** Has this window been closed? */
	public boolean isClosed() {
		return closed;
	}
	
	public void focusGained(FocusEvent e) {
		//IJ.log("focusGained: "+imp.getTitle());
		if (!Interpreter.isBatchMode())
			WindowManager.setCurrentWindow(this);
	}


	public void windowActivated(WindowEvent e) {
		//IJ.log("windowActivated: "+imp.getTitle());
		if (IJ.isMacintosh() && IJ.getInstance()!=null) {
			IJ.wait(10); // needed for 1.4 on OS X
			this.setMenuBar(Menus.getMenuBar());
		}
		ImageJ ij = IJ.getInstance();
		boolean quitting = ij!=null && ij.quitting();
		imp.setActivated(); // notify ImagePlus that image has been activated
		if (!closed && !quitting && !Interpreter.isBatchMode())
			WindowManager.setCurrentWindow(this);
	}
	
	public void windowClosing(WindowEvent e) {
		//IJ.log("windowClosing: "+imp.getTitle()+" "+closed);
		if (closed)
			return;
		if (IJ.getInstance()!=null) {
			WindowManager.setCurrentWindow(this);
			IJ.doCommand("Close");
		} else {
			setVisible(false);
			dispose();
			WindowManager.removeWindow(this);
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

