package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.VolunteerApplicationEntity;
import com.vihu.ganlu.entitys.VolunteerApplicationRequest;
import com.vihu.ganlu.mappers.VolunteerApplicationMapper;
import com.vihu.ganlu.service.VolunteerApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VolunteerApplicationServiceImpl implements VolunteerApplicationService {
    private final VolunteerApplicationMapper mapper;

    public VolunteerApplicationServiceImpl(VolunteerApplicationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public VolunteerApplicationEntity submit(VolunteerApplicationRequest request) {
        String phone = request.getPhone().trim();
        if (mapper.countActiveByPhone(phone) > 0) {
            throw new DuplicateApplicationException("该手机号已有正在处理的报名，请勿重复提交");
        }

        VolunteerApplicationEntity entity = new VolunteerApplicationEntity();
        entity.setName(request.getName().trim());
        entity.setPhone(phone);
        entity.setOrganization(request.getOrganization().trim());
        entity.setGradeOrMajor(trimToNull(request.getGradeOrMajor()));
        entity.setPreferredRegion(trimToNull(request.getPreferredRegion()));
        entity.setSkills(trimToNull(request.getSkills()));
        entity.setIntroduction(request.getIntroduction().trim());
        entity.setPrivacyAgreed(true);
        entity.setStatus("PENDING");
        mapper.insert(entity);
        return entity;
    }

    @Override
    public List<VolunteerApplicationEntity> findPage(String status, int page, int pageSize) {
        return mapper.findPage(normalizeStatus(status), (page - 1) * pageSize, pageSize);
    }

    @Override
    public int count(String status) {
        return mapper.count(normalizeStatus(status));
    }

    @Override
    public boolean updateStatus(Long id, String status) {
        if (id == null) throw new IllegalArgumentException("报名编号不能为空");
        String normalized = normalizeStatus(status);
        if (normalized == null) throw new IllegalArgumentException("报名状态不正确");
        return mapper.updateStatus(id, normalized) > 0;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) return null;
        String normalized = status.trim().toUpperCase();
        if (!normalized.matches("PENDING|CONTACTED|ACCEPTED|REJECTED")) {
            throw new IllegalArgumentException("报名状态不正确");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    public static class DuplicateApplicationException extends RuntimeException {
        public DuplicateApplicationException(String message) {
            super(message);
        }
    }
}
