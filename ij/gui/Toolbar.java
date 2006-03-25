package ij.gui;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.*;
import ij.*;
import ij.plugin.frame.Recorder;
import ij.plugin.MacroInstaller;

/** The ImageJ toolbar. */
public class Toolbar extends Canvas implements MouseListener, MouseMotionListener {

	public static final int RECTANGLE = 0;
	public static final int OVAL = 1;
	public static final int POLYGON = 2;
	public static final int FREEROI = 3;
	public static final int LINE = 4;
	public static final int POLYLINE = 5;
	public static final int FREELINE = 6;
	public static final int POINT = 7, CROSSHAIR = 7;
	public static final int WAND = 8;
	public static final int TEXT = 9;
	public static final int SPARE1 = 10;
	public static final int MAGNIFIER = 11;
	public static final int HAND = 12;
	public static final int DROPPER = 13;
	public static final int ANGLE = 14;
	public static final int SPARE2 = 15;
	public static final int SPARE3 = 16;
	public static final int SPARE4 = 17;
	public static final int SPARE5 = 18;
	public static final int SPARE6 = 19;
	public static final int SPARE7 = 20;
	//public static final int NONE = 100;
	
	public static final int DOUBLE_CLICK_THRESHOLD = 650;

	private static final int NUM_TOOLS = 21;
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
	private String[] names = new String[NUM_TOOLS];
	private String[] icons = new String[NUM_TOOLS];
	private int pc;
	private String icon;
	private MacroInstaller macroInstaller;

	private static Color foregroundColor = Prefs.getColor(Prefs.FCOLOR,Color.black);
	private static Color backgroundColor = Prefs.getColor(Prefs.BCOLOR,Color.white);
	
	private Color gray = ImageJ.backgroundColor;
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
		addMouseMotionListener(this);
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
		if (null==g) return;
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
		if (null==g) return;
        int index = toolIndex(tool);
        fill3DRect(g, index * 22 + 1, 1, 22, 21, !down[tool]);
        g.setColor(Color.black);
        int x = index * 22 + 3;
		int y = OFFSET;
		if (down[tool]) { x++; y++;}
		this.g = g;
		if (tool>=SPARE1 && tool<=SPARE7 && icons[tool]!=null) {
			drawIcon(g, icons[tool], x, y);
			return;
		}
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
				xOffset = x; yOffset = y+5;
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
			case POINT:
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
			case ANGLE:
				xOffset = x+1; yOffset = y+2;
				m(0,11); d(11,0); m(0,11); d(15,11); 
				m(10,11); d(10,8); m(9,7); d(9,6); m(8,5); d(8,5);
				//m(0,9); d(14,0); m(0,9); d(16,9); 
				//m(12,9); d(12,7); m(11,7); d(11,5); m(10,4); d(10,3);
				return;
		}
	}
	
	void drawIcon(Graphics g, String icon, int x, int y) {
		if (null==g) return;
		this.icon = icon;
		int length = icon.length();
		int x1, y1, x2, y2;
		pc = 0;
		while (true) {
			char command = icon.charAt(pc++);
			if (pc>=length) break;
			switch (command) {
				case 'B': x+=v(); y+=v(); break;  // reset base
				case 'R': g.drawRect(x+v(), y+v(), v(), v()); break;  // rectangle
				case 'F': g.fillRect(x+v(), y+v(), v(), v()); break;  // filled rectangle
				case 'O': g.drawOval(x+v(), y+v(), v(), v()); break;  // oval
				case 'o': g.fillOval(x+v(), y+v(), v(), v()); break;  // filled oval
				case 'C': g.setColor(new Color(v()*16,v()*16,v()*16)); break; // set color
				case 'L': g.drawLine(x+v(), y+v(), x+v(), y+v()); break; // line
				case 'D': g.drawLine(x1=x+v(), x2=y+v(), x1, x2); break; // dot
				case 'P': // polyline
					x1=x+v(); y1=y+v();
					while (true) {
						x2=v(); if (x2==0) break;
						y2=v(); if (y2==0) break;
						x2+=x; y2+=y;
						g.drawLine(x1, y1, x2, y2);
						x1=x2; y1=y2;
					}
					break;
				case 'T': // text (one character)
					x2 = x+v();
					y2 = y+v();
					int size = v()*10+v();
					char[] c = new char[1];
					c[0] = pc<icon.length()?icon.charAt(pc++):'e';
					g.setFont(new Font("SansSerif", Font.BOLD, size));
					g.drawString(new String(c), x2, y2);
					break;
				default: break;
			}
			if (pc>=length) break;
		}
		g.setColor(Color.black);
	}
	
	int v() {
		if (pc>=icon.length()) return 0;
		char c = icon.charAt(pc++);
		//IJ.log("v: "+pc+" "+c+" "+toInt(c));
		switch (c) {
			case '0': return 0;
			case '1': return 1;
			case '2': return 2;
			case '3': return 3;
			case '4': return 4;
			case '5': return 5;
			case '6': return 6;
			case '7': return 7;
			case '8': return 8;
			case '9': return 9;
			case 'a': return 10;
			case 'b': return 11;
			case 'c': return 12;
			case 'd': return 13;
			case 'e': return 14;
			case 'f': return 15;
			default: return 0;
		}
	}
	
	private void showMessage(int tool) {
		if (tool>=SPARE1 && tool<=SPARE7 && names[tool]!=null) {
			IJ.showStatus(names[tool]);
			return;
		}
		switch (tool) {
			case RECTANGLE:
				IJ.showStatus("Rectangular selections");
				return;
			case OVAL:
				IJ.showStatus("Elliptical selections");
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
			case POINT:
				IJ.showStatus("Point selections");
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
			case ANGLE:
				IJ.showStatus("Angle tool");
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
		if (null==g) return;
		drawButtons(g);
	}

	public void setTool(int tool) {
		if (tool==current || tool<0 || tool>=NUM_TOOLS)
			return;
		if ((tool==SPARE1||(tool>=SPARE2&&tool<=SPARE7)) && names[tool]==null)
			names[tool] = "Spare tool"; // enable tool
		setTool2(tool);
	}
	
	private void setTool2(int tool) {
		if (tool==current || tool<0 || tool>=NUM_TOOLS)
			return;
		if ((tool==SPARE1||(tool>=SPARE2&&tool<=SPARE7)) && names[tool]==null)
			return;
		current = tool;
		down[current] = true;
		down[previous] = false;
		Graphics g = this.getGraphics();
		drawButton(g, previous);
		drawButton(g, current);
		if (null==g) return;
		g.dispose();
		showMessage(current);
		previous = current;
		if (Recorder.record)
			Recorder.record("setTool", current);
		if (IJ.isMacOSX())
			repaint();
	}

	/** Obsolete. Use getForegroundColor(). */
	public Color getColor() {
		return foregroundColor;
	}

	/** Obsolete. Use setForegroundColor(). */
	public void setColor(Color c) {
		if (c!=null) {
			foregroundColor = c;
			drawButton(this.getGraphics(), DROPPER);
		}
	}

	public static Color getForegroundColor() {
		return foregroundColor;
	}

	public static void setForegroundColor(Color c) {
		if (c!=null) {
			foregroundColor = c;
			updateColors();
		}
	}

	public static Color getBackgroundColor() {
		return backgroundColor;
	}

	public static void setBackgroundColor(Color c) {
		if (c!=null) {
			backgroundColor = c;
			updateColors();
		}
	}
	
	static void updateColors() {
		if (IJ.getInstance()!=null) {
			Toolbar tb = getInstance();
			Graphics g = tb.getGraphics();
			tb.drawButton(g, DROPPER);
			tb.drawButton(g, POINT);
			if (g!=null) g.dispose();
		}
	}
	
	// Returns the toolbar position index of the specified tool
    int toolIndex(int tool){
        if(tool<=FREELINE || tool>ANGLE)
            return tool;
        if(tool == ANGLE)
            return 7;
        return tool + 1;
    }

	// Returns the tool corresponding to the specified tool position index
    int toolID(int index) {
        if(index<=6 || index>14)
            return index;
        if(index == 7)
            return ANGLE;
        return index - 1;
    }

	public void mousePressed(MouseEvent e) {
		int x = e.getX();
 		int newTool = 0;
		for (int i=0; i<NUM_TOOLS; i++) {
			if (x>i*SIZE && x<i*SIZE+SIZE)
				newTool = toolID(i);
		}
		boolean doubleClick = newTool==current && (System.currentTimeMillis()-mouseDownTime)<=DOUBLE_CLICK_THRESHOLD;
 		mouseDownTime = System.currentTimeMillis();
		if (!doubleClick) {
			if (isMacroTool(newTool)) {
				String name = names[newTool].endsWith(" ")?names[newTool]:names[newTool]+" ";
				macroInstaller.runMacroTool(name+"Selected");
			}
			mpPrevious = current;
			setTool2(newTool);
		} else {
			if (isMacroTool(current)) {
				String name = names[current].endsWith(" ")?names[current]:names[current]+" ";
				macroInstaller.runMacroTool(name+"Options");
				return;
			}
			ImagePlus imp = WindowManager.getCurrentImage();
			switch (current) {
				case FREEROI:
					IJ.doCommand("Set Measurements...");
					setTool2(mpPrevious);
					break;
				case MAGNIFIER:
					if (imp!=null) {
						ImageWindow win = imp.getWindow();
						if (win!=null) win.getCanvas().unzoom();
					}
					break;
				case POLYGON:
					if (imp!=null) IJ.doCommand("Calibrate...");
					setTool2(mpPrevious);
					break;
				case LINE: case POLYLINE: case FREELINE:
					IJ.runPlugIn("ij.plugin.frame.LineWidthAdjuster", "");
					break;
				case POINT:
					IJ.doCommand("Point Tool...");
					break;
				case TEXT:
					IJ.doCommand("Fonts...");
					break;
				case DROPPER:
					IJ.doCommand("Color Picker...");
					setTool2(mpPrevious);
					break;
				default:
			}
		}
	}
	
	public void restorePreviousTool() {
		setTool2(mpPrevious);
	}
	
	boolean isMacroTool(int tool) {
		return tool>=SPARE1 && tool<=SPARE7 && names[tool]!=null && macroInstaller!=null;
	}
	
	public void mouseReleased(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
    public void mouseDragged(MouseEvent e) {}
	
	public Dimension getPreferredSize(){
		return ps;
	}

	public Dimension getMinimumSize(){
		return ps;
	}
	
	public void mouseMoved(MouseEvent e) {
		int x = e.getX();
		x=toolID(x/SIZE);
		showMessage(x);
	}

	/** Enables the unused tool between the text and zoom tools. The 'toolTip' string 
		is displayed in the status bar when the user clicks on the tool. If the 'toolTip'
		string includes an icon (see Tools.txt macro), enables the next available tool
		and draws it using that icon. Returns the tool ID, or -1 if all tools are in use. */
	public int addTool(String toolTip) {
		int index = toolTip.indexOf('-');
		boolean hasIcon = index>=0 && (toolTip.length()-index)>4;
		if (!hasIcon) {
			names[SPARE1] = toolTip;
			return SPARE1;
		}
		int tool =  -1;
		if (names[SPARE1]==null)
			tool = SPARE1;
		if (tool==-1) {
			for (int i=SPARE2; i<=SPARE7; i++) {
				if (names[i]==null) {
					tool = i;
					break;
				}			
			}
		}
		if (tool==-1)
			return -1;
		icons[tool] = toolTip.substring(index+1);
		names[tool] = toolTip.substring(0, index);
		return tool;
	}

	/** Used by the MacroInstaller class to install macro tools. */
	public void addMacroTool(String name, MacroInstaller macroInstaller, int id) {
	    if (id==0) {
			for (int i=SPARE1; i<NUM_TOOLS; i++) {
				names[i] = null;
				icons[i] = null;
			}
	    }
		int tool = addTool(name);
		this.macroInstaller = macroInstaller;
	}
			
	void runMacroTool(int id) {
		if (macroInstaller!=null)
			macroInstaller.runMacroTool(names[id]);
	}

}
