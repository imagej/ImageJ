package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.ContrastEnhancer;
import ij.util.ThreadUtil;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;


/** This plugin implements the Mean, Minimum, Maximum, Variance, Median,
 *	Remove Outliers, Remove NaNs and Despeckle commands.
 */
 // Version 2012-07-15 M. Schmid:	Fixes a bug that could cause preview not to work correctly
 // Version 2012-12-23 M. Schmid:	Test for inverted LUT only once (not in each slice)
 // Version 2014-10-10 M. Schmid:   Fixes a bug that caused Threshold=0 when calling from API
 // Version 2019-07-26 M. Schmid:   Nonblocking dialog enabled
 // Version 2020-07-17 M. Schmid:   bugfix: changing preview parameters could lead to partial filtering with different parameters.
 //                                 Uses AtomicIntegers. Top-hat filter added

public class RankFilters implements ExtendedPlugInFilter, DialogListener {
	public static final int	 MEAN=0, MIN=1, MAX=2, VARIANCE=3, MEDIAN=4, OUTLIERS=5, DESPECKLE=6, REMOVE_NAN=7,
			OPEN=8, CLOSE=9, TOP_HAT=10; //when adding a new filter, set HIGHEST_FILTER below.
	public static final int BRIGHT_OUTLIERS = 0, DARK_OUTLIERS = 1;
	private static final String[] outlierStrings = {"Bright","Dark"};
	private static int HIGHEST_FILTER = TOP_HAT;
	// Filter parameters
	private int filterType;
	private double radius;
	private double threshold;        //this and the next for 'remove outliers' only
	private int whichOutliers;
	private boolean lightBackground = Prefs.get("bs.background", true); //this and the next for top hat only
	private boolean dontSubtract;
	// Remember filter parameters for the next time
	private static double[] lastRadius = new double[HIGHEST_FILTER+1]; //separate for each filter type
	private static double lastThreshold = 50.;
	private static int lastWhichOutliers = BRIGHT_OUTLIERS;
	private static boolean lastLightBackground = false;
	private static boolean lastDontSubtract = false;
	//
	// F u r t h e r   c l a s s   v a r i a b l e s
	int flags = DOES_ALL|SUPPORTS_MASKING|KEEP_PREVIEW;
	private ImagePlus imp;
	private int nPasses = 1;			// The number of passes (color channels * stack slices)
	private PlugInFilterRunner pfr;
	private int pass;
	private boolean previewing = false;
	// M u l t i t h r e a d i n g - r e l a t e d
	private int numThreads = Prefs.getThreads();
	// The current state of multithreaded processing is in class variables.
	// Thus, stack parallelization must be done ONLY with one thread for the image
	// (not using these class variables).
	// Atomic objects are used to avoid caching (i.e., to ensure that always the current state of the variable is read).
	private AtomicInteger highestYinCache = new AtomicInteger(Integer.MIN_VALUE);	// the highest line read into the cache so far
	private AtomicInteger nThreadsWaiting = new AtomicInteger(0);			// number of threads waiting until they may read data
	private AtomicBoolean copyingToCache  = new AtomicBoolean(false);		// whether a thread is currently copying data to the cache

	/** OPEN, CLOSE, TOPHAT need more than one run of the underlying filter */
	private boolean isMultiStepFilter(int filterType) {
		return filterType>=OPEN && filterType<=TOP_HAT;
	}

	/** Setup of the PlugInFilter. Returns the flags specifying the capabilities and needs
	 * of the filter.
	 *
	 * @param arg	Defines type of filter operation
	 * @param imp	The ImagePlus to be processed
	 * @return		Flags specifying further action of the PlugInFilterRunner
	 */
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		if (arg.equals("mean"))
			filterType = MEAN;
		else if (arg.equals("min"))
			filterType = MIN;
		else if (arg.equals("max"))
			filterType = MAX;
		else if (arg.equals("variance")) {
			filterType = VARIANCE;
			flags |= FINAL_PROCESSING;
		} else if (arg.equals("median"))
			filterType = MEDIAN;
		else if (arg.equals("outliers"))
			filterType = OUTLIERS;
		else if (arg.equals("despeckle"))
			filterType = DESPECKLE;
		else if (arg.equals("tophat"))
			filterType = TOP_HAT;
		else if (arg.equals("nan")) {
			filterType = REMOVE_NAN;
			if (imp!=null && imp.getBitDepth()!=32) {
				IJ.error("RankFilters","\"Remove NaNs\" requires a 32-bit image");
				return DONE;
			}
		} else if (arg.equals("final")) {	//after variance && tophat filter, adjust brightness&contrast
			setDisplayRange(imp.getProcessor());
		} else if (arg.equals("masks")) {
			showMasks();
			return DONE;
		} else {
			IJ.error("RankFilters","Argument missing or undefined: "+arg);
			return DONE;
		}
		if (isMultiStepFilter(filterType) && imp!=null) {  //composite filter: 'open maxima' etc:
			Roi roi = imp.getRoi();
			if (roi!=null && !roi.getBounds().contains(new Rectangle(imp.getWidth(), imp.getHeight())))
				//Roi < image? (actually tested: NOT (Roi>=image))
				flags |= SNAPSHOT;			//snapshot for resetRoiBoundary, also for top-hat subtraction
		}
		return flags;
	}

	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
		if (filterType == DESPECKLE) {
			filterType = MEDIAN;
			radius = 1.0;
		} else {
			GenericDialog gd = NonBlockingGenericDialog.newDialog(command,imp);
			radius = lastRadius[filterType]<=0 ? 2 :  lastRadius[filterType];
			gd.addNumericField("Radius", radius, 1, 6, "pixels");
			if (filterType==OUTLIERS) {
				int digits = imp.getType() == ImagePlus.GRAY32 ? 2 : 0;
				gd.addNumericField("Threshold", lastThreshold, digits);
				gd.addChoice("Which outliers", outlierStrings, outlierStrings[lastWhichOutliers]);
				gd.addHelp(IJ.URL+"/docs/menus/process.html#outliers");
			} else if (filterType==REMOVE_NAN) {
				gd.addHelp(IJ.URL+"/docs/menus/process.html#nans");
			} else if (filterType==TOP_HAT) {
				gd.addCheckbox("Light Background", lastLightBackground);
				gd.addCheckbox("Don't subtract (grayscale open)", lastDontSubtract);
			}
			gd.addPreviewCheckbox(pfr);		//passing pfr makes the filter ready for preview
			gd.addDialogListener(this);		//the DialogItemChanged method will be called on user input
			previewing = true;
			gd.showDialog();				//display the dialog; preview may run now
			previewing = false;
			if (gd.wasCanceled()) return DONE;
			IJ.register(this.getClass());	//protect static class variables (filter parameters) from garbage collection
			if (Macro.getOptions() == null) { //interactive only: remember parameters entered
				lastRadius[filterType] = radius;
				if (filterType == OUTLIERS) {
					lastThreshold = threshold;
					lastWhichOutliers = whichOutliers;
				} else if (filterType==TOP_HAT) {
					lastLightBackground = lightBackground;
					lastDontSubtract = dontSubtract;
				}
			}
		}
		this.pfr = pfr;
		flags = IJ.setupDialog(imp, flags); //ask whether to process all slices of stack (if a stack)
		if ((flags&DOES_STACKS)!=0) {
			int size = imp.getWidth() * imp.getHeight();
			Roi roi = imp.getRoi();
			if (roi != null) {
				Rectangle roiRect = roi.getBounds();
				size = roiRect.width * roiRect.height;
			}
			double workToDo = size*(double)radius;	//estimate computing time (arb. units)
			if (filterType==MEAN || filterType==VARIANCE) workToDo *= 0.5;
			else if (filterType==MEDIAN) workToDo *= radius*0.5;
			if (workToDo < 1e6 && imp.getImageStackSize()>=2*numThreads) {
				numThreads = 1;				//for fast operations, avoid overhead of multi-threading in each image
				flags |= PARALLELIZE_STACKS;
			}
		}
		return flags;
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		radius = gd.getNextNumber();
		if (filterType == OUTLIERS) {
			threshold = gd.getNextNumber();
			whichOutliers = gd.getNextChoiceIndex();
		} else if (filterType==TOP_HAT || filterType==OPEN || filterType==CLOSE) {
			lightBackground = gd.getNextBoolean();
			dontSubtract = gd.getNextBoolean();
		}
		int maxRadius = (filterType==MEDIAN || filterType==OUTLIERS || filterType==REMOVE_NAN) ? 100 : 1000;
		if (gd.invalidNumber() || radius<0 || radius>maxRadius || (filterType==OUTLIERS && threshold <0))
			return false;
		if (filterType == TOP_HAT) {
			if (dontSubtract)
				flags &= ~FINAL_PROCESSING;	//grayscale open: keep display range
			else
				flags |= FINAL_PROCESSING;	//top-hat: readjust display range
		}
		return true;
	}

	public void run(ImageProcessor ip) {
		rank(ip, radius, filterType, whichOutliers, (float)threshold, lightBackground, dontSubtract);
		if (IJ.escapePressed())									// interrupted by user?
			ip.reset();
		else if (previewing && (flags&FINAL_PROCESSING)!=0)
			setDisplayRange(ip);
	}

	/** Filters an image by any method except 'despecle', 'remove outliers', or top-hat
	 *	@param ip	   The ImageProcessor that should be filtered (all 4 types supported)
	 *	@param radius  Determines the kernel size, see Process>Filters>Show Circular Masks.
	 *				   Must not be negative. No checking is done for large values that would
	 *				   lead to excessive computing times.
	 *	@param filterType May be MEAN, MIN, MAX, VARIANCE, or MEDIAN.
	 */
	public void rank(ImageProcessor ip, double radius, int filterType) {
		rank(ip, radius, filterType, 0, 50f);
	}

	/** Filters an image by any method except 'despecle' and top-hat (for 'despeckle', use 'median' and radius=1)
	 * @param ip The image subject to filtering
	 * @param radius The kernel radius
	 * @param filterType as defined above; DESPECKLE is not a valid type here; use median and
	 *		  a radius of 1.0 instead
	 * @param whichOutliers BRIGHT_OUTLIERS or DARK_OUTLIERS for 'outliers' filter
	 * @param threshold Threshold for 'outliers' filter
	 */
	public void rank(ImageProcessor ip, double radius, int filterType, int whichOutliers, float threshold) {
		rank(ip, radius, filterType, whichOutliers, threshold, false, false);
	}

	/** Filters an image by any method except 'despecle' (for 'despeckle', use 'median' and radius=1)
	 * @param ip The image subject to filtering
	 * @param radius The kernel radius
	 * @param filterType as defined above; DESPECKLE is not a valid type here; use median and
	 *		  a radius of 1.0 instead
	 * @param whichOutliers BRIGHT_OUTLIERS or DARK_OUTLIERS for 'outliers' filter
	 * @param threshold Threshold for 'outliers' filter
	 * @param lightBackground for top-hat background subtraction, background is light, not dark
	 * @param dontSubtract fpr top-hat filter, performs a grayscale open or close instead of top-hat,
	 *        where the result of grayscale open/close is subtracted from the original.
	 */
	public void rank(ImageProcessor ip, double radius, int filterType, int whichOutliers, float threshold, boolean lightBackground, boolean dontSubtract) {
		Rectangle roi = ip.getRoi();
		ImageProcessor mask = ip.getMask();
		Rectangle roi1 = null;
		int[] lineRadii = makeLineRadii(radius);

		boolean snapshotRequired = (filterType==TOP_HAT && !dontSubtract) ||
			(isMultiStepFilter(filterType) && (roi.width!=ip.getWidth() || roi.height!=ip.getHeight()));
		if (snapshotRequired && ip.getSnapshotPixels()==null)
			ip.snapshot();

		float minMaxOutliersSign = filterType==MIN || filterType==OPEN ? -1f : 1f; //open is minimum first
		if (filterType == OUTLIERS)		//sign is -1 for high outliers: compare number with minimum
			minMaxOutliersSign = (ip.isInvertedLut()==(whichOutliers==DARK_OUTLIERS)) ? -1f : 1f;

		if (filterType == TOP_HAT) {
			boolean invertedLut = ip.isInvertedLut();
			boolean invert = (invertedLut && !lightBackground) || (!invertedLut && lightBackground);
			minMaxOutliersSign = invert ? 1f : -1f;
		}

		ImageProcessor snapIp = null;
		FloatProcessor fp = null, snapFp = null;
		boolean isImagePart = (roi.width<ip.getWidth()) || (roi.height<ip.getHeight());
		AtomicInteger nextY = new AtomicInteger();		// becomes negative when interrupted during preview or ESC pressed
		if (filterType == TOP_HAT && !dontSubtract) {
			snapIp = ip.createProcessor(ip.getWidth(), ip.getHeight());
			snapIp.setPixels(ip.getSnapshotPixels());
		}
		for (int ch=0; ch<ip.getNChannels(); ch++) {
			int filterType1 = filterType;
			if (isMultiStepFilter(filterType)) { //open, close, top-hat
				filterType1 = (minMaxOutliersSign == -1f) ? MIN : MAX;
					if (isImagePart) { //composite filters ('open maxima' etc.) need larger area in first step
					int kRadius = kRadius(lineRadii);
					int kHeight = kHeight(lineRadii);
					Rectangle roiClone = (Rectangle)roi.clone();
					roiClone.grow(kRadius, kHeight/2);
					roi1 = roiClone.intersection(new Rectangle(ip.getWidth(), ip.getHeight()));
					ip.setRoi(roi1);
				}
			}
			doFiltering(ip, lineRadii, filterType1, minMaxOutliersSign, threshold, ch, nextY);
			if (isMultiStepFilter(filterType)) { //open, close, top-hat
				ip.setRoi(roi);
				ip.setMask(mask);
				if (nextY.get() < 0) break;
				int filterType2 = (minMaxOutliersSign == -1f) ? MAX : MIN;
				doFiltering(ip, lineRadii, filterType2, -minMaxOutliersSign, threshold, ch, nextY);
				if (isImagePart)
					resetRoiBoundary(ip, roi, roi1);
			}
			if (nextY.get() < 0) break;

			if (filterType == TOP_HAT && !dontSubtract) {   //top-hat filter: Subtract opened from input
				float offset = 0;
				if (minMaxOutliersSign == 1f && !(ip instanceof FloatProcessor))
					offset = (float)ip.maxValue();          //background is maximum of possible range (255 or 65535)
				fp = ip.toFloat(ch, fp);
				snapFp = snapIp.toFloat(ch, snapFp);
				float[] pixels = (float[])fp.getPixels();
				float[] snapPixels = (float[])snapFp.getPixels();
				int width = ip.getWidth();
				for (int y=roi.y; y<roi.y+roi.height; y++)
					for (int ix=0, p=y*width+roi.x; ix<roi.width; ix++, p++)
						pixels[p] = snapPixels[p] - pixels[p] + offset;
				ip.setPixels(ch, fp);
			}
		}

	}

	// Filter a grayscale image or one channel of an RGB image with several threads
	// Implementation: each thread uses the same input buffer (cache), always works on the next unfiltered line
	// Usually, one thread reads reads several lines into the cache, while the others are processing the data.
	// 'nextY.get()' is set to a large negative number if the main thread has been interrupted (during preview) or ESC pressed.
	// 'nextY' must not be a class variable because it is also used with one thread (with stack parallelization)
	private void doFiltering(final ImageProcessor ip, final int[] lineRadii, final int filterType,
			final float minMaxOutliersSign, final float threshold, final int colorChannel, final AtomicInteger nextY) {
		Rectangle roi = ip.getRoi();
		int width = ip.getWidth();
		Object pixels = ip.getPixels();
		int numThreads = Math.min(roi.height, this.numThreads);
		if (numThreads==0)
			return;

		int kHeight = kHeight(lineRadii);
		int kRadius	 = kRadius(lineRadii);
		final int cacheWidth = roi.width+2*kRadius;
		final int cacheHeight = kHeight + (numThreads>1 ? 2*numThreads : 0);
		// 'cache' is the input buffer. Each line y in the image is mapped onto cache line y%cacheHeight
		final float[] cache = new float[cacheWidth*cacheHeight];
		highestYinCache.set(Math.max(roi.y-kHeight/2, 0) - 1); //this line+1 will be read into the cache first

		final AtomicIntegerArray yForThread = new AtomicIntegerArray(numThreads);	//threads announce here which line they currently process
		for (int i=0; i<numThreads; i++)
			yForThread.set(i, -1);
		nextY.set(roi.y);										//first thread started should begin at roi.y
		Callable[] callables = new Callable[numThreads-1];
		for (int i=0; i<numThreads-1; i++) {
			final int threadNumber = i+1; // number 0 is reserved for main thread
			callables[i] = new Callable<Void>() {
				final public Void call() {
					doFiltering(ip, lineRadii, cache, cacheWidth, cacheHeight,
							filterType, minMaxOutliersSign, threshold, colorChannel,
							yForThread, threadNumber, nextY);
					return null;
				}
			};
		}
		Future[] futures = ThreadUtil.start(callables);

		doFiltering(ip, lineRadii, cache, cacheWidth, cacheHeight,
				filterType, minMaxOutliersSign, threshold, colorChannel,
				yForThread, /*threadNumber=*/0, nextY);
		ThreadUtil.joinAll(futures);
		showProgress(1.0, ip instanceof ColorProcessor);
		pass++;
	}

	// Filter a grayscale image or one channel of an RGB image using one thread
	//
	// Synchronization: unless a thread is waiting, we avoid the overhead of 'synchronized'
	// statements. That's because a thread waiting for another one should be rare.
	//
	// Data handling: The area needed for processing a line is written into the array 'cache'.
	// This is a stripe of sufficient width for all threads to have each thread processing one
	// line, and some extra space if one thread is finished to start the next line.
	// This array is padded at the edges of the image so that a surrounding with radius kRadius
	// for each pixel processed is within 'cache'. Out-of-image
	// pixels are set to the value of the nearest edge pixel. When adding a new line, the lines in
	// 'cache' are not shifted but rather the smaller array with the start and end pointers of the
	// kernel area is modified to point at the addresses for the next line.
	//
	// Algorithm: For mean and variance, except for very small radius, usually do not calculate the
	// sum over all pixels. This sum is calculated for the first pixel of every line only. For the
	// following pixels, add the new values and subtract those that are not in the sum any more.
	// For min/max, also first look at the new values, use their maximum if larger than the old
	// one. The look at the values not in the area any more; if it does not contain the old
	// maximum, leave the maximum unchanged. Otherwise, determine the maximum inside the area.
	// For outliers, calculate the median only if the pixel deviates by more than the threshold
	// from any pixel in the area. Therfore min or max is calculated; this is a much faster
	// operation than the median.
	private void doFiltering(ImageProcessor ip, int[] lineRadii, float[] cache, int cacheWidth, int cacheHeight,
			int filterType, float minMaxOutliersSign, float threshold, int colorChannel,
			AtomicIntegerArray yForThread, int threadNumber, AtomicInteger nextY) {
		if (nextY.get() < 0 || Thread.currentThread().isInterrupted()) return;
		int width = ip.getWidth();
		int height = ip.getHeight();
		Rectangle roi = ip.getRoi();

		int kHeight = kHeight(lineRadii);
		int kRadius	 = kRadius(lineRadii);
		int kNPoints = kNPoints(lineRadii);

		int xmin = roi.x - kRadius;
		int xmax = roi.x + roi.width + kRadius;
		int[]cachePointers = makeCachePointers(lineRadii, cacheWidth);

		int padLeft = xmin<0 ? -xmin : 0;
		int padRight = xmax>width? xmax-width : 0;
		int xminInside = xmin>0 ? xmin : 0;
		int xmaxInside = xmax<width ? xmax : width;
		int widthInside = xmaxInside - xminInside;

		boolean minOrMax = filterType == MIN || filterType == MAX;
		boolean minOrMaxOrOutliers = minOrMax || filterType == OUTLIERS;
		boolean sumFilter = filterType == MEAN || filterType == VARIANCE;
		boolean medianFilter = filterType == MEDIAN || filterType == OUTLIERS;
		double[] sums = sumFilter ? new double[2] : null;
		float[] medianBuf1 = (medianFilter||filterType==REMOVE_NAN) ? new float[kNPoints] : null;
		float[] medianBuf2 = (medianFilter||filterType==REMOVE_NAN) ? new float[kNPoints] : null;

		boolean smallKernel = kRadius < 2;

		Object pixels = ip.getPixels();
		boolean isFloat = pixels instanceof float[];
		float maxValue = isFloat ? Float.NaN : (float)ip.maxValue();
		float[] values = isFloat ? (float[])pixels : new float[roi.width];

		int numThreads = yForThread.length();
		long lastTime = System.currentTimeMillis();
		int previousY = kHeight/2-cacheHeight;
		boolean rgb = ip instanceof ColorProcessor;

		while (true) {
			int y = nextY.getAndIncrement();		// y of the next line that needs processing
			if (y >= 0)
				yForThread.set(threadNumber, y);
			//IJ.log("thread "+threadNumber+" @y="+y+" needs"+(y-kHeight/2)+"-"+(y+kHeight/2)+" highestYinC="+highestYinCache.get());
			boolean threadFinished = y >= roi.y+roi.height || y < 0;	// y<0 if aborted
			if (numThreads>1 && (nThreadsWaiting.get()>0 || threadFinished))		// 'if' is not synchronized to avoid overhead
				//If nThreadsWaiting gets incremented to 1 after the 'if' by another thread, there will be no notifyAll.
				//This is not an issue since the the other thread rechecking for a slow thread (synchronized, ~30 lines below)
				//will not see the current thread being slow any more, so that other thread will not wait.
				synchronized(this) {
					notifyAll();					// we may have blocked another thread
					//IJ.log("thread "+threadNumber+" @y="+y+" notifying");
				}
			if (threadFinished)
				return;								// all done, break the loop

			if (threadNumber==0) {					// main thread checks for abort and ProgressBar
				long time = System.currentTimeMillis();
				if (time-lastTime>100) {
					lastTime = time;
					showProgress((y-roi.y)/(double)(roi.height), rgb);
					if (Thread.currentThread().isInterrupted() || (imp!= null && IJ.escapePressed())) {
						nextY.set(Integer.MIN_VALUE);
						synchronized(this) {notifyAll();}
						return;
					}
				}
			}

			for (int i=0; i<cachePointers.length; i++)	//shift kernel pointers to new line
				cachePointers[i] = (cachePointers[i] + cacheWidth*(y-previousY))%cache.length;
			previousY = y;

			if (numThreads>1) {							// thread synchronization
				int slowestThreadY = arrayMinNonNegative(yForThread); // non-synchronized check to avoid overhead

				if (y - slowestThreadY + kHeight > cacheHeight) {	// we would overwrite data needed by another thread
					nThreadsWaiting.incrementAndGet();
					synchronized(this) {
						slowestThreadY = arrayMinNonNegative(yForThread); //recheck whether we have to wait
						if (y - slowestThreadY + kHeight > cacheHeight) {
							do {
								//notifyAll();			// avoid deadlock: wake up others waiting
								//IJ.log("Thread "+threadNumber+" waiting @y="+y+" slowest@y="+slowestThreadY);
								try {
									wait();
									if (nextY.get() < 0) return;
								} catch (InterruptedException e) {
									nextY.set(Integer.MIN_VALUE);
									notifyAll();
									Thread.currentThread().interrupt(); //keep interrupted status (PlugInFilterRunner needs it)
									return;
								}
								slowestThreadY = arrayMinNonNegative(yForThread);
							} while (y - slowestThreadY + kHeight > cacheHeight);
						} //if
					}
					nThreadsWaiting.decrementAndGet();
				}
			}

			if (numThreads==1) {															// R E A D
				int yStartReading = y==roi.y ? Math.max(roi.y-kHeight/2, 0) : y+kHeight/2;
				for (int yNew = yStartReading; yNew<=y+kHeight/2; yNew++) { //only 1 line except at start
					readLineToCacheOrPad(pixels, width, height, roi.y, xminInside, widthInside,
							cache, cacheWidth, cacheHeight, padLeft, padRight, colorChannel, kHeight, yNew);
				}
			} else {										// if no other thread is copying or if the own thread needs the data
				if (!copyingToCache.get() || highestYinCache.get() < y+kHeight/2) synchronized(cache) {
					copyingToCache.set(true);				// copy as many new line(s) as possible into the cache
					while (highestYinCache.get() < arrayMinNonNegative(yForThread) - kHeight/2 + cacheHeight - 1) {
						int yNew = highestYinCache.get() + 1;
						readLineToCacheOrPad(pixels, width, height, roi.y, xminInside, widthInside,
							cache, cacheWidth, cacheHeight, padLeft, padRight, colorChannel, kHeight, yNew);
						highestYinCache.set(yNew);
					}
					copyingToCache.set(false);
				}
			}

			int cacheLineP = cacheWidth * (y % cacheHeight) + kRadius;	//points to pixel (roi.x, y)
			filterLine(values, width, cache, cachePointers, kNPoints, cacheLineP, roi, y,	// F I L T E R
					sums, medianBuf1, medianBuf2, minMaxOutliersSign, maxValue, isFloat, filterType,
					smallKernel, sumFilter, minOrMax, minOrMaxOrOutliers, threshold);
			if (!isFloat)		//Float images: data are written already during 'filterLine'
				writeLineToPixels(values, pixels, roi.x+y*width, roi.width, colorChannel);	// W R I T E
			//IJ.log("thread "+threadNumber+" @y="+y+" line done");
		} // while (true); loop over y (lines)
	}

	//returns the minimum of the array, which may be modified concurrently, but not less than 0
	private int arrayMinNonNegative(AtomicIntegerArray array) {
		int min = Integer.MAX_VALUE;
		for (int i=0; i<array.length(); i++) {
			int v = array.get(i);
			if (v < min) min = v;
		}
		return min<0 ? 0 : min;
	}

	private void filterLine(float[] values, int width, float[] cache, int[] cachePointers, int kNPoints, int cacheLineP, Rectangle roi, int y,
			double[] sums, float[] medianBuf1, float[] medianBuf2, float minMaxOutliersSign, float maxValue, boolean isFloat, int filterType,
			boolean smallKernel, boolean sumFilter, boolean minOrMax, boolean minOrMaxOrOutliers, float threshold) {
			int valuesP = isFloat ? roi.x+y*width : 0;
			float max = 0f;
			float median = Float.isNaN(cache[cacheLineP]) ? 0 : cache[cacheLineP];	// a first guess
			boolean fullCalculation = true;
			for (int x=0; x<roi.width; x++, valuesP++) {							// x is with respect to roi.x
				if (fullCalculation) {
					fullCalculation = smallKernel;	//for small kernel, always use the full area, not incremental algorithm
					if (minOrMaxOrOutliers)
						max = getAreaMax(cache, x, cachePointers, 0, -Float.MAX_VALUE, minMaxOutliersSign);
					if (minOrMax) {
						values[valuesP] = max*minMaxOutliersSign;
						continue;
					}
					else if (sumFilter)
						getAreaSums(cache, x, cachePointers, sums);
				} else {
					if (minOrMaxOrOutliers) {
						float newPointsMax = getSideMax(cache, x, cachePointers, true, minMaxOutliersSign);
						if (newPointsMax >= max) { //compare with previous maximum 'max'
							max = newPointsMax;
						} else {
							float removedPointsMax = getSideMax(cache, x, cachePointers, false, minMaxOutliersSign);
							if (removedPointsMax >= max)
								max = getAreaMax(cache, x, cachePointers, 1, newPointsMax, minMaxOutliersSign);
						}
						if (minOrMax) {
							values[valuesP] = max*minMaxOutliersSign;
							continue;
						}
					} else if (sumFilter) {
						addSideSums(cache, x, cachePointers, sums);
						if (Double.isNaN(sums[0])) //avoid perpetuating NaNs into remaining line
							fullCalculation = true;
					}
				}
				if (sumFilter) {
					if (filterType == MEAN)
						values[valuesP] = (float)(sums[0]/kNPoints);
					else	{// Variance: sum of squares - square of sums
						float value = (float)((sums[1] - sums[0]*sums[0]/kNPoints)/kNPoints);
						if (value>maxValue) value = maxValue;
                        if (value < 0) value = 0;   // numeric noise can cause values < 0
						values[valuesP] = value;
					}
				} else if (filterType == MEDIAN) {
					if (isFloat) {
						median = Float.isNaN(values[valuesP]) ? Float.NaN : values[valuesP]; // a first guess
						median = getNaNAwareMedian(cache, x, cachePointers, medianBuf1, medianBuf2, kNPoints, median);
					} else
						median = getMedian(cache, x, cachePointers, medianBuf1, medianBuf2, kNPoints, median);
					values[valuesP] = median;
				} else if (filterType == OUTLIERS) {
					float v = cache[cacheLineP+x];
					if (v*minMaxOutliersSign+threshold < max) {		//for low outliers: median can't be higher than max (minMaxOutliersSign is +1)
						median = getMedian(cache, x, cachePointers, medianBuf1, medianBuf2, kNPoints, median);
						if (v*minMaxOutliersSign+threshold < median*minMaxOutliersSign)
							v = median;					//beyond threshold (below if minMaxOutliersSign=+1), replace outlier by median
					}
					values[valuesP] = v;
				} else if (filterType == REMOVE_NAN) {	 //float only; then 'values' is pixels array
					if (Float.isNaN(values[valuesP]))
						values[valuesP] = getNaNAwareMedian(cache, x, cachePointers, medianBuf1, medianBuf2, kNPoints, median);
					else
						median = values[valuesP];	//initial guess for the next point
				}
			} // for x
		}

	/** Read a line into the cache (including padding in x).
	 *	If y>=height, instead of reading new data, it duplicates the line y=height-1.
	 *	If y==0, it also creates the data for y<0, as far as necessary, thus filling the cache with
	 *	more than one line (padding by duplicating the y=0 row).
	 */
	private static void readLineToCacheOrPad(Object pixels, int width, int height, int roiY, int xminInside, int widthInside,
			float[]cache, int cacheWidth, int cacheHeight, int padLeft, int padRight, int colorChannel,
			int kHeight, int y) {
		int lineInCache = y%cacheHeight;
		if (y < height) {
			readLineToCache(pixels, y*width, xminInside, widthInside,
					cache, lineInCache*cacheWidth, padLeft, padRight, colorChannel);
			if (y==0) for (int prevY = roiY-kHeight/2; prevY<0; prevY++) {	//for y<0, pad with y=0 border pixels
				int prevLineInCache = cacheHeight+prevY;
				System.arraycopy(cache, 0, cache, prevLineInCache*cacheWidth, cacheWidth);
			}
		} else
			System.arraycopy(cache, cacheWidth*((height-1)%cacheHeight), cache, lineInCache*cacheWidth, cacheWidth);
	}

	/** Read a line into the cache (includes conversion to flaot). Pad with edge pixels in x if necessary */
	private static void readLineToCache(Object pixels, int pixelLineP, int xminInside, int widthInside,
								float[] cache, int cacheLineP, int padLeft, int padRight, int colorChannel) {
		if (pixels instanceof byte[]) {
			byte[] bPixels = (byte[])pixels;
			for (int pp=pixelLineP+xminInside, cp=cacheLineP+padLeft; pp<pixelLineP+xminInside+widthInside; pp++,cp++)
				cache[cp] = bPixels[pp]&0xff;
		} else if (pixels instanceof short[]){
			short[] sPixels = (short[])pixels;
			for (int pp=pixelLineP+xminInside, cp=cacheLineP+padLeft; pp<pixelLineP+xminInside+widthInside; pp++,cp++)
				cache[cp] = sPixels[pp]&0xffff;
		} else if (pixels instanceof float[]) {
			System.arraycopy(pixels, pixelLineP+xminInside, cache, cacheLineP+padLeft, widthInside);
		} else {	//RGB
			int[] cPixels = (int[])pixels;
			int shift = 16 - 8*colorChannel;
			int byteMask = 255<<shift;
			for (int pp=pixelLineP+xminInside, cp=cacheLineP+padLeft; pp<pixelLineP+xminInside+widthInside; pp++,cp++)
				cache[cp] = (cPixels[pp]&byteMask)>>shift;
		}
		for (int cp=cacheLineP; cp<cacheLineP+padLeft; cp++)
			cache[cp] = cache[cacheLineP+padLeft];
		for (int cp=cacheLineP+padLeft+widthInside; cp<cacheLineP+padLeft+widthInside+padRight; cp++)
			cache[cp] = cache[cacheLineP+padLeft+widthInside-1];
	}

	/** Write a line to pixels arrax, converting from float (not for float data!)
	 *	No checking for overflow/underflow
	 */
	private static void writeLineToPixels(float[] values, Object pixels, int pixelP, int length, int colorChannel) {
		if (pixels instanceof byte[]) {
			byte[] bPixels = (byte[])pixels;
			for (int i=0, p=pixelP; i<length; i++,p++)
				bPixels[p] = (byte)(((int)(values[i] + 0.5f))&0xff);
		} else if (pixels instanceof short[]) {
			short[] sPixels = (short[])pixels;
			for (int i=0, p=pixelP; i<length; i++,p++)
				sPixels[p] = (short)(((int)(values[i] + 0.5f))&0xffff);
		} else {	//RGB
			int[] cPixels = (int[])pixels;
			int shift = 16 - 8*colorChannel;
			int resetMask = 0xffffffff^(0xff<<shift);
			for (int i=0, p=pixelP; i<length; i++,p++)
				cPixels[p] = (cPixels[p]&resetMask) | (((int)(values[i] + 0.5f))<<shift);
		}
	}

	/** Get max (or -min if sign=-1) within the kernel area.
	 *	@param x between 0 and cacheWidth-1
	 *	@param ignoreRight should be 0 for analyzing all data or 1 for leaving out the row at the right
	 *	@param max should be -Float.MAX_VALUE or the smallest value the maximum can be */
	private static float getAreaMax(float[] cache, int xCache0, int[] kernel, int ignoreRight, float max, float sign) {
		for (int kk=0; kk<kernel.length; kk++) {	// y within the cache stripe (we have 2 kernel pointers per cache line)
			for (int p=kernel[kk++]+xCache0; p<=kernel[kk]+xCache0-ignoreRight; p++) {
				float v = cache[p]*sign;
				if (max < v) max = v;
			}
		}
		return max;
	}

	/** Get max (or -min if sign=-1) at the right border inside or left border outside the kernel area.
	 *	x between 0 and cacheWidth-1 */
	private static float getSideMax(float[] cache, int xCache0, int[] kernel, boolean isRight, float sign) {
		float max = -Float.MAX_VALUE;
		if (!isRight) xCache0--;
		for (int kk= isRight ? 1 : 0; kk<kernel.length; kk+=2) {	// y within the cache stripe (we have 2 kernel pointers per cache line)
			float v = cache[xCache0 + kernel[kk]]*sign;
			if (max < v) max = v;
		}
		return max;
	}

	/** Get sum of values and values squared within the kernel area.
	 *	x between 0 and cacheWidth-1
	 *	Output is written to array sums[0] = sum; sums[1] = sum of squares */
	private static void getAreaSums(float[] cache, int xCache0, int[] kernel, double[] sums) {
		double sum=0, sum2=0;
		for (int kk=0; kk<kernel.length; kk++) {	// y within the cache stripe (we have 2 kernel pointers per cache line)
			for (int p=kernel[kk++]+xCache0; p<=kernel[kk]+xCache0; p++) {
				double v = cache[p];
				sum += v;
				sum2 += v*v;
			}
		}
		sums[0] = sum;
		sums[1] = sum2;
		return;
	}

	/** Add all values and values squared at the right border inside minus at the left border outside the kernal area.
	 *	Output is added or subtracted to/from array sums[0] += sum; sums[1] += sum of squares  when at
	 *	the right border, minus when at the left border */
	private static void addSideSums(float[] cache, int xCache0, int[] kernel, double[] sums) {
		double sum=0, sum2=0;
		for (int kk=0; kk<kernel.length; /*k++;k++ below*/) {
			double v = cache[kernel[kk++]+(xCache0-1)]; //this value is not in the kernel area any more
			sum -= v;
			sum2 -= v*v;
			v = cache[kernel[kk++]+xCache0];            //this value comes into the kernel area
			sum += v;
			sum2 += v*v;
		}
		sums[0] += sum;
		sums[1] += sum2;
		return;
	}

	/** Get median of values within kernel-sized neighborhood. Kernel size kNPoints should be odd.
	 */
	private static float getMedian(float[] cache, int xCache0, int[] kernel,
			float[] aboveBuf, float[]belowBuf, int kNPoints, float guess) {
		int nAbove = 0, nBelow = 0;
		for (int kk=0; kk<kernel.length; kk++) {
			for (int p=kernel[kk++]+xCache0; p<=kernel[kk]+xCache0; p++) {
				float v = cache[p];
				if (v > guess) {
					aboveBuf[nAbove] = v;
					nAbove++;
				}
				else if (v < guess) {
					belowBuf[nBelow] = v;
					nBelow++;
				}
			}
		}
		int half = kNPoints/2;
		if (nAbove>half)
			return findNthLowestNumber(aboveBuf, nAbove, nAbove-half-1);
		else if (nBelow>half)
			return findNthLowestNumber(belowBuf, nBelow, half);
		else
			return guess;
	}

	/** Get median of values within kernel-sized neighborhood.
	 *	NaN data values are ignored; the output is NaN only if there are only NaN values in the
	 *	kernel-sized neighborhood */
	private static float getNaNAwareMedian(float[] cache, int xCache0, int[] kernel,
			float[] aboveBuf, float[]belowBuf, int kNPoints, float guess) {
		int nAbove = 0, nBelow = 0;
		for (int kk=0; kk<kernel.length; kk++) {
			for (int p=kernel[kk++]+xCache0; p<=kernel[kk]+xCache0; p++) {
				float v = cache[p];
				if (Float.isNaN(v)) {
					kNPoints--;
				} else if (v > guess) {
					aboveBuf[nAbove] = v;
					nAbove++;
				}
				else if (v < guess) {
					belowBuf[nBelow] = v;
					nBelow++;
				}
			}
		}
		if (kNPoints == 0) return Float.NaN;	//only NaN data in the neighborhood?
		int half = kNPoints/2;
		if (nAbove>half)
			return findNthLowestNumber(aboveBuf, nAbove, nAbove-half-1);
		else if (nBelow>half)
			return findNthLowestNumber(belowBuf, nBelow, half);
		else
			return guess;
	}

	/** Find the n-th lowest number in part of an array
	 *	@param buf The input array. Only values 0 ... bufLength are read. <code>buf</code> will be modified.
	 *	@param bufLength Number of values in <code>buf</code> that should be read
	 *	@param n which value should be found; n=0 for the lowest, n=bufLength-1 for the highest
	 *	@return the value */
	public final static float findNthLowestNumber(float[] buf, int bufLength, int n) {
		// Hoare's find, algorithm, based on http://www.geocities.com/zabrodskyvlada/3alg.html
		// Contributed by Heinz Klar
		int i,j;
		int l=0;
		int m=bufLength-1;
		float med=buf[n];
		float dum ;

		while (l<m) {
			i=l ;
			j=m ;
			do {
				while (buf[i]<med) i++ ;
				while (med<buf[j]) j-- ;
				dum=buf[j];
				buf[j]=buf[i];
				buf[i]=dum;
				i++ ; j-- ;
			} while ((j>=n) && (i<=n)) ;
			if (j<n) l=i ;
			if (n<i) m=j ;
			med=buf[n] ;
		}
	return med ;
	}

	/** Reset region between inner rectangle 'roi' and outer rectangle 'roi1' to the snapshot */
	private void resetRoiBoundary(ImageProcessor ip, Rectangle roi, Rectangle roi1) {
		int width = ip.getWidth();
		Object pixels = ip.getPixels();
		Object snapshot = ip.getSnapshotPixels();
		for (int y=roi1.y, p = roi1.x+y*width; y<roi.y; y++,p+=width)
			System.arraycopy(snapshot, p, pixels, p, roi1.width);
		int leftWidth = roi.x - roi1.x;
		int rightWidth = roi1.x+roi1.width - (roi.x+roi.width);
		for (int y=roi.y, pL=roi1.x+y*width, pR=roi.x+roi.width+y*width; y<roi.y+roi.height; y++,pL+=width,pR+=width) {
			if (leftWidth > 0)
				System.arraycopy(snapshot, pL, pixels, pL, leftWidth);
			if (rightWidth > 0)
				System.arraycopy(snapshot, pR, pixels, pR, rightWidth);
		}
		for (int y=roi.y+roi.height, p = roi1.x+y*width; y<roi1.y+roi1.height; y++,p+=width)
			System.arraycopy(snapshot, p, pixels, p, roi1.width);
	}

	/** @deprecated
	 * Not needed any more, use the rank(ip, ...) method, which creates the kernel */
	public void makeKernel(double radius) {
		this.radius = radius;
	}

	/** Sets the display range after variance and top-hat (16-bit & floating-point images only) */
	private void setDisplayRange(ImageProcessor ip) {
		if ((ip instanceof ByteProcessor) || (ip instanceof ColorProcessor)) return;
		new ContrastEnhancer().stretchHistogram(ip, 0.5);
	}

	/** Create a circular kernel (structuring element) of a given radius.
	 *	@param radius
	 *	Radius = 0.5 includes the 4 neighbors of the pixel in the center,
	 *	radius = 1 corresponds to a 3x3 kernel size.
	 *	@return the circular kernel
	 *	The output is an array that gives the length of each line of the structuring element
	 *	(kernel) to the left (negative) and to the right (positive):
	 *	[0] left in line 0, [1] right in line 0,
	 *	[2] left in line 2, ...
	 *	The maximum (absolute) value should be kernelRadius.
	 *	Array elements at the end:
	 *	length-2: nPoints, number of pixels in the kernel area
	 *	length-1: kernelRadius in x direction (kernel width is 2*kernelRadius+1)
	 *	Kernel height can be calculated as (array length - 1)/2 (odd number);
	 *	Kernel radius in y direction is kernel height/2 (truncating integer division).
	 *	Note that kernel width and height are the same for the circular kernels used here,
	 *	but treated separately for the case of future extensions with non-circular kernels.
	 */
	protected int[] makeLineRadii(double radius) {
		if (radius>=1.5 && radius<1.75) //this code creates the same sizes as the previous RankFilters
			radius = 1.75;
		else if (radius>=2.5 && radius<2.85)
			radius = 2.85;
		int r2 = (int) (radius*radius) + 1;
		int kRadius = (int)(Math.sqrt(r2+1e-10));
		int kHeight = 2*kRadius + 1;
		int[] kernel = new int[2*kHeight + 2];
		kernel[2*kRadius]	= -kRadius;
		kernel[2*kRadius+1] =  kRadius;
		int nPoints = 2*kRadius+1;
		for (int y=1; y<=kRadius; y++) {		//lines above and below center together
			int dx = (int)(Math.sqrt(r2-y*y+1e-10));
			kernel[2*(kRadius-y)]	= -dx;
			kernel[2*(kRadius-y)+1] =  dx;
			kernel[2*(kRadius+y)]	= -dx;
			kernel[2*(kRadius+y)+1] =  dx;
			nPoints += 4*dx+2;	//2*dx+1 for each line, above&below
		}
		kernel[kernel.length-2] = nPoints;
		kernel[kernel.length-1] = kRadius;
		//for (int i=0; i<kHeight;i++)IJ.log(i+": "+kernel[2*i]+"-"+kernel[2*i+1]);
		return kernel;
	}

	//kernel height
	private int kHeight(int[] lineRadii) {
		return (lineRadii.length-2)/2;
	}

	//kernel radius in x direction. width is 2+kRadius+1
	private int kRadius(int[] lineRadii) {
		return lineRadii[lineRadii.length-1];
	}

	//number of points in kernal area
	private int kNPoints(int[] lineRadii) {
		return lineRadii[lineRadii.length-2];
	}

	//cache pointers for a given kernel
	private int[] makeCachePointers(int[] lineRadii, int cacheWidth) {
		int kRadius = kRadius(lineRadii);
		int kHeight = kHeight(lineRadii);
		int[] cachePointers = new int[2*kHeight];
		for (int i=0; i<kHeight; i++) {
			cachePointers[2*i]	 = i*cacheWidth+kRadius + lineRadii[2*i];
			cachePointers[2*i+1] = i*cacheWidth+kRadius + lineRadii[2*i+1];
		}
		return cachePointers;
	}

	void showMasks() {
		int w=150, h=150;
		ImageStack stack = new ImageStack(w, h);
		//for (double r=0.1; r<3; r+=0.01) {
		for (double r=0.5; r<50; r+=0.5) {
			ImageProcessor ip = new FloatProcessor(w,h,new int[w*h]);
			float[] pixels = (float[])ip.getPixels();
			int[] lineRadii = makeLineRadii(r);
			int kHeight = kHeight(lineRadii);
			int kRadius = kRadius(lineRadii);
			int y0 = h/2-kHeight/2;
			for (int i = 0, y = y0; i<kHeight; i++, y++)
				for (int x = w/2+lineRadii[2*i], p = x+y*w; x <= w/2+lineRadii[2*i+1]; x++, p++)
					pixels[p] = 1f;
			stack.addSlice("radius="+r+", size="+(2*kRadius+1), ip);
		}
		new ImagePlus("Masks", stack).show();
	}

	/** This method is called by ImageJ to set the number of calls to run(ip)
	 *	corresponding to 100% of the progress bar.
	 *  Setting nPasses=0 suppresses the progress bar */
	public void setNPasses (int nPasses) {
		this.nPasses = nPasses;
		pass = 0;
	}

	private void showProgress(double percent, boolean rgb) {
		if (nPasses==0) return;
		int nPasses2 = rgb?nPasses*3:nPasses;
		percent = (double)pass/nPasses2 + percent/nPasses2;
		IJ.showProgress(percent);
	}

}
