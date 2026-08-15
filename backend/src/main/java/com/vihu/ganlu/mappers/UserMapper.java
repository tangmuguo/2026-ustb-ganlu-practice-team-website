package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.UserQueryVo;

import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {

    List<UserEntity> findAllUser();
    UserEntity findUserById(int id);
    UserEntity findUserByIdForUpdate(int id);
    List<UserEntity> findUserByLevel(int level);
    List<UserEntity> findStudentsByActiveTeam(@Param("teamId") int teamId);
    UserEntity findStudentByIdForTeamForUpdate(@Param("studentId") int studentId,
                                               @Param("teamId") int teamId);
    List<UserEntity> findUserBigLevel(int level);
    UserEntity findUserByUsername(@Param("username") String username);
    int countByUsername(@Param("username") String username);
    int countByPhone(@Param("phone") String phone);
    int updatePasswordById(@Param("id") Integer id, @Param("password") String password);
    int findCountUserByPage(UserQueryVo vo);
    List<UserEntity> findUserByPage(UserQueryVo vo);
    Integer addUser(UserEntity e);
    Integer updateUserById(UserEntity e);
    int updateStudentByIdForTeam(@Param("student") UserEntity student,
                                 @Param("teamId") int teamId);
    int deleteStudentsByIdsForTeam(@Param("ids") List<Integer> ids,
                                   @Param("teamId") int teamId);
    int deleteStudentAfterAssignmentRevoked(@Param("studentId") int studentId);
    Integer findActiveTeamIdByStudentId(@Param("studentId") int studentId);
    int updateStudentVerification(@Param("studentId") int studentId,
                                  @Param("actorUserId") int actorUserId,
                                  @Param("verificationStatus") String verificationStatus,
                                  @Param("verificationMethod") String verificationMethod,
                                  @Param("guardianConsentStatus") String guardianConsentStatus,
                                  @Param("guardianConsentVersion") String guardianConsentVersion,
                                  @Param("privacyConsentVersion") String privacyConsentVersion);
    int insertConsentRecord(@Param("studentId") int studentId,
                            @Param("consentType") String consentType,
                            @Param("policyVersion") String policyVersion,
                            @Param("granted") boolean granted,
                            @Param("actorUserId") int actorUserId,
                            @Param("evidenceDigest") String evidenceDigest);
    /**
     * Applies a submitted privacy-rights withdrawal to the existing account
     * gate and invalidates every JWT issued before the change.  The service
     * deliberately exposes no general-purpose user update for this path.
     */
    int withdrawConsentAndInvalidateSession(@Param("userId") int userId,
                                            @Param("consentType") String consentType);
    int incrementSessionVersion(@Param("id") int id);
    int countTeamBindingsByUserIds(@Param("ids") List<Integer> ids);
    Integer deleteUserByIds(List<Integer> ids);

}
