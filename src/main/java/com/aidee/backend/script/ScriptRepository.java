package com.aidee.backend.script;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScriptRepository extends JpaRepository<ScriptSegment, String> {
    List<ScriptSegment> findByMeetingIdOrderByStartTimeAsc(String meetingId);
}
