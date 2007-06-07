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
	private static String defaultDirectory;
	
	/** Displays a file open dialog with 'title' as
		the title. If 'path' is non-blank, it is
		used and the dialog is not displayed. */
	public OpenDialog(String title, String path) {
		String macroOptions = Macro.getOptions();
		if (macroOptions!=null)
			path = Macro.getValue(macroOptions, "path", path);
		if (path==null || path.equals("")) {
			ImageJ ij = IJ.getInstance();
			Frame parent = ij!=null?ij:new Frame();
			FileDialog fd = new FileDialog(parent, title);
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
			fd.dispose();
			if (ij==null)
				parent.dispose();
		} else {
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
		IJ.register(OpenDialog.class);
	}
	
	/** Returns the selected directory. */
	public String getDirectory() {
		return dir;
	}
	
	/** Returns the selected file name. */
	public String getFileName() {
		if (Recorder.record)
			Recorder.recordPath(dir+name);
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
