package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;

/** This plugin implements the Brightness/Contrast, Window/level and
	Color Balance commands, all in the Image/Adjust sub-menu. It
	allows the user to interactively adjust the brightness  and
	contrast of the active image. It is multi-threaded to
	provide a more  responsive user interface. */
public class ContrastAdjuster extends PlugInDialog implements Runnable,
	ActionListener, AdjustmentListener, ItemListener {

	public static final String LOC_KEY = "b&c.loc";
	public static final String[] sixteenBitRanges = {"Automatic", "8-bit (0-255)", "10-bit (0-1023)",
		"12-bit (0-4095)", "14-bit (0-16383)", "15-bit (0-32767)", "16-bit (0-65535)"};
	static final int AUTO_THRESHOLD = 5000;
	static final String[] channelLabels = {"Red", "Green", "Blue", "Cyan", "Magenta", "Yellow", "All"};
	static final String[] altChannelLabels = {"Channel 1", "Channel 2", "Channel 3", "Channel 4", "Channel 5", "Channel 6", "All"};
	static final String[] greyChannelLabels = {"LUT level"};
	//ColorProcessor.setMinAndMax(min, max, channels): R=4, G=2, B=1 and bitwise or thereof. Thus, 7='all':
	static final int[] channelConstants = {4, 2, 1, 3, 5, 6, 7}; 

	ContrastPlot plot = new ContrastPlot();
	Thread thread;                                          // background thread that does most of the work
	private static ContrastAdjuster instance;

	int sliderRange = 256;

	Panel panel, tPanel;
	Button autoB, resetB, setB, applyB;
	ImageJ ij;
	double min, max;
	double defaultMin, defaultMax;
	int contrast, brightness;
	Scrollbar minSlider, maxSlider, contrastSlider, brightnessSlider;
	Label minLabel, maxLabel, windowLabel, levelLabel;
	static final int SETUP=0,                               // tasks (array indices in taskPending), in order of priority: setup first
			RESET=1, AUTO=2, SET=3, APPLY=4,                // then buttons
			MIN=5, MAX=6, BRIGHTNESS=7, CONTRAST=8;         // task indices of sliders
	static final int FIRST_SLIDER=MIN;
	private final boolean[] taskPending = new boolean[9];   // any changes must be synchronized by 'this'
	private volatile boolean done;                          // stops background thread upon close()
	int autoThreshold;
	GridBagLayout gridbag;
	GridBagConstraints c;
	int y = 0;
	boolean windowLevel, balance;                           // Window/Level and Color Balance mode
	Font monoFont = new Font("Monospaced", Font.PLAIN, 11);
	Font sanFont = IJ.font12;
	int channels = 7;
	Choice choice;
	Checkbox logHistCheckbox;
	boolean isLogHist = false;
	private String blankLabel8 = "--------";
	private String blankLabel12 = "------------";
	private double scale = Prefs.getGuiScale();
	private int digits;
	private java.util.WeakHashMap<ImageProcessor,Integer> rgbPixelsHashes =  // per RGB image: hashCode of the pixels
			new java.util.WeakHashMap<ImageProcessor,Integer>();             // as last set by the ContrastAdjuster
	private int currentImageID;                             // ID of the image for which setup was done last
	private volatile boolean newRGBChannel;                 // Color Balance: other channel selected, make RGB adjustments so far permanent

	public ContrastAdjuster() {
		super("B&C");
	}

	public void run(String arg) {
		windowLevel = arg.equals("wl");
		balance = arg.equals("balance");
		if (windowLevel)
			setTitle("W&L");
		else if (balance) {
			setTitle("Color");
			channels = 4;
		}

		if (instance!=null) {
			if (!instance.getTitle().equals(getTitle())) {
				ContrastAdjuster ca = instance;
				Prefs.saveLocation(LOC_KEY, ca.getLocation());
				ca.close();
			} else {
				instance.toFront();
				return;
			}
		}
		instance = this;
		IJ.register(ContrastAdjuster.class);
		WindowManager.addWindow(this);

		ij = IJ.getInstance();
		gridbag = new GridBagLayout();
		c = new GridBagConstraints();
		setLayout(gridbag);
		if (scale>1.0) {
			sanFont = sanFont.deriveFont((float)(sanFont.getSize()*scale));
			monoFont = monoFont.deriveFont((float)(monoFont.getSize()*scale));
		}

		// plot
		c.gridx = 0;
		y = 0;
		c.gridy = y++;
		c.fill = GridBagConstraints.BOTH;
		c.anchor = GridBagConstraints.CENTER;
		c.insets = new Insets(10, 10, 0, 10);
		gridbag.setConstraints(plot, c);
		add(plot);
		plot.addKeyListener(ij);
		// min and max labels

		if (!windowLevel) {
			panel = new Panel();
			c.gridy = y++;
			c.insets = new Insets(0, 10, 0, 10);
			gridbag.setConstraints(panel, c);
			panel.setLayout(new BorderLayout());
			minLabel = new Label(blankLabel8, Label.LEFT);
			minLabel.setFont(monoFont);
			if (IJ.debugMode) minLabel.setBackground(Color.yellow);
			panel.add("West", minLabel);
			maxLabel = new Label(blankLabel8, Label.RIGHT);
			maxLabel.setFont(monoFont);
			if (IJ.debugMode) maxLabel.setBackground(Color.yellow);
			panel.add("East", maxLabel);
			add(panel);
			blankLabel8 = "        ";
		}

		// log histogram scale checkbox
		logHistCheckbox = new Checkbox("Log scale");
		logHistCheckbox.setState(isLogHist);
		logHistCheckbox.addItemListener(this);
		logHistCheckbox.addKeyListener(ij);
		c.gridy = y++;
		gridbag.setConstraints(logHistCheckbox, c);
		add(logHistCheckbox);

		// min slider
		if (!windowLevel) {
			minSlider = new Scrollbar(Scrollbar.HORIZONTAL, sliderRange/2, 1, 0, sliderRange);
			GUI.fixScrollbar(minSlider);
			c.gridy = y++;
			c.insets = new Insets(2, 10, 0, 10);
			gridbag.setConstraints(minSlider, c);
			add(minSlider);
			minSlider.addAdjustmentListener(this);
			minSlider.addKeyListener(ij);
			minSlider.setUnitIncrement(1);
			minSlider.setFocusable(false); // prevents blinking on Windows
			addLabel("Minimum", null);
		}

		// max slider
		if (!windowLevel) {
			maxSlider = new Scrollbar(Scrollbar.HORIZONTAL, sliderRange/2, 1, 0, sliderRange);
			GUI.fixScrollbar(maxSlider);
			c.gridy = y++;
			c.insets = new Insets(2, 10, 0, 10);
			gridbag.setConstraints(maxSlider, c);
			add(maxSlider);
			maxSlider.addAdjustmentListener(this);
			maxSlider.addKeyListener(ij);
			maxSlider.setUnitIncrement(1);
			maxSlider.setFocusable(false);
			addLabel("Maximum", null);
		}

		// brightness slider
		brightnessSlider = new Scrollbar(Scrollbar.HORIZONTAL, sliderRange/2, 1, 0, sliderRange);
		GUI.fixScrollbar(brightnessSlider);
		c.gridy = y++;
		c.insets = new Insets(windowLevel?12:2, 10, 0, 10);
		gridbag.setConstraints(brightnessSlider, c);
		add(brightnessSlider);
		brightnessSlider.addAdjustmentListener(this);
		brightnessSlider.addKeyListener(ij);
		brightnessSlider.setUnitIncrement(1);
		brightnessSlider.setFocusable(false);
		if (windowLevel)
			addLabel("Level: ", levelLabel=new TrimmedLabel(blankLabel12));
		else
			addLabel("Brightness", null);

		// contrast slider
		if (!balance) {
			contrastSlider = new Scrollbar(Scrollbar.HORIZONTAL, sliderRange/2, 1, 0, sliderRange);
			GUI.fixScrollbar(contrastSlider);
			c.gridy = y++;
			c.insets = new Insets(2, 10, 0, 10);
			gridbag.setConstraints(contrastSlider, c);
			add(contrastSlider);
			contrastSlider.addAdjustmentListener(this);
			contrastSlider.addKeyListener(ij);
			contrastSlider.setUnitIncrement(1);
			contrastSlider.setFocusable(false);
			if (windowLevel)
				addLabel("Window: ", windowLabel=new TrimmedLabel(blankLabel12));
			else
				addLabel("Contrast", null);
		}

		// color channel popup menu
		if (balance) {
			c.gridy = y++;
			c.insets = new Insets(5, 10, 0, 10);
			choice = new Choice();
			addBalanceChoices();
			gridbag.setConstraints(choice, c);
			choice.addItemListener(this);
			add(choice);
		}

		// buttons
		if (scale>1.0) {
			Font font = getFont();
			if (font!=null)
				font = font.deriveFont((float)(font.getSize()*scale));
			else
				font = new Font("SansSerif", Font.PLAIN, (int)(12*scale));
			setFont(font);
		}
		int trim = IJ.isMacOSX()?20:0;
		panel = new Panel();
		panel.setLayout(new GridLayout(0,2, 0, 0));
		autoB = new TrimmedButton("Auto",trim);
		autoB.addActionListener(this);
		autoB.addKeyListener(ij);
		panel.add(autoB);
		resetB = new TrimmedButton("Reset",trim);
		resetB.addActionListener(this);
		resetB.addKeyListener(ij);
		panel.add(resetB);
		setB = new TrimmedButton("Set",trim);
		setB.addActionListener(this);
		setB.addKeyListener(ij);
		panel.add(setB);
		applyB = new TrimmedButton("Apply",trim);
		applyB.addActionListener(this);
		applyB.addKeyListener(ij);
		panel.add(applyB);
		c.gridy = y++;
		c.insets = new Insets(8, 5, 10, 5);
		gridbag.setConstraints(panel, c);
		add(panel);

		addKeyListener(ij);  // ImageJ handles keyboard shortcuts
		pack();
		Point loc = Prefs.getLocation(LOC_KEY);
		if (loc!=null)
			setLocation(loc);
		else
			GUI.centerOnImageJScreen(this);
		if (IJ.isMacOSX()) setResizable(false);
		show();

		thread = new Thread(this, "ContrastAdjuster");
		//thread.setPriority(thread.getPriority()-1);
		thread.start();
		requestSetup();
	}

	void addBalanceChoices() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null && imp.isComposite()) {
			for (int i=0; i<altChannelLabels.length; i++)
				choice.addItem(altChannelLabels[i]);
		} else if (imp!=null && ((imp.getType() == ImagePlus.GRAY8) || (imp.getType() == ImagePlus.GRAY16) || (imp.getType() == ImagePlus.GRAY32))) {
			for (int i=0; i<greyChannelLabels.length; i++)
				choice.addItem(greyChannelLabels[i]);
		} else {
			for (int i=0; i<channelLabels.length; i++)
				choice.addItem(channelLabels[i]);
		}
	}

	void addLabel(String text, Label label2) {
		if (label2==null&&IJ.isMacOSX()) text += "    ";
		panel = new Panel();
		c.gridy = y++;
		int bottomInset = IJ.isMacOSX()?4:0;
		c.insets = new Insets(0, 10, bottomInset, 0);
		gridbag.setConstraints(panel, c);
		panel.setLayout(new FlowLayout(label2==null?FlowLayout.CENTER:FlowLayout.LEFT, 0, 0));
		Label label= new TrimmedLabel(text);
		label.setFont(sanFont);
		panel.add(label);
		if (label2!=null) {
			label2.setFont(monoFont);
			label2.setAlignment(Label.LEFT);
			panel.add(label2);
		}
		add(panel);
	}

	/** Slider callback */
	public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
		Object source = e.getSource();
		if (source==null) return;
		if (source==minSlider)             taskPending[MIN] = true;
		else if (source==maxSlider)        taskPending[MAX] = true;
		else if (source==contrastSlider)   taskPending[CONTRAST] = true;
		else if (source==brightnessSlider) taskPending[BRIGHTNESS] = true;
		notify();
	}

	/** Button callback */
	public synchronized void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source==null) return;
		//remove any pending (previously requested) tasks from slider adjustments
		taskPending[MIN] = taskPending[MAX] = taskPending[BRIGHTNESS] = taskPending[CONTRAST] = false;
		if      (source==resetB) taskPending[RESET] = true;
		else if (source==autoB)  taskPending[AUTO] = true;
		else if (source==setB)   taskPending[SET] = true;
		else if (source==applyB) taskPending[APPLY] = true;
		notify();
	}

	/** Choice callback */
	public synchronized  void itemStateChanged(ItemEvent e) {
		Object source = e.getSource();
		if (source==logHistCheckbox) {
			isLogHist=logHistCheckbox.getState();
			// IJ.log("log state changed");
			taskPending[SETUP] = true;          // notify() follows below
		} else { //the other possible source is the color/channels choice
			int index = choice.getSelectedIndex();
			channels = channelConstants[index];
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) {
				if (imp.isComposite()) {
					if (index+1<=imp.getNChannels())
						imp.setPosition(index+1, imp.getSlice(), imp.getFrame());
					else {
						choice.select(channelLabels.length-1);
						channels = 7;           // 'all' channels/colors
					}
				} else {                        // not composite, i.e. RGB
					newRGBChannel = true;       // keep adjustments of previous channel (handled by doTask)
					taskPending[RESET] = true;  // Then reset sliders for the new channel. notify() follows below
				}
			}
		}
		notify();
	}

	/** Requests a setup by the background thread (e.g. if the image may have changed).
	 *  The background thread then performs setup(). */
	private synchronized void requestSetup() {
		taskPending[SETUP] = true;
		notify();
	}

	/** Called by the background thread on requestSetup(),
	 *  Always does a full setup since there is no quicker way to check
	 *  whether the image or its data have changed */
	void setup(ImagePlus imp) {
		if (isRGB(imp) && imp.isLocked())
			return;
		ImageProcessor ip = imp.getProcessor();
		if (ip == null)
			return;                                     // if imp was asynchronously closed and flushed
		Roi roi = imp.getRoi();
		if (roi!=null) roi.endPaste();

		// Range and statistics for histogram
		// RGB: setup does not modify the pixels or snapshot (keeps Undo, e.g. of 'Apply');
		// a new RGB image gets our own snapshot only when adjusting it (doTask)
		double min2 = imp.getDisplayRangeMin();
		double max2 = imp.getDisplayRangeMax();
		boolean newRGBImage = isRGB(imp) && !hasOwnRGBSnapshot(ip);
		if (newRGBImage) {                              // RGB image that we have not worked on before: min-max range is 0-255
			min2=0.0;
			max2=255.0;
		}
		int bitDepth = imp.getBitDepth();
		int range16 = bitDepth==16 && !imp.getCalibration().isSigned16Bit() ?
				ImagePlus.getDefault16bitRange() : 0;   // 'Unsigned 16-bit range' setting, 0 = automatic
		if (range16>0) {                                // same defaultMin&Max as reset(imp) would obtain via imp.resetDisplayRange()
			defaultMin = 0;                             // ... if imp.setDefaultDisplayRange(min, max) has not been called
			defaultMax = (1<<range16)-1;
		} else if (bitDepth==16 || bitDepth==32) {
			ImageStatistics stats = getRawStatistics(imp, /*useRoi=*/false); // whole image
			if (stats == null) return;                  // image asynchronously closed
			defaultMin = stats.min;
			defaultMax = stats.max;
			avoidZeroDefaultRange(bitDepth);
		} else {
			defaultMin = 0;
			defaultMax = 255;
		}
		if (!isRGB(imp))                                // RGB: pixels are already as displayed, do not modify them
			setMinAndMax(imp, min2, max2);
		if (imp.getProcessor()==null) return;           // image asynchronously closed
		min = newRGBImage ? min2 : imp.getDisplayRangeMin();
		max = newRGBImage ? max2 : imp.getDisplayRangeMax();
		plot.defaultMin = defaultMin;
		plot.defaultMax = defaultMax;
		int valueRange = (int)(defaultMax-defaultMin);
		int newSliderRange = valueRange;
		if (newSliderRange>640 && newSliderRange<1280)
			newSliderRange /= 2;
		else if (newSliderRange>=1280)
			newSliderRange /= 5;
		if (newSliderRange<256) newSliderRange = 256;
		if (newSliderRange>1024) newSliderRange = 1024;
		double displayRange = max-min;
		if (valueRange>=1280 && valueRange!=0 && displayRange/valueRange<0.25)
			newSliderRange *= 1.6666;
		if (newSliderRange!=sliderRange) {
			sliderRange = newSliderRange;
			updateScrollBars(null, true);
		} else
			updateScrollBars(null, false);
		if (balance) {
			if (imp.isComposite()) {
				int channel = imp.getChannel();
				if (channel<=4) {
					choice.removeAll();
					addBalanceChoices();
					choice.select(channel-1);
					channels = channelConstants[channel-1];
				}
				if (!choice.getItem(0).equals("Channel 1")) { // if the choice is wrong
					choice.removeAll();
					addBalanceChoices();
				}
			} else if ((imp.getType() == ImagePlus.GRAY8) || (imp.getType() == ImagePlus.GRAY16) || (imp.getType() == ImagePlus.GRAY32)) { // grey image
				if (!choice.getItem(0).equals("LUT level")) { // if the choice is wrong
					choice.removeAll();
					addBalanceChoices();
				}
			} else { // RGB, not composite
				if (!choice.getItem(0).equals("Red")) {       // if the choice is wrong
					choice.removeAll();
					addBalanceChoices();
				}
				channels = channelConstants[choice.getSelectedIndex()]; // may be wrong after a composite image
			}
		}
		if (isRGB(imp))
			rememberRGBPixels(ip);          //pixels may have changed in setMinAndMax

		plotHistogram(imp);
		autoThreshold = 0;
		currentImageID = imp.getID();

		updatePlot();
		updateLabels(imp);
		imp.updateAndDraw();
		if (imp.isComposite())
			IJ.setKeyUp(KeyEvent.VK_SHIFT);  //shift is for "all channels" in composite images
	}

	/** If all pixel values of a 16/32-bit image are the same (except NaNs),
	 *  widens defaultMin...defaultMax interval so that sliders and histogram plot
	 *  work correctly and that pixel value is displayed with a mid color */
	void avoidZeroDefaultRange(int bitDepth) {
		if (defaultMin==defaultMax) {        // false for NaN
			double delta = Math.max(1.0, Math.abs(defaultMin)*1e-6); //delta=1.0 might be below double resolution for huge values
			defaultMin -= delta;
			defaultMax += delta;
			if (bitDepth==16) {              // not mid color if min or max of 16-bit range
				if (defaultMin < 0) defaultMin = 0;
				if (defaultMax > 65535) defaultMax = 65535;
			}
		}
	}

	void setMinAndMax(ImagePlus imp, double min, double max) {
		boolean rgb = imp.getType()==ImagePlus.COLOR_RGB;
		if (imp.getProcessor() == null) return; // image asynchronously closed
		if (channels!=7 && rgb)
			imp.setDisplayRange(min, max, channels);
		else
			imp.setDisplayRange(min, max);
	}

	void updatePlot() {
		plot.min = min;
		plot.max = max;
		plot.repaint();
	}

	void updateLabels(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		if (ip == null) return;             // image asynchronously closed
		double min = imp.getDisplayRangeMin();
		double max = imp.getDisplayRangeMax();
		int type = imp.getType();
		Calibration cal = imp.getCalibration();
		boolean realValue = type==ImagePlus.GRAY32;
		if (cal.calibrated()) {
			min = cal.getCValue((int)min);
			max = cal.getCValue((int)max);
			realValue = true;
		}
		digits = realValue?4:0;
		if (realValue) {
			double s = min<0||max<0?0.1:1.0;
			double amin = Math.abs(min);
			double amax = Math.abs(max);
			if (amin>99.0*s||amax>99.0*s) digits = 3;
			if (amin>999.0*s||amax>999.0*s) digits = 2;
			if (amin>9999.0*s||amax>9999.0*s) digits = 1;
			if (amin>99999.0*s||amax>99999.0*s) digits = 0;
			if (amin>9999999.0*s||amax>9999999.0*s) digits = -2;
			if ((amin>0&&amin<0.001)||(amax>0&&amax<0.001)) digits = -2;
		}
		if (windowLevel) {
			double window = max-min;
			double level = min+(window)/2.0;
			windowLabel.setText(ResultsTable.d2s(window, digits));
			levelLabel.setText(ResultsTable.d2s(level, digits));
		} else {
			String minString = IJ.d2s(min, min==0.0?0:digits) + blankLabel8;
			minLabel.setText(minString.substring(0,blankLabel8.length()));
			String maxString = blankLabel8 + IJ.d2s(max, digits);
			maxString = maxString.substring(maxString.length()-blankLabel8.length(), maxString.length());
			maxLabel.setText(maxString);
		}
	}

	void updateScrollBars(Scrollbar sb, boolean newRange) {
		if (sb==null || sb!=contrastSlider) {
			double mid = sliderRange/2;
			double c = ((defaultMax-defaultMin)/(max-min))*mid;
			if (c>mid)
				c = sliderRange - ((max-min)/(defaultMax-defaultMin))*mid;
			contrast = (int)c;
			if (contrastSlider!=null) {
				if (newRange)
					contrastSlider.setValues(contrast, 1, 0,  sliderRange);
				else
					contrastSlider.setValue(contrast);
			}
		}
		if (sb==null || sb!=brightnessSlider) {
			double level = min + (max-min)/2.0;
			double normalizedLevel = 1.0 - (level - defaultMin)/(defaultMax-defaultMin);
			brightness = (int)(normalizedLevel*sliderRange);
			if (newRange)
				brightnessSlider.setValues(brightness, 1, 0,  sliderRange);
			else
				brightnessSlider.setValue(brightness);
		}
		if (minSlider!=null && (sb==null || sb!=minSlider)) {
			if (newRange)
				minSlider.setValues(scaleDown(min), 1, 0,  sliderRange);
			else
				minSlider.setValue(scaleDown(min));
		}
		if (maxSlider!=null && (sb==null || sb!=maxSlider)) {
			if (newRange)
				maxSlider.setValues(scaleDown(max), 1, 0,  sliderRange);
			else
				maxSlider.setValue(scaleDown(max));
		}
	}

	/** Converts image pixel value to slider value */
	int scaleDown(double v) {
		if (v<defaultMin) v = defaultMin;
		if (v>defaultMax) v = defaultMax;
		return (int)((v-defaultMin)*(sliderRange-1.0)/(defaultMax-defaultMin));
	}

	/** 'Min' slider moved */
	void adjustMin(ImagePlus imp, ImageProcessor ip, double minvalue) {
		min = defaultMin + minvalue*(defaultMax-defaultMin)/(sliderRange-1.0);
		if (max>defaultMax)
			max = defaultMax;
		if (min>max)
			max = min;
		setMinAndMax(imp, min, max);
		if (min==max)
			setThreshold(ip);
		if (isRGB(imp)) restoreOutsideMask(imp, ip);
		updateScrollBars(minSlider, false);
	}

	/** 'Max' slider moved */
	void adjustMax(ImagePlus imp, ImageProcessor ip, double maxvalue) {
		max = defaultMin + maxvalue*(defaultMax-defaultMin)/(sliderRange-1.0);
		//IJ.log("adjustMax: "+maxvalue+"  "+max);
		if (min<defaultMin)
			min = defaultMin;
		if (max<min)
			min = max;
		setMinAndMax(imp, min, max);
		if (min==max)
			setThreshold(ip);
		if (isRGB(imp)) restoreOutsideMask(imp, ip);
		updateScrollBars(maxSlider, false);
	}

	/** 'Brightness' slider moved ('Level' in W/L mode) */
	void adjustBrightness(ImagePlus imp, ImageProcessor ip, double bvalue) {
		double center = defaultMin + (defaultMax-defaultMin)*((sliderRange-bvalue)/sliderRange);
		double width = max-min;
		min = center - width/2.0;
		max = center + width/2.0;
		setMinAndMax(imp, min, max);
		if (min==max)
			setThreshold(ip);
		if (isRGB(imp)) restoreOutsideMask(imp, ip);
		updateScrollBars(brightnessSlider, false);
	}

	/** 'Contrast' slider moved ('Window' in W/L mode) */
	void adjustContrast(ImagePlus imp, ImageProcessor ip, int cvalue) {
		double slope;
		double center = min + (max-min)/2.0;
		double range = defaultMax-defaultMin;
		double mid = sliderRange/2;
		if (cvalue<=mid)
			slope = cvalue/mid;
		else
			slope = mid/(sliderRange-cvalue);
		if (slope>0.0) {
			min = center-(0.5*range)/slope;
			max = center+(0.5*range)/slope;
		}
		setMinAndMax(imp, min, max);
		if (isRGB(imp)) restoreOutsideMask(imp, ip);
		updateScrollBars(contrastSlider, false);
	}

	/** Reset display range. When called after Reset button pressed,
	 *  'fromResetButton' should be true for macro recording. */
	void reset(ImagePlus imp, ImageProcessor ip, boolean fromResetButton) {
		if (isRGB(imp))
			ip.reset();
		int bitDepth = imp.getBitDepth();
		if ((bitDepth==16 || bitDepth==32) && imp.getProcessor() != null) {
			imp.resetDisplayRange();
			defaultMin = imp.getDisplayRangeMin();
			defaultMax = imp.getDisplayRangeMax();
			avoidZeroDefaultRange(bitDepth);
			plot.defaultMin = defaultMin;
			plot.defaultMax = defaultMax;
		}
		min = defaultMin;
		max = defaultMax;
		setMinAndMax(imp, min, max);
		updateScrollBars(null, false);
		autoThreshold = 0;
		if (fromResetButton && IJ.recording()) {
			if (Recorder.scriptMode())
				Recorder.recordCall("IJ.resetMinAndMax(imp);");
			else
				Recorder.record("resetMinAndMax");
		}
		// when invoked by Reset button, doTask refreshes histogram thereafter
	}

	/** For RGB images: If the pixels were modified by another operation (or there is no snapshot
	 *  of the ContrastAdjuster yet), takes the current pixels as the new unadjusted image */
	private void ensureOwnRGBSnapshot(ImageProcessor ip) {
		if (!(ip instanceof ColorProcessor) || hasOwnRGBSnapshot(ip))
			return;                     // not RGB or we have a ContrastAdjuster snapshot
		Undo.reset();                   // the undo snapshot will be overwritten
		if (ip.getPixels() == null) return;
		ip.snapshot();
		((ColorProcessor)ip).caSnapshot(true);
		ip.setMinAndMax(0, 255);        // pixels remain as they are
		min = 0;
		max = 255;
	}

	/** Returns whether the image is a 24-bit RGB image that needs special treatment because
	 *  brightness/contrast/balance adjustment is done by modifying the image data. */
	private static boolean isRGB(ImagePlus imp) {
		return imp!=null && imp.getType()==ImagePlus.COLOR_RGB;
	}

	/** Whether the snapshot of an RGB image holds the unadjusted pixels, i.e., whether the pixels
	 *  were modified only by the ContrastAdjuster since it has taken the snapshot */
	private boolean hasOwnRGBSnapshot(ImageProcessor ip) {
		if (!(ip instanceof ColorProcessor && ((ColorProcessor)ip).caSnapshot()
				&& ip.getSnapshotPixels()!=null))
			return false;
		Object pixels = ip.getPixels();
		Integer hash = rgbPixelsHashes.get(ip);
		if (hash == null)
			return false;
		return pixels!=null && java.util.Arrays.hashCode((int[])pixels)==hash.intValue();
	}

	/** Remembers the RGB pixels as set by the ContrastAdjuster (for detecting modifications by others) */
	private void rememberRGBPixels(ImageProcessor ip) {
		if (ip instanceof ColorProcessor) {
			Object pixels = ip.getPixels();
			if (pixels==null) return;
			rgbPixelsHashes.put(ip, Integer.valueOf(java.util.Arrays.hashCode((int[])pixels)));
		}
	}

	/** Restores the pixels within the roi's bounding rectangle that are outside the mask,
	 *  i.e. not contained in the (non-rectangular) roi. Here, used for RGB images only */
	void restoreOutsideMask(ImagePlus imp, ImageProcessor ip) {
		Roi roi = imp.getRoi();
		if (roi==null || !roi.isArea())
			return;                         // line & point selections: whole image modified, no masking
		ImageProcessor mask = imp.getMask();
		if (mask!=null) {
			Rectangle r = ip.getRoi();
			if (mask.getWidth()!=r.width||mask.getHeight()!=r.height) {
				ip.setRoi(imp.getRoi());
				mask = ip.getMask();
			}
			ip.reset(mask);
		}
	}

	/** Returns a new ImageProcessor that uses the same pixels array as the given one,
	 *  but has its own roi and mask. Other threads that use the image's processor
	 *  (e.g. via ImagePlus.getProcessor, which sets its roi) then cannot interfere.
	 *  May return null if the image was asynchronously closed */
	static ImageProcessor getPixelsProcessor(ImageProcessor ip) {
		int w = ip.getWidth(), h = ip.getHeight();
		Object pixels = ip.getPixels();
		if (pixels instanceof byte[])
			return new ByteProcessor(w, h, (byte[])pixels, ip.getColorModel());
		else if (pixels instanceof short[])
			return new ShortProcessor(w, h, (short[])pixels, ip.getColorModel());
		else if (pixels instanceof float[])
			return new FloatProcessor(w, h, (float[])pixels, ip.getColorModel());
		else if (pixels instanceof int[])
			return new ColorProcessor(w, h, (int[])pixels);
		else
			return null;  // pixels = null, image asynchronously closed
	}

	/** Raw (uncalibrated) statistics of the area roi (whole image if roi is null or not an area).
	 *  'ip2' must not be the image's processor (see getPixelsProcessor), its roi and histogram range
	 *  are modified by this method.
	 *  hMin, hMax: histogram range; 0, 0 for min-max of the pixel values */
	static ImageStatistics getRawStatistics(ImageProcessor ip2, Roi roi, double hMin, double hMax) {
		if (roi!=null && roi.isArea())  //not for single-Point rois, in agreement with rawStatistics in ImageJ 1.54u
			ip2.setRoi(roi);
		ip2.setHistogramRange(hMin, hMax);
		return ImageStatistics.getStatistics(ip2,
			ImageStatistics.AREA+ImageStatistics.MEAN+ImageStatistics.MODE+ImageStatistics.MIN_MAX, null);
	}

	/** Raw (uncalibrated) statistics of the image, or of its area roi if 'useRoi' is true.
	 *  Does not modify the roi or mask of the image's processor (thread-safe, see getPixelsProcessor).
	 *  May return null if the image was asynchronously closed */
	static ImageStatistics getRawStatistics(ImagePlus imp, boolean useRoi) {
		ImageProcessor ip = imp.getProcessor();
		if (ip == null) return null;
		ImageProcessor ip2 = getPixelsProcessor(ip);
		if (ip2 == null) return null;
		return getRawStatistics(ip2, useRoi ? imp.getRoi() : null, 0, 0);
	}

	/** Statistics of an RGB image, or of its area roi if 'useRoi' is true, with the histogram
	 *  being the sum of the histograms of the channels selected by 'channels' (4=red, 2=green, 1=blue).
	 *  Only histogram, longPixelCount, pixelCount, maxCount, mode, min, max and the histogram range are set.
	 *  Does not modify the roi or mask of the image's processor (thread-safe, see getPixelsProcessor).
	 *  May return null if the image was asynchronously closed */
	static ImageStatistics getRGBStatistics(ImagePlus imp, int channels, boolean useRoi) {
		ImageProcessor ip = imp.getProcessor();
		if (ip == null) return null;
		int[] pixels = (int[])ip.getPixels();
		if (pixels == null) return null;        // image asynchronously closed
		int width = ip.getWidth(), height = ip.getHeight();
		Rectangle r = new Rectangle(0, 0, width, height);
		byte[] maskPixels = null;
		Roi roi = useRoi  ? imp.getRoi() : null;
		if (roi != null && roi.isArea()) {
			ImageProcessor ip2 = getPixelsProcessor(ip);
			if (ip2 == null) return null;       // image asynchronously closed
			ip2.setRoi(roi);
			r = ip2.getRoi();
			maskPixels = ip2.getMaskArray();
		}
		boolean includeRed = (channels&4)!=0, includeGreen = (channels&2)!=0, includeBlue = (channels&1)!=0;
		int[] histogram = new int[256];
		for (int y=r.y,yM=0; yM<r.height; y++,yM++) {
			int p = y*width + r.x;              // pointer in pixels array
			int pM = yM*r.width;                // pointer in maskPixels array (roi rectangle)
			for (int xM=0; xM<r.width; xM++,p++,pM++) {
				if (maskPixels!=null && maskPixels[pM]==0)
					continue;
				int c = pixels[p];
				if (includeRed)   histogram[(c>>16)&255]++;
				if (includeGreen) histogram[(c>>8)&255]++;
				if (includeBlue)  histogram[c&255]++;
			}
		}
		ImageStatistics stats = new ImageStatistics();
		stats.histogram = histogram;
		stats.nBins = 256;
		stats.histMin = 0;
		stats.histMax = 255;
		stats.binSize = 1;
		int min = -1, max = 0;
		for (int i=0; i<256; i++) {
			int count = histogram[i];
			stats.pixelCount += count;
			if (count > stats.maxCount) {
				stats.maxCount = count;
				stats.mode = i;
			}
			if (count > 0) {
				if (min < 0) min = i;
				max = i;
			}
		}
		stats.longPixelCount = stats.pixelCount;
		stats.dmode = stats.mode;
		stats.min = Math.max(min, 0);
		stats.max = max;
		return stats;
	}

	void plotHistogram(ImagePlus imp) {
		ImageStatistics stats = null;
		boolean isRGB = isRGB(imp);
		if (isRGB) {
			if (balance)                            // color balance mode
				setTitle("Color");
			stats = getRGBStatistics(imp, channels, /*useRoi=*/true);
		} else {
			if (balance) {setTitle("Color");}
			if (balance && ((imp.getType() == ImagePlus.GRAY8) || (imp.getType() == ImagePlus.GRAY16) || (imp.getType() == ImagePlus.GRAY32)) && !imp.isComposite()) { // image is grey
				setTitle("LUT Color");
			}
			int range = imp.getType()==ImagePlus.GRAY16 ? ImagePlus.getDefault16bitRange() : 0;
			ImageProcessor ip = imp.getProcessor();
			if (ip == null) return;  //image asynchronously closed
			if (range!=0 && ip.getMax()==(1<<range)-1 && !(imp.getCalibration().isSigned16Bit())) {//(1<<range) is 2^range
				ImageProcessor ip2 = getPixelsProcessor(ip);
				if (ip2 == null) return;
				stats = getRawStatistics(ip2, imp.getRoi(), 0, Math.pow(2,range)-1);
			} else
				stats = getRawStatistics(imp, true);   // raw values, like defaultMin, defaultMax
		}
		if (stats == null) return;
		int bitDepth = imp.getBitDepth();
		boolean isRawHistogram = bitDepth==16 || bitDepth==32;
		double histStart = isRawHistogram ? stats.histMin : 0.0;   // 8-bit & RGB: one bin per value 0-255
		double histBinSize = isRawHistogram ? stats.binSize : 1.0;
		// Default histogram color for images without LUT is now defined in the setHistogram method
		plot.setHistogram(imp, stats, isLogHist, histStart, histBinSize, isRGB);
	}

	/** Called if the Apply button was pressed */
	void apply(ImagePlus imp, ImageProcessor ip) {
		if (balance && imp.isComposite())
			return;
		int bitDepth = imp.getBitDepth();
		if ((bitDepth==8||bitDepth==16) && !IJ.isMacro()) {
			String msg = "WARNING: the pixel values will\nchange if you click \"OK\".";
			if (!IJ.showMessageWithCancel("Apply Lookup Table?", msg))
				return;
		}
		String option = null;
		if (!isRGB(imp))
			if (!imp.lock())     // lock, except for RGB images, which are already locked by doTask(task)
				return;
		if (isRGB(imp)) {
			if (imp.getStackSize()>1)
				applyRGBStack(imp);
			else
				applyRGB(imp,ip);
			return;
		}
		if (bitDepth==32) {
			IJ.beep();
			IJ.error("\"Apply\" does not work with 32-bit images");
			imp.unlock();
			return;
		}
		// The display range min-max is mapped to the output range outMin-outMax: 0-255 for 8-bit images.
		// For 16-bit images, the output range is the range of the 'Unsigned 16-bit range' setting
		// or (if 'Automatic') the defaultMin-defaultMax range of the x axis of the plot, i.e., the
		// pixel value range of the image. Thus, after 'Apply', the image looks like the preview.
		int outMin = 0, outMax = 255;
		if (bitDepth==16) {
			int defaultRange = imp.getDefault16bitRange();
			if (defaultRange>0)
				outMax = (1<<defaultRange) - 1; //1<<n is 2^n
			else {
				outMin = (int)Math.max(0, Math.round(defaultMin));
				outMax = (int)Math.min(65535, Math.round(defaultMax));
			}
		}
		int tableSize = bitDepth==16 ? 65536 : 256;
		int[] table = new int[tableSize];
		int min = (int)imp.getDisplayRangeMin();
		int max = (int)imp.getDisplayRangeMax();
		if (IJ.debugMode) IJ.log("Apply: mapping "+min+"-"+max+" to "+outMin+"-"+outMax);
		for (int i=0; i<tableSize; i++) {
			if (i<=min)
				table[i] = outMin;
			else if (i>=max)
				table[i] = outMax;
			else
				table[i] = outMin + (int)(((double)(i-min)/(max-min))*(outMax-outMin+1));
		}
		if (imp.getStackSize()>1 && !imp.isComposite()) {
			ImageStack stack = imp.getStack();
			YesNoCancelDialog d = new YesNoCancelDialog(new Frame(),
				"Entire Stack?", "Apply LUT to all "+stack.size()+" stack slices?");
			if (d.cancelPressed())
				{imp.unlock(); return;}
			if (d.yesPressed()) {
				if (imp.getStack().isVirtual()) {
					imp.unlock();
					IJ.error("\"Apply\" does not work with virtual stacks. Use\nImage>Duplicate to convert to a normal stack.");
					return;
				}
				Roi roi = imp.getRoi();
				for (int i=1; i<=stack.size(); i++) {
					ImageProcessor ip2 = stack.getProcessor(i); // shares the pixels, has its own roi & mask
					if (roi!=null && roi.isArea()) {
						ip2.setRoi(roi);
						if (ip2.getMask()!=null)
							ip2.snapshot();
					}
					ip2.applyTable(table);
					ip2.reset(ip2.getMask());                   // does nothing if no mask
				}
				Undo.reset();                                   // no undo for the whole stack
				option = "stack";
			} else {
				applyTable(imp, ip, table);
				option = "slice";
			}
		} else //single-slice image
			applyTable(imp, ip, table);

		reset(imp, ip, false);                                  // back to default display range 
		imp.changes = true;
		imp.unlock();
		if (IJ.recording()) {
			if (Recorder.scriptMode()) {
				if (option==null) option = "";
				Recorder.recordCall("IJ.run(imp, \"Apply LUT\", \""+option+"\");");
			} else {
				if (option!=null)
					Recorder.record("run", "Apply LUT", option);
				else
					Recorder.record("run", "Apply LUT");
			}
		}
	}

	/** Applies the table to the roi of the current 8-bit or 16-bit image, with Undo.
	 *  The roi and mask are set on a separate processor sharing the pixels and snapshot,
	 *  so other threads that use the image's processor cannot interfere */
	private void applyTable(ImagePlus imp, ImageProcessor ip, int[] table) {
		if (ip == null || ip.getPixels() == null) return;  //image asynchronously closed
		ip.snapshot();                          // for Undo
		ImageProcessor ip2 = getPixelsProcessor(ip);
		if (ip2 == null) return;
		ip2.setSnapshotPixels(ip.getSnapshotPixels());
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isArea())
			ip2.setRoi(roi);
		ip2.applyTable(table);
		ip2.reset(ip2.getMask());               // restore pixels outside a non-rectangular roi
		Undo.setup(Undo.FILTER, imp);
	}

	/** 'Apply' for RGB single images (not for RGB stacks or stack slices):
	 *  The adjusted pixels become the new unadjusted pixels.
	 *  Undoable: the snapshot then holds the previous (unadjusted) pixels and 'Undo' swaps them
	 *  (as long as nothing else modifies the image or its snapshot, incl. further adjustments with B&C) */
	void applyRGB(ImagePlus imp, ImageProcessor ip) {
		recordSetMinAndMax(ip.getMin(), ip.getMax());
		Object unadjustedPixels = ip.getSnapshotPixels(); // our own snapshot, see ensureOwnRGBSnapshot
		((ColorProcessor)ip).caSnapshot(false); // otherwise setSnapshotPixels(null) is ignored
		ip.setSnapshotPixels(null);             // so snapshot() creates a new array, keeps unadjustedPixels
		ip.snapshot();                          // the adjusted pixels as new unadjusted image
		ip.setMinAndMax(0, 255);                // pixels remain as they are
		reset(imp, ip, false);                  // back to default display range
		if (unadjustedPixels != null) {
			ip.setSnapshotPixels(unadjustedPixels);  // also sets caSnapshot=false: B&C takes a new snapshot when needed
			Undo.setup(Undo.FILTER, imp);       // Undo swaps pixels and snapshot
		}
		imp.changes = true;
	}

	private void applyRGBStack(ImagePlus imp) {
		double min = imp.getDisplayRangeMin();
		double max = imp.getDisplayRangeMax();
		if (IJ.debugMode) IJ.log("applyRGBStack: "+min+"-"+max);
		int current = imp.getCurrentSlice();
		int n = imp.getStackSize();
		if (!IJ.showMessageWithCancel("Update Entire Stack?",
		"Apply brightness and contrast settings\n"+
		"to all "+n+" slices in the stack?\n \n"+
		"NOTE: There is no Undo for this operation."))
			return;

		Roi roi = imp.getRoi();
		boolean areaRoi = roi!=null && roi.isArea();
		ImageStack stack = imp.getStack();
		for (int i=1; i<=n; i++) {
			IJ.showProgress(i, n);
			IJ.showStatus(i+"/"+n);
			if (i!=current) {
				ImageProcessor ip = stack.getProcessor(i);   // shares the pixels, has its own roi & mask
				if (areaRoi)
					ip.setRoi(roi);                          // also crops the mask if the roi extends beyond the image
				ImageProcessor mask = ip.getMask();
				if (mask!=null) ip.snapshot();
				if (channels!=7)
					((ColorProcessor)ip).setMinAndMax(min, max, channels);
				else
					ip.setMinAndMax(min, max);
				if (mask!=null) ip.reset(mask);
			}
		}
		IJ.showStatus("");
		imp.setStack(null, stack);
		imp.setSlice(current);
		imp.changes = true;
		requestSetup();        // like reset(imp...) in applyRGB: B&C takes the applied pixels as unadjusted, range 0-255
		if (IJ.recording()) {
			if (Recorder.scriptMode())
				Recorder.recordCall("IJ.run(imp, \"Apply LUT\", \"stack\");");
			else
				Recorder.record("run", "Apply LUT", "stack");
		}
	}

	void setThreshold(ImageProcessor ip) {
		if (!(ip instanceof ByteProcessor))
			return;
		if (((ByteProcessor)ip).isInvertedLut())
			ip.setThreshold(max, 255, ImageProcessor.NO_LUT_UPDATE);
		else
			ip.setThreshold(0, max, ImageProcessor.NO_LUT_UPDATE);
	}

	/** Called if the Auto button was pressed.
	 *  The new range is chosen based on the selection. Nevertheless, it applies the
	 *  new display range (the brightness&contrast) to the whole image, not only the
	 *  selection for all image types including RGB (in contrast to other functions,
	 *  which are limited to the selection for RGB images). */
	void autoAdjust(ImagePlus imp, ImageProcessor ip) {
		boolean isRGB = isRGB(imp);
		if (isRGB)
			ip.reset();
		ImageStatistics stats = isRGB ?
				getRGBStatistics(imp, channels, /*useRoi=*/true) :
				getRawStatistics(imp, /*useRoi=*/true);
		if (stats == null) return;         //image asynchronously closed
		long pixelCount = Math.max(stats.longPixelCount, stats.pixelCount); // Byte/Short/FloatStatistics set only pixelCount
		int limit = (int)(pixelCount/10);
		int[] histogram = stats.histogram;
		if (autoThreshold<10)
			autoThreshold = AUTO_THRESHOLD;
		else
			autoThreshold /= 2;
		int threshold = (int)(pixelCount/autoThreshold);
		int i = -1;
		boolean found = false;
		int count;
		do {
			i++;
			count = histogram[i];
			if (count>limit) count = 0;
			found = count>threshold;
		} while (!found && i<255);
		int hmin = i;
		i = 256;
		do {
			i--;
			count = histogram[i];
			if (count>limit) count = 0;
			found = count > threshold;
		} while (!found && i>0);
		int hmax = i;
		Roi roi = imp.getRoi();
		if (hmax>=hmin) {
			if (isRGB(imp)) imp.deleteRoi();
			min = stats.histMin+hmin*stats.binSize;
			max = stats.histMin+hmax*stats.binSize;
			if (min==max)
				{min=stats.min; max=stats.max;}
			setMinAndMax(imp, min, max);
			if (isRGB(imp) && roi!=null) imp.setRoi(roi);
		} else {
			reset(imp, ip, false);       //can't increase contrast any more, start with default contrast
			return;
		}
		updateScrollBars(null, false);
		if (IJ.recording()) {
			if (Recorder.scriptMode())
				Recorder.recordCall("IJ.run(imp, \"Enhance Contrast\", \"saturated=0.35\");");
			else
				Recorder.record("run", "Enhance Contrast", "saturated=0.35");
		}
	}

	/** Called if the Set button was pressed (except Window/Level mode) */
	void setMinAndMax(ImagePlus imp, ImageProcessor ip) {
		min = imp.getDisplayRangeMin();
		max = imp.getDisplayRangeMax();
		Calibration cal = imp.getCalibration();
		double minValue = cal.getCValue(min);
		double maxValue = cal.getCValue(max);
		int channels = imp.getNChannels();
		GenericDialog gd = new GenericDialog("Set Display Range");
		gd.addNumericField("Minimum displayed value: ", minValue, digits, 9, "");
		gd.addNumericField("Maximum displayed value: ", maxValue, digits, 9, "");
		gd.addChoice("Unsigned 16-bit range:", sixteenBitRanges, sixteenBitRanges[get16bitRangeIndex()]);
		String label = "Propagate to all other ";
		label = imp.isComposite()?label+channels+" channel images" :
				label+"open images of this type";
		gd.addCheckbox(label, false);
		boolean allChannels = false;
		if (imp.isComposite() && channels>1) {
			label = "Propagate to the other ";
			label = channels==2?label+"channel of this image":label+(channels-1)+" channels of this image";
			gd.addCheckbox(label, allChannels);
		}
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		minValue = gd.getNextNumber();
		maxValue = gd.getNextNumber();
		minValue = cal.getRawValue(minValue);
		maxValue = cal.getRawValue(maxValue);
		int rangeIndex = gd.getNextChoiceIndex();
		int range1 = ImagePlus.getDefault16bitRange();  // 16-bit range before the dialog
		int range2 = set16bitRange(rangeIndex);         // set default to the user Choice of 16-bit range
		if (range1!=range2 && imp.getType()==ImagePlus.GRAY16 && !cal.isSigned16Bit()) {
			reset(imp, ip , false);                     // user has chosen a different 16-bit range, apply it
			minValue = imp.getDisplayRangeMin();        // (ignoring the numeric fields, this overrides them)
			maxValue = imp.getDisplayRangeMax();
		}
		boolean propagate = gd.getNextBoolean();
		if (imp.isComposite() && channels>1)
			allChannels = gd.getNextBoolean();
		if (maxValue>=minValue) {
			min = minValue;
			max = maxValue;
			setMinAndMax(imp, min, max);
			updateScrollBars(null, false);
			if (isRGB(imp)) restoreOutsideMask(imp, ip);
			if (allChannels) {
				int channel = imp.getChannel();
				for (int c=1; c<=channels; c++) {
					imp.setPositionWithoutUpdate(c, imp.getSlice(), imp.getFrame());
					imp.setDisplayRange(min, max);
					//IJ.log("setDisplayRange: "+c+" "+min+" "+max);
				}
				((CompositeImage)imp).reset();
				imp.setPosition(channel, imp.getSlice(), imp.getFrame());
			}
			if (propagate)
				propagate(imp);
			if (IJ.recording()) {
				if (imp.getBitDepth()==32)
					recordSetMinAndMax(min, max);
				else {
					int imin = (int)min;
					int imax = (int)max;
					if (cal.isSigned16Bit()) {
						imin = (int)cal.getCValue(imin);
						imax = (int)cal.getCValue(imax);
					}
					recordSetMinAndMax(imin, imax);
				}
				if (range2>0) {
					if (Recorder.scriptMode())
						Recorder.recordCall("ImagePlus.setDefault16bitRange("+range2+");");
					else
						Recorder.recordString("call(\"ij.ImagePlus.setDefault16bitRange\", "+range2+");\n");
				}
			}
		}
	}

	/** Implements the "Propagate to all other ... images" of the "Set" dialog.
	 *  Does not propagate to RGB images because this would modify the pixel values. */
	private void propagate(ImagePlus img) {
		if (isRGB(img)) {
			GenericDialog gd = new GenericDialog("Contrast Adjuster");
			gd.addMessage( "Propagation of RGB images not supported. As a work-around,\nconvert images to multi-channel composite color.");
			gd.hideCancelButton();
			gd.showDialog();
			return;
		}
		int[] list = WindowManager.getIDList();
		if (list==null) return;
		int nImages = list.length;
		if (nImages<=1) return;
		ImageProcessor ip = img.getProcessor();
			if (ip == null || img.getProcessor()==null) return; // image asynchronously closed
		double min = ip.getMin();
		double max = ip.getMax();
		int depth = img.getBitDepth();
		if (depth==24) return;
		int id = img.getID();
		if (img.isComposite()) {
			int nChannels = img.getNChannels();
			for (int i=0; i<nImages; i++) {
				ImagePlus img2 = WindowManager.getImage(list[i]);
				if (img2==null) continue;
				int nChannels2 = img2.getNChannels();
				if (img2.isComposite() && img2.getBitDepth()==depth && img2.getID()!=id
				&& img2.getNChannels()==nChannels && img2.getWindow()!=null) {
					int channel = img2.getChannel();
					for (int c=1; c<=nChannels; c++) {
						LUT  lut = ((CompositeImage)img).getChannelLut(c);
						img2.setPosition(c, img2.getSlice(), img2.getFrame());
						img2.setDisplayRange(lut.min, lut.max);
						img2.updateAndDraw();
					}
					img2.setPosition(channel, img2.getSlice(), img2.getFrame());
				}
			}
		} else {
			for (int i=0; i<nImages; i++) {
				ImagePlus img2 = WindowManager.getImage(list[i]);
				if (img2!=null && img2.getBitDepth()==depth && img2.getID()!=id
				&& img2.getNChannels()==1 && img2.getWindow()!=null) {
					ImageProcessor ip2 = img2.getProcessor();
					ip2.setMinAndMax(min, max);
					img2.updateAndDraw();
				}
			}
		}
	}

	/** 16-bit images: converts the 2^n exponent of the global default 16-bit range held in
	 *  ImagePlus.set/getDefault16bitRange to the corresponding index in sixteenBitRanges
	 *  as used by the 'Unsigned 16-bit range' Choice of the 'Set' dialog.
	 *  Index 0 means 'automatic' */
	public static int get16bitRangeIndex() {
		int range = ImagePlus.getDefault16bitRange();
		int index = 0;
		if (range==8) index = 1;
		else if (range==10) index = 2;
		else if (range==12) index = 3;
		else if (range==14) index = 4;
		else if (range==15) index = 5;
		else if (range==16) index = 6;
		return index;
	}

	/** 16-bit images: Takes the index in sixteenBitRanges (used by the 'Unsigned 16-bit range' Choice
	 *  of the 'Set' dialog) as input and sets the global ImagePlus.getDefault16bitRange accordingly.
	 *  (This option can be also set in Edit>Options>Appearance.)
	 *  Returns the ImagePlus.getDefault16bitRange range (this is the exponent n for a range of 2^n,
	 *  or 0 for 'automatic') */
	public static int set16bitRange(int index) {
		int range = 0;
		if (index==1) range = 8;
		else if (index==2) range = 10;
		else if (index==3) range = 12;
		else if (index==4) range = 14;
		else if (index==5) range = 15;
		else if (index==6) range = 16;
		ImagePlus.setDefault16bitRange(range);
		return range;
	}

	public static String[] getSixteenBitRanges() {
		return sixteenBitRanges;
	}

	/** Called if the Set button was pressed in Window/Level mode */
	void setWindowLevel(ImagePlus imp, ImageProcessor ip) {
		min = imp.getDisplayRangeMin();
		max = imp.getDisplayRangeMax();
		Calibration cal = imp.getCalibration();
		double minValue = cal.getCValue(min);
		double maxValue = cal.getCValue(max);
		//IJ.log("setWindowLevel: "+min+" "+max);
		double windowValue = maxValue - minValue;
		double levelValue = minValue + windowValue/2.0;
		GenericDialog gd = new GenericDialog("Set W&L");
		gd.addNumericField("Window Center (Level): ", levelValue, digits, 9, "");
		gd.addNumericField("Window Width: ", windowValue, digits, 9, "");
		gd.addCheckbox("Propagate to all open images", false);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		levelValue = gd.getNextNumber();
		windowValue = gd.getNextNumber();
		minValue = levelValue-(windowValue/2.0);
		maxValue = levelValue+(windowValue/2.0);
		minValue = cal.getRawValue(minValue);
		maxValue = cal.getRawValue(maxValue);
		boolean propagate = gd.getNextBoolean();
		if (maxValue>=minValue) {
			min = minValue;
			max = maxValue;
			setMinAndMax(imp, minValue, maxValue);
			updateScrollBars(null, false);
			if (isRGB(imp)) restoreOutsideMask(imp, ip);
			if (propagate)
				propagate(imp);
			if (IJ.recording()) {
				if (imp.getBitDepth()==32)
					recordSetMinAndMax(min, max);
				else {
					int imin = (int)min;
					int imax = (int)max;
					if (cal.isSigned16Bit()) {
						imin = (int)cal.getCValue(imin);
						imax = (int)cal.getCValue(imax);
					}
					recordSetMinAndMax(imin, imax);
				}
			}
		}
	}

	public static void recordSetMinAndMax(double min, double max) {
		if ((int)min==min && (int)max==max) {
			int imin=(int)min, imax = (int)max;
			if (Recorder.scriptMode()) {
				Recorder.recordCall("imp.setDisplayRange("+imin+", "+imax+");");
				Recorder.recordCall("imp.updateAndDraw();");
			} else
				Recorder.record("setMinAndMax", imin, imax);
		} else {
			if (Recorder.scriptMode()) {
				Recorder.recordCall("imp.setDisplayRange("+ResultsTable.d2s(min,2)+", "+ResultsTable.d2s(max,2)+");");
				Recorder.recordCall("imp.updateAndDraw();");
			} else
				Recorder.recordString("setMinAndMax("+ResultsTable.d2s(min,2)+", "+ResultsTable.d2s(max,2)+");");
		}
	}

	/** Separate thread that does the potentially time-consuming processing */
	public void run() {
		while (!done) {
			int task;
			synchronized(this) {
				while ((task = nextTask()) < 0 && !done) {  // get task from 'tasksPending'
					try {wait();}               // nothing to do, wait
					catch(InterruptedException e) {}
				}
			}
			if (done) return;
			ImagePlus imp = WindowManager.getCurrentImage();
			try {
				if (imp != null && imp.getID() != currentImageID) {
					doTask(SETUP, imp);       // image has changed without a SETUP request
					if (task == SETUP  || task >= FIRST_SLIDER)
						continue;               // setup done; also skip any stray slider tasks
				}
				doTask(task, imp);            // do the operation retrieved from 'tasksPending'
			} catch (Throwable e) {
				ImageProcessor ip = imp==null ? null : imp.getProcessor();
				if (ip!=null && ip.getPixels()!=null)   // otherwise the image was closed during the task: ignore
					IJ.handleException(e);      // report any exception but keep B&C working
			}
		}
	}

	/** Returns the index of the next task to do and removes it.
	 *  Returns -1 if nothing to do.
	 *  Call only from synchronized(this) */
	private int nextTask() {
		for (int i=0; i<taskPending.length; i++)
			if (taskPending[i]) {
				taskPending[i] = false;
				return i;
			}
		return -1;
	}

	/** Handles one task in the background thread */
	void doTask(int task, ImagePlus imp) {
		//IJ.log("doTask task="+task+" imp="+imp);
		boolean locked = false;                 // whether we have locked the (RGB) image and must unlock it
		try {                                   //'try' ensures that imp.lock() for RGB images is followed by unlock in case of an exception
			if (task==SETUP) {                  // B&C window activated: the image may have changed, update
				if (imp != null && !(IJ.isMacro() && imp.getBitDepth()==24)) // macro: avoid that setup could modify an RGB image
					setup(imp);
				return;
			}
			if (imp==null) {                    // all other tasks need an image
				IJ.beep();
				IJ.showStatus("No image");
				return;
			}
			if (imp.getOverlay()!=null && imp.getOverlay().isCalibrationBar()) {
				IJ.beep();                      // if we have a color/grayscale calibration bar, it would be wrong after adjusting
				IJ.showStatus("Has calibration bar");
				return;
			}
			ImageProcessor ip = imp.getProcessor();
			if (ip == null || ip.getPixels()==null)
				return;                         // image asynchronously closed
			boolean isRGBImage = isRGB(imp);
			if (isRGBImage) {
				if (!imp.lock())
					{imp=null; return;}         // RGB: pixels may be modified, lock the image
				locked = true;

				if (newRGBChannel) {            // Color dialog: other channel selected, make adjustments so far permanent
					((ColorProcessor)ip).caSnapshot(false);
					newRGBChannel = false;
				}
				ensureOwnRGBSnapshot(ip);       // We need our own snapshot; make one if pixels were modified by another operation
			}
			switch (task) {
				case RESET: reset(imp, ip, true); break;
				case AUTO: autoAdjust(imp, ip); break;
				case SET: if (windowLevel) setWindowLevel(imp, ip); else setMinAndMax(imp, ip); break;
				case APPLY: apply(imp, ip); break;
				case MIN: adjustMin(imp, ip, minSlider.getValue()); break;
				case MAX: adjustMax(imp, ip, maxSlider.getValue()); break;
				case BRIGHTNESS: adjustBrightness(imp, ip, brightnessSlider.getValue()); break;
				case CONTRAST: adjustContrast(imp, ip, contrastSlider.getValue()); break;
			}
			plotHistogram(imp);
			updatePlot();
			updateLabels(imp);
			if ((IJ.shiftKeyDown()||(balance&&channels==7)) && imp.isComposite())
				((CompositeImage)imp).updateAllChannelsAndDraw();
			else
				imp.updateChannelAndDraw();
			if (isRGBImage)
				rememberRGBPixels(ip);
		} finally {
			if (locked)
				imp.unlock();
		}
	}

	/** Overrides close() in PlugInDialog. */
	public void close() {
		super.close();
		instance = null;
		done = true;                         // tells the background thread to terminate
		synchronized(this) {
			notify();
		}
		Prefs.saveLocation(LOC_KEY, getLocation());
	}

	/** When the B&C frame comes to the foreground, the image may have changed, do a setup. */
	public void windowActivated(WindowEvent e) {
		super.windowActivated(e);
		// The setup is always done by the background thread. This also happens after any of the
		// ContrastAdjusters dialogs (e.g. 'Set'), which re-activates this window when the dialog
		// closes. Single-threaded operation avoids race conditions 
		requestSetup();
		WindowManager.setWindow(this);
	}

	/** Resets this ContrastAdjuster and brings it to the front. */
	public void updateAndDraw() {
		requestSetup();
		toFront();
	}

	/** Requests the background thread to update the ContrastAdjuster, i.e. do a new setup
	 *  Called by ImagePlus.setSlice() and others */
	public static void update() {
		ContrastAdjuster ca = instance;     // local copy: 'instance' may become null in another thread
		if (ca!=null)
		ca.requestSetup();
	}

} // ContrastAdjuster class


class ContrastPlot extends Canvas {
	Color[] hColors;
	static final int WIDTH=128, HEIGHT=64;
	double defaultMin = 0;
	double defaultMax = 255;
	double min = 0;
	double max = 255;
	int[] histogram;
	double histStart, histBinSize;  // value of the start of histogram bin 0, bin width (raw)
	int hmax;
	boolean isRGB;
	Image os;                       // histogram image
	double scale = Prefs.getGuiScale();
	int width = WIDTH;
	int height = HEIGHT;

	public ContrastPlot() {
		width = (int)(width*1.3);   // increase size w.r.t. old default 128x84 pxl
		height = (int)(height*1.3);
		if (scale>1.0) {
			width = (int)(width*scale);
			height = (int)(height*scale);
		}
		setSize(width+1, height+1);
	}

	/** Overrides Component getPreferredSize(). Added to work
	 *  around a bug in Java 1.4.1 on Mac OS X.*/
	public Dimension getPreferredSize() {
		return new Dimension(width+1, height+1);
	}

	/** Sets the data and prepares for the histogram plot.
	 *  Synchronized with paint() to avoid inconsistent data in paint. */
	synchronized void setHistogram(ImagePlus imp, ImageStatistics stats, boolean isLogHist, double histStart, double histBinSize, boolean isRGB) {
		ImageProcessor ip = imp.getProcessor();
		if (ip == null || stats == null) return;
		histogram = stats.histogram;
		this.histStart = histStart;
		this.histBinSize = histBinSize;
		this.isRGB = isRGB;
		if (histogram.length!=256) {
			histogram = null;
			return;
		}
		if (isLogHist) {
			for (int j=0;j<256;j++) {
				histogram[j]=(int)(Math.log(histogram[j]+1)*100);
			}
		}
		hColors = new Color[256];
		for (int i=0; i<256; i++) { // set the default histogram color when there is no LUT
			hColors[i] = new Color(110, 110,150);
		}
		int impType= imp.getType();
		if ((impType == ImagePlus.GRAY8) || (impType == ImagePlus.GRAY16) || (impType == ImagePlus.GRAY32)) { //if image has LUT
			ColorModel cm = ip.getColorModel();
			if (cm instanceof IndexColorModel && ((IndexColorModel)cm).getMapSize()==256) {
				IndexColorModel icm = (IndexColorModel)cm;
				int mapSize = icm.getMapSize();
				if (mapSize==256) {
					byte[] red = new byte[256];
					byte[] green = new byte[256];
					byte[] blue = new byte[256];
					icm.getReds(red);
					icm.getGreens(green);
					icm.getBlues(blue);
					for (int i=0; i<256; i++) {
						hColors[i] = new Color(red[i]&255, green[i]&255, blue[i]&255);
					}
				}
			}
		}
		int maxCount = 0;
		int mode = 0;
		for (int i=0; i<256; i++) {
			if (histogram[i]>maxCount) {
				maxCount = histogram[i];
				mode = i;
			}
		}
		int maxCount2 = 0;
		for (int i=0; i<256; i++) {
			if ((histogram[i]>maxCount2) && (i!=mode))
				maxCount2 = histogram[i];
		}
		if (isLogHist) {
			hmax =(int)(Math.log(stats.maxCount+1)*100);
		} else {
			hmax = stats.maxCount;
		}
		if ((hmax>(maxCount2*2)) && (maxCount2!=0)) {
			hmax = (int)(maxCount2*1.5);
			histogram[mode] = hmax;
		}
		os = null;                      //image invalid after data update
	}

	public void update(Graphics g) {
		paint(g);
	}

	public synchronized void paint(Graphics g) {
		double range = defaultMax-defaultMin;
		boolean validRange = range>0;   // false also for NaN
		int x1, y1, x2, y2;
		double slope = max>min ? height/(max-min) : 0.0;
		if (isRGB) {                    // in RGB mode, histogram changes, not the transfer curve
			x1 = 0;                     // show a diagonal transfer curve, 'max' tick mark hideden by frame (not needed)
			y1 = height;
			x2 = width;
			y2 = 0;
		} else {                        // calculate transfer curve end points
			if (min>=defaultMin) {
				x1 = xPosition(min);
				y1 = height;
			} else {
				x1 = xPosition(defaultMin);
				y1 = max>min ? height-(int)((defaultMin-min)*slope) : height;
			}
			if (max<=defaultMax) {
				x2 = xPosition(max);
				y2 = 0;
			} else {
				x2 = xPosition(defaultMax);
				y2 = max>min ? height-(int)((defaultMax-min)*slope) : 0;
			}
		}
		if (histogram!=null && validRange) {
			if (os==null && hmax!=0) {                      // new histogram, new image required
				os = createImage(width,height);
				if (os == null) return;                     // not displayable, e.g. window closing
				Graphics osg = os.getGraphics();
				osg.setColor(Color.white);
				osg.fillRect(0, 0, width, height);
				
				int[] columnCount = new int[width+1];   	// highest count of the bins in this column
				int[] columnBin = new int[width+1];     	// bin with this count (for the color)
				for (int i=0; i<256; i++) {
					double v = histStart + i*histBinSize;   // value at the start of the bin
					if (!(v>=defaultMin && v<=defaultMax))
						continue;                         	// not on the x axis of the plot
					int x = xPosition(v);
					if (histogram[i]>columnCount[x]) {
						columnCount[x] = histogram[i];
						columnBin[x] = i;
					}
				}
				for (int x=1; x<width; x++) {               // x=0 and x=width are the frame
					if (columnCount[x]==0)
						continue;
					if (hColors!=null)
						osg.setColor(hColors[lutIndex(histStart + columnBin[x]*histBinSize)]);
					int y = height - (int)((long)height*columnCount[x]/hmax);
					if (y<1) y = 1;                         // dot always below the frame line
					osg.drawLine(x, height, x, y);
					osg.setColor(Color.black);
					osg.fillRect(x, y, 1, 1);
				}
				osg.dispose();
			}
			if (os!=null) g.drawImage(os, 0, 0, this);
		} else {
			g.setColor(Color.white);
			g.fillRect(0, 0, width, height);
		}
		g.setColor(Color.black);
		g.drawLine(x1, y1, x2, y2);
		g.drawLine(x2, height-5, x2, height);
		g.drawRect(0, 0, width, height);
	}

	/** Screen x of a value; defaultMin and defaultMax are at the first and last column inside the frame */
	int xPosition(double v) {
		double range = defaultMax-defaultMin;
		if (!(range>0))
			return width/2;
		int x = 1 + (int)Math.round((v-defaultMin)/range*(width-2));
		return x<0 ? 0 : (x>width ? width : x);
	}

	/** Index in the LUT (0-255) for displaying a value with the current display range min-max */
	int lutIndex(double v) {
		int j;
		if (max>min)
			j = (int)((v-min)/(max-min)*255+0.5);
		else
			j = v<min ? 0 : (v>max ? 255 : 128);
		return j<0 ? 0 : (j>255 ? 255 : j);
	}

} // ContrastPlot class

class TrimmedLabel extends Label {
	int trim = IJ.isMacOSX()?0:6;

	public TrimmedLabel(String title) {
		super(title);
	}

	public Dimension getMinimumSize() {
		return new Dimension(super.getMinimumSize().width, super.getMinimumSize().height-trim);
	}

	public Dimension getPreferredSize() {
		return getMinimumSize();
	}

} // TrimmedLabel class



