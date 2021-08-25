package ij;
import ij.process.*;
import ij.util.*;
import ij.gui.ImageWindow;
import ij.plugin.MacroInstaller;
import ij.gui.Toolbar;
import ij.macro.Interpreter;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import java.applet.Applet;
import java.awt.event.*;
import java.util.zip.*;

/**
This class installs and updates ImageJ's menus. Note that menu labels,
even in submenus, must be unique. This is because ImageJ uses a single
hash table for all menu labels. If you look closely, you will see that
File->Import->Text Image... and File->Save As->Text Image... do not use
the same label. One of the labels has an extra space.

@see ImageJ
*/

public class Menus {

	public static final char PLUGINS_MENU = 'p';
	public static final char IMPORT_MENU = 'i';
	public static final char SAVE_AS_MENU = 's';
	public static final char SHORTCUTS_MENU = 'h'; // 'h'=hotkey
	public static final char ABOUT_MENU = 'a';
	public static final char FILTERS_MENU = 'f';
	public static final char TOOLS_MENU = 't';
	public static final char UTILITIES_MENU = 'u';
		
	public static final int WINDOW_MENU_ITEMS = 6; // fixed items at top of Window menu
	
	public static final int NORMAL_RETURN = 0;
	public static final int COMMAND_IN_USE = -1;
	public static final int INVALID_SHORTCUT = -2;
	public static final int SHORTCUT_IN_USE = -3;
	public static final int NOT_INSTALLED = -4;
	public static final int COMMAND_NOT_FOUND = -5;
	
	public static final int MAX_OPEN_RECENT_ITEMS = 15;

	private static Menus instance;
	private static MenuBar mbar;
	private static CheckboxMenuItem gray8Item,gray16Item,gray32Item,
			color256Item,colorRGBItem,RGBStackItem,HSBStackItem,LabStackItem,HSB32Item;
	private static PopupMenu popup;

	private static ImageJ ij;
	private static Applet applet;
	private Hashtable demoImagesTable = new Hashtable();
	private static String ImageJPath, pluginsPath, macrosPath;
	private static Properties menus;
	private static Properties menuSeparators;
	private static Menu pluginsMenu, saveAsMenu, shortcutsMenu, utilitiesMenu, macrosMenu;
	static Menu window, openRecentMenu;
	private static Hashtable pluginsTable;
	
	private static int nPlugins, nMacros;
	private static Hashtable shortcuts;
	private static Hashtable macroShortcuts;
	private static Vector pluginsPrefs; // commands saved in IJ_Prefs
	static int windowMenuItems2; // non-image windows listed in Window menu + separator
	private String error;
	private String jarError;
	private String pluginError;
    private boolean isJarErrorHeading;
	private static boolean installingJars, duplicateCommand;
	private static Vector jarFiles;  // JAR files in plugins folder with "_" in their name
	private Map menuEntry2jarFile = new HashMap();
	private static Vector macroFiles;  // Macros and scripts in the plugins folder
	private static int userPluginsIndex; // First user plugin or submenu in Plugins menu
	private static boolean addSorted;
	private static int defaultFontSize = IJ.isWindows()?15:0;
	private static int fontSize = Prefs.getInt(Prefs.MENU_SIZE, defaultFontSize);
	private static Font menuFont;
	private static double scale = 1.0;

	static boolean jnlp; // true when using Java WebStart
	public static int setMenuBarCount;
		
	Menus(ImageJ ijInstance, Applet appletInstance) {
		ij = ijInstance;
		String title = ij!=null?ij.getTitle():null;
		applet = appletInstance;
		instance = this;
	}

	String addMenuBar() {
		scale = Prefs.getGuiScale();
		if ((scale>=1.5&&scale<2.0) || (scale>=2.5&&scale<3.0))
			scale = (int)Math.round(scale);
		nPlugins = nMacros = userPluginsIndex = 0;
		addSorted = installingJars = duplicateCommand = false;
		error = null;
		mbar = null;
		menus = new Properties();
		pluginsTable = new Hashtable();
		shortcuts = new Hashtable();
		pluginsPrefs = new Vector();
		macroShortcuts = null;
		setupPluginsAndMacrosPaths();
		Menu file = getMenu("File");
		Menu newMenu = getMenu("File>New", true);
		addPlugInItem(file, "Open...", "ij.plugin.Commands(\"open\")", KeyEvent.VK_O, false);
		addPlugInItem(file, "Open Next", "ij.plugin.NextImageOpener", KeyEvent.VK_O, true);
		Menu openSamples = getMenu("File>Open Samples", true);
		openSamples.addSeparator();
		addPlugInItem(openSamples, "Cache Sample Images ", "ij.plugin.URLOpener(\"cache\")", 0, false);
		addOpenRecentSubMenu(file);
		Menu importMenu = getMenu("File>Import", true);		
		Menu showFolderMenu = new Menu("Show Folder");
		file.add(showFolderMenu);
		addPlugInItem(showFolderMenu, "Image", "ij.plugin.SimpleCommands(\"showdirImage\")", 0, false);
		addPlugInItem(showFolderMenu, "Plugins", "ij.plugin.SimpleCommands(\"showdirPlugins\")", 0, false);
		addPlugInItem(showFolderMenu, "Macros", "ij.plugin.SimpleCommands(\"showdirMacros\")", 0, false);
		addPlugInItem(showFolderMenu, "LUTs", "ij.plugin.SimpleCommands(\"showdirLuts\")", 0, false);
		addPlugInItem(showFolderMenu, "ImageJ", "ij.plugin.SimpleCommands(\"showdirImageJ\")", 0, false);
		addPlugInItem(showFolderMenu, "temp", "ij.plugin.SimpleCommands(\"showdirTemp\")", 0, false);
		addPlugInItem(showFolderMenu, "Home", "ij.plugin.SimpleCommands(\"showdirHome\")", 0, false);
		file.addSeparator();
		addPlugInItem(file, "Close", "ij.plugin.Commands(\"close\")", KeyEvent.VK_W, false);
		addPlugInItem(file, "Close All", "ij.plugin.Commands(\"close-all\")", KeyEvent.VK_W, true);
		addPlugInItem(file, "Save", "ij.plugin.Commands(\"save\")", KeyEvent.VK_S, false);
		saveAsMenu = getMenu("File>Save As", true);
		addPlugInItem(file, "Revert", "ij.plugin.Commands(\"revert\")", KeyEvent.VK_R,  true);
		file.addSeparator();
		addPlugInItem(file, "Page Setup...", "ij.plugin.filter.Printer(\"setup\")", 0, false);
		addPlugInItem(file, "Print...", "ij.plugin.filter.Printer(\"print\")", KeyEvent.VK_P, false);
		
		Menu edit = getMenu("Edit");
		addPlugInItem(edit, "Undo", "ij.plugin.Commands(\"undo\")", KeyEvent.VK_Z, false);
		edit.addSeparator();
		addPlugInItem(edit, "Cut", "ij.plugin.Clipboard(\"cut\")", KeyEvent.VK_X, false);
		addPlugInItem(edit, "Copy", "ij.plugin.Clipboard(\"copy\")", KeyEvent.VK_C, false);
		addPlugInItem(edit, "Copy to System", "ij.plugin.Clipboard(\"scopy\")", 0, false);
		addPlugInItem(edit, "Paste", "ij.plugin.Clipboard(\"paste\")", KeyEvent.VK_V, false);
		addPlugInItem(edit, "Paste Control...", "ij.plugin.frame.PasteController", 0, false);
		edit.addSeparator();
		addPlugInItem(edit, "Clear", "ij.plugin.filter.Filler(\"clear\")", 0, false);
		addPlugInItem(edit, "Clear Outside", "ij.plugin.filter.Filler(\"outside\")", 0, false);
		addPlugInItem(edit, "Fill", "ij.plugin.filter.Filler(\"fill\")", KeyEvent.VK_F, false);
		addPlugInItem(edit, "Draw", "ij.plugin.filter.Filler(\"draw\")", KeyEvent.VK_D, false);
		addPlugInItem(edit, "Invert", "ij.plugin.filter.Filters(\"invert\")", KeyEvent.VK_I, true);
		edit.addSeparator();
		getMenu("Edit>Selection", true);
		Menu optionsMenu = getMenu("Edit>Options", true);
		
		Menu image = getMenu("Image");
		Menu imageType = getMenu("Image>Type");
			gray8Item = addCheckboxItem(imageType, "8-bit", "ij.plugin.Converter(\"8-bit\")");
			gray16Item = addCheckboxItem(imageType, "16-bit", "ij.plugin.Converter(\"16-bit\")");
			gray32Item = addCheckboxItem(imageType, "32-bit", "ij.plugin.Converter(\"32-bit\")");
			color256Item = addCheckboxItem(imageType, "8-bit Color", "ij.plugin.Converter(\"8-bit Color\")");
			colorRGBItem = addCheckboxItem(imageType, "RGB Color", "ij.plugin.Converter(\"RGB Color\")");
			imageType.add(new MenuItem("-"));
			RGBStackItem = addCheckboxItem(imageType, "RGB Stack", "ij.plugin.Converter(\"RGB Stack\")");
			HSBStackItem = addCheckboxItem(imageType, "HSB Stack", "ij.plugin.Converter(\"HSB Stack\")");
			HSB32Item = addCheckboxItem(imageType, "HSB (32-bit)", "ij.plugin.Converter(\"HSB (32-bit)\")");
			LabStackItem = addCheckboxItem(imageType, "Lab Stack", "ij.plugin.Converter(\"Lab Stack\")");
			image.add(imageType);
			
		image.addSeparator();
		getMenu("Image>Adjust", true);
		addPlugInItem(image, "Show Info...", "ij.plugin.ImageInfo", KeyEvent.VK_I, false);
		addPlugInItem(image, "Properties...", "ij.plugin.filter.ImageProperties", KeyEvent.VK_P, true);
		getMenu("Image>Color", true);
		getMenu("Image>Stacks", true);
		getMenu("Image>Stacks>Animation_", true);
		getMenu("Image>Stacks>Tools_", true);
		Menu hyperstacksMenu = getMenu("Image>Hyperstacks", true);
		image.addSeparator();
		addPlugInItem(image, "Crop", "ij.plugin.Resizer(\"crop\")", KeyEvent.VK_X, true);
		addPlugInItem(image, "Duplicate...", "ij.plugin.Duplicator", KeyEvent.VK_D, true);
		addPlugInItem(image, "Rename...", "ij.plugin.SimpleCommands(\"rename\")", 0, false);
		addPlugInItem(image, "Scale...", "ij.plugin.Scaler", KeyEvent.VK_E, false);
		getMenu("Image>Transform", true);
		getMenu("Image>Zoom", true);
		getMenu("Image>Overlay", true);
		image.addSeparator();
		getMenu("Image>Lookup Tables", true);
		
		Menu process = getMenu("Process");
		addPlugInItem(process, "Smooth", "ij.plugin.filter.Filters(\"smooth\")", KeyEvent.VK_S, true);
		addPlugInItem(process, "Sharpen", "ij.plugin.filter.Filters(\"sharpen\")", 0, false);
		addPlugInItem(process, "Find Edges", "ij.plugin.filter.Filters(\"edge\")", 0, false);
		addPlugInItem(process, "Find Maxima...", "ij.plugin.filter.MaximumFinder", 0, false);
		addPlugInItem(process, "Enhance Contrast...", "ij.plugin.ContrastEnhancer", 0, false);
		getMenu("Process>Noise", true);
		getMenu("Process>Shadows", true);
		getMenu("Process>Binary", true);
		getMenu("Process>Math", true);
		getMenu("Process>FFT", true);
		Menu filtersMenu = getMenu("Process>Filters", true);
		process.addSeparator();
		getMenu("Process>Batch", true);
		addPlugInItem(process, "Image Calculator...", "ij.plugin.ImageCalculator", 0, false);
		addPlugInItem(process, "Subtract Background...", "ij.plugin.filter.BackgroundSubtracter", 0, false);
		addItem(process, "Repeat Command", KeyEvent.VK_R, false);
		
		Menu analyzeMenu = getMenu("Analyze");
		addPlugInItem(analyzeMenu, "Measure", "ij.plugin.filter.Analyzer", KeyEvent.VK_M, false);
		addPlugInItem(analyzeMenu, "Analyze Particles...", "ij.plugin.filter.ParticleAnalyzer", 0, false);
		addPlugInItem(analyzeMenu, "Summarize", "ij.plugin.filter.Analyzer(\"sum\")", 0, false);
		addPlugInItem(analyzeMenu, "Distribution...", "ij.plugin.Distribution", 0, false);
		addPlugInItem(analyzeMenu, "Label", "ij.plugin.filter.Filler(\"label\")", 0, false);
		addPlugInItem(analyzeMenu, "Clear Results", "ij.plugin.filter.Analyzer(\"clear\")", 0, false);
		addPlugInItem(analyzeMenu, "Set Measurements...", "ij.plugin.filter.Analyzer(\"set\")", 0, false);
		analyzeMenu.addSeparator();
		addPlugInItem(analyzeMenu, "Set Scale...", "ij.plugin.filter.ScaleDialog", 0, false);
		addPlugInItem(analyzeMenu, "Calibrate...", "ij.plugin.filter.Calibrator", 0, false);
		if (IJ.isMacOSX()) {
			addPlugInItem(analyzeMenu, "Histogram", "ij.plugin.Histogram", 0, false);
			shortcuts.put(new Integer(KeyEvent.VK_H),"Histogram");
		} else
			addPlugInItem(analyzeMenu, "Histogram", "ij.plugin.Histogram", KeyEvent.VK_H, false);
		addPlugInItem(analyzeMenu, "Plot Profile", "ij.plugin.Profiler(\"plot\")", KeyEvent.VK_K, false);
		addPlugInItem(analyzeMenu, "Surface Plot...", "ij.plugin.SurfacePlotter", 0, false);
		getMenu("Analyze>Gels", true);
		Menu toolsMenu = getMenu("Analyze>Tools", true);

		// the plugins will be added later, after a separator
		addPluginsMenu();

		Menu window = getMenu("Window");
		addPlugInItem(window, "Show All", "ij.plugin.WindowOrganizer(\"show\")", KeyEvent.VK_CLOSE_BRACKET, false);
		String key = IJ.isWindows()?"enter":"return";
		addPlugInItem(window, "Main Window ["+key+"]", "ij.plugin.WindowOrganizer(\"imagej\")", 0, false);
		addPlugInItem(window, "Put Behind [tab]", "ij.plugin.Commands(\"tab\")", 0, false);
		addPlugInItem(window, "Cascade", "ij.plugin.WindowOrganizer(\"cascade\")", 0, false);
		addPlugInItem(window, "Tile", "ij.plugin.WindowOrganizer(\"tile\")", 0, false);
		window.addSeparator();

		Menu help = getMenu("Help");
		addPlugInItem(help, "ImageJ Website...", "ij.plugin.BrowserLauncher", 0, false);
		help.addSeparator();
		addPlugInItem(help, "Dev. Resources...", "ij.plugin.BrowserLauncher(\""+IJ.URL+"/developer/index.html\")", 0, false);
		addPlugInItem(help, "Plugins...", "ij.plugin.BrowserLauncher(\""+IJ.URL+"/plugins\")", 0, false);
		addPlugInItem(help, "Macros...", "ij.plugin.BrowserLauncher(\""+IJ.URL+"/macros/\")", 0, false);
		addPlugInItem(help, "Macro Functions...", "ij.plugin.BrowserLauncher(\""+IJ.URL+"/developer/macro/functions.html\")", 0, false);
		Menu examplesMenu = getExamplesMenu(ij);
		addPlugInItem(examplesMenu, "Open as Panel", "ij.plugin.SimpleCommands(\"opencp\")", 0, false);
		help.add(examplesMenu);
		help.addSeparator();
		addPlugInItem(help, "Update ImageJ...", "ij.plugin.ImageJ_Updater", 0, false);
		addPlugInItem(help, "Refresh Menus", "ij.plugin.ImageJ_Updater(\"menus\")", 0, false);
		help.addSeparator();
		Menu aboutMenu = getMenu("Help>About Plugins", true);
		addPlugInItem(help, "About ImageJ...", "ij.plugin.AboutBox", 0, false);
				
		if (applet==null) {
			menuSeparators = new Properties();
			installPlugins();
		}

		// make	sure "Quit" is the last item in the File menu
		file.addSeparator();
		addPlugInItem(file, "Quit", "ij.plugin.Commands(\"quit\")", 0, false);

		//System.out.println("MenuBar.setFont: "+fontSize+" "+scale+"  "+getFont());
		if (fontSize!=0 || scale>1.0)
			mbar.setFont(getFont());
		if (ij!=null) {
			ij.setMenuBar(mbar);
			Menus.setMenuBarCount++;
		}
		
		// Add deleted sample images to commands table
		pluginsTable.put("Lena (68K)", "ij.plugin.URLOpener(\"lena-std.tif\")");
		pluginsTable.put("Bridge (174K)", "ij.plugin.URLOpener(\"bridge.gif\")");
		
		if (pluginError!=null)
			error = error!=null?error+="\n"+pluginError:pluginError;
		if (jarError!=null)
			error = error!=null?error+="\n"+jarError:jarError;
		return error;
	}
	
	public static Menu getExamplesMenu(ActionListener listener) {
		Menu menu = new Menu("Examples");
		Menu submenu = new Menu("Plots");
		addExample(submenu, "Example Plot", "Example_Plot_.ijm");
		addExample(submenu, "Semi-log Plot", "Semi-log_Plot_.ijm");
		addExample(submenu, "Arrow Plot", "Arrow_Plot_.ijm");
		addExample(submenu, "Damped Wave Plot", "Damped_Wave_Plot_.ijm");
		addExample(submenu, "Dynamic Plot", "Dynamic_Plot_.ijm");
		addExample(submenu, "Dynamic Plot 2D", "Dynamic_Plot_2D_.ijm");
		addExample(submenu, "Custom Plot Symbols", "Custom_Plot_Symbols_.ijm");
		addExample(submenu, "Histograms", "Histograms_.ijm");
		addExample(submenu, "Bar Charts", "Bar_Charts_.ijm");
		addExample(submenu, "Shapes", "Plot_Shapes_.ijm");
		addExample(submenu, "Plot Styles", "Plot_Styles_.ijm");
		addExample(submenu, "Random Data", "Random_Data_.ijm");
		addExample(submenu, "Plot Results", "Plot_Results_.ijm");
		submenu.addActionListener(listener);
		menu.add(submenu);
		
		submenu = new Menu("Tools");
		addExample(submenu, "Annular Selection", "Annular_Selection_Tool.ijm");		
		addExample(submenu, "Big Cursor", "Big_Cursor_Tool.ijm");		
		addExample(submenu, "Circle Tool", "Circle_Tool.ijm");
		addExample(submenu, "Point Picker", "Point_Picker_Tool.ijm");		
		addExample(submenu, "Star Tool", "Star_Tool.ijm");
		addExample(submenu, "Animated Icon Tool", "Animated_Icon_Tool.ijm");
		submenu.addActionListener(listener);
		menu.add(submenu);

		submenu = new Menu("Macro");
		addExample(submenu, "Sphere", "Sphere.ijm");
		addExample(submenu, "Dialog Box", "Dialog_Box.ijm");
		addExample(submenu, "Process Folder", "Batch_Process_Folder.ijm");
		addExample(submenu, "OpenDialog Demo", "OpenDialog_Demo.ijm");
		addExample(submenu, "Save All Images", "Save_All_Images.ijm");
		addExample(submenu, "Sine/Cosine Table", "Sine_Cosine_Table.ijm");
		addExample(submenu, "Non-numeric Table", "Non-numeric_Table.ijm");
		addExample(submenu, "Overlay", "Overlay.ijm");
		addExample(submenu, "Stack Overlay", "Stack_Overlay.ijm");
		addExample(submenu, "Array Functions", "Array_Functions.ijm");
		addExample(submenu, "Dual Progress Bars", "Dual_Progress_Bars.ijm");
		addExample(submenu, "Grab Viridis Colormap", "Grab_Viridis_Colormap.ijm");
		addExample(submenu, "Custom Measurement", "Custom_Measurement.ijm");
		addExample(submenu, "Synthetic Images", "Synthetic_Images.ijm");
		addExample(submenu, "Spiral Rotation", "Spiral_Rotation.ijm");
		addExample(submenu, "Curve Fitting", "Curve_Fitting.ijm");
		addExample(submenu, "Colors of 2021", "Colors_of_2021.ijm");
		submenu.addActionListener(listener);
		menu.add(submenu);

		submenu = new Menu("JavaScript");
		addExample(submenu, "Sphere", "Sphere.js");
		addExample(submenu, "Plasma Cloud", "Plasma_Cloud.js");
		addExample(submenu, "Cloud Debugger", "Cloud_Debugger.js");
		addExample(submenu, "Synthetic Images", "Synthetic_Images.js");
		addExample(submenu, "Points", "Points.js");
		addExample(submenu, "Spiral Rotation", "Spiral_Rotation.js");
		addExample(submenu, "Example Plot", "Example_Plot.js");
		addExample(submenu, "Semi-log Plot", "Semi-log_Plot.js");
		addExample(submenu, "Arrow Plot", "Arrow_Plot.js");
		addExample(submenu, "Dynamic Plot", "Dynamic_Plot.js");
		addExample(submenu, "Plot Styles", "Plot_Styles.js");
		addExample(submenu, "Plot Random Data", "Plot_Random_Data.js");
		addExample(submenu, "Histogram Plots", "Histogram_Plots.js");
		addExample(submenu, "JPEG Quality Plot", "JPEG_Quality_Plot.js");
		addExample(submenu, "Process Folder", "Batch_Process_Folder.js");
		addExample(submenu, "Sine/Cosine Table", "Sine_Cosine_Table.js");
		addExample(submenu, "Non-numeric Table", "Non-numeric_Table.js");
		addExample(submenu, "Overlay", "Overlay.js");
		addExample(submenu, "Stack Overlay", "Stack_Overlay.js");
		addExample(submenu, "Dual Progress Bars", "Dual_Progress_Bars.js");
		addExample(submenu, "Gamma Adjuster", "Gamma_Adjuster.js");
		addExample(submenu, "Custom Measurement", "Custom_Measurement.js");
		addExample(submenu, "Terabyte VirtualStack", "Terabyte_VirtualStack.js");
		addExample(submenu, "Event Listener", "Event_Listener.js");
		addExample(submenu, "FFT Filter", "FFT_Filter.js");
		addExample(submenu, "Curve Fitting", "Curve_Fitting.js");
		addExample(submenu, "Overlay Text", "Overlay_Text.js");
		addExample(submenu, "Crop Multiple Rois", "Crop_Multiple_Rois.js");
		addExample(submenu, "Show all LUTs", "Show_all_LUTs.js");
		addExample(submenu, "Dialog Demo", "Dialog_Demo.js");
		submenu.addActionListener(listener);
		menu.add(submenu);
		submenu = new Menu("BeanShell");
		addExample(submenu, "Sphere", "Sphere.bsh");
		addExample(submenu, "Example Plot", "Example_Plot.bsh");
		addExample(submenu, "Semi-log Plot", "Semi-log_Plot.bsh");
		addExample(submenu, "Arrow Plot", "Arrow_Plot.bsh");
		addExample(submenu, "Sine/Cosine Table", "Sine_Cosine_Table.bsh");
		submenu.addActionListener(listener);
		menu.add(submenu);
		submenu = new Menu("Python");
		addExample(submenu, "Sphere", "Sphere.py");
		addExample(submenu, "Animated Gaussian Blur", "Animated_Gaussian_Blur.py");
		addExample(submenu, "Spiral Rotation", "Spiral_Rotation.py");
		addExample(submenu, "Overlay", "Overlay.py");
		submenu.addActionListener(listener);
		menu.add(submenu);
		submenu = new Menu("Java");
		addExample(submenu, "Sphere", "Sphere_.java");
		addExample(submenu, "Plasma Cloud", "Plasma_Cloud.java");
		addExample(submenu, "Gamma Adjuster", "Gamma_Adjuster.java");
		addExample(submenu, "Plugin", "My_Plugin.java");
		addExample(submenu, "Plugin Filter", "Filter_Plugin.java");
		addExample(submenu, "Plugin Frame", "Plugin_Frame.java");
		addExample(submenu, "Plugin Tool", "Prototype_Tool.java");
		submenu.addActionListener(listener);
		menu.add(submenu);
		menu.addSeparator();
		CheckboxMenuItem item = new CheckboxMenuItem("Autorun Examples");
		menu.add(item);
		item.addItemListener(ij);
		item.setState(Prefs.autoRunExamples);
		return menu;
	}
	
	private static void addExample(Menu menu, String label, String command) {
		MenuItem item = new MenuItem(label);
		menu.add(item);
		item.setActionCommand(command);
	}

	void addOpenRecentSubMenu(Menu menu) {
		openRecentMenu = getMenu("File>Open Recent");
 		for (int i=0; i<MAX_OPEN_RECENT_ITEMS; i++) {
			String path = Prefs.getString("recent" + (i/10)%10 + i%10);
			if (path==null) break;
			MenuItem item = new MenuItem(path);
			openRecentMenu.add(item);
			item.addActionListener(ij);
		}
		menu.add(openRecentMenu);
	}

	static void addItem(Menu menu, String label, int shortcut, boolean shift) {
		if (menu==null)
			return;
		MenuItem item;
		if (shortcut==0)
			item = new MenuItem(label);
		else {
			if (shift) {
				item = new MenuItem(label, new MenuShortcut(shortcut, true));
				shortcuts.put(new Integer(shortcut+200),label);
			} else {
				item = new MenuItem(label, new MenuShortcut(shortcut));
				shortcuts.put(new Integer(shortcut),label);
			}
		}
		if (addSorted) {
			if (menu==pluginsMenu)
				addItemSorted(menu, item, userPluginsIndex);
			else
				addOrdered(menu, item);
		} else
			menu.add(item);
		item.addActionListener(ij);
	}
	
	void addPlugInItem(Menu menu, String label, String className, int shortcut, boolean shift) {
		pluginsTable.put(label, className);
		nPlugins++;
		addItem(menu, label, shortcut, shift);
	}

	CheckboxMenuItem addCheckboxItem(Menu menu, String label, String className) {
		pluginsTable.put(label, className);
		nPlugins++;
		CheckboxMenuItem item = new CheckboxMenuItem(label);
		menu.add(item);
		item.addItemListener(ij);
		item.setState(false);
		return item;
	}

	static Menu addSubMenu(Menu menu, String name) {
		String value;
		String key = name.toLowerCase(Locale.US);
		int index;
 		Menu submenu=new Menu(name.replace('_', ' '));
		index = key.indexOf(' ');
		if (index>0)
			key = key.substring(0, index);
 		for (int count=1; count<100; count++) {
			value = Prefs.getString(key + (count/10)%10 + count%10);
			if (value==null)
				break;
			if (count==1)
				menu.add(submenu);
			if (value.equals("-"))
				submenu.addSeparator();
			else
				addPluginItem(submenu, value);
		}
		if (name.equals("Lookup Tables") && applet==null)
			addLuts(submenu);
		return submenu;
	}
	
	static void addLuts(Menu submenu) {
		String path = IJ.getDirectory("luts");
		if (path==null) return;
		File f = new File(path);
		String[] list = null;
		if (applet==null && f.exists() && f.isDirectory())
			list = f.list();
		if (list==null) return;
		if (IJ.isLinux() || IJ.isMacOSX())
			Arrays.sort(list);
		submenu.addSeparator();
 		for (int i=0; i<list.length; i++) {
 			String name = list[i];
 			if (name.endsWith(".lut")) {
 				name = name.substring(0,name.length()-4);
 				if (name.contains("_") && !name.contains(" "))
 					name = name.replace("_", " ");
 				MenuItem item = new MenuItem(name);
				submenu.add(item);
				item.addActionListener(ij);
				nPlugins++;
			}
		}
	}

	static void addPluginItem(Menu submenu, String s) {
		if (s.startsWith("\"-\"")) {
			// add menu separator if command="-"
			addSeparator(submenu);
			return;
		}
		int lastComma = s.lastIndexOf(',');
		if (lastComma<=0)
			return;
		String command = s.substring(1,lastComma-1);
		int keyCode = 0;
		boolean shift = false;
		if (command.endsWith("]")) {
			int openBracket = command.lastIndexOf('[');
			if (openBracket>0) {
				String shortcut = command.substring(openBracket+1,command.length()-1);
				keyCode = convertShortcutToCode(shortcut);
				boolean functionKey = keyCode>=KeyEvent.VK_F1 && keyCode<=KeyEvent.VK_F12;
				if (keyCode>0 && !functionKey)
					command = command.substring(0,openBracket);
			}
		}
		if (keyCode>=KeyEvent.VK_F1 && keyCode<=KeyEvent.VK_F12) {
			shortcuts.put(new Integer(keyCode),command);
			keyCode = 0;
		} else if (keyCode>=265 && keyCode<=290) {
			keyCode -= 200;
			shift = true;
		}
		addItem(submenu,command,keyCode,shift);
		while(s.charAt(lastComma+1)==' ' && lastComma+2<s.length())
			lastComma++; // remove leading spaces
		String className = s.substring(lastComma+1,s.length());
		//IJ.log(command+"  "+className);
		if (installingJars)
			duplicateCommand = pluginsTable.get(command)!=null;
		pluginsTable.put(command, className);
		nPlugins++;
	}

	void checkForDuplicate(String command) {
		if (pluginsTable.get(command)!=null) {
		}
	}
	
	void addPluginsMenu() {
		String value,label,className;
		int index;
		//pluginsMenu = new Menu("Plugins");
		pluginsMenu = getMenu("Plugins");
		for (int count=1; count<100; count++) {
			value = Prefs.getString("plug-in" + (count/10)%10 + count%10);
			if (value==null)
				break;
			char firstChar = value.charAt(0);
			if (firstChar=='-')
				pluginsMenu.addSeparator();
			else if (firstChar=='>') {
				String submenu = value.substring(2,value.length()-1);
				//Menu menu = getMenu("Plugins>" + submenu, true);
				Menu menu = addSubMenu(pluginsMenu, submenu);
				if (submenu.equals("Shortcuts"))
					shortcutsMenu = menu;
				else if (submenu.equals("Utilities"))
					utilitiesMenu = menu;
				else if (submenu.equals("Macros"))
					macrosMenu = menu;
			} else
				addPluginItem(pluginsMenu, value);
		}
		userPluginsIndex = pluginsMenu.getItemCount();
		if (userPluginsIndex<0) userPluginsIndex = 0;
	}

	/** Install plugins using "pluginxx=" keys in IJ_Prefs.txt.
		Plugins not listed in IJ_Prefs are added to the end
		of the Plugins menu. */
	void installPlugins() {
		int nPlugins0 = nPlugins;
		String value, className;
		char menuCode;
		Menu menu;
		String[] pluginList = getPlugins();
		String[] pluginsList2 = null;
		Hashtable skipList = new Hashtable();
 		for (int index=0; index<100; index++) {
			value = Prefs.getString("plugin" + (index/10)%10 + index%10);
			if (value==null)
				break;
			menuCode = value.charAt(0);
			switch (menuCode) {
				case PLUGINS_MENU: default: menu = pluginsMenu; break;
				case IMPORT_MENU: menu = getMenu("File>Import"); break;
				case SAVE_AS_MENU: menu = getMenu("File>Save As"); break;
				case SHORTCUTS_MENU: menu = shortcutsMenu; break;
				case ABOUT_MENU: menu = getMenu("Help>About Plugins"); break;
				case FILTERS_MENU: menu = getMenu("Process>Filters"); break;
				case TOOLS_MENU: menu = getMenu("Analyze>Tools"); break;
				case UTILITIES_MENU: menu = utilitiesMenu; break;
			}
			String prefsValue = value;
			value = value.substring(2,value.length()); //remove menu code and coma
			className = value.substring(value.lastIndexOf(',')+1,value.length());
			boolean found = className.startsWith("ij.");
			if (!found && pluginList!=null) { // does this plugin exist?
				if (pluginsList2==null)
					pluginsList2 = getStrippedPlugins(pluginList);
				for (int i=0; i<pluginsList2.length; i++) {
					if (className.startsWith(pluginsList2[i])) {
						found = true;
						break;
					}
				}
			}
			if (found && menu!=pluginsMenu) {
				addPluginItem(menu, value);
				pluginsPrefs.addElement(prefsValue);
				if (className.endsWith("\")")) { // remove any argument
					int argStart = className.lastIndexOf("(\"");
					if (argStart>0)
						className = className.substring(0, argStart);
				}
				skipList.put(className, "");
			}
		}
		if (pluginList!=null) {
			for (int i=0; i<pluginList.length; i++) {
				if (!skipList.containsKey(pluginList[i]))
					installUserPlugin(pluginList[i]);
			}
		}
		if ((nPlugins-nPlugins0)<=1 && IJ.getDir("imagej")!=null && IJ.getDir("imagej").startsWith("/private")) {
			pluginsMenu.addSeparator();
			addPlugInItem(pluginsMenu, "Why are Plugins Missing?", "ij.plugin.SimpleCommands(\"missing\")", 0, false);
		}
		installJarPlugins();
		installMacros();
	}
	
	/** Installs macros and scripts located in the plugins folder. */
	void installMacros() {
		if (macroFiles==null)
			return;
		for (int i=0; i<macroFiles.size(); i++) {
			String name = (String)macroFiles.elementAt(i);
			installMacro(name);
		}		
	}

	/** Installs a macro or script in the Plugins menu, or submenu, with
		with underscores in the file name replaced by spaces. */
	void installMacro(String name) {
		Menu menu = pluginsMenu;
		String dir = null;
		int slashIndex = name.indexOf('/');
		if (slashIndex>0) {
			dir = name.substring(0, slashIndex);
			name = name.substring(slashIndex+1, name.length());
			menu = getPluginsSubmenu(dir);
			slashIndex = name.indexOf('/');
			if (slashIndex>0) {
				String dir2 = name.substring(0, slashIndex);
				name = name.substring(slashIndex+1, name.length());
				String menuName = "Plugins>"+dir+">"+dir2;
				menu = getMenu(menuName);
				dir += File.separator+dir2;
			}
		}
		String command = name.replace('_',' ');
		if (command.endsWith(".js")||command.endsWith(".py"))
			command = command.substring(0, command.length()-3); //remove ".js" or ".py"
		else
			command = command.substring(0, command.length()-4); //remove ".txt", ".ijm" or ".bsh"
		command.trim();
		if (pluginsTable.get(command)!=null) // duplicate command?
			command = command + " Macro";
		MenuItem item = new MenuItem(command);
		addOrdered(menu, item);
		item.addActionListener(ij);
		String path = (dir!=null?dir+File.separator:"") + name;
		pluginsTable.put(command, "ij.plugin.Macro_Runner(\""+path+"\")");
		nMacros++;
	}

	static int addPluginSeparatorIfNeeded(Menu menu) {
		if (menuSeparators == null)
			return 0;
		Integer i = (Integer)menuSeparators.get(menu);
		if (i == null) {
			if (menu.getItemCount() > 0)
				addSeparator(menu);
			i = new Integer(menu.getItemCount());
			menuSeparators.put(menu, i);
		}
		return i.intValue();
	}

	/** Inserts 'item' into 'menu' in alphanumeric order. */
	static void addOrdered(Menu menu, MenuItem item) {
		String label = item.getLabel();
		int start = addPluginSeparatorIfNeeded(menu);
		for (int i=start; i<menu.getItemCount(); i++) {
			if (label.compareTo(menu.getItem(i).getLabel())<0) {
				menu.insert(item, i);
				return;
			}
		}
		menu.add(item);
	}
	
	public static String getJarFileForMenuEntry(String menuEntry) {
		if (instance == null)
			return null;
		return (String)instance.menuEntry2jarFile.get(menuEntry);
	}

	/** Install plugins located in JAR files. */
	void installJarPlugins() {
		if (jarFiles==null)
			return;
		installingJars = true;
		for (int i=0; i<jarFiles.size(); i++) {
            isJarErrorHeading = false;
			String jar = (String)jarFiles.elementAt(i);
			InputStream is = getConfigurationFile(jar);
            if (is==null) continue;
            ArrayList entries = new ArrayList(20);
            LineNumberReader lnr = new LineNumberReader(new InputStreamReader(is));
            try {
                while(true) {
                    String s = lnr.readLine();
                    if (s==null) break;
					if (s.length()>=3 && !s.startsWith("#"))
						entries.add(s);
	            }
            }
            catch (IOException e) {}
			finally {
				try {if (lnr!=null) lnr.close();}
				catch (IOException e) {}
			}
			for (int j=0; j<entries.size(); j++)
				installJarPlugin(jar, (String)entries.get(j));
		}		
	}
    
    /** Install a plugin located in a JAR file. */
	void installJarPlugin(String jar, String s) {
		addSorted = false;
		Menu menu;
		s = s.trim();
		if (s.startsWith("Plugins>")) {
			int firstComma = s.indexOf(',');
			if (firstComma==-1 || firstComma<=8)
				menu = null;
			else {
				String name = s.substring(8, firstComma);
				menu = getPluginsSubmenu(name);
			}
		} else if (s.startsWith("\"") || s.startsWith("Plugins")) {
			String name = getSubmenuName(jar);
			if (name!=null)
				menu = getPluginsSubmenu(name);
			else
				menu = pluginsMenu;
			addSorted = true;
		} else {
			int firstQuote = s.indexOf('"');
			String name = firstQuote<0 ? s : s.substring(0, firstQuote).trim();
			int comma = name.indexOf(',');
			if (comma >= 0)
				name = name.substring(0, comma);
			if (name.startsWith("Help>About")) // for backward compatibility
				name = "Help>About Plugins";
			menu = getMenu(name);
		}
		int firstQuote = s.indexOf('"');
		if (firstQuote==-1)
			return;
		s = s.substring(firstQuote, s.length()); // remove menu
		if (menu!=null) {
			addPluginSeparatorIfNeeded(menu);
            addPluginItem(menu, s);
            addSorted = false;
        }
		String menuEntry = s;
		if (s.startsWith("\"")) {
			int quote = s.indexOf('"', 1);
			menuEntry = quote<0?s.substring(1):s.substring(1, quote);
		} else {
			int comma = s.indexOf(',');
			if (comma > 0)
				menuEntry = s.substring(0, comma);
		}
		if (duplicateCommand) {
			if (jarError==null) jarError = "";
            addJarErrorHeading(jar);
			String jar2 = (String)menuEntry2jarFile.get(menuEntry);
			if (jar2 != null && jar2.startsWith(pluginsPath))
				jar2 = jar2.substring(pluginsPath.length());
			jarError += "    Duplicate command: " + s
				+ (jar2 != null ? " (already in " + jar2 + ")"
				   : "") + "\n";
		} else
			menuEntry2jarFile.put(menuEntry, jar);
		duplicateCommand = false;
    }
    
    void addJarErrorHeading(String jar) {
        if (!isJarErrorHeading) {
                if (!jarError.equals(""))
                    jarError += " \n";
                jarError += "Plugin configuration error: " + jar + "\n";
                isJarErrorHeading = true;
            }
    }

	/** Returns the specified ImageJ menu (e.g., "File>New") or null if it is not found. */
	public static Menu getImageJMenu(String menuPath) {
		if (menus==null)
			IJ.init();
		if (menus==null)
			return null;
		if (menus.get(menuPath)!=null)
			return getMenu(menuPath, false);
		else
			return null;
	}

	private static Menu getMenu(String menuPath) {
		return getMenu(menuPath, false);
	}

	private static Menu getMenu(String menuName, boolean readFromProps) {
		if (menuName.endsWith(">"))
			menuName = menuName.substring(0, menuName.length() - 1);
		Menu result = (Menu)menus.get(menuName);
		if (result==null) {
			int offset = menuName.lastIndexOf('>');
			if (offset < 0) {
				result = new Menu(menuName);
				if (mbar == null)
					mbar = new MenuBar();
				if (menuName.equals("Help"))
					mbar.setHelpMenu(result);
				else
					mbar.add(result);
				if (menuName.equals("Window"))
					window = result;
				else if (menuName.equals("Plugins"))
					pluginsMenu = result;
			} else {
				String parentName = menuName.substring(0, offset);
				String menuItemName = menuName.substring(offset + 1);
				Menu parentMenu = getMenu(parentName);
				result = new Menu(menuItemName);
				addPluginSeparatorIfNeeded(parentMenu);
				if (readFromProps)
					result = addSubMenu(parentMenu, menuItemName);
				else if (parentName.startsWith("Plugins") && menuSeparators != null)
					addItemSorted(parentMenu, result, parentName.equals("Plugins")?userPluginsIndex:0);
				else
					parentMenu.add(result);
				if (menuName.equals("File>Open Recent"))
					openRecentMenu = result;
			}
			menus.put(menuName, result);
		}
		return result;
	}

	Menu getPluginsSubmenu(String submenuName) {
		return getMenu("Plugins>" + submenuName);
    }
    
	String getSubmenuName(String jarPath) {
		//IJ.log("getSubmenuName: \n"+jarPath+"\n"+pluginsPath);
		if (pluginsPath == null)
			return null;
		if (jarPath.startsWith(pluginsPath))
			jarPath = jarPath.substring(pluginsPath.length() - 1);
		int index = jarPath.lastIndexOf(File.separatorChar);
		if (index<0) return null;
		String name = jarPath.substring(0, index);
		index = name.lastIndexOf(File.separatorChar);
		if (index<0) return null;
		name = name.substring(index+1);
		if (name.equals("plugins")) return null;
		return name;
    }

	static void addItemSorted(Menu menu, MenuItem item, int startingIndex) {
		String itemLabel = item.getLabel();
		int count = menu.getItemCount();
		boolean inserted = false;
		for (int i=startingIndex; i<count; i++) {
			MenuItem mi = menu.getItem(i);
			String label = mi.getLabel();
			//IJ.log(i+ "  "+itemLabel+"  "+label + "  "+(itemLabel.compareTo(label)));
			if (itemLabel.compareTo(label)<0) {
				menu.insert(item, i);
				inserted = true;
				break;
			}
		}
		if (!inserted) menu.add(item);
	}

    static void addSeparator(Menu menu) {
    	menu.addSeparator();
    }

    /** Opens the configuration file ("plugins.config") from a JAR file and returns it as an InputStream. */
	InputStream getConfigurationFile(String jar) {
		try {
			ZipFile jarFile = new ZipFile(jar);
			Enumeration entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = (ZipEntry) entries.nextElement();
        		if (entry.getName().endsWith("plugins.config"))
					return jarFile.getInputStream(entry);
			}
		}
    	catch (Throwable e) {
    		IJ.log(jar+": "+e);
    	}
		return autoGenerateConfigFile(jar);
	}
	
    /** Creates a configuration file for JAR/ZIP files that do not have one. */
	InputStream autoGenerateConfigFile(String jar) {
		StringBuffer sb = null;
		try {
			ZipFile jarFile = new ZipFile(jar);
			Enumeration entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = (ZipEntry) entries.nextElement();
				String name = entry.getName();
				if (name.endsWith(".class") && name.indexOf("_")>0 && name.indexOf("$")==-1
				&& name.indexOf("/_")==-1 && !name.startsWith("_")) {
					if (Character.isLowerCase(name.charAt(0))&&name.indexOf("/")!=-1)
						continue;
					if (sb==null) sb = new StringBuffer();
					String className = name.substring(0, name.length()-6);
					int slashIndex = className.lastIndexOf('/');
					String plugins = "Plugins";
					if (slashIndex >= 0) {
						plugins += ">" + className.substring(0, slashIndex).replace('/', '>').replace('_', ' ');
						name = className.substring(slashIndex + 1);
					} else
						name = className;
					name = name.replace('_', ' ');
					className = className.replace('/', '.');
					//if (className.indexOf(".")==-1 || Character.isUpperCase(className.charAt(0)))
					sb.append(plugins + ", \""+name+"\", "+className+"\n");
				}
			}
		}
    	catch (Throwable e) {
    		IJ.log(jar+": "+e);
    	}
		//IJ.log(""+(sb!=null?sb.toString():"null"));
		if (sb==null)
			return null;
		else
    		return new ByteArrayInputStream(sb.toString().getBytes());
	}
	
	/** Returns a list of the plugins with directory names removed. */
	String[] getStrippedPlugins(String[] plugins) {
		String[] plugins2 = new String[plugins.length];
		int slashPos;
		for (int i=0; i<plugins2.length; i++) {
			plugins2[i] = plugins[i];
			slashPos = plugins2[i].lastIndexOf('/');
			if (slashPos>=0)
				plugins2[i] = plugins[i].substring(slashPos+1,plugins2[i].length());
		}
		return plugins2;
	}
	
	void setupPluginsAndMacrosPaths() {
		ImageJPath = pluginsPath = macrosPath = null;
		String currentDir = Prefs.getHomeDir(); // "user.dir"
		if (currentDir==null)
			return;
		if (currentDir.endsWith("plugins"))
			ImageJPath = pluginsPath = currentDir+File.separator;
		else {
			String pluginsDir = System.getProperty("plugins.dir");
			if (pluginsDir!=null) {
				if (pluginsDir.endsWith("/")||pluginsDir.endsWith("\\"))
					pluginsDir = pluginsDir.substring(0, pluginsDir.length()-1);
				if (pluginsDir.endsWith("/plugins")||pluginsDir.endsWith("\\plugins"))
					pluginsDir = pluginsDir.substring(0, pluginsDir.length()-8);
			}
			if (pluginsDir==null)
				pluginsDir = currentDir;
			else if (pluginsDir.equals("user.home")) {
				pluginsDir = System.getProperty("user.home");
				if (!(new File(pluginsDir+File.separator+"plugins")).isDirectory()) 
					pluginsDir = pluginsDir + File.separator + "ImageJ";
				// needed to run plugins when ImageJ launched using Java WebStart
				if (applet==null)
					System.setSecurityManager(null);
				jnlp = true;
			}
			pluginsPath = pluginsDir+File.separator+"plugins"+File.separator;
			macrosPath = pluginsDir+File.separator+"macros"+File.separator;
			ImageJPath = pluginsDir+File.separator;
		}
		File f = pluginsPath!=null?new File(pluginsPath):null;
		if (f==null || !f.isDirectory()) {
			ImageJPath = currentDir+File.separator;
			pluginsPath = ImageJPath+"plugins"+File.separator;
			f = new File(pluginsPath);
			if (!f.isDirectory()) {
				String altPluginsPath = System.getProperty("plugins.dir");
				if (altPluginsPath!=null) {
					f = new File(altPluginsPath);
					if (!f.isDirectory())
						altPluginsPath = null;
					else {
						ImageJPath = f.getParent() + File.separator;
						pluginsPath = ImageJPath + f.getName() + File.separator;
						macrosPath = ImageJPath+"macros"+File.separator;
					}
				}
				if (altPluginsPath==null)
					ImageJPath = pluginsPath = null;
			}
		}
		f = macrosPath!=null?new File(macrosPath):null;
		if (f!=null && !f.isDirectory()) {
			macrosPath = currentDir+File.separator+"macros"+File.separator;
			f = new File(macrosPath);
			if (!f.isDirectory())
				macrosPath = null;
		}
		if (IJ.debugMode) {
			IJ.log("Menus.setupPluginsAndMacrosPaths");
			IJ.log("   user.dir: "+currentDir);
			IJ.log("   plugins.dir: "+System.getProperty("plugins.dir"));
			IJ.log("   ImageJPath: "+ImageJPath);
			IJ.log("   pluginsPath: "+pluginsPath);
		}
	}
		
	/** Returns a list of the plugins in the plugins menu. */
	public static synchronized String[] getPlugins() {
		File f = pluginsPath!=null?new File(pluginsPath):null;
		if (f==null || (f!=null && !f.isDirectory()))
			return null;
		String[] list = f.list();
		if (list==null)
			return null;
		Vector v = new Vector();
		jarFiles = null;
		macroFiles = null;
		for (int i=0; i<list.length; i++) {
			String name = list[i];
			boolean isClassFile = name.endsWith(".class");
			boolean hasUnderscore = name.indexOf('_')>=0;
			if (hasUnderscore && isClassFile && name.indexOf('$')<0 ) {
				name = name.substring(0, name.length()-6); // remove ".class"
				v.addElement(name);
			} else if (hasUnderscore && (name.endsWith(".jar") || name.endsWith(".zip"))) {
				if (jarFiles==null) jarFiles = new Vector();
				jarFiles.addElement(pluginsPath + name);
			} else if (validMacroName(name,hasUnderscore)) {
				if (macroFiles==null) macroFiles = new Vector();
				macroFiles.addElement(name);
			} else {
				if (!isClassFile)
					checkSubdirectory(pluginsPath, name, v);
			}
		}
		list = new String[v.size()];
		v.copyInto((String[])list);
		StringSorter.sort(list);
		return list;
	}
	
	/** Looks for plugins and jar files in a subdirectory of the plugins directory. */
	private static void checkSubdirectory(String path, String dir, Vector v) {
		if (dir.endsWith(".java"))
			return;
		File f = new File(path, dir);
		if (!f.isDirectory())
			return;
		String[] list = f.list();
		if (list==null)
			return;
		dir += "/";
		int classCount=0, otherCount=0;
		String className = null;
		for (int i=0; i<list.length; i++) {
			String name = list[i];
			boolean hasUnderscore = name.indexOf('_')>=0;
			if (hasUnderscore && name.endsWith(".class") && name.indexOf('$')<0) {
				name = name.substring(0, name.length()-6); // remove ".class"
				v.addElement(dir+name);
				classCount++;
				className = name;
			} else if (hasUnderscore && (name.endsWith(".jar") || name.endsWith(".zip"))) {
				if (jarFiles==null) jarFiles = new Vector();
				jarFiles.addElement(f.getPath() + File.separator + name);
				otherCount++;
			} else if (validMacroName(name,hasUnderscore)) {
				if (macroFiles==null) macroFiles = new Vector();
				macroFiles.addElement(dir + name);
				otherCount++;
			} else {
				File f2 = new File(f, name);
				if (f2.isDirectory()) installSubdirectorMacros(f2, dir+name);
			}
		}
		if (Prefs.moveToMisc && classCount==1 && otherCount==0 && dir.indexOf("_")==-1)
			v.setElementAt("Miscellaneous/" + className,
				v.size() - 1);
	}
	
	/** Installs macros and scripts located in subdirectories. */
	private static void installSubdirectorMacros(File f2, String dir) {
		if (dir.endsWith("Launchers")) return;
		String[] list = f2.list();
		if (list==null) return;
		for (int i=0; i<list.length; i++) {
			String name = list[i];
			boolean hasUnderscore = name.indexOf('_')>=0;
			if (validMacroName(name,hasUnderscore)) {
				if (macroFiles==null) macroFiles = new Vector();
				macroFiles.addElement(dir+"/"+name);
			}
		}
	}
	
	private static boolean validMacroName(String name, boolean hasUnderscore) {
		return (hasUnderscore&&name.endsWith(".txt")) || name.endsWith(".ijm")
			|| name.endsWith(".js") || name.endsWith(".bsh") || name.endsWith(".py");
	}
	
	/** Installs a plugin in the Plugins menu using the class name,
		with underscores replaced by spaces, as the command. */
	void installUserPlugin(String className) {
		installUserPlugin(className, false);
	}

	public void installUserPlugin(String className, boolean force) {
		int slashIndex = className.indexOf('/');
		String menuName = slashIndex < 0 ? "Plugins" : "Plugins>" +
			className.substring(0, slashIndex).replace('/', '>');
		Menu menu = getMenu(menuName);
		String command = className;
		if (slashIndex>0) {
			command = className.substring(slashIndex+1);
		}
		command = command.replace('_',' ');
		command.trim();
		boolean itemExists = (pluginsTable.get(command)!=null);
		if(force && itemExists)
			return;

		if (!force && itemExists)  // duplicate command?
			command = command + " Plugin";
		MenuItem item = new MenuItem(command);
		if(force)
			addItemSorted(menu,item,0);
		else
			addOrdered(menu, item);
		item.addActionListener(ij);
		pluginsTable.put(command, className.replace('/', '.'));
		nPlugins++;
	}
	
	void installPopupMenu(ImageJ ij) {
		String s;
		int count = 0;
		MenuItem mi;
		popup = new PopupMenu("");
		if (fontSize!=0 || scale>1.0)
			popup.setFont(getFont());
		while (true) {
			count++;
			s = Prefs.getString("popup" + (count/10)%10 + count%10);
			if (s==null)
				break;
			if (s.equals("-"))
				popup.addSeparator();
			else if (!s.equals("")) {
				mi = new MenuItem(s);
				mi.addActionListener(ij);
				popup.add(mi);
			}
		}
	}

	public static MenuBar getMenuBar() {
		return mbar;
	}
		
	public static Menu getMacrosMenu() {
		return macrosMenu;
	}

	public static Menu getOpenRecentMenu() {
		return openRecentMenu;
	}

	public int getMacroCount() {
		return nMacros;
	}

	public int getPluginCount() {
		return nPlugins;
	}
		
	static final int RGB_STACK=10, HSB_STACK=11, LAB_STACK=12, HSB32_STACK=13;
	
	/** Updates the Image/Type and Window menus. */
	public static void updateMenus() {
		if (ij==null) return;
		gray8Item.setState(false);
		gray16Item.setState(false);
		gray32Item.setState(false);
		color256Item.setState(false);
		colorRGBItem.setState(false);
		RGBStackItem.setState(false);
		HSBStackItem.setState(false);
		LabStackItem.setState(false);
		HSB32Item.setState(false);
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			return;
    	int type = imp.getType();
     	if (imp.getStackSize()>1) {
    		ImageStack stack = imp.getStack();
    		if (stack.isRGB())
    			type = RGB_STACK;
    		else if (stack.isHSB())
    			type = HSB_STACK;
    		else if (stack.isLab())
    			type = LAB_STACK;
    		else if (stack.isHSB32())
    			type = HSB32_STACK;
    	}
    	switch (type) {
    		case ImagePlus.GRAY8:
				gray8Item.setState(true);
				break;
     		case ImagePlus.GRAY16:
				gray16Item.setState(true);
				break;
     		case ImagePlus.GRAY32:
				gray32Item.setState(true);
				break;
   			case ImagePlus.COLOR_256:
				color256Item.setState(true);
				break;
    		case ImagePlus.COLOR_RGB:
				colorRGBItem.setState(true);
				break;
    		case RGB_STACK:
				RGBStackItem.setState(true);
				break;
    		case HSB_STACK:
				HSBStackItem.setState(true);
				break;
    		case LAB_STACK:
				LabStackItem.setState(true);
				break;
    		case HSB32_STACK:
				HSB32Item.setState(true);
				break;
		}
		
    	//update Window menu
    	int nItems = window.getItemCount();
    	int start = WINDOW_MENU_ITEMS + windowMenuItems2;
    	int index = start + WindowManager.getCurrentIndex();
    	try {  // workaround for Linux/Java 5.0/bug
			for (int i=start; i<nItems; i++) {
				CheckboxMenuItem item = (CheckboxMenuItem)window.getItem(i);
				item.setState(i==index);
			}
		} catch (Exception e) {}
	}
	
	static boolean isColorLut(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
    	IndexColorModel cm = (IndexColorModel)ip.getColorModel();
    	if (cm==null) return false;
		int mapSize = cm.getMapSize();
		byte[] reds = new byte[mapSize];
		byte[] greens = new byte[mapSize];
		byte[] blues = new byte[mapSize];	
		cm.getReds(reds); 
		cm.getGreens(greens); 
		cm.getBlues(blues);
		boolean isColor = false;
		for (int i=0; i<mapSize; i++) {
			if ((reds[i] != greens[i]) || (greens[i] != blues[i])) {
				isColor = true;
				break;
			}
		}
		return isColor;
	}

	
	/** Use Prefs.getImageJDir() to get the path to the ImageJ directory. */
	static String getImageJPath() {
		return ImageJPath;
	}

	/** Returns the path to the user plugins directory or
		null if the plugins directory was not found. */
	public static String getPlugInsPath() {
		return pluginsPath;
	}

	/** Returns the path to the macros directory or
		null if the macros directory was not found. */
	public static String getMacrosPath() {
		return macrosPath;
	}
        
	/** Returns the hashtable that associates commands with plugins. */
	public static Hashtable getCommands() {
		if (pluginsTable==null)
			IJ.init();
		return pluginsTable;
	}
        
	/** Returns the hashtable that associates shortcuts with commands. The keys
		in the hashtable are Integer keycodes, or keycode+200 for uppercase. */
	public static Hashtable getShortcuts() {
		return shortcuts;
	}
        
	/** Returns the hashtable that associates keyboard shortcuts with macros. The keys
		in the hashtable are Integer keycodes, or keycode+200 for uppercase. */
	public static Hashtable getMacroShortcuts() {
		if (macroShortcuts==null)
			macroShortcuts = new Hashtable();
		return macroShortcuts;
	}
        
	/** Inserts one item (a non-image window) into the Window menu. */
	static synchronized void insertWindowMenuItem(Window win) {
		if (ij==null || win==null)
			return;
		String title = win instanceof Frame?((Frame)win).getTitle():((Dialog)win).getTitle();
		CheckboxMenuItem item = new CheckboxMenuItem(title);
		item.addItemListener(ij);
		int index = WINDOW_MENU_ITEMS+windowMenuItems2;
		if (windowMenuItems2>=2)
			index--;
		window.insert(item, index);
		windowMenuItems2++;
		if (windowMenuItems2==1) {
			window.insertSeparator(WINDOW_MENU_ITEMS+windowMenuItems2);
			windowMenuItems2++;
		}
	}

	/** Adds one image to the end of the Window menu. */
	static synchronized void addWindowMenuItem(ImagePlus imp) {
		if (ij==null)
			return;
		String name = imp.getTitle();
		String size = ImageWindow.getImageSize(imp);
		CheckboxMenuItem item = new CheckboxMenuItem(name+" "+size);
		item.setActionCommand("" + imp.getID());
		window.add(item);
		item.addItemListener(ij);
	}
	
	/** Removes the specified item from the Window menu. */
	static synchronized void removeWindowMenuItem(int index) {
		//IJ.log("removeWindowMenuItem: "+index+" "+windowMenuItems2+" "+window.getItemCount());
		if (ij==null)
			return;
		try {
			if (index>=0 && index<window.getItemCount()) {
				window.remove(WINDOW_MENU_ITEMS+index);
				if (index<windowMenuItems2) {
					windowMenuItems2--;
					if (windowMenuItems2==1) {
						window.remove(WINDOW_MENU_ITEMS);
						windowMenuItems2 = 0;
					}
				}
			}
		} catch (Exception e) {}
	}

	/** Changes the name of an item in the Window menu. */
	public static synchronized void updateWindowMenuItem(String oldLabel, String newLabel) {
		updateWindowMenuItem(null, oldLabel, newLabel);
	}

	/** Changes the name of an item in the Window menu. */
	public static synchronized void updateWindowMenuItem(ImagePlus imp, String oldLabel, String newLabel) {
		if (oldLabel==null || newLabel==null)
			return;
		int first = WINDOW_MENU_ITEMS;
		int count = window.getItemCount();
		try {  // workaround for Linux/Java 5.0/bug
			for (int i=first; i<count; i++) {
				MenuItem item = window.getItem(i);
				String label = item.getLabel();
				String cmd = item.getActionCommand();
				if (imp!=null) {  //remove size (e.g. " 24MB")
					int index = label.lastIndexOf(" ");
					if (index>-1)
						label = label.substring(0, index);
				}
				if (item!=null && label.equals(oldLabel) && (imp==null||(""+imp.getID()).equals(cmd))) {
					String size = "";
					if (imp!=null)
						size =  " " + ImageWindow.getImageSize(imp);
					item.setLabel(newLabel+size);
					return;
				}
			}
		} catch (Exception e) {}
	}
	
	/** Adds a file path to the beginning of the File/Open Recent submenu. */
	public static synchronized void addOpenRecentItem(String path) {
		if (ij==null) return;
		int count = openRecentMenu.getItemCount();
		for (int i=0; i<count; ) {
			if (openRecentMenu.getItem(i).getLabel().equals(path)) {
				openRecentMenu.remove(i);
				count--;
			} else
				i++;
		}
		if (count==MAX_OPEN_RECENT_ITEMS)
			openRecentMenu.remove(MAX_OPEN_RECENT_ITEMS-1);
		MenuItem item = new MenuItem(path);
		openRecentMenu.insert(item, 0);
		item.addActionListener(ij);
	}

	public static PopupMenu getPopupMenu() {
		return popup;
	}
	
	public static Menu getSaveAsMenu() {
		return saveAsMenu;
	}

	/** Adds a plugin based command to the end of a specified menu.
	* @param plugin			the plugin (e.g. "Inverter_", "Inverter_("arg")")
	* @param menuCode		PLUGINS_MENU, IMPORT_MENU, SAVE_AS_MENU or HOT_KEYS
	* @param command		the menu item label (set to "" to uninstall)
	* @param shortcut		the keyboard shortcut (e.g. "y", "Y", "F1")
	* @param ij				ImageJ (the action listener)
	*
	* @return				returns an error code(NORMAL_RETURN,COMMAND_IN_USE_ERROR, etc.)
	*/
	public static int installPlugin(String plugin, char menuCode, String command, String shortcut, ImageJ ij) {
		if (command.equals("")) //uninstall
			return NORMAL_RETURN;
		if (commandInUse(command))
			return COMMAND_IN_USE;
		if (!validShortcut(shortcut))
			return INVALID_SHORTCUT;
		if (shortcutInUse(shortcut))
			return SHORTCUT_IN_USE;
			
		Menu menu;
		switch (menuCode) {
			case PLUGINS_MENU: menu = pluginsMenu; break;
			case IMPORT_MENU: menu = getMenu("File>Import"); break;
			case SAVE_AS_MENU: menu = getMenu("File>Save As"); break;
			case SHORTCUTS_MENU: menu = shortcutsMenu; break;
			case ABOUT_MENU: menu = getMenu("Help>About Plugins"); break;
			case FILTERS_MENU: menu = getMenu("Process>Filters"); break;
			case TOOLS_MENU: menu = getMenu("Analyze>Tools"); break;
			case UTILITIES_MENU: menu = utilitiesMenu; break;
			default: return 0;
		}
		int code = convertShortcutToCode(shortcut);
		MenuItem item;
		boolean functionKey = code>=KeyEvent.VK_F1 && code<=KeyEvent.VK_F12;
		if (code==0)
			item = new MenuItem(command);
		else if (functionKey) {
			command += " [F"+(code-KeyEvent.VK_F1+1)+"]";
			shortcuts.put(new Integer(code),command);
			item = new MenuItem(command);
		} else {
			shortcuts.put(new Integer(code),command);
			int keyCode = code;
			boolean shift = false;
			if (keyCode>=265 && keyCode<=290) {
				keyCode -= 200;
				shift = true;
			}
			item = new MenuItem(command, new MenuShortcut(keyCode, shift));
		}
		menu.add(item);
		item.addActionListener(ij);
		pluginsTable.put(command, plugin);
		shortcut = code>0 && !functionKey?"["+shortcut+"]":"";
		pluginsPrefs.addElement(menuCode+",\""+command+shortcut+"\","+plugin);
		return NORMAL_RETURN;
	}
	
	/** Deletes a command installed by Plugins/Shortcuts/Add Shortcut. */
	public static int uninstallPlugin(String command) {
		boolean found = false;
		for (Enumeration en=pluginsPrefs.elements(); en.hasMoreElements();) {
			String cmd = (String)en.nextElement();
			if (cmd.contains(command)) {
				boolean ok = pluginsPrefs.removeElement((Object)cmd);
				found = true;
				break;
			}
		}
		if (found)
			return NORMAL_RETURN;
		else
			return COMMAND_NOT_FOUND;

	}
	
	public static boolean commandInUse(String command) {
		if (pluginsTable.get(command)!=null)
			return true;
		else
			return false;
	}

	public static int convertShortcutToCode(String shortcut) {
		int code = 0;
		int len = shortcut.length();
		if (len==2 && shortcut.charAt(0)=='F') {
			code = KeyEvent.VK_F1+(int)shortcut.charAt(1)-49;
			if (code>=KeyEvent.VK_F1 && code<=KeyEvent.VK_F9)
				return code;
			else
				return 0;
		}
		if (len==3 && shortcut.charAt(0)=='F') {
			code = KeyEvent.VK_F10+(int)shortcut.charAt(2)-48;
			if (code>=KeyEvent.VK_F10 && code<=KeyEvent.VK_F12)
				return code;
			else
				return 0;
		}
		if (len==2 && shortcut.charAt(0)=='N') { // numeric keypad
			code = KeyEvent.VK_NUMPAD0+(int)shortcut.charAt(1)-48;
			if (code>=KeyEvent.VK_NUMPAD0 && code<=KeyEvent.VK_NUMPAD9)
				return code;
			switch (shortcut.charAt(1)) {
				case '/': return KeyEvent.VK_DIVIDE;
				case '*': return KeyEvent.VK_MULTIPLY;
				case '-': return KeyEvent.VK_SUBTRACT;
				case '+': return KeyEvent.VK_ADD;
				case '.': return KeyEvent.VK_DECIMAL;
				default: return 0;
			}
		}
		if (len!=1)
			return 0;
		int c = (int)shortcut.charAt(0);
		if (c>=65&&c<=90) //A-Z
			code = KeyEvent.VK_A+c-65 + 200;
		else if (c>=97&&c<=122) //a-z
			code = KeyEvent.VK_A+c-97;
		else if (c>=48&&c<=57) //0-9
			code = KeyEvent.VK_0+c-48;
		return code;
	}
	
	void installStartupMacroSet() {
		if (macrosPath==null) {
			MacroInstaller.installFromJar("/macros/StartupMacros.txt");
			return;
		}
		String path = macrosPath + "StartupMacros.txt";
		File f = new File(path);
		if (!f.exists()) {
			path = macrosPath + "StartupMacros.ijm";
			f = new File(path);
			if (!f.exists()) {
				(new MacroInstaller()).installFromIJJar("/macros/StartupMacros.txt");
				return;
			}
		} else {
			if ("StartupMacros.fiji.ijm".equals(f.getName()))
				path = f.getPath();
		}
		String libraryPath = macrosPath + "Library.txt";
		f = new File(libraryPath);
		boolean isLibrary = f.exists();
		try {
			MacroInstaller mi = new MacroInstaller();
			if (isLibrary) mi.installLibrary(libraryPath);
			mi.installStartupMacros(path);
			nMacros += mi.getMacroCount();
		} catch (Exception e) {}
	}
	
	static boolean validShortcut(String shortcut) {
		int len = shortcut.length();
		if (shortcut.equals(""))
			return true;
		else if (len==1)
			return true;
		else if (shortcut.startsWith("F") && (len==2 || len==3))
			return true;
		else
			return false;
	}

	/** Returns 'true' if this keyboard shortcut is in use. */
	public static boolean shortcutInUse(String shortcut) {
		int code = convertShortcutToCode(shortcut);
		if (shortcuts.get(new Integer(code))!=null)
			return true;
		else
			return false;
	}
	
	/** Set the size (in points) used for the fonts in ImageJ menus. 
		Set the size to 0 to use the Java default size. */
	public static void setFontSize(int size) {
		if (size<9 && size!=0) size = 9;
		if (size>24) size = 24;
		fontSize = size;
	}
	
	/** Returns the size (in points) used for the fonts in ImageJ menus. Returns
		0 if the default font size is being used or if this is a Macintosh. */
	public static int getFontSize() {
		return fontSize;
		//return IJ.isMacintosh()?0:fontSize;
	}
	
	public static Font getFont() {
		if (menuFont==null) {
			int size = fontSize==0?13:fontSize;
			size = (int)Math.round(size*scale);
			if (IJ.isWindows() && scale>1.0 && size>17)
				size = 17;  // Java resets size to 12 if you try to set it to 18 or greater
			menuFont =  new Font("SanSerif", Font.PLAIN, size);
		}
		//System.out.println("Menus.getFont: "+scale+" "+fontSize+" "+menuFont);
		return menuFont;
	}

	/** Called once when ImageJ quits. */
	public static void savePreferences(Properties prefs) {
		if (pluginsPrefs==null)
			return;
		int index = 0;
		for (Enumeration en=pluginsPrefs.elements(); en.hasMoreElements();) {
			String key = "plugin" + (index/10)%10 + index%10;
			String value = (String)en.nextElement();
			prefs.put(key, value);
			index++;
		}
		int n = openRecentMenu.getItemCount();
		for (int i=0; i<n; i++) {
			String key = ""+i;
			if (key.length()==1) key = "0"+key;
			key = "recent"+key;
			prefs.put(key, openRecentMenu.getItem(i).getLabel());
		}
		prefs.put(Prefs.MENU_SIZE, Integer.toString(fontSize));
	}
	
	public static void updateImageJMenus() {
		jarFiles = macroFiles = null;
		Menus m = new Menus(IJ.getInstance(), IJ.getApplet());
		String err = m.addMenuBar();
		if (err!=null) IJ.error(err);
		m.installStartupMacroSet();
		IJ.resetClassLoader();
		IJ.runPlugIn("ij.plugin.ClassChecker", "");
		IJ.showStatus("Menus updated: "+m.nPlugins + " commands, " + m.nMacros + " macros");
	}
	
	public static void updateFont() {
		scale = (int)Math.round(Prefs.getGuiScale());
		Font font = getFont();
		mbar.setFont(font);
		if (ij!=null)
			ij.setMenuBar(mbar);
		popup.setFont(font);
	}
	
	/** Adds a command to the ImageJ menu bar. */
	public static void add(String menuPath, String plugin) {
		if (pluginsTable==null)
			return;
		int index = menuPath.lastIndexOf(">");
		if (index==-1 || index==menuPath.length()-1)
			return;
		String label = menuPath.substring(index+1, menuPath.length());
		menuPath = menuPath.substring(0, index);
		pluginsTable.put(label, plugin);
		addItem(getMenu(menuPath), label, 0, false);
	}
	
}
