package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.entitys.TeamDetailDto;
import com.vihu.ganlu.entitys.TeamYearSummary;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TeamMapper {
    List<TeamYearSummary> findPublishedYears();

    List<TeamDetailDto> findPublishedByYear(@Param("year") String year,
                                            @Param("offset") long offset,
                                            @Param("size") int size);

    long countPublishedByYear(@Param("year") String year);

    TeamEntity findById(@Param("id") int id);

    TeamDetailDto findPublishedById(@Param("id") int id);

    int countByYearAndNameExcludingId(@Param("year") String year,
                                      @Param("name") String name,
                                      @Param("excludeId") Integer excludeId);

    /**
     * 统计指定负责人账号已绑定的小队数量（排除 excludeId 自身，用于更新时校验）。
     * 配合 Patch 12 的 UNIQUE(owner_user_id) 约束，应用层在 create/update 前主动校验，
     * 给出明确的 DuplicateKeyException 而非依赖 DB 约束抛出。
     */
    int countByOwnerUserIdExcludingId(@Param("ownerUserId") int ownerUserId,
                                      @Param("excludeId") Integer excludeId);

    int insertTeam(TeamEntity team);

    int updateTeam(TeamEntity team);

    int archiveTeam(@Param("id") int id);

    // ---- 团队风采内容管理新增 ----

    /**
     * 查找指定用户负责的小队（owner_user_id = userId），用于从 Token 推导 teamId。
     * 团队端权限规则：只要团队未被归档即可（允许 DRAFT 状态准备内容），
     * 与公开端 "PUBLISHED 才可见" 的规则区分开。
     */
    TeamEntity findOwnedTeamByOwnerUserId(@Param("ownerUserId") int ownerUserId);

    /**
     * 查找所有团队（管理员内容管理下拉选择用）。
     */
    List<TeamEntity> findAllTeams();
}
