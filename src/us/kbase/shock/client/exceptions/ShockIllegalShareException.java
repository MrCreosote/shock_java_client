package us.kbase.shock.client.exceptions;

@SuppressWarnings("serial")
public class ShockIllegalShareException extends ShockHttpException {


	public ShockIllegalShareException(int code, String message) {
		super(code, message);
	}
}
