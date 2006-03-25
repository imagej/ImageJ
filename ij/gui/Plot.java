package ij.gui;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import ij.*;
import ij.process.*;
import ij.util.*;
import ij.plugin.filter.Analyzer;
import ij.macro.Interpreter;


/** This class is an image that line graphs can be drawn on. */
public class Plot {

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
	
	private static final int LEFT_MARGIN = 50;
	private static final int RIGHT_MARGIN = 20;
	private static final int TOP_MARGIN = 20;
	private static final int BOTTOM_MARGIN = 30;
	private static final int WIDTH = 450;
	private static final int HEIGHT = 200;
	
	private int frameWidth;
	private int frameHeight;
	private int xloc;
	private int yloc;
	
	Rectangle frame = null;
	float[] xValues, yValues;
	float[] errorBars;
	int nPoints;
	double xMin, xMax, yMin, yMax;
		
	private double xScale, yScale;
	private Button list, save, copy;
	private Label coordinates;
	private static String defaultDirectory = null;
	private String xLabel;
	private String yLabel;
	private Font font = new Font("Helvetica", Font.PLAIN, 12);
	private boolean fixedYScale;
	private static int options;
	private int lineWidth = 1; // Line.getWidth();
	private boolean realNumbers;
	private int markSize = 5;
	private ImageProcessor ip;
	private String title;
	private boolean initialized;
	private boolean plotDrawn;
	private int plotWidth = PlotWindow.plotWidth;
	private int plotHeight = PlotWindow.plotHeight;
	private boolean multiplePlots;
	private boolean drawPending;
	 	
	/** Construct a new PlotWindow.
	* @param title			the window title
	* @param xLabel			the x-axis label
	* @param yLabel			the y-axis label
	* @param xValues		the x-coodinates, or null
	* @param yValues		the y-coodinates, or null
	*/
	public Plot(String title, String xLabel, String yLabel, float[] xValues, float[] yValues) {
		this.title = title;
		this.xLabel = xLabel;
		this.yLabel = yLabel;
		if (xValues==null || yValues==null) {
			xValues = new float[1];
			yValues = new float[1];
			xValues[0] = -1f;
			yValues[0] = -1f;
		}
		this.xValues = xValues;
		this.yValues = yValues;
		double[] a = Tools.getMinMax(xValues);
		xMin=a[0]; xMax=a[1];
		a = Tools.getMinMax(yValues);
		yMin=a[0]; yMax=a[1];
		fixedYScale = false;
		nPoints = xValues.length;
		drawPending = true;
	}

	/** This version of the constructor excepts double arrays. */
	public Plot(String title, String xLabel, String yLabel, double[] xValues, double[] yValues) {
		this(title, xLabel, yLabel, xValues!=null?Tools.toFloat(xValues):null, yValues!=null?Tools.toFloat(yValues):null);
	}
	
	/** Sets the x-axis and y-axis range. */
	public void setLimits(double xMin, double xMax, double yMin, double yMax) {
		this.xMin = xMin;
		this.xMax = xMax;
		this.yMin = yMin;
		this.yMax = yMax;
		fixedYScale = true;
		if (initialized) setScale();
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
				for (int i=0; i<x.length; i++) {
					int xt = LEFT_MARGIN + (int)((x[i]-xMin)*xScale);
					int yt = TOP_MARGIN + frameHeight - (int)((y[i]-yMin)*yScale);
					if (xt>=frame.x && yt>=frame.y && xt<=frame.x+frame.width && yt<=frame.y+frame.height)
						drawShape(shape, xt, yt, markSize);
				}	
				break;
			case LINE:
				ip.setClipRect(frame);					
				int xts[] = new int[x.length];
				int yts[] = new int[y.length];
				for (int i=0; i<x.length; i++) {
					xts[i] = LEFT_MARGIN + (int)((x[i]-xMin)*xScale);
					yts[i] = TOP_MARGIN + frameHeight - (int)((y[i]-yMin)*yScale);
				}
				drawPolyline(ip, xts, yts, x.length);
				ip.setClipRect(null);					
				break;
		}
		multiplePlots = true;
		if (xValues.length==1) {
			xValues = x;
			yValues = y;
			nPoints = x.length;
			drawPending = false;
		}
	}

	/** Adds a set of points to the plot using double arrays.
		Must be called before the plot is displayed. */
	public void addPoints(double[] x, double[] y, int shape) {
		addPoints(Tools.toFloat(x), Tools.toFloat(y), shape);
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
	
	/** Adds error bars to the plot. */
	public void addErrorBars(float[] errorBars) {
		if (errorBars.length!=nPoints)
			throw new IllegalArgumentException("errorBars.length != npoints");
		this.errorBars = errorBars  ;
	}

	/** Adds error bars to the plot. */
	public void addErrorBars(double[] errorBars) {
		addErrorBars(Tools.toFloat(errorBars));
	}

	/** Draws text at the specified location, where (0,0)
		is the upper left corner of the the plot frame and (1,1) is
		the lower right corner. */
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
	
	/** Sets the justification used  by addLabel(), where <code>justification</code>
		is ImageProcessor.LEFT, ImageProcessor.CENTER or ImageProcessor.RIGHT. */
	public void setJustification(int justification) {
		setup();
		ip.setJustification(justification);
	}

	/** Changes the drawing color. The frame and labels are
		always drawn in black. */
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

	/** Changes the font. */
    public void changeFont(Font font) {
    	setup();
		ip.setFont(font);
		this.font = font;
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
		setScale();
	}

	void setScale() {
		if ((xMax-xMin)==0.0)
			xScale = 1.0;
		else
			xScale = frame.width/(xMax-xMin);
		if ((yMax-yMin)==0.0)
			yScale = 1.0;
		else
			yScale = frame.height/(yMax-yMin);
	}

	void createImage() {
		if (ip!=null)
			return;
		int width = plotWidth+LEFT_MARGIN+RIGHT_MARGIN;
		int height = plotHeight+TOP_MARGIN+BOTTOM_MARGIN;
		byte[] pixels = new byte[width*height];
		for (int i=0; i<width*height; i++)
			pixels[i] = (byte)255;
		ip = new ByteProcessor(width, height, pixels, null);
	}

	int getDigits(double n1, double n2) {
		if (Math.round(n1)==n1 && Math.round(n2)==n2)
			return 0;
		else {
			n1 = Math.abs(n1);
			n2 = Math.abs(n2);
		    double n = n1<n2&&n1>0.0?n1:n2;
		    double diff = Math.abs(n2-n1);
		    if (diff>0.0 && diff<n) n = diff;
			int digits = 1;
			if (n<10.0) digits = 2;
			if (n<0.01) digits = 3;
			if (n<0.001) digits = 4;
			if (n<0.0001) digits = 5;	    
			return digits;
		}
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
			ip.setClipRect(frame);					
			int xpoints[] = new int[nPoints];
			int ypoints[] = new int[nPoints];
			for (int i=0; i<nPoints; i++) {
				xpoints[i] = LEFT_MARGIN + (int)((xValues[i]-xMin)*xScale);
				ypoints[i] = TOP_MARGIN + frame.height - (int)((yValues[i]-yMin)*yScale);
			}
			drawPolyline(ip, xpoints, ypoints, nPoints); 
			ip.setClipRect(null);	
			if (this.errorBars != null) {
				xpoints = new int[2];
				ypoints = new int[2];
				for (int i=0; i<nPoints; i++) {
					xpoints[0] = xpoints[1] = LEFT_MARGIN + (int)((xValues[i]-xMin)*xScale);
					ypoints[0] = TOP_MARGIN + frame.height - (int)((yValues[i]-yMin-errorBars[i])*yScale);
					ypoints[1] = TOP_MARGIN + frame.height - (int)((yValues[i]-yMin+errorBars[i])*yScale);
					drawPolyline(ip, xpoints,ypoints, 2);
				}	    
			}
		}				

		if (ip instanceof ColorProcessor)
			ip.setColor(Color.black);
		ip.drawRect(frame.x, frame.y, frame.width+1, frame.height+1);
		int digits = getDigits(yMin, yMax);
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
		FontMetrics fm = ip.getFontMetrics();
		x = LEFT_MARGIN;
		y = TOP_MARGIN + frame.height + fm.getAscent() + 6;
		digits = getDigits(xMin, xMax);
		ip.drawString(IJ.d2s(xMin,digits), x, y);
		s = IJ.d2s(xMax,digits);
		ip.drawString(s, x + frame.width-ip.getStringWidth(s)+6, y);
		ip.drawString(xLabel, LEFT_MARGIN+(frame.width-ip.getStringWidth(xLabel))/2, y+3);
		drawYLabel(yLabel,LEFT_MARGIN,TOP_MARGIN,frame.height, fm);
	}
	
	void drawPolyline(ImageProcessor ip, int[] x, int[] y, int n) {
		ip.moveTo(x[0], y[0]);
		for (int i=0; i<n; i++)
			ip.lineTo(x[i], y[i]);
	}
		
	void drawYLabel(String yLabel, int x, int y, int height, FontMetrics fm) {
		if (yLabel.equals(""))
			return;
		int w =  fm.stringWidth(yLabel) + 5;
		int h =  fm.getHeight() + 5;
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
		int x2 = x-h-2;
		//new ImagePlus("after", label).show();
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
		if (fixedYScale || multiplePlots) { // display cursor location
			double xv = (x-LEFT_MARGIN)/xScale + xMin;
			double yv = (TOP_MARGIN+frameHeight-y)/yScale +yMin;
			text =  "X=" + IJ.d2s(xv,getDigits(xv,xv))+", Y=" + IJ.d2s(yv,getDigits(yv,yv));
		} else { // display x and f(x)
			int index = (int)((x-frame.x)/((double)frame.width/nPoints));
			if (index>0 && index<nPoints) {
				double xv = xValues[index];
				double yv = yValues[index];
				text = "X=" + IJ.d2s(xv,getDigits(xv,xv))+", Y=" + IJ.d2s(yv,getDigits(yv,yv));
			}
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
		return new ImagePlus(title, ip);
	}

	/** Displays the plot in a PlotWindow and returns a reference to the PlotWindow. */
	public PlotWindow show() {
		draw();
		if (Prefs.useInvertingLut && (ip instanceof ByteProcessor) && !Interpreter.isBatchMode() && IJ.getInstance()!=null) {
			ip.invertLut();
			ip.invert();
		}
		if ((IJ.macroRunning() && IJ.getInstance()==null) || Interpreter.isBatchMode()) {
			ImagePlus imp = new ImagePlus(title, ip);
			WindowManager.setTempCurrentImage(imp);
			Interpreter.addBatchModeImage(imp);
			return null;
		}
		return new PlotWindow(this);
	}
	
}


