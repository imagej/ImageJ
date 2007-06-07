package ij.plugin;
import java.awt.*;
import java.awt.image.*;
import java.util.*;
import ij.*;
import ij.io.*;

/** Uses the FileOpener class to load raw images from a URL.*/
public class RawReader implements PlugIn {

	/** Opens the image described by the string argument
	("name width height nImages bitsPerPixel offset ['black']
	['white'] ['big'] ['little']") from the images url
	specified in IJ_Props.txt.
	*/
 	public void run(String args) {
		StringTokenizer st = new StringTokenizer(args, ", \t");
		int nTokens = st.countTokens();
		if (nTokens<6) {
			IJ.write("RawReader expects at least 6 arguments: " + args);
			return;
		}
		FileInfo fi = new FileInfo();
		fi.fileFormat = fi.RAW;
		fi.url = Prefs.getImagesURL();
		fi.fileName = st.nextToken(); //1
		fi.width = getNextArg(st); //2
		fi.height = getNextArg(st); //3
		fi.nImages = getNextArg(st); //4
		fi.fileType = (getNextArg(st)==8)?FileInfo.GRAY8:FileInfo.GRAY16_UNSIGNED; //5
		fi.offset = getNextArg(st); //6
		for (int i=7; i<=nTokens; i++) {
			String arg = st.nextToken();
			if (arg.startsWith("white")) fi.whiteIsZero = true;
			else if (arg.startsWith("black")) fi.whiteIsZero = false;
			else if (arg.startsWith("little")) fi.intelByteOrder = true;
			else if (arg.startsWith("big")) fi.intelByteOrder = false;
		}
		if (IJ.debugMode) IJ.log("RawReader: "+fi);
		IJ.showStatus(fi.url + fi.fileName);
		new FileOpener(fi).open();
	}

	int getNextArg(StringTokenizer st) {
		int arg = 0;
		try {arg = Integer.parseInt(st.nextToken());}
		catch (NumberFormatException e) {IJ.write(""+e);}
		return arg;
	}
	
}