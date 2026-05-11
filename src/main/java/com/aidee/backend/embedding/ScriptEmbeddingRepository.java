package com.aidee.backend.embedding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScriptEmbeddingRepository extends JpaRepository<ScriptEmbedding, String> {

    @Query(value = """
            SELECT * FROM script_embeddings
            WHERE project_id = :projectId
            ORDER BY embedding <=> CAST(:query AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<ScriptEmbedding> findSimilarByProject(@Param("projectId") String projectId,
                                                @Param("query") String query,
                                                @Param("topK") int topK);

    @Query(value = """
            SELECT * FROM script_embeddings
            WHERE meeting_id = :meetingId
            ORDER BY embedding <=> CAST(:query AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<ScriptEmbedding> findSimilarByMeeting(@Param("meetingId") String meetingId,
                                                @Param("query") String query,
                                                @Param("topK") int topK);

    void deleteByScriptId(String scriptId);
}
