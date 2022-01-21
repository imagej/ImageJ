package ij;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link CommandListener}.
 *
 * @author Barry DeZonia
 */
public class CommandListenerTest {
	
	// implement the interface so that we have compile time check it exists
	class FakeCL implements CommandListener {
		@Override
		public String commandExecuting(String command) { return null; }
	}
	
	@Test
	public void testExistence() {
		assertTrue(true);
	}
	
}
