package com.college.attendance.repository;

import com.college.attendance.domain.CaseStatus;
import com.college.attendance.domain.ShortageCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShortageCaseRepository extends JpaRepository<ShortageCase, Long> {

    List<ShortageCase> findByStatusInOrderByOpenedAtDesc(List<CaseStatus> statuses);

    List<ShortageCase> findByStudent_RollNumberOrderByOpenedAtDesc(String rollNumber);

    /**
     * A student may have at most one case in flight. Raising a second while
     * the first is unanswered would leave them with two questions about the
     * same shortage and no way to tell which reply belongs to which.
     */
    Optional<ShortageCase> findFirstByStudent_RollNumberAndStatusInOrderByOpenedAtDesc(
            String rollNumber, List<CaseStatus> statuses);

    long countByStatusIn(List<CaseStatus> statuses);
}
