Basic java client for the Shock service. Supports node creation, deletion and
retrieval of a subset of node data, streaming file up/download, viewing
ACLs, and limited ACL modification.

Basic Usage
-----------

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
			AuthUser au = AuthService.login("kbasetest", [redacted]);
			BasicShockClient c = new BasicShockClient(
					new URL("https://ci.kbase.us/services/shock-api"),
					au.getToken());
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
			
			ShockACL acl2 = c.getACLs(sn2.getId(), ShockACLType.READ);
			System.out.println(acl2.getRead());
			
			sn2.delete();
		}
	
	}
	
Output:

	0.9.6
	ShockFileInformation [checksum={md5=42568d7a6f0b1d81eb3cc2ff53978f74}, name=myfile, format=UTF-8, size=61]
	You try that around here, young man, and we'll slit your face
	{baz=[1, 2, 3, 5, 8, 13], foo=bar}
	[ShockUserId [uuid=23bf5737-85c5-4b5d-98dd-d5e9848d8d32, username=kbasetest],
	 ShockUserId [uuid=0038a5e0-67c2-4ce5-b911-b6873b234788, username=kbasetest2]]
	[ShockUserId [uuid=23bf5737-85c5-4b5d-98dd-d5e9848d8d32, username=kbasetest],
	 ShockUserId [uuid=0038a5e0-67c2-4ce5-b911-b6873b234788, username=kbasetest2]]


See the Javadocs for more information.

Compatibility
-------------

Tested against Shock 0.8.23, 0.9.6, and 0.9.12. Some features are unsupported
by earlier versions (see the Javadoc).

Build
-----

This documentation assumes the build occurs on Ubuntu 12.04LTS,
but things should work similarly on other distributions. It does **not**
assume that the KBase runtime or `dev_container` are installed.

The build requires:

Java JDK 6+ ([install instructions](https://www.digitalocean.com/community/tutorials/how-to-install-java-on-ubuntu-with-apt-get))

[Java ant](http://ant.apache.org):

    sudo apt-get install ant
  
Clone the jars and shock_java_client repos:

    bareubuntu@bu:~/ws$ git clone https://github.com/kbase/jars
    Cloning into 'jars'...
    remote: Counting objects: 1466, done.
    remote: Total 1466 (delta 0), reused 0 (delta 0), pack-reused 1466
    Receiving objects: 100% (1466/1466), 59.43 MiB | 2.43 MiB/s, done.
    Resolving deltas: 100% (626/626), done.

    bareubuntu@bu:~/ws$ git clone https://github.com/kbase/workspace_deluxe
    Cloning into 'workspace_deluxe'...
    remote: Counting objects: 22004, done.
    remote: Compressing objects: 100% (82/82), done.
    remote: Total 22004 (delta 41), reused 0 (delta 0), pack-reused 21921
    Receiving objects: 100% (22004/22004), 21.44 MiB | 2.44 MiB/s, done.
    Resolving deltas: 100% (14000/14000), done.
    
Build:

    bareubuntu@bu:~/ws$ cd workspace_deluxe/
    bareubuntu@bu:~/ws/workspace_deluxe$ make build-docs
    
do ls here

Known issues
------------

- The filename is not set consistently.
- If a client is created such that it trusts self-signed certificates, all
  future clients will also trust all SSCs regardless of the constructor
  arguments. Similarly, creation of a standard client means that any new
  clients will not trust SSCs. 
