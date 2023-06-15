package ij.gui;
import java.awt.*;
import ij.*;

/** This class is used by GenericDialog to add images to dialogs. */
public class ImagePanel extends Panel {
	private ImagePlus img;
	 
	public ImagePanel(ImagePlus img) {
		this.img = img;
	}

	public Dimension getPreferredSize() {
		return new Dimension(img.getWidth(), img.getHeight());
	}

	public Dimension getMinimumSize() {
		return new Dimension(img.getWidth(), img.getHeight());
	}

	public void paint(Graphics g) {
		g.drawImage(img.getProcessor().createImage(), 0, 0, null);
	}

}
