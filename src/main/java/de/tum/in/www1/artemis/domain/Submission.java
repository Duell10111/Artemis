package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.view.QuizView;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

/**
 * A Submission.
 */
@Entity
@Table(name = "submission")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "S")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "submissionExerciseType")
// Annotation necessary to distinguish between concrete implementations of Submission when deserializing from JSON
@JsonSubTypes({ @JsonSubTypes.Type(value = ProgrammingSubmission.class, name = "programming"), @JsonSubTypes.Type(value = ModelingSubmission.class, name = "modeling"),
        @JsonSubTypes.Type(value = QuizSubmission.class, name = "quiz"), @JsonSubTypes.Type(value = TextSubmission.class, name = "text"),
        @JsonSubTypes.Type(value = FileUploadSubmission.class, name = "file-upload"), })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Submission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(QuizView.Before.class)
    private Long id;

    @Column(name = "submitted")
    @JsonView(QuizView.Before.class)
    private Boolean submitted;

    @Enumerated(EnumType.STRING)
    @Column(name = "jhi_type")
    @JsonView(QuizView.Before.class)
    private SubmissionType type;

    @Column(name = "example_submission")
    private Boolean exampleSubmission;

    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    private Language language;

    @ManyToOne
    private Participation participation;

    /**
     * A submission can have a result and therefore, results are persisted and removed with a submission.
     */
    @OneToOne(mappedBy = "submission", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JsonIgnoreProperties({ "submission", "participation" })
    @JoinColumn(unique = true)
    private Result result;

    @Column(name = "submission_date")
    private ZonedDateTime submissionDate;

    @JsonView(QuizView.Before.class)
    public ZonedDateTime getSubmissionDate() {
        return submissionDate;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public Participation getParticipation() {
        return participation;
    }

    public void setParticipation(Participation participation) {
        this.participation = participation;
    }

    public Submission submissionDate(ZonedDateTime submissionDate) {
        this.submissionDate = submissionDate;
        return this;
    }

    public void setSubmissionDate(ZonedDateTime submissionDate) {
        this.submissionDate = submissionDate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean isSubmitted() {
        return submitted != null ? submitted : false;
    }

    public Submission submitted(Boolean submitted) {
        this.submitted = submitted;
        return this;
    }

    public void setSubmitted(Boolean submitted) {
        this.submitted = submitted;
    }

    public SubmissionType getType() {
        return type;
    }

    public Submission type(SubmissionType type) {
        this.type = type;
        return this;
    }

    public void setType(SubmissionType type) {
        this.type = type;
    }

    public Boolean isExampleSubmission() {
        return exampleSubmission;
    }

    public Submission exampleSubmission(Boolean exampleSubmission) {
        this.exampleSubmission = exampleSubmission;
        return this;
    }

    public Language getLanguage() {
        return language;
    }

    public Submission language(Language language) {
        this.language = language;
        return this;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public void setExampleSubmission(Boolean exampleSubmission) {
        this.exampleSubmission = exampleSubmission;
    }

    /**
     * Removes sensitive information (e.g. example solution of the exercise) from the submission based on the role of the current user. This should be called before sending a
     * submission to the client. IMPORTANT: Do not call this method from a transactional context as this would remove the sensitive information also from the entities in the
     * database without explicitly saving them.
     *
     * @param authCheckService authorization check service to check what details can be hidden
     */
    public void hideDetails(AuthorizationCheckService authCheckService, User user) {
        // do not send old submissions or old results to the client
        if (getParticipation() != null) {
            getParticipation().setSubmissions(null);
            getParticipation().setResults(null);

            Exercise exercise = getParticipation().getExercise();
            if (exercise != null) {
                // make sure that sensitive information is not sent to the client for students
                if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
                    exercise.filterSensitiveInformation();
                    setResult(null);
                }
                // remove information about the student from the submission for tutors to ensure a double-blind assessment
                if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
                    ((StudentParticipation) getParticipation()).filterSensitiveInformation();
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Submission submission = (Submission) o;
        if (submission.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), submission.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Submission{" + "id=" + getId() + ", submitted='" + isSubmitted() + "'" + ", type='" + getType() + "'" + "}";
    }

}
