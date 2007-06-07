package ij.io;
import java.awt.*;
import ij.*;
import ij.gui.*;
import ij.plugin.frame.Recorder;

/** This class displays a dialog window from 
	which the user can select an input file. */ 
 public class OpenDialog {

	private String dir;
	private String name;
	private boolean recordPath;
	private static String defaultDirectory;
	private String title;
	
	/** Displays a file open dialog with 'title' as
		the title. If 'path' is non-blank, it is
		used and the dialog is not displayed. Uses
		and updates the ImageJ default directory. */
	public OpenDialog(String title, String path) {
		String macroOptions = Macro.getOptions();
		if (macroOptions!=null) {
			path = Macro.getValue(macroOptions, title, path);
			if (path==null || path.equals(""))
				path = Macro.getValue(macroOptions, "path", path);		
		}
		if (path==null || path.equals("")) {
			ImageJ ij = IJ.getInstance();
			Frame parent = ij!=null?ij:new Frame();
			FileDialog fd = new FileDialog(parent, title);
			this.title = title;
			defaultDirectory = getDefaultDirectory();
			if (defaultDirectory!=null)
				fd.setDirectory(defaultDirectory);
			GUI.center(fd);
			fd.show();
			name = fd.getFile();
			if (name==null)
				Macro.abort();
			else {
				dir = fd.getDirectory();
				defaultDirectory = dir;
			}
			recordPath = true;
			fd.dispose();
			if (ij==null)
				parent.dispose();
		} else {
			decodePath(path);
			recordPath = IJ.macroRunning();
		}
		IJ.register(OpenDialog.class);
	}
	
	/** Displays a file open dialog, using the specified 
		default directory and file name. */
	public OpenDialog(String title, String defaultDir, String defaultName) {
		String path = null;
		String macroOptions = Macro.getOptions();
		if (macroOptions!=null)
			path = Macro.getValue(macroOptions, title, path);
		if (path!=null)
			decodePath(path);
		else {
			ImageJ ij = IJ.getInstance();
			Frame parent = ij!=null?ij:new Frame();
			FileDialog fd = new FileDialog(parent, title);
			this.title = title;
			if (defaultDir!=null)
				fd.setDirectory(defaultDir);
			if (defaultName!=null)
				fd.setFile(defaultName);
			GUI.center(fd);
			fd.show();
			name = fd.getFile();
			if (name==null)
				Macro.abort();
			dir = fd.getDirectory();
			recordPath = true;
			fd.dispose();
			if (ij==null)
				parent.dispose();
		}
	}
	
	void decodePath(String path) {
		int i = path.lastIndexOf('/');
		if (i==-1)
			i = path.lastIndexOf('\\');
		if (i>0) {
			dir = path.substring(0, i+1);
			name = path.substring(i+1);
		} else {
			dir = "";
			name = path;
		}
	}

	/** Returns the selected directory. */
	public String getDirectory() {
		return dir;
	}
	
	/** Returns the selected file name. */
	public String getFileName() {
		if (Recorder.record && recordPath)
			Recorder.recordPath(title, dir+name);
		return name;
	}
		
	/** Returns the current working directory, which my be null. */
	public static String getDefaultDirectory() {
		if (defaultDirectory==null)
			defaultDirectory = Prefs.getString(Prefs.DIR_IMAGE);
		return defaultDirectory;
	}

	static void setDefaultDirectory(String defaultDir) {
		defaultDirectory = defaultDir;
		IJ.register(OpenDialog.class);
	}

}
