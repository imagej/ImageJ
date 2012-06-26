package ij.gui;
import ij.ImagePlus;
import ij.process.*;
import ij.io.FileSaver;
import java.awt.*;
import java.awt.image.*;

/** An ImageRoi is an Roi that displays an image as an overlay. 
* @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
*/
public class ImageRoi extends Roi {
	private Image img;
	private Composite composite;
	private double opacity = 1.0;
	private double angle = 0.0;
	private boolean zeroTransparent;

	/** Creates a new ImageRoi from a BufferedImage.*/
	public ImageRoi(int x, int y, BufferedImage bi) {
		super(x, y, bi.getWidth(), bi.getHeight());
		img = bi;
		setStrokeColor(Color.black);
	}

	/** Creates a new ImageRoi from a ImageProcessor.*/
	public ImageRoi(int x, int y, ImageProcessor ip) {
		super(x, y, ip.getWidth(), ip.getHeight());
		img = ip.createImage();
		setStrokeColor(Color.black);
	}
		
	public void draw(Graphics g) {
		Graphics2D g2d = (Graphics2D)g;						
		double mag = getMagnification();
		int sx2 = screenX(x+width);
		int sy2 = screenY(y+height);
		Composite saveComposite = null;
		if (composite!=null) {
			saveComposite = g2d.getComposite();
			g2d.setComposite(composite);
		}
		Image img2 = img;
		if (angle!=0.0) {
			ImageProcessor ip = new ColorProcessor(img);
			ip.setInterpolate(true);
			ip.setBackgroundValue(0.0);
			ip.rotate(angle);
			if (zeroTransparent)
				ip = makeZeroTransparent(ip, true);
			img2 = ip.createImage();
		}
		g.drawImage(img2, screenX(x), screenY(y), sx2, sy2, 0, 0, img.getWidth(null), img.getHeight(null), null);
		if (composite!=null) g2d.setComposite(saveComposite);
		if (isActiveOverlayRoi() && !overlay)
			super.draw(g);
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
		this.opacity = opacity;
		if (opacity!=1.0)
			composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float)opacity);
		else
			composite = null;
	}
	
	/** Returns a serialized version of the image. */
	public byte[] getSerializedImage() {
		ImagePlus imp = new ImagePlus("",img);
		return new FileSaver(imp).serialize();
	}

	/** Returns the current opacity. */
	public double getOpacity() {
		return opacity;
	}

	public void rotate(double angle) {
		this.angle += angle;
	}

	public void setAngle(double angle) {
		this.angle = angle;
	}

	public void setZeroTransparent(boolean zeroTransparent) {
		if (this.zeroTransparent!=zeroTransparent) {
			ImageProcessor ip = makeZeroTransparent(new ColorProcessor(img), zeroTransparent);
			img = ip.createImage();
		}
		this.zeroTransparent = zeroTransparent;
	}
	
	private ImageProcessor makeZeroTransparent(ImageProcessor ip, boolean transparent) {
		if (transparent) {
			ip.setColorModel(new DirectColorModel(32,0x00ff0000,0x0000ff00,0x000000ff,0xff000000));
			for (int x=0; x<width; x++) {
				for (int y=0; y<height; y++) {
					double v = ip.getPixelValue(x, y);
					if (v>1)
						ip.set(x, y, ip.get(x,y)|0xff000000); // set alpha bits
					else
						ip.set(x, y, ip.get(x,y)&0xffffff); // clear alpha bits
				}
			}
		}
		return ip;
	}

	public synchronized Object clone() {
		ImagePlus imp = new ImagePlus("", img);
		ImageRoi roi2 = new ImageRoi(x, y, imp.getProcessor());
		roi2.setOpacity(getOpacity());
		return roi2;
	}

	//public void setImage(ImagePlus imp) {
	//	ij.IJ.log("draw "+ic+"  "+img);
	//	super.setImage(imp);
	//	if (imp==null) img = null;
	//}

}