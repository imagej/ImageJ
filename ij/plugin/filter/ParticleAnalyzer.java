package ij.plugin.filter;
import java.awt.*;
import java.awt.image.IndexColorModel; 
import java.util.Properties;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.Recorder;

/** Implements ImageJ's Analyze Particles command.
	<p>
	<pre>
	for each line do
	    for each pixel in this line do
	        if the pixel value is "inside" the threshold range then
	            trace the edge to mark the object
	            do the measurement
		        fill the object with a color outside the threshold range
		    else
		        continue the scan
	</pre>
*/
public class ParticleAnalyzer implements PlugInFilter {

	/** Display results in the ImageJ console. */
	public static final int SHOW_RESULTS = 1;
	
	/** Obsolete */
	public static final int SHOW_SUMMARY = 2;
	
	/** Display and image containg outlines of measured paticles. */
	public static final int SHOW_OUTLINES = 4;
	
	/** Do not measure particles touching edge of image. */
	public static final int EXCLUDE_EDGE_PARTICLES = 8;
	
	/** Display a particle size distribution histogram. */
	public static final int SHOW_SIZE_DISTRIBUTION = 16;
	
	/** Display a progress bar. */
	public static final int SHOW_PROGRESS = 32;
	
	/** Clear ImageJ console before starting. */
	public static final int CLEAR_WORKSHEET = 64;
	
	static final String OPTIONS = "ap.options";
	static final String BINS = "ap.bins";
	
	private static int staticMinSize = 1;
	private static int staticMaxSize = 999999;
	private static int staticOptions = Prefs.getInt(OPTIONS,SHOW_SUMMARY+CLEAR_WORKSHEET);
	private static int staticBins = Prefs.getInt(BINS,20);
	
	private static final int NOTHING=0,OUTLINES=1,FILLED=2;
	private static String[] showStrings = {"Nothing","Outlines","Filled"};
	private static int showChoice;
	
	protected ImagePlus imp;
	protected ResultsTable rt;
	protected Analyzer analyzer;

	private int level1, level2;
	private int minSize;
	private int maxSize;
	private int sizeBins;
	private int options;
	private int measurements;
	private Calibration calibration;
	private String arg;
	private int fillColor;
	private boolean thresholdingLUT;
	private boolean showResults,showSummary,excludeEdgeParticles,
		showSizeDistribution,resetCounter,showProgress;
	private ImageProcessor ip2;
	private int width,height;
	private int slice;
	private boolean canceled;
	private ImageStack outlines;
	
	
	/** Construct a ParticleAnalyzer.
		@param options	a flag word created by ORing SHOW_RESULTS, SHOW_SUMMARY, etc.
		@param measurements	a flag word created by ORing constants defined in the Measurements interface
		@param rt		a ResultsTable where the measurements will be stored
		@param minSize	the smallest particle size in pixels
		@param maxSize	the largest particle size in pixels
	*/
	public ParticleAnalyzer(int options, int measurements, ResultsTable rt, double minSize, double maxSize) {
		this.options = options;
		this.measurements = measurements;
		this.rt = rt;
		if (this.rt==null)
			this.rt = new ResultsTable();
		this.minSize = (int)minSize;
		this.maxSize = (int)maxSize;
		sizeBins = staticBins;
	}
	
	/** Default constructor */
	public ParticleAnalyzer() {
	}
	
	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		IJ.register(ParticleAnalyzer.class);
		if (!showDialog())
			return DONE;
		return IJ.setupDialog(imp, DOES_8G+NO_CHANGES+NO_UNDO);
	}

	public void run(ImageProcessor ip) {
		if (canceled)
			return;
		slice++;
		if (slice==1)
			imp.startTiming();
		if (imp.getStackSize()>1)
			imp.setSlice(slice);
		analyze(imp, ip);
	}
	
	/** Displays a modal options dialog. */
	public boolean showDialog() {
		GenericDialog gd = new GenericDialog("Analyze Particles");
		minSize = staticMinSize;
		maxSize = staticMaxSize;
		sizeBins = staticBins;
		options = staticOptions;
		gd.addNumericField("Minimum Size (pixels):", minSize, 0);
		gd.addNumericField("Maximum Size (pixels):", maxSize, 0);
		gd.addNumericField("Bins (2-256):", sizeBins, 0);
		gd.addChoice("Show:", showStrings, showStrings[showChoice]);
		gd.addCheckbox("Display Results", (options&SHOW_RESULTS)!=0);
		//gd.addCheckbox("Display Summary", (options&SHOW_SUMMARY)!=0);
		//gd.addCheckbox("Show Outlines", (options&SHOW_OUTLINES)!=0);
		gd.addCheckbox("Exclude Edge Particles", (options&EXCLUDE_EDGE_PARTICLES)!=0);
		gd.addCheckbox("Size Distribution", (options&SHOW_SIZE_DISTRIBUTION)!=0);
		gd.addCheckbox("Clear Worksheet", (options&CLEAR_WORKSHEET)!=0);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		minSize = (int)gd.getNextNumber();
		maxSize = (int)gd.getNextNumber();
		sizeBins = (int)gd.getNextNumber();
		if (gd.invalidNumber()) {
			IJ.error("Minimum Size, Maximum Size or Bins invalid.");
			canceled = true;
			return false;
		}
		staticMinSize = minSize;
		staticMaxSize = maxSize;
		staticBins = sizeBins;
		showChoice = gd.getNextChoiceIndex();
		if (gd.getNextBoolean())
			options |= SHOW_RESULTS; else options &= ~SHOW_RESULTS;
		//if (gd.getNextBoolean())
		//	options |= SHOW_SUMMARY; else options &= ~SHOW_SUMMARY;
		//if (gd.getNextBoolean())
		//	options |= SHOW_OUTLINES; else options &= ~SHOW_OUTLINES;
		if (gd.getNextBoolean())
			options |= EXCLUDE_EDGE_PARTICLES; else options &= ~EXCLUDE_EDGE_PARTICLES;
		if (gd.getNextBoolean())
			options |= SHOW_SIZE_DISTRIBUTION; else options &= ~SHOW_SIZE_DISTRIBUTION;
		if (gd.getNextBoolean())
			options |= CLEAR_WORKSHEET; else options &= ~CLEAR_WORKSHEET;
		staticOptions = options;
		options |= SHOW_PROGRESS;
		return true;
	}

	/** Performs particle analysis on the specified image. Returns
		false if there is an error. */
	public boolean analyze(ImagePlus imp) {
		return analyze(imp, imp.getProcessor());
	}

	/** Performs particle analysis on the specified ImagePlus and
		ImageProcessor. Returns false if there is an error. */
	public boolean analyze(ImagePlus imp, ImageProcessor ip) {
		showResults = (options&SHOW_RESULTS)!=0;
		excludeEdgeParticles = (options&EXCLUDE_EDGE_PARTICLES)!=0;
		showSizeDistribution = (options&SHOW_SIZE_DISTRIBUTION)!=0;
		resetCounter = (options&CLEAR_WORKSHEET)!=0;
		showProgress = (options&SHOW_PROGRESS)!=0;
		ip.snapshot();
		ip.setProgressBar(null);
		if (!setThresholdLevels(imp, ip))
			return false;
		width = ip.getWidth();
		height = ip.getHeight();
		if (showChoice!=NOTHING) {
			if (slice==1)
				outlines = new ImageStack(width, height);
			ip2 = new ByteProcessor(width, height);
			outlines.addSlice(null, ip2);
			ip2.setColor(Color.white);
			ip2.fill();
			ip2.setColor(Color.black);
		}
		calibration = imp.getCalibration();
		
		byte[] pixels = (byte[])ip.getPixels();
		Rectangle r = ip.getRoi();
		int[] mask = ip.getMask();
		int offset, value;
		int inc = r.height/20;
		if (inc<1) inc = 1;
		int mi = 0;
		if (rt==null) {
			rt = Analyzer.getResultsTable();
			analyzer = new Analyzer(imp);
		} else
			analyzer = new Analyzer(imp, measurements, rt);
		if (resetCounter && slice==1) {
			if (!Analyzer.resetCounter())
				return false;
		}
		ImageWindow win = imp.getWindow();
		if (win!=null)
			win.running = true;
		if (measurements==0)
			measurements = Analyzer.getMeasurements();

		for (int y=r.y; y<(r.y+r.height); y++) {
			offset = y*width;
			for (int x=r.x; x<(r.x+r.width); x++) {
				value = pixels[offset+x]&255;
				if (mask!=null && mask[mi++]!=ip.BLACK)
					value = -1;
				if (value>=level1 && value<=level2)
					analyzeParticle(x,y,imp,ip);
			}
			if (showProgress && (y%inc==0))
				IJ.showProgress((double)(y-r.y)/r.height);
			if (win!=null)
				canceled = !win.running;
			if (canceled) {
				Macro.abort();
				break;
			}
		}
		if (showProgress)
			IJ.showProgress(1.0);
		imp.killRoi();
		ip.setRoi(null);
		ip.reset();
		if (!canceled)
			showResults();
		return true;
	}
	
	boolean setThresholdLevels(ImagePlus imp, ImageProcessor ip) {
		double t1 = ip.getMinThreshold();
		double t2 = ip.getMaxThreshold();
		boolean invertedLut = imp.isInvertedLut();
		if (t1==ip.NO_THRESHOLD) {
			ImageStatistics stats = imp.getStatistics();
			if (stats.histogram[0]+stats.histogram[255]!=stats.pixelCount) {
				IJ.error("8-bit binary or thresholded image required.");
				canceled = true;
				return false;
			}
			if (invertedLut) {
				level1 = 255;
				level2 = 255;
				fillColor = 64;
			} else {
				level1 = 0;
				level2 = 0;
				fillColor = 192;
			}
		} else {
			level1 = (int)t1;
			level2 = (int)t2;
			if (level1>0)
				fillColor = 0;
			else if (level2<255)
				fillColor = 255;
			else
				return false;
		}
		return true;
	}
	
	void analyzeParticle(int x, int y,ImagePlus imp, ImageProcessor ip) {
		Wand wand = new Wand(ip);
		wand.autoOutline(x,y, level1, level2);
		if (wand.npoints==0)
			{IJ.write("wand error: "+x+" "+y); return;}
		Roi roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, imp, Roi.TRACED_ROI);
		Rectangle r = roi.getBoundingRect();
		ip.setRoi(r);
		if (r.width>1 && r.height>1)ip.setMask(roi.getMask());
		ip.setColor(fillColor);
		ImageStatistics s = new ByteStatistics(ip,measurements,calibration);
		boolean include = true;
		if (excludeEdgeParticles &&
		(r.x==0||r.y==0||r.x+r.width==width||r.y+r.height==height))
				include = false;
		int[] mask = ip.getMask();
		if (s.pixelCount>=minSize && s.pixelCount<=maxSize && include) {
			saveResults(s, roi);
			if (showChoice!=NOTHING)
				drawParticle(ip2, roi, mask);
		}
		ip.fill(mask);
	}

	/** Saves statistics for one particle in a results table. This is
		a method subclasses may want to override. */
	protected void saveResults(ImageStatistics stats, Roi roi) {
		analyzer.saveResults(stats, roi);
		if (showResults)
			analyzer.displayResults();
	}
	
	/** Draws a selected particle in a separate image.  This is
		another method subclasses may want to override. */
	protected void drawParticle(ImageProcessor ip2, Roi roi, int[] mask) {
		switch (showChoice) {
			case FILLED: drawFilledParticle(ip2, roi, mask); break;
			case OUTLINES: drawOutline(roi, rt.getCounter()); break;
			default:
		}
	}

	void drawFilledParticle(ImageProcessor ip, Roi roi, int[] mask) {
		//IJ.write(roi.getBoundingRect()+" "+mask.length);
		ip.setRoi(roi.getBoundingRect());
		ip.fill(mask);
	}

	void drawOutline(Roi roi, int count) {
		Rectangle r = roi.getBoundingRect();
		int nPoints = ((PolygonRoi)roi).getNCoordinates();
		int[] xp = ((PolygonRoi)roi).getXCoordinates();
		int[] yp = ((PolygonRoi)roi).getYCoordinates();
		int x=r.x, y=r.y;
		ip2.moveTo(x+xp[0], y+yp[0]);
		for (int i=1; i<nPoints; i++)
			ip2.lineTo(x+xp[i], y+yp[i]);
		ip2.lineTo(x+xp[0], y+yp[0]);
		String s = IJ.d2s(count,0);
		ip2.moveTo(r.x+r.width/2-ip2.getStringWidth(s)/2, r.y+r.height/2+4);
		ip2.drawString(s);
	}

	void showResults() {
		int count = rt.getCounter();
		if (count==0)
			return;
		//if (!showResults && rt==Analyzer.getResultsTable())
		//	IJ.write("threshold: "+level1+"-"+level2);
		//	IJ.write("count: "+IJ.d2s(count,0));
		//}
		if (showSizeDistribution && slice==imp.getStackSize()) {
			float[] areas = rt.getColumn(ResultsTable.AREA);
			if (areas!=null) {
				ImageProcessor ip = new FloatProcessor(count, 1, areas, null);
				new HistogramWindow("Particle Size Distribution", new ImagePlus("",ip), sizeBins);
			}
		}
		if (outlines!=null && slice==imp.getStackSize())
			new ImagePlus("Drawing of "+imp.getShortTitle(), outlines).show();
	}
	
	/** Called once when ImageJ quits. */
	public static void savePreferences(Properties prefs) {
		prefs.put(OPTIONS, Integer.toString(staticOptions));
		prefs.put(BINS, Integer.toString(staticBins));
	}

}
