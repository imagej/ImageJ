package ij.plugin;

import java.awt.*;
import java.awt.image.*;
import java.util.Vector;
import java.io.*;
import ij.*;
import ij.process.*;
import ij.io.*;
import ij.gui.*;
import ij.measure.ResultsTable;


/** Writes the XY coordinates and pixel values of all non-background pixels
	to a tab-delimited text file. Backround is assumed to be the value of
	the pixel in the upper left corner of the image. */
public class XYCoordinates implements PlugIn {

	static boolean processStack;
	static boolean invertY;
	static boolean suppress;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		Roi roi = imp.getRoi();
		String options = Macro.getOptions();
		boolean legacyMacro = IJ.isMacro() && options!=null && options.contains("background=");
		if (roi!=null && roi.isArea() && !legacyMacro) {
			saveSelectionCoordinates(imp);
			return;
		}
		ImageProcessor ip = imp.getProcessor();
		int width = imp.getWidth();
		int height = imp.getHeight();
		double background = ip.getPixelValue(0,0);
		String bg = " \n";
		boolean rgb = imp.getBitDepth()==24;
		if (rgb) {
			int c = ip.getPixel(0,0);
			int r = (c&0xff0000)>>16;
			int g = (c&0xff00)>>8;
			int b = c&0xff;
			bg = r+","+g+","+b;
		    bg = " \n    Background value: " + bg + "\n";
		}
		imp.deleteRoi();
		
		int slices = imp.getStackSize();
		String msg =
			"This plugin writes to a text file the XY coordinates and\n"
			+ "pixel value of all non-background pixels. Backround\n"
			+ "defaults to be the value of the pixel in the upper\n"
			+ "left corner of the image.\n \n"
			+ "If there is a selection, this dialog is skipped and the\n"
			+ "coordinates and values of pixels in the selection are saved.\n";
				
		GenericDialog gd = new GenericDialog("Save XY Coordinates");
		gd.setInsets(0, 20, 0);
		gd.addMessage(msg, null, Color.darkGray);
		int digits = (int)background==background?0:4;
		if (!rgb) {
			gd.setInsets(5, 35, 3);
			gd.addNumericField("Background value:", background, digits);
		}
		gd.setInsets(10, 35, 0);
		gd.addCheckbox("Invert y coordinates off (0 at top of image)", invertY);
		gd.setInsets(0, 35, 0);
		gd.addCheckbox("Suppress Log output", suppress);
		if (slices>1) {
			gd.setInsets(0, 35, 0);
			gd.addCheckbox("Process all "+slices+" images", processStack);
		}
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		if (!rgb)
			background = gd.getNextNumber();
		invertY = gd.getNextBoolean();
		suppress = gd.getNextBoolean();
		if (slices>1)
			processStack = gd.getNextBoolean();
		else
			processStack = false;
		if (!processStack) slices = 1;

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
		float v;
		int c,r,g,b;
		int type = imp.getType();
		ImageStack stack = imp.getStack();
		for (int z=0; z<slices; z++) {
			if (slices>1) ip = stack.getProcessor(z+1);
			String zstr = slices>1?z+"\t":"";
			for (int i=0; i<height; i++) {
				int y = invertY?i:height-1-i;
				for (int x=0; x<width; x++) {
					v = ip.getPixelValue(x,y);
					if (v!=background) {
						if (type==ImagePlus.GRAY32)
							pw.println(x+"\t"+(invertY?y:height-1-y)+"\t"+zstr+v);
						else if (rgb) {
							c = ip.getPixel(x,y);
							r = (c&0xff0000)>>16;
							g = (c&0xff00)>>8;
							b = c&0xff;
							pw.println(x+"\t"+(invertY?y:height-1-y)+"\t"+zstr+r+"\t"+g+"\t"+b);
						} else
							pw.println(x+"\t"+(invertY?y:height-1-y)+"\t"+zstr+(int)v);
						count++;
					}
				} // x
				if (slices==1&&y%10==0) IJ.showProgress((double)(height-y)/height);
			} // y
			if (slices>1) IJ.showProgress(z+1, slices);
			String img = slices>1?"-"+(z+1):"";
			if (!suppress)
				IJ.log(imp.getTitle() + img+": " + count + " pixels (" + IJ.d2s(count*100.0/(width*height)) + "%)\n");
			count = 0;
		} // z
		IJ.showProgress(1.0);
		IJ.showStatus("");
		pw.close();
	}
	
	private void saveSelectionCoordinates(ImagePlus imp) {
		SaveDialog sd = new SaveDialog("Save Coordinates as Text...", imp.getTitle(), ".csv");
		String name = sd.getFileName();
		if (name == null)
			return;
		String dir = sd.getDirectory();
		Roi roi = imp.getRoi();
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor mask = roi.getMask();
		Rectangle r = roi.getBounds();
		ResultsTable rt = new ResultsTable();
		boolean rgb = imp.getBitDepth()==24;
		for (int y=0; y<r.height; y++) {
			for (int x=0; x<r.width; x++) {
				if (mask.getPixel(x,y)!=0) {
					rt.incrementCounter();
					rt.addValue("X", r.x+x);
					rt.addValue("Y", r.y+y);
					if (rgb) {
						int c = ip.getPixel(r.x+x,r.y+y);
						rt.addValue("Red", (c&0xff0000)>>16);
						rt.addValue("Green", (c&0xff00)>>8);
						rt.addValue("Blue", c&0xff);
					} else
						rt.addValue("Value", ip.getPixelValue(r.x+x,r.y+y));
				}
			}
		}
		//rt.show("Results");
		try {
			rt.saveAs(dir+name);
		} catch (IOException e) {
			IJ.error(""+e);
		}
	}

}
