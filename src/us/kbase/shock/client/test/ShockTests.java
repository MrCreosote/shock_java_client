package us.kbase.shock.client.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gc.iotools.stream.is.InputStreamFromOutputStream;
import com.gc.iotools.stream.os.OutputStreamToInputStream;
import com.github.zafarkhaja.semver.Version;

import us.kbase.auth.AuthToken;
import us.kbase.common.test.TestException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockACL;
import us.kbase.shock.client.ShockACLType;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.ShockUserId;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockAuthorizationException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockIllegalShareException;
import us.kbase.shock.client.exceptions.ShockIllegalUnshareException;
import us.kbase.shock.client.exceptions.ShockNoNodeException;
import us.kbase.shock.client.exceptions.ShockNodeDeletedException;
import us.kbase.shock.client.test.controllers.blobstore.BlobstoreController;
import us.kbase.shock.client.test.controllers.minio.MinioController;
import us.kbase.test.auth2.authcontroller.AuthController;

public class ShockTests {
	
	private static BasicShockClient BSC1;
	private static BasicShockClient BSC2;
	private static String USER1 = "user1";
	private static String USER2 = "user2";
	
	private static ShockUserId USER1_SID;
	private static ShockUserId USER2_SID;
	
	private static MongoController MONGO;
	private static MinioController MINIO;
	private static BlobstoreController BLOB;
	private static AuthController AUTH;
	
	private static Version VERSION;

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
		
		// set up auth
		AUTH = new AuthController(
				ShockTestCommon.getJarsDir(),
				"localhost:" + MONGO.getServerPort(),
				"test_" + ShockTests.class.getSimpleName(),
				Paths.get(ShockTestCommon.getTempDir()));
		final URL authURL = new URL("http://localhost:" + AUTH.getServerPort() + "/testmode");
		System.out.println("started auth server at " + authURL);
		ShockTestCommon.createAuthUser(authURL, USER1, "display1");
		final String token1 = ShockTestCommon.createLoginToken(authURL, USER1);
		ShockTestCommon.createAuthUser(authURL, USER2, "display2");
		final String token2 = ShockTestCommon.createLoginToken(authURL, USER2);
		final AuthToken t1 = new AuthToken(token1, USER1);
		final AuthToken t2 = new AuthToken(token2, USER2);
		
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
		
		final URL url = new URL("http://localhost:" + BLOB.getPort());
		System.out.println("Testing shock clients pointed at: " + url);
		
		try {
			BSC1 = new BasicShockClient(url, t1);
			BSC2 = new BasicShockClient(url, t2);
		} catch (IOException ioe) {
			throw new TestException("Couldn't set up shock client: " +
					ioe.getLocalizedMessage());
		}
		VERSION = Version.valueOf(BSC1.getShockVersion());
		System.out.println("Set up shock clients for Shock version " +
				BSC1.getShockVersion());
		USER1_SID = BSC1.addNode(getIS(), 1, "f", null).getACLs().getOwner();
		USER2_SID = BSC2.addNode(getIS(), 1, "f", null).getACLs().getOwner();
	}
	
	private static InputStream getIS() {
		return new ByteArrayInputStream("a".getBytes());
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
	public void setNullToken() throws Exception {
		AuthToken current = BSC1.getToken();
		BSC1.updateToken(null);
		try {
			BSC1.addNode(getIS(), 1, "f", null);
			fail("Added node with no token");
		} catch (ShockAuthorizationException sae) {
			assertThat("correct exception message", sae.getLocalizedMessage(),
					is("No Authorization"));
			assertThat("correct code", sae.getHttpCode(), is(401));
		}
		assertThat("token is null", BSC1.getToken(), is((AuthToken) null));
		BSC1.updateToken(current);
	}
	

	@Test
	public void shockUrl() throws Exception {
		URL url = BSC1.getShockUrl();
		BasicShockClient b = new BasicShockClient(url); //will choke if bad url
		assertThat("url is preserved", b.getShockUrl().toString(), is(url.toString()));
		//Note using cdmi to test for cases where valid json is returned but
		//the id field != Shock. However, sometimes the cdmi server doesn't 
		//return an id, which I assume is a bug (see https://atlassian.kbase.us/browse/KBASE-200)
		//TODO TEST should just use a mock server for this stuff.
		String newurl = "https://kbase.us/services/shock-api/";
		List<String> badURLs = Arrays.asList("ftp://thing.us/",
			"http://google.com/", "http://kbase.us/services/cdmi_api/",
			"http://kbase.us/services/shock-api/node/9u8093481-1758175-157-15/",
			newurl + "foo/");
		for (String burl: badURLs) {
			try {
				new BasicShockClient(new URL(burl));
				fail("init'd client with bad url");
			} catch (InvalidShockUrlException isu) {}
		}
//		BasicShockClient b2 = new BasicShockClient(new URL(newurl + "foo/"));
//		assertThat("https url not preserved", b2.getShockUrl().toString(), is(newurl));
		String newurl2 = "https://kbase.us/services/shock-api";
		BasicShockClient b3 = new BasicShockClient(new URL(newurl2));
		assertThat("url w/o trailing slash fails", b3.getShockUrl().toString(),
				is(newurl2 + "/"));
		
	}

	
	@Test
	public void addGetDeleteNodeBasic() throws Exception {
		addGetDeleteNodeBasic(BSC1);
	}
	
	private void addGetDeleteNodeBasic(BasicShockClient bsc) throws Exception {
		ShockNode sn = bsc.addNode(getIS(), 1, "f", null);
		ShockNode snget = bsc.getNode(sn.getId());
		assertThat("get node != add Node output", snget.toString(), is(sn.toString()));
		bsc.deleteNode(sn.getId());
		getDeletedNode(sn.getId());
	}
	
	@Test
	public void getNodeBadId() throws Exception {
		try {
			BSC1.getNode(new ShockNodeId("00000000-0000-0000-0000-000000000000"));
			fail("got node with bad id");
		} catch (ShockNoNodeException snne) {
			assertThat("Bad exception message", snne.getLocalizedMessage(),
					is("Node not found"));
		}
		try {
			BSC1.getNode(null);
			fail("got node with bad id");
		} catch (NullPointerException npe) {
			assertThat("Bad exception message", npe.getLocalizedMessage(),
					is("id may not be null"));
		}
	}
	
	private void getDeletedNode(ShockNodeId id) throws Exception {
		try {
			BSC1.getNode(id);
			fail("Able to retrieve deleted node");
		} catch (ShockNoNodeException snne) {
			assertThat("Bad exception message", snne.getLocalizedMessage(),
					is("Node not found"));
		}
	}
	
	@Test
	public void deleteByNode() throws Exception {
		ShockNode sn = BSC1.addNode(getIS(), 1, "f", null);
		ShockNodeId id = sn.getId();
		sn.delete();
		getDeletedNode(id);
		try {
			sn.delete();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getFile(new ByteArrayOutputStream());
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getFile();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getFileInformation();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getFormat();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getId();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.setPubliclyReadable(true);
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
	}
	
	@Test
	public void getNodeWithFile() throws Exception {
		String content = "Been shopping? No, I've been shopping";
		String name = "apistonengine.recipe";
		ShockNode sn = addNode(BSC1, content, 37, name, null);
		testFile(content, name, null, sn);
		BSC1.deleteNode(sn.getId());
	}
	
	@Test
	public void getNodeWithFileAndWhitespaceFormat() throws Exception {
		String content = "Been shopping? No, I've been shopping";
		String name = "apistonengine.recipe";
		ShockNode sn = addNode(BSC1, content, 37, name, "     \t   ");
		testFile(content, name, null, sn);
		BSC1.deleteNode(sn.getId());
	}
	
	@Test
	public void getNodeWithFileAndFormat() throws Exception {
		String content = "Like the downy growth on the upper lip of a mediterranean girl";
		String name = "bydemagogueryImeandemagoguery";
		ShockNode sn = addNode(BSC1, content, 62, name, "UTF-8");
		testFile(content, name, "UTF-8", sn);
		sn.delete();
	}
	
	private ShockNode addNode(
			final BasicShockClient bsc,
			final String content,
			final long contentBytes,
			final String name,
			final String format)
			throws Exception {
		return bsc.addNode(new ReaderInputStream(new StringReader(content)),
				contentBytes, name, format);
	}
	
//	@Ignore
	@Test
	public void threaded() throws Exception {
		//this test either hangs forever or throws an exception in commit
		//c8aa276
		SaveAndGetThread[] threads = new SaveAndGetThread[10];
		for (int i = 0; i < threads.length; i++) {
			threads[i] = new SaveAndGetThread("aaaaabbbbbc", 1000000, i);
		}

		// start the threads
		for (int j = 0; j < threads.length; j++) {
//			System.out.println("ID " + j + " starting thread.");
			threads[j].start();
//			System.out.println("\tID " + j + " started.");
		}

		// join the threads
		for (int j = 0; j < threads.length; j++) {
//			System.out.println("ID " + j + " joining thread ");
			threads[j].join();
			if (threads[j].e != null) {
				throw threads[j].e;
			}
//			System.out.println("\tID " + j + " joined.");
		}
		
	}
	
	class SaveAndGetThread extends Thread {
		
		private final String s;
		private final int count;
		private final int id;
		public Exception e = null;
		
		public SaveAndGetThread(String s, int count, int id) {
			this.s = s;
			this.count = count;
			this.id = id;
		}
		
		
		@Override
		public void run() {
			try {
				ShockNode sn = writeFileToNode(s, count, "", "" + id, "JSON", id);
				verifyStreamedNode(sn, s, count, "", "" + id, "JSON", id);
				sn.delete();
			} catch (Exception e) {
				this.e = e;
			}
		}
	}
	
	public static void assertExceptionCorrect(
			final Exception got,
			final Exception expected) {
		assertThat("incorrect exception. trace:\n" +
				ExceptionUtils.getStackTrace(got),
				got.getLocalizedMessage(),
				is(expected.getLocalizedMessage()));
		assertThat("incorrect exception type", got, is(expected.getClass()));
	}
	

	@Test
	public void getFileViaInputStreamByteRead() throws Exception {
		/* test reading files one byte at a time, which is not exercised by
		 * the other tests.
		 */
		
		// won't get new chunk
		ShockNode sn = writeFileToNode("aaaaaaaaaa", 4999999,
				"abcdefghij", "foo", "UTF-8", 1);
		InputStream is = sn.getFile();
		new DataInputStream(is).readFully(new byte[49999990]);
		int offset = 97; //a in ascii
		for (int i = 0; i < 10; i++) {
			assertThat("incorrect read", is.read(), is(i + offset));
		}
		assertThat("incorrect read", is.read(), is(-1));
		
		// will get new 1 byte chunk
		sn = writeFileToNode("aaaaaaaaaa", 4999999,
				"abcdefghijk", "foo", "UTF-8", 1);
		is = sn.getFile();
		new DataInputStream(is).readFully(new byte[49999990]);
		for (int i = 0; i < 11; i++) {
			assertThat("incorrect read", is.read(), is(i + offset));
		}
		assertThat("incorrect read", is.read(), is(-1));
		
		is = sn.getFile();
		is.close();
		try {
			is.read();
			fail("Read successfully but expected fail");
		} catch (IOException e) {
			assertThat("incorrect exception message", e.getMessage(),
					is("Attempted read on closed stream."));
		}
	}
	
	@Test
	public void saveAndGetStreamingFiles() throws Exception {
		int chunksize = 50000000;
		StringBuilder sb = new StringBuilder();
		sb.append("abcd");
		sb.appendCodePoint(0x0100);
		sb.appendCodePoint(0x20AC);
		Map<String, Object> attribs = new HashMap<String, Object>();
		attribs.put("foobar", "barbaz");
		String ch2 = new StringBuilder().appendCodePoint(0x0100).toString();
		String ch3 = new StringBuilder().appendCodePoint(0x20AC).toString();
		
		int length = 9; //length of sb
		
		long minwrites = chunksize / length;
		int rem = chunksize % length;
		if (minwrites == chunksize) {
			minwrites--;
			rem = length;
		}
		StringBuilder sbsmall = new StringBuilder();
		for (int i = 0; i < rem - 1; i++) {
			sbsmall.append("a");
		}
		String sbs = sbsmall.toString();
		String sbs2 = sbs + sbs;
		
		ShockNode sn = writeFileToNode(sb.toString(), minwrites, sbs, "filename", "JSON", 1);
		verifyStreamedNode(sn, sb.toString(), minwrites, sbs, "filename", "JSON", 1);
		sn.delete();
		
		sn = writeFileToNode(sb.toString(), minwrites, sbs + "~", "a", null, 2);
		verifyStreamedNode(sn, sb.toString(), minwrites, sbs + "~", "a", null, 2);
		sn.delete();
		
		sn = writeFileToNode(sb.toString(), minwrites, sbs + ch2, "b", "", 3);
		verifyStreamedNode(sn, sb.toString(), minwrites, sbs + ch2, "b", null, 3);
		sn.delete();
		
		sn = writeFileToNode(sb.toString(), minwrites * 2, sbs2 + "j", "myfile", "", 4);
		verifyStreamedNode(sn, sb.toString(), minwrites * 2, sbs2 + "j", "myfile", null, 4);
		sn.delete();
		
		sn = writeFileToNode(sb.toString(), minwrites * 2, sbs2 + ch2, "somefile", "UTF-8", 5);
		verifyStreamedNode(sn, sb.toString(), minwrites * 2, sbs2 + ch2, "somefile", "UTF-8", 5);
		sn.delete();
		
		sn = writeFileToNode(sb.toString(), minwrites * 2, sbs2 + ch3, "whee", "ASCII", 6);
		verifyStreamedNode(sn, sb.toString(), minwrites * 2, sbs2 + ch3, "whee", "ASCII", 6);
		sn.delete();
	}
	
	private void verifyStreamedNode(
			final ShockNode sn,
			final String string,
			final long writes,
			final String last,
			final String filename,
			final String format,
			final int id)
			throws Exception {
		final int readlen = string.getBytes(StandardCharsets.UTF_8).length;
		final int finallen = last.getBytes(StandardCharsets.UTF_8).length;
		final long filesize = readlen * writes + finallen;
		assertThat("filesize correct", sn.getFileInformation().getSize(), is(filesize));
		assertThat("filename correct", sn.getFileInformation().getName(),
				is(filename));
		assertThat("format correct", sn.getFormat(), is(format));
		System.out.println("ID " + id + " Verifying " + filesize + "b file via outputstream... ");

		OutputStreamToInputStream<String> osis =
				new OutputStreamToInputStream<String>() {
					
			@Override
			protected String doRead(InputStream is) throws Exception {
				verifyStreamedNode(sn, string, writes, last, is);
				return null;
			}


		};
		BSC1.getFile(sn, osis);
		osis.close();
		System.out.println("\tID " + id + " Verifying done.");
		
		System.out.println("ID " + id + " Verifying " + filesize + "b file via inputstream... ");
		InputStream is = BSC1.getFile(sn);
		verifyStreamedNode(sn, string, writes, last, is);
		is.close();
	}
	
	private void verifyStreamedNode(
			final ShockNode sn,
			final String string,
			final long writes,
			final String last,
			final InputStream is)
			throws IOException {
		final int readlen = string.getBytes(StandardCharsets.UTF_8).length;
		final int finallen = last.getBytes(StandardCharsets.UTF_8).length;
		final long filesize = readlen * writes + finallen;
		byte[] data = new byte[readlen];
		int read = read(is, data);
		long size = 0;
		long reads = 1;
		while (reads <= writes) {
			assertThat("file incorrect at pos " + size, 
					new String(data, StandardCharsets.UTF_8),
					is(string));
			size += read;
			assertThat("read size correct", read, is(readlen));
			reads++;
			read = read(is, data);
		}
		byte[] finaldata = new byte[finallen];
		int read2 = read(is, finaldata);
		assertThat("correct length of final string for node "
				+ sn.getId().getId(), read + read2, is(finallen));
		byte[] lastgot = new byte[read + read2];
		System.arraycopy(data, 0, lastgot, 0, read);
		System.arraycopy(finaldata, 0, lastgot, read, read2);
		if (finallen > 0) {
			final String l = new String(lastgot, StandardCharsets.UTF_8);
			assertThat("file incorrect at last pos " + size, l, is(last));
			size += read + read2;
		}
		data = new byte[1];
		if (is.read(data) > 0) {
			fail("file is too long");
		}
		assertThat("correct file size for node " + sn.getId().getId(),
				size, is(filesize));
	}
	
	private int read(final InputStream file, final byte[] b)
			throws IOException {
		int pos = 0;
		while (pos < b.length) {
			final int read = file.read(b, pos, b.length - pos);
			if (read == -1) {
				break;
			}
			pos += read;
		}
		return pos;
	}

	private ShockNode writeFileToNode(
			final String string,
			final long writes,
			final String last,
			final String filename,
			final String format,
			final int id)
			throws Exception {
		final int readlen = string.getBytes(StandardCharsets.UTF_8).length;
		final int finallen = last.getBytes(StandardCharsets.UTF_8).length;
		final long filesize = readlen * writes + finallen;
		
		InputStreamFromOutputStream<String> isos =
				new InputStreamFromOutputStream<String>() {
			
			@Override
			public String produce(final OutputStream dataSink)
					throws Exception {
				Writer writer = new OutputStreamWriter(dataSink,
						StandardCharsets.UTF_8);
				for (int i = 0; i < writes - 1; i++) {
					writer.write(string);
				}
				writer.write(string + last);
//				System.out.println("ID " + id + " finished writes.");
				writer.flush();
//				writer.close();
//				dataSink.flush();
//				dataSink.close();
//				System.out.println("ID " + id + " closed the output stream.");
				return null;
			}
		};
		System.out.println("ID " + id + " Streaming " + filesize + "b file... ");
		ShockNode sn = BSC1.addNode(isos, filesize, filename, format);
		isos.close();
		System.out.println("\tID " + id + " Streaming done.");
		return sn;
	}

	@Test
	public void saveAndGetNodeWith4GBFile() throws Exception {
		StringBuilder sb = new StringBuilder();
		sb.append("abcd");
		sb.appendCodePoint(0x20AC);
		
		StringBuilder last = new StringBuilder();
		last.appendCodePoint(0x10310);
		
		final long writes = 571428571;
		ShockNode sn = writeFileToNode(sb.toString(), writes, last.toString(), "somefile", "JSON", 1);
		verifyStreamedNode(sn, sb.toString(), writes, last.toString(), "somefile", "JSON", 1);
		BSC1.deleteNode(sn.getId());
	}
	
	private void testFile(String content, String name, String format, ShockNode sn)
			throws Exception {
		ShockNode snget = BSC1.getNode(sn.getId());
		String utf8 = StandardCharsets.UTF_8.name();
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		BSC1.getFile(sn, bos);
		String filecon1 = bos.toString(utf8);
		
		bos = new ByteArrayOutputStream();
		BSC1.getFile(sn.getId(), bos);
		String filecon2 = bos.toString(utf8);
		
		bos = new ByteArrayOutputStream();
		snget.getFile(bos);
		String filefromnode = bos.toString(utf8);
		
		 
		String filecon1is = IOUtils.toString(BSC1.getFile(sn), utf8);
		String filecon2is = IOUtils.toString(BSC1.getFile(sn.getId()), utf8);
		String filefromnodeIs = IOUtils.toString(sn.getFile(), utf8);
		
		Set<String> digestTypes = snget.getFileInformation().getChecksumTypes();
		assertTrue("has md5", digestTypes.contains("md5"));
		assertThat("unequal md5", snget.getFileInformation().getChecksum("md5"),
				is(DigestUtils.md5Hex(content)));
		try {
			snget.getFileInformation().getChecksum(
					"this is not a checksum type");
			fail("got checksum type that doesn't exist");
		} catch (IllegalArgumentException iae) {
			assertThat("exception string incorrect", 
					"java.lang.IllegalArgumentException: No such checksum type: this is not a checksum type",
					is(iae.toString()));
		}
		assertThat("file from node != file from client", filefromnode,
				is(filecon1));
		assertThat("files from the 2 polymorphic getFile() methods different",
				filecon1, is(filecon2));
		assertThat("files from node w/ input & outputstreams differ",
				filefromnode, is(filefromnodeIs));
		assertThat("inpustream: file from node != file from client",
				filefromnodeIs, is(filecon1is));
		assertThat("inputstream: files from the 2 polymorphic getFile() methods different",
				filecon1is, is(filecon2is));
		assertThat("file content unequal", filecon1, is(content));
		assertThat("file name unequal", snget.getFileInformation().getName(),
				is(name));
		assertThat("file format unequal", snget.getFormat(),
				is(format));
		assertThat("file size wrong", snget.getFileInformation().getSize(),
				is((long) content.length()));
	}
	
	@Test
	public void invalidFileRequest() throws Exception {
		ShockNode sn = BSC1.addNode(new ByteArrayInputStream("a".getBytes()), 1, "f", null);
		try {
			sn.getFile(null);
			fail("called get file w/ null arg");
		} catch (NullPointerException ioe) {
			assertThat("no file exc string incorrect", ioe.getLocalizedMessage(), 
					is("os"));
		}
		try {
			BSC1.getFile((ShockNode) null, new ByteArrayOutputStream());
			fail("called get file w/ null arg");
		} catch (NullPointerException iae) {
			assertThat("no file exc string incorrect", iae.getLocalizedMessage(), 
					is("sn"));
		}
		try {
			BSC1.getFile((ShockNode) null);
			fail("called get file w/ null arg");
		} catch (NullPointerException iae) {
			assertThat("no file exc string incorrect", iae.getLocalizedMessage(), 
					is("sn"));
		}
		try {
			BSC1.getFile((ShockNodeId) null, new ByteArrayOutputStream());
			fail("called get file w/ null arg");
		} catch (NullPointerException npe) {
			assertThat("no file exc string incorrect", npe.getLocalizedMessage(), 
					is("id may not be null"));
		}
		try {
			BSC1.getFile((ShockNodeId) null);
			fail("called get file w/ null arg");
		} catch (NullPointerException npe) {
			assertThat("no file exc string incorrect", npe.getLocalizedMessage(), 
					is("id may not be null"));
		}
		try {
			BSC1.getFile(sn, null);
			fail("called get file w/ null arg");
		} catch (NullPointerException ioe) {
			assertThat("no file exc string incorrect", ioe.getLocalizedMessage(), 
					is("os"));
		}
		sn.delete();
		
		sn = addNode(BSC1, "Been shopping? No, I've been shopping", 37, "filename",
				null);
		BSC1.deleteNode(sn.getId());
		try {
			BSC1.getFile(sn, new ByteArrayOutputStream());
			fail("got deleted file");
		} catch (ShockNoNodeException snne) {
			assertThat("correct exception message", snne.getLocalizedMessage(),
					is("Node not found"));
		}
		try {
			BSC1.getFile(sn);
			fail("got deleted file");
		} catch (ShockNoNodeException snne) {
			assertThat("correct exception message", snne.getLocalizedMessage(),
					is("Node not found"));
		}
	}
	
	@Test
	public void addNodeNulls() throws Exception {
		try {
			BSC1.addNode(null, 1, "foo", "foo");
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("file may not be null"));
		}
		try {
			addNode(BSC1, "foo", 1, null, "foo");
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("filename may not be null or empty"));
		}
		try {
			addNode(BSC1, "foo", 1, "", "foo");
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("filename may not be null or empty"));
		}
	}
	
	@Test
	public void addNodeFailNegativeSize() throws Exception {
		try {
			BSC1.addNode(getIS(), -1, "foo", "foo");
			fail("called addNode with negative size");
		} catch (IllegalArgumentException e) {
			assertThat("exception message incorrect", e.getMessage(),
					is("fileLength may not be negative"));
		}
	}
	
	@SuppressWarnings("unlikely-arg-type")
	@Test
	public void ids() throws Exception {
		ShockNodeId id1 = new ShockNodeId("cbf19927-1e04-456c-b2c3-812edd90fa68");
		ShockNodeId id2 = new ShockNodeId("cbf19927-1e04-456c-b2c3-812edd90fa68");
		assertTrue("id equality failed", id1.equals(id1));
		assertTrue("id state failed", id1.equals(id2));
		assertFalse("non id equal to id", id1.equals(Collections.emptyList()));
		
		
		List<String> badUUIDs = Arrays.asList("cbf19927a1e04-456c-b2c3-812edd90fa68",
				"cbf19927-1e04-456c1-b2c3-812edd90fa68", "acbf19927-1e04-456c-b2c3-812edd90fa68",
				"cbf19927-1e04-456c-b2c3-812gdd90fa68");
		for (String uuid: badUUIDs) {
			try {
				new ShockNodeId(uuid);
				fail("Node id accepted invalid id string " + uuid);
			} catch (IllegalArgumentException iae) {
				assertThat("Bad exception message", iae.getLocalizedMessage(),
						is("id must be a UUID hex string"));
			}
		}
	}
	
	@Test
	public void copyNode() throws Exception {
		String content = "Been\n shopping? No,\n I've been shopping";
		String name = "apistonengine.recipe";
		final ShockNode sn = addNode(BSC1, content, 39, name, "text");
		sn.addToNodeAcl(Arrays.asList(USER2), ShockACLType.READ);
		sn.addToNodeAcl(Arrays.asList(USER2), ShockACLType.WRITE);
		sn.addToNodeAcl(Arrays.asList(USER2), ShockACLType.DELETE);
		sn.setPubliclyReadable(true);
		final ShockNode copy = BSC2.copyNode(sn.getId(), false);
		assertThat("nodes are the same", sn.getId().equals(copy.getId()), is(false));
		final ShockACL acl = copy.getACLs();
		assertThat("correct owner", acl.getOwner(), is(USER2_SID));
		assertThat("correct writers", acl.getWrite(), is(Arrays.asList(USER2_SID)));
		assertThat("correct delete", acl.getDelete(), is(Arrays.asList(USER2_SID)));
		assertThat("correct read", acl.getRead(), is(Arrays.asList(USER2_SID)));
		assertThat("correct pub", acl.isPublicallyReadable(), is(false));
		
		copy.addToNodeAcl(Arrays.asList(USER1), ShockACLType.READ);
		testFile(content, name, "text", copy);
		BSC1.deleteNode(sn.getId());
		BSC2.deleteNode(copy.getId());
	}
	
	@Test
	public void copyNodeNoAttribsAndNotAlreadyOwned() throws Exception {
		// tests the case where the user requests no copy if the node is owned by the user,
		// but it isn't.
		// don't repeat tests from copyNode()
		String content = "Been\n shopping? No,\n I've been shopping";
		String name = "apistonengine.recipe";
		final ShockNode sn = addNode(BSC1, content, 39, name, "text");
		sn.addToNodeAcl(Arrays.asList(USER2), ShockACLType.READ);
		final ShockNode copy = BSC2.copyNode(sn.getId(), true);
		assertThat("nodes are the same", sn.getId().equals(copy.getId()), is(false));
		final ShockACL acl = copy.getACLs();
		assertThat("correct owner", acl.getOwner(), is(USER2_SID));
		assertThat("correct writers", acl.getWrite(), is(Arrays.asList(USER2_SID)));
		assertThat("correct delete", acl.getDelete(), is(Arrays.asList(USER2_SID)));
		assertThat("correct read", acl.getRead(), is(Arrays.asList(USER2_SID)));
		assertThat("correct pub", acl.isPublicallyReadable(), is(false));
		
		copy.addToNodeAcl(Arrays.asList(USER1), ShockACLType.READ);
		testFile(content, name, "text", copy);
		BSC1.deleteNode(sn.getId());
		BSC2.deleteNode(copy.getId());
	}
	
	@Test
	public void copyNodeNoAttribsAndAlreadyOwned() throws Exception {
		// don't repeat tests from copyNode()
		String content = "Been\n shopping? No,\n I've been shopping";
		String name = "apistonengine.recipe";
		final ShockNode sn = addNode(BSC1, content, 39, name, "text");
		final ShockNode copy = BSC1.copyNode(sn.getId(), true);
		assertThat("nodes are the same", sn.getId().equals(copy.getId()), is(true));
		final ShockACL acl = copy.getACLs();
		assertThat("correct owner", acl.getOwner(), is(USER1_SID));
		assertThat("correct writers", acl.getWrite(), is(Arrays.asList(USER1_SID)));
		assertThat("correct delete", acl.getDelete(), is(Arrays.asList(USER1_SID)));
		assertThat("correct read", acl.getRead(), is(Arrays.asList(USER1_SID)));
		assertThat("correct pub", acl.isPublicallyReadable(), is(false));
		
		testFile(content, name, "text", copy);
		BSC1.deleteNode(sn.getId());
	}

	@Test
	public void copyNodeFail() throws Exception {
		final ShockNode sn = BSC1.addNode(getIS(), 1, "f", null);
		
		copyNodeFail(BSC1, new ShockNodeId(UUID.randomUUID().toString()),
				new ShockNoNodeException(400, "Node not found"));
		
		copyNodeFail(BSC2, sn.getId(), new ShockAuthorizationException(401, "User Unauthorized"));
	}
	
	private void copyNodeFail(
			final BasicShockClient client,
			final ShockNodeId id,
			final Exception expected) {
		try {
			client.copyNode(id, false);
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, expected);
		}
	}
	
	private void failSetPubliclyReadable(
			final ShockNode node,
			final boolean read,
			final Exception exp) throws Exception {
		try {
			node.setPubliclyReadable(read);
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, exp);
		}
	}
	
	private void failSetPubliclyReadable(
			final BasicShockClient cli,
			final ShockNodeId id,
			final boolean read,
			final Exception exp) throws Exception {
		try {
			cli.setPubliclyReadable(id, read);
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, exp);
		}
	}
	
	@Test
	public void modifyAcls() throws Exception {
		ShockNode sn = setUpNodeAndCheckAuth(BSC1, BSC2);
		ShockACL acls = sn.getACLs();
		assertThat("user1 is owner", acls.getOwner(), is(USER1_SID));
		assertThat("only owner in read acl", acls.getRead(),
				is(Arrays.asList(USER1_SID)));
		assertThat("only owner in write acl", acls.getWrite(),
				is(Arrays.asList(USER1_SID)));
		assertThat("only owner in delete acl", acls.getDelete(),
				is(Arrays.asList(USER1_SID)));
		assertThat("node is private", acls.isPublicallyReadable(), is(false));
		failSetPubliclyReadable(BSC1, null, true,
				new NullPointerException("id"));
		ShockACL pacl = BSC1.setPubliclyReadable(sn.getId(), true);
		assertThat("node set readable", pacl.isPublicallyReadable(), is(true));
		assertThat("node set readable", BSC1.getACLs(sn.getId())
				.isPublicallyReadable(), is(true));
		assertThat("node set readable", sn.getACLs().isPublicallyReadable(),
				is(true));
		
		pacl = sn.setPubliclyReadable(false);
		assertThat("node set private", pacl.isPublicallyReadable(), is(false));
		assertThat("node set private", BSC1.getACLs(sn.getId())
				.isPublicallyReadable(), is(false));
		assertThat("node set private", sn.getACLs().isPublicallyReadable(),
				is(false));
		
		final List<ShockACLType> singleAcls = Arrays.asList(ShockACLType.READ,
				ShockACLType.WRITE, ShockACLType.DELETE);
		for (final ShockACLType aclType: singleAcls) {
			failAddAcl(BSC1, null, Arrays.asList(USER2), aclType,
					new NullPointerException("id cannot be null"));
			
			failAddAcl(BSC1, sn.getId(), Arrays.asList((String) null), aclType,
					new IllegalArgumentException("user cannot be null or the empty string"));
			failAddAcl(sn, Arrays.asList((String) null), aclType,
					new IllegalArgumentException("user cannot be null or the empty string"));
			
			failAddAcl(BSC1, sn.getId(), Arrays.asList(""), aclType,
					new IllegalArgumentException("user cannot be null or the empty string"));
			failAddAcl(sn, Arrays.asList(""), aclType,
					new IllegalArgumentException("user cannot be null or the empty string"));
			
			failAddAcl(BSC1, sn.getId(), null, aclType,
					new IllegalArgumentException("user list cannot be null or empty"));
			failAddAcl(sn, null, aclType,
					new IllegalArgumentException("user list cannot be null or empty"));
			
			failAddAcl(BSC1, sn.getId(), new LinkedList<String>(), aclType,
					new IllegalArgumentException("user list cannot be null or empty"));
			failAddAcl(sn, new LinkedList<String>(), aclType,
					new IllegalArgumentException("user list cannot be null or empty"));
			
			failAddAcl(BSC1, sn.getId(), Arrays.asList(USER2), null,
					new NullPointerException("aclType cannot be null"));
			failAddAcl(sn, Arrays.asList(USER2), null,
					new NullPointerException("aclType cannot be null"));
			
			failRemoveAcl(BSC1, null, Arrays.asList(USER2), aclType,
					new NullPointerException("id cannot be null"));
			
			failRemoveAcl(BSC1, sn.getId(), Arrays.asList((String) null), aclType,
					new IllegalArgumentException("user cannot be null or the empty string"));
			failRemoveAcl(sn, Arrays.asList((String) null), aclType,
					new IllegalArgumentException("user cannot be null or the empty string"));
			
			failRemoveAcl(BSC1, sn.getId(), Arrays.asList(""), aclType,
					new IllegalArgumentException("user cannot be null or the empty string"));
			failRemoveAcl(sn, Arrays.asList(""), aclType,
					new IllegalArgumentException("user cannot be null or the empty string"));
			
			failRemoveAcl(BSC1, sn.getId(), null, aclType,
					new IllegalArgumentException("user list cannot be null or empty"));
			failRemoveAcl(sn, null, aclType,
					new IllegalArgumentException("user list cannot be null or empty"));
			
			failRemoveAcl(BSC1, sn.getId(), new LinkedList<String>(), aclType,
					new IllegalArgumentException("user list cannot be null or empty"));
			failRemoveAcl(sn, new LinkedList<String>(), aclType,
					new IllegalArgumentException("user list cannot be null or empty"));
			
			failRemoveAcl(BSC1, sn.getId(), Arrays.asList(USER2), null,
					new NullPointerException("aclType cannot be null"));
			failRemoveAcl(sn, Arrays.asList(USER2), null,
					new NullPointerException("aclType cannot be null"));

			String acl = aclType.getType() + " acl";
			final List<ShockUserId> expectedAddedUsers = new LinkedList<>();
			expectedAddedUsers.add(USER1_SID);
			if (aclType == ShockACLType.READ) {
				// the blobstore silently ignores write and delete acl requests
				expectedAddedUsers.add(USER2_SID);
			}
			
			ShockACL retacl = BSC1.addToNodeAcl(sn.getId(),
					Arrays.asList(USER2), aclType);
			assertThat("added user to " + acl, getAcls(retacl, aclType),
					is(expectedAddedUsers));
			assertThat("added user to " + acl, getAcls(BSC1, sn.getId(), aclType),
					is(expectedAddedUsers));
			assertThat("node is private", retacl.isPublicallyReadable(),
					is(false));
			
			retacl = BSC1.removeFromNodeAcl(sn.getId(),
					Arrays.asList(USER2), aclType);
			assertThat("removed user from " + acl, getAcls(retacl, aclType),
					is(Arrays.asList(USER1_SID)));
			assertThat("removed user from " + acl, getAcls(BSC1, sn.getId(), aclType),
					is(Arrays.asList(USER1_SID)));
			assertThat("node is private", retacl.isPublicallyReadable(),
					is(false));
			
			retacl = sn.addToNodeAcl(Arrays.asList(USER2), aclType);
			assertThat("added user to " + acl, getAcls(retacl, aclType),
					is(expectedAddedUsers));
			assertThat("added user to " + acl, getAcls(sn, aclType),
					is(expectedAddedUsers));
			assertThat("node is private", retacl.isPublicallyReadable(),
					is(false));
			
			retacl = sn.removeFromNodeAcl(Arrays.asList(USER2), aclType);
			assertThat("removed user from " + acl, getAcls(retacl, aclType),
					is(Arrays.asList(USER1_SID)));
			assertThat("removed user from " + acl, getAcls(sn, aclType),
					is(Arrays.asList(USER1_SID)));
			assertThat("node is private", retacl.isPublicallyReadable(),
					is(false));
		}
		
		
		failAddAcl(sn, Arrays.asList(BSC1.getToken().getUserName(),
				USER2), ShockACLType.OWNER,
				new ShockIllegalShareException(400,
						"Too many users. Nodes may have only one owner."));
		failAddAcl(BSC1, sn.getId(), Arrays.asList(
				BSC1.getToken().getUserName(), USER2),
				ShockACLType.OWNER,
				new ShockIllegalShareException(400,
						"Too many users. Nodes may have only one owner."));
		failRemoveAcl(BSC1, sn.getId(), Arrays.asList(USER2),
				ShockACLType.OWNER,
				new ShockIllegalUnshareException(400,
						"Deleting ownership is not a supported request type."));
		failRemoveAcl(sn, Arrays.asList(USER2), ShockACLType.OWNER,
				new ShockIllegalUnshareException(400,
						"Deleting ownership is not a supported request type."));
		
		String owneracl = ShockACLType.OWNER.getType() + " acl";
		ShockACL retacl = BSC1.addToNodeAcl(sn.getId(),
				Arrays.asList(USER2), ShockACLType.OWNER);
		assertThat("added user to " + owneracl, retacl.getOwner(),
				is(USER2_SID));
		assertThat("added user to " + owneracl,
				BSC2.getACLs(sn.getId()).getOwner(), is(USER2_SID));
		assertThat("node is private", retacl.isPublicallyReadable(),
				is(false));
		
		assertThat("username correct", retacl.getOwner().getUsername(),
				is(USER2_SID.getUsername()));
		assertThat("username correct", retacl.getOwner().getUsername(),
				is(USER2));
		assertThat("user id correct", retacl.getOwner().getID(),
				is(USER2_SID.getID()));
		ShockNode sn2 = BSC2.getNode(sn.getId());
		retacl = sn2.addToNodeAcl(Arrays.asList(BSC1.getToken().getUserName()),
				ShockACLType.OWNER);
		assertThat("node is private", retacl.isPublicallyReadable(),
				is(false));
		assertThat("added user to " + owneracl, retacl.getOwner(),
				is(USER1_SID));
		assertThat("added user to " + owneracl, sn.getACLs().getOwner(),
				is(USER1_SID));
		sn.removeFromNodeAcl(Arrays.asList(USER2),
				ShockACLType.READ);
		
		// blobstore doesn't accept the `all` acl
		failAddAcl(BSC1, sn.getId(), Arrays.asList(USER2), ShockACLType.ALL,
				new ShockHttpException(400, "Invalid acl type"));
		failRemoveAcl(BSC1, sn.getId(), Arrays.asList(USER2), ShockACLType.ALL,
				new ShockHttpException(400, "Invalid acl type"));
		failAddAcl(sn, Arrays.asList(USER2), ShockACLType.ALL,
				new ShockHttpException(400, "Invalid acl type"));
		failRemoveAcl(sn, Arrays.asList(USER2), ShockACLType.ALL,
				new ShockHttpException(400, "Invalid acl type"));
		
		String failAddErr =
				"Users that are not node owners can only delete themselves from ACLs.";
		failAddAcl(BSC2, sn.getId(), Arrays.asList(USER2),
				ShockACLType.READ,
				new ShockIllegalShareException(400, failAddErr));
		failAddAcl(sn2, Arrays.asList(USER2), ShockACLType.READ,
				new ShockIllegalShareException(400, failAddErr));
		failSetPubliclyReadable(BSC2, sn.getId(), true,
				new ShockIllegalShareException(400, failAddErr));
		failSetPubliclyReadable(sn2, true,
				new ShockIllegalShareException(400, failAddErr));
		
		// test removing self from ACLs
		sn.addToNodeAcl(Arrays.asList(USER2), ShockACLType.READ);
		final ShockACLType acltype = ShockACLType.READ;
		ShockACL newacl = BSC2.removeFromNodeAcl(sn.getId(),
				Arrays.asList(USER2), acltype);
		assertThat("removed user from " + acltype.getType() + " acl",
				getAcls(newacl, acltype),
				is(Arrays.asList(USER1_SID)));
		assertThat("removed user from " + acltype.getType() + " acl",
				getAcls(BSC1, sn.getId(), acltype),
				is(Arrays.asList(USER1_SID)));
		
		ShockNodeId id = sn.getId();
		sn.delete();
		failAddAcl(BSC1, id, Arrays.asList(USER2),
				ShockACLType.READ, new ShockNoNodeException(
						404, "Node not found"));
		failSetPubliclyReadable(BSC1, id, false, new ShockNoNodeException(
				404, "Node not found"));
		
	}
	
	@Test
	public void getACLsWithReadPermission() throws Exception {
		final ShockNode sn = BSC1.addNode(getIS(), 1, "f", null);
		failGetACLS(BSC2, sn.getId(), new ShockAuthorizationException(401, "User Unauthorized"));
		
		sn.addToNodeAcl(Arrays.asList(USER2), ShockACLType.READ);
		final ShockACL acl = BSC2.getACLs(sn.getId());
		assertThat("correct owner", acl.getOwner(), is(USER1_SID));
		assertThat("correct writers", acl.getWrite(), is(Arrays.asList(USER1_SID)));
		assertThat("correct delete", acl.getDelete(), is(Arrays.asList(USER1_SID)));
		assertThat("correct read", acl.getRead(), is(Arrays.asList(USER1_SID, USER2_SID)));
		assertThat("correct pub", acl.isPublicallyReadable(), is(false));
	}
	
	private void failGetACLS(
			final BasicShockClient client,
			final ShockNodeId id,
			final Exception expected)
			throws Exception {
		try {
			client.getACLs(id);
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, expected);
		}
	}
	
	private List<ShockUserId> getAcls(ShockACL acl, ShockACLType type) {
		if (type.getType().equals("read")) {
			return acl.getRead();
		}
		if (type.getType().equals("write")) {
			return acl.getWrite();
		}
		if (type.getType().equals("delete")) {
			return acl.getDelete();
		}
		throw new RuntimeException("can't handle that acl type here");
	}

	private List<ShockUserId> getAcls(BasicShockClient cli, ShockNodeId id,
			ShockACLType type) throws Exception {
		if (type.getType().equals("read")) {
			return cli.getACLs(id).getRead();
		}
		if (type.getType().equals("write")) {
			return cli.getACLs(id).getWrite();
		}
		if (type.getType().equals("delete")) {
			return cli.getACLs(id).getDelete();
		}
		throw new RuntimeException("can't handle that acl type here");
	}
	
	private List<ShockUserId> getAcls(ShockNode sn,
			ShockACLType type) throws Exception {
		if (type.getType().equals("read")) {
			return sn.getACLs().getRead();
		}
		if (type.getType().equals("write")) {
			return sn.getACLs().getWrite();
		}
		if (type.getType().equals("delete")) {
			return sn.getACLs().getDelete();
		}
		throw new RuntimeException("can't handle that acl type here");
	}
	
	private void failAddAcl(BasicShockClient cli, ShockNodeId id,
			List<String> users, ShockACLType aclType, Exception e) throws Exception {
		try {
			cli.addToNodeAcl(id, users, aclType);
			fail("added to acl with bad args");
		} catch (Exception exp) {
			assertThat("correct exception type", exp, is(e.getClass()));
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getMessage()));
		}
	}
	
	private void failAddAcl(ShockNode sn, List<String> users,
			ShockACLType aclType, Exception e) throws Exception {
		try {
			sn.addToNodeAcl(users, aclType);
			fail("addded to acl with bad args");
		} catch (Exception exp) {
			assertThat("correct exception type", exp, is(e.getClass()));
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getMessage()));
		} 
	}
	
	private void failRemoveAcl(BasicShockClient cli, ShockNodeId id,
			List<String> users, ShockACLType aclType, Exception e) throws Exception {
		try {
			cli.removeFromNodeAcl(id, users, aclType);
			fail("removed from acl with bad args");
		} catch (Exception exp) {
			assertThat("correct exception type", exp, is(e.getClass()));
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getMessage()));
		}
	}
	
	private void failRemoveAcl(ShockNode sn, List<String> users,
			ShockACLType aclType, Exception e) throws Exception {
		try {
			sn.removeFromNodeAcl(users, aclType);
			fail("removed from acl with bad args");
		} catch (Exception exp) {
			assertThat("correct exception type", exp, is(e.getClass()));
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getMessage()));
		} 
	}
	
	
	private ShockNode setUpNodeAndCheckAuth(BasicShockClient source,
			BasicShockClient check)
			throws Exception {
		ShockNode sn = source.addNode(getIS(), 1, "f", null);
		try {
			check.getNode(sn.getId());
			fail("Node is readable with no permissions");
		} catch (ShockAuthorizationException aue) {
			assertThat("auth exception string is correct", aue.getLocalizedMessage(),
					is("User Unauthorized"));
			assertThat("correct code", aue.getHttpCode(), is(401));
		}
		return sn;
	}
	
	@Test
	public void getRemoteVersion() throws Exception {
		String v = BSC1.getRemoteVersion();
		assertThat("incorrect version", Version.valueOf(v), is(VERSION));
	}
}
