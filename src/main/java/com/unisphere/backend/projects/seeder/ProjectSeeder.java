package com.unisphere.backend.projects.seeder;

import com.unisphere.backend.identity.entity.Alumni;
import com.unisphere.backend.identity.entity.Club;
import com.unisphere.backend.identity.entity.Student;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Seeds sample projects, roles, memberships and applications. Runs after JobSeeder (@Order(8)).
 * Guard: skips entirely if any project already exists.
 *
 * <p>Only {@code student@unisphere.dev} and {@code club@unisphere.dev} are valid project owners
 * among the seeded accounts (STUDENT/ALUMNI/CLUB), and only {@code alumni@unisphere.dev} is a valid
 * applicant besides the owners themselves — so every {@link ProjectApplicationStatus} is
 * demonstrated using that one alumni account applying to four different roles across four projects
 * (the {@code UNIQUE(project_role_id, applicant_id)} constraint only blocks re-applying to the
 * <em>same</em> role, not a different one).
 */
@Slf4j
@Component
@Order(9)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeding.enabled", havingValue = "true", matchIfMissing = false)
public class ProjectSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectApplicationRepository projectApplicationRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (projectRepository.count() > 0) {
            log.info("Projects already seeded — skipping.");
            return;
        }

        Optional<User> studentOpt = userRepository.findByEmail("student@unisphere.dev");
        Optional<User> alumniOpt = userRepository.findByEmail("alumni@unisphere.dev");
        Optional<User> clubOpt = userRepository.findByEmail("club@unisphere.dev");

        if (studentOpt.isEmpty() || alumniOpt.isEmpty() || clubOpt.isEmpty()) {
            log.warn("ProjectSeeder: seeded users not found — run UserSeeder first (app.seeding.enabled=true).");
            return;
        }

        User student = studentOpt.get();
        User alumni = alumniOpt.get();
        User club = clubOpt.get();

        // ── Project A: OPEN, recruiting — one role filled via an ACCEPTED application ──
        Project projectA = baseProject(student, "Campus Marketplace App",
                "A peer-to-peer marketplace for students to buy and sell textbooks and gear.",
                ProjectStatus.OPEN, true, "https://github.com/example/campus-marketplace", null);
        projectRepository.save(projectA);
        addOwnerMembership(projectA, student);

        ProjectRole roleA1 = baseRole(projectA, "Backend Developer", "Spring Boot + MySQL. Own the listings and orders API.", 2);
        ProjectRole roleA2 = baseRole(projectA, "UI/UX Designer", "Design the browse and checkout flows.", 1);
        projectRoleRepository.saveAll(List.of(roleA1, roleA2));

        ProjectApplication acceptedApp = application(projectA, roleA1, alumni, ProjectApplicationStatus.ACCEPTED,
                "I've shipped two Spring Boot APIs before and would love to help.", "Great fit — welcome aboard!");
        roleA1.setFilledCount(1);
        projectRoleRepository.save(roleA1);
        projectApplicationRepository.save(acceptedApp);
        ProjectMember acceptedMember = new ProjectMember();
        acceptedMember.setProjectId(projectA.getId());
        acceptedMember.setUserId(alumni.getId());
        acceptedMember.setProjectRoleId(roleA1.getId());
        acceptedMember.setRole(ProjectMemberRole.CONTRIBUTOR);
        projectMemberRepository.save(acceptedMember);

        // ── Project B: IN_PROGRESS, recruiting — one PENDING application ──────────────
        Project projectB = baseProject(student, "AI Study Buddy Chatbot",
                "A Gemini-powered chatbot that helps students revise from their own lecture notes.",
                ProjectStatus.IN_PROGRESS, true, "https://github.com/example/study-buddy", "https://study-buddy.example.com");
        projectRepository.save(projectB);
        addOwnerMembership(projectB, student);
        ProjectRole roleB1 = baseRole(projectB, "ML Engineer", "Fine-tune retrieval over uploaded lecture notes.", 1);
        projectRoleRepository.save(roleB1);
        projectApplicationRepository.save(
                application(projectB, roleB1, alumni, ProjectApplicationStatus.PENDING,
                        "I've been experimenting with RAG pipelines in my current job — happy to help out.", null));

        // ── Project C: OPEN, recruiting paused — one REJECTED application ─────────────
        Project projectC = baseProject(club, "GDSC Hackathon Portal",
                "Registration and judging portal for the club's annual hackathon.",
                ProjectStatus.OPEN, false, "https://github.com/example/hackathon-portal", null);
        projectRepository.save(projectC);
        addOwnerMembership(projectC, club);
        ProjectRole roleC1 = baseRole(projectC, "Frontend Developer", "React + Tailwind for the judging dashboard.", 3);
        projectRoleRepository.save(roleC1);
        projectApplicationRepository.save(
                application(projectC, roleC1, alumni, ProjectApplicationStatus.REJECTED,
                        "I'd like to help build the judging dashboard.",
                        "Looking for someone with more React experience for this one — thanks for applying!"));

        // ── Project D: COMPLETED — one WITHDRAWN application ──────────────────────────
        Project projectD = baseProject(student, "Campus Event Finder",
                "A map-based directory of campus events. Shipped and no longer recruiting.",
                ProjectStatus.COMPLETED, false, "https://github.com/example/event-finder", "https://events.example.com");
        projectRepository.save(projectD);
        addOwnerMembership(projectD, student);
        ProjectRole roleD1 = baseRole(projectD, "Full-stack Developer", "Help finish the map integration.", 1);
        projectRoleRepository.save(roleD1);
        ProjectApplication withdrawnApp = application(projectD, roleD1, alumni, ProjectApplicationStatus.WITHDRAWN,
                "Interested in the map integration work.", "No longer available — withdrawing.");
        withdrawnApp.setWithdrawnAt(LocalDateTime.now());
        projectApplicationRepository.save(withdrawnApp);

        log.info("Seeded 4 projects, 5 roles, 4 applications (PENDING/ACCEPTED/REJECTED/WITHDRAWN) and 1 accepted membership.");
    }

    private Project baseProject(User owner, String title, String description, ProjectStatus status,
                                boolean recruiting, String githubUrl, String demoUrl) {
        Project project = new Project();
        project.setOwnerId(owner.getId());
        project.setUniversityId(viewerUniversityId(owner));
        project.setTitle(title);
        project.setDescription(description);
        project.setGithubUrl(githubUrl);
        project.setDemoUrl(demoUrl);
        project.setStatus(status);
        project.setRecruiting(recruiting);
        return project;
    }

    private Long viewerUniversityId(User user) {
        if (user instanceof Student s) return s.getUniversityId();
        if (user instanceof Alumni a) return a.getUniversityId();
        if (user instanceof Club c) return c.getUniversityId();
        return null;
    }

    private ProjectRole baseRole(Project project, String title, String description, int slots) {
        ProjectRole role = new ProjectRole();
        role.setProjectId(project.getId());
        role.setTitle(title);
        role.setDescription(description);
        role.setSlots(slots);
        role.setStatus(ProjectRoleStatus.OPEN);
        return role;
    }

    private void addOwnerMembership(Project project, User owner) {
        ProjectMember membership = new ProjectMember();
        membership.setProjectId(project.getId());
        membership.setUserId(owner.getId());
        membership.setRole(ProjectMemberRole.OWNER);
        projectMemberRepository.save(membership);
    }

    @SuppressWarnings("java:S107") // A seeder fixture builder; named constants would be less readable.
    private ProjectApplication application(Project project, ProjectRole role, User applicant,
                                           ProjectApplicationStatus status, String message, String decisionReason) {
        ProjectApplication application = new ProjectApplication();
        application.setProjectId(project.getId());
        application.setProjectRoleId(role.getId());
        application.setApplicantId(applicant.getId());
        application.setMessage(message);
        application.setStatus(status);
        application.setDecisionReason(decisionReason);
        if (status == ProjectApplicationStatus.ACCEPTED || status == ProjectApplicationStatus.REJECTED) {
            application.setReviewedBy(project.getOwnerId());
            application.setReviewedAt(LocalDateTime.now());
        }
        return application;
    }
}
