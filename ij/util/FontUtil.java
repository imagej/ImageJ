package ij.util;
import java.text.*;
import java.awt.*;

/** This class contains static utility methods for replacing fonts that are not available on the
 *  current system.
 */

public class FontUtil {

	/** Returns a font with the given family name or, if not available, a similar font, e.g. Helvetica replaced by Arial */
	public static Font getFont(String fontFamilyName, int style, float size) {
		Font font = new Font(fontFamilyName, style, (int)size);
		if (!font.getFamily().startsWith(fontFamilyName)) {
			String[] similarFonts = getSimilarFontsList(fontFamilyName);
			font = getFont(fontFamilyName, style, (int)size);
		}
		if (size != (int)size)
			font = font.deriveFont(size);
		return font;
	}

	/** Returns the font for first element of the 'fontNames' array, where a Font Family Name starts with this name.
	 *	E.g. if fontNames = {"Times New Roman", "Serif" and the system has no "Times New Roman", but finds "Serif",
	 *	it would return a "Serif" font with suitable style and size */
	private static Font getFont(String[] fontNames, int style, int size) {
		int iSize = (int)size;
		Font font = null;
		for (String fontName : fontNames) {
			font = new Font(fontName, style, iSize);
			if (font.getFamily().startsWith(fontName))
				break;
		}
		return font;
	}

	/** For a few basic font types, gets a list of replacement font families
	 *	Note that java's 'SansSerif' has wider characters (significantly different metrics) than the other
	 *	sans-serif fonts in the list; thus it should be considered a fallback option only.
	 *	Also note that some fonts (Times, Helvetica, Courier, Monospace, Serif) tend to truncate some
	 *	diacritical marks when using FontMetrics.getHeight (as ImageJ does), e.g. the ring of the
	 *  Angstrom symbol Å may be clipped (at least on Java 1.6/Mac)
	 */
	public static String[] getSimilarFontsList(String fontFamily) {
		if (fontFamily.indexOf("Times")>=0 || fontFamily.indexOf("Serif")>=0)
			return new String[]{"Times New Roman", "Times", "Liberation Serif", "Serif"};
		else if (fontFamily.indexOf("Arial")>=0 || fontFamily.indexOf("Helvetica")>=0 || fontFamily.indexOf("Sans")>=0)
			return new String[]{"Arial", "Helvetica", "Helvetica Neue", "Liberation Sans", "SansSerif"};
		else if (fontFamily.indexOf("Courier")>=0 || fontFamily.indexOf("Mono")>=0)
			return new String[]{"Courier New", "Courier", "Liberation Mono", "Monospaced"};
		else
			return new String[]{fontFamily};
	}


}
