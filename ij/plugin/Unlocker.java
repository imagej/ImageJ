package ij.plugin;
import ij.*;
import ij.process.*;

/** This plugin implements the Plugins/Utilities/Unlock Image command. */
public class Unlocker implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
		boolean wasUnlocked = imp.lockSilently();
		if (wasUnlocked)
			IJ.showMessage("Unlocker", "\""+imp.getTitle()+"\" is not locked.");
		else
			IJ.showMessage("Unlocker", "\""+imp.getTitle()+"\" is now unlocked.");
		imp.unlock();
	}

}
