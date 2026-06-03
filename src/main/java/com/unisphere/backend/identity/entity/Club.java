package com.unisphere.backend.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clubs")
@DiscriminatorValue("CLUB")
@PrimaryKeyJoinColumn(name = "user_id")
@Getter
@Setter
@NoArgsConstructor
public class Club extends User {

    @Override
    public Role getRole() { return Role.CLUB; }

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "university_id")
    private Long universityId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(length = 100)
    private String category;

    @Column(name = "is_official", nullable = false)
    private boolean official = false;

    @Column(name = "advisor_student_id", columnDefinition = "BIGINT UNSIGNED")
    private Long advisorStudentId;
}
