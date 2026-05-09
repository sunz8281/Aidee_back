package com.aidee.backend.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, String> {
    List<Schedule> findByProjectId(String projectId);
    long countByProjectId(String projectId);
    List<Schedule> findByProjectIdAndStartTimeBetween(String projectId,
                                                       LocalDateTime startOfMonth,
                                                       LocalDateTime endOfMonth);
    List<Schedule> findByMeetingId(String meetingId);
}
