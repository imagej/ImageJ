package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.measure.*;
import java.awt.event.*;

/** This plugin implements the Analyze/Tools/Scale Bar command.
 * Divakar Ramachandran added options to draw a background 
 * and use a serif font on 23 April 2006.
 * Remi Berthoz added an option to draw vertical scale
 * bars on 17 September 2021.
*/
public class ScaleBar implements PlugIn {

	static final String[] locations = {"Upper Right", "Lower Right", "Lower Left", "Upper Left", "At Selection"};
	static final int UPPER_RIGHT=0, LOWER_RIGHT=1, LOWER_LEFT=2, UPPER_LEFT=3, AT_SELECTION=4;
	static final String[] colors = {"White","Black","Light Gray","Gray","Dark Gray","Red","Green","Blue","Yellow"};
	static final String[] bcolors = {"None","Black","White","Dark Gray","Gray","Light Gray","Yellow","Blue","Green","Red"};
	static final String[] checkboxLabels = {"Horizontal", "Vertical", "Bold Text", "Hide Text", "Serif Font", "Overlay"};
	final static String SCALE_BAR = "|SB|";
	
	private static final ScaleBarConfiguration sConfig = new ScaleBarConfiguration();
	private ScaleBarConfiguration config = new ScaleBarConfiguration(sConfig);

	ImagePlus imp;
	int hBarWidthInPixels;
	int vBarHeightInPixels;
	int roiX, roiY, roiWidth, roiHeight;
	boolean userRoiExists;
	boolean[] checkboxStates = new boolean[6];

	Rectangle hBackground = new Rectangle();
	Rectangle hBar = new Rectangle();
	Rectangle hText = new Rectangle();
	Rectangle vBackground = new Rectangle();
	Rectangle vBar = new Rectangle();
	Rectangle vText = new Rectangle();

	/**
	 * This method is called when the plugin is loaded. 'arg', which
	 * may be blank, is the argument specified for this plugin in
	 * IJ_Props.txt.
	 */
	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
		if (imp == null) {
			IJ.noImage();
			return;
		}
		// Snapshot before anything, so we can revert if the user cancels the action.
		imp.getProcessor().snapshot();

		userRoiExists = parseCurrentROI();
		boolean userOKed = askUserConfiguration(userRoiExists);

		if (!userOKed) {
			removeScalebar();
			return;
		}

		if (!IJ.isMacro())
			persistConfiguration();
		updateScalebar(!config.labelAll);
	 }

	/**
	 * Remove the scalebar drawn by this plugin.
	 * 
	 * If the scalebar was drawn without the overlay by another
	 * instance of the plugin (it is drawn into the image), then
	 * we cannot remove it.
	 * 
	 * If the scalebar was drawn using the overlay by another
	 * instance of the plugin, then we can remove it.
	 * 
	 * With or without the overlay, we can remove a scalebar
	 * drawn by this instance of the plugin.
	 */
	void removeScalebar() {
		// Revert with Undo, in case "Use Overlay" is not ticked
		imp.getProcessor().reset();
		imp.updateAndDraw();
		// Remove overlay drawn by this plugin, in case "Use Overlay" is ticked
		Overlay overlay = imp.getOverlay();
		if (overlay!=null) {
			overlay.remove(SCALE_BAR);
			imp.draw();
		}
	}

	/**
	 * If there is a user selected ROI, set the class variables {roiX}
	 * and {roiY}, {roiWidth}, {roiHeight} to the corresponding
	 * features of the ROI, and return true. Otherwise, return false.
	 */
    boolean parseCurrentROI() {
        Roi roi = imp.getRoi();
        if (roi == null) return false;

        Rectangle r = roi.getBounds();
        roiX = r.x;
        roiY = r.y;
		roiWidth = r.width;
		roiHeight = r.height;
        return true;
    }

	/**
	 * There is no hard codded value for the width of the scalebar,
	 * when the plugin is called for the first time in an ImageJ
	 * instance, a defautl value for the width will be computed by
	 * this method.
	 */
	void computeDefaultBarWidth(boolean currentROIExists) {
		Calibration cal = imp.getCalibration();
		ImageWindow win = imp.getWindow();
		double mag = (win!=null)?win.getCanvas().getMagnification():1.0;
		if (mag>1.0)
			mag = 1.0;

		double pixelWidth = cal.pixelWidth;
		if (pixelWidth==0.0)
			pixelWidth = 1.0;
		double pixelHeight = cal.pixelHeight;
		if (pixelHeight==0.0)
			pixelHeight = 1.0;
		double imageWidth = imp.getWidth()*pixelWidth;
		double imageHeight = imp.getHeight()*pixelHeight;

		if (currentROIExists && roiX>=0 && roiWidth>10) {
			// If the user has a ROI, set the bar width according to ROI width.
			config.hBarWidth = roiWidth*pixelWidth;
		}
		else if (config.hBarWidth<=0.0 || config.hBarWidth>0.67*imageWidth) {
			// If the bar is of negative width or too wide for the image,
			// set the bar width to 80 pixels.
			config.hBarWidth = (80.0*pixelWidth)/mag;
			if (config.hBarWidth>0.67*imageWidth)
				// If 80 pixels is too much, do 2/3 of the image.
				config.hBarWidth = 0.67*imageWidth;
			if (config.hBarWidth>5.0)
				// If the resulting size is larger than 5 units, round the value.
				config.hBarWidth = (int) config.hBarWidth;
		}

		if (currentROIExists && roiY>=0 && roiHeight>10) {
			config.vBarHeight = roiHeight*pixelHeight;
		}
		else if (config.vBarHeight<=0.0 || config.vBarHeight>0.67*imageHeight) {
			config.vBarHeight = (80.0*pixelHeight)/mag;
			if (config.vBarHeight>0.67*imageHeight)
				// If 80 pixels is too much, do 2/3 of the image.
				config.vBarHeight = 0.67*imageHeight;
			if (config.vBarHeight>5.0)
				// If the resulting size is larger than 5 units, round the value.
				config.vBarHeight = (int) config.vBarHeight;
		}
	} 

	/**
	 * Genreate & draw the configuration dialog.
	 * 
	 * Return the value of dialog.wasOKed() when the user clicks OK
	 * or Cancel.
	 */
	boolean askUserConfiguration(boolean currentROIExists) {
		// Update the user configuration if there is an ROI, or if
		// the defined bar width is negative (it is if it has never
		// been set in this ImageJ instance).
		if (currentROIExists) {
			config.location = locations[AT_SELECTION];
		}
		if (config.hBarWidth <= 0 || config.vBarHeight <= 0 || currentROIExists) {
			computeDefaultBarWidth(currentROIExists);
		}
		if (IJ.isMacro()) {
			config.updateFrom(new ScaleBarConfiguration());
		}

		// Draw a first preview scalebar, with the default or presisted
		// configuration.
		updateScalebar(true);
		
		// Create & show the dialog, then return.
		boolean multipleSlices = imp.getStackSize() > 1;
		GenericDialog dialog = new BarDialog(getHUnit(), getVUnit(), config.hDigits, config.vDigits, multipleSlices);
		DialogListener dialogListener = new BarDialogListener(multipleSlices);
		dialog.addDialogListener(dialogListener);
		dialog.showDialog();
		return dialog.wasOKed();
	}

	/**
	 * Store the active configuration into the static variable that
	 * is persisted across calls of the plugin.
	 * 
	 * The "active" configuration is normally the one reflected by
	 * the dialog.
	 */
	void persistConfiguration() {
		sConfig.updateFrom(config);
	}
	
	/**
	 * Return the X unit strings defined in the image calibration.
	 */
	String getHUnit() {
		String hUnits = imp.getCalibration().getXUnit();
		if (hUnits.equals("microns"))
			hUnits = IJ.micronSymbol+"m";
		return hUnits;
	}

	/**
	 * Return the Y unit strings defined in the image calibration.
	 */
	String getVUnit() {
		String vUnits = imp.getCalibration().getYUnit();
		if (vUnits.equals("microns"))
			vUnits = IJ.micronSymbol+"m";
		return vUnits;
	}

	/**
	 * Create & draw the scalebar using an Overlay.
	 */
	Overlay createScaleBarOverlay() throws MissingRoiException {
		Overlay overlay = new Overlay();

		Color color = getColor();
		Color bcolor = getBColor();
		
		int fontType = config.boldText?Font.BOLD:Font.PLAIN;
		String face = config.serifFont?"Serif":"SanSerif";
		Font font = new Font(face, fontType, config.fontSize);
		ImageProcessor ip = imp.getProcessor();
		ip.setFont(font);

		setElementsPositions(ip);

		if (bcolor != null) {
			if (config.showHorizontal) {
				Roi hBackgroundRoi = new Roi(hBackground.x, hBackground.y, hBackground.width, hBackground.height);
				hBackgroundRoi.setFillColor(bcolor);
				overlay.add(hBackgroundRoi, SCALE_BAR);
			}
			if (config.showVertical) {
				Roi vBackgroundRoi = new Roi(vBackground.x, vBackground.y, vBackground.width, vBackground.height);
				vBackgroundRoi.setFillColor(bcolor);
				overlay.add(vBackgroundRoi, SCALE_BAR);
			}
		}

		if (config.showHorizontal) {
			Roi hBarRoi = new Roi(hBar.x, hBar.y, hBar.width, hBar.height);
			hBarRoi.setFillColor(color);
			overlay.add(hBarRoi, SCALE_BAR);
		}
		if (config.showVertical) {
			Roi vBarRoi = new Roi(vBar.x, vBar.y, vBar.width, vBar.height);
			vBarRoi.setFillColor(color);
			overlay.add(vBarRoi, SCALE_BAR);
		}

		if (!config.hideText) {
			if (config.showHorizontal) {
				TextRoi hTextRoi = new TextRoi(hText.x, hText.y, getHLabel(), font);
				hTextRoi.setStrokeColor(color);
				overlay.add(hTextRoi, SCALE_BAR);
			}
			if (config.showVertical) {
				TextRoi vTextRoi = new TextRoi(vText.x, vText.y + vText.height, getVLabel(), font);
				vTextRoi.setStrokeColor(color);
				vTextRoi.setAngle(90.0);
				overlay.add(vTextRoi, SCALE_BAR);
			}
		}

		return overlay;
	}

	/**
	 * Returns the text to draw near the scalebar (<width> <unit>).
	 */
	String getHLabel() {
		return IJ.d2s(config.hBarWidth, config.hDigits) + " " + getHUnit();
	}

	/**
	 * Returns the text to draw near the scalebar (<height> <unit>).
	 */
	String getVLabel() {
		return IJ.d2s(config.vBarHeight, config.vDigits) + " " + getVUnit();
	}

	/**
	 * Returns the width of the box that contains the horizontal scalebar and
	 * its label.
	 */
	int getHBoxWidthInPixels() {
		updateFont();
		ImageProcessor ip = imp.getProcessor();
		int hLabelWidth = config.hideText ? 0 : ip.getStringWidth(getHLabel());
		int hBoxWidth = Math.max(hBarWidthInPixels, hLabelWidth);
		return (config.showHorizontal ? hBoxWidth : 0);
	}

	/**
	 * Returns the height of the box that contains the horizontal scalebar and
	 * its label.
	 */
	int getHBoxHeightInPixels() {
		int hLabelHeight = config.hideText ? 0 : config.fontSize;
		int hBoxHeight = config.barThicknessInPixels + (int) (hLabelHeight * 1.25);
		return (config.showHorizontal ? hBoxHeight : 0);
	}

	/**
	 * Returns the height of the box that contains the vertical scalebar and
	 * its label.
	 */
	int getVBoxHeightInPixels() {
		updateFont();
		ImageProcessor ip = imp.getProcessor();
		int vLabelHeight = config.hideText ? 0 : ip.getStringWidth(getVLabel());
		int vBoxHeight = Math.max(vBarHeightInPixels, vLabelHeight);
		return (config.showVertical ? vBoxHeight : 0);
	}

	/**
	 * Returns the width of the box that contains the vertical scalebar and
	 * its label.
	 */
	int getVBoxWidthInPixels() {
		int vLabelWidth = config.hideText ? 0 : config.fontSize;
		int vBoxWidth = config.barThicknessInPixels + (int) (vLabelWidth * 1.25);
		return (config.showVertical ? vBoxWidth : 0);
	}

	/**
	 * Returns the size of margins that should be displayed between the scalebar
	 * elements and the image edge.
	 */
	int getOuterMarginSizeInPixels() {
		int imageWidth = imp.getWidth();
		int imageHeight = imp.getHeight();
		return (imageWidth + imageHeight) / 100;
	}

	/**
	 * Retruns the size of margins that should be displayed between the scalebar
	 * elements and the edge of the element's backround.
	 */
	int getInnerMarginSizeInPixels() {
		int maxWidth = Math.max(getHBoxWidthInPixels(), getVBoxHeightInPixels());
		int margin = Math.max(maxWidth/20, 2);
		return config.bcolor.equals("None") ? 0 : margin;
	}

	void updateFont() {
		int fontType = config.boldText?Font.BOLD:Font.PLAIN;
		String font = config.serifFont?"Serif":"SanSerif";
		ImageProcessor ip = imp.getProcessor();
		ip.setFont(new Font(font, fontType, config.fontSize));
		ip.setAntialiasedText(true);
	}

	/**
	 * Sets the positions x y of hBackground and vBackground based on
	 * the current configuration.
	 */
	void setBackgroundBoxesPositions(ImageProcessor ip) throws MissingRoiException {
		Calibration cal = imp.getCalibration();
		hBarWidthInPixels = (int)(config.hBarWidth/cal.pixelWidth);
		vBarHeightInPixels = (int)(config.vBarHeight/cal.pixelHeight);

		int imageWidth = imp.getWidth();
		int imageHeight = imp.getHeight();
		int hBoxWidth = getHBoxWidthInPixels();
		int hBoxHeight = getHBoxHeightInPixels();
		int vBoxWidth = getVBoxWidthInPixels();
		int vBoxHeight = getVBoxHeightInPixels();
		int outerMargin = getOuterMarginSizeInPixels();
		int innerMargin = getInnerMarginSizeInPixels();
		
		hBackground.width = innerMargin + hBoxWidth + innerMargin;
		hBackground.height = innerMargin + hBoxHeight + innerMargin;
		vBackground.width = innerMargin + vBoxWidth + innerMargin;
		vBackground.height = innerMargin + vBoxHeight + innerMargin;

		if (config.location.equals(locations[UPPER_RIGHT])) {
			hBackground.x = imageWidth - outerMargin - innerMargin - vBoxWidth - hBoxWidth - innerMargin;
			hBackground.y = outerMargin;
			vBackground.x = imageWidth - outerMargin - innerMargin - vBoxWidth - innerMargin;
			vBackground.y = outerMargin;

		} else if (config.location.equals(locations[LOWER_RIGHT])) {
			hBackground.x = imageWidth - outerMargin - innerMargin - vBoxWidth - hBoxWidth + (config.showVertical ? config.barThicknessInPixels : 0) - innerMargin;
			hBackground.y = imageHeight - outerMargin - innerMargin - hBoxHeight - innerMargin;
			vBackground.x = imageWidth - outerMargin - innerMargin - vBoxWidth - innerMargin;
			vBackground.y = imageHeight - outerMargin - innerMargin - hBoxHeight + (config.showHorizontal ? config.barThicknessInPixels : 0) - vBoxHeight - innerMargin;
			vBackground.height += (config.showHorizontal ? hBoxHeight - config.barThicknessInPixels : 0);

		} else if (config.location.equals(locations[UPPER_LEFT])) {
			hBackground.x = outerMargin;
			hBackground.y = outerMargin;
			vBackground.x = outerMargin;
			vBackground.y = outerMargin;

		} else if (config.location.equals(locations[LOWER_LEFT])) {
			hBackground.x = outerMargin;
			hBackground.y = imageHeight - outerMargin - innerMargin - hBoxHeight - innerMargin;
			vBackground.x = outerMargin;
			vBackground.y = imageHeight - outerMargin - innerMargin - hBoxHeight + (config.showHorizontal ? config.barThicknessInPixels : 0) - vBoxHeight - innerMargin;

		} else {
			if (!userRoiExists)
				throw new MissingRoiException();

			hBackground.x = roiX;
			hBackground.y = roiY;
			vBackground.x = roiX;
			vBackground.y = roiY;
		}
	}

	/**
	 * Sets the rectangles x y positions for scalebar elements (hBar, hText, vBar, vText),
	 * based on the current configuration. Also sets the width and height of the rectangles.
	 * 
	 * The position of each rectangle is relative to hBackground and vBackground,
	 * so setBackgroundBoxesPositions() must run before this method computes positions.
	 * This method calls setBackgroundBoxesPositions().
	 */
	void setElementsPositions(ImageProcessor ip) throws MissingRoiException {

		setBackgroundBoxesPositions(ip);

		int hBoxWidth = getHBoxWidthInPixels();
		int hBoxHeight = getHBoxHeightInPixels();

		int vBoxWidth = getVBoxWidthInPixels();
		int vBoxHeight = getVBoxHeightInPixels();

		int innerMargin = getInnerMarginSizeInPixels();
		
		hBar.x = hBackground.x + innerMargin + (hBoxWidth - hBarWidthInPixels)/2;
		hBar.y = hBackground.y + innerMargin;
		hBar.width = hBarWidthInPixels;
		hBar.height = config.barThicknessInPixels;

		hText.height = config.hideText ? 0 : config.fontSize;
		hText.width = config.hideText ? 0 : ip.getStringWidth(getHLabel());
		hText.x = hBackground.x + innerMargin + (hBoxWidth - hText.width)/2;
		hText.y = hBar.y + hBar.height;

		vBar.width = config.barThicknessInPixels;
		vBar.height = vBarHeightInPixels;
		vBar.x = vBackground.x + innerMargin;
		vBar.y = vBackground.y + innerMargin + (vBoxHeight - vBar.height)/2;

		vText.height = config.hideText ? 0 : ip.getStringWidth(getVLabel());
		vText.width = config.hideText ? 0 : config.fontSize;
		vText.x = vBar.x + vBar.width;
		vText.y = vBackground.y + innerMargin + (vBoxHeight - vText.height)/2;
	}

	Color getColor() {
		Color c = Color.black;
		if (config.color.equals(colors[0])) c = Color.white;
		else if (config.color.equals(colors[2])) c = Color.lightGray;
		else if (config.color.equals(colors[3])) c = Color.gray;
		else if (config.color.equals(colors[4])) c = Color.darkGray;
		else if (config.color.equals(colors[5])) c = Color.red;
		else if (config.color.equals(colors[6])) c = Color.green;
		else if (config.color.equals(colors[7])) c = Color.blue;
		else if (config.color.equals(colors[8])) c = Color.yellow;
	   return c;
	}

	// Div., mimic getColor to write getBColor for bkgnd	
	Color getBColor() {
		if (config.bcolor==null || config.bcolor.equals(bcolors[0])) return null;
		Color bc = Color.white;
		if (config.bcolor.equals(bcolors[1])) bc = Color.black;
		else if (config.bcolor.equals(bcolors[3])) bc = Color.darkGray;
		else if (config.bcolor.equals(bcolors[4])) bc = Color.gray;
		else if (config.bcolor.equals(bcolors[5])) bc = Color.lightGray;
		else if (config.bcolor.equals(bcolors[6])) bc = Color.yellow;
		else if (config.bcolor.equals(bcolors[7])) bc = Color.blue;
		else if (config.bcolor.equals(bcolors[8])) bc = Color.green;
		else if (config.bcolor.equals(bcolors[9])) bc = Color.red;
		return bc;
	}

	/**
	 * Draw the scale bar, based on the current configuration.
	 * 
	 * If {previewOnly} is true, only the active slice will be
	 * labeled with a scalebar. If it is false, all slices of
	 * the stack will be labeled.
	 * 
	 * This method chooses whether to use an overlay or the
	 * drawing tool to create the scalebar.
	 */
	void updateScalebar(boolean previewOnly) {
		removeScalebar();

		Overlay scaleBarOverlay;
		try {
			scaleBarOverlay = createScaleBarOverlay();
		} catch (MissingRoiException e) {
			return; // Simply don't draw the scalebar.
		}

		Overlay impOverlay = imp.getOverlay();
		if (impOverlay==null) {
			impOverlay = new Overlay();
		}

		if (config.useOverlay) {
			for (Roi roi : scaleBarOverlay)
				impOverlay.add(roi);
			imp.setOverlay(impOverlay);
		} else {
			if (previewOnly) {
				ImageProcessor ip = imp.getProcessor();
				drawOverlayOnProcessor(scaleBarOverlay, ip);
				imp.updateAndDraw();
			} else {
				ImageStack stack = imp.getStack();
				for (int i=1; i<=stack.size(); i++) {
					ImageProcessor ip = stack.getProcessor(i);
					drawOverlayOnProcessor(scaleBarOverlay, ip);
					imp.updateAndDraw();
				}
				imp.setStack(stack);
			}
		}
	}

	void drawOverlayOnProcessor(Overlay overlay, ImageProcessor processor) {
		if (processor.getBitDepth() == 8 || processor.getBitDepth() == 24) {
			// drawOverlay() only works for 8-bits and RGB
			processor.drawOverlay(overlay);
			return;
		}
		ImageProcessor ip = new ByteProcessor(imp.getWidth(), imp.getHeight());
		ip.drawOverlay(overlay);
		for (int y = 0; y < ip.getHeight(); y++)
			for (int x = 0; x < ip.getWidth(); x++) {
				int p = ip.get(x, y);
				if (p > 0)
					processor.putPixelValue(x, y, p / 255. * (processor.getMax() - processor.getMin()) + processor.getMin());
			}
	}

   class BarDialog extends GenericDialog {

		BarDialog(String hUnits, String vUnits, int hDigits, int vDigits, boolean multipleSlices) {
			super("Scale Bar");

			addNumericField("Width in "+hUnits+": ", config.hBarWidth, hDigits);
			addNumericField("Height in "+vUnits+": ", config.vBarHeight, vDigits);
			addNumericField("Thickness in pixels: ", config.barThicknessInPixels, 0);
			addNumericField("Font size: ", config.fontSize, 0);
			addChoice("Color: ", colors, config.color);
			addChoice("Background: ", bcolors, config.bcolor);
			addChoice("Location: ", locations, config.location);
			checkboxStates[0] = config.showHorizontal; checkboxStates[1] = config.showVertical;
			checkboxStates[2] = config.boldText; checkboxStates[3] = config.hideText;
			checkboxStates[4] = config.serifFont; checkboxStates[5] = config.useOverlay;
			setInsets(10, 25, 0);
			addCheckboxGroup(3, 2, checkboxLabels, checkboxStates);

			// For simplicity of the itemStateChanged() method below,
			// is is best to keep the "Label all slices" checkbox in
			// the last position.
			if (multipleSlices) {
				setInsets(0, 25, 0);
				addCheckbox("Label all slices", config.labelAll);
			}
		}
   } //BarDialog inner class

	class BarDialogListener implements DialogListener {

		boolean multipleSlices;

		public BarDialogListener(boolean multipleSlices) {
			super();
			this.multipleSlices = multipleSlices;
		}

		@Override
		public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
			config.hBarWidth = gd.getNextNumber();
			config.vBarHeight = gd.getNextNumber();
			config.barThicknessInPixels = (int)gd.getNextNumber();
			config.fontSize = (int)gd.getNextNumber();
			config.color = gd.getNextChoice();
			config.bcolor = gd.getNextChoice();
			config.location = gd.getNextChoice();
			config.showHorizontal = gd.getNextBoolean();
			config.showVertical = gd.getNextBoolean();
			if (!IJ.isMacro() && !config.showHorizontal && !config.showVertical)
				config.showHorizontal = true;
			config.boldText = gd.getNextBoolean();
			config.hideText = gd.getNextBoolean();
			config.serifFont = gd.getNextBoolean();
			config.useOverlay = gd.getNextBoolean();
			if (multipleSlices)
				config.labelAll = gd.getNextBoolean();
			if (!config.showHorizontal && !config.showVertical) {
				// Previous versions of this plugin did not handle vertical scale bars:
				// the macro syntax was different in that "height" meant "thickness" of
				// the horizontal scalebar.
				// If the conditional above is true, then the macro syntax is the old
				// one, so we swap a few config variables.
				config.showHorizontal = true;
				config.barThicknessInPixels = (int)config.vBarHeight;
				config.vBarHeight = 0.0;
			}
			updateScalebar(true);
			return true;
		}
	}

   class MissingRoiException extends Exception {
		MissingRoiException() {
			super("Scalebar location is set to AT_SELECTION but there is no selection on the image.");
		}
   } //MissingRoiException inner class

	static class ScaleBarConfiguration {
	
		private static int defaultBarHeight = 4;

		boolean showHorizontal;
		boolean showVertical;
		double hBarWidth;
		double vBarHeight;
		int hDigits;  // The number of digits after the decimal point that the user input in the dialog for vBarWidth.
		int vDigits;
		int barThicknessInPixels;
		String location;
		String color;
		String bcolor;
		boolean boldText;
		boolean hideText;
		boolean serifFont;
		boolean useOverlay;
		int fontSize;
		boolean labelAll;

		/**
		 * Create ScaleBarConfiguration with default values.
		 */
		ScaleBarConfiguration() {
			this.showHorizontal = true;
			this.showVertical = false;
			this.hBarWidth = -1;
			this.vBarHeight = -1;
			this.barThicknessInPixels = defaultBarHeight;
			this.location = locations[LOWER_RIGHT];
			this.color = colors[0];
			this.bcolor = bcolors[0];
			this.boldText = true;
			this.hideText = false;
			this.serifFont = false;
			this.useOverlay = true;
			this.fontSize = 14;
			this.labelAll = false;
		}

		/**
		 * Copy constructor.
		 */
		ScaleBarConfiguration(ScaleBarConfiguration model) {
			this.updateFrom(model);
		}
		
		void updateFrom(ScaleBarConfiguration model) {
			this.showHorizontal = model.showHorizontal;
			this.showVertical = model.showVertical;
			this.hBarWidth = model.hBarWidth;
			this.vBarHeight = model.vBarHeight;
			this.hDigits = model.hDigits;
			this.vDigits = model.vDigits;
			this.barThicknessInPixels = model.barThicknessInPixels;
			this.location = locations[LOWER_RIGHT];
			this.color = model.color;
			this.bcolor = model.bcolor;
			this.boldText = model.boldText;
			this.serifFont = model.serifFont;
			this.hideText = model.hideText;
			this.useOverlay = model.useOverlay;
			this.fontSize = model.fontSize;
			this.labelAll = model.labelAll;
		}
	} //ScaleBarConfiguration inner class

} //ScaleBar class