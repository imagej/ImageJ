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
	int xloc, yloc;
	int hBarWidthInPixels;
	int vBarHeightInPixels;
	int roiX, roiY, roiWidth, roiHeight;
	boolean userRoiExists;
	boolean[] checkboxStates = new boolean[6];

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

		// Draw a first preview scalebar, with the default or presisted
		// configuration.
		updateScalebar(true);
		
		// Create & show the dialog, then return.
		boolean multipleSlices = imp.getStackSize() > 1;
		GenericDialog dialog = new BarDialog(getHUnit(), getVUnit(), config.hDigits, config.vDigits, multipleSlices);
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
	Overlay createScaleBarOverlay() {
		Overlay overlay = new Overlay();

		Color color = getColor();
		Color bcolor = getBColor();
		
		int fontType = config.boldText?Font.BOLD:Font.PLAIN;
		String face = config.serifFont?"Serif":"SanSerif";
		Font font = new Font(face, fontType, config.fontSize);
		ImageProcessor ip = imp.getProcessor();
		ip.setFont(font);

		Rectangle[] r = getElementsPosition(ip);
		Rectangle hBackground = r[0];
		Rectangle hBar = r[1];
		Rectangle hText = r[2];
		Rectangle vBackground = r[3];
		Rectangle vBar = r[4];
		Rectangle vText = r[5];

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
				TextRoi hTextRoi = new TextRoi(hText.x, hText.y - hText.height, getHLabel(), font);
				hTextRoi.setStrokeColor(color);
				overlay.add(hTextRoi, SCALE_BAR);
			}
			if (config.showVertical) {
				TextRoi vTextRoi = new TextRoi(vText.x, vText.y, getVLabel(), font);
				vTextRoi.setStrokeColor(color);
				vTextRoi.setAngle(90.0);
				overlay.add(vTextRoi, SCALE_BAR);
			}
		}

		return overlay;
	}

	Rectangle[] getElementsPosition(ImageProcessor ip) {
		Rectangle hBackground = new Rectangle();
		Rectangle hBar = new Rectangle();
		Rectangle hText = new Rectangle();
		Rectangle vBackground = new Rectangle();
		Rectangle vBar = new Rectangle();
		Rectangle vText = new Rectangle();

		boolean upper = config.location.equals(locations[UPPER_RIGHT]) || config.location.equals(locations[UPPER_LEFT]);
		boolean right = config.location.equals(locations[UPPER_RIGHT]) || config.location.equals(locations[LOWER_RIGHT]);

		int textGap = config.fontSize/(config.serifFont?8:4);

		hBar.x = xloc;
		hBar.y = yloc + (config.showVertical ? computeVLabelHeightInPixels() : 0);
		hBar.width = hBarWidthInPixels;
		hBar.height = config.barThicknessInPixels;

		vBar.x = hBar.x + (right ? (hBar.width - config.barThicknessInPixels) : 0);
		vBar.y = hBar.y + (upper ? 0 : - vBarHeightInPixels + config.barThicknessInPixels);
		vBar.width = config.barThicknessInPixels;
		vBar.height = vBarHeightInPixels;

		hText.width = config.hideText ? 0 : ip.getStringWidth(getHLabel());
		hText.height = config.hideText ? 0 : (textGap + ip.getStringBounds(getHLabel()).height);
		hText.x = hBar.x + (hBarWidthInPixels - hText.width) / 2;
		hText.y = hBar.y + config.barThicknessInPixels + hText.height;

		vText.width = config.hideText ? 0 : (textGap + ip.getStringBounds(getVLabel()).height);
		vText.height = config.hideText ? 0 : ip.getStringWidth(getVLabel());
		vText.x = vBar.x + (right ? config.barThicknessInPixels : - vText.width);
		vText.y = vBar.y + (vBarHeightInPixels + vText.height) / 2;

		int margin = Math.max(computeHLabelWidthInPixels()/20, 2);

		hBackground.x = hBar.x - margin;
		hBackground.y = hBar.y - margin;
		hBackground.width = margin + hBarWidthInPixels + computeHLabelWidthInPixels() + margin;
		hBackground.height = margin + config.barThicknessInPixels + hText.height + margin;

		vBackground.x = vBar.x - margin - (right ? 0 : vText.width);
		vBackground.y = vBar.y - margin;
		vBackground.width = margin + config.barThicknessInPixels + vText.width + margin;
		vBackground.height = margin + vBarHeightInPixels + computeVLabelHeightInPixels() + margin + (upper ? 0 : hText.height);

		Rectangle[] r = {hBackground, hBar, hText, vBackground, vBar, vText};
		return r;
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

	int computeHLabelWidthInPixels() {
		ImageProcessor ip = imp.getProcessor();
		int hLabelWidth = config.hideText?0:ip.getStringWidth(getHLabel());
		return (hLabelWidth < hBarWidthInPixels)?0:(int) (hBarWidthInPixels-hLabelWidth)/2;
	}

	int computeVLabelHeightInPixels() {
		ImageProcessor ip = imp.getProcessor();
		int vLabelHeight = config.hideText?0:ip.getStringWidth(getVLabel());
		return (vLabelHeight < vBarHeightInPixels)?0:(int) (vBarHeightInPixels-vLabelHeight)/2;
	}

	void updateFont() {
		int fontType = config.boldText?Font.BOLD:Font.PLAIN;
		String font = config.serifFont?"Serif":"SanSerif";
		ImageProcessor ip = imp.getProcessor();
		ip.setFont(new Font(font, fontType, config.fontSize));
		ip.setAntialiasedText(true);
	}

	void updateLocation() throws MissingRoiException {
		Calibration cal = imp.getCalibration();
		ImageWindow win = imp.getWindow();
		double mag = (win!=null)?win.getCanvas().getMagnification():1.0;

		hBarWidthInPixels = (int)(config.hBarWidth/cal.pixelWidth);
		vBarHeightInPixels = (int)(config.vBarHeight/cal.pixelHeight);
		int imageWidth = imp.getWidth();
		int imageHeight = imp.getHeight();
		int margin = (imageWidth+imageHeight)/100;
		if (mag==1.0)
			margin = (int)(margin*1.5);
		updateFont();
		int hLabelWidth = computeHLabelWidthInPixels();
		int fontSize = config.hideText ? 0 : config.fontSize;
		int x = 0;
		int y = 0;
		if (config.location.equals(locations[UPPER_RIGHT])) {
			x = imageWidth - margin - (config.showVertical ? fontSize : 0) - hBarWidthInPixels + hLabelWidth;
			y = margin;
		} else if (config.location.equals(locations[LOWER_RIGHT])) {
			x = imageWidth - margin - (config.showVertical ? fontSize : 0) - hBarWidthInPixels + hLabelWidth;
			y = imageHeight - margin - config.barThicknessInPixels - fontSize;
		} else if (config.location.equals(locations[UPPER_LEFT])) {
			x = margin - hLabelWidth + (config.showVertical ? fontSize : 0);
			y = margin;
		} else if (config.location.equals(locations[LOWER_LEFT])) {
			x = margin - hLabelWidth + (config.showVertical ? fontSize : 0);
			y = imageHeight - margin - config.barThicknessInPixels - fontSize;
		} else {
			if (!userRoiExists)
				throw new MissingRoiException();
			x = roiX;
			y = roiY;
		}
		xloc = x;
		yloc = y;
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
		try {
			updateLocation();
		} catch (MissingRoiException e) {
			return; // Simply don't draw the scalebar.
		}

		Overlay scaleBarOverlay = createScaleBarOverlay();
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
				ip.drawOverlay(scaleBarOverlay);
			} else {
				ImageStack stack = imp.getStack();
				for (int i=1; i<=stack.size(); i++) {
					ImageProcessor ip = stack.getProcessor(i);
					ip.drawOverlay(scaleBarOverlay);
				}
				imp.setStack(stack);
			}
		}
	}

   class BarDialog extends GenericDialog {

		private boolean multipleSlices;

		BarDialog(String hUnits, String vUnits, int hDigits, int vDigits, boolean multipleSlices) {
			super("Scale Bar");
			this.multipleSlices = multipleSlices;

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

		public void textValueChanged(TextEvent e) {
			TextField hWidthField = ((TextField)numberField.elementAt(0));
			Double d = getValue(hWidthField.getText());
			if (d==null)
				return;
			config.hBarWidth = d.doubleValue();
			TextField vHeightField = ((TextField)numberField.elementAt(1));
			d = getValue(vHeightField.getText());
			if (d==null)
				return;
			config.vBarHeight = d.doubleValue();
			TextField thicknessField = ((TextField)numberField.elementAt(2));
			d = getValue(thicknessField.getText());
			if (d==null)
				return;
			config.barThicknessInPixels = (int)d.doubleValue();
			TextField fontSizeField = ((TextField)numberField.elementAt(3));
			d = getValue(fontSizeField.getText());
			if (d==null)
				return;
			int size = (int)d.doubleValue();
			if (size>5)
				config.fontSize = size;

			String widthString = hWidthField.getText();
			boolean hasDecimalPoint = false;
			config.hDigits = 0;
			for (int i = 0; i < widthString.length(); i++) {
				if (hasDecimalPoint) {
					config.hDigits += 1;
				}
				if (widthString.charAt(i) == '.') {
					hasDecimalPoint = true;
				}
			}

			String heightString = vHeightField.getText();
			hasDecimalPoint = false;
			config.vDigits = 0;
			for (int i = 0; i < heightString.length(); i++) {
				if (hasDecimalPoint) {
					config.vDigits += 1;
				}
				if (heightString.charAt(i) == '.') {
					hasDecimalPoint = true;
				}
			}

			updateScalebar(true);
		}

		public void itemStateChanged(ItemEvent e) {
			Choice col = (Choice)(choice.elementAt(0));
			config.color = col.getSelectedItem();
			Choice bcol = (Choice)(choice.elementAt(1));
			config.bcolor = bcol.getSelectedItem();
			Choice loc = (Choice)(choice.elementAt(2));
			config.location = loc.getSelectedItem();
			config.showHorizontal = ((Checkbox)(checkbox.elementAt(0))).getState();
			config.showVertical = ((Checkbox)(checkbox.elementAt(1))).getState();
			config.boldText = ((Checkbox)(checkbox.elementAt(2))).getState();
			config.hideText = ((Checkbox)(checkbox.elementAt(3))).getState();
			config.serifFont = ((Checkbox)(checkbox.elementAt(4))).getState();
			config.useOverlay = ((Checkbox)(checkbox.elementAt(5))).getState();
			if (multipleSlices)
				config.labelAll = ((Checkbox)(checkbox.elementAt(6))).getState();
			updateScalebar(true);
		}

   } //BarDialog inner class

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