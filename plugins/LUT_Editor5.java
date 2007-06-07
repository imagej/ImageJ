//package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;
import java.awt.*;
import java.awt.image.*;
import ij.util.*;
import java.util.Vector;
import java.awt.event.*;

public class LUT_Editor implements PlugIn, ActionListener{
    Vector colors;
    private ImagePlus imp;
    Button openButton, saveButton, resizeButton;
    ColorPanel colorPanel;

    public void run(String args) {
     	ImagePlus imp = WindowManager.getCurrentImage();
    	if (imp==null) {
    		IJ.showMessage("LUT Editor", "No images are open");
    		return;
    	}
    	if (imp.getBitDepth()==24) {
    		IJ.showMessage("LUT Editor", "RGB images do not use LUTs");
    		return;
    	}
        int red=0, green=0, blue=0;
        GenericDialog gd = new GenericDialog("LUT Editor Plus");
        colorPanel = new ColorPanel(imp);
        Panel buttonPanel = new Panel(new GridLayout(3, 1, 0, 5));
        openButton = new Button("Open...");
        openButton.addActionListener(this);
        buttonPanel.add(openButton);
        saveButton = new Button("Save...");
        saveButton.addActionListener(this);
        buttonPanel.add(saveButton);
        resizeButton = new Button("Resize...");
        resizeButton.addActionListener(this);
        buttonPanel.add(resizeButton);
        Panel panel = new Panel();
        panel.add(colorPanel);
        panel.add(buttonPanel);
        gd.addPanel(panel, GridBagConstraints.CENTER, new Insets(10, 0, 0, 0));
        gd.showDialog();
        if (gd.wasCanceled()){
            colorPanel.cancelLUT();
            return;
        } else
        colorPanel.applyLUT();
    }

    void save() {
        IJ.run("LUT...");
    }

    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source==openButton)
            colorPanel.open();
        else if (source==saveButton)
            save();
        else if (source==resizeButton)
            IJ.showMessage("Resize not implemented yet"); //resize();
    }
}

class ColorPanel extends Panel implements MouseListener, MouseMotionListener{
     static final int entryWidth=12, entryHeight=12;
     int rows = 16;
     int columns = 16; 
     Color c[] = new Color[256];
     Color b;
     ColorProcessor cp;
     LookUpTable origin;
     private ImagePlus imp;
     private int mapSize, x, y, initialC = -1, finalC = -1;
     private byte[] reds, greens, blues;
     private boolean updateLut;
     
     ColorPanel(ImagePlus imp) {
         setup(imp);
     }
     
     public void setup(ImagePlus imp) {
        if (imp==null) {
           IJ.noImage();
           return;
        }
        this.imp  =  imp;
        LookUpTable lut = imp.createLut();
        origin = imp.createLut();
        mapSize = lut.getMapSize();
        if (mapSize == 0)
             return;
        reds = lut.getReds();
        greens = lut.getGreens();
        blues = lut.getBlues();
        addMouseListener(this);
        addMouseMotionListener(this);
        for(int index  = 0; index < mapSize; index++)
            c[index] = new Color(reds[index]&255, greens[index]&255, blues[index]&255);
    }
    public Dimension getPreferredSize()  {
        return new Dimension(columns*entryWidth, rows*entryHeight);
    }
    public Dimension getMinimumSize() {
        return new Dimension(columns*entryWidth, rows*entryHeight);
    }
    int getMouseZone(int x, int y){
        int horizontal = (int)x/entryWidth;
        int vertical = (int)y/entryHeight;
        int index = (columns*vertical + horizontal);
        //IJ.showMessage("Index: " + index);
        return index;
    }
    public void colorRamp() {
        if (initialC>finalC) {
            int tmp = initialC;
            initialC = finalC;
            finalC = tmp;
        }
        float difference = finalC - initialC+1;
        int start = (byte)c[initialC].getRed()&255;
        int end = (byte)c[finalC].getRed()&255;
        float rstep = (end-start)/difference;
        for(int index =  initialC;  index <= finalC; index++)
            reds[index] = (byte)(start+ (index-initialC)*rstep);
        
        start = (byte)c[initialC].getGreen()&255;
        end = (byte)c[finalC].getGreen()&255;
        float gstep = (end-start)/difference;
            for(int index = initialC; index <= finalC; index++)
                greens[index] = (byte)(start + (index-initialC)*gstep);
        
        start = (byte)c[initialC].getBlue()&255;
        end = (byte)c[finalC].getBlue()&255;
        float bstep = (end-start)/difference;
        for(int index = initialC; index <= finalC; index++)
            blues[index] = (byte)(start + (index-initialC)*bstep);
        for (int index = initialC; index <= finalC; index++)
            c[index] = new Color(reds[index]&255, greens[index]&255, blues[index]&255);
        repaint();
    }

    public void mousePressed(MouseEvent e){
        x = (e.getX());
        y = (e.getY());
        initialC = getMouseZone(x,y);
    }

    public void mouseReleased(MouseEvent e){
        x = (e.getX());
        y = (e.getY());
        finalC =  getMouseZone(x,y);
        if(initialC>=mapSize&&finalC>=mapSize) {
    		initialC = finalC = -1;
    		return;
        }
        if(initialC>=mapSize)
            initialC = mapSize-1;
        if(finalC>=mapSize)
            finalC = mapSize-1;
        if(finalC<0)
            finalC = 0;
        //IJ.log("Initial Entry " + initialC + " Final Entry " + finalC);
        if (initialC == finalC) {
            repaint();
            b = c[finalC];
            ColorChooser cc = new ColorChooser("Color at Entry " + (finalC) , c[finalC] ,  false);
            c[finalC] = cc.getColor();
            if (c[finalC]==null){
                c[finalC] = b;
            }
            colorRamp();
        } else {
            b = c[initialC];
            ColorChooser icc = new ColorChooser("Initial Entry (" + (initialC)+")" , c[initialC] , false);
            c[initialC] = icc.getColor();
            if (c[initialC]==null){
                c[initialC] = b;
                initialC = finalC = -1;
                repaint();
                return;
            }
            b = c[finalC];
            ColorChooser fcc = new ColorChooser("Final Entry (" + (finalC)+")" , c[finalC] , false);
            c[finalC] = fcc.getColor();
            if (c[finalC]==null){
                c[finalC] = b;
                initialC = finalC = -1;
                repaint();
                return;
            }
            colorRamp();
        }
    initialC = finalC = -1;
    repaint();
    applyLUT();
    }

    public void mouseClicked(MouseEvent e){}
    public void mouseEntered(MouseEvent e){}
    public void mouseExited(MouseEvent e){}
    public void mouseDragged(MouseEvent e){
        x = (e.getX());
        y = (e.getY());
        finalC =  getMouseZone(x,y);
        repaint();
    }
    public void mouseMoved(MouseEvent e) {
        x = (e.getX());
        y = (e.getY());
        int entry = getMouseZone(x,y);
        int red = reds[entry]&255;
        int green = greens[entry]&255;
        int blue = blues[entry]&255;
        IJ.showStatus("index = " + entry + ", color = " + red + "," + green + "," + blue);
    }

    void open() {
        IJ.run("LUT... ");
        updateLut = true;
        repaint();
   }

    void updateLut() {
        ImagePlus imp = WindowManager.getCurrentImage();
        LookUpTable lut = imp.createLut();
        mapSize = lut.getMapSize();
        if (mapSize == 0)
             return;
        reds = lut.getReds();
        greens = lut.getGreens();
        blues = lut.getBlues();
        for(int index  = 0; index < mapSize; index++)
            c[index] = new Color(reds[index]&255, greens[index]&255, blues[index]&255);
   }

    public void cancelLUT() {
        if (mapSize == 0)
             return;
        reds = origin.getReds();
        greens = origin.getGreens();
        blues = origin.getBlues();
        ColorModel cm = new IndexColorModel(8, mapSize, reds, greens, blues);
        ImageProcessor ip = imp.getProcessor();
        ip.setColorModel(cm);
        if (imp.getStackSize()>1)
            imp.getStack().setColorModel(cm);
        imp.updateAndDraw();
    }

    public void applyLUT() {
        ColorModel cm = new IndexColorModel(8, mapSize, reds, greens, blues);
        ImageProcessor ip = imp.getProcessor();
        ip.setColorModel(cm);
        if (imp.getStackSize()>1)
            imp.getStack().setColorModel(cm);
        imp.updateAndDraw();
    }

    public void update(Graphics g) {
        paint(g);
    }

    public void paint(Graphics g) {
        if (updateLut) {
            updateLut();
            updateLut = false;
        }
        int x = 0, y = 0, r = 0, remain = mapSize;
        int w=columns*entryWidth, h=rows*entryHeight;
        int index = 0;
        for ( y=0; y<=(mapSize/columns); y++) {
            if(remain < (columns-1)){
                r = remain-1;
            } else {
                remain = remain - (columns-1);
                r = (columns-1);
            }
            for ( x=0; x<=r; x++) {
        if(index>(mapSize-1)) continue;
                if (((index <= finalC) && (index >= initialC)) || ((index >= finalC) && (index <=  initialC))){
                    g.setColor(c[index].brighter());
                    g.fillRect(x*entryWidth,  y*entryHeight, entryWidth, entryHeight);
                    g.setColor(Color.white);
                    g.drawRect((x*entryWidth), (y*entryHeight), entryWidth, entryHeight);
                    g.setColor(Color.black);
                    g.drawLine((x*entryWidth)+entryWidth-1, (y*entryHeight), (x*entryWidth)+entryWidth-1, (y*entryWidth)+entryHeight);
                    g.drawLine((x*entryWidth), (y*entryHeight)+entryHeight-1, (x*entryWidth)+entryWidth-1, (y*entryHeight)+entryHeight-1);
                    g.setColor(Color.white);
                } else {
                    g.setColor(c[index]);
                    g.fillRect(x*entryWidth,  y*entryHeight, entryWidth, entryHeight);
                    g.setColor(Color.white);
                    g.drawRect((x*entryWidth), (y*entryHeight), entryWidth-1, entryHeight-1);
                    g.setColor(Color.black);
                    g.drawLine((x*entryWidth), (y*entryHeight), (x*entryWidth)+entryWidth-1, (y*entryWidth));
                    g.drawLine((x*entryWidth), (y*entryHeight), (x*entryWidth), (y*entryHeight)+entryHeight-1); 
                }
                index++;
            }
        }
    }
}
