package ij;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link ImageListener}.
 *
 * @author Barry DeZonia
 */
public class ImageListenerTest {

	// implement the interface so that we have compile time check it exists
	class FakeIL implements ImageListener {

		@Override
		public void imageClosed(ImagePlus imp) {
			// do nothing
		}

		@Override
		public void imageOpened(ImagePlus imp) {
			// do nothing
		}

		@Override
		public void imageUpdated(ImagePlus imp) {
			// do nothing
		}
		
	}
	
	@Test
	public void testExistence() {
		assertTrue(true);
	}
}
