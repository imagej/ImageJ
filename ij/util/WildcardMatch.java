package ij.util;

/**
 * This class allows for simple wildcard pattern matching. Possible
 * patterns allow to match single characters ('?') or any count of
 * characters ('*').<p>
 * Wildcard characters can be escaped (default: by an '\').<p>
 * This class always matches for the whole word.<p>
 * Examples:
 * <pre>
 * WildcardMatch wm = new WildcardMatch();
 * System.out.println(wm.match("CfgOptions.class", "C*.class"));	  // true
 * System.out.println(wm.match("CfgOptions.class", "?gOpti*c?as?"));  // false
 * System.out.println(wm.match("CfgOptions.class", "??gOpti*c?ass")); // true
 * System.out.println(wm.match("What's this?",	   "What*\\?"));	  // true
 * System.out.println(wm.match("What's this?",	   "What*?"));		  // true
 * System.out.println(wm.match("A \\ backslash", "*\\\\?back*"));	  // true
 * </pre>
 */
public class WildcardMatch {

	public WildcardMatch() {
	}

	public WildcardMatch(char singleChar, char multipleChars) {
		this.sc = singleChar;
		this.mc = multipleChars;
	}

	/**
	 * Sets new characters to be used as wildcard characters, overriding the
	 * the default of '?' for any single character match and '*' for any
	 * amount of characters, including 0 characters.
	 * @param singleChar The char used to match exactly ONE character.
	 * @param multipleChars The char used to match any amount of characters
	 * including o characters.
	 */
	public void setWildcardChars(char singleChar, char multipleChars) {
		this.sc = singleChar;
		this.mc = multipleChars;
	}

	/**
	 * Sets the new character to be used as an escape character, overriding the
	 * the default of '\'.
	 * @param escapeChar The char used to match escape wildcard characters.
	 */
	public void setEscapeChar(char escapeChar) {
		this.ec = escapeChar;
	}

	/**
	 * Returns the character used to specify exactly one character.
	 * @return Wildcard character matching any single character.
	 */
	public char getSingleWildcardChar() {
		return sc;
	}

	/**
	 * Returns the character used to specify any amount of characters.
	 * @return Wildcard character matching any count of characters.
	 */
	public char getMultipleWildcardChar() {
		return mc;
	}

	/**
	 * Returns the character used to escape the wildcard functionality of a
	 * wildcard character. If two escape characters are used in sequence, they
	 * mean the escape character itself. It defaults to '\'.
	 * @return Escape character.
	 */
	public char getEscapeChar() {
		return ec;
	}

	/**
	 * Makes pattern matching case insensitive.
	 * @param caseSensitive false for case insensitivity. Default is case
	 * sensitive match.
	 */
	public void setCaseSensitive(boolean caseSensitive) {
		this.caseSensitive = caseSensitive;
	}

	/**
	 * Returns the current state of case sensitivity.
	 * @return true for case sensitive pattern matching, false otherwise.
	 */
	public boolean getCaseSensitive() {
		return caseSensitive;
	}
	boolean preceededByMultipleChar = false;

	/**
	 * Matches a string against a pattern with wildcards. Two wildcard types
	 * are supported: single character match (defaults to '?') and ANY
	 * character match ('*'), matching any count of characters including 0.
	 * Wildcard characters may be escaped by an escape character, which
	 * defaults to '\'.
	 * @param s The string, in which the search should be performed.
	 * @param pattern The search pattern string including wildcards.
	 * @return true, if string 's' matches 'pattern'.
	 */
	public boolean match(String s, String pattern) {
		preceededByMultipleChar = false;
		isEscaped = false;
		if (!caseSensitive) {
			pattern = pattern.toLowerCase();
			s = s.toLowerCase();
		}
		int offset = 0;

		while (true) {
			String ps = getNextSubString(pattern);
			int len = ps.length();
			pattern = pattern.substring(len + escCnt);

			if (len > 0 && isWildcard(ps.charAt(0)) && escCnt == 0) {
				offset = getWildcardOffset(ps.charAt(0));
				if (isSingleWildcardChar(ps.charAt(0))) {
					s = s.substring(1);
// This is not yet enough: If a '*' precedes '?', 's' might be SHORTER
// than seen here, for this we need preceededByMultipleChar variable...
				}
				if (pattern.length() == 0) {
					return s.length() <= offset || preceededByMultipleChar;
				}
			} else {
				int idx = s.indexOf(ps);
				if (idx < 0 || (idx > offset && !preceededByMultipleChar)) {
					return false;
				}
				s = s.substring(idx + len);
				preceededByMultipleChar = false;
			}
			if (pattern.length() == 0) {
				return s.length() == 0;
			}
		}
	}
	private char sc = '?';
	private char mc = '*';
	private char ec = '\\'; // Escape character
	private boolean caseSensitive = true;
	private boolean isEscaped = false;
	private int escCnt = 0;

	private String getNextSubString(String pat) {
		escCnt = 0;
		if ("".equals(pat)) {
			return "";
		}
		if (isWildcard(pat.charAt(0))) {
			// if '?' is preceeded by '*', we need special considerations:
			if (pat.length() > 1 && !isSingleWildcardChar(pat.charAt(0)) && isSingleWildcardChar(pat.charAt(1))) {
				preceededByMultipleChar = true;
			}
			return pat.substring(0, 1);
		} else {
			String s = "";
			int i = 0;
			while (i < pat.length() && !isWildcard(pat.charAt(i), isEscaped)) {
				if (pat.charAt(i) == ec) {
					isEscaped = !isEscaped;
					if (!isEscaped) {
						s += pat.charAt(i);
					}
					escCnt++;
				} else if (isWildcard(pat.charAt(i))) {
					isEscaped = false;
					s += pat.charAt(i);
				} else {
					s += pat.charAt(i);
				}
				i++;
			}
			return s;
		}
	}

	private boolean isWildcard(char c, boolean isEsc) {
		return !isEsc && isWildcard(c);
	}

	private boolean isSingleWildcardChar(char c) {
		return c == sc;
	}

	private boolean isWildcard(char c) {
		return c == mc || c == sc;
	}

	private int getWildcardOffset(char c) {
		if (c == mc) {
			return Integer.MAX_VALUE;
		}
		return 0;
	}
}