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
		private static String KEY = "startup.macro";
		private GenericDialog gd;
		private static final String[] code = {
			"[Select from list]",
			"Black background",
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
		StringBuilder sb = new StringBuilder();
		originalLength = 0;
		for (int i=0; i<100; i++) {
			String line = Prefs.get(KEY+(i/10)%10+i%10, null);
			if (line==null)
				break;
			else {
				originalLength++;
				sb.append(line);
				sb.append("\n");
			}
		}
		return sb.toString();
	}
		
	private void saveStartupMacro(String macro) {
		String[] lines = macro.split("\n");
		int n = Math.max(lines.length, originalLength);
		for (int i=0; i<n; i++) {
			if (i<lines.length)
				Prefs.set(KEY+(i/10)%10+i%10, lines[i]);
			else
				Prefs.set(KEY+(i/10)%10+i%10, null);
		}
	}

	private boolean showDialog() {
		gd = new GenericDialog("Startup Macro");
		String text = "Macro code contained in the following text\narea will be executed when ImageJ starts up.";
		Font font = new Font("SansSerif", Font.PLAIN, 14);
		gd.setInsets(5,15,0);
		gd.addMessage(text, font);
		gd.setInsets(5, 10, 0);
		gd.addTextAreas(macro, null, 12, 50);
		gd.addChoice("Add code:", code, code[0]);
		Vector choices = gd.getChoices();
		Choice choice = (Choice)choices.elementAt(0);
		choice.addItemListener(this);
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
			statement = "call(\"ij.ImagePlus.setDefault16bitRange\", 10);\n";
		else if (item.equals(code[3]))
			statement = "call(\"ij.ImagePlus.setDefault16bitRange\", 12);\n";
		if (statement!=null) {
			TextArea ta = gd.getTextArea1();
			ta.insert(statement, ta.getCaretPosition());
			if (IJ.isMacOSX()) ta.requestFocus();
		}
	}

}
