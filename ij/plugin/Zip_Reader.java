package ij.plugin;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.zip.*;
import ij.*;
import ij.io.*;
import ij.process.*;

/** This plugin opens a single TIFF image or stack contained in a ZIP archive
	or a ZIPed collection of ".roi" files created by the ROI manager. */
public class Zip_Reader extends ImagePlus implements PlugIn {

	private static final String TEMP_NAME = "temp.tif";
	private int width, height;
	private boolean rawBits;
	private String tifName="";
	private	String dir;
	
	public void run(String arg) {
		OpenDialog od = new OpenDialog("ZIP/TIFF Reader...", arg);
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return;
		String path = directory + name;
		dir = IJ.getDirectory("temp");
		if (dir==null)
			dir = IJ.getDirectory("startup");
		if (dir==null)
			dir = "";
		IJ.showStatus("Opening: " + path);
		ImagePlus imp;
		try {
			imp = openZip(path);
		}
		catch (Exception e) {
			IJ.error("ZIP Reader", e.getMessage());
			return;
		}
		if (imp!=null) {
			setStack(tifName, imp.getStack());
			setCalibration(imp.getCalibration());
			if (imp.getStackSize()>1) {
				int[] dim = imp.getDimensions();
				setDimensions(dim[2], dim[3], dim[4]);
			}
			FileInfo fi = new FileInfo();
			fi.fileFormat = FileInfo.ZIP_ARCHIVE;
			fi.fileName = name;
			fi.directory = directory;
			fi.width = getWidth();
			fi.height = getHeight();
			fi.nImages = getStackSize();
			setFileInfo(fi);
			Object info = imp.getProperty("Info");
			if (info!=null) setProperty("Info", info);
			if (arg.equals("")) show();
		}
	}

	public ImagePlus openZip(String path) throws IOException {
		String name = extractTiffFile(path);
		if (name==null)
			return null;
		else {
			tifName = name;
			ImagePlus imp = new Opener().openTiff(dir, TEMP_NAME);
			new File(dir+TEMP_NAME).delete();
			return imp;
		}
	}

	String extractTiffFile(String path) throws IOException {
		ZipInputStream in = new ZipInputStream(new FileInputStream(path));
		OutputStream out = new FileOutputStream(dir+TEMP_NAME);
		byte[] buf = new byte[1024];
		int len;
		ZipEntry entry = in.getNextEntry();
		if (entry==null)
			return null;
		String name = entry.getName();
		if (name.endsWith(".roi")) {
			out.close(); in.close();
			IJ.runMacro("roiManager(\"Open\", getArgument());", path);
			return null;
		} else if (!name.endsWith(".tif")) {
			out.close(); in.close();
			throw new IOException("This ZIP archive does not appear to contain a TIFF file or ROIs");
		}
		while ((len = in.read(buf)) > 0)
			out.write(buf, 0, len);
		out.close();
		in.close();
		return name;
	}

}


