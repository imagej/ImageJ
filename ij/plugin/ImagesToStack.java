package ij.plugin;
import ij.plugin.frame.Recorder;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.Calibration;
import ij.macro.Interpreter;
import ij.io.FileInfo;
import java.awt.Color;


/** Implements the Image/Stacks/Images to Stack" command. */
public class ImagesToStack implements PlugIn {
	private static final int rgb = 33;
	private static final int COPY_CENTER=0, COPY_TOP_LEFT=1, SCALE_SMALL=2, SCALE_LARGE=3;
	private static final String[] methods = {"Copy (center)", "Copy (top-left)", "Scale (smallest)", "Scale (largest)"};
	private static int staticMethod = COPY_CENTER;
	private static boolean staticBicubic;
	private static boolean staticKeep;
	private static boolean staticTitlesAsLabels = true;
	private int method = COPY_CENTER;
	private boolean bicubic;
	private boolean keep;
	private boolean titlesAsLabels = true;
	private String filter;
	private int width, height;
	private int maxWidth, maxHeight;
	private int minWidth, minHeight;
	private int minSize, maxSize;
	private boolean allInvertedLuts;
	private Calibration cal2;
	private int stackType;
	private ImagePlus[] images;
	private String name = "Stack";
	private Color fillColor;
	
	/** Converts the images in 'images' to a stack, using the 
		default settings ("copy center" and "titles as labels"). */
	public static ImagePlus run(ImagePlus[] images) {
		ImagesToStack itos = new ImagesToStack();
		int count = itos.findMinMaxSize(images, images.length);
		return itos.convert(images, count);
	}

	public void run(String arg) {
		convertImagesToStack();
	}

	public void convertImagesToStack() {
		boolean scale = false;
		int[] wList = WindowManager.getIDList();
		if (wList==null) {
			IJ.error("No images are open.");
			return;
		}

		int count = 0;
		int stackCount = 0;
		images = new ImagePlus[wList.length];
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp = WindowManager.getImage(wList[i]);
			if (imp.getStackSize()==1)
				images[count++] = imp;
			else
				stackCount++;
		}		
		if (count<2) {
			String msg = "";
			if (stackCount>1)
				msg = "\n \nUse the Image>Stacks>Tools>Concatenate\ncommand to combine stacks.";
			IJ.error("Images to Stack", "There must be at least two open 2D images."+msg);
			return;
		}

		filter = null;
		count = findMinMaxSize(images, count);
		boolean sizesDiffer = width!=minWidth||height!=minHeight;
		boolean showDialog = true;
		String macroOptions = Macro.getOptions();
		if (IJ.macroRunning() && macroOptions==null) {
			if (sizesDiffer) {
				IJ.error("Images are not all the same size");
				return;
			} 
			showDialog = false;
		}
		if (showDialog) {
			GenericDialog gd = new GenericDialog("Images to Stack");
			if (sizesDiffer) {
				String msg = "The "+count+" images differ in size (smallest="+minWidth+"x"+minHeight
				+",\nlargest="+maxWidth+"x"+maxHeight+"). They will be converted\nto a stack using the specified method.";
				gd.setInsets(0,0,5);
				gd.addMessage(msg);
				gd.addChoice("Method:", methods, methods[staticMethod]);
			}
			gd.setSmartRecording(true);
			gd.addStringField("Name:", name, 12);
			gd.addStringField("Title contains:", "", 12);
			gd.addStringField("Fill color:", "", 12);
			if (sizesDiffer)
				gd.addCheckbox("Bicubic interpolation", staticBicubic);
			gd.addCheckbox("Use titles as labels", staticTitlesAsLabels);
			gd.addCheckbox("Keep source images", staticKeep);
			gd.showDialog();
			if (gd.wasCanceled()) return;
			if (sizesDiffer)
				method = gd.getNextChoiceIndex();
			name = gd.getNextString();
			filter = gd.getNextString();
			String fillc = gd.getNextString();
			fillColor = Colors.decode(fillc, null);
			if (sizesDiffer)
				bicubic = gd.getNextBoolean();
			titlesAsLabels = gd.getNextBoolean();
			keep = gd.getNextBoolean();
			if (filter!=null && (filter.equals("") || filter.equals("*")))
				filter = null;
			if (filter!=null) {
				count = findMinMaxSize(images, count);
				if (count==0) {
					IJ.error("Images to Stack", "None of the images have a title containing \""+filter+"\"");
				}
			}
			if (!IJ.isMacro()) {
				staticMethod = method;
				staticBicubic = bicubic;
				staticKeep = keep;
				staticTitlesAsLabels = titlesAsLabels;
			}
			if (Recorder.record)
   				Recorder.recordCall("imp = ImagesToStack.run(arrayOfImages);");
		} else
			keep = false;
		if (method==SCALE_SMALL) {
			width = minWidth;
			height = minHeight;
		} else if (method==SCALE_LARGE) {
			width = maxWidth;
			height = maxHeight;
		}
		ImagePlus stack = convert(images, count);
		if (stack!=null)
			stack.show();
	}
	
	private ImagePlus convert(ImagePlus[] images, int count) {		
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		ImageStack stack = new ImageStack(width, height);
		FileInfo fi = images[0].getOriginalFileInfo();
		if (fi!=null && fi.directory==null) fi = null;
		Overlay overlay = new Overlay();
		for (int i=0; i<count; i++) {
			ImageProcessor ip = images[i].getProcessor();
			boolean invertedLut = ip.isInvertedLut();
			if (ip.getMin()<min) min = ip.getMin();
			if (ip.getMax()>max) max = ip.getMax();
			String label = titlesAsLabels?images[i].getTitle():null;
			if (label==null)
				label = (String)images[i].getProperty("Label");
			if (label!=null) {
				String info = (String)images[i].getProperty("Info");
				if (info!=null) label += "\n" + info;
			}
			if (fi!=null) {
				FileInfo fi2 = images[i].getOriginalFileInfo();
				if (fi2!=null && !fi.directory.equals(fi2.directory))
					fi = null;
			}
			switch (stackType) {
				case 16: ip = ip.convertToShort(false); break;
				case 32: ip = ip.convertToFloat(); break;
				case rgb: ip = ip.convertToRGB(); break;
				default: break;
			}
			if (invertedLut && !allInvertedLuts) {
				if (keep)
					ip = ip.duplicate();
				ip.invert();
			}
			if (ip.getWidth()!=width||ip.getHeight()!=height) {
				switch (method) {
					case COPY_TOP_LEFT: case COPY_CENTER:
						ImageProcessor ip2 = null;
						switch (stackType) {
							case 8: ip2 = new ByteProcessor(width, height); break;
							case 16: ip2 = new ShortProcessor(width, height); break;
							case 32: ip2 = new FloatProcessor(width, height); break;
							case rgb: ip2 = new ColorProcessor(width, height); break;
						}
						if (fillColor!=null) {
							ip2.setColor(fillColor);
							ip2.fill();
						}							
						int xoff=0, yoff=0;
						if (method==COPY_CENTER) {
							xoff = (width-ip.getWidth())/2;
							yoff = (height-ip.getHeight())/2;
						}
						ip2.insert(ip, xoff, yoff);
						ip = ip2;
						break;
					case SCALE_SMALL: case SCALE_LARGE:
						ip.setInterpolationMethod((bicubic?ImageProcessor.BICUBIC:ImageProcessor.BILINEAR));
						ip.resetRoi();
						ip = ip.resize(width, height);
						break;
				}
			} else {
				if (keep)
					ip = ip.duplicate();
				Overlay overlay2 = images[i].getOverlay();
				if (overlay2!=null) {
					for (int j=0; j<overlay2.size(); j++) {
						Roi roi = overlay2.get(j);
						roi.setPosition(i+1);
						overlay.add((Roi)roi.clone());
					}
				}
			}
			stack.addSlice(label, ip);
			if (i==0 && invertedLut && !allInvertedLuts)
				stack.setColorModel(null);
			if (!keep) {
				images[i].changes = false;
				images[i].close();
			}
		}
		if (stack.size()==0)
			return null;
		ImagePlus imp = new ImagePlus(name, stack);
		if (stackType==16 || stackType==32)
			imp.getProcessor().setMinAndMax(min, max);
		if (cal2!=null)
			imp.setCalibration(cal2);
		if (fi!=null) {
			fi.fileName = "";
			fi.nImages = imp.getStackSize();
			imp.setFileInfo(fi);
		}
		if (overlay.size()>0)
			imp.setOverlay(overlay);
		return imp;
	}
	
	private int findMinMaxSize(ImagePlus[] images, int count) {
		int index = 0;
		stackType = 8;
		width = 0;
		height = 0;
		cal2 = images[0].getCalibration();
		maxWidth = 0;
		maxHeight = 0;
		minWidth = Integer.MAX_VALUE;
		minHeight = Integer.MAX_VALUE;
		minSize = Integer.MAX_VALUE;
		allInvertedLuts = true;
		maxSize = 0;
		for (int i=0; i<count; i++) {
			if (exclude(images[i].getTitle())) continue;
			if (images[i].getType()==ImagePlus.COLOR_256)
				stackType = rgb;
			if (!images[i].getProcessor().isInvertedLut())
				allInvertedLuts = false;
			int type = images[i].getBitDepth();
			if (type==24) type = rgb;
			if (type>stackType) stackType = type;
			int w=images[i].getWidth(), h=images[i].getHeight();
			if (w>width) width = w;
			if (h>height) height = h;
			int size = w*h;
			if (size<minSize) {
				minSize = size;
				minWidth = w;
				minHeight = h;
			}
			if (size>maxSize) {
				maxSize = size;
				maxWidth = w;
				maxHeight = h;
			}
			Calibration cal = images[i].getCalibration();
			if (!images[i].getCalibration().equals(cal2))
				cal2 = null;
			images[index++] = images[i];
		}
		return index;
	}

	final boolean exclude(String title) {
		return filter!=null && title!=null && title.indexOf(filter)==-1;
	}
	
}

