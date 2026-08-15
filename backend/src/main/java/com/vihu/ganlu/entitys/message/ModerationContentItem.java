package com.vihu.ganlu.entitys.message;

import java.util.Date;

/** Safe moderation-queue projection. It never includes account real-name or phone fields. */
public class ModerationContentItem {
    private String contentType;
    private Integer contentId;
    private Integer messageId;
    private Integer userId;
    private String displayName;
    private String content;
    private String contentStatus;
    private Date createTime;

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Integer getContentId() { return contentId; }
    public void setContentId(Integer contentId) { this.contentId = contentId; }
    public Integer getMessageId() { return messageId; }
    public void setMessageId(Integer messageId) { this.messageId = messageId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getContentStatus() { return contentStatus; }
    public void setContentStatus(String contentStatus) { this.contentStatus = contentStatus; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
