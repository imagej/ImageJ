package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;

/** This plugin implements the Help/About ImageJ, Help/ImageJ Web Site,
	Plugins/Utilities/Unlock Image and the Image/Rename commands. */
public class SimpleCommands implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (arg.equals("about"))
			{showAboutBox(); return;}
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
			IJ.showMessage("Unlocker", "\""+imp.getTitle()+"\" is not locked.");
		else
			IJ.showMessage("Unlocker", "\""+imp.getTitle()+"\" is now unlocked.");
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
	
	void showAboutBox() {
		MessageDialog d = new MessageDialog(IJ.getInstance(), "About ImageJ...",
			"         ImageJ " + ImageJ.VERSION + "\n" +
			" \n" +
			"Wayne Rasband (wayne@codon.nih.gov)\n" +
			"National Institutes of Health, USA\n" +
			"http://rsb.info.nih.gov/ij/\n" +
			" \n" +
			"ImageJ is in the public domain."
		);
	}
	
}
