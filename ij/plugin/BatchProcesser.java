package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.util.Tools;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Vector;

/** This plugin implements the File/Batch/Convert command, 
	which converts the images in a folder to a specified format. */
	public class BatchProcesser implements PlugIn, ActionListener, ItemListener, Runnable {
		private static final String MACRO_FILE_NAME = "BatchMacro.ijm";
		private static final String[] formats = {"TIFF", "8-bit TIFF", "JPEG", "GIF", "PNG", "PGM", "BMP", "FITS", "Text Image", "ZIP", "Raw"};
		private static String format = Prefs.get("batch.format", formats[0]);
		private static final String[] code = {
			"[Select from list]",
			"Border",
			"Convert to RGB",
			"Crop",
			"Invert",
			"Measure",
			"Resize",
			"Text"
		};
		private static String macro = "";
		private static int testImage;
		private Button input, output, open, save, test;
		private TextField inputDir, outputDir;
		private GenericDialog gd;
		private Thread thread;

	public void run(String arg) {
		String macroPath = IJ.getDirectory("macros")+MACRO_FILE_NAME;
		macro = IJ.openAsString(macroPath);
		if (macro==null || macro.startsWith("Error: ")) {
			IJ.showStatus(macro.substring(7) + ": "+macroPath);
			macro = "";
		}
		if (!showDialog()) return;
		String inputPath = inputDir.getText();
		if (inputPath.equals("")) {
			error("Please choose an input folder");
			return;
		}
		File f1 = new File(inputPath);
		if (!f1.exists() || !f1.isDirectory()) {
			error("Input does not exist or is not a folder\n \n"+inputPath);
			return;
		}
		String outputPath = outputDir.getText();
		File f2 = new File(outputPath);
		if (!outputPath.equals("") && (!f2.exists() || !f2.isDirectory())) {
			error("Output does not exist or is not a folder\n \n"+outputPath);
			return;
		}
		if (macro.equals("")) {
			error("There is no macro code in the text area");
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
		Prefs.set("batch.format", format);
		macro = gd.getTextArea1().getText();
		if (!macro.equals(""))
			IJ.saveString(macro, IJ.getDirectory("macros")+MACRO_FILE_NAME);
	}
		
	boolean showDialog() {
		validateFormat();
		gd = new GenericDialog("Batch Process");
		addPanels(gd);
		gd.setInsets(15, 0, 5);
		gd.addChoice("Output Format:", formats, format);
		gd.setInsets(0, 0, 5);
		gd.addChoice("Add Macro Code:", code, code[0]);
		gd.setInsets(15, 10, 0);
		gd.addTextAreas(macro, null, 12, 55);
		addButtons(gd);
		gd.setOKLabel("Process");
		Vector choices = gd.getChoices();
		Choice choice = (Choice)choices.elementAt(1);
		choice.addItemListener(this);
		gd.showDialog();
		format = gd.getNextChoice();
		macro = gd.getNextText();
		return !gd.wasCanceled();
	}
	
	void validateFormat() {
		boolean validFormat = false;
		for (int i=0; i<formats.length; i++) {
			if (format.equals(formats[i])) {
				validFormat = true;
				break;
			}
		}
		if (!validFormat) format = formats[0];
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
	
	void addButtons(GenericDialog gd) {
		Panel p = new Panel();
    	p.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
		test = new Button("Test");
		test.addActionListener(this);
		p.add(test);
		open = new Button("Open...");
		open.addActionListener(this);
		p.add(open);
		save = new Button("Save...");
		save.addActionListener(this);
		p.add(save);
		gd.addPanel(p);
	}

	public void itemStateChanged(ItemEvent e) {
		Choice choice = (Choice)e.getSource();
		String item = choice.getSelectedItem();
		String code = null;
		if (item.equals("Convert to RGB"))
			code = "run(\"RGB Color\");\n";
		else if (item.equals("Measure"))
			code = "run(\"Measure\");\n";
		else if (item.equals("Resize"))
			code = "run(\"Size...\", \"width=0 height=480 constrain interpolation=Bicubic\");\n";
		else if (item.equals("Text"))
			code = "setFont(\"SansSerif\", 18, \"antialiased\");\nsetColor(\"red\");\ndrawString(\"Hello\", 20, 30);\n";
		else if (item.equals("Crop"))
			code = "makeRectangle(getWidth/4, getHeight/4, getWidth/2, getHeight/2)\";\nrun(\"Crop\");\n";
		else if (item.equals("Border"))
			code = "run(\"Canvas Size...\", \"width=\"+getWidth+50+\" height=\"\n   +getHeight+50+\" position=Center zero\");\n";
		else if (item.equals("Invert"))
			code = "run(\"Invert\");\n";
		if (code!=null) {
			TextArea ta = gd.getTextArea1();
			ta.insert(code, ta.getCaretPosition());
		}
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source==input) {
			String path = IJ.getDirectory("Input Folder");
			if (path==null) return;
			inputDir.setText(path);
			if (IJ.isMacOSX())
				{gd.setVisible(false); gd.setVisible(true);}
		} else if (source==output) {
			String path = IJ.getDirectory("Output Folder");
			if (path==null) return;
			outputDir.setText(path);
			if (IJ.isMacOSX())
				{gd.setVisible(false); gd.setVisible(true);}
		} else if (source==test) {
			thread = new Thread(this, "BatchTest"); 
			thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
			thread.start();
		} else if (source==open)
			open();
		else if (source==save)
			save();
	}
	
	void open() {
		String text = IJ.openAsString("");
		if (text==null) return;
		if (text.startsWith("Error: "))
			error(text.substring(7));
		else {
			if (text.length()>30000)
				error("File is too large");
			else
				gd.getTextArea1().setText(text);
		}
	}
	
	void save() {
		macro = gd.getTextArea1().getText();
		if (!macro.equals(""))
			IJ.saveString(macro, "");
	}

	void error(String msg) {
		IJ.error("Batch Processer", msg);
	}
	
	public void run() {
		TextArea ta = gd.getTextArea1();
		//ta.selectAll();
		String macro = ta.getText();
		String inputPath = inputDir.getText();
		File f1 = new File(inputPath);
		if (!f1.exists() || !f1.isDirectory()) {
			error("Input does not exist or is not a folder\n \n"+inputPath);
			return;
		}
		if (macro.equals("")) {
			error("There is no macro code in the text area");
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
