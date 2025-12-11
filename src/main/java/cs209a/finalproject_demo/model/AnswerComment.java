package cs209a.finalproject_demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "answer_comments")
public class AnswerComment {
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String body;

    private Integer score;

    private Instant creationDate;

    private Long ownerUserId;

    private Integer ownerReputation;

    @Column(length = 256)
    private String ownerDisplayName;

    @Column(length = 512)
    private String ownerProfileImage;

    @Column(length = 512)
    private String ownerLink;
}
