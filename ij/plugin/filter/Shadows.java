package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.awt.*;

/** Implements the commands in the Process/Shadows submenu. */
public class Shadows implements PlugInFilter {
	
	String arg;
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		if (imp!=null && imp.getStackSize()>1 && arg.equals("demo")) {
			IJ.error("Shadows Demo does not work with stacks.");
			return DONE;
		}
		return IJ.setupDialog(imp, DOES_ALL+SUPPORTS_MASKING);
	}

	public void run(ImageProcessor ip) {
		if (arg.equals("demo")) {
			int iterations = 20;
			IJ.resetEscape();
			for (int i=0; i<iterations; i++) {
				north(ip); update(imp,ip,i,iterations);
				northeast(ip); update(imp,ip,i,iterations);
				east(ip); update(imp,ip,i,iterations);
				southeast(ip); update(imp,ip,i,iterations);
				south(ip); update(imp,ip,i,iterations);
				southwest(ip); update(imp,ip,i,iterations);
				west(ip); update(imp,ip,i,iterations);
				northwest(ip); update(imp,ip,i,iterations);
				if (IJ.escapePressed()) {
					IJ.beep();
					break;
				};
			}
		}
		else if (arg.equals("north")) north(ip);
		else if (arg.equals("northeast")) northeast(ip);
		else if (arg.equals("east")) east(ip);
		else if (arg.equals("southeast")) southeast(ip);
		else if (arg.equals("south")) south(ip);
		else if (arg.equals("southwest")) southwest(ip);
		else if (arg.equals("west")) west(ip);
		else if (arg.equals("northwest")) northwest(ip);
	}
	
	private static void update(ImagePlus imp, ImageProcessor ip, int i, int iterations) {
		imp.updateAndDraw();
		IJ.showStatus(i+"/"+iterations);
		IJ.wait(50);
		ip.reset();
	}
		
		
	public void north(ImageProcessor ip) {
		int[] kernel = {1,2,1, 0,1,0,  -1,-2,-1};
		ip.convolve3x3(kernel);
	}

	public void south(ImageProcessor ip) {
		int[] kernel = {-1,-2,-1,  0,1,0,  1,2,1};
		ip.convolve3x3(kernel);
	}

	public void east(ImageProcessor ip) {
		int[] kernel = {-1,0,1,  -2,1,2,  -1,0,1};
		ip.convolve3x3(kernel);
	}

	public void west(ImageProcessor ip) {
		int[] kernel = {1,0,-1,  2,1,-2,  1,0,-1};
		ip.convolve3x3(kernel);
	}

	public void northwest(ImageProcessor ip) {
		int[] kernel = {2,1,0,  1,1,-1,  0,-1,-2};
		ip.convolve3x3(kernel);
	}

	public void southeast(ImageProcessor ip) {
		int[] kernel = {-2,-1,0,  -1,1,1,  0,1,2};
		ip.convolve3x3(kernel);
	}
	
	public void northeast(ImageProcessor ip) {
		int[] kernel = {0,1,2,  -1,1,1,  -2,-1,0};
		ip.convolve3x3(kernel);
	}
	
	public void southwest(ImageProcessor ip) {
		int[] kernel = {0,-1,-2,  1,1,-1,  2,1,0};
		ip.convolve3x3(kernel);
	}
}
