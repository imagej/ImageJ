package ij.io;
import java.awt.*;
import ij.*;
import ij.plugin.frame.Recorder;

/** This class displays a dialog window from 
	which the user can save a file. */ 
public class SaveDialog {

	private String dir;
	private String name;
	
	/** Displays a file save dialog with 'title' as the 
		title, 'defaultName' as the initial file name, and
		'extension' (e.g. ".tif") as the default extension.
	*/
	public SaveDialog(String title, String defaultName, String extension) {
		String macroOptions = Macro.getOptions();
		if (macroOptions!=null) {
			String path = Macro.getValue(macroOptions, "path", null);
			if (path!=null) {
				Opener o = new Opener();
				dir = o.getDir(path);
				name = o.getName(path);
				return;
			}
		}

		ImageJ ij = IJ.getInstance();
		Frame parent = ij!=null?ij:new Frame();
		FileDialog fd = new FileDialog(parent, title, FileDialog.SAVE);
		if (defaultName!=null) {
			if (defaultName!=null) {
				int dotIndex = defaultName.lastIndexOf(".");
				if (dotIndex>=0)
					defaultName = defaultName.substring(0, dotIndex)+extension;
				else
					defaultName += extension;
			}
			fd.setFile(defaultName);
		}
		String defaultDir = OpenDialog.getDefaultDirectory();
		if (defaultDir!=null)
			fd.setDirectory(defaultDir);
		fd.setVisible(true);
		name = fd.getFile();
		dir = fd.getDirectory();
		if (name!=null && dir!=null)
			OpenDialog.setDefaultDirectory(dir);
		fd.dispose();
		if (ij==null)
			parent.dispose();
		IJ.showStatus(title+": "+dir+name);
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
}
