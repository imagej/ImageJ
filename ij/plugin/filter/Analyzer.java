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
import ij.plugin.Straightener;
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
		CENTROID,CENTER_OF_MASS,PERIMETER,RECT,ELLIPSE,SHAPE_DESCRIPTORS, FERET,
		INTEGRATED_DENSITY,MEDIAN,SKEWNESS,KURTOSIS,AREA_FRACTION,STACK_POSITION,
		LIMIT,LABELS,INVERT_Y,SCIENTIFIC_NOTATION,ADD_TO_OVERLAY,NaN_EMPTY_CELLS};

	private static final String MEASUREMENTS = "measurements";
	private static final String MARK_WIDTH = "mark.width";
	private static final String PRECISION = "precision";
	//private static int counter;
	private static boolean unsavedMeasurements;
	public static Color darkBlue = new Color(0,0,160);
	private static int systemMeasurements = Prefs.getInt(MEASUREMENTS,AREA+MEAN+MIN_MAX);
	public static int markWidth;
	public static int precision = Prefs.getInt(PRECISION,3);
	private static float[] umeans = new float[MAX_STANDARDS];
	private static ResultsTable systemRT = new ResultsTable();
	private static int redirectTarget;
	private static String redirectTitle = "";
	private static ImagePlus redirectImage; // non-displayed images
	static int firstParticle, lastParticle;
	private static boolean summarized;
	private static boolean switchingModes;
	private static boolean showMin = true;
	private static boolean showAngle = true;
	
	public Analyzer() {
		rt = systemRT;
		rt.showRowNumbers(true);
		rt.setPrecision((systemMeasurements&SCIENTIFIC_NOTATION)!=0?-precision:precision);
		rt.setNaNEmptyCells((systemMeasurements&NaN_EMPTY_CELLS)!=0);
		measurements = systemMeasurements;
	}
	
	/** Constructs a new Analyzer using the specified ImagePlus object
		and the current measurement options and default results table. */
	public Analyzer(ImagePlus imp) {
		this();
		this.imp = imp;
	}
	
	/** Construct a new Analyzer using an ImagePlus object and private
		measurement options and results table. */
	public Analyzer(ImagePlus imp, int measurements, ResultsTable rt) {
		this.imp = imp;
		this.measurements = measurements;
		if (rt==null)
			rt = new ResultsTable();
		rt.setPrecision((systemMeasurements&SCIENTIFIC_NOTATION)!=0?-precision:precision);
		rt.setNaNEmptyCells((systemMeasurements&NaN_EMPTY_CELLS)!=0);
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
		displayResults();
		if ((measurements&ADD_TO_OVERLAY)!=0)
			addToOverlay();
	}
	
	void addToOverlay() {
		Roi roi = imp.getRoi();
		if (roi==null)
			return;
		roi = (Roi)roi.clone();
		if (imp.getStackSize()>1) {
			if (imp.isHyperStack()||imp.isComposite())
				roi.setPosition(0, imp.getSlice(), imp.getFrame());
			else
				roi.setPosition(imp.getCurrentSlice());
		}
		if (roi.getName()==null)
			roi.setName(""+rt.size());
		//roi.setName(IJ.getString("Label:", "m"+rt.getCounter()));
		roi.setIgnoreClipRect(true);
		Overlay overlay = imp.getOverlay();
		if (overlay==null)
			overlay = new Overlay();
		if (!overlay.getDrawNames())
			overlay.drawNames(true);
		overlay.setLabelColor(Color.white);
		overlay.drawBackgrounds(true);
		overlay.add(roi);
		imp.setOverlay(overlay);
		if (roi.getType()==Roi.COMPOSITE && Toolbar.getToolId()==Toolbar.OVAL && Toolbar.getBrushSize()>0)
			imp.deleteRoi();  // delete ROIs created with the selection brush tool
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
		String macroOptions = Macro.getOptions();
		if (macroOptions!=null && macroOptions.indexOf("circularity ")!=-1)
			Macro.setOptions(macroOptions.replaceAll("circularity ", "shape "));
		if (macroOptions!=null && macroOptions.indexOf("slice ")!=-1)
			Macro.setOptions(macroOptions.replaceAll("slice ", "stack "));

 		GenericDialog gd = new GenericDialog("Set Measurements", IJ.getInstance());
		String[] labels = new String[18];
		boolean[] states = new boolean[18];
		labels[0]="Area"; states[0]=(systemMeasurements&AREA)!=0;
		labels[1]="Mean gray value"; states[1]=(systemMeasurements&MEAN)!=0;
		labels[2]="Standard deviation"; states[2]=(systemMeasurements&STD_DEV)!=0;
		labels[3]="Modal gray value"; states[3]=(systemMeasurements&MODE)!=0;
		labels[4]="Min & max gray value"; states[4]=(systemMeasurements&MIN_MAX)!=0;
		labels[5]="Centroid"; states[5]=(systemMeasurements&CENTROID)!=0;
		labels[6]="Center of mass"; states[6]=(systemMeasurements&CENTER_OF_MASS)!=0;
		labels[7]="Perimeter"; states[7]=(systemMeasurements&PERIMETER)!=0;
		labels[8]="Bounding rectangle"; states[8]=(systemMeasurements&RECT)!=0;
		labels[9]="Fit ellipse"; states[9]=(systemMeasurements&ELLIPSE)!=0;
		labels[10]="Shape descriptors"; states[10]=(systemMeasurements&SHAPE_DESCRIPTORS)!=0;
		labels[11]="Feret's diameter"; states[11]=(systemMeasurements&FERET)!=0;
		labels[12]="Integrated density"; states[12]=(systemMeasurements&INTEGRATED_DENSITY)!=0;
		labels[13]="Median"; states[13]=(systemMeasurements&MEDIAN)!=0;
		labels[14]="Skewness"; states[14]=(systemMeasurements&SKEWNESS)!=0;
		labels[15]="Kurtosis"; states[15]=(systemMeasurements&KURTOSIS)!=0;
		labels[16]="Area_fraction"; states[16]=(systemMeasurements&AREA_FRACTION)!=0;
		labels[17]="Stack position"; states[17]=(systemMeasurements&STACK_POSITION)!=0;
		gd.setInsets(0, 0, 0);
		gd.addCheckboxGroup(10, 2, labels, states);
		labels = new String[6];
		states = new boolean[6];
		labels[0]="Limit to threshold"; states[0]=(systemMeasurements&LIMIT)!=0;
		labels[1]="Display label"; states[1]=(systemMeasurements&LABELS)!=0;
		labels[2]="Invert Y coordinates"; states[2]=(systemMeasurements&INVERT_Y)!=0;
		labels[3]="Scientific notation"; states[3]=(systemMeasurements&SCIENTIFIC_NOTATION)!=0;;
		labels[4]="Add to overlay"; states[4]=(systemMeasurements&ADD_TO_OVERLAY)!=0;;
		labels[5]="NaN empty cells"; states[5]=(systemMeasurements&NaN_EMPTY_CELLS)!=0;;
		gd.setInsets(0, 0, 0);
		gd.addCheckboxGroup(3, 2, labels, states);
		gd.setInsets(15, 0, 0);
        gd.addChoice("Redirect to:", titles, target);
		gd.setInsets(5, 0, 0);
		gd.addNumericField("Decimal places (0-9):", precision, 0, 2, "");
		gd.addHelp(IJ.URL+"/docs/menus/analyze.html#set");
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int oldMeasurements = systemMeasurements;
		setOptions(gd);
		int index = gd.getNextChoiceIndex();
		redirectTarget = index==0?0:wList[index-1];
		redirectTitle = titles[index];
		ImagePlus imp = WindowManager.getImage(redirectTarget);
		redirectImage = imp!=null && imp.getWindow()==null?imp:null;

		int prec = (int)gd.getNextNumber();
		if (prec<0) prec = 0;
		if (prec>9) prec = 9;
		boolean notationChanged = (oldMeasurements&SCIENTIFIC_NOTATION)!=(systemMeasurements&SCIENTIFIC_NOTATION);
		if (prec!=precision || notationChanged) {
			precision = prec;
			rt.setPrecision((systemMeasurements&SCIENTIFIC_NOTATION)!=0?-precision:precision);
			if (rt.size()>0)
				rt.show("Results");
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
		if ((oldMeasurements&(~SCIENTIFIC_NOTATION))!=(systemMeasurements&(~SCIENTIFIC_NOTATION))&&IJ.isResultsWindow()) {
				rt.setPrecision((systemMeasurements&SCIENTIFIC_NOTATION)!=0?-precision:precision);
				clearSummary();
				rt.update(systemMeasurements, imp, null);
		}
		if ((systemMeasurements&LABELS)==0)
			systemRT.disableRowLabels();
	}
	
	/** Measures the image or selection and adds the results to the default results table. */
	public void measure() {
		String lastHdr = rt.getColumnHeading(ResultsTable.LAST_HEADING);
		if (lastHdr==null || lastHdr.charAt(0)!='M') {
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
		ImageStatistics stats;
		if (isRedirectImage()) {
			stats = getRedirectStats(measurements, roi);
			if (stats==null) return;
		} else
			stats = imp.getStatistics(measurements);
		if (!IJ.isResultsWindow() && IJ.getInstance()!=null)
			reset();
		saveResults(stats, roi);
	}
	
	/*
	void showHeadings() {
		String[] headings = rt.getHeadings();
		int columns = headings.length;
		if (columns==0)
			return;
		IJ.log("Headings: "+headings.length+" "+rt.getColumnHeading(ResultsTable.LAST_HEADING));
		for (int i=0; i<columns; i++) {
			if (headings[i]!=null)
				IJ.log("   "+i+" "+headings[i]+" "+rt.getColumnIndex(headings[i]));
		}
	}
	*/
	
	boolean reset() {
		boolean ok = true;
		if (rt.size()>0)
			ok = resetCounter();
		if (ok && rt.getColumnHeading(ResultsTable.LAST_HEADING)==null)
			rt.setDefaultHeadings();
		return ok;
	}

	/** Returns <code>true</code> if an image is selected in the "Redirect To:"
		popup menu of the Analyze/Set Measurements dialog box. */
	public static boolean isRedirectImage() {
		return redirectTarget!=0;
	}
	
	/** Set the "Redirect To" image. Pass 'null' as the 
	    argument to disable redirected sampling. */
	public static void setRedirectImage(ImagePlus imp) {
		if (imp==null) {
			redirectTarget = 0;
			redirectTitle = null;
			redirectImage = null;
		} else {
			redirectTarget = imp.getID();
			redirectTitle = imp.getTitle();
			if (imp.getWindow()==null)
				redirectImage = imp;
		}
	}
	
	private ImagePlus getRedirectImageOrStack(ImagePlus cimp) {
		ImagePlus rimp = getRedirectImage(cimp);
		if (rimp!=null) {
			int depth = rimp.getStackSize();
			if (depth>1 && depth==cimp.getStackSize() && rimp.getCurrentSlice()!=cimp.getCurrentSlice())
				rimp.setSlice(cimp.getCurrentSlice());
		}
		return rimp;
	}

	/** Returns the image selected in the "Redirect To:" popup
		menu of the Analyze/Set Measurements dialog, or null
		if "None" is selected, the image was not found or the 
		image is not the same size as <code>currentImage</code>. */
	public static ImagePlus getRedirectImage(ImagePlus cimp) {
		ImagePlus rimp = WindowManager.getImage(redirectTarget);
		if (rimp==null)
			rimp = redirectImage;
		if (rimp==null) {
			IJ.error("Analyzer", "Redirect image (\""+redirectTitle+"\")\n"
				+ "not found.");
			redirectTarget = 0;
			Macro.abort();
			return null;
		}
		if (rimp.getWidth()!=cimp.getWidth() || rimp.getHeight()!=cimp.getHeight()) {
			IJ.error("Analyzer", "Redirect image (\""+redirectTitle+"\") \n"
				+ "is not the same size as the current image.");
			Macro.abort();
			return null;
		}
		return rimp;
	}

	ImageStatistics getRedirectStats(int measurements, Roi roi) {
		ImagePlus redirectImp = getRedirectImageOrStack(imp);
		if (redirectImp==null)
			return null;
		ImageProcessor ip = redirectImp.getProcessor();
		if (imp.getTitle().equals("mask") && imp.getBitDepth()==8) {
			ip.setMask(imp.getProcessor());
			ip.setRoi(0, 0, imp.getWidth(), imp.getHeight());
		} else
			ip.setRoi(roi);
		return ImageStatistics.getStatistics(ip, measurements, redirectImp.getCalibration());
	}
	
	void measurePoint(Roi roi) {
		if (rt.size()>0) {
			if (!IJ.isResultsWindow()) reset();
			int index = rt.getColumnIndex("X");
			if (index<0 || !rt.columnExists(index)) {
				clearSummary();
				rt.update(measurements, imp, roi);
			}
		}
		FloatPolygon p = roi.getFloatPolygon();
		ImagePlus imp2 = isRedirectImage()?getRedirectImageOrStack(imp):null;
		if (imp2==null) imp2 = imp;
		for (int i=0; i<p.npoints; i++) {
			ImageProcessor ip = imp2.getProcessor();
			ip.setRoi((int)p.xpoints[i], (int)p.ypoints[i], 1, 1);
			ImageStatistics stats = ImageStatistics.getStatistics(ip, measurements, imp2.getCalibration());
			saveResults(stats, new PointRoi(p.xpoints[i], p.ypoints[i]));
			if (i!=p.npoints-1) displayResults();
		}
	}
	
	void measureAngle(Roi roi) {
		if (rt.size()>0) {
			if (!IJ.isResultsWindow()) reset();
			int index = rt.getColumnIndex("Angle");
			if (index<0 || !rt.columnExists(index)) {
				clearSummary();
				rt.update(measurements, imp, roi);
			}
		}
		ImageProcessor ip = imp.getProcessor();
		ip.setRoi(roi.getPolygon());
		ImageStatistics stats = new ImageStatistics();
		saveResults(stats, roi);
	}
	
	void measureLength(Roi roi) {
		ImagePlus imp2 = isRedirectImage()?getRedirectImageOrStack(imp):null;
		if (imp2!=null)
			imp2.setRoi(roi);
		else
			imp2 = imp;
		if (rt.size()>0) {
			if (!IJ.isResultsWindow()) reset();
			boolean update = false;
			int index = rt.getColumnIndex("Length");
			if (index<0 || !rt.columnExists(index))
				update=true;
			if (roi.getType()==Roi.LINE) {
				index = rt.getColumnIndex("Angle");
				if (index<0 || !rt.columnExists(index)) update=true;
			}
			if (update) {
				clearSummary();
				rt.update(measurements, imp2, roi);
			}
		}
		boolean straightLine = roi.getType()==Roi.LINE;
		int lineWidth = (int)Math.round(roi.getStrokeWidth());
		ImageProcessor ip2 = imp2.getProcessor();
		double minThreshold = ip2.getMinThreshold();
		double maxThreshold = ip2.getMaxThreshold();
		int limit = (Analyzer.getMeasurements()&LIMIT)!=0?LIMIT:0;
		boolean calibrated = imp2.getCalibration().calibrated();
		Rectangle saveR = null;
		Calibration globalCal = calibrated?imp2.getGlobalCalibration():null;
		Calibration localCal = null;
		if (globalCal!=null) {
			imp2.setGlobalCalibration(null);
			localCal = imp2.getCalibration().copy();
			imp2.setCalibration(globalCal);
		}
		if (straightLine && lineWidth>1) {
			saveR = ip2.getRoi();
			ip2.setRoi(roi.getPolygon());
		} else if (lineWidth>1 && calibrated && limit!=0) {
			Calibration cal = imp2.getCalibration().copy();
			imp2.getCalibration().disableDensityCalibration();
			ip2 = (new Straightener()).straightenLine(imp2, lineWidth);
			imp2.setCalibration(cal);
			ip2 = convertToOriginalDepth(imp2, ip2);
			ip2.setCalibrationTable(cal.getCTable());
		} else if (lineWidth>1) {
			if ((measurements&AREA)!=0 || (measurements&MEAN)!=0 || calibrated) {
				ip2 = (new Straightener()).straightenLine(imp2, lineWidth);
				if (limit!=0)
					ip2 = convertToOriginalDepth(imp2, ip2);
			} else {
				saveResults(new ImageStatistics(), roi);
				return;
			}
		} else if (calibrated && limit!=0) {
			Calibration cal = imp2.getCalibration().copy();
			imp2.getCalibration().disableDensityCalibration();
			ProfilePlot profile = new ProfilePlot(imp2);
			imp2.setCalibration(cal);
			double[] values = profile.getProfile();
			if (values==null) return;
			ip2 = new FloatProcessor(values.length, 1, values);
			ip2 = convertToOriginalDepth(imp2, ip2);
			ip2.setCalibrationTable(cal.getCTable());
		} else {
			ProfilePlot profile = new ProfilePlot(imp2);
			double[] values = profile.getProfile();
			if (values==null) return;
			ip2 = new FloatProcessor(values.length, 1, values);
			if (limit!=0)
				ip2 = convertToOriginalDepth(imp2, ip2);
		}
		if (limit!=0 && minThreshold!=ImageProcessor.NO_THRESHOLD)
			ip2.setThreshold(minThreshold,maxThreshold,ImageProcessor.NO_LUT_UPDATE);
		ImageStatistics stats = ImageStatistics.getStatistics(ip2, AREA+MEAN+STD_DEV+MODE+MIN_MAX+limit, imp2.getCalibration());
		if (saveR!=null) ip2.setRoi(saveR);
		if ((roi instanceof Line) && (measurements&CENTROID)!=0) {
			FloatPolygon p = ((Line)roi).getFloatPoints();
			stats.xCentroid = p.xpoints[0] + (p.xpoints[1]-p.xpoints[0])/2.0;
			stats.yCentroid = p.ypoints[0] + (p.ypoints[1]-p.ypoints[0])/2.0;
			if (imp2!=null) {
				Calibration cal = imp.getCalibration();
				stats.xCentroid = cal.getX(stats.xCentroid);
				stats.yCentroid = cal.getY(stats.yCentroid);
			}
		}
		saveResults(stats, roi);
		if (globalCal!=null && localCal!=null) {
			imp2.setGlobalCalibration(globalCal);
			imp2.setCalibration(localCal);
		}
	}
	
	private ImageProcessor convertToOriginalDepth(ImagePlus imp, ImageProcessor ip) {
		if (imp.getBitDepth()==8)
			ip = ip.convertToByte(false);
		else if (imp.getBitDepth()==16)
			ip = ip.convertToShort(false);
		return ip;
	}

	
	/** Saves the measurements specified in the "Set Measurements" dialog,
		or by calling setMeasurements(), in the default results table.
	*/
	public void saveResults(ImageStatistics stats, Roi roi) {
		if (rt.getColumnHeading(ResultsTable.LAST_HEADING)==null)
			reset();
		clearSummary();
		incrementCounter();
		int counter = rt.getCounter();
		if (counter<=MAX_STANDARDS && !(stats.umean==0.0&&counter==1&&umeans!=null && umeans[0]!=0f)) {
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
			if (showMin) rt.addValue(ResultsTable.MIN,stats.min);
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
		if ((measurements&PERIMETER)!=0 || (measurements&SHAPE_DESCRIPTORS)!=0) {
			double perimeter;
			if (roi!=null)
				perimeter = roi.getLength();
			else
				perimeter = imp!=null?imp.getWidth()*2+imp.getHeight()*2:0.0;
			if ((measurements&PERIMETER)!=0) 
				rt.addValue(ResultsTable.PERIMETER,perimeter);
			if ((measurements&SHAPE_DESCRIPTORS)!=0) {
				double circularity = perimeter==0.0?0.0:4.0*Math.PI*(stats.area/(perimeter*perimeter));
				if (circularity>1.0) circularity = 1.0;
				rt.addValue(ResultsTable.CIRCULARITY, circularity);
				Polygon ch = null;
				boolean isArea = roi==null || roi.isArea();
				double convexArea = roi!=null?getArea(roi.getConvexHull()):stats.pixelCount;
				rt.addValue(ResultsTable.ASPECT_RATIO, isArea?stats.major/stats.minor:0.0);
				rt.addValue(ResultsTable.ROUNDNESS, isArea?4.0*stats.area/(Math.PI*stats.major*stats.major):0.0);
				rt.addValue(ResultsTable.SOLIDITY, isArea?stats.pixelCount/convexArea:Double.NaN);
				if (rt.size()==1) {
					rt.setDecimalPlaces(ResultsTable.CIRCULARITY, precision);
					rt.setDecimalPlaces(ResultsTable.ASPECT_RATIO, precision);
					rt.setDecimalPlaces(ResultsTable.ROUNDNESS, precision);
					rt.setDecimalPlaces(ResultsTable.SOLIDITY, precision);
				}
				//rt.addValue(ResultsTable.CONVEXITY, getConvexPerimeter(roi, ch)/perimeter);
			}
		}
		if ((measurements&RECT)!=0) {
			if (roi!=null && roi.isLine()) {
				Rectangle bounds = roi.getBounds();
				double rx = bounds.x;
				double ry = bounds.y;
				double rw = bounds.width;
				double rh = bounds.height;
				Calibration cal = imp!=null?imp.getCalibration():null;
				if (cal!=null) {
					rx = cal.getX(rx);
					ry = cal.getY(ry);
					rw *= cal.pixelWidth;
					rh *= cal.pixelHeight;
				}
				rt.addValue(ResultsTable.ROI_X, rx);
				rt.addValue(ResultsTable.ROI_Y, ry);
				rt.addValue(ResultsTable.ROI_WIDTH, rw);
				rt.addValue(ResultsTable.ROI_HEIGHT, rh);
			} else {
				rt.addValue(ResultsTable.ROI_X,stats.roiX);
				rt.addValue(ResultsTable.ROI_Y,stats.roiY);
				rt.addValue(ResultsTable.ROI_WIDTH,stats.roiWidth);
				rt.addValue(ResultsTable.ROI_HEIGHT,stats.roiHeight);
			}
		}
		if ((measurements&ELLIPSE)!=0) {
			rt.addValue(ResultsTable.MAJOR,stats.major);
			rt.addValue(ResultsTable.MINOR,stats.minor);
			rt.addValue(ResultsTable.ANGLE,stats.angle);
		}
		if ((measurements&FERET)!=0) {
			boolean extras = true;
			double FeretDiameter=Double.NaN, feretAngle=Double.NaN, minFeret=Double.NaN,
				feretX=Double.NaN, feretY=Double.NaN;
			Roi roi2 = roi;
			if (roi2==null && imp!=null)
				roi2 = new Roi(0, 0, imp.getWidth(), imp.getHeight());
			if (roi2!=null) {
				double[] a = roi2.getFeretValues();
				if (a!=null) {
					FeretDiameter = a[0];
					feretAngle = a[1];
					minFeret = a[2];
					feretX = a[3];
					feretY = a[4];
				}
			}
			rt.addValue(ResultsTable.FERET, FeretDiameter);
			rt.addValue(ResultsTable.FERET_X, feretX);
			rt.addValue(ResultsTable.FERET_Y, feretY);
			rt.addValue(ResultsTable.FERET_ANGLE, feretAngle);
			rt.addValue(ResultsTable.MIN_FERET, minFeret);
		}
		if ((measurements&INTEGRATED_DENSITY)!=0) {
			rt.addValue(ResultsTable.INTEGRATED_DENSITY,stats.area*stats.mean);
			rt.addValue(ResultsTable.RAW_INTEGRATED_DENSITY,stats.pixelCount*stats.umean);
		}
		if ((measurements&MEDIAN)!=0) rt.addValue(ResultsTable.MEDIAN, stats.median);
		if ((measurements&SKEWNESS)!=0) rt.addValue(ResultsTable.SKEWNESS, stats.skewness);
		if ((measurements&KURTOSIS)!=0) rt.addValue(ResultsTable.KURTOSIS, stats.kurtosis);
		if ((measurements&AREA_FRACTION)!=0) rt.addValue(ResultsTable.AREA_FRACTION, stats.areaFraction);
		if ((measurements&STACK_POSITION)!=0) {
			boolean update = false;
			if (imp!=null && (imp.isHyperStack()||imp.isComposite())) {
				int[] position = imp.convertIndexToPosition(imp.getCurrentSlice());
				if (imp.getNChannels()>1) {
					int index = rt.getColumnIndex("Ch");
					if (index<0 || !rt.columnExists(index)) update=true;
					rt.addValue("Ch", position[0]);
				}
				if (imp.getNSlices()>1) {
					int index = rt.getColumnIndex("Slice");
					if (index<0 || !rt.columnExists(index)) update=true;
					rt.addValue("Slice", position[1]);
				}
				if (imp.getNFrames()>1) {
					int index = rt.getColumnIndex("Frame");
					if (index<0 || !rt.columnExists(index)) update=true;
					rt.addValue("Frame", position[2]);
				}
			} else {
				int index = rt.getColumnIndex("Slice");
				if (index<0 || !rt.columnExists(index)) update=true;
				rt.addValue("Slice", imp!=null?imp.getCurrentSlice():1.0);
			}
			if (update && rt==systemRT && IJ.isResultsWindow())
				rt.update(measurements, imp, roi);
		}
		if (roi!=null) {
			if (roi.isLine()) {
				rt.addValue("Length", roi.getLength());
				if (roi.getType()==Roi.LINE && showAngle) {
					double angle = 0.0;
					Line l = (Line)roi;
					angle = roi.getAngle(l.x1, l.y1, l.x2, l.y2);
					rt.addValue("Angle", angle);
				}
			} else if (roi.getType()==Roi.ANGLE) {
				double angle = ((PolygonRoi)roi).getAngle();
				if (Prefs.reflexAngle) angle = 360.0-angle;
				rt.addValue("Angle", angle);
			} else if (roi.getType()==Roi.POINT)
				savePoints(roi);
		}
		if ((measurements&LIMIT)!=0 && imp!=null && imp.getBitDepth()!=24) {
			rt.addValue(ResultsTable.MIN_THRESHOLD, stats.lowerThreshold);
			rt.addValue(ResultsTable.MAX_THRESHOLD, stats.upperThreshold);
		}
	}
	
	private void clearSummary() {
		if (summarized && rt.size()>=4 && "Max".equals(rt.getLabel(rt.size()-1))) {
			for (int i=0; i<4; i++)
				rt.deleteRow(rt.size()-1);
			rt.show("Results");
			summarized = false;
		}
	}
		
	final double getArea(Polygon p) {
		if (p==null) return Double.NaN;
		int carea = 0;
		int iminus1;
		for (int i=0; i<p.npoints; i++) {
			iminus1 = i-1;
			if (iminus1<0) iminus1=p.npoints-1;
			carea += (p.xpoints[i]+p.xpoints[iminus1])*(p.ypoints[i]-p.ypoints[iminus1]);
		}
		return (Math.abs(carea/2.0));
	}
		
	void savePoints(Roi roi) {
		if (imp==null) {
			rt.addValue("X", 0.0);
			rt.addValue("Y", 0.0);
			if (imp.getStackSize()>1)
				rt.addValue("Slice", 0.0);
			return;
		}
		if ((measurements&AREA)!=0)
			rt.addValue(ResultsTable.AREA,0);
		FloatPolygon p = roi.getFloatPolygon();
		ImageProcessor ip = imp.getProcessor();
		Calibration cal = imp.getCalibration();
		double x = p.xpoints[0];
		double y = p.ypoints[0];
		int ix=(int)x, iy=(int)y;
		double value = ip.getPixelValue(ix,iy);
		if (markWidth>0 && !Toolbar.getMultiPointMode()) {
			ip.setColor(Toolbar.getForegroundColor());
			ip.setLineWidth(markWidth);
			ip.moveTo(ix,iy);
			ip.lineTo(ix,iy);
			imp.updateAndDraw();
			ip.setLineWidth(Line.getWidth());
		}
		rt.addValue("X", cal.getX(x));
		rt.addValue("Y", cal.getY(y, imp.getHeight()));
		if (imp.isHyperStack() || imp.isComposite()) {
			if (imp.getNChannels()>1)
				rt.addValue("Ch", imp.getChannel());
			if (imp.getNSlices()>1)
				rt.addValue("Slice", imp.getSlice());
			if (imp.getNFrames()>1)
				rt.addValue("Frame", imp.getFrame());
		} else if (imp.getStackSize()>1)
			rt.addValue("Slice", cal.getZ(imp.getCurrentSlice()));
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
		//if ((measurements&MEAN)==0)
		//	rt.addValue("Mean", value);
	}

	String getFileName() {
		String s = "";
		if (imp!=null) {
			if (redirectTarget!=0) {
				ImagePlus rImp = WindowManager.getImage(redirectTarget);
				if (rImp==null) rImp = redirectImage;
				if (rImp!=null) s = rImp.getTitle();				
			} else
				s = imp.getTitle();
			//int len = s.length();
			//if (len>4 && s.charAt(len-4)=='.' && !Character.isDigit(s.charAt(len-1)))
			//	s = s.substring(0,len-4); 
			Roi roi = imp.getRoi();
			String roiName = roi!=null?roi.getName():null;
			if (roiName!=null && !roiName.contains(".")) {
				if (roiName.length()>30)
					roiName = roiName.substring(0,27) + "...";
				s += ":"+roiName;
			}
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

	/** Writes the last row in the system results table to the Results window. */
	public void displayResults() {
		int counter = rt.getCounter();
		if (counter==1)
			IJ.setColumnHeadings(rt.getColumnHeadings());
		TextPanel tp = IJ.isResultsWindow()?IJ.getTextPanel():null;
		int lineCount = tp!=null?IJ.getTextPanel().getLineCount():0;
		if (counter>lineCount+1) { // delete rt rows added by particle analyzer
			int n = counter - lineCount - 1;
			int index = lineCount;
			for (int i=0; i<n; i++)
				rt.deleteRow(index);
			counter = rt.getCounter();
		}
		IJ.write(rt.getRowAsString(counter-1));
	}

	/** Redisplays the results table. */
	public void updateHeadings() {
		rt.show("Results");
	}

	/** Converts a number to a formatted string with a tab at the end. */
	public String n(double n) {
		String s;
		if (Math.round(n)==n)
			s = ResultsTable.d2s(n,0);
		else
			s = ResultsTable.d2s(n,precision);
		return s+"\t";
	}
		
	void incrementCounter() {
		//counter++;
		if (rt==null) rt = systemRT;
		rt.incrementCounter();
		unsavedMeasurements = true;
	}
	
	public void summarize() {
		if (summarized)
			return;
		int n = rt.size();
		if (n<2)
			return;
		String[] headings = rt.getHeadings();
		int columns = headings.length;
		if (columns==0)
			return;
		int first = "Label".equals(headings[0])?1:0;
		double[] min = new double[columns];
		double[] max = new double[columns];
		double[] sum = new double[columns];
		double[] sum2 = new double[columns];
		for (int i=0; i<columns; i++) {
			min[i] = Double.MAX_VALUE;
			max[i] = -Double.MAX_VALUE;
		}
		for (int row=0; row<n; row++) {
			for (int col=first; col<columns; col++) {
				double v = rt.getValue(headings[col], row);
				if (v<min[col]) min[col]=v;
				if (v>max[col]) max[col]=v;
				sum[col]+=v;
				sum2[col]+=v*v;
			}
		}
		rt.incrementCounter(); rt.setLabel("Mean", n+0);
		rt.incrementCounter(); rt.setLabel("SD", n+1);
		rt.incrementCounter(); rt.setLabel("Min", n+2);
		rt.incrementCounter(); rt.setLabel("Max", n+3);
		for (int col=first; col<columns; col++) {
			rt.setValue(headings[col], n+0, sum[col]/n);
			//IJ.log(col+"  "+sum2[col]+"  "+sum[col]+"  "+n);
			rt.setValue(headings[col], n+1, Math.sqrt((sum2[col]-sum[col]*sum[col]/n)/(n-1)));
			rt.setValue(headings[col], n+2, min[col]);
			rt.setValue(headings[col], n+3, max[col]);
		}
		rt.show("Results");
		summarized = true;
	}

	/** Returns the current measurement count. */
	public static int getCounter() {
		return systemRT.size();
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
		//prefs.put(MARK_WIDTH, Integer.toString(markWidth));
		prefs.put(PRECISION, Integer.toString(precision));	}

	/** Returns an array containing the first 20 uncalibrated means. */
	public static float[] getUMeans() {
		return umeans;
	}

	/** Returns the default results table. This table should only
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

	public static void setOption(String option, boolean b) {
		if (option.contains("min"))
			showMin = b;
		else if (option.contains("angle"))
			showAngle = b;
	}
	
	public static void setResultsTable(ResultsTable rt) {
		TextPanel tp = IJ.isResultsWindow()?IJ.getTextPanel():null;
		if (tp!=null)
			tp.clear();
		if (rt==null)
			rt = new ResultsTable();
		rt.setPrecision((systemMeasurements&SCIENTIFIC_NOTATION)!=0?-precision:precision);
		rt.setNaNEmptyCells((systemMeasurements&NaN_EMPTY_CELLS)!=0);
		systemRT = rt;
		summarized = false;
		umeans = null;
		unsavedMeasurements = false;
	}
	
}
	
