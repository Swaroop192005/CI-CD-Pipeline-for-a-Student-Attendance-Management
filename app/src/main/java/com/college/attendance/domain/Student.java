package com.college.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

/**
 * A student on the roll. The roll number is the business key: it is what
 * appears on the register, on the hall ticket and in every conversation
 * about a student, so it carries a uniqueness constraint.
 */
@Entity
@Table(name = "student")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Roll number is required")
    @Column(name = "roll_number", nullable = false, unique = true, length = 32)
    private String rollNumber;

    @NotBlank(message = "Full name is required")
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Email(message = "Enter a valid e-mail address")
    @Column(length = 160)
    private String email;

    @NotBlank(message = "Department is required")
    @Column(nullable = false, length = 80)
    private String department;

    @Min(value = 1, message = "Semester must be between 1 and 8")
    @Max(value = 8, message = "Semester must be between 1 and 8")
    @Column(nullable = false)
    private int semester;

    /** Inactive students stay in history but cannot receive new records. */
    @Column(nullable = false)
    private boolean active = true;

    protected Student() {
        // required by JPA
    }

    public Student(String rollNumber, String fullName, String email, String department, int semester) {
        this.rollNumber = rollNumber;
        this.fullName = fullName;
        this.email = email;
        this.department = department;
        this.semester = semester;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getSemester() {
        return semester;
    }

    public void setSemester(int semester) {
        this.semester = semester;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** "1CS21CS001 - Aditya Rao", as shown in drop-downs and tables. */
    public String getDisplayName() {
        return rollNumber + " - " + fullName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Student student)) {
            return false;
        }
        return id != null && Objects.equals(id, student.id);
    }

    @Override
    public int hashCode() {
        return Student.class.hashCode();
    }

    @Override
    public String toString() {
        return "Student{" + rollNumber + ", " + fullName + "}";
    }
}
