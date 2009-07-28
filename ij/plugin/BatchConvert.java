package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.util.Tools;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

/** This plugin implements the File/Batch/Convert command, 
	which converts the images in a folder to a specified format. */
	public class BatchConvert implements PlugIn, ActionListener {
		private static final String[] formats = {"TIFF", "8-bit TIFF", "JPEG", "GIF", "PNG", "PGM", "BMP", "FITS", "Text Image", "ZIP", "Raw"};
		private static String format = formats[0];
		private static int height;
		private static double scale = 1.0;
		private static int interpolationMethod = ImageProcessor.BILINEAR;
		private String[] methods = ImageProcessor.getInterpolationMethods();
		private static String inputPath, outputPath;
		private Button input, output;
		private Label inputDir, outputDir;
		private GenericDialog gd;

	public void run(String arg) {
		if (!showDialog()) return;
		if (inputPath==null) {
			IJ.error("Batch Convert", "Please choose an input folder");
			return;
		}
		if (outputPath==null) {
			IJ.error("Batch Convert", "Please choose an output folder");
			return;
		}
		String[] list = (new File(inputPath)).list();
		ImageJ ij = IJ.getInstance();
		if (ij!=null) ij.getProgressBar().setBatchMode(true);
		IJ.resetEscape();
		for (int i=0; i<list.length; i++) {
			if (IJ.escapePressed()) break;
			if (IJ.debugMode) IJ.log(i+"  "+list[i]);
			String path = inputPath + list[i];
			if ((new File(path)).isDirectory())
				continue;
			if (list[i].startsWith(".")||list[i].endsWith(".avi")||list[i].endsWith(".AVI"))
				continue;
			IJ.showProgress(i+1, list.length);
			ImagePlus imp = IJ.openImage(path);
			if (imp==null) continue;
			if (height!=0) {
				double aspectRatio = (double)imp.getWidth()/imp.getHeight();
				int width = (int)(height*aspectRatio);
				ImageProcessor ip = imp.getProcessor();
				ip.setInterpolationMethod(interpolationMethod);
				imp.setProcessor(null, ip.resize(width,height));
			} else if (scale!=1.0) {
				int width = (int)(scale*imp.getWidth());
				int height = (int)(scale*imp.getHeight());
				ImageProcessor ip = imp.getProcessor();
				ip.setInterpolationMethod(interpolationMethod);
				imp.setProcessor(null, ip.resize(width,height));
			}
			if (format.equals("8-bit TIFF") || format.equals("GIF")) {
				if (imp.getBitDepth()==24)
					IJ.run(imp, "8-bit Color", "number=256");
				else
					IJ.run(imp, "8-bit", "");
			}
			IJ.saveAs(imp, format, outputPath+list[i]);
			imp.close();
		}
		IJ.showProgress(1,1);
	}
		
	boolean showDialog() {
		gd = new GenericDialog("Batch Convert");
		addPanels(gd);
		gd.setInsets(15, 0, 5);
		gd.addChoice("Output Format: ", formats, format);
		gd.addChoice("Interpolation:", methods, methods[interpolationMethod]);
		gd.addStringField("Height (pixels): ", height==0?"\u2014":""+height, 6);
		gd.addNumericField("or Scale Factor: ", scale, 2);
		gd.setOKLabel("Convert");
		gd.showDialog();
		format = gd.getNextChoice();
		interpolationMethod = gd.getNextChoiceIndex();
		height = (int)Tools.parseDouble(gd.getNextString(), 0.0);
		scale = gd.getNextNumber();
		return !gd.wasCanceled();
	}

	void addPanels(GenericDialog gd) {
		Panel p = new Panel();
    	p.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
		input = new Button("Input...");
		input.addActionListener(this);
		p.add(input);
		inputDir = makeLabel(1);
		p.add(inputDir);
		gd.addPanel(p);
		p = new Panel();
    	p.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
		output = new Button("Output...");
		output.addActionListener(this);
		p.add(output);
		outputDir = makeLabel(2);
		p.add(outputDir);
		gd.addPanel(p);
	}
	
	Label makeLabel(int n) {
		String label = null;
		if (n==1 && inputPath!=null)
			label = (new File(inputPath)).getName();
		else if (n==2 && outputPath!=null)
			label = (new File(outputPath)).getName();
		if (label!=null) {
			while (label.length()<25)
				label = label + '\u2002';  //en space
		} else
			label = "_________________________"; 
		return new Label(label);
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		String s = source==input?"Input":"Output";
		String path = IJ.getDirectory(s+" Folder");
		if (path==null) return;
		if (source==input) {
			inputPath = path;
			inputDir.setText(new File(path).getName());
		} else {
			outputPath = path;
			outputDir.setText(new File(path).getName());
		}
		if (IJ.isMacOSX()) gd.repaint();
	}

}
