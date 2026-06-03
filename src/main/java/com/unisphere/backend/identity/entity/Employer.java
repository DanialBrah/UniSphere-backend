package com.unisphere.backend.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "employers")
@DiscriminatorValue("EMPLOYER")
@PrimaryKeyJoinColumn(name = "user_id")
@Getter
@Setter
@NoArgsConstructor
public class Employer extends User {

    @Override
    public Role getRole() { return Role.EMPLOYER; }

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "company_logo_url", length = 500)
    private String companyLogoUrl;

    @Column(length = 100)
    private String industry;

    @Column(name = "company_size", length = 50)
    private String companySize;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Separate company verification status — distinct from users.is_verified
    @Column(name = "is_verified", nullable = false)
    private boolean companyVerified = false;    // named companyVerified to avoid clash with inherited verified field
}
