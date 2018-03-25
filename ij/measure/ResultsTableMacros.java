package ij.measure;
import ij.plugin.filter.Analyzer;
import ij.plugin.*;
import ij.*;
import ij.gui.*;
import ij.text.*;
import java.awt.*;
import java.awt.event.*;


/** This class implements the Apply Macro command in tables.
* @author Michael Schmid
*/
public class ResultsTableMacros implements Runnable, ActionListener, KeyListener {
	private static String NAME = "TableMacro.ijm";
	private String defaultMacro = "Sin=sin(rowNumber*0.1);\nCos=cos(rowNumber*0.1);\nSqr=Sin*Sin+Cos*Cos";
	private GenericDialog gd;
	private ResultsTable rt, rt2;
	private Button insertButton, runButton, undoButton;
	private String title;
	
	public ResultsTableMacros(ResultsTable rt) {
		this.rt = rt;
		title = rt!=null?rt.getTitle():null;
		Thread thread = new Thread(this, "ResultTableMacros");
		thread.start();
	}
	
	private void showDialog() {
		if (rt==null)
			rt = Analyzer.getResultsTable();
		if (rt==null || rt.size()==0) {
			IJ.error("Results Table required");
			return;
		}
		ResultsTable rtBackup = (ResultsTable)rt.clone();
		String[] variableNames = rt.getHeadingsAsVariableNames();
		gd = new GenericDialog("Apply Macro to \""+title+"\"");
		gd.addTextAreas(getMacro(), null, 12, 45);
		gd.getTextArea1().addKeyListener(this);
		gd.addChoice("Variables:", variableNames, variableNames[0]);
		insertButton = new Button("Insert");
		insertButton.addActionListener(this);
		undoButton = new Button("Undo");
		undoButton.addActionListener(this);
		Panel panel = new Panel();
		panel.add(insertButton);
		panel.add(undoButton);
		gd.addToSameRow();
		gd.addPanel(panel);

		runButton = new Button("Run");
		runButton.addActionListener(this);
		panel = new Panel(new FlowLayout(FlowLayout.RIGHT));
		panel.add(runButton);
		gd.addPanel(panel);
		gd.addToSameRow();

		gd.addHelp("<html><body><h1>Macro Equations for Results Tables</h1><ul>"+
				"<li>A new variable starting with an Uppercase character creates a new column.<"+
				"<li>A new variable starting with a lowercase character is temporary."+
				"<li>Also <tt>rowNumber</tt> is defined as variable.\n"+
				"<li>String operations are supported for the 'Label' column only (if enabled<br>"+
				"with Analyze&gt;Set Measurements&gt;Display Label)."+				
				"<li>Click \"<b>Run</b>\" to apply the macro code to the table."+
				"<li>Select a line and press "+(IJ.isMacOSX()?"cmd":"ctrl") + "-r to apply a line of macro code."+
				"<li>Click \"<b>Undo</b>\", or press "+(IJ.isMacOSX()?"cmd":"ctrl")+"-z, to undo the table changes."+
				"<li>The code is saved at <b>macros/TableMacro.ijm</b> when you click \"<b>Close</b>\"."+
				"</ul></body></html>");

		gd.setOKLabel("Close");
		gd.showDialog();
		if (gd.wasCanceled()) {  // dialog cancelled?
			rt = rtBackup;
			if (title!=null) rt.show(title);
			return;
		}
		IJ.saveString(gd.getTextArea1().getText(), IJ.getDir("macros")+NAME);
	 }

	private void applyMacro() {
		TextArea ta = gd.getTextArea1();
		int start = ta.getSelectionStart();
		int end = ta.getSelectionEnd();
		String macro  = start==end?ta.getText():ta.getSelectedText();
		rt.applyMacro(macro);
		if (title!=null) rt.show(title);
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source==runButton) {
		  rt2 = (ResultsTable)rt.clone();
		  applyMacro();
		} else if (source==insertButton) {
		  TextArea ta = gd.getTextArea1();
		  Choice choice = (Choice)(gd.getChoices().get(0));
		  String variableName = choice.getSelectedItem(); // getNextChoice would not work: does not reset counter
		  int pos = ta.getCaretPosition();
		  ta.insert(variableName, pos);
		  //ta.setCaretPosition(pos+variableName.length());
	   } else if (source==undoButton) {
		  if (rt2!=null) {
			 rt = rt2;
			 if (title!=null) rt.show(title);
			 rt2 = null;
		  }
	   }
	 }
	 
	public void keyPressed(KeyEvent e) { 
		int flags = e.getModifiers();
		boolean control = (flags & KeyEvent.CTRL_MASK) != 0;
		boolean meta = (flags & KeyEvent.META_MASK) != 0;
		int keyCode = e.getKeyCode();
		if (keyCode==KeyEvent.VK_R && (control||meta)) {
			rt2 = (ResultsTable)rt.clone();
			applyMacro();
		}
		if (keyCode==KeyEvent.VK_Z && (control||meta) && rt2!=null) {
			 rt = rt2;
			 if (title!=null) rt.show(title);
			 rt2 = null;
		}
	} 
	
	public void keyReleased(KeyEvent e) {
	}
	
	public void keyTyped(KeyEvent e) {
	}
	 
	private String getMacro() {
		String macro = IJ.openAsString(IJ.getDir("macros")+NAME);
		if (macro==null || macro.startsWith("Error:"))
			return defaultMacro;
		else
			return macro;
	}
	 
	 public void run() {
		showDialog();
 	}
	 
}
