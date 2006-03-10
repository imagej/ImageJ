package ij.gui;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import ij.*;
import ij.plugin.frame.Recorder;
import ij.plugin.ScreenGrabber;
import ij.util.Tools;

/**
 * This class is a customizable modal dialog box. Here is an example
 * GenericDialog with one string field and two numeric fields:
 * <pre>
 *  public class Generic_Dialog_Example implements PlugIn {
 *    static String title="Example";
 *    static int width=512,height=512;
 *    public void run(String arg) {
 *      GenericDialog gd = new GenericDialog("New Image");
 *      gd.addStringField("Title: ", title);
 *      gd.addNumericField("Width: ", width, 0);
 *      gd.addNumericField("Height: ", height, 0);
 *      gd.showDialog();
 *      if (gd.wasCanceled()) return;
 *      title = gd.getNextString();
 *      width = (int)gd.getNextNumber();
 *      height = (int)gd.getNextNumber();
 *      IJ.run("New...", "name="+title+" type='8-bit Unsigned' width="+width+" height="+height);
 *   }
 * }
 * </pre>
 */
public class GenericDialog extends Dialog implements ActionListener,
TextListener, FocusListener, ItemListener, KeyListener, AdjustmentListener {

	protected Vector numberField, stringField, checkbox, choice, slider;
	protected TextArea textArea1, textArea2;
	protected Vector defaultValues,defaultText;
	protected Component theLabel;
	private Button cancel, okay;
    private boolean wasCanceled;
    private int y;
    private int nfIndex, sfIndex, cbIndex, choiceIndex;
	private GridBagLayout grid;
	private GridBagConstraints c;
	private boolean firstNumericField=true;
	private boolean firstSlider=true;
	private boolean invalidNumber;
	private String errorMessage;
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
		if (Prefs.blackCanvas) {
			//Color fc = getForeground();
			//Color bc = getBackground();
			//IJ.log("before: "+fc+" "+bc);
			setForeground(SystemColor.controlText);
			setBackground(SystemColor.control);
			//fc = getForeground();
			//bc = getBackground();
			//IJ.log("after : "+fc+" "+bc);
		}
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
		addNumericField(label, defaultValue, digits, 6, null);
	}

	/** Adds a numeric field. The first word of the label must be
		unique or command recording will not work.
	* @param label			the label
	* @param defaultValue	value to be initially displayed
	* @param digits			number of digits to right of decimal point
	* @param columns		width of field in characters
	* @param units			a string displayed to the right of the field
	*/
   public void addNumericField(String label, double defaultValue, int digits, int columns, String units) {
   		String label2 = label;
   		if (label2.indexOf('_')!=-1)
   			label2 = label2.replace('_', ' ');
		Label theLabel = makeLabel(label2);
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
		if (IJ.isWindows()) columns -= 2;
		if (columns<1) columns = 1;
		TextField tf = new TextField(IJ.d2s(defaultValue, digits), columns);
		tf.addActionListener(this);
		tf.addTextListener(this);
		tf.addFocusListener(this);
		tf.addKeyListener(this);
		numberField.addElement(tf);
		defaultValues.addElement(new Double(defaultValue));
		defaultText.addElement(tf.getText());
		c.gridx = 1; c.gridy = y;
		c.anchor = GridBagConstraints.WEST;
		tf.setEditable(true);
		if (firstNumericField) tf.selectAll();
		firstNumericField = false;
		if (units==null||units.equals("")) {
			grid.setConstraints(tf, c);
			add(tf);
		} else {
    		Panel panel = new Panel();
			panel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
    		panel.add(tf);
			panel.add(new Label(" "+units));
			grid.setConstraints(panel, c);
			add(panel);    		
		}
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
    	if (label.length()>0) {
    		if (label.charAt(0)==' ')
    			label = label.trim();
			labels.put(component, label);
		}
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
   		String label2 = label;
   		if (label2.indexOf('_')!=-1)
   			label2 = label2.replace('_', ' ');
		Label theLabel = makeLabel(label2);
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
    	String label2 = label;
   		if (label2.indexOf('_')!=-1)
   			label2 = label2.replace('_', ' ');
    	if (checkbox==null) {
    		checkbox = new Vector(4);
			c.insets = new Insets(15, 20, 0, 0);
    	} else
			c.insets = new Insets(0, 20, 0, 0);
		c.gridx = 0; c.gridy = y;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		Checkbox cb = new Checkbox(label2);
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
    	panel.setLayout(new GridLayout(rows,columns, 5, 0));
    	int startCBIndex = cbIndex;
    	int i1 = 0;
    	int[] index = new int[labels.length];
    	if (checkbox==null)
    		checkbox = new Vector(12);
    	boolean addListeners = labels.length<=4;
    	for (int row=0; row<rows; row++) {
			for (int col=0; col<columns; col++) {
				int i2 = col*rows+row;
				if (i2>=labels.length) break;
				index[i1] = i2;
				String label = labels[i1];
				if (label.indexOf('_')!=-1)
   					label = label.replace('_', ' ');
				Checkbox cb = new Checkbox(label);
				checkbox.addElement(cb);
				cb.setState(defaultValues[i1]);
				if (addListeners) cb.addItemListener(this);
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
		//textArea1.setBackground(Color.white);
		//textArea1.append(text1);
		panel.add(textArea1);
		if (text2!=null) {
			textArea2 = new TextArea(text2,rows,columns,TextArea.SCROLLBARS_NONE);
			//textArea2.setBackground(Color.white);
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
    
   public void addSlider(String label, double minValue, double maxValue, double defaultValue) {
		int columns = 4;
		int digits = 0;
   		String label2 = label;
   		if (label2.indexOf('_')!=-1)
   			label2 = label2.replace('_', ' ');
		Label theLabel = makeLabel(label2);
		c.gridx = 0; c.gridy = y;
		c.anchor = GridBagConstraints.EAST;
		c.gridwidth = 1;
		c.insets = new Insets(0, 0, 3, 0);
		grid.setConstraints(theLabel, c);
		add(theLabel);
		
		if (slider==null)
			slider = new Vector(5);
		Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, (int)defaultValue, 1, (int)minValue, (int)maxValue+1);
		slider.addElement(s);
		s.addAdjustmentListener(this);
		s.setUnitIncrement(1);

		if (numberField==null) {
			numberField = new Vector(5);
			defaultValues = new Vector(5);
			defaultText = new Vector(5);
		}
		if (IJ.isWindows()) columns -= 2;
		if (columns<1) columns = 1;
		TextField tf = new TextField(IJ.d2s(defaultValue, digits), columns);
		tf.addActionListener(this);
		tf.addTextListener(this);
		tf.addFocusListener(this);
		tf.addKeyListener(this);
		numberField.addElement(tf);
		defaultValues.addElement(new Double(defaultValue));
		defaultText.addElement(tf.getText());
		tf.setEditable(true);
		if (firstNumericField && firstSlider) tf.selectAll();
		firstSlider = false;
		
    	Panel panel = new Panel();
		GridBagLayout pgrid = new GridBagLayout();
		GridBagConstraints pc  = new GridBagConstraints();
		panel.setLayout(pgrid);
		// label
		//pc.insets = new Insets(5, 0, 0, 0);
		//pc.gridx = 0; pc.gridy = 0;
		//pc.gridwidth = 1;
		//pc.anchor = GridBagConstraints.EAST;
		//pgrid.setConstraints(theLabel, pc);
		//panel.add(theLabel);
		// slider
		pc.gridx = 0; pc.gridy = 0;
		pc.gridwidth = 1;
		pc.ipadx = 75;
		pc.anchor = GridBagConstraints.WEST;
		pgrid.setConstraints(s, pc);
		panel.add(s);
		pc.ipadx = 0;  // reset
		// text field
		pc.gridx = 1;
		pc.insets = new Insets(5, 5, 0, 0);
		pc.anchor = GridBagConstraints.EAST;
		pgrid.setConstraints(tf, pc);
    	panel.add(tf);
    	
		grid.setConstraints(panel, c);
		c.gridx = 1; c.gridy = y;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(0, 0, 0, 0);
		grid.setConstraints(panel, c);
		add(panel);
		y++;
		if (Recorder.record || macro)
			saveLabel(tf, label);
    }

    /** Adds a Panel to the dialog. */
    public void addPanel(Panel panel) {
    	addPanel(panel , GridBagConstraints.WEST, new Insets(5, 0, 0, 0));
    }

    /** Adds a Panel to the dialog with custom contraint and insets. The
    	defaults are GridBagConstraints.WEST (left justified) and 
    	"new Insets(5, 0, 0, 0)" (5 pixels of padding at the top). */
    public void addPanel(Panel panel, int contraints, Insets insets) {
		c.gridx = 0; c.gridy = y;
		c.gridwidth = 2;
		c.anchor = contraints;
		c.insets = insets;
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
        String label=null;
		if (macro) {
			label = (String)labels.get((Object)tf);
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
				errorMessage = "\""+theText+"\" is an invalid number";
				value = 0.0;
                if (macro) {
                    IJ.error("Macro Error", "Numeric value expected in run() function\n \n"
                        +"   Dialog: \""+getTitle()+"\"\n"
                        +"   Label: \""+label+"\"\n"
                        +"   Value: \""+theText+"\"");
                }
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
		if (label!=null) {
			if (cb.getState()) // checked
				Recorder.recordOption(label);
			else if (Recorder.getCommandOptions()==null)
				Recorder.recordOption(" ");
		}
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
		invalid number. Must be called after one or more calls to getNextNumber(). */
   public boolean invalidNumber() {
    	boolean wasInvalid = invalidNumber;
    	invalidNumber = false;
    	return wasInvalid;
    }
    
	/** Returns an error message if getNextNumber was unable to convert a 
		string into a number, otherwise, returns null. */
	public String getErrorMessage() {
		return errorMessage;
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
			if (index==oldIndex && !item.equals(oldItem))
				IJ.error(getTitle(), "\""+item+"\" is not a valid choice for \""+label+"\"");
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
		//EventQueue.invokeLater(new Runnable () {
		//	public void run () { 
		//		show(); 
		//}}); 

  	}
  	
  	/** Returns the Vector containing the numeric TextFields. */
  	public Vector getNumericFields() {
  		return numberField;
  	}
    
  	/** Returns the Vector containing the string TextFields. */
  	public Vector getStringFields() {
  		return stringField;
  	}

  	/** Returns the Vector containing the Checkboxes. */
  	public Vector getCheckboxes() {
  		return checkbox;
  	}

  	/** Returns the Vector containing the Choices. */
  	public Vector getChoices() {
  		return choice;
  	}

  	/** Returns the sliders (Scrollbars). */
  	public Vector getSliders() {
  		return slider;
  	}

  	/** Returns a reference to textArea1. */
  	public TextArea getTextArea1() {
  		return textArea1;
  	}

  	/** Returns a reference to textArea2. */
  	public TextArea getTextArea2() {
  		return textArea2;
  	}
  	
  	/** Returns a reference to the Label or MultiLineLabel created
  		by addMessage(), or null if addMessage() was not called. */
  	public Component getMessage() {
  		return theLabel;
  	}

	protected void setup() {
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source==okay || source==cancel) {
			wasCanceled = source==cancel;
			closeDialog();
		}
	}

	void closeDialog() {
		setVisible(false);
		dispose();
	}
	
	public void textValueChanged(TextEvent e) {
		if (slider==null || slider.size()!=numberField.size())
			return;
		Object source = e.getSource();
		for (int i=0; i<numberField.size(); i++) {
			if (source==numberField.elementAt(i)) {
				TextField tf = (TextField)numberField.elementAt(i);
				double value = Tools.parseDouble(tf.getText());
				if (!Double.isNaN(value)) {
					Scrollbar sb = (Scrollbar)slider.elementAt(i);
					sb.setValue((int)value);
				}	
				//IJ.log(i+" "+tf.getText());
			}
		}
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
		if (keyCode==KeyEvent.VK_ENTER && textArea1==null) 
			closeDialog(); 
		else if (keyCode==KeyEvent.VK_ESCAPE) { 
			wasCanceled = true; 
			closeDialog(); 
			IJ.resetEscape();
		} 
	} 

	public void keyReleased(KeyEvent e) {
		int keyCode = e.getKeyCode();
		IJ.setKeyUp(keyCode);
		int flags = e.getModifiers();
		boolean control = (flags & KeyEvent.CTRL_MASK) != 0;
		boolean meta = (flags & KeyEvent.META_MASK) != 0;
		boolean shift = (flags & e.SHIFT_MASK) != 0;
		if (keyCode==KeyEvent.VK_G && shift && (control||meta) && IJ.isJava2())
			new ScreenGrabber().run(""); 
	}
		
	public void keyTyped(KeyEvent e) {}

	public Insets getInsets() {
    	Insets i= super.getInsets();
    	return new Insets(i.top+10, i.left+10, i.bottom+10, i.right+10);
	}

	public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
		Object source = e.getSource();
		for (int i=0; i<slider.size(); i++) {
			if (source==slider.elementAt(i)) {
				Scrollbar sb = (Scrollbar)source;
				TextField tf = (TextField)numberField.elementAt(i);
				tf.setText(""+sb.getValue());
			}
		}
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