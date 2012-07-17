package ij.plugin;
import java.awt.*;
import java.io.*;
import java.awt.event.*;
import java.awt.image.ColorModel;
import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.Calibration;
import ij.util.*;

/** Implements the File/Import/Image Sequence command, which
	opens a folder of images as a stack. */
public class FolderOpener implements PlugIn {

	private static String[] excludedTypes = {".txt", ".lut", ".roi", ".pty", ".hdr", ".java", ".ijm", ".py", ".js", ".bsh", ".xml"};
	private static boolean staticSortFileNames = true;
	private static boolean staticOpenAsVirtualStack;
	private boolean convertToRGB;
	private boolean sortFileNames = true;
	private boolean openAsVirtualStack;
	private double scale = 100.0;
	private int n, start, increment;
	private String filter;
	private boolean isRegex;
	private FileInfo fi;
	private String info1;
	private ImagePlus image;
	private boolean saveImage;
	
	/** Opens the images in the specified directory as a stack. Displays
		directory chooser and options dialogs if the argument is null. */
	public static ImagePlus open(String path) {
		FolderOpener fo = new FolderOpener();
		fo.saveImage = true;
		fo.run(path);
		return fo.image;
	}

	/** Opens the images in the specified directory as a stack. Displays
		directory chooser and options dialogs if the argument is null. */
	public ImagePlus openFolder(String path) {
		saveImage = true;
		run(path);
		return image;
	}

	public void run(String arg) {
		String directory = null;
		if (arg!=null && !arg.equals("")) {
			directory = arg;
		} else {
			if (!IJ.macroRunning()) {
				sortFileNames = staticSortFileNames;
				openAsVirtualStack = staticOpenAsVirtualStack;
			}
			arg = null;
			String title = "Open Image Sequence...";
			String macroOptions = Macro.getOptions();
			if (macroOptions!=null) {
				directory = Macro.getValue(macroOptions, title, null);
				if (directory!=null) {
					directory = OpenDialog.lookupPathVariable(directory);
					File f = new File(directory);
					if (!f.isDirectory() && (f.exists()||directory.lastIndexOf(".")>directory.length()-5))
						directory = f.getParent();
				}
			}
			if (directory==null) {
				if (Prefs.useFileChooser && !IJ.isMacOSX()) {
					OpenDialog od = new OpenDialog(title, arg);
					directory = od.getDirectory();
					String name = od.getFileName();
					if (name==null)
						return;
				} else
					directory = IJ.getDirectory(title);
			}
		}
		if (directory==null)
			return;
		String[] list = (new File(directory)).list();
		if (list==null)
			return;
		String title = directory;
		if (title.endsWith(File.separator) || title.endsWith("/"))
			title = title.substring(0, title.length()-1);
		int index = title.lastIndexOf(File.separatorChar);
		if (index!=-1) title = title.substring(index + 1);
		if (title.endsWith(":"))
			title = title.substring(0, title.length()-1);
		
		IJ.register(FolderOpener.class);
		list = trimFileList(list);
		if (list==null) return;
		if (IJ.debugMode) IJ.log("FolderOpener: "+directory+" ("+list.length+" files)");
		int width=0, height=0, stackSize=1, bitDepth=0;
		ImageStack stack = null;
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		Calibration cal = null;
		boolean allSameCalibration = true;
		IJ.resetEscape();		
		Overlay overlay = null;
		try {
			for (int i=0; i<list.length; i++) {
				IJ.redirectErrorMessages();
				Opener opener = new Opener();
				opener.setSilentMode(true);
				ImagePlus imp = opener.openImage(directory, list[i]);
				if (imp!=null) {
					width = imp.getWidth();
					height = imp.getHeight();
					bitDepth = imp.getBitDepth();
					fi = imp.getOriginalFileInfo();
					if (arg==null) {
						if (!showDialog(imp, list))
							return;
					} else {
						n = list.length;
						start = 1;
						increment = 1;
					}
					break;
				}
			}
			if (width==0) {
				IJ.error("Import Sequence", "This folder does not appear to contain\n"
				+ "any TIFF, JPEG, BMP, DICOM, GIF, FITS or PGM files.\n \n"
				+ "   \""+directory+"\"");
				return;
			}

			if (filter!=null && (filter.equals("") || filter.equals("*")))
				filter = null;
			if (filter!=null) {
				int filteredImages = 0;
  				for (int i=0; i<list.length; i++) {
					if (isRegex&&list[i].matches(filter))
						filteredImages++;
					else if (list[i].indexOf(filter)>=0)
						filteredImages++;
 					else
 						list[i] = null;
 				}
  				if (filteredImages==0) {
  					if (isRegex)
  						IJ.error("Import Sequence", "None of the file names match the regular expression.");
  					else
   						IJ.error("Import Sequence", "None of the "+list.length+" files contain\n the string '"+filter+"' in their name.");
 					return;
  				}
  				String[] list2 = new String[filteredImages];
  				int j = 0;
  				for (int i=0; i<list.length; i++) {
 					if (list[i]!=null)
 						list2[j++] = list[i];
 				}
  				list = list2;
  			}
			if (sortFileNames)
				list = StringSorter.sortNumerically(list);

			if (n<1)
				n = list.length;
			if (start<1 || start>list.length)
				start = 1;
			if (start+n-1>list.length)
				n = list.length-start+1;
			int count = 0;
			int counter = 0;
			ImagePlus imp = null;
			boolean firstMessage = true;
			for (int i=start-1; i<list.length; i++) {
				if ((counter++%increment)!=0)
					continue;
				Opener opener = new Opener();
				opener.setSilentMode(true);
				IJ.redirectErrorMessages();
				if (!openAsVirtualStack||stack==null)
					imp = opener.openImage(directory, list[i]);
				if (imp!=null && stack==null) {
					width = imp.getWidth();
					height = imp.getHeight();
					bitDepth = imp.getBitDepth();
					cal = imp.getCalibration();
					if (convertToRGB) bitDepth = 24;
					ColorModel cm = imp.getProcessor().getColorModel();
					if (openAsVirtualStack) {
						stack = new VirtualStack(width, height, cm, directory);
						((VirtualStack)stack).setBitDepth(bitDepth);
					} else if (scale<100.0)						
						stack = new ImageStack((int)(width*scale/100.0), (int)(height*scale/100.0), cm);
					else
						stack = new ImageStack(width, height, cm);
					info1 = (String)imp.getProperty("Info");
				}
				if (imp==null)
					continue;
				stackSize = imp.getStackSize();
				if (imp.getWidth()!=width || imp.getHeight()!=height) {
					IJ.log(list[i] + ": wrong size; "+width+"x"+height+" expected, "+imp.getWidth()+"x"+imp.getHeight()+" found");
					continue;
				}
				String label = imp.getTitle();
				if (stackSize==1) {
					String info = (String)imp.getProperty("Info");
					if (info!=null)
						label += "\n" + info;
				}
				if (imp.getCalibration().pixelWidth!=cal.pixelWidth)
					allSameCalibration = false;
				ImageStack inputStack = imp.getStack();
				Overlay overlay2 = imp.getOverlay();
				if (overlay2!=null && !openAsVirtualStack) {
					if (overlay==null)
						overlay = new Overlay();
					for (int j=0; j<overlay2.size(); j++) {
						Roi roi = overlay2.get(j);
						int position = roi.getPosition();
						if (position==0)
							roi.setPosition(count+1);
						overlay.add(roi);
					}
				}
				for (int slice=1; slice<=stackSize; slice++) {
					ImageProcessor ip = inputStack.getProcessor(slice);
					String label2 = label;
					if (stackSize>1) {
						String sliceLabel = inputStack.getSliceLabel(slice);
						if (sliceLabel!=null)
							label2=sliceLabel;
						else if (label2!=null && !label2.equals(""))
							label2 += ":"+slice;
					}
					int bitDepth2 = imp.getBitDepth();
					if (!openAsVirtualStack) {
						if (convertToRGB) {
							ip = ip.convertToRGB();
							bitDepth2 = 24;
						}
						if (bitDepth2!=bitDepth) {
							if (bitDepth==8) {
								ip = ip.convertToByte(true);
								bitDepth2 = 8;
							} else if (bitDepth==24) {
								ip = ip.convertToRGB();
								bitDepth2 = 24;
							}
						}
						if (bitDepth2!=bitDepth) {
							IJ.log(list[i] + ": wrong bit depth; "+bitDepth+" expected, "+bitDepth2+" found");
							break;
						}
					}
					if (slice==1) count++;
					IJ.showStatus(count+"/"+n);
					IJ.showProgress(count, n);
					if (scale<100.0)
						ip = ip.resize((int)(width*scale/100.0), (int)(height*scale/100.0));
					if (ip.getMin()<min) min = ip.getMin();
					if (ip.getMax()>max) max = ip.getMax();
					//if (depth>1) label2 = null;
					if (openAsVirtualStack) {
						if (slice==1)
							((VirtualStack)stack).addSlice(list[i]);
						else if (slice==2 && firstMessage) {
							IJ.log(list[i] + " ["+stackSize+"]: only the first slice will be used in virtual stack");
							firstMessage = false;
						}
					} else
						stack.addSlice(label2, ip);
				}
				if (count>=n)
					break;
				if (IJ.escapePressed())
					{IJ.beep(); break;}
				//System.gc();
			}
		} catch(OutOfMemoryError e) {
			IJ.outOfMemory("FolderOpener");
			if (stack!=null) stack.trim();
		}
		if (stack!=null && stack.getSize()>0) {
			ImagePlus imp2 = new ImagePlus(title, stack);
			if (imp2.getType()==ImagePlus.GRAY16 || imp2.getType()==ImagePlus.GRAY32)
				imp2.getProcessor().setMinAndMax(min, max);
			if (fi==null)
				fi = new FileInfo();
			fi.fileFormat = FileInfo.UNKNOWN;
			fi.fileName = "";
			fi.directory = directory;
			imp2.setFileInfo(fi); // saves FileInfo of the first image
			imp2.setOverlay(overlay);
			if (allSameCalibration) {
				// use calibration from first image
				if (scale!=100.0 && cal.scaled()) {
					cal.pixelWidth /= scale/100.0;
					cal.pixelHeight /= scale/100.0;
				}
				if (cal.pixelWidth!=1.0 && cal.pixelDepth==1.0)
					cal.pixelDepth = cal.pixelWidth;
				if (cal.pixelWidth<=0.0001 && cal.getUnit().equals("cm")) {
					cal.pixelWidth *= 10000.0;
					cal.pixelHeight *= 10000.0;
					cal.pixelDepth *= 10000.0;
					cal.setUnit("um");
				}
				imp2.setCalibration(cal);
			}
			if (info1!=null && info1.lastIndexOf("7FE0,0010")>0) {
				stack = DicomTools.sort(stack);
				imp2.setStack(stack);
				double voxelDepth = DicomTools.getVoxelDepth(stack);
				if (voxelDepth>0.0) {
					if (IJ.debugMode) IJ.log("DICOM voxel depth set to "+voxelDepth+" ("+cal.pixelDepth+")");
					cal.pixelDepth = voxelDepth;
					imp2.setCalibration(cal);
				}
			}
			if (imp2.getStackSize()==1 && info1!=null)
				imp2.setProperty("Info", info1);
			if (arg==null && !saveImage) {
				imp2.show();
				if (stack.isVirtual()) {
					overlay = stack.getProcessor(1).getOverlay();
					if (overlay!=null)
						imp2.setOverlay(overlay);
				}
			}
			if (saveImage)
				image = imp2;
		}
		IJ.showProgress(1.0);
	}
	
	boolean showDialog(ImagePlus imp, String[] list) {
		int fileCount = list.length;
		FolderOpenerDialog gd = new FolderOpenerDialog("Sequence Options", imp, list);
		gd.addNumericField("Number of images:", fileCount, 0);
		gd.addNumericField("Starting image:", 1, 0);
		gd.addNumericField("Increment:", 1, 0);
		gd.addNumericField("Scale images:", scale, 0, 4, "%");
		gd.addStringField("File name contains:", "", 10);
		gd.addStringField("or enter pattern:", "", 10);
		gd.addCheckbox("Convert_to_RGB", convertToRGB);
		gd.addCheckbox("Sort names numerically", sortFileNames);
		gd.addCheckbox("Use virtual stack", openAsVirtualStack);
		gd.addMessage("10000 x 10000 x 1000 (100.3MB)");
		gd.addHelp(IJ.URL+"/docs/menus/file.html#seq1");
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		n = (int)gd.getNextNumber();
		start = (int)gd.getNextNumber();
		increment = (int)gd.getNextNumber();
		if (increment<1)
			increment = 1;
		scale = gd.getNextNumber();
		if (scale<5.0) scale = 5.0;
		if (scale>100.0) scale = 100.0;
		filter = gd.getNextString();
		String regex = gd.getNextString();
		if (!regex.equals("")) {
			filter = regex;
			isRegex = true;
		}
		convertToRGB = gd.getNextBoolean();
		sortFileNames = gd.getNextBoolean();
		openAsVirtualStack = gd.getNextBoolean();
		if (openAsVirtualStack)
			scale = 100.0;
		if (!IJ.macroRunning()) {
			staticSortFileNames = sortFileNames;
			staticOpenAsVirtualStack = openAsVirtualStack;
		}
		return true;
	}

	/** Removes names that start with "." or end with ".db", ".txt", ".lut", "roi", ".pty", ".hdr", ".py", etc. */
	public String[] trimFileList(String[] rawlist) {
		int count = 0;
		for (int i=0; i< rawlist.length; i++) {
			String name = rawlist[i];
			if (name.startsWith(".")||name.equals("Thumbs.db")||excludedFileType(name))
				rawlist[i] = null;
			else
				count++;
		}
		if (count==0) return null;
		String[] list = rawlist;
		if (count<rawlist.length) {
			list = new String[count];
			int index = 0;
			for (int i=0; i< rawlist.length; i++) {
				if (rawlist[i]!=null)
					list[index++] = rawlist[i];
			}
		}
		return list;
	}
	
	/* Returns true if 'name' ends with ".txt", ".lut", ".roi", ".pty", ".hdr", ".java", ".ijm", ".py", ".js" or ".bsh. */
	public static boolean excludedFileType(String name) {
		if (name==null) return true;
		for (int i=0; i<excludedTypes.length; i++) {
			if (name.endsWith(excludedTypes[i]))
				return true;
		}
		return false;
	}
			
	public void openAsVirtualStack(boolean b) {
		openAsVirtualStack = b;
	}
	
	public void sortFileNames(boolean b) {
		sortFileNames = b;
	}
	
	/** Sorts file names containing numerical components.
	* @see ij.util.StringSorter#sortNumerically
	* @author Norbert Vischer
	*/
	public String[] sortFileList(String[] list) {
		return StringSorter.sortNumerically(list);
	}

} // FolderOpener

class FolderOpenerDialog extends GenericDialog {
	ImagePlus imp;
	int fileCount;
 	boolean eightBits, rgb;
 	String[] list;
 	boolean isRegex;

	public FolderOpenerDialog(String title, ImagePlus imp, String[] list) {
		super(title);
		this.imp = imp;
		this.list = list;
		this.fileCount = list.length;
	}

	protected void setup() {
 		eightBits = ((Checkbox)checkbox.elementAt(0)).getState();
 		rgb = ((Checkbox)checkbox.elementAt(1)).getState();
		setStackInfo();
	}
 	
	public void itemStateChanged(ItemEvent e) {
	}
	
	public void textValueChanged(TextEvent e) {
 		setStackInfo();
	}

	void setStackInfo() {
		int width = imp.getWidth();
		int height = imp.getHeight();
		int depth = imp.getStackSize();
		int bytesPerPixel = 1;
 		int n = getNumber(numberField.elementAt(0));
		int start = getNumber(numberField.elementAt(1));
		int inc = getNumber(numberField.elementAt(2));
		double scale = getNumber(numberField.elementAt(3));
		if (scale<5.0) scale = 5.0;
		if (scale>100.0) scale = 100.0;
		
		if (n<1) n = fileCount;
		if (start<1 || start>fileCount) start = 1;
		if (start+n-1>fileCount)
			n = fileCount-start+1;
		if (inc<1) inc = 1;
 		TextField tf = (TextField)stringField.elementAt(0);
 		String filter = tf.getText();
		tf = (TextField)stringField.elementAt(1);
  		String regex = tf.getText();
		if (!regex.equals("")) {
			filter = regex;
			isRegex = true;
		}
 		if (!filter.equals("") && !filter.equals("*")) {
 			int n2 = 0;
			for (int i=0; i<list.length; i++) {
				if (isRegex&&list[i].matches(filter))
					n2++;
				else if (list[i].indexOf(filter)>=0)
					n2++;
			}
			if (n2<n) n = n2;
 		}
		switch (imp.getType()) {
			case ImagePlus.GRAY16:
				bytesPerPixel=2;break;
			case ImagePlus.COLOR_RGB:
			case ImagePlus.GRAY32:
				bytesPerPixel=4; break;
		}
		if (eightBits)
			bytesPerPixel = 1;
		if (rgb)
			bytesPerPixel = 4;
		width = (int)(width*scale/100.0);
		height = (int)(height*scale/100.0);
		int n2 = ((fileCount-start+1)*depth)/inc;
		if (n2<0) n2 = 0;
		if (n2>n) n2 = n;
		double size = ((double)width*height*n2*bytesPerPixel)/(1024*1024);
 		((Label)theLabel).setText(width+" x "+height+" x "+n2+" ("+IJ.d2s(size,1)+"MB)");
	}

	public int getNumber(Object field) {
		TextField tf = (TextField)field;
		String theText = tf.getText();
		double value;
		Double d;
		try {d = new Double(theText);}
		catch (NumberFormatException e){
			d = null;
		}
		if (d!=null)
			return (int)d.doubleValue();
		else
			return 0;
      }

} // FolderOpenerDialog

