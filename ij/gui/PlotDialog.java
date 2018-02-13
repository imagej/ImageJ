package ij.gui;
import ij.*;
import ij.process.*;
import ij.plugin.frame.Recorder;
import java.awt.*;

/*
 * This class contains dialogs for formatting of plots (range, axes, labels, legend, creating a high-resolution plot)
 * Formatting of contents (curves, symbols, ...) is in PlotContentsStyleDialog
 */

public class PlotDialog {

	/** types of dialog */
	public static final int SET_RANGE = 0, AXIS_OPTIONS = 1, LEGEND = 2, HI_RESOLUTION = 3;
	/** dialog headings for the dialogType */
	private static final String[] HEADINGS = new String[] {"Plot Range...", "Axis Options...", "Add Legend...", "High-Resolution Plot..."};
	/** potins and numbers for legend position */
	private static final String[] LEGEND_POSITIONS = new String[] {"Auto",	"Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right", "No Legend"};
	private static final int[] LEGEND_POSITION_N = new int[] {Plot.AUTO_POSITION, Plot.TOP_LEFT, Plot.TOP_RIGHT, Plot.BOTTOM_LEFT, Plot.BOTTOM_RIGHT, 0};

	private Plot plot;
	private int dialogType;

	//saved dialog options: legend
	private static int legendPosNumber = 0;
	private static boolean bottomUp;
	private static boolean transparentBackground;
	//saved dialog options: Axis labels
	private static String xLabel, yLabel;
	private static float plotFontSize;
	private static boolean axisLabelBold;
	//saved dialog options: High-resolution plot
	private static float hiResFactor = 4.0f;
	private static boolean hiResAntiAliased = true;

	/** Construct a new PlotDialog, show it and do the appropriate action on the plot
	 */
	public PlotDialog(Plot plot, int dialogType) {
		this.plot = plot;
		this.dialogType = dialogType;
	}

	/** Asks the user for axis scaling; then replot with new scale on the same ImageProcessor.
	 *	The 'parent' frame may be null */
	public void showDialog(Frame parent) {
		GenericDialog gd = parent == null ? new GenericDialog(HEADINGS[dialogType]) :
				new GenericDialog(HEADINGS[dialogType]);
		if (dialogType == SET_RANGE) {
			double[] currentMinMax = plot.currentMinMax;
			boolean livePlot = plot.plotMaker != null;
			int xDigits = plot.logXAxis ? -2 : Plot.getDigits(currentMinMax[0], currentMinMax[1], 0.005*Math.abs(currentMinMax[1]-currentMinMax[0]), 6);
			int yDigits = plot.logYAxis ? -2 : Plot.getDigits(currentMinMax[2], currentMinMax[3], 0.005*Math.abs(currentMinMax[3]-currentMinMax[2]), 6);
			gd.addNumericField("X_From*", currentMinMax[0], xDigits);
			gd.addNumericField("X_To*", currentMinMax[1], xDigits);
			gd.setInsets(0, 20, 0); //top, left, bottom
			if (livePlot)
				gd.addCheckbox("Fix_X Range While Live", (plot.templateFlags & Plot.X_RANGE) != 0);
			gd.addCheckbox("Log_X Axis", (plot.hasFlag(Plot.X_LOG_NUMBERS)));
			gd.setInsets(20, 0, 3); //top, left, bottom
			gd.addNumericField("Y_From*", currentMinMax[2], yDigits);
			gd.addNumericField("Y_To*", currentMinMax[3], yDigits);
			if (livePlot)
				gd.addCheckbox("Fix_Y Range While Live", (plot.templateFlags & Plot.Y_RANGE) != 0);
			gd.addCheckbox("Log_Y Axis", (plot.hasFlag(Plot.Y_LOG_NUMBERS)));
			gd.addMessage("*Leave empty for automatic range", new Font("SansSerif", Font.PLAIN, 12));        //n__ begin PlotDialog
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			plot.saveMinMax();
			double linXMin = gd.getNextNumber();
			if (gd.invalidNumber())
				linXMin = Double.NaN;
			double linXMax = gd.getNextNumber();
			if (gd.invalidNumber())
				linXMax = Double.NaN;
			double linYMin = gd.getNextNumber();
			if (gd.invalidNumber())
				linYMin = Double.NaN;
			double linYMax = gd.getNextNumber();
			if (gd.invalidNumber())
				linYMax = Double.NaN;

			currentMinMax[0] = linXMin;
			currentMinMax[1] = linXMax;
			currentMinMax[2] = linYMin;
			currentMinMax[3] = linYMax;

			if (livePlot) plot.templateFlags = setFlag(plot.templateFlags, Plot.X_RANGE, gd.getNextBoolean());
			boolean xLog = gd.getNextBoolean();
			if (livePlot) plot.templateFlags = setFlag(plot.templateFlags, Plot.Y_RANGE, gd.getNextBoolean());
			boolean yLog = gd.getNextBoolean();
			plot.setAxisXLog(xLog);
			plot.setAxisYLog(yLog);
                        plot.setLimits(linXMin, linXMax, linYMin, linYMax);
			plot.updateImage();
			if (Recorder.record) {
				if (Recorder.scriptMode()) {
					Recorder.recordCall("//plot = IJ.getImage().getProperty(Plot.PROPERTY_KEY)");
					Recorder.recordCall("plot.setAxisXLog("+xLog+");");
					Recorder.recordCall("plot.setAxisYLog("+yLog+");");
					//Recorder.recordCall("plot.setLimits("+IJ.d2s(currentMinMax[0],xDigits)+","+IJ.d2s(currentMinMax[1],xDigits)+","+IJ.d2s(currentMinMax[2],yDigits)+","+IJ.d2s(currentMinMax[3],yDigits)+");");
					Recorder.recordCall("plot.setLimits("+IJ.d2s(linXMin,xDigits)+","+IJ.d2s(linXMax,xDigits)+","+IJ.d2s(linYMin,yDigits)+","+IJ.d2s(linYMax,yDigits)+");");
				} else {
					Recorder.recordString("Plot.setLogScaleX("+xLog+");\n");
					Recorder.recordString("Plot.setLogScaleY("+yLog+");\n");
					//Recorder.recordString("Plot.setLimits("+IJ.d2s(currentMinMax[0],xDigits)+","+IJ.d2s(currentMinMax[1],xDigits)+","+IJ.d2s(currentMinMax[2],yDigits)+","+IJ.d2s(currentMinMax[3],yDigits)+");\n");
					Recorder.recordString("Plot.setLimits("+IJ.d2s(linXMin,xDigits)+","+IJ.d2s(linXMax,xDigits)+","+IJ.d2s(linYMin,yDigits)+","+IJ.d2s(linYMax,yDigits)+");\n");
				}
			}
		//n__ end PlotDialog
		} else if (dialogType == AXIS_OPTIONS) {
			int flags = plot.getFlags();
			int columns = 2;
			final String[] labels = new String[] {"Draw Grid", "Major Ticks", "Minor Ticks", "Ticks if Logarithmic", "Numbers"};
			final int[] xFlags = new int[] {Plot.X_GRID, Plot.X_TICKS, Plot.X_MINOR_TICKS, Plot.X_LOG_TICKS, Plot.X_NUMBERS};
			Panel panel = new Panel();
			panel.setLayout(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			c.anchor = GridBagConstraints.CENTER;
			c.gridy = 0;
			c.gridx = 1;
			panel.add(new Label("X Axis"), c);
			c.gridx = 2;
			panel.add(new Label("Y Axis"), c);

			Checkbox[] checkboxes = new Checkbox[labels.length*columns];
			for (int l=0; l<labels.length; l++) {
				c.gridy++;
				c.gridx = 0;
				c.anchor = GridBagConstraints.EAST;
				panel.add(new Label(labels[l]), c);
				c.anchor = GridBagConstraints.CENTER;
				c.gridx = GridBagConstraints.RELATIVE;
				checkboxes[l*columns] = new Checkbox(null, getFlag(flags, xFlags[l])); //x flag
				panel.add(checkboxes[l*columns], c);
				checkboxes[l*columns+1] = new Checkbox(null, getFlag(flags, xFlags[l]<<1)); //y flags are shifted up one bit
				panel.add(checkboxes[l*columns+1], c);
			}
			gd.addPanel(panel);
			gd.setInsets(15, 0, 3); // top, left, bottom -- extra space
			Font plotFont = (plot.currentFont != null) ? plot.currentFont : plot.defaultFont;
			Font labelFont = plot.getFont('x');
			if (labelFont == null) labelFont = plotFont;
			Font numberFont = plot.getFont('f');
			if (numberFont == null) numberFont = plotFont;
			String plotXLabel = plot.getLabel('x');
			String plotYLabel = plot.getLabel('y');
			if (plotXLabel == null) plotXLabel = "";
			if (plotYLabel == null) plotYLabel = "";
			if (xLabel == null || (!(plotXLabel.equals("Distance (pixels)") || plotXLabel.equals("Distance ( )"))))
				xLabel = plotXLabel;
			if (yLabel == null || !plotYLabel.equals("Gray Value"))
                yLabel = plotYLabel; // suggest last dialog entry if default profile label
			gd.addNumericField("Number Font Size", numberFont.getSize2D(), 1);
			gd.addNumericField("Label Font Size", labelFont.getSize2D(), 1);
			String xMultiLineLabel = xLabel.replace("\n", "|");//n__
			int nChars = 15;
			if(xLabel.startsWith("{") ||yLabel.startsWith("{" )){
					nChars = Math.max(nChars, xLabel.length());
					nChars = Math.max(nChars, yLabel.length());
			}
			gd.addStringField("X Axis Label", xMultiLineLabel, nChars);
			String yMultiLineLabel = yLabel.replace("\n", "|");
			gd.addStringField("Y Axis Label", yMultiLineLabel, nChars);
			gd.setInsets(0, 20, 0); // no extra space
			gd.addCheckbox("Bold Labels", plotFontSize>0 ? axisLabelBold : (labelFont.isBold()));
			gd.showDialog();
			if (gd.wasCanceled()) return;

			flags = 0;
			for (int l=0; l<labels.length; l++) {
				flags = setFlag(flags, xFlags[l], checkboxes[l*columns].getState());
				flags = setFlag(flags, xFlags[l]<<1, checkboxes[l*columns+1].getState());
			}
			plot.setFormatFlags(flags);
			float numberFontSize = (float)gd.getNextNumber();
			if (gd.invalidNumber()) numberFontSize = numberFont.getSize2D();
			if (numberFontSize < 9)  numberFontSize = 9f;
			if (numberFontSize > 24) numberFontSize = 24f;
			float labelFontSize = (float)gd.getNextNumber();
			if (gd.invalidNumber()) labelFontSize = labelFont.getSize2D();
			xMultiLineLabel = gd.getNextString();//n__
			xLabel = xMultiLineLabel.replace("|", "\n");
			yMultiLineLabel = gd.getNextString();
			yLabel = yMultiLineLabel.replace("|", "\n");
			axisLabelBold = gd.getNextBoolean();
			plot.setFont('f', numberFont.deriveFont(numberFont.getStyle(), numberFontSize));
			plot.setAxisLabelFont(axisLabelBold ? Font.BOLD : Font.PLAIN, labelFontSize);
			plot.setXYLabels(xLabel, yLabel);
			plot.updateImage();
			if (Recorder.record) {
				String xLabel2 = xLabel.replace("\n", "\\n");
				String yLabel2 = yLabel.replace("\n", "\\n");
				if (Recorder.scriptMode()) {
					Recorder.recordCall("//plot = IJ.getImage().getProperty(Plot.PROPERTY_KEY)");
					Recorder.recordCall("plot.setFont(-1,"+IJ.d2s(plotFontSize,1)+");");
					Recorder.recordCall("plot.setAxisLabelFont(Plot."+(axisLabelBold ? "BOLD" : "PLAIN")+","+IJ.d2s(labelFontSize,1)+");");
					//Recorder.recordCall("plot.setXYLabels(\""+xLabel+"\", \""+yLabel+"\");");
					Recorder.recordCall("plot.setXYLabels(\""+xLabel2+"\", \""+yLabel2+"\");");//n__
					Recorder.recordCall("plot.setFormatFlags(0x"+Integer.toHexString(flags)+");");
				} else {
					Recorder.recordString("Plot.setFontSize("+IJ.d2s(plotFontSize,1)+");\n");
					Recorder.recordString("Plot.setAxisLabelSize("+IJ.d2s(labelFontSize,1)+", \""+(axisLabelBold ? "bold" : "plain")+"\");\n");
					//Recorder.recordString("Plot.setXYLabels(\""+xLabel+"\", \""+yLabel+"\");\n");
					Recorder.recordString("Plot.setXYLabels(\""+xLabel2+"\", \""+yLabel2+"\");\n");//n__
					Recorder.recordString("Plot.setFormatFlags(\""+Integer.toString(flags,2)+"\");\n");
				}
			}
		} else if (dialogType == LEGEND) {
			gd.addMessage("Enter Labels for the datasets, one per line.\n");
			String labels = plot.getDataLabels();
			int nLines = labels.split("\n", -1).length;
			gd.addTextAreas(labels, null, Math.min(nLines+1, 20), 40);
			gd.addChoice("Legend position", LEGEND_POSITIONS, LEGEND_POSITIONS[legendPosNumber]);
			gd.addCheckbox("Transparent background", transparentBackground);
			gd.addCheckbox("Bottom-to-top", bottomUp);
			gd.showDialog();
			if (gd.wasCanceled()) return;

			labels = gd.getNextText();
			legendPosNumber = gd.getNextChoiceIndex();
			int flags = LEGEND_POSITION_N[legendPosNumber];
			transparentBackground = gd.getNextBoolean();
			bottomUp = gd.getNextBoolean();
			if (bottomUp)
				flags |= Plot.LEGEND_BOTTOM_UP;
			if (transparentBackground)
				flags |= Plot.LEGEND_TRANSPARENT;
			plot.setColor(Color.black);
			plot.setLineWidth(1);
			plot.setLegend(labels, flags);
			plot.updateImage();
			if (Recorder.record) {
				String options = LEGEND_POSITIONS[legendPosNumber];
				if (bottomUp) options+=" Bottom-To-Top";
				if (transparentBackground) options+=" Transparent";
				labels = labels.replaceAll("\n","\\\\n");
				if (Recorder.scriptMode()) {
					Recorder.recordCall("//plot = IJ.getImage().getProperty(Plot.PROPERTY_KEY);");
					Recorder.recordCall("plot.setColor(Color.black);");
					Recorder.recordCall("plot.setLineWidth(1);");
					Recorder.recordCall("plot.addLegend(\""+labels+"\", \""+options+"\");");
				} else {
					Recorder.recordString("Plot.addLegend(\""+labels+"\", \""+options+"\");\n");
				}
			}
		} else if (dialogType == HI_RESOLUTION) {
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
	}

	boolean getFlag(int flags, int bitMask) {
		return (flags&bitMask) != 0;
	}

	int setFlag(int flags, int bitMask, boolean state) {
		flags &= ~bitMask;
		if (state) flags |= bitMask;
		return flags;
	}

}
