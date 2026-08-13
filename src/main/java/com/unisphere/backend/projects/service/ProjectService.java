package com.unisphere.backend.projects.service;

import com.unisphere.backend.common.exception.ProjectNotFoundException;
import com.unisphere.backend.common.exception.ProjectRoleNotFoundException;
import com.unisphere.backend.common.exception.InvalidProjectStatusTransitionException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.service.UserService;
import com.unisphere.backend.projects.dto.request.CreateProjectRequest;
import com.unisphere.backend.projects.dto.request.CreateProjectRoleRequest;
import com.unisphere.backend.projects.dto.request.ProjectStatusUpdateRequest;
import com.unisphere.backend.projects.dto.request.UpdateProjectRequest;
import com.unisphere.backend.projects.dto.request.UpdateProjectRoleRequest;
import com.unisphere.backend.projects.dto.response.ProjectActorResponse;
import com.unisphere.backend.projects.dto.response.ProjectMemberResponse;
import com.unisphere.backend.projects.dto.response.ProjectResponse;
import com.unisphere.backend.projects.dto.response.ProjectRoleResponse;
import com.unisphere.backend.projects.dto.response.ProjectSummaryResponse;
import com.unisphere.backend.projects.entity.Project;
import com.unisphere.backend.projects.entity.ProjectMember;
import com.unisphere.backend.projects.entity.ProjectRole;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Project CRUD, role CRUD, the member roster (leave/remove), the browse feed, search, and every
 * response mapping in the module. The private {@code to*} methods at the bottom are the only
 * places a {@link Project}/{@link ProjectRole}/{@link ProjectMember} is turned into a DTO.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectApplicationRepository projectApplicationRepository;
    private final UserRepository userRepository;
    private final MediaUrlResolver mediaUrlResolver;
    private final ProjectAccessService accessService;
    private final ProjectMediaService projectMediaService;
    private final NotificationService notificationService;

    // ── Project writes ──────────────────────────────────────────────────────

    public ProjectResponse createProject(CreateProjectRequest req, User currentUser) {
        accessService.assertCanCreate(currentUser);
        if (req.coverImageKey() != null) {
            projectMediaService.assertOwnedKey(req.coverImageKey(), currentUser);
        }

        Project project = new Project();
        project.setOwnerId(currentUser.getId());
        project.setUniversityId(accessService.viewerUniversityId(currentUser));
        project.setTitle(req.title());
        project.setDescription(req.description());
        project.setCoverImageKey(req.coverImageKey());
        project.setGithubUrl(req.githubUrl());
        project.setDemoUrl(req.demoUrl());
        project.setStatus(ProjectStatus.OPEN);
        Project saved = projectRepository.save(project);

        ProjectMember ownerMembership = new ProjectMember();
        ownerMembership.setProjectId(saved.getId());
        ownerMembership.setUserId(currentUser.getId());
        ownerMembership.setRole(ProjectMemberRole.OWNER);
        projectMemberRepository.save(ownerMembership);

        return toResponse(saved, currentUser);
    }

    public ProjectResponse updateProject(Long projectId, UpdateProjectRequest req, User currentUser) {
        Project project = findActiveProject(projectId);
        accessService.assertCanModify(project, currentUser);

        if (req.title() != null) {
            if (req.title().isBlank()) {
                throw new IllegalArgumentException("title cannot be blank");
            }
            project.setTitle(req.title());
        }
        if (req.description() != null) project.setDescription(blankToNull(req.description()));
        if (req.coverImageKey() != null) {
            String key = blankToNull(req.coverImageKey());
            if (key != null) projectMediaService.assertOwnedKey(key, currentUser);
            project.setCoverImageKey(key);
        }
        if (req.githubUrl() != null) project.setGithubUrl(blankToNull(req.githubUrl()));
        if (req.demoUrl() != null) project.setDemoUrl(blankToNull(req.demoUrl()));
        if (req.isRecruiting() != null) project.setRecruiting(req.isRecruiting());

        return toResponse(projectRepository.save(project), currentUser);
    }

    /** The project lifecycle, in one place. COMPLETED is terminal. */
    public ProjectResponse changeStatus(Long projectId, ProjectStatusUpdateRequest req, User currentUser) {
        Project project = findActiveProject(projectId);
        accessService.assertCanModify(project, currentUser);

        ProjectStatus from = project.getStatus();
        ProjectStatus to = req.status();

        if (from == to) {
            throw new InvalidProjectStatusTransitionException(from, to);
        }

        boolean allowed = switch (from) {
            case OPEN -> to == ProjectStatus.IN_PROGRESS || to == ProjectStatus.COMPLETED;
            case IN_PROGRESS -> to == ProjectStatus.OPEN || to == ProjectStatus.COMPLETED;
            case COMPLETED -> false;
        };
        if (!allowed) {
            throw new InvalidProjectStatusTransitionException(from, to);
        }

        project.setStatus(to);
        // A completed project is no longer recruiting — an explicit re-open (OPEN/IN_PROGRESS again
        // is unreachable from COMPLETED, so this only ever fires once, on the way in.
        if (to == ProjectStatus.COMPLETED) {
            project.setRecruiting(false);
        }

        return toResponse(projectRepository.save(project), currentUser);
    }

    /** Soft delete. Blocked once the project has any non-owner member — see {@code deleteRole} for the analogous role guard. */
    public void deleteProject(Long projectId, User currentUser) {
        Project project = findActiveProject(projectId);
        accessService.assertCanModify(project, currentUser);
        if (projectMemberRepository.countByProjectId(projectId) > 1) {
            throw new IllegalArgumentException(
                    "This project has other members — remove them or mark it COMPLETED instead of deleting it");
        }
        project.setDeletedAt(LocalDateTime.now());
        projectRepository.save(project);
    }

    // ── Project reads ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long projectId, User currentUser) {
        return toResponse(findActiveProject(projectId), currentUser);
    }

    @Transactional(readOnly = true)
    public Page<ProjectSummaryResponse> getFeed(ProjectStatus status, Boolean recruiting, Long universityId,
                                                Long ownerId, Pageable pageable, User currentUser) {
        return toSummaryResponses(
                projectRepository.findFeed(isAdmin(currentUser), accessService.viewerUniversityId(currentUser),
                        status, recruiting, universityId, ownerId, pageable),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<ProjectSummaryResponse> search(String query, Pageable pageable, User currentUser) {
        String sanitized = sanitizeBooleanQuery(query);
        if (sanitized.isEmpty()) {
            return Page.empty(pageable);
        }
        return toSummaryResponses(
                projectRepository.searchFullText(sanitized, isAdmin(currentUser),
                        accessService.viewerUniversityId(currentUser), stripSort(pageable)),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<ProjectSummaryResponse> getMyProjects(ProjectStatus status, Pageable pageable, User currentUser) {
        return toSummaryResponses(projectRepository.findByOwnerId(currentUser.getId(), status, pageable), currentUser);
    }

    @Transactional(readOnly = true)
    public Page<ProjectSummaryResponse> getJoinedProjects(Pageable pageable, User currentUser) {
        return toSummaryResponses(projectRepository.findJoinedByUserId(currentUser.getId(), pageable), currentUser);
    }

    // ── Roles ────────────────────────────────────────────────────────────────

    public ProjectRoleResponse addRole(Long projectId, CreateProjectRoleRequest req, User currentUser) {
        Project project = findActiveProject(projectId);
        accessService.assertCanModify(project, currentUser);

        ProjectRole role = new ProjectRole();
        role.setProjectId(projectId);
        role.setTitle(req.title());
        role.setDescription(req.description());
        role.setSlots(req.slots() != null ? req.slots() : 1);
        return toRoleResponse(projectRoleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public List<ProjectRoleResponse> listRoles(Long projectId, User currentUser) {
        findActiveProject(projectId);
        return projectRoleRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(this::toRoleResponse)
                .toList();
    }

    public ProjectRoleResponse updateRole(Long projectId, Long roleId, UpdateProjectRoleRequest req, User currentUser) {
        Project project = findActiveProject(projectId);
        accessService.assertCanModify(project, currentUser);
        ProjectRole role = findActiveRole(projectId, roleId);

        if (req.title() != null) {
            if (req.title().isBlank()) {
                throw new IllegalArgumentException("title cannot be blank");
            }
            role.setTitle(req.title());
        }
        if (req.description() != null) role.setDescription(blankToNull(req.description()));
        if (req.slots() != null) {
            if (req.slots() < role.getFilledCount()) {
                throw new IllegalArgumentException("slots cannot be less than the number of members already filling this role");
            }
            role.setSlots(req.slots());
        }
        if (req.status() != null) role.setStatus(req.status());

        return toRoleResponse(projectRoleRepository.save(role));
    }

    /** Blocked once the role has ever received an application — its audit trail would otherwise cascade-delete with it. */
    public void deleteRole(Long projectId, Long roleId, User currentUser) {
        Project project = findActiveProject(projectId);
        accessService.assertCanModify(project, currentUser);
        ProjectRole role = findActiveRole(projectId, roleId);
        if (projectApplicationRepository.existsByProjectRoleId(roleId)) {
            throw new IllegalArgumentException(
                    "This role has received applications — close it instead of deleting it");
        }
        projectRoleRepository.delete(role);
    }

    // ── Members ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ProjectMemberResponse> listMembers(Long projectId, Pageable pageable, User currentUser) {
        findActiveProject(projectId);
        Page<ProjectMember> page = projectMemberRepository.findByProjectId(projectId, pageable);
        Map<Long, User> users = loadUsers(page.getContent().stream().map(ProjectMember::getUserId).collect(Collectors.toSet()));
        Map<Long, String> roleTitles = loadRoleTitles(page.getContent().stream()
                .map(ProjectMember::getProjectRoleId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        return page.map(m -> toMemberResponse(m, users.get(m.getUserId()), roleTitles));
    }

    public void leaveProject(Long projectId, User currentUser) {
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("You are not a member of this project"));
        if (member.getRole() == ProjectMemberRole.OWNER) {
            throw new IllegalArgumentException(
                    "The project owner cannot leave — mark the project COMPLETED or delete it instead");
        }
        removeMembership(member);
    }

    /** Owner/admin removing someone else. Never the owner's own row. */
    public void removeMember(Long projectId, Long targetUserId, User currentUser) {
        Project project = findActiveProject(projectId);
        accessService.assertCanModify(project, currentUser);
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("This user is not a member of this project"));
        if (member.getRole() == ProjectMemberRole.OWNER) {
            throw new IllegalArgumentException("Cannot remove the project owner");
        }
        removeMembership(member);
        notificationService.createAndPush(targetUserId, currentUser.getId(),
                NotificationType.PROJECT_MEMBER_REMOVED, projectId, "PROJECT_MEMBER_REMOVED");
    }

    /** Deletes the membership row and, if it filled a role slot, frees that slot back up. */
    private void removeMembership(ProjectMember member) {
        projectMemberRepository.delete(member);
        if (member.getProjectRoleId() != null) {
            projectRoleRepository.findById(member.getProjectRoleId()).ifPresent(role -> {
                role.setFilledCount(Math.max(0, role.getFilledCount() - 1));
                if (role.getStatus() == ProjectRoleStatus.CLOSED && role.getFilledCount() < role.getSlots()) {
                    role.setStatus(ProjectRoleStatus.OPEN);
                }
                projectRoleRepository.save(role);
            });
        }
    }

    // ── Shared with ProjectApplicationService ───────────────────────────────

    Project findActiveProject(Long projectId) {
        return projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    ProjectRole findActiveRole(Long projectId, Long roleId) {
        return projectRoleRepository.findByIdAndProjectId(roleId, projectId)
                .orElseThrow(() -> new ProjectRoleNotFoundException(roleId));
    }

    ProjectResponse toResponse(Project project, User currentUser) {
        PageContext ctx = loadPageContext(List.of(project), currentUser);
        return buildResponse(project, ctx);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    private PageRequest stripSort(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private String sanitizeBooleanQuery(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[+\\-><()~*\"@]", " ").trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Map<Long, User> loadUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<Long, String> loadRoleTitles(Set<Long> roleIds) {
        if (roleIds.isEmpty()) return Map.of();
        return projectRoleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(ProjectRole::getId, ProjectRole::getTitle));
    }

    private ProjectActorResponse toActorResponse(User user) {
        return new ProjectActorResponse(user.getId(), UserService.resolveDisplayName(user),
                mediaUrlResolver.toViewableUrl(user.getAvatarUrl()), user.getRole().name());
    }

    private ProjectActorResponse unknownActor(Long userId) {
        return new ProjectActorResponse(userId, "Unknown", null, "UNKNOWN");
    }

    private ProjectMemberResponse toMemberResponse(ProjectMember member, User user, Map<Long, String> roleTitles) {
        String displayName = user != null ? UserService.resolveDisplayName(user) : "Unknown";
        String avatarUrl = user != null ? mediaUrlResolver.toViewableUrl(user.getAvatarUrl()) : null;
        String roleTitle = member.getProjectRoleId() != null ? roleTitles.get(member.getProjectRoleId()) : null;
        return new ProjectMemberResponse(member.getUserId(), displayName, avatarUrl, member.getRole(),
                member.getProjectRoleId(), roleTitle, member.getJoinedAt());
    }

    private ProjectRoleResponse toRoleResponse(ProjectRole role) {
        return new ProjectRoleResponse(role.getId(), role.getTitle(), role.getDescription(),
                role.getSlots(), role.getFilledCount(), role.getStatus());
    }

    // ── Response mapping ─────────────────────────────────────────────────────

    private Page<ProjectSummaryResponse> toSummaryResponses(Page<Project> projects, User currentUser) {
        PageContext ctx = loadPageContext(projects.getContent(), currentUser);
        return projects.map(p -> toSummaryResponse(p, ctx));
    }

    /**
     * Everything a page of projects needs that isn't on the rows themselves, loaded in three
     * queries regardless of page size: owners, member counts, and open-role counts.
     */
    private record PageContext(User viewer, Map<Long, User> owners,
                               Map<Long, Long> memberCounts, Map<Long, Long> openRoleCounts) {}

    private PageContext loadPageContext(List<Project> projects, User currentUser) {
        if (projects.isEmpty()) return new PageContext(currentUser, Map.of(), Map.of(), Map.of());

        Set<Long> projectIds = projects.stream().map(Project::getId).collect(Collectors.toSet());
        Set<Long> ownerIds = projects.stream().map(Project::getOwnerId).collect(Collectors.toSet());

        Map<Long, Long> memberCounts = projectMemberRepository.countByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(ProjectMemberRepository.ProjectCount::getProjectId,
                        ProjectMemberRepository.ProjectCount::getTotal));
        Map<Long, Long> openRoleCounts = projectRoleRepository.countOpenByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(ProjectRoleRepository.ProjectCount::getProjectId,
                        ProjectRoleRepository.ProjectCount::getTotal));

        return new PageContext(currentUser, loadUsers(ownerIds), memberCounts, openRoleCounts);
    }

    private ProjectResponse buildResponse(Project project, PageContext ctx) {
        List<ProjectRoleResponse> roles = projectRoleRepository.findByProjectIdOrderByCreatedAtAsc(project.getId())
                .stream().map(this::toRoleResponse).toList();
        long memberCount = projectMemberRepository.countByProjectId(project.getId());

        return new ProjectResponse(
                project.getId(),
                resolveOwner(project.getOwnerId(), ctx.owners()),
                project.getTitle(), project.getDescription(),
                mediaUrlResolver.toViewableUrl(project.getCoverImageKey()),
                project.getGithubUrl(), project.getDemoUrl(),
                project.getStatus(), project.isRecruiting(), project.getUniversityId(),
                roles, (int) memberCount,
                accessService.isOwnerOrAdmin(project, ctx.viewer()),
                project.getCreatedAt(), project.getUpdatedAt());
    }

    private ProjectSummaryResponse toSummaryResponse(Project project, PageContext ctx) {
        return new ProjectSummaryResponse(
                project.getId(),
                resolveOwner(project.getOwnerId(), ctx.owners()),
                project.getTitle(),
                mediaUrlResolver.toViewableUrl(project.getCoverImageKey()),
                project.getStatus(), project.isRecruiting(), project.getUniversityId(),
                ctx.memberCounts().getOrDefault(project.getId(), 0L).intValue(),
                ctx.openRoleCounts().getOrDefault(project.getId(), 0L).intValue(),
                accessService.isOwnerOrAdmin(project, ctx.viewer()),
                project.getCreatedAt());
    }

    private ProjectActorResponse resolveOwner(Long ownerId, Map<Long, User> owners) {
        User owner = owners.get(ownerId);
        return owner != null ? toActorResponse(owner) : unknownActor(ownerId);
    }
}
