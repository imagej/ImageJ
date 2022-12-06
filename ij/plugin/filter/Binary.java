package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.frame.ThresholdAdjuster;
import java.awt.*;

/** Implements the Erode, Dilate, Open, Close, Outline, Skeletonize
    and Fill Holes commands in the Process/Binary submenu. 
    Gabriel Landini contributed the clever binary fill algorithm
    that fills holes in objects by filling the background.
    Version 2009-06-23 preview added, interations can be aborted by escape (Michael Schmid)
*/
public class Binary implements ExtendedPlugInFilter, DialogListener {
    static final int MAX_ITERATIONS = 100;
    static final String NO_OPERATION = "Nothing";
    static final String[] outputTypes = {"Overwrite", "8-bit", "16-bit", "32-bit"};
    static final String[] operations = {NO_OPERATION, "Erode", "Dilate", "Open", "Close", "Outline", "Fill Holes", "Skeletonize"};
	static final String COUNT_KEY = "binary.count";

    //parameters / options
    static int iterations = 1;      //iterations for erode, dilate, open, close
    static int count = (int)Prefs.get(COUNT_KEY, 1); //nearest neighbor count for erode, dilate, open, close
    String operation = NO_OPERATION;  //for dialog; will be copied to 'arg' for actual previewing

    String arg;
    ImagePlus imp;                  //null if only setting options with no preview possibility
    PlugInFilterRunner pfr;
    boolean doOptions;              //whether options dialog is required
    boolean previewing;
    boolean escapePressed;
    int foreground, background;
    int flags = DOES_8G | DOES_8C | SUPPORTS_MASKING | PARALLELIZE_STACKS | KEEP_PREVIEW | KEEP_THRESHOLD;
    int nPasses;
    double medianRadius = 3;

    public int setup(String arg, ImagePlus imp) {
        this.arg = arg;
        IJ.register(Binary.class);
        doOptions = arg.equals("options");
        if (doOptions) {
            if (imp == null) return NO_IMAGE_REQUIRED;  //options dialog does not need a (suitable) image
            ImageProcessor ip = imp.getProcessor();
            if (!(ip instanceof ByteProcessor)) return NO_IMAGE_REQUIRED;
            if (!((ByteProcessor)ip).isBinary()) return NO_IMAGE_REQUIRED;
        }
        if (arg.equals("median"))
			medianRadius = IJ.getNumber("Radius:", medianRadius);
        return flags;
    }

    public int showDialog (ImagePlus imp, String command, PlugInFilterRunner pfr) {
        if (doOptions) {
            this.imp = imp;
            this.pfr = pfr;
            if (count<1) count=1;
            if (count>8) count=8;
            GenericDialog gd = new GenericDialog("Binary Options");
            gd.addNumericField("Iterations (1-"+MAX_ITERATIONS+"):", iterations, 0, 3, "");
            gd.addNumericField("Count (1-8):", count, 0, 3, "");
            gd.addCheckbox("Black background", Prefs.blackBackground);
            gd.addCheckbox("Pad edges when eroding", Prefs.padEdges);
            gd.addChoice("EDM output:", outputTypes, outputTypes[EDM.getOutputType()]);
            if (imp!=null) {
                gd.addChoice("Do:", operations, operation);
                gd.addPreviewCheckbox(pfr);
                gd.addDialogListener(this);
                previewing = true;
            }
            gd.addHelp(IJ.URL+"/docs/menus/process.html#options");
            gd.showDialog();
            previewing = false;
            if (gd.wasCanceled())
            	return DONE;
            Prefs.set(COUNT_KEY, count);
            if (imp==null) {                 //options dialog only, no do/preview
                dialogItemChanged(gd, null); //read dialog result
                return DONE;
            }
            return operation.equals(NO_OPERATION) ? DONE : IJ.setupDialog(imp, flags);
        } else {   //no dialog, 'arg' is operation type
            if (!((ByteProcessor)imp.getProcessor()).isBinary()) {
                IJ.error("8-bit binary (black and white only) image required.");
                return DONE;
            }
            return IJ.setupDialog(imp, flags);
        }
    }

    public boolean dialogItemChanged (GenericDialog gd, AWTEvent e) {
        iterations = (int)gd.getNextNumber();
        count = (int)gd.getNextNumber();
		boolean bb = Prefs.blackBackground;
        Prefs.blackBackground = gd.getNextBoolean();
        if (Prefs.blackBackground!=bb)
        	ThresholdAdjuster.update();
        Prefs.padEdges = gd.getNextBoolean();
        gd.setSmartRecording(EDM.getOutputType()==0);
        EDM.setOutputType(gd.getNextChoiceIndex());
        gd.setSmartRecording(false);
        boolean isInvalid = gd.invalidNumber();
        if (iterations<1) {iterations = 1; isInvalid = true;}
        if (iterations>MAX_ITERATIONS) {iterations = MAX_ITERATIONS; isInvalid = true;}
        if (count < 1)    {count = 1; isInvalid = true;}
        if (count > 8)    {count = 8; isInvalid = true;}
        if (isInvalid) return false;
        if (imp != null) {
            operation = gd.getNextChoice();
            arg = operation.toLowerCase();
        }
        return true;
    }

    public void setNPasses (int nPasses) {
    	this.nPasses = nPasses;
    }

    public void run (ImageProcessor ip) {
        int fg = Prefs.blackBackground ? 255 : 0;
        foreground = ip.isInvertedLut() ? 255-fg : fg;
        background = 255 - foreground;
        ip.setSnapshotCopyMode(true);
        if (arg.equals("median"))
            median(ip);
        else if (arg.equals("outline"))
            outline(ip);
        else if (arg.startsWith("fill"))
            fill(ip, foreground, background);
        else if (arg.startsWith("skel")) {
            ip.resetRoi();
            skeletonize(ip);
        } else if (arg.equals("erode") || arg.equals("dilate"))
            doIterations((ByteProcessor)ip, arg);
        else if (arg.equals("open")) {
            doIterations(ip, "erode");
            doIterations(ip, "dilate");
        } else if (arg.equals("close")) {
            doIterations(ip, "dilate");
            doIterations(ip, "erode");
        }
        ip.setSnapshotCopyMode(false);
        ip.setBinaryThreshold();
    }

    void doIterations (ImageProcessor ip, String mode) {
        if (escapePressed) return;
        if (!previewing && iterations>1)
            IJ.showStatus(arg+"... press ESC to cancel");
        for (int i=0; i<iterations; i++) {
            if (Thread.currentThread().isInterrupted()) return;
            if (IJ.escapePressed()) {
                escapePressed = true;
                ip.reset();
                return;
            }
            if (mode.equals("erode"))
                ((ByteProcessor)ip).erode(count, background);
            else
                ((ByteProcessor)ip).dilate(count, background);
        }
    }
    
    void outline(ImageProcessor ip) {
        if (Prefs.blackBackground) ip.invert();
        ((ByteProcessor)ip).outline();
        if (Prefs.blackBackground) ip.invert();
    }

    void median(ImageProcessor ip) {
		new RankFilters().rank(ip, medianRadius, RankFilters.MEAN);
		ip.threshold(128);
	}

	void skeletonize(ImageProcessor ip) {
		int fg = Prefs.blackBackground?255:0;
		if (ip.isInvertedLut())
			fg = 255-fg;
		((ByteProcessor)ip).skeletonize(fg);
	}

    // Binary fill by Gabriel Landini, G.Landini at bham.ac.uk
    // 21/May/2008
    void fill(ImageProcessor ip, int foreground, int background) {
        int width = ip.getWidth();
        int height = ip.getHeight();
        FloodFiller ff = new FloodFiller(ip);
        ip.setColor(127);
        for (int y=0; y<height; y++) {
            if (ip.getPixel(0,y)==background) ff.fill(0, y);
            if (ip.getPixel(width-1,y)==background) ff.fill(width-1, y);
        }
        for (int x=0; x<width; x++){
            if (ip.getPixel(x,0)==background) ff.fill(x, 0);
            if (ip.getPixel(x,height-1)==background) ff.fill(x, height-1);
        }
        byte[] pixels = (byte[])ip.getPixels();
        int n = width*height;
        for (int i=0; i<n; i++) {
        if (pixels[i]==127)
            pixels[i] = (byte)background;
        else
            pixels[i] = (byte)foreground;
        }
    }

}
