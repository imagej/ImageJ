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
	public class BatchProcesser implements PlugIn, ActionListener, Runnable {
		private static final String[] formats = {"TIFF", "8-bit TIFF", "JPEG", "GIF", "PNG", "PGM", "BMP", "FITS", "Text Image", "ZIP", "Raw"};
		private static String format = formats[0];
		private static String macro = "";
		private static int testImage;
		private Button input, output, test;
		private TextField inputDir, outputDir;
		private GenericDialog gd;
		private Thread thread;

	public void run(String arg) {
		macro = "setFont(\"SansSerif\", 18, \"antialiased\");\nsetColor(\"red\");\ndrawString(\"Hello\", 20, 30);\n";
		if (!showDialog()) return;
		String inputPath = inputDir.getText();
		if (inputPath.equals("")) {
			IJ.error("Batch Processer", "Please choose an input folder");
			return;
		}
		File f1 = new File(inputPath);
		if (!f1.exists() || !f1.isDirectory()) {
			IJ.error("Batch Processer", "Input does not exist or is not a folder\n \n"+inputPath);
			return;
		}
		String outputPath = outputDir.getText();
		File f2 = new File(outputPath);
		if (!outputPath.equals("") && (!f2.exists() || !f2.isDirectory())) {
			IJ.error("Batch Processer", "Output does not exist or is not a folder\n \n"+outputPath);
			return;
		}
		if (macro.equals("")) {
			IJ.error("Batch Processer", "There is no macro code in the text area");
			return;
		}
		String[] list = (new File(inputPath)).list();
		ImageJ ij = IJ.getInstance();
		if (ij!=null) ij.getProgressBar().setBatchMode(true);
		IJ.resetEscape();
		for (int i=0; i<list.length; i++) {
			if (IJ.escapePressed()) break;
			String path = inputPath + list[i];
			if (IJ.debugMode) IJ.log(i+": "+path);
			if ((new File(path)).isDirectory())
				continue;
			if (list[i].startsWith(".")||list[i].endsWith(".avi")||list[i].endsWith(".AVI"))
				continue;
			IJ.showProgress(i+1, list.length);
			ImagePlus imp = IJ.openImage(path);
			if (imp==null) continue;
			if (!macro.equals("")) {
				WindowManager.setTempCurrentImage(imp);
				String str = IJ.runMacro(macro, "");
				if (str!=null && str.equals("[aborted]")) break;
			}
			if (!outputPath.equals("")) {
				if (format.equals("8-bit TIFF") || format.equals("GIF")) {
					if (imp.getBitDepth()==24)
						IJ.run(imp, "8-bit Color", "number=256");
					else
						IJ.run(imp, "8-bit", "");
				}
				IJ.saveAs(imp, format, outputPath+list[i]);
			}
			imp.close();
		}
		IJ.showProgress(1,1);
		Prefs.set("batch.input", inputDir.getText());
		Prefs.set("batch.output", outputDir.getText());
	}
		
	boolean showDialog() {
		gd = new GenericDialog("Batch Process");
		addPanels(gd);
		gd.setInsets(15, 0, 5);
		gd.addChoice("Output Format: ", formats, format);
		gd.setInsets(15, 10, 0);
		gd.addTextAreas(macro, null, 12, 55);
		addTestButton(gd);
		gd.setOKLabel("Process");
		gd.showDialog();
		macro = gd.getNextText();
		return !gd.wasCanceled();
	}

	void addPanels(GenericDialog gd) {
		Panel p = new Panel();
    	p.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
		input = new Button("Input...");
		input.addActionListener(this);
		p.add(input);
		inputDir = new TextField(Prefs.get("batch.input", ""), 45);
		p.add(inputDir);
		gd.addPanel(p);
		p = new Panel();
    	p.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
		output = new Button("Output...");
		output.addActionListener(this);
		p.add(output);
		outputDir = new TextField(Prefs.get("batch.output", ""), 45);
		p.add(outputDir);
		gd.addPanel(p);
	}
	
	void addTestButton(GenericDialog gd) {
		Panel p = new Panel();
    	p.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
		test = new Button("Test");
		test.addActionListener(this);
		p.add(test);
		gd.addPanel(p);
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source==input) {
			String path = IJ.getDirectory("Input Folder");
			if (path==null) return;
			inputDir.setText(path);
		} else if (source==output) {
			String path = IJ.getDirectory("Output Folder");
			if (path==null) return;
			outputDir.setText(path);
		} else {
			thread = new Thread(this, "BatchTest"); 
			thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
			thread.start();
		}
		if (source!=test && IJ.isMacOSX())
			{gd.setVisible(false); gd.setVisible(true);}
	}
	
	public void run() {
		TextArea ta = gd.getTextArea1();
		ta.selectAll();
		String macro = ta.getText();
		String inputPath = inputDir.getText();
		File f1 = new File(inputPath);
		if (!f1.exists() || !f1.isDirectory()) {
			IJ.error("Batch Processer", "Input does not exist or is not a folder\n \n"+inputPath);
			return;
		}
		if (macro.equals("")) {
			IJ.error("Batch Processer", "There is no macro code in the text area");
			return;
		}
		String[] list = (new File(inputPath)).list();
		String name = list[0];
		if (name.startsWith(".")&&list.length>1) name = list[1];
		String path = inputPath + name;
		ImagePlus imp = IJ.openImage(path);
		if (imp==null) return;
		WindowManager.setTempCurrentImage(imp);
		String str = IJ.runMacro(macro, "");
		if (testImage!=0) {
			ImagePlus imp2 = WindowManager.getImage(testImage);
			if (imp2!=null)
				{imp2.changes=false; imp2.close();}
		}
		imp.show();
		ImageWindow iw = imp.getWindow();
		if (iw!=null) iw.setLocation(10, 30);
		testImage = imp.getID();
	}

}
