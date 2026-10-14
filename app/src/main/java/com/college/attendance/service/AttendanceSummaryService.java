package com.college.attendance.service;

import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.dto.AttendanceBucket;
import com.college.attendance.repository.AttendanceRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Attendance totalled by week and by month for one student.
 *
 * <p>A running percentage answers "is this student in trouble"; it does not
 * answer "when did it start". Two students on 60% can have arrived there by
 * opposite routes -- one steady, one who stopped attending a month ago -- and
 * only the second is urgent. Bucketing by period is what makes that visible.
 *
 * <p>Both groupings obey BR-03: an {@code EXCUSED} session counts neither as
 * attended nor towards the total, so excusing an absence removes it from the
 * calculation rather than quietly crediting it as attendance.
 */
@Service
public class AttendanceSummaryService {

    private final AttendanceRecordRepository records;

    public AttendanceSummaryService(AttendanceRecordRepository records) {
        this.records = records;
    }

    /** Totals by ISO week, oldest first. */
    public List<AttendanceBucket> weekly(String rollNumber) {
        return bucket(rollNumber,
                date -> "Week " + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                        + ", " + date.get(IsoFields.WEEK_BASED_YEAR),
                // ISO week, not day-of-month arithmetic: a week that straddles
                // a month or a year boundary still groups as one week.
                date -> date.get(IsoFields.WEEK_BASED_YEAR) * 100L
                        + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** Totals by calendar month, oldest first. */
    public List<AttendanceBucket> monthly(String rollNumber) {
        return bucket(rollNumber,
                date -> date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                        + " " + date.getYear(),
                date -> date.getYear() * 100L + date.getMonthValue());
    }

    private List<AttendanceBucket> bucket(String rollNumber,
                                           Function<LocalDate, String> label,
                                           Function<LocalDate, Long> key) {
        List<AttendanceRecord> all =
                records.findByStudent_RollNumberOrderBySessionDateAsc(rollNumber);

        // LinkedHashMap because the query is already ordered by date, so
        // insertion order is chronological and needs no second sort.
        Map<Long, Accumulator> byPeriod = new LinkedHashMap<>();
        for (AttendanceRecord record : all) {
            LocalDate date = record.getSessionDate();
            byPeriod.computeIfAbsent(key.apply(date), k -> new Accumulator(label.apply(date), date))
                    .add(record);
        }

        List<AttendanceBucket> buckets = new ArrayList<>(byPeriod.size());
        byPeriod.values().forEach(a -> buckets.add(a.toBucket()));
        return buckets;
    }

    private static final class Accumulator {
        private final String label;
        private final LocalDate from;
        private LocalDate to;
        private long attended;
        private long counted;
        private long excused;

        private Accumulator(String label, LocalDate first) {
            this.label = label;
            this.from = first;
            this.to = first;
        }

        private void add(AttendanceRecord record) {
            LocalDate date = record.getSessionDate();
            if (date.isAfter(to)) {
                to = date;
            }
            AttendanceStatus status = record.getAttendanceStatus();
            if (status == AttendanceStatus.EXCUSED) {
                excused++;
            }
            if (status.countsTowardsTotal()) {
                counted++;
                if (status.countsAsAttended()) {
                    attended++;
                }
            }
        }

        private AttendanceBucket toBucket() {
            return new AttendanceBucket(label, from, to, attended, counted, excused);
        }
    }
}
