package ij.plugin.filter;
import java.awt.Frame;
import ij.*;
import ij.process.*;
import ij.gui.*;

/** This plug-in implements ImageJ's Image/Duplicate command. */
public class Duplicater implements PlugInFilter {
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		duplicate(imp);
	}

	public void duplicate(ImagePlus imp) {
		ImageProcessor ip2 = imp.getProcessor().crop();
		imp.killRoi();
		imp.trimProcessor();
		String newTitle;
		String title = imp.getTitle();
		if (imp.getStackSize()>1)
			newTitle = title + "-" + imp.getCurrentSlice();
		else {
			if (!title.endsWith("-copy"))
				newTitle = title + "-copy";
			else
				newTitle = title;
		}
		if (!IJ.altKeyDown())
			newTitle = getString("Duplicate...", "Title: ", newTitle);
		if (newTitle.equals(""))
			return;
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setProcessor(newTitle, ip2);
		imp2.show();
	}
                
	String getString(String title, String prompt, String defaultString) {
		Frame win = imp.getWindow();
		if (win==null)
			win = IJ.getInstance();
		GenericDialog gd = new GenericDialog(title, win);
		gd.addStringField(prompt, defaultString, 20);
		gd.showDialog();
		if (gd.wasCanceled())
			return "";
		return gd.getNextString();
	}
	
}