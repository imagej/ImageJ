package ij.plugin.frame;
import ij.*;
import ij.plugin.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;
import javax.swing.BoxLayout;
import ij.process.*;
import ij.gui.*;

/** Implements the Image/Color/Color Picker command. */
public class ColorPicker extends PlugInDialog {
	public static int ybase = 2;
	private int colorWidth = 22;
	private int colorHeight = 16;
	private int columns = 5;
	private int rows = 20;
	private static final String LOC_KEY = "cp.loc";
	private static ColorPicker instance;
	private ColorGenerator cg; 
	private Canvas colorCanvas;
	TextField colorField;
	
    public ColorPicker() {
		super("CP");
		if (instance!=null) {
			instance.toFront();
			return;
		}
		double scale = Prefs.getGuiScale();
		instance = this;
		WindowManager.addWindow(this);
        int width = (int)(columns*colorWidth*scale);
        int height = (int)((rows*colorHeight+ybase)*scale);
        addKeyListener(IJ.getInstance());
		setLayout(new BorderLayout());
		cg = new ColorGenerator(width, height, new int[width*height]);
        cg.drawColors(colorWidth, colorHeight, columns, rows);
        colorCanvas = new ColorCanvas(width, height, this, cg, scale);
        Panel panel = new Panel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(colorCanvas);
		String hexColor = Colors.colorToString(Toolbar.getForegroundColor());
        colorField = new TextField(hexColor+" ",7);
        colorField.setEditable(false);
        colorField.select(hexColor.length(),hexColor.length());
        GUI.scale(colorField);
        panel.add(colorField);
        add(panel);
		setResizable(false);
		pack();
		Point loc = Prefs.getLocation(LOC_KEY);
		if (loc!=null)
			setLocation(loc);
		else
			GUI.centerOnImageJScreen(this);
		show();
	}
    
    public void close() {
	 	super.close();
		instance = null;
		Prefs.saveLocation(LOC_KEY, getLocation());
		IJ.notifyEventListeners(IJEventListener.COLOR_PICKER_CLOSED);
	}
	
	public static void update() {
		ColorPicker cp = instance;
		if (cp!=null && cp.colorCanvas!=null) {
			cp.cg.refreshBackground(false);
			cp.cg.refreshForeground(false);
			cp.colorCanvas.repaint();
			cp.colorField.setText(Colors.colorToString(Toolbar.getForegroundColor()));
		}
	}
	
}

class ColorGenerator extends ColorProcessor {
	private int ybase = ColorPicker.ybase;
    private int w, h;
    private int[] colors = {0xff0000, 0x00ff00, 0x0000ff, 0xffffff, 0x00ffff, 0xff00ff, 0xffff00, 0x000000};

    public ColorGenerator(int width, int height, int[] pixels) {
        super(width, height, pixels);
        setAntialiasedText(true);
    }
    
    void drawColors(int colorWidth, int colorHeight, int columns, int rows) {
        w = colorWidth;
        h = colorHeight;
        setColor(0xffffff);
        setRoi(0, ybase, 110, 320);
        fill();
        drawRamp();
        resetBW();
        flipper();
        //drawLine(0, 256, 110, 256);
        
        refreshBackground(false);
        refreshForeground(false);

        Color c;
        float hue, saturation=1f, brightness=1f;
        double w=colorWidth, h=colorHeight;
        for (int x=2; x<10; x++) {
            for (int y=0; y<32; y++) {
                hue = (float)(y/(2*h)-.15);
                if (x<6) { 
                    saturation = 1f;
                    brightness = (float)(x*4/w);
                } else {
                    saturation = 1f - ((float)((5-x)*-4/w));
                    brightness = 1f;
                }
                c = Color.getHSBColor(hue, saturation, brightness);
                setRoi(x*(int)(w/2), ybase+y*(int)(h/2), (int)w/2, (int)h/2);
                setColor(c);
                fill();
            }
        }
        drawSpectrum(h);        
        resetRoi();
    }
       
    void drawColor(int x, int y, Color c) {
        setRoi(x*w, y*h, w, h);
        setColor(c);
        fill();
    }

    public void refreshBackground(boolean backgroundInFront) {
        //Boundary for Background Selection
        setColor(0x444444);
        drawRect((w*2)-12, ybase+276, (w*2)+4, (h*2)+4);
        setColor(0x999999);
        drawRect((w*2)-11, ybase+277, (w*2)+2, (h*2)+2);
        setRoi((w*2)-10, ybase+278, w*2, h*2);//Paints the Background Color
        Color bg = Toolbar.getBackgroundColor();
        setColor(bg);
        fill();
        if (backgroundInFront)
        	drawLabel("B", bg, w*4-18, ybase+278+h*2);
    }

    public void refreshForeground(boolean backgroundInFront) {
        //Boundary for Foreground Selection
        setColor(0x444444);
        drawRect(8, ybase+266, (w*2)+4, (h*2)+4);
        setColor(0x999999);
        drawRect(9, ybase+267, (w*2)+2, (h*2)+2);
        setRoi(10, ybase+268, w*2, h*2); //Paints the Foreground Color
        Color fg = Toolbar.getForegroundColor();
        setColor(fg);
        fill();
        if (backgroundInFront)
        	drawLabel("F", fg, 12, ybase+268+14);
    }
    
    private void drawLabel(String label, Color c, int x, int y) {
		int intensity = (c.getRed()+c.getGreen()+c.getBlue())/3;
		c = intensity<128?Color.white:Color.black;
		setColor(c);
		drawString(label, x, y);
	}

	void drawSpectrum(double h) {
		Color c;
		for ( int x=5; x<7; x++) {
			for ( int y=0; y<32; y++) {
				float hue = (float)(y/(2*h)-.15);        
				c = Color.getHSBColor(hue, 1f, 1f);
				setRoi(x*(int)(w/2), ybase+y*(int)(h/2), (int)w/2, (int)h/2);
				setColor(c);
				fill();
			}
		}
		setRoi(55, ybase+32, 22, 16); //Solid red
		setColor(0xff0000);
		fill();
		setRoi(55, ybase+120, 22, 16); //Solid green
		setColor(0x00ff00);
		fill();
		setRoi(55, ybase+208, 22, 16); //Solid blue
		setColor(0x0000ff);
		fill();
		setRoi(55, ybase+80, 22, 8); //Solid yellow
		setColor(0xffff00);
		fill();
		setRoi(55, ybase+168, 22, 8); //Solid cyan
		setColor(0x00ffff);
		fill();
		setRoi(55, ybase+248, 22, 8); //Solid magenta
		setColor(0xff00ff);
		fill();
	}

    void drawRamp() {
        int r,g,b;
        for (int x=0; x<w; x++) {
             for (int y=0; y<(h*16); y++) {
                r = g = b = (byte)y;
                set(x, ybase+y, 0xff000000 | ((r<<16)&0xff0000) | ((g<<8)&0xff00) | (b&0xff));
            }
        }
    }

    void resetBW() {   //Paints the Color Reset Button
        setColor(0x000000);
        setRoi(92, ybase+300, 9, 7);
        fill();
        drawRect(88, ybase+297, 9, 7);
        setColor(0xffffff);
        setRoi(89, ybase+298, 7, 5);
        fill();
    }

    void flipper() {   //Paints the Flipper Button
        int xa = 90; 
        int ya = ybase+272; 
        setColor(0x000000);
        drawLine(xa, ya, xa+9, ya+9);//Main Body
        drawLine(xa+1, ya, xa+9, ya+8);
        drawLine(xa, ya+1, xa+8, ya+9);
        drawLine(xa, ya, xa, ya+5);//Upper Arrow
        drawLine(xa+1, ya+1, xa+1, ya+6);
        drawLine(xa, ya, xa+5, ya);
        drawLine(xa+1, ya+1, xa+6, ya+1);
        drawLine(xa+9, ya+9, xa+9, ya+4);//Lower Arrow
        drawLine(xa+8, ya+8, xa+8, ya+3);
        drawLine(xa+9, ya+9, xa+4, ya+9);
        drawLine(xa+8, ya+8, xa+3, ya+8);
    }
    
} 

class ColorCanvas extends Canvas implements MouseListener, MouseMotionListener {
	private static Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
	private static Cursor crosshairCursor = new Cursor(Cursor.CROSSHAIR_CURSOR);
	int ybase = ColorPicker.ybase;
	Rectangle flipperRect = new Rectangle(86, ybase+268, 18, 18);
	Rectangle resetRect = new Rectangle(84, ybase+293, 21, 18);
	Rectangle foreground1Rect = new Rectangle(9, ybase+266, 45, 10);
	Rectangle foreground2Rect = new Rectangle(9, ybase+276, 23, 25);
	Rectangle background1Rect = new Rectangle(33, ybase+302, 45, 10);
	Rectangle background2Rect = new Rectangle(56, ybase+277, 23, 25);
	int width, height;
	Vector colors;
	boolean background;
	long mouseDownTime;
	ColorGenerator ip;
	ColorPicker cp;
	double scale;
	String status = "";
			
	public ColorCanvas(int width, int height, ColorPicker cp, ColorGenerator ip, double scale) {
		this.width=width; this.height=height;
		this.ip = ip;
		this.cp = cp;
		addMouseListener(this);
 		addMouseMotionListener(this);
        addKeyListener(IJ.getInstance());
		setSize(width, height);
		this.scale = scale;
	}
	
	public Dimension getPreferredSize() {
		return new Dimension(width, height);
	}
	
	public void update(Graphics g) {
		paint(g);
	}
	
	public void paint(Graphics g) {
		g.drawImage(ip.createImage(), 0, 0, (int)(ip.getWidth()*scale), (int)(ip.getHeight()*scale), null);
	}

	public void mousePressed(MouseEvent e) {
		//IJ.log("mousePressed "+e);
		ip.setLineWidth(1);
		if (Toolbar.getToolId()==Toolbar.DROPPER)
			IJ.setTool(Toolbar.RECTANGLE );
		int x = (int)(e.getX()/scale);
		int y = (int)(e.getY()/scale);
		long difference = System.currentTimeMillis()-mouseDownTime;
		boolean doubleClick = (difference<=250);
		mouseDownTime = System.currentTimeMillis();
		if (flipperRect.contains(x, y)) {
			Color c = Toolbar.getBackgroundColor();
			Toolbar.setBackgroundColor(Toolbar.getForegroundColor());
			Toolbar.setForegroundColor(c);
			Recorder.setForegroundColor(Toolbar.getForegroundColor());
			Recorder.setBackgroundColor(Toolbar.getBackgroundColor());
		} else if (resetRect.contains(x,y)) {
			Toolbar.setForegroundColor(Color.white);
			Toolbar.setBackgroundColor(Color.black);
			Recorder.setForegroundColor(Color.white);
			Recorder.setBackgroundColor(Color.black);
		} else if ((background1Rect.contains(x,y)) || (background2Rect.contains(x,y))) {
			background = true;
			if (doubleClick) editColor();
			//ip.refreshForeground(background);
			//ip.refreshBackground(background);
		} else if ((foreground1Rect.contains(x,y)) || (foreground2Rect.contains(x,y))) {
			background = false;
			if (doubleClick) editColor();
			//ip.refreshBackground(background);
			//ip.refreshForeground(background);
		} else {
			if (doubleClick)
				editColor();
			else {
				setDrawingColor(x, y, background);
				showStatus(" ", Toolbar.getForegroundColor().getRGB());
			} 
		}
		Color color;
		if (background) {
			ip.refreshForeground(background);
			ip.refreshBackground(background);
			color= Toolbar.getBackgroundColor();
		} else {
			ip.refreshBackground(background);
			ip.refreshForeground(background);
			color= Toolbar.getForegroundColor();
		}
		cp.colorField.setText(Colors.colorToString(color));
		showStatus(" ", color.getRGB());
		repaint();
	}

	public void mouseMoved(MouseEvent e) {
		int x = (int)(e.getX()/scale);
		int y = (int)(e.getY()/scale);
		if (flipperRect.contains(x, y))
			showStatus("Click to flip foreground and background colors", 0);
		else if (resetRect.contains(x,y))
			showStatus("Click to reset foreground to white, background to black", 0);
		else if (!background && (background1Rect.contains(x,y) || background2Rect.contains(x,y)))
			showStatus("Click to switch to background selection mode ", 0);
		else if (background && (foreground1Rect.contains(x,y) || foreground2Rect.contains(x,y)))
			showStatus("Click to switch to foreground selection mode", 0);
		else
			showStatus("", ip.getPixel(x, y));
	}

	String pad(int n) {
		String str = ""+n;
		while (str.length()<3)
		str = "0" + str;
		return str;
	}	

	void setDrawingColor(int x, int y, boolean setBackground) {
		int p = ip.getPixel(x, y);
		int r = (p&0xff0000)>>16;
		int g = (p&0xff00)>>8;
		int b = p&0xff;
		Color c = new Color(r, g, b);
		if (setBackground) {
			Toolbar.setBackgroundColor(c);
			if (Recorder.record)
				Recorder.setBackgroundColor(c);
		} else {
			Toolbar.setForegroundColor(c);
			if (Recorder.record)
				Recorder.setForegroundColor(c);
		}
	}

	void editColor() {
		Color c  = background?Toolbar.getBackgroundColor():Toolbar.getForegroundColor();
		ColorChooser cc = new ColorChooser((background?"Background":"Foreground")+" Color", c, false);
		c = cc.getColor();
		if (background)
			Toolbar.setBackgroundColor(c);
		else
			Toolbar.setForegroundColor(c);
	}
	
	public void refreshColors() {
		ip.refreshBackground(false);
		ip.refreshForeground(false);
		repaint();
	}
	
	private void showStatus(String msg, int rgb) {
		if (msg.length()>1)
			IJ.showStatus(msg);
		else {
			int r = (rgb&0xff0000)>>16;
			int g = (rgb&0xff00)>>8;
			int b = rgb&0xff;
			String hex = Colors.colorToString(new Color(r,g,b));
			IJ.showStatus("red="+pad(r)+", green="+pad(g)+", blue="+pad(b)+" ("+hex+") "+msg);
		}
	}
	
	public void mouseExited(MouseEvent e) {
		IJ.showStatus("");
		setCursor(defaultCursor);
	}

	public void mouseEntered(MouseEvent e) {
		setCursor(crosshairCursor);
	}

	public void mouseReleased(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}
	public void mouseDragged(MouseEvent e) {}
	
}

