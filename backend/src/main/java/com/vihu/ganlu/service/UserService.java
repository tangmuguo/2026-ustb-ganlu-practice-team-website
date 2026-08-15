package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.UserQueryVo;
import com.vihu.ganlu.entitys.StudentProvisionRequest;
import com.vihu.ganlu.entitys.StudentUpdateRequest;
import com.vihu.ganlu.entitys.StudentListItemDto;
import com.vihu.ganlu.entitys.AdminStudentDetailDto;
import com.vihu.ganlu.entitys.StudentVerificationRequest;

import java.util.List;

public interface UserService {
    List<UserEntity> findAllUser();
    UserEntity findUserById(int id);
    List<UserEntity> findUserByLevel(int level);
    List<UserEntity> findUserBigLevel(int level);
    UserEntity authenticate(String username, String rawPassword);
    boolean usernameExists(String username);
    boolean phoneExists(String phone);
    int findCountUserByPage(UserQueryVo vo);
    List<UserEntity> findUserByPage(UserQueryVo vo);
    Integer addUser(UserEntity e);
    Integer updateUserById(UserEntity e);
    Integer deleteUserByIds(List<Integer> ids);
    List<StudentListItemDto> findManageableStudents(UserEntity actor);
    StudentListItemDto provisionStudent(StudentProvisionRequest request, UserEntity actor);
    void updateStudent(int studentId, StudentUpdateRequest request, UserEntity actor);
    void deleteStudents(List<Integer> studentIds, UserEntity actor);
    AdminStudentDetailDto findStudentForAdministrator(int studentId, UserEntity actor);
    void updateStudentVerification(int studentId, StudentVerificationRequest request, UserEntity actor);
    void revokeCurrentSession(UserEntity actor);
}
