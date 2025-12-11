package cs209a.finalproject_demo.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "answers")
public class Answer {
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(columnDefinition = "TEXT")
    private String body;

    private Boolean accepted;

    private Integer score;

    private Instant creationDate;

    private Instant lastActivityDate;

    private Integer ownerReputation;

    private Long ownerUserId;

    @Column(length = 256)
    private String ownerDisplayName;

    @Column(length = 512)
    private String ownerProfileImage;

    @Column(length = 512)
    private String ownerLink;

    @OneToMany(mappedBy = "answer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnswerComment> comments = new ArrayList<>();
}
