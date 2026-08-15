package com.vihu.ganlu.entitys;

/** Data a team needs to manage only its assigned students. Phone and consent evidence are never exposed. */
public class StudentListItemDto {
    private Integer id;
    private String username;
    private String displayName;
    private String realname;
    private String belongschool;
    private String grade;
    private String verificationStatus;
    private String guardianConsentStatus;

    public static StudentListItemDto from(UserEntity user) {
        StudentListItemDto result = new StudentListItemDto();
        result.id = user.getId();
        result.username = user.getUsername();
        result.displayName = UserSummary.displayNameFor(user);
        result.realname = user.getRealname();
        result.belongschool = user.getBelongschool();
        result.grade = user.getGrade();
        result.verificationStatus = user.getVerificationStatus();
        result.guardianConsentStatus = user.getGuardianConsentStatus();
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
}
