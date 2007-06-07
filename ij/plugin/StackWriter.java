package ij.plugin;
import java.awt.*;
import java.io.*;
import java.text.DecimalFormat;	
import ij.*;
import ij.io.*;
import ij.gui.*;

/** Writes the slices of stack as separate files. */
public class StackWriter implements PlugIn {

	//private static String defaultDirectory = null;
	private static String[] choices = {"Tiff","Gif","Jpeg","Raw","Zip","Text"};
	private static String fileType = "Tiff";
	private static int ndigits = 4;
	private static boolean useLabels;
	//private static boolean startAtZero;

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null || (imp!=null && imp.getStackSize()<2)) {
			IJ.error("This command requires a stack.");
			return;
		}
		String name = imp.getTitle();
		int dotIndex = name.lastIndexOf(".");
		if (dotIndex>=0)
			name = name.substring(0, dotIndex);
		
		GenericDialog gd = new GenericDialog("Save Image Sequence");
		gd.addChoice("Save Slices as:", choices, fileType);
		gd.addStringField("Name:", name, 12);
		gd.addNumericField("Digits (1-8):", ndigits, 0);
		gd.addCheckbox("Use Slice Labels as File Names", useLabels);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		fileType = gd.getNextChoice();
		name = gd.getNextString();
		ndigits = (int)gd.getNextNumber();
		useLabels = gd.getNextBoolean();
		int number = 0;
		if (ndigits<1) ndigits = 1;
		if (ndigits>8) ndigits = 8;
		if (fileType.equals("Gif") && !FileSaver.okForGif(imp))
			return;
		if (fileType.equals("Jpeg") && !FileSaver.okForJpeg(imp))
			return;

		String extension = "";
		if (fileType.equals("Tiff"))
			extension = ".tif";
		else if (fileType.equals("Jpeg"))
			extension = ".jpg";
		else if (fileType.equals("Gif"))
			extension = ".gif";
		else if (fileType.equals("Raw"))
			extension = ".raw";
		else if (fileType.equals("Zip"))
			extension = ".zip";
		else if (fileType.equals("Text"))
			extension = ".txt";
		
		String digits = getDigits(number);
		SaveDialog sd = new SaveDialog("Save Image Sequence", name+digits+extension, extension);
		String name2 = sd.getFileName();
		if (name2==null)
			return;
		String directory = sd.getDirectory();
		
		ImageStack stack = imp.getStack();
		ImagePlus tmp = new ImagePlus();
		tmp.setTitle(imp.getTitle());
		int nSlices = stack.getSize();
		String path,label=null;
		for (int i=1; i<=nSlices; i++) {
			IJ.showStatus("writing: "+i+"/"+nSlices);
			IJ.showProgress((double)i/nSlices);
			tmp.setProcessor(null, stack.getProcessor(i));
			digits = getDigits(number++);
			if (useLabels) {
				label = stack.getSliceLabel(i);
				if (label!=null && label.equals(""))
					label = null;
				if (label!=null) {
					int index = label.lastIndexOf(".");
					if (index>=0)
						label = label.substring(0, index);
				}
			}
			if (label==null)
				path = directory+name+digits+extension;
			else
				path = directory+label+extension;
			if (fileType.equals("Tiff")) {
				if (!(new FileSaver(tmp).saveAsTiff(path)))
					break;
			} else if (fileType.equals("Gif")) {
				if (!(new FileSaver(tmp).saveAsGif(path)))
					break;
			} else if (fileType.equals("Jpeg")) {
				if (!(new FileSaver(tmp).saveAsJpeg(path)))
					break;
			} else if (fileType.equals("Raw")) {
				if (!(new FileSaver(tmp).saveAsRaw(path)))
					break;
			} else if (fileType.equals("Zip")) {
				tmp.setTitle(name+digits+extension);
				if (!(new FileSaver(tmp).saveAsZip(path)))
					break;
			} else if (fileType.equals("Text")) {
				if (!(new FileSaver(tmp).saveAsText(path)))
					break;
			}
			System.gc();
		}
		IJ.showStatus("");
		IJ.showProgress(1.0);
		IJ.register(StackWriter.class);
	}
	
	String getDigits(int n) {
		String digits = "00000000"+n;
		return digits.substring(digits.length()-ndigits,digits.length());
	}
	
}

