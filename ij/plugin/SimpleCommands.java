package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;

/** This plugin implements the Plugins/Utilities/Unlock, Image/Rename
	and Plugins/Utilities/Search commands. */
public class SimpleCommands implements PlugIn {
	static String searchArg;

	public void run(String arg) {
		if (arg.equals("search"))
			{search(); return;}
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
		if (arg.equals("unlock"))
			unlock(imp);
		else if (arg.equals("rename"))
			rename(imp);
	}

	void unlock(ImagePlus imp) {
		boolean wasUnlocked = imp.lockSilently();
		if (wasUnlocked)
			IJ.showStatus("\""+imp.getTitle()+"\" is not locked");
		else {
			IJ.showStatus("\""+imp.getTitle()+"\" is now unlocked");
			IJ.beep();
		}
		imp.unlock();
	}

	void rename(ImagePlus imp) {
		GenericDialog gd = new GenericDialog("Rename");
		gd.addStringField("Title:", imp.getTitle(), 30);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		else
			imp.setTitle(gd.getNextString());
	}
		
	void search() {
		searchArg = IJ.runMacroFile("ij.jar:Search", searchArg);
	}

}
