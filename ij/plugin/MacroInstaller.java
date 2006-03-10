package ij.plugin;
import java.awt.*;
import java.io.*;
import java.awt.event.*;
import ij.*;
import ij.gui.*;
import ij.macro.*;
import ij.text.*;
import ij.util.Tools;
import ij.io.*;
																																																																																																																																																																																																																																																								 import java.util.*;																																																																																																																																																					   

/** This plugin implements the Plugins/Macros/Install Macros command. It is also used by the Editor
	class to install macro in menus and by the ImageJ class to install macros at startup. */
public class MacroInstaller implements PlugIn, MacroConstants, ActionListener {

	public static final int MAX_SIZE = 28000, MAX_MACROS=60, XINC=10, YINC=18;
	public static final char commandPrefix = '^';
	static final String commandPrefixS = "^";
	
	private int[] macroStarts;
	private String[] macroNames;
	private MenuBar mb = new MenuBar();
	private int nMacros;
	private Program pgm;
	private boolean firstEvent = true;
	private String shortcutsInUse;
	private int inUseCount;
	private int nShortcuts;
	private String text;
	private String anonymousName;
	private Menu macrosMenu;
	
	private static String defaultDir, fileName;
	private static MacroInstaller instance, listener;
	
	public void run(String path) {
		if (path==null || path.equals(""))
			path = showDialog();
		if (path==null)
			return;
		String text = open(path);
		if (text!=null)
			install(text);
	}
			
	void install() {
		if (text!=null) {
			Tokenizer tok = new Tokenizer();
			pgm = tok.tokenize(text);
		}
		IJ.showStatus("");
		int[] code = pgm.getCode();
		Symbol[] symbolTable = pgm.getSymbolTable();
		int count=0, token, nextToken, address;
		String name;
		Symbol symbol;
		shortcutsInUse = null;
		inUseCount = 0;
		nShortcuts = 0;
		macroStarts = new int[MAX_MACROS];
		macroNames = new String[MAX_MACROS];
		int toolCount = 0;
		int itemCount = macrosMenu.getItemCount();
		int baseCount = macrosMenu==Menus.getMacrosMenu()?4:2;
		if (itemCount>baseCount)
			for (int i=itemCount-1; i>=baseCount; i--)
				macrosMenu.remove(i);
		if (pgm.hasVars() && pgm.getGlobals()==null)
			new Interpreter().saveGlobals(pgm);
		for (int i=0; i<code.length; i++) {
			token = code[i]&0xffff;
			if (token==MACRO) {
				nextToken = code[i+1]&0xffff;
				if (nextToken==STRING_CONSTANT) {
					if (count==MAX_MACROS) {
						if (macrosMenu==Menus.getMacrosMenu())
							IJ.error("Macro Installer", "Macro sets are limited to "+MAX_MACROS+" macros.");
						break;
					}
					address = code[i+1]>>16;
					symbol = symbolTable[address];
					name = symbol.str;
					macroStarts[count] = i + 2;
					macroNames[count] = name;
					if (name.indexOf('-')!=-1 && (name.indexOf("Tool")!=-1||name.indexOf("tool")!=-1)) {
						Toolbar.getInstance().addMacroTool(name, this, toolCount);
						toolCount++;
                    } else if (name.startsWith("AutoRun")) {
                        new MacroRunner(pgm, macroStarts[count], name);
                        count--;
					} else { 
						addShortcut(name);
						macrosMenu.add(new MenuItem(name));
					}
					//IJ.log(count+" "+name+" "+macroStarts[count]);
					count++;
				}					
			} else if (token==EOF)
				break;
		}
		nMacros = count;
		if (toolCount>0) {
			Toolbar tb = Toolbar.getInstance();
			if(Toolbar.getToolId()>=Toolbar.SPARE2)
				tb.setTool(Toolbar.RECTANGLE);
			tb.repaint();
		}
		this.instance = nShortcuts>0?this:null;
		if (shortcutsInUse!=null && text!=null)
			IJ.showMessage("Install Macros", (inUseCount==1?"This keyboard shortcut is":"These keyboard shortcuts are")
			+ " already in use:"+shortcutsInUse);
		if (nMacros==0 && fileName!=null) {
			int dotIndex = fileName.lastIndexOf('.');
			if (dotIndex>0)
				anonymousName = fileName.substring(0, dotIndex);
			else
				anonymousName =fileName;
			macrosMenu.add(new MenuItem(anonymousName));
			nMacros = 1;
		}
		String word = nMacros==1?" macro":" macros";
		IJ.showStatus(nMacros + word + " installed");
	}
	
	public int install(String text) {
		if (text==null && pgm==null)
			return 0;
		this.text = text;
		macrosMenu = Menus.getMacrosMenu();
		if (listener!=null)
			macrosMenu.removeActionListener(listener);
		macrosMenu.addActionListener(this);
		listener = this;
		install();
		return nShortcuts;
	}
	
	public int install(String text, Menu menu) {
		this.text = text;
		macrosMenu = menu;
		install();
		return nShortcuts;
	}

	void removeShortcuts() {
		Menus.getMacroShortcuts().clear();
		Hashtable shortcuts = Menus.getShortcuts();
		for (Enumeration en=shortcuts.keys(); en.hasMoreElements();) {
			Integer key = (Integer)en.nextElement();
			String value = (String)shortcuts.get(key);
			if (value.charAt(0)==commandPrefix)
				shortcuts.remove(key);
		}
	}

	void addShortcut(String name) {
		int index1 = name.indexOf('[');
		if (index1==-1)
			return;
		int index2 = name.lastIndexOf(']');
		if (index2<=(index1+1))
			return;
		String shortcut = name.substring(index1+1, index2);
		int len = shortcut.length();
		if (len>1)
			shortcut = shortcut.toUpperCase(Locale.US);;
		if (len>3 || (len>1&&shortcut.charAt(0)!='F'&&shortcut.charAt(0)!='N'))
			return;
		int code = Menus.convertShortcutToCode(shortcut);
		if (code==0)
			return;
		if (nShortcuts==0)
			removeShortcuts();
		// One character shortcuts go in a separate hash table to
		// avoid conflicts with ImageJ menu shortcuts.
		if (len==1) {
			Hashtable macroShortcuts = Menus.getMacroShortcuts();
			macroShortcuts.put(new Integer(code), commandPrefix+name);
			nShortcuts++;
			return;
		}
		Hashtable shortcuts = Menus.getShortcuts();
		if (shortcuts.get(new Integer(code))!=null) {
			if (shortcutsInUse==null)
				shortcutsInUse = "\n \n";
			shortcutsInUse += "    " + name + "\n";
			inUseCount++;
			return;
		}
		shortcuts.put(new Integer(code), commandPrefix+name);
		nShortcuts++;
		//IJ.log("addShortcut3: "+name+"   "+shortcut+"   "+code);
	}
	
	 String showDialog() {
		if (defaultDir==null) defaultDir = Menus.getMacrosPath();
		OpenDialog od = new OpenDialog("Install Macros", defaultDir, fileName);
		String name = od.getFileName();
		if (name==null) return null;
		String dir = od.getDirectory();
		if (!(name.endsWith(".txt")||name.endsWith(".ijm"))) {
			IJ.showMessage("Macro Installer", "File name must end with \".txt\" or \".ijm\" .");
			return null;
		}
		fileName = name;
		defaultDir = dir;
		return dir+name;
	}

	String open(String path) {
		try {
			StringBuffer sb = new StringBuffer(5000);
			BufferedReader r = new BufferedReader(new FileReader(path));
			while (true) {
				String s=r.readLine();
				if (s==null)
					break;
				else
					sb.append(s+"\n");
			}
			r.close();
			return new String(sb);
		}
		catch (Exception e) {
			IJ.error(e.getMessage());
			return null;
		}
	}
	
	//void runMacro() {
	//	new MacroRunner(text);
	//}

	public boolean runMacroTool(String name) {
		for (int i=0; i<nMacros; i++) {
			if (macroNames[i].startsWith(name)) {
				new MacroRunner(pgm, macroStarts[i], name);
				return true;
			}
		}
		return false;
	}
	
	public static void doShortcut(String name) {
		if (instance==null)
			return;
		if (name.startsWith(commandPrefixS))
			name = name.substring(1);
		for (int i=0; i<instance.nMacros; i++) {
			if (name.equals(instance.macroNames[i])) {
				new MacroRunner(instance.pgm, instance.macroStarts[i], name);
				return;
			}
		}
	}
	
	public void runMacro(String name) {
		if (anonymousName!=null && name.equals(anonymousName)) {
			//IJ.log("runMacro: "+anonymousName);
			new MacroRunner(pgm, 0, anonymousName);
			return;
		}
		for (int i=0; i<nMacros; i++)
			if (name.equals(macroNames[i])) {
				new MacroRunner(pgm, macroStarts[i], name);
				return;
			}
	}
	
	public int getMacroCount() {
		return nMacros;
	}
	
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public void actionPerformed(ActionEvent evt) {
		runMacro(evt.getActionCommand());
	}

} 



