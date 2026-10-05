package com.college.attendance.repository;

import com.college.attendance.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByRollNumber(String rollNumber);

    List<Student> findByActiveTrueOrderByRollNumberAsc();

    boolean existsByRollNumber(String rollNumber);
}
