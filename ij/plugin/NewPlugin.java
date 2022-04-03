package ij.plugin;
import java.awt.*;
import ij.*;
import ij.gui.*;
import ij.plugin.frame.Editor;
import ij.text.TextWindow;
import ij.io.SaveDialog;
import ij.util.Tools;

/** This class creates a new macro or the Java source for a new plugin. */
public class NewPlugin implements PlugIn {

	public static final int MACRO=0, JAVASCRIPT=1, PLUGIN=2, PLUGIN_FILTER=3, PLUGIN_FRAME=4,
		TEXT_FILE=5, TABLE=6, MACRO_TOOL=7, PLUGIN_TOOL=8, TEMPLATE=9;
    private static int rows = 24;
    private static int columns = 80;
    private static int tableWidth = 350;
    private static int tableHeight = 250;
    private int type = MACRO;
    private String name = "Macro.ijm";
    private boolean monospaced;
    private boolean menuBar = true;
	private Editor ed;
    
    public void run(String arg) {
    	type = -1;
    	if (arg.startsWith("text")||arg.equals("")) {
    		type = TEXT_FILE;
    		if (IJ.altKeyDown())
    			name = "Untitled.ijm";
    		else
    			name = "Untitled.txt";
    	} else if (arg.equals("macro")) {
    		type = MACRO;
    		name = "Macro.ijm";
    	} else if (arg.equals("macro-tool")) {
    		type = TEMPLATE;
    		name = "Circle_Tool.txt";
    	} else if (arg.equals("javascript")) {
    		type = JAVASCRIPT;
    		name = "Script.js";
     	} else if (arg.equals("plugin")) {
    		type = TEMPLATE;
    		name = "My_Plugin.src";
    	} else if (arg.equals("frame")) {
    		type = TEMPLATE;
    		name = "Plugin_Frame.src";
    	} else if (arg.equals("plugin-tool")) {
    		type = TEMPLATE;
    		name = "Prototype_Tool.src";
    	} else if (arg.equals("filter")) {
    		type = TEMPLATE;
    		name = "Filter_Plugin.src";
    	} else if (arg.equals("table")) {
			String options = Macro.getOptions();
			if  (IJ.isMacro() && options!=null && options.indexOf("[Text File]")!=-1) {
    			type = TEXT_FILE;
    			name = "Untitled.txt";
    			arg = "text+dialog";
    		} else {
    			type = TABLE;
    			name = "Table";
    		}
    	}
    	menuBar = true;
    	if (arg.equals("text+dialog") || type==TABLE) {
			if (!showDialog()) return;
		}
		if (type==-1)
    		createPlugin("Converted_Macro.java", PLUGIN, arg);
		else if (type==TEMPLATE || type==MACRO || type==TEXT_FILE || type==JAVASCRIPT) {
			if (type==TEXT_FILE && name.equals("Macro"))
				name = "Untitled.txt";
			createMacro(name);
		} else if (type==TABLE)
			createTable();
		else
			createPlugin(name, type, arg);
    }
    
	public void createMacro(String name) {
		int options = (monospaced?Editor.MONOSPACED:0)+(menuBar?Editor.MENU_BAR:0);
		if (name.endsWith(".ijm") || name.endsWith(".js"))
			options |= Editor.RUN_BAR;
		if (name.endsWith(".ijm"))
			options |= Editor.INSTALL_BUTTON;
		String text = "";
		ed = new Editor(rows, columns, 0, options);
		if (type==TEMPLATE)
			text = Tools.openFromIJJarAsString("/macros/"+name);
		if (name.endsWith(".src"))
			name = name.substring(0,name.length()-4) + ".java";
		if (type==MACRO && !name.endsWith(".ijm"))
			name = SaveDialog.setExtension(name, ".ijm");
		else if (type==JAVASCRIPT && !name.endsWith(".js")) {
			if (name.equals("Macro")) name = "script";
			name = SaveDialog.setExtension(name, ".js");
		}
		if (text!=null)
			ed.create(name, text);
	}
	
	void createTable() {
			new TextWindow(name, "", tableWidth, tableHeight);
	}

	public void createPlugin(String name, int type, String methods) {
  		ed = (Editor)IJ.runPlugIn("ij.plugin.frame.Editor", "");
		if (ed==null) return;
		String pluginName = name;
		if (!(name.endsWith(".java") || name.endsWith(".JAVA")))
			name = SaveDialog.setExtension(name, ".java");
		String className = pluginName.substring(0, pluginName.length()-5);
		String text = "";
		text += "import ij.*;\n";
		text += "import ij.process.*;\n";
		text += "import ij.gui.*;\n";
		text += "import java.awt.*;\n";
		text += "import ij.plugin.*;\n";
		text += "\n";
		text += "public class "+className+" implements PlugIn {\n";
		text += "\n";
		text += "\tpublic void run(String arg) {\n";
		text += methods;
		text += "\t}\n";
		text += "\n";
		text += "}\n";
		ed.create(pluginName, text);
	}
	
	public boolean showDialog() {
		String title;
		String widthUnit, heightUnit;
		int width, height;
		if (type==TABLE) {
			title = "New Table";
			name = "Table";
			width = tableWidth;
			height = tableHeight;
			widthUnit = "pixels";
			heightUnit = "pixels";
		} else {
			title = "New Text Window";
			name = "Untitled";
			width = columns;
			height = rows;
			widthUnit = "characters";
			heightUnit = "lines";
		}
		GenericDialog gd = new GenericDialog(title);
		gd.addStringField("Name:", name, 16);
		gd.addMessage("");
		gd.addNumericField("Width:", width, 0, 3, widthUnit);
		gd.addNumericField("Height:", height, 0, 3, heightUnit);
		if (type!=TABLE) {
			gd.setInsets(5, 30, 0);
			gd.addCheckbox("Menu Bar", true);
			gd.setInsets(0, 30, 0);
			gd.addCheckbox("Monospaced Font", monospaced);
		}
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		name = gd.getNextString();
		width = (int)gd.getNextNumber();
		height = (int)gd.getNextNumber();
		if (width<1) width = 1;
		if (height<1) height = 1;
		if (type!=TABLE) {
			menuBar = gd.getNextBoolean();
			monospaced = gd.getNextBoolean();
			columns = width;
			rows = height;
			if (rows>100) rows = 100;
			if (columns>200) columns = 200;
		} else {
			tableWidth = width;
			tableHeight = height;
			if (tableWidth<128) tableWidth = 128;
			if (tableHeight<75) tableHeight = 75;
		}
		return true;
	}
	
	/** Returns the Editor the newly created macro or plugin was opened in. */
	public Editor getEditor() {
		return ed;
	}

}
