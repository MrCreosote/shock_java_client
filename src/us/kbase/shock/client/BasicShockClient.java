package us.kbase.shock.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.auth.AuthToken;
import us.kbase.auth.TokenExpiredException;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockNoFileException;

/**
 * A basic client for shock. Creating nodes, deleting nodes,
 * getting a subset of node data, and altering read acls is currently supported.
 * 
 * Currently limited to 1000 connections.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class BasicShockClient {
	
	private final URI baseurl;
	private final URI nodeurl;
	private final static PoolingHttpClientConnectionManager connmgr =
			new PoolingHttpClientConnectionManager();
	static {
		connmgr.setMaxTotal(1000); //perhaps these should be configurable
		connmgr.setDefaultMaxPerRoute(1000);
	}
	private final static CloseableHttpClient client =
			HttpClients.custom().setConnectionManager(connmgr).build();
	private final ObjectMapper mapper = new ObjectMapper();
	private AuthToken token = null;
	
	private static final String AUTH = "Authorization";
	private static final String OAUTH = "OAuth ";
	private static final String ATTRIBFILE = "attribs";
	private static final ShockACLType ACL_READ = new ShockACLType("read");
	
	/**
	 * The size of the file chunks sent/received from the Shock server.
	 */
	public static final int CHUNK_SIZE = 100000000; //~100 Mb
	private static final String DOWNLOAD_CHUNK = 
			"/?download&index=size&chunk_size=" + CHUNK_SIZE + "&part=";
	
	/**
	 * Create a new shock client authorized to act as a shock user.
	 * @param url the location of the shock server.
	 * @param token the authorization token to present to shock.
	 * @throws IOException if an IO problem occurs.
	 * @throws InvalidShockUrlException if the <code>url</code> does not reference
	 * a shock server.
	 * @throws TokenExpiredException if the <code>token</code> is expired.
	 */
	public BasicShockClient(final URL url, final AuthToken token)
			throws IOException, InvalidShockUrlException,
			TokenExpiredException {
		this(url);
		updateToken(token);
	}
	
	/**
	 * Create a new shock client.
	 * @param url the location of the shock server.
	 * @throws IOException if an IO problem occurs.
	 * @throws InvalidShockUrlException if the <code>url</code> does not reference
	 * a shock server.
	 */
	@SuppressWarnings("unchecked")
	public BasicShockClient(final URL url) throws IOException, 
			InvalidShockUrlException {

		mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
		
		String turl = url.getProtocol() + "://" + url.getAuthority()
				+ url.getPath();
		if (turl.charAt(turl.length() - 1) != '/') {
			turl = turl + "/";
		}
		if (!(url.getProtocol().equals("http") ||
				url.getProtocol().equals("https"))) {
			throw new InvalidShockUrlException(turl.toString());
			
		}
		final CloseableHttpResponse response = client.execute(
				new HttpGet(turl));
		final Map<String, Object> shockresp;
		try {
			final String resp = EntityUtils.toString(response.getEntity());
			shockresp = mapper.readValue(resp, Map.class);
		} catch (JsonParseException jpe) {
			throw new InvalidShockUrlException(turl.toString());
		} finally {
			response.close();
		}
		if (!shockresp.containsKey("id")) {
			throw new InvalidShockUrlException(turl.toString());
		}
		if (!shockresp.get("id").equals("Shock")) {
			throw new InvalidShockUrlException(turl.toString());
		}
		URL shockurl = new URL(shockresp.get("url").toString());
		//https->http is caused by the router, not shock, per Jared
		if (url.getProtocol().equals("https")) {
			shockurl = new URL("https", shockurl.getAuthority(),
					shockurl.getPort(), shockurl.getFile());
		}
		try {
			baseurl = shockurl.toURI();
		} catch (URISyntaxException use) {
			throw new Error(use); //something went badly wrong 
		}
		nodeurl = baseurl.resolve("node/");
	}
	
	/**
	 * Replace the token this client presents to the shock server.
	 * @param token the new token
	 * @throws TokenExpiredException if the <code>token</code> is expired.
	 */
	public void updateToken(final AuthToken token)
			throws TokenExpiredException {
		if (token == null) {
			this.token = null;
			return;
		}
		if (token.isExpired()) {
			throw new TokenExpiredException(token.getTokenId());
		}
		this.token = token;
	}
	
	/**
	 * Check the token's validity.
	 * @return <code>true</code> if the client has no auth token or the token
	 * is expired, <code>false</code> otherwise.
	 */
	public boolean isTokenExpired() {
		if (token == null || token.isExpired()) {
			return true;
		}
		return false;
	}
	
	/** 
	 * Get the url of the shock server this client communicates with.
	 * @return the shock url.
	 */
	public URL getShockUrl() {
		return uriToUrl(baseurl);
	}
	
	private <T extends ShockResponse> ShockData
			processRequest(final HttpRequestBase httpreq, final Class<T> clazz)
			throws IOException, ShockHttpException, TokenExpiredException {
		authorize(httpreq);
		final CloseableHttpResponse response = client.execute(httpreq);
		try {
			return getShockData(response, clazz);
		} finally {
			response.close();
		}
	}
	
	private <T extends ShockResponse> ShockData
			getShockData(final HttpResponse response, final Class<T> clazz) 
			throws IOException, ShockHttpException {
		final String resp = EntityUtils.toString(response.getEntity());
		try {
			return mapper.readValue(resp, clazz).getShockData();
		} catch (JsonParseException jpe) {
			throw new RuntimeException(jpe); //something's broken
		}
	}
	
	private void authorize(final HttpRequestBase httpreq) throws
			TokenExpiredException {
		if (token != null) {
			if (token.isExpired()) {
				throw new TokenExpiredException(token.getTokenId());
			}
			httpreq.setHeader(AUTH, OAUTH + token);
		}
	}

	/** 
	 * Gets a node from the shock server. Note the object returned 
	 * represents the shock node's state at the time getNode() was called
	 * and does not update further.
	 * @param id the ID of the shock node.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be fetched from shock.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode getNode(final ShockNodeId id) throws IOException,
			ShockHttpException, TokenExpiredException {
		if (id == null) {
			throw new IllegalArgumentException("id may not be null");
		}
		final URI targeturl = nodeurl.resolve(id.getId());
		final HttpGet htg = new HttpGet(targeturl);
		final ShockNode sn = (ShockNode)processRequest(htg, ShockNodeResponse.class);
		sn.addClient(this);
		return sn;
	}
	
	/**
	 * Equivalent to client.getFile(client.getNode(id), file)
	 * @param id the ID of the shock node.
	 * @param file the stream to which the file will be written.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the file could not be fetched from shock.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public void getFile(final ShockNodeId id, final OutputStream file)
			throws IOException, ShockHttpException, TokenExpiredException {
		getFile(getNode(id), file);
	}
	
	/**
	 * Get the file for this shock node.
	 * @param sn the shock node from which to retrieve the file.
	 * @param file the stream to which the file will be written.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the file could not be fetched from shock.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public void getFile(final ShockNode sn, final OutputStream file)
			throws TokenExpiredException, IOException, ShockHttpException {
		if (sn == null || file == null) {
			throw new IllegalArgumentException(
					"Neither the shock node nor the file may be null");
		}
		if (sn.getFileInformation().getSize() == 0) {
			throw new ShockNoFileException(400, "Node has no file");
		}
		final BigDecimal size = new BigDecimal(
				sn.getFileInformation().getSize());
		//if there are more than 2^32 chunks we're in big trouble
		final int chunks = size.divide(new BigDecimal(CHUNK_SIZE))
				.setScale(0, BigDecimal.ROUND_CEILING).intValueExact();
		final URI targeturl = nodeurl.resolve(sn.getId().getId() +
				DOWNLOAD_CHUNK);
		for (int i = 0; i < chunks; i++) {
			final HttpGet htg = new HttpGet(targeturl.toString() + (i + 1));
			authorize(htg);
			final CloseableHttpResponse response = client.execute(htg);
			try {
				final int code = response.getStatusLine().getStatusCode();
				if (code > 299) {
					getShockData(response, ShockNodeResponse.class); //trigger errors
				}
				file.write(EntityUtils.toByteArray(response.getEntity()));
			} finally {
				response.close();
			}
		}
	}
	
	/**
	 * Creates an empty node on the shock server.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be created.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode addNode() throws IOException, ShockHttpException,
			TokenExpiredException {
		return _addNode(null, null, null, null);
	}
	
	/**
	 * Creates a node on the shock server with user-specified attributes.
	 * @param attributes the user-specified attributes.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be created.
	 * @throws JsonProcessingException if the <code>attributes</code> could
	 * not be serialized to JSON.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode addNode(final Map<String, Object> attributes) throws
			IOException, ShockHttpException, JsonProcessingException,
			TokenExpiredException {
		if (attributes == null) {
			throw new IllegalArgumentException("attributes may not be null");
		}
		return _addNode(attributes, null, null, null);
	}
	
	/**
	 * Creates a node on the shock server containing a file.
	 * @param file the file data.
	 * @param filename the name of the file.
	 * @param format the format of the file, e.g. ASCII, UTF-8, JSON. Ignored
	 * if null.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be created.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode addNode(final InputStream file, final String filename,
			final String format)
			throws IOException, ShockHttpException, TokenExpiredException {
		if (file == null) {
			throw new IllegalArgumentException("file may not be null");
		}
		if (filename == null) {
			throw new IllegalArgumentException("filename may not be null");
		}
		return _addNodeStreaming(null, file, filename, format);
	}
	
	/**
	 * Creates a node on the shock server with user-specified attributes and 
	 * a file.
	 * @param attributes the user-specified attributes.
	 * @param file the file data.
	 * @param filename the name of the file.
	 * @param format the format of the file, e.g. ASCII, UTF-8, JSON. Ignored
	 * if null.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be created.
	 * @throws JsonProcessingException if the <code>attributes</code> could
	 * not be serialized to JSON.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode addNode(final Map<String, Object> attributes,
			final InputStream file, final String filename, final String format)
			throws IOException, ShockHttpException,
			JsonProcessingException, TokenExpiredException {
		if (attributes == null) {
			throw new IllegalArgumentException("attributes may not be null");
		}
		if (file == null) {
			throw new IllegalArgumentException("file may not be null");
		}
		if (filename == null) {
			throw new IllegalArgumentException("filename may not be null");
		}
		return _addNodeStreaming(attributes, file, filename, format);
	}
	
	private ShockNode _addNode(final Map<String, Object> attributes,
			final byte[] file, final String filename, final String format)
			throws IOException, ShockHttpException, JsonProcessingException,
			TokenExpiredException {
		final HttpPost htp = new HttpPost(nodeurl);
		if (attributes != null || file != null) {
			final MultipartEntityBuilder mpeb = MultipartEntityBuilder.create();
			if (attributes != null) {
				final byte[] attribs = mapper.writeValueAsBytes(attributes);
				mpeb.addBinaryBody("attributes", attribs,
						ContentType.APPLICATION_JSON, ATTRIBFILE);
			}
			if (file != null) {
				mpeb.addBinaryBody("upload", file, ContentType.DEFAULT_BINARY,
						filename);
			}
			if (format != null) {
				mpeb.addTextBody("format", format);
			}
			htp.setEntity(mpeb.build());
		}
		final ShockNode sn = (ShockNode) processRequest(htp,
				ShockNodeResponse.class);
		sn.addClient(this);
		return sn;
	}
	
	private ShockNode _addNodeStreaming(final Map<String, Object> attributes,
			final InputStream file, final String filename, final String format)
			throws IOException, ShockHttpException, JsonProcessingException,
			TokenExpiredException {
		byte[] b = new byte[CHUNK_SIZE];
		int read = read(file, b);
		if (read < CHUNK_SIZE) {
			return _addNode(attributes, Arrays.copyOf(b, read), filename,
					format);
		}
		int chunks = 1;
		ShockNode sn;
		{
			final HttpPost htp = new HttpPost(nodeurl);
			final MultipartEntityBuilder mpeb = MultipartEntityBuilder.create();
			mpeb.addTextBody("parts", "unknown");
			if (attributes != null) {
				final byte[] attribs = mapper.writeValueAsBytes(attributes);
				mpeb.addBinaryBody("attributes", attribs,
						ContentType.APPLICATION_JSON, ATTRIBFILE);
			}
			if (format != null) {
				mpeb.addTextBody("format", format);
			}
			htp.setEntity(mpeb.build());
			sn = (ShockNode) processRequest(htp, ShockNodeResponse.class);
		}
		final URI targeturl = nodeurl.resolve(sn.getId().getId());
		while (read > 0) {
			final HttpPut htp = new HttpPut(targeturl);
			if (read < CHUNK_SIZE) {
				b = Arrays.copyOf(b, read);
			}
			final MultipartEntityBuilder mpeb = MultipartEntityBuilder.create();
			mpeb.addBinaryBody("" + chunks, b, ContentType.DEFAULT_BINARY,
					filename);
			htp.setEntity(mpeb.build());
			processRequest(htp, ShockNodeResponse.class);
			b = new byte[CHUNK_SIZE];
			read = read(file, b);
			chunks++;
		}
		{
			final HttpPut htp = new HttpPut(targeturl);
			final MultipartEntityBuilder mpeb = MultipartEntityBuilder.create();
			mpeb.addTextBody("parts", "close");
			htp.setEntity(mpeb.build());
			sn = (ShockNode) processRequest(htp, ShockNodeResponse.class);
		}
		sn.addClient(this);
		return sn;
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
	
	/**
	 * Deletes a node on the shock server.
	 * @param id the node to delete.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be deleted.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public void deleteNode(final ShockNodeId id) throws IOException, 
			ShockHttpException, TokenExpiredException {
		final URI targeturl = nodeurl.resolve(id.getId());
		final HttpDelete htd = new HttpDelete(targeturl);
		processRequest(htd, ShockNodeResponse.class); //triggers throwing errors
	}
	
	/**
	 * Adds a user to a shock node's read access control list (ACL).
	 * @param id the node to modify.
	 * @param user the user to be added to the read ACL.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node ACL could not be altered.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public void setNodeReadable(final ShockNodeId id, final String user)
			throws IOException, ShockHttpException,TokenExpiredException {
		if (id == null) {
			throw new IllegalArgumentException("id cannot be null");
		}
		if (user == null || user.equals("")) {
			throw new IllegalArgumentException(
					"user cannot be null or the empty string");
		}
		final URI targeturl = nodeurl.resolve(id.getId() + ACL_READ.acl + 
				"?users=" + user);
		final HttpPut htp = new HttpPut(targeturl);
		//TODO check errors are ok when Shock changes to ACLs for editing ACLs
		processRequest(htp, ShockACLResponse.class); //triggers throwing errors
	}
	
	/**
	 * Makes a node world readable.
	 * @param id the node to modify.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node read access control list could not be
	 * altered.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public void setNodeWorldReadable(final ShockNodeId id) throws IOException,
			ShockHttpException, TokenExpiredException {
		//TODO change to settingn global read boolean rather than deleting users when shock supports
		final List<ShockUserId> acls = getACLs(id, ACL_READ).getRead();
		final List<String> userlist = new ArrayList<String>();
		for (ShockUserId uid: acls) {
			userlist.add(uid.getId());
		}
		final URI targeturl = nodeurl.resolve(id.getId() + ACL_READ.acl +
				"?users=" + StringUtils.join(userlist, ","));
		final HttpDelete htd = new HttpDelete(targeturl);
		processRequest(htd, ShockACLResponse.class);
		
	}
	
	/**
	 * Retrieves all the access control lists (ACLs) from the shock server for
	 * a node. Note the object returned represents the shock node's state at
	 * the time getACLs() was called and does not update further.
	 * @param id the node to query.
	 * @return the ACLs for the node.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node's access control lists could not be
	 * retrieved.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockACL getACLs(final ShockNodeId id) throws IOException,
			ShockHttpException, TokenExpiredException {
		return getACLs(id, new ShockACLType());
	}
	
	/**
	 * Retrieves a specific access control list (ACL) from the shock server for
	 * a node. Note the object returned represents the shock node's state at
	 * the time getACLs() was called and does not update further.
	 * @param id the node to query.
	 * @param acl the type of ACL to retrieve.
	 * @return the ACL for the node.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node's access control list could not be
	 * retrieved.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockACL getACLs(final ShockNodeId id, final ShockACLType acl) 
			throws IOException, ShockHttpException, TokenExpiredException {
		final URI targeturl = nodeurl.resolve(id.getId() + acl.acl);
		final HttpGet htg = new HttpGet(targeturl);
		return (ShockACL)processRequest(htg, ShockACLResponse.class);
	}
	
	//for known good uris ONLY
	private URL uriToUrl(final URI uri) {
		try {
			return uri.toURL();
		} catch (MalformedURLException mue) {
			throw new RuntimeException(mue); //something is seriously fuxxored
		}
	}
}
