
package ij.gui;

import static org.junit.Assert.assertNotNull;
import ij.IJInfo;
import ij.ImagePlus;
import ij.process.ByteProcessor;

import org.junit.Test;

/**
 * Unit tests for {@link FreehandRoi}.
 *
 * @author Barry DeZonia
 */
public class FreehandRoiTest {
	
	FreehandRoi roi;

	// only one public method - the constructor
	@Test
	public void testFreehandRoi() {
		if (IJInfo.RUN_ENHANCED_TESTS)
		{
			// the underlying superclass PolygonRoi assumes that an ImageCanvas exists. This next test bombs out as is.
			
			ImagePlus ip = new ImagePlus("Zoops",new ByteProcessor(2,2,new byte[]{1,2,3,4},null));
			roi = new FreehandRoi(2,4,ip);
			assertNotNull(roi);
		}
	}

}
