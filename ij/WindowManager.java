package ij;
import ij.plugin.Converter;
import ij.plugin.frame.Recorder;
import java.awt.*;
import java.util.*;
import ij.gui.*;

/** This class consists of static methods used to manage ImageJ's windows. */
public class WindowManager {

	private static Vector imageList = new Vector();		// list of image windows
	private static Vector nonImageList = new Vector();	// list of non-image windows
	private static ImageWindow currentWindow;			// active image window
	private static Frame frontWindow;
	private static ImagePlus tempCurrentImage;
	
	private WindowManager() {
	}

	/** Makes the specified image active. */
	public synchronized static void setCurrentWindow(ImageWindow win) {
		setWindow(win);
		tempCurrentImage = null;
		if (win==currentWindow || win==null || imageList.size()==0)
			return;
		//if (IJ.debugMode && win!=null)
		//	IJ.write(win.getImagePlus().getTitle()+": setCurrentWindow (previous="+(currentWindow!=null?currentWindow.getImagePlus().getTitle():"null") + ")");
		if (currentWindow!=null) {
			// free up pixel buffers AWT Image resources used by current window
			ImagePlus imp = currentWindow.getImagePlus();
			if (imp!=null && imp.lockSilently()) {
				imp.trimProcessor();
				Image img = imp.getImage();
				//if (img!=null)
				//	img.flush();
				if (!Converter.newWindowCreated)
					imp.saveRoi();
				Converter.newWindowCreated = false;
				imp.unlock();
			}
		}
		Undo.reset();
		if (!win.isClosed() && win.getImagePlus()!=null)
			currentWindow = win;
		else
			currentWindow = null;
		Menus.updateMenus();
	}
	
	/** Returns the active ImageWindow. */
	public static ImageWindow getCurrentWindow() {
		//if (IJ.debugMode) IJ.write("ImageWindow.getCurrentWindow");
		return currentWindow;
	}

	static int getCurrentIndex() {
		return imageList.indexOf(currentWindow);
	}

	/** Returns the active ImagePlus. */
	public synchronized static ImagePlus getCurrentImage() {
		//if (IJ.debugMode) IJ.write("ImageWindow.getCurrentImage");
		if (tempCurrentImage!=null)
			return tempCurrentImage;
		else if (currentWindow!=null)
			return currentWindow.getImagePlus();
		else
			return null;
	}

	/** Returns the number of open images. */
	public static int getWindowCount() {
		return imageList.size();
	}

	/** Returns the front most window or null. */
	public static Frame getFrontWindow() {
		return frontWindow;
	}

	/** Returns a list of the IDs of open images. Returns
		null if no windows are open. */
	public synchronized static int[] getIDList() {
		int nWindows = imageList.size();
		if (nWindows==0)
			return null;
		int[] list = new int[nWindows];
		for (int i=0; i<nWindows; i++) {
			ImageWindow win = (ImageWindow)imageList.elementAt(i);
			list[i] = win.getImagePlus().getID();
		}
		return list;
	}

	/** For IDs less than zero, returns the ImagePlus with the specified ID. 
		Returns null if no open window has a matching ID or no images are open. 
		For IDs greater than zero, returns the Nth ImagePlus. Returns null if 
		the ID is zero. */
	public synchronized static ImagePlus getImage(int imageID) {
		//if (IJ.debugMode) IJ.write("ImageWindow.getImage");
		if (imageID==0)
			return null;
		int nImages = imageList.size();
		if (nImages==0)
			return null;
		if (imageID>0) {
			if (imageID>nImages)
				return null;
			ImageWindow win = (ImageWindow)imageList.elementAt(imageID-1);
			if (win!=null)
				return win.getImagePlus();
			else
				return null;
		}
		ImagePlus imp = null;
		for (int i=0; i<imageList.size(); i++) {
			ImageWindow win = (ImageWindow)imageList.elementAt(i);
			ImagePlus imp2 = win.getImagePlus();
			if (imageID==imp2.getID()) {
				imp = imp2;
				break;
			}
		}
		return imp;
	}

	/** Adds the specified window to the Window menu. */
	public synchronized static void addWindow(Frame win) {
		//IJ.write("addWindow: "+win.getTitle());
		if (win==null)
			return;
		else if (win instanceof ImageWindow)
			addImageWindow((ImageWindow)win);
		else {
			Menus.insertWindowMenuItem(win);
			nonImageList.addElement(win);
 		}
    }

	private static void addImageWindow(ImageWindow win) {
		imageList.addElement(win);
        Menus.addWindowMenuItem(win.getImagePlus());
        setCurrentWindow(win);
    }

	/** Removes the specified window from the Window menu. */
	public synchronized static void removeWindow(Frame win) {
		//IJ.write("removeWindow: "+win.getTitle());
		if (win instanceof ImageWindow)
			removeImageWindow((ImageWindow)win);
		else {
			int index = nonImageList.indexOf(win);
			if (index>=0) {
				Menus.removeWindowMenuItem(index);
				nonImageList.removeElement(win);
			}
		}
		setWindow(null);
	}

	private static void removeImageWindow(ImageWindow win) {
		int index = imageList.indexOf(win);
		if (index==-1)
			return;  // not on the window list
		if (imageList.size()>1) {
			int newIndex = index-1;
			if (newIndex<0)
				newIndex = imageList.size()-1;
			setCurrentWindow((ImageWindow)imageList.elementAt(newIndex));
		} else
			currentWindow = null;
		imageList.removeElementAt(index);
		int nonImageCount = nonImageList.size();
		if (nonImageCount>0)
			nonImageCount++;
		Menus.removeWindowMenuItem(nonImageCount+index);
		Menus.updateMenus();
		Undo.reset();
	}

	/** The specified frame becomes the front window, the one returnd by getFrontWindow(). */
	public static void setWindow(Frame win) {
		frontWindow = win;
		//IJ.log("Set window: "+(win!=null?win.getTitle():"null"));
    }

	/** Closes all image windows. Stops and returns false if any "save changes" dialog is canceled. */
	public synchronized static boolean closeAllWindows() {
		while (imageList.size()>0) {
			//ImagePlus imp = ((ImageWindow)imageList.elementAt(0)).getImagePlus();
			//IJ.write("Closing: " + imp.getTitle() + " " + imageList.size());
			if (!((ImageWindow)imageList.elementAt(0)).close())
				return false;
			IJ.wait(100);
		}
		return true;
    }
    
	/** Activates the next window on the window list. */
	public static void putBehind() {
		if (IJ.debugMode) IJ.log("putBehind");
		if(imageList.size()<1 || currentWindow==null)
			return;
		int index = imageList.indexOf(currentWindow);
		index--;
		if (index<0)
			index = imageList.size()-1;
		ImageWindow win = (ImageWindow)imageList.elementAt(index);
		setCurrentWindow(win);
		win.toFront();
		Menus.updateMenus();
    }

	/** Makes the specified image temporarily the active image.
		Allows use of IJ.run() commands on images that
		are not displayed in a window. Call again with a null
		argument to revert to the previous active image. */
	public static void setTempCurrentImage(ImagePlus imp) {
		tempCurrentImage = imp;
    }
    
    /** Returns the frame with the specified title or null if a frame with that 
    	title is not found. */
    public static Frame getFrame(String title) {
		for (int i=0; i<nonImageList.size(); i++) {
			Frame frame = (Frame)nonImageList.elementAt(i);
			if (title.equals(frame.getTitle()))
				return frame;
		}
		int[] wList = getIDList();
		int len = wList!=null?wList.length:0;
		for (int i=0; i<len; i++) {
			ImagePlus imp = getImage(wList[i]);
			if (imp!=null) {
				if (imp.getTitle().equals(title))
					return imp.getWindow();
			}
		}
		return null;
    }

	/** Activates a window selected from the Window menu. */
	synchronized static void activateWindow(String menuItemLabel, MenuItem item) {
		//IJ.write("activateWindow: "+menuItemLabel+" "+item);
		for (int i=0; i<nonImageList.size(); i++) {
			Frame win = (Frame)nonImageList.elementAt(i);
			String title = win.getTitle();
			if (menuItemLabel.equals(title)) {
				win.toFront();
				((CheckboxMenuItem)item).setState(false);
				if (Recorder.record)
					Recorder.record("selectWindow", title);
				return;
			}
		}
		int lastSpace = menuItemLabel.lastIndexOf(' ');
		if (lastSpace>0) // remove image size (e.g., " 90K")
			menuItemLabel = menuItemLabel.substring(0, lastSpace);
		for (int i=0; i<imageList.size(); i++) {
			ImageWindow win = (ImageWindow)imageList.elementAt(i);
			String title = win.getImagePlus().getTitle();
			if (menuItemLabel.equals(title)) {
				setCurrentWindow(win);
				win.toFront();
				int index = imageList.indexOf(win);
				int n = Menus.window.getItemCount();
				int start = Menus.WINDOW_MENU_ITEMS+Menus.windowMenuItems2;
				for (int j=start; j<n; j++) {
					MenuItem mi = Menus.window.getItem(j);
					((CheckboxMenuItem)mi).setState((j-start)==index);						
				}
				if (Recorder.record)
					Recorder.record("selectWindow", title);
				break;
			}
		}
    }
    
    static void showList() {
		if (IJ.debugMode) {
			for (int i=0; i<imageList.size(); i++) {
				ImageWindow win = (ImageWindow)imageList.elementAt(i);
				ImagePlus imp = win.getImagePlus();
				IJ.log(i + " " + imp.getTitle() + (win==currentWindow?"*":""));
			}
			IJ.log(" ");
		}
    }
    
}