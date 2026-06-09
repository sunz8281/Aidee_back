package com.aidee.backend.script;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ScriptRepository extends JpaRepository<ScriptSegment, String> {
    List<ScriptSegment> findByMeetingIdOrderByStartTimeAsc(String meetingId);

    @Transactional
    void deleteByMeetingId(String meetingId);

    @Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM ScriptSegment s WHERE s.meeting.project.id = :projectId")
    void deleteByMeetingProjectId(@Param("projectId") String projectId);

    @Query("SELECT s FROM ScriptSegment s WHERE s.meeting.project.id = :projectId ORDER BY s.meeting.createdAt ASC, s.startTime ASC")
    List<ScriptSegment> findByProjectId(@Param("projectId") String projectId);
}
