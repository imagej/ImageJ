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
	int sliceCount;
	int[] mask;

	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		if (imp!=null)
			roi = imp.getRoi();			
		boolean isTextRoi = roi!=null && (roi instanceof TextRoi);
		IJ.register(Filler.class);
		int baseCapabilities = DOES_ALL+ROI_REQUIRED;
	 	if (arg.equals("clear")) {
	 		if (isTextRoi || isLineSelection())
				return baseCapabilities;
			else
				return IJ.setupDialog(imp,baseCapabilities+SUPPORTS_MASKING);
		} else if (arg.equals("draw")) {
	 		if (isTextRoi)
				return baseCapabilities+SUPPORTS_MASKING;
			else
				return baseCapabilities;
		} else if (arg.equals("outside")) {
				return IJ.setupDialog(imp,baseCapabilities);
		} else
			return IJ.setupDialog(imp,baseCapabilities+SUPPORTS_MASKING);
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

	boolean isLineSelection() {
		return roi!=null && roi.getType()>=Roi.LINE && roi.getType()<=Roi.FREELINE;
	}
	
	public void clear(ImageProcessor ip) {
	 	ip.setColor(Toolbar.getBackgroundColor());
		if (isLineSelection())
			roi.drawPixels();
		else
	 		ip.fill(); // fill with background color
		ip.setColor(Toolbar.getForegroundColor());
	}
		
	public void fill(ImageProcessor ip) {
		ip.setColor(Toolbar.getForegroundColor());
		if (isLineSelection())
			roi.drawPixels();
		else
	 		ip.fill(); // fill with foreground color
	}
	 			 		
	public void draw(ImageProcessor ip) {
		ip.setColor(Toolbar.getForegroundColor());
		roi.drawPixels();
	}

	public synchronized void clearOutside(ImageProcessor ip) {
		if (isLineSelection()) {
			IJ.error("\"Clear Outside\" does not work with line selections.");
			return;
		}
 		sliceCount++;
 		Rectangle r = ip.getRoi();
 		if (mask==null)
 			makeMask(ip, r);
  		ip.setColor(Toolbar.getBackgroundColor());
 		int stackSize = imp.getStackSize();
 		if (stackSize>1)
 			ip.snapshot();
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
 		if (sliceCount==stackSize) {
			ip.setColor(Toolbar.getForegroundColor());
			Roi roi = imp.getRoi();
			imp.killRoi();
			imp.updateAndDraw();
			imp.setRoi(roi);
		}
	}

	public void makeMask(ImageProcessor ip, Rectangle r) {
 		mask = imp.getMask();
 		if (mask==null) {
 			mask = new int[r.width*r.height];
 			for (int i=0; i<mask.length; i++)
 				mask[i] = ImageProcessor.BLACK;
 		} else {
 			// duplicate mask (needed because getMask caches masks)
 			int[] mask2 = new int[mask.length];
 			for (int i=0; i<mask.length; i++)
 				mask2[i] = mask[i];
 			mask = mask2;
 		}
  		// invert mask
 		for (int i=0; i<mask.length; i++) {
 			if (mask[i]==ImageProcessor.BLACK)
 				mask[i] = 0xFFFFFFFF;
 			else
 				mask[i] = ImageProcessor.BLACK;
 		}
 	}
}
