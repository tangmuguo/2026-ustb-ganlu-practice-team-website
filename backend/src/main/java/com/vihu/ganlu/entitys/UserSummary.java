package com.vihu.ganlu.entitys;

public class UserSummary {
    private Integer id;
    private String username;
    private String imageUrl;
    private String teamname;
    private String helplocation;
    private String helpschool;
    private String displayName;
    private Integer level;
    private Boolean interactiveContentEnabled;

    public static UserSummary from(UserEntity user) {
        UserSummary summary = new UserSummary();
        summary.id = user.getId();
        summary.username = user.getUsername();
        summary.imageUrl = user.getImageUrl();
        summary.teamname = user.getTeamname();
        summary.helplocation = user.getHelplocation();
        summary.helpschool = user.getHelpschool();
        summary.displayName = displayNameFor(user);
        summary.level = user.getLevel();
        summary.interactiveContentEnabled = user.getLevel() == null || user.getLevel() != 2
                || ("VERIFIED".equals(user.getVerificationStatus())
                && "CONSENTED".equals(user.getGuardianConsentStatus()));
        return summary;
    }

    public Integer getId() { return id; }
    public String getUsername() { return username; }
    public String getImageUrl() { return imageUrl; }
    public String getTeamname() { return teamname; }
    public String getHelplocation() { return helplocation; }
    public String getHelpschool() { return helpschool; }
    public String getDisplayName() { return displayName; }
    public Integer getLevel() { return level; }
    public Boolean getInteractiveContentEnabled() { return interactiveContentEnabled; }

    public static String displayNameFor(UserEntity user) {
        if (user == null) return "已注销用户";
        if (user.getTeamname() != null && !user.getTeamname().trim().isEmpty()) {
            return user.getTeamname().trim();
        }
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName().trim();
        }
        if (user.getLevel() != null && user.getLevel() == 0) {
            return "系统管理员";
        }
        return user.getId() == null ? "注册用户" : "用户#" + user.getId();
    }
}
