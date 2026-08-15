package com.vihu.ganlu.entitys;

/** Administrator-only detail view. Do not reuse this type in a public or team response. */
public class AdminStudentDetailDto {
    private Integer id;
    private String username;
    private String displayName;
    private String realname;
    private String belongschool;
    private String grade;
    private String verificationStatus;
    private String guardianConsentStatus;
    private String phone;
    private String verificationMethod;
    private java.util.Date verifiedAt;
    private java.util.Date guardianConsentedAt;

    public static AdminStudentDetailDto from(UserEntity user) {
        AdminStudentDetailDto result = new AdminStudentDetailDto();
        result.id = user.getId();
        result.username = user.getUsername();
        result.displayName = UserSummary.displayNameFor(user);
        result.realname = user.getRealname();
        result.belongschool = user.getBelongschool();
        result.grade = user.getGrade();
        result.verificationStatus = user.getVerificationStatus();
        result.guardianConsentStatus = user.getGuardianConsentStatus();
        // Keep sensitive data in this deliberately separate response type.
        result.phone = user.getPhone();
        result.verificationMethod = user.getVerificationMethod();
        result.verifiedAt = user.getVerifiedAt();
        result.guardianConsentedAt = user.getGuardianConsentedAt();
        return result;
    }

    public Integer getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getRealname() { return realname; }
    public String getBelongschool() { return belongschool; }
    public String getGrade() { return grade; }
    public String getVerificationStatus() { return verificationStatus; }
    public String getGuardianConsentStatus() { return guardianConsentStatus; }
    public String getPhone() { return phone; }
    public String getVerificationMethod() { return verificationMethod; }
    public java.util.Date getVerifiedAt() { return verifiedAt; }
    public java.util.Date getGuardianConsentedAt() { return guardianConsentedAt; }
}
