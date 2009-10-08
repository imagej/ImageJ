package ij.plugin.filter;
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.macro.Interpreter;
import ij.util.Tools;

/** This plugin implements ImageJ's Image/Duplicate command. */
public class Duplicator implements PlugInFilter, TextListener {
	ImagePlus imp;
	static boolean duplicateStack;
	boolean duplicateSubstack;
	int first, last;
	Checkbox checkbox;
	TextField rangeField;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		IJ.register(Duplicater.class);
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		duplicate(imp);
	}

	public void duplicate(ImagePlus imp) {
		int stackSize = imp.getStackSize();
		String title = imp.getTitle();
		String newTitle = WindowManager.getUniqueName(title);
		if (!IJ.altKeyDown()||stackSize>1)
			newTitle = getString("Duplicate...", "Title: ", newTitle);
		if (newTitle==null)
			return;
		ImagePlus imp2;
		Roi roi = imp.getRoi();
		if (duplicateSubstack && (first>1||last<stackSize))
			imp2 = duplicateSubstack(imp, newTitle, first, last);
		else if (duplicateStack)
			imp2 = duplicateStack(imp, newTitle);
		else {
			ImageProcessor ip2 = imp.getProcessor().crop();
			imp2 = imp.createImagePlus();
			imp2.setProcessor(newTitle, ip2);
			String info = (String)imp.getProperty("Info");
			if (info!=null)
				imp2.setProperty("Info", info);
			if (stackSize>1) {
				ImageStack stack = imp.getStack();
				String label = stack.getSliceLabel(imp.getCurrentSlice());
				if (label!=null && label.indexOf('\n')>0)
					imp2.setProperty("Info", label);
				if (imp.isComposite()) {
					LUT lut = ((CompositeImage)imp).getChannelLut();
					imp2.getProcessor().setColorModel(lut);
				}
			}
		}
		imp2.show();
		if (roi!=null && roi.isArea() && roi.getType()!=Roi.RECTANGLE)
			imp2.restoreRoi();
	}
                
	public ImagePlus duplicateStack(ImagePlus imp, String newTitle) {
		Rectangle rect = null;
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isArea())
			rect = roi.getBounds();
		int width = rect!=null?rect.width:imp.getWidth();
		int height = rect!=null?rect.height:imp.getHeight();
		ImageStack stack = imp.getStack();
		ImageStack stack2 = new ImageStack(width, height, imp.getProcessor().getColorModel());
		for (int i=1; i<=stack.getSize(); i++) {
			ImageProcessor ip2 = stack.getProcessor(i);
			ip2.setRoi(rect);
			ip2 = ip2.crop();
			stack2.addSlice(stack.getSliceLabel(i), ip2);
		}
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setStack(newTitle, stack2);
		int[] dim = imp.getDimensions();
		imp2.setDimensions(dim[2], dim[3], dim[4]);
		if (imp.isComposite()) {
			imp2 = new CompositeImage(imp2, 0);
			((CompositeImage)imp2).copyLuts(imp);
		}
		if (imp.isHyperStack())
			imp2.setOpenAsHyperStack(true);
		return imp2;
	}
	
	public ImagePlus duplicateSubstack(ImagePlus imp, String newTitle, int first, int last) {
		Rectangle rect = null;
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isArea())
			rect = roi.getBounds();
		int width = rect!=null?rect.width:imp.getWidth();
		int height = rect!=null?rect.height:imp.getHeight();
		ImageStack stack = imp.getStack();
		ImageStack stack2 = new ImageStack(width, height, imp.getProcessor().getColorModel());
		for (int i=first; i<=last; i++) {
			ImageProcessor ip2 = stack.getProcessor(i);
			ip2.setRoi(rect);
			ip2 = ip2.crop();
			stack2.addSlice(stack.getSliceLabel(i), ip2);
		}
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setStack(newTitle, stack2);
		int size = stack2.getSize();
		boolean tseries = imp.getNFrames()==imp.getStackSize();
		if (tseries)
			imp2.setDimensions(1, 1, size);
		else
			imp2.setDimensions(1, size, 1);
		return imp2;
	}

	String getString(String title, String prompt, String defaultString) {
		Frame win = imp.getWindow();
		int stackSize = imp.getStackSize();
		if (win==null)
			win = IJ.getInstance();
		duplicateSubstack = stackSize>1 && (stackSize==imp.getNSlices()||stackSize==imp.getNFrames());
		GenericDialog gd = new GenericDialog(title, win);
		gd.addStringField(prompt, defaultString, duplicateSubstack?15:20);
		if (stackSize>1) {
			String msg = duplicateSubstack?"Duplicate Stack":"Duplicate Entire Stack";
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
				double d1 = Tools.parseDouble(range[0]);
				double d2 = range.length==2?Tools.parseDouble(range[1]):Double.NaN;
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
	
	public void textValueChanged(TextEvent e) {
		checkbox.setState(true);
	}

	
}