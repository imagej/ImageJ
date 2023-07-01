package ij;
import ij.gui.*;
import ij.process.*;
import ij.io.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.plugin.frame.*;
import ij.text.*;
import ij.macro.Interpreter;
import ij.io.Opener;
import ij.util.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.awt.image.*;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
This frame is the main ImageJ class.
<p>
ImageJ is a work of the United States Government. It is in the public domain 
and open source. There is no copyright. You are free to do anything you want
with this source but I like to get credit for my work and I would like you to 
offer your changes to me so I can possibly add them to the "official" version.

<pre>
The following command line options are recognized by ImageJ:

  "file-name"
     Opens a file
     Example 1: blobs.tif
     Example 2: /Users/wayne/images/blobs.tif
     Example 3: e81*.tif

  -macro path [arg]
     Runs a macro or script (JavaScript, BeanShell or Python), passing an
     optional string argument, which the macro or script can be retrieve
     using the getArgument() function. The macro or script is assumed to 
     be in the ImageJ/macros folder if 'path' is not a full directory path.
     Example 1: -macro analyze.ijm
     Example 2: -macro script.js /Users/wayne/images/stack1
     Example 2: -macro script.py '1.2 2.4 3.8'

  -batch path [arg]
    Runs a macro or script (JavaScript, BeanShell or Python) in
    batch (no GUI) mode, passing an optional argument.
    ImageJ exits when the macro finishes.

  -eval "macro code"
     Evaluates macro code
     Example 1: -eval "print('Hello, world');"
     Example 2: -eval "return getVersion();"

  -run command
     Runs an ImageJ menu command
     Example: -run "About ImageJ..."
     
  -ijpath path
     Specifies the path to the directory containing the plugins directory
     Example: -ijpath /Applications/ImageJ

  -port<n>
     Specifies the port ImageJ uses to determine if another instance is running
     Example 1: -port1 (use default port address + 1)
     Example 2: -port2 (use default port address + 2)
     Example 3: -port0 (don't check for another instance)

  -debug
     Runs ImageJ in debug mode
</pre>
@author Wayne Rasband (rasband@gmail.com)
*/
public class ImageJ extends Frame implements ActionListener, 
	MouseListener, KeyListener, WindowListener, ItemListener, Runnable {

	/** Plugins should call IJ.getVersion() or IJ.getFullVersion() to get the version string. */
	public static final String VERSION = "1.54f";
	public static final String BUILD = ""; //34
	public static Color backgroundColor = new Color(237,237,237);
	/** SansSerif, 12-point, plain font. */
	public static final Font SansSerif12 = new Font("SansSerif", Font.PLAIN, 12);
	/** SansSerif, 14-point, plain font. */
	public static final Font SansSerif14 = new Font("SansSerif", Font.PLAIN, 14);
	/** Address of socket where Image accepts commands */
	public static final int DEFAULT_PORT = 57294;
	
	/** Run as normal application. */
	public static final int STANDALONE = 0;
	
	/** Run embedded in another application. */
	public static final int EMBEDDED = 1;
	
	/** Run embedded and invisible in another application. */
	public static final int NO_SHOW = 2;
	
	/** Run as the ImageJ application. */
	public static final int IMAGEJ_APP = 3;

	/** Run ImageJ in debug mode. */
	public static final int DEBUG = 256;

	private static final String IJ_X="ij.x",IJ_Y="ij.y";
	private static int port = DEFAULT_PORT;
	private static String[] arguments;
	
	private Toolbar toolbar;
	private Panel statusBar;
	private ProgressBar progressBar;
	private JLabel statusLine;
	private boolean firstTime = true;
	private java.applet.Applet applet; // null if not running as an applet
	private Vector classes = new Vector();
	private boolean exitWhenQuitting;
	private boolean quitting;
	private boolean quitMacro;
	private long keyPressedTime, actionPerformedTime;
	private String lastKeyCommand;
	private boolean embedded;
	private boolean windowClosed;
	private static String commandName;
	private static boolean batchMode;
		
	boolean hotkey;
	
	/** Creates a new ImageJ frame that runs as an application. */
	public ImageJ() {
		this(null, STANDALONE);
	}
	
	/** Creates a new ImageJ frame that runs as an application in the specified mode. */
	public ImageJ(int mode) {
		this(null, mode);
	}

	/** Creates a new ImageJ frame that runs as an applet. */
	public ImageJ(java.applet.Applet applet) {
		this(applet, STANDALONE);
	}

	/** If 'applet' is not null, creates a new ImageJ frame that runs as an applet.
		If  'mode' is ImageJ.EMBEDDED and 'applet is null, creates an embedded 
		(non-standalone) version of ImageJ. */
	public ImageJ(java.applet.Applet applet, int mode) {
		super("ImageJ");
		if ((mode&DEBUG)!=0)
			IJ.setDebugMode(true);
		mode = mode & 255;
		boolean useExceptionHandler = false;
		if (mode==IMAGEJ_APP) {
			mode = STANDALONE;
			useExceptionHandler = true;
		}
		if (IJ.debugMode) IJ.log("ImageJ starting in debug mode: "+mode);
		embedded = applet==null && (mode==EMBEDDED||mode==NO_SHOW);
		this.applet = applet;
		String err1 = Prefs.load(this, applet);
		setBackground(backgroundColor);
		Menus m = new Menus(this, applet);
		String err2 = m.addMenuBar();
		m.installPopupMenu(this);
		setLayout(new BorderLayout());
		
		// Tool bar
		toolbar = new Toolbar();
		toolbar.addKeyListener(this);
		add("Center", toolbar);

		// Status bar
		statusBar = new Panel();
		statusBar.setLayout(new BorderLayout());
		statusBar.setForeground(Color.black);
		statusBar.setBackground(backgroundColor);
		statusLine = new JLabel();
		double scale = Prefs.getGuiScale();
		statusLine.setFont(new Font("SansSerif", Font.PLAIN, (int)(13*scale)));
		statusLine.addKeyListener(this);
		statusLine.addMouseListener(this);
		statusBar.add("Center", statusLine);
		progressBar = new ProgressBar((int)(ProgressBar.WIDTH*scale), (int)(ProgressBar.HEIGHT*scale));
		progressBar.addKeyListener(this);
		progressBar.addMouseListener(this);
		statusBar.add("East", progressBar);
		add("South", statusBar);

		IJ.init(this, applet);
		addKeyListener(this);
		addWindowListener(this);
		setFocusTraversalKeysEnabled(false);
		m.installStartupMacroSet(); //add custom tools
 		
		Point loc = getPreferredLocation();
		Dimension tbSize = toolbar.getPreferredSize();
		setCursor(Cursor.getDefaultCursor()); // work-around for JDK 1.1.8 bug
		if (mode!=NO_SHOW) {
			if (IJ.isWindows()) try {setIcon();} catch(Exception e) {}
			setResizable(false);
			setAlwaysOnTop(Prefs.alwaysOnTop);
			pack();
			setLocation(loc.x, loc.y);
			setVisible(true);
			Dimension size = getSize();
			if (size!=null) {
				if (IJ.debugMode) IJ.log("size: "+size);
				if (IJ.isWindows() && (size.height>108||IJ.javaVersion()>=10)) {
					// workaround for IJ window layout and FileDialog freeze problems with Windows 10 Creators Update
					IJ.wait(10);
					pack();
					if (IJ.debugMode) IJ.log("pack()");
					if (!Prefs.jFileChooserSettingChanged)
						Prefs.useJFileChooser = true;
				} else if (IJ.isMacOSX()) {
					Rectangle maxBounds = GUI.getMaxWindowBounds(this);
					if (loc.x+size.width>maxBounds.x+maxBounds.width)
						setLocation(loc.x, loc.y);
				}
			}
		}
		if (err1!=null)
			IJ.error(err1);
		if (err2!=null) {
			IJ.error(err2);
			//IJ.runPlugIn("ij.plugin.ClassChecker", "");
		}
		if (IJ.isMacintosh()&&applet==null) {
			try {
				if (IJ.javaVersion()>8) // newer JREs use different drag-drop, about mechanism
					IJ.runPlugIn("ij.plugin.MacAdapter9", "");
				else
					IJ.runPlugIn("ij.plugin.MacAdapter", "");
			} catch(Throwable e) {}
		} 
		if (applet==null)
			IJ.runPlugIn("ij.plugin.DragAndDrop", "");
		if (!getTitle().contains("Fiji") && useExceptionHandler) {
			Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());
			System.setProperty("sun.awt.exception.handler",ExceptionHandler.class.getName());
		}
		String str = m.getMacroCount()==1?" macro":" macros";
		configureProxy();
		if (applet==null)
			loadCursors();
		(new ij.macro.StartupRunner()).run(batchMode); // run RunAtStartup and AutoRun macros
		IJ.showStatus(version()+ m.getPluginCount() + " commands; " + m.getMacroCount() + str);
 	}
 	
 	private void loadCursors() {
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		String path = Prefs.getImageJDir()+"images/crosshair-cursor.gif";
		File f = new File(path);
		if (!f.exists())
			return;
		//Image image = toolkit.getImage(path);
		ImageIcon icon = new ImageIcon(path);
		Image image = icon.getImage();
		if (image==null)
			return;
		int width = icon.getIconWidth();
		int height = icon.getIconHeight();
		Point hotSpot = new Point(width/2, height/2);
		Cursor crosshairCursor = toolkit.createCustomCursor(image, hotSpot, "crosshair-cursor.gif");
		ImageCanvas.setCursor(crosshairCursor, 0);
	}
    	
	void configureProxy() {
		if (Prefs.useSystemProxies) {
			try {
				System.setProperty("java.net.useSystemProxies", "true");
			} catch(Exception e) {}
		} else {
			String server = Prefs.get("proxy.server", null);
			if (server==null||server.equals(""))
				return;
			int port = (int)Prefs.get("proxy.port", 0);
			if (port==0) return;
			Properties props = System.getProperties();
			props.put("proxySet", "true");
			props.put("http.proxyHost", server);
			props.put("http.proxyPort", ""+port);
			props.put("https.proxyHost", server);
			props.put("https.proxyPort", ""+port);
		}
		//new ProxySettings().logProperties();
	}
	
    void setIcon() throws Exception {
		URL url = this.getClass().getResource("/microscope.gif");
		if (url==null) return;
		Image img = createImage((ImageProducer)url.getContent());
		if (img!=null) setIconImage(img);
	}
	
	public Point getPreferredLocation() {
		int ijX = Prefs.getInt(IJ_X,-99);
		int ijY = Prefs.getInt(IJ_Y,-99);
		Rectangle maxBounds = GUI.getMaxWindowBounds();
		//System.out.println("getPreferredLoc1: "+ijX+" "+ijY+" "+maxBounds);
		if (ijX>=maxBounds.x && ijY>=maxBounds.y && ijX<(maxBounds.x+maxBounds.width-75)
		&& ijY<(maxBounds.y+maxBounds.height-75))
			return new Point(ijX, ijY);
		Dimension tbsize = toolbar.getPreferredSize();
		int ijWidth = tbsize.width+10;
		double percent = maxBounds.width>832?0.8:0.9;
		ijX = (int)(percent*(maxBounds.width-ijWidth));
		if (ijX<10) ijX = 10;
		return new Point(ijX, maxBounds.y);
	}
	
	void showStatus(String s) {
        statusLine.setText(s);
	}

	public ProgressBar getProgressBar() {
        return progressBar;
	}

	public Panel getStatusBar() {
        return statusBar;
	}
	
	public static String getStatusBarText() {
		ImageJ ij = IJ.getInstance();
		return ij!=null?ij.statusLine.getText():"";
	}

    /** Starts executing a menu command in a separate thread. */
    void doCommand(String name) {
		new Executer(name, null);
    }
        
	public void runFilterPlugIn(Object theFilter, String cmd, String arg) {
		new PlugInFilterRunner(theFilter, cmd, arg);
	}
        
	public Object runUserPlugIn(String commandName, String className, String arg, boolean createNewLoader) {
		return IJ.runUserPlugIn(commandName, className, arg, createNewLoader);	
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
			Frame frame = WindowManager.getFrontWindow();
			if (frame!=null && (frame instanceof Fitter)) {
				((Fitter)frame).actionPerformed(e);
				return;
			}
			commandName = cmd;
			ImagePlus imp = null;
			if (item.getParent()==Menus.getOpenRecentMenu()) {
				new RecentOpener(cmd); // open image in separate thread
				return;
			} else if (item.getParent()==Menus.getPopupMenu()) {
				Object parent = Menus.getPopupMenu().getParent();
				if (parent instanceof ImageCanvas)
					imp = ((ImageCanvas)parent).getImage();
			}
			int flags = e.getModifiers();
			hotkey = false;
			actionPerformedTime = System.currentTimeMillis();
			long ellapsedTime = actionPerformedTime-keyPressedTime;
			if (cmd!=null && (ellapsedTime>=200L||!cmd.equals(lastKeyCommand))) {
				if ((flags & Event.ALT_MASK)!=0)
					IJ.setKeyDown(KeyEvent.VK_ALT);
				if ((flags & Event.SHIFT_MASK)!=0)
					IJ.setKeyDown(KeyEvent.VK_SHIFT);
				new Executer(cmd, imp);
			}
			lastKeyCommand = null;
			if (IJ.debugMode) IJ.log("actionPerformed: time="+ellapsedTime+", "+e);
		}
	}

	/** Handles CheckboxMenuItem state changes. */
	public void itemStateChanged(ItemEvent e) {
		MenuItem item = (MenuItem)e.getSource();
		MenuComponent parent = (MenuComponent)item.getParent();
		String cmd = e.getItem().toString();
		if ("Autorun Examples".equals(cmd)) // Examples>Autorun Examples
			Prefs.autoRunExamples = e.getStateChange()==1;
		else if ((Menu)parent==Menus.window)
			WindowManager.activateWindow(cmd, item);
		else
			doCommand(cmd);
	}

	public void mousePressed(MouseEvent e) {
		Undo.reset();
		if (!Prefs.noClickToGC)
			System.gc();
		IJ.showStatus(version()+IJ.freeMemory());
		if (IJ.debugMode)
			IJ.log("Windows: "+WindowManager.getWindowCount());
	}
	
	public String getInfo() {
		return version()+System.getProperty("os.name")+" "+System.getProperty("os.version")+"; "+IJ.freeMemory();
	}

	private String version() {
		return "ImageJ "+VERSION+BUILD + "; "+"Java "+System.getProperty("java.version")+(IJ.is64Bit()?" [64-bit]; ":" [32-bit]; ");
	}
	
	public void mouseReleased(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}

 	public void keyPressed(KeyEvent e) {
		if (e.isConsumed())
			return;
		int keyCode = e.getKeyCode();
		IJ.setKeyDown(keyCode);
		hotkey = false;
		if (keyCode==KeyEvent.VK_CONTROL || keyCode==KeyEvent.VK_SHIFT)
			return;
		char keyChar = e.getKeyChar();
		int flags = e.getModifiers();
		if (IJ.debugMode) IJ.log("keyPressed: code=" + keyCode + " (" + KeyEvent.getKeyText(keyCode)
			+ "), char=\"" + keyChar + "\" (" + (int)keyChar + "), flags="
			+ KeyEvent.getKeyModifiersText(flags));
		boolean shift = (flags & KeyEvent.SHIFT_MASK) != 0;
		boolean control = (flags & KeyEvent.CTRL_MASK) != 0;
		boolean alt = (flags & KeyEvent.ALT_MASK) != 0;
		boolean meta = (flags & KeyEvent.META_MASK) != 0;
		if (keyCode==KeyEvent.VK_H && meta && IJ.isMacOSX())
			return; // Allow macOS to run ImageJ>Hide ImageJ command
		String cmd = null;
		ImagePlus imp = WindowManager.getCurrentImage();
		boolean isStack = (imp!=null) && (imp.getStackSize()>1);
		
		if (imp!=null && !meta && ((keyChar>=32 && keyChar<=255) || keyChar=='\b' || keyChar=='\n')) {
			Roi roi = imp.getRoi();
			if (roi!=null && roi instanceof TextRoi) {
				if (imp.getOverlay()!=null && (control || alt || meta)
				&& (keyCode==KeyEvent.VK_BACK_SPACE || keyCode==KeyEvent.VK_DELETE)) {
					if (deleteOverlayRoi(imp))
							return;
				}
				if ((flags & KeyEvent.META_MASK)!=0 && IJ.isMacOSX())
					return;
				if (alt) {
					switch (keyChar) {
						case 'u': case 'm': keyChar = IJ.micronSymbol; break;
						case 'A': keyChar = IJ.angstromSymbol; break;
						default:
					}
				}
				((TextRoi)roi).addChar(keyChar);
				return;
			}
		}
        		
		// Handle one character macro shortcuts
		if (!control && !meta) {
			Hashtable macroShortcuts = Menus.getMacroShortcuts();
			if (macroShortcuts.size()>0) {
				if (shift)
					cmd = (String)macroShortcuts.get(Integer.valueOf(keyCode+200));
				else
					cmd = (String)macroShortcuts.get(Integer.valueOf(keyCode));
				if (cmd!=null) {
					commandName = cmd;
					MacroInstaller.runMacroShortcut(cmd);
					return;
				}
			}
		}

		if (keyCode==KeyEvent.VK_SEPARATOR)
			keyCode = KeyEvent.VK_DECIMAL;
		boolean functionKey = keyCode>=KeyEvent.VK_F1 && keyCode<=KeyEvent.VK_F12;
		boolean numPad = keyCode==KeyEvent.VK_DIVIDE || keyCode==KeyEvent.VK_MULTIPLY
			|| keyCode==KeyEvent.VK_DECIMAL
			|| (keyCode>=KeyEvent.VK_NUMPAD0 && keyCode<=KeyEvent.VK_NUMPAD9);			
		if ((!Prefs.requireControlKey||control||meta||functionKey||numPad) && keyChar!='+') {
			Hashtable shortcuts = Menus.getShortcuts();
			if (shift && !functionKey)
				cmd = (String)shortcuts.get(Integer.valueOf(keyCode+200));
			else
				cmd = (String)shortcuts.get(Integer.valueOf(keyCode));
		}
		
		if (cmd==null) {
			switch (keyChar) {
				case '<': case ',': if (isStack) cmd="Previous Slice [<]"; break;
				case '>': case '.': case ';': if (isStack) cmd="Next Slice [>]"; break;
				case '+': case '=': cmd="In [+]"; break;
				case '-': cmd="Out [-]"; break;
				case '/': cmd="Reslice [/]..."; break;
				default:
			}
		}

		if (cmd==null) {
			switch (keyCode) {
				case KeyEvent.VK_TAB: WindowManager.putBehind(); return;				
				case KeyEvent.VK_BACK_SPACE: case KeyEvent.VK_DELETE:
					if (!(shift||control||alt||meta)) {
						if (deleteOverlayRoi(imp))
							return;
						if (imp!=null&&imp.getOverlay()!=null&&imp==GelAnalyzer.getGelImage())
							return;
						cmd="Clear";
						hotkey=true;
					}
					break;
				//case KeyEvent.VK_BACK_SLASH: cmd=IJ.altKeyDown()?"Animation Options...":"Start Animation"; break;
				case KeyEvent.VK_EQUALS: cmd="In [+]"; break;
				case KeyEvent.VK_MINUS: cmd="Out [-]"; break;
				case KeyEvent.VK_SLASH: case 0xbf: cmd="Reslice [/]..."; break;
				case KeyEvent.VK_COMMA: case 0xbc: if (isStack) cmd="Previous Slice [<]"; break;
				case KeyEvent.VK_PERIOD: case 0xbe: if (isStack) cmd="Next Slice [>]"; break;
				case KeyEvent.VK_LEFT: case KeyEvent.VK_RIGHT: case KeyEvent.VK_UP: case KeyEvent.VK_DOWN: // arrow keys
					if (imp==null) return;
					Roi roi = imp.getRoi();
					if (shift&&imp==Orthogonal_Views.getImage())
						return;
					if (IJ.isMacOSX() && IJ.isJava18()) {
						RoiManager rm = RoiManager.getInstance();
						boolean rmActive = rm!=null && rm==WindowManager.getActiveWindow();
						if (rmActive && (keyCode==KeyEvent.VK_DOWN||keyCode==KeyEvent.VK_UP))
						  rm.repaint();
					}
					boolean stackKey = imp.getStackSize()>1 && (roi==null||shift);
					boolean zoomKey = roi==null || shift || control;
					if (stackKey && keyCode==KeyEvent.VK_RIGHT)
							cmd="Next Slice [>]";
					else if (stackKey && keyCode==KeyEvent.VK_LEFT)
							cmd="Previous Slice [<]";
					else if (zoomKey && keyCode==KeyEvent.VK_DOWN && !ignoreArrowKeys(imp) && Toolbar.getToolId()<Toolbar.SPARE6)
							cmd="Out [-]";
					else if (zoomKey && keyCode==KeyEvent.VK_UP && !ignoreArrowKeys(imp) && Toolbar.getToolId()<Toolbar.SPARE6)
							cmd="In [+]";
					else if (roi!=null) {
						if ((flags & KeyEvent.ALT_MASK)!=0 || (flags & KeyEvent.CTRL_MASK)!=0)
							roi.nudgeCorner(keyCode);
						else
							roi.nudge(keyCode);
						return;
					}
					break;
				case KeyEvent.VK_ESCAPE:
					abortPluginOrMacro(imp);
					return;
				case KeyEvent.VK_ENTER: WindowManager.toFront(this); return;
				default: break;
			}
		}
		
		if (cmd!=null && !cmd.equals("")) {
			commandName = cmd;
			if (!control && !meta && (cmd.equals("Fill")||cmd.equals("Draw")))
				hotkey = true;
			if (cmd.charAt(0)==MacroInstaller.commandPrefix)
				MacroInstaller.runMacroShortcut(cmd);
			else {
				doCommand(cmd);
				keyPressedTime = System.currentTimeMillis();
				lastKeyCommand = cmd;
			}
		}
	}
	
	private boolean deleteOverlayRoi(ImagePlus imp) {
		if (imp==null)
			return false;
		Overlay overlay = null;
		ImageCanvas ic = imp.getCanvas();
		if (ic!=null)
			overlay = ic.getShowAllList();
		if (overlay==null)
			overlay = imp.getOverlay();
		if (overlay==null)
			return false;
		Roi roi = imp.getRoi();
		for (int i=0; i<overlay.size(); i++) {
			Roi roi2 = overlay.get(i);
			if (roi2==roi) {
				overlay.remove(i);
				imp.deleteRoi();
				ic = imp.getCanvas();
				if (ic!=null)
					ic.roiManagerSelect(roi, true);
				return true;
			}
		}
		return false;
	}
	
	private boolean ignoreArrowKeys(ImagePlus imp) {
		Frame frame = WindowManager.getFrontWindow();
		String title = frame!=null?frame.getTitle():null;
		if (title!=null && title.equals("ROI Manager"))
			return true;
		// Control Panel?
		if (frame!=null && frame instanceof javax.swing.JFrame)
			return true;
		// Channels dialog?
		Window window = WindowManager.getActiveWindow();
		title = window!=null&&(window instanceof Dialog)?((Dialog)window).getTitle():null;
		if (title!=null && title.equals("Channels"))
			return true;
		ImageWindow win = imp.getWindow();
		// LOCI Data Browser window?
		if (imp.getStackSize()>1 && win!=null && win.getClass().getName().startsWith("loci"))
			return true;
		return false;
	}
	
	public void keyTyped(KeyEvent e) {
		char keyChar = e.getKeyChar();
		int flags = e.getModifiers();
		//if (IJ.debugMode) IJ.log("keyTyped: char=\"" + keyChar + "\" (" + (int)keyChar 
		//	+ "), flags= "+Integer.toHexString(flags)+ " ("+KeyEvent.getKeyModifiersText(flags)+")");
		if (keyChar=='\\' || keyChar==171 || keyChar==223) {
			if (((flags&Event.ALT_MASK)!=0))
				doCommand("Animation Options...");
			else
				doCommand("Start Animation [\\]");
		}
	}

	public void keyReleased(KeyEvent e) {
		IJ.setKeyUp(e.getKeyCode());
	}
			
	/** called when escape pressed */
	void abortPluginOrMacro(ImagePlus imp) {
		if (imp!=null) {
			ImageWindow win = imp.getWindow();
			if (win!=null) {
				Roi roi = imp.getRoi();
				if (roi!=null && roi.getState()!=Roi.NORMAL) {
					roi.abortModification(imp);
					return;
				} else {
					win.running = false;
					win.running2 = false;
				}
			}
		}
		Macro.abort();
		Interpreter.abort();
		if (Interpreter.getInstance()!=null)
			IJ.beep();
	}

	public void windowClosing(WindowEvent e) {
		if (Executer.getListenerCount()>0)
			doCommand("Quit");
		else {
			quit();
			windowClosed = true;
		}
	}

	public void windowActivated(WindowEvent e) {
		if (IJ.isMacintosh() && !quitting) {
			IJ.wait(10); // may be needed for Java 1.4 on OS X
			MenuBar mb = Menus.getMenuBar();
			if (mb!=null && mb!=getMenuBar()) {
				setMenuBar(mb);
				Menus.setMenuBarCount++;
				//if (IJ.debugMode) IJ.log("setMenuBar: "+Menus.setMenuBarCount);
			}
		}
	}
	
	public void windowClosed(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}
	
	/** Adds the specified class to a Vector to keep it from being
		garbage collected, causing static fields to be reset. */
	public void register(Class c) {
		if (!classes.contains(c))
			classes.addElement(c);
	}

	/** Called by ImageJ when the user selects Quit. */
	public void quit() {
		quitMacro = IJ.macroRunning();
		Thread thread = new Thread(this, "Quit");
		thread.setPriority(Thread.NORM_PRIORITY);
		thread.start();
		IJ.wait(10);
	}
	
	/** Returns true if ImageJ is exiting. */
	public boolean quitting() {
		return quitting;
	}
	
	/** Returns true if ImageJ is quitting as a result of a run("Quit") macro call. */
	public boolean quittingViaMacro() {
		return quitting && quitMacro;
	}

	/** Called once when ImageJ quits. */
	public void savePreferences(Properties prefs) {
		Point loc = getLocation();
		prefs.put(IJ_X, Integer.toString(loc.x));
		prefs.put(IJ_Y, Integer.toString(loc.y));
	}

	public static void main(String args[]) {
		boolean noGUI = false;
		int mode = IMAGEJ_APP;
		arguments = args;
		int nArgs = args!=null?args.length:0;
		boolean commandLine = false;
		for (int i=0; i<nArgs; i++) {
			String arg = args[i];
			if (arg==null) continue;
			if (arg.startsWith("-batch")) {
				noGUI = true;
				batchMode = true;
			} else if (arg.startsWith("-macro") || arg.endsWith(".ijm") || arg.endsWith(".txt"))
				batchMode = true;
			else if (arg.startsWith("-debug"))
				IJ.setDebugMode(true);
			else if (arg.startsWith("-ijpath") && i+1<nArgs) {
				if (IJ.debugMode) IJ.log("-ijpath: "+args[i+1]);
				Prefs.setHomeDir(args[i+1]);
				commandLine = true;
				args[i+1] = null;
			} else if (arg.startsWith("-port")) {
				int delta = (int)Tools.parseDouble(arg.substring(5, arg.length()), 0.0);
				commandLine = true;
				if (delta==0)
					mode = EMBEDDED;
				else if (delta>0 && DEFAULT_PORT+delta<65536)
					port = DEFAULT_PORT+delta;
			} 
		}
  		// If existing ImageJ instance, pass arguments to it and quit.
  		boolean passArgs = (mode==IMAGEJ_APP||mode==STANDALONE) && !noGUI;
		if (IJ.isMacOSX() && !commandLine)
			passArgs = false;
		if (passArgs && isRunning(args)) 
  			return;
 		ImageJ ij = IJ.getInstance();    	
		if (!noGUI && (ij==null || (ij!=null && !ij.isShowing()))) {
			ij = new ImageJ(null, mode);
			ij.exitWhenQuitting = true;
		} else if (batchMode && noGUI)
			Prefs.load(null, null);
		int macros = 0;
		for (int i=0; i<nArgs; i++) {
			String arg = args[i];
			if (arg==null) continue;
			if (arg.startsWith("-")) {
				if ((arg.startsWith("-macro") || arg.startsWith("-batch")) && i+1<nArgs) {
					String arg2 = i+2<nArgs?args[i+2]:null;
					Prefs.commandLineMacro = true;
					if (noGUI && args[i+1].endsWith(".js"))
						Interpreter.batchMode = true;
					IJ.runMacroFile(args[i+1], arg2);
					break;
				} else if (arg.startsWith("-eval") && i+1<nArgs) {
					String rtn = IJ.runMacro(args[i+1]);
					if (rtn!=null)
						System.out.print(rtn);
					args[i+1] = null;
				} else if (arg.startsWith("-run") && i+1<nArgs) {
					IJ.run(args[i+1]);
					args[i+1] = null;
				}
			} else if (macros==0 && (arg.endsWith(".ijm") || arg.endsWith(".txt"))) {
				IJ.runMacroFile(arg);
				macros++;
			} else if (arg.length()>0 && arg.indexOf("ij.ImageJ")==-1) {
				File file = new File(arg);
				IJ.open(file.getAbsolutePath());
			}
		}
		if (IJ.debugMode && IJ.getInstance()==null && !GraphicsEnvironment.isHeadless())
			new JavaProperties().run("");
		if (noGUI) System.exit(0);
	}
		
	// Is there another instance of ImageJ? If so, send it the arguments and quit.
	static boolean isRunning(String args[]) {
		return OtherInstance.sendArguments(args);
	}

	/** Returns the port that ImageJ checks on startup to see if another instance is running.
	* @see ij.OtherInstance
	*/
	public static int getPort() {
		return port;
	}
	
	/** Returns the command line arguments passed to ImageJ. */
	public static String[] getArgs() {
		return arguments;
	}

	/** ImageJ calls System.exit() when qutting when 'exitWhenQuitting' is true.*/
	public void exitWhenQuitting(boolean ewq) {
		exitWhenQuitting = ewq;
	}
	
	/** Quit using a separate thread, hopefully avoiding thread deadlocks. */
	public void run() {
		quitting = true;
		boolean changes = false;
		int[] wList = WindowManager.getIDList();
		if (wList!=null) {
			for (int i=0; i<wList.length; i++) {
				ImagePlus imp = WindowManager.getImage(wList[i]);
				if (imp!=null && imp.changes==true) {
					changes = true;
					break;
				}
			}
		}
		Frame[] frames = WindowManager.getNonImageWindows();
		if (frames!=null) {
			for (int i=0; i<frames.length; i++) {
				if (frames[i]!=null && (frames[i] instanceof Editor)) {
					if (((Editor)frames[i]).fileChanged()) {
						changes = true;
						break;
					}
				}
			}
		}
		if (windowClosed && !changes && Menus.window.getItemCount()>Menus.WINDOW_MENU_ITEMS && !(IJ.macroRunning()&&WindowManager.getImageCount()==0)) {
			GenericDialog gd = new GenericDialog("ImageJ", this);
			gd.addMessage("Are you sure you want to quit ImageJ?");
			gd.showDialog();
			quitting = !gd.wasCanceled();
			windowClosed = false;
		}
		if (!quitting)
			return;
		if (!WindowManager.closeAllWindows()) {
			quitting = false;
			return;
		}
		if (applet==null) {
			saveWindowLocations();
			Prefs.set(ImageWindow.LOC_KEY,null); // don't save image window location
			Prefs.savePreferences();
		}
		IJ.cleanup();
		dispose();
		if (exitWhenQuitting)
			System.exit(0);
	}
	
	void saveWindowLocations() {
		Window win = WindowManager.getWindow("B&C");
		if (win!=null)
			Prefs.saveLocation(ContrastAdjuster.LOC_KEY, win.getLocation());
		win = WindowManager.getWindow("Threshold");
		if (win!=null)
			Prefs.saveLocation(ThresholdAdjuster.LOC_KEY, win.getLocation());
		win = WindowManager.getWindow("Results");
		if (win!=null) {
			Prefs.saveLocation(TextWindow.LOC_KEY, win.getLocation());
			Dimension d = win.getSize();
			Prefs.set(TextWindow.WIDTH_KEY, d.width);
			Prefs.set(TextWindow.HEIGHT_KEY, d.height);
		}
		win = WindowManager.getWindow("Log");
		if (win!=null) {
			Prefs.saveLocation(TextWindow.LOG_LOC_KEY, win.getLocation());
			Dimension d = win.getSize();
			Prefs.set(TextWindow.LOG_WIDTH_KEY, d.width);
			Prefs.set(TextWindow.LOG_HEIGHT_KEY, d.height);
		}
		win = WindowManager.getWindow("ROI Manager");
		if (win!=null)
			Prefs.saveLocation(RoiManager.LOC_KEY, win.getLocation());
	}
	
	public static String getCommandName() {
		return commandName!=null?commandName:"null";
	}
	
	public static void setCommandName(String name) {
		commandName = name;
	}
	
	public void resize() {
		double scale = Prefs.getGuiScale();
		toolbar.init();
		statusLine.setFont(new Font("SansSerif", Font.PLAIN, (int)(13*scale)));
		progressBar.init((int)(ProgressBar.WIDTH*scale), (int)(ProgressBar.HEIGHT*scale));
		pack();
	}
	
  /** Handles exceptions on the EDT. */
  public static class ExceptionHandler implements Thread.UncaughtExceptionHandler {

    // for EDT exceptions
    public void handle(Throwable thrown) {
      handleException(Thread.currentThread().getName(), thrown);
    }

    // for other uncaught exceptions
    public void uncaughtException(Thread thread, Throwable thrown) {
      handleException(thread.getName(), thrown);
    }

    protected void handleException(String tname, Throwable e) {
    	if (Macro.MACRO_CANCELED.equals(e.getMessage()))
			return;
		CharArrayWriter caw = new CharArrayWriter();
		PrintWriter pw = new PrintWriter(caw);
		e.printStackTrace(pw);
		String s = caw.toString();
		if (s!=null && s.contains("ij.")) {
			if (IJ.getInstance()!=null)
				s = IJ.getInstance().getInfo()+"\n"+s;
			IJ.log(s);
		}
    }
    
  } // inner class ExceptionHandler

}
