package ij.plugin.frame;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.image.*;
import java.util.*;
import java.awt.event.*;
import ij.measure.*;
import ij.plugin.*;
import ij.plugin.filter.ThresholdToSelection;


/*	This plugin isolates pixels in an RGB image or stack according to a range of Hue.
	Original PassBand2 by Bob Dougherty. Some code borrowed from ThresholdAdjuster by Wayne Rasband.

	Version 0 5/12/2002.
	Version 1 5/13			Filtered pixels set to foreground color.  Speed improved.
	Version 2 5/13.			Fixed a bug in setting the restore pixels that was causing problems with stacks.
							Explicitly get the foreground color from the toolbar in apply.

	Modifications by G. Landini.
		17/Feb/2004.	The changes are seen as the sliders/checkboxes are adjusted.
				Added hue strip to histogram window, changed histogram scale factor
		19/Feb/2004.	Added Saturation and Brightness histograms,
				Added Pass/Stop checkboxes for each HSB channel.
				Added threshold, added inversion of threshold
				Cleaned some variables. Changed name to Threshold_HSB
		22/Feb/2004	Threshold in RGB or HSB space
				Changed name to Threshold_Colour, changed button names.
				Added thresholding by "sampling". Hue band sampled selection may not'
				always work if there are 0 valued histograms. Thread now finishes properly
		23/Feb/2004	Java 1.4 on Mac OS X bug (thanks Wayne)
		25/Feb/2004	Any type of ROI supported for [Sample]
		26/Feb/2004	Modified ROI handling (thanks Wayne)
		28/Feb/2004	Improved Hue sampling, changed histogram background colour
		29/Feb/2004	Added CIE Lab colour space
		6/Mar/2004	v1.4 Requires ImageJ 1.32c (thanks Wayne)
		8/Mar/2004	v1.5 ColourProcessor bug (thanks Wayne), filter checkboxes detect a new image
		23/Jun/2004	v1.6 Added YUV colour space
		1/May/2006	v1.7 Minor changes to Lab coding, added macro recorder button
		5/Jan/2007	v1.8 added warning and commented lines for back/foreground colours
		2/Feb/2008	v1.9 closing does not apply the filter if Original was being displayed. Thanks for the hint Bob!
 */

/**  Selects pixels according to hsb or rgb components.  */
public class ColorThresholder extends PlugInFrame implements PlugIn, Measurements,
 ActionListener, AdjustmentListener, FocusListener, ItemListener, Runnable{

	private static final int HSB=0, RGB=1, LAB=2, YUV=3;
	private static final String[] colorSpaces = {"HSB", "RGB", "Lab", "YUV"};
	private boolean flag = false;
	private int colorSpace = HSB;
	private Thread thread;
	private static Frame instance;

	private BandPlot plot = new BandPlot();
	private BandPlot splot = new BandPlot();
	private BandPlot bplot = new BandPlot();
	private int sliderRange = 256;
	private Panel panel, panelt;
	private Button  originalB, filteredB, stackB, helpB, sampleB, resetallB, newB, macroB, selectB;
	private Checkbox bandPassH, bandPassS, bandPassB, darkBackground;
	private CheckboxGroup colourMode;
	private Choice colorSpaceChoice, methodChoice, modeChoice;
	private int previousImageID = -1;
	private int previousSlice = -1;
	private ImageJ ij;
	private int minHue = 0, minSat = 0, minBri = 0;
	private int maxHue = 255, maxSat = 255, maxBri = 255;
	private Scrollbar minSlider, maxSlider, minSlider2, maxSlider2, minSlider3, maxSlider3;
	private Label label1, label2, label3, label4, label5, label6, labelh, labels, labelb, labelf;
	private boolean done;
	private byte[] hSource, sSource, bSource;
	private boolean applyingStack;

	private static final int DEFAULT = 0;
	private static String[] methodNames = AutoThresholder.getMethods();
	private static String method = methodNames[DEFAULT];
	private static AutoThresholder thresholder = new AutoThresholder();
	private static final int RED=0, WHITE=1, BLACK=2, BLACK_AND_WHITE=3;
	private static final String[] modes = {"Red", "White", "Black", "B&W"};
	private static int mode = RED;	

	private int numSlices;
	private ImageStack stack;
	private int width, height, numPixels;

	public ColorThresholder() {
		super("Threshold Color");
		if (instance!=null) {
			WindowManager.toFront(instance);
			return;
		}
		thread = new Thread(this, "BandAdjuster");
		WindowManager.addWindow(this);
		instance = this;

		ij = IJ.getInstance();
		Font font = IJ.font10;
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();
		setLayout(gridbag);

		int y = 0;
		c.gridx = 0;
		c.gridy = y;
		c.gridwidth = 1;
		c.weightx = 0;
		c.insets = new Insets(5, 0, 0, 0);
		labelh = new Label("Hue", Label.CENTER);
		add(labelh, c);

		c.gridx = 1;
		c.gridy = y++;
		c.gridwidth = 1;
		c.weightx = 0;
		c.insets = new Insets(7, 0, 0, 0);
		labelf = new Label("", Label.RIGHT);
		add(labelf, c);

		// plot
		c.gridx = 0;
		c.gridy = y;
		c.gridwidth = 1;
		c.fill = c.BOTH;
		c.anchor = c.CENTER;
		c.insets = new Insets(0, 5, 0, 0);
		add(plot, c);

		// checkboxes
		bandPassH = new Checkbox("Pass");
		bandPassH.addItemListener(this);
		bandPassH.setState(true);
		c.gridx = 1;
		c.gridy = y++;
		c.gridwidth = 2;
		c.insets = new Insets(5, 5, 0, 5);
		add(bandPassH, c);

		// minHue slider
		minSlider = new Scrollbar(Scrollbar.HORIZONTAL, 0, 1, 0, sliderRange);
		GUI.fixScrollbar(minSlider);
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 1;
		c.weightx = IJ.isMacintosh()?90:100;
		c.fill = c.HORIZONTAL;
		c.insets = new Insets(5, 5, 0, 0);

		add(minSlider, c);
		minSlider.addAdjustmentListener(this);
		minSlider.setUnitIncrement(1);

		// minHue slider label
		c.gridx = 1;
		c.gridwidth = 1;
		c.weightx = IJ.isMacintosh()?10:0;
		c.insets = new Insets(5, 0, 0, 0);
		label1 = new Label("       ", Label.LEFT);
		label1.setFont(font);
		add(label1, c);

		// maxHue sliderHue
		maxSlider = new Scrollbar(Scrollbar.HORIZONTAL, 0, 1, 0, sliderRange);
		GUI.fixScrollbar(maxSlider);
		c.gridx = 0;
		c.gridy = y;
		c.gridwidth = 1;
		c.weightx = 100;
		c.insets = new Insets(5, 5, 0, 0);
		add(maxSlider, c);
		maxSlider.addAdjustmentListener(this);
		maxSlider.setUnitIncrement(1);

		// maxHue slider label
		c.gridx = 1;
		c.gridwidth = 1;
		c.gridy = y++;
		c.weightx = 0;
		c.insets = new Insets(5, 0, 0, 0);
		label2 = new Label("       ", Label.LEFT);
		label2.setFont(font);
		add(label2, c);

		//=====
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 1;
		c.weightx = 0;
		c.insets = new Insets(10, 0, 0, 0);
		labels = new Label("Saturation", Label.CENTER);
		add(labels, c);

		// plot
		c.gridx = 0;
		c.gridy = y;
		c.gridwidth = 1;
		c.fill = c.BOTH;
		c.anchor = c.CENTER;
		c.insets = new Insets(0, 5, 0, 0);
		add(splot, c);

		// checkboxes
		bandPassS = new Checkbox("Pass");
		bandPassS.addItemListener(this);
		bandPassS.setState(true);
		c.gridx = 1;
		c.gridy = y++;
		c.gridwidth = 2;
		c.insets = new Insets(5, 5, 0, 5);
		add(bandPassS, c);

		// minSat slider
		minSlider2 = new Scrollbar(Scrollbar.HORIZONTAL, 0, 1, 0, sliderRange);
		GUI.fixScrollbar(minSlider2);
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 1;
		c.weightx = IJ.isMacintosh()?90:100;
		c.fill = c.HORIZONTAL;
		c.insets = new Insets(5, 5, 0, 0);
		add(minSlider2, c);
		minSlider2.addAdjustmentListener(this);
		minSlider2.setUnitIncrement(1);

		// minSat slider label
		c.gridx = 1;
		c.gridwidth = 1;
		c.weightx = IJ.isMacintosh()?10:0;
		c.insets = new Insets(5, 0, 0, 0);
		label3 = new Label("       ", Label.LEFT);
		label3.setFont(font);
		add(label3, c);

		// maxSat slider
		maxSlider2 = new Scrollbar(Scrollbar.HORIZONTAL, 0, 1, 0, sliderRange);
		GUI.fixScrollbar(maxSlider2);
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 1;
		c.weightx = 100;
		c.insets = new Insets(5, 5, 0, 0);
		add(maxSlider2, c);
		maxSlider2.addAdjustmentListener(this);
		maxSlider2.setUnitIncrement(1);

		// maxSat slider label
		c.gridx = 1;
		c.gridwidth = 1;
		c.weightx = 0;
		c.insets = new Insets(5, 0, 0, 0);
		label4 = new Label("       ", Label.LEFT);
		label4.setFont(font);
		add(label4, c);

		//=====
		c.gridx = 0;
		c.gridwidth = 1;
		c.gridy = y++;
		c.weightx = 0;
		c.insets = new Insets(10, 0, 0, 0);
		labelb = new Label("Brightness", Label.CENTER);
		add(labelb, c);

		c.gridx = 0;
		c.gridwidth = 1;
		c.gridy = y;
		c.fill = c.BOTH;
		c.anchor = c.CENTER;
		c.insets = new Insets(0, 5, 0, 0);
		add(bplot, c);

		// checkboxes
		bandPassB = new Checkbox("Pass");
		bandPassB.addItemListener(this);
		bandPassB.setState(true);
		c.gridx = 1;
		c.gridy = y++;
		c.gridwidth = 2;
		c.insets = new Insets(5, 5, 0, 5);
		add(bandPassB, c);

		// minBri slider
		minSlider3 = new Scrollbar(Scrollbar.HORIZONTAL, 0, 1, 0, sliderRange);
		GUI.fixScrollbar(minSlider3);
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 1;
		c.weightx = IJ.isMacintosh()?90:100;
		c.fill = c.HORIZONTAL;
		c.insets = new Insets(5, 5, 0, 0);
		add(minSlider3, c);
		minSlider3.addAdjustmentListener(this);
		minSlider3.setUnitIncrement(1);

		// minBri slider label
		c.gridx = 1;
		c.gridwidth = 1;
		c.weightx = IJ.isMacintosh()?10:0;
		c.insets = new Insets(5, 0, 0, 0);
		label5 = new Label("       ", Label.LEFT);
		label5.setFont(font);
		add(label5, c);

		// maxBri slider
		maxSlider3 = new Scrollbar(Scrollbar.HORIZONTAL, 0, 1, 0, sliderRange);
		GUI.fixScrollbar(maxSlider3);
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 1;
		c.weightx = 100;
		c.insets = new Insets(5, 5, 0, 0);
		add(maxSlider3, c);
		maxSlider3.addAdjustmentListener(this);
		maxSlider3.setUnitIncrement(1);

		// maxBri slider label
		c.gridx = 1;
		c.gridwidth = 1;
		c.weightx = 0;
		c.insets = new Insets(5, 0, 0, 0);
		label6 = new Label("       ", Label.LEFT);
		label6.setFont(font);
		add(label6, c);

		GridBagLayout gridbag2 = new GridBagLayout();
		GridBagConstraints c2 = new GridBagConstraints();
		int y2 = 0;
		Panel panel = new Panel();
		panel.setLayout(gridbag2);
		
		// threshoding method choice
		c2.gridx = 0; c2.gridy = y2;
		c2.anchor = GridBagConstraints.EAST;
		c2.gridwidth = 1;
		c2.insets = new Insets(5, 0, 0, 0);
		Label theLabel = new Label("Thresholding method:");
		gridbag2.setConstraints(theLabel, c2);
		panel.add(theLabel);
		methodChoice = new Choice();
		for (int i=0; i<methodNames.length; i++)
			methodChoice.addItem(methodNames[i]);
		methodChoice.select(method);
		methodChoice.addItemListener(this);
		c2.gridx = 1; c2.gridy = y2;
		c2.anchor = GridBagConstraints.WEST;
		gridbag2.setConstraints(methodChoice, c2);
		panel.add(methodChoice);
		y2++;
		
		// display mode choice
		c2.gridx = 0; c2.gridy = y2;
		c2.anchor = GridBagConstraints.EAST;
		c2.insets = new Insets(0, 0, 0, 0);
		theLabel = new Label("Threshold color:");
		gridbag2.setConstraints(theLabel, c2);
		panel.add(theLabel);
		modeChoice = new Choice();
		for (int i=0; i<modes.length; i++)
			modeChoice.addItem(modes[i]);
		modeChoice.select(mode);
		modeChoice.addItemListener(this);
		c2.gridx = 1; c2.gridy = y2;
		c2.anchor = GridBagConstraints.WEST;
		gridbag2.setConstraints(modeChoice, c2);
		panel.add(modeChoice);
		y2++;

		// color space choice
		c2.gridx = 0; c2.gridy = y2;
		c2.anchor = GridBagConstraints.EAST;
		theLabel = new Label("Color space:");
		gridbag2.setConstraints(theLabel, c2);
		panel.add(theLabel);
		colorSpaceChoice = new Choice();
		for (int i=0; i<colorSpaces.length; i++)
			colorSpaceChoice.addItem(colorSpaces[i]);
		colorSpaceChoice.select(HSB);
		colorSpaceChoice.addItemListener(this);
		c2.gridx = 1; c2.gridy = y2;
		c2.anchor = GridBagConstraints.WEST;
		gridbag2.setConstraints(colorSpaceChoice, c2);
		panel.add(colorSpaceChoice);
		y2++;

		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 2;
		c.insets = new Insets(5, 0, 0, 0);
		c.anchor = GridBagConstraints.CENTER;
		c.fill = GridBagConstraints.NONE;
		add(panel, c);

		//=====
		panelt = new Panel();
		boolean db = Prefs.get("cthresholder.dark", true);
		darkBackground = new Checkbox("Dark background", db);
		darkBackground.addItemListener(this);
		panelt.add(darkBackground);

		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 2;
		c.insets = new Insets(0, 0, 0, 0);
		add(panelt, c);

		// buttons
		int trim = IJ.isMacOSX()?10:0;
		panel = new Panel();
		panel.setLayout(new GridLayout(0, 4, 0, 0));
		originalB = new TrimmedButton("Original", trim);
		//originalB.setEnabled(false);
		originalB.addActionListener(this);
		originalB.addKeyListener(ij);
		panel.add(originalB);

		filteredB = new TrimmedButton("Filtered", trim);
		filteredB.setEnabled(false);
		filteredB.addActionListener(this);
		filteredB.addKeyListener(ij);
		panel.add(filteredB);

		selectB = new TrimmedButton("Select", trim);
		selectB.addActionListener(this);
		selectB.addKeyListener(ij);
		panel.add(selectB);

		sampleB = new TrimmedButton("Sample", trim);
		sampleB.addActionListener(this);
		sampleB.addKeyListener(ij);
		panel.add(sampleB);
		
		stackB = new TrimmedButton("Stack", trim);
		stackB.addActionListener(this);
		stackB.addKeyListener(ij);
		panel.add(stackB);

		macroB = new TrimmedButton("Macro", trim);
		macroB.addActionListener(this);
		macroB.addKeyListener(ij);
		panel.add(macroB);

		helpB = new TrimmedButton("Help", trim);
		helpB.addActionListener(this);
		helpB.addKeyListener(ij);
		panel.add(helpB);
		
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 2;
		c.insets = new Insets(5, 5, 10, 5);
		gridbag.setConstraints(panel, c);
		add(panel);

		addKeyListener(ij);  // ImageJ handles keyboard shortcuts
		GUI.scale(this);
		pack();
		GUI.centerOnImageJScreen(this);
		setVisible(true);

		thread.start();
		if (!checkImage()) return;
		synchronized(this) {
			notify();
		}
	}

	public void run() {
		while (!done) {
			synchronized(this) {
				try {wait();}
				catch(InterruptedException e) {}
				if (!done) {//RPD
					ImagePlus imp = WindowManager.getCurrentImage();
					if (imp!=null) {
						reset(imp);
						apply(imp);
						imp.updateAndDraw();
					}
				}
			}
		}
	}

	public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
    	if (IJ.debugMode) IJ.log("ColorThresholder.adjustmentValueChanged ");
		if (!checkImage()) return;
		if (e.getSource() == minSlider)
			adjustMinHue((int) minSlider.getValue());
		else if (e.getSource() == maxSlider)
			adjustMaxHue((int) maxSlider.getValue());
		else if (e.getSource() == minSlider2)
			adjustMinSat((int) minSlider2.getValue());
		else if (e.getSource() == maxSlider2)
			adjustMaxSat((int) maxSlider2.getValue());
		else if (e.getSource() == minSlider3)
			adjustMinBri((int) minSlider3.getValue());
		else if (e.getSource() == maxSlider3)
			adjustMaxBri((int) maxSlider3.getValue());
		//originalB.setEnabled(true);
		updateLabels();
		updatePlot();
		notify();
	}

	public synchronized void itemStateChanged(ItemEvent e) {
		if (IJ.debugMode) IJ.log("ColorThresolder.itemStateChanged");
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) return;
		Object source = e.getSource();
		if (source==methodChoice) {
			method = methodChoice.getSelectedItem();
		} else if (source==modeChoice) {
			mode = modeChoice.getSelectedIndex();
		} else if (source==colorSpaceChoice) {
			colorSpace = ((Choice)source).getSelectedIndex();
			flag = true;
			//originalB.setEnabled(false);
			filteredB.setEnabled(false);
			minHue=minSat=minBri=0;
			maxHue=maxSat=maxBri=255;
			bandPassH.setState(true);
			bandPassS.setState(true);
			bandPassB.setState(true);
		} else if (source==darkBackground) {
		}
		reset(imp);
		if (source==methodChoice || source==colorSpaceChoice || source==darkBackground)
			autoSetThreshold();
		checkImage(); //new
		updateNames();
		notify();
	}


	public void focusGained(FocusEvent e){
		if (IJ.debugMode) IJ.log("ColorThresolder.focusGained");
		checkImage();
	}

	public void focusLost(FocusEvent e){}

	public void actionPerformed(ActionEvent e) {
		if (IJ.debugMode) IJ.log("ColorThresholder.actionPerformed");
		Button b = (Button)e.getSource();
		if (b==null) return;
		boolean imageThere = b==sampleB || checkImage();
		if (imageThere) {
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp==null) return;
			if (b==originalB) {
				reset(imp);
				imp.setProperty("OriginalImage", null);
				filteredB.setEnabled(true);
			} else if (b==filteredB) {
				reset(imp);
				apply(imp);
			} else if (b==sampleB) {
				sample();
				apply(imp);
			} else if (b==selectB) {
				createSelection();
			} else if (b==stackB) {
				applyStack();
			} else if (b==macroB) {
				generateMacro();
				return;
			} else if (b==helpB) {
				showHelp();
				return;
			}
			updatePlot();
			updateLabels();
			imp.updateAndDraw();
		} else {
			IJ.beep();
			IJ.showStatus("No Image");
		}
		//notify();
	}
	
	//Select button pressed
	void createSelection() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) return;
		int saveMode = mode;
		mode = BLACK_AND_WHITE;
		apply(imp);
		mode = saveMode;
		ImageProcessor ip = imp.getProcessor().convertToByte(false);
		int fg = Prefs.blackBackground?255:0;
		ip.setThreshold(fg, fg, ImageProcessor.NO_LUT_UPDATE);
		Roi roi = (new ThresholdToSelection()).convert(ip);
		reset(imp);
		imp.setRoi(roi);
	}
	
	void generateMacro() {
		if (!Recorder.record) {
			IJ.error("Threshold Color", "Command recorder is not running");
			return;
		}
		Recorder.recordString("// Color Thresholder "+IJ.getVersion()+"\n");
		Recorder.recordString("// Autogenerated macro, single images only!\n");
		Recorder.recordString("min=newArray(3);\n");
		Recorder.recordString("max=newArray(3);\n");
		Recorder.recordString("filter=newArray(3);\n");
		Recorder.recordString("a=getTitle();\n");
		if (colorSpace==HSB) {
			Recorder.recordString("run(\"HSB Stack\");\n");
			Recorder.recordString("run(\"Convert Stack to Images\");\n");
			Recorder.recordString("selectWindow(\"Hue\");\n");
			Recorder.recordString("rename(\"0\");\n");
			Recorder.recordString("selectWindow(\"Saturation\");\n");
			Recorder.recordString("rename(\"1\");\n");
			Recorder.recordString("selectWindow(\"Brightness\");\n");
			Recorder.recordString("rename(\"2\");\n");
		} else {
			if (colorSpace==LAB)
				Recorder.recordString("call(\"ij.plugin.frame.ColorThresholder.RGBtoLab\");\n");
			if (colorSpace==YUV)
				Recorder.recordString("call(\"ij.plugin.frame.ColorThresholder.RGBtoYUV\");\n");
			Recorder.recordString("run(\"RGB Stack\");\n");
			Recorder.recordString("run(\"Convert Stack to Images\");\n");
			Recorder.recordString("selectWindow(\"Red\");\n");
			Recorder.recordString("rename(\"0\");\n");
			Recorder.recordString("selectWindow(\"Green\");\n");
			Recorder.recordString("rename(\"1\");\n");
			Recorder.recordString("selectWindow(\"Blue\");\n");
			Recorder.recordString("rename(\"2\");\n");
		}
		Recorder.recordString("min[0]="+minSlider.getValue()+";\n");
		Recorder.recordString("max[0]="+maxSlider.getValue()+";\n");

		if (bandPassH.getState())
			Recorder.recordString("filter[0]=\"pass\";\n");
		else
			Recorder.recordString("filter[0]=\"stop\";\n");

		Recorder.recordString("min[1]="+minSlider2.getValue()+";\n");
		Recorder.recordString("max[1]="+maxSlider2.getValue()+";\n");

		if (bandPassS.getState())
			Recorder.recordString("filter[1]=\"pass\";\n");
		else
			Recorder.recordString("filter[1]=\"stop\";\n");
		Recorder.recordString("min[2]="+minSlider3.getValue()+";\n");
		Recorder.recordString("max[2]="+maxSlider3.getValue()+";\n");

		if (bandPassB.getState())
			Recorder.recordString("filter[2]=\"pass\";\n");
		else
			Recorder.recordString("filter[2]=\"stop\";\n");

		Recorder.recordString("for (i=0;i<3;i++){\n");
		Recorder.recordString("  selectWindow(\"\"+i);\n");
		Recorder.recordString("  setThreshold(min[i], max[i]);\n");
		Recorder.recordString("  run(\"Convert to Mask\");\n");
		Recorder.recordString("  if (filter[i]==\"stop\")  run(\"Invert\");\n");
		Recorder.recordString("}\n");
		Recorder.recordString("imageCalculator(\"AND create\", \"0\",\"1\");\n");
		Recorder.recordString("imageCalculator(\"AND create\", \"Result of 0\",\"2\");\n");
		Recorder.recordString("for (i=0;i<3;i++){\n");
		Recorder.recordString("  selectWindow(\"\"+i);\n");
		Recorder.recordString("  close();\n");
		Recorder.recordString("}\n");
		Recorder.recordString("selectWindow(\"Result of 0\");\n");
		Recorder.recordString("close();\n");
		Recorder.recordString("selectWindow(\"Result of Result of 0\");\n");

		//if(invert.getState())
		//   Recorder.recordString("run(\"Invert\");\n");
		Recorder.recordString("rename(a);\n");
		Recorder.recordString("// Colour Thresholding-------------\n");
	}
	
	void showHelp() {
		IJ.showMessage("Help","Color Thresholder\n \n"+
			"Modification of Bob Dougherty's BandPass2 plugin by G.Landini\n"+
			"to threshold 24 bit RGB images based on HSB, RGB, CIE Lab \n"+
			"or YUV components.\n \n"+
			"[Pass]: Everything within range is thresholded, otherwise,\n"+
			"everything outside range is thresholded.\n \n"+
			"[Default] [Huang] [Intermodes] [etc.]: Selects the automatic\n"+
			"thresholding method.\n \n"+
			"[Red] [White] [Black] [B&W]: Selects the threshold color.\n \n"+
			"[Dark background]: Auto-thresholding methods assume\n"+
			"light features and dark background.\n \n"+
			"[Original]: Shows the original image and updates the buffer\n"+
			"when switching to another image.\n \n"+
			"[Filtered]: Shows the filtered image.\n \n"+
			"[Stack]: Processes the rest of the slices in the stack (if any)\n"+
			"using the current settings.\n \n"+
			"[Macro]: Creates a macro based on the current settings which\n"+
			"is sent to the macro Recorder window, if open.\n \n"+
			"[Sample]: (experimental) Sets the ranges of the filters based\n"+
			"on the pixel value components in a user-defined ROI.\n \n"+
			"[HSB] [RGB] [CIE Lab] [YUV]: Selects the color space.\n \n"+
			"");
	}

	void sample() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null || imp.getBitDepth()!=24)
			return;
		reset(imp);
		byte[] hsSource,ssSource,bsSource;

		int [] bin = new int[256];
		int counter=0, pi=0, rangePassH = 0, rangeStopH = 0, rangePassL = 0, rangeStopL = 0, i, j;
		int snumPixels=0;

		Roi roi = imp.getRoi();
		if (roi==null) {
			IJ.error("Selection required");
			return;
		}

		ImageProcessor mask = roi.getMask(); 

		Rectangle r = roi.getBoundingRect();
		//new ImagePlus("Mask", ipm).show(); // display the mask

		ImageProcessor ip = imp.getProcessor();

		// ROI size
		if (mask==null) { //rectangular
			snumPixels = r.width*r.height;
		}
		else{
			snumPixels=0;
			for (j=0; j<r.height; j++) {
				for (i=0; i<r.width; i++) {
					if (mask.getPixel(i,j)!=0) {
						snumPixels++;
					}
				}
			}
		}

		hsSource = new byte[snumPixels];
		ssSource = new byte[snumPixels];
		bsSource = new byte[snumPixels];
		int [] pixs = new int[snumPixels];

		// get pixel in ROI
		if (mask==null) {
			for (j=0; j<r.height; j++) {
				for (i=0; i<r.width; i++) {
					pixs[counter++] = ip.getPixel(i+r.x, j+r.y);
				}
			}
		}
		else{
			for (j=0; j<r.height; j++) {
				for (i=0; i<r.width; i++) {
					if (mask.getPixel(i,j)!=0) { //v1.32c onwards
						pixs[counter++] = ip.getPixel(i+r.x, j+r.y);
					}
				}
			}
		}
		imp.deleteRoi();

		//Get hsb or rgb from roi.
		//1pixel wide to fit all pixels of a non-square ROI in a ColorProcessor:
		ColorProcessor cp2 = new ColorProcessor(1, snumPixels, pixs);

		int iminhue=256, imaxhue=-1, iminsat=256, imaxsat=-1, iminbri=256, imaxbri=-1;
		int iminred=256, imaxred=-1, imingre=256, imaxgre=-1, iminblu=256, imaxblu=-1;

		if(colorSpace==RGB)
			cp2.getRGB(hsSource,ssSource,bsSource);
		else if(colorSpace==HSB)
			cp2.getHSB(hsSource,ssSource,bsSource);
		else if(colorSpace==LAB)
			getLab(cp2, hsSource,ssSource,bsSource);
		else if(colorSpace==YUV)
			getYUV(cp2, hsSource,ssSource,bsSource);


		for (i = 0; i < snumPixels; i++){
			bin[hsSource[i]&255]=1;
			if ((hsSource[i]&255)>imaxhue) imaxhue=(hsSource[i]&255);
			if ((hsSource[i]&255)<iminhue) iminhue=(hsSource[i]&255);
			if ((ssSource[i]&255)>imaxsat) imaxsat=(ssSource[i]&255);
			if ((ssSource[i]&255)<iminsat) iminsat=(ssSource[i]&255);
			if ((bsSource[i]&255)>imaxbri) imaxbri=(bsSource[i]&255);
			if ((bsSource[i]&255)<iminbri) iminbri=(bsSource[i]&255);
			//IJ.showMessage("h:"+iminhue+"H:"+imaxhue+"s:"+iminsat+"S:"+imaxsat+"b:"+iminbri+"B:"+imaxbri);
		}

		if(colorSpace==HSB){ // get pass or stop filter whichever has a narrower range
			int gap=0, maxgap=0, maxgapst=-1, maxgapen=-1, gapst=0 ;

			if (bin[0]==0){
				gapst=0;
				gap=1;
			}

			for (i = 1; i < 256; i++){
				//System.out.println("i:"+i+" bin:"+bin[i]);
				if (bin[i]==0){
					if (bin[i-1]>0){
						gap=1;
						gapst=i;
					}
					else {
						gap++;
					}
					if (gap>maxgap){
						maxgap=gap;
						maxgapst=gapst;
						maxgapen=i;
					}
				}
			}

			for (i = 0; i < 256; i++){
				if (bin[i]>0){
					rangePassL = i;
					break;
				}
			}
			for (i = 255; i >= 0; i--){
				if (bin[i]>0){
					rangePassH = i;
					break;
				}
			}
			if ((rangePassH-rangePassL)<maxgap){
				bandPassH.setState(true);
				iminhue=rangePassL;
				imaxhue=rangePassH;
			}
			else{
				bandPassH.setState(false);
				iminhue=maxgapst;
				imaxhue=maxgapen;
			}
		}
		else {
			bandPassH.setState(true);
		}

		adjustMinHue(iminhue);
		minSlider.setValue(iminhue);
		adjustMaxHue(imaxhue);
		maxSlider.setValue(imaxhue);
		adjustMinSat(iminsat);
		minSlider2.setValue(iminsat);
		adjustMaxSat(imaxsat);
		maxSlider2.setValue(imaxsat);
		adjustMinBri(iminbri);
		minSlider3.setValue(iminbri);
		adjustMaxBri(imaxbri);
		maxSlider3.setValue(imaxbri);
		//originalB.setEnabled(true);
		//IJ.showStatus("done");
	}


	private boolean checkImage() {
		if (IJ.debugMode) IJ.log("ColorThresholder.checkImage");
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null || imp.getBitDepth()!=24) {
			IJ.beep();
			IJ.showStatus("No RGB image");
			return false;
		}
		imp.deleteRoi();
		boolean ok = setup(imp);
		return ok;
	}

	boolean setup(ImagePlus imp) {
		if (IJ.debugMode) IJ.log("ColorThresholder.setup");
		ImageProcessor ip;
		int type = imp.getType();
		if (type!=ImagePlus.COLOR_RGB)
			return false;
		ip = imp.getProcessor();
		int id = imp.getID();
		int slice = imp.getCurrentSlice();
		if ((id!=previousImageID)||(slice!=previousSlice)||(flag) ) {
			Undo.reset();
			flag = false; //if true, flags a change of colour model
			numSlices = imp.getStackSize();
			stack = imp.getStack();
			width = stack.getWidth();
			height = stack.getHeight();
			numPixels = width*height;

			hSource = new byte[numPixels];
			sSource = new byte[numPixels];
			bSource = new byte[numPixels];
			
			ImageProcessor mask = new ByteProcessor(width, height);
			imp.setProperty("Mask", mask);

			//Get hsb or rgb from image.
			ColorProcessor cp = (ColorProcessor)ip;
			IJ.showStatus("Converting colour space...");
			if(colorSpace==RGB)
				cp.getRGB(hSource,sSource,bSource);
			else if(colorSpace==HSB)
				cp.getHSB(hSource,sSource,bSource);
			else if(colorSpace==LAB)
				getLab(cp, hSource,sSource,bSource);
			else if(colorSpace==YUV)
				getYUV(cp, hSource,sSource,bSource);

			IJ.showStatus("");

			//Create a spectrum ColorModel for the Hue histogram plot.
			Color c;
			byte[] reds = new byte[256];
			byte[] greens = new byte[256];
			byte[] blues = new byte[256];
			for (int i=0; i<256; i++) {
				c = Color.getHSBColor(i/255f, 1f, 1f);
				reds[i] = (byte)c.getRed();
				greens[i] = (byte)c.getGreen();
				blues[i] = (byte)c.getBlue();
			}
			ColorModel cm = new IndexColorModel(8, 256, reds, greens, blues);

			//Make an image with just the hue from the RGB image and the spectrum LUT.
			//This is just for a hue histogram for the plot.  Do not show it.
			//ByteProcessor bpHue = new ByteProcessor(width,height,h,cm);
			ByteProcessor bpHue = new ByteProcessor(width,height,hSource,cm);
			ImagePlus impHue = new ImagePlus("Hue",bpHue);
			//impHue.show();

			ByteProcessor bpSat = new ByteProcessor(width,height,sSource,cm);
			ImagePlus impSat = new ImagePlus("Sat",bpSat);
			//impSat.show();

			ByteProcessor bpBri = new ByteProcessor(width,height,bSource,cm);
			ImagePlus impBri = new ImagePlus("Bri",bpBri);
			//impBri.show();

			plot.setHistogram(impHue, 0);
			splot.setHistogram(impSat, 1);
			bplot.setHistogram(impBri, 2);

			if (!applyingStack)
				autoSetThreshold();
			imp.updateAndDraw();
		}
		previousImageID = id;
		previousSlice = slice;
		return ip!=null;
	}

	void autoSetThreshold() {
		if (IJ.debugMode) IJ.log("ColorThresholder.autoSetThreshold");
		boolean darkb = darkBackground!=null && darkBackground.getState();
		switch (colorSpace) {
			case HSB:
				int[] histogram = bplot.getHistogram();
				if (histogram==null) return;
				int threshold = thresholder.getThreshold(method, histogram);
				if (darkb) {
					minBri = threshold+1;
					maxBri = 255;
				} else {
					minBri = 0;
					maxBri = threshold;
				}
				break;
			case RGB:
				int[] rhistogram = plot.getHistogram();
				threshold = thresholder.getThreshold(method, rhistogram);
				if (darkb) {
					minHue = threshold+1;
					maxHue = 255;
				} else {
					minHue = 0;
					maxHue = threshold;
				}
				int[] ghistogram = splot.getHistogram();
				threshold = thresholder.getThreshold(method, ghistogram);
				if (darkb) {
					minSat = threshold+1;
					maxSat = 255;
				} else {
					minSat = 0;
					maxSat = threshold;
				}
				int[] bhistogram = bplot.getHistogram();
				threshold = thresholder.getThreshold(method, bhistogram);
				if (darkb) {
					minBri = threshold+1;
					maxBri = 255;
				} else {
					minBri = 0;
					maxBri = threshold;
				}
				break;
			case LAB: case YUV:
				histogram = plot.getHistogram();
				threshold = thresholder.getThreshold(method, histogram);
				if (darkb) {
					minHue = threshold+1;
					maxHue = 255;
				} else {
					minHue = 0;
					maxHue = threshold;
				}
				break;
		}
		updateScrollBars();
		updateLabels();
		updatePlot();
	}

	void updatePlot() {
		plot.minHue = minHue;
		plot.maxHue = maxHue;
		plot.repaint();
		splot.minHue = minSat;
		splot.maxHue = maxSat;
		splot.repaint();
		bplot.minHue = minBri;
		bplot.maxHue = maxBri;
		bplot.repaint();
	}

	void updateLabels() {
		label1.setText(""+((int)minHue));
		label2.setText(""+((int)maxHue));
		label3.setText(""+((int)minSat));
		label4.setText(""+((int)maxSat));
		label5.setText(""+((int)minBri));
		label6.setText(""+((int)maxBri));
	}

	void updateNames() {
		if (colorSpace==RGB){
			labelh.setText("Red");
			labels.setText("Green");
			labelb.setText("Blue");
		} else if(colorSpace==HSB){
			labelh.setText("Hue");
			labels.setText("Saturation");
			labelb.setText("Brightness");
		} else if(colorSpace==LAB){
			labelh.setText("L*");
			labels.setText("a*");
			labelb.setText("b*");
		} else if(colorSpace==YUV){
			labelh.setText("Y");
			labels.setText("U");
			labelb.setText("V");
		}
	}

	void updateScrollBars() {
		minSlider.setValue((int)minHue);
		maxSlider.setValue((int)maxHue);
		minSlider2.setValue((int)minSat);
		maxSlider2.setValue((int)maxSat);
		minSlider3.setValue((int)minBri);
		maxSlider3.setValue((int)maxBri);
	}

	void adjustMinHue(int value) {
		minHue = value;
		if (maxHue<minHue) {
			maxHue = minHue;
			maxSlider.setValue((int)maxHue);
		}
	}

	void adjustMaxHue(int value) {
		maxHue = value;
		if (minHue>maxHue) {
			minHue = maxHue;
			minSlider.setValue((int)minHue);
		}
	}

	void adjustMinSat(int value) {
		minSat = value;
		if (maxSat<minSat) {
			maxSat = minSat;
			maxSlider2.setValue((int)maxSat);
		}
	}

	void adjustMaxSat(int value) {
		maxSat = value;
		if (minSat>maxSat) {
			minSat = maxSat;
			minSlider2.setValue((int)minSat);
		}
	}

	void adjustMinBri(int value) {
		minBri = value;
		if (maxBri<minBri) {
			maxBri = minBri;
			maxSlider3.setValue((int)maxBri);
		}
	}

	void adjustMaxBri(int value) {
		maxBri = value;
		if (minBri>maxBri) {
			minBri = maxBri;
			minSlider3.setValue((int)minBri);
		}
	}

	void apply(ImagePlus imp) {
		if (IJ.debugMode) IJ.log("ColorThresholder.apply");
		ImageProcessor fillMaskIP = (ImageProcessor)imp.getProperty("Mask");
		if (fillMaskIP==null) return;
		byte[] fillMask = (byte[])fillMaskIP.getPixels();
		byte fill = (byte)255;
		byte keep = (byte)0;

		if (bandPassH.getState() && bandPassS.getState() && bandPassB.getState()){ //PPP All pass
			for (int j = 0; j < numPixels; j++){
				int hue = hSource[j]&0xff;
				int sat = sSource[j]&0xff;
				int bri = bSource[j]&0xff;
				if (((hue < minHue)||(hue > maxHue)) || ((sat < minSat)||(sat > maxSat)) || ((bri < minBri)||(bri > maxBri)))
					fillMask[j] = keep;
				else
					fillMask[j] = fill;
			}
		} else if(!bandPassH.getState() && !bandPassS.getState() && !bandPassB.getState()){ //SSS All stop
			for (int j = 0; j < numPixels; j++){
				int hue = hSource[j]&0xff;
				int sat = sSource[j]&0xff;
				int bri = bSource[j]&0xff;
				if (((hue >= minHue)&&(hue <= maxHue)) || ((sat >= minSat)&&(sat <= maxSat)) || ((bri >= minBri)&&(bri <= maxBri)))
					fillMask[j] = keep;
				else
					fillMask[j] = fill;
			}
		} else if(bandPassH.getState() && bandPassS.getState() && !bandPassB.getState()){ //PPS
			for (int j = 0; j < numPixels; j++){
				int hue = hSource[j]&0xff;
				int sat = sSource[j]&0xff;
				int bri = bSource[j]&0xff;
				if (((hue < minHue)||(hue > maxHue)) || ((sat < minSat)||(sat > maxSat)) || ((bri >= minBri) && (bri <= maxBri)))
					fillMask[j] = keep;
				else
					fillMask[j] = fill;
			}
		} else if(!bandPassH.getState() && !bandPassS.getState() && bandPassB.getState()){ //SSP
			for (int j = 0; j < numPixels; j++){
				int hue = hSource[j]&0xff;
				int sat = sSource[j]&0xff;
				int bri = bSource[j]&0xff;
				if (((hue >= minHue) && (hue <= maxHue)) || ((sat >= minSat) && (sat <= maxSat)) || ((bri < minBri) || (bri > maxBri)))
					fillMask[j] = keep;
				else
					fillMask[j] = fill;
			}
		} else if (bandPassH.getState() && !bandPassS.getState() && !bandPassB.getState()){ //PSS
			for (int j = 0; j < numPixels; j++){
				int hue = hSource[j]&0xff;
				int sat = sSource[j]&0xff;
				int bri = bSource[j]&0xff;
				if (((hue < minHue) || (hue > maxHue)) || ((sat >= minSat) && (sat <= maxSat)) || ((bri >= minBri) && (bri <= maxBri)))
					fillMask[j] = keep;
				else
					fillMask[j] = fill;
			}
		} else if(!bandPassH.getState() && bandPassS.getState() && bandPassB.getState()){ //SPP
			for (int j = 0; j < numPixels; j++){
				int hue = hSource[j]&0xff;
				int sat = sSource[j]&0xff;
				int bri = bSource[j]&0xff;
				if (((hue >= minHue) && (hue <= maxHue))|| ((sat < minSat) || (sat > maxSat)) || ((bri < minBri) || (bri > maxBri)))
					fillMask[j] = keep;
				else
					fillMask[j] = fill;
			}
		} else if (!bandPassH.getState() && bandPassS.getState() && !bandPassB.getState()){ //SPS
			for (int j = 0; j < numPixels; j++){
				int hue = hSource[j]&0xff;
				int sat = sSource[j]&0xff;
				int bri = bSource[j]&0xff;
				if (((hue >= minHue)&& (hue <= maxHue)) || ((sat < minSat)||(sat > maxSat)) || ((bri >= minBri) && (bri <= maxBri)))
					fillMask[j] = keep;
				else
					fillMask[j] = fill;
			}
		} else if(bandPassH.getState() && !bandPassS.getState() && bandPassB.getState()){ //PSP
			for (int j = 0; j < numPixels; j++){
				int hue = hSource[j]&0xff;
				int sat = sSource[j]&0xff;
				int bri = bSource[j]&0xff;
				if (((hue < minHue) || (hue > maxHue)) || ((sat >= minSat)&&(sat <= maxSat)) || ((bri < minBri) || (bri > maxBri)))
					fillMask[j] = keep;
				else
					fillMask[j] = fill;
			}
		}

		ImageProcessor ip = imp.getProcessor();
		if (ip==null) return;
		if (mode==BLACK_AND_WHITE) {
			int[] pixels = (int[])ip.getPixels();
			int fcolor = Prefs.blackBackground?0xffffffff:0xff000000;
			int bcolor = Prefs.blackBackground?0xff000000:0xffffffff;
			for (int i=0; i<numPixels; i++) {
				if (fillMask[i]!=0)
					pixels[i] = fcolor;
				else
					pixels[i]= bcolor;
			}
		} else {
			ip.setColor(thresholdColor());
			ip.fill(fillMaskIP);
		}
	}
	
	Color thresholdColor() {
		Color color = null;
		switch (mode) {
			case RED: color=Color.red; break;
			case WHITE: color=Color.white; break;
			case BLACK: color=Color.black; break;
			case BLACK_AND_WHITE: color=Color.black; break;
		}
		return color;
	}

	void applyStack() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) return;
		applyingStack = true;
		for (int i = 1; i <= numSlices; i++){
			imp.setSlice(i);
			if (!checkImage()) return;
			apply(imp);
		}
		applyingStack = false;
	}

	// Restores the original pixel data
	void reset(ImagePlus imp) {
		if (IJ.debugMode) IJ.log("ColorThresholder.reset");
		ImageProcessor ip = imp.getProcessor();
		ImagePlus originalImage = (ImagePlus)imp.getProperty("OriginalImage");
		if (originalImage==null) {
			originalImage = imp.createImagePlus();
			originalImage.setTitle(imp.getTitle()+" (Original)");
			originalImage.setProcessor(ip.duplicate());
			imp.setProperty("OriginalImage", originalImage);
		}
		if (originalImage.getBitDepth()==24) {
			int[] restore = (int[])originalImage.getProcessor().getPixels();
			int[] pixels = (int[])ip.getPixels();
			for (int i=0; i<numPixels; i++)
				pixels[i] = restore[i];
		}
	}

    public void windowActivated(WindowEvent e) {
    	if (IJ.debugMode) IJ.log("ColorThresholder.windowActivated ");
    	ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null || imp.getBitDepth()!=24) {
			IJ.beep();
			IJ.showStatus("No RGB image");
		} else {
			setup(imp);
			//reset(imp);
			filteredB.setEnabled(true);
    	}
	}

	public void close() {
		super.close();
		instance = null;
		done = true;
		Prefs.set("cthresholder.dark", darkBackground.getState());
		synchronized(this) {
			notify();
		}
	}

	/** Returns Lab in 3 byte arrays. */
	public static void getLab(ImageProcessor ip, byte[] L, byte[] a, byte[] b) {
		ColorSpaceConverter converter = new ColorSpaceConverter();
		int[] pixels = (int[])ip.getPixels();
		for (int i=0; i<pixels.length; i++) {
			double[] values = converter.RGBtoLAB(pixels[i]);
			int L1 = (int) (values[0] * 2.55);
			int a1 = (int) (Math.floor((1.0625 * values[1] + 128) + 0.5));
			int b1 = (int) (Math.floor((1.0625 * values[2] + 128) + 0.5));
			L[i] = (byte)((int)(L1<0?0:(L1>255?255:L1)) & 0xff);
			a[i] = (byte)((int)(a1<0?0:(a1>255?255:a1)) & 0xff);
			b[i] = (byte)((int)(b1<0?0:(b1>255?255:b1)) & 0xff);
		}
	}
		
	public void getYUV(ImageProcessor ip, byte[] Y, byte[] U, byte[] V) {
		// Returns YUV in 3 byte arrays.
		
		//RGB <--> YUV Conversion Formulas from http://www.cse.msu.edu/~cbowen/docs/yuvtorgb.html
		//R = Y + (1.4075 * (V - 128));
		//G = Y - (0.3455 * (U - 128) - (0.7169 * (V - 128));
		//B = Y + (1.7790 * (U - 128);
		//
		//Y = R *  .299 + G *  .587 + B *  .114;
		//U = R * -.169 + G * -.332 + B *  .500 + 128.;
		//V = R *  .500 + G * -.419 + B * -.0813 + 128.;

		int c, x, y, i=0, r, g, b;
		double yf;

		int width=ip.getWidth();
		int height=ip.getHeight();

		for(y=0;y<height; y++) {
			for (x=0; x< width;x++){
				c = ip.getPixel(x,y);

				r = ((c&0xff0000)>>16);//R
				g = ((c&0x00ff00)>>8);//G
				b = ( c&0x0000ff); //B 

				// Kai's plugin
				yf = (0.299 * r  + 0.587 * g + 0.114 * b);
				Y[i] = (byte)((int)Math.floor(yf + 0.5)) ;
				U[i] = (byte)(128+(int)Math.floor((0.493 *(b - yf))+ 0.5)); 
				V[i] = (byte)(128+(int)Math.floor((0.877 *(r - yf))+ 0.5)); 
				
				//Y[i] = (byte) (Math.floor( 0.299 * r + 0.587 * g + 0.114  * b)+.5);
				//U[i] = (byte) (Math.floor(-0.169 * r - 0.332 * g + 0.500  * b + 128.0)+.5);
				//V[i] = (byte) (Math.floor( 0.500 * r - 0.419 * g - 0.0813 * b + 128.0)+.5);
				
				i++;
			}
		}
	}
	
	/** Converts the current image from RGB to CIE L*a*b* and stores the results 
	* in the same RGB image R=L*, G=a*, B=b*. Values are therfore offset and rescaled.
	*/
	public static void RGBtoLab() {
		ImagePlus imp = IJ.getImage();
		if (imp.getBitDepth()==24)
			imp.setProcessor(RGBtoLab(imp.getProcessor()));
	}
	
	private static ImageProcessor RGBtoLab(ImageProcessor ip) {
		int n = ip.getPixelCount();
		byte[] L = new byte[n];
		byte[] a = new byte[n];
		byte[] b = new byte[n];
		ColorThresholder.getLab(ip, L, a, b);
		ColorProcessor cp = new ColorProcessor(ip.getWidth(),ip.getHeight());
		cp.setRGB(L,a,b);
		return cp;
	}
	
	/** Converts the current image from RGB to YUV and stores 
	* the results in the same RGB image R=Y, G=U, B=V.
	* Author: Gabriel Landini,  G.Landini@bham.ac.uk
	*/
	public static void RGBtoYUV() {
		ImagePlus imp = IJ.getImage();
		if (imp.getBitDepth()==24) {
			RGBtoYUV(imp.getProcessor());
			imp.updateAndDraw();
		}
	}

	static void RGBtoYUV(ImageProcessor ip) {
		int xe = ip.getWidth();
		int ye = ip.getHeight();
		int c, x, y, i=0, Y, U, V, r, g, b;
		double yf;

		ImagePlus imp = WindowManager.getCurrentImage();

		for(y=0;y<ye;y++){
			for (x=0;x<xe;x++){
				c=ip.getPixel(x,y);

					r = ((c&0xff0000)>>16);//R
					g = ((c&0x00ff00)>>8);//G
					b = ( c&0x0000ff); //B 

					// Kai's plugin
					yf = (0.299 * r  + 0.587 * g + 0.114 * b);
					Y = ((int)Math.floor(yf + 0.5)) ;
					U = (128+(int)Math.floor((0.493 *(b - yf))+ 0.5)); 
					V = (128+(int)Math.floor((0.877 *(r - yf))+ 0.5)); 

					ip.putPixel(x,y, (((Y<0?0:Y>255?255:Y) & 0xff) << 16)+
									 (((U<0?0:U>255?255:U) & 0xff) << 8) +
								 	  ((V<0?0:V>255?255:V) & 0xff));
				
				ip.putPixel(x,y, ((Y & 0xff) <<16) + ((U & 0xff) << 8) + ( V & 0xff));
			}
		}
	}


	
	class BandPlot extends Canvas implements Measurements, MouseListener {
	
		final int WIDTH = 256, HEIGHT=64;
		double minHue = 0, minSat=0, minBri=0;
		double maxHue = 255, maxSat= 255, maxBri=255;
		int[] histogram;
		Color[] hColors;
		int hmax;
		Image os;
		Graphics osg;
	
		public BandPlot() {
			addMouseListener(this);
			setSize(WIDTH+1, HEIGHT+1);
		}
	
		/** Overrides Component getPreferredSize(). Added to work
		around a bug in Java 1.4 on Mac OS X.*/
		public Dimension getPreferredSize() {
			return new Dimension(WIDTH+1, HEIGHT+1);
		}
	
		void setHistogram(ImagePlus imp, int j) {
			ImageProcessor ip = imp.getProcessor();
			ImageStatistics stats = ImageStatistics.getStatistics(ip, AREA+MODE, null);
			int maxCount2 = 0;
			histogram = stats.histogram;
			for (int i = 0; i < stats.nBins; i++)
				if ((histogram[i] > maxCount2) ) maxCount2 = histogram[i];
				//if ((histogram[i] > maxCount2) && (i != stats.mode)) maxCount2 = histogram[i];
	
			hmax = (int)(maxCount2 * 1.15);//GL was 1.5
			os = null;
			ColorModel cm = ip.getColorModel();
			if (!(cm instanceof IndexColorModel))
				return;
			IndexColorModel icm = (IndexColorModel)cm;
			int mapSize = icm.getMapSize();
			if (mapSize!=256)
				return;
			byte[] r = new byte[256];
			byte[] g = new byte[256];
			byte[] b = new byte[256];
			icm.getReds(r);
			icm.getGreens(g);
			icm.getBlues(b);
			hColors = new Color[256];
	
			if (colorSpace==RGB){
				if (j==0){
					for (int i=0; i<256; i++)
						hColors[i] = new Color(i&255, 0&255, 0&255);
					}
				else if (j==1){
					for (int i=0; i<256; i++)
						hColors[i] = new Color(0&255, i&255, 0&255);
				}
				else if (j==2){
					for (int i=0; i<256; i++)
						hColors[i] = new Color(0&255, 0&255, i&255);
				}
			}
			else if (colorSpace==HSB){
				if (j==0){
					for (int i=0; i<256; i++)
						hColors[i] = new Color(r[i]&255, g[i]&255, b[i]&255);
				}
				else if (j==1){
					for (int i=0; i<256; i++)
						hColors[i] = new Color(255&255, 255-i&255, 255-i&255);
						//hColors[i] = new Color(192-i/4&255, 192+i/4&255, 192-i/4&255);
				}
				else if (j==2){
					for (int i=0; i<256; i++)
						//hColors[i] = new Color(i&255, i&255, 0&255);
						hColors[i] = new Color(i&255, i&255, i&255);
				}
			}
			else if (colorSpace==LAB){
				if (j==0){
					for (int i=0; i<256; i++)
						hColors[i] = new Color(i&255, i&255, i&255);
				}
				else if (j==1){
					for (int i=0; i<256; i++)
						hColors[i] = new Color(i&255, 255-i&255, 0&255);
				}
				else if (j==2){
					for (int i=0; i<256; i++)
						hColors[i] = new Color(i&255, i&255, 255-i&255);
				}
			}
			else if (colorSpace==YUV){
				if (j==0){
					for (int i=0; i<256; i++)
						hColors[i] = new Color(i&255, i&255, i&255);
				}
				else if (j==1){
					for (int i=0; i<256; i++)
						hColors[i] = new Color((int)(36+(255-i)/1.4)&255, 255-i&255, i&255);
				}
				else if (j==2){
					for (int i=0; i<256; i++)
						hColors[i] = new Color(i&255, 255-i&255, (int)(83+(255-i)/2.87)&255);
				}
			}
			
		}
		
		int[] getHistogram() {
			return histogram;
		}
	
		public void update(Graphics g) {
			paint(g);
		}
	
		public void paint(Graphics g ) {
			int hHist=0;
			if (histogram!=null) {
				if (os==null) {
					os = createImage(WIDTH,HEIGHT);
					osg = os.getGraphics();
					//osg.setColor(Color.white);
					osg.setColor(new Color(140,152,144));
					osg.fillRect(0, 0, WIDTH, HEIGHT);
					for (int i = 0; i < WIDTH; i++) {
						if (hColors!=null) osg.setColor(hColors[i]);
						hHist=HEIGHT - ((int)(HEIGHT * histogram[i])/hmax)-6;
						osg.drawLine(i, HEIGHT, i, hHist);
						osg.setColor(Color.black);
						osg.drawLine(i, hHist, i, hHist);
					}
					osg.dispose();
				}
				if (os!=null) g.drawImage(os, 0, 0, this);
			} else {
				g.setColor(Color.white);
				g.fillRect(0, 0, WIDTH, HEIGHT);
			}
			g.setColor(Color.black);
			g.drawLine(0, HEIGHT -6, 256, HEIGHT-6);
			g.drawRect(0, 0, WIDTH, HEIGHT);
			g.drawRect((int)minHue, 1, (int)(maxHue-minHue), HEIGHT-7);
		}
	
		public void mousePressed(MouseEvent e) {}
		public void mouseReleased(MouseEvent e) {}
		public void mouseExited(MouseEvent e) {}
		public void mouseClicked(MouseEvent e) {}
		public void mouseEntered(MouseEvent e) {}
	} // BandPlot class

} // BandAdjuster class




