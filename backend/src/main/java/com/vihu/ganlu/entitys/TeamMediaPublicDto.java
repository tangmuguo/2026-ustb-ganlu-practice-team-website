package com.vihu.ganlu.entitys;

import lombok.Data;

/**
 * 公开端媒体附件 DTO（脱敏）。
 * 仅暴露公开展示所需字段，不返回 relativePath / uploaderId 等内部字段。
 */
@Data
public class TeamMediaPublicDto {
    Integer id;
    String filename;
    String mimeType;
    Long fileSize;
    String relatedType;
    Integer relatedId;

    public static TeamMediaPublicDto from(TeamMediaEntity e) {
        if (e == null) return null;
        TeamMediaPublicDto dto = new TeamMediaPublicDto();
        dto.id = e.getId();
        dto.filename = e.getFilename();
        dto.mimeType = e.getMimeType();
        dto.fileSize = e.getFileSize();
        dto.relatedType = e.getRelatedType();
        dto.relatedId = e.getRelatedId();
        return dto;
    }
}
