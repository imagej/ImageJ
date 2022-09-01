package ij.plugin.frame;
import ij.*;
import ij.plugin.*;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;

/** Displays the ImageJ "Channels" dialog. */
public class Channels extends PlugInDialog implements PlugIn, ItemListener, ActionListener {

	private static final String[] modes = {"Composite", "Color", "Grayscale", "---------",
		"Composite Max", "Composite Min", "Composite Invert"};
	private static final int COMP=0, COLOR=1, GRAY=2, DIVIDER=3, MAX=4, MIN=5, INVERT=6;
	private static String[] menuItems = {"Make Composite", "Create RGB Image", "Split Channels", "Merge Channels...",
		"Show LUT", "Invert LUTs", "-", "Red", "Green", "Blue", "Cyan", "Magenta", "Yellow", "Grays"};
	private static String moreLabel = "More "+'\u00bb';

	public static final String help = "<html>"
	+"<h1>Composite Display Modes</h1>"
	+"<font size=+1>"
	+"<ul>"
	+"<li> <u>Composite</u> -  Effectively creates an RGB image for each channel, based on its LUT, and then adds the red, green and blue values to create the displayed image. The values are clipped at 255, which can cause saturation. For an example, open the \"Neuron (5 channel)\" sample image and compare the <i>Composite</i> and <i>Composite Max</i> display modes. This is the original ImageJ composite mode.<br>"
	+"<li> <u>Composite Max</u> - Similar to <i>Composite</i>, except uses the maximum of the red, green and blue values across all channels.<br>"
	+"<li> <u>Composite Min</u> - Similar to <i>Composite</i>, except uses the minimum of the red, green and blue values across all channels. This mode, and <i>Composite Invert</i>, require that the channels have inverting (white background) LUTs. Linear non-inverting LUTs that use a single color are automatically inverted.<br>"
	+"<li> <u>Composite Invert</u> - Similar to <i>Composite</i>, except the red, green and blue values are effectively subracted from 255. The values are clipped at 0, which can cause saturation.<br>"
	+"</ul>"
	+"<h1>More"+'\u00bb'+"Commands</h1>"
	+"<font size=+1>"
	+"<ul>"
	+"<li> <u>Make Composite</u> - Converts an RGB image into a three channel composite image.<br>"
	+"<li> <u>Create RGB image</u> - Creates an RGB version of a multichannel image.<br>"
	+"<li> <u>Split Channels</u> - Splits a multichannel image into separate images.<br>"
	+"<li> <u>Merge Channels</u> - Combines multiple images into a single multichannel image.<br>"
	+"<li> <u>Show LUT</u> - Displays a plot of the current channel's LUT. Click \"List\" to create a table of the RGB values for each of the LUT's 256 entries.<br>"
	+"<li> <u>Invert LUTs</u> - Inverts the LUTs of all the channels of a composite image. Black background LUTs with ascending RGB values are converted to inverting LUTs (descending RGB values) with white backgrounds, or vis versa. Does nothing if the LUT is not linear or it uses more than one color. This command runs the macro at http://wsr.imagej.net/macros/Invert_All_LUTs.txt.<br>"
	+"<li> <u>Red, Green, Blue, Cyan, Magenta, Yellow, Grays</u> - Updates the current channel's LUT so that it uses the selected color.<br>"
	+"</ul>"
	+"<br>"
	+"The <i>\"Channels & Colors\"</i> chapter of Pete Bankhead's \"<i>Introduction to Bioimage Analysis</i>\" (https://bioimagebook.github.io) is a good introduction to multichannel images and LUTs.<br>"	
	+"<br>"	
	+"The macro at http://wsr.imagej.net/macros/CompositeProjection.ijm uses the \"Invert LUTs\", \"RGB Stack\", \"Z Project\" and \"Invert\" commands to reproduce the four composite display modes.<br>"
	+"</font>";

	private Choice choice;
	private Checkbox[] checkbox;
	private Button helpButton, moreButton;
	private static Channels instance;
	private int id;
	private static Point location;
	private PopupMenu pm;

	public Channels() {
		super("Channels");
		if (instance!=null) {
			instance.toFront();
			return;
		}
		ImageJ ij = IJ.getInstance();
		WindowManager.addWindow(this);
		instance = this;
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();
		setLayout(gridbag);
		int y = 0;
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 1;
		c.fill = GridBagConstraints.BOTH;
		c.anchor = GridBagConstraints.CENTER;
		int margin = 32;
		if (IJ.isMacOSX())
			margin = 20;
		c.insets = new Insets(10, margin, 10, margin);
		choice = new Choice();
		for (int i=0; i<modes.length; i++)
			choice.addItem(modes[i]);
		choice.select(0);
		choice.addItemListener(this);
		if (ij!=null) choice.addKeyListener(ij);
		add(choice, c);

		CompositeImage ci = getImage();
		int nCheckBoxes = ci!=null?ci.getNChannels():3;
		if (nCheckBoxes>CompositeImage.MAX_CHANNELS)
			nCheckBoxes = CompositeImage.MAX_CHANNELS;
		checkbox = new Checkbox[nCheckBoxes];
		for (int i=0; i<nCheckBoxes; i++) {
			checkbox[i] = new Checkbox("Channel "+(i+1), true);
			c.insets = new Insets(0, 25, i<nCheckBoxes-1?0:10, 5);
			c.gridy = y++;
			add(checkbox[i], c);
			checkbox[i].addItemListener(this);
			checkbox[i].addKeyListener(ij);
		}

		c.insets = new Insets(0, 15, 10, 15);
		c.fill = GridBagConstraints.NONE;
		c.gridwidth = 2;
		c.gridy = y++;
		Panel panel = new Panel();
		int hgap = IJ.isMacOSX()?1:5;
		panel.setLayout(new FlowLayout(FlowLayout.RIGHT,hgap,0));
		helpButton = new TrimmedButton("Help",IJ.isMacOSX()?10:0);//new Button("Help");
		helpButton.addActionListener(this);
		helpButton.addKeyListener(ij);
		panel.add(helpButton, c);
		add(panel, c);
		moreButton = new TrimmedButton(moreLabel,IJ.isMacOSX()?10:0);//new Button(moreLabel);
		moreButton.addActionListener(this);
		moreButton.addKeyListener(ij);
		panel.add(moreButton, c);
		update();

		pm = new PopupMenu();
		GUI.scalePopupMenu(pm);
		for (int i=0; i<menuItems.length; i++)
			addPopupItem(menuItems[i]);
		add(pm);

		addKeyListener(ij);  // ImageJ handles keyboard shortcuts
		setResizable(false);
		GUI.scale(this);
		pack();
		if (location==null) {
			GUI.centerOnImageJScreen(this);
			location = getLocation();
		} else
			setLocation(location);
		show();
	}
	
	public void update() {
		CompositeImage ci = getImage();
		if (ci==null || checkbox==null)
			return;
		int n = checkbox.length;
		int nChannels = ci.getNChannels();
		if (nChannels!=n && nChannels<=CompositeImage.MAX_CHANNELS) {
			instance = null;
			location = getLocation();
			close();
			new Channels();
			return;
		}
		boolean[] active = ci.getActiveChannels();
		for (int i=0; i<checkbox.length; i++)
			checkbox[i].setState(active[i]);
		int index = 0;
		
		String cmode = ci.getProp("CompositeProjection");
		int cindex = COMP;
		if (cmode!=null) {			
			if (cmode.contains("Max")||cmode.contains("max")) cindex=MAX;
			if (cmode.contains("Min")||cmode.contains("min")) cindex=MIN;
			if (cmode.contains("Invert")||cmode.contains("invert")) cindex=INVERT;
		}
		switch (ci.getMode()) {
			case IJ.COMPOSITE: index=cindex; break;
			case IJ.COLOR: index=COLOR; break;
			case IJ.GRAYSCALE: index=GRAY; break;
		}
		choice.select(index);
	}
	
	public static void updateChannels() {
		if (instance!=null)
			instance.update();
	}
	
	void addPopupItem(String s) {
		MenuItem mi=new MenuItem(s);
		mi.addActionListener(this);
		pm.add(mi);
	}

	CompositeImage getImage() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null || !imp.isComposite())
			return null;
		else
			return (CompositeImage)imp;
	}

	public void itemStateChanged(ItemEvent e) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) return;
		if (!imp.isComposite()) {
			int channels = imp.getNChannels();
			if (channels==1 && imp.getStackSize()<=4)
				channels = imp.getStackSize();
			if (imp.getBitDepth()==24 || (channels>1&&channels<CompositeImage.MAX_CHANNELS)) {
				GenericDialog gd = new GenericDialog(imp.getTitle());
				gd.addMessage("Convert to multichannel composite image?");
				gd.showDialog();
				if (gd.wasCanceled())
					return;
				else
					IJ.doCommand("Make Composite");					
			} else {
				IJ.error("Channels", "A composite image is required (e.g., "+moreLabel+" Open HeLa Cells),\nor create one using "+moreLabel+" Make Composite.");
				return;
			}
		}
		if (!imp.isComposite()) return;
		CompositeImage ci = (CompositeImage)imp;
		Object source = e.getSource();
		if (source==choice) {
			int index = ((Choice)source).getSelectedIndex();
			String cstr = null;
			int cmode = IJ.COMPOSITE;
			switch (index) {
				case COMP: cmode=IJ.COMPOSITE; cstr="Sum"; break;
				case COLOR: cmode=IJ.COLOR; break;
				case GRAY: cmode=IJ.GRAYSCALE; break;
				case DIVIDER: cmode=IJ.COMPOSITE; cstr="Sum"; break;
				case MAX: cmode=IJ.COMPOSITE; ; cstr="Max"; break;
				case MIN: cmode=IJ.COMPOSITE; ; cstr="Min"; break;
				case INVERT: cmode=IJ.COMPOSITE; ; cstr="Invert"; break;
			}
			if (cstr!=null && !(cstr.equals("Sum")&&ci.getProp("CompositeProjection")==null))
				ci.setProp("CompositeProjection", cstr);
			//IJ.log(cmode+" "+cstr+" "+imp.isInvertedLut());
			if (cmode==IJ.COMPOSITE && (("Min".equals(cstr)||"Invert".equals(cstr)) && !imp.isInvertedLut())
			|| ("Max".equals(cstr)||"Sum".equals(cstr)) && imp.isInvertedLut())
				IJ.runMacroFile("ij.jar:InvertAllLuts", null);	
			ci.setMode(cmode);
			ci.updateAndDraw();
			if (Recorder.record) {
				String mode = null;
				if (index!=DIVIDER && Recorder.scriptMode()) {
					switch (index) {
						case COMP: case MAX: case MIN: case INVERT: mode="IJ.COMPOSITE"; break;
						case COLOR: mode="IJ.COLOR"; break;
						case GRAY: mode="IJ.GRAYSCALE"; break;
					}
					cstr="\""+cstr+"\"";
					Recorder.recordCall("imp.setProp(\"CompositeProjection\", "+cstr+");");
					Recorder.recordCall("imp.setDisplayMode("+mode+");");
				} else {
					switch (index) {
						case COMP: case MAX: case MIN: case INVERT: mode="composite"; break;
						case COLOR: mode="color"; break;
						case GRAY: mode="grayscale"; break;
					}
					Recorder.recordString("Property.set(\"CompositeProjection\", \""+cstr+"\");\n");
					Recorder.record("Stack.setDisplayMode", mode);
				}
			}
		} else if (source instanceof Checkbox) {
			for (int i=0; i<checkbox.length; i++) {
				Checkbox cb = (Checkbox)source;
				if (cb==checkbox[i]) {
					if (ci.getMode()==IJ.COMPOSITE) {
						boolean[] active = ci.getActiveChannels();
						active[i] = cb.getState();
						if (Recorder.record) {
							String str = "";
							for (int c=0; c<ci.getNChannels(); c++)
								str += active[c]?"1":"0";
							if (Recorder.scriptMode())
								Recorder.recordCall("imp.setActiveChannels(\""+str+"\");");
							else
								Recorder.record("Stack.setActiveChannels", str);
						}
					} else {
						imp.setPosition(i+1, imp.getSlice(), imp.getFrame());
						if (Recorder.record) {
							if (Recorder.scriptMode())
								Recorder.recordCall("imp.setC("+(i+1)+");");
							else
								Recorder.record("Stack.setChannel", i+1);
						}
					}
					ci.updateAndDraw();
					return;
				}
			}
		}
	}
	
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source==helpButton) {
			new HTMLDialog("Channels", help, false);
			return;
		}
		String command = e.getActionCommand();
		if (command==null) return;
		if (command.equals(moreLabel)) {
			Point bloc = moreButton.getLocation();
			pm.show(this, bloc.x, bloc.y);
		} else if (command.equals("Create RGB Image"))
			IJ.doCommand("Stack to RGB");
		else
			IJ.doCommand(command);
	}
	
	/** Obsolete; always returns null. */
	public static Frame getInstance() {
		return null;
	}
		
	public void close() {
		super.close();
		instance = null;
		location = getLocation();
	}
	
}
