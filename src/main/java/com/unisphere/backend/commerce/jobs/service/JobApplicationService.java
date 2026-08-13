package com.unisphere.backend.commerce.jobs.service;

import com.unisphere.backend.commerce.jobs.dto.request.CreateJobApplicationRequest;
import com.unisphere.backend.commerce.jobs.dto.request.JobApplicationStatusUpdateRequest;
import com.unisphere.backend.commerce.jobs.dto.response.JobApplicantResponse;
import com.unisphere.backend.commerce.jobs.dto.response.JobApplicationResponse;
import com.unisphere.backend.commerce.jobs.entity.Job;
import com.unisphere.backend.commerce.jobs.entity.JobApplication;
import com.unisphere.backend.commerce.jobs.enums.JobApplicationMode;
import com.unisphere.backend.commerce.jobs.enums.JobApplicationStatus;
import com.unisphere.backend.commerce.jobs.enums.JobStatus;
import com.unisphere.backend.commerce.jobs.repository.JobApplicationRepository;
import com.unisphere.backend.commerce.jobs.repository.JobRepository;
import com.unisphere.backend.common.exception.InvalidJobApplicationTransitionException;
import com.unisphere.backend.common.exception.JobApplicationNotFoundException;
import com.unisphere.backend.common.exception.JobNotFoundException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.service.UserService;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Application lifecycle: apply, the combined employer-decision/applicant-withdraw status update,
 * and every read over an application.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class JobApplicationService {

    private static final Set<JobApplicationStatus> WITHDRAWABLE_FROM = Set.of(
            JobApplicationStatus.SUBMITTED, JobApplicationStatus.REVIEWED, JobApplicationStatus.SHORTLISTED);

    private static final Set<JobApplicationStatus> EMPLOYER_TARGET_STATUSES = Set.of(
            JobApplicationStatus.REVIEWED, JobApplicationStatus.SHORTLISTED,
            JobApplicationStatus.REJECTED, JobApplicationStatus.HIRED);

    private static final Set<JobApplicationStatus> EMPLOYER_SOURCE_STATUSES = Set.of(
            JobApplicationStatus.SUBMITTED, JobApplicationStatus.REVIEWED, JobApplicationStatus.SHORTLISTED);

    private final JobRepository jobRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobService jobService;
    private final JobAccessService accessService;
    private final JobApplicationMediaService jobApplicationMediaService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final MediaUrlResolver mediaUrlResolver;

    public JobApplicationResponse apply(Long jobId, CreateJobApplicationRequest req, User currentUser) {
        Job job = jobService.findActiveJob(jobId);
        accessService.assertCanApply(currentUser);

        if (job.getStatus() != JobStatus.OPEN) {
            throw new IllegalArgumentException("Applications are only open for OPEN jobs");
        }
        if (job.getApplicationMode() != JobApplicationMode.INTERNAL) {
            throw new IllegalArgumentException(
                    "This job accepts applications on the employer's own site: " + job.getExternalApplyUrl());
        }
        if (job.getApplicationDeadline() != null && job.getApplicationDeadline().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("The application deadline for this job has passed");
        }
        if (jobApplicationRepository.existsByJobIdAndApplicantId(jobId, currentUser.getId())) {
            throw new IllegalArgumentException("You have already applied to this job");
        }
        jobApplicationMediaService.assertOwnedKey(req.resumeKey(), currentUser);

        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setApplicantId(currentUser.getId());
        application.setResumeKey(req.resumeKey());
        application.setCoverLetter(req.coverLetter());
        application.setStatus(JobApplicationStatus.SUBMITTED);

        JobApplication saved = jobApplicationRepository.save(application);
        job.setApplicationCount(job.getApplicationCount() + 1);
        jobRepository.save(job);

        notificationService.createAndPush(job.getEmployerId(), currentUser.getId(),
                NotificationType.JOB, jobId, "JOB_APPLICATION_RECEIVED");

        // currentUser is already loaded — the applicant is always the caller here, so no extra query.
        return toResponse(saved, job.getTitle(), resolveApplicant(currentUser));
    }

    /**
     * The client-facing entry point for {@code PATCH /jobs/applications/{id}}. Two disjoint actors
     * funnel through here — see {@code JobApplicationStatusUpdateRequest}'s javadoc.
     */
    public JobApplicationResponse updateApplicationStatus(Long applicationId, JobApplicationStatusUpdateRequest req,
                                                           User currentUser) {
        JobApplication application = findApplication(applicationId);
        Job job = jobRepository.findActiveById(application.getJobId())
                .orElseThrow(() -> new JobNotFoundException(application.getJobId()));

        if (req.status() == JobApplicationStatus.WITHDRAWN) {
            return withdraw(application, job, req.reason(), currentUser);
        }
        return decide(application, job, req, currentUser);
    }

    private JobApplicationResponse withdraw(JobApplication application, Job job, String reason, User currentUser) {
        if (!application.getApplicantId().equals(currentUser.getId())) {
            throw new UnauthorizedActionException("Only the applicant may withdraw their own application");
        }
        if (!WITHDRAWABLE_FROM.contains(application.getStatus())) {
            throw new InvalidJobApplicationTransitionException(application.getStatus(), JobApplicationStatus.WITHDRAWN);
        }

        application.setStatus(JobApplicationStatus.WITHDRAWN);
        application.setDecisionReason(reason);
        application.setWithdrawnAt(LocalDateTime.now());
        JobApplication saved = jobApplicationRepository.save(application);

        // Self-action — no notification, same as EventRegistrationService's self-cancel path.
        return toResponse(saved, job.getTitle(), resolveApplicant(currentUser));
    }

    private JobApplicationResponse decide(JobApplication application, Job job, JobApplicationStatusUpdateRequest req,
                                          User currentUser) {
        accessService.assertCanManageApplication(application, job, currentUser);

        JobApplicationStatus from = application.getStatus();
        JobApplicationStatus to = req.status();
        if (!EMPLOYER_TARGET_STATUSES.contains(to) || !EMPLOYER_SOURCE_STATUSES.contains(from)) {
            throw new InvalidJobApplicationTransitionException(from, to);
        }

        application.setStatus(to);
        application.setDecisionReason(req.reason());
        JobApplication saved = jobApplicationRepository.save(application);

        notificationService.createAndPush(application.getApplicantId(), currentUser.getId(),
                NotificationType.JOB, job.getId(), "JOB_APPLICATION_STATUS_CHANGED");

        return toResponse(saved, job.getTitle(), resolveApplicant(application.getApplicantId()));
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    /** Applicant roster for a job the caller owns/administers. */
    @Transactional(readOnly = true)
    public Page<JobApplicationResponse> listApplications(Long jobId, JobApplicationStatus status,
                                                          Pageable pageable, User currentUser) {
        Job job = jobService.findViewableJob(jobId, currentUser);
        accessService.assertCanModify(job, currentUser);
        Page<JobApplication> page = jobApplicationRepository.findByJob(jobId, status, pageable);
        Map<Long, User> applicants = loadApplicants(page.getContent());
        return page.map(a -> toResponse(a, job.getTitle(), resolveApplicant(a.getApplicantId(), applicants)));
    }

    @Transactional(readOnly = true)
    public JobApplicationResponse myApplicationForJob(Long jobId, User currentUser) {
        Job job = jobService.findViewableJob(jobId, currentUser);
        JobApplication application = jobApplicationRepository.findByJobIdAndApplicantId(jobId, currentUser.getId())
                .orElseThrow(() -> new JobApplicationNotFoundException(jobId));
        return toResponse(application, job.getTitle(), resolveApplicant(currentUser));
    }

    /** "My applications" across every job — always the caller's own. */
    @Transactional(readOnly = true)
    public Page<JobApplicationResponse> myApplications(JobApplicationStatus status, Pageable pageable, User currentUser) {
        Page<JobApplication> page = jobApplicationRepository.findByApplicant(currentUser.getId(), status, pageable);
        Map<Long, String> jobTitles = loadJobTitles(page.getContent());
        JobApplicantResponse applicant = resolveApplicant(currentUser);
        return page.map(a -> toResponse(a, jobTitles.get(a.getJobId()), applicant));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private JobApplication findApplication(Long applicationId) {
        return jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new JobApplicationNotFoundException(applicationId));
    }

    private Map<Long, String> loadJobTitles(List<JobApplication> applications) {
        if (applications.isEmpty()) return Map.of();
        Set<Long> jobIds = applications.stream().map(JobApplication::getJobId).collect(Collectors.toSet());
        return jobRepository.findAllById(jobIds).stream()
                .collect(Collectors.toMap(Job::getId, Job::getTitle));
    }

    /** Batch-loads every distinct applicant on a page of applications — one query, not one per row. */
    private Map<Long, User> loadApplicants(List<JobApplication> applications) {
        if (applications.isEmpty()) return Map.of();
        Set<Long> userIds = applications.stream().map(JobApplication::getApplicantId).collect(Collectors.toSet());
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private JobApplicantResponse resolveApplicant(User user) {
        return new JobApplicantResponse(user.getId(), UserService.resolveDisplayName(user),
                mediaUrlResolver.toViewableUrl(user.getAvatarUrl()), user.getRole().name());
    }

    /** Batch-map lookup — for a page of applications, paired with {@link #loadApplicants}. */
    private JobApplicantResponse resolveApplicant(Long userId, Map<Long, User> users) {
        User user = users.get(userId);
        return user != null ? resolveApplicant(user) : unknownApplicant(userId);
    }

    /** Single-row lookup — for the write paths where the actor isn't the applicant. */
    private JobApplicantResponse resolveApplicant(Long userId) {
        return userRepository.findById(userId).map(this::resolveApplicant).orElseGet(() -> unknownApplicant(userId));
    }

    private JobApplicantResponse unknownApplicant(Long userId) {
        return new JobApplicantResponse(userId, "Unknown", null, "UNKNOWN");
    }

    private JobApplicationResponse toResponse(JobApplication application, String jobTitle, JobApplicantResponse applicant) {
        return new JobApplicationResponse(
                application.getId(), application.getJobId(), jobTitle, application.getApplicantId(), applicant,
                mediaUrlResolver.toViewableUrl(application.getResumeKey()), application.getCoverLetter(),
                application.getStatus(), application.getDecisionReason(), application.getWithdrawnAt(),
                application.getCreatedAt());
    }
}
