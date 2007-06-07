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

/** This plugin implements ImageJ's Analyze/Measure and Analyze/Set Measurements commands. */
public class Analyzer implements PlugInFilter, Measurements {
	
	private String arg;
	private ImagePlus imp;
	private ResultsTable rt;
	private int measurements;
	private StringBuffer min,max,mean,sd;
	
	private static final int[] list = {AREA,MEAN,STD_DEV,MODE,MIN_MAX,
		CENTROID,CENTER_OF_MASS,PERIMETER,RECT,ELLIPSE,LIMIT,LABELS,INVERT_Y};

	private static final int UNDEFINED=0,AREAS=1,LENGTHS=2,ANGLES=3,MARK_AND_COUNT=4;
	private static int mode = UNDEFINED;
	private static final String MEASUREMENTS = "measurements";
	private static final String MARK_WIDTH = "mark.width";
	private static final String PRECISION = "precision";
	//private static int counter;
	private static boolean unsavedMeasurements;
	public static Color darkBlue = new Color(0,0,160);
	private static int systemMeasurements = Prefs.getInt(MEASUREMENTS,AREA+MEAN+MIN_MAX);
	public static int markWidth = Prefs.getInt(MARK_WIDTH,3);
	public static int precision = Prefs.getInt(PRECISION,3);
	private static float[] umeans = new float[MAX_STANDARDS];
	private static ResultsTable systemRT = new ResultsTable();

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
		else if (arg.equals("clear"))
			{clearWorksheet(); return DONE;}
		else
			return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		measure();
	}

	void doSetDialog() {
		GenericDialog gd = new GenericDialog("Set Measurements", IJ.getInstance());
		String[] labels = new String[10];
		boolean[] states = new boolean[10];
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
		gd.addCheckboxGroup(5, 2, labels, states);
		labels = new String[3];
		states = new boolean[3];
		labels[0]="Limit to Threshold"; states[0]=(systemMeasurements&LIMIT)!=0;
		labels[1]="Display Image Name"; states[1]=(systemMeasurements&LABELS)!=0;
		labels[2]="Invert Y Coordinates"; states[2]=(systemMeasurements&INVERT_Y)!=0;
		gd.addCheckboxGroup(2, 2, labels, states);
		gd.addMessage("");
		gd.addNumericField("Decimal Places:", precision, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		setOptions(gd);
		int prec = (int)gd.getNextNumber();
		if (prec>=0 && prec<=8 && prec!=precision) {
			precision = prec;
			rt.setPrecision(precision);
			if (mode==AREAS) {
				IJ.setColumnHeadings("");
				updateHeadings();
			}
		}
	}
	
	void clearWorksheet() {
		resetCounter();
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
		if ((oldMeasurements&(~LIMIT))!=(systemMeasurements&(~LIMIT)))
			mode = UNDEFINED;
		if ((systemMeasurements&LABELS)==0)
			systemRT.disableRowLabels();
	}
	
	void measure() {
		if (Toolbar.getToolId()==Toolbar.CROSSHAIR) {
			markAndCount();
			return;
		}
		Roi roi = imp.getRoi();
		if (roi!=null && roi.getType()>=Roi.LINE) {
			//if (roi.getType()==Roi.POLYLINE && ((PolygonRoi)roi).getAngle()>=0.0)
			//	measureAngle(roi);
			//else
			measureLength(roi);
			return;
		}
		if (mode!=AREAS) {
			if (!resetCounter())
				return;
			mode = AREAS;
		}
		ImageStatistics stats = imp.getStatistics(measurements);
		saveResults(stats, imp.getRoi());
		displayResults();
	}

	void markAndCount() {
		if (imp.getTitle().equals("Colors"))
			return;
		int x=-1, y=-1;
		ImageWindow win = imp.getWindow();
		if (win!=null) {
			Point p = win.getCanvas().getCursorLoc();
			x = p.x;
			y = p.y;
		}
		if (x<0)
			return;
		imp.killRoi();
		if (mode!=MARK_AND_COUNT) {
			if (!resetCounter())
				return;
			//IJ.setColumnHeadings(" \tX\tY\tValue");		
			mode = MARK_AND_COUNT;
		}
		incrementCounter();
		ImageProcessor ip = imp.getProcessor();
		Calibration cal = imp.getCalibration();
		ip.setCalibrationTable(cal.getCTable());
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
			rt.addLabel("Name", getFileName());
		rt.addValue("X", cal.getX(x));
		rt.addValue("Y", cal.getY(updateY(y,imp.getHeight())));
		rt.addValue("Value", value);
		displayResults();
		//IJ.write(rt.getCounter()+"\t"+n(cal.getX(x))+n(cal.getY(y))+n(value));
	}
	
	void measureAngle(Roi roi) {
		if (mode!=ANGLES) {
			if (!resetCounter())
				return;
			IJ.setColumnHeadings(" \tangle");		
			mode = ANGLES;
		}
		incrementCounter();
		IJ.write(rt.getCounter()+"\t"+n(((PolygonRoi)roi).getAngle()));
	}
	
	void measureLength(Roi roi) {
		if (mode!=LENGTHS) {
			if (!resetCounter())
				return;
			if ((measurements&LABELS)!=0)
				IJ.setColumnHeadings(" \tName\tlength");
			else		
				IJ.setColumnHeadings(" \tlength");
			mode = LENGTHS;
		}
		incrementCounter();
		if ((measurements&LABELS)!=0)
			rt.addLabel("Name", getFileName());
		rt.addValue("Length", roi.getLength());
		displayResults();
	}
	
	/** Saves the measurements specified in the "Set Measurements" dialog,
		or by calling setMeasurments(), in the system results table.
	*/
	public void saveResults(ImageStatistics stats, Roi roi) {
		incrementCounter();
		int counter = rt.getCounter();
		if (counter<=MAX_STANDARDS) {
			if (umeans==null) umeans = new float[MAX_STANDARDS];
			umeans[counter-1] = (float)stats.umean;
		}
		if ((measurements&LABELS)!=0)
			rt.addLabel("Name", getFileName());
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
			rt.addValue(ResultsTable.Y_CENTROID,updateY(stats.yCentroid));
		}
		if ((measurements&CENTER_OF_MASS)!=0) {
			rt.addValue(ResultsTable.X_CENTER_OF_MASS,stats.xCenterOfMass);
			rt.addValue(ResultsTable.Y_CENTER_OF_MASS,updateY(stats.yCenterOfMass));
		}
		if ((measurements&PERIMETER)!=0) {
			double perimeter;
			if (roi!=null)
				perimeter = roi.getLength();
			else
				perimeter = 0.0;
			rt.addValue(ResultsTable.PERIMETER,perimeter);
		}
		if ((measurements&RECT)!=0) {
			rt.addValue(ResultsTable.ROI_X,stats.roiX);
			rt.addValue(ResultsTable.ROI_Y,updateY2(stats.roiY));
			rt.addValue(ResultsTable.ROI_WIDTH,stats.roiWidth);
			rt.addValue(ResultsTable.ROI_HEIGHT,stats.roiHeight);
		}
		if ((measurements&ELLIPSE)!=0) {
			rt.addValue(ResultsTable.MAJOR,stats.major);
			rt.addValue(ResultsTable.MINOR,stats.minor);
			rt.addValue(ResultsTable.ANGLE,stats.angle);
		}
	}
	
	// Update centroid and center of mass y-coordinate
	// based on value "Invert Y Coordinates" flag
	double updateY(double y) {
		if (imp==null)
			return y;
		else {
			if ((systemMeasurements&INVERT_Y)!=0) {
				Calibration cal = imp.getCalibration();
				y = imp.getHeight()*cal.pixelHeight-y;
			}
			return y;
		}
	}

	// Update bounding rectangle y-coordinate based
	// on value "Invert Y Coordinates" flag
	double updateY2(double y) {
		if (imp==null)
			return y;
		else {
			if ((systemMeasurements&INVERT_Y)!=0) {
				Calibration cal = imp.getCalibration();
				y = imp.getHeight()*cal.pixelHeight-y-cal.pixelHeight;
			}
			return y;
		}
	}

	String getFileName() {
		String s = "";
		if (imp!=null) {
			s = imp.getTitle();
			if (imp.getStackSize()>1) {
				ImageStack stack = imp.getStack();
				int currentSlice = imp.getCurrentSlice();
				String label = stack.getSliceLabel(currentSlice);
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
		if (mode==MARK_AND_COUNT) 
			summarizePoints(rt);
		else if (mode==LENGTHS) 
			add2(rt.getColumnIndex("Length"));
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
	}
	
	void summarizePoints(ResultsTable rt) {
		add2(rt.getColumnIndex("X"));
		add2(rt.getColumnIndex("Y"));
		add2(rt.getColumnIndex("Value"));
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
		if (counter>0 && lineCount>0 && unsavedMeasurements && !IJ.macroRunning()) {
			SaveChangesDialog d = new SaveChangesDialog(IJ.getInstance(), "Save "+counter+" measurements?");
			if (d.cancelPressed())
				return false;
			else if (d.savePressed())
				new MeasurementsWriter().run("");
		}
		umeans = null;
		systemRT.reset();
		unsavedMeasurements = false;
		if (tp!=null) {
			tp.selectAll();
			tp.clearSelection();
		}
		return true;
	}
	
	public static void setSaved() {
		unsavedMeasurements = false;
	}
	
	// Returns the measurements defined in the Set Measurements dialog. */
	public static int getMeasurements() {
		return systemMeasurements;
	}

	// Sets the system-wide measurements. */
	public static void setMeasurements(int measurements) {
		systemMeasurements = measurements;
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

	/** Returns the ImageJ results table. */
	public static ResultsTable getResultsTable() {
		return systemRT;
	}

	/** Returns the number of digits displayed on the right of decimal point. */
	public static int getPrecision() {
		return precision;
	}

	/** Returns an updated Y coordinate based on
		the current "Invert Y Coordinates" flag. */
	public static int updateY(int y, int imageHeight) {
		if ((systemMeasurements&INVERT_Y)!=0)
			y = imageHeight-y-1;
		return y;
	}
}
	
