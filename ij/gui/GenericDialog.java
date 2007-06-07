package ij.gui;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import ij.*;
import ij.plugin.frame.Recorder;

/** This class is a customizable modal dialog box. */
public class GenericDialog extends Dialog implements ActionListener,
TextListener, FocusListener, ItemListener, KeyListener {

	protected Vector defaultValues,defaultText,numberField,stringField,checkbox,choice;
	protected Component theLabel;
	protected TextArea textArea1,textArea2;
	private Button cancel, okay;
    private boolean wasCanceled;
    private int y;
    private int nfIndex, sfIndex, cbIndex, choiceIndex;
	private GridBagLayout grid;
	private GridBagConstraints c;
	private boolean firstNumericField=true;
	private boolean invalidNumber;
	private boolean firstPaint = true;
	private Hashtable labels;
	private boolean macro;
	private String macroOptions;

    /** Creates a new GenericDialog with the specified title. Uses the current image
    	image window as the parent frame or the ImageJ frame if no image windows
    	are open. Dialog parameters are recorded by ImageJ's command recorder but
    	this requires that the first word of each label be unique. */
	public GenericDialog(String title) {
		this(title, WindowManager.getCurrentImage()!=null?
			(Frame)WindowManager.getCurrentImage().getWindow():IJ.getInstance()!=null?IJ.getInstance():new Frame());
	}

    /** Creates a new GenericDialog using the specified title and parent frame. */
    public GenericDialog(String title, Frame parent) {
		super(parent==null?new Frame():parent, title, true);
		grid = new GridBagLayout();
		c = new GridBagConstraints();
		setLayout(grid);
		macroOptions = Macro.getOptions();
		macro = macroOptions!=null;
		addKeyListener(this);
    }
    
	//void showFields(String id) {
	//	String s = id+": ";
	//	for (int i=0; i<maxItems; i++)
	//		if (numberField[i]!=null)
	//			s += i+"='"+numberField[i].getText()+"' ";
	//	IJ.write(s);
	//}

	/** Adds a numeric field. The first word of the label must be
		unique or command recording will not work.
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
			c.insets = new Insets(5, 0, 3, 0);
		else
			c.insets = new Insets(0, 0, 3, 0);
		grid.setConstraints(theLabel, c);
		add(theLabel);

		if (numberField==null) {
			numberField = new Vector(5);
			defaultValues = new Vector(5);
			defaultText = new Vector(5);
		}
		TextField tf = new TextField(IJ.d2s(defaultValue, digits), 6);
		tf.addActionListener(this);
		tf.addTextListener(this);
		tf.addFocusListener(this);
		tf.addKeyListener(this);
		numberField.addElement(tf);
		defaultValues.addElement(new Double(defaultValue));
		defaultText.addElement(tf.getText());
		c.gridx = 1; c.gridy = y;
		c.anchor = GridBagConstraints.WEST;
		grid.setConstraints(tf, c);
		tf.setEditable(true);
		if (firstNumericField) tf.selectAll();
		firstNumericField = false;
		add(tf);
		if (Recorder.record || macro)
			saveLabel(tf, label);
		y++;
    }
    
    private Label makeLabel(String label) {
    	if (IJ.isMacintosh())
    		label += " ";
		return new Label(label);
    }
    
    private void saveLabel(Component component, String label) {
    	if (labels==null)
    		labels = new Hashtable();
		labels.put(component, label);
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
		if (stringField==null) {
			stringField = new Vector(4);
			c.insets = new Insets(5, 0, 5, 0);
		} else
			c.insets = new Insets(0, 0, 5, 0);
		grid.setConstraints(theLabel, c);
		add(theLabel);

		TextField tf = new TextField(defaultText, columns);
		tf.addActionListener(this);
		tf.addTextListener(this);
		tf.addFocusListener(this);
		tf.addKeyListener(this);
		c.gridx = 1; c.gridy = y;
		c.anchor = GridBagConstraints.WEST;
		grid.setConstraints(tf, c);
		tf.setEditable(true);
		add(tf);
		stringField.addElement(tf);
		if (Recorder.record || macro)
			saveLabel(tf, label);
		y++;
    }
    
	/** Adds a checkbox.
	* @param label			the label
	* @param defaultValue	the initial state
	*/
    public void addCheckbox(String label, boolean defaultValue) {
    	if (checkbox==null) {
    		checkbox = new Vector(4);
			c.insets = new Insets(15, 20, 0, 0);
    	} else
			c.insets = new Insets(0, 20, 0, 0);
		c.gridx = 0; c.gridy = y;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		Checkbox cb = new Checkbox(label);
		grid.setConstraints(cb, c);
		cb.setState(defaultValue);
		cb.addItemListener(this);
		cb.addKeyListener(this);
		add(cb);
		checkbox.addElement(cb);
		//ij.IJ.write("addCheckbox: "+ y+" "+cbIndex);
		if (Recorder.record || macro)
			saveLabel(cb, label);
		y++;
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
    	if (checkbox==null)
    		checkbox = new Vector(12);
    	for (int row=0; row<rows; row++) {
			for (int col=0; col<columns; col++) {
				int i2 = col*rows+row;
				if (i2>=labels.length)
					break;
				index[i1] = i2;
				Checkbox cb = new Checkbox(labels[i1]);
				checkbox.addElement(cb);
				cb.setState(defaultValues[i1]);
				if (Recorder.record || macro)
					saveLabel(cb, labels[i1]);
				panel.add(cb);
 				i1++;
			}
		}
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
		if (choice==null) {
			choice = new Vector(4);
			c.insets = new Insets(5, 0, 5, 0);
		} else
			c.insets = new Insets(0, 0, 5, 0);
		grid.setConstraints(theLabel, c);
		add(theLabel);
		Choice thisChoice = new Choice();
		thisChoice.addKeyListener(this);
		thisChoice.addItemListener(this);
		for (int i=0; i<items.length; i++)
			thisChoice.addItem(items[i]);
		thisChoice.select(defaultItem);
		c.gridx = 1; c.gridy = y;
		c.anchor = GridBagConstraints.WEST;
		grid.setConstraints(thisChoice, c);
		add(thisChoice);
		choice.addElement(thisChoice);
		if (Recorder.record || macro)
			saveLabel(thisChoice, label);
		y++;
    }
    
    /** Adds a message consisting of one or more lines of text. */
    public void addMessage(String text) {
    	if (text.indexOf('\n')>=0)
			theLabel = new MultiLineLabel(text);
		else
			theLabel = new Label(text);
		//theLabel.addKeyListener(this);
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
    	if (wasCanceled)
    		Macro.abort();
    	return wasCanceled;
    }
    
	/** Returns the contents of the next numeric field. */
   public double getNextNumber() {
		if (numberField==null)
			return -1.0;
		TextField tf = (TextField)numberField.elementAt(nfIndex);
		String theText = tf.getText();
		if (macro) {
			String label = (String)labels.get((Object)tf);
			theText = Macro.getValue(macroOptions, label, theText);
			//IJ.write("getNextNumber: "+label+"  "+theText);
		}	
		String originalText = (String)defaultText.elementAt(nfIndex);
		double defaultValue = ((Double)(defaultValues.elementAt(nfIndex))).doubleValue();
		double value;
		if (theText.equals(originalText))
			value = defaultValue;
		else {
			Double d = getValue(theText);
			if (d!=null)
				value = d.doubleValue();
			else {
				invalidNumber = true;
				value = 0.0;
			}
		}
		if (Recorder.record)
			recordOption(tf, trim(theText));
		nfIndex++;
		return value;
    }
    
	private String trim(String value) {
		if (value.endsWith(".0"))
			value = value.substring(0, value.length()-2);
		if (value.endsWith(".00"))
			value = value.substring(0, value.length()-3);
		return value;
	}

	private void recordOption(Component component, String value) {
		String label = (String)labels.get((Object)component);
		Recorder.recordOption(label, value);
	}

	private void recordCheckboxOption(Checkbox cb) {
		String label = (String)labels.get((Object)cb);
		if (cb.getState() && label!=null)
			Recorder.recordOption(label);
	}

 	protected Double getValue(String theText) {
 		Double d;
 		try {d = new Double(theText);}
		catch (NumberFormatException e){
			d = null;
		}
		return d;
	}

	/** Returns true if one or more of the numeric fields contained an  
		invalid number. Must be called after calls to getNextNumber(). */
   public boolean invalidNumber() {
    	boolean wasInvalid = invalidNumber;
    	invalidNumber = false;
    	return wasInvalid;
    }
    
  	/** Returns the contents of the next text field. */
   public String getNextString() {
   		String theText;
		if (stringField==null)
			return "";
		TextField tf = (TextField)(stringField.elementAt(sfIndex));
		theText = tf.getText();
		if (macro) {
			String label = (String)labels.get((Object)tf);
			theText = Macro.getValue(macroOptions, label, theText);
			//IJ.write("getNextString: "+label+"  "+theText);
		}	
		if (Recorder.record)
			recordOption(tf, theText);
		sfIndex++;
		return theText;
    }
    
  	/** Returns the state of the next checkbox. */
    public boolean getNextBoolean() {
		if (checkbox==null)
			return false;
		Checkbox cb = (Checkbox)(checkbox.elementAt(cbIndex));
		if (Recorder.record)
			recordCheckboxOption(cb);
		boolean state = cb.getState();
		if (macro) {
			String label = (String)labels.get((Object)cb);
			String key = Macro.trimKey(label);
			state = macroOptions.indexOf(key+" ")>=0;
			//IJ.write("getNextBoolean: "+label+"  "+state);
		}
		cbIndex++;
		return state;
    }
    
  	/** Returns the selected item in the next popup menu. */
    public String getNextChoice() {
		if (choice==null)
			return "";
		Choice thisChoice = (Choice)(choice.elementAt(choiceIndex));
		String item = thisChoice.getSelectedItem();
		if (macro) {
			String label = (String)labels.get((Object)thisChoice);
			item = Macro.getValue(macroOptions, label, item);
			//IJ.write("getNextChoice: "+label+"  "+item);
		}	
		if (Recorder.record)
			recordOption(thisChoice, item);
		choiceIndex++;
		return item;
    }
    
  	/** Returns the index of the selected item in the next popup menu. */
    public int getNextChoiceIndex() {
		if (choice==null)
			return -1;
		Choice thisChoice = (Choice)(choice.elementAt(choiceIndex));
		int index = thisChoice.getSelectedIndex();
		if (macro) {
			String label = (String)labels.get((Object)thisChoice);
			String oldItem = thisChoice.getSelectedItem();
			int oldIndex = thisChoice.getSelectedIndex();
			String item = Macro.getValue(macroOptions, label, oldItem);
			thisChoice.select(item);
			index = thisChoice.getSelectedIndex();
			if (index==oldIndex && !item.equals(oldItem)) {
				IJ.showMessage(getTitle(), "\""+item+"\" is not a vaid choice for \""+label+"\"");
				Macro.abort();
			}

		}	
		if (Recorder.record)
			recordOption(thisChoice, thisChoice.getSelectedItem());
		choiceIndex++;
		return index;
    }
    
  	/** Returns the contents of the next text area. */
	public String getNextText() {
		String text;
		if (textArea1!=null) {
			textArea1.selectAll();
			text = textArea1.getText();
			textArea1 = null;
			if (macro)
				text = Macro.getValue(macroOptions, "text1", text);
			if (Recorder.record)
				Recorder.recordOption("text1", text.replace('\n',' '));
		} else if (textArea2!=null) {
			textArea2.selectAll();
			text = textArea2.getText();
			textArea2 = null;
			if (macro)
				text = Macro.getValue(macroOptions, "text2", text);
			if (Recorder.record)
				Recorder.recordOption("text2", text.replace('\n',' '));
		} else
			text = null;
		return text;
	}

  	/** Displays this dialog box. */
    public void showDialog() {
		nfIndex = 0;
		sfIndex = 0;
		cbIndex = 0;
		choiceIndex = 0;
		if (macro) {
			//IJ.write("showDialog: "+macroOptions);
			dispose();
			return;
		}
    	if (stringField!=null&&numberField==null) {
    		TextField tf = (TextField)(stringField.elementAt(0));
    		tf.selectAll();
    	}
		Panel buttons = new Panel();
    	buttons.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
		cancel = new Button("Cancel");
		cancel.addActionListener(this);
		okay = new Button("  OK  ");
		okay.addActionListener(this);
		if (IJ.isMacintosh()) {
			buttons.add(cancel);
			buttons.add(okay);
		} else {
			buttons.add(okay);
			buttons.add(cancel);
		}
		c.gridx = 0; c.gridy = y;
		c.anchor = GridBagConstraints.EAST;
		c.gridwidth = 2;
		c.insets = new Insets(15, 0, 0, 0);
		grid.setConstraints(buttons, c);
		add(buttons);
        if (IJ.isMacintosh())
        	setResizable(false);
		pack();
		setup();
		GUI.center(this);
		show();
		IJ.wait(250); // work around for Sun/WinNT bug
  	}
    
	protected void setup() {
	}

	public void actionPerformed(ActionEvent e) {
		wasCanceled = (e.getSource()==cancel);
		setVisible(false);
		dispose();
	}

	public void textValueChanged(TextEvent e) {
	}

	public void itemStateChanged(ItemEvent e) {
	}

	public void focusGained(FocusEvent e) {
		Component c = e.getComponent();
		if (c instanceof TextField)
			((TextField)c).selectAll();
	}

	public void focusLost(FocusEvent e) {
		Component c = e.getComponent();
		if (c instanceof TextField)
			((TextField)c).select(0,0);
	}

 	public void keyPressed(KeyEvent e) {
		int keyCode = e.getKeyCode();
		IJ.setKeyDown(keyCode);
	}

	public void keyReleased(KeyEvent e) {
		IJ.setKeyUp(e.getKeyCode());
	}
		
	public void keyTyped(KeyEvent e) {}

	public Insets getInsets() {
    	Insets i= super.getInsets();
    	return new Insets(i.top+10, i.left+10, i.bottom+10, i.right+10);
	}

    public void paint(Graphics g) {
    	super.paint(g);
      	if (firstPaint && numberField!=null) {
      		TextField tf = (TextField)(numberField.elementAt(0));
    		tf.requestFocus();
    		firstPaint = false;
    	}
    }
    	
}