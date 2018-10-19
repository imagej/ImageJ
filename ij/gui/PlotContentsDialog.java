package ij.gui;
import ij.*;
import ij.process.*;
import ij.measure.CurveFitter;
import ij.measure.Minimizer;
import ij.text.TextWindow;
import ij.measure.ResultsTable;
import ij.plugin.Colors;
import ij.plugin.frame.Recorder;
import ij.util.Tools;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.Vector;
import java.util.ArrayList;

/** This class implements the Plot Window's "Data>Add from Plot" and "More>Contents Style" dialogs */
public class PlotContentsDialog implements DialogListener {
	/** Types of dialog (ERROR suppresses the dialog after an invalid call of a constructor) */
	public final static int ERROR=-1, STYLE=0, ADD_FROM_PLOT=1, ADD_FROM_TABLE=2, ADD_FROM_ARRAYS=3, ADD_FIT=4;
	/** Dialog headings for each dialogType >= 0 */
	private static final String[] HEADINGS = new String[] {"Plot Contents Style", "Add From Plot", "Plot From Table", "Add Plot Data", "Add Fit"};
	private Plot plot;
	private int dialogType;
	GenericDialog gd;
	private int currentObjectIndex = -1;
	private Choice        objectChoice;
	private Choice        symbolChoice;
	private TextField     colorField, color2Field, labelField, widthField;
	private Checkbox      visibleCheckbox;
	private boolean       creatingPlot;              // for creating plots; dialogType determines source
	private Choice        plotChoice;                // for "Add from Plot"
	private Plot[]        allPlots;
	private String[]      allPlotNames;
	private static Plot   previousPlot;
	private static int    previousPlotObjectIndex;
	private int           defaultPlotIndex, defaultObjectIndex;
	private int           currentPlotNumObjects;
	private Choice        tableChoice;               // for "Add from Table"
	final static int      N_COLUMNS = 4;             // number of data columns that we can have; x, y, xE, yE
	private int           nColumnsToUse = N_COLUMNS; // user may restrict to 2, for not having error bars
	private Choice[]      columnChoice = new Choice[N_COLUMNS];
	private final static String[] COLUMN_NAMES = new String[] {"X:", "Y:", "X Error:", "Y Error:"};
	private final static boolean[] COLUMN_ALLOW_NONE = new boolean[] {true, false, true, true}; //y data cannot be null
	private ResultsTable[] allTables;
	private String[]      allTableNames;
	private static String previousTableName;
	private static int[]  previousColumns = new int[]{1, 1, 0, 0}; //must be N_COLUMNS elements
	private static int    defaultTableIndex;
	private static int[]  defaultColumnIndex = new int[N_COLUMNS];
	private String[]      arrayHeadings;             // for "Add from Arrays"
	private ArrayList<float[]> arrayData;
	private Choice        fitDataChoice;             // for "Add Fit"
	private Choice        fitFunctionChoice;
	private Thread        fittingThread;
	private static String lastFitFunction = CurveFitter.fitList[0];
	private String        curveFitterStatusString;
	private static String previousColor="blue", previousColor2="#a0a0ff", previousSymbol="Circle";
	private static double previousLineWidth = 1;
	private static final String[] PLOT_COLORS = new String[] {"blue", "red", "black", "#00c0ff", "#00e000", "gray", "#c08060", "magenta"};


	/** Prepares a new PlotContentsDialog for an existing plot. Use showDialog thereafter.
	 *  @param dialogType may be STYLE (contents style), ADD_FROM_PLOT (add object from other plot), ADD_FROM_TABLE, and ADD_FIT */
	public PlotContentsDialog(Plot plot, int dialogType) {
		this.plot = plot;
		this.dialogType = plot == null ? ERROR : dialogType;
		if (plot != null) currentPlotNumObjects = plot.getNumPlotObjects();
	}

	/** Prepares a new PlotContentsDialog for creating a new plot using data from a ResultsTable.
	 *  Use showDialog thereafter. */
	public PlotContentsDialog(String title, ResultsTable rt) {
		creatingPlot = true;
		dialogType = ADD_FROM_TABLE;
		if (rt == null || !isValid(rt)) {
			IJ.error("Cant Create Plot","No (results) table or no data in "+title);
			dialogType = ERROR;
		}
		plot = new Plot("Plot of "+title, "x", "y");
		allTables = new ResultsTable[] {rt};
		allTableNames = new String[] {title};
	}

	/** Prepares a new PlotContentsDialog for creating a new plot using float[] arrays as data.
	 *  Each 'data' array in the ArrayList must have a corresponding element in the 'headings' array.
	 *  'defaultHeadings' may contain the headings of the items selected initially for x, y, x error and y error, respectively.
	 *  'defaultHeadings' and each of its entries may be null, and the array may have any length. */
	public PlotContentsDialog(String title, String[] headings, String[] defaultHeadings, ArrayList<float[]> data) {
		creatingPlot = true;
		dialogType = ADD_FROM_ARRAYS;
		this.arrayHeadings = headings;
		this.arrayData = data;
		plot = new Plot(title, "x", "y");
		setDefaultColumns(defaultHeadings);
	}

	/** Prepares a new PlotContentsDialog for adding data from float[] arrays to a plot.
	 *  Each 'data' array in the ArrayList must have a corresponding element in the 'headings' array.
	 *  'defaultHeadings' may contain the headings of the items selected initially for x, y, x error and y error, respectively.
	 *  'defaultHeadings' and each of its entries may be null, and the array may have any length. */
	public PlotContentsDialog(Plot plot, String[] headings, String[] defaultHeadings, ArrayList<float[]> data) {
		dialogType = ADD_FROM_ARRAYS;
		this.plot = plot;
		this.arrayHeadings = headings;
		this.arrayData = data;
		setDefaultColumns(defaultHeadings);
	}

	/** Avoids showing a selection for the error bars; must be called before showDialog. */
	public void noErrorBars() {
		nColumnsToUse = 2; // only x, y columns can be selected
	}

	/** Shows the dialog, with a given parent Frame (may be null) */
	public void showDialog(Frame parent) {
		if (dialogType == ERROR) return;
		plot.savePlotObjects();
		String[] designations = plot.getPlotObjectDesignations();
		if (dialogType == STYLE && designations.length==0) {
			IJ.error("Empty Plot");
			return;
		} else if (dialogType == ADD_FROM_PLOT) {
			prepareAddFromPlot();
			if (allPlots.length == 0) return;	//should never happen; we have at least the current plot
		} else if (dialogType == ADD_FROM_TABLE && !creatingPlot) {
			prepareAddFromTable();
			if (allTables.length == 0) return;	//should never happen; PlotWindow should not enable if no table
		}
		if ((dialogType == ADD_FROM_TABLE || dialogType == ADD_FROM_ARRAYS) && !creatingPlot)
			suggestColor();
		if (parent == null && plot.getImagePlus() != null)
			parent = plot.getImagePlus().getWindow();
		gd = parent == null ? new GenericDialog(HEADINGS[dialogType]) :
				new GenericDialog(HEADINGS[dialogType], parent);
		IJ.wait(100);			//sometimes needed to avoid hanging?
		if (dialogType == STYLE) {
			gd.addChoice("Item:", designations, designations[0]);
			objectChoice = (Choice)(gd.getChoices().get(0));
			currentObjectIndex = objectChoice.getSelectedIndex();
		} else if (dialogType == ADD_FROM_PLOT) {
			gd.addChoice("Select Plot:", allPlotNames, allPlotNames[defaultPlotIndex]);
			gd.addChoice("Item to Add:", new String[]{""}, "");  // will be set up by makeSourcePlotObjects
			Vector choices = gd.getChoices();
			plotChoice = (Choice)(choices.get(0));
			objectChoice = (Choice)(choices.get(1));
			makeSourcePlotObjects();
		} else if (dialogType == ADD_FROM_TABLE) {
			gd.addChoice("Select Table:", allTableNames, allTableNames[defaultTableIndex]);
			tableChoice = (Choice)(gd.getChoices().get(0));
			if (creatingPlot) tableChoice.setEnabled(false);     // we can't select the table, we have only one
		} else if (dialogType == ADD_FIT) {
			String[] dataSources = plot.getDataObjectDesignations();
			if (dataSources.length == 0) {
				IJ.error("No Data For Fitting");
				return;
			}
			gd.addChoice("Fit Data Set:", dataSources, dataSources[0]);
			gd.addChoice("Fit Function:", new String[0], "");
			Vector choices = gd.getChoices();
			fitDataChoice = (Choice)(choices.get(0));
			fitFunctionChoice = (Choice)(choices.get(1));
			if (dataSources.length == 1)
				fitDataChoice.setEnabled(false);
			for (int i=0; i<CurveFitter.fitList.length; i++)
				fitFunctionChoice.addItem(CurveFitter.fitList[CurveFitter.sortedTypes[i]]);
			fitFunctionChoice.select(lastFitFunction);
		}
		if (dialogType == ADD_FROM_TABLE || dialogType == ADD_FROM_ARRAYS) {
			for (int i=0; i<nColumnsToUse; i++) {
				gd.addChoice(COLUMN_NAMES[i], new String[]{""}, "");  // will set up by makeSourceColumns
				Vector choices = gd.getChoices();
				columnChoice[i] = (Choice)(choices.get(choices.size()-1));
			}
			makeSourceColumns();
		}
		gd.addStringField("Color:", previousColor, 10);
		gd.addStringField("Secondary (fill) color:", previousColor2, 10);
		gd.addNumericField("Line width: ", previousLineWidth, 1);
		gd.addChoice("Symbol:", Plot.SORTED_SHAPES, dialogType == ADD_FIT ? "line" : previousSymbol);
		gd.addStringField("Label:", "", 20);
		gd.setInsets(10, 60, 0);
		gd.addCheckbox("Visible", true);
		Vector choices = gd.getChoices();
		symbolChoice = (Choice)(choices.get(choices.size()-1));
		Vector stringFields = gd.getStringFields();
		colorField = (TextField)(stringFields.get(0));
		color2Field = (TextField)(stringFields.get(1));
		labelField = (TextField)(stringFields.get(2));
		widthField = (TextField)(gd.getNumericFields().get(0));
		visibleCheckbox = (Checkbox)(gd.getCheckboxes().get(0));
		gd.addDialogListener(this);
		IJ.wait(100);			//sometimes needed to avoid hanging?
		if (dialogType == STYLE)
			setDialogStyleFields(objectChoice.getSelectedIndex());
		else if (dialogType == ADD_FROM_PLOT)
			addObjectFromPlot();
		else if (dialogType == ADD_FROM_TABLE || dialogType == ADD_FROM_ARRAYS)
			addObjectFromTable();
		else if (dialogType == ADD_FIT)
			addFitCurve();
		plot.updateImage();
		if (creatingPlot) {
			boolean recording = Recorder.record;
			Recorder.record = false; // don't record creating the image as image selection
			plot.show();
			if (recording) Recorder.record = true;
		}

		gd.showDialog();
		if (fittingThread != null) {
			fittingThread.interrupt();
			try {
				fittingThread.join();
			} catch (InterruptedException e) {}
		}
		if (gd.wasCanceled()) {
			if (creatingPlot) {
				ImagePlus imp = plot.getImagePlus();
				if (imp != null) imp.close();
			} else {
				plot.restorePlotObjects();
				plot.updateImage();
				plot.killPlotObjectsSnapshot();
			}
			return;
		}
		plot.killPlotObjectsSnapshot();
		if (dialogType == ADD_FROM_TABLE || dialogType == ADD_FROM_ARRAYS || dialogType == ADD_FIT) {
			previousColor = colorField.getText();
			previousColor2 = color2Field.getText();
			previousSymbol = symbolChoice.getSelectedItem();
			previousLineWidth = Tools.parseDouble(widthField.getText());
		}
		if (dialogType == ADD_FIT) {
			lastFitFunction = fitFunctionChoice.getSelectedItem();
			IJ.log(curveFitterStatusString);
		}
		if (Recorder.record && !Recorder.scriptMode()) {
			if (dialogType == ADD_FROM_PLOT) {
				Recorder.recordString("Plot.addFromPlot(\""+plotChoice.getSelectedItem()+"\", "+objectChoice.getSelectedIndex()+");\n");
			} else if (dialogType == ADD_FROM_TABLE) {
				if (creatingPlot)
					Recorder.recordString("Plot.create(\""+plot.getTitle()+"\", \""+plot.getLabel('x')+"\", \""+plot.getLabel('y')+"\");\n");
				String xSource = columnChoice[0].getSelectedIndex() == 0 ? "" :
						"Table.getColumn(\""+columnChoice[0].getSelectedItem()+"\", \""+tableChoice.getSelectedItem()+"\"), ";
				String ySource = "Table.getColumn(\""+columnChoice[1].getSelectedItem()+"\", \""+tableChoice.getSelectedItem()+"\")";
				Recorder.recordString("Plot.add(\""+symbolChoice.getSelectedItem()+"\", "+xSource+ySource+");\n");
				if (columnChoice[2].getSelectedIndex() > 0)
					Recorder.recordString("Plot.add(\"xerror\", Table.getColumn(\""+
							columnChoice[2].getSelectedItem()+"\", \""+tableChoice.getSelectedItem()+"\"));\n");
				if (columnChoice[3].getSelectedIndex() > 0)
					Recorder.recordString("Plot.add(\"error\", Table.getColumn(\""+
							columnChoice[3].getSelectedItem()+"\", \""+tableChoice.getSelectedItem()+"\"));\n");
			}
			Recorder.recordString("Plot.setStyle("+currentObjectIndex+", \""+getStyleString()+"\");\n");
		}
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		if (e == null) return true;	//gets called with e=null upon OK
		boolean setStyle = false;
		if (dialogType == STYLE) {
			int objectIndex = objectChoice.getSelectedIndex();  // no getNextChoice since Choices depend on dialog type
			setStyle = (e.getSource() != objectChoice);
			if (e.getSource() == objectChoice) {
				setDialogStyleFields(objectIndex);
				currentObjectIndex = objectIndex;
			} else
				setStyle = true;
		} else if (dialogType == ADD_FROM_PLOT) {
			if (e.getSource() == plotChoice) {
				makeSourcePlotObjects();
				addObjectFromPlot();
			} else if (e.getSource() == objectChoice) {
				addObjectFromPlot();
			} else
				setStyle = true;
		} else if (dialogType == ADD_FROM_TABLE || dialogType == ADD_FROM_ARRAYS) {
			if (e.getSource() == tableChoice) {
				makeSourceColumns();
				addObjectFromTable();
			} else {
				boolean columnChanged = false;
				for (int c=0; c<nColumnsToUse; c++)
					if (e.getSource() == columnChoice[c]) {
						columnChanged = true;
						break;
					}
				if (columnChanged)
					addObjectFromTable();
				else
					setStyle = true;
			}
		} else if (dialogType == ADD_FIT) {
			if (e.getSource() == fitDataChoice || e.getSource() == fitFunctionChoice)
				addFitCurve();
			else
				setStyle = true;
		}
		if (setStyle)
			setPlotObjectStyle();
		return true;
	}

	private void setPlotObjectStyle() {
		String style = getStyleString();
		plot.setPlotObjectStyle(currentObjectIndex, style);
		if (labelField.isEnabled()) {
			String label = labelField.getText();
			plot.setPlotObjectLabel(currentObjectIndex, label.length() > 0 ? label : null);
		}
	}

	/** Returns the style String for Plot.setPlotObjectStyle from the dialog fields */
	private String getStyleString() {
		String color = colorField.getText();
		String color2 = color2Field.getText();
		double width = Tools.parseDouble(widthField.getText());
		String symbol = symbolChoice.getSelectedItem();
		Boolean visible = visibleCheckbox.getState();
		String style = color.trim()+","+color2.trim()+","+(float)width+","+ symbol+(visible?"":"hidden");
		return style;
	}

	/** Sets the style fields of the dialog according to the style of the PlotObject having the
	 *  index given. Does nothing with index < 0 */
	private void setDialogStyleFields(int index) {
		if (index < 0) return;
		Checkbox visibleC = (Checkbox)gd.getCheckboxes().get(0);
		String styleString = plot.getPlotObjectStyle(index);
		String designation = plot.getPlotObjectDesignations()[index].toLowerCase();
		boolean isData = designation.startsWith("data");
		boolean isText = designation.startsWith("text");
		boolean isBox = designation.startsWith("shapes") &&
				(designation.contains("boxes") || designation.contains("rectangles"));
		boolean isGrid = designation.startsWith("shapes") && designation.contains("redraw_grid");

		String[] items = styleString.split(",");
		colorField.setText(items[0]);
		color2Field.setText(items[1]);
		widthField.setText(items[2]);
		if (items.length >= 4)
			symbolChoice.select(items[3]);
		labelField.setText(isData ? plot.getPlotObjectLabel(index) : "");
		visibleC.setState(!styleString.contains("hidden"));

		colorField.setEnabled(!isGrid);	        //grid color is fixed
		color2Field.setEnabled(isData || isBox);//only (some) data symbols and boxes have secondary (fill) color
		widthField.setEnabled(!isText  && !isGrid); //all non-Text types have line width
		// visibleC.setEnabled(!isGrid);        //allow to hide everything
		symbolChoice.setEnabled(isData);        //only data have a symbol to choose
		labelField.setEnabled(isData);          //only data have a label in the legend
	}


	/** Prepare the lists 'allPlots', 'allPlotNames' for the "Add from Plot" dialog.
	 *  Also sets 'defaultPlotIndex', 'defaultObjectIndex' */
	private void prepareAddFromPlot() {
		int[] windowIDlist = WindowManager.getIDList();
		ArrayList<ImagePlus> plotImps = new ArrayList<ImagePlus>();
		ImagePlus currentPlotImp = null;
		for (int windowID : windowIDlist) {
			ImagePlus imp = WindowManager.getImage(windowID);
			if (imp == null || imp.getWindow() == null) continue;
			Plot thePlot = (Plot)(imp.getProperty(Plot.PROPERTY_KEY));
			if (thePlot != null) {
				if (thePlot == plot)
					currentPlotImp = imp;
				else
					plotImps.add(imp);
			}
		}
		if (currentPlotImp != null)
			plotImps.add(currentPlotImp);     // add current plot as the last one (usually not used)
		if (plotImps.size() == 0) return;     // should never happen; we have at least the current plot

		allPlots = new Plot[plotImps.size()];
		allPlotNames = new String[plotImps.size()];
		defaultPlotIndex = 0;
		for (int i=0; i<allPlots.length; i++) {
			ImagePlus imp = plotImps.get(i);
			allPlots[i] = (Plot)(imp.getProperty(Plot.PROPERTY_KEY));
			if (allPlots[i] == previousPlot)
				defaultPlotIndex = i;
			allPlotNames[i] = imp.getWindow().getTitle();
			if (imp == currentPlotImp)
				allPlotNames[i] = "THIS PLOT: " + allPlotNames[i];
		}
	}

	/** Set up the Choice of Plot objects for the source plot after selecting that plot */
	private void makeSourcePlotObjects() {
		int plotIndex = plotChoice.getSelectedIndex();
		String[] plotObjectNames = allPlots[plotIndex].getPlotObjectDesignations();
		objectChoice.removeAll();
		int nPlotObjects = allPlots[plotIndex] == plot ? currentPlotNumObjects : plotObjectNames.length; //for the own plot, the number of objects may have changed in the meanwhile
		for (int i=0; i<nPlotObjects; i++)
			objectChoice.addItem(plotObjectNames[i]);
		objectChoice.select(Math.min(nPlotObjects-1, previousPlotObjectIndex));
	}

	/** For "Add from Plot", adds item to the plot according to the current Choice settings
	 *  and sets the Style fields for it. */
	private void addObjectFromPlot() {
		int plotIndex = plotChoice.getSelectedIndex();
		int objectIndex = objectChoice.getSelectedIndex();
		plot.restorePlotObjects();
		currentObjectIndex = plot.addObjectFromPlot(allPlots[plotIndex], objectIndex); //no updateImage; will be done later
		setDialogStyleFields(currentObjectIndex);
		previousPlot = allPlots[plotIndex];
		previousPlotObjectIndex = objectIndex;
	}

	/** Prepare the lists 'allTables', "allTableNames' for the "Add from Table" dialog.
	 *  Also sets 'defaultTableIndex' */
	private void prepareAddFromTable() {
		ArrayList<TextWindow> tableWindows = new ArrayList<TextWindow>();
		Frame[] windows = WindowManager.getNonImageWindows();
		for (Frame win : windows) {
			if (!(win instanceof TextWindow)) continue;
			ResultsTable rt = ((TextWindow)win).getResultsTable();
			if (isValid(rt))
				tableWindows.add((TextWindow)win);
		}
		allTables = new ResultsTable[tableWindows.size()];
		allTableNames = new String[tableWindows.size()];
		defaultTableIndex =  0;
		for (int i=0; i<allTables.length; i++) {
			TextWindow tw = tableWindows.get(i);
			allTables[i] = tw.getResultsTable();
			if (allTableNames[i] == previousTableName)
				defaultTableIndex = i;
			allTableNames[i] = tw.getTitle();
		}
	}

	/** Returns whether there is at least one table that can be used for "Add from Table" */
	public static boolean tableWindowExists() {
		Frame[] windows = WindowManager.getNonImageWindows();
		for (Frame win : windows) {
			if (win instanceof TextWindow) {
				ResultsTable rt = ((TextWindow)win).getResultsTable();
				if (isValid(rt)) return true;
			}
		}
		return false;
	}

	/** Set up the Choices for the source columns for "Add from Table" and "Add from Arrays" */
	private void makeSourceColumns() {
		String[] columnHeadings = null;
		if (dialogType == ADD_FROM_TABLE) {
			int tableIndex = tableChoice.getSelectedIndex();
			ResultsTable rt = allTables[tableIndex];
			String columnHeadingStr = rt.getColumnHeadings();
			if (!columnHeadingStr.startsWith(" \t"))
				columnHeadingStr = " \t"+columnHeadingStr;	//add empty field at beginning (if we don't have one)
			columnHeadings = columnHeadingStr.split("\t");
		} else {   // if (dialogType == ADD_FROM_ARRAYS)
			columnHeadings = new String[arrayHeadings.length+1];
			System.arraycopy(arrayHeadings, 0, columnHeadings, 1, arrayHeadings.length);
		}
		columnHeadings[0] = "---";
		for (int c=0; c<nColumnsToUse; c++) {
			columnChoice[c].removeAll();
			for (int i=COLUMN_ALLOW_NONE[c] ? 0 : 1; i<columnHeadings.length; i++)
				columnChoice[c].addItem(columnHeadings[i]);
			columnChoice[c].select(Math.min(columnChoice[c].getItemCount()-1, previousColumns[c]));
		}
	}

	/** For "Add from Table" and "Add from Arrays" adds item to the plot according to the current Choice settings
	 *  and sets the Style fields for it. */
	private void addObjectFromTable() {
		float data[][] = new float[N_COLUMNS][];
		if (dialogType == ADD_FROM_TABLE) {
			ResultsTable rt = allTables[tableChoice.getSelectedIndex()];
			for (int c=0; c<N_COLUMNS; c++) {
				String heading = columnChoice[c].getSelectedItem();
				int index = rt.getColumnIndex(heading);
				if (index >= 0)
					data[c] = rt.getColumn(index);
				previousColumns[c] = columnChoice[c].getSelectedIndex();
			}
		} else { //if (dialogType == ADD_FROM_ARRAYS)
			for (int c=0; c<nColumnsToUse; c++) {
				String heading = columnChoice[c].getSelectedItem();
				int index = getIndex(arrayHeadings, heading);
				if (index >= 0)
					data[c] = arrayData.get(index);
				previousColumns[c] = columnChoice[c].getSelectedIndex();
			}
		}
		String label = columnChoice[1].getSelectedItem();	//take label from y
		int shape = Plot.toShape(symbolChoice.getSelectedItem());
		float lineWidth = (float)(Tools.parseDouble(widthField.getText()));
		if (lineWidth > 0)
			plot.setLineWidth(lineWidth);
		plot.restorePlotObjects();
		plot.setColor(colorField.getText(), color2Field.getText());
		plot.addPoints(data[0], data[1], data[3], shape, label);
		if (data[2] != null)
			plot.addHorizontalErrorBars(data[2]);
		if (creatingPlot) {
			plot.setXYLabels(data[0]==null ? "x" : columnChoice[0].getSelectedItem(), columnChoice[1].getSelectedItem());
			plot.setLimitsToFit(false);
		}
		currentObjectIndex = plot.getNumPlotObjects()-1;
		setDialogStyleFields(currentObjectIndex);
		if (dialogType == ADD_FROM_TABLE)
			previousTableName = allTableNames[tableChoice.getSelectedIndex()];
	}

	/** Does the curve fit and adds the fit curve to the plot */
	private void addFitCurve() {
		plot.restorePlotObjects();
		int dataIndex = fitDataChoice.getSelectedIndex();
		float[][] data = plot.getDataObjectArrays(dataIndex);
		String fitName = fitFunctionChoice.getSelectedItem();
		int fitType = getIndex(CurveFitter.fitList, fitName);
		CurveFitter cf = new CurveFitter(Tools.toDouble(data[0]), Tools.toDouble(data[1]));
		cf.doFit(fitType);
		String statusString = "Fit: "+Minimizer.STATUS_STRING[cf.getStatus()];
		if (cf.getStatus() == Minimizer.SUCCESS)
			statusString += ", sum residuals ^2 = "+(float)(cf.getSumResidualsSqr());
		IJ.showStatus(statusString);
		curveFitterStatusString = "Fit for "+plot.getTitle()+": "+fitDataChoice.getSelectedItem()+cf.getResultString(); //will be shown in Log when done

		double[] plotMinMax = plot.getLimits();
		double[] dataMinMax = Tools.getMinMax(data[0]);
		double min = Math.min(plotMinMax[0], dataMinMax[0]);
		double max = Math.max(plotMinMax[1], dataMinMax[1]);
		double plotSpan = Math.abs(plotMinMax[1] - plotMinMax[0]);
		double dataSpan = Math.abs(dataMinMax[1] - dataMinMax[0]);
		double rangeFactor = Math.max(plotSpan/dataSpan, dataSpan/plotSpan);
		if (rangeFactor > 20) rangeFactor = 20;
		int nPoints = (int)(1000*rangeFactor); //finer data point spacing if we will want to zoom out or in
		float[] xFit = new float[nPoints];
		float[] yFit = new float[nPoints];
		for (int i=0; i<nPoints; i++) {
			xFit[i] = (float)(min + i*((max-min)/(nPoints-1)));
			yFit[i] = (float)(cf.f(xFit[i]));
		}
		plot.addPoints(xFit, yFit, Plot.LINE);
		currentObjectIndex = plot.getNumPlotObjects()-1;
		labelField.setText("Fit: "+fitName);
		setPlotObjectStyle();
	}

	/** Returns whether the table is non-null and has at least one non-trivial data column */
	private static boolean isValid(ResultsTable rt) {
		if (rt == null) return false;
		String columnHeadingStr = rt.getColumnHeadings();
		if (columnHeadingStr.startsWith(" \t"))
			return columnHeadingStr.length() >=3;
		else
			return columnHeadingStr.length() >= 1;

	}

	/** For "Add from Arrays", sets default columns from the colums titles */
	private void setDefaultColumns(String[] defaultHeadings) {
		if (defaultHeadings == null) return;
		for (int i=0; i<Math.min(defaultHeadings.length, nColumnsToUse); i++) {
			if (defaultHeadings[i] == null || defaultHeadings[i].length()==0)
				previousColumns[i] = 0;
			else
				previousColumns[i] = Math.max(0,  //default column index; index=0 means 'none' for all except y
						getIndex(arrayHeadings, defaultHeadings[i]) + (COLUMN_ALLOW_NONE[i] ? 1 : 0));
		}
	}

	/** Finds the index of a searchTerm in the String array; returns -1 if not found */
	private static int getIndex(String[] strArray, String searchTerm) {
		for (int i=0; i<strArray.length; i++)
			if (strArray[i].equals(searchTerm)) return i;
		return -1;
	}

	/** Sets the 'previousColor', 'previousColor2' with a suggestion for new colors */
	private void suggestColor() {
		boolean[] colorUsed = new boolean[PLOT_COLORS.length];
		String[] designations = plot.getPlotObjectDesignations();
		for (int i=0; i<designations.length; i++) {
			if (!(designations[i].toLowerCase().startsWith("data"))) continue;
			String styleString = plot.getPlotObjectStyle(i);
			for (int j=0; j<PLOT_COLORS.length; j++)
				if (styleString.startsWith(PLOT_COLORS[j]))
					colorUsed[j] = true;
		}
		String newColor = previousColor;
		for (int j=0; j<PLOT_COLORS.length; j++)
			if (!colorUsed[j]) {
				newColor = PLOT_COLORS[j];
				break;
			}
		if (previousColor2 != null && previousColor2.equals(previousColor))  //if fill color was main color, keep it such
			previousColor2 = newColor;
		else if (Colors.decode(previousColor2, null) != null) {              //if we had a separate fill color, make main color brighter
			Color newC = Colors.decode(newColor,Color.BLACK);
			Color newC2 = new Color(makeBrighter(newC.getRed()), makeBrighter(newC.getGreen()), makeBrighter(newC.getBlue()));
			previousColor2 = Colors.colorToString(newC2);
		}
		previousColor = newColor;
	}

	/** Creates an 8-bit color value closer to 255 */
	private static int makeBrighter(int v) {
		return v> 190 ? 255 : 255 - (int)(0.4*(255-v));
	}
}
