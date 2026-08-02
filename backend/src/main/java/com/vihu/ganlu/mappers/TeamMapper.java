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

    int insertTeam(TeamEntity team);

    int updateTeam(TeamEntity team);

    int archiveTeam(@Param("id") int id);
}
