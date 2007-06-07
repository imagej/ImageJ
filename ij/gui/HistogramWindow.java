package ij.gui;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;
import java.awt.datatransfer.*;
import ij.*;
import ij.process.*;
import ij.measure.Measurements;
import ij.plugin.filter.Analyzer;
import ij.text.TextWindow;

/** This class is an extended ImageWindow that displays histograms. */
public class HistogramWindow extends ImageWindow implements Measurements, ActionListener, ClipboardOwner {
	static final int WIN_WIDTH = 300;
	static final int WIN_HEIGHT = 240;
	static final int HIST_WIDTH = 256;
	static final int HIST_HEIGHT = 128;
	static final int BAR_HEIGHT = 12;
	static final int XMARGIN = 20;
	static final int YMARGIN = 10;
	
	protected ImageStatistics stats;
	protected float[] cTable;
	protected int[] histogram;
	protected LookUpTable lut;
	protected Rectangle frame = null;
	protected Image img;
	protected Button list, save, copy,log;
	protected Label value, count;
	protected static String defaultDirectory = null;
	protected int decimalPlaces;
	protected int newMaxCount;
	protected int plotScale = 1;
	protected boolean logScale;
	public static int nBins = 256;
    
	/** Displays a histogram using the title "Histogram of ImageName". */
	public HistogramWindow(ImagePlus imp) {
		super(new ImagePlus("Histogram of "+imp.getShortTitle(), GUI.createBlankImage(WIN_WIDTH, WIN_HEIGHT)));
		showHistogram(imp, nBins);
	}

	/** Displays a histogram using the specified title and number of bins. */
	public HistogramWindow(String title, ImagePlus imp, int bins) {
		super(new ImagePlus(title, GUI.createBlankImage(WIN_WIDTH, WIN_HEIGHT)));
		showHistogram(imp, bins);
	}

	public void showHistogram(ImagePlus imp, int bins) {
		setup();
		stats = imp.getStatistics(AREA+MEAN+MODE+MIN_MAX, bins);
		histogram = stats.histogram;
		lut = imp.createLut();
		img = this.imp.getImage();
		int type = imp.getType();
		cTable = imp.getCalibration().getCTable();
		boolean fixedRange = type==ImagePlus.GRAY8 || type==ImagePlus.COLOR_256 || type==ImagePlus.COLOR_RGB;
		Graphics g = img.getGraphics();
		if (g!=null)
			{drawHistogram(g, fixedRange); g.dispose();}
		this.imp.setImage(img); // needed to get text to show on WinNT??
		//this.imp.draw();
	}

	public void setup() {
 		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));
		list = new Button("List");
		list.addActionListener(this);
		buttons.add(list);
		copy = new Button("Copy");
		copy.addActionListener(this);
		buttons.add(copy);
		log = new Button("Log");
		log.addActionListener(this);
		buttons.add(log);
		Panel valueAndCount = new Panel();
		valueAndCount.setLayout(new GridLayout(2, 1));
		value = new Label("                  "); //21
		value.setFont(new Font("Monospaced", Font.PLAIN, 12));
		valueAndCount.add(value);
		count = new Label("                  ");
		count.setFont(new Font("Monospaced", Font.PLAIN, 12));
		valueAndCount.add(count);
		buttons.add(valueAndCount);
		add(buttons);
		pack();
    }

	public void mouseMoved(int x, int y) {
		if (value==null || count==null)
			return;
		if ((frame!=null)  && x>=frame.x && x<=(frame.x+frame.width)) {
			x = x - frame.x;
			if (x>255) x = 255;
			int index = (int)(x*((double)histogram.length)/HIST_WIDTH);
			double v = 0.0;
			if (cTable!=null && cTable.length==256)
				v = cTable[index];
			else if (cTable!=null && cTable.length==65536) {
				int index2 = (int)(stats.histMin+index*stats.binSize+stats.binSize/2.0);
				if (index2>=0&&index<65536) v = cTable[index2];
			} else {
				v = stats.histMin+index*stats.binSize;
				if (stats.binSize!=1.0) v += stats.binSize/2.0;
			}
			if (v==(int)v)
				value.setText("  Value: " + (int)v);
			else
				value.setText("  Value: " + IJ.d2s(v,decimalPlaces));
			count.setText("  Count: " + histogram[index]);
		} else {
			value.setText("");
			count.setText("");
		}
	}
    
	protected void drawHistogram(Graphics g, boolean fixedRange) {
		int x, y;
		int maxCount2 = 0;
		int mode2 = 0;
		int saveModalCount;
		    	
		g.setColor(Color.black);
		decimalPlaces = Analyzer.getPrecision();
		saveModalCount = histogram[stats.mode];
		for (int i = 0; i<histogram.length; i++)
 		if ((histogram[i] > maxCount2) && (i != stats.mode)) {
			maxCount2 = histogram[i];
			mode2 = i;
  		}
		newMaxCount = stats.maxCount;
		if ((newMaxCount>(maxCount2 * 2)) && (maxCount2 != 0)) {
			newMaxCount = (int)(maxCount2 * 1.5);
  			//histogram[stats.mode] = newMaxCount;
		}
		drawPlot(newMaxCount, g);
		histogram[stats.mode] = saveModalCount;
 		x = XMARGIN + 1;
		y = YMARGIN + HIST_HEIGHT + 2;
		lut.drawUnscaledColorBar(g, x, y, 256, BAR_HEIGHT);
		y += BAR_HEIGHT+15;
  		drawText(g, x, y, fixedRange);
	}

       
	void drawPlot(int maxCount, Graphics g) {
		frame = new Rectangle(XMARGIN, YMARGIN, HIST_WIDTH, HIST_HEIGHT);
		g.drawRect(frame.x, frame.y, frame.width, frame.height);
		int index, y;
		for (int i = 0; i<HIST_WIDTH; i++) {
			index = (int)(i*(double)histogram.length/HIST_WIDTH); 
			y = (int)(HIST_HEIGHT*histogram[index])/maxCount;
			if (y>HIST_HEIGHT)
				y = HIST_HEIGHT;
			g.drawLine(i+XMARGIN, YMARGIN+HIST_HEIGHT, i+XMARGIN, YMARGIN+HIST_HEIGHT-y);
		}
	}
		
	void drawLogPlot (int maxCount, Graphics g) {
		frame = new Rectangle(XMARGIN, YMARGIN, HIST_WIDTH, HIST_HEIGHT);
		g.drawRect(frame.x, frame.y, frame.width, frame.height);
		double max = Math.log(maxCount);
		g.setColor(Color.gray);
		int index, y;
		for (int i = 0; i<HIST_WIDTH; i++) {
			index = (int)(i*(double)histogram.length/HIST_WIDTH); 
			y = histogram[index]==0?0:(int)(HIST_HEIGHT*Math.log(histogram[index])/max);
			if (y>HIST_HEIGHT)
				y = HIST_HEIGHT;
			g.drawLine(i+XMARGIN, YMARGIN+HIST_HEIGHT, i+XMARGIN, YMARGIN+HIST_HEIGHT-y);
		}
		g.setColor(Color.black);
	}
		
	void drawText(Graphics g, int x, int y, boolean fixedRange) {
		double hmin = stats.histMin;
		double hmax = stats.histMax;
		if (cTable!=null && (int)hmin>=0 && (int)hmax<cTable.length) {
			hmin = cTable[(int)hmin];
			hmax = cTable[(int)hmax];
		}
		g.drawString(d2s(hmin), x - 4, y);
		g.drawString(d2s(hmax), x + HIST_WIDTH - getWidth(hmax, g) + 10, y);
        
		boolean showBins = stats.nBins!=256 || !fixedRange;
		int col1 = XMARGIN + 5;
		int col2 = XMARGIN + HIST_WIDTH/2;
		int row1 = y+25;
		if (showBins) row1 -= 8;
		int row2 = row1 + 15;
		int row3 = row2 + 15;
		int row4 = row3 + 15;
		g.drawString("Count: " + stats.pixelCount, col1, row1);
		g.drawString("Mean: " + d2s(stats.mean), col1, row2);
		g.drawString("StdDev: " + d2s(stats.stdDev), col1, row3);
		g.drawString("Mode: " + d2s(stats.dmode) + " (" + stats.maxCount + ")", col2, row3);
		g.drawString("Min: " + d2s(stats.min), col2, row1);
		g.drawString("Max: " + d2s(stats.max), col2, row2);
		
		if (showBins) {
			g.drawString("Bins: " + d2s(stats.nBins), col1, row4);
			g.drawString("Bin Width: " + d2s(stats.binSize), col2, row4);
		}
	}

	String d2s(double d) {
		if ((int)d==d)
			return IJ.d2s(d,0);
		else
			return IJ.d2s(d,decimalPlaces);
	}
	
	int getWidth(double d, Graphics g) {
		FontMetrics fm = g.getFontMetrics();
		return fm.stringWidth(d2s(d));
		//String s = ""+d;
		//return (""+d).length()*8;
	}

	void showList() {
		StringBuffer sb = new StringBuffer();
		if (stats.binSize==1.0 && stats.nBins==256 )
			for (int i=0; i<stats.nBins; i++)
				sb.append(i+"\t"+histogram[i]+"\n");
		else
			for (int i=0; i<stats.nBins; i++) {
				double v = stats.histMin+i*stats.binSize;
				if (stats.binSize!=1.0)
					v += stats.binSize/2;
				sb.append(IJ.d2s(v, decimalPlaces)+"\t"+histogram[i]+"\n");
			}
		TextWindow tw = new TextWindow(getTitle(), "value\tcount", sb.toString(), 200, 400);
	}

	void copyToClipboard() {
		Clipboard systemClipboard = null;
		try {systemClipboard = getToolkit().getSystemClipboard();}
		catch (Exception e) {systemClipboard = null; }
		if (systemClipboard==null)
			{IJ.error("Unable to copy to Clipboard."); return;}
		IJ.showStatus("Copying histogram values...");
		CharArrayWriter aw = new CharArrayWriter(stats.nBins*4);
		PrintWriter pw = new PrintWriter(aw);
		if (stats.binSize==1.0)
			for (int i=0; i<stats.nBins; i++)
				pw.print(i+"\t"+histogram[i]+"\n");
		else
			for (int i=0; i<stats.nBins; i++)
				pw.print(IJ.d2s(stats.histMin+i*stats.binSize+stats.binSize/2, decimalPlaces)+"\t"+histogram[i]+"\n");
		String text = aw.toString();
		pw.close();
		StringSelection contents = new StringSelection(text);
		systemClipboard.setContents(contents, this);
		IJ.showStatus(text.length() + " characters copied to Clipboard");
	}
	
	void replot() {
		logScale = !logScale;
		Graphics g = img.getGraphics();
		frame = new Rectangle(XMARGIN, YMARGIN, HIST_WIDTH, HIST_HEIGHT);
		g.setColor(Color.white);
		g.fillRect(frame.x, frame.y, frame.width, frame.height);
		g.setColor(Color.black);
		if (logScale) {
			drawLogPlot(newMaxCount, g);
			drawPlot(newMaxCount, g);
		} else
			drawPlot(newMaxCount, g);
		this.imp.setImage(img);
	}
	
	/*
	void rescale() {
		Graphics g = img.getGraphics();
		plotScale *= 2;
		if ((newMaxCount/plotScale)<50) {
			plotScale = 1;
			frame = new Rectangle(XMARGIN, YMARGIN, HIST_WIDTH, HIST_HEIGHT);
			g.setColor(Color.white);
			g.fillRect(frame.x, frame.y, frame.width, frame.height);
			g.setColor(Color.black);
		}
		drawPlot(newMaxCount/plotScale, g);
		//ImageProcessor ip = new ColorProcessor(img);
		//this.imp.setProcessor(null, ip);
		this.imp.setImage(img);
	}
	*/
	
	public void actionPerformed(ActionEvent e) {
		Object b = e.getSource();
		if (b==list)
			showList();
		else if (b==copy)
			copyToClipboard();
		else if (b==log)
			replot();
	}
	
	public void lostOwnership(Clipboard clipboard, Transferable contents) {}

}

