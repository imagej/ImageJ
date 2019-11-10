package ij.plugin.frame;
import ij.*;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;


/** This plugin implements the Plugins>Utiltiees>Recent Commands command. */
public class Commands extends PlugInFrame implements ActionListener, ItemListener, CommandListener {
	public static final String LOC_KEY = "commands.loc";
	public static final int MAX_COMMANDS = 20;
	private static Frame instance;
	private List list;
	private String command;

	public Commands() {
		super("Commands");
		if (instance!=null) {
			WindowManager.toFront(instance);
			return;
		}
		instance = this;
		list = new List(MAX_COMMANDS);
		list.addItemListener(this);
		list.add("Blobs (25K)");
		list.add("Image...");
		list.add("Open...");
		list.add("Show Info...");
		list.add("Close");
		list.add("Close All");
		list.add("Invert");
		list.add("Gaussian Blur...");		
		list.add("Record...");
		list.add("Capture Screen");
		list.add("Monitor Memory...");
		list.add("Find Commands...");
		ImageJ ij = IJ.getInstance();
		addKeyListener(ij);
IJ.log("addCommandListener");
		Executer.addCommandListener(this);
		//WindowManager.addWindow(this);
		GUI.scale(list);
		list.addKeyListener(ij);
		add(list);
		pack();
		Dimension size = getSize();
		Point loc = Prefs.getLocation(LOC_KEY);
		if (loc!=null)
			setLocation(loc);
		show();
	}

	public void actionPerformed(ActionEvent e) {
		IJ.log("actionPerformed: "+e);
	}

	public void itemStateChanged(ItemEvent e) {
		//IJ.log("itemStateChanged: "+e);
		if (e.getStateChange()==ItemEvent.SELECTED) {
			int index = list.getSelectedIndex();
			command = list.getItem(index);
			IJ.doCommand(command);
			list.deselect(index);
		}
	}
	
	public String commandExecuting(String cmd2) {
		IJ.log("commandExecuting: "+cmd2);
		String cmd1 = command;
		if (cmd1==null || !cmd1.equals(cmd2)) {
			try {
				list.remove(cmd2);
			} catch(Exception e) {}
			list.add(cmd2, 0);
			if (list.getItemCount()>MAX_COMMANDS)
				list.remove(list.getItemCount()-1);
		}
		command = null;
		return cmd2;
	}
	
	/** Overrides PlugInFrame.close(). */
	public void close() {
		super.close();
		instance = null;
IJ.log("close");
		Executer.removeCommandListener(this);
		Prefs.saveLocation(LOC_KEY, getLocation());
	}


}
