package ij.plugin;
import ij.*;
import java.io.*;

/** Checks for duplicate class files in the plugins directory. */
public class Verifier implements PlugIn {

	public void run(String arg) {
		verify();
	}
	
	void verify() {
		String path = Menus.getPlugInsPath();
		File f = new File(path);
		String[] list = f.list();
		if (list==null)
			return;
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<list.length; i++) {
			String name = list[i];
			if (name.endsWith(".class"))
				sb.append(name);
		}
		String classesInMainDir = new String(sb);
		
		for (int i=0; i<list.length; i++) {
			f=new File(path, list[i]);
			if (f.isDirectory()) {
				String[] list2 = f.list();
				if (list2!=null)
					for (int j=0; j<list2.length; j++) {
						String name = list2[j];
						if (name.endsWith(".class") && classesInMainDir.indexOf(name)>=0) {
							IJ.write("Deleting duplicate class: /plugins/"+name+" ("+list[i]+")");
							new File(path+name).delete();
						}
					}
			}
		}
	}
	
}
