package com.unisphere.backend.identity.mapper;

import com.unisphere.backend.identity.dto.*;
import com.unisphere.backend.identity.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Multi-source MapStruct mapper: each method takes the base User
 * (for common fields) plus the role-specific entity (for profile fields).
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id",         source = "user.id")
    @Mapping(target = "email",      source = "user.email")
    @Mapping(target = "role",       expression = "java(user.getRole().name())")
    @Mapping(target = "status",     expression = "java(user.getStatus().name())")
    @Mapping(target = "isVerified", source = "user.verified")
    @Mapping(target = "avatarUrl",  source = "user.avatarUrl")
    @Mapping(target = "phone",      source = "user.phone")
    @Mapping(target = "createdAt",  source = "user.createdAt")
    @Mapping(target = "fullName",         source = "student.fullName")
    @Mapping(target = "matricNumber",     source = "student.matricNumber")
    @Mapping(target = "universityEmail",  source = "student.universityEmail")
    @Mapping(target = "universityId",     source = "student.universityId")
    @Mapping(target = "faculty",          source = "student.faculty")
    @Mapping(target = "program",          source = "student.program")
    @Mapping(target = "yearOfStudy",      source = "student.yearOfStudy")
    @Mapping(target = "enrollmentDate",   source = "student.enrollmentDate")
    @Mapping(target = "expectedGraduation", source = "student.expectedGraduation")
    StudentProfileResponse toStudentProfile(User user, Student student);

    @Mapping(target = "id",         source = "user.id")
    @Mapping(target = "email",      source = "user.email")
    @Mapping(target = "role",       expression = "java(user.getRole().name())")
    @Mapping(target = "status",     expression = "java(user.getStatus().name())")
    @Mapping(target = "isVerified", source = "user.verified")
    @Mapping(target = "avatarUrl",  source = "user.avatarUrl")
    @Mapping(target = "phone",      source = "user.phone")
    @Mapping(target = "createdAt",  source = "user.createdAt")
    @Mapping(target = "fullName",         source = "alumni.fullName")
    @Mapping(target = "universityId",     source = "alumni.universityId")
    @Mapping(target = "graduationYear",   source = "alumni.graduationYear")
    @Mapping(target = "degree",           source = "alumni.degree")
    @Mapping(target = "major",            source = "alumni.major")
    @Mapping(target = "currentCompany",   source = "alumni.currentCompany")
    @Mapping(target = "currentPosition",  source = "alumni.currentPosition")
    @Mapping(target = "linkedinUrl",      source = "alumni.linkedinUrl")
    AlumniProfileResponse toAlumniProfile(User user, Alumni alumni);

    @Mapping(target = "id",         source = "user.id")
    @Mapping(target = "email",      source = "user.email")
    @Mapping(target = "role",       expression = "java(user.getRole().name())")
    @Mapping(target = "status",     expression = "java(user.getStatus().name())")
    @Mapping(target = "isVerified", source = "user.verified")
    @Mapping(target = "avatarUrl",  source = "user.avatarUrl")
    @Mapping(target = "phone",      source = "user.phone")
    @Mapping(target = "createdAt",  source = "user.createdAt")
    @Mapping(target = "companyName",    source = "employer.companyName")
    @Mapping(target = "companyLogoUrl", source = "employer.companyLogoUrl")
    @Mapping(target = "industry",       source = "employer.industry")
    @Mapping(target = "companySize",    source = "employer.companySize")
    @Mapping(target = "websiteUrl",     source = "employer.websiteUrl")
    @Mapping(target = "description",    source = "employer.description")
    @Mapping(target = "companyVerified", source = "employer.companyVerified")
    EmployerProfileResponse toEmployerProfile(User user, Employer employer);

    @Mapping(target = "id",         source = "user.id")
    @Mapping(target = "email",      source = "user.email")
    @Mapping(target = "role",       expression = "java(user.getRole().name())")
    @Mapping(target = "status",     expression = "java(user.getStatus().name())")
    @Mapping(target = "isVerified", source = "user.verified")
    @Mapping(target = "avatarUrl",  source = "user.avatarUrl")
    @Mapping(target = "phone",      source = "user.phone")
    @Mapping(target = "createdAt",  source = "user.createdAt")
    @Mapping(target = "name",               source = "university.name")
    @Mapping(target = "shortName",          source = "university.shortName")
    @Mapping(target = "logoUrl",            source = "university.logoUrl")
    @Mapping(target = "websiteUrl",         source = "university.websiteUrl")
    @Mapping(target = "address",            source = "university.address")
    @Mapping(target = "country",            source = "university.country")
    @Mapping(target = "state",              source = "university.state")
    @Mapping(target = "institutionVerified", source = "university.institutionVerified")
    UniversityProfileResponse toUniversityProfile(User user, University university);

    @Mapping(target = "id",         source = "user.id")
    @Mapping(target = "email",      source = "user.email")
    @Mapping(target = "role",       expression = "java(user.getRole().name())")
    @Mapping(target = "status",     expression = "java(user.getStatus().name())")
    @Mapping(target = "isVerified", source = "user.verified")
    @Mapping(target = "avatarUrl",  source = "user.avatarUrl")
    @Mapping(target = "phone",      source = "user.phone")
    @Mapping(target = "createdAt",  source = "user.createdAt")
    @Mapping(target = "name",            source = "club.name")
    @Mapping(target = "universityId",    source = "club.universityId")
    @Mapping(target = "description",     source = "club.description")
    @Mapping(target = "logoUrl",         source = "club.logoUrl")
    @Mapping(target = "category",        source = "club.category")
    @Mapping(target = "isOfficial",      source = "club.official")
    ClubProfileResponse toClubProfile(User user, Club club);

    @Mapping(target = "id",         source = "user.id")
    @Mapping(target = "email",      source = "user.email")
    @Mapping(target = "role",       expression = "java(user.getRole().name())")
    @Mapping(target = "status",     expression = "java(user.getStatus().name())")
    @Mapping(target = "isVerified", source = "user.verified")
    @Mapping(target = "avatarUrl",  source = "user.avatarUrl")
    @Mapping(target = "phone",      source = "user.phone")
    @Mapping(target = "createdAt",  source = "user.createdAt")
    @Mapping(target = "fullName",   source = "admin.fullName")
    @Mapping(target = "adminLevel", expression = "java(admin.getAdminLevel().name())")
    AdminProfileResponse toAdminProfile(User user, Admin admin);
}
