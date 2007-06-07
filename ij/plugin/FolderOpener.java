package ij.plugin;
import java.awt.*;
import java.io.*;
import ij.*;
import ij.io.*;
import ij.process.*;

/** Opens a folder of images as a stack. */
public class FolderOpener implements PlugIn {

	public void run(String arg) {
		OpenDialog od = new OpenDialog("Open All As Stack...", arg);
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return;

		String[] list = new File(directory).list();
		if (list==null)
			return;
		ij.util.StringSorter.sort(list);
		if (IJ.debugMode) IJ.write("FolderOpener: "+directory+" ("+list.length+" files)");
		int width=0,height=0,type=0;
		ImageStack stack = null;
		int n = 0;
		try {
			for (int i=0; i<list.length; i++) {
				ImagePlus imp = new Opener().openImage(directory, list[i]);
				if (imp!=null && stack==null) {
					stack = imp.createEmptyStack();
					width = imp.getWidth();
					height = imp.getHeight();
					type = imp.getType();
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
				else
					stack.addSlice(imp.getTitle(), imp.getProcessor());
				System.gc();
			}
		} catch(OutOfMemoryError e) {
			IJ.outOfMemory("FolderOpener");
			stack.trim();
		}
		if (stack!=null && stack.getSize()>0)
			new ImagePlus("Stack", stack).show();
		IJ.showProgress(1.0);
	}

}

