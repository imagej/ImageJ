package ij.text;
import java.awt.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.awt.datatransfer.*;
import ij.*;
import ij.plugin.filter.Analyzer;
import ij.plugin.Distribution;
import ij.io.SaveDialog;
import ij.measure.*;
import ij.util.Tools;
import ij.plugin.frame.Recorder;
import ij.gui.*;
import ij.macro.Interpreter;


/**
This is an unlimited size text panel with tab-delimited,
labeled and resizable columns. It is based on the hGrid
class at
    http://www.lynx.ch/contacts/~/thomasm/Grid/index.html.
*/
public class TextPanel extends Panel implements AdjustmentListener,
	MouseListener, MouseMotionListener, KeyListener,  ClipboardOwner,
	ActionListener, MouseWheelListener, Runnable {

	static final int DOUBLE_CLICK_THRESHOLD = 650;
	// height / width
	int iGridWidth,iGridHeight;
	int iX,iY;
	// data
	String[] sColHead;
	Vector vData;
	int[] iColWidth;
	int iColCount,iRowCount;
	int iRowHeight,iFirstRow;
	// scrolling
	Scrollbar sbHoriz,sbVert;
	int iSbWidth,iSbHeight;
	boolean bDrag;
	int iXDrag,iColDrag;

	boolean headings = true;
	String title = "";
	String labels;
	KeyListener keyListener;
	Cursor resizeCursor = new Cursor(Cursor.E_RESIZE_CURSOR);
  	Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
	int selStart=-1, selEnd=-1,selOrigin=-1, selLine=-1;
	TextCanvas tc;
	PopupMenu pm;
	boolean columnsManuallyAdjusted;
	long mouseDownTime;
    String filePath;
    ResultsTable rt;
    boolean unsavedLines;
    String searchString;
    Menu fileMenu, editMenu;
    boolean menusExtended;
    boolean saveAsCSV;


	/** Constructs a new TextPanel. */
	public TextPanel() {
		tc = new TextCanvas(this);
		setLayout(new BorderLayout());
		add("Center",tc);
		sbHoriz=new Scrollbar(Scrollbar.HORIZONTAL);
		GUI.fixScrollbar(sbHoriz);
		sbHoriz.addAdjustmentListener(this);
		sbHoriz.setFocusable(false); // prevents scroll bar from blinking on Windows
		add("South", sbHoriz);
		sbVert=new Scrollbar(Scrollbar.VERTICAL);
		GUI.fixScrollbar(sbVert);
		sbVert.addAdjustmentListener(this);
		sbVert.setFocusable(false);
		ImageJ ij = IJ.getInstance();
		if (ij!=null) {
			sbHoriz.addKeyListener(ij);
			sbVert.addKeyListener(ij);
		}
		add("East", sbVert);
		addPopupMenu();
	}

	/** Constructs a new TextPanel. */
	public TextPanel(String title) {
		this();
		this.title = title;
		if (title.equals("Results") || title.endsWith("(Results)")) {
			pm.addSeparator();
			addPopupItem("Clear Results");
			addPopupItem("Summarize");
			addPopupItem("Distribution...");
			addPopupItem("Set Measurements...");
		}
	}

	void addPopupMenu() {
		pm = new PopupMenu();
		GUI.scalePopupMenu(pm);
		addPopupItem("Save As...");
		addPopupItem("Table Action");
		pm.addSeparator();
		addPopupItem("Cut");
		addPopupItem("Copy");
		addPopupItem("Clear");
		addPopupItem("Select All");
		add(pm);
	}

	void addPopupItem(String s) {
		MenuItem mi=new MenuItem(s);
		mi.addActionListener(this);
		pm.add(mi);
	}

	/**
	Clears this TextPanel and sets the column headings to
	those in the tab-delimited 'headings' String. Set 'headings'
	to "" to use a single column with no headings.
	*/
	public synchronized void setColumnHeadings(String labels) {
		//if (count++==5) throw new IllegalArgumentException();
		boolean sameLabels = labels.equals(this.labels);
		this.labels = labels;
		if (labels.equals("")) {
			iColCount = 1;
			sColHead=new String[1];
			sColHead[0] = "";
		} else {
			if (labels.endsWith("\t"))
				this.labels = labels.substring(0, labels.length()-1);
			sColHead = this.labels.split("\t");
        	iColCount = sColHead.length;
		}
		flush();
		vData=new Vector();
		if (!(iColWidth!=null && iColWidth.length==iColCount && sameLabels && iColCount!=1)) {
			iColWidth=new int[iColCount];
			columnsManuallyAdjusted = false;
		}
		iRowCount=0;
		resetSelection();
		adjustHScroll();
		tc.repaint();
	}

	/** Returns the column headings as a tab-delimited string. */
	public String getColumnHeadings() {
		return labels==null?"":labels;
	}

	public synchronized void updateColumnHeadings(String labels) {
		this.labels = labels;
		if (labels.equals("")) {
			iColCount = 1;
			sColHead=new String[1];
			sColHead[0] = "";
		} else {
			if (labels.endsWith("\t"))
				this.labels = labels.substring(0, labels.length()-1);
			sColHead = this.labels.split("\t");
        	iColCount = sColHead.length;
			iColWidth=new int[iColCount];
			columnsManuallyAdjusted = false;
		}
	}

	public void setFont(Font font, boolean antialiased) {
		tc.fFont = font;
		tc.iImage = null;
		tc.fMetrics = null;
		tc.antialiased = antialiased;
		iColWidth[0] = 0;
		if (isShowing()) updateDisplay();
	}

	/** Adds a single line to the end of this TextPanel. */
	public void appendLine(String text) {
		if (vData==null)
			setColumnHeadings("");
		char[] chars = text.toCharArray();
		vData.addElement(chars);
		iRowCount++;
		if (isShowing()) {
			if (iColCount==1 && tc.fMetrics!=null) {
				iColWidth[0] = Math.max(iColWidth[0], tc.fMetrics.charsWidth(chars,0,chars.length));
				adjustHScroll();
			}
			updateDisplay();
			unsavedLines = true;
		}
	}

	/** Adds one or more lines to the end of this TextPanel. */
	public void append(String text) {
		if (text==null) text="null";
		if (vData==null)
			setColumnHeadings("");
		if (text.length()==1 && text.equals("\n"))
			text = "";
		String[] lines = text.split("\n");
		for (int i=0; i<lines.length; i++)
			appendWithoutUpdate(lines[i]);
		if (isShowing()) {
			updateDisplay();
			unsavedLines = true;
		}
	}

	/** Adds strings contained in an ArrayList to the end of this TextPanel. */
	public void append(ArrayList list) {
		if (list==null) return;
		if (vData==null) setColumnHeadings("");
		for (int i=0; i<list.size(); i++)
			appendWithoutUpdate((String)list.get(i));
		if (isShowing()) {
			updateDisplay();
			unsavedLines = true;
		}
	}

	/** Adds a single line to the end of this TextPanel without updating the display. */
	public void appendWithoutUpdate(String data) {
		if (vData!=null) {
			char[] chars = data.toCharArray();
			vData.addElement(chars);
			iRowCount++;
		}
	}

	public void updateDisplay() {
		iY=iRowHeight*(iRowCount+1);
		adjustVScroll();
		if (iColCount>1 && iRowCount<=10 && !columnsManuallyAdjusted)
			iColWidth[0] = 0; // forces column width calculation
		tc.repaint();
	}

	String getCell(int column, int row) {
		if (column<0||column>=iColCount||row<0||row>=iRowCount)
			return null;
		return new String(tc.getChars(column, row));
	}

	synchronized void adjustVScroll() {
		if(iRowHeight==0) return;
		Dimension d = tc.getSize();
		int value = iY/iRowHeight;
		int visible = d.height/iRowHeight;
		int maximum = iRowCount+1;
		if (visible<0) visible=0;
		if (visible>maximum) visible=maximum;
		if (value>(maximum-visible)) value=maximum-visible;
		sbVert.setValues(value,visible,0,maximum);
		iY=iRowHeight*value;
	}

	synchronized void adjustHScroll() {
		if (iRowHeight==0) return;
		Dimension d = tc.getSize();
		int w=0;
		for (int i=0; i<iColCount; i++)
			w+=iColWidth[i];
		iGridWidth=w;
		sbHoriz.setValues(iX,d.width,0,iGridWidth);
		iX=sbHoriz.getValue();
	}

	public void adjustmentValueChanged (AdjustmentEvent e) {
		iX=sbHoriz.getValue();
 		iY=iRowHeight*sbVert.getValue();
		tc.repaint();
 	}

	private void showLinePos() { // show line numbers in status bar (Norbert Visher)
		int startLine = getSelectionStart() +1;
		int endLine = getSelectionEnd() + 1;
		String msg = "Line " + startLine;
		if (startLine != endLine) {
			msg += "-" + endLine;
		}
		if (!msg.equals("Line 0"))
			IJ.showStatus(msg);
	}
	
	public void mousePressed (MouseEvent e) {
		int x=e.getX(), y=e.getY();
		if (e.isPopupTrigger() || e.isMetaDown())
			pm.show(e.getComponent(),x,y);
 		else if (e.isShiftDown())
			extendSelection(x, y);
		else {
 			select(x, y);
 			handleDoubleClick();
 		}
	}

	void handleDoubleClick() {//Marcel Boeglin 2019.10.07
		boolean overlayList = title.startsWith("Overlay Elements of ");
		if (selStart<0 || selStart!=selEnd || (iColCount!=1&&!overlayList))
			return;
		boolean doubleClick = System.currentTimeMillis()-mouseDownTime<=DOUBLE_CLICK_THRESHOLD;
		mouseDownTime = System.currentTimeMillis();
		if (doubleClick) {
			char[] chars = (char[])(vData.elementAt(selStart));
			String s = new String(chars);
			if (overlayList) {
				String owner = title.substring(20, title.length());
				String[] titles = WindowManager.getImageTitles();
				for (int i=0; i<titles.length; i++) {
					String t = titles[i];
					if (titles[i].equals(owner)) {
						ImagePlus imp = WindowManager.getImage(owner);
						WindowManager.setTempCurrentImage(imp);//?
						Frame frame = imp.getWindow();
						frame.toFront();
						if (frame.getState()==Frame.ICONIFIED)
							frame.setState(Frame.NORMAL);
						handleDoubleClickInOverlayList(s);
						break;
					}
				}
				return;
			}
			int index = s.indexOf(": ");
			if (index>-1 && !s.endsWith(": "))
				s = s.substring(index+2); // remove sequence number added by ListFilesRecursively
			if (s.indexOf(File.separator)!=-1 ||  s.indexOf(".")!=-1) {
				filePath = s;
				Thread thread = new Thread(this, "Open");
				thread.setPriority(thread.getPriority()-1);
				thread.start();
			}
		}
	}

	private void handleDoubleClickInOverlayList(String s) {//Marcel Boeglin 2019.10.09
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			return;
		Overlay overlay = imp.getOverlay();
		if (overlay==null)
			return;
		String[] columns = s.split("\t");
		int index = (int)Tools.parseDouble(columns[1]);
		Roi roi = overlay.get(index);
		if (roi==null)
			return;
		if (imp.isHyperStack()) {
			int c = roi.getCPosition();
			int z = roi.getZPosition();
			int t = roi.getTPosition();
			c = c==0?imp.getChannel():c;
			z = z==0?imp.getSlice():z;
			t = t==0?imp.getFrame():t;
			imp.setPosition(c, z, t);
		} else {
			int p = roi.getPosition();
			if (p>=1 && p<=imp.getStackSize())
				imp.setPosition(p);
		}
		imp.setRoi(roi);
	}
	
    /** For better performance, open double-clicked files on
    	separate thread instead of on event dispatch thread. */
    public void run() {
        if (filePath==null)
        	return;
        File f = new File(filePath);
		if (f.exists() || filePath.startsWith("https"))
			IJ.open(filePath);
    }

	public void mouseExited (MouseEvent e) {
		if(bDrag) {
			setCursor(defaultCursor);
			bDrag=false;
		}
	}

	public void mouseMoved (MouseEvent e) {
		int x=e.getX(), y=e.getY();
		if(y<=iRowHeight) {
			int xb=x;
			x=x+iX-iGridWidth;
			int i=iColCount-1;
			for(;i>=0;i--) {
				if(x>-7 && x<7) break;
				x+=iColWidth[i];
			}
			if(i>=0) {
				if(!bDrag) {
					setCursor(resizeCursor);
					bDrag=true;
					iXDrag=xb-iColWidth[i];
					iColDrag=i;
				}
				return;
			}
		}
		if(bDrag) {
			setCursor(defaultCursor);
			bDrag=false;
		}
	}

	public void mouseDragged (MouseEvent e) {
		if (e.isPopupTrigger() || e.isMetaDown())
			return;
		int x=e.getX(), y=e.getY();
		if(bDrag && x<tc.getSize().width) {
			int w=x-iXDrag;
			if(w<0) w=0;
			iColWidth[iColDrag]=w;
			columnsManuallyAdjusted = true;
			adjustHScroll();
			tc.repaint();
		} else {
			extendSelection(x, y);
		}
	}

 	public void mouseReleased (MouseEvent e) {
			showLinePos();
	}
	
	public void mouseClicked (MouseEvent e) {
		if (e.getClickCount() == 2 && !e.isConsumed()) {
			e.consume();
			boolean doubleClickableTable = title!=null && (title.equals("Log")||title.startsWith("Overlay Elements"));
			Hashtable commands = Menus.getCommands();
			boolean tableActionCommand = commands!=null && commands.get("Table Action")!=null;
			if (!tableActionCommand)
				tableActionCommand = ij.plugin.MacroInstaller.isMacroCommand("Table Action");
			if (doubleClickableTable || !tableActionCommand)
				return;
			String options = title+"|"+getSelectionStart()+"|"+getSelectionEnd();
			IJ.run("Table Action", options);
		}
	}
	
	public void mouseWheelMoved(MouseWheelEvent event) {
		synchronized(this) {
			int rot = event.getWheelRotation();
			sbVert.setValue(sbVert.getValue()+rot);
			iY=iRowHeight*sbVert.getValue();
			tc.repaint();
		}
	}

	public void mouseEntered (MouseEvent e) {}

	private void scroll(int inc) {
		synchronized(this) {
			sbVert.setValue(sbVert.getValue()+inc);
			iY=iRowHeight*sbVert.getValue();
			tc.repaint();
		}
	}

	/** Unused keyPressed and keyTyped events will be passed to 'listener'.*/
	public void addKeyListener(KeyListener listener) {
		keyListener = listener;
	}

	public void addMouseListener(MouseListener listener) {
		tc.addMouseListener(listener);
	}

	public void keyPressed(KeyEvent e) {
		int key = e.getKeyCode();
		if (key==KeyEvent.VK_BACK_SPACE || key==KeyEvent.VK_DELETE)
			clearSelection();
		else if (key==KeyEvent.VK_UP)
			scroll(-1);
		else if (key==KeyEvent.VK_DOWN)
			scroll(1);
		else if (keyListener!=null&&key!=KeyEvent.VK_S&& key!=KeyEvent.VK_C && key!=KeyEvent.VK_X
		&& key!=KeyEvent.VK_A && key!=KeyEvent.VK_F && key!=KeyEvent.VK_G)
			keyListener.keyPressed(e);
	}

	public void keyReleased (KeyEvent e) {
		IJ.setKeyUp(e.getKeyCode());
		showLinePos();
	}

	public void keyTyped (KeyEvent e) {
		if (keyListener!=null)
			keyListener.keyTyped(e);
	}

	public void actionPerformed (ActionEvent e) {
		String cmd=e.getActionCommand();
		doCommand(cmd);
	}

 	void doCommand(String cmd) {
 		if (cmd==null)
 			return;
		if (cmd.equals("Save As..."))
			saveAs("");
		else if (cmd.equals("Cut"))
			cutSelection();
		else if (cmd.equals("Copy"))
			copySelection();
		else if (cmd.equals("Clear"))
			doClear();
		else if (cmd.equals("Select All"))
			selectAll();
		else if (cmd.equals("Find..."))
			find(null);
		else if (cmd.equals("Find Next"))
			find(searchString);
		else if (cmd.equals("Rename..."))
			rename(null);
		else if (cmd.equals("Duplicate..."))
			duplicate();
		else if (cmd.equals("Summarize")) {
			if ("Results".equals(title))
				IJ.doCommand("Summarize");
			else {
				Analyzer analyzer = new Analyzer(null, getResultsTable());
				analyzer.summarize();
			}
		} else if (cmd.equals("Distribution...")) {
			if ("Results".equals(title))
				IJ.doCommand("Distribution...");
			else
				new Distribution().run(getResultsTable());
		} else if (cmd.equals("Clear Results"))
			doClear();
		else if (cmd.equals("Set Measurements..."))
			IJ.doCommand("Set Measurements...");
 		else if (cmd.equals("Options..."))
			IJ.doCommand("Input/Output...");
 		else if (cmd.equals("Apply Macro..."))
			new ResultsTableMacros(rt);
 		else if (cmd.equals("Sort..."))
			sort();
 		else if (cmd.equals("Plot..."))
			new PlotContentsDialog(title, getOrCreateResultsTable()).showDialog(getParent() instanceof Frame ? (Frame)getParent() : null);
		else if (cmd.equals("Table Action")) {
			String options = title+"|"+getSelectionStart()+"|"+getSelectionEnd();
			IJ.run("Table Action", options);
		}
	}

 	public void lostOwnership (Clipboard clip, Transferable cont) {}

	private void find(String s) {
		int first = 0;
		if (s==null) {
			GenericDialog gd = new GenericDialog("Find...", getTextWindow());
			gd.addStringField("Find: ", searchString, 20);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			s = gd.getNextString();
		} else {
			if (selEnd>=0 && selEnd<iRowCount-1)
				first = selEnd + 1;
			else {
				IJ.beep();
				return;
			}
		}
		if (s.equals(""))
			return;
		boolean found = false;
		for (int i=first; i<iRowCount; i++) {
			String line = new String((char[])(vData.elementAt(i)));
			if (line.contains(s)) {
				setSelection(i, i);
				found = true;
				first = i + 1;
				break;
			}
		}
		if (!found) {
			IJ.beep();
			first = 0;
		}
		searchString = s;
	}

	private TextWindow getTextWindow() {
		Component comp = getParent();
		if (comp==null || !(comp instanceof TextWindow))
			return null;
		else
			return (TextWindow)comp;
	}

	void rename(String title2) {
		ResultsTable rt2 = getOrCreateResultsTable();
		if (rt2==null)
			return;
		if (title2!=null && title2.equals(""))
			title2 = null;
		TextWindow tw = getTextWindow();
		if (tw==null)
			return;
		if (title2==null) {
			GenericDialog gd = new GenericDialog("Rename", tw);
			gd.addStringField("Title:", getNewTitle(title), 20);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			title2 = gd.getNextString();
		}
		String title1 = title;
		if (title!=null && title.equals("Results")) {
			IJ.setTextPanel(null);
			Analyzer.setUnsavedMeasurements(false);
			Analyzer.setResultsTable(null);
			Analyzer.resetCounter();
		}
		if (title2.equals("Results")) {
			//tw.setVisible(false);
			tw.dispose();
			WindowManager.removeWindow(tw);
			flush();
			rt2.show("Results");
		} else {
			tw.setTitle(title2);
			title = title2;
			rt2.show(title);
		}
		Menus.updateWindowMenuItem(title1, title2);
		if (Recorder.record) {
			if (Recorder.scriptMode())
				Recorder.recordString("IJ.renameResults(\""+title1+"\", \""+title2+"\");\n");
			else
				Recorder.record("Table.rename", title1, title2);
		}
	}

	void duplicate() {
		ResultsTable rt2 = getOrCreateResultsTable();
		if (rt2==null)
			return;
		rt2 = (ResultsTable)rt2.clone();
		String title2 = IJ.getString("Title:", getNewTitle(title));
		if (!title2.equals("")) {
			if (title2.equals("Results")) title2 = "Results2";
			rt2.show(title2);
		}
	}

	private String getNewTitle(String oldTitle) {
		if (oldTitle==null)
			return "Table2";
		String title2 = oldTitle;
		if (title2.endsWith("-1") || title2.endsWith("-2"))
			title2 = title2.substring(0,title.length()-2);
		String title3 = title2+"-1";
		if (title3.equals(oldTitle))
			title3 = title2+"-2";
        return title3;
	}

	void select(int x,int y) {
		Dimension d = tc.getSize();
		if(iRowHeight==0 || x>d.width || y>d.height)
			return;
     	int r=(y/iRowHeight)-1+iFirstRow;
     	int lineWidth = iGridWidth;
		if (iColCount==1 && tc.fMetrics!=null && r>=0 && r<iRowCount) {
			char[] chars = (char[])vData.elementAt(r);
			lineWidth = Math.max(tc.fMetrics.charsWidth(chars,0,chars.length), iGridWidth);
		}
      	if (r>=0 && r<iRowCount && x<lineWidth) {
			selOrigin = r;
			selStart = r;
			selEnd = r;
		} else {
			resetSelection();
			selOrigin = r;
			if (r>=iRowCount)
				selOrigin = iRowCount-1;
		}
		tc.repaint();
		selLine=r;
		Interpreter interp = Interpreter.getInstance();
		if (interp!=null && title.equals("Debug"))
			interp.showArrayInspector(r);
	}

	void extendSelection(int x,int y) {
		Dimension d = tc.getSize();
		if(iRowHeight==0 || x>d.width || y>d.height)
			return;
     	int r=(y/iRowHeight)-1+iFirstRow;
     	if(r>=0 && r<iRowCount) {
			if (r<selOrigin) {
				selStart = r;
				selEnd = selOrigin;

			} else {
				selStart = selOrigin;
				selEnd = r;
			}
		}
		tc.repaint();
		selLine=r;
	}

    /** Converts a y coordinate in pixels into a row index. */
    public int rowIndex(int y) {
        if (y > tc.getSize().height)
        	return -1;
        else
        	return (y/iRowHeight)-1+iFirstRow;
    }

	/**
	Copies the current selection to the system clipboard.
	Returns the number of characters copied.
	*/
	public int copySelection() {
		if (Recorder.record && title.equals("Results"))
			Recorder.record("String.copyResults");
		if (selStart==-1 || selEnd==-1)
			return copyAll();
		StringBuffer sb = new StringBuffer();
		ResultsTable rt2 = getResultsTable();
		boolean hasRowNumers = rt2!=null && rt2.showRowNumbers();
		if (Prefs.copyColumnHeaders && labels!=null && !labels.equals("") && selStart==0 && selEnd==iRowCount-1) {
			if (hasRowNumers && Prefs.noRowNumbers) {
				String s = labels;
				int index = s.indexOf("\t");
				if (index!=-1)
					s = s.substring(index+1, s.length());
				sb.append(s);
			} else
				sb.append(labels);
			sb.append('\n');
		}
		for (int i=selStart; i<=selEnd; i++) {
			char[] chars = (char[])(vData.elementAt(i));
			String s = new String(chars);
			if (s.endsWith("\t"))
				s = s.substring(0, s.length()-1);
			if (hasRowNumers && Prefs.noRowNumbers && labels!=null && !labels.equals("")) {
				int index = s.indexOf("\t");
				if (index!=-1)
					s = s.substring(index+1, s.length());
				sb.append(s);
			} else
				sb.append(s);
			if (i<selEnd || selEnd>selStart) sb.append('\n');
		}
		String s = new String(sb);
		Clipboard clip = getToolkit().getSystemClipboard();
		if (clip==null) return 0;
		StringSelection cont = new StringSelection(s);
		clip.setContents(cont,this);
		if (s.length()>0) {
			IJ.showStatus((selEnd-selStart+1)+" lines copied to clipboard");
			if (this.getParent() instanceof ImageJ)
				Analyzer.setUnsavedMeasurements(false);
		}
		return s.length();
	}

	int copyAll() {
		selectAll();
		int count = selEnd - selStart + 1;
		if (count>0)
			copySelection();
		resetSelection();
		unsavedLines = false;
		return count;
	}

	void cutSelection() {
		if (selStart==-1 || selEnd==-1)
			selectAll();
		copySelection();
		clearSelection();
	}

	/** Implements the Clear command. */
	public void doClear() {
		if (getLineCount()>0 && selStart!=-1 && selEnd!=-1)
			clearSelection();
		else if ("Results".equals(title))
			IJ.doCommand("Clear Results");
		else {
			selectAll();
			clearSelection();
		}
	}

	/** Deletes the selected lines. */
	public void clearSelection() {
		if (selStart==-1 || selEnd==-1) {
			if (getLineCount()>0)
				IJ.error("Text selection required");
			return;
		}
		if (Recorder.record) {
			if (Recorder.scriptMode())
				Recorder.recordString("IJ.deleteRows("+selStart+", "+selEnd+");\n");
			else {
				if ("Results".equals(title))
					Recorder.record("Table.deleteRows", selStart, selEnd);
				else
					Recorder.record("Table.deleteRows", selStart, selEnd, title);
			}
		}
		int first=selStart, last=selEnd, rows=iRowCount;
		if (selStart==0 && selEnd==(iRowCount-1)) {
			vData.removeAllElements();
			iRowCount = 0;
			if (rt!=null) {
				if (IJ.isResultsWindow() && IJ.getTextPanel()==this) {
					Analyzer.setUnsavedMeasurements(false);
					Analyzer.resetCounter();
				} else
					rt.reset();
			}
		} else {
			int rowCount = iRowCount;
			boolean atEnd = rowCount-selEnd<8;
			int count = selEnd-selStart+1;
			for (int i=0; i<count; i++) {
				vData.removeElementAt(selStart);
				iRowCount--;
			}
			if (rt!=null && rowCount==rt.size()) {
				for (int i=0; i<count; i++)
					rt.deleteRow(selStart);
				rt.show(title);
				if (!atEnd) {
					iY = 0;
					tc.repaint();
				}
			}
		}
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null)
			Overlay.updateTableOverlay(imp, first, last, rows);
		selStart=-1; selEnd=-1; selOrigin=-1; selLine=-1;
		adjustVScroll();
		tc.repaint();
	}

	/** Deletes all the lines. */
	public synchronized void clear() {
		if (vData==null) return;
		vData.removeAllElements();
		iRowCount = 0;
		selStart=-1; selEnd=-1; selOrigin=-1; selLine=-1;
		adjustVScroll();
		tc.repaint();
	}

	/** Selects all the lines in this TextPanel. */
	public void selectAll() {
		if (selStart==0 && selEnd==iRowCount-1) {
			resetSelection();
			IJ.showStatus("");
			return;
		}
		selStart = 0;
		selEnd = iRowCount-1;
		selOrigin = 0;
		tc.repaint();
		selLine=-1;
		showLinePos();
	}

	/** Clears the selection, if any. */
	public void resetSelection() {
		selStart=-1;
		selEnd=-1;
		selOrigin=-1;
		selLine=-1;
		if (iRowCount>0)
			tc.repaint();
	}

	/** Creates a selection and insures it is visible. */
	public void setSelection (int startLine, int endLine) {
		if (startLine>endLine) endLine = startLine;
		if (startLine<0) startLine = 0;
		if (endLine<0) endLine = 0;
		if (startLine>=iRowCount) startLine = iRowCount-1;
		if (endLine>=iRowCount) endLine = iRowCount-1;
		selOrigin = startLine;
		selStart = startLine;
		selEnd = endLine;
		int vstart = sbVert.getValue();
		int visible = sbVert.getVisibleAmount()-1;
		if (startLine<vstart) {
			sbVert.setValue(startLine);
			iY=iRowHeight*startLine;
		} else if (endLine>=vstart+visible) {
			vstart = endLine - visible + 1;
			if (vstart<0) vstart = 0;
			sbVert.setValue(vstart);
			iY=iRowHeight*vstart;
		}
		tc.repaint();
	}

	/** Updates the vertical scroll bar so that the specified row is visible. */
	public void showRow(int rowIndex) {
		showCell(rowIndex, null);
	}


	/** Updates the scroll bars so that the specified cell is visible. */
	public void showCell(int rowIndex, String column) {
		if (rowIndex<0) rowIndex=0;
		if (rowIndex>=iRowCount) rowIndex=iRowCount-1;
		sbVert.setValue(rowIndex);
		iY=iRowHeight*sbVert.getValue();
		int hstart = sbHoriz.getValue();
		int hVisible = sbHoriz.getVisibleAmount()-1;
		int col = 0;
		if (column!=null && sColHead!=null && iColWidth!=null) {
			for (int i=0; i<sColHead.length; i++) {
				if (column.equals(sColHead[i])) {
					for (int j=0; j<i; j++)
						col += iColWidth[j];
					break;
				}
			}
		}
		sbHoriz.setValue(col);
		iX=col;
		tc.repaint();
	}

	/** Writes all the text in this TextPanel to a file. */
	public void save(PrintWriter pw) {
		resetSelection();
		if (labels!=null && !labels.equals("")) {
			String labels2 = labels;
			if (saveAsCSV)
				labels2 = labels2.replaceAll("\t",",");
			pw.println(labels2);
		}
		for (int i=0; i<iRowCount; i++) {
			char[] chars = (char[])(vData.elementAt(i));
			String s = new String(chars);
			if (s.endsWith("\t"))
				s = s.substring(0, s.length()-1);
			if (saveAsCSV)
				s = s.replaceAll("\t",",");
			pw.println(s);
		}
		unsavedLines = false;
	}

	/** Saves the text in this TextPanel to a file. Set 'path' to "" to
	 * display a "save as" dialog. Returns 'false' if the user cancels
	 * the dialog.
	*/
	public boolean saveAs(String path) {
		boolean isResults = IJ.isResultsWindow() && IJ.getTextPanel()==this;
		boolean summarized = false;
		if (isResults) {
			String lastLine = iRowCount>=2?getLine(iRowCount-2):null;
			summarized = lastLine!=null && lastLine.startsWith("Max");
		}
		String fileName = null;
		if (rt!=null && rt.size()>0 && !summarized) {
			if (path==null || path.equals("")) {
				IJ.wait(10);
				String name = isResults?"Results":title;
				SaveDialog sd = new SaveDialog("Save Table", name, Prefs.defaultResultsExtension());
				fileName = sd.getFileName();
				if (fileName==null)
					return false;
				path = sd.getDirectory() + fileName;
			}
			rt.saveAndRename(path);
			TextWindow tw = getTextWindow();
			String title2 = rt.getTitle();
			if (tw!=null && !"Results".equals(title)) {
				tw.setTitle(title2);
				Menus.updateWindowMenuItem(title, title2);
				title = title2;
			}
		} else {
			if (path.equals("")) {
				IJ.wait(10);
				boolean hasHeadings = !getColumnHeadings().equals("");
				String ext = isResults||hasHeadings?Prefs.defaultResultsExtension():".txt";
				SaveDialog sd = new SaveDialog("Save as Text", title, ext);
				String file = sd.getFileName();
				if (file==null)
					return false;
				path = sd.getDirectory() + file;
			}
			PrintWriter pw = null;
			try {
				FileOutputStream fos = new FileOutputStream(path);
				BufferedOutputStream bos = new BufferedOutputStream(fos);
				pw = new PrintWriter(bos);
			}
			catch (IOException e) {
				IJ.error("Save As>Text", e.getMessage());
				return true;
			}
			saveAsCSV = path.endsWith(".csv");
			save(pw);
			saveAsCSV = false;
			pw.close();
		}
		if (isResults) {
			Analyzer.setUnsavedMeasurements(false);
			if (Recorder.record && !IJ.isMacro())
				Recorder.record("saveAs", "Results", path);
		} else if (rt!=null) {
			if (Recorder.record && !IJ.isMacro())
				Recorder.record("saveAs", "Results", path);
		} else {
			if (Recorder.record && !IJ.isMacro())
				Recorder.record("saveAs", "Text", path);
		}
		IJ.showStatus("");
		return true;
	}

	/** Returns all the text as a string. */
	public synchronized String getText() {
		if (vData==null)
			return "";
		StringBuffer sb = new StringBuffer();
		if (labels!=null && !labels.equals("")) {
			sb.append(labels);
			sb.append('\n');
		}
		for (int i=0; i<iRowCount; i++) {
			if (vData==null) break;
			char[] chars = (char[])(vData.elementAt(i));
			sb.append(chars);
			sb.append('\n');
		}
		return new String(sb);
	}

	public void setTitle(String title) {
		this.title = title;
	}

	/** Returns the number of lines of text in this TextPanel. */
	public int getLineCount() {
		return iRowCount;
	}

	/** Returns the specified line as a string. The argument
		must be greater than or equal to zero and less than
		the value returned by getLineCount(). */
	public String getLine(int index) {
		if (index<0 || index>=iRowCount)
			throw new IllegalArgumentException("index out of range: "+index);
		return new String((char[])(vData.elementAt(index)));
	}

	/** Replaces the contents of the specified line, where 'index'
		must be greater than or equal to zero and less than
		the value returned by getLineCount(). */
	public void setLine(int index, String s) {
		if (index<0 || index>=iRowCount)
			throw new IllegalArgumentException("index out of range: "+index);
		if (vData!=null) {
			vData.setElementAt(s.toCharArray(), index);
			tc.repaint();
		}
	}

	/** Returns the index of the first selected line, or -1
		if there is no slection. */
	public int getSelectionStart() {
		return selStart;
	}

	/** Returns the index of the last selected line, or -1
		if there is no slection. */
	public int getSelectionEnd() {
		return selEnd;
	}

	/** Sets the ResultsTable associated with this TextPanel. */
	public void setResultsTable(ResultsTable rt) {
		if (IJ.debugMode) IJ.log("setResultsTable: "+rt);
		this.rt = rt;
		if (!menusExtended)
			extendMenus();
	}

	/** Returns the ResultsTable associated with this TextPanel, or null. */
	public ResultsTable getResultsTable() {
		if (IJ.debugMode) IJ.log("getResultsTable: "+rt);
		return rt;
	}

	/** Returns the ResultsTable associated with this TextPanel, or
		attempts to create one and returns the created table. */
	public ResultsTable getOrCreateResultsTable() {
		if ((rt==null||rt.size()==0) && iRowCount>0 && labels!=null && !labels.equals("")) {
			String tmpDir = IJ.getDir("temp");
			if (tmpDir==null) {
				if (IJ.debugMode) IJ.log("getOrCreateResultsTable: tmpDir null");
				return null;
			}
			String path = tmpDir+"temp-table.csv";
			saveAs(path);
			try {
				rt = ResultsTable.open(path);
				new File(path).delete();
			} catch (Exception e) {
				rt = null;
				if (IJ.debugMode) IJ.log("getOrCreateResultsTable: "+e);
			}
		}
		if (IJ.debugMode) IJ.log("getOrCreateResultsTable: "+rt);
		return rt;
	}

	private void extendMenus() {
		pm.addSeparator();
		addPopupItem("Rename...");
		addPopupItem("Duplicate...");
		addPopupItem("Apply Macro...");
		addPopupItem("Sort...");
		addPopupItem("Plot...");
		if (fileMenu!=null) {
			fileMenu.add("Rename...");
			fileMenu.add("Duplicate...");
		}
		if (editMenu!=null) {
			editMenu.addSeparator();
			editMenu.add("Apply Macro...");
		}
		menusExtended = true;
	}

	public void scrollToTop() {
		sbVert.setValue(0);
		iY = 0;
		for (int i=0; i<iColCount; i++)
			tc.calcAutoWidth(i);
		adjustHScroll();
		tc.repaint();
	}

	void flush() {
		if (vData!=null)
			vData.removeAllElements();
		vData = null;
	}
	
	private void sort() {
		ResultsTable rt2 = getOrCreateResultsTable();
		if (rt2==null)
			return;
		String[] headers = rt2.getHeadings();
		String[] headers2 = headers;
		if (headers[0].equals("Label")) {
			headers = new String[headers.length-1];
			for (int i=0; i<headers.length; i++)
				headers[i] = headers2[i+1];
		}
		GenericDialog gd = new GenericDialog("Sort Table");
		gd.addChoice ("Column: ", headers, headers[0]);
		gd.showDialog();
		if (gd.wasCanceled()) 
			return;
		String column = gd.getNextChoice();
		rt2.sort(column);
		rt2.show(title);
		scrollToTop();
		if (Recorder.record)
			Recorder.record("Table.sort", column);
	}

}
