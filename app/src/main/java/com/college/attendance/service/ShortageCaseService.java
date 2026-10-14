package com.college.attendance.service;

import com.college.attendance.config.AttendanceProperties;
import com.college.attendance.config.CurrentUser;
import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.CaseStatus;
import com.college.attendance.domain.CaseTier;
import com.college.attendance.domain.Role;
import com.college.attendance.domain.ShortageCase;
import com.college.attendance.domain.Student;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.ShortageCaseRepository;
import com.college.attendance.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The conversation between a head of department and a student whose
 * attendance has fallen too far.
 *
 * <p>This is what the department does instead of approving every record.
 * Approving sessions one at a time produced a queue that grew with class
 * size and told nobody anything: a head of department clicking approve four
 * hundred times is not reviewing anything. Raising a case against the few
 * students actually in trouble is work with a point to it.
 *
 * <p>Business rules enforced here:
 *
 * <ul>
 *   <li><b>BR-12</b> — a case may only be raised against a student who is
 *       actually below the shortage threshold. Without this the mechanism
 *       drifts back into being a general-purpose queue.</li>
 *   <li><b>BR-13</b> — one open case per student. A second question while
 *       the first is unanswered leaves the student unable to say which
 *       reply belongs to which.</li>
 *   <li><b>BR-14</b> — a case cannot be decided before the student has
 *       answered. The point of the case is to hear the explanation; a head
 *       of department who could reject on sight would simply be issuing a
 *       verdict.</li>
 *   <li><b>BR-15</b> — only the student a case is about may answer it.</li>
 *   <li><b>BR-16</b> — accepting marks the chosen absences {@code EXCUSED},
 *       which removes them from both sides of the percentage under BR-03.
 *       Accepting an explanation and leaving the figure untouched would be
 *       sympathy without consequence.</li>
 * </ul>
 */
@Service
public class ShortageCaseService {

    private static final Logger log = LoggerFactory.getLogger(ShortageCaseService.class);
    private static final List<CaseStatus> OPEN = List.of(
            CaseStatus.AWAITING_RESPONSE, CaseStatus.ANSWERED, CaseStatus.ESCALATED);

    private final ShortageCaseRepository cases;
    private final AttendanceRecordRepository records;
    private final StudentRepository students;
    private final CurrentUser currentUser;
    private final AttendanceProperties properties;

    public ShortageCaseService(ShortageCaseRepository cases, AttendanceRecordRepository records,
                               StudentRepository students, CurrentUser currentUser,
                               AttendanceProperties properties) {
        this.cases = cases;
        this.records = records;
        this.students = students;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    // ---- head of department -----------------------------------------------

    @Transactional
    public ShortageCase raise(String rollNumber, String question) {
        if (question == null || question.isBlank()) {
            throw new NotPermittedException("Ask the student something; a case needs a question.");
        }

        Student student = students.findByRollNumber(rollNumber)
                .orElseThrow(() -> new NotPermittedException("No student " + rollNumber + "."));

        cases.findFirstByStudent_RollNumberAndStatusInOrderByOpenedAtDesc(rollNumber, OPEN)
                .ifPresent(existing -> {
                    throw new DuplicateCaseException(rollNumber, existing.getId());
                });

        double percent = percentFor(rollNumber);
        CaseTier tier = tierFor(currentUser);
        int shortage = properties.shortageThreshold();
        int eligibility = properties.eligibilityThreshold();

        // BR-12, now band by band. The two tiers do not overlap, so a
        // student is never questioned by faculty and the head of department
        // at the same time about the same shortage.
        if (tier == CaseTier.HOD) {
            if (percent >= shortage) {
                throw new NotPermittedException(
                        rollNumber + " is at " + percent + "%, which is not below the "
                        + shortage + "% shortage threshold. Faculty raise queries above that.");
            }
        } else {
            if (percent < shortage) {
                throw new NotPermittedException(
                        rollNumber + " is at " + percent + "%, below the " + shortage
                        + "% shortage threshold, so this one is for the head of department.");
            }
            if (percent >= eligibility) {
                throw new NotPermittedException(
                        rollNumber + " is at " + percent + "%, which meets the " + eligibility
                        + "% eligibility threshold. There is nothing to answer for.");
            }
        }

        int threshold = tier == CaseTier.HOD ? shortage : eligibility;
        ShortageCase raised = cases.save(new ShortageCase(
                student, percent, threshold, question.trim(), currentUser.username(), tier));
        log.info("{} case {} raised against {} at {}% by {}",
                tier, raised.getId(), rollNumber, percent, currentUser.username());
        return raised;
    }

    /**
     * Hand a case faculty are not satisfied with to the head of department.
     *
     * <p>The student is not asked again. Their reply, and the reason faculty
     * found it insufficient, are what the head of department reads: asking
     * twice would make the student answer for the same absence to two
     * people, which is the queue this design exists to avoid.
     */
    @Transactional
    public ShortageCase escalate(Long caseId, String note) {
        ShortageCase shortage = require(caseId);

        if (shortage.getTier() != CaseTier.FACULTY) {
            throw new NotPermittedException(
                    "Case " + caseId + " already belongs to the head of department.");
        }
        assertOwnTier(shortage);
        if (shortage.getStatus() != CaseStatus.ANSWERED) {
            throw new NotPermittedException(shortage.getStatus() == CaseStatus.AWAITING_RESPONSE
                    ? "The student has not answered yet. Escalating now would send the head "
                      + "of department a question with no reply attached."
                    : "Case " + caseId + " has already been decided.");
        }
        if (note == null || note.isBlank()) {
            throw new NotPermittedException(
                    "Say why the explanation was not enough; that is what the head of "
                    + "department has to weigh.");
        }

        shortage.setTier(CaseTier.HOD);
        shortage.setStatus(CaseStatus.ESCALATED);
        shortage.setEscalationNote(note.trim());
        shortage.setEscalatedBy(currentUser.username());
        shortage.setEscalatedAt(Instant.now());

        log.info("Case {} escalated to the head of department by {}",
                caseId, currentUser.username());
        return cases.save(shortage);
    }

    /**
     * Accept the student's explanation and excuse their absences in the
     * given window.
     */
    @Transactional
    public ShortageCase accept(Long caseId, String note, LocalDate from, LocalDate to) {
        ShortageCase shortage = require(caseId);
        assertDecidable(shortage);
        assertOwnTier(shortage);

        // Faculty can be satisfied by an explanation, but cannot excuse
        // sessions: excusing changes the percentage the eligibility decision
        // rests on, and that belongs to the head of department. So a faculty
        // case closes as accepted and the record is left alone.
        if (shortage.getTier() == CaseTier.FACULTY) {
            shortage.setStatus(CaseStatus.ACCEPTED);
            shortage.setDecisionNote(note);
            shortage.setDecidedBy(currentUser.username());
            shortage.setDecidedAt(Instant.now());
            shortage.setExcusedCount(0);
            log.info("Faculty case {} closed as satisfied by {}", caseId, currentUser.username());
            return cases.save(shortage);
        }

        if (from == null || to == null || to.isBefore(from)) {
            throw new NotPermittedException(
                    "Give the dates the explanation covers, earliest first.");
        }

        List<AttendanceRecord> excused = records
                .findByStudent_RollNumberOrderBySessionDateAsc(shortage.getStudent().getRollNumber())
                .stream()
                .filter(r -> r.getAttendanceStatus() == AttendanceStatus.ABSENT)
                .filter(r -> !r.getSessionDate().isBefore(from) && !r.getSessionDate().isAfter(to))
                .toList();

        for (AttendanceRecord record : excused) {
            record.setAttendanceStatus(AttendanceStatus.EXCUSED);
            record.setExcusedBy(currentUser.username());
            record.setExcusedAt(Instant.now());
            record.setExcuseNote(note == null || note.isBlank()
                    ? "Explanation accepted (case " + caseId + ")"
                    : note.trim());
            record.setUpdatedAt(Instant.now());
            records.save(record);
        }

        shortage.setStatus(CaseStatus.ACCEPTED);
        shortage.setDecisionNote(note);
        shortage.setDecidedBy(currentUser.username());
        shortage.setDecidedAt(Instant.now());
        shortage.setExcusedCount(excused.size());
        shortage.setExcusedFrom(from);
        shortage.setExcusedTo(to);

        log.info("Case {} accepted by {}: {} sessions excused between {} and {}",
                caseId, currentUser.username(), excused.size(), from, to);
        return cases.save(shortage);
    }

    @Transactional
    public ShortageCase reject(Long caseId, String note) {
        ShortageCase shortage = require(caseId);
        assertDecidable(shortage);
        assertOwnTier(shortage);

        shortage.setStatus(CaseStatus.REJECTED);
        shortage.setDecisionNote(note);
        shortage.setDecidedBy(currentUser.username());
        shortage.setDecidedAt(Instant.now());

        log.info("Case {} rejected by {}", caseId, currentUser.username());
        return cases.save(shortage);
    }

    // ---- student ----------------------------------------------------------

    @Transactional
    public ShortageCase respond(Long caseId, String response) {
        if (response == null || response.isBlank()) {
            throw new NotPermittedException("Write an explanation before sending it.");
        }

        ShortageCase shortage = require(caseId);

        String roll = currentUser.restrictedToRollNumber()
                .orElseThrow(() -> new NotPermittedException(
                        "Only the student a case is about may answer it."));
        if (!roll.equals(shortage.getStudent().getRollNumber())) {
            throw new NotPermittedException("Case " + caseId + " is not yours to answer.");
        }
        if (shortage.getStatus() != CaseStatus.AWAITING_RESPONSE) {
            throw new NotPermittedException(
                    "This case has already been answered or decided.");
        }

        shortage.setResponse(response.trim());
        shortage.setRespondedAt(Instant.now());
        shortage.setStatus(CaseStatus.ANSWERED);

        log.info("Case {} answered by {}", caseId, roll);
        return cases.save(shortage);
    }

    // ---- queries ----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ShortageCase> open() {
        return cases.findByStatusInOrderByOpenedAtDesc(OPEN);
    }

    /** Cases the signed-in member of staff is the one to act on. */
    @Transactional(readOnly = true)
    public List<ShortageCase> mine() {
        CaseTier tier = tierFor(currentUser);
        return cases.findByStatusInOrderByOpenedAtDesc(List.of(CaseStatus.values())).stream()
                .filter(c -> c.getTier() == tier)
                .toList();
    }

    /**
     * Students the signed-in member of staff may raise a case against:
     * those inside their band who do not already have one open.
     */
    @Transactional(readOnly = true)
    public List<CaseCandidate> candidates() {
        CaseTier tier = tierFor(currentUser);
        int shortage = properties.shortageThreshold();
        int eligibility = properties.eligibilityThreshold();

        List<String> alreadyOpen = cases.findByStatusInOrderByOpenedAtDesc(OPEN).stream()
                .map(c -> c.getStudent().getRollNumber())
                .toList();

        return students.findByActiveTrueOrderByRollNumberAsc().stream()
                .filter(s -> !alreadyOpen.contains(s.getRollNumber()))
                .map(s -> new CaseCandidate(s.getRollNumber(), s.getFullName(),
                        percentFor(s.getRollNumber())))
                .filter(c -> tier == CaseTier.HOD
                        ? c.percent() < shortage
                        : c.percent() >= shortage && c.percent() < eligibility)
                .toList();
    }

    /** A student who could be questioned, with the figure that justifies it. */
    public record CaseCandidate(String rollNumber, String fullName, double percent) { }

    /**
     * Which tier the acting user owns.
     *
     * <p>An administrator is treated as the head of department rather than
     * given both tiers: one account that can act on every case would make
     * the band split unenforceable and the audit trail ambiguous about which
     * role made a decision.
     */
    private static CaseTier tierFor(CurrentUser user) {
        return user.hasRole(Role.FACULTY) && !user.isAdmin() ? CaseTier.FACULTY : CaseTier.HOD;
    }

    /**
     * Refuses a decision on a case belonging to the other tier.
     *
     * <p>The URL rules already separate raising from responding, but they
     * cannot express "this case, in this band": only the row knows that.
     */
    private void assertOwnTier(ShortageCase shortage) {
        CaseTier mine = tierFor(currentUser);
        if (shortage.getTier() != mine) {
            throw new NotPermittedException("Case " + shortage.getId() + " belongs to "
                    + shortage.getTier().getLabel() + ", not to you.");
        }
    }

    @Transactional(readOnly = true)
    public List<ShortageCase> all() {
        return cases.findByStatusInOrderByOpenedAtDesc(List.of(CaseStatus.values()));
    }

    @Transactional(readOnly = true)
    public List<ShortageCase> forStudent(String rollNumber) {
        return cases.findByStudent_RollNumberOrderByOpenedAtDesc(rollNumber);
    }

    @Transactional(readOnly = true)
    public ShortageCase require(Long caseId) {
        return cases.findById(caseId)
                .orElseThrow(() -> new RecordNotFoundException(caseId));
    }

    /**
     * Overall attendance for one student, by the same rules the dashboard
     * uses: excused sessions leave both sides (BR-03).
     */
    @Transactional(readOnly = true)
    public double percentFor(String rollNumber) {
        List<AttendanceRecord> own = records.findByStudent_RollNumberOrderBySessionDateAsc(rollNumber);
        long counted = own.stream()
                .filter(r -> r.getAttendanceStatus().countsTowardsTotal()).count();
        long attended = own.stream()
                .filter(r -> r.getAttendanceStatus().countsTowardsTotal())
                .filter(r -> r.getAttendanceStatus().countsAsAttended()).count();
        return counted == 0 ? 0.0 : Math.round(attended * 1000.0 / counted) / 10.0;
    }

    private static void assertDecidable(ShortageCase shortage) {
        if (shortage.getStatus() == CaseStatus.AWAITING_RESPONSE) {
            throw new NotPermittedException(
                    "The student has not answered yet. The point of the case is the explanation.");
        }
        if (shortage.getStatus().isDecided()) {
            throw new NotPermittedException("Case " + shortage.getId() + " has already been decided.");
        }
    }
}
