Basic java client for the Shock service. Supports node creation, deletion and
retrieval of a subset of node data, streaming file up/download, viewing
ACLs, and limited ACL modification.

Basic Usage
-----------

```java
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
```
	
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

Tested against Shock 0.8.23, 0.9.6, 0.9.12, and 0.9.13. Some features are
unsupported by earlier versions (see the Javadoc).

Build
-----

This documentation assumes the build occurs on Ubuntu 12.04LTS,
but things should work similarly on other distributions. It does **not**
assume that the KBase runtime or `dev_container` are installed.

The build requires:

Java JDK 7+ ([install instructions](https://www.digitalocean.com/community/tutorials/how-to-install-java-on-ubuntu-with-apt-get))

[Java ant](http://ant.apache.org):

    sudo apt-get install ant
  
Clone the jars and shock_java_client repos:

	bareubuntu@bu:~/shockclient$ git clone https://github.com/kbase/jars
	Cloning into 'jars'...
	remote: Counting objects: 1499, done.
	remote: Total 1499 (delta 0), reused 0 (delta 0), pack-reused 1499
	Receiving objects: 100% (1499/1499), 60.22 MiB | 2.22 MiB/s, done.
	Resolving deltas: 100% (645/645), done.

	bareubuntu@bu:~/shockclient$ git clone https://github.com/kbase/shock_java_client
	Cloning into 'shock_java_client'...
	remote: Counting objects: 572, done.
	remote: Total 572 (delta 0), reused 0 (delta 0), pack-reused 572
	Receiving objects: 100% (572/572), 97.59 KiB, done.
	Resolving deltas: 100% (254/254), done.

Build:

	bareubuntu@bu:~/shockclient$ cd shock_java_client/

	bareubuntu@bu:~/shockclient/shock_java_client$ make
	ant compile -Dcompile.jarfile=shock-client-0.0.15
	Buildfile: /home/bareubuntu/shockclient/shock_java_client/build.xml
	
	init:
	    [mkdir] Created dir: /home/bareubuntu/shockclient/shock_java_client/classes
	    [mkdir] Created dir: /home/bareubuntu/shockclient/shock_java_client/docs
	
	compile:
	    [javac] Compiling 25 source files to /home/bareubuntu/shockclient/shock_java_client/classes
	    [javac] warning: [options] bootstrap class path not set in conjunction with -source 1.6
	    [javac] 1 warning
	      [jar] Building jar: /home/bareubuntu/shockclient/shock_java_client/shock-client-0.0.15.jar
	      [jar] Building jar: /home/bareubuntu/shockclient/shock_java_client/shock-client-0.0.15-sources.jar
	
	BUILD SUCCESSFUL
	Total time: 2 seconds
	rm -r docs 
	ant javadoc
	Buildfile: /home/bareubuntu/shockclient/shock_java_client/build.xml
	
	init:
	    [mkdir] Created dir: /home/bareubuntu/shockclient/shock_java_client/docs
	
	javadoc:
	  [javadoc] Generating Javadoc
	  [javadoc] Javadoc execution
	  [javadoc] Creating destination directory: "/home/bareubuntu/shockclient/shock_java_client/docs/javadoc/"
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/BasicShockClient.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/ShockACL.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/ShockACLResponse.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/ShockACLType.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/ShockData.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/ShockFileInformation.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/ShockNode.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/ShockNodeId.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/ShockNodeResponse.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/ShockResponse.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/ShockUserId.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/ShockVersionStamp.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/exceptions/InvalidShockUrlException.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/exceptions/ShockAuthorizationException.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/exceptions/ShockException.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/exceptions/ShockHttpException.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/exceptions/ShockIllegalShareException.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/exceptions/ShockIllegalUnshareException.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/exceptions/ShockNoFileException.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/exceptions/ShockNoNodeException.java...
	  [javadoc] Loading source file /home/bareubuntu/shockclient/shock_java_client/src/us/kbase/shock/client/exceptions/ShockNodeDeletedException.java...
	  [javadoc] Constructing Javadoc information...
	  [javadoc] Standard Doclet version 1.7.0_91
	  [javadoc] Building tree for all the packages and classes...
	  [javadoc] Building index for all the packages and classes...
	  [javadoc] Building index for all classes...
	
	BUILD SUCCESSFUL
	Total time: 4 seconds
	
	bareubuntu@bu:~/shockclient/shock_java_client$ ls
	build.xml     docs        RELEASE_NOTES.md
	classes       internal    shock-client-0.0.15.jar
	COMMANDS      LICENSE.md  shock-client-0.0.15-sources.jar
	DEPENDENCIES  Makefile    src
	deploy.cfg    README.md   test
	
	bareubuntu@bu:~/shockclient/shock_java_client$ ls docs/
	javadoc

Known issues
------------

- The filename is not set consistently.
- If a client is created such that it trusts self-signed certificates, all
  future clients will also trust all SSCs regardless of the constructor
  arguments. Similarly, creation of a standard client means that any new
  clients will not trust SSCs. 
