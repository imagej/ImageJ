package ij.plugin;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.util.*;
import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.plugin.frame.*;

/** Opens TIFFs, ZIP compressed TIFFs, DICOMs, GIFs and JPEGs using a URL. 
	TIFF file names must end in ".tif", ZIP file names must end 
	in ".zip" and DICOM file names must end in ".dcm". 
	Opens a Web page in the default browser if the URL ends with "/".
*/
public class URLOpener implements PlugIn {

	private static String url = IJ.URL+"/images/clown.gif";

	/** If 'urlOrName' is a URL, opens the image at that URL. If it is
		a file name, opens the image with that name from the 'images.location'
		URL in IJ_Props.txt. If it is blank, prompts for an image
		URL and open the specified image. */
	public void run(String urlOrName) {
		if (!urlOrName.equals("")) {
			if (urlOrName.equals("cache"))
				cacheSampleImages();
			else if (urlOrName.endsWith("StartupMacros.txt"))
				openTextFile(urlOrName, true);
			else {
				double startTime = System.currentTimeMillis();
				String url = urlOrName.indexOf("://")>0?urlOrName:Prefs.getImagesURL()+urlOrName;
				ImagePlus imp = new ImagePlus(url);
				if (Recorder.record)
					Recorder.recordCall("imp = IJ.openImage(\""+url+"\");");
				if (imp.getType()==ImagePlus.COLOR_RGB)
					Opener.convertGrayJpegTo8Bits(imp);
				WindowManager.checkForDuplicateName = true;
				FileInfo fi = imp.getOriginalFileInfo();
				if (fi!=null && fi.fileType==FileInfo.RGB48)
					imp = new CompositeImage(imp, IJ.COMPOSITE);
				else if (imp.getNChannels()>1 && fi!=null && fi.description!=null && fi.description.indexOf("mode=")!=-1) {
					int mode = IJ.COLOR;
					if (fi.description.indexOf("mode=composite")!=-1)
						mode = IJ.COMPOSITE;
					else if (fi.description.indexOf("mode=gray")!=-1)
						mode = IJ.GRAYSCALE;
					imp = new CompositeImage(imp, mode);
				}
				if (fi!=null && (fi.url==null || fi.url.length()==0)) {
					fi.url = url;
					imp.setFileInfo(fi);
				}
				imp.show(Opener.getLoadRate(startTime,imp));
				if ("flybrain.tif".equals(imp.getTitle()) || "t1-head.tif".equals(imp.getTitle()) )
					imp.setSlice(imp.getStackSize()/2);
			}
			return;
		}
		
		GenericDialog gd = new GenericDialog("Enter a URL");
		gd.setInsets(10, 32, 0);
		gd.addMessage("Enter URL of an image, macro or web page", null, Color.darkGray);
		gd.addStringField("URL:", url, 45);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		url = gd.getNextString();
		url = url.trim();
		if (url.indexOf("://")==-1)
			url = "http://" + url;
		if (url.endsWith("/"))
			IJ.runPlugIn("ij.plugin.BrowserLauncher", url.substring(0, url.length()-1));
		else if (url.endsWith(".html") || url.endsWith(".htm") ||  url.indexOf(".html#")>0 || noExtension(url))
			IJ.runPlugIn("ij.plugin.BrowserLauncher", url);
		else if (url.endsWith(".txt")||url.endsWith(".ijm")||url.endsWith(".js")||url.endsWith(".java"))
			openTextFile(url, false);
		else if (url.endsWith(".jar")||url.endsWith(".class"))
			IJ.open(url);
		else {
			IJ.showStatus("Opening: " + url);
			double startTime = System.currentTimeMillis();
			ImagePlus imp = new ImagePlus(url);
			WindowManager.checkForDuplicateName = true;
			FileInfo fi = imp.getOriginalFileInfo();
			if (fi!=null && fi.fileType==FileInfo.RGB48)
				imp = new CompositeImage(imp, IJ.COMPOSITE);
			else if (imp.getNChannels()>1 && fi!=null && fi.description!=null && fi.description.indexOf("mode=")!=-1) {
				int mode = IJ.COLOR;
				if (fi.description.indexOf("mode=composite")!=-1)
					mode = IJ.COMPOSITE;
				else if (fi.description.indexOf("mode=gray")!=-1)
					mode = IJ.GRAYSCALE;
				imp = new CompositeImage(imp, mode);
			}
			if (fi!=null && (fi.url==null || fi.url.length()==0)) {
				fi.url = url;
				imp.setFileInfo(fi);
			}
			imp.show(Opener.getLoadRate(startTime,imp));
		}
		IJ.register(URLOpener.class);  // keeps this class from being GC'd
	}
	
	boolean noExtension(String url) {
		int lastSlash = url.lastIndexOf("/");
		if (lastSlash==-1) lastSlash = 0;
		int lastDot = url.lastIndexOf(".");
		if (lastDot==-1 || lastDot<lastSlash || (url.length()-lastDot)>6)
			return true;  // no extension
		else
			return false;
	}
	
	void openTextFile(String urlString, boolean install) {
		StringBuffer sb = null;
		try {
			URL url = new URL(urlString);
			InputStream in = url.openStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			sb = new StringBuffer() ;
			String line;
			while ((line=br.readLine()) != null)
				sb.append (line + "\n");
			in.close ();
		} catch (IOException e) {
			if  (!(install&&urlString.endsWith("StartupMacros.txt")))
				IJ.error("URL Opener", ""+e);
			sb = null;
		}
		if (sb!=null) {
			if (install)
				(new MacroInstaller()).install(new String(sb));
			else {
				int index = urlString.lastIndexOf("/");
				if (index!=-1 && index<=urlString.length()-1)
					urlString = urlString.substring(index+1);
				(new Editor()).create(urlString, new String(sb));
			}
		}
	}
	
	private void cacheSampleImages() {
		String[] names = getSampleImageNames();
		int n = names.length;
		if (n==0) return;
		String dir = IJ.getDirectory("imagej")+"samples";
		File f = new File(dir);
		if (!f.exists()) {
			boolean ok = f.mkdir();
			if (!ok) {
				IJ.error("Unable to create directory:\n \n"+dir);
				return;
			}
		}
		IJ.resetEscape();
		for (int i=0; i<n; i++) {
			IJ.showStatus((i+1)+"/"+n+" ("+names[i]+")");
			String url = Prefs. getImagesURL()+names[i];
			byte[] data = PluginInstaller.download(url, null);
			if (data==null) continue;
			f = new File(dir,names[i]);
			try {
				FileOutputStream out = new FileOutputStream(f);
				out.write(data, 0, data.length);
				out.close();
			} catch (IOException e) {
				IJ.log(names[i]+": "+e);
			}
			if (IJ.escapePressed())
				{IJ.beep(); break;};
		}
		IJ.showStatus("");
	}
	 
	public static String[] getSampleImageNames() {
		ArrayList list = new ArrayList();
		Hashtable commands = Menus.getCommands();
		Menu samplesMenu = Menus.getImageJMenu("File>Open Samples");
		if (samplesMenu==null)
			return new String[0];
		for (int i=0; i<samplesMenu.getItemCount(); i++) {
			MenuItem menuItem = samplesMenu.getItem(i);
			if (menuItem.getActionListeners().length == 0) continue; // separator?
			String label = menuItem.getLabel();
			if (label.contains("Cache Sample Images")) continue;
			String command = (String)commands.get(label);
			if (command==null) continue;
			String[] items = command.split("\"");
			if (items.length!=3) continue;
			String name = items[1];
			list.add(name);
		}
		return (String[])list.toArray(new String[list.size()]);
	}

}
