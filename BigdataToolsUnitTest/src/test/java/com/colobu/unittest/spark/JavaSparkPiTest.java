package com.colobu.unittest.spark;

import java.util.Properties;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class JavaSparkPiTest {

	@Test
	public void testPi() {
		Properties props = System.getProperties();		
		props.setProperty("spark.master", "local[4]");
		props.setProperty("spark.rdd.compress", "true");
		props.setProperty("spark.executor.memory", "1g");

		try {
			double pi = JavaSparkPi.calculatePi(new String[]{"1"});
			assertTrue((pi -3.14) < 1);
		} catch (Exception e) {
			fail(e.getMessage(),e);
		}
	}
}
