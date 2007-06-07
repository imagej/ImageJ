package ij.gui;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.KeyEvent;
import ij.*;
import ij.process.*;
import ij.measure.*;

/** A rectangular region of interest and superclass for the other ROI classes. */
public class Roi extends Object implements Cloneable {

	public static final int CONSTRUCTING=0, MOVING=1, RESIZING=2, NORMAL=3; // States
	public static final int RECTANGLE=0, OVAL=1, POLYGON=2, FREEROI=3, TRACED_ROI=4, LINE=5, POLYLINE=6, FREELINE=7; // Types
	public static final int HANDLE_SIZE = 4; 
	
	int startX, startY, x, y, width, height;
	int state;
	
	public static Roi previousRoi = null;
	protected static Color ROIColor = Prefs.getColor(Prefs.ROICOLOR,Color.yellow);
	protected static int pasteMode = Blitter.COPY;
	
	protected int type;
	protected int xMax, yMax;
	protected ImagePlus imp;
	protected ImageCanvas ic;
	protected int oldX, oldY, oldWidth, oldHeight;
	protected int clipX, clipY, clipWidth, clipHeight;
	protected ImagePlus clipboard = null;
	protected boolean constrain = false;
	protected boolean updateFullWindow;

	public Roi(int x, int y, int width, int height, ImagePlus imp) {
		setImage(imp);
		if (width>xMax) width = xMax;
		if (height>yMax) height = yMax;
		setLocation(x, y);
		this.width = width;
		this.height = height;
		oldWidth=width;
		oldHeight=height;
		clipX = x;
		clipY = y;
		clipWidth = width;
		clipHeight = height;
		state = NORMAL;
		Graphics g = ic.getGraphics();
		draw(g);
		g.dispose();
		type = RECTANGLE;
	}
	
	public Roi(int x, int y, ImagePlus imp) {
		if (imp!=null)
			setImage(imp);
		setLocation(x, y);
		width = 0;
		height = 0;
		state = CONSTRUCTING;
		type = RECTANGLE;
	}
	
	public void setLocation(int x, int y) {
		if (x<0) x = 0;
		if (y<0) y = 0;
		if ((x+width)>xMax) x = xMax-width;
		if ((y+height)>yMax) y = yMax-height;
		//if (IJ.debugMode) IJ.write(imp.getTitle() + ": Roi.setlocation(" + x + "," + y + ")");
		this.x = x;
		this.y = y;
		startX = x; startY = y;
		oldX = x; oldY = y; oldWidth=0; oldHeight=0;
	}
	
	public void setImage(ImagePlus imp) {
		this.imp = imp;
		ImageWindow win = imp.getWindow();
		if (win!=null)
			ic = win.getCanvas();
		xMax = imp.getWidth();
		yMax = imp.getHeight();
	}
	
	public int getType() {
		return type;
	}
	
	public int getState() {
		return state;
	}
	
	/** Returns the perimeter length. */
	public double getLength() {
		return 2*(width+height);
	}
	
	public Rectangle getBoundingRect() {
		return new Rectangle(x, y, width, height);
	}
	
	/** Returns a copy of this roi. See Thinking is Java by Bruce Eckel
	    (www.eckelobjects.com) for a good description of object cloning. */
	public synchronized Object clone() {
		try { 
			Roi r = (Roi)super.clone();
			r.previousRoi = null;
			r.imp = null;
			return r;
		}
		catch (CloneNotSupportedException e) {return null;}
	}
	
	protected void grow(int xNew, int yNew) {
		if (clipboard!=null)
			return;
		if (xNew < 0) xNew = 0;
		if (yNew < 0) yNew = 0;
		if (constrain) {
			// constrain selection to be square
			int dx, dy, d;
			dx = xNew - x;
			dy = yNew - y;
			if (dx<dy)
				d = dx;
			else
				d = dy;
			xNew = x + d;
			yNew = y + d;
		}
		width = Math.abs(xNew - startX);
		height = Math.abs(yNew - startY);
		x = (xNew>=startX)?startX:startX - width;
		y = (yNew>=startY)?startY:startY - height;
		if ((x+width) > xMax)
			width = xMax-x;
		if ((y+height) > yMax)
			height = yMax-y;
		updateClipRect();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x;
		oldY = y;
		oldWidth = width;
		oldHeight = height;
	}

	void move(int xNew, int yNew) {
		x += xNew - startX;
		y += yNew - startY;
		if (x < 0) x = 0;
		if (y < 0) y = 0;
		if ((x+width)>xMax) x = xMax-width;
		if ((y+height)>yMax) y = yMax-height;
		startX = xNew;
		startY = yNew;
		updateClipRect();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x; oldY = y;
	}

	/** Nudge ROI one pixel on arrow key press. */
	public void nudge(int key) {
		switch(key) {
        	case KeyEvent.VK_UP:
        		y--;
				if (y<0) y = 0;
				break;
        	case KeyEvent.VK_DOWN:
        		y++;
				if ((y+height)>=yMax) y = yMax-height;
				break;
			case KeyEvent.VK_LEFT:
				x--;
				if (x<0) x = 0;
				break;
			case KeyEvent.VK_RIGHT:
        		x++;
				if ((x+width)>=xMax) x = xMax-width;
				break;
        }
		updateClipRect();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x; oldY = y;
	}
	
	/** Nudge lower right corner of rectangular and oval ROIs by
		one pixel based on arrow key press. */
	public void nudgeCorner(int key) {
		if (type>OVAL || clipboard!=null)
			return;
		switch(key) {
        	case KeyEvent.VK_UP:
        		height--;
				if (height<1) height = 1;
				break;
        	case KeyEvent.VK_DOWN:
        		height++;
				if ((y+height) > yMax) height = yMax-y;
				break;
			case KeyEvent.VK_LEFT:
				width--;
				if (width<1) width = 1;
				break;
			case KeyEvent.VK_RIGHT:
        		width++;
				if ((x+width) > xMax) width = xMax-x;
				break;
        }
		updateClipRect();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x; oldY = y;
	}
	
	protected void updateClipRect() {
	// Finds the union of current and previous roi
		clipX = (x<=oldX)?x:oldX;
		clipY = (y<=oldY)?y:oldY;
		clipWidth = ((x+width>=oldX+oldWidth)?x+width:oldX+oldWidth) - clipX + 1;
		clipHeight = ((y+height>=oldY+oldHeight)?y+height:oldY+oldHeight) - clipY + 1;
		double mag = ic.getMagnification();
		if (mag<1.0) {
			clipWidth += (int)(1/mag);
			clipHeight += (int)(1/mag);
		}
	}
		
	protected void handleMouseDrag(int sx, int sy, boolean constrain) {
		this.constrain = constrain;
		int ox = ic.offScreenX(sx);
		int oy = ic.offScreenY(sy);
		switch(state) {
			case CONSTRUCTING:
				grow(ox, oy);
				break;
			case MOVING:
				move(ox, oy);
				break;
			default:
				break;
		}
	}

	int getHandleSize() {
		double mag = ic.getMagnification();
		double size = HANDLE_SIZE/mag;
		return (int)(size*mag);
	}
	
	public void draw(Graphics g) {
		double mag = ic.getMagnification();
		g.setColor(ROIColor);
		int sx1 = ic.screenX(x);
		int sy1 = ic.screenY(y);
		int sx2 = sx1+(int)(width*mag)-1;
		int sy2 = sy1+(int)(height*mag)-1;
		g.drawRect(sx1, sy1, sx2-sx1, sy2-sy1);
		//IJ.write(sx1 + " " + sy1 + " " + (sx2-sx1+1) + " " + (sy2-sy1+1));
		if ((sx2-sx1)>2 && (sy2-sy1)>2 ) {
			int handleSize = getHandleSize();
			g.fillRect(sx2-HANDLE_SIZE, sy2-HANDLE_SIZE, HANDLE_SIZE, HANDLE_SIZE);
		}
		showStatus();
		if (updateFullWindow)
			{updateFullWindow = false; imp.draw();}
	}

	public void drawPixels() {
		endPaste();
		ImageProcessor ip = imp.getProcessor();
		ip.moveTo(x, y);
		ip.lineTo(x+width-1, y);
		ip.lineTo(x+width-1, y+height-1);
		ip.lineTo(x, y+height-1);
		ip.lineTo(x, y);
		if (Line.getWidth()>1)
			updateFullWindow = true;
	}


	public boolean contains(int x, int y) {
		Rectangle r = new Rectangle(this.x, this.y, width, height);
		return r.contains(x, y);
	}
		
	boolean insideHandle(int sx, int sy) {
		if (type!=RECTANGLE)
			return false;
		return sx>=(ic.screenX(x+width)-HANDLE_SIZE*2) && sy>=(ic.screenY(y+height)-HANDLE_SIZE*2);
	}

	protected void handleMouseDown(int sx, int sy) {
		if (state==NORMAL) {
			if (insideHandle(sx, sy)) {
				state = CONSTRUCTING;
				startX = x;
				startY = y;
			} else {
				state = MOVING;
				startX = ic.offScreenX(sx);
				startY = ic.offScreenY(sy);
			}
			showStatus();
		}
	}
		
	protected void handleMouseUp(int screenX, int screenY) {
		state = NORMAL;
	}


	protected void showStatus() {
		String value;
		if (state!=CONSTRUCTING && type==RECTANGLE && width<=25 && height<=25) {
			ImageProcessor ip = imp.getProcessor();
			double v = ip.getPixelValue(x,y);
			int digits = (imp.getType()==ImagePlus.GRAY8||imp.getType()==ImagePlus.GRAY16)?0:2;
			value = ", value="+IJ.d2s(v,digits);
		} else
			value = "";
		Calibration cal = imp.getCalibration();
		if (cal.scaled())
			IJ.showStatus("Location = (" + IJ.d2s(x*cal.pixelWidth) + "," + IJ.d2s(y*cal.pixelHeight)
			+ "), width=" + IJ.d2s(width*cal.pixelWidth) + ", height=" + IJ.d2s(height*cal.pixelHeight)+value);
		else
			IJ.showStatus("Location = ("+x+","+y+"), width="+width+", height="+height+value);

	}
	
	
	public int[] getMask() {
		return null;
	}
	
	void startPaste(ImagePlus clipboard) {
		IJ.showStatus("Pasting...");
		imp.getProcessor().snapshot();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		Toolbar.getInstance().setTool(Toolbar.RECTANGLE);
		this.clipboard = clipboard;
	}
	
	void updatePaste() {
		if (clipboard!=null) {
			ImageProcessor ip = imp.getProcessor();
			ip.reset();
			ip.copyBits(clipboard.getProcessor(), x, y, pasteMode);
			ic.setImageUpdated();
		}
	}

	public void endPaste() {
		if (clipboard!=null) {
			ImageProcessor ip = imp.getProcessor();
			if (pasteMode!=Blitter.COPY) ip.reset();
			ip.copyBits(clipboard.getProcessor(), x, y, pasteMode);
			ip.snapshot();
			clipboard = null;
			imp.updateAndDraw();
			Undo.setup(Undo.FILTER, imp);
		}
	}
	
	public void abortPaste() {
		clipboard = null;
		imp.getProcessor().reset();
		imp.updateAndDraw();
	}
	
	/** Returns the angle in degrees between the specified line and a horizontal line. */
	public double getAngle(int x1, int y1, int x2, int y2) {
		final int q1=0, q2orq3=2, q4=3; //quadrant
		int quadrant;
		double dx = x2-x1;
		double dy = y1-y2;
		double angle;

		if (dx!=0.0)
			angle = Math.atan(dy/dx);
		else {
			if (dy>=0.0)
				angle = Math.PI/2.0;
			else
				angle = -Math.PI/2.0;
		}
		angle = (180.0/Math.PI)*angle;
		if (dx>=0.0 && dy>=0.0)
			quadrant = q1;
		else if (dx<0.0)
			quadrant = q2orq3;
		else
			quadrant = q4;
		switch (quadrant) {
			case q1: 
				break;
			case q2orq3: 
				angle = angle+180.0;
				break;
			case q4: 
				angle = angle+360.0;
		}
		return angle;
	}
	
	/** Returns the color used for drawing ROI outlines. */
	public static Color getColor() {
		return ROIColor;
	}

	/** Sets the color used for ROI outline to the specified value. */
	public static void setColor(Color c) {
		ROIColor = c;
	}
	
	/** Sets the Paste transfer mode. */
	public static void setPasteMode(int transferMode) {
		int previousMode = pasteMode;
		pasteMode = transferMode;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			return;
		if (previousMode!=Blitter.COPY) {
			ImageProcessor ip = imp.getProcessor();
			ip.reset();
			if (pasteMode!=Blitter.COPY) {
				//ip.copyBits(clipboard.getProcessor(), x, y, pasteMode);
				//ic.setImageUpdated();
			}
		}
		imp.updateAndDraw();
	}

	public String toString() {
		return ("Roi: type="+type+", x="+x+", y="+y+", width="+width+", height="+height);
	}

}
