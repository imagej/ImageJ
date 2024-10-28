package ij.measure;
import ij.*;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.Editor;
import ij.text.*;
import ij.process.*;
import ij.gui.Roi;
import ij.util.Tools;
import ij.io.*;
import ij.macro.*;
import java.awt.*;
import java.text.*;
import java.util.*;
import java.io.*;
import java.math.RoundingMode;


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
	public static final short AUTO_FORMAT = Short.MIN_VALUE;
	private static final char commaSubstitute = 0x08B3;
	
	public static final int AREA=0, MEAN=1, STD_DEV=2, MODE=3, MIN=4, MAX=5,
		X_CENTROID=6, Y_CENTROID=7, X_CENTER_OF_MASS=8, Y_CENTER_OF_MASS=9,
		PERIMETER=10, ROI_X=11, ROI_Y=12, ROI_WIDTH=13, ROI_HEIGHT=14,
		MAJOR=15, MINOR=16, ANGLE=17, CIRCULARITY=18, FERET=19, 
		INTEGRATED_DENSITY=20, MEDIAN=21, SKEWNESS=22, KURTOSIS=23, 
		AREA_FRACTION=24, RAW_INTEGRATED_DENSITY=25, CHANNEL=26, SLICE=27, FRAME=28, 
		FERET_X=29, FERET_Y=30, FERET_ANGLE=31, MIN_FERET=32, ASPECT_RATIO=33,
		ROUNDNESS=34, SOLIDITY=35, MIN_THRESHOLD=36, MAX_THRESHOLD=37, LAST_HEADING=37;
	private static final String[] defaultHeadings = {"Area","Mean","StdDev","Mode","Min","Max",
		"X","Y","XM","YM","Perim.","BX","BY","Width","Height","Major","Minor","Angle",
		"Circ.", "Feret", "IntDen", "Median","Skew","Kurt", "%Area", "RawIntDen", "Ch", "Slice", "Frame", 
		 "FeretX", "FeretY", "FeretAngle", "MinFeret", "AR", "Round", "Solidity", "MinThr", "MaxThr"};

	private int maxRows = 100; // will be increased as needed
	private int maxColumns = MAX_COLUMNS; // will be increased as needed
	private String[] headings = new String[maxColumns];
	private boolean[] keep = new boolean[maxColumns];
	private short[] decimalPlaces = new short[maxColumns];
	private int counter;
	private double[][] columns = new double[maxColumns][];
	private String[] rowLabels;
	private int lastColumn = -1;
	private	StringBuilder sb;
	private short precision = 3;
	private String rowLabelHeading = "";
	private char delimiter = '\t';
	private boolean headingSet; 
	private boolean showRowNumbers;
	private boolean showRowNumbersSet;
	private int baseRowNumber = 1;
	private Hashtable stringColumns;
	private boolean NaNEmptyCells;
	private boolean quoteCommas;
	private String title;
	private boolean columnDeleted;
	private boolean renameWhenSaving;
	private boolean saveColumnHeaders = !Prefs.dontSaveHeaders;
	public boolean isResultsTable;


	/** Constructs an empty ResultsTable with the counter=0, no columns
		and the precision set to 3 or the "Decimal places" value in
		Analyze/Set Measurements if that value is higher than 3. */
	public ResultsTable() {
		init();
	} 
	
	/** Constructs a ResultsTable with 'nRows' rows. */
	public ResultsTable(Integer nRows) {
		init();
		for (int i=0; i<nRows; i++)
			incrementCounter();
	} 
	
	private void init() {
		int p = Analyzer.getPrecision();
		if (p>precision)
			precision = (short)p;
		for (int i=0; i<decimalPlaces.length; i++)
			decimalPlaces[i] = AUTO_FORMAT;
	}

	/** Returns the ResultsTable used by the Measure command. This
		table must be displayed in the "Results" window. */
	public static ResultsTable getResultsTable() {
		return Analyzer.getResultsTable();
	}
		
	/** Returns the ResultsTable with the specified title, or null if it does not exist, */
	public static ResultsTable getResultsTable(String title) {
		Frame f = WindowManager.getFrame(title);
		if (f!=null && (f instanceof TextWindow))
			return ((TextWindow)f).getResultsTable();
		else
			return null;
	}
	
	/** Returns the active (front most) displayed ResultsTable. */
	public static ResultsTable getActiveTable() {
		ResultsTable rt = null;
		Window win = WindowManager.getActiveTable();
		if (win!=null && (win instanceof TextWindow)) {
			TextPanel tp = ((TextWindow)win).getTextPanel();
			rt = tp.getOrCreateResultsTable();
		}
		return rt;
	}
		
	/** Obsolete. */
	public static TextWindow getResultsWindow() {
		Frame f = WindowManager.getFrame("Results");
		if (f==null || !(f instanceof TextWindow))
			return null;
		else
			return (TextWindow)f;
	}

	/** Adds a row to the table. */
	public void addRow() {
		incrementCounter();
	}

	/** Adds a row to the table. */
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
					if (NaNEmptyCells)
						Arrays.fill(tmp, maxRows, tmp.length, Double.NaN);
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
		short[] tmp4 = new short[maxColumns*2];
		for (int i=0; i<tmp4.length; i++)
			tmp4[i] = AUTO_FORMAT;
		System.arraycopy(decimalPlaces, 0, tmp4, 0, maxColumns);
		decimalPlaces = tmp4;
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

	/** Adds a numeric value to the specified column, on the last
	 * table row. Use addRow() to add another row to
	 * the table.
	 * @see #addRow
	 * @see #addValue(String,double)
	 * @see #addValue(String,String)
	 * @see #size
	*/
	public void addValue(int column, double value) {
		if (column>=maxColumns)
			addColumns();
		if (column<0 || column>=maxColumns)
			throw new IllegalArgumentException("Column out of range");
		if (counter==0)
			incrementCounter();
		if (columns[column]==null) {
			columns[column] = new double[maxRows];
			if (NaNEmptyCells)
				Arrays.fill(columns[column], Double.NaN);
			if (headings[column]==null)
				headings[column] = "C"+(column+1);
			if (column>lastColumn) lastColumn = column;
		}
		columns[column][counter-1] = value;
		if (counter<25) {
			if ((int)value!=value && !Double.isNaN(value))
				decimalPlaces[column] = (short)precision;
		}
	}
	
	/** Adds a numeric value to the specified column, on the last
	 * table row. If the column does not exist, it is created.
	 * Use addRow() to add another row to the table.
	 * <p>JavaScript example:
	 * <pre>
	 * rt = new ResultsTable();
	 * for (n=0; n<=2*Math.PI; n+=0.1) {
	 *    rt.addRow();
	 *    rt.addValue("n", n);
	 *    rt.addValue("Sine(n)", Math.sin(n));
	 *    rt.addValue("Cos(n)", Math.cos(n));
	 * }
	 * rt.show("Sine/Cosine Table");
	 * </pre>
	 * @see #addRow
	 * @see #addValue(String,String)
	 * @see #size
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
	
	/** Adds a string value to the specified column, on the last
	 * table row. If the column does not exist, it is created.
	 * Use addRow() to add another row to the table.
	 * @see #addRow
	 * @see #addValue(String,double)
	 * @see #size
	 */
	public void addValue(String column, String value) {
		if (column==null)
			throw new IllegalArgumentException("Column is null");
		int index = getColumnIndex(column);
		if (index==COLUMN_NOT_FOUND)
			index = getFreeColumn(column);
		addValue(index, Double.NaN);
		setValue(column, size()-1, value);
		keep[index] = true;
	}

	/** Adds a label to the beginning of the current row. */
	public void addLabel(String label) {
		if (rowLabelHeading.equals(""))
			rowLabelHeading = "Label";
		addLabel(rowLabelHeading, label);
	}

	/**
	 * @deprecated
	 * Replaced by setValue(String,int,String)
	*/
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
		or updates an existing lable, where 0<=row<size().
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
	
	/** Returns a copy of the given column as a double array,
		or null if the column is not found. */
	public double[] getColumn(String column) {
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND || columns[col]==null)
			throw new IllegalArgumentException("\""+column+"\" column not found");
		return getColumnAsDoubles(col);
	}

	/** Returns a copy of the given column as a String array,
		or null if the column is not found. */
	public String[] getColumnAsStrings(String column) {
		String[] array = new String[size()];
		if ("Label".equals(column) && rowLabels!=null) {
			for (int i=0; i<size(); i++)
				array[i] = getLabel(i);
			return array;
		}
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND || columns[col]==null)
			throw new IllegalArgumentException("\""+column+"\" column not found");
		for (int i=0; i<size(); i++)
			array[i] = getStringValue(col, i);
		return array;
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
		int rows = size();
		if (rows==0) return null;
		fp = new FloatProcessor(columns, rows);
		for (int x=0; x<columns; x++) {
			for (int y=0; y<rows; y++)
				fp.setf(x,y,(float)getValueAsDouble(col[x],y));
		}
		return fp;
	}
	
	/** Creates a ResultsTable from an image or image selection. */
	public static ResultsTable createTableFromImage(ImagePlus imp) {
		if (imp==null)
			return null;
		Roi roi = imp.getRoi();
		ImageProcessor ip = imp.getProcessor();
		if (roi==null || roi.getType()==Roi.RECTANGLE)
			return createTableFromImage(ip);
		ResultsTable rt = new ResultsTable();
		Rectangle r = ip.getRoi();
		for (int y=r.y; y<r.y+r.height; y++) {
			rt.incrementCounter();
			rt.addLabel(" ", "Y"+y);
			for (int x=r.x; x<r.x+r.width; x++) {
				if (roi.contains(x,y))
					rt.addValue("X"+x, ip.getPixelValue(x,y));
				else
					rt.addValue("X"+x, Double.NaN);
			}
		}
		return rt;
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

	/** Returns 'true' if the specified column exists and is not empty. */
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
				if (NaNEmptyCells)
					Arrays.fill(columns[i], Double.NaN);
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
		if (NaNEmptyCells)
			Arrays.fill(columns[lastColumn], Double.NaN);
		headings[lastColumn] = heading;
		return lastColumn;
	}
	
	/**	Returns the value of the given column and row, where
		column must be less than or equal the value returned by
		getLastColumn() and row must be greater than or equal
		zero and less than the value returned by size(). */
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
		than or equal zero and less than value returned by size(). 
		Throws an IllegalArgumentException if this ResultsTable
		does not have a column with the specified heading. */
	public double getValue(String column, int row) {
		if (row<0 || row>=size())
			throw new IllegalArgumentException("Row out of range");
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND)
			throw new IllegalArgumentException("\""+column+"\" column not found");
		//IJ.log("col: "+col+" "+(col==COLUMN_NOT_FOUND?"not found":""+columns[col]));
		return getValueAsDouble(col,row);
	}
	
	/** Returns 'true' if the specified column exists and is not emptly. */
	public boolean columnExists(String column) {
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND)
			return false;
		else
			return (col<columns.length && columns[col]!=null);
	}

	/** Returns the string value of the given column and row,
		where row must be greater than or equal zero
		and less than the value returned by size(). */
	public String getStringValue(String column, int row) {
		if (row<0 || row>=size())
			throw new IllegalArgumentException("Row out of range");
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND) {
			String label = null;
			if ("Label".equals(column))
				label = getLabel(row);
			if (label!=null)
				return label;
			else
				throw new IllegalArgumentException("\""+column+"\" column not found");
		}
		return getStringValue(col, row);
	}

	/** Returns the string value of the given column and row, where
		column must be less than or equal the value returned by
		getLastColumn() and row must be greater than or equal
		zero and less than the value returned by size(). */
	public String getStringValue(int column, int row) {
		if (column>=maxColumns || row>=counter)
			throw new IllegalArgumentException("Index out of range: "+column+","+row);
		if (columns[column]==null)
			throw new IllegalArgumentException("Column not defined: "+column);
		return getValueAsString(column, row);
	}

	/**	 Returns the label of the specified row. Returns null if the row does not have a label. */
	public String getLabel(int row) {
		if (row<0 || row>=size())
			throw new IllegalArgumentException("Row out of range");
		String label = null;
		if (rowLabels!=null && rowLabels[row]!=null)
				label = rowLabels[row];
		return label;
	}

	/** Sets the value of the given column and row, where
		where 0&lt;=row&lt;size(). If the specified column does 
		not exist, it is created. When adding columns, 
		<code>show()</code> must be called to update the 
		window that displays the table.*/
	public void setValue(String column, int row, double value) {
		if (column==null)
			throw new IllegalArgumentException("Column is null");
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND)
			col = getFreeColumn(column);
		setValue(col, row, value);
	}

	/** Sets the value of the given column and row, where
		where 0&lt;=column&lt;=(lastRow+1 and 0&lt;=row&lt;=size(). */
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
			if (NaNEmptyCells)
				Arrays.fill(columns[column], Double.NaN);
			if (column>lastColumn) lastColumn = column;
		}
		columns[column][row] = value;
		if (headings[column]==null)
			headings[column] = "C"+(column+1);
		if ((int)value!=value && !Double.isNaN(value))
			decimalPlaces[column] = (short)precision;
	}

	/** Sets the string value of the given column and row, where
		where 0&lt;=row&lt;size(). If the specified column does 
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
		where 0&lt;=column&lt;=(lastRow+1 and 0&lt;=row&lt;=size(). */
	public void setValue(int column, int row, String value) {
		setValue(column, row, Double.NaN);
		if (stringColumns==null)
			stringColumns = new Hashtable();
		ArrayList stringColumn = (ArrayList)stringColumns.get(Integer.valueOf(column));
		if (stringColumn==null) {
			stringColumn = new ArrayList();
			stringColumns.put(Integer.valueOf(column), stringColumn);
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
	
	/** Sets the values of the given column to the values in the array.
	 *  If the specified column does not exist, it is created.
	 *  When adding columns, <code>show()</code> must be called to
	 *  update the window that displays the table.
	 *  If the array is shorter than the column length, the remaining
	 *  values of the column are left unchanged. If the array is longer,
	 *  the table is extended. String values are unaffected, but only
	 *  used if the numeric value at the given position is NaN. */
	public void setValues(String column, double[] values) {
		if (values.length > 0)
			setValue(column, 0, values[0]); //creates the column if required
		int col = getColumnIndex(column);
		for (int i=1; i<values.length; i++)
			setValue(col, i, values[i]);
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
				if (heading==null) heading ="C"+(i+1); 
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
				if (heading==null) heading ="C"+(i+1); 
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
		given row, where 0<=row<=size()-1. */
	public String getRowAsString(int row) {
		if ((row<0) || (row>=counter))
			throw new IllegalArgumentException("Row out of range: "+row);
		if (sb==null)
			sb = new StringBuilder(200);
		else
			sb.setLength(0);
		if (showRowNumbers) {
			sb.append(Integer.toString(row+baseRowNumber));
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
				String value = getValueAsString(i,row);
				if (quoteCommas) {
					if (value!=null && (value.contains(",")||value.equals("")))
						value = "\""+value+"\"";
				}
				sb.append(value);
				if (i!=lastColumn)
					sb.append(delimiter);
			}
		}
		return new String(sb);
	}
	
	/** Implements the Table.getColumn() macro function. */
	public Variable[] getColumnAsVariables(String column) {
		if ("Label".equals(column) && rowLabels!=null) {
			int n = size();
			Variable[] labels = new Variable[n];
			for (int i=0; i<n; i++) {
				String label = getLabel(i);
				labels[i] = new Variable(label!=null?label:"");
			}
			return labels;
		}
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND || columns[col]==null)
			throw new IllegalArgumentException("\""+column+"\" column not found");
		boolean firstValueNumeric = true;
		int nValues = size();
		Variable[] values = new Variable[nValues];
		for (int row=0; row<size(); row++) {
			double value = columns[col][row];
			String str = null;
			if (Double.isNaN(value) && stringColumns!=null) {
				ArrayList stringColumn = (ArrayList)stringColumns.get(Integer.valueOf(col));
				if (stringColumn!=null && row>=0 && row<stringColumn.size())
					str = (String)stringColumn.get(row);
			}
			if (str!=null)
				values[row] = new Variable(str);
			else {
				values[row] = new Variable(value);
				if (row==0) firstValueNumeric=true;
			}
		}
		if (nValues<values.length) {
			Variable[] values2 = new Variable[nValues];
			for (int i=0; i<nValues; i++)
				values2[i] = values[i];
			values = values2;
		}
		return values;
	}
	
	/** Implements the Table.setColumn() macro function. */
	public void setColumn(String column, Variable[] array) {
		if (column==null)
			return;
		int initialSize = size();
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND)
			col = getFreeColumn(column);
		for (int i=0; i<array.length; i++) {
			if (array[i].getString()!=null)
				setValue(col, i, array[i].getString());
			else
				setValue(col, i, array[i].getValue());
		}
		if (array.length<size()) {
			for (int i=array.length; i<size(); i++)
				setValue(col, i, "");
		}
		if (initialSize>0 && size()>initialSize) {
			for (int c=0; c<=lastColumn; c++) {
				if (c!=col && columns[c]!=null) {
					String heading = headings[c];
					if (heading!=null) {
						for (int i=initialSize; i<size(); i++)
							setValue(c, i, "");
					}
				}
			}
		}
	}
		
	private String getValueAsString(int column, int row) { 
		double value = columns[column][row];
		//IJ.log("getValueAsString1: col="+column+ ", row= "+row+", value= "+value+", size="+stringColumns.size());
		if (Double.isNaN(value) && stringColumns!=null) {
			String string = "NaN";
			ArrayList stringColumn = (ArrayList)stringColumns.get(Integer.valueOf(column));
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
			int places = decimalPlaces[column];
			if (places==AUTO_FORMAT)
				return n(value);
			else
				return d2s(value, places);
		}
	}
	
	private String n(double n) {
		String s;
		if ((int)n==n && precision>=0)
			s = d2s(n, 0);
		else
			s = d2s(n, precision);
		return s;
	}
		
	/**
	* @deprecated
	* Replaced by addValue(String,double) and setValue(String,int,double)
	*/
	public void setHeading(int column, String heading) {
		if ((column<0) || (column>=headings.length))
			throw new IllegalArgumentException("Column out of range: "+column);
		headings[column] = heading;
		if (columns[column]==null) {
			columns[column] = new double[maxRows];
			if (NaNEmptyCells)
				Arrays.fill(columns[column], Double.NaN);
		}
		if (column>lastColumn) lastColumn = column;
		headingSet = true;
	}
	
	/** Sets the headings used by the Measure command ("Area", "Mean", etc.). */
	public void setDefaultHeadings() {
		for(int i=0; i<defaultHeadings.length; i++)
				headings[i] = defaultHeadings[i];
		showRowNumbers(true);
	}

	/** Sets the decimal places (digits to the right of decimal point)
		that are used when this table is displayed. */
	public synchronized void setPrecision(int precision) {
		if (precision>9) precision=9;
		this.precision = (short)precision;
		for (int i=0; i<decimalPlaces.length; i++) {
			if (decimalPlaces[i]!=AUTO_FORMAT)
				decimalPlaces[i] = (short)precision;
		}
	}
	
	public void setDecimalPlaces(int column, int digits) {
		if ((column<0) || (column>=headings.length))
			throw new IllegalArgumentException("Column out of range: "+column);
		decimalPlaces[column] = (short)digits;
	}

	/** Set 'true' to initially fill data arrays with NaNs instead of zeros. */
	public void setNaNEmptyCells(boolean NaNEmptyCells) {
		this.NaNEmptyCells = NaNEmptyCells;
	}

	public void showRowNumbers(boolean showNumbers) {
		if (!showNumbers && this==Analyzer.getResultsTable()) {
			IJ.beep();
			IJ.showStatus("Standard \"Results\" table always has row numbers");
			return;
		}
		showRowNumbers = showNumbers;
		baseRowNumber = 1;
		showRowNumbersSet = true;
	}

	public boolean showRowNumbers() {
		return showRowNumbers;
	}

	public void showRowIndexes(boolean showIndexes) {
		showRowNumbers = showIndexes;
		baseRowNumber = showIndexes?0:1;
	}

	public void saveColumnHeaders(boolean save) {
		saveColumnHeaders = save;
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
		if ((np!=0.0 && np<1.0/Math.pow(10,decimalPlaces)) || np>999999999999d || decimalPlaces<0) {
			if (decimalPlaces<0) {
				decimalPlaces = -decimalPlaces;
				if (decimalPlaces>9) decimalPlaces=9;
			} else
				decimalPlaces = 3;
			if (sf==null) {
				if (dfs==null)
					dfs = new DecimalFormatSymbols(Locale.US);
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
			df[0].setRoundingMode(RoundingMode.HALF_UP);
		}
		return df[decimalPlaces].format(n);
	}

	/** Deletes the specified row. */
	public synchronized void deleteRow(int rowIndex) {
		if (counter==0 || rowIndex<0 || rowIndex>counter-1)
			return;
		if (rowLabels!=null) {
			rowLabels[rowIndex] = null;
			for (int i=rowIndex; i<counter-1; i++)
				rowLabels[i] = rowLabels[i+1];
		}
		for (int col=0; col<=lastColumn; col++) {
			if (columns[col]!=null) {
				for (int i=rowIndex; i<counter-1; i++)
					columns[col][i] = columns[col][i+1];
				ArrayList stringColumn = stringColumns!=null?(ArrayList)stringColumns.get(Integer.valueOf(col)):null;
				if (stringColumn!=null && stringColumn.size()==counter) {
					for (int i=rowIndex; i<counter-1; i++)
						stringColumn.set(i,stringColumn.get(i+1));
					stringColumn.remove(counter-1);
				}
			}
		}
		counter--;
	}
	
	/** Deletes the specified rows. */
	public void deleteRows(int index1, int index2) {
		if (index1<0) index1=0;
		int n = index2 - index1 + 1;
		for (int i=index1; i<index1+n; i++)
			deleteRow(index1);
	}
	
	/** Deletes the specified column. */
	public void deleteColumn(String column) {
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND)
			throw new IllegalArgumentException("\""+column+"\" column not found");
		columns[col] = null;
		headings[col] = "-";
		columnDeleted = true;
	}

	/** Changes the name of a column. */
	public void renameColumn(String oldName, String newName) {
		int oldCol = getColumnIndex(oldName);
		if (oldCol==COLUMN_NOT_FOUND)
			throw new IllegalArgumentException("\""+oldName+"\" column not found");
		int newCol = getColumnIndex(newName);
		if (columnExists(newCol))
			throw new IllegalArgumentException("\""+newName+"\" column exists");
		headings[oldCol] = newName;
	}

	public synchronized void reset() {
		counter = 0;
		maxRows = 100;
		for (int i=0; i<maxColumns; i++) {
			columns[i] = null;
			headings[i] = null;
			keep[i] = false;
			decimalPlaces[i] = AUTO_FORMAT;
		}
		lastColumn = -1;
		rowLabels = null;
		stringColumns = null;
		columnDeleted = false;
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
		if  (GraphicsEnvironment.isHeadless())
			return; // Tables can't be displayed in headless mode
		if (windowTitle==null)
			windowTitle = "Results";
		title = windowTitle;
		if (!windowTitle.equals("Results") && this==Analyzer.getResultsTable())
			IJ.log("ResultsTable.show(): the system ResultTable should only be displayed in the \"Results\" window.");
		if (windowTitle.equals("Results")) {
			if(!showRowNumbersSet)
				showRowNumbers(true);
			isResultsTable = true;
		}
		String tableHeadings = getColumnHeadings();		
		TextPanel tp;
		boolean newWindow = false;
		boolean cloneNeeded = false;
		if (windowTitle.equals("Results")) {
			tp = IJ.getTextPanel();
			if (tp==null) return;
			newWindow = tp.getLineCount()==0;
			if (!newWindow && tp.getLineCount()==size()-1 && ResultsTable.getResultsTable()==this
			&& tp.getColumnHeadings().equals(tableHeadings)) {
				String s = getRowAsString(size()-1);
				tp.append(s);
				return;
			}
			IJ.setColumnHeadings(tableHeadings);
			if (this!=Analyzer.getResultsTable())
				Analyzer.setResultsTable(this);
			if (size()>0)
				Analyzer.setUnsavedMeasurements(true);
		} else {
			Frame frame = WindowManager.getFrame(windowTitle);
			TextWindow win;
			if (frame!=null && frame instanceof TextWindow) {
				win = (TextWindow)frame;
				if (win!=null) {
					win.toFront();
					WindowManager.setWindow(frame);
				}
			} else {
				int chars = Math.max(size()>0?getRowAsString(0).length():15, getColumnHeadings().length());
				int width = 100 + chars*10;
				if (width<180) width=180;
				if (width>700) width=700;
				if (showRowNumbers)
					width += 50;
				int height = 300;
				if (size()>15) height = 400;
				if (size()>30 && width>300) height = 500;
				String wtitle = windowTitle + (isResultsTable&&showRowNumbers?"(Results)":"");
				win = new TextWindow(wtitle, "", width, height);
				cloneNeeded = true;
			}
			tp = win.getTextPanel();
			tp.setColumnHeadings(tableHeadings);
			newWindow = tp.getLineCount()==0;
		}
		tp.setResultsTable(cloneNeeded?(ResultsTable)this.clone():this);
		int n = size();
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
				if (NaNEmptyCells)
					Arrays.fill(columns[i], Double.NaN);
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
		if (size()>0) show("Results");
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
		if (path==null || path.equals("")) {
			OpenDialog od = new OpenDialog("Open Table", "");
			String dir = od.getDirectory();
			String name = od.getFileName();
			if (name==null)
				return null;
			path = dir+name;
		}
		String text = IJ.openAsString(path);
		if (text==null)
			return null;
		if (text.length()==0)
			return new ResultsTable();
		if (text.startsWith("Error:"))
			throw new IOException("Error opening "+path);
		boolean csv = path.endsWith(".csv") || path.endsWith(".CSV");
		String cellSeparator =  csv?",":"\t";
		boolean commasReplaced = false;
		if (csv && text.contains("\"")) {
			text = replaceQuotedCommas(text);
			commasReplaced = true;
		}
		String commaSubstitute2 = ""+commaSubstitute;
		String[] lines = text.split(lineSeparator);
		if (lines.length==0 || (lines.length==1 && lines[0].length()==0))
			throw new IOException("Table is empty or invalid");
		String[] headings = lines[0].split(cellSeparator);
		if (headings.length<1)
			throw new IOException("This is not a tab or comma delimited text file.");
		String zeroWidthSpace = "\uFEFF";
		if (headings[0].startsWith(zeroWidthSpace))
			headings[0] = headings[0].substring(1, headings[0].length());
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
		int firstColumn = headings.length>0&&headings[0].equals(" ")?1:0;
		for (int i=0; i<headings.length; i++) {
			headings[i] = headings[i].trim();
			if (commasReplaced) {
				if (headings[i].startsWith("\"") && headings[i].endsWith("\""))
					headings[i] = headings[i].substring(1, headings[i].length()-1);
			}
		}
		int firstRow = allNumericHeadings?0:1;
		boolean labels = firstColumn==1 && headings[1].equals("Label");
		int type=getTableType(path, lines, firstRow, cellSeparator);
		//if (!labels && (type==1||type==2))
		//	labels = true;
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
		if (firstRow>=lines.length) { //empty table?
			for (int i=0; i<headings.length; i++) {
				if (headings[i]==null) continue;
				int col = rt.getColumnIndex(headings[i]);
				if (col==COLUMN_NOT_FOUND)
					col = rt.getFreeColumn(headings[i]);
			}
			return rt;
		}
		rt.showRowNumbers(path.contains("Results"));
		for (int i=firstRow; i<lines.length; i++) {
			rt.incrementCounter();
			String[] items = lines[i].split(cellSeparator);
			for (int j=firstColumn; j<headings.length; j++) {
				if (j==labelsIndex&&labels)
					rt.addLabel(headings[labelsIndex], items[labelsIndex]);
				else {
					double defaultValue = -Double.MAX_VALUE;
					double value = j<items.length?Tools.parseDouble(items[j], defaultValue):Double.NaN;
					if (value==defaultValue) {
						String item = j<items.length?items[j]:"";
						if (commasReplaced) {
							item = item.replaceAll(commaSubstitute2, ",");
							if (item.startsWith("\"") && item.endsWith("\""))
								item = item.substring(1, item.length()-1);
						}
						rt.addValue(headings[j], item);
					} else
						rt.addValue(headings[j], value);
				}
			}
		}
		return rt;
	}
	
	private static int getTableType(String path, String[] lines, int firstRow, String cellSeparator) {
		if (lines.length<2) return 0;
		String[] items=lines[1].split(cellSeparator);
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
	
	private static String replaceQuotedCommas(String text) {
		char[] c = text.toCharArray();
		boolean inQuotes = false;
		for (int i=0; i<c.length; i++) {
			if (c[i]=='"')
				inQuotes = !inQuotes;
			if (inQuotes && c[i]==',')
				c[i] = commaSubstitute;
		}
		return new String(c);
	}
	
	/** Saves this ResultsTable as a tab or comma delimited text file. The table
	     is saved as a CSV (comma-separated values) file if 'path' ends with ".csv".
	     Displays a file save dialog if 'path' is empty or null. Does nothing if the
	     table is empty. Displays an error message and returns 'false' if there is
	     an error. */
	public boolean save(String path) {
		try {
			saveAs(path);
			return true;
		} catch (IOException e) {
			delimiter = '\t';
			IJ.error("Save As>Results", ""+"Error saving results:\n   "+e.getMessage());
			return false;
		}
	}

	public boolean saveAndRename(String path) {
		if (title!=null && !title.equals("Results"))
			renameWhenSaving = true;
		boolean ok = save(path);
		renameWhenSaving = false;
		return ok;
	}

	public void saveAs(String path) throws IOException {
		boolean emptyTable = size()==0 && lastColumn<0;
		if (path==null || path.equals("")) {
			SaveDialog sd = new SaveDialog("Save Table", "Table", Prefs.defaultResultsExtension());
			String file = sd.getFileName();
			if (file==null)
				return;
			path = sd.getDirectory() + file;
		}
		boolean csv = path.endsWith(".csv") || path.endsWith(".CSV");
		delimiter = csv?',':'\t';
		PrintWriter pw = null;
		FileOutputStream fos = new FileOutputStream(path);
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		pw = new PrintWriter(bos);
		boolean saveShowRowNumbers = showRowNumbers;
		if (Prefs.dontSaveRowNumbers)	
			showRowNumbers = false;
		if (saveColumnHeaders && !emptyTable) {
			String headings = getColumnHeadings();
			pw.println(headings);
		}
		quoteCommas = csv?true:false;
		for (int i=0; i<size(); i++)
			pw.println(getRowAsString(i));
		quoteCommas = false;
		showRowNumbers = saveShowRowNumbers;
		pw.close();
		delimiter = '\t';
		if (renameWhenSaving) {
			File f = new File(path);
			title =  f.getName();
		}
	}
	
	/** Returns the default headings ("Area","Mean","StdDev", etc.). */
	public static String[] getDefaultHeadings() {
		return defaultHeadings;
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
			rt2.isResultsTable = isResultsTable;
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
		return ("title="+title+", size="+counter+", hdr="+getColumnHeadings());
	}
	
	/** Applies a macro to each row of the table; the columns are assigned variable names
	 *  as given by getHeadingsAsVaribleNames(). New variables starting with an uppercase letter
	 *  create a new column with this name.
	 *  The variable 'row' (the row index) is pre-defined.
	 *  Except for the row label (if existing), currently only supports numeric values, no Strings.
	 *  @return false in case of a macro error */
	public boolean applyMacro(String macro) {
		String[] columnHeadings = getHeadings();
		String[] columnNames = getHeadingsAsVariableNames(columnHeadings); // same as variable names
		int[] columnIndices = new int[columnHeadings.length]; // corresponding column index; <0 for rowLabels
		for (int i=0; i<columnHeadings.length; i++)
			columnIndices[i] = getColumnIndex(columnHeadings[i]);

		Program pgm = (new Tokenizer()).tokenize(macro);
		StringBuilder sb = new StringBuilder(1000);
		sb.append("var ");
		for (int i=0; i<columnNames.length; i++) {  // create 'var' statement with 'real' data values, so errors are less likely
			sb.append(columnNames[i]);
			sb.append('=');
			if (columnIndices[i] < 0)
				sb.append(rowLabels[0]==null ? "\"\"" : '"'+rowLabels[0]+'"');
			else
				sb.append(Math.abs(getValueAsDouble(columnIndices[i], 0))); //avoid negative values since minus would be extra token
			sb.append(',');
		}
		sb.append("row;\n");
		sb.append("function dummy() {}\n");
		sb.append(macro);
		sb.append(";\n");
		String code = sb.toString();
		int PCStart = 9+4*columnNames.length;       // 'macro' code starts at this token number
		Interpreter interp = new Interpreter();
		interp.setApplyMacroTable(this);
		try {
			interp.run(code, null);  // first test run
		} catch(Exception e) {}
		if (interp.wasError())
			return false;

		boolean[] columnInUse = new boolean[columnNames.length];
		ArrayList<String> newColumnList = new ArrayList<String>();
		String[] variables = interp.getVariableNames();
		for (String variable:variables) {           // check for variables that make a new Column
			int columnNumber = indexOf(columnNames, variable);
			if (columnNumber >= 0)                  // variable is a know column
				columnInUse[columnNumber] = macro.indexOf(variable) >=0;
			else if (Character.isUpperCase(variable.charAt(0))) {
				getFreeColumn(variable);            // create new column
				newColumnList.add(variable);
			}
		}
		String[] newColumns = newColumnList.toArray(new String[0]);
		int[] newColumnIndices = new int[newColumns.length];
		for (int i=0; i<newColumns.length; i++)
		    newColumnIndices[i] = getColumnIndex(newColumns[i]);

		for (int row=0; row<counter; row++) {       // apply macro to each row
			for (int col=0; col<columnHeadings.length; col++) {
				if (columnInUse[col]) {             // set variable values for used columns
					if (columnIndices[col] < 0) {
						String str = rowLabels[row];
						interp.setVariable(columnNames[col], str);
					} else {
						double v = getValueAsDouble(columnIndices[col], row);
						interp.setVariable(columnNames[col], v);
					}
				}
			}
			interp.setVariable("row", row);
			interp.run(PCStart);
			if (interp.wasError())
				return false;
			for (int col=0; col<columnNames.length; col++) {
				if (columnInUse[col]) {             // set new values for previous columns
					if (columnIndices[col] < 0) {
						String str = interp.getVariableAsString(columnNames[col]);
						rowLabels[row] = str;
					} else {
						double v = interp.getVariable(columnNames[col]);
						setValue(columnIndices[col], row, v);
					}
				}
			}
			for (int i=0; i<newColumns.length; i++) {   // set new values for newly-created columns
				double v = interp.getVariable(newColumns[i]);
				setValue(newColumnIndices[i], row, v);
			}
		}
		return true;
	}
	
	/** Returns the first index of a given non-null String in a String array, or -1 if not found */
	private int indexOf(String[] sArray, String s) {
		for (int i=0; i<sArray.length; i++)
		    if (s.equals(sArray[i])) return i;
		return -1;
	}

	/** Returns the column headings; headings not suitable as variable names are converted
	 *  to valid variable names by replacing non-fitting characters with underscores and
	 *  adding underscores. To make unique names, underscores+numbers are added as required. */
	public String[] getHeadingsAsVariableNames() {
		return getHeadingsAsVariableNames(getHeadings());
	}

	/** Converts a list of column headings to a list of corresponding variable names */
	String[] getHeadingsAsVariableNames(String[] names) {
		names = (String[])names.clone();
		for (int i=0; i<names.length; i++) {
			if (names[i].charAt(0)>='0' && names[i].charAt(0)<='9') // variable must not start with digit
				names[i] = "_"+names[i];
			names[i] = names[i].replaceAll("[^A-Za-z0-9_]","_");    // replace unsuitable characters with underscores
			for (int postfix=0; ; postfix++) {
				boolean isDuplicate = false;
				for (int j=0; j<i; j++) {
					if (names[i].equals(names[j])) {                // check for duplicates
						isDuplicate = true;
						break;
					}
				}
				if (!isDuplicate) break;
				if (postfix > 0)                                    // remove trailing underscore+postfix
					names[i] = names[i].substring(0, names[i].lastIndexOf('_'));
				names[i] += "_"+postfix;                            // add underscore+postfix number
			}
		}
		return names;
	}
	
	public String getTitle() {
		if (title==null && this==Analyzer.getResultsTable())
			title = "Results";
		return title;
	}
	
	public boolean columnDeleted() {
		return columnDeleted;
	}
	
	/** Selects the row in the "Results" table assocuiated with the specified Roi.
		The row number is obtained from the roi name..
	*/
	public static boolean selectRow(Roi roi) {
		if (roi==null)
			return false;	
		String name = roi.getName();
		if (name==null || name.length()>8)
			return false ;
		Frame frame = WindowManager.getFrame("Results");
		if (frame==null)
			return false;
		if (!(frame instanceof TextWindow))
			return false ;
		ResultsTable rt = ((TextWindow)frame).getResultsTable();
		if (rt==null || rt!=Analyzer.getResultsTable())
			return false ;
		double n = Tools.parseDouble(name);
		if (Double.isNaN(n))
			return false;
		int index = (int)n - 1;
		if (index<0 || index>=rt.size())
			return false;
		((TextWindow)frame).getTextPanel().setSelection(index, index);
    	return true;	
    }
    	
	/** Sorts this table on the specified column, with string support.
	 * Author: 'mountain_man', 8 April 2019
	*/
	public void sort(String column) {
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND)
			throw new IllegalArgumentException("Column not found");

		// pad short string columns with "NaN" to avoid "holes" after sorting
		if (stringColumns!=null) {
		    for (Object c : stringColumns.values()) {
			ArrayList sc = (ArrayList) c;
		        for (int i = sc.size(); i < size(); i++)  sc.add ("NaN");
		    }
		}
		
		ComparableEntry[] ces = new ComparableEntry[size()];
		ArrayList stringColumn = null;
		if (stringColumns!=null)
		    stringColumn = (ArrayList) stringColumns.get (Integer.valueOf(col));
		for (int i = 0; i < size(); i++) {
		    ComparableEntry ce = new ComparableEntry();
		    ce.index = i;
		    ce.dValue = columns[col][i];
		    if (stringColumn != null)
			ce.sValue = (String) stringColumn.get (i);
		    ces[i] = ce;
		}
		Arrays.sort(ces);
		// copy sorted values back into rt from a duplicate
		ResultsTable rt2 = (ResultsTable)clone();
		for (int i = 0; i <= getLastColumn(); i++) {
			if (columns[i]==null)
				continue;
		    for (int j = 0; j < size(); j++)
				columns[i][j] = rt2.columns[i][ces[j].index];
		    ArrayList sc = null;
		    Map scs =  stringColumns;
		    	if (scs != null)
			sc = (ArrayList) scs.get (Integer.valueOf(i));
		    if (sc != null) {
				ArrayList sc2 = (ArrayList) rt2.stringColumns.get (Integer.valueOf(i));
				for (int j = 0; j < size(); j++)
			    	sc.set (j, sc2.get (ces[j].index));
		    }
		}
		if (rowLabels != null) {
			for (int i = 0; i < size(); i++)
				rowLabels[i] =  rt2.rowLabels[ces[i].index];
		}
	}
	
	class ComparableEntry implements Comparable<ComparableEntry>  {
		int index;
		double dValue;
		String sValue;
		
		boolean isStr() {
			return  Double.isNaN (dValue)  &&  sValue != null  &&  !sValue.equals ("NaN");
		}
		
		public int compareTo (ComparableEntry e) {
			if (isStr() && e.isStr())
				return sValue.compareTo (e.sValue);
			if (isStr())
				return -1;
			if (e.isStr())
				return +1;
			return  (dValue < e.dValue) ? -1 : ( (dValue > e.dValue) ? 1 : 0 );
		}
	}
	
	public void setIsResultsTable(boolean isResultsTable) {
		this.isResultsTable = isResultsTable;
	}
		
}
