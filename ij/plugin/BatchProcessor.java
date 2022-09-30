package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.util.Tools;
import ij.io.*;
import ij.macro.Interpreter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Vector;

/** This plugin implements the File/Batch/Macro and File/Batch/Virtual Stack commands. */
	public class BatchProcessor implements PlugIn, ActionListener, ItemListener, Runnable {
		private static final String MACRO_FILE_NAME = "BatchMacro.ijm";
		private static final String[] formats = {"TIFF", "8-bit TIFF", "JPEG", "GIF", "PNG", "PGM", "BMP", "FITS", "Text Image", "ZIP", "Raw"};
		private static String format = Prefs.get("batch.format", formats[0]);
		
		private static final String[] code = {
			"[Select from list]",
			"Add Border",
			"Convert to RGB",
			"Crop",
			"Gaussian Blur",
			"Invert",
			"Label",
			"Timestamp",
			"Max Dimension",
			"Measure",
			"Print Index and Title",
			"Resize",
			"Scale",
			"Show File Info",
			"Unsharp Mask",
		};
		
		private static final String help = "<html>"
		+"<h1>Process&gt;Batch&gt;Virtual Stack</h1>"
		+"<font size=+1>"
		+"This command runs macro code on each image in a virtual stack.<br>"
		+"The processed images are saved in the <i>Output</i> folder,<br>"
		+"in the specified <i>Format</i>, allowing them to be opened as a<br>"
		+"virtual stack. Make sure the <i>Output</i> folder is empty<br>"
		+"before clicking on <i>Process</i>.<br>"
		+"<br>"
		+"In the macro code, the 'i' (slice index) and 'n' (stack size) variables<br>"
		+"are predefined. Call <i>setOption('SaveBatchOutput',false)</i> to<br>"
		+"prevent the image currently being processed from being saved,<br>"
		+"effectively removing it from the output virtual stack.<br> <br>"
		+"</font>";

		private String macro = "";
		private int testImage;
		private Button input, output, open, save, test;
		private TextField inputDir, outputDir;
		private GenericDialog gd;
		private Thread thread;
		private ImagePlus virtualStack;
		private ImagePlus outputImage;
		private boolean errorDisplayed;
		private String filter;
		private static boolean saveOutput = true;

	public void run(String arg) {
		if (arg.equals("stack")) {
			virtualStack = IJ.getImage();
			if (virtualStack.getStackSize()==1) {
				error("This command requires a stack or virtual stack.");
				return;
			}
		}
		String macroPath = IJ.getDirectory("macros")+MACRO_FILE_NAME;
		macro = IJ.openAsString(macroPath);
		if (macro==null || macro.startsWith("Error: ")) {
			IJ.showStatus(macro.substring(7) + ": "+macroPath);
			macro = "";
		}
		if (!showDialog()) return;
		String inputPath = null;
		if (virtualStack==null) {
			inputPath = inputDir.getText();
			if (inputPath.equals("")) {
				error("Please choose an input folder");
				return;
			}
			inputPath = addSeparator(inputPath);
			File f1 = new File(inputPath);
			if (!f1.exists() || !f1.isDirectory()) {
				error("Input does not exist or is not a folder\n \n"+inputPath);
				return;
			}
		}
		String outputPath = outputDir.getText();
		outputPath = addSeparator(outputPath);
		File f2 = new File(outputPath);
		if (!outputPath.equals("") && (!f2.exists() || !f2.isDirectory())) {
			error("Output does not exist or is not a folder\n \n"+outputPath);
			return;
		}
		if (macro.equals("")) {
			error("There is no macro code in the text area");
			return;
		}
		ImageJ ij = IJ.getInstance();
		if (ij!=null) ij.getProgressBar().setBatchMode(true);
		IJ.resetEscape();
		if (virtualStack!=null)
			processVirtualStack(outputPath);
		else
			processFolder(inputPath, outputPath);
		IJ.showProgress(1,1);
		if (virtualStack==null)
			Prefs.set("batch.input", inputDir.getText());
		Prefs.set("batch.output", outputDir.getText());
		Prefs.set("batch.format", format);
		macro = gd.getTextArea1().getText();
		if (!macro.equals(""))
			IJ.saveString(macro, IJ.getDirectory("macros")+MACRO_FILE_NAME);
	}
		
	boolean showDialog() {
		validateFormat();
		gd = GUI.newNonBlockingDialog("Batch Process");
		addPanels(gd);
		gd.setInsets(15, 0, 5);
		gd.addChoice("Output_format:", formats, format);
		gd.setInsets(0, 0, 5);
		gd.addChoice("Add macro code:", code, code[0]);
		if (virtualStack==null)
			gd.addStringField("File name contains:", "", 10);
		gd.setInsets(15, 10, 0);
		Dimension screen = IJ.getScreenSize();
		gd.addTextAreas(macro, null, screen.width<=600?10:15, 60);
		addButtons(gd);
		gd.setOKLabel("Process");
		Vector choices = gd.getChoices();
		Choice choice = (Choice)choices.elementAt(1);
		if (virtualStack!=null)
			gd.addHelp(help);
		choice.addItemListener(this);
		gd.showDialog();
		format = gd.getNextChoice();
		if (virtualStack==null)
			filter = gd.getNextString();
		macro = gd.getNextText();
		return !gd.wasCanceled();
	}
	
	void processVirtualStack(String outputPath) {
		ImageStack stack = virtualStack.getStack();
		int n = stack.size();
		int index = 0;
		for (int i=1; i<=n; i++) {
			if (IJ.escapePressed()) break;
			IJ.showProgress(i, n);
			ImageProcessor ip = stack.getProcessor(i);
			if (ip==null) return;
			ImagePlus imp = new ImagePlus(i+"/"+stack.size(), ip);
			if (!macro.equals("")) {
				if (!runMacro("i="+(index++)+";"+"n="+stack.size()+";"+macro, imp))
					break;
			}
			if (saveOutput && !outputPath.equals("")) {
				if (format.equals("8-bit TIFF") || format.equals("GIF")) {
					if (imp.getBitDepth()==24)
						IJ.run(imp, "8-bit Color", "number=256");
					else
						IJ.run(imp, "8-bit", "");
				}
				IJ.saveAs(imp, format, outputPath+pad(i));
			}
			saveOutput = true;
			imp.close();
		}
		if (outputPath!=null && !outputPath.equals(""))
			IJ.run("Image Sequence...", "open=[" + outputPath + "]"+" use");
	}
	
	String pad(int n) {
		String str = ""+n;
		while (str.length()<5)
		str = "0" + str;
		return str;
	}

	
	void processFolder(String inputPath, String outputPath) {
		String[] list = (new File(inputPath)).list();
		list = FolderOpener.getFilteredList(list, filter, "Batch Processor");
		if (list==null)
			return;
		int index = 0;
		int startingCount = WindowManager.getImageCount();
		for (int i=0; i<list.length; i++) {
			if (IJ.escapePressed()) break;
			String path = inputPath + list[i];
			if (IJ.debugMode) IJ.log(i+": "+path);
			if ((new File(path)).isDirectory())
				continue;
			if (list[i].startsWith(".")||list[i].endsWith(".avi")||list[i].endsWith(".AVI") || list[i].equals("Thumbs.db"))
				continue;
			IJ.showProgress(i+1, list.length);
			IJ.redirectErrorMessages(true);
			ImagePlus imp = IJ.openImage(path);
			IJ.redirectErrorMessages(false);
			if (imp==null && WindowManager.getImageCount()>startingCount)
				imp = WindowManager.getCurrentImage();
			if (imp==null)
				imp = Opener.openUsingBioFormats(path);
			if (imp==null) {
				IJ.log("openImage() and openUsingBioFormats() returned null: "+path);
				continue;
			}
			if (!macro.equals("")) {
				outputImage = null;
				if (!runMacro("i="+(index++)+";"+macro, imp))
					break;
			}
			if (saveOutput && !outputPath.equals("")) {
				if (format.equals("8-bit TIFF") || format.equals("GIF")) {
					if (imp.getBitDepth()==24)
						IJ.run(imp, "8-bit Color", "number=256");
					else
						IJ.run(imp, "8-bit", "");
				}
				if (outputImage!=null && outputImage!=imp)
					IJ.saveAs(outputImage, format, outputPath+list[i]);
				else
					IJ.saveAs(imp, format, outputPath+list[i]);
			}
			saveOutput = true;
			imp.close();
		}
	}
	
	private boolean runMacro(String macro, ImagePlus imp) {
		WindowManager.setTempCurrentImage(imp);
		Interpreter interp = new Interpreter();
		try {
			outputImage = interp.runBatchMacro(macro, imp);
		} catch(Throwable e) {
			interp.abortMacro();
			String msg = e.getMessage();
			if (!(e instanceof RuntimeException && msg!=null && e.getMessage().equals(Macro.MACRO_CANCELED)))
				IJ.handleException(e);
			return false;
		} finally {
			WindowManager.setTempCurrentImage(null);
		}
		return true;
	}
		
	String addSeparator(String path) {
		if (path.equals("")) return path;
		if (!(path.endsWith("/")||path.endsWith("\\")))
			path = path + File.separator;
		return path;
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
		if (virtualStack==null) {
			input = new Button("Input...");
			input.addActionListener(this);
			p.add(input);
			inputDir = new TextField(Prefs.get("batch.input", ""), 45);
			p.add(inputDir);
			gd.addPanel(p);
		}
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
			code = "run(\"Size...\", \"width=512 height=512 interpolation=Bicubic\");\n";
		else if (item.equals("Scale"))
			code = "scale=1.5;\nw=getWidth*scale; h=getHeight*scale;\nrun(\"Size...\", \"width=w height=h interpolation=Bilinear\");\n";
		else if (item.equals("Label"))
			code = "setFont(\"SansSerif\", 18, \"antialiased\");\nsetColor(\"red\");\ndrawString(\"Hello\", 20, 30);\n";
		else if (item.equals("Timestamp"))
			code = openMacroFromJar("TimeStamp.ijm");
		else if (item.equals("Crop"))
			code = "makeRectangle(getWidth/4, getHeight/4, getWidth/2, getHeight/2);\nrun(\"Crop\");\n";
		else if (item.equals("Add Border"))
			code = "border=25;\nw=getWidth+border*2; h=getHeight+border*2;\nrun(\"Canvas Size...\", \"width=w height=h position=Center zero\");\n";
		else if (item.equals("Invert"))
			code = "run(\"Invert\");\n";
		else if (item.equals("Gaussian Blur"))
			code = "run(\"Gaussian Blur...\", \"sigma=2\");\n";
		else if (item.equals("Unsharp Mask"))
			code = "run(\"Unsharp Mask...\", \"radius=1 mask=0.60\");\n";
		else if (item.equals("Show File Info"))
			code = "path=File.directory+File.name;\ndate=File.dateLastModified(path);\nsize=File.length(path);\nprint(i+\", \"+getTitle+\", \"+date+\", \"+size);\n";
		else if (item.equals("Max Dimension"))
			code = "max=2048;\nw=getWidth; h=getHeight;\nsize=maxOf(w,h);\nif (size>max) {\n  scale = max/size;\n  w*=scale; h*=scale;\n  run(\"Size...\", \"width=w height=h interpolation=Bicubic average\");\n}";
		else if (item.equals("Print Index and Title"))
			code =  "if (i==0) print(\"\\\\Clear\"); print(IJ.pad(i,4)+\": \"+getTitle());\n";
		if (code!=null) {
			TextArea ta = gd.getTextArea1();
			ta.insert(code, ta.getCaretPosition());
			if (IJ.isMacOSX()) ta.requestFocus();
		}
	}

	public static String openMacroFromJar(String name) {
		ImageJ ij = IJ.getInstance();
		Class c = ij!=null?ij.getClass():(new ImageStack()).getClass();
		String macro = null;
        try {
			InputStream is = c .getResourceAsStream("/macros/"+name);
			if (is==null) return null;
            InputStreamReader isr = new InputStreamReader(is);
            StringBuffer sb = new StringBuffer();
            char [] b = new char [8192];
            int n;
            while ((n = isr.read(b)) > 0)
                sb.append(b,0, n);
            macro = sb.toString();
        }
        catch (IOException e) {
        	return null;
        }
        return macro;
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
		IJ.error("Batch Processor", msg);
	}
	
	public void run() {
		TextArea ta = gd.getTextArea1();
		//ta.selectAll();
		String macro = ta.getText();
		if (macro.equals("")) {
			error("There is no macro code in the text area");
			return;
		}
		ImagePlus imp = null;
		IJ.redirectErrorMessages(true);
		if (virtualStack!=null)
			imp = getVirtualStackImage();
		else
			imp = getFolderImage();
		IJ.redirectErrorMessages(false);
		if (imp==null) {
			if (!errorDisplayed)
				IJ.log("IJ.openImage() returned null");
			return;
		}
		runMacro("i=0;"+macro, imp);
		Point loc = new Point(10, 30);
		if (testImage!=0) {
			ImagePlus imp2 = WindowManager.getImage(testImage);
			if (imp2!=null) {
				ImageWindow win = imp2.getWindow();
				if (win!=null) loc = win.getLocation();
				imp2.changes=false;
				imp2.close();
			}
		}
		imp.show();
		ImageWindow iw = imp.getWindow();
		if (iw!=null) iw.setLocation(loc);
		testImage = imp.getID();
	}
	
	ImagePlus getVirtualStackImage() {
		ImagePlus imp = virtualStack.createImagePlus();
		imp.setProcessor("", virtualStack.getProcessor().duplicate());
		return imp;
	}

	ImagePlus getFolderImage() {
		String inputPath = inputDir.getText();
		inputPath = addSeparator(inputPath);
		File f1 = new File(inputPath);
		if (!f1.exists() || !f1.isDirectory()) {
			error("Input does not exist or is not a folder\n \n"+inputPath);
			errorDisplayed = true;
			return null;
		}
		String[] list = (new File(inputPath)).list();
		String name = list[0];
		if (name.startsWith(".")&&list.length>1) name = list[1];
		String path = inputPath + name;
		setDirAndName(path);
		return IJ.openImage(path);
	}
	
	void setDirAndName(String path) {
		File f = new File(path);
		OpenDialog.setLastDirectory(f.getParent()+File.separator);
		OpenDialog.setLastName(f.getName());
	}
	
	public static void saveOutput(boolean b) {
		saveOutput = b;
	}

}
