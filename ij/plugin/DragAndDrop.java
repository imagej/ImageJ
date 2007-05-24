package ij.plugin;
import ij.*;
import ij.gui.YesNoCancelDialog;
import ij.io.*;
import java.io.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

/** This class opens images, roi's, luts and text files dragged and dropped on  the "ImageJ" window.
     It requires Java 1.3.1 or later. Based on the Draw_And_Drop plugin by Eric Kischell (keesh@ieee.org).
     
     10 November 2006: Albert Cardona added Linux support and an  
     option to open all images in a dragged folder as a stack.
*/
     
public class DragAndDrop implements PlugIn, DropTargetListener, Runnable {
	private Iterator iterator;
	
	public void run(String arg) {
		ImageJ ij = IJ.getInstance();
		ij.setDropTarget(null);
		DropTarget dropTarget = new DropTarget(ij, this);
	}  
	    
	public void drop(DropTargetDropEvent dtde)  {
		dtde.acceptDrop(DnDConstants.ACTION_COPY);
		try  {
			Transferable t = dtde.getTransferable();
			iterator = null;
			if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				Object data = t.getTransferData(DataFlavor.javaFileListFlavor);
				iterator = ((List)data).iterator();
			} else {
				// find a String path
				DataFlavor[] flavors = t.getTransferDataFlavors();
				for (int i=0; i<flavors.length; i++) {
					if (!flavors[i].getRepresentationClass().equals(String.class)) continue;
					Object ob = t.getTransferData(flavors[i]);
					if (!(ob instanceof String)) continue;
					String s = ob.toString().trim();
					BufferedReader br = new BufferedReader(new StringReader(s));
					String tmp;
					ArrayList list = new ArrayList();
					while (null != (tmp = br.readLine())) {
						tmp = java.net.URLDecoder.decode(tmp, "UTF-8");
						if (tmp.startsWith("file://")) {
							tmp = tmp.substring(7);
						}
						list.add(new File(tmp));
					}
					this.iterator = list.iterator();
					break;
				}
			}
			if (null != iterator) {
				Thread thread = new Thread(this, "DrawAndDrop");
				thread.setPriority(Math.max(thread.getPriority()-1, Thread.MIN_PRIORITY));
				thread.start();
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
	    
		public void run() {
			Iterator iterator = this.iterator;
			while(iterator.hasNext()) {
				File file = (File)iterator.next();
				openFile(file);
			}
		}

		/** Open a file. If it's a directory, ask to open all images as a sequence in a stack or individually. */
		private void openFile(File f) {
			try {
				if (null == f) return;
				String tmp = f.getCanonicalPath();
				if (f.exists()) {
					if (f.isDirectory()) {
						String[] names = f.list();
						YesNoCancelDialog yn = new YesNoCancelDialog(IJ.getInstance(), "Open folder?", "Open all "+names.length+" images in \"" + f.getName() + "\" as a stack?");
						if (yn.yesPressed()) {
							IJ.run("Image Sequence...", "open=[" + tmp + "/]");
						} else if (!yn.cancelPressed()) {
							for (int k=0; k<names.length; k++) {
								IJ.redirectErrorMessages();
								if (!names[k].startsWith("."))
									(new Opener()).open(tmp + "/" + names[k]);
							}
						}
					} else {
						(new Opener()).openAndAddToRecent(tmp);
						OpenDialog.setLastDirectory(f.getParent()+File.separator);
						OpenDialog.setLastName(f.getName());
					}
				} else {
					IJ.log("File not found: " + tmp);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
}
