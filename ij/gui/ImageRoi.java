package ij.gui;
import java.awt.*;
import java.awt.image.*;//import ij.*;
import ij.process.*;

/** Image region of interest that can be added to 
	a display list to create an image overlay. */
public class ImageRoi extends Roi {
	ImageProcessor ip;
	BufferedImage bi;

	/** Creates a new ImageRoi from a BufferedImage.*/
	public ImageRoi(int x, int y, BufferedImage bi) {
		super(x, y, bi.getWidth(), bi.getHeight());
		this.bi = bi;
		setStrokeColor(Color.black);
	}

	/** Creates a new ImageRoi from a ImageProcessor.*/
	public ImageRoi(int x, int y, ImageProcessor ip) {
		super(x, y, ip.getWidth(), ip.getHeight());
		this.ip = ip;
		setStrokeColor(Color.black);
	}
		
	public void draw(Graphics g) {
		if (ic==null) return;
		double mag = ic.getMagnification();
		Image img = bi!=null?bi:ip.createImage();
		int sx2 = ic.screenX(x+width);
		int sy2 = ic.screenY(y+height);
		g.drawImage(img, ic.screenX(x), ic.screenY(y), sx2, sy2, 0, 0, ip.getWidth(), ip.getHeight(), null);
 	}

}
