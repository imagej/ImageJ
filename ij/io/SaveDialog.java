package ij.io;
import java.awt.*;
import ij.*;

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
		ImageJ ij = IJ.getInstance();
		Frame parent = ij!=null?ij:new Frame();
		FileDialog fd = new FileDialog(parent, title, FileDialog.SAVE);
		if (defaultName!=null) {
			if (defaultName!=null) {
				int dotIndex = defaultName.lastIndexOf(".");
				if (dotIndex>=0)
					defaultName = defaultName.substring(0, dotIndex)+extension;
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
		return name;
	}
}
