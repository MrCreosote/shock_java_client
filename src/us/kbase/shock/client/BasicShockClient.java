package us.kbase.shock.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
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
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
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
	
	private String version;
	private final URI baseurl;
	private final URI nodeurl;
	private static CloseableHttpClient client;
	private final ObjectMapper mapper = new ObjectMapper();
	private AuthToken token = null;
	
	private static final String AUTH = "Authorization";
	private static final String OAUTH = "OAuth ";
	private static final String ATTRIBFILE = "attribs";
	
	private static int CHUNK_SIZE = 50000000; //~50 Mb
	
	/** Get the size of the upload / download chunk size.
	 * @return the size of the file chunks sent/received from the Shock server.
	 */
	public static int getChunkSize() {
		return CHUNK_SIZE;
	}
	private static String getDownloadURLPrefix() {
		return "/?download&index=size&chunk_size=" + CHUNK_SIZE + "&part=";
	}
	
	private static synchronized void createHttpClient(
			final boolean allowSelfSignedCerts) {
		if (client != null) {
			return; //already done
		}
		if (allowSelfSignedCerts) {
			//http://stackoverflow.com/questions/19517538/ignoring-ssl-certificate-in-apache-httpclient-4-3
			final SSLConnectionSocketFactory sslsf;
			try {
				final SSLContextBuilder builder = new SSLContextBuilder();
				builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
				sslsf = new SSLConnectionSocketFactory(builder.build());
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException("Unable to build http client", e);
			} catch (KeyStoreException e) {
				throw new RuntimeException("Unable to build http client", e);
			} catch (KeyManagementException e) {
				throw new RuntimeException("Unable to build http client", e);
			}

			final Registry<ConnectionSocketFactory> registry =
					RegistryBuilder.<ConnectionSocketFactory>create()
					.register("http", new PlainConnectionSocketFactory())
					.register("https", sslsf)
					.build();

			final PoolingHttpClientConnectionManager cm =
					new PoolingHttpClientConnectionManager(registry);
			cm.setMaxTotal(1000); //perhaps these should be configurable
			cm.setDefaultMaxPerRoute(1000);

			//TODO set timeouts for the client for 1/2m for conn req timeout and std timeout
			client = HttpClients.custom()
					.setSSLSocketFactory(sslsf)
					.setConnectionManager(cm)
					.build();
		} else {
			final PoolingHttpClientConnectionManager cm =
					new PoolingHttpClientConnectionManager();
			cm.setMaxTotal(1000); //perhaps these should be configurable
			cm.setDefaultMaxPerRoute(1000);
			//TODO set timeouts for the client for 1/2m for conn req timeout and std timeout
			client = HttpClients.custom()
					.setConnectionManager(cm)
					.build();
		}
	}
	
	/**
	 * Create a new shock client.
	 * @param url the location of the shock server.
	 * @throws IOException if an IO problem occurs.
	 * @throws InvalidShockUrlException if the <code>url</code> does not
	 * reference a shock server.
	 */
	public BasicShockClient(final URL url)
			throws IOException, InvalidShockUrlException {
		this(url, false);
	}
	
	/**
	 * Create a new shock client authorized to act as a shock user.
	 * @param url the location of the shock server.
	 * @param token the authorization token to present to shock.
	 * @throws IOException if an IO problem occurs.
	 * @throws InvalidShockUrlException if the <code>url</code> does not
	 * reference a shock server.
	 * @throws TokenExpiredException if the <code>token</code> is expired.
	 * @throws ShockHttpException if the connection to shock fails.
	 */
	public BasicShockClient(final URL url, final AuthToken token)
			throws IOException, InvalidShockUrlException,
			TokenExpiredException, ShockHttpException {
		this(url, token, false);
	}
	
	/**
	 * Create a new shock client.
	 * @param url the location of the shock server.
	 * @param allowSelfSignedCerts <code>true</code> to permit self signed
	 * certificates when contacting servers.
	 * @throws IOException if an IO problem occurs.
	 * @throws InvalidShockUrlException if the <code>url</code> does not
	 * reference a shock server.
	 */
	public BasicShockClient(final URL url, boolean allowSelfSignedCerts)
			throws InvalidShockUrlException, IOException {
		
		createHttpClient(allowSelfSignedCerts);
		
		mapper.enable(
				DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
		
		String turl = url.getProtocol() + "://" + url.getAuthority()
				+ url.getPath();
		if (turl.charAt(turl.length() - 1) != '/') {
			turl = turl + "/";
		}
		try {
			baseurl = new URL(turl).toURI();
		} catch (URISyntaxException use) {
			throw new RuntimeException(use); //something went badly wrong 
		}
		if (!(url.getProtocol().equals("http") ||
				url.getProtocol().equals("https"))) {
			throw new InvalidShockUrlException(turl.toString());
			
		}
		getRemoteVersion();
		nodeurl = baseurl.resolve("node/");
	}
	
	/**
	 * Create a new shock client authorized to act as a shock user.
	 * @param url the location of the shock server.
	 * @param token the authorization token to present to shock.
	 * @param allowSelfSignedCerts <code>true</code> to permit self signed
	 * certificates when contacting servers.
	 * @throws IOException if an IO problem occurs.
	 * @throws InvalidShockUrlException if the <code>url</code> does not
	 * reference a shock server.
	 * @throws TokenExpiredException if the <code>token</code> is expired.
	 * @throws ShockHttpException if the connection to shock fails.
	 */
	public BasicShockClient(
			final URL url,
			final AuthToken token,
			boolean allowSelfSignedCerts)
			throws TokenExpiredException, InvalidShockUrlException,
			ShockHttpException, IOException {
		this(url, allowSelfSignedCerts);
		updateToken(token);
		if (token != null) { // test shock config/auth etc.
			final ShockNode sn = addNode();
			sn.delete();
		}
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
	
	/** Get the auth token used by this client, if any.
	 * 
	 * @return the auth token.
	 */
	public AuthToken getToken() {
		return token;
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
	
	/** Get the version of the Shock server. This version is cached in the
	 * client on startup and after getRemoteVersion() is called.
	 * @return the version.
	 */
	public String getShockVersion() {
		return version;
	}
	
	/** Fetch the version from the Shock server and cache it client side.
	 * @return the version.
	 * @throws IOException if an IO error occurs.
	 * @throws InvalidShockUrlException if the url no longer points to a Shock
	 * server.
	 */
	public String getRemoteVersion() throws IOException,
			InvalidShockUrlException {
		final CloseableHttpResponse response = client.execute(
				new HttpGet(baseurl));
		final Map<String, Object> shockresp;
		try {
			final String resp = EntityUtils.toString(response.getEntity());
			@SuppressWarnings("unchecked")
			Map<String, Object> respobj = mapper.readValue(resp, Map.class);
			shockresp = respobj;
		} catch (JsonParseException jpe) {
			throw new InvalidShockUrlException(baseurl.toString(), jpe);
		} finally {
			response.close();
		}
		if (!shockresp.containsKey("id")) {
			throw new InvalidShockUrlException(baseurl.toString());
		}
		if (!shockresp.get("id").equals("Shock")) {
			throw new InvalidShockUrlException(baseurl.toString());
		}
		version = (String) shockresp.get("version");
		return version;
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
			throw new ShockHttpException(
					response.getStatusLine().getStatusCode(),
					"Invalid Shock response. Server said " +
					response.getStatusLine().getStatusCode() + " " +
					response.getStatusLine().getReasonPhrase() + 
					". JSON parser said " + jpe.getLocalizedMessage(), jpe);
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
			throw new NullPointerException("id may not be null");
		}
		final URI targeturl = nodeurl.resolve(id.getId());
		final HttpGet htg = new HttpGet(targeturl);
		final ShockNode sn = (ShockNode) processRequest
				(htg, ShockNodeResponse.class);
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
				getDownloadURLPrefix());
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
			if (format != null && !format.isEmpty()) {
				mpeb.addTextBody("format", format);
			}
			// causes an error for 0.8.23, makes node immutable
			// doesn't work in 0.9.6 but doesn't break anything
			// works in 0.9.12
			// TODO Add when 0.8 drops support.
//			if (filename != null && !filename.isEmpty()) {
//				mpeb.addTextBody("file_name", filename);
//			}
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
			b = new byte[CHUNK_SIZE]; // could just zero it
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
	
	/** Add users to a node's ACLs.
	 * @param id the node to update.
	 * @param users the users to add to the ACL.
	 * @param aclType the ACL to which the users should be added.
	 * @return the new ACL
	 * @throws TokenExpiredException if the token is expired.
	 * @throws ShockHttpException if a shock error occurs.
	 * @throws IOException if an IO error occurs.
	 */
	public ShockACL addToNodeAcl(
			final ShockNodeId id,
			final List<String> users,
			final ShockACLType aclType)
			throws TokenExpiredException, ShockHttpException, IOException {
		final URI targeturl = checkACLArgsAndGenURI(id, users, aclType);
		final HttpPut htp = new HttpPut(targeturl);
		return (ShockACL) processRequest(htp, ShockACLResponse.class);
	}
	
	/** Remove users to a node's ACLs.
	 * @param id the node to update.
	 * @param users the users to remove from the ACL.
	 * @param aclType the ACL to which the users should be removed.
	 * @return the new ACL.
	 * @throws TokenExpiredException if the token is expired.
	 * @throws ShockHttpException if a shock error occurs.
	 * @throws IOException if an IO error occurs.
	 */
	public ShockACL removeFromNodeAcl(
			final ShockNodeId id,
			final List<String> users,
			final ShockACLType aclType)
			throws TokenExpiredException, ShockHttpException, IOException {
		final URI targeturl = checkACLArgsAndGenURI(id, users, aclType);
		final HttpDelete htd = new HttpDelete(targeturl);
		return (ShockACL) processRequest(htd, ShockACLResponse.class);
	}
	
	// look into sharing shock and awe client code
	private URI checkACLArgsAndGenURI(
			final ShockNodeId id,
			final List<String> users,
			final ShockACLType aclType) {
		if (id == null) {
			throw new NullPointerException("id cannot be null");
		}
		if (users == null || users.isEmpty()) {
			throw new IllegalArgumentException(
					"user list cannot be null or empty");
		}
		if (aclType == null) {
			throw new NullPointerException("aclType cannot be null");
		}
		for (final String user: users) {
			if (user == null || user.equals("")) {
				throw new IllegalArgumentException(
						"user cannot be null or the empty string");
			}
		}
		final URI targeturl = nodeurl.resolve(id.getId() +
				aclType.getUrlFragmentForAcl() + "?users=" +
				StringUtils.join(users, ",") + ";verbosity=full");
		return targeturl;
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
		return getACLs(id, ShockACLType.ALL);
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
		final URI targeturl = nodeurl.resolve(id.getId() +
				acl.getUrlFragmentForAcl() + "?verbosity=full");
		final HttpGet htg = new HttpGet(targeturl);
		return (ShockACL) processRequest(htg, ShockACLResponse.class);
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
