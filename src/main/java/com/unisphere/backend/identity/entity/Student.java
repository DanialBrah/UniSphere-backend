package com.unisphere.backend.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "students")
@DiscriminatorValue("STUDENT")
@PrimaryKeyJoinColumn(name = "user_id")
@Getter
@Setter
@NoArgsConstructor
public class Student extends User {

    @Override
    public Role getRole() { return Role.STUDENT; }

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "matric_number", length = 50)
    private String matricNumber;

    @Column(name = "university_email", length = 255)
    private String universityEmail;

    @Column(name = "university_id")
    private Long universityId;

    @Column(length = 255)
    private String faculty;

    @Column(length = 255)
    private String program;

    @Column(name = "year_of_study")
    private Integer yearOfStudy;

    @Column(name = "enrollment_date")
    private LocalDate enrollmentDate;

    @Column(name = "expected_graduation")
    private LocalDate expectedGraduation;
}
