package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamDetailDto;
import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.entitys.TeamPageEntity;
import com.vihu.ganlu.entitys.TeamSaveRequest;
import com.vihu.ganlu.entitys.TeamYearSummary;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.TeamMapper;
import com.vihu.ganlu.service.TeamPageService;
import com.vihu.ganlu.service.TeamServie;
import com.vihu.ganlu.service.UserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

@Service
public class TeamServieImpl implements TeamServie {
    private static final Pattern YEAR_PATTERN = Pattern.compile("^[0-9]{4}$");
    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2100;
    private static final int MAX_PAGE_SIZE = 100;

    private final TeamMapper teamMapper;
    private final TeamPageService teamPageService;
    private final UserService userService;

    public TeamServieImpl(TeamMapper teamMapper, TeamPageService teamPageService, UserService userService) {
        this.teamMapper = teamMapper;
        this.teamPageService = teamPageService;
        this.userService = userService;
    }

    @Override
    public List<TeamYearSummary> getPublishedYears() {
        return teamMapper.findPublishedYears();
    }

    @Override
    public Map<String, Object> getPublishedTeams(String year, int page, int size) {
        String normalizedYear = validateYear(year);
        validatePagination(page, size);

        long offset = (long) (page - 1) * size;
        List<TeamDetailDto> items = teamMapper.findPublishedByYear(normalizedYear, offset, size);
        long total = teamMapper.countPublishedByYear(normalizedYear);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("size", size);
        result.put("total", total);
        result.put("totalPages", total == 0 ? 0 : (total + size - 1) / size);
        return result;
    }

    @Override
    public TeamDetailDto getPublishedTeamDetail(int teamId) {
        validateTeamId(teamId);
        return teamMapper.findPublishedById(teamId);
    }

    @Override
    @Transactional
    public TeamDetailDto createTeam(TeamSaveRequest request) {
        TeamEntity team = validatedTeam(null, request);
        ensureUniqueName(team.getYear(), team.getName(), null);

        int inserted = teamMapper.insertTeam(team);
        if (inserted != 1 || team.getId() == null) {
            throw new IllegalStateException("创建小队失败");
        }

        TeamPageEntity page = teamPageService.ensureTeamPage(team);
        return detailAfterSave(team, page.getId());
    }

    @Override
    @Transactional
    public TeamDetailDto updateTeam(int teamId, TeamSaveRequest request) {
        validateTeamId(teamId);
        TeamEntity existing = teamMapper.findById(teamId);
        if (existing == null) {
            throw new NoSuchElementException("小队不存在");
        }

        TeamEntity team = validatedTeam(teamId, request);
        ensureUniqueName(team.getYear(), team.getName(), teamId);
        teamMapper.updateTeam(team);

        TeamPageEntity page = teamPageService.ensureTeamPage(team);
        return detailAfterSave(team, page.getId());
    }

    @Override
    @Transactional
    public void archiveTeam(int teamId) {
        validateTeamId(teamId);
        TeamEntity existing = teamMapper.findById(teamId);
        if (existing == null) {
            throw new NoSuchElementException("小队不存在");
        }

        if (existing.getStatus() != TeamEntity.Status.ARCHIVED) {
            teamMapper.archiveTeam(teamId);
            existing.setStatus(TeamEntity.Status.ARCHIVED);
        }
        teamPageService.ensureTeamPage(existing);
    }

    private TeamEntity validatedTeam(Integer teamId, TeamSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }

        TeamEntity team = new TeamEntity();
        team.setId(teamId);
        team.setYear(validateYear(request.getYear()));
        team.setName(requiredText(request.getName(), "小队名称", 100));
        team.setRegion(requiredText(request.getRegion(), "支教地区", 100));
        team.setSchool(requiredText(request.getSchool(), "支教学校", 150));
        team.setDescription(optionalText(request.getDescription(), "小队简介", 2000));
        team.setCoverUrl(optionalText(request.getCoverUrl(), "封面地址", 512));
        team.setStatus(parseStatus(request.getStatus()));

        Integer ownerUserId = request.getOwnerUserId();
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("负责人账号不能为空");
        }
        UserEntity owner = userService.findUserById(ownerUserId);
        if (owner == null || owner.getLevel() == null || owner.getLevel() != 1) {
            throw new IllegalArgumentException("负责人账号必须是有效的甘露团队账号");
        }
        team.setOwnerUserId(ownerUserId);
        return team;
    }

    private void ensureUniqueName(String year, String name, Integer excludeId) {
        if (teamMapper.countByYearAndNameExcludingId(year, name, excludeId) > 0) {
            throw new DuplicateKeyException("同一年份下已存在同名小队");
        }
    }

    private TeamDetailDto detailAfterSave(TeamEntity submitted, Integer pageId) {
        TeamEntity saved = teamMapper.findById(submitted.getId());
        return TeamDetailDto.from(saved == null ? submitted : saved, pageId);
    }

    private String validateYear(String year) {
        String normalized = requiredText(year, "年份", 4);
        if (!YEAR_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("年份必须是4位数字");
        }
        int numericYear = Integer.parseInt(normalized);
        if (numericYear < MIN_YEAR || numericYear > MAX_YEAR) {
            throw new IllegalArgumentException("年份必须在1900至2100之间");
        }
        return normalized;
    }

    private void validatePagination(int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于等于1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("每页数量必须在1至100之间");
        }
    }

    private void validateTeamId(int teamId) {
        if (teamId <= 0) {
            throw new IllegalArgumentException("小队ID必须大于0");
        }
    }

    private TeamEntity.Status parseStatus(String status) {
        String normalized = status == null || status.trim().isEmpty()
                ? TeamEntity.Status.DRAFT.name()
                : status.trim().toUpperCase(Locale.ROOT);
        try {
            return TeamEntity.Status.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("状态只能是DRAFT、PUBLISHED或ARCHIVED");
        }
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        String normalized = optionalText(value, fieldName, maxLength);
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }
}
