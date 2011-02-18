package ij.plugin.frame;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;

/** This plugin continuously plots ImageJ's memory
	utilization. Click on the status bar in the ImageJ
	window to force the JVM to do garbage collection.
*/
public class MemoryMonitor extends PlugInFrame {
 	private static final int WIDTH=250, HEIGHT=90;
	private static final String LOC_KEY = "memory.loc";
	private static MemoryMonitor instance;
	private ImageProcessor ip;
	private int frames;
	private double[] mem;
	private int index;
	private long value;
 	private double defaultMax = 15*1204*1024; // 15MB
	private double max = defaultMax;
	private long maxMemory = IJ.maxMemory();

	public MemoryMonitor() {
		super("Memory");
		if (instance!=null) {
			WindowManager.toFront(instance);
			return;
		}
		instance = this;
		WindowManager.addWindow(this);
		
		setLayout(new BorderLayout());
		Canvas ic = new Canvas();
		ic.setSize(WIDTH, HEIGHT);
		add(ic);
		setResizable(false);
		pack();
		Point loc = Prefs.getLocation(LOC_KEY);
		if (loc!=null)
			setLocation(loc);
		else
			GUI.center(this);
		ip = new ByteProcessor(WIDTH, HEIGHT, new byte[WIDTH*HEIGHT], null);
	 	ip.setColor(Color.white);
		ip.fill();
	 	ip.setColor(Color.black);
		ip.setFont(new Font("SansSerif",Font.PLAIN,12));
		ip.setAntialiasedText(true);
		ip.snapshot();
		Graphics g = ic.getGraphics();
		g.drawImage(ip.createImage(), 0, 0, null);
		show();
		ImageJ ij = IJ.getInstance();
		if (ij!=null) {
			addKeyListener(ij);
			ic.addKeyListener(ij);
			ic.addMouseListener(ij);
		}
		mem = new double[WIDTH+1];
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
       	while (true) {
			updatePlot();
         	showValue();
			g.drawImage(ip.createImage(), 0, 0, null);
        	IJ.wait(50);
       		frames++;
		}
	}
	
    void showValue() {
    	double value2 = (double)value/1048576L;
    	String s = IJ.d2s(value2,value2>50?0:2)+"MB";
    	if (maxMemory>0L) {
			double percent = value*100/maxMemory;
			s += " ("+(percent<1.0?"<1":IJ.d2s(percent,0)) + "%)";
		}
    	ip.moveTo(2, 15);
		ip.drawString(s);
	}

	void updatePlot() {
		double used = IJ.currentMemory();
		if (frames%10==0) value = (long)used;
		if (used>0.9*max) max *= 2.0;
		mem[index++] = used;
		if (index==mem.length) index = 0;
		double maxmax = 0.0;
		for (int i=0; i<mem.length; i++) {
			if (mem[i]>maxmax) maxmax= mem[i];
		}
		if (maxmax<defaultMax) max=defaultMax*2;
		if (maxmax<defaultMax/2) max = defaultMax;
		ip.setLineWidth(1);
		ip.reset();
		int index2 = index+1;
		if (index2==mem.length) index2 = 0;
		double scale = HEIGHT/max;
		ip.moveTo(0, HEIGHT-(int)(mem[index2]*scale));
		for (int x=1; x<WIDTH; x++) {
			index2++;
			if (index2==mem.length) index2 = 0;
			ip.lineTo(x, HEIGHT-(int)(mem[index2]*scale));
		}
	}

    public void close() {
	 	super.close();
		instance = null;
		Prefs.saveLocation(LOC_KEY, getLocation());
	}

}
