package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.util.Tools;
import ij.plugin.frame.Recorder;
import ij.plugin.filter.*;
import ij.plugin.ChannelSplitter;
import ij.plugin.Thresholder;

/** Adjusts the lower and upper threshold levels of the active image. This
	class is multi-threaded to provide a more responsive user interface. */
public class ThresholdAdjuster extends PlugInDialog implements PlugIn, Measurements, Runnable,
	ActionListener, AdjustmentListener, ItemListener, FocusListener, KeyListener, MouseWheelListener, ImageListener {

	public static final String LOC_KEY = "threshold.loc";
	public static final String MODE_KEY = "threshold.mode";
	public static final String DARK_BACKGROUND = "threshold.dark";
	public static final String NO_RESET = "threshold.no-reset";
	static final int RED=0, BLACK_AND_WHITE=1, OVER_UNDER=2;
	static final String[] modes = {"Red","B&W", "Over/Under"};
	static final double defaultMinThreshold = 0;//85;
	static final double defaultMaxThreshold = 255;//170;
	static final int DEFAULT = 0;
	static boolean fill1 = true;
	static boolean fill2 = true;
	static boolean useBW = true;
	static boolean backgroundToNaN = true;
	static ThresholdAdjuster instance;
	static int mode = RED;
	static String[] methodNames = AutoThresholder.getMethods();
	static String method = methodNames[DEFAULT];
	static AutoThresholder thresholder = new AutoThresholder();
	ThresholdPlot plot = new ThresholdPlot();
	Thread thread;  //background thread calculating and applying the threshold

	int minValue = -1;  // min slider, 0-255
	int maxValue = -1;
	int sliderRange = 256;
	boolean doAutoAdjust,doReset,doApplyLut,doStateChange,doSet,doBackground; //actions required from user interface

	Panel panel;
	Button autoB, resetB, applyB, setB;
	int previousImageID;
	int previousImageType;
	int previousRoiHashCode;
	double previousMin, previousMax;
	int previousSlice;
	boolean imageWasUpdated;
	ImageJ ij;
	double minThreshold, maxThreshold;  // 0-255
	Scrollbar minSlider, maxSlider;
	TextField minLabel, maxLabel;           // for current threshold
	Label percentiles;
	boolean done;
	int lutColor;
	Choice methodChoice, modeChoice;
	Checkbox darkBackground, stackHistogram, noResetButton;
	boolean firstActivation = true;
	boolean setButtonPressed;
	boolean noReset;
	boolean noResetChanged;
	boolean enterPressed;

	public ThresholdAdjuster() {
		super("Threshold");
		ImagePlus cimp = WindowManager.getCurrentImage();
		if (cimp!=null && cimp.getBitDepth()==24) {
			IJ.error("Threshold Adjuster",
				"Image>Adjust>Threshold only works with grayscale images.\n \n"
				+"You can:\n"
				+"   Convert to grayscale: Image>Type>8-bit\n"
				+"   Convert to RGB stack: Image>Type>RGB Stack\n"
				+"   Convert to HSB stack: Image>Type>HSB Stack\n"
				+"   Convert to 3 grayscale images: Image>Color>Split Channels\n"
				+"   Do color thresholding: Image>Adjust>Color Threshold\n");
			return;
		}
		if (instance!=null) {
			instance.firstActivation = true;
			instance.toFront();
			instance.setup(cimp, true);
			instance.updateScrollBars();
			return;
		}

		WindowManager.addWindow(this);
		instance = this;
		mode = (int)Prefs.get(MODE_KEY, RED);
		if (mode<RED || mode>OVER_UNDER) mode = RED;
		setLutColor(mode);
		IJ.register(PasteController.class);

		ij = IJ.getInstance();
		Font font = new Font("SansSerif", Font.PLAIN, 10);
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();
		setLayout(gridbag);

		// plot
		int y = 0;
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 2;
		c.fill = GridBagConstraints.BOTH;
		c.anchor = GridBagConstraints.CENTER;
		c.insets = new Insets(10, 10, 0, 10); //top left bottom right
		add(plot, c);
		plot.addKeyListener(ij);

		// percentiles
		c.gridx = 0;
		c.gridy = y++;
		c.insets = new Insets(1, 10, 0, 10);
		percentiles = new Label("");
		percentiles.setFont(font);
		add(percentiles, c);

		// minThreshold slider
		minSlider = new Scrollbar(Scrollbar.HORIZONTAL, sliderRange/3, 1, 0, sliderRange);
		GUI.fixScrollbar(minSlider);
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 1;
		c.weightx = IJ.isMacintosh()?90:100;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(1, 10, 0, 0);
		add(minSlider, c);
		minSlider.addAdjustmentListener(this);
		minSlider.addMouseWheelListener(this);
//		minSlider.addKeyListener(ij);
		minSlider.setUnitIncrement(1);
		minSlider.setFocusable(false);

		// minThreshold slider label
		c.gridx = 1;
		c.gridwidth = 1;
		c.weightx = IJ.isMacintosh()?10:0;
		c.insets = new Insets(5, 0, 0, 10);
		String text = "000000";
		int columns = 4;
		minLabel = new TextField(text,columns);
		minLabel.setFont(font);
		add(minLabel, c);
		minLabel.addFocusListener(this);
		minLabel.addMouseWheelListener(this);
		minLabel.addKeyListener(this);

		// maxThreshold slider
		maxSlider = new Scrollbar(Scrollbar.HORIZONTAL, sliderRange*2/3, 1, 0, sliderRange);
		GUI.fixScrollbar(maxSlider);
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 1;
		c.weightx = 100;
		c.insets = new Insets(2, 10, 0, 0);
		add(maxSlider, c);
		maxSlider.addAdjustmentListener(this);
		maxSlider.addMouseWheelListener(this);
//		maxSlider.addKeyListener(ij);
		maxSlider.setUnitIncrement(1);
		maxSlider.setFocusable(false);

		// maxThreshold slider label
		c.gridx = 1;
		c.gridwidth = 1;
		c.weightx = 0;
		c.insets = new Insets(2, 0, 0, 10);
		maxLabel = new TextField(text,columns);
		maxLabel.setFont(font);
		add(maxLabel, c);
		maxLabel.addFocusListener(this);
		maxLabel.addMouseWheelListener(this);
		maxLabel.addKeyListener(this);

		// choices
		panel = new Panel();
		methodChoice = new Choice();
		for (int i=0; i<methodNames.length; i++)
			methodChoice.addItem(methodNames[i]);
		methodChoice.select(method);
		methodChoice.addItemListener(this);
		//methodChoice.addKeyListener(ij);
		panel.add(methodChoice);
		modeChoice = new Choice();
		for (int i=0; i<modes.length; i++)
			modeChoice.addItem(modes[i]);
		modeChoice.select(mode);
		modeChoice.addItemListener(this);
		//modeChoice.addKeyListener(ij);
		panel.add(modeChoice);
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 2;
		c.insets = new Insets(8, 5, 0, 5);
		c.anchor = GridBagConstraints.CENTER;
		c.fill = GridBagConstraints.NONE;
		add(panel, c);

		// checkboxes
		panel = new Panel();
		panel.setLayout(new GridLayout(2, 2));
		boolean db = Prefs.get(DARK_BACKGROUND, Prefs.blackBackground?true:false);
		darkBackground = new Checkbox("Dark background");
		darkBackground.setState(db);
		darkBackground.addItemListener(this);
		panel.add(darkBackground);
		stackHistogram = new Checkbox("Stack histogram");
		stackHistogram.setState(false);
		stackHistogram.addItemListener(this);
		panel.add(stackHistogram); 
		noReset = Prefs.get(NO_RESET, false);
		noResetButton = new Checkbox("Don't reset range");
		noResetButton.setState(noReset);
		noResetButton.addItemListener(this);
		panel.add(noResetButton);
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 2;
		c.insets = new Insets(5, 5, 0, 0);
		add(panel, c);

		// buttons
		int trim = IJ.isMacOSX()?11:0;
		panel = new Panel();
		int hgap = IJ.isMacOSX()?1:5;
		panel.setLayout(new FlowLayout(FlowLayout.RIGHT,hgap,0));
		autoB = new TrimmedButton("Auto",trim);
		autoB.addActionListener(this);
		autoB.addKeyListener(ij);
		panel.add(autoB);
		applyB = new TrimmedButton("Apply",trim);
		applyB.addActionListener(this);
		applyB.addKeyListener(ij);
		panel.add(applyB);
		resetB = new TrimmedButton("Reset",trim);
		resetB.addActionListener(this);
		resetB.addKeyListener(ij);
		panel.add(resetB);
		setB = new TrimmedButton("Set",trim);
		setB.addActionListener(this);
		setB.addKeyListener(ij);
		panel.add(setB);
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 2;
		c.insets = new Insets(0, 5, 10, 5);
		add(panel, c);

 		addKeyListener(ij);  // ImageJ handles keyboard shortcuts
		GUI.scale(this);
		pack();
		Point loc = Prefs.getLocation(LOC_KEY);
		if (loc!=null)
			setLocation(loc);
		else
			GUI.centerOnImageJScreen(this);
		if (IJ.isMacOSX()) setResizable(false);
		show();

		thread = new Thread(this, "ThresholdAdjuster");
		//thread.setPriority(thread.getPriority()-1);
		thread.start();
		ImagePlus imp = WindowManager.getCurrentImage();
		ImagePlus.addImageListener(this);
		if (imp!=null) {
			setup(imp, true);
			updateScrollBars();
		}
	}

	public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
		if (e.getSource()==minSlider)
			minValue = minSlider.getValue();
		else
			maxValue = maxSlider.getValue();
		enterPressed = false;
		notify();
	}

	public synchronized void actionPerformed(ActionEvent e) {
		Button b = (Button)e.getSource();
		if (b==null) return;
		if (b==resetB)
			doReset = true;
		else if (b==autoB)
			doAutoAdjust = true;
		else if (b==applyB)
			doApplyLut = true;
		else if (b==setB) {
			doSet = true;
			setButtonPressed = true;
		}
		notify();
	}

    public synchronized void focusLost(FocusEvent e) {
        doSet = true;
		notify();
    }

	public synchronized void mouseWheelMoved(MouseWheelEvent e)	{
		if (e.getSource()==minSlider || e.getSource()==minLabel) {
			minSlider.setValue(minSlider.getValue() + e.getWheelRotation());
			minValue = minSlider.getValue();
		} else {
			maxSlider.setValue(maxSlider.getValue() + e.getWheelRotation());
			maxValue = maxSlider.getValue();
		}
		notify();
	}

	public synchronized void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_ENTER) {
			doSet = true;
			enterPressed = true;
		} else if(e.getKeyCode() == KeyEvent.VK_DOWN) {
			if (e.getSource()==minLabel) {
				minSlider.setValue(minSlider.getValue() - 1);
				minValue = minSlider.getValue();
			} else {
				maxSlider.setValue(maxSlider.getValue() - 1);
				maxValue = maxSlider.getValue();
			}
		} else if(e.getKeyCode() == KeyEvent.VK_UP) {
			if (e.getSource()==minLabel) {
				minSlider.setValue(minSlider.getValue() + 1);
				minValue = minSlider.getValue();
			} else {
				maxSlider.setValue(maxSlider.getValue() + 1);
				maxValue = maxSlider.getValue();
			}
		} else return;
		notify();
	}

	public void keyReleased(KeyEvent e) {}
	public void keyTyped(KeyEvent e) {}

	public void imageUpdated(ImagePlus imp) {
		if (imp.getID()==previousImageID && Thread.currentThread()!=thread)
			imageWasUpdated = true;
	}

	public void imageOpened(ImagePlus imp) {}
	public void imageClosed(ImagePlus imp) {}

	void setLutColor(int mode) {
		switch (mode) {
			case RED:
				lutColor = ImageProcessor.RED_LUT;
				break;
			case BLACK_AND_WHITE:
				lutColor = ImageProcessor.BLACK_AND_WHITE_LUT;
				break;
			case OVER_UNDER:
				lutColor = ImageProcessor.OVER_UNDER_LUT;
				break;
		}
	}

	public synchronized void itemStateChanged(ItemEvent e) {
		Object source = e.getSource();
		if (source==methodChoice) {
			method = methodChoice.getSelectedItem();
			doAutoAdjust = true;
		} else if (source==modeChoice) {
			mode = modeChoice.getSelectedIndex();
			setLutColor(mode);
			doStateChange = true;
			if (Recorder.record) {
				if (Recorder.scriptMode())
					Recorder.recordCall("ThresholdAdjuster.setMode(\""+modes[mode]+"\");");
				else
					Recorder.recordString("call(\"ij.plugin.frame.ThresholdAdjuster.setMode\", \""+modes[mode]+"\");\n");
			}
		} else if (source==darkBackground) {
			doBackground = true;
		} else if (source==noResetButton) {
			noReset = noResetButton.getState();
			noResetChanged = true;
			doReset = true;
		} else
			doAutoAdjust = true;
		notify();
	}

	/** Called before each user interface action.
	 *  Auto-thresholding is performed if there is currently no threshold and 'enableAutoThreshold' is true.
	 *  Returns the ImageProcessor of the image that should be used, or null if no appropriate image.
	*/
	ImageProcessor setup(ImagePlus imp, boolean enableAutoThreshold) {
		if (IJ.debugMode) IJ.log("ThresholdAdjuster.setup: enableAuto="+enableAutoThreshold);
		if (imp==null)
			return null;
		ImageProcessor ip;
		int type = imp.getType();
		if (type==ImagePlus.COLOR_RGB || (imp.isComposite()&&((CompositeImage)imp).getMode()==IJ.COMPOSITE))
			return null;
		ip = imp.getProcessor();
		boolean minMaxChange = false;
		boolean not8Bits = type==ImagePlus.GRAY16 || type==ImagePlus.GRAY32;
		int slice = imp.getCurrentSlice();
		if (not8Bits) {
			if (ip.getMin()==plot.stackMin && ip.getMax()==plot.stackMax && !imageWasUpdated)
				minMaxChange = false;
			else if (ip.getMin()!=previousMin || ip.getMax()!=previousMax || imageWasUpdated) {
				minMaxChange = true;
	 			previousMin = ip.getMin();
	 			previousMax = ip.getMax();
	 		} else if (slice!=previousSlice)
	 			minMaxChange = true;
		}
		int id = imp.getID();
		int roiHashCode = roiHashCode(imp.getRoi());
		if (minMaxChange || id!=previousImageID || type!=previousImageType ||
				imageWasUpdated || roiHashCode!=previousRoiHashCode) {
			minThreshold = ip.getMinThreshold();
			maxThreshold = ip.getMaxThreshold();
			boolean isThreshold = minThreshold != ImageProcessor.NO_THRESHOLD
					&& ip.getCurrentColorModel() != ip.getColorModel(); //does not work???
			if (not8Bits && minMaxChange && (!noReset || mode==OVER_UNDER)) {
				double max1 = ip.getMax();
				resetMinAndMax(ip);
				if (maxThreshold==max1)
					maxThreshold = ip.getMax();
			}
			ImageStatistics stats = plot.setHistogram(imp, entireStack(imp));
			if (stats == null)
				return null;
			if (isThreshold) {
				minThreshold = scaleDown(ip, minThreshold);
				maxThreshold = scaleDown(ip, maxThreshold);
			} else {
				if (enableAutoThreshold && !isThreshold)
					autoSetLevels(ip, stats);
				else
					minThreshold = ImageProcessor.NO_THRESHOLD;  //may be an invisible threshold after 'apply'
			}
			scaleUpAndSet(ip, minThreshold, maxThreshold);
			updateLabels(imp, ip);
			updatePercentiles(imp, ip);
			updatePlot(ip);
			//updateScrollBars();
			imp.updateAndDraw();
			imageWasUpdated = false;
		}
	 	previousImageID = id;
	 	previousImageType = type;
	 	previousRoiHashCode = roiHashCode;
	 	previousSlice = slice;
	 	firstActivation = false;
	 	return ip;
	}

	private void resetMinAndMax(ImageProcessor ip) {
		if (ip.getBitDepth()!=8 && (!noReset || mode==OVER_UNDER)) {
			ImageStatistics stats = ip.getStats();
			if (ip.getMin()!=stats.min || ip.getMax()!=stats.max) {
				ip.resetMinAndMax();
				ContrastAdjuster.update();
			} else
				ip.resetMinAndMax();
		}
	}

	boolean entireStack(ImagePlus imp) {
		return stackHistogram!=null && stackHistogram.getState() && imp.getStackSize()>1;
	}

	void autoSetLevels(ImageProcessor ip, ImageStatistics stats) {
		if (stats==null || stats.histogram==null) {
			minThreshold = defaultMinThreshold;
			maxThreshold = defaultMaxThreshold;
			return;
		}
		int modifiedModeCount = stats.histogram[stats.mode];
		if (!method.equals(methodNames[DEFAULT]))
			stats.histogram[stats.mode] = plot.originalModeCount;
		int threshold = thresholder.getThreshold(method, stats.histogram);
		stats.histogram[stats.mode] = modifiedModeCount;
		if (thresholdHigh(ip))  // dark background for non-inverting LUT, or bright background for inverting LUT
			{minThreshold=threshold+1; maxThreshold=255;}
		else
			{minThreshold=0; maxThreshold=threshold;}
		if (minThreshold>255)
			minThreshold = 255;
		if (Recorder.record) {
			boolean stack = stackHistogram!=null && stackHistogram.getState();
			if (noReset && ip.getBitDepth()!=8) {
				ImageStatistics stats2 = ip.getStats();
				if (ip.getMin()>stats2.min || ip.getMax()<stats2.max)
					ContrastAdjuster.recordSetMinAndMax(ip.getMin(),ip.getMax());
			}
			boolean darkb = darkBackground!=null && darkBackground.getState();
			String options = method+(darkb?" dark":"")+(noReset?" no-reset":"")+(stack?" stack":"");
			if (Recorder.scriptMode())
				Recorder.recordCall("IJ.setAutoThreshold(imp, \""+options+"\");");
			else
				Recorder.record("setAutoThreshold", options);
		}
	}

	/** Whether the (auto)-thresholded pixels should be those 
	 * with high values, i.e., the background should be at low values.
	 * (E.g. dark background and non-inverting LUT)
	*/
	boolean thresholdHigh(ImageProcessor ip) {
		boolean darkb = darkBackground!=null && darkBackground.getState();
		boolean invertedLut = ip.isInvertedLut();
		return invertedLut ? !darkb : darkb;
	}

	/** Scales threshold levels in the range 0-255 to the actual levels. */
	void scaleUpAndSet(ImageProcessor ip, double lower, double upper) {
		ip.scaleAndSetThreshold(lower, upper, lutColor);
	}

	/** Scales a threshold level to the range 0-255. */
	double scaleDown(ImageProcessor ip, double threshold) {
		if (ip instanceof ByteProcessor)
			return threshold;
		double min = ip.getMin();
		double max = ip.getMax();
		if (max>min) {
			double scaledThr = ((threshold-min)/(max-min))*255.0;
			if (scaledThr < 0.0) scaledThr = 0.0;
			if (scaledThr > 255.0) scaledThr = 255.0;
			return scaledThr;
		} else
			return ImageProcessor.NO_THRESHOLD;
	}

	/** Scales a threshold level in the range 0-255 to the actual level. */
	double scaleUp(ImageProcessor ip, double threshold) {
		double min = ip.getMin();
		double max = ip.getMax();
		if (max>min)
			return min + (threshold/255.0)*(max-min);
		else
			return ImageProcessor.NO_THRESHOLD;
	}

	void updatePlot(ImageProcessor ip) {
		int min = (int)Math.round(minThreshold);
		if (min<0) min=0;
 		if (min>255) min=255;
 		if (ip.getMinThreshold()==ImageProcessor.NO_THRESHOLD)
			min = -1;
 		int max = (int)Math.round(maxThreshold);
 		if (max<0) max=0;
 		if (max>255) max=255;
 		plot.setThreshold(min,max);
		plot.mode = mode;
		plot.repaint();
	}

	void updatePercentiles(ImagePlus imp, ImageProcessor ip) {
		if (percentiles==null)
			return;
		ImageStatistics stats = plot.stats;
		int minThresholdInt = (int)Math.round(minThreshold);
		if (minThresholdInt<0) minThresholdInt=0;
		if (minThresholdInt>255) minThresholdInt=255;
		int maxThresholdInt = (int)Math.round(maxThreshold);
		if (maxThresholdInt<0) maxThresholdInt=0;
		if (maxThresholdInt>255) maxThresholdInt=255;
		if (stats!=null && stats.histogram!=null && stats.histogram.length==256
		&& ip.getMinThreshold()!=ImageProcessor.NO_THRESHOLD) {
			int[] histogram = stats.histogram;
			int below = 0, inside = 0, above = 0;
			int minValue=0, maxValue=255;
			if (imp.getBitDepth()==16 && !entireStack(imp)) {   //16-bit histogram for better accuracy
				ip.setRoi(imp.getRoi());
				histogram = ip.getHistogram();
				minThresholdInt = (int)Math.round(ip.getMinThreshold());
				if (minThresholdInt<0) minThresholdInt=0;
				maxThresholdInt = (int)Math.round(ip.getMaxThreshold());
				if (maxThresholdInt>65535) maxThresholdInt=65535;
				minValue=0; maxValue=histogram.length-1;
			}
			for (int i=minValue; i<minThresholdInt; i++)
				below += histogram[i];
			for (int i=minThresholdInt; i<=maxThresholdInt; i++)
				inside += histogram[i];
			for (int i=maxThresholdInt+1; i<=maxValue; i++)
				above += histogram[i];
			int total = below + inside + above;
			//IJ.log("<"+minThresholdInt+":"+below+" in:"+inside+"; >"+maxThresholdInt+":"+above+" sum="+total);
			if (mode==OVER_UNDER)
				percentiles.setText("below: "+IJ.d2s(100.*below/total)+" %,  above: "+IJ.d2s(100.*above/total)+" %");
			else
				percentiles.setText(IJ.d2s(100.*inside/total)+" %");
		} else
			percentiles.setText("");
	}

	void updateLabels(ImagePlus imp, ImageProcessor ip) {
		if (minLabel==null || maxLabel==null || enterPressed)
			return;
		double min = ip.getMinThreshold();
		double max = ip.getMaxThreshold();
		if (min==ImageProcessor.NO_THRESHOLD) {
			minLabel.setText("");
			maxLabel.setText("");
		} else {
			Calibration cal = imp.getCalibration();
			if (cal.calibrated()) {
				min = cal.getCValue((int)min);
				max = cal.getCValue((int)max);
			}
			if ((((int)min==min && (int)max==max && Math.abs(min)<1e6 && Math.abs(max)<1e6)) ||
					(ip instanceof ShortProcessor && (cal.isSigned16Bit() || !cal.calibrated()))) {
				minLabel.setText(ResultsTable.d2s(min,0));
				maxLabel.setText(ResultsTable.d2s(max,0));
			} else {
				minLabel.setText(min==-1e30 ? "-1e30" : d2s(min));
				maxLabel.setText(max== 1e30 ? "1e30" : d2s(max));
			}
		}
	}

	/** Converts a number to a String, such that it should not take much space (for the minLabel, maxLabel TextFields) */
	String d2s(double x) {
		return Math.abs(x)>=1e6 ? IJ.d2s(x,-2) : ResultsTable.d2s(x,2);  //the latter uses exp notation also for small x
	}

	void updateScrollBars() {
		minSlider.setValue((int)minThreshold);
		maxSlider.setValue((int)maxThreshold);
	}

	/** Restore image outside non-rectangular roi. */
  	void doMasking(ImagePlus imp, ImageProcessor ip) {
		ImageProcessor mask = imp.getMask();
		if (mask!=null)
			ip.reset(mask);
	}

	void adjustMinThreshold(ImagePlus imp, ImageProcessor ip, double value) {
		if (IJ.altKeyDown() || IJ.shiftKeyDown() ) {
			double width = maxThreshold-minThreshold;
			if (width<1.0) width = 1.0;
			minThreshold = value;
			maxThreshold = minThreshold+width;
			if ((minThreshold+width)>255) {
				minThreshold = 255-width;
				maxThreshold = minThreshold+width;
				minSlider.setValue((int)minThreshold);
			}
			maxSlider.setValue((int)maxThreshold);
			scaleUpAndSet(ip, minThreshold, maxThreshold);
			return;
		}
		minThreshold = value;
		if (maxThreshold<minThreshold) {
			maxThreshold = minThreshold;
			maxSlider.setValue((int)maxThreshold);
		}
		scaleUpAndSet(ip, minThreshold, maxThreshold);
	}

	void adjustMaxThreshold(ImagePlus imp, ImageProcessor ip, int cvalue) {
		maxThreshold = cvalue;
		if (minThreshold>maxThreshold) {
			minThreshold = maxThreshold;
			minSlider.setValue((int)minThreshold);
		}
		if (minThreshold < 0) {     //remove NO_THRESHOLD
			minThreshold = 0;
			minSlider.setValue((int)minThreshold);
		}
		scaleUpAndSet(ip, minThreshold, maxThreshold);
		IJ.setKeyUp(KeyEvent.VK_ALT);
		IJ.setKeyUp(KeyEvent.VK_SHIFT);
	}

	void reset(ImagePlus imp, ImageProcessor ip) {
		if (noResetChanged) {
			noResetChanged = false;
			if ((noReset&&mode!=OVER_UNDER) || ip.getBitDepth()==8)
				return;
			if (!noReset) {
				ImageStatistics stats = ip.getStats();
				if (ip.getMin()==stats.min && ip.getMax()==stats.max)
					return; // not contrast enhanced; no need to reset
			}
		}
		ip.resetThreshold();
		if (!noReset)
			resetMinAndMax(ip);
		ImageStatistics stats = plot.setHistogram(imp, entireStack(imp));
		if (ip.getBitDepth()!=8 && entireStack(imp))
			ip.setMinAndMax(stats.min, stats.max);
		updateScrollBars();
		if (Recorder.record) {
			if (Recorder.scriptMode())
				Recorder.recordCall("IJ.resetThreshold(imp);");
			else
				Recorder.record("resetThreshold");
		}
	}

	/** Numeric input via 'Set' dialog or minLabel, maxLabel TextFields */
	void doSet(ImagePlus imp, ImageProcessor ip) {
		double level1 = ip.getMinThreshold();
		double level2 = ip.getMaxThreshold();
		Calibration cal = imp.getCalibration();
		if (level1==ImageProcessor.NO_THRESHOLD) {
			level1 = scaleUp(ip, defaultMinThreshold);
			level2 = scaleUp(ip, defaultMaxThreshold);
		}
		level1 = cal.getCValue(level1);
		level2 = cal.getCValue(level2);
		if (setButtonPressed) {
			int digits = (ip instanceof FloatProcessor)||(cal.calibrated() && !cal.isSigned16Bit()) ? Math.max(Analyzer.getPrecision(), 4) : 0;
			GenericDialog gd = new GenericDialog("Set Threshold Levels");
			gd.addNumericField("Lower threshold level: ", level1, Math.abs(level1)<1e7 ? digits : -4, 10, null);
			gd.addNumericField("Upper threshold level: ", level2, Math.abs(level2)<1e7 ? digits : -4, 10, null);
			gd.showDialog();
			if (gd.wasCanceled()) {
				setButtonPressed = false;
				return;
			}
			level1 = gd.getNextNumber();
			level2 = gd.getNextNumber();
			setButtonPressed = false;
		} else {
			level1 = Tools.parseDouble(minLabel.getText(), level1);
			level2 = Tools.parseDouble(maxLabel.getText(), level2);
		}
		enterPressed = false;
		level1 = cal.getRawValue(level1);
		level2 = cal.getRawValue(level2);
		if (level2<level1)
			level2 = level1;
		double minDisplay = ip.getMin();
		double maxDisplay = ip.getMax();
		if (noReset && (level1<minDisplay||level2>maxDisplay)) {
			noReset = false;
			noResetChanged = true;
			noResetButton.setState(false);
		}
		resetMinAndMax(ip);
		double minValue = ip.getMin();
		double maxValue = ip.getMax();
		if (imp.getStackSize()==1) {
			if (level1<minValue) level1 = minValue;
			if (level2>maxValue) level2 = maxValue;
		}
		IJ.wait(500);
		ip.setThreshold(level1, level2, lutColor);
		ip.setSnapshotPixels(null); // disable undo
		previousImageID = 0;
		setup(imp, false);
		updateScrollBars();
		if (Recorder.record) {
			if (imp.getBitDepth()==32) {
				if (Recorder.scriptMode())
					Recorder.recordCall("IJ.setThreshold(imp, "+IJ.d2s(ip.getMinThreshold(),4)+", "+IJ.d2s(ip.getMaxThreshold(),4)+");");
				else
					Recorder.record("setThreshold", ip.getMinThreshold(), ip.getMaxThreshold());
			} else {
				int min = (int)ip.getMinThreshold();
				int max = (int)ip.getMaxThreshold();
				if (cal.isSigned16Bit()) {
					min = (int)cal.getCValue(level1);
					max = (int)cal.getCValue(level2);
					if (Recorder.scriptMode())
						Recorder.recordCall("IJ.setThreshold(imp, "+min+", "+max+");");
					else
						Recorder.record("setThreshold", min, max);
				}
				if (Recorder.scriptMode())
					Recorder.recordCall("IJ.setRawThreshold(imp, "+min+", "+max+", null);");
				else {
					if (cal.calibrated())
						Recorder.record("setThreshold", min, max, "raw");
					else
						Recorder.record("setThreshold", min, max);
				}
			}
		}
	}

	void changeState(ImagePlus imp, ImageProcessor ip) {
		scaleUpAndSet(ip, minThreshold, maxThreshold);
		updateScrollBars();
	}

	void autoThreshold(ImagePlus imp, ImageProcessor ip) {
		ip.resetThreshold();
		previousImageID = 0;
		setup(imp, true);
		updateScrollBars();
 	}

	/** User has clicked 'Dark background'.
	 *  Switch only if the current thresholds are consistent with
	 * the previous 'Dark background' state.
	*/
	void switchBackground(ImagePlus imp, ImageProcessor ip) {
		if (minThreshold < 0) {     //remove NO_THRESHOLD
			autoThreshold(imp, ip);
			return;
		}
		if (thresholdHigh(ip)) {
			if (minThreshold == 0) {
				minThreshold = maxThreshold+1;
				if (minThreshold > 255) minThreshold = 255;
				maxThreshold = 255;
			}
		} else {
			if (maxThreshold == 255) {
				maxThreshold = minThreshold-1;
				if (maxThreshold < 0) maxThreshold = 0;
				minThreshold = 0;
			}
		}
		minSlider.setValue((int)minThreshold);
		maxSlider.setValue((int)maxThreshold);
		scaleUpAndSet(ip, minThreshold, maxThreshold);
	}

 	void apply(ImagePlus imp) {
 		if (imp.getProcessor().getMinThreshold()==ImageProcessor.NO_THRESHOLD) {
 			IJ.error("Thresholder", "Threshold is not set");
 			return;
 		}
 		try {
 			if (imp.getBitDepth()==32) {
				YesNoCancelDialog d = new YesNoCancelDialog(null, "Thresholder",
					"Convert to 8-bit mask or set background pixels to NaN?",  "Convert to Mask", "Set to NaN");
				if (d.cancelPressed())
					return;
				else if (!d.yesPressed()) {
					Recorder.recordInMacros = true;
					IJ.run("NaN Background");
					Recorder.recordInMacros = false;
					return;
				}
			}
			runThresholdCommand();
 		} catch (Exception e) {}
 	}

 	void runThresholdCommand() {
		Thresholder.setMethod(method);
		Thresholder.setBackground(darkBackground.getState()?"Dark":"Light");
		if (Recorder.record) {
			Recorder.setCommand("Convert to Mask");
			(new Thresholder()).run("mask");
			Recorder.saveCommand();
		} else
			(new Thresholder()).run("mask");
	}

	static final int RESET=0, AUTO=1, HIST=2, APPLY=3, STATE_CHANGE=4, MIN_THRESHOLD=5, MAX_THRESHOLD=6, SET=7, BACKGROUND=8;

	// Separate thread that does the potentially time-consuming processing
	public void run() {
		while (!done) {
			synchronized(this) {
				if (!doAutoAdjust && !doReset && !doApplyLut && !doStateChange && !doSet && !doBackground && minValue<0 &&  maxValue<0) {
					try {wait();}
					catch(InterruptedException e) {}
				}
			}
			doUpdate();
		}
	}

	/** Triggered by the user interface, with the corresponding boolean, e.g., 'doAutoAdjust' */
	void doUpdate() {
		ImagePlus imp;
		ImageProcessor ip;
		int action;
		int min = minValue;
		int max = maxValue;
		if (doReset)            { action = RESET;        doReset = false; }
		else if (doAutoAdjust)  { action = AUTO;         doAutoAdjust = false; }
		else if (doApplyLut)    { action = APPLY;        doApplyLut = false; }
		else if (doStateChange) { action = STATE_CHANGE; doStateChange = false; }
		else if (doSet)         { action = SET;          doSet = false; }
		else if (doBackground)  { action = BACKGROUND;   doBackground = false; }
		else if (minValue>=0)   { action = MIN_THRESHOLD; minValue = -1; }
		else if (maxValue>=0)   { action = MAX_THRESHOLD; maxValue = -1; }
		else return;

		imp = WindowManager.getCurrentImage();
		if (imp==null) {
			IJ.beep();
			IJ.showStatus("No image");
			return;
		}
		ip = setup(imp, false);
		if (ip==null) {
			imp.unlock();
			IJ.beep();
			if (imp.isComposite())
				IJ.showStatus("\"Composite\" mode images cannot be thresholded");
			else
				IJ.showStatus("RGB images cannot be thresholded");
			return;
		}
		switch (action) {
			case RESET: reset(imp, ip); break;
			case AUTO: autoThreshold(imp, ip); break;
			case APPLY: apply(imp); break;
			case STATE_CHANGE: changeState(imp, ip); break;
			case SET: doSet(imp, ip); break;
			case MIN_THRESHOLD: adjustMinThreshold(imp, ip, min); break;
			case MAX_THRESHOLD: adjustMaxThreshold(imp, ip, max); break;
			case BACKGROUND: switchBackground(imp, ip); break;
		}
		updatePlot(ip);
		updateLabels(imp, ip);
		updatePercentiles(imp, ip);
		ip.setLutAnimation(true);
		imp.updateAndDraw();
	}

	/** Overrides close() in PlugInFrame. */
    public void close() {
    	super.close();
		instance = null;
		done = true;
		Prefs.saveLocation(LOC_KEY, getLocation());
		Prefs.set(MODE_KEY, mode);
		Prefs.set(DARK_BACKGROUND, darkBackground.getState());
		Prefs.set(NO_RESET, noResetButton.getState());
		synchronized(this) {
			notify();
		}
	}

    public void windowActivated(WindowEvent e) {
    	super.windowActivated(e);
    	plot.requestFocus();
		ImagePlus imp = WindowManager.getCurrentImage();
		if (!firstActivation && imp!=null) {
			setup(imp, false);
			updateScrollBars();
		}
	}

	// Returns a hashcode for the specified ROI that typically changes
	// if it is moved,  even though is still the same object.
	int roiHashCode(Roi roi) {
		return roi!=null?roi.getHashCode():0;
	}

	/** Notifies the ThresholdAdjuster that the image has changed.
	 *  If the image has no threshold, it does not autothreshold the image.
	*/
	public static void update() {
		if (instance!=null) {
			ThresholdAdjuster ta = ((ThresholdAdjuster)instance);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null && ta.previousImageID==imp.getID()) {
				if ((imp.getCurrentSlice()!=ta.previousSlice) && ta.entireStack(imp))
					return;
				ta.previousImageID = 0;
				ta.setup(imp, false);
				ta.updateScrollBars();
			}
		}
	}

	/** Returns the current thresholding method ("Default", "Huang", etc). */
	public static String getMethod() {
		return method;
	}

	/** Sets the thresholding method ("Default", "Huang", etc). */
	public static void setMethod(String thresholdingMethod) {
		boolean valid = false;
		for (int i=0; i<methodNames.length; i++) {
			if (methodNames[i].equals(thresholdingMethod)) {
				valid = true;
				break;
			}
		}
		if (valid) {
			method = thresholdingMethod;
			if (instance!=null)
				instance.methodChoice.select(method);
		}
	}

	/** Returns the current mode ("Red","B&W" or"Over/Under"). */
	public static String getMode() {
		return modes[mode];
	}

	/** Sets the current mode ("Red","B&W" or"Over/Under"). */
	public static void setMode(String tmode) {
		if (instance!=null) synchronized (instance) {
			ThresholdAdjuster ta = ((ThresholdAdjuster)instance);
			if (modes[0].equals(tmode))
				mode = 0;
			else if (modes[1].equals(tmode))
				mode = 1;
			else if (modes[2].equals(tmode))
				mode = 2;
			else
				return;
			ta.setLutColor(mode);
			ta.doStateChange = true;
			ta.modeChoice.select(mode);
			ta.notify();
		}
	}

} // ThresholdAdjuster class


class ThresholdPlot extends Canvas implements Measurements, MouseListener {
	double scale = Prefs.getGuiScale();
	int width = (int)Math.round(256*scale);
	int height= (int)Math.round(48*scale);
	int lowerThreshold = -1;
 	int upperThreshold = (int)Math.round(170*scale);

	ImageStatistics stats;
	int[] histogram;
	Color[] hColors;
	int hmax;               // maximum of histogram to display
	Image os;
	Graphics osg;
	int mode;
	int originalModeCount;
	double stackMin, stackMax;
	int imageID2;           // ImageID of previous call
	boolean entireStack2;   // 'entireStack' of previous call
	double mean2;

	public ThresholdPlot() {
		addMouseListener(this);
		setSize(width+2, height+2);
	}

    /** Overrides Component getPreferredSize(). Added to work
    	around a bug in Java 1.4.1 on Mac OS X.*/
    public Dimension getPreferredSize() {
        return new Dimension(width+2, height+2);
    }

	ImageStatistics setHistogram(ImagePlus imp, boolean entireStack) {
		if (IJ.debugMode) IJ.log("ThresholdAdjuster:setHistogram: "+entireStack+" "+entireStack2);
		double mean = entireStack?imp.getProcessor().getStats().mean:0.0;
		if (entireStack && stats!=null && imp.getID()==imageID2
		&& entireStack==entireStack2 && mean==mean2)
			return stats;
		mean2 = mean;
		ImageProcessor ip = imp.getProcessor();
		ColorModel cm = ip.getColorModel();
		stats = null;
		if (entireStack) {
			if (imp.isHyperStack()) {
				ImageStack stack = ChannelSplitter.getChannel(imp, imp.getChannel());
				stats = new StackStatistics(new ImagePlus("", stack));
			} else
				stats = new StackStatistics(imp);
		}
		if (!(ip instanceof ByteProcessor)) {
			if (entireStack) {
				if (imp.getLocalCalibration().isSigned16Bit())
					{stats.min += 32768; stats.max += 32768;}
				stackMin = stats.min;
				stackMax = stats.max;
				ip.setMinAndMax(stackMin, stackMax);
				imp.updateAndDraw();
			} else {
				stackMin = stackMax = 0.0;
				if (entireStack2) {
					ip.resetMinAndMax();
					imp.updateAndDraw();
				}
			}
			Calibration cal = imp.getCalibration();
			if (ip instanceof FloatProcessor) {
				int digits = Math.max(Analyzer.getPrecision(), 2);
				IJ.showStatus("min="+IJ.d2s(ip.getMin(),digits)+", max="+IJ.d2s(ip.getMax(),digits));
			} else {
				int digits = cal.calibrated() && !cal.isSigned16Bit() ? 2 : 0;
				IJ.showStatus("min="+IJ.d2s(cal.getCValue(ip.getMin()), digits)+", max="+IJ.d2s(cal.getCValue(ip.getMax()), digits));
			}
			ip = ip.convertToByte(true);
			ip.setColorModel(ip.getDefaultColorModel());
		}
		Roi roi = imp.getRoi();
		if (roi!=null && !roi.isArea()) roi = null;
		ip.setRoi(roi);
		if (stats==null)
			stats = ip.getStats();
		if (IJ.debugMode) IJ.log("  stats: "+stats);
		int maxCount2 = 0;  // number of pixels in 2nd-highest bin, used for y scale if mode is too high
		histogram = stats.histogram;
		originalModeCount = histogram[stats.mode];
		for (int i = 0; i < stats.nBins; i++)
			if ((histogram[i] > maxCount2) && (i != stats.mode))
				maxCount2 = histogram[i];
		hmax = stats.maxCount;
		if ((hmax>(maxCount2 * 1.5)) && (maxCount2 != 0))
			hmax = (int)(maxCount2 * 1.2);
		os = null;

		if (!(cm instanceof IndexColorModel))
			return null;
		IndexColorModel icm = (IndexColorModel)cm;
		int mapSize = icm.getMapSize();
		if (mapSize!=256)
			return null;
		byte[] r = new byte[256];
		byte[] g = new byte[256];
		byte[] b = new byte[256];
		icm.getReds(r);
		icm.getGreens(g);
		icm.getBlues(b);
		hColors = new Color[256];
		final int brightnessLimit = 1800; // 0 ... 2550 scale; brightness is reduced above
		for (int i=0; i<256; i++) {     //avoid colors that are too bright (invisible)
			int sum = 4*(r[i]&255) + 5*(g[i]&255) + (b[i]&255);
			if (sum > brightnessLimit) {
				r[i] = (byte)(((r[i]&255)*brightnessLimit*2)/(sum+brightnessLimit));
				g[i] = (byte)(((g[i]&255)*brightnessLimit*2)/(sum+brightnessLimit));
				b[i] = (byte)(((b[i]&255)*brightnessLimit*2)/(sum+brightnessLimit));
			}
			hColors[i] = new Color(r[i]&255, g[i]&255, b[i]&255);
		}
		imageID2 = imp.getID();
		entireStack2 = entireStack;
		return stats;
	}

	public void update(Graphics g) {
		paint(g);
	}

	public void paint(Graphics g) {
		if (g==null) return;
		if (histogram!=null) {
			if (os==null && hmax>0) {
				os = createImage(width,height);
				osg = os.getGraphics();
				if (scale>1)
					((Graphics2D)osg).setStroke(new BasicStroke((float)scale));
				osg.setColor(Color.white);
				osg.fillRect(0, 0, width, height);
				osg.setColor(Color.gray);
				double scale2 = width/256.0;
				int barWidth = 1;
				if (scale>1) barWidth=2;
				if (scale>2) barWidth=3;
				for (int i = 0; i < 256; i++) {
					if (hColors!=null) osg.setColor(hColors[i]);
					int x =(int)(i*scale2);
					for (int j = 0; j<barWidth; j++)
						osg.drawLine(x+j, height, x+j, height - ((int)(height*histogram[i]+hmax-1)/hmax));
				}
				osg.dispose();
			}
			if (os==null) return;
			g.drawImage(os, 1, 1, this);
		} else {
			g.setColor(Color.white);
			g.fillRect(1, 1, width, height);
		}
		g.setColor(Color.black);
 		g.drawRect(0, 0, width+1, height+1);
 		if (lowerThreshold==-1)
 			return;
		if (mode==ThresholdAdjuster.OVER_UNDER) {
			g.setColor(Color.blue);
			g.drawRect(0, 0, lowerThreshold, height+1);
			g.drawRect(0, 1, lowerThreshold, 1);
			g.setColor(Color.green);
			g.drawRect(upperThreshold+2, 0, width-upperThreshold-1, height+1);
			g.drawLine(upperThreshold+2, 1, width+1,1);
			return;
		}
		if (mode==ThresholdAdjuster.RED)
			g.setColor(Color.red);
		g.drawRect(lowerThreshold+1, 0, upperThreshold-lowerThreshold, height+1);
		g.drawLine(lowerThreshold+1, 1, upperThreshold+1, 1);
	}
	
	void setThreshold(int min, int max) {
 		lowerThreshold = (int)Math.round(min*scale);
 		upperThreshold = (int)Math.round(max*scale);
	}

	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}

} // ThresholdPlot class