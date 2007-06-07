package ij.plugin;

/** Plug-ins that acquire images or display windows should
	implement this interface. Plug-ins that process images 
	should implement the PlugInFilter interface. */
public interface PlugIn {

	/** This method is called when the plug-in is loaded.
		'arg', which may be blank, is the argument specified
		for this plug-in in IJ_Props.txt. */ 
	public void run(String arg);
	
}