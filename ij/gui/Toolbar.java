package ij.gui;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.Timer;
import java.util.Hashtable;
import java.util.TimerTask;
import java.util.Arrays;
import java.util.Locale;
import ij.*;
import ij.plugin.frame.*;
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
	public static final int UNUSED = 10;
	public static final int MAGNIFIER = 11;
	public static final int HAND = 12;
	public static final int DROPPER = 13;
	public static final int ANGLE = 14;
	public static final int CUSTOM1 = 15;
	public static final int CUSTOM2 = 16;
	public static final int CUSTOM3 = 17;
	public static final int CUSTOM4 = 18;
	public static final int CUSTOM5 = 19;
	public static final int CUSTOM6 = 20;
	public static final int CUSTOM7 = 21;
	
	public static final int DOUBLE_CLICK_THRESHOLD = 650; //ms

	public static final int RECT_ROI=0, ROUNDED_RECT_ROI=1, ROTATED_RECT_ROI=2;
	public static final int OVAL_ROI=0, ELLIPSE_ROI=1, BRUSH_ROI=2;
	
	public static final String[] builtInTools = {"Arrow","Brush","Command Finder", "Developer Menu","Flood Filler",
		"Label Maker","LUT Menu","Overlay Brush","Pencil","Pixel Inspector","Selection Rotator",
		"Spray Can","Stacks Menu","ROI Menu"};
	private static final String[] builtInTools2 = {"Pixel Inspection Tool","Paintbrush Tool","Flood Fill Tool"};

	private static final int NUM_TOOLS = 23;
	private static final int MAX_EXTRA_TOOLS = 8;
	private static final int MAX_TOOLS = NUM_TOOLS+MAX_EXTRA_TOOLS;
	private static final int NUM_BUTTONS = 21;
	private static final int BUTTON_WIDTH = 30;
	private static final int BUTTON_HEIGHT = 31;
	private static final int SIZE = 28;  // no longer used
	private static final int GAP_SIZE = 9;
	private static final int OFFSET = 7;
	private static final String BRUSH_SIZE = "toolbar.brush.size";
	public static final String CORNER_DIAMETER = "toolbar.arc.size";
	public static String TOOL_KEY = "toolbar.tool";
		
	private Dimension ps;
	private boolean[] down;
	private static int current;
	private int previousTool;
	private int x,y;
	private int xOffset, yOffset;
	private long mouseDownTime;
	private Graphics g;
	private static Toolbar instance;
	private int mpPrevious = RECTANGLE;
	private String[] names = new String[MAX_TOOLS];
	private String[] icons = new String[MAX_TOOLS];
	private PlugInTool[] tools = new PlugInTool[MAX_TOOLS];
	private PopupMenu[] menus = new PopupMenu[MAX_TOOLS];
	private int nExtraTools;
	private MacroInstaller macroInstaller;
	private boolean addingSingleTool;
	private boolean installingStartupTool;
	private boolean doNotSavePrefs;
	private int pc;
	private String icon;
	private int startupTime;
	private PopupMenu rectPopup, ovalPopup, pointPopup, linePopup, zoomPopup, pickerPopup, switchPopup;
	private CheckboxMenuItem rectItem, roundRectItem, rotatedRectItem;
	private CheckboxMenuItem ovalItem, ellipseItem, brushItem;
	private CheckboxMenuItem pointItem, multiPointItem;
	private CheckboxMenuItem straightLineItem, polyLineItem, freeLineItem, arrowItem;
	private String currentSet = "Startup Macros";
	private Timer pressTimer;
	private static int longClickDelay = 600; //ms

	private static Color foregroundColor = Prefs.getColor(Prefs.FCOLOR,Color.white);
	private static Color backgroundColor = Prefs.getColor(Prefs.BCOLOR,Color.black);
	private static double foregroundValue = Double.NaN;
	private static double backgroundValue = Double.NaN;
	private static int ovalType = OVAL_ROI;
	private static int rectType = RECT_ROI;
	private static boolean multiPointMode = Prefs.multiPointMode;
	private static boolean arrowMode;
	private static int brushSize = (int)Prefs.get(BRUSH_SIZE, 15);
	private static int arcSize = (int)Prefs.get(CORNER_DIAMETER, 20);
	private int lineType = LINE;
	private static boolean legacyMode;
	private static double dscale = 1.0;
	private static int scale = 1;
	private static int buttonWidth;
	private static int buttonHeight;
	private static int gapSize;
	private static int offset;
	
	private Color gray = new Color(228,228,228);
	private Color brighter = gray.brighter();
	private Color darker = new Color(180, 180, 180);
	private Color evenDarker = new Color(110, 110, 110);
	private Color triangleColor = new Color(150, 0, 0);
	private Color toolColor = new Color(0, 25, 45);
	
	/** Obsolete public constants */
	public static final int SPARE1=UNUSED, SPARE2=CUSTOM1, SPARE3=CUSTOM2, SPARE4=CUSTOM3, 
		SPARE5=CUSTOM4, SPARE6=CUSTOM5, SPARE7=CUSTOM6, SPARE8=CUSTOM7, SPARE9=22;


	public Toolbar() {
		init();
		down = new boolean[MAX_TOOLS];
		resetButtons();
		down[0] = true;
		setForeground(Color.black);
		setBackground(ImageJ.backgroundColor);
		addMouseListener(this);
		addMouseMotionListener(this);
		instance = this;
		names[getNumTools()-1] = "\"More Tools\" menu (switch toolsets or add tools)";
		icons[getNumTools()-1] = "C900T0d18>T6d18>"; // ">>"
		addPopupMenus();
	}
	
	public void init() {
		dscale = Prefs.getGuiScale();
		scale = (int)Math.round(dscale);
		if ((dscale>=1.5&&dscale<2.0) || (dscale>=2.5&&dscale<3.0))
			dscale = scale;
		if (dscale>1.0) {
			buttonWidth = (int)((BUTTON_WIDTH-2)*dscale);
			buttonHeight = (int)((BUTTON_HEIGHT-2)*dscale);
			offset = (int)Math.round((OFFSET-1)*dscale);
		} else {
			buttonWidth = BUTTON_WIDTH;
			buttonHeight = BUTTON_HEIGHT;
			offset = OFFSET;
		}
		gapSize = GAP_SIZE;
		ps = new Dimension(buttonWidth*NUM_BUTTONS-(buttonWidth-gapSize), buttonHeight);
	}

	void addPopupMenus() {
		rectPopup = new PopupMenu();
		if (Menus.getFontSize()!=0)
			rectPopup.setFont(Menus.getFont());
		rectItem = new CheckboxMenuItem("Rectangle", rectType==RECT_ROI);
		rectItem.addItemListener(this);
		rectPopup.add(rectItem);
		roundRectItem = new CheckboxMenuItem("Rounded Rectangle", rectType==ROUNDED_RECT_ROI);
		roundRectItem.addItemListener(this);
		rectPopup.add(roundRectItem);
		rotatedRectItem = new CheckboxMenuItem("Rotated Rectangle", rectType==ROTATED_RECT_ROI);
		rotatedRectItem.addItemListener(this);
		rectPopup.add(rotatedRectItem);
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

		zoomPopup = new PopupMenu();
		if (Menus.getFontSize()!=0)
			zoomPopup.setFont(Menus.getFont());
		addMenuItem(zoomPopup, "Reset Zoom");
		addMenuItem(zoomPopup, "Zoom In");
		addMenuItem(zoomPopup, "Zoom Out");
		addMenuItem(zoomPopup, "View 100%");
		addMenuItem(zoomPopup, "Zoom To Selection");
		addMenuItem(zoomPopup, "Scale to Fit");
		addMenuItem(zoomPopup, "Set...");
		addMenuItem(zoomPopup, "Maximize");
		add(zoomPopup);

		pickerPopup = new PopupMenu();
		if (Menus.getFontSize()!=0)
			pickerPopup.setFont(Menus.getFont());
		addMenuItem(pickerPopup, "White/Black");
		addMenuItem(pickerPopup, "Black/White");
		addMenuItem(pickerPopup, "Red");
		addMenuItem(pickerPopup, "Green");
		addMenuItem(pickerPopup, "Blue");
		addMenuItem(pickerPopup, "Yellow");
		addMenuItem(pickerPopup, "Cyan");
		addMenuItem(pickerPopup, "Magenta");
		pickerPopup.addSeparator();
		addMenuItem(pickerPopup, "Foreground...");
		addMenuItem(pickerPopup, "Background...");
		addMenuItem(pickerPopup, "Color Picker...");
		add(pickerPopup);
		
		switchPopup = new PopupMenu();
		if (Menus.getFontSize()!=0)
			switchPopup.setFont(Menus.getFont());
		add(switchPopup);
	}
	
	private void addMenuItem(PopupMenu menu, String command) {
		MenuItem item = new MenuItem(command);
		item.addActionListener(this);
		menu.add(item);
	}
	
	/** Returns the ID of the current tool (Toolbar.RECTANGLE, Toolbar.OVAL, etc.). */
	public static int getToolId() {
		int id = current;
		if (legacyMode) {
			if (id==CUSTOM1)
				id=UNUSED;
			else if (id>=CUSTOM2)
				id--;
		}
		return id;
	}

	/** Returns the ID of the tool whose name (the description displayed in the status bar)
		starts with the specified string, or -1 if the tool is not found. */
	public int getToolId(String name) {
		int tool =  -1;
		for (int i=0; i<getNumTools(); i++) {
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
		if (g==null)
			return;
		Graphics2D g2d = (Graphics2D)g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		setStrokeWidth(g2d);
		for (int i=0; i<LINE; i++)
			drawButton(g, i);
		drawButton(g, lineType);
		for (int i=POINT; i<getNumTools(); i++)
			drawButton(g, i);
	}

	private void setStrokeWidth(Graphics2D g2d) {
		if (scale==1) {
			if (dscale>1.0)
				g2d.setStroke(new BasicStroke(IJ.isMacOSX()?1.4f:1.25f));
		} else
			g2d.setStroke(new BasicStroke(scale));
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
		if (legacyMode) {
			if (tool==UNUSED)
				tool = CUSTOM1;
			else if (tool>=CUSTOM1)
				tool++;
			if ((tool==POLYLINE && lineType!=POLYLINE) || (tool==FREELINE && lineType!=FREELINE))
				return;
		}
        int index = toolIndex(tool);
        int x = index*buttonWidth + 1*scale;
        if (tool>=CUSTOM1)
        	x -= buttonWidth-gapSize;
        if (tool!=UNUSED)
        	fill3DRect(g, x, 1, buttonWidth, buttonHeight-1, !down[tool]);
        g.setColor(toolColor);
        x = index*buttonWidth + offset;
        if (tool>=CUSTOM1)
        	x -= buttonWidth-gapSize;
		int y = offset;
		if (dscale==1.3) {x++; y++;}
		if (dscale==1.4) {x+=2; y+=2;}
		if (down[tool]) { x++; y++;}
		this.g = g;
		if (tool>=CUSTOM1 && tool<=getNumTools() && icons[tool]!=null) {
			drawIcon(g, tool, x+1*scale, y+1*scale);
			return;
		}
		switch (tool) {
			case RECTANGLE:
				xOffset = x; yOffset = y;
				if (rectType==ROUNDED_RECT_ROI)
					g.drawRoundRect(x-1*scale, y+1*scale, 17*scale, 13*scale, 8*scale, 8*scale);
				else if (rectType==ROTATED_RECT_ROI)
					polyline(0,10,7,0,15,6,8,16,0,10); 
				else
					g.drawRect(x-1*scale, y+1*scale, 17*scale, 13*scale);
				drawTriangle(16,16);
				return;
			case OVAL:
				xOffset = x; yOffset = y;
				if (ovalType==BRUSH_ROI) {
					yOffset = y - 1;
					polyline(6,4,8,2,12,1,15,2,16,4,15,7,12,8,9,11,9,14,6,16,2,16,0,13,1,10,4,9,6,7,6,4);
				} else if (ovalType==ELLIPSE_ROI) {
					xOffset = x - 1;
					yOffset = y + 1;
					polyline(11,0,13,0,14,1,15,1,16,2,17,3,17,7,12,12,11,12,10,13,8,13,7,14,4,14,3,13,2,13,1,12,1,11,0,10,0,9,1,8,1,7,6,2,7,2,8,1,10,1,11,0);
				} else
					g.drawOval(x, y+1*scale, 17*scale, 13*scale);
				drawTriangle(16,16);
				return;
			case POLYGON:
				xOffset = x+1; yOffset = y+2;
				polyline(4,0,15,0,15,1,11,5,11,6,14,11,14,12,0,12,0,4,4,0);
				return;
			case FREEROI:
				xOffset = x; yOffset = y+3;
				polyline(2,0,5,0,7,3,10,3,12,0,15,0,17,2,17,5,16,8,13,10,11,11,6,11,4,10,1,8,0,6,0,2,2,0); 
				return;
			case LINE:
				if (arrowMode) {
					xOffset = x; yOffset = y;
					m(1,14); d(14,1); m(6,5); d(14,1); m(10,9); d(14,1); m(6,5); d(10,9);
				} else {
					xOffset = x-1; yOffset = y-1;
					m(1,17); d(18,0);
					drawDot(0,16); drawDot(17,0);
				}
				drawTriangle(16,16);
				return;
			case POLYLINE:
				xOffset = x; yOffset = y;
				polyline(15,6,11,2,1,2,1,3,7,9,2,14);
				drawTriangle(14,16);
				return;
			case FREELINE:
				xOffset = x; yOffset = y;
				polyline(16,4,14,6,12,6,9,3,8,3,6,7,2,11,1,11);
				drawTriangle(14,16);
				return;
			case POINT:
				xOffset = x; yOffset = y;
				if (multiPointMode) {
					drawPoint(1,3); drawPoint(9,0); drawPoint(15,5);
					drawPoint(10,11); drawPoint(2,13);
				} else {
					m(1,8); d(6,8); d(6,6); d(10,6); d(10,10); d(6,10); d(6,9);
					m(8,1); d(8,5); m(11,8); d(15,8); m(8,11); d(8,15);
					m(8,8); d(8,8);
					g.setColor(Roi.getColor());
					m(7,7); d(9,7);
					m(7,8); d(9,8);
					m(7,9); d(9,9);
				}
				drawTriangle(16,16);
				return;
			case WAND:
				xOffset = x+2; yOffset = y+1;
				dot(4,0);  m(2,0); d(3,1); d(4,2);  m(0,0); d(1,1);
				m(0,2); d(1,3); d(2,4);  dot(0,4); m(3,3); d(15,15);
				g.setColor(Roi.getColor());
				m(1,2); d(3,2); m(2,1); d(2,3);
				return;
			case TEXT:
				xOffset = x; yOffset = y;
				m(1,16); d(9,0); d(16,16);
				m(0,16); d(2,16);
				m(15,16); d(17,16);
				m(4,10); d(13,10);
				return; 
			case MAGNIFIER:
				xOffset = x; yOffset = y;
				g.drawOval(x+3, y, 13*scale, 13*scale);
				m(5,12); d(-1,18);
				drawTriangle(15,17);
				return;
			case HAND:
				xOffset = x; yOffset = y;
				polyline(5,17,5,16,0,11,0,8,1,8,5,11,5,2,8,2,8,8,8,0,11,0,11,8,11,1,14,1,14,9,14,3,17,3,17,12,16,13,16,17);
				return;
			case DROPPER:
				// draw foreground/background rectangles	
				g.setColor(backgroundColor);
				g.fillRect(x+2*scale, y+3*scale, 15*scale, 16*scale);
				g.drawRect(x, y+2*scale, 13*scale, 13*scale);
				g.setColor(foregroundColor);
				g.fillRect(x, y+2*scale, 13*scale, 13*scale);
				// draw dropper icon		
				xOffset = x+3; yOffset = y-4;
				g.setColor(toolColor);
				m(12,2); d(14,2);
				m(11,3); d(15,3);
				m(11,4); d(15,4);
				m(8,5); d(15,5);
				m(9,6); d(14,6);
				polyline(10,7,12,7,12,9);
				polyline(9,6,2,13,2,15,4,15,11,8);
				g.setColor(gray);
				polygon(9,6,2,13,2,15,4,15,11,8);
				drawTriangle(12,21);
				return;
			case ANGLE:
				xOffset = x; yOffset = y+3;
				m(0,11); d(13,-1); m(0,11); d(16,11); 
				m(10,11); d(10,8); m(9,7); d(9,6); dot(8,5);
				drawDot(13,-2); drawDot(16,10);
				return;
		}
	}
	
	void drawTriangle(int x, int y) {
		g.setColor(triangleColor);
		xOffset+=x*scale; yOffset+=y*scale;
		m(0,0); d(4,0); m(1,1); d(3,1); m(2,2); d(2,2);
	}
	
	void drawDot(int x, int y) {
		g.fillRect(xOffset+x*scale, yOffset+y*scale, 2*scale, 2*scale);
	}

	void drawPoint(int x, int y) {
		g.setColor(toolColor);
		m(x-3,y); d(x+3,y);
		m(x,y-3); d(x,y+3);
		g.setColor(Roi.getColor());
		dot(x,y); dot(x-1,y-1);
	}
	
	void drawIcon(Graphics g, int tool, int x, int y) {
		if (null==g) return;
		icon = icons[tool];
		if (icon==null) return;
		this.icon = icon;
		int x1, y1, x2, y2;
		pc = 0;
		if (icon.trim().startsWith("icon:")) {
			String path = IJ.getDir("macros")+"toolsets/icons/"+icon.substring(icon.indexOf(":")+1);
			try {
				BufferedImage bi = ImageIO.read(new File(path));
				if (scale==1)
					g.drawImage(bi,x-5,y-5,null);
				else {
					int size = Math.max(bi.getWidth(),bi.getHeight());
					g.drawImage(bi,x-5*scale,y-5*scale, size*scale,size*scale,null);
				}
			} catch (Exception e) {
				IJ.error("Toolbar", "Error reading tool icon:\n"+path);
			}
		} else {
			while (true) {
				char command = icon.charAt(pc++);
				if (pc>=icon.length()) break;
				switch (command) {
					case 'B': x+=v(); y+=v(); break;  // adjust base
					case 'N': x-=v(); y-=v(); break;  // adjust base negatively
					case 'R': g.drawRect(x+v(), y+v(), v(), v()); break;  // rectangle
					case 'F': g.fillRect(x+v(), y+v(), v(), v()); break;  // filled rectangle
					case 'O': g.drawOval(x+v(), y+v(), v(), v()); break;  // oval
					case 'V': case 'o': g.fillOval(x+v(), y+v(), v(), v()); break;  // filled oval
					case 'C': // set color
						int saveScale = scale;
						scale = 1;
						int v1=v(), v2=v(), v3=v();
						int red=v1*16, green=v2*16, blue=v3*16;
						if (red>255) red=255; if (green>255) green=255; if (blue>255) blue=255;
						Color color = v1==1&&v2==2&&v3==3?foregroundColor:new Color(red,green,blue);
						g.setColor(color);
						scale = saveScale;
						break; 
					case 'L': g.drawLine(x+v(), y+v(), x+v(), y+v()); break; // line
					case 'D':  g.fillRect(x+v(), y+v(), scale, scale); break; // dot
					case 'P': // polyline
						Polygon p = new Polygon();
						p.addPoint(x+v(), y+v());
						while (true) {
							x2=v(); if (x2==0) break;
							y2=v(); if (y2==0) break;
							p.addPoint(x+x2, y+y2);
						}
						g.drawPolyline(p.xpoints, p.ypoints, p.npoints);
						break;
					case 'G': case 'H':// polygon or filled polygon
						p = new Polygon();
						p.addPoint(x+v(), y+v());
						while (true) {
							x2=v(); y2=v();
							if (x2==0 && y2==0 && p.npoints>2)
								break;
							p.addPoint(x+x2, y+y2);
						}
						if (command=='G')
							g.drawPolygon(p.xpoints, p.ypoints, p.npoints);
						else
							g.fillPolygon(p.xpoints, p.ypoints, p.npoints);
						break;
					case 'T': // text (one character)
						x2 = x+v()-2;
						y2 = y+v();
						int size = v()*10+v()+1;
						char[] c = new char[1];
						c[0] = pc<icon.length()?icon.charAt(pc++):'e';
						g.setFont(new Font("SansSerif", Font.PLAIN, size));
						g.drawString(new String(c), x2, y2);
						break;
					default: break;
				}
				if (pc>=icon.length()) break;
			}
		}
		if (menus[tool]!=null && menus[tool].getItemCount()>0) { 
			xOffset = x; yOffset = y;
			drawTriangle(15, 16);
		}
	}
	
	int v() {
		if (pc>=icon.length()) return 0;
		char c = icon.charAt(pc++);
		switch (c) {
			case '0': return 0*scale;
			case '1': return 1*scale;
			case '2': return 2*scale;
			case '3': return 3*scale;
			case '4': return 4*scale;
			case '5': return 5*scale;
			case '6': return 6*scale;
			case '7': return 7*scale;
			case '8': return 8*scale;
			case '9': return 9*scale;
			case 'a': return 10*scale;
			case 'b': return 11*scale;
			case 'c': return 12*scale;
			case 'd': return 13*scale;
			case 'e': return 14*scale;
			case 'f': return 15*scale;
			case 'g': return 16*scale;
			case 'h': return 17*scale;
			case 'i': return 18*scale;
			case 'j': return 19*scale;
			case 'k': return 20*scale;
			case 'l': return 21*scale;
			case 'm': return 22*scale;
			case 'n': return 23*scale;
			default: return 0;
		}
	}
	
	private void showMessage(int tool) {
		if (IJ.statusBarProtected())
			return;
		if (tool>=UNUSED && tool<getNumTools() && names[tool]!=null) {
			String name = names[tool];
			int index = name.indexOf("Action Tool");
			if (index!=-1)
				name = name.replace("Action Tool", "Tool");
			else {
				index = name.indexOf("Menu Tool");
				if (index!=-1)
					name = name.substring(0, index+4);
			}
			IJ.showStatus(name);
			return;
		}
		String hint = " (alt or long click to switch)";
		String hint2 = " (alt or long click to switch; double click to configure)";
		switch (tool) {
			case RECTANGLE:
				if (rectType==ROUNDED_RECT_ROI)
					IJ.showStatus("Rectangle, *rounded rect* or rotated rect"+hint);
				else if (rectType==ROTATED_RECT_ROI)
					IJ.showStatus("Rectangle, rounded rect or *rotated rect*"+hint);
				else
					IJ.showStatus("*Rectangle*, rounded rect or rotated rect"+hint);
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
					IJ.showStatus("*Multi-point* or point"+hint2);
				else
					IJ.showStatus("*Point* or multi-point"+hint2);
				return;
			case WAND:
				IJ.showStatus("Wand (tracing) tool");
				return;
			case TEXT:
				IJ.showStatus("Text tool (double-click to configure)");
				return;
			case MAGNIFIER:
				IJ.showStatus("Magnifying glass (or \"+\" and \"-\" keys; alt or long click for menu)");
				return;
			case HAND:
				IJ.showStatus("Scrolling tool (or press space bar and drag)");
				return;
			case DROPPER:
				String fg = foregroundColor.getRed() + "," + foregroundColor.getGreen() + "," + foregroundColor.getBlue();
				String bg = backgroundColor.getRed() + "," + backgroundColor.getGreen() + "," + backgroundColor.getBlue();
				IJ.showStatus("Color picker " +  fg + "/"+ bg + " (alt or long click for menu)");
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
		this.x = xOffset+x*scale;
		this.y = yOffset+y*scale;
	}

	private void d(int x, int y) {
		x *= scale;
		y *= scale;
		x += xOffset;
		y += yOffset;
		g.drawLine(this.x, this.y, x, y);
		this.x = x;
		this.y = y;
	}
	
	private void dot(int x, int y) {
		g.fillRect(xOffset+x*scale, yOffset+y*scale, 1*scale, 1*scale);
	}
	
	private void polyline(int... values) {
		Polygon p = new Polygon();
		int n = values.length/2;
		for (int i=0; i<n; i++)
			p.addPoint(values[i*2]*scale+xOffset, values[i*2+1]*scale+yOffset);
		g.drawPolyline(p.xpoints, p.ypoints, p.npoints);
	}

	private void polygon(int... values) {
		Polygon p = new Polygon();
		int n = values.length/2;
		for (int i=0; i<n; i++)
			p.addPoint(values[i*2]*scale+xOffset, values[i*2+1]*scale+yOffset);
		g.fillPolygon(p.xpoints, p.ypoints, p.npoints);
	}

	private void resetButtons() {
		for (int i=0; i<getNumTools(); i++)
			down[i] = false;
	}

	public void paint(Graphics g) {
		drawButtons(g);
	}

	public boolean setTool(String name) {
		if (name==null) return false;
		if (name.indexOf(" Tool")!=-1) { // macro tool?
			for (int i=UNUSED; i<getNumTools(); i++) {
				if (name.equals(names[i])) {
					setTool(i);
					return true;
				}
			}
		}
		name = name.toLowerCase(Locale.US);
		boolean ok = true;
		if (name.indexOf("round")!=-1) {
			rectType = ROUNDED_RECT_ROI;
			setTool(RECTANGLE);
		} else if (name.indexOf("rot")!=-1) {
			rectType = ROTATED_RECT_ROI;
			setTool(RECTANGLE);
		} else if (name.indexOf("rect")!=-1) {
			rectType = RECT_ROI;
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
		if (current>=UNUSED && current<instance.getNumTools() && instance.names[current]!=null)
			name = instance.names[current];
		return name!=null?name:"";
	}

	/** Returns the name of the specified tool. */
	String getName(int id) {
		switch (id) {
			case RECTANGLE:
				switch (rectType) {
					case RECT_ROI: return "rectangle";
					case ROUNDED_RECT_ROI: return "roundrect";
					case ROTATED_RECT_ROI: return "rotrect";
				}
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
		if (tool==UNUSED)  //  "Unused" (blank) tool replaced with gap in 1.48h
			tool = CUSTOM1;
		else if (legacyMode && tool>=CUSTOM1)
			tool++;
		if (IJ.debugMode) IJ.log("Toolbar.setTool: "+tool);
		if ((tool==current&&!(tool==RECTANGLE||tool==OVAL||tool==POINT)) || tool<0 || tool>=getNumTools()-1)
			return;
		if (tool>=CUSTOM1&&tool<=getNumTools()-2) {
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
		previousTool = current;
		current = tool;
		Graphics g = this.getGraphics();
		if (g==null)
			return;
		down[current] = true;
		if (current!=previousTool)
			down[previousTool] = false;
		Graphics2D g2d = (Graphics2D)g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		setStrokeWidth(g2d);
		drawButton(g, previousTool);
		drawButton(g, current);
		if (null==g) return;
		g.dispose();
		showMessage(current);
		if (Recorder.record) {
			String name = getName(current);
			if (name!=null) {
				IJ.wait(100); // workaround for OSX/Java 8 bug
				Recorder.record("setTool", name);
			}
		}
		if (legacyMode)
			repaint();
		if (!previousName.equals(getToolName())) {
			IJ.notifyEventListeners(IJEventListener.TOOL_CHANGED);;
			repaint();
		}
	}
	
	boolean isValidTool(int tool) {
		if (tool<0 || tool>=getNumTools())
			return false;
		if (tool>=CUSTOM1 && tool<getNumTools() && names[tool]==null)
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
		if (c==null)
			return;
		foregroundColor = c;
		foregroundValue = Double.NaN;
		IJ.notifyEventListeners(IJEventListener.FOREGROUND_COLOR_CHANGED);
		if (instance==null)
			return;
		repaintTool(DROPPER);
		for (int i=CUSTOM1; i<=instance.getNumTools()-2; i++) {
			if (instance.icons[i]!=null && instance.icons[i].contains("C123"))
				repaintTool(i);  // some of this tool's icon is drawn in the foreground color
		}
		if (!IJ.isMacro()) setRoiColor(c);
	}

	public static Color getBackgroundColor() {
		return backgroundColor;
	}

	public static void setBackgroundColor(Color c) {
		if (c!=null) {
			backgroundColor = c;
			backgroundValue = Double.NaN;
			repaintTool(DROPPER);
			IJ.notifyEventListeners(IJEventListener.BACKGROUND_COLOR_CHANGED);
		}
	}
	
	public static double getForegroundValue() {
		return foregroundValue;
	}
	
	public static void setForegroundValue(double value) {
		if (value>=0) {
			int v = (int)value;
			if (v>255) v=255;
			setForegroundColor(new Color(v,v,v));
		}
		foregroundValue = value;
	}

	public static double getBackgroundValue() {
		return backgroundValue;
	}

	public static void setBackgroundValue(double value) {
		if (value>=0) {
			int v = (int)value;
			if (v>255) v=255;
			setBackgroundColor(new Color(v,v,v));
		}
		backgroundValue = value;
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

	/** Returns the size of the selection brush tool, or 0 if the brush tool is not enabled. */
	public static int getBrushSize() {
		if (ovalType==BRUSH_ROI)
			return brushSize;
		else
			return 0;
	}

	/** Set the size of the selection brush tool, in pixels. */
	public static void setBrushSize(int size) {
		brushSize = size;
		if (brushSize<1) brushSize = 1;
		Prefs.set(BRUSH_SIZE, brushSize);
	}
		
	/** Returns the rounded rectangle arc size, or 0 if the rounded rectangle tool is not enabled. */
	public static int getRoundRectArcSize() {
		if (rectType==ROUNDED_RECT_ROI)
			return arcSize;
		else
			return 0;
	}

	/** Sets the rounded rectangle corner diameter (pixels). */
	public static void setRoundRectArcSize(int size) {
		if (size<=0)
			rectType = RECT_ROI;
		else {
			arcSize = size;
			Prefs.set(CORNER_DIAMETER, arcSize);
		}
		repaintTool(RECTANGLE);
		ImagePlus imp = WindowManager.getCurrentImage();
		Roi roi = imp!=null?imp.getRoi():null;
		if (roi!=null && roi.getType()==Roi.RECTANGLE)
			roi.setCornerDiameter(rectType==ROUNDED_RECT_ROI?arcSize:0);
	}

	/** Returns 'true' if the multi-point tool is enabled. */
	public static boolean getMultiPointMode() {
		return multiPointMode;
	}

	/** Returns the rectangle tool type (RECT_ROI, ROUNDED_RECT_ROI or ROTATED_RECT_ROI). */
	public static int getRectToolType() {
		return rectType;
	}

	/** Returns the oval tool type (OVAL_ROI, ELLIPSE_ROI or BRUSH_ROI). */
	public static int getOvalToolType() {
		return ovalType;
	}

	/** Returns the button width (button spacing). */
	public static int getButtonSize() {
		return buttonWidth;
	}
	
	public static void repaintTool(int tool) {
		Toolbar tb = getInstance();
		if (tb!=null) {
			Graphics g = tb.getGraphics();
			if (IJ.debugMode) IJ.log("Toolbar.repaintTool: "+tool+" "+g);
			if (g==null) return;
			if (dscale>1.0)
				tb.setStrokeWidth((Graphics2D)g);
			((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			tb.drawButton(g, tool);
			if (g!=null) g.dispose();
		}
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
			case UNUSED: return 12;
			default: return tool - 2;
		}
    }

	// Returns the tool corresponding to the specified x coordinate
	private int toolID(int x) {
		if (x>buttonWidth*12+gapSize)
			x -= gapSize;
		int index = x/buttonWidth;
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
			default: return index + 3;
		}
    }
    
	private boolean inGap(int x) {
		return x>=(buttonWidth*12) && x<(buttonWidth*12+gapSize);
 	}

	public void triggerPopupMenu(int newTool, MouseEvent e, boolean isRightClick, boolean isLongPress) {
		mpPrevious = current;
		if (isMacroTool(newTool)) {
			String name = names[newTool];
			if (newTool==UNUSED || name.contains("Unused Tool"))
				return;
			if (name.indexOf("Action Tool")!=-1) {
				if (e.isPopupTrigger()||e.isMetaDown()) {
					name = name.endsWith(" ")?name:name+" ";
					tools[newTool].runMacroTool(name+"Options");
				} else {
					//drawTool(newTool, true);
					//IJ.wait(50);
					drawTool(newTool, false);
					runMacroTool(newTool);
				}
				return;
			} else {	
				name = name.endsWith(" ")?name:name+" ";
				tools[newTool].runMacroTool(name+"Selected");
			}
		}
		if (!isLongPress)
			setTool2(newTool);
		int x = e.getX();
		int y = e.getY();
		if (current==RECTANGLE && isRightClick) {	
			rectItem.setState(rectType==RECT_ROI);	
			roundRectItem.setState(rectType==ROUNDED_RECT_ROI);	
			rotatedRectItem.setState(rectType==ROTATED_RECT_ROI);	
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
		if (current==MAGNIFIER && isRightClick) {	
			zoomPopup.show(e.getComponent(),x,y);	
			mouseDownTime = 0L;	
		}				
		if (current==DROPPER && isRightClick) {	
			pickerPopup.show(e.getComponent(),x,y);	
			mouseDownTime = 0L;	
		}	
		if (isMacroTool(current) && isRightClick) {	
			String name = names[current].endsWith(" ")?names[current]:names[current]+" ";	
			tools[current].runMacroTool(name+"Options");	
		}	
		if (isPlugInTool(current) && isRightClick) {	
			tools[current].showPopupMenu(e, this);	
		}
	}

	public void mousePressed(final MouseEvent e) {
		int x = e.getX();
		if (inGap(x))
			return;
 		final int newTool = toolID(x);
		if (newTool==getNumTools()-1) {
			showSwitchPopupMenu(e);
			return;
		}
		if (!isValidTool(newTool))
			return;
		if (menus[newTool]!=null && menus[newTool].getItemCount()>0) {
            menus[newTool].show(e.getComponent(), e.getX(), e.getY());
			return;
		}
		int flags = e.getModifiers();
		boolean isRightClick = e.isPopupTrigger()||(!IJ.isMacintosh()&&(flags&Event.META_MASK)!=0);
		boolean doubleClick = newTool==current && (System.currentTimeMillis()-mouseDownTime)<=DOUBLE_CLICK_THRESHOLD;
 		mouseDownTime = System.currentTimeMillis();
		if (!doubleClick || isRightClick) {
			triggerPopupMenu(newTool, e, isRightClick, false);
			if (isRightClick) mouseDownTime = 0L;
		} else if(!isRightClick) { //double click
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
					if (rectType==ROUNDED_RECT_ROI)
						IJ.doCommand("Rounded Rect Tool...");
					else
						IJ.doCommand("Roi Defaults...");
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
					IJ.doCommand("Point Tool...");
					break;
				case WAND:
					IJ.doCommand("Wand Tool...");
					break;
				case TEXT:
					IJ.run("Fonts...");
					break;
				case DROPPER:
					IJ.doCommand("Color Picker...");
					setTool2(mpPrevious);
					break;
				default:
			}
		}

		if (!isRightClick && longClickDelay>0) {
			if (pressTimer==null)
				pressTimer = new Timer();			
			pressTimer.schedule(new TimerTask() {
				public void run() {
					if (pressTimer != null) {
						pressTimer.cancel();
						pressTimer = null;
					}
					triggerPopupMenu(newTool, e, true, true);
				}
			}, longClickDelay);
		}
		
	}
	
	public void mouseReleased(MouseEvent e) {
		if (pressTimer!=null) {
			pressTimer.cancel();
			pressTimer = null;
		}
	}
	
	void showSwitchPopupMenu(MouseEvent e) {
		String path = IJ.getDir("macros")+"toolsets/";
		if (path==null)
			return;
		boolean applet = IJ.getApplet()!=null;
		File f = new File(path);
		String[] list;
		if (!applet && f.exists() && f.isDirectory()) {
			list = f.list();
			if (list==null) return;
			Arrays.sort(list);
		} else
			list = new String[0];
		switchPopup.removeAll();
        path = IJ.getDir("macros") + "StartupMacros.txt";
		f = new File(path);
		if (!f.exists()) {
			path = IJ.getDir("macros") + "StartupMacros.ijm";
			f = new File(path);
		}
		if (!applet && f.exists())
            addItem("Startup Macros");
        else
            addItem("StartupMacros*");
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
		addItem("Overlay Editing Tools*");
		addPluginTools();
		addItem("Restore Startup Tools");
		addItem("Remove Custom Tools");
		addItem("Help...");
		add(ovalPopup);
		if (IJ.isMacOSX()) IJ.wait(10);
		switchPopup.show(e.getComponent(), e.getX(), e.getY());
	}

	private void addPluginTools() {
		switchPopup.addSeparator();
		for (int i=0; i<builtInTools.length; i++)
			addBuiltInTool(builtInTools[i]);
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
		if (!drawDown) {
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
		return tool>=CUSTOM1 && tool<getNumTools() && names[tool]!=null && (tools[tool] instanceof MacroToolRunner);
	}
	
	boolean isPlugInTool(int tool) {
		return tool>=CUSTOM1 && tool<getNumTools() && tools[tool]!=null;
	}

	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void  mouseDragged(MouseEvent e) {}
	
	public void itemStateChanged(ItemEvent e) {
		CheckboxMenuItem item = (CheckboxMenuItem)e.getSource();
		String previousName = getToolName();
		if (item==rectItem || item==roundRectItem || item==rotatedRectItem) {
			if (item==roundRectItem)
				rectType = ROUNDED_RECT_ROI;
			else if (item==rotatedRectItem)
				rectType = ROTATED_RECT_ROI;
			else
				rectType = RECT_ROI;
			repaintTool(RECTANGLE);
			showMessage(RECTANGLE);
			ImagePlus imp = WindowManager.getCurrentImage();
			Roi roi = imp!=null?imp.getRoi():null;
			if (roi!=null && roi.getType()==Roi.RECTANGLE)
				roi.setCornerDiameter(rectType==ROUNDED_RECT_ROI?arcSize:0);
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
			if (!(label.equals("Help...")||label.equals("Remove Custom Tools")) && !isTool && !label.endsWith("Tool") && !label.endsWith("Tool "))
				currentSet = label;
			if (isTool) {
				if (cmd.equals("Tool")) // built in tool
					installBuiltinTool(label);
				else  // plugin or macro tool in ImageJ/plugins/Tools
					IJ.run(label);
				return;
			}
			String path;
			if (label.equals("Remove Custom Tools")) {
				removeTools();
			} else if (label.equals("Restore Startup Tools")) {
				removeTools();
				installStartupMacros();
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
                if (IJ.shiftKeyDown())
				    showCode(label, mi.openFromIJJar(path));
				else {
				    resetTools();
				    mi.installFromIJJar(path);
                }
            } else {
                // load from ImageJ/macros/toolsets
                if (label.equals("Startup Macros")) {
                	installStartupMacros();
                	return;
                } else if (label.endsWith(" "))
                    path = IJ.getDir("macros")+"toolsets"+File.separator+label.substring(0, label.length()-1)+".ijm";
                else
                    path = IJ.getDir("macros")+"toolsets"+File.separator+label+".txt";
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
	
	private void removeTools() {
		removeMacroTools();
		setTool(RECTANGLE);
		currentSet = "Startup Macros";
		resetPrefs();
		if (nExtraTools>0) {
			String name = names[getNumTools()-1];
			String icon = icons[getNumTools()-1];
			nExtraTools = 0;
			names[getNumTools()-1] = name;
			icons[getNumTools()-1] = icon;
			ps = new Dimension(buttonWidth*NUM_BUTTONS-(BUTTON_WIDTH-gapSize)+nExtraTools*BUTTON_WIDTH, buttonHeight);
			IJ.getInstance().pack();
		}
	}
	
	private void resetPrefs() {
		for (int i=0; i<7; i++) {
			String key = TOOL_KEY+(i/10)%10+i%10;
			if (!Prefs.get(key, "").equals(""))
				Prefs.set(key, "");
		}
	}
	
	public static void restoreTools() {
		Toolbar tb = Toolbar.getInstance();
		if (tb!=null) {
			if (tb.getToolId()>=UNUSED)
				tb.setTool(RECTANGLE);
			tb.installStartupMacros();
		}
	}

	private 	void installStartupMacros() {
		resetTools();
		String path = IJ.getDir("macros")+"StartupMacros.txt";
		File f = new File(path);
		if (!f.exists()) {
			path = IJ.getDir("macros")+"StartupMacros.ijm";
			f = new File(path);
		}
		if (!f.exists()) {
			path = IJ.getDir("macros")+"StartupMacros.fiji.ijm";
			f = new File(path);
		}
		if (!f.exists()) {
			IJ.error("StartupMacros not found in\n \n"+IJ.getDir("macros"));
			return;
		}
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
		
		if (zoomPopup==popup) {
			if ("Zoom In".equals(cmd))
				IJ.runPlugIn("ij.plugin.Zoom", "in");
			else if ("Zoom Out".equals(cmd))
				IJ.runPlugIn("ij.plugin.Zoom", "out");
			else if ("Reset Zoom".equals(cmd))
				IJ.runPlugIn("ij.plugin.Zoom", "orig");
			else if ("View 100%".equals(cmd))
				IJ.runPlugIn("ij.plugin.Zoom", "100%");
			else if ("Zoom To Selection".equals(cmd))
				IJ.runPlugIn("ij.plugin.Zoom", "to");
			else if ("Scale to Fit".equals(cmd))
				IJ.runPlugIn("ij.plugin.Zoom", "scale");
			else if ("Set...".equals(cmd))
				IJ.runPlugIn("ij.plugin.Zoom", "set");
			else if ("Maximize".equals(cmd))
				IJ.runPlugIn("ij.plugin.Zoom", "max");
			setTool(previousTool);			
			return;
		}
		
		if (pickerPopup==popup) {
			if ("White/Black".equals(cmd)) {
				setForegroundColor(Color.white);
				setBackgroundColor(Color.black);				
			} else if ("Black/White".equals(cmd)) {
				setForegroundColor(Color.black);
				setBackgroundColor(Color.white);				
			} else if ("Red".equals(cmd))
				setForegroundColor(Color.red);
			else if ("Green".equals(cmd))
				setForegroundColor(Color.green);
			else if ("Blue".equals(cmd))
				setForegroundColor(Color.blue);
			else if ("Yellow".equals(cmd))
				setForegroundColor(Color.yellow);
			else if ("Cyan".equals(cmd))
				setForegroundColor(Color.cyan);
			else if ("Magenta".equals(cmd))
				setForegroundColor(Color.magenta);
			else if ("Foreground...".equals(cmd))
				setForegroundColor(new ColorChooser("Select Foreground Color", foregroundColor, false).getColor());
			else if ("Background...".equals(cmd))
				setBackgroundColor(new ColorChooser("Select Background Color", backgroundColor, false).getColor());
			else
				IJ.run("Color Picker...", "");
			if (!"Color Picker".equals(cmd))
				ColorPicker.update();	
			setTool(previousTool);			
			return;
		}
		
		int tool = -1;
		for (int i=CUSTOM1; i<getNumTools(); i++) {
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
		if (inGap(x))
			IJ.showStatus("");
		else
			showMessage(toolID(x));
	}

	/** Adds a tool to the toolbar. The 'toolTip' string is displayed in the status bar
		 when the mouse is over the tool icon. The 'toolTip' string may include icon 
		(http://imagej.nih.gov/ij/developer/macro/macros.html#tools).
		Returns the tool ID, or -1 if all tool slots are in use. */
	public int addTool(String toolTip) {
		int index = toolTip.indexOf('-');
		boolean hasIcon = index>=0 && (toolTip.length()-index)>4;
		int tool =-1;
		for (int i=CUSTOM1; i<=getNumTools()-2; i++) {
			if (names[i]==null || toolTip.startsWith(names[i])) {
				tool = i;
				break;
			}			
		}
		if (tool==CUSTOM1)
			legacyMode = toolTip.startsWith("Select and Transform Tool"); //TrakEM2
		if (tool==-1 && (nExtraTools<MAX_EXTRA_TOOLS)) {
			nExtraTools++;
			names[getNumTools()-1] = names[getNumTools()-2];
			icons[getNumTools()-1] = icons[getNumTools()-2];
			names[getNumTools()-2] = null;
			icons[getNumTools()-2] = null;
			ps = new Dimension(buttonWidth*NUM_BUTTONS-(buttonWidth-gapSize)+nExtraTools*buttonWidth, buttonHeight);
			IJ.getInstance().pack();
			tool = getNumTools()-2;
		}
		if (tool==-1) {
			if (addingSingleTool)
				tool = getNumTools()-2;
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
        if (IJ.debugMode) IJ.log("Toolbar.addTool: "+tool+" "+toolTip);
		return tool;
	}
    
    void installMenu(int tool) {
        Program pgm = macroInstaller.getProgram();
        Hashtable h = pgm.getMenus();
        if (h==null) return;
        String[] commands = (String[])h.get(names[tool]);
        if (commands==null)
        	return;
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
		if (id==0) {
			resetTools();
			if (name.startsWith("Unused"))
				return;
 		}
		if (name.endsWith(" Built-in Tool")) {
			name = name.substring(0,name.length()-14);
			doNotSavePrefs = true;
			boolean ok = installBuiltinTool(name);
			if (!ok) {
				Hashtable commands = Menus.getCommands();
				if (commands!=null && commands.get(name)!=null)
					IJ.run(name);
			}
			doNotSavePrefs = false;
			return;
		}
		this.macroInstaller = macroInstaller;
		int tool = addTool(name);
		this.macroInstaller = null;
		if (tool!=-1)
			tools[tool] = new MacroToolRunner(macroInstaller);
	}
	
	private void resetTools() {
		for (int i=CUSTOM1; i<getNumTools()-1; i++) {
			names[i] = null;
			tools[i] = null;
			icons[i] = null;
			if (menus[i]!=null)
				menus[i].removeAll();
		}
	}
	
	/** Used by the MacroInstaller class to add a macro tool to the toolbar. */
	public void addMacroTool(String name, MacroInstaller macroInstaller) {
		String custom1Name = names[CUSTOM1];
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
			if ((tool-CUSTOM1)>0 || custom1Name==null)
				setPrefs(tool);
		}
	}
	
	private void setPrefs(int id) {
		if (doNotSavePrefs)
			return;
		boolean ok = isBuiltInTool(names[id]);
		String prefsName = instance.names[id];
		if (!ok) {
			String name = names[id];
			int i = name.indexOf(" (");  // remove any hint in parens
			if (i>0) {
				name = name.substring(0, i);
				prefsName=name;
			}
		}
		int index = id - CUSTOM1;
		String key = TOOL_KEY + (index/10)%10 + index%10;
		Prefs.set(key, prefsName);
	}
	
	private boolean isBuiltInTool(String name) {
		for (int i=0; i<builtInTools2.length; i++) {
			if (name.equals(builtInTools2[i]))
				return true;
		}
		for (int i=0; i<builtInTools.length; i++) {
			if (name.startsWith(builtInTools[i]))
				return true;
		}
		return false;
	}

	public static void removeMacroTools() {
		if (instance!=null) {
			if (instance.getToolId()>=CUSTOM1)
				instance.setTool(RECTANGLE);
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
		PlugInTool tool = null;
		if (instance==null)
			return null;
		if (current<instance.tools.length)
			tool = instance.tools[current];
		if (tool!=null && tool instanceof MacroToolRunner)
			tool = null;
		return tool;
	}

	void runMacroTool(int id) {
		if (id<getNumTools() && tools[id]!=null)
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
		if (IJ.debugMode) IJ.log("Toolbar.installStartupTools");
		for (int i=0; i<=6; i++) {
			String name = Prefs.get(TOOL_KEY + (i/10)%10 + i%10, "");
			if (IJ.debugMode) IJ.log("  "+i+" "+name);
			if (name.equals("")) continue;
			installingStartupTool = true;
			boolean ok = installBuiltinTool(name);
			if (!ok) {
				ok = installToolsetTool(name);
				if (!ok) {  // install tool in plugins/Tools
					if (name.endsWith("Menu Tool"))
						name = name.substring(0, name.length()-5);
					Hashtable commands = Menus.getCommands();
					if (commands!=null && commands.get(name)!=null)
						IJ.run(name);
				}
			}
			installingStartupTool = false;
		}
	}
	
	// install tool from ImageJ/macros/toolsets
	private boolean installToolsetTool(String name) {
		String path = IJ.getDir("macros")+"toolsets"+File.separator+name+".ijm";
		if (!((new File(path)).exists())) {
			name = name.replaceAll(" ", "_");
			path = IJ.getDir("macros")+"toolsets"+File.separator+name+".ijm";
		}
		String text = IJ.openAsString(path);
		if (text==null || text.startsWith("Error"))
			return false;
		new MacroInstaller().installSingleTool(text);
		return true;
	}
	
	private boolean installBuiltinTool(String label) {
		if (IJ.debugMode) IJ.log("Toolbar.installBuiltinTool: "+label);
		boolean ok = true;
		PlugInTool tool = null;
		if (label.startsWith("Arrow")) {
			tool = new ij.plugin.tool.ArrowTool();
			if (tool!=null) tool.run("");
			showSource("Arrow");
		} else if (label.startsWith("Overlay Brush")) {
			tool = new ij.plugin.tool.OverlayBrushTool();
			if (tool!=null) tool.run("");
			showSource("OverlayBrush");
		} else if (label.startsWith("Pixel Inspect")) {
			tool = new ij.plugin.tool.PixelInspectionTool();
			if (tool!=null) tool.run("");
			showSource("PixelInspection");
		} else if (label.startsWith("Brush")||label.startsWith("Paintbrush")) {
			tool = new ij.plugin.tool.BrushTool();
			if (tool!=null) tool.run("");
			showSource("Brush");
		} else if (label.startsWith("Pencil")) {
			tool = new ij.plugin.tool.BrushTool();
			if (tool!=null) tool.run("pencil");
			showSource("Brush");
		} else if (label.startsWith("Selection Rotator")) {
			tool = new ij.plugin.tool.RoiRotationTool();
			if (tool!=null) tool.run("");
			showSource("RoiRotation");
		} else if (label.startsWith("Flood Fill"))
			installMacroFromJar("/macros/FloodFillTool.txt");
		else if (label.startsWith("Spray Can"))
			installMacroFromJar("/macros/SprayCanTool.txt");
		else if (label.startsWith("Developer Menu"))
			installMacroFromJar("/macros/DeveloperMenuTool.txt");
		else if (label.startsWith("Stacks Menu"))
			installMacroFromJar("/macros/StacksMenuTool.txt");
		else if (label.startsWith("LUT Menu"))
			installMacroFromJar("/macros/LUTMenuTool.txt");
		else if (label.startsWith("Command Finder"))
			installMacroFromJar("/macros/CommandFinderTool.txt");
		else if (label.startsWith("ROI"))
			installMacroFromJar("/macros/RoiMenuTool.txt");
		else if (label.startsWith("Label"))
			installMacroFromJar("/macros/Label_Tool.txt");
		else
			ok = false;
		return ok;
	}
	
	private void showSource(String name) {
		if (IJ.shiftKeyDown()) {
			IJ.runPlugIn("ij.plugin.BrowserLauncher", IJ.URL+"/source/ij/plugin/tool/"+name+"Tool.java");
			IJ.setKeyUp(KeyEvent.VK_SHIFT);
		}
	}
	
	private void installMacroFromJar(String path) {
		if (IJ.shiftKeyDown())
			showCode(path, (new MacroInstaller()).openFromIJJar(path));
		else
			(new MacroInstaller()).installFromIJJar(path);
	}
	
	public static void showCode(String title, String code) {
		Editor ed = new Editor();
		ed.setSize(550, 450);
		ed.create(title, code);
		IJ.setKeyUp(KeyEvent.VK_SHIFT);
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
	
	public int getNumTools() {
		return NUM_TOOLS + nExtraTools;
	}
	
	/** Sets the tool menu long click delay in milliseconds
	 * (default is 600). Set to 0 to disable long click triggering.
	*/
	 public static void setLongClickDelay(int delay) {
		longClickDelay = delay;
	}
	
	/** Sets the icon of the specified macro or plugin tool.<br>
	 * See: Help&gt;Examples&gt;Tool&gt;Animated Icon Tool;
	*/
	 public static void setIcon(String toolName, String icon) {
	 	if (instance==null)
	 		return;
	 	int tool = 0;
	 	for (int i=CUSTOM1; i<instance.getNumTools(); i++) {
	 		if (instance.names[i]!=null && instance.names[i].equals(toolName)) {
	 			tool = i;
	 			break;
	 		}
	 	}
		if (tool>0) {
	 		instance.icons[tool] = icon;
	 		Graphics2D g = (Graphics2D)instance.getGraphics();
	 		instance.setStrokeWidth(g);
	 		instance.drawButton(g, tool);
	 	}
	}

}
