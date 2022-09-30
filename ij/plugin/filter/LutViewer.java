package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
//import ij.text.*;
import ij.measure.ResultsTable;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.ArrayList;

/** Displays the active image's look-up table. */
public class LutViewer implements PlugInFilter {

	ImagePlus imp;
	
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+NO_UNDO+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		if (ip.getNChannels()==3) {
			IJ.error("RGB images do not have LUTs.");
			return;
		}
		int xMargin = 35;
		int yMargin = 20;
		int width = 256;
		int height = 128;
		int x, y, x1, y1, x2, y2;
		int imageWidth, imageHeight;
		int barHeight = 12;
		boolean isGray;
		double scale;

        ip = imp.getChannelProcessor();
        IndexColorModel cm = (IndexColorModel)ip.getColorModel();
        LookUpTable lut = new LookUpTable(cm);
		int mapSize = lut.getMapSize();
		byte[] reds = lut.getReds();
		byte[] greens = lut.getGreens();
		byte[] blues = lut.getBlues();
        isGray = lut.isGrayscale();

		imageWidth = width + 2*xMargin;
		imageHeight = height + 3*yMargin;
		Image img = IJ.getInstance().createImage(imageWidth, imageHeight);
		Graphics g = img.getGraphics();
		g.setColor(Color.white);
		g.fillRect(0, 0, imageWidth, imageHeight);
		g.setColor(Color.black);
		g.drawRect(xMargin, yMargin, width, height);

		scale = 256.0/mapSize;
		if (isGray)
			g.setColor(Color.black);
		else
			g.setColor(Color.red);
		x1 = xMargin;
		y1 = yMargin + height - (reds[0]&0xff)/2;
		for (int i = 1; i<256; i++) {
			x2 = xMargin + i;
			y2 = yMargin + height - (reds[(int)(i/scale)]&0xff)/2;
			g.drawLine(x1, y1, x2, y2);
			x1 = x2;
			y1 = y2;
		}

		if (!isGray) {
			g.setColor(Color.green);
			x1 = xMargin;
			y1 = yMargin + height - (greens[0]&0xff)/2;
			for (int i = 1; i<256; i++) {
				x2 = xMargin + i;
				y2 = yMargin + height - (greens[(int)(i/scale)]&0xff)/2;
				g.drawLine(x1, y1, x2, y2);
				x1 = x2;
				y1 = y2;
			}
		}

		if (!isGray) {
			g.setColor(Color.blue);
			x1 = xMargin;
			y1 = yMargin + height - (blues[0]&0xff)/2;
			for (int i = 1; i<255; i++) {
				x2 = xMargin + i;
				y2 = yMargin + height - (blues[(int)(i/scale)]&0xff)/2;
				g.drawLine(x1, y1, x2, y2);
				x1 = x2;
				y1 = y2;
			}
		}

		x = xMargin;
		y = yMargin + height + 2;
		lut.drawColorBar(g, x, y, 256, barHeight);
		
		y += barHeight + 15;
		g.setColor(Color.black);
		g.drawString("0", x - 4, y);
		g.drawString(""+(mapSize-1), x + width - 10, y);
		g.drawString("255", 7, yMargin + 4);
		g.dispose();
		
        ImagePlus imp = new ImagePlus("Look-Up Table", img);
        //imp.show();
        new LutWindow(imp, new ImageCanvas(imp), ip);
    }

} // LutViewer class

class LutWindow extends ImageWindow implements ActionListener {

	private Button button;
	private ImageProcessor ip;

	LutWindow(ImagePlus imp, ImageCanvas ic, ImageProcessor ip) {
		super(imp, ic);
		this.ip = ip;
		addPanel();
	}

	void addPanel() {
		Panel panel = new Panel();
		panel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		button = new Button(" List... ");
		button.addActionListener(this);
		panel.add(button);
		add(panel);
		pack();
	}
	
	public void actionPerformed(ActionEvent e) {
		Object b = e.getSource();
		if (b==button)
			list(ip);
	}

	void list(ImageProcessor ip) {
		IndexColorModel icm = (IndexColorModel)ip.getColorModel();
		int size = icm.getMapSize();
		byte[] r = new byte[size];
		byte[] g = new byte[size];
		byte[] b = new byte[size];
		icm.getReds(r); 
		icm.getGreens(g); 
		icm.getBlues(b);		
		ResultsTable rt = new ResultsTable();
		for (int i=0; i<size; i++) {
      		rt.setValue("Index", i, i);
      		rt.setValue("Red", i, r[i]&255);
      		rt.setValue("Green", i, g[i]&255);
      		rt.setValue("Blue", i, b[i]&255);
      	}
		rt.show("LUT");
	}

} // LutWindow class


