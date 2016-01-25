package us.kbase.shock.client.exceptions;

/**
 * Thrown on an attempt to get a node from shock that doesn't exist.
 * @author gaprice@lbl.gov
 *
 */
public class ShockNoNodeException extends ShockHttpException {

	private static final long serialVersionUID = 1L;
	
	public ShockNoNodeException(int code, String message) {
		super(code, message);
	}
}
