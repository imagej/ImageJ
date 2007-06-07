package ij.gui;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;
import java.awt.datatransfer.*;
import ij.*;
import ij.process.*;
import ij.measure.Measurements;

/** This class is an extended ImageWindow that displays histograms. */
public class HistogramWindow extends ImageWindow implements Measurements,
ActionListener, ClipboardOwner {
	static final int WIN_WIDTH = 300;
	static final int WIN_HEIGHT = 250;
    static final int HIST_WIDTH = 256;
    static final int HIST_HEIGHT = 128;
	static final int BAR_HEIGHT = 12;
	static final int XMARGIN = 20;
	static final int YMARGIN = 10;
	
	private ImageStatistics stats;
	private float[] cTable;
	private int[] histogram;
	private LookUpTable lut;
	private Rectangle frame = null;
	private Image img;
	private Button list, save, copy;
	private Label value, count;
	private static String defaultDirectory = null;
	public static int nBins = 256;
    
    /** Displays a histogram using the title "Histogram". */
    public HistogramWindow(ImagePlus imp) {
		super(new ImagePlus("Histogram", GUI.createBlankImage(WIN_WIDTH, WIN_HEIGHT)));
		showHistogram(imp, nBins);

    }

   /** Displays a histogram using the specified title and number of bins. */
    public HistogramWindow(String title, ImagePlus imp, int bins) {
		super(new ImagePlus(title, GUI.createBlankImage(WIN_WIDTH, WIN_HEIGHT)));
		showHistogram(imp, bins);

    }

    public void showHistogram(ImagePlus imp, int bins) {
 		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));
		list = new Button(" List ");
		list.addActionListener(this);
		buttons.add(list);
		copy = new Button("Copy...");
		copy.addActionListener(this);
		buttons.add(copy);
		Panel valueAndCount = new Panel();
		valueAndCount.setLayout(new GridLayout(2, 1));
		value = new Label("                     ");
		value.setFont(new Font("Monospaced", Font.PLAIN, 12));
		valueAndCount.add(value);
		count = new Label("                     ");
		count.setFont(new Font("Monospaced", Font.PLAIN, 12));
		valueAndCount.add(count);
		buttons.add(valueAndCount);
		add(buttons);
		pack();

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
        this.imp.draw();
    }

    public void mouseMoved(int x, int y) {
		if (value==null || count==null)
			return;
		if ((frame!=null)  && x>=frame.x && x<=(frame.x+frame.width)) {
			x = x - frame.x;
			if (x>255) x = 255;
			int index = (int)(x*((double)histogram.length)/HIST_WIDTH);
			double v = (cTable!=null&&cTable.length==256)?cTable[index]:stats.histMin+index*stats.binSize;
			if (stats.nBins<256) v += stats.binSize/2.0;
			if (v==(int)v)
				value.setText("  Value: " + (int)v);
			else
				value.setText("  Value: " + IJ.d2s(v));
			count.setText("  Count: " + histogram[index]);
		} else {
			value.setText("");
			count.setText("");
		}
	}
    
    void drawHistogram(Graphics g, boolean fixedRange) {
    	int x, y;
    	int maxCount2 = 0, newMaxCount;
    	int mode2 = 0;
    	int saveModalCount;
		    	
		g.setColor(Color.black);
			        
        saveModalCount = histogram[stats.mode];
        for (int i = 0; i<histogram.length; i++)
            if ((histogram[i] > maxCount2) && (i != stats.mode)) {
                maxCount2 = histogram[i];
                mode2 = i;
            }
        newMaxCount = stats.maxCount;
        if ((newMaxCount>(maxCount2 * 2)) && (maxCount2 != 0)) {
        	newMaxCount = (int)(maxCount2 * 1.5);
        	histogram[stats.mode] = newMaxCount;
        }
        
        frame = new Rectangle(XMARGIN, YMARGIN, HIST_WIDTH, HIST_HEIGHT);
        g.drawRect(frame.x, frame.y, frame.width, frame.height);
        x = XMARGIN + 1;
        int count;
		for (int i = 0; i<HIST_WIDTH; i++) {
			int index = (int)(i*(double)histogram.length/HIST_WIDTH); 
			//if (index>=histogram.length) index = histogram.length-1;
			count = histogram[index];
            g.drawLine(i+XMARGIN, YMARGIN+HIST_HEIGHT,
              i+XMARGIN, YMARGIN+HIST_HEIGHT-((int)(HIST_HEIGHT*count)/newMaxCount));
           }
        histogram[stats.mode] = saveModalCount;
        
        
        y = YMARGIN + HIST_HEIGHT + 2;
        lut.drawUnscaledColorBar(g, x, y, 256, BAR_HEIGHT);
        y += BAR_HEIGHT;
        
        y += 15;
        double hmin = stats.histMin;
        double hmax = stats.histMax;
        if (cTable!=null && cTable.length==256) {
        	hmin = cTable[(int)hmin];
        	hmax = cTable[(int)hmax];
        }
        g.drawString(d2s(hmin), x - 4, y);
        g.drawString(d2s(hmax), x + HIST_WIDTH - getWidth(hmax, g) + 10, y);
        
		x += 10;
		y += 20;
		int saveY = y;
		g.drawString("Total: " + stats.pixelCount, x, y);
		y += 15;
		g.drawString("Mean: " + d2s(stats.mean), x, y);
		y += 15;
		g.drawString("Mode: " + d2s(stats.dmode) + " (" + stats.maxCount + ")", x, y);
		y += 15;
		g.drawString("Min: " + d2s(stats.min) + ",  Max: " + d2s(stats.max), x, y);
		
        if (stats.nBins!=256 || !fixedRange) {
	        x = XMARGIN + HIST_WIDTH/2 + 20;
			y = saveY;
			g.drawString("Bins: " + d2s(stats.nBins), x, y);
			y += 15;
			double range = fixedRange?256:stats.max-stats.min;
			double binWidth = range/stats.nBins;
			g.drawString("Bin Width: " + d2s(binWidth), x, y);
		}
	}

	String d2s(double d) {
		if ((int)d==d)
			return IJ.d2s(d,0);
		else
			return IJ.d2s(d);
	}
	
	int getWidth(double d, Graphics g) {
		FontMetrics fm = g.getFontMetrics();
		return fm.stringWidth(d2s(d));
		//String s = ""+d;
		//return (""+d).length()*8;
	}

	void showList() {
		IJ.setColumnHeadings("value\tcount");
		for (int i=0; i<stats.nBins; i++)
			IJ.write(i+"\t"+histogram[i]);
	}

	/*
	void saveAsText() {
		FileDialog fd = new FileDialog(this, "Save as Text...", FileDialog.SAVE);
		if (defaultDirectory!=null)
			fd.setDirectory(defaultDirectory);
		fd.setVisible(true);
		String name = fd.getFile();
		String directory = fd.getDirectory();
		defaultDirectory = directory;
		fd.dispose();
		PrintStream ps = null;
		try {ps = new PrintStream(new FileOutputStream(directory+name));}
		catch (IOException e) {IJ.write("" + e); return;}
		IJ.wait(250);  // give system time to redraw ImageJ window
		IJ.showStatus("Saving histogram values...");
		for (int i=0; i<stats.nBins; i++)
			ps.println(histogram[i]);
		ps.close();
	}
	*/
	
	void copyToClipboard() {
		Clipboard systemClipboard = null;
		try {systemClipboard = getToolkit().getSystemClipboard();}
		catch (Exception e) {systemClipboard = null; }
		if (systemClipboard==null)
			{IJ.error("Unable to copy to Clipboard."); return;}
		IJ.showStatus("Copying histogram values...");
        CharArrayWriter aw = new CharArrayWriter(stats.nBins*4);
        PrintWriter pw = new PrintWriter(aw);
		for (int i=0; i<stats.nBins; i++)
			//pw.println(histogram[i]);
			pw.print(i+"\t"+histogram[i]+"\n");
        String text = aw.toString();
		pw.close();
		StringSelection contents = new StringSelection(text);
		systemClipboard.setContents(contents, this);
		IJ.showStatus(text.length() + " characters copied to Clipboard");
	}
	
	public void actionPerformed(ActionEvent e) {
		Object b = e.getSource();
		if (b==list)
			showList();
		else
			copyToClipboard();
	}
	
	public void lostOwnership(Clipboard clipboard, Transferable contents) {}

}
