import ij.IJ;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

/**
  This plugin demonstrates how a plugin can store preference settings
  using ImageJs built-in preference mechanism (i.e., the IJ_Prefs.txt file).
  This works with ImageJ 1.32c and higher.

  There are three methods  for saving preferences values (strings, 
  numbers and booleans) and three for retrieving values

    Prefs.setPref(key, value);
        Saves the value of the string, number or boolean in the preferences 
        file using the keyword 'key'.

    value = Prefs.getPref(key, defaultValue);
        Uses the keyword 'key' to retrieve a string, number or boolean value 
        from the preferences file. Returns 'defaultValue' if the key is not found.

    The key should contain a unique prefix (e.g. "prefsdemo.").

    @author Ulf Dittmer (udittmer (at) yahoo.com)

 */

public class Preference_Demo implements PlugIn {

	String text = Prefs.get("prefsdemo.string", "Some Text");
	int integer = (int)Prefs.get("prefsdemo.int", 10);
	double real = Prefs.get("prefsdemo.real", 0.123);
	boolean check = Prefs.get("prefsdemo.boolean", true);

	public void run (String arg) {
		if (IJ.versionLessThan("1.32c"))
			return;

		GenericDialog gd = new GenericDialog("Preference Demo");
		gd.addStringField("Text: ", text, 12);
		gd.addNumericField("Integer: ", integer, 0);
		gd.addNumericField("Float: ", real, 4);
		gd.addCheckbox("Check", check);
		gd.showDialog();
		if (gd.wasCanceled()) return;

		text = gd.getNextString();
		integer = (int)gd.getNextNumber();
		real = gd.getNextNumber();
		check = gd.getNextBoolean();
		if (gd.invalidNumber()) {
			IJ.showMessage("Preference Demo", gd.getErrorMessage());
			return;
		}

		Prefs.set("prefsdemo.string", text);
		Prefs.set("prefsdemo.int", integer);
		Prefs.set("prefsdemo.real", real);
		Prefs.set("prefsdemo.boolean", check);
	}
}
