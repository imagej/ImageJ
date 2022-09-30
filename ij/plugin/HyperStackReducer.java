package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.Calibration;
import java.awt.*;
import java.util.Vector;

/** Implements the Image/HyperStacks/Reduce Dimensionality command. */
public class HyperStackReducer implements PlugIn, DialogListener {
	ImagePlus imp;
	int channels1, slices1, frames1;
	int channels2, slices2, frames2;
	double imageSize;
	static boolean keep = true;
	
	/** Default constructor */
	public HyperStackReducer() {
	}

	/** Constructs a HyperStackReducer using the specified source image. */
	public HyperStackReducer(ImagePlus imp) {
		this.imp = imp;
	}

	public void run(String arg) {
		//IJ.log("HyperStackReducer-1");
		imp = IJ.getImage();
		if (!imp.isHyperStack() && imp.getNChannels()==1) {
			IJ.error("Reducer", "HyperStack required");
			return;
		}
		int width = imp.getWidth();
		int height = imp.getHeight();
		imageSize = width*height*imp.getBytesPerPixel()/(1024.0*1024.0);
		channels1 = channels2 = imp.getNChannels();
		slices1 = slices2 = imp.getNSlices();
		frames1 = frames2 = imp.getNFrames();
		int z0 = imp.getSlice();
		int t0 = imp.getFrame();
		if (!showDialog())
			return;
		//IJ.log("HyperStackReducer-2: "+keep+" "+channels2+" "+slices2+" "+frames2);
		String title2 = keep?WindowManager.getUniqueName(imp.getTitle()):imp.getTitle();
		ImagePlus imp2 = null;
		if (keep) {
			imp2 = IJ.createImage(title2, imp.getBitDepth()+"-bit", width, height, channels2*slices2*frames2);
			if (imp2==null) return;
			imp2.setDimensions(channels2, slices2, frames2);
			imp2.setCalibration(imp.getCalibration());
			imp2.setOpenAsHyperStack(true);
		} else
			imp2 = imp.createHyperStack(title2, channels2, slices2, frames2, imp.getBitDepth());
		imp2.setProperty("Info", (String)imp.getProperty("Info"));		
		imp2.setProperties(imp.getPropertiesAsArray());
		reduce(imp2);
		if (channels2>1 && channels2==imp.getNChannels() && imp.isComposite()) {
			int mode = ((CompositeImage)imp).getMode();
			imp2 = new CompositeImage(imp2, mode);
			((CompositeImage)imp2).copyLuts(imp);
		} else {
			imp2.setDisplayRange(imp.getDisplayRangeMin(), imp.getDisplayRangeMax());
			if (imp.isComposite() && ((CompositeImage)imp).getMode()==IJ.GRAYSCALE)
				IJ.run(imp2, "Grays", "");
		}
		if (imp.getWindow()==null && !keep) {
			imp.setImage(imp2);
			return;
		}
		imp2.show();
		if (z0>1 || t0>1)
			imp2.setPosition(1, z0, t0);
		//IJ.log("HyperStackReducer-4");
		if (!keep) {
			imp.changes = false;
			imp.close();
		}
	}

	public void reduce(ImagePlus imp2) {
		int channels = imp2.getNChannels();
		int slices = imp2.getNSlices();
		int frames = imp2.getNFrames();
		int c1 = imp.getChannel();
		int z1 = imp.getSlice();
		int t1 = imp.getFrame();
		int i = 1;
		int n = channels*slices*frames;
		ImageStack stack = imp.getStack();
		ImageStack stack2 = imp2.getStack();
		for (int c=1; c<=channels; c++) {
			if (channels==1) c = c1;
			LUT lut = imp.isComposite()?((CompositeImage)imp).getChannelLut():imp.getProcessor().getLut();
			imp.setPositionWithoutUpdate(c, 1, 1);
			ImageProcessor ip = imp.getProcessor();
			double min = ip.getMin();
			double max = ip.getMax();
			for (int z=1; z<=slices; z++) {
				if (slices==1) z = z1;
				for (int t=1; t<=frames; t++) {
					if (frames==1) t = t1;
					int n1 = imp.getStackIndex(c, z, t);
					ip = stack.getProcessor(n1);
					String label = stack.getSliceLabel(n1);
					int n2 = imp2.getStackIndex(c, z, t);
					if (stack2.getPixels(n2)!=null)
						stack2.getProcessor(n2).insert(ip, 0, 0);
					else
						stack2.setPixels(ip.getPixels(), n2);
					stack2.setSliceLabel(label, n2);
				}
			}
			if (lut!=null) {
				if (imp2.isComposite())
					((CompositeImage)imp2).setChannelLut(lut);
				else
					imp2.getProcessor().setColorModel(lut);
			}
			imp2.getProcessor().setMinAndMax(min, max);
		}
		imp.setPosition(c1, z1, t1);
		imp2.resetStack();
		imp2.setPosition(1, 1, 1);
		Overlay overlay = imp.getOverlay();
		if (overlay!=null && !imp.getHideOverlay())
			imp2.setOverlay(reduce(overlay));
	}

	//Added by Marcel Boeglin 2013.11.29
	/** Returns a copy of 'overlay', limited to the dimensions of the reduced image. */
	private Overlay reduce(Overlay overlay) {
		int c1 = imp.getChannel();
		int z1 = imp.getSlice();
		int t1 = imp.getFrame();
		Overlay overlay2 = overlay.duplicate();
		if (channels2==1 && slices2==slices1 && frames2==frames1)
			overlay2.crop(c1, c1, 1, slices1, 1, frames1);
		else if (channels2==channels1 && slices2==1 && frames2==frames1)
			overlay2.crop(1, channels1, z1, z1, 1, frames1);
		else if (channels2==channels1 && slices2==slices1 && frames2==1)
			overlay2.crop(1, channels1, 1, slices1, t1, t1);
		else if (channels2==channels1 && slices2==1 && frames2==1)
			overlay2.crop(1, channels1, z1, z1, t1, t1);
		else if (channels2==1 && slices2==slices1 && frames2==1)
			overlay2.crop(c1, c1, 1, slices1, t1, t1);
		else if (channels2==1 && slices2==1 && frames2==frames1)
			overlay2.crop(c1, c1, z1, z1, 1, frames1);
		else if (channels2==1 && slices2==1 && frames2==1)
			overlay2.crop(c1, c1, z1, z1, t1, t1);
		return overlay2;
	}
    
    boolean showDialog() {
		GenericDialog gd = new GenericDialog("Reduce");
		gd.setInsets(10, 20, 5);
		gd.addMessage("Create image with:");
		gd.setInsets(0, 35, 0);
		if (channels1!=1) gd.addCheckbox("Channels ("+channels1+")", true);
		gd.setInsets(0, 35, 0);
		if (slices1!=1) gd.addCheckbox("Slices ("+slices1+")", true);
		gd.setInsets(0, 35, 0);
		if (frames1!=1) gd.addCheckbox("Frames ("+frames1+")", true);
		gd.setInsets(5, 20, 0);
		gd.addMessage(getNewDimensions()+"      ");
		gd.setInsets(15, 20, 0);
		gd.addCheckbox("Keep source", keep);
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		else
			return true;
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		if (IJ.isMacOSX()) IJ.wait(100);
		if (channels1!=1) channels2 = gd.getNextBoolean()?channels1:1;
		if (slices1!=1) slices2 = gd.getNextBoolean()?slices1:1;
		if (frames1!=1) frames2 = gd.getNextBoolean()?frames1:1;
		keep = gd.getNextBoolean();
		if (imp!=null && imp.getWindow()!=null)
			((Label)gd.getMessage()).setText(getNewDimensions());
		return true;
	}

	String getNewDimensions() {
		String s = channels2+"x"+slices2+"x"+frames2;
		s += " ("+(int)Math.round(imageSize*channels2*slices2*frames2)+"MB)";
		return(s);
	}

}
