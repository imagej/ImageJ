package ij.plugin;
import java.awt.*;
import java.io.*;
import java.awt.event.*;
import ij.*;
import ij.gui.*;
import ij.macro.*;
import ij.text.*;
import ij.util.Tools;
																																																																																																																																																																																																																																																								 import java.util.*;																																																																																																																																																					   

/** This plugin implements the Plugins/Macros/Install Macros command. It is also used by the Editor
	class to install macro in menus and by the ImageJ class to install macros at startup. */
public class MacroInstaller implements PlugIn, MacroConstants, ActionListener {

	public static final int MAX_SIZE = 28000, MAX_MACROS=50, XINC=10, YINC=18;
	public static final char commandPrefix = '^';
	
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
		int baseCount = macrosMenu==Menus.getMacrosMenu()?3:2;
		if (itemCount>baseCount)
			for (int i=itemCount-1; i>=baseCount; i--)
				macrosMenu.remove(i);
		for (int i=0; i<code.length; i++) {
			token = code[i]&0xffff;
			if (token==MACRO) {
				nextToken = code[i+1]&0xffff;
				if (nextToken==STRING_CONSTANT) {
					address = code[i+1]>>16;
					symbol = symbolTable[address];
					name = symbol.str;
					macroStarts[count] = i + 2;
					macroNames[count] = name;
					if (name.indexOf('-')!=-1 && (name.indexOf("Tool")!=-1||name.indexOf("tool")!=-1)) {
						Toolbar.getInstance().addMacroTool(name, this, toolCount);
						toolCount++;
					} else { 
						addShortcut(name);
						macrosMenu.add(new MenuItem(name));
					}
					//IJ.log(count+" "+name+" "+macroStarts[count]);
					count++;
					if (count==MAX_MACROS)
						break;
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
		if (pgm.hasVars() && pgm.getGlobals()==null)
			new Interpreter().saveGlobals(pgm);
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
		shortcut = shortcut.replace('f', 'F');
		int len = shortcut.length();
		if (len>3 || (len>1 && shortcut.charAt(0)!='F'))
			return;
		int code = Menus.convertShortcutToCode(shortcut);
		if (code==0)
			return;
		if (nShortcuts==0)
			removeShortcuts();
		Hashtable shortcuts= Menus.getShortcuts();
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
		String name, dir;
		FileDialog fd = new FileDialog(IJ.getInstance(), "Install Macros...");
		if (defaultDir!=null)
			fd.setDirectory(defaultDir);
		else {
			String macrosDir = Menus.getMacrosPath();
			if (macrosDir!=null)
				fd.setDirectory(macrosDir);
		}
		if (fileName!=null)
			fd.setFile(fileName);
		GUI.center(fd);
		fd.show();
		name = fd.getFile();
		if (name==null) return null;
		dir = fd.getDirectory();
		fd.dispose();
		if (!name.endsWith(".txt")) {
			IJ.showMessage("Macro Installer", "File name must end with \".txt\".");
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

	public void runMacroTool(String name) {
		for (int i=0; i<nMacros; i++)
			if (macroNames[i].startsWith(name)) {
				new MacroRunner(pgm, macroStarts[i], name);
				return;
			}
	}
	
	public static void doShortcut(String name) {
		if (instance==null)
			return;
		for (int i=0; i<instance.nMacros; i++)
			if (name.endsWith(instance.macroNames[i])) {
				new MacroRunner(instance.pgm, instance.macroStarts[i], name);
				return;
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



