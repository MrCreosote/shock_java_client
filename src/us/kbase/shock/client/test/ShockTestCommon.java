package us.kbase.shock.client.test;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.common.test.TestException;

public class ShockTestCommon {
	
	public static final String SHOCKEXE = "test.shock.exe";
	public static final String SHOCKVER = "test.shock.version";
	public static final String MONGOEXE = "test.mongo.exe";
	
	public static final String TEST_TEMP_DIR = "test.temp.dir";
	public static final String KEEP_TEMP_DIR = "test.temp.dir.keep";
	
	public static final String JARS_PATH = "test.jars.dir";
			
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
	
	public static Path getJarsDir() {
		return Paths.get(getProp(JARS_PATH));
	}

	public static boolean getDeleteTempFiles() {
		return !"true".equals(System.getProperty(KEEP_TEMP_DIR));
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
