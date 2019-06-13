package us.kbase.shock.client.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthToken;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockACL;
import us.kbase.shock.client.ShockACLType;
import us.kbase.shock.client.ShockNode;

public class TryShock {
	
	public static void main(String[] args) throws Exception {
		final ConfigurableAuthService auth = new ConfigurableAuthService(new AuthConfig()
				.withKBaseAuthServerURL(new URL(
						"https://appdev.kbase.us/services/auth/api/legacy/KBase/Sessions/Login")));
		final AuthToken t = auth.validateToken(args[0]);
		final BasicShockClient c = new BasicShockClient(
				new URL("https://appdev.kbase.us/services/shock-api"), t);
		System.out.println(c.getShockVersion());
		
		final String s = "You try that around here, young man, and we'll slit your face";
		final InputStream is = new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
		
		final ShockNode sn = c.addNode(is, 60, "myfile", "UTF-8");
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
