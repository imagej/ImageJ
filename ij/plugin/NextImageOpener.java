/**
This plugin, written by Jon Harmon, implements the File/Open Next command.
It opens the "next" image in a directory, where "next" can be the
succeeding or preceeding image in the directory list.
Press shift-o to open the succeeding image or 
alt-shift-o to open the preceeding image.
It can leave the previous file open, or close it.
You may contact the author at Jonathan_Harman at yahoo.com
This code was modified from Image_Browser by Albert Cardona
*/

package ij.plugin;
import ij.*;
import ij.io.*;
import ij.gui.*;
import java.io.File;

public class NextImageOpener implements PlugIn {

	boolean forward = true; // default browse direction is forward
	boolean closeCurrent = true; //default behavior is to close current window
	ImagePlus imp0;
	
	public void run(String arg) {
		/* get changes to defaults */
		if (arg.equals("backward") || IJ.altKeyDown()) forward = false;
		if (arg.equals("backwardsc")) {
			forward = false;
			closeCurrent = false;
		}
		if (arg.equals("forwardsc")) {
			forward = true;
			closeCurrent = false;
		}
				
		// get current image; displays error and aborts if no image is open
 		imp0 = IJ.getImage();
 		// get current image directory
 		String currentPath = getDirectory(imp0);
		if (IJ.debugMode) IJ.log("OpenNext.currentPath:" + currentPath);
		if (currentPath==null) {
			IJ.error("Next Image", "Directory information for \""+imp0.getTitle()+"\" not found.");
			return;
		}
		String nextPath = getNext(currentPath, getName(imp0), forward);
		if (IJ.debugMode) IJ.log("OpenNext.nextPath:" + nextPath);
		// open
		if (nextPath != null) {
			String rtn = open(nextPath);
			if (rtn==null)
				open(getNext(currentPath, (new File(nextPath)).getName(), forward));
		}
	}
	
	String getDirectory(ImagePlus imp) {
		FileInfo fi = imp.getOriginalFileInfo();
		if (fi==null) return null;
		String dir = fi.openNextDir;
		if (dir==null) dir = fi.directory;
		return dir;
	}

	String getName(ImagePlus imp) {
		String name = imp.getTitle();
		FileInfo fi = imp.getOriginalFileInfo();
		if (fi!=null) {
			if (fi.openNextName!=null)
				name = fi.openNextName;
			else if (fi.fileName!=null)
				name = fi.fileName;
		}
		return name;
	}
	
	String open(String nextPath) {
		int nImages = WindowManager.getImageCount();
		ImagePlus imp2 = IJ.openImage(nextPath);		
		if (imp2==null) {
			if (WindowManager.getImageCount()>nImages)
				return "ok";
			else
				return null;
		}
		String newTitle = imp2.getTitle();
		if (imp0.changes) {
			String msg;
			String name = imp0.getTitle();
			if (name.length()>22)
				msg = "Save changes to\n" + "\"" + name + "\"?";
			else
				msg = "Save changes to \"" + name + "\"?";
			YesNoCancelDialog d = new YesNoCancelDialog(imp0.getWindow(), "ImageJ", msg);
			if (d.cancelPressed())
				return "Canceled";
			else if (d.yesPressed()) {
				FileSaver fs = new FileSaver(imp0);
				if (!fs.save())
					return "Canceled";
			}
			imp0.changes = false;
		}
		if (!(imp0 instanceof CompositeImage) && (imp2.isComposite() || imp2.isHyperStack())) {
			// imp0.setImage(imp2) does not work if 'imp2' is composite and 'imp0' is not
			imp2.show();
			imp0.close();
			imp0 = imp2;
		} else
			imp0.setImage(imp2);
		return "ok";
	}
	
	/** gets the next image name in a directory list */
	String getNext(String path, String imageName, boolean forward) {
		File dir = new File(path);
		if (!dir.isDirectory()) return null;
		String[] names = dir.list();
		ij.util.StringSorter.sort(names);
		int thisfile = -1;
		for (int i=0; i<names.length; i++) {
			if (names[i].equals(imageName)) {
				thisfile = i;
				break;
			}
		}
		if (IJ.debugMode) IJ.log("OpenNext.thisfile:" + thisfile);
		if(thisfile == -1) return null;// can't find current image
		
		// make candidate the index of the next file
		int candidate = thisfile + 1;
		if (!forward) candidate = thisfile - 1;
		if (candidate<0) candidate = names.length - 1;
		if (candidate==names.length) candidate = 0;
		// keep on going until an image file is found or we get back to beginning
		while (candidate!=thisfile) {
			String nextPath = path + names[candidate];
			if (IJ.debugMode) IJ.log("OpenNext: "+ candidate + "  " + names[candidate]);
			File nextFile = new File(nextPath);
			boolean canOpen = true;
			if (names[candidate].startsWith(".") || nextFile.isDirectory())
				canOpen = false;
			if (canOpen) {
				Opener o = new Opener();
				int type = o.getFileType(nextPath);
				if (type==Opener.JAVA_OR_TEXT
				||  type==Opener.ROI ||  type==Opener.TEXT)
					canOpen = false;
			}
			if (canOpen)
					return nextPath;
			else {// increment again
				if (forward)
					candidate = candidate + 1;
				else
					candidate = candidate - 1;
				if (candidate<0) candidate = names.length - 1;
				if (candidate == names.length) candidate = 0;
			}
			
		}
		if (IJ.debugMode) IJ.log("OpenNext: Search failed");
		return null;
	}

}
