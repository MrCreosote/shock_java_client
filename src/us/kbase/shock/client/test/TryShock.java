package us.kbase.shock.client.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthUser;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockACL;
import us.kbase.shock.client.ShockACLType;
import us.kbase.shock.client.ShockNode;

public class TryShock {
	
	public static void main(String[] args) throws Exception {
		AuthUser au = AuthService.login("kbasetest", "@Suite525");
		BasicShockClient c = new BasicShockClient(
				new URL("https://dev03.berkeley.kbase.us/services/shock-api"),
				au.getToken(), true);
		System.out.println(c.getShockVersion());
		
		Map<String, Object> attribs = new HashMap<String, Object>();
		attribs.put("foo", "bar");
		attribs.put("baz", Arrays.asList(1,2,3,5,8,13));
		
		String s = "You try that around here, young man, and we'll slit " +
				"your face";
		InputStream is = new ByteArrayInputStream(
				s.getBytes(StandardCharsets.UTF_8));
		
		ShockNode sn = c.addNode(attribs, is, "myfile", "UTF-8");
		System.out.println(sn.getFileInformation());
		
		ShockNode sn2 = c.getNode(sn.getId());
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		sn2.getFile(os);
		System.out.println(new String(os.toByteArray(),
				StandardCharsets.UTF_8));
		
		System.out.println(sn2.getAttributes());
		
		ShockACL acl1 = sn2.addToNodeAcl(Arrays.asList("kbasetest2"),
				ShockACLType.READ);
		System.out.println(acl1.getRead());
		
		ShockACL acl2 = c.getACLs(sn2.getId());
		System.out.println(acl2.getRead());
		
		sn2.delete();
	}

}
