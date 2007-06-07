package ij.gui;
import java.awt.*;
import ij.*;
import ij.process.*;


/** This class is a rectangular ROI containing text. */
public class TextRoi extends Roi {

	static final int MAX_LINES = 50;

	private String[] theText = new String[MAX_LINES];
	private static String name = "SansSerif";
	private static int style = Font.PLAIN;
	private static int size = 18;
	private boolean firstChar = true;
	private int cline = 0;

	public TextRoi(int x, int y, ImagePlus imp) {
		super(x, y, imp);
		theText[0] = "Replace me";
		//type = OVAL;
	}

	/** Adds the specified character to the end of the text string. */
	public void addChar(char c) {
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
			imp.draw(clipX, clipY, clipWidth, clipHeight);
			firstChar = false;
			return;
		} else if ((int)c=='\n') {
			// newline
			if (cline<(MAX_LINES-1)) cline++;
			theText[cline] = "";
		} else {
			char[] chr = {c};
			theText[cline] += new String(chr);
			if (firstChar)
				imp.draw(clipX, clipY, clipWidth, clipHeight);
			else
				draw(ic.getGraphics());
			firstChar = false;
			return;
		}
	}

	/** Returns a mask that can be used to draw the text on an image. */
	public int[] getMask() {
		Image img = GUI.createBlankImage(width, height);
		Graphics g = img.getGraphics();
		g.setColor(Color.black);
		Font font = new Font(name, style, size);
		FontMetrics metrics = g.getFontMetrics(font);
		int fontHeight = metrics.getHeight();
		int descent = metrics.getDescent();
		g.setFont(font);
		int i = 0;
		int yy = 0;
		while (i<MAX_LINES && theText[i]!=null) {
			g.drawString(theText[i], 1, yy + fontHeight-descent+1);
			i++;
			yy += fontHeight;
		}
		g.dispose();
		ColorProcessor cp = new ColorProcessor(img);
		return (int[])cp.getPixels();
	}

	/** Draws the text on the screen, clipped to the ROI. */
	public void draw(Graphics g) {
		super.draw(g); // draw the rectangle
		double mag = ic.getMagnification();
		int sx = ic.screenX(x);
		int sy = ic.screenY(y);
		int swidth = (int)(width*mag);
		int sheight = (int)(height*mag);
		Font font = new Font(name, style, (int)(size*mag));
		FontMetrics metrics = g.getFontMetrics(font);
		int fontHeight = metrics.getHeight();
		int descent = metrics.getDescent();
		g.setFont(font);
		Rectangle r = g.getClipBounds();
		g.setClip(sx, sy, swidth, sheight);
		int i = 0;
		while (i<MAX_LINES && theText[i]!=null) {
			g.drawString(theText[i], sx+(int)(mag), sy+fontHeight-descent+(int)(mag));
			i++;
			sy += fontHeight;
		}
		if (r!=null) g.setClip(r.x, r.y, r.width, r.height);
	}

	/*
	void handleMouseUp(int screenX, int screenY) {
		if (width<size || height<size)
			grow(x+Math.max(size*5,width), y+Math.max((int)(size*1.5),height));
		super.handleMouseUp(screenX, screenY);
	}
	*/

	/** Returns the current font. */
	public static String getFont() {
		return name;
	}

	/** Returns the current font size. */
	public static int getSize() {
		return size;
	}

	/** Returns the current font style. */
	public static int getStyle() {
		return style;
	}

	/** Sets the font face, size and style. */
	public static void setFont(String fontName, int fontSize, int fontStyle) {
		name = fontName;
		size = fontSize;
		style = fontStyle;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			Roi roi = imp.getRoi();
			if (roi instanceof TextRoi)
				imp.draw();
		}
	}

}