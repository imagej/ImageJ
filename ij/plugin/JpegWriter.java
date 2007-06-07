package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import com.sun.image.codec.jpeg.*;
import java.awt.image.*;
import java.awt.*;
import java.io.*;

/** The File->Save As->Jpeg command (FileSaver.saveAsJpeg()) uses
      this plugin to save images in JPEG format when running Java 2. The
      path where the image is to be saved is passed to the run method. */
public class JpegWriter implements PlugIn {

    public void run(String arg) {
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp==null)
	 return;
        imp.startTiming();
        saveAsJpeg(imp,arg);
        IJ.showTime(imp, imp.getStartTime(), "JpegWriter: ");
    } 

    void saveAsJpeg(ImagePlus imp, String path) {
        //IJ.log("saveAsJpeg: "+path);
        int width = imp.getWidth();
        int height = imp.getHeight();
        BufferedImage   bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try {
            FileOutputStream  f  = new FileOutputStream(path);                
            Graphics g = bi.createGraphics();
            g.drawImage(imp.getImage(), 0, 0, null);
            g.dispose();            
            JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(f);
            JPEGEncodeParam param = encoder.getDefaultJPEGEncodeParam(bi);
            param.setQuality((float)(JpegEncoder.getQuality()/100.0), true);
            encoder.encode(bi, param);
            f.close();
        }
        catch (Exception e) {
           IJ.showMessage("Jpeg Writer", ""+e);
        }
    }

}
