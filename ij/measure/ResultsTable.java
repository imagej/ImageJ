package ij.measure;
import ij.*;
import ij.plugin.filter.Analyzer;
import ij.text.*;
import java.awt.*;

/** This is a table for storing measurement results as columns of numeric values. 
	Call the static ResultsTable.getResultsTable() method to get a reference to the 
	ResultsTable used by the <i>Analyze/Measure</i> command. 
	@see ij.plugin.filter.Analyzer#getResultsTable
*/
public class ResultsTable {

	public static final int MAX_COLUMNS = 150;
	
	public static final int COLUMN_NOT_FOUND = -1;
	public static final int COLUMN_IN_USE = -2;
	public static final int TABLE_FULL = -3;
	
	public static final int AREA=0, MEAN=1, STD_DEV=2, MODE=3, MIN=4, MAX=5,
		X_CENTROID=6, Y_CENTROID=7, X_CENTER_OF_MASS=8, Y_CENTER_OF_MASS=9,
		PERIMETER=10, ROI_X=11, ROI_Y=12, ROI_WIDTH=13, ROI_HEIGHT=14,
		MAJOR=15, MINOR=16, ANGLE=17, CIRCULARITY=18, FERET=19, INTEGRATED_DENSITY=20,
		MEDIAN=21, SKEWNESS=22, KURTOSIS=23, AREA_FRACTION=24, SLICE=25;

	private String[] headings = new String[MAX_COLUMNS];
	private String[] defaultHeadings = {"Area","Mean","StdDev","Mode","Min","Max",
		"X","Y","XM","YM","Perim.","BX","BY","Width","Height","Major","Minor","Angle",
		"Circ.", "Feret", "IntDen", "Median","Skew","Kurt", "%Area", "Slice"};
	private int counter;
	private double[][] columns = new double[MAX_COLUMNS][];
	private String[] rowLabels;
	private int maxRows = 100; // will be increased as needed
	private int lastColumn = -1;
	private	StringBuffer sb;
	private int precision = 3;
	private String rowLabelHeading = "";

	/** Constructs an empty ResultsTable with the counter=0 and no columns. */
	public ResultsTable() {
		for(int i=0; i<defaultHeadings.length; i++)
				headings[i] = defaultHeadings[i];
	}
	
	/** Returns the ResultsTable used by the Measure command. */
	public static ResultsTable getResultsTable() {
		return Analyzer.getResultsTable();
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
	
	/** Returns the current value of the measurement counter. */
	public int getCounter() {
		return counter;
	}
	
	/** Adds a value to the end of the given column. Counter must be >0.*/
	public void addValue(int column, double value) {
		if ((column<0) || (column>=MAX_COLUMNS))
			throw new IllegalArgumentException("Index out of range: "+column);
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
		does not exist, it is created.  Counter must be >0. */
	public void addValue(String column, double value) {
		int index = getColumnIndex(column);
		if (index==COLUMN_NOT_FOUND) {
			index = getFreeColumn(column);
			if (index==TABLE_FULL)
				throw new IllegalArgumentException("table is full");
		}
		addValue(index, value);
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
	
	/** Set the row label column to null. */
	public void disableRowLabels() {
		rowLabels = null;
	}
	
	/** Returns a copy of the given column as a float array.
		Returns null if the column is empty. */
	public float[] getColumn(int column) {
		if ((column<0) || (column>=MAX_COLUMNS))
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
	
	/** Returns true if the specified column exists and is not empty. */
	public boolean columnExists(int column) {
		if ((column<0) || (column>=MAX_COLUMNS))
			return false;
		else
			return columns[column]!=null;
	}

	/** Returns the index of the first column with the given heading.
		heading. If not found, returns COLUMN_NOT_FOUND. */
	public int getColumnIndex(String heading) {
		for(int i=0; i<headings.length; i++) {
			if (headings[i]==null)
				return COLUMN_NOT_FOUND;
			else if (headings[i].equals(heading))
				return i;
		}
		return COLUMN_NOT_FOUND;
	}
	
	/** Sets the heading of the the first available column and
		returns that column's index. Returns COLUMN_IN_USE if this
		is a duplicate heading. Returns TABLE_FULL if there
		are no free columns. */
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
		return TABLE_FULL;
	}
	
	/**	Returns the value of the given column and row, where
		column must be greater than or equal zero and less than
		MAX_COLUMNS and row must be greater than or equal zero
		and less than counter. */
	public double getValueAsDouble(int column, int row) {
		if (columns[column]==null)
			throw new IllegalArgumentException("Column not defined: "+column);
		if (column>=MAX_COLUMNS || row>=counter)
			throw new IllegalArgumentException("Index out of range: "+column+","+row);
		return columns[column][row];
	}
	
	/**	Obsolete, replaced by getValueAsDouble. */
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
		int col = getColumnIndex(column);
		if (col==COLUMN_NOT_FOUND) {
			col = getFreeColumn(column);
			if (col==TABLE_FULL)
				throw new IllegalArgumentException("Too many columns (>"+(MAX_COLUMNS-defaultHeadings.length)+")");
		}
		setValue(col, row, value);
	}

	/** Sets the value of the given column and row, where
		where 0&lt;=column&lt;MAX_COLUMNS and 0<=row<counter. */
	public void setValue(int column, int row, double value) {
		if ((column<0) || (column>=MAX_COLUMNS))
			throw new IllegalArgumentException("Column out of range: "+column);
		if (row>=counter)
			throw new IllegalArgumentException("row>=counter");
		if (columns[column]==null) {
			columns[column] = new double[maxRows];
			if (column>lastColumn) lastColumn = column;
		}
		columns[column][row] = value;
	}

	/** Returns a tab-delimited string containing the column headings. */
	public String getColumnHeadings() {
		StringBuffer sb = new StringBuffer(200);
		sb.append(" \t");
		if (rowLabels!=null)
			sb.append(rowLabelHeading + "\t");
		String heading;
		for (int i=0; i<=lastColumn; i++) {
			if (columns[i]!=null) {
				heading = headings[i];
				if (heading==null) heading ="---"; 
				sb.append(heading + "\t");
			}
		}
		return new String(sb);
	}

	/** Returns the heading of the specified column or null if the column is empty. */
	public String getColumnHeading(int column) {
		if ((column<0) || (column>=MAX_COLUMNS))
			throw new IllegalArgumentException("Index out of range: "+column);
		return headings[column];
	}

	/** Returns a tab-delimited string representing the
		given row, where 0<=row<=counter-1. */
	public String getRowAsString(int row) {
		if ((row<0) || (row>=counter))
			throw new IllegalArgumentException("Row out of range: "+row);
		if (sb==null)
			sb = new StringBuffer(200);
		else
			sb.setLength(0);
		sb.append(Integer.toString(row+1));
		sb.append("\t");
		if (rowLabels!=null) {
			if (rowLabels[row]!=null)
				sb.append(rowLabels[row]);
			sb.append("\t");
		}
		for (int i=0; i<=lastColumn; i++) {
			if (columns[i]!=null)
				sb.append(n(columns[i][row]));
		}
		return new String(sb);
	}
	
	/** Changes the heading of the given column. */
	public void setHeading(int column, String heading) {
		if ((column<0) || (column>=headings.length))
			throw new IllegalArgumentException("Column out of range: "+column);
		headings[column] = heading;
	}
	
	/** Sets the number of digits to the right of decimal point. */
	public void setPrecision(int precision) {
		this.precision = precision;
	}
	
	String n(double n) {
		String s;
		if (Math.round(n)==n)
			s = IJ.d2s(n,0);
		else
			s = IJ.d2s(n,precision);
		return s+"\t";
	}
		
	/** Deletes the specified row. */
	public synchronized void deleteRow(int row) {
		if (counter==0 || row>counter-1) return;
		if (counter==1)
			{reset(); return;}
		if (rowLabels!=null) {
			for (int i=row; i<counter-1; i++)
				rowLabels[i] = rowLabels[i+1];
		}
		for (int i=0; i<=lastColumn; i++) {
			if (columns[i]!=null) {
				for (int j=row; j<counter-1; j++)
					columns[i][j] = columns[i][j+1];
			}
		}
		counter--;
	}
	
	/** Clears all the columns and sets the counter to zero. */
	public synchronized void reset() {
		counter = 0;
		maxRows = 100;
		for (int i=0; i<=lastColumn; i++) {
			columns[i] = null;
			if (i<defaultHeadings.length)
				headings[i] = defaultHeadings[i];
			else
				headings[i] = null;
		}
		lastColumn = -1;
		rowLabels = null;
	}

	/** Displays the contents of this ResultsTable in a window with the specified title. 
		Opens a new window if there is no open text window with this title. */
	public void show(String windowTitle) {
		String tableHeadings = getColumnHeadings();		
		TextPanel tp;
		if (windowTitle.equals("Results")) {
			tp = IJ.getTextPanel();
			if (tp==null) return;
			IJ.setColumnHeadings(tableHeadings);
		} else {
			Frame frame = WindowManager.getFrame(windowTitle);
			TextWindow win;
			if (frame!=null && frame instanceof TextWindow)
				win = (TextWindow)frame;
			else
				win = new TextWindow(windowTitle, "", 300, 200);
			tp = win.getTextPanel();
			tp.setColumnHeadings(tableHeadings);
		}
		int n = getCounter();
		if (n>0) {
			StringBuffer sb = new StringBuffer(n*tableHeadings.length());
			for (int i=0; i<n; i++)
				sb.append(getRowAsString(i)+"\n");
			tp.append(new String(sb));
		}
	}
	
	public String toString() {
		return ("ctr="+counter+", hdr="+getColumnHeadings());
	}
	
}
