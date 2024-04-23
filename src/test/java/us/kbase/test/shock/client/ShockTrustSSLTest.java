package us.kbase.test.shock.client;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.auth.AuthToken;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockNoNodeException;
import us.kbase.test.auth2.authcontroller.AuthController;
import us.kbase.test.shock.client.controllers.blobstore.BlobstoreController;
import us.kbase.test.shock.client.controllers.minio.MinioController;

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
	private static MinioController MINIO;
	private static BlobstoreController BLOB;
	private static AuthController AUTH;
	
	private static final String USER1 = "user1";
	private static AuthToken TOKEN;

	@BeforeClass
	public static void setUpClass() throws Exception {
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
		
		MONGO = new MongoController(ShockTestCommon.getMongoExe(),
				Paths.get(ShockTestCommon.getTempDir()));
		System.out.println("Using Mongo temp dir " + MONGO.getTempDir());
		final String mongohost = "localhost:" + MONGO.getServerPort();

		final String minioUser = "s3key";
		final String minioKey = "supersecretkey";
		MINIO = new MinioController(
				ShockTestCommon.getMinioExe(),
				minioUser,
				minioKey,
				Paths.get(ShockTestCommon.getTempDir())
				);
		System.out.println("Using Minio temp dir " + MINIO.getTempDir());
		
		AUTH = new AuthController(
				"localhost:" + MONGO.getServerPort(),
				"test_" + ShockTests.class.getSimpleName(),
				Paths.get(ShockTestCommon.getTempDir()));
		final URL authURL = new URL("http://localhost:" + AUTH.getServerPort() + "/testmode");
		System.out.println("started auth server at " + authURL);
		ShockTestCommon.createAuthUser(authURL, USER1, "display1");
		final String token1 = ShockTestCommon.createLoginToken(authURL, USER1);
		TOKEN = new AuthToken(token1, USER1);
		
		BLOB = new BlobstoreController(
				ShockTestCommon.getBlobstoreExe(),
				Paths.get(ShockTestCommon.getTempDir()),
				mongohost,
				ShockTests.class.getSimpleName() + "_blobstore_test",
				"localhost:" + MINIO.getServerPort(),
				"blobstore",
				minioUser,
				minioKey,
				"us-west-1",
				authURL,
				Collections.emptyList());
		final URL blobURL = new URL("http://localhost:" + BLOB.getPort());
		System.out.println("started blobstore at " + blobURL);
		System.out.println("Using Blobstore temp dir " + BLOB.getTempDir());
		
		URL url = new URL("http://localhost:" + BLOB.getPort());
		System.out.println("Testing shock clients pointed at: " + url);
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (BLOB != null) {
			BLOB.destroy(ShockTestCommon.getDeleteTempFiles());
		}
		if (MINIO != null) {
			MINIO.destroy(ShockTestCommon.getDeleteTempFiles());
		}
		if (AUTH != null) {
			AUTH.destroy(ShockTestCommon.getDeleteTempFiles());
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
				new URL("http://localhost:" + BLOB.getPort()),
				TOKEN, true);
		//also not testing the tokenless constructor since would require 
		//spinning up another shock server that writable w/o auth, and that
		//config isn't used in KBase
		addGetDeleteNodeBasic(bsc);
	}

	private void addGetDeleteNodeBasic(BasicShockClient bsc)
			throws IOException, ShockHttpException,
			Exception {
		ShockNode sn = bsc.addNode(new ByteArrayInputStream("a".getBytes()), 1, "f", null);
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
