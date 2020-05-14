package ij.gui;

import java.util.*;

import javax.swing.table.AbstractTableModel;
import javax.swing.*;
import java.awt.Panel;
import ij.gui.GenericDialog;


public class RoiGroupTableModel extends AbstractTableModel{
	
	static final String[] headers = {"Group number", "Name"};
	private Map<Integer, String> groupNames;
	private Vector<Vector> columns = new Vector(2); // 2 columns
	
	public RoiGroupTableModel() {
		super();
		this.groupNames = Roi.groupNames;
		//this.groupNames = new TreeMap<Integer, String>(groupNames); // sort the map by keys to makes sure keySet and values are ordered the same
		
		// Populate the column vectors
		int size = this.groupNames.size();
		Vector<Integer> columnGroups = new Vector<Integer>(size);
		Vector<String>  columnNames  = new Vector<String>(size);
		
		for (Map.Entry<Integer, String> entry : this.groupNames.entrySet()) {
			columnGroups.add(entry.getKey().intValue());
			columnNames.add(entry.getValue());
		}
		
		// Populate the 2D-data vector containing the 2 column vector
		this.columns.add(columnGroups);
		this.columns.add(columnNames);	
		}
	
	public Class<?> getColumnClass(int index){
		if (index==0) {return int.class;}
		else {return String.class;}
	}
	
	public int getRowCount() {return this.columns.get(0).size();}

	public int getColumnCount() {return 2;}

	public Object getValueAt(int row, int column) {
			return this.columns.get(column).get(row);
	}
			
	public Vector getColumn(int column) {
			return this.columns.get(column);
	}
	
	public String getColumnName(int column) {return RoiGroupTableModel.headers[column];}
    	
	public boolean isCellEditable(int row, int col) { return true; } // does not work for integer column

    public void setValueAt(Object value, int row, int column) {
    	this.columns.get(column).set(row, value);
		fireTableCellUpdated(row, column);
    }
    
    public void addRow(int group, String name) {
    	this.columns.get(0).add(group);
    	this.columns.get(1).add(name);
    	int n = this.getRowCount();
    	fireTableRowsInserted(n-1, n-1);
    }
    
    public void deleteRow(int row) {
    	this.columns.get(0).removeElementAt(row);
    	this.columns.get(1).removeElementAt(row);
    	fireTableRowsDeleted(row, row);
    }
    
    public void deleteRows(int first, int last) {
    	
    	//this.columns.get(0).removeRange(first, last); //removeRange is protected ><
    }
    
}