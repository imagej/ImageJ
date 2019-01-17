package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.awt.datatransfer.*;																																																																																													
import ij.*;
import ij.gui.*;
import ij.util.Tools;
import ij.text.*;
import ij.macro.*;
import ij.plugin.MacroInstaller;
import ij.plugin.Commands;
import ij.plugin.Macro_Runner;
import ij.plugin.JavaScriptEvaluator;
import ij.io.SaveDialog;

/** This is a simple TextArea based editor for editing and compiling plugins. */
public class Editor extends PlugInFrame implements ActionListener, ItemListener,
	TextListener, KeyListener, ClipboardOwner, MacroConstants, Runnable, Debugger {
	
	/** ImportPackage statements added in front of scripts. Contains no 
	newlines so that lines numbers in error messages are not changed. */
	public static String JavaScriptIncludes =
		"importPackage(Packages.ij);"+
		"importPackage(Packages.ij.gui);"+
		"importPackage(Packages.ij.process);"+
		"importPackage(Packages.ij.measure);"+
		"importPackage(Packages.ij.util);"+
		"importPackage(Packages.ij.plugin);"+
		"importPackage(Packages.ij.io);"+
		"importPackage(Packages.ij.plugin.filter);"+
		"importPackage(Packages.ij.plugin.frame);"+
		"importPackage(java.lang);"+
		"importPackage(java.awt);"+
		"importPackage(java.awt.image);"+
		"importPackage(java.awt.geom);"+
		"importPackage(java.util);"+
		"importPackage(java.io);"+
		"function print(s) {IJ.log(s);};";
		
	private static String JS_EXAMPLES =
		"img = IJ.openImage(\"http://wsr.imagej.net/images/blobs.gif\")\n"
 		+"img = IJ.createImage(\"Untitled\", \"16-bit ramp\", 500, 500, 1)\n" 		
 		+"img.show()\n"
 		+"ip = img.getProcessor()\n"
 		+"ip.getStats()\n"
 		+"IJ.setAutoThreshold(img, \"IsoData\")\n"
 		+"IJ.run(img, \"Analyze Particles...\", \"show=Overlay display clear\")\n"
		+"ip.invert()\n"
 		+"ip.blurGaussian(5)\n"	 
 		+"ip.get(10,10)\n"
 		+"ip.set(10,10,222)\n"
 		+"(To run, move cursor to end of a line and press 'enter'.\n"
 		+"Visible images are automatically updated.)\n";

	public static final int MAX_SIZE=28000, XINC=10, YINC=18;
	public static final int MONOSPACED=1, MENU_BAR=2;
	public static final int MACROS_MENU_ITEMS = 14;
	public static final String INTERACTIVE_NAME = "Interactive Interpreter";
	static final String FONT_SIZE = "editor.font.size";
	static final String FONT_MONO= "editor.font.mono";
	static final String CASE_SENSITIVE= "editor.case-sensitive";
	static final String DEFAULT_DIR= "editor.dir";
	static final String INSERT_SPACES= "editor.spaces";
	static final String TAB_INC= "editor.tab-inc";
	public static Editor currentMacroEditor;
	private TextArea ta;
	private String path;
	protected boolean changes;
	private static String searchString = "";
	private static boolean caseSensitive = Prefs.get(CASE_SENSITIVE, true);
	private static int lineNumber = 1;
	private static int xoffset, yoffset;
	private static int nWindows;
	private Menu fileMenu, editMenu;
	private Properties p = new Properties();
	private int[] macroStarts;
	private String[] macroNames;
	private MenuBar mb;
	private Menu macrosMenu;
	private int nMacros;
	private Program pgm;
	private int eventCount;
	private String shortcutsInUse;
	private int inUseCount;
	private MacroInstaller installer;
	private static String defaultDir = Prefs.get(DEFAULT_DIR, null);;
	private boolean dontShowWindow;
	private int[] sizes = {9, 10, 11, 12, 13, 14, 16, 18, 20, 24, 36, 48, 60, 72};
	private int fontSize = (int)Prefs.get(FONT_SIZE, 6); // defaults to 16-point
	private CheckboxMenuItem monospaced;
	private static boolean wholeWords;
	private boolean isMacroWindow;
	private int debugStart, debugEnd;
	private static TextWindow debugWindow;
	private boolean step;
	private int previousLine;
	private static Editor instance;
	private int runToLine;
	private String downloadUrl;
	private boolean downloading;
	private FunctionFinder functionFinder;
	private ArrayList undoBuffer = new ArrayList();
	private boolean performingUndo;
	private boolean checkForCurlyQuotes;
	private static int tabInc = (int)Prefs.get(TAB_INC, 3);
	private static boolean insertSpaces = Prefs.get(INSERT_SPACES, false);
	private CheckboxMenuItem insertSpacesItem;
	private boolean interactiveMode;
	private Interpreter interpreter;
	private JavaScriptEvaluator evaluator;
	private int messageCount;
	
	public Editor() {
		this(24, 80, 0, MENU_BAR);
	}

	public Editor(int rows, int columns, int fontSize, int options) {
		super("Editor");
		WindowManager.addWindow(this);
		addMenuBar(options);	
		ta = new TextArea(rows, columns);
		ta.addTextListener(this);
		ta.addKeyListener(this);
		if (IJ.isLinux()) ta.setBackground(Color.white);
 		addKeyListener(IJ.getInstance());  // ImageJ handles keyboard shortcuts
		add(ta);
		pack();
		if (fontSize<0)
			fontSize = 0;
		if (fontSize>=sizes.length)
			fontSize = sizes.length-1;
		setFont();
		positionWindow();
		if (!IJ.isJava18() && !IJ.isLinux())
			insertSpaces = false;
	}
	
	void addMenuBar(int options) {
		mb = new MenuBar();
		if (Menus.getFontSize()!=0) ;
			mb.setFont(Menus.getFont());
		Menu m = new Menu("File");
		m.add(new MenuItem("New...", new MenuShortcut(KeyEvent.VK_N, true)));
		m.add(new MenuItem("Open...", new MenuShortcut(KeyEvent.VK_O)));
		m.add(new MenuItem("Save", new MenuShortcut(KeyEvent.VK_S)));
		m.add(new MenuItem("Save As..."));
		m.add(new MenuItem("Revert"));
		m.add(new MenuItem("Print..."));
		m.addActionListener(this);
		fileMenu = m;
		mb.add(m);
		
		m = new Menu("Edit");
		MenuItem item = null;
		if (IJ.isWindows())
			item = new MenuItem("Undo  Ctrl+Z");
		else
			item = new MenuItem("Undo",new MenuShortcut(KeyEvent.VK_Z));		
		m.add(item);
		m.addSeparator();		
		if (IJ.isWindows())
			item = new MenuItem("Cut  Ctrl+X");
		else
			item = new MenuItem("Cut",new MenuShortcut(KeyEvent.VK_X));
		m.add(item);
		if (IJ.isWindows())
			item = new MenuItem("Copy  Ctrl+C");
		else
			item = new MenuItem("Copy", new MenuShortcut(KeyEvent.VK_C));
		m.add(item);
		if (IJ.isWindows())
			item = new MenuItem("Paste  Ctrl+V");
		else
			item = new MenuItem("Paste",new MenuShortcut(KeyEvent.VK_V));
		m.add(item);
		m.addSeparator();
		m.add(new MenuItem("Find...", new MenuShortcut(KeyEvent.VK_F)));
		m.add(new MenuItem("Find Next", new MenuShortcut(KeyEvent.VK_G)));
		m.add(new MenuItem("Go to Line...", new MenuShortcut(KeyEvent.VK_L)));
		m.addSeparator();
		m.add(new MenuItem("Select All", new MenuShortcut(KeyEvent.VK_A)));
		m.add(new MenuItem("Balance", new MenuShortcut(KeyEvent.VK_B,false)));
		m.add(new MenuItem("Detab..."));
		insertSpacesItem = new CheckboxMenuItem("Tab Key Inserts Spaces");
		insertSpacesItem.addItemListener(this);
		insertSpacesItem.setState(insertSpaces);
		m.add(insertSpacesItem);
		m.add(new MenuItem("Zap Gremlins"));
		m.add(new MenuItem("Copy to Image Info"));
		m.addActionListener(this);
		mb.add(m);
		editMenu = m;
		if ((options&MENU_BAR)!=0)
			setMenuBar(mb);
		
		m = new Menu("Font");
		m.add(new MenuItem("Make Text Smaller"));
		m.add(new MenuItem("Make Text Larger"));
		//m.add(new MenuItem("Make Text Smaller", new MenuShortcut(KeyEvent.VK_MINUS)));
		//m.add(new MenuItem("Make Text Larger", new MenuShortcut(KeyEvent.VK_EQUALS)));
		m.addSeparator();
		monospaced = new CheckboxMenuItem("Monospaced Font", Prefs.get(FONT_MONO, false));
		if ((options&MONOSPACED)!=0) monospaced.setState(true);
		monospaced.addItemListener(this);
		m.add(monospaced);
		m.add(new MenuItem("Save Settings"));
		m.addActionListener(this);
		mb.add(m);
		
		m = Menus.getExamplesMenu(this);
		mb.add(m);
	}			
			
	public void positionWindow() {
		Dimension screen = IJ.getScreenSize();
		Dimension window = getSize();
		if (window.width==0)
			return;
		int left = screen.width/2-window.width/2;
		int top = screen.height/(IJ.isWindows()?6:5);
		if (IJ.isMacOSX())
			top = (screen.height-window.height)/4;
		if (top<0) top = 0;
		if (nWindows<=0 || xoffset>8*XINC)
			{xoffset=0; yoffset=0;}
		setLocation(left+xoffset, top+yoffset);
		xoffset+=XINC; yoffset+=YINC;
		nWindows++;
	}

	void setWindowTitle(String title) {
		Menus.updateWindowMenuItem(getTitle(), title);
		setTitle(title);
	}
	
	public void create(String name, String text) {
		ta.append(text);
		if (IJ.isMacOSX()) IJ.wait(25); // needed to get setCaretPosition() on OS X
		ta.setCaretPosition(0);
		setWindowTitle(name);
		boolean macroExtension = name.endsWith(".txt") || name.endsWith(".ijm");
		if (macroExtension || name.endsWith(".js") || name.endsWith(".bsh") || name.endsWith(".py") || name.indexOf(".")==-1) {
			macrosMenu = new Menu("Macros");			
			macrosMenu.add(new MenuItem("Run Macro", new MenuShortcut(KeyEvent.VK_R)));
			macrosMenu.add(new MenuItem("Evaluate Line", new MenuShortcut(KeyEvent.VK_Y)));
			macrosMenu.add(new MenuItem("Abort Macro"));
			macrosMenu.add(new MenuItem("Install Macros", new MenuShortcut(KeyEvent.VK_I)));
			macrosMenu.add(new MenuItem("Macro Functions...", new MenuShortcut(KeyEvent.VK_M, true)));
			macrosMenu.add(new MenuItem("Function Finder...", new MenuShortcut(KeyEvent.VK_F, true)));
			macrosMenu.add(new MenuItem("Enter Interactive Mode", new MenuShortcut(KeyEvent.VK_M)));
			macrosMenu.addSeparator();
			macrosMenu.add(new MenuItem("Evaluate Macro"));
			macrosMenu.add(new MenuItem("Evaluate JavaScript", new MenuShortcut(KeyEvent.VK_J, false)));
			macrosMenu.add(new MenuItem("Evaluate BeanShell", new MenuShortcut(KeyEvent.VK_B, true)));
			macrosMenu.add(new MenuItem("Evaluate Python", new MenuShortcut(KeyEvent.VK_P, false)));
			macrosMenu.add(new MenuItem("Show Log Window", new MenuShortcut(KeyEvent.VK_L, true)));
			macrosMenu.addSeparator();
			// MACROS_MENU_ITEMS must be updated if items are added to this menu
			macrosMenu.addActionListener(this);
			mb.add(macrosMenu);
			if (!(name.endsWith(".js")||name.endsWith(".bsh")||name.endsWith(".py"))) {
				Menu debugMenu = new Menu("Debug");			
				debugMenu.add(new MenuItem("Debug Macro", new MenuShortcut(KeyEvent.VK_D)));
				debugMenu.add(new MenuItem("Step", new MenuShortcut(KeyEvent.VK_E)));
				debugMenu.add(new MenuItem("Trace", new MenuShortcut(KeyEvent.VK_T)));
				debugMenu.add(new MenuItem("Fast Trace", new MenuShortcut(KeyEvent.VK_T,true)));
				debugMenu.add(new MenuItem("Run"));
				debugMenu.add(new MenuItem("Run to Insertion Point", new MenuShortcut(KeyEvent.VK_E, true)));
				debugMenu.add(new MenuItem("Abort"));
				debugMenu.addActionListener(this);
				mb.add(debugMenu);
			}
		} else {
			fileMenu.addSeparator();
			fileMenu.add(new MenuItem("Compile and Run", new MenuShortcut(KeyEvent.VK_R)));
		}
		if (IJ.getInstance()!=null && !dontShowWindow)
			show();
		if (dontShowWindow) {
			dispose();
			dontShowWindow = false;
		}
		if (name.equals(INTERACTIVE_NAME)) {
			enterInteractiveMode();
			String txt = ta.getText();
			ta.setCaretPosition(txt.length());
		}
		WindowManager.setWindow(this);
		checkForCurlyQuotes = true;
		changes = false;
	}

	public void createMacro(String name, String text) {
		create(name, text);
	}
	
	void installMacros(String text, boolean installInPluginsMenu) {
		String functions = Interpreter.getAdditionalFunctions();
		if (functions!=null && text!=null) {
			if (!(text.endsWith("\n") || functions.startsWith("\n")))
				text = text + "\n" + functions;
			else
				text = text + functions;
		}
		installer = new MacroInstaller();
		installer.setFileName(getTitle());
		int nShortcutsOrTools = installer.install(text, macrosMenu);
		if (installInPluginsMenu || nShortcutsOrTools>0)
			installer.install(null);
		dontShowWindow = installer.isAutoRunAndHide();
		currentMacroEditor = this;
	}
		
	/** Opens a file and replaces the text (if any) by the contents of the file. */
	public void open(String dir, String name) {
		path = dir+name;
		File file = new File(path);
		if (!file.exists()) {
			IJ.error("File not found: "+path);
			return;
		}
		try {
			StringBuffer sb = new StringBuffer(5000);
			BufferedReader r = new BufferedReader(new FileReader(file));
			while (true) {
				String s=r.readLine();
				if (s==null)
					break;
				else
					sb.append(s+"\n");
			}
			r.close();
			if (ta!=null && ta.getText().length()>0) {
				ta.setText("");  //delete previous contents (if any)
				eventCount = 0;
			}
			create(name, new String(sb));
			changes = false;
		}
		catch (Exception e) {
			IJ.handleException(e);
			return;
		}
	}

	public String getText() {
		if (ta==null)
			return "";
		else
			return ta.getText();
	}

	public TextArea getTextArea() {
		return ta;
	}

	public void display(String title, String text) {
		ta.selectAll();
		ta.replaceRange(text, ta.getSelectionStart(), ta.getSelectionEnd());
		ta.setCaretPosition(0);
		setWindowTitle(title);
		changes = false;
		if (IJ.getInstance()!=null)
			show();
		WindowManager.setWindow(this);
	}

	void save() {
		if (path==null) {
			saveAs(); 
			return;
		}
		File f = new File(path);
		if (f.exists() && !f.canWrite()) {
			IJ.showMessage("Editor", "Unable to save because file is write-protected. \n \n" + path);
			return;
		}
		String text = ta.getText();
		char[] chars = new char[text.length()];
		text.getChars(0, text.length(), chars, 0);
		try {
			BufferedReader br = new BufferedReader(new CharArrayReader(chars));
			BufferedWriter bw = new BufferedWriter(new FileWriter(path));
			while (true) {
				String s = br.readLine();
				if (s==null) break;
				bw.write(s, 0, s.length());
				bw.newLine();
			}
			bw.close();
			IJ.showStatus(text.length()+" chars saved to " + path);
			changes = false;
		} catch
			(IOException e) {}
	}

	void compileAndRun() {
		if (path==null)
			saveAs();
		if (path!=null) {
			save();
			String text = ta.getText();
			if (text.contains("implements PlugInFilter") && text.contains("IJ.run("))
				IJ.log("<<Plugins that call IJ.run() should probably implement PlugIn, not PlugInFilter.>>");
			IJ.runPlugIn("ij.plugin.Compiler", path);
		}
	}
	
	final void runMacro(boolean debug) {
		if (path!=null)
			Macro_Runner.setFilePath(path);
		if (getTitle().endsWith(".js"))
			{evaluateJavaScript(); return;}
		else if (getTitle().endsWith(".bsh"))
			{evaluateScript(".bsh"); return;}
		else if (getTitle().endsWith(".py"))
			{evaluateScript(".py"); return;}
		int start = ta.getSelectionStart();
		int end = ta.getSelectionEnd();
		String text;
		if (start==end)
			text = ta.getText();
		else
			text = ta.getSelectedText();
		Interpreter.abort();  // abort any currently running macro
		if (checkForCurlyQuotes && text.contains("\u201D")) {
			// replace curly quotes with standard quotes
 			text = text.replaceAll("\u201C", "\""); 
			text = text.replaceAll("\u201D", "\"");
			if (start==end)
				ta.setText(text);
			else {
				String text2 = ta.getText();
 				text2 = text2.replaceAll("\u201C", "\""); 
				text2 = text2.replaceAll("\u201D", "\"");
				ta.setText(text2);
			}
			changes = true;
			checkForCurlyQuotes = false;
		}
		currentMacroEditor = this;
		new MacroRunner(text, debug?this:null);
	}
	
	void evaluateMacro() {
		String title = getTitle();
		if (title.endsWith(".js")||title.endsWith(".bsh")||title.endsWith(".py"))
			setWindowTitle(title.substring(0,title.length()-3)+".ijm");
		runMacro(false);
	}

	void evaluateJavaScript() {
		if (!getTitle().endsWith(".js"))
			setWindowTitle(SaveDialog.setExtension(getTitle(), ".js"));
		int start = ta.getSelectionStart();
		int end = ta.getSelectionEnd();
		String text;
		if (start==end)
			text = ta.getText();
		else
			text = ta.getSelectedText();
		if (text.equals(""))
			return;
		boolean strictMode = false;
		if (IJ.isJava18()) {
			// text.matches("^( |\t)*(\"use strict\"|'use strict')");
			String text40 = text.substring(0,Math.min(40,text.length()));
			strictMode =  text40.contains("'use strict'") || text40.contains("\"use strict\"");
		}
		text = getJSPrefix("") + text;
		if (IJ.isJava18()) {
			text = "load(\"nashorn:mozilla_compat.js\");" + text;
			if (strictMode)
				text = "'use strict';" + text;
		}
		if (!(IJ.isMacOSX()&&!IJ.is64Bit())) {
			// Use JavaScript engine built into Java 6 and later.
			IJ.runPlugIn("ij.plugin.JavaScriptEvaluator", text);
		} else {
			Object js = IJ.runPlugIn("JavaScript", text);
			if (js==null)
				download("/download/tools/JavaScript.jar");
		}
	}

	public void evaluateScript(String ext) {
		if (downloading) {
			IJ.beep();
			IJ.showStatus("Download in progress");
			return;
		}
		if (ext.endsWith(".js")) {
			evaluateJavaScript();
			return;
		}
		if (!getTitle().endsWith(ext))
			setWindowTitle(SaveDialog.setExtension(getTitle(), ext));
		int start = ta.getSelectionStart();
		int end = ta.getSelectionEnd();
		String text;
		if (start==end)
			text = ta.getText();
		else
			text = ta.getSelectedText();
		if (text.equals("")) return;
		String plugin, url;
		if (ext.equals(".bsh")) {
			plugin = "bsh";
			url = "/plugins/bsh/BeanShell.jar";
		} else {
			// download Jython from http://imagej.nih.gov/ij/plugins/jython/
			plugin = "Jython";
			url = "/plugins/jython/Jython.jar";
		}
		Object obj = IJ.runPlugIn(plugin, text);
		if (obj==null)
			download(url);
	}
	
	private void download(String url) {
		this.downloadUrl = url;
		Thread thread = new Thread(this, "Downloader");
		thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
		thread.start();
	}

	void evaluateLine() {
		int start = ta.getSelectionStart();
		int end = ta.getSelectionEnd();
		if (end>start)
			{runMacro(false); return;}
		String text = ta.getText();
		while (start>0) {
			start--;
			if (text.charAt(start)=='\n')
				{start++; break;}
		}
		while (end<text.length()-1) {
			end++;
			if (text.charAt(end)=='\n')
				break;
		}
		ta.setSelectionStart(start);
		ta.setSelectionEnd(end);
		runMacro(false);
	}

	void print () {
		PrintJob pjob = Toolkit.getDefaultToolkit().getPrintJob(this, "Cool Stuff", p);
		if (pjob != null) {
			Graphics pg = pjob.getGraphics( );
			if (pg != null) {
				String s = ta.getText();
				printString(pjob, pg, s);
				pg.dispose( );	
			}
			pjob.end( );
		}
	}

	void printString (PrintJob pjob, Graphics pg, String s) {
		int pageNum = 1;
		int linesForThisPage = 0;
		int linesForThisJob = 0;
		int topMargin = 30;
		int leftMargin = 30;
		int bottomMargin = 30;
		
		if (!(pg instanceof PrintGraphics))
			throw new IllegalArgumentException ("Graphics contextt not PrintGraphics");
		if (IJ.isMacintosh()) {
			topMargin = 0;
			leftMargin = 0;
			bottomMargin = 0;
		}
		StringReader sr = new StringReader (s);
		LineNumberReader lnr = new LineNumberReader (sr);
		String nextLine;
		int pageHeight = pjob.getPageDimension().height - bottomMargin;
		Font helv = new Font(getFontName(), Font.PLAIN, 10);
		pg.setFont (helv);
		FontMetrics fm = pg.getFontMetrics(helv);
		int fontHeight = fm.getHeight();
		int fontDescent = fm.getDescent();
		int curHeight = topMargin;
		try {
			do {
				nextLine = lnr.readLine();
			   if (nextLine != null) {		   
					nextLine = detabLine(nextLine);
					if ((curHeight + fontHeight) > pageHeight) {
						// New Page
						pageNum++;
						linesForThisPage = 0;
						pg.dispose();
						pg = pjob.getGraphics();
						if (pg != null)
							pg.setFont (helv);
						curHeight = topMargin;
					}
					curHeight += fontHeight;
					if (pg != null) {
						pg.drawString (nextLine, leftMargin, curHeight - fontDescent);
						linesForThisPage++;
						linesForThisJob++;
					} 
				}
			} while (nextLine != null);
		} catch (EOFException eof) {
	   // Fine, ignore
		} catch (Throwable t) { // Anything else
			IJ.handleException(t);
		}
	}
	
	String detabLine(String s) {
		if (s.indexOf('\t')<0)
			return s;
		int tabSize = 4;
		StringBuffer sb = new StringBuffer((int)(s.length()*1.25));
		char c;
		for (int i=0; i<s.length(); i++) {
			c = s.charAt(i);
			if (c=='\t') {
				  for (int j=0; j<tabSize; j++)
					  sb.append(' '); 
		} else
			sb.append(c);
		 }
		return sb.toString();
	}	   

	void undo() {
		if (IJ.isWindows()) {
			IJ.showMessage("Editor", "Press Ctrl-Z to undo");
			return;
		}
		if (IJ.debugMode) IJ.log("Undo1: "+undoBuffer.size());
		int position = ta.getCaretPosition();
		if (undoBuffer.size()>1) {
			undoBuffer.remove(undoBuffer.size()-1);
			String text = (String)undoBuffer.get(undoBuffer.size()-1);
			performingUndo = true;
			ta.setText(text);
			if (position<=text.length())
				ta.setCaretPosition(position-offset(position));
			if (IJ.debugMode) IJ.log("Undo2: "+undoBuffer.size()+" "+text);
		}
	}
	
	boolean copy() { 
		String s; 
		s = ta.getSelectedText();
		Clipboard clip = getToolkit().getSystemClipboard();
		if (clip!=null) {
			StringSelection cont = new StringSelection(s);
			clip.setContents(cont,this);
			return true;
		} else
			return false;
	}
	  
	void cut() {
		if (copy()) {
			int start = ta.getSelectionStart();
			int end = ta.getSelectionEnd();
			ta.replaceRange("", start-offset(start), end-offset(end-2>=start?end-2:start));
			if (IJ.isMacOSX())
				ta.setCaretPosition(start);
		}	
	}

	void paste() {
		String s;
		s = ta.getSelectedText();
		Clipboard clipboard = getToolkit( ). getSystemClipboard(); 
		Transferable clipData = clipboard.getContents(s);
		try {
			s = (String)(clipData.getTransferData(DataFlavor.stringFlavor));
		} catch  (Exception e)  {
			s  = e.toString( );
		}
		int start = ta.getSelectionStart( );
		int end = ta.getSelectionEnd( );
		ta.replaceRange(s, start-offset(start), end-offset(end-2>=start?end-2:start));
		if (IJ.isMacOSX())
			ta.setCaretPosition(start+s.length());
		checkForCurlyQuotes = true;
	}
	
	// workaround for TextArea.getCaretPosition() bug on Windows
	private int offset(int pos) {
		if (!IJ.isWindows())
			return 0;
		String text = ta.getText();
		int rcount = 0;
		for (int i=0; i<=pos; i++) {
			if (text.charAt(i)=='\r')
				rcount++;
		}
		if (IJ.debugMode) IJ.log("offset: "+pos+" "+rcount);
		return pos-rcount>=0?rcount:0;
	}

	void copyToInfo() { 
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) {
			IJ.noImage();
			return;
		}
		int start = ta.getSelectionStart();
		int end = ta.getSelectionEnd();
		String text;
		if (start==end)
			text = ta.getText();
		else
			text = ta.getSelectedText();
		imp.setProperty("Info", text);
	}
	
	public void actionPerformed(ActionEvent e) {
		String what = e.getActionCommand();
		int flags = e.getModifiers();
		boolean altKeyDown = (flags & Event.ALT_MASK)!=0;		
		if ("Save".equals(what))
			save();
		else if ("Compile and Run".equals(what))
				compileAndRun();
		else if ("Run Macro".equals(what)) {
			if (altKeyDown) {
				enableDebugging();
				runMacro(true);
			} else
				runMacro(false);
		} else if ("Debug Macro".equals(what)) {
				enableDebugging();
				runMacro(true);
		} else if ("Step".equals(what))
			setDebugMode(STEP);
		else if ("Trace".equals(what))
			setDebugMode(TRACE);
		else if ("Fast Trace".equals(what))
			setDebugMode(FAST_TRACE);
		else if ("Run".equals(what))
			setDebugMode(RUN_TO_COMPLETION);
		else if ("Run to Insertion Point".equals(what))
			runToInsertionPoint();
		else if ("Abort".equals(what) || "Abort Macro".equals(what)) {
			Interpreter.abort();
			IJ.beep();		
		} else if ("Evaluate Line".equals(what))
			evaluateLine();
		else if ("Install Macros".equals(what))
			installMacros(ta.getText(), true);
		else if ("Macro Functions...".equals(what))
			showMacroFunctions();
		else if ("Function Finder...".equals(what))
			functionFinder = new FunctionFinder(this);
		else if ("Evaluate Macro".equals(what))
			evaluateMacro();
		else if ("Evaluate JavaScript".equals(what))
			evaluateJavaScript();
		else if ("Evaluate BeanShell".equals(what))
			evaluateScript(".bsh");
		else if ("Evaluate Python".equals(what))
			evaluateScript(".py");
		else if ("Show Log Window".equals(what))
			showLogWindow();
		else if ("Revert".equals(what))
			revert();
		else if ("Print...".equals(what))
			print();
		else if (what.startsWith("Undo"))
		   undo();
		else if (what.startsWith("Paste"))
			paste();
		else if (what.startsWith("Copy"))
			copy();
		else if (what.startsWith("Cut"))
		   cut();
		else if ("Save As...".equals(what))
			saveAs();
		else if ("Select All".equals(what))
			selectAll();
		else if ("Find...".equals(what))
			find(null);
		else if ("Find Next".equals(what))
			find(searchString);
		else if ("Go to Line...".equals(what))
			gotoLine();
		else if ("Balance".equals(what))
			balance();
		else if ("Detab...".equals(what))
			detab();
		else if ("Zap Gremlins".equals(what))
			zapGremlins();
		else if ("Make Text Larger".equals(what))
			changeFontSize(true);
		else if ("Make Text Smaller".equals(what))
			changeFontSize(false);
		else if ("Save Settings".equals(what))
			saveSettings();
		else if ("New...".equals(what))
			IJ.run("Text Window");
		else if ("Open...".equals(what))
			IJ.open();
		else if (what.equals("Copy to Image Info"))
			copyToInfo();
		else if (what.equals("Enter Interactive Mode"))
			enterInteractiveMode();
		else if (what.endsWith(".ijm") || what.endsWith(".java") || what.endsWith(".js") || what.endsWith(".bsh") || what.endsWith(".py"))
			openExample(what);
		else {
			if (altKeyDown) {
				enableDebugging();
				installer.runMacro(what, this);
			} else
				installer.runMacro(what, null);
		}
	}
	
	/** Opens an example from the Help/Examples menu
		and runs if "Autorun Exampes" is checked. */
	public static boolean openExample(String name) {
		boolean isJava = name.endsWith(".java");
		boolean isJavaScript = name.endsWith(".js");
		boolean isBeanShell = name.endsWith(".bsh");
		boolean isPython = name.endsWith(".py");
		boolean isMacro = name.endsWith(".ijm");
		if (!(isMacro||isJava||isJavaScript||isBeanShell||isPython))
			return false;
		boolean run = !isJava && !name.contains("_Tool") && Prefs.autoRunExamples;
		int rows = 24;
		int columns = 70;
		int options = MENU_BAR;
		String text = null;
		Editor ed = new Editor(rows, columns, 0, options);
		String dir = "Macro/";
		if (isJava)
			dir = "Java/";
		else if (isJavaScript)
			dir = "JavaScript/";
		else if (isBeanShell)
			dir = "BeanShell/";
		else if (isPython)
			dir = "Python/";
		String url = "http://wsr.imagej.net/download/Examples/"+dir+name;
		text = IJ.openUrlAsString(url);
		if (text.startsWith("<Error: ")) {
			IJ.error("Open Example", text);
			return true;
		}
		ed.create(name, text);
		if (run)
			ed.runMacro(false);
		return true;
	}
	
	protected void showMacroFunctions() {
		String url= "/developer/macro/functions.html";
		String selText = ta.getSelectedText().replace("\n", " ");
		String[] selectedWords = Tools.split(selText, "/,(,[\"\'&+");
		if (selectedWords.length==1 && selectedWords[0].length()>0) 
			url += "#" +selectedWords[0];//append selection as hash tag
		IJ.runPlugIn("ij.plugin.BrowserLauncher", IJ.URL+url);
	}

	final void runToInsertionPoint() {
		Interpreter interp = Interpreter.getInstance();
		if (interp==null)
			IJ.beep();
		else {
			runToLine = getCurrentLine();
			setDebugMode(RUN_TO_CARET);
		}
	}
		
	final int getCurrentLine() {
		int pos = ta.getCaretPosition();
		int currentLine = 0;
		String text = ta.getText();
		if (IJ.isWindows())
			text = text.replaceAll("\r\n", "\n");
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		int count=0;
		int start=0, end=0;
		int len = chars.length;
		for (int i=0; i<len; i++) {
			if (chars[i]=='\n') {
				count++;
				start = end;
				end = i;
				if (pos>=start && pos<end) {
					currentLine = count;
					break;
				}
			}
		}
		if (currentLine==0 && pos>end)
			currentLine = count;
		return currentLine;
	}

	final void enableDebugging() {
			step = true;
			int start = ta.getSelectionStart();
			int end = ta.getSelectionEnd();
			if (start==debugStart && end==debugEnd)
				ta.select(start, start);
	}
	
	final void setDebugMode(int mode) {
		step = true;
		Interpreter interp = Interpreter.getInstance();
		if (interp!=null) {
			if (interp.getDebugger()==null)
				fixLineEndings();
			interp.setDebugger(this);
			interp.setDebugMode(mode);
		}
	}

	public void textValueChanged(TextEvent e) {
		String text = ta.getText();
		//if (undo2==null || text.length()!=undo2.length()+1 || text.charAt(text.length()-1)=='\n')
		int length = 0;
		if (!performingUndo) {
			for (int i=0; i<undoBuffer.size(); i++)
				length += ((String)undoBuffer.get(i)).length();
			if (length<2000000)
				undoBuffer.add(text);
			else {
				for (int i=1; i<undoBuffer.size(); i++)
					undoBuffer.set(i-1, undoBuffer.get(i));
				undoBuffer.set(undoBuffer.size()-1, text);
			}
		}
		performingUndo = false;
		if (isMacroWindow) return;
		// first few textValueChanged events may be bogus
		eventCount++;
		if (eventCount>2 || !IJ.isMacOSX() && eventCount>1)
			changes = true;
		if (IJ.isMacOSX()) // screen update bug work around
			ta.setCaretPosition(ta.getCaretPosition());
	}
	
	public void keyPressed(KeyEvent e) { 
	} 
	
	public void keyReleased(KeyEvent e) {
		int pos = ta.getCaretPosition();
		if (insertSpaces && pos>0 && e.getKeyCode()==KeyEvent.VK_TAB) {
			String spaces = " ";
			for (int i=1; i<tabInc; i++)
				spaces += " ";
			ta.replaceRange(spaces, pos-1, pos);
		}
		if (interactiveMode && e.getKeyChar()=='\n')
			runMacro(e);
	}
	
	private void runMacro(KeyEvent e) {
		boolean isScript = getTitle().endsWith(".js");
		String text = ta.getText();
		int pos2 = ta.getCaretPosition()-2;
		if (pos2<0) pos2=0;
		int pos1 = 0;
		for (int i=pos2; i>=0; i--) {
			if (i==0 || text.charAt(i)=='\n') {
				pos1 = i;
				break;
			}
		}
		if (isScript) {
			if (evaluator==null) {
				interpreter = null;
				evaluator = new JavaScriptEvaluator();
			}
		} else {
			if (interpreter==null) {
				evaluator = null;
				interpreter = new Interpreter();
			}
		}
		String code = text.substring(pos1,pos2+1);
		if (code.length()==0 || code.equals("\n"))
			return;		
		else if (code.length()<=6 && code.contains("help")) {
			ta.appendText("  Type a statement (e.g., \"run('Invert')\") to run it.\n");			
			ta.appendText("  Enter an expression (e.g., \"x/2\" or \"log(2)\") to evaluate it.\n");			
			ta.appendText("  Move cursor to end of line and press 'enter' to repeat.\n");			
			ta.appendText("  \"quit\" - exit interactive mode\n");			
			ta.appendText("  "+(IJ.isMacOSX()?"cmd":"ctrl")+"+M - enter interactive mode\n");
			if (isScript) {	
				ta.appendText("  \"macro\" - switch language to macro\n");
				ta.appendText("  \"examples\" - show JavaScript examples\n");	
			} else {
				ta.appendText("  "+(IJ.isMacOSX()?"cmd":"ctrl")+"+shift+F - open Function Finder\n");	
				ta.appendText("  \"js\" - switch language to JavaScript\n");	
			}
		} else if (isScript && code.length()==9 && code.contains("examples")) {
			ta.appendText(JS_EXAMPLES);					
		} else if (code.length()<=3 && code.contains("js")) {
			interactiveMode = false;
			interpreter = null;
			evaluator = null;
			changeExtension(".js");
			enterInteractiveMode();
		} else if (code.length()<=6 && code.contains("macro")) {
			interactiveMode = false;
			interpreter = null;
			evaluator = null;
			changeExtension(".txt");
			enterInteractiveMode();
		} else if (code.length()<=6 && code.contains("quit")) {
			interactiveMode = false;
			interpreter = null;
			evaluator = null;
			ta.appendText("[Exiting interactive mode.]\n");
		} else if (isScript) {
			boolean updateImage = code.contains("ip.");
			code = "load(\"nashorn:mozilla_compat.js\");"+JavaScriptIncludes+code;
			String rtn = evaluator.eval(code);
			if (rtn!=null && rtn.length()>0) {
				int index = rtn.indexOf("at line number ");
				if (index>-1)
					rtn = rtn.substring(0,index);
				insertText(rtn);	
			}
			if (updateImage && (rtn==null||rtn.length()==0)) {
				ImagePlus imp = WindowManager.getCurrentImage();
				if (imp!=null)
					imp.updateAndDraw();
			}
		} else if (!code.startsWith("[Macro ")) {
			String rtn = interpreter.eval(code);
			if (rtn!=null)
				insertText(rtn);
		}
	}
	
	private void changeExtension(String ext) {
		String title = getTitle();
		int index = title.indexOf(".");
		if (index>-1)
			title = title.substring(0,index);
		setTitle(title+ext);
	}
	
	private void enterInteractiveMode() {
		if (interactiveMode)
			return;
		String title = getTitle();
		if (ta!=null && ta.getText().length()>400 && !(title.startsWith("Untitled")||title.startsWith(INTERACTIVE_NAME))) {
			GenericDialog gd = new GenericDialog("Enter Interactive Mode");
			gd.addMessage("Enter mode that supports interactive\nediting and running of macros and scripts?");
			gd.setOKLabel("Enter");
			gd.showDialog();
			if (gd.wasCanceled())
				return;
		}
		String language = title.endsWith(".js")?"JavaScript ":"Macro ";
		messageCount++;
		String help = messageCount<=2?" Type \"help\" for info.":"";
		ta.appendText("["+language+"interactive mode."+help+"]\n");
		interactiveMode = true;
	}
	
	public void insertText(String text) {
		if (ta==null) return;			
		int start = ta.getSelectionStart( );
		ta.replaceRange("  "+text+"\n", start, start);
	}
		
	public void keyTyped(KeyEvent e) {
	}

	public void itemStateChanged(ItemEvent e) {
		CheckboxMenuItem item = (CheckboxMenuItem)e.getSource();
		String cmd = e.getItem().toString();
		if ("Tab Key Inserts Spaces".equals(cmd)) {
			insertSpaces = e.getStateChange()==1;
			Prefs.set(INSERT_SPACES, insertSpaces);
		} else
			setFont();
	}

	/** Override windowActivated in PlugInFrame to
		prevent Mac menu bar from being installed. */
	public void windowActivated(WindowEvent e) {
			if (IJ.debugMode) IJ.log("Editor.windowActivated");
			WindowManager.setWindow(this);
			instance = this;
	}

	/** Overrides close() in PlugInFrame. */
	public void close() {
		boolean okayToClose = true;
		ImageJ ij = IJ.getInstance();
		if (!getTitle().equals("Errors") && changes && !IJ.isMacro() && ij!=null && !ij.quittingViaMacro()) {
			String msg = "Save changes to \"" + getTitle() + "\"?";
			YesNoCancelDialog d = new YesNoCancelDialog(this, "Editor", msg);
			if (d.cancelPressed())
				okayToClose = false;
			else if (d.yesPressed())
				save();
		}
		if (okayToClose) {
			//setVisible(false);
			dispose();
			WindowManager.removeWindow(this);
			nWindows--;
			instance = null;
			changes = false;
			if (functionFinder!=null)
				functionFinder.close();
		}
	}

	public void saveAs() {
		String name1 = getTitle();
		if (name1.indexOf(".")==-1) name1 += ".txt";
		if (defaultDir==null) {
			if (name1.endsWith(".txt")||name1.endsWith(".ijm"))
				defaultDir = Menus.getMacrosPath();
			else
				defaultDir = Menus.getPlugInsPath();
		}
		SaveDialog sd = new SaveDialog("Save As...", defaultDir, name1, null);
		String name2 = sd.getFileName();
		String dir = sd.getDirectory();
		if (name2!=null) {
			if (name2.endsWith(".java"))
				updateClassName(name1, name2);
			path = dir+name2;
			save();
			changes = false;
			setWindowTitle(name2);
			setDefaultDirectory(dir);
			if (defaultDir!=null)
				Prefs.set(DEFAULT_DIR, defaultDir);
			if (Recorder.record)
				Recorder.record("saveAs", "Text", path);
		}
	}
	
	protected void revert() {
		if (!changes)
			return;
		String title = getTitle();
		if (path==null || !(new File(path).exists()) || !path.endsWith(title)) {
			IJ.showStatus("Cannot revert, no file "+getTitle());
			return;
		}
		if (!IJ.showMessageWithCancel("Revert?", "Revert to saved version of\n\""+getTitle()+"\"?"))
			return;
		String directory = path.substring(0, path.length()-title.length());
		open(directory, title);
		undoBuffer = new ArrayList();
	}

	/** Changes a plugins class name to reflect a new file name. */
	public void updateClassName(String oldName, String newName) {
		if (newName.indexOf("_")<0)
			IJ.showMessage("Plugin Editor", "Plugins without an underscore in their name will not\n"
				+"be automatically installed when ImageJ is restarted.");
		if (oldName.equals(newName) || !oldName.endsWith(".java") || !newName.endsWith(".java"))
			return;
		oldName = oldName.substring(0,oldName.length()-5);
		newName = newName.substring(0,newName.length()-5);
		String text1 = ta.getText();
		int index = text1.indexOf("public class "+oldName);
		if (index<0)
			return;
		String text2 = text1.substring(0,index+13)+newName+text1.substring(index+13+oldName.length(),text1.length());
		ta.setText(text2);
	}
	
	void find(String s) {
		if (s==null) {
			GenericDialog gd = new GenericDialog("Find", this);
			gd.addStringField("Find: ", searchString, 20);
			String[] labels = {"Case Sensitive", "Whole Words"};
			boolean[] states = {caseSensitive, wholeWords};
			gd.addCheckboxGroup(1, 2, labels, states);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			s = gd.getNextString();
			caseSensitive = gd.getNextBoolean();
			wholeWords = gd.getNextBoolean();
			Prefs.set(CASE_SENSITIVE, caseSensitive);
		}
		if (s.equals(""))
			return;
		String text = ta.getText();
		String s2 = s;
		if (!caseSensitive) {
			text = text.toLowerCase(Locale.US);
			s = s.toLowerCase(Locale.US);
		}
		int index = -1;
		if (wholeWords) {
			int position = ta.getCaretPosition()+1;
			while (true) {
				index = text.indexOf(s, position);
				if (index==-1) break;
				if (isWholeWordMatch(text, s, index)) break;
				position = index + 1;
				if (position>=text.length()-1)
					{index=-1; break;}
			}
		} else
			index = text.indexOf(s, ta.getCaretPosition()+1);
		searchString = s2;
		if (index<0)
			{IJ.beep(); return;}
		ta.setSelectionStart(index);
		ta.setSelectionEnd(index+s.length());
	}
	
	boolean isWholeWordMatch(String text, String word, int index) {
		char c = index==0?' ':text.charAt(index-1);
		if (Character.isLetterOrDigit(c) || c=='_') return false;
		c = index+word.length()>=text.length()?' ':text.charAt(index+word.length());
		if (Character.isLetterOrDigit(c) || c=='_') return false;
		return true;
	}
	
	void gotoLine() {
		GenericDialog gd = new GenericDialog("Go to Line", this);
		gd.addNumericField("Go to line number: ", lineNumber, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int n = (int)gd.getNextNumber();
		if (n<1) return;
		String text = ta.getText();
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		int count=1, loc=0;
		for (int i=0; i<chars.length; i++) {
			if (chars[i]=='\n') count++;
			if (count==n)
				{loc=i+1; break;}
		}
		ta.setCaretPosition(loc);
		lineNumber = n;
	}
	
	//extracts  characters  "({[]})" as string and removes inner pairs
	private void balance() { //modified: N.Vischer
		String text = ta.getText();
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		maskComments(chars);
		maskQuotes(chars);
		int position = ta.getCaretPosition();
		if (position == 0) {
			IJ.error("Balance", "This command locates the pair of brackets, curly braces or\nparentheses that surround the insertion point.");
			return;
		}
		int start = -1;
		int stop = -1;
		String leftBows = "";
		for (int i = position -1 ; i >= 0; i--) {
			char ch = chars[i];
			if ("({[]})".indexOf(ch) >= 0) {
				leftBows = ch + leftBows;
				leftBows = leftBows.replace("[]", "");//skip nested pairs
				leftBows = leftBows.replace("()", "");
				leftBows = leftBows.replace("{}", "");
				if (leftBows.equals ("[") || leftBows.equals ("{") || leftBows.equals ("(")) {
					start = i;
					break;
				}
			}
		}
		String rightBows = "";
		for (int i = position ; i < chars.length; i++) {
			char ch = chars[i];
			if ("({[]})".indexOf(ch) >= 0) {
				rightBows += ch;
				rightBows = rightBows.replace("[]", "");//skip nested pairs
				rightBows = rightBows.replace("()", "");
				rightBows = rightBows.replace("{}", "");
				String pair = leftBows + rightBows;
				if (pair.equals("[]") ||  pair.equals("{}") || pair.equals("()")) {
					stop = i;
					break;
				}
			}
		}
		if (start == -1 || stop == -1) {
			IJ.beep();
			return;
		}
		ta.setSelectionStart(start);
		ta.setSelectionEnd(stop + 1);
		IJ.showStatus(chars.length + " " + position + " " + start + " " + stop);
	}

	// replaces contents of comments with blanks
	private void maskComments(char[] chars) {
		int n = chars.length;
		boolean inSlashSlashComment = false;
		boolean inSlashStarComment = false;
		for (int i=0; i<n-1; i++) {
			if (chars[i]=='/' && chars[i+1]=='/')
				inSlashSlashComment = true;
			if (chars[i]=='\n' )
				inSlashSlashComment = false;
			if (!inSlashSlashComment){
				if (chars[i]=='/' && chars[i+1]=='*')
					inSlashStarComment = true;
				if (chars[i]=='*' && chars[i+1]=='/')
					inSlashStarComment = false;
			}
			if (inSlashSlashComment||inSlashStarComment)
				chars[i] = ' ';
		}
	}

	// replaces contents of single and double quotes with blanks - N. Vischer
	private void maskQuotes(char[] chars) {
		int n = chars.length;
		char quote = '\'';//single quote
		for (int loop = 1; loop <= 2; loop++) {
			if (loop == 2)
				quote = '"';//double quote
			boolean inQuotes = false;
			int startMask = 0;
			int stopMask = 0;
			for (int i = 0; i < n - 1; i++) {
				boolean escaped = i > 0 && chars[i - 1] == '\\';
				if (chars[i] == '\n')
					inQuotes = false;
				if (chars[i] == quote && !escaped) {
					if (!inQuotes) {
						startMask = i;
						inQuotes = true;
					} else {
						stopMask = i;
						for (int jj = startMask; jj <= stopMask; jj++) {
							chars[jj] = ' ';
						}
						inQuotes = false;
					}
				}
			}
		}
	}
	
   //replaces contents of comments with blanks
	private void rmaskComments(char[] chars) {
		int n = chars.length;
		boolean inSlashSlashComment = false;
		boolean inSlashStarComment = false;
		for (int i=0; i<n-1; i++) {
			if (chars[i]=='/' && chars[i+1]=='/')
				inSlashSlashComment = true;
			if (chars[i]=='\n' )
				inSlashSlashComment = false;
			if (chars[i]=='/' && chars[i+1]=='*')
				inSlashStarComment = true;
			if (chars[i]=='*' && chars[i+1]=='/')
				inSlashStarComment = false;
			if (inSlashSlashComment||inSlashStarComment)
				chars[i] = ' ';
		}
	}

	void zapGremlins() {
		String text = ta.getText();
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		int count=0;
		boolean inQuotes = false;
		char quoteChar = 0;
		for (int i=0; i<chars.length; i++) {
			char c = chars[i];
			if (!inQuotes && (c=='"' || c=='\'')) {
				inQuotes = true;
				quoteChar = c;
			} else  {
				if (inQuotes && (c==quoteChar || c=='\n'))
				inQuotes = false;
			}
			if (!inQuotes && c!='\n' && c!='\t' && (c<32||c>127)) {
				count++;
				chars[i] = ' ';
			}
		}
		if (count>0) {
			text = new String(chars);
			ta.setText(text);
		}
		if (count>0)
			IJ.showMessage("Zap Gremlins", count+" invalid characters converted to spaces");
		else
			IJ.showMessage("Zap Gremlins", "No invalid characters found");
	}
	
	
	private void detab() {
		GenericDialog gd = new GenericDialog("Detab", this);
		gd.addNumericField("Spaces per tab: ", tabInc, 0);
		gd.addCheckbox("Tab key inserts spaces: ", insertSpaces);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int tabInc2 = tabInc;
		tabInc = (int)gd.getNextNumber();
		if (tabInc<1) tabInc=1;
		if (tabInc>8) tabInc=8;
		if (tabInc!=tabInc2)
			Prefs.set(TAB_INC, tabInc);
		boolean insertSpaces2 = insertSpaces;
		insertSpaces = gd.getNextBoolean();
		if (insertSpaces!=insertSpaces2) {
			Prefs.set(INSERT_SPACES, insertSpaces);
			insertSpacesItem.setState(insertSpaces);
		}
		int nb = 0;
		int pos = 1;
		String text = ta.getText();
		if (text.indexOf('\t')<0)
			return;
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		StringBuffer sb = new StringBuffer((int)(chars.length*1.25));
		for (int i=0; i<chars.length; i++) {
			char c = chars[i];
			if (c=='\t') {
				nb = tabInc - ((pos-1)%tabInc);
				while(nb>0) {
					sb.append(' ');
					++pos;
					--nb;
				}
			} else if (c=='\n') {
				sb.append(c);
				pos = 1;
			} else {
				sb.append(c);
				++pos;
			}
		}
		ta.setText(sb.toString());
	}

	void selectAll() {
		ta.selectAll();
	}
    
    void changeFontSize(boolean larger) {
        int in = fontSize;
        if (larger) {
            fontSize++;
            if (fontSize==sizes.length)
                fontSize = sizes.length-1;
        } else {
            fontSize--;
            if (fontSize<0)
                fontSize = 0;
        }
        IJ.showStatus(sizes[fontSize]+" point");
        setFont();
    }
    
    void saveSettings() {
		Prefs.set(FONT_SIZE, fontSize);
		Prefs.set(FONT_MONO, monospaced.getState());
		IJ.showStatus("Font settings saved (size="+sizes[fontSize]+", monospaced="+monospaced.getState()+")");
    }
    
    void setFont() {
        ta.setFont(new Font(getFontName(), Font.PLAIN, sizes[fontSize]));
    }
    
    String getFontName() {
    	return monospaced.getState()?"Monospaced":"SansSerif";
    }
	
	public void setFont(Font font) {
		ta.setFont(font);
	}

	public void append(String s) {
		ta.append(s);
	}

	public void setIsMacroWindow(boolean mw) {
		isMacroWindow = mw;
	}

	public static void setDefaultDirectory(String defaultDirectory) {
		defaultDir = defaultDirectory;
		if (defaultDir!=null && !(defaultDir.endsWith(File.separator)||defaultDir.endsWith("/")))
			defaultDir += File.separator;
	}
	
	public void lostOwnership (Clipboard clip, Transferable cont) {}
	
	public int debug(Interpreter interp, int mode) {
		if (IJ.debugMode)
			IJ.log("debug: "+interp.getLineNumber()+"  "+mode+"  "+interp);
		if (mode==RUN_TO_COMPLETION)
			return 0;
		int n = interp.getLineNumber();
		if (mode==RUN_TO_CARET) {
			if (n==runToLine) {
				mode = STEP;
				interp.setDebugMode(mode);
			} else
				return 0;
		}
		if (!isVisible()) { // abort macro if user closes window
			interp.abortMacro();
			return 0;
		}
		if (n==previousLine) {
			previousLine=0;
			return 0;
		}
		Window win = WindowManager.getActiveWindow();
		if (win!=this)
			IJ.wait(50);
		toFront();
		previousLine = n;
		String text = ta.getText();
		if (IJ.isWindows())
			text = text.replaceAll("\r\n", "\n");
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		int count=1;
		debugStart=0;
		int len = chars.length;
		debugEnd = len;
		for (int i=0; i<len; i++) {
			if (chars[i]=='\n') count++;
			if (count==n && debugStart==0)
				debugStart=i+1;
			else if (count==n+1) {
				debugEnd=i;
				break;
			}
		}
		//IJ.log("debug: "+debugStart+"  "+debugEnd+"  "+len+"  "+count);
		if (debugStart==1) debugStart = 0;
		if ((debugStart==0||debugStart==len) && debugEnd==len)
			return 0; // skip code added with Interpreter.setAdditionalFunctions()
		ta.select(debugStart, debugEnd);
		if (debugWindow!=null && !debugWindow.isShowing()) {
			interp.setDebugger(null);
			debugWindow = null;
		} else
			debugWindow = interp.updateDebugWindow(interp.getVariables(), debugWindow);
		if (debugWindow!=null) {
			interp.updateArrayInspector();
			toFront();
		}
		if (mode==STEP) {
			step = false;
			while (!step && !interp.done() && isVisible())
				IJ.wait(5);
		} else {
			if (mode==FAST_TRACE)
				IJ.wait(5);
			else
				IJ.wait(150);
		}
		return 0;
	}
		
	public static Editor getInstance() {
		return instance;
	}
	
	public static String getJSPrefix(String arg) {
		if (arg==null)
			arg = "";
		return JavaScriptIncludes+"function getArgument() {return \""+arg+"\";};";
	}
	
	/** Changes Windows (CRLF) line separators to line feeds (LF). */
	public void fixLineEndings() {
		if (!IJ.isWindows())
			return;
		String text = ta.getText();
		text = text.replaceAll("\r\n", "\n");
		ta.setText(text);
	}
	
	public void showLogWindow() {
		Frame log = WindowManager.getFrame("Log");
		if (log!=null)
			log.toFront();
		else
			IJ.log("");
	}

	public boolean fileChanged() {
		return changes;
	}
	
	/** Downloads BeanShell or Jython interpreter using a separate thread. */
	public void run() {
		if (downloading || downloadUrl==null)
			return;
		downloading = true;
		boolean ok = Macro_Runner.downloadJar(downloadUrl);
		downloading = false;
	}

}
