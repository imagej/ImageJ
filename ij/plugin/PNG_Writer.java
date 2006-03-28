package ij.plugin;
import ij.*;
import ij.io.*;
import ij.process.*;
import java.awt.*;
import java.io.*;
import java.awt.image.*;
import javax.imageio.ImageIO;


/** Saves in PNG format using the ImageIO class  available in java 1.4 or later.  */
public class PNG_Writer implements PlugIn {
    ImagePlus imp;

    public void run(String path) {
        if (!IJ.isJava14()) {
        	IJ.showMessage("PNG Writer" , "The command requires java 1.4 or later");
        	return;
        }
        imp = WindowManager.getCurrentImage();
        if (imp==null)
        	{IJ.noImage(); return;}

        if (path.equals("")) {
            SaveDialog sd = new SaveDialog("Save as PNG...", imp.getTitle(), ".png");
            String name = sd.getFileName();
            if (name==null)
                return;
            String dir = sd.getDirectory();
            path = dir + name;
        }

        try {
            writeImage(imp, path);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg==null || msg.equals(""))
                msg = ""+e;
            IJ.showMessage("PNG Writer", "An error occured writing the file.\n \n" + msg);
        }
        IJ.showStatus("");
    }

    void writeImage(ImagePlus imp, String path) throws Exception {
		int width = imp.getWidth();
		int  height = imp.getHeight();
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D)bi.getGraphics();
		g.drawImage(imp.getImage(), 0, 0, null);
		File f = new File(path);
		ImageIO.write(bi, "png", f);
    }


}

