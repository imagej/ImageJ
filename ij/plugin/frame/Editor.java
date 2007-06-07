package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import ij.*;
import ij.gui.*;

/** This is a simple TextArea based editor for editing and compiling plugins. */
public class Editor extends PlugInFrame implements ActionListener, TextListener {

	public static final int MAX_SIZE = 28000;
	private TextArea ta;
	private String path;
	private boolean changes;
	private static String searchString = "";
	private static int lineNumber = 1;

	public Editor() {
		super("Editor");

		MenuBar mb = new MenuBar();
		Menu m = new Menu("File");
		m.add(new MenuItem("Save", new MenuShortcut(KeyEvent.VK_S)));
		m.add(new MenuItem("Save As..."));
		m.add(new MenuItem("Compile and Run", new MenuShortcut(KeyEvent.VK_R)));
		m.addActionListener(this);
		mb.add(m);
		
		m = new Menu("Edit");
		m.add(new MenuItem("Find...", new MenuShortcut(KeyEvent.VK_F)));
		m.add(new MenuItem("Find Next", new MenuShortcut(KeyEvent.VK_G)));
		m.add(new MenuItem("Go to Line...", new MenuShortcut(KeyEvent.VK_L)));
		m.addActionListener(this);
		mb.add(m);
		setMenuBar(mb);

		ta = new TextArea();
		ta.addTextListener(this);
		add(ta);
		pack();
	}
			
	public void create(String name, String text) {
		ta.append(text);
		ta.setCaretPosition(0);
		setTitle(name);
		changes = true;
		setVisible(true);
	}

	public void open(String dir, String name) {
		path = dir+name;
		File file = new File(path);
		int size = (int)file.length();
		if (size>MAX_SIZE && !IJ.isMacintosh()) {
			IJ.error("This file is too large for ImageJ to open.\n"
				+" \n"
				+"    File size: "+size+" bytes\n"
				+"    Max. size: "+MAX_SIZE+" bytes");
			dispose();
			return;
		}

		try {
			StringBuffer sb = new StringBuffer(5000);
			BufferedReader r = new BufferedReader(new FileReader(file));
			while (true) {
				String s=r.readLine();
				if (s==null)
					break;
				else
					sb.append(s+"\n");
			}
			create(name, new String(sb));
			changes = false;
		}
		catch (Exception e) {
			IJ.error(e.getMessage());
			return;
		}
	}

	public void display(String title, String text) {
		ta.selectAll();
		ta.replaceRange(text,ta.getSelectionStart(),ta.getSelectionEnd());
		ta.setCaretPosition(0);
		setTitle(title);
		changes = false;
		setVisible(true);
	}

	void save() {
		if (path==null)
			return;
		String text = ta.getText();
		char[] chars = new char[text.length()];
		text.getChars(0, text.length(), chars, 0);
		try {
			BufferedReader br = new BufferedReader(new CharArrayReader(chars));
			BufferedWriter bw = new BufferedWriter(new FileWriter(path));
			while (true) {
				String s = br.readLine();
				if (s==null) break;
				bw.write(s, 0, s.length());
				bw.newLine();
			}
			bw.close();
			changes = false;
		} catch
			(IOException e) {}
	}

	void compileAndRun() {
		if (path==null)
			saveAs();
		if (path!=null) {
			save();
			IJ.runPlugIn("ij.plugin.Compiler", path);
		}
	}


	public void actionPerformed(ActionEvent evt) {
		String what = evt.getActionCommand();
		if ("Save".equals(what))
			save();
		else if ("Compile and Run".equals(what))
			compileAndRun();
		else if ("Save As...".equals(what))
			saveAs();
		else if ("Find...".equals(what))
			find(null);
		else if ("Find Next".equals(what))
			find(searchString);
		else if ("Go to Line...".equals(what))
			gotoLine();
	}

	public void textValueChanged(TextEvent evt) {
		changes = true;
	}

    /** Override windowActivated in PlugInFrame to
    	prevent Mac meno bar from being installed. */
    public void windowActivated(WindowEvent e) {
	}

    public void windowClosing(WindowEvent e) {
		if (getTitle().equals("Errors") || close()) {	
			setVisible(false);
			dispose();
		}
	}

	boolean close() {
		boolean okay = true;
		if (changes) {
			SaveChangesDialog d = new SaveChangesDialog(this, getTitle());
			if (d.cancelPressed())
				okay = false;
			else if (d.savePressed())
				save();
		}
		return okay;
	}

	void saveAs() {
		FileDialog fd = new FileDialog(this, "Save Plugin As...", FileDialog.SAVE);
		String name1 = getTitle();
		fd.setFile(name1);
		fd.setDirectory(Menus.getPlugInsPath());
		fd.setVisible(true);
		String name2 = fd.getFile();
		String dir = fd.getDirectory();
		fd.dispose();
		if (name2!=null) {
			updateClassName(name1, name2);
			path = dir+name2;
			save();
			changes = false;
			setTitle(name2);
		}
	}
	
	void updateClassName(String oldName, String newName) {
		if (newName.indexOf("_")<0)
			IJ.showMessage("Plugin Editor", "Plugins without an underscore in their name will not\n"
				+"be automatically installed when ImageJ is restarted.");
		if (oldName.equals(newName) || !oldName.endsWith(".java") || !newName.endsWith(".java"))
			return;
		oldName = oldName.substring(0,oldName.length()-5);
		newName = newName.substring(0,newName.length()-5);
		String text1 = ta.getText();
		int index = text1.indexOf("public class "+oldName);
		if (index<0)
			return;
		String text2 = text1.substring(0,index+13)+newName+text1.substring(index+13+oldName.length(),text1.length());
		ta.setText(text2);
	}
	
	void find(String s) {
		if (s==null) {
			GenericDialog gd = new GenericDialog("Find", this);
			gd.addStringField("Find: ", searchString, 20);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			s = gd.getNextString();
		}
		if (s.equals(""))
			return;
		String text = ta.getText();
		int index = text.indexOf(s, ta.getCaretPosition()+1);
		if (index<0)
			{IJ.beep(); return;}
		ta.setSelectionStart(index);
		ta.setSelectionEnd(index+s.length());
		searchString = s;
	}
	
	void gotoLine() {
		GenericDialog gd = new GenericDialog("Go to Line", this);
		gd.addNumericField("Go to line number: ", lineNumber, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int n = (int)gd.getNextNumber();
		if (n<1) return;
		String text = ta.getText();
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		int count=1, loc=0;
		for (int i=0; i<chars.length; i++) {
			if (chars[i]=='\n') count++;
			if (count==n)
				{loc=i+1; break;}
		}
		ta.setCaretPosition(loc);
		lineNumber = n;
	}
	
}



