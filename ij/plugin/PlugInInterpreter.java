package ij.plugin;

/** Plugins that run scripts (e.g., BeanShell, Jython) extend this class. */
public abstract class PlugInInterpreter implements PlugIn {

	/** Run script on separate thread. */
	public void run(String script) {
	}
	
	/** Run script on current thread. */
	public abstract String run(String script, String arg);
	
	/** Returns the value returned by the script, if any, or null. */
	public abstract String getReturnValue();

	/** Returns the name of this PlugInInterpreter. */
	public abstract String getName();

	/** Returns the import statements that are added to the script. */
	public abstract String getImports();
	
	/** Returns the version of ImageJ at the time this plugin was created. */
	public abstract String getVersion();

}
