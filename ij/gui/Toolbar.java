package ij.gui;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.*;
import ij.*;
import ij.plugin.frame.Recorder;

/** The ImageJ toolbar. */
public class Toolbar extends Canvas implements MouseListener {

	public static final int RECTANGLE = 0;
	public static final int OVAL = 1;
	public static final int POLYGON = 2;
	public static final int FREEROI = 3;
	public static final int LINE = 4;
	public static final int POLYLINE = 5;
	public static final int FREELINE = 6;
	public static final int CROSSHAIR = 7;
	public static final int WAND = 8;
	public static final int TEXT = 9;
	public static final int SPARE1 = 10;
	public static final int MAGNIFIER = 11;
	public static final int HAND = 12;
	public static final int DROPPER = 13;
	//public static final int NONE = 100;

	private static final int NUM_TOOLS = 20;
	private static final int SIZE = 22;
	private static final int OFFSET = 3;

	private Dimension ps = new Dimension(SIZE*NUM_TOOLS, SIZE);
	private boolean[] down;
	private static int current;
	private int previous;
	private int x,y;
	private int xOffset, yOffset;
	private long mouseDownTime;
	private Graphics g;
	private static Toolbar instance;
	private int mpPrevious = RECTANGLE;
	private String spareTip;


	private static Color foregroundColor = Prefs.getColor(Prefs.FCOLOR,Color.black);
	private static Color backgroundColor = Prefs.getColor(Prefs.BCOLOR,Color.white);
	
	private Color gray = Color.lightGray;
	private Color brighter = gray.brighter();
	private Color darker = gray.darker();
	private Color evenDarker = darker.darker();

	public Toolbar() {
		down = new boolean[NUM_TOOLS];
		resetButtons();
		down[0] = true;
		setForeground(foregroundColor);
		setBackground(gray);
		//setBackground(Color.red);
		addMouseListener(this);
		instance = this;
	}

	/** Returns the ID of the current tool (Toolbar.RECTANGLE,
		Toolbar.OVAL, etc.). */
	public static int getToolId() {
		return current;
	}

	/** Returns a reference to the ImageJ toolbar. */
	public static Toolbar getInstance() {
		return instance;
	}

	private void drawButtons(Graphics g) {
		for (int i=0; i<NUM_TOOLS; i++)
			drawButton(g, i);
	}

	private void fill3DRect(Graphics g, int x, int y, int width, int height, boolean raised) {
		if (raised)
			g.setColor(gray);
		else
			g.setColor(darker);
		g.fillRect(x+1, y+1, width-2, height-2);
		g.setColor(raised ? brighter : evenDarker);
		g.drawLine(x, y, x, y + height - 1);
		g.drawLine(x + 1, y, x + width - 2, y);
		g.setColor(raised ? evenDarker : brighter);
		g.drawLine(x + 1, y + height - 1, x + width - 1, y + height - 1);
		g.drawLine(x + width - 1, y, x + width - 1, y + height - 2);
	}    

	private void drawButton(Graphics g, int tool) {
		fill3DRect(g, tool*SIZE+1, 1, SIZE, SIZE-1, !down[tool]);
		g.setColor(Color.black);
		int x = tool*SIZE+OFFSET;
		int y = OFFSET;
		if (down[tool]) { x++; y++;}
		this.g = g;
		switch (tool) {
			case RECTANGLE:
				g.drawRect(x+1, y+2, 14, 11);
				return;
			case OVAL:
				g.drawOval(x+1, y+3, 14, 11);
				return;
			case POLYGON:
				xOffset = x+1; yOffset = y+3;
				m(4,0); d(14,0); d(14,1); d(10,5); d(10,6);
				d(13,9); d(13,10); d(0,10); d(0,4); d(4,0);
				return;
			case FREEROI:
				xOffset = x+1; yOffset = y+3;
				m(3,0); d(5,0); d(7,2); d(9,2); d(11,0); d(13,0); d(14,1); d(15,2);
				d(15,4); d(14,5); d(14,6); d(12,8); d(11,8); d(10,9); d(9,9); d(8,10);
				d(5,10); d(3,8); d(2,8); d(1,7); d(1,6); d(0,5); d(0,2); d(1,1); d(2,1);
				return;
			case LINE:
				xOffset = x; yOffset = y+4;
				m(0,0); d(16,6);
				return;
			case POLYLINE:
				xOffset = x+1; yOffset = y+3;
				m(0,3); d(3,0); d(13,0); d(13,1); d(8,6); d(12,10);
				return;
			case FREELINE:
				xOffset = x+1; yOffset = y+4;
				m(0,1); d(2,3); d(4,3); d(7,0); d(8,0); d(10,4); d(14,8); d(15,8);
				return;
			case CROSSHAIR:
				xOffset = x; yOffset = y;
				m(1,8); d(6,8); d(6,6); d(10,6); d(10,10); d(6,10); d(6,9);
				m(8,1); d(8,5); m(11,8); d(15,8); m(8,11); d(8,15);
				m(8,8); d(8,8);
				return;
			case WAND:
				xOffset = x+2; yOffset = y+2;
				m(4,0); d(4,0);  m(2,0); d(3,1); d(4,2);  m(0,0); d(1,1);
				m(0,2); d(1,3); d(2,4);  m(0,4); d(0,4);  m(3,3); d(12,12);
				return;
			case TEXT:
				xOffset = x+2; yOffset = y+1;
				m(0,13); d(3,13);
				m(1,12); d(7,0); d(12,13);
				m(11,13); d(14,13);
				m(3,8); d(10,8);
				return;
			case MAGNIFIER:
				xOffset = x+2; yOffset = y+2;
				m(3,0); d(3,0); d(5,0); d(8,3); d(8,5); d(7,6); d(7,7);
				d(6,7); d(5,8); d(3,8); d(0,5); d(0,3); d(3,0);
				m(8,8); d(9,8); d(13,12); d(13,13); d(12,13); d(8,9); d(8,8);
				return;
			case HAND:
				xOffset = x+1; yOffset = y+1;
				m(5,14); d(2,11); d(2,10); d(0,8); d(0,7); d(1,6); d(2,6); d(4,8); 
				d(4,6); d(3,5); d(3,4); d(2,3); d(2,2); d(3,1); d(4,1); d(5,2); d(5,3);
				m(6,5); d(6,1); d(7,0); d(8,0); d(9,1); d(9,5);
				m(9,1); d(11,1); d(12,2); d(12,6);
				m(13,4); d(14,3); d(15,4); d(15,7); d(14,8);
				d(14,10); d(13,11); d(13,12); d(12,13); d(12,14);
				return;
			case DROPPER:
				xOffset = x; yOffset = y;
				g.setColor(foregroundColor);
				//m(0,0); d(17,0); d(17,17); d(0,17); d(0,0);
				m(12,2); d(14,2);
				m(11,3); d(15,3);
				m(11,4); d(15,4);
				m(8,5); d(15,5);
				m(9,6); d(14,6);
				m(10,7); d(12,7); d(12,9);
				m(8,7); d(2,13); d(2,15); d(4,15); d(11,8);
				g.setColor(backgroundColor);
				m(0,0); d(16,0); d(16,16); d(0,16); d(0,0);
				g.setColor(Color.black);
				/*
				xOffset = x; yOffset = y;
				g.setColor(backgroundColor);
				g.fillOval(x+2,y+2,14,13);
				g.setColor(foregroundColor);
				g.fillOval(x+4,y+4,10,9);
				g.setColor(Color.black);
				*/
				return;
		}
	}

	private void showMessage(int tool) {
		switch (tool) {
			case RECTANGLE:
				IJ.showStatus("Rectangular selections");
				return;
			case OVAL:
				IJ.showStatus("Oval selections");
				return;
			case POLYGON:
				IJ.showStatus("Polygon selections");
				return;
			case FREEROI:
				IJ.showStatus("Freehand selections");
				return;
			case LINE:
				IJ.showStatus("Straight line selections");
				return;
			case POLYLINE:
				IJ.showStatus("Segmented line selections");
				return;
			case FREELINE:
				IJ.showStatus("Freehand line selections");
				return;
			case CROSSHAIR:
				IJ.showStatus("Crosshair (mark and count) tool");
				return;
			case WAND:
				IJ.showStatus("Wand (tracing) tool");
				return;
			case TEXT:
				IJ.showStatus("Text tool");
				return;
			case MAGNIFIER:
				IJ.showStatus("Magnifying glass");
				return;
			case HAND:
				IJ.showStatus("Scrolling tool");
				return;
			case DROPPER:
				IJ.showStatus("Color picker (" + foregroundColor.getRed() + ","
				+ foregroundColor.getGreen() + "," + foregroundColor.getBlue() + ")");
				return;
			case SPARE1:
				if (spareTip!=null)
					IJ.showStatus(spareTip);
				return;
			default:
				IJ.showStatus("");
				return;
		}
	}

	private void m(int x, int y) {
		this.x = xOffset+x;
		this.y = yOffset+y;
	}

	private void d(int x, int y) {
		x += xOffset;
		y += yOffset;
		g.drawLine(this.x, this.y, x, y);
		this.x = x;
		this.y = y;
	}

	private void resetButtons() {
		for (int i=0; i<NUM_TOOLS; i++)
			down[i] = false;
	}

	public void paint(Graphics g) {
		drawButtons(g);
	}

	public void setTool(int tool) {
		if (tool==current || tool<0 || tool>DROPPER
		|| (tool==SPARE1&&spareTip==null))
			return;
		current = tool;
		down[current] = true;
		down[previous] = false;
		Graphics g = this.getGraphics();
		drawButton(g, previous);
		drawButton(g, current);
		g.dispose();
		showMessage(current);
		previous = current;
		if (Recorder.record)
			Recorder.record("setTool", current);
	}
	
	/** Obsolete. Use getForegroundColor(). */
	public Color getColor() {
		return foregroundColor;
	}

	/** Obsolete. Use setForegroundColor(). */
	public void setColor(Color c) {
		foregroundColor = c;
		drawButton(this.getGraphics(), DROPPER);
	}

	public static Color getForegroundColor() {
		return foregroundColor;
	}

	public static void setForegroundColor(Color c) {
		foregroundColor = c;
		updateColors();
	}

	public static Color getBackgroundColor() {
		return backgroundColor;
	}

	public static void setBackgroundColor(Color c) {
		backgroundColor = c;
		updateColors();
	}
	
	static void updateColors() {
		Toolbar tb = getInstance();
		Graphics g = tb.getGraphics();
		tb.drawButton(g, DROPPER);
		tb.drawButton(g, CROSSHAIR);
		g.dispose();
	}

	public void mousePressed(MouseEvent e) {
		int x = e.getX();
 		int newTool = 0;
		for (int i=0; i<NUM_TOOLS; i++)
			if (x>i*SIZE && x<i*SIZE+SIZE)
				newTool = i;
		boolean doubleClick = newTool==current && (System.currentTimeMillis()-mouseDownTime)<=500;
 		mouseDownTime = System.currentTimeMillis();
		if (!doubleClick) {
			mpPrevious = current;
			setTool(newTool);
		} else {
			ImagePlus imp = WindowManager.getCurrentImage();
			switch (current) {
				case FREEROI:
					IJ.doCommand("Set Measurements...");
					setTool(mpPrevious);
					break;
				case MAGNIFIER:
					if (imp!=null) imp.getWindow().getCanvas().unzoom();
					break;
				case POLYGON:
					if (imp!=null) IJ.doCommand("Calibrate...");
					setTool(mpPrevious);
					break;
				case LINE: case POLYLINE: case FREELINE:
					IJ.doCommand("Line Width...");
					break;
				case CROSSHAIR:
					IJ.doCommand("Crosshair...");
					break;
				case TEXT:
					IJ.doCommand("Fonts...");
					break;
				case DROPPER:
					IJ.doCommand("Colors...");
					setTool(mpPrevious);
					break;
				default:
			}
		}
	}
	
	public void mouseReleased(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	
	public void selectTool(int key) {
		switch(key) {
			case KeyEvent.VK_F1: setTool(RECTANGLE); break;
			case KeyEvent.VK_F2: setTool(OVAL); break;
			case KeyEvent.VK_F3: setTool(POLYGON); break;
			case KeyEvent.VK_F4: setTool(FREEROI); break;
			case KeyEvent.VK_F5: setTool(LINE); break;
			case KeyEvent.VK_F6: setTool(POLYLINE); break;
			case KeyEvent.VK_F7: setTool(FREELINE); break;
			case KeyEvent.VK_F8: setTool(CROSSHAIR); break;
			case KeyEvent.VK_F9: setTool(WAND); break;
			case KeyEvent.VK_F10: setTool(TEXT); break;
			case KeyEvent.VK_F11: setTool(MAGNIFIER); break;
			case KeyEvent.VK_F12: setTool(HAND); break;
			default: break;
		}
	}

	public Dimension getPreferredSize(){
		return ps;
	}

	public Dimension getMinimumSize(){
		return ps;
	}
	
	/** Enables the unused tool between the text and zoom tools.
		The specified string is displayed in the status bar when
		the user enables this tool. Returns the tool ID. Future
		versions may support multiple tools and custom icons. */
	public int addTool(String toolTip) {
		spareTip = toolTip;
		return SPARE1;
	}

}
