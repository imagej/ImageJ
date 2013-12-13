package ij.io;
import ij.IJ;
import java.io.*;
import java.net.*;

/** ImageJ uses this class loader to load plugins and resources from the
 * plugins directory and immediate subdirectories. This class loader will
 * also load classes and resources from JAR files.
 *
 * <p> The class loader searches for classes and resources in the following order:
 * <ol>
 *  <li> Plugins directory</li>
 *  <li> Subdirectories of the Plugins directory</li>
 *  <li> JAR and ZIP files in the plugins directory and subdirectories</li>
 * </ol>
 * <p> The class loader does not recurse into subdirectories beyond the first level.
*/
public class PluginClassLoader extends URLClassLoader {
    protected String path;

    /**
     * Creates a new PluginClassLoader that searches in the directory path
     * passed as a parameter. The constructor automatically finds all JAR and ZIP
     * files in the path and first level of subdirectories. The JAR and ZIP files
     * are stored in a Vector for future searches.
     * @param path the path to the plugins directory.
     */
	public PluginClassLoader(String path) {
		super(new URL[0], IJ.class.getClassLoader());
		init(path);
	}
	
	/** This version of the constructor is used when ImageJ is launched using Java WebStart. */
	public PluginClassLoader(String path, boolean callSuper) {
		super(new URL[0], Thread.currentThread().getContextClassLoader());
		init(path);
	}

	void init(String path) {
		this.path = path;

		//find all JAR files on the path and subdirectories
		File f = new File(path);
        try {
            // Add plugin directory to search path
            addURL(f.toURI().toURL());
        } catch (MalformedURLException e) {
            ij.IJ.log("PluginClassLoader: "+e);
        }
		String[] list = f.list();
		if (list==null)
			return;
		for (int i=0; i<list.length; i++) {
			if (list[i].equals(".rsrc"))
				continue;
			File f2=new File(path, list[i]);
			if (f2.isDirectory())
				addDirectory(f2);
			else 
				addJar(f2);
		}
		addDirectory(f, "jars"); // add ImageJ/jars; requested by Wilhelm Burger
	}

	private void addDirectory(File f) {
		if (IJ.debugMode) IJ.log("PluginClassLoader.addDirectory: "+f);
		try {
			// Add first level subdirectories to search path
			addURL(f.toURI().toURL());
		} catch (MalformedURLException e) {
			ij.IJ.log("PluginClassLoader: "+e);
		}
		String[] innerlist = f.list();
		if (innerlist==null)
			return;
		for (int j=0; j<innerlist.length; j++) {
			File g = new File(f,innerlist[j]);
			if (g.isFile())
				addJar(g);
		}
	}

    private void addJar(File f) {
        if (f.getName().endsWith(".jar") || f.getName().endsWith(".zip")) {
			if (IJ.debugMode) IJ.log("PluginClassLoader.addJar: "+f);
            try {
                addURL(f.toURI().toURL());
            } catch (MalformedURLException e) {
				ij.IJ.log("PluginClassLoader: "+e);
            }
        }
    }

	private void addDirectory(File f, String name) {
		f = f.getParentFile();
		if (f==null)
			return;
		f = new File(f, name);
		if (f==null)
			return;
		if (f.isDirectory())
			addDirectory(f);
	}

}
