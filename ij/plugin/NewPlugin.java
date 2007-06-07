package ij.plugin;
import java.awt.*;
import ij.*;
import ij.gui.*;
import ij.plugin.frame.Editor;

/** This class creates new plugins in Java source form. */
public class NewPlugin implements PlugIn {

	public static final int PLUGIN=0, PLUGIN_FILTER=1, PLUGIN_FRAME=2;
	
    private static int type = PLUGIN;
    private static String name = "Plugin_.java";
    private static String[] types = {"Plugin", "Filter", "Frame"};
    
    public void run(String arg) {
		if (arg.equals("")&&!showDialog())
			return;
		if (arg.equals(""))
    		createPlugin(name, type, arg);
    	else
    		createPlugin("Macro_.java", type, arg);
    	IJ.register(NewPlugin.class);
    }
    
	public void createPlugin(String name, int type, String methods) {
		Editor ed = (Editor)IJ.runPlugIn("ij.plugin.frame.Editor", "");
		if (ed==null)
			return;
		String pluginName = name;
		if (!(name.endsWith(".java") || name.endsWith(".JAVA")))
			pluginName += ".java";
		String className = pluginName.substring(0,pluginName.length()-5);
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
				text += "\t\tTextArea ta = new TextArea();\n";
				text += "\t\tadd(ta);\n";
				text += "\t\tpack();\n";
				text += "\t\tGUI.center(this);\n";
				text += "\t\tsetVisible(true);\n";
				text += "\t}\n";
				break;
		}
		text += "\n";
		text += "}\n";
		ed.create(pluginName, text);
	}
	
	public boolean showDialog() {
		GenericDialog gd = new GenericDialog("New Plugin...");
		gd.addStringField("Name:", name, 16);
		gd.addChoice("Type:", types, types[type]);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		name = gd.getNextString();
		type = gd.getNextChoiceIndex();
		return true;
	}

}