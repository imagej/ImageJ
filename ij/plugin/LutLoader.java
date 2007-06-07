package ij.plugin;
import java.awt.*;
import java.io.*;
import java.awt.image.*;
import java.net.*;
import ij.*;
import ij.io.*;
import ij.process.*;

/** Opens NIH Image look-up tables (LUTs), 768 byte raw LUTs
	(256 reds, 256 greens and 256 blues), or uses a method to
	generate the LUT. */
public class LutLoader extends ImagePlus implements PlugIn {

	private static String defaultDirectory = null;

	/** If 'arg'="", displays a file open dialog and opens the specified
		LUT. If 'arg' is a path, opens the LUT specified by the path. If
		'arg'="fire", "ice", etc., uses a method to generate the LUT. */
	public void run(String arg) {
		FileInfo fi = new FileInfo();
		fi.reds = new byte[256]; 
		fi.greens = new byte[256]; 
		fi.blues = new byte[256];
		fi.lutSize = 256;
		int nColors = 0;
		
		if (arg.equals("invert"))
			{invertLut(); return;}
		else if (arg.equals("fire"))
			nColors = fire(fi.reds, fi.greens, fi.blues);
		else if (arg.equals("grays"))
			nColors = grays(fi.reds, fi.greens, fi.blues);
		else if (arg.equals("ice"))
			nColors = ice(fi.reds, fi.greens, fi.blues);
		else if (arg.equals("spectrum"))
			nColors = spectrum(fi.reds, fi.greens, fi.blues);
		else if (arg.equals("3-3-2 RGB"))
			nColors = rgb332(fi.reds, fi.greens, fi.blues);
		else if (arg.equals("red"))
			nColors = primaryColor(4, fi.reds, fi.greens, fi.blues);
		else if (arg.equals("green"))
			nColors = primaryColor(2, fi.reds, fi.greens, fi.blues);
		else if (arg.equals("blue"))
			nColors = primaryColor(1, fi.reds, fi.greens, fi.blues);
		else if (arg.equals("cyan"))
			nColors = primaryColor(3, fi.reds, fi.greens, fi.blues);
		else if (arg.equals("magenta"))
			nColors = primaryColor(5, fi.reds, fi.greens, fi.blues);
		else if (arg.equals("yellow"))
			nColors = primaryColor(6, fi.reds, fi.greens, fi.blues);
		else if (arg.equals("redgreen"))
			nColors = redGreen(fi.reds, fi.greens, fi.blues);
		if (nColors>0) {
			if (nColors<256)
				interpolate(fi.reds, fi.greens, fi.blues, nColors);
			fi.fileName = arg;
			showLut(fi, true);
			Menus.updateMenus();
			return;
		}
		
		OpenDialog od = new OpenDialog("Open LUT...", arg);
		fi.directory = od.getDirectory();
		fi.fileName = od.getFileName();
		if (fi.fileName==null)
				return;
		IJ.showStatus("Opening: " + fi.directory + fi.fileName);
		try {
			int size = readLut(fi, false); // attempt to read NIH Image LUT
			if (size==0) readLut(fi,true);  // otherwise read 768 byte raw LUT
		} catch (IOException e) {
			IJ.error(e.getMessage());
			return;
		}
		showLut(fi, arg.equals(""));
		IJ.showStatus("");
	}
	
	void showLut(FileInfo fi, boolean showImage) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			if (imp.getType()==ImagePlus.COLOR_RGB)
				IJ.error("Color tables cannot be assiged to RGB Images.");
			else {
				ImageProcessor ip = imp.getProcessor();
				ColorModel cm = new IndexColorModel(8, 256, fi.reds, fi.greens, fi.blues);
				ip.setColorModel(cm);
				if (imp.getStackSize()>1)
					imp.getStack().setColorModel(cm);
				imp.updateAndDraw();
			}
		} else
			createImage(fi, showImage);
	}
	
	void invertLut() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
		if (imp.getType()==ImagePlus.COLOR_RGB)
			{IJ.error("RGB images do not use LUTs"); return;}
		ImageProcessor ip = imp.getProcessor();
		ip.invertLut();
		if (imp.getStackSize()>1)
			imp.getStack().setColorModel(ip.getColorModel());
		imp.updateAndDraw();
		if (imp.getRoi()!=null)
			imp.draw(); // update entire image, not just roi
	}

	int fire(byte[] reds, byte[] greens, byte[] blues) {
		int[] r = {0,0,1,25,49,73,98,122,146,162,173,184,195,207,217,229,240,252,255,255,255,255,255,255,255,255,255,255,255,255,255,255};
		int[] g = {0,0,0,0,0,0,0,0,0,0,0,0,0,14,35,57,79,101,117,133,147,161,175,190,205,219,234,248,255,255,255,255};
		int[] b = {31,61,96,130,165,192,220,227,210,181,151,122,93,64,35,5,0,0,0,0,0,0,0,0,0,0,0,35,98,160,223,255};
		for (int i=0; i<r.length; i++) {
			reds[i] = (byte)r[i];
			greens[i] = (byte)g[i];
			blues[i] = (byte)b[i];
		}
		return r.length;
	}

	int grays(byte[] reds, byte[] greens, byte[] blues) {
		for (int i=0; i<256; i++) {
			reds[i] = (byte)i;
			greens[i] = (byte)i;
			blues[i] = (byte)i;
		}
		return 256;
	}
	
	int primaryColor(int color, byte[] reds, byte[] greens, byte[] blues) {
		for (int i=0; i<256; i++) {
			if ((color&4)!=0)
				reds[i] = (byte)i;
			if ((color&2)!=0)
				greens[i] = (byte)i;
			if ((color&1)!=0)
				blues[i] = (byte)i;
		}
		return 256;
	}
	
	int ice(byte[] reds, byte[] greens, byte[] blues) {
		int[] r = {0,0,0,0,0,0,19,29,50,48,79,112,134,158,186,201,217,229,242,250,250,250,250,251,250,250,250,250,251,251,243,230};
		int[] g = {156,165,176,184,190,196,193,184,171,162,146,125,107,93,81,87,92,97,95,93,93,90,85,69,64,54,47,35,19,0,4,0};
		int[] b = {140,147,158,166,170,176,209,220,234,225,236,246,250,251,250,250,245,230,230,222,202,180,163,142,123,114,106,94,84,64,26,27};
		for (int i=0; i<r.length; i++) {
			reds[i] = (byte)r[i];
			greens[i] = (byte)g[i];
			blues[i] = (byte)b[i];
		}
		return r.length;
	}

	int spectrum(byte[] reds, byte[] greens, byte[] blues) {
		Color c;
		for (int i=0; i<256; i++) {
			c = Color.getHSBColor(i/255f, 1f, 1f);
			reds[i] = (byte)c.getRed();
			greens[i] = (byte)c.getGreen();
			blues[i] = (byte)c.getBlue();
		}
		return 256;
	}
	
	int rgb332(byte[] reds, byte[] greens, byte[] blues) {
		Color c;
		for (int i=0; i<256; i++) {
			reds[i] = (byte)(i&0xe0);
			greens[i] = (byte)((i<<3)&0xe0);
			blues[i] = (byte)((i<<6)&0xc0);
		}
		return 256;
	}

	int redGreen(byte[] reds, byte[] greens, byte[] blues) {
		for (int i=0; i<128; i++) {
			reds[i] = (byte)(i*2);
			greens[i] = (byte)0;
			blues[i] = (byte)0;
		}
		for (int i=128; i<256; i++) {
			reds[i] = (byte)0;
			greens[i] = (byte)(i*2);
			blues[i] = (byte)0;
		}
		return 256;
	}

	void interpolate(byte[] reds, byte[] greens, byte[] blues, int nColors) {
		/*
		String s = "";
		for (int i=0; i<nColors; i++)
			s += (reds[i]&255)+",";
		IJ.write(s);
		s = "";
		for (int i=0; i<nColors; i++)
			s += (greens[i]&255)+",";
		IJ.write(s);
		s = "";
		for (int i=0; i<nColors; i++)
			s += (blues[i]&255)+",";
		IJ.write(s);
		*/
		byte[] r = new byte[nColors]; 
		byte[] g = new byte[nColors]; 
		byte[] b = new byte[nColors];
		System.arraycopy(reds, 0, r, 0, nColors);
		System.arraycopy(greens, 0, g, 0, nColors);
		System.arraycopy(blues, 0, b, 0, nColors);
		double scale = nColors/256.0;
		int i1, i2;
		double fraction;
		for (int i=0; i<256; i++) {
			i1 = (int)(i*scale);
			i2 = i1+1;
			if (i2==nColors) i2 = nColors-1;
			fraction = i*scale - i1;
			//IJ.write(i+" "+i1+" "+i2+" "+fraction);
			reds[i] = (byte)((1.0-fraction)*(r[i1]&255) + fraction*(r[i2]&255));
			greens[i] = (byte)((1.0-fraction)*(g[i1]&255) + fraction*(g[i2]&255));
			blues[i] = (byte)((1.0-fraction)*(b[i1]&255) + fraction*(b[i2]&255));
		}
	}
	
	/** Opens an NIH Image LUT or a raw 768 byte LUT. */
	int readLut(FileInfo fi, boolean raw) throws IOException {
		InputStream is;
		if (fi.url==null || fi.url.equals(""))
			is = new FileInputStream(fi.directory + fi.fileName);
		else
			is = new URL(fi.url+fi.fileName).openStream();
		DataInputStream f = new DataInputStream(is);
		int nColors = 256;
		if (!raw) {
			// attempt to read 32 byte NIH Image LUT header
			int id = f.readInt();
			if (id!=1229147980) // 'ICOL'
				return 0;
			int version = f.readShort();
			nColors = f.readShort();
			int start = f.readShort();
			int end = f.readShort();
			long fill1 = f.readLong();
			long fill2 = f.readLong();
			int filler = f.readInt();
		}
		//IJ.write(id+" "+version+" "+nColors);
		f.read(fi.reds, 0, nColors);
		f.read(fi.greens, 0, nColors);
		f.read(fi.blues, 0, nColors);
		if (nColors<256)
			interpolate(fi.reds, fi.greens, fi.blues, nColors);
		f.close();
		return 256;
	}
	
	void createImage(FileInfo fi, boolean show) {
		int width = 256;
		int height = 32;
		IndexColorModel cm = new IndexColorModel(8, 256, fi.reds, fi.greens, fi.blues);
		byte[] pixels = new byte[width*height];
		ByteProcessor bp = new ByteProcessor(width, height, pixels, cm);
		int[] ramp = new int[width];
		for (int i=0; i<width; i++)
			ramp[i] = i; 
		for (int y=0; y<height; y++)
			bp.putRow(0, y, ramp, width);
    	setProcessor(fi.fileName, bp);
     	if (show)
    		show();
	}
}