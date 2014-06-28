package ij.gui;

import java.awt.*;
import java.util.*;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.util.*;
import ij.plugin.filter.Analyzer;
import ij.macro.Interpreter;
import ij.measure.Calibration;

/*		Plots are now accepting ArrayList input and allowing to display arrow
 *		plots, logarithmic (log in x and/or y) plots, minor ticks (decimal and
 *		logarithmic),(Philippe CARL, CNRS, philippe.carl (AT) unistra.fr)
 *
 */

/** This class is an image that line graphs can be drawn on and displayed. */
public class Plot {

	/** Text justification. */
	public static final int LEFT=0, CENTER=1, RIGHT=2;
	/** Display points using a circle 5 pixels in diameter. */
	public static final int CIRCLE = 0;
	/** Display points using an X-shaped mark. */
	public static final int X = 1;
	/** Display points using an box-shaped mark. */
	public static final int BOX = 3;
	/** Display points using an tiangular mark. */
	public static final int TRIANGLE = 4;
	/** Display points using an cross-shaped mark. */
	public static final int CROSS = 5;
	/** Display points using a single pixel. */
	public static final int DOT = 6;
	/** Connect points with solid lines. */
	public static final int LINE = 2;
	///** flag multiplier for maximum number of ticks&grid lines along x */
	//public static final int X_INTERVALS_M = 0x1;
	///** flag multiplier for maximum number of ticks&grid lines along y */
	//public static final int Y_INTERVALS_M = 0x100;
	
	/** flag for numeric labels of x-axis ticks */
	public static final int X_NUMBERS = 0x1;
	/** flag for numeric labels of x-axis ticks */
	public static final int Y_NUMBERS = 0x2;
	/** flag for drawing x-axis ticks */
	public static final int X_TICKS = 0x4;
	/** flag for drawing x-axis ticks */
	public static final int Y_TICKS = 0x8;
	/** flag for drawing vertical grid lines for x-axis ticks */
	public static final int X_GRID = 0x10;
	/** flag for drawing horizontal grid lines for y-axis ticks */
	public static final int Y_GRID = 0x20;
	/** flag for forcing frame to coincide with the grid/ticks in x direction (results in unused space) */
	public static final int X_FORCE2GRID = 0x40;
	/** flag for forcing frame to coincide with the grid/ticks in y direction (results in unused space) */
	public static final int Y_FORCE2GRID = 0x80;
	/** flag for drawing x-axis minor ticks */
	public static final int X_MINOR_TICKS = 0x100;
	/** flag for drawing y-axis minor ticks */
	public static final int Y_MINOR_TICKS = 0x200;
	/** flag for logarithmic labels of x-axis ticks */
	public static final int X_LOG_NUMBERS = 0x400;
	/** flag for logarithmic numeric labels of x-axis ticks */
	public static final int Y_LOG_NUMBERS = 0x800;
	/** the default flags */
	public static final int DEFAULT_FLAGS =	 X_NUMBERS + Y_NUMBERS + /*X_TICKS + Y_TICKS +*/ X_GRID + Y_GRID; 
	/** the margin width left of the plot frame (enough for 5-digit numbers such as unscaled 16-bit */
	public static final int LEFT_MARGIN = 60;
	/** the margin width right of the plot frame */
	public static final int RIGHT_MARGIN = 18;
	/** the margin width above the plot frame */
	public static final int TOP_MARGIN = 15;
	/** the margin width below the plot frame */
	public static final int BOTTOM_MARGIN = 40;

	private static		 int MAX_INTERVALS = 12;			//maximum number of intervals between ticks or grid lines
	private static final int MIN_X_GRIDWIDTH = 60;			//minimum distance between grid lines or ticks along x
	private static final int MIN_Y_GRIDWIDTH = 40;			//minimum distance between grid lines or ticks along y
	private static		 int TICK_LENGTH = 6;			//length of ticks
	private static		 int MINOR_TICK_LENGTH = 4;			//length of minor ticks
	private final Color gridColor = new Color(0xc0c0c0);		//light gray
	private int frameWidth;
	private int frameHeight;
	
	Rectangle frame = null;
	float[] xValues, yValues;
	float[] errorBars;
	int nPoints;
	double xMin, xMax, yMin, yMax;
	
	private double xScale, yScale;
	private static String defaultDirectory = null;
	private String xLabel;
	private String yLabel;
	private int flags;
	private Font font		= new Font("Helvetica", Font.PLAIN, 12);
	private Font fontMedium = new Font("Helvetica", Font.PLAIN, 10);
	private Font fontSmall	= new Font("Helvetica", Font.PLAIN, 9 );
	private Font xLabelFont = font;
	private Font yLabelFont = font;
	private boolean fixedYScale;
	private int lineWidth = 1; // Line.getWidth();
	private int markSize = 5;
	private int minimalArrowSize = 5;
	private ImageProcessor ip;
	private String title;
	private boolean initialized;
	private boolean plotDrawn;
	private int plotWidth = PlotWindow.plotWidth;
	private int plotHeight = PlotWindow.plotHeight;
	private boolean multiplePlots;
	private boolean drawPending;
	private PlotMaker plotMaker;
	private float[] previousYValues;
	
	/** keeps a reference to all of the	 that is going to be plotted. */
	ArrayList storedData;
	
	/** Construct a new PlotWindow.
	 * @param title the window title
	 * @param xLabel	the x-axis label
	 * @param yLabel	the y-axis label
	 * @param xValues	the x-coodinates, or null
	 * @param yValues	the y-coodinates, or null
	 */
	public Plot(String title, String xLabel, String yLabel, float[] xValues, float[] yValues) {
		this(title, xLabel, yLabel, xValues, yValues, DEFAULT_FLAGS);
	}

	/** This version of the constructor accepts double arrays. */
	public Plot(String title, String xLabel, String yLabel, double[] xValues, double[] yValues) {
		this(title, xLabel, yLabel, xValues!=null?Tools.toFloat(xValues):null, yValues!=null?Tools.toFloat(yValues):null, DEFAULT_FLAGS);
	}

	/** This is a constructor that works with JavaScript. */
	public Plot(String dummy, String title, String xLabel, String yLabel, float[] xValues, float[] yValues) {
		this(title, xLabel, yLabel, xValues, yValues, DEFAULT_FLAGS);
	}

	/** This is a version of the constructor with no intial arrays. */
	public Plot(String title, String xLabel, String yLabel) {
		this(title, xLabel, yLabel, (float[])null, (float[])null, DEFAULT_FLAGS);
	}

	/** This is a version of the constructor with no intial arrays. */
	public Plot(String title, String xLabel, String yLabel, int flags) {
		this(title, xLabel, yLabel, (float[])null, (float[])null, flags);
	}	

	/** This version of the constructor has a 'flags' argument for
		controlling the appearance of ticks, grid, etc. The default is
		Plot.X_NUMBERS+Plot.Y_NUMBERS+Plot.X_GRID+Plot.Y_GRID.
	*/
	public Plot(String title, String xLabel, String yLabel, float[] xValues, float[] yValues, int flags) {
		this.title = title;
		this.xLabel = xLabel;
		this.yLabel = yLabel;
		this.flags = flags;
		storedData = new ArrayList();
		if ((xValues==null || xValues.length==0) && yValues!=null) {
			xValues = new float[yValues.length];
			for (int i=0; i<yValues.length; i++)
				xValues[i] = i;
		}
		if (xValues==null) {
			xValues = new float[0];
			yValues = new float[0];
		} else
			store(xValues, yValues);
		this.xValues = xValues;
		this.yValues = yValues;		

		double[] a = Tools.getMinMax(xValues);
		xMin=a[0]; xMax=a[1];
		a = Tools.getMinMax(yValues);
		yMin=a[0]; yMax=a[1];
		fixedYScale = false;
		nPoints = Math.min(xValues.length, yValues.length);
		drawPending = true;
	}

	private float[] arrayToLog(float[] val){
		float[] newVal = new float[val.length];
		for (int i=0; i<newVal.length; i++)
			newVal[i] = (float) (Math.log10(val[i]));
		return newVal;
	}

	/** This version of the constructor accepts double arrays and has a 'flags' argument. */
	public Plot(String title, String xLabel, String yLabel, double[] xValues, double[] yValues, int flags) {
		this(title, xLabel, yLabel, xValues!=null?Tools.toFloat(xValues):null, yValues!=null?Tools.toFloat(yValues):null, flags);
	}

	/** Sets the x-axis and y-axis range. */
	public void setLimits(double xMin, double xMax, double yMin, double yMax) {
		if ((flags&X_LOG_NUMBERS)!=0) {
			xMin = Math.log10(xMin);
			xMax = Math.log10(xMax);
		}
		if ((flags&Y_LOG_NUMBERS)!=0) {
			yMin = Math.log10(yMin);
			yMax = Math.log10(yMax);
		}
		if (!Double.isNaN(xMin)) this.xMin = xMin;	//ignore invalid ranges, esp in log scale
		if (!Double.isNaN(xMax)) this.xMax = xMax;
		if (!Double.isNaN(yMin)) this.yMin = yMin;
		if (!Double.isNaN(yMax)) this.yMax = yMax;
		fixedYScale = true;
		if (initialized) {
			ip.setColor(Color.white);
			ip.resetRoi();
			ip.fill();
			ip.setColor(Color.black);
			setScaleAndDrawAxisLabels();
		}
	}

	/** Sets the canvas size (i.e., size of the resulting ImageProcessor).
	 * By default, the size is adjusted for the plot frame size specified
	 * in Edit>Options>Profile Plot Options. */
	public void setSize(int width, int height) {
		if (!initialized && width>LEFT_MARGIN+RIGHT_MARGIN+20 && height>TOP_MARGIN+BOTTOM_MARGIN+20) {
			plotWidth = width-LEFT_MARGIN-RIGHT_MARGIN;
			plotHeight = height-TOP_MARGIN-BOTTOM_MARGIN;
		}
	}

	/** Sets the plot frame size in pixels. */
	public void setFrameSize(int width, int height) {
			plotWidth = width;
			plotHeight = height;
	}

	/** Sets the maximum number of intervals in a plot. */
	public void setMaxIntervals(int intervals) {
			MAX_INTERVALS = intervals;
	}

	/** Sets the length of the major tick in pixels. */
	public void setTickLength(int tickLength) {
			TICK_LENGTH = tickLength;
	}

	/** Sets the length of the minor tick in pixels. */
	public void setMinorTickLength(int minorTickLength) {
			MINOR_TICK_LENGTH = minorTickLength;
	}

	/** Sets the X Axis format to Log or Linear. */
	public void setAxisXLog(boolean axisXLog) {
		if(axisXLog && ((flags&X_LOG_NUMBERS)==0)) {
			flags += X_LOG_NUMBERS;
			if((flags&X_NUMBERS)!=0)
				flags -= X_NUMBERS;
		}
		else if(!axisXLog && ((flags&X_LOG_NUMBERS)!=0)) {
			flags -= X_LOG_NUMBERS;
			if((flags&X_NUMBERS)==0)
				flags += X_NUMBERS;
		}
	}

	/** Sets the Y Axis format to Log or Linear. */
	public void setAxisYLog(boolean axisYLog) {
		if(axisYLog && ((flags&Y_LOG_NUMBERS)==0)) {
			flags += Y_LOG_NUMBERS;
			if((flags&Y_NUMBERS)!=0)
				flags -= Y_NUMBERS;
		}
		else if(!axisYLog && ((flags&Y_LOG_NUMBERS)!=0)) {
			flags -= Y_LOG_NUMBERS;
			if((flags&Y_NUMBERS)==0)
				flags += Y_NUMBERS;
		}
	}

	/** Sets the X Axis ticks. */
	public void setXTicks(boolean XTicks) {
		if(XTicks && ((flags&X_TICKS)==0))
			flags += X_TICKS;
		else if(!XTicks && ((flags&X_TICKS)!=0))
			flags -= X_TICKS;
	}

	/** Sets the Y Axis ticks. */
	public void setYTicks(boolean YTicks) {
		if(YTicks && ((flags&Y_TICKS)==0))
			flags += Y_TICKS;
		else if(!YTicks && ((flags&Y_TICKS)!=0))
			flags -= Y_TICKS;
	}

	/** Sets the X Axis minor ticks. */
	public void setXMinorTicks(boolean XMinorTicks) {
		if(XMinorTicks && ((flags&X_MINOR_TICKS)==0)) {
			flags += X_MINOR_TICKS;
			if((flags&X_TICKS)==0)
				flags += X_TICKS;
		}
		else if(!XMinorTicks && ((flags&X_MINOR_TICKS)!=0))
			flags -= X_MINOR_TICKS;
	}

	/** Sets the Y Axis minor ticks. */
	public void setYMinorTicks(boolean YMinorTicks) {
		if(YMinorTicks && ((flags&Y_MINOR_TICKS)==0)) {
			flags += Y_MINOR_TICKS;
			if((flags&Y_TICKS)==0)
				flags += Y_TICKS;
		}
		else if(!YMinorTicks && ((flags&Y_MINOR_TICKS)!=0))
			flags -= Y_MINOR_TICKS;
	}

	/** Sets the properties of the axes. */
	public void setAxes(boolean xLog, boolean yLog, boolean xTicks, boolean yTicks, boolean xMinorTicks, boolean yMinorTicks, int tickLenght, int minorTickLenght) {
		setAxisXLog		  (xLog);
		setAxisYLog		  (yLog);
		setXMinorTicks	  (xMinorTicks);
		setYMinorTicks	  (yMinorTicks);
		setXTicks		  (xTicks);
		setYTicks		  (yTicks);
		setTickLength	  (tickLenght);
		setMinorTickLength(minorTickLenght);
	}
	
	public void setLogScaleX() {
		setAxisXLog(true);
		setXMinorTicks(true);
		setYMinorTicks (true);
		setTickLength(8);
	}

	public void setLogScaleY() {
		setAxisYLog(true);
		setXMinorTicks(true);
		setYMinorTicks (true);
		setTickLength(8);
	}

	/** Adds a set of points to the plot or adds a curve if shape is set to LINE.
	 * @param x			the x-coodinates
	 * @param y			the y-coodinates
	 * @param shape		CIRCLE, X, BOX, TRIANGLE, CROSS, DOT or LINE
	 */
	public void addPoints(float[] x, float[] y, int shape) {
		setup();
		switch(shape) {
			case CIRCLE: case X:  case BOX: case TRIANGLE: case CROSS: case DOT:
				ip.setClipRect(frame);
				for (int i=0; i<x.length; i++)
					drawShape(shape, scaleX(x[i]), scaleY(y[i]), markSize);
				ip.setClipRect(null);
				break;
			case LINE:
				drawFloatPolyline(ip, ((flags&X_LOG_NUMBERS)!=0) ? arrayToLog(x) : x, ((flags&Y_LOG_NUMBERS)!=0) ? arrayToLog(y) : y, x.length);
				break;
		}
		multiplePlots = true;
		if (xValues==null || xValues.length==0) {
			xValues = x;
			yValues = y;
			nPoints = x.length;
			drawPending = false;
		}
		if (shape==DOT || shape==LINE || !duplicate(y,previousYValues))
			store(x, y);
		previousYValues = y;
	}
	
	private boolean duplicate(float[] current, float[] previous) {
		if (current==null || previous==null || current.length!=previous.length)
			return false;
		for (int i=0; i<current.length; i++) {
			if (current[i]!=previous[i])
				return false;
		}
		return true;
	}

	/** Adds a set of points to the plot using double arrays.
	 * Must be called before the plot is displayed. */
	public void addPoints(double[] x, double[] y, int shape) {
		addPoints(Tools.toFloat(x), Tools.toFloat(y), shape);
	}
	
	/** This a version of addPoints that works with JavaScript. */
	public void addPoints(String dummy, float[] x, float[] y, int shape) {
		addPoints(x, y, shape);
	}

	/** Adds a set of points to the plot using double ArrayLists.
	 * Must be called before the plot is displayed. */	
	public void addPoints(ArrayList x, ArrayList y, int shape) {
		addPoints(getDoubleFromArrayList(x), getDoubleFromArrayList(y), shape);
	}	

	/** Adds a set of points to the plot or adds a curve if shape is set to LINE.
	 * @param x			the x-coodinates
	 * @param y			the y-coodinates
	 * @param errorBars			the errorBars
	 * @param shape		CIRCLE, X, BOX, TRIANGLE, CROSS, DOT or LINE
	 */
	public void addPoints(double[] x, double[] y, double[] errorBars, int shape) {
		addPoints(Tools.toFloat(x), Tools.toFloat(y), shape);
		drawErrorBars(Tools.toFloat(x), Tools.toFloat(y), Tools.toFloat(errorBars));
	}

	/** Adds a set of points to the plot using double ArrayLists.
	 * Must be called before the plot is displayed. */	
	public void addPoints(ArrayList x, ArrayList y, ArrayList z, int shape) {
		addPoints(getDoubleFromArrayList(x), getDoubleFromArrayList(y), getDoubleFromArrayList(z), shape);
	}	

	public double[] getDoubleFromArrayList(ArrayList list) {
		double[] targ = new double[list.size()];
		for (int i = 0; i < list.size(); i++)
			targ[i] = ((Double) list.get(i)).doubleValue();
		return targ;
	}
	
	void drawShape(int shape, int x, int y, int size) {
		int xbase = x-size/2;
		int ybase = y-size/2;
		switch(shape) {
			case X:
				ip.drawLine(xbase,ybase,xbase+size,ybase+size);
				ip.drawLine(xbase+size,ybase,xbase,ybase+size);
				break;
			case BOX:
				ip.drawLine(xbase,ybase,xbase+size,ybase);
				ip.drawLine(xbase+size,ybase,xbase+size,ybase+size);
				ip.drawLine(xbase+size,ybase+size,xbase,ybase+size);
				ip.drawLine(xbase,ybase+size,xbase,ybase);
				break;
			case TRIANGLE:
				ip.drawLine(x,ybase,xbase+size,ybase+size);
				ip.drawLine(xbase+size,ybase+size,xbase,ybase+size);
				ip.drawLine(xbase,ybase+size,x,ybase);
				break;
			case CROSS:
				ip.drawLine(xbase,y,xbase+size,y);
				ip.drawLine(x,ybase,x,ybase+size);
				break;
			case DOT:
				ip.drawDot(x, y);
				break;
			default: // 5x5 oval
				ip.drawLine(x-1, y-2, x+1, y-2);
				ip.drawLine(x-1, y+2, x+1, y+2);
				ip.drawLine(x+2, y+1, x+2, y-1);
				ip.drawLine(x-2, y+1, x-2, y-1);
				break;
		}
	}

	/** Adds a set of points that will be drawn as ARROWs.
	 * @param x1		the x-coodinates of the beginning of the arrow
	 * @param y1		the y-coodinates of the beginning of the arrow
	 * @param x2		the x-coodinates of the end		  of the arrow
	 * @param y2		the y-coodinates of the end		  of the arrow
	 */
	public void drawVectors(double[] x1, double[] y1, double[] x2, double[] y2) {
		setup();
		ip.setClipRect(frame);
		int arrowSize = 5;
		for (int i=0; i<x1.length; i++) {
			int xt1 = LEFT_MARGIN + (int)((x1[i]-xMin)*xScale);
			int yt1 = TOP_MARGIN + frameHeight - (int)((y1[i]-yMin)*yScale);
			int xt2 = LEFT_MARGIN + (int)((x2[i]-xMin)*xScale);
			int yt2 = TOP_MARGIN + frameHeight - (int)((y2[i]-yMin)*yScale);
			double dist = calculateDistance(xt1, yt1, xt2, yt2);
			if(xt1==xt2 && yt1==yt2)
				ip.drawDot(xt1, yt1);
			else if(dist > arrowSize)
				if(dist > minimalArrowSize)
					drawArrow(xt1, yt1, xt2, yt2, dist / minimalArrowSize);
				else
					drawArrow(xt1, yt1, xt2, yt2, minimalArrowSize);
			else
				ip.drawLine(xt1, yt1, xt2, yt2);
		}
		ip.setClipRect(null);
		multiplePlots = true;
		if (xValues==null || xValues.length==0) {
			xValues = Tools.toFloat(x1);
			yValues = Tools.toFloat(y1);
			nPoints = x1.length;
			drawPending = false;
		}
		store(Tools.toFloat(x1), Tools.toFloat(y1));
	}

	public double calculateDistance(int x1, int y1, int x2, int y2) {
		return java.lang.Math.sqrt(java.lang.Math.pow(x2 - x1, 2.0) + java.lang.Math.pow(y2 - y1, 2.0));
	}

	public void drawArrow(int x1, int y1, int x2, int y2, double size) {
		double dx = x2 - x1;
		double dy = y2 - y1;
		double ra = java.lang.Math.sqrt(dx * dx + dy * dy);
		dx /= ra;
		dy /= ra;
		int x3 = (int) Math.round(x2 - dx * size);
		int y3 = (int) Math.round(y2 - dy * size);
		double r = 0.3 * size;
		int x4 = (int) Math.round(x3 + dy * r);
		int y4 = (int) Math.round(y3 - dx * r);
		int x5 = (int) Math.round(x3 - dy * r);
		int y5 = (int) Math.round(y3 + dx * r);
		ip.moveTo(x1, y1); ip.lineTo(x2, y2);
		ip.moveTo(x4, y4); ip.lineTo(x2, y2); ip.lineTo(x5, y5);
	}	
	
	/** Adds a set of vectors to the plot using double ArrayLists.
	 * Must be called before the plot is displayed. */	
	public void drawVectors(ArrayList x1, ArrayList y1, ArrayList x2, ArrayList y2) {
		drawVectors(getDoubleFromArrayList(x1), getDoubleFromArrayList(y1), getDoubleFromArrayList(x2), getDoubleFromArrayList(y2));
	}	

	/** Adds error bars to the plot. */
	public void addErrorBars(float[] errorBars) {
		this.errorBars = errorBars	;
	}
	
	/** Adds error bars to the plot. */
	public void addErrorBars(double[] errorBars) {
		addErrorBars(Tools.toFloat(errorBars));
	}
	
	/** This is a version of addErrorBars that works with JavaScript. */
	public void addErrorBars(String dummy, float[] errorBars) {
		addErrorBars(errorBars);
	}

	/** Draws text at the specified location, where (0,0)
	 * is the upper left corner of the the plot frame and (1,1) is
	 * the lower right corner. */
	public void addLabel(double x, double y, String label) {
		setup();
		int xt = LEFT_MARGIN + (int)(x*frameWidth);
		int yt = TOP_MARGIN + (int)(y*frameHeight);
		ip.drawString(label, xt, yt);
	}
	
		/* Draws text at the specified location, using the coordinate system defined
				by setLimits() and the justification specified by setJustification(). */
	//	public void addText(String text, double x, double y) {
	//		setup();
	//		int xt = LEFT_MARGIN + (int)((x-xMin)*xScale);
	//		int yt = TOP_MARGIN + frameHeight - (int)((y-yMin)*yScale);
	//		if (justification==CENTER)
	//			xt -= ip.getStringWidth(text)/2;
	//		else if (justification==RIGHT)
	//			xt -= ip.getStringWidth(text);
	//		ip.drawString(text, xt, yt);
	//	}
	
	/** Sets the justification used by addLabel(), where <code>justification</code>
	 * is Plot.LEFT, Plot.CENTER or Plot.RIGHT. */
	public void setJustification(int justification) {
		setup();
		ip.setJustification(justification);
	}
	
	/** Changes the drawing color. For selecting the color of the  passed with the constructor,
	 * use <code>setColor</code> before <code>draw</code>.
	 * The frame and labels are always drawn in black. */
	public void setColor(Color c) {
		setup();
		if (!(ip instanceof ColorProcessor)) {
			ip = ip.convertToRGB();
			ip.setLineWidth(lineWidth);
			ip.setFont(font);
			ip.setAntialiasedText(true);
		}
		ip.setColor(c);
	}
	
	/** Changes the line width. */
	public void setLineWidth(int lineWidth) {
		if (lineWidth<1) lineWidth = 1;
		setup();
		ip.setLineWidth(lineWidth);
		this.lineWidth = lineWidth;
		markSize = lineWidth==1?5:7;
	}
	
	/* Draws a line using the coordinate system defined by setLimits(). */
	public void drawLine(double x1, double y1, double x2, double y2) {
		setup();
		ip.drawLine(scaleX(x1), scaleY(y1), scaleX(x2), scaleY(y2));
	}
	
	/** Draws a line using a normalized 0-1, 0-1 coordinate system,
	 * with (0,0) at the top left and (1,1) at the lower right corner.
	 * This is the same coordinate system used by addLabel(x,y,label).
	 */
	public void drawNormalizedLine(double x1, double y1, double x2, double y2) {
		setup();
		int ix1 = LEFT_MARGIN + (int)(x1*frameWidth);
		int iy1 = TOP_MARGIN  + (int)(y1*frameHeight);
		int ix2 = LEFT_MARGIN + (int)(x2*frameWidth);
		int iy2 = TOP_MARGIN  + (int)(y2*frameHeight);
		ip.drawLine(ix1, iy1, ix2, iy2);
	}

	/* Draws a line using the coordinate system defined by setLimits(). */
	public void drawDottedLine(double x1, double y1, double x2, double y2, int step) {
		setup();
		int ix1 = scaleX(x1);
		int iy1 = scaleY(y1);
		int ix2 = scaleX(x2);
		int iy2 = scaleY(y2);
		for(int i = ix1; i <= ix2; i = i + step)
			for(int j = iy1; j <= iy2; j = j + step)
				ip.drawDot(i, j);
	}

	private int  scaleX(double x) {
		if ((flags&X_LOG_NUMBERS)!=0)
			return LEFT_MARGIN+(int)Math.round((Math.log10(x)-xMin)*xScale);
		else
			return LEFT_MARGIN+(int)Math.round((x-xMin)*xScale);
	}

	private int  scaleY(double y) {
		if ((flags&Y_LOG_NUMBERS)!=0)
			return TOP_MARGIN +frameHeight-(int)Math.round((Math.log10(y)-yMin)*yScale);
		else
			return TOP_MARGIN+frameHeight-(int)Math.round((y-yMin)*yScale)	;
	}

	/** Sets the font. */
	public void setFont(Font font) {
		setup();
		ip.setFont(font);
		this.font = font;
	}

	/** Sets the xLabelFont. */
	public void setXLabelFont(Font font) {
		xLabelFont = font;
	}

	/** Sets the yLabelFont. */
	public void setYLabelFont(Font font) {
		yLabelFont = font;
	}

	/** Obsolete; replaced by setFont(). */
	public void changeFont(Font font) {
		setFont(font);
	}

	void setup() {
		if (initialized)
			return;
		initialized = true;
		createImage();
		ip.setColor(Color.black);
		if (lineWidth>3)
			lineWidth = 3;
		ip.setLineWidth(lineWidth);
		ip.setFont(font);
		ip.setAntialiasedText(true);
		if (frameWidth==0) {
			frameWidth = plotWidth;
			frameHeight = plotHeight;
		}
		frame = new Rectangle(LEFT_MARGIN, TOP_MARGIN, frameWidth, frameHeight);
		setScaleAndDrawAxisLabels();
	}
	
	void setScaleAndDrawAxisLabels() {
		if ((xMax-xMin)==0.0)
			xScale = 1.0;
		else
			xScale = frame.width/(xMax-xMin);
		if ((yMax-yMin)==0.0)
			yScale = 1.0;
		else
			yScale = frame.height/(yMax-yMin);
		if (PlotWindow.noGridLines)
			drawAxisLabels();
		else
			drawTicksEtc();
	}

	// draw simple axis labels with min&max value only
	void drawAxisLabels() {
		int digits = getDigits(yMin, xMax, 0.001*(yMax-yMin), 8);
		String s = IJ.d2s(yMax, digits);
		int sw = ip.getStringWidth(s);
		if ((flags&Y_LOG_NUMBERS)!=0) {
			if ((sw+4)>LEFT_MARGIN) {
				ip.setFont(fontSmall);
				ip.drawString(s, 4-ip.getStringWidth(s)/2+6, TOP_MARGIN-4-7);
				ip.setFont(font);
				String t = "10";
				ip.drawString(t, 4-10, TOP_MARGIN-4+2);
			} else {
				ip.setFont(fontSmall);
				ip.drawString(s, LEFT_MARGIN-ip.getStringWidth(s)-4-ip.getStringWidth(s)/2+6, TOP_MARGIN+10-7);
				ip.setFont(font);
				String t = "10";
				ip.drawString(t, LEFT_MARGIN-ip.getStringWidth(s)-4-10, TOP_MARGIN+10+2);
			}
		} else {
			if ((sw+4)>LEFT_MARGIN)
				ip.drawString(s, 4, TOP_MARGIN-4);
			else
				ip.drawString(s, LEFT_MARGIN-ip.getStringWidth(s)-4, TOP_MARGIN+10);
		}
		s = IJ.d2s(yMin, digits);
		sw = ip.getStringWidth(s);
		if ((flags&Y_LOG_NUMBERS)!=0) {
			if ((sw+4)>LEFT_MARGIN) {
				ip.setFont(fontSmall);
				ip.drawString(s, 4-ip.getStringWidth(s)/2+6, TOP_MARGIN+frame.height-7);
				ip.setFont(font);
				String t = "10";
				ip.drawString(t, 4-10, TOP_MARGIN+frame.height+2);
			} else {
				ip.setFont(fontSmall);
				ip.drawString(s, LEFT_MARGIN-ip.getStringWidth(s)-4-ip.getStringWidth(s)/2+6, TOP_MARGIN+frame.height-7);
				ip.setFont(font);
				String t = "10";
				ip.drawString(t, LEFT_MARGIN-ip.getStringWidth(s)-4-10, TOP_MARGIN+frame.height+2);
			}
		} else {		
			if ((sw+4)>LEFT_MARGIN)
				ip.drawString(s, 4, TOP_MARGIN+frame.height);
			else
				ip.drawString(s, LEFT_MARGIN-ip.getStringWidth(s)-4, TOP_MARGIN+frame.height);
		}
		FontMetrics fm = ip.getFontMetrics();
		int x = LEFT_MARGIN;
		int y = TOP_MARGIN + frame.height + fm.getAscent() + 6;
		digits = getDigits(xMin, xMax, 0.001*(xMax-xMin), 8);
		if ((flags&X_LOG_NUMBERS)!=0) {
			ip.setFont(fontSmall);
			ip.drawString(IJ.d2s(xMin,digits), x-ip.getStringWidth(s)/2+6, y-7);
			ip.drawString(IJ.d2s(xMax,digits), x+frame.width-ip.getStringWidth(s)*3/2+12, y-7);
			ip.setFont(font);
			String t = "10";
			ip.drawString(t, x-10, y+2);
			ip.drawString(t, x + frame.width-ip.getStringWidth(s)-10, y+2);
		} else {
			ip.drawString(IJ.d2s(xMin,digits), x, y);
			ip.drawString(IJ.d2s(xMax,digits), x+frame.width-ip.getStringWidth(s)+6, y);
		}
		if (xLabelFont != font) {
			ip.setFont(xLabelFont);
			ip.drawString(xLabel, LEFT_MARGIN+(frame.width-ip.getStringWidth(xLabel))/2, y+3);
			ip.setFont(font);
		} else 
			ip.drawString(xLabel, LEFT_MARGIN+(frame.width-ip.getStringWidth(xLabel))/2, y+3);
		if (yLabelFont != font) {
			ip.setFont(yLabelFont);
			drawYLabel(yLabel,LEFT_MARGIN-4,TOP_MARGIN,frame.height, fm);
			ip.setFont(font);
		} else 
			drawYLabel(yLabel,LEFT_MARGIN-4,TOP_MARGIN,frame.height, fm);
	}

	//draw ticks, grid and axis label for each tick/grid line
	void drawTicksEtc() {
		int fontAscent = ip.getFontMetrics().getAscent();
		int fontMaxAscent = ip.getFontMetrics().getMaxAscent();
		if ((flags&(X_NUMBERS + X_TICKS + X_GRID))!=0) {
			double step = Math.abs(Math.max(frame.width/MAX_INTERVALS, MIN_X_GRIDWIDTH)/xScale); //the smallest allowable increment
			step = niceNumber(step);
			int i1, i2;
			if ((flags&X_FORCE2GRID)!=0) {
				i1 = (int)Math.floor(Math.min(xMin,xMax)/step+1.e-10);	//this also allows for inverted xMin, xMax
				i2 = (int)Math.ceil (Math.max(xMin,xMax)/step-1.e-10);
				xMin = i1*step;
				xMax = i2*step;
				xScale = frame.width/(xMax-xMin);			//rescale to make it fit
			} else {
				i1 = (int)Math.ceil (Math.min(xMin,xMax)/step-1.e-10);
				i2 = (int)Math.floor(Math.max(xMin,xMax)/step+1.e-10);
			}
			int digits = getDigits(xMin, xMax, step, 7);
			int y1 = TOP_MARGIN;
			int y2 = TOP_MARGIN+frame.height;
			int yNumbers = y2 + fontAscent + 7;
			for (int i=0; i<=(i2-i1); i++) {
				double v = (i+i1)*step;
				int x = (int)Math.round((v - xMin)*xScale) + LEFT_MARGIN;
				if ((flags&X_GRID)!=0) {
					ip.setColor(gridColor);
					ip.drawLine(x, y1, x, y2);
					ip.setColor(Color.black);
				}
				if ((flags&X_TICKS)!=0) {
					ip.drawLine(x, y1, x, y1+TICK_LENGTH);
					ip.drawLine(x, y2, x, y2-TICK_LENGTH);
				}
				if ((flags&X_NUMBERS)!=0) {
					String s = IJ.d2s(v,digits);
					ip.drawString(s, x-ip.getStringWidth(s)/2, yNumbers);
				}
				if ((flags&X_LOG_NUMBERS)!=0) {
					ip.setFont(fontSmall);
					String s = IJ.d2s(v,digits);
					ip.drawString(s, x-ip.getStringWidth(s)/4+9, yNumbers-7);
					ip.setFont(font);
					String t = "10";
					ip.drawString(t, x-ip.getStringWidth(t)/2, yNumbers+2);
				}
			}
			if ((flags&X_MINOR_TICKS)!=0  && (flags&X_LOG_NUMBERS)==0) {
				step /= 10;
				i1 = (int)Math.ceil (Math.min(xMin,xMax)/step-1.e-10);
				i2 = (int)Math.floor(Math.max(xMin,xMax)/step+1.e-10);
				for (int i=0; i<=(i2-i1); i++) {
					double v = (i+i1)*step;
					int x = (int)Math.round((v - xMin)*xScale) + LEFT_MARGIN;
					ip.drawLine(x, y1, x, y1+MINOR_TICK_LENGTH);
					ip.drawLine(x, y2, x, y2-MINOR_TICK_LENGTH);
				}
			} 
			if ((flags&X_MINOR_TICKS)!=0  && (flags&X_LOG_NUMBERS)!=0) {
				int i10 = (int)Math.ceil(Math.min(xMin,xMax)/step-1.e-10);
				step /= 10;
				i1 = (int)Math.ceil (Math.min(xMin,xMax)/step-1.e-10);
				i2 = (int)Math.floor(Math.max(xMin,xMax)/step+1.e-10);
				for (int i=0; i<=(i2-i1); i++) {
					int diff = i1+(1-i10)*10;
					if ((i+diff)%10>1) {
						double v = (i-(i+diff)%10+10*Math.log10((i+diff)%10)+i1)*step;
						int x = (int)Math.round((v - xMin)*xScale) + LEFT_MARGIN;
						if (x < frame.width + LEFT_MARGIN) {
							ip.drawLine(x, y1, x, y1+MINOR_TICK_LENGTH);
							ip.drawLine(x, y2, x, y2-MINOR_TICK_LENGTH);
						}
					}
				}
			}
		}
		int maxNumWidth = 0;
		if ((flags&(Y_NUMBERS + Y_TICKS + Y_GRID))!=0) {
			double step = Math.abs(Math.max(frame.height/MAX_INTERVALS, MIN_Y_GRIDWIDTH)/yScale); //the smallest allowable increment
			step = niceNumber(step);
			int i1, i2;
			if ((flags&Y_FORCE2GRID)!=0) {
				i1 = (int)Math.floor(Math.min(yMin,yMax)/step+1.e-10);	//this also allows for inverted xMin, xMax
				i2 = (int)Math.ceil (Math.max(yMin,yMax)/step-1.e-10);
				yMin = i1*step;
				yMax = i2*step;
				yScale = frame.height/(yMax-yMin);			//rescale to make it fit
			} else {
				i1 = (int)Math.ceil (Math.min(yMin,yMax)/step-1.e-10);
				i2 = (int)Math.floor(Math.max(yMin,yMax)/step+1.e-10);
			}
			int digits = getDigits(yMin, yMax, step, 5);
			int x1 = LEFT_MARGIN;
			int x2 = LEFT_MARGIN+frame.width;
			if (yMin==yMax && (flags&Y_NUMBERS)!=0) {
				String s = IJ.d2s(yMin,getDigits(yMin, 0.001*yMin, 5));
				int w = ip.getStringWidth(s);
				if (w>maxNumWidth) maxNumWidth = w;
				int y = TOP_MARGIN + frame.height;
				ip.drawString(s, LEFT_MARGIN-w-4, y+fontMaxAscent/2+1);
				drawYLabel(yLabel,LEFT_MARGIN-maxNumWidth-4,TOP_MARGIN,frame.height, ip.getFontMetrics());
			}
			else {
				ip.setFont(font);
				int w1 = ip.getStringWidth(IJ.d2s(yMin,digits));
				int w2 = ip.getStringWidth(IJ.d2s(yMax,digits));
				int wMax = Math.max(w1,w2);
				//IJ.log(IJ.d2s(yMin,digits)+"w="+w1+","+IJ.d2s(yMax,digits)+"w="+w2+((wMax > LEFT_MARGIN-4)?"med":"norm"));
				if ((flags&Y_NUMBERS)!=0 && (flags&Y_LOG_NUMBERS)==0)
					if (wMax > LEFT_MARGIN-4)
						ip.setFont(fontMedium);	 //small font if there is not enough space for the numbers
				for (int i=0; i<=(i2-i1); i++) {
					double v = step==0 ? yMin : (i+i1)*step;
					int y = TOP_MARGIN + frame.height - (int)Math.round((v - yMin)*yScale);
					if ((flags&Y_GRID)!=0) {
						ip.setColor(gridColor);
						ip.drawLine(x1, y, x2, y);
						ip.setColor(Color.black);
					}
					if ((flags&Y_TICKS)!=0) {
						ip.drawLine(x1, y, x1+TICK_LENGTH, y);
						ip.drawLine(x2, y, x2-TICK_LENGTH, y);
					}
					if ((flags&Y_NUMBERS)!=0) {
						String s = IJ.d2s(v,digits);
						int w = ip.getStringWidth(s);
						if (w>maxNumWidth) maxNumWidth = w;
						ip.drawString(s, LEFT_MARGIN-w-4, y+fontMaxAscent/2+1);
					}
					if ((flags&Y_LOG_NUMBERS)!=0) {
						ip.setFont(fontSmall);
						String s = IJ.d2s(v,digits);
						int ws = ip.getStringWidth(s);
						if (ws>maxNumWidth) maxNumWidth = ws;
						ip.drawString(s, LEFT_MARGIN-ws-1, y+fontMaxAscent/2-8);
						ip.setFont(font);
						String t = "10";
						int w = ip.getStringWidth(t);
						if (w>maxNumWidth) maxNumWidth = w;
						ip.drawString(t, LEFT_MARGIN-w-11, y+fontMaxAscent/2+2);
					}				
				}
			}
			ip.setFont(font);
			if ((flags&Y_MINOR_TICKS)!=0  && (flags&Y_LOG_NUMBERS)==0) {
				step /= 10;
				i1 = (int)Math.ceil (Math.min(yMin,yMax)/step-1.e-10);
				i2 = (int)Math.floor(Math.max(yMin,yMax)/step+1.e-10);
				for (int i=0; i<=(i2-i1); i++) {
					double v = (i+i1)*step;
					int y = TOP_MARGIN + frame.height - (int)Math.round((v - yMin)*yScale);
					ip.drawLine(x1, y, x1+MINOR_TICK_LENGTH, y);
					ip.drawLine(x2, y, x2-MINOR_TICK_LENGTH, y);
				}
			}
			if ((flags&Y_MINOR_TICKS)!=0  && (flags&Y_LOG_NUMBERS)!=0) {
				int i10 = (int)Math.ceil(Math.min(yMin,yMax)/step-1.e-10);
				step /= 10;
				i1 = (int)Math.ceil (Math.min(yMin,yMax)/step-1.e-10);
				i2 = (int)Math.floor(Math.max(yMin,yMax)/step+1.e-10);
				for (int i=0; i<=(i2-i1); i++) {
					int diff = i1+(1-i10)*10;
					if(i%10>1) {
						double v = (i-(i+diff)%10+10*Math.log10((i+diff)%10)+i1)*step;
						int y = TOP_MARGIN + frame.height - (int)Math.round((v - yMin)*yScale);
						if(y > TOP_MARGIN) {
							ip.drawLine(x1, y, x1+MINOR_TICK_LENGTH, y);
							ip.drawLine(x2, y, x2-MINOR_TICK_LENGTH, y);
						}
					}
				}
			}
		}
		if ((flags&(Y_NUMBERS+Y_LOG_NUMBERS))==0) {					//simply note y-axis min&max
			int digits = getDigits(yMin, yMax, 0.001*(yMax-yMin), 6);
			String s = IJ.d2s(yMax, digits);
			int sw = ip.getStringWidth(s);
			if ((sw+4)>LEFT_MARGIN)
				ip.drawString(s, 4, TOP_MARGIN-4);
			else
				ip.drawString(s, LEFT_MARGIN-ip.getStringWidth(s)-4, TOP_MARGIN+10);
			s = IJ.d2s(yMin, digits);
			sw = ip.getStringWidth(s);
			if ((sw+4)>LEFT_MARGIN)
				ip.drawString(s, 4, TOP_MARGIN+frame.height);
			else
				ip.drawString(s, LEFT_MARGIN-ip.getStringWidth(s)-4, TOP_MARGIN+frame.height);
		}
		FontMetrics fm = ip.getFontMetrics();
		int x = LEFT_MARGIN;
		int y = TOP_MARGIN + frame.height + fm.getAscent() + 6;
		if ((flags&(X_NUMBERS+X_LOG_NUMBERS))==0) {				//simply note x-axis min&max
			int digits = getDigits(xMin, xMax, 0.001*(xMax-xMin), 7);
			ip.drawString(IJ.d2s(xMin,digits), x, y);
			String s = IJ.d2s(xMax,digits);
			ip.drawString(s, x + frame.width-ip.getStringWidth(s)+6, y);
		} else
			y += fm.getAscent();							//space needed for x numbers
		if (xLabelFont != font) {
			ip.setFont(xLabelFont);
			ip.drawString(xLabel, LEFT_MARGIN+(frame.width-ip.getStringWidth(xLabel))/2, y+6);
			ip.setFont(font);
		} else 
			ip.drawString(xLabel, LEFT_MARGIN+(frame.width-ip.getStringWidth(xLabel))/2, y+6);
		if (xLabelFont != font) {
			ip.setFont(xLabelFont);
			if ((flags&Y_LOG_NUMBERS)!=0)
				drawYLabel(yLabel,LEFT_MARGIN-maxNumWidth-20,TOP_MARGIN,frame.height, fm);
			else
				drawYLabel(yLabel,LEFT_MARGIN-maxNumWidth-4,TOP_MARGIN,frame.height, fm);
			ip.setFont(font);
		} else {
			if ((flags&Y_LOG_NUMBERS)!=0)
				drawYLabel(yLabel,LEFT_MARGIN-maxNumWidth-20,TOP_MARGIN,frame.height, fm);
			else
				drawYLabel(yLabel,LEFT_MARGIN-maxNumWidth-4,TOP_MARGIN,frame.height, fm);
		}
	}
	
	double niceNumber(double v) {	//the smallest "nice" number >= v. "Nice" numbers are .. 0.5, 1, 2, 5, 10, 20 ...
		double base = Math.pow(10,Math.floor(Math.log10(v)-1.e-6));
		if (v > 5.0000001*base) return 10*base;
		else if (v > 2.0000001*base) return 5*base;
		else return 2*base;
	}

	void createImage() {
		if (ip!=null) return;
	   int width = plotWidth+LEFT_MARGIN+RIGHT_MARGIN;
		int height = plotHeight+TOP_MARGIN+BOTTOM_MARGIN;
		byte[] pixels = new byte[width*height];
		for (int i=0; i<width*height; i++)
			pixels[i] = (byte)255;
		ip = new ByteProcessor(width, height, pixels, null);
	}

	// Number of digits to display the number n with resolution 'resolution';
	// (if n is integer and small enough to display without scientific notation,
	// no decimals are needed, irrespective of 'resolution')
	// Scientific notation is used for more than 'maxDigits' (must be >=3), and indicated
	// by a negative return value
	int getDigits(double n, double resolution, int maxDigits) {
		if (isInteger(n) && Math.abs(n) < Math.pow(10,maxDigits-1)-1)
			return 0;
		else
			return getDigits2(n, resolution, maxDigits);
	}

	// Number of digits to display the range between n1 and n2 with resolution 'resolution';
	// Scientific notation is used for more than 'maxDigits' (must be >=3), and indicated
	// by a negative return value
	int getDigits(double n1, double n2, double resolution, int maxDigits) {
		if (n1==0 && n2==0) return 0;
		return getDigits2(Math.max(Math.abs(n1),Math.abs(n2)), resolution, maxDigits);
	}

	int getDigits2(double n, double resolution, int maxDigits) {
		int log10ofN = (int)Math.floor(Math.log10(Math.abs(n))+1e-7);
		int digits = resolution != 0 ?
				-(int)Math.floor(Math.log10(Math.abs(resolution))+1e-7) : 
				Math.max(0, -log10ofN+maxDigits-2);
		int sciDigits = -Math.max((log10ofN+digits),1);
		//IJ.log("n="+(float)n+"digitsRaw="+digits+" log10ofN="+log10ofN+" sciDigits="+sciDigits);
		if (digits < -2 && log10ofN >= maxDigits)
			digits = sciDigits; //scientific notation for large numbers
		else if (digits < 0)
			digits = 0;
		else if (digits > maxDigits-1 && log10ofN < -2)
			digits = sciDigits; // scientific notation for small numbers
		return digits;
	}

	boolean isInteger(double n) {
		return n==Math.round(n);
	}

	/** Draws the plot specified in the constructor. */
	public void draw() {
		int x, y;
		double v;
		
		if (plotDrawn)
			return;
		plotDrawn = true;
		createImage();
		setup();
		
		if (drawPending) {
			drawFloatPolyline(ip, ((flags&X_LOG_NUMBERS)!=0) ? arrayToLog(xValues) : xValues, ((flags&Y_LOG_NUMBERS)!=0) ? arrayToLog(yValues) : yValues, nPoints);
			if (yMin==yMax) {
				int yy = frame.y + frame.height-1;
				ip.drawLine(frame.x, yy, frame.x+frame.width, yy);
			}
			if (this.errorBars != null)
				drawErrorBars(xValues, yValues, errorBars);
		}
		
		if (ip instanceof ColorProcessor)
			ip.setColor(Color.black);
		if (lineWidth>5)
			ip.setLineWidth(5);
		ip.drawRect(frame.x, frame.y, frame.width+1, frame.height+1);
		ip.setLineWidth(lineWidth);
	}

	private void drawErrorBars(float[] x, float[] y, float[] e) {
		int nPoints2 = nPoints;
		if (e.length<nPoints)
			nPoints2 = e.length;
		int[] xpoints = new int[2];
		int[] ypoints = new int[2];
		for (int i=0; i<nPoints2; i++) {
			xpoints[0] = xpoints[1] = LEFT_MARGIN  + (int)(((((flags&X_LOG_NUMBERS)!=0) ? Math.log10(x[i]) : x[i])-xMin)*xScale);
			ypoints[0] = TOP_MARGIN + frame.height - (int)(((((flags&Y_LOG_NUMBERS)!=0) ? Math.log10(y[i]) : y[i])-yMin-(((flags&Y_LOG_NUMBERS)!=0) ? Math.log10(e[i]) : e[i]))*yScale);
			ypoints[1] = TOP_MARGIN + frame.height - (int)(((((flags&Y_LOG_NUMBERS)!=0) ? Math.log10(y[i]) : y[i])-yMin+(((flags&Y_LOG_NUMBERS)!=0) ? Math.log10(e[i]) : e[i]))*yScale);
			ypoints[0] = (ypoints[0]>TOP_MARGIN + frame.height) ? TOP_MARGIN + frame.height : ypoints[0];
			ypoints[1] = (ypoints[1]<TOP_MARGIN) ? TOP_MARGIN	 : ypoints[1];
			drawPolyline(ip, xpoints,ypoints, 2, false);
		}
	}

	void drawPolyline(ImageProcessor ip, int[] x, int[] y, int n, boolean clip) {
		if (clip) ip.setClipRect(frame);
		ip.moveTo(x[0], y[0]);
		for (int i=0; i<n; i++)
			ip.lineTo(x[i], y[i]);
		if (clip) ip.setClipRect(null);
	}
	
	void drawFloatPolyline(ImageProcessor ip, float[] x, float[] y, int n) {
		if (x==null || x.length==0) return;
		ip.setClipRect(frame);
		int x1, y1, x2, y2;
		boolean y1IsNaN, y2IsNaN;
		x2 = LEFT_MARGIN + (int)((x[0]-xMin)*xScale);
		y2 = TOP_MARGIN + frame.height - (int)((y[0]-yMin)*yScale);
		y2IsNaN = Float.isNaN(y[0]);
		for (int i=1; i<n; i++) {
			x1 = x2;
			y1 = y2;
			y1IsNaN = y2IsNaN;
			x2 = LEFT_MARGIN + (int)((x[i]-xMin)*xScale);
			y2 = TOP_MARGIN + frame.height - (int)((y[i]-yMin)*yScale);
			y2IsNaN = Float.isNaN(y[i]);
			if (!y1IsNaN && !y2IsNaN) {
				ip.drawLine(x1, y1, x2, y2);
			}
		}
		ip.setClipRect(null);
	}

	void drawYLabel(String yLabel, int x, int y, int height, FontMetrics fm) {
		if (yLabel.equals(""))
			return;
		int w =	 fm.stringWidth(yLabel) + 5;
		int h =	 fm.getHeight() + 5;
		ImageProcessor label = new ByteProcessor(w, h);
		label.setColor(Color.white);
		label.fill();
		label.setColor(Color.black);
		label.setFont(font);
		label.setAntialiasedText(true);
		int descent = fm.getDescent();
		label.drawString(yLabel, 0, h-descent);
		label = label.rotateLeft();
		int y2 = y+(height-ip.getStringWidth(yLabel))/2;
		if (y2<y) y2 = y;
		int x2 = Math.max(x-h, 0);
		ip.insert(label, x2, y2);
	}
	
	ImageProcessor getBlankProcessor() {
		createImage();
		return ip;
	}
	
	String getCoordinates(int x, int y) {
		String text = "";
		if (!frame.contains(x, y))
			return text;
		double xv = Double.NaN, yv = Double.NaN;
		if (multiplePlots) { // display cursor location
			xv = (x-LEFT_MARGIN)/xScale + xMin;
			yv = (TOP_MARGIN+frameHeight-y)/yScale +yMin;
		} else { // display x and f(x)
			int index = (int)((x-frame.x)/((double)frame.width/nPoints));
			if (index>=0 && index<nPoints) {
				xv = xValues[index];
				yv = yValues[index];
			}
		}
		if (!Double.isNaN(xv)) {
			xv = ((flags&X_LOG_NUMBERS)!=0) ? Math.pow(10.0,xv) : xv;
			yv = ((flags&Y_LOG_NUMBERS)!=0) ? Math.pow(10.0,yv) : yv;
			text =	"X=" + IJ.d2s(xv, getDigits(xv, 0.001*(xMax-xMin), 6))
					+", Y=" + IJ.d2s(yv, getDigits(yv, 0.001*(yMax-yMin), 6));
		}
		return text;
	}
	
	/** Returns the plot as an ImageProcessor. */
	public ImageProcessor getProcessor() {
		draw();
		return ip;
	}
	
	/** Returns the plot as an ImagePlus. */
	public ImagePlus getImagePlus() {
		draw();
		ImagePlus img = new ImagePlus(title, ip);
		Calibration cal = img.getCalibration();
		cal.xOrigin = LEFT_MARGIN-xMin*xScale;
		cal.yOrigin = TOP_MARGIN+frameHeight+yMin*yScale;
		cal.pixelWidth = 1.0/xScale;
		cal.pixelHeight = 1.0/yScale;
		cal.setInvertY(true);
		return img;
	}

	/** Displays the plot in a PlotWindow and returns a reference to the PlotWindow. */
	public PlotWindow show() {
		 draw();
		 if (Prefs.useInvertingLut && (ip instanceof ByteProcessor) && !Interpreter.isBatchMode() && IJ.getInstance()!=null) {
			ip.invertLut();
			ip.invert();
		}
		if ((IJ.macroRunning() && IJ.getInstance()==null) || Interpreter.isBatchMode()) {
			ImagePlus imp = getImagePlus();
			WindowManager.setTempCurrentImage(imp);
			imp.setProperty("XValues", xValues); //Allows values to be retrieved by 
			imp.setProperty("YValues", yValues); // by Plot.getValues() macro function
			Interpreter.addBatchModeImage(imp);
			return null;
		}
		PlotWindow pw = new PlotWindow(this);
		ImagePlus imp = pw.getImagePlus();
		if (IJ.isMacro() && imp!=null) // wait for plot to be displayed
			IJ.selectWindow(imp.getID());
		return pw;
	}
		
	/** Stores plot	 into an ArrayList	to be used 
		 when a plot window	 wants to 'createlist'. */
	private void store(float[] xvalues, float[] yvalues) {
		storedData.add(xvalues);
		storedData.add(yvalues);
	}
	
	public void setPlotMaker(PlotMaker plotMaker) {
		this.plotMaker = plotMaker;
	}
	
	PlotMaker getPlotMaker() {
		return plotMaker;
	}

}