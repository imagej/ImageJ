import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import ij.plugin.frame.*;

/*  This plugin continuously generates and displays 640x480 images.
    It is plugin version of the "Plasma2" applet at "http://rsb.info.nih.gov/plasma2".
*/
public class Plasma_ extends PlugInFrame implements Runnable {

    boolean noDisplay = false;
    boolean synch = false;
    boolean showFPS = true;
    int width = 640;
    int height =480;
    int w,h,size;
    Image img;
    MemoryImageSource source;
    Thread runThread;
    long firstFrame, frames, fps, frames2, fps2;
    IndexColorModel icm;
    int[] waveTable;
    byte[][] paletteTable;
    byte[] pixels;
    boolean running = true;

    public Plasma_() {
        super("Plasma");
        WindowManager.addWindow(this);
        init();
        setSize(width, height);
        GUI.center(this);
        setVisible(true);
    }

    public void init() {
        w = width/4;
        h = height/4;
        pixels = new byte[width*height];
        size = (int) ((w+h)/2)*4;
        waveTable = new int[size];
        paletteTable = new byte[3][256];
        calculatePaletteTable();
        source=new MemoryImageSource(width, height, icm, pixels, 0, width);
        source.setAnimated(true);
        source.setFullBufferUpdates(true);
        img=createImage(source);
        setForeground(Color.white);
        setFont(new Font("SansSerif", Font.PLAIN, 14));
        start();
    }

    public void start() {
        if (runThread == null) {
            runThread=new Thread(this, "Plasma");
            runThread.start();
            firstFrame=System.currentTimeMillis();
            frames = 0;
            frames2 = 0;
        };
    }
    
    public void stop() {
        running = false;;
        runThread = null;
    }

    public void update(Graphics g) {
        paint(g);
    }

    public void paint(Graphics g) {
        source.newPixels();
        g.drawImage(img, 0, 0, null);
        if (showFPS) {
            frames++;
            if (System.currentTimeMillis()>firstFrame+4000) {
                firstFrame=System.currentTimeMillis();
                fps = frames;
                fps2 = frames2;
                frames = 0;
                frames2 = 0;
            }
            g.drawString((int)((fps+0.5)/4) + " fps ("+(int)((fps2+0.5)/4)+")", 10, 50);
        }
    }

    void calculateWaveTable() {
        for(int i=0;i<size;i++)
            waveTable[i]=(int)(32*(1+Math.sin(((double)i*2*Math.PI)/size)));
    }

    int FadeBetween(int start,int end,int proportion) {
        return ((end-start)*proportion)/128+start;
    }

    void calculatePaletteTable() {
        for(int i=0;i<128;i++) {
            paletteTable[0][i]=(byte)FadeBetween(0,255,i);
            paletteTable[1][i]=(byte)0;
            paletteTable[2][i]=(byte)FadeBetween(255,0,i);
        }
        for(int i=0;i<128;i++) {
            paletteTable[0][i+128]=(byte)FadeBetween(255,0,i);
            paletteTable[1][i+128]=(byte)0;
            paletteTable[2][i+128]=(byte)FadeBetween(0,255,i);
        }
        icm = new IndexColorModel(8, 256, paletteTable[0], paletteTable[1], paletteTable[2]);
    }

    public void run() {
        int x,y;
        int index, index2, index3, index4;
        int tempval;
        int spd1=2,spd2=5,spd3=1,spd4=4;
        int pos1=0,pos2=0,pos3=0,pos4=0;
        int tpos1,tpos2,tpos3,tpos4;
        int inc1=6,inc2=3,inc3=3,inc4=9;
        byte result;

        runThread.setPriority(Thread.MIN_PRIORITY);
        calculateWaveTable();
        while(running) {
            tpos1=pos1; tpos2=pos2;
            for(y=0; y<h; y++) {
                tpos3=pos3; tpos4=pos4;
                tpos1%=size; tpos2%=size;
                tempval=waveTable[tpos1] + waveTable[tpos2];
                index = y*width*4;
                index2 = index + width;
                index3 = index2 + width; 
                index4 = index3 + width; 
                for(x=0; x<w; x++) {
                    tpos3%=size; tpos4%=size;
                    result = (byte)(tempval + waveTable[tpos3] + waveTable[tpos4]);
                    pixels[index++]=result;
                    pixels[index++]=result;
                    pixels[index++]=result;
                    pixels[index++]=result;
                    pixels[index2++]=result;
                    pixels[index2++]=result;
                    pixels[index2++]=result;
                    pixels[index2++]=result;
                    pixels[index3++]=result;
                    pixels[index3++]=result;
                    pixels[index3++]=result;
                    pixels[index3++]=result;
                    pixels[index4++]=result;
                    pixels[index4++]=result;
                    pixels[index4++]=result;
                    pixels[index4++]=result;
                    tpos3+=inc3;  tpos4+=inc4;
                }
                index += w*3;
                tpos1+=inc1; tpos2+=inc2;
            }
            pos1+=spd1; pos2+=spd2; pos3+=spd3; pos4+=spd4;
            frames2++;
            if (noDisplay)
                showFPS();
            else {
                if (synch)
                    paint(getGraphics());
                else {
                    repaint();
                    Thread.yield();
                }
            }
        }
    }

    void showFPS() {
        frames++;
        if (System.currentTimeMillis()>firstFrame+4000) {
            firstFrame=System.currentTimeMillis();
            fps=frames;
            frames=0;
        }
        IJ.showStatus((int)((fps+0.5)/4) + " fps");
    }

    public void windowClosing(WindowEvent e) {
        if (e.getSource()!=this)
            return;
        stop();
        IJ.wait(500);
        setVisible(false);
        dispose();
        WindowManager.removeWindow(this);
   }

    //public void processWindowEvent(WindowEvent e) {
        //super.processWindowEvent(e);
    //    if (e.getID()==WindowEvent.WINDOW_CLOSING)
    //        stop(); 
    //}

}
