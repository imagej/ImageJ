package ij.plugin;
import ij.*;
import ij.plugin.frame.Editor;
import javax.script.*;

/** Implements the text editor's Macros/Run command, and the
    IJ.runMacroFile() method, when the file name ends with ".js". */
public class JavaScriptEvaluator implements PlugIn, Runnable  {
	private Thread thread;
	private String script;
	private Object result;
	private String error;
	private boolean evaluating;
	ScriptEngine engine;

	// run script in separate thread
	public void run(String script) {
		if (script.equals(""))
			return;
		this.script = script;
		thread = new Thread(this, "JavaScript"); 
		thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
		thread.start();
	}

	// Run script in current thread
	public String run(String script, String arg) {
		this.script = script;
		run();
		return null;
	}

	// Evaluates 'script' and returns any error messages as a String. 
	public String eval(String script) {
		this.script = script;
		evaluating = true;
		run();
		if (error!=null)
			return error;
		else
			return result!=null?""+result:"";
	}

	public void run() {
		result = null;
		error = null;
		Thread.currentThread().setContextClassLoader(IJ.getClassLoader());
		if (IJ.isJava19())
			System.setProperty("nashorn.args", "--language=es6"); // Use ECMAScript 6 on Java 9
		try {
			if (engine==null) {
				ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
				engine = scriptEngineManager.getEngineByName("ECMAScript");
				if (engine == null) {
					IJ.error("Could not find JavaScript engine");
					return;
				}
				if (!IJ.isJava18()) {
					engine.eval("function load(path) {\n"
						+ "  importClass(Packages.sun.org.mozilla.javascript.internal.Context);\n"
						+ "  importClass(Packages.java.io.FileReader);\n"
						+ "  var cx = Context.getCurrentContext();\n"
						+ "  cx.evaluateReader(this, new FileReader(path), path, 1, null);\n"
						+ "}");
				}
			}
			result = engine.eval(script);
		} catch(Throwable e) {
			String msg = e.getMessage();
			if (msg==null)
				msg = "";
			if (msg.startsWith("sun.org.mozilla.javascript.internal.EcmaError: "))
				msg = msg.substring(47, msg.length());
			if (msg.startsWith("sun.org.mozilla.javascript.internal.EvaluatorException"))
				msg = "Error"+msg.substring(54, msg.length());
			if (msg.length()>0 && !msg.contains("Macro canceled")) {
				if (evaluating)
					error = msg;
				else
					IJ.log(msg);
			}
		}
	}
	
	public String toString() {
		return result!=null?""+result:"";
	}

}
