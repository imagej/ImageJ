package ij.macro;
import ij.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Hashtable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

/** This class implements the text editor's Macros/Find Functions command.
It was written by jerome.mutterer at ibmp.fr, and is based on Mark Longair's CommandFinder plugin.
*/
public class FunctionFinder implements TextListener,  WindowListener, KeyListener, ItemListener, ActionListener {
	private Dialog dialog;
	private TextField prompt;
	private List functions;
	private Button insertButton, infoButton, closeButton;
	private String [] commands;
	private Editor editor;
	
	public FunctionFinder(Editor editor) {
		this.editor = editor;
		String exists = IJ.runMacro("return File.exists(getDirectory('macros')+'functions.html');");
		if (exists=="0")	{
			String installLocalMacroFunctionsFile = "functions = File.openUrlAsString('"+IJ.URL+"/developer/macro/functions.html');\n"+
					"f = File.open(getDirectory('macros')+'functions.html');\n"+
					"print (f, functions);\n"+
					"File.close(f);";
			try { IJ.runMacro(installLocalMacroFunctionsFile);
			} catch (Throwable e) { IJ.error("Problem downloading functions.html"); return;}
		}
		String f = IJ.runMacro("return File.openAsString(getDirectory('macros')+'functions.html');");
		String [] l = f.split("\n");
		commands= new String [l.length];
		int c=0;
		for (int i=0; i<l.length; i++) {
			String line = l[i];
			if (line.startsWith("<b>")) {
				commands[c]=line.substring(line.indexOf("<b>")+3,line.indexOf("</b>"));
				c++;
			}
		}
		if (c==0) {
			IJ.error("ImageJ/macros/functions.html is corrupted");
			return;
		}
		
		ImageJ imageJ = IJ.getInstance();
		dialog = new Dialog(imageJ, "Built-in Functions");
		dialog.setLayout(new BorderLayout());
		dialog.addWindowListener(this);
		Panel northPanel = new Panel();
		prompt = new TextField("", 30);
		prompt.addTextListener(this);
		prompt.addKeyListener(this);
		northPanel.add(prompt);
		dialog.add(northPanel, BorderLayout.NORTH);
		functions = new List(12);
		functions.addKeyListener(this);
		populateList("");
		dialog.add(functions, BorderLayout.CENTER);
		Panel buttonPanel = new Panel();
		//panel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		insertButton = new Button("Insert");
		insertButton.addActionListener(this);
		buttonPanel.add(insertButton);
		infoButton = new Button("Info");
		infoButton.addActionListener(this);
		buttonPanel.add(infoButton);
		closeButton = new Button("Close");
		closeButton.addActionListener(this);
		buttonPanel.add(closeButton);
		dialog.add(buttonPanel, BorderLayout.SOUTH);
		dialog.pack();
		
		Frame frame = WindowManager.getFrontWindow();
		if (frame==null) return;
		java.awt.Point posi=frame.getLocationOnScreen();
		int initialX = (int)posi.getX() + 38;
		int initialY = (int)posi.getY() + 84;
		dialog.setLocation(initialX,initialY);
		dialog.setVisible(true);
		dialog.toFront();
	}

	public FunctionFinder() {
		this(null);
	}

	public void populateList(String matchingSubstring) {
		String substring = matchingSubstring.toLowerCase();
		functions.removeAll();
		try {
			for(int i=0; i<commands.length; ++i) {
				String commandName = commands[i];
				if (commandName.length()==0)
					continue;
				String lowerCommandName = commandName.toLowerCase();
				if( lowerCommandName.indexOf(substring) >= 0 ) {
					functions.add(commands[i]);
				}
			}
		} catch (Exception e){}
	}
	
	public void edPaste(String arg) {
		Frame frame = editor;
		if (frame!=null && !frame.isVisible())
			frame = null;
		if (frame==null) {
			frame = WindowManager.getFrontWindow();
			if (!(frame instanceof Editor))
				return;
		}
		try {
			TextArea ta = ((Editor)frame).getTextArea();
			int start = ta.getSelectionStart( );
			int end = ta.getSelectionEnd( );
			try {
				ta.replaceRange(arg.substring(0,arg.length()), start, end);
			} catch (Exception e) { }
			if (IJ.isMacOSX())
				ta.setCaretPosition(start+arg.length());
		} catch (Exception e) { }
	}
	
	public void itemStateChanged(ItemEvent ie) {
		populateList(prompt.getText());
	}
	
	protected void runFromLabel(String listLabel) {
		edPaste(listLabel);
		dialog.dispose();
	}
	
	public void keyPressed(KeyEvent ke) {
		int key = ke.getKeyCode();
		int items = functions.getItemCount();
		Object source = ke.getSource();
		if (source==prompt) {
			if (key==KeyEvent.VK_ENTER) {
				if (1==items) {
					String selected = functions.getItem(0);
					edPaste(selected);
				}
			} else if (key==KeyEvent.VK_UP) {
				functions.requestFocus();
				if(items>0)
					functions.select(functions.getItemCount()-1);
			} else if (key==KeyEvent.VK_ESCAPE) {
				dialog.dispose();
			} else if (key==KeyEvent.VK_DOWN)  {
				functions.requestFocus();
				if (items>0)
					functions.select(0);
			}
		} else if (source==functions) {
			if (key==KeyEvent.VK_ENTER) {
				String selected = functions.getSelectedItem();
				if (selected!=null)
					edPaste(selected);
			}
		}
	}
	
	public void keyReleased(KeyEvent ke) { }
	
	public void keyTyped(KeyEvent ke) { }
	
	public void textValueChanged(TextEvent te) {
		populateList(prompt.getText());
	}
		
	public void actionPerformed(ActionEvent e) {
		Object b = e.getSource();
		if (b==insertButton) {
			int index = functions.getSelectedIndex();
			if (index>=0) {
				String selected = functions.getItem(index);
				edPaste(selected);
			}
		} else if (b==infoButton) {
			String url = IJ.URL+"/developer/macro/functions.html";
			int index = functions.getSelectedIndex();
			if (index>=0) {
				String selected = functions.getItem(index);
				int index2 = selected.indexOf("(");
				if (index2==-1)
					index2 = selected.length();
				url = url + "#" + selected.substring(0, index2);
			}
			IJ.runPlugIn("ij.plugin.BrowserLauncher", url);
		} else if (b==closeButton)
			dialog.dispose();
	}

	public void windowClosing(WindowEvent e) {
		dialog.dispose();
	}
	
	public void windowActivated(WindowEvent e) { }
	public void windowDeactivated(WindowEvent e) { }
	public void windowClosed(WindowEvent e) { }
	public void windowOpened(WindowEvent e) { }
	public void windowIconified(WindowEvent e) { }
	public void windowDeiconified(WindowEvent e) { }
}

