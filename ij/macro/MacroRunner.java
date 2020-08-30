package ij.macro;
import ij.*;
import ij.text.*;
import ij.util.*;
import ij.gui.ImageCanvas;
import java.io.*;
import java.awt.*;
import ij.plugin.frame.Editor;
																																																																																																																																																					   

/** This class runs macros in a separate thread. */
public class MacroRunner implements Runnable {

	private String macro;
	private Program pgm;
	private int address;
	private String name;
	private Thread thread;
	private String argument;
	private Editor editor;

	/** Create a MacroRunner. */
	public MacroRunner() {
	}

	/** Create a new object that interprets macro source in a separate thread. */
	public MacroRunner(String macro) {
		this(macro, (Editor)null);
	}

	/** Create a new object that interprets macro source in debug mode if 'editor' is not null. */
	public MacroRunner(String macro, Editor editor) {
		this.macro = macro;
		this.editor = editor;
		thread = new Thread(this, "Macro$"); 
		thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
		thread.start();
	}

	/** Interprets macro source in a separate thread using a string argument. */
	public MacroRunner(String macro, String argument) {
		this.macro = macro;
		this.argument = argument;
		thread = new Thread(this, "Macro$"); 
		thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
		thread.start();
	}

	/** Interprets a macro file in a separate thread. */
	public MacroRunner(File file) {
		int size = (int)file.length();
		if (size<=0)
			return;
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
			r.close();
			macro = new String(sb);
		}
		catch (Exception e) {
			IJ.error(e.getMessage());
			return;
		}
		thread = new Thread(this, "Macro$"); 
		thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
		thread.start();
	}

	/** Runs a tokenized macro in a separate thread. */
	public MacroRunner(Program pgm, int address, String name) {
		this(pgm, address, name, (String)null);
	}

	/** Runs a tokenized macro in a separate thread,
		passing a string argument. */
	public MacroRunner(Program pgm, int address, String name, String argument) {
		this.pgm = pgm;
		this.address = address;
		this.name = name;
		this.argument = argument;
		thread = new Thread(this, name+"_Macro$");
		thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
		thread.start();
	}

	/** Runs a tokenized macro in debug mode if 'editor' is not null. */
	public MacroRunner(Program pgm, int address, String name, Editor editor) {
		this.pgm = pgm;
		this.address = address;
		this.name = name;
		this.editor = editor;
		thread = new Thread(this, name+"_Macro$");
		thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
		thread.start();
	}

	/** Runs tokenized macro on current thread if pgm.queueCommands is true. */
	public void runShortcut(Program pgm, int address, String name) {
		this.pgm = pgm;
		this.address = address;
		this.name = name;
		if (pgm.queueCommands)
			run();
		else {
			thread = new Thread(this, name+"_Macro$");
			thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
			thread.start();
		}
	}
	
	/** Runs a tokenized macro on the current thread. */
	public void run(Program pgm, int address, String name) {
		this.pgm = pgm;
		this.address = address;
		this.name = name;
		this.argument = null;
		run();
	}

	public Thread getThread() {
		return thread;
	}

	/** Used to run the macro code in 'macro' on a separate thread. */
	public void run() {
		Interpreter interp = new Interpreter();
		interp.argument = argument;
		if (editor!=null)
			interp.setDebugger(editor);
		try {
			if (pgm==null)
				interp.run(macro);
			else {
				if ("Popup Menu".equals(name)) {
					PopupMenu popup = Menus.getPopupMenu();
					if (popup!=null) {
						ImagePlus imp = null;
						Object parent = popup.getParent();
						if (parent instanceof ImageCanvas)
							imp = ((ImageCanvas)parent).getImage();
						if (imp!=null)
							WindowManager.setTempCurrentImage(Thread.currentThread(), imp);
					}
				}
				interp.runMacro(pgm, address, name);
			}
		} catch(Throwable e) {
			interp.abortMacro();
			IJ.showStatus("");
			IJ.showProgress(1.0);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null)
				imp.unlock();
			String msg = e.getMessage();
			if (e instanceof RuntimeException && msg!=null && e.getMessage().equals(Macro.MACRO_CANCELED)) {
				interp.error(null);
				return;
			}
			IJ.handleException(e);
		} finally {
			if (thread!=null)
				WindowManager.setTempCurrentImage(null);
		}
	}

}

