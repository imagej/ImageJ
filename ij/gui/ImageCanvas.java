package ij.gui;

import java.awt.*;
import java.util.Properties;
import java.awt.image.*;
import java.awt.event.*;
import ij.process.ImageProcessor;
import ij.measure.*;
import ij.plugin.frame.Recorder;
import ij.macro.Interpreter;
import ij.*;
import ij.util.Java2;

/** This is a Canvas used to display images in a Window. */
public class ImageCanvas extends Canvas implements MouseListener, MouseMotionListener, Cloneable {

	protected static Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
	protected static Cursor handCursor = new Cursor(Cursor.HAND_CURSOR);
	protected static Cursor moveCursor = new Cursor(Cursor.MOVE_CURSOR);
	protected static Cursor crosshairCursor = new Cursor(Cursor.CROSSHAIR_CURSOR);

	public static boolean usePointer = Prefs.usePointerCursor;
	
	protected ImagePlus imp;
	protected boolean imageUpdated;
	protected Rectangle srcRect;
	protected int imageWidth, imageHeight;
	protected int xMouse; // current cursor offscreen x location 
	protected int yMouse; // current cursor offscreen y location
		
	private ImageJ ij;
	private double magnification;
	private int dstWidth, dstHeight;

	private int xMouseStart;
	private int yMouseStart;
	private int xSrcStart;
	private int ySrcStart;
	private int flags;
	
	public ImageCanvas(ImagePlus imp) {
		this.imp = imp;
		ij = IJ.getInstance();
		int width = imp.getWidth();
		int height = imp.getHeight();
		imageWidth = width;
		imageHeight = height;
		srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
		setDrawingSize(imageWidth, (int)(imageHeight));
		magnification = 1.0;
 		addMouseListener(this);
 		addMouseMotionListener(this);
 		addKeyListener(ij);  // ImageJ handles keyboard shortcuts
	}
		
	void updateImage(ImagePlus imp) {
		this.imp = imp;
		int width = imp.getWidth();
		int height = imp.getHeight();
		imageWidth = width;
		imageHeight = height;
		srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
		setDrawingSize(imageWidth, (int)imageHeight);
		magnification = 1.0;
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

    public void paint(Graphics g) {
		Roi roi = imp.getRoi();
		if (roi != null) roi.updatePaste();
		try {
			if (imageUpdated) {
				imageUpdated = false;
				imp.updateImage();
			}
			if (IJ.isJava2())
				Java2.setBilinearInterpolation(g, Prefs.interpolateScaledImages);
			Image img = imp.getImage();
			if (img!=null)
 				g.drawImage(img, 0, 0, (int)(srcRect.width*magnification), (int)(srcRect.height*magnification),
				srcRect.x, srcRect.y, srcRect.x+srcRect.width, srcRect.y+srcRect.height, null);
			if (roi != null) roi.draw(g);
			if (IJ.debugMode) showFrameRate(g);
		}
		catch(OutOfMemoryError e) {IJ.outOfMemory("Paint");}
    }
    
    long firstFrame;
    int frames, fps;
        
	void showFrameRate(Graphics g) {
		frames++;
		if (System.currentTimeMillis()>firstFrame+1000) {
			firstFrame=System.currentTimeMillis();
			fps = frames;
			frames=0;
		}
		g.setColor(Color.white);
		g.fillRect(10, 12, 50, 15);
		g.setColor(Color.black);
		g.drawString((int)(fps+0.5) + " fps", 10, 25);
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

	/** Returns the mouse event modifiers. */
	public int getModifiers() {
		return flags;
	}

	/** Sets the cursor based on the current tool and cursor location. */
	public void setCursor(int sx, int sy, int ox, int oy) {
		xMouse = ox;
		yMouse = oy;
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
				if (roi!=null && roi.getState()!=roi.CONSTRUCTING && roi.isHandle(sx, sy)>=0)
					setCursor(handCursor);
				else if (Prefs.usePointerCursor || (roi!=null && roi.getState()!=roi.CONSTRUCTING && roi.contains(ox, oy)))
					setCursor(defaultCursor);
				else
					setCursor(crosshairCursor);
		}
	}
		
	/**Converts a screen x-coordinate to an offscreen x-coordinate.*/
	public int offScreenX(int sx) {
		return srcRect.x + (int)(sx/magnification);
	}
		
	/**Converts a screen y-coordinate to an offscreen y-coordinate.*/
	public int offScreenY(int sy) {
		return srcRect.y + (int)(sy/magnification);
	}
	
	/**Converts a screen x-coordinate to a floating-point offscreen x-coordinate.*/
	public double offScreenXD(int sx) {
		return srcRect.x + sx/magnification;
	}
		
	/**Converts a screen y-coordinate to a floating-point offscreen y-coordinate.*/
	public double offScreenYD(int sy) {
		return srcRect.y + sy/magnification;

	}
	
	/**Converts an offscreen x-coordinate to a screen x-coordinate.*/
	public int screenX(int ox) {
		return  (int)((ox-srcRect.x)*magnification);
	}
	
	/**Converts an offscreen y-coordinate to a screen y-coordinate.*/
	public int screenY(int oy) {
		return  (int)((oy-srcRect.y)*magnification);
	}

	/**Converts a floating-point offscreen x-coordinate to a screen x-coordinate.*/
	public int screenXD(double ox) {
		return  (int)((ox-srcRect.x)*magnification);
	}
	
	/**Converts a floating-point offscreen x-coordinate to a screen x-coordinate.*/
	public int screenYD(double oy) {
		return  (int)((oy-srcRect.y)*magnification);
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
		1/72.0, 1/48.0, 1/32.0, 1/24.0, 1/16.0, 1/12.0, 
		1/8.0, 1/6.0, 1/4.0, 1/3.0, 1/2.0, 0.75, 1.0,
		2.0, 3.0, 4.0, 6.0, 8.0, 12.0, 16.0, 24.0, 32.0 };
	
	public static double getLowerZoomLevel(double currentMag) {
		double newMag = zoomLevels[0];
		for (int i=0; i<zoomLevels.length; i++) {
		if (zoomLevels[i] < currentMag)
			newMag = zoomLevels[i];
		else
			break;
		}
		return newMag;
	}

	public static double getHigherZoomLevel(double currentMag) {
		double newMag = 32.0;
		for (int i=zoomLevels.length-1; i>=0; i--) {
			if (zoomLevels[i]>currentMag)
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
		int newWidth = (int)(imageWidth*newMag);
		int newHeight = (int)(imageHeight*newMag);
		if (canEnlarge(newWidth, newHeight)) {
			setDrawingSize(newWidth, newHeight);
			imp.getWindow().pack();
		} else {
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
		if ((flags&Event.SHIFT_MASK)!=0 || IJ.shiftKeyDown())
			return false;
		Rectangle r1 = imp.getWindow().getBounds();
		r1.width = newWidth + 20;
		r1.height = newHeight + 50;
		if (imp.getStackSize()>1)
			r1.height += 20;
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		boolean fitsOnScreen = r1.x+r1.width<screen.width && r1.y+r1.height+30<screen.height;
		return fitsOnScreen;
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

	public void unzoom() {
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
		
	protected void scroll(int sx, int sy) {
		int ox = xSrcStart + (int)(sx/magnification);  //convert to offscreen coordinates
		int oy = ySrcStart + (int)(sy/magnification);
		//IJ.log("scroll: "+ox+" "+oy+" "+xMouseStart+" "+yMouseStart);
		int newx = xSrcStart + (xMouseStart-ox);
		int newy = ySrcStart + (yMouseStart-oy);
		if (newx<0) newx = 0;
		if (newy<0) newy = 0;
		if ((newx+srcRect.width)>imageWidth) newx = imageWidth-srcRect.width;
		if ((newy+srcRect.height)>imageHeight) newy = imageHeight-srcRect.height;
		srcRect.x = newx;
		srcRect.y = newy;
		//IJ.log(sx+"  "+sy+"  "+newx+"  "+newy+"  "+srcRect);
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
		if (win!=null && win.running2 && toolID!=Toolbar.MAGNIFIER) {
			win.running2 = false;
			return;
		}
		
		int x = e.getX();
		int y = e.getY();
		flags = e.getModifiers();
		//IJ.log("Mouse pressed: " + e.isPopupTrigger() + "  " + ij.modifiers(flags));		
		//if (toolID!=Toolbar.MAGNIFIER && e.isPopupTrigger()) {
		if (toolID!=Toolbar.MAGNIFIER && (e.isPopupTrigger() || (flags & Event.META_MASK)!=0)) {
			handlePopupMenu(e);
			return;
		}

		int ox = offScreenX(x);
		int oy = offScreenY(y);
		xMouse = ox; yMouse = oy;
		if (IJ.spaceBarDown()) {
			// temporarily switch to "hand" tool of space bar down
			setupScroll(ox, oy);
			return;
		}
		//if ((flags&Event.ALT_MASK)!=0 && toolID!=Toolbar.MAGNIFIER && toolID!=Toolbar.DROPPER) {
			// temporarily switch to color tool alt/option key down
			//setDrawingColor(ox, oy, false);
			//return;
		//}
		switch (toolID) {
			case Toolbar.MAGNIFIER:
				if ((flags & (Event.ALT_MASK|Event.META_MASK|Event.CTRL_MASK))!=0)
					zoomOut(x, y);
				else
					zoomIn(x, y);
				break;
			case Toolbar.HAND:
				setupScroll(ox, oy);
				break;
			case Toolbar.DROPPER:
				setDrawingColor(ox, oy, IJ.altKeyDown());
				break;
			case Toolbar.WAND:
				Roi roi = imp.getRoi();
				if (roi!=null && roi.contains(ox, oy)) {
					Rectangle r = roi.getBounds();
					if (r.width==imageWidth && r.height==imageHeight)
						imp.killRoi();
					else if (!e.isAltDown()) {
						handleRoiMouseDown(e);
						return;
					}
				}
				if (roi!=null) {
					int handle = roi.isHandle(x, y);
					if (handle>=0) {
						roi.mouseDownInHandle(handle, x, y);
						return;
					}
				}
				setRoiModState(e, roi, -1);
				int npoints = IJ.doWand(ox, oy);
				if (Recorder.record && npoints>0)
					Recorder.record("doWand", ox, oy);
				break;
			case Toolbar.SPARE1: case Toolbar.SPARE2: case Toolbar.SPARE3: 
			case Toolbar.SPARE4: case Toolbar.SPARE5: case Toolbar.SPARE6:
			case Toolbar.SPARE7:
				Toolbar.getInstance().runMacroTool(toolID);
				break;
			default:  //selection tool
				handleRoiMouseDown(e);
		}
	}

	protected void setupScroll(int ox, int oy) {
		xMouseStart = ox;
		yMouseStart = oy;
		xSrcStart = srcRect.x;
		ySrcStart = srcRect.y;
	}

	protected void handlePopupMenu(MouseEvent e) {
		if (IJ.debugMode) IJ.log("show popup: " + (e.isPopupTrigger()?"true":"false"));
		int x = e.getX();
		int y = e.getY();
		Roi roi = imp.getRoi();
		if (roi!=null && (roi.getType()==Roi.POLYGON || roi.getType()==Roi.POLYLINE || roi.getType()==Roi.ANGLE)
		&& roi.getState()==roi.CONSTRUCTING) {
			roi.handleMouseUp(x, y); // simulate double-click to finalize
			roi.handleMouseUp(x, y); // polygon or polyline selection
			return;
		}
		PopupMenu popup = Menus.getPopupMenu();
		if (popup!=null) {
			add(popup);
			if (IJ.isMacOSX()) IJ.wait(10);
			popup.show(this, x, y);
		}
	}
	
	public void mouseExited(MouseEvent e) {
		//autoScroll(e);
		ImageWindow win = imp.getWindow();
		if (win!=null)
			setCursor(defaultCursor);
		IJ.showStatus("");
	}

	/*
	public void autoScroll(MouseEvent e) {
		Roi roi = imp.getRoi();
		if (roi==null || roi.getState()!=roi.CONSTRUCTING || srcRect.width>=imageWidth || srcRect.height>=imageHeight
		|| !(roi.getType()==Roi.POLYGON || roi.getType()==Roi.POLYLINE || roi.getType()==Roi.ANGLE))
			return;
		int sx = e.getX();
		int sy = e.getY();
		xMouseStart = srcRect.x+srcRect.width/2;
		yMouseStart = srcRect.y+srcRect.height/2;
		Rectangle r = roi.getBounds();
		Dimension size = getSize();
		int deltax=0, deltay=0;
		if (sx<0)
			deltax = srcRect.width/4;
		else if (sx>size.width)
			deltax = -srcRect.width/4;
		if (sy<0)
			deltay = srcRect.height/4;
		else if (sy>size.height)
			deltay = -srcRect.height/4;
		//IJ.log("autoscroll: "+sx+" "+sy+" "+deltax+" "+deltay+" "+r);
		scroll(screenX(xMouseStart+deltax), screenY(yMouseStart+deltay));
	}
	*/

	public void mouseDragged(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();
		xMouse = offScreenX(x);
		yMouse = offScreenY(y);
		flags = e.getModifiers();
		//IJ.log("mouseDragged: "+flags);
		if (flags==0)  // workaround for Mac OS 9 bug
			flags = InputEvent.BUTTON1_MASK;
		if (Toolbar.getToolId()==Toolbar.HAND || IJ.spaceBarDown())
			scroll(x, y);
		else {
			IJ.setInputEvent(e);
			Roi roi = imp.getRoi();
			if (roi != null)
				roi.handleMouseDrag(x, y, flags);
		}
	}

	void handleRoiMouseDown(MouseEvent e) {
		int sx = e.getX();
		int sy = e.getY();
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		Roi roi = imp.getRoi();
		int handle = roi!=null?roi.isHandle(sx, sy):-1;		
		setRoiModState(e, roi, handle);
		if (roi!=null) {
			Rectangle r = roi.getBounds();
			int type = roi.getType();
			if (type==Roi.RECTANGLE && r.width==imp.getWidth() && r.height==imp.getHeight()
			&& roi.getPasteMode()==Roi.NOT_PASTING) {
				imp.killRoi();
				return;
			}
			if (handle>=0) {
				roi.mouseDownInHandle(handle, sx, sy);
				return;
			}
			if (roi.contains(ox, oy)) {
				if (roi.modState==Roi.NO_MODS)
					roi.handleMouseDown(sx, sy);
				else {
					imp.killRoi();
					imp.createNewRoi(sx,sy);
				}
				return;
			}
			if ((type==Roi.POLYGON || type==Roi.POLYLINE || type==Roi.ANGLE)
			&& roi.getState()==roi.CONSTRUCTING)
				return;
			if (Toolbar.getToolId()==Toolbar.POLYGON && !(IJ.shiftKeyDown()||IJ.altKeyDown())) {
				imp.killRoi();
				return;
			}
		}
		imp.createNewRoi(sx,sy);
	}
	
	void setRoiModState(MouseEvent e, Roi roi, int handle) {
		if (roi==null || (handle>=0 && roi.modState==Roi.NO_MODS))
			return;
		if (roi.state==Roi.CONSTRUCTING)
			return;
		int tool = Toolbar.getToolId();
		if (tool>Toolbar.FREEROI && tool!=Toolbar.WAND && tool!=Toolbar.POINT)
			{roi.modState = Roi.NO_MODS; return;}
		if (e.isShiftDown())
			roi.modState = Roi.ADD_TO_ROI;
		else if (e.isAltDown())
			roi.modState = Roi.SUBTRACT_FROM_ROI;
		else
			roi.modState = Roi.NO_MODS;
		//IJ.log("setRoiModState: "+roi.modState+" "+ roi.state);
	}

	public void mouseReleased(MouseEvent e) {
		flags = e.getModifiers();
		flags &= ~InputEvent.BUTTON1_MASK; // make sure button 1 bit is not set
		flags &= ~InputEvent.BUTTON2_MASK; // make sure button 2 bit is not set
		flags &= ~InputEvent.BUTTON3_MASK; // make sure button 3 bit is not set
		Roi roi = imp.getRoi();
		if (roi != null) {
			Rectangle r = roi.getBounds();
			int type = roi.getType();
			if ((r.width==0 || r.height==0)
			&& !(type==Roi.POLYGON || type==Roi.POLYLINE || type==Roi.ANGLE)
			&& !(roi instanceof TextRoi)
			&& roi.getState()==roi.CONSTRUCTING
			&& type!=roi.POINT)
				imp.killRoi();
			else
				roi.handleMouseUp(e.getX(), e.getY());
		}
	}

	public void mouseMoved(MouseEvent e) {
		if (ij==null) return;
		int sx = e.getX();
		int sy = e.getY();
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		flags = e.getModifiers();
		setCursor(sx, sy, ox, oy);
		IJ.setInputEvent(e);
		Roi roi = imp.getRoi();
		if (roi!=null && (roi.getType()==Roi.POLYGON || roi.getType()==Roi.POLYLINE || roi.getType()==Roi.ANGLE) 
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