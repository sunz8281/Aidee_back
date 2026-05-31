package com.aidee.backend.embedding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ScriptEmbeddingRepository extends JpaRepository<ScriptEmbedding, String> {

    @Query(value = """
            SELECT * FROM script_embeddings
            WHERE project_id = :projectId
              AND (embedding <=> CAST(:query AS vector)) < :threshold
            ORDER BY embedding <=> CAST(:query AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<ScriptEmbedding> findSimilarByProject(@Param("projectId") String projectId,
                                                @Param("query") String query,
                                                @Param("topK") int topK,
                                                @Param("threshold") double threshold);

    @Query(value = """
            SELECT * FROM script_embeddings
            WHERE meeting_id = :meetingId
              AND (embedding <=> CAST(:query AS vector)) < :threshold
            ORDER BY embedding <=> CAST(:query AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<ScriptEmbedding> findSimilarByMeeting(@Param("meetingId") String meetingId,
                                                @Param("query") String query,
                                                @Param("topK") int topK,
                                                @Param("threshold") double threshold);

    @Transactional
    void deleteByScriptId(String scriptId);

    @Transactional
    void deleteByMeetingId(String meetingId);
}
