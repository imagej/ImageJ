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
		String[] list = new String[v.size()];
		v.copyInto((String[])list);
		showList("Commands", " \tCommand\tPlugin", list);
	}

	public void listShortcuts() {
		String[] shortcuts = getShortcuts();
		for (int i=0; i<shortcuts.length; i++) {
			if (shortcuts[i].contains("\t^"))
				shortcuts[i] += " (macro)";
		}
		showList("Keyboard Shortcuts", "Shortcut\tCommand", shortcuts);
	}
	
	public String[] getShortcuts() {
		Vector v = new Vector();
		Hashtable shortcuts = Menus.getShortcuts();
		addShortcutsToVector(shortcuts, v);
		Hashtable macroShortcuts = Menus.getMacroShortcuts();
		addShortcutsToVector(macroShortcuts, v);
		String[] list = new String[v.size()];
		v.copyInto((String[])list);
		return list;
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

	void showList(String title, String headings, String[] list) {
		Arrays.sort(list, String.CASE_INSENSITIVE_ORDER);
		ArrayList list2 = new ArrayList();
		for (int i=0; i<list.length; i++)
			list2.add(list[i]);
		TextWindow tw = new TextWindow(title, headings, list2, 600, 500);
	}
}
