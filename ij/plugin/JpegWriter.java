package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import ij.measure.Calibration;
import java.util.Locale;	
import java.awt.image.*;
import java.awt.*;
import java.io.*;

import java.util.Iterator;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.metadata.IIOMetadata;
import org.w3c.dom.Element;

/** The File/Save As/Jpeg command (FileSaver.saveAsJpeg()) uses
      this plugin to save images in JPEG format. The path where 
      the image is to be saved is passed to the run method. */
public class JpegWriter implements PlugIn {
	public static final int DEFAULT_QUALITY = 75;

    public void run(String arg) {
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp==null) return;
        imp.startTiming();
        saveAsJpeg(imp,arg,FileSaver.getJpegQuality());
        IJ.showTime(imp, imp.getStartTime(), "JpegWriter: ");
    }

    /** Thread-safe method. */
    public static void save(ImagePlus imp, String path, int quality) {
        if (imp!=null) 
            new JpegWriter().saveAsJpeg(imp,path,quality);
    }

    void saveAsJpeg(ImagePlus imp, String path, int quality) {
        int width = imp.getWidth();
        int height = imp.getHeight();
        BufferedImage   bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try {
            Graphics g = bi.createGraphics();
            g.drawImage(imp.getImage(), 0, 0, null);
            g.dispose();            
			Iterator iter = ImageIO.getImageWritersByFormatName("jpeg");
			try {
				ImageWriter writer = (ImageWriter)iter.next();
				writer.setOutput(ImageIO.createImageOutputStream(new File(path)));
				ImageWriteParam param = writer.getDefaultWriteParam();
				param.setCompressionMode(param.MODE_EXPLICIT);
				param.setCompressionQuality(quality / 100f);
				if (quality == 100)
					param.setSourceSubsampling(1, 1, 0, 0);
				IIOMetadata metaData = writer.getDefaultImageMetadata(new ImageTypeSpecifier(bi), param);
				Calibration cal = imp.getCalibration();
				String unit = cal.getUnit().toLowerCase(Locale.US);
				if (cal.getUnit().equals("inch")||cal.getUnit().equals("in")) {
					Element tree = (Element)metaData.getAsTree("javax_imageio_jpeg_image_1.0");
					Element jfif = (Element)tree.getElementsByTagName("app0JFIF").item(0);
					jfif.setAttribute("Xdensity", "" + (int)Math.round(1.0/cal.pixelWidth));
					jfif.setAttribute("Ydensity", "" + (int)Math.round(1.0/cal.pixelHeight));
					jfif.setAttribute("resUnits", "1"); // density is dots per inch*/
					metaData.mergeTree("javax_imageio_jpeg_image_1.0",tree);
				}
				IIOImage iioImage = new IIOImage(bi, null, metaData);
				writer.write(metaData, iioImage, param);
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("falling back to plain jpeg writing because of " + e);
				ImageIO.write(bi, "jpeg", new FileOutputStream(path));
			}
        }
        catch (Exception e) {
           IJ.error("Jpeg Writer", ""+e);
        }
    }

	/** Obsolete, replaced by FileSaver.setJpegQuality(). */
    public static void setQuality(int jpegQuality) {
    	FileSaver.setJpegQuality(jpegQuality);
    }

	/** Obsolete, replaced by FileSaver.getJpegQuality(). */
    public static int getQuality() {
        return FileSaver.getJpegQuality();
    }

}
