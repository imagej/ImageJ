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
import ij.plugin.frame.*;
import ij.plugin.Colors;
import ij.macro.Interpreter;
import ij.util.Tools;

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
	
	/** Obsolete, replaced by  DISPLAY_SUMMARY */
	public static final int SHOW_SUMMARY = 2;
	
	/** Display image containing outlines of measured particles. */
	public static final int SHOW_OUTLINES = 4;
	
	/** Do not measure particles touching edge of image. */
	public static final int EXCLUDE_EDGE_PARTICLES = 8;
	
	/** Display image containing grayscales masks that identify measured particles. */
	public static final int SHOW_ROI_MASKS = 16;
	
	/** Display a progress bar. */
	public static final int SHOW_PROGRESS = 32;
	
	/** Clear "Results" window before starting. */
	public static final int CLEAR_WORKSHEET = 64;
	
	/** Record starting coordinates so outline can be recreated later using doWand(x,y). */
	public static final int RECORD_STARTS = 128;

	/** Display a summary. */
	public static final int DISPLAY_SUMMARY = 256;

	/** Do not display particle outline image. */
	public static final int SHOW_NONE = 512;

	/** Flood fill to ignore interior holes. */
	public static final int INCLUDE_HOLES = 1024;
	
	/** Add particles to ROI Manager. */
	public static final int ADD_TO_MANAGER = 2048;

	/** Display image containing binary masks of measured particles. */
	public static final int SHOW_MASKS = 4096;

	/** Use 4-connected particle tracing. */
	public static final int FOUR_CONNECTED = 8192;

	/** Replace original image with masks. */
	public static final int IN_SITU_SHOW = 16384;

	/** Display particle outlines as an overlay. */
	public static final int SHOW_OVERLAY_OUTLINES = 32768;
	
	/** Display filled particle as an overlay. */
	public static final int SHOW_OVERLAY_MASKS = 65536;

	static final String OPTIONS = "ap.options";
	
	static final int BYTE=0, SHORT=1, FLOAT=2, RGB=3;
	static final double DEFAULT_MIN_SIZE = 0.0;
	static final double DEFAULT_MAX_SIZE = Double.POSITIVE_INFINITY;
	
	private static double staticMinSize = 0.0;
	private static double staticMaxSize = DEFAULT_MAX_SIZE;
	private static boolean pixelUnits;
	private static int staticOptions = Prefs.getInt(OPTIONS,CLEAR_WORKSHEET);
	private static String[] showStrings = {"Nothing", "Outlines", "Bare Outlines", "Ellipses", "Masks", "Count Masks", "Overlay", "Overlay Masks"};
	private static double staticMinCircularity=0.0, staticMaxCircularity=1.0;
		
	protected static final int NOTHING=0, OUTLINES=1, BARE_OUTLINES=2, ELLIPSES=3, MASKS=4, ROI_MASKS=5,
		OVERLAY_OUTLINES=6, OVERLAY_MASKS=7;
	protected static int staticShowChoice;
	protected ImagePlus imp;
	protected ResultsTable rt;
	protected Analyzer analyzer;
	protected int slice;
	protected boolean processStack;
	protected boolean showResults,excludeEdgeParticles,showSizeDistribution,
		resetCounter,showProgress, recordStarts, displaySummary, floodFill,
		addToManager, inSituShow;
		
	private boolean showResultsTable = true;
	private boolean showSummaryTable = true;
	private double level1, level2;
	private double minSize, maxSize;
	private double minCircularity, maxCircularity;
	private int showChoice;
	private int options;
	private int measurements;
	private Calibration calibration;
	private String arg;
	private double fillColor;
	private boolean thresholdingLUT;
	private ImageProcessor drawIP;
	private int width,height;
	private boolean canceled;
	private ImageStack outlines;
	private IndexColorModel customLut;
	private int particleCount;
	private int maxParticleCount = 0;
	private int totalCount;
	private ResultsTable summaryTable;
	private Wand wand;
	private int imageType, imageType2;
	private boolean roiNeedsImage;
	private int minX, maxX, minY, maxY;
	private ImagePlus redirectImp;
	private ImageProcessor redirectIP;
	private PolygonFiller pf;
	private Roi saveRoi;
	private int saveSlice;
	private int beginningCount;
	private Rectangle r;
	private ImageProcessor mask;
	private double totalArea;
	private FloodFiller ff;
	private Polygon polygon;
	private RoiManager roiManager;
	private static RoiManager staticRoiManager;
	private static ResultsTable staticResultsTable, staticSummaryTable;
	private ImagePlus outputImage;
	private boolean hideOutputImage;
	private int roiType;
	private int wandMode = Wand.LEGACY_MODE;
	private Overlay overlay;
	boolean blackBackground;
	private static int defaultFontSize = 9;
	private static int nextFontSize = defaultFontSize;
	private static Color defaultFontColor = Color.red;
	private static Color nextFontColor = defaultFontColor;
	private static int nextLineWidth = 1;
	private int fontSize = nextFontSize;
	private Color fontColor = nextFontColor;
	private int lineWidth = nextLineWidth;
	private boolean noThreshold;
	private boolean calledByPlugin;
	private boolean hyperstack;

			
	/** Constructs a ParticleAnalyzer.
		@param options	a flag word created by Oring SHOW_RESULTS, EXCLUDE_EDGE_PARTICLES, etc.
		@param measurements a flag word created by ORing constants defined in the Measurements interface
		@param rt		a ResultsTable where the measurements will be stored
		@param minSize	the smallest particle size in pixels
		@param maxSize	the largest particle size in pixels
		@param minCirc	minimum circularity
		@param maxCirc	maximum circularity
	*/
	public ParticleAnalyzer(int options, int measurements, ResultsTable rt, double minSize, double maxSize, double minCirc, double maxCirc) {
		this.options = options;
		this.measurements = measurements;
		this.rt = rt;
		if (this.rt==null)
			this.rt = new ResultsTable();
		this.minSize = minSize;
		this.maxSize = maxSize;
		this.minCircularity = minCirc;
		this.maxCircularity = maxCirc;
		slice = 1;
		if ((options&SHOW_ROI_MASKS)!=0)
			showChoice = ROI_MASKS;
		if ((options&SHOW_OVERLAY_OUTLINES)!=0)
			showChoice = OVERLAY_OUTLINES;
		if ((options&SHOW_OVERLAY_MASKS)!=0)
			showChoice = OVERLAY_MASKS;
		if ((options&SHOW_OUTLINES)!=0)
			showChoice = OUTLINES;
		if ((options&SHOW_MASKS)!=0)
			showChoice = MASKS;
		if ((options&SHOW_NONE)!=0)
			showChoice = NOTHING;
		if ((options&FOUR_CONNECTED)!=0) {
			wandMode = Wand.FOUR_CONNECTED;
			options |= INCLUDE_HOLES;
		}
		nextFontSize = defaultFontSize;
		nextFontColor = defaultFontColor;
		nextLineWidth = 1;
		calledByPlugin = true;
	}
	
	/** Constructs a ParticleAnalyzer using the default min and max circularity values (0 and 1). */
	public ParticleAnalyzer(int options, int measurements, ResultsTable rt, double minSize, double maxSize) {
		this(options, measurements, rt, minSize, maxSize, 0.0, 1.0);
	}

	/** Default constructor */
	public ParticleAnalyzer() {
		slice = 1;
	}
	
	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		IJ.register(ParticleAnalyzer.class);
		if (imp==null) {
			IJ.noImage();
			return DONE;
		}
		if (imp.getBitDepth()==24 && !isThresholdedRGB(imp)) {
			IJ.error("Particle Analyzer",
			"RGB images must be thresholded using\n"
			+"Image>Adjust>Color Threshold.");
			return DONE;
		}
		if (!showDialog())
			return DONE;
		int baseFlags = DOES_ALL+NO_CHANGES+NO_UNDO;
		int flags = IJ.setupDialog(imp, baseFlags);
		processStack = (flags&DOES_STACKS)!=0;
		slice = 0;
		saveRoi = imp.getRoi();
		saveSlice = imp.getCurrentSlice();
		if (saveRoi!=null && saveRoi.getType()!=Roi.RECTANGLE && saveRoi.isArea())
			polygon = saveRoi.getPolygon();
		imp.startTiming();
		nextFontSize = defaultFontSize;
		nextFontColor = defaultFontColor;
		nextLineWidth = 1;
		return flags;
	}

	public void run(ImageProcessor ip) {
		if (canceled)
			return;
		slice++;
		if (imp.getStackSize()>1 && processStack)
			imp.setSlice(slice);
		if (imp.getType()==ImagePlus.COLOR_RGB) {
			ip = (ImageProcessor)imp.getProperty("Mask");
			ip.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
			ip.setRoi(imp.getRoi());
		}		
		if (!analyze(imp, ip))
			canceled = true;
		if (slice==imp.getStackSize()) {
			imp.updateAndDraw();
			if (saveRoi!=null) imp.setRoi(saveRoi);
			if (processStack) imp.setSlice(saveSlice);
		}
	}
	
	/** Displays a modal options dialog. */
	public boolean showDialog() {
		Calibration cal = imp!=null?imp.getCalibration():(new Calibration());
		double unitSquared = cal.pixelWidth*cal.pixelHeight;
		if (pixelUnits)
			unitSquared = 1.0;
		if (Macro.getOptions()!=null) {
			boolean oldMacro = updateMacroOptions();
			if (oldMacro) unitSquared = 1.0;
			staticMinSize = 0.0; staticMaxSize = DEFAULT_MAX_SIZE;
			staticMinCircularity=0.0; staticMaxCircularity=1.0;
			staticShowChoice = NOTHING;
		}
		GenericDialog gd = new GenericDialog("Analyze Particles");
		minSize = staticMinSize;
		maxSize = staticMaxSize;
		minCircularity = staticMinCircularity;
		maxCircularity = staticMaxCircularity;
		showChoice = staticShowChoice;
		if (maxSize==999999)
			maxSize = DEFAULT_MAX_SIZE;
		options = staticOptions;
		String unit = cal.getUnit();
		boolean scaled = cal.scaled();
		String units = unit+"^2";
		int places = 0;
		double cmin = minSize*unitSquared;
		if ((int)cmin!=cmin) places = 2;
		double cmax = maxSize*unitSquared;
		if ((int)cmax!=cmax && cmax!=DEFAULT_MAX_SIZE) places = 2;
		String minStr = ResultsTable.d2s(cmin,places);
		if (minStr.indexOf("-")!=-1) {
			for (int i=places; i<=6; i++) {
				minStr = ResultsTable.d2s(cmin, i);
				if (minStr.indexOf("-")==-1) break;
			}
		}
		String maxStr = ResultsTable.d2s(cmax, places);
		if (maxStr.indexOf("-")!=-1) {
			for (int i=places; i<=6; i++) {
				maxStr = ResultsTable.d2s(cmax, i);
				if (maxStr.indexOf("-")==-1) break;
			}
		}
		if (scaled)
			gd.setInsets(5, 0, 0);
		gd.addStringField("Size ("+units+"):", minStr+"-"+maxStr, 12);
		if (scaled) {
			gd.setInsets(0, 40, 5);
			gd.addCheckbox("Pixel units", pixelUnits);
		}
		gd.addStringField("Circularity:", IJ.d2s(minCircularity)+"-"+IJ.d2s(maxCircularity), 12);
		gd.addChoice("Show:", showStrings, showStrings[showChoice]);
		String[] labels = new String[8];
		boolean[] states = new boolean[8];
		labels[0]="Display results"; states[0] = (options&SHOW_RESULTS)!=0;
		labels[1]="Exclude on edges"; states[1]=(options&EXCLUDE_EDGE_PARTICLES)!=0;
		labels[2]="Clear results"; states[2]=(options&CLEAR_WORKSHEET)!=0;
		labels[3]="Include holes"; states[3]=(options&INCLUDE_HOLES)!=0;
		labels[4]="Summarize"; states[4]=(options&DISPLAY_SUMMARY)!=0;
		labels[5]="Record starts"; states[5]=false;
		labels[6]="Add to Manager"; states[6]=(options&ADD_TO_MANAGER)!=0;
		labels[7]="In_situ Show"; states[7]=(options&IN_SITU_SHOW)!=0;
		gd.addCheckboxGroup(4, 2, labels, states);
		gd.addHelp(IJ.URL+"/docs/menus/analyze.html#ap");
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
			
		gd.setSmartRecording(minSize==0.0&&maxSize==Double.POSITIVE_INFINITY);
		String size = gd.getNextString(); // min-max size
		if (scaled)
			pixelUnits = gd.getNextBoolean();
		if (pixelUnits)
			unitSquared = 1.0;
		else
			unitSquared = cal.pixelWidth*cal.pixelHeight;
		String[] minAndMax = Tools.split(size, " -");
		double mins = minAndMax.length>=1?gd.parseDouble(minAndMax[0]):0.0;
		double maxs = minAndMax.length==2?gd.parseDouble(minAndMax[1]):Double.NaN;
		minSize = Double.isNaN(mins)?DEFAULT_MIN_SIZE:mins/unitSquared;
		maxSize = Double.isNaN(maxs)?DEFAULT_MAX_SIZE:maxs/unitSquared;
		if (minSize<DEFAULT_MIN_SIZE) minSize = DEFAULT_MIN_SIZE;
		if (maxSize<minSize) maxSize = DEFAULT_MAX_SIZE;
		staticMinSize = minSize;
		staticMaxSize = maxSize;
		
		gd.setSmartRecording(minCircularity==0.0&&maxCircularity==1.0);
		minAndMax = Tools.split(gd.getNextString(), " -"); // min-max circularity
		double minc = minAndMax.length>=1?gd.parseDouble(minAndMax[0]):0.0;
		double maxc = minAndMax.length==2?gd.parseDouble(minAndMax[1]):Double.NaN;
		minCircularity = Double.isNaN(minc)?0.0:minc;
		maxCircularity = Double.isNaN(maxc)?1.0:maxc;
		if (minCircularity<0.0) minCircularity = 0.0;
		if (minCircularity>maxCircularity && maxCircularity==1.0) minCircularity = 0.0;
		if (minCircularity>maxCircularity) minCircularity = maxCircularity;
		if (maxCircularity<minCircularity) maxCircularity = minCircularity;
		if (minCircularity==1.0 && maxCircularity==1.0) minCircularity = 0.0;
		staticMinCircularity = minCircularity;
		staticMaxCircularity = maxCircularity;
		
		if (gd.invalidNumber()) {
			IJ.error("Bins invalid.");
			canceled = true;
			return false;
		}
		gd.setSmartRecording(showChoice==0);
		showChoice = gd.getNextChoiceIndex();
		gd.setSmartRecording(false);
		staticShowChoice = showChoice;
		if (gd.getNextBoolean())
			options |= SHOW_RESULTS; else options &= ~SHOW_RESULTS;
		if (gd.getNextBoolean())
			options |= EXCLUDE_EDGE_PARTICLES; else options &= ~EXCLUDE_EDGE_PARTICLES;
		if (gd.getNextBoolean())
			options |= CLEAR_WORKSHEET; else options &= ~CLEAR_WORKSHEET;
		if (gd.getNextBoolean())
			options |= INCLUDE_HOLES; else options &= ~INCLUDE_HOLES;
		if (gd.getNextBoolean())
			options |= DISPLAY_SUMMARY; else options &= ~DISPLAY_SUMMARY;
		if (gd.getNextBoolean())
			options |= RECORD_STARTS; else options &= ~RECORD_STARTS;
		if (gd.getNextBoolean())
			options |= ADD_TO_MANAGER; else options &= ~ADD_TO_MANAGER;
		if (gd.getNextBoolean())
			options |= IN_SITU_SHOW; else options &= ~IN_SITU_SHOW;
		staticOptions = options;
		options |= SHOW_PROGRESS;
		if ((options&DISPLAY_SUMMARY)!=0)
			Analyzer.setMeasurements(Analyzer.getMeasurements()|AREA);
		return true;
	}
	
	private boolean isThresholdedRGB(ImagePlus imp) {
		Object obj = imp.getProperty("Mask");
		if (obj==null || !(obj instanceof ImageProcessor))
			return false;
		ImageProcessor mask = (ImageProcessor)obj;
		return mask.getWidth()==imp.getWidth() && mask.getHeight()==imp.getHeight();
	}

	boolean updateMacroOptions() {
		String options = Macro.getOptions();
		options = options.replace("show=[Overlay Outlines]", "show=Overlay");
		Macro.setOptions(options);
		int index = options.indexOf("maximum=");
		if (index==-1)
			return false;
		index +=8;
		int len = options.length();
		while (index<len-1 && options.charAt(index)!=' ')
			index++;
		if (index==len-1)
			return false;
		int min = (int)Tools.parseDouble(Macro.getValue(options, "minimum", "1"));
		int max = (int)Tools.parseDouble(Macro.getValue(options, "maximum", "999999"));
		options = "size="+min+"-"+max+options.substring(index, len);
		Macro.setOptions(options);
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
		if (this.imp==null) this.imp = imp;
		showResults = (options&SHOW_RESULTS)!=0;
		excludeEdgeParticles = (options&EXCLUDE_EDGE_PARTICLES)!=0;
		resetCounter = (options&CLEAR_WORKSHEET)!=0;
		showProgress = (options&SHOW_PROGRESS)!=0;
		floodFill = (options&INCLUDE_HOLES)==0;
		recordStarts = (options&RECORD_STARTS)!=0;
		addToManager = (options&ADD_TO_MANAGER)!=0;
		if (staticRoiManager!=null) {
			addToManager = true;
			roiManager = staticRoiManager;
			staticRoiManager = null;
		}
		hyperstack = imp.isHyperStack();
		if (staticResultsTable!=null) {
			rt = staticResultsTable;
			staticResultsTable = null;
			showResultsTable = false;
		}
		if (staticSummaryTable!=null) {
			summaryTable = staticSummaryTable;
			staticSummaryTable = null;
			showSummaryTable = false;
		}
		displaySummary = (options&DISPLAY_SUMMARY)!=0 ||  (options&SHOW_SUMMARY)!=0;
		inSituShow = (options&IN_SITU_SHOW)!=0;
		outputImage = null;
		ip.snapshot();
		ip.setProgressBar(null);
		if (Analyzer.isRedirectImage()) {
			redirectImp = Analyzer.getRedirectImage(imp);
			if (redirectImp==null) return false;
			int depth = redirectImp.getStackSize();
			if (depth>1 && depth==imp.getStackSize()) {
				ImageStack redirectStack = redirectImp.getStack();
				redirectIP = redirectStack.getProcessor(imp.getCurrentSlice());
			} else
				redirectIP = redirectImp.getProcessor();
		} else if (imp.getType()==ImagePlus.COLOR_RGB) {
			ImagePlus original = (ImagePlus)imp.getProperty("OriginalImage");
			if (original!=null && original.getWidth()==imp.getWidth() && original.getHeight()==imp.getHeight()) {
				redirectImp = original;
				redirectIP = original.getProcessor();
			}
		}
		if (!setThresholdLevels(imp, ip))
			return false;
		width = ip.getWidth();
		height = ip.getHeight();
		if (!(showChoice==NOTHING||showChoice==OVERLAY_OUTLINES||showChoice==OVERLAY_MASKS)) {
			blackBackground = Prefs.blackBackground && inSituShow;
			if (slice==1)
				outlines = new ImageStack(width, height);
			if (showChoice==ROI_MASKS)
				drawIP = new ShortProcessor(width, height);
			else
				drawIP = new ByteProcessor(width, height);
			drawIP.setLineWidth(lineWidth);
			if (showChoice==ROI_MASKS)
				{} // Place holder for now...
			else if (showChoice==MASKS&&!blackBackground)
				drawIP.invertLut();
			else if (showChoice==OUTLINES) {
				if (!inSituShow) {
					if (customLut==null)
						makeCustomLut();
					drawIP.setColorModel(customLut);
				}
				drawIP.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
				if (fontSize>12 && inSituShow)
					drawIP.setAntialiasedText(true);
			} 
			outlines.addSlice(null, drawIP);

			if (showChoice==ROI_MASKS || blackBackground) {
				drawIP.setColor(Color.black);
				drawIP.fill();
				drawIP.setColor(Color.white);
			} else {
				drawIP.setColor(Color.white);
				drawIP.fill();
				drawIP.setColor(Color.black);
			}
		}
		calibration = redirectImp!=null?redirectImp.getCalibration():imp.getCalibration();
		
		if (measurements==0)
			measurements = Analyzer.getMeasurements();
		measurements &= ~LIMIT;	 // ignore "Limit to Threshold"
		if (rt==null) {
			Frame table = WindowManager.getFrame("Results");
			if (!showResults && table!=null) {
				rt = new ResultsTable();
				if (resetCounter && table instanceof TextWindow) {
					IJ.run("Clear Results");
					((TextWindow)table).close();
					rt = Analyzer.getResultsTable();
				}
			} else
				rt = Analyzer.getResultsTable();
		}
		analyzer = new Analyzer(imp, measurements, rt);
		if (resetCounter && slice==1 && rt.size()>0) {
			if (!Analyzer.resetCounter())
				return false;
		}
		beginningCount = Analyzer.getCounter();

		byte[] pixels = null;
		if (ip instanceof ByteProcessor)
			pixels = (byte[])ip.getPixels();
		if (r==null) {
			r = ip.getRoi();
			mask = ip.getMask();
			if (displaySummary) {
				if (mask!=null)
					totalArea = ImageStatistics.getStatistics(ip, AREA, calibration).area;
				else
					totalArea = r.width*calibration.pixelWidth*r.height*calibration.pixelHeight;
			}
		}
		minX=r.x; maxX=r.x+r.width; minY=r.y; maxY=r.y+r.height;
		if (r.width<width || r.height<height || mask!=null) {
			if (!eraseOutsideRoi(ip, r, mask)) return false;
		}
		int offset;
		double value;
		int inc = Math.max(r.height/25, 1);
		int mi = 0;
		ImageWindow win = imp.getWindow();
		if (win!=null)
			win.running = true;
		if (showChoice==ELLIPSES)
			measurements |= ELLIPSE;
		roiNeedsImage = (measurements&PERIMETER)!=0 || (measurements&SHAPE_DESCRIPTORS)!=0 || (measurements&FERET)!=0;
		particleCount = 0;
		wand = new Wand(ip);
		pf = new PolygonFiller();
		if (floodFill) {
			ImageProcessor ipf = ip.duplicate();
			ipf.setValue(fillColor);
			ff = new FloodFiller(ipf);
		}
		roiType = Wand.allPoints()?Roi.FREEROI:Roi.TRACED_ROI;

		boolean done = false;
		for (int y=r.y; y<(r.y+r.height); y++) {
			offset = y*width;
			for (int x=r.x; x<(r.x+r.width); x++) {
				if (pixels!=null)
					value = pixels[offset+x]&255;
				else if (imageType==SHORT)
					value = ip.getPixel(x, y);
				else
					value = ip.getPixelValue(x, y);
				if (value>=level1 && value<=level2 && !done) {
					analyzeParticle(x, y, imp, ip);
					done = level1==0.0&&level2==255.0&&imp.getBitDepth()==8;
				}
			}
			if (showProgress && ((y%inc)==0))
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
		if (showResults && showResultsTable && rt.size()>0)
			rt.updateResults();
		imp.deleteRoi();
		ip.resetRoi();
		ip.reset();
		if (displaySummary)
			updateSliceSummary();
		if (addToManager && roiManager!=null)
			roiManager.setEditMode(imp, true);
		maxParticleCount = (particleCount > maxParticleCount) ? particleCount : maxParticleCount;
		totalCount += particleCount;
		if (!canceled)
			showResults();
		return true;
	}
	
	void updateSliceSummary() {
		int slices = imp.getStackSize();
		if (slices==1) {
			Frame frame = WindowManager.getFrame("Summary");
			if (frame!=null && (frame instanceof TextWindow)) {
				TextWindow tw = (TextWindow)frame;
				ResultsTable table = tw.getTextPanel().getResultsTable();
				if (table!= null)
					summaryTable = table;
			}
		} else {
			Frame frame = WindowManager.getFrame("Summary of "+imp.getTitle());
			if (frame!=null && (frame instanceof TextWindow)) {
				TextWindow tw = (TextWindow)frame;
				ResultsTable table = tw.getTextPanel().getResultsTable();
				if (table!= null)
					summaryTable = table;
			}
		}
		if (summaryTable==null)
			summaryTable = new ResultsTable();
		float[] areas = rt.getColumn(ResultsTable.AREA);
		if (areas==null)
			areas = new float[0];
		String label = imp.getTitle();
		if (slices>1) {
			if (processStack)
				label = imp.getStack().getShortSliceLabel(slice);
			else
				label = imp.getStack().getShortSliceLabel(imp.getCurrentSlice());
			label = label!=null&&!label.equals("")?label:""+slice;
		}
		summaryTable.incrementCounter();
		summaryTable.addValue("Slice", label);

		double sum = 0.0;
		int start = areas.length-particleCount;
		if (start<0)
			return;
		for (int i=start; i<areas.length; i++)
			sum += areas[i];
		int places = Analyzer.getPrecision();
		Calibration cal = imp.getCalibration();
		summaryTable.addValue("Count", particleCount);
		summaryTable.addValue("Total Area", sum);
		summaryTable.addValue("Average Size", sum/particleCount);
		summaryTable.addValue("%Area", sum*100.0/totalArea);
		addMeans(areas.length>0?start:-1);
		String title = slices==1?"Summary":"Summary of "+imp.getTitle();
		if (showSummaryTable)
			summaryTable.show(title);
	}

 	void addMeans(int start) {
		if ((measurements&MEAN)!=0) addMean(ResultsTable.MEAN, start);
		if ((measurements&MODE)!=0) addMean(ResultsTable.MODE, start);
		if ((measurements&PERIMETER)!=0)
			addMean(ResultsTable.PERIMETER, start);
		if ((measurements&ELLIPSE)!=0) {
			addMean(ResultsTable.MAJOR, start);
			addMean(ResultsTable.MINOR, start);
			addMean(ResultsTable.ANGLE, start);
		}
		if ((measurements&SHAPE_DESCRIPTORS)!=0) {
			addMean(ResultsTable.CIRCULARITY, start);
			addMean(ResultsTable.SOLIDITY, start);
		}
		if ((measurements&FERET)!=0) {
			addMean(ResultsTable.FERET, start);
			addMean(ResultsTable.FERET_X, start);
			addMean(ResultsTable.FERET_Y, start);
			addMean(ResultsTable.FERET_ANGLE, start);
			addMean(ResultsTable.MIN_FERET, start);
		}
		if ((measurements&INTEGRATED_DENSITY)!=0)
			addMean(ResultsTable.INTEGRATED_DENSITY, start);
		if ((measurements&MEDIAN)!=0)
			addMean(ResultsTable.MEDIAN, start);
		if ((measurements&SKEWNESS)!=0)
			addMean(ResultsTable.SKEWNESS, start);
		if ((measurements&KURTOSIS)!=0)
			addMean(ResultsTable.KURTOSIS, start);
	}

	private void addMean(int column, int start) {
		double value = Double.NaN;
		if (start!=-1) {
			float[] c = column>=0?rt.getColumn(column):null;
			if (c!=null) {
				ImageProcessor ip = new FloatProcessor(c.length, 1, c, null);
				if (ip==null) return;
				ip.setRoi(start, 0, ip.getWidth()-start, 1);
				ip = ip.crop();
				ImageStatistics stats = new FloatStatistics(ip);
				if (stats==null)
					return;
				value = stats.mean;
			}
		}
		summaryTable.addValue(ResultsTable.getDefaultHeading(column), value);
	}

	boolean eraseOutsideRoi(ImageProcessor ip, Rectangle r, ImageProcessor mask) {
		int width = ip.getWidth();
		int height = ip.getHeight();
		ip.setRoi(r);
		if (excludeEdgeParticles && polygon!=null) {
			ImageStatistics stats = ImageStatistics.getStatistics(ip, MIN_MAX, null);
			if (fillColor>=stats.min && fillColor<=stats.max) {
				double replaceColor = level1-1.0;
				if (replaceColor<0.0 || replaceColor==fillColor) {
					replaceColor = level2+1.0;
					int maxColor = imageType==BYTE?255:65535;
					if (replaceColor>maxColor || replaceColor==fillColor) {
						IJ.error("Particle Analyzer", "Unable to remove edge particles");
						return false;
					}
				}
				for (int y=minY; y<maxY; y++) {
					for (int x=minX; x<maxX; x++) {
						int v  = ip.getPixel(x, y);
						if (v==fillColor) ip.putPixel(x, y, (int)replaceColor);
					}
				}
			}
		}
		ip.setValue(fillColor);		
		if (mask!=null) {
			mask = mask.duplicate();
			mask.invert();
			ip.fill(mask);
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
		return true;
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
		if (t1==ImageProcessor.NO_THRESHOLD) {
			noThreshold = true;
			ImageStatistics stats = imp.getStatistics();
			if (imageType!=BYTE || (stats.histogram[0]+stats.histogram[255]!=stats.pixelCount)) {
				IJ.error("Particle Analyzer",
					"A thresholded image or 8-bit binary image is\n"
					+"required. Threshold levels can be set using\n"
					+"the Image->Adjust->Threshold tool.");
				canceled = true;
				return false;
			}
			boolean threshold255 = invertedLut;
			if (Prefs.blackBackground)
				threshold255 = !threshold255;
			if (threshold255) {
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
		imageType2 = imageType;
		if (redirectIP!=null) {
			if (redirectIP instanceof ShortProcessor)
				imageType2 = SHORT;
			else if (redirectIP instanceof FloatProcessor)
				imageType2 = FLOAT;
			else if (redirectIP instanceof ColorProcessor)
				imageType2 = RGB;
			else
				imageType2 = BYTE;
		}
		return true;
	}
	
	int counter = 0;
	
	void analyzeParticle(int x, int y, ImagePlus imp, ImageProcessor ip) {
		ImageProcessor ip2 = redirectIP!=null?redirectIP:ip;
		wand.autoOutline(x, y, level1, level2, wandMode);
		if (wand.npoints==0)
			{IJ.log("wand error: "+x+" "+y); return;}
		Roi roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, roiType);
		Rectangle r = roi.getBounds();
		if (r.width>1 && r.height>1) {
			PolygonRoi proi = (PolygonRoi)roi;
			pf.setPolygon(proi.getXCoordinates(), proi.getYCoordinates(), proi.getNCoordinates());
			ip2.setMask(pf.getMask(r.width, r.height));
			if (floodFill) ff.particleAnalyzerFill(x, y, level1, level2, ip2.getMask(), r);
		}
		ip2.setRoi(r);
		ip.setValue(fillColor);
		ImageStatistics stats = getStatistics(ip2, measurements, calibration);
		boolean include = true;
		if (excludeEdgeParticles) {
			if (r.x==minX||r.y==minY||r.x+r.width==maxX||r.y+r.height==maxY)
				include = false;
			if (polygon!=null) {
				Rectangle bounds = roi.getBounds();
				int x1=bounds.x+wand.xpoints[wand.npoints-1];
				int y1=bounds.y+wand.ypoints[wand.npoints-1];
				int x2, y2;
				for (int i=0; i<wand.npoints; i++) {
					x2=bounds.x+wand.xpoints[i];
					y2=bounds.y+wand.ypoints[i];
					if (!polygon.contains(x2, y2))
						{include = false; break;}
					if ((x1==x2 && ip.getPixel(x1,y1-1)==fillColor) || (y1==y2 && ip.getPixel(x1-1,y1)==fillColor))
						{include = false; break;}
					x1=x2; y1=y2;
				}
			}
		}
		ImageProcessor mask = ip2.getMask();
		if (minCircularity>0.0 || maxCircularity!=1.0) {
			double perimeter = roi.getLength();
			double circularity = perimeter==0.0?0.0:4.0*Math.PI*(stats.pixelCount/(perimeter*perimeter));
			if (circularity>1.0 && maxCircularity<=1.0) circularity = 1.0;
			if (circularity<minCircularity || circularity>maxCircularity) include = false;
		}
		if (stats.pixelCount>=minSize && stats.pixelCount<=maxSize && include) {
			particleCount++;
			if (roiNeedsImage)
				roi.setImage(imp);
			stats.xstart=x; stats.ystart=y;
			saveResults(stats, roi);
			if (addToManager)
				addToRoiManager(roi, mask, particleCount);				
			if (showChoice!=NOTHING)
				drawParticle(drawIP, roi, stats, mask);
		}
		if (redirectIP!=null)
			ip.setRoi(r);
		ip.fill(mask);
	}

	ImageStatistics getStatistics(ImageProcessor ip, int mOptions, Calibration cal) {
		switch (imageType2) {
			case BYTE:
				return new ByteStatistics(ip, mOptions, cal);
			case SHORT:
				return new ShortStatistics(ip, mOptions, cal);
			case FLOAT:
				return new FloatStatistics(ip, mOptions, cal);
			case RGB:
				return new ColorStatistics(ip, mOptions, cal);
			default:
				return null;
		}
	}

	/** Saves statistics for one particle in a results table. This is
		a method subclasses can override. */
	protected void saveResults(ImageStatistics stats, Roi roi) {
		analyzer.saveResults(stats, roi);
		if (maxCircularity>1.0 && rt.columnExists("Circ.") && rt.getValue("Circ.", rt.size()-1)==1.0) {
			double perimeter = roi.getLength();
			double circularity = perimeter==0.0?0.0:4.0*Math.PI*(stats.pixelCount/(perimeter*perimeter));
			rt.addValue("Circ.", circularity);
		}
		if (recordStarts) {
			rt.addValue("XStart", stats.xstart);
			rt.addValue("YStart", stats.ystart);
		}
		if (showResultsTable && showResults)
			rt.addResults();
	}
	
	/** Adds the ROI to the ROI Manager. */
	private void addToRoiManager(Roi roi, ImageProcessor mask, int particleNumber) {
		if (roiManager==null) {
			if (Macro.getOptions()!=null && Interpreter.isBatchMode())
				roiManager = Interpreter.getBatchModeRoiManager();
			if (roiManager==null) {
				Frame frame = WindowManager.getFrame("ROI Manager");
				if (frame==null)
					IJ.run("ROI Manager...");
				frame = WindowManager.getFrame("ROI Manager");
				if (frame==null || !(frame instanceof RoiManager))
					{addToManager=false; return;}
				roiManager = (RoiManager)frame;
			}
			if (resetCounter)
				roiManager.runCommand("reset");
		}
		if (imp.getStackSize()>1) {
			int n = imp.getCurrentSlice();
			if (hyperstack) {
				int[] pos = imp.convertIndexToPosition(n);
				roi.setPosition(pos[0],pos[1],pos[2]);
			} else
				roi.setPosition(n);
		}
		if (lineWidth!=1)
			roi.setStrokeWidth(lineWidth);
		roiManager.add(imp, roi, particleNumber);
	}
	
	/** Draws a selected particle in a separate image.	This is
		another method subclasses may want to override. */
	protected void drawParticle(ImageProcessor drawIP, Roi roi, ImageStatistics stats, ImageProcessor mask) {
		switch (showChoice) {
			case MASKS: drawFilledParticle(drawIP, roi, mask); break;
			case OUTLINES: case BARE_OUTLINES: case OVERLAY_OUTLINES: case OVERLAY_MASKS:
				drawOutline(drawIP, roi, mask, rt.size()); break;
			case ELLIPSES: drawEllipse(drawIP, stats, rt.size()); break;
			case ROI_MASKS: drawRoiFilledParticle(drawIP, roi, mask, rt.size()); break;
			default:
		}
	}

	void drawFilledParticle(ImageProcessor ip, Roi roi, ImageProcessor mask) {
		ip.setRoi(roi.getBounds());
		ip.fill(mask);
	}

	void drawOutline(ImageProcessor ip, Roi roi, ImageProcessor mask, int count) {
		if (showChoice==OVERLAY_OUTLINES || showChoice==OVERLAY_MASKS) {
			if (overlay==null) {
				overlay = new Overlay();
				overlay.drawLabels(true);
				overlay.setLabelFont(new Font("SansSerif", Font.PLAIN, fontSize));
			}
			Roi roi2 = (Roi)roi.clone();
			roi2.setStrokeColor(Color.cyan);
			if (lineWidth!=1)
				roi2.setStrokeWidth(lineWidth);
			if (showChoice==OVERLAY_MASKS)
				roi2.setFillColor(Color.cyan);
			if (processStack || imp.getStackSize()>1) {
				int currentSlice = slice;
				if (!processStack)
					currentSlice = imp.getCurrentSlice();
				if (hyperstack) {
					int[] pos = imp.convertIndexToPosition(currentSlice);
					roi2.setPosition(pos[0],pos[1],pos[2]);
				} else
					roi2.setPosition(currentSlice);
			}
			if (showResults)
				roi2.setName(""+count);
			overlay.add(roi2);
		} else {
			Rectangle r = roi.getBounds();
			int nPoints = ((PolygonRoi)roi).getNCoordinates();
			int[] xp = ((PolygonRoi)roi).getXCoordinates();
			int[] yp = ((PolygonRoi)roi).getYCoordinates();
			int x=r.x, y=r.y;
			if (!inSituShow)
				ip.setValue(0.0);
			ip.moveTo(x+xp[0], y+yp[0]);
			for (int i=1; i<nPoints; i++)
				ip.lineTo(x+xp[i], y+yp[i]);
			ip.lineTo(x+xp[0], y+yp[0]);
			if (showChoice!=BARE_OUTLINES) {
				String s = ResultsTable.d2s(count,0);
				ip.moveTo(r.x+r.width/2-ip.getStringWidth(s)/2, r.y+r.height/2+fontSize/2);
				if (!inSituShow)
					ip.setValue(1.0);
				ip.drawString(s);
			}
		}
	}

	void drawEllipse(ImageProcessor ip, ImageStatistics stats, int count) {
		stats.drawEllipse(ip);
	}

	void drawRoiFilledParticle(ImageProcessor ip, Roi roi, ImageProcessor mask, int count) {
		int grayLevel = (count < 65535) ? count : 65535;
		ip.setValue((double) grayLevel); 
		ip.setRoi(roi.getBounds());
		ip.fill(mask);
	}

	void showResults() {
		int count = rt.size();
		// if (count==0) return;
		boolean lastSlice = !processStack||slice==imp.getStackSize();
		if ((showChoice==OVERLAY_OUTLINES||showChoice==OVERLAY_MASKS) && count>0 && (!processStack||slice==imp.getStackSize())) {
			if (processStack)
				imp.setOverlay(overlay);
			else {
				Overlay overlay0 = imp.getOverlay();
				if (overlay0==null || imp.getStackSize()==1)
					imp.setOverlay(overlay);
				else {
					for (int i=0; i<overlay.size(); i++)
						overlay0.add(overlay.get(i));
					imp.setOverlay(overlay0);
				}
			}
		}
		else if (outlines!=null && lastSlice) {
			String title = imp!=null?imp.getTitle():"Outlines";
			String prefix;
			if (showChoice == MASKS)
				prefix = "Mask of ";
			else if (showChoice == ROI_MASKS)
				prefix = "Count Masks of ";
			else
				prefix = "Drawing of ";
			outlines.update(drawIP);
			outputImage = new ImagePlus(prefix+title, outlines);
			outputImage.setCalibration(imp.getCalibration());
			if (inSituShow) {
				if (imp.getStackSize()==1 && !Recorder.record)
					Undo.setup(Undo.TRANSFORM, imp);
				ImageStack outputStack = outputImage.getStack();
				if (imp.getStackSize()>1 && outputStack.getSize()==1 && imp.getBitDepth()==8)
					imp.setProcessor(outputStack.getProcessor(1));
				else
					imp.setStack(null, outputStack);
			} else if (!hideOutputImage)
				outputImage.show();
		}
		if (showResults && !processStack) {
			if (showResultsTable && rt.size()>0) {
				TextPanel tp = IJ.getTextPanel();
				if (beginningCount>0 && tp!=null && tp.getLineCount()!=count)
					rt.show("Results");
			}
			Analyzer.firstParticle = beginningCount;
			Analyzer.lastParticle = Analyzer.getCounter()-1;
		} else
			Analyzer.firstParticle = Analyzer.lastParticle = 0;
		if (showResults && rt.size()==0 && !(IJ.isMacro()||calledByPlugin) && (!processStack||slice==imp.getStackSize())) {
			int digits = (int)level1==level1&&(int)level2==level2?0:2;
			String range = IJ.d2s(level1,digits)+"-"+IJ.d2s(level2,digits);
			String assummed = noThreshold?"assumed":"";
			IJ.showMessage("Particle Analyzer", "No particles were detected. The "+assummed+"\nthreshold ("+range+") may not be correct.");
		}
	}
	
	/** Returns the "Outlines", "Masks", "Elipses" or "Count Masks" image,
		or null if "Nothing" is selected in the "Show:" menu. */
	public ImagePlus getOutputImage() {
		return outputImage;
	}

	/** Set 'hideOutputImage' true to not display the "Show:" image. */
	public void setHideOutputImage(boolean hideOutputImage) {
		this.hideOutputImage = hideOutputImage;
	}

	/** Sets the size of the font used to label outlines in the next particle analyzer instance. */
	public static void setFontSize(int size) {
		nextFontSize = size;
	}

	/** Sets the color ("blue", "black", etc.) of the font used to label outlines in the next particle analyzer instance. */
	public static void setFontColor(String color) {
		nextFontColor = Colors.decode(color, defaultFontColor);
	}

	/** Sets the outline line width for the next ParticleAnalyzer instance. */
	public static void setLineWidth(int width) {
		nextLineWidth = width;
	}
	
	/** Sets the RoiManager to be used by the next ParticleAnalyzer 
		instance. There is a JavaScript example at
		http://imagej.nih.gov/ij/macros/js/HiddenRoiManager.js
	*/
	public static void setRoiManager(RoiManager manager) {
		staticRoiManager = manager;
	}
	
	/** Sets the ResultsTable to be used by the next  
		ParticleAnalyzer instance.	*/
	public static void setResultsTable(ResultsTable rt) {
		staticResultsTable = rt;
	}

	/** Sets the ResultsTable to be used by the next  
		ParticleAnalyzer instance for the summary.	*/
	public static void setSummaryTable(ResultsTable rt) {
		staticSummaryTable = rt;
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
		reds[1] =(byte)fontColor.getRed();
		greens[1] = (byte)fontColor.getGreen();;
		blues[1] = (byte)fontColor.getBlue();;
		customLut = new IndexColorModel(8, 256, reds, greens, blues);
	}

	/** Called once when ImageJ quits. */
	public static void savePreferences(Properties prefs) {
		prefs.put(OPTIONS, Integer.toString(staticOptions));
	}

}
