/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ij.plugin;

import ij.IJ;
import ij.ImageStack;
import ij.process.StackProcessor;
import ij.util.ThreadUtil;
import java.util.concurrent.atomic.AtomicInteger;

public class FastFilters3D {

	public static ImageStack filterIntImage(ImageStack stackorig, int filter, float vx, float vy, float vz, int nbcpus) {
	
		if (stackorig.getBitDepth()==24)
			return filterRGB(stackorig, filter, vx, vy, vz, nbcpus);

		// get stack info
		final ImageStack stack = stackorig;
		final float voisx = vx;
		final float voisy = vy;
		final float voisz = vz;
		ImageStack res = null;

		if ((filter == StackProcessor.FILTER_MEAN) || (filter == StackProcessor.FILTER_MEDIAN) || (filter == StackProcessor.FILTER_MIN) || (filter == StackProcessor.FILTER_MAX) || (filter == StackProcessor.FILTER_VAR)) {
			res = new ImageStack(stack.getWidth(), stack.getHeight());
			if (filter == StackProcessor.FILTER_VAR) {
				res.createEmptyStackFloat(stack.getSize());
			} else {
				if (stack.getBitDepth() == 16) {
					res.createEmptyStackShort(stack.getSize());
				} else if (stack.getBitDepth() == 8) {
					res.createEmptyStackByte(stack.getSize());
				}
			}
			// PARALLEL 
			final ImageStack out = res;
			final AtomicInteger ai = new AtomicInteger(0);
			final int n_cpus = nbcpus == 0 ? ThreadUtil.getNbCpus() : nbcpus;

			final int f = filter;
			final int dec = (int) Math.ceil((double) stack.getSize() / (double) n_cpus);
			Thread[] threads = ThreadUtil.createThreadArray(n_cpus);
			for (int ithread = 0; ithread < threads.length; ithread++) {
				threads[ithread] = new Thread() {
					public void run() {
						StackProcessor image = new StackProcessor(stack, stack.getProcessor(1));
						//image.setShowStatus(show);
						for (int k = ai.getAndIncrement(); k < n_cpus; k = ai.getAndIncrement()) {
							image.filterGeneric(out, voisx, voisy, voisz, dec * k, dec * (k + 1), f);
						}
					}
				};
			}
			ThreadUtil.startAndJoin(threads);
		}
		return res;
	}
	
	private static ImageStack filterRGB(ImageStack stackorig, int filter, float vx, float vy, float vz, int nbcpus) {
		return null;
	}

}
