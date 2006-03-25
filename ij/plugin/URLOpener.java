package ij.plugin;
import java.awt.*;
import java.io.*;
import ij.*;
import ij.io.*;
import ij.gui.*;

/** Opens TIFFs, ZIP compressed TIFFs, DICOMs, GIFs and JPEGs using a URL. 
	TIFF file names must end in ".tif", ZIP file names must end 
	in ".zip" and DICOM file names must end in ".dcm". 
	Opens a Web page in the default browser if the URL ends with "/".
*/
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
			WindowManager.checkForDuplicateName = true;
			imp.show();
			return;
		}
		
		GenericDialog gd = new GenericDialog("Enter a URL");
		gd.addMessage("Enter URL of a TIFF, JPEG, GIF, PNG or DICOM image");
		gd.addStringField("URL:", url, 40);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		url = gd.getNextString();
		url = url.trim();
		if (url.indexOf("://")==-1)
			url = "http://" + url;
		if (url.endsWith("/"))
			IJ.runPlugIn("ij.plugin.BrowserLauncher", url.substring(0, url.length()-1));
		else {
			IJ.showStatus("Opening: " + url);
			ImagePlus imp = new ImagePlus(url);
			WindowManager.checkForDuplicateName = true;
			imp.show();
			IJ.showStatus("");
		}
		IJ.register(URLOpener.class);  // keeps this class from being GC'd
	}
}
