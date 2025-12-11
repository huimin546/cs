package cs209a.finalproject_demo.repository;

import cs209a.finalproject_demo.model.Question;
import cs209a.finalproject_demo.repository.projection.TagPairRow;
import cs209a.finalproject_demo.repository.projection.TopicTrendRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @Query(value = """
            SELECT
                                            bucketed.tag,
                                            bucketed.bucket,
                                            COUNT(bucketed.question_id) AS question_count,
                                            COALESCE(SUM(bucketed.score), 0) AS score_sum
            FROM (
                                            SELECT
                                                                            t.name AS tag,
                                                                            q.id AS question_id,
                                                                            q.score AS score,
                                                                            CASE WHEN :is_year
                                                                                                            THEN date_trunc('year', q.creation_date)
                                                                                                            ELSE date_trunc('month', q.creation_date)
                                                                            END AS bucket
                                                FROM questions q
                                                JOIN question_tags qt ON q.id = qt.question_id
                                                JOIN tags t ON t.id = qt.tag_id
                                                WHERE q.creation_date >= :from_ts
                                                                AND q.creation_date <= :to_ts
                                                                AND t.name = ANY(:tag_array)
            ) AS bucketed
            GROUP BY bucketed.tag, bucketed.bucket
            ORDER BY bucketed.tag, bucketed.bucket
            """, nativeQuery = true)
    List<TopicTrendRow> findTopicTrends(
            @Param("tag_array") String[] tagArray,
            @Param("from_ts") Instant from,
            @Param("to_ts") Instant to,
            @Param("is_year") boolean isYear);

    @Query(value = """
            SELECT
                            t1.name AS tag_a,
                            t2.name AS tag_b,
                            COUNT(DISTINCT qt1.question_id) AS pair_count
            FROM question_tags qt1
            JOIN question_tags qt2
                    ON qt1.question_id = qt2.question_id
                    AND qt1.tag_id < qt2.tag_id
            JOIN tags t1 ON t1.id = qt1.tag_id
            JOIN tags t2 ON t2.id = qt2.tag_id
            GROUP BY t1.name, t2.name
            ORDER BY pair_count DESC, tag_a ASC, tag_b ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<TagPairRow> findTopTagPairs(@Param("limit") int limit);

    @Query("""
            SELECT DISTINCT q.id FROM Question q
            JOIN q.tags t
            WHERE LOWER(t.name) IN :tagNames
            """)
    List<Long> findDistinctIdsByTagNames(@Param("tagNames") List<String> tagNames);
}
