package ij.plugin;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.net.URL;
import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.event.*;
import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.plugin.frame.PlugInFrame;
import ij.util.*;
import ij.text.TextWindow;

/**ControlPanel.
 * This plugin displays a panel with ImageJ commands in a hierarchical tree structure.
 * @author Cezar M. Tigaret <c.tigaret@ucl.ac.uk>
 */
public class ControlPanel implements PlugIn {

	/** The platform-specific file separator string.*/
	private static final String fileSeparator=System.getProperty("file.separator");

	/** The platform-specific file separator character. */
	private static final char sep=fileSeparator.charAt(0);

	private Hashtable panels = new Hashtable();
	private Vector visiblePanels = new Vector();
	private Vector expandedNodes = new Vector();
	private String defaultArg = "";

	private boolean savePropsUponClose=true;
	private boolean propertiesChanged=true;
	private boolean closeChildPanelOnExpand = true;
	private boolean requireDoubleClick;
	private boolean quitting = true;

	Vector menus = new Vector();
	Vector allMenus = new Vector();
	Hashtable commands=new Hashtable();
	Hashtable menuCommands=new Hashtable();
	String[] pluginsArray;
	Hashtable treeCommands = new Hashtable();
	int argLength;

	private String path=null;
	private DefaultMutableTreeNode root;

	MenuItem reloadMI = null;

	public ControlPanel() {
		//requireDoubleClick = !(IJ.isWindows() || IJ.isMacintosh());
		//Java2.setSystemLookAndFeel();
	}


	/** Creates a panel with the hierarchical tree structure of ImageJ's commands. */
	public void run(String arg) {
		load();
	}


	/* *********************************************************************** */
	/*                             Tree logic                                  */
	/* *********************************************************************** */

	synchronized void load() {
		commands = Menus.getCommands();
		pluginsArray = Menus.getPlugins();
		root=doRootFromMenus();
		if (root==null || root.getChildCount()==0 ) return; // do nothing if there's no tree or a root w/o children
		loadProperties();
		restoreVisiblePanels();
		if (panels.isEmpty())
			newPanel(root);
	}

	/** Builds up a root tree from ImageJ's menu bar.
	 * The root tree replicates ImageJ's menu bar with its menus and their submenus.
	 * Delegates to the {@link recursesubMenu(Menu, DefaultMutableTreeNode} method to gather the root children.
	 *
	 */
	private synchronized DefaultMutableTreeNode doRootFromMenus() {
		DefaultMutableTreeNode node = new DefaultMutableTreeNode("ImageJ Menus");
		if(argLength==0) node.setUserObject("Control Panel");
		MenuBar menuBar = Menus.getMenuBar();
		for (int i=0; i<menuBar.getMenuCount(); i++) {
			Menu menu = menuBar.getMenu(i);
			DefaultMutableTreeNode menuNode = new DefaultMutableTreeNode(menu.getLabel());
			recurseSubMenu(menu, menuNode);
			node.add(menuNode);
		}
		return node;
	}

	/**Recursively builds up a tree structure from the Menu argument, by populating the TreeNode argument
	 * with children TreeNode objects constructed on the menu items.
	 * Descendants can be intermediate-level nodes (submenus) or leaf nodes (i.e., no children).
	 * Leaf nodes will only be added if there are any commands associated with them, i.e.
	 * their labels correspond to keys in the hashtable returned by <code>ij.Menus.getCommands()</code>
	 * except for the "Reload Plugins" menu item, for which a local action command string is assigned
	 * to avoid clashes with the action fired from ImageJ Plugins->Utilties->Reload Plugins
	 * menu item.<br>
	 * <strong>Note: </strong> this method bypasses the tree buildup based on the
	 * {@link populateNode(Hashtable,DefaultMutableTreeNode)} method.
	 * @param menu The Menu instance to be searched recursively for menu items
	 * @param node The DefaultMutableTreeNode corresponding to the <code>Menu menu</code> argument.
	 */
	private void recurseSubMenu(Menu menu, DefaultMutableTreeNode node) {
		int items = menu.getItemCount();
		if(items==0) return;
		for (int i=0; i<items; i++) {
			MenuItem mItem = menu.getItem(i);
			String label = mItem.getActionCommand();
			if (mItem instanceof Menu) {
				DefaultMutableTreeNode subNode = new DefaultMutableTreeNode(label);
				recurseSubMenu((Menu)mItem,subNode);
				node.add(subNode);
			} else if (mItem instanceof MenuItem) {
				if (!(label.equals("-"))) {
					DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(label);
					node.add(leaf);
					if(treeCommands==null) treeCommands = new Hashtable();
					if (label.equals("Reload Plugins")) {
						reloadMI = mItem;
						treeCommands.put(label,"Reload Plugins From Panel");
					}
				}
			}
		}
	}

	/**Populates the <code>node</code> argument with items retrieved from the collection.
	 * Actually, the method delegates to {@link populateNode{String[], String[], DefaultMutableTreeNode)}.
	 * @param collection Hashtable with `keys' (java.lang.String) representing a tree path which
	 * is to be added to this node, but excludes it; the path extends to the last child (leaf node).
   * The `values' are (java.lang.String) as labels and user objects for the last child of the path.
	 * Actually, <code><strong>null<strong></code> elements for labels are allowed, in which case
	 * the last child will be constructed on the last token in the `key'.
	 * @param node The TreeNode to be populated.
	 */
	private void populateNode(Hashtable collection, DefaultMutableTreeNode node) {
		Vector labelVector = new Vector();
		for (Enumeration e=collection.keys(); e.hasMoreElements();) {
			String key = (String)e.nextElement();
			labelVector.addElement(key);
		}
		String[] labels = new String[labelVector.size()];
		String[] items = new String[labelVector.size()];
		labelVector.copyInto((String[])labels); // keys into labels[]
		StringSorter.sort(labels);
		for(int i=0; i<labels.length; i++) {
			items[i] = (String)collection.get(labels[i]); //values into items[]
		}
		populateNode(items,labels,node);
	}

	/**Populates the <code>node</code> argument with items retrieved from the two String[] arguments.
	 * Delegates indirectly to {@link buildTreePath(String.String,String,DefaultMutableTreeNode)} to do the job.
	 * If either arguments are empty (i.e., length=0) or have different sizes, the method does nothing.
	 * @param items String array where each element is the source of a tree path (see {@see buildTreePath(String, String, DefaultMutableTreeNode)}
	 * and {@see buildTreePath(String,String,String,DefaultMutableTreeNode)}.
	 * @param labels String array where each element is the label of the root of the tree path
	 * @param node The TreeNode to be populated
	 */
	private void populateNode(String[] items, String[] labels, DefaultMutableTreeNode node) {
		if (items.length==0 || items.length!=labels.length) return;
		String label=null;
		for (int i=0; i<items.length; i++) {
			if(labels!=null && i<labels.length)
				label = labels[i];
			buildTreePath(items[i], label, node);
		}
	}

	/**Short-hand for the four-argument <code>buildTreePath</code> method.
	 * Calls <code>buildTreePath(source,label,<strong>null</strong>,topNode)</code>.
	 * @see buildTreePath(String,String,String,DefaultMutableTreeNode).
	 *
	 */
	private void buildTreePath(String source, String label, DefaultMutableTreeNode topNode) {
		buildTreePath(source, label, null, topNode);
	}

	/**Builds up a tree path structure.
	 * Populates the <code>node</code> argument with a tree path constructed as described below:
   * @param source String to be parsed in a tree path; must be composed of tokens delimited by "/"
	 * @param label The label (String) of the leaf node for this path. If <code><strong>null</strong></code>
	 * then the leafe nod will be constructed from the last token.
	 * @param command The command string of the action event fired upon clicking the leaf node.
	 * If <code><strong>null</strong></code>, then the last token will be taken as action command.
	 * @param topNode The DefaulMutableTreeNode to which this path will be added
	 */
	private void buildTreePath(String source, String label, String command, DefaultMutableTreeNode topNode) {
		String local=source; // will contain the string to be parsed into the tree path
		String argument="";  // will store any argument for the plugin
		String delimiter = fileSeparator; // meaning `/'

		// 1. store plugin arguments (the string between parentheses) in `argument'
		// then store the rest into `local'
		// if there aren't any plugin arguments, then local remains the same as `source'
		int leftParen=source.indexOf('(');
		int rightParen = source.indexOf(')');
		if (leftParen>-1 && rightParen>leftParen) {
			argument = source.substring(leftParen+1, rightParen);
			local = source.substring(0,leftParen);
		}
		// 2. maybe `local' was passed in from some plugin class finder, and is prefixed by
		// full path of the plugins directory; if so, then remove this prefix
		String pluginsPath=Menus.getPlugInsPath();
		if (local.startsWith(pluginsPath))
			local = local.substring(pluginsPath.length(),local.length());
		// 3. convert package/class separators into file separators,
		// to allow parsing into tree path later
		local=local.replace('.',delimiter.charAt(0));
		// 4. append the plugin arguments, but with file separator so that to the same
		// plugin with different arguments will show up as children of the same tree branch
		if (argument.length()>0)
			local=local.concat(fileSeparator).concat(argument);

		DefaultMutableTreeNode node=null;

		// 5. parse the tree path: this code is entirely different from the logic in TreePanel,
		// so don't hold your breath:
		// split the string into tokens delimited by file separator
		// and iterate through the tokens adding an intermediate subnode for each token
		//
		// use the name of the token for intermediate nodes, and the `label' argument for
		// the leaf node if not null; else use the last token for the leaf node
		//
		// for leaf node replace `_' with ` ' and trim away the `.class' extension
		StringTokenizer pathParser = new StringTokenizer(local,delimiter);
		int tokens = pathParser.countTokens();
		while(pathParser.hasMoreTokens()) {
			String token = pathParser.nextToken();
			tokens--;
			if (topNode.isLeaf()&&topNode.getAllowsChildren()) {
				if(token.indexOf("ControlPanel")==-1) {// avoid showing this up in the tree
					// when at leaf level the user object for the node is the `label' if not null,
					// else is the `token'
					if(tokens==0) {
						if(label!=null) token=label; // if label is not null use it instead of token
						token=token.replace('_',' ');
						if(token.endsWith(".class"))
							token = token.substring(0,token.length()-6);//...
					}

					node = new DefaultMutableTreeNode(token); // finally, construct the node

					// when at leaf level, store the `command' (or the `token' if `command' is null)
					// into our internal table
					if (tokens==0) {
						String cmd = (command==null) ? token : command;
						if(treeCommands==null) treeCommands = new Hashtable();
						if(!treeCommands.containsKey(token)) treeCommands.put(token,cmd);
					}
					// add this node to the top node, then make it top node and continue iteration
					topNode.add(node);
					topNode=node;
				}
				continue;
			} else {
				// this node may have been visited before, so we avoid duplicate nodes
				// by recursing through the tree until we find a token that has not been
				// "made" into a node
				boolean hasTokenAsNode=false;
				Enumeration nodes = topNode.children();
				while(nodes.hasMoreElements()) {
					node = (DefaultMutableTreeNode)nodes.nextElement();
					if(((String)node.getUserObject()).equals(token)) {
						hasTokenAsNode = true;
						topNode = node;
						break;
					}
				}
				if (!hasTokenAsNode) {
					if (token.indexOf("ControlPanel")==-1) {
						if (tokens==0) {// we're at leaf level
							if(label!=null) token = label;
							token=token.replace('_',' ');
							if (token.endsWith(".class"))
								token=token.substring(0,token.length()-6); // ...
						}
						node = new DefaultMutableTreeNode(token);
						topNode.add(node);
						topNode=node;
					}
				}
			}
		}
	}

	/* *********************************************************************** */
	/*                          Factories                                      */
	/* *********************************************************************** */

	/**Constructs a TreePanel rooted at the <code>node</code> argument.
	 *
	 */
	TreePanel newPanel(DefaultMutableTreeNode node) {
		boolean main = node.getUserObject().equals(root.getUserObject());
		TreePanel panel = new TreePanel(node, this, main);
		return panel;
	}

	TreePanel newPanel(DefaultMutableTreeNode node, Point location) {
		boolean main = node.getUserObject().equals(root.getUserObject());
		TreePanel panel = new TreePanel(node, this, main, location);
		return panel;
	}
	/**Constructs a TreePanel rooted at the path.
	 *
	 *  @param s A string with the structure "[item1,item2,...,itemn]", as returned by
	 *  a call to the <code>toString()</code> method in the <code>javax.swing.tree.TreePath</code> class.
	 *
	 */
	TreePanel newPanel(String path) {
		if (path.equals("Control_Panel.@Main")) path = "Control_Panel";
		path = key2pStr(path);
		TreePanel pnl = null;
		for(Enumeration e = root.breadthFirstEnumeration(); e.hasMoreElements();) {
			DefaultMutableTreeNode n = (DefaultMutableTreeNode)e.nextElement();
			TreePath p = new TreePath(n.getPath());
			if (p.toString().equals(path))
				pnl=newPanel(n);
		}
		return pnl;
	}

	/* *************************************************************************** */
	/*                          Various Accessors                                  */
	/* *************************************************************************** */

	boolean requiresDoubleClick() {return requireDoubleClick;}

	void setDoubleClick(boolean dc) {requireDoubleClick = dc;}

	boolean hasPanelForNode(DefaultMutableTreeNode node) {
		TreePath path = new TreePath(node.getPath());
		return panels.containsKey(pStr2Key(path.toString()));
	}

	TreePanel getPanelForNode(DefaultMutableTreeNode node) {
		TreePath path = new TreePath(node.getPath());
		String pathString = path.toString();
		if (panels.containsKey(pStr2Key(pathString)))
			return (TreePanel)panels.get(pStr2Key(pathString));
		//else return newPanel(node);
		else return null;
	}

	public DefaultMutableTreeNode getRoot() {return root;}

	Hashtable getPanels() {return panels;}

	Hashtable getTreeCommands() {
		return treeCommands;
	}

	boolean hasVisiblePanels() {
		return visiblePanels.size()>0;
	}

	int getVisiblePanelsCount() { return visiblePanels.size(); }



	/* ************************************************************************** */
	/*                      Properties and panels management                      */
	/* ************************************************************************** */

	void registerPanel(TreePanel panel) {
		String key = pStr2Key(panel.getRootPath().toString());
		panels.put(key,panel);
		setPanelShowingProperty(panel.getRootPath().toString());
		propertiesChanged=true;
	}

	/** All properties related to the ControlPanel have keywords starting with "Control_Panel".
	 * This is to facilitate the integration of these properties with ImageJ's properties database.
	 * The keywords are dot-separated lists of tokens; in each token, spaces are relaced by
	 * underscores. Each token represents a node, and hence the keyword represents a tree path.
	 * The values can be either:
	 * <ol>
	 * <li> a space-separated list of integers,
	 * indicating panel frame geometry <strong>in the following fixed order:</strong>
	 * x coordinate, y coordinate , width, height</li>
	 * <li> or the word "expand" which indicates that the path represented by the keyword is
	 * an expanded branch
	 * </li>
	 * </ol>
	 *
	 */
	void loadProperties() {
		if (IJ.debugMode) IJ.log("CP.loadProperties");
		visiblePanels.removeAllElements();
		expandedNodes.removeAllElements();
		panels.clear();
		Properties properties = Prefs.getControlPanelProperties();
		for (Enumeration e=properties.keys(); e.hasMoreElements();) {
			String key = (String)e.nextElement();
			if (key.startsWith(".Control_Panel.")) {
				key = key.substring(1, key.length());
				String val = Prefs.get(key, null);
				if (IJ.debugMode) IJ.log("  "+key+": "+val);
				if (Character.isDigit(val.charAt(0))) // value starts with digit
					visiblePanels.addElement(key);
				else if (val.equals("expand"))
					expandedNodes.addElement(key);
			}
		}
	}

	void saveProperties() {
		if (IJ.debugMode) IJ.log("CP.saveProperties: "+propertiesChanged);
		if (propertiesChanged) {
			clearProperties();
			for (Enumeration e=visiblePanels.elements(); e.hasMoreElements();) {
				String s = (String)e.nextElement();
				TreePanel p = (TreePanel)panels.get(s);
				if (p!=null) recordGeometry(p);
			}
			for(Enumeration e=expandedNodes.elements(); e.hasMoreElements();)
				Prefs.set((String)e.nextElement(),"expand");
		}
		propertiesChanged=false;
	}
	
	void clearProperties() {
		Properties properties = Prefs.getControlPanelProperties();
		for (Enumeration e=properties.keys(); e.hasMoreElements();) {
			String key = (String)e.nextElement();
			if (key.startsWith(".Control_Panel."))
				properties.remove(key);
		}
	}

	void setExpandedStateProperty(String item) {
		String s = pStr2Key(item);
		expandedNodes.addElement(s);
		propertiesChanged=true;
	}

	boolean hasExpandedStateProperty(String item) {
		String s = pStr2Key(item);
		return expandedNodes.contains(s);
	}

	void unsetExpandedStateProperty(String item) {
		String s = pStr2Key(item);
		expandedNodes.remove(s);
		propertiesChanged=true;
	}

	void setPanelShowingProperty(String item) {
		String s = pStr2Key(item);
		if (!(visiblePanels.contains(s)))
			visiblePanels.addElement(s);
		propertiesChanged=true;
	}

	void unsetPanelShowingProperty(String item) {
		String s = pStr2Key(item);
		visiblePanels.remove(s);
	}

	boolean hasPanelShowingProperty(String item) {
		String s = pStr2Key(item);
		return visiblePanels.contains(s);
	}

	void restoreVisiblePanels() {
		String[] visPanls = new String[visiblePanels.size()];
		visiblePanels.toArray(visPanls);
		Arrays.sort(visPanls);
		for (int i=0; i<visPanls.length; i++) {
			if (!panels.containsKey(visPanls[i])) {
				TreePanel p = newPanel(visPanls[i]);
			}
		}
	}

	void recordGeometry(TreePanel panel) {
		String pTitle = panel.getRootPath().toString();
		pTitle = pStr2Key(pTitle);
		JFrame frame = panel.getFrame();
		if (frame!=null) {
			Rectangle rect=frame.getBounds();
			String xCoord = (Integer.valueOf(rect.x)).toString();
			String yCoord = (Integer.valueOf(rect.y)).toString();
			String width = (Integer.valueOf(rect.width)).toString();
			String height = (Integer.valueOf(rect.height)).toString();
			if (pTitle.equals("Control_Panel")) pTitle = "Control_Panel.@Main";
			String geometry = xCoord+" "+yCoord+" "+width+" "+height;
			if (IJ.debugMode) IJ.log("CP.recordGeometry: "+pTitle+" "+geometry);
			Prefs.set(pTitle, geometry);
		}
	}

	void restoreGeometry(TreePanel panel) {
		String pTitle = panel.getRootPath().toString();
		pTitle = pStr2Key(pTitle);
		if (pTitle.equals("Control_Panel")) pTitle = "Control_Panel.@Main";
		if (IJ.debugMode) IJ.log("CP.restoreGeometry: "+pTitle);
		String geom = Prefs.get(pTitle, null);
		if (geom!=null) {
			int[] coords = s2ints(geom);
			if (coords!=null && coords.length==4)
				panel.setBounds(coords[0],coords[1],coords[2],coords[3]);
			else {
				Point pnt = panel.getDefaultLocation();
				if (pnt!=null)
					panel.getFrame().setLocation((int)pnt.getX(),(int)pnt.getY());
			}
		}
	}

	void closeAll(boolean die) {
		quitting = die;
		if (!visiblePanels.isEmpty()) {
			propertiesChanged = true;
			saveProperties();
		}
		for (Enumeration e = panels.elements(); e.hasMoreElements();) {
			TreePanel p = (TreePanel)e.nextElement();
			p.close();
		}
		//if(quitting) panels.clear();
		quitting = true;
	}

	/* **************************************************************************** */
	/*                              Helper methods                                  */
	/* **************************************************************************** */

// 	/**Removes the leading "[" and trailing "]" from the argument
// 	 *  @param s A string with the structure "[item1,item2,...,itemn]", as returned by
// 	 *  a call to the <code>toString()</code> method in the <code>javax.swing.tree.TreePath</code> class.
// 	 *  @return A string with the structure "item1,item2,...,itemn".
// 	 *  @see javax.swing.tree.TreePath
// 	 */
// 	String trimPathString(String s)
// 	{
// 		int leftBracket = s.indexOf("[");
// 		int rightBracket = s.indexOf("]");
// 		return s.substring(leftBracket+1,rightBracket);
// 	}

	void showHelp() {
		IJ.showMessage("About Control Panel...",
		"This plugin displays a panel with ImageJ commands in a hierarchical tree structure.\n"+" \n"+
		"Usage:\n"+" \n"+
		"     Click on a leaf node to launch the corresponding ImageJ command (or plugin)\n"+
		"     (double-click on X Window Systems)\n"+" \n"+
		"     Double-click on a tree branch node (folder) to expand or collapse it\n"+" \n"+
		"     Click and drag on a tree branch node (folder) to display its descendants,\n"+
		"     in a separate (child) panel (\"tear-off\" mock-up)\n"+" \n"+
		"     In a child panel, use the \"Show Parent\" menu item to re-open the parent panel\n"+
		"     if it was accidentally closed\n"+" \n"+
		"Author: Cezar M. Tigaret (c.tigaret@ucl.ac.uk)\n"+
		"This code is in the public domain."
		);
	}


	// 1. trim away the eclosing brackets
	// 2. replace comma-space with dots
	// 3. replace spaces with underscores
	String pStr2Key(String pathString) {
		String keyword = pathString;
		if(keyword.startsWith("["))
			keyword = keyword.substring(keyword.indexOf("[")+1,keyword.length());
		if(keyword.endsWith("]"))
			keyword = keyword.substring(0,keyword.lastIndexOf("]"));
		StringTokenizer st = new StringTokenizer(keyword,",");
		String result = "";
		while(st.hasMoreTokens()) {
			String token  = st.nextToken();
			if(token.startsWith(" ")) token = token.substring(1,token.length()); // remove leading space
			result+=token+".";
		}
		result = result.substring(0,result.length()-1);//remove trailing dot
		result = result.replace(' ','_');
		return result;
	}

	String key2pStr(String keyword) {
		//keyword = keyword.replace('_',' '); // restore the spaces from underscores
		StringTokenizer st = new StringTokenizer(keyword,".");
		String result = "";
		while(st.hasMoreTokens()) {
			String token = st.nextToken();
			result += token +", ";
		}
		result = result.substring(0,result.length()-2); // trim away the ending comma-space
		result = "["+result+"]";
		result = result.replace('_', ' ');
		return result;
	}


	// Thank you, Wayne!
	/** Breaks the specified string into an array
	 of ints. Returns null if there is an error.*/
	public int[] s2ints(String s) {
		StringTokenizer st = new StringTokenizer(s, ", \t");
		int nInts = st.countTokens();
            if (nInts==0) return null;
		int[] ints = new int[nInts];
		for(int i=0; i<nInts; i++) {
			try {ints[i] = Integer.parseInt(st.nextToken());}
			catch (NumberFormatException e) {return null;}
		}
		return ints;
	}

	//boolean isRunning(){return running;}

} // ControlPanel


/**TreePanel.
*
*
* <br>
* This class lays out the ImageJ user plugins in a vertical, hierarchical tree.. Plugins are launched by double-clicking on their names, in the vertical tree.
*
* Advantages: uses less screen estate, and provides a realistic graphical presentation of the plugins installed in the file system.<br>
*
* Created: Thu Nov 23 02:12:12 2000
* @see ControlPanel
* @author Cezar M. Tigaret <c.tigaret@ucl.ac.uk>
* @version 1.0f
*/


class TreePanel implements
	ActionListener, WindowListener, TreeExpansionListener, TreeWillExpandListener {

	ControlPanel pcp;
	//Vector childrenPanels = new Vector();
	boolean isMainPanel;
	String title;
	boolean isDragging=false;
	//boolean requireDoubleClick=false;
	Point defaultLocation;

	private JTree pTree;
	private JMenuBar pMenuBar;
	private DefaultMutableTreeNode root;
	private DefaultMutableTreeNode draggingNode=null;
	private DefaultTreeModel pTreeModel;
	private ActionListener listener;
	private JFrame pFrame;
	private JCheckBoxMenuItem pMenu_saveOnClose, pMenu_noClutter;
	private TreePath rootPath;

	// the "up" arrow
	private static final int _uparrow1_data[] = {
    0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
    0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
    0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
    0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
    0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
    0x00,0x0d,0x0e,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
    0x00,0x00,0x0d,0x01,0x01,0x0d,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
    0x00,0x00,0x00,0x00,0x01,0x0e,0x02,0x01,0x03,0x00,0x00,0x00,0x00,0x00,
    0x00,0x00,0x00,0x00,0x00,0x01,0x0e,0x04,0x05,0x06,0x01,0x07,0x00,0x00,
    0x00,0x00,0x00,0x00,0x00,0x00,0x01,0x08,0x04,0x09,0x0e,0x02,0x06,0x01,
    0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x02,0x08,0x04,0x09,0x0e,0x0e,0x0e,
    0x02,0x06,0x01,0x00,0x00,0x00,0x00,0x00,0x08,0x08,0x04,0x09,0x0e,0x0e,
    0x0e,0x0e,0x0e,0x02,0x06,0x02,0x00,0x00,0x00,0x08,0x0a,0x0e,0x08,0x0a,
    0x0b,0x0b,0x0c,0x0c,0x0c,0x0c,0x0c,0x06,0x02,0x00,0x0e,0x01,0x01,0x01,
    0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x0e,0x00,0x00,
    0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
    0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
    0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
    0x00,0x00,0x00,0x00
	};

	private static final int _uparrow1_ctable[] = {
    0x21,0xff000000,0xff303030,0xffaaaaaa,0xffffffff,0xff3c3c3c,0xff252525,0xffb6b6b6,0xff585858,0xffc3c3c3,0xff222222,0xff2b2b2b,0xff2e2e2e,0xffa0a0a0,
    0xff808080
	};

	private static IndexColorModel iconCM = new IndexColorModel(8,_uparrow1_ctable.length,_uparrow1_ctable,0,true,255,DataBuffer.TYPE_BYTE);
	private static final ImageIcon upIcon = new ImageIcon( Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(16,16,iconCM,_uparrow1_data,0,16)));

	public TreePanel (DefaultMutableTreeNode root, ControlPanel pcp, boolean isMainPanel) {
		new TreePanel(root,pcp,isMainPanel,null);
	}

	public TreePanel(DefaultMutableTreeNode root, ControlPanel pcp, boolean isMainPanel, Point location) {
		this.root=root;
		this.pcp=pcp;
		this.isMainPanel = isMainPanel;
		defaultLocation = location;
		rootPath=new TreePath(root.getPath());
		title = (String)root.getUserObject();
		buildTreePanel();
		pcp.registerPanel(this);
	}

	/* ************************************************************************** */
	/*                              GUI factories                                 */
	/* ************************************************************************** */

	public void buildTreePanel() {
		pFrame=new JFrame(title);
		pFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		pTreeModel = new DefaultTreeModel(root);
		pTree=new JTree(pTreeModel);
		pTree.setEditable(false);
		pTree.putClientProperty("JTree.lineStyle","Angled");
		pTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		pTree.setRootVisible(false);
		pTree.setShowsRootHandles(true);
		//pTree.setDragEnabled(true);
		JScrollPane ptView=new JScrollPane(pTree);
		addMenu();
		pFrame.getContentPane().add(ptView, BorderLayout.CENTER);
		addListeners();
		pFrame.pack();
		if (defaultLocation!=null) {
			if (IJ.debugMode) IJ.log("CP.buildTreePanel: "+defaultLocation);
			pFrame.setLocation(defaultLocation.x, defaultLocation.y);
		} else
			pcp.restoreGeometry(this);
		//restoreExpandedNodes();
		if (pFrame.getLocation().x==0)
			GUI.center(pFrame);
		setVisible();
		ImageJ ij = IJ.getInstance();
		ij.addWindowListener(this);
		pFrame.addKeyListener(ij);
		pTree.addKeyListener(ij);
	}

	void addMenu() {
		pMenuBar=new JMenuBar();
		Insets ins = new Insets(0,0,0,10);
		pMenuBar.setMargin(ins);
		if (isMainPanel) {
			JMenuItem helpMI = new JMenuItem("Help");
			helpMI.addActionListener(this);
			helpMI.setActionCommand("Help");
			pMenuBar.add(helpMI);
/*			if(pcp.reloadMI!=null)
			{
				pcp.reloadMI.addActionListener(this);
				pMenuBar.add(pcp.reloadMI);
			}*/
		}
		else {
			JMenuItem spMI = new JMenuItem("Show Parent",upIcon);
			spMI.addActionListener(this);
			spMI.setActionCommand("Show Parent");
			pMenuBar.add(spMI);
		}
		pFrame.setJMenuBar(pMenuBar);
	}

	void addListeners() {
		addActionListener(this);
		pFrame.addWindowListener(this);
		pFrame.addComponentListener(new ComponentAdapter() {
			public void componentMoved(ComponentEvent e) {
				Rectangle r = e.getComponent().getBounds();
				if (IJ.debugMode) IJ.log("CP.componentMoved: "+r);
				if (r.x>0) {
					defaultLocation = new Point(r.x, r.y);
					recordGeometry();
				}
			}
		});
		pTree.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				isDragging = false;
				if (e.getClickCount()!=2)
					return;
				int selRow=pTree.getRowForLocation(e.getX(),e.getY());
				if (selRow!=-1) toAction();
			}

			public void mouseReleased(MouseEvent e) {
				if (isDragging) {
					Point pnt = new Point(e.getX(), e.getY());
					SwingUtilities.convertPointToScreen(pnt,pTree);
					tearOff(null,pnt);
				}
				isDragging = false;
			}
		});
		pTree.addMouseMotionListener(new MouseMotionAdapter()
		{
			public void mouseDragged(MouseEvent e)
			{
				int selRow = pTree.getRowForLocation(e.getX(), e.getY());
				if(selRow!=-1)
				{
					if(((DefaultMutableTreeNode)pTree.getLastSelectedPathComponent()).isLeaf()) return;
					pFrame.setCursor(new Cursor(Cursor.MOVE_CURSOR));
					isDragging = true;
				}
			}
		});
		pTree.addTreeExpansionListener(this);
		pTree.addTreeWillExpandListener(this);
	}

	/* ************************************************************************** */
	/*              Accessors -- see also Properties management section           */
	/* ************************************************************************** */

	public String getTitle() {return title;}

	public TreePath getRootPath() {return rootPath;}

	public boolean isTheMainPanel() {return isMainPanel;}

	public JFrame getFrame() {return pFrame;}

	public JTree getTree() {return pTree;}

	public DefaultMutableTreeNode getRootNode() {return root;}

	public Point getDefaultLocation() {return defaultLocation;}

	/* ************************************************************************** */
	/*                        Properties managmenent                              */
	/* ************************************************************************** */

	boolean isVisible() {
		return pFrame.isVisible();
	}

	void setBounds(int x, int y, int w, int h) {
		pFrame.setBounds(new Rectangle(x,y,w,h));
		defaultLocation = new Point(x,y);
	}

	void setAutoSaveProps(boolean autoSave) {
		if(isTheMainPanel()) pMenu_saveOnClose.setSelected(autoSave);
	}

	boolean getAutoSaveProps() {return pMenu_saveOnClose.isSelected();}

	void restoreExpandedNodes() {
		if (pTree==null || root==null)
			return;
		pTree.removeTreeExpansionListener(this);
		TreeNode[] rootPath = root.getPath();
		for(Enumeration e = root.breadthFirstEnumeration(); e.hasMoreElements();)
		//for(Enumeration e = root.children(); e.hasMoreElements();)
		{
			DefaultMutableTreeNode node = (DefaultMutableTreeNode)e.nextElement();
			if(!node.isLeaf() && node != root)
			{
				TreeNode[] nodePath = node.getPath();
				TreePath nTreePath = new TreePath(nodePath);
				String npS = nTreePath.toString();
				DefaultMutableTreeNode[] localPath = new DefaultMutableTreeNode[nodePath.length-rootPath.length+1];
				for(int i=0; i<localPath.length; i++)
				{
					localPath[i]=(DefaultMutableTreeNode)nodePath[i+rootPath.length-1];
				}
				TreePath newPath = new TreePath(localPath);
				if(pcp.hasExpandedStateProperty(npS) && !pcp.hasPanelShowingProperty(npS))
				{
					if(newPath!=null)
					{
						try
						{
							pTree.expandPath(newPath);
						}catch(Throwable t){}
					}
				}
				else if((pcp.hasExpandedStateProperty(npS) || pTree.isExpanded(newPath)) && pcp.hasPanelShowingProperty(npS))
				{
					pTree.collapsePath(newPath);
					pcp.unsetExpandedStateProperty(npS);
				}
			}
		}
		pTree.addTreeExpansionListener(this);
	}

	/* ************************************************************************** */
	/*                        AWT and Swing events manangement                    */
	/* ************************************************************************** */

	public void processEvent(ActionEvent e) {
		if (listener != null) listener.actionPerformed(e);
	}

	public void addActionListener(ActionListener al) {
			listener=AWTEventMulticaster.add(listener, al);
	}

	public void removeActionListener(ActionListener al) {
			listener=AWTEventMulticaster.remove(listener, al);
	}

	public void actionPerformed(ActionEvent e) {
			String cmd=e.getActionCommand();
			if(cmd==null) return;
			if (cmd.equals("Help")) {
				showHelp();
				return;
			}
			if(cmd.equals("Show Parent")) {
				DefaultMutableTreeNode parent = (DefaultMutableTreeNode)root.getParent();
				if (parent!=null) {
					TreePanel panel = pcp.getPanelForNode(parent);
					if(panel==null) panel = pcp.newPanel(parent);
					if(panel!=null) panel.setVisible();
				}
				return;
			}
			if(cmd.equals("Reload Plugins From Panel")) {// cmd fired by clicking on tree leaf
				pcp.closeAll(false);
				IJ.doCommand("Reload Plugins");
			}
			else {
				if(cmd.equals("Reload Plugins")) // cmd fired from ImageJ menu; don't propagate it further
					pcp.closeAll(false);
				else
					IJ.doCommand(cmd);
				return;
			}
	}

	// we implement window listener directly so that we can catch ImageJ's window events
	/** Upon window closing, the panel removes itself from the vector of visible panels (member of pcp).
	 * Then, if this is the only visible panel left, the properties are saved; else, if this is the
	 * main panel, all other visible panels are also closed and properties
	 * are saved
	 */
	public void windowClosing(WindowEvent e) {
		if (IJ.debugMode) IJ.log("CP.windowClosing: "+isMainPanel);
		if (isMainPanel)
			pcp.saveProperties();
		pcp.unsetPanelShowingProperty(getRootPath().toString());
	}

	public void windowActivated(WindowEvent e) {
		WindowManager.setWindow(getFrame());
	}
	
	public void windowClosed(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}


	public void treeCollapsed (TreeExpansionEvent ev) {
		String evPathString = ev.getPath().toString();
		evPathString = evPathString.substring(evPathString.indexOf("[")+1,evPathString.lastIndexOf("]"));
		evPathString = evPathString.substring(getTitle().length()+2,evPathString.length());
		String rootPath = getRootPath().toString();
		rootPath = rootPath.substring(rootPath.indexOf("[")+1,rootPath.lastIndexOf("]"));
		String path = "["+rootPath +", "+evPathString+"]";
		pcp.unsetExpandedStateProperty(path);
	}

	public void treeExpanded(TreeExpansionEvent ev) {
		TreePath evPath = ev.getPath();
		//DefaultMutableTreeNode node = (DefaultMutableTreeNode)evPath.getLastPathComponent();
		String evPathString = ev.getPath().toString();
		evPathString = pcp.pStr2Key(evPathString);
		evPathString = evPathString.substring(getTitle().length()+1,evPathString.length());
		String rootPath = getRootPath().toString();
		rootPath = pcp.pStr2Key(rootPath);
		//String path = rootPath+"."+evPathString;
		String path = rootPath+"."+evPathString;
		if (pcp.hasPanelShowingProperty(path)) {
			Hashtable panels = pcp.getPanels();
			TreePanel p = (TreePanel)panels.get(path);
			if(p!=null) p.close();
		}
		pcp.setExpandedStateProperty(path);
	}

	//stub for future development
	public void treeWillExpand(TreeExpansionEvent ev) {}
	//stub for future development
	public void treeWillCollapse(TreeExpansionEvent ev) {}

	void recordGeometry() {pcp.recordGeometry(this);}

	/* ************************************************************************** */
	/*                             Actions                                        */
	/* ************************************************************************** */

	void refreshTree() {
		pTreeModel.reload();
	}

	void tearOff() {
		tearOff(null);
	}

	void tearOff(DefaultMutableTreeNode node) {
		tearOff(node, null);
	}

	void tearOff(DefaultMutableTreeNode node, Point pnt) {
		isDragging = false;
		pFrame.setCursor(Cursor.getDefaultCursor());
		if(node==null)
			node = (DefaultMutableTreeNode)pTree.getLastSelectedPathComponent();
		if(node.isLeaf()) return;
		TreeNode[] nPath = node.getPath();
		TreeNode[] rPath = root.getPath();
		DefaultMutableTreeNode[] tPath = new DefaultMutableTreeNode[nPath.length-rPath.length+1];
		for(int i=0; i<tPath.length; i++)
			tPath[i] = (DefaultMutableTreeNode)nPath[i+rPath.length-1];
		TreePath path = new TreePath(nPath);
		TreePath localPath = new TreePath(tPath);
		String pathString = localPath.toString();
		TreePanel p = pcp.getPanelForNode(node);
		if (p==null) {
			if(pnt!=null)
				p = pcp.newPanel(node, pnt);
			else
				p = pcp.newPanel(node);
			pTree.collapsePath(localPath);
		} else {
			if (pnt!=null)
				p.setLocation(pnt);
			p.setVisible();
			pTree.collapsePath(localPath);
		}
	}

	/** Fires an ActionEvent upon double-click on the plugin item (leaf node) in the JTree */
	void toAction() {
			DefaultMutableTreeNode nde=(DefaultMutableTreeNode)pTree.getLastSelectedPathComponent();
			// if the node has children then do nothing (return)
			if (nde.getChildCount()>0) return;
			String aCmd=nde.toString();
			String cmd= aCmd;
			if(pcp.treeCommands.containsKey(aCmd))
				cmd = (String)pcp.treeCommands.get(aCmd);
			processEvent(new ActionEvent(this,ActionEvent.ACTION_PERFORMED,cmd));
	}

	void setVisible() {
		if (pFrame!=null && !pFrame.isVisible()) {
			restoreExpandedNodes();
			if (defaultLocation!=null) pFrame.setLocation(defaultLocation);
			pFrame.setVisible(true);
			// close expanded path to this panel in its parent panel (if visible and if the path is expanded)
			DefaultMutableTreeNode parent = (DefaultMutableTreeNode)root.getParent();
			if (parent!=null) {
				TreePanel pnl = pcp.getPanelForNode(parent);
				if (pnl!=null && pnl.isVisible()) {
					TreeNode[] rPath = root.getPath();
					TreeNode[] pPath = pnl.getRootNode().getPath();
					DefaultMutableTreeNode[] tPath = new DefaultMutableTreeNode[rPath.length-pPath.length+1];
					for(int i=0; i<tPath.length; i++)
						tPath[i] = (DefaultMutableTreeNode)rPath[i+pPath.length-1];
					//TreePath path = new TreePath(rPath);
					TreePath localPath = new TreePath(tPath);
					pnl.getTree().collapsePath(localPath);
				}
			}
		}
		if (pcp!=null) pcp.setPanelShowingProperty(getRootPath().toString());
	}

	void setLocation(Point p) {
		if (p!=null) defaultLocation = p;
	}

	void close() {
		pFrame.dispatchEvent(new WindowEvent(pFrame,WindowEvent.WINDOW_CLOSING));
		pcp.unsetPanelShowingProperty(getRootPath().toString());
	}

	private void showHelp() {pcp.showHelp();}

} // TreePanel


