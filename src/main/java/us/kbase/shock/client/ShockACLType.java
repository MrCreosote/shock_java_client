package us.kbase.shock.client;


/** 
 * <p>Enum that models the type of access control list (ACL) to retrieve from
 * shock.</p>
 * 
 * @author gaprice@lbl.gov
 *
 */
public enum ShockACLType {
	READ ("read"),
	WRITE ("write"),
	OWNER ("owner"),
	DELETE ("delete"),
	ALL ("all");
	
	private final String type;
	
	private ShockACLType(final String type) {
		this.type = type;
	}
	
	String getUrlFragmentForAcl() {
		return "/acl/" + type + "/";
	}
	
	/** 
	 * Returns the ACL type.
	 * @return the type of ACLS this object represents.
	 */
	public String getType() {
		return type;
	}
}
