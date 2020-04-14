package ij.util;
import ij.process.*;
import java.awt.Color;
import java.util.*;
import java.io.*;
import java.util.Comparator;
import java.nio.channels.FileChannel;

/** This class contains static utility methods. */
 public class Tools {
	/** This array contains the 16 hex digits '0'-'F'. */
	public static final char[] hexDigits = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
	
	/** Converts a Color to an 7 byte hex string starting with '#'. */
	public static String c2hex(Color c) {
		int i = c.getRGB();
		char[] buf7 = new char[7];
		buf7[0] = '#';
		for (int pos=6; pos>=1; pos--) {
			buf7[pos] = hexDigits[i&0xf];
			i >>>= 4;
		}
		return new String(buf7);
	}
		
	/** Converts a float to an 9 byte hex string starting with '#'. */
	public static String f2hex(float f) {
		int i = Float.floatToIntBits(f);
		char[] buf9 = new char[9];
		buf9[0] = '#';
		for (int pos=8; pos>=1; pos--) {
			buf9[pos] = hexDigits[i&0xf];
			i >>>= 4;
		}
		return new String(buf9);
	}
		
	/** Converts an int to a zero-padded hex string of fixed length 'digits'. 
	 *  If the number is too high, it gets truncated, keeping only the lowest 'digits' characters. */
	public static String int2hex(int i, int digits) {
		char[] buf = new char[digits];
		for (int pos=buf.length-1; pos>=0; pos--) {
			buf[pos] = hexDigits[i&0xf];
			i >>>= 4;
		}
		return new String(buf);
	}

	public static ImageStatistics getStatistics(double[] a) {
		ImageProcessor ip = new FloatProcessor(a.length, 1, a);
		return ip.getStats();
	}

	public static double[] getMinMax(double[] a) {
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		double value;
		for (int i=0; i<a.length; i++) {
			value = a[i];
			if (value<min)
				min = value;
			if (value>max)
				max = value;
		}
		double[] minAndMax = new double[2];
		minAndMax[0] = min;
		minAndMax[1] = max;
		return minAndMax;
	}

	public static double[] getMinMax(float[] a) {
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		double value;
		for (int i=0; i<a.length; i++) {
			value = a[i];
			if (value<min)
				min = value;
			if (value>max)
				max = value;
		}
		double[] minAndMax = new double[2];
		minAndMax[0] = min;
		minAndMax[1] = max;
		return minAndMax;
	}
	
	/** Converts the float array 'a' to a double array. */
	public static double[] toDouble(float[] a) {
		int len = a.length;
		double[] d = new double[len];
		for (int i=0; i<len; i++)
			d[i] = a[i];
		return d;
	}
	
	/** Converts the double array 'a' to a float array. */
	public static float[] toFloat(double[] a) {
		if (a==null)
			return null;
		int len = a.length;
		float[] f = new float[len];
		for (int i=0; i<len; i++)
			f[i] = (float)a[i];
		return f;
	}

	/** Adds a number to all array elements */
	public static void addToArray(float[] a, float value) {
		for (int i=0; i<a.length; i++)
			a[i] += value;
	}

	/** Converts carriage returns to line feeds. */
	public static String fixNewLines(String s) {
		if (s==null)
			return null;
		char[] chars = s.toCharArray();
		for (int i=0; i<chars.length; i++)
			{if (chars[i]=='\r') chars[i] = '\n';}
		return new String(chars);
	}

	/**
	* Returns a double containg the value represented by the 
	* specified <code>String</code>.
	*
	* @param      s   the string to be parsed.
	* @param      defaultValue   the value returned if <code>s</code>
	*	does not contain a parsable double
	* @return     The double value represented by the string argument or
	*	<code>defaultValue</code> if the string does not contain a parsable double
	*/
	public static double parseDouble(String s, double defaultValue) {
		if (s==null) return defaultValue;
		try {
			defaultValue = Double.parseDouble(s);
		} catch (NumberFormatException e) {}
		return defaultValue;			
	}

	/**
	* Returns a double containg the value represented by the 
	* specified <code>String</code>.
	*
	* @param      s   the string to be parsed.
	* @return     The double value represented by the string argument or
	*	Double.NaN if the string does not contain a parsable double
	*/
	public static double parseDouble(String s) {
		return parseDouble(s, Double.NaN);
	}
	
	/** Returns the number of decimal places needed to display a 
		number, or -2 if exponential notation should be used. */
	public static int getDecimalPlaces(double n) {
		if ((int)n==n || Double.isNaN(n))
			return 0;
		String s = ""+n;
		if (s.contains("E"))
			return -2;
		while (s.endsWith("0"))
			s = s.substring(0,s.length()-1);
		int index = s.indexOf(".");
		if (index==-1) return 0;
		int digits = s.length() - index - 1;
		if (digits>4) digits=4;
		return digits;
	}
	
	/** Returns the number of decimal places needed to display two numbers,
		or -2 if exponential notation should be used. */
	public static int getDecimalPlaces(double n1, double n2) {
		if ((int)n1==n1 && (int)n2==n2)
			return 0;
		int digits = getDecimalPlaces(n1);
		int digits2 = getDecimalPlaces(n2);
		if (digits==0)
			return digits2;
		if (digits2==0)
			return digits;
		if (digits<0 || digits2<0)
			return digits;
		if (digits2>digits)
			digits = digits2;
		return digits;
	}
	
	/** Splits a string into substrings using the default delimiter set, 
	which is " \t\n\r" (space, tab, newline and carriage-return). */
	public static String[] split(String str) {
		return split(str, " \t\n\r");
	}

	/** Splits a string into substring using the characters
	contained in the second argument as the delimiter set. */
	public static String[] split(String str, String delim) {
		if (delim.equals("\n"))
			return splitLines(str);
		StringTokenizer t = new StringTokenizer(str, delim);
		int tokens = t.countTokens();
		String[] strings;
		if (tokens>0) {
			strings = new String[tokens];
			for(int i=0; i<tokens; i++) 
				strings[i] = t.nextToken();
		} else
			strings = new String[0];
		return strings;
	}
	
	static String[] splitLines(String str) {
		Vector v = new Vector();
		try {
			BufferedReader br  = new BufferedReader(new StringReader(str));
			String line;
			while (true) {
				line = br.readLine();
				if (line == null) break;
				v.addElement(line);
			}
			br.close();
		} catch(Exception e) { }
		String[] lines = new String[v.size()];
		v.copyInto((String[])lines);
		return lines;
	}
	
	/** Returns a sorted list of indices of the specified double array.
		Modified from: http://stackoverflow.com/questions/951848 by N.Vischer.
	*/
	public static int[] rank(double[] values) {
		int n = values.length;
		final Integer[] indexes = new Integer[n];
		final Double[] data = new Double[n];
		for (int i=0; i<n; i++) {
			indexes[i] = new Integer(i);
			data[i] = new Double(values[i]);
		}
		Arrays.sort(indexes, new Comparator<Integer>() {
			public int compare(final Integer o1, final Integer o2) {
				return data[o1].compareTo(data[o2]);
			}
		});
		int[] indexes2 = new int[n];
		for (int i=0; i<n; i++)
			indexes2[i] = indexes[i].intValue();
		return indexes2;
	}

	/** Returns a sorted list of indices of the specified String array. */
	public static int[] rank(final String[] data) {
		int n = data.length;
		final Integer[] indexes = new Integer[n];
		for (int i=0; i<n; i++)
			indexes[i] = new Integer(i);
		Arrays.sort(indexes, new Comparator<Integer>() {
			public int compare(final Integer o1, final Integer o2) {
				return data[o1].compareToIgnoreCase(data[o2]);
			}
		});
		int[] indexes2 = new int[n];
		for (int i=0; i<n; i++)
			indexes2[i] = indexes[i].intValue();
		return indexes2;
	}
	
	/** Returns an array linearly resampled to a different length. */
	public static double[] resampleArray(double[] y1, int len2) {
		int len1 = y1.length;
		double factor =  (double)(len2-1)/(len1-1);
		double[] y2 = new double[len2];
		if(len1 == 0){
		    return y2;
		}
		if(len1 == 1){
		    for (int jj=0; jj<len2; jj++)
			    y2[jj] = y1[0];
		    return(y2);
		}
		double[] f1 = new double[len1];//fractional positions
		double[] f2 = new double[len2];
		for (int jj=0; jj<len1; jj++)
			f1[jj] = jj*factor;
		for (int jj=0; jj<len2; jj++)
			f2[jj] = jj/factor;
		for (int jj=0; jj<len2-1; jj++) {
			double pos = f2[jj];
			int leftPos = (int)Math.floor(pos);
			int rightPos = (int)Math.floor(pos)+1;
			double fraction = pos-Math.floor(pos);
			double value = y1[leftPos] + fraction*(y1[rightPos]-y1[leftPos]);
			y2[jj] = value;
		}
		y2[len2-1] = y1[len1-1];
		return y2;
	}

	/** Opens a text file in ij.jar as a String (example path: "/macros/Circle_Tool.txt"). */
	public static String openFromIJJarAsString(String path) {
		return (new ij.plugin.MacroInstaller()).openFromIJJar(path);
	}

	/** Copies the contents of the file at 'path1' to 'path2', returning an error message
		(as a non-empty string) if there is an error. Based on the method with the
		same name in Tobias Pietzsch's TifBenchmark class.
	*/
	public static String copyFile(String path1, String path2) {
		File f1 = new File(path1);
		File f2 = new File(path2);	
		try {
			if (!f1.exists() )
				return "Source file does not exist";
			if (!f2.exists() )
				f2.createNewFile();
			long time = f1.lastModified();	
			FileInputStream stream1 = new FileInputStream(f1);
			FileChannel channel1 = stream1.getChannel();
			FileOutputStream stream2 = new FileOutputStream(f2);
			final FileChannel channel2 = stream2.getChannel();
			if (channel2!=null && channel1!=null )
				channel2.transferFrom(channel1, 0, channel1.size());
			channel1.close();
			stream1.close();
			channel2.close();
			stream2.close();	
			f2.setLastModified(time);
		} catch(Exception e) {
			return e.getMessage();
		}
		return "";
	}
	
	/** Retrieves a number form a list of key-number pairs like "value1=1234.5 area=1.2e6".
	 *  The "=" (if present) must be part of the 'key' string. Delimiters may be commas, semicolons or whitespace.
	 *  There must be no whitespace between key and number.
	 *  Returns Double.NaN if 'list' is null, if the key is not found, if the number is 'NaN' or invalid. */
	public static double getNumberFromList(String list, String key) {
		if (list == null) return Double.NaN;
		int i = list.indexOf(key);
		if (i < 0) return Double.NaN;
		int start = i + key.length();
		int n = start;
		while (n < list.length() && !isDelimiter(list.charAt(n))) n++;
		return parseDouble(list.substring(start, n));
	}

	private static boolean isDelimiter(char c) {
		return Character.isWhitespace(c) || c==',' || c==';';
	}

}
