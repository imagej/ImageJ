package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import java.awt.*;
import java.io.*;

/** This plugin opens a multi-page TIFF file as a virtual stack. */
public class FileInfoVirtualStack extends VirtualStack implements PlugIn {
	FileInfo[] info;
	int nSlices;

	public void run(String arg) {
		OpenDialog  od = new OpenDialog("Open TIFF", arg);
		String name = od.getFileName();
		if (name==null) return;
		if (name.endsWith(".zip")) {
			IJ.error("Virtual Stack", "ZIP compressed stacks not supported");
			return;
		}
		String  dir = od.getDirectory();
		TiffDecoder td = new TiffDecoder(dir, name);
		if (IJ.debugMode) td.enableDebugging();
		try {info = td.getTiffInfo();}
		catch (IOException e) {
			String msg = e.getMessage();
			if (msg==null||msg.equals("")) msg = ""+e;
			IJ.error("TiffDecoder", msg);
			return;
		}
		if (info==null || info.length==0) {
			IJ.error("Virtual Stack", "This does not appear to be a TIFF stack");
			return;
		}
		FileInfo fi = info[0];
		//IJ.log(""+fi);
		int n = fi.nImages;
		if (info.length==1 && n>1) {
			info = new FileInfo[n];
			int size = fi.width*fi.height*fi.getBytesPerPixel();
			for (int i=0; i<n; i++) {
				info[i] = (FileInfo)fi.clone();
				info[i].nImages = 1;
				info[i].offset = fi.offset + i*(size + fi.gapBetweenImages);
			}
		}
		nSlices = info.length;
		FileOpener fo = new FileOpener(info[0]);
		ImagePlus imp = fo.open(false);
		ImagePlus imp2 = new ImagePlus(fi.fileName, this);
		if (imp!=null) {
			imp2.setCalibration(imp.getCalibration());
			int[] dim = imp.getDimensions();
			imp2.setDimensions(dim[2], dim[3], dim[4]);
			IJ.log(dim[2]+"  "+dim[3]+"  "+dim[4]);
		}
		imp2.show();
	}

	/** Deletes the specified slice, were 1<=n<=nslices. */
	public void deleteSlice(int n) {
		if (n<1 || n>nSlices)
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (nSlices<1) return;
		for (int i=n; i<nSlices; i++)
			info[i-1] = info[i];
		info[nSlices-1] = null;
		nSlices--;
	}
	
	/** Returns an ImageProcessor for the specified slice,
		were 1<=n<=nslices. Returns null if the stack is empty.
	*/
	public ImageProcessor getProcessor(int n) {
		if (n<1 || n>nSlices)
			throw new IllegalArgumentException("Argument out of range: "+n);
		info[n-1].nImages = 1; // why is this needed?
		FileOpener fo = new FileOpener(info[n-1]);
		ImagePlus imp = fo.open(false);
		if (imp!=null)
			return imp.getProcessor();
		else
			return null;
	 }
 
	 /** Returns the number of slices in this stack. */
	public int getSize() {
		return nSlices;
	}

	/** Returns null. */
	public String getSliceLabel(int n) {
		 return null;
	}

}
