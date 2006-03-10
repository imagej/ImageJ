package ij.io;
import java.awt.*;
import java.io.*;
import javax.swing.*;
import javax.swing.filechooser.*;
import ij.*;
import ij.plugin.frame.Recorder;
import ij.util.Java2;

/** This class displays a dialog window from 
	which the user can save a file. */ 
public class SaveDialog {

	private String dir;
	private String name;
	private String title;
	private String ext;
	
	/** Displays a file save dialog with 'title' as the 
		title, 'defaultName' as the initial file name, and
		'extension' (e.g. ".tif") as the default extension.
	*/
	public SaveDialog(String title, String defaultName, String extension) {
		this.title = title;
		ext = extension;
		if (isMacro())
			return;
		String defaultDir = OpenDialog.getDefaultDirectory();
		defaultName = addExtension(defaultName, extension);
		if (Prefs.useJFileChooser)
			jsave(title, defaultDir, defaultName);
		else
			save(title, defaultDir, defaultName);
		if (name!=null && dir!=null)
			OpenDialog.setDefaultDirectory(dir);
		IJ.showStatus(title+": "+dir+name);
	}
	
	/** Displays a file save dialog, using the specified 
		default directory and file name and extension. */
	public SaveDialog(String title, String defaultDir, String defaultName, String extension) {
		this.title = title;
		ext = extension;
		if (isMacro())
			return;
		defaultName = addExtension(defaultName, extension);
		if (Prefs.useJFileChooser)
			jsave(title, defaultDir, defaultName);
		else
			save(title, defaultDir, defaultName);
		IJ.showStatus(title+": "+dir+name);
	}
	
	boolean isMacro() {
		String macroOptions = Macro.getOptions();
		if (macroOptions!=null) {
			String path = Macro.getValue(macroOptions, title, null);
			if (path==null)
				path = Macro.getValue(macroOptions, "path", null);
			//if (path==null && oneRunArg) {
			//	path = macroOptions;
			//}
			if (path!=null) {
				Opener o = new Opener();
				dir = o.getDir(path);
				name = o.getName(path);
				return true;
			}
		}
		return false;
	}
	
	String addExtension(String name, String extension) {
		if (name!=null && extension!=null) {
			int dotIndex = name.lastIndexOf(".");
			if (dotIndex>=0)
				name = name.substring(0, dotIndex) + extension;
			else
				name += extension;
		}
		return name;
	}
	
	// Save using JFileChooser
	void jsave(String title, String defaultDir, String defaultName) {
		Java2.setSystemLookAndFeel();
		JFileChooser fc = new JFileChooser();
		if (defaultDir!=null) {
			File f = new File(defaultDir);
			if (f!=null)
				fc.setCurrentDirectory(f);
		}
		if (defaultName!=null)
			fc.setSelectedFile(new File(defaultName));
		int returnVal = fc.showSaveDialog(IJ.getInstance());
		if (returnVal!=JFileChooser.APPROVE_OPTION)
			{Macro.abort(); return;}
		File f = fc.getSelectedFile();
		if(f.exists()) {
			int ret = JOptionPane.showConfirmDialog (fc,
				"The file "+ f.getName() + " already exists. \nWould you like to replace it?",
				"Replace?",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (ret!=JOptionPane.OK_OPTION) f = null;
		}
		if (f==null)
			Macro.abort();
		else {
			dir = fc.getCurrentDirectory().getPath()+File.separator;
			name = fc.getName(f);
		}
	}

	// Save using FileDialog
	void save(String title, String defaultDir, String defaultName) {
		ImageJ ij = IJ.getInstance();
		Frame parent = ij!=null?ij:new Frame();
		FileDialog fd = new FileDialog(parent, title, FileDialog.SAVE);
		if (defaultName!=null)
			fd.setFile(defaultName);			
		if (defaultDir!=null)
			fd.setDirectory(defaultDir);
		fd.show();
		name = fd.getFile();
		dir = fd.getDirectory();
		if (name==null)
			Macro.abort();
		fd.dispose();
		if (ij==null)
			parent.dispose();
	}
	
	/** Returns the selected directory. */
	public String getDirectory() {
		return dir;
	}
	
	/** Returns the selected file name. */
	public String getFileName() {
		if (Recorder.record) {
			Recorder.recordPath(title, dir+name);
			//String cmd = Recorder.getCommandName();
			//if (cmd.endsWith("..."))
			//	cmd = cmd.substring(0, cmd.length()-3);
			//Recorder.record("saveAs", cmd, dir+name);
			//Recorder.setCommand(null);
		}
		return name;
	}
	
}
