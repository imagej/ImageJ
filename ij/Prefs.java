package ij;
import ij.util.Java2;
import java.io.*;
import java.util.*;
import java.applet.*;
import java.net.URL;
import java.awt.*;
import java.applet.Applet;
import ij.io.*;
import ij.util.Tools;
import ij.gui.*;
import ij.plugin.filter.*;
import ij.process.ImageConverter;
import ij.plugin.Animator;
import ij.process.FloatBlitter;
import ij.plugin.GelAnalyzer;
import ij.process.ColorProcessor;
import ij.text.TextWindow;

/**
This class contains the ImageJ preferences, which are 
loaded from the "IJ_Props.txt" and "IJ_Prefs.txt" files.
@see ij.ImageJ
*/
public class Prefs {

	public static final String PROPS_NAME = "IJ_Props.txt";
	public static final String PREFS_NAME = "IJ_Prefs.txt";
	public static final String DIR_IMAGE = "dir.image";
	public static final String FCOLOR = "fcolor";
	public static final String BCOLOR = "bcolor";
	public static final String ROICOLOR = "roicolor";
	public static final String SHOW_ALL_COLOR = "showcolor";
	public static final String JPEG = "jpeg";
	public static final String FPS = "fps";
    public static final String DIV_BY_ZERO_VALUE = "div-by-zero";
    public static final String NOISE_SD = "noise.sd";
    public static final String MENU_SIZE = "menu.size";
    public static final String GUI_SCALE = "gui.scale";
    public static final String THREADS = "threads";
	public static final String KEY_PREFIX = ".";
 
	private static final int USE_POINTER=1<<0, ANTIALIASING=1<<1, INTERPOLATE=1<<2, ONE_HUNDRED_PERCENT=1<<3,
		BLACK_BACKGROUND=1<<4, JFILE_CHOOSER=1<<5, UNUSED=1<<6, BLACK_CANVAS=1<<7, WEIGHTED=1<<8, 
		AUTO_MEASURE=1<<9, REQUIRE_CONTROL=1<<10, USE_INVERTING_LUT=1<<11, ANTIALIASED_TOOLS=1<<12,
		INTEL_BYTE_ORDER=1<<13, DOUBLE_BUFFER=1<<14, NO_POINT_LABELS=1<<15, NO_BORDER=1<<16,
		SHOW_ALL_SLICE_ONLY=1<<17, COPY_HEADERS=1<<18, NO_ROW_NUMBERS=1<<19,
		MOVE_TO_MISC=1<<20, ADD_TO_MANAGER=1<<21, RUN_SOCKET_LISTENER=1<<22,
		MULTI_POINT_MODE=1<<23, ROTATE_YZ=1<<24, FLIP_XZ=1<<25,
		DONT_SAVE_HEADERS=1<<26, DONT_SAVE_ROW_NUMBERS=1<<27, NO_CLICK_TO_GC=1<<28,
		AVOID_RESLICE_INTERPOLATION=1<<29, KEEP_UNDO_BUFFERS=1<<30; 
    public static final String OPTIONS = "prefs.options";
    
	public static final String vistaHint = "";  // no longer used

	private static final int USE_SYSTEM_PROXIES=1<<0, USE_FILE_CHOOSER=1<<1,
		SUBPIXEL_RESOLUTION=1<<2, ENHANCED_LINE_TOOL=1<<3, SKIP_RAW_DIALOG=1<<4,
		REVERSE_NEXT_PREVIOUS_ORDER=1<<5, AUTO_RUN_EXAMPLES=1<<6, SHOW_ALL_POINTS=1<<7,
		DO_NOT_SAVE_WINDOW_LOCS=1<<8, JFILE_CHOOSER_CHANGED=1<<9,
		CANCEL_BUTTON_ON_RIGHT=1<<10, IGNORE_RESCALE_SLOPE=1<<11,
		NON_BLOCKING_DIALOGS=1<<12;
	public static final String OPTIONS2 = "prefs.options2";
    
	/** file.separator system property */
	public static String separator = System.getProperty("file.separator");
	/** Use pointer cursor instead of cross */
	public static boolean usePointerCursor;
	/** No longer used */
	public static boolean antialiasedText;
	/** Display images scaled <100% using bilinear interpolation */
	public static boolean interpolateScaledImages;
	/** Open images at 100% magnification*/
	public static boolean open100Percent;
	/** Backgound is black in binary images*/
	public static boolean blackBackground;
	/** Use JFileChooser instead of FileDialog to open and save files. */
	public static boolean useJFileChooser;
	/** Color to grayscale conversion is weighted (0.299, 0.587, 0.114) if the variable is true. */
	public static boolean weightedColor;
	/** Use black image border. */
	public static boolean blackCanvas;
	/** Point tool auto-measure mode. */
	public static boolean pointAutoMeasure;
	/** Point tool auto-next slice mode (not saved in IJ_Prefs). */
	public static boolean pointAutoNextSlice;
	/** Require control or command key for keybaord shortcuts. */
	public static boolean requireControlKey;
	/** Open 8-bit images with inverting LUT so 0 is white and 255 is black. */
	public static boolean useInvertingLut;
	/** Draw tool icons using antialiasing (always true). */
	public static boolean antialiasedTools = true;
	/** Export TIFF and Raw using little-endian byte order. */
	public static boolean intelByteOrder;
	/** No longer used */
	public static boolean doubleBuffer = true;
	/** Do not label multiple points created using point tool. */
	public static boolean noPointLabels;
	/** Disable Edit/Undo command. */
	public static boolean disableUndo;
	/** Do not draw black border around image. */
	public static boolean noBorder;
	/** Only show ROIs associated with current slice in Roi Manager "Show All" mode. */
	public static boolean showAllSliceOnly;
	/** Include column headers when copying tables to clipboard. */
	public static boolean copyColumnHeaders;
	/** Do not include row numbers when copying tables to clipboard. */
	public static boolean noRowNumbers;
	/** Move isolated plugins to Miscellaneous submenu. */
	public static boolean moveToMisc;
	/** Add points to ROI Manager. */
	public static boolean pointAddToManager;
	/** Add points to overlay. */
	public static boolean pointAddToOverlay;
	/** Extend the borders to foreground for binary erosions and closings. */
	public static boolean padEdges;
	/** Run the SocketListener. */
	public static boolean runSocketListener;
	/** Use MultiPoint tool. */
	public static boolean multiPointMode;
	/** Open DICOMs as 32-bit float images */
	public static boolean openDicomsAsFloat;
	/** Ignore Rescale Slope when opening DICOMs */
	public static boolean ignoreRescaleSlope;
	/** Plot rectangular selectons vertically */
	public static boolean verticalProfile;
	/** Rotate YZ orthogonal views 90 degrees */
	public static boolean rotateYZ;
	/** Rotate XZ orthogonal views 180 degrees */
	public static boolean flipXZ;
	/** Don't save Results table column headers */
	public static boolean dontSaveHeaders;
	/** Don't save Results table row numbers */
	public static boolean dontSaveRowNumbers;
	/** Don't run garbage collector when user clicks in status bar */
	public static boolean noClickToGC;
	/** Angle tool measures reflex angle */
	public static boolean reflexAngle;
	/** Avoid interpolation when re-slicing */
	public static boolean avoidResliceInterpolation;
	/** Preserve undo (snapshot) buffers when switching images */
	public static boolean keepUndoBuffers;
	/** Use ROI names as "show all" labels in the ROI Manager */
	public static boolean useNamesAsLabels;
	/** Set the "java.net.useSystemProxies" property */
	public static boolean useSystemProxies;
	/** Use the file chooser to import and export image sequences on Windows and Linux*/
	public static boolean useFileChooser;
	/** Use sub-pixel resolution with line selections */
	public static boolean subPixelResolution;
	/** Adjust contrast when scrolling stacks */
	public static boolean autoContrast;
	/** Allow lines to be created with one click at start and another at the end */
	public static boolean enhancedLineTool;
	/** Keep arrow selection after adding to overlay */
	public static boolean keepArrowSelections;
	/** Aways paint images using double buffering */
	public static boolean paintDoubleBuffered;
	/** Do not display dialog when opening .raw files */
	public static boolean skipRawDialog;
	/** Reverse channel-slice-frame priority used by Next Slice and Previous Slice commands. */
	public static boolean reverseNextPreviousOrder;
	/** Automatically run examples in Help/Examples menu. */
	public static boolean autoRunExamples = true;
	/** Ignore stack positions when displaying points. */
	public static boolean showAllPoints;
	/** Set MenuBar on Macs running Java 8. */
	public static boolean setIJMenuBar = IJ.isMacOSX();
	/** "ImageJ" window is always on top. */
	public static boolean alwaysOnTop;
	/** Automatically spline fit line selections */
	public static boolean splineFitLines;
	/** Enable this option to workaround a bug with some Linux window
		managers that causes windows to wander down the screen. */
	public static boolean doNotSaveWindowLocations;
	/** Use JFileChooser setting changed/ */
	public static boolean jFileChooserSettingChanged;
	/** Convert tiff units to microns if pixel width is less than 0.0001 cm. */
	public static boolean convertToMicrons = true;
	/** Wand tool "Smooth if thresholded" option */
	public static boolean smoothWand;
	/** "Close All" command running */
	public static boolean closingAll;
	/** Dialog "Cancel" button is on right on Linux */
	public static boolean dialogCancelButtonOnRight;
	/** Support TRANSFORM Undo in macros */
	public static boolean supportMacroUndo;
	/** Use NonBlockingGenericDialogs in filters */	
	public static boolean nonBlockingFilterDialogs;
	//Save location of moved image windows */	
	//public static boolean saveImageLocation = true;

	static boolean commandLineMacro;
	static Properties ijPrefs = new Properties();
	static Properties props = new Properties(ijPrefs);
	static String prefsDir;
	static String imagesURL;
	static String ImageJDir;
	static int threads;
	static int transparentIndex = -1;
	private static boolean resetPreferences;
	private static double guiScale = 1.0;
	private static Properties locKeys = new Properties();
	private static String propertiesPath; // location of custom IJ_Props.txt
	private static String preferencesPath; // location of custom IJ_Prefs.txt

	/** Finds and loads the configuration file ("IJ_Props.txt")
	 * and the preferences file ("IJ_Prefs.txt").
	 * @return	an error message if "IJ_Props.txt" not found.
	*/
	public static String load(Object ij, Applet applet) {
		if (ImageJDir==null)
			ImageJDir = System.getProperty("user.dir");
		InputStream f = null;
		try { // Look for IJ_Props.txt in ImageJ folder
			f = new FileInputStream(ImageJDir+"/"+PROPS_NAME);
			propertiesPath = ImageJDir+"/"+PROPS_NAME;
		} catch (FileNotFoundException e) {
			f = null;
		}
		if (f==null) {
			// Look in ij.jar if not found in ImageJ folder
			f = ij.getClass().getResourceAsStream("/"+PROPS_NAME);
		}			
		if (applet!=null)
			return loadAppletProps(f, applet);
		if (f==null)
			return PROPS_NAME+" not found in ij.jar or in "+ImageJDir;
		f = new BufferedInputStream(f);
		try {
			props.load(f);
			f.close();
		} catch (IOException e) {
			return("Error loading "+PROPS_NAME);
		}
		imagesURL = props.getProperty("images.location");
		loadPreferences();
		loadOptions();
		guiScale = get(GUI_SCALE, 1.0);
		return null;
	}

	/*
	static void dumpPrefs() {
		System.out.println("");
		Enumeration e = ijPrefs.keys();
		while (e.hasMoreElements()) {
			String key = (String) e.nextElement();
			System.out.println(key+": "+ijPrefs.getProperty(key));
		}
	}
	*/

	static String loadAppletProps(InputStream f, Applet applet) {
		if (f==null)
			return PROPS_NAME+" not found in ij.jar";
		try {
			props.load(f);
			f.close();
		}
		catch (IOException e) {return("Error loading "+PROPS_NAME);}
		try {
			URL url = new URL(applet.getDocumentBase(), "images/");
			imagesURL = url.toString();
		}
		catch (Exception e) {}
		return null;
	}

	/** Returns the URL of the directory that contains the ImageJ sample images. */
	public static String getImagesURL() {
		return imagesURL;
	}

	/** Sets the URL of the directory that contains the ImageJ sample images. */
	public static void setImagesURL(String url) {
		imagesURL = url;
	}

	/** Obsolete, replaced by getImageJDir(), which, unlike this method, 
		returns a path that ends with File.separator. */
	public static String getHomeDir() {
		return ImageJDir;
	}

	/** Returns the path, ending in File.separator, to the ImageJ directory. */
	public static String getImageJDir() {
		String path = Menus.getImageJPath();
		if (path==null)
			return ImageJDir + File.separator;
		else
			return path;
	}

	/** Returns the path to the directory where the 
		preferences file (IJPrefs.txt) is saved. */
	public static String getPrefsDir() {
		if (prefsDir==null) {
			if (ImageJDir==null)
				ImageJDir = System.getProperty("user.dir");
			File f = new File(ImageJDir+File.separator+PREFS_NAME);
			if (f.exists()) {
				prefsDir = ImageJDir;
				preferencesPath = ImageJDir+"/"+PREFS_NAME;
			}
			//System.out.println("getPrefsDir: "+f+"  "+prefsDir);
			if (prefsDir==null) {
				String dir = System.getProperty("user.home");
				if (IJ.isMacOSX())
					dir += "/Library/Preferences";
				else
					dir += File.separator+".imagej";
				prefsDir = dir;
			}
		}
		return prefsDir;
	}

	/** Sets the path to the ImageJ directory. */
	static void setHomeDir(String path) {
		if (path.endsWith(File.separator))
			path = path.substring(0, path.length()-1);
		ImageJDir = path;
	}

	/** Returns the default directory, if any, or null. */
	public static String getDefaultDirectory() {
		if (commandLineMacro)
			return null;
		else
			return getString(DIR_IMAGE);
	}

	/** Finds a string in IJ_Props or IJ_Prefs.txt. */
	public static String getString(String key) {
		return props.getProperty(key);
	}

	/** Finds an string in IJ_Props or IJ_Prefs.txt. */
	public static String getString(String key, String defaultString) {
		if (props==null)
			return defaultString;
		String s = props.getProperty(key);
		if (s==null)
			return defaultString;
		else
			return s;
	}

	/** Finds a boolean in IJ_Props or IJ_Prefs.txt. */
	public static boolean getBoolean(String key, boolean defaultValue) {
		if (props==null) return defaultValue;
		String s = props.getProperty(key);
		if (s==null)
			return defaultValue;
		else
			return s.equals("true");
	}

	/** Finds an int in IJ_Props or IJ_Prefs.txt. */
	public static int getInt(String key, int defaultValue) {
		if (props==null) //workaround for Netscape JIT bug
			return defaultValue;
		String s = props.getProperty(key);
		if (s!=null) {
			try {
				return Integer.decode(s).intValue();
			} catch (NumberFormatException e) {IJ.log(""+e);}
		}
		return defaultValue;
	}

	/** Looks up a real number in IJ_Props or IJ_Prefs.txt. */
	public static double getDouble(String key, double defaultValue) {
		if (props==null)
			return defaultValue;
		String s = props.getProperty(key);
		Double d = null;
		if (s!=null) {
			try {d = new Double(s);}
			catch (NumberFormatException e){d = null;}
			if (d!=null)
				return(d.doubleValue());
		}
		return defaultValue;
	}

	/** Finds a color in IJ_Props or IJ_Prefs.txt. */
	public static Color getColor(String key, Color defaultColor) {
		int i = getInt(key, 0xaaa);
		if (i == 0xaaa)
			return defaultColor;
		return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF);
	}

	/** Returns the file.separator system property. */
	public static String getFileSeparator() {
		return separator;
	}

	/** Opens the ImageJ preferences file ("IJ_Prefs.txt") file. */
	static void loadPreferences() {
		String path = getPrefsDir()+separator+PREFS_NAME;
		boolean ok =  loadPrefs(path);
		if (!ok) { // not found
			if (IJ.isWindows())
				path = ImageJDir +separator+PREFS_NAME;
			else
				path = System.getProperty("user.home")+separator+PREFS_NAME; //User's home dir
			ok = loadPrefs(path);
			if (ok)
				new File(path).delete();
		}

	}

	static boolean loadPrefs(String path) {
		try {
			InputStream is = new BufferedInputStream(new FileInputStream(path));
			ijPrefs.load(is);
			is.close();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/** Saves user preferences in the IJ_Prefs.txt properties file. */
	public static void savePreferences() {
		String path = null;
		try {
			Properties prefs = new Properties();
			String dir = OpenDialog.getDefaultDirectory();
			if (dir!=null)
				prefs.put(DIR_IMAGE, dir);
			prefs.put(ROICOLOR, Tools.c2hex(Roi.getColor()));
			prefs.put(SHOW_ALL_COLOR, Tools.c2hex(ImageCanvas.getShowAllColor()));
			prefs.put(FCOLOR, Tools.c2hex(Toolbar.getForegroundColor()));
			prefs.put(BCOLOR, Tools.c2hex(Toolbar.getBackgroundColor()));
			prefs.put(JPEG, Integer.toString(FileSaver.getJpegQuality()));
			prefs.put(FPS, Double.toString(Animator.getFrameRate()));
			prefs.put(DIV_BY_ZERO_VALUE, Double.toString(FloatBlitter.divideByZeroValue));
			prefs.put(NOISE_SD, Double.toString(Filters.getSD()));
			if (threads>1) prefs.put(THREADS, Integer.toString(threads));
			if (IJ.isMacOSX()) useJFileChooser = false;
			if (!IJ.isLinux()) dialogCancelButtonOnRight = false;
			saveOptions(prefs);
			savePluginPrefs(prefs);
			IJ.getInstance().savePreferences(prefs);
			Menus.savePreferences(prefs);
			ParticleAnalyzer.savePreferences(prefs);
			Analyzer.savePreferences(prefs);
			ImportDialog.savePreferences(prefs);
			PlotWindow.savePreferences(prefs);
			NewImage.savePreferences(prefs);
			String prefsDir = getPrefsDir();
			path = prefsDir+separator+PREFS_NAME;
			if (prefsDir.endsWith(".imagej")) {
				File f = new File(prefsDir);
				if (!f.exists()) f.mkdir(); // create .imagej directory
			}
			if (resetPreferences) {
				File f = new File(path);
				if (!f.exists())
					IJ.error("Edit>Options>Reset", "Unable to reset preferences. File not found at\n"+path);
				boolean rtn = f.delete();
				resetPreferences = false;
			} else
				savePrefs(prefs, path);
		} catch (Throwable t) {
			String msg = t.getMessage();
			if (msg==null) msg = ""+t;
			int delay = 4000;
			try {
				new TextWindow("Error Saving Preferences:\n"+path, msg, 500, 200);
				IJ.wait(delay);
			} catch (Throwable t2) {}
		}
	}

	/** Delete the preferences file when ImageJ quits. */
	public static void resetPreferences() {
		resetPreferences = true;
	}

	static void loadOptions() {
		int defaultOptions = ANTIALIASING+AVOID_RESLICE_INTERPOLATION+ANTIALIASED_TOOLS+MULTI_POINT_MODE
			+(!IJ.isMacOSX()?RUN_SOCKET_LISTENER:0)+BLACK_BACKGROUND;
		int options = getInt(OPTIONS, defaultOptions);
		usePointerCursor = (options&USE_POINTER)!=0;
		//antialiasedText = (options&ANTIALIASING)!=0;
		antialiasedText = false;
		interpolateScaledImages = (options&INTERPOLATE)!=0;
		open100Percent = (options&ONE_HUNDRED_PERCENT)!=0;
		blackBackground = (options&BLACK_BACKGROUND)!=0;
		useJFileChooser = (options&JFILE_CHOOSER)!=0;
		weightedColor = (options&WEIGHTED)!=0;
		if (weightedColor)
			ColorProcessor.setWeightingFactors(0.299, 0.587, 0.114);
		blackCanvas = (options&BLACK_CANVAS)!=0;
		requireControlKey = (options&REQUIRE_CONTROL)!=0;
		useInvertingLut = (options&USE_INVERTING_LUT)!=0;
		antialiasedTools = (options&ANTIALIASED_TOOLS)!=0;
		intelByteOrder = (options&INTEL_BYTE_ORDER)!=0;
		noBorder = (options&NO_BORDER)!=0;
		showAllSliceOnly = (options&SHOW_ALL_SLICE_ONLY)!=0;
		copyColumnHeaders = (options&COPY_HEADERS)!=0;
		noRowNumbers = (options&NO_ROW_NUMBERS)!=0;
		moveToMisc = (options&MOVE_TO_MISC)!=0;
		runSocketListener = (options&RUN_SOCKET_LISTENER)!=0;
		multiPointMode = (options&MULTI_POINT_MODE)!=0;
		rotateYZ = (options&ROTATE_YZ)!=0;
		flipXZ = (options&FLIP_XZ)!=0;
		//dontSaveHeaders = (options&DONT_SAVE_HEADERS)!=0;
		//dontSaveRowNumbers = (options&DONT_SAVE_ROW_NUMBERS)!=0;
		noClickToGC = (options&NO_CLICK_TO_GC)!=0;
		avoidResliceInterpolation = (options&AVOID_RESLICE_INTERPOLATION)!=0;
		keepUndoBuffers = (options&KEEP_UNDO_BUFFERS)!=0;
		
		defaultOptions = (!IJ.isMacOSX()?USE_FILE_CHOOSER:0);
		int options2 = getInt(OPTIONS2, defaultOptions);
		useSystemProxies = (options2&USE_SYSTEM_PROXIES)!=0;
		useFileChooser = (options2&USE_FILE_CHOOSER)!=0;
		subPixelResolution = (options2&SUBPIXEL_RESOLUTION)!=0;
		enhancedLineTool = (options2&ENHANCED_LINE_TOOL)!=0;
		skipRawDialog = (options2&SKIP_RAW_DIALOG)!=0;
		reverseNextPreviousOrder = (options2&REVERSE_NEXT_PREVIOUS_ORDER)!=0;
		autoRunExamples = (options2&AUTO_RUN_EXAMPLES)!=0;
		showAllPoints = (options2&SHOW_ALL_POINTS)!=0;
		doNotSaveWindowLocations = (options2&DO_NOT_SAVE_WINDOW_LOCS)!=0;
		jFileChooserSettingChanged = (options2&JFILE_CHOOSER_CHANGED)!=0;
		dialogCancelButtonOnRight = (options2&CANCEL_BUTTON_ON_RIGHT)!=0;
		ignoreRescaleSlope = (options2&IGNORE_RESCALE_SLOPE)!=0;
		nonBlockingFilterDialogs = (options2&NON_BLOCKING_DIALOGS)!=0;
	}

	static void saveOptions(Properties prefs) {
		int options = (usePointerCursor?USE_POINTER:0) + (antialiasedText?ANTIALIASING:0)
			+ (interpolateScaledImages?INTERPOLATE:0) + (open100Percent?ONE_HUNDRED_PERCENT:0)
			+ (blackBackground?BLACK_BACKGROUND:0) + (useJFileChooser?JFILE_CHOOSER:0)
			+ (blackCanvas?BLACK_CANVAS:0) + (weightedColor?WEIGHTED:0) 
			+ (requireControlKey?REQUIRE_CONTROL:0)
			+ (useInvertingLut?USE_INVERTING_LUT:0) + (antialiasedTools?ANTIALIASED_TOOLS:0)
			+ (intelByteOrder?INTEL_BYTE_ORDER:0) + (doubleBuffer?DOUBLE_BUFFER:0)
			+ (noPointLabels?NO_POINT_LABELS:0) + (noBorder?NO_BORDER:0)
			+ (showAllSliceOnly?SHOW_ALL_SLICE_ONLY:0) + (copyColumnHeaders?COPY_HEADERS:0)
			+ (noRowNumbers?NO_ROW_NUMBERS:0) + (moveToMisc?MOVE_TO_MISC:0)
			+ (runSocketListener?RUN_SOCKET_LISTENER:0)
			+ (multiPointMode?MULTI_POINT_MODE:0) + (rotateYZ?ROTATE_YZ:0)
			+ (flipXZ?FLIP_XZ:0) + (dontSaveHeaders?DONT_SAVE_HEADERS:0)
			+ (dontSaveRowNumbers?DONT_SAVE_ROW_NUMBERS:0) + (noClickToGC?NO_CLICK_TO_GC:0)
			+ (avoidResliceInterpolation?AVOID_RESLICE_INTERPOLATION:0)
			+ (keepUndoBuffers?KEEP_UNDO_BUFFERS:0);
		prefs.put(OPTIONS, Integer.toString(options));

		int options2 = (useSystemProxies?USE_SYSTEM_PROXIES:0)
			+ (useFileChooser?USE_FILE_CHOOSER:0) + (subPixelResolution?SUBPIXEL_RESOLUTION:0)
			+ (enhancedLineTool?ENHANCED_LINE_TOOL:0) + (skipRawDialog?SKIP_RAW_DIALOG:0)
			+ (reverseNextPreviousOrder?REVERSE_NEXT_PREVIOUS_ORDER:0)
			+ (autoRunExamples?AUTO_RUN_EXAMPLES:0) + (showAllPoints?SHOW_ALL_POINTS:0)
			+ (doNotSaveWindowLocations?DO_NOT_SAVE_WINDOW_LOCS:0)
			+ (jFileChooserSettingChanged?JFILE_CHOOSER_CHANGED:0)
			+ (dialogCancelButtonOnRight?CANCEL_BUTTON_ON_RIGHT:0)
			+ (ignoreRescaleSlope?IGNORE_RESCALE_SLOPE:0)
			+ (nonBlockingFilterDialogs?NON_BLOCKING_DIALOGS:0);
		prefs.put(OPTIONS2, Integer.toString(options2));
	}

	/** Saves the value of the string <code>text</code> in the preferences
		file using the keyword <code>key</code>. This string can be 
		retrieved using the appropriate <code>get()</code> method. */
	public static void set(String key, String text) {
		if (key.indexOf('.')<1)
			throw new IllegalArgumentException("Key must have a prefix");
		if (text==null)
			ijPrefs.remove(KEY_PREFIX+key);
		else
			ijPrefs.put(KEY_PREFIX+key, text);
	}

	/** Saves <code>value</code> in the preferences file using 
		the keyword <code>key</code>. This value can be retrieved 
		using the appropriate <code>getPref()</code> method. */
	public static void set(String key, int value) {
		set(key, Integer.toString(value));
	}

	/** Saves <code>value</code> in the preferences file using 
		the keyword <code>key</code>. This value can be retrieved 
		using the appropriate <code>getPref()</code> method. */
	public static void set(String key, double value) {
		set(key, ""+value);
	}

	/** Saves the boolean variable <code>value</code> in the preferences
		 file using the keyword <code>key</code>. This value can be retrieved 
		using the appropriate <code>getPref()</code> method. */
	public static void set(String key, boolean value) {
		set(key, ""+value);
	}

	/** Uses the keyword <code>key</code> to retrieve a string from the
		preferences file. Returns <code>defaultValue</code> if the key
		is not found. */
	public static String get(String key, String defaultValue) {
		String value = ijPrefs.getProperty(KEY_PREFIX+key);
		if (value == null)
			return defaultValue;
		else
			return value;
	}

	/** Uses the keyword <code>key</code> to retrieve a number from the
		preferences file. Returns <code>defaultValue</code> if the key
		is not found. */
	public static double get(String key, double defaultValue) {
		String s = ijPrefs.getProperty(KEY_PREFIX+key);
		Double d = null;
		if (s!=null) {
			try {d = new Double(s);}
			catch (NumberFormatException e) {d = null;}
			if (d!=null)
				return(d.doubleValue());
		}
		return defaultValue;
	}

	/** Uses the keyword <code>key</code> to retrieve a boolean from
		the preferences file. Returns <code>defaultValue</code> if
		the key is not found. */
	public static boolean get(String key, boolean defaultValue) {
		String value = ijPrefs.getProperty(KEY_PREFIX+key);
		if (value==null)
			return defaultValue;
		else
			return value.equals("true");
	}

	/** Saves the Point <code>loc</code> in the preferences
		 file as a string using the keyword <code>key</code>. */
	public static void saveLocation(String key, Point loc) {
		if (!doNotSaveWindowLocations)
			set(key, loc!=null?loc.x+","+loc.y:null);
	}

	/** Uses the keyword <code>key</code> to retrieve a location
		from the preferences file. Returns null if the
		key is not found or the location is not valid (e.g., offscreen). */
	public static Point getLocation(String key) {
		String value = ijPrefs.getProperty(KEY_PREFIX+key);
		if (value==null) return null;
		int index = value.indexOf(",");
		if (index==-1) return null;
		double xloc = Tools.parseDouble(value.substring(0, index));
		if (Double.isNaN(xloc) || index==value.length()-1) return null;
		double yloc = Tools.parseDouble(value.substring(index+1));
		if (Double.isNaN(yloc)) return null;
		Point p = new Point((int)xloc, (int)yloc);
		Rectangle bounds = GUI.getScreenBounds(p); // get bounds of screen that contains p
		if (bounds!=null && p.x+100<=bounds.x+bounds.width && p.y+ 40<=bounds.y+bounds.height) {
			if (locKeys.get(key)==null) { // first time for this key? 
				locKeys.setProperty(key, "");
				Rectangle primaryScreen = GUI.getMaxWindowBounds();
				ImageJ ij = IJ.getInstance();
				Point ijLoc = ij!=null?ij.getLocation():null;
				//System.out.println("getLoc: "+key+" "+(ijLoc!=null&&primaryScreen.contains(ijLoc)) + "  "+!primaryScreen.contains(p));
				if ((ijLoc!=null&&primaryScreen.contains(ijLoc)) && !primaryScreen.contains(p))
					return null; // return null if "ImageJ" window on primary screen and this location is not
			}
			return p;
		} else
			return null;
	}

	/** Save plugin preferences. */
	static void savePluginPrefs(Properties prefs) {
		Enumeration e = ijPrefs.keys();
		while (e.hasMoreElements()) {
			String key = (String) e.nextElement();
			if (key.indexOf(KEY_PREFIX) == 0)
				prefs.put(key, ijPrefs.getProperty(key));
		}
	}

	public static void savePrefs(Properties prefs, String path) throws IOException{
		FileOutputStream fos = new FileOutputStream(path);
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		prefs.store(bos, "ImageJ "+ImageJ.VERSION+" Preferences");
		bos.close();
	}
	
	/** Returns the number of threads used by PlugInFilters to process images and stacks. */
	public static int getThreads() {
		if (threads==0) {
			threads = getInt(THREADS, 0);
			int processors = Runtime.getRuntime().availableProcessors();
			if (threads<1 || threads>processors)
				threads = processors;
		}
		return threads;
	}
	
	/** Sets the number of threads (1-32) used by PlugInFilters to process stacks. */
	public static void setThreads(int n) {
		if (n<1) n = 1;
		threads = n;
	}
	
	/** Sets the transparent index (0-255), or set to -1 to disable transparency. */
	public static void setTransparentIndex(int index) {
		if (index<-1 || index>255) index = -1;
		transparentIndex = index;
	}

	/** Returns the transparent index (0-255), or -1 if transparency is disabled. */
	public static int getTransparentIndex() {
		return transparentIndex;
	}
	
	public static Properties getControlPanelProperties() {
		return ijPrefs;
	}
	
	public static String defaultResultsExtension() {
		return get("options.ext", ".csv");
	}
		
	/** Sets the GenericDialog and Command Finder text scale (0.5 to 3.0). */
	public static void setGuiScale(double scale) {
		if (scale>=0.5 && scale<=3.0) {
			guiScale = scale;
			set(GUI_SCALE, guiScale);
			Roi.resetHandleSize();
		}
	}

	/** Returns the GenericDialog and Command Finder text scale. */
	public static double getGuiScale() {
		return guiScale;
	}

	/** Returns the custom properties (IJ_Props.txt) file path. */
	public static String getCustomPropsPath() {
		return propertiesPath;
	}

	/** Returns the custom preferences (IJ_Prefs.txt) file path. */
	public static String getCustomPrefsPath() {
		return preferencesPath;
	}

}

