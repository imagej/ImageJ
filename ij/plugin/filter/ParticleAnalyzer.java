package ij.plugin.filter;
import java.awt.*;
import java.awt.image.IndexColorModel; 
import java.util.Properties;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.text.*;
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
public class ParticleAnalyzer implements PlugInFilter, Measurements {

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
	
	/** Record starting coordinates so outline can be recreated later using doWand(x,y). */
	public static final int RECORD_STARTS = 128;

	/** Display a summaru. */
	public static final int DISPLAY_SUMMARY = 256;

	static final String OPTIONS = "ap.options";
	static final String BINS = "ap.bins";
	
	static final int BYTE=0, SHORT=1, FLOAT=2;
	
	private static int staticMinSize = 1;
	private static int staticMaxSize = 999999;
	private static int staticOptions = Prefs.getInt(OPTIONS,CLEAR_WORKSHEET);
	private static int staticBins = Prefs.getInt(BINS,20);
	private static String[] showStrings = {"Nothing","Outlines","Filled","Ellipses"};
	
	protected static final int NOTHING=0,OUTLINES=1,FILLED=2,ELLIPSES=3;
	protected static int showChoice;
	protected ImagePlus imp;
	protected ResultsTable rt;
	protected Analyzer analyzer;
	protected int slice;
	protected boolean processStack;
	protected boolean showResults,excludeEdgeParticles,showSizeDistribution,
		resetCounter,showProgress, recordStarts, displaySummary;
		
	private double level1, level2;
	private int minSize;
	private int maxSize;
	private int sizeBins;
	private int options;
	private int measurements;
	private Calibration calibration;
	private String arg;
	private double fillColor;
	private boolean thresholdingLUT;
	private ImageProcessor ip2;
	private int width,height;
	private boolean canceled;
	private ImageStack outlines;
	private IndexColorModel customLut;
	private int particleCount;
	private int totalCount;
	private TextWindow tw;
	private Wand wand;
	private int imageType;
	private int xStartC, yStartC;
	private boolean roiNeedsImage;
	private int minX, maxX, minY, maxY;

	
	/** Construct a ParticleAnalyzer.
		@param options	a flag word created by Oring SHOW_RESULTS, EXCLUDE_EDGE_PARTICLES, etc.
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
		slice = 1;
	}
	
	/** Default constructor */
	public ParticleAnalyzer() {
		slice = 1;
	}
	
	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		IJ.register(ParticleAnalyzer.class);
		if (imp==null)
			{IJ.noImage();return DONE;}
		if (!showDialog())
			return DONE;
		int flags = IJ.setupDialog(imp, DOES_8G+DOES_16+DOES_32+NO_CHANGES+NO_UNDO);
		processStack = (flags&DOES_STACKS)!=0;
		slice = 0;
		imp.startTiming();
		return flags;
	}

	public void run(ImageProcessor ip) {
		if (canceled)
			return;
		slice++;
		if (imp.getStackSize()>1 && processStack)
			imp.setSlice(slice);
		analyze(imp, ip);
		if (slice==imp.getStackSize())
			imp.updateAndDraw();
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
		gd.addCheckbox("Clear Results Table", (options&CLEAR_WORKSHEET)!=0);
		gd.addCheckbox("Record Starts", (options&RECORD_STARTS)!=0);
		gd.addCheckbox("Summarize", (options&DISPLAY_SUMMARY)!=0);
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
		if (gd.getNextBoolean())
			options |= RECORD_STARTS; else options &= ~RECORD_STARTS;
		if (gd.getNextBoolean())
			options |= DISPLAY_SUMMARY; else options &= ~DISPLAY_SUMMARY;
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
		recordStarts = (options&RECORD_STARTS)!=0;
		displaySummary = (options&DISPLAY_SUMMARY)!=0;
		if ((options&SHOW_OUTLINES)!=0)
			showChoice = OUTLINES;
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
			if (showChoice==OUTLINES) {
				if (customLut==null)
					makeCustomLut();
				ip2.setColorModel(customLut);
				ip2.setFont(new Font("SansSerif", Font.PLAIN, 9));

			}
			outlines.addSlice(null, ip2);
			ip2.setColor(Color.white);
			ip2.fill();
			ip2.setColor(Color.black);
		}
		calibration = imp.getCalibration();
		
		byte[] pixels = null;
		if (ip instanceof ByteProcessor)
			pixels = (byte[])ip.getPixels();
		Rectangle r = ip.getRoi();
		int[] mask = ip.getMask();
		if (r.width<width || r.height<height || mask!=null)
			eraseOutsideRoi(ip, r, mask);
		minX=r.x; maxX=r.x+r.width; minY=r.y; maxY=r.y+r.height;
		int offset;
		double value;
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
		if (recordStarts) {
			xStartC = getColumnID("XStart");
			yStartC = getColumnID("YStart");
		}
		ImageWindow win = imp.getWindow();
		if (win!=null)
			win.running = true;
		if (measurements==0)
			measurements = Analyzer.getMeasurements();
		if (showChoice==ELLIPSES)
			measurements |= ELLIPSE;
		roiNeedsImage = (measurements&PERIMETER)!=0 || (measurements&CIRCULARITY)!=0 || (measurements&FERET)!=0;
		particleCount = 0;
		wand = new Wand(ip);

		for (int y=r.y; y<(r.y+r.height); y++) {
			offset = y*width;
			for (int x=r.x; x<(r.x+r.width); x++) {
				if (pixels!=null)
					value = pixels[offset+x]&255;
				else
					value = ip.getPixelValue(x, y);
				//if (mask!=null && mask[mi++]!=ip.BLACK)
				//	value = -Double.MAX_VALUE;
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
		ip.resetRoi();
		ip.reset();
		if (processStack && IJ.getInstance()!=null) {
			String aLine = slice+"\t"+particleCount;
			if (tw==null) {
				String title = "Counts of "+imp.getShortTitle();
				String headings = "Slice\tCount";
				tw = new TextWindow(title, headings, aLine, 180, 360);
			} else
				tw.append(aLine);
		}
		totalCount += particleCount;
		if (!canceled)
			showResults();
		return true;
	}
	
	void eraseOutsideRoi(ImageProcessor ip, Rectangle r, int[] mask) {
		int width = ip.getWidth();
		int height = ip.getHeight();
		ip.setValue(fillColor);		
		if (mask!=null) {
 			int[] invertedMask = new int[mask.length];
 			for (int i=0; i<mask.length; i++) {
 				if (mask[i]==ImageProcessor.BLACK)
 					invertedMask[i] = 0xFFFFFFFF;
 				else
 					invertedMask[i] = ImageProcessor.BLACK;
 			}
 			ip.setMask(invertedMask);
			ip.fill();
 			ip.reset(invertedMask);
 		} 		
 		ip.setRoi(0, 0, r.x, height);
 		ip.fill();
 		ip.setRoi(r.x, 0, r.width, r.y);
 		ip.fill();
 		ip.setRoi(r.x, r.y+r.height, r.width, height-(r.y+r.height));
 		ip.fill();
 		ip.setRoi(r.x+r.width, 0, width-(r.x+r.width), height);
 		ip.fill();
 		ip.resetRoi();
	}

	boolean setThresholdLevels(ImagePlus imp, ImageProcessor ip) {
		double t1 = ip.getMinThreshold();
		double t2 = ip.getMaxThreshold();
		boolean invertedLut = imp.isInvertedLut();
		boolean byteImage = ip instanceof ByteProcessor;
		if (ip instanceof ShortProcessor)
			imageType = SHORT;
		else if (ip instanceof FloatProcessor)
			imageType = FLOAT;
		else
			imageType = BYTE;
		if (t1==ip.NO_THRESHOLD) {
			ImageStatistics stats = imp.getStatistics();
			if (imageType!=BYTE || (stats.histogram[0]+stats.histogram[255]!=stats.pixelCount)) {
				IJ.showMessage("Particle Analyzer",
					"A thresholded image or an 8-bit binary image is\n"
					+"required. Refer to Image->Adjust->Threshold\n"
					+"or to Process->Binary->Threshold.");
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
			level1 = t1;
			level2 = t2;
			if (imageType==BYTE) {
				if (level1>0)
					fillColor = 0;
				else if (level2<255)
					fillColor = 255;
			} else if (imageType==SHORT) {
				if (level1>0)
					fillColor = 0;
				else if (level2<65535)
					fillColor = 65535;
			} else if (imageType==FLOAT)
					fillColor = -Float.MAX_VALUE;
			else
				return false;
		}
		return true;
	}
	
	void analyzeParticle(int x, int y,ImagePlus imp, ImageProcessor ip) {
		//Wand wand = new Wand(ip);
		wand.autoOutline(x,y, level1, level2);
		if (wand.npoints==0)
			{IJ.log("wand error: "+x+" "+y); return;}
		Roi roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, Roi.TRACED_ROI);
		Rectangle r = roi.getBoundingRect();
		if (r.width>1 && r.height>1)ip.setMask(roi.getMask());
		ip.setRoi(r);
		ip.setValue(fillColor);
		ImageStatistics stats = getStatistics(ip,measurements,calibration);
		boolean include = true;
		if (excludeEdgeParticles &&
		(r.x==minX||r.y==minY||r.x+r.width==maxX||r.y+r.height==maxY))
				include = false;
		int[] mask = ip.getMask();
		if (stats.pixelCount>=minSize && stats.pixelCount<=maxSize && include) {
			particleCount++;
			if (roiNeedsImage)
				roi.setImage(imp);
			saveResults(stats, roi);
			if (showChoice!=NOTHING)
				drawParticle(ip2, roi, stats, mask);
		}
		ip.fill(mask);
	}

    ImageStatistics getStatistics(ImageProcessor ip, int mOptions, Calibration cal) {
    	switch (imageType) {
        	case BYTE:
            	return new ByteStatistics(ip, mOptions, cal);
        	case SHORT:
            	return new ShortStatistics(ip, mOptions, cal);
            case FLOAT:
            	return new FloatStatistics(ip, mOptions, cal);
			default:
				return null;
		}
    }

	/** Saves statistics for one particle in a results table. This is
		a method subclasses may want to override. */
	protected void saveResults(ImageStatistics stats, Roi roi) {
		analyzer.saveResults(stats, roi);
		if (recordStarts) {
			int coordinates = ((PolygonRoi)roi).getNCoordinates();
			Rectangle r = roi.getBoundingRect();
			int x = r.x+((PolygonRoi)roi).getXCoordinates()[coordinates-1];
			int y = r.y+((PolygonRoi)roi).getYCoordinates()[coordinates-1];
			rt.addValue(xStartC, x);
			rt.addValue(yStartC, y);
		}
		if (showResults)
			analyzer.displayResults();
	}
	
	/** Draws a selected particle in a separate image.  This is
		another method subclasses may want to override. */
	protected void drawParticle(ImageProcessor ip2, Roi roi,
	ImageStatistics stats, int[] mask) {
		switch (showChoice) {
			case FILLED: drawFilledParticle(ip2, roi, mask); break;
			case OUTLINES: drawOutline(ip2, roi, rt.getCounter()); break;
			case ELLIPSES: drawEllipse(ip2, stats, rt.getCounter()); break;
			default:
		}
	}

	void drawFilledParticle(ImageProcessor ip, Roi roi, int[] mask) {
		//IJ.write(roi.getBoundingRect()+" "+mask.length);
		ip.setRoi(roi.getBoundingRect());
		ip.fill(mask);
	}

	void drawOutline(ImageProcessor ip, Roi roi, int count) {
		Rectangle r = roi.getBoundingRect();
		int nPoints = ((PolygonRoi)roi).getNCoordinates();
		int[] xp = ((PolygonRoi)roi).getXCoordinates();
		int[] yp = ((PolygonRoi)roi).getYCoordinates();
		int x=r.x, y=r.y;
		ip.setValue(0.0);
		ip.moveTo(x+xp[0], y+yp[0]);
		for (int i=1; i<nPoints; i++)
			ip.lineTo(x+xp[i], y+yp[i]);
		ip.lineTo(x+xp[0], y+yp[0]);
		String s = IJ.d2s(count,0);
		ip.moveTo(r.x+r.width/2-ip.getStringWidth(s)/2, r.y+r.height/2+4);
		ip.setValue(1.0);
		ip.drawString(s);
	}

	void drawEllipse(ImageProcessor ip, ImageStatistics stats, int count) {
		stats.drawEllipse(ip);
	}

	void showResults() {
		int count = rt.getCounter();
		if (count==0)
			return;
		boolean lastSlice = !processStack||slice==imp.getStackSize();
		if (displaySummary && lastSlice && rt==Analyzer.getResultsTable() && imp!=null) {
			showSummary();
		}
		if (showSizeDistribution && lastSlice) {
			float[] areas = rt.getColumn(ResultsTable.AREA);
			if (areas!=null) {
				ImageProcessor ip = new FloatProcessor(count, 1, areas, null);
				new HistogramWindow("Particle Size Distribution", new ImagePlus("",ip), sizeBins);
			}
		}
		if (outlines!=null && lastSlice) {
			String title = imp!=null?imp.getShortTitle():"Outlines";
			new ImagePlus("Drawing of "+title, outlines).show();
		}
	}
	
	void showSummary() {
		String s = "";
		s += "Threshold: ";
		if ((int)level1==level1 && (int)level2==level2)
			s += (int)level1+"-"+(int)level2+"\n";
		else
			s += IJ.d2s(level1,2)+"-"+IJ.d2s(level2,2)+"\n";
		s += "Count: " + totalCount+"\n";
		float[] areas = rt.getColumn(ResultsTable.AREA);
		if (areas!=null) {
			if (areas.length!=totalCount)
				return;
			double sum = 0.0;
			for (int i=0; i<totalCount; i++)
				sum += areas[i];
			int places = Analyzer.getPrecision();
			Calibration cal = imp.getCalibration();
			String unit = cal.getUnit();
			s += "Total Area: "+IJ.d2s(sum,places)+" "+unit+"^2\n";
			s += "Average Size: "+IJ.d2s(sum/totalCount,places)+" "+unit+"^2\n";
			double totalArea = imp.getWidth()*cal.pixelWidth*imp.getHeight()*cal.pixelHeight*imp.getStackSize();
			s += "Area Fraction: "+IJ.d2s(sum*100.0/totalArea,1)+"%";
		}
		new TextWindow("Summary of "+imp.getTitle(), s, 300, 200);
	}

	int getColumnID(String name) {
		int id = rt.getFreeColumn(name);
		if (id==ResultsTable.COLUMN_IN_USE)
			id = rt.getColumnIndex(name);
		return id;
	}

	void makeCustomLut() {
		IndexColorModel cm = (IndexColorModel)LookUpTable.createGrayscaleColorModel(false);
		byte[] reds = new byte[256];
		byte[] greens = new byte[256];
		byte[] blues = new byte[256];
		cm.getReds(reds);
		cm.getGreens(greens);
		cm.getBlues(blues);
		reds[1] =(byte) 255;
		greens[1] = (byte)0;
		blues[1] = (byte)0;
		customLut = new IndexColorModel(8, 256, reds, greens, blues);
	}

	/** Called once when ImageJ quits. */
	public static void savePreferences(Properties prefs) {
		prefs.put(OPTIONS, Integer.toString(staticOptions));
		prefs.put(BINS, Integer.toString(staticBins));
	}

}
