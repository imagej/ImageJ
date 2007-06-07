import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import ij.plugin.filter.*; 
import ij.measure.*;

public class Measure_And_Label implements PlugIn { 

public void run(String arg) {
        IJ.run("Measure");
        ImagePlus imp = IJ.getImage();
        Roi roi = imp.getRoi();
        if (roi!=null) {
            IJ.setForegroundColor(255, 255, 255);
            IJ.run("Line Width...", "line=1");
            IJ.run("Draw");
            ResultsTable rt = ResultsTable.getResultsTable();
            int count = rt.getCounter();
            double length = rt.getValue("Length", count-1);
            if (!Double.isNaN(length))
                drawLabel(imp, roi, IJ.d2s(length,1));
             else
                IJ.error("No length measurement available");
       }
    } 

void drawLabel(ImagePlus imp, Roi roi, String label) {
            if (roi==null) return;
            Rectangle r = roi.getBoundingRect();
            ImageProcessor ip = imp.getProcessor();
            //label = Analyzer.getCounter() + label;
            int x = r.x + r.width/2 - ip.getStringWidth(label)/2;
            int y = r.y + r.height/2 + 6;
            ip.setFont(new Font("SansSerif", Font.PLAIN, 9));
            ip.drawString(label, x, y);
            imp.updateAndDraw();
    } 

}



