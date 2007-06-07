package ij;
import ij.process.*;
import ij.io.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;

/** The class contains methods that perform macro operations. */
public class Macros {

	public void run(String command) {
		IJ.run(command);
	}

	public void open() {
		Opener o = new Opener();
		o.openImage();
	}

	public boolean open(String path) {
		if (path==null || path.equals(""))
			open();
		else {
			Opener o = new Opener();
			ImagePlus img = o.openImage(getDir(path), getName(path));
			if (img==null)
				return false;
			else
				img.show();
		}
		return true;		
	}

	public boolean importRaw(String path, String args, int width, int height, int offset) {
		return importRaw(path, args, width, height, offset, 1, 0);
	}

	public boolean importRaw(String path, String args, int width, int height, int offset, int nImages, int gap) {
		File f = new File(path);
		args = args.toLowerCase();
		FileInfo fi = new FileInfo();
		fi.fileFormat = fi.RAW;
		fi.fileName = getName(path);
		fi.directory = getDir(path);
		fi.width = width;
		fi.height = height;
		fi.offset = offset;
		fi.nImages = nImages;
		fi.gapBetweenImages = gap;
		fi.intelByteOrder = args.indexOf("little")>=0;
		fi.whiteIsZero = args.indexOf("white")>=0;
		if (args.indexOf("16-bit")>=0) {
			if (args.indexOf("unsigned")>=0)
				fi.fileType = FileInfo.GRAY16_UNSIGNED;
			else
				fi.fileType = FileInfo.GRAY16_SIGNED;
		} else if (args.indexOf("32-bit")>=0) {
			if (args.indexOf("integer")>=0)
				fi.fileType = FileInfo.GRAY32_INT;
			else
				fi.fileType = FileInfo.GRAY32_FLOAT;
		} else if (args.indexOf("RGB")>=0) {
			if (args.indexOf("planar")>=0)
				fi.fileType = FileInfo.RGB_PLANAR;
			else
				fi.fileType = FileInfo.RGB;
		} else
			fi.fileType = FileInfo.GRAY8;
			
		if (IJ.debugMode) IJ.write("importRaw(): "+fi);
		//if (openAll) {
		//	String[] list = new File(directory).list();
		//	if (list==null)
		//		return;
		//	openAll(list, fi);
		//} else {
		FileOpener fo = new FileOpener(fi);
		fo.open();
		return true;
	}
	
	public String getName(String path) {
		int i = path.lastIndexOf('/');
		if (i==-1)
			i = path.lastIndexOf('\\');
		if (i>0)
			return path.substring(i+1);
		else
			return path;
	}
	
	public String getDir(String path) {
		int i = path.lastIndexOf('/');
		if (i==-1)
			i = path.lastIndexOf('\\');
		if (i>0)
			return path.substring(0, i+1);
		else
			return "";
	}

}

