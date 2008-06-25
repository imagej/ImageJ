package ij.plugin;
import java.awt.*;
import ij.*;
import ij.gui.*;
import ij.plugin.frame.Editor;
import ij.text.TextWindow;
import ij.io.SaveDialog;

/** This class creates a new macro or the Java source for a new plugin. */
public class NewPlugin implements PlugIn {

	public static final int MACRO=0, JAVASCRIPT=1, PLUGIN=2, PLUGIN_FILTER=3, PLUGIN_FRAME=4, TEXT_FILE=5, TABLE=6;
	
    private static int type = MACRO;
    private static String name = "Macro";
    private static String[] types = {"Macro", "JavaScript", "Plugin", "Plugin Filter", "Plugin Frame", "Text File", "Table"};
    private static int rows = 16;
    private static int columns = 60;
    private static boolean monospaced;
    private boolean menuBar = true;
	private Editor ed;
	private int saveType = type;
	private String saveName = name;
	private int saveRows = rows;
	private int saveColumns = columns;
	private boolean saveMonospaced = monospaced;
    
    public void run(String arg) {
		if (arg.equals("")&&!showDialog())
			return;
		if (arg.equals("text")) {
			type = TEXT_FILE;
			arg = "";
		}
		if (arg.equals("")) {
			if (type==MACRO || type==TEXT_FILE || type==JAVASCRIPT) {
				if (type==TEXT_FILE && name.equals("Macro"))
					name = "Untitled.txt";
    			createMacro(name);
    		} else if (type==TABLE) {
    			createTextWindow();
    		} else
    			createPlugin(name, type, arg);
    	} else
    		createPlugin("Converted_Macro.java", PLUGIN, arg);
    	if (IJ.macroRunning()) {
			type = saveType;
			name = saveName;
			rows = saveRows;
			columns = saveColumns;
			monospaced = saveMonospaced;
    	}
    	IJ.register(NewPlugin.class);
    }
    
	public void createMacro(String name) {
		int options = (monospaced?Editor.MONOSPACED:0)+(menuBar?Editor.MENU_BAR:0);
		ed = new Editor(rows, columns, 0, options);
		if (type==MACRO && !name.endsWith(".txt"))
			name = SaveDialog.setExtension(name, ".txt");
		else if (type==JAVASCRIPT && !name.endsWith(".js")) {
			if (name.equals("Macro")) name = "script";
			name = SaveDialog.setExtension(name, ".js");
		}
		ed.create(name, "");
	}
	
	void createTextWindow() {
		String tableName = name;
		if (tableName.equals("Macro")) tableName = "Table";
		if (columns<128 || rows<75 )
			new TextWindow(tableName, "", 350, 250);
		else
			new TextWindow(tableName, "", columns, rows);
	}

	public void createPlugin(String name, int type, String methods) {
  		ed = (Editor)IJ.runPlugIn("ij.plugin.frame.Editor", "");
		if (ed==null) return;
		if (name.equals("Macro") || name.equals("Macro.txt") || name.equals("Untitled.txt")) {
			switch (type) {
				case PLUGIN: name = "My_Plugin.java"; break;
				case PLUGIN_FILTER:  name = "Filter_Plugin.java"; break;
				case PLUGIN_FRAME:  name = "Plugin_Frame.java"; break;
			}
		}
		String pluginName = name;
		if (!(name.endsWith(".java") || name.endsWith(".JAVA")))
			name = SaveDialog.setExtension(name, ".java");
		String className = pluginName.substring(0, pluginName.length()-5);
		String text = "";
		text += "import ij.*;\n";
		text += "import ij.process.*;\n";
		text += "import ij.gui.*;\n";
		text += "import java.awt.*;\n";
		switch (type) {
			case PLUGIN:
				text += "import ij.plugin.*;\n";
				text += "\n";
				text += "public class "+className+" implements PlugIn {\n";
				text += "\n";
				text += "\tpublic void run(String arg) {\n";
				if (methods.equals(""))
					text += "\t\tIJ.showMessage(\""+className+"\",\"Hello world!\");\n";
				else
					text += methods;
				text += "\t}\n";
				break;
			case PLUGIN_FILTER:
				text += "import ij.plugin.filter.*;\n";
				text += "\n";
				text += "public class "+className+" implements PlugInFilter {\n";
				text += "\tImagePlus imp;\n";
				text += "\n";
				text += "\tpublic int setup(String arg, ImagePlus imp) {\n";
				text += "\t\tthis.imp = imp;\n";
				text += "\t\treturn DOES_ALL;\n";
				text += "\t}\n";
				text += "\n";
				text += "\tpublic void run(ImageProcessor ip) {\n";
				text += "\t\tip.invert();\n";
				text += "\t\timp.updateAndDraw();\n";
				text += "\t\tIJ.wait(500);\n";
				text += "\t\tip.invert();\n";
				text += "\t\timp.updateAndDraw();\n";
				text += "\t}\n";
				break;
			case PLUGIN_FRAME:
				text += "import ij.plugin.frame.*;\n";
				text += "\n";
				text += "public class "+className+" extends PlugInFrame {\n";
				text += "\n";
				text += "\tpublic "+className+"() {\n";
				text += "\t\tsuper(\""+className+"\");\n";
				text += "\t\tTextArea ta = new TextArea(15, 50);\n";
				text += "\t\tadd(ta);\n";
				text += "\t\tpack();\n";
				text += "\t\tGUI.center(this);\n";
				text += "\t\tshow();\n";
				text += "\t}\n";
				break;
		}
		text += "\n";
		text += "}\n";
		ed.create(pluginName, text);
	}
	
	public boolean showDialog() {
		GenericDialog gd = new GenericDialog("New Text Window");
		gd.addStringField("Name:", name, 16);
		gd.addChoice("Type:", types, types[type]);
		gd.addMessage("");
		gd.addNumericField("Width:", columns, 0, 3, "characters");
		gd.addNumericField("Height:", rows, 0, 3, "lines");
		gd.setInsets(5, 30, 0);
		gd.addCheckbox("Menu Bar", menuBar);
		gd.setInsets(0, 30, 0);
		gd.addCheckbox("Monospaced Font", monospaced);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		name = gd.getNextString();
		type = gd.getNextChoiceIndex();
		columns = (int)gd.getNextNumber();
		rows = (int)gd.getNextNumber();
		menuBar = gd.getNextBoolean();
		monospaced = gd.getNextBoolean();
		if (rows<1) rows = 1;
		if (type!=TABLE && rows>100) rows = 100;
		if (columns<1) columns = 1;
		if (type!=TABLE && columns>200) columns = 200;
		return true;
	}
	
	/** Returns the Editor the newly created macro or plugin was opened in. */
	public Editor getEditor() {
		return ed;
	}

}