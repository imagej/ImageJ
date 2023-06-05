package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.io.*;
import ij.plugin.filter.*;
import ij.util.Tools;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/** This plugin implements most of the Edit/Options/Colors command. */
public class Colors implements PlugIn, ItemListener {
	public static final String[] colors = {"red","green","blue","magenta","cyan","yellow","orange","black","white","gray","lightgray","darkgray","pink"};
	private static final String[] colors2 = {"Red","Green","Blue","Magenta","Cyan","Yellow","Orange","Black","White","Gray","lightGray","darkGray","Pink"};
	private Choice fchoice, bchoice, schoice;
	private Color fc2, bc2, sc2;
	private static final double gamma = 0.8;


 	public void run(String arg) {
		showDialog();
	}

	/** The Edit>Options>Colors dialog */
	void showDialog() {
		Color fc =Toolbar.getForegroundColor();
		String fname = getColorName(fc, "black");
		Color bc =Toolbar.getBackgroundColor();
		String bname = getColorName(bc, "white");
		Color sc =Roi.getColor();
		String sname = getColorName(sc, "yellow");
		GenericDialog gd = new GenericDialog("Colors");
		gd.addChoice("Foreground:", colors, fname);
		gd.addChoice("Background:", colors, bname);
		gd.addChoice("Selection:", colors, sname);
		Vector choices = gd.getChoices();
		if (choices!=null) {
			fchoice = (Choice)choices.elementAt(0);
			bchoice = (Choice)choices.elementAt(1);
			schoice = (Choice)choices.elementAt(2);
			fchoice.addItemListener(this);
			bchoice.addItemListener(this);
			schoice.addItemListener(this);
		}

		gd.showDialog();
		if (gd.wasCanceled()) {
			if (fc2!=fc) Toolbar.setForegroundColor(fc);
			if (bc2!=bc) Toolbar.setBackgroundColor(bc);
			if (sc2!=sc) {
				Roi.setColor(sc);
				ImagePlus imp = WindowManager.getCurrentImage();
				if (imp!=null && imp.getRoi()!=null) imp.draw();
			}
			return;
		}
		fname = gd.getNextChoice();
		bname = gd.getNextChoice();
		sname = gd.getNextChoice();
		fc2 = getColor(fname, Color.black);
		bc2 = getColor(bname, Color.white);
		sc2 = getColor(sname, Color.yellow);
		if (fc2!=fc) Toolbar.setForegroundColor(fc2);
		if (bc2!=bc) Toolbar.setBackgroundColor(bc2);
		if (sc2!=sc) {
			Roi.setColor(sc2);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) imp.draw();
			Toolbar tb = Toolbar.getInstance();
			if (tb!=null) tb.repaint();
		}
	}

	/** For named colors, returns the name, or 'defaultName' if not a named color.
	 *  If 'defaultName' is non-null and starts with an uppercase character,
	 *  the returned name is capitalized (first character uppercase).
	 *  Use colorToString or colorToString2 to get a String representation (hexadecimal)
	 *  also for unnamed colors.*/
	public static String getColorName(Color c, String defaultName) {
		if (c==null) return defaultName;
		boolean useCapitalizedName = defaultName!=null && defaultName.length()>0 && Character.isUpperCase(defaultName.charAt(0));
		return getColorName(c, defaultName, useCapitalizedName);
	}

	/** For named colors, returns the name, or 'defaultName' if not a named color.
	 *  'color' must not be null. */
	private static String getColorName(Color c, String defaultName, boolean useCapitalizedName) {
		String[] colorNames = useCapitalizedName ? colors2 : colors;
		if (c.equals(Color.red))            return colorNames[0];
		else if (c.equals(Color.green))     return colorNames[1];
		else if (c.equals(Color.blue))      return colorNames[2];
		else if (c.equals(Color.magenta))   return colorNames[3];
		else if (c.equals(Color.cyan))      return colorNames[4];
		else if (c.equals(Color.yellow))    return colorNames[5];
		else if (c.equals(Color.orange))    return colorNames[6];
		else if (c.equals(Color.black))     return colorNames[7];
		else if (c.equals(Color.white))     return colorNames[8];
		else if (c.equals(Color.gray))      return colorNames[9];
		else if (c.equals(Color.lightGray)) return colorNames[10];
		else if (c.equals(Color.darkGray))  return colorNames[11];
		else if (c.equals(Color.pink))      return colorNames[12];
		return defaultName;
	}

	/** For named colors, converts the name String to the corresponding color.
	 *  Returns 'defaultColor' if the color has no name.
	 *  Use 'decode' to also decode hex color names like "#ffff00" */
	public static Color getColor(String name, Color defaultColor) {
		if (name==null || name.length()<2)
			return defaultColor;
		name = name.toLowerCase(Locale.US);
		Color c = defaultColor;
		if (name.contains(colors[7])) c = Color.black;
		else if (name.contains(colors[8]))  c = Color.white;
		else if (name.contains(colors[0]))  c = Color.red;
		else if (name.contains(colors[2]))  c = Color.blue;
		else if (name.contains(colors[5]))  c = Color.yellow;
		else if (name.contains(colors[1]))  c = Color.green;
		else if (name.contains(colors[3]))  c = Color.magenta;
		else if (name.contains(colors[4]))  c = Color.cyan;
		else if (name.contains(colors[6]))  c = Color.orange;
		else if (name.contains(colors[12])) c = Color.pink;
		else if (name.contains(colors[9]) || name.contains("grey")) { //gray or grey
			if (name.contains("light"))     c = Color.lightGray;
			else if (name.contains("dark")) c = Color.darkGray;
			else                            c = Color.gray;
		}
		return c;
	}

	/** Converts a String with the color name or the hexadecimal representation
	 *  of a color with 6 or 8 hex digits to a Color.
	 *  With 8 hex digits, the first two digits are the alpha.
	 *  With 6 hex digits, the color is opaque (alpha = hex ff).
	 *  A hex String may be preceded by '#' such as "#80ff00".
	 *  When the string does not include a valid color name or hex code,
	 *  returns Color.GRAY. */
	public static Color decode(String hexColor) {
		return decode(hexColor, Color.gray);
	}

	/** Converts a String with the color name or the hexadecimal representation
	 *  of a color with 6 or 8 hex digits to a Color.
	 *  With 8 hex digits, the first two digits are the alpha.
	 *  With 6 hex digits, the color is opaque (alpha = hex ff).
	 *  A hex String may be preceded by "#" such as "#80ff00" or "0x".
	 *  When the string does not include a valid color name or hex code,
	 *  returns 'defaultColor'. */
	public static Color decode(String hexColor, Color defaultColor) {
		if (hexColor==null || hexColor.length()<2)
			return defaultColor;
		Color color = getColor(hexColor, null);  //for named colors
		if (color==null) {
			if (hexColor.startsWith("#"))
				hexColor = hexColor.substring(1);
			else if (hexColor.startsWith("0x"))
				hexColor = hexColor.substring(2);
			int len = hexColor.length();
			if (!(len==6 || len==8))
				return defaultColor;
			boolean hasAlpha = len==8;
			try {
				int rgba = (int)Long.parseLong(hexColor, 16);
				color = new Color(rgba, hasAlpha);
			} catch (NumberFormatException e) {
				return defaultColor;
			}
		}
		return color;
	}

	public static int getRed(String hexColor) {
		return decode(hexColor, Color.black).getRed();
	}

	public static int getGreen(String hexColor) {
		return decode(hexColor, Color.black).getGreen();
	}

	public static int getBlue(String hexColor) {
		return decode(hexColor, Color.black).getBlue();
	}

	/** Converts a hex color (e.g., "ffff00") into "red", "green", "yellow", etc.
	 *  Returns null if the hex color does not have a name.
	 *  Unused in ImageJ, for compatibility only. */
	public static String hexToColor(String hex) {
		if (hex==null) return null;
		Color color = decode(hex, null);
		if (color==null) return null;
		return getColorName(color, null, false);
	}

	/** Converts a hex color (e.g., "ffff00" or "#ffff00") into a color name
	 *  "Red", "Green", "Yellow", etc.
	 *  Returns null if the hex color does not have a name.
	 *  Unused in ImageJ, for compatibility only. */
	public static String hexToColor2(String hex) {
		if (hex==null) return null;
		Color color = decode(hex, null);
		if (color==null) return null;
		return getColorName(color, null, true);
	}

	/** Converts a Color into a lowercase string ("red", "green", "#aa55ff", etc.).
	 *  If <code>color</code> is <code>null</code>, returns the String "none". */
	public static String colorToString(Color color) {
		if (color == null) return "none";
		String str = getColorName(color, null, false);
		if (str == null)
			str = "#"+getHexString(color);
		return str;
	}

	/** Converts a Color into a string ("Red", "Green", #aa55ff, etc.).
	 *  If <code>color</code> is <code>null</code>, returns the String "None". */
	public static String colorToString2(Color color) {
		if (color == null) return "None";
		String str = getColorName(color, null, true);
		if (str == null)
			str = "#"+getHexString(color);
		return str;
	}

	/** Returns the 6-digit hex string such as "aa55ff" for opaque colors or
	 *  or 8-digit like "80aa55ff" for other colors (the first two hex digits are alpha).
	 *  'color' must not be null. */
	private static String getHexString(Color color) {
		int rgb = color.getRGB();
		boolean isOpaque = (rgb & 0xff000000) == 0xff000000;
		if (isOpaque)
			rgb &= 0x00ffffff;  //don't show alpha for opaque colors
		String format = isOpaque? "%06x" : "%08x";
		return String.format(format, rgb);
	}

	/** Returns an opaque color with the specified red, green, and blue values.
	 *  Values is outside the 0-255 range are replaced by the nearest
	 *  valid number (0 or 255) */
	public static Color toColor(int red, int green, int blue) {
	    if (red<0) red=0; if (green<0) green=0; if (blue<0) blue=0; 
	    if (red>255) red=255; if (green>255) green=255; if (blue>255) blue=255;  
		return  new Color(red, green, blue);
	}

	/** Callback listener for Choice modifications in the dialog */
	public void itemStateChanged(ItemEvent e) {
		Choice choice = (Choice)e.getSource();
		String item = choice.getSelectedItem();
		Color color = getColor(item, Color.black);
		if (choice==fchoice)
			Toolbar.setForegroundColor(color);
		else if (choice==bchoice)
			Toolbar.setBackgroundColor(color);
		else if (choice==schoice) {
			Roi.setColor(color);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null && imp.getRoi()!=null) imp.draw();
			Toolbar.getInstance().repaint();
		}
	}

	/** Returns an array of the color Strings in the argument(s) and the 13
	 *  predefined color names "Red", "Green", ... "Pink".
	 *  The Strings arguments must be either "None" or hex codes starting with "#".
	 *  Any null arguments are ignored. */
	public static String[] getColors(String... moreColors) {
		ArrayList names = new ArrayList();
		for (String arg: moreColors) {
			if (arg!=null && arg.length()>0 && (!Character.isLetter(arg.charAt(0))||arg.equals("None")))
				names.add(arg);
		}
		for (String arg: colors2)
			names.add(arg);
		return (String[])names.toArray(new String[names.size()]);
	}
	
	/** Converts a wavelength (380-750 nm) to color.
	 * Based on the wavelength_to_rgb.R program at
	 * https://gist.github.com/friendly/67a7df339aa999e2bcfcfec88311abfc,
	 * which in turn is based on a FORTRAN program at
	 * http://www.physics.sfasu.edu/astro/color.html.
	*/
	public static Color wavelengthToColor(double wl) {
		double R, G, B;
		if (wl >= 380 & wl <= 440) {
			double attenuation = 0.3 + 0.7 * (wl - 380) / (440 - 380);
			R = Math.pow((-(wl - 440) / (440 - 380) * attenuation), gamma);
			G = 0.0;
			B = Math.pow((1.0 * attenuation), gamma);
		} else if (wl >= 440 & wl <= 490) {
			R = 0.0;
			G = Math.pow(((wl - 440) / (490 - 440)), gamma);
			B = 1.0;
		} else if (wl >= 490 & wl <= 510) {
			R = 0.0;
			G = 1.0;
			B = Math.pow((-(wl - 510) / (510 - 490)), gamma);
		} else if (wl >= 510 & wl <= 580) {
			R = Math.pow(((wl - 510) / (580 - 510)), gamma);
			G = 1.0;
			B = 0.0;
		} else if (wl >= 580 & wl <= 645) {
			R = 1.0;
			G = Math.pow((-(wl - 645) / (645 - 580)), gamma);
			B = 0.0;
		} else if (wl >= 645 & wl <= 750) {
			double attenuation = 0.3 + 0.7 * (750 - wl) / (750 - 645);
			R = Math.pow((1.0 * attenuation), gamma);
			G = 0.0;
			B = 0.0;
		} else {
			R = 0.0;
			G = 0.0;
			B = 0.0;
		}
		R = Math.floor(R*255);
		G = Math.floor(G*255);
		B = Math.floor(B*255);
		return new Color((int)R, (int)G, (int)B);
	}

}
