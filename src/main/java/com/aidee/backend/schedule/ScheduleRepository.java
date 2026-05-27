package com.aidee.backend.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, String> {
    List<Schedule> findByProjectId(String projectId);
    long countByProjectId(String projectId);
    @Query("SELECT s FROM Schedule s WHERE s.project.id = :projectId AND s.startTime < :rangeEnd AND s.endTime > :rangeStart")
    List<Schedule> findByProjectIdAndPeriodOverlaps(
            @Param("projectId") String projectId,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);
    List<Schedule> findByMeetingId(String meetingId);
    void deleteByMeetingId(String meetingId);
}
