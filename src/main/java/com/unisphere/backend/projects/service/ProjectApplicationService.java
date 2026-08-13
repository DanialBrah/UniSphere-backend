package com.unisphere.backend.projects.service;

import com.unisphere.backend.common.exception.InvalidProjectApplicationTransitionException;
import com.unisphere.backend.common.exception.ProjectApplicationNotFoundException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.service.UserService;
import com.unisphere.backend.projects.dto.request.CreateProjectApplicationRequest;
import com.unisphere.backend.projects.dto.request.ProjectApplicationStatusUpdateRequest;
import com.unisphere.backend.projects.dto.response.ProjectActorResponse;
import com.unisphere.backend.projects.dto.response.ProjectApplicationResponse;
import com.unisphere.backend.projects.entity.Project;
import com.unisphere.backend.projects.entity.ProjectApplication;
import com.unisphere.backend.projects.entity.ProjectMember;
import com.unisphere.backend.projects.entity.ProjectRole;
import com.unisphere.backend.projects.enums.ProjectApplicationStatus;
import com.unisphere.backend.projects.enums.ProjectMemberRole;
import com.unisphere.backend.projects.enums.ProjectRoleStatus;
import com.unisphere.backend.projects.enums.ProjectStatus;
import com.unisphere.backend.projects.repository.ProjectApplicationRepository;
import com.unisphere.backend.projects.repository.ProjectMemberRepository;
import com.unisphere.backend.projects.repository.ProjectRepository;
import com.unisphere.backend.projects.repository.ProjectRoleRepository;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Application lifecycle: apply to a specific role, the combined owner-decision/applicant-withdraw
 * status update, and every read over an application. Mirrors {@code JobApplicationService}.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ProjectApplicationService {

    private final ProjectApplicationRepository projectApplicationRepository;
    private final ProjectRepository projectRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectService projectService;
    private final ProjectAccessService accessService;
    private final UserRepository userRepository;
    private final MediaUrlResolver mediaUrlResolver;
    private final NotificationService notificationService;

    public ProjectApplicationResponse apply(Long projectId, Long roleId, CreateProjectApplicationRequest req,
                                            User currentUser) {
        Project project = projectService.findActiveProject(projectId);
        ProjectRole role = projectService.findActiveRole(projectId, roleId);
        accessService.assertCanApply(currentUser);

        if (project.getStatus() == ProjectStatus.COMPLETED) {
            throw new IllegalArgumentException("This project has already completed and is not recruiting");
        }
        if (!project.isRecruiting()) {
            throw new IllegalArgumentException("This project is not currently recruiting");
        }
        if (role.getStatus() != ProjectRoleStatus.OPEN || role.getFilledCount() >= role.getSlots()) {
            throw new IllegalArgumentException("This role is no longer open");
        }
        if (project.getOwnerId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("You cannot apply to your own project");
        }
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, currentUser.getId())) {
            throw new IllegalArgumentException("You are already a member of this project");
        }
        if (projectApplicationRepository.existsByProjectRoleIdAndApplicantId(roleId, currentUser.getId())) {
            throw new IllegalArgumentException("You have already applied to this role");
        }

        ProjectApplication application = new ProjectApplication();
        application.setProjectId(projectId);
        application.setProjectRoleId(roleId);
        application.setApplicantId(currentUser.getId());
        application.setMessage(req.message());
        application.setStatus(ProjectApplicationStatus.PENDING);

        ProjectApplication saved = projectApplicationRepository.save(application);

        notificationService.createAndPush(project.getOwnerId(), currentUser.getId(),
                NotificationType.PROJECT_APPLICATION_RECEIVED, projectId, "PROJECT_APPLICATION_RECEIVED");

        // currentUser is already loaded — the applicant is always the caller here, so no extra query.
        return toResponse(saved, project.getTitle(), role.getTitle(), resolveApplicant(currentUser));
    }

    /**
     * The client-facing entry point for {@code PATCH /projects/applications/{id}}. Two disjoint
     * actors funnel through here — see {@code ProjectApplicationStatusUpdateRequest}'s javadoc.
     */
    public ProjectApplicationResponse updateApplicationStatus(Long applicationId,
                                                               ProjectApplicationStatusUpdateRequest req,
                                                               User currentUser) {
        ProjectApplication application = findApplication(applicationId);
        Project project = projectService.findActiveProject(application.getProjectId());

        if (req.status() == ProjectApplicationStatus.WITHDRAWN) {
            return withdraw(application, project, req.reason(), currentUser);
        }
        return decide(application, project, req, currentUser);
    }

    private ProjectApplicationResponse withdraw(ProjectApplication application, Project project, String reason,
                                                User currentUser) {
        if (!application.getApplicantId().equals(currentUser.getId())) {
            throw new UnauthorizedActionException("Only the applicant may withdraw their own application");
        }
        if (application.getStatus() != ProjectApplicationStatus.PENDING) {
            throw new InvalidProjectApplicationTransitionException(application.getStatus(), ProjectApplicationStatus.WITHDRAWN);
        }

        application.setStatus(ProjectApplicationStatus.WITHDRAWN);
        application.setDecisionReason(reason);
        application.setWithdrawnAt(LocalDateTime.now());
        ProjectApplication saved = projectApplicationRepository.save(application);

        // Self-action — no notification, same as JobApplicationService.withdraw.
        ProjectRole role = projectService.findActiveRole(project.getId(), application.getProjectRoleId());
        return toResponse(saved, project.getTitle(), role.getTitle(), resolveApplicant(currentUser));
    }

    private ProjectApplicationResponse decide(ProjectApplication application, Project project,
                                              ProjectApplicationStatusUpdateRequest req, User currentUser) {
        accessService.assertCanManageApplication(application, project, currentUser);

        ProjectApplicationStatus from = application.getStatus();
        ProjectApplicationStatus to = req.status();
        if (from != ProjectApplicationStatus.PENDING
                || (to != ProjectApplicationStatus.ACCEPTED && to != ProjectApplicationStatus.REJECTED)) {
            throw new InvalidProjectApplicationTransitionException(from, to);
        }

        ProjectRole role = projectService.findActiveRole(project.getId(), application.getProjectRoleId());
        if (to == ProjectApplicationStatus.ACCEPTED) {
            acceptIntoRole(project, role, application.getApplicantId());
        }

        application.setStatus(to);
        application.setDecisionReason(req.reason());
        application.setReviewedBy(currentUser.getId());
        application.setReviewedAt(LocalDateTime.now());
        ProjectApplication saved = projectApplicationRepository.save(application);

        NotificationType type = to == ProjectApplicationStatus.ACCEPTED
                ? NotificationType.PROJECT_APPLICATION_ACCEPTED
                : NotificationType.PROJECT_APPLICATION_REJECTED;
        notificationService.createAndPush(application.getApplicantId(), currentUser.getId(),
                type, project.getId(), type.name());

        return toResponse(saved, project.getTitle(), role.getTitle(), resolveApplicant(application.getApplicantId()));
    }

    /** Creates the membership row and updates the role's capacity bookkeeping — see migration 020's header note. */
    private void acceptIntoRole(Project project, ProjectRole role, Long applicantId) {
        ProjectMember member = new ProjectMember();
        member.setProjectId(project.getId());
        member.setUserId(applicantId);
        member.setProjectRoleId(role.getId());
        member.setRole(ProjectMemberRole.CONTRIBUTOR);
        projectMemberRepository.save(member);

        role.setFilledCount(role.getFilledCount() + 1);
        if (role.getFilledCount() >= role.getSlots()) {
            role.setStatus(ProjectRoleStatus.CLOSED);
        }
        projectRoleRepository.save(role);
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    /** Applicant roster across every role on a project the caller owns/administers, optionally filtered. */
    @Transactional(readOnly = true)
    public Page<ProjectApplicationResponse> listApplications(Long projectId, Long roleId, ProjectApplicationStatus status,
                                                              Pageable pageable, User currentUser) {
        Project project = projectService.findActiveProject(projectId);
        accessService.assertCanModify(project, currentUser);
        Page<ProjectApplication> page = projectApplicationRepository.findByProject(projectId, roleId, status, pageable);
        Map<Long, User> applicants = loadApplicants(page.getContent());
        Map<Long, String> roleTitles = loadRoleTitles(page.getContent());
        return page.map(a -> toResponse(a, project.getTitle(), roleTitles.get(a.getProjectRoleId()),
                resolveApplicant(a.getApplicantId(), applicants)));
    }

    /** "My applications" across every project — always the caller's own. */
    @Transactional(readOnly = true)
    public Page<ProjectApplicationResponse> myApplications(ProjectApplicationStatus status, Pageable pageable,
                                                            User currentUser) {
        Page<ProjectApplication> page = projectApplicationRepository.findByApplicant(currentUser.getId(), status, pageable);
        Map<Long, String> projectTitles = loadProjectTitles(page.getContent());
        Map<Long, String> roleTitles = loadRoleTitles(page.getContent());
        ProjectActorResponse applicant = resolveApplicant(currentUser);
        return page.map(a -> toResponse(a, projectTitles.get(a.getProjectId()), roleTitles.get(a.getProjectRoleId()), applicant));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ProjectApplication findApplication(Long applicationId) {
        return projectApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ProjectApplicationNotFoundException(applicationId));
    }

    /** Batch-loads every distinct project title on a page of applications — one query, not one per row. */
    private Map<Long, String> loadProjectTitles(List<ProjectApplication> applications) {
        if (applications.isEmpty()) return Map.of();
        Set<Long> projectIds = applications.stream().map(ProjectApplication::getProjectId).collect(Collectors.toSet());
        return projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getTitle));
    }

    private Map<Long, String> loadRoleTitles(List<ProjectApplication> applications) {
        if (applications.isEmpty()) return Map.of();
        Set<Long> roleIds = applications.stream().map(ProjectApplication::getProjectRoleId).collect(Collectors.toSet());
        return projectRoleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(ProjectRole::getId, ProjectRole::getTitle));
    }

    /** Batch-loads every distinct applicant on a page of applications — one query, not one per row. */
    private Map<Long, User> loadApplicants(List<ProjectApplication> applications) {
        if (applications.isEmpty()) return Map.of();
        Set<Long> userIds = applications.stream().map(ProjectApplication::getApplicantId).collect(Collectors.toSet());
        return userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
    }

    private ProjectActorResponse resolveApplicant(User user) {
        return new ProjectActorResponse(user.getId(), UserService.resolveDisplayName(user),
                mediaUrlResolver.toViewableUrl(user.getAvatarUrl()), user.getRole().name());
    }

    /** Batch-map lookup — for a page of applications, paired with {@link #loadApplicants}. */
    private ProjectActorResponse resolveApplicant(Long userId, Map<Long, User> users) {
        User user = users.get(userId);
        return user != null ? resolveApplicant(user) : unknownApplicant(userId);
    }

    /** Single-row lookup — for the write paths where the actor isn't the applicant. */
    private ProjectActorResponse resolveApplicant(Long userId) {
        return userRepository.findById(userId).map(this::resolveApplicant).orElseGet(() -> unknownApplicant(userId));
    }

    private ProjectActorResponse unknownApplicant(Long userId) {
        return new ProjectActorResponse(userId, "Unknown", null, "UNKNOWN");
    }

    private ProjectApplicationResponse toResponse(ProjectApplication application, String projectTitle,
                                                  String roleTitle, ProjectActorResponse applicant) {
        return new ProjectApplicationResponse(
                application.getId(), application.getProjectId(), projectTitle,
                application.getProjectRoleId(), roleTitle, application.getApplicantId(), applicant,
                application.getMessage(), application.getStatus(), application.getDecisionReason(),
                application.getReviewedAt(), application.getWithdrawnAt(), application.getCreatedAt());
    }
}
