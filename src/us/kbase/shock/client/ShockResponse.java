package us.kbase.shock.client;

import java.util.List;

import us.kbase.shock.client.exceptions.ShockAuthorizationException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockIllegalShareException;
import us.kbase.shock.client.exceptions.ShockIllegalUnshareException;
import us.kbase.shock.client.exceptions.ShockNoFileException;
import us.kbase.shock.client.exceptions.ShockNoNodeException;

abstract class ShockResponse {

	ShockResponse() {}

	// per Jared, the error field will either be null or a list with one error
	// string.
	private List<String> error;
	@SuppressWarnings("unused")
	private ShockData data;
	private int status;

	public String getError() {
		return error.get(0);
	}

	public boolean hasError() {
		return error != null;
	}

	abstract ShockData getShockData() throws ShockHttpException;

	protected void checkErrors() throws ShockHttpException {
		if (hasError()) {
			if (status == 401) {
				throw new ShockAuthorizationException(getStatus(), getError());
			} else if (status == 400) {
				if (getError().equals("Node has no file")) {
					throw new ShockNoFileException(getStatus(), getError());
				} else if (getError().equals("Node does not exist")) {
					throw new ShockNoNodeException(getStatus(),
							// Make response consistent for different versions
							"Node not found");
				} else if (getError().equals(
						"Too many users. Nodes may have only one owner.") ||
						getError().equals(
								"Only the node owner can edit/view node ACL's")
						|| getError().equals(
								"Users that are not node owners can only delete themselves from ACLs.")
						) {
					throw new ShockIllegalShareException(
							getStatus(), getError());
				} else if (getError().equals(
						"Deleting ownership is not a supported request type.")) {
					throw new ShockIllegalUnshareException(
							getStatus(), getError());
				} else {
					throw new ShockHttpException(getStatus(), getError());
				}
			} else if (status == 404) {
				if (getError().equals("Node not found")) {
					throw new ShockNoNodeException(getStatus(), getError());
				} else {
					throw new ShockHttpException(getStatus(), getError());
				}
			} else {
				throw new ShockHttpException(getStatus(), getError());
			}
		}
	}

	public int getStatus() {
		return status;
	}

//	@Override
//	public String toString() {
//		return getClass().getName() + " [error=" + error.get(0) +
//				", data=" + data + ", status=" + status + "]";
//	}
}
