package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.awt.*;

/** This plugin implements ImageJ's Fill, Clear, Clear Outside and Draw commands. */
public class Filler implements PlugInFilter {
	
	String arg;
	Roi roi;
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		if (imp!=null)
			roi = imp.getRoi();			
		boolean isTextRoi = roi!=null && (roi instanceof TextRoi);
		IJ.register(Filler.class);
		int baseCapabilities = DOES_ALL+ROI_REQUIRED;
	 	if (arg.equals("clear")) {
	 		if (isTextRoi)
				return baseCapabilities;
			else
				return baseCapabilities+SUPPORTS_MASKING;
		} else if (arg.equals("draw")) {
	 		if (isTextRoi)
				return baseCapabilities+SUPPORTS_MASKING;
			else
				return baseCapabilities;
		} else if (arg.equals("outside"))
			return baseCapabilities;
		else
			return baseCapabilities+SUPPORTS_MASKING;
	}

	public void run(ImageProcessor ip) {
	 	if (arg.equals("clear"))
	 		clear(ip);
	 	else if (arg.equals("fill") || (arg.equals("draw")&&(roi instanceof TextRoi)))
	 		fill(ip);
	 	else if (arg.equals("draw"))
			draw(ip);
	 	else if (arg.equals("outside"))
	 		clearOutside(ip);
	}

	public void clear(ImageProcessor ip) {
	 	ip.setColor(Toolbar.getBackgroundColor());
		ip.fill();
		ip.setColor(Toolbar.getForegroundColor());
	}
		
	public void fill(ImageProcessor ip) {
		ip.setColor(Toolbar.getForegroundColor());
	 	ip.fill(); // fill with foreground color
	}
	 			 		
	public void draw(ImageProcessor ip) {
		ip.setColor(Toolbar.getForegroundColor());
		roi.drawPixels();
	}

	public synchronized void clearOutside(ImageProcessor ip) {
 		Rectangle r = ip.getRoi();
 		int[] mask = imp.getMask();
 		if (mask==null) {
 			mask = new int[r.width*r.height];
 			for (int i=0; i<mask.length; i++)
 				mask[i] = ip.BLACK;
 		}
 		// invert mask
 		for (int i=0; i<mask.length; i++)
 			if (mask[i]==ip.BLACK)
 				mask[i] = 0xFFFFFFFF;
 			else
 				mask[i] = ip.BLACK;
 		ip.setColor(Toolbar.getBackgroundColor());
		ip.fill();
 		ip.reset(mask);
		int width = ip.getWidth();
		int height = ip.getHeight();
 		ip.setRoi(0, 0, r.x, height);
 		ip.fill();
 		ip.setRoi(r.x, 0, r.width, r.y);
 		ip.fill();
 		ip.setRoi(r.x, r.y+r.height, r.width, height-(r.y+r.height));
 		ip.fill();
 		ip.setRoi(r.x+r.width, 0, width-(r.x+r.width), height);
 		ip.fill();
 		ip.setRoi(null);
		ip.setColor(Toolbar.getForegroundColor());
		Roi roi = imp.getRoi();
		imp.killRoi();
		imp.updateAndDraw();
		imp.setRoi(roi);
	}

}
