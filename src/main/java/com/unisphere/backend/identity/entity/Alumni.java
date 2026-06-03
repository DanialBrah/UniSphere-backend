package com.unisphere.backend.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "alumni")
@DiscriminatorValue("ALUMNI")
@PrimaryKeyJoinColumn(name = "user_id")
@Getter
@Setter
@NoArgsConstructor
public class Alumni extends User {

    @Override
    public Role getRole() { return Role.ALUMNI; }

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "university_id")
    private Long universityId;

    @Column(name = "graduation_year", length = 4)
    private String graduationYear;

    @Column(length = 100)
    private String degree;

    @Column(length = 255)
    private String major;

    @Column(name = "current_company", length = 255)
    private String currentCompany;

    @Column(name = "current_position", length = 255)
    private String currentPosition;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;
}
