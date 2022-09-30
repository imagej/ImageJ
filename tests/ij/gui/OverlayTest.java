package ij.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import ij.IJInfo;

import java.awt.Rectangle;

import org.junit.Test;

/**
 * Unit tests for {@link Overlay}.
 *
 * @author Barry DeZonia
 */
public class OverlayTest {

	Overlay o;

	// ********************* TESTS ****************************************************
	
	@Test
	public void testOverlay() {
		// default construction
		o = new Overlay();
		assertEquals(0,o.size());
	}

	@Test
	public void testOverlayRoi() {
		// construct from a Roi
		Roi roi = new Roi(new Rectangle(1,2,3,4));
		o = new Overlay(roi);
		assertEquals(1,o.size());
		assertEquals(roi,o.get(0));
	}

	@Test
	public void testAdd() {
		// add a roi
		Roi roi = new Roi(new Rectangle(1,2,3,4));
		o = new Overlay();
		assertEquals(0,o.size());
		o.add(roi);
		assertEquals(1,o.size());
		assertEquals(roi,o.get(0));
	}

	@Test
	public void testAddElement() {
		// add a roi via addElement
		Roi roi = new Roi(new Rectangle(1,2,3,4));
		o = new Overlay();
		assertEquals(0,o.size());
		o.addElement(roi);
		assertEquals(1,o.size());
		assertEquals(roi,o.get(0));
	}

	@Test
	public void testRemoveInt() {
		
		// remove a roi via index
		
		// setup overlay
		Roi roi = new Roi(new Rectangle(1,2,3,4));
		o = new Overlay(roi);
		assertEquals(1,o.size());
		
		// try bad indices
		if (IJInfo.RUN_ENHANCED_TESTS)
		{
			// results in ArrayIndex == -1 exception
			o.remove(-1);
			// results in ArrayIndex out of range exception
			o.remove(1);
		}
		
		// remove data
		o.remove(0);
		
		// test results
		assertEquals(0,o.size());
	}

	@Test
	public void testRemoveRoi() {
		
		// remove a roi via object ref
		
		// setup overlay
		Roi roi = new Roi(new Rectangle(1,2,3,4));
		o = new Overlay(roi);
		assertEquals(1,o.size());
		
		// try null ref
		o.remove((Roi) null);
		assertEquals(1,o.size());

		// try bad obj ref
		o.remove(new Roi(new Rectangle(0,0,16,5)));
		assertEquals(1,o.size());

		// try good obj ref
		o.remove(roi);
		assertEquals(0,o.size());
	}

	@Test
	public void testClear() {
		// setup overlay
		Roi roi = new Roi(new Rectangle(1,2,3,4));
		o = new Overlay(roi);
		assertEquals(1,o.size());
		
		// clear it
		o.clear();
		assertEquals(0,o.size());
	}

	@Test
	public void testGet() {
		// setup overlay
		Roi roi = new Roi(new Rectangle(1,2,3,4));
		o = new Overlay(roi);
		assertEquals(1,o.size());
		
		if (IJInfo.RUN_ENHANCED_TESTS)
		{
			// try bad ref to low
			assertNull(o.get(-1));

			// try bad ref too high
			assertNull(o.get(1));
		}
		
		// try valid ref
		assertEquals(roi,o.get(0));
	}

	@Test
	public void testSize() {
		// add a bunch of Rois and track size
		o = new Overlay();
		for (int i = 0; i < 103; i++)
		{
			o.add(new Roi(new Rectangle(1,2,3,4)));
			assertEquals(i+1,o.size());
		}
	}

	@Test
	public void testToArray() {
		Roi[] rois;
		
		// try on an empty Overlay
		o = new Overlay();
		rois = o.toArray();
		assertEquals(0,rois.length);
		
		// try on a populated Overlay
		Roi roi = new Roi(new Rectangle(1,2,3,4));
		o = new Overlay(roi);
		rois = o.toArray();
		assertEquals(1,rois.length);
		assertEquals(roi,rois[0]);
	}

	@Test
	public void testToString() {
	}

	@Test
	public void testDrawLabels() {
		// no accessor - can only do a compile time check
		o = new Overlay();
		o.drawLabels(true);
		o.drawLabels(false);
	}

}
