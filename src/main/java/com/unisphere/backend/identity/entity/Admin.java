package com.unisphere.backend.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "admins")
@DiscriminatorValue("ADMIN")
@PrimaryKeyJoinColumn(name = "user_id")
@Getter
@Setter
@NoArgsConstructor
public class Admin extends User {

    @Override
    public Role getRole() { return Role.ADMIN; }

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "admin_level", nullable = false, length = 20)
    private AdminSubRole adminLevel = AdminSubRole.SUPPORT;
}
