package ij.text;

import java.awt.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.awt.datatransfer.*;
import ij.*;
import ij.plugin.filter.Analyzer;
import ij.io.SaveDialog;
import ij.measure.ResultsTable;
import ij.util.Tools;
import ij.plugin.frame.Recorder;


/**
This is an unlimited size text panel with tab-delimited,
labeled and resizable columns. It is based on the hGrid
class at
    http://www.lynx.ch/contacts/~/thomasm/Grid/index.html.
*/
public class TextPanel extends Panel implements AdjustmentListener,
	MouseListener, MouseMotionListener, KeyListener,  ClipboardOwner,
	ActionListener {

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
  
	/** Constructs a new TextPanel. */
	public TextPanel() {
		tc = new TextCanvas(this);
		setLayout(new BorderLayout());
		add("Center",tc);
		sbHoriz=new Scrollbar(Scrollbar.HORIZONTAL);
		sbHoriz.addAdjustmentListener(this);
		add("South", sbHoriz);
		sbVert=new Scrollbar(Scrollbar.VERTICAL);
		sbVert.addAdjustmentListener(this);
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
		if (title.equals("Results")) {
			pm.addSeparator();
			addPopupItem("Clear Results");
			addPopupItem("Summarize");
			addPopupItem("Distribution...");
			addPopupItem("Set Measurements...");
		}
	}

	void addPopupMenu() {
		pm=new PopupMenu();
		addPopupItem("Save As...");
		pm.addSeparator();
		addPopupItem("Cut");
		addPopupItem("Copy");
		addPopupItem("Clear");
		addPopupItem("Select All");
		addPopupItem("Copy All");
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
		boolean sameLabels = labels.equals(this.labels);
		this.labels = labels;
		if (labels.equals("")) {
			iColCount = 1;
			sColHead=new String[1];
			sColHead[0] = "";
		} else {
			sColHead = Tools.split(labels, "\t");
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
	
	public void setFont(Font font) {
		tc.fFont = font;
		tc.iImage = null;
		tc.fMetrics = null;
		iColWidth[0] = 0;
	}
  
	/** Adds a single line to the end of this TextPanel. */
	public void appendLine(String data) {
		if (vData==null)
			setColumnHeadings("");
		char[] chars = data.toCharArray();
		vData.addElement(chars);
		iRowCount++;
		if (isShowing()) {
			if (iColCount==1 && tc.fMetrics!=null) {
				iColWidth[0] = Math.max(iColWidth[0], tc.fMetrics.charsWidth(chars,0,chars.length));
				adjustHScroll();
			}
			updateDisplay();
		}
	}
	
	/** Adds one or more lines to the end of this TextPanel. */
	public void append(String data) {
		if (data==null) data="null";
		if (vData==null)
			setColumnHeadings("");
		while (true) {
			int p=data.indexOf('\n');
			if (p<0) {
				appendWithoutUpdate(data);
				break;
			}
			appendWithoutUpdate(data.substring(0,p));
			data = data.substring(p+1);
			if (data.equals("")) 
				break;
		}
		if (isShowing())
			updateDisplay();
	}
	
	/** Adds a single line to the end of this TextPanel without updating the display. */
	void appendWithoutUpdate(String data) {
		char[] chars = data.toCharArray();
		vData.addElement(chars);
		iRowCount++;
	}

	void updateDisplay() {
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
		if(iRowHeight==0) return;
		Dimension d = tc.getSize();
		int w=0;
		for(int i=0;i<iColCount;i++)
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
    
	public void mousePressed (MouseEvent e) {
		int x=e.getX(), y=e.getY();
		if (e.isPopupTrigger() || e.isMetaDown())
			pm.show(e.getComponent(),x,y);
 		else if (e.isShiftDown())
			extendSelection(x, y);
		else
 			select(x, y);
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

 	public void mouseReleased (MouseEvent e) {}
	public void mouseClicked (MouseEvent e) {}
	public void mouseEntered (MouseEvent e) {}
	
	/** Unused keyPressed events will be passed to 'listener'.*/
	public void addKeyListener(KeyListener listener) {
		keyListener = listener;
	}
	
	public void keyPressed(KeyEvent e) {
		//boolean cutCopyOK = (e.isControlDown()||e.isMetaDown())
		//	&& selStart!=-1 && selEnd!=-1;
		//if (cutCopyOK && e.getKeyCode()==KeyEvent.VK_C)
		//	copySelection();
		//else if (cutCopyOK && e.getKeyCode()==KeyEvent.VK_X) 
		//	{if (copySelection()>0) clearSelection();}
		//else if (cutCopyOK && e.getKeyCode()==KeyEvent.VK_A) 
		//	selectAll();
		//else if (keyListener!=null)
		//	keyListener.keyPressed(e);
		int key = e.getKeyCode();
		if (keyListener!=null&&key!=KeyEvent.VK_S&& key!=KeyEvent.VK_C && key!=KeyEvent.VK_X&& key!=KeyEvent.VK_A)
			keyListener.keyPressed(e);
		
	}
	
	public void keyReleased (KeyEvent e) {}
	public void keyTyped (KeyEvent e) {}
  
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
			{copySelection();clearSelection();}
		else if (cmd.equals("Copy"))
			copySelection();
		else if (cmd.equals("Clear"))
			clearSelection();
		else if (cmd.equals("Select All"))
			selectAll();
		else if (cmd.equals("Copy All")) {
			selectAll();
			copySelection();
			resetSelection();		
		} else if (cmd.equals("Summarize"))
			IJ.doCommand("Summarize");
		else if (cmd.equals("Distribution..."))
			IJ.doCommand("Distribution...");
		else if (cmd.equals("Clear Results"))
			IJ.doCommand("Clear Results");
		else if (cmd.equals("Set Measurements..."))
			IJ.doCommand("Set Measurements...");
 		else if (cmd.equals("Set File Extension..."))
			IJ.doCommand("Input/Output...");
	}
 	
 	public void lostOwnership (Clipboard clip, Transferable cont) {}

	void select(int x,int y) {
		Dimension d = tc.getSize();
		if(iRowHeight==0 || x>d.width || y>d.height)
			return;
     	int r=(y/iRowHeight)-1+iFirstRow;
      	if(r>=0 && r<iRowCount && x<iGridWidth) {
			selOrigin = r;
			selStart = r;
			selEnd = r;
		} else {
			resetSelection();
			selOrigin = r;
			if (r>=iRowCount)
				selOrigin = iRowCount-1;
			//System.out.println("select: "+selOrigin);
		}
		tc.repaint();
		selLine=r;
	}

	void extendSelection(int x,int y) {
		Dimension d = tc.getSize();
		if(iRowHeight==0 || x>d.width || y>d.height)
			return;
     	int r=(y/iRowHeight)-1+iFirstRow;
		//System.out.println(r+"  "+selOrigin);
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

	/**
	Copies the current selection to the system clipboard. 
	Returns the number of characters copied.
	*/
	public int copySelection() {
		if (selStart==-1 || selEnd==-1) return 0;
		StringBuffer sb = new StringBuffer();
		for (int i=selStart; i<=selEnd; i++) {
			char[] chars = (char[])(vData.elementAt(i));
			sb.append(chars);
			sb.append('\n');
		}
		String s = new String(sb);
		Clipboard clip = getToolkit().getSystemClipboard();
		if (clip==null) return 0;
		StringSelection cont = new StringSelection(s);
		clip.setContents(cont,this);
		if (s.length()>0) {
			IJ.showStatus((selEnd-selStart+1)+" lines copied to clipboard");
			if (this.getParent() instanceof ImageJ)
				Analyzer.setSaved();
		}
		return s.length();
	}
	
	/** Deletes the selected lines. */
	public void clearSelection() {
		if (selStart==-1 || selEnd==-1)
			return;
		if (selStart==0 && selEnd==(iRowCount-1)) {
			vData.removeAllElements();
			iRowCount = 0;
			if (IJ.isResultsWindow() && IJ.getTextPanel()==this) {
				Analyzer.setSaved();
				Analyzer.resetCounter();
			}
		} else {
			int count = selEnd-selStart+1;
			for (int i=0; i<count; i++) {
				vData.removeElementAt(selStart);
				iRowCount--;
			}
			if (IJ.isResultsWindow() && IJ.getTextPanel()==this) {
				ResultsTable rt = ResultsTable.getResultsTable();
				for (int i=0; i<count; i++)
					rt.deleteRow(selStart);
				rt.show("Results");
			}
		}
		selStart=-1; selEnd=-1; selOrigin=-1; selLine=-1; 
		adjustVScroll();
		tc.repaint();
	}
	
	/** Selects all the lines in this TextPanel. */
	public void selectAll() {
		selStart = 0;
		selEnd = iRowCount-1;
		selOrigin = 0;
		tc.repaint();
		selLine=-1;
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
	
	/** Writes all the text in this TextPanel to a file. */
	public void save(PrintWriter pw) {
		resetSelection();
		if (labels!=null && !labels.equals(""))
			pw.println(labels);
		for (int i=0; i<iRowCount; i++) {
			char[] chars = (char[])(vData.elementAt(i));
			pw.println(new String(chars));
		}
	}

	/** Saves all the text in this TextPanel to a file. Set
		'path' to "" to display a save as dialog. */
	public void saveAs(String path) {
		boolean isResults = IJ.isResultsWindow() && IJ.getTextPanel()==this;
		if (path.equals("")) {
			String ext = isResults?Prefs.get("options.ext", ".xls"):".txt";
			SaveDialog sd = new SaveDialog("Save as Text", title, ext);
			String file = sd.getFileName();
			if (file == null) return;
			path = sd.getDirectory() + file;
		}
		PrintWriter pw = null;
		try {
			FileOutputStream fos = new FileOutputStream(path);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			pw = new PrintWriter(bos);
		}
		catch (IOException e) {
			//IJ.write("" + e);
			return;
		}
		save(pw);
		pw.close();
		if (isResults) {
			Analyzer.setSaved();
			if (Recorder.record)
				Recorder.record("saveAs", "Measurements", path);
		} else {
			if (Recorder.record)
				Recorder.record("saveAs", "Text", path);
		}
		IJ.showStatus("");
	}

	/** Returns all the text as a string. */
	public String getText() {
		StringBuffer sb = new StringBuffer();
		if (labels!=null && !labels.equals("")) {
			sb.append(labels);
			sb.append('\n');
		}
		for (int i=0; i<iRowCount; i++) {
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

	void flush() {
		if (vData!=null)
			vData.removeAllElements();
		vData = null;
	}

}




