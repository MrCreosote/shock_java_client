package us.kbase.shock.client.exceptions;

@SuppressWarnings("serial")
public class ShockIllegalShareException extends ShockHttpException {

	public ShockIllegalShareException(int code) {
		super(code);
	}

	public ShockIllegalShareException(int code, String message) {
		super(code, message);
	}

	public ShockIllegalShareException(int code, String message, Throwable cause) {
		super(code, message, cause);
	}

	public ShockIllegalShareException(int code, Throwable cause) {
		super(code, cause);
	}

}
