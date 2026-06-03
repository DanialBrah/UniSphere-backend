package com.unisphere.backend.identity.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "role", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StudentProfileResponse.class,    name = "STUDENT"),
        @JsonSubTypes.Type(value = AlumniProfileResponse.class,     name = "ALUMNI"),
        @JsonSubTypes.Type(value = EmployerProfileResponse.class,   name = "EMPLOYER"),
        @JsonSubTypes.Type(value = UniversityProfileResponse.class, name = "UNIVERSITY"),
        @JsonSubTypes.Type(value = ClubProfileResponse.class,       name = "CLUB"),
        @JsonSubTypes.Type(value = AdminProfileResponse.class,      name = "ADMIN"),
})
public sealed interface UserProfileResponse
        permits StudentProfileResponse, AlumniProfileResponse, EmployerProfileResponse,
                UniversityProfileResponse, ClubProfileResponse, AdminProfileResponse {

    Long id();
    String email();
    String role();
    String status();
    boolean isVerified();
    String avatarUrl();
    String phone();
}
