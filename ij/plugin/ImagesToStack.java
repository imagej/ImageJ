package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.Calibration;
import ij.macro.Interpreter;
import ij.io.FileInfo;


/** Implements the Image/Stacks/Images to Stack" command. */
public class ImagesToStack implements PlugIn {
	private static final int rgb = 33;
	private static final int COPY_CENTER=0, COPY_TOP_LEFT=1, SCALE_SMALL=2, SCALE_LARGE=3;
	private static final String[] methods = {"Copy (center)", "Copy (top-left)", "Scale (smallest)", "Scale (largest)"};
	private static int method = COPY_CENTER;
	private static boolean bicubic;
	private static boolean keep;
	private String filter;

	public void run(String arg) {
    	convertImagesToStack();
	}

	public void convertImagesToStack() {
		boolean scale = false;
		int stackType = 8;
		int[] wList = WindowManager.getIDList();
		if (wList==null) {
			IJ.error("No images are open.");
			return;
		}

		int count = 0;
		ImagePlus[] image = new ImagePlus[wList.length];
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp = WindowManager.getImage(wList[i]);
			if (imp.getStackSize()==1)
				image[count++] = imp;
		}		
		if (count<2) {
			IJ.error("There must be at least two open images.");
			return;
		}

		int width = image[0].getWidth();
		int height = image[0].getHeight();
		int maxWidth = width;
		int maxHeight = height;
		int minWidth = width;
		int minHeight = height;
		int minSize = Integer.MAX_VALUE;
		int maxSize = 0;
		Calibration cal2 = image[0].getCalibration();
		for (int i=0; i<count; i++) {
			int type = image[i].getBitDepth();
			if (type==24) type = rgb;
			if (type>stackType) stackType = type;
			int w=image[i].getWidth(), h=image[i].getHeight();
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
			Calibration cal = image[i].getCalibration();
			if (!image[i].getCalibration().equals(cal2))
				cal2 = null;
		}
		boolean sizesDiffer = width!=minWidth||height!=minHeight;
		boolean showDialog = IJ.shiftKeyDown() || IJ.altKeyDown();
		if (sizesDiffer||showDialog) {
			String msg = "The "+count+" images differ in size (smallest="+minWidth+"x"+minHeight
			+",\nlargest="+maxWidth+"x"+maxHeight+"). They will be converted\nto a stack using the specified method.";
			GenericDialog gd = new GenericDialog("Images to Stack");
			if (sizesDiffer) {
				gd.setInsets(0,0,5);
				gd.addMessage(msg);
			}
			gd.addChoice("Method:", methods, methods[method]);
			gd.addStringField("Title Contains:", "", 12);
			gd.addCheckbox("Bicubic Interpolation", bicubic);
			gd.addCheckbox("Keep Source Images", keep);
			gd.showDialog();
			if (gd.wasCanceled()) return;
			method = gd.getNextChoiceIndex();
			filter = gd.getNextString();
			bicubic = gd.getNextBoolean();
			keep = gd.getNextBoolean();
		} else
			keep = false;
		if (method==SCALE_SMALL) {
			width = minWidth;
			height = minHeight;
		} else if (method==SCALE_LARGE) {
			width = maxWidth;
			height = maxHeight;
		}
		
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		ImageStack stack = new ImageStack(width, height);
		FileInfo fi = image[0].getOriginalFileInfo();
		if (fi!=null && fi.directory==null) fi = null;
		if (filter!=null && (filter.equals("") || filter.equals("*"))) filter = null;
		for (int i=0; i<count; i++) {
			if (filter!=null && image[i].getTitle().indexOf(filter)==-1)
				continue;
			ImageProcessor ip = image[i].getProcessor();
			if (ip.getMin()<min) min = ip.getMin();
			if (ip.getMax()>max) max = ip.getMax();
            String label = image[i].getTitle();
            String info = (String)image[i].getProperty("Info");
            if (info!=null) label += "\n" + info;
            if (fi!=null) {
				FileInfo fi2 = image[i].getOriginalFileInfo();
				if (fi2!=null && !fi.directory.equals(fi2.directory))
					fi = null;
            }
            switch (stackType) {
            	case 16: ip = ip.convertToShort(false); break;
            	case 32: ip = ip.convertToFloat(); break;
            	case rgb: ip = ip.convertToRGB(); break;
            	default: break;
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
            } else if (keep)
            	ip = ip.duplicate();
            stack.addSlice(label, ip);
            if (!keep) {
				image[i].changes = false;
				image[i].close();
			}
		}
		if (stack.getSize()==0) return;
		ImagePlus imp = new ImagePlus("Stack", stack);
		if (stackType==16 || stackType==32)
			imp.getProcessor().setMinAndMax(min, max);
		if (cal2!=null)
			imp.setCalibration(cal2);
		if (fi!=null) {
			fi.fileName = "";
			fi.nImages = imp.getStackSize();
			imp.setFileInfo(fi);
		}
		imp.show();
	}
	
}

