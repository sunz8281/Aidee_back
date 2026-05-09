package com.aidee.backend.meeting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, String> {
    List<Meeting> findByProjectId(String projectId);
    List<Meeting> findTop5ByProjectIdOrderByCreatedAtDesc(String projectId);
    List<Meeting> findByProjectIdAndMemoIsNotNull(String projectId);
    long countByProjectId(String projectId);
    long countByProjectIdAndMemoIsNotNullAndMemoNot(String projectId, String memo);
}
