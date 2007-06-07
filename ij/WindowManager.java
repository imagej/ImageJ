package ij;
import java.awt.*;
import java.util.*;
import ij.gui.*;

/** This class consists of static methods used to manage ImageJ's windows. */
public class WindowManager {

	private static Vector windowList = new Vector();
	private static ImageWindow currentWindow = null;


	public synchronized static void setCurrentWindow(ImageWindow win) {
		if (win==currentWindow || win==null || windowList.size()==0)
			return;
		if (IJ.debugMode && win!=null)
			IJ.write(win.getImagePlus().getTitle()+": setCurrentWindow (previous="+(currentWindow!=null?currentWindow.getImagePlus().getTitle():"null") + ")");
		if (currentWindow!=null) {
			// free up pixel buffers used by current window
			ImagePlus imp = currentWindow.getImagePlus();
			if (imp!=null && imp.lockSilently()) {
				imp.trimProcessor();
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
	
	
	public static ImageWindow getCurrentWindow() {
		if (IJ.debugMode) IJ.write("ImageWindow.getCurrentWindow");
		return currentWindow;
	}
	

	static int getCurrentIndex() {
		return windowList.indexOf(currentWindow);
	}
	

	public synchronized static ImagePlus getCurrentImage() {
		if (IJ.debugMode) IJ.write("ImageWindow.getCurrentImage");
		if (currentWindow!=null)
			return currentWindow.getImagePlus();
		else
			return null;
	}


	public static int getWindowCount() {
		return windowList.size();
	}


	/** Returns a list of the IDs of open images. Returns
		null if no windows are open. */
	public synchronized static int[] getIDList() {
		int nWindows = windowList.size();
		if (nWindows==0)
			return null;
		int[] list = new int[nWindows];
		for (int i=0; i<nWindows; i++) {
			ImageWindow win = (ImageWindow)windowList.elementAt(i);
			list[i] = win.getImagePlus().getID();
		}
		return list;
	}


	/** Returns the ImagePlus with the specified ID. Returns
		null if no open window has a matching ID. */
	public synchronized static ImagePlus getImage(int imageID) {
		if (IJ.debugMode) IJ.write("ImageWindow.getImage");
		if (imageID>=0)
			return null;
		ImagePlus imp = null;
		for (int i=0; i<windowList.size(); i++) {
			ImageWindow win = (ImageWindow)windowList.elementAt(i);
			ImagePlus imp2 = win.getImagePlus();
			if (imageID==imp2.getID()) {
				imp = imp2;
				break;
			}
		}
		return imp;
	}


	public synchronized static void addWindow(ImageWindow win) {
		if (IJ.debugMode) IJ.write(win.getImagePlus().getTitle()+": addWindow");
		windowList.addElement(win);
        Menus.extendWindowMenu(win.getImagePlus());
        setCurrentWindow(win);
    }


	/** Closes the current window and removes it from the window list. */
	public synchronized static void removeWindow(ImageWindow win) {
		if (IJ.debugMode)
			IJ.write(win.getImagePlus().getTitle() + ": removeWindow (" + windowList.size() + ")");
		int index = windowList.indexOf(win);
		if (index==-1)
			return;  // not on the window list
		if (windowList.size()>1) {
			int newIndex = index-1;
			if (newIndex<0)
				newIndex = windowList.size()-1;
			setCurrentWindow((ImageWindow)windowList.elementAt(newIndex));
		} else
			currentWindow = null;
		Menus.trimWindowMenu(windowList.size()-index);
		windowList.removeElementAt(index);
		for (int i=index; i<windowList.size(); i++) {
			ImageWindow w = (ImageWindow)windowList.elementAt(i);
			Menus.extendWindowMenu(w.getImagePlus());
		}
		Menus.updateMenus();
		Undo.reset();
	}

	/** Closes all image windows. Stops and returns false if any "save changes" dialog is canceled. */
	public synchronized static boolean closeAllWindows() {
		while (windowList.size()>0) {
			ImagePlus imp = ((ImageWindow)windowList.elementAt(0)).getImagePlus();
			//IJ.write("Closing: " + imp.getTitle() + " " + windowList.size());
			if (!((ImageWindow)windowList.elementAt(0)).close())
				return false;
			IJ.wait(100);
		}
		return true;
    }
    

	/** Activates the next window on the window list. */
	public static void putBehind() {
		if (IJ.debugMode) IJ.write("putBehind");
		if(windowList.size()<1 || currentWindow==null)
			return;
		int index = windowList.indexOf(currentWindow);
		index--;
		if (index<0)
			index = windowList.size()-1;
		ImageWindow win = (ImageWindow)windowList.elementAt(index);
		setCurrentWindow(win);
		win.toFront();
		Menus.updateMenus();
    }


	/** Activates a window selected from the Window menu. */
	synchronized static void activateWindow(String menuItemLabel, MenuItem item) {
		for (int i=0; i<windowList.size(); i++) {
			ImageWindow win = (ImageWindow)windowList.elementAt(i);
			if (menuItemLabel.startsWith(win.getImagePlus().getTitle())) {
				setCurrentWindow(win);
				win.toFront();
				int index = windowList.indexOf(win);
				int n = Menus.window.getItemCount();
				for (int j=Menus.WINDOW_MENU_ITEMS; j<n; j++) {
					MenuItem mi = Menus.window.getItem(j);
					((CheckboxMenuItem)mi).setState((j-Menus.WINDOW_MENU_ITEMS)==index);						
				}
				break;
			}
		}
    }
    

	static void showList() {
		if (IJ.debugMode) {
			for (int i=0; i<windowList.size(); i++) {
				ImageWindow win = (ImageWindow)windowList.elementAt(i);
				ImagePlus imp = win.getImagePlus();
				IJ.write(i + " " + imp.getTitle() + (win==currentWindow?"*":""));
			}
			IJ.write(" ");
		}
    }
    
}