package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.StudentTeamAssignmentEntity;
import org.apache.ibatis.annotations.Param;

public interface StudentTeamAssignmentMapper {
    int insertActiveAssignment(StudentTeamAssignmentEntity assignment);

    int revokeActiveAssignment(@Param("studentUserId") int studentUserId,
                               @Param("teamId") int teamId,
                               @Param("actorUserId") int actorUserId);

    int hasActiveAssignment(@Param("studentUserId") int studentUserId,
                            @Param("teamId") int teamId);
}
