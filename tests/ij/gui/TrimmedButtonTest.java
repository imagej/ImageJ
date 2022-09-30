package ij.gui;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link TrimmedButton}.
 *
 * @author Barry DeZonia
 */
public class TrimmedButtonTest {
	
	TrimmedButton b; 

	/* Tests removed 7-20-10
	 * because hudson complains about missing gui

	@Test
	public void testTrimmedButton() {
		b = new TrimmedButton("Hookey Booyah", 4);
		Dimension dims = b.getMinimumSize();
		assertEquals(-4,dims.width);
		assertEquals(0,dims.height);
	}

	@Test
	public void testGetMinimumSize() {
		b = new TrimmedButton("Hookey Booyah", 1);
		Dimension dims = b.getMinimumSize();
		assertEquals(-1,dims.width);
		assertEquals(0,dims.height);
	}

	@Test
	public void testGetPreferredSize() {
		b = new TrimmedButton("Hookey Booyah", -2);
		Dimension dims = b.getPreferredSize();
		assertEquals(2,dims.width);
		assertEquals(0,dims.height);
	}
	
	*/

	// at least one test must be present
	@Test
	public void testNothing()
	{
		assertTrue(true);
	}
}
