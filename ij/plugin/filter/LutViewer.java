package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.ResultsTable;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.ArrayList;

/** Displays the active image's look-up table. */
public class LutViewer implements PlugInFilter {
	private double guiScale = Prefs.getGuiScale();
	private ImagePlus imp;
	
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+NO_UNDO+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		if (ip.getNChannels()==3) {
			IJ.error("RGB images do not have LUTs.");
			return;
		}
		int xMargin = (int)(35*guiScale);
		int yMargin = (int)(20*guiScale);
		int width = (int)(256*guiScale);
		int height = (int)(128*guiScale);
		int x, y, x1, y1, x2, y2;
		int imageWidth, imageHeight;
		int barHeight = (int)(12*guiScale);
		boolean isGray;

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
		Graphics2D g = (Graphics2D)img.getGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(new Font("SansSerif",Font.PLAIN,(int)(12*guiScale)));
		g.setColor(Color.white);
		g.fillRect(-1, 0, imageWidth, imageHeight);
		g.setColor(Color.black);
		g.drawRect(xMargin, yMargin-1, width+1, height+1);

		if (isGray)
			g.setColor(Color.black);
		else
			g.setColor(Color.red);

		boolean drawSteps = width > mapSize;		//more than 1 pxl per color
		double xShift = drawSteps ? 0.5 : 0.0;		//when drawing steps, vertical lines are at half-integer positions
		x1 = scaledX(-0.5, xMargin, width, mapSize);	//for drawing steps, we need the x where the previous color would end
		y1 = yMargin + height - 1 - (int)Math.round(((reds[0]&0xff)-0.5)*guiScale/2);
		for (int i=0; i<mapSize; i++) {				// R E D   or   G R A Y S C A L E
			x2 = scaledX(i+xShift, xMargin, width, mapSize);
			y2 = yMargin + height - 1 - (int)Math.round(((reds[i]&0xff)-0.5)*guiScale/2);
			if (drawSteps) {
				if (i>0) g.drawLine(x1, y1, x1, y2);
				g.drawLine(x1, y2, x2, y2);
			} else if (i>0)
				g.drawLine(x1, y1, x2, y2);
			x1 = x2;
			y1 = y2;
		}

		if (!isGray) {								// G R E E N
			g.setColor(Color.green);
			x1 = scaledX(-0.5, xMargin, width, mapSize);
			y1 = yMargin + height - 1 - (int)Math.round(((greens[0]&0xff)-0.5)*guiScale/2);
			for (int i=0; i<mapSize; i++) {
			x2 = scaledX(i+xShift, xMargin, width, mapSize);
				y2 = yMargin + height - 1 - (int)Math.round(((greens[i]&0xff)-0.5)*guiScale/2);
				if (drawSteps) {
					if (i>0) g.drawLine(x1, y1, x1, y2);
					g.drawLine(x1, y2, x2, y2);
				} else if (i>0)
					g.drawLine(x1, y1, x2, y2);
				x1 = x2;
				y1 = y2;
			}

			g.setColor(Color.blue);					// B L U E
			x1 = scaledX(-0.5, xMargin, width, mapSize);
			y1 = yMargin + height - 1 - (int)Math.round(((blues[0]&0xff)-0.5)*guiScale/2);
			for (int i=0; i<mapSize; i++) {
				x2 = scaledX(i+xShift, xMargin, width, mapSize);
				y2 = yMargin + height - 1 - (int)Math.round(((blues[i]&0xff)-0.5)*guiScale/2);
				if (drawSteps) {
					if (i>0) g.drawLine(x1, y1, x1, y2);
					g.drawLine(x1, y2, x2, y2);
				} else if (i>0)
					g.drawLine(x1, y1, x2, y2);
				x1 = x2;
				y1 = y2;
			}
		}

		x = xMargin;
		y = yMargin + height + (int)Math.round(2*guiScale);
		lut.drawColorBar(g, x, y, width, barHeight);
		
		y += barHeight + (int)(15*guiScale);
		g.setColor(Color.black);
		g.drawString("0", x-(int)(4*guiScale), y);
		g.drawString(""+(mapSize-1), x+width - (int)(10*guiScale), y);
		g.drawString("255", (int)(7*guiScale), yMargin + (int)(4*guiScale));
		g.dispose();
		
        ImagePlus imp = new ImagePlus("Look-Up Table", img);
        new LutWindow(imp, new ImageCanvas(imp), ip);
    }

	private int scaledX(double x, int xMargin, int width, int mapSize) {
		return xMargin + (int)Math.round(0.5 + (x+0.5)*width*(1.0/mapSize));
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


