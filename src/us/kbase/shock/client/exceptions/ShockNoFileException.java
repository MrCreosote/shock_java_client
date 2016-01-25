package us.kbase.shock.client.exceptions;

/**
 * Thrown on an attempt to get a file from a shock node that has no file.
 * @author gaprice@lbl.gov
 *
 */
public class ShockNoFileException extends ShockHttpException {

	private static final long serialVersionUID = 1L;
	
	public ShockNoFileException(int code, String message) {
		super(code, message);
	}
}
