package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.AdminStudentDetailDto;
import com.vihu.ganlu.entitys.StudentListItemDto;
import com.vihu.ganlu.entitys.StudentProvisionRequest;
import com.vihu.ganlu.entitys.StudentTeamAssignmentEntity;
import com.vihu.ganlu.entitys.StudentUpdateRequest;
import com.vihu.ganlu.entitys.StudentVerificationRequest;
import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.UserQueryVo;
import com.vihu.ganlu.mappers.StudentTeamAssignmentMapper;
import com.vihu.ganlu.mappers.TeamMapper;
import com.vihu.ganlu.mappers.UserMapper;
import com.vihu.ganlu.service.UserService;
import com.vihu.ganlu.utils.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PublicImageLifecycleService imageLifecycleService;
    private final TeamMapper teamMapper;
    private final StudentTeamAssignmentMapper studentTeamAssignmentMapper;

    @Autowired
    public UserServiceImpl(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            PublicImageLifecycleService imageLifecycleService,
            TeamMapper teamMapper,
            StudentTeamAssignmentMapper studentTeamAssignmentMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.imageLifecycleService = imageLifecycleService;
        this.teamMapper = teamMapper;
        this.studentTeamAssignmentMapper = studentTeamAssignmentMapper;
    }

    /** Retained for focused unit tests that do not exercise tenant assignment. */
    public UserServiceImpl(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            PublicImageLifecycleService imageLifecycleService) {
        this(userMapper, passwordEncoder, imageLifecycleService, null, null);
    }

    @Override
    public List<UserEntity> findAllUser() { return userMapper.findAllUser(); }

    @Override
    public UserEntity findUserById(int id) { return userMapper.findUserById(id); }

    @Override
    public List<UserEntity> findUserByLevel(int level) { return userMapper.findUserByLevel(level); }

    @Override
    public List<UserEntity> findUserBigLevel(int level) { return userMapper.findUserBigLevel(level); }

    @Override
    @Transactional
    public UserEntity authenticate(String username, String rawPassword) {
        if (username == null || rawPassword == null) return null;
        UserEntity user = userMapper.findUserByUsername(username.trim());
        if (user == null || user.getPassword() == null) return null;

        String storedPassword = user.getPassword();
        boolean matches;
        if (isBcrypt(storedPassword)) {
            matches = passwordEncoder.matches(rawPassword, storedPassword);
        } else {
            matches = MessageDigest.isEqual(
                    rawPassword.getBytes(StandardCharsets.UTF_8),
                    storedPassword.getBytes(StandardCharsets.UTF_8));
            if (matches) {
                userMapper.updatePasswordById(user.getId(), passwordEncoder.encode(rawPassword));
            }
        }

        return matches ? user : null;
    }

    @Override
    public boolean usernameExists(String username) {
        return username != null && userMapper.countByUsername(username.trim()) > 0;
    }

    @Override
    public boolean phoneExists(String phone) {
        return phone != null && userMapper.countByPhone(phone.trim()) > 0;
    }

    @Override
    public int findCountUserByPage(UserQueryVo vo) { return userMapper.findCountUserByPage(vo); }

    @Override
    public List<UserEntity> findUserByPage(UserQueryVo vo) { return userMapper.findUserByPage(vo); }

    @Override
    public Integer addUser(UserEntity user) {
        if (user == null) throw new IllegalArgumentException("用户信息不能为空");
        if (user.getUsername() == null || user.getUsername().trim().length() < 3
                || user.getUsername().trim().length() > 30) {
            throw new IllegalArgumentException("账号长度应为3到30个字符");
        }
        if (usernameExists(user.getUsername())) throw new ConflictException("账号已存在");
        if (user.getPhone() != null && !user.getPhone().trim().isEmpty() && phoneExists(user.getPhone())) {
            throw new ConflictException("手机号已存在");
        }
        if (user.getPassword() == null || user.getPassword().length() < 8 || user.getPassword().length() > 72) {
            throw new IllegalArgumentException("密码长度应为8到72个字符");
        }
        if (user.getLevel() == null || user.getLevel() < 0 || user.getLevel() > 2) {
            throw new IllegalArgumentException("账号角色不合法");
        }
        user.setUsername(user.getUsername().trim());
        user.setPhone(user.getPhone() == null ? null : user.getPhone().trim());
        if (user.getSessionVersion() == null) user.setSessionVersion(0);
        if (user.getVerificationStatus() == null) {
            user.setVerificationStatus(user.getLevel() == 2 ? "PENDING" : "VERIFIED");
        }
        if (user.getGuardianConsentStatus() == null) {
            user.setGuardianConsentStatus(user.getLevel() == 2 ? "PENDING" : "NOT_REQUIRED");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userMapper.addUser(user);
    }

    @Override
    @Transactional
    public Integer updateUserById(UserEntity user) {
        if (user == null || user.getId() == null) throw new IllegalArgumentException("用户编号不能为空");
        if (user.getUsername() == null || user.getUsername().trim().length() < 3
                || user.getUsername().trim().length() > 30) {
            throw new IllegalArgumentException("账号长度应为3到30个字符");
        }
        user.setUsername(user.getUsername().trim());
        String submittedPassword = user.getPassword();
        if (submittedPassword == null || submittedPassword.trim().isEmpty()) {
            user.setPassword(null);
        } else if (submittedPassword.length() < 8 || submittedPassword.length() > 72) {
            throw new IllegalArgumentException("密码长度应为8到72个字符");
        } else {
            // 更新接口只接收明文密码。即使输入伪装成 BCrypt，也必须重新哈希。
            user.setPassword(passwordEncoder.encode(submittedPassword));
        }
        UserEntity existing = userMapper.findUserByIdForUpdate(user.getId());
        String oldImagePath = existing == null ? null : existing.getImageUrl();
        boolean replacingImage = hasImageUploadToken(user);
        if (replacingImage) {
            if (user.getImageUploadUserId() == null) {
                throw new IllegalArgumentException("图片上传用户不正确");
            }
            imageLifecycleService.deletePublicImageAfterCommit(oldImagePath);
            user.setImageUrl(imageLifecycleService.promote(
                    user.getImageUploadUserId(), user.getImageUploadToken()));
        } else {
            imageLifecycleService.requireManagedImageAsset(oldImagePath);
            user.setImageUrl(oldImagePath);
        }
        int updated = userMapper.updateUserById(user);
        if (updated != 1 && replacingImage) throw new IllegalStateException("更新用户图片失败");
        return updated;
    }

    @Override
    @Transactional
    public Integer deleteUserByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("用户编号不能为空");
        List<Integer> lockedIds = ids.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
        if (lockedIds.isEmpty()) throw new IllegalArgumentException("用户编号不能为空");
        List<UserEntity> existingUsers = new java.util.ArrayList<>();
        for (Integer id : lockedIds) {
            UserEntity existing = userMapper.findUserByIdForUpdate(id);
            if (existing != null) existingUsers.add(existing);
        }
        if (userMapper.countTeamBindingsByUserIds(lockedIds) > 0) {
            throw new ConflictException("所选团队账号已绑定团队内容，请先归档、迁移或解绑团队后再删除");
        }
        final int deleted;
        try {
            deleted = userMapper.deleteUserByIds(lockedIds);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("所选账号仍被业务数据引用，请先归档、迁移或解绑后再删除");
        }
        if (deleted > 0) {
            for (UserEntity existing : existingUsers) {
                imageLifecycleService.deletePublicImageAfterCommit(existing.getImageUrl());
            }
        }
        return deleted;
    }

    @Override
    public List<StudentListItemDto> findManageableStudents(UserEntity actor) {
        if (actor == null || actor.getId() == null || actor.getLevel() == null) {
            throw new SecurityException("请先登录");
        }
        List<UserEntity> students;
        if (actor.getLevel() == 0) {
            students = userMapper.findUserByLevel(2);
        } else {
            int teamId = resolveManagedTeamId(actor, null);
            students = userMapper.findStudentsByActiveTeam(teamId);
        }
        return students.stream().map(StudentListItemDto::from)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional
    public StudentListItemDto provisionStudent(StudentProvisionRequest request, UserEntity actor) {
        if (request == null) throw new IllegalArgumentException("学生信息不能为空");
        requireMatchingPasswords(request.getPassword(), request.getConfirmPassword());
        if (actor != null && actor.getLevel() != null && actor.getLevel() == 1) {
            requireVerifiedTeamAccount(actor);
        }
        int teamId = resolveManagedTeamId(actor, request.getTeamId());

        UserEntity student = new UserEntity();
        student.setUsername(request.getUsername());
        student.setRealname(trimRequired(request.getRealname(), "真实姓名"));
        student.setBelongschool(trimRequired(request.getBelongschool(), "所属小学"));
        student.setGrade(trimRequired(request.getGrade(), "年级"));
        student.setPhone(trimOptional(request.getPhone()));
        student.setDisplayName(trimOptional(request.getDisplayName()));
        student.setPassword(request.getPassword());
        student.setLevel(2);
        // Offline verification and guardian consent must be recorded by an authorized administrator.
        student.setVerificationStatus("PENDING");
        student.setGuardianConsentStatus("PENDING");
        student.setSessionVersion(0);
        addUser(student);
        if (student.getId() == null || student.getId() < 1) {
            throw new IllegalStateException("学生账号创建失败");
        }

        StudentTeamAssignmentEntity assignment = new StudentTeamAssignmentEntity();
        assignment.setStudentUserId(student.getId());
        assignment.setTeamId(teamId);
        assignment.setAssignedByUserId(actor.getId());
        assignment.setScope("MANAGE");
        if (studentTeamAssignmentMapper.insertActiveAssignment(assignment) != 1) {
            throw new IllegalStateException("学生归属记录创建失败");
        }
        return StudentListItemDto.from(student);
    }

    @Override
    @Transactional
    public void updateStudent(int studentId, StudentUpdateRequest request, UserEntity actor) {
        if (request == null) throw new IllegalArgumentException("学生信息不能为空");
        if (actor == null || actor.getId() == null || actor.getLevel() == null) {
            throw new SecurityException("请先登录");
        }
        UserEntity existing;
        if (actor.getLevel() != null && actor.getLevel() == 0) {
            existing = userMapper.findUserByIdForUpdate(studentId);
            if (existing == null || existing.getLevel() == null || existing.getLevel() != 2) {
                throw new java.util.NoSuchElementException("学生账号不存在");
            }
            // Administrators are allowed to manage a student regardless of current assignment.
            TeamEntity assignedTeam = findAnyActiveStudentTeam(studentId);
            if (assignedTeam == null) {
                throw new IllegalStateException("学生尚未完成团队归属，不能修改");
            }
            int teamId = assignedTeam.getId();
            updateStudentRecord(studentId, request, teamId);
        } else {
            int teamId = resolveManagedTeamId(actor, null);
            existing = userMapper.findStudentByIdForTeamForUpdate(studentId, teamId);
            if (existing == null) throw new SecurityException("无权管理该学生账号");
            updateStudentRecord(studentId, request, teamId);
        }
    }

    private void updateStudentRecord(int studentId, StudentUpdateRequest request, int teamId) {
        UserEntity student = new UserEntity();
        student.setId(studentId);
        student.setUsername(request.getUsername().trim());
        student.setRealname(trimRequired(request.getRealname(), "真实姓名"));
        student.setBelongschool(trimRequired(request.getBelongschool(), "所属小学"));
        student.setGrade(trimRequired(request.getGrade(), "年级"));
        student.setPhone(trimOptional(request.getPhone()));
        student.setDisplayName(trimOptional(request.getDisplayName()));
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            if (request.getPassword().length() < 8 || request.getPassword().length() > 72) {
                throw new IllegalArgumentException("密码长度应为8到72个字符");
            }
            student.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (userMapper.updateStudentByIdForTeam(student, teamId) != 1) {
            throw new SecurityException("无权管理该学生账号");
        }
    }

    @Override
    @Transactional
    public void deleteStudents(List<Integer> studentIds, UserEntity actor) {
        if (studentIds == null || studentIds.isEmpty()) {
            throw new IllegalArgumentException("请选择要删除的学生账号");
        }
        if (actor == null || actor.getId() == null || actor.getLevel() == null) {
            throw new SecurityException("请先登录");
        }
        List<Integer> ids = studentIds.stream().filter(java.util.Objects::nonNull)
                .distinct().sorted().collect(java.util.stream.Collectors.toList());
        if (ids.isEmpty()) throw new IllegalArgumentException("请选择要删除的学生账号");
        if (actor.getLevel() != null && actor.getLevel() == 0) {
            for (Integer id : ids) {
                TeamEntity assignedTeam = findAnyActiveStudentTeam(id);
                if (assignedTeam == null) throw new IllegalStateException("学生尚未完成团队归属，不能删除");
                revokeAndDeleteOne(id, assignedTeam.getId(), actor);
            }
            return;
        }
        int teamId = resolveManagedTeamId(actor, null);
        for (Integer id : ids) revokeAndDeleteOne(id, teamId, actor);
    }

    @Override
    public AdminStudentDetailDto findStudentForAdministrator(int studentId, UserEntity actor) {
        requireAdministrator(actor);
        UserEntity student = userMapper.findUserById(studentId);
        if (student == null || student.getLevel() == null || student.getLevel() != 2) {
            throw new java.util.NoSuchElementException("学生账号不存在");
        }
        return AdminStudentDetailDto.from(student);
    }

    @Override
    @Transactional
    public void updateStudentVerification(int studentId, StudentVerificationRequest request, UserEntity actor) {
        requireAdministrator(actor);
        if (request == null) throw new IllegalArgumentException("核验信息不能为空");
        String verificationStatus = request.getVerificationStatus();
        String consentStatus = request.getGuardianConsentStatus();
        if (!isOneOf(verificationStatus, "VERIFIED", "REJECTED", "SUSPENDED")) {
            throw new IllegalArgumentException("核验状态不合法");
        }
        if (!isOneOf(consentStatus, "PENDING", "CONSENTED", "WITHDRAWN")) {
            throw new IllegalArgumentException("监护人授权状态不合法");
        }
        String method = trimOptional(request.getVerificationMethod());
        String guardianVersion = trimOptional(request.getGuardianConsentVersion());
        String privacyVersion = trimOptional(request.getPrivacyConsentVersion());
        String evidenceDigest = trimOptional(request.getEvidenceDigest());
        if ("VERIFIED".equals(verificationStatus) && method == null) {
            throw new IllegalArgumentException("已核验账号必须记录线下核验方式");
        }
        if ("CONSENTED".equals(consentStatus) && guardianVersion == null) {
            throw new IllegalArgumentException("已授权账号必须记录监护人授权版本");
        }
        if (evidenceDigest != null && !evidenceDigest.matches("^[A-Fa-f0-9]{64}$")) {
            throw new IllegalArgumentException("授权凭据摘要格式不正确");
        }
        if (userMapper.updateStudentVerification(studentId, actor.getId(), verificationStatus, method,
                consentStatus, guardianVersion, privacyVersion) != 1) {
            throw new java.util.NoSuchElementException("学生账号不存在");
        }
        if ("CONSENTED".equals(consentStatus) || "WITHDRAWN".equals(consentStatus)) {
            userMapper.insertConsentRecord(studentId, "GUARDIAN", guardianVersion,
                    "CONSENTED".equals(consentStatus), actor.getId(), evidenceDigest);
        }
        if (privacyVersion != null) {
            userMapper.insertConsentRecord(studentId, "PRIVACY", privacyVersion,
                    true, actor.getId(), null);
        }
    }

    @Override
    @Transactional
    public void revokeCurrentSession(UserEntity actor) {
        if (actor == null || actor.getId() == null) throw new SecurityException("请先登录");
        if (userMapper.incrementSessionVersion(actor.getId()) != 1) {
            throw new java.util.NoSuchElementException("账号不存在或已失效");
        }
    }

    private void revokeAndDeleteOne(int studentId, int teamId, UserEntity actor) {
        UserEntity student = userMapper.findStudentByIdForTeamForUpdate(studentId, teamId);
        if (student == null) throw new SecurityException("无权管理该学生账号");
        if (studentTeamAssignmentMapper.revokeActiveAssignment(studentId, teamId, actor.getId()) != 1) {
            throw new SecurityException("无权管理该学生账号");
        }
        // The assignment revocation above is actor-bound in SQL. Deletion is then
        // allowed only when no active assignment remains, so a stale or forged
        // request cannot delete a currently managed student outside that boundary.
        if (userMapper.deleteStudentAfterAssignmentRevoked(studentId) != 1) {
            throw new IllegalStateException("删除学生账号失败");
        }
        imageLifecycleService.deletePublicImageAfterCommit(student.getImageUrl());
    }

    private int resolveManagedTeamId(UserEntity actor, Integer requestedTeamId) {
        if (actor == null || actor.getId() == null || actor.getLevel() == null) {
            throw new SecurityException("请先登录");
        }
        requireStudentDependencies();
        if (actor.getLevel() == 0) {
            if (requestedTeamId == null || requestedTeamId < 1) {
                throw new IllegalArgumentException("管理员创建学生时必须提供所属团队");
            }
            TeamEntity team = teamMapper.findById(requestedTeamId);
            if (!isUsableTeam(team)) {
                throw new IllegalArgumentException("所属团队不存在或已归档");
            }
            return team.getId();
        }
        if (actor.getLevel() != 1) throw new SecurityException("无权管理学生账号");
        TeamEntity team = teamMapper.findOwnedTeamByOwnerUserId(actor.getId());
        if (!isUsableTeam(team)) throw new SecurityException("当前团队账号未绑定有效团队");
        return team.getId();
    }

    private boolean isUsableTeam(TeamEntity team) {
        return team != null
                && team.getId() != null
                && team.getId() > 0
                && team.getStatus() != null
                && team.getStatus() != TeamEntity.Status.ARCHIVED;
    }

    private void requireVerifiedTeamAccount(UserEntity actor) {
        if (!"VERIFIED".equals(actor.getVerificationStatus())) {
            throw new SecurityException("团队账号尚未完成核验");
        }
    }

    private TeamEntity findAnyActiveStudentTeam(int studentId) {
        Integer teamId = userMapper.findActiveTeamIdByStudentId(studentId);
        TeamEntity team = teamId == null ? null : teamMapper.findById(teamId);
        return isUsableTeam(team) ? team : null;
    }

    private void requireStudentDependencies() {
        if (teamMapper == null || studentTeamAssignmentMapper == null) {
            throw new IllegalStateException("学生团队归属模块未配置");
        }
    }

    private void requireAdministrator(UserEntity actor) {
        if (actor == null || actor.getLevel() == null || actor.getLevel() != 0) {
            throw new SecurityException("无访问权限");
        }
    }

    private void requireMatchingPasswords(String password, String confirmPassword) {
        if (password == null || !password.equals(confirmPassword)) {
            throw new IllegalArgumentException("两次输入的密码不一致");
        }
    }

    private String trimRequired(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + "不能为空");
        return value.trim();
    }

    private String trimOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private boolean isOneOf(String value, String... values) {
        if (value == null) return false;
        for (String allowed : values) {
            if (allowed.equals(value)) return true;
        }
        return false;
    }

    private boolean hasImageUploadToken(UserEntity user) {
        return user.getImageUploadToken() != null && !user.getImageUploadToken().trim().isEmpty();
    }

    private boolean isBcrypt(String value) {
        return value != null && value.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }
}
