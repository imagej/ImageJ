package ij.gui;

import ij.macro.Interpreter;
import java.awt.*;
import java.awt.image.*;

/**
 * This is the progress bar that is displayed in the lower right hand corner of
 * the ImageJ window. Use one of the static IJ.showProgress() methods to display
 * and update the progress bar.
 */
public class ProgressBar extends Canvas {

    public static final int WIDTH = 120;
    public static final int HEIGHT = 20;

    private int canvasWidth, canvasHeight;
    private int x, y, width, height;
    private long lastTime = 0;
    private boolean showBar;
    private boolean batchMode;

    private Color barColor = Color.gray;
    private Color fillColor = new Color(204, 204, 255);
    private Color backgroundColor = ij.ImageJ.backgroundColor;
    private Color frameBrighter = backgroundColor.brighter();
    private Color frameDarker = backgroundColor.darker();
    private boolean dualDisplay = false;
    private double slowX = 0.0;//box
    private double fastX = 0.0;//dot

    /**
     * This constructor is called once by ImageJ at startup.
     */
    public ProgressBar(int canvasWidth, int canvasHeight) {
    	init(canvasWidth, canvasHeight);
	}
    
    public void init(int canvasWidth, int canvasHeight) {
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        x = 3;
        y = 5;
        width = canvasWidth - 8;
        height = canvasHeight - 7;
    }

    void fill3DRect(Graphics g, int x, int y, int width, int height) {
        g.setColor(fillColor);
        g.fillRect(x + 1, y + 1, width - 2, height - 2);
        g.setColor(frameDarker);
        g.drawLine(x, y, x, y + height);
        g.drawLine(x + 1, y, x + width - 1, y);
        g.setColor(frameBrighter);
        g.drawLine(x + 1, y + height, x + width, y + height);
        g.drawLine(x + width, y, x + width, y + height - 1);
    }

    /**
     * Updates the progress bar, where abs(progress) should run from 0 to 1.
     * If abs(<code>progress</code>) == 1 the bar is erased. The bar is updated only
     * if more than 90 ms have passed since the last call. Does nothing if the
     * ImageJ window is not present.
     * @param progress Length of the progress bar to display (0...1). 
     * Using <code>progress</code> with negative sign (0 .. -1) will regard subsequent calls with
     * positive argument as sub-ordinate processes that are displayed as moving dot.
     */
    public void show(double progress) {
        show(progress, false);
    }

    /**
     * Updates the progress bar, where abs(progress) should run from 0 to 1.
     * @param progress Length of the progress bar to display (0...1). 
     * @param showInBatchMode show progress bar in batch mode macros?
     */
    public void show(double progress, boolean showInBatchMode) {
        boolean finished = false;
        if (progress<=-1)
            finished = true;
        if (!dualDisplay && progress >= 1)
            finished = true;
        if (!finished) {
            if (progress < 0) {
                slowX = -progress;
                fastX = 0.0;
                dualDisplay = true;
            } else if (dualDisplay)
                fastX = progress;
            if (!dualDisplay)
                slowX = progress;
        }
        if (!showInBatchMode && (batchMode || Interpreter.isBatchMode()))
            return;
        if (finished) {//clear the progress bar
            slowX = 0.0;
            fastX = 0.0;
            showBar = false;
            dualDisplay = false;
            repaint();
            return;
        }
        long time = System.currentTimeMillis();
        if (time-lastTime<90 && progress!=1.0)
            return;
        lastTime = time;
        showBar = true;
        repaint();
    }

    /**
     * Updates the progress bar, where the length of the bar is set to
     * (<code>(abs(currentIndex)+1)/abs(finalIndex)</code> of the maximum bar
     * length. Use a negative <code>currentIndex</code> to show subsequent
     * plugin calls as moving dot. The bar is erased if
     * <code>currentIndex&gt;=finalIndex-1</code> or <code>finalIndex == 0</code>.
     */
    public void show(int currentIndex, int finalIndex) {
        boolean wasNegative = currentIndex < 0;
        double progress = ((double) Math.abs(currentIndex) + 1.0) / Math.abs(finalIndex);
        if (wasNegative)
            progress = -progress;
        if (finalIndex == 0)
            progress = -1;
        show(progress);
    }

    public void update(Graphics g) {
        paint(g);
    }

    public void paint(Graphics g) {
        if (showBar) {
            fill3DRect(g, x - 1, y - 1, width + 1, height + 1);
            drawBar(g);
        } else {
            g.setColor(backgroundColor);
            g.fillRect(0, 0, canvasWidth, canvasHeight);
        }
    }

    void drawBar(Graphics g) {
        int barEnd = (int) (width * slowX);
        if (Toolbar.getToolId()==Toolbar.ANGLE)
			g.setColor(Color.getHSBColor(((float)(System.currentTimeMillis()%1000))/1000, 0.5f, 1.0f));
		else
       		g.setColor(barColor);
        g.fillRect(x, y, barEnd, height);
        if (dualDisplay && fastX > 0) {
            int dotPos = (int) (width * fastX);
            g.setColor(Color.BLACK);
            if (dotPos > 1 && dotPos < width - 7)
                g.fillOval(dotPos, y + 3, 7, 7);
        }
    }

    public Dimension getPreferredSize() {
        return new Dimension(canvasWidth, canvasHeight);
    }

    public void setBatchMode(boolean batchMode) {
        this.batchMode = batchMode;
    }

}
