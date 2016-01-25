package us.kbase.shock.client.exceptions;

@SuppressWarnings("serial")
public class ShockIllegalUnshareException extends ShockHttpException {

	public ShockIllegalUnshareException(int code, String message) {
		super(code, message);
	}
}
