package ij.gui;
import java.awt.*;
import java.awt.image.*;
import ij.process.*;

	/** An ImageRoi is an Roi that contains an image. An ImageRoi
	 * can be added to a display list to create an image overlay. 
	 * @see ij.ImagePlus#setDisplayList(Vector)
	 */
public class ImageRoi extends Roi {
	private ImageProcessor ip;
	private BufferedImage bi;
	private Composite composite;

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
		Graphics2D g2d = (Graphics2D)g;						
		double mag = ic.getMagnification();
		Image img = bi!=null?bi:ip.createImage();
		int sx2 = ic.screenX(x+width);
		int sy2 = ic.screenY(y+height);
		Composite saveComposite = null;
		if (composite!=null) {
			saveComposite = g2d.getComposite();
			g2d.setComposite(composite);
		}
		g.drawImage(img, ic.screenX(x), ic.screenY(y), sx2, sy2, 0, 0, img.getWidth(null), img.getHeight(null), null);
		if (composite!=null) g2d.setComposite(saveComposite);
 	}
 	
	/** Sets the composite mode. */
	public void setComposite(Composite composite) {
		this.composite = composite;
	}
	
	/** Sets the composite mode using the specified opacity (alpha), in the 
	     range 0.0-1.0, where 0.0 is fully transparent and 1.0 is fully opaque. */
	public void setOpacity(double opacity) {
		if (opacity<0.0) opacity = 0.0;
		if (opacity>1.0) opacity = 1.0;
		if (opacity!=1.0)
			composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float)opacity);
		else
			composite = null;
	}

}