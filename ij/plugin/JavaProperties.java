package ij.plugin;
import ij.*;
import ij.text.*;
import java.awt.*;

/** Displays the Java system properties in a text window. */
public class JavaProperties implements PlugIn {

	StringBuffer sb = new StringBuffer();
	
	public void run(String arg) {
		sb.append("\n");
		sb.append("Java properties applets can read:\n");
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
		sb.append("  line.separator: " + str1 + str2+"\n");
			
		if (IJ.getApplet()!=null)
			return;
		sb.append("\n");
		sb.append("Java properties only applications can read:\n");
		show("user.name");
		show("user.home");
		show("user.dir");
		show("java.home");
		show("java.compiler");
		show("java.class.path");
		
		sb.append("\n");
		sb.append("Other properties:\n");
		String userDir = System.getProperty("user.dir");
		String userHome = System.getProperty("user.home");
		String osName = System.getProperty("os.name");
		String prefsDir = osName.indexOf("Windows",0)>-1?userDir:userHome;
		sb.append("  version: "+IJ.getInstance().VERSION+"\n");
		sb.append("  prefs dir: "+prefsDir+"\n");
		sb.append("  plugins dir: "+Menus.getPlugInsPath()+"\n");
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		sb.append("  screen size: " + d.width + "x" + d.height+"\n");
		String mem = IJ.freeMemory();
		sb.append("  memory in use"+mem.substring(6,mem.length())+"\n");
		TextWindow tw = new TextWindow("Properties", new String(sb), 300, 400);
	}
	
	void show(String property) {
		String p = System.getProperty(property);
		if (p!=null)
			sb.append("  " + property + ": " + p+"\n");
	}

}
