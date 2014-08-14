package us.kbase.shock.client.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gc.iotools.stream.is.InputStreamFromOutputStream;
import com.gc.iotools.stream.os.OutputStreamToInputStream;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthUser;
import us.kbase.auth.TokenExpiredException;
import us.kbase.common.test.TestException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.common.test.controllers.shock.ShockController;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockACL;
import us.kbase.shock.client.ShockACLType;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.ShockUserId;
import us.kbase.shock.client.ShockVersionStamp;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockAuthorizationException;
import us.kbase.shock.client.exceptions.ShockNoFileException;
import us.kbase.shock.client.exceptions.ShockNoNodeException;
import us.kbase.shock.client.exceptions.ShockNodeDeletedException;

public class ShockTests {
	
	private static BasicShockClient bsc1;
	private static BasicShockClient bsc2;
//	private static BasicShockClient bscNoAuth;
	private static AuthUser otherguy;
	
	private static ShockUserId USER1_SID;
	private static ShockUserId USER2_SID;
	
	private static MongoController mongo;
	private static ShockController shock;

	@BeforeClass
	public static void setUpClass() throws Exception {
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
		
		mongo = new MongoController(ShockTestCommon.getMongoExe(),
				Paths.get(ShockTestCommon.getTempDir()));
		System.out.println("Using Mongo temp dir " + mongo.getTempDir());
		
		shock = new ShockController(
				ShockTestCommon.getShockExe(),
				Paths.get(ShockTestCommon.getTempDir()),
				"***---fakeuser---***",
				"localhost:" + mongo.getServerPort(),
				"ShockTests_ShockDB",
				"foo",
				"foo");
		System.out.println("Using Shock temp dir " + shock.getTempDir());
		
		
		URL url = new URL("http://localhost:" + shock.getServerPort());
		System.out.println("Testing shock clients pointed at: " + url);
		String u1 = System.getProperty("test.user1");
		String u2 = System.getProperty("test.user2");
		String p1 = System.getProperty("test.pwd1");
		String p2 = System.getProperty("test.pwd2");
		
		System.out.println("Logging in users");
		AuthUser user1;
		try {
			user1 = AuthService.login(u1, p1);
		} catch (AuthException ae) {
			throw new TestException("Unable to login with test.user1: " + u1 +
					"\nPlease check the credentials in the test configuration.", ae);
		}
		System.out.println("Logged in user1");
		try {
			otherguy = AuthService.login(u2, p2);
		} catch (AuthException ae) {
			throw new TestException("Unable to login with test.user2: " + u2 +
					"\nPlease check the credentials in the test configuration.", ae);
		}
		System.out.println("Logged in user2");
		if (user1.getUserId().equals(otherguy.getUserId())) {
			throw new TestException("The user IDs of test.user1 and " + 
					"test.user2 are the same. Please provide test users with different email addresses.");
		}
		try {
			bsc1 = new BasicShockClient(url, user1.getToken());
			bsc2 = new BasicShockClient(url, otherguy.getToken());
		} catch (IOException ioe) {
			throw new TestException("Couldn't set up shock client: " +
					ioe.getLocalizedMessage());
		}
//		bscNoAuth = new BasicShockClient(url);
		System.out.println("Set up shock clients");
		USER1_SID = bsc1.addNode().getACLs().getOwner();
		USER2_SID = bsc2.addNode().getACLs().getOwner();
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (shock != null) {
			shock.destroy(ShockTestCommon.getDeleteTempFiles());
		}
		if (mongo != null) {
			mongo.destroy(ShockTestCommon.getDeleteTempFiles());
		}
	}
	
	@Test
	public void setExpiredToken() throws Exception {
		AuthToken exptok = new AuthToken(otherguy.getTokenString(), 0);
		Thread.sleep(5000); //account for a little clockskew
		try {
			bsc2.updateToken(exptok);
			fail("accepted expired token on update");
		} catch (TokenExpiredException tee) {}
	}
	
	@Test
	public void setOldToken() throws Exception {
		AuthToken orig = otherguy.getToken();
		AuthToken exptok = new AuthToken(orig.toString(),
				(new Date().getTime() - orig.getIssueDate().getTime())/1000 + 1);
		bsc2.updateToken(exptok);
		Thread.sleep(2000);
		assertTrue("token is expired", bsc2.isTokenExpired());
		try {
			bsc2.addNode();
			fail("Added node with expired token");
		} catch (TokenExpiredException tee) {}
		
		bsc2.updateToken(orig); //restore to std state
		assertFalse("token is now valid", bsc2.isTokenExpired());
	}
	

	@Test
	public void shockUrl() throws Exception {
		URL url = bsc1.getShockUrl();
		BasicShockClient b = new BasicShockClient(url); //will choke if bad url
		assertThat("url is preserved", b.getShockUrl().toString(), is(url.toString()));
		//Note using cdmi to test for cases where valid json is returned but
		//the id field != Shock. However, sometimes the cdmi server doesn't 
		//return an id, which I assume is a bug (see https://atlassian.kbase.us/browse/KBASE-200)
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
		ShockNode sn = bsc1.addNode();
		ShockNode snget = bsc1.getNode(sn.getId());
		assertThat("get node != add Node output", snget.toString(), is(sn.toString()));
		bsc1.deleteNode(sn.getId());
		getDeletedNode(sn.getId());
	}
	
	@Test
	public void getNodeBadId() throws Exception {
		try {
			bsc1.getNode(new ShockNodeId("00000000-0000-0000-0000-000000000000"));
			fail("got node with bad id");
		} catch (ShockNoNodeException snne) {
			assertThat("Bad exception message", snne.getLocalizedMessage(),
					is("Node does not exist"));
		}
	}
	
	private void getDeletedNode(ShockNodeId id) throws Exception {
		try {
			bsc1.getNode(id);
			fail("Able to retrieve deleted node");
		} catch (ShockNoNodeException snne) {
			assertThat("Bad exception message", snne.getLocalizedMessage(),
					is("Node does not exist"));
		}
	}
	
	@Test
	public void deleteByNode() throws Exception {
		ShockNode sn = bsc1.addNode();
		ShockNodeId id = sn.getId();
		sn.delete();
		getDeletedNode(id);
		try {
			sn.delete();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getAttributes();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getFile(new ByteArrayOutputStream());
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getFileInformation();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getId();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getVersion();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
	}
	
	private Map<String,Object> makeSomeAttribs(String astring) {
		Map<String, Object> attribs = new HashMap<String, Object>();
		List<Object> l = new ArrayList<Object>();
		l.add("alist");
		l.add(astring);
		Map<String, Object> inner = new HashMap<String, Object>();
		inner.put("entity", "enigma");
		l.add(inner);
		attribs.put("foo", l);
		return attribs;
	}
	
	@Test
	public void getNodeWithAttribs() throws Exception {
		Map<String, Object> attribs = makeSomeAttribs("funkycoldmedina");
		ShockNode sn = bsc1.addNode(attribs);
		testAttribs(attribs, sn);
		bsc1.deleteNode(sn.getId());
	}
	
	private void testAttribs(Map<String, Object> attribs, ShockNode sn) throws Exception {
		ShockNode snget = bsc1.getNode(sn.getId());
		assertThat("get node != add Node output", snget.toString(), is(sn.toString()));
		assertThat("attribs altered", snget.getAttributes(), is(attribs));
	}
	
	@Test
	public void getNodeWithFile() throws Exception {
		String content = "Been shopping? No, I've been shopping";
		String name = "apistonengine.recipe";
		ShockNode sn = addNode(bsc1, content, name, null);
		testFile(content, name, null, sn);
		bsc1.deleteNode(sn.getId());
	}
	
	private ShockNode addNode(BasicShockClient bsc, String content, String name,
			String format)
			throws Exception {
		return bsc.addNode(new ReaderInputStream(new StringReader(content)),
				name, format);
	}
	
	private ShockNode addNode(BasicShockClient bsc, Map<String, Object> attribs,
			String content, String name, String format)
			throws Exception {
		return bsc.addNode(attribs, new ReaderInputStream(new StringReader(content)),
				name, format);
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
				ShockNode sn = writeFileToNode(null, s, count, "", "" + id, "JSON", id);
				verifyStreamedNode(sn, null, s, count, "", "" + id, "JSON", id);
				sn.delete();
			} catch (Exception e) {
				this.e = e;
			}
		}
	}
	
//	@Ignore
	@Test
	public void saveAndGetStreamingFiles() throws Exception {
		int chunksize = BasicShockClient.getChunkSize();
		if (chunksize % 10 != 0) {
			throw new TestException("expected chunk size to be divisible by 10");
		}
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
		
		ShockNode sn = writeFileToNode(attribs, sb.toString(), minwrites, sbs, "filename", "JSON", 1);
		verifyStreamedNode(sn, attribs, sb.toString(), minwrites, sbs, "filename", "JSON", 1);
		sn.delete();
		
		sn = writeFileToNode(null, sb.toString(), minwrites, sbs + "~", "filename", null, 2);
		verifyStreamedNode(sn, null, sb.toString(), minwrites, sbs + "~", "filename", null, 2);
		sn.delete();
		
		sn = writeFileToNode(null, sb.toString(), minwrites, sbs + ch2, "filename", "", 3);
		verifyStreamedNode(sn, null, sb.toString(), minwrites, sbs + ch2, "filename", null, 3);
		sn.delete();
		
		sn = writeFileToNode(attribs, sb.toString(), minwrites * 2, sbs2 + "j", "filename", "", 4);
		verifyStreamedNode(sn, attribs, sb.toString(), minwrites * 2, sbs2 + "j", "filename", null, 4);
		sn.delete();
		
		sn = writeFileToNode(null, sb.toString(), minwrites * 2, sbs2 + ch2, "filename", "UTF-8", 5);
		verifyStreamedNode(sn, null, sb.toString(), minwrites * 2, sbs2 + ch2, "filename", "UTF-8", 5);
		sn.delete();
		
		sn = writeFileToNode(attribs, sb.toString(), minwrites * 2, sbs2 + ch3, "filename", "ASCII", 6);
		verifyStreamedNode(sn, attribs, sb.toString(), minwrites * 2, sbs2 + ch3, "filename", "ASCII", 6);
		sn.delete();
	}
	
	private void verifyStreamedNode(final ShockNode sn,
			final Map<String, Object> attribs, final String string,
			final long writes, final String last, final String filename,
			final String format, final int id)
			throws Exception {
		assertThat("attribs correct", sn.getAttributes(), is(attribs));
		final int readlen = string.getBytes(StandardCharsets.UTF_8).length;
		final int finallen = last.getBytes(StandardCharsets.UTF_8).length;
		final long filesize = readlen * writes + finallen;
		assertThat("filesize correct", sn.getFileInformation().getSize(), is(filesize));
		//TODO restore when setting filename works for streamed files
//		assertThat("filename correct", sn.getFileInformation().getName(), is(filename));
		assertThat("format correct", sn.getFileInformation().getFormat(), is(format));
		System.out.println("ID " + id + " Verifying " + filesize + "b file... ");

		OutputStreamToInputStream<String> osis =
				new OutputStreamToInputStream<String>() {
					
			@Override
			protected String doRead(InputStream is) throws Exception {
				byte[] data = new byte[readlen];
				int read = read(is, data);
				long size = 0;
				long reads = 1;
				while (reads <= writes) {
					assertThat("file incorrect at pos " + size, 
							new String(data, StandardCharsets.UTF_8),
							is(string));
					size += read;
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
				return null;
			}
		};
		bsc1.getFile(sn, osis);
		osis.close();
		System.out.println("\tID " + id + " Verifying done.");
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

	private ShockNode writeFileToNode(final Map<String, Object> attribs,
			final String string, final long writes, final String last,
			final String filename, final String format, final int id)
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
		ShockNode sn;
		if (attribs == null) {
			sn = bsc1.addNode(isos, filename, format);
		} else {
			sn = bsc1.addNode(attribs, isos, filename, format);
		}
		isos.close();
		System.out.println("\tID " + id + " Streaming done.");
		return sn;
	}

	@Test
	public void saveAndGetNodeWith4GBFile() throws Exception {
		if (BasicShockClient.getChunkSize() != 50000000) {
			throw new TestException("expected chunk size to be 100000000");
		}
		StringBuilder sb = new StringBuilder();
		sb.append("abcd");
		sb.appendCodePoint(0x20AC);
		
		StringBuilder last = new StringBuilder();
		last.appendCodePoint(0x10310);
		Map<String, Object> attribs = new HashMap<String, Object>();
		attribs.put("foo", "bar");
		
		final long writes = 571428571;
		ShockNode sn = writeFileToNode(attribs, sb.toString(), writes, last.toString(), "somefile", "JSON", 1);
		verifyStreamedNode(sn, attribs, sb.toString(), writes, last.toString(), "somefile", "JSON", 1);
		bsc1.deleteNode(sn.getId());
	}
	
	private void testFile(String content, String name, String format, ShockNode sn)
			throws Exception {
		ShockNode snget = bsc1.getNode(sn.getId());
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bsc1.getFile(sn, bos);
		String filecon = bos.toString(StandardCharsets.UTF_8.name());
		ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
		snget.getFile(bos2);
		String filefromnode = bos2.toString(StandardCharsets.UTF_8.name());
		Set<String> digestTypes = snget.getFileInformation().getChecksumTypes();
		assertTrue("has md5", digestTypes.contains("md5"));
		assertThat("unequal md5", snget.getFileInformation().getChecksum("md5"),
				is(DigestUtils.md5Hex(content)));
		try {
			snget.getFileInformation().getChecksum("this is not a checksum type");
			fail("got checksum type that doesn't exist");
		} catch (IllegalArgumentException iae) {
			assertThat("exception string incorrect", 
					"java.lang.IllegalArgumentException: No such checksum type: this is not a checksum type",
					is(iae.toString()));
		}
		assertThat("file from node != file from client", filefromnode, is(filecon));
		assertThat("file content unequal", filecon, is(content));
		assertThat("file name unequal", snget.getFileInformation().getName(), is(name));
		assertThat("file format unequal", snget.getFileInformation().getFormat(), is(format));
		assertThat("file size wrong", snget.getFileInformation().getSize(), is((long) content.length()));
	}
	
	@Test
	public void getNodeWithFileAndAttribs() throws Exception {
		String content = "Like the downy growth on the upper lip of a mediterranean girl";
		String name = "bydemagogueryImeandemagoguery";
		Map<String, Object> attribs = makeSomeAttribs("castellaandlillete");
		ShockNode sn = addNode(bsc1, attribs, content, name, "UTF-8");
		testFile(content, name, "UTF-8", sn);
		testAttribs(attribs, sn);
		sn.delete();
	}
	
	@Test
	public void invalidFileRequest() throws Exception {
		ShockNode sn = bsc1.addNode();
		try {
			sn.getFile(new ByteArrayOutputStream());
			fail("Got file from node w/o file");
		} catch (ShockNoFileException snfe) {
			assertThat("no file exc string incorrect", snfe.getLocalizedMessage(), 
					is("Node has no file"));
		}
		try {
			sn.getFile(null);
			fail("called get file w/ null arg");
		} catch (IllegalArgumentException ioe) {
			assertThat("no file exc string incorrect", ioe.getLocalizedMessage(), 
					is("Neither the shock node nor the file may be null"));
		}
		try {
			bsc1.getFile(sn, new ByteArrayOutputStream());
			fail("Got file from node w/o file");
		} catch (ShockNoFileException snfe) {
			assertThat("no file exc string incorrect", snfe.getLocalizedMessage(), 
					is("Node has no file"));
		}
		try {
			bsc1.getFile(sn.getId(), new ByteArrayOutputStream());
			fail("Got file from node w/o file");
		} catch (ShockNoFileException snfe) {
			assertThat("no file exc string incorrect", snfe.getLocalizedMessage(), 
					is("Node has no file"));
		}
		try {
			bsc1.getFile(sn, null);
			fail("called get file w/ null arg");
		} catch (IllegalArgumentException ioe) {
			assertThat("no file exc string incorrect", ioe.getLocalizedMessage(), 
					is("Neither the shock node nor the file may be null"));
		}
		//can't test with sn == null since method call is ambiguous
		sn.delete();
	}
	
	@Test
	public void getNodeNulls() throws Exception {
		Map<String, Object> attribs = makeSomeAttribs("wuggawugga");
		try {
			bsc1.addNode(null);
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("attributes may not be null"));
		}
		try {
			bsc1.addNode(null, "foo", "foo");
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("file may not be null"));
		}
		try {
			addNode(bsc1, "foo", null, "foo");
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("filename may not be null"));
		}
		try {
			addNode(bsc1, null, "foo", "foo", "foo");
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("attributes may not be null"));
		}
		try {
			bsc1.addNode(attribs, null, "foo", null);
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("file may not be null"));
		}
		try {
			addNode(bsc1, attribs, "foo", null, null);
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("filename may not be null"));
		}
	}
	
	@Test
	public void ids() throws Exception {
		//will throw exception if doesn't process good uuid
		new ShockUserId("cbf19927-1e04-456c-b2c3-812edd90fa68");
		ShockNodeId id1 = new ShockNodeId("cbf19927-1e04-456c-b2c3-812edd90fa68");
		ShockNodeId id2 = new ShockNodeId("cbf19927-1e04-456c-b2c3-812edd90fa68");
		assertTrue("id equality failed", id1.equals(id1));
		assertTrue("id state failed", id1.equals(id2));
		assertFalse("non id equal to id", id1.equals(new ArrayList<Object>()));
		
		
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
		for (String uuid: badUUIDs) {
			try {
				new ShockUserId(uuid);
				fail("User id accepted invalid id string " + uuid);
			} catch (IllegalArgumentException iae) {
				assertThat("Bad exception message", iae.getLocalizedMessage(),
						is("id must be a UUID hex string"));
			}
		}
	}
	
	@Test
	public void generalAcls() throws Exception {
		try {
			new ShockACLType("invalid type") ;
			fail("invalid acl type accepted");
		} catch (IllegalArgumentException iae) {
			assertThat("wrong exception string for bad acl type", iae.getLocalizedMessage(),
					is("invalid type is not a valid acl type"));
		}
		ShockACLType owner = new ShockACLType("owner");
		ShockNode sn = bsc1.addNode();
		assertTrue("acl access methods produce different acls",
				sn.getACLs().equals(bsc1.getACLs(sn.getId())));
		ShockACL acl1 = sn.getACLs(owner);
		ShockACL acl2 = bsc1.getACLs(sn.getId(), owner);
		assertTrue("acl owner access methods produce different acls",
				acl1.equals(acl2));
		assertTrue("owners for same node are different",
				acl1.getOwner().equals(acl1.getOwner()));
		assertTrue("same acls aren't equal", acl1.equals(acl1));
		assertFalse("acl equal to different type", acl1.equals(owner));
		
		List<ShockACLType> acls = Arrays.asList(new ShockACLType("all"),
				new ShockACLType("read"), new ShockACLType("write"),
				new ShockACLType("delete"), new ShockACLType("all"),
				new ShockACLType());
		for (ShockACLType acl: acls) {
			ShockACL sacl = sn.getACLs(acl);
			assertTrue(String.format("%s subset of acls are different", acl.getType()),
					sacl.equals(bsc1.getACLs(sn.getId(), acl)));
			checkListLengthIfNotNull(sacl.getRead(), 1);
			checkListLengthIfNotNull(sacl.getWrite(), 1);
			checkListLengthIfNotNull(sacl.getDelete(), 1);
		}
		sn.delete();
	}
	
	private void checkListLengthIfNotNull(@SuppressWarnings("rawtypes") List list,
			int length) {
		if (list != null) {
			assertTrue(String.format("only %d user in new acl", length),
					list.size() == length);
		}
	}
	
	@Test
	public void modifyAcls() throws Exception {
		ShockNode sn = setUpNodeAndCheckAuth(bsc1, bsc2);
		ShockACL acls = sn.getACLs();
		assertThat("user1 is owner", acls.getOwner(), is(USER1_SID));
		assertThat("only owner in read acl", acls.getRead(), is(Arrays.asList(USER1_SID)));
		assertThat("only owner in write acl", acls.getWrite(), is(Arrays.asList(USER1_SID)));
		assertThat("only owner in delete acl", acls.getDelete(), is(Arrays.asList(USER1_SID)));
		List<String> singleAcls = Arrays.asList("read", "write", "delete");
		for (String aclTypeString: singleAcls) {
			ShockACLType aclType = new ShockACLType(aclTypeString);
			failAddAcl(bsc1, null, Arrays.asList(otherguy.getUserId()), aclType,
					new NullPointerException("id cannot be null"));
			failAddAcl(bsc1, sn.getId(), Arrays.asList((String) null), aclType,
					new IllegalArgumentException("user cannot be null or the empty string"));
			failAddAcl(sn, Arrays.asList((String) null), aclType,
					new IllegalArgumentException("user cannot be null or the empty string"));
			failAddAcl(bsc1, sn.getId(), Arrays.asList(""), aclType,
					new IllegalArgumentException("user cannot be null or the empty string"));
			failAddAcl(sn, Arrays.asList(""), aclType,
					new IllegalArgumentException("user cannot be null or the empty string"));

			String acl = aclType.getType() + " acl";
			bsc1.addToNodeAcl(sn.getId(), Arrays.asList(otherguy.getUserId()), aclType);
			assertThat("added user to " + acl, getAcls(bsc1, sn.getId(), aclType),
					is(Arrays.asList(USER1_SID, USER2_SID)));
			bsc1.removeFromNodeAcl(sn.getId(), Arrays.asList(otherguy.getUserId()), aclType);
			assertThat("removed user from " + acl, getAcls(bsc1, sn.getId(), aclType),
					is(Arrays.asList(USER1_SID)));
			sn.addToNodeAcl(Arrays.asList(otherguy.getUserId()), aclType);
			assertThat("added user to " + acl, getAcls(sn, aclType),
					is(Arrays.asList(USER1_SID, USER2_SID)));
			sn.removeFromNodeAcl(Arrays.asList(otherguy.getUserId()), aclType);
			assertThat("removed user from " + acl, getAcls(sn, aclType),
					is(Arrays.asList(USER1_SID)));
		}
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
		} catch (Exception exp) {
			assertThat("correct exception type", exp, is(e.getClass()));
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getMessage()));
		} 
	}
	
	
	private ShockNode setUpNodeAndCheckAuth(BasicShockClient source,
			BasicShockClient check)
			throws Exception {
		ShockNode sn = source.addNode();
		try {
			check.getNode(sn.getId());
			fail("Node is readable with no permissions");
		} catch (ShockAuthorizationException aue) {
			assertThat("auth exception string is correct", aue.getLocalizedMessage(),
					is("User Unauthorized"));
		}
		return sn;
	}
	
	private void checkAuthAndDelete(ShockNode sn, BasicShockClient c, int size)
			throws Exception {
		assertThat("Setting read perms failed", size, 
				is(sn.getACLs(new ShockACLType("read")).getRead().size()));
		sn = bsc1.getNode(sn.getId()); //version stamp changed
		ShockNode sn2 = c.getNode(sn.getId());
		assertThat("different users see different nodes", sn.toString(),
				is(sn2.toString()));
		sn.delete();
	}
	
	@Test
	public void version() throws Exception {
		ShockNode sn = bsc1.addNode();
		sn.getVersion().getVersion(); //not much else to do here
		List<String> badMD5s = Arrays.asList("fe90c05e51aa22e53daec604c815962g3",
				"e90c05e51aa22e53daec604c815962f", "e90c05e51aa-2e53daec604c815962f3",
				"e90c05e51aa22e53daec604c815962f31");
		for (String md5: badMD5s) {
			try {
				new ShockVersionStamp(md5);
				fail("Version stamp accepted invalid version string");
			} catch (IllegalArgumentException iae) {
				assertThat("Bad exception message", iae.getLocalizedMessage(),
						is("version must be an md5 string"));
			}
		}
		bsc1.deleteNode(sn.getId());
		//will throw errors if doesn't accept md5
		new ShockVersionStamp("e90c05e51aa22e53daec604c815962f3");
	}
}
