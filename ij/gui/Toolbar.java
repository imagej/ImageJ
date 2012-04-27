package ij.gui;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import ij.*;
import ij.plugin.frame.Recorder; 
import ij.plugin.frame.Editor; 
import ij.plugin.MacroInstaller;
import ij.plugin.RectToolOptions;
import ij.plugin.tool.PlugInTool;
import ij.plugin.tool.MacroToolRunner;
import ij.macro.Program;

/** The ImageJ toolbar. */
public class Toolbar extends Canvas implements MouseListener, MouseMotionListener, ItemListener, ActionListener {

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
	public static final int SPARE8 = 21;
	public static final int SPARE9 = 22;
	
	public static final int DOUBLE_CLICK_THRESHOLD = 650;

	public static final int OVAL_ROI=0, ELLIPSE_ROI=1, BRUSH_ROI=2;

	private static final int NUM_TOOLS = 23;
	private static final int NUM_BUTTONS = 21;
	private static final int SIZE = 26;
	private static final int OFFSET = 5;
	private static final String BRUSH_SIZE = "toolbar.brush.size";
	public static final String CORNER_DIAMETER = "toolbar.arc.size";
	public static String TOOL_KEY = "toolbar.tool";
		
	private Dimension ps = new Dimension(SIZE*NUM_BUTTONS, SIZE);
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
	private PlugInTool[] tools = new PlugInTool[NUM_TOOLS];
    private PopupMenu[] menus = new PopupMenu[NUM_TOOLS];
    private MacroInstaller macroInstaller;
    private boolean addingSingleTool;
    private boolean installingStartupTool;
	private int pc;
	private String icon;
	private int startupTime;
	private PopupMenu rectPopup, ovalPopup, pointPopup, linePopup, switchPopup;
	private CheckboxMenuItem rectItem, roundRectItem;
	private CheckboxMenuItem ovalItem, ellipseItem, brushItem;
	private CheckboxMenuItem pointItem, multiPointItem;
	private CheckboxMenuItem straightLineItem, polyLineItem, freeLineItem, arrowItem;
	private String currentSet = "Startup Macros";

	private static Color foregroundColor = Prefs.getColor(Prefs.FCOLOR,Color.black);
	private static Color backgroundColor = Prefs.getColor(Prefs.BCOLOR,Color.white);
	private static int ovalType = OVAL_ROI;
	private static boolean multiPointMode = Prefs.multiPointMode;
	private static boolean roundRectMode;
	private static boolean arrowMode;
	private static int brushSize = (int)Prefs.get(BRUSH_SIZE, 15);
	private static int arcSize = (int)Prefs.get(CORNER_DIAMETER, 20);
	private int lineType = LINE;
	
	private Color gray = ImageJ.backgroundColor;
	private Color brighter = gray.brighter();
	private Color darker = new Color(175, 175, 175);
	private Color evenDarker = new Color(110, 110, 110);
	private Color triangleColor = new Color(150, 0, 0);
	private Color toolColor = new Color(0, 25, 45);


	public Toolbar() {
		down = new boolean[NUM_TOOLS];
		resetButtons();
		down[0] = true;
		setForeground(Color.black);
		setBackground(gray);
		addMouseListener(this);
		addMouseMotionListener(this);
		instance = this;
		names[NUM_TOOLS-1] = "\"More Tools\" menu (switch toolsets or add tools)";
		icons[NUM_TOOLS-1] = "C900T1c12>T7c12>"; // ">>"
		addPopupMenus();
		if (IJ.isMacOSX() || IJ.isVista()) Prefs.antialiasedTools = true;
	}

	void addPopupMenus() {
		rectPopup = new PopupMenu();
		if (Menus.getFontSize()!=0)
			rectPopup.setFont(Menus.getFont());
		rectItem = new CheckboxMenuItem("Rectangle Tool", !roundRectMode);
		rectItem.addItemListener(this);
		rectPopup.add(rectItem);
		roundRectItem = new CheckboxMenuItem("Rounded Rectangle Tool", roundRectMode);
		roundRectItem.addItemListener(this);
		rectPopup.add(roundRectItem);
		add(rectPopup);

		ovalPopup = new PopupMenu();
		if (Menus.getFontSize()!=0)
			ovalPopup.setFont(Menus.getFont());
		ovalItem = new CheckboxMenuItem("Oval selections", ovalType==OVAL_ROI);
		ovalItem.addItemListener(this);
		ovalPopup.add(ovalItem);
		ellipseItem = new CheckboxMenuItem("Elliptical selections", ovalType==ELLIPSE_ROI);
		ellipseItem.addItemListener(this);
		ovalPopup.add(ellipseItem);
		brushItem = new CheckboxMenuItem("Selection Brush Tool", ovalType==BRUSH_ROI);
		brushItem.addItemListener(this);
		ovalPopup.add(brushItem);
		add(ovalPopup);

		pointPopup = new PopupMenu();
		if (Menus.getFontSize()!=0)
			pointPopup.setFont(Menus.getFont());
		pointItem = new CheckboxMenuItem("Point Tool", !multiPointMode);
		pointItem.addItemListener(this);
		pointPopup.add(pointItem);
		multiPointItem = new CheckboxMenuItem("Multi-point Tool", multiPointMode);
		multiPointItem.addItemListener(this);
		pointPopup.add(multiPointItem);
		add(pointPopup);

		linePopup = new PopupMenu();
		if (Menus.getFontSize()!=0)
			linePopup.setFont(Menus.getFont());
		straightLineItem = new CheckboxMenuItem("Straight Line", lineType==LINE&&!arrowMode);
		straightLineItem.addItemListener(this);
		linePopup.add(straightLineItem);
		polyLineItem = new CheckboxMenuItem("Segmented Line", lineType==POLYLINE);
		polyLineItem.addItemListener(this);
		linePopup.add(polyLineItem);
		freeLineItem = new CheckboxMenuItem("Freehand Line", lineType==FREELINE);
		freeLineItem.addItemListener(this);
		linePopup.add(freeLineItem);
		arrowItem = new CheckboxMenuItem("Arrow tool", lineType==LINE&&!arrowMode);
		arrowItem.addItemListener(this);
		linePopup.add(arrowItem);
		add(linePopup);

		switchPopup = new PopupMenu();
		if (Menus.getFontSize()!=0)
			switchPopup.setFont(Menus.getFont());
		add(switchPopup);
	}
	
	/** Returns the ID of the current tool (Toolbar.RECTANGLE,
		Toolbar.OVAL, etc.). */
	public static int getToolId() {
		return current;
	}

	/** Returns the ID of the tool whose name (the description displayed in the status bar)
		starts with the specified string, or -1 if the tool is not found. */
	public int getToolId(String name) {
		int tool =  -1;
		for (int i=0; i<=SPARE9; i++) {
			if (names[i]!=null && names[i].startsWith(name)) {
				tool = i;
				break;
			}			
		}
		return tool;
	}

	/** Returns a reference to the ImageJ toolbar. */
	public static Toolbar getInstance() {
		return instance;
	}

	private void drawButtons(Graphics g) {
		if (Prefs.antialiasedTools) {
			Graphics2D g2d = (Graphics2D)g;
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		}
		for (int i=0; i<LINE; i++)
			drawButton(g, i);
		drawButton(g, lineType);
		for (int i=POINT; i<NUM_TOOLS; i++)
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
		if (g==null) return;
        int index = toolIndex(tool);
        fill3DRect(g, index * SIZE + 1, 1, SIZE, SIZE-1, !down[tool]);
        g.setColor(toolColor);
        int x = index * SIZE + OFFSET;
		int y = OFFSET;
		if (down[tool]) { x++; y++;}
		this.g = g;
		if (tool>=SPARE1 && tool<=SPARE9 && icons[tool]!=null) {
			drawIcon(g, tool, x, y);
			return;
		}
		switch (tool) {
			case RECTANGLE:
				xOffset = x; yOffset = y;
				if (roundRectMode)
					g.drawRoundRect(x+1, y+2, 15, 12, 8, 8);
				else
					g.drawRect(x+1, y+2, 15, 12);
				drawTriangle(15,14);
				return;
			case OVAL:
				xOffset = x; yOffset = y;
				if (ovalType==BRUSH_ROI) {
					m(9,2); d(13,2); d(13,2); d(15,5); d(15,8);
					d(13,10); d(10,10); d(8,13); d(4,13); 
					d(2,11);  d(2,7); d(4,5); d(7,5); d(9,2);
				} else if (ovalType==ELLIPSE_ROI) {
					yOffset = y + 1;
					m(11,0); d(13,0); d(14,1); d(15,1); d(16,2); d(17,3); d(17,7);
					d(12,12); d(11,12); d(10,13); d(8,13); d(7,14); d(4,14); d(3,13);
					d(2,13); d(1,12); d(1,11); d(0,10); d(0,9); d(1,8); d(1,7);
					d(6,2); d(7,2); d(8,1); d(10,1); d(11,0);
				} else
					g.drawOval(x+1, y+2, 15, 12);
				drawTriangle(15,14);
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
				xOffset = x; yOffset = y;
				if (arrowMode) {
					m(1,14); d(14,1); m(6,5); d(14,1); m(10,9); d(14,1); m(6,5); d(10,9);
				} else {
					m(0,12); d(17,3);
				}
				drawTriangle(12,14);
				return;
			case POLYLINE:
				xOffset = x; yOffset = y;
				m(14,6); d(11,3); d(1,3); d(1,4); d(6,9); d(2,13);
				drawTriangle(12,14);
				return;
			case FREELINE:
				xOffset = x; yOffset = y;
				m(16,4); d(14,6); d(12,6); d(9,3); d(8,3); d(6,7); d(2,11); d(1,11);
				drawTriangle(12,14);
				return;
			case POINT:
				xOffset = x; yOffset = y;
				if (multiPointMode) {
					drawPoint(1,3); drawPoint(9,1); drawPoint(15,5);
					drawPoint(10,11); drawPoint(2,12);
				} else {
					m(1,8); d(6,8); d(6,6); d(10,6); d(10,10); d(6,10); d(6,9);
					m(8,1); d(8,5); m(11,8); d(15,8); m(8,11); d(8,15);
					m(8,8); d(8,8);
					g.setColor(Roi.getColor());
					g.fillRect(x+7, y+7, 3, 3);
				}
				drawTriangle(14,14);
				return;
			case WAND:
				xOffset = x+2; yOffset = y+2;
				dot(4,0);  m(2,0); d(3,1); d(4,2);  m(0,0); d(1,1);
				m(0,2); d(1,3); d(2,4);  dot(0,4); m(3,3); d(12,12);
				g.setColor(Roi.getColor());
				m(1,2); d(3,2); m(2,1); d(2,3);
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
				return;
			case ANGLE:
				xOffset = x+1; yOffset = y+2;
				m(0,11); d(11,0); m(0,11); d(15,11); 
				m(10,11); d(10,8); m(9,7); d(9,6); dot(8,5);
				return;
		}
	}
	
	void drawTriangle(int x, int y) {
		g.setColor(triangleColor);
		xOffset+=x; yOffset+=y;
		m(0,0); d(4,0); m(1,1); d(3,1); dot(2,2);
	}
	
	void drawPoint(int x, int y) {
		g.setColor(toolColor);
		m(x-2,y); d(x+2,y);
		m(x,y-2); d(x,y+2);
		g.setColor(Roi.getColor());
		dot(x,y);
	}
	
	void drawIcon(Graphics g, int tool, int x, int y) {
		if (null==g) return;
		icon = icons[tool];
		if (icon==null) return;
		this.icon = icon;
		int x1, y1, x2, y2;
		pc = 0;
		while (true) {
			char command = icon.charAt(pc++);
			if (pc>=icon.length()) break;
			switch (command) {
				case 'B': x+=v(); y+=v(); break;  // reset base
				case 'R': g.drawRect(x+v(), y+v(), v(), v()); break;  // rectangle
				case 'F': g.fillRect(x+v(), y+v(), v(), v()); break;  // filled rectangle
				case 'O': g.drawOval(x+v(), y+v(), v(), v()); break;  // oval
				case 'o': g.fillOval(x+v(), y+v(), v(), v()); break;  // filled oval
				case 'C': // set color
					int v1=v(), v2=v(), v3=v();
					Color color = v1==1&&v2==2&&v3==3?foregroundColor:new Color(v1*16,v2*16,v3*16);
					g.setColor(color);
					break; 
				case 'L': g.drawLine(x+v(), y+v(), x+v(), y+v()); break; // line
				case 'D': g.fillRect(x+v(), y+v(), 1, 1); break; // dot
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
			if (pc>=icon.length()) break;
		}
		if (menus[tool]!=null && menus[tool].getItemCount()>0) { 
			xOffset = x; yOffset = y;
			drawTriangle(14, 14);
		}
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
		if (tool>=SPARE1 && tool<=SPARE9 && names[tool]!=null) {
			String name = names[tool];
			int index = name.indexOf("Action Tool");
			if (index!=-1)
				name = name.substring(0, index);
			else {
				index = name.indexOf("Menu Tool");
				if (index!=-1)
					name = name.substring(0, index+4);
			}
			IJ.showStatus(name);
			return;
		}
		String hint = " (right click to switch)";
		switch (tool) {
			case RECTANGLE:
				if (roundRectMode)
					IJ.showStatus("Rectangular or *rounded rectangular* selections"+hint);
				else
					IJ.showStatus("*Rectangular* or rounded rectangular selections"+hint);
				return;
			case OVAL:
				if (ovalType==BRUSH_ROI)
					IJ.showStatus("Oval, elliptical or *brush* selections"+hint);
				else if (ovalType==ELLIPSE_ROI)
					IJ.showStatus("Oval, *elliptical* or brush selections"+hint);
				else
					IJ.showStatus("*Oval*, elliptical or brush selections"+hint);
				return;
			case POLYGON:
				IJ.showStatus("Polygon selections");
				return;
			case FREEROI:
				IJ.showStatus("Freehand selections");
				return;
			case LINE:
				if (arrowMode)
					IJ.showStatus("Straight, segmented or freehand lines, or *arrows*"+hint);
				else
					IJ.showStatus("*Straight*, segmented or freehand lines, or arrows"+hint);
				return;
			case POLYLINE:
				IJ.showStatus("Straight, *segmented* or freehand lines, or arrows"+hint);
				return;
			case FREELINE:
				IJ.showStatus("Straight, segmented or *freehand* lines, or arrows"+hint);
				return;
			case POINT:
				if (multiPointMode)
					IJ.showStatus("Point or *multi-point* selections"+hint);
				else
					IJ.showStatus("*Point* or multi-point selections"+hint);
				return;
			case WAND:
				IJ.showStatus("Wand (tracing) tool");
				return;
			case TEXT:
				IJ.showStatus("Text tool");
				TextRoi.recordSetFont();
				return;
			case MAGNIFIER:
				IJ.showStatus("Magnifying glass (or use \"+\" and \"-\" keys)");
				return;
			case HAND:
				IJ.showStatus("Scrolling tool (or press space bar and drag)");
				return;
			case DROPPER:
				IJ.showStatus("Color picker (" + foregroundColor.getRed() + ","
				+ foregroundColor.getGreen() + "," + foregroundColor.getBlue() + ")");
				return;
			case ANGLE:
				IJ.showStatus("Angle tool");
				return;
			default:
				IJ.showStatus("ImageJ "+IJ.getVersion()+" / Java "+System.getProperty("java.version")+(IJ.is64Bit()?" (64-bit)":" (32-bit)"));
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
	
	private void dot(int x, int y) {
		g.fillRect(x+xOffset, y+yOffset, 1, 1);
	}

	private void resetButtons() {
		for (int i=0; i<NUM_TOOLS; i++)
			down[i] = false;
	}

	public void paint(Graphics g) {
		if (null==g) return;
		drawButtons(g);
	}

	public boolean setTool(String name) {
		if (name==null) return false;
		if (name.indexOf(" Tool")!=-1) { // macro tool?
			for (int i=SPARE1; i<=SPARE9; i++) {
				if (name.equals(names[i])) {
					setTool(i);
					return true;
				}
			}
		}
		name = name.toLowerCase(Locale.US);
		boolean ok = true;
		if (name.indexOf("round")!=-1) {
			roundRectMode = true;
			setTool(RECTANGLE);
		} else if (name.indexOf("rect")!=-1) {
			roundRectMode = false;
			setTool(RECTANGLE);
		} else if (name.indexOf("oval")!=-1) {
			ovalType = OVAL_ROI;
			setTool(OVAL);
		} else if (name.indexOf("ellip")!=-1) {
			ovalType = ELLIPSE_ROI;
			setTool(OVAL);
		} else if (name.indexOf("brush")!=-1) {
			ovalType = BRUSH_ROI;
			setTool(OVAL);
		} else if (name.indexOf("polygon")!=-1)
			setTool(POLYGON);
		else if (name.indexOf("polyline")!=-1)
			setTool(POLYLINE);
		else if (name.indexOf("freeline")!=-1)
			setTool(FREELINE);
		else if (name.indexOf("line")!=-1) {
			arrowMode = false;
			setTool(LINE);
		} else if (name.indexOf("arrow")!=-1) {
			arrowMode = true;
			setTool(LINE);
		} else if (name.indexOf("free")!=-1)
			setTool(FREEROI);
		else if (name.indexOf("multi")!=-1) {
			multiPointMode = true;
			Prefs.multiPointMode = true;
			setTool(POINT);
		} else if (name.indexOf("point")!=-1) {
			multiPointMode = false;
			Prefs.multiPointMode = false;
			setTool(POINT);
		} else if (name.indexOf("wand")!=-1)
			setTool(WAND);
		else if (name.indexOf("text")!=-1)
			setTool(TEXT);
		else if (name.indexOf("hand")!=-1)
			setTool(HAND);
		else if (name.indexOf("zoom")!=-1)
			setTool(MAGNIFIER);
		else if (name.indexOf("dropper")!=-1||name.indexOf("color")!=-1)
			setTool(DROPPER);
		else if (name.indexOf("angle")!=-1)
			setTool(ANGLE);
		else
			ok = false;
		return ok;
	}
	
	/** Returns the name of the current tool. */
	public static String getToolName() {
		String name = instance.getName(current);
		if (current>=SPARE1 && current<=SPARE9 && instance.names[current]!=null)
			name = instance.names[current];
		return name!=null?name:"";
	}

	/** Returns the name of the specified tool. */
	String getName(int id) {
		switch (id) {
			case RECTANGLE: return roundRectMode?"roundrect":"rectangle";
			case OVAL:
				switch (ovalType) {
					case OVAL_ROI: return "oval";
					case ELLIPSE_ROI: return "ellipse";
					case BRUSH_ROI: return "brush";
				}
			case POLYGON: return "polygon";
			case FREEROI: return "freehand";
			case LINE: return arrowMode?"arrow":"line";
			case POLYLINE: return "polyline";
			case FREELINE: return "freeline";
			case ANGLE: return "angle";
			case POINT: return Prefs.multiPointMode?"multipoint":"point";
			case WAND: return "wand";
			case TEXT: return "text";
			case HAND: return "hand";
			case MAGNIFIER: return "zoom";
			case DROPPER: return "dropper";
			default: return null;
		}
		
	}
	
	public void setTool(int tool) {
		if ((tool==current&&!(tool==RECTANGLE||tool==OVAL||tool==POINT)) || tool<0 || tool>=NUM_TOOLS-1)
			return;
		if (tool==SPARE1||(tool>=SPARE2&&tool<=SPARE8)) {
			if (names[tool]==null)
				names[tool] = "Spare tool"; // enable tool
			if (names[tool].indexOf("Action Tool")!=-1)
				return;
		}
		if (isLine(tool)) lineType = tool;
		setTool2(tool);
	}
		
	private void setTool2(int tool) {
		if (!isValidTool(tool)) return;
		String previousName = getToolName();
		current = tool;
		down[current] = true;
		if (current!=previous)
			down[previous] = false;
		Graphics g = this.getGraphics();
		if (g==null)
			return;
		if (Prefs.antialiasedTools) {
			Graphics2D g2d = (Graphics2D)g;
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		}
		drawButton(g, previous);
		drawButton(g, current);
		if (null==g) return;
		g.dispose();
		showMessage(current);
		previous = current;
		if (Recorder.record) {
			String name = getName(current);
			if (name!=null) Recorder.record("setTool", name);
		}
		if (IJ.isMacOSX())
			repaint();
		if (!previousName.equals(getToolName()))
			IJ.notifyEventListeners(IJEventListener.TOOL_CHANGED);
	}
	
	boolean isValidTool(int tool) {
		if (tool<0 || tool>=NUM_TOOLS)
			return false;
		if ((tool==SPARE1||(tool>=SPARE2&&tool<=SPARE9)) && names[tool]==null)
			return false;
		return true;
	}

	/**
	* @deprecated
	* replaced by getForegroundColor()
	*/
	public Color getColor() {
		return foregroundColor;
	}

	/**
	* @deprecated
	* replaced by setForegroundColor()
	*/
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
		if (c==null) return;
		foregroundColor = c;
		repaintTool(DROPPER);
		for (int i=SPARE2; i<=SPARE8; i++) {
			if (instance!=null && instance.icons[i]!=null && instance.icons[i].contains("C123"))
				repaintTool(i);  // some of this tool's icon is drawn in the foreground color
		}
		if (!IJ.isMacro()) setRoiColor(c);
		IJ.notifyEventListeners(IJEventListener.FOREGROUND_COLOR_CHANGED);
	}

	public static Color getBackgroundColor() {
		return backgroundColor;
	}

	public static void setBackgroundColor(Color c) {
		if (c!=null) {
			backgroundColor = c;
			repaintTool(DROPPER);
			IJ.notifyEventListeners(IJEventListener.BACKGROUND_COLOR_CHANGED);
		}
	}
	
	private static void setRoiColor(Color c) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) return;
		Roi roi = imp.getRoi();
		if (roi!=null && (roi.isDrawingTool())) {
			roi.setStrokeColor(c);
			imp.draw();
		}
	}

	/** Returns the size of the brush tool, or 0 if the brush tool is not enabled. */
	public static int getBrushSize() {
		if (ovalType==BRUSH_ROI)
			return brushSize;
		else
			return 0;
	}

	/** Set the size of the brush tool, in pixels. */
	public static void setBrushSize(int size) {
		brushSize = size;
		if (brushSize<1) brushSize = 1;
		Prefs.set(BRUSH_SIZE, brushSize);
	}
		
	/** Returns the rounded rectangle arc size, or 0 if the rounded rectangle tool is not enabled. */
	public static int getRoundRectArcSize() {
		if (!roundRectMode)
			return 0;
		else
			return arcSize;
	}

	/** Sets the rounded rectangle corner diameter (pixels). */
	public static void setRoundRectArcSize(int size) {
		if (size<=0)
			roundRectMode = false;
		else {
			arcSize = size;
			Prefs.set(CORNER_DIAMETER, arcSize);
		}
		repaintTool(RECTANGLE);
		ImagePlus imp = WindowManager.getCurrentImage();
		Roi roi = imp!=null?imp.getRoi():null;
		if (roi!=null && roi.getType()==Roi.RECTANGLE)
			roi.setCornerDiameter(roundRectMode?arcSize:0);
	}

	/** Returns 'true' if the multi-point tool is enabled. */
	public static boolean getMultiPointMode() {
		return multiPointMode;
	}

	/** Returns the oval tool type (OVAL_ROI, ELLIPSE_ROI or BRUSH_ROI). */
	public static int getOvalToolType() {
		return ovalType;
	}

	public static int getButtonSize() {
		return SIZE;
	}
	
	static void repaintTool(int tool) {
		if (IJ.getInstance()!=null) {
			Toolbar tb = getInstance();
			Graphics g = tb.getGraphics();
			if (g==null) return;
			if (Prefs.antialiasedTools)
				((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			tb.drawButton(g, tool);
			if (g!=null) g.dispose();
		}
		//Toolbar tb = getInstance();
		//tb.repaint(tool * SIZE , 0, SIZE, SIZE);
	}
	
	// Returns the toolbar position index of the specified tool
    int toolIndex(int tool) {
    	switch (tool) {
			case RECTANGLE: return 0;
			case OVAL: return 1;
			case POLYGON: return 2;
			case FREEROI: return 3;
			case LINE: return 4;
			case POLYLINE: return 4;
			case FREELINE: return 4;
			case POINT: return 6;
			case WAND: return 7;
			case TEXT: return 8;
			case MAGNIFIER: return 9;
			case HAND: return 10;
			case DROPPER: return 11;
			case ANGLE: return 5;
			case SPARE1: return 12;
			default: return tool - 2;
		}
    }

	// Returns the tool corresponding to the specified tool position index
    int toolID(int index) {
    	switch (index) {
			case 0: return RECTANGLE;
			case 1: return OVAL;
			case 2: return POLYGON;
			case 3: return FREEROI;
			case 4: return lineType;
			case 5: return ANGLE;
			case 6: return POINT;
			case 7: return WAND;
			case 8: return TEXT;
			case 9: return MAGNIFIER;
			case 10: return HAND;
			case 11: return DROPPER;
			case 12: return SPARE1;
			default: return index + 2;
		}
    }

	public void mousePressed(MouseEvent e) {
		int x = e.getX();
 		int newTool = 0;
		for (int i=0; i<NUM_BUTTONS; i++) {
			if (x>i*SIZE && x<i*SIZE+SIZE)
				newTool = toolID(i);
		}
		if (newTool==SPARE9) {
			showSwitchPopupMenu(e);
			return;
		}
		if (!isValidTool(newTool)) return;
		if (menus[newTool]!=null && menus[newTool].getItemCount()>0) {
            menus[newTool].show(e.getComponent(), e.getX(), e.getY());
			return;
		}
		boolean doubleClick = newTool==current && (System.currentTimeMillis()-mouseDownTime)<=DOUBLE_CLICK_THRESHOLD;
 		mouseDownTime = System.currentTimeMillis();
		if (!doubleClick) {
			mpPrevious = current;
			if (isMacroTool(newTool)) {
				String name = names[newTool];
				if (name.contains("Unused Tool"))
					return;
				if (name.indexOf("Action Tool")!=-1) {
					if (e.isPopupTrigger()||e.isMetaDown()) {
						name = name.endsWith(" ")?name:name+" ";
						tools[newTool].runMacroTool(name+"Options");
					} else {
						drawTool(newTool, true);
						IJ.wait(50);
						drawTool(newTool, false);
						runMacroTool(newTool);
					}
					return;
				} else {	
					name = name.endsWith(" ")?name:name+" ";
					tools[newTool].runMacroTool(name+"Selected");
				}
			}
			setTool2(newTool);
			boolean isRightClick = e.isPopupTrigger()||e.isMetaDown();
			if (current==RECTANGLE && isRightClick) {
				rectItem.setState(!roundRectMode);
				roundRectItem.setState(roundRectMode);
				if (IJ.isMacOSX()) IJ.wait(10);
				rectPopup.show(e.getComponent(),x,y);
				mouseDownTime = 0L;
			}
			if (current==OVAL && isRightClick) {
				ovalItem.setState(ovalType==OVAL_ROI);
				ellipseItem.setState(ovalType==ELLIPSE_ROI);
				brushItem.setState(ovalType==BRUSH_ROI);
				if (IJ.isMacOSX()) IJ.wait(10);
				ovalPopup.show(e.getComponent(),x,y);
				mouseDownTime = 0L;
			}
			if (current==POINT && isRightClick) {
				pointItem.setState(!multiPointMode);
				multiPointItem.setState(multiPointMode);
				if (IJ.isMacOSX()) IJ.wait(10);
				pointPopup.show(e.getComponent(),x,y);
				mouseDownTime = 0L;
			}
			if (isLine(current) && isRightClick) {
				straightLineItem.setState(lineType==LINE&&!arrowMode);
				polyLineItem.setState(lineType==POLYLINE);
				freeLineItem.setState(lineType==FREELINE);
				arrowItem.setState(lineType==LINE&&arrowMode);
				if (IJ.isMacOSX()) IJ.wait(10);
				linePopup.show(e.getComponent(),x,y);
				mouseDownTime = 0L;
			}
			if (isMacroTool(current) && isRightClick) {
				String name = names[current].endsWith(" ")?names[current]:names[current]+" ";
				tools[current].runMacroTool(name+"Options");
			}
		} else { //double click
			if (isMacroTool(current)) {
				String name = names[current].endsWith(" ")?names[current]:names[current]+" ";
				tools[current].runMacroTool(name+"Options");
				return;
			}
			if (isPlugInTool(current)) {
				tools[current].showOptionsDialog();
				return;
			}
			ImagePlus imp = WindowManager.getCurrentImage();
			switch (current) {
				case RECTANGLE:
					if (roundRectMode)
						IJ.doCommand("Rounded Rect Tool...");
					break;
				case OVAL:
					showBrushDialog();
					break;
				case MAGNIFIER:
					if (imp!=null) {
						ImageCanvas ic = imp.getCanvas();
						if (ic!=null) ic.unzoom();
					}
					break;
				case LINE: case POLYLINE: case FREELINE:
					if (current==LINE && arrowMode) {
						IJ.doCommand("Arrow Tool...");
					} else
						IJ.runPlugIn("ij.plugin.frame.LineWidthAdjuster", "");
					break;
				case ANGLE:
					showAngleDialog();
					break;
				case POINT:
					if (multiPointMode) {
						if (imp!=null && imp.getRoi()!=null)
							IJ.doCommand("Add Selection...");
					} else
						IJ.doCommand("Point Tool...");
					break;
				case WAND:
					IJ.doCommand("Wand Tool...");
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
	
	void showSwitchPopupMenu(MouseEvent e) {
		String path = IJ.getDirectory("macros")+"toolsets/";
		if (path==null)
			return;
		boolean applet = IJ.getApplet()!=null;
		File f = new File(path);
		String[] list;
		if (!applet && f.exists() && f.isDirectory()) {
			list = f.list();
			if (list==null) return;
		} else
			list = new String[0];
        boolean stackTools = false;
		for (int i=0; i<list.length; i++) {
            if (list[i].equals("Stack Tools.txt")) {
                stackTools = true;
                break;
            }
		}
		switchPopup.removeAll();
        path = IJ.getDirectory("macros") + "StartupMacros.txt";
		f = new File(path);
		if (!applet && f.exists())
            addItem("Startup Macros");
        else
            addItem("StartupMacros*");
		if (!stackTools) addItem("Stack Tools*");
 		for (int i=0; i<list.length; i++) {
			String name = list[i];
			if (name.startsWith(".") || name.endsWith(" Tool"))
				continue;
			if (name.endsWith(".txt")) {
				name = name.substring(0, name.length()-4);
                addItem(name);
			} else if (name.endsWith(".ijm")) {
				name = name.substring(0, name.length()-4) + " ";
                addItem(name);
			}
		}
		addPluginTools();
		addItem("Remove Tools");
		addItem("Help...");
		add(ovalPopup);
		if (IJ.isMacOSX()) IJ.wait(10);
		switchPopup.show(e.getComponent(), e.getX(), e.getY());
	}

	private void addPluginTools() {
		switchPopup.addSeparator();
		addBuiltInTool("Arrow");
		addBuiltInTool("Brush");
		addBuiltInTool("Developer Menu");
		addBuiltInTool("Flood Filler");
		addBuiltInTool("LUT Menu");
		addBuiltInTool("Overlay Brush");
		addBuiltInTool("Pencil");
		addBuiltInTool("Pixel Inspector");
		addBuiltInTool("Spray Can");
		addBuiltInTool("Stacks Menu");
		MenuBar menuBar = Menus.getMenuBar();
		if (menuBar==null)
			return;
		int n = menuBar.getMenuCount();
		Menu pluginsMenu = null;
		if (menuBar.getMenuCount()>=5)
			pluginsMenu = menuBar.getMenu(5);
		if (pluginsMenu==null || !"Plugins".equals(pluginsMenu.getLabel()))
			return;
		n = pluginsMenu.getItemCount();
		Menu toolsMenu = null;
		for (int i=0; i<n; ++i) {
			MenuItem m = pluginsMenu.getItem(i);
			if ("Tools".equals(m.getLabel()) && (m instanceof Menu)) {
				toolsMenu = (Menu)m;
				break;
			}
		}
		if (toolsMenu==null) {
			switchPopup.addSeparator();
			return;
		}
		n = toolsMenu.getItemCount();
		boolean separatorAdded = false;
		for (int i=0; i<n; ++i) {
			MenuItem m = toolsMenu.getItem(i);
			String label = m.getLabel();
			if (label!=null && (label.endsWith(" Tool")||label.endsWith(" Menu"))) {
				if (!separatorAdded) {
					switchPopup.addSeparator();
					separatorAdded = true;
				}
				addPluginTool(label);
			}
		}
		switchPopup.addSeparator();
	}

    private void addBuiltInTool(String name) {
		CheckboxMenuItem item = new CheckboxMenuItem(name, name.equals(currentSet));
		item.addItemListener(this);
		item.setActionCommand("Tool");
		switchPopup.add(item);
    }

    private void addPluginTool(String name) {
		CheckboxMenuItem item = new CheckboxMenuItem(name, name.equals(currentSet));
		item.addItemListener(this);
		item.setActionCommand("Plugin Tool");
		switchPopup.add(item);
    }

    private void addItem(String name) {
		CheckboxMenuItem item = new CheckboxMenuItem(name, name.equals(currentSet));
		item.addItemListener(this);
		switchPopup.add(item);
    }

	void drawTool(int tool, boolean drawDown) {
		down[tool] = drawDown;
		Graphics g = this.getGraphics();
		if (!drawDown && Prefs.antialiasedTools) {
			Graphics2D g2d = (Graphics2D)g;
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		}
		drawButton(g, tool);
		if (null==g) return;
		g.dispose();
	}

	boolean isLine(int tool) {
		return tool==LINE || tool==POLYLINE || tool==FREELINE;
	}
	
	public void restorePreviousTool() {
		setTool2(mpPrevious);
	}
	
	boolean isMacroTool(int tool) {
		return tool>=SPARE1 && tool<=SPARE9 && names[tool]!=null
			&& (tools[tool] instanceof MacroToolRunner||names[tool].equals("Unused Tool"));
	}
	
	boolean isPlugInTool(int tool) {
		return tool>=SPARE1 && tool<=SPARE9 && tools[tool]!=null;
	}

	public void mouseReleased(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
    public void mouseDragged(MouseEvent e) {}
	
	public void itemStateChanged(ItemEvent e) {
		CheckboxMenuItem item = (CheckboxMenuItem)e.getSource();
		String previousName = getToolName();
		if (item==rectItem || item==roundRectItem) {
			roundRectMode = item==roundRectItem;
			repaintTool(RECTANGLE);
			showMessage(RECTANGLE);
			ImagePlus imp = WindowManager.getCurrentImage();
			Roi roi = imp!=null?imp.getRoi():null;
			if (roi!=null && roi.getType()==Roi.RECTANGLE)
				roi.setCornerDiameter(roundRectMode?arcSize:0);
			if (!previousName.equals(getToolName()))
				IJ.notifyEventListeners(IJEventListener.TOOL_CHANGED);
		} else if (item==ovalItem || item==ellipseItem || item==brushItem) {
			if (item==brushItem)
				ovalType = BRUSH_ROI;
			else if (item==ellipseItem)
				ovalType = ELLIPSE_ROI;
			else
				ovalType = OVAL_ROI;
			repaintTool(OVAL);
			showMessage(OVAL);
			if (!previousName.equals(getToolName()))
				IJ.notifyEventListeners(IJEventListener.TOOL_CHANGED);
		} else if (item==pointItem || item==multiPointItem) {
			multiPointMode = item==multiPointItem;
			Prefs.multiPointMode = multiPointMode;
			repaintTool(POINT);
			showMessage(POINT);
			if (!previousName.equals(getToolName()))
				IJ.notifyEventListeners(IJEventListener.TOOL_CHANGED);
		} else if (item==straightLineItem) {
			lineType = LINE;
			arrowMode = false;
			setTool2(LINE);
			showMessage(LINE);
		} else if (item==polyLineItem) {
			lineType = POLYLINE;
			setTool2(POLYLINE);
			showMessage(POLYLINE);
		} else if (item==freeLineItem) {
			lineType = FREELINE;
			setTool2(FREELINE);
			showMessage(FREELINE);
		} else if (item==arrowItem) {
			lineType = LINE;
			arrowMode = true;
			setTool2(LINE);
			showMessage(LINE);
		} else {
			String label = item.getLabel();
			String cmd = item.getActionCommand();
			boolean isTool = cmd.equals("Tool") || cmd.equals("Plugin Tool");
			if (!(label.equals("Help...")||label.equals("Remove Tools")) && !isTool)
				currentSet = label;
			if (isTool) {
				if (cmd.equals("Tool")) // built in tool
					installBuiltinTool(label);
				else  // plugin or macro tool in ImageJ/plugins/Tools
					IJ.run(label);
				return;
			}
			String path;
			if (label.equals("Remove Tools")) {
				removeMacroTools();
				setTool(RECTANGLE);
				currentSet = "Startup Macros";
				resetPrefs();
			} else if (label.equals("Help...")) {
				IJ.showMessage("Tool Switcher and Loader",
					"Use this drop down menu to switch to alternative\n"+
					"macro toolsets or to load additional plugin tools.\n"+
					"The toolsets listed in the menu are located\n"+
					"in the ImageJ/macros/toolsets folder and the\n"+
					"plugin tools are the ones installed in the\n"+
					"Plugins>Tools submenu.\n"+
					" \n"+
					"Hold the shift key down while selecting a\n"+
					"toolset to view its source code.\n"+
					" \n"+
					"More macro toolsets are available at\n"+
					"  <"+IJ.URL+"/macros/toolsets/>\n"+
					" \n"+
					"Plugin tools can be downloaded from\n"+
					"the Tools section of the Plugins page at\n"+
					"  <"+IJ.URL+"/plugins/>\n"
					);
				return;
			} else if (label.endsWith("*")) {
                // load from ij.jar
                MacroInstaller mi = new MacroInstaller();
                label = label.substring(0, label.length()-1) + ".txt";
                path = "/macros/"+label;
				if (IJ.shiftKeyDown()) {
					String macros = mi.openFromIJJar(path);
                    Editor ed = new Editor();
                    ed.setSize(350, 300);
                    ed.create(label, macros);
                	IJ.setKeyUp(KeyEvent.VK_SHIFT);
				} else {
					resetTools();
					mi.installFromIJJar(path);
				}
            } else {
                // load from ImageJ/macros/toolsets
                if (label.equals("Startup Macros")) {
                	installStartupMacros();
                	return;
                } else if (label.endsWith(" "))
                    path = IJ.getDirectory("macros")+"toolsets/"+label.substring(0, label.length()-1)+".ijm";
                else
                    path = IJ.getDirectory("macros")+"toolsets/"+label+".txt";
                try {
                    if (IJ.shiftKeyDown()) {
                        IJ.open(path);
                		IJ.setKeyUp(KeyEvent.VK_SHIFT);
                    } else
                        new MacroInstaller().run(path);
                } catch(Exception ex) {}
            }
		}
	}
	
	private void resetPrefs() {
		for (int i=0; i<7; i++) {
			String key = TOOL_KEY+(i/10)%10+i%10;
			if (!Prefs.get(key, "").equals(""))
				Prefs.set(key, "");
		}
	}
	
	private 	void installStartupMacros() {
		resetTools();
		String path = IJ.getDirectory("macros")+"StartupMacros.txt";
		if (IJ.shiftKeyDown()) {
			IJ.open(path);
			IJ.setKeyUp(KeyEvent.VK_SHIFT);
		} else {
			try {
				MacroInstaller mi = new MacroInstaller();
				mi.installFile(path);
			} catch
				(Exception ex) {}
		}
	}

	public void actionPerformed(ActionEvent e) {
		MenuItem item = (MenuItem)e.getSource();
		String cmd = e.getActionCommand();
		PopupMenu popup = (PopupMenu)item.getParent();
		int tool = -1;
		for (int i=SPARE1; i<NUM_TOOLS; i++) {
			if (popup==menus[i]) {
				tool = i;
				break;
			}
		}
		if (tool==-1) return;
		if (tools[tool]!=null)
			tools[tool].runMenuTool(names[tool], cmd);
    }

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

	/** Adds a tool to the toolbar. The 'toolTip' string is displayed in the status bar
		 when the mouse is over the tool icon. The 'toolTip' string may include icon 
		(http://imagej.nih.gov/ij/developer/macro/macros.html#tools).
		Returns the tool ID, or -1 if all tools are in use. */
	public int addTool(String toolTip) {
		int index = toolTip.indexOf('-');
		boolean hasIcon = index>=0 && (toolTip.length()-index)>4;
		int tool =-1;
		if (names[SPARE1]==null) {
			if (addingSingleTool) {
				names[SPARE1] = "Unused Tool";
			} else
				tool = SPARE1;
		}
		if (tool==-1) {
			for (int i=SPARE2; i<=SPARE8; i++) {
				if (names[i]==null || toolTip.startsWith(names[i])) {
					tool = i;
					break;
				}			
			}
		}
		if (tool==-1) {
			if (addingSingleTool)
				tool = SPARE8;
			else
				return -1;
		}
		if (hasIcon) {
			icons[tool] = toolTip.substring(index+1);
			if (index>0 && toolTip.charAt(index-1)==' ')
				names[tool] = toolTip.substring(0, index-1);
			else
				names[tool] = toolTip.substring(0, index);
		} else {
			if (toolTip.endsWith("-"))
				toolTip = toolTip.substring(0, toolTip.length()-1);
			else if (toolTip.endsWith("- "))
				toolTip = toolTip.substring(0, toolTip.length()-2);
			names[tool] = toolTip;
		}
        if (tool==current && (names[tool].indexOf("Action Tool")!=-1||names[tool].indexOf("Unused Tool")!=-1))
        	setTool(RECTANGLE);
        if (names[tool].endsWith(" Menu Tool"))
            installMenu(tool);
		return tool;
	}
    
    void installMenu(int tool) {
        Program pgm = macroInstaller.getProgram();
        Hashtable h = pgm.getMenus();
        if (h==null) return;
        String[] commands = (String[])h.get(names[tool]);
        if (commands==null) return;
		if (menus[tool]==null) {
			menus[tool] = new PopupMenu("");
			if (Menus.getFontSize()!=0)
				menus[tool].setFont(Menus.getFont());
			add(menus[tool] );
		} else
			menus[tool].removeAll();
        for (int i=0; i<commands.length; i++) {
			if (commands[i].equals("-"))
				menus[tool].addSeparator();
			else if (commands[i].startsWith("-"))
				menus[tool].addSeparator();
			else {
				boolean disable = commands[i].startsWith("*");
				String command = commands[i];
				if (disable)
					command = command.substring(1);
				MenuItem mi = new MenuItem(command);
				if (disable)
					mi.setEnabled(false);
				mi.addActionListener(this);
				menus[tool].add(mi);
			}
        }
        if (tool==current) setTool(RECTANGLE);
    }
    
	/** Used by the MacroInstaller class to install a set of macro tools. */
	public void addMacroTool(String name, MacroInstaller macroInstaller, int id) {
		if (id==0)
			resetTools();
		this.macroInstaller = macroInstaller;
		int tool = addTool(name);
		this.macroInstaller = null;
		if (tool!=-1)
			tools[tool] = new MacroToolRunner(macroInstaller);
	}
	
	private void resetTools() {
		for (int i=SPARE1; i<NUM_TOOLS-1; i++) {
			names[i] = null;
			tools[i] = null;
			icons[i] = null;
			if (menus[i]!=null)
				menus[i].removeAll();
		}
	}
	
	/** Used by the MacroInstaller class to add a macro tool to the first
		available toolbar slot, or to the last slot if the toolbar is full. */
	public void addMacroTool(String name, MacroInstaller macroInstaller) {
		this.macroInstaller = macroInstaller;
		addingSingleTool = true;
		int tool = addTool(name);
		addingSingleTool = false;
		this.macroInstaller = null;
		if (tool!=-1) {
			tools[tool] = new MacroToolRunner(macroInstaller);
			if (!name.contains(" Menu Tool")) {
				if (menus[tool]!=null)
					menus[tool].removeAll();
				if (!installingStartupTool)
					setTool(tool);
				else
					installingStartupTool = false;
			}
			setPrefs(tool);
		}
	}
	
	private void setPrefs(int id) {
		int index = id - SPARE2;
		String key = TOOL_KEY + (index/10)%10 + index%10;
		Prefs.set(key, instance.names[id]);
	}

	public static void removeMacroTools() {
		if (instance!=null) {
			instance.resetTools();
			instance.repaint();
		}
	}

	/** Adds a plugin tool to the first available toolbar slot,
		or to the last slot if the toolbar is full. */
	public static void addPlugInTool(PlugInTool tool) {
		if (instance==null) return;
		String nameAndIcon = tool.getToolName()+" - "+tool.getToolIcon();
		instance.addingSingleTool = true;
		int id = instance.addTool(nameAndIcon);
		instance.addingSingleTool = false;
		if (id!=-1) {
			instance.tools[id] = tool;
			if (instance.menus[id]!=null)
				instance.menus[id].removeAll();
			instance.repaintTool(id);	
			if (!instance.installingStartupTool)
				instance.setTool(id);
			else
				instance.installingStartupTool = false;
			instance.setPrefs(id);
		}
	}

	public static PlugInTool getPlugInTool() {
		if (instance==null) return null;
		PlugInTool tool = instance.tools[current];
		if (tool!=null && tool instanceof MacroToolRunner)
			tool = null;
		return tool;
	}

	void runMacroTool(int id) {
		if (tools[id]!=null)
			tools[id].runMacroTool(names[id]);
	}
	
	void showBrushDialog() {
		GenericDialog gd = new GenericDialog("Selection Brush");
		gd.addCheckbox("Enable selection brush", ovalType==BRUSH_ROI);
		gd.addNumericField("           Size:", brushSize, 0, 4, "pixels");
		gd.showDialog();
		if (gd.wasCanceled()) return;
		if (gd.getNextBoolean())
			ovalType = BRUSH_ROI;
		brushSize = (int)gd.getNextNumber();
		if (brushSize<1) brushSize=1;
		repaintTool(OVAL);
		ImagePlus img = WindowManager.getCurrentImage();
		Roi roi = img!=null?img.getRoi():null;
		if (roi!=null && roi.getType()==Roi.OVAL && ovalType==BRUSH_ROI)
			img.deleteRoi();
		Prefs.set(BRUSH_SIZE, brushSize);
	}

	void showAngleDialog() {
		GenericDialog gd = new GenericDialog("Angle Tool");
		gd.addCheckbox("Measure reflex angle", Prefs.reflexAngle);
		gd.showDialog();
		if (!gd.wasCanceled())
			Prefs.reflexAngle = gd.getNextBoolean();
	}
	
	public void installStartupTools() {
		for (int i=0; i<=6; i++) {
			String name = Prefs.get(TOOL_KEY + (i/10)%10 + i%10, "");
			if (name.equals("")) continue;
			installingStartupTool = true;
			boolean ok = installBuiltinTool(name);
			if (!ok) {
				if (name.endsWith("Menu Tool"))
					name = name.substring(0, name.length()-5);
				Hashtable commands = Menus.getCommands();
				if (commands!=null && commands.get(name)!=null)
					IJ.run(name);
			}
			installingStartupTool = false;
		}
	}
	
	private boolean installBuiltinTool(String label) {
		boolean ok = true;
		PlugInTool tool = null;
		if (label.startsWith("Arrow")) {
			tool = new ij.plugin.tool.ArrowTool();
			if (tool!=null) tool.run("");
		} else if (label.startsWith("Overlay Brush")) {
			tool = new ij.plugin.tool.OverlayBrushTool();
			if (tool!=null) tool.run("");
		} else if (label.startsWith("Pixel Inspect")) {
			tool = new ij.plugin.tool.PixelInspectionTool();
			if (tool!=null) tool.run("");
		} else if (label.startsWith("Brush")||label.startsWith("Paintbrush")) {
			tool = new ij.plugin.tool.BrushTool();
			if (tool!=null) tool.run("");
		} else if (label.startsWith("Pencil")) {
			tool = new ij.plugin.tool.BrushTool();
			if (tool!=null) tool.run("pencil");
		} else if (label.startsWith("Flood Fill")) {
			(new MacroInstaller()).installFromIJJar("/macros/FloodFillTool.txt");
		} else if (label.startsWith("Spray Can")) {
			(new MacroInstaller()).installFromIJJar("/macros/SprayCanTool.txt");
		} else if (label.startsWith("Developer Menu")) {
			(new MacroInstaller()).installFromIJJar("/macros/DeveloperMenuTool.txt");
		} else if (label.startsWith("Stacks Menu")) {
			(new MacroInstaller()).installFromIJJar("/macros/StacksMenuTool.txt");
		} else if (label.startsWith("LUT Menu")) {
			(new MacroInstaller()).installFromIJJar("/macros/LUTMenuTool.txt");
		} else
			ok = false;
		return ok;
	}
	
	private boolean isMacroSet(int id) {
		if (tools[id]==null)
			return false;
		if (!(tools[id] instanceof MacroToolRunner))
			return false;
		boolean rtn = ((MacroToolRunner)tools[id]).getMacroCount()>2;
		return rtn;
	}
	
	public static boolean installStartupMacrosTools() {
		String customTool0 = Prefs.get(Toolbar.TOOL_KEY+"00", "");
		return customTool0.equals("") || Character.isDigit(customTool0.charAt(0));
	}
	
	//public void repaint() {
	//	super.repaint();
	//}

}
