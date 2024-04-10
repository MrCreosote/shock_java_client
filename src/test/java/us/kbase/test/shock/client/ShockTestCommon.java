package us.kbase.test.shock.client;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.ini4j.Ini;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.testutils.TestException;

public class ShockTestCommon {
	
	public static final String BLOBEXE = "test.blobstore.exe";
	public static final String MINIOEXE = "test.minio.exe";
	public static final String MONGOEXE = "test.mongo.exe";
	
	public static final String TEST_TEMP_DIR = "test.temp.dir";
	public static final String KEEP_TEMP_DIR = "test.temp.dir.keep";
	
	public static final String TEST_CONFIG_FILE_PROP_NAME = "test.cfg";
	public static final String TEST_CONFIG_FILE_SECTION = "ShockClientTest";
			
	public static void printJava() {
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
	}
	
	public static String getTempDir() {
		return getTestProperty(TEST_TEMP_DIR);
	}
	
	public static String getMinioExe() {
		return getTestProperty(MINIOEXE);
	}
	
	public static String getBlobstoreExe() {
		return getTestProperty(BLOBEXE);
	}
	
	public static String getMongoExe() {
		return getTestProperty(MONGOEXE);
	}
	
	public static boolean getDeleteTempFiles() {
		return !"true".equals(getTestProperty(KEEP_TEMP_DIR, true));
	}
	
	private static Map<String, String> testConfig;

	public static String getTestProperty(final String propertyKey, final boolean allowNull) {
		getTestConfig();
		final String prop = testConfig.get(propertyKey);
		if (!allowNull && (prop == null || prop.trim().isEmpty())) {
			throw new TestException(String.format(
					"Property %s in section %s of test file %s is missing",
					propertyKey, TEST_CONFIG_FILE_SECTION, getConfigFilePath()));
		}
		return prop;
	}

	public static String getTestProperty(final String propertyKey) {
		return getTestProperty(propertyKey, false);
	}

	private static void getTestConfig() {
		if (testConfig != null) {
			return;
		}
		final Path testCfgFilePath = getConfigFilePath();
		final Ini ini;
		try {
			ini = new Ini(testCfgFilePath.toFile());
		} catch (IOException ioe) {
			throw new TestException(String.format(
					"IO Error reading the test configuration file %s: %s",
					testCfgFilePath, ioe.getMessage()), ioe);
		}
		testConfig = ini.get(TEST_CONFIG_FILE_SECTION);
		if (testConfig == null) {
			throw new TestException(String.format("No section %s found in test config file %s",
					TEST_CONFIG_FILE_SECTION, testCfgFilePath));
		}
	}

	private static Path getConfigFilePath() {
		final String testCfgFilePathStr = System.getProperty(TEST_CONFIG_FILE_PROP_NAME);
		if (testCfgFilePathStr == null || testCfgFilePathStr.trim().isEmpty()) {
			throw new TestException(String.format("Cannot get the test config file path." +
					" Ensure the java system property %s is set to the test config file location.",
					TEST_CONFIG_FILE_PROP_NAME));
		}
		return Paths.get(testCfgFilePathStr).toAbsolutePath().normalize();
	}
	
	public static void createAuthUser(
			final URL authURL,
			final String userName,
			final String displayName)
			throws Exception {
		final URL target = new URL(authURL.toString() + "/api/V2/testmodeonly/user");
		final HttpURLConnection conn = getPOSTConnection(target);

		final DataOutputStream writer = new DataOutputStream(conn.getOutputStream());
		final Map<String, String> input = new HashMap<>();
		input.put("user", userName);
		input.put("display", displayName);
		writer.writeBytes(new ObjectMapper().writeValueAsString(input));
		writer.flush();
		writer.close();

		checkForError(conn);
	}

	private static HttpURLConnection getPOSTConnection(final URL target) throws Exception {
		return getConnection("POST", target);
	}
	
	private static HttpURLConnection getConnection(final String verb, final URL target)
			throws Exception {
		final HttpURLConnection conn = (HttpURLConnection) target.openConnection();
		conn.setRequestMethod(verb);
		conn.setRequestProperty("content-type", "application/json");
		conn.setRequestProperty("accept", "application/json");
		conn.setDoOutput(true);
		return conn;
	}

	private static void checkForError(final HttpURLConnection conn) throws IOException {
		final int rescode = conn.getResponseCode();
		if (rescode < 200 || rescode >= 300) {
			System.out.println("Response code: " + rescode);
			String err = IOUtils.toString(conn.getErrorStream()); 
			System.out.println(err);
			if (err.length() > 200) {
				err = err.substring(0, 200);
			}
			throw new TestException(err);
		}
	}

	public static String createLoginToken(final URL authURL, String user) throws Exception {
		final URL target = new URL(authURL.toString() + "/api/V2/testmodeonly/token");
		final HttpURLConnection conn = getPOSTConnection(target);

		final DataOutputStream writer = new DataOutputStream(conn.getOutputStream());
		final Map<String, String> input = new HashMap<>();
		input.put("user", user);
		input.put("type", "Login");
		writer.writeBytes(new ObjectMapper().writeValueAsString(input));
		writer.flush();
		writer.close();

		checkForError(conn);
		final String out = IOUtils.toString(conn.getInputStream());
		@SuppressWarnings("unchecked")
		final Map<String, Object> resp = new ObjectMapper().readValue(out, Map.class);
		return (String) resp.get("token");
	}
}
