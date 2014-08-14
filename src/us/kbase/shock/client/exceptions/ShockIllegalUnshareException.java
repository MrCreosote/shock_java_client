package us.kbase.shock.client.exceptions;

@SuppressWarnings("serial")
public class ShockIllegalUnshareException extends ShockHttpException {

	public ShockIllegalUnshareException(int code) {
		super(code);
	}

	public ShockIllegalUnshareException(int code, String message) {
		super(code, message);
	}

	public ShockIllegalUnshareException(int code, String message, Throwable cause) {
		super(code, message, cause);
	}

	public ShockIllegalUnshareException(int code, Throwable cause) {
		super(code, cause);
	}

}
