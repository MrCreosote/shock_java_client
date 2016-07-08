package us.kbase.shock.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents one or more of the access control lists (ACLs) for a shock
 * object <b>at the time the ACL(s) were retrieved from shock</b>. 
 * Later updates to the ACLs will not be reflected in the instance.
 * To update the local representation of the ACLs
 * {@link us.kbase.shock.client.BasicShockClient#getACLs(ShockNodeId)
 * getACLs()} must be called again.</p>
 *
 * This class is never instantiated manually.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class ShockACL extends ShockData {

	private ShockUserId owner;
	private List<ShockUserId> read;
	private List<ShockUserId> write;
	private List<ShockUserId> delete;
	@JsonProperty("public")
	private Map<String, Boolean> public_;
	
	private ShockACL(){}

	/**
	 * Get the user ID of the node's owner.
	 * @return the owner ID.
	 */
	public ShockUserId getOwner() {
		return owner;
	}

	/**
	 * Get the list of user IDs that can read the node.
	 * @return the list of IDs or <code>null</code> if the list was not 
	 * included in the server response.
	 */
	public List<ShockUserId> getRead() {
		if (read == null) {return null;}
		return new ArrayList<ShockUserId>(read);
	}

	/**
	 * Get the list of user IDs that can write to the node.
	 * @return the list of IDs or <code>null</code> if the list was not 
	 * included in the server response.
	 */
	public List<ShockUserId> getWrite() {
		if (write == null) {return null;}
		return new ArrayList<ShockUserId>(write);
	}

	/**
	 * Get the list of user IDs that can delete the node.
	 * @return the list of IDs or <code>null</code> if the list was not 
	 * included in the server response.
	 */
	public List<ShockUserId> getDelete() {
		if (delete == null) {return null;}
		return new ArrayList<ShockUserId>(delete);
	}
	
	public boolean isPublicallyReadable() {
		return public_.get(ShockACLType.READ.getType());
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((delete == null) ? 0 : delete.hashCode());
		result = prime * result + ((owner == null) ? 0 : owner.hashCode());
		result = prime * result + ((public_ == null) ? 0 : public_.hashCode());
		result = prime * result + ((read == null) ? 0 : read.hashCode());
		result = prime * result + ((write == null) ? 0 : write.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ShockACL other = (ShockACL) obj;
		if (delete == null) {
			if (other.delete != null)
				return false;
		} else if (!delete.equals(other.delete))
			return false;
		if (owner == null) {
			if (other.owner != null)
				return false;
		} else if (!owner.equals(other.owner))
			return false;
		if (public_ == null) {
			if (other.public_ != null)
				return false;
		} else if (!public_.equals(other.public_))
			return false;
		if (read == null) {
			if (other.read != null)
				return false;
		} else if (!read.equals(other.read))
			return false;
		if (write == null) {
			if (other.write != null)
				return false;
		} else if (!write.equals(other.write))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ShockACL [owner=" + owner + ", read=" + read + ", write="
				+ write + ", delete=" + delete + "]";
	}
}
