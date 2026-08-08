package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.VolunteerApplicationEntity;
import com.vihu.ganlu.entitys.VolunteerApplicationRequest;

import java.util.List;

public interface VolunteerApplicationService {
    VolunteerApplicationEntity submit(VolunteerApplicationRequest request);
    List<VolunteerApplicationEntity> findPage(String status, int page, int pageSize);
    int count(String status);
    boolean updateStatus(Long id, String status);
}
