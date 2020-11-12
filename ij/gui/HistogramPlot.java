package ij.gui;
import ij.*;
import ij.process.*;
import ij.plugin.filter.Analyzer;
import ij.measure.*;
import ij.macro.Interpreter;
import java.awt.*;
import java.awt.image.*;

public class HistogramPlot extends ImagePlus {
	static final double SCALE = Prefs.getGuiScale();
	static final int HIST_WIDTH = (int)(SCALE*256);
	static final int HIST_HEIGHT = (int)(SCALE*128);
	static final int XMARGIN = (int)(20*SCALE);
	static final int YMARGIN = (int)(10*SCALE);
	static final int WIN_WIDTH = HIST_WIDTH + (int)(44*SCALE);
	static final int WIN_HEIGHT = HIST_HEIGHT + (int)(118*SCALE);
	static final int BAR_HEIGHT = (int)(SCALE*12);
	static final int INTENSITY1=0, INTENSITY2=1, RGB=2, RED=3, GREEN=4, BLUE=5;
	
	int rgbMode = -1;
	ImageStatistics stats;
	boolean stackHistogram;
	Calibration cal;
	long[] histogram;
	LookUpTable lut;
	int decimalPlaces;
	int digits;
	long newMaxCount;
	boolean logScale;
	int yMax;
	int srcImageID;
	Rectangle frame;
	Font font = new Font("SansSerif",Font.PLAIN,(int)(12*SCALE));
	boolean showBins;
	int col1, col2, row1, row2, row3, row4, row5;
	    
	public HistogramPlot() {
		setImage(NewImage.createRGBImage("Histogram", WIN_WIDTH, WIN_HEIGHT, 1, NewImage.FILL_WHITE));
	}

	/** Plots a histogram using the specified title and number of bins. 
		Currently, the number of bins must be 256 expect for 32 bit images. */
	public void draw(String title, ImagePlus imp, int bins) {
		draw(imp, bins, 0.0, 0.0, 0);
	}

	/** Plots a histogram using the specified title, number of bins and histogram range.
		Currently, the number of bins must be 256 and the histogram range range must be 
		the same as the image range expect for 32 bit images. */
	public void draw(ImagePlus imp, int bins, double histMin, double histMax, int yMax) {
		boolean limitToThreshold = (Analyzer.getMeasurements()&LIMIT)!=0;
		ImageProcessor ip = imp.getProcessor();
		if (ip.getMinThreshold()!=ImageProcessor.NO_THRESHOLD
		&& ip.getLutUpdateMode()==ImageProcessor.NO_LUT_UPDATE)
			limitToThreshold = false;  // ignore invisible thresholds
		if (imp.getBitDepth()==24 && rgbMode<INTENSITY1)
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
		draw(imp, stats);
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
	public void draw(ImagePlus imp, ImageStatistics stats) {
		if (imp.getBitDepth()==24 && rgbMode<INTENSITY1)
			rgbMode=INTENSITY1;
		stackHistogram = stats.stackStatistics;
		this.stats = stats;
		this.yMax = stats.histYMax;
		cal = imp.getCalibration();
		boolean limitToThreshold = (Analyzer.getMeasurements()&LIMIT)!=0;
		imp.getMask();
		histogram = stats.getHistogram();
		if (limitToThreshold && histogram.length==256) {
			ImageProcessor ip = imp.getProcessor();
			if (ip.getMinThreshold()!=ImageProcessor.NO_THRESHOLD) {
				int lower = scaleDown(ip, ip.getMinThreshold());
				int upper = scaleDown(ip, ip.getMaxThreshold());
				for (int i=0; i<lower; i++)
					histogram[i] = 0L;
				for (int i=upper+1; i<256; i++)
					histogram[i] = 0L;
			}
		}
		lut = imp.createLut();
		int type = imp.getType();
		boolean fixedRange = type==ImagePlus.GRAY8 || type==ImagePlus.COLOR_256 || type==ImagePlus.COLOR_RGB;
		ip.setColor(Color.white);
		ip.resetRoi();
		ip.fill();
		ImageProcessor srcIP = imp.getProcessor();
		drawHistogram(imp, ip, fixedRange, stats.histMin, stats.histMax);
	}
	
	protected void drawHistogram(ImageProcessor ip, boolean fixedRange) {
		drawHistogram(null, ip, fixedRange, 0.0, 0.0);
	}

	void drawHistogram(ImagePlus imp, ImageProcessor ip, boolean fixedRange, double xMin, double xMax) {
		setTitle("Histogram of "+imp.getShortTitle());
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
		if (logScale)
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
		if (!(ipSource instanceof ColorProcessor)) {
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
	
    @Override
    public void show() {
		if (IJ.isMacro()&&Interpreter.isBatchMode())
			super.show();
		else
			new HistogramWindow(this, WindowManager.getImage(srcImageID));
	}
	
}
