package ij.gui;
import java.awt.*;
import java.awt.event.*;
import ij.*;

/** This class is a customizable modal dialog box. */
public class GenericDialog extends Dialog implements ActionListener {
	static final int MAX_ITEMS = 20;
	private TextField[] numberField = new TextField[MAX_ITEMS];
	private double[] defaultValues = new double[MAX_ITEMS];
	private String[] defaultText = new String[MAX_ITEMS];
	private TextField[] stringField = new TextField[MAX_ITEMS];
	private Checkbox[] checkbox = new Checkbox[MAX_ITEMS];
	private Choice[] choice = new Choice[MAX_ITEMS];
	private Button cancel, okay;
	private TextArea textArea1,textArea2;
    private boolean wasCanceled;
    private int y;
    private int nfIndex;
    private int sfIndex;
    private int cbIndex;
    private int choiceIndex;
	private GridBagLayout grid;
	private GridBagConstraints c;
	private boolean firstNumericField = true;
	private boolean firstStringField = true;
	private boolean firstCheckbox = true;
	private boolean firstChoice = true;
	private boolean invalidNumber;
	private boolean firstPaint = true;

    /** Creates a new GenericDialog with the specified title. Uses the current image
    	window as the parent frame or the ImageJ frame if no image windows are open. */
	public GenericDialog(String title) {
		super(WindowManager.getCurrentImage()!=null?(Frame)WindowManager.getCurrentImage().getWindow():
			IJ.getInstance(), title, true);
		grid = new GridBagLayout();
		c = new GridBagConstraints();
		setLayout(grid);
	}

    /** Creates a new GenericDialog using the specified title and parent frame. */
    public GenericDialog(String title, Frame parent) {
		super(parent, title, true);
		grid = new GridBagLayout();
		c = new GridBagConstraints();
		setLayout(grid);
    }
    
	//void showFields(String id) {
	//	String s = id+": ";
	//	for (int i=0; i<MAX_ITEMS; i++)
	//		if (numberField[i]!=null)
	//			s += i+"='"+numberField[i].getText()+"' ";
	//	IJ.write(s);
	//}

	/** Adds a numeric field.
	* @param label			the label
	* @param defaultValue	value to be initially displayed
	* @param digits			number of digits to right of decimal point
	*/
   public void addNumericField(String label, double defaultValue, int digits) {
		Label theLabel = makeLabel(label);
		c.gridx = 0; c.gridy = y;
		c.anchor = GridBagConstraints.EAST;
		c.gridwidth = 1;
		if (firstNumericField)
			c.insets = new Insets(5, 0, 5, 0);
		else
			c.insets = new Insets(0, 0, 5, 0);
		grid.setConstraints(theLabel, c);
		add(theLabel);

		numberField[nfIndex] = new TextField(IJ.d2s(defaultValue, digits), 6);
		numberField[nfIndex].addActionListener(this);
		defaultValues[nfIndex] = defaultValue;
		defaultText[nfIndex] = numberField[nfIndex].getText();
		c.gridx = 1; c.gridy = y;
		c.anchor = GridBagConstraints.WEST;
		grid.setConstraints(numberField[nfIndex], c);
		numberField[nfIndex].setEditable(true);
		if (firstNumericField) numberField[nfIndex].selectAll();
		firstNumericField = false;
		add(numberField[nfIndex]);
		y++; nfIndex++;
    }
    
    private Label makeLabel(String label) {
    	if (IJ.isMacintosh())
    		label += " ";
		return new Label(label);
    }
    
	/** Adds an 8 column text field.
	* @param label			the label
	* @param defaultText		the text initially displayed
	*/
	public void addStringField(String label, String defaultText) {
		addStringField(label, defaultText, 8);
	}

	/** Adds a text field.
	* @param label			the label
	* @param defaultText		text initially displayed
	* @param columns			width of the text field
	*/
	public void addStringField(String label, String defaultText, int columns) {
		Label theLabel = makeLabel(label);
		c.gridx = 0; c.gridy = y;
		c.anchor = GridBagConstraints.EAST;
		c.gridwidth = 1;
		if (firstStringField)
			c.insets = new Insets(5, 0, 5, 0);
		else
			c.insets = new Insets(0, 0, 5, 0);
		grid.setConstraints(theLabel, c);
		add(theLabel);

		stringField[sfIndex] = new TextField(defaultText, columns);
		stringField[sfIndex].addActionListener(this);
		c.gridx = 1; c.gridy = y;
		c.anchor = GridBagConstraints.WEST;
		grid.setConstraints(stringField[sfIndex], c);
		stringField[sfIndex].setEditable(true);
		add(stringField[sfIndex]);
		y++; sfIndex++;
    }
    
	/** Adds a checkbox.
	* @param label			the label
	* @param defaultValue	the initial state
	*/
    public void addCheckbox(String label, boolean defaultValue) {
		checkbox[cbIndex] = new Checkbox(label);
		c.gridx = 0; c.gridy = y;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		if (firstCheckbox)
			c.insets = new Insets(15, 20, 0, 0);
		else
			c.insets = new Insets(0, 20, 0, 0);
		firstCheckbox = false;
		grid.setConstraints(checkbox[cbIndex], c);
		checkbox[cbIndex].setState(defaultValue);
		add(checkbox[cbIndex]);
		//ij.IJ.write("addCheckbox: "+ y+" "+cbIndex);
		y++; cbIndex++;
    }
    
	/** Adds a group of checkboxs using a grid layout.
	* @param rows			the number of rows
	* @param columns		the number of columns
	* @param labels			the labels
	* @param defaultValues	the initial states
	*/
    public void addCheckboxGroup(int rows, int columns, String[] labels, boolean[] defaultValues) {
    	Panel panel = new Panel();
    	panel.setLayout(new GridLayout(rows,columns,10,0));
    	int startCBIndex = cbIndex;
    	int i1 = 0;
    	int[] index = new int[labels.length];
    	for (int row=0; row<rows; row++) {
			for (int col=0; col<columns; col++) {
				int i2 = col*rows+row;
				if (i2>=labels.length)
					break;
				index[i1] = i2;
				checkbox[cbIndex] = new Checkbox(labels[i1]);
				checkbox[cbIndex].setState(defaultValues[i1]);
				cbIndex++;
 				i1++;
			}
		}
		for (int i=0; i<i1; i++)
			panel.add(checkbox[startCBIndex+index[i]]);
		c.gridx = 0; c.gridy = y;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(10, 0, 0, 0);
		grid.setConstraints(panel, c);
		add(panel);
		y++;
    }

    /** Adds a popup menu.
   * @param label	the label
   * @param items	the menu items
   * @param defaultItem	the menu item initially selected
   */
   public void addChoice(String label, String[] items, String defaultItem) {
		Label theLabel = makeLabel(label);
		c.gridx = 0; c.gridy = y;
		c.anchor = GridBagConstraints.EAST;
		c.gridwidth = 1;
		if (firstChoice)
			c.insets = new Insets(5, 0, 5, 0);
		else
			c.insets = new Insets(0, 0, 5, 0);
		grid.setConstraints(theLabel, c);
		add(theLabel);
		choice[choiceIndex] = new Choice();
		for (int i=0; i<items.length; i++)
			choice[choiceIndex].addItem(items[i]);
		choice[choiceIndex].select(defaultItem);
		c.gridx = 1; c.gridy = y;
		c.anchor = GridBagConstraints.WEST;
		grid.setConstraints(choice[choiceIndex], c);
		firstChoice = false;
		add(choice[choiceIndex]);
		y++; choiceIndex++;
    }
    
    /** Adds a message consisting of one or more lines of text. */
    public void addMessage(String text) {
    	Component theLabel;
    	if (text.indexOf('\n')>=0)
			theLabel = new MultiLineLabel(text);
		else
			theLabel = new Label(text);
		c.gridx = 0; c.gridy = y;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(text.equals("")?0:10, 20, 0, 0);
		grid.setConstraints(theLabel, c);
		add(theLabel);
		y++;
    }
    
	/** Adds one or two (side by side) text areas.
	* @param text1	initial contents of the first text area
	* @param text2	initial contents of the second text area or null
	* @param rows	the number of rows
	* @param rows	the number of columns
	*/
    public void addTextAreas(String text1, String text2, int rows, int columns) {
    	if (textArea1!=null)
    		return;
    	Panel panel = new Panel();
		textArea1 = new TextArea(text1,rows,columns,TextArea.SCROLLBARS_NONE);
		//textArea1.append(text1);
		panel.add(textArea1);
		if (text2!=null) {
			textArea2 = new TextArea(text2,rows,columns,TextArea.SCROLLBARS_NONE);
			//textArea2.append(text2);
			panel.add(textArea2);
		}
		c.gridx = 0; c.gridy = y;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(15, 20, 0, 0);
		grid.setConstraints(panel, c);
		add(panel);
		y++;
    }
    
	/** Returns true if the user clicks on "Cancel". */
    public boolean wasCanceled() {
    	return wasCanceled;
    }
    
	/** Returns the contents of the next numeric field. */
   public double getNextNumber() {
		Double d;
		if (numberField[nfIndex]==null)
			return -1.0;
		String theText = numberField[nfIndex].getText();
		String originalText = defaultText[nfIndex];
		double defaultValue = defaultValues[nfIndex];
		nfIndex++;
		if (theText.equals(originalText))
			return defaultValue;
		try {d = new Double(theText);}
		catch (NumberFormatException e){
			//numberField[nfIndex].setText(""+xScale);
			d = null;
		}
		if (d!=null)
			return(d.doubleValue());
		else {
			invalidNumber = true;
			return 0.0;
		}
    }
    
 	/** Returns true if one or more of the numeric fields contained an invalid number. */
   public boolean invalidNumber() {
    	boolean wasInvalid = invalidNumber;
    	invalidNumber = false;
    	return wasInvalid;
    }
    
  	/** Returns the contents of the next text field. */
   public String getNextString() {
		Double d;
		if (stringField[sfIndex]==null)
			return "";
		else
			return stringField[sfIndex++].getText();
    }
    
  	/** Returns the state of the next checkbox. */
    public boolean getNextBoolean() {
		if (checkbox[cbIndex]==null)
			return false;
		else
			return checkbox[cbIndex++].getState();
    }
    
  	/** Returns the selected item in the next popup menu. */
    public String getNextChoice() {
		if (choice[choiceIndex]==null)
			return "";
		else
			return choice[choiceIndex++].getSelectedItem();
    }
    
  	/** Returns the index of the selected item in the next popup menu. */
    public int getNextChoiceIndex() {
		if (choice[choiceIndex]==null)
			return -1;
		else
			return choice[choiceIndex++].getSelectedIndex();
    }
    
  	/** Returns the contents of the next text area. */
   public String getNextText() {
    	String text;
    	if (textArea1!=null) {
			textArea1.selectAll();
			text = textArea1.getText();
			textArea1 = null;
    	} else if (textArea2!=null) {
			textArea2.selectAll();
			text = textArea2.getText();
			textArea2 = null;
		} else
			text = null;
		return text;
    }

  	/** Displays this dialog box. */
    public void showDialog() {
    	if (stringField[0]!=null && numberField[0]==null)
    		stringField[0].selectAll();
		Panel buttons = new Panel();
    	buttons.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
		cancel = new Button("Cancel");
		cancel.addActionListener(this);
		buttons.add(cancel);
		okay = new Button("  OK  ");
		okay.addActionListener(this);
		buttons.add(okay);
		c.gridx = 0; c.gridy = y;
		c.anchor = GridBagConstraints.EAST;
		c.gridwidth = 2;
		c.insets = new Insets(20, 0, 0, 0);
		grid.setConstraints(buttons, c);
		add(buttons);
		nfIndex = 0;
		sfIndex = 0;
		cbIndex = 0;
		choiceIndex = 0;
		pack();
		GUI.center(this);
		setVisible(true);
		IJ.wait(250); // work around for Sun/WinNT bug
  }
    
	public void actionPerformed(ActionEvent e) {
		wasCanceled = (e.getSource()==cancel);
		setVisible(false);
		dispose();
	}

    public Insets getInsets() {
    	return new Insets(40, 20, 20, 20);
    }

    public void paint(Graphics g) {
    	super.paint(g);
      	if (firstPaint && numberField[0]!=null)
    		numberField[0].requestFocus();
    	firstPaint = false;
    }
    
}