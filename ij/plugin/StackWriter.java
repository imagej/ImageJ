package ij.plugin;
import java.awt.*;
import java.io.*;
import java.text.DecimalFormat;	
import java.util.*;
import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.measure.Calibration;
import ij.process.*;
import ij.plugin.frame.Recorder;
import ij.macro.Interpreter;
import ij.util.Tools;

/** This plugin, which saves the images in a stack as separate files, 
	implements the File/Save As/Image Sequence command. */
public class StackWriter implements PlugIn {
	private static final String DIR_KEY = "save.sequence.dir";
	private static String[] choices = {"BMP",  "FITS", "GIF", "JPEG", "PGM", "PNG", "Raw", "Text", "TIFF",  "ZIP"};
	private static String staticFileType = "TIFF";
	private String fileType = "TIFF";
	private int ndigits = 4;
	private boolean useLabels;
	private boolean firstTime = true;
	private int startAt;
	private boolean hyperstack;
	private int[] dim;
	private ImagePlus imp;
	private String directory;
	private String format = "tiff";
	private String name;
	
	/** Saves the specified image as a sequence of images. */
	public static void save(ImagePlus imp, String directoryPath, String options) {
		StackWriter sw = new StackWriter();
		sw.imp = imp;
		sw.format = Tools.getStringFromList(options, "format=", sw.format);
		sw.name = Tools.getStringFromList(options, "name=");
		sw.ndigits = (int)Tools.getNumberFromList(options, "digits=", sw.ndigits);
		sw.useLabels = options.contains(" use");
		sw.run(directoryPath);
	}


	public void run(String arg) {
		if (imp==null)
			imp = WindowManager.getCurrentImage();
		if (imp==null || (imp!=null && imp.getStackSize()<2&&!IJ.isMacro())) {
			IJ.error("Stack Writer", "This command requires a stack.");
			return;
		}
		int stackSize = imp.getStackSize();
		if (name==null) {
			name = imp.getTitle();
			int dotIndex = name.lastIndexOf(".");
			if (dotIndex>=0)
				name = name.substring(0, dotIndex);
		}
		hyperstack = imp.isHyperStack();
		LUT[] luts = null;
		int lutIndex = 0;
		int nChannels = imp.getNChannels();
		if (hyperstack) {
			dim = imp.getDimensions();
			if (imp.isComposite())
				luts = ((CompositeImage)imp).getLuts();
			if (firstTime && ndigits==4) {
				ndigits = 3;
				firstTime = false;
			}
		}
		if (arg!=null && arg.length()>0)
			directory = arg;
		else {		
			if (!showDialog(imp))
				return;
		}
		int number = 0;
		if (ndigits<1) ndigits = 1;
		if (ndigits>8) ndigits = 8;
		int maxImages = (int)Math.pow(10,ndigits);
		if (stackSize>maxImages && !useLabels && !hyperstack) {
			IJ.error("Stack Writer", "More than " + ndigits
				+" digits are required to generate \nunique file names for "+stackSize+" images.");
			return;			
		}
		if (format.equals("fits") && !FileSaver.okForFits(imp))
			return;			
		if (format.equals("text"))
			format = "text image";
		String extension = "." + format;
		if (format.equals("tiff"))
			extension = ".tif";
		else if (format.equals("text image"))
			extension = ".txt";					
		Overlay overlay = imp.getOverlay();
		boolean isOverlay = overlay!=null && !imp.getHideOverlay();
		if (!(format.equals("jpeg")||format.equals("png")))
			isOverlay = false;
		ImageStack stack = imp.getStack();
		ImagePlus imp2 = new ImagePlus();
		imp2.setTitle(imp.getTitle());
		Calibration cal = imp.getCalibration();
		int nSlices = stack.size();
		String path,label=null;
		imp.lock();
		for (int i=1; i<=nSlices; i++) {
			IJ.showStatus("writing: "+i+"/"+nSlices);
			IJ.showProgress(i, nSlices);
			ImageProcessor ip = stack.getProcessor(i);
			if (isOverlay) {
				imp.setSliceWithoutUpdate(i);
				ip = imp.flatten().getProcessor();
			} else if (luts!=null && nChannels>1 && hyperstack) {
				ip.setColorModel(luts[lutIndex++]);
				if (lutIndex>=luts.length) lutIndex = 0;
			}
			imp2.setProcessor(null, ip);
			String label2 = stack.getSliceLabel(i);
			imp2.setProperty("Label", null);
			if (label2!=null) {
				if (label2.contains("\n"))
					imp2.setProperty("Info", label2);
				else
					imp2.setProperty("Label", label2);;
			} else {
				Properties props = imp2.getProperties();
				if (props!=null) props.remove("Info");
			}
			imp2.setCalibration(cal);
			String digits = getDigits(number++);
			if (useLabels) {
				label = stack.getShortSliceLabel(i, 111);
				if (label!=null && label.equals("")) label = null;
				if (label!=null) label = label.replaceAll("/","-");
			}
			if (label==null)
				path = directory+name+digits+extension;
			else
				path = directory+label+extension;
			if (i==1) {
				File f = new File(path);
				if (f.exists()) {
					if (!IJ.isMacro() && !IJ.showMessageWithCancel("Overwrite files?",
						"One or more files will be overwritten if you click \"OK\".\n \n"+path)) {
						imp.unlock();
						IJ.showStatus("");
						IJ.showProgress(1.0);
						return;
					}
				}
			}
			if (Recorder.record)
				Recorder.disablePathRecording();
			imp2.setOverlay(null);
			if (overlay!=null && format.equals("tiff")) {
				Overlay overlay2 = overlay.duplicate();
				overlay2.crop(i, i);
				if (overlay2.size()>0) {
					for (int j=0; j<overlay2.size(); j++) {
						Roi roi = overlay2.get(j);
						int pos = roi.getPosition();
						if (pos==1)
							roi.setPosition(i);
					}
					imp2.setOverlay(overlay2);
				}
			}
			IJ.saveAs(imp2, format, path);
		}
		imp.unlock();
		if (isOverlay) imp.setSlice(1);
		IJ.showStatus("");
	}
	
	private boolean showDialog(ImagePlus imp) {
		String options = Macro.getOptions();
		if (options!=null && options.contains("save="))  //macro
			Macro.setOptions(options.replaceAll("save=", "dir="));
		directory = Prefs.get(DIR_KEY, IJ.getDir("downloads")+"stack2/");
		GenericDialog gd = new GenericDialog("Save Image Sequence");
		if (!IJ.isMacro())
			fileType = staticFileType;
		gd.setInsets(5, 0, 0);
		gd.addDirectoryField("Dir:", directory);		
		gd.setInsets(2, 110, 5);
		gd.addMessage("drag and drop target", IJ.font10, Color.darkGray);
		gd.addChoice("Format:", choices, fileType);
		gd.addStringField("Name:", name, 12);
		if (!hyperstack)
			gd.addNumericField("Start At:", startAt, 0);
		gd.addNumericField("Digits (1-8):", ndigits, 0);
		if (!hyperstack)
			gd.addCheckbox("Use slice labels as file names", useLabels);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		directory = gd.getNextString();
		directory = IJ.addSeparator(directory);
		Prefs.set(DIR_KEY, directory);
		gd.setSmartRecording(true);
		fileType = gd.getNextChoice();
		format = fileType.toLowerCase(Locale.US);
		if (!IJ.isMacro())
			staticFileType = fileType;
		String name2 = gd.getNextString();
		boolean nameChanged =  !name2.equals(name);
		name = name2;
		if (!hyperstack)
			startAt = (int)gd.getNextNumber();
		if (startAt<0) startAt = 0;
		int ndigits2 = (int)gd.getNextNumber();
		boolean ndigitsChanged = ndigits2!=ndigits;
		ndigits = ndigits2;
		if (!hyperstack)
			useLabels = gd.getNextBoolean();
		else
			useLabels = false;
		if (Recorder.record) {
			String options2 = "format="+format;
			if (nameChanged)
				options2 += " name="+name;
			if (ndigitsChanged)
				options2 += " digits="+ndigits;
			if (useLabels)
				options2 += " use";			
			String dir = Recorder.fixPath(directory);
   			Recorder.recordCall("StackWriter.save(imp, \""+dir+"\", \""+options2+"\");");
		}
		return true;
	}
	
	String getDigits(int n) {
		if (hyperstack) {
			int c = (n%dim[2])+1;
			int z = ((n/dim[2])%dim[3])+1;
			int t = ((n/(dim[2]*dim[3]))%dim[4])+1;
			String cs="", zs="", ts="";
			if (dim[2]>1) {
				cs = "00000000"+c;
				cs = "_c"+cs.substring(cs.length()-ndigits);
			}
			if (dim[3]>1) {
				zs = "00000000"+z;
				zs = "_z"+zs.substring(zs.length()-ndigits);
			}
			if (dim[4]>1) {
				ts = "00000000"+t;
				ts = "_t"+ts.substring(ts.length()-ndigits);
			}
			return ts+zs+cs;
		} else {
			String digits = "00000000"+(startAt+n);
			return digits.substring(digits.length()-ndigits);
		}
	}
	
}

