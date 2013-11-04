package ij.plugin;
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.util.Tools;
import ij.plugin.frame.Recorder;
import ij.measure.Calibration;

/** This plugin implements the Image/Duplicate command.
<pre>
   // test script
   img1 = IJ.getImage();
   img2 = new Duplicator().run(img1);
   //img2 = new Duplicator().run(img1,1,10);
   img2.show();
</pre>
*/
public class Duplicator implements PlugIn, TextListener {
	private static boolean duplicateStack;
	private boolean duplicateSubstack;
	private int first, last;
	private Checkbox checkbox;
	private TextField rangeField;
	private TextField[] rangeFields;
	private int firstC, lastC, firstZ, lastZ, firstT, lastT;
	private boolean isCommand;

	public void run(String arg) {
		isCommand = true;
		ImagePlus imp = IJ.getImage();
		int stackSize = imp.getStackSize();
		String title = imp.getTitle();
		String newTitle = WindowManager.getUniqueName(title);
		if (!IJ.altKeyDown()||stackSize>1) {
			if (imp.isHyperStack() || imp.isComposite()) {
				duplicateHyperstack(imp, newTitle);
				return;
			} else
				newTitle = showDialog(imp, "Duplicate...", "Title: ", newTitle);
		}
		if (newTitle==null)
			return;
		ImagePlus imp2;
		Roi roi = imp.getRoi();
		if (duplicateSubstack && (first>1||last<stackSize))
			imp2 = run(imp, first, last);
		else if (duplicateStack || imp.getStackSize()==1)
			imp2 = run(imp);
		else
			imp2 = duplicateImage(imp);
		if (imp2.getWidth()==0 || imp2.getHeight()==0) {
			IJ.error("Duplicator", "Selection is outside the image");
			return;
		}
		Calibration cal = imp2.getCalibration();
		if (roi!=null && (cal.xOrigin!=0.0||cal.yOrigin!=0.0)) {
			cal.xOrigin -= roi.getBounds().x;
			cal.yOrigin -= roi.getBounds().y;
		}
		imp2.setTitle(newTitle);
		imp2.show();
		if (stackSize>1 && imp2.getStackSize()==stackSize)
			imp2.setSlice(imp.getCurrentSlice());
		if (roi!=null && roi.isArea() && roi.getType()!=Roi.RECTANGLE && roi.getBounds().width==imp2.getWidth())
			imp2.restoreRoi();
	}
                
	/** Returns a copy of the image, stack or hyperstack contained in the specified ImagePlus. */
	public ImagePlus run(ImagePlus imp) {
   		if (Recorder.record&&isCommand)
   			Recorder.recordCall("imp = new Duplicator().run(imp);");
		if (imp.getStackSize()==1)
			return duplicateImage(imp);
		Rectangle rect = null;
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isArea())
			rect = roi.getBounds();
		ImageStack stack = imp.getStack();
		ImageStack stack2 = null;
		for (int i=1; i<=stack.getSize(); i++) {
			ImageProcessor ip2 = stack.getProcessor(i);
			ip2.setRoi(rect);
			ip2 = ip2.crop();
			if (stack2==null)
				stack2 = new ImageStack(ip2.getWidth(), ip2.getHeight(), imp.getProcessor().getColorModel());
			stack2.addSlice(stack.getSliceLabel(i), ip2);
		}
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setStack("DUP_"+imp.getTitle(), stack2);
		String info = (String)imp.getProperty("Info");
		if (info!=null)
			imp2.setProperty("Info", info);
		int[] dim = imp.getDimensions();
		imp2.setDimensions(dim[2], dim[3], dim[4]);
		if (imp.isComposite()) {
			imp2 = new CompositeImage(imp2, 0);
			((CompositeImage)imp2).copyLuts(imp);
		}
		if (imp.isHyperStack())
			imp2.setOpenAsHyperStack(true);
		Overlay overlay = imp.getOverlay();
		if (overlay!=null && !imp.getHideOverlay()) {
			if (rect==null)
				rect = new Rectangle(0,0,imp.getWidth(),imp.getHeight());
			imp2.setOverlay(overlay.crop(rect));
		}
		return imp2;
	}
	
	ImagePlus duplicateImage(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor ip2 = ip.crop();
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setProcessor("DUP_"+imp.getTitle(), ip2);
		String info = (String)imp.getProperty("Info");
		if (info!=null)
			imp2.setProperty("Info", info);
		if (imp.getStackSize()>1) {
			ImageStack stack = imp.getStack();
			String label = stack.getSliceLabel(imp.getCurrentSlice());
			if (label!=null && label.indexOf('\n')>0)
				imp2.setProperty("Info", label);
			if (imp.isComposite()) {
				LUT lut = ((CompositeImage)imp).getChannelLut();
				imp2.getProcessor().setColorModel(lut);
			}
		}
		Overlay overlay = imp.getOverlay();
		if (overlay!=null && !imp.getHideOverlay())
 			imp2.setOverlay(overlay.crop(ip.getRoi()));
		return imp2;
	}
	
	/** Returns a new stack containing a subrange of the specified stack. */
	public ImagePlus run(ImagePlus imp, int firstSlice, int lastSlice) {
		Rectangle rect = null;
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isArea())
			rect = roi.getBounds();
		ImageStack stack = imp.getStack();
		ImageStack stack2 = null;
		for (int i=firstSlice; i<=lastSlice; i++) {
			ImageProcessor ip2 = stack.getProcessor(i);
			ip2.setRoi(rect);
			ip2 = ip2.crop();
			if (stack2==null)
				stack2 = new ImageStack(ip2.getWidth(), ip2.getHeight(), imp.getProcessor().getColorModel());
			stack2.addSlice(stack.getSliceLabel(i), ip2);
		}
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setStack("DUP_"+imp.getTitle(), stack2);
		String info = (String)imp.getProperty("Info");
		if (info!=null)
			imp2.setProperty("Info", info);
		int size = stack2.getSize();
		boolean tseries = imp.getNFrames()==imp.getStackSize();
		if (tseries)
			imp2.setDimensions(1, 1, size);
		else
			imp2.setDimensions(1, size, 1);
   		if (Recorder.record&&isCommand)
   			Recorder.recordCall("imp = new Duplicator().run(imp, "+firstSlice+", "+lastSlice+");");
		return imp2;
	}

	/** Returns a new hyperstack containing a possibly reduced version of the input image. */
	public ImagePlus run(ImagePlus imp, int firstC, int lastC, int firstZ, int lastZ, int firstT, int lastT) {
		Rectangle rect = null;
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isArea())
			rect = roi.getBounds();
		ImageStack stack = imp.getStack();
		ImageStack stack2 = null;
		for (int t=firstT; t<=lastT; t++) {
			for (int z=firstZ; z<=lastZ; z++) {
				for (int c=firstC; c<=lastC; c++) {
					int n1 = imp.getStackIndex(c, z, t);
					ImageProcessor ip = stack.getProcessor(n1);
					String label = stack.getSliceLabel(n1);
					ip.setRoi(rect);
					ip = ip.crop();
					if (stack2==null)
						stack2 = new ImageStack(ip.getWidth(), ip.getHeight(), null);
					stack2.addSlice(label, ip);
				}
			}
		}
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setStack("DUP_"+imp.getTitle(), stack2);
		imp2.setDimensions(lastC-firstC+1, lastZ-firstZ+1, lastT-firstT+1);
		if (imp.isComposite()) {
			int mode = ((CompositeImage)imp).getMode();
			if (lastC>firstC) {
				imp2 = new CompositeImage(imp2, mode);
				int i2 = 1;
				for (int i=firstC; i<=lastC; i++) {
					LUT lut = ((CompositeImage)imp).getChannelLut(i);
					((CompositeImage)imp2).setChannelLut(lut, i2++);
				}
			} else if (firstC==lastC) {
				LUT lut = ((CompositeImage)imp).getChannelLut(firstC);
				imp2.getProcessor().setColorModel(lut);
				imp2.setDisplayRange(lut.min, lut.max);
			}
        }
		imp2.setOpenAsHyperStack(true);
   		if (Recorder.record&&isCommand)
   			Recorder.recordCall("imp = new Duplicator().run(imp, "+firstC+", "+lastC+", "+firstZ+", "+lastZ+", "+firstT+", "+lastT+");");
		return imp2;
	}

	String showDialog(ImagePlus imp, String title, String prompt, String defaultString) {
		int stackSize = imp.getStackSize();
		duplicateSubstack = stackSize>1 && (stackSize==imp.getNSlices()||stackSize==imp.getNFrames());
		GenericDialog gd = new GenericDialog(title);
		gd.addStringField(prompt, defaultString, duplicateSubstack?15:20);
		if (stackSize>1) {
			String msg = duplicateSubstack?"Duplicate stack":"Duplicate entire stack";
			gd.addCheckbox(msg, duplicateStack||imp.isComposite());
			if (duplicateSubstack) {
				gd.setInsets(2, 30, 3);
				gd.addStringField("Range:", "1-"+stackSize);
				Vector v = gd.getStringFields();
				rangeField = (TextField)v.elementAt(1);
				rangeField.addTextListener(this);
				checkbox = (Checkbox)(gd.getCheckboxes().elementAt(0));
			}
		} else
			duplicateStack = false;
		gd.showDialog();
		if (gd.wasCanceled())
			return null;
		title = gd.getNextString();
		if (stackSize>1) {
			duplicateStack = gd.getNextBoolean();
			if (duplicateStack && duplicateSubstack) {
				String[] range = Tools.split(gd.getNextString(), " -");
				double d1 = gd.parseDouble(range[0]);
				double d2 = range.length==2?gd.parseDouble(range[1]):Double.NaN;
				first = Double.isNaN(d1)?1:(int)d1;
				last = Double.isNaN(d2)?stackSize:(int)d2;
				if (first<1) first = 1;
				if (last>stackSize) last = stackSize;
				if (first>last) {first=1; last=stackSize;}
			} else {
				first = 1;
				last = stackSize;
			}
		}
		return title;
	}
	
	void duplicateHyperstack(ImagePlus imp, String newTitle) {
		newTitle = showHSDialog(imp, newTitle);
		if (newTitle==null)
			return;
		ImagePlus imp2 = null;
		Roi roi = imp.getRoi();
		if (!duplicateStack) {
			int nChannels = imp.getNChannels();
			boolean singleComposite = imp.isComposite() && nChannels==imp.getStackSize();
			if (!singleComposite && nChannels>1 && imp.isComposite() && ((CompositeImage)imp).getMode()==CompositeImage.COMPOSITE) {
				firstC = 1;
				lastC = nChannels;
			} else
				firstC = lastC = imp.getChannel();
			firstZ = lastZ = imp.getSlice();
			firstT = lastT = imp.getFrame();
		}
		imp2 = run(imp, firstC, lastC, firstZ, lastZ, firstT, lastT);
		if (imp2==null) return;
		Calibration cal = imp2.getCalibration();
		if (roi!=null && (cal.xOrigin!=0.0||cal.yOrigin!=0.0)) {
			cal.xOrigin -= roi.getBounds().x;
			cal.yOrigin -= roi.getBounds().y;
		}
		imp2.setTitle(newTitle);
		Overlay overlay = imp.getOverlay();
		if (overlay!=null && !imp.getHideOverlay()) {
			if (roi!=null)
				imp2.setOverlay(overlay.crop(roi.getBounds()));
			else
				imp2.setOverlay(overlay.duplicate());
		}
		if (imp2.getWidth()==0 || imp2.getHeight()==0) {
			IJ.error("Duplicator", "Selection is outside the image");
			return;
		}
		imp2.show();
		if (roi!=null && roi.isArea() && roi.getType()!=Roi.RECTANGLE && roi.getBounds().width==imp2.getWidth())
			imp2.restoreRoi();
		imp2.setPosition(imp.getC(), imp.getZ(), imp.getT());
		if (IJ.isMacro()&&imp2.getWindow()!=null)
			IJ.wait(50);
	}

	String showHSDialog(ImagePlus imp, String newTitle) {
		int nChannels = imp.getNChannels();
		int nSlices = imp.getNSlices();
		int nFrames = imp.getNFrames();
		boolean composite = imp.isComposite() && nChannels==imp.getStackSize();
		GenericDialog gd = new GenericDialog("Duplicate");
		gd.addStringField("Title:", newTitle, 15);
		gd.setInsets(12, 20, 8);
		gd.addCheckbox("Duplicate hyperstack", duplicateStack||composite);
		int nRangeFields = 0;
		if (nChannels>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Channels (c):", "1-"+nChannels);
			nRangeFields++;
		}
		if (nSlices>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Slices (z):", "1-"+nSlices);
			nRangeFields++;
		}
		if (nFrames>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Frames (t):", "1-"+nFrames);
			nRangeFields++;
		}
		Vector v = gd.getStringFields();
		rangeFields = new TextField[3];
		for (int i=0; i<nRangeFields; i++) {
			rangeFields[i] = (TextField)v.elementAt(i+1);
			rangeFields[i].addTextListener(this);
		}
		checkbox = (Checkbox)(gd.getCheckboxes().elementAt(0));
		gd.showDialog();
		if (gd.wasCanceled())
			return null;
		newTitle = gd.getNextString();
		duplicateStack = gd.getNextBoolean();
		if (nChannels>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double c1 = gd.parseDouble(range[0]);
			double c2 = range.length==2?gd.parseDouble(range[1]):Double.NaN;
			firstC = Double.isNaN(c1)?1:(int)c1;
			lastC = Double.isNaN(c2)?firstC:(int)c2;
			if (firstC<1) firstC = 1;
			if (lastC>nChannels) lastC = nChannels;
			if (firstC>lastC) {firstC=1; lastC=nChannels;}
		} else
			firstC = lastC = 1;
		if (nSlices>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double z1 = gd.parseDouble(range[0]);
			double z2 = range.length==2?gd.parseDouble(range[1]):Double.NaN;
			firstZ = Double.isNaN(z1)?1:(int)z1;
			lastZ = Double.isNaN(z2)?firstZ:(int)z2;
			if (firstZ<1) firstZ = 1;
			if (lastZ>nSlices) lastZ = nSlices;
			if (firstZ>lastZ) {firstZ=1; lastZ=nSlices;}
		} else
			firstZ = lastZ = 1;
		if (nFrames>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double t1 = gd.parseDouble(range[0]);
			double t2 = range.length==2?gd.parseDouble(range[1]):Double.NaN;
			firstT= Double.isNaN(t1)?1:(int)t1;
			lastT = Double.isNaN(t2)?firstT:(int)t2;
			if (firstT<1) firstT = 1;
			if (lastT>nFrames) lastT = nFrames;
			if (firstT>lastT) {firstT=1; lastT=nFrames;}
		} else
			firstT = lastT = 1;
		return newTitle;
	}
	
	public static Overlay cropOverlay(Overlay overlay1, Rectangle imgBounds) {
		return overlay1.crop(imgBounds);
	}

	public void textValueChanged(TextEvent e) {
		checkbox.setState(true);
	}
	


}
