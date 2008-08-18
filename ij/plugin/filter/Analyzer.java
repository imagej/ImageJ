package ij.plugin.filter;
import java.awt.*;
import java.util.Vector;
import java.util.Properties;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.text.*;
import ij.plugin.MeasurementsWriter;
import ij.util.Tools;
import ij.macro.Interpreter;

/** This plugin implements ImageJ's Analyze/Measure and Analyze/Set Measurements commands. */
public class Analyzer implements PlugInFilter, Measurements {
	
	private String arg;
	private ImagePlus imp;
	private ResultsTable rt;
	private int measurements;
	private StringBuffer min,max,mean,sd;
	
	// Order must agree with order of checkboxes in Set Measurements dialog box
	private static final int[] list = {AREA,MEAN,STD_DEV,MODE,MIN_MAX,
		CENTROID,CENTER_OF_MASS,PERIMETER,RECT,ELLIPSE,CIRCULARITY, FERET,
		INTEGRATED_DENSITY,MEDIAN,SKEWNESS,KURTOSIS,AREA_FRACTION,SLICE,
		LIMIT,LABELS,INVERT_Y};

	private static final int UNDEFINED=0,AREAS=1,LENGTHS=2,ANGLES=3,POINTS=4;
	private static int mode = AREAS;
	private static final String MEASUREMENTS = "measurements";
	private static final String MARK_WIDTH = "mark.width";
	private static final String PRECISION = "precision";
	//private static int counter;
	private static boolean unsavedMeasurements;
	public static Color darkBlue = new Color(0,0,160);
	private static int systemMeasurements = Prefs.getInt(MEASUREMENTS,AREA+MEAN+MIN_MAX);
	public static int markWidth = Prefs.getInt(MARK_WIDTH,0);
	public static int precision = Prefs.getInt(PRECISION,3);
	private static float[] umeans = new float[MAX_STANDARDS];
	private static ResultsTable systemRT = new ResultsTable();
	private static int redirectTarget;
	private static String redirectTitle = "";
	static int firstParticle, lastParticle;
	private static boolean summarized;
	private static boolean switchingModes;
	
	public Analyzer() {
		rt = systemRT;
		rt.setPrecision(precision);
		measurements = systemMeasurements;
	}
	
	/** Constructs a new Analyzer using the specified ImagePlus object
		and the system-wide measurement options and results table. */
	public Analyzer(ImagePlus imp) {
		this();
		this.imp = imp;
	}
	
	/** Construct a new Analyzer using an ImagePlus object and private
		measurement options and results table. */
	public Analyzer(ImagePlus imp, int measurements, ResultsTable rt) {
		this.imp = imp;
		this.measurements = measurements;
		this.rt = rt;
	}
	
	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		IJ.register(Analyzer.class);
		if (arg.equals("set"))
			{doSetDialog(); return DONE;}
		else if (arg.equals("sum"))
			{summarize(); return DONE;}
		else if (arg.equals("clear")) {
			if (IJ.macroRunning()) unsavedMeasurements = false;
			resetCounter();
			return DONE;
		} else
			return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		measure();
	}

	void doSetDialog() {
		String NONE = "None";
		String[] titles;
        int[] wList = WindowManager.getIDList();
        if (wList==null) {
        	titles = new String[1];
            titles[0] = NONE;
        } else {
			titles = new String[wList.length+1];
			titles[0] = NONE;
			for (int i=0; i<wList.length; i++) {
				ImagePlus imp = WindowManager.getImage(wList[i]);
				titles[i+1] = imp!=null?imp.getTitle():"";
			}
		}
		ImagePlus tImp = WindowManager.getImage(redirectTarget);
		String target = tImp!=null?tImp.getTitle():NONE;
		
 		GenericDialog gd = new GenericDialog("Set Measurements", IJ.getInstance());
		String[] labels = new String[18];
		boolean[] states = new boolean[18];
		labels[0]="Area"; states[0]=(systemMeasurements&AREA)!=0;
		labels[1]="Mean Gray Value"; states[1]=(systemMeasurements&MEAN)!=0;
		labels[2]="Standard Deviation"; states[2]=(systemMeasurements&STD_DEV)!=0;
		labels[3]="Modal Gray Value"; states[3]=(systemMeasurements&MODE)!=0;
		labels[4]="Min & Max Gray Value"; states[4]=(systemMeasurements&MIN_MAX)!=0;
		labels[5]="Centroid"; states[5]=(systemMeasurements&CENTROID)!=0;
		labels[6]="Center of Mass"; states[6]=(systemMeasurements&CENTER_OF_MASS)!=0;
		labels[7]="Perimeter"; states[7]=(systemMeasurements&PERIMETER)!=0;
		labels[8]="Bounding Rectangle"; states[8]=(systemMeasurements&RECT)!=0;
		labels[9]="Fit Ellipse"; states[9]=(systemMeasurements&ELLIPSE)!=0;
		labels[10]="Circularity"; states[10]=(systemMeasurements&CIRCULARITY)!=0;
		labels[11]="Feret's Diameter"; states[11]=(systemMeasurements&FERET)!=0;
		labels[12]="Integrated Density"; states[12]=(systemMeasurements&INTEGRATED_DENSITY)!=0;
		labels[13]="Median"; states[13]=(systemMeasurements&MEDIAN)!=0;
		labels[14]="Skewness"; states[14]=(systemMeasurements&SKEWNESS)!=0;
		labels[15]="Kurtosis"; states[15]=(systemMeasurements&KURTOSIS)!=0;
		labels[16]="Area_Fraction"; states[16]=(systemMeasurements&AREA_FRACTION)!=0;
		labels[17]="Slice Number"; states[17]=(systemMeasurements&SLICE)!=0;
		gd.setInsets(0, 0, 0);
		gd.addCheckboxGroup(10, 2, labels, states);
		labels = new String[3];
		states = new boolean[3];
		labels[0]="Limit to Threshold"; states[0]=(systemMeasurements&LIMIT)!=0;
		labels[1]="Display Label"; states[1]=(systemMeasurements&LABELS)!=0;
		labels[2]="Invert Y Coordinates"; states[2]=(systemMeasurements&INVERT_Y)!=0;
		gd.setInsets(0, 0, 0);
		gd.addCheckboxGroup(2, 2, labels, states);
		gd.setInsets(15, 0, 0);
        gd.addChoice("Redirect To:", titles, target);
		gd.setInsets(5, 0, 0);
		gd.addNumericField("Decimal Places (0-9):", precision, 0, 2, "");
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		setOptions(gd);
		int index = gd.getNextChoiceIndex();
		redirectTarget = index==0?0:wList[index-1];
		redirectTitle = titles[index];

		int prec = (int)gd.getNextNumber();
		if (prec<0) prec = 0;
		if (prec>9) prec = 9;
		if (prec!=precision) {
			precision = prec;
			rt.setPrecision(precision);
			if (mode==AREAS && IJ.isResultsWindow()) {
				IJ.setColumnHeadings("");
				updateHeadings();
			}
		}
	}
	
	void setOptions(GenericDialog gd) {
		int oldMeasurements = systemMeasurements;
		int previous = 0;
		boolean b = false;
		for (int i=0; i<list.length; i++) {
			//if (list[i]!=previous)
			b = gd.getNextBoolean();
			previous = list[i];
			if (b)
				systemMeasurements |= list[i];
			else
				systemMeasurements &= ~list[i];
		}
		if ((oldMeasurements&(~LIMIT))!=(systemMeasurements&(~LIMIT))) {
			if (IJ.macroRunning()) {
				unsavedMeasurements = false;
				reset();
				mode = AREAS;
			} else
				mode = UNDEFINED;
		}
		if ((systemMeasurements&LABELS)==0)
			systemRT.disableRowLabels();
	}
	
	void measure() {
		String sliceHdr = rt.getColumnHeading(ResultsTable.SLICE);
		if (sliceHdr==null || sliceHdr.charAt(0)!='S') {
			if (!reset()) return;
		}
		firstParticle = lastParticle = 0;
		Roi roi = imp.getRoi();
		if (roi!=null && roi.getType()==Roi.POINT) {
			measurePoint(roi);
			return;
		}
		if (roi!=null && roi.isLine()) {
			measureLength(roi);
			return;
		}
		if (roi!=null && roi.getType()==Roi.ANGLE) {
			measureAngle(roi);
			return;
		}
		if (mode!=AREAS) {
			switchingModes = true;
			if (!reset())
				return;
			mode = AREAS;
		}
		ImageStatistics stats;
		if (isRedirectImage()) {
			stats = getRedirectStats(measurements, roi);
			if (stats==null) return;
		} else
			stats = imp.getStatistics(measurements);
		if (!IJ.isResultsWindow())
			reset();
		saveResults(stats, roi);
		displayResults();
	}
	
	boolean reset() {
		boolean ok = true;
		if (rt.getCounter()>0)
			ok = resetCounter();
		if (ok && rt.getColumnHeading(ResultsTable.SLICE)==null)
			rt.setDefaultHeadings();
		return ok;
	}

	/** Returns <code>true</code> if an image is selected in the "Redirect To:"
		popup menu of the Analyze/Set Measurements dialog box. */
	public static boolean isRedirectImage() {
		return redirectTarget!=0;
	}
	
	/** Returns the image selected in the "Redirect To:" popup
		menu of the Analyze/Set Measurements dialog or null
		if "None" is selected, the image was not found or the 
		image is not the same size as <code>currentImage</code>. */
	public static ImagePlus getRedirectImage(ImagePlus currentImage) {
		ImagePlus rImp = WindowManager.getImage(redirectTarget);
		if (rImp==null) {
			IJ.error("Analyzer", "Redirect image (\""+redirectTitle+"\")\n"
				+ "not found.");
			redirectTarget = 0;
			Macro.abort();
			return null;
		}
		if (rImp.getWidth()!=currentImage.getWidth() || rImp.getHeight()!=currentImage.getHeight()) {
			IJ.error("Analyzer", "Redirect image (\""+redirectTitle+"\") \n"
				+ "is not the same size as the current image.");
			Macro.abort();
			return null;
		}
		return rImp;
	}

	ImageStatistics getRedirectStats(int measurements, Roi roi) {
		ImagePlus redirectImp = getRedirectImage(imp);
		if (redirectImp==null)
			return null;
		int depth = redirectImp.getStackSize();
		if (depth>1 && depth==imp.getStackSize())
			redirectImp.setSlice(imp.getCurrentSlice());
		ImageProcessor ip = redirectImp.getProcessor();
		if (imp.getTitle().equals("mask") && imp.getBitDepth()==8) {
			ip.setMask(imp.getProcessor());
			ip.setRoi(0, 0, imp.getWidth(), imp.getHeight());
		} else
			ip.setRoi(roi);
		return ImageStatistics.getStatistics(ip, measurements, redirectImp.getCalibration());
	}
	
	void measurePoint(Roi roi) {
		if (mode!=POINTS) {
			switchingModes = true;
			if (!reset())
				return;
			//IJ.setColumnHeadings(" \tX\tY\tValue");		
			mode = POINTS;
		}
		Polygon p = roi.getPolygon();
		ImageProcessor ip = imp.getProcessor();
		Calibration cal = imp.getCalibration();
		//ip.setCalibrationTable(cal.getCTable());
		for (int i=0; i<p.npoints; i++) {
			incrementCounter();
			int x = p.xpoints[i];
			int y = p.ypoints[i];
			double value = ip.getPixelValue(x,y);
			if (markWidth>0) {
				ip.setColor(Toolbar.getForegroundColor());
				ip.setLineWidth(markWidth);
				ip.moveTo(x,y);
				ip.lineTo(x,y);
				imp.updateAndDraw();
				ip.setLineWidth(Line.getWidth());
			}
			if ((measurements&LABELS)!=0)
				rt.addLabel("Label", getFileName());
			rt.addValue("X", cal.getX(x));
			rt.addValue("Y", cal.getY(y, imp.getHeight()));
			rt.addValue("Z", cal.getZ(imp.getCurrentSlice()-1));
			if (imp.getProperty("FHT")!=null) {
				double center = imp.getWidth()/2.0;
				y = imp.getHeight()-y-1;
				double r = Math.sqrt((x-center)*(x-center) + (y-center)*(y-center));
				if (r<1.0) r = 1.0;
				double theta = Math.atan2(y-center, x-center);
				theta = theta*180.0/Math.PI;
				if (theta<0) theta = 360.0+theta;
				rt.addValue("R", (imp.getWidth()/r)*cal.pixelWidth);
				rt.addValue("Theta", theta);
			}
			rt.addValue("Value", value);
			displayResults();
		}
		//IJ.write(rt.getCounter()+"\t"+n(cal.getX(x))+n(cal.getY(y))+n(value));
	}
	
	void measureAngle(Roi roi) {
		if (mode!=ANGLES) {
			switchingModes = true;
			if (!reset())
				return;
			if ((measurements&LABELS)!=0)
				IJ.setColumnHeadings(" \tName\tangle");
			else		
				IJ.setColumnHeadings(" \tangle");
			mode = ANGLES;
		}
		incrementCounter();
		if ((measurements&LABELS)!=0)
			rt.addLabel("Label", getFileName());
		rt.addValue("Angle", ((PolygonRoi)roi).getAngle());
		displayResults();
		//IJ.write(rt.getCounter()+"\t"+n(((PolygonRoi)roi).getAngle()));
	}
	
	void measureLength(Roi roi) {
		if (mode!=LENGTHS) {
			switchingModes = true;
			if (!reset())
				return;
			if ((measurements&LABELS)!=0)
				IJ.setColumnHeadings(" \tName\tlength");
			else		
				IJ.setColumnHeadings(" \tlength");
			mode = LENGTHS;
		}
		incrementCounter();
		if ((measurements&LABELS)!=0)
			rt.addLabel("Label", getFileName());
		rt.addValue("Length", roi.getLength());
		double angle = 0.0;
		if (roi.getType()==Roi.LINE) {
			Line l = (Line)roi;
			angle = roi.getAngle(l.x1, l.y1, l.x2, l.y2);
		}
		rt.addValue("Angle", angle);
		boolean moreParams = (measurements&MEAN)!=0||(measurements&STD_DEV)!=0||(measurements&MODE)!=0||(measurements&MIN_MAX)!=0;
		if (moreParams) {
			ProfilePlot profile = new ProfilePlot(imp);
			double[] values = profile.getProfile();
			ImageProcessor ip2 = new FloatProcessor(values.length, 1, values);
			if (roi.getType()==Roi.LINE) {
				Line l = (Line)roi;
				if ((l.y1==l.y2||l.x1==l.x2)&&l.x1==l.x1d&& l.y1==l.y1d&& l.x2==l.x2d&& l.y2==l.y2d)
					ip2.setRoi(0, 0, ip2.getWidth()-1, 1);
			}
			ImageStatistics stats = ImageStatistics.getStatistics(ip2, MEAN+STD_DEV+MODE+MIN_MAX, null);
			if ((measurements&MEAN)!=0) rt.addValue(ResultsTable.MEAN,stats.mean);
			if ((measurements&STD_DEV)!=0) rt.addValue(ResultsTable.STD_DEV,stats.stdDev);
			if ((measurements&MODE)!=0) rt.addValue(ResultsTable.MODE, stats.dmode);
			if ((measurements&MIN_MAX)!=0) {
				rt.addValue(ResultsTable.MIN,stats.min);
				rt.addValue(ResultsTable.MAX,stats.max);
			}
		}
		if ((measurements&RECT)!=0) {
			Rectangle r = roi.getBounds();
			Calibration cal = imp.getCalibration();
			rt.addValue(ResultsTable.ROI_X, cal.getX(r.x));
			rt.addValue(ResultsTable.ROI_Y, cal.getY(r.y, imp.getHeight()));
			rt.addValue(ResultsTable.ROI_WIDTH, r.width*cal.pixelWidth);
			rt.addValue(ResultsTable.ROI_HEIGHT, r.height*cal.pixelHeight);
		}
		displayResults();
	}
	
	/** Saves the measurements specified in the "Set Measurements" dialog,
		or by calling setMeasurments(), in the system results table.
	*/
	public void saveResults(ImageStatistics stats, Roi roi) {
		if (rt.getColumnHeading(ResultsTable.SLICE)==null)
			reset();
		incrementCounter();
		int counter = rt.getCounter();
		if (counter<=MAX_STANDARDS) {
			if (umeans==null) umeans = new float[MAX_STANDARDS];
			umeans[counter-1] = (float)stats.umean;
		}
		if ((measurements&LABELS)!=0)
			rt.addLabel("Label", getFileName());
		if ((measurements&AREA)!=0) rt.addValue(ResultsTable.AREA,stats.area);
		if ((measurements&MEAN)!=0) rt.addValue(ResultsTable.MEAN,stats.mean);
		if ((measurements&STD_DEV)!=0) rt.addValue(ResultsTable.STD_DEV,stats.stdDev);
		if ((measurements&MODE)!=0) rt.addValue(ResultsTable.MODE, stats.dmode);
		if ((measurements&MIN_MAX)!=0) {
			rt.addValue(ResultsTable.MIN,stats.min);
			rt.addValue(ResultsTable.MAX,stats.max);
		}
		if ((measurements&CENTROID)!=0) {
			rt.addValue(ResultsTable.X_CENTROID,stats.xCentroid);
			rt.addValue(ResultsTable.Y_CENTROID,stats.yCentroid);
		}
		if ((measurements&CENTER_OF_MASS)!=0) {
			rt.addValue(ResultsTable.X_CENTER_OF_MASS,stats.xCenterOfMass);
			rt.addValue(ResultsTable.Y_CENTER_OF_MASS,stats.yCenterOfMass);
		}
		if ((measurements&PERIMETER)!=0 || (measurements&CIRCULARITY)!=0) {
			double perimeter;
			if (roi!=null)
				perimeter = roi.getLength();
			else
				perimeter = 0.0;
			if ((measurements&PERIMETER)!=0) 
				rt.addValue(ResultsTable.PERIMETER,perimeter);
			if ((measurements&CIRCULARITY)!=0) {
				double circularity = perimeter==0.0?0.0:4.0*Math.PI*(stats.area/(perimeter*perimeter));
				if (circularity>1.0) circularity = 1.0;
				rt.addValue(ResultsTable.CIRCULARITY, circularity);
			}
		}
		if ((measurements&RECT)!=0) {
			rt.addValue(ResultsTable.ROI_X,stats.roiX);
			rt.addValue(ResultsTable.ROI_Y,stats.roiY);
			rt.addValue(ResultsTable.ROI_WIDTH,stats.roiWidth);
			rt.addValue(ResultsTable.ROI_HEIGHT,stats.roiHeight);
		}
		if ((measurements&ELLIPSE)!=0) {
			rt.addValue(ResultsTable.MAJOR,stats.major);
			rt.addValue(ResultsTable.MINOR,stats.minor);
			rt.addValue(ResultsTable.ANGLE,stats.angle);
		}
		if ((measurements&FERET)!=0)
			rt.addValue(ResultsTable.FERET, roi!=null?roi.getFeretsDiameter():0.0);
		if ((measurements&INTEGRATED_DENSITY)!=0)
			rt.addValue(ResultsTable.INTEGRATED_DENSITY,stats.area*stats.mean);
		if ((measurements&MEDIAN)!=0) rt.addValue(ResultsTable.MEDIAN, stats.median);
		if ((measurements&SKEWNESS)!=0) rt.addValue(ResultsTable.SKEWNESS, stats.skewness);
		if ((measurements&KURTOSIS)!=0) rt.addValue(ResultsTable.KURTOSIS, stats.kurtosis);
		if ((measurements&AREA_FRACTION)!=0) rt.addValue(ResultsTable.AREA_FRACTION, stats.areaFraction);
		if ((measurements&SLICE)!=0) rt.addValue(ResultsTable.SLICE, imp!=null?imp.getCurrentSlice():1.0);
	}
	
	String getFileName() {
		String s = "";
		if (imp!=null) {
			if (mode==AREAS && redirectTarget!=0) {
				ImagePlus rImp = WindowManager.getImage(redirectTarget);
				if (rImp!=null) s = rImp.getTitle();				
			} else
				s = imp.getTitle();
			//int len = s.length();
			//if (len>4 && s.charAt(len-4)=='.' && !Character.isDigit(s.charAt(len-1)))
			//	s = s.substring(0,len-4); 
			Roi roi = imp.getRoi();
			String roiName = roi!=null?roi.getName():null;
			if (roiName!=null)
				s += ":"+roiName;
			if (imp.getStackSize()>1) {
				ImageStack stack = imp.getStack();
				int currentSlice = imp.getCurrentSlice();
				String label = stack.getShortSliceLabel(currentSlice);
				String colon = s.equals("")?"":":";
				if (label!=null && !label.equals(""))
					s += colon+label;
				else
					s += colon+currentSlice;
			}
		}
		return s;
	}

	/** Writes the last row in the results table to the ImageJ window. */
	public void displayResults() {
		int counter = rt.getCounter();
		if (counter==1)
			IJ.setColumnHeadings(rt.getColumnHeadings());		
		IJ.write(rt.getRowAsString(counter-1));
	}

	/** Updates the displayed column headings. Does nothing if
	    the results table headings and the displayed headings are
        the same. Redisplays the results if the headings are
        different and the results table is not empty. */
	public void updateHeadings() {
		TextPanel tp = IJ.getTextPanel();
		if (tp==null)
			return;
		String worksheetHeadings = tp.getColumnHeadings();		
		String tableHeadings = rt.getColumnHeadings();		
		if (worksheetHeadings.equals(tableHeadings))
			return;
		IJ.setColumnHeadings(tableHeadings);
		int n = rt.getCounter();
		if (n>0) {
			StringBuffer sb = new StringBuffer(n*tableHeadings.length());
			for (int i=0; i<n; i++)
				sb.append(rt.getRowAsString(i)+"\n");
			tp.append(new String(sb));
		}
	}

	/** Converts a number to a formatted string with a tab at the end. */
	public String n(double n) {
		String s;
		if (Math.round(n)==n)
			s = IJ.d2s(n,0);
		else
			s = IJ.d2s(n,precision);
		return s+"\t";
	}
		
	void incrementCounter() {
		//counter++;
		if (rt==null) rt = systemRT;
		rt.incrementCounter();
		unsavedMeasurements = true;
	}
	
	public void summarize() {
		rt = systemRT;
		if (rt.getCounter()==0)
			return;
		if (summarized)
			rt.show("Results");
		measurements = systemMeasurements;
		min = new StringBuffer(100);
		max = new StringBuffer(100);
		mean = new StringBuffer(100);
		sd = new StringBuffer(100);
		min.append("Min\t");
		max.append("Max\t");
		mean.append("Mean\t");
		sd.append("SD\t");
		if ((measurements&LABELS)!=0) {
			min.append("\t");
			max.append("\t");
			mean.append("\t");
			sd.append("\t");
		}
		if (mode==POINTS) 
			summarizePoints(rt);
		else if (mode==LENGTHS) 
			summarizeLengths(rt);
		else if (mode==ANGLES) 
			add2(rt.getColumnIndex("Angle"));
		else
			summarizeAreas();
		TextPanel tp = IJ.getTextPanel();
		if (tp!=null) {
			String worksheetHeadings = tp.getColumnHeadings();		
			if (worksheetHeadings.equals(""))
				IJ.setColumnHeadings(rt.getColumnHeadings());
		}		
		IJ.write("");		
		IJ.write(new String(mean));		
		IJ.write(new String(sd));		
		IJ.write(new String(min));		
		IJ.write(new String(max));
		IJ.write("");		
		mean = null;		
		sd = null;		
		min = null;		
		max = null;
		summarized = true;		
	}
	
	void summarizePoints(ResultsTable rt) {
		add2(rt.getColumnIndex("X"));
		add2(rt.getColumnIndex("Y"));
		add2(rt.getColumnIndex("Z"));
		add2(rt.getColumnIndex("Value"));
	}

	void summarizeLengths(ResultsTable rt) {
		int index = rt.getColumnIndex("Mean");
		if (rt.columnExists(index)) add2(index);
		index = rt.getColumnIndex("StdDev");
		if (rt.columnExists(index)) add2(index);
		index = rt.getColumnIndex("Mode");
		if (rt.columnExists(index)) add2(index);
		index = rt.getColumnIndex("Min");
		if (rt.columnExists(index)) add2(index);
		index = rt.getColumnIndex("Max");
		if (rt.columnExists(index)) add2(index);
		index = rt.getColumnIndex("Angle");
		if (rt.columnExists(index)) add2(index);
		index = rt.getColumnIndex("Length");
		if (rt.columnExists(index)) add2(index);
	}
	
	void summarizeAreas() {
		if ((measurements&AREA)!=0) add2(ResultsTable.AREA);
		if ((measurements&MEAN)!=0) add2(ResultsTable.MEAN);
		if ((measurements&STD_DEV)!=0) add2(ResultsTable.STD_DEV);
		if ((measurements&MODE)!=0) add2(ResultsTable.MODE);
		if ((measurements&MIN_MAX)!=0) {
			add2(ResultsTable.MIN);
			add2(ResultsTable.MAX);
		}
		if ((measurements&CENTROID)!=0) {
			add2(ResultsTable.X_CENTROID);
			add2(ResultsTable.Y_CENTROID);
		}
		if ((measurements&CENTER_OF_MASS)!=0) {
			add2(ResultsTable.X_CENTER_OF_MASS);
			add2(ResultsTable.Y_CENTER_OF_MASS);
		}
		if ((measurements&PERIMETER)!=0)
			add2(ResultsTable.PERIMETER);
		if ((measurements&RECT)!=0) {
			add2(ResultsTable.ROI_X);
			add2(ResultsTable.ROI_Y);
			add2(ResultsTable.ROI_WIDTH);
			add2(ResultsTable.ROI_HEIGHT);
		}
		if ((measurements&ELLIPSE)!=0) {
			add2(ResultsTable.MAJOR);
			add2(ResultsTable.MINOR);
			add2(ResultsTable.ANGLE);
		}
		if ((measurements&CIRCULARITY)!=0)
			add2(ResultsTable.CIRCULARITY);
		if ((measurements&FERET)!=0)
			add2(ResultsTable.FERET);
		if ((measurements&INTEGRATED_DENSITY)!=0)
			add2(ResultsTable.INTEGRATED_DENSITY);
		if ((measurements&MEDIAN)!=0)
			add2(ResultsTable.MEDIAN);
		if ((measurements&SKEWNESS)!=0)
			add2(ResultsTable.SKEWNESS);
		if ((measurements&KURTOSIS)!=0)
			add2(ResultsTable.KURTOSIS);
		if ((measurements&AREA_FRACTION)!=0)
			add2(ResultsTable.AREA_FRACTION);
	}

	private void add2(int column) {
		float[] c = column>=0?rt.getColumn(column):null;
		if (c!=null) {
			ImageProcessor ip = new FloatProcessor(c.length, 1, c, null);
			if (ip==null)
				return;
			ImageStatistics stats = new FloatStatistics(ip);
			if (stats==null)
				return;
			mean.append(n(stats.mean));
			min.append(n(stats.min));
			max.append(n(stats.max));
			sd.append(n(stats.stdDev));
		} else {
			mean.append("-\t");
			min.append("-\t");
			max.append("-\t");
			sd.append("-\t");
		}
	}

	/** Returns the current measurement count. */
	public static int getCounter() {
		return systemRT.getCounter();
	}

	/** Sets the measurement counter to zero. Displays a dialog that
	    allows the user to save any existing measurements. Returns
	    false if the user cancels the dialog.
	*/
	public synchronized static boolean resetCounter() {
		TextPanel tp = IJ.isResultsWindow()?IJ.getTextPanel():null;
		int counter = systemRT.getCounter();
		int lineCount = tp!=null?IJ.getTextPanel().getLineCount():0;
		ImageJ ij = IJ.getInstance();
		boolean macro = (IJ.macroRunning()&&!switchingModes) || Interpreter.isBatchMode();
		switchingModes = false;
		if (counter>0 && lineCount>0 && unsavedMeasurements && !macro && ij!=null && !ij.quitting()) {
			YesNoCancelDialog d = new YesNoCancelDialog(ij, "ImageJ", "Save "+counter+" measurements?");
			if (d.cancelPressed())
				return false;
			else if (d.yesPressed()) {
				if (!(new MeasurementsWriter()).save(""))
					return false;
			}
		}
		umeans = null;
		systemRT.reset();
		unsavedMeasurements = false;
		if (tp!=null) tp.clear();
		summarized = false;
		return true;
	}
	
	public static void setUnsavedMeasurements(boolean b) {
		unsavedMeasurements = b;
	}
	
	// Returns the measurement options defined in the Set Measurements dialog. */
	public static int getMeasurements() {
		return systemMeasurements;
	}

	/** Sets the system-wide measurement options. */
	public static void setMeasurements(int measurements) {
		systemMeasurements = measurements;
	}

	/** Sets the specified system-wide measurement option. */
	public static void setMeasurement(int option, boolean state) {
			if (state)
				systemMeasurements |= option;
			else
				systemMeasurements &= ~option;
	}

	/** Called once when ImageJ quits. */
	public static void savePreferences(Properties prefs) {
		prefs.put(MEASUREMENTS, Integer.toString(systemMeasurements));
		prefs.put(MARK_WIDTH, Integer.toString(markWidth));
		prefs.put(PRECISION, Integer.toString(precision));	}

	/** Returns an array containing the first 20 uncalibrated means. */
	public static float[] getUMeans() {
		return umeans;
	}

	/** Returns the ImageJ results table. This table should only
		be displayed in a the "Results" window. */
	public static ResultsTable getResultsTable() {
		return systemRT;
	}

	/** Returns the number of digits displayed to the right of decimal point. */
	public static int getPrecision() {
		return precision;
	}

	/** Sets the number of digits displayed to the right of decimal point. */
	public static void setPrecision(int decimalPlaces) {
		if (decimalPlaces<0) decimalPlaces = 0;
		if (decimalPlaces>9) decimalPlaces = 9;
		precision = decimalPlaces;
	}

	/** Returns an updated Y coordinate based on
		the current "Invert Y Coordinates" flag. */
	public static int updateY(int y, int imageHeight) {
		if ((systemMeasurements&INVERT_Y)!=0)
			y = imageHeight-y-1;
		return y;
	}
	
	/** Returns an updated Y coordinate based on
		the current "Invert Y Coordinates" flag. */
	public static double updateY(double y, int imageHeight) {
		if ((systemMeasurements&INVERT_Y)!=0)
			y = imageHeight-y-1;
		return y;
	}
	
	/** Sets the default headings ("Area", "Mean", etc.). */
	public static void setDefaultHeadings() {
		systemRT.setDefaultHeadings();
	}


}
	
