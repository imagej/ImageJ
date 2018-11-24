package ij.gui;
import ij.*;
import ij.plugin.URLOpener;
import ij.macro.MacroRunner;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkEvent.EventType;
import java.net.URL;

/** This is modal or non-modal dialog box that displays HTML formated text. */
public class HTMLDialog extends JDialog implements ActionListener, KeyListener, HyperlinkListener {
	private boolean escapePressed;
	private JEditorPane editorPane;
	private boolean modal = true;

	public HTMLDialog(String title, String message) {
		super(ij.IJ.getInstance(), title, true);
		init(message);
	}

	public HTMLDialog(Dialog parent, String title, String message) {
		super(parent, title, true);
		init(message);
	}

	public HTMLDialog(String title, String message, boolean modal) {
		super(ij.IJ.getInstance(), title, modal);
		this.modal = modal;
		init(message);
	}
	
	private void init(String message) {
		ij.util.Java2.setSystemLookAndFeel();
		Container container = getContentPane();
		container.setLayout(new BorderLayout());
		if (message==null) message = "";
		editorPane = new JEditorPane("text/html","");
		editorPane.setEditable(false);
		HTMLEditorKit kit = new HTMLEditorKit();
		editorPane.setEditorKit(kit);
		StyleSheet styleSheet = kit.getStyleSheet();
		styleSheet.addRule("body{font-family:Verdana,sans-serif; font-size:11.5pt; margin:5px 10px 5px 10px;}"); //top right bottom left
		styleSheet.addRule("h1{font-size:18pt;}");
		styleSheet.addRule("h2{font-size:15pt;}");
		styleSheet.addRule("dl dt{font-face:bold;}");
		editorPane.setText(message);    //display the html text with the above style
		editorPane.getActionMap().put("insert-break", new AbstractAction(){
				public void actionPerformed(ActionEvent e) {}		
		}); //suppress beep on <ENTER> key
		JScrollPane scrollPane = new JScrollPane(editorPane);
		container.add(scrollPane);
		JButton button = new JButton("OK");
		button.addActionListener(this);
		button.addKeyListener(this);
		editorPane.addKeyListener(this);
		editorPane.addHyperlinkListener(this);
		JPanel panel = new JPanel();
		panel.add(button);
		container.add(panel, "South");
		setForeground(Color.black);
		pack();
		Dimension screenD = IJ.getScreenSize();
		Dimension dialogD = getSize();
		int maxWidth = (int)(Math.min(0.70*screenD.width, 800)); //max 70% of screen width, but not more than 800 pxl
		if (maxWidth>400 && dialogD.width>maxWidth)
			dialogD.width = maxWidth;
		if (dialogD.height > 0.80*screenD.height && screenD.height>400)  //max 80% of screen height
			dialogD.height = (int)(0.80*screenD.height);
		setSize(dialogD);
		GUI.center(this);
		if (!modal) WindowManager.addWindow(this);
		show();
	}

	public void actionPerformed(ActionEvent e) {
		dispose();
	}
	
	public void keyPressed(KeyEvent e) {
		int keyCode = e.getKeyCode(); 
		ij.IJ.setKeyDown(keyCode);
		escapePressed = keyCode==KeyEvent.VK_ESCAPE;
		if (keyCode==KeyEvent.VK_C) {
			if (editorPane.getSelectedText()==null || editorPane.getSelectedText().length()==0)
				editorPane.selectAll();
			editorPane.copy();
			editorPane.select(0,0);
		} else if (keyCode==KeyEvent.VK_ENTER || keyCode==KeyEvent.VK_W || escapePressed)
			dispose();
	}
	
	public void keyReleased(KeyEvent e) {
		int keyCode = e.getKeyCode();
		ij.IJ.setKeyUp(keyCode);
	}
	
	public void keyTyped(KeyEvent e) {}
	
	public boolean escapePressed() {
		return escapePressed;
	}

	public void hyperlinkUpdate(HyperlinkEvent e) {
		if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
			String url = e.getDescription(); //getURL does not work for relative links within document such as "#top"
			if (url==null) return;
			if (url.startsWith("#"))
				editorPane.scrollToReference(url.substring(1));
			else {
				String macro = "run('URL...', 'url="+url+"');";
				new MacroRunner(macro);
			}
		}
	}

	public void dispose() {
		super.dispose();
		if (!modal) WindowManager.removeWindow(this);
	}

}
