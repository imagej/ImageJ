package ij.plugin;
import java.awt.*;
import java.io.*;
import ij.*;
import ij.io.*;
import ij.gui.*;

/** Opens TIFFs, ZIP compressed TIFFs, GIFs and JPEGs using a URL. */
public class URLOpener implements PlugIn {

	private static String url = "http://rsb.info.nih.gov/ij/images/clown.gif";

	/** If 'urlOrName' is a URL, opens the image at that URL. If it is
		a file name, opens the image with that name from the 'images.location'
		URL in IJ_Props.txt. If it is blank, prompts for an image
		URL and open the specified image. */
	public void run(String urlOrName) {
		if (!urlOrName.equals("")) {
			String url = urlOrName.indexOf("://")>0?urlOrName:Prefs.getImagesURL()+urlOrName;
			ImagePlus imp = new ImagePlus(url);
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
