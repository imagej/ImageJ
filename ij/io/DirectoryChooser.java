package ij.io;
import ij.*;
import ij.gui.*;
import ij.plugin.frame.Recorder;
import ij.util.Java2;
import java.awt.*;
import java.io.*;
import javax.swing.*;
import javax.swing.filechooser.*;

/** This class displays a dialog box that allows 
	the user can select a directory. */ 
 public class DirectoryChooser {
 	private String directory;
 	private static String defaultDir;
 
 	/** Display a dialog using the specified title. */
 	public DirectoryChooser(String title) {
 		if (IJ.isJava2())
 			getDirectoryUsingJFileChooser(title);
 		else {
			OpenDialog od = new OpenDialog(title, null);
			directory = od.getDirectory();
		}
 	}
 	
 	void getDirectoryUsingJFileChooser(String title) {
		Java2.setSystemLookAndFeel();
		JFileChooser chooser = new JFileChooser();
		if (defaultDir!=null) 
			chooser.setCurrentDirectory(new File(defaultDir));
		chooser.setDialogTitle(title);
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setApproveButtonText("Select");
		if (chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
			File dir = chooser.getCurrentDirectory();
			File file = chooser.getSelectedFile();
			directory = dir.getPath()+File.separator;
			defaultDir = directory;
			directory += file.getName()+File.separator;
		}
	}
 
 	/** Returns the directory selected by the user. */
 	public String getDirectory() {
 		return directory;
 	}
 	
}
