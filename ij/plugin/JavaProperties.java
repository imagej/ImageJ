package ij.plugin;
import ij.*;
import ij.text.*;
import ij.io.OpenDialog;
import java.awt.*;
import java.util.*;
import java.applet.Applet;

/** Displays the Java system properties in a text window. */
public class JavaProperties implements PlugIn {

	StringBuffer sb = new StringBuffer();
	
	public void run(String arg) {
		sb.append("\n");
		sb.append("Java properties applets can read:\n");
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
		sb.append("  line.separator: " + str1 + str2+"\n");
			
		Applet applet = IJ.getApplet();
		if (applet!=null) {
			sb.append("\n");
			sb.append("  code base: "+applet.getCodeBase()+"\n");
			sb.append("  document base: "+applet.getDocumentBase()+"\n");
			sb.append("  sample images dir: "+Prefs.getImagesURL()+"\n");
			TextWindow tw = new TextWindow("Properties", new String(sb), 400, 400);
			return;
		}
		sb.append("\n");
		sb.append("Java properties only applications can read:\n");
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
		
		sb.append("\n");
		sb.append("Other properties:\n");
		String userDir = System.getProperty("user.dir");
		String userHome = System.getProperty("user.home");
		String osName = System.getProperty("os.name");
		String prefsDir = osName.indexOf("Windows",0)>-1?userDir:userHome;
		if (IJ.isMacOSX()) prefsDir = prefsDir + "/Library/Preferences";
		sb.append("  version: "+IJ.getInstance().VERSION+"\n");
		sb.append("  java 2: "+IJ.isJava2()+"\n");
		sb.append("  java 1.4: "+IJ.isJava14()+"\n");
		sb.append("  prefs dir: "+prefsDir+"\n");
		sb.append("  imagej dir: "+Prefs.getHomeDir()+"\n");
		sb.append("  plugins dir: "+Menus.getPlugInsPath()+"\n");
		sb.append("  macros dir: "+Menus.getMacrosPath()+"\n");
		sb.append("  current dir: "+OpenDialog.getDefaultDirectory()+"\n");
		sb.append("  sample images dir: "+Prefs.getImagesURL()+"\n");
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		sb.append("  screen size: " + d.width + "x" + d.height+"\n");
		sb.append("  memory in use: "+IJ.freeMemory()+"\n");		
		if (IJ.altKeyDown())
			doFullDump();
		TextWindow tw = new TextWindow("Properties", new String(sb), 300, 400);
	}
	
	void show(String property) {
		String p = System.getProperty(property);
		if (p!=null)
			sb.append("  " + property + ": " + p+"\n");
	}
	
	void doFullDump() {
		sb.append("\n");
		sb.append("All Properties:\n");
		Properties props = System.getProperties();
		for (Enumeration en=props.keys(); en.hasMoreElements();) {
			String key = (String)en.nextElement();
			sb.append("  "+key+": "+(String)props.get(key)+"\n");
		}
	}

}
