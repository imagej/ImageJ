package ij.plugin;

import ij.*;
import ij.process.*;
import ij.gui.GenericDialog;
import ij.util.ThreadUtil;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * This plugin implements most of the 3D filters in the Process/Filters submenu.
 * @author Thomas Boudier
 */
public class Filters3D implements PlugIn {
    public final static int MEAN=10, MEDIAN=11, MIN=12, MAX=13, VAR=14, MAXLOCAL=15;
	private static float xradius = 2, yradius = 2, zradius = 2;
	private static boolean doAllFrms=true;
	private static boolean doAllChs=true;

	public void run(String arg) {
		String name = null;
		int filter = 0;
		if (arg.equals("mean")) {
			name = "3D Mean";
			filter = MEAN;
		} else if (arg.equals("median")) {
			name = "3D Median";
			filter = MEDIAN;
		} else if (arg.equals("min")) {
			name = "3D Minimum";
			filter = MIN;
		} else if (arg.equals("max")) {
			name = "3D Maximum";
			filter = MAX;
		} else if (arg.equals("var")) {
			name = "3D Variance";
			filter = VAR;
		} else
			return;
		ImagePlus imp = IJ.getImage();
		if (imp.isComposite() && imp.getNChannels()==imp.getStackSize()) {
			IJ.error(name, "Composite color images not supported");
			return;
		}
		if (!showDialog(name))
			return;
		imp.startTiming();
		run(imp, filter, xradius, yradius, zradius);
		int nProcessedSlices=imp.getStackSize();
		if(!doAllChs)nProcessedSlices/=imp.getNChannels();
		if(!doAllFrms)nProcessedSlices/=imp.getNFrames();
		IJ.showTime(imp, imp.getStartTime(), "", nProcessedSlices);
	}

	private boolean showDialog(String name) {
		ImagePlus imp = IJ.getImage();
		GenericDialog gd = new GenericDialog(name);
		gd.addNumericField("X radius:", xradius, 1);
		gd.addNumericField("Y radius:", yradius, 1);
		gd.addNumericField("Z radius:", zradius, 1);
		if(imp.getNChannels()>1)gd.addCheckbox("SingleChannel (current channel only)", !doAllChs);
		if(imp.getNFrames()>1)gd.addCheckbox("SingleFrame (current frame only)", !doAllFrms);
		gd.showDialog();
		if (gd.wasCanceled()) {
			return false;
		}
		xradius = (float) gd.getNextNumber();
		yradius = (float) gd.getNextNumber();
		zradius = (float) gd.getNextNumber();
		if(imp.getNChannels()>1)doAllChs=!gd.getNextBoolean();
		if(imp.getNFrames()>1)doAllFrms=!gd.getNextBoolean();
		return true;
	}

	private void run(ImagePlus imp, int filter, float radX, float radY, float radZ) {
		int cmax=imp.getC(), cmin=cmax-1, tmax=imp.getT(), tmin=tmax-1, nZSlices=imp.getNSlices();
		if(doAllChs) { cmin=0; cmax=imp.getNChannels(); }
		if(doAllFrms) { tmin=0; tmax=imp.getNFrames(); }
		if(imp.getStackSize()==imp.getNFrames()) nZSlices=imp.getNFrames();
		ImageStack res = filter(imp.getStack(), filter, radX, radY, radZ, imp.getNChannels(), nZSlices, cmin, cmax, tmin, tmax);
		imp.setStack(res);
	}

	public static ImageStack filter(ImageStack stackorig, int filter, float vx, float vy, float vz) {
		return filter(stackorig, filter, vx, vy, vz, 1, stackorig.size(), 0, 1, 0, 1);
	}
	
	/**
	 * Original functionality was for a single stack, so if imp is not provided, then
	 * assume a non-hyperstack.
	 * 
	 * @param imp Required if a hyperstack, otherwise null is ok
	 * @param stack ImageStack
	 * @param filter
	 * @param vx
	 * @param vy
	 * @param vz
	 * @return
	 */
	public static ImageStack filter(final ImageStack stack, final int filter, final float vx, final float vy, final float vz,
			final int nChs, int numberOfZSlices, int cmin, int cmax, int tmin, int tmax) {
	
		if (stack.getBitDepth()==24)
			return filterRGB(stack, filter, vx, vy, vz, numberOfZSlices, tmin, tmax);

		// get stack info
		final int width= stack.getWidth();
		final int height= stack.getHeight();
		final int depth= stack.size();
		if(numberOfZSlices>depth) numberOfZSlices=depth;
		final int nZSlices=numberOfZSlices;
		if(cmin<0) cmin=0;
		if(tmin<0) tmin=0;
		if(cmax<nChs) cmax=nChs;
		if(tmax>(depth/nChs/nZSlices)) tmax=depth/nChs/nZSlices;
		ImageStack res = null;
		
		if ((filter==MEAN) || (filter==MEDIAN) || (filter==MIN) || (filter==MAX) || (filter==VAR)) {
			if (filter==VAR)
				res = ImageStack.create(width, height, depth, 32);
			else
				res = ImageStack.create(width, height, depth, stack.getBitDepth());
			IJ.showStatus("3D filtering...");
			// PARALLEL 
			final ImageStack out = res;
			final AtomicInteger ai = new AtomicInteger(0);
			final int n_cpus = Prefs.getThreads();

			final int dec = (int) Math.ceil((double) stack.size() / (double) n_cpus);
			for(int fr=0; fr< (depth/nZSlices/nChs); fr++) {
				for(int ch=0; ch<nChs; ch++) {
					if( fr>=tmin && fr<tmax && ch>=cmin && ch<cmax) {
						final int chf=ch;
						final int frf=fr;
						ai.set(0);
						Thread[] threads = ThreadUtil.createThreadArray(n_cpus);
						for (int ithread = 0; ithread < threads.length; ithread++) {
							threads[ithread] = new Thread() {
								public void run() {
									StackProcessor processor = new StackProcessor(stack);
									for (int k = ai.getAndIncrement(); k < n_cpus; k = ai.getAndIncrement()) {
										processor.filter3D(out, nChs, nZSlices, vx, vy, vz, chf, chf+1, dec * k, dec * (k + 1), frf, frf+1, filter);
									}
								}
							};
						}
						ThreadUtil.startAndJoin(threads);
					}else {
						for(int sl=0;sl<nZSlices;sl++) {
							int index=1+ch+(nChs*sl)+(nChs*nZSlices*fr);
							out.setProcessor(stack.getProcessor(index),index);
							out.setSliceLabel(stack.getSliceLabel(index), index);
						}
					}
				}
			}
			
		}
		return res;
	}

	private static ImageStack filterRGB(ImageStack rgb_in, int filter, float vx, float vy, float vz, int nZSlices, int tmin, int tmax) {
        ImageStack[] channels = ChannelSplitter.splitRGB(rgb_in, false);
		ImageStack red = filter(channels[0], filter, vx, vy, vz, 1, nZSlices, 0, 1, tmin, tmax);
		ImageStack green = filter(channels[1], filter, vx, vy, vz, 1, nZSlices, 0, 1, tmin, tmax);
		ImageStack blue = filter(channels[2], filter, vx, vy, vz, 1, nZSlices, 0, 1, tmin, tmax);
        return RGBStackMerge.mergeStacks(red, green, blue, false);
	}

}
