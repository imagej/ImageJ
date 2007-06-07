package ij.gui;

import java.awt.*;
import java.awt.image.*;

/** This is ImageJ's progress bar. It is not displayed if
	the time between the first and second calls to 'show'
	is less than 50 milliseconds. It is erased when show
	is passed a percent value >= 1.0. */
public class ProgressBar extends Canvas {

	private int canvasWidth, canvasHeight;
	private int x, y, width, height;
	private double percent;
	private long startTime;
	private int count;
	private boolean showBar;
	private boolean negativeProgress;
	private static boolean autoHide;
	
	private Color barColor = Color.gray;
	private Color fillColor = new Color(204,204,255);
	private Color backgroundColor = Color.lightGray;
	private Color frameBrighter = backgroundColor.brighter();
	private Color frameDarker = backgroundColor.darker();

	public ProgressBar(int canvasWidth, int canvasHeight) {
		this.canvasWidth = canvasWidth;
		this.canvasHeight = canvasHeight;
		x = 3;
		y = 5;
		width = canvasWidth - 8;
		height = canvasHeight - 7;
		showBar = false;
		negativeProgress = false;
		count = 0;
		percent = 0.0;
	}
		
    void fill3DRect(Graphics g, int x, int y, int width, int height) {
		g.setColor(fillColor);
		g.fillRect(x+1, y+1, width-2, height-2);
		g.setColor(frameDarker);
		g.drawLine(x, y, x, y+height);
		g.drawLine(x+1, y, x+width-1, y);
		g.setColor(frameBrighter);
		g.drawLine(x+1, y+height, x+width, y+height);
		g.drawLine(x+width, y, x+width, y+height-1);
    }    

	public void show(double percent) {
		count++;
    	if (count==1) {
    		startTime = System.currentTimeMillis();
    		showBar = false;
    	}
		else if (count==2) {
			long time2 = System.currentTimeMillis();
			//if (IJ.debugMode) IJ.write("Progress: " + (time2 - startTime) + "ms");
			if ((time2 - startTime)>=50)
				showBar = true;
		}
		
		negativeProgress = percent<this.percent;
		this.percent = percent;
    	if (percent>=1.0) {
			count = 0;
			percent = 0.0;
			showBar = false;
			repaint();
    	} else if (showBar)
    		repaint();
	}

	public void update(Graphics g) {
		paint(g);
	}

    public void paint(Graphics g) {
    	if (showBar) {
			fill3DRect(g, x-1, y-1, width+1, height+1);
			drawBar(g);
		} else {
			g.setColor(backgroundColor);
			g.fillRect(0, 0, canvasWidth, canvasHeight);
		}
    }

    void drawBar(Graphics g) {
    	if (percent<0.0)
    		percent = 0.0;
    	int barEnd = (int)(width*percent);
		if (negativeProgress) {
			g.setColor(fillColor);
			g.fillRect(barEnd+2, y, width-barEnd, height);
		} else {
			g.setColor(barColor);
			g.fillRect(x, y, barEnd, height);
		}
    }
    
    public Dimension getPreferredSize() {
        return new Dimension(canvasWidth, canvasHeight);
    }

}