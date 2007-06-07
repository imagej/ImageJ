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

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
	
		if (arg.equals("show"))
			{showBenchmarkResults(); return DONE;}

		if (arg.equals("particles"))
			{showParticlesResults(); return DONE;}

		this.arg = arg;
		if (arg.equals("draw"))
			return DOES_ALL;
		else
			return DOES_ALL-DOES_32+NO_CHANGES;
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
		
		ip.setInterpolate(false);
		for (int i=0; i <4; i++) {
			ip.invert();
			imp.updateAndDraw();
		}
		for (int i=0; i <4; i++) {
			ip.flipVertical();
			imp.updateAndDraw();
		}
		ip.flipHorizontal(); imp.updateAndDraw();
		ip.flipHorizontal(); imp.updateAndDraw();
		for (int i=0; i <6; i++) {
			ip.smooth();
			imp.updateAndDraw();
		}
		ip.reset();
		for (int i=0; i <6; i++) {
			ip.sharpen();
			imp.updateAndDraw();
		}
		ip.reset();
		ip.smooth(); imp.updateAndDraw();
		ip.findEdges(); imp.updateAndDraw();
		ip.invert(); imp.updateAndDraw();
		ip.autoThreshold(); imp.updateAndDraw();
		ip.reset();
		ip.medianFilter(); imp.updateAndDraw();
		for (int i=0; i <360; i +=15) {
			ip.reset();
			ip.rotate(i);
			imp.updateAndDraw();
		}
		double scale = 1.5;
		for (int i=0; i <8; i++) {
			ip.reset();
			ip.scale(scale, scale);
			imp.updateAndDraw();
			scale = scale*1.5;
		}
		for (int i=0; i <12; i++) {
			ip.reset();
			scale = scale/1.5;
			ip.scale(scale, scale);
			imp.updateAndDraw();
		}
		ip.reset();
		imp.updateAndDraw();
	}

	void showBenchmarkResults() {
		TextWindow tw = new TextWindow("ImageJ Benchmark", "", 450, 450);
		tw.setFont(new Font("Monospaced", Font.PLAIN, 12));
		tw.append("Time in seconds needed to perform 62 image processing");
		tw.append("operations on the 512x512 \"Mandrill\" image");
		tw.append("---------------------------------------------------------");
		tw.append(" 3.3   Pentium 4/1.4, Win2K  IE 5.0");
		tw.append(" 5.3   Pentium 3/750, Win98  IE 5.0");
		tw.append(" 5.6   Pentium 4/1.4, Win2K  JDK 1.3");
		tw.append(" 6.0   Pentium 3/750, Win98  Netscape 4.7");
		tw.append(" 8.6   PPC G4/400, MacOS     MRJ 2.2");
		tw.append(" 9.1   Pentium 2/400, Win95  JRE 1.1.8");
		tw.append(" 9.2   Pentium 2/400, Win95  IE 4.0");
		tw.append(" 9.8   Pentium 2/400, Linux  IBM JDK 1.1.8");
		tw.append("  11   Pentium 2/400, Win95  JDK 1.2 (24% slower)");
		tw.append("  11   Pentium 2/400, Win95  Netscape 4.5");
		tw.append("  14   PPC G3/300, MacOS     MRJ 2.1");
		tw.append("  21   Pentium 2/400, Win95  JDK 1.3 (>2 times slower!!)");
		tw.append("  38   PPC 604/132, MacOS    MRJ 2.1ea2");
		tw.append("  61   PPC 604/132, MacOS    MRJ 2.0");
		tw.append("  89   Pentium/100, Win95    JRE 1.1.6");
		tw.append("  96   Pentium/400, Linux    Sun JDK 1.2.2 (17 with JIT)");
		tw.append("");
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


