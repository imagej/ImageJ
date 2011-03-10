package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;

/** This plugin, which concatenates two images or stacks, implements the
	Image/Stacks/Tools/Concatenate command.
	The images or stacks must have same width, height and data type. */
public class Concatenator implements PlugIn {

	ImagePlus imp1, imp2;
	static boolean keep;
	static String title = "Concatenated Stacks";

	public void run(String arg) {
		if (!showDialog())
			return;
		ImagePlus imp3 = null;
		if (imp1.isComposite() || imp1.isHyperStack()) {
			ImagePlus[] images = new ImagePlus[2];
			images[0] = imp1;
			images[1] = imp2;
			imp3 = concatenate(images, keep);
			if (imp3==null)
				error();
			else
				imp3.setTitle(title);
		} else
			imp3 = concatenate(imp1, imp2, keep);
		if (imp3!=null) imp3.show();
	}
	
	public ImagePlus concatenate(ImagePlus imp1, ImagePlus imp2, boolean keep) {
		if (imp1.getType()!=imp2.getType() || imp1.isHyperStack() || imp2.isHyperStack())
			{error(); return null;}
		int width = imp1.getWidth();
		int height = imp1.getHeight();
		if (width!=imp2.getWidth() || height!=imp2.getHeight())
			{error(); return null;}
		ImageStack stack1 = imp1.getStack();
		ImageStack stack2 = imp2.getStack();
		int size1 = stack1.getSize();
		int size2 = stack2.getSize();
		ImageStack stack3 = imp1.createEmptyStack();
		int slice = 1;
		for (int i=1; i<=size1; i++) {
			ImageProcessor ip = stack1.getProcessor(slice);
			String label = stack1.getSliceLabel(slice);
			if (keep || imp1==imp2) {
				ip = ip.duplicate();
				slice++;
			} else
				stack1.deleteSlice(slice);
			stack3.addSlice(label, ip);
		}
		slice = 1;
		for (int i=1; i<=size2; i++) {
			ImageProcessor ip = stack2.getProcessor(slice);
			String label = stack2.getSliceLabel(slice);
			if (keep || imp1==imp2) {
				ip = ip.duplicate();
				slice++;
			} else
				stack2.deleteSlice(slice);
			stack3.addSlice(label, ip);
		}
		ImagePlus imp3 = new ImagePlus(title, stack3);
		imp3.setCalibration(imp1.getCalibration());
		if (!keep) {
			imp1.changes = false;
			imp1.close();
			if (imp1!=imp2) {
				imp2.changes = false;
				imp2.close();
			}
		}
		return imp3;
	}
	
	public ImagePlus concatenate(ImagePlus[] images, boolean keepSourceImages) {
		int n = images.length;
		int width = images[0].getWidth();
		int height = images[0].getHeight();
		int bitDepth = images[0].getBitDepth();
		int channels = images[0].getNChannels();
		int slices =  images[0].getNSlices();
		int frames = images[0].getNFrames();
		boolean concatSlices = slices>1 && frames==1;
		for (int i=1; i<n; i++) {
			if (images[i].getNFrames()>1) concatSlices = false;
			if (images[i].getWidth()!=width
			|| images[i].getHeight()!=height
			|| images[i].getBitDepth()!=bitDepth
			|| images[i].getNChannels()!=channels
			|| (!concatSlices && images[i].getNSlices()!=slices))
				return null;
		}
		ImageStack stack2 = new ImageStack(width, height);
		int slices2=0, frames2=0;
		for (int i=0;i<n;i++) {
			ImageStack stack = images[i].getStack();
			slices = images[i].getNSlices();
			if (concatSlices) {
				slices = images[i].getNSlices();
				slices2 += slices;
				frames2 = frames;
			} else {
				frames = images[i].getNFrames();
				frames2 += frames;
				slices2 = slices;
			}
			for (int f=1; f<=frames; f++) {
				for (int s=1; s<=slices; s++) {
					for (int c=1; c<=channels; c++) {
						int index = (f-1)*channels*s + (s-1)*channels + c;
						ImageProcessor ip = stack.getProcessor(index);
						if (keepSourceImages)
							ip = ip.duplicate();
						String label = stack.getSliceLabel(index);
						stack2.addSlice(label, ip);
					}
				}
			}
		}
		ImagePlus imp2 = new ImagePlus("Concatenated Images", stack2);
		imp2.setDimensions(channels, slices2, frames2);
		if (channels>1) {
			int mode = 0;
			if (images[0].isComposite())
				mode = ((CompositeImage)images[0]).getMode();
			imp2 = new CompositeImage(imp2, mode);
			((CompositeImage)imp2).copyLuts(images[0]);
		}
		if (channels>1 && frames2>1)
			imp2.setOpenAsHyperStack(true);
		if (!keepSourceImages) {
			for (int i=0; i<n; i++) {
				images[i].changes = false;
				images[i].close();
			}
		}
		return imp2;
	}

	int getStackIndex(int channel, int slice, int frame, int nChannels, int nSlices, int nFrames) {	
		return (frame-1)*nChannels*nSlices + (slice-1)*nChannels + channel;
	}

	boolean showDialog() {
		int[] wList = WindowManager.getIDList();
		if (wList==null) {
			IJ.noImage();
			return false;
		}

		String[] titles = new String[wList.length];
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp = WindowManager.getImage(wList[i]);
			titles[i] = imp!=null?imp.getTitle():"";
		}

		GenericDialog gd = new GenericDialog("Concatenator");
		gd.addChoice("Stack1:", titles, titles[0]);
		gd.addChoice("Stack2:", titles, wList.length>1?titles[1]:titles[0]);
		gd.addStringField("Title:", title, 16);
		gd.addCheckbox("Keep Source Stacks", keep);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		int[] index = new int[3];
		int index1 = gd.getNextChoiceIndex();
		int index2 = gd.getNextChoiceIndex();
		title = gd.getNextString();
		keep = gd.getNextBoolean();

		imp1 = WindowManager.getImage(wList[index1]);
		imp2 = WindowManager.getImage(wList[index2]);
		return true;
	}

	void error() {
		IJ.showMessage("Concatenator", "This command requires two images with\n"+
			"the same dimesions and data type.");
	}

}

