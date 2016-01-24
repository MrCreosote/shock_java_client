package us.kbase.shock.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a shock user ID. Cannot be instantiated.
 * @author gaprice@lbl.gov
 *
 */
public class ShockUserId {

	@JsonProperty("uuid")
	private String uuid;
	@JsonProperty("username")
	private String username;
	
	//may use the below in the future, for now just for compatibility
	// if used, update toString, hashCode, equals
	@JsonProperty("fullname")
	private String fullname;
	@JsonProperty("email")
	private String email;
	@JsonProperty("shock_admin")
	private boolean shock_admin;
	
	// for jackson
	private ShockUserId() {}
	// for Shock 0.8.23, Shock 0.9.6 sends full attrib hash
	private ShockUserId(final String id) {
		uuid = id;
	}

	/** Get the user's Shock ID.
	 * @return the user's ID.
	 */
	public String getID() {
		return uuid;
	}

	/** Get the username. Null for shock versions < 0.9.
	 * @return the username.
	 */
	public String getUsername() {
		return username;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ShockUserId [uuid=");
		builder.append(uuid);
		builder.append(", username=");
		builder.append(username);
		builder.append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((username == null) ? 0 : username.hashCode());
		result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
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
		ShockUserId other = (ShockUserId) obj;
		if (username == null) {
			if (other.username != null)
				return false;
		} else if (!username.equals(other.username))
			return false;
		if (uuid == null) {
			if (other.uuid != null)
				return false;
		} else if (!uuid.equals(other.uuid))
			return false;
		return true;
	}
}
