/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2016 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

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
