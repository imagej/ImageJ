package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import ij.*;
import ij.plugin.*;
import ij.gui.*;
import ij.util.*;

/**
Displays a panel with buttons for launching the plugins in the plugins folder.
*/
public class ControlPanel extends PlugInFrame implements PlugIn, ActionListener {

	public ControlPanel () {
		super("");
		if (IJ.getApplet()!=null)
			return;
		String[] list = getPlugins();
		if (list==null || list.length==0)
			{IJ.error("No plugins found"); return;}
		setLayout(new GridLayout(list.length, 1, 0, 0));
		StringSorter.sort(list);
		for (int i=0; i<list.length; i++) {
			Button b = new Button(list[i]);
			b.addActionListener(this);
			add(b);
		}
		pack();
		GUI.center(this);
		show();
	}
		
	String[] getPlugins() {
		String path = Menus.getPlugInsPath();
		if (path==null)
			return null;
		File f = new File(path);
		String[] list = f.list();
		if (list==null) return null;
		Vector v = new Vector();
		for (int i=0; i<list.length; i++) {
			String className = list[i];
			if (className.indexOf('_')>=0 && className.endsWith(".class")&& className.indexOf('$')<0 ) {
				className = className.substring(0, className.length()-6); 
				String cmd = className.replace('_',' ');
				v.addElement(cmd);
				
			}
		}
		list = new String[v.size()];
		v.copyInto((String[])list);
		return list;
	}
	
	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();
		if (cmd!=null)
			IJ.doCommand(cmd);
	}

}


