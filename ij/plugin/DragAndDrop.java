package ij.plugin;
import ij.*;
import ij.io.*;
import java.io.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.util.List;
import java.util.Iterator;

/** This class opens images, roi's, luts and text files dragged and dropped on  the ImageJ frame.
     Requires Java 2, v1.3.1. Based on the Draw_And_Drop plugin by Eric Kischell (keesh@ieee.org). */
public class DragAndDrop implements PlugIn, DropTargetListener {
	protected static ImageJ ij = null;  // the "ImageJ" frame
	private static boolean enableDND = true; 
	protected DataFlavor dFlavor;  
	
	public void run(String arg) {
		String vers = System.getProperty("java.version");
		if (vers.compareTo("1.3.1") < 0)
			 return;
		ij = IJ.getInstance();
		ij.setDropTarget(null);
		DropTarget dropTarget = new DropTarget(ij, this);
	}  
	    
	public void drop(DropTargetDropEvent dtde)  {
		dtde.acceptDrop(DnDConstants.ACTION_COPY);
		try  {
			Transferable t = dtde.getTransferable();
			if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				Object data = t.getTransferData(DataFlavor.javaFileListFlavor);
				Iterator iterator = ((List)data).iterator();
				//IJ.log("drop");
				while(iterator.hasNext()) {
					File file = (File)iterator.next();
					//IJ.log("dopen: "+file.getAbsolutePath());
					new Opener().open(file.getAbsolutePath());
				}
			}
		}
		catch(Exception e)  {
			    dtde.dropComplete(false);
			    return;
		}
		dtde.dropComplete(true);
	    } 

	    public void dragEnter(DropTargetDragEvent dtde)  {
		dtde.acceptDrag(DnDConstants.ACTION_COPY);
	    }

	    public void dragOver(DropTargetDragEvent e) {}
	    public void dragExit(DropTargetEvent e) {}
	    public void dropActionChanged(DropTargetDragEvent e) {}
} 



