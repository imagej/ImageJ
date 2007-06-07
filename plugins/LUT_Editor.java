
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;
import java.awt.*;
import ij.util.*;
import java.util.Vector;
import java.awt.event.*;

 

public class LUT_Editor implements PlugIn{

    Vector colors;
    ColorPanel panel;

    public void run(String args) {
        int red=0, green=0, blue=0;
        GenericDialog gd = new GenericDialog("LUT Editor");
        panel = new ColorPanel();
        gd.addPanel(panel, GridBagConstraints.CENTER, new Insets(10, 0, 0, 0));
       gd.showDialog();
        if (gd.wasCanceled()) return;
    }
}

class ColorPanel extends Panel implements MouseListener, MouseMotionListener{
    static final int entryWidth=16, entryHeight=16;
    Color c[] = new Color[256];
    ImagePlus imp;
    int mapSize, x, y, initialC, finalC;
    byte[] reds, greens, blues;
    Graphics gee;

    ColorPanel() {
        setup(IJ.getImage());
    }

    public void setup(ImagePlus imp) {
       if (imp==null) {
          IJ.noImage();
          return;
       }
       this.imp = imp;
       LookUpTable lut = imp.createLut();
       mapSize = lut.getMapSize();
       if (mapSize == 0)
            return;
       reds = lut.getReds();
       greens = lut.getGreens();
       blues = lut.getBlues();
      addMouseListener(this);
      addMouseMotionListener(this);
       for(int index = 0; index <mapSize; index++)
          c[index] = new Color(reds[index]&255, greens[index]&255, blues[index]&255);
    }

    public Dimension getPreferredSize() {
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

    public void mousePressed(MouseEvent e){        
        x = (e.getX());
        y = (e.getY());
        initialC = getMouseZone(x,y);
        finalC = initialC;
        repaint();
    }

    public void mouseReleased(MouseEvent e){
        x = (e.getX());
        y = (e.getY());
        finalC = getMouseZone(x,y);
        if (initialC == finalC) {
            repaint();
        }
    }

    public void mouseClicked(MouseEvent e){}
    public void mouseEntered(MouseEvent e){}
    public void mouseExited(MouseEvent e){}
    public void mouseDragged(MouseEvent e){
        x = (e.getX());
        y = (e.getY());
        finalC = getMouseZone(x,y);
        repaint();
    }

    public void mouseMoved(MouseEvent e) {}

    public void paint(Graphics g) {
        int x = 0, y = 0;
        int w=32*entryWidth, h=8*entryHeight;
        int index = 0;
        for ( y=0; y<=7; y++) {
            for ( x=0; x<=31; x++) {
                g.setColor(c[index]);
                g.fillRect(x*entryWidth, y*entryHeight, entryWidth, entryHeight);
                if (((index <= finalC) && (index >= initialC)) || ((index >= finalC) && (index <= initialC))){
                    g.setColor(Color.black);
                } else { 
                    g.setColor(Color.white);
                }
                g.drawRect((x*entryWidth), (y*entryHeight), entryWidth-1, entryHeight-1);
                index++;
            }
        }
    }
}

