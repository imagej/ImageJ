package ij;

import java.awt.*;
import java.util.*;
import java.awt.event.*;
import java.io.*;
import java.net.URL;
import java.awt.image.*;
import ij.gui.*;
import ij.process.*;
import ij.io.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.text.*;

/**
This frame is the main ImageJ class.
<p>
ImageJ is open-source. You are free to do anything you want
with this source as long as I get credit for my work and you
offer your changes to me so I can possibly add them to the
"official" version.

@author Wayne Rasband <wayne@codon.nih.gov>
*/
public class ImageJ extends Frame implements ActionListener, 
	MouseListener, KeyListener, WindowListener, ItemListener {

	public static final String VERSION = "1.24t";

	private static final String IJ_X="ij.x",IJ_Y="ij.y",IJ_WIDTH="ij.width",IJ_HEIGHT="ij.height";
	
	private Toolbar toolbar;
	private Panel statusBar;
	private ProgressBar progressBar;
	private Label statusLine;
	private TextPanel textPanel;
	private boolean firstTime = true;
	private java.applet.Applet applet; // null if not running as an applet
	private Vector classes = new Vector();
	private static PluginClassLoader classLoader;
	private boolean notVerified = true;
	private static boolean wasMaximized;

	/** Creates a new ImageJ frame. */
	public ImageJ() {
		this(null);
	}
	
	/** Creates a new ImageJ frame running as an applet
		if the 'applet' argument is not null. */
	public ImageJ(java.applet.Applet applet) {
		super("ImageJ");
		this.applet = applet;
		String err1 = Prefs.load(this, applet);
		Menus m = new Menus(this, applet);
		String err2 = m.addMenuBar();
		m.installPopupMenu(this);
		notVerified = true;		

		// Tool bar
		Panel panel = new Panel();
		panel.setLayout(new GridLayout(2, 1));
		toolbar = new Toolbar();
		toolbar.addKeyListener(this);
		panel.add(toolbar);

		// Status bar
		statusBar = new Panel();
		statusBar.setLayout(new BorderLayout());
		statusBar.setForeground(Color.black);
		statusBar.setBackground(Color.lightGray);
		statusLine = new Label();
		statusLine.addKeyListener(this);
		statusLine.addMouseListener(this);
		statusBar.add("Center", statusLine);
		progressBar = new ProgressBar(90, 16);
		progressBar.addKeyListener(this);
		progressBar.addMouseListener(this);
		statusBar.add("East", progressBar);
		panel.add(statusBar);
		add("North", panel);

		// Text panel
		textPanel = new TextPanel();
		textPanel.setTitle("results.txt");
		textPanel.setBackground(Color.white);
		textPanel.setFont(new Font("Monospaced", Font.PLAIN, 12));
		//Dimension tbSize = toolbar.getPreferredSize();
		//textPanel.setSize(tbSize.width+10, 250);
		textPanel.addKeyListener(this);
		add("Center", textPanel);
		IJ.init(this, applet, textPanel);
		IJ.write("ImageJ "+VERSION);
		if (err1!=null)
			IJ.write("<<"+err1+">>");
		if (err2!=null)
			IJ.write("<<"+err2+">>");

		IJ.showStatus("  " + Menus.nPlugins + " plugin commands installed");
 		addKeyListener(this);
 		addWindowListener(this);
 		
		setResizable(true);
		//pack();
		Point loc = getPreferredLocation();
		int ijWidth = Prefs.getInt(IJ_WIDTH,0);
		int ijHeight = Prefs.getInt(IJ_HEIGHT,0);
		if (ijWidth<=100 || ijHeight<=10) {
			Dimension tbSize = toolbar.getPreferredSize();
			ijWidth = tbSize.width+10;
			ijHeight = 230;
		}
		setBounds(loc.x, loc.y, ijWidth, ijHeight);
		setCursor(Cursor.getDefaultCursor()); // work-around for JDK 1.1.8 bug
		setIcon();
		setVisible(true);
		requestFocus();
	}
    
	void setIcon() {
		URL url = this .getClass() .getResource("/microscope.gif"); 
		if (url==null)
			return;
		Image img = null;
		try {img = createImage((ImageProducer)url.getContent());}
		catch(Exception e) {}
		if (img!=null)
			setIconImage(img);
	}
	
	public Point getPreferredLocation() {
		int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width;
		int ijX = Prefs.getInt(IJ_X,-99);
		int ijY = Prefs.getInt(IJ_Y,-99);
		if (ijX!=-99 && ijY!=-99 && ijX<(screenWidth-75))
			return new Point(ijX, ijY);
			
		Dimension tbsize = toolbar.getPreferredSize();
		int windowWidth = tbsize.width+10;
		double percent;
		if (screenWidth > 832)
			percent = 0.8;
		else
			percent = 0.9;
		int windowX = (int)(percent * (screenWidth - windowWidth));
		if (windowX < 10)
			windowX = 10;
		int windowY = 32;
		return new Point(windowX, windowY);
	}
	
	void showStatus(String s) {
        statusLine.setText(s);
	}

	public ProgressBar getProgressBar() {
        return progressBar;
	}

    /** Starts executing a menu command in a separate thread. */
    void doCommand(String name) {
		new Executer(name, WindowManager.getCurrentImage());
    }
        
	void wrongType(int capabilities) {
		String s = "This command requires an image of type:\n \n";
		if ((capabilities&PlugInFilter.DOES_8G)!=0) s +=  "    8-bit grayscale\n";
		if ((capabilities&PlugInFilter.DOES_8C)!=0) s +=  "    8-bit color\n";
		if ((capabilities&PlugInFilter.DOES_16)!=0) s +=  "    16-bit grayscale\n";
		if ((capabilities&PlugInFilter.DOES_32)!=0) s +=  "    32-bit (float) grayscale\n";
		if ((capabilities&PlugInFilter.DOES_RGB)!=0) s += "    RGB color\n";
		IJ.error(s);
	}
	
	public void runFilterPlugIn(Object theFilter, String cmd, String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		int capabilities = ((PlugInFilter)theFilter).setup(arg, imp);
		if ((capabilities&PlugInFilter.DONE)!=0)
			return;
		if ((capabilities&PlugInFilter.NO_IMAGE_REQUIRED)!=0)
			{((PlugInFilter)theFilter).run(null); return;}
		if (imp==null)
			{IJ.noImage(); return;}
		if ((capabilities&PlugInFilter.ROI_REQUIRED)!=0 && imp.getRoi()==null)
			{IJ.error("Selection required"); return;}
		if ((capabilities&PlugInFilter.STACK_REQUIRED)!=0 && imp.getStackSize()==1)
			{IJ.error("Stack required"); return;}
		int type = imp.getType();
		switch (type) {
			case ImagePlus.GRAY8:
				if ((capabilities&PlugInFilter.DOES_8G)==0)
					{wrongType(capabilities); return;}
				break;
			case ImagePlus.COLOR_256:
				if ((capabilities&PlugInFilter.DOES_8C)==0)
					{wrongType(capabilities); return;}
				break;
			case ImagePlus.GRAY16:
				if ((capabilities&PlugInFilter.DOES_16)==0)
					{wrongType(capabilities); return;}
				break;
			case ImagePlus.GRAY32:
				if ((capabilities&PlugInFilter.DOES_32)==0)
					{wrongType(capabilities); return;}
				break;
			case ImagePlus.COLOR_RGB:
				if ((capabilities&PlugInFilter.DOES_RGB)==0)
					{wrongType(capabilities); return;}
				break;
		}
		int slices = imp.getStackSize();
		boolean doesStacks = (capabilities&PlugInFilter.DOES_STACKS)!=0;
		if (!imp.lock())
			return; // exit if image is in use
		imp.startTiming();
		IJ.showStatus(cmd + "...");
		ImageProcessor ip;
		ImageStack stack = null;
		if (slices>1)
			stack = imp.getStack();
		int[] mask = null;
		float[] cTable = imp.getCalibration().getCTable();
		if (slices==1 || !doesStacks) {
			ip = imp.getProcessor();
			mask = imp.getMask();
			if ((capabilities&PlugInFilter.NO_UNDO)!=0)
				Undo.reset();
			else {
				Undo.setup(Undo.FILTER, imp);
				ip.snapshot();
			}
			ip.setMask(mask);
			ip.setCalibrationTable(cTable);
			((PlugInFilter)theFilter).run(ip);
			if ((capabilities&PlugInFilter.SUPPORTS_MASKING)!=0)
				ip.reset(mask);  //restore image outside irregular roi
		} else {
       		Undo.reset(); // can't undo stack operations
			int n = stack.getSize();
			int currentSlice = imp.getCurrentSlice();
			Rectangle r = null;
			Roi roi = imp.getRoi();
			if (roi!=null && roi.getType()<Roi.LINE)
				r = roi.getBoundingRect();
			mask = imp.getMask();
			ip = imp.getProcessor();
			double minThreshold = ip.getMinThreshold();
			double maxThreshold = ip.getMaxThreshold();
			for (int i=1; i<=n; i++) {
				ip = stack.getProcessor(i);
				boolean doMasking = roi!=null && roi.getType()!=Roi.RECTANGLE 
					&& (capabilities&PlugInFilter.SUPPORTS_MASKING)!=0;
				if (doMasking)
					ip.snapshot();
				ip.setRoi(r);
				ip.setMask(mask);
				ip.setCalibrationTable(cTable);
				if (minThreshold!=ImageProcessor.NO_THRESHOLD)
					ip.setThreshold(minThreshold,maxThreshold,ImageProcessor.NO_LUT_UPDATE);
				((PlugInFilter)theFilter).run(ip);
				if (doMasking)
					ip.reset(mask);
				IJ.showProgress((double)i/n);
				System.gc();
				Thread.yield();
			}
			int current = imp.getCurrentSlice();
			imp.setProcessor(null,stack.getProcessor(current));
			if (roi!=null)
				imp.setRoi(roi);
			IJ.showProgress(1.0);
		}
		IJ.showTime(imp, imp.getStartTime(), cmd + ": ");
		if ((capabilities&PlugInFilter.NO_CHANGES)==0) {
			imp.changes = true;
	 		imp.updateAndDraw();
	 	}
		ImageWindow win = imp.getWindow();
		if (win!=null)
			win.running = false;
		imp.unlock();
	}
        
	public Object runUserPlugIn(String commandName, String className, String arg, boolean createNewLoader) {
		if (applet!=null)
			return null;
		String pluginsDir = Menus.getPlugInsPath();
		if (pluginsDir==null)
			return null;
		if (notVerified) {
			// check for duplicate classes in the plugins folder
			IJ.runPlugIn("ij.plugin.ClassChecker", "");
			notVerified = false;
		}
		PluginClassLoader loader;
		if (createNewLoader)
			loader = new PluginClassLoader(pluginsDir);
		else {
			if (classLoader==null)
				classLoader = new PluginClassLoader(pluginsDir);
			loader = classLoader;
		}
		Object thePlugIn = null;
		try { 
			thePlugIn = (loader.loadClass(className)).newInstance(); 
 			if (thePlugIn instanceof PlugIn)
				((PlugIn)thePlugIn).run(arg);
 			else if (thePlugIn instanceof PlugInFilter)
				runFilterPlugIn(thePlugIn, commandName, arg);
		}
		catch (ClassNotFoundException e) {IJ.write("Plugin not found: "+className);}
		catch (InstantiationException e) {IJ.write("Unable to load plugin (ins)");}
		catch (IllegalAccessException e) {IJ.write("Unable to load plugin (acc)");}
		return thePlugIn;
	} 
	
	/** Return the current list of modifier keys. */
	public static String modifiers(int flags) { //?? needs to be moved
		String s = " [ ";
		if (flags == 0) return "";
		if ((flags & Event.SHIFT_MASK) != 0) s += "Shift ";
		if ((flags & Event.CTRL_MASK) != 0) s += "Control ";
		if ((flags & Event.META_MASK) != 0) s += "Meta ";
		if ((flags & Event.ALT_MASK) != 0) s += "Alt ";
		s += "] ";
		return s;
	}

	/** Handle menu events. */
	public void actionPerformed(ActionEvent e) {
		if ((e.getSource() instanceof MenuItem)) {
			MenuItem item = (MenuItem)e.getSource();
			String cmd = e.getActionCommand();
			if (cmd!=null)
				doCommand(cmd);
			if (IJ.debugMode) IJ.write("actionPerformed: "+cmd);
		}
	}

	/** Handles CheckboxMenuItem state changes. */
	public void itemStateChanged(ItemEvent e) {
		MenuItem item = (MenuItem)e.getSource();
		MenuComponent parent = (MenuComponent)item.getParent();
		String cmd = e.getItem().toString();
		if ((Menu)parent==Menus.window)
			WindowManager.activateWindow(cmd, item);
		else
			doCommand(cmd);
	}

	public void mousePressed(MouseEvent e) {
		Undo.reset();
		IJ.showStatus(IJ.freeMemory());
		if (IJ.debugMode)
			IJ.write("Windows: "+WindowManager.getWindowCount());
	}
	
	public void mouseReleased(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}

 	public void keyPressed(KeyEvent e) {
		int keyCode = e.getKeyCode();
		IJ.setKeyDown(keyCode);
		if (keyCode==e.VK_CONTROL || keyCode==e.VK_SHIFT)
			return;
		char keyChar = e.getKeyChar();
		int flags = e.getModifiers();
		if (IJ.debugMode) IJ.write("keyCode=" + keyCode + " (" + KeyEvent.getKeyText(keyCode)
			+ ") keyChar=\"" + keyChar + "\" (" + (int)keyChar + ") "
			+ KeyEvent.getKeyModifiersText(flags));
		boolean shift = (flags & e.SHIFT_MASK) != 0;
		boolean control = (flags & e.CTRL_MASK) != 0;
		String c = "";
		ImagePlus imp = WindowManager.getCurrentImage();
		boolean isStack = (imp!=null) && (imp.getStackSize()>1);
		
		if (imp!=null && !control && ((keyChar>=32 && keyChar<=127) || keyChar=='\b' || keyChar=='\n')) {
			Roi roi = imp.getRoi();
			if (roi instanceof TextRoi) {
				((TextRoi)roi).addChar(keyChar);
				return;
			}
		}
        		
		Hashtable shortcuts = Menus.getShortcuts();
		if (shift)
			c = (String)shortcuts.get(new Integer(keyCode+200));
		else
			c = (String)shortcuts.get(new Integer(keyCode));

		if (c==null)
			switch(keyCode) {
				case KeyEvent.VK_TAB: WindowManager.putBehind(); return;
				case KeyEvent.VK_BACK_SPACE: c="Clear"; break; // delete
				case KeyEvent.VK_EQUALS: case 0xbb: c="Start Animation [=]"; break;
				case KeyEvent.VK_SLASH: case 0xbf: c="Reslice [/]..."; break;
				case KeyEvent.VK_COMMA: case 0xbc: c="Previous Slice [<]"; break;
				case KeyEvent.VK_PERIOD: case 0xbe: c="Next Slice [>]"; break;
				case KeyEvent.VK_LEFT: case KeyEvent.VK_RIGHT: case KeyEvent.VK_UP: case KeyEvent.VK_DOWN: // arrow keys
					Roi roi = null;
					if (imp!=null) roi = imp.getRoi();
					if (roi==null) return;
					if ((flags & KeyEvent.ALT_MASK) != 0)
						roi.nudgeCorner(keyCode);
					else
						roi.nudge(keyCode);
					return;
				case KeyEvent.VK_F1: case KeyEvent.VK_F2: case KeyEvent.VK_F3: case KeyEvent.VK_F4: // function keys
				case KeyEvent.VK_F5: case KeyEvent.VK_F6: case KeyEvent.VK_F7: case KeyEvent.VK_F8:
				case KeyEvent.VK_F9: case KeyEvent.VK_F10: case KeyEvent.VK_F11: case KeyEvent.VK_F12:
					Toolbar.getInstance().selectTool(keyCode);
					if (imp!=null) {
						ImageWindow win = imp.getWindow();
						if (win!=null) {
							ImageCanvas ic = win.getCanvas();
							Point loc = ic.getCursorLoc();
							if (loc.x>0 && loc.y>0)
								ic.setCursor(loc.x, loc.y);
						}
					}
					break;
				case KeyEvent.VK_ESCAPE:
					if (imp!=null)
						imp.getWindow().running = false;
					Macro.abort();
					return;
				case KeyEvent.VK_ENTER: this.toFront(); return;
				default: break;
			}
		if (c!=null && !c.equals(""))
			doCommand(c);
	}

	public void keyReleased(KeyEvent e) {
		IJ.setKeyUp(e.getKeyCode());
	}
		
	public void keyTyped(KeyEvent e) {}

	public void windowClosing(WindowEvent e) {
		doCommand("Quit");
	}

	public void windowActivated(WindowEvent e) {
		if (IJ.isMacintosh())
			this.setMenuBar(Menus.getMenuBar());
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension window = getSize();
		//if (IJ.debugMode) IJ.write("screen: "+screen);
		//if (IJ.debugMode) IJ.write("Window: "+window);
		boolean bigWindow = window.width>=screen.width;
		if (bigWindow) {
			//ImagePlus imp = WindowManager.getCurrentImage();
			//if (imp!=null)
			//	imp.getWindow().toFront();
			if (!wasMaximized) {
				wasMaximized = true;
				IJ.beep();
				IJ.showMessage("Error",
					"The \"ImageJ\" window should not be maximized!\n"
					+"Instead, make it as small as possible and position\n"
					+"it so as to minimize overlap of image windows."
					);
			}
		}
	}
	
	public void windowClosed(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}

	/**Copies text from the ImageJ window to the system clipboard. Returns 0 if the system clipboard
	is not available or no text is selected. Clears the selection if "cut" is true.*/
	public int copyText(boolean cut) {
		boolean isActiveWindow = getFocusOwner()!=null;
		if (!isActiveWindow)
			return 0;
		else {
			int count =  textPanel.copySelection();
			if (cut && count>0) textPanel.clearSelection();
			textPanel.resetSelection();
			return count;
		}
	}
	
	/** Clears text from the ImageJ window. Returns
		false if the ImageJ window is not active. */
	public boolean clearText() {
		boolean isActiveWindow = getFocusOwner()!=null;
		if (!isActiveWindow)
			return false;
		else {
			textPanel.clearSelection();
			return true;
		}
	}
	
	/** Adds the specified class to a Vector to keep it from being
		garbage collected, causing static fields to be reset. */
	public void register(Class c) {
		if (!classes.contains(c))
			classes.addElement(c);
	}

	/** Called by ImageJ when the user selects Quit. */
	void quit() {
		if (applet==null)
			Prefs.savePreferences();
		if (!WindowManager.closeAllWindows())
			return;
		setVisible(false);
		dispose();
		if (applet==null)
			System.exit(0);
	}
	
	/** Called once when ImageJ quits. */
	public void savePreferences(Properties prefs) {
		Point loc = getLocation();
		Dimension size = getSize();
		prefs.put(IJ_X, Integer.toString(loc.x));
		prefs.put(IJ_Y, Integer.toString(loc.y));
		prefs.put(IJ_WIDTH, Integer.toString(size.width));
		prefs.put(IJ_HEIGHT, Integer.toString(size.height));
	}

    public static void main(String args[]) {
		new ImageJ(null);
    }


} //class ImageJ

