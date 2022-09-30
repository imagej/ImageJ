package ij.gui;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;
import java.awt.datatransfer.*;
import java.util.ArrayList;
import ij.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.filter.Analyzer;
import ij.text.TextWindow;

/** This class is an extended ImageWindow that displays histograms. */
public class HistogramWindow extends ImageWindow implements Measurements, ActionListener, 
	ClipboardOwner, ImageListener, RoiListener, Runnable {
	private static final double SCALE = HistogramPlot.SCALE;
	static final int HIST_WIDTH = HistogramPlot.HIST_WIDTH;
	static final int HIST_HEIGHT = HistogramPlot.HIST_HEIGHT;
	static final int XMARGIN = HistogramPlot.XMARGIN;
	static final int YMARGIN = HistogramPlot.YMARGIN;
	static final int WIN_WIDTH = HistogramPlot.WIN_WIDTH;
	static final int WIN_HEIGHT = HistogramPlot.WIN_HEIGHT;
	static final int BAR_HEIGHT = HistogramPlot.BAR_HEIGHT;

	static final int INTENSITY1=0, INTENSITY2=1, RGB=2, RED=3, GREEN=4, BLUE=5;
	
	protected ImageStatistics stats;
	protected long[] histogram;
	protected LookUpTable lut;
	protected Rectangle frame = null;
	protected Button list, save, copy, log, live, rgb;
	protected Label value, count;
	protected static String defaultDirectory = null;
	protected int decimalPlaces;
	protected int digits;
	protected long newMaxCount;
	protected int plotScale = 1;
	protected boolean logScale;
	protected Calibration cal;
	protected int yMax;
	public static int nBins = 256;
		
	private int srcImageID;			// ID of source image
	private ImagePlus srcImp;		// source image for live histograms
	private Thread bgThread;		// thread background drawing
	private boolean doUpdate;	// tells background thread to update
	private int rgbMode = -1;
	private String blankLabel;
	private boolean stackHistogram;
	private Font font = new Font("SansSerif",Font.PLAIN,(int)(12*SCALE));
	private boolean showBins;
	private int col1, col2, row1, row2, row3, row4, row5;
	    
	public HistogramWindow(HistogramPlot plot, ImagePlus srcImp) {
		super(plot);
		srcImageID = srcImp.getID();
		frame = plot.frame;
		histogram = plot.histogram;
		cal = plot.cal;
		stats = plot.stats;
		yMax = plot.yMax;
		newMaxCount = plot.newMaxCount;
		showBins = plot.showBins;
		rgbMode = plot.rgbMode;
		col1=plot.col1; col2=plot.col2; row1=plot.row1; row2=plot.row2;
		row3=plot.row3; row4=plot.row4; row5=plot.row5;
		if (list==null)
			setup(srcImp);
	}

	/** Displays a histogram using the title "Histogram of ImageName". */
	public HistogramWindow(ImagePlus imp) {
		super(NewImage.createRGBImage("Histogram of "+imp.getShortTitle(), WIN_WIDTH, WIN_HEIGHT, 1, NewImage.FILL_WHITE));
		showHistogram(imp, 256, 0.0, 0.0);
	}

	/** Displays a histogram using the specified title and number of bins. 
		Currently, the number of bins must be 256 expect for 32 bit images. */
	public HistogramWindow(String title, ImagePlus imp, int bins) {
		super(NewImage.createRGBImage(title, WIN_WIDTH, WIN_HEIGHT, 1, NewImage.FILL_WHITE));
		showHistogram(imp, bins, 0.0, 0.0);
	}

	/** Displays a histogram using the specified title, number of bins and histogram range.
		Currently, the number of bins must be 256 and the histogram range range must be the 
		same as the image range expect for 32 bit images. */
	public HistogramWindow(String title, ImagePlus imp, int bins, double histMin, double histMax) {
		super(NewImage.createRGBImage(title, WIN_WIDTH, WIN_HEIGHT, 1, NewImage.FILL_WHITE));
		showHistogram(imp, bins, histMin, histMax);
	}

	/** Displays a histogram using the specified title, number of bins, histogram range and yMax. */
	public HistogramWindow(String title, ImagePlus imp, int bins, double histMin, double histMax, int yMax) {
		super(NewImage.createRGBImage(title, WIN_WIDTH, WIN_HEIGHT, 1, NewImage.FILL_WHITE));
		this.yMax = yMax;
		showHistogram(imp, bins, histMin, histMax);
	}

	/** Displays a histogram using the specified title and ImageStatistics. */
	public HistogramWindow(String title, ImagePlus imp, ImageStatistics stats) {
		super(NewImage.createRGBImage(title, WIN_WIDTH, WIN_HEIGHT, 1, NewImage.FILL_WHITE));
		this.yMax = stats.histYMax;
		showHistogram(imp, stats);
	}

	/** Draws the histogram using the specified title and number of bins.
		Currently, the number of bins must be 256 expect for 32 bit images. */
	public void showHistogram(ImagePlus imp, int bins) {
		showHistogram(imp, bins, 0.0, 0.0);
	}

	/** Draws the histogram using the specified title, number of bins and histogram range.
		Currently, the number of bins must be 256 and the histogram range range must be 
		the same as the image range expect for 32 bit images. */
	public void showHistogram(ImagePlus imp, int bins, double histMin, double histMax) {
		boolean limitToThreshold = (Analyzer.getMeasurements()&LIMIT)!=0;
		ImageProcessor ip = imp.getProcessor();
		if (ip.getMinThreshold()!=ImageProcessor.NO_THRESHOLD
		&& ip.getLutUpdateMode()==ImageProcessor.NO_LUT_UPDATE)
			limitToThreshold = false;  // ignore invisible thresholds
		if (imp.isRGB() && rgbMode<INTENSITY1)
			rgbMode=INTENSITY1;
		if (rgbMode==RED||rgbMode==GREEN||rgbMode==BLUE) {
			int channel = rgbMode - 2;
			ColorProcessor cp = (ColorProcessor)imp.getProcessor();
			ip = cp.getChannel(channel, null);
			ImagePlus imp2 = new ImagePlus("", ip);
			imp2.setRoi(imp.getRoi());
			stats = imp2.getStatistics(AREA+MEAN+MODE+MIN_MAX, bins, histMin, histMax);
		} else if (rgbMode==RGB)
			stats = RGBHistogram(imp, bins, histMin, histMax);
		else
			stats = imp.getStatistics(AREA+MEAN+MODE+MIN_MAX+(limitToThreshold?LIMIT:0), bins, histMin, histMax);
		showHistogram(imp, stats);
	}
	
	private ImageStatistics RGBHistogram(ImagePlus imp, int bins, double histMin, double histMax) {
		ImageProcessor ip = (ColorProcessor)imp.getProcessor();
		ip = ip.crop();
		int w = ip.getWidth();
		int h = ip.getHeight();
		ImageProcessor ip2 = new ByteProcessor(w*3, h);
		ByteProcessor temp = null;
		for (int i=0; i<3; i++) {
			temp = ((ColorProcessor)ip).getChannel(i+1,temp);
			ip2.insert(temp, i*w, 0);
		}
		ImagePlus imp2 = new ImagePlus("imp2", ip2);
		return imp2.getStatistics(AREA+MEAN+MODE+MIN_MAX, bins, histMin, histMax);
	}

	/** Draws the histogram using the specified title and ImageStatistics. */
	public void showHistogram(ImagePlus srcImp, ImageStatistics stats) {
		if (srcImp.isRGB() && rgbMode<INTENSITY1)
			rgbMode=INTENSITY1;
		stackHistogram = stats.stackStatistics;
		if (list==null)
			setup(srcImp);
		this.stats = stats;
		cal = srcImp.getCalibration();
		boolean limitToThreshold = (Analyzer.getMeasurements()&LIMIT)!=0;
		srcImp.getMask();
		histogram = stats.getHistogram();
		if (limitToThreshold && histogram.length==256) {
			ImageProcessor ip = srcImp.getProcessor();
			if (ip.getMinThreshold()!=ImageProcessor.NO_THRESHOLD) {
				int lower = scaleDown(ip, ip.getMinThreshold());
				int upper = scaleDown(ip, ip.getMaxThreshold());
				for (int i=0; i<lower; i++)
					histogram[i] = 0L;
				for (int i=upper+1; i<256; i++)
					histogram[i] = 0L;
			}
		}
		lut = srcImp.createLut();
		int type = srcImp.getType();
		boolean fixedRange = type==ImagePlus.GRAY8 || type==ImagePlus.COLOR_256 || srcImp.isRGB();
		if (imp==null) {
			IJ.showStatus("imp==null");
			return;
		}
		ImageProcessor ip = imp.getProcessor();
		ip.setColor(Color.white);
		ip.resetRoi();
		ip.fill();
		ImageProcessor srcIP = srcImp.getProcessor();
		drawHistogram(srcImp, ip, fixedRange, stats.histMin, stats.histMax);
		imp.updateAndDraw();
	}

	private void setup(ImagePlus imp) {
		boolean isRGB = imp.isRGB();
 		Panel buttons = new Panel();
 		int hgap = IJ.isMacOSX()||isRGB?1:5;
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT,hgap,0));
		int trim = IJ.isMacOSX()?6:0;
		list = new TrimmedButton("List", trim);
		list.addActionListener(this);
		buttons.add(list);
		copy = new TrimmedButton("Copy", trim);
		copy.addActionListener(this);
		buttons.add(copy);
		log = new TrimmedButton("Log", trim);
		log.addActionListener(this);
		buttons.add(log);
		if (!stackHistogram) {
			live = new TrimmedButton("Live", trim);
			live.addActionListener(this);
			buttons.add(live);
		}
		if (imp!=null && isRGB && !stackHistogram) {
			rgb = new TrimmedButton("RGB", trim);
			rgb.addActionListener(this);
			buttons.add(rgb);
		}
		value = new Label(" ");
		count = new Label(" ");
		add(buttons);
		GUI.scale(buttons);
		pack();
		if (IJ.isMacOSX() && IJ.isJava18()) {
			IJ.wait(50);
			pack();
		}
    }
    
	public void setup() {
		setup(null);
	}
	
	public void mouseMoved(int x, int y) {
		ImageProcessor ip = this.imp!=null?this.imp.getProcessor():null;
		if (ip==null)
			return;
		if ((frame!=null)  && x>=frame.x && x<=(frame.x+frame.width)) {
			x = (x - frame.x);
			int index = (int)(x*(SCALE*histogram.length)/HIST_WIDTH/SCALE);
			if (index>=histogram.length) index = histogram.length-1;
			double value = cal.getCValue(stats.histMin+index*stats.binSize);
			drawValueAndCount(ip, value, histogram[index]);
		} else
			drawValueAndCount(ip, Double.NaN, -1);
		this.imp.updateAndDraw();
	}
	
	protected void drawHistogram(ImageProcessor ip, boolean fixedRange) {
		drawHistogram(null, ip, fixedRange, 0.0, 0.0);
	}

	void drawHistogram(ImagePlus imp, ImageProcessor ip, boolean fixedRange, double xMin, double xMax) {
		int x, y;
		long maxCount2 = 0;
		int mode2 = 0;
		long saveModalCount;		    	
		ip.setColor(Color.black);
		ip.setLineWidth(1);
		decimalPlaces = Analyzer.getPrecision();
		digits = cal.calibrated()||stats.binSize!=1.0?decimalPlaces:0;
		saveModalCount = histogram[stats.mode];
		for (int i = 0; i<histogram.length; i++) {
 			if ((histogram[i] > maxCount2) && (i != stats.mode)) {
				maxCount2 = histogram[i];
				mode2 = i;
  			}
  		}
		newMaxCount = histogram[stats.mode];
		if ((newMaxCount>(maxCount2 * 2)) && (maxCount2 != 0))
			newMaxCount = (int)(maxCount2 * 1.5);
		if (logScale || IJ.shiftKeyDown() && !liveMode())
			drawLogPlot(yMax>0?yMax:newMaxCount, ip);
		drawPlot(yMax>0?yMax:newMaxCount, ip);
		histogram[stats.mode] = saveModalCount;
 		x = XMARGIN + 1;
		y = YMARGIN + HIST_HEIGHT + 2;
		if (imp==null)
			lut.drawUnscaledColorBar(ip, x-1, y, HIST_WIDTH, BAR_HEIGHT);
		else
			drawAlignedColorBar(imp, xMin, xMax, ip, x-1, y, HIST_WIDTH, BAR_HEIGHT);
		y += BAR_HEIGHT+(int)(15*SCALE);
  		drawText(ip, x, y, fixedRange);
  		srcImageID = imp.getID();
	}
       
	void drawAlignedColorBar(ImagePlus imp, double xMin, double xMax, ImageProcessor ip, int x, int y, int width, int height) {
		ImageProcessor ipSource = imp.getProcessor();
		float[] pixels = null;
		ImageProcessor ipRamp = null;
		if (rgbMode>=INTENSITY1) {
			ipRamp = new FloatProcessor(width, height);
			if (rgbMode==RED)
				ipRamp.setColorModel(LUT.createLutFromColor(Color.red));
			else if (rgbMode==GREEN)
				ipRamp.setColorModel(LUT.createLutFromColor(Color.green));
			else if (rgbMode==BLUE)
				ipRamp.setColorModel(LUT.createLutFromColor(Color.blue));
			pixels = (float[])ipRamp.getPixels();
		} else
			pixels = new float[width*height];
		for (int j=0; j<height; j++) {
			for(int i=0; i<width; i++)
				pixels[i+width*j] = (float)(xMin+i*(xMax-xMin)/(width - 1));
		}
		double min = ipSource.getMin();
		double max = ipSource.getMax();
		if (ipSource.getNChannels()==1) {
			ColorModel cm = null;
			if (imp.isComposite()) {
				if (stats!=null && stats.pixelCount>ipSource.getPixelCount()) { // stack histogram
					cm = LUT.createLutFromColor(Color.white);
					min = stats.min;
					max = stats.max;
				} else
					cm = ((CompositeImage)imp).getChannelLut();
			} else if (ipSource.getMinThreshold()==ImageProcessor.NO_THRESHOLD)
				cm = ipSource.getColorModel();
			else
				cm = ipSource.getCurrentColorModel();
			ipRamp = new FloatProcessor(width, height, pixels, cm);
		}
		ipRamp.setMinAndMax(min,max);
		ImageProcessor bar = null;
		if (ip instanceof ColorProcessor)
			bar = ipRamp.convertToRGB();
		else
			bar = ipRamp.convertToByte(true);
		ip.insert(bar, x,y);
		ip.setColor(Color.black);
		ip.drawRect(x-1, y, width+2, height);
	}

	/** Scales a threshold level to the range 0-255. */
	int scaleDown(ImageProcessor ip, double threshold) {
		double min = ip.getMin();
		double max = ip.getMax();
		if (max>min)
			return (int)(((threshold-min)/(max-min))*255.0);
		else
			return 0;
	}

	void drawPlot(long maxCount, ImageProcessor ip) {
		if (maxCount==0) maxCount = 1;
		frame = new Rectangle(XMARGIN, YMARGIN, HIST_WIDTH, HIST_HEIGHT);
		ip.drawRect(frame.x-1, frame.y, frame.width+2, frame.height+1);
		if (histogram.length==256) {
			double scale2 = HIST_WIDTH/256.0;
			int barWidth = 1;
			if (SCALE>1) barWidth=2;
			if (SCALE>2) barWidth=3;
			for (int i = 0; i < 256; i++) {
				int x =(int)(i*scale2);
				int y = (int)(((double)HIST_HEIGHT*(double)histogram[i])/maxCount);
				if (y>HIST_HEIGHT) y = HIST_HEIGHT;
				for (int j = 0; j<barWidth; j++)
					ip.drawLine(x+j+XMARGIN, YMARGIN+HIST_HEIGHT, x+j+XMARGIN, YMARGIN+HIST_HEIGHT-y);
			}
		} else if (histogram.length<=HIST_WIDTH) {
			int index, y;
			for (int i=0; i<HIST_WIDTH; i++) {
				index = (int)(i*(double)histogram.length/HIST_WIDTH); 
				y = (int)(((double)HIST_HEIGHT*(double)histogram[index])/maxCount);
				if (y>HIST_HEIGHT) y = HIST_HEIGHT;
				ip.drawLine(i+XMARGIN, YMARGIN+HIST_HEIGHT, i+XMARGIN, YMARGIN+HIST_HEIGHT-y);
			}
		} else {
			double xscale = (double)HIST_WIDTH/histogram.length; 
			for (int i=0; i<histogram.length; i++) {
				long value = histogram[i];
				if (value>0L) {
					int y = (int)(((double)HIST_HEIGHT*(double)value)/maxCount);
					if (y>HIST_HEIGHT) y = HIST_HEIGHT;
					int x = (int)(i*xscale)+XMARGIN;
					ip.drawLine(x, YMARGIN+HIST_HEIGHT, x, YMARGIN+HIST_HEIGHT-y);
				}
			}
		}
	}
		
	void drawLogPlot (long maxCount, ImageProcessor ip) {
		frame = new Rectangle(XMARGIN, YMARGIN, HIST_WIDTH, HIST_HEIGHT);
		ip.drawRect(frame.x-1, frame.y, frame.width+2, frame.height+1);
		double max = Math.log(maxCount);
		ip.setColor(Color.gray);
		if (histogram.length==256) {
			double scale2 = HIST_WIDTH/256.0;
			int barWidth = 1;
			if (SCALE>1) barWidth=2;
			if (SCALE>2) barWidth=3;
			for (int i=0; i < 256; i++) {
				int x =(int)(i*scale2);
				int y = histogram[i]==0?0:(int)(HIST_HEIGHT*Math.log(histogram[i])/max);
				if (y>HIST_HEIGHT) y = HIST_HEIGHT;
				for (int j = 0; j<barWidth; j++)
					ip.drawLine(x+j+XMARGIN, YMARGIN+HIST_HEIGHT, x+j+XMARGIN, YMARGIN+HIST_HEIGHT-y);
			}
		} else if (histogram.length<=HIST_WIDTH) {
			int index, y;
			for (int i = 0; i<HIST_WIDTH; i++) {
				index = (int)(i*(double)histogram.length/HIST_WIDTH); 
				y = histogram[index]==0?0:(int)(HIST_HEIGHT*Math.log(histogram[index])/max);
				if (y>HIST_HEIGHT) y = HIST_HEIGHT;
				ip.drawLine(i+XMARGIN, YMARGIN+HIST_HEIGHT, i+XMARGIN, YMARGIN+HIST_HEIGHT-y);
			}
		} else {
			double xscale = (double)HIST_WIDTH/histogram.length; 
			for (int i=0; i<histogram.length; i++) {
				long value = histogram[i];
				if (value>0L) {
					int y = (int)(HIST_HEIGHT*Math.log(value)/max);
					if (y>HIST_HEIGHT) y = HIST_HEIGHT;
					int x = (int)(i*xscale)+XMARGIN;
					ip.drawLine(x, YMARGIN+HIST_HEIGHT, x, YMARGIN+HIST_HEIGHT-y);
				}
			}
		}
		ip.setColor(Color.black);
	}
		
	void drawText(ImageProcessor ip, int x, int y, boolean fixedRange) {
		ip.setFont(font);
		ip.setAntialiasedText(true);
		double hmin = cal.getCValue(stats.histMin);
		double hmax = cal.getCValue(stats.histMax);
		double range = hmax-hmin;
		if (fixedRange&&!cal.calibrated()&&hmin==0&&hmax==255)
			range = 256;
		ip.drawString(d2s(hmin), x - 4, y);
		ip.drawString(d2s(hmax), x + HIST_WIDTH - getWidth(hmax, ip) + 10, y);
		if (rgbMode>=INTENSITY1) {
			x += HIST_WIDTH/2;
			y += 1;
			ip.setJustification(ImageProcessor.CENTER_JUSTIFY);
			boolean weighted = ((ColorProcessor)ip).weightedHistogram();
			switch (rgbMode) {
				case INTENSITY1: ip.drawString((weighted?"Intensity (weighted)":"Intensity (unweighted)"), x, y); break;
				case INTENSITY2: ip.drawString((weighted?"Intensity (unweighted)":"Intensity (weighted)"), x, y); break;
				case RGB: ip.drawString("R+G+B", x, y); break;
				case RED: ip.drawString("Red", x, y); break;
				case GREEN: ip.drawString("Green", x, y); break;
				case BLUE: ip.drawString("Blue", x, y);  break;
			}
			ip.setJustification(ImageProcessor.LEFT_JUSTIFY);
		}        
		double binWidth = range/stats.nBins;
		binWidth = Math.abs(binWidth);
		showBins = binWidth!=1.0 || !fixedRange;
		col1 = XMARGIN + 5;
		col2 = XMARGIN + HIST_WIDTH/2;
		row1 = y+(int)(25*SCALE);
		if (showBins) row1 -= (int)(8*SCALE);
		row2 = row1 + (int)(15*SCALE);
		row3 = row2 + (int)(15*SCALE);
		row4 = row3 + (int)(15*SCALE);
		row5 = row4 + (int)(15*SCALE);
		long count = stats.longPixelCount>0?stats.longPixelCount:stats.pixelCount;
		String modeCount = " (" + stats.maxCount + ")";
		if (modeCount.length()>12) modeCount = "";
		
		ip.drawString("N: " + count, col1, row1);
		ip.drawString("Min: " + d2s(stats.min), col2, row1);
		ip.drawString("Mean: " + d2s(stats.mean), col1, row2);
		ip.drawString("Max: " + d2s(stats.max), col2, row2);
		ip.drawString("StdDev: " + d2s(stats.stdDev), col1, row3);
		ip.drawString("Mode: " + d2s(stats.dmode) + modeCount, col2, row3);
		if (showBins) {
			ip.drawString("Bins: " + d2s(stats.nBins), col1, row4);
			ip.drawString("Bin Width: " + d2s(binWidth), col2, row4);
		}
		drawValueAndCount(ip, Double.NaN, -1);		
	}
	
	private void drawValueAndCount(ImageProcessor ip, double value, long count) {
		int y = showBins?row4:row3;
		ip.setRoi(0, y, WIN_WIDTH, WIN_HEIGHT-y);
		ip.setColor(Color.white);
		ip.fill();
		ip.setColor(Color.black);
		String sValue = Double.isNaN(value)?"---":d2s(value);
		String sCount = count==-1?"---":""+count;
		int row = showBins?row5:row4;
		ip.drawString("Value: " + sValue, col1, row);
		ip.drawString("Count: " + sCount, col2, row);
	}	
	
	private String d2s(double d) {
		if ((int)d==d)
			return IJ.d2s(d, 0);
		else
    		return IJ.d2s(d, 3, 8);
    }
	
	int getWidth(double d, ImageProcessor ip) {
		return ip.getStringWidth(d2s(d));
	}
	
	/** Returns the histogram values as a ResultsTable. */
	public ResultsTable getResultsTable() {
		int decimalPlaces = Analyzer.getPrecision();
		if (digits==0 && stats.binSize!=1.0)
			digits = decimalPlaces;
		ResultsTable rt = new ResultsTable();
		rt.setPrecision(digits);
		String vheading = stats.binSize==1.0?"value":"bin start";
		if (cal.calibrated() && !cal.isSigned16Bit()) {
			for (int i=0; i<stats.nBins; i++) {
				rt.setValue("level", i, i);
				rt.setValue(vheading, i, cal.getCValue(stats.histMin+i*stats.binSize));
				rt.setValue("count", i, histogram[i]);
			}
		} else {
			for (int i=0; i<stats.nBins; i++) {
				if (stats.binSize!=1.0)
					rt.setValue("index", i, i);
				rt.setValue(vheading, i, cal.getCValue(stats.histMin+i*stats.binSize));
				rt.setValue("count", i, histogram[i]);
			}
		}
		return rt;
	}

	protected void showList() {
		ResultsTable rt = getResultsTable();
		rt.show(getTitle());
	}
	
	protected void copyToClipboard() {
		Clipboard systemClipboard = null;
		try {systemClipboard = getToolkit().getSystemClipboard();}
		catch (Exception e) {systemClipboard = null; }
		if (systemClipboard==null)
			{IJ.error("Unable to copy to Clipboard."); return;}
		IJ.showStatus("Copying histogram values...");
		CharArrayWriter aw = new CharArrayWriter(stats.nBins*4);
		PrintWriter pw = new PrintWriter(aw);
		for (int i=0; i<stats.nBins; i++)
			pw.print(ResultsTable.d2s(cal.getCValue(stats.histMin+i*stats.binSize), digits)+"\t"+histogram[i]+"\n");
		String text = aw.toString();
		pw.close();
		StringSelection contents = new StringSelection(text);
		systemClipboard.setContents(contents, this);
		IJ.showStatus(text.length() + " characters copied to Clipboard");
	}
	
	void replot() {
		ImageProcessor ip = this.imp.getProcessor();
		frame = new Rectangle(XMARGIN, YMARGIN, HIST_WIDTH, HIST_HEIGHT);
		ip.setColor(Color.white);
		ip.setRoi(frame.x-1, frame.y, frame.width+2, frame.height);
		ip.fill();
		ip.resetRoi();
		ip.setColor(Color.black);
		ip.setLineWidth(1);
		if (logScale) {
			drawLogPlot(yMax>0?yMax:newMaxCount, ip);
			drawPlot(yMax>0?yMax:newMaxCount, ip);
		} else
			drawPlot(yMax>0?yMax:newMaxCount, ip);
		this.imp.updateAndDraw();
	}
		
	public void actionPerformed(ActionEvent e) {
		Object b = e.getSource();
		if (b==live)
			toggleLiveMode();
		else if (b==rgb)
			changeChannel();
		else if (b==list)
			showList();
		else if (b==copy)
			copyToClipboard();
		else if (b==log) {
			logScale = !logScale;
			replot();
		}
	}
	
	public void lostOwnership(Clipboard clipboard, Transferable contents) {}
	
	public int[] getHistogram() {
		int[] hist = new int[histogram.length];
		for (int i=0; i<histogram.length; i++)
			hist[i] = (int)histogram[i];
		return hist;
	}

	public double[] getXValues() {
		double[] values = new double[stats.nBins];
		for (int i=0; i<stats.nBins; i++)
			values[i] = cal.getCValue(stats.histMin+i*stats.binSize);
		return values;
	}

	private void toggleLiveMode() {
		if (liveMode())
			removeListeners();
		else
			enableLiveMode();
	}
	
	private void changeChannel() {
		ImagePlus imp = WindowManager.getImage(srcImageID);
		if (imp==null || !imp.isRGB())
			return;
		else {
			rgbMode++;
			if (rgbMode>BLUE) rgbMode=INTENSITY1;
			ColorProcessor cp = (ColorProcessor)imp.getProcessor();
			boolean weighted = cp.weightedHistogram();
			if (rgbMode==INTENSITY2) {
				double[] weights = cp.getRGBWeights();
				if (weighted)
					cp.setRGBWeights(1d/3d, 1d/3d, 1d/3d);
				else
					cp.setRGBWeights(0.299, 0.587, 0.114);
				showHistogram(imp, 256);
				cp.setRGBWeights(weights);
			} else
				showHistogram(imp, 256);
		}
	}

	private boolean liveMode() {
		return live!=null && live.getForeground()==Color.red;
	}

	private void enableLiveMode() {
		if (bgThread==null) {
			srcImp = WindowManager.getImage(srcImageID);
			if (srcImp==null) return;
			bgThread = new Thread(this, "Live Histogram");
			bgThread.setPriority(Math.max(bgThread.getPriority()-3, Thread.MIN_PRIORITY));
			bgThread.start();
			imageUpdated(srcImp);
		}
		createListeners();
		if (srcImp!=null)
			imageUpdated(srcImp);
	}
	
	// Unused
	public void imageOpened(ImagePlus imp) {
	}

	// This listener is called if the source image content is changed
	public synchronized void imageUpdated(ImagePlus imp) {
		if (imp==srcImp) { 
			doUpdate = true;
			notify();
		}
	}
	
	public synchronized void roiModified(ImagePlus img, int id) {
		if (img==srcImp) {
			doUpdate=true;
			notify();
		}
	}

	// If either the source image or this image are closed, exit
	public void imageClosed(ImagePlus imp) {
		if (imp==srcImp || imp==this.imp) {
			if (bgThread!=null)
				bgThread.interrupt();
			bgThread = null;
			removeListeners();
			srcImp = null;
		}
	}
	
	// the background thread for live plotting.
	public void run() {
		while (true) {
			if (doUpdate && srcImp!=null) {
				if (srcImp.getRoi()!=null)
					IJ.wait(50);	//delay to make sure the roi has been updated
				if (srcImp!=null) {
					if (srcImp.getBitDepth()==16 && ImagePlus.getDefault16bitRange()!=0)
						showHistogram(srcImp, 256, 0, Math.pow(2,ImagePlus.getDefault16bitRange())-1);
					else
						showHistogram(srcImp, 256);
				}
			}
			synchronized(this) {
				if (doUpdate) {
					doUpdate = false;		//and loop again
				} else {
					try {wait();}	//notify wakes up the thread
					catch(InterruptedException e) { //interrupted tells the thread to exit
						return;
					}
				}
			}
		}
	}
	
	private void createListeners() {
		if (srcImp==null)
			return;
		ImagePlus.addImageListener(this);
		Roi.addRoiListener(this);
		if (live!=null) {
			Font font = live.getFont();
			live.setFont(new Font(font.getName(), Font.BOLD, font.getSize()));
			live.setForeground(Color.red);
		}
	}
	
	private void removeListeners() {
		if (srcImp==null)
			return;
		ImagePlus.removeImageListener(this);
		Roi.removeRoiListener(this);
		if (live!=null) {
			Font font = live.getFont();
			live.setFont(new Font(font.getName(), Font.PLAIN, font.getSize()));
			live.setForeground(Color.black);
		}
	}

}
