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

	private static Frame instance;

	public ControlPanel () {
		super("Plugins");
		if (IJ.getApplet()!=null)
			return;
		if (instance!=null) {
			instance.toFront();
			return;
		}
		instance = this;
		IJ.register(ControlPanel.class);
		WindowManager.addWindow(this);
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();

		String[] plugins = Menus.getPlugins();
		if (plugins==null)
			return;
		plugins = getStrippedPlugins(plugins);
		if (plugins==null || plugins.length==0)
			{IJ.error("No plugins found"); return;}
		StringSorter.sort(plugins);
		int n = plugins.length;
		boolean useScrollPane = n>screen.height/25;

		//int n = Math.min(plugins.length, 30);
		Panel p = new Panel();
		p.setLayout(new GridLayout(n, 1, 0, 0));
		Button[] button = new Button[n];
		for (int i=0; i<n; i++) {
			button[i] = new Button(plugins[i]);
			button[i].addActionListener(this);
			p.add(button[i]);
		}
		ScrollPane sp;
		if (useScrollPane) {
			sp = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
			sp.add(p);
			add(sp);
			pack();
			Dimension size;
			int width=0, height=0;
			for (int i=0; i<n; i++) {
				size = button[i].getPreferredSize();
				if (size.width>width)
					width = size.width;
				height += size.height;
			}
			int maxHeight = (int)(0.67*screen.height);
			if (height>maxHeight)
				height = maxHeight;
			sp.setSize(width+30, height);
		} else
			add(p);
		pack();

		GUI.center(this);
		show();
	}
		
	/** Removes directory names and underscores. */
	String[] getStrippedPlugins(String[] plugins) {
		String[] plugins2 = new String[plugins.length];
		int slashPos;
		for (int i=0; i<plugins2.length; i++) {
			plugins2[i] = plugins[i];
			slashPos = plugins2[i].lastIndexOf('/');
			if (slashPos>=0)
				plugins2[i] = plugins[i].substring(slashPos+1,plugins2[i].length());
			plugins2[i] = plugins2[i].replace('_',' ');

		}
		return plugins2;
	}

	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();
		if (cmd!=null)
			IJ.doCommand(cmd);
	}

    public void windowClosing(WindowEvent e) {
		super.windowClosing(e);
		instance = null;
	}

}


