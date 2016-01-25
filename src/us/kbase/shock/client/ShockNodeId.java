package us.kbase.shock.client;

import java.util.regex.Pattern;

/**
 * Represents a shock node ID.
 * @author gaprice@lbl.gov
 *
 */
public class ShockNodeId {
	
	//8-4-4-4-12
	private static final Pattern UUID =
			Pattern.compile("[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}");

	private final String id;

	/**
	 * Constructs a shock node ID.
	 * @param id the shock node ID.
	 * @throws IllegalArgumentException if the ID is not a valid shock ID.
	 */
	public ShockNodeId(String id) throws IllegalArgumentException {
		if (!UUID.matcher(id).matches()) {
			throw new IllegalArgumentException("id must be a UUID hex string");
		}
		this.id = id;
	}
		
	/**
	 * Returns the ID string.
	 * @return the ID string.
	 */
	public String getId() {
		return id;
	}

	@Override
	public String toString() {
		return getClass().getName() + " [id=" + id + "]";
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
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
		ShockNodeId other = (ShockNodeId) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
}
