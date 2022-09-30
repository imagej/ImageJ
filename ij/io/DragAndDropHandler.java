package ij.io;
import ij.*;
import ij.gui.*;
import ij.plugin.frame.Recorder;
import ij.util.Java2;
import java.awt.*;
import java.io.*;
import java.util.ArrayList; //no  need to import java.util.List; it would be ambiguous because of java.awt.List
import java.net.URI;
import java.net.URISyntaxException;
import javax.swing.*;
import javax.swing.filechooser.*;
import java.awt.datatransfer.*;

/** This class handles drag&drop onto JFileChoosers. */ 
 public class DragAndDropHandler extends TransferHandler {
 	private JFileChooser jFileChooser;
 
	/** Given a JFileChooser 'fc', this is how to use this class:
	 * <pre>
	 *     fc.setDragEnabled(true);
	 *     fc.setTransferHandler(new DragAndDropHandler(fc));
	 * </pre>
	 */
 	public DragAndDropHandler(JFileChooser jFileChooser) {
		super();
		this.jFileChooser = jFileChooser;
 	}

	/** Returns whether any of the transfer flavors is supported */
	public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
		for (DataFlavor dataFlavor : transferFlavors) {
			if (isSupportedTransferFlavor(dataFlavor))
				return true;
			}
		return false;
	}

	/** Imports the drag&drop file or list of files and sets the JFileChooser to this. 
	 *  Returns true if successful */
	public boolean importData(JComponent comp, Transferable t) {
		DataFlavor[] transferFlavors = t.getTransferDataFlavors();
		for (DataFlavor dataFlavor : transferFlavors) {
			try {
				java.util.List<File> fileList = null;
				if (dataFlavor.isFlavorJavaFileListType()) {
					fileList = (java.util.List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);
					if (IJ.debugMode) IJ.log("dragAndDrop FileList size="+fileList.size()+" first: "+fileList.get(0));
				} else if (isSupportedTransferFlavor(dataFlavor)) {
					String str = (String)t.getTransferData(dataFlavor);
					if (IJ.debugMode) IJ.log("dragAndDrop str="+str);
					String[] strs = str.split("[\n\r]+");               //multiple files are separate lines
					fileList = new ArrayList<File>(strs.length);
					for (String s : strs) {
						if (s.length() > 0) {
							File file = new File(s);                    //Try whether it is a plain path (pointing to an existing file)
							if (!file.exists()) try {
								file = new File(new URI(s));            //When not successful, try a whether it is a URL, e.g. file:///absolute/path/to/file
							} catch (URISyntaxException e) {continue;}
							if (file.exists())                          //We only accept drag&drop of existing files
								fileList.add(file);
						}
					}
				}
				if (fileList == null || fileList.size() ==0) continue;  //this data flavor did not work

				File firstFile = fileList.get(0);
				if (jFileChooser.isMultiSelectionEnabled()) {   //multiple files accepted
					File dir = firstFile.getParentFile();
					jFileChooser.setCurrentDirectory(dir);
					File[] files = fileList.toArray(new File[0]);
					jFileChooser.setSelectedFiles(files);
				} else {
					File file = firstFile;                      //single file required; if we get more take the first one
					if (jFileChooser.getFileSelectionMode() == JFileChooser.DIRECTORIES_ONLY && !file.isDirectory())
						file = file.getParentFile();            //if a directory is required and we get a file, use its directory
					if (jFileChooser.getDialogType() == JFileChooser.SAVE_DIALOG && file.isDirectory())
						jFileChooser.setCurrentDirectory(file); //in a save operation, if we get a directory, move there
					else
						jFileChooser.setSelectedFile(file);
				}
				jFileChooser.rescanCurrentDirectory();
				return true;
			} catch (Exception e) {if (IJ.debugMode) IJ.handleException(e);}
		}
		return false;
	}

	/** Returns whether this transfer flavor is supported. We support File Lists and Strings (plain or as list of URLs). */
	public boolean isSupportedTransferFlavor(DataFlavor flavor) {
		return flavor.isFlavorJavaFileListType() ||
				(flavor.getRepresentationClass() == String.class &&
				(flavor.getMimeType().startsWith("text/uri-list") || flavor.getMimeType().startsWith("text/plain")));
	}
}
