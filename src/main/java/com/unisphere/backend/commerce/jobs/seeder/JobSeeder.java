package com.unisphere.backend.commerce.jobs.seeder;

import com.unisphere.backend.commerce.jobs.entity.Job;
import com.unisphere.backend.commerce.jobs.entity.JobApplication;
import com.unisphere.backend.commerce.jobs.enums.ExperienceLevel;
import com.unisphere.backend.commerce.jobs.enums.JobApplicationMode;
import com.unisphere.backend.commerce.jobs.enums.JobApplicationStatus;
import com.unisphere.backend.commerce.jobs.enums.JobStatus;
import com.unisphere.backend.commerce.jobs.enums.JobType;
import com.unisphere.backend.commerce.jobs.enums.WorkMode;
import com.unisphere.backend.commerce.jobs.repository.JobApplicationRepository;
import com.unisphere.backend.commerce.jobs.repository.JobRepository;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Seeds sample job postings and applications. Runs after EventSeeder (@Order(7)). Guard: skips
 * entirely if any job already exists.
 *
 * <p>Covers every {@link JobStatus}, both {@link JobApplicationMode}s (including one EXTERNAL
 * posting demonstrating the "apply on the employer's own site" flow), and a spread of
 * {@link JobApplicationStatus} values on the OPEN/INTERNAL postings, including WITHDRAWN.
 */
@Slf4j
@Component
@Order(8)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeding.enabled", havingValue = "true", matchIfMissing = false)
public class JobSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final JobApplicationRepository jobApplicationRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (jobRepository.count() > 0) {
            log.info("Jobs already seeded — skipping.");
            return;
        }

        Optional<User> employerOpt   = userRepository.findByEmail("employer@unisphere.dev");
        Optional<User> studentOpt    = userRepository.findByEmail("student@unisphere.dev");
        Optional<User> alumniOpt     = userRepository.findByEmail("alumni@unisphere.dev");
        Optional<User> universityOpt = userRepository.findByEmail("university@unisphere.dev");

        if (employerOpt.isEmpty() || studentOpt.isEmpty() || alumniOpt.isEmpty() || universityOpt.isEmpty()) {
            log.warn("JobSeeder: seeded users not found — run UserSeeder first (app.seeding.enabled=true).");
            return;
        }

        User employer   = employerOpt.get();
        User student    = studentOpt.get();
        User alumni     = alumniOpt.get();
        User university = universityOpt.get();

        LocalDate today = LocalDate.now();

        // ── DRAFT — not yet published, visible only to the employer ─────────────
        Job draftRole = baseJob(employer, "Backend Engineering Intern",
                "Join our platform team to build internal tooling.", JobType.INTERNSHIP,
                WorkMode.HYBRID, ExperienceLevel.INTERNSHIP, "Kuala Lumpur, Malaysia", null);
        draftRole.setStatus(JobStatus.DRAFT);

        // ── OPEN, INTERNAL, university-scoped — the Easy Apply demo ─────────────
        Job graduateRole = baseJob(employer, "Graduate Software Engineer",
                "Full-time role for recent graduates across our product engineering squads.",
                JobType.FULL_TIME, WorkMode.HYBRID, ExperienceLevel.ENTRY_LEVEL,
                "Kuala Lumpur, Malaysia", university.getId());
        graduateRole.setSalaryMin(new BigDecimal("4500.00"));
        graduateRole.setSalaryMax(new BigDecimal("6000.00"));
        graduateRole.setApplicationDeadline(today.plusDays(30));
        graduateRole.setStatus(JobStatus.OPEN);

        // ── OPEN, INTERNAL, remote, visible to all universities ──────────────────
        Job remoteRole = baseJob(employer, "Remote Data Analyst (Contract)",
                "6-month contract analysing product usage data, fully remote.",
                JobType.CONTRACT, WorkMode.REMOTE, ExperienceLevel.MID_LEVEL, null, null);
        remoteRole.setApplicationDeadline(today.plusDays(45));
        remoteRole.setStatus(JobStatus.OPEN);

        // ── OPEN, EXTERNAL — the "apply on the employer's own site" flow ────────
        Job externalRole = baseJob(employer, "Senior Product Designer",
                "Lead design for our flagship mobile app. Apply through our careers portal.",
                JobType.FULL_TIME, WorkMode.ON_SITE, ExperienceLevel.SENIOR_LEVEL,
                "Petaling Jaya, Malaysia", null);
        externalRole.setApplicationMode(JobApplicationMode.EXTERNAL);
        externalRole.setExternalApplyUrl("https://careers.example.com/senior-product-designer");
        externalRole.setStatus(JobStatus.OPEN);

        // ── CLOSED ────────────────────────────────────────────────────────────
        Job closedRole = baseJob(employer, "Marketing Intern (Closed)",
                "Summer marketing internship — applications have closed.", JobType.INTERNSHIP,
                WorkMode.ON_SITE, ExperienceLevel.INTERNSHIP, "Kuala Lumpur, Malaysia", null);
        closedRole.setStatus(JobStatus.CLOSED);

        // ── FILLED ────────────────────────────────────────────────────────────
        Job filledRole = baseJob(employer, "DevOps Engineer",
                "Position has been filled.", JobType.FULL_TIME, WorkMode.HYBRID,
                ExperienceLevel.MID_LEVEL, "Kuala Lumpur, Malaysia", null);
        filledRole.setStatus(JobStatus.FILLED);

        jobRepository.saveAll(List.of(draftRole, graduateRole, remoteRole, externalRole, closedRole, filledRole));

        // ── Applications on the two OPEN/INTERNAL postings ───────────────────────
        // uq_japp_job_applicant allows at most one row per (job, applicant) — never repeat a
        // (job, applicant) pair below, even across different statuses.
        graduateRole.setApplicationCount(2);
        remoteRole.setApplicationCount(2);
        jobRepository.saveAll(List.of(graduateRole, remoteRole));

        jobApplicationRepository.saveAll(List.of(
                application(graduateRole, student, JobApplicationStatus.SHORTLISTED,
                        "Excited to bring my internship experience to a full-time role."),
                application(graduateRole, alumni, JobApplicationStatus.REVIEWED, null),

                application(remoteRole, alumni, JobApplicationStatus.SUBMITTED, null),
                application(remoteRole, student, JobApplicationStatus.WITHDRAWN, null)));

        log.info("Seeded 6 jobs and 4 applications.");
    }

    @SuppressWarnings("java:S107") // A seeder fixture builder; named constants would be less readable.
    private Job baseJob(User employer, String title, String description, JobType jobType, WorkMode workMode,
                        ExperienceLevel experienceLevel, String location, Long universityId) {
        Job job = new Job();
        job.setEmployerId(employer.getId());
        job.setUniversityId(universityId);
        job.setTitle(title);
        job.setDescription(description);
        job.setRequirements("Strong communication skills and a willingness to learn.");
        job.setJobType(jobType);
        job.setWorkMode(workMode);
        job.setExperienceLevel(experienceLevel);
        job.setLocation(location);
        return job;
    }

    private JobApplication application(Job job, User applicant, JobApplicationStatus status, String decisionReason) {
        JobApplication application = new JobApplication();
        application.setJobId(job.getId());
        application.setApplicantId(applicant.getId());
        // Bare object key, never a resolved URL — see changeset 012. Points at an object that does
        // not exist in dev storage; MediaUrlResolver still signs it and the browser 404s on the
        // file, which is the intended "no real asset" behaviour for seed data.
        application.setResumeKey("job-applications/" + applicant.getId() + "/seed-resume.pdf");
        application.setCoverLetter("I'm very interested in this opportunity and believe my background is a strong fit.");
        application.setStatus(status);
        application.setDecisionReason(decisionReason);
        if (status == JobApplicationStatus.WITHDRAWN) {
            application.setWithdrawnAt(LocalDateTime.now());
        }
        return application;
    }
}
