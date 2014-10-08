package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.macro.Interpreter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Vector;

/** This plugin implements the Edit/Options/Startup command. */
	public class Startup implements PlugIn, ItemListener {
		private static String NAME = "RunAtStartup.ijm";
		private GenericDialog gd;
		private static final String[] code = {
			"[Select from list]",
			"Black background",
			"Debug mode",
			"10-bit (0-1023) range",
			"12-bit (0-4095) range"
		};
	private String macro = "";
	private int originalLength;

	public void run(String arg) {
		macro = getStartupMacro();
		String macro2 = macro;
		if (!showDialog())
			return;
		if (!macro.equals(macro2)) {
			if (!runMacro(macro))
				return;
			saveStartupMacro(macro);
		}
	}
	
	public String getStartupMacro() {
		String macro = IJ.openAsString(IJ.getDirectory("macros")+NAME);
		if (macro==null || macro.startsWith("Error:"))
			return null;
		else
			return macro;
	}
		
	private void saveStartupMacro(String macro) {
		IJ.saveString(macro, IJ.getDirectory("macros")+NAME);
	}

	private boolean showDialog() {
		gd = new GenericDialog("Startup Macro");
		String text = "Macro code contained in this text area\nexecutes when ImageJ starts up.";
		Font font = new Font("SansSerif", Font.PLAIN, 14);
		gd.setInsets(5,15,0);
		gd.addMessage(text, font);
		gd.setInsets(5, 10, 0);
		gd.addTextAreas(macro, null, 12, 50);
		gd.addChoice("Add code:", code, code[0]);
		Vector choices = gd.getChoices();
		if (choices!=null) {
			Choice choice = (Choice)choices.elementAt(0);
			choice.addItemListener(this);
		}
		gd.showDialog();
		macro = gd.getNextText();
		return !gd.wasCanceled();
	}
	
	private boolean runMacro(String macro) {
		Interpreter interp = new Interpreter();
		interp.run(macro, null);
		if (interp.wasError())
			return false;
		else
			return true;
	}
				
	public void itemStateChanged(ItemEvent e) {
		Choice choice = (Choice)e.getSource();
		String item = choice.getSelectedItem();
		String statement = null;
		if (item.equals(code[1]))
			statement = "setOption(\"BlackBackground\", true);\n";
		else if (item.equals(code[2]))
			statement = "setOption(\"DebugMode\", true);\n";
		else if (item.equals(code[3]))
			statement = "call(\"ij.ImagePlus.setDefault16bitRange\", 10);\n";
		else if (item.equals(code[4]))
			statement = "call(\"ij.ImagePlus.setDefault16bitRange\", 12);\n";
		if (statement!=null) {
			TextArea ta = gd.getTextArea1();
			ta.insert(statement, ta.getCaretPosition());
			if (IJ.isMacOSX()) ta.requestFocus();
		}
	}

}
