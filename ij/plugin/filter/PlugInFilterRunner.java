package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.filter.PlugInFilter.*;
import ij.plugin.filter.*;
import ij.measure.Calibration;
import ij.macro.Interpreter;
import java.awt.*;
import java.util.*;

public class PlugInFilterRunner implements Runnable, DialogListener {
	private String command;					// the command, can be but need not be the name of the PlugInFilter
	private Object theFilter;					// the instance of the PlugInFilter
	private ImagePlus imp;
	private int flags;							// the flags returned by the PlugInFilter
	private Overlay originalOverlay;		// overlay before pressing 'preview', to revert
	private boolean previewCheckboxOn;			// the state of the preview checkbox (true = on)
	private boolean bgPreviewOn;		// tells the background thread that preview is allowed
	private boolean bgKeepPreview;		// tells the background thread to keep the result of preview
	private Thread previewThread;		 // the thread of the preview
	private GenericDialog gd;			 // the dialog (if it registers here by setDialog)
	private Checkbox previewCheckbox;	 // reference to the preview Checkbox of the dialog
	private long previewTime;				// time (ms) needed for preview processing
	private boolean ipChanged;			// whether the image data have been changed
	private int processedAsPreview;			// the slice processed during preview (if non-zero)
	private Object snapshotPixels;		// the snapshot to show we have one and for undo in case of parallel actions intervening
	private Hashtable<Thread, int[]> slicesForThread;		// gives first&last slice that a given thread should process
	private Hashtable<Thread, ImageProcessor> roisForThread;// gives ROI that a given thread should process
	Hashtable sliceForThread = new Hashtable(); // here the stack slice currently processed is stored.
	private int nPasses;						// the number of calls to the run(ip) method of the filter
	private int pass;						// passes done so far
	private boolean doStack;

	/** The constructor runs a PlugInFilter or ExtendedPlugInFilter by calling its
	 * setup, run, etc. methods. For details, see the documentation of interfaces
	 * PlugInFilter and ExtendedPlugInFilter.
	 * @param theFilter The PlugInFilter to be run
	 * @param command	The command that has caused running the PlugInFilter
	 * @param arg		The argument specified for this PlugInFilter in IJ_Props.txt or in the
	 * plugins.config file of a .jar archive conatining a collection of plugins. <code>arg</code>
	 * may be a string of length zero.
	 */
	public PlugInFilterRunner(Object theFilter, String command, String arg) {
		this.theFilter = theFilter;
		this.command = command;
		imp = WindowManager.getCurrentImage();
		flags = ((PlugInFilter)theFilter).setup(arg, imp);	// S E T U P
		if ((flags&PlugInFilter.DONE)!=0) return;
		if (!checkImagePlus(imp, flags, command)) return;	// check whether the PlugInFilter can handle this image type
		if ((flags&PlugInFilter.NO_IMAGE_REQUIRED)!=0)
			imp = null;									// if the plugin does not want an image, it should not get one
		Roi roi = null;
		if (imp != null) {
			roi = imp.getRoi();
			if (roi!=null) roi.endPaste();				// prepare the image: finish previous paste operation (if any)
			if (!imp.lock())
				return;					// exit if image is in use
			nPasses = ((flags&PlugInFilter.CONVERT_TO_FLOAT)!=0) ? imp.getProcessor().getNChannels():1;
		}
		if (theFilter instanceof ExtendedPlugInFilter) { // calling showDialog required?
			try {
				flags = ((ExtendedPlugInFilter)theFilter).showDialog(imp, command, this);  // D I A L O G (may include preview)
			} catch(Exception e) {
				killPreview();
				if (Macro.MACRO_CANCELED.equals(e.getMessage()))
					throw new RuntimeException(Macro.MACRO_CANCELED);
			}
			if (snapshotPixels != null)
				Undo.setup(Undo.FILTER, imp);			// ip has a snapshot that may be used for Undo
			boolean keepPreviewFlag = (flags&ExtendedPlugInFilter.KEEP_PREVIEW)!=0;
			if (keepPreviewFlag && imp!=null && previewThread!=null && ipChanged &&
					previewCheckbox!=null && previewCheckboxOn) {
				bgKeepPreview = true;
				waitForPreviewDone();
				processedAsPreview = imp.getCurrentSlice();
			} else {
				killPreview();
				previewTime = 0;
			}
		} // if ExtendedPlugInFilter
		if ((flags&PlugInFilter.DONE)!=0) {
			if (imp != null)
				imp.unlock();
			return;
		} else if (imp==null) {
			((PlugInFilter)theFilter).run(null);		// not DONE, but NO_IMAGE_REQUIRED
			return;
		}
		/* preparing for the run(ip) method of the PlugInFilter... */
		int slices = imp.getStackSize();
		//IJ.log("processedAsPreview="+processedAsPreview+"; slices="+slices+"; doesStacks="+((flags&PlugInFilter.DOES_STACKS)!=0));
		if ((flags&PlugInFilter.PARALLELIZE_IMAGES)!=0)
			flags &= ~PlugInFilter.PARALLELIZE_STACKS;
		doStack = slices>1 && (flags&PlugInFilter.DOES_STACKS)!=0;
		imp.startTiming();
		if (doStack || processedAsPreview==0) {				// if processing during preview was not enough
			//IJ.showStatus(command + (doStack ? " (Stack)..." : "..."));
			ImageProcessor ip = imp.getProcessor();
			pass = 0;
			if (!doStack) {	   // single image
				FloatProcessor fp = null;
				prepareProcessor(ip, imp);
				announceSliceNumber(imp.getCurrentSlice());
				if (theFilter instanceof ExtendedPlugInFilter)
					((ExtendedPlugInFilter)theFilter).setNPasses(nPasses);
				if ((flags&PlugInFilter.NO_CHANGES)==0) {	// for filters modifying the image
					boolean disableUndo = Prefs.disableUndo || (flags&PlugInFilter.NO_UNDO)!=0;
					if (!disableUndo || ((ip instanceof ColorProcessor)&&WindowManager.getWindow("B&C")!=null)) {
						ip.snapshot();
						snapshotPixels = ip.getSnapshotPixels();
					}
				}
				processOneImage(ip, fp, snapshotPixels);	// may also set class variable snapshotPixels
				if ((flags&PlugInFilter.NO_CHANGES)==0) {	// (filters doing no modifications don't change undo status)
					if (snapshotPixels != null) {
						ip.setSnapshotPixels(snapshotPixels);
						Undo.setup(Undo.FILTER, imp);
					} else
						Undo.reset();
				}
				if ((flags&PlugInFilter.NO_CHANGES)==0&&(flags&PlugInFilter.KEEP_THRESHOLD)==0)
					ip.resetBinaryThreshold();
			} else {  // stack
				if ((flags&PlugInFilter.NO_UNDO_RESET)==0)
					Undo.reset();	 // no undo for processing a complete stack
				IJ.resetEscape();
				int slicesToDo = processedAsPreview!=0 ? slices-1 : slices;
				nPasses *= slicesToDo;
				if (theFilter instanceof ExtendedPlugInFilter)
					((ExtendedPlugInFilter)theFilter).setNPasses(nPasses);
				int threads = 1;
				if ((flags&PlugInFilter.PARALLELIZE_STACKS)!=0) {
					threads = Prefs.getThreads(); // multithread support for multiprocessor machines
					if (threads>slicesToDo) threads = slicesToDo;
					if (threads>1) slicesForThread = new Hashtable<Thread, int[]>(threads-1);
				}
				int startSlice = 1;
				for (int i=1; i<threads; i++) {				// setup the background threads
					int endSlice = (slicesToDo*i)/threads;
					if (processedAsPreview!=0 && processedAsPreview<=endSlice) endSlice++;
					Thread bgThread = new Thread(this, command+" "+startSlice+"-"+endSlice);
					slicesForThread.put(bgThread, new int[] {startSlice, endSlice});
					bgThread.start();
					//IJ.log("Stack: Thread for slices "+startSlice+"-"+endSlice+" started");
					startSlice = endSlice+1;
				}
				//IJ.log("Stack: Slices "+startSlice+"-"+slices+" by main thread");
				processStack(startSlice, slices);			// the current thread does the rest
				if (slicesForThread != null) {
					while (slicesForThread.size()>0) {	  // for all other threads:
						Thread theThread = (Thread)slicesForThread.keys().nextElement();
						try {
							theThread.join();				// wait until thread has finished
						} catch (InterruptedException e) {}
						slicesForThread.remove(theThread);	// and remove it from the list.
					}
				}
			}
		} // end processing:
		if ((flags&PlugInFilter.FINAL_PROCESSING)!=0 && !IJ.escapePressed())
			((PlugInFilter)theFilter).setup("final", imp);
		if (IJ.escapePressed()) {
			IJ.showStatus(command + " INTERRUPTED");
			IJ.showProgress(1,1);
		} else
			IJ.showTime(imp, imp.getStartTime()-previewTime, command + ": ", doStack?slices:1);
		IJ.showProgress(1.0);
		if (ipChanged) {
			imp.changes = true;
			imp.updateAndDraw();
		}
		ImageWindow win = imp.getWindow();
		if (win!=null) {
			win.running = false;
			win.running2 = false;
		}
		imp.unlock();
	}

	/** Process a stack or part of it. The slice given by class variable
	 *	processedAsPreview remains unchanged.
	 * @param firstSlice Slice number of the first slice to be processed
	 * @param endSlice	 Slice number of the last slice to be processed
	 */
	private void processStack(int firstSlice, int endSlice) {
		ImageStack stack = imp.getStack();
		ImageProcessor ip = stack.getProcessor(firstSlice);
		prepareProcessor(ip, imp);
		ip.setLineWidth(Line.getWidth());		//in contrast to imp.getProcessor, stack.getProcessor does not do this
		FloatProcessor fp = null;
		int slices = imp.getNSlices();
		for (int i=firstSlice; i<=endSlice; i++) {
			if (i != processedAsPreview) {
				announceSliceNumber(i);
				ip.setPixels(stack.getPixels(i));
				ip.setSliceNumber(i);
				ip.setSnapshotPixels(null);
				processOneImage(ip, fp, null);
				if (IJ.escapePressed()) {IJ.beep(); break;}
			}
		}
	}

	/** prepare an ImageProcessor by setting roi and CalibrationTable.
	 */
	private void prepareProcessor(ImageProcessor ip, ImagePlus imp) {
		ImageProcessor mask = imp.getMask();
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isArea())
			ip.setRoi(roi);
		else
			ip.setRoi((Roi)null);
		if (imp.getStackSize()>1) {
			ImageProcessor ip2 = imp.getProcessor();
			double min1 = ip2.getMinThreshold();
			double max1 = ip2.getMaxThreshold();
			double min2 = ip.getMinThreshold();
			double max2 = ip.getMaxThreshold();
			if (min1!=ImageProcessor.NO_THRESHOLD && (min1!=min2||max1!=max2))
				ip.setThreshold(min1, max1, ImageProcessor.NO_LUT_UPDATE);
		}
		//float[] cTable = imp.getCalibration().getCTable();
		//ip.setCalibrationTable(cTable);
	}

	/**
	 * Process a single image with the PlugInFilter.
	 * @param ip		The image data that should be processed
	 * @param fp		A Floatprocessor as a target for conversion to Float. May be null.
	 * @param snapshotPixels Valid snapshotPixels for the current ImageProcessor (null when processing a stack)
	 * Class variables used: flags (input), snapshotPixels (set if a snapshot of ip
	 * is taken), ipChanged (set if ip was probably changed).
	 */
	private void processOneImage(ImageProcessor ip, FloatProcessor fp, Object snapshotPixels) {
		if ((flags&PlugInFilter.PARALLELIZE_IMAGES)!=0) {
			processImageUsingThreads(ip, fp, snapshotPixels);
			return;
		}
		Thread thread = Thread.currentThread();
		boolean convertToFloat = (flags&PlugInFilter.CONVERT_TO_FLOAT)!=0 && !(ip instanceof FloatProcessor);
		boolean doMasking = (flags&PlugInFilter.SUPPORTS_MASKING)!=0 && ip.getMask() != null;
		if (snapshotPixels==null && (doMasking || ((flags&PlugInFilter.SNAPSHOT)!=0) && !convertToFloat)) {
			ip.snapshot();
			this.snapshotPixels = ip.getSnapshotPixels();
		}
		if (convertToFloat) {
			for (int i=0; i<ip.getNChannels(); i++) {
				fp = ip.toFloat(i, fp);
				fp.setSliceNumber(ip.getSliceNumber());
				if (thread.isInterrupted()) return;			// interrupt processing for preview?
				if ((flags&PlugInFilter.SNAPSHOT)!=0) fp.snapshot();
				if (doStack) IJ.showProgress(pass/(double)nPasses);
				((PlugInFilter)theFilter).run(fp);
				if (thread.isInterrupted()) return;
				//IJ.log("slice="+getSliceNumber()+" pass="+pass+"/"+nPasses);
				pass++;
				if ((flags&PlugInFilter.NO_CHANGES)==0) {
					ipChanged = true;
					ip.setPixels(i, fp);
				}
			}
		} else {
			if ((flags&PlugInFilter.NO_CHANGES)==0) ipChanged = true;
				if (doStack) IJ.showProgress(pass/(double)nPasses);
			((PlugInFilter)theFilter).run(ip);
			pass++;
		}

		if (thread.isInterrupted()) return;
		if (doMasking) {
			if (snapshotPixels != null)
				ip.setSnapshotPixels(snapshotPixels); //in case something has intervened and destroyed the snapshot
			ip.reset(ip.getMask());	 //restore image outside irregular roi
		}
   }

	private void processImageUsingThreads(ImageProcessor ip, FloatProcessor fp, Object snapshotPixels) {
		if (IJ.debugMode)
			IJ.log("using threads: "+ip.getNChannels());
		Thread thread = Thread.currentThread();
		boolean convertToFloat = (flags&PlugInFilter.CONVERT_TO_FLOAT)!=0 && !(ip instanceof FloatProcessor);
		boolean doMasking = (flags&PlugInFilter.SUPPORTS_MASKING)!=0 && ip.getMask() != null;
		if (snapshotPixels==null && (doMasking || ((flags&PlugInFilter.SNAPSHOT)!=0) && !convertToFloat)) {
			ip.snapshot();
			this.snapshotPixels = ip.getSnapshotPixels();
		}
		if (convertToFloat) {
			for (int i=0; i<ip.getNChannels(); i++) {
				fp = ip.toFloat(i, fp);
				fp.setSliceNumber(ip.getSliceNumber());
				if (thread.isInterrupted()) return;			// interrupt processing for preview?
				if ((flags&PlugInFilter.SNAPSHOT)!=0) fp.snapshot();
				if (doStack) IJ.showProgress(pass/(double)nPasses);
				processChannelUsingThreads(fp);
				if (thread.isInterrupted()) return;
				//IJ.log("slice="+getSliceNumber()+" pass="+pass+"/"+nPasses);
				if ((flags&PlugInFilter.NO_CHANGES)==0) {
					ipChanged = true;
					ip.setPixels(i, fp);
				}
			}
		} else {
			if ((flags&PlugInFilter.NO_CHANGES)==0) ipChanged = true;
				if (doStack) IJ.showProgress(pass/(double)nPasses);
			processChannelUsingThreads(ip);
		}
		if (thread.isInterrupted()) return;
		if (doMasking) {
			if (snapshotPixels != null)
				ip.setSnapshotPixels(snapshotPixels);
			ip.reset(ip.getMask());	 //restore image outside irregular roi
		}
	}

	/** process an image or a single color channel of an RGB image by splitting
	 *	into ROIs and using a separate thread for each roi */
	private void processChannelUsingThreads(ImageProcessor ip) {
		ImageProcessor mask = ip.getMask();
		Rectangle roi = ip.getRoi();
		int threads = Prefs.getThreads();
		if (IJ.debugMode)
			IJ.log("processing channel: "+threads);
		if (threads>roi.height) threads = roi.height;
		if (threads>1) roisForThread = new Hashtable<Thread, ImageProcessor>(threads-1);
		int y1 = roi.y;
		for (int i=1; i<threads; i++) {
			int y2 = roi.y+(roi.height*i)/threads-1;
			Thread bgThread = new Thread(this, command+" "+y1+"-"+y2);
			Rectangle roi2 = new Rectangle(roi.x, y1, roi.width, y2-y1+1);
			roisForThread.put(bgThread, duplicateProcessor(ip, roi2));
			bgThread.start();
			if (IJ.debugMode)
				IJ.log("  starting thread: "+y1+"-"+y2);
			y1 = y2+1;
		}
		if (IJ.debugMode)
			IJ.log("  main thread "+y1+"-"+(roi.y+roi.height));
		Rectangle roi2 = new Rectangle(roi.x, y1, roi.width, roi.y+roi.height-y1);
		((PlugInFilter)theFilter).run(duplicateProcessor(ip, roi2)); 	// current thread does the rest
		pass++;
		if (roisForThread != null) {
			for (Enumeration<Thread> en = roisForThread.keys(); en.hasMoreElements();) {
				Thread theThread = en.nextElement();
				try {
					theThread.join();		// wait until thread has finished
				} catch (InterruptedException e) { // if preview cancelled:
					interruptRoiThreads(roisForThread); //interrupt all threads and join
					Thread.currentThread().interrupt(); //restore 'interrupted' state
					break;
				}
			}
		}
		roisForThread = null;
		ip.setMask(mask);  // restore ROI
		ip.setRoi(roi);
	}

	ImageProcessor duplicateProcessor(ImageProcessor ip, Rectangle roi) {
		ImageProcessor ip2 = (ImageProcessor)ip.clone();
		ip2.setRoi(roi);
		return ip2;
	}

	/** interrupt threads processing the rois of an image and wait till they have finished */
	void interruptRoiThreads(Hashtable<Thread, ImageProcessor> roisForThread) {
		if (roisForThread==null) return;	//class variable may become null in other thread
		for (Enumeration<Thread> en = roisForThread.keys(); en.hasMoreElements();)
			((Thread)en.nextElement()).interrupt(); //interrupt all threads
		for (Enumeration<Thread> en = roisForThread.keys(); en.hasMoreElements();)
			try {
				((Thread)en.nextElement()).join();
			} catch (Exception e){}
	}

	/** test whether an ImagePlus can be processed based on the flags specified
	 *	and display an error message if not.
	 */
	private boolean checkImagePlus(ImagePlus imp, int flags, String cmd) {
		boolean imageRequired = (flags&PlugInFilter.NO_IMAGE_REQUIRED)==0;
		if (imageRequired && imp==null)
			{IJ.noImage(); return false;}
		if (imageRequired) {
			if (imp.getProcessor()==null)
				{wrongType(flags, cmd); return false;}
			int type = imp.getType();
			switch (type) {
				case ImagePlus.GRAY8:
					if ((flags&PlugInFilter.DOES_8G)==0)
					{wrongType(flags, cmd); return false;}
					break;
				case ImagePlus.COLOR_256:
					if ((flags&PlugInFilter.DOES_8C)==0)
					{wrongType(flags, cmd); return false;}
					break;
				case ImagePlus.GRAY16:
					if ((flags&PlugInFilter.DOES_16)==0)
					{wrongType(flags, cmd); return false;}
					break;
				case ImagePlus.GRAY32:
					if ((flags&PlugInFilter.DOES_32)==0)
					{wrongType(flags, cmd); return false;}
					break;
				case ImagePlus.COLOR_RGB:
					if ((flags&PlugInFilter.DOES_RGB)==0)
					{wrongType(flags, cmd); return false;}
					break;
			}
			if ((flags&PlugInFilter.ROI_REQUIRED)!=0 && imp.getRoi()==null)
			{IJ.error(cmd, "This command requires a selection"); return false;}
			if ((flags&PlugInFilter.STACK_REQUIRED)!=0 && imp.getStackSize()==1)
			{IJ.error(cmd, "This command requires a stack"); return false;}
		} // if imageRequired
		return true;
	}

	/** Display an error message, telling the allowed image types
	 */
	static void wrongType(int flags, String cmd) {
		String s = "\""+cmd+"\" requires an image of type:\n \n";
		if ((flags&PlugInFilter.DOES_8G)!=0) s +=  "	8-bit grayscale\n";
		if ((flags&PlugInFilter.DOES_8C)!=0) s +=  "	8-bit color\n";
		if ((flags&PlugInFilter.DOES_16)!=0) s +=  "	16-bit grayscale\n";
		if ((flags&PlugInFilter.DOES_32)!=0) s +=  "	32-bit (float) grayscale\n";
		if ((flags&PlugInFilter.DOES_RGB)!=0) s += "	RGB color\n";
		IJ.error(s);
	}

	/** Make the slice number accessible to the PlugInFilter by putting it
	 * into the appropriate hashtable.
	 */
	private void announceSliceNumber(int slice) {
		synchronized(sliceForThread){
			Integer number = Integer.valueOf(slice);
			sliceForThread.put(Thread.currentThread(), number);
		}
	}

	/** Return the slice number currently processed by the calling thread.
	 * @return The slice number. Returns -1 on error (when not processing).
	 */
	public int getSliceNumber() {
		synchronized(sliceForThread){
			Integer number = (Integer)sliceForThread.get(Thread.currentThread());
			return (number == null) ? -1 : number.intValue();
		}
	}

	/** The dispatcher for the background threads
	 */
	public void run() {
		Thread thread = Thread.currentThread();
		try {
			if (thread==previewThread)
				runPreview();
			else if (roisForThread!=null && roisForThread.containsKey(thread)) {
				ImageProcessor ip = (ImageProcessor)roisForThread.get(thread);
				((PlugInFilter)theFilter).run(ip);
				ip.setPixels(null);
				ip.setSnapshotPixels(null);
			} else if (slicesForThread!=null && slicesForThread.containsKey(thread)) {
				int[] range = (int[])slicesForThread.get(thread);
				processStack(range[0], range[1]);
			} else
				IJ.error("PlugInFilterRunner internal error:\nunsolicited background thread");
		} catch (Exception err) {
			if (thread==previewThread) {
				gd.previewRunning(false);
				IJ.wait(100); // needed on Macs
				previewCheckbox.setState(false);
				bgPreviewOn = false;
				previewThread = null;
			}
			String msg = ""+err;
			if (msg.indexOf(Macro.MACRO_CANCELED)==-1) {
				IJ.beep();
				IJ.log("ERROR: "+msg+"\nin "+thread.getName()+
					"\nat "+(err.getStackTrace()[0])+"\nfrom "+(err.getStackTrace()[1]));
			}
		}
	}

	/** The background thread for preview */
	private void runPreview() {
		if (IJ.debugMode)
			IJ.log("preview thread started; imp="+imp.getTitle());
		Thread thread = Thread.currentThread();
		ImageProcessor ip = imp.getProcessor();
		Roi originalRoi = imp.getRoi();
		originalOverlay = imp.getOverlay();
		FloatProcessor fp = null;
		prepareProcessor(ip, imp);
		announceSliceNumber(imp.getCurrentSlice());
		if (snapshotPixels==null && (flags&PlugInFilter.NO_CHANGES)==0) {
			ip.snapshot();
			snapshotPixels = ip.getSnapshotPixels();
		}
		boolean previewDataOk = false;
		while(bgPreviewOn) {
			if (previewCheckboxOn) gd.previewRunning(true); // visual feedback
			interruptable: {
				if (imp.getRoi() != originalRoi) {
					imp.setRoi(originalRoi);		// restore roi; the PlugInFilter may have affected it
					if (originalRoi!=null && originalRoi.isArea())
						ip.setRoi(originalRoi);
					else
						ip.setRoi((Roi)null);
				}
				if (ipChanged) {					// restore image data if necessary
					ip.setSnapshotPixels(snapshotPixels);
					ip.reset();
				}
				ipChanged = false;
				previewDataOk = false;
				long startTime = System.currentTimeMillis();
				pass = 0;
				if (theFilter instanceof ExtendedPlugInFilter)
					((ExtendedPlugInFilter)theFilter).setNPasses(nPasses); //this should reset pass in the filter
				if (thread.isInterrupted())
					break interruptable;
				//IJ.log("process preview start now");
				processOneImage(ip, fp, snapshotPixels); // P R O C E S S   (sets ipChanged)
				IJ.showProgress(1.0);
				if (thread.isInterrupted())
					break interruptable;
				previewDataOk = true;
				previewTime = System.currentTimeMillis() - startTime;
				imp.updateAndDraw();
				if (IJ.debugMode)
					IJ.log("preview processing done");
			}
			gd.previewRunning(false);				// optical feedback
			IJ.showStatus("");						//delete last status messages from processing
			synchronized(this) {
				if (!bgPreviewOn)
					break;							//thread should stop and possibly keep the data
				try {
					wait();							//wait for interrupted (don't keep preview) or notify (keep preview)
				} catch (InterruptedException e) {
					previewDataOk = false;
				}
			} // synchronized
		} // while bgPreviewOn
		if (thread.isInterrupted())
			previewDataOk = false;					//interrupted always means "don't keep preview"
		if (!previewDataOk || !bgKeepPreview) {		//no need to keep the result
			imp.setRoi(originalRoi);				//restore roi
			if (ipChanged) {						//revert the image data
				ip.setSnapshotPixels(snapshotPixels);
				ip.reset();
				ipChanged = false;
			}
		}
		imp.updateAndDraw();						//display current state of image (reset or result of preview)
		sliceForThread.remove(thread);				//no need to announce the slice number any more
	 }

	/** stop the background process responsible for preview as fast as possible
	 and wait until the preview thread has finished */
	private void killPreview() {
		if (previewThread == null) return;
		synchronized (this) {
			previewThread.interrupt();		//ask for premature finishing (interrupt first -> no keepPreview)
			bgPreviewOn = false;				//tell a possible background thread to terminate when it has finished
			if (roisForThread!=null)
				interruptRoiThreads(roisForThread);
		}
		waitForPreviewDone();
		imp.setOverlay(originalOverlay);
	}

	/** stop the background process responsible for preview and wait until the preview thread has finished */
	private void waitForPreviewDone() {
		if (previewThread.isAlive()) try {		//a NullPointerException is possible if the thread finishes in the meanwhile
			previewThread.setPriority(Thread.currentThread().getPriority());
		} catch (Exception e) {}
		synchronized (this) {
			bgPreviewOn = false;	 //tell a possible background thread to terminate
			notify();							//(but finish processing unless interrupted)
		}
		try {previewThread.join();}				//wait until the background thread is done
		catch (InterruptedException e){}
		previewThread = null;
	}

	/* set the GenericDialog gd where the preview comes from (if gd is
	 * suitable for listening, i.e., if it has a preview checkbox).
	 */
	public void setDialog(GenericDialog gd) {
		if (gd != null && imp != null) {
			previewCheckbox = gd.getPreviewCheckbox();
			if (previewCheckbox != null) {
				gd.addDialogListener(this);
				this.gd = gd;
			}
		} //IJ.log("setDialog done");
	}

	/** The listener to any change in the dialog. It is used for preview.
	 * It is invoked every time the user changes something in the dialog
	 * (except OK and cancel buttons), provided that all previous
	 * listeners (parameter checking) have returned true.
	 *
	 * @param e The event that has happened in the dialog. This method may
	 *			be also called with e=null, e.g. to start preview already
	 *			when the dialog appears.
	 * @return	Always true. (The return value determines whether the
	 *			dialog will enable the OK button)
	 */
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		if (previewCheckbox == null || imp == null) return true;
		previewCheckboxOn = previewCheckbox.getState();
		if (previewCheckboxOn  && previewThread == null) {
			bgPreviewOn = true;						//need to start a background thread for preview
			previewThread = new Thread(this, command+" Preview");
			int priority = Thread.currentThread().getPriority() - 2;
			if (priority < Thread.MIN_PRIORITY) priority = Thread.MIN_PRIORITY;
			previewThread.setPriority(priority);	//preview on lower priority than dialog
			previewThread.start();
			if (IJ.debugMode)
				IJ.log(command+" Preview thread was started");
			return true;
		}
		if (previewThread != null) {				//thread runs already
			if (!previewCheckboxOn) {				//preview toggled off
				killPreview();
				return true;
			} else
				previewThread.interrupt();	//all other changes: restart calculating preview (with new parameters)
		}
		return true;
	}

}
