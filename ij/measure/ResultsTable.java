package ij.measure;
import ij.*;
import ij.plugin.filter.Analyzer;
import ij.text.*;
import ij.process.*;
import ij.gui.Roi;
import ij.util.Tools;
import ij.io.SaveDialog;
import java.awt.*;
import java.text.*;
import java.util.*;
import java.io.*;


/** This is a table for storing measurement results and strings as columns of values. 
	Call the static ResultsTable.getResultsTable() method to get a reference to the 
	ResultsTable used by the <i>Analyze/Measure</i> command. 
	@see ij.plugin.filter.Analyzer#getResultsTable
*/
public class ResultsTable implements Cloneable {

	/** Obsolete; use getLastColumn(). */
	public static final int MAX_COLUMNS = 150;
	
	public static final int COLUMN_NOT_FOUND = -1;
	public static final int COLUMN_IN_USE = -2;
	public static final int TABLE_FULL = -3; // no longer used
	
	public static final int AREA=0, MEAN=1, STD_DEV=2, MODE=3, MIN=4, MAX=5,
		X_CENTROID=6, Y_CENTROID=7, X_CENTER_OF_MASS=8, Y_CENTER_OF_MASS=9,
		PERIMETER=10, ROI_X=11, ROI_Y=12, ROI_WIDTH=13, ROI_HEIGHT=14,
		MAJOR=15, MINOR=16, ANGLE=17, CIRCULARITY=18, FERET=19, 
		INTEGRATED_DENSITY=20, MEDIAN=21, SKEWNESS=22, KURTOSIS=23, 
		AREA_FRACTION=24, RAW_INTEGRATED_DENSITY=25, CHANNEL=26, SLICE=27, FRAME=28, 
		FERET_X=29, FERET_Y=30, FERET_ANGLE=31, MIN_FERET=32, ASPECT_RATIO=33,
		ROUNDNESS=34, SOLIDITY=35, LAST_HEADING=35;
	private static final String[] defaultHeadings = {"Area","Mean","StdDev","Mode","Min","Max",
		"X","Y","XM","YM","Perim.","BX","BY","Width","Height","Major","Minor","Angle",
		"Circ.", "Feret", "IntDen", "Median","Skew","Kurt", "%Area", "RawIntDen", "Ch", "Slice", "Frame", 
		 "FeretX", "FeretY", "FeretAngle", "MinFeret", "AR", "Round", "Solidity"};

	private int maxRows = 100; // will be increased as needed
	private int maxColumns = MAX_COLUMNS; // will be increased as needed
	private String[] headings = new String[maxColumns];
	private boolean[] keep = new boolean[maxColumns];
	private int counter;
	private double[][] columns = new double[maxColumns][];
	private String[] rowLabels;
	private int lastColumn = -1;
	private	StringBuilder sb;
	private int precision = 3;
	private int[] decimalPlaces;
	private String rowLabelHeading = "";
	private char delimiter = '\t';
	private boolean headingSet; 
	private boolean showRowNumbers = true;
	private boolean autoFormat = true;
	private Hashtable stringColumns;


	/** Constructs an empty ResultsTable with the counter=0, no columns
		and the precision set to 3 or the "Decimal places" value in
		Analyze/Set Measurements if that value is higher than 3. */
	public ResultsTable() {
		int p = Analyzer.getPrecision();
		if (p>precision)
			setPrecision(p);
	}
	
	/** Returns the ResultsTable used by the Measure command. This
		table must be displayed in the "Results" window. */
	public static ResultsTable getResultsTable() {
		return Analyzer.getResultsTable();
	}
		
	/** Returns the "Results" TextWindow. */
	public static TextWindow getResultsWindow() {
		Frame f = WindowManager.getFrame("Results");
		if (f==null || !(f instanceof TextWindow))
			return null;
		else
			return (TextWindow)f;
	}

	/** Increments the measurement counter by one. */
	public synchronized void incrementCounter() {
		counter++;
		if (counter==maxRows) {
			if (rowLabels!=null) {
				String[] s = new String[maxRows*2];
				System.arraycopy(rowLabels, 0, s, 0, maxRows);
				rowLabels = s;
			}
			for (int i=0; i<=lastColumn; i++) {
				if (columns[i]!=null) {
					double[] tmp = new double[maxRows*2];
					System.arraycopy(columns[i], 0, tmp, 0, maxRows);
					columns[i] = tmp;
				}
			}
			maxRows *= 2;
		}
	}
	
	/** Obsolete; the addValue() method automatically adds columns as needed.
	* @see #addValue(String, double)
	*/
	public synchronized void addColumns() {
		String[] tmp1 = new String[maxColumns*2];
		System.arraycopy(headings, 0, tmp1, 0, maxColumns);
		headings = tmp1;
		double[][] tmp2 = new double[maxColumns*2][];
		for (int i=0; i<maxColumns; i++)
			tmp2[i] = columns[i];
		columns = tmp2;
		boolean[] tmp3 = new boolean[maxColumns*2];
		System.arraycopy(keep, 0, tmp3, 0, maxColumns);
		keep = tmp3;
		maxColumns *= 2;
	}
	
	/** Returns the current value of the measurement counter. */
	public int getCounter() {
		return counter;
	}
	
	/** Returns the size of this ResultsTable. */
	public int size() {
		return counter;
	}

	/** Adds a value to the end of the given column. Counter must be >0.*/
	public void addValue(int column, double value) {
		if (column>=maxColumns)
			addColumns();
		if (column<0 || column>=maxColumns)
			throw new IllegalArgumentException("Column out of range");
		if (counter==0)
			throw new IllegalArgumentException("Counter==0");
		if (columns[column]==null) {
			columns[column] = new double[maxRows];
			if (headings[column]==null)
				headings[column] = "---";
			if (column>lastColumn) lastColumn = column;
		}
		columns[column][counter-1] = value;
	}
	
	/** Adds a value to the end of the given column. If the column
		does not exist, it is created.  Counter must be >0.
		There is an example at:<br>
		http://imagej.nih.gov/ij/plugins/sine-cosine.html
		*/
	public void addValue(String column, double value) {
		if (column==null)
			throw new IllegalArgumentException("Column is null");
		int index = getColumnIndex(column);
		if (index==COLUMN_NOT_FOUND)
			index = getFreeColumn(column);
		addValue(index, value);
		keep[index] = true;
	}
	
	/** Adds a string value to the end of the given column. If the column
		does not exist, it is created.  Counter must be >0. */
	public void addValue(String column, String value) {
		if (column==null)
			throw new IllegalArgumentException("Column is null");
		int index = getColumnIndex(column);
		if (index==COLUMN_NOT_FOUND)
			index = getFreeColumn(column);
		addValue(index, Double.NaN);
		setValue(column, getCounter()-1, value);
		keep[index] = true;
	}

	/** Adds a label to the beginning of the current row. Counter must be >0. */
	public void addLabel(String label) {
		if (rowLabelHeading.equals(""))
			rowLabelHeading = "Label";
		addLabel(rowLabelHeading, label);
	}

	/** Adds a label to the beginning of the current row. Counter must be >0. */
	public void addLabel(String columnHeading, String label) {
		if (counter==0)
			throw new IllegalArgumentException("Counter==0");
		if (rowLabels==null)
			rowLabels = new String[maxRows];
		rowLabels[counter-1] = label;
		if (columnHeading!=null)
			rowLabelHeading = columnHeading;
	}
	
	/** Adds a label to the beginning of the specified row, 
		or updates an existing lable, where 0<=row<counter.
		After labels are added or modified, call <code>show()</code>
		to update the window displaying the table. */
	public void setLabel(String label, int row) {
		if (row<0||row>=counter)
			throw new IllegalArgumentException("row>=counter");
		if (rowLabels==null)
			rowLabels = new String[maxRows];
		if (rowLabelHeading.equals(""))
			rowLabelHeading = "Label";
		rowLabels[row] = label;
	}
	
	/** Set the row label column to null if the column label is "Label". */
	public void disableRowLabels() {
		if (rowLabelHeading.equals("Label"))
			rowLabels = null;
	}
	
	/** Returns a copy of the given column as a float array,
		or null if the column is empty. */
	public float[] getColumn(int column) {
		if ((column<0) || (column>=maxColumns))
			throw new IllegalArgumentException("Index out of range: "+column);
		if (columns[column]==null)
			return null;
		else {
			float[] data = new float[counter];
			for (int i=0; i<counter; i++)
				data[i] = (float)columns[column][i];
			return data;
		}
	}
	
	/** Returns a copy of the given column as a double array,
		or null if the column is empty. */
	public double[] getColumnAsDoubles(int column) {
		if ((column<0) || (column>=maxColumns))
			throw new IllegalArgumentException("Index out of range: "+column);
		if (columns[column]==null)
			return null;
		else {
			double[] data = new double[counter];
			for (int i=0; i<counter; i++)
				data[i] = columns[column][i];
			return data;
		}
	}
	
	/** Returns the contents of this ResultsTable as a FloatProcessor. */
	public ImageProcessor getTableAsImage() {
		FloatProcessor fp = null;
		int columns = 0;
		int[] col = new int[lastColumn+1];
		for (int i=0; i<=lastColumn; i++) {
			if (columnExists(i)) {
				col[columns] = i;
				columns++;
			}
		}
		if (columns==0) return null;
		int rows = getCounter();
		if (rows==0) return null;
		fp = new FloatProcessor(columns, rows);
		for (int x=0; x<columns; x++) {
			for (int y=0; y<rows; y++)
				fp.setf(x,y,(float)getValueAsDouble(col[x],y));
		}
		return fp;
	}
	
	/** Creates a ResultsTable from an image or image selection. */
	public static ResultsTable createTableFromImage(ImageProcessor ip) {
		ResultsTable rt = new ResultsTable();
		Rectangle r = ip.getRoi();
		for (int y=r.y; y<r.y+r.height; y++) {
			rt.incrementCounter();
			rt.addLabel(" ", "Y"+y);
			for (int x=r.x; x<r.x+r.width; x++)
				rt.addValue("X"+x, ip.getPixelValue(x,y));
		}
		return rt;
	}

	/** Returns true if the specified column exists and is not empty. */
	public boolean columnExists(int column) {
		if ((column<0) || (column>=maxColumns))
			return false;
		else
			return columns[column]!=null;
	}

	/** Returns the index of the first column with the given heading.
		heading. If not found, returns COLUMN_NOT_FOUND. */
	public int getColumnIndex(String heading) {
		for (int i=0; i<headings.length; i++) {
			if (headings[i]==null)
				return COLUMN_NOT_FOUND;
			else if (headings[i].equals(heading))
				return i;
		}
		return COLUMN_NOT_FOUND;
	}
	
	/** Sets the heading of the the first available column and
		returns that column's index. Returns COLUMN_IN_USE
		 if this is a duplicate heading. */
	public int getFreeColumn(String heading) {
		for(int i=0; i<headings.length; i++) {
			if (headings[i]==null) {
				columns[i] = new double[maxRows];
				headings[i] = heading;
				if (i>lastColumn) lastColumn = i;
				return i;
			}
			if (headings[i].equals(heading))
				return COLUMN_IN_USE;
		}
		addColumns();
		lastColumn++;
		columns[lastColumn] = new double[maxRows];
		headings[lastColumn] = heading;
		return lastColumn;
	}
	
	/**	Returns the value of the given column and row, where
		column must be less than or equal the value returned by
		getLastColumn() and row must be greater than or equal
		zero and less than the value returned by getCounter(). */
	public double getValueAsDouble(int column, int row) {
		if (column>=maxColumns || row>=counter)
			throw new IllegalArgumentException("Index out of range: "+column+","+row);
		if (columns[column]==null)
			throw new IllegalArgumentException("Column not defined: "+column);
		return columns[column][row];
	}
	
	/**
	* @deprecated
	* replaced by getValueAsDouble
	*/
	public float getValue(int column, int row) {
		return (float)getValueAsDouble(column, row);
	}

	/**	Returns the value of the specified column and row, where
		column is the column heading and row is a number greater
		than or equal zero and less than value returned by getCounter(). 
		Throws an IllegalArgumentException if this ResultsTable
		does not have a column with the specified heading. */
	public double getValue(String column, int row) {
		if (row<0 || row>=getCounter())
			throw new IllegalArgumentException("Row out of range");
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND)
			throw new IllegalArgumentException("\""+column+"\" column not found");
		//IJ.log("col: "+col+" "+(col==COLUMN_NOT_FOUND?"not found":""+columns[col]));
		return getValueAsDouble(col,row);
	}

	/** Returns the string value of the given column and row,
		where row must be greater than or equal zero
		and less than the value returned by getCounter(). */
	public String getStringValue(String column, int row) {
		if (row<0 || row>=getCounter())
			throw new IllegalArgumentException("Row out of range");
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND)
			throw new IllegalArgumentException("\""+column+"\" column not found");
		return getStringValue(col, row);
	}

	/** Returns the string value of the given column and row, where
		column must be less than or equal the value returned by
		getLastColumn() and row must be greater than or equal
		zero and less than the value returned by getCounter(). */
	public String getStringValue(int column, int row) {
		if (column>=maxColumns || row>=counter)
			throw new IllegalArgumentException("Index out of range: "+column+","+row);
		if (columns[column]==null)
			throw new IllegalArgumentException("Column not defined: "+column);
		return getValueAsString(column, row);
	}

	/**	 Returns the label of the specified row. Returns null if the row does not have a label. */
	public String getLabel(int row) {
		if (row<0 || row>=getCounter())
			throw new IllegalArgumentException("Row out of range");
		String label = null;
		if (rowLabels!=null && rowLabels[row]!=null)
				label = rowLabels[row];
		return label;
	}

	/** Sets the value of the given column and row, where
		where 0&lt;=row&lt;counter. If the specified column does 
		not exist, it is created. When adding columns, 
		<code>show()</code> must be called to update the 
		window that displays the table.*/
	public void setValue(String column, int row, double value) {
		if (column==null)
			throw new IllegalArgumentException("Column is null");
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND) {
			col = getFreeColumn(column);
		}
		setValue(col, row, value);
	}

	/** Sets the value of the given column and row, where
		where 0&lt;=column&lt;=(lastRow+1 and 0&lt;=row&lt;=counter. */
	public void setValue(int column, int row, double value) {
		if (column>=maxColumns)
			addColumns();
		if (column<0 || column>=maxColumns)
			throw new IllegalArgumentException("Column out of range");
		if (row>=counter) {
			if (row==counter)
				incrementCounter();
			else
				throw new IllegalArgumentException("row>counter");
		}
		if (columns[column]==null) {
			columns[column] = new double[maxRows];
			if (column>lastColumn) lastColumn = column;
		}
		columns[column][row] = value;
	}

	/** Sets the string value of the given column and row, where
		where 0&lt;=row&lt;counter. If the specified column does 
		not exist, it is created. When adding columns, 
		<code>show()</code> must be called to update the 
		window that displays the table.*/
	public void setValue(String column, int row, String value) {
		if (column==null)
			throw new IllegalArgumentException("Column is null");
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND)
			col = getFreeColumn(column);
		setValue(col, row, value);
	}

	/** Sets the string value of the given column and row, where
		where 0&lt;=column&lt;=(lastRow+1 and 0&lt;=row&lt;=counter. */
	public void setValue(int column, int row, String value) {
		setValue(column, row, Double.NaN);
		if (stringColumns==null)
			stringColumns = new Hashtable();
		ArrayList stringColumn = (ArrayList)stringColumns.get(new Integer(column));
		if (stringColumn==null) {
			stringColumn = new ArrayList();
			stringColumns.put(new Integer(column), stringColumn);
		}
		int size = stringColumn.size();
		if (row>=size) {
			for (int i=size; i<row; i++)
				stringColumn.add(i, "");
		}
		if (row==stringColumn.size())
			stringColumn.add(row, value);
		else
			stringColumn.set(row, value);
	}

	/** Returns a tab or comma delimited string containing the column headings. */
	public String getColumnHeadings() {
		if (headingSet && !rowLabelHeading.equals("")) { // workaround setHeading() bug
			for (int i=0; i<=lastColumn; i++) {
				if (columns[i]!=null && rowLabelHeading.equals(headings[i]))
					{headings[i]=null; columns[i]=null;}
			}
			headingSet = false;
		}
		StringBuilder sb = new StringBuilder(200);
		if (showRowNumbers)
			sb.append(" "+delimiter);
		if (rowLabels!=null)
			sb.append(rowLabelHeading + delimiter);
		String heading;
		for (int i=0; i<=lastColumn; i++) {
			if (columns[i]!=null) {
				heading = headings[i];
				if (heading==null) heading ="---"; 
				sb.append(heading);
				if (i!=lastColumn) sb.append(delimiter);
			}
		}
		return new String(sb);
	}

	/** Returns the column headings as an array of Strings. */
	public String[] getHeadings() {
		int n = 0;
		if (rowLabels!=null)
			n++;
		for (int i=0; i<=lastColumn; i++)
			if (columns[i]!=null) n++;
		String[] temp = new String[n];
		int index = 0;
		if (rowLabels!=null)
			temp[index++] = rowLabelHeading;
		String heading;
		for (int i=0; i<=lastColumn; i++) {
			if (columns[i]!=null) {
				heading = headings[i];
				if (heading==null) heading ="---"; 
				temp[index++] = heading;
			}
		}
		return temp;
	}

	/** Returns the heading of the specified column or null if the column is empty. */
	public String getColumnHeading(int column) {
		if ((column<0) || (column>=maxColumns))
			throw new IllegalArgumentException("Index out of range: "+column);
		return headings[column];
	}

	/** Returns a tab or comma delimited string representing the
		given row, where 0<=row<=counter-1. */
	public String getRowAsString(int row) {
		if ((row<0) || (row>=counter))
			throw new IllegalArgumentException("Row out of range: "+row);
		if (sb==null)
			sb = new StringBuilder(200);
		else
			sb.setLength(0);
		if (showRowNumbers) {
			sb.append(Integer.toString(row+1));
			sb.append(delimiter);
		}
		if (rowLabels!=null) {
			if (rowLabels[row]!=null) {
				String label = rowLabels[row];
				if (delimiter==',')
					label = label.replaceAll(",", ";");
				sb.append(label);
			}
			sb.append(delimiter);
		}
		for (int i=0; i<=lastColumn; i++) {
			if (columns[i]!=null) {
				sb.append(getValueAsString(i,row));
				if (i!=lastColumn)
					sb.append(delimiter);
			}
		}
		return new String(sb);
	}
	
	private String getValueAsString(int column, int row) { 
		double value = columns[column][row];
		//IJ.log("getValueAsString1: col="+column+ ", row= "+row+", value= "+value+", size="+stringColumns.size());
		if (Double.isNaN(value) && stringColumns!=null) {
			String string = "NaN";
			ArrayList stringColumn = (ArrayList)stringColumns.get(new Integer(column));
			if (stringColumn==null)
				return string;
			//IJ.log("getValueAsString2: "+column+ +row+" "+stringColumn.size());
			if (row>=0 && row<stringColumn.size()) {
				string = (String)stringColumn.get(row);
				if (string!=null && string.contains("\n"))
					string = string.replaceAll("\n", "\\\\n");
				return string;
			} else
				return string;
		} else {
			if (decimalPlaces!=null && column<decimalPlaces.length)
				return d2s(value, decimalPlaces[column]);
			else
				return n(value);
		}
	}
	
	private String n(double n) {
		String s;
		if (autoFormat && (int)n==n && precision>=0)
			s = d2s(n, 0);
		
			s = d2s(n, precision);
		return s;
	}
		
	/**
	* @deprecated
	* replaced by addValue(String,double) and setValue(String,int,double)
	*/
	public void setHeading(int column, String heading) {
		if ((column<0) || (column>=headings.length))
			throw new IllegalArgumentException("Column out of range: "+column);
		headings[column] = heading;
		if (columns[column]==null)
			columns[column] = new double[maxRows];
		if (column>lastColumn) lastColumn = column;
		headingSet = true;
	}
	
	/** Sets the headings used by the Measure command ("Area", "Mean", etc.). */
	public void setDefaultHeadings() {
		for(int i=0; i<defaultHeadings.length; i++)
				headings[i] = defaultHeadings[i];
	}

	/** Sets the decimal places (digits to the right of decimal point)
		that are used when this table is displayed. */
	public void setPrecision(int decimalPlaces) {
		this.precision = decimalPlaces;
	}
	
	/** Sets the decimal places (digits to the right of 
		decimal point)  for each column. */
	public void setPrecision(int[] decimalPlaces) {
		this.decimalPlaces = decimalPlaces;
	}

	public void showRowNumbers(boolean showNumbers) {
		showRowNumbers = showNumbers;
	}

	private static DecimalFormat[] df;
	private static DecimalFormat[] sf;
	private static DecimalFormatSymbols dfs;

	/** This is a version of IJ.d2s() that uses scientific notation for
		small numbes that would otherwise display as zero. */
	public static String d2s(double n, int decimalPlaces) {
		if (Double.isNaN(n)||Double.isInfinite(n))
			return ""+n;
		if (n==Float.MAX_VALUE) // divide by 0 in FloatProcessor
			return "3.4e38";
		double np = n;
		if (n<0.0) np = -n;
		if ((np<0.001 && np!=0.0 && np<1.0/Math.pow(10,decimalPlaces)) || np>999999999999d || decimalPlaces<0) {
			if (decimalPlaces<0) {
				decimalPlaces = -decimalPlaces;
				if (decimalPlaces>9) decimalPlaces=9;
			} else
				decimalPlaces = 3;
			if (sf==null) {
				sf = new DecimalFormat[10];
				sf[1] = new DecimalFormat("0.0E0",dfs);
				sf[2] = new DecimalFormat("0.00E0",dfs);
				sf[3] = new DecimalFormat("0.000E0",dfs);
				sf[4] = new DecimalFormat("0.0000E0",dfs);
				sf[5] = new DecimalFormat("0.00000E0",dfs);
				sf[6] = new DecimalFormat("0.000000E0",dfs);
				sf[7] = new DecimalFormat("0.0000000E0",dfs);
				sf[8] = new DecimalFormat("0.00000000E0",dfs);
				sf[9] = new DecimalFormat("0.000000000E0",dfs);
			}
			return sf[decimalPlaces].format(n); // use scientific notation
		}
		if (decimalPlaces<0) decimalPlaces = 0;
		if (decimalPlaces>9) decimalPlaces = 9;
		if (df==null) {
			dfs = new DecimalFormatSymbols(Locale.US);
			df = new DecimalFormat[10];
			df[0] = new DecimalFormat("0", dfs);
			df[1] = new DecimalFormat("0.0", dfs);
			df[2] = new DecimalFormat("0.00", dfs);
			df[3] = new DecimalFormat("0.000", dfs);
			df[4] = new DecimalFormat("0.0000", dfs);
			df[5] = new DecimalFormat("0.00000", dfs);
			df[6] = new DecimalFormat("0.000000", dfs);
			df[7] = new DecimalFormat("0.0000000", dfs);
			df[8] = new DecimalFormat("0.00000000", dfs);
			df[9] = new DecimalFormat("0.000000000", dfs);
		}
		return df[decimalPlaces].format(n);
	}

	/** Deletes the specified row. */
	public synchronized void deleteRow(int row) {
		if (counter==0 || row<0 || row>counter-1) return;
		if (rowLabels!=null) {
			rowLabels[row] = null;
			for (int i=row; i<counter-1; i++)
				rowLabels[i] = rowLabels[i+1];
		}
		for (int col=0; col<=lastColumn; col++) {
			if (columns[col]!=null) {
				for (int i=row; i<counter-1; i++)
					columns[col][i] = columns[col][i+1];
				ArrayList stringColumn = stringColumns!=null?(ArrayList)stringColumns.get(new Integer(col)):null;
				if (stringColumn!=null && stringColumn.size()==counter) {
					for (int i=row; i<counter-1; i++)
						stringColumn.set(i,stringColumn.get(i+1));
					stringColumn.remove(counter-1);
				}
			}
		}
		counter--;
	}
	
	public synchronized void reset() {
		counter = 0;
		maxRows = 100;
		for (int i=0; i<maxColumns; i++) {
			columns[i] = null;
			headings[i] = null;
			keep[i] = false;
		}
		lastColumn = -1;
		rowLabels = null;
		stringColumns = null;
	}
	
	/** Returns the index of the last used column, or -1 if no columns are used. */
	public int getLastColumn() {
		return lastColumn;
	}

	/** Adds the last row in this table to the Results window without updating it. */
	public void addResults() {
		if (counter==1)
			IJ.setColumnHeadings(getColumnHeadings());		
		TextPanel textPanel = IJ.getTextPanel();
		String s = getRowAsString(counter-1);
		if (textPanel!=null)
				textPanel.appendWithoutUpdate(s);
		else
			System.out.println(s);
	}

	/** Updates the Results window. */
	public void updateResults() {
		TextPanel textPanel = IJ.getTextPanel();
		if (textPanel!=null) {
			textPanel.updateColumnHeadings(getColumnHeadings());		
			textPanel.updateDisplay();
		}
	}
	
	/** Displays the contents of this ResultsTable in a window with 
		the specified title, or updates an existing results window. Opens
		a new window if there is no open text window with this title. 
		The title must be "Results" if this table was obtained using 
		ResultsTable.getResultsTable() or Analyzer.getResultsTable . */
	public void show(String windowTitle) {
		if (!windowTitle.equals("Results") && this==Analyzer.getResultsTable())
			IJ.log("ResultsTable.show(): the system ResultTable should only be displayed in the \"Results\" window.");
		String tableHeadings = getColumnHeadings();		
		TextPanel tp;
		boolean newWindow = false;
		boolean cloneNeeded = false;
		if (windowTitle.equals("Results")) {
			tp = IJ.getTextPanel();
			if (tp==null) return;
			newWindow = tp.getLineCount()==0;
			if (!newWindow && tp.getLineCount()==getCounter()-1 && ResultsTable.getResultsTable()==this
			&& tp.getColumnHeadings().equals(tableHeadings)) {
				String s = getRowAsString(getCounter()-1);
				tp.append(s);
				return;
			}
			IJ.setColumnHeadings(tableHeadings);
			if (this!=Analyzer.getResultsTable())
				Analyzer.setResultsTable(this);
			if (getCounter()>0)
				Analyzer.setUnsavedMeasurements(true);
		} else {
			Frame frame = WindowManager.getFrame(windowTitle);
			TextWindow win;
			if (frame!=null && frame instanceof TextWindow) {
				win = (TextWindow)frame;
				win.toFront();
			} else {
				int width = getLastColumn()<=1?250:400;
				win = new TextWindow(windowTitle, "", width, 300);
				cloneNeeded = true;
			}
			tp = win.getTextPanel();
			tp.setColumnHeadings(tableHeadings);
			newWindow = tp.getLineCount()==0;
			if (!windowTitle.startsWith("Summary"))
				autoFormat = false;
		}
		tp.setResultsTable(cloneNeeded?(ResultsTable)this.clone():this);
		int n = getCounter();
		if (n>0) {
			if (tp.getLineCount()>0) tp.clear();
			for (int i=0; i<n; i++)
				tp.appendWithoutUpdate(getRowAsString(i));
			tp.updateDisplay();
		}
		if (newWindow) tp.scrollToTop();
	}
	
	public void update(int measurements, ImagePlus imp, Roi roi) {
		if (roi==null && imp!=null) roi = imp.getRoi();
		ResultsTable rt2 = new ResultsTable();
		Analyzer analyzer = new Analyzer(imp, measurements, rt2);
		ImageProcessor ip = new ByteProcessor(1, 1);
		ImageStatistics stats = new ByteStatistics(ip, measurements, null);
		analyzer.saveResults(stats, roi);
		//IJ.log(rt2.getColumnHeadings());
		int last = rt2.getLastColumn();
		//IJ.log("update1: "+last+"  "+getMaxColumns());
		while (last+1>=getMaxColumns()) {
			addColumns();
		//IJ.log("addColumns: "+getMaxColumns());
		}
		if (last<getLastColumn()) {
			last=getLastColumn();
			if (last>=rt2.getMaxColumns())
				last = rt2.getMaxColumns() - 1;
		}
		for (int i=0; i<=last; i++) {
			//IJ.log(i+"  "+rt2.getColumn(i)+"  "+columns[i]+"  "+rt2.getColumnHeading(i)+"  "+getColumnHeading(i));
			if (rt2.getColumn(i)!=null && columns[i]==null) {
				columns[i] = new double[maxRows];
				headings[i] = rt2.getColumnHeading(i);
				if (i>lastColumn) lastColumn = i;
			} else if (rt2.getColumn(i)==null && columns[i]!=null && !keep[i])
				columns[i] = null;
		}
		if (rt2.getRowLabels()==null)
			rowLabels = null;
		else if (rt2.getRowLabels()!=null && rowLabels==null) {
			rowLabels = new String[maxRows];
			rowLabelHeading = "Label";
		}
		if (getCounter()>0) show("Results");
	}
	
	int getMaxColumns() {
		return maxColumns;
	}
	
	String[] getRowLabels() {
		return rowLabels;
	}
	
	/** Opens a tab or comma delimited text file and returns it 
	* as a ResultsTable, without requiring a try/catch statement.
	* Displays a file open dialog if 'path' is empty or null.
	*/
	public static ResultsTable open2(String path) {
		ResultsTable rt = null;
		try {
			rt = open(path);
		} catch (IOException e) {
			IJ.error("Open Results", e.getMessage());
			rt = null;
		}
		return rt;
	}
	
	/** Opens a tab or comma delimited text file and returns it as a 
	* ResultsTable. Displays a file open dialog if 'path' is empty or null.
	* @see #open2(String)
	*/
	public static ResultsTable open(String path) throws IOException {
		final String lineSeparator =  "\n";
		final String cellSeparator =  ",\t";
		String text =IJ.openAsString(path);
		if (text==null)
			return null;
		if (text.startsWith("Error:"))
			throw new IOException("Error opening "+path);
		String[] lines = Tools.split(text, lineSeparator);
		if (lines.length==0)
			throw new IOException("Table is empty or invalid");
		String[] headings = Tools.split(lines[0], cellSeparator);
		if (headings.length==1)
			throw new IOException("This is not a tab or comma delimited text file.");
		int numbersInHeadings = 0;
		for (int i=0; i<headings.length; i++) {
			if (headings[i].equals("NaN") || !Double.isNaN(Tools.parseDouble(headings[i])))
				numbersInHeadings++;
		}
		boolean allNumericHeadings = numbersInHeadings==headings.length;
		if (allNumericHeadings) {
			for (int i=0; i<headings.length; i++)
				headings[i] = "C"+(i+1);
		}
		int firstColumn = headings[0].equals(" ")?1:0;
		for (int i=0; i<headings.length; i++)
			headings[i] = headings[i].trim();
		int firstRow = allNumericHeadings?0:1;
		boolean labels = firstColumn==1 && headings[1].equals("Label");
		int type=getTableType(path, lines, firstRow, cellSeparator);
		if (!labels && (type==1||type==2))
			labels = true;
		int labelsIndex = (type==2)?0:1;
		if (lines[0].startsWith("\t")) {
			String[] headings2 = new String[headings.length+1];
			headings2[0] = " ";
			for (int i=0; i<headings.length; i++)
				headings2[i+1] = headings[i];
			headings = headings2;
			firstColumn = 1;
		}
		ResultsTable rt = new ResultsTable();
		for (int i=firstRow; i<lines.length; i++) {
			rt.incrementCounter();
			String[] items=Tools.split(lines[i], cellSeparator);
			for (int j=firstColumn; j<items.length; j++) {
				if (j==labelsIndex&&labels)
					rt.addLabel(headings[labelsIndex], items[labelsIndex]);
				else if (j<headings.length) {
					double value = Tools.parseDouble(items[j]);
					if (Double.isNaN(value))
						rt.addValue(headings[j], items[j]);
					else
						rt.addValue(headings[j], value);
				}
			}
		}
		return rt;
	}
	
	private static int getTableType(String path, String[] lines, int firstRow, String cellSeparator) {
		if (lines.length<2) return 0;
		String[] items=Tools.split(lines[1], cellSeparator);
		int nonNumericCount = 0;
		int nonNumericIndex = 0;
		for (int i=0; i<items.length; i++) {
			if (!items[i].equals("NaN") && Double.isNaN(Tools.parseDouble(items[i]))) {
				nonNumericCount++;
				nonNumericIndex = i;
			}
		}
		boolean csv = path.endsWith(".csv");
		if (nonNumericCount==0)
			return 0; // assume this is all-numeric table
		if (nonNumericCount==1 && nonNumericIndex==1)
			return 1; // assume this is an ImageJ Results table with row numbers and row labels
		if (nonNumericCount==1 && nonNumericIndex==0)
			return 2; // assume this is an ImageJ Results table without row numbers and with row labels
		return 3;
	}
	
	/** Saves this ResultsTable as a tab or comma delimited text file. The table
	     is saved as a CSV (comma-separated values) file if 'path' ends with ".csv".
	     Displays a file save dialog if 'path' is empty or null. Does nothing if the
	     table is empty. */
	public void saveAs(String path) throws IOException {
		if (getCounter()==0 && lastColumn<0) return;
		if (path==null || path.equals("")) {
			SaveDialog sd = new SaveDialog("Save Results", "Results", Prefs.get("options.ext", ".xls"));
			String file = sd.getFileName();
			if (file==null) return;
			path = sd.getDirectory() + file;
		}
		delimiter = path.endsWith(".csv")?',':'\t';
		PrintWriter pw = null;
		FileOutputStream fos = new FileOutputStream(path);
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		pw = new PrintWriter(bos);
		boolean saveShowRowNumbers = showRowNumbers;
		if (Prefs.dontSaveRowNumbers)	
			showRowNumbers = false;
		if (!Prefs.dontSaveHeaders) {
			String headings = getColumnHeadings();
			pw.println(headings);
		}
		for (int i=0; i<getCounter(); i++)
			pw.println(getRowAsString(i));
		showRowNumbers = saveShowRowNumbers;
		pw.close();
		delimiter = '\t';
	}
	
	public static String getDefaultHeading(int index) {
		if (index>=0 && index<defaultHeadings.length)
			return defaultHeadings[index];
		else
			return "null";
	}

	/** Duplicates this ResultsTable. */
	public synchronized Object clone() {
		try { 
			ResultsTable rt2 = (ResultsTable)super.clone();
			rt2.headings = new String[headings.length];
			for (int i=0; i<=lastColumn; i++)
				rt2.headings[i] = headings[i];
			rt2.columns = new double[columns.length][];
			for (int i=0; i<=lastColumn; i++) {
				if (columns[i]!=null) {
					double[] data = new double[maxRows];
					for (int j=0; j<counter; j++)
						data[j] = columns[i][j];
					rt2.columns[i] = data;
				}
			}
			if (rowLabels!=null) {
				rt2.rowLabels = new String[rowLabels.length];
				for (int i=0; i<counter; i++)
					rt2.rowLabels[i] = rowLabels[i];
			}
			if (stringColumns!=null) {
				rt2.stringColumns = new Hashtable();
				Set set = stringColumns.keySet();
				for (Iterator i=set.iterator(); i.hasNext();) {
					Integer column = (Integer)i.next();
					ArrayList list = (ArrayList)stringColumns.get(column);
					rt2.stringColumns.put(column, list.clone());
				}
			}
			return rt2;
		}
		catch (CloneNotSupportedException e) {return null;}
	}
	
	public String toString() {
		return ("ctr="+counter+", hdr="+getColumnHeadings());
	}
	
}
