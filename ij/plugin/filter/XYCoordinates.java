package ij.plugin.filter;

import java.awt.*;
import java.awt.image.*;
import java.util.Vector;
import java.io.*;
import ij.*;
import ij.process.*;
import ij.io.*;


/**Writes the XY coordinates of all black pixels in the active image to a text file.*/
public class XYCoordinates implements PlugInFilter {

	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_8G+DOES_8C;
	}

	public void run(ImageProcessor ip) {
		int width = imp.getWidth();
		int height = imp.getHeight();
		int background = ip.getPixel(0,0);
		imp.killRoi();
		
		boolean okay = IJ.showMessageWithCancel("XY_Coordinates", 
			"This plugin writes to a text file the XY coordinates and\n"
			+ "pixel value of all non-background pixels. Backround\n"
			+ "is assumed to be the value of the pixel in the\n"
			+ "upper left corner of the image.\n"
			+ " \n"
			+ "width: " + width + "\n"
			+ "height: " + height + "\n"
			+ "background value: " + background + "\n"
			);
		if (!okay)
			return;
				
		SaveDialog sd = new SaveDialog("Save Coordinates as Text...", imp.getTitle(), ".txt");
		String name = sd.getFileName();
		if (name == null)
			return;
		String directory = sd.getDirectory();
		PrintWriter pw = null;
		try {
			FileOutputStream fos = new FileOutputStream(directory+name);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			pw = new PrintWriter(bos);
		}
		catch (IOException e) {
			IJ.write("" + e);
			return;
		}

		IJ.showStatus("Saving coordinates...");
		int count = 0;
		byte[] pixels = (byte[])ip.getPixels();
		String ls = System.getProperty("line.separator");
		int v;
		for (int y=height-1; y>=0; y--) {
			for (int x=0; x<width; x++) {
				v = pixels[y*width+x]&0xff;
				if (v!=background) {
					pw.print(x+" "+(height-1-y)+" "+v+ls);
					count++;
				}
			}
			if (y%10==0) IJ.showProgress((double)(height-y)/height);
		}
		IJ.showProgress(1.0);
		pw.close();
		IJ.write(imp.getTitle() + ": " + count + " pixels (" + IJ.d2s(count*100.0/(width*height)) + "%)\n");
	}

}
