package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;

/** Continuously plots ImageJ's memory utilization. Could be a
	starting point for a video acquisition plugin. */
public class MemoryMonitor implements PlugIn {
	int width = 200;
	int height = 75;
	long fps, startTime, elapsedTime;
	ImageProcessor ip;
	int frames;
	ImageCanvas ic;
	int[] mem;
	int index;
	int value;
	int max = 12*1204*1024; // 12MB

	void showAbout() {
		IJ.showMessage("About MemoryMonitor...",
			"This plugin continuously plots ImageJ's memory\n" +
			"utilization. It could also be used as a starting\n" +
			"point for a video acquisition plugin. Hold down the\n" +
			"alt/option key when selecting the \"Monitor Memory\"\n" +
			"command and the plugin will use a 640x480 window\n" +
			"and display the frame rate. Click on the status bar in the\n" +
			"ImageJ window to force the JVM to do garbage collection."
		);
	}

	public void run(String arg) {
		if (arg.equals("about"))
			{showAbout(); return;}
		if (IJ.altKeyDown()) {
			// simulate frame grabber
			width = 640;
			height = 480;
		}
		ip = new ByteProcessor(width, height, new byte[width*height], null);
	 	ip.setColor(Color.white);
		ip.fill();
	 	ip.setColor(Color.black);
		ip.setFont(new Font("SansSerif",Font.PLAIN,12));
		ip.snapshot();
		ImagePlus imp = new ImagePlus("Memory", ip);
		ImageWindow.centerNextImage();
		imp.show();
		imp.lock();
		ImageWindow win = imp.getWindow();
		ic = win.getCanvas();
		mem = new int[width+1];
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		startTime = System.currentTimeMillis();
		win.running = true;
       	while (win.running) {
			updatePixels();
         	showValue();
			imp.updateAndDraw();
         	if (width==640)
        		Thread.yield();
        	else
        		IJ.wait(100);
       		frames++;
		}
		imp.unlock();
	}
	
    void showValue() {
    	String s = (value/1024)+"K";
    	if (width==640) {
			elapsedTime = System.currentTimeMillis()-startTime;
			if (elapsedTime>0) {
				double scale = ic.getMagnification();
				fps = (frames*10000)/elapsedTime;
				s += ", " + fps/10 + "." + fps%10 + " fps";
			}
    	}
    	ip.moveTo(2, 15);
		ip.drawString(s);
	}

	int memoryInUse() {
		long freeMem = Runtime.getRuntime().freeMemory();
		long totMem = Runtime.getRuntime().totalMemory();
		return  (int)(totMem-freeMem);
	}

	void updatePixels() {
		int used = memoryInUse();
		if (frames%10==0) value=used;
		if (used>0.9*max) max*=2;
		mem[index++] = used;
		if (index==mem.length) index = 0;
		ip.reset();
		int index2 = index+1;
		if (index2==mem.length) index2 = 0;
		double scale = (double)height/max;
		ip.moveTo(0, height-(int)(mem[index2]*scale));
		for (int x=1; x<width; x++) {
			index2++;
			if (index2==mem.length) index2 = 0;
			ip.lineTo(x, height-(int)(mem[index2]*scale));
		}
	}

}
