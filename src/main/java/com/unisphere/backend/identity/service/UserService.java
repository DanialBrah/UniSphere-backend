package com.unisphere.backend.identity.service;

import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.dto.*;
import com.unisphere.backend.identity.entity.*;
import com.unisphere.backend.identity.mapper.UserMapper;
import com.unisphere.backend.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserProfileResponse updateProfile(User currentUser, UpdateProfileRequest req) {
        if (req.phone() != null) currentUser.setPhone(req.phone());

        if (req.avatarUrl() != null) {
            String newAvatar = req.avatarUrl().isBlank() ? null : req.avatarUrl();
            if (newAvatar != null && !newAvatar.startsWith("avatars/" + currentUser.getId() + "/")) {
                throw new UnauthorizedActionException("Avatar key does not belong to the current user");
            }
            currentUser.setAvatarUrl(newAvatar);
            syncRoleAvatar(currentUser, newAvatar);
        }

        if (currentUser instanceof Student s) {
            if (req.fullName()    != null) s.setFullName(req.fullName());
            if (req.faculty()     != null) s.setFaculty(req.faculty());
            if (req.program()     != null) s.setProgram(req.program());
            if (req.yearOfStudy() != null) s.setYearOfStudy(req.yearOfStudy());
        } else if (currentUser instanceof Alumni a) {
            if (req.fullName()        != null) a.setFullName(req.fullName());
            if (req.currentCompany()  != null) a.setCurrentCompany(req.currentCompany());
            if (req.currentPosition() != null) a.setCurrentPosition(req.currentPosition());
            if (req.linkedinUrl()     != null) a.setLinkedinUrl(req.linkedinUrl());
        } else if (currentUser instanceof Employer e) {
            if (req.companyName()  != null) e.setCompanyName(req.companyName());
            if (req.industry()     != null) e.setIndustry(req.industry());
            if (req.companySize()  != null) e.setCompanySize(req.companySize());
            if (req.websiteUrl()   != null) e.setWebsiteUrl(req.websiteUrl());
            if (req.description()  != null) e.setDescription(req.description());
        } else if (currentUser instanceof University u) {
            if (req.fullName()   != null) u.setName(req.fullName());
            if (req.shortName()  != null) u.setShortName(req.shortName());
            if (req.websiteUrl() != null) u.setWebsiteUrl(req.websiteUrl());
            if (req.address()    != null) u.setAddress(req.address());
            if (req.country()    != null) u.setCountry(req.country());
            if (req.state()      != null) u.setState(req.state());
        } else if (currentUser instanceof Club c) {
            if (req.fullName()    != null) c.setName(req.fullName());
            if (req.description() != null) c.setDescription(req.description());
            if (req.category()    != null) c.setCategory(req.category());
        } else if (currentUser instanceof Admin adm) {
            if (req.fullName() != null) adm.setFullName(req.fullName());
        }

        userRepository.save(currentUser);
        return toProfile(currentUser);
    }

    private void syncRoleAvatar(User user, String url) {
        if (user instanceof Employer e) e.setCompanyLogoUrl(url);
        else if (user instanceof University u) u.setLogoUrl(url);
        else if (user instanceof Club c) c.setLogoUrl(url);
    }

    UserProfileResponse toProfile(User user) {
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
