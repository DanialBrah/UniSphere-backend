package com.unisphere.backend.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "universities")
@DiscriminatorValue("UNIVERSITY")
@PrimaryKeyJoinColumn(name = "user_id")
@Getter
@Setter
@NoArgsConstructor
public class University extends User {

    @Override
    public Role getRole() { return Role.UNIVERSITY; }

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "short_name", length = 50)
    private String shortName;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String state;

    // Separate institution verification — distinct from users.is_verified
    @Column(name = "is_verified", nullable = false)
    private boolean institutionVerified = false;    // named to avoid clash with inherited verified field
}
