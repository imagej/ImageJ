package ij.gui;

import java.awt.*;
import java.util.Properties;
import java.awt.image.*;
import java.awt.event.*;
import ij.process.ImageProcessor;
import ij.measure.*;
import ij.plugin.frame.Recorder;
import ij.*;
import ij.util.Java2;

/** This is as Canvas used to display images in a Window. */
public class ImageCanvas extends Canvas implements MouseListener, MouseMotionListener, Cloneable {

	protected static Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
	protected static Cursor handCursor = new Cursor(Cursor.HAND_CURSOR);
	protected static Cursor moveCursor = new Cursor(Cursor.MOVE_CURSOR);
	protected static Cursor crosshairCursor = new Cursor(Cursor.CROSSHAIR_CURSOR);

	public static boolean usePointer = Prefs.getBoolean(Prefs.USE_POINTER,false);
	
	protected ImagePlus imp;
	protected boolean imageUpdated;
	protected Rectangle srcRect;
	protected int imageWidth, imageHeight;
		
	private ImageJ ij;
	private double magnification;
	private int dstWidth, dstHeight;

	private int xMouseStart = 0;
	private int yMouseStart = 0;
	private int xSrcStart = 0;
	private int ySrcStart = 0;
	private int xMouse = -1;
	private int yMouse = -1;
	private int flags;

	public ImageCanvas(ImagePlus imp) {
		this.imp = imp;
		ij = IJ.getInstance();
		int width = imp.getWidth();
		int height = imp.getHeight();
		imageWidth = width;
		imageHeight = height;
		srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
		setDrawingSize(width, height);
		magnification = 1.0;
 		addMouseListener(this);
 		addMouseMotionListener(this);
 		addKeyListener(ij);  // ImageJ handles keyboard shortcuts
		//if (ij!=null) add(Menus.getPopupMenu());
	}
		
	public void setDrawingSize(int width, int height) {
	    dstWidth = width;
	    dstHeight = height;
		setSize(dstWidth, dstHeight);
	}
		
	/** ImagePlus.updateAndDraw calls this method to get paint 
		to update the image from the ImageProcessor. */
	public void setImageUpdated() {
		imageUpdated = true;
	}

	public void update(Graphics g) {
		paint(g);
	}

	public void print(Graphics g, double scale) {
		//Rectangle bounds = g.getClipBounds();
		//g.drawImage(imp.getImage(), 0, 0, null);
		g.drawImage(imp.getImage(), 0, 0, (int)(imageWidth*scale), (int)(imageHeight*scale),
			0, 0, imageWidth, imageHeight, null);
	}
	
    public void paint(Graphics g) {
		Roi roi = imp.getRoi();
		if (roi != null) roi.updatePaste();
		try {
			if (imageUpdated) {
				imageUpdated = false;
				imp.updateImage();
			}
			if (IJ.isJava2()) {
				if (magnification<1.0)
					 Java2.setBilinearInterpolation(g, true);
				else if (IJ.isMacOSX())
					Java2.setBilinearInterpolation(g, false);
			}
			Image img = imp.getImage();
			if (img!=null)
 				g.drawImage(img, 0, 0, (int)(srcRect.width*magnification), (int)(srcRect.height*magnification),
				srcRect.x, srcRect.y, srcRect.x+srcRect.width, srcRect.y+srcRect.height, null);
			if (roi != null) roi.draw(g);
		}
		catch(OutOfMemoryError e) {IJ.outOfMemory("Paint");}
    }
    
    public Dimension getPreferredSize() {
        return new Dimension(dstWidth, dstHeight);
    }

    int count;
    
    /*
    public Graphics getGraphics() {
     	Graphics g = super.getGraphics();
		IJ.write("getGraphics: "+count++);
		if (IJ.altKeyDown())
			throw new IllegalArgumentException("");
    	return g;
    }
    */

	/** Returns the current cursor location. */
	public Point getCursorLoc() {
		return new Point(xMouse, yMouse);
	}

	/** Sets the cursor based on the current tool and cursor location. */
	public void setCursor(int x, int y) {
		if (x!=-1) xMouse = x;
		if (y!=-1) yMouse = y;
		Roi roi = imp.getRoi();
		ImageWindow win = imp.getWindow();
		if (win==null)
			return;
		if (IJ.spaceBarDown()) {
			setCursor(handCursor);
			return;
		}
		switch (Toolbar.getToolId()) {
			case Toolbar.MAGNIFIER:
				if (IJ.isMacintosh())
					setCursor(defaultCursor);
				else 
					setCursor(moveCursor);
				break;
			case Toolbar.HAND:
				setCursor(handCursor);
				break;
			default:  //selection tool
				if (usePointer || (roi!=null && roi.getState()!=roi.CONSTRUCTING && roi.contains(x, y)))
					setCursor(defaultCursor);
				else
					setCursor(crosshairCursor);
		}
	}
		
	/**Converts a screen x-coordinate to an offscreen x-coordinate.*/
	public int offScreenX(int x) {
		return srcRect.x + (int)(x/magnification);
	}
	
	/**Converts a screen y-coordinate to an offscreen y-coordinate.*/
	public int offScreenY(int y) {
		return srcRect.y + (int)(y/magnification);
	}
	
	/**Converts an offscreen x-coordinate to a screen x-coordinate.*/
	public int screenX(int x) {
		return  (int)((x-srcRect.x)*magnification);
	}
	
	/**Converts an offscreen y-coordinate to a screen y-coordinate.*/
	public int screenY(int y) {
		return  (int)((y-srcRect.y)*magnification);
	}

	public double getMagnification() {
		return magnification;
	}
		
	public void setMagnification(double magnification) {
		this.magnification = magnification;
		imp.setTitle(imp.getTitle());
	}
		
	public Rectangle getSrcRect() {
		return srcRect;
	}
	
	/** Enlarge the canvas if the user enlarges the window. */
	void resizeCanvas(int width, int height) {
		if (srcRect.width<imageWidth || srcRect.height<imageHeight) {
			if (width>imageWidth*magnification)
				width = (int)(imageWidth*magnification);
			if (height>imageHeight*magnification)
				height = (int)(imageHeight*magnification);
			setDrawingSize(width, height);
			srcRect.width = (int)(dstWidth/magnification);
			srcRect.height = (int)(dstHeight/magnification);
			if ((srcRect.x+srcRect.width)>imageWidth)
				srcRect.x = imageWidth-srcRect.width;
			if ((srcRect.y+srcRect.height)>imageHeight)
				srcRect.y = imageHeight-srcRect.height;
			repaint();
			//IJ.write("resize: " + magnification + " " + srcRect.x+" "+srcRect.y+" "+srcRect.width+" "+srcRect.height+" "+dstWidth + " " + dstHeight);
		}
	}

	private static final double[] zoomLevels = {
		1/32.0, 1/24.0, 1/16.0, 1/12.0, 1/8.0,
		1/6.0, 1/4.0, 1/3.0, 1/2.0, 0.75, 1.0,
		2.0, 3.0, 4.0, 6.0, 8.0, 12.0, 16.0, 24.0, 32.0 };
	
	static double getLowerZoomLevel(double currentMag) {
		double newMag = zoomLevels[0];
		for (int i = 0; i < zoomLevels.length; i++) {
		if (zoomLevels[i] < currentMag)
			newMag = zoomLevels[i];
		else
			break;
		}
		return newMag;
	}

	static double getHigherZoomLevel(double currentMag) {
		double newMag = 32.0;
		for (int i = zoomLevels.length - 1; i >= 0; i--) {
			if (zoomLevels[i] > currentMag)
				newMag = zoomLevels[i];
			else
				break;
		}
		return newMag;
	}

	/** Zooms in by making the window bigger. If we can't
	make it bigger, then make the srcRect smaller.*/
	public void zoomIn(int x, int y) {
		if (magnification>=32)
			return;
		double newMag = getHigherZoomLevel(magnification);
		//if (newMag==imp.getWindow().getInitialMagnification()) {
		//	unzoom();
		//	return;
		//}
		//int newWidth = (int)(dstWidth*newMag/magnification);
		//int newHeight = (int)(dstHeight*newMag/magnification);
		int newWidth = (int)(imageWidth*newMag);
		int newHeight = (int)(imageHeight*newMag);
		if (canEnlarge(newWidth, newHeight)) {
			setDrawingSize(newWidth, newHeight);
			imp.getWindow().pack();
		}
		else {
			int w = (int)Math.round(dstWidth/newMag);
			if (w*newMag<dstWidth) w++;
			int h = (int)Math.round(dstHeight/newMag);
			if (h*newMag<dstHeight) h++;
			x = offScreenX(x);
			y = offScreenY(y);
			Rectangle r = new Rectangle(x-w/2, y-h/2, w, h);
			if (r.x<0) r.x = 0;
			if (r.y<0) r.y = 0;
			if (r.x+w>imageWidth) r.x = imageWidth-w;
			if (r.y+h>imageHeight) r.y = imageHeight-h;
			srcRect = r;
		}
		setMagnification(newMag);
		repaint();
	}
	
	boolean canEnlarge(int newWidth, int newHeight) {
		if ((flags&Event.SHIFT_MASK)!=0)
			return false;
		Rectangle r1 = imp.getWindow().getBounds();
		//int right = r1.x + r1.width;
		//int bottom = r1.y + r1.height;
		r1.width = newWidth + 20;
		r1.height = newHeight + 50;
		if (imp.getStackSize()>1)
			r1.height += 20;
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		//int[] wList = WindowManager.getIDList();
		boolean fitsOnScreen = r1.x+r1.width<screen.width && r1.y+r1.height+30<screen.height;
		//if (wList==null || wList.length<=1 || !fitsOnScreen)
			return fitsOnScreen;
		/*
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp = WindowManager.getImage(wList[i]);
			if (imp!=null && imp!=this.imp) {
				ImageWindow win = imp.getWindow();
				if (win!=null) {
					Rectangle r2 = win.getBounds();
					if (r2.x+r2.width<right && r2.y+r2.height<bottom)
						continue;
					Rectangle inter = r1.intersection(r2);
					if (inter.width*inter.height>0.25*r2.width*r2.height)
						return false;
				}
			}
		}
		return true;
	*/
	}
		
	/**Zooms out by making srcRect bigger. If we can't make
	it bigger, then make the window smaller.*/
	public void zoomOut(int x, int y) {
		if (magnification<=0.03125)
			return;
		double newMag = getLowerZoomLevel(magnification);
		//if (newMag==imp.getWindow().getInitialMagnification()) {
		//	unzoom();
		//	return;
		//}
		if (imageWidth*newMag>dstWidth) {
			int w = (int)Math.round(dstWidth/newMag);
			if (w*newMag<dstWidth) w++;
			int h = (int)Math.round(dstHeight/newMag);
			if (h*newMag<dstHeight) h++;
			x = offScreenX(x);
			y = offScreenY(y);
			Rectangle r = new Rectangle(x-w/2, y-h/2, w, h);
			if (r.x<0) r.x = 0;
			if (r.y<0) r.y = 0;
			if (r.x+w>imageWidth) r.x = imageWidth-w;
			if (r.y+h>imageHeight) r.y = imageHeight-h;
			srcRect = r;
		}
		else {
			srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
			setDrawingSize((int)(imageWidth*newMag), (int)(imageHeight*newMag));
			//setDrawingSize(dstWidth/2, dstHeight/2);
			imp.getWindow().pack();
		}
		//IJ.write(newMag + " " + srcRect.x+" "+srcRect.y+" "+srcRect.width+" "+srcRect.height+" "+dstWidth + " " + dstHeight);
		setMagnification(newMag);
		//IJ.write(srcRect.x + " " + srcRect.width + " " + dstWidth);
		repaint();
	}

	void unzoom() {
		double imag = imp.getWindow().getInitialMagnification();
		if (magnification==imag)
			return;
		setMagnification(imag);
		srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
		ImageWindow win = imp.getWindow();
		setDrawingSize((int)(imageWidth*imag), (int)(imageHeight*imag));
		win.pack();
		repaint();
	}
		
	void scroll(int sx, int sy) {
		int x = xSrcStart + (int)(sx/magnification);  //convert to offscreen coordinates
		int y = ySrcStart + (int)(sy/magnification);
		int newx = xSrcStart + (xMouseStart-x);
		int newy = ySrcStart + (yMouseStart-y);
		if (newx<0) newx = 0;
		if (newy<0) newy = 0;
		if ((newx+srcRect.width)>imageWidth) newx = imageWidth-srcRect.width;
		if ((newy+srcRect.height)>imageHeight) newy = imageHeight-srcRect.height;
		srcRect.x = newx;
		srcRect.y = newy;
		imp.draw();
		Thread.yield();
	}	
	
	Color getColor(int index){
		IndexColorModel cm = (IndexColorModel)imp.getProcessor().getColorModel();
		//IJ.write(""+index+" "+(new Color(cm.getRGB(index))));
		return new Color(cm.getRGB(index));
	}
	
	protected void setDrawingColor(int ox, int oy, boolean setBackground) {
		//IJ.write("setDrawingColor: "+setBackground+this);
		int type = imp.getType();
		int[] v = imp.getPixel(ox, oy);
		switch (type) {
			case ImagePlus.GRAY8: {
				if (setBackground)
					setBackgroundColor(getColor(v[0]));
				else
					setForegroundColor(getColor(v[0]));
				break;
			}
			case ImagePlus.GRAY16: case ImagePlus.GRAY32: {
				double min = imp.getProcessor().getMin();
				double max = imp.getProcessor().getMax();
				double value = (type==ImagePlus.GRAY32)?Float.intBitsToFloat(v[0]):v[0];
				int index = (int)(255.0*((value-min)/(max-min)));
				if (index<0) index = 0;
				if (index>255) index = 255;
				if (setBackground)
					setBackgroundColor(getColor(index));
				else
					setForegroundColor(getColor(index));
				break;
			}
			case ImagePlus.COLOR_RGB: case ImagePlus.COLOR_256: {
				Color c = new Color(v[0], v[1], v[2]);
				if (setBackground)
					setBackgroundColor(c);
				else
					setForegroundColor(c);
				break;
			}
		}
		Color c;
		if (setBackground)
			c = Toolbar.getBackgroundColor();
		else {
			c = Toolbar.getForegroundColor();
			imp.setColor(c);
		}
		IJ.showStatus("("+c.getRed()+", "+c.getGreen()+", "+c.getBlue()+")");
	}
	
	private void setForegroundColor(Color c) {
		Toolbar.setForegroundColor(c);
		if (Recorder.record)
			Recorder.record("setForegroundColor", c.getRed(), c.getGreen(), c.getBlue());
	}

	private void setBackgroundColor(Color c) {
		Toolbar.setBackgroundColor(c);
		if (Recorder.record)
			Recorder.record("setBackgroundColor", c.getRed(), c.getGreen(), c.getBlue());
	}

	public void mousePressed(MouseEvent e) {
		if (ij==null) return;
		int toolID = Toolbar.getToolId();
		ImageWindow win = imp.getWindow();
		if (win!=null && win.running && toolID!=Toolbar.MAGNIFIER) {
			win.running = false;
			return;
		}
		
		int x = e.getX();
		int y = e.getY();
		flags = e.getModifiers();
		if (IJ.debugMode) IJ.log("Mouse pressed: (" + x + "," + y + ")" + ij.modifiers(flags));
		if (toolID!=Toolbar.MAGNIFIER && (e.isPopupTrigger() || (flags & e.META_MASK)!=0)) {
			if (IJ.debugMode) IJ.log("show popup: " + (e.isPopupTrigger()?"true":"false"));
			Roi roi = imp.getRoi();
			if (roi!=null && (roi.getType()==Roi.POLYGON || roi.getType()==Roi.POLYLINE)
			&& roi.getState()==roi.CONSTRUCTING) {
				roi.handleMouseUp(x, y); // simulate double-click to finalize
				roi.handleMouseUp(x, y); // polygon or polyline selection
				return;
			}
			PopupMenu popup = Menus.getPopupMenu();
			if (popup!=null) {
				add(popup);
				popup.show(this, x, y);
			}
			return;
		}

		int ox = offScreenX(x);
		int oy = offScreenY(y);
		if (IJ.spaceBarDown()) {
			// temporarily switch to "hand" tool of space bar down
			xMouseStart = ox; yMouseStart = oy;
			xSrcStart = srcRect.x; ySrcStart = srcRect.y;
			return;
		}
		if ((flags&Event.ALT_MASK)!=0 && toolID!=Toolbar.MAGNIFIER && toolID!=Toolbar.DROPPER) {
			// temporarily switch to color tool alt/option key down
			setDrawingColor(ox, oy, false);
			return;
		}
		switch (toolID) {
			case Toolbar.MAGNIFIER:
				if ((flags & (Event.ALT_MASK|Event.META_MASK|Event.CTRL_MASK))!=0)
					zoomOut(x, y);
				else
					zoomIn(x, y);
				break;
			case Toolbar.HAND:
				xMouseStart = ox;
				yMouseStart = oy;
				xSrcStart = srcRect.x;
				ySrcStart = srcRect.y;
				break;
			case Toolbar.DROPPER:
				setDrawingColor(ox, oy, IJ.altKeyDown());
				break;
			case Toolbar.CROSSHAIR:
				xMouse = ox;
				yMouse = oy;
				IJ.doCommand("Measure");
				break;
			case Toolbar.WAND:
				Roi roi = imp.getRoi();
				if (roi!=null && roi.contains(ox, oy)) {
					Rectangle r = roi.getBoundingRect();
					if (r.width==imageWidth && r.height==imageHeight)
						imp.killRoi();
					else {
						handleRoiMouseDown(x, y);
						return;
					}
				}
				int npoints = IJ.doWand(ox, oy);
				if (Recorder.record && npoints>0)
					Recorder.record("doWand", ox, oy);
				break;
			default:  //selection tool
				handleRoiMouseDown(x, y);
		}
	}

	public void mouseExited(MouseEvent e) {
		ImageWindow win = imp.getWindow();
		if (win!=null) setCursor(defaultCursor);
		xMouse = -1; yMouse = -1;
		IJ.showStatus("");
	}

	public void mouseDragged(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();
		if (Toolbar.getToolId()==Toolbar.HAND || IJ.spaceBarDown())
			scroll(x, y);
		else {
			Roi roi = imp.getRoi();
			if (roi != null)
				roi.handleMouseDrag(x, y, (e.getModifiers()&e.SHIFT_MASK)!=0);
		}
	}

	void handleRoiMouseDown(int sx, int sy) {
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		Roi roi = imp.getRoi();
		if (roi!=null) {
			Rectangle r = roi.getBoundingRect();
			int type = roi.getType();
			if (type==Roi.RECTANGLE && r.width==imp.getWidth() && r.height==imp.getHeight()) {
				imp.killRoi();
				return;
			}
			if (roi.contains(ox, oy)) {
				roi.handleMouseDown(sx, sy);
				return;
			}
			if ((roi.getType()==Roi.POLYGON || roi.getType()==Roi.POLYLINE)
			&& roi.getState()==roi.CONSTRUCTING)
				return;
		}
		imp.createNewRoi(ox,oy);
	}

	public void mouseReleased(MouseEvent e) {
		Roi roi = imp.getRoi();
		if (roi != null) {
			Rectangle r = roi.getBoundingRect();
			if ((r.width==0 || r.height==0)
			&& !(roi.getType()==Roi.POLYGON || roi.getType()==Roi.POLYLINE)
			&& !(roi instanceof TextRoi)
			&& roi.getState()==roi.CONSTRUCTING)
				imp.killRoi();
			else
				roi.handleMouseUp(e.getX(), e.getY());
		}
	}

	public void mouseMoved(MouseEvent e) {
		if (ij==null) return;
		int ox = offScreenX(e.getX());
		int oy = offScreenY(e.getY());
		//if (IJ.debugMode) IJ.log(e.getX() + " " + e.getY() + " " + ox + " " + oy);
		setCursor(ox, oy);
		Roi roi = imp.getRoi();
		if (roi!=null && (roi.getType()==Roi.POLYGON || roi.getType()==Roi.POLYLINE) 
		&& roi.getState()==roi.CONSTRUCTING) {
			PolygonRoi pRoi = (PolygonRoi)roi;
			pRoi.handleMouseMove(ox, oy);
		} else {
			if (ox<imageWidth && oy<imageHeight) {
				ImageWindow win = imp.getWindow();
				if (win!=null) win.mouseMoved(ox, oy);
			} else
				IJ.showStatus("");

		}
	}
	
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}

}