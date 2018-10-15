package ij.text;
import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.plugin.filter.Analyzer;
import ij.macro.Interpreter;
import ij.measure.ResultsTable;
import java.awt.*;
import java.io.*;
import java.awt.event.*;
import java.util.ArrayList;

/** Uses a TextPanel to displays text in a window.
	@see TextPanel
*/
public class TextWindow extends Frame implements ActionListener, FocusListener, ItemListener {

	public static final String LOC_KEY = "results.loc";
	public static final String WIDTH_KEY = "results.width";
	public static final String HEIGHT_KEY = "results.height";
	public static final String LOG_LOC_KEY = "log.loc";
	public static final String LOG_WIDTH_KEY = "log.width";
	public static final String LOG_HEIGHT_KEY = "log.height";
	public static final String DEBUG_LOC_KEY = "debug.loc";
	static final String FONT_SIZE = "tw.font.size";
	static final String FONT_ANTI= "tw.font.anti";
	TextPanel textPanel;
    CheckboxMenuItem antialiased;
	int[] sizes = {9, 10, 11, 12, 13, 14, 16, 18, 20, 24, 36, 48, 60, 72};
	int fontSize = (int)Prefs.get(FONT_SIZE, 5);
	MenuBar mb;
 
	/**
	* Opens a new single-column text window.
	* @param title	the title of the window
	* @param text		the text initially displayed in the window
	* @param width	the width of the window in pixels
	* @param height	the height of the window in pixels
	*/
	public TextWindow(String title, String text, int width, int height) {
		this(title, "", text, width, height);
	}

	/**
	* Opens a new multi-column text window.
	* @param title	title of the window
	* @param headings	the tab-delimited column headings
	* @param text		text initially displayed in the window
	* @param width	width of the window in pixels
	* @param height	height of the window in pixels
	*/
	public TextWindow(String title, String headings, String text, int width, int height) {
		super(title);
		textPanel = new TextPanel(title);
		textPanel.setColumnHeadings(headings);
		if (text!=null && !text.equals(""))
			textPanel.append(text);
		create(title, textPanel, width, height);
	}

	/**
	* Opens a new multi-column text window.
	* @param title	title of the window
	* @param headings	tab-delimited column headings
	* @param text		ArrayList containing the text to be displayed in the window
	* @param width	width of the window in pixels
	* @param height	height of the window in pixels
	*/
	public TextWindow(String title, String headings, ArrayList text, int width, int height) {
		super(title);
		textPanel = new TextPanel(title);
		textPanel.setColumnHeadings(headings);
		if (text!=null)
			textPanel.append(text);
		create(title, textPanel, width, height);
	}

	private void create(String title, TextPanel textPanel, int width, int height) {
		enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		if (IJ.isLinux()) setBackground(ImageJ.backgroundColor);
		add("Center", textPanel);
		addKeyListener(textPanel);
		ImageJ ij = IJ.getInstance();
		if (ij!=null) {
			textPanel.addKeyListener(ij);
			if (!IJ.isMacOSX()) {
				Image img = ij.getIconImage();
				if (img!=null)
					try {setIconImage(img);} catch (Exception e) {}
			}
		}
 		addFocusListener(this);
 		addMenuBar();
		setFont();
		WindowManager.addWindow(this);
		Point loc=null;
		int w=0, h=0;
		if (title.equals("Results")) {
			loc = Prefs.getLocation(LOC_KEY);
			w = (int)Prefs.get(WIDTH_KEY, 0.0);
			h = (int)Prefs.get(HEIGHT_KEY, 0.0);
		} else if (title.equals("Log")) {
			loc = Prefs.getLocation(LOG_LOC_KEY);
			w = (int)Prefs.get(LOG_WIDTH_KEY, 0.0);
			h = (int)Prefs.get(LOG_HEIGHT_KEY, 0.0);
		} else if (title.equals("Debug")) {
			loc = Prefs.getLocation(DEBUG_LOC_KEY);
			w = width;
			h = height;
		}
		if (loc!=null&&w>0 && h>0) {
			setSize(w, h);
			setLocation(loc);
		} else {
			setSize(width, height);
			if (!IJ.debugMode) GUI.center(this);
		}
		show();
		WindowManager.setWindow(this);
	}

	/**
	* Opens a new text window containing the contents of a text file.
	* @param path		the path to the text file
	* @param width	the width of the window in pixels
	* @param height	the height of the window in pixels
	*/
	public TextWindow(String path, int width, int height) {
		super("");
		enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		textPanel = new TextPanel();
		textPanel.addKeyListener(IJ.getInstance());
		add("Center", textPanel);
		if (openFile(path)) {
			WindowManager.addWindow(this);
			setSize(width, height);
			show();
		} else
			dispose();
	}
	
	void addMenuBar() {
		mb = new MenuBar();
		if (Menus.getFontSize()!=0)
			mb.setFont(Menus.getFont());
		Menu m = new Menu("File");
		m.add(new MenuItem("Save As...", new MenuShortcut(KeyEvent.VK_S)));
		if (getTitle().equals("Results")) {
			m.add(new MenuItem("Rename..."));
			m.add(new MenuItem("Duplicate..."));
		}
		m.addActionListener(this);
		mb.add(m);
		textPanel.fileMenu = m;
		m = new Menu("Edit");
		m.add(new MenuItem("Cut", new MenuShortcut(KeyEvent.VK_X)));
		m.add(new MenuItem("Copy", new MenuShortcut(KeyEvent.VK_C)));
		m.add(new MenuItem("Clear"));
		m.add(new MenuItem("Select All", new MenuShortcut(KeyEvent.VK_A)));
		m.addSeparator();
		m.add(new MenuItem("Find...", new MenuShortcut(KeyEvent.VK_F)));
		m.add(new MenuItem("Find Next", new MenuShortcut(KeyEvent.VK_G)));
		m.addActionListener(this);
		mb.add(m);
		textPanel.editMenu = m;
		m = new Menu("Font");
		m.add(new MenuItem("Make Text Smaller"));
		m.add(new MenuItem("Make Text Larger"));
		m.addSeparator();
		antialiased = new CheckboxMenuItem("Antialiased", Prefs.get(FONT_ANTI, IJ.isMacOSX()?true:false));
		antialiased.addItemListener(this);
		m.add(antialiased);
		m.add(new MenuItem("Save Settings"));
		m.addActionListener(this);
		mb.add(m);
		if (getTitle().equals("Results")) {
			m = new Menu("Results");
			m.add(new MenuItem("Clear Results"));
			m.add(new MenuItem("Summarize"));
			m.add(new MenuItem("Distribution..."));
			m.add(new MenuItem("Set Measurements..."));
			m.add(new MenuItem("Plot..."));
			m.add(new MenuItem("Options..."));
			m.addActionListener(this);
			mb.add(m);
		}
		setMenuBar(mb);
	}

	/**
	Adds one or more lines of text to the window.
	@param text		The text to be appended. Multiple
					lines should be separated by \n.
	*/
	public void append(String text) {
		textPanel.append(text);
	}
	
	void setFont() {
        textPanel.setFont(new Font("SanSerif", Font.PLAIN, sizes[fontSize]), antialiased.getState());
	}
	
	boolean openFile(String path) {
		OpenDialog od = new OpenDialog("Open Text File...", path);
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return false;
		path = directory + name;
		
		IJ.showStatus("Opening: " + path);
		try {
			BufferedReader r = new BufferedReader(new FileReader(directory + name));
			load(r);
			r.close();
		}
		catch (Exception e) {
			IJ.error(e.getMessage());
			return true;
		}
		textPanel.setTitle(name);
		setTitle(name);
		IJ.showStatus("");
		return true;
	}
	
	/** Returns a reference to this TextWindow's TextPanel. */
	public TextPanel getTextPanel() {
		return textPanel;
	}
	
	/** Returns the ResultsTable associated with this TextWindow, or null. */
	public ResultsTable getResultsTable() {
		return textPanel!=null?textPanel.getResultsTable():null;
	}


	/** Appends the text in the specified file to the end of this TextWindow. */
	public void load(BufferedReader in) throws IOException {
		int count=0;
		while (true) {
			String s=in.readLine();
			if (s==null) break;
			textPanel.appendLine(s);
		}
	}

	public void actionPerformed(ActionEvent evt) {
		String cmd = evt.getActionCommand();
		if (cmd.equals("Make Text Larger"))
			changeFontSize(true);
		else if (cmd.equals("Make Text Smaller"))
			changeFontSize(false);
		else if (cmd.equals("Save Settings"))
			saveSettings();
		else
			textPanel.doCommand(cmd);
	}

	public void processWindowEvent(WindowEvent e) {
		super.processWindowEvent(e);
		int id = e.getID();
		if (id==WindowEvent.WINDOW_CLOSING)
			close();	
	}

	public void itemStateChanged(ItemEvent e) {
        setFont();
	}

	public void close() {
		close(true);
	}
	
	/** Closes this TextWindow. Display a "save changes" dialog
		if this is the "Results" window and 'showDialog' is true. */
	public void close(boolean showDialog) {
		if (getTitle().equals("Results")) {
			if (showDialog && !Analyzer.resetCounter())
				return;
			IJ.setTextPanel(null);
			Prefs.saveLocation(LOC_KEY, getLocation());
			Dimension d = getSize();
			Prefs.set(WIDTH_KEY, d.width);
			Prefs.set(HEIGHT_KEY, d.height);
		} else if (getTitle().equals("Log")) {
			Prefs.saveLocation(LOG_LOC_KEY, getLocation());
			Dimension d = getSize();
			Prefs.set(LOG_WIDTH_KEY, d.width);
			Prefs.set(LOG_HEIGHT_KEY, d.height);
			IJ.setDebugMode(false);
			IJ.log("\\Closed");
			IJ.notifyEventListeners(IJEventListener.LOG_WINDOW_CLOSED);
		} else if (getTitle().equals("Debug")) {
			Prefs.saveLocation(DEBUG_LOC_KEY, getLocation());
		} else if (textPanel!=null && textPanel.rt!=null) {
			if (!saveContents()) return;
		}
		//setVisible(false);
		dispose();
		WindowManager.removeWindow(this);
		textPanel.flush();
	}
	
	public void rename(String title) {
		textPanel.rename(title);
	}
	
	boolean saveContents() {
		int lineCount = textPanel.getLineCount();
		if (!textPanel.unsavedLines) lineCount = 0;
		ImageJ ij = IJ.getInstance();
		boolean macro = IJ.macroRunning() || Interpreter.isBatchMode();
		boolean isResults = getTitle().contains("Results");
		if (lineCount>0 && !macro && ij!=null && !ij.quitting() && isResults) {
			YesNoCancelDialog d = new YesNoCancelDialog(this, getTitle(), "Save "+lineCount+" measurements?");
			if (d.cancelPressed())
				return false;
			else if (d.yesPressed()) {
				if (!textPanel.saveAs(""))
					return false;
			}
		}
		textPanel.rt.reset();
		return true;
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
		Prefs.set(FONT_ANTI, antialiased.getState());
		IJ.showStatus("Font settings saved (size="+sizes[fontSize]+", antialiased="+antialiased.getState()+")");
	}
	
	public void focusGained(FocusEvent e) {
	}

	public void focusLost(FocusEvent e) {}

}
