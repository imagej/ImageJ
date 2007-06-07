package ij.plugin;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.process.*;

/** Opens a folder of images as a stack. */
public class FolderOpener implements PlugIn {

	private static boolean grayscale;
	private static boolean halfSize;

	public void run(String arg) {
		OpenDialog od = new OpenDialog("Open All As Stack...", arg);
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return;

		String[] list = new File(directory).list();
		if (list==null)
			return;
		IJ.register(FolderOpener.class);
		ij.util.StringSorter.sort(list);
		if (IJ.debugMode) IJ.write("FolderOpener: "+directory+" ("+list.length+" files)");
		int width=0,height=0,type=0;
		ImageStack stack = null;
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		int n = 0;
		try {
			for (int i=0; i<list.length; i++) {
				if (list[i].endsWith(".txt"))
					continue;
				ImagePlus imp = new Opener().openImage(directory, list[i]);
				if (imp!=null && stack==null) {
					width = imp.getWidth();
					height = imp.getHeight();
					type = imp.getType();
					if (!showDialog(imp, list.length))
						return;
					ColorModel cm = imp.getProcessor().getColorModel();
					if (halfSize)
						stack = new ImageStack(width/2, height/2, cm);
					else
						stack = new ImageStack(width, height, cm);
				}
				if (stack!=null)
					n = stack.getSize()+1;
				IJ.showStatus(n+"/"+list.length);
				IJ.showProgress((double)n/list.length);
				if (imp==null)
					IJ.write(list[i] + ": unable to open");
				else if (imp.getWidth()!=width || imp.getHeight()!=height)
					IJ.write(list[i] + ": wrong dimensions");
				else if (imp.getType()!=type)
					IJ.write(list[i] + ": wrong type");
				else {
					ImageProcessor ip = imp.getProcessor();
					if (grayscale)
						ip = ip.convertToByte(true);
					if (halfSize)
						ip = ip.resize(width/2, height/2);
					if (ip.getMin()<min) min = ip.getMin();
					if (ip.getMax()>max) max = ip.getMax();
					stack.addSlice(imp.getTitle(), ip);
				}
				System.gc();
			}
		} catch(OutOfMemoryError e) {
			IJ.outOfMemory("FolderOpener");
			stack.trim();
		}
		if (stack!=null && stack.getSize()>0) {
			ImagePlus imp2 = new ImagePlus("Stack", stack);
			if (imp2.getType()==ImagePlus.GRAY16 || imp2.getType()==ImagePlus.GRAY32)
				imp2.getProcessor().setMinAndMax(min, max);
			imp2.show();
		}
		IJ.showProgress(1.0);
	}
	
	boolean showDialog(ImagePlus imp, int fileCount) {
		GenericDialog gd = new FolderOpenerDialog("Sequence Options", imp, fileCount);
		gd.addCheckbox("Convert to 8-bits", grayscale);
		gd.addCheckbox("Open 1/2 Size", halfSize);
		gd.addMessage("10000 x 10000 x 1000 (100.3MB)");
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		grayscale = gd.getNextBoolean();
		halfSize = gd.getNextBoolean();
		return true;
	}

}

class FolderOpenerDialog extends GenericDialog {
	ImagePlus imp;
	int fileCount;
 	boolean eightBits, halfSize;

	public FolderOpenerDialog(String title, ImagePlus imp, int fileCount) {
		super(title);
		this.imp = imp;
		this.fileCount = fileCount;
	}

    protected void setup() {
   		setStackInfo();
    }
 	
	public void itemStateChanged(ItemEvent e) {
 		setStackInfo();
	}
	
	void setStackInfo() {
		int width = imp.getWidth();
		int height = imp.getHeight();
		int bytesPerPixel = 1;
 		eightBits = checkbox[0].getState();
 		halfSize = checkbox[1].getState();
		switch (imp.getType()) {
			case ImagePlus.GRAY16:
				bytesPerPixel=2;break;
			case ImagePlus.COLOR_RGB:
			case ImagePlus.GRAY32:
				bytesPerPixel=4; break;
		}
		if (eightBits)
			bytesPerPixel = 1;
		if (halfSize) {
			width /= 2;
			height /= 2;
		}
		double size = (double)(width*height*fileCount*bytesPerPixel)/(1024*1024);
 		((Label)theLabel).setText(width+" x "+height+" x "+fileCount+" ("+IJ.d2s(size,1)+"MB)");
	}

}
