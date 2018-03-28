package ij.measure;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.Recorder;
import ij.plugin.*;
import ij.*;
import ij.gui.*;
import ij.text.*;
import java.awt.*;
import java.awt.event.*;


/** This class implements the Apply Macro command in tables.
* @author Michael Schmid
*/
public class ResultsTableMacros implements Runnable, DialogListener, ActionListener, KeyListener {
	private static String NAME = "TableMacro.ijm";
	private String defaultMacro = "Sin=sin(rowIndex*0.1);\nCos=cos(rowIndex*0.1);\nSqr=Sin*Sin+Cos*Cos;";
	private GenericDialog gd;
	private ResultsTable rt, rtBackup;
	private Button runButton, resetButton, openButton, saveButton;
	private String title;
	private int runCount;
	private TextArea ta;

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
		String[] temp = rt.getHeadingsAsVariableNames();
		String[] variableNames = new String[temp.length+2];
		variableNames[0] = "Insert...";
		variableNames[1] = "rowIndex";
		for (int i=2; i<variableNames.length; i++)
			variableNames[i] = temp[i-2];
		String dialogTitle = "Apply Macro to "+(title!=null?"\""+title+"\"":"Table");
		Frame parent = title!=null?WindowManager.getFrame(title):null;
		if (parent!=null)
			gd = new GenericDialog(dialogTitle, parent);
		else
			gd = new GenericDialog(dialogTitle);
		gd.setInsets(5, 5, 0);
		gd.addTextAreas(getMacro(), null, 12, 48);
		ta = gd.getTextArea1();
		ta.addKeyListener(this);

		Panel panel = new Panel();
		if (IJ.isMacOSX())
			panel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
		runButton = new Button("Run");
		runButton.addActionListener(this);
		panel.add(runButton);
		resetButton = new Button("Reset");
		resetButton.addActionListener(this);
		panel.add(resetButton);
		openButton = new Button("Open");
		openButton.addActionListener(this);
		panel.add(openButton);
		saveButton = new Button("Save");
		saveButton.addActionListener(this);
		panel.add(saveButton);
		gd.addPanel(panel);
		gd.addToSameRow();
		gd.addChoice("", variableNames, variableNames[0]);


		gd.addHelp("<html><body><h1>Macro Equations for Results Tables</h1><ul>"+
				"<li>The macro, or a selection, is applied to each row of the table."+
				"<li>A new variable starting with an Uppercase character creates<br>a new column."+
				"<li>A new variable starting with a lowercase character is temporary."+
				"<li>The variable <b>rowIndex</b> is pre-defined.\n"+
				"<li>String operations are supported for the 'Label' column only (if<br>enabled"+
				"with Analyze&gt;Set Measurements&gt;Display Label)."+				
				"<li>Click \"<b>Run</b>\" to apply the macro code to the table."+
				"<li>Select a line and press "+(IJ.isMacOSX()?"cmd":"ctrl") + "-r to apply a line of macro code."+
				"<li>Click \"<b>Reset</b>\" to revert to the original version of the table."+
				"<li>The code is saved at <b>macros/TableMacro.ijm</b>, and the<br>\"Apply Macro\" command is recorded, when you click \"<b>OK</b>\"."+
				"</ul></body></html>");

		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {  // dialog cancelled?
			rt = rtBackup;
			updateDisplay();
			return;
		}
		if (runCount==0)
			applyMacro();
		if (Recorder.record) {
			String macro = getMacroCode();
			macro = macro.replaceAll("\n", " ");
			if (Recorder.scriptMode()) {
				Recorder.recordCall("title = \""+title+"\";");
				Recorder.recordCall("frame = WindowManager.getFrame(title);");
				Recorder.recordCall("rt = frame.getResultsTable();");
				Recorder.recordCall("rt.applyMacro(\""+macro+"\");");
				Recorder.recordCall("rt.show(title);");
			} else {
				Recorder.record("Table.applyMacro", title, macro);
			}
		}
		IJ.saveString(ta.getText(), IJ.getDir("macros")+NAME);
	 }

	private void applyMacro() {
		String code = getMacroCode();
		rt.applyMacro(code);
		updateDisplay();
		runCount++;
	}

	private String getMacroCode() {
		int start = ta.getSelectionStart();
		int end = ta.getSelectionEnd();
		return start==end?ta.getText():ta.getSelectedText();
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		final String variableName = gd.getNextChoice();
		if (e!=null && (e.getSource() instanceof Choice) && !variableName.equals("Insert...")) {
			final int pos = ta.getCaretPosition();
			((Choice)e.getSource()).select(0);
			final TextArea textArea = ta;
			new Thread(new Runnable() {
					public void run() {
						IJ.wait(100);
						textArea.insert(variableName, pos);
						textArea.setCaretPosition(pos+variableName.length());
						textArea.requestFocus();
					}}).start();
		}
		return true;
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source==runButton) {
			applyMacro();
		} else if (source==resetButton) {
			rt = (ResultsTable)rtBackup.clone();
			updateDisplay();
		} else if (source==openButton) {
			String macro = IJ.openAsString(null);
			if (macro==null)
				return;
			if (macro.startsWith("Error: ")) {
				IJ.error(macro);
				return;
			} else
				ta.setText(macro);
		} else if (source==saveButton) {
			ta.selectAll();
			String macro = ta.getText();
			ta.select(0, 0);
			IJ.saveString(macro, null);
		}

	}

	public void keyPressed(KeyEvent e) {
		int flags = e.getModifiers();
		boolean control = (flags & KeyEvent.CTRL_MASK) != 0;
		boolean meta = (flags & KeyEvent.META_MASK) != 0;
		int keyCode = e.getKeyCode();
		if (keyCode==KeyEvent.VK_R && (control||meta))
			applyMacro();
		if (keyCode==KeyEvent.VK_Z && (control||meta)) {
			rt = (ResultsTable)rtBackup.clone();
			updateDisplay();
		}
	}

	private void updateDisplay() {
		if (title!=null)
			rt.show(title);
	}

	public void keyReleased(KeyEvent e) {
	}

	public void keyTyped(KeyEvent e) {
	}

	private String getMacro() {
		String macro = IJ.openAsString(IJ.getDir("macros")+NAME);
		if (macro==null || macro.startsWith("Error:"))
			return defaultMacro;
		else {
			macro = macro.replaceAll("rowNumber", "rowIndex");
			return macro;
		}
	}

	public void run() {
		rtBackup = (ResultsTable)rt.clone();
		showDialog();
 	}

}

