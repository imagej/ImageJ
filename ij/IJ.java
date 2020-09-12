package ij;
import ij.gui.*;
import ij.process.*;
import ij.text.*;
import ij.io.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.util.Tools;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.ThresholdAdjuster;
import ij.macro.Interpreter;
import ij.macro.MacroRunner;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.measure.Measurements;
import java.awt.event.*;
import java.text.*;
import java.util.*;	
import java.awt.*;	
import java.applet.Applet;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import javax.net.ssl.*;
import java.security.cert.*;
import java.security.KeyStore;
import java.nio.ByteBuffer;
import java.math.RoundingMode;


/** This class consists of static utility methods. */
public class IJ {

	/** SansSerif, plain, 10-point font */
	public static Font font10 = new Font("SansSerif", Font.PLAIN, 10);
	/** SansSerif, plain, 12-point font */
	public static Font font12 = ImageJ.SansSerif12;
	
	/** Image display modes */
	public static final int COMPOSITE=1, COLOR=2, GRAYSCALE=3;
	
	public static final String URL = "http://imagej.nih.gov/ij";
	public static final int ALL_KEYS = -1;
	
	/** Use setDebugMode(boolean) to enable/disable debug mode. */
	public static boolean debugMode;
	
	public static boolean hideProcessStackDialog;
	    
    public static final char micronSymbol = '\u00B5';
    public static final char angstromSymbol = '\u00C5';
    public static final char degreeSymbol = '\u00B0';

	private static ImageJ ij;
	private static java.applet.Applet applet;
	private static ProgressBar progressBar;
	private static TextPanel textPanel;
	private static String osname, osarch;
	private static boolean isMac, isWin, isLinux, is64Bit;
	private static int javaVersion;
	private static boolean controlDown, altDown, spaceDown, shiftDown;
	private static boolean macroRunning;
	private static Thread previousThread;
	private static TextPanel logPanel;
	private static boolean checkForDuplicatePlugins = true;		
	private static ClassLoader classLoader;
	private static boolean memMessageDisplayed;
	private static long maxMemory;
	private static boolean escapePressed;
	private static boolean redirectErrorMessages;
	private static boolean suppressPluginNotFoundError;
	private static Hashtable commandTable;
	private static Vector eventListeners = new Vector();
	private static String lastErrorMessage;
	private static Properties properties;	private static DecimalFormat[] df;
	private static DecimalFormat[] sf;
	private static DecimalFormatSymbols dfs;
	private static boolean trustManagerCreated;
	private static String smoothMacro;
	private static Interpreter macroInterpreter;
	private static boolean protectStatusBar;
	private static Thread statusBarThread;
			
	static {
		osname = System.getProperty("os.name");
		isWin = osname.startsWith("Windows");
		isMac = !isWin && osname.startsWith("Mac");
		isLinux = osname.startsWith("Linux");
		String version = System.getProperty("java.version");
		if (version==null || version.length()<2)
			version = "1.8";
		if (version.startsWith("1.8"))
			javaVersion = 8;
		else if (version.charAt(0)=='1' && Character.isDigit(version.charAt(1)))
			javaVersion = 10 + (version.charAt(1) - '0');
		else if (version.charAt(0)=='2' && Character.isDigit(version.charAt(1)))
			javaVersion = 20 + (version.charAt(1) - '0');
		else if (version.startsWith("1.6"))
			javaVersion = 6;
		else if (version.startsWith("1.9")||version.startsWith("9"))
			javaVersion = 9;
		else if (version.startsWith("1.7"))
			javaVersion = 7;
		else
			javaVersion = 8;
		dfs = new DecimalFormatSymbols(Locale.US);
		df = new DecimalFormat[10];
		df[0] = new DecimalFormat("0", dfs);
		df[1] = new DecimalFormat("0.0", dfs);
		df[2] = new DecimalFormat("0.00", dfs);
		df[3] = new DecimalFormat("0.000", dfs);
		df[4] = new DecimalFormat("0.0000", dfs);
		df[5] = new DecimalFormat("0.00000", dfs);
		df[6] = new DecimalFormat("0.000000", dfs);
		df[7] = new DecimalFormat("0.0000000", dfs);
		df[8] = new DecimalFormat("0.00000000", dfs);
		df[9] = new DecimalFormat("0.000000000", dfs);
		df[0].setRoundingMode(RoundingMode.HALF_UP);
	}
			
	static void init(ImageJ imagej, Applet theApplet) {
		ij = imagej;
		applet = theApplet;
		progressBar = ij.getProgressBar();
	}

	static void cleanup() {
		ij=null; applet=null; progressBar=null; textPanel=null;
	}

	/**Returns a reference to the "ImageJ" frame.*/
	public static ImageJ getInstance() {
		return ij;
	}
	
	/**Enable/disable debug mode.*/
	public static void setDebugMode(boolean b) {
		debugMode = b;
		LogStream.redirectSystem(debugMode);
	}

	/** Runs the macro contained in the string <code>macro</code>.
		Returns any string value returned by the macro, null if the macro
		does not return a value, or "[aborted]" if the macro was aborted
		due to an error. The equivalent macro function is eval(). */
	public static String runMacro(String macro) {
		return runMacro(macro, "");
	}

	/** Runs the macro contained in the string <code>macro</code>.
		The optional string argument can be retrieved in the
		called macro using the getArgument() macro function. 
		Returns any string value returned by the macro, null if the macro
		does not return a value, or "[aborted]" if the macro was aborted
		due to an error.  */
	public static String runMacro(String macro, String arg) {
		Macro_Runner mr = new Macro_Runner();
		return mr.runMacro(macro, arg);
	}

	/** Runs the specified macro or script file in the current thread.
		The file is assumed to be in the macros folder
 		unless <code>name</code> is a full path.
		The optional string argument (<code>arg</code>) can be retrieved in the called 
		macro or script using the getArgument() function. 
		Returns any string value returned by the macro, or null. Scripts always return null.
		The equivalent macro function is runMacro(). */
	public static String runMacroFile(String name, String arg) {
		Macro_Runner mr = new Macro_Runner();
		return mr.runMacroFile(name, arg);
	}

	/** Runs the specified macro file. */
	public static String runMacroFile(String name) {
		return runMacroFile(name, null);
	}

	/** Runs the specified plugin using the specified image. */
	public static Object runPlugIn(ImagePlus imp, String className, String arg) {
		if (imp!=null) {
			ImagePlus temp = WindowManager.getTempCurrentImage();
			WindowManager.setTempCurrentImage(imp);
			Object o = runPlugIn("", className, arg);
			WindowManager.setTempCurrentImage(temp);
			return o;
		} else
			return runPlugIn(className, arg);
	}

	/** Runs the specified plugin and returns a reference to it. */
	public static Object runPlugIn(String className, String arg) {
		return runPlugIn("", className, arg);
	}
	
	/** Runs the specified plugin and returns a reference to it. */
	public static Object runPlugIn(String commandName, String className, String arg) {
		if (arg==null) arg = "";
		if (IJ.debugMode)
			IJ.log("runPlugIn: "+className+argument(arg));
		// Load using custom classloader if this is a user 
		// plugin and we are not running as an applet
		if (!className.startsWith("ij.") && applet==null)
			return runUserPlugIn(commandName, className, arg, false);
		Object thePlugIn=null;
		try {
			Class c = Class.forName(className);
 			thePlugIn = c.newInstance();
 			if (thePlugIn instanceof PlugIn)
				((PlugIn)thePlugIn).run(arg);
 			else
				new PlugInFilterRunner(thePlugIn, commandName, arg);
		} catch (ClassNotFoundException e) {
			log("Plugin or class not found: \"" + className + "\"\n(" + e+")");
			String path = Prefs.getCustomPropsPath();
			if (path!=null);
				log("Error may be due to custom properties at " + path);
		}
		catch (InstantiationException e) {log("Unable to load plugin (ins)");}
		catch (IllegalAccessException e) {log("Unable to load plugin, possibly \nbecause it is not public.");}
		redirectErrorMessages = false;
		return thePlugIn;
	}
        
	static Object runUserPlugIn(String commandName, String className, String arg, boolean createNewLoader) {
		if (IJ.debugMode)
			IJ.log("runUserPlugIn: "+className+", arg="+argument(arg));
		if (applet!=null) return null;
		if (checkForDuplicatePlugins) {
			// check for duplicate classes and jars in the plugins folder
			IJ.runPlugIn("ij.plugin.ClassChecker", "");
			checkForDuplicatePlugins = false;
		}
		if (createNewLoader)
			classLoader = null;
		ClassLoader loader = getClassLoader();
		Object thePlugIn = null;
		try { 
			thePlugIn = (loader.loadClass(className)).newInstance(); 
			if (thePlugIn instanceof PlugIn)
 				((PlugIn)thePlugIn).run(arg);
 			else if (thePlugIn instanceof PlugInFilter)
				new PlugInFilterRunner(thePlugIn, commandName, arg);
		}
		catch (ClassNotFoundException e) {
			if (className.startsWith("macro:"))
				runMacro(className.substring(6));
			else if (className.contains("_")  && !suppressPluginNotFoundError)
				error("Plugin or class not found: \"" + className + "\"\n(" + e+")");
		}
		catch (NoClassDefFoundError e) {
			int dotIndex = className.indexOf('.');
			if (dotIndex>=0 && className.contains("_")) {
				// rerun plugin after removing folder name
				if (debugMode) IJ.log("runUserPlugIn: rerunning "+className);
				return runUserPlugIn(commandName, className.substring(dotIndex+1), arg, createNewLoader);
			}
			if (className.contains("_") && !suppressPluginNotFoundError)
				error("Run User Plugin", "Class not found while attempting to run \"" + className + "\"\n \n   " + e);
		}
		catch (InstantiationException e) {error("Unable to load plugin (ins)");}
		catch (IllegalAccessException e) {error("Unable to load plugin, possibly \nbecause it is not public.");}
		if (thePlugIn!=null && !"HandleExtraFileTypes".equals(className))
 			redirectErrorMessages = false;
		suppressPluginNotFoundError = false;
		return thePlugIn;
	} 
	
	private static String argument(String arg) {
		return arg!=null && !arg.equals("") && !arg.contains("\n")?"(\""+arg+"\")":"";
	}

	static void wrongType(int capabilities, String cmd) {
		String s = "\""+cmd+"\" requires an image of type:\n \n";
		if ((capabilities&PlugInFilter.DOES_8G)!=0) s +=  "    8-bit grayscale\n";
		if ((capabilities&PlugInFilter.DOES_8C)!=0) s +=  "    8-bit color\n";
		if ((capabilities&PlugInFilter.DOES_16)!=0) s +=  "    16-bit grayscale\n";
		if ((capabilities&PlugInFilter.DOES_32)!=0) s +=  "    32-bit (float) grayscale\n";
		if ((capabilities&PlugInFilter.DOES_RGB)!=0) s += "    RGB color\n";
		error(s);
	}
	
    /** Runs a menu command on a separete thread and returns immediately. */
	public static void doCommand(String command) {
		new Executer(command, null);
	}
	
    /** Runs a menu command on a separete thread, using the specified image. */
	public static void doCommand(ImagePlus imp, String command) {
		new Executer(command, imp);
	}
	
    /** Runs an ImageJ command. Does not return until 
    	the command has finished executing. To avoid "image locked",
    	errors, plugins that call this method should implement
    	the PlugIn interface instead of PlugInFilter. */
	public static void run(String command) {
		run(command, null);
	}
	
    /** Runs an ImageJ command, with options that are passed to the
		GenericDialog and OpenDialog classes. Does not return until
		the command has finished executing. To generate run() calls,
		start the recorder (Plugins/Macro/Record) and run commands
		from the ImageJ menu bar.
	*/
	public static void run(String command, String options) {
		//IJ.log("run1: "+command+" "+Thread.currentThread().hashCode()+" "+options);
		if (ij==null && Menus.getCommands()==null)
			init();
		Macro.abort = false;
		Macro.setOptions(options);
		Thread thread = Thread.currentThread();
		if (previousThread==null || thread!=previousThread) {
			String name = thread.getName();
			if (!name.startsWith("Run$_"))
				thread.setName("Run$_"+name);
		}
		command = convert(command);
		previousThread = thread;
		macroRunning = true;
		Executer e = new Executer(command);
		e.run();
		macroRunning = false;
		Macro.setOptions(null);
		testAbort();
		macroInterpreter = null;
		//IJ.log("run2: "+command+" "+Thread.currentThread().hashCode());
	}
	
	/** The macro interpreter uses this method to run commands. */
	public static void run(Interpreter interpreter, String command, String options) {
		macroInterpreter = interpreter;
		run(command, options);
		macroInterpreter = null;
	}

	/** Converts commands that have been renamed so 
		macros using the old names continue to work. */
	private static String convert(String command) {
		if (commandTable==null) {
			commandTable = new Hashtable(30);
			commandTable.put("New...", "Image...");
			commandTable.put("Threshold", "Make Binary");
			commandTable.put("Display...", "Appearance...");
			commandTable.put("Start Animation", "Start Animation [\\]");
			commandTable.put("Convert Images to Stack", "Images to Stack");
			commandTable.put("Convert Stack to Images", "Stack to Images");
			commandTable.put("Convert Stack to RGB", "Stack to RGB");
			commandTable.put("Convert to Composite", "Make Composite");
			commandTable.put("RGB Split", "Split Channels");
			commandTable.put("RGB Merge...", "Merge Channels...");
			commandTable.put("Channels...", "Channels Tool...");
			commandTable.put("New... ", "Table...");
			commandTable.put("Arbitrarily...", "Rotate... ");
			commandTable.put("Measurements...", "Results... ");
			commandTable.put("List Commands...", "Find Commands...");
			commandTable.put("Capture Screen ", "Capture Screen");
			commandTable.put("Add to Manager ", "Add to Manager");
			commandTable.put("In", "In [+]");
			commandTable.put("Out", "Out [-]");
			commandTable.put("Enhance Contrast", "Enhance Contrast...");
			commandTable.put("XY Coodinates... ", "XY Coordinates... ");
			commandTable.put("Statistics...", "Statistics");
			commandTable.put("Channels Tool... ", "Channels Tool...");
			commandTable.put("Profile Plot Options...", "Plots...");
			commandTable.put("AuPbSn 40 (56K)", "AuPbSn 40");
			commandTable.put("Bat Cochlea Volume (19K)", "Bat Cochlea Volume");
			commandTable.put("Bat Cochlea Renderings (449K)", "Bat Cochlea Renderings");
			commandTable.put("Blobs (25K)", "Blobs");
			commandTable.put("Boats (356K)", "Boats");			
			commandTable.put("Cardio (768K, RGB DICOM)", "Cardio (RGB DICOM)");
			commandTable.put("Cell Colony (31K)", "Cell Colony");
			commandTable.put("Clown (14K)", "Clown");
			commandTable.put("Confocal Series (2.2MB)", "Confocal Series");
			commandTable.put("CT (420K, 16-bit DICOM)", "CT (16-bit DICOM)");			
			commandTable.put("Dot Blot (7K)", "Dot Blot");
			commandTable.put("Embryos (42K)", "Embryos");
			commandTable.put("Fluorescent Cells (400K)", "Fluorescent Cells");
			commandTable.put("Fly Brain (1MB)", "Fly Brain");			
			commandTable.put("Gel (105K)", "Gel");
			commandTable.put("HeLa Cells (1.3M, 48-bit RGB)", "HeLa Cells (48-bit RGB)");
			commandTable.put("Leaf (36K)", "Leaf");
			commandTable.put("Line Graph (21K)", "Line Graph");			
			commandTable.put("Mitosis (26MB, 5D stack)", "Mitosis (5D stack)");
			commandTable.put("MRI Stack (528K)", "MRI Stack");
			commandTable.put("M51 Galaxy (177K, 16-bits)", "M51 Galaxy (16-bits))");
			commandTable.put("Neuron (1.6M, 5 channels", "Neuron (5 channels");
			commandTable.put("Nile Bend (1.9M)", "Nile Bend");			
			commandTable.put("Organ of Corti (2.8M, 4D stack)", "Organ of Corti (4D stack)");
			commandTable.put("Particles (75K)", "Particles");
			commandTable.put("T1 Head (2.4M, 16-bits)", "T1 Head (16-bits)");
			commandTable.put("T1 Head Renderings (736K)", "T1 Head Renderings");
			commandTable.put("TEM Filter (112K)", "TEM Filter");
			commandTable.put("Tree Rings (48K)", "Tree Rings");
		}
		String command2 = (String)commandTable.get(command);
		if (command2!=null)
			return command2;
		else
			return command;
	}

	/** Runs an ImageJ command using the specified image and options.
		To generate run() calls, start the recorder (Plugins/Macro/Record)
		and run commands from the ImageJ menu bar.*/
	public static void run(ImagePlus imp, String command, String options) {
		if (ij==null && Menus.getCommands()==null)
			init();
		if (imp!=null) {
			ImagePlus temp = WindowManager.getTempCurrentImage();
			WindowManager.setTempCurrentImage(imp);
			run(command, options);
			WindowManager.setTempCurrentImage(temp);
		} else
			run(command, options);
	}

	static void init() {
		Menus m = new Menus(null, null);
		Prefs.load(m, null);
		m.addMenuBar();
	}

	private static void testAbort() {
		if (Macro.abort)
			abort();
	}

	/** Returns true if the run(), open() or newImage() method is executing. */
	public static boolean macroRunning() {
		return macroRunning;
	}

	/** Returns true if a macro is running, or if the run(), open() 
		or newImage() method is executing. */
	public static boolean isMacro() {
		return macroRunning || Interpreter.getInstance()!=null;
	}

	/**Returns the Applet that created this ImageJ or null if running as an application.*/
	public static java.applet.Applet getApplet() {
		return applet;
	}
	
	/**Displays a message in the ImageJ status bar. If 's' starts 
		with '!', subsequent showStatus() calls in the current
		thread (without "!" in the message) are suppressed. */
	public static void showStatus(String s) {
		if ((Interpreter.getInstance()==null&&statusBarThread==null)
		|| (statusBarThread!=null&&Thread.currentThread()!=statusBarThread))
			protectStatusBar(false);
		boolean doProtect = s.startsWith("!"); // suppress subsequent showStatus() calls
		if (doProtect) {
			protectStatusBar(true);
			statusBarThread = Thread.currentThread();
			s = s.substring(1);
		}
		if (doProtect || !protectStatusBar) {
			if (ij!=null)
				ij.showStatus(s);
			ImagePlus imp = WindowManager.getCurrentImage();
			ImageCanvas ic = imp!=null?imp.getCanvas():null;
			if (ic!=null)
				ic.setShowCursorStatus(s.length()==0?true:false);
		}
	}
	
	/**
	* @deprecated
	* replaced by IJ.log(), ResultsTable.setResult() and TextWindow.append().
	* There are examples at
	*   http://imagej.nih.gov/ij/plugins/sine-cosine.html
	*/
	public static void write(String s) {
		if (textPanel==null && ij!=null)
			showResults();
		if (textPanel!=null)
				textPanel.append(s);
		else
			System.out.println(s);
	}

	private static void showResults() {
		TextWindow resultsWindow = new TextWindow("Results", "", 400, 250);
		textPanel = resultsWindow.getTextPanel();
		textPanel.setResultsTable(Analyzer.getResultsTable());
	}

	public static synchronized void log(String s) {
		if (s==null) return;
		if (logPanel==null && ij!=null) {
			TextWindow logWindow = new TextWindow("Log", "", 400, 250);
			logPanel = logWindow.getTextPanel();
			logPanel.setFont(new Font("SansSerif", Font.PLAIN, 16));
		}
		if (logPanel!=null) {
			if (s.startsWith("\\"))
				handleLogCommand(s);
			else {
				if (s.endsWith("\n")) {
					if (s.equals("\n\n"))
						s= "\n \n ";
					else if (s.endsWith("\n\n"))
						s = s.substring(0, s.length()-2)+"\n \n ";
					else
						s = s+" ";
				}
				logPanel.append(s);
			}
		} else {
			LogStream.redirectSystem(false);
			System.out.println(s);
		}
	}

	static void handleLogCommand(String s) {
		if (s.equals("\\Closed"))
			logPanel = null;
		else if (s.startsWith("\\Update:")) {
			int n = logPanel.getLineCount();
			String s2 = s.substring(8, s.length());
			if (n==0)
				logPanel.append(s2);
			else
				logPanel.setLine(n-1, s2);
		} else if (s.startsWith("\\Update")) {
			int cindex = s.indexOf(":");
			if (cindex==-1)
				{logPanel.append(s); return;}
			String nstr = s.substring(7, cindex);
			int line = (int)Tools.parseDouble(nstr, -1);
			if (line<0 || line>25)
				{logPanel.append(s); return;}
			int count = logPanel.getLineCount();
			while (line>=count) {
				log("");
				count++;
			}
			String s2 = s.substring(cindex+1, s.length());
			logPanel.setLine(line, s2);
		} else if (s.equals("\\Clear")) {
			logPanel.clear();
		} else if (s.startsWith("\\Heading:")) {
			logPanel.updateColumnHeadings(s.substring(10));
		} else if (s.equals("\\Close")) {
			Frame f = WindowManager.getFrame("Log");
			if (f!=null && (f instanceof TextWindow))
				((TextWindow)f).close();
		} else
			logPanel.append(s);
	}
	
	/** Returns the contents of the Log window or null if the Log window is not open. */
	public static synchronized String getLog() { 
		if (logPanel==null || ij==null)
			return null;
		else
			return logPanel.getText(); 
	} 

/** Clears the "Results" window and sets the column headings to
		those in the tab-delimited 'headings' String. Writes to
		System.out.println if the "ImageJ" frame is not present.*/
	public static void setColumnHeadings(String headings) {
		if (textPanel==null && ij!=null)
			showResults();
		if (textPanel!=null)
			textPanel.setColumnHeadings(headings);
		else
			System.out.println(headings);
	}

	/** Returns true if the "Results" window is open. */
	public static boolean isResultsWindow() {
		return textPanel!=null;
	}
	
	/** Renames a results window. */
	public static void renameResults(String title) {
		Frame frame = WindowManager.getFrontWindow();
		if (frame!=null && (frame instanceof TextWindow)) {
			TextWindow tw = (TextWindow)frame;
			if (tw.getResultsTable()==null) {
				IJ.error("Rename", "\""+tw.getTitle()+"\" is not a results table");
				return;
			}
			tw.rename(title);
		} else if (isResultsWindow()) {
			TextPanel tp = getTextPanel();
			TextWindow tw = (TextWindow)tp.getParent();
			tw.rename(title);
		}
	}

	/** Changes the name of a table window from 'oldTitle' to 'newTitle'. */
	public static void renameResults(String oldTitle, String newTitle) {
		Frame frame = WindowManager.getFrame(oldTitle);
		if (frame==null) {
			error("Rename", "\""+oldTitle+"\" not found");
			return;
		} else if (frame instanceof TextWindow) {
			TextWindow tw = (TextWindow)frame;
			if (tw.getResultsTable()==null) {
				error("Rename", "\""+oldTitle+"\" is not a table");
				return;
			}
			tw.rename(newTitle);
		} else
			error("Rename", "\""+oldTitle+"\" is not a table");
	}

	/** Deletes 'row1' through 'row2' of the "Results" window, where
		'row1' and 'row2' must be in the range 0-Analyzer.getCounter()-1. */
	public static void deleteRows(int row1, int row2) {
		ResultsTable rt = Analyzer.getResultsTable();
		int tableSize = rt.size();
		rt.deleteRows(row1, row2);
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null)
			Overlay.updateTableOverlay(imp, row1, row2, tableSize);
		rt.show("Results");
	}
	
	/** Returns a measurement result, where 'measurement' is "Area", 
	 * "Mean", "StdDev", "Mode", "Min", "Max", "X", "Y", "XM", "YM",
	 * "Perim.", "BX", "BY", "Width", "Height", "Major", "Minor", "Angle",
	 * "Circ.", "Feret", "IntDen", "Median", "Skew", "Kurt", "%Area",
	 * "RawIntDen", "Ch", "Slice", "Frame", "FeretX", "FeretY",
	 * "FeretAngle", "MinFeret", "AR", "Round", "Solidity", "MinThr"
	 * or "MaxThr". Add " raw" to the argument to disable calibration,
	 * for example IJ.getValue("Mean raw"). Add " limit" to enable
	 * the "limit to threshold" option.
	*/
	public static double getValue(ImagePlus imp, String measurement) {
		String options = "";
		int index = measurement.indexOf(" ");
		if (index>0) {
			if (index<measurement.length()-1)
				options = measurement.substring(index+1, measurement.length());
			measurement = measurement.substring(0, index);
		}
		int measurements = Measurements.ALL_STATS + Measurements.SLICE;
		if (options.contains("limit"))
			measurements += Measurements.LIMIT;
		Calibration cal = null;
		if (options.contains("raw")) {
			cal = imp.getCalibration();
			imp.setCalibration(null);
		}
		ImageStatistics stats = imp.getStatistics(measurements);
		ResultsTable rt = new ResultsTable();
		Analyzer analyzer = new Analyzer(imp, measurements, rt);
		analyzer.saveResults(stats, imp.getRoi());
		double value = Double.NaN;
		try {
			value = rt.getValue(measurement, 0);
		} catch (Exception e) {};
		if (cal!=null)
			imp.setCalibration(cal);
		return value;
	}

	/** Returns a reference to the "Results" window TextPanel.
		Opens the "Results" window if it is currently not open.
		Returns null if the "ImageJ" window is not open. */
	public static TextPanel getTextPanel() {
		if (textPanel==null && ij!=null)
			showResults();
		return textPanel;
	}
	
	/** TextWindow calls this method with a null argument when the "Results" window is closed. */
	public static void setTextPanel(TextPanel tp) {
		textPanel = tp;
	}
    
    /**Displays a "no images are open" dialog box.*/
	public static void noImage() {
		String msg = "There are no images open";
		if (macroInterpreter!=null) {
			macroInterpreter.abort(msg);
			macroInterpreter = null;
		} else
			error("No Image", msg);
	}

	/** Displays an "out of memory" message to the "Log" window. */
	public static void outOfMemory(String name) {
		Undo.reset();
		System.gc();
		lastErrorMessage = "out of memory";
		String tot = Runtime.getRuntime().maxMemory()/1048576L+"MB";
		if (!memMessageDisplayed)
			log(">>>>>>>>>>>>>>>>>>>>>>>>>>>");
		log("<Out of memory>");
		if (!memMessageDisplayed) {
			log("<All available memory ("+tot+") has been>");
			log("<used. To make more available, use the>");
			log("<Edit>Options>Memory & Threads command.>");
			log(">>>>>>>>>>>>>>>>>>>>>>>>>>>");
			memMessageDisplayed = true;
		}
		Macro.abort();
	}

	/**	Updates the progress bar, where 0<=progress<=1.0. The progress bar is 
	not shown in BatchMode and erased if progress>=1.0. The progress bar is
    updated only if more than 90 ms have passes since the last call. Does nothing 
	if the ImageJ window is not present. */
	public static void showProgress(double progress) {
		if (progressBar!=null) progressBar.show(progress, false);
	}

	/**	Updates the progress bar, where the length of the bar is set to
    (<code>currentValue+1)/finalValue</code> of the maximum bar length.
    The bar is erased if <code>currentValue&gt;=finalValue</code>. 
    The bar is updated only if more than 90 ms have passed since the last call.
    Does nothing if the ImageJ window is not present. */
    public static void showProgress(int currentIndex, int finalIndex) {
		if (progressBar!=null) {
			progressBar.show(currentIndex, finalIndex);
			if (currentIndex==finalIndex)
				progressBar.setBatchMode(false);
		}
	}

	/** Displays a message in a dialog box titled "Message".
		Writes the Java console if ImageJ is not present. */
	public static void showMessage(String msg) {
		showMessage("Message", msg);
	}

	/** Displays a message in a dialog box with the specified title.
		Displays HTML formatted text if 'msg' starts with "<html>".
		There are examples at
		"http://imagej.nih.gov/ij/macros/HtmlDialogDemo.txt".
		Writes to the Java console if ImageJ is not present. */
	public static void showMessage(String title, String msg) {
		if (ij!=null) {
			if (msg!=null && (msg.startsWith("<html>")||msg.startsWith("<HTML>"))) {
				HTMLDialog hd = new HTMLDialog(title, msg);
				if (isMacro() && hd.escapePressed())
					throw new RuntimeException(Macro.MACRO_CANCELED);
			} else {
				MessageDialog md = new MessageDialog(ij, title, msg); 
				if (isMacro() && md.escapePressed())
					throw new RuntimeException(Macro.MACRO_CANCELED);
			}
		} else
			System.out.println(msg);
	}

	/** Displays a message in a dialog box titled "ImageJ". If a 
		macro or JavaScript is running, it is aborted. Writes to the
		Java console if the ImageJ window is not present.*/
	public static void error(String msg) {
		error(null, msg);
		if (Thread.currentThread().getName().endsWith("JavaScript"))
			throw new RuntimeException(Macro.MACRO_CANCELED);
		else
			Macro.abort();
	}
	
	/** Displays a message in a dialog box with the specified title. If a 
		macro or JavaScript is running, it is aborted. Writes to the
		Java console if the ImageJ window is not present. */
	public static void error(String title, String msg) {
		if (macroInterpreter!=null) {
			macroInterpreter.abort(msg);
			macroInterpreter = null;
			return;
		}
		if (msg!=null && msg.endsWith(Macro.MACRO_CANCELED))
			return;
		String title2 = title!=null?title:"ImageJ";
		boolean abortMacro = title!=null;
		lastErrorMessage = msg;
		if (redirectErrorMessages) {
			IJ.log(title2 + ": " + msg);
			if (abortMacro && (title.contains("Open")||title.contains("Reader")))
				abortMacro = false;
		} else
			showMessage(title2, msg);
		redirectErrorMessages = false;
		if (abortMacro)
			Macro.abort();
	}

	/** Aborts any currently running JavaScript, or use IJ.error(string)
		to abort a JavaScript with a message. */
	public static void exit() {
		if (Thread.currentThread().getName().endsWith("JavaScript"))
			throw new RuntimeException(Macro.MACRO_CANCELED);
	}

	/** 
	 * Returns the last error message written by IJ.error() or null if there
	 * was no error since the last time this method was called.
	 * @see #error(String)
	 */
	public static String getErrorMessage() {
		String msg = lastErrorMessage;
		lastErrorMessage = null;
		return msg;
	}

	/** Displays a message in a dialog box with the specified title.
		Returns false if the user pressed "Cancel". */
	public static boolean showMessageWithCancel(String title, String msg) {
		GenericDialog gd = new GenericDialog(title);
		gd.addMessage(msg);
		gd.showDialog();
		return !gd.wasCanceled();
	}

	public static final int CANCELED = Integer.MIN_VALUE;

	/** Allows the user to enter a number in a dialog box. Returns the	
	    value IJ.CANCELED (-2,147,483,648) if the user cancels the dialog box. 
	    Returns 'defaultValue' if the user enters an invalid number. */
	public static double getNumber(String prompt, double defaultValue) {
		GenericDialog gd = new GenericDialog("");
		int decimalPlaces = (int)defaultValue==defaultValue?0:2;
		gd.addNumericField(prompt, defaultValue, decimalPlaces);
		gd.showDialog();
		if (gd.wasCanceled())
			return CANCELED;
		double v = gd.getNextNumber();
		if (gd.invalidNumber())
			return defaultValue;
		else
			return v;
	}

	/** Allows the user to enter a string in a dialog box. Returns
	    "" if the user cancels the dialog box. */
	public static String getString(String prompt, String defaultString) {
		GenericDialog gd = new GenericDialog("");
		gd.addStringField(prompt, defaultString, 20);
		gd.showDialog();
		if (gd.wasCanceled())
			return "";
		return gd.getNextString();
	}

	/**Delays 'msecs' milliseconds.*/
	public static void wait(int msecs) {
		try {Thread.sleep(msecs);}
		catch (InterruptedException e) { }
	}
	
	/** Emits an audio beep. */
	public static void beep() {
		java.awt.Toolkit.getDefaultToolkit().beep();
	}
	
	/**	Returns a string something like "64K of 256MB (25%)"
	 * that shows how much of  the  available memory is in use.
	 * This is the string displayed when the user clicks in the
	 * status bar.
	*/
	public static String freeMemory() {
		long inUse = currentMemory();
		String inUseStr = inUse<10000*1024?inUse/1024L+"K":inUse/1048576L+"MB";
		String maxStr="";
		long max = maxMemory();
		if (max>0L) {
			double percent = inUse*100/max;
			maxStr = " of "+max/1048576L+"MB ("+(percent<1.0?"<1":d2s(percent,0)) + "%)";
		}
		return  inUseStr + maxStr;
	}
	
	/** Returns the amount of memory currently being used by ImageJ. */
	public static long currentMemory() {
		long freeMem = Runtime.getRuntime().freeMemory();
		long totMem = Runtime.getRuntime().totalMemory();
		return totMem-freeMem;
	}

	/** Returns the maximum amount of memory available to ImageJ or
		zero if ImageJ is unable to determine this limit. */
	public static long maxMemory() {
		if (maxMemory==0L) {
			Memory mem = new Memory();
			maxMemory = mem.getMemorySetting();
			if (maxMemory==0L) maxMemory = mem.maxMemory();
		}
		return maxMemory;
	}
	
	public static void showTime(ImagePlus imp, long start, String str) {
		showTime(imp, start, str, 1);
	}
	
	public static void showTime(ImagePlus imp, long start, String str, int nslices) {
		if (Interpreter.isBatchMode())
			return;
		double seconds = (System.currentTimeMillis()-start)/1000.0;
		if (seconds<=0.5 && macroRunning())
			return;
		double pixels = (double)imp.getWidth() * imp.getHeight();
		double rate = pixels*nslices/seconds;
		String str2;
		if (rate>1000000000.0)
			str2 = "";
		else if (rate<1000000.0)
			str2 = ", "+d2s(rate,0)+" pixels/second";
		else
			str2 = ", "+d2s(rate/1000000.0,1)+" million pixels/second";
		showStatus(str+seconds+" seconds"+str2);
	}
	
	/** Experimental */
	public static  String time(ImagePlus imp, long startNanoTime) {
		double planes = imp.getStackSize();
		double seconds = (System.nanoTime()-startNanoTime)/1000000000.0;
		double mpixels = imp.getWidth()*imp.getHeight()*planes/1000000.0;
		String time = seconds<1.0?d2s(seconds*1000.0,0)+" ms":d2s(seconds,1)+" seconds";
		return time+", "+d2s(mpixels/seconds,1)+" million pixels/second";
	}
		
	/** Converts a number to a formatted string using
		2 digits to the right of the decimal point. */
	public static String d2s(double n) {
		return d2s(n, 2);
	}
	
	/** Converts a number to a rounded formatted string.
		The 'decimalPlaces' argument specifies the number of
		digits to the right of the decimal point (0-9). Uses
		scientific notation if 'decimalPlaces is negative. */
	public static String d2s(double n, int decimalPlaces) {
		if (Double.isNaN(n)||Double.isInfinite(n))
			return ""+n;
		if (n==Float.MAX_VALUE) // divide by 0 in FloatProcessor
			return "3.4e38";
		double np = n;
		if (n<0.0) np = -n;
		if (decimalPlaces<0) synchronized(IJ.class) {
			decimalPlaces = -decimalPlaces;
			if (decimalPlaces>9) decimalPlaces=9;
			if (sf==null) {
				if (dfs==null)
					dfs = new DecimalFormatSymbols(Locale.US);
				sf = new DecimalFormat[10];
				sf[1] = new DecimalFormat("0.0E0",dfs);
				sf[2] = new DecimalFormat("0.00E0",dfs);
				sf[3] = new DecimalFormat("0.000E0",dfs);
				sf[4] = new DecimalFormat("0.0000E0",dfs);
				sf[5] = new DecimalFormat("0.00000E0",dfs);
				sf[6] = new DecimalFormat("0.000000E0",dfs);
				sf[7] = new DecimalFormat("0.0000000E0",dfs);
				sf[8] = new DecimalFormat("0.00000000E0",dfs);
				sf[9] = new DecimalFormat("0.000000000E0",dfs);
			}
			return sf[decimalPlaces].format(n); // use scientific notation
		}
		if (decimalPlaces<0) decimalPlaces = 0;
		if (decimalPlaces>9) decimalPlaces = 9;
		return df[decimalPlaces].format(n);
	}

    /** Converts a number to a rounded formatted string.
    * The 'significantDigits' argument specifies the minimum number
    * of significant digits, which is also the preferred number of
    * digits behind the decimal. Fewer decimals are shown if the 
    * number would have more than 'maxDigits'.
    * Exponential notation is used if more than 'maxDigits' would be needed.
    */
    public static String d2s(double x, int significantDigits, int maxDigits) {
        double log10 = Math.log10(Math.abs(x));
        double roundErrorAtMax = 0.223*Math.pow(10, -maxDigits);
        int magnitude = (int)Math.ceil(log10+roundErrorAtMax);
        int decimals = x==0 ? 0 : maxDigits - magnitude;
        if (decimals<0 || magnitude<significantDigits+1-maxDigits)
            return IJ.d2s(x, -significantDigits); // exp notation for large and small numbers
        else {
            if (decimals>significantDigits)
                decimals = Math.max(significantDigits, decimals-maxDigits+significantDigits);
            return IJ.d2s(x, decimals);
        }
    }

	/** Pad 'n' with leading zeros to the specified number of digits. */
	public static String pad(int n, int digits) {
		String str = ""+n;
		while (str.length()<digits)
			str = "0"+str;
		return str;
	}

	/** Pad 's' with leading zeros to the specified number of digits. */
	public static String pad(String s, int digits) {
		String str = ""+s;
		while (str.length()<digits)
			str = "0"+str;
		return str;
	}

	/** Adds the specified class to a Vector to keep it from being garbage
	collected, which would cause the classes static fields to be reset. 
	Probably not needed with Java 1.2 or later. */
	public static void register(Class c) {
		if (ij!=null) ij.register(c);
	}
	
	/** Returns true if the space bar is down. */
	public static boolean spaceBarDown() {
		return spaceDown;
	}

	/** Returns true if the control key is down. */
	public static boolean controlKeyDown() {
		return controlDown;
	}

	/** Returns true if the alt key is down. */
	public static boolean altKeyDown() {
		return altDown;
	}

	/** Returns true if the shift key is down. */
	public static boolean shiftKeyDown() {
		return shiftDown;
	}
	
	public static void setKeyDown(int key) {
		if (debugMode) IJ.log("setKeyDown: "+key);
		switch (key) {
			case KeyEvent.VK_CONTROL:
				controlDown=true;
				break;
			case KeyEvent.VK_META:
				if (isMacintosh()) controlDown=true;
				break;
			case KeyEvent.VK_ALT:
				altDown=true;
				updateStatus();
				break;
			case KeyEvent.VK_SHIFT:
				shiftDown=true;
				if (debugMode) beep();
				break;
			case KeyEvent.VK_SPACE: {
				spaceDown=true;
				ImageWindow win = WindowManager.getCurrentWindow();
				//if (win!=null) win.getCanvas().setCursor(-1,-1,-1, -1);
				break;
			}
			case KeyEvent.VK_ESCAPE: {
				escapePressed = true;
				break;
			}
		}
	}

	public static void setKeyUp(int key) {
		if (debugMode) IJ.log("setKeyUp: "+key);
		switch (key) {
			case KeyEvent.VK_CONTROL: controlDown=false; break;
			case KeyEvent.VK_META: if (isMacintosh()) controlDown=false; break;
			case KeyEvent.VK_ALT: altDown=false; updateStatus(); break;
			case KeyEvent.VK_SHIFT: shiftDown=false; if (debugMode) beep(); break;
			case KeyEvent.VK_SPACE:
				spaceDown=false;
				ImageWindow win = WindowManager.getCurrentWindow();
				//if (win!=null) win.getCanvas().setCursor(-1,-1,-1,-1);
				break;
			case ALL_KEYS:
				shiftDown=controlDown=altDown=spaceDown=false;
				break;
		}
	}
	
	private static void updateStatus() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			Roi roi = imp.getRoi();
			if (roi!=null && imp.getCalibration().scaled()) {
				roi.showStatus();
			}
		}
	}

	public static void setInputEvent(InputEvent e) {
		altDown = e.isAltDown();
		shiftDown = e.isShiftDown();
	}

	/** Returns true if this machine is a Macintosh. */
	public static boolean isMacintosh() {
		return isMac;
	}
	
	/** Returns true if this machine is a Macintosh running OS X. */
	public static boolean isMacOSX() {
		return isMacintosh();
	}

	/** Returns true if this machine is running Windows. */
	public static boolean isWindows() {
		return isWin;
	}
	
	/** Returns the Java version (6, 7, 8, 9, 10, etc.). */
	public static int javaVersion() {
		return javaVersion;
	}
	
	/** Always returns true. */
	public static boolean isJava2() {
		return true;
	}
	
	/** Always returns true. */
	public static boolean isJava14() {
		return true;
	}

	/** Always returns true. */
	public static boolean isJava15() {
		return true;
	}

	/** Returns true if ImageJ is running on a Java 1.6 or greater JVM. */
	public static boolean isJava16() {
		return javaVersion >= 6;
	}

	/** Returns true if ImageJ is running on a Java 1.7 or greater JVM. */
	public static boolean isJava17() {
		return javaVersion >= 7;
	}

	/** Returns true if ImageJ is running on a Java 1.8 or greater JVM. */
	public static boolean isJava18() {
		return javaVersion >= 8;
	}

	/** Returns true if ImageJ is running on a Java 1.9 or greater JVM. */
	public static boolean isJava19() {
		return javaVersion >= 9;
	}

	/** Returns true if ImageJ is running on Linux. */
	public static boolean isLinux() {
		return isLinux;
	}

	/** Obsolete; always returns false. */
	public static boolean isVista() {
		return false;
	}
	
	/** Returns true if ImageJ is running a 64-bit version of Java. */
	public static boolean is64Bit() {
		if (osarch==null)
			osarch = System.getProperty("os.arch");
		return osarch!=null && osarch.indexOf("64")!=-1;
	}

	/** Displays an error message and returns true if the
		ImageJ version is less than the one specified. */
	public static boolean versionLessThan(String version) {
		boolean lessThan = ImageJ.VERSION.compareTo(version)<0;
		if (lessThan)
			error("This plugin or macro requires ImageJ "+version+" or later. Use\nHelp>Update ImageJ to upgrade to the latest version.");
		return lessThan;
	}
	
	/** Displays a "Process all images?" dialog. Returns
		'flags'+PlugInFilter.DOES_STACKS if the user selects "Yes",
		'flags' if the user selects "No" and PlugInFilter.DONE
		if the user selects "Cancel".
	*/
	public static int setupDialog(ImagePlus imp, int flags) {
		if (imp==null || (ij!=null&&ij.hotkey)) {
			if (ij!=null) ij.hotkey=false;
			return flags;
		}
		int stackSize = imp.getStackSize();
		if (stackSize>1) {
			String macroOptions = Macro.getOptions();
			if (imp.isComposite() && ((CompositeImage)imp).getMode()==IJ.COMPOSITE) {
				if (macroOptions==null || !macroOptions.contains("slice"))
					return flags | PlugInFilter.DOES_STACKS;
			}
			if (macroOptions!=null) {
				if (macroOptions.indexOf("stack ")>=0)
					return flags | PlugInFilter.DOES_STACKS;
				else
					return flags;
			}
			if (hideProcessStackDialog)
				return flags;
			String note = ((flags&PlugInFilter.NO_CHANGES)==0)?" There is\nno Undo if you select \"Yes\".":"";
 			YesNoCancelDialog d = new YesNoCancelDialog(getInstance(),
				"Process Stack?", "Process all "+stackSize+" images?"+note);
			if (d.cancelPressed())
				return PlugInFilter.DONE;
			else if (d.yesPressed()) {
		    	if (imp.getStack().isVirtual() && ((flags&PlugInFilter.NO_CHANGES)==0)) {
		    		int size = (stackSize*imp.getWidth()*imp.getHeight()*imp.getBytesPerPixel()+524288)/1048576;
		    		String msg =
						"Use the Process>Batch>Virtual Stack command\n"+
						"to process a virtual stack or convert it into a\n"+
						"normal stack using Image>Duplicate, which\n"+
						"will require "+size+"MB of additional memory.";
		    		error(msg);
					return PlugInFilter.DONE;
		    	}
				if (Recorder.record)
					Recorder.recordOption("stack");
				return flags | PlugInFilter.DOES_STACKS;
			}
			if (Recorder.record)
				Recorder.recordOption("slice");
		}
		return flags;
	}
		
	/** Creates a rectangular selection. Removes any existing 
		selection if width or height are less than 1. */
	public static void makeRectangle(int x, int y, int width, int height) {
		if (width<=0 || height<0)
			getImage().deleteRoi();
		else {
			ImagePlus img = getImage();
			if (Interpreter.isBatchMode())
				img.setRoi(new Roi(x,y,width,height), false);
			else
				img.setRoi(x, y, width, height);
		}
	}
	
	/** Creates a subpixel resolution rectangular selection. */
	public static void makeRectangle(double x, double y, double width, double height) {
		if (width<=0 || height<0)
			getImage().deleteRoi();
		else
			getImage().setRoi(new Roi(x,y,width,height), !Interpreter.isBatchMode());
	}

	/** Creates an oval selection. Removes any existing 
		selection if width or height are less than 1. */
	public static void makeOval(int x, int y, int width, int height) {
		if (width<=0 || height<0)
			getImage().deleteRoi();
		else {
			ImagePlus img = getImage();
			img.setRoi(new OvalRoi(x, y, width, height));
		}
	}
	
	/** Creates an subpixel resolution oval selection. */
	public static void makeOval(double x, double y, double width, double height) {
		if (width<=0 || height<0)
			getImage().deleteRoi();
		else
			getImage().setRoi(new OvalRoi(x, y, width, height));
	}

	/** Creates a straight line selection. */
	public static void makeLine(int x1, int y1, int x2, int y2) {
		getImage().setRoi(new Line(x1, y1, x2, y2));
	}
	
	/** Creates a straight line selection using floating point coordinates. */
	public static void makeLine(double x1, double y1, double x2, double y2) {
		getImage().setRoi(new Line(x1, y1, x2, y2));
	}

	/** Creates a point selection using integer coordinates.. */
	public static void makePoint(int x, int y) {
		ImagePlus img = getImage();
		Roi roi = img.getRoi();
		if (shiftKeyDown() && roi!=null && roi.getType()==Roi.POINT) {
			Polygon p = roi.getPolygon();
			p.addPoint(x, y);
			img.setRoi(new PointRoi(p.xpoints, p.ypoints, p.npoints));
			IJ.setKeyUp(KeyEvent.VK_SHIFT);
		} else if (altKeyDown() && roi!=null && roi.getType()==Roi.POINT) {
			((PolygonRoi)roi).deleteHandle(x, y);
			IJ.setKeyUp(KeyEvent.VK_ALT);
		} else
			img.setRoi(new PointRoi(x, y));
	}
	
	/** Creates a point selection using floating point coordinates. */
	public static void makePoint(double x, double y) {
		ImagePlus img = getImage();
		Roi roi = img.getRoi();
		if (shiftKeyDown() && roi!=null && roi.getType()==Roi.POINT) {
			Polygon p = roi.getPolygon();
			p.addPoint((int)Math.round(x), (int)Math.round(y));
			img.setRoi(new PointRoi(p.xpoints, p.ypoints, p.npoints));
			IJ.setKeyUp(KeyEvent.VK_SHIFT);
		} else if (altKeyDown() && roi!=null && roi.getType()==Roi.POINT) {
			((PolygonRoi)roi).deleteHandle(x, y);
			IJ.setKeyUp(KeyEvent.VK_ALT);
		} else
			img.setRoi(new PointRoi(x, y));
	}

	/** Creates an Roi. */
	public static Roi Roi(double x, double y, double width, double height) {
		return new Roi(x, y, width, height);
	}

	/** Creates an OvalRoi. */
	public static OvalRoi OvalRoi(double x, double y, double width, double height) {
		return new OvalRoi(x, y, width, height);
	}

	/** Sets the display range (minimum and maximum displayed pixel values) of the current image. */
	public static void setMinAndMax(double min, double max) {
		setMinAndMax(getImage(), min, max, 7);
	}

	/** Sets the display range (minimum and maximum displayed pixel values) of the specified image. */
	public static void setMinAndMax(ImagePlus img, double min, double max) {
		setMinAndMax(img, min, max, 7);
	}

	/** Sets the minimum and maximum displayed pixel values on the specified RGB
	channels, where 4=red, 2=green and 1=blue. */
	public static void setMinAndMax(double min, double max, int channels) {
		setMinAndMax(getImage(), min, max, channels);
	}

	private static void setMinAndMax(ImagePlus img, double min, double max, int channels) {
		Calibration cal = img.getCalibration();
		min = cal.getRawValue(min); 
		max = cal.getRawValue(max);
		if (channels==7)
			img.setDisplayRange(min, max);
		else
			img.setDisplayRange(min, max, channels);
		img.updateAndDraw();
	}

	/** Resets the minimum and maximum displayed pixel values of the
		current image to be the same as the min and max pixel values. */
	public static void resetMinAndMax() {
		resetMinAndMax(getImage());
	}

	/** Resets the minimum and maximum displayed pixel values of the
		specified image to be the same as the min and max pixel values. */
	public static void resetMinAndMax(ImagePlus img) {
		img.resetDisplayRange();
		img.updateAndDraw();
	}

	/** Sets the lower and upper threshold levels and displays the image 
		using red to highlight thresholded pixels. May not work correctly on
		16 and 32 bit images unless the display range has been reset using IJ.resetMinAndMax().
	*/
	public static void setThreshold(double lowerThreshold, double upperThresold) {
		setThreshold(lowerThreshold, upperThresold, null);
	}
	
	/** Sets the lower and upper threshold levels and displays the image using
		the specified <code>displayMode</code> ("Red", "Black & White", "Over/Under" or "No Update"). */
	public static void setThreshold(double lowerThreshold, double upperThreshold, String displayMode) {
		setThreshold(getImage(), lowerThreshold, upperThreshold, displayMode);
	}

	/** Sets the lower and upper threshold levels of the specified image. */
	public static void setThreshold(ImagePlus img, double lowerThreshold, double upperThreshold) {
		setThreshold(img, lowerThreshold, upperThreshold, "Red");
	}

	/** Sets the lower and upper threshold levels of the specified image and updates the display using
		the specified <code>displayMode</code> ("Red", "Black & White", "Over/Under" or "No Update").
		With calibrated images, 'lowerThreshold' and 'upperThreshold' must be density calibrated values.
		Use setRawThreshold() to set the threshold using raw (uncalibrated) values. */
	public static void setThreshold(ImagePlus img, double lowerThreshold, double upperThreshold, String displayMode) {
		Calibration cal = img.getCalibration();
		if (displayMode==null || !displayMode.contains("raw")) {
			lowerThreshold = cal.getRawValue(lowerThreshold); 
			upperThreshold = cal.getRawValue(upperThreshold);
		}
		setRawThreshold(img, lowerThreshold, upperThreshold, displayMode);
	}

	/** This is a version of setThreshold() that always uses raw (uncalibrated) values
		 in the range 0-255 for 8-bit images and 0-65535 for 16-bit images. */
	public static void setRawThreshold(ImagePlus img, double lowerThreshold, double upperThreshold, String displayMode) {
		int mode = ImageProcessor.RED_LUT;
		if (displayMode!=null) {
			displayMode = displayMode.toLowerCase(Locale.US);
			if (displayMode.contains("black"))
				mode = ImageProcessor.BLACK_AND_WHITE_LUT;
			else if (displayMode.contains("over"))
				mode = ImageProcessor.OVER_UNDER_LUT;
			else if (displayMode.contains("no"))
				mode = ImageProcessor.NO_LUT_UPDATE;
		}
		img.getProcessor().setThreshold(lowerThreshold, upperThreshold, mode);
		if (mode!=ImageProcessor.NO_LUT_UPDATE && img.getWindow()!=null) {
			img.getProcessor().setLutAnimation(true);
			img.updateAndDraw();
			ThresholdAdjuster.update();
		}
	}

	public static void setAutoThreshold(ImagePlus imp, String method) {
		ImageProcessor ip = imp.getProcessor();
		if (ip instanceof ColorProcessor)
			throw new IllegalArgumentException("Non-RGB image required");
		ip.setRoi(imp.getRoi());
		if (method!=null) {
			try {
				if (method.indexOf("stack")!=-1)
					setStackThreshold(imp, ip, method);
				else
					ip.setAutoThreshold(method);
			} catch (Exception e) {
				log(e.getMessage());
			}
		} else
			ip.setAutoThreshold(ImageProcessor.ISODATA2, ImageProcessor.RED_LUT);
		imp.updateAndDraw();
	}
	
	private static void setStackThreshold(ImagePlus imp, ImageProcessor ip, String method) {
		boolean darkBackground = method.indexOf("dark")!=-1;
		int measurements = Analyzer.getMeasurements();
		Analyzer.setMeasurements(Measurements.AREA+Measurements.MIN_MAX);
		ImageStatistics stats = new StackStatistics(imp);
		Analyzer.setMeasurements(measurements);
		AutoThresholder thresholder = new AutoThresholder();
		double min=0.0, max=255.0;
		if (imp.getBitDepth()!=8) {
			min = stats.min;
			max = stats.max;
		}
		int threshold = thresholder.getThreshold(method, stats.histogram);
		double lower, upper;
		if (darkBackground) {
			if (ip.isInvertedLut())
				{lower=0.0; upper=threshold;}
			else
				{lower=threshold+1; upper=255.0;}
		} else {
			if (ip.isInvertedLut())
				{lower=threshold+1; upper=255.0;}
			else
				{lower=0.0; upper=threshold;}
		}
		if (lower>255) lower = 255;
		if (max>min) {
			lower = min + (lower/255.0)*(max-min);
			upper = min + (upper/255.0)*(max-min);
		} else
			lower = upper = min;
		ip.setMinAndMax(min, max);
		ip.setThreshold(lower, upper, ImageProcessor.RED_LUT);
		imp.updateAndDraw();
	}

	/** Disables thresholding on the current image. */
	public static void resetThreshold() {
		resetThreshold(getImage());
	}
	
	/** Disables thresholding on the specified image. */
	public static void resetThreshold(ImagePlus img) {
		ImageProcessor ip = img.getProcessor();
		ip.resetThreshold();
		ip.setLutAnimation(true);
		img.updateAndDraw();
		ThresholdAdjuster.update();
	}
	
	/** For IDs less than zero, activates the image with the specified ID.
		For IDs greater than zero, activates the Nth image. */
	public static void selectWindow(int id) {
		if (id>0)
			id = WindowManager.getNthImageID(id);
		ImagePlus imp = WindowManager.getImage(id);
		if (imp==null)
			error("Macro Error", "Image "+id+" not found or no images are open.");
		if (Interpreter.isBatchMode()) {
			ImagePlus impT = WindowManager.getTempCurrentImage();
			ImagePlus impC = WindowManager.getCurrentImage();
			if (impC!=null && impC!=imp && impT!=null)
				impC.saveRoi();
            WindowManager.setTempCurrentImage(imp);
            Interpreter.activateImage(imp);
            WindowManager.setWindow(null);
		} else {
			if (imp==null)
				return;
			ImageWindow win = imp.getWindow();
			if (win!=null) {
				win.toFront();
				WindowManager.setWindow(win);
			}
			long start = System.currentTimeMillis();
			// timeout after 1 second unless current thread is event dispatch thread
			String thread = Thread.currentThread().getName();
			int timeout = thread!=null&&thread.indexOf("EventQueue")!=-1?0:1000;
			if (IJ.isMacOSX() && IJ.isJava18() && timeout>0)
				timeout = 250;  //work around OS X/Java 8 window activation bug
			while (true) {
				wait(10);
				imp = WindowManager.getCurrentImage();
				if (imp!=null && imp.getID()==id)
					return; // specified image is now active
				if ((System.currentTimeMillis()-start)>timeout && win!=null) {
					WindowManager.setCurrentWindow(win);
					return;
				}
			}
		}
	}

	/** Activates the window with the specified title. */
	public static void selectWindow(String title) {
		if (title.equals("ImageJ")&&ij!=null)
			{ij.toFront(); return;}
		long start = System.currentTimeMillis();
		while (System.currentTimeMillis()-start<3000) { // 3 sec timeout
			Window win = WindowManager.getWindow(title);
			if (win!=null && !(win instanceof ImageWindow)) {
				selectWindow(win);
				return;
			}
			int[] wList = WindowManager.getIDList();
			int len = wList!=null?wList.length:0;
			for (int i=0; i<len; i++) {
				ImagePlus imp = WindowManager.getImage(wList[i]);
				if (imp!=null) {
					if (imp.getTitle().equals(title)) {
						selectWindow(imp.getID());
						return;
					}
				}
			}
			wait(10);
		}
		error("Macro Error", "No window with the title \""+title+"\" found.");
	}
	
	static void selectWindow(Window win) {
		if (win instanceof Frame)
			((Frame)win).toFront();
		else
			((Dialog)win).toFront();
		long start = System.currentTimeMillis();
		while (true) {
			wait(10);
			if (WindowManager.getActiveWindow()==win)
				return; // specified window is now in front
			if ((System.currentTimeMillis()-start)>1000) {
				WindowManager.setWindow(win);
				return;   // 1 second timeout
			}
		}
	}
	
	/** Sets the foreground color. */
	public static void setForegroundColor(int red, int green, int blue) {
		setColor(red, green, blue, true);
	}

	/** Sets the background color. */
	public static void setBackgroundColor(int red, int green, int blue) {
		setColor(red, green, blue, false);
	}
	
	static void setColor(int red, int green, int blue, boolean foreground) {
	    if (red<0) red=0; if (green<0) green=0; if (blue<0) blue=0; 
	    if (red>255) red=255; if (green>255) green=255; if (blue>255) blue=255;  
		Color c = new Color(red, green, blue);
		if (foreground) {
			Toolbar.setForegroundColor(c);
			ImagePlus img = WindowManager.getCurrentImage();
			if (img!=null)
				img.getProcessor().setColor(c);
		} else
			Toolbar.setBackgroundColor(c);
	}

	/** Switches to the specified tool, where id = Toolbar.RECTANGLE (0),
		Toolbar.OVAL (1), etc. */
	public static void setTool(int id) {
		Toolbar.getInstance().setTool(id);
	}

	/** Switches to the specified tool, where 'name' is "rect", "elliptical", 
		"brush", etc. Returns 'false' if the name is not recognized. */
	public static boolean setTool(String name) {
		return Toolbar.getInstance().setTool(name);
	}

	/** Returns the name of the current tool. */
	public static String getToolName() {
		return Toolbar.getToolName();
	}

	/** Equivalent to clicking on the current image at (x,y) with the
		wand tool. Returns the number of points in the resulting ROI. */
	public static int doWand(int x, int y) {
		return doWand(getImage(), x, y, 0, null);
	}

	/** Traces the boundary of the area with pixel values within
	* 'tolerance' of the value of the pixel at the starting location.
	* 'tolerance' is in uncalibrated units.
	* 'mode' can be "4-connected", "8-connected" or "Legacy".
	* "Legacy" is for compatibility with previous versions of ImageJ;
	* it is ignored if 'tolerance' > 0.
	*/
	public static int doWand(int x, int y, double tolerance, String mode) {
		return doWand(getImage(), x, y, tolerance, mode);
	}
	
	/** This version of doWand adds an ImagePlus argument. */
	public static int doWand(ImagePlus img, int x, int y, double tolerance, String mode) {
		ImageProcessor ip = img.getProcessor();
		if ((img.getType()==ImagePlus.GRAY32) && Double.isNaN(ip.getPixelValue(x,y)))
			return 0;
		int imode = Wand.LEGACY_MODE;
		boolean smooth = false;
		if (mode!=null) {
			if (mode.startsWith("4"))
				imode = Wand.FOUR_CONNECTED;
			else if (mode.startsWith("8"))
				imode = Wand.EIGHT_CONNECTED;
			smooth = mode.contains("smooth");
				
		}
		Wand w = new Wand(ip);
		double t1 = ip.getMinThreshold();
		if (t1==ImageProcessor.NO_THRESHOLD || (ip.getLutUpdateMode()==ImageProcessor.NO_LUT_UPDATE&& tolerance>0.0)) {
			w.autoOutline(x, y, tolerance, imode);
			smooth = false;
		} else
			w.autoOutline(x, y, t1, ip.getMaxThreshold(), imode);
		if (w.npoints>0) {
			Roi previousRoi = img.getRoi();
			Roi roi = new PolygonRoi(w.xpoints, w.ypoints, w.npoints, Roi.TRACED_ROI);
			img.deleteRoi();
			img.setRoi(roi);			
			if (previousRoi!=null)
				roi.update(shiftKeyDown(), altKeyDown());  // add/subtract ROI to previous one if shift/alt key down
			Roi roi2 = img.getRoi();
			if (smooth && roi2!=null && roi2.getType()==Roi.TRACED_ROI) {
				Rectangle bounds = roi2.getBounds();
				if (bounds.width>1 && bounds.height>1) {
					if (smoothMacro==null)
						smoothMacro = BatchProcessor.openMacroFromJar("SmoothWandTool.txt");
					if (EventQueue.isDispatchThread())
						new MacroRunner(smoothMacro); // run on separate thread
					else
						Macro.eval(smoothMacro);
				}
			}
		}
		return w.npoints;
	}
	
	/** Sets the transfer mode used by the <i>Edit/Paste</i> command, where mode is "Copy", "Blend", "Average", "Difference", 
		"Transparent", "Transparent2", "AND", "OR", "XOR", "Add", "Subtract", "Multiply", or "Divide". */
	public static void setPasteMode(String mode) {
		mode = mode.toLowerCase(Locale.US);
		int m = Blitter.COPY;
		if (mode.startsWith("ble") || mode.startsWith("ave"))
			m = Blitter.AVERAGE;
		else if (mode.startsWith("diff"))
			m = Blitter.DIFFERENCE;
		else if (mode.indexOf("zero")!=-1)
			m = Blitter.COPY_ZERO_TRANSPARENT;
		else if (mode.startsWith("tran"))
			m = Blitter.COPY_TRANSPARENT;
		else if (mode.startsWith("and"))
			m = Blitter.AND;
		else if (mode.startsWith("or"))
			m = Blitter.OR;
		else if (mode.startsWith("xor"))
			m = Blitter.XOR;
		else if (mode.startsWith("sub"))
			m = Blitter.SUBTRACT;
		else if (mode.startsWith("add"))
			m = Blitter.ADD;
		else if (mode.startsWith("div"))
			m = Blitter.DIVIDE;
		else if (mode.startsWith("mul"))
			m = Blitter.MULTIPLY;
		else if (mode.startsWith("min"))
			m = Blitter.MIN;
		else if (mode.startsWith("max"))
			m = Blitter.MAX;
		Roi.setPasteMode(m);
	}

	/** Returns a reference to the active image, or displays an error
		message and aborts the plugin or macro if no images are open. */
	public static ImagePlus getImage() {
		ImagePlus img = WindowManager.getCurrentImage();
		if (img==null) {
			IJ.noImage();
			if (ij==null)
				System.exit(0);
			else
				abort();
		}
		return img;
	}
	
	/**The macro interpreter uses this method to call getImage().*/
	public static ImagePlus getImage(Interpreter interpreter) {
		macroInterpreter = interpreter;
		ImagePlus imp =  getImage();
		macroInterpreter = null;
		return imp;
	}
	
	/** Returns the active image or stack slice as an ImageProcessor, or displays
		an error message and aborts the plugin or macro if no images are open. */
	public static ImageProcessor getProcessor() {
		ImagePlus imp = IJ.getImage();
		return imp.getProcessor();
	}

	/** Switches to the specified stack slice, where 1<='slice'<=stack-size. */
	public static void setSlice(int slice) {
		getImage().setSlice(slice);
	}

	/** Returns the ImageJ version number as a string. */
	public static String getVersion() {
		return ImageJ.VERSION;
	}
	
	/** Returns the ImageJ version and build number as a String, for 
		example "1.46n05", or 1.46n99 if there is no build number. */
	public static String getFullVersion() {
		String build = ImageJ.BUILD;
		if (build.length()==0)
			build = "99";
		else if (build.length()==1)
			build = "0" + build;
		return ImageJ.VERSION+build;
	}
		
	/** Returns the path to the specified directory if <code>title</code> is
		"home" ("user.home"), "downloads", "startup",  "imagej" (ImageJ directory),
		"plugins", "macros", "luts", "temp", "current", "default",
		"image" (directory active image was loaded from) or "file" 
		(directory most recently used to open or save a file),
		otherwise displays a dialog and returns the path to the
		directory selected by the user. Returns null if the specified
		directory is not found or the user cancels the dialog box.
		Also aborts the macro if the user cancels
		the dialog box.*/
	public static String getDirectory(String title) {
		String dir = null;
		String title2 = title.toLowerCase(Locale.US);
		if (title2.equals("plugins"))
			dir = Menus.getPlugInsPath();
		else if (title2.equals("macros"))
			dir = Menus.getMacrosPath();
		else if (title2.equals("luts")) {
			String ijdir = Prefs.getImageJDir();
			if (ijdir!=null)
				dir = ijdir + "luts" + File.separator;
			else
				dir = null;
		} else if (title2.equals("home"))
			dir = System.getProperty("user.home");
		else if (title2.equals("downloads"))
			dir = System.getProperty("user.home")+File.separator+"Downloads";
		else if (title2.equals("startup"))
			dir = Prefs.getImageJDir();
		else if (title2.equals("imagej"))
			dir = Prefs.getImageJDir();
		else if (title2.equals("current") || title2.equals("default"))
			dir = OpenDialog.getDefaultDirectory();
		else if (title2.equals("temp")) {
			dir = System.getProperty("java.io.tmpdir");
			if (isMacintosh()) dir = "/tmp/";
		} else if (title2.equals("image")) {
			ImagePlus imp = WindowManager.getCurrentImage();
			FileInfo fi = imp!=null?imp.getOriginalFileInfo():null;
			if (fi!=null && fi.directory!=null) {
				dir = fi.directory;
			} else
				dir = null;
		} else if (title2.equals("file")) {
			dir = OpenDialog.getLastDirectory();
		} else {
			DirectoryChooser dc = new DirectoryChooser(title);
			dir = dc.getDirectory();
			if (dir==null) Macro.abort();
		}
		dir = addSeparator(dir);
		return dir;
	}
	
	public static String addSeparator(String path) {
		if (path==null)
			return null;
		if (path.length()>0 && !(path.endsWith(File.separator)||path.endsWith("/"))) {
			if (IJ.isWindows()&&path.contains(File.separator))
				path += File.separator;
			else
				path += "/";
		}
		return path;
	}
		
	/** Alias for getDirectory(). */
	public static String getDir(String title) {
		return getDirectory(title);
	}

	
	/** Displays an open file dialog and returns the path to the
		choosen file, or returns null if the dialog is canceled. */
	public static String getFilePath(String dialogTitle) {
		OpenDialog od = new OpenDialog(dialogTitle);
		return od.getPath();
	}
		
	/** Displays a file open dialog box and then opens the tiff, dicom, 
		fits, pgm, jpeg, bmp, gif, lut, roi, or text file selected by 
		the user. Displays an error message if the selected file is not
		in one of the supported formats, or if it is not found. */
	public static void open() {
		open(null);
	}

	/** Opens and displays a tiff, dicom, fits, pgm, jpeg, bmp, gif, lut, 
		roi, or text file. Displays an error message if the specified file
		is not in one of the supported formats, or if it is not found.
		With 1.41k or later, opens images specified by a URL.
		*/
	public static void open(String path) {
		if (ij==null && Menus.getCommands()==null)
			init();
		Opener o = new Opener();
		macroRunning = true;
		if (path==null || path.equals(""))		
			o.open();
		else
			o.open(path);
		macroRunning = false;
	}
		
	/** Opens and displays the nth image in the specified tiff stack. */
	public static void open(String path, int n) {
		if (ij==null && Menus.getCommands()==null)
			init();
		ImagePlus imp = openImage(path, n);
		if (imp!=null) imp.show();
	}

	/** Opens the specified file as a tiff, bmp, dicom, fits, pgm, gif, jpeg 
		or text image and returns an ImagePlus object if successful.
		Calls HandleExtraFileTypes plugin if the file type is not recognised.
		Displays a file open dialog if 'path' is null or an empty string.
		Note that 'path' can also be a URL. Some reader plugins, including
		the Bio-Formats plugin, display the image and return null.
		Use IJ.open() to display a file open dialog box.
	*/
	public static ImagePlus openImage(String path) {
		macroRunning = true;
		ImagePlus imp = (new Opener()).openImage(path);
		macroRunning = false;
		return imp;
	}

	/** Opens the nth image of the specified tiff stack. */
	public static ImagePlus openImage(String path, int n) {
		return (new Opener()).openImage(path, n);
	}

	/** Opens the specified tiff file as a virtual stack. */
	public static ImagePlus openVirtual(String path) {
		return FileInfoVirtualStack.openVirtual(path);
	}

	/** Opens an image using a file open dialog and returns it as an ImagePlus object. */
	public static ImagePlus openImage() {
		return openImage(null);
	}

	/** Opens a URL and returns the contents as a string.
		Returns "<Error: message>" if there an error, including
		host or file not found. */
	public static String openUrlAsString(String url) {
		//if (!trustManagerCreated && url.contains("nih.gov")) trustAllCerts();
		url = Opener.updateUrl(url);
		if (debugMode) log("OpenUrlAsString: "+url);
		StringBuffer sb = null;
		url = url.replaceAll(" ", "%20");
		try {
			//if (url.contains("nih.gov")) addRootCA();
			URL u = new URL(url);
			URLConnection uc = u.openConnection();
			long len = uc.getContentLength();
			if (len>5242880L)
				return "<Error: file is larger than 5MB>";
			InputStream in = u.openStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			sb = new StringBuffer() ;
			String line;
			while ((line=br.readLine()) != null)
				sb.append (line + "\n");
			in.close ();
		} catch (Exception e) {
			return("<Error: "+e+">");
		}
		if (sb!=null)
			return new String(sb);
		else
			return "";
	}
	
	/* 
	public static void addRootCA() throws Exception {
		String path = "/Users/wayne/Downloads/Certificates/lets-encrypt-x1-cross-signed.pem";
		InputStream fis = new BufferedInputStream(new FileInputStream(path));
		Certificate ca = CertificateFactory.getInstance("X.509").generateCertificate(fis);
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		ks.load(null, null);
		ks.setCertificateEntry(Integer.toString(1), ca);
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(ks);
		SSLContext ctx = SSLContext.getInstance("TLS");
		ctx.init(null, tmf.getTrustManagers(), null); 
		HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
	}
	*/
	
	/*
	// Create a new trust manager that trust all certificates
	// http://stackoverflow.com/questions/10135074/download-file-from-https-server-using-java
	private static void trustAllCerts() {
		trustManagerCreated = true;
		TrustManager[] trustAllCerts = new TrustManager[] {
			new X509TrustManager() {
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}
				public void checkClientTrusted (java.security.cert.X509Certificate[] certs, String authType) {
				}
				public void checkServerTrusted (java.security.cert.X509Certificate[] certs, String authType) {
				}
			}
		};
		// Activate the new trust manager
		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (Exception e) {
			IJ.log(""+e);
		}
	}
	*/

	/** Saves the current image, lookup table, selection or text window to the specified file path. 
		The path must end in ".tif", ".jpg", ".gif", ".zip", ".raw", ".avi", ".bmp", ".fits", ".pgm", ".png", ".lut", ".roi" or ".txt".  */
	public static void save(String path) {
		save(null, path);
	}

	/** Saves the specified image, lookup table or selection to the specified file path. 
		The file path should end with ".tif", ".jpg", ".gif", ".zip", ".raw", ".avi", ".bmp", 
		".fits", ".pgm", ".png", ".lut", ".roi" or ".txt". The specified image is saved in 
		TIFF format if there is no extension. */
	public static void save(ImagePlus imp, String path) {
		ImagePlus imp2 = imp;
		if (imp2==null)
			imp2 = WindowManager.getCurrentImage();
		int dotLoc = path.lastIndexOf('.');
		if (dotLoc==-1 && imp2!=null) {
			path = path + ".tif"; // save as TIFF if file name does not have an extension
			dotLoc = path.lastIndexOf('.');
		}
		if (dotLoc!=-1) {
			String title = imp2!=null?imp2.getTitle():null;
			saveAs(imp, path.substring(dotLoc+1), path);
			if (title!=null)
				imp2.setTitle(title);
		} else
			error("The file path passed to IJ.save() method or save()\nmacro function is missing the required extension.\n \n\""+path+"\"");
	}

	/* Saves the active image, lookup table, selection, measurement results, selection XY 
		coordinates or text window to the specified file path. The format argument must be "tiff", 
		"jpeg", "gif", "zip", "raw", "avi", "bmp", "fits", "pgm", "png", "text image", "lut", "selection", "measurements", 
		"xy Coordinates" or "text".  If <code>path</code> is null or an emply string, a file
		save dialog is displayed. */
 	public static void saveAs(String format, String path) {
 		saveAs(null, format, path);
 	}

	/* Saves the specified image. The format argument must be "tiff",  
		"jpeg", "gif", "zip", "raw", "avi", "bmp", "fits", "pgm", "png", 
		"text image", "lut", "selection" or "xy Coordinates". */
 	public static void saveAs(ImagePlus imp, String format, String path) {
		if (format==null)
			return;
		if (path!=null && path.length()==0)
			path = null;
		format = format.toLowerCase(Locale.US);
		Roi roi2 = imp!=null?imp.getRoi():null;
		if (roi2!=null)
			roi2.endPaste();
		if (format.indexOf("tif")!=-1) {
			saveAsTiff(imp, path);
			return;
		} else if (format.indexOf("jpeg")!=-1 || format.indexOf("jpg")!=-1) {
			path = updateExtension(path, ".jpg");
			JpegWriter.save(imp, path, FileSaver.getJpegQuality());
			return;
		} else if (format.indexOf("gif")!=-1) {
			path = updateExtension(path, ".gif");
			GifWriter.save(imp, path);
			return;
		} else if (format.indexOf("text image")!=-1) {
			path = updateExtension(path, ".txt");
			format = "Text Image...";
		} else if (format.indexOf("text")!=-1 || format.indexOf("txt")!=-1) {
			if (path!=null && !path.endsWith(".xls") && !path.endsWith(".csv") && !path.endsWith(".tsv"))
				path = updateExtension(path, ".txt");
			format = "Text...";
		} else if (format.indexOf("zip")!=-1) {
			path = updateExtension(path, ".zip");
			format = "ZIP...";
		} else if (format.indexOf("raw")!=-1) {
			//path = updateExtension(path, ".raw");
			format = "Raw Data...";
		} else if (format.indexOf("avi")!=-1) {
			path = updateExtension(path, ".avi");
			format = "AVI... ";
		} else if (format.indexOf("bmp")!=-1) {
			path = updateExtension(path, ".bmp");
			format = "BMP...";
		} else if (format.indexOf("fits")!=-1) {
			path = updateExtension(path, ".fits");
			format = "FITS...";
		} else if (format.indexOf("png")!=-1) {
			path = updateExtension(path, ".png");
			format = "PNG...";
		} else if (format.indexOf("pgm")!=-1) {
			path = updateExtension(path, ".pgm");
			format = "PGM...";
		} else if (format.indexOf("lut")!=-1) {
			path = updateExtension(path, ".lut");
			format = "LUT...";
		} else if (format.contains("results") || format.contains("measurements") || format.contains("table")) {
			format = "Results...";
		} else if (format.contains("selection") || format.contains("roi")) {
			path = updateExtension(path, ".roi");
			format = "Selection...";
		} else if (format.indexOf("xy")!=-1 || format.indexOf("coordinates")!=-1) {
			path = updateExtension(path, ".txt");
			format = "XY Coordinates...";
		} else
			error("Unsupported save() or saveAs() file format: \""+format+"\"\n \n\""+path+"\"");
		if (path==null)
			run(format);
		else {
			if (path.contains(" "))
				run(imp, format, "save=["+path+"]");
			else
				run(imp, format, "save="+path);
		}
	}
	
	/** Saves the specified image in TIFF format. Displays a file save dialog
		if 'path' is null or an empty string. Returns 'false' if there is an
		error or if the user selects "Cancel" in the file save dialog. */
	public static boolean saveAsTiff(ImagePlus imp, String path) {
		if (imp==null)
			imp = getImage();
		if (path==null || path.equals(""))
			return (new FileSaver(imp)).saveAsTiff();
		if (!path.endsWith(".tiff"))
			path = updateExtension(path, ".tif");
		FileSaver fs = new FileSaver(imp);
		boolean ok;
		if (imp.getStackSize()>1)
			ok = fs.saveAsTiffStack(path);
		else
			ok = fs.saveAsTiff(path);
		if (ok)
			fs.updateImagePlus(path, FileInfo.TIFF);
		return ok;
	}
	
	static String updateExtension(String path, String extension) {
		if (path==null) return null;
		int dotIndex = path.lastIndexOf(".");
		int separatorIndex = path.lastIndexOf(File.separator);
		if (dotIndex>=0 && dotIndex>separatorIndex && (path.length()-dotIndex)<=5) {
			if (dotIndex+1<path.length() && Character.isDigit(path.charAt(dotIndex+1)))
				path += extension;
			else
				path = path.substring(0, dotIndex) + extension;
		} else
			path += extension;
		return path;
	}
	
	/** Saves a string as a file. Displays a file save dialog if
		'path' is null or blank. Returns an error message 
		if there is an exception, otherwise returns null. */
	public static String saveString(String string, String path) {
		return write(string, path, false);
	}

	/** Appends a string to the end of a file. A newline character ("\n") 
		is added to the end of the string before it is written. Returns an  
		error message if there is an exception, otherwise returns null. */
	public static String append(String string, String path) {
		return write(string+"\n", path, true);
	}

	private static String write(String string, String path, boolean append) {
		if (path==null || path.equals("")) {
			String msg = append?"Append String...":"Save String...";
			SaveDialog sd = new SaveDialog(msg, "Untitled", ".txt");
			String name = sd.getFileName();
			if (name==null) return null;
			path = sd.getDirectory() + name;
		}
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(path, append));
			out.write(string);
			out.close();
		} catch (IOException e) {
			return ""+e;
		}
		return null;
	}

	/** Opens a text file as a string. Displays a file open dialog
		if path is null or blank. Returns null if the user cancels
		the file open dialog. If there is an error, returns a 
		 message in the form "Error: message". */
	public static String openAsString(String path) {
		if (path==null || path.equals("")) {
			OpenDialog od = new OpenDialog("Open Text File", "");
			String directory = od.getDirectory();
			String name = od.getFileName();
			if (name==null) return null;
			path = directory + name;
		}
		String str = "";
		File file = new File(path);
		if (!file.exists())
			return "Error: file not found";
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
			str = new String(sb);
		}
		catch (Exception e) {
			str = "Error: "+e.getMessage();
		}
		return str;
	}
	
	public static ByteBuffer openAsByteBuffer(String path) {
		if (path==null || path.equals("")) {
			OpenDialog od = new OpenDialog("Open as ByteBuffer", "");
			String directory = od.getDirectory();
			String name = od.getFileName();
			if (name==null) return null;
			path = directory + name;
		}
		File file = new File(path);
		if (!file.exists()) {
			error("OpenAsByteBuffer", "File not found");
			return null;
		}
		int len = (int)file.length();
		byte[] buffer = new byte[len];
		try {
			InputStream in = new BufferedInputStream(new FileInputStream(path));
			DataInputStream dis = new DataInputStream(in);
			dis.readFully(buffer);
			dis.close();
		}
		catch (Exception e) {
			error("OpenAsByteBuffer", e.getMessage());
			return null;
		}
		return ByteBuffer.wrap(buffer);
	}

	/** Creates a new image.
	*  @param title   image name
	*  @param width  image width in pixels
	*  @param height image height in pixels
	*  @param depth number of stack images
	*  @param bitdepth  8, 16, 32 (float) or 24 (RGB)
	*/
	 public static ImagePlus createImage(String title, int width, int height, int depth, int bitdepth) {
		return NewImage.createImage(title, width, height, depth, bitdepth, NewImage.FILL_BLACK);
	 }

	 /** Creates a new imagePlus. <code>Type</code> should contain "8-bit", "16-bit", "32-bit" or "RGB". 
		 In addition, it can contain "white", "black" or "ramp". <code>Width</code> 
	 	and <code>height</code> specify the width and height of the image in pixels.  
	 	<code>Depth</code> specifies the number of stack slices. */
	 public static ImagePlus createImage(String title, String type, int width, int height, int depth) {
		type = type.toLowerCase(Locale.US);
		int bitDepth = 8;
		if (type.contains("16")) bitDepth = 16;
		if (type.contains("24")||type.contains("rgb")) bitDepth = 24;
		if (type.contains("32")) bitDepth = 32;
		int options = NewImage.FILL_WHITE;
		if (bitDepth==16 || bitDepth==32)
			options = NewImage.FILL_BLACK;
		if (type.contains("white"))
			options = NewImage.FILL_WHITE;
		else if (type.contains("black"))
			options = NewImage.FILL_BLACK;
		else if (type.contains("ramp"))
			options = NewImage.FILL_RAMP;
		else if (type.contains("noise") || type.contains("random"))
			options = NewImage.FILL_NOISE;
		options += NewImage.CHECK_AVAILABLE_MEMORY;
		return NewImage.createImage(title, width, height, depth, bitDepth, options);
	}

	/** Creates a new hyperstack.
	* @param title   image name
	* @param type  "8-bit", "16-bit", "32-bit" or "RGB".  May also
	* contain "white" , "black" (the default), "ramp", "composite-mode",
	* "color-mode", "grayscale-mode or "label".
	* @param width  image width in pixels
	* @param height image height in pixels
	* @param channels number of channels
	* @param slices number of slices
	* @param frames number of frames
	*/
	 public static ImagePlus createImage(String title, String type, int width, int height, int channels, int slices, int frames) {
		if (type.contains("label"))
	 		type += "ramp";
		if (!(type.contains("white")||type.contains("ramp")))
	 		type += "black";
		ImagePlus imp = IJ.createImage(title, type, width, height, channels*slices*frames);
		imp.setDimensions(channels, slices, frames);
		int mode = IJ.COLOR;
		if (type.contains("composite"))
			mode = IJ.COMPOSITE;
		if (type.contains("grayscale"))
			mode = IJ.GRAYSCALE;
		if (channels>1 && imp.getBitDepth()!=24)
			imp = new CompositeImage(imp, mode);
		imp.setOpenAsHyperStack(true);
		if (type.contains("label"))
			HyperStackMaker.labelHyperstack(imp);
		return imp;
	 }

	/** Creates a new hyperstack.
	*  @param title   image name
	*  @param width  image width in pixels
	*  @param height image height in pixels
	*  @param channels number of channels
	*  @param slices number of slices
	*  @param frames number of frames
	*  @param bitdepth  8, 16, 32 (float) or 24 (RGB)
	*/
	 public static ImagePlus createHyperStack(String title, int width, int height, int channels, int slices, int frames, int bitdepth) {
		ImagePlus imp = createImage(title, width, height, channels*slices*frames, bitdepth);
		imp.setDimensions(channels, slices, frames);
		if (channels>1 && bitdepth!=24)
			imp = new CompositeImage(imp, IJ.COMPOSITE);
		imp.setOpenAsHyperStack(true);
		return imp;
	 }
	 
	 /** Opens a new image. <code>Type</code> should contain "8-bit", "16-bit", "32-bit" or "RGB". 
		In addition, it can contain "white", "black" or "ramp". <code>Width</code> 
		and <code>height</code> specify the width and height of the image in pixels.  
		<code>Depth</code> specifies the number of stack slices. */
	public static void newImage(String title, String type, int width, int height, int depth) {
		ImagePlus imp = createImage(title, type, width, height, depth);
		if (imp!=null) {
			macroRunning = true;
			imp.show();
			macroRunning = false;
		}
	}

	/** Returns true if the <code>Esc</code> key was pressed since the
		last ImageJ command started to execute or since resetEscape() was called. */
	public static boolean escapePressed() {
		return escapePressed;
	}

	/** This method sets the <code>Esc</code> key to the "up" position.
		The Executer class calls this method when it runs 
		an ImageJ command in a separate thread. */
	public static void resetEscape() {
		escapePressed = false;
	}
	
	/** Causes IJ.error() output to be temporarily redirected to the "Log" window. */
	public static void redirectErrorMessages() {
		redirectErrorMessages = true;
		lastErrorMessage = null;
	}
	
	/** Set 'true' and IJ.error() output will be temporarily redirected to the "Log" window. */
	public static void redirectErrorMessages(boolean redirect) {
		redirectErrorMessages = redirect;
		lastErrorMessage = null;
	}

	/** Returns the state of the  'redirectErrorMessages' flag, which is set by File/Import/Image Sequence. */
	public static boolean redirectingErrorMessages() {
		return redirectErrorMessages;
	}

	/** Temporarily suppress "plugin not found" errors. */
	public static void suppressPluginNotFoundError() {
		suppressPluginNotFoundError = true;
	}

	/** Returns the class loader ImageJ uses to run plugins or the
		system class loader if Menus.getPlugInsPath() returns null. */
	public static ClassLoader getClassLoader() {
		if (classLoader==null) {
			String pluginsDir = Menus.getPlugInsPath();
			if (pluginsDir==null) {
				String home = System.getProperty("plugins.dir");
				if (home!=null) {
					if (!home.endsWith(Prefs.separator)) home+=Prefs.separator;
					pluginsDir = home+"plugins"+Prefs.separator;
					if (!(new File(pluginsDir)).isDirectory())
						pluginsDir = home;
				}
			}
			if (pluginsDir==null)
				return IJ.class.getClassLoader();
			else {
				if (Menus.jnlp)
					classLoader = new PluginClassLoader(pluginsDir, true);
				else
					classLoader = new PluginClassLoader(pluginsDir);
			}
		}
		return classLoader;
	}
	
	/** Returns the size, in pixels, of the primary display. */
	public static Dimension getScreenSize() {
		Rectangle bounds = GUI.getScreenBounds();
		return new Dimension(bounds.width, bounds.height);
	}
	
	/** Returns, as an array of strings, a list of the LUTs in the 
	 * Image/Lookup Tables menu.
	 * @see ij.plugin#LutLoader.getLut
	 * See also: Help>Examples>JavaScript/Show all LUTs
	 * and Image/Color/Display LUTs
	*/
	public static String[] getLuts() {
		ArrayList list = new ArrayList();
		Hashtable commands = Menus.getCommands();
		Menu lutsMenu = Menus.getImageJMenu("Image>Lookup Tables");
		if (commands==null || lutsMenu==null)
			return new String[0];
		for (int i=0; i<lutsMenu.getItemCount(); i++) {
			MenuItem menuItem = lutsMenu.getItem(i);
			if (menuItem.getActionListeners().length == 0) // separator?
				continue;
			String label = menuItem.getLabel();
			if (label.equals("Invert LUT") || label.equals("Apply LUT"))
				continue;
			String command = (String)commands.get(label);
			if (command==null || command.startsWith("ij.plugin.LutLoader"))
				list.add(label);
		}
		return (String[])list.toArray(new String[list.size()]);
	}
	
	static void abort() {
		if ((ij!=null || Interpreter.isBatchMode()) && macroInterpreter==null)
			throw new RuntimeException(Macro.MACRO_CANCELED);
	}
	
	static void setClassLoader(ClassLoader loader) {
		classLoader = loader;
	}

	public static void resetClassLoader() {
		setClassLoader(null);
	}

	/** Displays a stack trace. Use the setExceptionHandler 
		method() to override with a custom exception handler. */
	public static void handleException(Throwable e) {
		if (exceptionHandler!=null) {
			exceptionHandler.handle(e);
			return;
		}
		if (Macro.MACRO_CANCELED.equals(e.getMessage()))
			return;
		CharArrayWriter caw = new CharArrayWriter();
		PrintWriter pw = new PrintWriter(caw);
		e.printStackTrace(pw);
		String s = caw.toString();
		if (s!=null && s.contains("ThreadDeath"))
			return;
		if (getInstance()!=null) {
			s = IJ.getInstance().getInfo()+"\n \n"+s;
			new TextWindow("Exception", s, 500, 340);
		} else
			log(s);
	}

	/** Installs a custom exception handler that 
		overrides the handleException() method. */
	public static void setExceptionHandler(ExceptionHandler handler) {
		exceptionHandler = handler;
	}

	public interface ExceptionHandler {
		public void handle(Throwable e);
	}

	static ExceptionHandler exceptionHandler;

	public static void addEventListener(IJEventListener listener) {
		eventListeners.addElement(listener);
	}
	
	public static void removeEventListener(IJEventListener listener) {
		eventListeners.removeElement(listener);
	}
	
	public static void notifyEventListeners(int eventID) {
		synchronized (eventListeners) {
			for (int i=0; i<eventListeners.size(); i++) {
				IJEventListener listener = (IJEventListener)eventListeners.elementAt(i);
				listener.eventOccurred(eventID);
			}
		}
	}

	/** Adds a key-value pair to IJ.properties. The key
	* and value are removed if 'value' is null.
	*/
	public static void setProperty(String key, Object value) {
		if (properties==null)
			properties = new Properties();
		if (value==null)
			properties.remove(key);
		else
			properties.put(key, value);
	}

	/** Returns the object in IJ.properties associated
	*  with 'key', or null if 'key' is not found.
	*/
	public static Object getProperty(String key) {
		if (properties==null)
			return null;
		else
			return properties.get(key);
	}
	
	public static boolean statusBarProtected() {
		return protectStatusBar;
	}
	
	public static void protectStatusBar(boolean protect) {
		protectStatusBar = protect;
		if (!protectStatusBar)
			statusBarThread = null;
	}

}
