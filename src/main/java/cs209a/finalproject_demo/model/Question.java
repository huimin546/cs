package cs209a.finalproject_demo.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "questions")
public class Question {
    @Id
    private Long id;

    @Column(length = 512)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    private Boolean answered;

    private Integer viewCount;

    private Integer answerCount;

    private Integer score;

    @Column(length = 512)
    private String questionLink;

    private Instant creationDate;

    private Instant lastActivityDate;

    private Instant closedDate;

    @Column(length = 256)
    private String closedReason;

    private Long acceptedAnswerId;

    private Integer ownerReputation;

    private Long ownerUserId;

    @Column(length = 256)
    private String ownerDisplayName;

    @Column(length = 512)
    private String ownerProfileImage;

    @Column(length = 512)
    private String ownerLink;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Answer> answers = new ArrayList<>();

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuestionComment> questionComments = new ArrayList<>();

    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "question_tags", joinColumns = @JoinColumn(name = "question_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();
}
