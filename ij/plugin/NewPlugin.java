package ij.plugin;
import java.awt.*;
import ij.*;
import ij.gui.*;
import ij.plugin.frame.Editor;

/** This class creates a new macro or the Java source for a new plugin. */
public class NewPlugin implements PlugIn {

	public static final int MACRO=0, PLUGIN=1, PLUGIN_FILTER=2, PLUGIN_FRAME=3, TEXT_FILE=4;
	
    private static int type = MACRO;
    private static String name = "Macro";
    private static String[] types = {"Macro", "Plugin", "Plugin Filter", "Plugin Frame", "Text File"};
	private Editor ed;
    
    public void run(String arg) {
		if (arg.equals("")&&!showDialog())
			return;
		if (arg.equals("")) {
			if (type==MACRO || type==TEXT_FILE) {
				if (type==TEXT_FILE && name.equals("Macro"))
					name = "Untitled.txt";
    			createMacro(name);
    		} else
    			createPlugin(name, type, arg);
    	} else
    		createPlugin("Converted_Macro.java", PLUGIN, arg);
    	IJ.register(NewPlugin.class);
    }
    
	public void createMacro(String name) {
		ed = (Editor)IJ.runPlugIn("ij.plugin.frame.Editor", "");
		if (ed==null)
			return;
		if (name.endsWith(".java"))
			name = name.substring(0, name.length()-5);
		//if (name.endsWith("_"))
		//	name = name.substring(0, name.length()-1);
		if (!(name.endsWith(".txt")))
			name += ".txt";
		ed.create(name, "");
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
		if (!(name.endsWith(".java") || name.endsWith(".JAVA"))) {
			if (pluginName.endsWith(".txt"))
				pluginName = pluginName.substring(0, pluginName.length()-4);
			pluginName += ".java";
		}
		//if (name.indexOf('_')==-1) {
		//	pluginName = pluginName.substring(0, pluginName.length()-5);
		//	pluginName = pluginName + "_.java";
		//}
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
		GenericDialog gd = new GenericDialog("New...");
		gd.addStringField("Name:", name, 16);
		gd.addChoice("Type:", types, types[type]);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		name = gd.getNextString();
		type = gd.getNextChoiceIndex();
		return true;
	}
	
	/** Returns the Editor the newly created macro or plugin was opened in. */
	public Editor getEditor() {
		return ed;
	}

}