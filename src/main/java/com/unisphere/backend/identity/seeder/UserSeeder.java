package com.unisphere.backend.identity.seeder;

import com.unisphere.backend.identity.entity.*;
import com.unisphere.backend.identity.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds one sample user of every role on first startup.
 * Each seed is guarded by existsByEmail — safe to restart without duplicates.
 *
 * Default password for all seeded accounts: Password123!
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeding.enabled", havingValue = "true", matchIfMissing = false)
public class UserSeeder implements CommandLineRunner {

    private final UserRepository       userRepository;
    private final StudentRepository    studentRepository;
    private final AlumniRepository     alumniRepository;
    private final EmployerRepository   employerRepository;
    private final UniversityRepository universityRepository;
    private final ClubRepository       clubRepository;
    private final AdminRepository      adminRepository;
    private final PasswordEncoder      passwordEncoder;

    private static final String DEFAULT_PASSWORD = "Password123!";

    @Override
    @Transactional
    public void run(String... args) {
        seedStudent();
        seedAlumni();
        seedEmployer();
        seedUniversity();
        seedClub();
        seedAdmin();
        linkUniversityAffiliations();
        log.info("User seeding complete.");
    }

    // Runs after all seeds so the university's DB-assigned id is known. Links the seeded student
    // and alumni to it so UNIVERSITY-visibility posts have real, testable affiliation data —
    // otherwise every seeded user has a null universityId and that visibility tier is unreachable
    // with seed data alone.
    private void linkUniversityAffiliations() {
        userRepository.findByEmail("university@unisphere.dev").ifPresent(university -> {
            userRepository.findByEmail("student@unisphere.dev")
                    .flatMap(u -> studentRepository.findById(u.getId()))
                    .ifPresent(student -> {
                        student.setUniversityId(university.getId());
                        studentRepository.save(student);
                    });
            userRepository.findByEmail("alumni@unisphere.dev")
                    .flatMap(u -> alumniRepository.findById(u.getId()))
                    .ifPresent(alumni -> {
                        alumni.setUniversityId(university.getId());
                        alumniRepository.save(alumni);
                    });
            log.info("Linked STUDENT and ALUMNI to UNIVERSITY → {}", university.getId());
        });
    }

    // ── Student ──────────────────────────────────────────────────────────────

    private void seedStudent() {
        String email = "student@unisphere.dev";
        if (userRepository.existsByEmail(email)) return;

        Student s = new Student();
        s.setEmail(email);
        s.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        s.setPhone("+60123456701");
        s.setStatus(UserStatus.ACTIVE);
        s.setVerified(true);
        s.setFullName("Ahmad Faiz bin Abdullah");
        s.setMatricNumber("2021001234");
        s.setUniversityEmail("ahmadfahiz@student.uitm.edu.my");
        s.setFaculty("Faculty of Computer and Mathematical Sciences");
        s.setProgram("Bachelor of Computer Science (Hons.)");
        s.setYearOfStudy(3);
        studentRepository.save(s);
        log.info("Seeded STUDENT    → {}", email);
    }

    // ── Alumni ───────────────────────────────────────────────────────────────

    private void seedAlumni() {
        String email = "alumni@unisphere.dev";
        if (userRepository.existsByEmail(email)) return;

        Alumni a = new Alumni();
        a.setEmail(email);
        a.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        a.setPhone("+60123456702");
        a.setStatus(UserStatus.ACTIVE);
        a.setVerified(true);
        a.setFullName("Siti Rahimah binti Yusof");
        a.setGraduationYear("2022");
        a.setDegree("Bachelor of Information Technology (Hons.)");
        a.setMajor("Software Engineering");
        a.setCurrentCompany("Petronas Digital Sdn Bhd");
        a.setCurrentPosition("Software Engineer");
        a.setLinkedinUrl("https://linkedin.com/in/sitirahimah");
        alumniRepository.save(a);
        log.info("Seeded ALUMNI     → {}", email);
    }

    // ── Employer ─────────────────────────────────────────────────────────────

    private void seedEmployer() {
        String email = "employer@unisphere.dev";
        if (userRepository.existsByEmail(email)) return;

        Employer e = new Employer();
        e.setEmail(email);
        e.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        e.setPhone("+60387654321");
        e.setStatus(UserStatus.ACTIVE);
        e.setVerified(true);
        e.setCompanyName("TechCorp Sdn Bhd");
        e.setIndustry("Technology");
        e.setCompanySize("51-200");
        e.setWebsiteUrl("https://techcorp.com.my");
        e.setDescription("A leading software development company in Malaysia.");
        employerRepository.save(e);
        log.info("Seeded EMPLOYER   → {}", email);
    }

    // ── University ───────────────────────────────────────────────────────────

    private void seedUniversity() {
        String email = "university@unisphere.dev";
        if (userRepository.existsByEmail(email)) return;

        University u = new University();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        u.setStatus(UserStatus.ACTIVE);
        u.setVerified(true);
        u.setName("Universiti Teknologi MARA");
        u.setShortName("UiTM");
        u.setWebsiteUrl("https://www.uitm.edu.my");
        u.setAddress("40450 Shah Alam, Selangor");
        u.setCountry("Malaysia");
        u.setState("Selangor");
        universityRepository.save(u);
        log.info("Seeded UNIVERSITY → {}", email);
    }

    // ── Club ─────────────────────────────────────────────────────────────────

    private void seedClub() {
        String email = "club@unisphere.dev";
        if (userRepository.existsByEmail(email)) return;

        Club c = new Club();
        c.setEmail(email);
        c.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        c.setStatus(UserStatus.ACTIVE);
        c.setVerified(true);
        c.setName("Google Developer Student Club UiTM");
        c.setCategory("Technology");
        c.setDescription("A student-led community that connects developers at UiTM.");
        // universityId left null in seeder — university seed runs first but its ID
        // is only known after the DB assigns it; link via admin flow in production
        clubRepository.save(c);
        log.info("Seeded CLUB       → {}", email);
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    private void seedAdmin() {
        String email = "admin@unisphere.dev";
        if (userRepository.existsByEmail(email)) return;

        Admin a = new Admin();
        a.setEmail(email);
        a.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        a.setStatus(UserStatus.ACTIVE);
        a.setVerified(true);
        a.setFullName("Super Admin");
        a.setAdminLevel(AdminSubRole.SUPER);
        adminRepository.save(a);
        log.info("Seeded ADMIN      → {}", email);
    }
}
