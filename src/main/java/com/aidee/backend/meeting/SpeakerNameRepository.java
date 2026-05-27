package com.aidee.backend.meeting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpeakerNameRepository extends JpaRepository<SpeakerName, String> {
    List<SpeakerName> findByMeetingId(String meetingId);
    Optional<SpeakerName> findByMeetingIdAndLabel(String meetingId, String label);
    void deleteByMeetingId(String meetingId);
}
