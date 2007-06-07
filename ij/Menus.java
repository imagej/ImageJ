package ij;
import ij.util.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.File;
import java.applet.Applet;
import java.awt.event.*;

/**
This class installs and updates ImageJ's menus.
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
		
	public static final int WINDOW_MENU_ITEMS = 3;
	
	public static final int NORMAL_RETURN = 0;
	public static final int COMMAND_IN_USE = -1;
	public static final int INVALID_SHORTCUT = -2;
	public static final int SHORTCUT_IN_USE = -3;
	public static final int NOT_INSTALLED = -4;
	public static final int COMMAND_NOT_FOUND = -5;
	
	private static MenuBar mbar;
	private static CheckboxMenuItem gray8Item,gray16Item,gray32Item,
			color256Item,colorRGBItem,RGBStackItem,HSBStackItem;
	private static PopupMenu popup;

	private static ImageJ ij;
	private static Applet applet;
	private static Hashtable demoImagesTable = new Hashtable();
	private static String pluginsPath;
	private static Menu pluginsMenu, importMenu, saveAsMenu, shortcutsMenu,
		aboutMenu, filtersMenu, toolsMenu, utilitiesMenu;
	private static Hashtable pluginsTable = new Hashtable();
	
	static Menu window;
	static int nPlugins;
	private static Hashtable shortcuts = new Hashtable();
	private static Vector pluginsPrefs = new Vector();
	private static Font font; // = new Font("Dialog", Font.PLAIN, 14);
	
	Menus(ImageJ ijInstance, Applet appletInstance) {
		ij = ijInstance;
		applet = appletInstance;
	}

	Menu createMenu(String name) {
		Menu m = new Menu(name);
		if (font!=null) m.setFont(font);
		return m;
	}
	
	void addMenuBar() {
		Menu file = createMenu("File");
		addItem(file, "New...", KeyEvent.VK_N, false);
		addItem(file, "Open...", KeyEvent.VK_O, false);
		addSubMenu(file, "Open Samples");
		importMenu = addSubMenu(file, "Import");
		file.addSeparator();
		addItem(file, "Close", KeyEvent.VK_W, false);
		addItem(file, "Save",  KeyEvent.VK_S, false);
		saveAsMenu = addSubMenu(file, "Save As");
		addItem(file, "Revert", KeyEvent.VK_R,  false);
		file.addSeparator();
		addPlugInItem(file, "Page Setup...", "ij.plugin.filter.Printer(\"setup\")", 0, false);
		addPlugInItem(file, "Print...", "ij.plugin.filter.Printer(\"print\")", KeyEvent.VK_P, false);
		file.addSeparator();
		addItem(file, "Quit",  0, false);
		
		Menu edit = createMenu("Edit");
		addItem(edit, "Undo", KeyEvent.VK_Z, false);
		edit.addSeparator();
		addItem(edit, "Cut", KeyEvent.VK_X, false);
		addItem(edit, "Copy", KeyEvent.VK_C, false);
		addItem(edit, "Paste", KeyEvent.VK_V, false);
		addPlugInItem(edit, "Paste Control...", "ij.plugin.frame.PasteController", 0, false);
		addPlugInItem(edit, "Clear", "ij.plugin.filter.Filler(\"clear\")", 0, false);
		addPlugInItem(edit, "Clear Outside", "ij.plugin.filter.Filler(\"outside\")", 0, false);
		edit.addSeparator();
		addItem(edit, "Select All", KeyEvent.VK_A, false);
		addItem(edit, "Select None", KeyEvent.VK_A, true);
		addItem(edit, "Restore Selection", KeyEvent.VK_E, true);
		edit.addSeparator();
		addPlugInItem(edit, "Fill", "ij.plugin.filter.Filler(\"fill\")", KeyEvent.VK_F, false);
		addPlugInItem(edit, "Draw", "ij.plugin.filter.Filler(\"draw\")", KeyEvent.VK_D, false);
		addPlugInItem(edit, "Invert", "ij.plugin.filter.Filters(\"invert\")", KeyEvent.VK_I, true);
		edit.addSeparator();
		addSubMenu(edit, "Options");
		
		Menu image = createMenu("Image");
		Menu imageType = createMenu("Type");
			gray8Item = new CheckboxMenuItem("8-bit");
			imageType.add(gray8Item);
			gray8Item.addItemListener(ij);
			gray8Item.setState(false);
			gray16Item = new CheckboxMenuItem("16-bit");
			imageType.add(gray16Item);
			gray16Item.addItemListener(ij);
			gray16Item.setState(false);
			gray32Item = new CheckboxMenuItem("32-bit");
			imageType.add(gray32Item);
			gray32Item.addItemListener(ij);
			gray32Item.setState(false);
			imageType.add(new MenuItem("-"));
			
			color256Item = new CheckboxMenuItem("8-bit Color");
			imageType.add(color256Item);
			color256Item.addItemListener(ij);
			color256Item.setState(false);
			colorRGBItem = new CheckboxMenuItem("RGB Color");
			imageType.add(colorRGBItem);
			colorRGBItem.addItemListener(ij);
			colorRGBItem.setState(false);
			imageType.add(new MenuItem("-"));
			
			RGBStackItem = new CheckboxMenuItem("RGB Stack");
			imageType.add(RGBStackItem);
			RGBStackItem.addItemListener(ij);
			RGBStackItem.setState(false);
			HSBStackItem = new CheckboxMenuItem("HSB Stack");
			imageType.add(HSBStackItem);
			HSBStackItem.addItemListener(ij);
			HSBStackItem.setState(false);
			image.add(imageType);
			
		image.addSeparator();
		addSubMenu(image, "Adjust");
		addPlugInItem(image, "Show Info...", "ij.plugin.filter.Info", KeyEvent.VK_I, false);
		addSubMenu(image, "Benchmarks");
		addSubMenu(image, "Stacks");
		image.addSeparator();
		addPlugInItem(image, "Crop", "ij.plugin.filter.Resizer(\"crop\")", 0, false);
		addPlugInItem(image, "Duplicate...", "ij.plugin.filter.Duplicater", KeyEvent.VK_D, true);
		addPlugInItem(image, "Scale...", "ij.plugin.filter.Scaler", KeyEvent.VK_E, false);
		addSubMenu(image, "Rotate");
		image.addSeparator();
		addSubMenu(image, "Lookup Tables");
		addPlugInItem(image, "Colors...", "ij.plugin.Colors", 0, false);

		Menu process = createMenu("Process");
		addPlugInItem(process, "Smooth", "ij.plugin.filter.Filters(\"smooth\")", KeyEvent.VK_S, true);
		addPlugInItem(process, "Sharpen", "ij.plugin.filter.Filters(\"sharpen\")", 0, false);
		addPlugInItem(process, "Find Edges", "ij.plugin.filter.Filters(\"edge\")", KeyEvent.VK_F, true);
		addPlugInItem(process, "Equalize", "ij.plugin.filter.Equalizer", 0, false);
		addSubMenu(process, "Noise");
		addSubMenu(process, "Shadows");
		addSubMenu(process, "Binary");
		addSubMenu(process, "Math");
		filtersMenu = addSubMenu(process, "Filters");
		process.addSeparator();
		addPlugInItem(process, "Image Calculator...", "ij.plugin.ImageCalculator", 0, false);
		addPlugInItem(process, "Subtract Background...", "ij.plugin.filter.BackgroundSubtracter", 0, false);
		addItem(process, "Repeat Command", KeyEvent.VK_R, true);
		
		Menu analyze = createMenu("Analyze");
		addPlugInItem(analyze, "Measure", "ij.plugin.filter.Analyzer", KeyEvent.VK_M, false);
		addPlugInItem(analyze, "Analyze Particles...", "ij.plugin.filter.ParticleAnalyzer", 0, false);
		addPlugInItem(analyze, "Summarize", "ij.plugin.filter.Analyzer(\"sum\")", 0, false);
		addPlugInItem(analyze, "Clear Results", "ij.plugin.filter.Analyzer(\"clear\")", 0, false);
		addPlugInItem(analyze, "Set Measurements...", "ij.plugin.filter.Analyzer(\"set\")", 0, false);
		analyze.addSeparator();
		addPlugInItem(analyze, "Set Scale...", "ij.plugin.filter.ScaleDialog", 0, false);
		addPlugInItem(analyze, "Calibrate...", "ij.plugin.filter.Calibrator", 0, false);
		addItem(analyze, "Histogram", KeyEvent.VK_H, false);
		addPlugInItem(analyze, "Plot Profile", "ij.plugin.filter.Profiler(\"plot\")", KeyEvent.VK_K, false);
		addPlugInItem(analyze, "Show LUT", "ij.plugin.filter.LutViewer", 0, false);
		addSubMenu(analyze, "Gels");
		toolsMenu = addSubMenu(analyze, "Tools");

		window = createMenu("Window");
		addItem(window, "ImageJ [enter]", 0, false);
		addItem(window, "Put Behind [tab]", 0, false);
		window.addSeparator();

		Menu help = createMenu("Help");
		addItem(help, "About ImageJ...", 0, false);
		help.addSeparator();
		aboutMenu = addSubMenu(help, "About Plugins");
		
		addPluginsMenu();
		if (applet==null)
			installPlugins();
		
		mbar = new MenuBar();
		mbar.add(file);
		mbar.add(edit);
		mbar.add(image);
		mbar.add(process);
		mbar.add(analyze);
		mbar.add(pluginsMenu);
		mbar.add(window);
		mbar.setHelpMenu(help);
		ij.setMenuBar(mbar);
	}
	
	void addItem(Menu menu, String label, int shortcut, boolean shift) {
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
		if (font!=null) item.setFont(font);
		menu.add(item);
		item.addActionListener(ij);
	}

	void addPlugInItem(Menu menu, String label, String className, int shortcut, boolean shift) {
		pluginsTable.put(label, className);
		nPlugins++;
		addItem(menu, label, shortcut, shift);
	}

	Menu addSubMenu(Menu menu, String name) {
		String value;
		String key = name.toLowerCase();
		int index;
 		Menu submenu=createMenu(name);
 
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
		return submenu;
	}

	void addPluginItem(Menu submenu, String s) {
		int index = s.lastIndexOf(',');
		if (index<=0)
			return;
		String command = s.substring(1,index-1);
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
				//IJ.write(command+": "+shortcut);
			}
		}
		if (keyCode>=KeyEvent.VK_F1 && keyCode<=KeyEvent.VK_F12) {
			shortcuts.put(new Integer(keyCode),command);
			keyCode = 0;
		} else if (keyCode>200) {
			keyCode -= 200;
			shift = true;
		}
		addItem(submenu,command,keyCode,shift);
		String className = s.substring(index+1,s.length());
		pluginsTable.put(command, className);
		nPlugins++;
	}

	void addPluginsMenu() {
		String value,label,className;
		int index;
		pluginsMenu = createMenu("Plugins");
		for (int count=1; count<100; count++) {
			value = Prefs.getString("plug-in" + (count/10)%10 + count%10);
			if (value==null)
				break;
			char firstChar = value.charAt(0);
			if (firstChar=='-')
				pluginsMenu.addSeparator();
			else if (firstChar=='>') {
				String submenu = value.substring(2,value.length()-1);
				Menu menu = addSubMenu(pluginsMenu, submenu);
				if (submenu.equals("Shortcuts"))
					shortcutsMenu = menu;
				else if (submenu.equals("Utilities"))
					utilitiesMenu = menu;
			} else
				addPluginItem(pluginsMenu, value);
		}
	}

	/** Install plugins using "pluginxx=" keys in IJ_Prefs.txt.
		Plugins not listed in IJ_Prefs are added to the end
		of the Plugins menu. */
	void installPlugins() {
		String value, className;
		char menuCode;
		Menu menu;
		String[] plugins = getPlugins();
		String[] plugins2 = null;
		Hashtable skipList = new Hashtable();
 		for (int index=0; index<100; index++) {
			value = Prefs.getString("plugin" + (index/10)%10 + index%10);
			if (value==null)
				break;
			menuCode = value.charAt(0);
			switch (menuCode) {
				case PLUGINS_MENU: default: menu = pluginsMenu; break;
				case IMPORT_MENU: menu = importMenu; break;
				case SAVE_AS_MENU: menu = saveAsMenu; break;
				case SHORTCUTS_MENU: menu = shortcutsMenu; break;
				case ABOUT_MENU: menu = aboutMenu; break;
				case FILTERS_MENU: menu = filtersMenu; break;
				case TOOLS_MENU: menu = toolsMenu; break;
				case UTILITIES_MENU: menu = utilitiesMenu; break;
			}
			String prefsValue = value;
			value = value.substring(2,value.length()); //remove menu code and coma
			className = value.substring(value.lastIndexOf(',')+1,value.length());
			boolean found = className.startsWith("ij.");
			if (!found && plugins!=null) { // does this plugin exist?
				if (plugins2==null)
					plugins2 = getStrippedPlugins(plugins);
				for (int i=0; i<plugins2.length; i++) {
					if (className.startsWith(plugins2[i])) {
						found = true;
						break;
					}
				}
			}
			if (found) {
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
		if (plugins!=null)
			for (int i=0; i<plugins.length; i++) {
				if (!skipList.containsKey(plugins[i]))
					installUserPlugin(plugins[i]);
			}
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
		

	/** Returns a list of the plugins in the plugins menu. */
	String[] getPlugins() {
		String homeDir = Prefs.getHomeDir();
		if (homeDir==null)
			return null;
		if (homeDir.endsWith("plugins"))
			pluginsPath = homeDir;
		else {
			String separator = Prefs.getFileSeparator();
			pluginsPath = homeDir+separator+"plugins"+separator;
		}
		File f = new File(pluginsPath);
		String[] list = f.list();
		if (list==null)
			return null;
		Vector v = new Vector();
		for (int i=0; i<list.length; i++) {
			String name = list[i];
			boolean isClassFile = name.endsWith(".class");
			if (name.indexOf('_')>=0 && isClassFile && name.indexOf('$')<0 ) {
				name = name.substring(0, name.length()-6); // remove ".class"
				v.addElement(name);
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
	
	/** Looks for plugins in a subdirectorie of the plugins directory. */
	void checkSubdirectory(String path, String dir, Vector v) {
		if (dir.endsWith(".java"))
			return;
		File f = new File(path, dir);
		if (!f.isDirectory())
			return;
		String[] list = f.list();
		if (list==null)
			return;
		dir += "/";
		for (int i=0; i<list.length; i++) {
			String name = list[i];
			if (name.indexOf('_')>=0 && name.endsWith(".class") && name.indexOf('$')<0 ) {
				name = name.substring(0, name.length()-6); // remove ".class"
				v.addElement(dir+name);
				//IJ.write("File: "+f+"/"+name);
			}
		}
	}
	
	static String submenuName;
	static Menu submenu;

	/** Installs a plugin in the Plugins menu using the class name,
		with underscores replaced by spaces, as the command. */
	void installUserPlugin(String className) {
		Menu menu = pluginsMenu;
		int slashIndex = className.indexOf('/');
		if (slashIndex>0) {
			String dir = className.substring(0, slashIndex);
			className = className.substring(slashIndex+1, className.length());
			if (submenu==null || !submenuName.equals(dir)) {
 				submenuName = dir;
 				submenu = createMenu(submenuName);
 				pluginsMenu.add(submenu);
			}
		menu = submenu;
		//IJ.write(dir + "  " + className);
		}
		String command = className.replace('_',' ');
		command.trim();
		MenuItem item = new MenuItem(command);
		if (font!=null) item.setFont(font);
		menu.add(item);
		item.addActionListener(ij);
		pluginsTable.put(command, className);
		nPlugins++;
	}
	
	void installPopupMenu(ImageJ ij) {
		String s;
		int count = 0;
		MenuItem mi;
		popup = new PopupMenu("");

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
		
	static final int RGB_STACK=10, HSB_STACK=11;
	
	static void updateMenus() {
	
		if (ij==null) return;
		gray8Item.setState(false);
		gray16Item.setState(false);
		gray32Item.setState(false);
		color256Item.setState(false);
		colorRGBItem.setState(false);
		RGBStackItem.setState(false);
		HSBStackItem.setState(false);
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			return;
    	int type = imp.getType();
     	if (imp.getStackSize()>1) {
    		ImageStack stack = imp.getStack();
    		if (stack.isRGB()) type = RGB_STACK;
    		else if (stack.isHSB()) type = HSB_STACK;
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
		}
		
    	//update Window menu
    	int nItems = window.getItemCount();
    	int index = WindowManager.getCurrentIndex() + WINDOW_MENU_ITEMS;
    	for (int i=WINDOW_MENU_ITEMS; i<nItems; i++) {
			CheckboxMenuItem item = (CheckboxMenuItem)window.getItem(i);
			item.setState(i==index);
		}
	}
	
	/** Returns the path to the user plugins directory. */
	public static String getPlugInsPath() {
		return pluginsPath;
	}
        
	/** Returns the hashtable that associates commands with plugins. */
	public static Hashtable getCommands() {
		return pluginsTable;
	}
        
	/** Returns the hashtable that associates shortcuts with commands. The keys
		in the hashtable are Integer keycodes, or keycode+200 for uppercase. */
	public static Hashtable getShortcuts() {
		return shortcuts;
	}
        
	/** Removes the last n items from the Window menu. */
	static synchronized void trimWindowMenu(int n) {
		//if (IJ.debugMode) IJ.write("Triming " + n + " items from Window menu");
		int nItems = window.getItemCount();
		if (n<=(nItems-WINDOW_MENU_ITEMS))
			for (int i=0; i<n; i++)
				window.remove(--nItems);
	}

	/** Adds one window to the end of the Window menu. */
	static synchronized void extendWindowMenu(ImagePlus imp) {
		if (ij==null) return;
		String name = imp.getTitle();
		int size = (imp.getWidth()*imp.getHeight()*imp.getStackSize())/1024;
		switch (imp.getType()) {
			case ImagePlus.GRAY32: case ImagePlus.COLOR_RGB: // 32-bit
				size *=4;
				break;
			case ImagePlus.GRAY16:  // 16-bit
				size *= 2;
				break;
			default: // 8-bit
				;
		}
		CheckboxMenuItem item = new CheckboxMenuItem(name + " " + size + "K");
		window.add(item);
		item.addItemListener(ij);
	}
	
	public static PopupMenu getPopupMenu() {
		return popup;
	}
	
	/** Adds a plugin based command to the end of a specified menu.
	* @param plugin			the plugin (e.g. "Inverter_", "Inverter_("arg")")
	* @param menuCode		PLUGINS_MENU, IMPORT_MENU, SAVE_AS_MENU or HOT_KEYS
	* @param command		the menu item label (set to "" to uninstall)
	* @param command		the keyboard shortcut (e.g. "y", "Y", "F1")
	* @param ij				ImageJ (the action listener)
	*
	* @return				returns an error code(NORMAL_RETURN,COMMAND_IN_USE_ERROR, etc.)
	*/
	public static int installPlugin(String plugin, char menuCode, String command, String shortcut, ImageJ ij) {
		if (command.equals("")) { //uninstall
			//Object o = pluginsPrefs.remove(plugin);
			//if (o==null)
			//	return NOT_INSTALLED;
			//else
				return NORMAL_RETURN;
		}
		
		if (commandInUse(command))
			return COMMAND_IN_USE;
		if (!validShortcut(shortcut))
			return INVALID_SHORTCUT;
		if (shortcutInUse(shortcut))
			return SHORTCUT_IN_USE;
			
		Menu menu;
		switch (menuCode) {
			case PLUGINS_MENU: menu = pluginsMenu; break;
			case IMPORT_MENU: menu = importMenu; break;
			case SAVE_AS_MENU: menu = saveAsMenu; break;
			case SHORTCUTS_MENU: menu = shortcutsMenu; break;
			case ABOUT_MENU: menu = aboutMenu; break;
			case FILTERS_MENU: menu = filtersMenu; break;
			case TOOLS_MENU: menu = toolsMenu; break;
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
		}else {
			shortcuts.put(new Integer(code),command);
			int keyCode = code;
			boolean shift = false;
			if (keyCode>200) {
				keyCode -= 200;
				shift = true;
			}
			item = new MenuItem(command, new MenuShortcut(keyCode, shift));
		}
		menu.add(item);
		item.addActionListener(ij);
		pluginsTable.put(command, plugin);
		shortcut = code>0 && !functionKey?"["+shortcut+"]":"";
		//IJ.write("installPlugin: "+menuCode+",\""+command+shortcut+"\","+plugin);
		pluginsPrefs.addElement(menuCode+",\""+command+shortcut+"\","+plugin);
		return NORMAL_RETURN;
	}
	
	/** Deletes a command installed by installPlugin. */
	public static int uninstallPlugin(String command) {
		boolean found = false;
		for (Enumeration en=pluginsPrefs.elements(); en.hasMoreElements();) {
			String cmd = (String)en.nextElement();
			if (cmd.indexOf(command)>0) {
				pluginsPrefs.removeElement((Object)cmd);
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

	static int convertShortcutToCode(String shortcut) {
		int code = 0;
		int len = shortcut.length();
		if (len==2 && shortcut.startsWith("F")) {
			code = KeyEvent.VK_F1+(int)shortcut.charAt(1)-49;
			if (code>=KeyEvent.VK_F1 && code<=KeyEvent.VK_F9)
				return code;
			else
				return 0;
		}
		if (len==3 && shortcut.startsWith("F")) {
			code = KeyEvent.VK_F10+(int)shortcut.charAt(2)-48;
			if (code>=KeyEvent.VK_F10 && code<=KeyEvent.VK_F12)
				return code;
			else
				return 0;
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

	public static boolean shortcutInUse(String shortcut) {
		int code = convertShortcutToCode(shortcut);
		if (shortcuts.get(new Integer(code))!=null)
			return true;
		else
			return false;
	}

	public static void savePreferences(Properties prefs) {
		int index = 0;
		for (Enumeration en=pluginsPrefs.elements(); en.hasMoreElements();) {
			String key = "plugin" + (index/10)%10 + index%10;
			prefs.put(key, (String)en.nextElement());
			index++;
		}
	}

}