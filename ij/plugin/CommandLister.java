package ij.plugin;
import ij.*;
import ij.text.*;
import ij.util.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;

/** This class is used by the Plugins/Shortcuts/List Shortcuts 
	command to display a list keyboard shortcuts. */
public class CommandLister implements PlugIn {

	public void run(String arg) {
		if (arg.equals("shortcuts"))
			listShortcuts();
		else
			listCommands();
	}
	
	public void listCommands() {
		Hashtable commands = Menus.getCommands();
		Vector v = new Vector();
		int index = 1;
		for (Enumeration en=commands.keys(); en.hasMoreElements();) {
			String command = (String)en.nextElement();
			v.addElement(index+"\t"+command+"\t"+(String)commands.get(command));
			index++;
		}
		showList("Commands", " \tCommand\tPlugin", v);
	}

	public void listShortcuts() {
		Hashtable shortcuts = Menus.getShortcuts();
		Vector v = new Vector();
		addShortcutsToVector(shortcuts, v);
		Hashtable macroShortcuts = Menus.getMacroShortcuts();
		addShortcutsToVector(macroShortcuts, v);
		showList("Keyboard Shortcuts", "Hot Key\tCommand", v);
	}
	
	void addShortcutsToVector(Hashtable shortcuts, Vector v) {
		for (Enumeration en=shortcuts.keys(); en.hasMoreElements();) {
			Integer key = (Integer)en.nextElement();
			int keyCode = key.intValue();
			boolean upperCase = false;
			if (keyCode>=200+65 && keyCode<=200+90) {
				upperCase = true;
				keyCode -= 200;
			}
			String shortcut = KeyEvent.getKeyText(keyCode);
			if (!upperCase && shortcut.length()==1) {
				char c = shortcut.charAt(0);
				if (c>=65 && c<=90)
					c += 32;
				char[] chars = new char[1];
				chars[0] = c;
				shortcut = new String(chars);
			}
			if (shortcut.length()>1)
				shortcut = " " + shortcut; 
			v.addElement(shortcut+"\t"+(String)shortcuts.get(key));
		}
	}

	void showList(String title, String headings, Vector v) {
		String[] list = new String[v.size()];
		v.copyInto((String[])list);
		Arrays.sort(list, String.CASE_INSENSITIVE_ORDER);
		ArrayList list2 = new ArrayList();
		for (int i=0; i<list.length; i++)
			list2.add(list[i]);
		TextWindow tw = new TextWindow(title, headings, list2, 600, 500);
	}
}
