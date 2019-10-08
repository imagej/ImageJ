package ij.plugin;
import ij.*;
import ij.io.*;
import ij.process.*;
import java.awt.*;
import java.io.*;
import java.awt.image.*;
import javax.imageio.ImageIO;


/** Saves in PNG format using the ImageIO classes.  RGB images are saved
	as RGB PNGs. All other image types are saved as 8-bit PNGs. With 8-bit images,
	the value of the transparent index can be set in the Edit/Options/Input-Output dialog,
	or by calling Prefs.setTransparentIndex(index), where 0<=index<=255. */
public class PNG_Writer implements PlugIn {
    ImagePlus imp;

    public void run(String path) {
        imp = WindowManager.getCurrentImage();
        if (imp==null) {
        	IJ.noImage();
        	return;
        }
        if (path.equals("")) {
            SaveDialog sd = new SaveDialog("Save as PNG...", imp.getTitle(), ".png");
            String name = sd.getFileName();
            if (name==null)
                return;
            String dir = sd.getDirectory();
            path = dir + name;
        }
        try {
            writeImage(imp, path, Prefs.getTransparentIndex());
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg==null || msg.equals(""))
                msg = ""+e;
            IJ.error("PNG Writer", "An error occured writing the file.\n \n" + msg);
        }
        IJ.showStatus("");
    }

	public void writeImage(ImagePlus imp, String path, int transparentIndex) throws Exception {
		if (imp.getType()==ImagePlus.COLOR_256) {
			imp = imp.duplicate();
			new ImageConverter(imp).convertToRGB();
		}
		if (imp.getStackSize()==4 && imp.getBitDepth()==8 && "alpha".equalsIgnoreCase(imp.getStack().getSliceLabel(4)))
			writeFourChannelsWithAlpha(imp, path);
		else if (transparentIndex>=0 && transparentIndex<=255 && imp.getBitDepth()==8)
			writeImageWithTransparency(imp, path, transparentIndex);
		else if (imp.getOverlay()!=null && !imp.getHideOverlay())
			ImageIO.write(imp.flatten().getBufferedImage(), "png", new File(path));
		else if (imp.getBitDepth()==16 && !imp.isComposite() && imp.getProcessor().isDefaultLut())
			write16gs(imp, path);
        else
			ImageIO.write(imp.getBufferedImage(), "png", new File(path));
	}
	
	private void writeFourChannelsWithAlpha(ImagePlus imp, String path) throws Exception {
		ImageStack stack = imp.getStack();
		int w=imp.getWidth(), h=imp.getHeight();
		ImagePlus imp2 = new ImagePlus("", new ColorProcessor(w,h));
		ColorProcessor cp = (ColorProcessor)imp2.getProcessor();
		for (int channel=1; channel<=4; channel++)
			cp.setChannel(channel, (ByteProcessor)stack.getProcessor(channel));
		BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		WritableRaster raster = bi.getRaster();
		raster.setDataElements(0, 0, w, h, cp.getPixels());
		ImageIO.write(bi, "png", new File(path));
	}
    
	void writeImageWithTransparency(ImagePlus imp, String path, int transparentIndex) throws Exception {
		int width = imp.getWidth();
		int  height = imp.getHeight();
		ImageProcessor ip = imp.getProcessor();
		IndexColorModel cm = (IndexColorModel)ip.getColorModel();
		int size = cm.getMapSize();
		byte[] reds = new byte[256];
		byte[] greens = new byte[256];
		byte[] blues = new byte[256];	
		cm.getReds(reds); 
		cm.getGreens(greens); 
		cm.getBlues(blues);
		cm = new IndexColorModel(8, 256, reds, greens, blues, transparentIndex);
		WritableRaster wr = cm.createCompatibleWritableRaster(width, height);
		DataBufferByte db = (DataBufferByte)wr.getDataBuffer();
		byte[] biPixels = db.getData();
		System.arraycopy(ip.getPixels(), 0, biPixels, 0, biPixels.length);
		BufferedImage bi = new BufferedImage(cm, wr, false, null);
		ImageIO.write(bi, "png", new File(path));
	}

    void write16gs(ImagePlus imp, String path) throws Exception {
		ShortProcessor sp = (ShortProcessor)imp.getProcessor();
		BufferedImage bi = sp.get16BitBufferedImage();
		File f = new File(path);
		ImageIO.write(bi, "png", f);
    }
}
