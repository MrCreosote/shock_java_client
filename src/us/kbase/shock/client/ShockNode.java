package us.kbase.shock.client;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import us.kbase.auth.TokenExpiredException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockNodeDeletedException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * <p>Represents a shock node <b>at the time the node was retrieved from shock</b>.
 * Later updates to the node, including updates made via this class' methods,
 * will not be reflected in the instance. To update the local representation
 * of the node {@link us.kbase.shock.client.BasicShockClient#getNode(ShockNodeId)
 * getNode()} must be called again.</p>
 * 
 * <p>This class is never instantiated manually.</p>
 * 
 * Note that mutating the return value of the {@link #getAttributes()} method
 * will alter this object's internal representation of the attributes.
 * 
 * @author gaprice@lbl.gov
 *
 */
// Don't need these and they're undocumented so ignore for now.
// last modified is a particular pain since null is represented as '-' so you
// can't just deserialize to a Date
@JsonIgnoreProperties({"relatives", "type", "indexes", "tags", "linkages",
	"created_on", "last_modified", "public"})
public class ShockNode extends ShockData {

	private Map<String, Object> attributes;
	@JsonProperty("file")
	private ShockFileInformation file;
	private ShockNodeId id;
	private ShockVersionStamp version;
	@JsonIgnore
	private BasicShockClient client;
	@JsonIgnore
	private boolean deleted = false;
	
	private ShockNode(){}
	
	//MUST add a client after object deserialization or many of the methods
	//below will fail
	void addClient(final BasicShockClient client) {
		this.client = client;
	}
	
	private void checkDeleted() {
		if (deleted) {
			throw new ShockNodeDeletedException();
		}
	}

	/**
	 * Get the shock node's attributes. Note mutating the attributes will
	 * mutate the internal representation of the attributes in this object.
	 * @return the shock node's attributes.
	 */
	public Map<String, Object> getAttributes() {
		checkDeleted();
		return attributes;
	}

	/** 
	 * Proxy for {@link BasicShockClient#deleteNode(ShockNodeId) deleteNode()}. 
	 * Deletes this node on the server. All methods will throw an
	 * exception after this method is called.
	 * @throws ShockHttpException if the shock server couldn't delete the node.
	 * @throws IOException if an IO problem occurs.
	 * @throws TokenExpiredException if the client's token has expired.
	 */
	public void delete() throws ShockHttpException, IOException,
			TokenExpiredException {
		client.deleteNode(getId());
		client = null; //remove ref to client
		deleted = true;
	}
	
	/**
	 * Proxy for {@link BasicShockClient#getACLs(ShockNodeId) getACLs()}.
	 * Returns all the access control lists (ACLs) for the node.
	 * @return the ACLs.
	 * @throws ShockHttpException if the shock server cannot retrieve the ACLs.
	 * @throws IOException if an IO problem occurs.
	 * @throws TokenExpiredException if the client's token has expired.
	 */
	@JsonIgnore
	public ShockACL getACLs() throws ShockHttpException, IOException,
			TokenExpiredException {
		checkDeleted();
		return client.getACLs(getId());
	}
	
	/**
	 * Proxy for {@link BasicShockClient#getACLs(ShockNodeId, ShockACLType)
	 * getACLs()}.
	 * Returns the requested access control list (ACL) for the node.
	 * @param acl the type of ACL to retrive from shock.
	 * @return an ACL.
	 * @throws ShockHttpException if the shock server cannot retrieve the ACL.
	 * @throws IOException if an IO problem occurs.
	 * @throws TokenExpiredException if the client's token has expired.
	 */
	@JsonIgnore
	public ShockACL getACLs(final ShockACLType acl) throws ShockHttpException,
			IOException, TokenExpiredException {
		checkDeleted();
		return client.getACLs(getId(), acl);
	}
	
	/**
	 * Proxy for {@link BasicShockClient#addToNodeAcl(ShockNodeId, List,
	 * ShockACLType)}.
	 * Adds the users to the node's access control list(s) (ACL).
	 * @param users the users to which permissions shall be granted.
	 * @param aclType the ACL(s) to which the users shall be added.
	 * @return the new ACL.
	 * @throws ShockHttpException if the read ACL could not be modified.
	 * @throws IOException if an IO problem occurs.
	 * @throws TokenExpiredException if the client's token has expired.
	 */
	@JsonIgnore
	public ShockACL addToNodeAcl(
			final List<String> users,
			final ShockACLType aclType)
			throws ShockHttpException, IOException, TokenExpiredException {
		checkDeleted();
		return client.addToNodeAcl(getId(), users, aclType);
	}
	
	/**
	 * Proxy for {@link BasicShockClient#removeFromNodeAcl(ShockNodeId, List,
	 * ShockACLType)}.
	 * Removes the users from the node's access control list(s) (ACL).
	 * @param users the users from which permissions shall be removed.
	 * @param aclType the ACL(s) from which the users shall be removed.
	 * @return the new ACL.
	 * @throws ShockHttpException if the read ACL could not be modified.
	 * @throws IOException if an IO problem occurs.
	 * @throws TokenExpiredException if the client's token has expired.
	 */
	@JsonIgnore
	public ShockACL removeFromNodeAcl(
			final List<String> users,
			final ShockACLType aclType)
			throws ShockHttpException, IOException, TokenExpiredException {
		checkDeleted();
		return client.removeFromNodeAcl(getId(), users, aclType);
	}
	
	/**
	 * Proxy for {@link BasicShockClient#getFile(ShockNode, OutputStream)
	 * getFile()}.
	 * Gets the file stored at this shock node.
	 * @param file the stream to which the file will be written.
	 * @throws ShockHttpException if the file could not be retrieved from shock.
	 * @throws IOException if an IO problem occurs.
	 * @throws TokenExpiredException if the client's token has expired.
	 */
	@JsonIgnore
	public void getFile(final OutputStream file)
			throws ShockHttpException, IOException, TokenExpiredException {
		checkDeleted();
		client.getFile(this, file);
	}
	
	/**
	 * Get information about the file stored at this node.
	 * @return file information.
	 */
	@JsonIgnore
	public ShockFileInformation getFileInformation() {
		checkDeleted();
		return file;
	}
	
	/**
	 * Get the id of this node.
	 * @return this node's id.
	 */
	public ShockNodeId getId() {
		checkDeleted();
		return id;
	}
	
	/**
	 * Get the version of this node.
	 * @return this node's current version.
	 */
	public ShockVersionStamp getVersion() {
		checkDeleted();
		return version;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ShockNode [attributes=" + attributes + ", file=" + file
				+ ", id=" + id + ", version=" + version + ", deleted=" +
				deleted + "]";
	}
}
