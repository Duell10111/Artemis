package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.Submission;

/**
 * Generic interface for Spring Data JPA repositories for file upload, modeling and text submissions.
 */
@NoRepositoryBean
public interface GenericSubmissionRepository<T extends Submission> extends JpaRepository<T, Long> {

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor
     */
    @Query("select distinct submission from #{#entityName} submission left join fetch submission.result r left join fetch r.feedbacks left join fetch r.assessor where submission.id=?1")
    Optional<T> findByIdWithEagerResultAndFeedback(@Param("submissionId") Long submissionId);

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its assessor
     */
    @Query("select distinct submission from #{#entityName} submission left join fetch submission.result r left join fetch r.assessor where submission.id=?1")
    Optional<T> findByIdWithEagerResultAndAssessor(@Param("submissionId") Long submissionId);

    /**
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date before the exercise due date, or no exercise
     *         due date at all
     */
    @Query("SELECT COUNT (DISTINCT submission) FROM #{#entityName} submission WHERE submission.participation.exercise.course.id = ?1 AND submission.submitted = TRUE AND (submission.submissionDate < submission.participation.exercise.dueDate OR submission.participation.exercise.dueDate IS NULL)")
    long countByCourseIdSubmittedBeforeDueDate(@Param("courseId") Long courseId);

    /**
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date before the exercise due date, or no
     *         exercise due date at all
     */
    @Query("SELECT COUNT (DISTINCT submission) FROM #{#entityName} submission WHERE submission.participation.exercise.id = ?1 AND submission.submitted = TRUE AND (submission.submissionDate < submission.participation.exercise.dueDate OR submission.participation.exercise.dueDate IS NULL)")
    long countByExerciseIdSubmittedBeforeDueDate(@Param("exerciseId") Long exerciseId);

    /**
     * Load the submission with the given id together with its result, the feedback list of the result, the assessor of the result, its participation and all results of
     * the participation.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the submission with its result, the feedback list of the result, the assessor of the result, its participation and all results of the participation
     */
    @EntityGraph(attributePaths = { "result", "result.feedbacks", "result.assessor", "participation", "participation.results" })
    Optional<T> findWithEagerResultAndFeedbackAndAssessorAndParticipationResultsById(Long submissionId);
}
