package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.io.*;
import java.io.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

/** This class opens images, roi's, luts and text files dragged and dropped on  the "ImageJ" window.
     It is ased on the Draw_And_Drop plugin by Eric Kischell (keesh@ieee.org).
     
     10 November 2006: Albert Cardona added Linux support and an  
     option to open all images in a dragged folder as a stack.
*/
     
public class DragAndDrop implements PlugIn, DropTargetListener, Runnable {
	private Iterator iterator;
	private static boolean convertToRGB;
	private static boolean virtualStack;
	
	public void run(String arg) {
		ImageJ ij = IJ.getInstance();
		ij.setDropTarget(null);
		new DropTarget(ij, this);
		new DropTarget(Toolbar.getInstance(), this);
		new DropTarget(ij.getStatusBar(), this);
	}  
	    
	public void drop(DropTargetDropEvent dtde)  {
		dtde.acceptDrop(DnDConstants.ACTION_COPY);
		try  {
			Transferable t = dtde.getTransferable();
			iterator = null;
			DataFlavor[] flavors = t.getTransferDataFlavors();
			if (IJ.debugMode) IJ.log("DragAndDrop.drop: "+flavors.length+" flavors");
			for (int i=0; i<flavors.length; i++) {
				if (IJ.debugMode) IJ.log("  flavor["+i+"]: "+flavors[i].getMimeType());
				if (flavors[i].isFlavorJavaFileListType()) {
					Object data = t.getTransferData(DataFlavor.javaFileListFlavor);
					iterator = ((List)data).iterator();
					break;
				} else if (flavors[i].isFlavorTextType()) {
					Object ob = t.getTransferData(flavors[i]);
					if (!(ob instanceof String)) continue;
					String s = ob.toString().trim();
					ArrayList list = new ArrayList();
					if (s.startsWith("<html")) {
						s = parseHTML(s);
						if (IJ.debugMode) IJ.log("  url: "+s);
						list.add(s);
						this.iterator = list.iterator();
						break;
					}
					if (IJ.debugMode) IJ.log("  content: "+s);
					if (s.startsWith("file://"))
						s = s.substring(7);
					if (s.startsWith("http://"))
						list.add(s);
					else
						list.add(new File(s));
					this.iterator = list.iterator();
					break;
				}
			}
			if (iterator!=null) {
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
	    
	    private String parseHTML(String s) {
	    	if (IJ.debugMode) IJ.log("parseHTML:\n"+s);
	    	int index1 = s.indexOf("<a href=\"");
	    	if (index1>0) {
	    		int index2 = s.indexOf("\"", index1+9);
	    		if (index2>0)
	    			return s.substring(index1+9, index2);
	    	}
	    	index1 = s.indexOf("<img src=\"");
	    	if (index1>0) {
	    		int index2 = s.indexOf("\"", index1+10);
	    		if (index2>0)
	    			return s.substring(index1+10, index2);
	    	}
	    	return s;
	    }

	    public void dragEnter(DropTargetDragEvent dtde)  {
	    	IJ.showStatus("<<Drag and Drop>>");
			if (IJ.debugMode) IJ.log("DragAndDrop.dragEnter");
			dtde.acceptDrag(DnDConstants.ACTION_COPY);
	    }

	    public void dragOver(DropTargetDragEvent e) {}
	    public void dragExit(DropTargetEvent e) {
	    	IJ.showStatus("");
	    }
	    public void dropActionChanged(DropTargetDragEvent e) {}
	    
		public void run() {
			Iterator iterator = this.iterator;
			while(iterator.hasNext()) {
				Object obj = iterator.next();
				if (obj!=null && (obj instanceof String))
					openURL((String)obj);
				else
					openFile((File)obj);
			}
		}
		
		/** Open a URL. */
		private void openURL(String url) {
			if (IJ.debugMode) IJ.log("DragAndDrop.openURL: "+url);
			if (url==null) return;
			if (url.endsWith(".jar") || url.endsWith(".class"))
				IJ.log(url);
			else
				IJ.open(url);
		}

		/** Open a file. If it's a directory, ask to open all images as a sequence in a stack or individually. */
		public void openFile(File f) {
			if (IJ.debugMode) IJ.log("DragAndDrop.openFile: "+f);
			try {
				if (null == f) return;
				String path = f.getCanonicalPath();
				if (f.exists()) {
					if (f.isDirectory())
						openDirectory(f, path);
					else {
						(new Opener()).openAndAddToRecent(path);
						OpenDialog.setLastDirectory(f.getParent()+File.separator);
						OpenDialog.setLastName(f.getName());
					}
				} else {
					IJ.log("File not found: " + path);
				}
			} catch (Throwable e) {
				CharArrayWriter caw = new CharArrayWriter();
				PrintWriter pw = new PrintWriter(caw);
				e.printStackTrace(pw);
				String s = caw.toString();
				new ij.text.TextWindow("Exception", s, 400, 300);
			}
		}
		
		private void openDirectory(File f, String path) {
			String[] names = f.list();
			String msg = "Open all "+names.length+" images in \"" + f.getName() + "\" as a stack?";
			GenericDialog gd = new GenericDialog("Open Folder");
			gd.setInsets(10,5,0);
			gd.addMessage(msg);
			gd.setInsets(15,35,0);
			gd.addCheckbox("Convert to RGB", convertToRGB);
			gd.setInsets(0,35,0);
			gd.addCheckbox("Use Virtual Stack", virtualStack);
			gd.enableYesNoCancel();
			gd.showDialog();
			if (gd.wasCanceled()) return;
			if (gd.wasOKed()) {
				convertToRGB = gd.getNextBoolean();
				virtualStack = gd.getNextBoolean();
				String options  = " sort";
				if (convertToRGB) options += " convert_to_rgb";
				if (virtualStack) options += " use";
				IJ.run("Image Sequence...", "open=[" + path + "/]"+options);
			} else {
				for (int k=0; k<names.length; k++) {
					IJ.redirectErrorMessages();
					if (!names[k].startsWith("."))
						(new Opener()).open(path + "/" + names[k]);
				}
			}
			IJ.register(DragAndDrop.class);
		}
		
}
