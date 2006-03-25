package ij.plugin;

import java.awt.*;
import java.awt.image.*;
import java.util.Vector;
import ij.*;
import ij.gui.*;

/** ImageJ plugin that tries to find out how much memory is available for opening images. */
public class MemoryTest implements PlugIn {


	public void run(String arg) {
	
		boolean okay = IJ.showMessageWithCancel("Memory Test", 
			"The MemoryTest plugin opens as many\n"
			+ "1024x1024x8-bit images as possible and then\n"
			+ "calculates how much memory was used for each\n"
			+ "image. This is a demanding test that may require\n"
			+ "ImageJ to be restarted, may crash your browser, \n"
			+ "or may even crash your machine."
			);
		if (!okay)
			return;

		int width = 1024;
		int arraySize = 1024*1024;
		Vector objects;
		int nArrays = 0;
		int nImages = 0;
		byte[] pixels;
		ColorModel cm;
		Image img, saveImg=null;

		IJ.write("");
		IJ.write("Opening " + width + "x" + width + " 8-bit image windows...");
		collectGarbage();
		int[] times = new int[2000];
		long time, time2;
		time = System.currentTimeMillis();
		try {
			while(true) {
				NewImage.open(""+(nImages+1), width, width, 1, NewImage.GRAY8, NewImage.FILL_WHITE);
				time2 = System.currentTimeMillis();
				times[nImages] = (int)(time2-time);
				time = time2;
				nImages++;
			}
		}
		catch(OutOfMemoryError ex) {
			IJ.write("Closing windows...");
			WindowManager.closeAllWindows();
			collectGarbage();
		}

		IJ.wait(100);
		objects = new Vector();
		IJ.write("Probing memory...");
		try {
			while(true) {
				System.gc();
				byte[] a = new byte[arraySize];
				objects.addElement(a);
				nArrays++;
				IJ.showStatus((nArrays*arraySize)/(1024*1024) + "MB");
				IJ.wait(50);
			}
		}
		catch(OutOfMemoryError e) {
			objects = null;
			collectGarbage();
			IJ.write(arraySize*nArrays/(1024*1024) + "MB is available");
			IJ.write(nImages + " images were opened");
			if (nImages>0)
				IJ.write("~" + arraySize*nArrays/(nImages*1024) + "K was required for each image");
			IJ.write("");
			//for (int i=0; i<nImages; i++)
			//	IJ.write((i+1)+": "+times[i]);
			IJ.showStatus("");
		}

	}

	void collectGarbage() {
		for (int i=0; i<10; i++)
			System.gc();
	}

}
