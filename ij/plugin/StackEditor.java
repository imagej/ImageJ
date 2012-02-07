package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.Calibration;
import ij.macro.Interpreter;
import ij.io.FileInfo;
import java.awt.*;
import java.util.ArrayList;

/** Implements the AddSlice, DeleteSlice and "Stack to Images" commands. */
public class StackEditor implements PlugIn {
	ImagePlus imp;
	int nSlices, width, height;

	public void run(String arg) {
		imp = IJ.getImage();
    	nSlices = imp.getStackSize();
    	width = imp.getWidth();
    	height = imp.getHeight();
    	
    	if (arg.equals("add"))
    		addSlice();
    	else if (arg.equals("delete"))
    		deleteSlice();
    	else if (arg.equals("toimages"))
    		convertStackToImages(imp);
	}

	void addSlice() {
		if (imp.isComposite() && nSlices==imp.getNChannels()) {
			addChannel();
			return;
		}
		if (imp.isDisplayedHyperStack()) return;
 		if (!imp.lock()) return;
		int id = 0;
		ImageStack stack = imp.getStack();
		if (stack.getSize()==1) {
			String label = stack.getSliceLabel(1);
			if (label!=null && label.indexOf("\n")!=-1)
				stack.setSliceLabel(null, 1);
			Object obj = imp.getProperty("Label");
			if (obj!=null && (obj instanceof String))
				stack.setSliceLabel((String)obj, 1);
			id = imp.getID();
		}
		ImageProcessor ip = imp.getProcessor();
		int n = imp.getCurrentSlice();
		if (IJ.altKeyDown()) n--; // insert in front of current slice
		stack.addSlice(null, ip.createProcessor(width, height), n);
		imp.setStack(null, stack);
		imp.setSlice(n+1);
		imp.unlock();
		if (id!=0) IJ.selectWindow(id); // prevents macros from failing
	}
	
	void deleteSlice() {
		if (nSlices<2)
			{IJ.error("\"Delete Slice\" requires a stack"); return;}
		if (imp.isComposite() && nSlices==imp.getNChannels()) {
			deleteChannel();
			return;
		}
		if (imp.isDisplayedHyperStack()) {
			deleteHyperstackChannelSliceOrFrame();
			return;
		}
		if (!imp.lock()) return;
		ImageStack stack = imp.getStack();
		int n = imp.getCurrentSlice();
 		stack.deleteSlice(n);
 		if (stack.getSize()==1) {
			String label = stack.getSliceLabel(1);
 			if (label!=null) imp.setProperty("Label", label);
 		}
		imp.setStack(null, stack);
 		if (n--<1) n = 1;
		imp.setSlice(n);
		imp.unlock();
	}

	void addChannel() {
		int c = imp.getChannel();
		ImageStack stack = imp.getStack();
		CompositeImage ci = (CompositeImage)imp;
		if (stack.getSize()>=7 && ci.getMode()==CompositeImage.COMPOSITE) {
			IJ.error("Add Channel", "Composite mode images limited to 7 channels");
			return;
		}
		LUT[] luts = ci.getLuts();
		ImageProcessor ip = stack.getProcessor(1);
		ImageProcessor ip2 = ip.createProcessor(ip.getWidth(), ip.getHeight());
 		stack.addSlice(null, ip2, c);
 		int channels = stack.getSize();
		LUT lut = LUT.createLutFromColor(Color.white);
		imp.setStack(stack, channels, 1, 1);
 		int index = 0;
		for (int i=1; i<=channels; i++) {
			if (c+1==index+1) {
				((CompositeImage)imp).setChannelLut(lut, i);
				c = -1;
			} else
				((CompositeImage)imp).setChannelLut(luts[index++], i);
		}
		imp.updateAndDraw();
	}

	void deleteChannel() {
		int c = imp.getChannel();
		ImageStack stack = imp.getStack();
		CompositeImage ci = (CompositeImage)imp;
 		int mode = ci.getMode();
		LUT[] luts = ci.getLuts();
 		stack.deleteSlice(c);
 		int channels = stack.getSize();
 		imp.setStack(stack, channels, 1, 1);
 		if (mode==CompositeImage.COMPOSITE && channels==1)
 			mode = CompositeImage.COLOR;
		int index = 0;
		for (int i=1; i<=channels; i++) {
			if (c==index+1) index++;
			((CompositeImage)imp).setChannelLut(luts[index++], i);
		}
		imp.updateAndDraw();
	}

	void deleteHyperstackChannelSliceOrFrame() {
		int channels = imp.getNChannels();
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		int c1 = imp.getChannel();
		int z1 = imp.getSlice();
		int t1 = imp.getFrame();
		ArrayList list = new ArrayList();
		if (channels>1) list.add("channel");
		if (slices>1) list.add("slice");
		if (frames>1) list.add("frame");
		String[] choices = new String[list.size()];
		list.toArray(choices);
		String choice = choices[0];
		if (frames>1 && slices==1)
			choice = "frame";
		else if (slices>1)
			choice = "slice";
    	String options = Macro.getOptions();
		if (IJ.isMacro() && options!=null && !options.contains("delete=")) {
			if (options.contains("delete"))
    			Macro.setOptions("delete=frame");
    		else
    			Macro.setOptions("delete=slice");
    	}
		GenericDialog gd = new GenericDialog("Delete");
		gd.addChoice("Delete current", choices, choice);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		choice = gd.getNextChoice();
		if (!imp.lock()) return;
		ImageStack stack = imp.getStack();
		LUT[] luts = null;
		if (choice.equals("frame")) { // delete time point
			for (int z=slices; z>=1; z--) {
				int index = imp.getStackIndex(channels, z, t1);
				for (int i=0; i<channels; i++)
					stack.deleteSlice(index-i);
			}
			frames--;
		} else if (choice.equals("slice")) { // delete slice z1 from all volumes
			for (int t=frames; t>=1; t--) {
				int index = imp.getStackIndex(channels, z1, t);
				for (int i=0; i<channels; i++)
					stack.deleteSlice(index-i);
			}
			slices--;
		} else if (choice.equals("channel")) { // delete channe c1
			if (imp.isComposite())
				luts = ((CompositeImage)imp).getLuts();
			int index = imp.getStackIndex(c1, slices, frames);
			while (index>0) {
				stack.deleteSlice(index);
				index -= channels;
			}
			channels--;
		}
		imp.setDimensions(channels, slices, frames);
		if (luts!=null) {
			for (int i=c1-1; i<luts.length-1; i++)
				luts[i] = luts[i+1];
			for (int c=1; c<=channels; c++)
				((CompositeImage)imp).setChannelLut(luts[c-1], c);
			imp.updateAndDraw();
		}
		imp.unlock();
	}

	public void convertImagesToStack() {
		(new ImagesToStack()).run("");
	}

	public void convertStackToImages(ImagePlus imp) {
		if (nSlices<2)
			{IJ.error("\"Convert Stack to Images\" requires a stack"); return;}
		if (!imp.lock())
			return;
		ImageStack stack = imp.getStack();
		int size = stack.getSize();
		if (size>30 && !IJ.isMacro()) {
			boolean ok = IJ.showMessageWithCancel("Convert to Images?",
			"Are you sure you want to convert this\nstack to "
			+size+" separate windows?");
			if (!ok)
				{imp.unlock(); return;}
		}
		Calibration cal = imp.getCalibration();
		CompositeImage cimg = imp.isComposite()?(CompositeImage)imp:null;
		if (imp.getNChannels()!=imp.getStackSize()) cimg = null;
		for (int i=1; i<=size; i++) {
			String label = stack.getShortSliceLabel(i);
			String title = label!=null&&!label.equals("")?label:getTitle(imp, i);
			ImageProcessor ip = stack.getProcessor(i);
			if (cimg!=null) {
				LUT lut = cimg.getChannelLut(i);
				if (lut!=null) {
					ip.setColorModel(lut);
					ip.setMinAndMax(lut.min, lut.max);
				}
			}
			ImagePlus imp2 = new ImagePlus(title, ip);
			imp2.setCalibration(cal);
			String info = stack.getSliceLabel(i);
			if (info!=null && !info.equals(label))
				imp2.setProperty("Info", info);
			imp2.show();
		}
		imp.changes = false;
		ImageWindow win = imp.getWindow();
		if (win!=null)
			win.close();
		else if (Interpreter.isBatchMode())
			Interpreter.removeBatchModeImage(imp);
		imp.unlock();
	}

	String getTitle(ImagePlus imp, int n) {
		String digits = "00000000"+n;
		return imp.getShortTitle()+"-"+digits.substring(digits.length()-4,digits.length());
	}
	
}

