VERSION: 0.1.0 (Released TBD)
-----------------------------

BACKWARDS INCOMPATIBILITIES:

- Major rewrite for cross compatibility between Shock and https://github.com/kbase/blobstore.
- A file is now required for node creation.
- The length of the file is required for node creation. In the case of the blobstore, if the
  length is incorrect the file will fail to save.
- Attributes are not supported.
- Indexes are not supported.

VERSION: 0.0.16 (Released 3/3/2019)
-----------------------------------

NEW FEATURES:
- Added the copyNode function.

UPDATED FEATURES / MAJOR BUG FIXES:
- Fix a bug getting ACLs from a read-only node.

VERSION: 0.0.15 (Released 2/3/2017)
-----------------------------------------

NEW FEATURES:
- N/A

UPDATED FEATURES / MAJOR BUG FIXES:
- Node attributes are now accepted and returned as Objects instead of Maps.

VERSION: 0.0.14 (Released 7/31/2016)
-----------------------------------------

NEW FEATURES:
- N/A

UPDATED FEATURES / MAJOR BUG FIXES:
- Updated to the latest auth and java common libraries.

VERSION: 0.0.13 (Released 7/7/2016)
-----------------------------------------

BACKWARDS INCOMPATIBILIES:
- removed the getACLs(ShockNodeId, ShockAclType) method. Use
  getACLs(ShockNodeId).

NEW FEATURES:
- Added methods for setting and getting a node's public readability.

UPDATED FEATURES / MAJOR BUG FIXES:
- Setting the filename in the addNode() methods will now work for supported
  Shock versions (0.9.6 and up).
- The input stream returned by getFile()'s close method now prevents further
  reads and releases any data cached in memory for garbage collection.

VERSION: 0.0.12 (Released 7/3/2016)
-----------------------------------------

BACKWARDS INCOMPATIBILITIES:
- Dropped support for 0.8.X Shock versions.

NEW FEATURES:
- Added a getFile() method that returns an InputStream.

UPDATED FEATURES / MAJOR BUG FIXES:
- Setting the filename in the addNode() methods will now work for Shock
  versions that support it correctly.

VERSION: 0.0.11 (Released 6/10/2016)
-----------------------------------------

NEW FEATURES:
- Added getRemoteVersion() function to the client. This will allow detection
  of shock upgrades, but more usefully makes a cheap call to shock to ensure
  it's up.

UPDATED FEATURES / MAJOR BUG FIXES:
- None

VERSION: 0.0.10 (Released 1/26/2016)
-----------------------------------------

NEW FEATURES:
- Add compatibility with Shock 0.9.13.

UPDATED FEATURES / MAJOR BUG FIXES:
- None

VERSION: 0.0.9 (Released 1/24/2016)
-----------------------------------------

NEW FEATURES:
- Compatible with Shock 0.8.23, 0.9.6, and 0.9.12. Probably compatible with
  other versions but they have not been tested.
- Get the version of the Shock server.

UPDATED FEATURES / MAJOR BUG FIXES:
- ACLs now include the user name as well as the user ID if Shock supports it.
- Methods that modify ACLs now return the new ACLs.

VERSION: 0.0.8 (Released 10/23/2014)
-----------------------------------------

shock_service commit: fc6d2b2e82446ea7b51c118a47f14722d76deef4

NEW FEATURES:
- The constructor now has an option to trust self signed SSL certificates.

UPDATED FEATURES / MAJOR BUG FIXES:
- The URL provided by the Shock server no longer replaces the user provided
  URL on startup.

VERSION: 0.0.7 (Released 8/14/2014)
-----------------------------------------

shock_service commit: fc6d2b2e82446ea7b51c118a47f14722d76deef4

NEW FEATURES:
- Modify all node ACLs.

UPDATED FEATURES / MAJOR BUG FIXES:
- Improved error handling when the client does not receive JSON data 
  (for a 502, for example).

VERSION: 0.0.6 (Released 1/24/2014)
-----------------------------------------

shock_service commit: e2549cf9cd17386aa17fedaf0aaffeba6d33a1ba

NEW FEATURES:
- None

UPDATED FEATURES / MAJOR BUG FIXES:
- Constructor tries to create & delete a node to ensure shock is set up
  correctly. Currently if shock cannot auth to mongo it throws
  400 Invalid authorization header or content.

VERSION: 0.0.5 (Released 12/6/2013)
-----------------------------------------

shock_service commit: 08b9f0c6b690cf06c04604165b6534f6580295d5

NEW FEATURES:
- None

UPDATED FEATURES / MAJOR BUG FIXES:
- Cut memory requirements in half based on performance testing of different
	download/upload chunk sizes.

VERSION: 0.0.4 (Released 12/3/2013)
-----------------------------------------

shock_service commit: 08b9f0c6b690cf06c04604165b6534f6580295d5

NEW FEATURES:
- None

UPDATED FEATURES / MAJOR BUG FIXES:
- Fixed bug causing failures in multi-threaded environments
	- Requires update of Apache HTTP libs to 4.3+
- removed setNodeWorldReadable() in anticipation of the upcoming Shock ACL
	changes

VERSION: 0.0.3 (Released 11/24/2013)
-----------------------------------------

shock_service commit: 08b9f0c6b690cf06c04604165b6534f6580295d5

NEW FEATURES:
- Provide a format (e.g. JSON, UTF-8) when uploading a file.

UPDATED FEATURES / MAJOR BUG FIXES:
- None

VERSION: 0.0.2 (Released 11/12/2013)
-----------------------------------------

shock_service commit: 08b9f0c6b690cf06c04604165b6534f6580295d5

NEW FEATURES:
- No need to specify file size when uploading a file.
- ACLs are now set by user, not by email address, so the AuthUser class is
	not necessary.

UPDATED FEATURES / MAJOR BUG FIXES:
- Fixed a rounding bug such that retrieved files where the size % 100,000,000
	is > 0 but close to 1 are truncated.

VERSION: 0.0.1 (Released 11/11/2013)
-----------------------------------------

shock_service commit: 931c60b76c00fc9229a338b51905cfbea7d796b7

NEW FEATURES:
- First release.

UPDATED FEATURES / MAJOR BUG FIXES:
- N/A
