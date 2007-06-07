import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;
import java.awt.*;
import java.awt.image.*;
import ij.util.*;
import java.util.Vector;
import java.awt.event.*;

public class LUT_Editor implements PlugIn{
    Vector colors;
    ColorPanel panel;
    ButtonPanel button;
    
    public void run(String args) {
        int red=0, green=0, blue=0;
        GenericDialog gd = new GenericDialog("LUT Editor");
        panel = new ColorPanel();
        gd.addPanel(panel, GridBagConstraints.CENTER, new Insets(10, 0, 0, 0));
        button = new ButtonPanel();
        gd.addPanel(button, GridBagConstraints.CENTER, new Insets(10, 0, 0, 0));
        gd.showDialog();
        if (gd.wasCanceled()) return;
        else
        panel.applyLUT();
    }
}

class ColorPanel extends Panel implements MouseListener, MouseMotionListener {
     static final int entryWidth=16, entryHeight=16;
     Color c[] = new Color[256];
     ColorProcessor cp;
     private ImagePlus imp;
     private int mapSize, x, y, initialC = -1, finalC = -1;
     private byte[] reds, greens, blues;
     
     ColorPanel() {
         setup(IJ.getImage());
     }
     
     public void setup(ImagePlus imp) {
        if (imp==null) {
           IJ.noImage();
           return;
        }
        this.imp  =  imp;
        LookUpTable lut = imp.createLut();
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
        return new Dimension(32*entryWidth, 8*entryHeight);
    }
    public Dimension getMinimumSize() {
        return new Dimension(32*entryWidth, 8*entryHeight);
    }
    int getMouseZone(int x, int y){
        int horizontal = (int)x/16;
        int vertical = (int)y/16;
        int index = (32*vertical + horizontal);
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
        //repaint();
    }

    public void mouseReleased(MouseEvent e){
        x = (e.getX());
        y = (e.getY());
        finalC = getMouseZone(x,y);
        if (initialC == finalC) {
            repaint();
            ColorChooser cc = new ColorChooser("Color at Entry #" + (finalC+1) , c[finalC] ,  false);
            c[finalC] = cc.getColor();
            if (c[finalC]==null)
                return;
            colorRamp();
        } else {
            ColorChooser icc = new ColorChooser("Initial Color at Entry #" + (initialC+1) , c[initialC] , false);
            c[initialC] = icc.getColor();
            if (c[initialC]==null)
                return;
            ColorChooser fcc = new ColorChooser("Final Color at Entry #" + (finalC+1) , c[finalC] , false);
            c[finalC] = fcc.getColor();
            colorRamp();
        }
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
    public void mouseMoved(MouseEvent e) {}

    public void applyLUT() {
       ColorModel cm = new IndexColorModel(8, mapSize, reds, greens, blues);
        ImageProcessor ip = imp.getProcessor();
        ip.setColorModel(cm);
        if (imp.getStackSize()>1)
            imp.getStack().setColorModel(cm);
        imp.updateAndDraw();
    }

// Remainder from Y (mapSize-32*(mapSize/32-1)-1)

    public void paint(Graphics g) {
        int x = 0, y = 0, r = 0, remain = mapSize;
        int w=32*entryWidth, h=8*entryHeight;
        int index = 0;
        for ( y=0; y<=(mapSize/32); y++) {
            if(remain < 31){
                r = remain-1;
            } else {
                remain = remain - 31;
                r = 31;
            }
            for ( x=0; x<=r; x++) {
        if(index>255) continue;
                g.setColor(c[index]);
                g.fillRect(x*entryWidth,  y*entryHeight, entryWidth, entryHeight);
                if (((index <= finalC) && (index >= initialC)) || ((index >= finalC) && (index <=  initialC))){
                    g.setColor(Color.white);
                    g.drawRect((x*entryWidth), (y*entryHeight), entryWidth, entryHeight);
                    g.setColor(Color.black);
                    g.drawLine((x*entryWidth)+entryWidth-1, (y*entryHeight), (x*entryWidth)+entryWidth-1, (y*entryWidth)+entryHeight);
                    g.drawLine((x*entryWidth), (y*entryHeight)+entryHeight-1, (x*entryWidth)+entryWidth-1, (y*entryHeight)+entryHeight-1);
                    g.setColor(Color.white);
                } else {
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

class ButtonPanel extends Panel implements ActionListener {

    private Button open, save;
    private ImagePlus imp;

    Panel makeButtonPanel(GenericDialog gd) {
        Panel buttons = new Panel();
        buttons.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
        open = new Button("Open...");
        open.addActionListener(this);
        buttons.add(open);
        save = new Button("Save...");
        save.addActionListener(this);
        buttons.add(save);
        return buttons;
    }

    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source==save)
            save();
        else if (source==open)
            open();
    }

    void save() {
    }
    
    void open() {
    }

     public void paint(Graphics g) {
     }
}
