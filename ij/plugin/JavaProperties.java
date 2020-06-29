package ij.plugin;
import ij.*;
import ij.text.*;
import ij.io.OpenDialog;
import ij.gui.GUI;
import java.awt.*;
import java.util.*;
import java.applet.Applet;

/** Displays the Java system properties in a text window. */
public class JavaProperties implements PlugIn {

	ArrayList list = new ArrayList();
	
	public void run(String arg) {
		show("java.version");
		show("java.vendor");
		if (IJ.isMacintosh()) show("mrj.version");
		show("os.name");
		show("os.version");
		show("os.arch");
		show("file.separator");
		show("path.separator");
		
		String s = System.getProperty("line.separator");
		char ch1, ch2;
		String str1, str2="";
		ch1 = s.charAt(0);
		if (ch1=='\r')
			str1 = "<cr>";
		else
			str1 = "<lf>";
		if (s.length()==2) {
			ch2 = s.charAt(1);
			if (ch2=='\r')
				str2 = "<cr>";
			else
				str2 = "<lf>";
		}
		list.add("  line.separator: " + str1 + str2);
			
		Applet applet = IJ.getApplet();
		if (applet!=null) {
			list.add("");
			list.add("  code base: "+applet.getCodeBase());
			list.add("  document base: "+applet.getDocumentBase());
			list.add("  sample images dir: "+Prefs.getImagesURL());
			TextWindow tw = new TextWindow("Properties", "", list, 400, 400);
			return;
		}
		list.add("");
		show("user.name");
		show("user.home");
		show("user.dir");
		show("user.country");
		show("file.encoding");
		show("java.home");
		show("java.compiler");
		show("java.class.path");
		show("java.ext.dirs");
		show("java.io.tmpdir");
		
		list.add("");
		String userDir = System.getProperty("user.dir");
		String userHome = System.getProperty("user.home");
		String osName = System.getProperty("os.name");
		String path = Prefs.getCustomPropsPath();
		if (path!=null)
			list.add("  *Custom properties*: "+path);
		path = Prefs.getCustomPrefsPath();
		if (path!=null)
			list.add("  *Custom preferences*: "+path);
		list.add("  IJ.getVersion: "+IJ.getVersion());
		list.add("  IJ.getFullVersion: "+IJ.getFullVersion());
		list.add("  IJ.javaVersion: "+IJ.javaVersion());
		list.add("  IJ.isJava18(): "+IJ.isJava18());
		list.add("  IJ.isLinux: "+IJ.isLinux());
		list.add("  IJ.isMacintosh: "+IJ.isMacintosh());
		list.add("  IJ.isMacOSX: "+IJ.isMacOSX());
		list.add("  IJ.isWindows: "+IJ.isWindows());
		list.add("  IJ.is64Bit: "+IJ.is64Bit());
		list.add("");
		list.add("  IJ.getDir(\"imagej\"): "+ IJ.getDir("imagej"));
		list.add("  IJ.getDir(\"home\"): "+ IJ.getDir("home"));
		list.add("  IJ.getDir(\"plugins\"): "+ IJ.getDir("plugins"));
		list.add("  IJ.getDir(\"macros\"): "+ IJ.getDir("macros"));
		list.add("  IJ.getDir(\"luts\"): "+ IJ.getDir("luts"));
		list.add("  IJ.getDir(\"current\"): "+ IJ.getDir("current"));
		list.add("  IJ.getDir(\"temp\"): "+ IJ.getDir("temp"));
		list.add("  IJ.getDir(\"default\"): "+ IJ.getDir("default"));
		list.add("  IJ.getDir(\"image\"): "+ IJ.getDir("image"));
		list.add("");
		
		list.add("  Menus.getPlugInsPath: "+Menus.getPlugInsPath());
		list.add("  Menus.getMacrosPath: "+Menus.getMacrosPath());
		list.add("  Prefs.getImageJDir: "+Prefs.getImageJDir());		
		list.add("  Prefs.getThreads: "+Prefs.getThreads()+cores());	
		list.add("  Prefs.open100Percent: "+Prefs.open100Percent);		
		list.add("  Prefs.blackBackground: "+Prefs.blackBackground);		
		list.add("  Prefs.useJFileChooser: "+Prefs.useJFileChooser);		
		list.add("  Prefs.weightedColor: "+Prefs.weightedColor);		
		list.add("  Prefs.blackCanvas: "+Prefs.blackCanvas);		
		list.add("  Prefs.pointAutoMeasure: "+Prefs.pointAutoMeasure);		
		list.add("  Prefs.pointAutoNextSlice: "+Prefs.pointAutoNextSlice);		
		list.add("  Prefs.requireControlKey: "+Prefs.requireControlKey);		
		list.add("  Prefs.useInvertingLut: "+Prefs.useInvertingLut);		
		list.add("  Prefs.antialiasedTools: "+Prefs.antialiasedTools);		
		list.add("  Prefs.useInvertingLut: "+Prefs.useInvertingLut);		
		list.add("  Prefs.intelByteOrder: "+Prefs.intelByteOrder);			
		list.add("  Prefs.noPointLabels: "+Prefs.noPointLabels);		
		list.add("  Prefs.disableUndo: "+Prefs.disableUndo);		
		list.add("  Prefs dir: "+Prefs.getPrefsDir());
		list.add("  Current dir: "+OpenDialog.getDefaultDirectory());
		list.add("  Sample images dir: "+Prefs.getImagesURL());
		list.add("  Memory in use: "+IJ.freeMemory());	
		Rectangle s1 = GUI.getScreenBounds(); // primary screen
		Rectangle s2 = GUI.getScreenBounds(IJ.getInstance()); // screen with "ImageJ" window
		if (s1.equals(s2))
			list.add("  Screen size: " + s1.width + "x" + s1.height);
		else {
			list.add("  Size of primary screen: " + s1.width + "x" + s1.height);
			list.add("  Size of \"ImageJ\" screen: " + s2.width + "x" + s2.height);
		}
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		list.add("  Max window bounds: " + toString(GUI.getMaxWindowBounds(IJ.getInstance())));
		listMonitors(ge, list);
		System.gc();
		doFullDump();
		if (IJ.getInstance()==null) {
			for (int i=0; i<list.size(); i++)
				IJ.log((String)list.get(i));
		} else
			new TextWindow("Properties", "", list, 400, 500);
	}
	
	private void listMonitors(GraphicsEnvironment ge, ArrayList list) {
		int max = 10;
		String[] str = new String[max];
		int n = 0;
		Rectangle bounds2 = null;
		GraphicsDevice[] gs = ge.getScreenDevices();
		for (int j=0; j<gs.length; j++) {
			GraphicsDevice gd = gs[j];
			GraphicsConfiguration[] gc = gd.getConfigurations();
			for (int i=0; i<gc.length; i++) {
				Rectangle bounds = gc[i].getBounds();
				if (bounds!=null && !bounds.equals(bounds2) && n<max) {
					str[n++] = toString(bounds);
					bounds2 = bounds;
				}
			}
		}
		if (n>1) {
			for (int i=0; i<n; i++)
				list.add("  Monitor"+(i+1)+": " + str[i]);
		}
	}

	private String toString(Rectangle r) {
		if (r==null) return "";
		String s = r.toString();
		return s.substring(19, s.length()-1);
	}
	
	String cores() {
		int cores = Runtime.getRuntime().availableProcessors();
		if (cores==1)
			return " (1 core)";
		else
			return " ("+cores+" cores)";
	}
	
	void show(String property) {
		String p = System.getProperty(property);
		if (p!=null)
			list.add("  " + property + ": " + p);
	}
	
	void doFullDump() {
		list.add("");
		list.add("All Java Properties");
		Properties props = System.getProperties();
		for (Enumeration en=props.keys(); en.hasMoreElements();) {
			String key = (String)en.nextElement();
			list.add("  "+key+": "+(String)props.get(key));
		}
	}

}
