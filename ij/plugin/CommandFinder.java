/** This plugin implements the Plugins/Utilities/Find Commands 
    command. It provides an easy user interface to finding commands 
    you might know the name of without having to go through
    all the menus.  If you type a part of a command name, the box
    below will only show commands that match that substring (case
    insensitively).  If only a single command matches then that
    command can be run by hitting Enter.  If multiple commands match,
    they can be selected by selecting with the mouse and clicking
    "Run"; alternatively hitting the up or down arrows will move the
    keyboard focus to the list and the selected command can be run
    with Enter. When the list has focus, it is also possible to use
    keyboard "scrolling": E.g., pressing "H" will select the first
    command starting with the char "H". Pressing "H" again will select
    the next row starting with the char "H", etc., looping between all
    "H" starting commands. Double-clicking on a command in the list
    should also run the appropriate command.

    @author Mark Longair <mark-imagej@longair.net>
    @author Johannes Schindelin <johannes.schindelin@gmx.de>
    @author Curtis Rueden <ctrueden@wisc.edu>
    @author Tiago Ferreira <tiago.ferreira@mail.mcgill.ca>

 */

package ij.plugin;
import ij.*;
import ij.text.*;
import ij.plugin.frame.Editor;
import ij.gui.HTMLDialog;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.File;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import javax.swing.event.DocumentEvent;


public class CommandFinder implements PlugIn, ActionListener, WindowListener, KeyListener, ItemListener, MouseListener {

	private static final int TABLE_WIDTH = 640;
	private static final int TABLE_ROWS = 18;
	private int multiClickInterval;
	private long lastClickTime;
	private static JFrame frame;
	private JTextField prompt;
	private JScrollPane scrollPane;
	private JButton runButton, sourceButton, closeButton, helpButton;
	private JCheckBox closeCheckBox;
	private Hashtable commandsHash;
	private String [] commands;
	private static boolean closeWhenRunning = Prefs.get("command-finder.close", false);
	private JTable table;
	private TableModel tableModel;
	private int lastClickedRow;

	public CommandFinder() {
		Toolkit toolkit=Toolkit.getDefaultToolkit();
		Integer interval=(Integer)toolkit.getDesktopProperty("awt.multiClickInterval");
		if (interval==null)
			// Hopefully 300ms is a sensible default when the property
			// is not available.
			multiClickInterval = 300;
		else
			multiClickInterval = interval.intValue();
	}

	class CommandAction {
		CommandAction(String classCommand, MenuItem menuItem, String menuLocation) {
			this.classCommand = classCommand;
			this.menuItem = menuItem;
			this.menuLocation = menuLocation;
		}
		String classCommand;
		MenuItem menuItem;
		String menuLocation;
		public String toString() {
			return "classCommand: " + classCommand + ", menuItem: "+menuItem+", menuLocation: "+menuLocation;
		}
	}

	protected String[] makeRow(String command, CommandAction ca) {
		String[] result = new String[tableModel.getColumnCount()];
		result[0] = command;
		if (ca.menuLocation != null)
			result[1] = ca.menuLocation;
		if (ca.classCommand != null)
			result[2] = ca.classCommand;
		String jarFile = Menus.getJarFileForMenuEntry(command);
		if (jarFile != null)
			result[3] = jarFile;
		return result;
	}

	protected void populateList(String matchingSubstring) {
		String substring = matchingSubstring.toLowerCase();
		ArrayList list = new ArrayList();
		int count = 0;
		for (int i=0; i<commands.length; ++i) {
			String commandName = commands[i];
			String command = commandName.toLowerCase();
			CommandAction ca = (CommandAction)commandsHash.get(commandName);
			String menuPath = ca.menuLocation;
			if (menuPath==null)
				menuPath = "";
			menuPath = menuPath.toLowerCase();
			if (command.indexOf(substring)>=0 || menuPath.indexOf(substring)>=0) {
				String[] row = makeRow(commandName, ca);
				list.add(row);
			}
		}
		tableModel.setData(list);
		prompt.requestFocus();
	}

	public void actionPerformed(ActionEvent ae) {
		Object source = ae.getSource();
		if (source==runButton) {
			int row = table.getSelectedRow();
			if (row<0) {
				error("Please select a command to run");
				return;
			}
			runCommand(tableModel.getCommand(row));
		} else if (source==sourceButton) {
			int row = table.getSelectedRow();
			if (row<0) {
				error("Please select a command");
				return;
			}
			showSource(tableModel.getCommand(row));
		} else if (source == closeButton) {
			closeWindow();
		} else if (source == helpButton) {
			String text = "<html>Shortcuts:<br>"
				+ "&emsp;&uarr; &darr;&ensp; Select items<br>"
				+ "&emsp;&crarr;&emsp; Open item<br>"
				+ "&ensp;A-Z&ensp; Alphabetic scroll<br>"
				+ "&emsp;&#9003;&emsp;Activate search field</html>";
			new HTMLDialog("", text);
		}
	}

	public void itemStateChanged(ItemEvent ie) {
		populateList(prompt.getText());
	}

	public void mouseClicked(MouseEvent e) {
		long now=System.currentTimeMillis();
		int row = table.getSelectedRow();
		// Is this fast enough to be a double-click?
		long thisClickInterval = now-lastClickTime;
		if (thisClickInterval<multiClickInterval) {
			if (row>=0 && lastClickedRow>=0 && row==lastClickedRow)
				runCommand(tableModel.getCommand(row));
		}
		lastClickTime = now;
		lastClickedRow = row;
	}

	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	
	void showSource(String cmd) {
		if (showMacro(cmd))
			return;
		Hashtable table = Menus.getCommands();
		String className = (String)table.get(cmd);
		if (IJ.debugMode)
			IJ.log("showSource: "+cmd+"   "+className);
		if (className==null) {
			error("No source associated with this command:\n  "+cmd);
			return;
		}
		int mstart = className.indexOf("ij.plugin.Macro_Runner(\"");
		if (mstart>=0) { // macro or script
			int mend = className.indexOf("\")");
			if (mend==-1)
				return;
			String macro = className.substring(mstart+24,mend);
			IJ.open(IJ.getDirectory("plugins")+macro);
			return;
		}
		if (className.endsWith("\")")) {
			int openParen = className.lastIndexOf("(\"");
			if (openParen>0)
				className = className.substring(0, openParen);
		}
		if (className.startsWith("ij.")) {
			className = className.replaceAll("\\.", "/");
			IJ.runPlugIn("ij.plugin.BrowserLauncher", IJ.URL+"/source/"+className+".java");
			return;
		}
		className = IJ.getDirectory("plugins")+className.replaceAll("\\.","/");
		String path = className+".java";
		File f = new File(path);
		if (f.exists()) {
			IJ.open(path);
			return;
		}
		error("Unable to display source for this plugin:\n  "+className);
	}
	
	private boolean showMacro(String cmd) {
		String name = null;
		if (cmd.equals("Display LUTs"))
			name = "ShowAllLuts.txt";
		else if (cmd.equals("Search..."))
			name = "Search.txt";
		if (name==null)
			return false;
		String code = BatchProcessor.openMacroFromJar(name);
		if (code!=null) {
			Editor ed = new Editor();
			ed.setSize(700, 600);
			ed.create(name, code);
			return true;
		}
		return false;
	}

	private void error(String msg) {
		IJ.error("Command Finder", msg);
	}

	protected void runCommand(String command) {
		IJ.showStatus("Running command "+command);
		IJ.doCommand(command);
		closeWhenRunning = closeCheckBox.isSelected();
		if (closeWhenRunning)
			closeWindow();
	}

	public void keyPressed(KeyEvent ke) {
		int key = ke.getKeyCode();
		int flags = ke.getModifiers();
		int items = tableModel.getRowCount();
		Object source = ke.getSource();
		boolean meta = ((flags&KeyEvent.META_MASK) != 0) || ((flags&KeyEvent.CTRL_MASK) != 0);
		if (key==KeyEvent.VK_ESCAPE || (key==KeyEvent.VK_W&&meta)) {
			closeWindow();
		} else if (source==prompt) {
			/* If you hit enter in the text field, and
			   there's only one command that matches, run
			   that: */
			if (key==KeyEvent.VK_ENTER) {
				if (1==items)
					runCommand(tableModel.getCommand(0));
			}
			/* If you hit the up or down arrows in the
			   text field, move the focus to the
			   table and select the row at the
			   bottom or top. */
			int index = -1;
			if (key==KeyEvent.VK_UP) {
				index = table.getSelectedRow() - 1;
				if (index<0)
					index = items - 1;
			} else if (key==KeyEvent.VK_DOWN) {
				index = table.getSelectedRow() + 1;
				if (index>=items)
					index = Math.min(items-1, 0);
			}
			if (index>=0) {
				table.requestFocus();
				//completions.ensureIndexIsVisible(index);
				table.setRowSelectionInterval(index, index);
			}
		} else if (key==KeyEvent.VK_BACK_SPACE) {
			/* If someone presses backspace they probably want to
			   remove the last letter from the search string, so
			   switch the focus back to the prompt: */
			prompt.requestFocus();
		} else if (source==table) {
			/* If you hit enter with the focus in the table, run the selected command */
			if (key==KeyEvent.VK_ENTER) {
				ke.consume();
				int row = table.getSelectedRow();
				if (row>=0)
					runCommand(tableModel.getCommand(row));
			/* Loop through the list using the arrow keys */
			} else if (key == KeyEvent.VK_UP) {
				if (table.getSelectedRow() == 0)
					table.setRowSelectionInterval(tableModel.getRowCount() - 1, tableModel.getRowCount() - 1);
			} else if (key == KeyEvent.VK_DOWN) {
				if (table.getSelectedRow() == tableModel.getRowCount() - 1)
					table.setRowSelectionInterval(0, 0);
			}
		}
	}

	public void keyReleased(KeyEvent ke) { }

	public void keyTyped(KeyEvent ke) { }

	class PromptDocumentListener implements DocumentListener {
		public void insertUpdate(DocumentEvent e) {
			populateList(prompt.getText());
		}
		public void removeUpdate(DocumentEvent e) {
			populateList(prompt.getText());
		}
		public void changedUpdate(DocumentEvent e) {
			populateList(prompt.getText());
		}
	}

	/* This function recurses down through a menu, adding to
	   commandsHash the location and MenuItem of any items it
	   finds that aren't submenus. */

	public void parseMenu(String path, Menu menu) {
		int n=menu.getItemCount();
		for (int i=0; i<n; ++i) {
			MenuItem m=menu.getItem(i);
			String label=m.getActionCommand();
			if (m instanceof Menu) {
				Menu subMenu=(Menu)m;
				parseMenu(path+">"+label,subMenu);
			} else {
				String trimmedLabel = label.trim();
				if (trimmedLabel.length()==0 || trimmedLabel.equals("-"))
					continue;
				CommandAction ca=(CommandAction)commandsHash.get(label);
				if( ca == null )
					commandsHash.put(label, new CommandAction(null,m,path));
				else {
					ca.menuItem=m;
					ca.menuLocation=path;
				}
				CommandAction caAfter=(CommandAction)commandsHash.get(label);
			}
		}
	}

	/* Finds all the top level menus from the menu bar and
	   recurses down through each. */

	public void findAllMenuItems() {
		MenuBar menuBar = Menus.getMenuBar();
		int topLevelMenus = menuBar.getMenuCount();
		for (int i=0; i<topLevelMenus; ++i) {
			Menu topLevelMenu=menuBar.getMenu(i);
			parseMenu(topLevelMenu.getLabel(), topLevelMenu);
		}
	}

	/**
	 * Displays the Command Finder dialog. If a Command Finder window is
	 * already being displayed and <tt>initialSearch</tt> contains a valid
	 * query, it will be closed and a new one displaying the new search
	 * will be rebuilt at the same screen location.
	 *
	 * @param initialSearch
	 *            The search string that populates Command Finder's search
	 *            field. It is ignored if contains an invalid query (ie, if
	 *            it is either <tt>null</tt> or <tt>empty</tt>).
	 */
	public void run(String initialSearch) {
		if (frame!=null) {
			if (initialSearch!=null && !initialSearch.isEmpty()) {
				frame.dispose(); // Rebuild dialog with new search string
			} else {
				WindowManager.toFront(frame);
				return;
			}
		}
		commandsHash = new Hashtable();

		/* Find the "normal" commands; those which are
		   registered plugins: */
		Hashtable realCommandsHash = (Hashtable)(ij.Menus.getCommands().clone());
		Set realCommandSet = realCommandsHash.keySet();
		for (Iterator i = realCommandSet.iterator();
		     i.hasNext();) {
			String command = (String)i.next();
			// Some of these are whitespace only or separators - ignore them:
			String trimmedCommand = command.trim();
			if (trimmedCommand.length()>0 && !trimmedCommand.equals("-")) {
				commandsHash.put(command,
						 new CommandAction((String)realCommandsHash.get(command), null, null));
			}
		}

		/* There are some menu items that don't correspond to
		   plugins, such as those added by RefreshScripts, so
		   look through all the menus as well: */
		findAllMenuItems();

		/* Sort the commands, generate list labels for each
		   and put them into a hash: */
		commands = (String[])commandsHash.keySet().toArray(new String[0]);
		Arrays.sort(commands);

		/* The code below just constructs the dialog: */
		ImageJ imageJ = IJ.getInstance();
		frame = new JFrame("Command Finder") {
			public void setVisible(boolean visible) {
				if (visible)
					WindowManager.addWindow(this);
				super.setVisible(visible);
			}
			public void dispose() {
				WindowManager.removeWindow(this);
				Prefs.set("command-finder.close", closeWhenRunning);
				frame = null;
				super.dispose();
			}
		};
		Container contentPane = frame.getContentPane();
		contentPane.setLayout(new BorderLayout());
		frame.addWindowListener(this);
		if (imageJ!=null && !IJ.isMacOSX()) {
			Image img = imageJ.getIconImage();
			if (img!=null)
				try {frame.setIconImage(img);} catch (Exception e) {}
		}


		closeCheckBox = new JCheckBox("Close window after running command", closeWhenRunning);
		closeCheckBox.addItemListener(this);

		JPanel northPanel = new JPanel(new BorderLayout());
		northPanel.add(new JLabel(" Search:"), BorderLayout.WEST);
		prompt = new JTextField("", 20);
		prompt.getDocument().addDocumentListener(new PromptDocumentListener());
		prompt.addKeyListener(this);
		northPanel.add(prompt);
		contentPane.add(northPanel, BorderLayout.NORTH);

		tableModel = new TableModel();
		table = new JTable(tableModel);
		//table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setRowSelectionAllowed(true);
		table.setColumnSelectionAllowed(false);
		//table.setAutoCreateRowSorter(true);
		tableModel.setColumnWidths(table.getColumnModel());
		Dimension dim = new Dimension(TABLE_WIDTH, table.getRowHeight()*TABLE_ROWS);
		table.setPreferredScrollableViewportSize(dim);
		table.addKeyListener(this);
		table.addMouseListener(this);

		// Auto-scroll table using keystrokes
		table.addKeyListener(new KeyAdapter() {
			public void keyTyped(final KeyEvent evt) {
				if (evt.isControlDown() || evt.isMetaDown())
					return;
				final int nRows = tableModel.getRowCount();
				final char ch = Character.toLowerCase(evt.getKeyChar());
				if (!Character.isLetterOrDigit(ch)) {
					return; // Ignore searches for non alpha-numeric characters
				}
				final int sRow = table.getSelectedRow();
				for (int row = (sRow+1) % nRows; row != sRow; row = (row+1) % nRows) {
					final String rowData = tableModel.getValueAt(row, 0).toString();
					final char rowCh = Character.toLowerCase(rowData.charAt(0));
					if (ch == rowCh) {
						table.setRowSelectionInterval(row, row);
						table.scrollRectToVisible(table.getCellRect(row, 0, true));
						break;
					}
				}
			}
		});

		scrollPane = new JScrollPane(table);
		if (initialSearch==null)
			initialSearch = "";
		prompt.setText(initialSearch);
		populateList(initialSearch);
		contentPane.add(scrollPane, BorderLayout.CENTER);

		runButton = new JButton("Run");
		sourceButton = new JButton("Source");
		closeButton = new JButton("Close");
		helpButton = new JButton("Help");
		runButton.addActionListener(this);
		sourceButton.addActionListener(this);
		closeButton.addActionListener(this);
		helpButton.addActionListener(this);
		runButton.addKeyListener(this);
		sourceButton.addKeyListener(this);
		closeButton.addKeyListener(this);
		helpButton.addKeyListener(this);

		JPanel southPanel = new JPanel();
		southPanel.setLayout(new BorderLayout());

		JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		optionsPanel.add(closeCheckBox);

		JPanel buttonsPanel = new JPanel();
		buttonsPanel.add(runButton);
		buttonsPanel.add(sourceButton);
		buttonsPanel.add(closeButton);
		buttonsPanel.add(helpButton);

		southPanel.add(optionsPanel, BorderLayout.CENTER);
		southPanel.add(buttonsPanel, BorderLayout.SOUTH);

		contentPane.add(southPanel, BorderLayout.SOUTH);

		Dimension screenSize = IJ.getScreenSize();

		frame.pack();

		int dialogWidth = frame.getWidth();
		int dialogHeight = frame.getHeight();
		int screenWidth = (int)screenSize.getWidth();
		int screenHeight = (int)screenSize.getHeight();

		Point pos = imageJ.getLocationOnScreen();
		Dimension size = imageJ.getSize();

		/* Generally try to position the dialog slightly
		   offset from the main ImageJ window, but if that
		   would push the dialog off to the screen to any
		   side, adjust it so that it's on the screen.
		*/
		int initialX = (int)pos.getX() + 10;
		int initialY = (int)pos.getY() + size.height+10;

		if (initialX+dialogWidth>screenWidth)
			initialX = screenWidth-dialogWidth;
		if (initialX<0)
			initialX = 0;
		if (initialY+dialogHeight>screenHeight)
			initialY = screenHeight-dialogHeight;
		if (initialY<0)
			initialY = 0;

		frame.setLocation(initialX,initialY);
		frame.setVisible(true);
		frame.toFront();
	}

	/* Make sure that clicks on the close icon close the window: */
	public void windowClosing(WindowEvent e) {
		closeWindow();
	}
	
	private void closeWindow() {
		if (frame!=null)
			frame.dispose();
	}

	public void windowActivated(WindowEvent e) {
		if (IJ.isMacOSX() && frame!=null)
			frame.setMenuBar(Menus.getMenuBar());
	}
	
	public void windowDeactivated(WindowEvent e) { }
	public void windowClosed(WindowEvent e) { }
	public void windowOpened(WindowEvent e) { }
	public void windowIconified(WindowEvent e) { }
	public void windowDeiconified(WindowEvent e) { }
	
	
	private class TableModel extends AbstractTableModel {
		protected ArrayList list;
		public final static int COLUMNS = 4;

		public TableModel() {
			list = new ArrayList();
		}

		public void setData(ArrayList list) {
			this.list = list;
			fireTableDataChanged();
		}

		public int getColumnCount() {
			return COLUMNS;
		}

		public String getColumnName(int column) {
			switch (column) {
				case 0: return "Command";
				case 1: return "Menu Path";
				case 2: return "Class";
				case 3: return "File";
			}
			return null;
		}

		public int getRowCount() {
			return list.size();
		}

		public Object getValueAt(int row, int column) {
			if (row>=list.size() || column>=COLUMNS)
				return null;
			String[] strings = (String[])list.get(row);
			return strings[column];
		}
		
		public String getCommand(int row) {
			if (row<0 || row>=list.size())
				return "";
			else
				return (String)getValueAt(row, 0);
		}

		public void setColumnWidths(TableColumnModel columnModel) {
			int[] widths = {170, 150, 170, 30};
			for (int i=0; i<widths.length; i++)
				columnModel.getColumn(i).setPreferredWidth(widths[i]);
		}

	}

}
