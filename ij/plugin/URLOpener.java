package ij.plugin;
import java.awt.*;
import java.io.*;
import ij.*;
import ij.io.*;
import ij.gui.*;

/** Opens TIFFs, ZIP compressed TIFFs, GIFs and JPEGs using a URL. */
public class URLOpener implements PlugIn {

	private static String url = "http://rsb.info.nih.gov/ij/images/clown.gif";

	/** If 'name' is not blank, open that image from a URL specified in
	IJ_Props.txt. Otherwise, prompt for an image URL and open that image. */
	public void run(String name) {
		if (!name.equals("")) {
			ImagePlus imp = new ImagePlus(Prefs.getImagesURL()+name);
			if (imp.getType()==ImagePlus.COLOR_RGB)
				Opener.convertGrayJpegTo8Bits(imp);
			imp.show();
			return;
		}
		
		GenericDialog gd = new GenericDialog("Enter a URL");
		gd.addMessage("Enter the URL of a TIFF, JPEG or GIF image:");
		gd.addStringField("", url, 40);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		String url = gd.getNextString();
		IJ.showStatus("Opening: " + url);
		ImagePlus imp = new ImagePlus(url);
		imp.show();
		IJ.showStatus("");
		IJ.register(URLOpener.class);  // keeps this class from being GC'd
	}
}
