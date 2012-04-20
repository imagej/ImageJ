package ij.plugin.tool;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.macro.Program;
import ij.gui.Toolbar;
import java.awt.event.*;

public abstract class PlugInTool implements PlugIn {

	public void run(String arg) {
		Toolbar.addPlugInTool(this);
	}
	
	public void mousePressed(ImagePlus imp, MouseEvent e) {e.consume();}

	public void mouseReleased(ImagePlus imp, MouseEvent e) {e.consume();}

	public void mouseClicked(ImagePlus imp, MouseEvent e) {e.consume();}

	public void mouseDragged(ImagePlus imp, MouseEvent e) {e.consume();}
	
	public void mouseMoved(ImagePlus imp, MouseEvent e) { }
	
	public void mouseEntered(ImagePlus imp, MouseEvent e) {e.consume();}

	public void mouseExited(ImagePlus imp, MouseEvent e) {e.consume();}

	/** Return the tool name. */
	public String getToolName() {
		return getClass().getName().replace('_', ' ');
	}
	
	/** Return the string encoding of the tool icon. See
		http://rsb.info.nih.gov/ij/developer/macro/macros.html#icons
		The default icon is the first letter of the tool name.
	*/
	public String getToolIcon() {
		String letter = getToolName();
		if (letter!=null && letter.length()>0)
			letter = letter.substring(0,1);
		else
			letter = "P";
		return "C037T5f16"+letter;
	}
	
	public void showOptionsDialog() {
	}

	/** These methods are overridden by MacroToolRunner. */
	public void runMacroTool(String name) { }
	public void runMenuTool(String name, String command) { }
	public Program getMacroProgram() {return null;}

}
