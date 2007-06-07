package ij.measure;
import ij.IJ;

/** This is a table for storing measurement results as columns of real numbers. */
public class ResultsTable {

	public static final int MAX_COLUMNS = 50;
	
	public static final int COLUMN_NOT_FOUND = -1;
	public static final int COLUMN_IN_USE = -2;
	public static final int TABLE_FULL = -3;
	
	public static final int AREA=0, MEAN=1, STD_DEV=2, MODE=3, MIN=4, MAX=5,
		X_CENTROID=6, Y_CENTROID=7, X_CENTER_OF_MASS=8, Y_CENTER_OF_MASS=9,
		PERIMETER=10, ROI_X=11, ROI_Y=12, ROI_WIDTH=13, ROI_HEIGHT=14,
		MAJOR=15, MINOR=16, ANGLE=17; 

	private String[] headings = new String[MAX_COLUMNS];
	private String[] defaultHeadings = {"Area","Mean","StdDev","Mode","Min","Max",
		"X","Y","XM","YM","Perim.","BX","BY","Width","Height","Major","Minor","Angle"};
	private int counter;
	private float[][] columns = new float[MAX_COLUMNS][];
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
	
	/** Increments the measurement counter by one. */
	public synchronized void incrementCounter() {
		counter++;
		if (counter==maxRows) {
			if (rowLabels!=null) {
				String[] s = new String[maxRows*2];
				System.arraycopy(rowLabels, 0, s, 0, maxRows);
				rowLabels = s;
			}
			for (int i=0; i<MAX_COLUMNS; i++) {
				if (columns[i]!=null) {
					float[] tmp = new float[maxRows*2];
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
			columns[column] = new float[maxRows];
			if (headings[column]==null)
				headings[column] = "---";
			if (column>lastColumn) lastColumn = column;
		}
		columns[column][counter-1] = (float)value;
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
	public void addLabel(String heading, String label) {
		if (counter==0)
			throw new IllegalArgumentException("Counter==0");
		if (rowLabels==null)
			rowLabels = new String[maxRows];
		rowLabels[counter-1] = label;
		if (heading!=null)
			rowLabelHeading = heading;
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
				data[i] = columns[column][i];
			return data;
		}
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
				columns[i] = new float[maxRows];
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
	public float getValue(int column, int row) {
		if (columns[column]==null)
			throw new IllegalArgumentException("Column not defined: "+column);
		if (column>=MAX_COLUMNS || row>=counter)
			throw new IllegalArgumentException("Index out of range: "+column+","+row);
		return columns[column][row];
	}
	
	/** Sets the value of the given column and row, where
		where 0<=column<MAX_COLUMNS and 0<=row<counter. */
	public void setValue(int column, int row, double value) {
		if ((column<0) || (column>=MAX_COLUMNS))
			throw new IllegalArgumentException("Column out of range: "+column);
		if (row>=counter)
			throw new IllegalArgumentException("row>=counter");
		if (columns[column]==null) {
			columns[column] = new float[maxRows];
			if (column>lastColumn) lastColumn = column;
		}
		columns[column][row] = (float)value;
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
		
	/** Clears all the columns and sets the counter to zero. */
	public synchronized void reset() {
		counter = 0;
		maxRows = 100;
		for (int i=0; i<=lastColumn; i++) {
			columns[i] = null;
			if (i<defaultHeadings.length)
				headings[i] = defaultHeadings[i];
		}
		lastColumn = -1;
	}
	
	public String toString() {
		return ("ctr="+counter+", hdr="+getColumnHeadings());
	}
	
}
