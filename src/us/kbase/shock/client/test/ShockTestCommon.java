package us.kbase.shock.client.test;

import us.kbase.common.test.TestException;

public class ShockTestCommon {
	
	public static final String SHOCKEXE = "test.shock.exe";
	public static final String SHOCKVER = "test.shock.version";
	public static final String MONGOEXE = "test.mongo.exe";
	
	public static final String TEST_TEMP_DIR = "test.temp.dir";
	public static final String KEEP_TEMP_DIR = "test.temp.dir.keep";
			
	public static void printJava() {
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
	}
	
	private static String getProp(String prop) {
		if (System.getProperty(prop) == null || prop.isEmpty()) {
			throw new TestException("Property " + prop + " cannot be null or the empty string.");
		}
		return System.getProperty(prop);
	}
	
	public static String getTempDir() {
		return getProp(TEST_TEMP_DIR);
	}
	
	public static String getMongoExe() {
		return getProp(MONGOEXE);
	}
	
	public static String getShockExe() {
		return getProp(SHOCKEXE);
	}
	
	public static String getShockVersion() {
		return getProp(SHOCKVER);
	}

	
	public static boolean getDeleteTempFiles() {
		return !"true".equals(System.getProperty(KEEP_TEMP_DIR));
	}
}
