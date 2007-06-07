package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.text.*;
import java.awt.*;

/** Continuously applies a sine wave to the x-coordinates of an image. */
public class Waves implements PlugInFilter {
	String arg;
	double amplitude = 5.0;
	double frequency = 25.0;
	int[] waveTable;
	ImagePlus imp;
	ImageWindow win;
	ImageCanvas ic;
	byte[] pixels, pixels2;
	int[] rgbPixels, rgbPixels2;
	int width, height;
	int index, increment;
	long fps, startTime, elapsedTime;
	Graphics g;
	int frames;
	Roi roi;

	void showAbout() {
		IJ.showMessage("About Waves...",
			"\"Waves.java\" is a sample plugin filter that illustrates\n" +
			"how to continuously animate both 8-bit and RGB images until\n" +
			"the user clicks on the image or presses escape."
		);
	}

	void showRates() {
		TextWindow tw = new TextWindow("waves Benchmark", "", 450, 500);
		tw.setFont(new Font("Monospaced", Font.PLAIN, 12));
		tw.append("\"Waves\" frame rates using the 512x512 Mandrill image");
		tw.append("on a P2/400 running Windows 95 with 16-bit graphics.");
		tw.append("");
		tw.append("              JDK 1.1.7  JDK 1.2   JDK 1.3");
        tw.append("              ---------  -------   -----");
		tw.append("RGB              19.4     11.7      8.0");
		tw.append("RGB(1)           20.1      5.1");
		tw.append("RGB(2)           18.8      9.1");
		tw.append("RGB-D            14.4      2.9");
		tw.append("RGB-D(1)         15.4      2.9");
		tw.append("RGB-D(2)         14.6      2.8");
		tw.append("");
		tw.append("8-bit color      25.7      25.5    15.9");
		tw.append("8-bit color(1)   27.0       6.2");
		tw.append("8-bit color(2)   24.6      18.3");
		tw.append("");
		tw.append("8-bit grays      25.6      17.2    15.1");   
		tw.append("8-bit grays(1)   26.3      8.4");  
		tw.append("8-bit grays(2)   24.4      13.8");
		tw.append("");
		tw.append("(1) Image partially covered by ImageJ window");
		tw.append("(2) Image zoomed 2:1 and displayed in 512x512 window");
		tw.append("RGB-D = RGB image using the default direct color model"); 
	} 

	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about"))
			{showAbout(); return DONE;}
		if (arg.equals("show"))
			{showRates(); return DONE;}
		this.imp = imp;
		return DOES_8G+DOES_8C+DOES_RGB+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		width = ip.getWidth();
		increment = 3;
		if ((width%increment)==0) {
			increment = 5;
			if ((width%increment)==0) {
				increment = 7;
				if ((width%increment)==0) {
					IJ.error("Width cannot be a multiple of 3, 5 or 7");
					return;
				}
			}
		}
		height = ip.getHeight();
		if (ip.getPixels() instanceof byte[]) {
			pixels = (byte[])ip.getPixels();
			pixels2 = (byte[])ip.getPixelsCopy();
		} else {
			rgbPixels = (int[])ip.getPixels();
			rgbPixels2 = (int[])ip.getPixelsCopy();
		}
		waveTable = new int[width];
		for (int i=0; i<width; i++)
			waveTable[i] = (int)((Math.sin(i/frequency)+1.0)*amplitude);
		win = imp.getWindow();
		ic = win.getCanvas();
		imp.killRoi();
		g = ic.getGraphics();
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		startTime = System.currentTimeMillis();
		win.running = true;
       	while (win.running) {
			if (pixels!=null)
				updateByteImage();
			else
				updateRGBImage();
			imp.updateAndDraw();
         	showFrameRate();
        	Thread.yield();
		}
		ip.reset();
		imp.updateAndDraw();
	}
	
    void showFrameRate() {
       	frames++;
		elapsedTime = System.currentTimeMillis()-startTime;
		if (elapsedTime>0) {
			//double scale = ic.getMagnification();
			int cheight = ic.getSize().height;
			fps = (frames*10000)/elapsedTime;
			g.clearRect(0, cheight-15, 50, 15);
			g.drawString(fps/10 + "." + fps%10 + " fps", 2, cheight-2);
		}
	}

	void updateByteImage() {
		int i = index, inc = increment;
		int offset, x2;
		for (int y=height; --y>=0;) {
			offset = y*width;
			for (int x=width; --x>=0;) {
				i += inc;
				if (i>=width) i = 0;
				x2 = x+waveTable[i];
				if (x2>=width) x2 = width-1;
				pixels[offset+x] = (byte)pixels2[offset+x2];
			}
		}
		index = i;
	}

	void updateRGBImage() {
		int i = index, inc = increment;
		int offset, x2;
		for (int y=0; y<height; y++) {
			offset = y*width;
			for (int x=0; x<width; x++) {
				i += inc;
				if (i>=width) i = 0;
				x2 = x+waveTable[i];
				if (x2>=width) x2 = width-1;
				rgbPixels[offset+x] = rgbPixels2[offset+x2];
			}
		}
		index = i;
	}

}
