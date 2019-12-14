package ij.gui;
import ij.*;
import ij.process.*;
import ij.plugin.frame.Recorder;
import java.awt.*;
import java.util.Vector;

/*
 * This class contains dialogs for formatting of plots (range, axes, labels, legend, creating a high-resolution plot)
 * Adding and formatting of contents (curves, symbols, ...) is in PlotContentsStyleDialog
 */

public class PlotDialog implements DialogListener {

	/** Types of dialog. Note that 10-14 must be the same as the corresponding PlotWindow.rangeArrow numbers */
	public static final int SET_RANGE = 0, AXIS_OPTIONS = 1, LEGEND = 2, HI_RESOLUTION = 3, TEMPLATE = 4, //5-9 spare
			X_LEFT = 10, X_RIGHT = 11, Y_BOTTOM = 12, Y_TOP = 13, X_AXIS = 14, Y_AXIS = 15;
	/** Dialog headings for the dialogTypes */
	private static final String[] HEADINGS = new String[] {"Plot Range", "Axis Options", "Add Legend", "High-Resolution Plot", "Use Template",
			null, null, null, null, null,  // 5-9 spare
			"X Left", "X Right", "Y Bottom","Y Top", "X Axis", "Y Axis"};
	/** Positions and corresponding codes for legend position */
	private static final String[] LEGEND_POSITIONS = new String[] {"Auto",	"Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right", "No Legend"};
	private static final int[] LEGEND_POSITION_N = new int[] {Plot.AUTO_POSITION, Plot.TOP_LEFT, Plot.TOP_RIGHT, Plot.BOTTOM_LEFT, Plot.BOTTOM_RIGHT, 0};
	/** Template "copy what" flag: dialog texts and corresponding bit masks, in the sequence as they appear in the dialog*/
	private static final String[] TEMPLATE_FLAG_NAMES = new String[] {"X Range", "Y Range", "Axis Style", "Labels",
			"Legend", "Contents Style", "Extra Objects (Curves...)",  "Window Size"};
	private static final int[] TEMPLATE_FLAGS = new int[] {Plot.X_RANGE, Plot.Y_RANGE, Plot.COPY_AXIS_STYLE, Plot.COPY_LABELS,
			Plot.COPY_LEGEND, Plot.COPY_CONTENTS_STYLE, Plot.COPY_EXTRA_OBJECTS, Plot.COPY_SIZE};

	private Plot plot;
	private int  dialogType;
	private boolean minMaxSaved;			//whether plot min&max has been saved for "previous range"
	private boolean dialogShowing;			//when the dialog is showing, ignore the last call with event null
	private Plot[]  templatePlots;

	private Checkbox xLogCheckbox, yLogCheckbox;

	//saved dialog options: legend
	private static int legendPosNumber = 0;
	private static boolean bottomUp;
	private static boolean transparentBackground;
	//saved dialog options: Axis labels
	private static String lastXLabel, lastYLabel;
	private static float plotFontSize;
	//saved dialog options: High-resolution plot
	private static float hiResFactor = 4.0f;
	private static boolean hiResAntiAliased = true;
	//saved dialog options: Use Template
	private static int templateID;
	private static int lastTemplateFlags = Plot.COPY_AXIS_STYLE|Plot.COPY_CONTENTS_STYLE;

	/** Constructs a new PlotDialog for a given plot and sets the type of dialog */
	public PlotDialog(Plot plot, int dialogType) {
		this.plot = plot;
		this.dialogType = dialogType;
	}

	/** Asks the user for axis scaling; then replot with new scale on the same ImageProcessor.
	 *	The 'parent' frame may be null */
	public void showDialog(Frame parent) {
		if (dialogType == HI_RESOLUTION) {	//'make high-resolution plot' dialog has no preview, handled separately
			doHighResolutionDialog(parent);
			return;
		}
		plot.savePlotPlotProperties();
		if (dialogType == TEMPLATE)
			plot.savePlotObjects();

		String dialogTitle = dialogType >= X_LEFT && dialogType <= Y_TOP ?
			"Set Axis Limit..." : (HEADINGS[dialogType] + "...");
		GenericDialog gd = parent == null ? new GenericDialog(dialogTitle) :
				new GenericDialog(dialogTitle, parent);
		if (!setupDialog(gd)) return;
		gd.addDialogListener(this);
		dialogItemChanged(gd, null);		//preview immediately
		dialogShowing = true;
		gd.showDialog();
		if (gd.wasCanceled()) {
			plot.restorePlotProperties();
			if (dialogType == TEMPLATE)
				plot.restorePlotObjects();
			plot.update();
		} else {
			if (Recorder.record)
				record();
			String xAxisLabel = plot.getLabel('x');
			if ((dialogType == AXIS_OPTIONS || dialogType == X_AXIS) && xAxisLabel != null && xAxisLabel.length() > 0)
				lastXLabel = xAxisLabel;	//remember for next time, in case we have none
			String yAxisLabel = plot.getLabel('y');
			if ((dialogType == AXIS_OPTIONS || dialogType == Y_AXIS) && yAxisLabel != null && yAxisLabel.length() > 0)
				lastYLabel = yAxisLabel;
			if (dialogType == SET_RANGE || dialogType == X_AXIS || dialogType == Y_AXIS)
				plot.makeLimitsDefault();
			if (dialogType == TEMPLATE)
				lastTemplateFlags = plot.templateFlags;

		}
		plot.killPlotPropertiesSnapshot();
		if (dialogType == TEMPLATE)
			plot.killPlotObjectsSnapshot();

		ImagePlus imp = plot.getImagePlus();
		ImageWindow win = imp == null ? null : imp.getWindow();
		if (win instanceof PlotWindow)
			((PlotWindow)win).hideRangeArrows(); // arrows etc might be still visible, but the mouse maybe elsewhere

		if (!gd.wasCanceled() && !gd.wasOKed()) { // user has pressed "Set all limits" or "Set Axis Options" button
			int newDialogType = (dialogType == SET_RANGE) ? AXIS_OPTIONS : SET_RANGE;
			new PlotDialog(plot, newDialogType).showDialog(parent);
		}
	}

	/** Setting up the dialog fields and initial parameters. The input is read in the dialogItemChanged method, which must
	 *  have exactly the same structure of 'if' blocks and matching 'get' methods for each input field.
	 *  @return false on error */
	private boolean setupDialog(GenericDialog gd) {
		double[] currentMinMax = plot.getLimits();
		boolean livePlot = plot.plotMaker != null;

		int xDigits = plot.logXAxis ? -2 : Plot.getDigits(currentMinMax[0], currentMinMax[1], 0.005*Math.abs(currentMinMax[1]-currentMinMax[0]), 6);
		if (dialogType == SET_RANGE || dialogType == X_AXIS) {
			gd.addNumericField("X_From", currentMinMax[0], xDigits, 6, "*");
			gd.addToSameRow();
			gd.addNumericField("To", currentMinMax[1], xDigits, 6, "*");
			gd.setInsets(0, 20, 0); //top, left, bottom
			if (livePlot)
				gd.addCheckbox("Fix_X Range While Live", (plot.templateFlags & Plot.X_RANGE) != 0);
			gd.addCheckbox("Log_X Axis  **", (plot.hasFlag(Plot.X_LOG_NUMBERS)));
			xLogCheckbox = lastCheckboxAdded(gd);
			enableDisableLogCheckbox(xLogCheckbox, currentMinMax[0], currentMinMax[1]);
		}
		int yDigits = plot.logYAxis ? -2 : Plot.getDigits(currentMinMax[2], currentMinMax[3], 0.005*Math.abs(currentMinMax[3]-currentMinMax[2]), 6);
		if (dialogType == SET_RANGE || dialogType == Y_AXIS) {
			gd.setInsets(20, 0, 3); //top, left, bottom
			gd.addNumericField("Y_From", currentMinMax[2], yDigits, 6, "*");
			gd.addToSameRow();
			gd.addNumericField("To", currentMinMax[3], yDigits, 6, "*");
			if (livePlot)
				gd.addCheckbox("Fix_Y Range While Live", (plot.templateFlags & Plot.Y_RANGE) != 0);
			gd.addCheckbox("Log_Y Axis  **", (plot.hasFlag(Plot.Y_LOG_NUMBERS)));
			yLogCheckbox = lastCheckboxAdded(gd);
			enableDisableLogCheckbox(yLogCheckbox, currentMinMax[2], currentMinMax[3]);
		}
		if (dialogType >= X_LEFT && dialogType <= Y_TOP) {
			int digits = dialogType < Y_BOTTOM ? xDigits : yDigits;
			gd.addNumericField(HEADINGS[dialogType], currentMinMax[dialogType - X_LEFT], digits, 6, "*");
		}

		if (dialogType == AXIS_OPTIONS || dialogType == X_AXIS || dialogType == Y_AXIS) {
			int flags = plot.getFlags();
			final String[] labels = new String[] {" Draw Grid", " Major Ticks", " Minor Ticks", " Ticks if Logarithmic", " Numbers"};
			final int[] xFlags = new int[] {Plot.X_GRID, Plot.X_TICKS, Plot.X_MINOR_TICKS, Plot.X_LOG_TICKS, Plot.X_NUMBERS};
			int rows = xFlags.length;
			int columns = dialogType == AXIS_OPTIONS ? 2 : 1;
			String[] allLabels = new String[rows*columns];
			boolean[] defaultValues = new boolean[rows*columns];
			String[] headings = dialogType == AXIS_OPTIONS ? new String[]{"X Axis", "Y Axis"} : null;
			int i=0;
			for (int l=0; l<xFlags.length; l++) {
				String label = labels[l];
				boolean xFlag = getFlag(flags, xFlags[l]);
				boolean yFlag = getFlag(flags, xFlags[l]<<1); //y flags are shifted up one bit
				if (dialogType == AXIS_OPTIONS || dialogType == X_AXIS) {
					allLabels[i] = labels[l];
					defaultValues[i++] = xFlag;
				}
				if (dialogType == AXIS_OPTIONS || dialogType == Y_AXIS) {
					allLabels[i] = labels[l];
					defaultValues[i++] = yFlag;
				}
			}
			if (dialogType == X_AXIS || dialogType == Y_AXIS)
				gd.setInsets(15, 20, 0);	//top, left, bottom
			gd.addCheckboxGroup(rows, columns, allLabels, defaultValues, headings);
			if (dialogType == AXIS_OPTIONS)
				gd.setInsets(15, 0, 5);	//top, left, bottom

			String plotXLabel = plot.getLabel('x');
			String plotYLabel = plot.getLabel('y');
			if ((plotXLabel == null || plotXLabel.equals("Distance (pixels)") || plotXLabel.equals("Distance ( )")) && lastXLabel != null)
				plotXLabel = lastXLabel;	// suggest last dialog entry if default profile label
			if ((plotYLabel == null  || plotYLabel.equals("Gray Value")) && lastYLabel != null)
				plotYLabel = lastYLabel;
			int nChars = 20;
			if ((plotXLabel != null && plotXLabel.startsWith("{")) || (plotYLabel != null && plotYLabel.startsWith("{" ))) {
					nChars = Math.max(nChars, plotXLabel.length());
					nChars = Math.max(nChars, plotYLabel.length());
			}
			if (nChars > 80) nChars = 80;
			//plotXLabel = plotXLabel.replace("\n", "|");  //multiline label currently no supported by Plot class
			if (dialogType == AXIS_OPTIONS || dialogType == X_AXIS)
				gd.addStringField("X Axis Label", plotXLabel, nChars);
			//plotYLabel = plotYLabel.replace("\n", "|");
			if (dialogType == AXIS_OPTIONS || dialogType == Y_AXIS)
				gd.addStringField("Y Axis Label", plotYLabel, nChars);
		}
		if (dialogType == SET_RANGE || dialogType == X_AXIS || dialogType == Y_AXIS) {
			Font smallFont = new Font("SansSerif", Font.PLAIN, (int)(10*Prefs.getGuiScale()));//n__
			gd.setInsets(10, 0, 0);			//top, left, bottom
			gd.addMessage("*   Leave empty for automatic range", smallFont, Color.gray);
			gd.setInsets(0, 0, 0);
			gd.addMessage("** Requires limits > 0 and max/min > 3", smallFont, Color.gray);
			if (dialogType == X_AXIS || dialogType == Y_AXIS) {
				gd.setInsets(0, 0, 0);
				gd.addMessage("    Label supports !!sub-!! and ^^superscript^^", smallFont, Color.gray);
			}
		}

		if (dialogType == AXIS_OPTIONS) {
			Font plotFont = (plot.currentFont != null) ? plot.currentFont : plot.defaultFont;
			Font labelFont = plot.getFont('x');
			if (labelFont == null) labelFont = plotFont;
			Font numberFont = plot.getFont('f');
			if (numberFont == null) numberFont = plotFont;
			gd.addNumericField("Number Font Size", numberFont.getSize2D(), 1);
			gd.addNumericField("Label Font Size", labelFont.getSize2D(), 1);
			//gd.setInsets(0, 20, 0); // no extra space
			gd.addToSameRow();
			gd.addCheckbox("Bold", labelFont.isBold());
		}
		if (dialogType == LEGEND) {
			String labels = plot.getDataLabels();
			int nLines = labels.split("\n", -1).length;
			Font legendFont = plot.getFont('l');
			if (legendFont == null) legendFont = (plot.currentFont != null) ? plot.currentFont : plot.defaultFont;
			int lFlags = plot.getObjectFlags('l');
			if (lFlags != -1) { //if we have a legend already
				for (int i=0; i<LEGEND_POSITION_N.length; i++)  //determine the position option from the flags
					if ((lFlags & Plot.LEGEND_POSITION_MASK) == LEGEND_POSITION_N[i]) {
						legendPosNumber = i;
						break;
					}
				transparentBackground = getFlag(lFlags, Plot.LEGEND_TRANSPARENT);
				bottomUp = getFlag(lFlags, Plot.LEGEND_BOTTOM_UP);
			}
			gd.addMessage("Enter Labels for the datasets, one per line.\n");
			gd.addTextAreas(labels, null, Math.min(nLines+1, 20), 40);
			gd.addChoice("Legend position", LEGEND_POSITIONS, LEGEND_POSITIONS[legendPosNumber]);
			gd.addNumericField("Font Size", legendFont.getSize2D(), 1);

			gd.addCheckbox("Transparent background", transparentBackground);
			gd.addCheckbox("Bottom-to-top", bottomUp);
		}
		if (dialogType == TEMPLATE) {
			int[] idList = WindowManager.getIDList();			//create list of plots to use as template
			int[] plotIdList = new int[idList.length];
			templatePlots = new Plot[idList.length];
			int nPlots = 0;
			for (int id : idList) {
				ImagePlus imp = WindowManager.getImage(id);
				if (imp == null) continue;
				Plot impPlot = (Plot)(imp.getProperty(Plot.PROPERTY_KEY));
				if (impPlot != null && impPlot != this.plot) {
					templatePlots[nPlots] = impPlot;
					plotIdList[nPlots++] = id;
				}
			}
			if (nPlots == 0) {
				IJ.error("No plot to use as template");
				return false;
			}
			String[] plotImpTitles = new String[nPlots];
			int defaultTemplateIndex = 0;
			for (int i=0; i<nPlots; i++) {
				ImagePlus imp = WindowManager.getImage(plotIdList[i]);
				plotImpTitles[i] = imp==null ? "" : imp.getTitle();
				if (imp.getID() == templateID) defaultTemplateIndex = i;
			}
			gd.addChoice("Template Plot", plotImpTitles, plotImpTitles[defaultTemplateIndex]);
			gd.setInsets(10, 0, 0);			//top, left, bottom
			gd.addMessage("Copy From Template:");
			gd.setInsets(5, 20, 0);			//top, left, bottom
			if (lastTemplateFlags == 0) lastTemplateFlags = Plot.COPY_AXIS_STYLE|Plot.COPY_CONTENTS_STYLE;
			for (int i=0; i<TEMPLATE_FLAGS.length; i++) {
				boolean flag = getFlag(lastTemplateFlags, TEMPLATE_FLAGS[i]);
				gd.addCheckbox(TEMPLATE_FLAG_NAMES[i], flag);
			}
		}
		// Add a button to access another dialog that might be also desired
		if (dialogType >= X_LEFT && dialogType <= Y_TOP)
			gd.enableYesNoCancel("OK", "Set All Limits...");
		else if (dialogType == AXIS_OPTIONS)
			gd.enableYesNoCancel("OK", "Set Range...");
		else if (dialogType == SET_RANGE)
			gd.enableYesNoCancel("OK", "Set Axis Options...");
		return true;
	} //setupDialog

	/** This method is called when the user changes something in the dialog. Note that the 'if's for reading
	 *  the fields must be exactly the same as those for setting up the fields in 'setupDialog' (fields must be
	 *  also read in the same sequence). */
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		if (dialogShowing && e == null) return true;	//gets called with e=null upon OK; ignore this
		boolean livePlot = plot.plotMaker != null;

		if (dialogType == SET_RANGE || dialogType == X_AXIS) {
			double[] currentMinMax = plot.getLimits();
			double linXMin = gd.getNextNumber();
			//if (gd.invalidNumber())
				//linXMin = Double.NaN;
			double linXMax = gd.getNextNumber();
			//if (gd.invalidNumber())
				//linXMax = Double.NaN;
			if (linXMin == linXMax) return false;
			if (!minMaxSaved) {
				plot.saveMinMax();		//save for 'Previous Range' in plot menu
				minMaxSaved = true;
			}
			if (livePlot)
				plot.templateFlags = setFlag(plot.templateFlags, Plot.X_RANGE, gd.getNextBoolean());
			boolean xLog = gd.getNextBoolean();
			plot.setAxisXLog(xLog);
			plot.setLimitsNoUpdate(linXMin, linXMax, currentMinMax[2], currentMinMax[3]);
			currentMinMax = plot.getLimits();
			enableDisableLogCheckbox(xLogCheckbox, currentMinMax[0], currentMinMax[1]);
		}
		if (dialogType == SET_RANGE || dialogType == Y_AXIS) {
			double[] currentMinMax = plot.getLimits();
			double linYMin = gd.getNextNumber();
			//if (gd.invalidNumber())
				//linYMin = Double.NaN;
			double linYMax = gd.getNextNumber();
			//if (gd.invalidNumber())
				//linYMax = Double.NaN;
			if (linYMin == linYMax) return false;
			if (!minMaxSaved) {
				plot.saveMinMax();		//save for 'Previous Range' in plot menu
				minMaxSaved = true;
			}

			if (livePlot)
				plot.templateFlags = setFlag(plot.templateFlags, Plot.Y_RANGE, gd.getNextBoolean());
			boolean yLog = gd.getNextBoolean();
			plot.setAxisYLog(yLog);
			plot.setLimitsNoUpdate(currentMinMax[0], currentMinMax[1], linYMin, linYMax);
			currentMinMax = plot.getLimits();
			enableDisableLogCheckbox(yLogCheckbox, currentMinMax[2], currentMinMax[3]);
		}
		if (dialogType >= X_LEFT && dialogType <= Y_TOP) {
			double newLimit = gd.getNextNumber();
			double[] minMaxCopy = (double[])(plot.getLimits().clone());
			minMaxCopy[dialogType - X_LEFT] = newLimit;
			plot.setLimitsNoUpdate(minMaxCopy[0], minMaxCopy[1], minMaxCopy[2], minMaxCopy[3]);
		}

		if (dialogType == AXIS_OPTIONS || dialogType == X_AXIS || dialogType == Y_AXIS) {
			final int[] xFlags = new int[] {Plot.X_GRID, Plot.X_TICKS, Plot.X_MINOR_TICKS, Plot.X_LOG_TICKS, Plot.X_NUMBERS};
			int rows = xFlags.length;
			int columns = dialogType == AXIS_OPTIONS ? 2 : 1;
			int flags = 0;
			if (dialogType == X_AXIS)
				flags = plot.getFlags() & 0xaaaaaaaa; //keep y flags, i.e., odd bits
			if (dialogType == Y_AXIS)
				flags = plot.getFlags() & 0x55555555; //keep x flags, i.e., even bits
			for (int l=0; l<xFlags.length; l++) {
				if (dialogType == AXIS_OPTIONS || dialogType == X_AXIS)
					if (gd.getNextBoolean()) flags |= xFlags[l];
				if (dialogType == AXIS_OPTIONS || dialogType == Y_AXIS)
					if (gd.getNextBoolean()) flags |= xFlags[l]<<1; //y flags are shifted up one bit;
			}
			plot.setFormatFlags(flags);

			String xAxisLabel = plot.getLabel('x');
			String yAxisLabel = plot.getLabel('y');
			if (dialogType == AXIS_OPTIONS || dialogType == X_AXIS) {
				xAxisLabel = gd.getNextString();
				//xAxisLabel = xAxisLabel.replace("|", "\n");	//multiline label currently not supported by Plot class
			}
			if (dialogType == AXIS_OPTIONS || dialogType == Y_AXIS) {
				yAxisLabel = gd.getNextString();
				//yAxisLabel = yAxisLabel.replace("|", "\n");
			}
			plot.setXYLabels(xAxisLabel, yAxisLabel);
		}
		if (dialogType == AXIS_OPTIONS) {
			Font plotFont = (plot.currentFont != null) ? plot.currentFont : plot.defaultFont;
			Font labelFont = plot.getFont('x');
			if (labelFont == null) labelFont = plotFont;
			Font numberFont = plot.getFont('f');
			if (numberFont == null) numberFont = plotFont;

			float numberFontSize = (float)gd.getNextNumber();
			if (gd.invalidNumber()) numberFontSize = numberFont.getSize2D();
			if (numberFontSize < 9)  numberFontSize = 9f;
			if (numberFontSize > 24) numberFontSize = 24f;
			float labelFontSize = (float)gd.getNextNumber();
			if (gd.invalidNumber()) labelFontSize = labelFont.getSize2D();
			boolean axisLabelBold = gd.getNextBoolean();
			plot.setFont('f', numberFont.deriveFont(numberFont.getStyle(), numberFontSize));
			plot.setAxisLabelFont(axisLabelBold ? Font.BOLD : Font.PLAIN, labelFontSize);
			Font smallFont = new Font("SansSerif", Font.PLAIN, (int)(10*Prefs.getGuiScale()));
			gd.addMessage("Labels support !!sub-!! and ^^superscript^^", smallFont, Color.gray);//n__
		}	
		if (dialogType == LEGEND) {
			Font legendFont = plot.getFont('l');
			if (legendFont == null) legendFont = (plot.currentFont != null) ? plot.currentFont : plot.defaultFont;

			String labels = gd.getNextText();
			int legendPosNumber = gd.getNextChoiceIndex();
			int lFlags = LEGEND_POSITION_N[legendPosNumber];
			float legendFontSize = (float)gd.getNextNumber();
			transparentBackground = gd.getNextBoolean();
			bottomUp = gd.getNextBoolean();
			if (bottomUp)
				lFlags |= Plot.LEGEND_BOTTOM_UP;
			if (transparentBackground)
				lFlags |= Plot.LEGEND_TRANSPARENT;
			plot.setColor(Color.black);
			plot.setLineWidth(1);
			plot.setLegend(labels, lFlags);
			plot.setFont('l', legendFont.deriveFont(legendFont.getStyle(), legendFontSize));
		}
		if (dialogType == TEMPLATE) {
			Plot templatePlot = templatePlots[gd.getNextChoiceIndex()];
			ImagePlus imp = templatePlot.getImagePlus();
			if (imp != null) templateID = imp.getID();	//remember for next time
			int templateFlags = 0;
			for (int i=0; i<TEMPLATE_FLAGS.length; i++)
				if (gd.getNextBoolean())
					templateFlags |= TEMPLATE_FLAGS[i];
			plot.restorePlotProperties();
			plot.restorePlotObjects();
			plot.useTemplate(templatePlot, templateFlags);
		}

		plot.updateImage();
		return true;
	} //dialogItemChanged

	/** Macro recording */
	private void record() {
		if (Recorder.scriptMode())
			Recorder.recordCall("//plot = IJ.getImage().getProperty(Plot.PROPERTY_KEY);");
		String plotDot = Recorder.scriptMode() ? "plot." : "Plot.";

		if (dialogType == SET_RANGE || dialogType == X_AXIS)
			Recorder.recordString(plotDot+(Recorder.scriptMode() ? "setAxisXLog(" : "setLogScaleX(")+plot.hasFlag(Plot.X_LOG_NUMBERS)+");\n");
		if (dialogType == SET_RANGE || dialogType == Y_AXIS)
			Recorder.recordString(plotDot+(Recorder.scriptMode() ? "setAxisYLog(" : "setLogScaleY(")+plot.hasFlag(Plot.Y_LOG_NUMBERS)+");\n");
		if (dialogType == SET_RANGE || dialogType == X_AXIS || dialogType == Y_AXIS) {
			double[] currentMinMax = plot.getLimits();
			int xDigits = plot.logXAxis ? -2 : Plot.getDigits(currentMinMax[0], currentMinMax[1], 0.005*Math.abs(currentMinMax[1]-currentMinMax[0]), 6);
			int yDigits = plot.logYAxis ? -2 : Plot.getDigits(currentMinMax[2], currentMinMax[3], 0.005*Math.abs(currentMinMax[3]-currentMinMax[2]), 6);
			Recorder.recordString(plotDot+"setLimits("+IJ.d2s(currentMinMax[0],xDigits)+","+IJ.d2s(currentMinMax[1],xDigits)+
						","+IJ.d2s(currentMinMax[2],yDigits)+","+IJ.d2s(currentMinMax[3],yDigits)+");\n");
		}
		if (dialogType == AXIS_OPTIONS || dialogType == X_AXIS || dialogType == Y_AXIS) {
			int flags = plot.getFlags();
			String xAxisLabel = Recorder.fixString(plot.getLabel('x'));
			String yAxisLabel = Recorder.fixString(plot.getLabel('y'));
			Font labelFont = plot.getFont('x');
			Font numberFont = plot.getFont('f');
			if (labelFont != null) {
				if (Recorder.scriptMode())
					Recorder.recordString("plot.setAxisLabelFont("+(labelFont.isBold() ? "Font.BOLD," : "Font.PLAIN,")+IJ.d2s(labelFont.getSize2D(),1)+");\n");
				else
					Recorder.recordString("Plot.setAxisLabelSize("+IJ.d2s(labelFont.getSize2D(),1)+", \""+(labelFont.isBold() ? "bold" : "plain")+"\");\n");
			}
			if (numberFont != null)
				Recorder.recordString(plotDot+(Recorder.scriptMode() ? "setFont(-1, " : "setFontSize(")+IJ.d2s(numberFont.getSize2D(),1)+");\n");
			Recorder.recordString(plotDot+"setXYLabels(\""+xAxisLabel+"\", \""+yAxisLabel+"\");\n");
			Recorder.recordString(plotDot+"setFormatFlags("+(Recorder.scriptMode() ? "0x"+Integer.toHexString(flags) : '\"'+Integer.toString(flags,2)+'\"')+ ");\n");
		}
		if (dialogType == LEGEND) {
			String labels = Recorder.fixString(plot.getDataLabels());
			int lFlags = plot.getObjectFlags('l');
			if (Recorder.scriptMode()) {
				Recorder.recordCall("plot.setColor(Color.black);");
				Recorder.recordCall("plot.setLineWidth(1);");
				Recorder.recordCall("plot.addLegend(\""+labels+"\", 0x"+Integer.toHexString(lFlags)+"\");");
			} else {
				String options = LEGEND_POSITIONS[legendPosNumber];
				if (getFlag(lFlags, Plot.LEGEND_BOTTOM_UP))   options+=" Bottom-To-Top";
				if (getFlag(lFlags, Plot.LEGEND_TRANSPARENT)) options+=" Transparent";
				Recorder.recordString("Plot.addLegend(\""+labels+"\", \""+options+"\");\n");
			}
		}

		if (Recorder.scriptMode())
			Recorder.recordCall("plot.update();");
	}

	/** The dialog for "Make High Resolution Plot"; it has no preview*/
	private void doHighResolutionDialog(Frame parent) {
		GenericDialog gd = parent == null ? new GenericDialog(HEADINGS[dialogType]) :
				new GenericDialog(HEADINGS[dialogType], parent);
		String title = plot.getTitle() +"_HiRes";
		title = WindowManager.makeUniqueName(title);
		gd.addStringField("Title: ", title, 20);
		gd.addNumericField("Scale factor", hiResFactor, 1);
		gd.addCheckbox("Disable anti-aliased text", !hiResAntiAliased);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		title = gd.getNextString();
		double scale = gd.getNextNumber();
		if (!gd.invalidNumber() && scale>0) //more range checking is done in Plot.setScale
			hiResFactor = (float)scale;
		hiResAntiAliased = !gd.getNextBoolean();
		final ImagePlus hiresImp = plot.makeHighResolution(title, hiResFactor, hiResAntiAliased, /*showIt=*/true);
		/** The following command is needed to have the high-resolution plot as front window. Otherwise, as the
		 *	dialog is owned by the original PlotWindow, the WindowManager will see the original plot as active,
		 *	but the user interface will show the high-res plot as foreground window */
		EventQueue.invokeLater(new Runnable() {public void run() {IJ.selectWindow(hiresImp.getID());}});

		if (Recorder.record) {
			String options = !hiResAntiAliased ? "disable" : "";
			if (options.length() > 0)
				options = ",\""+options+"\"";
			Recorder.recordString("Plot.makeHighResolution(\""+title+"\","+hiResFactor+options+");\n");
		}
	}

	/** Disables switching on a checkbox for log range if the axis limits do not allow it.
	 *  The checkbox can be always switched off. */
	void enableDisableLogCheckbox(Checkbox checkbox, double limit1, double limit2) {
		boolean logPossible = limit1 > 0 && limit2 > 0 && (limit1 > 3*limit2 || limit2 > 3*limit1);
		checkbox.setEnabled(logPossible);
	}


	boolean getFlag(int flags, int bitMask) {
		return (flags&bitMask) != 0;
	}

	int setFlag(int flags, int bitMask, boolean state) {
		flags &= ~bitMask;
		if (state) flags |= bitMask;
		return flags;
	}

	Checkbox lastCheckboxAdded(GenericDialog gd) {
		Vector checkboxes = gd.getCheckboxes();
		return (Checkbox)(checkboxes.get(checkboxes.size() - 1));
	}

}

