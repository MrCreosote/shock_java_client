# Blobstore client

This is a basic client for the [Blobstore](https://github.com/kbase/blobstore), which replaced
Shock some years ago but has a mostly compatible API.

The client mostly exists for backwards compatibility purposes. If you're starting a new project
using the blobstore, you'd probably be fine with a REST client like the Java 11+ HTTP client,
the Jersey client, etc.

The client supports node creation, deletion and retrieval of node data,
streaming file up/download, and viewing and modifying ACLs.

## Including the client in your build

See https://jitpack.io/ for instructions on how to include JitPack built dependencies in your
build.

## JavaDoc

JavaDoc is available at
```
https://javadoc.jitpack.io/com/github/kbase/shock_java_client/<version>/javadoc/
```

For example:

https://javadoc.jitpack.io/com/github/kbase/shock_java_client/0.2.0/javadoc/


## Basic Usage

See the [TryShock](src/test/java/us/kbase/test/shock/client/TryShock.java) example.

## Development

### Adding and releasing code

* Adding code
  * All code additions and updates must be made as pull requests directed at the develop branch.
    * All tests must pass and all new code must be covered by tests.
    * All new code must be documented appropriately
      * Javadoc
      * General documentation if appropriate
      * Release notes
* Releases
  * The main branch is the stable branch. Releases are made from the develop branch to the main
    branch.
  * Tag the version in git and github.
  * Create a github release.
  * Check that the javadoc is appropriately built on JitPack.

### Testing

Copy `test.cfg.example` to `test.cfg` and fill it in appropriately. Then:

```
./gradlew test
```

## Known issues

* If a client is created such that it trusts self-signed certificates, all
  future clients will also trust all SSCs regardless of the constructor
  arguments. Similarly, creation of a standard client means that any new
  clients will not trust SSCs. 
