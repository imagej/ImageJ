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

package ij.macro;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Unit tests for {@link Symbol}.
 *
 * @author Barry DeZonia
 */
public class SymbolTest {

	@Test
	public void testSymbolIntString() 
	{
		String testString = "TestText";
		int a = MacroConstants.TOK_SHIFT;
		Symbol st = new Symbol( a, testString );
		assertEquals( st.toString(), (a&0xffff)+" 0.0 "+testString );
	}

	@Test
	public void testSymbolDouble() 
	{
		double testValue = 1.1;
		Symbol st = new Symbol( testValue );
		assertEquals( st.toString(), "0 1.1 null" );
	}

	@Test
	public void testGetFunctionType() 
	{
		String testString = "TestText";
		int a = MacroConstants.INVERT;
		Symbol st = new Symbol( a, testString );
		assertEquals( st.getFunctionType(), 134 );
	
		a = MacroConstants.GET_ZOOM;
		st = new Symbol( a, testString );
		assertEquals( st.getFunctionType(), 135 );
		
		a = MacroConstants.SELECTION_NAME;
		st = new Symbol( a, testString );
		assertEquals( st.getFunctionType(), 136 );
		
		a = MacroConstants.ARRAY_FUNC;
		st = new Symbol( a, testString );
		assertEquals( st.getFunctionType(), 137 );

	}

	@Test
	public void testToString() {
		double testValue = 1.1;
		Symbol st = new Symbol( testValue );
		assertEquals( st.toString(), "0 1.1 null" );
	}

}
