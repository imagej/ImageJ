package ij.plugin.filter;
import java.awt.*;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.text.*;
/** Implements the Image/Run Benchmark command. */
public class Benchmark implements PlugInFilter{

	String arg;
	ImagePlus imp;
	boolean showUpdates;
	int counter;
	
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
	
		if (arg.equals("show"))
			{showBenchmarkResults(); return DONE;}

		if (arg.equals("jvm"))
			{showJVMComparison(); return DONE;}

		if (arg.equals("particles"))
			{showParticlesResults(); return DONE;}

		this.arg = arg;
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
	
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

		if (arg.equals("draw")) {
			ImageWindow win = imp.getWindow();
			win.running = true;
			int loops = 200;
			long startTime = System.currentTimeMillis();
			for (int i=0; i <loops; i++) {
				imp.draw();
				Thread.yield();
				if (i%5==0) IJ.showProgress((double)i/loops);
				if (!win.running) {IJ.showProgress(1.0); return;}
			}
			IJ.showProgress(1.0);
			int pixels = imp.getWidth()*imp.getHeight()*loops;
			long time = System.currentTimeMillis()-startTime;
			double seconds = time/1000.0;
			IJ.write(IJ.d2s((pixels/seconds)/1000000.0) + " million pixels per second");
			return;
		}
		
		showUpdates = !arg.equals("no-updates");
		
		ip.setInterpolate(false);
		for (int i=0; i <4; i++) {
			ip.invert();
			updateScreen(imp);
		}
		for (int i=0; i <4; i++) {
			ip.flipVertical();
			updateScreen(imp);
		}
		ip.flipHorizontal(); updateScreen(imp);
		ip.flipHorizontal(); updateScreen(imp);
		for (int i=0; i <6; i++) {
			ip.smooth();
			updateScreen(imp);
		}
		ip.reset();
		for (int i=0; i <6; i++) {
			ip.sharpen();
			updateScreen(imp);
		}
		ip.reset();
		ip.smooth(); updateScreen(imp);
		ip.findEdges(); updateScreen(imp);
		ip.invert(); updateScreen(imp);
		ip.autoThreshold(); updateScreen(imp);
		ip.reset();
		ip.medianFilter(); updateScreen(imp);
		for (int i=0; i <360; i +=15) {
			ip.reset();
			ip.rotate(i);
			updateScreen(imp);
		}
		double scale = 1.5;
		for (int i=0; i <8; i++) {
			ip.reset();
			ip.scale(scale, scale);
			updateScreen(imp);
			scale = scale*1.5;
		}
		for (int i=0; i <12; i++) {
			ip.reset();
			scale = scale/1.5;
			ip.scale(scale, scale);
			updateScreen(imp);
		}
		ip.reset();
		updateScreen(imp);
	}
	
	void updateScreen(ImagePlus imp) {
		if (showUpdates)
			imp.updateAndDraw();
		IJ.showStatus((counter++) + "/"+72);
	}

	void showBenchmarkResults() {
		TextWindow tw = new TextWindow("ImageJ Benchmark", "", 450, 450);
		tw.setFont(new Font("Monospaced", Font.PLAIN, 12));
		tw.append("Time in seconds needed to perform 62 image processing");
		tw.append("operations on the 512x512 \"Mandrill\" image");
		tw.append("---------------------------------------------------------");
		tw.append(" 2.7   Xeon/1.7 (2X), WinXP  IE 6.0");
		tw.append(" 3.3   Pentium 4/1.4, Win2K  IE 5.0");
		tw.append(" 5.3   Pentium 3/750, Win98  IE 5.0");
		tw.append(" 5.6   Pentium 4/1.4, Win2K  JDK 1.3");
		tw.append(" 6.0   Pentium 3/750, Win98  Netscape 4.7");
		tw.append(" 8.6   PPC G4/400, MacOS     MRJ 2.2");
		tw.append(" 9.8   Pentium 2/400, Linux  IBM JDK 1.1.8");
		tw.append("  11   Pentium 2/400, Win95  JRE 1.1.8");
		tw.append("  12   Pentium 2/400, Win95  IE 5.5");
		tw.append("  12   Pentium 2/400, Win95  JDK 1.3");
		tw.append("  13   Pentium 2/400, Win95  Netscape 4.5");
		tw.append("  14   PPC G3/300, MacOS     MRJ 2.1");
		tw.append("  38   PPC 604/132, MacOS    MRJ 2.1ea2");
		tw.append("  61   PPC 604/132, MacOS    MRJ 2.0");
		tw.append("  89   Pentium/100, Win95    JRE 1.1.6");
		tw.append("  96   Pentium/400, Linux    Sun JDK 1.2.2 (17 with JIT)");
		tw.append("");
	}
	
	void showJVMComparison() {
		TextWindow tw = new TextWindow("JVM Comparison", "", 500, 550);
		tw.setFont(new Font("Monospaced", Font.PLAIN, 12));
	
		tw.append("   JVM           Benchmark  No Updates  Waves(fps) Particles");
		tw.append("");
		tw.append("PC (JRE 1.1.8)     11.5        8.7         16         44");
		tw.append("PC (MS Java)       12.3        9.6         19         38");
		tw.append("PC (JDK 1.4rc)     12.6       10.8         21         34");
		tw.append("PC (JDK 1.3)       13.2       11.8         20         34");
		tw.append("Mac OS 8/9)         8.7        6.4         19         35");
		tw.append("Mac OS X           11.0        9.0         18         44");
		tw.append("Linux (IBM 1.1.8)  10.8        8.8         15         46");
		tw.append("Linux (Sun 1.3.1)  13.2       11.5         17         13!");
		tw.append("");
		tw.append("'Benchmark' is the time needed to perform 72 image processing");
		tw.append("operations on a 512x512 RGB image with the screen updated");
		tw.append("after each operation. Lower is better.");
		tw.append("");
		tw.append("'No Updates' is the time needed to perform 72 image processing");
		tw.append("operations with no screen updates. Lower is better.");
		tw.append("");
		tw.append("'Waves' is the animation rate in frames per second using");
		tw.append("a 512x512 RGB image. Higher is better.");
		tw.append("");
		tw.append("'Particles' is the time needed to measure the location and");
		tw.append("size of 5097 particles in a 2000x1000 binary image. Lower");
		tw.append("is better.");
		tw.append("");
		tw.append("Test were run on 400Mhz machines. The Linux machine has 2 CPUs.");
	}
	
	void showParticlesResults() {
		TextWindow tw = new TextWindow("Particles Benchmark", "", 450, 500);
		tw.setFont(new Font("Monospaced", Font.PLAIN, 12));
		tw.append("These are times in seconds needed to measure the size");
		tw.append("and location of 5097 objects in a 2000x1000 binary image.");
		tw.append("Tests were run on 400Mhz Pentiums and a 400Mhz G3 Mac.");
		tw.append("");
		tw.append("24  Netscape 4.5");
		tw.append("24  Internet Explorer 4.0");
		tw.append("28  JDK 1.3");
		tw.append("30  JDK 1.2");
		tw.append("33  JDK 1.1.8");
		tw.append("35  MacOS Runtime for Java 2.2");
		tw.append("60  Sun JDK 1.2.2 for Linux");
		tw.append("70  IBM JDK 1.1.8 for Linux");
		tw.append("");
		tw.append("To run the benchmark:");
		tw.append("");
		tw.append("1) Open the test image using File/Open Samples/Particles.");
		tw.append("2) Check \"Area\" and \"Centroid\" in Analalyze/Set Measurements.");
		tw.append("3) Select Analyze/Analyze Particles.");
		tw.append("3) Check \"Display Results\" and then click \"OK\".");
	}

}


