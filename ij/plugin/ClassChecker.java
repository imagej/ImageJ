package  ij.plugin;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ij.IJ;
import ij.Menus;
import ij.Prefs;
import ij.util.StringSorter;

/** Checks for duplicate class and JAR files in the plugins folders. */
public class ClassChecker implements PlugIn {
	private String[] paths = null;
	private String[] names = null;

	public void run(String arg) {
		deleteDuplicates();
	}

	void deleteDuplicates() {
		getPathsAndNames();
		if (paths == null || paths.length < 2)
			return;
		String[] sortedNames = new String[names.length];
		for (int i = 0; i < names.length; i++)
			sortedNames[i] = names[i];
		StringSorter.sort(sortedNames);
		for (int i = 0; i < sortedNames.length - 1; i++) {
			if (sortedNames[i].equals(sortedNames[i + 1]))
				delete(sortedNames[i]);
		}
	}

	void delete(String name) {
		String path1 = null, path2 = null;
		File file1, file2;
		long date1, date2;
		for (int i = 0; i < names.length; i++) {
			if (path1 == null && names[i].equals(name)) {
				path1 = paths[i] + names[i];
			} else if (path2 == null && names[i].equals(name)) {
				path2 = paths[i] + names[i];
			}
			if (path1 != null && path2 != null) {
				file1 = new File(path1);
				file2 = new File(path2);
				if (file1 == null || file2 == null)
					return;
				date1 = file1.lastModified();
				date2 = file2.lastModified();
				if (date1 < date2)
					log(path1);
				else
					log(path2);
				break;
			}
		}
	}

	void log(String path) {
		IJ.log("Duplicate plugin: " + path);
	}

	/**
	 * Gets lists of all the class and jar files in the plugins folder and
	 * subfolders of the plugins folder.
	 */
	void getPathsAndNames() {
		String path = Menus.getPlugInsPath();
		if (path == null)
			return;
		File f = new File(path);
		String[] list = f.list();
		if (list == null)
			return;
		List<String> v1 = new ArrayList();
		List<String> v2 = new ArrayList();
		for (int i = 0; i < list.length; i++) {
			String name = list[i];
			if (name.endsWith(".class") || name.endsWith(".jar")) {
				if (!name.equals("package-info.class")) {
					v1.add(path);
					v2.add(name);
				}
			} else
				getSubdirectoryFiles(path, name, v1, v2);
		}
		paths = v1.toArray(new String[0]);
		names = v2.toArray(new String[0]);
	}

	/** Looks for class and jar files in a subfolders of the plugins folder. */
	void getSubdirectoryFiles(String path, String dir, List<String> v1, List<String> v2) {
		if (dir.endsWith(".java"))
			return;
		File f = new File(path, dir);
		if (!f.isDirectory())
			return;
		String[] list = f.list();
		if (list == null)
			return;
		dir += Prefs.separator;
		for (int i = 0; i < list.length; i++) {
			String name = list[i];
			if (name.endsWith(".class") || name.endsWith(".jar")) {
				if (!name.equals("package-info.class")) {
					v1.add(path + dir);
					v2.add(name);
				}
			}
		}
	}

}
