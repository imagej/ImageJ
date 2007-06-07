import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import java.awt.*;
import java.lang.String.*;
import ij.gui.*;

//Binary Fill by Gabriel Landini, G.Landini@bham.ac.uk

public class BinaryFill_ implements PlugInFilter {
	protected boolean doIwhite;

	public int setup(String arg, ImagePlus imp) {

		ImageStatistics stats;
		stats=imp.getStatistics();
		if (stats.histogram[0]+stats.histogram[255]!=stats.pixelCount){
			IJ.error("8-bit binary image (0 and 255) required.");
			return DONE;
		}

		if (arg.equals("about"))
			{showAbout(); return DONE;}
		GenericDialog gd = new GenericDialog("BinaryFill", IJ.getInstance());
		gd.addMessage("Binary Fill");
		gd.addCheckbox("White particles on black background",false);

		gd.showDialog();
		if (gd.wasCanceled())
			return DONE;

		doIwhite = gd.getNextBoolean ();
		return DOES_8G+DOES_STACKS;
	}

	public void run(ImageProcessor ip) {
		int xe = ip.getWidth();
		int ye = ip.getHeight();
		int x, y;
		boolean b;
		int [][] pixel = new int [xe][ye];

		//original converted to white particles
		if (doIwhite==false){
			for(y=0;y<ye;y++) {
				for(x=0;x<xe;x++)
					ip.putPixel(x,y,255-ip.getPixel(x,y));
			}
		}

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
		if (doIwhite==false){
			for(y=0;y<ye;y++) {
				for(x=0;x<xe;x++)
					ip.putPixel(x,y,255-ip.getPixel(x,y));
			}
		}
	}


	void showAbout() {
		IJ.showMessage("About BinaryFill_...",
		"BinaryFill_ by Gabriel Landini,  G.Landini@bham.ac.uk\n"+
		"ImageJ plugin for filling holes in 8-connected binary\n"+
		"particles.\n"+
		"Supports black particles on a white background and viceversa.");
	}

}
