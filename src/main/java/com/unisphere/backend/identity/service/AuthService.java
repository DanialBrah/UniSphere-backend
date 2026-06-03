package com.unisphere.backend.identity.service;

import com.unisphere.backend.common.exception.EmailAlreadyExistsException;
import com.unisphere.backend.common.exception.InvalidCredentialsException;
import com.unisphere.backend.common.exception.TokenExpiredException;
import com.unisphere.backend.common.exception.UserNotFoundException;
import com.unisphere.backend.config.JwtConfig;
import com.unisphere.backend.identity.dto.*;
import com.unisphere.backend.identity.entity.*;
import com.unisphere.backend.identity.entity.UserStatus;
import com.unisphere.backend.identity.mapper.UserMapper;
import com.unisphere.backend.identity.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AlumniRepository alumniRepository;
    private final EmployerRepository employerRepository;
    private final UniversityRepository universityRepository;
    private final ClubRepository clubRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final JwtConfig jwtConfig;

    // ── Register ────────────────────────────────────────────────────────────

    public AuthResponse registerStudent(RegisterStudentRequest req) {
        String email = normalizeEmail(req.email());
        assertEmailAvailable(email);
        Student s = new Student();
        s.setEmail(email);
        s.setPassword(passwordEncoder.encode(req.password()));
        s.setStatus(UserStatus.ACTIVE);
        s.setPhone(req.phone());
        s.setFullName(req.fullName());
        s.setMatricNumber(req.matricNumber());
        s.setUniversityEmail(req.universityEmail());
        s.setUniversityId(req.universityId());
        s.setFaculty(req.faculty());
        s.setProgram(req.program());
        s.setYearOfStudy(req.yearOfStudy());
        s.setEnrollmentDate(req.enrollmentDate());
        s.setExpectedGraduation(req.expectedGraduation());
        Student saved = studentRepository.save(s);
        return buildAuthResponse(saved, userMapper.toStudentProfile(saved, saved));
    }

    public AuthResponse registerAlumni(RegisterAlumniRequest req) {
        String email = normalizeEmail(req.email());
        assertEmailAvailable(email);
        Alumni a = new Alumni();
        a.setEmail(email);
        a.setPassword(passwordEncoder.encode(req.password()));
        a.setStatus(UserStatus.ACTIVE);
        a.setPhone(req.phone());
        a.setFullName(req.fullName());
        a.setUniversityId(req.universityId());
        a.setGraduationYear(req.graduationYear());
        a.setDegree(req.degree());
        a.setMajor(req.major());
        a.setCurrentCompany(req.currentCompany());
        a.setCurrentPosition(req.currentPosition());
        a.setLinkedinUrl(req.linkedinUrl());
        Alumni saved = alumniRepository.save(a);
        return buildAuthResponse(saved, userMapper.toAlumniProfile(saved, saved));
    }

    public AuthResponse registerEmployer(RegisterEmployerRequest req) {
        String email = normalizeEmail(req.email());
        assertEmailAvailable(email);
        Employer e = new Employer();
        e.setEmail(email);
        e.setPassword(passwordEncoder.encode(req.password()));
        e.setStatus(UserStatus.ACTIVE);
        e.setPhone(req.phone());
        e.setCompanyName(req.companyName());
        e.setIndustry(req.industry());
        e.setCompanySize(req.companySize());
        e.setWebsiteUrl(req.websiteUrl());
        e.setDescription(req.description());
        Employer saved = employerRepository.save(e);
        return buildAuthResponse(saved, userMapper.toEmployerProfile(saved, saved));
    }

    public AuthResponse registerUniversity(RegisterUniversityRequest req) {
        String email = normalizeEmail(req.email());
        assertEmailAvailable(email);
        University u = new University();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(req.password()));
        u.setStatus(UserStatus.ACTIVE);
        u.setPhone(req.phone());
        u.setName(req.name());
        u.setShortName(req.shortName());
        u.setWebsiteUrl(req.websiteUrl());
        u.setAddress(req.address());
        u.setCountry(req.country());
        u.setState(req.state());
        University saved = universityRepository.save(u);
        return buildAuthResponse(saved, userMapper.toUniversityProfile(saved, saved));
    }

    public AuthResponse registerClub(RegisterClubRequest req) {
        String email = normalizeEmail(req.email());
        assertEmailAvailable(email);
        Club c = new Club();
        c.setEmail(email);
        c.setPassword(passwordEncoder.encode(req.password()));
        c.setStatus(UserStatus.ACTIVE);
        c.setPhone(req.phone());
        c.setName(req.name());
        c.setUniversityId(req.universityId());
        c.setCategory(req.category());
        c.setDescription(req.description());
        Club saved = clubRepository.save(c);
        return buildAuthResponse(saved, userMapper.toClubProfile(saved, saved));
    }

    // ── Login ────────────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest req) {
        String email = normalizeEmail(req.email());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, req.password())
            );
        } catch (AuthenticationException e) {
            throw new InvalidCredentialsException();
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        return buildAuthResponse(user, toProfile(user));
    }

    // ── Me ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserProfileResponse meByEmail(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return toProfile(user);
    }

    // ── Refresh ──────────────────────────────────────────────────────────────

    public AuthResponse refresh(RefreshTokenRequest req) {
        if (!jwtService.isRefreshToken(req.refreshToken())) {
            throw new TokenExpiredException("Invalid token type — access tokens cannot be used to refresh");
        }
        String email;
        try {
            email = normalizeEmail(jwtService.extractEmail(req.refreshToken()));
        } catch (Exception e) {
            throw new TokenExpiredException("Refresh token is invalid or expired");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!jwtService.isTokenValid(req.refreshToken(), user)) {
            throw new TokenExpiredException("Refresh token is invalid or expired");
        }
        return buildAuthResponse(user, toProfile(user));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Lowercase + strip so Alice@Example.com and alice@example.com are the same identity. */
    private static String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT).strip();
    }

    private void assertEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
    }

    private AuthResponse buildAuthResponse(User user, UserProfileResponse profile) {
        return new AuthResponse(
                jwtService.generateToken(user),
                jwtService.generateRefreshToken(user),
                jwtConfig.getExpiration(),
                profile
        );
    }

    private UserProfileResponse toProfile(User user) {
        return switch (user.getRole()) {
            case STUDENT    -> userMapper.toStudentProfile(user, (Student) user);
            case ALUMNI     -> userMapper.toAlumniProfile(user, (Alumni) user);
            case EMPLOYER   -> userMapper.toEmployerProfile(user, (Employer) user);
            case UNIVERSITY -> userMapper.toUniversityProfile(user, (University) user);
            case CLUB       -> userMapper.toClubProfile(user, (Club) user);
            case ADMIN      -> userMapper.toAdminProfile(user, (Admin) user);
        };
    }
}
