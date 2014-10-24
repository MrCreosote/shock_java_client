shock_java_client repo

OVERVIEW
-----------------------------------------
A basic java client for the Shock service. Supports node creation, deletion and
retrieval of a subset of node data, streaming file up/download, viewing
ACLs, and limited ACL modification.

VERSION: 0.0.8 (Released 10/23/2014)
-----------------------------------------

shock_service commit: fc6d2b2e82446ea7b51c118a47f14722d76deef4

NEW FEATURES:
- The constructor now has an option to trust self signed SSL certificates.

UPDATED FEATURES / MAJOR BUG FIXES:
- The URL provided by the Shock server no longer replaces the user provided
  URL on startup.

ANTICIPATED FUTURE DEVELOPMENTS:
- Copy a node.
- ACLs via KBase groups when Shock supports it.

VERSION: 0.0.7 (Released 8/14/2014)
-----------------------------------------

shock_service commit: fc6d2b2e82446ea7b51c118a47f14722d76deef4

NEW FEATURES:
- Modify all node ACLs.

UPDATED FEATURES / MAJOR BUG FIXES:
- Improved error handling when the client does not receive JSON data 
  (for a 502, for example).

ANTICIPATED FUTURE DEVELOPMENTS:
- Copy a node when Shock supports it.
- ACLs via KBase groups when Shock supports it.

VERSION: 0.0.6 (Released 1/24/2014)
-----------------------------------------

shock_service commit: e2549cf9cd17386aa17fedaf0aaffeba6d33a1ba

NEW FEATURES:
- None

UPDATED FEATURES / MAJOR BUG FIXES:
- Constructor tries to create & delete a node to ensure shock is set up
  correctly. Currently if shock cannot auth to mongo it throws
  400 Invalid authorization header or content.

ANTICIPATED FUTURE DEVELOPMENTS:
- Copy a node when Shock supports it.
- ACLs via KBase groups when Shock supports it.

VERSION: 0.0.5 (Released 12/6/2013)
-----------------------------------------

shock_service commit: 08b9f0c6b690cf06c04604165b6534f6580295d5

NEW FEATURES:
- None

UPDATED FEATURES / MAJOR BUG FIXES:
- Cut memory requirements in half based on performance testing of different
	download/upload chunk sizes.

ANTICIPATED FUTURE DEVELOPMENTS:
- Copy a node when Shock supports it.
- ACLs via KBase groups when Shock supports it.

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

ANTICIPATED FUTURE DEVELOPMENTS:
- Copy a node when Shock supports it.
- ACLs via KBase groups when Shock supports it.


VERSION: 0.0.3 (Released 11/24/2013)
-----------------------------------------

shock_service commit: 08b9f0c6b690cf06c04604165b6534f6580295d5

NEW FEATURES:
- Provide a format (e.g. JSON, UTF-8) when uploading a file.

UPDATED FEATURES / MAJOR BUG FIXES:
- None

ANTICIPATED FUTURE DEVELOPMENTS:
- Copy a node when Shock supports it.
- ACLs via KBase groups when Shock supports it.


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

ANTICIPATED FUTURE DEVELOPMENTS:
- Copy a node when Shock supports it.
- ACLs via KBase groups when Shock supports it.


VERSION: 0.0.1 (Released 11/11/2013)
-----------------------------------------

shock_service commit: 931c60b76c00fc9229a338b51905cfbea7d796b7

NEW FEATURES:
- First release.

UPDATED FEATURES / MAJOR BUG FIXES:
- N/A

ANTICIPATED FUTURE DEVELOPMENTS:
- Remove need to specify the file size when uploading a file.
- Use KBase user name rather than email for ACLs.
- Copy a node when Shock supports it.
- ACLs via KBase groups when Shock supports it.

ISSUES:
Since the addNode(file) methods are going to be rewritten in the next release,
test coverage is not complete.