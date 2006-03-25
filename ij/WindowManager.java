package ij;
import ij.plugin.Converter;
import ij.plugin.frame.Recorder;
import ij.macro.Interpreter;
import ij.text.TextWindow;
import ij.plugin.frame.PlugInFrame;
import java.awt.*;
import java.util.*;
import ij.gui.*;

/** This class consists of static methods used to manage ImageJ's windows. */
public class WindowManager {

	private static Vector imageList = new Vector();		 // list of image windows
	private static Vector nonImageList = new Vector();	 // list of non-image windows
	private static ImageWindow currentWindow;			 // active image window
	private static Frame frontWindow;
	private static ImagePlus tempCurrentImage;
	public static boolean checkForDuplicateName;
	
	private WindowManager() {
	}

	/** Makes the specified image active. */
	public synchronized static void setCurrentWindow(ImageWindow win) {
		if (win==null || win.isClosed() || win.getImagePlus()==null) // deadlock-"wait to lock"
			return;
		setWindow(win);
		tempCurrentImage = null;
		if (win==currentWindow || imageList.size()==0)
			return;
		//IJ.log(win.getImagePlus().getTitle()+", previous="+(currentWindow!=null?currentWindow.getImagePlus().getTitle():"null") + ")");
		if (currentWindow!=null) {
			// free up pixel buffers AWT Image resources used by current window
			ImagePlus imp = currentWindow.getImagePlus();
			if (imp!=null && imp.lockSilently()) {
				imp.trimProcessor();
				Image img = imp.getImage();
				if (!Converter.newWindowCreated)
					imp.saveRoi();
				Converter.newWindowCreated = false;
				imp.unlock();
			}
		}
		Undo.reset();
		currentWindow = win;
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
		//IJ.log("getCurrentImage: "+tempCurrentImage+"  "+currentWindow);
		if (tempCurrentImage!=null)
			return tempCurrentImage;
		else if (currentWindow!=null)
			return currentWindow.getImagePlus();
		else if (frontWindow!=null && (frontWindow instanceof ImageWindow))
			return ((ImageWindow)frontWindow).getImagePlus();
		else 	if (imageList.size()>0) {	
			ImageWindow win = (ImageWindow)imageList.elementAt(imageList.size()-1);
			return win.getImagePlus();
		} else
			return Interpreter.getLastBatchModeImage(); 
	}

	/** Returns the number of open image windows. */
	public static int getWindowCount() {
		int count = imageList.size();
		if (count==0 && tempCurrentImage!=null)
			count = 1;
		return count;
	}

	/** Returns the number of open images. */
	public static int getImageCount() {
		int count = imageList.size();
		count += Interpreter.getBatchModeImageCount();
		if (count==0 && tempCurrentImage!=null)
			count = 1;
		return count;
	}

	/** Returns the front most window or null. */
	public static Frame getFrontWindow() {
		return frontWindow;
	}

	/** Returns a list of the IDs of open images. Returns
		null if no windows are open. */
	public synchronized static int[] getIDList() {
		int nWindows = imageList.size();
		int[] batchModeImages = Interpreter.getBatchModeImageIDs();
		int nBatchImages = batchModeImages.length;
		if ((nWindows+nBatchImages)==0)
			return null;
		int[] list = new int[nWindows+nBatchImages];
		for (int i=0; i<nBatchImages; i++)
			list[i] = batchModeImages[i];
		int index = 0;
		for (int i=nBatchImages; i<nBatchImages+nWindows; i++) {
			ImageWindow win = (ImageWindow)imageList.elementAt(index++);
			list[i] = win.getImagePlus().getID();
		}
		return list;
	}

	/** Returns an array containing a list of the non-image windows. */
	synchronized static Frame[] getNonImageWindows() {
		Frame[] list = new Frame[nonImageList.size()];
		nonImageList.copyInto((Frame[])list);
		return list;
	}

	/** For IDs less than zero, returns the ImagePlus with the specified ID. 
		Returns null if no open window has a matching ID or no images are open. 
		For IDs greater than zero, returns the Nth ImagePlus. Returns null if 
		the ID is zero. */
	public synchronized static ImagePlus getImage(int imageID) {
		//if (IJ.debugMode) IJ.write("ImageWindow.getImage");
		if (imageID==0 || getImageCount()==0)
			return null;
		if (imageID<0) {
			ImagePlus imp2 = Interpreter.getBatchModeImage(imageID);
			if (imp2!=null) return imp2;
		}
		if (imageID>0) {
            if (Interpreter.isBatchMode()) {
                int[] list = getIDList();
                if (imageID>list.length)
                	return null;
                else
                	return getImage(list[imageID-1]);
            } else {
            	if (imageID>imageList.size()) return null;
                ImageWindow win = (ImageWindow)imageList.elementAt(imageID-1);
                if (win!=null)
                    return win.getImagePlus();
                else
                    return null;
            }
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
	
	/** Returns the first image that has the specified title or null if it is not found. */
	public synchronized static ImagePlus getImage(String title) {
		int[] wList = getIDList();
		if (wList==null) return null;
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp = getImage(wList[i]);
			if (imp!=null && imp.getTitle().equals(title))
				return imp;
		}
		return null;
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
		ImagePlus imp = win.getImagePlus();
		if (imp==null) return;
		checkForDuplicateName(imp);
		imageList.addElement(win);
        Menus.addWindowMenuItem(imp);
        setCurrentWindow(win);
    }

	static void checkForDuplicateName(ImagePlus imp) {
		if (checkForDuplicateName) {
			String name = imp.getTitle();
			if (isDuplicateName(name))
				imp.setTitle(getUniqueName(name));
		} 
		checkForDuplicateName = false;
    }

	static boolean isDuplicateName(String name) {
		int n = imageList.size();
		for (int i=0; i<n; i++) {
			ImageWindow win = (ImageWindow)imageList.elementAt(i);
			String name2 = win.getImagePlus().getTitle();
			if (name.equals(name2))
				return true;
		}
		return false;
	}

	/** Returns a unique name by adding, before the extension,  -1, -2, etc. as needed. */
	public static String getUniqueName(String name) {
        String name2 = name;
        String extension = "";
        int len = name2.length();
        int lastDot = name2.lastIndexOf(".");
        if (lastDot!=-1 && len-lastDot<6 && lastDot!=len-1) {
            extension = name2.substring(lastDot, len);
            name2 = name2.substring(0, lastDot);
        }
        int lastDash = name2.lastIndexOf("-");
        if (lastDash!=-1 && name2.length()-lastDash<4)
            name2 = name2.substring(0, lastDash);
        for (int i=1; i<=99; i++) {
            String name3 = name2+"-"+ i + extension;
            //IJ.log(i+" "+name3);
            if (!isDuplicateName(name3))
                return name3;
        }
        return name;
	}

	/** Removes the specified window from the Window menu. */
	public synchronized static void removeWindow(Frame win) {
		//IJ.write("removeWindow: "+win.getTitle());
		if (win instanceof ImageWindow)
			removeImageWindow((ImageWindow)win);
		else {
			int index = nonImageList.indexOf(win);
			ImageJ ij = IJ.getInstance();
			if (index>=0) {
			 	//if (ij!=null && !ij.quitting())
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
		/*
		if (Recorder.record && win!=null && win!=frontWindow) {
			String title = win.getTitle();
			IJ.log("Set window: "+title+"  "+(getFrame(title)!=null?"not null":"null"));
			if (getFrame(title)!=null && !title.equals("Recorder"))
				Recorder.record("selectWindow", title);
		}
		*/
		frontWindow = win;
		//IJ.log("Set window: "+(win!=null?win.getTitle():"null"));
    }

	/** Closes all windows. Stops and returns false if any image "save changes" dialog is canceled. */
	public synchronized static boolean closeAllWindows() {
		while (imageList.size()>0) {
			if (!((ImageWindow)imageList.elementAt(0)).close())
				return false;
			IJ.wait(100);
		}
		Frame[] list = getNonImageWindows();
		for (int i=0; i<list.length; i++) {
			Frame frame = list[i];
			if (frame instanceof PlugInFrame)
				((PlugInFrame)frame).close();
			else if (frame instanceof TextWindow)
			((TextWindow)frame).close();
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
    
	/** Returns <code>tempCurrentImage</code>, which may be null. */
	public static ImagePlus getTempCurrentImage() {
		return tempCurrentImage;
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
    
    /** Repaints all open image windows. */
    public synchronized static void repaintImageWindows() {
		int[] list = getIDList();
		if (list==null) return;
		for (int i=0; i<list.length; i++) {
			ImagePlus imp2 = getImage(list[i]);
			if (imp2!=null) {
				ImageWindow win = imp2.getWindow();
				if (win!=null) win.repaint();
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