package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamPageWordEntity;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamPageWordMapper;
import com.vihu.ganlu.service.TeamPageWordService;
import com.vihu.ganlu.security.file.ChildPrivacyGateService;
import com.vihu.ganlu.security.file.PrivacyAssetType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
@Service
public class TeamPageWordServiceImpl implements TeamPageWordService {
    @Resource
    TeamPageWordMapper teamPageWordMapper;
    @Resource
    TeamMediaMapper teamMediaMapper;
    @Autowired(required = false)
    ChildPrivacyGateService childPrivacyGateService;

    @Override
    public int insertTeamWord(TeamPageWordEntity e) {
        return teamPageWordMapper.insertTeamWord(e);
    }

    @Override
    public List<TeamPageWordEntity> findAllWord(int id) {
        return teamPageWordMapper.findAllWord(id);
    }

    @Override
    public int deleteTeamPageWordByIds(List<Integer> ids) {
        return teamPageWordMapper.deleteTeamPageWordByIds(ids);
    }

    @Override
    public int deleteTeamPageWordByIdsAndUserId(List<Integer> ids, Integer userId) {
        return teamPageWordMapper.deleteTeamPageWordByIdsAndUserId(ids, userId);
    }

    @Override
    public List<TeamPageWordEntity> findByTeamId(int teamId) {
        return teamPageWordMapper.findByTeamId(teamId);
    }

    @Override
    public List<TeamPageWordEntity> findByTeamIdAndStatus(int teamId, String status) {
        return teamPageWordMapper.findByTeamIdAndStatus(teamId, status);
    }

    @Override
    public TeamPageWordEntity findById(int id) {
        return teamPageWordMapper.findById(id);
    }

    @Override
    @Transactional
    public boolean archiveById(int id) {
        TeamPageWordEntity e = teamPageWordMapper.findByIdForUpdate(id);
        if (e == null) return false;
        int n = teamPageWordMapper.archiveById(id);
        if (n > 0 && e != null && e.getTeamId() != null) {
            teamMediaMapper.archiveByRelated("WORD", id, e.getTeamId()); // 级联归档关联 media
        }
        return n > 0;
    }

    @Override
    @Transactional
    public boolean archiveByIdAndTeamId(int id, int teamId) {
        TeamPageWordEntity e = teamPageWordMapper.findByIdForUpdate(id);
        if (e == null || !Integer.valueOf(teamId).equals(e.getTeamId())) return false;
        int n = teamPageWordMapper.archiveByIdAndTeamId(id, teamId);
        if (n > 0) {
            teamMediaMapper.archiveByRelated("WORD", id, teamId); // 级联归档关联 media
        }
        return n > 0;
    }

    @Override
    @Transactional
    public boolean updateStatus(int id, String status, String rejectReason) {
        TeamPageWordEntity e = teamPageWordMapper.findByIdForUpdate(id);
        if (e == null) return false;
        // Type 4 is the classroom-log content path. Publication is denied
        // unless the current subject has an explicit guardian consent row.
        if ("PUBLISHED".equals(status) && e.getType() != null && e.getType() == 4) {
            if (childPrivacyGateService == null) {
                throw new com.vihu.ganlu.security.file.MissingPrivacyConsentException(
                        "儿童课堂日志授权门禁不可用，禁止发布");
            }
            childPrivacyGateService.requirePublicationAllowed(
                    PrivacyAssetType.CLASSROOM_LOG,
                    e.getId() == null ? null : e.getId().longValue(),
                    e.getUserId(), null);
        }
        int n = teamPageWordMapper.updateWordStatus(id, status, rejectReason);
        // 父内容被驳回/归档时，级联隐藏关联附件；父内容发布时不自动提升附件，
        // 附件需保持自身的独立审核结果，避免已驳回/已归档附件被复活。
        if (n > 0 && e != null && e.getTeamId() != null
                && ("REJECTED".equals(status) || "ARCHIVED".equals(status))) {
            teamMediaMapper.updateStatusByRelated("WORD", id, status, e.getTeamId());
        }
        return n > 0;
    }
}
