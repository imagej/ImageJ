package ij.plugin.frame;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;

/** This plugin continuously plots ImageJ's memory utilization. 
	Click on the plot to force the JVM to do garbage collection. */
public class MemoryMonitor extends PlugInFrame {
 	private static final int WIDTH=250, HEIGHT=90;
	private static final String LOC_KEY = "memory.loc";
	private static MemoryMonitor instance;
	private Image image;
	private Graphics2D g;
	private int frames;
	private double[] mem;
	private int index;
	private long value;
 	private double defaultMax = 20*1024*1024; // 20MB
	private double max = defaultMax;
	private long maxMemory = IJ.maxMemory();
	private boolean done;

	public MemoryMonitor() {
		super("Memory");
		if (instance!=null) {
			WindowManager.toFront(instance);
			return;
		}
		instance = this;
		WindowManager.addWindow(this);
		
		setLayout(new BorderLayout());
		Canvas ic = new PlotCanvas();
		ic.setSize(WIDTH, HEIGHT);
		add(ic);
		setResizable(false);
		pack();
		Point loc = Prefs.getLocation(LOC_KEY);
		if (loc!=null)
			setLocation(loc);
		else
			GUI.center(this);
		image = createImage(WIDTH,HEIGHT);
		g = (Graphics2D)image.getGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(Color.white);
		g.fillRect(0, 0, WIDTH, HEIGHT);
		g.setFont(new Font("SansSerif",Font.PLAIN,12));
		show();
		ImageJ ij = IJ.getInstance();
		if (ij!=null) {
			addKeyListener(ij);
			ic.addKeyListener(ij);
			ic.addMouseListener(ij);
		}
		mem = new double[WIDTH+1];
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
       	while (!done) {
			updatePlot();
         	addText();
			ic.repaint();
        	IJ.wait(50);
       		frames++;
		}
	}
	
    void addText() {
    	double value2 = (double)value/1048576L;
    	String s = IJ.d2s(value2,value2>50?0:2)+"MB";
    	if (maxMemory>0L) {
			double percent = value*100/maxMemory;
			s += " ("+(percent<1.0?"<1":IJ.d2s(percent,0)) + "%)";
		}
		g.drawString(s, 2, 15);
		String images = ""+WindowManager.getImageCount();
		g.drawString(images, WIDTH-(5+images.length()*8), 15);
	}

	void updatePlot() {
		double used = IJ.currentMemory();
		if (frames%10==0) value = (long)used;
		if (used>0.86*max) max *= 2.0;
		mem[index++] = used;
		if (index==mem.length) index = 0;
		double maxmax = 0.0;
		for (int i=0; i<mem.length; i++) {
			if (mem[i]>maxmax) maxmax= mem[i];
		}
		if (maxmax<defaultMax) max=defaultMax*2;
		if (maxmax<defaultMax/2) max = defaultMax;
		int index2 = index+1;
		if (index2==mem.length) index2 = 0;
		g.setColor(Color.white);
		g.fillRect(0, 0, WIDTH, HEIGHT);
	 	g.setColor(Color.black);	
		double scale = HEIGHT/max;
		int x1 = 0;
		int y1 = HEIGHT-(int)(mem[index2]*scale);
		for (int x2=1; x2<WIDTH; x2++) {
			index2++;
			if (index2==mem.length) index2 = 0;
			int y2 = HEIGHT-(int)(mem[index2]*scale);
			g.drawLine(x1, y1, x2, y2);
			x1=x2; y1=y2;
		}
	}

    public void close() {
	 	super.close();
		instance = null;
		Prefs.saveLocation(LOC_KEY, getLocation());
		done = true;
	}
	
	class PlotCanvas extends Canvas {
	
		public void update(Graphics g) {
			paint(g);
		}
		
		public void paint(Graphics g) {
			g.drawImage(image, 0, 0, null);
		}
		
	} 

}


