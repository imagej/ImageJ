package ij.io;
import ij.*;
import ij.gui.*;
import ij.plugin.frame.Recorder;
import ij.util.Java2;
import java.awt.*;
import java.io.*;
import javax.swing.*;
import javax.swing.filechooser.*;

/** This class displays a dialog box that allows the user can select a directory. */ 
 public class DirectoryChooser {
 	private String directory;
 	private static String defaultDir;
 
 	/** Display a dialog using the specified title. */
 	public DirectoryChooser(String title) {
 		if (IJ.isMacOSX() && IJ.isJava14())
			getDirectoryUsingFileDialog(title);
 		else if (IJ.isJava2()) {
 			if (EventQueue.isDispatchThread())
 				getDirectoryUsingJFileChooserOnThisThread(title);
 			else
 				getDirectoryUsingJFileChooser(title);
 		} else {
			OpenDialog od = new OpenDialog(title, null);
			directory = od.getDirectory();
		}
 	}
 	
	// runs JFileChooser in a separate thread to avoid possible thread deadlocks
 	void getDirectoryUsingJFileChooser(final String title) {
		Java2.setSystemLookAndFeel();
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					JFileChooser chooser = new JFileChooser();
					if (defaultDir!=null) 
						chooser.setCurrentDirectory(new File(defaultDir));
					chooser.setDialogTitle(title);
					chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					chooser.setApproveButtonText("Select");
					if (chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
						File dir = chooser.getCurrentDirectory();
						File file = chooser.getSelectedFile();
						directory = dir.getPath();
						if (!directory.endsWith(File.separator))
							directory += File.separator;
						defaultDir = directory;
						directory += file.getName()+File.separator;
					}
				}
			});
		} catch (Exception e) {}
	}
 
	// Choose a directory using JFileChooser on the current thread
 	void getDirectoryUsingJFileChooserOnThisThread(final String title) {
		Java2.setSystemLookAndFeel();
		try {
			JFileChooser chooser = new JFileChooser();
			if (defaultDir!=null) 
				chooser.setCurrentDirectory(new File(defaultDir));
			chooser.setDialogTitle(title);
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setApproveButtonText("Select");
			if (chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
				File dir = chooser.getCurrentDirectory();
				File file = chooser.getSelectedFile();
				directory = dir.getPath();
				if (!directory.endsWith(File.separator))
					directory += File.separator;
				defaultDir = directory;
				directory += file.getName()+File.separator;
			}
		} catch (Exception e) {}
	}

 	// On Mac OS X, we can select directories using the native file open dialog
 	void getDirectoryUsingFileDialog(String title) {
 		boolean saveUseJFC = Prefs.useJFileChooser;
 		Prefs.useJFileChooser = false;
		System.setProperty("apple.awt.fileDialogForDirectories", "true");
		OpenDialog od = new OpenDialog(title, null);
		directory = od.getDirectory() + od.getFileName() + "/";
		System.setProperty("apple.awt.fileDialogForDirectories", "false");
 		Prefs.useJFileChooser = saveUseJFC;
	}

 	/** Returns the directory selected by the user. */
 	public String getDirectory() {
 		//IJ.log("getDirectory: "+directory);
 		return directory;
 	}
 	
}
