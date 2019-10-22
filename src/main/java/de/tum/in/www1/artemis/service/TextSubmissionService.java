package de.tum.in.www1.artemis.service;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Transactional
public class TextSubmissionService extends SubmissionService<TextSubmission> {

    private final TextSubmissionRepository textSubmissionRepository;

    private final Optional<TextAssessmentQueueService> textAssessmentQueueService;

    public TextSubmissionService(TextSubmissionRepository textSubmissionRepository, SubmissionRepository submissionRepository,
            StudentParticipationRepository studentParticipationRepository, ParticipationService participationService, ResultRepository resultRepository, UserService userService,
            Optional<TextAssessmentQueueService> textAssessmentQueueService, SimpMessageSendingOperations messagingTemplate, AuthorizationCheckService authCheckService) {
        super(submissionRepository, userService, authCheckService, resultRepository, participationService, messagingTemplate, studentParticipationRepository);
        this.textSubmissionRepository = textSubmissionRepository;
        this.textAssessmentQueueService = textAssessmentQueueService;
    }

    /**
     * Handles text submissions sent from the client and saves them in the database.
     *
     * @param textSubmission the text submission that should be saved
     * @param textExercise   the corresponding text exercise
     * @param principal      the user principal
     * @return the saved text submission
     */
    @Transactional
    public TextSubmission handleTextSubmission(TextSubmission textSubmission, TextExercise textExercise, Principal principal) {
        if (textSubmission.isExampleSubmission() == Boolean.TRUE) {
            textSubmission = save(textSubmission);
        }
        else {
            textSubmission = save(textSubmission, textExercise, principal.getName(), TextSubmission.class);
        }
        return textSubmission;
    }

    /**
     * The same as `save()`, but without participation, is used by example submission, which aren't linked to any participation
     *
     * @param textSubmission the submission to notifyCompass
     * @return the textSubmission entity
     */
    @Transactional(rollbackFor = Exception.class)
    public TextSubmission save(TextSubmission textSubmission) {
        textSubmission.setSubmissionDate(ZonedDateTime.now());
        textSubmission.setType(SubmissionType.MANUAL);

        // Rebuild connection between result and submission, if it has been lost, because hibernate needs it
        if (textSubmission.getResult() != null && textSubmission.getResult().getSubmission() == null) {
            textSubmission.getResult().setSubmission(textSubmission);
        }

        textSubmission = textSubmissionRepository.save(textSubmission);

        return textSubmission;
    }

    /**
     * Given an exercise id, find a random text submission for that exercise which still doesn't have any manual result. No manual result means that no user has started an
     * assessment for the corresponding submission yet.
     *
     * @param textExercise the exercise for which we want to retrieve a submission without manual result
     * @return a textSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    @Transactional(readOnly = true)
    public Optional<TextSubmission> getTextSubmissionWithoutManualResult(TextExercise textExercise) {
        if (textExercise.isAutomaticAssessmentEnabled() && textAssessmentQueueService.isPresent()) {
            return textAssessmentQueueService.get().getProposedTextSubmission(textExercise);
        }
        return getRandomUnassessedSubmission(textExercise, TextSubmission.class);
    }

    /**
     * Return all TextSubmission which are the latest TextSubmission of a Participation and doesn't have a Result so far
     * The corresponding TextBlocks and Participations are retrieved from the database
     * @param exercise Exercise for which all assessed submissions should be retrieved
     * @return List of all TextSubmission which aren't assessed at the Moment, but need assessment in the future.
     *
     */
    public List<TextSubmission> getAllOpenTextSubmissions(TextExercise exercise) {
        return textSubmissionRepository.findByParticipation_ExerciseIdAndResultIsNullAndSubmittedIsTrue(exercise.getId()).stream()
                .filter(tS -> tS.getParticipation().findLatestSubmission().isPresent() && tS == tS.getParticipation().findLatestSubmission().get()).collect(Collectors.toList());
    }

    /**
     * Given an exercise id and a tutor id, it returns all the text submissions where the tutor has a result associated
     *
     * @param exerciseId - the id of the exercise we are looking for
     * @param tutorId    - the id of the tutor we are interested in
     * @return a list of text Submissions
     */
    @Transactional(readOnly = true)
    public List<TextSubmission> getAllTextSubmissionsByTutorForExercise(Long exerciseId, Long tutorId) {
        return textSubmissionRepository.findAllByResult_Participation_ExerciseIdAndResult_Assessor_Id(exerciseId, tutorId).stream().map(Optional::get).collect(Collectors.toList());
    }

    public TextSubmission findOneWithEagerResultAndAssessor(Long id) {
        return textSubmissionRepository.findByIdWithEagerResultAndAssessor(id)
                .orElseThrow(() -> new EntityNotFoundException("Text submission with id \"" + id + "\" does not exist"));
    }
}
