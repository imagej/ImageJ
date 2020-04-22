package ij.gui;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import ij.*;
import ij.plugin.frame.Recorder;
import ij.plugin.ScreenGrabber;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.util.Tools;
import ij.macro.*;


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
 *      IJ.newImage(title, "8-bit", width, height, 1);
 *   }
 * }
 * </pre>
* To work with macros, the first word of each component label must be
* unique. If this is not the case, add underscores, which will be converted
* to spaces when the dialog is displayed. For example, change the checkbox labels
* "Show Quality" and "Show Residue" to "Show_Quality" and "Show_Residue".
*/
public class GenericDialog extends Dialog implements ActionListener, TextListener,
FocusListener, ItemListener, KeyListener, AdjustmentListener, WindowListener {

	protected Vector numberField, stringField, checkbox, choice, slider, radioButtonGroups;
	protected TextArea textArea1, textArea2;
	protected Vector defaultValues,defaultText,defaultStrings,defaultChoiceIndexes;
	protected Component theLabel;
	private Button okay = new Button("  OK  ");
	private Button cancel = new Button("Cancel");
	private Button no, help;
	private String helpLabel = "Help";
	private boolean wasCanceled, wasOKed;
	private int nfIndex, sfIndex, cbIndex, choiceIndex, textAreaIndex, radioButtonIndex;
	private GridBagConstraints c;
	private boolean firstNumericField=true;
	private boolean firstSlider=true;
	private boolean invalidNumber;
	private String errorMessage;
	private Hashtable labels;
	private boolean macro;
	private String macroOptions;
	private boolean addToSameRow;
	private boolean addToSameRowCalled;
	private int topInset, leftInset, bottomInset;
	private boolean customInsets;
	private Vector sliderIndexes, sliderScales, sliderDigits;
	private Checkbox previewCheckbox;    // the "Preview" Checkbox, if any
	private Vector dialogListeners;      // the Objects to notify on user input
	private PlugInFilterRunner pfr;      // the PlugInFilterRunner for automatic preview
	private String previewLabel = " Preview";
	private final static String previewRunning = "wait...";
	private boolean recorderOn;          // whether recording is allowed (after the dialog is closed)
	private char echoChar;
	private boolean hideCancelButton;
	private boolean centerDialog = true;
	private String helpURL;
	private boolean smartRecording;
	private Vector imagePanels;
	private static GenericDialog instance;
	private boolean firstPaint = true;
	private boolean fontSizeSet;
	private boolean showDialogCalled;
	private boolean optionsRecorded;     // have dialogListeners been called to record options?
	private Label lastLabelAdded;


    /** Creates a new GenericDialog with the specified title. Uses the current image
    	image window as the parent frame or the ImageJ frame if no image windows
    	are open. Dialog parameters are recorded by ImageJ's command recorder but
    	this requires that the first word of each label be unique. */
	public GenericDialog(String title) {
		this(title, getParentFrame());
	}

	static Frame getParentFrame() {
		Frame parent = WindowManager.getCurrentImage()!=null?
			(Frame)WindowManager.getCurrentImage().getWindow():IJ.getInstance()!=null?IJ.getInstance():new Frame();
		if (IJ.isMacOSX() && IJ.isJava18()) {
			ImageJ ij = IJ.getInstance();
			if (ij!=null && ij.isActive())
				parent = ij;
			else
				parent = null;
		}
		return parent;
	}

    /** Creates a new GenericDialog using the specified title and parent frame. */
    public GenericDialog(String title, Frame parent) {
		super(parent==null?new Frame():parent, title, true);
		if (Prefs.blackCanvas) {
			setForeground(SystemColor.controlText);
			setBackground(SystemColor.control);
		}
		GridBagLayout grid = new GridBagLayout();
		c = new GridBagConstraints();
		setLayout(grid);
		macroOptions = Macro.getOptions();
		//IJ.log("macroOptions: "+macroOptions+"  "+title);
		macro = macroOptions!=null;
		addKeyListener(this);
		addWindowListener(this);
    }

	/** Adds a numeric field. The first word of the label must be
		unique or command recording will not work.
	* @param label			the label
	* @param defaultValue	value to be initially displayed
	*/
	public void addNumericField(String label, double defaultValue) {
		int decimalPlaces = (int)defaultValue==defaultValue?0:3;
		int columnWidth = decimalPlaces==3?8:6;
		addNumericField(label, defaultValue, decimalPlaces, columnWidth, null);
	}

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
		Label fieldLabel = makeLabel(label2);
		this.lastLabelAdded = fieldLabel;
		if (addToSameRow) {
			c.gridx = GridBagConstraints.RELATIVE;
			c.insets.left = 10;
		} else {
			c.gridx = 0; c.gridy++;
			if (firstNumericField)
				c.insets = getInsets(5, 0, 3, 0); // top, left, bottom, right
			else
				c.insets = getInsets(0, 0, 3, 0);
		}
		c.anchor = GridBagConstraints.EAST;
		c.gridwidth = 1;
		//IJ.log("x="+c.gridx+", y= "+c.gridy+", width="+c.gridwidth+", ancher= "+c.anchor+" "+c.insets);
		add(fieldLabel, c);
		if (addToSameRow) {
			c.insets.left = 0;
			addToSameRow = false;
		}
		if (numberField==null) {
			numberField = new Vector(5);
			defaultValues = new Vector(5);
			defaultText = new Vector(5);
		}
		if (IJ.isWindows()) columns -= 2;
		if (columns<1) columns = 1;
		String defaultString = IJ.d2s(defaultValue, digits);
		if (Double.isNaN(defaultValue))
			defaultString = "";
		TextField tf = new TextField(defaultString, columns);
		if (IJ.isLinux()) tf.setBackground(Color.white);
		tf.addActionListener(this);
		tf.addTextListener(this);
		tf.addFocusListener(this);
		tf.addKeyListener(this);
		numberField.addElement(tf);
		defaultValues.addElement(new Double(defaultValue));
		defaultText.addElement(tf.getText());
		c.gridx = GridBagConstraints.RELATIVE;
		c.anchor = GridBagConstraints.WEST;
		tf.setEditable(true);
		//if (firstNumericField) tf.selectAll();
		firstNumericField = false;
		if (units==null||units.equals("")) {
			add(tf, c);
		} else {
    		Panel panel = new Panel();
			panel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
    		panel.add(tf);
			panel.add(new Label(" "+units));
			add(panel, c);
		}
		if (Recorder.record || macro)
			saveLabel(tf, label);
    }

    private Label makeLabel(String label) {
    	if (IJ.isMacintosh())
    		label += " ";
		return new Label(label);
    }

	/** Saves the label for given component, for macro recording and for accessing the component in macros. */
    private void saveLabel(Object component, String label) {
    	if (labels==null)
    		labels = new Hashtable();
    	if (label.length()>0)
    		label = Macro.trimKey(label.trim());
    	if (label.length()>0 && hasLabel(label)) {                      // not a unique label?
    		label += "_0";
    		for (int n=1; hasLabel(label); n++) {   // while still not a unique label
    			label = label.substring(0, label.lastIndexOf('_')); //remove counter
    			label += "_"+n;
    		}
    	}
		labels.put(component, label);
    }

	/** Returns whether the list of labels for macro recording or macro creation contains a given label. */
    private boolean hasLabel(String label) {
    	for (Object o : labels.keySet())
    		if (labels.get(o).equals(label)) return true;
    	return false;
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
	* @param columns			width of the text field. If columns is 8 or more, additional items may be added to this line with addToSameRow()
	*/
	public void addStringField(String label, String defaultText, int columns) {
		if (addToSameRow && label.equals("_"))
			label = "";
   		String label2 = label;
   		if (label2.indexOf('_')!=-1)
   			label2 = label2.replace('_', ' ');
		Label fieldLabel = makeLabel(label2);
		this.lastLabelAdded = fieldLabel;
		boolean custom = customInsets;
		if (addToSameRow) {
			c.gridx = GridBagConstraints.RELATIVE;
			addToSameRow = false;
		} else {
			c.gridx = 0; c.gridy++;
			if (stringField==null)
				c.insets = getInsets(5, 0, 5, 0); // top, left, bottom, right
			else
				c.insets = getInsets(0, 0, 5, 0);
        }
		c.anchor = GridBagConstraints.EAST;
		c.gridwidth = 1;
		add(fieldLabel, c);
		if (stringField==null) {
			stringField = new Vector(4);
			defaultStrings = new Vector(4);
		}

		TextField tf = new TextField(defaultText, columns);
		if (IJ.isLinux()) tf.setBackground(Color.white);
		tf.setEchoChar(echoChar);
		echoChar = 0;
		tf.addActionListener(this);
		tf.addTextListener(this);
		tf.addFocusListener(this);
		tf.addKeyListener(this);
		c.gridx = GridBagConstraints.RELATIVE;
		c.anchor = GridBagConstraints.WEST;
		c.gridwidth = columns <= 8 ? 1 : GridBagConstraints.REMAINDER;
		c.insets.left = 0;
		tf.setEditable(true);
		add(tf, c);
		stringField.addElement(tf);
		defaultStrings.addElement(defaultText);
		if (Recorder.record || macro)
			saveLabel(tf, label);
    }

    /** Sets the echo character for the next string field. */
    public void setEchoChar(char echoChar) {
    	this.echoChar = echoChar;
    }

	/** Adds a checkbox.
	* @param label			the label
	* @param defaultValue	the initial state
	*/
    public void addCheckbox(String label, boolean defaultValue) {
        addCheckbox(label, defaultValue, false);
    }

    /** Adds a checkbox; does not make it recordable if isPreview is true.
     * With isPreview true, the checkbox can be referred to as previewCheckbox
     * from hereon.
     */
    private void addCheckbox(String label, boolean defaultValue, boolean isPreview) {
    	String label2 = label;
   		if (label2.indexOf('_')!=-1)
   			label2 = label2.replace('_', ' ');
		if (addToSameRow) {
			c.gridx = GridBagConstraints.RELATIVE;
			c.insets.left = 10;
			addToSameRow = false;
		} else {
			c.gridx = 0; c.gridy++;
			if (checkbox==null)
				c.insets = getInsets(15, 20, 0, 0);  // top, left, bottom, right
    		else
				c.insets = getInsets(0, 20, 0, 0);
		}
		c.anchor = GridBagConstraints.WEST;
		c.gridwidth = 2;
    	if (checkbox==null)
    		checkbox = new Vector(4);
		Checkbox cb = new Checkbox(label2);
		cb.setState(defaultValue);
		cb.addItemListener(this);
		cb.addKeyListener(this);
		add(cb, c);
		c.insets.left = 0;
		checkbox.addElement(cb);
        if (!isPreview &&(Recorder.record || macro)) //preview checkbox is not recordable
			saveLabel(cb, label);
        if (isPreview) previewCheckbox = cb;
    }

    /** Adds a checkbox labelled "Preview" for "automatic" preview.
     * The reference to this checkbox can be retrieved by getPreviewCheckbox()
     * and it provides the additional method previewRunning for optical
     * feedback while preview is prepared.
     * PlugInFilters can have their "run" method automatically called for
     * preview under the following conditions:
     * - the PlugInFilter must pass a reference to itself (i.e., "this") as an
     *   argument to the AddPreviewCheckbox
     * - it must implement the DialogListener interface and set the filter
     *   parameters in the dialogItemChanged method.
     * - it must have DIALOG and PREVIEW set in its flags.
     * A previewCheckbox is always off when the filter is started and does not get
     * recorded by the Macro Recorder.
     *
     * @param pfr A reference to the PlugInFilterRunner calling the PlugInFilter
     * if automatic preview is desired, null otherwise.
     */
    public void addPreviewCheckbox(PlugInFilterRunner pfr) {
        if (previewCheckbox != null)
        	return;
    	ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null && imp.isComposite() && ((CompositeImage)imp).getMode()==IJ.COMPOSITE)
			return;
        this.pfr = pfr;
        addCheckbox(previewLabel, false, true);
    }

    /** Add the preview checkbox with user-defined label; for details see the
     *  addPreviewCheckbox method with standard "Preview" label.
     * Adds the checkbox when the current image is a CompositeImage
     * in "Composite" mode, unlike the one argument version.
     * Note that a GenericDialog can have only one PreviewCheckbox.
     */
    public void addPreviewCheckbox(PlugInFilterRunner pfr, String label) {
        if (previewCheckbox!=null)
        	return;
        previewLabel = label;
        this.pfr = pfr;
        addCheckbox(previewLabel, false, true);
    }

    /** Adds a group of checkboxs using a grid layout.
	* @param rows			the number of rows
	* @param columns		the number of columns
	* @param labels			the labels
	* @param defaultValues	the initial states
	*/
    public void addCheckboxGroup(int rows, int columns, String[] labels, boolean[] defaultValues) {
    	addCheckboxGroup(rows, columns, labels, defaultValues, null);
    }

    /** Adds a group of checkboxs using a grid layout.
	* @param rows			the number of rows
	* @param columns		the number of columns
	* @param labels			the labels
	* @param defaultValues	the initial states
	* @param headings	the column headings
	* Example: http://imagej.nih.gov/ij/plugins/multi-column-dialog/index.html
	*/
    public void addCheckboxGroup(int rows, int columns, String[] labels, boolean[] defaultValues, String[] headings) {
    	Panel panel = new Panel();
    	int nRows = headings!=null?rows+1:rows;
    	panel.setLayout(new GridLayout(nRows, columns, 6, 0));
    	int startCBIndex = cbIndex;
    	if (checkbox==null)
    		checkbox = new Vector(12);
    	if (headings!=null) {
    		Font font = new Font("SansSerif", Font.BOLD, 12);
			for (int i=0; i<columns; i++) {
				if (i>headings.length-1 || headings[i]==null)
					panel.add(new Label(""));
				else {
					Label label = new Label(headings[i]);
					label.setFont(font);
					panel.add(label);
				}
			}
    	}
    	int i1 = 0;
    	int[] index = new int[labels.length];
    	for (int row=0; row<rows; row++) {
			for (int col=0; col<columns; col++) {
				int i2 = col*rows+row;
				if (i2>=labels.length) break;
				index[i1] = i2;
				String label = labels[i1];
				if (label==null || label.length()==0) {
					Label lbl = new Label("");
					panel.add(lbl);
					i1++;
					continue;
				}
				if (label.indexOf('_')!=-1)
   					label = label.replace('_', ' ');
				Checkbox cb = new Checkbox(label);
				checkbox.addElement(cb);
				cb.setState(defaultValues[i1]);
				cb.addItemListener(this);
				if (Recorder.record || macro)
					saveLabel(cb, labels[i1]);
				if (IJ.isLinux()) {
					Panel panel2 = new Panel();
					panel2.setLayout(new BorderLayout());
					panel2.add("West", cb);
					panel.add(panel2);
				} else
					panel.add(cb);
 				i1++;
			}
		}
		c.gridx = 0; c.gridy++;
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.anchor = GridBagConstraints.WEST;
		c.insets = getInsets(10, 0, 0, 0);
		addToSameRow = false;
		add(panel, c);
    }

    /** Adds a radio button group.
	* @param label			group label (or null)
	* @param items		radio button labels
	* @param rows			number of rows
	* @param columns	number of columns
	* @param defaultItem		button initially selected
	*/
    public void addRadioButtonGroup(String label, String[] items, int rows, int columns, String defaultItem) {
		addToSameRow = false;
    	Panel panel = new Panel();
    	int n = items.length;
     	panel.setLayout(new GridLayout(rows, columns, 0, 0));
		CheckboxGroup cg = new CheckboxGroup();
		for (int i=0; i<n; i++) {
			Checkbox cb = new Checkbox(items[i],cg,items[i].equals(defaultItem));
			cb.addItemListener(this);
			panel.add(cb);
		}
		if (radioButtonGroups==null)
			radioButtonGroups = new Vector();
		radioButtonGroups.addElement(cg);
		Insets insets = getInsets(5, 10, 0, 0);
		if (label==null || label.equals("")) {
			label = "rbg"+radioButtonGroups.size();
			insets.top += 5;
		} else {
			setInsets(10, insets.left, 0);
			addMessage(label);
			insets.top = 2;
			insets.left += 10;
		}
		c.gridx = 0; c.gridy++;
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(insets.top, insets.left, 0, 0);
		add(panel, c);
		if (Recorder.record || macro)
			saveLabel(cg, label);
    }

    /** Adds a popup menu.
   * @param label	the label
   * @param items	the menu items
   * @param defaultItem	the menu item initially selected
   */
   public void addChoice(String label, String[] items, String defaultItem) {
   		String label2 = label;
   		if (label2.indexOf('_')!=-1)
   			label2 = label2.replace('_', ' ');
		Label fieldLabel = makeLabel(label2);
		this.lastLabelAdded = fieldLabel;
		if (addToSameRow) {
			c.gridx = GridBagConstraints.RELATIVE;
			addToSameRow = false;
		} else {
			c.gridx = 0; c.gridy++;
			if (choice==null)
				c.insets = getInsets(5, 0, 5, 0);
			else
				c.insets = getInsets(0, 0, 5, 0);
		}
		c.anchor = GridBagConstraints.EAST;
		c.gridwidth = 1;
		if (choice==null) {
			choice = new Vector(4);
			defaultChoiceIndexes = new Vector(4);
		}
		add(fieldLabel, c);
		Choice thisChoice = new Choice();
		thisChoice.addKeyListener(this);
		thisChoice.addItemListener(this);
		for (int i=0; i<items.length; i++)
			thisChoice.addItem(items[i]);
		if (defaultItem!=null)
			thisChoice.select(defaultItem);
		else
			thisChoice.select(0);
		c.gridx = GridBagConstraints.RELATIVE;
		c.anchor = GridBagConstraints.WEST;
		add(thisChoice, c);
		choice.addElement(thisChoice);
		int index = thisChoice.getSelectedIndex();
		defaultChoiceIndexes.addElement(new Integer(index));
		if (Recorder.record || macro)
			saveLabel(thisChoice, label);
    }

    /** Adds a message consisting of one or more lines of text. */
    public void addMessage(String text) {
    	addMessage(text, null, null);
    }

    /** Adds a message consisting of one or more lines of text,
    	which will be displayed using the specified font. */
    public void addMessage(String text, Font font) {
    	addMessage(text, font, null);
    }

    /** Adds a message consisting of one or more lines of text,
    	which will be displayed using the specified font and color. */
    public void addMessage(String text, Font font, Color color) {
    	theLabel = null;
    	if (text.indexOf('\n')>=0)
			theLabel = new MultiLineLabel(text);
		else
			theLabel = new Label(text);
		if (addToSameRow) {
			c.gridx = GridBagConstraints.RELATIVE;
			addToSameRow = false;
		} else {
			c.gridx = 0; c.gridy++;
			c.insets = getInsets("".equals(text)?0:10, 20, 0, 0); // top, left, bottom, right
		}
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		if (font!=null) {
			if (Prefs.getGuiScale()>1.0)
				font = font.deriveFont((float)(font.getSize()*Prefs.getGuiScale()));
			theLabel.setFont(font);
		}
		if (color!=null)
			theLabel.setForeground(color);
		add(theLabel, c);
		c.fill = GridBagConstraints.NONE;
    }

	/** Adds one or two (side by side) text areas.
	* @param text1	initial contents of the first text area
	* @param text2	initial contents of the second text area or null
	* @param rows	the number of rows
	* @param columns	the number of columns
	*/
    public void addTextAreas(String text1, String text2, int rows, int columns) {
		if (textArea1!=null) return;
		Panel panel = new Panel();
		Font font = new Font("SansSerif", Font.PLAIN, (int)(14*Prefs.getGuiScale()));
		textArea1 = new TextArea(text1,rows,columns,TextArea.SCROLLBARS_NONE);
		if (IJ.isLinux()) textArea1.setBackground(Color.white);
		textArea1.setFont(font);
		textArea1.addTextListener(this);
		panel.add(textArea1);
		if (text2!=null) {
			textArea2 = new TextArea(text2,rows,columns,TextArea.SCROLLBARS_NONE);
			if (IJ.isLinux()) textArea2.setBackground(Color.white);
			textArea2.setFont(font);
			panel.add(textArea2);
		}
		c.gridx = 0; c.gridy++;
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.anchor = GridBagConstraints.WEST;
		c.insets = getInsets(15, 20, 0, 0);
		addToSameRow = false;
		add(panel, c);
    }

	/**
	* Adds a slider (scroll bar) to the dialog box.
	* Floating point values are used if (maxValue-minValue)<=5.0
	* and either defaultValue or minValue are non-integer.
	* @param label	 the label
	* @param minValue  the minimum value of the slider
	* @param maxValue  the maximum value of the slider
	* @param defaultValue  the initial value of the slider
	*/
	public void addSlider(String label, double minValue, double maxValue, double defaultValue) {
		if (defaultValue<minValue) defaultValue=minValue;
		if (defaultValue>maxValue) defaultValue=maxValue;
		int digits = 0;
		double scale = 1.0;
		if ((maxValue-minValue)<=5.0 && (minValue!=(int)minValue||maxValue!=(int)maxValue||defaultValue!=(int)defaultValue)) {
			scale = 50.0;
			minValue *= scale;
			maxValue *= scale;
			defaultValue *= scale;
			digits = 2;
		}
		addSlider( label, minValue, maxValue, defaultValue, scale, digits);
	}

	/** This vesion of addSlider() adds a 'stepSize' argument.<br>
	 * Example: http://wsr.imagej.net/macros/SliderDemo.txt
	*/
	public void addSlider(String label, double minValue, double maxValue, double defaultValue, double stepSize) {
		if ( stepSize <= 0 ) stepSize  = 1;
		int digits = digits(stepSize);
		if (digits==1 && "Angle:".equals(label))
			digits = 2;
		double scale = 1.0 / Math.abs( stepSize );
		if ( scale <= 0 ) scale = 1;
		if ( defaultValue < minValue ) defaultValue = minValue;
		if ( defaultValue > maxValue ) defaultValue = maxValue;
		minValue *= scale;
		maxValue *= scale;
		defaultValue *= scale;
		addSlider(label, minValue, maxValue, defaultValue, scale, digits);
	}

	/** Author: Michael Kaul */
	private static int digits(double d) {
		if (d == (int)d)
			return 0;
		String s  = Double.toString(d);
		int ePos  = s.indexOf("E");
		if (ePos==-1)
			ePos   = s.indexOf("e");
		int dotPos = s.indexOf( "." );
		int digits = 0;
		if (ePos==-1 )
			digits = s.substring(dotPos+1).length();
		else {
			String number = s.substring( dotPos + 1, ePos );
			if (!number.equals( "0" ))
				digits += number.length( );
			digits = digits - Integer.valueOf(s.substring(ePos+1));
		}
		return digits;
	}

	private void addSlider(String label, double minValue, double maxValue, double defaultValue, double scale, int digits) {
		int columns = 4 + digits - 2;
		if ( columns < 4 ) columns = 4;
		if (minValue<0.0) columns++;
		String mv = IJ.d2s(maxValue,0);
		if (mv.length()>4 && digits==0)
			columns += mv.length()-4;
   		String label2 = label;
   		if (label2.indexOf('_')!=-1)
   			label2 = label2.replace('_', ' ');
		Label fieldLabel = makeLabel(label2);
		this.lastLabelAdded = fieldLabel;
		if (addToSameRow) {
			c.gridx = GridBagConstraints.RELATIVE;
			c.insets.bottom += 3;
			addToSameRow = false;
		} else {
			c.gridx = 0; c.gridy++;
			c.insets = getInsets(0, 0, 3, 0); // top, left, bottom, right
		}
		c.anchor = GridBagConstraints.EAST;
		c.gridwidth = 1;
		add(fieldLabel, c);

		if (slider==null) {
			slider = new Vector(5);
			sliderIndexes = new Vector(5);
			sliderScales = new Vector(5);
			sliderDigits = new Vector(5);
		}
		Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, (int)defaultValue, 1, (int)minValue, (int)maxValue+1);
		GUI.fixScrollbar(s);
		slider.addElement(s);
		s.addAdjustmentListener(this);
		s.setUnitIncrement(1);
		if (IJ.isMacOSX())
			s.addKeyListener(this);

		if (numberField==null) {
			numberField = new Vector(5);
			defaultValues = new Vector(5);
			defaultText = new Vector(5);
		}
		if (IJ.isWindows()) columns -= 2;
		if (columns<1) columns = 1;
		//IJ.log("scale=" + scale + ", columns=" + columns + ", digits=" + digits);
		TextField tf = new TextField(IJ.d2s(defaultValue/scale, digits), columns);
		if (IJ.isLinux()) tf.setBackground(Color.white);
		tf.addActionListener(this);
		tf.addTextListener(this);
		tf.addFocusListener(this);
		tf.addKeyListener(this);
		numberField.addElement(tf);
		sliderIndexes.add(new Integer(numberField.size()-1));
		sliderScales.add(new Double(scale));
		sliderDigits.add(new Integer(digits));
		defaultValues.addElement(new Double(defaultValue/scale));
		defaultText.addElement(tf.getText());
		tf.setEditable(true);
		firstSlider = false;

    	Panel panel = new Panel();
		GridBagLayout pgrid = new GridBagLayout();
		GridBagConstraints pc  = new GridBagConstraints();
		panel.setLayout(pgrid);
		pc.gridx = 0; pc.gridy = 0;
		pc.gridwidth = 1;
		pc.ipadx = 85;
		pc.anchor = GridBagConstraints.WEST;
		panel.add(s, pc);
		pc.ipadx = 0;  // reset
		// text field
		pc.gridx = 1;
		pc.insets = new Insets(5, 5, 0, 0);
		pc.anchor = GridBagConstraints.EAST;
    	panel.add(tf, pc);

		c.gridx = GridBagConstraints.RELATIVE;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
        c.insets.left = 0;
        c.insets.bottom -= 3;
		add(panel, c);
		if (Recorder.record || macro)
			saveLabel(tf, label);
    }

    /** Adds a Panel to the dialog. */
    public void addPanel(Panel panel) {
    	addPanel(panel, GridBagConstraints.WEST, addToSameRow ? c.insets : getInsets(5,0,0,0));
    }

    /** Adds a Panel to the dialog with custom contraint and insets. The
    	defaults are GridBagConstraints.WEST (left justified) and
    	"new Insets(5, 0, 0, 0)" (5 pixels of padding at the top). */
    public void addPanel(Panel panel, int constraints, Insets insets) {
		if (addToSameRow) {
			c.gridx = GridBagConstraints.RELATIVE;
			addToSameRow = false;
		} else {
			c.gridx = 0; c.gridy++;
		}
		c.gridwidth = 2;
		c.anchor = constraints;
		c.insets = insets;
		add(panel, c);
    }

	/** Adds an image to the dialog. */
    public void addImage(ImagePlus image) {
    	ImagePanel imagePanel = new ImagePanel(image);
    	addPanel(imagePanel);
    	if (imagePanels==null)
    		imagePanels = new Vector();
    	imagePanels.add(imagePanel);
    }


    /** Set the insets (margins), in pixels, that will be
    	used for the next component added to the dialog
        (except components added to the same row with addToSameRow)
    <pre>
    Default insets:
        addMessage: 0,20,0 (empty string) or 10,20,0
        addCheckbox: 15,20,0 (first checkbox) or 0,20,0
        addCheckboxGroup: 10,0,0
        addRadioButtonGroup: 5,10,0
        addNumericField: 5,0,3 (first field) or 0,0,3
        addStringField: 5,0,5 (first field) or 0,0,5
        addChoice: 5,0,5 (first field) or 0,0,5
     </pre>
    */
    public void setInsets(int top, int left, int bottom) {
    	topInset = top;
    	leftInset = left;
    	bottomInset = bottom;
    	customInsets = true;
    }

    /** Makes the next item appear in the same row as the previous.
     *  May be used for addNumericField, addSlider, addChoice, addCheckbox, addStringField,
     *  addMessage, addPanel, and before the showDialog() method
     *  (in the latter case, the buttons appear to the right of the previous item).
     *  Note that addMessage (and addStringField, if its column width is more than 8) use
     *  the remaining width, so it must be the last item of a row.
     */
    public void addToSameRow() {
        addToSameRow = true;
        addToSameRowCalled = true;
    }

    /** Sets a replacement label for the "OK" button. */
    public void setOKLabel(String label) {
		okay.setLabel(label);
    }

    /** Sets a replacement label for the "Cancel" button. */
    public void setCancelLabel(String label) {
    	cancel.setLabel(label);
    }

    /** Sets a replacement label for the "Help" button. */
    public void setHelpLabel(String label) {
    	helpLabel = label;
    }

    /** Unchanged parameters are not recorder in 'smart recording' mode. */
    public void setSmartRecording(boolean smartRecording) {
    	this.smartRecording = smartRecording;
    }

    /** Make this a "Yes No Cancel" dialog. */
    public void enableYesNoCancel() {
    	enableYesNoCancel(" Yes ", " No ");
    }

    /** Make this a "Yes No Cancel" dialog with custom labels. Here is an example:
    	<pre>
        GenericDialog gd = new GenericDialog("YesNoCancel Demo");
        gd.addMessage("This is a custom YesNoCancel dialog");
        gd.enableYesNoCancel("Do something", "Do something else");
        gd.showDialog();
        if (gd.wasCanceled())
            IJ.log("User clicked 'Cancel'");
        else if (gd.wasOKed())
            IJ. log("User clicked 'Yes'");
        else
            IJ. log("User clicked 'No'");
    	</pre>
	*/
	public void enableYesNoCancel(String yesLabel, String noLabel) {
		okay.setLabel(yesLabel);
		if (no != null)
			no.setLabel(noLabel);
		else if (noLabel!=null)
			no = new Button(noLabel);
	}

    /** Do not display "Cancel" button. */
    public void hideCancelButton() {
    	hideCancelButton = true;
    }

	Insets getInsets(int top, int left, int bottom, int right) {
		if (customInsets) {
			customInsets = false;
			return new Insets(topInset, leftInset, bottomInset, 0);
		} else
			return new Insets(top, left, bottom, right);
	}

    /** Add an Object implementing the DialogListener interface. This object will
     * be notified by its dialogItemChanged method of input to the dialog. The first
     * DialogListener will be also called after the user has typed 'OK' or if the
     * dialog has been invoked by a macro; it should read all input fields of the
     * dialog.
     * For other listeners, the OK button will not cause a call to dialogItemChanged;
     * the CANCEL button will never cause such a call.
     * @param dl the Object that wants to listen.
     */
    public void addDialogListener(DialogListener dl) {
        if (dialogListeners == null)
            dialogListeners = new Vector();
        dialogListeners.addElement(dl);
        if (IJ.debugMode) IJ.log("GenericDialog: Listener added: "+dl);
    }

	/** Returns true if the user clicked on "Cancel". */
    public boolean wasCanceled() {
    	if (wasCanceled && !Thread.currentThread().getName().endsWith("Script_Macro$"))
    		Macro.abort();
    	return wasCanceled;
    }

	/** Returns true if the user has clicked on "OK" or a macro is running. */
    public boolean wasOKed() {
    	return wasOKed || macro;
    }

	/** Returns the contents of the next numeric field,
		or NaN if the field does not contain a number. */
    public double getNextNumber() {
		if (numberField==null)
			return -1.0;
		TextField tf = (TextField)numberField.elementAt(nfIndex);
		String theText = tf.getText();
		String label=null;
		if (macro) {
			label = (String)labels.get((Object)tf);
			theText = Macro.getValue(macroOptions, label, theText);
		}
		String originalText = (String)defaultText.elementAt(nfIndex);
		double defaultValue = ((Double)(defaultValues.elementAt(nfIndex))).doubleValue();
		double value;
		boolean skipRecording = false;
		if (theText.equals(originalText)) {
			value = defaultValue;
			if (smartRecording) skipRecording=true;
		} else {
			Double d = getValue(theText);
			if (d!=null)
				value = d.doubleValue();
			else {
				// Is the value a macro variable?
				if (theText.startsWith("&")) theText = theText.substring(1);
				Interpreter interp = Interpreter.getInstance();
				value = interp!=null?interp.getVariable2(theText):Double.NaN;
				if (Double.isNaN(value)) {
					invalidNumber = true;
					errorMessage = "\""+theText+"\" is an invalid number";
					value = Double.NaN;
					if (macro) {
						IJ.error("Macro Error", "Numeric value expected in run() function\n \n"
							+"   Dialog box title: \""+getTitle()+"\"\n"
							+"   Key: \""+label.toLowerCase(Locale.US)+"\"\n"
							+"   Value or variable name: \""+theText+"\"");
					}
				}
			}
		}
		if (recorderOn && !skipRecording) {
			recordOption(tf, trim(theText));
		}
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

	private void recordOption(Object component, String value) {
		String label = (String)labels.get(component);
		if (value.equals("")) value = "[]";
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

 	protected Double getValue(String text) {
 		Double d;
 		try {d = new Double(text);}
		catch (NumberFormatException e){
			d = null;
		}
		return d;
	}

	public double parseDouble(String s) {
		if (s==null) return Double.NaN;
		double value = Tools.parseDouble(s);
		if (Double.isNaN(value)) {
			if (s.startsWith("&")) s = s.substring(1);
			Interpreter interp = Interpreter.getInstance();
			value = interp!=null?interp.getVariable2(s):Double.NaN;
		}
		return value;
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
		String label = labels!=null?(String)labels.get((Object)tf):"";
		if (macro) {
			theText = Macro.getValue(macroOptions, label, theText);
			if (theText!=null && (theText.startsWith("&")||label.toLowerCase(Locale.US).startsWith(theText))) {
				// Is the value a macro variable?
				if (theText.startsWith("&")) theText = theText.substring(1);
				Interpreter interp = Interpreter.getInstance();
				String s = interp!=null?interp.getVariableAsString(theText):null;
				if (s!=null) theText = s;
			}
		}
		if (recorderOn && !label.equals("")) {
			String s = theText;
			if (s!=null&&s.length()>=3&&Character.isLetter(s.charAt(0))&&s.charAt(1)==':'&&s.charAt(2)=='\\')
				s = s.replaceAll("\\\\", "/");  // replace "\" with "/" in Windows file paths
			s = Recorder.fixString(s);
			if (!smartRecording || !s.equals((String)defaultStrings.elementAt(sfIndex)))
				recordOption(tf, s);
			else if (Recorder.getCommandOptions()==null)
				Recorder.recordOption(" ");
		}
		sfIndex++;
		return theText;
    }

  	/** Returns the state of the next checkbox. */
    public boolean getNextBoolean() {
		if (checkbox==null)
			return false;
		Checkbox cb = (Checkbox)(checkbox.elementAt(cbIndex));
		if (recorderOn)
			recordCheckboxOption(cb);
		boolean state = cb.getState();
		if (macro) {
			String label = (String)labels.get((Object)cb);
			String key = Macro.trimKey(label);
			state = isMatch(macroOptions, key+" ");
		}
		cbIndex++;
		return state;
    }

    // Returns true if s2 is in s1 and not in a bracketed literal (e.g., "[literal]")
    boolean isMatch(String s1, String s2) {
    	if (s1.startsWith(s2))
    		return true;
    	s2 = " " + s2;
    	int len1 = s1.length();
    	int len2 = s2.length();
    	boolean match, inLiteral=false;
    	char c;
    	for (int i=0; i<len1-len2+1; i++) {
    		c = s1.charAt(i);
     		if (inLiteral && c==']')
    			inLiteral = false;
    		else if (c=='[')
    			inLiteral = true;
    		if (c!=s2.charAt(0) || inLiteral || (i>1&&s1.charAt(i-1)=='='))
    			continue;
    		match = true;
			for (int j=0; j<len2; j++) {
				if (s2.charAt(j)!=s1.charAt(i+j))
					{match=false; break;}
			}
			if (match) return true;
    	}
    	return false;
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
			if (item!=null && item.startsWith("&")) // value is macro variable
				item = getChoiceVariable(item);
		}
		if (recorderOn)
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
			if (item!=null && item.startsWith("&")) // value is macro variable
				item = getChoiceVariable(item);
			thisChoice.select(item);
			index = thisChoice.getSelectedIndex();
			if (index==oldIndex && !item.equals(oldItem)) {
				// is value a macro variable?
				Interpreter interp = Interpreter.getInstance();
				String s = interp!=null?interp.getStringVariable(item):null;
				if (s==null)
					IJ.error(getTitle(), "\""+item+"\" is not a valid choice for \""+label+"\"");
				else
					item = s;
			}
		}
		if (recorderOn) {
			int defaultIndex = ((Integer)(defaultChoiceIndexes.elementAt(choiceIndex))).intValue();
			if (!(smartRecording&&index==defaultIndex)) {
				String item = thisChoice.getSelectedItem();
				if (!(item.equals("*None*")&&getTitle().equals("Merge Channels")))
					recordOption(thisChoice, thisChoice.getSelectedItem());
			}
		}
		choiceIndex++;
		return index;
    }

  	/** Returns the selected item in the next radio button group. */
    public String getNextRadioButton() {
		if (radioButtonGroups==null)
			return null;
		CheckboxGroup cg = (CheckboxGroup)(radioButtonGroups.elementAt(radioButtonIndex));
		radioButtonIndex++;
		Checkbox checkbox = cg.getSelectedCheckbox();
		String item = "null";
		if (checkbox!=null)
			item = checkbox.getLabel();
		if (macro) {
			String label = (String)labels.get((Object)cg);
			item = Macro.getValue(macroOptions, label, item);
		}
		if (recorderOn)
			recordOption(cg, item);
		return item;
    }

    private String getChoiceVariable(String item) {
		item = item.substring(1);
		Interpreter interp = Interpreter.getInstance();
		String s = interp!=null?interp.getStringVariable(item):null;
		if (s==null) {
			double value = interp!=null?interp.getVariable2(item):Double.NaN;
			if (!Double.isNaN(value)) {
				if ((int)value==value)
					s = ""+(int)value;
				else
					s = ""+value;
			}
		}
		if (s!=null)
			item = s;
		return item;
	}

  	/** Returns the contents of the next text area. */
	public String getNextText() {
		String text = null;
		String key = "text1";
		if (textAreaIndex==0 && textArea1!=null) {
			text = textArea1.getText();
			if (macro)
				text = Macro.getValue(macroOptions, "text1", text);
		} else if (textAreaIndex==1 && textArea2!=null) {
			text = textArea2.getText();
			if (macro)
				text = Macro.getValue(macroOptions, "text2", text);
			key = "text2";
		}
		textAreaIndex++;
		if (recorderOn && text!=null) {
			String text2 = text;
			String cmd = Recorder.getCommand();
			if (cmd!=null && cmd.equals("Calibrate..."))
				text2 = text2.replace('\n',' ');
			text2 = Recorder.fixString(text2);
			Recorder.recordOption(key, text2);
		}
		return text;
	}

	/** Displays this dialog box. */
	public void showDialog() {
		showDialogCalled = true;
		if (macro) {
			dispose();
			recorderOn = Recorder.record && Recorder.recordInMacros;
		} else {
			if (pfr!=null) // prepare preview (not in macro mode): tell the PlugInFilterRunner to listen
			pfr.setDialog(this);
			Panel buttons = new Panel();
			buttons.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
			okay.addActionListener(this);
			okay.addKeyListener(this);
			if (!hideCancelButton) {
				cancel.addActionListener(this);
				cancel.addKeyListener(this);
			}
			if (no != null) {
				no.addActionListener(this);
				no.addKeyListener(this);
			}
			boolean addHelp = helpURL!=null;
			if (addHelp) {
				help = new Button(helpLabel);
				help.addActionListener(this);
				help.addKeyListener(this);
			}
			if (IJ.isWindows() || Prefs.dialogCancelButtonOnRight) {
				buttons.add(okay);
				if (no != null) buttons.add(no);;
				if (!hideCancelButton)
					buttons.add(cancel);
				if (addHelp) buttons.add(help);
			} else {
				if (addHelp) buttons.add(help);
				if (no != null) buttons.add(no);
				if (!hideCancelButton) buttons.add(cancel);
				buttons.add(okay);
			}
			if (addToSameRow) {
				c.gridx = GridBagConstraints.RELATIVE;
			} else {
				c.gridx = 0; c.gridy++;
			}
			c.anchor = GridBagConstraints.EAST;
			c.gridwidth = addToSameRowCalled?GridBagConstraints.REMAINDER:2;
			c.insets = new Insets(15, 0, 0, 0);
			add(buttons, c);
			if (IJ.isMacOSX()&&IJ.isJava18())
				instance = this;
			Font font = getFont();
			if (IJ.debugMode) IJ.log("GenericDialog font: "+fontSizeSet+" "+font);
			if (!fontSizeSet && font!=null && Prefs.getGuiScale()!=1.0) {
				fontSizeSet = true;
				setFont(font.deriveFont((float)(font.getSize()*Prefs.getGuiScale())));
			}
			pack();

			if (okay!=null && numberField==null && stringField==null && checkbox==null
			&& choice==null && slider==null && radioButtonGroups==null && textArea1==null)
				okay.requestFocusInWindow();
			setup();
			if (centerDialog)
				GUI.centerOnImageJScreen(this);
			setVisible(true);					//except for NonBlockingGenericDialog, returns after 'dispose' by OK or Cancel

		}
	}

	@Override
	public void show() {
		super.show();
		if (!showDialogCalled)
			IJ.error("GenericDialog Error", "show() called instead of showDialog()");
	}

	/** For plugins that read their input only via dialogItemChanged, call it at least once, then stop recording */
	void finalizeRecording() {
		if (optionsRecorded)
			return;
		optionsRecorded = true;
		if (!wasCanceled && dialogListeners!=null && dialogListeners.size()>0) {
			resetCounters();
			((DialogListener)dialogListeners.elementAt(0)).dialogItemChanged(this,null);
			recorderOn = false;
		}
		resetCounters();
	}

	@Override
	public void setFont(Font font) {
		super.setFont(!fontSizeSet&&Prefs.getGuiScale()!=1.0?font.deriveFont((float)(font.getSize()*Prefs.getGuiScale())):font);
		fontSizeSet = true;
	}

    /** Reset the counters before reading the dialog parameters */
	void resetCounters() {
		nfIndex = 0;        // prepare for readout
		sfIndex = 0;
		cbIndex = 0;
		choiceIndex = 0;
		textAreaIndex = 0;
		radioButtonIndex = 0;
		invalidNumber = false;
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

  	/** Returns the Vector containing the sliders (Scrollbars). */
  	public Vector getSliders() {
  		return slider;
  	}

  	/** Returns the Vector that contains the RadioButtonGroups. */
  	public Vector getRadioButtonGroups() {
  		return radioButtonGroups;
  	}

  	/** Returns a reference to textArea1. */
  	public TextArea getTextArea1() {
  		return textArea1;
  	}

  	/** Returns a reference to textArea2. */
  	public TextArea getTextArea2() {
  		return textArea2;
  	}

  	/** Returns a reference to the Label or MultiLineLabel created by the
  	 *	last addMessage() call. Otherwise returns null. */
  	public Component getMessage() {
  		return theLabel;
  	}

    /** Returns a reference to the Preview checkbox. */
    public Checkbox getPreviewCheckbox() {
        return previewCheckbox;
    }

    /** Returns 'true' if this dialog has a "Preview" checkbox and it is enabled. */
    public boolean isPreviewActive() {
        return previewCheckbox!=null && previewCheckbox.getState();
    }

	/** Returns references to the "OK" ("Yes"), "Cancel",
		and if present, "No" buttons as an array. */
	public Button[] getButtons() {
  		Button[] buttons = new Button[3];
  		buttons[0] = okay;
  		buttons[1] = cancel;
  		buttons[2] = no;
		return buttons;
  	}

    /** Used by PlugInFilterRunner to provide visable feedback whether preview
    	is running or not by switching from "Preview" to "wait..."
     */
    public void previewRunning(boolean isRunning) {
        if (previewCheckbox!=null) {
            previewCheckbox.setLabel(isRunning ? previewRunning : previewLabel);
            if (IJ.isMacOSX()) repaint();   //workaround OSX 10.4 refresh bug
        }
    }

    /** Display dialog centered on the primary screen. */
    public void centerDialog(boolean b) {
    	centerDialog = b;
    }

    /* Display the dialog at the specified location. */
    public void setLocation(int x, int y) {
    	super.setLocation(x, y);
    	centerDialog = false;
    }

    public void setDefaultString(int index, String str) {
    	if (defaultStrings!=null && index>=0 && index<defaultStrings.size())
    		defaultStrings.set(index, str);
    }

    protected void setup() {
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source==okay || source==cancel | source==no) {
			wasCanceled = source==cancel;
			wasOKed = source==okay;
			dispose();
		} else if (source==help) {
			if (hideCancelButton) {
				if (helpURL!=null && helpURL.equals("")) {
            		notifyListeners(e);
            		return;
				} else {
					wasOKed = true;
					dispose();
				}
			}
			showHelp();
		} else
            notifyListeners(e);
	}

	public void textValueChanged(TextEvent e) {
        notifyListeners(e);
		if (slider==null) return;
		Object source = e.getSource();
		for (int i=0; i<slider.size(); i++) {
			int index = ((Integer)sliderIndexes.get(i)).intValue();
			if (source==numberField.elementAt(index)) {
				TextField tf = (TextField)numberField.elementAt(index);
				double value = Tools.parseDouble(tf.getText());
				if (!Double.isNaN(value)) {
					Scrollbar sb = (Scrollbar)slider.elementAt(i);
					double scale = ((Double)sliderScales.get(i)).doubleValue();
					sb.setValue((int)(value*scale));
				}
			}
		}
	}

	public void itemStateChanged(ItemEvent e) {
        notifyListeners(e);
	}

	public void focusGained(FocusEvent e) {
		Component c = e.getComponent();
		//IJ.log("focusGained: "+c);
		if (c instanceof TextField)
			((TextField)c).selectAll();
	}

	public void focusLost(FocusEvent e) {
		Component c = e.getComponent();
		if (c instanceof TextField)
			((TextField)c).select(0,0);
	}

	public void keyPressed(KeyEvent e) {
		Component component = e.getComponent();
		int keyCode = e.getKeyCode();
		IJ.setKeyDown(keyCode);
		if ((component instanceof Scrollbar) && (keyCode==KeyEvent.VK_LEFT||keyCode==KeyEvent.VK_RIGHT)) {
			Scrollbar sb = (Scrollbar)component;
			int value = sb.getValue();
			if (keyCode==KeyEvent.VK_RIGHT)
				sb.setValue(value+1);
			else
				sb.setValue(value-1);
			for (int i=0; i<slider.size(); i++) {
				if (sb==slider.elementAt(i)) {
					int index = ((Integer)sliderIndexes.get(i)).intValue();
					TextField tf = (TextField)numberField.elementAt(index);
					double scale = ((Double)sliderScales.get(i)).doubleValue();
					int digits = ((Integer)sliderDigits.get(i)).intValue();
					tf.setText(""+IJ.d2s(sb.getValue()/scale,digits));
				}
			}
			notifyListeners(e);
			return;
		}
		if (keyCode==KeyEvent.VK_ENTER && textArea1==null && okay!=null && okay.isEnabled()) {
			wasOKed = true;
			if (IJ.isMacOSX())
				accessTextFields();
			dispose();
		} else if (keyCode==KeyEvent.VK_ESCAPE) {
			wasCanceled = true;
			dispose();
			IJ.resetEscape();
		} else if (keyCode==KeyEvent.VK_W && (e.getModifiers()&Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())!=0) {
			wasCanceled = true;
			dispose();
		}
	}

	void accessTextFields() {
		if (stringField!=null) {
			for (int i=0; i<stringField.size(); i++)
				((TextField)(stringField.elementAt(i))).getText();
		}
		if (numberField!=null) {
			for (int i=0; i<numberField.size(); i++)
				((TextField)(numberField.elementAt(i))).getText();
		}
	}

	public void keyReleased(KeyEvent e) {
		int keyCode = e.getKeyCode();
		IJ.setKeyUp(keyCode);
		int flags = e.getModifiers();
		boolean control = (flags & KeyEvent.CTRL_MASK) != 0;
		boolean meta = (flags & KeyEvent.META_MASK) != 0;
		boolean shift = (flags & e.SHIFT_MASK) != 0;
		if (keyCode==KeyEvent.VK_G && shift && (control||meta))
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
				int index = ((Integer)sliderIndexes.get(i)).intValue();
				TextField tf = (TextField)numberField.elementAt(index);
				double scale = ((Double)sliderScales.get(i)).doubleValue();
				int digits = ((Integer)sliderDigits.get(i)).intValue();
				tf.setText(""+IJ.d2s(sb.getValue()/scale,digits));
			}
		}
	}

    /** Notify any DialogListeners of changes having occurred
     *  If a listener returns false, do not call further listeners and disable
     *  the OK button and preview Checkbox (if it exists).
     *  For PlugInFilters, this ensures that the PlugInFilterRunner,
     *  which listens as the last one, is not called if the PlugInFilter has
     *  detected invalid parameters. Thus, unnecessary calling the run(ip) method
     *  of the PlugInFilter for preview is avoided in that case.
     */
    private void notifyListeners(AWTEvent e) {
        if (dialogListeners==null)
        	return;
        boolean everythingOk = true;
        for (int i=0; everythingOk && i<dialogListeners.size(); i++) {
            try {
                resetCounters();
                if (this instanceof NonBlockingGenericDialog)
                	Recorder.resetCommandOptions();
                if (!((DialogListener)dialogListeners.elementAt(i)).dialogItemChanged(this, e))
                    everythingOk = false;         // disable further listeners if false (invalid parameters) returned
            } catch (Exception err) {             // for exceptions, don't cover the input by a window but
                IJ.beep();                          // show them at in the "Log"
                IJ.log("ERROR: "+err+"\nin DialogListener of "+dialogListeners.elementAt(i)+
                "\nat "+(err.getStackTrace()[0])+"\nfrom "+(err.getStackTrace()[1]));  //requires Java 1.4
            }
        }
        boolean workaroundOSXbug = IJ.isMacOSX() && okay!=null && !okay.isEnabled() && everythingOk;
        if (everythingOk && recorderOn)
			optionsRecorded = true;
        if (previewCheckbox!=null)
            previewCheckbox.setEnabled(everythingOk);
        if (okay!=null)
            okay.setEnabled(everythingOk);
        if (workaroundOSXbug)
        	repaint(); // OSX 10.4 bug delays update of enabled until the next input
    }

	public void repaint() {
		super.repaint();
		if (imagePanels!=null) {
			for (int i=0; i<imagePanels.size(); i++)
				((ImagePanel)imagePanels.get(i)).repaint();
		}
	}

	public void paint(Graphics g) {
		super.paint(g);
		if (firstPaint && IJ.isMacOSX() && IJ.isJava18()) { // fix for incompletely drawn dialogs on Macs
			EventQueue.invokeLater(new Runnable() {
				public void run() {
					IJ.wait(50);
					Dimension size = getSize();
					if (size!=null)
						setSize(size.width+2,size.height+2);
					firstPaint = false;
				}
			});
		}
	}

    public void windowClosing(WindowEvent e) {
		wasCanceled = true;
		dispose();
    }

    /** Adds a "Help" button that opens the specified URL in the default browser.
    	With v1.46b or later, displays an HTML formatted message if
    	'url' starts with "<html>". There is an example at
    	http://imagej.nih.gov/ij/macros/js/DialogWithHelp.js
    */
    public void addHelp(String url) {
    	helpURL = url;
    }

	void showHelp() {
		if (helpURL.startsWith("<html>")) {
			if (this instanceof NonBlockingGenericDialog)
				new HTMLDialog("", helpURL, false); // non blocking
			else
				new HTMLDialog(this, "", helpURL); //modal
		} else {
			String macro = "run('URL...', 'url="+helpURL+"');";
			new MacroRunner(macro);
		}
	}

	protected boolean isMacro() {
		return macro;
	}

	public static GenericDialog getInstance() {
		return instance;
	}

	/** Closes the dialog; records the options */
	public void dispose() {
		super.dispose();
		instance = null;

		if (!macro) {
			recorderOn = Recorder.record;
			IJ.wait(25);
		}
		resetCounters();
		finalizeRecording();
		resetCounters();
	}

	 /** Returns a reference to the label of the most recently
    	added numeric field, string field, choice or slider. */
    public Label getLabel() {
    	return lastLabelAdded;
    }

    public void windowActivated(WindowEvent e) {}
    public void windowOpened(WindowEvent e) {}
    public void windowClosed(WindowEvent e) {}
    public void windowIconified(WindowEvent e) {}
    public void windowDeiconified(WindowEvent e) {}
    public void windowDeactivated(WindowEvent e) {}

}
