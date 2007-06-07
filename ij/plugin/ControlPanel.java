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
//import javax.swing.plaf.*;
import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.plugin.frame.PlugInFrame;
import ij.util.*;
import ij.text.TextWindow;

/**ControlPanel.
 *
 *
 *
 * <br>Created: Tue Dec  5 00:52:15 2000; Modified 2004-01-27
 * <p>
 * </p>
 * @author Cezar M. Tigaret <c.tigaret@ucl.ac.uk>
 * @version 1.0f
 */
public class ControlPanel implements PlugIn
{


	private static final String pluginsPath=Menus.getPlugInsPath();

	private static final String pcpVersion="1.0f";

	/** The platform-specific file separator string.*/
	private static final String fileSeparator=System.getProperty("file.separator");

	/** The platform-specific file separator character. */
	private static final char sep=fileSeparator.charAt(0);

	/** The JVM version we're using */
	private static final String jvmversion=System.getProperty("java.version");

	/** The "major" part of the JVM version string. */
	private static final String jvmversionMajor=jvmversion.substring(0,jvmversion.lastIndexOf('.'));

	/** The instance of ImageJ application we're running */
	private static ImageJ ij=IJ.getInstance();

	private Hashtable panels = new Hashtable();
	private Vector visiblePanels = new Vector();
	private Vector expandedNodes = new Vector();
	private String defaultArg = "";

	private Properties pcpProperties=new Properties();
	private File pcpPropsFile=new File(System.getProperty("user.home")+System.getProperty("file.separator")+"PCPanel2.ini");
	private boolean savePropsUponClose=true;
	private boolean propertiesChanged=true;
	private boolean closeChildPanelOnExpand = true;
	private boolean requireDoubleClick=false;

	TreePanel panel;

	Vector menus = new Vector();
	Vector allMenus = new Vector();
	String[] installableMenuLabels = {"About Plugins","Filters","Import","Plugins","Save As","Shortcuts","Tools","Utilities"};
	Hashtable commands=new Hashtable();
	Hashtable menuCommands=new Hashtable();
	String[] pluginsArray;
	Hashtable treeCommands = new Hashtable();
	int argLength=0;

	private String path=null;
	private DefaultMutableTreeNode root;

	private static String pcpDir=null;


	public ControlPanel()
	{
		if (!IJ.isJava2()) { //wsr
			IJ.error("This command requires Java 1.2 or later");
			return;
		}
		commands = Menus.getCommands();
		pluginsArray = Menus.getPlugins();
		requireDoubleClick = !(IJ.isWindows() || IJ.isMacintosh());
		Java2.setSystemLookAndFeel();
	}


	/** Creates a panel with the hierarchical tree structure of (some of) Imagej's commands according to the structure
	 * of the String argument (see below).
	 *
	 * @param arg String (optional) = a semi-colon -- separated list of one or more tokens:<br>
	 * "imagej menus" creates a tree with all of ImageJ's menu structure, that means it replicates in tree
	 * form ImageJ's menu bar; this includes any jar plugin and user plugins
	 * "user plugins" creates a tree with loose user plugins (not "jar plugins")<br>
	 * "imagej commands" creates a tree with all ImageJ's commands<br>
	 * "about" will not create a tree panel; instead, it will show a brief help message<br>
	 * If there is more than one token, a subtree will be created for each token, and added to a common root tree.
	 * If the "about" token is also present, a help essage will be displayed<br>
	 * If the argument is missing, a panel with all of ImageJ's menus will be created as if "imagej menus" was passed as argument.<br>
	 * Please note that when no arguments are passed, ImageJ's menus will be shown as a unique tree named "Control Panel"; if "imagej menus"<br>
	 * is part of a multi-token argument, them ImageJ menus will be created as a sub tree called "ImageJ Menus" (what else ? :-)...) and the main
	 * tree will be called "Control Panel"
	 *
	 */
	public void run(String arg) {
		//IJ.write("***** MARK *****");
		if (!IJ.isJava2()) //wsr
			return;
		arg = (arg.length()==0) ? defaultArg : arg;
		argLength = arg.length();
		root=buildTree(arg);
		if(root==null | root.getChildCount()==0 ) return; // do nothing if there's no tree or a root w/o children
		loadProperties();
		restoreVisiblePanels();
		if(panels.isEmpty())
		{
			//IJ.write("no panels");
			newPanel(root);
		}
	}

	/* *********************************************************************** */
	/*                             Tree logic                                  */
	/* *********************************************************************** */

	DefaultMutableTreeNode buildTree(String arg)
	{
		DefaultMutableTreeNode rootNode = null;
		if(arg.length()==0) return doRootFromMenus();
		StringTokenizer argParser = new StringTokenizer(arg,";");
		int tokens = argParser.countTokens();
		if(tokens==1)
		{
			rootNode = doRoot(arg);
		}
		else
		{
			rootNode = new DefaultMutableTreeNode("Control Panel");
			while(argParser.hasMoreTokens())
			{
				String token = argParser.nextToken();
				DefaultMutableTreeNode node = doRoot(token);
				if(node!=null) rootNode.add(node);
			}
		}
		return rootNode;
	}

	private DefaultMutableTreeNode doRoot(String arg)
	{
		DefaultMutableTreeNode node = null;
		if(arg.equals("user plugins"))
		{
			node = new DefaultMutableTreeNode("User Plugins");
			if(argLength==0) node.setUserObject("Control Panel");
			retrieveItems(pluginsArray,null,node);
		}
		if(arg.equals("imagej menus"))
		{
			node=doRootFromMenus();
		}
		if(arg.equals("imagej commands"))
		{
			node = new DefaultMutableTreeNode("ImageJ Commands");
			if(argLength==0) node.setUserObject("Control Panel");
			retrieveItems(commands,node);
		}
		if(arg.equals("about"))
		{
			showHelp();
		}
		return node;
	}


	/** Builds up a root tree from ImageJ's menu bar.
	 * The root tree replicates ImageJ's menu bar with its menus and their submenus.
	 *
	 */
	private DefaultMutableTreeNode doRootFromMenus()
	{
		DefaultMutableTreeNode node = new DefaultMutableTreeNode("ImageJ Menus");
		if(argLength==0) node.setUserObject("Control Panel");
		MenuBar menuBar = Menus.getMenuBar();
		for (int i=0; i<menuBar.getMenuCount(); i++)
		{
			Menu menu = menuBar.getMenu(i);
			DefaultMutableTreeNode menuNode = new DefaultMutableTreeNode(menu.getLabel());
			recurseSubMenu(menu, menuNode);
			node.add(menuNode);
		}
		return node;
	}

	/** Recursively builds up a tree structure from a menu.
	 * Descendants can be intermediate-level nodes (submenus) or leaf nodes (i.e., no children).
	 * Leaf nodes will only be added if there are any commands associated with them, i.e.
	 * their labels correspond to keys in the hashtable returned by <code>ij.Menus.getCommands()</code>
	 * @param menu The Menu instance to be searched recursively for menu items
	 * @param node The DefaultMutableTreeNode corresponding to the <code>Menu menu</code> argument.
	 */
	private void recurseSubMenu(Menu menu, DefaultMutableTreeNode node)
	{
		int items = menu.getItemCount();
		if(items==0) return;
		for (int i=0; i<items; i++)
		{
			MenuItem mItem = menu.getItem(i);
			String label = mItem.getLabel();
			if(mItem instanceof Menu)
			{
				DefaultMutableTreeNode subNode = new DefaultMutableTreeNode(label);
				recurseSubMenu((Menu)mItem,subNode);
				node.add(subNode);
			}
			else if(mItem instanceof MenuItem)
			{
				if(!(label.equals("-")))
				{
					DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(label);
					node.add(leaf);
				}
			}
		}
	}

	// extract from Hashtable collection the keys into labels[]
	// and the values into items[]
	private void retrieveItems
	(
		Hashtable collection,
		DefaultMutableTreeNode node
	)
	{
		Vector labelVector = new Vector();
		for (Enumeration e=collection.keys(); e.hasMoreElements();)
		{
			String key = (String)e.nextElement();
			labelVector.addElement(key);
		}
		String[] labels = new String[labelVector.size()];
		String[] items = new String[labelVector.size()];
		labelVector.copyInto((String[])labels); // keys into labels[]
		StringSorter.sort(labels);
		for(int i=0; i<labels.length; i++)
		{
			items[i] = (String)collection.get(labels[i]); //values into items[]
		}
		retrieveItems(items,labels,node);
	}

	private void retrieveItems
	(
		String[] items,
		String[] labels,
		DefaultMutableTreeNode node
	)
	{
		if(items.length==0) return;
		String label=null;
		for (int i=0; i<items.length; i++)
		{
			if(labels!=null && i<labels.length)
				label = labels[i];
			buildTreePath(items[i], label, node);
		}
	}

	private void buildTreePath
	(
		String source,
		String label,
		DefaultMutableTreeNode topNode
	)
	{
		buildTreePath(source, label, null, topNode);
	}

	/**Builds up a tree path structure.
   * @param source String to be parsed in a tree path; must be composed of tokens delimited by "/"
	 * @param label The label (String) of the leaf node for this path.
	 * @param command The command string of the action event fired upon clicking the leaf node.
	 * @param topNode The DefaulMutableTreeNode to which this path will be added
	 */
	private void buildTreePath
	(
		String source,
		String label,
		String command,
		DefaultMutableTreeNode topNode
	)
	{
		String local=source; // here is where we place the string to be parsed into the tree path
		String argument="";
		String delimiter = fileSeparator;
		// 1. store away any argument (the string between parentheses) passed to the plugin
		int leftParen=source.indexOf('(');
		int rightParen = source.indexOf(')');
		if(leftParen>-1 && rightParen>leftParen)
		{
			argument = source.substring(leftParen+1, rightParen);
			local = source.substring(0,leftParen);
		}
		// 2. process the source:
		if(local.startsWith(pluginsPath))
		{
			local = local.substring(pluginsPath.length(),local.length());
		}
		// convert package/class separators into file separator, for parsing into tree path
		local=local.replace('.',delimiter.charAt(0));
		// 3. add back the arguments, but with file separator; like this, calls to the same
		// plugin with different arguments will show up under the same tree branch
		if(argument.length()>0)
		{
			local=local.concat(fileSeparator).concat(argument);
		}

		DefaultMutableTreeNode node=null;

		// parsing the tree path: split the string into tokens delimited by file separator
		// use the name of the token for intermediate nodes; and the label for the leaf node
		StringTokenizer pathParser = new StringTokenizer(local,delimiter);
		int tokens = pathParser.countTokens();
		while(pathParser.hasMoreTokens())
		{
			String token = pathParser.nextToken();
			tokens--;
			if(topNode.isLeaf()&&topNode.getAllowsChildren())
			{
				if(token.indexOf("ControlPanel")==-1)// avoid showing this up in the tree
				{
					String cmd=token;
					if(tokens==0) // we're at leaf level
					{
						if(label!=null) token=label;
						token=token.replace('_',' ');
						if(token.endsWith(".class"))
							token = token.substring(0,token.length()-6);//...
					}
					node = new DefaultMutableTreeNode(token);
					if(tokens==0)
					{
						if(treeCommands==null) treeCommands = new Hashtable();
						if(!treeCommands.containsKey(token)) treeCommands.put(token,cmd);
					}
					topNode.add(node);
					topNode=node;
				}
				continue;
			}
			else
			{
				boolean hasTokenAsNode=false;
				Enumeration nodes = topNode.children();
				while(nodes.hasMoreElements())
				{
					node = (DefaultMutableTreeNode)nodes.nextElement();
					if(((String)node.getUserObject()).equals(token))
					{
						hasTokenAsNode = true;
						topNode = node;
						break;
					}
				}
				if(!hasTokenAsNode)
				{
					if(token.indexOf("ControlPanel")==-1)
					{
						if(tokens==0) // we're at leaf level
						{
							if(label!=null) token = label;
							token=token.replace('_',' ');
							if(token.endsWith(".class"))
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
	TreePanel newPanel(DefaultMutableTreeNode node)
	{
		boolean main = node.getUserObject().equals(root.getUserObject());
		TreePanel panel = new TreePanel(node, this, main);
		return panel;
	}

	TreePanel newPanel(DefaultMutableTreeNode node, Point location)
	{
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
	TreePanel newPanel(String path)
	{
		path = key2pStr(path);
		TreePanel pnl = null;
		for(Enumeration e = root.breadthFirstEnumeration(); e.hasMoreElements();)
		{
			DefaultMutableTreeNode n = (DefaultMutableTreeNode)e.nextElement();
			TreePath p = new TreePath(n.getPath());
			if(p.toString().equals(path))
			{
				//IJ.write("creating "+p.toString());
				pnl=newPanel(n);
			}
		}
		return pnl;
	}

	/* *************************************************************************** */
	/*                          Various Accessors                                  */
	/* *************************************************************************** */

	boolean requiresDoubleClick() {return requireDoubleClick;}

	void setDoubleClick(boolean dc) {requireDoubleClick = dc;}

	boolean hasPanelForNode(DefaultMutableTreeNode node)
	{
		TreePath path = new TreePath(node.getPath());
		return panels.containsKey(pStr2Key(path.toString()));
	}

	TreePanel getPanelForNode(DefaultMutableTreeNode node)
	{
		TreePath path = new TreePath(node.getPath());
		String pathString = path.toString();
		if(panels.containsKey(pStr2Key(pathString)))
		{
			//IJ.write("get panel for node "+pStr2Key(pathString));
			return (TreePanel)panels.get(pStr2Key(pathString));
		}
		//else return newPanel(node);
		else return null;
	}

	String getPluginsPath(){
		return pluginsPath;
	}

	public String getVersion(){
		return pcpVersion;
	}

	public DefaultMutableTreeNode getRoot() {return root;}

	Hashtable getPanels() {return panels;}

	Hashtable getTreeCommands()
	{
		return treeCommands;
	}

	boolean hasVisiblePanels()
	{
		return visiblePanels.size()>0;
	}

	int getVisiblePanelsCount() { return visiblePanels.size(); }



	/* ************************************************************************** */
	/*                      Properties and panels management                      */
	/* ************************************************************************** */

	void registerPanel(TreePanel panel)
	{
		String key = pStr2Key(panel.getRootPath().toString());
		//IJ.write("register "+key);
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
	void loadProperties()
	{
		visiblePanels.removeAllElements();
		expandedNodes.removeAllElements();
		panels.clear();
		try
		{
			if(pcpPropsFile.exists() && pcpPropsFile.canRead())
			{
				pcpProperties.load(new FileInputStream(pcpPropsFile));
				for(Enumeration e=pcpProperties.keys(); e.hasMoreElements();)
				{
					String key = (String)e.nextElement();
					if(key.startsWith("Control_Panel"))
					{
						String val = pcpProperties.getProperty(key);
						if(Character.isDigit(val.charAt(0))) // value starts with digit
						{
							visiblePanels.addElement(key);
						}
						if(val.equals("expand"))
							expandedNodes.addElement(key);
					}
				}
			}
		} catch (Exception e) {}
	}

	void saveProperties()
	{
		if(propertiesChanged)
		{
			pcpProperties.clear();
			for(Enumeration e=visiblePanels.elements(); e.hasMoreElements();)
			{
				String s = (String)e.nextElement();
				TreePanel p = (TreePanel)panels.get(s);
				if(p!=null) recordGeometry(p);
			}
			for(Enumeration e=expandedNodes.elements(); e.hasMoreElements();)
			{
				pcpProperties.setProperty((String)e.nextElement(),"expand");
			}
			try
			{
				if(pcpPropsFile.exists() && !pcpPropsFile.canWrite()) return;
				pcpProperties.store(new FileOutputStream(pcpPropsFile),"Plugins Control Panel properties");
			}
			catch (Exception e) {}
		}
		propertiesChanged=false;
	}

	void setExpandedStateProperty(String item)
	{
		String s = pStr2Key(item);
		//IJ.write("set expanded "+s);
		expandedNodes.addElement(s);
		propertiesChanged=true;
	}

	boolean hasExpandedStateProperty(String item)
	{
		String s = pStr2Key(item);
		//IJ.write("has expanded prop "+s);
		return expandedNodes.contains(s);
	}

	void unsetExpandedStateProperty(String item)
	{
		String s = pStr2Key(item);
		//IJ.write("unset expanded "+s);
		expandedNodes.remove(s);
		propertiesChanged=true;
	}

	void setPanelShowingProperty(String item)
	{
		String s = pStr2Key(item);
		//IJ.write("set showing "+s);
		if(!(visiblePanels.contains(s)))
		{
			visiblePanels.addElement(s);
		}
		propertiesChanged=true;
	}

	void unsetPanelShowingProperty(String item)
	{
		String s = pStr2Key(item);
		//IJ.write("unset showing "+s);
		if(visiblePanels.remove(s))
		{
			//IJ.write("removed from showing "+item);
		}
		propertiesChanged=true;
	}

	boolean hasPanelShowingProperty(String item)
	{
		String s = pStr2Key(item);
		//IJ.write("has showing "+s);
		return visiblePanels.contains(s);
	}

	void restoreVisiblePanels()
	{
		//IJ.write("restoring "+visiblePanels.size()+" visible panels ...");
		String[] visPanls = new String[visiblePanels.size()];
		visiblePanels.toArray(visPanls);
		for(int i=0; i<visPanls.length; i++)
		{
			if(!panels.containsKey(visPanls[i]))
			{
				TreePanel p = newPanel(visPanls[i]);
			}
		}
		//IJ.write("restoring done");
	}

	void recordGeometry(TreePanel panel)
	{
		String pTitle = panel.getRootPath().toString();
		pTitle = pStr2Key(pTitle);
		JFrame frame = panel.getFrame();
		if(frame!=null)
		{
			Rectangle rect=frame.getBounds();
			String xCoord = (new Integer(rect.x)).toString();
			String yCoord = (new Integer(rect.y)).toString();
			String width = (new Integer(rect.width)).toString();
			String height = (new Integer(rect.height)).toString();
			String geometry = xCoord+" "+yCoord+" "+width+" "+height;
			pcpProperties.setProperty(pTitle,geometry);
		}
	}

	void restoreGeometry(TreePanel panel)
	{
		if(!pcpProperties.isEmpty())
		{
			String pTitle = panel.getRootPath().toString();
			pTitle = pStr2Key(pTitle);
			if(pcpProperties.containsKey(pTitle))
			{
				String geom = pcpProperties.getProperty(pTitle);
				int[] coords = s2ints(geom);
				if(coords!=null && coords.length==4) //wsr
				{
					panel.setBounds(coords[0],coords[1],coords[2],coords[3]);
				}
				else
				{
					Point pnt = panel.getDefaultLocation();
					if(pnt!=null)
					{
						IJ.write("restore for no geometry "+pnt.getX()+ " "+pnt.getY());
						panel.getFrame().setLocation((int)pnt.getX(),(int)pnt.getY());
					}
				}
			}
		}
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

	void showHelp()
	{
		IJ.showMessage("About Control Panel...",
		"This plugin displays a panel with ImageJ commands in a hierarchical tree structure.\n"+" \n"+
		"Usage:\n"+" \n"+
		"     Click on a leaf node to launch the corresponding ImageJ command (or plugin)\n"+" \n"+
		"     Double-click on a tree branch node (folder) to expand or collapse it\n"+" \n"+
		"     Click and drag on a tree branch node (folder) to display its descendants,\n"+
		"     in a separate (child) panel (\"tear-off\" mock-up)\n"+" \n"+
		"     In a child panel, use the \"Show Parent\" menu item to re-open the parent panel\n"+
		"     if it was accidentally closed\n"+" \n"+
		"Please note that after installing/removing plugins, ImageJ must be restarted\n"+" \n"+
		"Version: "+pcpVersion+"\n"+
		"Author: Cezar M. Tigaret (c.tigaret@ucl.ac.uk)\n"+
		"This code is in the public domain."
		);
	}


	// 1. trim away the eclosing brackets
	// 2. replace comma-space with dots
	// 3. replace spaces with underscores
	String pStr2Key(String pathString)
	{
		String keyword = pathString;
		if(keyword.startsWith("["))
			keyword = keyword.substring(keyword.indexOf("[")+1,keyword.length());
		if(keyword.endsWith("]"))
			keyword = keyword.substring(0,keyword.lastIndexOf("]"));
		StringTokenizer st = new StringTokenizer(keyword,",");
		String result = "";
		while(st.hasMoreTokens())
		{
			String token  = st.nextToken();
			if(token.startsWith(" ")) token = token.substring(1,token.length()); // remove leading space
			result+=token+".";
		}
		result = result.substring(0,result.length()-1);//remove trailing dot
		result = result.replace(' ','_');
		return result;
	}

	String key2pStr(String keyword)
	{
		//keyword = keyword.replace('_',' '); // restore the spaces from underscores
		StringTokenizer st = new StringTokenizer(keyword,".");
		String result = "";
		while(st.hasMoreTokens())
		{
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

} // ControlPanel


/**TreePanel.
*
*
* <br>
* This class lays out the ImageJ user plugins in a vertical, hierarchical tree.. Plugins are launched by double-clicking on their names, in the vertical tree.
*
* Advantages: uses less screen estate, and provides a realistic graphical presentation of the plugins installed in the file system.<br>
*
* Requires: ImageJ 1.20b or newer, and Java 2 Platform v 1.3 or newer.<br>
* NB! This plugin will NOT work with Java platform 1 even if you have the Swing components (Java Foundation Classes) installed.
*
* Created: Thu Nov 23 02:12:12 2000
* @see ControlPanel
* @author Cezar M. Tigaret <c.tigaret@ucl.ac.uk>
* @version 1.0f
*/


class TreePanel implements
	ActionListener, WindowListener, TreeExpansionListener, TreeWillExpandListener
{

	ControlPanel pcp;
	Vector childrenPanels = new Vector();
	boolean isMainPanel;
	String title;
	boolean isDragging=false;
	//boolean requireDoubleClick=false;
	Point defaultLocation = null;

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
	private static int _uparrow1_data[] =
	{
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

	private static int _uparrow1_ctable[] =
	{
    0x21,0xff000000,0xff303030,0xffaaaaaa,0xffffffff,0xff3c3c3c,0xff252525,0xffb6b6b6,0xff585858,0xffc3c3c3,0xff222222,0xff2b2b2b,0xff2e2e2e,0xffa0a0a0,
    0xff808080
	};


	private static IndexColorModel iconCM = new IndexColorModel(8,_uparrow1_ctable.length,_uparrow1_ctable,0,true,255,DataBuffer.TYPE_BYTE);
	private static final ImageIcon upIcon = new ImageIcon( Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(16,16,iconCM,_uparrow1_data,0,16)));

	public TreePanel
	(
		DefaultMutableTreeNode root,
		ControlPanel pcp,
		boolean isMainPanel
	)
	{
		new TreePanel(root,pcp,isMainPanel,null);
	}

	public TreePanel
	(
		DefaultMutableTreeNode root,
		ControlPanel pcp,
		boolean isMainPanel,
		Point location
	)
	{
		this.root=root;
		this.pcp=pcp;
		this.isMainPanel = isMainPanel;
		defaultLocation = location;
		rootPath=new TreePath(root.getPath());
		title = (String)root.getUserObject();
		//IJ.write("new panel "+pcp.pStr2Key(rootPath.toString()));
		buildTreePanel();
		pcp.registerPanel(this);
	}

	/* ************************************************************************** */
	/*                              GUI factories                                 */
	/* ************************************************************************** */

	public void buildTreePanel()
	{
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
		if(defaultLocation!=null)
			pFrame.setLocation((int)defaultLocation.getX(),(int)defaultLocation.getY());
		else pcp.restoreGeometry(this);
		//restoreExpandedNodes();
		setVisible();
		IJ.getInstance().addWindowListener(this);
	}

	void addMenu()
	{
		pMenuBar=new JMenuBar();
		if(isMainPanel)
		{
			JMenuItem helpMI = new JMenuItem("Help");
			helpMI.addActionListener(this);
			helpMI.setActionCommand("Help");
			pMenuBar.add(helpMI);
		}
		else
		{
			JMenuItem spMI = new JMenuItem("Show Parent",upIcon);
			spMI.addActionListener(this);
			spMI.setActionCommand("Show Parent");
			pMenuBar.add(spMI);
		}
		pFrame.setJMenuBar(pMenuBar);
	}

	void addListeners()
	{
		addActionListener(this);
		pFrame.addWindowListener(this);
		pFrame.addComponentListener(new ComponentAdapter()
		{
			public void componentMoved(ComponentEvent e)
			{
				Rectangle r = e.getComponent().getBounds();
				defaultLocation = new Point(r.x,r.y);
				recordGeometry();
			}
		});
		pTree.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				isDragging = false;
				if(pcp.requiresDoubleClick() && e.getClickCount()!=2) return;
				int selRow=pTree.getRowForLocation(e.getX(),e.getY());
				if (selRow!=-1) toAction();
			}

			public void mouseReleased(MouseEvent e)
			{
				if(isDragging)
				{
					tearOff(null);
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

	boolean isVisible()
	{
		return pFrame.isVisible();
	}

	void setBounds(int x, int y, int w, int h)
	{
		pFrame.setBounds(new Rectangle(x,y,w,h));
		defaultLocation = new Point(x,y);
	}

	void setAutoSaveProps(boolean autoSave)
	{
		if(isTheMainPanel()) pMenu_saveOnClose.setSelected(autoSave);
	}

	boolean getAutoSaveProps() {return pMenu_saveOnClose.isSelected();}

	void restoreExpandedNodes()
	{
		//IJ.write("restore exp nodes");
		pTree.removeTreeExpansionListener(this);
		TreeNode[] rootPath = root.getPath();
		//for(Enumeration e = root.breadthFirstEnumeration(); e.hasMoreElements();)
		for(Enumeration e = root.children(); e.hasMoreElements();)
		{
			DefaultMutableTreeNode node = (DefaultMutableTreeNode)e.nextElement();
			if(!node.isLeaf())
			{
				TreeNode[] nodePath = node.getPath();
				TreePath nTreePath = new TreePath(nodePath);
				String npS = nTreePath.toString();
/*				if(pcp.hasPanelShowingProperty(npS))
				{
					IJ.write("has pnel showing: "+npS);
				}*/
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

	public void processEvent(ActionEvent e)
	{
		if (listener != null) listener.actionPerformed(e);
	}

	public void addActionListener(ActionListener al)
	{
			listener=AWTEventMulticaster.add(listener, al);
	}

	public void removeActionListener(ActionListener al)
	{
			listener=AWTEventMulticaster.remove(listener, al);
	}

	public void actionPerformed(ActionEvent e)
	{
			String cmd=e.getActionCommand();
			if(cmd==null) return;
			if (cmd.equals("Help"))
			{
				showHelp();
				return;
			}
			if(cmd.equals("Show Parent"))
			{
				DefaultMutableTreeNode parent = (DefaultMutableTreeNode)root.getParent();
				if(parent!=null)
				{
					//IJ.write("show parent");
					TreePanel panel = pcp.getPanelForNode(parent);
					if(panel==null) panel = pcp.newPanel(parent);
					if(panel!=null) panel.setVisible();
				}
				return;
			}
			else
			{
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
	public void windowClosing(WindowEvent e)
	{
		if(isMainPanel)
			pcp.saveProperties();
		else pcp.unsetPanelShowingProperty(getRootPath().toString());
	}

	public void windowActivated(WindowEvent e){}
	public void windowClosed(WindowEvent e)
	{
	}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}


	public void treeCollapsed (TreeExpansionEvent ev)
	{
		String evPathString = ev.getPath().toString();
    evPathString = evPathString.substring(evPathString.indexOf("[")+1,evPathString.lastIndexOf("]"));
		evPathString = evPathString.substring(getTitle().length()+2,evPathString.length());
		String rootPath = getRootPath().toString();
		rootPath = rootPath.substring(rootPath.indexOf("[")+1,rootPath.lastIndexOf("]"));
		String path = "["+rootPath +", "+evPathString+"]";
		//IJ.write("collapse");
		pcp.unsetExpandedStateProperty(path);
	}

	public void treeExpanded(TreeExpansionEvent ev)
	{
		TreePath evPath = ev.getPath();
		//DefaultMutableTreeNode node = (DefaultMutableTreeNode)evPath.getLastPathComponent();
		String evPathString = ev.getPath().toString();
		evPathString = pcp.pStr2Key(evPathString);
		evPathString = evPathString.substring(getTitle().length()+1,evPathString.length());
		String rootPath = getRootPath().toString();
		rootPath = pcp.pStr2Key(rootPath);
		//String path = rootPath+"."+evPathString;
		String path = rootPath+"."+evPathString;
		if(pcp.hasPanelShowingProperty(path))
		{
			Hashtable panels = pcp.getPanels();
			TreePanel p = (TreePanel)panels.get(path);
			if(p!=null) p.close();
		}
		//IJ.write("expansion");
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

	void tearOff()
	{
		tearOff(null);
	}

	void tearOff(DefaultMutableTreeNode node)
	{
		tearOff(node, null);
	}

	void tearOff(DefaultMutableTreeNode node, Point pnt)
	{
		isDragging = false;
		if(node==null)
			node = (DefaultMutableTreeNode)pTree.getLastSelectedPathComponent();
		if(node.isLeaf()) return;
		TreeNode[] nPath = node.getPath();
		TreeNode[] rPath = root.getPath();
		DefaultMutableTreeNode[] tPath = new DefaultMutableTreeNode[nPath.length-rPath.length+1];
		for(int i=0; i<tPath.length; i++)
		{
			tPath[i] = (DefaultMutableTreeNode)nPath[i+rPath.length-1];
		}
		TreePath path = new TreePath(nPath);
		TreePath localPath = new TreePath(tPath);
		String pathString = localPath.toString();
		//IJ.write("to be collapsed "+pathString);
		TreePanel p = pcp.getPanelForNode(node);
		if(p==null)
		{
			if(pnt!=null)
				p = pcp.newPanel(node, pnt);
			else
				p = pcp.newPanel(node);
			pTree.collapsePath(localPath);
		}
		else
		{
			if(pnt!=null)
			{
				p.setLocation(pnt);
			}
			p.setVisible();
			pTree.collapsePath(localPath);
		}
	}

	/** Fires an ActionEvent upon double-click on the plugin item (leaf node) in the JTree */
	void toAction()
	{
			DefaultMutableTreeNode nde=(DefaultMutableTreeNode)pTree.getLastSelectedPathComponent();
			// if the node has children then do nothing (return)
			if (nde.getChildCount()>0) return;
			String aCmd=nde.toString();
			processEvent(new ActionEvent(this,ActionEvent.ACTION_PERFORMED,aCmd));
	}

	void setVisible()
	{
		//IJ.write("setVisible at "+defaultLocation.getX()+" "+defaultLocation.getY());
		if(!(pFrame.isVisible()))
		{
			restoreExpandedNodes();
			if(defaultLocation!=null) pFrame.setLocation(defaultLocation);
			pFrame.setVisible(true);
		}
		pcp.setPanelShowingProperty(getRootPath().toString());
	}

	void setLocation(Point p)
	{
		if(p!=null) defaultLocation = p;
	}

	void close()
	{
		pFrame.dispatchEvent(new WindowEvent(pFrame,WindowEvent.WINDOW_CLOSING));
		pcp.unsetPanelShowingProperty(getRootPath().toString());
	}

	private void showHelp() {pcp.showHelp();}

} // TreePanel


