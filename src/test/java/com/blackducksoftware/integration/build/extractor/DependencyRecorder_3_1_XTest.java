package com.blackducksoftware.integration.build.extractor;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class DependencyRecorder_3_1_XTest {
	@Test
	public void testDependencyRecorder() {
		final DependencyRecorder_3_1_X dependencyRecorder = new DependencyRecorder_3_1_X();
		assertNotNull(dependencyRecorder);
	}

}
