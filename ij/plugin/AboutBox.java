package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.io.*;
import java.net.URL;
import java.awt.image.*;

/** This plugin implements the Help/About ImageJ command by opening
	the about.jpg in ij.jar, scaling it 400% and adding some text. */
	public class AboutBox implements PlugIn {
		private static final int SMALL_FONT=20, LARGE_FONT=45;

	public void run(String arg) {
		System.gc();
		int lines = 7;
		String[] text = new String[lines];
		text[0] = "ImageJ "+ImageJ.VERSION+ImageJ.BUILD;
		text[1] = "Wayne Rasband";
		text[2] = "National Institutes of Health, USA";
		text[3] = IJ.URL;
		text[4] = "Java "+System.getProperty("java.version")+(IJ.is64Bit()?" (64-bit)":" (32-bit)");
		text[5] = IJ.freeMemory();
		text[6] = "ImageJ is in the public domain";
		ImageProcessor ip = null;
		ImageJ ij = IJ.getInstance();
		URL url = ij .getClass() .getResource("/about.jpg");
		if (url!=null) {
			Image img = null;
			try {img = ij.createImage((ImageProducer)url.getContent());}
			catch(Exception e) {}
			if (img!=null) {
				ImagePlus sImp = new ImagePlus("", img);
				ip = sImp.getProcessor();
			}
		}
		if (ip==null) 
			ip =  new ColorProcessor(55,45);
		ip = ip.resize(ip.getWidth()*6, ip.getHeight()*6);
		ImagePlus imp = new ImagePlus("About ImageJ", ip);
		int width = imp.getWidth();
		Overlay overlay = new Overlay();
		Color color = new Color(255,255, 140);
		Font font = new Font("SansSerif", Font.PLAIN, LARGE_FONT);
		int y  = 60;
		add(text[0], width-20, y, font, color, TextRoi.RIGHT, overlay);
		int xcenter = 410;
		font = new Font("SansSerif", Font.PLAIN, SMALL_FONT);
		y += 45;
		add(text[1], xcenter, y, font, color, TextRoi.CENTER, overlay);
		y += 27;
		add(text[2], xcenter, y, font, color, TextRoi.CENTER, overlay);
		y += 27;
		add(text[3], xcenter, y, font, color, TextRoi.CENTER, overlay);
		y += 27;
		add(text[4], xcenter, y, font, color, TextRoi.CENTER, overlay);
		if (IJ.maxMemory()>0L) {
			y += 27;
			add(text[5], xcenter, y, font, color, TextRoi.CENTER, overlay);
		}
		add(text[6], width-10, ip.getHeight()-10, font, color, TextRoi.RIGHT, overlay);
		imp.setOverlay(overlay);
		ImageWindow.centerNextImage();
		imp.show();
	}
	
	private void add(String text, int x, int y, Font font, Color color, int justification, Overlay overlay) {
		TextRoi roi = new TextRoi(text, x, y, font);
		roi.setStrokeColor(color);
		roi.setJustification(justification);
		overlay.add(roi);
	}

	private int x(String text, ImageProcessor ip, int max) {
		return ip.getWidth() - max + (max - ip.getStringWidth(text))/2 - 10;
	}

}
