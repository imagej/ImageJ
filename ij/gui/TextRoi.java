package ij.gui;
import ij.*;
import ij.process.*;
import ij.util.*;
import ij.macro.Interpreter;
import ij.plugin.frame.Recorder;
import ij.plugin.Colors;
import java.awt.geom.*;
import java.awt.*;


/** This class is a rectangular ROI containing text. */
public class TextRoi extends Roi {

	public static final int LEFT=0, CENTER=1, RIGHT=2;
	static final int MAX_LINES = 50;

	private static final String line1 = "Enter text, then press";
	private static final String line2 = "ctrl+b to add to overlay";
	private static final String line3 = "or ctrl+d to draw.";
	private static final String line1a = "Enter text...";
	private String[] theText = new String[MAX_LINES];
	private static String name = "SansSerif";
	private static int style = Font.PLAIN;
	private static int size = 18;
	private Font instanceFont;
	private static boolean newFont = true;
	private boolean newJustification;
	private static boolean antialiasedText = true; // global flag used by text tool
	private static int globalJustification;
	private static Color defaultFillColor;
	private int justification;
	private double previousMag;
	private boolean firstChar = true;
	private boolean firstMouseUp = true;
	private int cline = 0;
	private boolean drawStringMode;
	private double angle;  // degrees
	private static double defaultAngle;
	private static boolean firstTime = true;
	private Roi previousRoi;

	/** Creates a TextRoi.*/
	public TextRoi(int x, int y, String text) {
		super(x, y, 1, 1);
		init(text, null);
	}
	
	/** Use this constructor as a drop-in replacement for ImageProcessor.drawString(). */
	public TextRoi(String text, double x, double y, Font font) {
		super(x, y, 1, 1);
		drawStringMode = true;
		if (text!=null && text.contains("\n")) {
			String[] lines = Tools.split(text, "\n");
			int count = Math.min(lines.length, MAX_LINES);
			for (int i=0; i<count; i++)
				theText[i] = lines[i];
		} else
			theText[0] = text;
		instanceFont = font;
		if (instanceFont==null)
			instanceFont = new Font(name, style, size);
		ImageJ ij = IJ.getInstance();
		Graphics g = ij!=null?ij.getGraphics():null;
		if (g==null) return;
		FontMetrics metrics = g.getFontMetrics(instanceFont);
		g.dispose();
		bounds = null;
		this.width = (int)stringWidth(theText[0],metrics,g);
		this.height = (int)(metrics.getHeight());
		this.x = (int)x;
		this.y = (int)(y - this.height);
		setAntiAlias(antialiasedText);
		justification = LEFT;
	}

	/** Creates a TextRoi using sub-pixel coordinates.*/
	public TextRoi(double x, double y, String text) {
		super(x, y, 1.0, 1.0);
		init(text, null);
	}

	/** Creates a TextRoi using the specified location and Font.
	 * @see ij.gui.Roi#setStrokeColor
	 * @see ij.gui.Roi#setNonScalable
	 * @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
	 */
	public TextRoi(int x, int y, String text, Font font) {
		super(x, y, 1, 1);
		init(text, font);
	}

	/** Creates a TextRoi using the specified sub-pixel location and Font. */
	public TextRoi(double x, double y, String text, Font font) {
		super(x, y, 1.0, 1.0);
		init(text, font);
	}

	/** Creates a TextRoi using the specified sub-pixel location, size and Font. */
	public TextRoi(double x, double y, double width, double height, String text, Font font) {
		super(x, y, width, height);
		init(text, font);
	}
	
	private void init(String text, Font font) {
		String[] lines = Tools.split(text, "\n");
		int count = Math.min(lines.length, MAX_LINES);
		for (int i=0; i<count; i++)
			theText[i] = lines[i];
		if (font==null) font = new Font(name, style, size);
		instanceFont = font;
		setAntiAlias(antialiasedText);
		firstChar = false;
		if (this.width==1 && this.height==1) {
			ImageJ ij = IJ.getInstance();
			Graphics g = ij!=null?ij.getGraphics():null;
			if (g!=null)
				updateBounds(g);
		}
	}

	/** @deprecated */
	public TextRoi(int x, int y, String text, Font font, Color color) {
		super(x, y, 1, 1);
		if (font==null) font = new Font(name, style, size);
		instanceFont = font;
		IJ.error("TextRoi", "API has changed. See updated example at\nhttp://imagej.nih.gov/ij/macros/js/TextOverlay.js");
	}

	public TextRoi(int x, int y, ImagePlus imp) {
		super(x, y, imp);
		ImageCanvas ic = imp.getCanvas();
		double mag = getMagnification();
		if (mag>1.0)
			mag = 1.0;
		if (size<(12/mag))
			size = (int)(12/mag);
		if (firstTime) {
			theText[0] = line1;
			theText[1] = line2;
			theText[2] = line3;
			firstTime = false;
		} else
			theText[0] = line1a;
		if (previousRoi!=null && (previousRoi instanceof TextRoi)) {
			firstMouseUp = false;
			previousRoi = null;
		}
		instanceFont = new Font(name, style, size);
		justification = globalJustification;
		setStrokeColor(Toolbar.getForegroundColor());
		setAntiAlias(antialiasedText);
		if (WindowManager.getWindow("Fonts")!=null) {
			setFillColor(defaultFillColor);
			setAngle(defaultAngle);
		}
	}

	/** This method is used by the text tool to add typed
		characters to displayed text selections. */
	public void addChar(char c) {
		if (imp==null) return;
		if (!(c>=' ' || c=='\b' || c=='\n')) return;
		if (firstChar) {
			cline = 0;
			theText[cline] = new String("");
			for (int i=1; i<MAX_LINES; i++)
				theText[i] = null;
		}
		if ((int)c=='\b') {
			// backspace
			if (theText[cline].length()>0)
				theText[cline] = theText[cline].substring(0, theText[cline].length()-1);
			else if (cline>0) {
				theText[cline] = null;
				cline--;
			}
			if (angle!=0.0)
				imp.draw();
			else
				imp.draw(clipX, clipY, clipWidth, clipHeight);
			firstChar = false;
			return;
		} else if ((int)c=='\n') {
			// newline
			if (cline<(MAX_LINES-1)) cline++;
			theText[cline] = "";
			updateBounds(null);
			updateText();
		} else {
			char[] chr = {c};
			theText[cline] += new String(chr);
			updateBounds(null);
			updateText();
			firstChar = false;
			return;
		}
	}

	Font getScaledFont() {
		if (nonScalable)
			return instanceFont;
		else {
			if (instanceFont==null)
				instanceFont = new Font(name, style, size);
			double mag = getMagnification();
			return instanceFont.deriveFont((float)(instanceFont.getSize()*mag));
		}
	}
	
	/** Renders the text on the image. Draws the text in
	 * the foreground color if ip.setColor(Color) has
	 * not been called.
	 *	@see ij.process.ImageProcessor#setFont(Font)
	 *	@see ij.process.ImageProcessor#setAntialiasedText(boolean)
	 *	@see ij.process.ImageProcessor#setColor(Color)
	*/
	public void drawPixels(ImageProcessor ip) {
		if (newFont || this.width==1 || newJustification) {
			ImageJ ij = IJ.getInstance();
			Graphics g = ij!=null?ij.getGraphics():null;
			if (g!=null)
				updateBounds(g);
		}
		if (!ip.fillValueSet())
			ip.setColor(Toolbar.getForegroundColor());
		ip.setFont(instanceFont);
		ip.setAntialiasedText(getAntiAlias());
		FontMetrics metrics = ip.getFontMetrics();
		int fontHeight = metrics.getHeight();
		int descent = metrics.getDescent();
		int i = 0;
		int yy = 0;
		int xi = (int)Math.round(getXBase());
		int yi = (int)Math.round(getYBase());
		while (i<MAX_LINES && theText[i]!=null) {
			switch (justification) {
				case LEFT:
					ip.drawString(theText[i], xi, yi+yy+fontHeight);
					break;
				case CENTER:
					int tw = metrics.stringWidth(theText[i]);
					ip.drawString(theText[i], xi+(this.width-tw)/2, yi+yy+fontHeight);
					break;
				case RIGHT:
					tw = metrics.stringWidth(theText[i]);
					ip.drawString(theText[i], xi+this.width-tw, yi+yy+fontHeight);
					break;
			}
			i++;
			yy += fontHeight;
		}
	}

	/** Draws the text on the screen, clipped to the ROI. */
	public void draw(Graphics g) {
		if (IJ.debugMode) IJ.log("draw: "+theText[0]+"  "+this.width+","+this.height);
		if (Interpreter.isBatchMode() && ic!=null && ic.getDisplayList()!=null)
			return;
		if (newFont || this.width==1 || newJustification)
			updateBounds(g);
		Color c = getStrokeColor();
		setStrokeColor(getColor());
		super.draw(g); // draw the rectangle
		setStrokeColor(c);
		double mag = getMagnification();
		int sx = screenXD(getXBase());
		int sy = screenYD(getYBase());
		int swidth = (int)((bounds!=null?bounds.width:this.width)*mag);
		int sheight = (int)((bounds!=null?bounds.height:this.height)*mag);
		Rectangle r = null;
		if (angle!=0.0)
			drawText(g);
		else {
			r = g.getClipBounds();
			g.setClip(sx, sy, swidth, sheight);
			drawText(g);
			if (r!=null)
				g.setClip(r.x, r.y, r.width, r.height);
		}
	}
	
	public void drawOverlay(Graphics g) {
		drawText(g);
	}

	void drawText(Graphics g) {
		g.setColor( strokeColor!=null? strokeColor:ROIColor);
		Java2.setAntialiasedText(g, getAntiAlias());
		if (newFont || this.width==1)
			updateBounds(g);
		double mag = getMagnification();
		int xi = (int)Math.round(getXBase());
		int yi = (int)Math.round(getYBase());
		double widthd = bounds!=null?bounds.width:this.width;
		double heightd = bounds!=null?bounds.height:this.height;
		int widthi = (int)Math.round(widthd);
		int heighti = (int)Math.round(heightd);
		Font font = getScaledFont();
		FontMetrics metrics = g.getFontMetrics(font);
		int fontHeight = metrics.getHeight();
		int descent = metrics.getDescent();
		g.setFont(font);
		Graphics2D g2d = (Graphics2D)g;
		updateBounds(g);
		int sx = nonScalable?xi:screenXD(getXBase());
		int sy = nonScalable?yi:screenYD(getYBase());
		int sw = nonScalable?widthi:(int)(getMagnification()*widthd);
		int sh = nonScalable?heighti:(int)(getMagnification()*heightd);
		AffineTransform at = null;
		if (angle!=0.0) {
			at = g2d.getTransform();
			double cx=sx, cy=sy;
			double theta = Math.toRadians(angle);
			if (drawStringMode) {
				cx = screenX(this.x);
				cy = screenY(this.y+this.height-descent);
			}
			g2d.rotate(-theta, cx, cy);
		}
		int i = 0;
		if (fillColor!=null) {
			Color c = g.getColor();
			int alpha = fillColor.getAlpha();
 			g.setColor(fillColor);
			g.fillRect(sx, sy, sw, sh);
			g.setColor(c);
		}
		while (i<MAX_LINES && theText[i]!=null) {
			switch (justification) {
				case LEFT:
					g.drawString(theText[i], sx, sy+fontHeight-descent);
					break;
				case CENTER:
					int tw = metrics.stringWidth(theText[i]);
					g.drawString(theText[i], sx+(sw-tw)/2, sy+fontHeight-descent);
					break;
				case RIGHT:
					tw = metrics.stringWidth(theText[i]);
					g.drawString(theText[i], sx+sw-tw, sy+fontHeight-descent);
					break;
			}
			i++;
			sy += fontHeight;
		}
		if (at!=null)  // restore transformation matrix used to rotate text
			g2d.setTransform(at);
	}

	/** Returns the name of the default font. Use getCurrentFont().getName()
		 to get the name of the font that this TextRoi is using. */
	public static String getDefaultFontName() {
		return name;
	}

	/** Returns the default font size. Use getCurrentFont().getSize()
		 to get the size of the font that this TextRoi is using. */
	public static int getDefaultFontSize() {
		return size;
	}

	/** Returns the default font style. Use getCurrentFont().getStyle()
		 to get the style of the font that this TextRoi is using. */
	public static int getDefaultFontStyle() {
		return style;
	}
	
	/** Set the current (instance) font. */
	public void setCurrentFont(Font font) {
		instanceFont = font;
		updateBounds(null);
	}
	
	/** Returns the current (instance) font. */
	public Font getCurrentFont() {
		return instanceFont;
	}
	
	/** Returns the state of the global 'antialiasedText' variable, which is used by the "Fonts" widget. */
	public static boolean isAntialiased() {
		return antialiasedText;
	}

	/** Sets the state of the global 'antialiasedText' variable. */
	public static void setAntialiasedText(boolean antialiased) {
		antialiasedText = antialiased;
	}
	
	/** Sets the 'antiAlias' instance variable. */
	public void setAntialiased(boolean antiAlias) {
		setAntiAlias(antiAlias);
		if (angle>0.0)
			setAntiAlias(true);
	}
	
	/** Returns the state of the 'antiAlias' instance variable. */
	public boolean getAntialiased() {
		return getAntiAlias();
	}
		
	/** Sets the default text tool justification (LEFT, CENTER or RIGHT). */
	public static void setGlobalJustification(int justification) {
		if (justification<0 || justification>RIGHT)
			justification = LEFT;
		globalJustification = justification;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			Roi roi = imp.getRoi();
			if (roi instanceof TextRoi) {
				((TextRoi)roi).setJustification(justification);
				imp.draw();
			}
		}
	}
	
	/** Returns the default text tool justification (LEFT, CENTER or RIGHT). */
	public static int getGlobalJustification() {
		return globalJustification;
	}

	/** Sets the 'justification' instance variable (LEFT, CENTER or RIGHT) */
	public void setJustification(int justification) {
		if (justification<0 || justification>RIGHT)
			justification = LEFT;
		if (drawStringMode && justification==RIGHT && this.justification==LEFT) {
			bounds = null;
			this.x = this.x - this.width;
		}
		if (drawStringMode && justification==CENTER && this.justification==LEFT) {
			bounds = null;
			this.x = this.x - this.width/2;
		}
		this.justification = justification;
		newJustification = true;
		if (imp!=null)
			imp.draw();
	}
	
	/** Returns the value of the 'justification' instance variable (LEFT, CENTER or RIGHT). */
	public int getJustification() {
		return justification;
	}

	/** Sets the global font face, size and style that will be used by
		TextROIs interactively created using the text tool. */
	public static void setFont(String fontName, int fontSize, int fontStyle) {
		setFont(fontName, fontSize, fontStyle, true);
	}
	
	/** Sets the font face, size, style and antialiasing mode that will 
		be used by TextROIs interactively created using the text tool. */
	public static void setFont(String fontName, int fontSize, int fontStyle, boolean antialiased) {
		name = fontName;
		size = fontSize;
		style = fontStyle;
		globalJustification = LEFT;
		antialiasedText = antialiased;
		newFont = true;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			Roi roi = imp.getRoi();
			if (roi instanceof TextRoi) {
				roi.setAntiAlias(antialiased);
				((TextRoi)roi).setCurrentFont(new Font(name, style, size));
				imp.draw();
			}
		}
	}

	/** Sets the default fill (background) color. */
	public static void setDefaultFillColor(Color fillColor) {
		defaultFillColor = fillColor;
	}

	/** Sets the default angle. */
	public static void setDefaultAngle(double angle) {
		defaultAngle = angle;
	}

	protected void handleMouseUp(int screenX, int screenY) {
		super.handleMouseUp(screenX, screenY);
		if (this.width<5 && this.height<5 && imp!=null && previousRoi==null) {
			int ox = ic!=null?ic.offScreenX(screenX):screenX;
			int oy = ic!=null?ic.offScreenY(screenY):screenY;
			TextRoi roi = new TextRoi(ox, oy, line1a);
			roi.setStrokeColor(Toolbar.getForegroundColor());
			roi.firstChar = true;
			imp.setRoi(roi);
			return;
		} else if (firstMouseUp) {
			updateBounds(null);
			updateText();
			firstMouseUp = false;
		}
		if (this.width<5 || this.height<5)
			imp.deleteRoi();
	}
	
	/** Increases the size of bounding rectangle so it's large enough to hold the text. */ 
	void updateBounds(Graphics g) {
		if (firstChar || drawStringMode)
			return;
		double mag = ic!=null?ic.getMagnification():1.0;
		if (nonScalable) mag = 1.0;
		Font font = getScaledFont();
		newFont = false;
		newJustification = false;
		boolean nullg = g==null;
		if (nullg) {
			if (ic!=null)
				g = ic.getGraphics();
			else
				return;
		}
		Java2.setAntialiasedText(g, getAntiAlias());
		FontMetrics metrics = g.getFontMetrics(font);
		int fontHeight = (int)(metrics.getHeight()/mag);
		int descent = metrics.getDescent();
		int i=0, nLines=0;
		Rectangle2D.Double b = bounds;
		if (b==null)
			b = new Rectangle2D.Double(this.x, this.y, this.width, this.height);
		double oldXD = b.x;
		double oldYD = b.y;
		double oldWidthD = b.width;
		double oldHeightD = b.height;
		double newWidth = 10;
		while (i<MAX_LINES && theText[i]!=null) {
			nLines++;
			double w = stringWidth(theText[i],metrics,g)/mag;
			if (w>newWidth)
				newWidth = w;
			i++;
		}
		if (nullg) g.dispose();
		newWidth += 2.0;
		b.width = newWidth;
		switch (justification) {
			case LEFT:
				break;
			case CENTER:
				b.x = oldX+oldWidth/2.0 - newWidth/2.0;
				break;
			case RIGHT:
				b.x = oldX+oldWidth - newWidth;
				break;
		}
		b.height = nLines*fontHeight+2;
		this.x = (int)b.x;
		this.y = (int)b.y;
		this.width = (int)Math.ceil(b.width);
		this.height = (int)Math.ceil(b.height);
		//IJ.log("adjustSize2: "+theText[0]+"  "+this.width+","+this.height);
	}
	
	void updateText() {
		if (imp!=null) {
			updateClipRect();
			if (angle!=0.0)
				imp.draw();
			else
				imp.draw(clipX, clipY, clipWidth, clipHeight);
		}
	}

	double stringWidth(String s, FontMetrics metrics, Graphics g) {
		java.awt.geom.Rectangle2D r = metrics.getStringBounds(s, g);
		return r.getWidth();
	}
	
	/** Used by the Recorder for recording the text tool. */
	public String getMacroCode(String cmd, ImagePlus imp) {
		String code = "";
		boolean script = Recorder.scriptMode();
		boolean addSelection = cmd.startsWith("Add");
		if (script && !addSelection)
			code += "ip = imp.getProcessor();\n";
		if (script) {
			String str = "Font.PLAIN";
			if (style==Font.BOLD)
				str =  "Font.BOLD";
			else if (style==Font.ITALIC)
				str =  "Font.ITALIC";
			code += "font = new Font(\""+name+"\", "+str+", "+size+");\n";
			if (addSelection)
				return getAddSelectionScript(code);
			code += "ip.setFont(font);\n";
		} else {
			String options = "";
			if (style==Font.BOLD)
				options += "bold";
			if (style==Font.ITALIC)
				options += " italic";
			if (antialiasedText)
				options += " antialiased";
			if (options.equals(""))
				options = "plain";
			code += "setFont(\""+name+"\", "+size+", \""+options+"\");\n";
		}
		ImageProcessor ip = imp.getProcessor();
		ip.setFont(new Font(name, style, size));
		FontMetrics metrics = ip.getFontMetrics();
		int fontHeight = metrics.getHeight();
		if (script)
			code += "ip.setColor(new Color("+getColorArgs(getStrokeColor())+"));\n";
		else
			code += "setColor(\""+Colors.colorToString(getStrokeColor())+"\");\n";
		if (addSelection) {
			code += "Overlay.drawString(\""+text()+"\", "+this.x+", "+(this.y+fontHeight)+", "+getAngle()+");\n";
			code += "Overlay.show();\n";
		} else {
			code += (script?"ip.":"")+"drawString(\""+text()+"\", "+this.x+", "+(this.y+fontHeight)+");\n";
			if (script)
				code += "imp.updateAndDraw();\n";
			else
				code += "//makeText(\""+text()+"\", "+this.x+", "+(this.y+fontHeight)+");\n";
		}
		return (code);
	}
	
	private String text() {
		String text = "";
		for (int i=0; i<MAX_LINES; i++) {
			if (theText[i]==null) break;
			text += theText[i];
			if (theText[i+1]!=null) text += "\\n";
		}
		return text;
	}
	
	private String getAddSelectionScript(String code) {
		code += "roi = new TextRoi("+this.x+", "+this.y+", \""+text()+"\", font);\n";
		code += "roi.setStrokeColor(new Color("+getColorArgs(getStrokeColor())+"));\n";
		if (getFillColor()!=null)
			code += "roi.setFillColor(new Color("+getColorArgs(getFillColor())+"));\n";
		if (getAngle()!=0.0)
			code += "roi.setAngle("+getAngle()+");\n";
		code += "overlay.add(roi);\n";
		return code;
	}
	
	private String getColorArgs(Color c) {
		return IJ.d2s(c.getRed()/255.0,2)+", "+IJ.d2s(c.getGreen()/255.0,2)+", "+IJ.d2s(c.getBlue()/255.0,2);
	}
	
	public String getText() {
		String text = "";
		for (int i=0; i<MAX_LINES; i++) {
			if (theText[i]==null) break;
			text += theText[i]+"\n";
		}
		return text;
	}
	
	public boolean isDrawingTool() {
		return true;
	}
	
	public void clear(ImageProcessor ip) {
		if (instanceFont==null)
			ip.fill();
		else {
			ip.setFont(instanceFont);
			ip.setAntialiasedText(antialiasedText);
			int i=0, w=0;
			while (i<MAX_LINES && theText[i]!=null) {
				int w2 = ip.getStringWidth(theText[i]);
				if (w2>w)
					w = w2;
				i++;
			}
			Rectangle r = ip.getRoi();
			if (w>r.width) {
				r.width = w;
				ip.setRoi(r);
			}
			ip.fill();
		}
	}

	@Override
	public void setLocation(int x, int y) {
		super.setLocation(x, y);
		oldWidth = this.width;
	}

	/** Returns a copy of this TextRoi. */
	public synchronized Object clone() {
		TextRoi tr = (TextRoi)super.clone();
		tr.theText = new String[MAX_LINES];
		for (int i=0; i<MAX_LINES; i++)
			tr.theText[i] = theText[i];
		return tr;
	}
	
	public double getAngle() {
		return angle;
	}
	
	public void setAngle(double angle) {
		this.angle = angle;
		if (angle!=0.0)
			setAntiAlias(true);
	}

	public boolean getDrawStringMode() {
		return drawStringMode;
	}
	
	public void setDrawStringMode(boolean drawStringMode) {
		this.drawStringMode = drawStringMode;
	}
	
	public void setPreviousRoi(Roi previousRoi) {
		this.previousRoi = previousRoi;
	}
	
	/** @deprecated Replaced by getDefaultFontName */
	public static String getFont() {
		return name;
	}

	/** @deprecated Replaced by getDefaultFontSize */
	public static int getSize() {
		return size;
	}

	/** @deprecated Replaced by getDefaultFontStyle */
	public static int getStyle() {
		return style;
	}
        
}