package com.vihu.ganlu.entitys;

public class UserSummary {
    private Integer id;
    private String username;
    private String imageUrl;
    private String teamname;
    private String helplocation;
    private String helpschool;
    private String realname;
    private String belongschool;
    private String grade;
    private String phone;
    private Integer level;

    public static UserSummary from(UserEntity user) {
        UserSummary summary = new UserSummary();
        summary.id = user.getId();
        summary.username = user.getUsername();
        summary.imageUrl = user.getImageUrl();
        summary.teamname = user.getTeamname();
        summary.helplocation = user.getHelplocation();
        summary.helpschool = user.getHelpschool();
        summary.realname = user.getRealname();
        summary.belongschool = user.getBelongschool();
        summary.grade = user.getGrade();
        summary.phone = user.getPhone();
        summary.level = user.getLevel();
        return summary;
    }

    public Integer getId() { return id; }
    public String getUsername() { return username; }
    public String getImageUrl() { return imageUrl; }
    public String getTeamname() { return teamname; }
    public String getHelplocation() { return helplocation; }
    public String getHelpschool() { return helpschool; }
    public String getRealname() { return realname; }
    public String getBelongschool() { return belongschool; }
    public String getGrade() { return grade; }
    public String getPhone() { return phone; }
    public Integer getLevel() { return level; }
}
