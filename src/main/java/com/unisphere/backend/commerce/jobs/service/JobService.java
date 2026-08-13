package com.unisphere.backend.commerce.jobs.service;

import com.unisphere.backend.commerce.jobs.dto.request.CreateJobRequest;
import com.unisphere.backend.commerce.jobs.dto.request.JobStatusUpdateRequest;
import com.unisphere.backend.commerce.jobs.dto.request.UpdateJobRequest;
import com.unisphere.backend.commerce.jobs.dto.response.JobEmployerResponse;
import com.unisphere.backend.commerce.jobs.dto.response.JobResponse;
import com.unisphere.backend.commerce.jobs.dto.response.JobStatsResponse;
import com.unisphere.backend.commerce.jobs.dto.response.JobSummaryResponse;
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
import com.unisphere.backend.common.exception.InvalidJobStatusTransitionException;
import com.unisphere.backend.common.exception.JobNotFoundException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.University;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.EmployerRepository;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.entity.Employer;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Job CRUD, the browse feed, search, per-job stats, and every response mapping in the module. The
 * private {@code to*} methods at the bottom are the only places a {@link Job} is turned into a DTO.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final UserRepository userRepository;
    private final EmployerRepository employerRepository;
    private final MediaUrlResolver mediaUrlResolver;
    private final JobAccessService accessService;
    private final NotificationService notificationService;

    @Value("${jobs.default-salary-currency:MYR}")
    private String defaultSalaryCurrency;

    // ── Writes ───────────────────────────────────────────────────────────────

    public JobResponse createJob(CreateJobRequest req, User currentUser) {
        accessService.assertCanPost(currentUser);

        WorkMode workMode = req.workMode() != null ? req.workMode() : WorkMode.ON_SITE;
        JobApplicationMode applicationMode = req.applicationMode() != null ? req.applicationMode() : JobApplicationMode.INTERNAL;

        validateLocationRules(workMode, req.location());
        validateApplicationRules(applicationMode, req.externalApplyUrl());
        validateSalaryRange(req.salaryMin(), req.salaryMax());
        Long universityId = validateUniversityId(req.universityId());

        Job job = new Job();
        job.setEmployerId(currentUser.getId());
        job.setUniversityId(universityId);
        job.setTitle(req.title());
        job.setDescription(req.description());
        job.setRequirements(req.requirements());
        job.setJobType(req.jobType() != null ? req.jobType() : JobType.FULL_TIME);
        job.setWorkMode(workMode);
        job.setExperienceLevel(req.experienceLevel() != null ? req.experienceLevel() : ExperienceLevel.ENTRY_LEVEL);
        job.setLocation(req.location());
        job.setSalaryMin(req.salaryMin());
        job.setSalaryMax(req.salaryMax());
        job.setSalaryCurrency(req.salaryCurrency() != null ? req.salaryCurrency() : defaultSalaryCurrency);
        job.setApplicationMode(applicationMode);
        job.setExternalApplyUrl(req.externalApplyUrl());
        job.setApplicationDeadline(req.applicationDeadline());
        // Every job is born DRAFT — publishing is an explicit later action.
        job.setStatus(JobStatus.DRAFT);

        return toResponse(jobRepository.save(job), currentUser);
    }

    public JobResponse updateJob(Long jobId, UpdateJobRequest req, User currentUser) {
        Job job = findViewableJob(jobId, currentUser);
        accessService.assertCanModify(job, currentUser);

        if (req.title() != null) {
            if (req.title().isBlank()) {
                throw new IllegalArgumentException("title cannot be blank");
            }
            job.setTitle(req.title());
        }
        if (req.description() != null) job.setDescription(blankToNull(req.description()));
        if (req.requirements() != null) job.setRequirements(blankToNull(req.requirements()));
        if (req.jobType() != null) job.setJobType(req.jobType());
        if (req.workMode() != null) job.setWorkMode(req.workMode());
        if (req.experienceLevel() != null) job.setExperienceLevel(req.experienceLevel());
        if (req.location() != null) job.setLocation(blankToNull(req.location()));

        applySalary(job, req);

        if (req.salaryCurrency() != null) job.setSalaryCurrency(req.salaryCurrency());
        if (req.applicationMode() != null) job.setApplicationMode(req.applicationMode());
        if (req.externalApplyUrl() != null) job.setExternalApplyUrl(blankToNull(req.externalApplyUrl()));

        applyApplicationDeadline(job, req);
        applyUniversityId(job, req);

        // Re-check every cross-field invariant against the full post-update state, not just the
        // fields that changed this call — mirrors EventService.updateEvent.
        validateLocationRules(job.getWorkMode(), job.getLocation());
        validateApplicationRules(job.getApplicationMode(), job.getExternalApplyUrl());
        validateSalaryRange(job.getSalaryMin(), job.getSalaryMax());

        return toResponse(jobRepository.save(job), currentUser);
    }

    /** The job lifecycle, in one place. */
    public JobResponse changeStatus(Long jobId, JobStatusUpdateRequest req, User currentUser) {
        Job job = findViewableJob(jobId, currentUser);
        accessService.assertCanModify(job, currentUser);

        JobStatus from = job.getStatus();
        JobStatus to = req.status();

        if (from == to) {
            throw new InvalidJobStatusTransitionException(from, to);
        }

        boolean allowed = switch (from) {
            case DRAFT -> to == JobStatus.OPEN || to == JobStatus.CLOSED;
            case OPEN -> to == JobStatus.CLOSED || to == JobStatus.FILLED;
            case CLOSED, FILLED -> false;
        };
        if (!allowed) {
            throw new InvalidJobStatusTransitionException(from, to);
        }

        job.setStatus(to);
        Job saved = jobRepository.save(job);

        if (to == JobStatus.CLOSED || to == JobStatus.FILLED) {
            notifyApplicantsOfClosure(saved, to, currentUser);
        }

        return toResponse(saved, currentUser);
    }

    /** Soft delete. Blocked once the job has ever received an application — see {@code deviation #3}. */
    public void deleteJob(Long jobId, User currentUser) {
        Job job = findViewableJob(jobId, currentUser);
        accessService.assertCanModify(job, currentUser);
        if (job.getApplicationCount() > 0) {
            throw new IllegalArgumentException(
                    "This job has received applications — close it instead of deleting it");
        }
        job.setDeletedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public JobResponse getJobById(Long jobId, User currentUser) {
        return toResponse(findViewableJob(jobId, currentUser), currentUser);
    }

    /**
     * The browse feed. A client-supplied {@code DRAFT} filter is rejected outright — drafts are only
     * visible via {@code /jobs/me} — and a null filter defaults to {@code OPEN}.
     */
    @Transactional(readOnly = true)
    public Page<JobSummaryResponse> getFeed(JobType jobType, WorkMode workMode, ExperienceLevel experienceLevel,
                                            Long universityId, JobStatus status, Pageable pageable, User currentUser) {
        if (status == JobStatus.DRAFT) {
            throw new IllegalArgumentException("Drafts are only visible via /jobs/me");
        }
        JobStatus effectiveStatus = status != null ? status : JobStatus.OPEN;
        return toSummaryResponses(
                jobRepository.findFeed(isAdmin(currentUser), accessService.viewerUniversityId(currentUser),
                        effectiveStatus, jobType, workMode, experienceLevel, universityId, pageable),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<JobSummaryResponse> search(String query, JobType jobType, Pageable pageable, User currentUser) {
        String sanitized = sanitizeBooleanQuery(query);
        if (sanitized.isEmpty()) {
            return Page.empty(pageable);
        }
        return toSummaryResponses(
                jobRepository.searchFullText(sanitized, isAdmin(currentUser),
                        accessService.viewerUniversityId(currentUser), nameOf(jobType), stripSort(pageable)),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<JobSummaryResponse> getMyJobs(JobStatus status, Pageable pageable, User currentUser) {
        return toSummaryResponses(jobRepository.findByEmployerId(currentUser.getId(), status, pageable), currentUser);
    }

    @Transactional(readOnly = true)
    public JobStatsResponse getStats(Long jobId, User currentUser) {
        Job job = findActiveJob(jobId);
        accessService.assertCanModify(job, currentUser);

        Map<JobApplicationStatus, Long> byStatus = zeroFilled(JobApplicationStatus.class);
        jobApplicationRepository.countByJobIdGroupByStatus(jobId)
                .forEach(row -> byStatus.put(row.getStatus(), row.getTotal()));

        return new JobStatsResponse(
                byStatus.get(JobApplicationStatus.SUBMITTED),
                byStatus.get(JobApplicationStatus.REVIEWED),
                byStatus.get(JobApplicationStatus.SHORTLISTED),
                byStatus.get(JobApplicationStatus.REJECTED),
                byStatus.get(JobApplicationStatus.HIRED),
                byStatus.get(JobApplicationStatus.WITHDRAWN),
                job.getApplicationCount());
    }

    // ── Shared with JobApplicationService ───────────────────────────────────

    Job findActiveJob(Long jobId) {
        return jobRepository.findActiveById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }

    /**
     * Masks a DRAFT job as 404 for anyone but its employer/ADMIN, so the row's existence never leaks
     * through a 403 — same nuance {@code EventService.findViewableEvent} applies to DRAFT events.
     */
    Job findViewableJob(Long jobId, User viewer) {
        Job job = findActiveJob(jobId);
        if (job.getStatus() == JobStatus.DRAFT && !accessService.isOwnerOrAdmin(job, viewer)) {
            throw new JobNotFoundException(jobId);
        }
        return job;
    }

    JobResponse toResponse(Job job, User currentUser) {
        PageContext ctx = loadPageContext(List.of(job), currentUser);
        return buildResponse(job, ctx);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    private PageRequest stripSort(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String sanitizeBooleanQuery(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[+\\-><()~*\"@]", " ").trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private <E extends Enum<E>> Map<E, Long> zeroFilled(Class<E> type) {
        Map<E, Long> map = new EnumMap<>(type);
        for (E constant : type.getEnumConstants()) map.put(constant, 0L);
        return map;
    }

    /** Required unless REMOTE; forbidden when REMOTE — same shape as Event's online/venue rule. */
    private void validateLocationRules(WorkMode workMode, String location) {
        if (workMode == WorkMode.REMOTE) {
            if (location != null && !location.isBlank()) {
                throw new IllegalArgumentException("A remote job cannot have a location");
            }
        } else if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("An on-site or hybrid job requires a location");
        }
    }

    private void validateApplicationRules(JobApplicationMode mode, String externalApplyUrl) {
        if (mode == JobApplicationMode.EXTERNAL) {
            if (externalApplyUrl == null || externalApplyUrl.isBlank()) {
                throw new IllegalArgumentException("External application mode requires externalApplyUrl");
            }
        } else if (externalApplyUrl != null) {
            throw new IllegalArgumentException("externalApplyUrl is only valid when applicationMode is EXTERNAL");
        }
    }

    private void validateSalaryRange(BigDecimal salaryMin, BigDecimal salaryMax) {
        if (salaryMin != null && salaryMax != null && salaryMin.compareTo(salaryMax) > 0) {
            throw new IllegalArgumentException("salaryMin must not exceed salaryMax");
        }
    }

    private Long validateUniversityId(Long universityId) {
        if (universityId == null) return null;
        User user = userRepository.findById(universityId)
                .orElseThrow(() -> new IllegalArgumentException("universityId does not reference an existing account"));
        if (!(user instanceof University)) {
            throw new IllegalArgumentException("universityId must reference a university account");
        }
        return universityId;
    }

    private void applySalary(Job job, UpdateJobRequest req) {
        if (req.salaryMin() != null || req.salaryMax() != null) {
            if (req.salaryMin() != null) job.setSalaryMin(req.salaryMin());
            if (req.salaryMax() != null) job.setSalaryMax(req.salaryMax());
        } else if (Boolean.TRUE.equals(req.clearSalary())) {
            job.setSalaryMin(null);
            job.setSalaryMax(null);
        }
    }

    private void applyApplicationDeadline(Job job, UpdateJobRequest req) {
        if (req.applicationDeadline() != null) {
            job.setApplicationDeadline(req.applicationDeadline());
        } else if (Boolean.TRUE.equals(req.clearApplicationDeadline())) {
            job.setApplicationDeadline(null);
        }
    }

    private void applyUniversityId(Job job, UpdateJobRequest req) {
        if (req.universityId() != null) {
            job.setUniversityId(validateUniversityId(req.universityId()));
        } else if (Boolean.TRUE.equals(req.clearUniversityId())) {
            job.setUniversityId(null);
        }
    }

    private void notifyApplicantsOfClosure(Job job, JobStatus to, User employer) {
        String targetType = to == JobStatus.FILLED ? "JOB_FILLED" : "JOB_CLOSED";
        Set<JobApplicationStatus> inPipeline = Set.of(
                JobApplicationStatus.SUBMITTED, JobApplicationStatus.REVIEWED, JobApplicationStatus.SHORTLISTED);
        jobApplicationRepository.findApplicantIdsByJobIdAndStatusIn(job.getId(), inPipeline)
                .forEach(applicantId -> notificationService.createAndPush(
                        applicantId, employer.getId(), NotificationType.JOB, job.getId(), targetType));
    }

    // ── Response mapping ─────────────────────────────────────────────────────

    private Page<JobSummaryResponse> toSummaryResponses(Page<Job> jobs, User currentUser) {
        PageContext ctx = loadPageContext(jobs.getContent(), currentUser);
        return jobs.map(j -> toSummaryResponse(j, ctx));
    }

    /**
     * Everything a page of jobs needs that isn't on the rows themselves, loaded in two queries
     * regardless of page size: the employer/company block, and the viewer's own application status
     * per job (batched, not looked up per row).
     */
    private record PageContext(User viewer, Map<Long, Employer> employers,
                               Map<Long, JobApplicationStatus> viewerApplicationStatuses) {}

    private PageContext loadPageContext(List<Job> jobs, User currentUser) {
        if (jobs.isEmpty()) return new PageContext(currentUser, Map.of(), Map.of());

        Set<Long> employerIds = jobs.stream().map(Job::getEmployerId).collect(Collectors.toSet());

        Map<Long, JobApplicationStatus> viewerStatuses;
        if (currentUser.getRole() == Role.STUDENT || currentUser.getRole() == Role.ALUMNI) {
            Set<Long> jobIds = jobs.stream().map(Job::getId).collect(Collectors.toSet());
            viewerStatuses = jobApplicationRepository.findByJobIdInAndApplicantId(jobIds, currentUser.getId())
                    .stream()
                    .collect(Collectors.toMap(JobApplication::getJobId, JobApplication::getStatus));
        } else {
            viewerStatuses = Map.of();
        }

        return new PageContext(
                currentUser,
                employerRepository.findAllById(employerIds).stream()
                        .collect(Collectors.toMap(Employer::getId, e -> e)),
                viewerStatuses);
    }

    private JobResponse buildResponse(Job job, PageContext ctx) {
        return new JobResponse(
                job.getId(),
                resolveEmployer(job.getEmployerId(), ctx.employers()),
                job.getTitle(), job.getDescription(), job.getRequirements(),
                job.getJobType(), job.getWorkMode(), job.getExperienceLevel(),
                job.getLocation(), job.getSalaryMin(), job.getSalaryMax(), job.getSalaryCurrency(),
                job.getApplicationMode(), job.getExternalApplyUrl(), job.getApplicationDeadline(),
                job.getStatus(), job.getApplicationCount(),
                job.getUniversityId(),
                ctx.viewerApplicationStatuses().get(job.getId()),
                accessService.isOwnerOrAdmin(job, ctx.viewer()),
                job.getCreatedAt(), job.getUpdatedAt());
    }

    private JobSummaryResponse toSummaryResponse(Job job, PageContext ctx) {
        return new JobSummaryResponse(
                job.getId(),
                resolveEmployer(job.getEmployerId(), ctx.employers()),
                job.getTitle(), job.getJobType(), job.getWorkMode(), job.getExperienceLevel(),
                job.getLocation(), job.getSalaryMin(), job.getSalaryMax(), job.getSalaryCurrency(),
                job.getApplicationMode(), job.getStatus(), job.getApplicationDeadline(),
                job.getUniversityId(),
                ctx.viewerApplicationStatuses().get(job.getId()),
                accessService.isOwnerOrAdmin(job, ctx.viewer()),
                job.getCreatedAt());
    }

    private JobEmployerResponse resolveEmployer(Long employerId, Map<Long, Employer> employers) {
        Employer employer = employers.get(employerId);
        if (employer == null) return new JobEmployerResponse(employerId, "Unknown", null, null, null, null, false);
        return new JobEmployerResponse(
                employer.getId(), employer.getCompanyName(),
                mediaUrlResolver.toViewableUrl(employer.getCompanyLogoUrl()),
                employer.getIndustry(), employer.getCompanySize(), employer.getWebsiteUrl(),
                employer.isCompanyVerified());
    }
}
