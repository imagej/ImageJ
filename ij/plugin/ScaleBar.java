package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.measure.*;
import java.awt.event.*;

/** This plugin implements the Analyze/Tools/Draw Scale Bar command. */
public class ScaleBar implements PlugIn {

    static final String[] locations = {"Upper Right", "Lower Right", "Lower Left", "Upper Left", "At Selection"};
    static final int UPPER_RIGHT=0, LOWER_RIGHT=1, LOWER_LEFT=2, UPPER_LEFT=3, AT_SELECTION=4;
    static final String[] colors = {"White","Black","Light Gray","Gray","Dark Gray","Red","Green","Blue","Yellow"};
    static final String[] checkboxLabels = {"Bold Text", "Hide Text"};
    static double barWidth;
    static int defaultBarHeight = 3;
    static int barHeightInPixels = defaultBarHeight;
    static String location = locations[UPPER_RIGHT];
    static String color = colors[0];
    static boolean boldText = true;
    static boolean hideText;
    static int fontSize;
    static boolean labelAll;
    ImagePlus imp;
    double imageWidth;
    double mag;
    int xloc, yloc;
    int barWidthInPixels;
    int roiX=-1, roiY, roiWidth, roiHeight;
    boolean[] checkboxStates = new boolean[2];

 
    public void run(String arg) {
        imp = WindowManager.getCurrentImage();
        if (imp!=null) {
            if (showDialog(imp) && imp.getStackSize()>1 && labelAll)
            	labelSlices(imp);
        } else
            IJ.noImage();
     }

    void labelSlices(ImagePlus imp) {
		int slice = imp.getCurrentSlice();
		for (int i=1; i<=imp.getStackSize(); i++) {
			imp.setSlice(i);
			drawScaleBar(imp);
		}
		imp.setSlice(slice);		
    }
    
    boolean showDialog(ImagePlus imp) {
        Roi roi = imp.getRoi();
        if (roi!=null) {
            Rectangle r = roi.getBounds();
            roiX = r.x;
            roiY = r.y; 
            roiWidth = r.width;
            roiHeight = r.height;
            location = locations[AT_SELECTION];
        } else if (location.equals(locations[AT_SELECTION]))
            location = locations[UPPER_RIGHT];
            
        Calibration cal = imp.getCalibration();
        ImageWindow win = imp.getWindow();
        mag = (win!=null)?win.getCanvas().getMagnification():1.0;
        if (mag>1.0)
            mag = 1.0;
        if (fontSize<(12/mag))
        	fontSize = (int)(12/mag);
        String units = cal.getUnits();
        double pixelWidth = cal.pixelWidth;
        if (pixelWidth==0.0)
            pixelWidth = 1.0;
        double scale = 1.0/pixelWidth;
        imageWidth = imp.getWidth()*pixelWidth;
        if (roiX>0 && roiWidth>10)
            barWidth = roiWidth*pixelWidth;
        else if (barWidth==0.0 || barWidth>0.67*imageWidth) {
            barWidth = (80.0*pixelWidth)/mag;
            if (barWidth>0.67*imageWidth)
                barWidth = 0.67*imageWidth;
            if (barWidth>5.0)
                barWidth = (int)barWidth;
        }
        int stackSize = imp.getStackSize();
        int digits = (int)barWidth==barWidth?0:1;
        if (barWidth<1.0)
            digits = 2;
        int percent = (int)(barWidth*100.0/imageWidth);
        if (mag<1.0 && barHeightInPixels<defaultBarHeight/mag)
            barHeightInPixels = (int)(defaultBarHeight/mag);
        imp.getProcessor().snapshot();
        if (!IJ.macroRunning()) updateScalebar();
        GenericDialog gd = new BarDialog("Scale Bar");
        gd.addNumericField("Width in "+units+": ", barWidth, digits);
        gd.addNumericField("Height in pixels: ", barHeightInPixels, 0);
        gd.addNumericField("Font Size: ", fontSize, 0);
        gd.addChoice("Color: ", colors, color);
        gd.addChoice("Location: ", locations, location);
        checkboxStates[0] = boldText; checkboxStates[1] = hideText;
		gd.addCheckboxGroup(1, 2, checkboxLabels, checkboxStates);
		if (stackSize>1)
			gd.addCheckbox("Label all Slices", labelAll);
        gd.showDialog();
        if (gd.wasCanceled()) {
            imp.getProcessor().reset();
            imp.updateAndDraw();
            return false;
        }
        barWidth = gd.getNextNumber();
        barHeightInPixels = (int)gd.getNextNumber();
        fontSize = (int)gd.getNextNumber();
        color = gd.getNextChoice();
        location = gd.getNextChoice();
        boldText = gd.getNextBoolean();
        hideText = gd.getNextBoolean();
        if (stackSize>1)
        	labelAll = gd.getNextBoolean();
        if (IJ.macroRunning()) updateScalebar();
         return true;
    }

    void drawScaleBar(ImagePlus imp) {
          if (!updateLocation())
            return;
        ImageProcessor ip = imp.getProcessor();
        Undo.setup(Undo.FILTER, imp);
        Color color = getColor();
        //if (!(color==Color.white || color==Color.black)) {
        //    ip = ip.convertToRGB();
        //    imp.setProcessor(null, ip);
        //}
        ip.setColor(color);
        int x = xloc;
        int y = yloc;
        ip.setRoi(x, y, barWidthInPixels, barHeightInPixels);
        ip.fill();
        ip.resetRoi();

        int fontType = boldText?Font.BOLD:Font.PLAIN;
        ip.setFont(new Font("SansSerif", fontType, fontSize));
		ip.setAntialiasedText(true);
        int digits = (int)barWidth==barWidth?0:1;
        if (barWidth<1.0)
            digits = 2;
        String label = IJ.d2s(barWidth, digits) + " "+ imp.getCalibration().getUnits();
        x = x +(barWidthInPixels - ip.getStringWidth(label))/2;
        y = y + barHeightInPixels + fontSize + fontSize/4;
        if (!hideText)
            ip.drawString(label, x, y);   
        imp.updateAndDraw();
    }
 
     boolean updateLocation() {
        Calibration cal = imp.getCalibration();
        barWidthInPixels = (int)(barWidth/cal.pixelWidth);
        int width = imp.getWidth();
        int height = imp.getHeight();
        int fraction = 20;
        int x = width - width/fraction - barWidthInPixels;
        int y = 0;
        if (location.equals(locations[UPPER_RIGHT]))
             y = height/fraction;
        else if (location.equals(locations[LOWER_RIGHT]))
            y = height - height/fraction - barHeightInPixels - fontSize;
        else if (location.equals(locations[UPPER_LEFT])) {
            x = width/fraction;
            y = height/fraction;
        } else if (location.equals(locations[LOWER_LEFT])) {
            x = width/fraction;
            y = height - height/fraction - barHeightInPixels - fontSize;
        } else {
            if (roiX==-1)
                 return false;
            x = roiX;
            y = roiY;
        }
        xloc = x;
        yloc = y;
        return true;
    }

    Color getColor() {
        Color c = Color.white;
        if (color.equals(colors[1])) c = Color.black;
        else if (color.equals(colors[2])) c = Color.lightGray;
        else if (color.equals(colors[3])) c = Color.gray;
        else if (color.equals(colors[4])) c = Color.darkGray;
        else if (color.equals(colors[5])) c = Color.red;
        else if (color.equals(colors[6])) c = Color.green;
        else if (color.equals(colors[7])) c = Color.blue;
        else if (color.equals(colors[8])) c = Color.yellow;
       return c;
    }

     void updateScalebar() {
        updateLocation();
        imp.getProcessor().reset();
        drawScaleBar(imp);
    }

   class BarDialog extends GenericDialog {

        BarDialog(String title) {
            super(title);
        }

        public void textValueChanged(TextEvent e) {
            TextField widthField = ((TextField)numberField.elementAt(0));
            Double d = getValue(widthField.getText());
            if (d==null)
                return;
            barWidth = d.doubleValue();
            TextField heightField = ((TextField)numberField.elementAt(1));
            d = getValue(heightField.getText());
            if (d==null)
                return;
            barHeightInPixels = (int)d.doubleValue();
            TextField fontSizeField = ((TextField)numberField.elementAt(2));
            d = getValue(fontSizeField.getText());
            if (d==null)
                return;
            int size = (int)d.doubleValue();
            if (size>5)
                fontSize = size;
            updateScalebar();
        }

        public void itemStateChanged(ItemEvent e) {
            Choice col = (Choice)(choice.elementAt(0));
            color = col.getSelectedItem();
            Choice loc = (Choice)(choice.elementAt(1));
            location = loc.getSelectedItem();
            boldText = ((Checkbox)(checkbox.elementAt(0))).getState();
            hideText = ((Checkbox)(checkbox.elementAt(1))).getState();
            updateScalebar();
        }

   } //BarDialog inner class

} //ScaleBar class


