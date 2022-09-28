package ij.gui;

import java.awt.*;
import java.util.Properties;
import java.awt.image.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.*;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.RoiManager;
import ij.plugin.filter.Analyzer;
import ij.plugin.tool.PlugInTool;
import ij.macro.*;
import ij.*;
import ij.util.*;
import ij.text.*;
import java.awt.event.*;
import java.util.*;
import java.awt.geom.*;
import java.util.concurrent.atomic.AtomicBoolean;


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
	
	private boolean showCursorStatus = true;
	private int sx2, sy2;
	private boolean disablePopupMenu;
	private static Color zoomIndicatorColor;
	private static Font smallFont, largeFont;
	private Font font;
	private Rectangle[] labelRects;
    private boolean maxBoundsReset;
    private Overlay showAllOverlay;
    private static final int LIST_OFFSET = 100000;
    private static Color showAllColor = Prefs.getColor(Prefs.SHOW_ALL_COLOR, new Color(0, 255, 255));
    private Color defaultColor = showAllColor;
    private static Color labelColor, bgColor;
    private int resetMaxBoundsCount;
    private Roi currentRoi;
    private int mousePressedX, mousePressedY;
    private long mousePressedTime;
    private boolean overOverlayLabel;

    /** If the mouse moves less than this in screen pixels, successive zoom operations are on the same image pixel */
	protected final static int MAX_MOUSEMOVE_ZOOM = 10;
	/** Screen coordinates where the last zoom operation was done (initialized to impossible value) */
	protected int lastZoomSX = -9999999;
	protected int lastZoomSY = -9999999;
	/** Image (=offscreen) coordinates where the cursor was moved to for zooming */
	protected int zoomTargetOX = -1;
	protected int zoomTargetOY;

	protected ImageJ ij;
	protected double magnification;
	protected int dstWidth, dstHeight;

	protected int xMouseStart;
	protected int yMouseStart;
	protected int xSrcStart;
	protected int ySrcStart;
	protected int flags;
	
	private Image offScreenImage;
	private int offScreenWidth = 0;
	private int offScreenHeight = 0;
	private boolean mouseExited = true;
	private boolean customRoi;
	private boolean drawNames;
	private AtomicBoolean paintPending;
	private boolean scaleToFit;
	private boolean painted;
	private boolean hideZoomIndicator;
	private boolean flattening;
	private Timer pressTimer;
	private PopupMenu roiPopupMenu;
	private static int longClickDelay = 1000; //ms

		
	public ImageCanvas(ImagePlus imp) {
		this.imp = imp;
		paintPending = new AtomicBoolean(false);
		ij = IJ.getInstance();
		int width = imp.getWidth();
		int height = imp.getHeight();
		imageWidth = width;
		imageHeight = height;
		srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
		setSize(imageWidth, imageHeight);
		magnification = 1.0;
 		addMouseListener(this);
 		addMouseMotionListener(this);
 		addKeyListener(ij);  // ImageJ handles keyboard shortcuts
 		setFocusTraversalKeysEnabled(false);
		//setScaleToFit(true);
	}
		
	void updateImage(ImagePlus imp) {
		this.imp = imp;
		int width = imp.getWidth();
		int height = imp.getHeight();
		imageWidth = width;
		imageHeight = height;
		srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
		setSize(imageWidth, imageHeight);
		magnification = 1.0;
	}

	/** Update this ImageCanvas to have the same zoom and scale settings as the one specified. */
	void update(ImageCanvas ic) {
		if (ic==null || ic==this || ic.imp==null)
			return;
		if (ic.imp.getWidth()!=imageWidth || ic.imp.getHeight()!=imageHeight)
			return;
		srcRect = new Rectangle(ic.srcRect.x, ic.srcRect.y, ic.srcRect.width, ic.srcRect.height);
		setMagnification(ic.magnification);
		setSize(ic.dstWidth, ic.dstHeight);
	}

	/** Sets the region of the image (in pixels) to be displayed. */
	public void setSourceRect(Rectangle r) {
		if (r==null)
			return;
		r = new Rectangle(r.x, r.y, r.width, r.height);
		imageWidth = imp.getWidth();
		imageHeight = imp.getHeight();
		if (r.x<0) r.x = 0;
		if (r.y<0) r.y = 0;
		if (r.width<1)
			r.width = 1;
		if (r.height<1)
			r.height = 1;
		if (r.width>imageWidth)
			r.width = imageWidth;
		if (r.height>imageHeight)
			r.height = imageHeight;
		if (r.x+r.width>imageWidth)
			r.x = imageWidth-r.width;
		if (r.y+r.height>imageHeight)
			r.y = imageHeight-r.height;
		if (srcRect==null)
			srcRect = r;
		else {
			srcRect.x = r.x;
			srcRect.y = r.y;
			srcRect.width = r.width;
			srcRect.height = r.height;
		}
		if (dstWidth==0) {
			Dimension size = getSize();
			dstWidth = size.width;
			dstHeight = size.height;
		}
		magnification = (double)dstWidth/srcRect.width;
		imp.setTitle(imp.getTitle());
		if (IJ.debugMode) IJ.log("setSourceRect: "+magnification+" "+(int)(srcRect.height*magnification+0.5)+" "+dstHeight+" "+srcRect);
	}

	void setSrcRect(Rectangle srcRect) {
		setSourceRect(srcRect);
	}
		
	public Rectangle getSrcRect() {
		return srcRect;
	}
	
	/** Obsolete; replaced by setSize() */
	public void setDrawingSize(int width, int height) {
		dstWidth = width;
		dstHeight = height;
		setSize(dstWidth, dstHeight);
	}
		
	public void setSize(int width, int height) {
		super.setSize(width, height);
		dstWidth = width;
		dstHeight = height;
	}

	/** ImagePlus.updateAndDraw calls this method to force the paint()
		method to update the image from the ImageProcessor. */
	public void setImageUpdated() {
		imageUpdated = true;
	}

	public void setPaintPending(boolean state) {
		paintPending.set(state);
	}
	
	public boolean getPaintPending() {
		return paintPending.get();
	}

	public void update(Graphics g) {
		paint(g);
	}
	
	//public void repaint() {
	//	super.repaint();
	//	//if (IJ.debugMode) IJ.log("repaint: "+imp);
	//}
	
    public void paint(Graphics g) {
		// if (IJ.debugMode) IJ.log("paint: "+imp);
		painted = true;
		Roi roi = imp.getRoi();
		Overlay overlay = imp.getOverlay();
		if (roi!=null || overlay!=null || showAllOverlay!=null || Prefs.paintDoubleBuffered || (IJ.isLinux() && magnification<0.25)) {
			// Use double buffering to avoid flickering of ROIs and to work around
			// a Linux problem with large images not showing at low magnification.
			if (roi!=null)
				roi.updatePaste();
			if (imageWidth!=0) {
				paintDoubleBuffered(g);
				setPaintPending(false);
				return;
			}
		}
		try {
			if (imageUpdated) {
				imageUpdated = false;
				imp.updateImage();
			}
			setInterpolation(g, Prefs.interpolateScaledImages);
			Image img = imp.getImage();
			if (img!=null)
 				g.drawImage(img, 0, 0, (int)(srcRect.width*magnification+0.5), (int)(srcRect.height*magnification+0.5),
				srcRect.x, srcRect.y, srcRect.x+srcRect.width, srcRect.y+srcRect.height, null);
			if (overlay!=null)
				drawOverlay(overlay, g);
			if (showAllOverlay!=null)
				drawOverlay(showAllOverlay, g);
			if (roi!=null) drawRoi(roi, g);
			if (srcRect.width<imageWidth || srcRect.height<imageHeight)
				drawZoomIndicator(g);
			//if (IJ.debugMode) showFrameRate(g);
		} catch(OutOfMemoryError e) {IJ.outOfMemory("Paint");}
		setPaintPending(false);
    }
    
	private void setInterpolation(Graphics g, boolean interpolate) {
		if (magnification==1)
			return;
		else if (magnification<1.0 || interpolate) {
			Object value = RenderingHints.VALUE_RENDER_QUALITY;
			((Graphics2D)g).setRenderingHint(RenderingHints.KEY_RENDERING, value);
		} else if (magnification>1.0) {
			Object value = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
			((Graphics2D)g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, value);
		}
	}

    private void drawRoi(Roi roi, Graphics g) {
		if (roi==currentRoi) {
			Color lineColor = roi.getStrokeColor();
			Color fillColor = roi.getFillColor();
			float lineWidth = roi.getStrokeWidth();
			roi.setStrokeColor(null);
			roi.setFillColor(null);
			boolean strokeSet = roi.getStroke()!=null;
			if (strokeSet)
				roi.setStrokeWidth(1);
			roi.draw(g);
			roi.setStrokeColor(lineColor);
			if (strokeSet)
				roi.setStrokeWidth(lineWidth);
			roi.setFillColor(fillColor);
			currentRoi = null;
		} else
			roi.draw(g);
    }
           
	public int getSliceNumber(String label) {
		if (label==null) return 0;
		int slice = 0;
		if (label.length()>=14 && label.charAt(4)=='-' && label.charAt(9)=='-')
			slice = (int)Tools.parseDouble(label.substring(0,4),0);
		else if (label.length()>=17 && label.charAt(5)=='-' && label.charAt(11)=='-')
			slice = (int)Tools.parseDouble(label.substring(0,5),0);
		else if (label.length()>=20 && label.charAt(6)=='-' && label.charAt(13)=='-')
			slice = (int)Tools.parseDouble(label.substring(0,6),0);
		return slice;
	}

	private void drawOverlay(Overlay overlay, Graphics g) {
		if (imp!=null && imp.getHideOverlay() && overlay!=showAllOverlay)
			return;
		flattening = imp!=null && ImagePlus.flattenTitle.equals(imp.getTitle());
		if (imp!=null && showAllOverlay!=null && overlay!=showAllOverlay)
			overlay.drawLabels(false);
		Color labelColor = overlay.getLabelColor();
		if (labelColor==null) labelColor = Color.white;
		initGraphics(overlay, g, labelColor, Roi.getColor());
		int n = overlay.size();
		//if (IJ.debugMode) IJ.log("drawOverlay: "+n);
		int currentImage = imp!=null?imp.getCurrentSlice():-1;
		int stackSize = imp.getStackSize();
		if (stackSize==1)
			currentImage = -1;
		int channel=0, slice=0, frame=0;
		boolean hyperstack = imp.isHyperStack();
		if (hyperstack) {
			channel = imp.getChannel();
			slice = imp.getSlice();
			frame = imp.getFrame();
		}
		drawNames = overlay.getDrawNames() && overlay.getDrawLabels();
		boolean drawLabels = drawNames || overlay.getDrawLabels();
		if (drawLabels)
			labelRects = new Rectangle[n];
		else
			labelRects = null;
		font = overlay.getLabelFont();
		if (overlay.scalableLabels() && font!=null) {
			double mag = getMagnification();
			if (mag!=1.0)
				font = font.deriveFont((float)(font.getSize()*mag));
		}
		Roi activeRoi = imp.getRoi();
		boolean roiManagerShowAllMode = overlay==showAllOverlay && !Prefs.showAllSliceOnly;
		for (int i=0; i<n; i++) {
			if (overlay==null) break;
			Roi roi = overlay.get(i);
			if (roi==null) break;
			int c = roi.getCPosition();
			int z = roi.getZPosition();
			int t = roi.getTPosition();
			if (hyperstack) {
				int position = roi.getPosition();
				//IJ.log(c+" "+z+" "+t+"  "+position+" "+roiManagerShowAllMode);
				if (position>0) {
					if (z==0 && imp.getNSlices()>1)
						z = position;
					else if (t==0)
						t = position;
				}
				if (((c==0||c==channel) && (z==0||z==slice) && (t==0||t==frame)) || roiManagerShowAllMode)
					drawRoi(g, roi, drawLabels?i+LIST_OFFSET:-1);
			} else {
				int position = stackSize>1?roi.getPosition():0;
				if (position==0 && c==1) {
					if (z==1)
						position = t;
					else if (t==1)
						position = z;
				}
				if (position==0 && stackSize>1)
					position = getSliceNumber(roi.getName());
				if (position>0 && imp.getCompositeMode()==IJ.COMPOSITE)
					position = 0;
				//IJ.log(position+"  "+currentImage+" "+roiManagerShowAllMode+" "+c+" "+z+" "+t);
				if (position==0 || position==currentImage || roiManagerShowAllMode)
					drawRoi(g, roi, drawLabels?i+LIST_OFFSET:-1);
			}
		}
		((Graphics2D)g).setStroke(Roi.onePixelWide);
		drawNames = false;
		font = null;
	}
    	
	void drawOverlay(Graphics g) {
		drawOverlay(imp.getOverlay(), g);
	}

    private void initGraphics(Overlay overlay, Graphics g, Color textColor, Color defaultColor) {
		if (smallFont==null) {
			smallFont = new Font("SansSerif", Font.PLAIN, 9);
			largeFont = IJ.font12;
		}
		if (textColor!=null) {
			labelColor = textColor;
			if (overlay!=null && overlay.getDrawBackgrounds()) {
				double brightness = (labelColor.getRed()+labelColor.getGreen()+labelColor.getBlue())/3.0;
				if (labelColor==Color.green) brightness = 255;
				bgColor = brightness<=85?Color.white:Color.black;
			} else
				bgColor = null;
		} else {
			int red = defaultColor.getRed();
			int green = defaultColor.getGreen();
			int blue = defaultColor.getBlue();
			if ((red+green+blue)/3<128)
				labelColor = Color.white;
			else
				labelColor = Color.black;
			bgColor = defaultColor;
		}
		this.defaultColor = defaultColor;
		Font font = overlay!=null?overlay.getLabelFont():null;
		if (font!=null && font.getSize()>12)
			((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(defaultColor);
    }
    
    void drawRoi(Graphics g, Roi roi, int index) {
		ImagePlus imp2 = roi.getImage();
		roi.setImage(imp);
		Color saveColor = roi.getStrokeColor();
		if (saveColor==null)
			roi.setStrokeColor(defaultColor);
		if (roi.getStroke()==null)
			((Graphics2D)g).setStroke(Roi.onePixelWide);
		if (roi instanceof TextRoi)
			((TextRoi)roi).drawText(g);
		else
			roi.drawOverlay(g);
		roi.setStrokeColor(saveColor);
		if (index>=0) {
			if (roi==currentRoi)
				g.setColor(Roi.getColor());
			else
				g.setColor(defaultColor);
			drawRoiLabel(g, index, roi);
		}
		if (imp2!=null)
			roi.setImage(imp2);
		else
			roi.setImage(null);
    }
    
	void drawRoiLabel(Graphics g, int index, Roi roi) {
		if (roi.isCursor())
			return;
		boolean pointRoi = roi instanceof PointRoi;
		Rectangle r = roi.getBounds();
		int x = screenX(r.x);
		int y = screenY(r.y);
		double mag = getMagnification();
		int width = (int)(r.width*mag);
		int height = (int)(r.height*mag);
		int size = width>40 || height>40?12:9;
		int pointSize = 0;
		int crossSize = 0;
		if (pointRoi) {
			pointSize = ((PointRoi)roi).getSize();
			switch (pointSize) {
				case 0: case 1: size=9; break;
				case 2: case 3: size=10; break;
				case 4: size=12; break;
			}
			crossSize = pointSize + 10 + 2*pointSize;
		}
		if (font!=null) {
			g.setFont(font);
			size = font.getSize();
		} else if (size==12)
			g.setFont(largeFont);
		else
			g.setFont(smallFont);
		boolean drawingList = index >= LIST_OFFSET;
		if (drawingList) index -= LIST_OFFSET;
		String label = "" + (index+1);
		if (drawNames)
			label = roi.getName();
		if (label==null)
			return;
		FontMetrics metrics = g.getFontMetrics();
		int w = metrics.stringWidth(label);
		x = x + width/2 - w/2;
		y = y + height/2 + Math.max(size/2,6);
		int h = metrics.getAscent() + metrics.getDescent();
		int xoffset=0, yoffset=0;
		if (pointRoi) {
			xoffset = 6 + pointSize;
			yoffset = h - 6 + pointSize;
		}
		if (bgColor!=null) {
			int h2 = h;
			if (font!=null && font.getSize()>14)
				h2 = (int)(h2*0.8);
			g.setColor(bgColor);
			g.fillRoundRect(x-1+xoffset, y-h2+2+yoffset, w+1, h2-2, 5, 5);
		}
		if (labelRects!=null && index<labelRects.length) {
			if (pointRoi) {
				int x2 = screenX(r.x);
				int y2 = screenY(r.y);
				int crossSize2 = crossSize/2;
				labelRects[index] = new Rectangle(x2-crossSize2, y2-crossSize2, crossSize, crossSize);
			} else
				labelRects[index] = new Rectangle(x-3, y-h+1, w+4, h);
		}		
		//IJ.log("drawRoiLabel: "+" "+label+" "+x+" "+y+" "+flattening);
		g.setColor(labelColor);
		g.drawString(label, x+xoffset, y-2+yoffset);
		g.setColor(defaultColor);
	} 

	void drawZoomIndicator(Graphics g) {
		if (hideZoomIndicator)
			return;
		int x1 = 10;
		int y1 = 10;
		double aspectRatio = (double)imageHeight/imageWidth;
		int w1 = 64;
		if (aspectRatio>1.0)
			w1 = (int)(w1/aspectRatio);
		int h1 = (int)(w1*aspectRatio);
		if (w1<4) w1 = 4;
		if (h1<4) h1 = 4;
		int w2 = (int)(w1*((double)srcRect.width/imageWidth));
		int h2 = (int)(h1*((double)srcRect.height/imageHeight));
		if (w2<1) w2 = 1;
		if (h2<1) h2 = 1;
		int x2 = (int)(w1*((double)srcRect.x/imageWidth));
		int y2 = (int)(h1*((double)srcRect.y/imageHeight));
		if (zoomIndicatorColor==null)
			zoomIndicatorColor = new Color(128, 128, 255);
		g.setColor(zoomIndicatorColor);
		((Graphics2D)g).setStroke(Roi.onePixelWide);
		g.drawRect(x1, y1, w1, h1);
		if (w2*h2<=200 || w2<10 || h2<10)
			g.fillRect(x1+x2, y1+y2, w2, h2);
		else
			g.drawRect(x1+x2, y1+y2, w2, h2);
	}

	// Use double buffer to reduce flicker when drawing complex ROIs.
	// Author: Erik Meijering
	void paintDoubleBuffered(Graphics g) {
		final int srcRectWidthMag = (int)(srcRect.width*magnification+0.5);
		final int srcRectHeightMag = (int)(srcRect.height*magnification+0.5);
		if (offScreenImage==null || offScreenWidth!=srcRectWidthMag || offScreenHeight!=srcRectHeightMag) {
			offScreenImage = createImage(srcRectWidthMag, srcRectHeightMag);
			offScreenWidth = srcRectWidthMag;
			offScreenHeight = srcRectHeightMag;
		}
		Roi roi = imp.getRoi();
		try {
			if (imageUpdated) {
				imageUpdated = false;
				imp.updateImage();
			}
			Graphics offScreenGraphics = offScreenImage.getGraphics();
			setInterpolation(offScreenGraphics, Prefs.interpolateScaledImages);
			Image img = imp.getImage();
			if (img!=null)
				offScreenGraphics.drawImage(img, 0, 0, srcRectWidthMag, srcRectHeightMag,
					srcRect.x, srcRect.y, srcRect.x+srcRect.width, srcRect.y+srcRect.height, null);
			Overlay overlay = imp.getOverlay();
			if (overlay!=null)
				drawOverlay(overlay, offScreenGraphics);
			if (showAllOverlay!=null)
				drawOverlay(showAllOverlay, offScreenGraphics);
			if (roi!=null)
				drawRoi(roi, offScreenGraphics);
			if (srcRect.width<imageWidth || srcRect.height<imageHeight)
				drawZoomIndicator(offScreenGraphics);
			//if (IJ.debugMode) showFrameRate(offScreenGraphics);
			g.drawImage(offScreenImage, 0, 0, null);
		}
		catch(OutOfMemoryError e) {IJ.outOfMemory("Paint");}
	}
	
	public void resetDoubleBuffer() {
		offScreenImage = null;
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

	/** Returns the current cursor location in image coordinates. */
	public Point getCursorLoc() {
		return new Point(xMouse, yMouse);
	}
	
	/** Returns 'true' if the cursor is over this image. */
	public boolean cursorOverImage() {
		return !mouseExited;
	}

	/** Returns the mouse event modifiers. */
	public int getModifiers() {
		return flags;
	}
	
	/** Returns the ImagePlus object that is associated with this ImageCanvas. */
	public ImagePlus getImage() {
		return imp;
	}

	/** Sets the cursor based on the current tool and cursor location. */
	public void setCursor(int sx, int sy, int ox, int oy) {
		xMouse = ox;
		yMouse = oy;
		mouseExited = false;
		Roi roi = imp.getRoi();
		ImageWindow win = imp.getWindow();
		overOverlayLabel = false;
		if (win==null)
			return;
		if (IJ.spaceBarDown()) {
			setCursor(handCursor);
			return;
		}
		int id = Toolbar.getToolId();
		switch (id) {
			case Toolbar.MAGNIFIER:
				setCursor(moveCursor);
				break;
			case Toolbar.HAND:
				setCursor(handCursor);
				break;
			default:  //selection tool
				PlugInTool tool = Toolbar.getPlugInTool();
				boolean arrowTool = roi!=null && (roi instanceof Arrow) && tool!=null && "Arrow Tool".equals(tool.getToolName());
				if ((id>=Toolbar.CUSTOM1) && !arrowTool) {
					if (Prefs.usePointerCursor)
						setCursor(defaultCursor);
					else
						setCursor(crosshairCursor);
				} else if (roi!=null && roi.getState()!=roi.CONSTRUCTING && roi.isHandle(sx, sy)>=0) {
					setCursor(handCursor);
				} else if ((imp.getOverlay()!=null||showAllOverlay!=null) && overOverlayLabel(sx,sy,ox,oy) && (roi==null||roi.getState()!=roi.CONSTRUCTING)) {
					overOverlayLabel = true;
					setCursor(handCursor);
				} else if (Prefs.usePointerCursor || (roi!=null && roi.getState()!=roi.CONSTRUCTING && roi.contains(ox, oy)))
					setCursor(defaultCursor);
				else
					setCursor(crosshairCursor);
		}
	}
	
	private boolean overOverlayLabel(int sx, int sy, int ox, int oy) {
		Overlay o = showAllOverlay;
		if (o==null)
			o = imp.getOverlay();
		if (o==null || !o.isSelectable() || !o.isDraggable()|| !o.getDrawLabels() || labelRects==null)
			return false;
		for (int i=o.size()-1; i>=0; i--) {
			if (labelRects!=null&&labelRects[i]!=null&&labelRects[i].contains(sx,sy)) {
				Roi roi = imp.getRoi();
				if (roi==null || !roi.contains(ox,oy))
					return true;
				else
					return false;
			}
		}
		return false;
	}

	/**Converts a screen x-coordinate to an offscreen x-coordinate (nearest pixel center).*/
	public int offScreenX(int sx) {
		return srcRect.x + (int)(sx/magnification);
	}
		
	/**Converts a screen y-coordinate to an offscreen y-coordinate (nearest pixel center).*/
	public int offScreenY(int sy) {
		return srcRect.y + (int)(sy/magnification);
	}
	
	/**Converts a screen x-coordinate to an offscreen x-coordinate (Roi coordinate of nearest pixel border).*/
	public int offScreenX2(int sx) {
		return srcRect.x + (int)Math.round(sx/magnification);
	}
		
	/**Converts a screen y-coordinate to an offscreen y-coordinate (Roi coordinate of nearest pixel border).*/
	public int offScreenY2(int sy) {
		return srcRect.y + (int)Math.round(sy/magnification);
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
		setMagnification2(magnification);
	}
		
	void setMagnification2(double magnification) {
		if (magnification>32.0)
			magnification = 32.0;
		if (magnification<zoomLevels[0])
			magnification = zoomLevels[0];
		this.magnification = magnification;
		imp.setTitle(imp.getTitle());
	}

	/** Resizes the canvas when the user resizes the window. */
	void resizeCanvas(int width, int height) {
		ImageWindow win = imp.getWindow();
		//IJ.log("resizeCanvas: "+srcRect+" "+imageWidth+"  "+imageHeight+" "+width+"  "+height+" "+dstWidth+"  "+dstHeight+" "+win.maxBounds);
		if (!maxBoundsReset&& (width>dstWidth||height>dstHeight)&&win!=null&&win.maxBounds!=null&&width!=win.maxBounds.width-10) {
			if (resetMaxBoundsCount!=0)
				resetMaxBounds(); // Works around problem that prevented window from being larger than maximized size
			resetMaxBoundsCount++;
		}
		if (scaleToFit || IJ.altKeyDown())
			{fitToWindow(); return;}
		if (width>imageWidth*magnification)
			width = (int)(imageWidth*magnification);
		if (height>imageHeight*magnification)
			height = (int)(imageHeight*magnification);
		Dimension size = getSize();
		if (srcRect.width<imageWidth || srcRect.height<imageHeight || (painted&&(width!=size.width||height!=size.height))) {
			setSize(width, height);
			srcRect.width = (int)(dstWidth/magnification);
			srcRect.height = (int)(dstHeight/magnification);
			if ((srcRect.x+srcRect.width)>imageWidth)
				srcRect.x = imageWidth-srcRect.width;
			if ((srcRect.y+srcRect.height)>imageHeight)
				srcRect.y = imageHeight-srcRect.height;
			repaint();
		}
		//IJ.log("resizeCanvas2: "+srcRect+" "+dstWidth+"  "+dstHeight+" "+width+"  "+height);
	}
	
	public void fitToWindow() {
		ImageWindow win = imp.getWindow();
		if (win==null) return;
		Rectangle bounds = win.getBounds();
		Insets insets = win.getInsets();
		int sliderHeight = win.getSliderHeight();
		double xmag = (double)(bounds.width-(insets.left+insets.right+ImageWindow.HGAP*2))/srcRect.width;
		double ymag = (double)(bounds.height-(ImageWindow.VGAP*2+insets.top+insets.bottom+sliderHeight))/srcRect.height;
		setMagnification(Math.min(xmag, ymag));
		int width=(int)(imageWidth*magnification);
		int height=(int)(imageHeight*magnification);
		if (width==dstWidth&&height==dstHeight) return;
		srcRect=new Rectangle(0,0,imageWidth, imageHeight);
		setSize(width, height);
		getParent().doLayout();
	}
    
	void setMaxBounds() {
		if (maxBoundsReset) {
			maxBoundsReset = false;
			ImageWindow win = imp.getWindow();
			if (win!=null && !IJ.isLinux() && win.maxBounds!=null) {
				win.setMaximizedBounds(win.maxBounds);
				win.setMaxBoundsTime = System.currentTimeMillis();
			}
		}
	}

	void resetMaxBounds() {
		ImageWindow win = imp.getWindow();
		if (win!=null && (System.currentTimeMillis()-win.setMaxBoundsTime)>500L) {
			win.setMaximizedBounds(win.maxWindowBounds);
			maxBoundsReset = true;
		}
	}
	
	private static final double[] zoomLevels = {
		1/72.0, 1/48.0, 1/32.0, 1/24.0, 1/16.0, 1/12.0, 
		1/8.0, 1/6.0, 1/4.0, 1/3.0, 1/2.0, 0.75, 1.0, 1.5,
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

	/** Zooms in by making the window bigger. If it can't be made bigger, then makes 
		the source rectangle (srcRect) smaller and centers it on the position in the
		image where the cursor was when zooming has started.
		Note that sx and sy are screen coordinates. */
	public void zoomIn(int sx, int sy) {
		if (magnification>=32) return;
		scaleToFit = false;
	    boolean mouseMoved = sqr(sx-lastZoomSX) + sqr(sy-lastZoomSY) > MAX_MOUSEMOVE_ZOOM*MAX_MOUSEMOVE_ZOOM;
		lastZoomSX = sx;
		lastZoomSY = sy;
		if (mouseMoved || zoomTargetOX<0) {
		    boolean cursorInside = sx >= 0 && sy >= 0 && sx < dstWidth && sy < dstHeight;
		    zoomTargetOX = offScreenX(cursorInside ? sx : dstWidth/2); //where to zoom, offscreen (image) coordinates
		    zoomTargetOY = offScreenY(cursorInside ? sy : dstHeight/2);
		}
		double newMag = getHigherZoomLevel(magnification);
		int newWidth = (int)(imageWidth*newMag);
		int newHeight = (int)(imageHeight*newMag);
		Dimension newSize = canEnlarge(newWidth, newHeight);
		if (newSize!=null) {
			setSize(newSize.width, newSize.height);
			if (newSize.width!=newWidth || newSize.height!=newHeight)
				adjustSourceRect(newMag, zoomTargetOX, zoomTargetOY);
			else
				setMagnification(newMag);
			imp.getWindow().pack();
		} else // can't enlarge window
			adjustSourceRect(newMag, zoomTargetOX, zoomTargetOY);
		repaint();
		if (srcRect.width<imageWidth || srcRect.height<imageHeight)
			resetMaxBounds();
	}

	/** Centers the viewable area on offscreen (image) coordinates x, y */
	void adjustSourceRect(double newMag, int x, int y) {
		//IJ.log("adjustSourceRect1: "+newMag+" "+dstWidth+"  "+dstHeight);
		int w = (int)Math.round(dstWidth/newMag);
		if (w*newMag<dstWidth) w++;
		int h = (int)Math.round(dstHeight/newMag);
		if (h*newMag<dstHeight) h++;
		Rectangle r = new Rectangle(x-w/2, y-h/2, w, h);
		if (r.x<0) r.x = 0;
		if (r.y<0) r.y = 0;
		if (r.x+w>imageWidth) r.x = imageWidth-w;
		if (r.y+h>imageHeight) r.y = imageHeight-h;
		srcRect = r;
		setMagnification(newMag);
		//IJ.log("adjustSourceRect2: "+srcRect+" "+dstWidth+"  "+dstHeight);
	}

    /** Returns the size to which the window can be enlarged, or null if it can't be enlarged.
     *  <code>newWidth, newHeight</code> is the size needed for showing the full image
     *  at the magnification needed */
	protected Dimension canEnlarge(int newWidth, int newHeight) {
		if (IJ.altKeyDown())
			return null;
		ImageWindow win = imp.getWindow();
		if (win==null) return null;
		Rectangle r1 = win.getBounds();
		Insets insets = win.getInsets();
		Point loc = getLocation();
		if (loc.x>insets.left+5 || loc.y>insets.top+5) {
			r1.width = newWidth+insets.left+insets.right+ImageWindow.HGAP*2;
			r1.height = newHeight+insets.top+insets.bottom+ImageWindow.VGAP*2+win.getSliderHeight();
		} else {
			r1.width = r1.width - dstWidth + newWidth;
			r1.height = r1.height - dstHeight + newHeight;
		}
		Rectangle max = GUI.getMaxWindowBounds(win);
		boolean fitsHorizontally = r1.x+r1.width<max.x+max.width;
		boolean fitsVertically = r1.y+r1.height<max.y+max.height;
		if (fitsHorizontally && fitsVertically)
			return new Dimension(newWidth, newHeight);
		else if (fitsVertically && newHeight<dstWidth)
			return new Dimension(dstWidth, newHeight);
		else if (fitsHorizontally && newWidth<dstHeight)
			return new Dimension(newWidth, dstHeight);
		else
			return null;
	}
	
	/**Zooms out by making the source rectangle (srcRect)  
		larger and centering it on (x,y). If we can't make it larger,  
		then make the window smaller. Note that
		sx and sy are screen coordinates. */
	public void zoomOut(int sx, int sy) {
		if (magnification<=zoomLevels[0])
			return;
	    boolean mouseMoved = sqr(sx-lastZoomSX) + sqr(sy-lastZoomSY) > MAX_MOUSEMOVE_ZOOM*MAX_MOUSEMOVE_ZOOM;
		lastZoomSX = sx;
		lastZoomSY = sy;
		if (mouseMoved || zoomTargetOX<0) {
		    boolean cursorInside = sx >= 0 && sy >= 0 && sx < dstWidth && sy < dstHeight;
		    zoomTargetOX = offScreenX(cursorInside ? sx : dstWidth/2); //where to zoom, offscreen (image) coordinates
		    zoomTargetOY = offScreenY(cursorInside ? sy : dstHeight/2);
		}
		double oldMag = magnification;
		double newMag = getLowerZoomLevel(magnification);
		double srcRatio = (double)srcRect.width/srcRect.height;
		double imageRatio = (double)imageWidth/imageHeight;
		double initialMag = imp.getWindow().getInitialMagnification();
		if (Math.abs(srcRatio-imageRatio)>0.05) {
			double scale = oldMag/newMag;
			int newSrcWidth = (int)Math.round(srcRect.width*scale);
			int newSrcHeight = (int)Math.round(srcRect.height*scale);
			if (newSrcWidth>imageWidth) newSrcWidth=imageWidth; 
			if (newSrcHeight>imageHeight) newSrcHeight=imageHeight;
			int newSrcX = srcRect.x - (newSrcWidth - srcRect.width)/2;
			int newSrcY = srcRect.y - (newSrcHeight - srcRect.height)/2;
			if (newSrcX + newSrcWidth > imageWidth) newSrcX = imageWidth - newSrcWidth;
			if (newSrcY + newSrcHeight > imageHeight) newSrcY = imageHeight - newSrcHeight;
			if (newSrcX<0) newSrcX = 0;
			if (newSrcY<0) newSrcY = 0;
			srcRect = new Rectangle(newSrcX, newSrcY, newSrcWidth, newSrcHeight);
            //IJ.log(newMag+" "+srcRect+" "+dstWidth+" "+dstHeight);
			int newDstWidth = (int)(srcRect.width*newMag);
			int newDstHeight = (int)(srcRect.height*newMag);
			setMagnification(newMag);
			setMaxBounds();
            //IJ.log(newDstWidth+" "+dstWidth+" "+newDstHeight+" "+dstHeight);
			if (newDstWidth<dstWidth || newDstHeight<dstHeight) {
				setSize(newDstWidth, newDstHeight);
				imp.getWindow().pack();
			} else
				repaint();
			return;
		}
		if (imageWidth*newMag>dstWidth) {
			int w = (int)Math.round(dstWidth/newMag);
			if (w*newMag<dstWidth) w++;
			int h = (int)Math.round(dstHeight/newMag);
			if (h*newMag<dstHeight) h++;
			Rectangle r = new Rectangle(zoomTargetOX-w/2, zoomTargetOY-h/2, w, h);
			if (r.x<0) r.x = 0;
			if (r.y<0) r.y = 0;
			if (r.x+w>imageWidth) r.x = imageWidth-w;
			if (r.y+h>imageHeight) r.y = imageHeight-h;
			srcRect = r;
			setMagnification(newMag);
		} else {
			srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
			setSize((int)(imageWidth*newMag), (int)(imageHeight*newMag));
			setMagnification(newMag);
			imp.getWindow().pack();
		}
		setMaxBounds();
		repaint();
	}

    int sqr(int x) {
        return x*x;
    }

	/** Implements the Image/Zoom/Original Scale command. */
	public void unzoom() {
		double imag = imp.getWindow().getInitialMagnification();
		if (magnification==imag)
			return;
		srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
		ImageWindow win = imp.getWindow();
		setSize((int)(imageWidth*imag), (int)(imageHeight*imag));
		setMagnification(imag);
        setMaxBounds();
		win.pack();
		setMaxBounds();
		repaint();
	}
		
	/** Implements the Image/Zoom/View 100% command. */
	public void zoom100Percent() {
		if (magnification==1.0)
			return;
		double imag = imp.getWindow().getInitialMagnification();
		if (magnification!=imag)
			unzoom();
		if (magnification==1.0)
			return;
		if (magnification<1.0) {
			while (magnification<1.0)
				zoomIn(imageWidth/2, imageHeight/2);
		} else if (magnification>1.0) {
			while (magnification>1.0)
				zoomOut(imageWidth/2, imageHeight/2);
		} else
			return;
		int x=xMouse, y=yMouse;
		if (mouseExited) {
			x = imageWidth/2;
			y = imageHeight/2;
		}
		int sx = screenX(x);
		int sy = screenY(y);
		adjustSourceRect(1.0, sx, sy);
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
		return new Color(cm.getRGB(index));
	}
	
	/** Sets the foreground drawing color (or background color if 
		'setBackground' is true) to the color of the pixel at (ox,oy). */
	public void setDrawingColor(int ox, int oy, boolean setBackground) {
		//IJ.log("setDrawingColor: "+setBackground+this);
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

	public void mousePressed(final MouseEvent e) {
		showCursorStatus = true;
		int toolID = Toolbar.getToolId();
		ImageWindow win = imp.getWindow();
		if (win!=null && win.running2 && toolID!=Toolbar.MAGNIFIER) {
			if (win instanceof StackWindow)
				((StackWindow)win).setAnimate(false);
			else
				win.running2 = false;
			return;
		}
				
		int x = e.getX();
		int y = e.getY();
		flags = e.getModifiers();		
		if (toolID!=Toolbar.MAGNIFIER && (e.isPopupTrigger()||(!IJ.isMacintosh()&&(flags&Event.META_MASK)!=0))) {
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
		
		if (overOverlayLabel && (imp.getOverlay()!=null||showAllOverlay!=null)) {
			if (activateOverlayRoi(ox, oy))
				return;
		}
		
		if ((System.currentTimeMillis()-mousePressedTime)<300L && !drawingTool()) {
			if (activateOverlayRoi(ox,oy))
				return;
		}
		
		mousePressedX = ox;
		mousePressedY = oy;
		mousePressedTime = System.currentTimeMillis();
		
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool!=null) {
			tool.mousePressed(imp, e);
			if (e.isConsumed()) return;
		}
		if (customRoi && imp.getOverlay()!=null)
			return;

		if (toolID>=Toolbar.CUSTOM1) {
			if (tool!=null && "Arrow Tool".equals(tool.getToolName()))
				handleRoiMouseDown(e);
			else
				Toolbar.getInstance().runMacroTool(toolID);
			return;
		}
		
		final Roi roi1 = imp.getRoi();
		final int size1 = roi1!=null?roi1.size():0;
		final Rectangle r1 = roi1!=null?roi1.getBounds():null;

		switch (toolID) {
			case Toolbar.MAGNIFIER:
				if (IJ.shiftKeyDown())
					zoomToSelection(ox, oy);
				else if ((flags & (Event.ALT_MASK|Event.META_MASK|Event.CTRL_MASK))!=0) {
					zoomOut(x, y);
					if (getMagnification()<1.0)
						imp.repaintWindow();
				} else {
	 				zoomIn(x, y);
					if (getMagnification()<=1.0)
						imp.repaintWindow();
				}
				break;
			case Toolbar.HAND:
				setupScroll(ox, oy);
				break;
			case Toolbar.DROPPER:
				setDrawingColor(ox, oy, IJ.altKeyDown());
				break;
			case Toolbar.WAND:
				double tolerance = WandToolOptions.getTolerance();
				Roi roi = imp.getRoi();
				if (roi!=null && (tolerance==0.0||imp.isThreshold()) && roi.contains(ox, oy)) {
					Rectangle r = roi.getBounds();
					if (r.width==imageWidth && r.height==imageHeight)
						imp.deleteRoi();
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
				if (!imp.okToDeleteRoi())
					break;
				setRoiModState(e, roi, -1);
				String mode = WandToolOptions.getMode();
				if (Prefs.smoothWand)
					mode = mode + " smooth";
				int npoints = IJ.doWand(imp, ox, oy, tolerance, mode);
				if (Recorder.record && npoints>0) {
					if (Recorder.scriptMode())
						Recorder.recordCall("IJ.doWand(imp, "+ox+", "+oy+", "+tolerance+", \""+mode+"\");");
					else {
						if (tolerance==0.0 && mode.equals("Legacy"))
							Recorder.record("doWand", ox, oy);
						else
							Recorder.recordString("doWand("+ox+", "+oy+", "+tolerance+", \""+mode+"\");\n");
					}
				}
				break;
			case Toolbar.OVAL:
				if (Toolbar.getBrushSize()>0)
					new RoiBrush();
				else
					handleRoiMouseDown(e);
				break;
			default:  //selection tool
				handleRoiMouseDown(e);
		}
		
		if (longClickDelay>0) {
			if (pressTimer==null)
				pressTimer = new java.util.Timer();	
			final Point cursorLoc = getCursorLoc();	
			pressTimer.schedule(new TimerTask() {
				public void run() {
					if (pressTimer != null) {
						pressTimer.cancel();
						pressTimer = null;
					}
					Roi roi2 = imp.getRoi();
					int size2 = roi2!=null?roi2.size():0;
					Rectangle r2 = roi2!=null?roi2.getBounds():null;
					boolean empty = r2!=null&&r2.width==0&&r2.height==0;
					int state = roi2!=null?roi2.getState():-1;
					boolean unchanged = state!=Roi.MOVING_HANDLE && r1!=null && r2!=null && r2.x==r1.x
						&& r2.y==r1.y  && r2.width==r1.width && r2.height==r1.height && size2==size1
						&& !(size2>1&&state==Roi.CONSTRUCTING);
					boolean cursorMoved = !getCursorLoc().equals(cursorLoc);
					//IJ.log(size2+" "+empty+" "+unchanged+" "+state+" "+roi1+"  "+roi2);			
					if ((roi1==null && (size2<=1||empty)) || unchanged) {
						if (roi1==null) imp.deleteRoi();
						if (!cursorMoved && Toolbar.getToolId()!=Toolbar.HAND)
							handlePopupMenu(e);
					}
				}
			}, longClickDelay);
		}
		
	}
	
	
		
	private boolean drawingTool() {
		int id = Toolbar.getToolId();
		return id==Toolbar.POLYLINE || id==Toolbar.FREELINE || id>=Toolbar.CUSTOM1;
	}
	
	void zoomToSelection(int x, int y) {
		IJ.setKeyUp(IJ.ALL_KEYS);
		String macro =
			"args = split(getArgument);\n"+
			"x1=parseInt(args[0]); y1=parseInt(args[1]); flags=20;\n"+
			"while (flags&20!=0) {\n"+
				"getCursorLoc(x2, y2, z, flags);\n"+
				"if (x2>=x1) x=x1; else x=x2;\n"+
				"if (y2>=y1) y=y1; else y=y2;\n"+
				"makeRectangle(x, y, abs(x2-x1), abs(y2-y1));\n"+
				"wait(10);\n"+
			"}\n"+
			"run('To Selection');\n";
		new MacroRunner(macro, x+" "+y);
	}

	protected void setupScroll(int ox, int oy) {
		xMouseStart = ox;
		yMouseStart = oy;
		xSrcStart = srcRect.x;
		ySrcStart = srcRect.y;
	}

	protected void handlePopupMenu(MouseEvent e) {
		if (disablePopupMenu) return;
		if (IJ.debugMode) IJ.log("show popup: " + (e.isPopupTrigger()?"true":"false"));
		int sx = e.getX();
		int sy = e.getY();
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		Roi roi = imp.getRoi();
		if (roi!=null && roi.getType()==Roi.COMPOSITE && Toolbar.getToolId()==Toolbar.OVAL && Toolbar.getBrushSize()>0)
			return; // selection brush tool
		if (roi!=null && (roi.getType()==Roi.POLYGON || roi.getType()==Roi.POLYLINE || roi.getType()==Roi.ANGLE)
		&& roi.getState()==roi.CONSTRUCTING) {
			roi.handleMouseUp(sx, sy); // simulate double-click to finalize
			roi.handleMouseUp(sx, sy); // polygon or polyline selection
			return;
		}
		if (roi!=null && !(e.isAltDown()||e.isShiftDown())) {  // show ROI popup?
			if (roi.contains(ox,oy)) {
				if (roiPopupMenu==null)
					addRoiPopupMenu();
				if (IJ.isMacOSX()) IJ.wait(10);
				roiPopupMenu.show(this, sx, sy);
				return;					
			}
		}
		PopupMenu popup = Menus.getPopupMenu();
		if (popup!=null) {
			add(popup);
			if (IJ.isMacOSX()) IJ.wait(10);
			popup.show(this, sx, sy);
		}
	}
	
	public void mouseExited(MouseEvent e) {
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool!=null) {
			tool.mouseExited(imp, e);
			if (e.isConsumed()) return;
		}
		ImageWindow win = imp.getWindow();
		if (win!=null)
			setCursor(defaultCursor);
		IJ.showStatus("");
		mouseExited = true;
	}

	public void mouseDragged(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();
		xMouse = offScreenX(x);
		yMouse = offScreenY(y);
		flags = e.getModifiers();
		mousePressedX = mousePressedY = -1;
		//IJ.log("mouseDragged: "+flags);
		if (flags==0)  // workaround for Mac OS 9 bug
			flags = InputEvent.BUTTON1_MASK;
		if (Toolbar.getToolId()==Toolbar.HAND || IJ.spaceBarDown())
			scroll(x, y);
		else {
			PlugInTool tool = Toolbar.getPlugInTool();
			if (tool!=null) {
				tool.mouseDragged(imp, e);
				if (e.isConsumed()) return;
			}
			IJ.setInputEvent(e);
			Roi roi = imp.getRoi();
			if (roi != null)
				roi.handleMouseDrag(x, y, flags);
		}
	}

	protected void handleRoiMouseDown(MouseEvent e) {
		int sx = e.getX();
		int sy = e.getY();
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		Roi roi = imp.getRoi();	
		int tool = Toolbar.getToolId();	

		int handle = roi!=null?roi.isHandle(sx, sy):-1;
		boolean multiPointMode = roi!=null && (roi instanceof PointRoi) && handle==-1
			&& tool==Toolbar.POINT && Toolbar.getMultiPointMode();
		if (multiPointMode) {
			double oxd = roi.offScreenXD(sx);
			double oyd = roi.offScreenYD(sy);
			if (e.isShiftDown() && !IJ.isMacro()) {
				FloatPolygon points = roi.getFloatPolygon();
				if (points.npoints>0) {
					double x0 = points.xpoints[0];
					double y0 = points.ypoints[0];
					double slope = Math.abs((oxd-x0)/(oyd-y0));
					if (slope>=1.0)
						oyd = points.ypoints[0];
					else
						oxd = points.xpoints[0];
				}
			}
			((PointRoi)roi).addUserPoint(imp, oxd, oyd);
			imp.setRoi(roi);
			return;
		}
				
		if (roi!=null && (roi instanceof PointRoi)) {
			int npoints = ((PolygonRoi)roi).getNCoordinates();
			if (npoints>1 && handle==-1 && !IJ.altKeyDown() && !(tool==Toolbar.POINT && !Toolbar.getMultiPointMode()&&IJ.shiftKeyDown())) {
				String msg =  "Type shift-a (Edit>Selection>Select None) to delete\npoints. Use multi-point tool to add points.";
				GenericDialog gd=new GenericDialog("Point Selection");
				gd.addMessage(msg);
				gd.addHelp(PointToolOptions.help);
				gd.hideCancelButton();
				gd.showDialog();
				return;
			}
		}
		
		setRoiModState(e, roi, handle);
		if (roi!=null) {
			if (handle>=0) {
				roi.mouseDownInHandle(handle, sx, sy);
				return;
			}
			Rectangle r = roi.getBounds();
			int type = roi.getType();
			if (type==Roi.RECTANGLE && r.width==imp.getWidth() && r.height==imp.getHeight()
			&& roi.getPasteMode()==Roi.NOT_PASTING && !(roi instanceof ImageRoi)) {
				imp.deleteRoi();
				return;
			}
			if (roi.contains(ox, oy)) {
				if (roi.modState==Roi.NO_MODS)
					roi.handleMouseDown(sx, sy);
				else {
					imp.deleteRoi();
					imp.createNewRoi(sx,sy);
				}
				return;
			}			
			boolean segmentedTool = tool==Toolbar.POLYGON || tool==Toolbar.POLYLINE || tool==Toolbar.ANGLE;
			if (segmentedTool && (type==Roi.POLYGON || type==Roi.POLYLINE || type==Roi.ANGLE)
			&& roi.getState()==roi.CONSTRUCTING)
				return;
			if (segmentedTool&& !(IJ.shiftKeyDown()||IJ.altKeyDown())) {
				imp.deleteRoi();
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
	
	/** Disable/enable popup menu. */
	public void disablePopupMenu(boolean status) {
		disablePopupMenu = status;
	}
	
	public void setShowAllList(Overlay showAllList) {
		this.showAllOverlay = showAllList;
		labelRects = null;
	}
	
	public Overlay getShowAllList() {
		return showAllOverlay;
	}

	/** Obsolete */
	public void setShowAllROIs(boolean showAllROIs) {
		RoiManager rm = RoiManager.getInstance();
		if (rm!=null)
			rm.runCommand(showAllROIs?"show all":"show none");
	}

	/** Obsolete */
	public boolean getShowAllROIs() {
		return getShowAllList()!=null;
	}
	
	/** Obsolete */
	public static Color getShowAllColor() {
		if (showAllColor!=null && showAllColor.getRGB()==0xff80ffff)
			showAllColor = Color.cyan;
		return showAllColor;
	}

	/** Obsolete */
	public static void setShowAllColor(Color c) {
		if (c==null) return;
		showAllColor = c;
		labelColor = null;
	}
	
	/** Experimental */
	public static void setCursor(Cursor cursor, int type) {
		crosshairCursor = cursor;
	}

	/** Use ImagePlus.setOverlay(ij.gui.Overlay). */
	public void setOverlay(Overlay overlay) {
		imp.setOverlay(overlay);
	}
	
	/** Use ImagePlus.getOverlay(). */
	public Overlay getOverlay() {
		return imp.getOverlay();
	}

	/**
	* @deprecated
	* replaced by ImagePlus.setOverlay(ij.gui.Overlay)
	*/
	public void setDisplayList(Vector list) {
		if (list!=null) {
			Overlay list2 = new Overlay();
			list2.setVector(list);
			imp.setOverlay(list2);
		} else
			imp.setOverlay(null);
		Overlay overlay = imp.getOverlay();
		if (overlay!=null)
			overlay.drawLabels(overlay.size()>0&&overlay.get(0).getStrokeColor()==null);
		else
			customRoi = false;
		repaint();
	}

	/**
	* @deprecated
	* replaced by ImagePlus.setOverlay(Shape, Color, BasicStroke)
	*/
	public void setDisplayList(Shape shape, Color color, BasicStroke stroke) {
		imp.setOverlay(shape, color, stroke);
	}
	
	/**
	* @deprecated
	* replaced by ImagePlus.setOverlay(Roi, Color, int, Color)
	*/
	public void setDisplayList(Roi roi, Color color) {
		roi.setStrokeColor(color);
		Overlay list = new Overlay();
		list.add(roi);
		imp.setOverlay(list);
	}
	
	/**
	* @deprecated
	* replaced by ImagePlus.getOverlay()
	*/
	public Vector getDisplayList() {
		Overlay overlay = imp.getOverlay();
		if (overlay==null)
			return null;
		Vector displayList = new Vector();
		for (int i=0; i<overlay.size(); i++)
			displayList.add(overlay.get(i));
		return displayList;
	}
	
	/** Allows plugins (e.g., Orthogonal_Views) to create a custom ROI using a display list. */
	public void setCustomRoi(boolean customRoi) {
		this.customRoi = customRoi;
	}

	public boolean getCustomRoi() {
		return customRoi;
	}
	
	/** Called by IJ.showStatus() to prevent status bar text from
		being overwritten until the cursor moves at least 12 pixels. */
	public void setShowCursorStatus(boolean status) {
		showCursorStatus = status;
		if (status==true)
			sx2 = sy2 = -1000;
		else {
			sx2 = screenX(xMouse);
			sy2 = screenY(yMouse);
		}
	}

	public void mouseReleased(MouseEvent e) {

		if (pressTimer!=null) {
			pressTimer.cancel();
			pressTimer = null;
		}
		
		int ox = offScreenX(e.getX());
		int oy = offScreenY(e.getY());
		Overlay overlay = imp.getOverlay();
		if ((overlay!=null||showAllOverlay!=null) && ox==mousePressedX && oy==mousePressedY) {
			boolean cmdDown = IJ.isMacOSX() && e.isMetaDown();
			Roi roi = imp.getRoi();
			if (roi!=null && roi.getBounds().width==0)
				roi=null;
			if ((e.isAltDown()||e.isControlDown()||cmdDown) && roi==null) {
				if (activateOverlayRoi(ox, oy))
					return;
			} else if ((System.currentTimeMillis()-mousePressedTime)>250L && !drawingTool()) { // long press
				if (activateOverlayRoi(ox,oy))
					return;
			}

		}

		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool!=null) {
			tool.mouseReleased(imp, e);
			if (e.isConsumed()) return;
		}
		flags = e.getModifiers();
		flags &= ~InputEvent.BUTTON1_MASK; // make sure button 1 bit is not set
		flags &= ~InputEvent.BUTTON2_MASK; // make sure button 2 bit is not set
		flags &= ~InputEvent.BUTTON3_MASK; // make sure button 3 bit is not set
		Roi roi = imp.getRoi();
		if (roi != null) {
			Rectangle r = roi.getBounds();
			int type = roi.getType();
			if ((r.width==0 || r.height==0)
			&& !(type==Roi.POLYGON||type==Roi.POLYLINE||type==Roi.ANGLE||type==Roi.LINE)
			&& !(roi instanceof TextRoi)
			&& roi.getState()==roi.CONSTRUCTING
			&& type!=roi.POINT)
				imp.deleteRoi();
			else
				roi.handleMouseUp(e.getX(), e.getY());
		}
	}
	
	private boolean activateOverlayRoi(int ox, int oy) {
		int currentImage = -1;
		int stackSize = imp.getStackSize();
		if (stackSize>1)
			currentImage = imp.getCurrentSlice();
		int channel=0, slice=0, frame=0;
		boolean hyperstack = imp.isHyperStack();
		if (hyperstack) {
			channel = imp.getChannel();
			slice = imp.getSlice();
			frame = imp.getFrame();
		}
		Overlay o = showAllOverlay;
		if (o==null)
			o = imp.getOverlay();
		if (o==null || !o.isSelectable())
			return false;
		boolean roiManagerShowAllMode = o==showAllOverlay && !Prefs.showAllSliceOnly;
		boolean labels = o.getDrawLabels();
		int sx = screenX(ox);
		int sy = screenY(oy);
		for (int i=o.size()-1; i>=0; i--) {
			Roi roi = o.get(i);
			if (roi==null)
				continue;
			//IJ.log(".isAltDown: "+roi.contains(ox, oy));
			boolean containsMousePoint = false;
			if (roi instanceof Line) {	//grab line roi near its center
				double grabLineWidth = 1.1 + 5./magnification;
				containsMousePoint = (((Line)roi).getFloatPolygon(grabLineWidth)).contains(ox, oy);
			} else
				containsMousePoint = roi.contains(ox, oy);
			if (containsMousePoint || (labels&&labelRects!=null&&labelRects[i]!=null&&labelRects[i].contains(sx,sy))) {
				if (hyperstack && roi.getPosition()==0) {
					int c = roi.getCPosition();
					int z = roi.getZPosition();
					int t = roi.getTPosition();
					if (!((c==0||c==channel)&&(z==0||z==slice)&&(t==0||t==frame) || roiManagerShowAllMode))
						continue;
				} else {
					int position = stackSize>1?roi.getPosition():0;
					if (!(position==0||position==currentImage||roiManagerShowAllMode))
						continue;
				}
				if (!IJ.altKeyDown() && roi.getType()==Roi.COMPOSITE
				&& roi.getBounds().width==imp.getWidth() && roi.getBounds().height==imp.getHeight())
					return false;
				//if (Toolbar.getToolId()==Toolbar.OVAL && Toolbar.getBrushSize()>0)
				//	Toolbar.getInstance().setTool(Toolbar.RECTANGLE);
				roi.setImage(null);
				imp.setRoi(roi);
				roi.handleMouseDown(sx, sy);
				roiManagerSelect(roi, false);
				ResultsTable.selectRow(roi);
				return true;
			}
		}
		return false;
	}
		
    public boolean roiManagerSelect(Roi roi, boolean delete) {
		RoiManager rm=RoiManager.getInstance();
		if (rm==null)
			return false;
		int index = rm.getRoiIndex(roi);
		if (index<0)
			return false;
		if (delete) {
			rm.select(imp, index);
			rm.runCommand("delete");
		} else
			rm.selectAndMakeVisible(imp, index);
		return true;
    }
    
	public void mouseMoved(MouseEvent e) {
		//if (ij==null) return;
		int sx = e.getX();
		int sy = e.getY();
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		flags = e.getModifiers();
		setCursor(sx, sy, ox, oy);
		mousePressedX = mousePressedY = -1;
		IJ.setInputEvent(e);
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool!=null) {
			tool.mouseMoved(imp, e);
			if (e.isConsumed()) return;
		}
		Roi roi = imp.getRoi();
		int type = roi!=null?roi.getType():-1;
		if (type>0 && (type==Roi.POLYGON||type==Roi.POLYLINE||type==Roi.ANGLE||type==Roi.LINE) 
		&& roi.getState()==roi.CONSTRUCTING)
			roi.mouseMoved(e);
		else {
			if (ox<imageWidth && oy<imageHeight) {
				ImageWindow win = imp.getWindow();
				// Cursor must move at least 12 pixels before text
				// displayed using IJ.showStatus() is overwritten.
				if ((sx-sx2)*(sx-sx2)+(sy-sy2)*(sy-sy2)>144)
					showCursorStatus =  true;
				if (win!=null&&showCursorStatus)
					win.mouseMoved(ox, oy);
			} else
				IJ.showStatus("");
		}
	}
	
	public void mouseEntered(MouseEvent e) {
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool!=null)
			tool.mouseEntered(imp, e);
	}

	public void mouseClicked(MouseEvent e) {
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool!=null)
			tool.mouseClicked(imp, e);
	}
	
	public void setScaleToFit(boolean scaleToFit) {
		this.scaleToFit = scaleToFit;
	}

	public boolean getScaleToFit() {
		return scaleToFit;
	}
	
	public boolean hideZoomIndicator(boolean hide) {
		boolean hidden = this.hideZoomIndicator;
		if (!(srcRect.width<imageWidth||srcRect.height<imageHeight))
			return hidden;
		this.hideZoomIndicator = hide;
		setPaintPending(true);
		repaint();
		long t0 = System.currentTimeMillis();
		while(getPaintPending() && (System.currentTimeMillis()-t0)<500L)
			IJ.wait(10);
		return hidden;
	}
	
	public void repaintOverlay() {
		labelRects = null;
		repaint();
	}
	
	/** Sets the context menu long click delay in milliseconds
	 * (default is 1000). Set to 0 to disable long click triggering.
	*/
	 public static void setLongClickDelay(int delay) {
		longClickDelay = delay;
	}
	
	void addRoiPopupMenu() {
		ImageJ ij = IJ.getInstance();
		if (ij==null)
			return;
		roiPopupMenu = new PopupMenu();
		GUI.scalePopupMenu(roiPopupMenu);
		addPopupItem("ROI Properties... ", "Properties... ", roiPopupMenu, ij);
		addPopupItem("Roi Defaults...", null, roiPopupMenu, ij);
		addPopupItem("Add to Overlay", "Add Selection...", roiPopupMenu, ij);
		addPopupItem("Add to ROI Manager", "Add to Manager", roiPopupMenu, ij);				
		addPopupItem("Duplicate...", null, roiPopupMenu, ij);	
		addPopupItem("Fit Spline", null, roiPopupMenu, ij);	
		addPopupItem("Create Mask", null, roiPopupMenu, ij);	
		addPopupItem("Measure", null, roiPopupMenu, ij);							
		add(roiPopupMenu);
	}

	private void addPopupItem(String label, String command, PopupMenu pm, ImageJ ij) {
		MenuItem mi=new MenuItem(label);
		if (command!=null)
			mi.setActionCommand(command);
		mi.addActionListener(ij);
		pm.add(mi);
	}

}
