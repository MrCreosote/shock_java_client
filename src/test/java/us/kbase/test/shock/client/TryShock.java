package us.kbase.test.shock.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import us.kbase.auth.AuthToken;
import us.kbase.auth.client.AuthClient;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockACL;
import us.kbase.shock.client.ShockACLType;
import us.kbase.shock.client.ShockNode;

public class TryShock {
	
	/** Run an example program interacting with the Blobstore, KBase's replacement for Shock.
	 * @param args the CLI args.
	 * @throws Exception if an exception occurs.
	 */
	public static void main(String[] args) throws Exception {
		final AuthToken t = AuthClient.from(new URI("https://appdev.kbase.us/services/auth"))
				.validateToken(args[0]);
		final BasicShockClient c = new BasicShockClient(
				new URL("https://appdev.kbase.us/services/shock-api"), t);
		System.out.println(c.getShockVersion()); // this is not actually the blobstore version
		
		final String s = "You try that around here, young man, and we'll slit your face";
		final InputStream is = new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
		
		final ShockNode sn = c.addNode(is, 61, "myfile", "UTF-8");
		System.out.println(sn.getFileInformation());
		
		final ShockNode sn2 = c.getNode(sn.getId());
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		sn2.getFile(os);
		System.out.println(new String(os.toByteArray(), StandardCharsets.UTF_8));
		
		final ShockACL acl1 = sn2.addToNodeAcl(Arrays.asList("kbasetest2"), ShockACLType.READ);
		System.out.println(acl1.getRead());
		
		final ShockACL acl2 = c.getACLs(sn2.getId());
		System.out.println(acl2.getRead());
		
		sn2.delete();
	}
}
