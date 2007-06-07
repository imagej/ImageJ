package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;

/**Binary Fill (Process/Binary/Fill) by Gabriel Landini, G.Landini at bham.ac.uk. */
public class BinaryFiller implements PlugInFilter {
	protected boolean backgroundIsZero;

	public int setup(String arg, ImagePlus imp) {
		if (imp==null)
			{IJ.noImage(); return DONE;}
		ImageStatistics stats=imp.getStatistics();
		if (stats.histogram[0]+stats.histogram[255]!=stats.pixelCount){
			IJ.error("8-bit binary image (0 and 255) required.");
			return DONE;
		}
		backgroundIsZero = Binary.blackBackground;
		if (imp.isInvertedLut()) 
			backgroundIsZero = !backgroundIsZero;			
		return IJ.setupDialog(imp, DOES_8G);
	}

	public void run(ImageProcessor ip) {
		int xe = ip.getWidth();
		int ye = ip.getHeight();
		int x, y;
		boolean b;
		int [][] pixel = new int [xe][ye];

		//original converted to white particles
		if (!backgroundIsZero)
			ip.invert();
			
		//get original
		for(y=0;y<ye;y++) {
			for(x=0;x<xe;x++)
				pixel[x][y]=ip.getPixel(x,y);
		}

		//label background borders
		for (y=0; y<ye; y++){
			if(ip.getPixel(0,y)==0)
				ip.putPixel(0,y,127);
			if(ip.getPixel(xe-1,y)==0)
				ip.putPixel(xe-1,y,127);
		}

		for (x=0; x<xe; x++){
			if(ip.getPixel(x,0)==0)
				ip.putPixel(x,0,127);
			if(ip.getPixel(x,ye-1)==0)
				ip.putPixel(x,ye-1,127);
		}

		//flood background from borders
		//the background of 8-connected particles is 4-connected
		b=true;
		while(b){
			b=false;
			for(y=1;y<ye-1;y++) {
				for(x=1;x<xe-1;x++) {
					if (ip.getPixel(x,y)==0){
						if(ip.getPixel(x,y-1)==127 || ip.getPixel(x-1,y)==127) {
							ip.putPixel(x,y,127);
							b=true;
						}
					}
				}
			}
			for(y=ye-2;y>=1;y--) {
				for(x=xe-2;x>=1;x--) {
					if (ip.getPixel(x,y)==0){
						if(ip.getPixel(x+1,y)==127 || ip.getPixel(x,y+1)==127) {
							ip.putPixel(x,y,127);
							b=true;
						}
					}
				}
			}
		}//idempotent

		for(y=0;y<ye;y++) {
			for(x=0;x<xe;x++){
				if(ip.getPixel(x,y)==0)
					ip.putPixel(x,y,255);
				else
					ip.putPixel(x,y,pixel[x][y]);
			}
		}

		//return to original state
		if (!backgroundIsZero)
			ip.invert();
	}

}
