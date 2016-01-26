package us.kbase.shock.client.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthUser;
import us.kbase.auth.TokenExpiredException;
import us.kbase.common.test.TestException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.common.test.controllers.shock.ShockController;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockNoNodeException;

public class ShockTrustSSLTest {
	
	//TODO remove this test when trusted vs. no trust SLL handling is fixed
	/* Currently the HttpClient in the BasicShockClient is static and has
	 * two flavors - one is std and one trusts self signed certificates.
	 * However, the client is stored statically and is memoized, so the first
	 * type that gets built is used for all further BSC instances. This
	 * test makes sure the basic client operations work for clients that trust
	 * all SSCs. 
	 */
	
	private static MongoController MONGO;
	private static ShockController SHOCK;

	private static AuthUser USER;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
		
		MONGO = new MongoController(ShockTestCommon.getMongoExe(),
				Paths.get(ShockTestCommon.getTempDir()));
		System.out.println("Using Mongo temp dir " + MONGO.getTempDir());
		
		System.out.println("Passing version " +
				ShockTestCommon.getShockVersion() + " to Shock controller");
		SHOCK = new ShockController(
				ShockTestCommon.getShockExe(),
				ShockTestCommon.getShockVersion(),
				Paths.get(ShockTestCommon.getTempDir()),
				"***---fakeuser---***",
				"localhost:" + MONGO.getServerPort(),
				"ShockTests_ShockDB",
				"foo",
				"foo");
		System.out.println("Shock controller registered version: "
				+ SHOCK.getVersion());
		if (SHOCK.getVersion() == null) {
			System.out.println(
					"Unregistered version - Shock may not start correctly");
		}
		System.out.println("Using Shock temp dir " + SHOCK.getTempDir());
		
		URL url = new URL("http://localhost:" + SHOCK.getServerPort());
		System.out.println("Testing shock clients pointed at: " + url);
		String u1 = System.getProperty("test.user1");
		String p1 = System.getProperty("test.pwd1");
		
		System.out.println("Logging in users");
		try {
			USER = AuthService.login(u1, p1);
		} catch (AuthException ae) {
			throw new TestException("Unable to login with test.user1: " + u1 +
					"\nPlease check the credentials in the test configuration.", ae);
		}
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (SHOCK != null) {
			SHOCK.destroy(ShockTestCommon.getDeleteTempFiles());
		}
		if (MONGO != null) {
			MONGO.destroy(ShockTestCommon.getDeleteTempFiles());
		}
	}
	
	@Test
	public void addGetDeleteNodeBasicTrustSSL() throws Exception {
		//just makes sure the client basically works in naive mode
		//not going to set up nginx with a SSC just to run one test
		//although, dear reader, you are welcome to do so and it'd be much
		//appreciated
		BasicShockClient bsc = new BasicShockClient(
				new URL("http://localhost:" + SHOCK.getServerPort()),
				USER.getToken(), true);
		//also not testing the tokenless constructor since would require 
		//spinning up another shock server that writable w/o auth, and that
		//config isn't used in KBase
		addGetDeleteNodeBasic(bsc);
	}

	private void addGetDeleteNodeBasic(BasicShockClient bsc)
			throws IOException, ShockHttpException, TokenExpiredException,
			Exception {
		ShockNode sn = bsc.addNode();
		ShockNode snget = bsc.getNode(sn.getId());
		assertThat("get node != add Node output", snget.toString(), is(sn.toString()));
		bsc.deleteNode(sn.getId());
		getDeletedNode(bsc, sn.getId());
	}
	
	private void getDeletedNode(BasicShockClient bsc, ShockNodeId id) throws Exception {
		try {
			bsc.getNode(id);
			fail("Able to retrieve deleted node");
		} catch (ShockNoNodeException snne) {
			assertThat("Bad exception message", snne.getLocalizedMessage(),
					is("Node not found"));
		}
	}
}
