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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.awt.Dimension;

import org.junit.Test;

/**
 * Unit tests for {@link ProgressBar}.
 *
 * @author Barry DeZonia
 */
public class ProgressBarTest {

	ProgressBar bar;
	
	@Test
	public void testProgressBar() {
		bar = new ProgressBar(100, 20);
		assertNotNull(bar);
		assertEquals(0,bar.getWidth());   // huh. surprising.
		assertEquals(0,bar.getHeight());  // huh. surprising.
	}

	@Test
	public void testShowDouble() {
		bar = new ProgressBar(100, 20);
		bar.show(20.0);
		// nothing to test - its a visual method
	}

	@Test
	public void testShowDoubleBoolean() {
		bar = new ProgressBar(100, 20);
		bar.show(20.0,false);
		// nothing to test - its a visual method
		bar.show(20.0,true);
		// nothing to test - its a visual method
	}

	@Test
	public void testShowIntInt() {
		bar = new ProgressBar(100, 20);
		for (int i = 0; i < 5; i++)
			bar.show(i,5);
		// nothing to test - its a visual method
	}

	@Test
	public void testUpdateGraphics() {
		// can't test - it needs a Graphics context to work
	}

	@Test
	public void testPaintGraphics() {
		// can't test - it needs a Graphics context to work
	}

	@Test
	public void testGetPreferredSize() {
		bar = new ProgressBar(1600, 384);
		Dimension size = bar.getPreferredSize();
		assertEquals(1600,size.width);
		assertEquals(384,size.height);
	}

	@Test
	public void testSetBatchMode() {
		// no accessor and only visual differences in code - can only test API existence
		bar = new ProgressBar(10,3);
		bar.setBatchMode(false);
		bar.setBatchMode(true);
	}

}
