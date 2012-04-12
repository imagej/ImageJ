package ij.plugin.tool;
import ij.macro.Program;
import ij.plugin.MacroInstaller;

public class MacroToolRunner extends PlugInTool {
	MacroInstaller installer;

	public MacroToolRunner(MacroInstaller installer) {
		this.installer = installer;
	}
	
	public void runMacroTool(String name) {
		if (installer!=null)
			installer.runMacroTool(name);
	}

	public void runMenuTool(String name, String command) {
		if (installer!=null)
			installer.runMenuTool(name, command);
	}

	public Program getMacroProgram() {
		if (installer!=null)
			return installer.getProgram();
		else
			return null;
	}
	
	public int getMacroCount() {
		if (installer!=null)
			return installer.getMacroCount();
		else
			return 0;
	}

}


