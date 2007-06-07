package ij.io;
import java.io.*;
import java.net.*;
import java.util.*;

/** ImageJ uses this class loader to load plugins from the plugins
	folder. It's based on the FileClassLoader from
	"Java Class Libraries: Second Edition, Vol. 1"
	(http://java.sun.com/docs/books/chanlee/second_edition/vol1/).
*/
public class PluginClassLoader extends ClassLoader {
	String path;
	Hashtable cache = new Hashtable();
	public PluginClassLoader(String path) {
		this.path = path;
	}

	protected synchronized Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
		Class c = (Class)cache.get(name); // try to find in cache
		//ij.IJ.log("loadClass: "+name+" ("+((c == null) ? "not in cache" : "in cache")+"), "+resolve);
       
		if (c == null) {  // Not in cache
			try {
				return findSystemClass(name);  // try system class loader
			} catch (ClassNotFoundException e) {}
			c = loadIt(path, name);   // Try to get it from plugins folder
			if (c==null)
				c = loadFromSubdirectory(path, name);  // Try to get it from subfolders
			if (c==null) {
				// try loading from ij.jar
				try {c = Class.forName(name);}
				catch (Exception e) {c=null;}
			}
			if (c==null)
				throw new ClassNotFoundException(name);
		}

		// Link class if asked to do so
		if (c != null && resolve)
			resolveClass(c);
		return c;
	}
	
	// Loads the bytes from file 
	Class loadIt(String path, String classname) {
		String filename = classname.replace('.','/');
		filename += ".class";
		File fullname = new File(path, filename);
		//ij.IJ.write("loadIt: " + fullname);
		try { // read the byte codes
			InputStream is = new FileInputStream(fullname);
			int bufsize = (int)fullname.length();
			byte buf[] = new byte[bufsize];
			is.read(buf, 0, bufsize);
			is.close();
			Class c = defineClass(classname, buf, 0, buf.length);
			cache.put(classname, c);
			return c;
		} catch (Exception e) {
			return null;
		}
	}

	Class loadFromSubdirectory(String path, String name) {
		File f = new File(path);
		String[] list = f.list();
		if (list!=null) {
			for (int i=0; i<list.length; i++) {
				//ij.IJ.write(path+"  "+list[i]);
				f=new File(path, list[i]);
				if (f.isDirectory()) {
					Class c = loadIt(path+list[i], name);
					if (c!=null)
						return c;
				}
			}
		}
		return null;
	}

	/*
	Class loadFromSubdirectory(String path, String name) {
		String separator = System.getProperty("file.separator");
		File f = new File(path);
		if (f.isDirectory()) {
			Class c=loadIt(f.getPath(), name);
			if (c!=null ) return c;
			String [] list=f.list();
			if (list!=null) {
				for (int i=0;i<list.length;i++) {
					File g=new File(f.getPath(),list[i]);
					c=loadFromSubdirectory(g.getPath(),name);
					if (c!=null) return c;
				}
			}	
		} else
			return loadIt(f.getParent(),name);
		return null;
	}
	*/

}

