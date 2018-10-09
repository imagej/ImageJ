package ij.gui;
import ij.*;
import ij.process.*;
import ij.text.TextWindow;
import ij.measure.ResultsTable;
import ij.util.Tools;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.Vector;
import java.util.ArrayList;

/** This class implements the Plot Window's "Data>Add from Plot" and "More>Contents Style" dialogs */
public class PlotContentsDialog implements DialogListener {
	/** types of dialog */
	public final static int STYLE=0, ADD_FROM_PLOT=1, ADD_FROM_TABLE=2;
	/** dialog headings for the dialogType */
	private static final String[] HEADINGS = new String[] {"Plot Contents Style", "Add From Plot", "Add From Table"};
	private Plot plot;
	private int dialogType;
	GenericDialog gd;
	private int currentObjectIndex = -1;
	private Choice      objectChoice;
	private Choice      symbolChoice;
	private TextField   colorField, color2Field, labelField, widthField;
	private boolean     creatingPlot;                // for creating plots; dialogType determines source
	private Choice      plotChoice;                  // for "Add from Plot"
	private Plot[]      allPlots;
	private String[]    allPlotNames;
	private static Plot previousPlot;
	private static int  previousPlotObjectIndex;
	private int defaultPlotIndex, defaultObjectIndex;
	private Choice      tableChoice;                 // for "Add from Table"
	final static int N_COLUMNS = 4;                  // number of data columns that we can have; x, y, xE, yE
	private Choice[] columnChoice = new Choice[N_COLUMNS];
	private final static String[] COLUMN_NAMES = new String[] {"X:", "Y:", "X Error:", "Y Error:"};
	private final static boolean[] COLUMN_ALLOW_NONE = new boolean[] {true, false, true, true}; //y data cannot be null
	private ResultsTable[] allTables;
	private String[] allTableNames;
	private static ResultsTable previousTable;
	private static int[] previousColumns = new int[]{1, 1, 0, 0}; //must be N_COLUMNS elements
	private static int defaultTableIndex;
	private static int[] defaultColumnIndex = new int[N_COLUMNS];
	private static String previousColor="blue", previousColor2="none", previousSymbol="Circle";
	private static double previousLineWidth = 1;


	/** Prepare a new PlotContentsDialog for an existing plot. Use showDialog thereafter. */
	public PlotContentsDialog(Plot plot, int dialogType) {
		this.plot = plot;
		this.dialogType = dialogType;
	}

	/** Prepare a new PlotContentsDialog for plotting data from a ResultsTable */
	public PlotContentsDialog(ResultsTable rt, String title) {
		creatingPlot = true;
		dialogType = ADD_FROM_TABLE;
		if (rt == null)
			throw new RuntimeException("Cant Create Plot: No ResultsTable in "+title);

		plot = new Plot("Plot of "+title, "x", "y");
		allTables = new ResultsTable[] {rt};
		allTableNames = new String[] {title};
	}


	/** Shows the dialog, with a given parent Frame (may be null) */
	public void showDialog(Frame parent) {
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
		if (creatingPlot)
			plot.show();
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
			if (creatingPlot) tableChoice.setVisible(false);     // we can't select the table, we have only one
			for (int i=0; i<N_COLUMNS; i++) {
				gd.addChoice(COLUMN_NAMES[i], new String[]{""}, "");  // will set up by makeSourceColumns
				columnChoice[i] = (Choice)(gd.getChoices().get(i+1));
			}
			makeSourceColumns();
		}
		gd.addStringField("Color:", previousColor, 10);
		gd.addStringField("Secondary (fill) color:", previousColor2, 10);
		gd.addNumericField("Line width: ", previousLineWidth, 1);
		gd.addChoice("Symbol:", Plot.SORTED_SHAPES, previousSymbol);
		Vector choices = gd.getChoices();
		symbolChoice = (Choice)(choices.get(choices.size()-1));
		gd.addStringField("Label:", "", 20);
		Vector stringFields = gd.getStringFields();
		colorField = (TextField)(stringFields.get(0));
		color2Field = (TextField)(stringFields.get(1));
		labelField = (TextField)(stringFields.get(stringFields.size()-1));
		widthField = (TextField)(gd.getNumericFields().get(0));
		gd.setInsets(10, 60, 0);
		gd.addCheckbox("Hidden", false);
		gd.addDialogListener(this);
		IJ.wait(100);			//sometimes needed to avoid hanging?
		if (dialogType == STYLE)
			setDialogStyleFields(objectChoice.getSelectedIndex());
		else if (dialogType == ADD_FROM_PLOT)
			addObjectFromPlot();
		else if (dialogType == ADD_FROM_TABLE)
			addObjectFromTable();
		if (creatingPlot)
			plot.updateImage();

		gd.showDialog();
		if (gd.wasCanceled()) {
			if (creatingPlot) {
				ImagePlus imp = plot.getImagePlus();
				if (imp != null) imp.close();
			} else {
				plot.restorePlotObjects();
				plot.updateImage();
			}
		}
		plot.killPlotObjectsSnapshot();
		if (dialogType == ADD_FROM_TABLE && !gd.wasCanceled()) {
			previousColor = colorField.getText();
			previousColor2 = color2Field.getText();
			previousSymbol = symbolChoice.getSelectedItem();
			previousLineWidth = Tools.parseDouble(widthField.getText());
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
		} else if (dialogType == ADD_FROM_TABLE) {
			if (e.getSource() == tableChoice) {
				makeSourceColumns();
				addObjectFromTable();
			} else {
				boolean columnChanged = false;
				for (int c=0; c<N_COLUMNS; c++)
					if (e.getSource() == columnChoice[c]) {
						columnChanged = true;
						break;
					}
				if (columnChanged)
					addObjectFromTable();
				else
					setStyle = true;
			}
		}
		if (setStyle) {
			String color = gd.getNextString();
			String color2 = gd.getNextString();
			double width = gd.getNextNumber();
			String label = gd.getNextString();
			Boolean hidden = gd.getNextBoolean();
			String symbol = symbolChoice.getSelectedItem();
			if (labelField.isEnabled()) plot.setPlotObjectLabel(currentObjectIndex, label.length() > 0 ? label : null);
			String style = color.trim()+","+color2.trim()+","+(float)width+","+ symbol+(hidden?",hidden":"");
			plot.setPlotObjectStyles(currentObjectIndex, style);
		}
		return true;
	}

	/** Sets the style fields of the dialog according to the style of the PlotObject having the
	 *  index given. Does nothing with index < 0 */
	private void setDialogStyleFields(int index) {
		if (index < 0) return;
		Checkbox hiddenC = (Checkbox)gd.getCheckboxes().get(0);
		String styleString = plot.getPlotObjectStyles(index);
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
		hiddenC.setState(styleString.contains("hidden"));

		colorField.setEnabled(!isGrid);	//
		color2Field.setEnabled(isData || isBox);//only (some) data symbols and boxes have secondary (fill) color
		widthField.setEnabled(!isText  && !isGrid); //all non-Text types have line width
		hiddenC.setEnabled(!isGrid);            //dont't allow to hide
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
		for (int i=0; i<plotObjectNames.length; i++)
			objectChoice.addItem(plotObjectNames[i]);
		objectChoice.select(Math.min(plotObjectNames.length-1, previousPlotObjectIndex));
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
				if (rt != null && rt.getColumnHeadings().length()>0)
					tableWindows.add((TextWindow)win);
		}
		allTables = new ResultsTable[tableWindows.size()];
		allTableNames = new String[tableWindows.size()];
		defaultTableIndex =  0;
		for (int i=0; i<allTables.length; i++) {
			TextWindow tw = tableWindows.get(i);
			allTables[i] = tw.getResultsTable();
			if (allTables[i] == previousTable)
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
				if (rt != null && rt.getColumnHeadings().length()>0) return true;
			}
		}
		return false;
	}

	/** Set up the Choices for the source columns for "Add from Table" */
	private void makeSourceColumns() {
		int tableIndex = tableChoice.getSelectedIndex();
		ResultsTable rt = allTables[tableIndex];
		String columnHeadingStr = rt.getColumnHeadings();
		if (!columnHeadingStr.startsWith(" \t"))
			columnHeadingStr = " \t"+columnHeadingStr;	//add empty field at beginning (if we don't have one)
		String[] columnHeadings = columnHeadingStr.split("\t");
		columnHeadings[0] = "---";
		for (int c=0; c<N_COLUMNS; c++) {
			columnChoice[c].removeAll();
			for (int i=COLUMN_ALLOW_NONE[c] ? 0 : 1; i<columnHeadings.length; i++)
				columnChoice[c].addItem(columnHeadings[i]);
			columnChoice[c].select(Math.min(columnHeadings.length-1, previousColumns[c]));
		}
	}

	/** For "Add from Table", adds item to the plot according to the current Choice settings
	 *  and sets the Style fields for it. */
	private void addObjectFromTable() {
		int tableIndex = tableChoice.getSelectedIndex();
		ResultsTable rt = allTables[tableIndex];
		float data[][] = new float[N_COLUMNS][];
		for (int c=0; c<N_COLUMNS; c++) {
			String heading = columnChoice[c].getSelectedItem();
			int index = rt.getColumnIndex(heading);
			if (index >= 0)
				data[c] = rt.getColumn(index);
			previousColumns[c] = columnChoice[c].getSelectedIndex();
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
		currentObjectIndex = plot.getLastAddedIndex();
		setDialogStyleFields(currentObjectIndex);
		previousTable = rt;
	}

}

