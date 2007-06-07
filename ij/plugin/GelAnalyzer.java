package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;

/** This plugin generates gel profile plots that can be analyzed using
the wand tool. It is similar to the "Gel Plotting Macros" in NIH Image. */
public class GelAnalyzer implements PlugIn {

    static int saveID;
    static int nLanes = 0;
    static Rectangle firstRect;
    static final int MAX_LANES = 100;
    static int[] x = new int[MAX_LANES+1];
    static PlotsCanvas plotsCanvas;
    static boolean uncalibratedOD = true;
    static boolean labelWithPercentages = true;
    static boolean outlineLanes = true;
    static ImageProcessor ipCopy;
    boolean invertedLut;
    ImagePlus imp;

    Font f;
    FontMetrics fm;

    static boolean isVertical;

    public void run(String arg) {
        imp = WindowManager.getCurrentImage();
        if (imp==null) {
            IJ.noImage();
            return;
        }
        //IJ.write(""+isVertical);
        if (arg.equals("reset")) {
            nLanes=0;
            saveID=imp.getID();
            if (plotsCanvas!=null)
                plotsCanvas.reset();
            if (ipCopy!=null && imp.getID()==saveID)
                imp.setProcessor(null, ipCopy);
            ipCopy = null;
            return;
        }

        if (arg.equals("percent") && plotsCanvas!=null) {
            plotsCanvas.displayPercentages();
            return;
        }

        if (arg.equals("label") && plotsCanvas!=null) {
            if (plotsCanvas.counter==0)
                show("There are no peak area measurements.");
            else
                plotsCanvas.labelPeaks();
            return;
        }

        if (arg.equals("options")) {
            GenericDialog gd = new GenericDialog("Gel Analyzer Options...");
            gd.addCheckbox("Uncalibrated OD", uncalibratedOD);
            gd.addCheckbox("Label with Percentages", labelWithPercentages);
            //gd.addCheckbox("Outline Lanes", outlineLanes);
            gd.showDialog();
            if (gd.wasCanceled())
                return;
            uncalibratedOD = gd.getNextBoolean();
            labelWithPercentages = gd.getNextBoolean();
            //outlineLanes = gd.getNextBoolean();
            return;
        }

        if (imp.getID()!=saveID) {
            nLanes=0;
            ipCopy=null;
            saveID=imp.getID();
        }

        Roi roi = imp.getRoi();
        if (roi!=null && arg.equals("perimeter")) {
            IJ.write("Perimeter: "+roi.getLength());
            return;
        }
        if (imp.getType()!=ImagePlus.GRAY8 && uncalibratedOD) {
            show("The \"Uncalibrated OD\" option requires an 8-bit grayscale image.");
            return;
        }
        if (roi==null || roi.getType()!=Roi.RECTANGLE) {
            show("Rectangular selection required.");
            return;
        }
        invertedLut = imp.isInvertedLut();
        Rectangle rect = roi.getBoundingRect();
        if (nLanes==0)
            IJ.register(GelAnalyzer.class);  // keeps this class from being GC'd

        if (arg.equals("first")) {
            selectFirstLane(rect);
            return;
        }
        if (nLanes==0) {
            show("You must first use the \"Outline First Lane\" command.");
            return;
        }
        if (arg.equals("next")) {
            selectNextLane(rect);
            return;
        }
        if (arg.equals("plot")) {

            if (( isVertical && (rect.x!=x[nLanes]) ) || ( !(isVertical) && (rect.y!=x[nLanes]) )) {
                selectNextLane(rect);
            }
            plotLanes(imp);
            return;
        }

    }

    void selectFirstLane(Rectangle rect) {
        if (rect.height<=rect.width)
            isVertical = false;
        else
            isVertical = true;
            
        if ( (isVertical && (rect.height/rect.width)<2 ) || (!isVertical && (rect.width/rect.height)<2 ) ) {
            GenericDialog gd = new GenericDialog("Lane Orientation");
            String[] orientations = {"Vertical","Horizontal"};
            int defaultOrientation = isVertical?0:1;
            gd.addChoice("Lane Orientation:", orientations, orientations[defaultOrientation]);
            gd.showDialog();
            if (gd.wasCanceled())
                return;
            String orientation = gd.getNextChoice();
            if(orientation.equals(orientations[0]))
                isVertical=true;
            else
                isVertical=false;
        }

        IJ.showStatus("Lane 1 selected");
        if (nLanes!=0 && ipCopy!=null)
            imp.setProcessor(null, ipCopy);
        firstRect = rect;
        nLanes = 1;
        if(isVertical)
            x[1] = rect.x;
        else
            x[1] = rect.y;
        outlineLane(x[1]);
    }

    void selectNextLane(Rectangle rect) {
        if (rect.width!=firstRect.width || rect.height!=firstRect.height) {
            show("Selections must all be the same size.");
            return;
        }
        if (nLanes<MAX_LANES)
            nLanes += 1;
        IJ.showStatus("Lane " + nLanes + " selected");

        if(isVertical)
            x[nLanes] = rect.x;
        else
            x[nLanes] = rect.y;
        outlineLane(x[nLanes]);
    }

    void outlineLane(int x) {
        if (!outlineLanes)
            return;
        //IJ.write("outlining lane "+x);
        ImageProcessor ip = imp.getProcessor();
        int lineWidth = (int)(1.0/imp.getWindow().getCanvas().getMagnification());
        if (lineWidth<1)
            lineWidth = 1;
        ip.setLineWidth(lineWidth);
        if (nLanes==1) {
            f = new Font("Helvetica", Font.PLAIN, 12*lineWidth);
            //fm = new FontMetrics(f);
            ipCopy = ip.duplicate();
            ip.setColor(Toolbar.getForegroundColor());
            ip.setFont(f);
        }
        if(isVertical)
            ip.drawRect(x, firstRect.y, firstRect.width, firstRect.height);
        else
            ip.drawRect(firstRect.x, x, firstRect.width, firstRect.height);
        String s = ""+nLanes;
        if(isVertical)
            ip.drawString(s, x+firstRect.width/2-ip.getStringWidth(s)/2,firstRect.y );
        else 
            ip.drawString(s, firstRect.x-ip.getStringWidth(s)-2, x+firstRect.height/2+6);
        imp.updateAndDraw();
    }

    double od(double v) {
        if (invertedLut) {
            if (v==255.0)
                v = 254.5;
            return 0.434294481*Math.log(255.0/(255.0-v));
        } else {
            if (v==0.0)
                v = 0.5;
            return 0.434294481*Math.log(255.0/v);
        }
    }

    void plotLanes(ImagePlus imp) {
        if (ipCopy!=null) {
            ImageProcessor outlinedLanes = imp.getProcessor();
            imp.setProcessor(null, ipCopy);
            ipCopy = null;
            ImagePlus lanes = new ImagePlus("Lanes of "+imp.getShortTitle(), outlinedLanes);
            lanes.changes = true;
            lanes.show();
        }
        Calibration cal = imp.getCalibration();
        if (uncalibratedOD)
            cal.setFunction(Calibration.UNCALIBRATED_OD, null, "Uncalibrated OD");
        else if (cal.getFunction()==Calibration.UNCALIBRATED_OD)
            cal.setFunction(Calibration.NONE, null, "Gray Value");
        int topMargin = 16;
        int bottomMargin = 2;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        int plotWidth, plotHeight;
        double[][] profiles;
        profiles = new double[MAX_LANES+1][];
        IJ.showStatus("Plotting " + nLanes + " lanes");
        ImageProcessor ipRotated = imp.getProcessor();
        if(isVertical) {
            ipRotated = ipRotated.rotateLeft();
        }
        ImagePlus imp2 = new ImagePlus("", ipRotated);
        //imp2.show();


        for (int i=1; i<=nLanes; i++) {
            if(isVertical)
                imp2.setRoi(firstRect.y,
                            ipRotated.getHeight() - x[i] - firstRect.width,
                            firstRect.height, firstRect.width);
            else
                imp2.setRoi(firstRect.x, x[i], firstRect.width, firstRect.height);


            //imp2.setRoi(ipRotated.getWidth() - firstRect.y-firstRect.height,
            //                    x[i],
            //                    firstRect.height, firstRect.width);


            //ipRotated.setRoi(ipRotated.getWidth() - firstRect.y - firstRect.height,
            //                    x[i],
            //                    firstRect.height, firstRect.width);
            //new ImagePlus("", ipRotated.duplicate()).show();
            //new ImagePlus("", ipRotated.crop()).show();
            ProfilePlot pp = new ProfilePlot(imp2);
            profiles[i] = pp.getProfile();
            if (pp.getMin()<min)
                min = pp.getMin();
            if (pp.getMax()>max)
                max = pp.getMax();
            //IJ.write("  " + i + ": " + pp.getMin() + " " + pp.getMax());
        }
        //imp2.hide();
        //ipRotated = ipRotated.rotateLeft();

        if(isVertical)
            plotWidth = firstRect.height;
        else
            plotWidth = firstRect.width;
        if (plotWidth<500)
            plotWidth = 500;
        if(isVertical) {
            if (plotWidth>2*firstRect.height)
                plotWidth = 2*firstRect.height;
        } else {
            if (plotWidth>2*firstRect.width)
                plotWidth = 2*firstRect.width;
        }

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        if (plotWidth>screen.width-40)
            plotWidth = screen.width - 40;
        plotHeight = plotWidth/2;
        if (plotHeight<200)
            plotHeight = 200;
        if (plotHeight>400)
            plotHeight = 400;
        ImageProcessor ip = new ByteProcessor(plotWidth, topMargin+nLanes*plotHeight+bottomMargin);
        ip.setColor(Color.white);
        ip.fill();
        ip.setColor(Color.black);
        //draw border
        int h= ip.getHeight();
        ip.moveTo(0,0);
        ip.lineTo(plotWidth-1,0);
        ip.lineTo(plotWidth-1, h-1);
        ip.lineTo(0, h-1);
        ip.lineTo(0, 0);
        ip.moveTo(0, h-2);
        ip.lineTo(plotWidth-1, h-2);
        String s = imp.getTitle()+"; ";
        if (cal.calibrated())
            s += cal.getValueUnit();
        else
            s += "**Uncalibrated**";
        ip.moveTo(5,topMargin);
        ip.drawString(s);
        double xScale = (double)plotWidth/profiles[1].length;
        double yScale;
        if ((max-min)==0.0)
            yScale = 1.0;
        else
            yScale = plotHeight/(max-min);
        for (int i=1; i<=nLanes; i++) {
            double[] profile = profiles[i];
            int top = (i-1)*plotHeight + topMargin;
            int base = top+plotHeight;
            ip.moveTo(0, base);
            ip.lineTo((int)(profile.length*xScale), base);
            ip.moveTo(0, base-(int)((profile[0]-min)*yScale));
            for (int j = 1; j<profile.length; j++)
                ip.lineTo((int)(j*xScale+0.5), base-(int)((profile[j]-min)*yScale+0.5));
        }
        ImagePlus plots = new Plots();
        plots.setProcessor("Plots of "+imp.getShortTitle(), ip);
        plots.changes = true;
        ip.setThreshold(0,0,ImageProcessor.NO_LUT_UPDATE); // Wand tool works better with threshold set
        plots.show();
        nLanes = 0;
        saveID = 0;
        Toolbar toolbar = Toolbar.getInstance();
        toolbar.setColor(Color.black);
        toolbar.setTool(Toolbar.LINE);
        ImageWindow win = WindowManager.getCurrentWindow();
        ImageCanvas canvas = win.getCanvas();
        if (canvas instanceof PlotsCanvas)
            plotsCanvas = (PlotsCanvas)canvas;
        else
            plotsCanvas = null;



    }

    void show(String msg) {
        IJ.showMessage("Gel Analyzer", msg);
    }

}


class Plots extends ImagePlus {

    /** Overrides ImagePlus.show(). */
    public void show() {
        img = ip.createImage();
        ImageCanvas ic = new PlotsCanvas(this);
        win = new ImageWindow(this, ic);
        IJ.showStatus("");
        if (ic.getMagnification()==1.0)
            return;
        while(ic.getMagnification()<1.0)
            ic.zoomIn(0,0);
        Point loc = win.getLocation();
        int w = getWidth()+20;
        int h = getHeight()+30;
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        if (loc.x+w>screen.width)
            w = screen.width-loc.x-20;
        if (loc.y+h>screen.height)
            h = screen.height-loc.y-30;
        win.setSize(w, h);
        win.validate();
        repaintWindow();
    }

}


class PlotsCanvas extends ImageCanvas {

    public static final int MAX_PEAKS = 200;

    double[] actual = {428566.00,351368.00,233977.00,99413.00,60057.00,31382.00,
                       14531.00,7843.00,2146.00,752.00,367.00};
    double[] measured = new double[MAX_PEAKS];
    Rectangle[] rect = new Rectangle[MAX_PEAKS];
    int counter;

    public PlotsCanvas(ImagePlus imp) {
        super(imp);
    }

    public void mousePressed(MouseEvent e) {
        super.mousePressed(e);
        Roi roi = imp.getRoi();
        if (roi==null)
            return;
        if (roi.getType()==Roi.LINE)
            Roi.setColor(Color.blue);
        else
            Roi.setColor(Color.yellow);
        if (Toolbar.getToolId()!=Toolbar.WAND || IJ.spaceBarDown())
            return;
        ImageStatistics s = imp.getStatistics();
        if (counter==0)
            IJ.setColumnHeadings(" \tArea");
        double perimeter = roi.getLength();
        String error = "";
        double circularity = 4.0*Math.PI*(s.pixelCount/(perimeter*perimeter));
        if (circularity<0.025)
            error = " (error?)";
        double area = s.pixelCount+perimeter/2.0; // add perimeter/2 to account area under border
        rect[counter] = roi.getBoundingRect();

        //area += (rect[counter].width/rect[counter].height)*1.5;
        // adjustment for small peaks from NIH Image gel macros

        IJ.write((counter+1)+"\t"+IJ.d2s(area, 0)+error);
        measured[counter] = area;
        if (counter<MAX_PEAKS)
            counter++;
    }

    public void mouseReleased(MouseEvent e) {
        super.mouseReleased(e);
        Roi roi = imp.getRoi();
        if (roi!=null && roi.getType()==Roi.LINE) {
            Undo.setup(Undo.FILTER, imp);
            imp.getProcessor().snapshot();
            roi.drawPixels();
            imp.updateAndDraw();
            imp.killRoi();
        }
    }

    void reset() {
        counter = 0;
    }

    void labelPeaks() {
        imp.killRoi();
        double total = 0.0;
        for (int i=0; i<counter; i++)
            total += measured[i];
        ImageProcessor ip = imp.getProcessor();
        for (int i=0; i<counter; i++) {
            Rectangle r = rect[i];
            String s;
            if (GelAnalyzer.labelWithPercentages)
                s = IJ.d2s((measured[i]/total)*100, 2);
            else
                s = IJ.d2s(measured[i], 0);
            int swidth = ip.getStringWidth(s);
            int x = r.x + r.width/2 - swidth/2;
            int	y = r.y + r.height*3/4 + 9;
            int[] data = new int[swidth];
            ip.getRow(x, y, data, swidth);
            boolean fits = true;
            for (int j=0; j<swidth; j++)
                if (data[j]!=255) {
                    fits = false;
                    break;
                }
            fits = fits && measured[i]>500;
            if (!fits)
                y = r.y - 2;
            ip.moveTo(x, y);
            ip.drawString(s);
            //IJ.write(i+": "+x+" "+y+" "+s+" "+ip.StringWidth(s)/2);
        }
        imp.updateAndDraw();
        displayPercentages();
        //Toolbar.getInstance().setTool(Toolbar.RECTANGLE);
        reset();
    }

    void displayPercentages() {
        IJ.setColumnHeadings(" \tarea\tpercent");
        double total = 0.0;
        for (int i=0; i<counter; i++)
            total += measured[i];
        if (IJ.debugMode && counter==actual.length) {
            debug();
            return;
        }
        for (int i=0; i<counter; i++) {
            double percent = (measured[i]/total)*100;
            IJ.write((i+1)+"\t"+IJ.d2s(measured[i],4)+"\t"+IJ.d2s(percent,4));
        }
    }

    void debug() {
        for (int i=0; i<counter; i++) {
            double a = (actual[i]/actual[0])*100;
            double m = (measured[i]/measured[0])*100;
            IJ.write(IJ.d2s(a, 4)+" "
                     +IJ.d2s(m, 4)+" "
                     +IJ.d2s(((m-a)/m)*100, 4));
        }
    }

}

