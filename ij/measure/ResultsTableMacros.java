package ij.measure;
import ij.plugin.filter.Analyzer;
import ij.plugin.*;
import ij.*;
import ij.gui.*;
import ij.text.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;


/** This class implements the Analyze/Apply Macro... command.
* @author Michael Schmid
*/
public class ResultsTableMacros implements PlugIn, ActionListener {
	private static String macro = "Sin=sin(rowNumber*0.1);\nCos=cos(rowNumber*0.1);\nSqr=Sin*Sin+Cos*Cos";
	private GenericDialog gd;
	private ResultsTable rt, rt2;
	private Button insertButton, runButton, undoButton;
	
	public void run(String arg) {
		rt = Analyzer.getResultsTable();
		ResultsTable rtBackup = (ResultsTable)rt.clone();
		if (rt == null || rt.size()==0) {
			IJ.error("Results Table required");
			return;
		}
		String[] variableNames = rt.getHeadingsAsVariableNames();
		gd = new NonBlockingGenericDialog("Apply Macro to Results Table");
		gd.addTextAreas(macro, null, 10, 45);
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

		gd.addHelp("<html><body><h1>Macro equations for Results Tables</h1><ul>"+
				"<li>A new variable starting with an Uppercase character creates a new column.<"+
				"<li>A new variable starting with a lowercase character is temporary."+
				"<li>Also <tt>rowNumber</tt> is defined as variable.\n"+
				"<li>String operations are supported for the 'Label' column only (if enabled<br>with"+
				"Analyze&gt;Set Measurements&gt;Display Label)."+
				"</ul></body></html>");

		gd.setOKLabel("Close");
		gd.showDialog();
		if (gd.wasCanceled()) {						// dialog cancelled?
			Analyzer.setResultsTable(rtBackup);		// revert to backup of the ResultsTable
			rt.show("Results");
			return;
		}
	 }

	private void run() {
		macro = gd.getTextArea1().getText();
		rt.applyMacro(macro);
		rt.show("Results");
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source==runButton) {
		  rt2 = (ResultsTable)rt.clone();
		  run();
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
			 rt.show("Results");
			 rt2 = null;
		  }
	   }
	 }
	 
}
