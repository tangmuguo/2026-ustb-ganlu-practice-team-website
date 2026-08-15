package com.vihu.ganlu.entitys;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;
@Data
public class TeamPageImageEntity {
    /** 团队成员照片（保留给 /team-content/members 接口）。 */
    public static final int TYPE_MEMBER_PHOTO = 1;

    /** 支教风采照片（/team-content/photos 唯一允许的新建照片类型）。 */
    public static final int TYPE_TEACHING_STYLE_PHOTO = 2;

    Integer id;
    Integer teamId;
    String status;
    String rejectReason;
    String scanStatus;
    String scanDiagnosticStatus;
    Date logDate;
    Integer userId;
    Integer pageId;
    String imageUrl;
    String caption;
    /**
     * 照片备注。该字段为可选字段：未提供时保持 {@code null}，空字符串也可正常持久化。
     */
    String content;
    Integer displayOrder;
    Timestamp createdAt;
    Timestamp updatedAt;
    Integer type;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String imageUploadToken;
    @JsonIgnore
    Integer imageUploadUserId;
}
