package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.image.*;

public class RGBStackMerge implements PlugIn {

	private static boolean staticCreateComposite = true;
	private static boolean staticKeep;
	private static boolean staticIgnoreLuts;
	private ImagePlus imp;
	private byte[] blank;
	private boolean ignoreLuts;
 
	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
		mergeStacks();
	}
	
	public static ImagePlus mergeChannels(ImagePlus[] images, boolean keepSourceImages) {
		RGBStackMerge rgbsm = new RGBStackMerge();
		return rgbsm.mergeHyperstacks(images, keepSourceImages);
	}

	/** Combines three grayscale stacks into one RGB stack. */
	public void mergeStacks() {
		int[] wList = WindowManager.getIDList();
		if (wList==null) {
			error("No images are open.");
			return;
		}

		String[] titles = new String[wList.length+1];
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp = WindowManager.getImage(wList[i]);
			titles[i] = imp!=null?imp.getTitle():"";
		}
		String none = "*None*";
		titles[wList.length] = none;
		boolean createComposite = staticCreateComposite;
		boolean keep = staticKeep;
		ignoreLuts = staticIgnoreLuts;
		if (IJ.isMacro())
			createComposite = keep = ignoreLuts = false;

		GenericDialog gd = new GenericDialog("Color Merge");
		gd.addChoice("Red:", titles, titles[0]);
		gd.addChoice("Green:", titles, titles[1]);
		String title3 = titles.length>2&&!IJ.macroRunning()?titles[2]:none;
		gd.addChoice("Blue:", titles, title3);
		String title4 = titles.length>3&&!IJ.macroRunning()?titles[3]:none;
		gd.addChoice("Gray:", titles, title4);
		gd.addCheckbox("Create composite", createComposite);
		gd.addCheckbox("Keep source images", keep);
		gd.addCheckbox("Ignore source LUTs", ignoreLuts);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int[] index = new int[4];
		index[0] = gd.getNextChoiceIndex();
		index[1] = gd.getNextChoiceIndex();
		index[2] = gd.getNextChoiceIndex();
		index[3] = gd.getNextChoiceIndex();
		createComposite = gd.getNextBoolean();
		keep = gd.getNextBoolean();
		ignoreLuts = gd.getNextBoolean();
		if (!IJ.isMacro()) {
			staticCreateComposite = createComposite;
			staticKeep = keep;
			staticIgnoreLuts = ignoreLuts;
		}

		ImagePlus[] images = new ImagePlus[4];
		int stackSize = 0;
		int width = 0;
		int height = 0;
		int bitDepth = 0;
		int slices = 0;
		int frames = 0;
		for (int i=0; i<4; i++) {
			//IJ.log(i+"  "+index[i]+"	"+titles[index[i]]+"  "+wList.length);
			if (index[i]<wList.length) {
				images[i] = WindowManager.getImage(wList[index[i]]);
				if (width==0) {
					width = images[i].getWidth();
					height = images[i].getHeight();
					stackSize = images[i].getStackSize();
					bitDepth = images[i].getBitDepth();
					slices = images[i].getNSlices();
					frames = images[i].getNFrames();
				}
			}
		}
		if (width==0) {
			error("There must be at least one source image or stack.");
			return;
		}
		
		boolean mergeHyperstacks = false;
		for (int i=0; i<4; i++) {
			ImagePlus img = images[i];
			if (img==null) continue;
			if (img.getStackSize()!=stackSize) {
				error("The source stacks must have the same number of images.");
				return;
			}
			if (img.isHyperStack()) {
				if (img.isComposite()) {
					CompositeImage ci = (CompositeImage)img;
					if (ci.getMode()!=CompositeImage.COMPOSITE) {
						ci.setMode(CompositeImage.COMPOSITE);
						img.updateAndDraw();
						if (!IJ.isMacro()) IJ.run("Channels Tool...");
						return;
					}
				}
				if (bitDepth==24) {
					error("Source hyperstacks cannot be RGB.");
					return;
				}
				if (img.getNChannels()>1) {
					error("Source hyperstacks cannot have more than 1 channel.");
					return;
				}
				if (img.getNSlices()!=slices || img.getNFrames()!=frames) {
					error("Source hyperstacks must have the same dimensions.");
					return;
				}
				mergeHyperstacks = true;
			} // isHyperStack
			if (img.getWidth()!=width || images[i].getHeight()!=height) {
				error("The source images or stacks must have the same width and height.");
				return;
			}
			//if (createComposite) {
			//	for (int j=0; j<4; j++) {
			//		if (j!=i && images[j]!=null && img==images[j])
			//			createComposite = false;
			//	}
			//}
			if (createComposite && img.getBitDepth()!=bitDepth) {
				error("The source images must have the same bit depth.");
				return;
			}
		}

		ImageStack[] stacks = new ImageStack[4];
		stacks[0] = images[0]!=null?images[0].getStack():null;
		stacks[1]  = images[1]!=null?images[1].getStack():null;
		stacks[2]  = images[2]!=null?images[2].getStack():null;
		stacks[3]  = images[3]!=null?images[3].getStack():null;
		String macroOptions = Macro.getOptions();
		if	(macroOptions!=null && macroOptions.indexOf("gray=")==-1)
				stacks[3] = null; // ensure compatibility with old macros
		ImagePlus imp2;
		boolean fourChannelRGB = !createComposite && stacks[3]!=null;
		if (fourChannelRGB)
			createComposite = true;
		
		if (stacks[3]!=null)
			createComposite = true;
		for (int i=0; i<4; i++) {
			if (images[i]!=null && images[i].getBitDepth()==24)
				createComposite = false;
		}
		if (createComposite || mergeHyperstacks) {
			imp2 = mergeHyperstacks(images, keep);
			if (imp2==null) return;
		} else {
			ImageStack rgb = mergeStacks(width, height, stackSize, stacks[0], stacks[1], stacks[2], keep);
			imp2 = new ImagePlus("RGB", rgb);
		}
		if (images[0]!=null)
			imp2.setCalibration(images[0].getCalibration());
		if (!keep) {
			for (int i=0; i<4; i++) {
				if (images[i]!=null && images[i].getWindow()!=null) {
					images[i].changes = false;
					images[i].close();
				}
			}
		}
		if (fourChannelRGB) {
			if (imp2.getStackSize()==1) {
				imp2 = imp2.flatten();
				imp2.setTitle("RGB");
			} else {
				imp2.setTitle("RGB");
				IJ.run(imp2, "RGB Color", "slices");
			}
		}
		imp2.show();
	 }
	 
	
	public ImagePlus mergeHyperstacks(ImagePlus[] images, boolean keep) {
		int n = images.length;
		int channels = 0;
		for (int i=0; i<n; i++) {
			if (images[i]!=null) channels++;
		}
		if (channels<2) return null;
		ImagePlus[] images2 = new ImagePlus[channels];
		Color[] defaultColors = {Color.red, Color.green, Color.blue, Color.white};
		Color[] colors = new Color[channels];
		int j = 0;
		for (int i=0; i<n; i++) {
			if (images[i]!=null) {
				images2[j] = images[i];
				if (i<defaultColors.length)
					colors[j] = defaultColors[i];
				j++;
			}
		}
		images = images2;
		ImageStack[] stacks = new ImageStack[channels];
		for (int i=0; i<channels; i++) {
			ImagePlus imp2 = images[i];
			if (isDuplicate(i,images))
				imp2 = imp2.duplicate();
			stacks[i] = imp2.getStack();
		}
		ImagePlus imp = images[0];
		int w = imp.getWidth();
		int h = imp.getHeight();
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		ImageStack stack2 = new ImageStack(w, h);
		//IJ.log("mergeHyperstacks: "+w+" "+h+" "+channels+" "+slices+" "+frames);
		int[] index = new int[channels];
		for (int t=0; t<frames; t++) {
			for (int z=0; z<slices; z++) {
				for (int c=0; c<channels; c++) {
					ImageProcessor ip = stacks[c].getProcessor(index[c]+1);
					if (keep)
						ip = ip.duplicate();
					stack2.addSlice(null, ip);
					if (keep)
						index[c]++;
					else
						stacks[c].deleteSlice(1);
				}
			}
		}
		String title = imp.getTitle();
		if (title.startsWith("C1-"))
			title = title.substring(3);
		else
			title = frames>1?"Merged":"Composite";
		ImagePlus imp2 = new ImagePlus(title, stack2);
		imp2.setDimensions(channels, slices, frames);
		imp2 = new CompositeImage(imp2, CompositeImage.COMPOSITE);
		for (int c=0; c<channels; c++) {
			ImageProcessor ip = images[c].getProcessor();
			IndexColorModel cm = (IndexColorModel)ip.getColorModel();
			LUT lut = null;
			if (c<colors.length && colors[c]!=null && (ignoreLuts||!ip.isColorLut())) {
				lut = LUT.createLutFromColor(colors[c]);
				lut.min = ip.getMin();
				lut.max = ip.getMax();
			} else
				lut =  new LUT(cm, ip.getMin(), ip.getMax());
			((CompositeImage)imp2).setChannelLut(lut, c+1);
		}
		imp2.setOpenAsHyperStack(true);
		return imp2;
	}
	
	private boolean isDuplicate(int index, ImagePlus[] images) {
		int count = 0;
		for (int i=0; i<index; i++) {
			if (images[index]==images[i])
				return true;
		}
		return false;
	}

	/** Deprecated; replaced by mergeChannels(). */
	public ImagePlus createComposite(int w, int h, int d, ImageStack[] stacks, boolean keep) {
		ImagePlus[] images = new ImagePlus[stacks.length];
		for (int i=0; i<stacks.length; i++)
			images[i] = new ImagePlus(""+i, stacks[i]);
		return mergeHyperstacks(images, keep);
	}

	public ImageStack mergeStacks(int w, int h, int d, ImageStack red, ImageStack green, ImageStack blue, boolean keep) {
		ImageStack rgb = new ImageStack(w, h);
		int inc = d/10;
		if (inc<1) inc = 1;
		ColorProcessor cp;
		int slice = 1;
		blank = new byte[w*h];
		byte[] redPixels, greenPixels, bluePixels;
		boolean invertedRed = red!=null?red.getProcessor(1).isInvertedLut():false;
		boolean invertedGreen = green!=null?green.getProcessor(1).isInvertedLut():false;
		boolean invertedBlue = blue!=null?blue.getProcessor(1).isInvertedLut():false;
		try {
			for (int i=1; i<=d; i++) {
				cp = new ColorProcessor(w, h);
				redPixels = getPixels(red, slice, 0);
				greenPixels = getPixels(green, slice, 1);
				bluePixels = getPixels(blue, slice, 2);
				if (invertedRed) redPixels = invert(redPixels);
				if (invertedGreen) greenPixels = invert(greenPixels);
				if (invertedBlue) bluePixels = invert(bluePixels);
				cp.setRGB(redPixels, greenPixels, bluePixels);
			if (keep) {
				slice++;
			} else {
				if (red!=null) red.deleteSlice(1);
				if (green!=null &&green!=red) green.deleteSlice(1);
				if (blue!=null&&blue!=red && blue!=green) blue.deleteSlice(1);
			}
			rgb.addSlice(null, cp);
			if ((i%inc) == 0) IJ.showProgress((double)i/d);
			}
		IJ.showProgress(1.0);
		} catch(OutOfMemoryError o) {
			IJ.outOfMemory("Merge Stacks");
			IJ.showProgress(1.0);
		}
		return rgb;
	}
	
	 byte[] getPixels(ImageStack stack, int slice, int color) {
		 if (stack==null)
			return blank;
		Object pixels = stack.getPixels(slice);
		if (!(pixels instanceof int[])) {
			if (pixels instanceof byte[])
				return (byte[])pixels;
			else {
				ImageProcessor ip = stack.getProcessor(slice);
				ip = ip.convertToByte(true);
				return (byte[])ip.getPixels();
			}
		} else { //RGB
			byte[] r,g,b;
			int size = stack.getWidth()*stack.getHeight();
			r = new byte[size];
			g = new byte[size];
			b = new byte[size];
			ColorProcessor cp = (ColorProcessor)stack.getProcessor(slice);
			cp.getRGB(r, g, b);
			switch (color) {
				case 0: return r;
				case 1: return g;
				case 2: return b;
			}
		}
		return null;
	}

	byte[] invert(byte[] pixels) {
		byte[] pixels2 = new byte[pixels.length];
		System.arraycopy(pixels, 0, pixels2, 0, pixels.length);
		for (int i=0; i<pixels2.length; i++)
			pixels2[i] = (byte)(255-pixels2[i]&255);
		return pixels2;
	}
	
	void error(String msg) {
		IJ.error("Merge Channels", msg);
	}

}

