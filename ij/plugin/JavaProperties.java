package ij.plugin;
import ij.*;
import ij.text.*;
import java.awt.*;

/** Displays the Java system properties in a text window. */
public class JavaProperties implements PlugIn {

	TextWindow tw;
	
	public void run(String arg) {
	
		tw = new TextWindow("Properties", "", 300, 400);
		tw.append("");
		tw.append("Java properties applets can read:");
		show("java.version");
		show("java.vendor");
		show("java.vendor.url");
		show("java.class.version");
		if (IJ.isMacintosh()) show("mrj.version");
		show("os.name");
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
		tw.append("  line.separator: " + str1 + str2);
			
		if (IJ.getApplet()!=null)
			return;
		tw.append("");
		tw.append("Java properties only applications can read:");
		show("user.name");
		show("user.home");
		show("user.dir");
		show("java.home");
		show("java.compiler");
		show("java.class.path");
		
		tw.append("");
		tw.append("Other properties:");
		String userDir = System.getProperty("user.dir");
		String userHome = System.getProperty("user.home");
		String osName = System.getProperty("os.name");
		String prefsDir = osName.indexOf("Windows",0)>-1?userDir:userHome;
		tw.append("  prefs dir: "+prefsDir);
		tw.append("  plugins dir: "+Menus.getPlugInsPath());
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		tw.append("  screen size: " + d.width + "x" + d.height);
		String mem = IJ.freeMemory();
		tw.append("  memory"+mem.substring(6,mem.length()));
	}
	
	void show(String property) {
		String p = System.getProperty(property);
		if (p!=null)
			tw.append("  " + property + ": " + p);
	}

}
