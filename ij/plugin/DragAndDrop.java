package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.process.ImageProcessor;
import ij.plugin.frame.Recorder;
import java.io.*;
import java.awt.Point;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.util.*;
import java.util.Iterator;
import java.util.ArrayList;

/** This class opens images, roi's, luts and text files dragged and dropped on  the "ImageJ" window.
     It is based on the Draw_And_Drop plugin by Eric Kischell (keesh@ieee.org).
     
     10 November 2006: Albert Cardona added Linux support and an  
     option to open all images in a dragged folder as a stack.
*/
     
public class DragAndDrop implements PlugIn, DropTargetListener, Runnable {
	private Iterator iterator;
	private static boolean convertToRGB;
	private static boolean virtualStack;
	private boolean openAsVirtualStack;
	
	public void run(String arg) {
		ImageJ ij = IJ.getInstance();
		ij.setDropTarget(null);
		new DropTarget(ij, this);
		new DropTarget(Toolbar.getInstance(), this);
		new DropTarget(ij.getStatusBar(), this);
	}  
	    
	public void drop(DropTargetDropEvent dtde)  {
		dtde.acceptDrop(DnDConstants.ACTION_COPY);
		DataFlavor[] flavors = null;
		try  {
			Transferable t = dtde.getTransferable();
			iterator = null;
			flavors = t.getTransferDataFlavors();
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
					if (IJ.isLinux() && s.length()>1 && (int)s.charAt(1)==0)
						s = fixLinuxString(s);
					ArrayList list = new ArrayList();
					if (s.indexOf("href=\"")!=-1 || s.indexOf("src=\"")!=-1) {
						s = parseHTML(s);
						if (IJ.debugMode) IJ.log("  url: "+s);
						list.add(s);
						this.iterator = list.iterator();
						break;
					}
					BufferedReader br = new BufferedReader(new StringReader(s));
					String tmp;
					while (null != (tmp = br.readLine())) {
						tmp = java.net.URLDecoder.decode(tmp.replaceAll("\\+","%2b"), "UTF-8");
						if (tmp.startsWith("file://")) tmp = tmp.substring(7);
						if (IJ.debugMode) IJ.log("  content: "+tmp);
						if (tmp.startsWith("http://"))
							list.add(s);
						else
							list.add(new File(tmp));
					}
					this.iterator = list.iterator();
					break;
				}
			}
			if (iterator!=null) {
				Thread thread = new Thread(this, "DrawAndDrop");
				thread.setPriority(Math.max(thread.getPriority()-1, Thread.MIN_PRIORITY));
				thread.start();
			}
		} catch(Exception e)  {
			dtde.dropComplete(false);
			return;
		}
		dtde.dropComplete(true);
		if (flavors==null || flavors.length==0) {
			if (IJ.isMacOSX())
				IJ.error("First drag and drop ignored. Please try again. You can avoid this\n"
				+"problem by dragging to the toolbar instead of the status bar.");
			else
				IJ.error("Drag and drop failed");
		}
	}
	    
	private String fixLinuxString(String s) {
		StringBuffer sb = new StringBuffer(200);
		for (int i=0; i<s.length(); i+=2)
			sb.append(s.charAt(i));
		return new String(sb);
	}
	
	private String parseHTML(String s) {
		if (IJ.debugMode) IJ.log("parseHTML:\n"+s);
		int index1 = s.indexOf("src=\"");
		if (index1>=0) {
			int index2 = s.indexOf("\"", index1+5);
			if (index2>0)
				return s.substring(index1+5, index2);
		}
		index1 = s.indexOf("href=\"");
		if (index1>=0) {
			int index2 = s.indexOf("\"", index1+6);
			if (index2>0)
				return s.substring(index1+6, index2);
		}
		return s;
	}

	public void dragEnter(DropTargetDragEvent e)  {
		IJ.showStatus("<<Drag and Drop>>");
		if (IJ.debugMode) IJ.log("DragEnter: "+e.getLocation());
		e.acceptDrag(DnDConstants.ACTION_COPY);
		openAsVirtualStack = false;
	}

	public void dragOver(DropTargetDragEvent e) {
		if (IJ.debugMode) IJ.log("DragOver: "+e.getLocation());
		Point loc = e.getLocation();
		int buttonSize = Toolbar.getButtonSize();
		int width = IJ.getInstance().getSize().width;
		openAsVirtualStack = width-loc.x<=(buttonSize+buttonSize/3);
		if (openAsVirtualStack)
			IJ.showStatus("<<Open as virtual stack or text image>>");
		else
			IJ.showStatus("<<Drag and Drop>>");
	}
	
	public void dragExit(DropTargetEvent e) {
		IJ.showStatus("");
	}
	
	public void dropActionChanged(DropTargetDragEvent e) {}
	
	public void run() {
		Iterator iterator = this.iterator;
		while(iterator.hasNext()) {
			Object obj = iterator.next();
			String str = ""+obj;
			if (str!=null && str.startsWith("https:/")) {
				if (!str.startsWith("https://"))
					str = str.replace("https:/", "https://");
				obj = str;
			}
			if (obj!=null && (obj instanceof String))
				openURL((String)obj);
			else
				openFile((File)obj);
		}
	}
	
	/** Open a URL. */
	private void openURL(String url) {
		if (IJ.debugMode) IJ.log("DragAndDrop.openURL: "+url);
		if (url!=null)
			IJ.open(url);
	}

	/** Open a file. If it's a directory, ask to open all images as a sequence in a stack or individually. */
	public void openFile(File f) {
		if (IJ.debugMode) IJ.log("DragAndDrop.openFile: "+f);
		try {
			if (null == f) return;
			String path = f.getCanonicalPath();
			if (f.exists()) {
				if (f.isDirectory()) {
					if (openAsVirtualStack)
						IJ.run("Image Sequence...", "open=[" + path + "] sort use");
					else
						openDirectory(f, path);
				} else {
					if (openAsVirtualStack && (path.endsWith(".tif")||path.endsWith(".TIF")))
						(new FileInfoVirtualStack()).run(path);
					else if (openAsVirtualStack && (path.endsWith(".avi")||path.endsWith(".AVI")))
						IJ.run("AVI...", "open=["+path+"] use");
					else if (openAsVirtualStack && (path.endsWith(".txt"))) {
						ImageProcessor ip = (new TextReader()).open(path);
						if (ip!=null)
							new ImagePlus(f.getName(),ip).show();
					} else {
						Recorder.recordOpen(path);
						(new Opener()).openAndAddToRecent(path);
					}
					OpenDialog.setLastDirectory(f.getParent()+File.separator);
					OpenDialog.setLastName(f.getName());
					OpenDialog.setDefaultDirectory(f.getParent());
				}
			} else {
				IJ.log("File not found: " + path);
			}
		} catch (Throwable e) {
			if (!Macro.MACRO_CANCELED.equals(e.getMessage()))
				IJ.handleException(e);
		}
	}
	
	private void openDirectory(File f, String path) {
		if (path==null) return;
		path = IJ.addSeparator(path);
		String[] names = f.list();
		names = (new FolderOpener()).trimFileList(names);
		if (names==null)
			return;
		FolderOpener fo = new FolderOpener();
		fo.setDirectory(path);
		fo.run("");
	}
		
}
